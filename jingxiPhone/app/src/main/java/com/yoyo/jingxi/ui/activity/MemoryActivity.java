package com.yoyo.jingxi.ui.activity;

import android.app.AlertDialog;
import android.os.Bundle;
import com.yoyo.jingxi.R;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.Character;
import com.yoyo.jingxi.data.entity.EpisodicMemory;
import com.yoyo.jingxi.data.entity.MyPersona;
import com.yoyo.jingxi.data.entity.UserProfileNode;
import com.yoyo.jingxi.data.entity.Memory;
import com.yoyo.jingxi.ui.adapter.EpisodeAdapter;
import com.yoyo.jingxi.ui.adapter.MemoryAdapter;
import com.yoyo.jingxi.ui.adapter.UserProfileTableAdapter;
import com.yoyo.jingxi.utils.MemoryManager;
import com.yoyo.jingxi.utils.MemoryMigrationPlugin;
import com.yoyo.jingxi.utils.SpUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class MemoryActivity extends AppCompatActivity {

    private static final int PAGE_SIZE = 50;

    private Spinner spinnerCharacter;
    private Spinner spinnerPersona;
    private RecyclerView rvMemories;
    private TabLayout tabLayout;
    private AppDatabase db;
    private List<Character> characterList = new ArrayList<>();
    private List<MyPersona> personaList = new ArrayList<>();
    private int currentCharacterId = -1;
    private int currentTab = 0;
    private String currentPersonaName = ""; // 空字符串 = 全部人设
    private AlertDialog migrationDialog;
    private MemoryMigrationPlugin migrationPlugin;

    private UserProfileTableAdapter profileAdapter;
    private EpisodeAdapter episodeAdapter;
    private MemoryAdapter oldAdapter;

    private boolean isLoadingEpisodes = false;
    private boolean hasMoreEpisodes = true;

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
        spinnerPersona = findViewById(R.id.spinnerPersona);
        rvMemories = findViewById(R.id.rvMemories);
        tabLayout = findViewById(R.id.tabLayout);
        rvMemories.setLayoutManager(new LinearLayoutManager(this));

        // Infinite scroll for 心绪 tab
        rvMemories.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy <= 0 || currentTab != 1 || isLoadingEpisodes || !hasMoreEpisodes) return;
                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (lm == null) return;
                int totalItemCount = lm.getItemCount();
                int lastVisibleItem = lm.findLastVisibleItemPosition();
                if (lastVisibleItem >= totalItemCount - 5) {
                    loadMoreEpisodes();
                }
            }
        });

        db = AppDatabase.getDatabase(this);

        // Setup adapters
        profileAdapter = new UserProfileTableAdapter();
        episodeAdapter = new EpisodeAdapter();
        oldAdapter = new MemoryAdapter();
        oldAdapter.setOnMemoryLongClickListener(memory -> {
            if (memory.type == -1) return;
            if (memory.type <= 1) {
                new AlertDialog.Builder(this).setTitle("记忆")
                    .setItems(new String[]{"编辑", "删除"}, (d, which) -> {
                        if (which == 0) editOldMemory(memory); else deleteOldMemory(memory);
                    }).setNegativeButton("取消", null).show();
            }
        });

        // Tab setup
        tabLayout.addTab(tabLayout.newTab().setText("画像"));
        tabLayout.addTab(tabLayout.newTab().setText("心绪"));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                loadCurrentTab();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        // Long-click handlers
        profileAdapter.setOnItemClickListener(item -> {
            if (item.nodeId > 0) {
                // Existing: edit or delete
                showEditDeleteDialog("画像", () -> editProfileById(item.nodeId), () -> deleteProfileById(item.nodeId));
            } else {
                // Empty preset slot: add new
                addProfileItem(item.category, item.keyItem);
            }
        });
        episodeAdapter.setOnItemLongClickListener(ep -> showEditDeleteDialog("心绪", () -> editEpisode(ep), () -> deleteEpisode(ep)));

        // Buttons
        Button btnMigrate = findViewById(R.id.btnMigrate);
        btnMigrate.setOnClickListener(v -> {
            if (currentCharacterId <= 0) { Toast.makeText(this, "请先选择角色", Toast.LENGTH_SHORT).show(); return; }
            if (SpUtils.getBoolean("MEMORY_V2_MIGRATED", false)) {
                new AlertDialog.Builder(this).setTitle("已迁移过")
                    .setMessage("该角色已经完成过记忆迁移，确定要重新迁移吗？").setPositiveButton("确定", (d, w) -> startMigration()).setNegativeButton("取消", null).show();
            } else startMigration();
        });

        Button btnResetMigration = findViewById(R.id.btnResetMigration);
        btnResetMigration.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("重置迁移状态")
            .setMessage("这将清除当前角色的迁移进度记录（不会删除已迁移的数据）。").setPositiveButton("确定", (d, w) -> {
                SpUtils.putBoolean("MEMORY_V2_MIGRATED", false);
                SpUtils.putInt("MEMORY_MIGRATION_LAST_ID_" + currentCharacterId, -1);
                Toast.makeText(this, "已重置", Toast.LENGTH_SHORT).show();
            }).setNegativeButton("取消", null).show());

        Button btnClearOldMemories = findViewById(R.id.btnClearOldMemories);
        btnClearOldMemories.setOnClickListener(v -> {
            if (currentCharacterId <= 0) { Toast.makeText(this, "请先选择角色", Toast.LENGTH_SHORT).show(); return; }
            new AlertDialog.Builder(this).setTitle("清除旧记忆")
                .setMessage("将删除当前角色在旧记忆表中的所有数据。").setPositiveButton("确定清除", (d, w) -> {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        List<com.yoyo.jingxi.data.entity.Memory> old = db.memoryDao().getMemoriesByCharacterIdSyncAll(currentCharacterId);
                        int count = 0;
                        if (old != null) for (com.yoyo.jingxi.data.entity.Memory m : old) { db.memoryDao().delete(m); count++; }
                        final int c = count;
                        runOnUiThread(() -> Toast.makeText(this, "已清除 " + c + " 条旧记忆", Toast.LENGTH_LONG).show());
                    });
                }).setNegativeButton("取消", null).show();
        });

        loadCharacters();
    }

    private void showEditDeleteDialog(String typeName, Runnable onEdit, Runnable onDelete) {
        new AlertDialog.Builder(this).setTitle(typeName)
            .setItems(new String[]{"编辑", "删除"}, (d, which) -> {
                if (which == 0) onEdit.run(); else onDelete.run();
            }).setNegativeButton("取消", null).show();
    }

    // === Profile edit/delete/add ===
    private void editProfileById(int nodeId) {
        Executors.newSingleThreadExecutor().execute(() -> {
            UserProfileNode fresh = db.userProfileNodeDao().getByIdSync(nodeId);
            if (fresh == null) return;
            String cat = nns(fresh.category); String ki = nns(fresh.keyItem); String val = nns(fresh.valueContent); String emo = nns(fresh.emotionTag);
            runOnUiThread(() -> {
                LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(32, 16, 32, 0);
                android.widget.EditText etVal = fieldMulti(layout, "内容", val, 2);
                android.widget.EditText etEmo = field(layout, "情感标签", emo);
                new AlertDialog.Builder(this).setTitle("编辑 " + cat + " / " + ki).setView(layout).setPositiveButton("保存", (d2, w2) -> {
                    String nv = etVal.getText().toString().trim(); String ne = etEmo.getText().toString().trim();
                    if (!nv.isEmpty()) Executors.newSingleThreadExecutor().execute(() -> {
                        UserProfileNode p2 = db.userProfileNodeDao().getByIdSync(nodeId);
                        if (p2 != null) { p2.valueContent = nv; p2.emotionTag = ne.isEmpty() ? "普通" : ne; p2.lastUpdated = System.currentTimeMillis(); db.userProfileNodeDao().update(p2); runOnUiThread(this::loadCurrentTab); }
                    });
                }).setNegativeButton("取消", null).show();
            });
        });
    }

    private void deleteProfileById(int nodeId) {
        confirmDelete("画像", () -> Executors.newSingleThreadExecutor().execute(() -> {
            UserProfileNode f = db.userProfileNodeDao().getByIdSync(nodeId);
            if (f != null) { f.valueContent = ""; f.isActive = false; db.userProfileNodeDao().update(f); }
            runOnUiThread(this::loadCurrentTab);
        }));
    }

    private void addProfileItem(String category, String keyItem) {
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(32, 16, 32, 0);
        android.widget.EditText etVal = fieldMulti(layout, "内容", "", 2);
        android.widget.EditText etEmo = field(layout, "情感标签", "普通");
        new AlertDialog.Builder(this).setTitle("新增 " + category + " / " + keyItem).setView(layout).setPositiveButton("保存", (d2, w2) -> {
            String nv = etVal.getText().toString().trim(); String ne = etEmo.getText().toString().trim();
            if (!nv.isEmpty()) Executors.newSingleThreadExecutor().execute(() -> {
                MemoryManager.getInstance().init(db);
                // 用当前选中的人设；若为"全部"则取主人设
                String pn = currentPersonaName.isEmpty() ? getMainPersonaName() : currentPersonaName;
                MemoryManager.getInstance().addUserProfileNode(currentCharacterId, pn, category, keyItem, nv, ne.isEmpty() ? "普通" : ne, 7);
                runOnUiThread(this::loadCurrentTab);
            });
        }).setNegativeButton("取消", null).show();
    }

    // === Episode edit/delete ===
    private void editEpisode(EpisodicMemory ep) {
        Executors.newSingleThreadExecutor().execute(() -> {
            EpisodicMemory fresh = db.episodicMemoryDao().getByIdSync(ep.id);
            if (fresh == null) return;
            String title = nns(fresh.title); String diary = nns(fresh.subjectiveDiary); String emo = nns(fresh.emotionalTone);
            runOnUiThread(() -> {
                LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(32, 16, 32, 0);
                android.widget.EditText etTitle = field(layout, "标题", title);
                android.widget.EditText etDiary = fieldMulti(layout, "回忆内容", diary, 4);
                android.widget.EditText etEmo = field(layout, "情感色调", emo);
                new AlertDialog.Builder(this).setTitle("编辑心绪").setView(layout).setPositiveButton("保存", (d2, w2) -> {
                    String nt = etTitle.getText().toString().trim(); String nd = etDiary.getText().toString().trim(); String ne = etEmo.getText().toString().trim();
                    if (!nt.isEmpty() && !nd.isEmpty()) Executors.newSingleThreadExecutor().execute(() -> {
                        EpisodicMemory e2 = db.episodicMemoryDao().getByIdSync(ep.id);
                        if (e2 != null) { e2.title = nt; e2.subjectiveDiary = nd; e2.emotionalTone = ne.isEmpty() ? "平静" : ne; db.episodicMemoryDao().update(e2); }
                    });
                }).setNegativeButton("取消", null).show();
            });
        });
    }
    private void deleteEpisode(EpisodicMemory ep) { confirmDelete("心绪", () -> Executors.newSingleThreadExecutor().execute(() -> { EpisodicMemory f = db.episodicMemoryDao().getByIdSync(ep.id); if (f != null) db.episodicMemoryDao().delete(f); runOnUiThread(this::loadCurrentTab); })); }

    private void confirmDelete(String name, Runnable action) {
        new AlertDialog.Builder(this).setTitle("删除" + name).setMessage("确定删除这条" + name + "吗？").setPositiveButton("删除", (d, w) -> action.run()).setNegativeButton("取消", null).show();
    }

    private android.widget.EditText field(LinearLayout parent, String hint, String text) {
        android.widget.EditText et = new android.widget.EditText(this);
        et.setHint(hint); et.setText(text); et.setSingleLine(true);
        parent.addView(et); return et;
    }
    private android.widget.EditText fieldMulti(LinearLayout parent, String hint, String text, int minLines) {
        android.widget.EditText et = new android.widget.EditText(this);
        et.setHint(hint); et.setText(text);
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setMinLines(minLines);
        et.setHorizontallyScrolling(false);
        parent.addView(et); return et;
    }
    private String nns(String s) { return s != null ? s : ""; }

    private String getMainPersonaName() {
        try {
            MyPersona mp = db.myPersonaDao().getMainPersona();
            return mp != null ? mp.name : "我";
        } catch (Exception e) {
            return "我";
        }
    }

    private void editOldMemory(Memory memory) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setText(nns(memory.content)); input.setMinLines(3);
        new AlertDialog.Builder(this).setTitle("编辑记忆").setView(input).setPositiveButton("保存", (d, w) -> {
            String nc = input.getText().toString().trim();
            if (!nc.isEmpty()) Executors.newSingleThreadExecutor().execute(() -> {
                Memory m = db.memoryDao().getMemoryByIdSync(memory.id);
                if (m != null) { m.content = nc; db.memoryDao().update(m); }
            });
        }).setNegativeButton("取消", null).show();
    }
    private void deleteOldMemory(Memory memory) {
        confirmDelete("记忆", () -> Executors.newSingleThreadExecutor().execute(() -> { db.memoryDao().delete(memory); }));
    }

    // === Migration ===
    private void startMigration() {
        migrationPlugin = new MemoryMigrationPlugin(this); int cid = currentCharacterId;
        AlertDialog.Builder builder = new AlertDialog.Builder(this); builder.setTitle("迁移旧记忆"); builder.setMessage("正在准备..."); builder.setCancelable(false);
        builder.setNegativeButton("取消", (d, w) -> { if (migrationPlugin != null) migrationPlugin.cancel(); migrationDialog = null; });
        migrationDialog = builder.show();
        migrationPlugin.runMigration(cid, new MemoryMigrationPlugin.MigrationCallback() {
            @Override public void onProgress(int cur, int total, String item) {
                runOnUiThread(() -> { if (migrationDialog != null && migrationDialog.isShowing()) migrationDialog.setMessage("处理 " + cur + "/" + total + "\n" + (item.length() > 25 ? item.substring(0, 25) + "..." : item)); });
            }
            @Override public void onComplete(int profiles, int episodes, int failed) {
                if (migrationPlugin != null) { migrationPlugin.shutdown(); migrationPlugin = null; }
                runOnUiThread(() -> { if (migrationDialog != null && migrationDialog.isShowing()) migrationDialog.dismiss(); migrationDialog = null;
                    StringBuilder msg = new StringBuilder().append("画像 ").append(profiles).append(" 条\n心绪 ").append(episodes).append(" 条");
                    if (failed > 0) msg.append("\n\n").append(failed).append(" 条失败，下次可继续");
                    new AlertDialog.Builder(MemoryActivity.this).setTitle(failed > 0 ? "迁移完成（有失败）" : "迁移完成").setMessage(msg.toString()).setPositiveButton("确定", null).show();
                    loadCurrentTab(); });
            }
            @Override public void onError(String msg) {
                if (migrationPlugin != null) { migrationPlugin.shutdown(); migrationPlugin = null; }
                runOnUiThread(() -> { if (migrationDialog != null && migrationDialog.isShowing()) migrationDialog.dismiss(); migrationDialog = null;
                    new AlertDialog.Builder(MemoryActivity.this).setTitle("迁移失败").setMessage(msg).setPositiveButton("确定", null).show(); });
            }
        });
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (migrationPlugin != null) { migrationPlugin.cancel(); migrationPlugin.shutdown(); migrationPlugin = null; }
        if (migrationDialog != null && migrationDialog.isShowing()) { migrationDialog.dismiss(); migrationDialog = null; }
    }

    // === Character loading ===
    private void loadCharacters() {
        db.characterDao().getAllCharacters().observe(this, characters -> {
            characterList.clear();
            if (characters != null) characterList.addAll(characters);
            List<String> names = new ArrayList<>();
            for (Character ch : characterList) names.add(ch.name);
            ArrayAdapter<String> sa = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
            sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerCharacter.setAdapter(sa);
            if (!characterList.isEmpty() && currentCharacterId == -1) currentCharacterId = characterList.get(0).id;
            spinnerCharacter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(android.widget.AdapterView<?> p, android.view.View v, int pos, long id) { currentCharacterId = characterList.get(pos).id; loadCurrentTab(); }
                @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
            });
            if (currentCharacterId != -1) loadCurrentTab();
        });

        // 加载人设列表
        Executors.newSingleThreadExecutor().execute(() -> {
            List<MyPersona> all = db.myPersonaDao().getAllPersonasSync();
            personaList.clear();
            if (all != null) personaList.addAll(all);
            List<String> personaNames = new ArrayList<>();
            personaNames.add("全部人设");
            for (MyPersona p : personaList) personaNames.add(p.name);
            runOnUiThread(() -> {
                ArrayAdapter<String> pa = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, personaNames);
                pa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerPersona.setAdapter(pa);
                spinnerPersona.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(android.widget.AdapterView<?> p, android.view.View v, int pos, long id) {
                        currentPersonaName = pos == 0 ? "" : personaList.get(pos - 1).name;
                        loadCurrentTab();
                    }
                    @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
                });
            });
        });
    }

    private void loadCurrentTab() {
        if (currentCharacterId <= 0) return;
        int cid = currentCharacterId;
        boolean migrated = SpUtils.getBoolean("MEMORY_V2_MIGRATED", false);

        if (!migrated) {
            // Show old memories in original format — use proper section headers
            tabLayout.setVisibility(android.view.View.GONE);
            db.memoryDao().getMemoriesByCharacterIdAll(cid).observe(this, memories -> {
                List<com.yoyo.jingxi.ui.adapter.MemoryAdapter.MemoryDisplayItem> processed = new ArrayList<>();
                if (memories != null) {
                    java.util.Map<String, List<Memory>> grouped = new java.util.LinkedHashMap<>();
                    List<Memory> normal = new ArrayList<>();
                    for (Memory m : memories) {
                        if (m.type == 1) {
                            String cat = (m.category != null && !m.category.trim().isEmpty()) ? m.category.trim() : "其他";
                            if (!grouped.containsKey(cat)) grouped.put(cat, new ArrayList<>());
                            grouped.get(cat).add(m);
                        } else normal.add(m);
                    }
                    for (java.util.Map.Entry<String, List<Memory>> e : grouped.entrySet()) {
                        processed.add(com.yoyo.jingxi.ui.adapter.MemoryAdapter.MemoryDisplayItem.header("【核心记忆】 " + e.getKey()));
                        for (Memory m : e.getValue()) {
                            processed.add(com.yoyo.jingxi.ui.adapter.MemoryAdapter.MemoryDisplayItem.memoryItem(m));
                        }
                    }
                    if (!normal.isEmpty()) {
                        processed.add(com.yoyo.jingxi.ui.adapter.MemoryAdapter.MemoryDisplayItem.header("【近期总结】 普通记忆"));
                        for (Memory m : normal) {
                            processed.add(com.yoyo.jingxi.ui.adapter.MemoryAdapter.MemoryDisplayItem.memoryItem(m));
                        }
                    }
                }
                oldAdapter.setItems(processed);
            });
            rvMemories.setLayoutManager(new LinearLayoutManager(MemoryActivity.this));
            rvMemories.setAdapter(oldAdapter);
        } else {
            // Migrated: show new tabbed view
            tabLayout.setVisibility(android.view.View.VISIBLE);
            Executors.newSingleThreadExecutor().execute(() -> {
                MemoryManager memMgr = MemoryManager.getInstance(); memMgr.init(db);
                final String filterPersona = currentPersonaName;
                switch (currentTab) {
                    case 0:
                        List<UserProfileNode> profiles;
                        if (filterPersona.isEmpty()) {
                            profiles = memMgr.getAllActiveUserProfilesByCharacter(cid);
                        } else {
                            profiles = memMgr.getAllActiveUserProfiles(cid, filterPersona);
                        }
                        runOnUiThread(() -> {
                            rvMemories.setLayoutManager(new LinearLayoutManager(MemoryActivity.this));
                            rvMemories.setAdapter(profileAdapter);
                            profileAdapter.buildRows(profiles, filterPersona.isEmpty());
                        });
                        break;
                    case 1:
                        // Reset pagination state when switching character or tab
                        isLoadingEpisodes = false;
                        hasMoreEpisodes = true;
                        List<EpisodicMemory> episodes;
                        int totalCount;
                        if (filterPersona.isEmpty()) {
                            episodes = memMgr.getEpisodesPagedAll(cid, PAGE_SIZE, 0);
                            totalCount = db.episodicMemoryDao().getCountAll(cid);
                        } else {
                            episodes = memMgr.getEpisodesPaged(cid, filterPersona, PAGE_SIZE, 0);
                            totalCount = db.episodicMemoryDao().getCount(cid, filterPersona);
                        }
                        boolean more = episodes.size() < totalCount;
                        runOnUiThread(() -> {
                            rvMemories.setLayoutManager(new LinearLayoutManager(MemoryActivity.this));
                            rvMemories.setAdapter(episodeAdapter);
                            episodeAdapter.setEpisodes(episodes, filterPersona.isEmpty());
                            hasMoreEpisodes = more;
                        });
                        break;
                }
            });
        }
    }

    private void loadMoreEpisodes() {
        if (isLoadingEpisodes || !hasMoreEpisodes || currentCharacterId <= 0) return;
        isLoadingEpisodes = true;
        int offset = episodeAdapter.getEpisodeCount();
        int cid = currentCharacterId;
        final String filterPersona = currentPersonaName;
        Executors.newSingleThreadExecutor().execute(() -> {
            List<EpisodicMemory> more;
            int totalCount;
            if (filterPersona.isEmpty()) {
                more = db.episodicMemoryDao().getByCharacterIdPagedAll(cid, PAGE_SIZE, offset);
                totalCount = db.episodicMemoryDao().getCountAll(cid);
            } else {
                more = db.episodicMemoryDao().getByCharacterIdPaged(cid, filterPersona, PAGE_SIZE, offset);
                totalCount = db.episodicMemoryDao().getCount(cid, filterPersona);
            }
            runOnUiThread(() -> {
                isLoadingEpisodes = false;
                if (more != null && !more.isEmpty()) {
                    episodeAdapter.addEpisodes(more, filterPersona.isEmpty());
                    hasMoreEpisodes = episodeAdapter.getEpisodeCount() < totalCount;
                } else {
                    hasMoreEpisodes = false;
                }
            });
        });
    }
}
