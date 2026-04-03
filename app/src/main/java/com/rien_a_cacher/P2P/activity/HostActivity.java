package com.rien_a_cacher.P2P.activity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.rien_a_cacher.P2P.metier.HostServer;
import com.rien_a_cacher.P2P.metier.PlayerInfo;
import com.rien_a_cacher.P2P.metier.WaitingRoomAdapter;
import com.rien_a_cacher.P2P.metier.WifiDirectManager;
import com.rien_a_cacher.profile.ProfileManager;
import com.rien_a_cacher.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class HostActivity extends AppCompatActivity
        implements WifiDirectManager.WifiDirectListener, HostServer.HostServerListener {

    private static final String TAG = "WifiDirect";

    private TextView tvPin;
    private RecyclerView rvPlayers;
    private TextView tvStatus;
    private Button btnStart;

    private WifiDirectManager wifiDirectManager;
    private HostServer hostServer;
    private WaitingRoomAdapter adapter;
    private List<PlayerInfo> playerList = new ArrayList<>();

    private String pin;

    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (!result.containsValue(false)) {
                    onPermissionsGranted();
                } else {
                    Toast.makeText(this,
                            "Permissions Wi-Fi refusées",
                            Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_host);

        tvPin     = findViewById(R.id.tvPin);
        tvStatus  = findViewById(R.id.tvStatus);
        rvPlayers = findViewById(R.id.rvPlayers);
        btnStart  = findViewById(R.id.btnStart);

        pin = String.format("%04d", new Random().nextInt(10000));
        tvPin.setText(pin);
        tvStatus.setText("Création du groupe...");

        // Récupère le profil du host
        ProfileManager profileManager = new ProfileManager(this);
        PlayerInfo hostInfo = new PlayerInfo(
                profileManager.hasProfile() ? profileManager.getUsername() : "Host",
                profileManager.getPhotoPath(),
                true
        );
        playerList.add(hostInfo);

        adapter = new WaitingRoomAdapter(playerList);
        rvPlayers.setLayoutManager(new LinearLayoutManager(this));
        rvPlayers.setAdapter(adapter);

        wifiDirectManager = new WifiDirectManager(this, this);
        wifiDirectManager.registerReceiver();

        hostServer = new HostServer(pin, this);
        hostServer.setHostInfo(hostInfo);
        hostServer.start();

        btnStart.setOnClickListener(v -> {
            if (playerList.size() < 2) {
                Toast.makeText(this, "Aucun joueur connecté",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            hostServer.broadcastStart();
            // TODO : lancer l'écran de jeu
        });

        checkWifiPermissionsAndStart();
    }

    private void checkWifiPermissionsAndStart() {
        List<String> toRequest = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.NEARBY_WIFI_DEVICES)
                    != PackageManager.PERMISSION_GRANTED)
                toRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        } else {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED)
                toRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED)
                toRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        if (toRequest.isEmpty()) {
            onPermissionsGranted();
        } else {
            requestPermissionsLauncher.launch(toRequest.toArray(new String[0]));
        }
    }

    private void onPermissionsGranted() {
        wifiDirectManager.startAsHost();
    }


    // WifiDirectListener
    @Override
    public void onDevicesDiscovered(List<WifiP2pDevice> devices) {}

    @Override
    public void onConnectionInfoAvailable(String hostAddress, boolean isGroupOwner) {
        Log.d(TAG, "Host onConnectionInfoAvailable isGroupOwner=" + isGroupOwner);
        if (isGroupOwner) {
            tvStatus.setText("En attente de joueurs");
        }
    }

    @Override
    public void onError(String message) {
        Log.d(TAG, "onError: " + message);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // HostServerListener
    @Override
    public void onPlayerJoined(PlayerInfo player) {
        playerList.add(player);
        adapter.notifyItemInserted(playerList.size() - 1);
        Toast.makeText(this, player.name + " a rejoint !",
                Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        wifiDirectManager.unregisterReceiver();
        wifiDirectManager.removeGroup();
        hostServer.stop();
    }
}
