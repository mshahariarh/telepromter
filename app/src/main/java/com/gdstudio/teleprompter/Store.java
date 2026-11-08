package com.gdstudio.teleprompter;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Saves all scripts ("My Lines") and folders in SharedPreferences as JSON. */
public class Store {

    public static class Script {
        public String id, title, folder, text;
        public long time;
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
    }

    public static List<Script> all(Context c) {
        List<Script> list = new ArrayList<>();
        SharedPreferences p = prefs(c);
        String raw = p.getString("scripts", null);
        if (raw == null) {
            // migrate the single script from version 1/2
            String old = p.getString("script", "");
            if (!old.trim().isEmpty()) {
                Script s = new Script();
                s.id = newId();
                s.title = "Script 1";
                s.folder = "";
                s.text = old;
                s.time = System.currentTimeMillis();
                list.add(s);
                saveAll(c, list);
            }
            return list;
        }
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Script s = new Script();
                s.id = o.optString("id");
                s.title = o.optString("title");
                s.folder = o.optString("folder");
                s.text = o.optString("text");
                s.time = o.optLong("time");
                list.add(s);
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static void saveAll(Context c, List<Script> list) {
        JSONArray a = new JSONArray();
        try {
            for (Script s : list) {
                JSONObject o = new JSONObject();
                o.put("id", s.id);
                o.put("title", s.title);
                o.put("folder", s.folder);
                o.put("text", s.text);
                o.put("time", s.time);
                a.put(o);
            }
        } catch (Exception ignored) {
        }
        prefs(c).edit().putString("scripts", a.toString()).apply();
    }

    public static Script get(Context c, String id) {
        for (Script s : all(c)) if (s.id.equals(id)) return s;
        return null;
    }

    public static void put(Context c, Script s) {
        List<Script> l = all(c);
        boolean found = false;
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).id.equals(s.id)) {
                l.set(i, s);
                found = true;
            }
        }
        if (!found) l.add(0, s);
        saveAll(c, l);
    }

    public static void delete(Context c, String id) {
        List<Script> l = all(c);
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).id.equals(id)) l.remove(i);
        saveAll(c, l);
    }

    public static List<String> folders(Context c) {
        List<String> f = new ArrayList<>();
        for (String x : prefs(c).getString("folders", "").split("\n"))
            if (!x.trim().isEmpty() && !f.contains(x)) f.add(x);
        for (Script s : all(c))
            if (s.folder != null && !s.folder.isEmpty() && !f.contains(s.folder)) f.add(s.folder);
        return f;
    }

    public static void addFolder(Context c, String name) {
        List<String> f = folders(c);
        if (!f.contains(name)) f.add(name);
        prefs(c).edit().putString("folders", TextUtils.join("\n", f)).apply();
    }

    public static String newId() {
        return Long.toString(System.currentTimeMillis(), 36)
                + Integer.toString((int) (Math.random() * 1000), 36);
    }
}
