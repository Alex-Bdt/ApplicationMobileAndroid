package com.rien_a_cacher.P2P.metier;

import java.io.Serializable;

public class PlayerInfo implements Serializable {
    public String name;
    public String photoPath;
    public boolean isHost;

    public PlayerInfo(String name, String photoPath, boolean isHost) {
        this.name      = name;
        this.photoPath = photoPath;
        this.isHost    = isHost;
    }
}
