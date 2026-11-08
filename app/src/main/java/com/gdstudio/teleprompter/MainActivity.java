package com.gdstudio.teleprompter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** "My Lines" — list of all saved scripts. */
public class MainActivity extends Activity {

    static final String PREFS = "prompter";
    static final String K_FONT = "font";
    static final String K_SPEED = "speed";
    static final String K_MIRROR = "mirror";

    private LinearLayout list;
    private TextView folderBtn;
    private ImageView bt;
    private String filter = ""; // "" = all folders

    interface StrCb { void on(String s); }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(250, 250, 250));

        // Header
        FrameLayout header = new FrameLayout(this);
        header.setBackgroundColor(Color.BLACK);
        TextView t = new TextView(this);
        t.setText("My Lines");
        t.setTextColor(Color.WHITE);
        t.setTextSize(20);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(t, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        bt = new ImageView(this);
        bt.setImageResource(R.drawable.ic_bluetooth);
        bt.setPadding(dp(14), dp(14), dp(14), dp(14));
        bt.setOnClickListener(v -> startActivity(new Intent(this, RemoteActivity.class)));
        header.addView(bt, new FrameLayout.LayoutParams(dp(52), dp(52),
                Gravity.END | Gravity.CENTER_VERTICAL));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(56)));

        // List
        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Bottom bar
        LinearLayout bottom = new LinearLayout(this);
        bottom.setBackgroundColor(Color.BLACK);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(dp(16), dp(12), dp(16), dp(12));
        folderBtn = new TextView(this);
        folderBtn.setTextColor(Color.WHITE);
        folderBtn.setTextSize(16);
        folderBtn.setGravity(Gravity.CENTER);
        GradientDrawable outline = new GradientDrawable();
        outline.setStroke(dp(1.5f), Color.WHITE);
        outline.setCornerRadius(dp(10));
        folderBtn.setBackground(outline);
        folderBtn.setOnClickListener(v -> pickFilter());
        bottom.addView(folderBtn, new LinearLayout.LayoutParams(0, dp(48), 1f));
        ImageView add = new ImageView(this);
        add.setImageResource(R.drawable.ic_add);
        add.setColorFilter(Color.BLACK);
        GradientDrawable white = new GradientDrawable();
        white.setColor(Color.WHITE);
        white.setCornerRadius(dp(8));
        add.setBackground(white);
        add.setPadding(dp(12), dp(12), dp(12), dp(12));
        add.setOnClickListener(v -> startActivity(new Intent(this, EditorActivity.class)
                .putExtra("folder", filter)));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(56), dp(52));
        alp.leftMargin = dp(14);
        bottom.addView(add, alp);
        root.addView(bottom);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        list.removeAllViews();
        // green Bluetooth icon when a remote is connected
        boolean remoteOn = BleRemote.get(this).isConnected() || !Keys.remotes().isEmpty();
        bt.setColorFilter(remoteOn ? Color.rgb(0, 230, 118) : Color.WHITE);
        folderBtn.setText(filter.isEmpty() ? "All" : "📁 " + filter);
        List<Store.Script> all = Store.all(this);
        Collections.sort(all, (a, b) -> Long.compare(b.time, a.time));
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        int shown = 0;
        for (Store.Script s : all) {
            if (!filter.isEmpty() && !filter.equals(s.folder)) continue;
            shown++;
            list.addView(row(s, f));
        }
        if (shown == 0) {
            TextView e = new TextView(this);
            e.setText("Kono script nei\n\n＋ chepe notun script banan");
            e.setTextColor(Color.GRAY);
            e.setTextSize(16);
            e.setGravity(Gravity.CENTER);
            e.setPadding(dp(24), dp(80), dp(24), dp(24));
            list.addView(e);
        }
    }

    private View row(Store.Script s, SimpleDateFormat f) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundColor(Color.WHITE);
        row.setPadding(dp(20), dp(14), dp(12), 0);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(s.title == null || s.title.isEmpty() ? "Untitled" : s.title);
        title.setTextColor(Color.BLACK);
        title.setTextSize(19);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(title);
        TextView date = new TextView(this);
        String d = f.format(new Date(s.time));
        if (s.folder != null && !s.folder.isEmpty()) d += "   •   📁 " + s.folder;
        date.setText(d);
        date.setTextColor(Color.GRAY);
        date.setTextSize(12);
        col.addView(date);
        top.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(small(R.drawable.ic_folder, v -> chooseFolder(s)));
        top.addView(small(R.drawable.ic_edit, v -> edit(s)));
        row.addView(top);

        TextView pv = new TextView(this);
        pv.setText(s.text == null ? "" : s.text.trim());
        pv.setMaxLines(2);
        pv.setEllipsize(TextUtils.TruncateAt.END);
        pv.setTextColor(Color.rgb(40, 40, 40));
        pv.setTextSize(15);
        pv.setPadding(0, dp(10), dp(8), dp(14));
        row.addView(pv);

        View div = new View(this);
        div.setBackgroundColor(Color.rgb(220, 220, 220));
        row.addView(div, new LinearLayout.LayoutParams(-1, dp(1)));

        row.setOnClickListener(v -> play(s));
        row.setOnLongClickListener(v -> {
            options(s);
            return true;
        });
        return row;
    }

    private ImageView small(int res, View.OnClickListener l) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        iv.setColorFilter(Color.BLACK);
        iv.setPadding(dp(10), dp(10), dp(10), dp(10));
        iv.setOnClickListener(l);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        return iv;
    }

    private void play(Store.Script s) {
        startActivity(new Intent(this, PrompterActivity.class).putExtra("id", s.id));
    }

    private void edit(Store.Script s) {
        startActivity(new Intent(this, EditorActivity.class).putExtra("id", s.id));
    }

    private void options(Store.Script s) {
        String[] items = {"▶  Play", "✎  Edit", "⧉  Duplicate", "🗑  Delete"};
        new AlertDialog.Builder(this)
                .setTitle(s.title)
                .setItems(items, (dlg, w) -> {
                    if (w == 0) play(s);
                    else if (w == 1) edit(s);
                    else if (w == 2) {
                        Store.Script c = new Store.Script();
                        c.id = Store.newId();
                        c.title = s.title + " (copy)";
                        c.folder = s.folder;
                        c.text = s.text;
                        c.time = System.currentTimeMillis();
                        Store.put(this, c);
                        render();
                    } else {
                        new AlertDialog.Builder(this)
                                .setTitle("Delete korben?")
                                .setMessage("\"" + s.title + "\" muche jabe.")
                                .setPositiveButton("Delete", (a, b) -> {
                                    Store.delete(this, s.id);
                                    render();
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                })
                .show();
    }

    private void pickFilter() {
        List<String> fl = Store.folders(this);
        String[] items = new String[fl.size() + 1];
        items[0] = "All";
        for (int i = 0; i < fl.size(); i++) items[i + 1] = "📁 " + fl.get(i);
        new AlertDialog.Builder(this)
                .setTitle("Folder")
                .setItems(items, (d, w) -> {
                    filter = w == 0 ? "" : fl.get(w - 1);
                    render();
                })
                .show();
    }

    private void chooseFolder(Store.Script s) {
        List<String> fl = Store.folders(this);
        String[] items = new String[fl.size() + 2];
        items[0] = "No folder";
        for (int i = 0; i < fl.size(); i++) items[i + 1] = "📁 " + fl.get(i);
        items[fl.size() + 1] = "＋ New folder";
        new AlertDialog.Builder(this)
                .setTitle("Folder e rakhun")
                .setItems(items, (d, w) -> {
                    if (w == 0) s.folder = "";
                    else if (w <= fl.size()) s.folder = fl.get(w - 1);
                    else {
                        newFolder(name -> {
                            s.folder = name;
                            Store.put(this, s);
                            render();
                        });
                        return;
                    }
                    Store.put(this, s);
                    render();
                })
                .show();
    }

    private void newFolder(StrCb cb) {
        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        et.setHint("Folder name");
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(et);
        new AlertDialog.Builder(this)
                .setTitle("New folder")
                .setView(box)
                .setPositiveButton("Create", (d, w) -> {
                    String n = et.getText().toString().trim();
                    if (!n.isEmpty()) {
                        Store.addFolder(this, n);
                        cb.on(n);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
