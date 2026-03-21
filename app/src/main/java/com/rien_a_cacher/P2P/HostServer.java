package com.rien_a_cacher.P2P;

import android.os.Handler;
import android.os.Looper;

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
        void onPlayerJoined(String playerName);
        void onError(String message);
    }

    private final String pin;
    private final HostServerListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<PrintWriter> connectedClients = new ArrayList<>();
    private ServerSocket serverSocket;
    private boolean running = false;

    public HostServer(String pin, HostServerListener listener) {
        this.pin = pin;
        this.listener = listener;
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
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            // Attend directement la soumission du PIN (pas d'envoi préalable)
            String raw = in.readLine();
            if (raw == null) return;

            GameMessage msg = GameMessage.fromJson(raw);

            if (GameMessage.TYPE_PIN_SUBMIT.equals(msg.getType())
                    && pin.equals(msg.getPayload())) {

                out.println(new GameMessage(GameMessage.TYPE_PIN_OK, "").toJson());

                synchronized (connectedClients) {
                    connectedClients.add(out);
                }

                mainHandler.post(() ->
                        listener.onPlayerJoined("Joueur " + connectedClients.size())
                );

                // Maintient la connexion ouverte jusqu'au START
                in.readLine();

            } else {
                out.println(new GameMessage(GameMessage.TYPE_PIN_FAIL, "").toJson());
                socket.close();
            }

        } catch (Exception e) {
            mainHandler.post(() -> listener.onError("Erreur client : " + e.getMessage()));
        }
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
