package com.rien_a_cacher.P2P;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.rien_a_cacher.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class HostActivity extends AppCompatActivity
        implements WifiDirectManager.WifiDirectListener, HostServer.HostServerListener {

    private TextView tvPin;
    private TextView tvPlayers;
    private Button btnStart;

    private WifiDirectManager wifiDirectManager;
    private HostServer hostServer;
    private int playerCount = 0;

    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = !result.containsValue(false);
                if (allGranted) {
                    onPermissionsGranted();
                } else {
                    Toast.makeText(this,
                            "Permissions Wi-Fi refusées, impossible de créer une partie",
                            Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_host);

        tvPin     = findViewById(R.id.tvPin);
        tvPlayers = findViewById(R.id.tvPlayers);
        btnStart  = findViewById(R.id.btnStart);

        // Génère un PIN à 4 chiffres
        String pin = String.format("%04d", new Random().nextInt(10000));
        tvPin.setText(pin);

        // Init Wi-Fi Direct
        wifiDirectManager = new WifiDirectManager(this, this);
        wifiDirectManager.registerReceiver();

        // Démarre le serveur TCP
        hostServer = new HostServer(pin, this);
        hostServer.start();

        btnStart.setOnClickListener(v -> {
            if (playerCount == 0) {
                Toast.makeText(this, "Aucun joueur connecté", Toast.LENGTH_SHORT).show();
                return;
            }
            hostServer.broadcastStart();
            // TODO : lancer l'écran de jeu
        });
    }

    private void checkWifiPermissionsAndStart() {
        List<String> toRequest = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
                    != PackageManager.PERMISSION_GRANTED) {
                toRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                toRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                toRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            }
        }

        if (toRequest.isEmpty()) {
            // Toutes les permissions sont déjà accordées
            onPermissionsGranted();
        } else {
            // Déclenche la popup système de demande de permission
            requestPermissionsLauncher.launch(toRequest.toArray(new String[0]));
        }
    }

    private void onPermissionsGranted() {
        wifiDirectManager.createGroup();
    }


    // WifiDirectListener
    @Override
    public void onDevicesDiscovered(List<WifiP2pDevice> devices) {}

    @Override
    public void onConnectionInfoAvailable(String hostAddress, boolean isGroupOwner) {}

    @Override
    public void onError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // HostServerListener
    @Override
    public void onPlayerJoined(String playerName) {
        playerCount++;
        tvPlayers.setText("Joueurs connectés : " + playerCount);
        Toast.makeText(this, playerName + " a rejoint la partie !", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        wifiDirectManager.unregisterReceiver();
        wifiDirectManager.removeGroup();
        hostServer.stop();
    }

}
