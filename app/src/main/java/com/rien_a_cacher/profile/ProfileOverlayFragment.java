package com.rien_a_cacher.profile;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.rien_a_cacher.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ProfileOverlayFragment extends Fragment {

    private ImageView ivProfilePhoto;
    private EditText etUsername;
    private ProfileManager profileManager;

    private String currentPhotoPath = "";

    private Uri    cameraUri    = null;
    private File   cameraFile   = null;

    // Galerie
    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        if (isGranted) {
                            launchCamera();
                        } else {
                            Toast.makeText(requireContext(),
                                    "Permission caméra refusée", Toast.LENGTH_SHORT).show();
                        }
                    });

    // Appareil photo
    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(),
                    success -> {
                        if (success && cameraFile != null && cameraFile.exists()) {
                            File dest = getProfilePhotoFile();
                            if (!dest.getParentFile().exists()) dest.getParentFile().mkdirs();

                            try {
                                // Toujours copier, jamais renomme
                                copyFile(cameraFile, dest);
                                cameraFile.delete(); // Nettoie le temp après copie réussie
                                currentPhotoPath = dest.getAbsolutePath();
                                loadPhoto(currentPhotoPath);
                            } catch (IOException e) {
                                Toast.makeText(requireContext(),
                                        "Erreur enregistrement photo", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });

    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) {
                            String saved = savePhotoFromUri(uri);
                            if (saved != null) {
                                currentPhotoPath = saved;
                                loadPhoto(currentPhotoPath);
                            }
                        }
                    });


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile_overlay,
                container, false);

        profileManager = new ProfileManager(requireContext());

        ivProfilePhoto = view.findViewById(R.id.ivProfilePhoto);
        etUsername     = view.findViewById(R.id.etUsername);

        ImageButton btnClose   = view.findViewById(R.id.btnClose);
        Button      btnGallery = view.findViewById(R.id.btnGallery);
        Button      btnCamera  = view.findViewById(R.id.btnCamera);
        Button      btnSave    = view.findViewById(R.id.btnSave);

        //On pré-remplit
        etUsername.setText(profileManager.getUsername());
        currentPhotoPath = profileManager.getPhotoPath();
        loadPhoto(currentPhotoPath);

        btnClose.setOnClickListener(v -> close());

        btnGallery.setOnClickListener(v ->
                galleryLauncher.launch("image/*"));

        btnCamera.setOnClickListener(v ->
                checkCameraPermissionAndLaunch());

        btnSave.setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            if (username.isEmpty()) {
                Toast.makeText(requireContext(),
                        "Entre un pseudo", Toast.LENGTH_SHORT).show();
                return;
            }
            profileManager.save(username, currentPhotoPath);

            // Vide tout le cache Glide pour forcer le rechargement
            Glide.get(requireContext()).clearMemory();
            new Thread(() ->
                    Glide.get(requireContext()).clearDiskCache()
            ).start();

            Toast.makeText(requireContext(),
                    "Profil enregistré !", Toast.LENGTH_SHORT).show();
            close();
        });
        return view;
    }


    // --------------------------------------------------------------------
    // CAMERA
    // --------------------------------------------------------------------

    private void checkCameraPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchCamera() {
        File dir = getProfileDir();
        if (!dir.exists()) dir.mkdirs();

        cameraFile = new File(dir, "camera_temp_" + System.currentTimeMillis() + ".jpg");

        cameraUri = FileProvider.getUriForFile(
                requireContext(),
                requireContext().getPackageName() + ".fileprovider",
                cameraFile
        );
        cameraLauncher.launch(cameraUri);
    }

    // --------------------------------------------------------------------
    // Fichiers
    // --------------------------------------------------------------------


    private File getProfileDir() {
        return new File(requireContext().getFilesDir(), "profile");
    }

    private File getProfilePhotoFile() {
        return new File(getProfileDir(), "profile_photo.jpg");
    }

    private void copyFile(File src, File dest) throws IOException {
        try (java.io.FileInputStream in  = new java.io.FileInputStream(src);
             FileOutputStream        out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int    len;
            while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
        }
    }

    private String savePhotoFromUri(Uri uri) {
        File dir  = getProfileDir();
        File dest = getProfilePhotoFile();
        if (!dir.exists()) dir.mkdirs();

        File temp = new File(dir, "gallery_temp_" + System.currentTimeMillis() + ".jpg");

        try (InputStream     in  = requireContext()
                .getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(temp)) {
            if (in == null) return null;
            byte[] buf = new byte[8192];
            int    len;
            while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        // Copie le temp
        try {
            copyFile(temp, dest);
            temp.delete();
            return dest.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            temp.delete();
            return null;
        }
    }

    // --------------------------------------------------------------------
    // Affichage de la photo
    // --------------------------------------------------------------------

    private void loadPhoto(String path) {
        if (path == null || path.isEmpty()) {
            ivProfilePhoto.setImageResource(R.drawable.ic_profile_placeholder);
            return;
        }
        // skipMemoryCache + NONE pour forcer le rechargement
        // quand on change la photo (même chemin, contenu différent)
        Glide.with(this)
                .load(new File(path))
                .circleCrop()
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .into(ivProfilePhoto);
    }

    private void close() {
        // Notifie MainActivity pour rafraîchire le bouton profil
        if (getActivity() instanceof ProfileUpdateListener) {
            ((ProfileUpdateListener) getActivity()).onProfileUpdated();
        }
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .remove(this)
                .commit();
    }

    public interface ProfileUpdateListener {
        void onProfileUpdated();
    }
}
