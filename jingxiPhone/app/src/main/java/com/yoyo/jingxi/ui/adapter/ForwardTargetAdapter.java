package com.yoyo.jingxi.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.entity.ChatSession;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 转发目标选择列表适配器。
 * 支持多选角色，选中项通过 CheckBox 展示。
 */
public class ForwardTargetAdapter extends RecyclerView.Adapter<ForwardTargetAdapter.ViewHolder> {

    public static class SessionInfo {
        public ChatSession session;
        public String characterName;
        public String characterAvatarPath;
    }

    private final List<SessionInfo> sessions = new ArrayList<>();
    private final Set<Integer> selectedPositions = new HashSet<>();

    private OnSelectionChangedListener selectionListener;

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount);
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.selectionListener = listener;
    }

    public void setSessions(List<SessionInfo> sessions) {
        this.sessions.clear();
        if (sessions != null) {
            this.sessions.addAll(sessions);
        }
        selectedPositions.clear();
        notifyDataSetChanged();
    }

    public List<SessionInfo> getSelectedSessions() {
        List<SessionInfo> result = new ArrayList<>();
        for (int pos : selectedPositions) {
            if (pos >= 0 && pos < sessions.size()) {
                result.add(sessions.get(pos));
            }
        }
        return result;
    }

    public int getSelectedCount() {
        return selectedPositions.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_forward_target, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SessionInfo info = sessions.get(position);
        holder.tvCharacterName.setText(info.characterName != null ? info.characterName : "未知");
        holder.tvPersonaName.setText(info.session.myPersonaName != null ? info.session.myPersonaName : "");

        if (info.characterAvatarPath != null && !info.characterAvatarPath.isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(info.characterAvatarPath)
                    .circleCrop()
                    .placeholder(R.drawable.ic_launcher_round)
                    .into(holder.ivAvatar);
        } else {
            holder.ivAvatar.setImageResource(R.drawable.ic_launcher_round);
        }

        holder.cbSelectTarget.setChecked(selectedPositions.contains(position));

        holder.itemView.setOnClickListener(v -> {
            if (selectedPositions.contains(position)) {
                selectedPositions.remove(position);
                holder.cbSelectTarget.setChecked(false);
            } else {
                selectedPositions.add(position);
                holder.cbSelectTarget.setChecked(true);
            }
            if (selectionListener != null) {
                selectionListener.onSelectionChanged(selectedPositions.size());
            }
        });
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox cbSelectTarget;
        ImageView ivAvatar;
        TextView tvCharacterName;
        TextView tvPersonaName;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cbSelectTarget = itemView.findViewById(R.id.cbSelectTarget);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            tvCharacterName = itemView.findViewById(R.id.tvCharacterName);
            tvPersonaName = itemView.findViewById(R.id.tvPersonaName);
        }
    }
}
