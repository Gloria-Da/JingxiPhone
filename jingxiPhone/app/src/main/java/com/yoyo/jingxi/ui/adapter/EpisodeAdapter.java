package com.yoyo.jingxi.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.entity.EpisodicMemory;
import com.yoyo.jingxi.utils.TimeWeightCalculator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EpisodeAdapter extends RecyclerView.Adapter<EpisodeAdapter.ViewHolder> {

    private List<EpisodicMemory> episodes = new ArrayList<>();
    private OnItemLongClickListener listener;
    private boolean showPersonaTag = false;

    private static final Map<String, Integer> EMOTION_COLOR_RES = new HashMap<>();
    static {
        EMOTION_COLOR_RES.put("开心", R.color.emotion_happy);
        EMOTION_COLOR_RES.put("难过", R.color.emotion_sad);
        EMOTION_COLOR_RES.put("紧张", R.color.emotion_nervous);
        EMOTION_COLOR_RES.put("平静", R.color.emotion_calm);
        EMOTION_COLOR_RES.put("兴奋", R.color.emotion_excited);
        EMOTION_COLOR_RES.put("愤怒", R.color.emotion_angry);
        EMOTION_COLOR_RES.put("温馨", R.color.emotion_warm);
        EMOTION_COLOR_RES.put("尴尬", R.color.emotion_awkward);
        // UnifiedCurator emotions (also used by migration after alignment)
        EMOTION_COLOR_RES.put("心疼", R.color.emotion_heartache);
        EMOTION_COLOR_RES.put("感动", R.color.emotion_moved);
        EMOTION_COLOR_RES.put("生气", R.color.emotion_angry_intense);
        EMOTION_COLOR_RES.put("担心", R.color.emotion_worried);
        EMOTION_COLOR_RES.put("温暖", R.color.emotion_warmth);
        EMOTION_COLOR_RES.put("愧疚", R.color.emotion_guilty);
        EMOTION_COLOR_RES.put("好奇", R.color.emotion_curious);
    }

    public interface OnItemLongClickListener {
        void onLongClick(EpisodicMemory episode);
    }

    public void setOnItemLongClickListener(OnItemLongClickListener l) { this.listener = l; }

    public void setEpisodes(List<EpisodicMemory> episodes, boolean showPersonaTag) {
        this.episodes = episodes != null ? episodes : new ArrayList<>();
        this.showPersonaTag = showPersonaTag;
        notifyDataSetChanged();
    }

    public void addEpisodes(List<EpisodicMemory> newEpisodes, boolean showPersonaTag) {
        if (newEpisodes == null || newEpisodes.isEmpty()) return;
        int startPos = this.episodes.size();
        this.episodes.addAll(newEpisodes);
        this.showPersonaTag = showPersonaTag;
        notifyItemRangeInserted(startPos, newEpisodes.size());
    }

    public int getEpisodeCount() {
        return episodes.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_episodic_memory_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        EpisodicMemory ep = episodes.get(position);

        // Date
        if (ep.episodeDate != null && ep.episodeDate.length() >= 10) {
            try {
                String[] parts = ep.episodeDate.split("-");
                holder.tvDay.setText(parts.length >= 3 ? parts[2] : "");
                holder.tvMonth.setText(parts.length >= 2 ? (Integer.parseInt(parts[1]) + "月") : "");
            } catch (Exception e) {
                holder.tvDay.setText("");
                holder.tvMonth.setText("");
            }
        }

        holder.tvTitle.setText(ep.title != null ? ep.title : "");
        holder.tvContent.setText(ep.subjectiveDiary != null ? ep.subjectiveDiary : "");

        // Emotion color on left bar — use ColorRes
        Integer barColorRes = EMOTION_COLOR_RES.get(ep.emotionalTone);
        int barColor = ContextCompat.getColor(holder.itemView.getContext(),
            barColorRes != null ? barColorRes : R.color.emotion_bar_default);
        holder.vEmotionBar.setBackgroundColor(barColor);

        // Tone label
        String toneText = ep.emotionalTone != null ? ep.emotionalTone : "";
        if (TimeWeightCalculator.isEnabled()) {
            long now = System.currentTimeMillis();
            float retention = TimeWeightCalculator.computeEpisodicRetention(
                ep.createdAt, ep.lastRecalledAt, ep.recallCount, now);
            float weight = TimeWeightCalculator.computeEpisodicWeight(
                ep.importanceLevel, ep.createdAt, ep.lastRecalledAt, ep.recallCount, now);
            // Show recall count and weight on tone line
            if (ep.recallCount > 0) {
                toneText += String.format(" | 回忆%d次 权重%.1f", ep.recallCount, weight);
            } else {
                toneText += String.format(" | 权重%.1f", weight);
            }
            // Fade emotion bar for decayed memories
            if (retention < 0.5f) {
                holder.vEmotionBar.setAlpha(0.35f);
            } else if (retention < 0.8f) {
                holder.vEmotionBar.setAlpha(0.65f);
            }
        }
        holder.tvTone.setText(toneText);

        // 人设标签（仅在"全部人设"模式下显示）
        if (showPersonaTag && ep.myPersonaName != null && !ep.myPersonaName.isEmpty()) {
            holder.tvPersonaTag.setVisibility(View.VISIBLE);
            holder.tvPersonaTag.setText(ep.myPersonaName);
        } else {
            holder.tvPersonaTag.setVisibility(View.GONE);
        }

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onLongClick(ep);
            return true;
        });
    }

    @Override
    public int getItemCount() { return episodes.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDay, tvMonth, tvTitle, tvContent, tvTone, tvPersonaTag;
        View vEmotionBar;
        ViewHolder(View v) {
            super(v);
            tvDay = v.findViewById(R.id.tvDay);
            tvMonth = v.findViewById(R.id.tvMonth);
            tvTitle = v.findViewById(R.id.tvTitle);
            tvContent = v.findViewById(R.id.tvContent);
            tvTone = v.findViewById(R.id.tvTone);
            tvPersonaTag = v.findViewById(R.id.tvPersonaTag);
            vEmotionBar = v.findViewById(R.id.vEmotionBar);
        }
    }
}
