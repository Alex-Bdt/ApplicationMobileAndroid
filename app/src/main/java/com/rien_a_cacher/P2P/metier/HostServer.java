package com.rien_a_cacher.P2P.metier;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HostServer {

    static final int PORT = 8888;

    public interface HostServerListener {
        void onPlayerJoined(PlayerInfo player);
        void onError(String message);
    }

    private final String pin;
    private final HostServerListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<PrintWriter> connectedClients = new ArrayList<>();
    private final List<PlayerInfo>  connectedPlayers = new ArrayList<>();

    private ServerSocket serverSocket;
    private boolean running = false;

    // Les infos de l'host (pour les envoyer aux clients)
    private PlayerInfo hostInfo;

    public HostServer(String pin, HostServerListener listener) {
        this.pin      = pin;
        this.listener = listener;
    }

    public void setHostInfo(PlayerInfo info) {
        this.hostInfo = info;
    }

    public void start() {
        running = true;
        executor.execute(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                while (running) {
                    Socket clientSocket = serverSocket.accept();
                    executor.execute(() -> handleClient(clientSocket));
                }
            } catch (Exception e) {
                if (running) {
                    mainHandler.post(() -> listener.onError("Erreur serveur : " + e.getMessage()));
                }
            }
        });
    }

    private void handleClient(Socket socket) {
        try {
            BufferedReader in  = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            PrintWriter    out = new PrintWriter(socket.getOutputStream(), true);

            // Étape 1 : PIN
            String raw = in.readLine();
            if (raw == null) return;
            GameMessage msg = GameMessage.fromJson(raw);

            if (!GameMessage.TYPE_PIN_SUBMIT.equals(msg.getType())
                    || !pin.equals(msg.getPayload())) {
                out.println(new GameMessage(GameMessage.TYPE_PIN_FAIL, "").toJson());
                socket.close();
                return;
            }

            out.println(new GameMessage(GameMessage.TYPE_PIN_OK, "").toJson());

            // Étape 2 : reçoit les infos du joueur (nom + photo)
            String infoRaw = in.readLine();
            if (infoRaw == null) return;
            GameMessage infoMsg = GameMessage.fromJson(infoRaw);

            PlayerInfo newPlayer = null;
            if (GameMessage.TYPE_PLAYER_INFO.equals(infoMsg.getType())) {
                JSONObject json = new JSONObject(infoMsg.getPayload());
                newPlayer = new PlayerInfo(
                        json.optString("name", "Joueur"),
                        json.optString("photo", ""),
                        false
                );
            }

            synchronized (connectedClients) {
                connectedClients.add(out);
                connectedPlayers.add(newPlayer);
            }

            // Notifie l'interface de l'host
            PlayerInfo finalPlayer = newPlayer;
            mainHandler.post(() -> listener.onPlayerJoined(finalPlayer));
            // Envoie la liste complète à tous les clients
            broadcastPlayerList();
            // Garde la connexion ouverte jusqu'au START
            in.readLine();

        } catch (Exception e) {
            mainHandler.post(() ->
                    listener.onError("Erreur client : " + e.getMessage()));
        }
    }

    private void broadcastPlayerList() {
        executor.execute(() -> {
            try {
                JSONArray array = new JSONArray();

                // Le host en premier
                if (hostInfo != null) {
                    JSONObject h = new JSONObject();
                    h.put("name", hostInfo.name);
                    h.put("photo", hostInfo.photoPath);
                    h.put("isHost", true);
                    array.put(h);
                }
                synchronized (connectedClients) {
                    for (PlayerInfo p : connectedPlayers) {
                        JSONObject o = new JSONObject();
                        o.put("name", p.name);
                        o.put("photo", p.photoPath);
                        o.put("isHost", false);
                        array.put(o);
                    }
                    String msg = new GameMessage(
                            GameMessage.TYPE_PLAYER_LIST, array.toString()).toJson();
                    for (PrintWriter client : connectedClients) {
                        client.println(msg);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void broadcastStart() {
        executor.execute(() -> {
            synchronized (connectedClients) {
                String startMsg = new GameMessage(GameMessage.TYPE_START, "").toJson();
                for (PrintWriter client : connectedClients) {
                    client.println(startMsg);
                }
            }
        });
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception e) { /* ignoré */ }
        executor.shutdown();
    }

}
