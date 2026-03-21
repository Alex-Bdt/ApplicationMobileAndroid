package com.rien_a_cacher.P2P;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.rien_a_cacher.R;

import java.util.ArrayList;
import java.util.List;

public class JoinActivity extends AppCompatActivity
    implements WifiDirectManager.WifiDirectListener, ClientConnection.ClientListener {

    private TextView tvStatus;
    private EditText etPin;
    private Button btnConnect;
    private Button btnSubmitPin;

    private WifiDirectManager wifiDirectManager;
    private ClientConnection clientConnection;
    private List<WifiP2pDevice> discoveredDevices;

    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = !result.containsValue(false);
                if (allGranted) {
                    onPermissionsGranted();
                } else {
                    Toast.makeText(this,
                            "Permissions Wi-Fi refusées, impossible de rejoindre une partie",
                            Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join);

        tvStatus     = findViewById(R.id.tvStatus);
        etPin        = findViewById(R.id.etPin);
        btnConnect   = findViewById(R.id.btnConnect);
        btnSubmitPin = findViewById(R.id.btnSubmitPin);

        wifiDirectManager = new WifiDirectManager(this, this);
        wifiDirectManager.registerReceiver();

        tvStatus.setText("Finding games...");

        btnConnect.setOnClickListener(v -> {
            if (discoveredDevices != null && !discoveredDevices.isEmpty()) {
                wifiDirectManager.connect(discoveredDevices.get(0));
                tvStatus.setText("Connecting...");
            } else {
                Toast.makeText(this, "No game found", Toast.LENGTH_SHORT).show();
            }
        });

        btnSubmitPin.setOnClickListener(v -> {
            String pinInput = etPin.getText().toString().trim();
            if (pinInput.length() == 4 && clientConnection != null) {
                clientConnection.submitPin(pinInput);
            } else {
                Toast.makeText(this, "PIN invalide (4 chiffres requis)", Toast.LENGTH_SHORT).show();
            }
        });

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
        tvStatus.setText("Recherche de parties...");
        wifiDirectManager.startDiscovery();
    }

    // WifiDirectListener
    @Override
    public void onDevicesDiscovered(List<WifiP2pDevice> devices) {
        discoveredDevices = devices;
        tvStatus.setText(devices.size() + " partie(s) trouvée(s)");
        btnConnect.setEnabled(!devices.isEmpty());
    }

    @Override
    public void onConnectionInfoAvailable(String hostAddress, boolean isGroupOwner) {
        tvStatus.setText("Connecté ! Saisissez le PIN communiqué par le host");
        clientConnection = new ClientConnection(hostAddress, this);
        clientConnection.connect();
        etPin.setEnabled(true);
        btnSubmitPin.setEnabled(true);
    }

    @Override
    public void onError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // --- ClientListener ---
    @Override
    public void onPinAccepted() {
        tvStatus.setText("PIN valid ! Waiting...");
        etPin.setEnabled(false);
        btnSubmitPin.setEnabled(false);
    }

    @Override
    public void onPinRejected() {
        tvStatus.setText("PIN incorrect, réessayez");
        etPin.setText("");
    }

    @Override
    public void onGameStarted() {
        tvStatus.setText("Game started !");

        // TODO : lancer l'écran de jeu
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        wifiDirectManager.unregisterReceiver();
        if (clientConnection != null) clientConnection.disconnect();
    }
}
