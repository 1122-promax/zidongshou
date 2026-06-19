package com.cmlanche.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSON;
import com.cmlanche.adapter.TaskListAdapter;
import com.cmlanche.application.MyApplication;
import com.cmlanche.common.Constants;
import com.cmlanche.common.DeviceUtils;
import com.cmlanche.common.PackageUtils;
import com.cmlanche.common.SPService;

import com.google.android.material.button.MaterialButton;
import com.cmlanche.core.service.MyAccessbilityService;
import com.cmlanche.core.utils.AccessibilityUtils;
import com.cmlanche.floatwindow.PermissionUtil;
import com.cmlanche.jixieshou.R;
import com.cmlanche.ocrcore.ScreenCaptureManager;
import com.cmlanche.ocrcore.ScreenCaptureService;

import android.media.projection.MediaProjectionManager;
import com.cmlanche.model.AppInfo;
import com.cmlanche.model.TaskInfo;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private CardView cardView;
    private ListView taskListView;
    private TaskListAdapter taskListAdapter;
    private MaterialButton startBtn;
    private List<AppInfo> appInfos = new ArrayList<>();
    private boolean isFromAutoFindBtn = false;  // 标记是否来自"检测运行应用"

    private View contentView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        MyApplication.getAppInstance().setMainActivity(this);

        // 用户打开APP时显示浮窗
        MyApplication.getAppInstance().showFloatWindow();

        cardView = findViewById(R.id.newTaskCardView);
        cardView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                gotoAddNewTaskActivity();
            }
        });
        taskListView = findViewById(R.id.taskListView);
        taskListView.setAdapter(taskListAdapter = new TaskListAdapter(this, appInfos));
        taskListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                gotoEditTaskActivity(taskListAdapter.getItem(i));
            }
        });
        // 设备模式选择（二选一）
        final MaterialButton phoneModeBtn = findViewById(R.id.phoneModeBtn);
        final MaterialButton emulatorModeBtn = findViewById(R.id.emulatorModeBtn);

        android.view.View.OnClickListener modeListener = new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                boolean isPhone = v.getId() == R.id.phoneModeBtn;
                SPService.putString(SPService.DEVICE_MODE, isPhone ? "phone" : "emulator");
                com.cmlanche.ocrcore.ScreencapUtil.resetModeCache();
                updateModeButtons(phoneModeBtn, emulatorModeBtn, isPhone);
                Toast.makeText(MainActivity.this,
                        isPhone ? "已选择手机模式" : "已选择模拟器模式",
                        Toast.LENGTH_SHORT).show();
            }
        };
        phoneModeBtn.setOnClickListener(modeListener);
        emulatorModeBtn.setOnClickListener(modeListener);

        // 初始化按钮状态
        String savedMode = SPService.getString(SPService.DEVICE_MODE, "phone");
        updateModeButtons(phoneModeBtn, emulatorModeBtn, savedMode.equals("phone"));

        // 识别模式选择（二选一）
        final MaterialButton detectModeBtn = findViewById(R.id.detectModeBtn);
        final MaterialButton pureSwipeModeBtn = findViewById(R.id.pureSwipeModeBtn);

        android.view.View.OnClickListener detectListener = new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                boolean isDetect = v.getId() == R.id.detectModeBtn;
                SPService.putBoolean(SPService.DETECT_ENABLED, isDetect);
                updateDetectModeButtons(detectModeBtn, pureSwipeModeBtn, isDetect);
                Toast.makeText(MainActivity.this,
                        isDetect ? "已切换为识别点赞模式" : "已切换为纯刷视频模式",
                        Toast.LENGTH_SHORT).show();
            }
        };
        detectModeBtn.setOnClickListener(detectListener);
        pureSwipeModeBtn.setOnClickListener(detectListener);

        // 初始化识别模式按钮状态
        boolean isDetect = SPService.getBoolean(SPService.DETECT_ENABLED, true);
        updateDetectModeButtons(detectModeBtn, pureSwipeModeBtn, isDetect);

        startBtn = findViewById(R.id.startBtn);
        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (appInfos.isEmpty()) {
                    Toast.makeText(getApplicationContext(), "请选择一个任务", Toast.LENGTH_LONG).show();
                    return;
                }

                if(!checkPkgValid()) {
                    return;
                }

                if (!PermissionUtil.checkFloatPermission(getApplicationContext())) {
                    Toast.makeText(getApplicationContext(), "没有悬浮框权限，为了保证任务能够持续，请授权", Toast.LENGTH_LONG).show();
                    try {
                        PermissionUtil.requestOverlayPermission(MainActivity.this);
                    } catch (NoSuchFieldException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                    return;
                }

                if (!AccessibilityUtils.isAccessibilitySettingsOn(getApplicationContext())) {
                    Toast.makeText(getApplicationContext(), "请打开「自动手」的辅助服务", Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    startActivity(intent);
                    return;
                }

                // OCR模式需要MediaProjection截图权限
                isFromAutoFindBtn = false;  // 标记：来自"点我启动"
                if (!ScreenCaptureManager.isInitialized()) {
                    boolean restored = ScreenCaptureManager.init();
                    if (!restored) {
                        MediaProjectionManager mpm = (MediaProjectionManager)
                                getSystemService(MEDIA_PROJECTION_SERVICE);
                        startActivityForResult(mpm.createScreenCaptureIntent(), 999);
                        Toast.makeText(MainActivity.this, "请授权「自动手」录制屏幕以支持OCR识别", Toast.LENGTH_LONG).show();
                        return;
                    }
                }

                // 启动服务
                startService(new Intent(getApplicationContext(), MyAccessbilityService.class));
                MyApplication.getAppInstance().startTask(appInfos);
                // 自动跳转到第一个目标APP
                if (!appInfos.isEmpty()) {
                    PackageUtils.startApp(appInfos.get(0).getPkgName());
                }
            }
        });

        MaterialButton autoFindBtn = findViewById(R.id.autoFindBtn);
        autoFindBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 直接跳转选择应用界面，权限申请移到 AppListActivity
                isFromAutoFindBtn = true;  // 标记：来自"检测运行应用"
                startActivity(new Intent(MainActivity.this, AppListActivity.class));
            }
        });

        // 获取坐标按钮
        MaterialButton coordPickerBtn = findViewById(R.id.coordPickerBtn);
        coordPickerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, CoordinatePickerActivity.class));
            }
        });

        // 自定义标签按钮
        MaterialButton keywordBtn = findViewById(R.id.keywordBtn);
        keywordBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final android.widget.EditText input = new android.widget.EditText(MainActivity.this);
                input.setText(com.cmlanche.ocrcore.VideoAnalyzer.getCustomKeywordsText());
                input.setHint("用逗号分隔，如: 美女,cos,汉服");
                input.setSelection(input.getText().length());
                input.setPadding(40, 30, 40, 30);

                final android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(MainActivity.this)
                        .setTitle("自定义检测标签")
                        .setMessage("输入关键词，用逗号分隔\n留空则使用内置默认标签")
                        .setView(input)
                        .setPositiveButton("保存", new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                String text = input.getText().toString().trim();
                                com.cmlanche.ocrcore.VideoAnalyzer.saveCustomKeywords(text);
                                Toast.makeText(MainActivity.this, "自定义标签已保存", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNeutralButton("还原内置", null)
                        .setNegativeButton("取消", null)
                        .create();
                dialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
                    @Override
                    public void onShow(android.content.DialogInterface di) {
                        dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new android.view.View.OnClickListener() {
                            @Override
                            public void onClick(android.view.View v) {
                                input.setText(com.cmlanche.ocrcore.VideoAnalyzer.getDefaultKeywordsText());
                                input.setSelection(input.getText().length());
                                com.cmlanche.ocrcore.VideoAnalyzer.saveCustomKeywords("");
                                Toast.makeText(MainActivity.this, "已还原为内置标签", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
                dialog.show();
            }
        });

        // 坐标设置按钮
        MaterialButton coordSettingBtn = findViewById(R.id.coordSettingBtn);
        coordSettingBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 展开坐标输入
                final android.widget.EditText expandInput = new android.widget.EditText(MainActivity.this);
                int ex = com.cmlanche.ocrcore.VideoAnalyzer.getCustomCoord("custom_expand_x", 676);
                int ey = com.cmlanche.ocrcore.VideoAnalyzer.getCustomCoord("custom_expand_y", 819);
                expandInput.setText(ex + "," + ey);
                expandInput.setHint("格式: X,Y  如: 676,819");
                expandInput.setPadding(40, 30, 40, 30);

                // 关闭坐标输入
                final android.widget.EditText closeInput = new android.widget.EditText(MainActivity.this);
                int cx = com.cmlanche.ocrcore.VideoAnalyzer.getCustomCoord("custom_close_x", 319);
                int cy = com.cmlanche.ocrcore.VideoAnalyzer.getCustomCoord("custom_close_y", 352);
                closeInput.setText(cx + "," + cy);
                closeInput.setHint("格式: X,Y  如: 319,352");
                closeInput.setPadding(40, 30, 40, 30);

                android.widget.LinearLayout layout = new android.widget.LinearLayout(MainActivity.this);
                layout.setOrientation(android.widget.LinearLayout.VERTICAL);
                layout.setPadding(40, 20, 40, 20);
                android.widget.TextView label1 = new android.widget.TextView(MainActivity.this);
                label1.setText("展开坐标:");
                label1.setTextSize(14);
                android.widget.TextView label2 = new android.widget.TextView(MainActivity.this);
                label2.setText("退出评论区坐标:");
                label2.setTextSize(14);
                label2.setPadding(0, 30, 0, 0);

                // 双击点赞坐标输入
                final android.widget.EditText likeInput = new android.widget.EditText(MainActivity.this);
                int lx = com.cmlanche.ocrcore.VideoAnalyzer.getCustomCoord("custom_like_x", 540);
                int ly = com.cmlanche.ocrcore.VideoAnalyzer.getCustomCoord("custom_like_y", 960);
                likeInput.setText(lx + "," + ly);
                likeInput.setHint("格式: X,Y  如: 540,960");
                likeInput.setPadding(40, 30, 40, 30);
                android.widget.TextView label3 = new android.widget.TextView(MainActivity.this);
                label3.setText("点赞坐标(双击):");
                label3.setTextSize(14);
                label3.setPadding(0, 30, 0, 0);

                layout.addView(label1);
                layout.addView(expandInput);
                layout.addView(label2);
                layout.addView(closeInput);
                layout.addView(label3);
                layout.addView(likeInput);

                new android.app.AlertDialog.Builder(MainActivity.this)
                        .setTitle("自定义坐标设置")
                        .setView(layout)
                        .setPositiveButton("保存", new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                try {
                                    String[] expandXY = expandInput.getText().toString().trim().split(",");
                                    String[] closeXY = closeInput.getText().toString().trim().split(",");
                                    String[] likeXY = likeInput.getText().toString().trim().split(",");
                                    if (expandXY.length == 2 && closeXY.length == 2 && likeXY.length == 2) {
                                        com.cmlanche.ocrcore.VideoAnalyzer.saveCustomCoord("custom_expand_x", Integer.parseInt(expandXY[0].trim()));
                                        com.cmlanche.ocrcore.VideoAnalyzer.saveCustomCoord("custom_expand_y", Integer.parseInt(expandXY[1].trim()));
                                        com.cmlanche.ocrcore.VideoAnalyzer.saveCustomCoord("custom_close_x", Integer.parseInt(closeXY[0].trim()));
                                        com.cmlanche.ocrcore.VideoAnalyzer.saveCustomCoord("custom_close_y", Integer.parseInt(closeXY[1].trim()));
                                        com.cmlanche.ocrcore.VideoAnalyzer.saveCustomCoord("custom_like_x", Integer.parseInt(likeXY[0].trim()));
                                        com.cmlanche.ocrcore.VideoAnalyzer.saveCustomCoord("custom_like_y", Integer.parseInt(likeXY[1].trim()));
                                        Toast.makeText(MainActivity.this, "坐标已保存", Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(MainActivity.this, "格式错误，请输入 X,Y", Toast.LENGTH_SHORT).show();
                                    }
                                } catch (Exception e) {
                                    Toast.makeText(MainActivity.this, "格式错误：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        });

        TextView textView = findViewById(R.id.deviceNo);
        textView.setText("设备号：" + DeviceUtils.getDeviceSN());

        // 壁纸轮播
        contentView = findViewById(android.R.id.content);
        MyApplication.getAppInstance().startWallpaperCycle(contentView);

        this.initData();
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

    private void initData() {
        TaskInfo taskInfo = SPService.get(SPService.SP_TASK_LIST, TaskInfo.class);
        if (taskInfo == null || taskInfo.getAppInfos() == null || taskInfo.getAppInfos().isEmpty()) {
            cardView.setVisibility(View.VISIBLE);
        } else {
            cardView.setVisibility(View.GONE);
            appInfos.addAll(taskInfo.getAppInfos());
            taskListAdapter.notifyDataSetChanged();
        }

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

            // 根据标记判断：来自"点我启动"才启动任务，来自"检测运行应用"只获取权限不启动
            if (!isFromAutoFindBtn) {
                Toast.makeText(this, "截图权限已获取，正在启动任务...", Toast.LENGTH_LONG).show();
                // 启动任务并跳转到目标APP
                startService(new Intent(getApplicationContext(), MyAccessbilityService.class));
                MyApplication.getAppInstance().startTask(appInfos);
                if (!appInfos.isEmpty()) {
                    PackageUtils.startApp(appInfos.get(0).getPkgName());
                }
            } else {
                Toast.makeText(this, "截图权限已获取，请重新选择应用", Toast.LENGTH_LONG).show();
            }
            return;
        }

        if (requestCode == 100 && resultCode == 1) {
            // 100是新增任务
            AppInfo appInfo = JSON.parseObject(data.getStringExtra("appInfo"), AppInfo.class);
            cardView.setVisibility(View.GONE);
            appInfos.add(appInfo);
            taskListAdapter.notifyDataSetChanged();
            saveTaskList();
        }

        // 编辑任务成功
        if (requestCode == 101) {
            // 101是更新
            if (resultCode == 1) {
                AppInfo appInfo = JSON.parseObject(data.getStringExtra("appInfo"), AppInfo.class);
                // 1是删除
                deleteAppInfo(appInfo);
                if (appInfos.isEmpty()) {
                    cardView.setVisibility(View.VISIBLE);
                }
                saveTaskList();
            } else if (resultCode == 2) {
                AppInfo appInfo = JSON.parseObject(data.getStringExtra("appInfo"), AppInfo.class);
                // 2是编辑
                AppInfo editedAppInfo = JSON.parseObject(data.getStringExtra("editedAppInfo"), AppInfo.class);
                updateAppInfo(editedAppInfo.getUuid(), appInfo);
                saveTaskList();
            }
        }
    }

    private void gotoAddNewTaskActivity() {
        startActivityForResult(new Intent(this, NewOrEditTaskActivity.class), 100);
    }

    private void gotoEditTaskActivity(AppInfo appInfo) {
        Intent i = new Intent(this, NewOrEditTaskActivity.class);
        i.putExtra("appInfo", JSON.toJSONString(appInfo));
        startActivityForResult(i, 101);
    }

    private void deleteAppInfo(AppInfo appInfo) {
        for (int i = 0; i < appInfos.size(); i++) {
            if (appInfo.getUuid().equals(appInfos.get(i).getUuid())) {
                appInfos.remove(i);
                taskListAdapter.notifyDataSetChanged();
                break;
            }
        }
    }

    private void updateAppInfo(String uuid, AppInfo appInfo) {
        for (int i = 0; i < appInfos.size(); i++) {
            AppInfo curr = appInfos.get(i);
            if (uuid.equals(curr.getUuid())) {
                curr.setFree(appInfo.isFree());
                curr.setPkgName(appInfo.getPkgName());
                curr.setPeriod(appInfo.getPeriod());
                curr.setIcon(appInfo.getIcon());
                curr.setName(appInfo.getName());
                taskListAdapter.notifyDataSetChanged();
                break;
            }
        }
    }

    private void updateModeButtons(MaterialButton phoneBtn, MaterialButton emulatorBtn, boolean isPhone) {
        int activeColor = android.graphics.Color.parseColor("#1A472fc8");    // colorPrimary, 90%透明
        int inactiveColor = android.graphics.Color.parseColor("#1A9E9E9E");  // grey, 90%透明
        if (isPhone) {
            phoneBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(activeColor));
            phoneBtn.setEnabled(false);
            emulatorBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(inactiveColor));
            emulatorBtn.setEnabled(true);
        } else {
            emulatorBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(activeColor));
            emulatorBtn.setEnabled(false);
            phoneBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(inactiveColor));
            phoneBtn.setEnabled(true);
        }
    }

    private void updateDetectModeButtons(MaterialButton detectBtn, MaterialButton swipeBtn, boolean isDetect) {
        int activeColor = android.graphics.Color.parseColor("#1A4CAF50");   // green, 90%透明
        int inactiveColor = android.graphics.Color.parseColor("#1A9E9E9E");  // grey, 90%透明
        if (isDetect) {
            detectBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(activeColor));
            detectBtn.setEnabled(false);
            swipeBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(inactiveColor));
            swipeBtn.setEnabled(true);
        } else {
            swipeBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(activeColor));
            swipeBtn.setEnabled(false);
            detectBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(inactiveColor));
            detectBtn.setEnabled(true);
        }
    }

    private void saveTaskList() {
        TaskInfo taskInfo = new TaskInfo();
        taskInfo.setAppInfos(appInfos);
        SPService.put(SPService.SP_TASK_LIST, taskInfo);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void requestSharePermission() {
        if(Build.VERSION.SDK_INT>=23){
            String[] mPermissionList = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_EXTERNAL_STORAGE};
            ActivityCompat.requestPermissions(this,mPermissionList,123);
        }
    }

    private boolean checkPkgValid() {
        for(AppInfo appInfo: appInfos) {
            if(!isAppExist(appInfo.getPkgName())) {
                String appName = appInfo.getName();
                if (appName == null || appName.isEmpty()) appName = appInfo.getPkgName();
                Toast.makeText(this, String.format("请先安装应用「%s」", appName), Toast.LENGTH_LONG).show();
                return false;
            }
        }
        return true;
    }

    protected boolean isAppExist(String pkgName) {
        ApplicationInfo info;
        try {
            info = getPackageManager().getApplicationInfo(pkgName, 0);
        }
        catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            info = null;
        }
        return info != null;
    }
}
