package com.yoyo.jingxi.ui.activity;

import android.app.AlertDialog;
import android.os.Bundle;
import com.yoyo.jingxi.R;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.Character;
import com.yoyo.jingxi.ui.adapter.MemoryAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class MemoryActivity extends AppCompatActivity {

    private Spinner spinnerCharacter;
    private RecyclerView rvMemories;
    private MemoryAdapter adapter;
    private AppDatabase db;
    private List<Character> characterList = new ArrayList<>();
    private int currentCharacterId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.yoyo.jingxi.utils.ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_memory);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("记忆库");
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        spinnerCharacter = findViewById(R.id.spinnerCharacter);
        rvMemories = findViewById(R.id.rvMemories);

        rvMemories.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MemoryAdapter();
        rvMemories.setAdapter(adapter);

        db = AppDatabase.getDatabase(this);

        adapter.setOnMemoryLongClickListener(memory -> {
            new AlertDialog.Builder(this)
                    .setTitle("删除记忆")
                    .setMessage("确定要删除这条记忆吗？")
                    .setPositiveButton("删除", (dialog, which) -> {
                        Executors.newSingleThreadExecutor().execute(() -> {
                            db.memoryDao().delete(memory);
                        });
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        loadCharacters();
    }

    private void loadCharacters() {
        db.characterDao().getAllCharacters().observe(this, characters -> {
            characterList.clear();
            if (characters != null) {
                characterList.addAll(characters);
            }

            List<String> names = new ArrayList<>();
            for (Character ch : characterList) {
                names.add(ch.name);
            }

            ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerCharacter.setAdapter(spinnerAdapter);

            if (!characterList.isEmpty() && currentCharacterId == -1) {
                currentCharacterId = characterList.get(0).id;
            }

            spinnerCharacter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    currentCharacterId = characterList.get(position).id;
                    observeMemories(currentCharacterId);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
            
            // 初次加载
            if (currentCharacterId != -1) {
                observeMemories(currentCharacterId);
            }
        });
    }

    private void observeMemories(int characterId) {
        db.memoryDao().getMemoriesByCharacterId(characterId).observe(this, memories -> {
            List<com.yoyo.jingxi.data.entity.Memory> processedMemories = new ArrayList<>();
            if (memories != null) {
                // 分类存储核心记忆
                java.util.Map<String, List<com.yoyo.jingxi.data.entity.Memory>> groupedCoreMemories = new java.util.HashMap<>();
                List<com.yoyo.jingxi.data.entity.Memory> normalMemories = new ArrayList<>();

                for (com.yoyo.jingxi.data.entity.Memory mem : memories) {
                    if (mem.type == 1) { // 核心记忆
                        String category = (mem.category != null && !mem.category.trim().isEmpty()) ? mem.category.trim() : "其他";
                        if (!groupedCoreMemories.containsKey(category)) {
                            groupedCoreMemories.put(category, new ArrayList<>());
                        }
                        groupedCoreMemories.get(category).add(mem);
                    } else { // 普通记忆
                        normalMemories.add(mem);
                    }
                }

                // 添加核心记忆分类标题和内容
                for (java.util.Map.Entry<String, List<com.yoyo.jingxi.data.entity.Memory>> entry : groupedCoreMemories.entrySet()) {
                    // 创建一个虚拟的 Memory 作为分类标题
                    com.yoyo.jingxi.data.entity.Memory header = new com.yoyo.jingxi.data.entity.Memory();
                    header.type = -1; // -1 表示标题
                    header.content = "【核心记忆】 " + entry.getKey();
                    processedMemories.add(header);
                    processedMemories.addAll(entry.getValue());
                }

                // 添加普通记忆标题和内容
                if (!normalMemories.isEmpty()) {
                    com.yoyo.jingxi.data.entity.Memory header = new com.yoyo.jingxi.data.entity.Memory();
                    header.type = -1; // -1 表示标题
                    header.content = "【近期总结】 普通记忆";
                    processedMemories.add(header);
                    processedMemories.addAll(normalMemories);
                }
            }
            adapter.setMemories(processedMemories);
        });
    }
}
