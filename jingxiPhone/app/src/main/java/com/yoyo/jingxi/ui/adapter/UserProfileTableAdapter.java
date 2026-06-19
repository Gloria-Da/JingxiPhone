package com.yoyo.jingxi.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.entity.UserProfileNode;
import com.yoyo.jingxi.utils.MemoryPresetKeys;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UserProfileTableAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ROW = 1;

    private List<RowItem> rows = new ArrayList<>();
    private OnItemClickListener listener;
    private boolean showPersonaTag = false;

    public static class RowItem {
        public int type;
        public String category;
        public String keyItem;
        public String value;
        public String emotion;
        public int confidence;
        public int nodeId;
        public boolean isCustom;
        public String personaName;
    }

    public interface OnItemClickListener {
        void onRowClick(RowItem item);
    }
    public void setOnItemClickListener(OnItemClickListener l) { this.listener = l; }

    public void buildRows(List<UserProfileNode> existingNodes, boolean showPersonaTag) {
        this.showPersonaTag = showPersonaTag;
        rows.clear();
        // Index existing nodes by (category, keyItem)
        Map<String, UserProfileNode> index = new LinkedHashMap<>();
        if (existingNodes != null) {
            for (UserProfileNode n : existingNodes) {
                index.put((n.category != null ? n.category : "") + "::" + (n.keyItem != null ? n.keyItem : ""), n);
            }
        }

        Map<String, List<String>> presets = MemoryPresetKeys.getPresetKeys();
        for (Map.Entry<String, List<String>> catEntry : presets.entrySet()) {
            String cat = catEntry.getKey();
            // Header
            RowItem header = new RowItem(); header.type = TYPE_HEADER; header.category = cat;
            rows.add(header);
            // Preset rows
            for (String ki : catEntry.getValue()) {
                RowItem ri = new RowItem(); ri.type = TYPE_ROW; ri.category = cat; ri.keyItem = ki;
                UserProfileNode node = index.get(cat + "::" + ki);
                if (node != null) {
                    ri.value = node.valueContent;
                    ri.emotion = node.emotionTag;
                    ri.confidence = node.confidence;
                    ri.nodeId = node.id;
                    ri.isCustom = false;
                    ri.personaName = node.myPersonaName;
                }
                rows.add(ri);
            }
        }

        // Custom (non-preset) entries grouped by category
        Map<String, List<UserProfileNode>> customByCat = new LinkedHashMap<>();
        if (existingNodes != null) {
            for (UserProfileNode n : existingNodes) {
                if (n.isCustom) {
                    String cat = n.category != null ? n.category : "其他";
                    customByCat.computeIfAbsent(cat, k -> new ArrayList<>()).add(n);
                }
            }
        }
        for (Map.Entry<String, List<UserProfileNode>> entry : customByCat.entrySet()) {
            for (UserProfileNode n : entry.getValue()) {
                RowItem ri = new RowItem(); ri.type = TYPE_ROW; ri.category = entry.getKey(); ri.keyItem = n.keyItem;
                ri.value = n.valueContent; ri.emotion = n.emotionTag; ri.confidence = n.confidence;
                ri.nodeId = n.id; ri.isCustom = true; ri.personaName = n.myPersonaName;
                rows.add(ri);
            }
        }
        notifyDataSetChanged();
    }

    @Override public int getItemViewType(int pos) { return rows.get(pos).type; }

    @NonNull @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_profile_table_header, parent, false);
            return new HeaderVH(v);
        } else {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_profile_table_row, parent, false);
            return new RowVH(v);
        }
    }

    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
        RowItem item = rows.get(pos);
        if (item.type == TYPE_HEADER) {
            ((HeaderVH) h).tvHeader.setText(item.category);
        } else {
            RowVH r = (RowVH) h;
            r.tvKey.setText(item.keyItem);
            if (item.value != null && !item.value.isEmpty()) {
                r.tvValue.setText(item.value);
                r.tvValue.setTextColor(ContextCompat.getColor(r.itemView.getContext(), R.color.profile_value_text));
            } else {
                r.tvValue.setText("—");
                r.tvValue.setTextColor(ContextCompat.getColor(r.itemView.getContext(), R.color.profile_empty_placeholder));
            }
            if (item.emotion != null && !"普通".equals(item.emotion)) {
                r.tvEmotion.setVisibility(View.VISIBLE);
                r.tvEmotion.setText(item.emotion);
            } else {
                r.tvEmotion.setVisibility(View.GONE);
            }
            // 人设标签（仅在"全部人设"模式下显示）
            if (showPersonaTag && item.personaName != null && !item.personaName.isEmpty()) {
                r.tvPersonaTag.setVisibility(View.VISIBLE);
                r.tvPersonaTag.setText(item.personaName);
            } else {
                r.tvPersonaTag.setVisibility(View.GONE);
            }
            r.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onRowClick(item);
            });
        }
    }

    @Override public int getItemCount() { return rows.size(); }

    static class HeaderVH extends RecyclerView.ViewHolder {
        TextView tvHeader;
        HeaderVH(View v) { super(v); tvHeader = v.findViewById(R.id.tvHeader); }
    }
    static class RowVH extends RecyclerView.ViewHolder {
        TextView tvKey, tvValue, tvEmotion, tvPersonaTag;
        RowVH(View v) { super(v); tvKey = v.findViewById(R.id.tvKeyLabel); tvValue = v.findViewById(R.id.tvValue); tvEmotion = v.findViewById(R.id.tvEmotion); tvPersonaTag = v.findViewById(R.id.tvPersonaTag); }
    }
}
