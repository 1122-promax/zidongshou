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
 * 视频内容分析器
 * 识别当前视频是否为「美女」视频
 * 流程：OCR 扫描标签 → 找不到就点展开 → 再扫描描述区
 */
public class VideoAnalyzer {

    private static final String TAG = "VideoAnalyzer";
    private static final String SP_CUSTOM_KEYWORDS = "custom_keywords";

    // 美女相关的关键词/标签
    private static final String[] DEFAULT_KEYWORDS = {
            // ---- 基础 ----
            "美女", "小姐姐", "plmm", "pljj",
            "女神", "高颜值", "颜值", "好看",
            "漂亮", "可爱", "甜美",
            "大长腿", "身材", "御姐",
            "仙女", "温柔", "气质",

            // ---- 服饰/风格 ----
            "汉服", "lo裙", "汉服小姐姐", "汉服美女",
            "洛丽塔", "lolita", "lo娘", "lolita裙",
            "cos", "coser", "cosplay", "cos服",
            "jk", "jk制服", "jk裙",
            "旗袍", "古风", "古装", "古风美女",
            "制服", "女仆", "女仆装",

            // ---- 标签形式 ----
            "#美女", "#小姐姐", "#高颜值",
            "#汉服", "#汉服美人图鉴",
            "#洛丽塔", "#lolita", "#lo娘",
            "#cos", "#cosplay", "#coser",
            "#jk", "#jk制服",
            "#古风", "#古装", "#旗袍",
            "#女仆装", "#制服"
    };

    // ===== 自定义坐标存储 =====
    private static final String SP_EXPAND_X = "custom_expand_x";
    private static final String SP_EXPAND_Y = "custom_expand_y";
    private static final String SP_CLOSE_X = "custom_close_x";
    private static final String SP_CLOSE_Y = "custom_close_y";

    /** 保存自定义坐标 */
    public static void saveCustomCoord(String key, int value) {
        SPService.putInt(key, value);
    }

    public static int getCustomCoord(String key, int defaultVal) {
        return SPService.getInt(key, defaultVal);
    }

    public static String getCustomCoordText(String prefix, String keyX, String keyY, int defaultX, int defaultY) {
        int x = SPService.getInt(keyX, defaultX);
        int y = SPService.getInt(keyY, defaultY);
        return prefix + "(" + x + ", " + y + ")";
    }

    /** 保存自定义关键词 */
    public static void saveCustomKeywords(String keywords) {
        SPService.putString(SP_CUSTOM_KEYWORDS, keywords);
    }

    /** 获取当前自定义关键词文本 */
    public static String getCustomKeywordsText() {
        return SPService.getString(SP_CUSTOM_KEYWORDS, "");
    }

    /** 获取内置默认关键词的字符串格式（逗号分隔） */
    public static String getDefaultKeywordsText() {
        return String.join(",", DEFAULT_KEYWORDS);
    }

