package com.yoyo.jingxi.ui.activity;

import android.os.Bundle;
import com.yoyo.jingxi.R;
import android.util.SparseBooleanArray;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.WorldbookEntry;
import com.yoyo.jingxi.utils.SpUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;

public class ChatSettingsActivity extends AppCompatActivity {

    private int sessionId;
    private android.widget.LinearLayout llChatWorldbooks;
    private android.widget.SeekBar sbMaxAiMessages;
    private android.widget.TextView tvMaxAiMessages;
    private com.google.android.material.switchmaterial.SwitchMaterial switchAutoMessage;
    private android.view.View llAutoMessageConfig;
    private com.google.android.material.textfield.TextInputEditText etAutoMessageInterval;
    private com.google.android.material.textfield.TextInputEditText etAutoMessageStartTime;
    private com.google.android.material.textfield.TextInputEditText etAutoMessageEndTime;
    private AppDatabase db;
    
    private void showTimePicker(com.google.android.material.textfield.TextInputEditText editText) {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        String currentText = editText.getText().toString();
        if (!currentText.isEmpty() && currentText.contains(":")) {
            String[] parts = currentText.split(":");
            try {
                calendar.set(java.util.Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
                calendar.set(java.util.Calendar.MINUTE, Integer.parseInt(parts[1]));
            } catch (NumberFormatException e) {
                // Ignore and use current time
            }
        }
        
        android.app.TimePickerDialog timePickerDialog = new android.app.TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    String time = String.format(java.util.Locale.getDefault(), "%02d:%02d", hourOfDay, minute);
                    editText.setText(time);
                },
                calendar.get(java.util.Calendar.HOUR_OF_DAY),
                calendar.get(java.util.Calendar.MINUTE),
                true
        );
        timePickerDialog.show();
    }
    private List<WorldbookEntry> enabledEntries = new ArrayList<>();
    private List<String> allEmojiGroups = new ArrayList<>();
    private List<String> selectedEmojiGroups = new ArrayList<>();

    private android.widget.ImageView ivChatBgPreview;
    private android.net.Uri currentBgUri;
    private android.content.SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.yoyo.jingxi.utils.ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_settings);

        sessionId = getIntent().getIntExtra("session_id", -1);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("聊天设置");
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        llChatWorldbooks = findViewById(R.id.llChatWorldbooks);
        sbMaxAiMessages = findViewById(R.id.sbMaxAiMessages);
        tvMaxAiMessages = findViewById(R.id.tvMaxAiMessages);
        ivChatBgPreview = findViewById(R.id.ivChatBgPreview);
        
        switchAutoMessage = findViewById(R.id.switchAutoMessage);
        llAutoMessageConfig = findViewById(R.id.llAutoMessageConfig);
        etAutoMessageInterval = findViewById(R.id.etAutoMessageInterval);
        etAutoMessageStartTime = findViewById(R.id.etAutoMessageStartTime);
        etAutoMessageEndTime = findViewById(R.id.etAutoMessageEndTime);
        db = AppDatabase.getDatabase(this);
        prefs = getSharedPreferences("theme_prefs", MODE_PRIVATE);
        
        // 聊天背景设置
        String savedBgStr = SpUtils.getString("CHAT_BG_" + sessionId, null);
        if (savedBgStr != null) {
            currentBgUri = android.net.Uri.parse(savedBgStr);
            if (!isFinishing() && !isDestroyed()) {
                com.bumptech.glide.Glide.with(ChatSettingsActivity.this.getApplicationContext())
                    .load(currentBgUri)
                    .centerCrop()
                    .into(ivChatBgPreview);
            }
        } else {
            // 如果没有单独设置，则显示全局的主题背景
            String globalBg = prefs.getString("custom_desktop_bg", null);
            if (globalBg != null) {
                if (!isFinishing() && !isDestroyed()) {
                    com.bumptech.glide.Glide.with(ChatSettingsActivity.this.getApplicationContext())
                        .load(android.net.Uri.parse(globalBg))
                        .centerCrop()
                        .into(ivChatBgPreview);
                }
            }
        }
        
        findViewById(R.id.llChatBackground).setOnClickListener(v -> {
            pickBackground();
        });

        // 主动发消息设置
        boolean isAutoMessageEnabled = SpUtils.getBoolean("AUTO_MESSAGE_ENABLED_" + sessionId, false);
        switchAutoMessage.setChecked(isAutoMessageEnabled);
        llAutoMessageConfig.setVisibility(isAutoMessageEnabled ? android.view.View.VISIBLE : android.view.View.GONE);
        
        switchAutoMessage.setOnCheckedChangeListener((buttonView, isChecked) -> {
            llAutoMessageConfig.setVisibility(isChecked ? android.view.View.VISIBLE : android.view.View.GONE);
        });
        
        float autoMessageInterval = SpUtils.getFloat("AUTO_MESSAGE_INTERVAL_" + sessionId, 4.0f);
        etAutoMessageInterval.setText(String.valueOf(autoMessageInterval));
        etAutoMessageStartTime.setText(SpUtils.getString("AUTO_MESSAGE_START_" + sessionId, "08:00"));
        etAutoMessageEndTime.setText(SpUtils.getString("AUTO_MESSAGE_END_" + sessionId, "22:00"));
        
        etAutoMessageStartTime.setOnClickListener(v -> showTimePicker(etAutoMessageStartTime));
        etAutoMessageEndTime.setOnClickListener(v -> showTimePicker(etAutoMessageEndTime));

        // AI连发消息上限设置
        int currentMaxMessages = SpUtils.getInt("CHAT_MAX_AI_MESSAGES_" + sessionId, 5);
        // SeekBar范围 0-14, 对应 1-15
        sbMaxAiMessages.setProgress(currentMaxMessages - 1);
        tvMaxAiMessages.setText(currentMaxMessages + " 条");
        
        sbMaxAiMessages.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                tvMaxAiMessages.setText((progress + 1) + " 条");
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });

        // 历史通话
        findViewById(R.id.llCallHistory).setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(this, CallHistoryActivity.class);
            intent.putExtra("session_id", sessionId);
            startActivity(intent);
        });

        findViewById(R.id.llClearMemory).setOnClickListener(v -> {
            showEmojiGroupDialog();
        });

        loadWorldbooks();
        loadEmojiGroups();
    }

    private void pickBackground() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, 100);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            android.net.Uri sourceUri = data.getData();
            if (sourceUri != null) {
                startCropBg(sourceUri);
            }
        } else if (requestCode == com.yalantis.ucrop.UCrop.REQUEST_CROP) {
            if (resultCode == RESULT_OK && data != null) {
                currentBgUri = com.yalantis.ucrop.UCrop.getOutput(data);
                if (currentBgUri != null) {
                    if (!isFinishing() && !isDestroyed()) {
                        com.bumptech.glide.Glide.with(ChatSettingsActivity.this.getApplicationContext())
                            .load(currentBgUri)
                            .centerCrop()
                            .into(ivChatBgPreview);
                    }
                    SpUtils.putString("CHAT_BG_" + sessionId, currentBgUri.toString());
                }
            } else if (resultCode == com.yalantis.ucrop.UCrop.RESULT_ERROR) {
                Throwable cropError = com.yalantis.ucrop.UCrop.getError(data);
                if (cropError != null) {
                    android.widget.Toast.makeText(this, "裁剪失败: " + cropError.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void startCropBg(android.net.Uri sourceUri) {
        String destinationFileName = "cropped_chat_bg_" + sessionId + "_" + System.currentTimeMillis() + ".jpg";
        android.net.Uri destinationUri = android.net.Uri.fromFile(new java.io.File(getCacheDir(), destinationFileName));
        
        android.util.DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int width = displayMetrics.widthPixels;
        int height = displayMetrics.heightPixels;

        com.yalantis.ucrop.UCrop uCrop = com.yalantis.ucrop.UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(width, height);
        uCrop.start(this);
    }

    private void loadEmojiGroups() {
        Executors.newSingleThreadExecutor().execute(() -> {
            allEmojiGroups = db.emojiDao().getAllGroupsSync();
            if (allEmojiGroups == null) {
                allEmojiGroups = new ArrayList<>();
            }
            
            String selectedGroupsStr = SpUtils.getString("CHAT_EMOJI_GROUP_" + sessionId, "全部表情");
            List<String> selectedList = Arrays.asList(selectedGroupsStr.split(","));
            
            // If the old config is "全部表情", or it's empty, we should default to selecting all real groups.
            boolean shouldSelectAllDefault = selectedList.contains("全部表情") || selectedGroupsStr.isEmpty();

            runOnUiThread(() -> {
                selectedEmojiGroups.clear();
                for (int i = 0; i < allEmojiGroups.size(); i++) {
                    String group = allEmojiGroups.get(i);
                    if (shouldSelectAllDefault || selectedList.contains(group)) {
                        selectedEmojiGroups.add(group);
                    }
                }
            });
        });
    }

    private void showEmojiGroupDialog() {
        if (allEmojiGroups == null || allEmojiGroups.isEmpty()) {
            android.widget.Toast.makeText(this, "暂无表情包分组", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        String[] items = allEmojiGroups.toArray(new String[0]);
        boolean[] checkedItems = new boolean[items.length];
        for (int i = 0; i < items.length; i++) {
            checkedItems[i] = selectedEmojiGroups.contains(items[i]);
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("表情包管理")
                .setMultiChoiceItems(items, checkedItems, (dialog, which, isChecked) -> {
                    String group = items[which];
                    if (isChecked) {
                        if (!selectedEmojiGroups.contains(group)) {
                            selectedEmojiGroups.add(group);
                        }
                    } else {
                        selectedEmojiGroups.remove(group);
                    }
                })
                .setPositiveButton("确定", null)
                .show();
    }

    private void loadWorldbooks() {
        Executors.newSingleThreadExecutor().execute(() -> {
            enabledEntries = db.worldbookDao().getAllEnabledEntriesSync();
            
            String unselectedStr = SpUtils.getString("CHAT_WORLDBOOK_UNSELECTED_" + sessionId, "");
            List<String> unselectedList = Arrays.asList(unselectedStr.split(","));

            runOnUiThread(() -> {
                llChatWorldbooks.removeAllViews();
                
                int padding = (int) (12 * getResources().getDisplayMetrics().density);
                for (int i = 0; i < enabledEntries.size(); i++) {
                    WorldbookEntry entry = enabledEntries.get(i);
                    String typeStr = entry.type == 0 ? "[前]" : (entry.type == 1 ? "[中]" : "[后]");
                    String title = typeStr + " " + (entry.title != null && !entry.title.isEmpty() ? entry.title : "未命名");
                    
                    android.widget.CheckBox checkBox = new android.widget.CheckBox(this);
                    checkBox.setText(title);
                    checkBox.setTextSize(16);
                    checkBox.setPadding(padding, padding, padding, padding);
                    
                    if (!unselectedList.contains(String.valueOf(entry.id))) {
                        checkBox.setChecked(true);
                    } else {
                        checkBox.setChecked(false);
                    }
                    
                    llChatWorldbooks.addView(checkBox);
                }
            });
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        
        // Save Worldbook selections
        if (!enabledEntries.isEmpty()) {
            List<String> unselectedIds = new ArrayList<>();
            for (int i = 0; i < enabledEntries.size(); i++) {
                android.view.View view = llChatWorldbooks.getChildAt(i);
                if (view instanceof android.widget.CheckBox) {
                    if (!((android.widget.CheckBox) view).isChecked()) {
                        unselectedIds.add(String.valueOf(enabledEntries.get(i).id));
                    }
                }
            }
            String unselectedStr = android.text.TextUtils.join(",", unselectedIds);
            SpUtils.putString("CHAT_WORLDBOOK_UNSELECTED_" + sessionId, unselectedStr);
        }

        // Save Emoji Group selections
        String selectedStr = android.text.TextUtils.join(",", selectedEmojiGroups);
        SpUtils.putString("CHAT_EMOJI_GROUP_" + sessionId, selectedStr);
        
        // Save Auto Message Config
        if (switchAutoMessage != null) {
            SpUtils.putBoolean("AUTO_MESSAGE_ENABLED_" + sessionId, switchAutoMessage.isChecked());
            try {
                float interval = Float.parseFloat(etAutoMessageInterval.getText().toString());
                SpUtils.putFloat("AUTO_MESSAGE_INTERVAL_" + sessionId, interval);
            } catch (NumberFormatException ignored) {}
            SpUtils.putString("AUTO_MESSAGE_START_" + sessionId, etAutoMessageStartTime.getText().toString());
            SpUtils.putString("AUTO_MESSAGE_END_" + sessionId, etAutoMessageEndTime.getText().toString());
        }

        // Save Max AI Messages
        if (sbMaxAiMessages != null) {
            int maxMessages = sbMaxAiMessages.getProgress() + 1;
            SpUtils.putInt("CHAT_MAX_AI_MESSAGES_" + sessionId, maxMessages);
        }
    }
}
