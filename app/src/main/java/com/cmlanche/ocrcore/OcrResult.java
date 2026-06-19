package com.cmlanche.ocrcore;

import android.graphics.Rect;

/**
 * OCR 识别结果：文本 + 屏幕坐标矩形
 */
public class OcrResult {
    private final String text;
    private final Rect rect;
    private final float confidence;

    public OcrResult(String text, Rect rect, float confidence) {
        this.text = text;
        this.rect = rect;
        this.confidence = confidence;
    }

    public String getText() {
        return text;
    }

    public Rect getRect() {
        return rect;
    }

    public int getCenterX() {
        return rect.centerX();
    }

    public int getCenterY() {
        return rect.centerY();
    }

    public float getConfidence() {
        return confidence;
    }

    @Override
    public String toString() {
        return "OcrResult{" +
                "text='" + text + '\'' +
                ", rect=" + rect +
                ", confidence=" + confidence +
                '}';
    }
}
