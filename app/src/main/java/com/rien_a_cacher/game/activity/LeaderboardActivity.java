package com.rien_a_cacher.game.activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.rien_a_cacher.gallery.GalleryActivity;
import com.rien_a_cacher.R;
import com.rien_a_cacher.game.metier.GameConfig;
import java.util.ArrayList;
import java.util.List;

public class LeaderboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);

        int    soloScore  = getIntent().getIntExtra("score_solo", 0);

        // En solo : joueur réel + 3 joueurs fictifs à 0
        List<PlayerScore> scores = new ArrayList<>();
        for (String player : GameConfig.ALL_PLAYERS) {
            int score = player.equals(GameConfig.SOLO_PLAYER) ? soloScore : 0;
            scores.add(new PlayerScore(player, score));
        }

        // Trie par score décroissant
        scores.sort((a, b) -> b.score - a.score);

        LinearLayout container = findViewById(R.id.leaderboardContainer);
        buildLeaderboard(container, scores);

        Button btnReplay = findViewById(R.id.btnReplay);
        btnReplay.setOnClickListener(v -> {
            // Retourne à GalleryActivity en vidant la back stack
            Intent intent = new Intent(this, GalleryActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });
    }

    private void buildLeaderboard(LinearLayout container, List<PlayerScore> scores) {
        String[] medals = {"🥇", "🥈", "🥉", "4."}; // basé pour 4 !

        for (int i = 0; i < scores.size(); i++) {
            PlayerScore ps = scores.get(i);

            // Ligne de classement
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 16, 0, 16);

            // Médaille / position
            TextView tvMedal = new TextView(this);
            tvMedal.setText(i < medals.length ? medals[i] : (i + 1) + ".");
            tvMedal.setTextSize(22);
            tvMedal.setMinWidth(60);

            // Nom
            TextView tvName = new TextView(this);
            tvName.setText(ps.name);
            tvName.setTextSize(18);
            tvName.setTextColor(0xFFFFFFFF);
            tvName.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            // Score
            TextView tvScore = new TextView(this);
            tvScore.setText(ps.score + " pts");
            tvScore.setTextSize(18);
            tvScore.setTextColor(i == 0
                    ? 0xFF03DAC5   // 1er → teal
                    : 0xFFAAAAAA); // autres → gris

            row.addView(tvMedal);
            row.addView(tvName);
            row.addView(tvScore);
            container.addView(row);

            // Séparateur
            if (i < scores.size() - 1) {
                android.view.View divider = new android.view.View(this);
                divider.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(0xFF333333);
                container.addView(divider);
            }
        }
    }

    // Modèle simple
    private static class PlayerScore {
        String name;
        int    score;
        PlayerScore(String name, int score) {
            this.name = name; this.score = score;
        }
    }

}
