package com.gdstudio.teleprompter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
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
    private EditText searchBox;
    private boolean sortByName = false;
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
        LinearLayout icons = new LinearLayout(this);
        icons.setGravity(Gravity.CENTER_VERTICAL);
        icons.addView(headIcon(R.drawable.ic_search, v -> toggleSearch()));
        bt = headIcon(R.drawable.ic_bluetooth, v -> startActivity(new Intent(this, RemoteActivity.class)));
        icons.addView(bt);
        header.addView(icons, new FrameLayout.LayoutParams(-2, -1, Gravity.END | Gravity.CENTER_VERTICAL));
        header.addView(headIcon(R.drawable.ic_more, v -> menu()),
                new FrameLayout.LayoutParams(dp(48), dp(52), Gravity.START | Gravity.CENTER_VERTICAL));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(56)));

        searchBox = new EditText(this);
        searchBox.setHint("Script khujun…");
        searchBox.setSingleLine(true);
        searchBox.setTextColor(Color.BLACK);
        searchBox.setVisibility(View.GONE);
        searchBox.setPadding(dp(16), dp(12), dp(16), dp(12));
        searchBox.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { render(); }
            public void afterTextChanged(android.text.Editable e) {}
        });
        root.addView(searchBox, new LinearLayout.LayoutParams(-1, -2));

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
        handleShare(getIntent());
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
        if (sortByName) Collections.sort(all, (a, b) -> a.title.compareToIgnoreCase(b.title));
        else Collections.sort(all, (a, b) -> Long.compare(b.time, a.time));
        String q = searchBox.getText().toString().trim().toLowerCase();
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        int shown = 0;
        for (Store.Script s : all) {
            if (!filter.isEmpty() && !filter.equals(s.folder)) continue;
            if (!q.isEmpty() && !(s.title + "\n" + s.text).toLowerCase().contains(q)) continue;
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
        TextView dev = new TextView(this);
        dev.setText("GD Teleprompter  •  Developer: M. Shahariar");
        dev.setTextColor(Color.rgb(160, 160, 160));
        dev.setTextSize(12);
        dev.setGravity(Gravity.CENTER);
        dev.setPadding(0, dp(24), 0, dp(24));
        list.addView(dev);
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
        d += "   •   " + readTime(s.text);
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

    // ---------- v5: header, menu, import, backup, share ----------

    static final int REQ_IMPORT = 21, REQ_BACKUP_SAVE = 22, REQ_BACKUP_LOAD = 23;

    private ImageView headIcon(int res, View.OnClickListener l) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        iv.setPadding(dp(13), dp(14), dp(13), dp(14));
        iv.setOnClickListener(l);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(52)));
        return iv;
    }

    private void toggleSearch() {
        boolean show = searchBox.getVisibility() != View.VISIBLE;
        searchBox.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) searchBox.requestFocus();
        else searchBox.setText("");
        render();
    }

    private String readTime(String t) {
        int words = (t == null || t.trim().isEmpty()) ? 0 : t.trim().split("\\s+").length;
        int min = Math.round(words / 130f);
        return words + " shobdo · " + (min < 1 ? "<1" : String.valueOf(min)) + " min";
    }

    private void menu() {
        String[] items = {"📥  File import (txt / Word / PDF)", "💾  Backup save korun",
                "♻  Backup theke ferot anun", sortByName ? "↕  Sort: notun age" : "↕  Sort: nam (A-Z)",
                "ⓘ  About"};
        new AlertDialog.Builder(this)
                .setItems(items, (d, w) -> {
                    if (w == 0) pick(REQ_IMPORT, new String[]{"text/plain", "application/pdf",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"});
                    else if (w == 1) {
                        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("application/json");
                        i.putExtra(Intent.EXTRA_TITLE, "GD-Teleprompter-backup-"
                                + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()) + ".json");
                        try {
                            startActivityForResult(i, REQ_BACKUP_SAVE);
                        } catch (Exception e) {
                            toast("File save khola jacche na");
                        }
                    } else if (w == 2) pick(REQ_BACKUP_LOAD, new String[]{"application/json", "text/plain",
                            "application/octet-stream"});
                    else if (w == 3) {
                        sortByName = !sortByName;
                        render();
                    } else about();
                })
                .show();
    }

    private void pick(int req, String[] mimes) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, mimes);
        try {
            startActivityForResult(i, req);
        } catch (Exception e) {
            toast("File picker khola jacche na");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        if (req == REQ_IMPORT) importUri(u);
        else if (req == REQ_BACKUP_SAVE) {
            try (java.io.OutputStream o = getContentResolver().openOutputStream(u)) {
                o.write(Importer.exportJson(this).getBytes("UTF-8"));
                toast("Backup save hoyeche ✓");
            } catch (Exception e) {
                toast("Backup save hoyni");
            }
        } else if (req == REQ_BACKUP_LOAD) {
            try {
                int n = Importer.importJson(this, Importer.txt(this, u));
                toast(n + " ta script ferot ana hoyeche ✓");
                render();
            } catch (Exception e) {
                toast("Backup file thik na");
            }
        }
    }

    private void importUri(Uri u) {
        toast("Import hocche…");
        new Thread(() -> {
            try {
                Importer.Result r = Importer.read(this, u);
                runOnUiThread(() -> saveImported(r.title, r.text));
            } catch (Exception e) {
                runOnUiThread(() -> toast("Import hoyni: " + e.getMessage()));
            }
        }).start();
    }

    private void saveImported(String title, String text) {
        Store.Script s = new Store.Script();
        s.id = Store.newId();
        String t = title == null || title.trim().isEmpty() ? "Imported" : title.trim();
        s.title = t.length() > 40 ? t.substring(0, 40) + "…" : t;
        s.folder = filter;
        s.text = text;
        s.time = System.currentTimeMillis();
        Store.put(this, s);
        render();
        toast("✓ \"" + s.title + "\" add hoyeche");
        startActivity(new Intent(this, EditorActivity.class).putExtra("id", s.id));
    }

    private void handleShare(Intent in) {
        if (in == null) return;
        String act = in.getAction();
        if (Intent.ACTION_SEND.equals(act)) {
            Uri u = in.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u != null) importUri(u);
            else {
                String t = in.getStringExtra(Intent.EXTRA_TEXT);
                if (t != null && !t.trim().isEmpty()) {
                    String sub = in.getStringExtra(Intent.EXTRA_SUBJECT);
                    saveImported(sub != null ? sub : t.trim().split("\n")[0], t);
                }
            }
        } else if (Intent.ACTION_VIEW.equals(act) && in.getData() != null) {
            importUri(in.getData());
        }
        setIntent(new Intent());
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        handleShare(i);
    }

    private void toast(String t) {
        android.widget.Toast.makeText(this, t, android.widget.Toast.LENGTH_SHORT).show();
    }

    private void about() {
        new AlertDialog.Builder(this)
                .setTitle("GD Teleprompter")
                .setMessage("Version 5.0\n\nDeveloper: M. Shahariar\n\n"
                        + "Remote (Feelworld, Bluetooth, phone), voice scroll, Bangla font, "
                        + "PAUSE marker, file import o backup soho teleprompter app.")
                .setPositiveButton("OK", null)
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
