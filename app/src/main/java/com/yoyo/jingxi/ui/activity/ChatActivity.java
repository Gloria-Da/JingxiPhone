package com.yoyo.jingxi.ui.activity;

import android.os.Bundle;
import com.yoyo.jingxi.R;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.Character;
import com.yoyo.jingxi.data.entity.Message;
import com.yoyo.jingxi.network.OpenAIManager;
import com.yoyo.jingxi.network.OpenAiRequest;
import com.yoyo.jingxi.network.OpenAiResponse;
import com.yoyo.jingxi.network.SttResponse;
import com.yoyo.jingxi.ui.adapter.ChatAdapter;
import com.yoyo.jingxi.utils.SpUtils;

import java.io.File;
import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

import java.util.List;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import com.yoyo.jingxi.data.entity.ChatSession;
import com.yoyo.jingxi.data.entity.MyPersona;

import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.view.MenuItem;
import android.widget.PopupMenu;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.GridLayoutManager;

import com.google.android.material.tabs.TabLayout;
import com.yoyo.jingxi.data.entity.EmojiEntry;
import com.yoyo.jingxi.ui.adapter.EmojiAdapter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;

public class ChatActivity extends AppCompatActivity {

    private int sessionId;
    private String friendName;
    private ImageView ivChatBg;
    private static final int REQUEST_PICK_BG = 2001;
    
    private ChatSession currentSession;
    private Character currentCharacter;
    private MyPersona currentMyPersona;

    private RecyclerView rvChat;
    private ChatAdapter chatAdapter;
    private EditText etInput;
    private Button btnSend;
    private ImageView ivVoiceToggle;
    private ImageView ivEmoji;
    private ImageView ivAdd;
    private Button btnVoiceRecord;
    private android.widget.FrameLayout flRecordingOverlay;
    private android.widget.TextView tvRecordingHint;
    private android.widget.FrameLayout flVoiceConfirmOverlay;
    private EditText etVoiceConfirmText;
    private Button btnVoiceCancel;
    private Button btnVoiceSend;
    
    private android.media.MediaRecorder mediaRecorder;
    private String currentVoiceFilePath;
    private boolean isRecording = false;
    private boolean isVoiceCancelled = false;
    private float startY;
    private LinearLayout layoutFunctionPanel;
    private LinearLayout btnFuncImage;
    private LinearLayout btnFuncVoice;
    private LinearLayout btnFuncCall;
    private LinearLayout btnFuncRegenerate;
    private LinearLayout btnFuncSchedule;
    private LinearLayout btnFuncMemo;
    
    private LinearLayout layoutEmojiPanel;
    private TabLayout tabLayoutEmojiGroups;
    private RecyclerView rvEmojiPanel;
    private EmojiAdapter emojiAdapter;
    private LinearLayout layoutQuotePreview;
    private android.widget.TextView tvQuotePreview;
    private ImageView ivCloseQuote;

    private Message pendingQuoteMsg;

