package com.wodi.redguotools;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * 配置跨进程同步：模块 App ⇄ 红果。
 *
 * <h3>为什么需要它</h3>
 * 模块 App 与红果是<b>两个 uid、两个私有目录</b>：
 * <ul>
 *   <li>模块 App 写不进 {@code /data/data/com.phoenix.read/shared_prefs/}（无权限）；</li>
 *   <li>红果也读不到模块的（Android 11+ 包可见性，红果清单里没有指向模块的
 *       {@code <queries>}，ContentProvider / bindService / 广播 / Activity 四条路全被静默拦掉）——
 *       这是 v2.x 就实测确认过的结论，见 README「定时关闭 → 直接关屏」。</li>
 * </ul>
 * 唯一不需要 root、也不需要宿主配合的通道是<b>公共存储</b>：
 * 模块 App 用 MediaStore 写一份配置 JSON，红果启动时读回去。
 *
 * <p>之所以这条路走得通：红果声明了 {@code MANAGE_EXTERNAL_STORAGE}
 * （短剧要保存视频，用户本来就会授权），可以直接用 File 读任意公共目录；
 * 而模块 App 用 {@code MediaStore.Files + RELATIVE_PATH} 写入
 * <b>不需要任何存储权限</b>（v3.03 导出日志已经验证过这条路）。
 *
 * <h3>同步方向与时机</h3>
 * <ul>
 *   <li><b>写</b>：模块 App 里改任意配置 → 立刻导出到公共目录；</li>
 *   <li><b>读</b>：红果进程 {@code Config.init()} 时读一次并覆盖到宿主自己的
 *       SharedPreferences，之后所有 hook 读到的都是新值。</li>
 * </ul>
 * 因此模块 App 改完设置后<b>需要重启一次红果</b>（模块 App 首页有「重启红果」按钮）。
 * 红果已在运行时不会被打断，这个取舍换来的是「零权限、零 root」。
 *
 * <p>格式按 SharedPreferences 的三种类型分桶存，导入时按桶还原，
 * 这样 {@code boolean / int / String} 的类型不会在往返中丢失。
 */
final class CfgBridge {

    /** 公共目录下的相对路径（走 MediaStore 写入不需要任何权限）。 */
    private static final String REL_DIR = "Documents/RGTools/";
    private static final String FILE_NAME = "rg_cfg.json";

    private static final String K_BOOL = "b";
    private static final String K_INT = "i";
    private static final String K_STR = "s";

    private CfgBridge() {
    }

    /* ------------------------------------------------------------------ */
    /* 模块 App 侧：导出                                                     */
    /* ------------------------------------------------------------------ */

