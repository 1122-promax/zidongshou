package com.cmlanche.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import android.view.View;

import com.alibaba.fastjson.JSON;
import com.cmlanche.adapter.AppListAdapter;
import com.cmlanche.application.MyApplication;
import com.cmlanche.jixieshou.R;
import com.cmlanche.model.AppInfo;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class TaskTypeListActivity extends AppCompatActivity {

    private ListView listView;
    private List<AppInfo> appInfos = new ArrayList<>();
    private AppListAdapter appListAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_type_list);
        findViewById(R.id.backImg).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        appListAdapter = new AppListAdapter(this, appInfos);
        listView = findViewById(R.id.typeListView);
        listView.setAdapter(appListAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                choose(appListAdapter.getItem(i));
            }
        });

        // 加载默认任务类型
        loadDefaultTasks();

        // 壁纸轮播
        View contentView = findViewById(android.R.id.content);
        MyApplication.getAppInstance().startWallpaperCycle(contentView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        View contentView = findViewById(android.R.id.content);
        MyApplication.getAppInstance().startWallpaperCycle(contentView);
    }

    @Override
    protected void onPause() {
        super.onPause();
        MyApplication.getAppInstance().stopWallpaperCycle();
    }

    private void loadDefaultTasks() {
        addTaskType("快手极速版", "com.kuaishou.nebula");
        addTaskType("快手", "com.smile.gifmaker");
        addTaskType("抖音极速版", "com.ss.android.ugc.aweme.lite");
        addTaskType("抖音", "com.ss.android.ugc.aweme");
    }

    private void addTaskType(String name, String pkg) {
        AppInfo info = new AppInfo();
        info.setName(name);
        info.setPkgName(pkg);
        appInfos.add(info);
    }

    public void updateList(List<AppInfo> list) {
        appInfos.clear();
        appInfos.addAll(list);
        appListAdapter.notifyDataSetChanged();
    }

    private void choose(AppInfo info) {
        Intent data = new Intent();
        data.putExtra("appInfo", JSON.toJSONString(info));
        setResult(1, data);
        finish();
    }

}
