package com.rien_a_cacher.P2P;

import org.json.JSONException;
import org.json.JSONObject;

public class GameMessage {

    public static final String TYPE_PIN_SUBMIT = "PIN_SUBMIT"; // Client → Host
    public static final String TYPE_PIN_OK = "PIN_OK"; // Host → Client
    public static final String TYPE_PIN_FAIL = "PIN_FAIL"; // Host → Client
    public static final String TYPE_START = "START"; // Host → tous

    private final String type;
    private final String payload;

    public GameMessage(String type, String payload) {
        this.type=type;
        this.payload=payload;
    }

    public String getType(){
        return type;
    }

    public String getPayload(){
        return payload;
    }

    public String toJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", type);
            obj.put("payload", payload != null ? payload : "");
            return obj.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    public static GameMessage fromJson(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            return new GameMessage(
                    obj.getString("type"),
                    obj.optString("payload", "")
            );
        } catch (JSONException e) {
            return new GameMessage("UNKNOWN", "");
        }
    }
}
