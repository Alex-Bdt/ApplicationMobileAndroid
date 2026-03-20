package com.rien_a_cacher;

import android.content.ContentUris;
import android.database.Cursor;
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

public class GalleryActivity extends AppCompatActivity {

    private static final int PHOTO_COUNT = 10; // Hardcodé mais modifiable
    private RecyclerView recyclerView;
    private LinearLayout loadingLayout;
    private Button btnReload;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);

        recyclerView = findViewById(R.id.recyclerView);
        loadingLayout = findViewById(R.id.loadingLayout);
        btnReload = findViewById(R.id.btnReload);
        btnReload.setOnClickListener(v -> loadPhotos());

        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));

        loadPhotos();
    }

    private void loadPhotos() {
        loadingLayout.setVisibility(View.VISIBLE);
        btnReload.setEnabled(false); // désactive le bouton temporairement

        //Glide.get(this).clearMemory(); // vide le cache du thread UI

        // Thread background : requête MediaStore
        executor.execute(() -> {
            //Glide.get(this).clearDiskCache(); // vide le cache du thraed en back
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
                loadingLayout.setVisibility(View.GONE);
                btnReload.setEnabled(true); // réactive le bouton
                recyclerView.setAdapter(new PhotoAdapter(gamePhotos));
                recyclerView.scrollToPosition(0); // remonte la vue
            });
        });
    }

    // Récupèration de tous les URIs de la galerie
    private List<Uri> fetchAllImagesFromMediaStore() {
        List<Uri> uris = new ArrayList<>();

        String[] projection = { MediaStore.Images.Media._ID };

        Cursor cursor = getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                MediaStore.Images.Media.DATE_ADDED + " DESC"
        );

        if (cursor != null) {
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idColumn);
                Uri uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                );
                uris.add(uri);
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
                    InputStream in = getContentResolver().openInputStream(uri);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        clearTempFolder();
    }

    private void clearTempFolder() {
        File tempDir = new File(getCacheDir(), "game_photos");
        if (tempDir.exists()) {
            for (File file : tempDir.listFiles()) {
                file.delete();
            }
        }
    }
}