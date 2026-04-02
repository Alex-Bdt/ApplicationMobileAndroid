package com.rien_a_cacher;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ProfileOverlayFragment extends Fragment {

    private ImageView ivProfilePhoto;
    private EditText etUsername;
    private ProfileManager profileManager;

    private String currentPhotoPath = "";

    // Galerie
    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    String saved = savePhotoFromUri(uri);
                    if (saved != null) {
                        currentPhotoPath = saved;
                        loadPhoto(currentPhotoPath);
                    }
                }
            });

    // Appareil photo
    private Uri cameraUri;
    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && cameraUri != null) {

                    File tempFile = new File(requireContext().getFilesDir() + "/profile/camera_temp.jpg");
                    File finalFile = new File(requireContext().getFilesDir() + "/profile/profile_photo.jpg");

                    if (tempFile.exists()) {
                        tempFile.renameTo(finalFile); //attention a ce petit coquin
                        currentPhotoPath = finalFile.getAbsolutePath();
                        loadPhoto(currentPhotoPath);
                    }
                }
            });

    private ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    openCamera();
                } else {
                    Toast.makeText(requireContext(), "Permission caméra refusée", Toast.LENGTH_SHORT).show();
                }
            });

    private void checkCameraAndOpen() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void openCamera() {
        File photoFile = createTempImageFile();
        if (photoFile != null) {
            cameraUri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    photoFile
            );
            cameraLauncher.launch(cameraUri);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile_overlay, container, false);

        profileManager = new ProfileManager(requireContext());

        ivProfilePhoto = view.findViewById(R.id.ivProfilePhoto);
        etUsername     = view.findViewById(R.id.etUsername);

        ImageButton btnClose   = view.findViewById(R.id.btnClose);
        Button btnGallery = view.findViewById(R.id.btnGallery);
        Button      btnCamera  = view.findViewById(R.id.btnCamera);
        Button      btnSave    = view.findViewById(R.id.btnSave);

        // Remplit si un profil existe
        etUsername.setText(profileManager.getUsername());
        currentPhotoPath = profileManager.getPhotoPath();
        if (!currentPhotoPath.isEmpty()) {
            loadPhoto(currentPhotoPath);
        }

        btnClose.setOnClickListener(v -> close());

        btnGallery.setOnClickListener(v ->
                galleryLauncher.launch("image/*"));

        btnCamera.setOnClickListener(v -> {
            checkCameraAndOpen();

            /*
            Log.d("UWU", "CRAAASH");
            File photoFile = createTempImageFile();
            if (photoFile != null) {
                cameraUri = FileProvider.getUriForFile(
                        requireContext(),
                        requireContext().getPackageName() + ".fileprovider",
                        photoFile);
                cameraLauncher.launch(cameraUri); //ICI
            } */
        });

        btnSave.setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            if (username.isEmpty()) {
                Toast.makeText(requireContext(),
                        "Entre un pseudo", Toast.LENGTH_SHORT).show();
                return;
            }
            profileManager.save(username, currentPhotoPath);
            Toast.makeText(requireContext(),
                    "Profil enregistré !", Toast.LENGTH_SHORT).show();
            close();
        });

        return view;
    }

    private void loadPhoto(String path) {
        Glide.with(this)
                .load(path)
                .circleCrop()
                .placeholder(R.drawable.ic_profile_placeholder)
                .into(ivProfilePhoto);
    }

    private String savePhotoFromUri(Uri uri) {
        try {
            File dir = new File(requireContext().getFilesDir(), "profile");
            if (!dir.exists()) dir.mkdirs();
            File dest = new File(dir, "profile_photo.jpg");

            try (InputStream in = requireContext()
                    .getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(dest)) {
                if (in == null) return null;
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
            }
            return dest.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private File createTempImageFile() {
        try {
            File dir = new File(requireContext().getFilesDir(), "profile");
            if (!dir.exists()) dir.mkdirs();
            return new File(dir, "camera_temp.jpg");
        } catch (Exception e) {
            return null;
        }
    }

    private void close() {
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .remove(this)
                .commit();
    }
}
