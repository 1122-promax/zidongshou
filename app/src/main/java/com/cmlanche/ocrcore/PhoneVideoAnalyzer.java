package com.cmlanche.ocrcore;

import android.graphics.Bitmap;
import android.graphics.Rect;

import com.cmlanche.common.SPService;
import com.cmlanche.core.bus.BusEvent;
import com.cmlanche.core.bus.BusManager;
import com.cmlanche.core.utils.ActionUtils;
import com.cmlanche.core.utils.Logger;
import com.cmlanche.core.utils.Utils;

import static com.cmlanche.core.bus.EventType.detect_status;

import java.util.List;

/**
 * 手机版视频内容分析器
 * 与 VideoAnalyzer 的区别：展开后多了一个下拉操作，让评论区完整显示
 * 在 MainActivity 选择「手机模式」时使用
 */
public class PhoneVideoAnalyzer {

    private static final String SP_CUSTOM_KEYWORDS = "custom_keywords";

    private static final String[] DEFAULT_KEYWORDS = {
            "美女", "小姐姐", "plmm", "pljj",
            "女神", "高颜值", "颜值", "好看",
            "漂亮", "可爱", "甜美",
            "大长腿", "身材", "御姐",
            "仙女", "温柔", "气质",
            "汉服", "lo裙", "汉服小姐姐", "汉服美女",
            "洛丽塔", "lolita", "lo娘", "lolita裙",
            "cos", "coser", "cosplay", "cos服",
            "jk", "jk制服", "jk裙",
            "旗袍", "古风", "古装", "古风美女",
            "制服", "女仆", "女仆装",
            "#美女", "#小姐姐", "#高颜值",
            "#汉服", "#汉服美人图鉴",
            "#洛丽塔", "#lolita", "#lo娘",
            "#cos", "#cosplay", "#coser",
            "#jk", "#jk制服",
            "#古风", "#古装", "#旗袍",
            "#女仆装", "#制服"
    };

    private static final String SP_EXPAND_X = "custom_expand_x";
    private static final String SP_EXPAND_Y = "custom_expand_y";
    private static final String SP_CLOSE_X = "custom_close_x";
    private static final String SP_CLOSE_Y = "custom_close_y";
    private static final String SP_LIKE_X = "custom_like_x";
    private static final String SP_LIKE_Y = "custom_like_y";

    public static void saveCustomCoord(String key, int value) {
        SPService.putInt(key, value);
    }

    public static int getCustomCoord(String key, int defaultVal) {
        return SPService.getInt(key, defaultVal);
    }

    public static void saveCustomKeywords(String keywords) {
        SPService.putString(SP_CUSTOM_KEYWORDS, keywords);
    }

    public static String getCustomKeywordsText() {
        return SPService.getString(SP_CUSTOM_KEYWORDS, "");
    }

    public static String getDefaultKeywordsText() {
        return String.join(",", DEFAULT_KEYWORDS);
    }

    public static boolean shouldSkipDetection() {
        String saved = SPService.getString(SP_CUSTOM_KEYWORDS, null);
        if (saved == null) return false;
        if (saved.trim().isEmpty()) return true;
        return false;
    }

