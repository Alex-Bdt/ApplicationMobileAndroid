package com.rien_a_cacher;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
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

public class GameActivity extends AppCompatActivity {

    private static final int TIMER_SECONDS = 10;
    private static final float SHAKE_THRESHOLD = 12f;

    private ImageView ivPhoto;
    private TextView tvTimer;
    private TextView tvScore;
    private TextView tvCombo;
    private TextView tvLastPoints;
    private Button btnChoice1, btnChoice2, btnChoice3, btnChoice4;
    private Button btnNext;

    private List<GamePhoto> photos;
    private int currentIndex = 0;
    private int correctButton = 0; // index 0-3 du bon bouton

    private ScoreCalculator scoreCalculator = new ScoreCalculator();
    private long questionStartMs = 0;
    private CountDownTimer countDownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        ivPhoto = findViewById(R.id.ivPhoto);
        tvTimer = findViewById(R.id.tvTimer);
        tvScore = findViewById(R.id.tvScore);
        tvCombo = findViewById(R.id.tvCombo);
        tvLastPoints = findViewById(R.id.tvLastPoints);
        btnChoice1 = findViewById(R.id.btnChoice1);
        btnChoice2 = findViewById(R.id.btnChoice2);
        btnChoice3 = findViewById(R.id.btnChoice3);
        btnChoice4 = findViewById(R.id.btnChoice4);
        btnNext = findViewById(R.id.btnNext);

        // Récupère les photos depuis l'intent
        photos = (List<GamePhoto>) getIntent().getSerializableExtra("photos");
        if (photos == null || photos.isEmpty()) {
            finish();
            return;
        }

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
                goToLeaderboard();
            }
        });

        loadQuestion();
    }

    // -----------------------------------------------------------------
    // Question
    // -----------------------------------------------------------------

    private void loadQuestion() {
        btnNext.setVisibility(View.GONE);
        tvLastPoints.setVisibility(View.INVISIBLE);
        resetButtons();
        updateScoreDisplay();

        GamePhoto photo = photos.get(currentIndex);

        Glide.with(this)
                .load(photo.getFilePath())
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .centerCrop()
                .into(ivPhoto);

        // 3 faux + 1 vrai mélangés
        List<String> choices = new ArrayList<>();
        choices.add(GameConfig.SOLO_PLAYER);
        for (String fake : GameConfig.FAKE_PLAYERS) choices.add(fake);
        Collections.shuffle(choices);

        correctButton = choices.indexOf(GameConfig.SOLO_PLAYER);

        Button[] buttons = {btnChoice1, btnChoice2, btnChoice3, btnChoice4};
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].setText(choices.get(i));
        }

        //tvScore.setText("Score : " + score + " / " + photos.size());

        questionStartMs = System.currentTimeMillis();
        startTimer();
    }

    // -----------------------------------------------------------------
    // Timer
    // -----------------------------------------------------------------

    private void startTimer() {
        if (countDownTimer != null) countDownTimer.cancel();

        countDownTimer = new CountDownTimer(TIMER_SECONDS * 1000L, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) (millisUntilFinished / 1000.0);
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
                scoreCalculator.onWrongAnswer();
                revealAnswers(-1);
            }
        }.start();
    }

    // -----------------------------------------------------------------
    // Selection
    // -----------------------------------------------------------------

    private void onChoiceSelected(int selectedIndex) {
        if (countDownTimer != null) countDownTimer.cancel();

        if (selectedIndex == correctButton) {
            long elapsed = System.currentTimeMillis() - questionStartMs;
            int points = scoreCalculator.onCorrectAnswer(elapsed);
            showPointsGained(points);
        } else {
            scoreCalculator.onWrongAnswer();
        }

        revealAnswers(selectedIndex);
        updateScoreDisplay();
    }

    private void showPointsGained(int points) {
        tvLastPoints.setVisibility(View.VISIBLE);
        tvLastPoints.setText("+" + points + " pts");
    }

    private void updateScoreDisplay() {
        tvScore.setText(scoreCalculator.getTotalScore() + " pts");

        int combo = scoreCalculator.getCombo();
        if (combo >= 2) {
            float mult = scoreCalculator.getMultiplier();
            tvCombo.setVisibility(View.VISIBLE);
            tvCombo.setText("🔥 ×" + String.format("%.1f", mult) //🔥
                    + "  combo " + combo);
        } else {
            tvCombo.setVisibility(View.INVISIBLE);
        }
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

        btnNext.setVisibility(View.VISIBLE);
        btnNext.setText(currentIndex == photos.size() - 1
                ? "Voir le classement" : "Image suivante →");
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
    // Classement
    // -----------------------------------------------------------------

    private void goToLeaderboard() {
        if (countDownTimer != null) countDownTimer.cancel();

        Intent intent = new Intent(this, LeaderboardActivity.class);
        intent.putExtra("score_solo", scoreCalculator.getTotalScore());
        // En solo les autres joueurs ont 0
        intent.putExtra("player_name", GameConfig.SOLO_PLAYER);
        startActivity(intent);
        finish();
    }
}
