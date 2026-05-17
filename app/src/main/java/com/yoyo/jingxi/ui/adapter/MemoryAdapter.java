package com.yoyo.jingxi.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.entity.Memory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MemoryAdapter extends RecyclerView.Adapter<MemoryAdapter.ViewHolder> {

    private List<Memory> memories = new ArrayList<>();
    private OnMemoryLongClickListener listener;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    public interface OnMemoryLongClickListener {
        void onLongClick(Memory memory);
    }

    public void setOnMemoryLongClickListener(OnMemoryLongClickListener listener) {
        this.listener = listener;
    }

    public void setMemories(List<Memory> memories) {
        this.memories = memories;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_memory, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Memory memory = memories.get(position);

        holder.tvMemoryContent.setText(memory.content);
        
        if (memory.type == -1) {
            // 分类标题
            holder.tvMemoryTime.setVisibility(View.GONE);
            holder.tvMemoryType.setVisibility(View.GONE);
            holder.tvMemoryStar.setVisibility(View.GONE);
            holder.tvMemoryContent.setTextSize(18);
            holder.tvMemoryContent.getPaint().setFakeBoldText(true);
            holder.tvMemoryContent.setTextColor(0xFF333333);
            holder.itemView.setBackgroundColor(0xFFEEEEEE);
            holder.itemView.setPadding(20, 20, 20, 20);
            holder.itemView.setOnLongClickListener(null);
            return;
        }

        holder.tvMemoryTime.setVisibility(View.VISIBLE);
        holder.tvMemoryType.setVisibility(View.VISIBLE);
        holder.tvMemoryContent.setTextSize(14);
        holder.tvMemoryContent.getPaint().setFakeBoldText(false);
        holder.tvMemoryContent.setTextColor(0xFF000000);
        holder.itemView.setBackgroundColor(0xFFFFFFFF);
        
        int padding = (int) (8 * holder.itemView.getContext().getResources().getDisplayMetrics().density);
        holder.itemView.setPadding(padding, padding, padding, padding);

        holder.tvMemoryTime.setText(dateFormat.format(new Date(memory.timestamp)));

        if (memory.type == 1) {
            holder.tvMemoryType.setText("核心记忆");
            holder.tvMemoryType.setBackgroundColor(0xFFFF9800); // 橙色
            holder.tvMemoryStar.setVisibility(View.VISIBLE);
            StringBuilder stars = new StringBuilder();
            for (int i = 0; i < memory.starLevel; i++) {
                stars.append("★");
            }
            holder.tvMemoryStar.setText(stars.toString());
        } else {
            holder.tvMemoryType.setText("普通记忆");
            holder.tvMemoryType.setBackgroundColor(0xFF4CAF50); // 绿色
            holder.tvMemoryStar.setVisibility(View.GONE);
        }

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onLongClick(memory);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return memories == null ? 0 : memories.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvMemoryType;
        TextView tvMemoryStar;
        TextView tvMemoryTime;
        TextView tvMemoryContent;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMemoryType = itemView.findViewById(R.id.tvMemoryType);
            tvMemoryStar = itemView.findViewById(R.id.tvMemoryStar);
            tvMemoryTime = itemView.findViewById(R.id.tvMemoryTime);
            tvMemoryContent = itemView.findViewById(R.id.tvMemoryContent);
        }
    }
}
