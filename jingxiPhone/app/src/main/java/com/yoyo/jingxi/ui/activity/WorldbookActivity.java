package com.yoyo.jingxi.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import com.yoyo.jingxi.R;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.WorldbookEntry;
import com.yoyo.jingxi.ui.adapter.WorldbookAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class WorldbookActivity extends AppCompatActivity {

    private RecyclerView rvWorldbook;
    private WorldbookAdapter adapter;
    private AppDatabase db;
    private List<WorldbookEntry> allEntries = new ArrayList<>();

    private int currentTabType = 0; // 0:前, 1:中, 2:后

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.yoyo.jingxi.utils.ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_worldbook_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("世界书");
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        db = AppDatabase.getDatabase(this);

        rvWorldbook = findViewById(R.id.rvWorldbook);
        rvWorldbook.setLayoutManager(new LinearLayoutManager(this));
        adapter = new WorldbookAdapter();
        rvWorldbook.setAdapter(adapter);

        findViewById(R.id.tvTabPre).setOnClickListener(v -> switchTab(0));
        findViewById(R.id.tvTabMid).setOnClickListener(v -> switchTab(1));
        findViewById(R.id.tvTabPost).setOnClickListener(v -> switchTab(2));

        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);
        fabAdd.setOnClickListener(v -> {
            Intent intent = new Intent(this, WorldbookEditActivity.class);
            intent.putExtra("type", currentTabType);
            startActivity(intent);
        });

        adapter.setOnItemClickListener(new WorldbookAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(WorldbookEntry entry) {
                Intent intent = new Intent(WorldbookActivity.this, WorldbookEditActivity.class);
                intent.putExtra("entry", entry);
                startActivity(intent);
            }

            @Override
            public void onItemLongClick(WorldbookEntry entry) {
                new AlertDialog.Builder(WorldbookActivity.this)
                        .setTitle("删除词条")
                        .setMessage("确定要删除这条设定吗？")
                        .setPositiveButton("删除", (dialog, which) -> {
                            Executors.newSingleThreadExecutor().execute(() -> {
                                db.worldbookDao().delete(entry);
                            });
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        });

        adapter.setOnSwitchChangeListener((entry, isChecked) -> {
            entry.isEnabled = isChecked;
            Executors.newSingleThreadExecutor().execute(() -> {
                db.worldbookDao().update(entry);
            });
        });

        db.worldbookDao().getAllEntries().observe(this, entries -> {
            allEntries = entries;
            updateList();
        });
        
        switchTab(0); // 默认前世界书
    }

    private void switchTab(int type) {
        currentTabType = type;
        
        android.widget.TextView tvTabPre = findViewById(R.id.tvTabPre);
        android.widget.TextView tvTabMid = findViewById(R.id.tvTabMid);
        android.widget.TextView tvTabPost = findViewById(R.id.tvTabPost);

        // 修改文字颜色和加粗状态
        int selectedColor = getResources().getColor(R.color.text_primary); // 棕色
        int unselectedColor = getResources().getColor(R.color.text_secondary); // 次要文本颜色

        tvTabPre.setTextColor(type == 0 ? selectedColor : unselectedColor);
        tvTabPre.setTypeface(null, type == 0 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);

        tvTabMid.setTextColor(type == 1 ? selectedColor : unselectedColor);
        tvTabMid.setTypeface(null, type == 1 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);

        tvTabPost.setTextColor(type == 2 ? selectedColor : unselectedColor);
        tvTabPost.setTypeface(null, type == 2 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);

        updateList();
    }

    private void updateList() {
        if (allEntries == null) return;
        List<WorldbookEntry> filtered = new ArrayList<>();
        for (WorldbookEntry entry : allEntries) {
            if (entry.type == currentTabType) {
                filtered.add(entry);
            }
        }
        adapter.setEntries(filtered);
    }
}
