package com.rien_a_cacher.P2P;

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

import com.rien_a_cacher.R;

import java.util.ArrayList;
import java.util.List;

public class JoinActivity extends AppCompatActivity
    implements WifiDirectManager.WifiDirectListener, ClientConnection.ClientListener {

    private static final String TAG = "WifiDirect";

    private TextView tvStatus;
    private RecyclerView rvRooms;
    private LinearLayout pinLayout;
    private EditText etPin;
    private Button btnSubmitPin;

    private WifiDirectManager wifiDirectManager;
    private ClientConnection clientConnection;
    private RoomAdapter roomAdapter;
    private List<WifiP2pDevice> discoveredDevices = new ArrayList<>();


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

        tvStatus     = findViewById(R.id.tvStatus);
        rvRooms      = findViewById(R.id.rvRooms);
        pinLayout    = findViewById(R.id.pinLayout);
        etPin        = findViewById(R.id.etPin);
        btnSubmitPin = findViewById(R.id.btnSubmitPin);

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

        // Soumission du PIN
        btnSubmitPin.setOnClickListener(v -> {
            Log.d(TAG, "btnSubmitPin cliqué !");
            String pinInput = etPin.getText().toString().trim();
            Log.d(TAG, "pinInput=" + pinInput + " length=" + pinInput.length());
            Log.d(TAG, "clientConnection=" + clientConnection);

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
        Log.d(TAG, "pinLayout visibility=" + pinLayout.getVisibility()); // doit être 0
        Log.d(TAG, "btnSubmitPin visibility=" + btnSubmitPin.getVisibility()); // doit être 0
        clientConnection = new ClientConnection(hostAddress, this);
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
        etPin.setEnabled(false);
        btnSubmitPin.setEnabled(false);
    }

    @Override
    public void onPinRejected() {
        tvStatus.setText("PIN incorrect, réessayez");
        etPin.setText("");
        btnSubmitPin.setEnabled(true);
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
