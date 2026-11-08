package com.gdstudio.teleprompter;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Create / edit one script (title + text). Saves automatically. */
public class EditorActivity extends Activity {

    private Store.Script s;
    private boolean isNew;
    private EditText title, body;

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String id = getIntent().getStringExtra("id");
        s = id == null ? null : Store.get(this, id);
        if (s == null) {
            s = new Store.Script();
            s.id = Store.newId();
            s.title = "";
            s.text = "";
            String f = getIntent().getStringExtra("folder");
            s.folder = f == null ? "" : f;
            s.time = System.currentTimeMillis();
            isNew = true;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout header = new LinearLayout(this);
        header.setBackgroundColor(Color.BLACK);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), 0, dp(4), 0);
        header.addView(icon(R.drawable.ic_back, () -> finish()));
        TextView h = new TextView(this);
        h.setText(isNew ? "New script" : "Edit script");
        h.setTextColor(Color.WHITE);
        h.setTextSize(18);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(h, new LinearLayout.LayoutParams(0, -2, 1f));
        header.addView(icon(R.drawable.ic_play, () -> {
            save();
            if (!body.getText().toString().trim().isEmpty())
                startActivity(new Intent(this, PrompterActivity.class).putExtra("id", s.id));
        }));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(56)));

        title = new EditText(this);
        title.setHint("Title");
        title.setSingleLine(true);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.BLACK);
        title.setHintTextColor(Color.rgb(160, 160, 160));
        title.setPadding(dp(16), dp(16), dp(16), dp(12));
        title.setText(s.title);
        root.addView(title);

        body = new EditText(this);
        body.setHint("Script likhun ba paste korun...");
        body.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        body.setGravity(Gravity.TOP | Gravity.START);
        body.setTextSize(17);
        body.setTextColor(Color.rgb(30, 30, 30));
        body.setHintTextColor(Color.rgb(160, 160, 160));
        body.setBackground(null);
        body.setPadding(dp(16), dp(12), dp(16), dp(16));
        body.setText(s.text);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
        if (isNew) body.requestFocus();
    }

    private ImageView icon(int res, Runnable r) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        iv.setPadding(dp(12), dp(12), dp(12), dp(12));
        iv.setOnClickListener(v -> r.run());
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        return iv;
    }

    private void save() {
        String b = body.getText().toString();
        String t = title.getText().toString().trim();
        if (isNew && b.trim().isEmpty() && t.isEmpty()) return;
        if (t.isEmpty()) {
            String first = b.trim().split("\n")[0].trim();
            t = first.length() > 30 ? first.substring(0, 30) + "…" : first;
            if (t.isEmpty()) t = "Untitled";
            title.setText(t);
        }
        boolean changed = isNew || !b.equals(s.text) || !t.equals(s.title);
        if (!changed) return;
        s.title = t;
        s.text = b;
        s.time = System.currentTimeMillis();
        Store.put(this, s);
        isNew = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
    }
}
