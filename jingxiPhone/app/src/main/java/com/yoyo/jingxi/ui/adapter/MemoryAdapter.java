package com.yoyo.jingxi.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.entity.Memory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MemoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_ITEM = 1;

    /**
     * Unified list item: either a header string or a Memory object.
     */
    public static class MemoryDisplayItem {
        public boolean isHeader;
        public String headerText;
        public Memory memory;

        public static MemoryDisplayItem header(String text) {
            MemoryDisplayItem item = new MemoryDisplayItem();
            item.isHeader = true;
            item.headerText = text;
            return item;
        }

        public static MemoryDisplayItem memoryItem(Memory memory) {
            MemoryDisplayItem item = new MemoryDisplayItem();
            item.isHeader = false;
            item.memory = memory;
            return item;
        }
    }

    private List<MemoryDisplayItem> items = new ArrayList<>();
    private OnMemoryLongClickListener listener;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    public interface OnMemoryLongClickListener {
        void onLongClick(Memory memory);
    }

    public void setOnMemoryLongClickListener(OnMemoryLongClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<MemoryDisplayItem> items) {
        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    /** @deprecated Use setItems(List&lt;MemoryDisplayItem&gt;) instead. */
    @Deprecated
    public void setMemories(List<Memory> memories) {
        List<MemoryDisplayItem> converted = new ArrayList<>();
        if (memories != null) {
            for (Memory m : memories) {
                converted.add(MemoryDisplayItem.memoryItem(m));
            }
        }
        setItems(converted);
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).isHeader ? VIEW_TYPE_HEADER : VIEW_TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_HEADER) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_profile_table_header, parent, false);
            return new HeaderVH(v);
        } else {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_memory, parent, false);
            return new ItemVH(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        MemoryDisplayItem item = items.get(position);

        if (item.isHeader) {
            HeaderVH h = (HeaderVH) holder;
            h.tvHeader.setText(item.headerText);
        } else {
            bindMemoryItem((ItemVH) holder, item.memory);
        }
    }

    private void bindMemoryItem(@NonNull ItemVH holder, Memory memory) {
        holder.tvMemoryContent.setText(memory.content);
        holder.tvMemoryTime.setVisibility(View.VISIBLE);
        holder.tvMemoryType.setVisibility(View.VISIBLE);
        holder.tvMemoryContent.setTextSize(14);
        holder.tvMemoryContent.getPaint().setFakeBoldText(false);
        holder.tvMemoryContent.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), android.R.color.black));
        holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), android.R.color.white));
        holder.tvMemoryStar.setVisibility(View.GONE);

        int padding = (int) (8 * holder.itemView.getContext().getResources().getDisplayMetrics().density);
        holder.itemView.setPadding(padding, padding, padding, padding);

        if (memory.timestamp > 0) {
            holder.tvMemoryTime.setText(dateFormat.format(new Date(memory.timestamp)));
        } else {
            holder.tvMemoryTime.setText("");
        }

        if (memory.type == 1) {
            holder.tvMemoryType.setText("核心记忆");
            holder.tvMemoryType.setBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.memory_type_core));
            holder.tvMemoryStar.setVisibility(View.VISIBLE);
            StringBuilder stars = new StringBuilder();
            for (int i = 0; i < memory.starLevel; i++) stars.append("★");
            holder.tvMemoryStar.setText(stars.toString());
        } else if (memory.type == 2) {
            holder.tvMemoryType.setText("最近关注");
            holder.tvMemoryType.setBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.memory_type_concern));
        } else if (memory.type == 3) {
            holder.tvMemoryType.setText("画像");
            holder.tvMemoryType.setBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.memory_type_profile));
        } else if (memory.type == 4) {
            holder.tvMemoryType.setText("日记");
            holder.tvMemoryType.setBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.memory_type_diary));
        } else {
            holder.tvMemoryType.setText("普通记忆");
            holder.tvMemoryType.setBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.memory_type_normal));
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
        return items.size();
    }

    static class HeaderVH extends RecyclerView.ViewHolder {
        TextView tvHeader;
        HeaderVH(View v) {
            super(v);
            tvHeader = v.findViewById(R.id.tvHeader);
        }
    }

    static class ItemVH extends RecyclerView.ViewHolder {
        TextView tvMemoryType, tvMemoryStar, tvMemoryTime, tvMemoryContent;
        ItemVH(@NonNull View itemView) {
            super(itemView);
            tvMemoryType = itemView.findViewById(R.id.tvMemoryType);
            tvMemoryStar = itemView.findViewById(R.id.tvMemoryStar);
            tvMemoryTime = itemView.findViewById(R.id.tvMemoryTime);
            tvMemoryContent = itemView.findViewById(R.id.tvMemoryContent);
        }
    }
}
