package com.yoyo.jingxi.ui.widget;

import android.app.AlertDialog;
import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.yoyo.jingxi.R;

import java.lang.reflect.Type;
import java.util.List;

/**
 * 合并转发卡片组件。
 * 显示聊天记录标题 + 前几条消息预览，点击弹出完整消息列表。
 * Message.content 中存储的 JSON 格式：
 * {"title":"聊天记录","messages":[{"sender":"我","content":"hello"},...]}
 */
public class ForwardCardView extends LinearLayout {

    private static final Gson gson = new Gson();
    private static final Type FORWARD_TYPE = new TypeToken<ForwardData>() {}.getType();

    private LinearLayout cardContainer;
    private TextView tvForwardTitle;
    private TextView tvForwardPreview;
    private ForwardData forwardData;

    public ForwardCardView(Context context) {
        this(context, null);
    }

    public ForwardCardView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ForwardCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.widget_forward_card, this, true);
        cardContainer = findViewById(R.id.cardForwardContainer);
        tvForwardTitle = findViewById(R.id.tvForwardTitle);
        tvForwardPreview = findViewById(R.id.tvForwardPreview);

        cardContainer.setOnClickListener(v -> {
            if (forwardData != null && forwardData.messages != null) {
                showDetailDialog(context);
            }
        });
    }

    /**
     * 代理长按监听器到内部可点击的 cardContainer
     */
    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        cardContainer.setOnLongClickListener(l);
    }

    /**
     * 从 Message.content JSON 解析并绑定数据
     */
    public void setForwardData(String jsonContent) {
        if (TextUtils.isEmpty(jsonContent)) {
            setVisibility(GONE);
            return;
        }
        try {
            forwardData = gson.fromJson(jsonContent, FORWARD_TYPE);
            if (forwardData != null) {
                setVisibility(VISIBLE);
                tvForwardTitle.setText(forwardData.title != null ? forwardData.title : "聊天记录");

                // 生成预览（前3条）
                StringBuilder preview = new StringBuilder();
                int previewCount = Math.min(3, forwardData.messages.size());
                for (int i = 0; i < previewCount; i++) {
                    ForwardMessage fm = forwardData.messages.get(i);
                    String sender = fm.sender != null ? fm.sender : "";
                    String txt = fm.content != null ? fm.content : "";
                    if (txt.length() > 30) txt = txt.substring(0, 30) + "...";
                    preview.append(sender).append(": ").append(txt);
                    if (i < previewCount - 1) preview.append("\n");
                }
                if (forwardData.messages.size() > previewCount) {
                    preview.append("\n... 共 ").append(forwardData.messages.size()).append(" 条消息");
                }
                tvForwardPreview.setText(preview.toString());
            }
        } catch (Exception e) {
            setVisibility(GONE);
        }
    }

    private void showDetailDialog(Context context) {
        if (forwardData == null || forwardData.messages == null) return;

        StringBuilder detail = new StringBuilder();
        for (int i = 0; i < forwardData.messages.size(); i++) {
            ForwardMessage fm = forwardData.messages.get(i);
            String sender = fm.sender != null ? fm.sender : "";
            String txt = fm.content != null ? fm.content : "";
            detail.append("[").append(sender).append("]\n").append(txt);
            if (i < forwardData.messages.size() - 1) detail.append("\n\n");
        }

        TextView textView = new TextView(context);
        textView.setText(detail.toString());
        textView.setTextIsSelectable(true);
        textView.setPadding(48, 32, 48, 32);
        textView.setTextSize(14);
        textView.setTextColor(0xFF333333);
        textView.setLineSpacing(4, 1.1f);

        new AlertDialog.Builder(context)
                .setTitle(forwardData.title != null ? forwardData.title : "聊天记录")
                .setView(textView)
                .setPositiveButton("关闭", null)
                .show();
    }

    // ---- JSON 数据模型 ----

    public static class ForwardData {
        public String title;
        public List<ForwardMessage> messages;
    }

    public static class ForwardMessage {
        public String sender;
        public String content;
    }
}
