package com.yoyo.jingxi.ui.activity;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;
import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.EmojiEntry;
import com.yoyo.jingxi.ui.adapter.EmojiAdapter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.Executors;

public class EmojiManageActivity extends AppCompatActivity {

    private AppDatabase db;
    private EmojiAdapter adapter;
    private RecyclerView rvEmojis;
    private TabLayout tabLayoutGroups;

    private final ActivityResultLauncher<Intent> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri selectedImage = result.getData().getData();
                    if (selectedImage != null) {
                        showAddSingleDialog(selectedImage);
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.yoyo.jingxi.utils.ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emoji_manage);

        db = AppDatabase.getDatabase(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("管理表情包");
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        Button btnAddSingle = findViewById(R.id.btnAddSingle);
        Button btnBatchImport = findViewById(R.id.btnBatchImport);
        Button btnManageGroups = findViewById(R.id.btnManageGroups);
        rvEmojis = findViewById(R.id.rvEmojis);
        tabLayoutGroups = findViewById(R.id.tabLayoutGroups);

        rvEmojis.setLayoutManager(new GridLayoutManager(this, 4));
        adapter = new EmojiAdapter();
        rvEmojis.setAdapter(adapter);

        tabLayoutGroups.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getText() != null) {
                    loadEmojisByGroup(tab.getText().toString());
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        loadGroups();

        adapter.setOnEmojiClickListener(new EmojiAdapter.OnEmojiClickListener() {
            @Override
            public void onEmojiClick(EmojiEntry emoji) {
                // do nothing or preview
            }

            @Override
            public void onEmojiLongClick(EmojiEntry emoji, android.view.View view) {
                showDeleteDialog(emoji);
            }
        });
        
        btnManageGroups.setOnLongClickListener(v -> {
            Executors.newSingleThreadExecutor().execute(() -> {
                java.util.List<String> groups = db.emojiDao().getAllGroupNamesSync();
                runOnUiThread(() -> {
                    if (groups == null || groups.isEmpty()) {
                        Toast.makeText(this, "暂无表情分组", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String[] groupArray = groups.toArray(new String[0]);
                    new AlertDialog.Builder(this)
                            .setTitle("选择要删除的分组")
                            .setItems(groupArray, (dialog, which) -> {
                                String selectedGroup = groupArray[which];
                                confirmDeleteGroup(selectedGroup);
                            })
                            .setNegativeButton("取消", null)
                            .show();
                });
            });
            return true;
        });

        btnAddSingle.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            pickImageLauncher.launch(intent);
        });

        btnBatchImport.setOnClickListener(v -> showBatchImportDialog());
        btnManageGroups.setOnClickListener(v -> showManageGroupsDialog());
    }

    private void loadGroups() {
        Executors.newSingleThreadExecutor().execute(() -> {
            java.util.List<String> groups = db.emojiDao().getAllGroupNamesSync();
            runOnUiThread(() -> {
                tabLayoutGroups.removeAllTabs();
                
                TabLayout.Tab allTab = tabLayoutGroups.newTab().setText("全部表情");
                tabLayoutGroups.addTab(allTab);
                
                if (groups != null) {
                    for (String group : groups) {
                        tabLayoutGroups.addTab(tabLayoutGroups.newTab().setText(group));
                    }
                }
                
                // 默认选中"全部表情"
                tabLayoutGroups.selectTab(allTab);
                loadEmojisByGroup("全部表情");
            });
        });
    }

