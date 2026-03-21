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

import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.stream.Collectors;

public class WifiDirectManager {

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

    public WifiDirectManager(Context context, WifiDirectListener listener) {
        this.context = context;
        this.listener = listener;
        this.manager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        this.channel = manager.initialize(context, context.getMainLooper(), null);
    }

    // Vérifie si les permissions nécessaires sont accordées
    private boolean hasRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ : NEARBY_WIFI_DEVICES remplace ACCESS_FINE_LOCATION
            return ContextCompat.checkSelfPermission(context,
                    Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(context,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    @SuppressLint("MissingPermission") //permet de dire qu'on fait le taff
    public void startDiscovery() {
        if (!hasRequiredPermissions()) {
            listener.onError("Permission Wi-Fi manquante");
            return;
        }
        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override public void onSuccess() {}
            @Override public void onFailure(int reason) {
                listener.onError("Échec de la découverte : " + reason);
            }
        });
    }

    @SuppressLint("MissingPermission")
    public void connect(WifiP2pDevice device) {
        if (!hasRequiredPermissions()) {
            listener.onError("Permission Wi-Fi manquante");
            return;
        }
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;

        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override public void onSuccess() {}
            @Override public void onFailure(int reason) {
                listener.onError("Échec de la connexion : " + reason);
            }
        });
    }

    @SuppressLint("MissingPermission")
    public void createGroup() {
        if (!hasRequiredPermissions()) {
            listener.onError("Permission Wi-Fi manquante");
            return;
        }
        manager.createGroup(channel, new WifiP2pManager.ActionListener() {
            @Override public void onSuccess() {}
            @Override public void onFailure(int reason) {
                listener.onError("Échec création groupe : " + reason);
            }
        });
    }

    public void requestConnectionInfo() {
        manager.requestConnectionInfo(channel, info -> {
            if (info != null && info.groupFormed) {
                String hostAddress = info.groupOwnerAddress.getHostAddress();
                listener.onConnectionInfoAvailable(hostAddress, info.isGroupOwner);
            }
        });
    }

    public void registerReceiver() {
        receiver = new BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                    if (!hasRequiredPermissions()) return;
                    manager.requestPeers(channel, peers ->
                            listener.onDevicesDiscovered(
                                    peers.getDeviceList().stream().collect(Collectors.toList())
                            )
                    );
                } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                    requestConnectionInfo();
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(receiver, filter);
        }
    }

    public void unregisterReceiver() {
        if (receiver != null) context.unregisterReceiver(receiver);
    }

    public void removeGroup() {
        manager.removeGroup(channel, null);
    }
}
