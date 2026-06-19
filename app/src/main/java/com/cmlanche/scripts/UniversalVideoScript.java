package com.cmlanche.scripts;

import com.cmlanche.core.search.node.NodeInfo;
import com.cmlanche.core.utils.ActionUtils;
import com.cmlanche.core.utils.Logger;
import com.cmlanche.core.utils.Utils;
import com.cmlanche.model.AppInfo;

public class UniversalVideoScript extends BaseScript {

    private boolean isCheckedWozhidaole;

    public UniversalVideoScript(AppInfo appInfo) {
        super(appInfo);
    }

    @Override
    protected void executeScript() {
        if (!isCheckedWozhidaole) {
            NodeInfo nodeInfo = findByText("我知道了");
            if (nodeInfo != null) {
                isCheckedWozhidaole = true;
                ActionUtils.click(nodeInfo);
                Utils.sleep(300);
                return;
            }
            nodeInfo = findByText("同意");
            if (nodeInfo != null) {
                ActionUtils.click(nodeInfo);
                Utils.sleep(300);
                return;
            }
            nodeInfo = findByText("允许");
            if (nodeInfo != null) {
                ActionUtils.click(nodeInfo);
                Utils.sleep(300);
                return;
            }
            nodeInfo = findByText("确定");
            if (nodeInfo != null) {
                ActionUtils.click(nodeInfo);
                Utils.sleep(300);
                return;
            }
        }

        if (!isLoggedIn) {
            handleLogin();
        }

        swipeToNext();
        Utils.sleep(2000);
    }

    @Override
    protected int getMinSleepTime() { return 2500; }

    @Override
    protected int getMaxSleepTime() { return 5000; }

    @Override
    public boolean isDestinationPage() {
        if (!isTargetPkg()) return false;
        if (!isLoggedIn) return false;
        return true;
    }
}
