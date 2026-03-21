package com.rien_a_cacher;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.rien_a_cacher.P2P.HostActivity;
import com.rien_a_cacher.P2P.JoinActivity;

public class MainActivity extends AppCompatActivity {

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    goToGallery();
                } else {
                    Toast.makeText(this, "Permission refusée", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btnGo).setOnClickListener(v -> checkPermissionAndGo());
        findViewById(R.id.btnHost).setOnClickListener(v ->
                startActivity(new Intent(this, HostActivity.class)));
        findViewById(R.id.btnJoin).setOnClickListener(v ->
                startActivity(new Intent(this, JoinActivity.class)));
    }

    private void checkPermissionAndGo() {
        String permission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission = Manifest.permission.READ_MEDIA_IMAGES;
        } else {
            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        }
        if (ContextCompat.checkSelfPermission(this, permission)
                == PackageManager.PERMISSION_GRANTED) {
            goToGallery();
        } else if (shouldShowRequestPermissionRationale(permission)) {
            Toast.makeText(this, "On a besoin d'accéder à vos photos", Toast.LENGTH_LONG).show();
            requestPermissionLauncher.launch(permission);
        } else {
            requestPermissionLauncher.launch(permission);
        }
    }

    private void goToGallery() {
        Intent intent = new Intent(this, GalleryActivity.class);
        startActivity(intent);
    }

}