package com.rien_a_cacher;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GalleryActivity extends AppCompatActivity implements SensorEventListener {

    private static final int PHOTO_COUNT = 10; // Hardcodé mais modifiable
    private static final float SHAKE_THRESHOLD = 12f; //
    private RecyclerView recyclerView;
    private LinearLayout loadingLayout;
    private Button btnReload;
    private Button btnValidate;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private List<GamePhoto> currentPhotos = new ArrayList<>();

    // Shake
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private float lastX, lastY, lastZ;
    private boolean sensorInitialized = false;
    private boolean loading = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);

        recyclerView = findViewById(R.id.recyclerView);
        loadingLayout = findViewById(R.id.loadingLayout);
        btnReload = findViewById(R.id.btnReload);
        btnValidate   = findViewById(R.id.btnValidate);

        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        btnReload.setOnClickListener(v -> loadPhotos());

        btnValidate.setOnClickListener(v -> {
            if (!currentPhotos.isEmpty()) {
                Intent intent = new Intent(this, GameActivity.class);
                intent.putExtra("photos", (java.io.Serializable) currentPhotos);
                startActivity(intent);
            }
        });

        loadPhotos();
    }

    private void loadPhotos() {
        loading = true;
        loadingLayout.setVisibility(View.VISIBLE);
        btnReload.setEnabled(false);
        btnValidate.setEnabled(false); // désactive les bouton temporairement

        // Thread background : requête MediaStore
        executor.execute(() -> {
            clearTempFolder();

            // 1. Récupère tous les URIs de la galerie
            List<Uri> allUris = fetchAllImagesFromMediaStore();

            // 2. Mélange et prend les N premiers
            Collections.shuffle(allUris);
            List<Uri> selectedUris = allUris.subList(0, Math.min(PHOTO_COUNT, allUris.size()));

            // 3. Copie les fichiers dans le dossier temp + construit les GamePhoto
            List<GamePhoto> gamePhotos = copyToTempAndBuild(selectedUris);

            // 4. Retour UI
            mainHandler.post(() -> {
                currentPhotos = gamePhotos;
                loading = false;
                loadingLayout.setVisibility(View.GONE);
                btnReload.setEnabled(true);
                btnValidate.setEnabled(true);
                recyclerView.setAdapter(new PhotoAdapter(gamePhotos));
                recyclerView.scrollToPosition(0);
            });
        });
    }

    // Récupèration de tous les URIs de la galerie
    private List<Uri> fetchAllImagesFromMediaStore() {
        List<Uri> uris = new ArrayList<>();
        String[] projection = { MediaStore.Images.Media._ID };

        Cursor cursor = getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,null,null,
                MediaStore.Images.Media.DATE_ADDED + " DESC"
        );

        if (cursor != null) {
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idColumn);
                uris.add(ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id));
            }
            cursor.close();
        }
        return uris;
    }

    // Copie les URI dans le cache et retourne les GamePhoto associés
    private List<GamePhoto> copyToTempAndBuild(List<Uri> uris) {
        List<GamePhoto> result = new ArrayList<>();
        // Dossier temp dédié au jeu dans le cache interne de l'app
        File tempDir = new File(getCacheDir(), "game_photos");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        for (int i = 0; i < uris.size(); i++) {
            Uri uri = uris.get(i);
            File destFile = new File(tempDir, "photo_" + i + ".jpg");
            try (
                    InputStream in  = getContentResolver().openInputStream(uri);
                    FileOutputStream out = new FileOutputStream(destFile)
            ) {
                if (in != null) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                    result.add(new GamePhoto(uri, destFile.getAbsolutePath()));
                }
            } catch (Exception e) {
                e.printStackTrace();
                // Photo ignorée si erreur de lecture
            }
        }
        return result;
    }

    private void clearTempFolder() {
        File tempDir = new File(getCacheDir(), "game_photos");
        if (tempDir.exists()) {
            File[] files = tempDir.listFiles();
            if (files != null) for (File f : files) f.delete();
        }
    }

    // Mouvement our reroll
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        if (loading) return;

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        if (!sensorInitialized) {
            lastX = x; lastY = y; lastZ = z;
            sensorInitialized = true;
            return;
        }
        float delta = Math.abs(x - lastX) + Math.abs(y - lastY) + Math.abs(z - lastZ);
        if (delta > SHAKE_THRESHOLD) {
            sensorInitialized = false; // reset pour éviter les double-triggers
            loadPhotos();
        }

        lastX = x; lastY = y; lastZ = z;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        clearTempFolder();
    }
}