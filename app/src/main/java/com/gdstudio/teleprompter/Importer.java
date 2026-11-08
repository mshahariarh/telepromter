package com.gdstudio.teleprompter;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Import scripts from .txt / .docx / .pdf, and backup / restore all scripts as JSON. */
public class Importer {

    static class Result {
        String title, text;
    }

    static String name(Context c, Uri u) {
        String n = null;
        try (Cursor cur = c.getContentResolver().query(u, null, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                int i = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) n = cur.getString(i);
            }
        } catch (Exception ignored) {
        }
        if (n == null) n = u.getLastPathSegment();
        return n == null ? "Imported" : n;
    }

    static Result read(Context c, Uri u) throws Exception {
        String n = name(c, u);
        String low = n.toLowerCase();
        String mime = c.getContentResolver().getType(u);
        if (mime == null) mime = "";
        Result r = new Result();
        r.title = n.contains(".") ? n.substring(0, n.lastIndexOf('.')) : n;
        if (low.endsWith(".docx") || mime.contains("wordprocessingml")) r.text = docx(c, u);
        else if (low.endsWith(".pdf") || mime.equals("application/pdf")) r.text = pdf(c, u);
        else r.text = txt(c, u);
        if (r.text == null || r.text.trim().isEmpty()) throw new IOException("File e kono lekha paoa jayni");
        return r;
    }

    static byte[] bytes(Context c, Uri u) throws IOException {
        try (InputStream in = c.getContentResolver().openInputStream(u)) {
            if (in == null) throw new IOException("File khola jacche na");
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int k;
            while ((k = in.read(b)) > 0) o.write(b, 0, k);
            return o.toByteArray();
        }
    }

    static String txt(Context c, Uri u) throws IOException {
        String s = new String(bytes(c, u), "UTF-8");
        if (s.startsWith("﻿")) s = s.substring(1);
        return s;
    }

    static String docx(Context c, Uri u) throws IOException {
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(bytes(c, u)))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (e.getName().equals("word/document.xml")) {
                    ByteArrayOutputStream o = new ByteArrayOutputStream();
                    byte[] b = new byte[8192];
                    int k;
                    while ((k = z.read(b)) > 0) o.write(b, 0, k);
                    return docxText(new String(o.toByteArray(), "UTF-8"));
                }
            }
        }
        throw new IOException("Word file e lekha paoa jayni");
    }

    static String docxText(String xml) {
        StringBuilder sb = new StringBuilder();
        int i = 0, n = xml.length();
        while (i < n) {
            int lt = xml.indexOf('<', i);
            if (lt < 0) break;
            int gt = xml.indexOf('>', lt);
            if (gt < 0) break;
            String tag = xml.substring(lt + 1, gt);
            if (tag.startsWith("w:t") && (tag.length() == 3 || tag.charAt(3) == ' ')) {
                int end = xml.indexOf("</w:t>", gt);
                if (end < 0) break;
                sb.append(unescape(xml.substring(gt + 1, end)));
                i = end + 6;
                continue;
            } else if (tag.equals("/w:p")) {
                sb.append('\n');
            } else if (tag.startsWith("w:tab") && tag.endsWith("/")) {
                sb.append('\t');
            } else if ((tag.startsWith("w:br") || tag.startsWith("w:cr")) && tag.endsWith("/")) {
                sb.append('\n');
            }
            i = gt + 1;
        }
        return sb.toString().trim();
    }

    private static String unescape(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&amp;", "&");
    }

    static String pdf(Context c, Uri u) throws IOException {
        PDFBoxResourceLoader.init(c.getApplicationContext());
        try (InputStream in = c.getContentResolver().openInputStream(u);
             PDDocument d = PDDocument.load(in)) {
            return new PDFTextStripper().getText(d).trim();
        }
    }

    // ---------- backup ----------

    static String exportJson(Context c) {
        try {
            JSONObject o = new JSONObject();
            o.put("app", "GD Teleprompter");
            o.put("version", 1);
            o.put("scripts", new JSONArray(Store.prefs(c).getString("scripts", "[]")));
            o.put("folders", Store.prefs(c).getString("folders", ""));
            return o.toString(2);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** Adds scripts from a backup (existing ones are kept). Returns how many were added. */
    static int importJson(Context c, String json) throws Exception {
        JSONObject o = new JSONObject(json);
        JSONArray a = o.getJSONArray("scripts");
        List<Store.Script> cur = Store.all(c);
        Set<String> ids = new HashSet<>();
        for (Store.Script s : cur) ids.add(s.id);
        int added = 0;
        for (int i = 0; i < a.length(); i++) {
            JSONObject j = a.getJSONObject(i);
            String id = j.optString("id", Store.newId());
            if (ids.contains(id)) continue;
            Store.Script s = new Store.Script();
            s.id = id;
            s.title = j.optString("title");
            s.folder = j.optString("folder");
            s.text = j.optString("text");
            s.time = j.optLong("time", System.currentTimeMillis());
            cur.add(s);
            ids.add(id);
            added++;
        }
        Store.saveAll(c, cur);
        for (String f : o.optString("folders", "").split("\n"))
            if (!f.trim().isEmpty()) Store.addFolder(c, f.trim());
        return added;
    }
}
