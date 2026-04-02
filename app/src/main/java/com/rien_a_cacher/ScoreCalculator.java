package com.rien_a_cacher;

public class ScoreCalculator {

    private static final int    TIMER_SECONDS  = 10;
    private static final int    MAX_POINTS     = 100;
    private static final int    MIN_POINTS     = 10;
    // Palier humain : la 1 première seconde donnent le max
    private static final float  HUMAN_DELAY    = 1f;
    // Coefficient de la courbe exponentielle (plus élevé = plus brutal)
    private static final float  CURVE_FACTOR   = 2f;

    private int   combo        = 0;
    private float multiplier   = 1.0f;
    private int   totalScore   = 0;
    private int   lastQuestion = 0; // points gagnés sur la dernière question

    /**
     * Calcule les points pour une bonne réponse.
     * @param elapsedMs temps écoulé depuis l'affichage de la question en ms
     * @return points gagnés (déjà multipliés)
     */
    public int onCorrectAnswer(long elapsedMs) {
        float elapsedSeconds = elapsedMs / 1000f;

        // Palier humain : toute réponse sous HUMAN_DELAY = score max
        float effectiveTime = Math.max(0, elapsedSeconds - HUMAN_DELAY);
        float maxTime       = TIMER_SECONDS - HUMAN_DELAY;

        // Courbe exponentielle douce : score = MAX * e^(-k * t/maxT)
        // normalisée pour que t=0 → 100 et t=maxT → MIN_POINTS
        float k = (float) Math.log((float) MAX_POINTS / MIN_POINTS) * CURVE_FACTOR;
        float ratio = effectiveTime / maxTime;
        int basePoints = (int) (MAX_POINTS * Math.exp(-k * ratio));
        basePoints = Math.max(MIN_POINTS, Math.min(MAX_POINTS, basePoints));

        // Applique le multiplicateur de combo
        int finalPoints = Math.round(basePoints * multiplier);
        // Met à jour le combo
        combo++;
        if (combo >= 2) {
            // +0.2 par bonne réponse consécutive à partir de la 2ème
            multiplier = 1.0f + (combo - 1) * 0.2f;
        }

        totalScore   += finalPoints;
        lastQuestion  = finalPoints;
        return finalPoints;
    }

    public void onWrongAnswer() {
        combo      = 0;
        multiplier = 1.0f;
        lastQuestion = 0;
    }

    public int getTotalScore() {
        return totalScore;
    }
    public int getCombo() {
        return combo;
    }
    public float getMultiplier() {
        return multiplier;
    }
    public int getLastQuestion() {
        return lastQuestion;
    }

}
