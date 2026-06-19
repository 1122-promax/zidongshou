package com.cmlanche.application;

import android.app.Application;
import android.content.Context;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.BounceInterpolator;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.cmlanche.activity.MainActivity;
import com.cmlanche.activity.NewOrEditTaskActivity;
import com.cmlanche.activity.TaskTypeListActivity;
import com.cmlanche.common.PackageUtils;
import com.cmlanche.common.SPService;
import com.cmlanche.core.bus.BusEvent;
import com.cmlanche.core.bus.BusManager;
import com.cmlanche.core.service.MyAccessbilityService;
import com.cmlanche.core.utils.Logger;
import com.cmlanche.core.utils.Utils;
import com.cmlanche.floatwindow.FloatWindow;
import com.cmlanche.floatwindow.MoveType;
import com.cmlanche.floatwindow.PermissionListener;
import com.cmlanche.floatwindow.ViewStateListener;
import com.cmlanche.jixieshou.R;
import com.cmlanche.model.AppInfo;
import com.cmlanche.model.TaskInfo;
import com.cmlanche.scripts.TaskExecutor;
import com.cmlanche.widget.PointView;
import com.squareup.otto.Subscribe;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.cmlanche.core.bus.EventType.accessiblity_connected;
import static com.cmlanche.core.bus.EventType.detect_status;
import static com.cmlanche.core.bus.EventType.no_roots_alert;
import static com.cmlanche.core.bus.EventType.pause_becauseof_not_destination_page;
import static com.cmlanche.core.bus.EventType.pause_byhand;
import static com.cmlanche.core.bus.EventType.refresh_time;
import static com.cmlanche.core.bus.EventType.roots_ready;
import static com.cmlanche.core.bus.EventType.set_accessiblity;
import static com.cmlanche.core.bus.EventType.start_task;
import static com.cmlanche.core.bus.EventType.unpause_byhand;

public class MyApplication extends Application {

    private static final String TAG = "MainActivity";

    private MyAccessbilityService accessbilityService;
    protected static MyApplication appInstance;
    private int screenWidth;
    private int screenHeight;
    private boolean isVip = false;
    private View floatView;
    private MainActivity mainActivity;
    private boolean isFirstConnectAccessbilityService = false;
    private boolean isStarted = false;
    private TextView logText;
    /** 缓存日志文本，浮窗恢复时重新设置 */
    private String cachedLogText = "等待启动...";
    private String cachedStatusText = "";
    private boolean watchdogStarted = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Logger.setDebug(true);
        SPService.init(this);
        appInstance = this;

        Display display = getDisplay(getApplicationContext());
        if (display != null) {
            this.screenWidth = display.getWidth();
            this.screenHeight = display.getHeight();
        } else {
            // 兜底默认值，防止后续OCR区域计算全部为0
            this.screenWidth = 1080;
            this.screenHeight = 1920;
        }
        BusManager.getBus().register(this);

