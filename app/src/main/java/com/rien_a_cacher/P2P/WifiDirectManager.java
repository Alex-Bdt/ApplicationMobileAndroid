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

import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.stream.Collectors;

public class WifiDirectManager {

    private static final String TAG = "WifiDirect";

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
        // Supprime un éventuel groupe persistant avant d'en créer un propre
        manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "removeGroup OK → createGroup");
                createGroupInternal();
            }
            @Override
            public void onFailure(int reason) {
                Log.d(TAG, "removeGroup (pas de groupe existant) → createGroup");
                createGroupInternal();
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void createGroupInternal() {
        manager.createGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "createGroup() onSuccess");
                // Démarre la découverte pour être visible aux clients
                startDiscoveryInternal();
                // Vérifie l'état du groupe
                requestConnectionInfo();
            }
            @Override
            public void onFailure(int reason) {
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
        // Nettoie d'abord tout groupe persistant côté client
        manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "removeGroup client OK → connect");
                connectInternal(device);
            }
            @Override
            public void onFailure(int reason) {
                Log.d(TAG, "removeGroup client (normal) → connect");
                connectInternal(device);
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void connectInternal(WifiP2pDevice device) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        config.groupOwnerIntent = 0; // client = jamais Group Owner

        Log.d(TAG, "connectInternal() sur : " + device.deviceAddress);

        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "connect() onSuccess");
            }
            @Override
            public void onFailure(int reason) {
                Log.d(TAG, "connect() onFailure reason=" + reason);
                listener.onError("Échec connexion : " + reason);
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