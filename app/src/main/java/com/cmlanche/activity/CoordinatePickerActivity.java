package com.cmlanche.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.cmlanche.common.PackageUtils;
import com.cmlanche.common.SPService;
import com.cmlanche.jixieshou.R;
import com.cmlanche.model.AppInfo;
import com.cmlanche.model.TaskInfo;

import java.util.List;

/**
 * 坐标获取工具
 * 启动已选应用，在应用上方显示半透明覆盖层
 * 点击任意位置获取屏幕坐标
 */
public class CoordinatePickerActivity extends Activity {

    private View overlayView;
    private TextView coordText, tipText;
    private WindowManager wm;
    private int lastX, lastY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. 获取已保存的任务
        TaskInfo taskInfo = SPService.get(SPService.SP_TASK_LIST, TaskInfo.class);
        if (taskInfo == null || taskInfo.getAppInfos() == null || taskInfo.getAppInfos().isEmpty()) {
            Toast.makeText(this, "请先在主页添加任务并选择应用", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        AppInfo firstApp = taskInfo.getAppInfos().get(0);
        String pkgName = firstApp.getPkgName();
        String appName = firstApp.getName();
        if (appName == null || appName.isEmpty()) appName = pkgName;

        // 2. 创建悬浮覆盖层
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        overlayView = getLayoutInflater().inflate(R.layout.coordinate_overlay, null);
        coordText = overlayView.findViewById(R.id.coordValue);
        tipText = overlayView.findViewById(R.id.coordTip);

        // 更新提示：显示当前应用名
        tipText.setText("已在「" + appName + "」上方，点击展开按钮位置获取坐标");

        // 3. 设置触摸监听获取坐标
        overlayView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int x = (int) event.getRawX();
                int y = (int) event.getRawY();

                if (event.getAction() == MotionEvent.ACTION_UP) {
                    coordText.setText("(" + x + ", " + y + ")");
                    tipText.setText("已记录坐标，可继续点击其他位置更新");
                    lastX = x;
                    lastY = y;
                    Toast.makeText(CoordinatePickerActivity.this,
                            "坐标: (" + x + ", " + y + ")", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });

        // 保存按钮
        Button saveBtn = overlayView.findViewById(R.id.saveCoordBtn);
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (lastX == 0 && lastY == 0) {
                    Toast.makeText(CoordinatePickerActivity.this,
                            "请先点击屏幕获取坐标", Toast.LENGTH_SHORT).show();
                    return;
                }
                String coordStr = lastX + "," + lastY;
                // 复制到剪贴板
                android.content.ClipboardManager cm = (android.content.ClipboardManager)
                        getSystemService(CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("coord", coordStr);
                cm.setPrimaryClip(clip);

                // 截取点击位置周围60x60区域存为参考图
                try {
                    android.graphics.Bitmap screenshot = com.cmlanche.ocrcore.ScreencapUtil.captureScreen();
                    if (screenshot != null) {
                        int half = 30;
                        int cropX = Math.max(0, Math.min(lastX - half, screenshot.getWidth() - 60));
                        int cropY = Math.max(0, Math.min(lastY - half, screenshot.getHeight() - 60));
                        android.graphics.Bitmap icon = android.graphics.Bitmap.createBitmap(
                                screenshot, cropX, cropY, Math.min(60, screenshot.getWidth() - cropX), Math.min(60, screenshot.getHeight() - cropY));
                        com.cmlanche.ocrcore.ScreenRecognizer.saveReferenceImage(icon, "icon_" + android.text.format.DateFormat.format("HHmmss", new java.util.Date()));
                        icon.recycle();
                        screenshot.recycle();
                        Toast.makeText(CoordinatePickerActivity.this, "坐标和参考图已保存", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(CoordinatePickerActivity.this, "坐标已复制到剪贴板", Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(CoordinatePickerActivity.this, "坐标已复制到剪贴板", Toast.LENGTH_LONG).show();
                }
                removeOverlay();
                finish();
            }
        });

        // 关闭按钮
        Button closeBtn = overlayView.findViewById(R.id.closeCoordBtn);
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removeOverlay();
                finish();
            }
        });

        // 4. 添加悬浮窗到系统层（覆盖在所有应用之上）
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);

        try {
            wm.addView(overlayView, params);

            // 5. 启动目标应用
            PackageUtils.startApp(pkgName);

        } catch (Exception e) {
            Toast.makeText(this, "悬浮窗权限未开启，请在设置中授权", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void removeOverlay() {
        if (overlayView != null && wm != null) {
            try {
                wm.removeView(overlayView);
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onDestroy() {
        removeOverlay();
        super.onDestroy();
    }
}
