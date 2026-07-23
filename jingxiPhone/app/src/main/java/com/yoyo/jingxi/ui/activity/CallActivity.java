package com.yoyo.jingxi.ui.activity;

import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.ui.adapter.CallMessageAdapter;
import com.yoyo.jingxi.data.entity.CallMessage;
import com.yoyo.jingxi.data.entity.CallRecord;
import com.yoyo.jingxi.data.entity.Character;
import com.yoyo.jingxi.data.entity.ChatSession;
import com.yoyo.jingxi.data.entity.Message;
import com.yoyo.jingxi.data.entity.MyPersona;
import com.yoyo.jingxi.network.OpenAIManager;
import com.yoyo.jingxi.network.OpenAiRequest;
import com.yoyo.jingxi.network.OpenAiResponse;
import com.yoyo.jingxi.network.SttManager;
import com.yoyo.jingxi.network.SttProvider;
import com.yoyo.jingxi.service.CallForegroundService;
import com.yoyo.jingxi.utils.SpUtils;
import com.yoyo.jingxi.utils.VoiceGenerateHelper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CallActivity extends AppCompatActivity {

    private int sessionId;
    private boolean isIncoming;
    private String initialMessage;
    private ChatSession currentSession;
    private Character currentCharacter;
    private MyPersona currentMyPersona;

    private AppDatabase db;
    private OpenAIManager aiManager;
    private VoiceGenerateHelper voiceGenerateHelper;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private java.util.concurrent.ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    
    private ImageView ivCallAvatar;
    private TextView tvCallName;
    private TextView tvCallStatus;
    private TextView tvCallDuration;
    
    private LinearLayout llCallActions;
    private LinearLayout btnHangUp;
    private LinearLayout btnAccept;
    private LinearLayout btnSpeak;
    private TextView tvHangUpText;
    private TextView tvSpeakText;
    private ImageView ivSpeakIcon;
    
    private LinearLayout llKeyboardInput;
    private EditText etCallInput;
    private Button btnCallSend;

    // 通话状态管理
    private boolean isCallConnected = false;
    private boolean isRecording = false;
    private boolean isCallEnded = false;
    
    private long callStartTime;
    private long currentCallRecordId = -1;
    private List<CallMessage> tempCallMessages = new ArrayList<>();
    
    private android.media.MediaRecorder mediaRecorder;
    private String currentVoiceFilePath;
    private android.media.MediaPlayer mediaPlayer;
    private android.media.AudioManager audioManager;
    private boolean hasAudioFocus = false;

    // 免提/扬声器切换
    private LinearLayout btnSpeaker;
    private ImageView ivSpeakerIcon;
    private TextView tvSpeakerText;
    private boolean isSpeakerEnabled = false;  // false=听筒(handset), true=免提(speaker)

    // 接近传感器熄屏（脸靠近自动黑屏，远离自动亮屏）
    private PowerManager.WakeLock proximityWakeLock;
    private PowerManager powerManager;

    public static CallActivity instance;

    public int getSessionId() {
        return sessionId;
    }

    public boolean isCallEnded() {
        return isCallEnded;
    }

    // 悬浮窗相关
    private WindowManager windowManager;
    private View floatingView;
    private TextView tvFloatingTime;
    private TextView tvFloatingStatus;
    private ImageView ivFloatingAvatar;
    private boolean isFloating = false;

    // 语音播放队列
    private static class VoiceTask {
        String content;
        boolean hangupAfter;
        String emotion;

        VoiceTask(String content, boolean hangupAfter, String emotion) {
            this.content = content;
            this.hangupAfter = hangupAfter;
            this.emotion = emotion;
        }
    }
    private java.util.Queue<VoiceTask> voiceTaskQueue = new java.util.LinkedList<>();
    private boolean isProcessingVoice = false;
    
    // UI - 字幕
    private RecyclerView rvCallSubtitles;
    private CallMessageAdapter subtitleAdapter;

    private Runnable durationRunnable = new Runnable() {
        @Override
        public void run() {
            if (isCallConnected && !isCallEnded) {
                long durationSeconds = (System.currentTimeMillis() - callStartTime) / 1000;
                long mins = durationSeconds / 60;
                long secs = durationSeconds % 60;
                tvCallDuration.setText(String.format(java.util.Locale.getDefault(), "%02d:%02d", mins, secs));
                mainHandler.postDelayed(this, 1000);
                updateFloatingWindowUI();
            }
        }
    };

    @Override
    protected void onStop() {
        super.onStop();
        // 进入后台时释放接近传感器锁（悬浮窗模式下屏幕应正常显示）
        releaseProximityWakeLock();
        if (!isCallEnded) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                // 如果没有悬浮窗权限，使用 moveTaskToBack 或者让 Service 至少启动保证前台通知
                // 这里我们显示一个提示，并尝试启动服务保活
                mainHandler.post(() -> {
                    Toast.makeText(this, "未开启悬浮窗权限，应用将在后台保持通话", Toast.LENGTH_LONG).show();
                });
                
                Intent serviceIntent = new Intent(this, CallForegroundService.class);
                serviceIntent.putExtra("title", currentCharacter != null ? currentCharacter.name : "正在通话中");
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try {
                            startForegroundService(serviceIntent);
                        } catch (Exception e) {
                            android.util.Log.e("CallActivity", "Failed to start foreground service", e);
                            startService(serviceIntent);
                        }
                    } else {
                        startService(serviceIntent);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                showFloatingWindow();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideFloatingWindow();
        // 通话界面使用深色滤镜背景，不受主题色影响
        com.yoyo.jingxi.utils.ThemeManager.applyCallBackground(this);
        // 从悬浮窗返回后，如果在听筒模式，重新获取接近传感器锁
        if (isCallConnected && !isSpeakerEnabled) {
            acquireProximityWakeLock();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        int newSessionId = intent.getIntExtra("session_id", -1);
        if (newSessionId != -1) {
            boolean newIsIncoming = intent.getBooleanExtra("is_incoming", false);
            boolean sessionChanged = (newSessionId != sessionId);
            // 同时获取新的开场白
            String newInitialMessage = intent.getStringExtra("initial_message");
            if (newInitialMessage != null) {
                initialMessage = newInitialMessage;
            }

            if (isCallEnded || sessionChanged) {
                // 新通话：重置所有状态，重新加载数据
                isIncoming = newIsIncoming;
                sessionId = newSessionId;
                isCallEnded = false;
                isCallConnected = false;
                callStartTime = 0;
                loadData();
            } else if (newIsIncoming != isIncoming) {
                // 同一session，通话进行中，方向变了（边界情况）
                isIncoming = newIsIncoming;
                updateUIForStatus();
            } else {
                // 同一session，同一方向（如从悬浮窗恢复）
                isIncoming = newIsIncoming;
            }
        }
    }

    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void showFloatingWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            android.util.Log.w("CallActivity", "No overlay permission, cannot show floating window");
            return;
        }

        if (isFloating) return;
        
        Intent serviceIntent = new Intent(this, CallForegroundService.class);
        serviceIntent.putExtra("title", currentCharacter != null ? currentCharacter.name : "正在通话中");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                startForegroundService(serviceIntent);
            } catch (Exception e) {
                android.util.Log.e("CallActivity", "Failed to start foreground service", e);
                startService(serviceIntent);
            }
        } else {
            startService(serviceIntent);
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_call, null);

        tvFloatingTime = floatingView.findViewById(R.id.tvFloatingTime);
        tvFloatingStatus = floatingView.findViewById(R.id.tvFloatingStatus);
        ivFloatingAvatar = floatingView.findViewById(R.id.ivFloatingAvatar);

        if (currentCharacter != null && !TextUtils.isEmpty(currentCharacter.avatarPath)) {
            if (!isFinishing() && !isDestroyed()) {
                Glide.with(this)
                    .load(currentCharacter.avatarPath)
                    .circleCrop()
                    .into(ivFloatingAvatar);
            }
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : 
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 100;

        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private boolean isClick = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isClick = true;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(event.getRawX() - initialTouchX) > 10 || 
                            Math.abs(event.getRawY() - initialTouchY) > 10) {
                            isClick = false;
                        }
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (isClick) {
                            Intent intent = new Intent(CallActivity.this, CallActivity.class);
                            intent.putExtra("session_id", sessionId);
                            intent.putExtra("is_incoming", isIncoming);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            startActivity(intent);
                            hideFloatingWindow();
                        }
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(floatingView, params);
        isFloating = true;
        updateFloatingWindowUI();
    }

    private void hideFloatingWindow() {
        if (isFloating && floatingView != null && windowManager != null) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception e) {
                e.printStackTrace();
            }
            floatingView = null;
            isFloating = false;
        }
    }

    private void updateFloatingWindowUI() {
        if (!isFloating || floatingView == null) return;

        runOnUiThread(() -> {
            if (isCallConnected && !isCallEnded) {
                if (isAiSpeakingOrThinking) {
                    tvFloatingStatus.setText(tvCallStatus.getText().toString());
                } else {
                    tvFloatingStatus.setText("通话中");
                }

                long durationSeconds = (System.currentTimeMillis() - callStartTime) / 1000;
                long m = durationSeconds / 60;
                long s = durationSeconds % 60;
                tvFloatingTime.setText(String.format(java.util.Locale.getDefault(), "%02d:%02d", m, s));
            } else if (!isCallConnected && !isCallEnded && isIncoming) {
                tvFloatingStatus.setText("等待接听...");
                tvFloatingTime.setText("");
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.yoyo.jingxi.utils.ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        instance = this;
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        setContentView(R.layout.activity_call);

        // 通话界面强制黑色状态栏
        getWindow().setStatusBarColor(android.graphics.Color.BLACK);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(
                    getWindow().getDecorView().getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        checkOverlayPermission();

        sessionId = getIntent().getIntExtra("session_id", -1);
        isIncoming = getIntent().getBooleanExtra("is_incoming", false);
        initialMessage = getIntent().getStringExtra("initial_message");

        db = AppDatabase.getDatabase(this);
        aiManager = new OpenAIManager();

        initViews();

        loadData();

        // 全屏来电也需要铃声和震动（通知路径由 sendIncomingCallNotification 负责启动）
        if (isIncoming && !isCallConnected && !isCallEnded
            && !com.yoyo.jingxi.utils.CallIncomingNotificationHelper.isRinging()) {
            com.yoyo.jingxi.utils.CallIncomingNotificationHelper.startRinging(this);
        }
    }

    private void initViews() {
        ivCallAvatar = findViewById(R.id.ivCallAvatar);
        tvCallName = findViewById(R.id.tvCallName);
        tvCallStatus = findViewById(R.id.tvCallStatus);
        tvCallDuration = findViewById(R.id.tvCallDuration);
        
        rvCallSubtitles = findViewById(R.id.rvCallSubtitles);
        subtitleAdapter = new CallMessageAdapter();
        voiceGenerateHelper = new VoiceGenerateHelper(this, db, dbExecutor, mainHandler);
        subtitleAdapter.setVoiceGenerateHelper(voiceGenerateHelper);
        subtitleAdapter.setDb(db);
        subtitleAdapter.setDbExecutor(dbExecutor);
        if (currentCharacter != null) {
            subtitleAdapter.setCharacterId(currentCharacter.id);
        }
        rvCallSubtitles.setLayoutManager(new LinearLayoutManager(this));
        rvCallSubtitles.setAdapter(subtitleAdapter);
        
        llCallActions = findViewById(R.id.llCallActions);
        btnHangUp = findViewById(R.id.btnHangUp);
        btnAccept = findViewById(R.id.btnAccept);
        btnSpeak = findViewById(R.id.btnSpeak);
        tvHangUpText = findViewById(R.id.tvHangUpText);
        tvSpeakText = findViewById(R.id.tvSpeakText);
        ivSpeakIcon = findViewById(R.id.ivSpeakIcon);

        btnSpeaker = findViewById(R.id.btnSpeaker);
        ivSpeakerIcon = findViewById(R.id.ivSpeakerIcon);
        tvSpeakerText = findViewById(R.id.tvSpeakerText);
        
        llKeyboardInput = findViewById(R.id.llKeyboardInput);
        etCallInput = findViewById(R.id.etCallInput);
        btnCallSend = findViewById(R.id.btnCallSend);

        ImageView ivMinimize = findViewById(R.id.ivMinimize);
        ivMinimize.setOnClickListener(v -> minimizeToFloatingWindow());

        // 设置点击事件
        btnHangUp.setOnClickListener(v -> handleHangUp());
        btnAccept.setOnClickListener(v -> handleAccept());
        
        btnCallSend.setOnClickListener(v -> handleSendText());
        
        // 软键盘回车键和发送事件
        etCallInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND || 
                (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER && event.getAction() == android.view.KeyEvent.ACTION_DOWN)) {
                handleSendText();
                return true;
            }
            return false;
        });

        // 输入框获焦时自动滚动字幕到底部
        etCallInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                mainHandler.postDelayed(() -> {
                    if (rvCallSubtitles.getAdapter() != null && rvCallSubtitles.getAdapter().getItemCount() > 0) {
                        rvCallSubtitles.smoothScrollToPosition(rvCallSubtitles.getAdapter().getItemCount() - 1);
                    }
                }, 200);
            }
        });
        
        // 免提/听筒切换
        btnSpeaker.setOnClickListener(v -> toggleSpeaker());

        // 录音按钮事件 (改为点击开启/关闭麦克风)
        btnSpeak.setOnClickListener(v -> {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO}, 100);
                return;
            }
            toggleMicrophone();
        });
    }

    private void loadData() {
        dbExecutor.execute(() -> {
            if (sessionId == -1) {
                mainHandler.post(() -> {
                    Toast.makeText(this, "无效的会话 (id=-1)", Toast.LENGTH_SHORT).show();
                    finish();
                });
                return;
            }
            
            currentSession = db.chatSessionDao().getSessionById(sessionId);
            if (currentSession != null) {
                currentCharacter = db.characterDao().getCharacterById(currentSession.characterId);

                if (!TextUtils.isEmpty(currentSession.myPersonaName)) {
                    currentMyPersona = db.myPersonaDao().getMyPersonaByName(currentSession.myPersonaName);
                    if (currentMyPersona == null) {
                        currentMyPersona = new MyPersona();
                        currentMyPersona.name = currentSession.myPersonaName;
                    }
                }
                
                mainHandler.post(() -> {
                    if (currentCharacter != null) {
                        subtitleAdapter.setCharacterName(currentCharacter.name);
                        subtitleAdapter.setCharacterId(currentCharacter.id);
                    }
                    updateUIForStatus();
                    // 当通过 onNewIntent 进入来电时，也需要开始响铃
                    // （onCreate 里已经做了这个检查，但 onNewIntent → loadData 不会触发）
                    if (isIncoming && !isCallConnected && !isCallEnded
                            && !com.yoyo.jingxi.utils.CallIncomingNotificationHelper.isRinging()) {
                        com.yoyo.jingxi.utils.CallIncomingNotificationHelper.startRinging(CallActivity.this);
                    }
                });
                
                if (!isIncoming) {
                    // 主动拨打，请求AI是否接听
                    requestCallDecision();
                } else {
                    // 来电，直接等待用户选择接听
                }
            } else {
                mainHandler.post(() -> {
                    Toast.makeText(this, "无效的会话", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    private void updateUIForStatus() {
        updateUIForStatus(null);
    }

    private void minimizeToFloatingWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                Toast.makeText(this, "请开启悬浮窗权限后重试", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }
        showFloatingWindow();
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("session_id", sessionId);
        if (currentCharacter != null) {
            intent.putExtra("friend_name", currentCharacter.name);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    private void updateUIForStatus(String finalStatus) {
        if (currentCharacter != null) {
            tvCallName.setText(currentCharacter.name);
            if (!TextUtils.isEmpty(currentCharacter.avatarPath)) {
                if (!isFinishing() && !isDestroyed()) {
                    Glide.with(this)
                         .load(currentCharacter.avatarPath)
                         .circleCrop()
                         .placeholder(R.drawable.ic_launcher_round)
                         .into(ivCallAvatar);
                }
            }
        }
        
        if (isCallEnded) {
            if (finalStatus != null) {
                tvCallStatus.setText(finalStatus);
            } else if (!tvCallStatus.getText().toString().contains("拒绝") && !tvCallStatus.getText().toString().contains("失败")) {
                tvCallStatus.setText("通话已结束");
            }
            tvCallStatus.setVisibility(View.VISIBLE);
            tvCallDuration.setVisibility(View.GONE);
            btnAccept.setVisibility(View.GONE);
            btnSpeak.setVisibility(View.GONE);
            btnSpeaker.setVisibility(View.GONE);
            llKeyboardInput.setVisibility(View.GONE);
            tvHangUpText.setText("关闭");
            return;
        }

        if (isCallConnected) {
            if (!tvCallStatus.getText().toString().contains("对方正在讲话") && !tvCallStatus.getText().toString().contains("思考中")) {
                tvCallStatus.setVisibility(View.GONE);
            }
            tvCallDuration.setVisibility(View.VISIBLE);
            btnAccept.setVisibility(View.GONE);
            btnSpeak.setVisibility(View.VISIBLE);
            btnSpeaker.setVisibility(View.VISIBLE);
            llKeyboardInput.setVisibility(!isMicrophoneEnabled ? View.VISIBLE : View.GONE);
            tvHangUpText.setText("挂断");
            
            // 同步麦克风状态 UI
            updateMicrophoneUI();
        } else {
            tvCallDuration.setVisibility(View.GONE);
            btnSpeak.setVisibility(View.GONE);
            btnSpeaker.setVisibility(View.GONE);
            llKeyboardInput.setVisibility(View.GONE);

            if (isIncoming) {
                tvCallStatus.setText("等待接听...");
                btnAccept.setVisibility(View.VISIBLE);
                tvHangUpText.setText("拒绝");
            } else {
                tvCallStatus.setText("正在呼叫...");
                btnAccept.setVisibility(View.GONE);
                tvHangUpText.setText("取消");
            }
        }
    }

    private void requestCallDecision() {
        dbExecutor.execute(() -> {
            String apiKey = SpUtils.getString("OPENAI_API_KEY", "");
            String endpoint = SpUtils.getString("API_ENDPOINT", "https://api.openai.com/");
            String myName = currentMyPersona != null ? currentMyPersona.name : SpUtils.getString("MY_NAME", "我");
            String scheduleContent = SpUtils.getString("SCHEDULE_CONTENT_" + currentCharacter.id, "");

            int historyRounds = SpUtils.getInt("SETTING_HISTORY_ROUNDS", 80);
            List<Message> history = db.messageDao().getRecentMessagesBySessionIdSync(sessionId, historyRounds * 2);
            Collections.reverse(history);

            String relationshipContent = "";
            if (SpUtils.getBoolean("RELATIONSHIP_NETWORK_ENABLED", true)) {
                List<com.yoyo.jingxi.data.entity.RelationshipNode> nodes = db.relationshipNodeDao().getAllNodesSync();
                List<com.yoyo.jingxi.data.entity.RelationshipEdge> edges = db.relationshipEdgeDao().getAllEdgesSync();
                if (nodes != null && !nodes.isEmpty() && edges != null && !edges.isEmpty()) {
                    StringBuilder relBuilder = new StringBuilder();
                    for (com.yoyo.jingxi.data.entity.RelationshipEdge edge : edges) {
                        com.yoyo.jingxi.data.entity.RelationshipNode source = null;
                        com.yoyo.jingxi.data.entity.RelationshipNode target = null;
                        for (com.yoyo.jingxi.data.entity.RelationshipNode n : nodes) {
                if (n.id.equals(edge.sourceNodeId)) source = n;
                if (n.id.equals(edge.targetNodeId)) target = n;
                        }
                        if (source != null && target != null) {
                            relBuilder.append("- ").append(source.name).append(" -> ").append(target.name).append(" : ").append(edge.relation).append("\n");
                        }
                    }
                    relationshipContent = relBuilder.toString();
                }
            }

            if (TextUtils.isEmpty(apiKey)) {
                mainHandler.post(() -> {
                    Toast.makeText(this, "请配置 OpenAI API KEY", Toast.LENGTH_SHORT).show();
                    handleSystemReject();
                });
                return;
            }

            if (!endpoint.endsWith("/")) {
                endpoint += "/";
            }
            String finalUrl = endpoint + "v1/chat/completions";

            OpenAiRequest request = aiManager.buildCallAnswerDecisionRequest(currentCharacter.persona, myName, scheduleContent, history, relationshipContent);

            aiManager.getApi().createChatCompletion(finalUrl, "Bearer " + apiKey, request).enqueue(new Callback<OpenAiResponse>() {
                @Override
                public void onResponse(Call<OpenAiResponse> call, Response<OpenAiResponse> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().choices != null && !response.body().choices.isEmpty() && response.body().choices.get(0) != null && response.body().choices.get(0).message != null && response.body().choices.get(0).message.content != null) {
                        String rawContent = response.body().choices.get(0).message.content;
                        
                        // Clean up markdown formatting if present
                        if (rawContent != null) {
                            rawContent = rawContent.trim();
                            if (rawContent.contains("```json")) {
                                int start = rawContent.indexOf("```json") + 7;
                                int end = rawContent.lastIndexOf("```");
                                if (end > start) {
                                    rawContent = rawContent.substring(start, end).trim();
                                }
                            } else if (rawContent.contains("```")) {
                                int start = rawContent.indexOf("```") + 3;
                                int end = rawContent.lastIndexOf("```");
                                if (end > start) {
                                    rawContent = rawContent.substring(start, end).trim();
                                }
                            }
                        }
                        
                        try {
                            org.json.JSONObject json = new org.json.JSONObject(rawContent);
                            boolean accept = json.optBoolean("accept", true);
                            String reason = json.optString("reason", "");
                            
                            mainHandler.post(() -> {
                                if (accept && !isCallEnded) {
                                    startCall();
                                } else if (!isCallEnded) {
                                    String rejectMsg = "对方拒绝了您的电话\n" + (TextUtils.isEmpty(reason) ? "" : "(" + reason + ")");
                                    tvCallStatus.setText(rejectMsg);
                                    insertSystemMessageBubble("未接通的电话" + (TextUtils.isEmpty(reason) ? "" : " (拒接原因: " + reason + ")"));
                                    endCall(rejectMsg);
                                }
                            });
                        } catch (Exception e) {
                            mainHandler.post(() -> {
                                Toast.makeText(CallActivity.this, "解析失败，假装接通", Toast.LENGTH_SHORT).show();
                                if (!isCallEnded) startCall(); // 解析失败默认接通
                            });
                        }
                    } else {
                        String errBody = "";
                        try {
                            if (response.errorBody() != null) {
                                errBody = response.errorBody().string();
                            }
                        } catch (Exception e) {}
                        final String errorMsg = "错误代码: " + response.code() + " " + errBody;
                        android.util.Log.e("CallActivity", "Call Answer API Error: " + errorMsg);
                        mainHandler.post(() -> {
                            if (!isCallEnded) {
                                Toast.makeText(CallActivity.this, "呼叫失败, " + response.code(), Toast.LENGTH_LONG).show();
                                handleSystemReject();
                            }
                        });
                    }
                }

                @Override
                public void onFailure(Call<OpenAiResponse> call, Throwable t) {
                    final String errorMsg = t.getMessage() != null ? t.getMessage() : "未知网络错误";
                    mainHandler.post(() -> {
                        if (!isCallEnded) {
                            Toast.makeText(CallActivity.this, "连接失败: " + errorMsg, Toast.LENGTH_LONG).show();
                            handleSystemReject();
                        }
                    });
                }
            });
        });
    }

    private void handleSystemReject() {
        if (!isCallEnded) {
            tvCallStatus.setText("呼叫失败");
            insertSystemMessageBubble("未接通的电话");
            insertUnansweredBubble();
            endCall("呼叫失败");
        }
    }

    private void insertUnansweredBubble() {
        if (currentCharacter == null) return;
        dbExecutor.execute(() -> {
            Message msg = new Message();
            msg.sessionId = sessionId;
            msg.characterId = currentCharacter.id;
            msg.isFromUser = !isIncoming;
            msg.type = 0;
            msg.content = "[未接通的电话]";
            msg.timestamp = System.currentTimeMillis();
            db.messageDao().insert(msg);
        });
    }

    private void handleAccept() {
        startCall();
        // 作为被动方接听，让对方先开口
        if (isIncoming && !TextUtils.isEmpty(initialMessage)) {
            processAiVoice(initialMessage, false, null); // 初始消息暂无 emotion
        } else {
            requestAiReply();
        }
    }

    private void handleHangUp() {
        if (isCallEnded) {
            finish();
        } else {
            if (!isCallConnected) {
                insertSystemMessageBubble(isIncoming ? "拒绝了来电" : "取消了呼叫");
                insertUnansweredBubble();
            }
            endCall();
        }
    }

    private void startCall() {
        // 停止来电铃声和震动（用户已接听）
        com.yoyo.jingxi.utils.CallIncomingNotificationHelper.stopRinging(this);

        isCallConnected = true;
        callStartTime = System.currentTimeMillis();

        // 设置音频模式为通话模式
        if (audioManager != null) {
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        }

        // 默认听筒模式，启动接近传感器熄屏
        if (!isSpeakerEnabled) {
            acquireProximityWakeLock();
        }

        requestCallAudioFocus();

        dbExecutor.execute(() -> {
            CallRecord record = new CallRecord();
            record.sessionId = sessionId;
            record.characterId = currentCharacter.id;
            record.startTime = callStartTime;
            record.duration = 0; // 结束后更新
            record.summary = ""; // 结束后更新
            record.initiator = isIncoming ? 1 : 0;  // 0=用户发起, 1=AI发起
            record.isMissed = false;                  // 已接通，不算未接
            currentCallRecordId = db.callRecordDao().insert(record);
        });

        updateUIForStatus();
        mainHandler.post(durationRunnable);
    }

    private void endCall() {
        endCall(null);
    }

    private void endCall(String finalStatus) {
        isCallEnded = true;
        isCallConnected = false;
        hideFloatingWindow();
        // 通话结束，停止前台服务
        try {
            Intent serviceIntent = new Intent(this, CallForegroundService.class);
            stopService(serviceIntent);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 停止来电铃声和震动（通话已结束）
        com.yoyo.jingxi.utils.CallIncomingNotificationHelper.stopRinging(this);
        abandonCallAudioFocus();
        releaseProximityWakeLock();

        voiceTaskQueue.clear();
        isProcessingVoice = false;
        isSpeakerEnabled = false;

        if (mediaPlayer != null) {
            android.media.MediaPlayer playerToRelease = mediaPlayer;
            mediaPlayer = null;
            mainHandler.postDelayed(() -> {
                try {
                    playerToRelease.release();
                } catch (Exception e) {}
            }, 300);
        }

        updateUIForStatus(finalStatus);
        
        if (currentCallRecordId != -1) {
            // 更新时长并请求总结
            dbExecutor.execute(() -> {
                long durationSeconds = (System.currentTimeMillis() - callStartTime) / 1000;
                CallRecord record = db.callRecordDao().getRecordByIdSync((int)currentCallRecordId);
                if (record != null) {
                    record.endTime = System.currentTimeMillis();
                    record.duration = (int) durationSeconds;
                    db.callRecordDao().update(record);
                    
                    if (!tempCallMessages.isEmpty() && durationSeconds > 5) {
                        requestSummaryAndUpdateRecord(record);
                    }
                }
                
                // 将通话记录作为一个系统消息插入到当前聊天面板
                Message msg = new Message();
                msg.sessionId = sessionId;
                msg.characterId = currentCharacter.id;
                msg.isFromUser = false;
                msg.type = 99; // 使用99显示系统消息
                msg.content = "通话时长 " + String.format("%02d:%02d", durationSeconds / 60, durationSeconds % 60);
                msg.timestamp = System.currentTimeMillis();
                db.messageDao().insert(msg);
            });
        }

    }
    
    private void requestCallAudioFocus() {
        if (audioManager == null || hasAudioFocus) return;

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();

        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(audioAttributes)
                    .setOnAudioFocusChangeListener(focusChange -> {
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                            hasAudioFocus = false;
                        }
                    })
                    .build();
            result = audioManager.requestAudioFocus(focusRequest);
        } else {
            result = audioManager.requestAudioFocus(null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN);
        }

        hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
    }

    private void abandonCallAudioFocus() {
        if (audioManager == null || !hasAudioFocus) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(audioAttributes)
                    .setOnAudioFocusChangeListener(focusChange -> {})
                    .build();
            audioManager.abandonAudioFocusRequest(focusRequest);
        } else {
            audioManager.abandonAudioFocus(null);
        }

        hasAudioFocus = false;
    }

    private void insertSystemMessageBubble(String text) {
        dbExecutor.execute(() -> {
            Message msg = new Message();
            msg.sessionId = sessionId;
            msg.characterId = currentCharacter.id;
            msg.isFromUser = false;
            msg.type = 99;
            msg.content = text;
            msg.timestamp = System.currentTimeMillis();
            db.messageDao().insert(msg);
        });
    }

    private void requestSummaryAndUpdateRecord(CallRecord record) {
        String apiKey = SpUtils.getString("OPENAI_API_KEY", "");
        String endpoint = SpUtils.getString("API_ENDPOINT", "https://api.openai.com/");
        if (TextUtils.isEmpty(apiKey)) return;
        if (!endpoint.endsWith("/")) endpoint += "/";

        String myName = currentMyPersona != null ? currentMyPersona.name : SpUtils.getString("MY_NAME", "我");
        OpenAiRequest request = aiManager.buildCallSummaryRequest(currentCharacter.persona, myName, tempCallMessages);

        try {
            Response<OpenAiResponse> response = aiManager.getApi().createChatCompletion(endpoint + "v1/chat/completions", "Bearer " + apiKey, request).execute();
            if (response.isSuccessful() && response.body() != null && response.body().choices != null && !response.body().choices.isEmpty() && response.body().choices.get(0) != null && response.body().choices.get(0).message != null && response.body().choices.get(0).message.content != null) {
                String summary = response.body().choices.get(0).message.content.trim();
                record.summary = summary;
                db.callRecordDao().update(record);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleSendText() {
        String content = etCallInput.getText().toString().trim();
        if (TextUtils.isEmpty(content)) {
            // 如果为空且在通话中，可以让AI说话
            if (isCallConnected) requestAiReply();
            return;
        }

        saveCallMessage(content, true, null);
        etCallInput.setText("");
        
        // 触发 AI 回复
        requestAiReply();
    }

    private void saveCallMessage(String text, boolean isFromUser, String voiceUrl) {
        saveCallMessage(text, isFromUser, voiceUrl, false);
    }

    private void saveCallMessage(String text, boolean isFromUser, String voiceUrl, boolean showInChat) {
        if (currentCallRecordId == -1) return;
        
        long currentTime = System.currentTimeMillis();
        CallMessage msg = new CallMessage();
        msg.callId = (int) currentCallRecordId;
        msg.isFromUser = isFromUser;
        msg.content = text;
        msg.voiceUrl = voiceUrl;
        msg.timestamp = currentTime;
        
        tempCallMessages.add(msg);
        
        mainHandler.post(() -> {
            subtitleAdapter.addMessage(msg);
            rvCallSubtitles.scrollToPosition(subtitleAdapter.getItemCount() - 1);
        });
        
        dbExecutor.execute(() -> {
            db.callMessageDao().insert(msg);
            
            // Mirror save to Message table
            if (currentCharacter != null) {
                Message mirrorMsg = new Message();
                mirrorMsg.sessionId = sessionId;
                mirrorMsg.characterId = currentCharacter.id;
                mirrorMsg.isFromUser = isFromUser;
                mirrorMsg.content = text;
                // type 100 is for invisible context, type 0 for regular chat text message
                mirrorMsg.type = showInChat ? 0 : 100;
                mirrorMsg.timestamp = currentTime;
                db.messageDao().insert(mirrorMsg);
            }
        });
    }

    // --- VAD (Voice Activity Detection) 自动录音控制 ---
    
    private boolean isMicrophoneEnabled = false;
    private android.media.AudioRecord audioRecord;
    private Thread vadThread;
    private boolean isVadRunning = false;
    private boolean isAiSpeakingOrThinking = false;
    
    private void toggleMicrophone() {
        if (isAiSpeakingOrThinking) {
            Toast.makeText(this, "请等待对方说完", Toast.LENGTH_SHORT).show();
            return;
        }
        
        isMicrophoneEnabled = !isMicrophoneEnabled;
        updateMicrophoneUI();
        
        if (isMicrophoneEnabled) {
            startVadRecording();
        } else {
            stopVadRecording(false);
        }
    }
    
    private void updateMicrophoneUI() {
        if (isMicrophoneEnabled && !isAiSpeakingOrThinking) {
            ivSpeakIcon.setImageResource(R.drawable.ic_call_mic);
            tvSpeakText.setText("麦克风已开");
            ivSpeakIcon.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#34C759"))); // 绿色
            if (isCallConnected) llKeyboardInput.setVisibility(View.GONE);
        } else {
            ivSpeakIcon.setImageResource(R.drawable.ic_call_mic_off);
            tvSpeakText.setText("麦克风已关");
            ivSpeakIcon.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#888888"))); // 灰色
            if (isCallConnected) llKeyboardInput.setVisibility(View.VISIBLE);
        }
    }

    private void toggleSpeaker() {
        if (audioManager == null) return;

        isSpeakerEnabled = !isSpeakerEnabled;
        audioManager.setSpeakerphoneOn(isSpeakerEnabled);

        if (isSpeakerEnabled) {
            // 免提模式
            ivSpeakerIcon.setImageResource(R.drawable.ic_speaker);
            ivSpeakerIcon.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#34C759"))); // 绿色
            tvSpeakerText.setText("免提");
            releaseProximityWakeLock();
        } else {
            // 听筒模式
            ivSpeakerIcon.setImageResource(R.drawable.ic_handset);
            ivSpeakerIcon.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#888888"))); // 灰色
            tvSpeakerText.setText("听筒");
            acquireProximityWakeLock();
        }
    }

    @SuppressWarnings("deprecation")
    private void acquireProximityWakeLock() {
        if (powerManager == null) return;
        // 仅在通话已接通且听筒模式时启用
        if (!isCallConnected || isSpeakerEnabled) return;
        if (proximityWakeLock != null && proximityWakeLock.isHeld()) return;

        try {
            proximityWakeLock = powerManager.newWakeLock(
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                    "Jingxi:ProximityWakeLock");
            proximityWakeLock.acquire();
        } catch (Exception e) {
            android.util.Log.e("CallActivity", "Failed to acquire proximity wake lock", e);
        }
    }

    private void releaseProximityWakeLock() {
        if (proximityWakeLock != null) {
            try {
                if (proximityWakeLock.isHeld()) {
                    proximityWakeLock.release();
                }
            } catch (Exception e) {
                android.util.Log.e("CallActivity", "Failed to release proximity wake lock", e);
            }
            proximityWakeLock = null;
        }
    }

    public void sendTextFromChat(String text, boolean showInChat) {
        if (!isCallConnected || isCallEnded || TextUtils.isEmpty(text)) return;
        saveCallMessage(text, true, null, showInChat);
    }

    public void requestAiReplyFromChat() {
        if (!isCallConnected || isCallEnded) return;
        requestAiReply();
    }

    private void startVadRecording() {
        if (isRecording || isVadRunning) return;
        
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }

        String fileName = "CALL_VOICE_" + System.currentTimeMillis() + ".mp3";
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
            tvCallStatus.setVisibility(View.VISIBLE);
            tvCallStatus.setText("正在聆听...");
            
            startVadThread();
        } catch (IOException e) {
            e.printStackTrace();
            currentVoiceFilePath = null;
            tvCallStatus.setVisibility(View.GONE);
        }
    }
    
    private void startVadThread() {
        int sampleRate = 16000;
        int channelConfig = android.media.AudioFormat.CHANNEL_IN_MONO;
        int audioFormat = android.media.AudioFormat.ENCODING_PCM_16BIT;
        int minBufferSize = android.media.AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }
        audioRecord = new android.media.AudioRecord(android.media.MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, minBufferSize);
        
        isVadRunning = true;
        vadThread = new Thread(() -> {
            audioRecord.startRecording();
            short[] buffer = new short[minBufferSize];
            
            long silenceStartTime = 0;
            boolean hasSpoken = false; // 是否已经说过话
            final int SILENCE_THRESHOLD = 800; // 振幅阈值，根据设备调整
            final long SILENCE_DURATION_MS = 3000; // 静音多长时间认为结束
            
            while (isVadRunning && !Thread.interrupted()) {
                int readSize = audioRecord.read(buffer, 0, buffer.length);
                if (readSize > 0) {
                    double sum = 0;
                    for (int i = 0; i < readSize; i++) {
                        sum += buffer[i] * buffer[i];
                    }
                    double amplitude = Math.sqrt(sum / readSize);
                    
                    if (amplitude > SILENCE_THRESHOLD) {
                        hasSpoken = true;
                        silenceStartTime = 0;
                        mainHandler.post(() -> {
                            if (tvCallStatus.getVisibility() == View.VISIBLE && "正在聆听...".equals(tvCallStatus.getText().toString())) {
                                tvCallStatus.setText("正在录音...");
                            }
                        });
                    } else if (hasSpoken) {
                        if (silenceStartTime == 0) {
                            silenceStartTime = System.currentTimeMillis();
                        } else if (System.currentTimeMillis() - silenceStartTime > SILENCE_DURATION_MS) {
                            // 说话结束
                            mainHandler.post(() -> {
                                stopVadRecording(true);
                            });
                            break;
                        }
                    }
                }
            }
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception e) {}
        });
        vadThread.start();
    }

    private void stopVadRecording(boolean processVoice) {
        isVadRunning = false;
        if (vadThread != null) {
            vadThread.interrupt();
            vadThread = null;
        }
        
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
            
            if (processVoice && currentVoiceFilePath != null) {
                processVoiceToText(currentVoiceFilePath);
            }
        }
        
        if (!isAiSpeakingOrThinking) {
            tvCallStatus.setVisibility(View.GONE);
        }
    }

    private void processVoiceToText(String filePath) {
        File audioFile = new File(filePath);
        if (!audioFile.exists()) {
            tvCallStatus.setText("录音文件不存在");
            mainHandler.postDelayed(() -> {
                if (isMicrophoneEnabled && !isAiSpeakingOrThinking) {
                    startVadRecording();
                }
            }, 1000);
            return;
        }

        tvCallStatus.setVisibility(View.VISIBLE);
        tvCallStatus.setText("识别中...");

        SttManager.getInstance().recognize(audioFile, new SttProvider.Callback() {
            @Override
            public void onResult(String recognizedText) {
                mainHandler.post(() -> {
                    tvCallStatus.setVisibility(View.GONE);
                    if (!TextUtils.isEmpty(recognizedText)) {
                        saveCallMessage(recognizedText, true, currentVoiceFilePath);
                        requestAiReply();
                    } else {
                        if (isMicrophoneEnabled && !isAiSpeakingOrThinking) {
                            startVadRecording();
                        }
                    }
                });
            }

            @Override
            public void onError(int code, String message, boolean canFallback) {
                mainHandler.post(() -> {
                    tvCallStatus.setText("识别失败");
                    mainHandler.postDelayed(() -> {
                        if (isMicrophoneEnabled && !isAiSpeakingOrThinking) {
                            startVadRecording();
                        } else {
                            if (isCallConnected) tvCallStatus.setVisibility(View.GONE);
                        }
                    }, 1000);
                });
            }
        });
    }

    private void requestAiReply() {
        if (currentCharacter == null || isCallEnded) return;

        // 暂停录音，标记AI思考
        isAiSpeakingOrThinking = true;
        if (isRecording || isVadRunning) {
            stopVadRecording(false);
        }
        updateMicrophoneUI();

        tvCallStatus.setVisibility(View.VISIBLE);
        tvCallStatus.setText("思考中...");

        dbExecutor.execute(() -> {
            try {
                String apiKey = SpUtils.getString("OPENAI_API_KEY", "");
                String endpoint = SpUtils.getString("API_ENDPOINT", "https://api.openai.com/");
                String model = SpUtils.getString("API_MODEL", "gpt-4o-mini");

                if (TextUtils.isEmpty(apiKey)) {
                    mainHandler.post(() -> {
                        isAiSpeakingOrThinking = false;
                        updateMicrophoneUI();
                        tvCallStatus.setVisibility(View.GONE);
                        Toast.makeText(CallActivity.this, "请配置 OpenAI API KEY", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                if (!endpoint.endsWith("/")) endpoint += "/";
                String finalUrl = endpoint + "v1/chat/completions";

                // 获取最近的聊天历史记录供电话判断接听时作为上下文参考
                int historyRounds = SpUtils.getInt("SETTING_HISTORY_ROUNDS", 80);
                List<Message> history = db.messageDao().getRecentMessagesBySessionIdSync(sessionId, historyRounds * 2);
                Collections.reverse(history);

                String relationshipContent = "";
                if (SpUtils.getBoolean("RELATIONSHIP_NETWORK_ENABLED", true)) {
                    List<com.yoyo.jingxi.data.entity.RelationshipNode> nodes = db.relationshipNodeDao().getAllNodesSync();
                    List<com.yoyo.jingxi.data.entity.RelationshipEdge> edges = db.relationshipEdgeDao().getAllEdgesSync();
                    if (nodes != null && !nodes.isEmpty() && edges != null && !edges.isEmpty()) {
                        StringBuilder relBuilder = new StringBuilder();
                        for (com.yoyo.jingxi.data.entity.RelationshipEdge edge : edges) {
                            com.yoyo.jingxi.data.entity.RelationshipNode source = null;
                            com.yoyo.jingxi.data.entity.RelationshipNode target = null;
                            for (com.yoyo.jingxi.data.entity.RelationshipNode n : nodes) {
                                if (n.id.equals(edge.sourceNodeId)) source = n;
                                if (n.id.equals(edge.targetNodeId)) target = n;
                            }
                            if (source != null && target != null) {
                                relBuilder.append("- ").append(source.name).append(" -> ").append(target.name).append(" : ").append(edge.relation).append("\n");
                            }
                        }
                        relationshipContent = relBuilder.toString();
                    }
                }

                String myName = currentMyPersona != null ? currentMyPersona.name : SpUtils.getString("MY_NAME", "我");

                int memoryCallCount = SpUtils.getInt("SETTING_MEMORY_CALL_COUNT", 20);
                List<com.yoyo.jingxi.data.entity.Memory> importantMemories = db.memoryDao().getImportantMemoriesSyncAll(currentCharacter.id);
                List<com.yoyo.jingxi.data.entity.Memory> normalMemories;
                if (memoryCallCount > 0) {
                    normalMemories = db.memoryDao().getNormalMemoriesSyncAll(currentCharacter.id, memoryCallCount);
                } else {
                    normalMemories = db.memoryDao().getAllNormalMemoriesSyncAll(currentCharacter.id);
                }
                String scheduleContent = SpUtils.getString("SCHEDULE_CONTENT_" + currentCharacter.id, "");
                
                // 获取启用的世界书
                List<com.yoyo.jingxi.data.entity.WorldbookEntry> allEnabled = db.worldbookDao().getAllEnabledEntriesSync();
                String unselectedStr = SpUtils.getString("CHAT_WORLDBOOK_UNSELECTED_" + sessionId, "");
                List<String> unselectedList = java.util.Arrays.asList(unselectedStr.split(","));
        List<com.yoyo.jingxi.data.entity.WorldbookEntry> worldbookEntries = new ArrayList<>();
        for (com.yoyo.jingxi.data.entity.WorldbookEntry entry : allEnabled) {
            if (!unselectedList.contains(String.valueOf(entry.id))) {
                worldbookEntries.add(entry);
            }
        }

                // 获取近期动态
                String momentsContent = "";
                try {
                    List<com.yoyo.jingxi.data.entity.Moment> recentMoments = db.momentDao().getRecentMomentsSync(5);
                    if (recentMoments != null && !recentMoments.isEmpty()) {
                        StringBuilder momentsBuilder = new StringBuilder();
                        momentsBuilder.append("最近的朋友圈动态（供参考，可进行互动，但不强制要求）：\n");
                        for (com.yoyo.jingxi.data.entity.Moment m : recentMoments) {
                            momentsBuilder.append("- ").append(m.publisherName).append(": ").append(m.content).append(" (动态ID: ").append(m.id).append(")\n");
                        }
                        momentsContent = momentsBuilder.toString();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                // 强制使用电话模式
                String myPersonaDesc = currentMyPersona != null ? currentMyPersona.persona : "";
                int maxAiMessages = SpUtils.getInt("CHAT_MAX_AI_MESSAGES_" + sessionId, 5);
                OpenAiRequest request = aiManager.buildRequest(currentCharacter.persona, history, myName, myPersonaDesc, model, importantMemories, normalMemories, new ArrayList<>(), scheduleContent, worldbookEntries, new ArrayList<>(), true, relationshipContent, maxAiMessages, momentsContent);

                Response<OpenAiResponse> response = aiManager.getApi().createChatCompletion(finalUrl, "Bearer " + apiKey, request).execute();
                
                if (response.isSuccessful() && response.body() != null && response.body().choices != null && !response.body().choices.isEmpty() && response.body().choices.get(0) != null && response.body().choices.get(0).message != null && response.body().choices.get(0).message.content != null) {
                    String rawContent = response.body().choices.get(0).message.content;
                    handleAiReplies(rawContent);
                } else {
                    String errBody = "";
                    try {
                        if (response.errorBody() != null) {
                            errBody = response.errorBody().string();
                        }
                    } catch (Exception ex) {}
                    android.util.Log.e("CallActivity", "AI Reply API Error: " + response.code() + " " + errBody);
                    mainHandler.post(() -> {
                        isAiSpeakingOrThinking = false;
                        updateMicrophoneUI();
                        Toast.makeText(CallActivity.this, "对方暂时无法回应 (错误 " + response.code() + ")", Toast.LENGTH_SHORT).show();
                        if (isCallConnected) tvCallStatus.setVisibility(View.GONE);
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    isAiSpeakingOrThinking = false;
                    updateMicrophoneUI();
                    Toast.makeText(CallActivity.this, "连接超时，请重试", Toast.LENGTH_SHORT).show();
                    if (isCallConnected) tvCallStatus.setVisibility(View.GONE);
                });
            }
        });
    }

    private void handleAiReplies(String rawContent) {
        List<OpenAIManager.ReplyItem> replies = aiManager.parseMultiReplies(rawContent);
        
        dbExecutor.execute(() -> {
            boolean hasSpoken = false;
            for (OpenAIManager.ReplyItem item : replies) {
                if ("hangup".equalsIgnoreCase(item.type)) {
                    hasSpoken = true;
                    if (!TextUtils.isEmpty(item.content)) {
                        processAiVoice(item.content, true, item.emotion); // 播放最后一句后挂断
                    } else {
                        mainHandler.post(this::endCall);
                    }
                    break; // 停止处理后续消息
                } else if ("voice".equalsIgnoreCase(item.type) || "text".equalsIgnoreCase(item.type)) {
                    hasSpoken = true;
                    processAiVoice(item.content, false, item.emotion);
                } else if ("memo".equalsIgnoreCase(item.type)) {
                    com.yoyo.jingxi.data.entity.Memo memo = new com.yoyo.jingxi.data.entity.Memo();
                    memo.characterId = currentCharacter.id;
                    memo.content = item.content;
                    memo.targetDate = item.date != null ? item.date : "";
                    memo.status = item.status != null ? item.status : 2;
                    memo.createdAt = System.currentTimeMillis();
                    db.memoDao().insert(memo);
                }
            }
            
            // 如果AI没有说话回复，只是更新了记忆等，也要恢复麦克风
            if (!hasSpoken) {
                mainHandler.post(() -> {
                    isAiSpeakingOrThinking = false;
                    updateMicrophoneUI();
                    if (isMicrophoneEnabled && !isCallEnded) {
                        startVadRecording();
                    } else {
                        tvCallStatus.setVisibility(View.GONE);
                    }
                });
            }
        });
    }

    private void processAiVoice(String content, boolean hangupAfter, String emotion) {
        mainHandler.post(() -> {
            voiceTaskQueue.offer(new VoiceTask(content, hangupAfter, emotion));
            processNextVoiceTask();
        });
    }

    private synchronized void processNextVoiceTask() {
        if (isProcessingVoice || voiceTaskQueue.isEmpty() || isCallEnded) {
            return;
        }
        isProcessingVoice = true;
        VoiceTask task = voiceTaskQueue.poll();

        mainHandler.post(() -> {
            if (!isCallEnded) {
                tvCallStatus.setVisibility(View.VISIBLE);
                tvCallStatus.setText("对方正在讲话...");
            }
        });
        
        dbExecutor.execute(() -> {
            String voiceUrl = null;
            if (currentCharacter != null && !TextUtils.isEmpty(currentCharacter.voiceId)) {
                android.util.Log.d("CallActivity", "processNextVoiceTask: calling generateVoiceSync, voiceId=" + currentCharacter.voiceId);
                voiceUrl = voiceGenerateHelper.generateVoiceSync(currentCharacter, task.content, task.emotion);
            } else {
                android.util.Log.w("CallActivity", "processNextVoiceTask: skip TTS, currentCharacter=" + (currentCharacter != null ? "non-null" : "null") + " voiceId=" + (currentCharacter != null ? currentCharacter.voiceId : "N/A"));
            }

            final String finalVoiceUrl = voiceUrl;
            
            mainHandler.post(() -> {
                saveCallMessage(task.content, false, finalVoiceUrl);
                
                if (isCallEnded) {
                    isProcessingVoice = false;
                    return;
                }
                
                if (finalVoiceUrl != null) {
                    playAudio(finalVoiceUrl, task.hangupAfter);
                } else {
                    Toast.makeText(this, "对方说话: " + task.content, Toast.LENGTH_SHORT).show();
                    if (task.hangupAfter) {
                        mainHandler.postDelayed(this::endCall, 2000);
                    } else {
                        onAudioCompleted();
                    }
                }
            });
        });
    }

    private void onAudioCompleted() {
        isProcessingVoice = false;
        if (!voiceTaskQueue.isEmpty()) {
            processNextVoiceTask();
        } else {
            onAiSpeakingFinished();
        }
    }

    private void playAudio(String audioPath, boolean hangupAfter) {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }
            mediaPlayer = new android.media.MediaPlayer();
            if (isSpeakerEnabled) {
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            } else {
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);
            }
            mediaPlayer.setDataSource(audioPath);
            mediaPlayer.prepare();
            mediaPlayer.setOnPreparedListener(mp -> {
                mediaPlayer.start();
            });
            
            mediaPlayer.setOnCompletionListener(mp -> {
                if (hangupAfter) {
                    endCall();
                } else {
                    onAudioCompleted();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            if (hangupAfter) {
                endCall();
            } else {
                onAudioCompleted();
            }
        }
    }
    
    private void onAiSpeakingFinished() {
        if (isCallEnded) return;
        isAiSpeakingOrThinking = false;
        tvCallStatus.setVisibility(View.GONE);
        updateMicrophoneUI();
        updateFloatingWindowUI();
        
        if (isMicrophoneEnabled) {
            startVadRecording();
        }
    }

    @Override
    public void onBackPressed() {
        if (!isCallEnded) {
            minimizeToFloatingWindow();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (instance == this) {
            instance = null;
        }
        mainHandler.removeCallbacks(durationRunnable);
        if (isCallConnected && !isCallEnded) {
            endCall();
        }
        if (dbExecutor != null) {
            dbExecutor.shutdown();
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        stopVadRecording(false);
        releaseProximityWakeLock();
    }
}
