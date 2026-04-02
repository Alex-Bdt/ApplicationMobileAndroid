package com.rien_a_cacher;

import android.content.Context;
import android.content.SharedPreferences;

public class ProfileManager {

    private static final String PREFS_NAME = "profile_perfs";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PHOTO = "photo_path";

    private final SharedPreferences prefs;

    public ProfileManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getUsername() {
        return prefs.getString(KEY_USERNAME, "");
    }

    public String getPhotoPath() {
        return prefs.getString(KEY_PHOTO, "");
    }

    public void save(String username, String photoPath) {
        prefs.edit()
                .putString(KEY_USERNAME, username)
                .putString(KEY_PHOTO, photoPath)
                .apply();
    }

    public boolean hasProfile() {
        return !getUsername().isEmpty();
    }
}
