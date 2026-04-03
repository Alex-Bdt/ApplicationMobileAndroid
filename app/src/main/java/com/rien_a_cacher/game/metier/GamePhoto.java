package com.rien_a_cacher.game.metier;

import android.net.Uri;
import java.io.Serializable;

public class GamePhoto implements Serializable {

    private final String uriString; // Uri n'est pas Serializable → on stocke le String
    private final String filePath;

    public GamePhoto(Uri uri, String filePath) {
        this.uriString = uri.toString();
        this.filePath  = filePath;
    }

    public Uri getUri()         { return Uri.parse(uriString); }
    public String getFilePath() { return filePath; }
}
