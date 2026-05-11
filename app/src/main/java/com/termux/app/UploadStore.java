package com.termux.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class UploadStore {

    private static final String PREFS = "upload_index";
    private static final String KEY   = "data";
    static final String PENDING = "__pending__";

    private final SharedPreferences mPrefs;

    public UploadStore(Context ctx) {
        mPrefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 写入指定 session 桶（sessionId 可以是 PENDING） */
    public synchronized void addFile(String sessionId, String filename) {
        Map<String, List<String>> all = loadMap();
        List<String> files = all.computeIfAbsent(sessionId, k -> new ArrayList<>());
        if (!files.contains(filename)) files.add(filename);
        saveMap(all);
    }

    /** 把 __pending__ 桶合并到真实 sessionId 桶，然后清空 pending */
    public synchronized void commitPending(String sessionId) {
        Map<String, List<String>> all = loadMap();
        List<String> pending = all.remove(PENDING);
        if (pending != null && !pending.isEmpty()) {
            List<String> dest = all.computeIfAbsent(sessionId, k -> new ArrayList<>());
            for (String f : pending) {
                if (!dest.contains(f)) dest.add(f);
            }
        }
        saveMap(all);
    }

    /** 全量读取，返回 sessionId → 文件名列表 的 Map */
    public synchronized Map<String, List<String>> getAll() {
        return loadMap();
    }

    /** 读取某 session 的文件列表 */
    public synchronized List<String> getFiles(String sessionId) {
        return loadMap().getOrDefault(sessionId, new ArrayList<>());
    }

    /** 删除 session 条目，返回该 session 下所有文件名（供调用方删除 Ubuntu 文件） */
    public synchronized List<String> deleteSession(String sessionId) {
        Map<String, List<String>> all = loadMap();
        List<String> files = all.remove(sessionId);
        saveMap(all);
        return files != null ? files : new ArrayList<>();
    }

    /** 删除单个文件记录 */
    public synchronized void deleteFile(String sessionId, String filename) {
        Map<String, List<String>> all = loadMap();
        List<String> files = all.get(sessionId);
        if (files != null) {
            files.remove(filename);
            if (files.isEmpty()) all.remove(sessionId);
        }
        saveMap(all);
    }

    /** 清空全部记录 */
    public synchronized List<String> clearAll() {
        Map<String, List<String>> all = loadMap();
        List<String> allFiles = new ArrayList<>();
        for (List<String> files : all.values()) allFiles.addAll(files);
        mPrefs.edit().remove(KEY).apply();
        return allFiles;
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private Map<String, List<String>> loadMap() {
        Map<String, List<String>> result = new HashMap<>();
        String raw = mPrefs.getString(KEY, null);
        if (raw == null) return result;
        try {
            JSONObject obj = new JSONObject(raw);
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String sid = keys.next();
                JSONArray arr = obj.getJSONArray(sid);
                List<String> files = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) files.add(arr.getString(i));
                result.put(sid, files);
            }
        } catch (Exception ignored) {}
        return result;
    }

    private void saveMap(Map<String, List<String>> map) {
        try {
            JSONObject obj = new JSONObject();
            for (Map.Entry<String, List<String>> e : map.entrySet()) {
                JSONArray arr = new JSONArray();
                for (String f : e.getValue()) arr.put(f);
                obj.put(e.getKey(), arr);
            }
            mPrefs.edit().putString(KEY, obj.toString()).apply();
        } catch (Exception ignored) {}
    }
}
