package com.cmlanche.core.search;

import android.view.accessibility.AccessibilityNodeInfo;

import com.cmlanche.application.MyApplication;
import com.cmlanche.core.search.node.Dumper;
import com.cmlanche.core.search.node.NodeInfo;
import com.cmlanche.core.search.node.TreeInfo;
import com.cmlanche.core.utils.Utils;

public class FindById {

    public static NodeInfo find(String id) {
        AccessibilityNodeInfo[] roots = MyApplication.getAppInstance().getAccessbilityService().getRoots();
        if (roots == null) {
            return null;
        }

        TreeInfo treeInfo = new Dumper(roots).dump();

        if (treeInfo != null && treeInfo.getRects() != null) {
            for (NodeInfo rect : treeInfo.getRects()) {
                if (isMatch(rect, id)) {
                    return rect;
                }
            }
        }
        return null;
    }

    private static boolean isMatch(NodeInfo nodeInfo, String id) {
        if (nodeInfo == null) {
            return false;
        }
        String rid = nodeInfo.getId();
        return Utils.textMatch(id, rid);
    }

}