        // 看门狗在 onCreate 不启动，只由 showFloatWindow（用户打开APP/浮窗恢复时）启动
        Logger.i("应用启动完成");
    }

    @Subscribe
    public void subscribeEvent(BusEvent event) {
        switch (event.getType()) {
            case set_accessiblity:
                Toast.makeText(getApplicationContext(), "服务启动成功！", Toast.LENGTH_LONG).show();
                this.accessbilityService = (MyAccessbilityService) event.getData();
                break;
            case start_task:
                this.isStarted = true;
                long time = (long) event.getData();
                setFloatText("总执行时间：" + Utils.getTimeDescription(time));
                break;
            case pause_byhand:
                if(isStarted) {
                    setFloatText("自动手已被您暂停");
                }
                break;
            case unpause_byhand:
                if (isStarted) {
                    setFloatText("自动手已开始");
                }
                break;
            case pause_becauseof_not_destination_page:
                if(isStarted) {
                    // String reason = (String) event.getData();
                    setFloatText("非目标页面，自动手已暂停");
                }
                break;
            case refresh_time:
                if (!TaskExecutor.getInstance().isForcePause()) {
                    setFloatText("已执行：" + event.getData());
                }
                break;
            case no_roots_alert:
                TaskExecutor.getInstance().setForcePause(true);
                setFloatText("无法获取界面信息，请重启手机！");
                break;
            case roots_ready:
                TaskExecutor.getInstance().setForcePause(false);
                setFloatText("自动手重新准备就绪");
                break;
            case accessiblity_connected:
                this.isFirstConnectAccessbilityService = true;
                setFloatText("自动手已准备就绪，点我启动");
                break;
            case detect_status:
                appendLog((String) event.getData());
                break;
        }
    }


    /**
     * Get Display
     *
     * @param context Context for get WindowManager
     * @return Display
     */
    private static Display getDisplay(Context context) {
        WindowManager wm = (WindowManager) context.getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            return wm.getDefaultDisplay();
        } else {
            return null;
        }
    }

    public static MyApplication getAppInstance() {
        return appInstance;
    }

    public MyAccessbilityService getAccessbilityService() {
        return accessbilityService;
    }

    public boolean isAccessbilityServiceReady() {
        return accessbilityService != null;
    }

    public int getScreenWidth() {
        return screenWidth;
    }

    public int getScreenHeight() {
        return screenHeight;
    }

    public boolean isVip() {
        return isVip;
    }

    public void setVip(boolean vip) {
        isVip = vip;
    }

    public MainActivity getMainActivity() {
        return mainActivity;
    }

    public void setMainActivity(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }

    public void showFloatWindow() {
        floatView = LayoutInflater.from(getApplicationContext()).inflate(R.layout.floatview, null);
        logText = floatView.findViewById(R.id.logText);

        FloatWindow
                .with(getApplicationContext())
                .setView(floatView)
                .setY(0)
                .setX(0)
                .setFilter(false, MainActivity.class, NewOrEditTaskActivity.class, TaskTypeListActivity.class)
                .setMoveType(MoveType.slide)
                .setMoveStyle(500, new BounceInterpolator())
                .setViewStateListener(mViewStateListener)
                .setPermissionListener(new PermissionListener() {
                    @Override
                    public void onSuccess() {
                        Logger.i("悬浮框授权成功");
                    }

                    @Override
                    public void onFail() {
                        Logger.i("悬浮框授权失败");
                    }
                })
                .setDesktopShow(true)
                .build();

        // 定时检查悬浮窗是否还在，被系统杀掉后自动恢复
        startFloatWindowWatchdog();

        floatView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TaskInfo taskInfo = SPService.get(SPService.SP_TASK_LIST, TaskInfo.class);
                if (taskInfo != null && taskInfo.getAppInfos() != null && taskInfo.getAppInfos().size() > 0 &&
                        isFirstConnectAccessbilityService) {
                    // 服务岗连接上，可以点击快速启动，不需要跳转到自动手app去启动
                    isFirstConnectAccessbilityService = false;
                    startTask(taskInfo.getAppInfos());
                } else if(isStarted) {
                    // 已启动，则点击会触发暂停
                    if (TaskExecutor.getInstance().isForcePause()) {
                        TaskExecutor.getInstance().setForcePause(false);
                        BusManager.getBus().post(new BusEvent<>(unpause_byhand));
                    } else {
                        TaskExecutor.getInstance().setForcePause(true);
                        BusManager.getBus().post(new BusEvent<>(pause_byhand));
                    }
                } else {
                    // 未启动状态，单击会打开自动手app
                    PackageUtils.startSelf();
                }
            }
        });

        floatView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                TaskExecutor.getInstance().stop(true);
                Toast.makeText(getApplicationContext(), "自动手已暂停", Toast.LENGTH_LONG).show();
                PackageUtils.startSelf();
                return false;
            }
        });
    }

    private void setFloatText(String text) {
        cachedStatusText = text;
        if (floatView != null) {
            TextView textView = floatView.findViewById(R.id.text);
            if (textView != null) textView.setText(text);
        }
        // 同步更新无障碍悬浮窗
        try {
            com.cmlanche.core.service.MyAccessbilityService as = getAccessbilityService();
            if (as != null) as.updateOverlayStatus(text);
        } catch (Exception ignored) {}
    }

    /** 追加一行实时日志 */
    private void appendLog(String line) {
        // 同步输出到无障碍悬浮窗
        try {
            com.cmlanche.core.service.MyAccessbilityService as = getAccessbilityService();
            if (as != null) as.appendOverlayLog(line);
        } catch (Exception ignored) {}
        try {
            if (floatView == null) return;
            String current;
            if (logText != null) {
                current = logText.getText().toString();
            } else {
                current = cachedLogText;
            }
            if (current.equals("等待启动...")) current = "";

            // 保留最近10行
            String[] lines = current.split("\n");
            int start = Math.max(0, lines.length - 9);
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < lines.length; i++) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(lines[i]);
            }
            if (sb.length() > 0) sb.append("\n");
            sb.append(java.text.SimpleDateFormat.getTimeInstance(java.text.SimpleDateFormat.SHORT)
                    .format(new java.util.Date())).append(" ").append(line);
            String result = sb.toString();
            cachedLogText = result; // 缓存
            if (logText != null) {
                logText.setText(result);
            }
        } catch (Exception e) {
            Logger.e("appendLog异常: " + e.getMessage());
        }
    }

    private ViewStateListener mViewStateListener = new ViewStateListener() {
        @Override
        public void onPositionUpdate(int x, int y) {
            Log.d(TAG, "onPositionUpdate: x=" + x + " y=" + y);
        }

        @Override
        public void onShow() {
            Log.d(TAG, "onShow");
        }

        @Override
        public void onHide() {
            Log.d(TAG, "onHide");
        }

        @Override
        public void onDismiss() {
            Log.d(TAG, "onDismiss");
        }

        @Override
        public void onMoveAnimStart() {
            Log.d(TAG, "onMoveAnimStart");
        }

        @Override
        public void onMoveAnimEnd() {
            Log.d(TAG, "onMoveAnimEnd");
        }

        @Override
        public void onBackToDesktop() {
            Log.d(TAG, "onBackToDesktop");
            FloatWindow.get().show();
        }
    };

    /** 定时检查悬浮窗/无障碍浮窗，被系统杀掉后自动恢复 */
    private void startFloatWindowWatchdog() {
        if (watchdogStarted) return;
        watchdogStarted = true;
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    // 优先尝试恢复FloatWindow
                    com.cmlanche.floatwindow.IFloatWindow fw = com.cmlanche.floatwindow.FloatWindow.get();
                    if (fw == null) {
                        // 进程已死，不恢复任何东西
                    } else if (!fw.isShowing()) {
                        try {
                            fw.show();
                            logText = floatView.findViewById(R.id.logText);
                            TextView statusText = floatView.findViewById(R.id.text);
                            if (logText != null && cachedLogText != null) logText.setText(cachedLogText);
                            if (statusText != null) {
                                if (isStarted) {
                                    cachedStatusText = com.cmlanche.scripts.TaskExecutor.getInstance().isForcePause()
                                            ? "自动手已被暂停" : "自动手正在运行中...";
                                }
                                statusText.setText(cachedStatusText);
                            }
                            Logger.i("浮窗看门狗：浮窗已自动恢复");

                            // 检查是否需要续跑任务（仅当用户在本会话中点击过启动后才续跑）
                            if (isStarted
                                    && com.cmlanche.scripts.TaskExecutor.getInstance().isFinished()
                                    && com.cmlanche.scripts.TaskExecutor.hasSavedRunningTask()) {
                                Logger.i("浮窗看门狗：检测到未完成的任务，自动续跑...");
                                isStarted = true;
                                com.cmlanche.scripts.TaskExecutor.getInstance().setResumeAfterWatchdog(true);
                                // 从SP加载任务列表
                                com.cmlanche.model.TaskInfo savedTask = com.cmlanche.common.SPService.get(
                                        com.cmlanche.common.SPService.SP_TASK_LIST, com.cmlanche.model.TaskInfo.class);
                                if (savedTask != null && savedTask.getAppInfos() != null && !savedTask.getAppInfos().isEmpty()) {
                                    startTask(savedTask.getAppInfos());
                                } else {
                                    Logger.e("浮窗看门狗：SP中无任务列表，无法续跑");
                                    isStarted = false;
                                }
                            }
                        } catch (Exception e) {
                            // FloatWindow恢复失败，使用无障碍浮窗兜底
                            com.cmlanche.core.service.MyAccessbilityService as = getAccessbilityService();
                            if (as != null) {
                                as.showOverlayWindow();
                                as.appendOverlayLog("→ 浮窗恢复失败，已切换为无障碍浮窗");
                                as.updateOverlayStatus(cachedStatusText);
                                Logger.i("浮窗看门狗：已切换到无障碍浮窗");
                            }
                        }
                    }
                } catch (Exception ignored) {}
                new android.os.Handler().postDelayed(this, 3000);
            }
        }, 3000);
    }

    /**
     * 开始执行任务
     */
    public void startTask(List<AppInfo> appInfos) {
        TaskInfo taskInfo = new TaskInfo();
        taskInfo.setAppInfos(appInfos);
        TaskExecutor.getInstance().startTask(taskInfo);
    }

    // ========== 壁纸轮播（全局共享） ==========
    private android.os.Handler wallpaperHandler;
    private int wallpaperIndex;
    private View wallpaperTarget;
    private final int[] wallpapers = {
            com.cmlanche.jixieshou.R.drawable.bg_wallpaper,
            com.cmlanche.jixieshou.R.drawable.bg_wallpaper2,
            com.cmlanche.jixieshou.R.drawable.bg_wallpaper3
    };

    public void startWallpaperCycle(View targetView) {
        wallpaperTarget = targetView;
        if (wallpaperHandler != null) wallpaperHandler.removeCallbacksAndMessages(null);
        wallpaperHandler = new android.os.Handler();
        wallpaperHandler.post(new Runnable() {
            @Override
            public void run() {
                if (wallpaperTarget != null) {
                    wallpaperTarget.setBackgroundResource(wallpapers[wallpaperIndex]);
                    wallpaperIndex = (wallpaperIndex + 1) % wallpapers.length;
                }
                if (wallpaperHandler != null) {
                    wallpaperHandler.postDelayed(this, 3000);
                }
            }
        });
    }

    public void stopWallpaperCycle() {
        if (wallpaperHandler != null) {
            wallpaperHandler.removeCallbacksAndMessages(null);
            wallpaperHandler = null;
        }
    }
}
