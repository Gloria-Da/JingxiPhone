package com.yoyo.jingxi.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.entity.Message;

import java.util.ArrayList;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_LEFT = 0;
    private static final int VIEW_TYPE_RIGHT = 1;

    private List<Message> messages = new ArrayList<>();
    private String friendName;

    public ChatAdapter() {
    }

    public ChatAdapter(String friendName) {
        this.friendName = friendName;
    }

    @Override
    public void onAttachedToRecyclerView(@androidx.annotation.NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        recyclerView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        Message message = messages.get(position);
        if (message.isFromUser) {
            return VIEW_TYPE_RIGHT; // 发送方是用户，显示在右侧
        } else {
            return VIEW_TYPE_LEFT;  // 发送方是AI，显示在左侧
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_RIGHT) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_right, parent, false);
            return new RightViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_left, parent, false);
            return new LeftViewHolder(view);
        }
    }

    public interface OnMessageLongClickListener {
        void onMessageLongClick(Message msg, View view);
    }

    private OnMessageLongClickListener longClickListener;

    public void setOnMessageLongClickListener(OnMessageLongClickListener listener) {
        this.longClickListener = listener;
    }

    private Message getMessageById(int messageId) {
        for (Message msg : messages) {
            if (msg.id == messageId) {
                return msg;
            }
        }
        return null;
    }

    private String characterAvatarPath;
    private String myAvatarPath;

    public void setCharacterAvatarPath(String avatarPath) {
        this.characterAvatarPath = avatarPath;
        notifyDataSetChanged();
    }

    public void setMyAvatarPath(String avatarPath) {
        this.myAvatarPath = avatarPath;
        notifyDataSetChanged();
    }

    private android.media.MediaPlayer mediaPlayer;
    
    private void playAudio(android.content.Context context, String audioPath) {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }
            mediaPlayer = new android.media.MediaPlayer();
            mediaPlayer.setDataSource(audioPath);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception e) {
            e.printStackTrace();
            android.widget.Toast.makeText(context, "播放失败", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message message = messages.get(position);
        
        // 显示时间戳逻辑（距离上一条超过 5 分钟显示）
        boolean showTimestamp = false;
        if (position == 0) {
            showTimestamp = true;
        } else {
            Message prevMessage = messages.get(position - 1);
            if (message.timestamp - prevMessage.timestamp > 5 * 60 * 1000) {
                showTimestamp = true;
            }
        }
        
        String timeString = "";
        if (showTimestamp) {
            timeString = new java.text.SimpleDateFormat("MM月dd日 HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(message.timestamp));
        }
        
        Message quotedMessage = null;
        if (message.quoteMessageId != -1) {
            quotedMessage = getMessageById(message.quoteMessageId);
        }
        
        if (holder instanceof RightViewHolder) {
            RightViewHolder rightHolder = (RightViewHolder) holder;

            if (myAvatarPath != null && !myAvatarPath.isEmpty()) {
                android.content.Context ctx = rightHolder.itemView.getContext();
                if (ctx instanceof android.app.Activity) {
                    android.app.Activity activity = (android.app.Activity) ctx;
                    if (!activity.isFinishing() && !activity.isDestroyed()) {
                        com.bumptech.glide.Glide.with(ctx)
                                .load(myAvatarPath)
                                .circleCrop()
                                .placeholder(R.drawable.ic_launcher_round)
                                .into(rightHolder.ivAvatarRight);
                    }
                } else {
                    com.bumptech.glide.Glide.with(ctx)
                            .load(myAvatarPath)
                            .circleCrop()
                            .placeholder(R.drawable.ic_launcher_round)
                            .into(rightHolder.ivAvatarRight);
                }
            } else {
                rightHolder.ivAvatarRight.setImageResource(R.drawable.ic_launcher_round);
            }
            
            if (showTimestamp) {
                rightHolder.tvTimestampRight.setVisibility(View.VISIBLE);
                rightHolder.tvTimestampRight.setText(timeString);
            } else {
                rightHolder.tvTimestampRight.setVisibility(View.GONE);
            }
            
            if (quotedMessage != null) {
                rightHolder.llQuoteRight.setVisibility(View.VISIBLE);
                String sender = quotedMessage.isFromUser ? "我" : (friendName != null ? friendName : "对方");
                rightHolder.tvQuoteContentRight.setText("引用 " + sender + ": " + quotedMessage.content);
            } else {
                rightHolder.llQuoteRight.setVisibility(View.GONE);
            }
            
            rightHolder.tvContentRight.setVisibility(View.GONE);
            rightHolder.ivImageRight.setVisibility(View.GONE);
            rightHolder.flVirtualImageRight.setVisibility(View.GONE);
            if (rightHolder.llVoiceContainerRight != null) rightHolder.llVoiceContainerRight.setVisibility(View.GONE);
            if (rightHolder.tvVoiceRight != null) rightHolder.tvVoiceRight.setVisibility(View.GONE);
            if (rightHolder.tvVoiceTextRight != null) rightHolder.tvVoiceTextRight.setVisibility(View.GONE);

            if (message.type == 99) {
                rightHolder.llNormalContentRight.setVisibility(View.GONE);
                rightHolder.tvRevokeRight.setVisibility(View.VISIBLE);
                rightHolder.tvRevokeRight.setText(message.content);
            } else {
                rightHolder.llNormalContentRight.setVisibility(View.VISIBLE);
                rightHolder.tvRevokeRight.setVisibility(View.GONE);

                View targetView = rightHolder.tvContentRight;

                if (message.type == 1) { // 语音
                    rightHolder.tvContentRight.setVisibility(View.GONE);
                    
                    if (rightHolder.llVoiceContainerRight != null) {
                        rightHolder.llVoiceContainerRight.setVisibility(View.VISIBLE);
                        
                        // 计算语音时长
                        int duration = Math.max(1, message.content.length() / 4);
                        if (rightHolder.tvVoiceRight != null) {
                            rightHolder.tvVoiceRight.setVisibility(View.VISIBLE);
                            rightHolder.tvVoiceRight.setText(duration + "\"");
                            
                            // 动态调整宽度
                            int minWidth = 80;
                            int maxWidth = 220;
                            int width = minWidth + (duration * 3);
                            if (width > maxWidth) width = maxWidth;
                            
                            android.view.ViewGroup.LayoutParams params = rightHolder.tvVoiceRight.getLayoutParams();
                            params.width = (int) android.util.TypedValue.applyDimension(
                                    android.util.TypedValue.COMPLEX_UNIT_DIP, width, 
                                    rightHolder.itemView.getContext().getResources().getDisplayMetrics());
                            params.height = (int) android.util.TypedValue.applyDimension(
                                    android.util.TypedValue.COMPLEX_UNIT_DIP, 46, 
                                    rightHolder.itemView.getContext().getResources().getDisplayMetrics());
                            rightHolder.tvVoiceRight.setLayoutParams(params);
                            
                            rightHolder.tvVoiceRight.setOnClickListener(v -> {
                                if (message.voiceUrl != null && !message.voiceUrl.isEmpty()) {
                                    playAudio(v.getContext(), message.voiceUrl);
                                } else {
                                    android.widget.Toast.makeText(v.getContext(), "播放语音: " + message.content, android.widget.Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                        
                        if (rightHolder.tvVoiceToTextBtnRight != null && rightHolder.tvVoiceTextRight != null) {
                            rightHolder.tvVoiceToTextBtnRight.setOnClickListener(v -> {
                                if (rightHolder.tvVoiceTextRight.getVisibility() == View.VISIBLE) {
                                    rightHolder.tvVoiceTextRight.setVisibility(View.GONE);
                                } else {
                                    rightHolder.tvVoiceTextRight.setVisibility(View.VISIBLE);
                                    String cleanText = message.content != null ? message.content : "";
                                    cleanText = cleanText.replaceAll("<#[0-9.]+?#>", "");
                                    cleanText = cleanText.replaceAll("\\(laughs\\)", "");
                                    cleanText = cleanText.replaceAll("\\(chuckle\\)", "");
                                    cleanText = cleanText.replaceAll("\\(coughs\\)", "");
                                    cleanText = cleanText.replaceAll("\\(clear-throat\\)", "");
                                    cleanText = cleanText.replaceAll("\\(groans\\)", "");
                                    cleanText = cleanText.replaceAll("\\(breath\\)", "");
                                    cleanText = cleanText.replaceAll("\\(pant\\)", "");
                                    cleanText = cleanText.replaceAll("\\(inhale\\)", "");
                                    cleanText = cleanText.replaceAll("\\(exhale\\)", "");
                                    cleanText = cleanText.replaceAll("\\(gasps\\)", "");
                                    cleanText = cleanText.replaceAll("\\(sniffs\\)", "");
                                    cleanText = cleanText.replaceAll("\\(sighs\\)", "");
                                    cleanText = cleanText.replaceAll("\\(snorts\\)", "");
                                    cleanText = cleanText.replaceAll("\\(burps\\)", "");
                                    cleanText = cleanText.replaceAll("\\(lip-smacking\\)", "");
                                    cleanText = cleanText.replaceAll("\\(humming\\)", "");
                                    cleanText = cleanText.replaceAll("\\(hissing\\)", "");
                                    cleanText = cleanText.replaceAll("\\(emm\\)", "");
                                    cleanText = cleanText.replaceAll("\\(sneezes\\)", "");
                                    
                                    // Remove text within any remaining parentheses
                                    cleanText = cleanText.replaceAll("\\([^)]*\\)", "");

                                    rightHolder.tvVoiceTextRight.setText(cleanText.trim());
                                }
                            });
                        }
                    }
                    
                    targetView = rightHolder.tvVoiceRight;
                } else if (message.type == 3 || message.type == 2) {
                    // 真实图片或自定义表情
                    rightHolder.ivImageRight.setVisibility(View.VISIBLE);
                    // 如果是表情，限制图片大小
                    if (message.type == 2) {
                        rightHolder.ivImageRight.setMaxWidth(300);
                        rightHolder.ivImageRight.setMaxHeight(300);
                    } else {
                        rightHolder.ivImageRight.setMaxWidth(800);
                        rightHolder.ivImageRight.setMaxHeight(800);
                        rightHolder.ivImageRight.setOnClickListener(v -> {
                            android.content.Intent intent = new android.content.Intent(v.getContext(), com.yoyo.jingxi.ui.activity.ImageDetailActivity.class);
                            intent.putExtra("image_url", message.imageUrl);
                            v.getContext().startActivity(intent);
                        });
                    }
                    if (message.imageUrl != null && !message.imageUrl.isEmpty()) {
                        try {
                            if (message.imageUrl.startsWith("data:image")) {
                                String base64 = message.imageUrl.substring(message.imageUrl.indexOf(",") + 1);
                                byte[] decodedString = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                                android.content.Context ctx = rightHolder.itemView.getContext();
                                boolean isValid = !(ctx instanceof android.app.Activity) || (!((android.app.Activity) ctx).isFinishing() && !((android.app.Activity) ctx).isDestroyed());
                                if (isValid) {
                                    com.bumptech.glide.Glide.with(ctx)
                                            .load(decodedString)
                                            .error(android.R.drawable.ic_menu_gallery)
                                            .into(rightHolder.ivImageRight);
                                }
                            } else {
                                android.content.Context ctx = rightHolder.itemView.getContext();
                                boolean isValid = !(ctx instanceof android.app.Activity) || (!((android.app.Activity) ctx).isFinishing() && !((android.app.Activity) ctx).isDestroyed());
                                if (isValid) {
                                    com.bumptech.glide.Glide.with(ctx)
                                            .load(message.imageUrl)
                                            .error(android.R.drawable.ic_menu_gallery)
                                            .into(rightHolder.ivImageRight);
                                }
                            }
                            } catch (Exception e) {
                                android.content.Context ctx = rightHolder.itemView.getContext();
                                boolean isValid = !(ctx instanceof android.app.Activity) || (!((android.app.Activity) ctx).isFinishing() && !((android.app.Activity) ctx).isDestroyed());
                                if (isValid) {
                                    com.bumptech.glide.Glide.with(ctx)
                                            .load(android.R.drawable.ic_menu_gallery)
                                            .into(rightHolder.ivImageRight);
                                }
                            }
                    }
                    targetView = rightHolder.ivImageRight;
                } else if (message.type == 4) {
                    // 虚拟图片
                    rightHolder.flVirtualImageRight.setVisibility(View.VISIBLE);
                    rightHolder.flVirtualImageRight.setOnClickListener(v -> {
                        android.content.Intent intent = new android.content.Intent(v.getContext(), com.yoyo.jingxi.ui.activity.ImageDetailActivity.class);
                        intent.putExtra("virtual_desc", message.imageDesc);
                        intent.putExtra("image_url", message.imageDesc != null ? "virtual://" + message.imageDesc : "");
                        intent.putExtra("message_id", message.id);
                        v.getContext().startActivity(intent);
                    });
                    targetView = rightHolder.flVirtualImageRight;
                } else {
                    rightHolder.tvContentRight.setVisibility(View.VISIBLE);
                    String displayText = message.content != null ? message.content : "";
                    if (!message.isFromUser) {
                        displayText = displayText.replaceAll("<#[0-9.]+?#>", "");
                        displayText = displayText.replaceAll("\\(laughs\\)", "");
                        displayText = displayText.replaceAll("\\(sighs\\)", "");
                        displayText = displayText.replaceAll("\\(clears throat\\)", "");
                        displayText = displayText.replaceAll("\\(sniffs\\)", "");
                        displayText = displayText.replaceAll("\\(cries\\)", "");
                        displayText = displayText.replaceAll("\\(yawns\\)", "");
                        displayText = displayText.replaceAll("\\(gasps\\)", "");
                        displayText = displayText.replaceAll("\\(swallows\\)", "");
                    }
                    rightHolder.tvContentRight.setText(displayText);
                    // 恢复默认样式
                    rightHolder.tvContentRight.setBackgroundResource(R.drawable.bg_chat_bubble_right);
                    // Get color from theme
                    android.util.TypedValue typedValue = new android.util.TypedValue();
                    rightHolder.itemView.getContext().getTheme().resolveAttribute(R.attr.colorTextPrimary, typedValue, true);
                    int textColor = typedValue.data;
                    rightHolder.tvContentRight.setTextColor(textColor);
                    rightHolder.tvContentRight.setTextSize(16);
                }
                
                if (targetView != null) {
                    targetView.setOnLongClickListener(v -> {
                        if (longClickListener != null) {
                            longClickListener.onMessageLongClick(message, v);
                        }
                        return true;
                    });
                }
            }
            
        } else if (holder instanceof LeftViewHolder) {
            LeftViewHolder leftHolder = (LeftViewHolder) holder;
            
            if (showTimestamp) {
                leftHolder.tvTimestampLeft.setVisibility(View.VISIBLE);
                leftHolder.tvTimestampLeft.setText(timeString);
            } else {
                leftHolder.tvTimestampLeft.setVisibility(View.GONE);
            }
            
            if (quotedMessage != null) {
                leftHolder.llQuoteLeft.setVisibility(View.VISIBLE);
                String sender = quotedMessage.isFromUser ? "我" : (friendName != null ? friendName : "对方");
                leftHolder.tvQuoteContentLeft.setText("引用 " + sender + ": " + quotedMessage.content);
            } else {
                leftHolder.llQuoteLeft.setVisibility(View.GONE);
            }
            
            // 隐藏所有特定类型视图，再根据 type 显示对应的
            leftHolder.tvContentLeft.setVisibility(View.GONE);
            leftHolder.tvVoiceLeft.setVisibility(View.GONE);
            leftHolder.tvEmojiLeft.setVisibility(View.GONE);
            leftHolder.ivImageLeft.setVisibility(View.GONE);
            leftHolder.flVirtualImageLeft.setVisibility(View.GONE);

            if (characterAvatarPath != null && !characterAvatarPath.isEmpty()) {
                android.content.Context ctx = leftHolder.itemView.getContext();
                if (ctx instanceof android.app.Activity) {
                    android.app.Activity activity = (android.app.Activity) ctx;
                    if (!activity.isFinishing() && !activity.isDestroyed()) {
                        com.bumptech.glide.Glide.with(ctx)
                                .load(characterAvatarPath)
                                .circleCrop()
                                .placeholder(R.drawable.ic_launcher_round)
                                .into(leftHolder.ivAvatarLeft);
                    }
                } else {
                    com.bumptech.glide.Glide.with(ctx)
                            .load(characterAvatarPath)
                            .circleCrop()
                            .placeholder(R.drawable.ic_launcher_round)
                            .into(leftHolder.ivAvatarLeft);
                }
            } else {
                leftHolder.ivAvatarLeft.setImageResource(R.drawable.ic_launcher_round);
            }

            if (message.type == 99) {
                leftHolder.llNormalContentLeft.setVisibility(View.GONE);
                leftHolder.tvRevokeLeft.setVisibility(View.VISIBLE);
                leftHolder.tvRevokeLeft.setText(message.content);
            } else {
                leftHolder.llNormalContentLeft.setVisibility(View.VISIBLE);
                leftHolder.tvRevokeLeft.setVisibility(View.GONE);
                
                View targetView = leftHolder.tvContentLeft;
                
                // 隐藏转文字气泡，防止复用问题
                leftHolder.llVoiceContainerLeft.setVisibility(View.GONE);
                leftHolder.tvVoiceTextLeft.setVisibility(View.GONE);

                switch (message.type) {
            case 1: // 语音
                leftHolder.llVoiceContainerLeft.setVisibility(View.VISIBLE);
                leftHolder.tvVoiceLeft.setVisibility(View.VISIBLE);
                
                // 计算语音时长
                int duration = Math.max(1, message.content.length() / 4);
                leftHolder.tvVoiceLeft.setText(duration + "\"");
                
                // 动态调整宽度
                int minWidth = 80;
                int maxWidth = 220;
                int width = minWidth + (duration * 3);
                if (width > maxWidth) width = maxWidth;
                
                android.view.ViewGroup.LayoutParams params = leftHolder.tvVoiceLeft.getLayoutParams();
                params.width = (int) android.util.TypedValue.applyDimension(
                        android.util.TypedValue.COMPLEX_UNIT_DIP, width, 
                        leftHolder.itemView.getContext().getResources().getDisplayMetrics());
                params.height = (int) android.util.TypedValue.applyDimension(
                        android.util.TypedValue.COMPLEX_UNIT_DIP, 46, 
                        leftHolder.itemView.getContext().getResources().getDisplayMetrics());
                leftHolder.tvVoiceLeft.setLayoutParams(params);
                
                // 转文字按钮点击事件
                leftHolder.tvVoiceToTextBtnLeft.setOnClickListener(v -> {
                    if (leftHolder.tvVoiceTextLeft.getVisibility() == View.VISIBLE) {
                        leftHolder.tvVoiceTextLeft.setVisibility(View.GONE);
                    } else {
                        leftHolder.tvVoiceTextLeft.setVisibility(View.VISIBLE);
                        String cleanText = message.content != null ? message.content : "";
                        cleanText = cleanText.replaceAll("<#[0-9.]+?#>", "");
                        cleanText = cleanText.replaceAll("\\(laughs\\)", "");
                        cleanText = cleanText.replaceAll("\\(sighs\\)", "");
                        cleanText = cleanText.replaceAll("\\(clears throat\\)", "");
                        cleanText = cleanText.replaceAll("\\(sniffs\\)", "");
                        cleanText = cleanText.replaceAll("\\(cries\\)", "");
                        cleanText = cleanText.replaceAll("\\(yawns\\)", "");
                        cleanText = cleanText.replaceAll("\\(gasps\\)", "");
                        cleanText = cleanText.replaceAll("\\(swallows\\)", "");
                        
                        // Remove text within any remaining parentheses
                        cleanText = cleanText.replaceAll("\\([^)]*\\)", "");
                        
                        leftHolder.tvVoiceTextLeft.setText(cleanText.trim());
                    }
                });

                // 点击语音条的简单提示
                leftHolder.tvVoiceLeft.setOnClickListener(v -> {
                    if (message.voiceUrl != null && !message.voiceUrl.isEmpty()) {
                        playAudio(v.getContext(), message.voiceUrl);
                    } else {
                        android.widget.Toast.makeText(v.getContext(), "播放语音: " + message.content, android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
                targetView = leftHolder.tvVoiceLeft;
                break;
                    case 2: // 表情
                        if (message.imageUrl != null && !message.imageUrl.isEmpty()) {
                            leftHolder.ivImageLeft.setVisibility(View.VISIBLE);
                            leftHolder.ivImageLeft.setMaxWidth(300);
                            leftHolder.ivImageLeft.setMaxHeight(300);
                            try {
                                if (message.imageUrl.startsWith("data:image")) {
                                    String base64 = message.imageUrl.substring(message.imageUrl.indexOf(",") + 1);
                                    byte[] decodedString = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                                    android.content.Context ctx = leftHolder.itemView.getContext();
                                    boolean isValid = !(ctx instanceof android.app.Activity) || (!((android.app.Activity) ctx).isFinishing() && !((android.app.Activity) ctx).isDestroyed());
                                    if (isValid) {
                                        com.bumptech.glide.Glide.with(ctx)
                                                .load(decodedString)
                                                .error(android.R.drawable.ic_menu_gallery)
                                                .into(leftHolder.ivImageLeft);
                                    }
                                } else {
                                    android.content.Context ctx = leftHolder.itemView.getContext();
                                    boolean isValid = !(ctx instanceof android.app.Activity) || (!((android.app.Activity) ctx).isFinishing() && !((android.app.Activity) ctx).isDestroyed());
                                    if (isValid) {
                                        com.bumptech.glide.Glide.with(ctx)
                                                .load(message.imageUrl)
                                                .error(android.R.drawable.ic_menu_gallery)
                                                .into(leftHolder.ivImageLeft);
                                    }
                                }
                                            } catch (Exception e) {
                                                android.content.Context ctx = leftHolder.itemView.getContext();
                                                boolean isValid = !(ctx instanceof android.app.Activity) || (!((android.app.Activity) ctx).isFinishing() && !((android.app.Activity) ctx).isDestroyed());
                                                if (isValid) {
                                                    com.bumptech.glide.Glide.with(ctx)
                                                            .load(android.R.drawable.ic_menu_gallery)
                                                            .into(leftHolder.ivImageLeft);
                                                }
                                            }
                            targetView = leftHolder.ivImageLeft;
                        } else {
                            leftHolder.tvEmojiLeft.setVisibility(View.VISIBLE);
                            String emojiName = message.content != null ? message.content.replace("[", "").replace("]", "").replace("emoji:", "") : "";
                            leftHolder.tvEmojiLeft.setText("[" + emojiName + "]");
                            
                            // Load local resource if custom emoji loading failed
                            boolean loadedLocal = false;
                            
                            // fallback logic if you have custom drawable matching names
                            // if (emojiName.equals("为你加油") || emojiName.equals("加油")) {
                            //     leftHolder.tvEmojiLeft.setVisibility(View.GONE);
                            //     leftHolder.ivImageLeft.setVisibility(View.VISIBLE);
                            //     leftHolder.ivImageLeft.setImageResource(R.drawable.bg_badge); // Or placeholder if not available
                            //     targetView = leftHolder.ivImageLeft;
                            //     loadedLocal = true;
                            // }
                            
                            // Search Emoji database dynamically
                            if (!loadedLocal) {
                                new Thread(() -> {
                                    com.yoyo.jingxi.data.AppDatabase db = com.yoyo.jingxi.data.AppDatabase.getDatabase(leftHolder.itemView.getContext());
                                    java.util.List<com.yoyo.jingxi.data.entity.EmojiEntry> entries = db.emojiDao().getEmojiByNameSync(emojiName);
                                    
                                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                        if (entries != null && !entries.isEmpty() && entries.get(0).imageUrl != null && !entries.get(0).imageUrl.isEmpty()) {
                                            com.yoyo.jingxi.data.entity.EmojiEntry entry = entries.get(0);
                                            leftHolder.tvEmojiLeft.setVisibility(View.GONE);
                                            leftHolder.ivImageLeft.setVisibility(View.VISIBLE);
                                            leftHolder.ivImageLeft.setMaxWidth(300);
                                            leftHolder.ivImageLeft.setMaxHeight(300);
                                            try {
                                                if (entry.imageUrl.startsWith("data:image")) {
                                                    String base64 = entry.imageUrl.substring(entry.imageUrl.indexOf(",") + 1);
                                                    byte[] decodedString = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                                                    android.content.Context ctx = leftHolder.itemView.getContext();
                                                    boolean isValid = !(ctx instanceof android.app.Activity) || (!((android.app.Activity) ctx).isFinishing() && !((android.app.Activity) ctx).isDestroyed());
                                                    if (isValid) {
                                                        com.bumptech.glide.Glide.with(ctx)
                                                                .load(decodedString)
                                                                .error(android.R.drawable.ic_menu_gallery)
                                                                .into(leftHolder.ivImageLeft);
                                                    }
                                                } else {
                                                    android.content.Context ctx = leftHolder.itemView.getContext();
                                                    boolean isValid = !(ctx instanceof android.app.Activity) || (!((android.app.Activity) ctx).isFinishing() && !((android.app.Activity) ctx).isDestroyed());
                                                    if (isValid) {
                                                        com.bumptech.glide.Glide.with(ctx)
                                                                .load(entry.imageUrl)
                                                                .error(android.R.drawable.ic_menu_gallery)
                                                                .into(leftHolder.ivImageLeft);
                                                    }
                                                }
                                            } catch (Exception e) {
                                                android.content.Context ctx = leftHolder.itemView.getContext();
                                                boolean isValid = !(ctx instanceof android.app.Activity) || (!((android.app.Activity) ctx).isFinishing() && !((android.app.Activity) ctx).isDestroyed());
                                                if (isValid) {
                                                    com.bumptech.glide.Glide.with(ctx)
                                                            .load(android.R.drawable.ic_menu_gallery)
                                                            .into(leftHolder.ivImageLeft);
                                                }
                                            }
                                        } else {
                                            // Fallback to text if really no image
                                            leftHolder.tvEmojiLeft.setVisibility(View.VISIBLE);
                                            leftHolder.tvEmojiLeft.setText("[" + emojiName + "]");
                                            leftHolder.ivImageLeft.setVisibility(View.GONE);
                                        }
                                    });
                                }).start();
                                
                                targetView = leftHolder.tvEmojiLeft;
                            }
                        }
                        break;
                    case 3: // 真实图片
                        leftHolder.ivImageLeft.setVisibility(View.VISIBLE);
                        leftHolder.ivImageLeft.setMaxWidth(800);
                        leftHolder.ivImageLeft.setMaxHeight(800);
                        leftHolder.ivImageLeft.setOnClickListener(v -> {
                            android.content.Intent intent = new android.content.Intent(v.getContext(), com.yoyo.jingxi.ui.activity.ImageDetailActivity.class);
                            intent.putExtra("image_url", message.imageUrl);
                            v.getContext().startActivity(intent);
                        });
                        if (message.imageUrl != null) {
                            try {
                                if (message.imageUrl.startsWith("data:image")) {
                                    String base64 = message.imageUrl.substring(message.imageUrl.indexOf(",") + 1);
                                    byte[] decodedString = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                                    android.content.Context ctx = leftHolder.itemView.getContext();
                                    boolean isValid = !(ctx instanceof android.app.Activity) || (!((android.app.Activity) ctx).isFinishing() && !((android.app.Activity) ctx).isDestroyed());
                                    if (isValid) {
                                        com.bumptech.glide.Glide.with(ctx)
                                                .load(decodedString)
                                                .error(android.R.drawable.ic_menu_gallery)
                                                .into(leftHolder.ivImageLeft);
                                    }
                                } else {
                                    android.content.Context ctx = leftHolder.itemView.getContext();
                                    boolean isValid = !(ctx instanceof android.app.Activity) || (!((android.app.Activity) ctx).isFinishing() && !((android.app.Activity) ctx).isDestroyed());
                                    if (isValid) {
                                        com.bumptech.glide.Glide.with(ctx)
                                                .load(message.imageUrl)
                                                .error(android.R.drawable.ic_menu_gallery)
                                                .into(leftHolder.ivImageLeft);
                                    }
                                }
                            } catch (Exception e) {
                                android.content.Context ctx = leftHolder.itemView.getContext();
                                boolean isValid = !(ctx instanceof android.app.Activity) || (!((android.app.Activity) ctx).isFinishing() && !((android.app.Activity) ctx).isDestroyed());
                                if (isValid) {
                                    com.bumptech.glide.Glide.with(ctx)
                                            .load(android.R.drawable.ic_menu_gallery)
                                            .into(leftHolder.ivImageLeft);
                                }
                            }
                        }
                        targetView = leftHolder.ivImageLeft;
                        break;
                    case 4: // 虚拟图片
                        leftHolder.flVirtualImageLeft.setVisibility(View.VISIBLE);
                        leftHolder.flVirtualImageLeft.setOnClickListener(v -> {
                        android.content.Intent intent = new android.content.Intent(v.getContext(), com.yoyo.jingxi.ui.activity.ImageDetailActivity.class);
                        intent.putExtra("virtual_desc", message.imageDesc);
                        intent.putExtra("image_url", message.imageDesc != null ? "virtual://" + message.imageDesc : "");
                        intent.putExtra("message_id", message.id);
                        v.getContext().startActivity(intent);
                        });
                        targetView = leftHolder.flVirtualImageLeft;
                        break;
                    case 0: // 文本
                    default:
                        leftHolder.tvContentLeft.setVisibility(View.VISIBLE);
                        String displayText = message.content != null ? message.content : "";
                        if (!message.isFromUser) {
                            displayText = displayText.replaceAll("<#[0-9.]+?#>", "");
                            displayText = displayText.replaceAll("\\(laughs\\)", "");
                            displayText = displayText.replaceAll("\\(sighs\\)", "");
                            displayText = displayText.replaceAll("\\(clears throat\\)", "");
                            displayText = displayText.replaceAll("\\(sniffs\\)", "");
                            displayText = displayText.replaceAll("\\(cries\\)", "");
                            displayText = displayText.replaceAll("\\(yawns\\)", "");
                            displayText = displayText.replaceAll("\\(gasps\\)", "");
                            displayText = displayText.replaceAll("\\(swallows\\)", "");
                            
                            // Remove text within any remaining parentheses
                            displayText = displayText.replaceAll("\\([^)]*\\)", "");
                        }
                        leftHolder.tvContentLeft.setText(displayText.trim());
                        // 恢复默认样式
                        leftHolder.tvContentLeft.setBackgroundResource(R.drawable.bg_chat_bubble_left);
                        // Get color from theme
                        android.util.TypedValue typedValue2 = new android.util.TypedValue();
                        leftHolder.itemView.getContext().getTheme().resolveAttribute(R.attr.colorTextPrimary, typedValue2, true);
                        int textColor2 = typedValue2.data;
                        leftHolder.tvContentLeft.setTextColor(textColor2);
                        leftHolder.tvContentLeft.setTextSize(16);
                        targetView = leftHolder.tvContentLeft;
                        break;
                }
                
                targetView.setOnLongClickListener(v -> {
                    if (longClickListener != null) {
                        longClickListener.onMessageLongClick(message, v);
                    }
                    return true;
                });
            }
        }
    }

    @Override
    public int getItemCount() {
        return messages != null ? messages.size() : 0;
    }

    public static class LeftViewHolder extends RecyclerView.ViewHolder {
        public TextView tvTimestampLeft;
        android.widget.ImageView ivAvatarLeft;
        
        android.widget.FrameLayout flBubbleLeft;
        TextView tvContentLeft;
        android.widget.LinearLayout llVoiceLeft;
        android.widget.ImageView ivVoiceIconLeft;
        TextView tvVoiceDurationLeft;

        TextView tvVoiceLeft;
        TextView tvEmojiLeft;
        android.widget.ImageView ivImageLeft;
        View flVirtualImageLeft;
        View llQuoteLeft;
        TextView tvQuoteContentLeft;
        View llNormalContentLeft;
        TextView tvRevokeLeft;
        TextView tvSystemMessageLeft;
        
        View llVoiceContainerLeft;
        TextView tvVoiceToTextBtnLeft;
        TextView tvVoiceTextLeft;

        public LeftViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTimestampLeft = itemView.findViewById(R.id.tvTimestampLeft);
            ivAvatarLeft = itemView.findViewById(R.id.ivAvatarLeft);
            
            tvContentLeft = itemView.findViewById(R.id.tvContentLeft);

            tvVoiceLeft = itemView.findViewById(R.id.tvVoiceLeft);
            tvEmojiLeft = itemView.findViewById(R.id.tvEmojiLeft);
            ivImageLeft = itemView.findViewById(R.id.ivImageLeft);
            flVirtualImageLeft = itemView.findViewById(R.id.flVirtualImageLeft);
            llQuoteLeft = itemView.findViewById(R.id.llQuoteLeft);
            tvQuoteContentLeft = itemView.findViewById(R.id.tvQuoteContentLeft);
            llNormalContentLeft = itemView.findViewById(R.id.llNormalContentLeft);
            tvRevokeLeft = itemView.findViewById(R.id.tvRevokeLeft);
            tvSystemMessageLeft = itemView.findViewById(R.id.tvSystemMessageLeft);
            llVoiceContainerLeft = itemView.findViewById(R.id.llVoiceContainerLeft);
            tvVoiceToTextBtnLeft = itemView.findViewById(R.id.tvVoiceToTextBtnLeft);
            tvVoiceTextLeft = itemView.findViewById(R.id.tvVoiceTextLeft);
        }
    }

    public static class RightViewHolder extends RecyclerView.ViewHolder {
        public TextView tvTimestampRight;
        
        TextView tvContentRight;
        
        View llVoiceContainerRight;
        TextView tvVoiceToTextBtnRight;
        TextView tvVoiceRight;
        TextView tvVoiceTextRight;
        
        android.widget.ImageView ivImageRight;
        View flVirtualImageRight;
        View llQuoteRight;
        TextView tvQuoteContentRight;
        View llNormalContentRight;
        TextView tvRevokeRight;
        TextView tvSystemMessageRight;
        android.widget.ImageView ivAvatarRight;
        
        public RightViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTimestampRight = itemView.findViewById(R.id.tvTimestampRight);
            
            tvContentRight = itemView.findViewById(R.id.tvContentRight);
            
            llVoiceContainerRight = itemView.findViewById(R.id.llVoiceContainerRight);
            tvVoiceToTextBtnRight = itemView.findViewById(R.id.tvVoiceToTextBtnRight);
            tvVoiceRight = itemView.findViewById(R.id.tvVoiceRight);
            tvVoiceTextRight = itemView.findViewById(R.id.tvVoiceTextRight);
            
            ivImageRight = itemView.findViewById(R.id.ivImageRight);
            flVirtualImageRight = itemView.findViewById(R.id.flVirtualImageRight);
            llQuoteRight = itemView.findViewById(R.id.llQuoteRight);
            tvQuoteContentRight = itemView.findViewById(R.id.tvQuoteContentRight);
            llNormalContentRight = itemView.findViewById(R.id.llNormalContentRight);
            tvRevokeRight = itemView.findViewById(R.id.tvRevokeRight);
            tvSystemMessageRight = itemView.findViewById(R.id.tvSystemMessageRight);
            ivAvatarRight = itemView.findViewById(R.id.ivAvatarRight);
        }
    }
}