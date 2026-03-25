package com.rien_a_cacher;

import android.net.Uri;

public class GamePhoto {

    private final Uri uri;
    private final String filePath;

    public GamePhoto(Uri uri, String filePath){
        this.uri = uri;
        this.filePath = filePath;
    }

    public Uri getUri() {
        return uri;
    }
    public String getFilePath(){
        return filePath;
    }
}
