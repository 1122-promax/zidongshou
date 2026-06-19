package com.cmlanche.core.search;

import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import com.cmlanche.application.MyApplication;
import com.cmlanche.core.search.node.Dumper;
import com.cmlanche.core.search.node.NodeInfo;
import com.cmlanche.core.search.node.TreeInfo;
import com.cmlanche.core.utils.Utils;

public class FindByText {

    public static NodeInfo find(String text) {
        AccessibilityNodeInfo[] roots = MyApplication.getAppInstance().getAccessbilityService().getRoots();
        if (roots == null) {
            return null;
        }

        TreeInfo treeInfo = new Dumper(roots).dump();

        if (treeInfo != null && treeInfo.getRects() != null) {
            for (NodeInfo rect : treeInfo.getRects()) {
                if (isMatch(rect, text)) {
                    return rect;
                }
            }
        }
        return null;
    }

    public static NodeInfo findByTextContains(String textContains) {
        return find("*" + textContains + "*");
    }

    private static boolean isMatch(NodeInfo nodeInfo, String text) {
        if (nodeInfo == null) {
            return false;
        }
        return Utils.textMatch(text, nodeInfo.getText());
    }

}
