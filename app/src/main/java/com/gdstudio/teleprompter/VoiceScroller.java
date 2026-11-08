package com.gdstudio.teleprompter;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Voice scroll: listens continuously and moves the script to the word being read.
 * Works with Bangla (bn-BD) and English through the phone's speech recognizer.
 */
public class VoiceScroller implements RecognitionListener {

    interface Target {
        void voiceGoTo(int charOffset);
        void voiceState(String text, boolean on);
    }

    private final Context ctx;
    private final Target target;
    private final Handler h = new Handler(Looper.getMainLooper());
    private SpeechRecognizer sr;
    private boolean active = false;
    private String lang = "bn-BD";
    private String[] words = new String[0];
    private int[] offs = new int[0];
    private int cur = 0;

    VoiceScroller(Context c, Target t) {
        ctx = c;
        target = t;
    }

    boolean isActive() { return active; }

    void setText(String text) {
        ArrayList<String> w = new ArrayList<>();
        ArrayList<Integer> o = new ArrayList<>();
        split(text, w, o);
        words = w.toArray(new String[0]);
        offs = new int[o.size()];
        for (int k = 0; k < offs.length; k++) offs[k] = o.get(k);
        cur = 0;
    }

    /** Re-sync after manual scrolling: start matching from this character offset. */
    void setPositionFromOffset(int off) {
        int k = 0;
        while (k < offs.length && offs[k] < off) k++;
        cur = Math.max(0, Math.min(k, offs.length - 1));
    }

    private static void split(String text, ArrayList<String> w, ArrayList<Integer> o) {
        int i = 0, n = text.length();
        while (i < n) {
            while (i < n && !isWordChar(text.charAt(i))) i++;
            int s = i;
            while (i < n && isWordChar(text.charAt(i))) i++;
            if (i > s) {
                w.add(norm(text.substring(s, i)));
                if (o != null) o.add(s);
            }
        }
    }

    static boolean isWordChar(char c) {
        int t = Character.getType(c);
        return Character.isLetterOrDigit(c) || t == Character.NON_SPACING_MARK
                || t == Character.COMBINING_SPACING_MARK || c == '‌' || c == '‍';
    }

    static String norm(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFC).toLowerCase(Locale.ROOT)
                .replace("‌", "").replace("‍", "");
    }

    // ---------- start / stop ----------

    void start(String language) {
        lang = language;
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            target.voiceState("Voice recognition not available — update the Google app", false);
            return;
        }
        stopInternal();
        sr = SpeechRecognizer.createSpeechRecognizer(ctx);
        sr.setRecognitionListener(this);
        active = true;
        target.voiceState("🎤 Voice scroll ON — start reading", true);
        listen();
    }

    void stop() {
        boolean was = active;
        active = false;
        stopInternal();
        if (was) target.voiceState("🎤 Voice scroll OFF", false);
    }

    private void stopInternal() {
        h.removeCallbacksAndMessages(null);
        if (sr != null) {
            try {
                sr.cancel();
                sr.destroy();
            } catch (Exception ignored) {
            }
            sr = null;
        }
    }

    private void listen() {
        if (!active || sr == null) return;
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.getPackageName());
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L);
        i.putExtra("android.speech.extra.DICTATION_MODE", true);
        try {
            sr.startListening(i);
        } catch (Exception e) {
            h.postDelayed(this::listen, 500);
        }
    }

    private void again(long delay) {
        if (!active) return;
        h.postDelayed(() -> {
            if (active && sr != null) {
                try {
                    sr.cancel();
                } catch (Exception ignored) {
                }
                listen();
            }
        }, delay);
    }

    // ---------- matching ----------

    private void handle(Bundle b) {
        ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (r == null || r.isEmpty() || words.length == 0) return;
        for (String alt : r) if (match(alt)) return;
    }

    private boolean match(String heard) {
        ArrayList<String> hw = new ArrayList<>();
        split(heard, hw, null);
        if (hw.isEmpty()) return false;
        int tail = Math.min(4, hw.size());
        int from = Math.max(0, cur - 2);
        int to = Math.min(words.length - 1, cur + (tail <= 1 ? 8 : 50));
        int bestJ = -1, bestScore = 0;
        for (int j = from; j <= to; j++) {
            int score = 0;
            for (int m = 0; m < tail; m++) {
                int wi = j - m, hi = hw.size() - 1 - m;
                if (wi < 0) break;
                if (similar(words[wi], hw.get(hi))) score++;
            }
            if (score > bestScore) {
                bestScore = score;
                bestJ = j;
            }
        }
        int need = tail >= 2 ? 2 : 1;
        if (tail == 1 && hw.get(0).length() < 3) return false;
        boolean anywhere = false;
        if ((bestJ < 0 || bestScore < need) && tail >= 3) {
            // lost (e.g. after scrolling): look through the whole script for a strong match
            int best2 = -1, score2 = 0;
            for (int j = 0; j < words.length; j++) {
                int score = 0;
                for (int m = 0; m < tail; m++) {
                    int wi = j - m, hi = hw.size() - 1 - m;
                    if (wi < 0) break;
                    if (similar(words[wi], hw.get(hi))) score++;
                }
                if (score > score2 || (score == score2 && score > 0
                        && Math.abs(j - cur) < Math.abs(best2 - cur))) {
                    score2 = score;
                    best2 = j;
                }
            }
            if (score2 >= 3) {
                bestJ = best2;
                bestScore = score2;
                anywhere = true;
            }
        }
        if (bestJ < 0 || bestScore < need) return false;
        if (bestJ >= cur || anywhere) {
            cur = bestJ;
            final int off = offs[bestJ];
            h.post(() -> target.voiceGoTo(off));
        }
        return true;
    }

    static boolean similar(String a, String b) {
        if (a.equals(b)) return true;
        if (a.length() >= 3 && b.length() >= 3) {
            if (a.startsWith(b) || b.startsWith(a)) return true;
            return lev(a, b) <= (Math.max(a.length(), b.length()) >= 6 ? 2 : 1);
        }
        return false;
    }

    private static int lev(String a, String b) {
        int[] prev = new int[b.length() + 1], now = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            now[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int c = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                now[j] = Math.min(Math.min(now[j - 1] + 1, prev[j] + 1), prev[j - 1] + c);
            }
            int[] t = prev; prev = now; now = t;
        }
        return prev[b.length()];
    }

    // ---------- RecognitionListener ----------

    @Override public void onResults(Bundle b) { handle(b); again(50); }
    @Override public void onPartialResults(Bundle b) { handle(b); }

    @Override
    public void onError(int e) {
        if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            active = false;
            target.voiceState("Microphone permission needed", false);
            return;
        }
        again(e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ? 600 : 150);
    }

    @Override public void onReadyForSpeech(Bundle b) {}
    @Override public void onBeginningOfSpeech() {}
    @Override public void onRmsChanged(float v) {}
    @Override public void onBufferReceived(byte[] b) {}
    @Override public void onEndOfSpeech() {}
    @Override public void onEvent(int t, Bundle b) {}
}
