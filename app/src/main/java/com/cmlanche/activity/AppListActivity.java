package com.cmlanche.activity;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.media.projection.MediaProjectionManager;

import com.alibaba.fastjson.JSON;
import com.cmlanche.application.MyApplication;
import com.cmlanche.common.SPService;
import com.cmlanche.core.service.MyAccessbilityService;
import com.cmlanche.core.utils.AccessibilityUtils;
import com.cmlanche.floatwindow.PermissionUtil;
import com.cmlanche.jixieshou.R;
import com.cmlanche.model.AppInfo;
import com.cmlanche.model.TaskInfo;
import com.cmlanche.ocrcore.ScreenCaptureManager;
import com.cmlanche.ocrcore.ScreenCaptureService;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class AppListActivity extends AppCompatActivity {

    private ListView listView;
    private List<AppInfo> appInfos = new ArrayList<>();
    private AppListAdapter appListAdapter;
    private MaterialButton refreshBtn;
    private AppInfo selectedAppInfo = null;  // 保存选中的应用
    private View contentView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_list);

        findViewById(R.id.backImg).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        appListAdapter = new AppListAdapter(this, appInfos);
        listView = findViewById(R.id.appListView);
        listView.setAdapter(appListAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                AppInfo info = appListAdapter.getItem(i);
                startAutoTask(info);
            }
        });

        refreshBtn = findViewById(R.id.refreshBtn);
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadRunningApps();
            }
        });

        loadRunningApps();

        contentView = findViewById(android.R.id.content);
        MyApplication.getAppInstance().startWallpaperCycle(contentView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        contentView = findViewById(android.R.id.content);
        MyApplication.getAppInstance().startWallpaperCycle(contentView);
    }

    @Override
    protected void onPause() {
        super.onPause();
        MyApplication.getAppInstance().stopWallpaperCycle();
    }

    private void loadRunningApps() {
        appInfos.clear();
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        PackageManager pm = getPackageManager();

        // 方式1：尝试获取正在运行的进程（Android 10 及以下有效）
        List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
        if (processes != null) {
            for (ActivityManager.RunningAppProcessInfo process : processes) {
                if (process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                        || process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) {
                    for (String pkg : process.pkgList) {
                        addApp(pm, pkg);
                    }
                }
            }
        }

        // 方式2：通过任务列表获取（兼容旧版）
        List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(100);
        if (tasks != null) {
            for (ActivityManager.RunningTaskInfo task : tasks) {
                if (task.topActivity != null) {
                    addApp(pm, task.topActivity.getPackageName());
                }
            }
        }

        // 方式3：Android 11+ 限制下，两种方式都拿不到结果，则列出所有已安装应用
        if (appInfos.isEmpty()) {
            List<ApplicationInfo> installedApps = pm.getInstalledApplications(
                    PackageManager.MATCH_ALL);
            if (installedApps != null) {
                for (ApplicationInfo ai : installedApps) {
                    if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0
                            && !ai.packageName.equals(getPackageName())) {
                        addApp(pm, ai.packageName);
                    }
                }
            }
        }

        Collections.sort(appInfos, new Comparator<AppInfo>() {
            @Override
            public int compare(AppInfo o1, AppInfo o2) {
                String n1 = o1.getName() != null ? o1.getName() : "";
                String n2 = o2.getName() != null ? o2.getName() : "";
                return n1.compareTo(n2);
            }
        });

        appListAdapter.notifyDataSetChanged();

        if (appInfos.isEmpty()) {
            Toast.makeText(this, "没有找到已安装的应用，请授予权限后重试", Toast.LENGTH_SHORT).show();
        } else if (processes == null || processes.isEmpty()) {
            Toast.makeText(this, "当前为Android 11+，已列出全部应用供选择", Toast.LENGTH_SHORT).show();
        }
    }

    /** 添加一个非系统应用（去重） */
    private void addApp(PackageManager pm, String pkg) {
        if (containsPkg(appInfos, pkg) || pkg.equals(getPackageName())) return;
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) return;
            AppInfo info = new AppInfo();
            info.setPkgName(pkg);
            info.setName(pm.getApplicationLabel(ai).toString());
            info.setAppName(info.getName());
            info.setFree(true);
            info.setPeriod(1);
            appInfos.add(info);
        } catch (PackageManager.NameNotFoundException ignored) {
        }
    }

    private boolean containsPkg(List<AppInfo> list, String pkg) {
        for (AppInfo info : list) {
            if (info.getPkgName().equals(pkg)) {
                return true;
            }
        }
        return false;
    }

    private void startAutoTask(AppInfo appInfo) {
        if (!PermissionUtil.checkFloatPermission(getApplicationContext())) {
            Toast.makeText(getApplicationContext(), "没有悬浮框权限", Toast.LENGTH_LONG).show();
            try {
                PermissionUtil.requestOverlayPermission(AppListActivity.this);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        if (!AccessibilityUtils.isAccessibilitySettingsOn(getApplicationContext())) {
            Toast.makeText(getApplicationContext(), "请打开辅助服务", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            return;
        }

        // OCR模式需要MediaProjection截图权限
        if (!ScreenCaptureManager.isInitialized()) {
            boolean restored = ScreenCaptureManager.init();
            if (!restored) {
                selectedAppInfo = appInfo;  // 保存选中的应用
                MediaProjectionManager mpm = (MediaProjectionManager)
                        getSystemService(MEDIA_PROJECTION_SERVICE);
                startActivityForResult(mpm.createScreenCaptureIntent(), 999);
                Toast.makeText(AppListActivity.this, "请授权「自动手」录制屏幕以支持OCR识别", Toast.LENGTH_LONG).show();
                return;
            }
        }

        // 权限已有，直接启动
        startTaskWithApp(appInfo);
    }

    private void startTaskWithApp(AppInfo appInfo) {
        List<AppInfo> list = new ArrayList<>();
        list.add(appInfo);
        TaskInfo taskInfo = new TaskInfo();
        taskInfo.setAppInfos(list);
        SPService.put(SPService.SP_TASK_LIST, taskInfo);

        try {
            startService(new Intent(getApplicationContext(), MyAccessbilityService.class));
        } catch (Exception e) {
            e.printStackTrace();
        }
        MyApplication.getAppInstance().startTask(list);

        // 直接跳转到选中的应用
        com.cmlanche.common.PackageUtils.startApp(appInfo.getPkgName());

        Toast.makeText(this, "已启动: " + appInfo.getName(), Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // 处理截图授权结果
        if (requestCode == 999 && resultCode == RESULT_OK && data != null) {
            // 先直接初始化 MediaProjection（避免前台服务延迟）
            ScreenCaptureManager.createMediaProjection(resultCode, data);
            
            // 再启动前台服务（Android 14+ 必须）
            Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
            serviceIntent.putExtra("result_code", resultCode);
            serviceIntent.putExtra("data", data);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }

            Toast.makeText(this, "截图权限已获取，正在启动任务...", Toast.LENGTH_LONG).show();

            // 启动任务并跳转到选中的应用
            if (selectedAppInfo != null) {
                startTaskWithApp(selectedAppInfo);
            }
        }
    }

    private class AppListAdapter extends BaseAdapter {

        private List<AppInfo> appInfos;
        private LayoutInflater inflater;
        private PackageManager pm;

        public AppListAdapter(Context context, List<AppInfo> appInfos) {
            this.inflater = LayoutInflater.from(context);
            this.appInfos = appInfos;
            this.pm = context.getPackageManager();
        }

        @Override
        public int getCount() {
            return appInfos == null ? 0 : appInfos.size();
        }

        @Override
        public AppInfo getItem(int position) {
            return appInfos == null ? null : appInfos.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.listview_appitem, null);
                holder = new ViewHolder();
                holder.icon = convertView.findViewById(R.id.icon);
                holder.name = convertView.findViewById(R.id.name);
                holder.pkg = convertView.findViewById(R.id.pkg);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            AppInfo info = getItem(position);
            holder.name.setText(info.getName());
            holder.pkg.setText(info.getPkgName());
            try {
                holder.icon.setImageDrawable(pm.getApplicationIcon(info.getPkgName()));
            } catch (Exception e) {
                holder.icon.setImageResource(R.drawable.icon);
            }
            return convertView;
        }

        private class ViewHolder {
            ImageView icon;
            TextView name;
            TextView pkg;
        }
    }
}