    /**
     * 把一份 SharedPreferences 整份导出到公共目录。
     *
     * @return 失败时返回原因，便于在面板上显示
     */
    static String export(Context c, SharedPreferences sp) {
        if (c == null || sp == null) {
            return "空配置";
        }
        try {
            JSONObject root = new JSONObject();
            JSONObject b = new JSONObject();
            JSONObject i = new JSONObject();
            JSONObject s = new JSONObject();
            for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
                Object v = e.getValue();
                if (v instanceof Boolean) {
                    b.put(e.getKey(), v);
                } else if (v instanceof Integer) {
                    i.put(e.getKey(), v);
                } else if (v != null) {
                    s.put(e.getKey(), String.valueOf(v));
                }
            }
            root.put(K_BOOL, b);
            root.put(K_INT, i);
            root.put(K_STR, s);
            return write(c, root.toString());
        } catch (Throwable t) {
            return "导出失败：" + t;
        }
    }

    /** 通过 MediaStore 写入（同名文件先查后覆盖，不堆 `rg_cfg (1).json`）。 */
    private static String write(Context c, String text) {
        ContentResolver cr = c.getContentResolver();
        Uri collection = MediaStore.Files.getContentUri("external");
        Uri target = findExisting(cr, collection);
        try {
            if (target == null) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME);
                v.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, REL_DIR);
                target = cr.insert(collection, v);
                if (target == null) {
                    return "公共目录不可写";
                }
            }
            OutputStream os = cr.openOutputStream(target, "wt");
            if (os == null) {
                return "公共目录不可写";
            }
            try {
                os.write(text.getBytes("UTF-8"));
                os.flush();
            } finally {
                os.close();
            }
            return null;
        } catch (Throwable t) {
            return "写入失败：" + t;
        }
    }

    private static Uri findExisting(ContentResolver cr, Uri collection) {
        try {
            String[] proj = {MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME};
            android.database.Cursor cur = cr.query(collection, proj,
                    MediaStore.MediaColumns.DISPLAY_NAME + "=? AND "
                            + MediaStore.MediaColumns.RELATIVE_PATH + "=?",
                    new String[]{FILE_NAME, REL_DIR}, null);
            if (cur != null) {
                try {
                    if (cur.moveToFirst()) {
                        long id = cur.getLong(0);
                        return android.content.ContentUris.withAppendedId(collection, id);
                    }
                } finally {
                    cur.close();
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /* ------------------------------------------------------------------ */
    /* 红果侧：导入                                                         */
    /* ------------------------------------------------------------------ */

    /**
     * 把公共目录里的配置读回来，覆盖到本进程的 SharedPreferences。
     *
     * <p>只在 {@code Config.init()} 里调用一次（宿主进程每次冷启动）。
     * 文件不存在是正常情况（用户从没用过模块 App 改过设置）。
     *
     * @return 导入的条目数；0 表示没有可导入内容
     */
    static int importFrom(Context c, SharedPreferences sp) {
        if (c == null || sp == null) {
            return 0;
        }
        String text = read(c);
        if (TextUtils.isEmpty(text)) {
            return 0;
        }
        try {
            JSONObject root = new JSONObject(text);
            SharedPreferences.Editor ed = sp.edit();
            int n = 0;
            JSONObject b = root.optJSONObject(K_BOOL);
            if (b != null) {
                for (java.util.Iterator<String> it = b.keys(); it.hasNext(); ) {
                    String k = it.next();
                    ed.putBoolean(k, b.getBoolean(k));
                    n++;
                }
            }
            JSONObject i = root.optJSONObject(K_INT);
            if (i != null) {
                for (java.util.Iterator<String> it = i.keys(); it.hasNext(); ) {
                    String k = it.next();
                    ed.putInt(k, i.getInt(k));
                    n++;
                }
            }
            JSONObject s = root.optJSONObject(K_STR);
            if (s != null) {
                for (java.util.Iterator<String> it = s.keys(); it.hasNext(); ) {
                    String k = it.next();
                    ed.putString(k, s.getString(k));
                    n++;
                }
            }
            if (n > 0) {
                // 同步落盘：这份配置马上会被后面所有 hook 读到
                ed.commit();
            }
            return n;
        } catch (Throwable t) {
            UiController.logFile("cfg-bridge: 导入失败 " + t);
            return 0;
        }
    }

    /**
     * 读公共目录里的配置文本。
     *
     * <p>优先走 {@code File} 直读 —— 红果有 {@code MANAGE_EXTERNAL_STORAGE}，
     * 这条最快也不需要 ContentResolver 的查询权限；读不到再退回 MediaStore 查询。
     */
    private static String read(Context c) {
        java.io.File f = new java.io.File(
                android.os.Environment.getExternalStorageDirectory(), REL_DIR + FILE_NAME);
        if (f.exists() && f.length() > 0 && f.length() < 512 * 1024) {
            try {
                InputStream is = new java.io.FileInputStream(f);
                try {
                    return new String(readAll(is), "UTF-8");
                } finally {
                    is.close();
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            Uri u = findExisting(c.getContentResolver(),
                    MediaStore.Files.getContentUri("external"));
            if (u == null) {
                return null;
            }
            InputStream is = c.getContentResolver().openInputStream(u);
            if (is == null) {
                return null;
            }
            try {
                return new String(readAll(is), "UTF-8");
            } finally {
                is.close();
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static byte[] readAll(InputStream is) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) > 0) {
            bo.write(buf, 0, n);
            if (bo.size() > 512 * 1024) {
                break;
            }
        }
        return bo.toByteArray();
    }

    /* ------------------------------------------------------------------ */

    /** 供 UI 显示：公共目录里当前存着多少条配置。 */
    static int countStored(Context c) {
        String t = read(c);
        if (TextUtils.isEmpty(t)) {
            return 0;
        }
        try {
            JSONObject root = new JSONObject(t);
            int n = 0;
            for (String k : new String[]{K_BOOL, K_INT, K_STR}) {
                JSONObject o = root.optJSONObject(k);
                if (o != null) {
                    n += o.length();
                }
            }
            return n;
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