    // 是否是美女关键词（优先使用自定义，没有自定义才用内置）
    private static boolean isBeautyKeyword(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        String custom = SPService.getString(SP_CUSTOM_KEYWORDS, "").trim();
        if (!custom.isEmpty()) {
            // 有自定义关键词 → 完全使用自定义的
            for (String kw : custom.split(",")) {
                kw = kw.trim();
                if (!kw.isEmpty() && lower.contains(kw.toLowerCase())) return true;
            }
            return false;
        }
        // 没有自定义 → 使用内置默认关键词
        for (String kw : DEFAULT_KEYWORDS) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    /** 检查是否应该跳过检测（自定义标签被保存为空白时跳过） */
    public static boolean shouldSkipDetection() {
        // 如果key存在但值为空，说明用户主动清空了标签
        String saved = SPService.getString(SP_CUSTOM_KEYWORDS, null);
        if (saved == null) return false;                  // 从没设置过，用内置
        if (saved.trim().isEmpty()) return true;          // 设置成了空，跳过检测
        return false;
    }

    // 是否是展开按钮文字（优先完整短语，避免误判）
    private static boolean isExpandText(String text) {
        if (text == null) return false;
        String t = text.trim().toLowerCase().replaceAll("\\s", "");
        // 完整短语匹配（最可靠，避免匹配到其他地方的"更多"等泛词）
        if (t.contains("展开全文") || t.contains("查看全文")
                || t.contains("查看更多") || t.contains("展开更多")) {
            return true;
        }
        // 包含"展开"（大概率是展开按钮）
        if (t.contains("展开")) {
            return true;
        }
        // "···"或"...展开"
        if (t.contains("···") || t.contains("...展开") || t.matches(".*\\.{2,}展开.*")) {
            return true;
        }
        // 单独的"更多"、"查看"、"全文"不判定为展开（太泛，避免误判）
        return false;
    }

    /**
     * 分析当前视频是否包含目标标签
     * 仅使用 OCR 屏幕识别（截图+ML Kit）
     */
    public static boolean analyzeBeautyVideo() {
        if (shouldSkipDetection()) {
            Logger.i("自定义标签为空，跳过检测");
            return false;
        }
        Logger.i("OCR模式检测中...");
        return detectByOcr();
    }

    // ========== OCR屏幕识别模式 ==========

    private static boolean detectByOcr() {
        sendStatus("检测中...");

        // 截图（失败时重试3次）
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

        // ===== 第一步：只识别屏幕下方30%区域（视频描述区） =====
        sendStatus("OCR识别中...");
        int screenW = com.cmlanche.application.MyApplication.getAppInstance().getScreenWidth();
        int screenH = com.cmlanche.application.MyApplication.getAppInstance().getScreenHeight();
        Rect bottom30Region = new Rect(0, screenH * 70 / 100, screenW, screenH); // 下方30%
        
        List<OcrResult> descResults = OcrEngine.getInstance().recognizeRegion(screenshot, bottom30Region);
        Logger.i("下方30% OCR 识别到 " + descResults.size() + " 个文本块");

        // 输出识别内容
        StringBuilder allTexts = new StringBuilder();
        for (OcrResult r : descResults) {
            if (allTexts.length() > 0) allTexts.append(" | ");
            allTexts.append("[").append(r.getText()).append("]");
        }
        Logger.i("识别内容: " + (allTexts.length() > 200 ? allTexts.substring(0, 200) + "..." : allTexts.toString()));

        // 在下方30%区域查找美女关键词
        for (OcrResult r : descResults) {
            if (isBeautyKeyword(r.getText())) {
                sendStatus("✅ 检测到: " + r.getText() + "，正在点赞...");
                Logger.i("✅ OCR检测到目标！文本: " + r.getText());
                return true;
            }
        }

        // ===== 第二步：没找到，在下方30%区域找展开按钮 =====
        sendStatus("未找到，找展开按钮...");
        OcrResult expandBtn = null;
        for (OcrResult r : descResults) {
            if (isExpandText(r.getText())) {
                expandBtn = r;
                break;
            }
        }
        
        screenshot.recycle(); // 用完就释放

        if (expandBtn == null) {
            sendStatus("❌ 未检测到目标，下一个");
            return false;
        }

        // 点击展开
        sendStatus("描述区有展开按钮，点击展开识别...");
        Rect btnRect = expandBtn.getRect();
        String expandText = expandBtn.getText();
        Logger.i("OCR找到展开按钮 [" + expandText
                + "] 矩形=" + btnRect.toShortString());

        // ⚠️ 使用自定义或默认固定坐标精准点击展开
        int ex = getCustomCoord(SP_EXPAND_X, 676);
        int ey = getCustomCoord(SP_EXPAND_Y, 819);

        Logger.i("→ 精准点击展开坐标 坐标=(" + ex + "," + ey + ")");

        // 精确单次点击
        ActionUtils.click(ex, ey);
        Utils.sleep(2000);

        // 展开后只识别30%以上60%以下的区域（作者描述区）
        sendStatus("展开后识别中...");
        Bitmap afterShot = ScreencapUtil.captureScreen();
        boolean found = false;
        if (afterShot != null) {
            Rect midRegion = new Rect(0, screenH * 30 / 100, screenW, screenH * 60 / 100); // 30%-60%区域
            
            List<OcrResult> afterResults = OcrEngine.getInstance().recognizeRegion(afterShot, midRegion);
            afterShot.recycle();
            
            Logger.i("展开后30%-60%区域识别到 " + afterResults.size() + " 个文本块");
            StringBuilder afterTexts = new StringBuilder();
            for (OcrResult r : afterResults) {
                if (afterTexts.length() > 0) afterTexts.append(" | ");
                afterTexts.append("[").append(r.getText()).append("]");
            }
            Logger.i("展开后识别内容: " + (afterTexts.length() > 200 ? afterTexts.substring(0, 200) + "..." : afterTexts.toString()));
            
            for (OcrResult r : afterResults) {
                if (isBeautyKeyword(r.getText())) {
                    sendStatus("✅ 检测到: " + r.getText() + "，退出评论区后点赞...");
                    Logger.i("✅ OCR展开后检测到目标！文本: " + r.getText());
                    found = true;
                    break;
                }
            }
        }

        // 不管找没找到，都先退出评论区！
        sendStatus(found ? "退出评论区准备点赞..." : "❌ 未检测到，退出评论区后滑动下一个");
        ActionUtils.click(getCustomCoord(SP_CLOSE_X, 319), getCustomCoord(SP_CLOSE_Y, 352));
        Utils.sleep(500);

        // 如果找到了，多等1秒让评论区完全关闭，确保外部点赞操作生效
        if (found) {
            Utils.sleep(1000);
        }

        // 返回结果
        return found;
    }


    /**
     * 扫描作者描述区
     */
    private static BeautyCheckResult scanDescriptionArea() {
        int screenW = com.cmlanche.application.MyApplication.getAppInstance().getScreenWidth();
        int screenH = com.cmlanche.application.MyApplication.getAppInstance().getScreenHeight();

        // 全屏扫描 0%-100%，覆盖整个屏幕内容
        // 悬浮窗文字（检测中、展开按钮等提示）不含美女关键词，不会被误判
        int top = 0;
        int bottom = screenH;
        Rect descRegion = new Rect(0, top, screenW, bottom);

        Bitmap screenshot = ScreencapUtil.captureScreen();
        if (screenshot == null) {
            return new BeautyCheckResult(false, null, null, null);
        }

        List<OcrResult> results = OcrEngine.getInstance().recognizeRegion(screenshot, descRegion);
        screenshot.recycle();

        // 日志输出所有识别的文字，方便排查
        StringBuilder allTexts = new StringBuilder();
        for (OcrResult r : results) {
            if (allTexts.length() > 0) allTexts.append(" | ");
            allTexts.append("[").append(r.getText()).append("]");
        }
        Logger.i("展开后识别到: " + (allTexts.length() > 0 ? allTexts.toString() : "空"));

        for (OcrResult r : results) {
            if (isBeautyKeyword(r.getText())) {
                return new BeautyCheckResult(true, r.getText(), r.getRect(), "作者描述行");
            }
        }

        return new BeautyCheckResult(false, null, null, null);
    }

    /**
     * 获取当前视频的完整描述文字
     */
    public static String getFullDescription() {
        int screenW = com.cmlanche.application.MyApplication.getAppInstance().getScreenWidth();
        int screenH = com.cmlanche.application.MyApplication.getAppInstance().getScreenHeight();

        // 全屏扫描
        Rect descRegion = new Rect(0, 0, screenW, screenH);

        Bitmap screenshot = ScreencapUtil.captureScreen();
        if (screenshot == null) return "";

        List<OcrResult> results = OcrEngine.getInstance().recognizeRegion(screenshot, descRegion);
        screenshot.recycle();

        StringBuilder sb = new StringBuilder();
        // 按从上到下、从左到右排序
        results.sort((a, b) -> {
            if (a.getRect().top != b.getRect().top) {
                return a.getRect().top - b.getRect().top;
            }
            return a.getRect().left - b.getRect().left;
        });

        for (OcrResult r : results) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(r.getText());
        }

        String desc = sb.toString();
        Logger.i("描述内容: " + (desc.length() > 100 ? desc.substring(0, 100) + "..." : desc));
        return desc;
    }

    /**
     * 综合检测：先 OCR 找美女标签，
     * 找不到就展开描述区再找一次，最后返回判断结果
     */
    public static boolean isCurrentVideoBeauty() {
        boolean result = analyzeBeautyVideo();

        if (result) {
            Logger.i("🎯 当前视频: 美女内容");
        } else {
            Logger.i("🎯 当前视频: 非美女内容");
        }

        return result;
    }

    // ---- 内部数据结构 ----

    private static class BeautyCheckResult {
        boolean found;
        String matchedText;
        Rect location;
        String source;

        BeautyCheckResult(boolean found, String matchedText, Rect location, String source) {
            this.found = found;
            this.matchedText = matchedText;
            this.location = location;
            this.source = source;
        }
    }

    /** 发送检测状态到悬浮窗 */
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

    /** 更新 detectByAccessibility 方法中的状态 */
    private static void updateAccessibilityDetectStatus(int step, String keyword) {
        // step 0: 检测中, 1: 点击展开, 2: 已检测, 3: 未检测到
    }
}
