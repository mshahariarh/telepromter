package com.gdstudio.teleprompter;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Opening screen with the developer intro, then the script list. */
public class SplashActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean done = false;

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.rgb(14, 16, 24));
        root.setPadding(dp(24), dp(24), dp(24), dp(24));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        box.addView(logo, new LinearLayout.LayoutParams(dp(96), dp(96)));

        TextView name = text("GD Teleprompter", 28, Color.WHITE, true);
        name.setPadding(0, dp(18), 0, dp(4));
        box.addView(name);
        box.addView(text("Remote  •  Voice scroll  •  Bangla fonts", 14, Color.rgb(150, 155, 170), false));

        View gap = new View(this);
        box.addView(gap, new LinearLayout.LayoutParams(1, dp(44)));

        box.addView(text("Created by", 13, Color.rgb(150, 155, 170), false));

        // developer photo in an orange ring
        FrameLayout ring = new FrameLayout(this);
        GradientDrawable rd = new GradientDrawable();
        rd.setShape(GradientDrawable.OVAL);
        rd.setStroke(dp(3), Color.rgb(255, 152, 0));
        ring.setBackground(rd);
        ImageView photo = new ImageView(this);
        photo.setImageResource(R.drawable.dev_photo);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.setClipToOutline(true);
        photo.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline o) {
                o.setOval(0, 0, v.getWidth(), v.getHeight());
            }
        });
        ring.addView(photo, new FrameLayout.LayoutParams(dp(118), dp(118), Gravity.CENTER));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(130), dp(130));
        rlp.topMargin = dp(12);
        rlp.bottomMargin = dp(6);
        box.addView(ring, rlp);
        TextView dev = text("M. Shahariar", 24, Color.rgb(255, 152, 0), true);
        dev.setPadding(0, dp(4), 0, 0);
        box.addView(dev);

        root.addView(box);

        TextView ver = text("Version 5.3", 12, Color.rgb(100, 105, 120), false);
        ver.setPadding(0, dp(40), 0, 0);
        root.addView(ver);

        root.setOnClickListener(v -> openMain());
        setContentView(root);

        box.setAlpha(0f);
        box.setTranslationY(dp(24));
        box.animate().alpha(1f).translationY(0).setDuration(800).start();

        handler.postDelayed(this::openMain, 3200);
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private void openMain() {
        if (done) return;
        done = true;
        handler.removeCallbacksAndMessages(null);
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
