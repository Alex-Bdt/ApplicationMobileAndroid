package com.rien_a_cacher.P2P;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientConnection {

    private static final String TAG = "WifiDirect";

    public interface ClientListener {
        void onPinAccepted();
        void onPinRejected();
        void onGameStarted();
        void onError(String message);
    }

    private final String hostAddress;
    private final ClientListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Deux threads : un pour écouter, un pour envoyer
    private final ExecutorService readExecutor  = Executors.newSingleThreadExecutor();
    private final ExecutorService writeExecutor = Executors.newSingleThreadExecutor();

    private PrintWriter out;
    private Socket socket;

    public ClientConnection(String hostAddress, ClientListener listener) {
        this.hostAddress = hostAddress;
        this.listener = listener;
    }

    public void connect() {
        // Thread dédié à la lecture — bloquant, ne jamais y mettre d'écriture
        readExecutor.execute(() -> {
            try {
                Log.d(TAG, "ClientConnection: tentative TCP " + hostAddress + ":" + HostServer.PORT);
                socket = new Socket(hostAddress, HostServer.PORT);
                // PrintWriter sur le writeExecutor sera utilisé pour écrire
                out = new PrintWriter(socket.getOutputStream(), true);
                Log.d(TAG, "ClientConnection: socket TCP ouverte");

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));

                Log.d(TAG, "ClientConnection: en écoute...");
                String raw;
                while ((raw = in.readLine()) != null) {
                    Log.d(TAG, "ClientConnection: message reçu=" + raw);
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
                Log.d(TAG, "ClientConnection: connexion fermée par le host");

            } catch (Exception e) {
                Log.e(TAG, "ClientConnection: erreur=" + e.getMessage());
                mainHandler.post(() -> listener.onError("Connexion perdue : " + e.getMessage()));
            }
        });
    }

    public void submitPin(String pin) {
        // Thread dédié à l'écriture — indépendant du thread de lecture
        writeExecutor.execute(() -> {
            Log.d(TAG, "submitPin: out=" + out + " pin=" + pin);
            if (out != null) {
                String msg = new GameMessage(GameMessage.TYPE_PIN_SUBMIT, pin).toJson();
                Log.d(TAG, "submitPin: envoi=" + msg);
                out.println(msg);
                Log.d(TAG, "submitPin: envoyé ✅");
            } else {
                Log.e(TAG, "submitPin: out est NULL — socket pas encore ouverte");
                mainHandler.post(() ->
                        listener.onError("Connexion pas encore prête, réessayez"));
            }
        });
    }

    public void disconnect() {
        try {
            if (socket != null) socket.close();
        } catch (Exception e) {
            Log.e(TAG, "disconnect erreur=" + e.getMessage());
        }
        readExecutor.shutdown();
        writeExecutor.shutdown();
    }
}
