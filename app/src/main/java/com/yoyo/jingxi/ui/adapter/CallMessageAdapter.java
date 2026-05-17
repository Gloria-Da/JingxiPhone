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
import com.yoyo.jingxi.data.entity.CallMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CallMessageAdapter extends RecyclerView.Adapter<CallMessageAdapter.ViewHolder> {

    private List<CallMessage> messages = new ArrayList<>();
    private SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private Context context;
    private MediaPlayer mediaPlayer;
    private String characterName;

    public void setMessages(List<CallMessage> messages) {
        this.messages = messages;
        notifyDataSetChanged();
    }

    public void setCharacterName(String name) {
        this.characterName = name;
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
        
        if (msg.voiceUrl != null && !msg.voiceUrl.isEmpty()) {
            holder.llVoicePlay.setVisibility(View.VISIBLE);
            holder.llVoicePlay.setOnClickListener(v -> playAudio(msg.voiceUrl));
        } else {
            holder.llVoicePlay.setVisibility(View.GONE);
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

        ViewHolder(View view) {
            super(view);
            tvSpeaker = view.findViewById(R.id.tvSpeaker);
            tvTime = view.findViewById(R.id.tvTime);
            tvContent = view.findViewById(R.id.tvContent);
            llVoicePlay = view.findViewById(R.id.llVoicePlay);
        }
    }
}
