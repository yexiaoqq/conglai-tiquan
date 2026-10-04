package df.root;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements IReporter {

    private static final String TAG = "dfroot";

    private static final String CREATOR_TEXT =
            "聪来提权 · DFRoot 适配版\n\n" +
            "移植 / 适配：yexiaoqq\n" +
            "内核漏洞链：DirtyFrag (CVE-2026-43284)\n" +
            "上游：diabl0w/DFRoot (Apache-2.0)\n\n" +
            "仅供本人自有设备研究学习使用。";

    private static final String THEME_TEXT =
            "选择下方预设颜色，或输入十六进制色值自定义主色 / 主题色。\n" +
            "点击「应用颜色」即时生效，点击「恢复默认主题」还原。";

    private static final String PRINCIPLE_TEXT =
            "1. libexp.so 触发 5.15 内核内存破坏 (DirtyFrag)\n" +
            "2. patch 供应商库文件\n" +
            "3. 加载内嵌 dirtyfrag.ko（vermagic 精确匹配本机）\n" +
            "4. 置全局 SELinux permissive\n" +
            "5. call_usermodehelper 调起 ksud late-load\n" +
            "6. 完成 KernelSU late-load，取得内核级 root\n\n" +
            "全程不触碰 /boot 与启动链，Knox 保持完好。";

    private static final String DEVICE_TEXT =
            "适用机型：Samsung Galaxy S23\n" +
            "型号：SM-S9110 / dm1q（国行 / 港版）\n" +
            "内核：5.15.189-android13-8-3251900-abS9110ZCS8FZI1\n" +
            "KMI：android13-5.15\n" +
            "Bootloader：锁定   Knox：完好\n\n" +
            "版本号：v2.0-md3 (UI 重构版)";

    private static final String SOURCE_TEXT =
            "GitHub 仓库：\n" +
            "github.com/yexiaoqq/conglai-tiquan\n\n" +
            "Release：v2.0-md3\n" +
            "上游 DFRoot：github.com/diabl0w/DFRoot";

    private static final String HIST_FILE = "run-history.json";

    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final Executor mExec = Executors.newSingleThreadExecutor();
    private final Executor mIo = Executors.newSingleThreadExecutor();

    private View homePage, logPage, aboutPage, detailPage, colorPicker, toolbar;
    private ScrollView outputScroll, historyScroll;
    private LinearLayout historyList;
    private View historyEmpty;
    private TextView outputView, detailTitle, detailBody, btnDetailBack;
    private TextView txtRootState, txtRootHint, txtHistoryCount, txtLiveLabel;
    private ProgressBar runProgress;
    private BottomNavigationView bottomNav;
    private Button btnRun, btnApplyColor, btnResetColor;
    private CompoundButton switchBootStart, switchManualSoftReboot, switchAutoSoftReboot;
    private EditText colorHex;

    private final List<RunRecord> mHistory = new ArrayList<RunRecord>();
    private RunRecord mCurrent;

    /** 防止 setSelectedItemId -> listener -> selectTab 的重入递归。 */
    private boolean mNavSync;

    private final SimpleDateFormat mFmt =
            new SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault());

    /** 单条运行记录（最小持久化模型）。 */
    static class RunRecord {
        long startedAt;
        long completedAt;
        int rc = -1;          // 0=SUCCESS, 1/2/3=FAILED, -1=未完成
        String log = "";
        boolean running;
    }

    private int id(String name) {
        return getResources().getIdentifier(name, "id", getPackageName());
    }

    private int layoutId(String name) {
        return getResources().getIdentifier(name, "layout", getPackageName());
    }

    private View fv(String name) {
        return findViewById(id(name));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View root = getLayoutInflater().inflate(layoutId("activity_main"), null);
        setContentView(root);

        toolbar = fv("toolbar");
        homePage = fv("homePage");
        logPage = fv("logPage");
        aboutPage = fv("aboutPage");
        detailPage = fv("detailPage");
        colorPicker = fv("colorPicker");

        btnRun = (Button) fv("btnRun");
        outputView = (TextView) fv("outputView");
        outputScroll = (ScrollView) fv("outputScroll");

        switchBootStart = (CompoundButton) fv("switchBootStart");
        switchManualSoftReboot = (CompoundButton) fv("switchManualSoftReboot");
        switchAutoSoftReboot = (CompoundButton) fv("switchAutoSoftReboot");

        detailTitle = (TextView) fv("detailTitle");
        detailBody = (TextView) fv("detailBody");
        btnDetailBack = (TextView) fv("btnDetailBack");
        colorHex = (EditText) fv("colorHex");
        btnApplyColor = (Button) fv("btnApplyColor");
        btnResetColor = (Button) fv("btnResetColor");

        // ---- 运行记录页新增控件 ----
        historyScroll = (ScrollView) fv("historyScroll");
        historyList = (LinearLayout) fv("historyList");
        historyEmpty = fv("historyEmpty");
        txtHistoryCount = (TextView) fv("txtHistoryCount");
        txtLiveLabel = (TextView) fv("txtLiveLabel");
        runProgress = (ProgressBar) fv("runProgress");

        txtRootState = (TextView) fv("txtRootState");
        txtRootHint = (TextView) fv("txtRootHint");

        bottomNav = (BottomNavigationView) fv("bottomNav");

        // ---- 底部导航 ----
        if (bottomNav != null) {
            bottomNav.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
                public boolean onNavigationItemSelected(MenuItem item) {
                    int iid = item.getItemId();
                    if (iid == id("nav_home")) selectTab(0);
                    else if (iid == id("nav_log")) selectTab(1);
                    else if (iid == id("nav_about")) selectTab(2);
                    return true;
                }
            });
        }

        if (btnDetailBack != null) {
            btnDetailBack.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { detailPage.setVisibility(View.GONE); }
            });
        }

        bindCard("cardCreator", "创作者", CREATOR_TEXT, false);
        bindCard("cardTheme", "主题", THEME_TEXT, true);
        bindCard("cardPrinciple", "操作原理", PRINCIPLE_TEXT, false);
        bindCard("cardDevice", "适用机型 / 版本号", DEVICE_TEXT, false);
        bindCard("cardSource", "来源仓库", SOURCE_TEXT, false);

        bindSwatches("colorRow");
        bindSwatches("colorRow2");
        btnApplyColor.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { applyHex(); }
        });
        btnResetColor.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { clearColor(); }
        });
        applyStoredColor();

        // ---- 运行记录：导出 / 清空 ----
        View btnExport = fv("btnExportHistory");
        if (btnExport != null) {
            btnExport.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { exportHistory(); }
            });
        }
        View btnClear = fv("btnClearHistory");
        if (btnClear != null) {
            btnClear.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { clearHistory(); }
            });
        }

        // ---- 加载历史 ----
        loadHistory();
        renderHistory();

        btnRun.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!btnRun.isEnabled()) return;
                btnRun.setEnabled(false);
                outputView.setText("");
                startNewRecord();
                selectTab(1);
                if (runProgress != null) runProgress.setVisibility(View.VISIBLE);
                final boolean softReboot = switchManualSoftReboot.isChecked();
                mExec.execute(new Runnable() {
                    public void run() { runExploit(softReboot); }
                });
            }
        });

        final ComponentName bootReceiver = new ComponentName(this, BootReceiver.class);
        int state = getPackageManager().getComponentEnabledSetting(bootReceiver);
        final boolean bootEnabled = (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        switchBootStart.setChecked(bootEnabled);
        switchBootStart.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton btn, boolean checked) {
                getPackageManager().setComponentEnabledSetting(bootReceiver,
                        checked ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
                switchAutoSoftReboot.setEnabled(checked);
            }
        });

        Context dp = createDeviceProtectedStorageContext();
        SharedPreferences sp = dp.getSharedPreferences("dfroot", 0);
        boolean autoSoftReboot = sp.getBoolean("auto_soft_reboot", true);
        switchAutoSoftReboot.setChecked(autoSoftReboot);
        switchAutoSoftReboot.setEnabled(bootEnabled);
        switchAutoSoftReboot.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton btn, boolean checked) {
                createDeviceProtectedStorageContext().getSharedPreferences("dfroot", 0)
                        .edit().putBoolean("auto_soft_reboot", checked).apply();
            }
        });

        refreshRootState();
        selectTab(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRootState();
    }

    private void selectTab(int idx) {
        if (homePage != null) homePage.setVisibility(idx == 0 ? View.VISIBLE : View.GONE);
        if (logPage != null) logPage.setVisibility(idx == 1 ? View.VISIBLE : View.GONE);
        if (aboutPage != null) aboutPage.setVisibility(idx == 2 ? View.VISIBLE : View.GONE);
        if (detailPage != null) detailPage.setVisibility(View.GONE);
        if (bottomNav != null && !mNavSync) {
            int target = id(idx == 0 ? "nav_home" : idx == 1 ? "nav_log" : "nav_about");
            if (target != 0 && bottomNav.getSelectedItemId() != target) {
                mNavSync = true;
                try { bottomNav.setSelectedItemId(target); }
                finally { mNavSync = false; }
            }
        }
        if (idx == 1) renderHistory();
    }

    // ==================== root 状态检测 ====================

    private boolean isRooted() {
        return new File("/dev/df").exists();
    }

    private void refreshRootState() {
        if (txtRootState == null) return;
        boolean rooted = isRooted();
        txtRootState.setText(rooted ? "已获得内核级 root" : "未检测到 root");
        if (txtRootHint != null) {
            txtRootHint.setText(rooted
                    ? "内核守护标记存在 · KernelSU late-load 已生效"
                    : "点击下方「开始提权」尝试获取内核级 root");
        }
        txtRootState.setTextColor(rooted ? 0xFF006E1C : 0xFFBA1A1A);
    }

    // ==================== 运行记录持久化 ====================

    private File histFile() {
        return new File(getFilesDir(), HIST_FILE);
    }

    private static byte[] readAll(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        } finally {
            in.close();
        }
    }

    private void loadHistory() {
        mHistory.clear();
        File f = histFile();
        if (!f.exists()) return;
        try {
            JSONArray arr = new JSONArray(new String(readAll(f), "UTF-8"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                RunRecord r = new RunRecord();
                r.startedAt = o.optLong("startedAt");
                r.completedAt = o.optLong("completedAt");
                r.rc = o.optInt("rc", -1);
                r.log = o.optString("log", "");
                r.running = o.optBoolean("running", false);
                if (r.running) {
                    // 上次运行未正常收尾（多半是软重启把进程带走了）
                    r.running = false;
                    if (r.completedAt == 0) r.completedAt = System.currentTimeMillis();
                    r.log = r.log + "\n[中断] 应用未正常收尾，可能已被软重启终止。\n";
                }
                mHistory.add(r);
            }
        } catch (Exception e) {
            Log.e(TAG, "loadHistory failed", e);
        }
    }

    private void saveHistory() {
        try {
            JSONArray arr = new JSONArray();
            for (int i = 0; i < mHistory.size(); i++) {
                RunRecord r = mHistory.get(i);
                JSONObject o = new JSONObject();
                o.put("startedAt", r.startedAt);
                o.put("completedAt", r.completedAt);
                o.put("rc", r.rc);
                o.put("log", r.log);
                o.put("running", r.running);
                arr.put(o);
            }
            File f = histFile();
            File tmp = new File(getFilesDir(), HIST_FILE + ".tmp");
            FileOutputStream fos = new FileOutputStream(tmp);
            try {
                fos.write(arr.toString().getBytes("UTF-8"));
                fos.flush();
                fos.getFD().sync();
            } finally {
                fos.close();
            }
            if (!tmp.renameTo(f)) {
                tmp.delete();
            }
        } catch (Exception e) {
            Log.e(TAG, "saveHistory failed", e);
        }
    }

    private void startNewRecord() {
        RunRecord r = new RunRecord();
        r.startedAt = System.currentTimeMillis();
        r.running = true;
        r.rc = -1;
        mCurrent = r;
        mHistory.add(0, r);
        mIo.execute(new Runnable() { public void run() { saveHistory(); } });
        renderHistory();
    }

    private void renderHistory() {
        if (historyList == null) return;
        historyList.removeAllViews();
        int n = mHistory.size();
        if (txtHistoryCount != null) txtHistoryCount.setText(n + " 条");
        if (historyEmpty != null) historyEmpty.setVisibility(n == 0 ? View.VISIBLE : View.GONE);
        if (historyScroll != null) historyScroll.setVisibility(n == 0 ? View.GONE : View.VISIBLE);

        LayoutInflater inf = getLayoutInflater();
        for (int i = 0; i < n; i++) {
            final RunRecord r = mHistory.get(i);
            View item = inf.inflate(layoutId("cl_history_item"), historyList, false);
            ImageView icon = (ImageView) item.findViewById(id("itemIcon"));
            TextView title = (TextView) item.findViewById(id("itemTitle"));
            TextView time = (TextView) item.findViewById(id("itemTime"));
            TextView preview = (TextView) item.findViewById(id("itemPreview"));

            String status = r.running ? "运行中" : (r.rc == 0 ? "成功" : "失败");
            if (title != null) title.setText("提权任务 · " + status);
            if (time != null) time.setText(mFmt.format(new Date(r.startedAt)));
            if (preview != null) preview.setText(firstLine(r.log));
            if (icon != null) {
                int tint = r.running ? 0xFF6750A4 : (r.rc == 0 ? 0xFF006E1C : 0xFFBA1A1A);
                icon.setColorFilter(tint);
            }
            item.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { showRecordDetail(r); }
            });
            historyList.addView(item);
        }
    }

    private void showRecordDetail(RunRecord r) {
        if (detailPage == null) return;
        String status = r.running ? "运行中" : (r.rc == 0 ? "成功" : "失败");
        if (detailTitle != null) {
            detailTitle.setText(mFmt.format(new Date(r.startedAt)) + " · " + status);
        }
        if (detailBody != null) {
            String body = (r.log == null || r.log.length() == 0) ? "（本次运行没有输出）" : r.log;
            detailBody.setText(body);
        }
        if (colorPicker != null) colorPicker.setVisibility(View.GONE);
        detailPage.setVisibility(View.VISIBLE);
    }

    private String firstLine(String log) {
        if (log == null) return "";
        String[] lines = log.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String s = lines[i].trim();
            if (s.length() > 0) {
                return s.length() > 140 ? s.substring(0, 140) + "…" : s;
            }
        }
        return "（无输出）";
    }

    private void exportHistory() {
        if (mHistory.isEmpty()) {
            Toast.makeText(this, "暂无记录可复制", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("聪来提权 · 运行记录（").append(mHistory.size()).append(" 条）\n");
        for (int i = 0; i < mHistory.size(); i++) {
            RunRecord r = mHistory.get(i);
            String status = r.running ? "运行中" : (r.rc == 0 ? "成功" : "失败");
            sb.append("\n────────\n");
            sb.append("[").append(status).append("] ")
              .append(mFmt.format(new Date(r.startedAt))).append("\n");
            sb.append(r.log == null ? "" : r.log);
            if (!sb.toString().endsWith("\n")) sb.append("\n");
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("聪来提权运行记录", sb.toString()));
            Toast.makeText(this, "已复制 " + mHistory.size() + " 条记录", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearHistory() {
        if (mHistory.isEmpty()) {
            Toast.makeText(this, "暂无记录", Toast.LENGTH_SHORT).show();
            return;
        }
        mHistory.clear();
        mCurrent = null;
        mIo.execute(new Runnable() { public void run() { saveHistory(); } });
        renderHistory();
        Toast.makeText(this, "已清空运行记录", Toast.LENGTH_SHORT).show();
    }

    // ==================== 主题 ====================

    private void bindCard(String cardId, final String title, final String body, final boolean showColor) {
        View card = fv(cardId);
        if (card == null) return;
        card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                detailTitle.setText(title);
                detailBody.setText(body);
                colorPicker.setVisibility(showColor ? View.VISIBLE : View.GONE);
                detailPage.setVisibility(View.VISIBLE);
            }
        });
    }

    private void bindSwatches(String rowId) {
        View v = fv(rowId);
        if (!(v instanceof ViewGroup)) return;
        ViewGroup row = (ViewGroup) v;
        for (int i = 0; i < row.getChildCount(); i++) {
            View sw = row.getChildAt(i);
            Object tag = sw.getTag();
            if (tag == null) continue;
            final int color = parseColor(tag.toString());
            if (color == 0) continue;
            sw.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { storeAndApply(color); }
            });
        }
    }

    private int parseColor(String s) {
        try { return Color.parseColor(s); } catch (Exception e) { return 0; }
    }

    private void applyHex() {
        String s = colorHex.getText().toString().trim();
        if (s.length() == 0) {
            Toast.makeText(this, "请输入颜色", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!s.startsWith("#")) s = "#" + s;
        try {
            int c = Color.parseColor(s);
            storeAndApply(c);
            Toast.makeText(this, "已应用 " + s, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "颜色格式无效", Toast.LENGTH_SHORT).show();
        }
    }

    private void storeAndApply(int color) {
        createDeviceProtectedStorageContext().getSharedPreferences("dfroot", 0)
                .edit().putInt("theme_color", color).apply();
        tint(color);
    }

    private void tint(int color) {
        if (color == 0) return;
        ColorStateList csl = ColorStateList.valueOf(color);
        if (toolbar != null) toolbar.setBackgroundColor(color);
        if (btnRun != null) btnRun.setBackgroundTintList(csl);
        if (btnApplyColor != null) btnApplyColor.setBackgroundTintList(csl);
    }

    private void applyStoredColor() {
        int c = createDeviceProtectedStorageContext().getSharedPreferences("dfroot", 0)
                .getInt("theme_color", 0);
        if (c != 0) tint(c);
    }

    private void clearColor() {
        createDeviceProtectedStorageContext().getSharedPreferences("dfroot", 0)
                .edit().remove("theme_color").apply();
        Toast.makeText(this, "已恢复默认主题", Toast.LENGTH_SHORT).show();
        recreate();
    }

    // ==================== 提权执行 ====================

    private void runExploit(final boolean softReboot) {
        int rc;
        try {
            rc = ExploitRunner.run(this, this, softReboot);
        } catch (Exception e) {
            Log.e(TAG, "exploit exception", e);
            report("\nexception: " + e + "\n");
            rc = 3;
        }
        final int frc = rc;
        String msg;
        if (rc == 0) msg = "DFRoot: SUCCESS";
        else if (rc == 1) msg = "DFRoot FAILED: ksud exited with error";
        else if (rc == 2) msg = "DFRoot FAILED: check logs";
        else msg = "DFRoot FAILED: failed to patch files";
        report("\n" + msg + "\n");
        final String fmsg = msg;
        mMain.post(new Runnable() {
            public void run() {
                if (mCurrent != null) {
                    mCurrent.running = false;
                    mCurrent.rc = frc;
                    mCurrent.completedAt = System.currentTimeMillis();
                }
                mIo.execute(new Runnable() { public void run() { saveHistory(); } });
                renderHistory();
                if (runProgress != null) runProgress.setVisibility(View.GONE);
                refreshRootState();
                btnRun.setEnabled(true);
                Toast.makeText(MainActivity.this, fmsg, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void report(final String msg) {
        Log.i(TAG, msg.trim());
        mMain.post(new Runnable() {
            public void run() {
                if (mCurrent != null) mCurrent.log = mCurrent.log + msg;
                if (outputView != null) outputView.append(msg);
                if (outputScroll != null) {
                    outputScroll.post(new Runnable() {
                        public void run() { outputScroll.fullScroll(0x82); }
                    });
                }
                // 将日志落盘，防止软重启把本次记录带走
                mIo.execute(new Runnable() { public void run() { saveHistory(); } });
            }
        });
    }
}
