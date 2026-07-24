package com.yoyo.jingxi.ui.activity;

import android.os.Bundle;
import com.yoyo.jingxi.R;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.Character;
import com.yoyo.jingxi.data.entity.InnerVoice;
import com.yoyo.jingxi.data.entity.Message;
import com.yoyo.jingxi.network.ApiUrlBuilder;
import com.yoyo.jingxi.network.OpenAIManager;
import com.yoyo.jingxi.network.OpenAiRequest;
import com.yoyo.jingxi.network.OpenAiResponse;
import com.yoyo.jingxi.network.SttManager;
import com.yoyo.jingxi.network.SttProvider;
import com.yoyo.jingxi.ui.adapter.ChatAdapter;
import com.yoyo.jingxi.utils.LinkMetadataExtractor;
import com.yoyo.jingxi.utils.WebViewMetadataExtractor;
import com.yoyo.jingxi.utils.XiaohongshuCrawler;
import com.yoyo.jingxi.utils.SpUtils;
import com.yoyo.jingxi.utils.VoiceGenerateHelper;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import com.yoyo.jingxi.data.entity.ChatSession;
import com.yoyo.jingxi.data.entity.MyPersona;
import com.yoyo.jingxi.data.entity.SharedContent;

import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.view.MenuItem;
import android.widget.PopupMenu;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
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

    // 多选模式
    private boolean isMultiSelectMode = false;
    private Set<Integer> selectedMessageIds = new LinkedHashSet<>();
    private Toolbar multiSelectToolbar;
    private TextView tvSelectedCount;

    private ChatSession currentSession;
    private Character currentCharacter;
    private MyPersona currentMyPersona;

    private RecyclerView rvChat;
    private ChatAdapter chatAdapter;
    private PopupWindow innerVoicePopup;
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
    private LinearLayout layoutInputArea;
    private LinearLayout layoutFunctionPanel;
    private LinearLayout layoutMultiSelectActions;
    private Button btnMultiForwardSingle;
    private Button btnMultiForwardMerge;
    private Button btnMultiDelete;
    private Button btnMultiCancel;
    private LinearLayout btnFuncImage;
    private LinearLayout btnFuncVoice;
    private LinearLayout btnFuncCall;
    private LinearLayout btnFuncRegenerate;
    private LinearLayout btnFuncSchedule;
    private LinearLayout btnFuncMemo;
    private LinearLayout btnFuncCamera;

    private File pendingCameraPhotoFile;

    private LinearLayout layoutEmojiPanel;
    private TabLayout tabLayoutEmojiGroups;
    private RecyclerView rvEmojiPanel;
    private EmojiAdapter emojiAdapter;
    private LinearLayout layoutQuotePreview;
    private android.widget.TextView tvQuotePreview;
    private ImageView ivCloseQuote;

    private Message pendingQuoteMsg;

    private AppDatabase db;
    private java.util.concurrent.ExecutorService dbExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private boolean shouldAutoScroll = true;
    private BroadcastReceiver aiReplyStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.yoyo.jingxi.ACTION_AI_REPLY_STATUS".equals(intent.getAction())) {
                boolean isReplying = intent.getBooleanExtra("is_replying", false);
                String errorData = intent.getStringExtra("error_data");
                runOnUiThread(() -> {
                    if (!isReplying) {
                        if (getSupportActionBar() != null) {
                            getSupportActionBar().setTitle(friendName != null ? friendName : "聊天");
                        }
                        btnSend.setEnabled(true);
                        checkAndGenerateSummaryMemory();

                        // 如果有错误信息，弹出错误弹窗
                        if (errorData != null && !errorData.isEmpty()) {
                            com.yoyo.jingxi.ui.dialog.ErrorDialogFragment.newInstance(errorData)
                                .show(getSupportFragmentManager(), "error_dialog");
                        }
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

        db = AppDatabase.getDatabase(this);
        aiManager = new OpenAIManager();

        sessionId = getIntent().getIntExtra("session_id", -1);
        friendName = getIntent().getStringExtra("friend_name");

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(friendName != null ? friendName : "聊天");
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        // 多选Toolbar
        multiSelectToolbar = findViewById(R.id.toolbarMultiSelect);
        tvSelectedCount = findViewById(R.id.tvSelectedCount);
        setupMultiSelectToolbar();
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

        layoutInputArea = findViewById(R.id.layoutInputArea);
        layoutFunctionPanel = findViewById(R.id.layoutFunctionPanel);
        btnFuncImage = findViewById(R.id.btnFuncImage);

        // 多选底部操作栏
        layoutMultiSelectActions = findViewById(R.id.layoutMultiSelectActions);
        btnMultiForwardSingle = findViewById(R.id.btnMultiForwardSingle);
        btnMultiForwardMerge = findViewById(R.id.btnMultiForwardMerge);
        btnMultiDelete = findViewById(R.id.btnMultiDelete);
        btnMultiCancel = findViewById(R.id.btnMultiCancel);
        btnMultiForwardSingle.setOnClickListener(v -> startForwardActivity(0));
        btnMultiForwardMerge.setOnClickListener(v -> startForwardActivity(1));
        btnMultiDelete.setOnClickListener(v -> deleteSelectedMessages());
        btnMultiCancel.setOnClickListener(v -> exitMultiSelectMode());
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
        rvChat.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@androidx.annotation.NonNull RecyclerView recyclerView, int dx, int dy) {
                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (lm != null) {
                    int lastVisible = lm.findLastCompletelyVisibleItemPosition();
                    int totalItems = chatAdapter.getItemCount();
                    shouldAutoScroll = lastVisible >= totalItems - 2 || lastVisible == RecyclerView.NO_POSITION;
                }
            }
        });

        chatAdapter = new ChatAdapter(friendName);
        VoiceGenerateHelper vgh = new VoiceGenerateHelper(this, db, dbExecutor, mainHandler);
        chatAdapter.setVoiceGenerateHelper(vgh);
        chatAdapter.setDb(db);
        chatAdapter.setDbExecutor(dbExecutor);
        rvChat.setAdapter(chatAdapter);
        rvChat.setItemAnimator(null);
        
        chatAdapter.setOnMessageLongClickListener((msg, view) -> {
            showMessageLongClickMenu(msg, view);
        });

        // 头像长按 → 查看心声
        chatAdapter.setOnAvatarLongClickListener((msg, avatarView) -> {
            Executors.newSingleThreadExecutor().execute(() -> {
                InnerVoice iv = db.innerVoiceDao().getByMessageIdSync(msg.id);
                mainHandler.post(() -> {
                    if (iv != null) {
                        showInnerVoicePopup(msg, iv, avatarView);
                        // 标记已读 + 立即移除红点
                        chatAdapter.removeInnerVoiceMessageId(msg.id);
                        Executors.newSingleThreadExecutor().execute(() -> db.innerVoiceDao().markAsRead(iv.id));
                    } else {
                        android.widget.Toast.makeText(ChatActivity.this, "这条消息没有心声哦~", android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
            });
        });

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

            // 加载分享内容缓存
            List<SharedContent> sharedContents = db.sharedContentDao().getBySessionId(sessionId);
            if (sharedContents != null && !sharedContents.isEmpty()) {
                mainHandler.post(() -> chatAdapter.prefetchSharedContent(sharedContents));
            }
        });

        // 检查是否有分享内容ID（来自SendToAgentActivity）
        int sharedContentId = getIntent().getIntExtra("shared_content_id", 0);
        if (sharedContentId > 0) {
            // 稍等会话加载完成后触发AI回复
            new Handler().postDelayed(() -> requestAiReplyWithSharedContent(sharedContentId), 500);
        }

        // 监听消息列表变化
        db.messageDao().getMessagesBySessionId(sessionId).observe(this, messages -> {
            if (messages != null) {
                // 查询返回 DESC（最新在前），反转为 ASC 正序以正确显示时间线
                List<Message> sorted = new java.util.ArrayList<>(messages);
                java.util.Collections.reverse(sorted);
                // 过滤掉 type=100 的隐形电话上下文消息，不在聊天主界面显示
                List<Message> visibleMessages = new java.util.ArrayList<>();
                for (Message m : sorted) {
                    if (m.type != 100) {
                        visibleMessages.add(m);
                    }
                }
                // Save scroll position if user is reading history
                int savedPos = RecyclerView.NO_POSITION;
                int savedOffset = 0;
                if (!shouldAutoScroll && visibleMessages.size() > 1) {
                    LinearLayoutManager lm = (LinearLayoutManager) rvChat.getLayoutManager();
                    if (lm != null) {
                        savedPos = lm.findFirstVisibleItemPosition();
                        android.view.View v = lm.findViewByPosition(savedPos);
                        if (v != null) savedOffset = v.getTop();
                    }
                }

                final int finalSavedPos = savedPos;
                final int finalSavedOffset = savedOffset;
                chatAdapter.setMessages(visibleMessages, () -> {
                    // DiffUtil 提交完成后处理滚动位置
                    if (shouldAutoScroll && visibleMessages.size() > 0) {
                        rvChat.scrollToPosition(visibleMessages.size() - 1);
                    } else if (finalSavedPos != RecyclerView.NO_POSITION) {
                        LinearLayoutManager lm = (LinearLayoutManager) rvChat.getLayoutManager();
                        if (lm != null) {
                            lm.scrollToPositionWithOffset(finalSavedPos, finalSavedOffset);
                        }
                    }
                });

                // 加载心声数据：批量查询当前可见消息中哪些有心声
                Executors.newSingleThreadExecutor().execute(() -> {
                    java.util.List<Integer> ids = new java.util.ArrayList<>();
                    for (Message m : visibleMessages) {
                        if (!m.isFromUser) ids.add(m.id);
                    }
                    if (!ids.isEmpty()) {
                        java.util.List<InnerVoice> voices = db.innerVoiceDao().getUnreadByMessageIdsSync(ids);
                        java.util.Set<Integer> voiceMsgIds = new java.util.HashSet<>();
                        for (InnerVoice iv : voices) {
                            voiceMsgIds.add(iv.messageId);
                        }
                        mainHandler.post(() -> chatAdapter.setInnerVoiceMessageIds(voiceMsgIds));
                    }
                });

                // 刷新分享内容缓存，确保转发来的链接卡片立即可见
                Executors.newSingleThreadExecutor().execute(() -> {
                    List<SharedContent> contents = db.sharedContentDao().getBySessionId(sessionId);
                    if (contents != null && !contents.isEmpty()) {
                        mainHandler.post(() -> chatAdapter.prefetchSharedContent(contents));
                    }
                });

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
                ivVoiceToggle.setImageResource(R.drawable.ic_keyboard);
            } else {
                // 切换回文本模式
                btnVoiceRecord.setVisibility(View.GONE);
                etInput.setVisibility(View.VISIBLE);
                ivVoiceToggle.setImageResource(R.drawable.ic_mic);
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
                intent.putExtra("is_incoming", false);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
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

        btnFuncCamera = findViewById(R.id.btnFuncCamera);
        btnFuncCamera.setOnClickListener(v -> handleCameraFunc());
        
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
        if (requestCode == REQUEST_PICK_BG && resultCode == RESULT_OK && data != null) {
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
        Uri destinationUri = Uri.fromFile(new java.io.File(getFilesDir(), destinationFileName));
        
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

    /**
     * 多图选择器：使用 GetMultipleContents 支持一次选多张。
     * 选 1 张 → type=3 普通单图；选 2+ 张 → type=5 合并多图 + PhotoStackView。
     */
    private final ActivityResultLauncher<String> multiImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetMultipleContents(),
            uris -> {
                if (uris == null || uris.isEmpty()) return;
                Executors.newSingleThreadExecutor().execute(() -> {
                    try {
                        java.util.List<String> filePaths = new java.util.ArrayList<>();
                        for (Uri uri : uris) {
                            try {
                                java.io.InputStream inputStream = getContentResolver().openInputStream(uri);
                                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(inputStream);
                                if (bitmap == null) continue;

                                // 压缩图片
                                int maxWidth = 1024;
                                int maxHeight = 1024;
                                float scale = Math.min(((float) maxWidth / bitmap.getWidth()), ((float) maxHeight / bitmap.getHeight()));
                                if (scale < 1) {
                                    android.graphics.Matrix matrix = new android.graphics.Matrix();
                                    matrix.postScale(scale, scale);
                                    bitmap = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                                }

                                java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, outputStream);
                                byte[] byteArray = outputStream.toByteArray();

                                File imageDir = new File(getExternalFilesDir(null), "images");
                                if (!imageDir.exists()) imageDir.mkdirs();
                                String fileName = "msg_img_" + System.currentTimeMillis() + "_" + filePaths.size() + ".jpg";
                                File imageFile = new File(imageDir, fileName);
                                java.io.FileOutputStream fos = new java.io.FileOutputStream(imageFile);
                                fos.write(byteArray);
                                fos.close();
                                filePaths.add(imageFile.getAbsolutePath());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        if (filePaths.isEmpty()) {
                            mainHandler.post(() -> Toast.makeText(ChatActivity.this, "处理图片失败", Toast.LENGTH_SHORT).show());
                            return;
                        }

                        if (filePaths.size() == 1) {
                            // 单张 → 普通单图
                            mainHandler.post(() -> sendImageMessage(filePaths.get(0), false, null));
                        } else {
                            // 多张 → 合并多图 + PhotoStackView
                            mainHandler.post(() -> sendMultiImageMessage(filePaths));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        mainHandler.post(() -> Toast.makeText(ChatActivity.this, "处理图片失败", Toast.LENGTH_SHORT).show());
                    }
                });
            }
    );

    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && pendingCameraPhotoFile != null && pendingCameraPhotoFile.exists()) {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        try {
                            java.io.InputStream inputStream = new java.io.FileInputStream(pendingCameraPhotoFile);
                            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(inputStream);
                            inputStream.close();

                            int maxWidth = 1024;
                            int maxHeight = 1024;
                            float scale = Math.min(((float) maxWidth / bitmap.getWidth()), ((float) maxHeight / bitmap.getHeight()));
                            if (scale < 1) {
                                android.graphics.Matrix matrix = new android.graphics.Matrix();
                                matrix.postScale(scale, scale);
                                bitmap = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                            }

                            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, outputStream);
                            byte[] byteArray = outputStream.toByteArray();

                            // 保存图片到文件，避免 Base64 存入数据库导致 CursorWindow 溢出
                            File imageDir = new File(getExternalFilesDir(null), "images");
                            if (!imageDir.exists()) imageDir.mkdirs();
                            String fileName = "msg_img_" + System.currentTimeMillis() + ".jpg";
                            File imageFile = new File(imageDir, fileName);
                            java.io.FileOutputStream fos = new java.io.FileOutputStream(imageFile);
                            fos.write(byteArray);
                            fos.close();
                            String filePath = imageFile.getAbsolutePath();

                            mainHandler.post(() -> sendImageMessage(filePath, false, null));
                        } catch (Exception e) {
                            e.printStackTrace();
                            mainHandler.post(() -> Toast.makeText(ChatActivity.this, "处理照片失败", Toast.LENGTH_SHORT).show());
                        } finally {
                            if (pendingCameraPhotoFile != null) {
                                pendingCameraPhotoFile.delete();
                            }
                        }
                    });
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
                // 真实图片（多选，1张=单图，2+张=PhotoStackView堆叠）
                multiImageLauncher.launch("image/*");
            }
        });
        builder.show();
        layoutFunctionPanel.setVisibility(View.GONE);
    }

    private void handleCameraFunc() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.CAMERA}, 101);
            return;
        }

        try {
            pendingCameraPhotoFile = new File(getCacheDir(), "camera_capture_" + System.currentTimeMillis() + ".jpg");
            Uri photoUri = androidx.core.content.FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", pendingCameraPhotoFile);

            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            cameraLauncher.launch(intent);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "无法启动相机", Toast.LENGTH_SHORT).show();
        }

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

    /**
     * 发送多图消息（type=5），图片路径用逗号拼接存入 imageUrl。
     * 聊天界面中通过 PhotoStackView 堆叠显示。
     */
    private void sendMultiImageMessage(java.util.List<String> filePaths) {
        if (currentSession == null || filePaths == null || filePaths.isEmpty()) return;
        Message msg = new Message();
        msg.sessionId = sessionId;
        msg.characterId = currentSession.characterId;
        msg.isFromUser = true;
        msg.timestamp = System.currentTimeMillis();
        msg.quoteMessageId = pendingQuoteMsg != null ? pendingQuoteMsg.id : -1;
        pendingQuoteMsg = null;
        msg.type = 5; // 合并多图
        msg.imageUrl = TextUtils.join(",", filePaths);
        msg.content = "[图片]";

        Executors.newSingleThreadExecutor().execute(() -> {
            db.messageDao().insert(msg);
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
        File audioFile = new File(filePath);
        if (!audioFile.exists()) {
            Toast.makeText(this, "录音文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "正在识别语音...", Toast.LENGTH_SHORT).show();

        SttManager.getInstance().recognize(audioFile, new SttProvider.Callback() {
            @Override
            public void onResult(String recognizedText) {
                if (!TextUtils.isEmpty(recognizedText)) {
                    etVoiceConfirmText.setText(recognizedText);
                    flVoiceConfirmOverlay.setVisibility(View.VISIBLE);
                } else {
                    Toast.makeText(ChatActivity.this, "未识别到语音内容", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(int code, String message, boolean canFallback) {
                Toast.makeText(ChatActivity.this, message, Toast.LENGTH_SHORT).show();
                if (currentVoiceFilePath != null) {
                    new File(currentVoiceFilePath).delete();
                    currentVoiceFilePath = null;
                }
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

        // 通话进行中：聊天页打字走批量模式，空输入才触发 AI 语音回复
        if (CallActivity.instance != null && CallActivity.instance.getSessionId() == sessionId && !CallActivity.instance.isCallEnded()) {
            if (TextUtils.isEmpty(content)) {
                CallActivity.instance.requestAiReplyFromChat();
            } else {
                CallActivity.instance.sendTextFromChat(content, true);
                etInput.setText("");
            }
            return;
        }

        if (TextUtils.isEmpty(content)) {
            // 输入为空时，触发 AI 回复
            requestAiReply();
            return;
        }

        if (currentSession == null) return;

        // 检测是否为链接（http/https 或常见短链）
        String detectedUrl = extractUrlFromText(content);
        final boolean isUrl = detectedUrl != null;

        Message msg = new Message();
        msg.sessionId = sessionId;
        msg.characterId = currentSession.characterId;
        msg.content = content;
        msg.isFromUser = true;
        msg.type = isUrl ? 7 : 0;
        msg.timestamp = System.currentTimeMillis();
        if (pendingQuoteMsg != null) {
            msg.quoteMessageId = pendingQuoteMsg.id;
            pendingQuoteMsg = null;
        } else {
            msg.quoteMessageId = -1;
        }

        final String urlForMeta = detectedUrl;
        Executors.newSingleThreadExecutor().execute(() -> {
            long msgId = db.messageDao().insert(msg);
            db.chatSessionDao().updateUnreadCount(sessionId, 0);

            // 如果是链接，后台提取元数据并创建 SharedContent
            if (isUrl && urlForMeta != null) {
                // 先创建占位 SharedContent
                SharedContent sc = new SharedContent();
                sc.sourceUrl = urlForMeta;
                sc.siteName = LinkMetadataExtractor.fallbackSiteName(urlForMeta);
                sc.contentTitle = sc.siteName; // 临时显示站点名，爬虫成功后替换
                sc.timestamp = System.currentTimeMillis();
                sc.sessionId = sessionId;
                sc.characterId = currentSession.characterId;
                sc.messageId = (int) msgId;
                sc.id = (int) db.sharedContentDao().insert(sc);

                // 更新 adapter 缓存，初始显示站点名
                SharedContent placeholderSc = sc;
                mainHandler.post(() -> chatAdapter.addSharedContentToCache(placeholderSc));

                final SharedContent updatedSc = sc;
                final boolean isXhs = urlForMeta.contains("xiaohongshu.com")
                        || urlForMeta.contains("xhslink.com")
                        || urlForMeta.contains("xhslink.cn");

                if (isXhs) {
                    // 小红书：跳过 HTTP 提取（会消耗 xsec_token），直接走爬虫
                    XiaohongshuCrawler.crawl(xhsMeta -> {
                        updatedSc.contentTitle = xhsMeta.title;
                        updatedSc.description = xhsMeta.description;
                        updatedSc.thumbnailUrl = xhsMeta.imageUrl;
                        if (xhsMeta.siteName != null) updatedSc.siteName = xhsMeta.siteName;
                        if (xhsMeta.fullText != null) updatedSc.fullText = xhsMeta.fullText;
                        if (xhsMeta.imageUrls != null && !xhsMeta.imageUrls.isEmpty()) {
                            updatedSc.imageUrlsJson = new com.google.gson.Gson().toJson(xhsMeta.imageUrls);
                        }
                        Executors.newSingleThreadExecutor().execute(() -> {
                            db.sharedContentDao().update(updatedSc);
                            mainHandler.post(() ->
                                    chatAdapter.addSharedContentToCache(updatedSc));
                        });
                    }, urlForMeta);
                } else {
                    // 其他链接：HTTP 提取元数据
                    LinkMetadataExtractor.LinkMetadata meta = LinkMetadataExtractor.extract(urlForMeta);
                    boolean hasTitle = meta.title != null && !meta.title.isEmpty()
                            && !meta.title.equals(urlForMeta);

                    sc.contentTitle = meta.title;
                    sc.description = meta.description;
                    sc.thumbnailUrl = meta.imageUrl;
                    sc.faviconUrl = meta.faviconUrl;
                    if (meta.siteName != null) sc.siteName = meta.siteName;
                    if (meta.fullText != null) sc.fullText = meta.fullText;
                    if (meta.imageUrls != null && !meta.imageUrls.isEmpty()) {
                        sc.imageUrlsJson = new com.google.gson.Gson().toJson(meta.imageUrls);
                    }
                    db.sharedContentDao().update(sc);

                    if (hasTitle) {
                        mainHandler.post(() -> chatAdapter.addSharedContentToCache(updatedSc));
                    } else {
                        // HTTP 无结果 → WebView 兜底
                        final String webViewUrl = (meta.resolvedUrl != null) ? meta.resolvedUrl : urlForMeta;
                        mainHandler.post(() ->
                            WebViewMetadataExtractor.extract(ChatActivity.this, webViewUrl,
                                wvMeta -> {
                                    updatedSc.contentTitle = wvMeta.title;
                                    updatedSc.description = wvMeta.description;
                                    updatedSc.thumbnailUrl = wvMeta.imageUrl;
                                    if (wvMeta.siteName != null) updatedSc.siteName = wvMeta.siteName;
                                    if (wvMeta.fullText != null) updatedSc.fullText = wvMeta.fullText;
                                    if (wvMeta.imageUrls != null && !wvMeta.imageUrls.isEmpty()) {
                                        updatedSc.imageUrlsJson = new com.google.gson.Gson().toJson(wvMeta.imageUrls);
                                    }
                                    Executors.newSingleThreadExecutor().execute(() -> {
                                        db.sharedContentDao().update(updatedSc);
                                        mainHandler.post(() ->
                                                chatAdapter.addSharedContentToCache(updatedSc));
                                    });
                                }));
                    }
                }
            }
        });

        etInput.setText("");
        clearQuote();
    }

    /**
     * 从文本中提取 URL。支持完整链接和常见短链域名。
     */
    private String extractUrlFromText(String text) {
        if (text == null) return null;
        // 完整 http/https 链接
        if (text.startsWith("http://") || text.startsWith("https://")) {
            int spaceIdx = text.indexOf(' ');
            return spaceIdx > 0 ? text.substring(0, spaceIdx) : text;
        }
        // 常见短链域名（小红书、抖音、B站等）
        String[] shortDomains = {"xhslink.com", "xhslink.cn", "b23.tv", "v.douyin.com", "t.cn", "dwz.cn"};
        for (String domain : shortDomains) {
            int idx = text.indexOf(domain);
            if (idx >= 0) {
                // 向前找到协议或开头
                int start = idx;
                while (start > 0 && text.charAt(start - 1) != ' ' && text.charAt(start - 1) != '\n') {
                    start--;
                }
                int end = idx + domain.length();
                while (end < text.length() && text.charAt(end) != ' ' && text.charAt(end) != '\n') {
                    end++;
                }
                String candidate = text.substring(start, end);
                if (!candidate.startsWith("http")) {
                    candidate = "https://" + candidate;
                }
                return candidate;
            }
        }
        return null;
    }

    private void checkAndGenerateSummaryMemory() {
        int summaryRounds = SpUtils.getInt("SETTING_SUMMARY_ROUNDS", 0);
        if (summaryRounds <= 0 || currentCharacter == null) return;

        Executors.newSingleThreadExecutor().execute(() -> {
            boolean isSummarizing = SpUtils.getBoolean("IS_SUMMARIZING_" + currentCharacter.id, false);
            if (isSummarizing) return;

            int lastSummaryMsgId = SpUtils.getInt("LAST_SUMMARY_MSG_ID_" + sessionId, 0);
            // Get max message ID in this session
            int currentMaxMsgId = 0;
            List<Message> allSessionMsgs = db.messageDao().getRecentMessagesBySessionIdSync(sessionId, 1);
            if (allSessionMsgs != null && !allSessionMsgs.isEmpty()) {
                currentMaxMsgId = allSessionMsgs.get(0).id;
            }
            int newSinceLastSummary = currentMaxMsgId - lastSummaryMsgId;

            // Trigger only if enough NEW messages since last summary
            if (newSinceLastSummary >= summaryRounds * 2) {
                SpUtils.putBoolean("IS_SUMMARIZING_" + currentCharacter.id, true);
                String apiKey = SpUtils.getString("OPENAI_API_KEY", "");
                String endpoint = SpUtils.getString("API_ENDPOINT", "https://api.openai.com/v1/");
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
                String prompt = "请根据以下对话，以角色\"" + currentCharacter.name + "\"的第一人称视角写一篇200字以内的\"记忆日记\"。\n" +
                    "要求：\n" +
                    "1. 用第一人称（\"我\"），带情感地回忆这段共同经历\n" +
                    "2. 不要干巴巴的第三方总结，要有情绪流动和内心细节\n" +
                    "3. 使用双方的真实姓名（\"" + currentCharacter.name + "\" 和 \"" + myName + "\"）\n\n" +
                    "请返回纯JSON（不要markdown标记）：\n" +
                    "{\n" +
                    "  \"title\": \"简短标题（15字内）\",\n" +
                    "  \"keywords\": [\"关键词1\", \"关键词2\", \"关键词3\"],\n" +
                    "  \"subjectiveDiary\": \"第一人称日记内容（200字内）\",\n" +
                    "  \"emotionalTone\": \"开心/难过/紧张/平静/兴奋/愤怒/温馨/尴尬\",\n" +
                    "  \"importanceLevel\": 1到5分（数字）\n" +
                    "}\n\n对话内容：\n" + dialogue.toString();

                OpenAiRequest request = new OpenAiRequest();
                request.model = model;
                request.messages = new java.util.ArrayList<>();
                request.messages.add(new OpenAiRequest.Message("user", prompt));

                try {
                    retrofit2.Response<OpenAiResponse> response = aiManager.getApi().createChatCompletion(ApiUrlBuilder.chatCompletions(endpoint), "Bearer " + apiKey, request).execute();
                    if (response.isSuccessful() && response.body() != null && response.body().choices != null
                            && !response.body().choices.isEmpty()
                            && response.body().choices.get(0) != null
                            && response.body().choices.get(0).message != null
                            && response.body().choices.get(0).message.content != null) {
                        String rawContent = response.body().choices.get(0).message.content.trim();
                        String summaryText = rawContent;

                        // Try parsing as JSON for structured episodic memory
                        try {
                            String cleaned = rawContent;
                            if (cleaned.contains("```json")) {
                                int s = cleaned.indexOf("```json") + 7;
                                int e = cleaned.lastIndexOf("```");
                                if (e > s) cleaned = cleaned.substring(s, e).trim();
                            } else if (cleaned.contains("```")) {
                                int s = cleaned.indexOf("```") + 3;
                                int e = cleaned.lastIndexOf("```");
                                if (e > s) cleaned = cleaned.substring(s, e).trim();
                            }
                            com.google.gson.JsonObject json = new com.google.gson.Gson().fromJson(cleaned, com.google.gson.JsonObject.class);
                            if (json.has("subjectiveDiary")) {
                                String diary = json.get("subjectiveDiary").getAsString();
                                summaryText = diary;
                                String title = json.has("title") ? json.get("title").getAsString() : "";
                                String emotionalTone = json.has("emotionalTone") ? json.get("emotionalTone").getAsString() : "平静";
                                int importance = json.has("importanceLevel") ? json.get("importanceLevel").getAsInt() : 1;
                                String participants = json.has("participants") ? json.get("participants").getAsString() : "";
                                com.google.gson.JsonArray kwArr = json.has("keywords") ? json.getAsJsonArray("keywords") : null;
                                String keywordsStr = "";
                                if (kwArr != null) {
                                    StringBuilder kwSb = new StringBuilder();
                                    for (int k = 0; k < kwArr.size(); k++) {
                                        if (k > 0) kwSb.append(",");
                                        kwSb.append(kwArr.get(k).getAsString());
                                    }
                                    keywordsStr = kwSb.toString();
                                }
                                String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());
                                com.yoyo.jingxi.utils.MemoryManager.getInstance().init(db);
                                com.yoyo.jingxi.utils.MemoryManager.getInstance().addEpisodicMemory(
                                    currentCharacter.id, currentSession.myPersonaName != null ? currentSession.myPersonaName : "", today, title, diary, keywordsStr, emotionalTone, importance, participants);
                            }
                        } catch (Exception jsonEx) {
                            // If JSON parsing fails, use raw text as summary
                        }

                        // Save backward-compatible flat memory only if migration not yet complete
                        if (!SpUtils.getBoolean("MEMORY_V2_MIGRATED", false)) {
                            com.yoyo.jingxi.data.entity.Memory memory = new com.yoyo.jingxi.data.entity.Memory();
                            memory.characterId = currentCharacter.id;
                            memory.type = 0;
                            memory.content = summaryText;
                            memory.timestamp = System.currentTimeMillis();
                            db.memoryDao().insert(memory);
                        }
                        // Save max processed message ID for next threshold check
                        int maxMsgId = 0;
                        for (Message h : history) {
                            if (h.id > maxMsgId) maxMsgId = h.id;
                        }
                        SpUtils.putInt("LAST_SUMMARY_MSG_ID_" + sessionId, maxMsgId);
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
        // 多选模式下不显示长按菜单
        if (isMultiSelectMode) return;

        int singleImgIdx = chatAdapter.consumePendingSingleImageIndex();
        boolean isSingleImageOp = (singleImgIdx >= 0 && msg.type == 5 && msg.imageUrl != null);

        PopupMenu popupMenu = new PopupMenu(this, anchorView);
        popupMenu.getMenu().add(0, 1, 0, "复制");
        popupMenu.getMenu().add(0, 2, 0, "转发");
        popupMenu.getMenu().add(0, 3, 0, "删除");
        if (msg.isFromUser) {
            popupMenu.getMenu().add(0, 4, 0, "撤回");
        }
        popupMenu.getMenu().add(0, 5, 0, "引用");
        popupMenu.getMenu().add(0, 6, 0, "多选");
        if (msg.type == 0) {
            popupMenu.getMenu().add(0, 7, 0, "编辑");
        }

        popupMenu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: {
                    if (isSingleImageOp) {
                        // 复制图片 URL
                        String[] urls = msg.imageUrl.split(",");
                        if (singleImgIdx < urls.length) {
                            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                            ClipData clip = ClipData.newPlainText("image_url", urls[singleImgIdx]);
                            clipboard.setPrimaryClip(clip);
                            Toast.makeText(this, "已复制图片路径", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        String textToCopy = msg.content;
                        if (!TextUtils.isEmpty(textToCopy)) {
                            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                            ClipData clip = ClipData.newPlainText("message", textToCopy);
                            clipboard.setPrimaryClip(clip);
                            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
                        }
                    }
                    break;
                }
                case 2:
                case 6:
                    enterMultiSelectMode(msg);
                    break;
                case 3:
                    if (isSingleImageOp) {
                        removeImageFromMultiMessage(msg, singleImgIdx);
                    } else {
                        Executors.newSingleThreadExecutor().execute(() -> db.messageDao().delete(msg));
                    }
                    break;
                case 4:
                    if (isSingleImageOp) {
                        removeImageFromMultiMessage(msg, singleImgIdx);
                    } else {
                        Executors.newSingleThreadExecutor().execute(() -> {
                            msg.type = 99;
                            msg.content = "你撤回了一条消息";
                            db.messageDao().update(msg);
                        });
                    }
                    break;
                case 5:
                    quoteMessage(msg);
                    break;
                case 7:
                    showEditMessageDialog(msg);
                    break;
            }
            return true;
        });
        popupMenu.show();
    }

    /** 从 type=5 多图消息中移除指定索引的单张图片 */
    private void removeImageFromMultiMessage(Message msg, int index) {
        if (msg.type != 5 || msg.imageUrl == null) return;
        String[] urls = msg.imageUrl.split(",");
        if (index < 0 || index >= urls.length) return;

        java.util.List<String> newList = new java.util.ArrayList<>();
        for (int i = 0; i < urls.length; i++) {
            String u = urls[i].trim();
            if (i != index && !u.isEmpty()) newList.add(u);
        }

        if (newList.isEmpty()) {
            // 从适配器列表中移除该消息（即时 UI 反馈）
            chatAdapter.clearMultiImageExpandState(msg.id);
            java.util.List<Message> cur = new java.util.ArrayList<>(chatAdapter.getCurrentMessages());
            cur.remove(msg);
            chatAdapter.setMessages(cur);
            dbExecutor.execute(() -> db.messageDao().deleteById(msg.id));
        } else {
            msg.type = newList.size() == 1 ? 3 : 5;
            msg.imageUrl = android.text.TextUtils.join(",", newList);
            if (newList.size() == 1) {
                chatAdapter.clearMultiImageExpandState(msg.id);
            }
            // 先更新 UI（同步），再写 DB（异步）
            chatAdapter.updateMessageImageUrl(msg.id, msg.imageUrl, msg.type);
            dbExecutor.execute(() -> db.messageDao().update(msg));
        }
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

    // ==================== 多选模式 ====================

    private void enterMultiSelectMode(Message initialMsg) {
        isMultiSelectMode = true;
        selectedMessageIds.clear();
        if (initialMsg != null) {
            selectedMessageIds.add(initialMsg.id);
        }
        chatAdapter.setMultiSelectMode(true, selectedMessageIds);
        chatAdapter.setOnMessageSelectListener((msg, isSelected) -> updateMultiSelectCount());
        updateMultiSelectCount();
        // 切换Toolbar
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        if (multiSelectToolbar != null) multiSelectToolbar.setVisibility(View.VISIBLE);
        // 隐藏输入区域，显示底部多选操作栏
        if (layoutInputArea != null) layoutInputArea.setVisibility(View.GONE);
        if (layoutQuotePreview != null) layoutQuotePreview.setVisibility(View.GONE);
        if (layoutEmojiPanel != null) layoutEmojiPanel.setVisibility(View.GONE);
        if (layoutFunctionPanel != null) layoutFunctionPanel.setVisibility(View.GONE);
        if (layoutMultiSelectActions != null) layoutMultiSelectActions.setVisibility(View.VISIBLE);
        // 关闭软键盘
        View currentFocus = getCurrentFocus();
        if (currentFocus != null) {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
        }
    }

    private void exitMultiSelectMode() {
        isMultiSelectMode = false;
        selectedMessageIds.clear();
        chatAdapter.setMultiSelectMode(false, null);
        chatAdapter.setOnMessageSelectListener(null);
        if (multiSelectToolbar != null) multiSelectToolbar.setVisibility(View.GONE);
        if (getSupportActionBar() != null) getSupportActionBar().show();
        // 恢复输入区域，隐藏底部多选操作栏
        if (layoutMultiSelectActions != null) layoutMultiSelectActions.setVisibility(View.GONE);
        if (layoutInputArea != null) layoutInputArea.setVisibility(View.VISIBLE);
    }

    private void updateMultiSelectCount() {
        if (tvSelectedCount != null) {
            int count = selectedMessageIds.size();
            tvSelectedCount.setText("已选择 " + count + " 条");
        }
    }

    private void startForwardActivity(int mode) {
        if (selectedMessageIds.isEmpty()) {
            Toast.makeText(this, "请至少选择一条消息", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, ForwardTargetActivity.class);
        intent.putIntegerArrayListExtra("selected_message_ids", new ArrayList<>(selectedMessageIds));
        intent.putExtra("source_session_id", sessionId);
        intent.putExtra("source_character_id", currentCharacter != null ? currentCharacter.id : 0);
        intent.putExtra("friend_name", friendName);
        intent.putExtra("forward_mode", mode);
        startActivity(intent);
        exitMultiSelectMode();
    }

    private void deleteSelectedMessages() {
        if (selectedMessageIds.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("删除消息")
                .setMessage("确定删除选中的 " + selectedMessageIds.size() + " 条消息吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    // 复制到局部变量，防止 exitMultiSelectMode 清空 set
                    final List<Integer> idsToDelete = new ArrayList<>(selectedMessageIds);
                    Executors.newSingleThreadExecutor().execute(() -> {
                        for (int msgId : idsToDelete) {
                            Message msg = db.messageDao().getMessageByIdSync(msgId);
                            if (msg != null) {
                                db.messageDao().delete(msg);
                            }
                        }
                    });
                    exitMultiSelectMode();
                    Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void setupMultiSelectToolbar() {
        if (multiSelectToolbar == null) return;
        multiSelectToolbar.setNavigationOnClickListener(v -> exitMultiSelectMode());
    }

    // ==================== 多选模式结束 ====================

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // 处理新的分享内容
        int sharedContentId = intent.getIntExtra("shared_content_id", 0);
        if (sharedContentId > 0) {
            new Handler().postDelayed(() -> requestAiReplyWithSharedContent(sharedContentId), 500);
        }
    }

    private void requestAiReplyWithSharedContent(int sharedContentId) {
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
        serviceIntent.putExtra(com.yoyo.jingxi.service.AiReplyService.EXTRA_SHARED_CONTENT_ID, sharedContentId);
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception e) {
            android.util.Log.e("ChatActivity", "Failed to start AiReplyService with shared content", e);
            try {
                startService(serviceIntent);
            } catch (Exception e2) {
                android.util.Log.e("ChatActivity", "startService also failed", e2);
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(friendName != null ? friendName : "聊天");
                }
                btnSend.setEnabled(true);
                return;
            }
        }
        btnSend.setEnabled(false);
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
                // Android 12+ 后台启动前台服务限制：回退到 startService()
                // AiReplyService.onStartCommand 已无条件调用 startForeground()，回退安全
                android.util.Log.w("ChatActivity", "startForegroundService failed, falling back to startService: " + e.getMessage());
                try {
                    startService(serviceIntent);
                } catch (Exception e2) {
                    android.util.Log.e("ChatActivity", "startService also failed", e2);
                    Toast.makeText(this, "无法在后台启动AI回复，请保持应用在前台", Toast.LENGTH_SHORT).show();
                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setTitle(friendName != null ? friendName : "聊天");
                    }
                    btnSend.setEnabled(true);
                    return;
                }
            }
        } else {
            startService(serviceIntent);
        }
        // 发出请求后禁用发送按钮
        btnSend.setEnabled(false);
    }

    private void showInnerVoicePopup(Message msg, InnerVoice iv, View anchorView) {
        // 先关闭旧的
        if (innerVoicePopup != null && innerVoicePopup.isShowing()) {
            innerVoicePopup.dismiss();
        }

        View popupView = LayoutInflater.from(this).inflate(R.layout.popup_inner_voice, null);

        // 设置标题
        TextView tvTitle = popupView.findViewById(R.id.tvInnerVoiceTitle);
        tvTitle.setText("💭 " + friendName + " 的心声");

        // 设置情绪标签
        TextView tvEmotion = popupView.findViewById(R.id.tvInnerVoiceEmotion);
        if (iv.emotion != null && !iv.emotion.isEmpty()) {
            tvEmotion.setText(iv.emotion);
            tvEmotion.setVisibility(View.VISIBLE);
        } else {
            tvEmotion.setVisibility(View.GONE);
        }

        // 设置心声内容
        TextView tvContent = popupView.findViewById(R.id.tvInnerVoiceContent);
        tvContent.setText(iv.content);

        innerVoicePopup = new PopupWindow(popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);  // focusable=true，点击外部自动关闭

        innerVoicePopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        innerVoicePopup.setElevation(16);

        // 弹出位置：默认显示在锚点 view（头像）的上方
        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int popupWidth = popupView.getMeasuredWidth();
        int popupHeight = popupView.getMeasuredHeight();

        int[] location = new int[2];
        anchorView.getLocationOnScreen(location);
        int anchorCenterX = location[0] + anchorView.getWidth() / 2;
        int anchorTop = location[1];

        // x: 以头像水平中心为基准，但不出屏幕
        int x = Math.max(16, anchorCenterX - popupWidth / 2);
        x = Math.min(x, getResources().getDisplayMetrics().widthPixels - popupWidth - 16);
        // y: 默认在头像上方，空间不够则放头像下方
        int y = anchorTop - popupHeight - 8;
        if (y < 0) {
            y = anchorTop + anchorView.getHeight() + 8;
        }

        innerVoicePopup.showAtLocation(
                findViewById(android.R.id.content), Gravity.NO_GRAVITY, x, y);

        // 弹出动画：轻微缩放
        popupView.setScaleX(0.9f);
        popupView.setScaleY(0.9f);
        popupView.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
    }
}
