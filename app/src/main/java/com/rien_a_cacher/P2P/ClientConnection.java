package com.rien_a_cacher.P2P;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientConnection {

    public interface ClientListener {
        void onPinAccepted();
        void onPinRejected();
        void onGameStarted();
        void onError(String message);
    }

    private final String hostAddress;
    private final ClientListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private PrintWriter out;

    public ClientConnection(String hostAddress, ClientListener listener) {
        this.hostAddress = hostAddress;
        this.listener = listener;
    }

    public void connect() {
        executor.execute(() -> {
            try {
                Socket socket = new Socket(hostAddress, HostServer.PORT);
                out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));

                String raw;
                while ((raw = in.readLine()) != null) {
                    GameMessage msg = GameMessage.fromJson(raw);

                    switch (msg.getType()) {
                        case GameMessage.TYPE_PIN_OK:
                            mainHandler.post(listener::onPinAccepted);
                            break;
                        case GameMessage.TYPE_PIN_FAIL:
                            mainHandler.post(listener::onPinRejected);
                            break;
                        case GameMessage.TYPE_START:
                            mainHandler.post(listener::onGameStarted);
                            break;
                    }
                }

            } catch (Exception e) {
                mainHandler.post(() -> listener.onError("Connexion perdue : " + e.getMessage()));
            }
        });
    }

    public void submitPin(String pin) {
        executor.execute(() -> {
            if (out != null) {
                out.println(new GameMessage(GameMessage.TYPE_PIN_SUBMIT, pin).toJson());
            }
        });
    }

    public void disconnect() {
        executor.shutdown();
    }
}
