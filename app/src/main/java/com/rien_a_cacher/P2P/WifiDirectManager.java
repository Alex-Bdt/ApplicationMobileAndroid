package com.rien_a_cacher.P2P;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.stream.Collectors;

public class WifiDirectManager {

    private static final String TAG = "WifiDirect";

    // SSID et mot de passe fixes → pas de MAC randomisée, connexions stables
    private static final String GROUP_SSID       = "DIRECT-PhotoGame";
    private static final String GROUP_PASSPHRASE = "photogame123";

    public interface WifiDirectListener {
        void onDevicesDiscovered(List<WifiP2pDevice> devices);
        void onConnectionInfoAvailable(String hostAddress, boolean isGroupOwner);
        void onError(String message);
    }

    private final WifiP2pManager manager;
    private final WifiP2pManager.Channel channel;
    private final Context context;
    private final WifiDirectListener listener;
    private BroadcastReceiver receiver;

    // Redécouverte périodique
    private final Handler retryHandler = new Handler(Looper.getMainLooper());
    private boolean discovering = false;
    private final Runnable discoveryRunnable = new Runnable() {
        @Override
        public void run() {
            if (discovering) {
                startDiscoveryInternal();
                retryHandler.postDelayed(this, 5000);
            }
        }
    };

    private final Handler connectionHandler = new Handler(Looper.getMainLooper());
    private boolean waitingForConnection = false;
    private final Runnable connectionCheckRunnable = new Runnable() {
        @Override
        public void run() {
            if (waitingForConnection) {
                Log.d(TAG, "Polling requestConnectionInfo...");
                requestConnectionInfo();
                connectionHandler.postDelayed(this, 2000);
            }
        }
    };

    public WifiDirectManager(Context context, WifiDirectListener listener) {
        this.context = context;
        this.listener = listener;
        this.manager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        this.channel = manager.initialize(context, context.getMainLooper(), null);
    }

    // Vérifie si les permissions nécessaires sont accordées
    private boolean hasRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context,
                    Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(context,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    // -------------------------------------------------------------------
    // HOST
    // -------------------------------------------------------------------
    @SuppressLint("MissingPermission")
    public void startAsHost() {
        if (!hasRequiredPermissions()) {
            listener.onError("Permission Wi-Fi manquante");
            return;
        }
        // Supprime d'abord tout groupe persistant
        manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
            @Override public void onSuccess() {
                Log.d(TAG, "removeGroup OK → createGroup");
                // Délai Samsung : attend que le channel soit libéré
                new Handler(Looper.getMainLooper()).postDelayed(
                        () -> createGroupInternal(), 500);
            }
            @Override public void onFailure(int reason) {
                Log.d(TAG, "removeGroup (pas de groupe) → createGroup");
                new Handler(Looper.getMainLooper()).postDelayed(
                        () -> createGroupInternal(), 500);
            }
        });
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void createGroupInternal() {
        if (!hasRequiredPermissions()) return;

        // SSID et passphrase fixes → adresse MAC stable, pas de randomisation
        WifiP2pConfig config = new WifiP2pConfig.Builder()
                .setNetworkName(GROUP_SSID)
                .setPassphrase(GROUP_PASSPHRASE)
                .enablePersistentMode(true)
                .build();

        manager.createGroup(channel, config, new WifiP2pManager.ActionListener() {
            @Override public void onSuccess() {
                Log.d(TAG, "createGroup() onSuccess");
                // Vérifie l'état du groupe et notifie le listener
                requestConnectionInfo();
                // Démarre la découverte pour être visible
                startDiscoveryInternal();
            }
            @Override public void onFailure(int reason) {
                Log.d(TAG, "createGroup() onFailure reason=" + reason);
                listener.onError("Échec création groupe : " + reason);
            }
        });
    }

    // -------------------------------------------------------------------
    // CLIENT - découverte
    // -------------------------------------------------------------------

    public void startDiscovery() {
        if (!hasRequiredPermissions()) {
            listener.onError("Permission Wi-Fi manquante");
            return;
        }
        discovering = true;
        startDiscoveryInternal();
        retryHandler.removeCallbacks(discoveryRunnable);
        retryHandler.postDelayed(discoveryRunnable, 5000);
    }

