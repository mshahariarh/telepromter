package com.gdstudio.teleprompter;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    static final String PREFS = "prompter";
    static final String K_SCRIPT = "script";
    static final String K_FONT = "font";
    static final String K_SPEED = "speed";
    static final String K_MIRROR = "mirror";

    private EditText script;
    private SharedPreferences prefs;

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(20, 24, 38));
        root.setPadding(dp(16), dp(24), dp(16), dp(16));

        TextView title = new TextView(this);
        title.setText("GD Teleprompter");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Script likhun ba paste korun");
        hint.setTextColor(Color.rgb(170, 176, 196));
        hint.setPadding(0, dp(4), 0, dp(10));
        root.addView(hint);

        script = new EditText(this);
        script.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        script.setGravity(Gravity.TOP | Gravity.START);
        script.setTextColor(Color.WHITE);
        script.setHintTextColor(Color.rgb(120, 126, 146));
        script.setHint("Ekhane apnar script...");
        script.setBackgroundColor(Color.rgb(32, 37, 56));
        script.setPadding(dp(12), dp(12), dp(12), dp(12));
        script.setText(prefs.getString(K_SCRIPT, ""));
        root.addView(script, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button start = new Button(this);
        start.setText("START");
        start.setTextSize(18);
        start.setTypeface(Typeface.DEFAULT_BOLD);
        start.setTextColor(Color.rgb(20, 24, 38));
        start.setBackgroundColor(Color.rgb(255, 196, 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        lp.topMargin = dp(8);
        root.addView(start, lp);

        TextView keys = new TextView(this);
        keys.setText("Remote: Play/Pause = Volume Up / Space / Enter / OK\n"
                + "Pichone = Volume Down / Page Up / Up   •   Samne = Page Down / Down\n"
                + "Speed: Right = beshi, Left = kom   •   Screen tap = Play/Pause\n"
                + "Font, colour, spacing, mirror, speed — shob ⚙ button e (prompter screen)");
        keys.setTextColor(Color.rgb(140, 146, 166));
        keys.setTextSize(11);
        keys.setPadding(0, dp(8), 0, 0);
        root.addView(keys);

        start.setOnClickListener(v -> {
            save();
            startActivity(new Intent(this, PrompterActivity.class));
        });

        setContentView(root);
    }

    private void save() {
        prefs.edit()
                .putString(K_SCRIPT, script.getText().toString())
                .apply();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // script may have been edited inside the prompter
        script.setText(prefs.getString(K_SCRIPT, ""));
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
    }
}
