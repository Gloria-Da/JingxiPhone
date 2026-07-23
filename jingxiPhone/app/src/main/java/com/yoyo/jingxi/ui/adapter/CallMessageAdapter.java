package com.yoyo.jingxi.ui.adapter;

import android.content.Context;
import android.media.MediaPlayer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.CallMessage;
import com.yoyo.jingxi.utils.VoiceGenerateHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

public class CallMessageAdapter extends RecyclerView.Adapter<CallMessageAdapter.ViewHolder> {

    private List<CallMessage> messages = new ArrayList<>();
    private SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private Context context;
    private MediaPlayer mediaPlayer;
    private String characterName;

    // 语音补生成相关
    private VoiceGenerateHelper voiceGenerateHelper;
    private int characterId = -1;
    private ExecutorService dbExecutor;
    private AppDatabase db;

    public void setMessages(List<CallMessage> messages) {
        this.messages = messages;
        notifyDataSetChanged();
    }

    public void setCharacterName(String name) {
        this.characterName = name;
    }

    public void setVoiceGenerateHelper(VoiceGenerateHelper helper) {
        this.voiceGenerateHelper = helper;
    }

    public void setCharacterId(int characterId) {
        this.characterId = characterId;
    }

    public void setDb(AppDatabase db) {
        this.db = db;
    }

    public void setDbExecutor(ExecutorService executor) {
        this.dbExecutor = executor;
    }

    public void addMessage(CallMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.item_call_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CallMessage msg = messages.get(position);
        
        holder.tvTime.setText(sdf.format(new Date(msg.timestamp)));
        holder.tvSpeaker.setText(msg.isFromUser ? "我" : (characterName != null ? characterName : "对方"));
        
        String displayText = msg.content;
        if (displayText != null) {
            // Remove pause tags like <#0.5#>
            displayText = displayText.replaceAll("<#[0-9.]+?#>", "");
            // Remove parenthetical expressions like (laughs)
            displayText = displayText.replaceAll("\\([^)]*\\)", "");
        }
        holder.tvContent.setText(displayText);
        
        if (msg.isFromUser) {
            // 用户自己说的话：只有 voiceUrl 非空才显示播放按钮（用户录音直接播放，不补生成）
            if (msg.voiceUrl != null && !msg.voiceUrl.isEmpty()) {
                holder.llVoicePlay.setVisibility(View.VISIBLE);
                holder.llVoicePlay.setOnClickListener(v -> playAudio(msg.voiceUrl));
                if (holder.tvPlayLabel != null) holder.tvPlayLabel.setText("播放语音");
            } else {
                holder.llVoicePlay.setVisibility(View.GONE);
            }
        } else {
            // AI 说话：不论 voiceUrl 是否为空都显示播放按钮
            holder.llVoicePlay.setVisibility(View.VISIBLE);
            if (msg.voiceUrl != null && !msg.voiceUrl.isEmpty()) {
                // 已有文件，直接播放
                holder.llVoicePlay.setOnClickListener(v -> playAudio(msg.voiceUrl));
                if (holder.tvPlayLabel != null) holder.tvPlayLabel.setText("播放语音");
            } else {
                // 没有文件，点击触发生成
                holder.llVoicePlay.setOnClickListener(v -> generateAndPlay(msg, holder));
                if (holder.tvPlayLabel != null) holder.tvPlayLabel.setText("生成语音");
            }
        }
    }

    private void playAudio(String audioPath) {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(audioPath);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "无法播放语音", Toast.LENGTH_SHORT).show();
        }
    }

    private void generateAndPlay(CallMessage msg, ViewHolder holder) {
        if (voiceGenerateHelper == null || characterId <= 0) {
            Toast.makeText(context, "无法生成语音", Toast.LENGTH_SHORT).show();
            return;
        }

        // UI 状态：显示进度
        if (holder.tvPlayLabel != null) holder.tvPlayLabel.setText("生成中...");
        holder.llVoicePlay.setEnabled(false);

        voiceGenerateHelper.generateVoiceAsync(characterId, msg.content, null,
                new VoiceGenerateHelper.GenerateCallback() {
                    @Override
                    public void onSuccess(String audioFilePath) {
                        // 回写数据库
                        if (db != null && dbExecutor != null) {
                            dbExecutor.execute(() -> {
                                msg.voiceUrl = audioFilePath;
                                db.callMessageDao().update(msg);
                            });
                        }
                        // 恢复按钮状态并播放
                        if (holder.tvPlayLabel != null) holder.tvPlayLabel.setText("播放语音");
                        holder.llVoicePlay.setEnabled(true);
                        holder.llVoicePlay.setOnClickListener(v -> playAudio(audioFilePath));
                        playAudio(audioFilePath);
                    }

                    @Override
                    public void onError(String reason) {
                        if (holder.tvPlayLabel != null) holder.tvPlayLabel.setText("生成语音");
                        holder.llVoicePlay.setEnabled(true);
                        Toast.makeText(context, reason, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    public void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    @Override
    public int getItemCount() {
        return messages != null ? messages.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvSpeaker;
        TextView tvTime;
        TextView tvContent;
        LinearLayout llVoicePlay;
        TextView tvPlayLabel;

        ViewHolder(View view) {
            super(view);
            tvSpeaker = view.findViewById(R.id.tvSpeaker);
            tvTime = view.findViewById(R.id.tvTime);
            tvContent = view.findViewById(R.id.tvContent);
            llVoicePlay = view.findViewById(R.id.llVoicePlay);
            tvPlayLabel = view.findViewById(R.id.tvPlayLabel);
        }
    }
}