    public static boolean isBeautyKeyword(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase().trim();
        String custom = SPService.getString(SP_CUSTOM_KEYWORDS, "").trim();

        // 先把文本按空格/标点拆成独立词，解决OCR粘连问题
        String[] tokens = lower.split("[\\s,，、#＃·、：:;；！!？?|/\\\\（）()\\[\\]【】{}]+");

        List<String> keywords;
        if (!custom.isEmpty()) {
            keywords = new java.util.ArrayList<>();
            for (String kw : custom.split(",")) {
                kw = kw.trim().toLowerCase();
                if (!kw.isEmpty()) keywords.add(kw);
            }
        } else {
            keywords = java.util.Arrays.asList(DEFAULT_KEYWORDS);
        }

        for (String kw : keywords) {
            // 全文匹配（标准）
            if (lower.contains(kw)) {
                com.cmlanche.core.utils.Logger.i("关键词[" + kw + "] 在全文命中: " + text.substring(0, Math.min(30, text.length())));
                return true;
            }
            // 逐词匹配：只检查token是否包含关键词（不反向，避免单字误匹配）
            for (String token : tokens) {
                if (token.length() >= 2 && token.contains(kw)) {
                    com.cmlanche.core.utils.Logger.i("关键词[" + kw + "] 在token[" + token + "]中命中: " + text.substring(0, Math.min(30, text.length())));
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isExpandText(String text) {
        if (text == null) return false;
        String t = text.trim().toLowerCase().replaceAll("\\s", "");
        // 匹配 "·展开" / "···展开" / "…展开" / "...展开" 格式（任意中间点+展开）
        if (t.contains("·展开") ||t.contains("··展开") || t.contains("···展开") || t.contains(".展开") || t.contains("展开") || t.contains("..展开") ||
         t.contains("…展开") || t.contains("...展开") || t.matches(".*\\.{2,}展开.*")) {
            return true;
        }
        // 极短文本包含"展开"（如单独的"展开"二字）
        if (t.length() <= 6 && t.contains("展开")) {
            return true;
        }
        return false;
    }

    public static boolean analyzeBeautyVideo() {
        if (shouldSkipDetection()) {
            Logger.i("自定义标签为空，跳过检测");
            return false;
        }
        Logger.i("PhoneOCR模式检测中...");
        return detectByOcr();
    }

    private static boolean detectByOcr() {
        sendStatus("检测中...");

        Bitmap screenshot = null;
        for (int i = 0; i < 3; i++) {
            screenshot = ScreencapUtil.captureScreen();
            if (screenshot != null) break;
            Logger.i("截图失败，第 " + (i+1) + " 次重试...");
            Utils.sleep(500);
        }
        if (screenshot == null) {
            Logger.e("截图连续失败3次，跳过检测");
            sendStatus("截图失败，跳过检测");
            return false;
        }

        sendStatus("OCR识别中...");
        int screenW = com.cmlanche.application.MyApplication.getAppInstance().getScreenWidth();
        int screenH = com.cmlanche.application.MyApplication.getAppInstance().getScreenHeight();
        Rect bottomRegion = new Rect(0, 0, screenW, screenH); // 全屏扫描

        List<OcrResult> descResults = OcrEngine.getInstance().recognizeRegion(screenshot, bottomRegion);
        Logger.i("描述区 OCR 识别到 " + descResults.size() + " 个文本块");

        StringBuilder allTexts = new StringBuilder();
        for (OcrResult r : descResults) {
            if (allTexts.length() > 0) allTexts.append(" | ");
            allTexts.append("[").append(r.getText()).append("]");
        }
        Logger.i("识别内容: " + (allTexts.length() > 200 ? allTexts.substring(0, 200) + "..." : allTexts.toString()));

        for (OcrResult r : descResults) {
            if (isBeautyKeyword(r.getText())) {
                sendStatus("✅ 检测到: " + r.getText() + "，正在点赞...");
                Logger.i("✅ OCR检测到目标！文本: " + r.getText());
                return true;
            }
        }

        sendStatus("未找到，找展开按钮...");
        OcrResult expandBtn = null;
        for (OcrResult r : descResults) {
            if (isExpandText(r.getText())) {
                expandBtn = r;
                break;
            }
        }

        screenshot.recycle();

        if (expandBtn == null) {
            sendStatus("❌ 未检测到目标，下一个");
            return false;
        }

        sendStatus("描述区有展开按钮，点击展开识别...");
        Rect btnRect = expandBtn.getRect();
        String expandText = expandBtn.getText();
        Logger.i("OCR找到展开按钮 [" + expandText + "] 矩形=" + btnRect.toShortString());

        int ex = getCustomCoord(SP_EXPAND_X, 676);
        int ey = getCustomCoord(SP_EXPAND_Y, 819);
        Logger.i("→ 精准点击展开坐标 坐标=(" + ex + "," + ey + ")");

        ActionUtils.click(ex, ey);
        Utils.sleep(500);

        // ★★ 手机版特色：使用无障碍滚动，让评论区露出顶部标签 ★★
        Logger.i("手机模式：执行无障碍滚动...");
        try {
            android.accessibilityservice.AccessibilityService as =
                    com.cmlanche.application.MyApplication.getAppInstance().getAccessbilityService();
            if (as != null) {
                android.view.accessibility.AccessibilityNodeInfo root = as.getRootInActiveWindow();
                if (root != null) {
                    scrollBackward(root);
                    root.recycle();
                }
            }
        } catch (Exception e) {
            Logger.e("无障碍滚动失败: " + e.getMessage());
        }
        Utils.sleep(800);

        // 展开后识别（重试3次，解决OCR偶尔漏识别的问题）
        sendStatus("展开后识别中...");
        boolean found = false;
        for (int retry = 0; retry < 3 && !found; retry++) {
            if (retry > 0) {
                Logger.i("展开后第" + (retry + 1) + "次重试OCR...");
                Utils.sleep(400);
            }
            Bitmap afterShot = ScreencapUtil.captureScreen();
            if (afterShot == null) continue;
            try {
                Rect midRegion = new Rect(0, screenH * 308 / 1000, screenW, screenH * 488 / 1000);
                List<OcrResult> afterResults = OcrEngine.getInstance().recognizeRegion(afterShot, midRegion);
                Logger.i("展开后区域识别到 " + afterResults.size() + " 个文本块");
                StringBuilder afterTexts = new StringBuilder();
                for (OcrResult r : afterResults) {
                    if (afterTexts.length() > 0) afterTexts.append(" | ");
                    afterTexts.append("[").append(r.getText()).append("]");
                }
                Logger.i("展开后识别内容: " + (afterTexts.length() > 300 ? afterTexts.substring(0, 300) + "..." : afterTexts.toString()));

                for (OcrResult r : afterResults) {
                    if (isBeautyKeyword(r.getText())) {
                        sendStatus("✅ 检测到: " + r.getText() + "，退出评论区后点赞...");
                        Logger.i("✅ OCR展开后检测到目标！文本: " + r.getText());
                        found = true;
                        break;
                    }
                }
            } finally {
                afterShot.recycle();
            }
        }

        sendStatus(found ? "退出评论区..." : "❌ 未检测到");
        int closeX = getCustomCoord(SP_CLOSE_X, 319);
        int closeY = getCustomCoord(SP_CLOSE_Y, 352);
        ActionUtils.click(closeX, closeY);
        Utils.sleep(500);
        ActionUtils.click(closeX, closeY);
        Utils.sleep(500);
        ActionUtils.click(closeX, closeY);
        Utils.sleep(500);

        if (found) {
            sendStatus("评论区已关闭，开始点赞...");
            int likeX = getCustomCoord(SP_LIKE_X, 540);
            int likeY = getCustomCoord(SP_LIKE_Y, 960);
            ActionUtils.click(likeX, likeY);
            Utils.sleep(100);
            ActionUtils.click(likeX, likeY);
            Utils.sleep(100);
            Logger.i("手机模式：点赞完成");
            sendStatus("点赞完成");
        }

        return found;
    }

    public static boolean isCurrentVideoBeauty() {
        boolean result = analyzeBeautyVideo();
        if (result) {
            Logger.i("🎯 当前视频: 美女内容");
        } else {
            Logger.i("🎯 当前视频: 非美女内容");
        }
        return result;
    }

    /** 递归查找可滚动节点并执行后向滚动（回滚到评论区顶部） */
    public static boolean scrollBackward(android.view.accessibility.AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (node.isScrollable() && node.getChildCount() > 0) {
            boolean result = node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
            node.recycle();
            return result;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            android.view.accessibility.AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                boolean result = scrollBackward(child);
                child.recycle();
                if (result) return true;
            }
        }
        return false;
    }

    private static void sendStatus(String status) {
        com.cmlanche.activity.MainActivity mainActivity =
                com.cmlanche.application.MyApplication.getAppInstance().getMainActivity();
        if (mainActivity != null) {
            mainActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    BusManager.getBus().post(new BusEvent<>(detect_status, status));
                }
            });
        }
    }
}
