package com.rien_a_cacher;

import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GameActivity extends AppCompatActivity implements SensorEventListener {

    private static final int TIMER_SECONDS  = 10;
    private static final float SHAKE_THRESHOLD = 12f;
    private static final String SOLO_PLAYER = "Moi";
    private static final String[] FAKE_PLAYERS = {"Alex", "Kirby", "Fabie"}; // Pour le solo

    private ImageView ivPhoto;
    private TextView tvTimer;
    private TextView tvScore;
    private Button btnChoice1, btnChoice2, btnChoice3, btnChoice4;
    private Button btnNext;

    private List<GamePhoto> photos;
    private int currentIndex = 0;
    private int score = 0;
    private int correctButton = 0; // index 0-3 du bon bouton

    private CountDownTimer countDownTimer;

    // Shake
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private float lastX, lastY, lastZ;
    private boolean sensorInitialized = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        ivPhoto   = findViewById(R.id.ivPhoto);
        tvTimer   = findViewById(R.id.tvTimer);
        tvScore   = findViewById(R.id.tvScore);
        btnChoice1 = findViewById(R.id.btnChoice1);
        btnChoice2 = findViewById(R.id.btnChoice2);
        btnChoice3 = findViewById(R.id.btnChoice3);
        btnChoice4 = findViewById(R.id.btnChoice4);
        btnNext   = findViewById(R.id.btnNext);

        // Récupère les photos depuis l'intent
        photos = (List<GamePhoto>) getIntent().getSerializableExtra("photos");
        if (photos == null || photos.isEmpty()) {
            finish();
            return;
        }

        // Detecteur de mouvement
        sensorManager  = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer  = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // Listeners boutons choix
        btnChoice1.setOnClickListener(v -> onChoiceSelected(0));
        btnChoice2.setOnClickListener(v -> onChoiceSelected(1));
        btnChoice3.setOnClickListener(v -> onChoiceSelected(2));
        btnChoice4.setOnClickListener(v -> onChoiceSelected(3));

        btnNext.setOnClickListener(v -> {
            currentIndex++;
            if (currentIndex < photos.size()) {
                loadQuestion();
            } else {
                showEndScreen();
            }
        });

        loadQuestion();
    }

    // -----------------------------------------------------------------
    // Question
    // -----------------------------------------------------------------

    private void loadQuestion() {
        btnNext.setVisibility(View.GONE);
        resetButtons();

        GamePhoto photo = photos.get(currentIndex);

        Glide.with(this)
                .load(photo.getFilePath())
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .centerCrop()
                .into(ivPhoto);

        // Génère les 4 choix : 3 faux + 1 vrai mélangés
        List<String> choices = new ArrayList<>();
        choices.add(SOLO_PLAYER); // le bon
        for (String fake : FAKE_PLAYERS) choices.add(fake);
        Collections.shuffle(choices);

        correctButton = choices.indexOf(SOLO_PLAYER);

        Button[] buttons = {btnChoice1, btnChoice2, btnChoice3, btnChoice4};
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].setText(choices.get(i));
        }

        tvScore.setText("Score : " + score + " / " + photos.size());

        startTimer();
    }

    // -----------------------------------------------------------------
    // Timer
    // -----------------------------------------------------------------

    private void startTimer() {
        if (countDownTimer != null) countDownTimer.cancel();

        countDownTimer = new CountDownTimer(TIMER_SECONDS * 1000L, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) (millisUntilFinished / 1000);
                tvTimer.setText(secondsLeft + "s");
                // Rouge quand il reste 3s ou moins
                tvTimer.setTextColor(secondsLeft <= 3
                        ? Color.parseColor("#FF5252")
                        : Color.WHITE);
            }

            @Override
            public void onFinish() {
                tvTimer.setText("0s");
                tvTimer.setTextColor(Color.parseColor("#FF5252"));
                // Temps écoulé = mauvaise réponse
                revealAnswers(-1);
            }
        }.start();
    }

    // -----------------------------------------------------------------
    // Selection
    // -----------------------------------------------------------------

    private void onChoiceSelected(int selectedIndex) {
        if (countDownTimer != null) countDownTimer.cancel();
        if (selectedIndex == correctButton) score++;
        revealAnswers(selectedIndex);
    }

    private void revealAnswers(int selectedIndex) {
        Button[] buttons = {btnChoice1, btnChoice2, btnChoice3, btnChoice4};

        for (int i = 0; i < buttons.length; i++) {
            buttons[i].setEnabled(false);

            if (i == correctButton) {
                // Bonne réponse → vert
                buttons[i].setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(
                                Color.parseColor("#4CAF50")));
            } else if (i == selectedIndex) {
                // Mauvais choix sélectionné → rouge
                buttons[i].setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(
                                Color.parseColor("#F44336")));
            } else {
                // Non sélectionné → gris
                buttons[i].setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(
                                Color.parseColor("#555555")));
            }
        }

        tvScore.setText("Score : " + score + " / " + photos.size());
        btnNext.setVisibility(View.VISIBLE);

        // Dernière image → bouton libellé différent
        if (currentIndex == photos.size() - 1) {
            btnNext.setText("Voir les résultats");
        } else {
            btnNext.setText("Image suivante →");
        }
    }

    private void resetButtons() {
        Button[] buttons = {btnChoice1, btnChoice2, btnChoice3, btnChoice4};
        for (Button b : buttons) {
            b.setEnabled(true);
            b.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            Color.parseColor("#6200EE")));
        }
    }

    // -----------------------------------------------------------------
    // Ecran fin
    // -----------------------------------------------------------------

    private void showEndScreen() {
        if (countDownTimer != null) countDownTimer.cancel();
        setContentView(R.layout.activity_game_end);

        TextView tvFinalScore = findViewById(R.id.tvFinalScore);
        Button   btnReplay    = findViewById(R.id.btnReplay);

        tvFinalScore.setText(score + " / " + photos.size());

        btnReplay.setOnClickListener(v -> finish()); // retour à GalleryActivity
    }

    // -----------------------------------------------------------------
    // Détection mouvement
    // -----------------------------------------------------------------

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        if (!sensorInitialized) {
            lastX = x; lastY = y; lastZ = z;
            sensorInitialized = true;
            return;
        }
        float delta = Math.abs(x - lastX) + Math.abs(y - lastY) + Math.abs(z - lastZ);
        if (delta > SHAKE_THRESHOLD) {
            // Retourne à GalleryActivity pour reroll
            finish();
        }

        lastX = x; lastY = y; lastZ = z;
    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL);
    }
    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
        if (countDownTimer != null) countDownTimer.cancel();
    }

}
