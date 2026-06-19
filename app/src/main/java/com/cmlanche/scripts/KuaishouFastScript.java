package com.cmlanche.scripts;

import android.graphics.Bitmap;
import android.graphics.Rect;

import com.cmlanche.application.MyApplication;
import com.cmlanche.core.search.node.NodeInfo;
import com.cmlanche.core.utils.ActionUtils;
import com.cmlanche.core.utils.Logger;
import com.cmlanche.core.utils.Utils;
import com.cmlanche.model.AppInfo;
import com.cmlanche.ocrcore.OcrEngine;
import com.cmlanche.ocrcore.OcrResult;
import com.cmlanche.ocrcore.PhoneVideoAnalyzer;
import com.cmlanche.ocrcore.ScreencapUtil;

import java.util.List;

/**
 * 快手极速版 - 模拟器模式
 */
public class KuaishouFastScript extends BaseScript {

    private boolean isCheckedWozhidaole;

    public KuaishouFastScript(AppInfo appInfo) { super(appInfo); }

    @Override
    protected void executeScript() {
        try {
            if (!isTargetPkg()) {
                Logger.i("快手极速版：不在快手页面，等待中...");
                Utils.sleep(2000);
                return;
            }

            if (!com.cmlanche.common.SPService.getBoolean(com.cmlanche.common.SPService.DETECT_ENABLED, true)) {
                Logger.i("识别点赞已关闭，纯刷视频模式");
                swipeToNext();
                Utils.sleep(2000);
                return;
            }

            if (!isCheckedWozhidaole) {
                NodeInfo nodeInfo = findByText("*为呵护未成年人健康*");
                if (nodeInfo != null) {
                    if (findTextAndClick("我知道了")) {
                        isCheckedWozhidaole = true;
                        Utils.sleep(200);
                    }
                }
            }

            if (!isLoggedIn) handleLogin();

            totalVideos++;
            if (totalVideos % 10 == 0) {
                Logger.i("定期重置 OCR 引擎（第" + totalVideos + "轮）");
                OcrEngine.getInstance().reset();
                Utils.sleep(500);
            }

            Logger.i("快手极速版：正在检测第 " + totalVideos + " 个视频");

            int screenW = MyApplication.getAppInstance().getScreenWidth();
            int screenH = MyApplication.getAppInstance().getScreenHeight();

            boolean directHit = false;
            String hitText = "";
            OcrResult expandBtn = null;

            for (int scan = 0; scan < 3 && !directHit && expandBtn == null; scan++) {
                if (scan > 0) {
                    Logger.i("初始OCR第" + (scan + 1) + "次扫描...");
                    Utils.sleep(400);
                }

                sendAppStatus("截图/OCR识别中...");
                Bitmap screenshot = ScreencapUtil.captureScreen();
                if (screenshot == null) {
                    Logger.e("截图返回null");
                    if (scan == 2) {
                        Logger.e("截图连续失败3次，直接滑动");
                        swipeToNext();
                        Utils.sleep(2000);
                        return;
                    }
                    continue;
                }

                Bitmap grayBitmap = toGrayscale(screenshot);
                List<OcrResult> results = OcrEngine.getInstance().recognizeRegion(
                        grayBitmap, new Rect(0, screenH * 40 / 100, screenW, screenH));
                grayBitmap.recycle();
            Logger.i("截图成功(" + screenshot.getWidth() + "x" + screenshot.getHeight() + ")，开始OCR...");
            Logger.i("OCR识别到 " + results.size() + " 个文本块");

                StringBuilder allTexts = new StringBuilder();
                for (OcrResult r : results) {
                    if (allTexts.length() > 0) allTexts.append(" | ");
                    allTexts.append("[").append(r.getText()).append("]");
                }
                Logger.i("识别内容: " + (allTexts.length() > 300 ? allTexts.substring(0, 300) + "..." : allTexts.toString()));
                sendAppStatus("识别到 " + results.size() + " 个文本块");

                for (OcrResult r : results) {
                    if (PhoneVideoAnalyzer.isBeautyKeyword(r.getText())) {
                        directHit = true;
                        hitText = r.getText();
                        break;
                    }
                }

                if (!directHit) {
                    for (OcrResult r : results) {
                        if (PhoneVideoAnalyzer.isExpandText(r.getText())) {
                            Logger.i("✅ 找到展开按钮: [" + r.getText() + "]");
                            expandBtn = r;
                            break;
                        }
                    }
                    if (expandBtn == null && scan == 2) {
                        Logger.i("❌ 3次均未找到展开按钮");
                    }
                }

                screenshot.recycle();
            }

            if (directHit) {
                beautyVideos++;
                Logger.i("✅ 直接命中关键词: " + hitText);
                sendAppStatus("✅ 检测到: " + hitText + "，等待1秒后点赞...");
                Utils.sleep(1000);
                clickLikeButton();
                Logger.i("点赞完成，停留0.5秒后滑动");
                Utils.sleep(500);
                swipeToNext();
                Utils.sleep(2000);
                return;
            }

            if (expandBtn == null) {
                Logger.i("❌ 3次扫描均未命中关键词和展开按钮，直接滑动");
                sendAppStatus("未检测到目标，下一个");
                swipeToNext();
                Utils.sleep(2000);
                return;
            }

            Logger.i("描述区有展开按钮 [" + expandBtn.getText() + "]，点击展开...");
            sendAppStatus("点击展开...");
            int ex = PhoneVideoAnalyzer.getCustomCoord("custom_expand_x", 676);
            int ey = PhoneVideoAnalyzer.getCustomCoord("custom_expand_y", 819);
            ActionUtils.click(ex, ey);
            Utils.sleep(3000);

            Logger.i("执行无障碍后向滚动...");
            try {
                android.accessibilityservice.AccessibilityService as = MyApplication.getAppInstance().getAccessbilityService();
                if (as != null) {
                    android.view.accessibility.AccessibilityNodeInfo root = as.getRootInActiveWindow();
                    if (root != null) {
                        PhoneVideoAnalyzer.scrollBackward(root);
                        root.recycle();
                    }
                }
            } catch (Exception e) {
                Logger.e("无障碍滚动失败: " + e.getMessage());
            }
            Utils.sleep(1000);

            sendAppStatus("展开后识别中...");
            boolean found = false;
            String foundText = "";
            for (int retry = 0; retry < 3 && !found; retry++) {
                if (retry > 0) {
                    Logger.i("展开后第" + (retry + 1) + "次重试OCR...");
                    Utils.sleep(400);
                }
                Bitmap afterShot = ScreencapUtil.captureScreen();
                if (afterShot == null) continue;
                try {
                    Bitmap afterGray = toGrayscale(afterShot);
                    List<OcrResult> afterResults = OcrEngine.getInstance().recognizeRegion(
                            afterGray, new Rect(0, screenH * 308 / 1000, screenW, screenH * 488 / 1000));
                    afterGray.recycle();
                    Logger.i("展开后识别到 " + afterResults.size() + " 个文本块");

                    StringBuilder afterTexts = new StringBuilder();
                    for (OcrResult r : afterResults) {
                        if (afterTexts.length() > 0) afterTexts.append(" | ");
                        afterTexts.append("[").append(r.getText()).append("]");
                    }
                    Logger.i("展开后内容: " + (afterTexts.length() > 300 ? afterTexts.substring(0, 300) + "..." : afterTexts.toString()));

                    for (OcrResult r : afterResults) {
                        if (PhoneVideoAnalyzer.isBeautyKeyword(r.getText())) {
                            found = true;
                            foundText = r.getText();
                            Logger.i("✅ 展开后检测到目标！文本: " + foundText);
                            break;
                        }
                    }
                } finally {
                    afterShot.recycle();
                }
            }

            sendAppStatus("退出评论区...");
            int closeX = PhoneVideoAnalyzer.getCustomCoord("custom_close_x", 319);
            int closeY = PhoneVideoAnalyzer.getCustomCoord("custom_close_y", 352);
            ActionUtils.click(closeX, closeY);
            Utils.sleep(500);
            ActionUtils.click(closeX, closeY);
            Utils.sleep(500);
            ActionUtils.click(closeX, closeY);
            Utils.sleep(500);

            if (found) {
                beautyVideos++;
                Logger.i("🎯 第 " + totalVideos + " 个是目标视频！（累计: " + beautyVideos + "/" + totalVideos + "）");
                sendAppStatus("✅ 检测到: " + foundText + "，等待2秒后点赞...");
                Utils.sleep(2000);
                clickLikeButton();
                Logger.i("点赞完成");
                sendAppStatus("点赞完成，停留观看...");
                Utils.sleep(1000);
            } else {
                Logger.i("❌ 展开后仍未检测到目标");
            }

            Logger.i("滑动到下一个视频");
            sendAppStatus("滑动下一个...");
            swipeToNext();
            Utils.sleep(2000);
            sendAppStatus("已滑动，等待加载...");
        } catch (Exception e) {
            Logger.e("executeScript异常: " + e.getMessage(), e);
            sendAppStatus("脚本异常: " + e.getClass().getSimpleName());
        }
    }

    @Override
    public boolean isDestinationPage() {
        if (!isTargetPkg() || !isLoggedIn) return false;
        return findById("slide_play_view_pager") != null || findById("slide_playerkit_view") != null;
    }
}