    @SuppressLint("MissingPermission")
    private void startDiscoveryInternal() {
        if (!hasRequiredPermissions()) return;
        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "discoverPeers() onSuccess");
            }
            @Override
            public void onFailure(int reason) {
                Log.d(TAG, "discoverPeers() onFailure reason=" + reason);
            }
        });
    }

    public void stopDiscovery() {
        discovering = false;
        retryHandler.removeCallbacks(discoveryRunnable);
        manager.stopPeerDiscovery(channel, null);
    }

    // connexion

    public void connectToDevice(WifiP2pDevice device) {
        if (!hasRequiredPermissions()) {
            listener.onError("Permission Wi-Fi manquante");
            return;
        }

        Log.d(TAG, "connectToDevice → requestConnectionInfo d'abord");

        // Samsung : stoppe la découverte AVANT de connecter
        manager.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {
            @Override public void onSuccess() {
                Log.d(TAG, "stopPeerDiscovery OK → connect");
                // Délai Samsung : laisse le channel se stabiliser
                new Handler(Looper.getMainLooper()).postDelayed(
                        () -> connectInternal(device), 500);
            }
            @Override public void onFailure(int reason) {
                Log.d(TAG, "stopPeerDiscovery failed=" + reason + " → connect quand même");
                new Handler(Looper.getMainLooper()).postDelayed(
                        () -> connectInternal(device), 500);
            }
        });
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void connectInternal(WifiP2pDevice device) {
        if (!hasRequiredPermissions()) return;

        // Connexion avec le même SSID/passphrase que le host
        // → contourne la MAC randomisée, Samsung reconnaît le groupe
        WifiP2pConfig config = new WifiP2pConfig.Builder()
                .setNetworkName(GROUP_SSID)
                .setPassphrase(GROUP_PASSPHRASE)
                .build();

        Log.d(TAG, "connectInternal() sur : " + device.deviceAddress
                + " name=" + device.deviceName
                + " status=" + device.status);

        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override public void onSuccess() {
                Log.d(TAG, "connect() onSuccess");
            }
            @Override public void onFailure(int reason) {
                Log.d(TAG, "connect() onFailure reason=" + reason);
                listener.onError("Échec connexion : " + reason);
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void doConnect(WifiP2pDevice device) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        // Ne pas forcer groupOwnerIntent sur Samsung — laisser la négociation automatique
        // config.groupOwnerIntent = 0; ← supprime cette ligne

        Log.d(TAG, "doConnect() sur : " + device.deviceAddress
                + " name=" + device.deviceName
                + " status=" + device.status);

        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override public void onSuccess() {
                Log.d(TAG, "doConnect() onSuccess");
            }
            @Override public void onFailure(int reason) {
                Log.d(TAG, "doConnect() onFailure reason=" + reason);
                // Sur Samsung, essaie avec un délai de 2s
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    Log.d(TAG, "Retry doConnect après délai...");
                    retryConnect(device);
                }, 2000);
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void retryConnect(WifiP2pDevice device) {
        if (!hasRequiredPermissions()) return;

        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;

        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override public void onSuccess() {
                Log.d(TAG, "retryConnect() onSuccess");
            }
            @Override public void onFailure(int reason) {
                Log.d(TAG, "retryConnect() onFailure reason=" + reason);
                listener.onError("Impossible de se connecter (code " + reason + ")");
            }
        });
    }

    // ptite sécurité pour la connexion si pb de broadcast

    public void startConnectionPolling() {
        waitingForConnection = true;
        connectionHandler.postDelayed(connectionCheckRunnable, 2000);
    }

    public void stopConnectionPolling() {
        waitingForConnection = false;
        connectionHandler.removeCallbacks(connectionCheckRunnable);
    }

    // -------------------------------------------------------------------
    // Info de connexion
    // -------------------------------------------------------------------

    public void requestConnectionInfo() {
        manager.requestConnectionInfo(channel, info -> {
            if (info == null) {
                Log.d(TAG, "requestConnectionInfo: info null");
                return;
            }
            Log.d(TAG, "groupFormed=" + info.groupFormed
                    + " isGroupOwner=" + info.isGroupOwner
                    + " hostAddress=" + info.groupOwnerAddress);

            if (info.groupFormed) {
                stopConnectionPolling();
                String hostAddress = info.groupOwnerAddress.getHostAddress();
                listener.onConnectionInfoAvailable(hostAddress, info.isGroupOwner);
            }
        });
    }

    // -------------------------------------------------------------------
    // BroadCastReceiver
    // -------------------------------------------------------------------

    public void registerReceiver() {
        receiver = new BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                Log.d(TAG, "BroadcastReceiver reçu : " + action);

                if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                    if (!hasRequiredPermissions()) return;
                    manager.requestPeers(channel, peers -> {
                        Log.d(TAG, "Peers trouvés : " + peers.getDeviceList().size());
                        listener.onDevicesDiscovered(
                                peers.getDeviceList().stream().collect(Collectors.toList())
                        );
                    });

                } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                    Log.d(TAG, "CONNECTION_CHANGED reçu");
                    requestConnectionInfo();

                } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
                    Log.d(TAG, "THIS_DEVICE_CHANGED reçu");
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(receiver, filter);
        }
        Log.d(TAG, "BroadcastReceiver enregistré");
    }

    public void unregisterReceiver() {
        stopDiscovery();
        stopConnectionPolling();
        if (receiver != null) context.unregisterReceiver(receiver);
    }

    public void removeGroup() {
        manager.removeGroup(channel, null);
    }
}