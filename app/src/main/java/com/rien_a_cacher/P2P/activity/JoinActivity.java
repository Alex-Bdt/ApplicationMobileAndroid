package com.rien_a_cacher.P2P.activity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.rien_a_cacher.P2P.metier.ClientConnection;
import com.rien_a_cacher.P2P.metier.PlayerInfo;
import com.rien_a_cacher.P2P.metier.RoomAdapter;
import com.rien_a_cacher.P2P.metier.WaitingRoomAdapter;
import com.rien_a_cacher.P2P.metier.WifiDirectManager;
import com.rien_a_cacher.profile.ProfileManager;
import com.rien_a_cacher.R;
import java.util.ArrayList;
import java.util.List;

public class JoinActivity extends AppCompatActivity
    implements WifiDirectManager.WifiDirectListener, ClientConnection.ClientListener {

    private static final String TAG = "WifiDirect";

    private TextView tvStatus;
    private RecyclerView rvRooms;
    private LinearLayout pinLayout;
    private LinearLayout waitingLayout;
    private EditText etPin;
    private Button btnSubmitPin;
    private RecyclerView rvWaitingPlayers;

    private WifiDirectManager wifiDirectManager;
    private ClientConnection clientConnection;
    private RoomAdapter roomAdapter;
    private WaitingRoomAdapter waitingAdapter;
    private List<WifiP2pDevice> discoveredDevices = new ArrayList<>();
    private List<PlayerInfo> waitingPlayers = new ArrayList<>();

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
        setContentView(R.layout.activity_join);

        tvStatus      = findViewById(R.id.tvStatus);
        rvRooms       = findViewById(R.id.rvRooms);
        pinLayout     = findViewById(R.id.pinLayout);
        waitingLayout = findViewById(R.id.waitingLayout);
        etPin         = findViewById(R.id.etPin);
        btnSubmitPin  = findViewById(R.id.btnSubmitPin);
        rvWaitingPlayers = findViewById(R.id.rvWaitingPlayers);

        // Setup RecyclerView des salles
        roomAdapter = new RoomAdapter(discoveredDevices, device -> {
            tvStatus.setText("Connexion à " + device.deviceName + "...");
            rvRooms.setVisibility(View.GONE);
            wifiDirectManager.stopDiscovery();
            wifiDirectManager.connectToDevice(device); // utilise SSID/passphrase en interne
            wifiDirectManager.startConnectionPolling();
        });
        rvRooms.setLayoutManager(new LinearLayoutManager(this));
        rvRooms.setAdapter(roomAdapter);

        // Adapter salle d'attente
        waitingAdapter = new WaitingRoomAdapter(waitingPlayers);
        rvWaitingPlayers.setLayoutManager(new LinearLayoutManager(this));
        rvWaitingPlayers.setAdapter(waitingAdapter);

        // Soumission du PIN
        btnSubmitPin.setOnClickListener(v -> {
            String pinInput = etPin.getText().toString().trim();
            Log.d(TAG, "pinInput=" + pinInput + " length=" + pinInput.length());

            if (pinInput.length() != 4) {
                Toast.makeText(this, "Le PIN doit contenir 4 chiffres", Toast.LENGTH_SHORT).show();
                return;
            }
            if (clientConnection == null) {
                Toast.makeText(this, "Pas encore connecté au host", Toast.LENGTH_SHORT).show();
                return;
            }
            clientConnection.submitPin(pinInput);
        });
        wifiDirectManager = new WifiDirectManager(this, this);
        wifiDirectManager.registerReceiver();

        checkWifiPermissionsAndStart();
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
            onPermissionsGranted();
        } else {
            requestPermissionsLauncher.launch(toRequest.toArray(new String[0]));
        }
    }

    private void onPermissionsGranted() {
        tvStatus.setText("Recherche de salles...");
        wifiDirectManager.startDiscovery();
    }

    // WifiDirectListener
    @Override
    public void onDevicesDiscovered(List<WifiP2pDevice> devices) {
        Log.d(TAG, "onDevicesDiscovered : " + devices.size());
        discoveredDevices.clear();
        discoveredDevices.addAll(devices);
        roomAdapter.notifyDataSetChanged();

        if (devices.isEmpty()) {
            tvStatus.setText("Aucune salle trouvée — recherche en cours...");
        } else {
            tvStatus.setText(devices.size() + " salle(s) trouvée(s)");
        }
    }

    @Override
    public void onConnectionInfoAvailable(String hostAddress, boolean isGroupOwner) {
        Log.d(TAG, "onConnectionInfoAvailable hostAddress=" + hostAddress);
        wifiDirectManager.stopConnectionPolling();
        tvStatus.setText("Connecté ! Saisissez le PIN");
        pinLayout.setVisibility(View.VISIBLE);

        // Passe les infos du profil à la connexion
        ProfileManager profileManager = new ProfileManager(this);
        PlayerInfo myInfo = new PlayerInfo(
                profileManager.hasProfile() ? profileManager.getUsername() : "Joueur",
                profileManager.getPhotoPath(),
                false
        );

        clientConnection = new ClientConnection(hostAddress, this);
        clientConnection.setPlayerInfo(myInfo);
        clientConnection.connect();
    }

    @Override
    public void onError(String message) {
        Log.d(TAG, "onError: " + message);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // --- ClientListener ---
    @Override
    public void onPinAccepted() {
        tvStatus.setText("PIN accepté ! En attente du lancement...");
        pinLayout.setVisibility(View.GONE);
        waitingLayout.setVisibility(View.VISIBLE);
        etPin.setEnabled(false);
    }

    @Override
    public void onPinRejected() {
        tvStatus.setText("PIN incorrect, réessayez");
        etPin.setText("");
        btnSubmitPin.setEnabled(true);
    }

    @Override
    public void onPlayerListUpdated(List<PlayerInfo> players) {
        waitingPlayers.clear();
        waitingPlayers.addAll(players);
        waitingAdapter.notifyDataSetChanged();
    }

    @Override
    public void onGameStarted() {
        tvStatus.setText("La partie commence !");
        // TODO : lancer l'écran de jeu
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        wifiDirectManager.unregisterReceiver();
        if (clientConnection != null) clientConnection.disconnect();
    }
}