    private AppDatabase db;
    private BroadcastReceiver messageUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.yoyo.jingxi.ACTION_MESSAGE_UPDATED".equals(intent.getAction())) {
                runOnUiThread(() -> {
                    // Update specific message item or full list.
                    // For simplicity, notifying dataset changed.
                    if (chatAdapter != null) {
                        chatAdapter.notifyDataSetChanged();
                    }
                });
            }
        }
    };
    private BroadcastReceiver aiReplyStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.yoyo.jingxi.ACTION_AI_REPLY_STATUS".equals(intent.getAction())) {
                boolean isReplying = intent.getBooleanExtra("is_replying", false);
                runOnUiThread(() -> {
                    if (!isReplying) {
                        if (getSupportActionBar() != null) {
                            getSupportActionBar().setTitle(friendName != null ? friendName : "聊天");
                        }
                        btnSend.setEnabled(true);
                        checkAndGenerateSummaryMemory();
                    } else {
                        if (getSupportActionBar() != null) {
                            getSupportActionBar().setTitle("对方正在输入中...");
                        }
                        btnSend.setEnabled(false);
                    }
                });
            }
        }
    };
    private OpenAIManager aiManager;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        com.yoyo.jingxi.utils.ThemeManager.applyTheme(this);
        setContentView(R.layout.activity_chat);

        sessionId = getIntent().getIntExtra("session_id", -1);
        friendName = getIntent().getStringExtra("friend_name");

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(friendName != null ? friendName : "聊天");
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
        
        // Remove the original ImageView click listener logic if we are using options menu
        ImageView ivChatSettings = findViewById(R.id.ivChatSettings);
        if (ivChatSettings != null) {
            ivChatSettings.setVisibility(View.GONE);
        }

        ImageView ivChatBgSettings = findViewById(R.id.ivChatBgSettings);
        if (ivChatBgSettings != null) {
            ivChatBgSettings.setVisibility(View.GONE);
        }

        rvChat = findViewById(R.id.rvChat);
        etInput = findViewById(R.id.etInput);
        btnSend = findViewById(R.id.btnSend);
        ivVoiceToggle = findViewById(R.id.ivVoiceToggle);
        ivEmoji = findViewById(R.id.ivEmoji);
        ivAdd = findViewById(R.id.ivAdd);
        
        btnVoiceRecord = findViewById(R.id.btnVoiceRecord);
        flRecordingOverlay = findViewById(R.id.flRecordingOverlay);
        tvRecordingHint = findViewById(R.id.tvRecordingHint);
        flVoiceConfirmOverlay = findViewById(R.id.flVoiceConfirmOverlay);
        etVoiceConfirmText = findViewById(R.id.etVoiceConfirmText);
        btnVoiceCancel = findViewById(R.id.btnVoiceCancel);
        btnVoiceSend = findViewById(R.id.btnVoiceSend);

        layoutFunctionPanel = findViewById(R.id.layoutFunctionPanel);
        btnFuncImage = findViewById(R.id.btnFuncImage);
        btnFuncVoice = findViewById(R.id.btnFuncVoice);
        btnFuncCall = findViewById(R.id.btnFuncCall);
        btnFuncRegenerate = findViewById(R.id.btnFuncRegenerate);
        btnFuncSchedule = findViewById(R.id.btnFuncSchedule);
        btnFuncMemo = findViewById(R.id.btnFuncMemo);
        layoutQuotePreview = findViewById(R.id.layoutQuotePreview);
        tvQuotePreview = findViewById(R.id.tvQuotePreview);
        ivCloseQuote = findViewById(R.id.ivCloseQuote);
        
        layoutEmojiPanel = findViewById(R.id.layoutEmojiPanel);
        tabLayoutEmojiGroups = findViewById(R.id.tabLayoutEmojiGroups);
        rvEmojiPanel = findViewById(R.id.rvEmojiPanel);
        
        rvEmojiPanel.setLayoutManager(new GridLayoutManager(this, 5));
        emojiAdapter = new EmojiAdapter();
        rvEmojiPanel.setAdapter(emojiAdapter);

        tabLayoutEmojiGroups.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
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

        ivCloseQuote.setOnClickListener(v -> clearQuote());

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvChat.setLayoutManager(layoutManager);
        
        chatAdapter = new ChatAdapter(friendName);
        rvChat.setAdapter(chatAdapter);
        
        chatAdapter.setOnMessageLongClickListener((msg, view) -> {
            showMessageLongClickMenu(msg, view);
        });

        db = AppDatabase.getDatabase(this);
        aiManager = new OpenAIManager();

        // 加载会话和角色信息
        Executors.newSingleThreadExecutor().execute(() -> {
            currentSession = db.chatSessionDao().getSessionById(sessionId);
            if (currentSession != null) {
                currentCharacter = db.characterDao().getCharacterById(currentSession.characterId);
                
                if (currentCharacter != null) {
                    mainHandler.post(() -> chatAdapter.setCharacterAvatarPath(currentCharacter.avatarPath));
                }

                if (!TextUtils.isEmpty(currentSession.myPersonaName)) {
                    currentMyPersona = db.myPersonaDao().getMyPersonaByName(currentSession.myPersonaName);
                    if (currentMyPersona != null) {
                        mainHandler.post(() -> chatAdapter.setMyAvatarPath(currentMyPersona.avatarPath));
                    } else {
                        currentMyPersona = new MyPersona();
                        currentMyPersona.name = currentSession.myPersonaName;
                    }
                }
            }
        });

        // 监听消息列表变化
        db.messageDao().getMessagesBySessionId(sessionId).observe(this, messages -> {
            if (messages != null) {
                // 过滤掉 type=100 的隐形电话上下文消息，不在聊天主界面显示
                List<Message> visibleMessages = new java.util.ArrayList<>();
                for (Message m : messages) {
                    if (m.type != 100) {
                        visibleMessages.add(m);
                    }
                }
                chatAdapter.setMessages(visibleMessages);
                if (visibleMessages.size() > 0) {
                    rvChat.scrollToPosition(visibleMessages.size() - 1);
                }
            }
        });

        btnSend.setOnClickListener(v -> sendMessage());
        
        btnVoiceRecord.setOnTouchListener((v, event) -> {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO}, 100);
                return false;
            }

            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    startY = event.getY();
                    isVoiceCancelled = false;
                    btnVoiceRecord.setText("松开 结束");
                    flRecordingOverlay.setVisibility(View.VISIBLE);
                    tvRecordingHint.setText("手指上滑，取消发送");
                    tvRecordingHint.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                    startRecording();
                    return true;
                case android.view.MotionEvent.ACTION_MOVE:
                    float currentY = event.getY();
                    if (startY - currentY > 100) {
                        isVoiceCancelled = true;
                        tvRecordingHint.setText("松开手指，取消发送");
                        tvRecordingHint.setBackgroundColor(android.graphics.Color.parseColor("#99FF0000"));
                    } else {
                        isVoiceCancelled = false;
                        tvRecordingHint.setText("手指上滑，取消发送");
                        tvRecordingHint.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                    }
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    btnVoiceRecord.setText("按住 说话");
                    flRecordingOverlay.setVisibility(View.GONE);
                    stopRecording();
                    if (!isVoiceCancelled && currentVoiceFilePath != null) {
                        processVoiceToText(currentVoiceFilePath);
                    } else {
                        if (currentVoiceFilePath != null) {
                            new File(currentVoiceFilePath).delete();
                            currentVoiceFilePath = null;
                        }
                    }
                    return true;
            }
            return false;
        });

        btnVoiceCancel.setOnClickListener(v -> {
            flVoiceConfirmOverlay.setVisibility(View.GONE);
            if (currentVoiceFilePath != null) {
                new File(currentVoiceFilePath).delete();
                currentVoiceFilePath = null;
            }
        });

        btnVoiceSend.setOnClickListener(v -> {
            String text = etVoiceConfirmText.getText().toString().trim();
            if (!text.isEmpty()) {
                sendVoiceMessage(text, currentVoiceFilePath);
            }
            flVoiceConfirmOverlay.setVisibility(View.GONE);
            currentVoiceFilePath = null;
        });

        ivVoiceToggle.setOnClickListener(v -> {
            if (btnVoiceRecord.getVisibility() != View.VISIBLE) {
                // 切换到语音模式
                btnVoiceRecord.setVisibility(View.VISIBLE);
                etInput.setVisibility(View.GONE);
                ivVoiceToggle.setImageResource(android.R.drawable.ic_menu_edit); // 使用现有图标临时表示键盘，如果有更好的图标可以替换
            } else {
                // 切换回文本模式
                btnVoiceRecord.setVisibility(View.GONE);
                etInput.setVisibility(View.VISIBLE);
                ivVoiceToggle.setImageResource(android.R.drawable.ic_btn_speak_now);
            }
        });

        ivEmoji.setOnClickListener(v -> {
            if (layoutEmojiPanel.getVisibility() == View.VISIBLE) {
                layoutEmojiPanel.setVisibility(View.GONE);
            } else {
                layoutFunctionPanel.setVisibility(View.GONE);
                layoutEmojiPanel.setVisibility(View.VISIBLE);
                
                loadEmojiGroups();
            }
        });
        
        emojiAdapter.setOnEmojiClickListener(new EmojiAdapter.OnEmojiClickListener() {
            @Override
            public void onEmojiClick(EmojiEntry emoji) {
                sendEmojiMessage(emoji);
                layoutEmojiPanel.setVisibility(View.GONE);
            }

            @Override
            public void onEmojiLongClick(EmojiEntry emoji, View view) {
                // do nothing here
            }
        });

        ivAdd.setOnClickListener(v -> {
            if (layoutFunctionPanel.getVisibility() == View.VISIBLE) {
                layoutFunctionPanel.setVisibility(View.GONE);
            } else {
                layoutEmojiPanel.setVisibility(View.GONE);
                layoutFunctionPanel.setVisibility(View.VISIBLE);
            }
        });

        btnFuncImage.setOnClickListener(v -> handleImageFunc());

        btnFuncVoice.setOnClickListener(v -> handleVoiceFunc());

        btnFuncCall.setOnClickListener(v -> {
            if (CallActivity.instance != null && !CallActivity.instance.isCallEnded()) {
                Toast.makeText(ChatActivity.this, "当前已有正在进行的通话", Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentSession != null && currentCharacter != null) {
                Intent intent = new Intent(ChatActivity.this, CallActivity.class);
                intent.putExtra("session_id", sessionId);
                intent.putExtra("is_incoming", false); // 主动拨打
                startActivityForResult(intent, 200); // 监听返回结果
            }
        });

        // 注册广播监听 AiReplyService 的状态更新
        IntentFilter replyFilter = new IntentFilter("com.yoyo.jingxi.ACTION_AI_REPLY_STATUS");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(aiReplyStatusReceiver, replyFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(aiReplyStatusReceiver, replyFilter);
        }

        btnFuncRegenerate.setOnClickListener(v -> handleRegenerate());

        btnFuncSchedule.setOnClickListener(v -> {
            if (currentSession != null) {
                Intent intent = new Intent(ChatActivity.this, ScheduleActivity.class);
                intent.putExtra("character_id", currentSession.characterId);
                startActivity(intent);
            }
        });

        btnFuncMemo.setOnClickListener(v -> {
            if (currentSession != null) {
                Intent intent = new Intent(ChatActivity.this, MemoActivity.class);
                intent.putExtra("character_id", currentSession.characterId);
                startActivity(intent);
            }
        });
        
        IntentFilter filter = new IntentFilter("com.yoyo.jingxi.ACTION_MESSAGE_UPDATED");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(messageUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(messageUpdateReceiver, filter);
        }

        loadChatBackground();
    }

    private void loadChatBackground() {
        if (ivChatBg == null) {
            ivChatBg = findViewById(R.id.ivChatBg);
        }
        if (ivChatBg != null) {
            String sessionBgStr = com.yoyo.jingxi.utils.SpUtils.getString("CHAT_BG_" + sessionId, null);
            if (sessionBgStr != null) {
                ivChatBg.setVisibility(View.VISIBLE);
                if (!isFinishing() && !isDestroyed()) {
                    com.bumptech.glide.Glide.with(this.getApplicationContext())
                        .load(android.net.Uri.parse(sessionBgStr))
                        .centerCrop()
                        .into(ivChatBg);
                }
                if (com.yoyo.jingxi.utils.ThemeManager.isDarkMode(this)) {
                    ivChatBg.setColorFilter(android.graphics.Color.parseColor("#40000000"), android.graphics.PorterDuff.Mode.SRC_ATOP);
                } else {
                    ivChatBg.clearColorFilter();
                }
            } else {
                String bgPath = com.yoyo.jingxi.utils.ThemeManager.getBgImagePath(this);
                if (bgPath != null && !bgPath.isEmpty()) {
                    ivChatBg.setVisibility(View.VISIBLE);
                    if (!isFinishing() && !isDestroyed()) {
                        com.bumptech.glide.Glide.with(this.getApplicationContext())
                            .load(android.net.Uri.parse(bgPath))
                            .centerCrop()
                            .into(ivChatBg);
                    }
                    if (com.yoyo.jingxi.utils.ThemeManager.isDarkMode(this)) {
                        ivChatBg.setColorFilter(android.graphics.Color.parseColor("#40000000"), android.graphics.PorterDuff.Mode.SRC_ATOP);
                    } else {
                        ivChatBg.clearColorFilter();
                    }
                } else {
                    ivChatBg.setVisibility(View.GONE);
                }
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(messageUpdateReceiver);
        } catch (IllegalArgumentException e) {
            // ignore
        }
        try {
            unregisterReceiver(aiReplyStatusReceiver);
        } catch (IllegalArgumentException e) {
            // ignore
        }
    }
    
    private void openChatSettings() {
        Intent intent = new Intent(this, ChatSettingsActivity.class);
        intent.putExtra("session_id", sessionId);
        startActivity(intent);
    }
    
    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.chat_activity_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            openChatSettings();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 200) {
            if (resultCode == RESULT_CANCELED) {
                // AI 拨打的电话，用户未接听或挂断（如果是未接听，则生成未接通的气泡）
                // 或者是用户拨打的电话，AI未接听
                boolean isUnanswered = data != null && data.getBooleanExtra("is_unanswered", false);
                boolean isIncomingCall = data != null && data.getBooleanExtra("is_incoming", false);
                if (isUnanswered && currentSession != null) {
                    Message msg = new Message();
                    msg.sessionId = sessionId;
                    msg.characterId = currentSession.characterId;
                    msg.isFromUser = !isIncomingCall;
                    msg.type = 0;
                    msg.content = "[未接通的电话]";
                    msg.timestamp = System.currentTimeMillis();
                    Executors.newSingleThreadExecutor().execute(() -> {
                        db.messageDao().insert(msg);
                    });
                }
            }
        } else if (requestCode == REQUEST_PICK_BG && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                startCropBg(imageUri);
            }
        } else if (requestCode == com.yalantis.ucrop.UCrop.REQUEST_CROP) {
            if (resultCode == RESULT_OK && data != null) {
                Uri resultUri = com.yalantis.ucrop.UCrop.getOutput(data);
                if (resultUri != null) {
                    com.yoyo.jingxi.utils.ThemeManager.setChatBgPath(this, resultUri.toString());
                    loadChatBackground();
                }
            } else if (resultCode == com.yalantis.ucrop.UCrop.RESULT_ERROR) {
                Throwable cropError = com.yalantis.ucrop.UCrop.getError(data);
                if (cropError != null) {
                    Toast.makeText(this, "裁剪失败: " + cropError.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void startCropBg(Uri sourceUri) {
        String destinationFileName = "cropped_chat_bg_" + System.currentTimeMillis() + ".jpg";
        Uri destinationUri = Uri.fromFile(new java.io.File(getCacheDir(), destinationFileName));
        
        android.util.DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int width = displayMetrics.widthPixels;
        int height = displayMetrics.heightPixels;

        com.yalantis.ucrop.UCrop uCrop = com.yalantis.ucrop.UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(width, height);
        uCrop.start(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // 标记当前Activity在前台
        SpUtils.putInt("CURRENT_CHAT_SESSION_ID", sessionId);
        
        // 重新进入时刷新表情分组，以防在设置里修改了
        if (layoutEmojiPanel != null && layoutEmojiPanel.getVisibility() == View.VISIBLE) {
            loadEmojiGroups();
        }
        
        // 每次前台可见时清零未读数
        Executors.newSingleThreadExecutor().execute(() -> {
            if (db != null) {
                db.chatSessionDao().updateUnreadCount(sessionId, 0);
            }
        });
        
        loadChatBackground();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 标记当前Activity不在前台
        SpUtils.putInt("CURRENT_CHAT_SESSION_ID", -1);
    }

    private final ActivityResultLauncher<Intent> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri selectedImage = result.getData().getData();
                    if (selectedImage != null) {
                        // 先将图片转为Base64，因为OpenAI Vision API 需要网络URL或Base64数据
                        Executors.newSingleThreadExecutor().execute(() -> {
                            try {
                                java.io.InputStream inputStream = getContentResolver().openInputStream(selectedImage);
                                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(inputStream);
                                
                                // 压缩图片，防止过大导致请求失败
                                int maxWidth = 1024;
                                int maxHeight = 1024;
                                float scale = Math.min(((float)maxWidth / bitmap.getWidth()), ((float)maxHeight / bitmap.getHeight()));
                                if (scale < 1) {
                                    android.graphics.Matrix matrix = new android.graphics.Matrix();
                                    matrix.postScale(scale, scale);
                                    bitmap = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                                }
                                
                                java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, outputStream);
                                byte[] byteArray = outputStream.toByteArray();
                                String base64Image = android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP);
                                
                                // 保存 Base64 以供 OpenAI 请求
                                String dataUri = "data:image/jpeg;base64," + base64Image;
                                
                                // 但是对于本地显示（尤其是 Android ImageView），直接用 data URI 前缀可能会无法解析。
                                // 为了让 ImageView 正常显示，我们仍然可以把它当做普通字符串传过去。
                                // 如果要让 ChatAdapter 能解析它，我们可以修改 ChatAdapter 的解析逻辑，
                                // 把 data:image 去掉，解码为 Bitmap 再显示。
                                // 也可以直接传完整的 dataUri 并在 ChatAdapter 里处理。
                                mainHandler.post(() -> sendImageMessage(dataUri, false, null));
                            } catch (Exception e) {
                                e.printStackTrace();
                                mainHandler.post(() -> Toast.makeText(ChatActivity.this, "处理图片失败", Toast.LENGTH_SHORT).show());
                            }
                        });
                    }
                }
            }
    );

    private void handleVoiceFunc() {
        // 由于已经在输入栏添加了语音按钮，功能面板中的语音改为专门用于发送虚拟语音
        AlertDialog.Builder descBuilder = new AlertDialog.Builder(this);
        descBuilder.setTitle("输入虚拟语音内容");
        final EditText input = new EditText(this);
        input.setHint("例如：你好呀，今天过得怎么样？");
        descBuilder.setView(input);
        descBuilder.setPositiveButton("发送", (dialog1, which1) -> {
            String desc = input.getText().toString().trim();
            if (!TextUtils.isEmpty(desc)) {
                sendVoiceMessage(desc, null);
            }
        });
        descBuilder.setNegativeButton("取消", (dialog1, which1) -> dialog1.cancel());
        descBuilder.show();
        layoutFunctionPanel.setVisibility(View.GONE);
    }

    private void loadEmojiGroups() {
        Executors.newSingleThreadExecutor().execute(() -> {
            // 获取当前会话选中的所有表情分组
            String selectedGroupsStr = SpUtils.getString("CHAT_EMOJI_GROUP_" + sessionId, "全部表情");
            // If the string is exactly "全部表情", or empty, we consider it as having all real groups
            boolean selectAllDefault = selectedGroupsStr.isEmpty() || selectedGroupsStr.equals("全部表情") || selectedGroupsStr.contains("全部表情");
            
            List<String> finalGroupsToDisplay = new java.util.ArrayList<>();
            if (selectAllDefault) {
                List<String> allDbGroups = db.emojiDao().getAllGroupsSync();
                if (allDbGroups != null) {
                    finalGroupsToDisplay.addAll(allDbGroups);
                }
            } else {
                String[] groupsArray = selectedGroupsStr.split(",");
                for (String group : groupsArray) {
                    if (!TextUtils.isEmpty(group.trim())) {
                        finalGroupsToDisplay.add(group.trim());
                    }
                }
            }

            mainHandler.post(() -> {
                tabLayoutEmojiGroups.removeAllTabs();
                
                for (String group : finalGroupsToDisplay) {
                    tabLayoutEmojiGroups.addTab(tabLayoutEmojiGroups.newTab().setText(group));
                }
                
                if (tabLayoutEmojiGroups.getTabCount() > 0) {
                    TabLayout.Tab firstTab = tabLayoutEmojiGroups.getTabAt(0);
                    tabLayoutEmojiGroups.selectTab(firstTab);
                    if (firstTab != null && firstTab.getText() != null) {
                        loadEmojisByGroup(firstTab.getText().toString());
                    }
                }
            });
        });
    }

    private void loadEmojisByGroup(String groupName) {
        db.emojiDao().getAllEmojis().removeObservers(this);
        db.emojiDao().getEmojisByGroup(groupName).removeObservers(this);
        
        if ("全部表情".equals(groupName)) {
            db.emojiDao().getAllEmojis().observe(this, emojis -> emojiAdapter.setEmojis(emojis));
        } else {
            db.emojiDao().getEmojisByGroup(groupName).observe(this, emojis -> emojiAdapter.setEmojis(emojis));
        }
    }

    private void handleImageFunc() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("发送图片");
        String[] options = {"虚拟图片 (输入描述)", "真实图片 (从相册选择)"};
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                // 虚拟图片
                AlertDialog.Builder descBuilder = new AlertDialog.Builder(this);
                descBuilder.setTitle("虚拟图片描述");
                final EditText input = new EditText(this);
                input.setHint("例如：一张小猫在草地上睡觉的照片");
                descBuilder.setView(input);
                descBuilder.setPositiveButton("发送", (dialog1, which1) -> {
                    String desc = input.getText().toString().trim();
                    if (!TextUtils.isEmpty(desc)) {
                        sendImageMessage(null, true, desc);
                    }
                });
                descBuilder.setNegativeButton("取消", (dialog1, which1) -> dialog1.cancel());
                descBuilder.show();
            } else if (which == 1) {
                // 真实图片
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                pickImageLauncher.launch(intent);
            }
        });
        builder.show();
        layoutFunctionPanel.setVisibility(View.GONE);
    }

    private void sendEmojiMessage(EmojiEntry emoji) {
        if (currentSession == null) return;
        Message msg = new Message();
        msg.sessionId = sessionId;
        msg.characterId = currentSession.characterId;
        msg.isFromUser = true;
        msg.timestamp = System.currentTimeMillis();
        msg.quoteMessageId = pendingQuoteMsg != null ? pendingQuoteMsg.id : -1;
        pendingQuoteMsg = null;
        
        msg.type = 2; // 自定义表情与普通表情合并使用type 2，或者可以保留作为真实图片发送
        // 既然我们有表情的概念，我们使用 type=2，并通过 imageUrl 保存，同时 content 可以用来存储 name
        msg.content = "[" + emoji.name + "]";
        msg.imageUrl = emoji.imageUrl;
        
        Executors.newSingleThreadExecutor().execute(() -> {
            db.messageDao().insert(msg);
        });
    }

    private void sendImageMessage(String imageUri, boolean isVirtual, String virtualDesc) {
        if (currentSession == null) return;
        Message msg = new Message();
        msg.sessionId = sessionId;
        msg.characterId = currentSession.characterId;
        msg.isFromUser = true;
        msg.timestamp = System.currentTimeMillis();
        msg.quoteMessageId = pendingQuoteMsg != null ? pendingQuoteMsg.id : -1;
        pendingQuoteMsg = null;

        if (isVirtual) {
            msg.type = 4; // 4 为虚拟图片
            msg.imageDesc = virtualDesc;
            msg.content = "[虚拟图片]";
        } else {
            msg.type = 3; // 3 为真实图片
            msg.imageUrl = imageUri;
            msg.content = "[图片]";
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            long id = db.messageDao().insert(msg);
            msg.id = (int) id;
                if (isVirtual) {
                    com.yoyo.jingxi.utils.ImageGenerationManager.getInstance().checkAndGenerateImagesForMessage(msg);
                }
        });
    }

    private void startRecording() {
        String fileName = "VOICE_" + System.currentTimeMillis() + ".mp3";
        File dir = new File(getExternalFilesDir(null), "voice");
        if (!dir.exists()) dir.mkdirs();
        currentVoiceFilePath = new File(dir, fileName).getAbsolutePath();

        mediaRecorder = new android.media.MediaRecorder();
        mediaRecorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setOutputFile(currentVoiceFilePath);

        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
        } catch (IOException e) {
            e.printStackTrace();
            currentVoiceFilePath = null;
        }
    }

    private void stopRecording() {
        if (mediaRecorder != null && isRecording) {
            try {
                mediaRecorder.stop();
            } catch (RuntimeException e) {
                if (currentVoiceFilePath != null) {
                    new File(currentVoiceFilePath).delete();
                    currentVoiceFilePath = null;
                }
            }
            mediaRecorder.release();
            mediaRecorder = null;
            isRecording = false;
        }
    }

    private void processVoiceToText(String filePath) {
        String sttBaseUrl = SpUtils.getString("STT_BASE_URL", "https://api.siliconflow.cn/");
        String sttApiKey = SpUtils.getString("STT_API_KEY", "");
        String sttModel = SpUtils.getString("STT_MODEL", "FunAudioLLM/SenseVoiceSmall");

        if (TextUtils.isEmpty(sttApiKey)) {
            Toast.makeText(this, "请先在API设置中配置STT API Key", Toast.LENGTH_SHORT).show();
            return;
        }

        File file = new File(filePath);
        RequestBody requestFile = RequestBody.create(MediaType.parse("audio/mpeg"), file);
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", file.getName(), requestFile);
        RequestBody modelBody = RequestBody.create(MediaType.parse("text/plain"), sttModel);

        Toast.makeText(this, "正在识别语音...", Toast.LENGTH_SHORT).show();

        aiManager.getApi().transcribeAudio(sttBaseUrl + "v1/audio/transcriptions", "Bearer " + sttApiKey, body, modelBody).enqueue(new retrofit2.Callback<SttResponse>() {
            @Override
            public void onResponse(retrofit2.Call<SttResponse> call, retrofit2.Response<SttResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().text != null) {
                    String recognizedText = response.body().text;
                    runOnUiThread(() -> {
                        etVoiceConfirmText.setText(recognizedText);
                        flVoiceConfirmOverlay.setVisibility(View.VISIBLE);
                    });
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(ChatActivity.this, "识别失败: " + response.code(), Toast.LENGTH_SHORT).show();
                        if (currentVoiceFilePath != null) {
                            new File(currentVoiceFilePath).delete();
                            currentVoiceFilePath = null;
                        }
                    });
                }
            }

            @Override
            public void onFailure(retrofit2.Call<SttResponse> call, Throwable t) {
                runOnUiThread(() -> {
                    Toast.makeText(ChatActivity.this, "语音识别网络错误", Toast.LENGTH_SHORT).show();
                    if (currentVoiceFilePath != null) {
                        new File(currentVoiceFilePath).delete();
                        currentVoiceFilePath = null;
                    }
                });
            }
        });
    }

    private void sendVoiceMessage(String text, String voicePath) {
        if (currentSession == null) return;

        Message msg = new Message();
        msg.sessionId = sessionId;
        msg.characterId = currentSession.characterId;
        msg.isFromUser = true;
        msg.content = text;
        msg.type = 1; // 1 for voice
        msg.voiceUrl = voicePath;
        msg.timestamp = System.currentTimeMillis();

        if (pendingQuoteMsg != null) {
            msg.quoteMessageId = pendingQuoteMsg.id;
            pendingQuoteMsg = null;
        } else {
            msg.quoteMessageId = -1;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            db.messageDao().insert(msg);
        });

        clearQuote();
        // 移除自动触发 AI 回复逻辑
    }

    private void sendMessage() {
        String content = etInput.getText().toString().trim();
        if (TextUtils.isEmpty(content)) {
            // 输入为空时，触发 AI 回复
            requestAiReply();
            return;
        }

        if (CallActivity.instance != null && CallActivity.instance.getSessionId() == sessionId && !CallActivity.instance.isCallEnded()) {
            CallActivity.instance.sendTextFromChat(content, true);
            etInput.setText("");
            return;
        }

        if (currentSession == null) return;

        Message msg = new Message();
        msg.sessionId = sessionId;
        msg.characterId = currentSession.characterId; // 冗余保存
        msg.content = content;
        msg.isFromUser = true;
        msg.type = 0;
        msg.timestamp = System.currentTimeMillis();
        if (pendingQuoteMsg != null) {
            msg.quoteMessageId = pendingQuoteMsg.id;
            pendingQuoteMsg = null;
        } else {
            msg.quoteMessageId = -1;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            db.messageDao().insert(msg);
            db.chatSessionDao().updateUnreadCount(sessionId, 0); // 自己发消息也会重置未读
        });

        etInput.setText("");
        clearQuote();
    }

    private void checkAndGenerateSummaryMemory() {
        int summaryRounds = SpUtils.getInt("SETTING_SUMMARY_ROUNDS", 50);
        if (summaryRounds <= 0 || currentCharacter == null) return;

        Executors.newSingleThreadExecutor().execute(() -> {
            boolean isSummarizing = SpUtils.getBoolean("IS_SUMMARIZING_" + currentCharacter.id, false);
            if (isSummarizing) return;

            long lastSummaryTime = SpUtils.getLong("LAST_SUMMARY_TIME_" + currentCharacter.id, 0);
            int newMessagesCount = db.messageDao().getMessagesCountSinceSync(sessionId, lastSummaryTime);

            if (newMessagesCount >= summaryRounds * 2) {
                SpUtils.putBoolean("IS_SUMMARIZING_" + currentCharacter.id, true);
                String apiKey = SpUtils.getString("OPENAI_API_KEY", "");
                String endpoint = SpUtils.getString("API_ENDPOINT", "https://api.openai.com/");
                String model = SpUtils.getString("API_MODEL", "gpt-4o-mini");
                if (TextUtils.isEmpty(apiKey)) return;
                if (!endpoint.endsWith("/")) endpoint += "/";

                List<Message> history = db.messageDao().getRecentMessagesBySessionIdSync(sessionId, summaryRounds * 2);
                java.util.Collections.reverse(history);

                StringBuilder dialogue = new StringBuilder();
                for (Message msg : history) {
                    dialogue.append(msg.isFromUser ? "用户: " : "角色: ").append(msg.content).append("\n");
                }

                String myName = currentMyPersona != null ? currentMyPersona.name : "用户";
                String prompt = "请根据以下对话，以第三人称视角客观地总结一段发生的事情或重要信息的\"普通记忆\"。\n" +
                        "【重要要求】：必须使用双方的真实姓名（\"" + currentCharacter.name + "\" 和 \"" + myName + "\"）来进行描述，绝对不要笼统地用\"他们\"、\"他\"或\"她\"代指。（200字以内，直接输出总结内容，不要任何废话）：\n\n" + dialogue.toString();

                OpenAiRequest request = new OpenAiRequest();
                request.model = model;
                request.messages = new java.util.ArrayList<>();
                request.messages.add(new OpenAiRequest.Message("user", prompt));

                try {
                    retrofit2.Response<OpenAiResponse> response = aiManager.getApi().createChatCompletion(endpoint + "v1/chat/completions", "Bearer " + apiKey, request).execute();
                    if (response.isSuccessful() && response.body() != null && response.body().choices != null
                            && !response.body().choices.isEmpty()
                            && response.body().choices.get(0) != null
                            && response.body().choices.get(0).message != null
                            && response.body().choices.get(0).message.content != null) {
                        String summary = response.body().choices.get(0).message.content.trim();
                        com.yoyo.jingxi.data.entity.Memory memory = new com.yoyo.jingxi.data.entity.Memory();
                        memory.characterId = currentCharacter.id;
                        memory.type = 0;
                        memory.content = summary;
                        memory.timestamp = System.currentTimeMillis();
                        db.memoryDao().insert(memory);
                        SpUtils.putLong("LAST_SUMMARY_TIME_" + currentCharacter.id, System.currentTimeMillis());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    SpUtils.putBoolean("IS_SUMMARIZING_" + currentCharacter.id, false);
                }
            }
        });
    }

    private void handleRegenerate() {
        layoutFunctionPanel.setVisibility(View.GONE);
        Executors.newSingleThreadExecutor().execute(() -> {
            List<Message> history = db.messageDao().getRecentMessagesBySessionIdSync(sessionId, 50);

            int countToDelete = 0;
            for (Message msg : history) {
                if (!msg.isFromUser) {
                    countToDelete++;
                } else {
                    break;
                }
            }

            if (countToDelete > 0) {
                for (int i = 0; i < countToDelete; i++) {
                    db.messageDao().delete(history.get(i));
                }
            }

            mainHandler.post(this::requestAiReply);
        });
    }

    private void showMessageLongClickMenu(Message msg, View anchorView) {
        PopupMenu popupMenu = new PopupMenu(this, anchorView);
        popupMenu.getMenu().add(0, 1, 0, "复制");
        popupMenu.getMenu().add(0, 2, 0, "转发");
        popupMenu.getMenu().add(0, 3, 0, "删除");
        if (msg.isFromUser) {
            popupMenu.getMenu().add(0, 4, 0, "撤回");
        }
        popupMenu.getMenu().add(0, 5, 0, "引用");
        if (msg.type == 0) {
            popupMenu.getMenu().add(0, 6, 0, "编辑");
        }

        popupMenu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    Toast.makeText(this, "复制成功 (占位)", Toast.LENGTH_SHORT).show();
                    break;
                case 2:
                    Toast.makeText(this, "转发功能开发中", Toast.LENGTH_SHORT).show();
                    break;
                case 3:
                    Executors.newSingleThreadExecutor().execute(() -> db.messageDao().delete(msg));
                    break;
                case 4:
                    Executors.newSingleThreadExecutor().execute(() -> {
                        msg.type = 99;
                        msg.content = "你撤回了一条消息";
                        db.messageDao().update(msg);
                    });
                    break;
                case 5:
                    quoteMessage(msg);
                    break;
                case 6:
                    showEditMessageDialog(msg);
                    break;
            }
            return true;
        });
        popupMenu.show();
    }

    private void showEditMessageDialog(Message msg) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("编辑消息");
        final EditText input = new EditText(this);
        input.setText(msg.content);
        input.setSelection(input.getText().length());
        builder.setView(input);
        builder.setPositiveButton("保存", (dialog, which) -> {
            String newContent = input.getText().toString().trim();
            if (!TextUtils.isEmpty(newContent) && !newContent.equals(msg.content)) {
                Executors.newSingleThreadExecutor().execute(() -> {
                    msg.content = newContent;
                    db.messageDao().update(msg);
                });
            }
        });
        builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void quoteMessage(Message msg) {
        pendingQuoteMsg = msg;
        String sender = msg.isFromUser ? "我" : (friendName != null ? friendName : "对方");
        layoutQuotePreview.setVisibility(View.VISIBLE);
        tvQuotePreview.setText("引用 " + sender + ": " + msg.content);
        etInput.requestFocus();
    }

    private void clearQuote() {
        pendingQuoteMsg = null;
        layoutQuotePreview.setVisibility(View.GONE);
        tvQuotePreview.setText("");
        etInput.setHint("输入消息...");
    }

    private void requestAiReply() {
        if (currentCharacter == null) {
            Toast.makeText(this, "正在加载角色信息，请稍后再试", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String apiKey = SpUtils.getString("OPENAI_API_KEY", "");
        if (TextUtils.isEmpty(apiKey)) {
            Toast.makeText(this, "请先在桌面的设置应用中配置 OpenAI API KEY", Toast.LENGTH_LONG).show();
            return;
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("对方正在输入中...");
        }
        
        Intent serviceIntent = new Intent(this, com.yoyo.jingxi.service.AiReplyService.class);
        serviceIntent.setAction(com.yoyo.jingxi.service.AiReplyService.ACTION_START_REPLY);
        serviceIntent.putExtra(com.yoyo.jingxi.service.AiReplyService.EXTRA_SESSION_ID, sessionId);
        serviceIntent.putExtra(com.yoyo.jingxi.service.AiReplyService.EXTRA_CHARACTER_ID, currentCharacter.id);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                startForegroundService(serviceIntent);
            } catch (Exception e) {
                android.util.Log.e("ChatActivity", "Failed to start foreground service", e);
                startService(serviceIntent);
            }
        } else {
            startService(serviceIntent);
        }
        // 发出请求后禁用发送按钮
        btnSend.setEnabled(false);
    }
}