    private void showManageGroupsDialog() {
        Executors.newSingleThreadExecutor().execute(() -> {
            java.util.List<String> groups = db.emojiDao().getAllGroupNamesSync();
            runOnUiThread(() -> {
                if (groups == null || groups.isEmpty()) {
                    Toast.makeText(this, "暂无表情分组", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                String[] groupArray = groups.toArray(new String[0]);
                new AlertDialog.Builder(this)
                        .setTitle("选择要删除的分组")
                        .setItems(groupArray, (dialog, which) -> {
                            String selectedGroup = groupArray[which];
                            confirmDeleteGroup(selectedGroup);
                        })
                        .setNegativeButton("取消", null)
                        .show();
            });
        });
    }

    private void loadEmojisByGroup(String groupName) {
        // 确保清除所有旧的观察者，因为LiveDatas可能不一样
        db.emojiDao().getAllEmojis().removeObservers(this);
        
        // 同样移除 getEmojisByGroup 返回的可能活跃的观察者，但因为这里没有直接引用到特定的LiveDate实例
        // 为确保干净，直接执行一次查询或者绑定一个新的观察者 (如果是不断切换，可能会导致内存泄漏或多重更新)
        // 更好的方式是使用 LiveData 的 switchMap，或者只在此进行一次性更新 (如果是用于管理界面的刷新)
        if ("全部表情".equals(groupName)) {
            db.emojiDao().getAllEmojis().observe(this, emojis -> adapter.setEmojis(emojis));
        } else {
            db.emojiDao().getEmojisByGroup(groupName).observe(this, emojis -> adapter.setEmojis(emojis));
        }
    }

    private void confirmDeleteGroup(String groupName) {
        new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("确定要删除分组 [" + groupName + "] 以及其下的所有表情吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        db.emojiDao().deleteByGroup(groupName);
                        runOnUiThread(() -> {
                            Toast.makeText(EmojiManageActivity.this, "已删除分组: " + groupName, Toast.LENGTH_SHORT).show();
                            loadGroups();
                        });
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showDeleteDialog(EmojiEntry emoji) {
        new AlertDialog.Builder(this)
                .setTitle("删除表情")
                .setMessage("确定要删除表情 [" + emoji.name + "] 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        db.emojiDao().delete(emoji);
                        if (emoji.imageUrl.startsWith(getFilesDir().getAbsolutePath())) {
                            new File(emoji.imageUrl).delete();
                        }
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showAddSingleDialog(Uri imageUri) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("输入表情含义");
        final EditText input = new EditText(this);
        input.setHint("例如：飞吻");
        builder.setView(input);

        builder.setPositiveButton("保存", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                Toast.makeText(this, "含义不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            saveSingleEmoji(name, imageUri);
        });
        builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void saveSingleEmoji(String name, Uri imageUri) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                InputStream is = getContentResolver().openInputStream(imageUri);
                File emojiDir = new File(getFilesDir(), "emojis");
                if (!emojiDir.exists()) emojiDir.mkdirs();

                File destFile = new File(emojiDir, "emoji_" + System.currentTimeMillis() + ".jpg");
                FileOutputStream fos = new FileOutputStream(destFile);
                byte[] buffer = new byte[1024];
                int length;
                while ((length = is.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                }
                fos.close();
                is.close();

                EmojiEntry entry = new EmojiEntry();
                entry.name = name;
                entry.imageUrl = destFile.getAbsolutePath();
                db.emojiDao().insert(entry);

                runOnUiThread(() -> {
                    Toast.makeText(EmojiManageActivity.this, "添加成功", Toast.LENGTH_SHORT).show();
                    loadGroups();
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(EmojiManageActivity.this, "保存图片失败", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showBatchImportDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("批量导入表情");

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);

        final EditText groupInput = new EditText(this);
        groupInput.setHint("输入分组名称(可选)");
        layout.addView(groupInput);

        final EditText input = new EditText(this);
        input.setHint("格式：\n含义1：URL1\n含义2：URL2\n(支持中英文冒号)");
        input.setMinLines(5);
        input.setMaxLines(15);
        input.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        layout.addView(input);

        builder.setView(layout);

        builder.setPositiveButton("导入", (dialog, which) -> {
            String groupName = groupInput.getText().toString().trim();
            String text = input.getText().toString();
            processBatchImport(text, groupName);
        });
        builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void processBatchImport(String text, String groupName) {
        if (TextUtils.isEmpty(text)) return;

        Executors.newSingleThreadExecutor().execute(() -> {
            String[] lines = text.split("\n");
            int count = 0;
            for (String line : lines) {
                line = line.trim();
                if (TextUtils.isEmpty(line)) continue;
                
                // 支持中英文冒号分隔
                String[] parts = line.split("[:：]", 2);
                if (parts.length == 2) {
                    String name = parts[0].trim();
                    String url = parts[1].trim();
                    
                    if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(url)) {
                        EmojiEntry entry = new EmojiEntry();
                        entry.name = name;
                        entry.imageUrl = url;
                        if (!TextUtils.isEmpty(groupName)) {
                            entry.groupName = groupName;
                        }
                        db.emojiDao().insert(entry);
                        count++;
                    }
                }
            }
            
            final int finalCount = count;
            runOnUiThread(() -> {
                Toast.makeText(EmojiManageActivity.this, "成功导入 " + finalCount + " 个表情", Toast.LENGTH_SHORT).show();
                loadGroups();
            });
        });
    }
}
