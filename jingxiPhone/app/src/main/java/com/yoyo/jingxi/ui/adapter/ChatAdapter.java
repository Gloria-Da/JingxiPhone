package com.yoyo.jingxi.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.Message;
import com.yoyo.jingxi.data.entity.SharedContent;
import com.yoyo.jingxi.ui.widget.ForwardCardView;
import com.yoyo.jingxi.ui.widget.PhotoStackView;
import com.yoyo.jingxi.ui.widget.SharedContentCardView;
import com.yoyo.jingxi.utils.VoiceGenerateHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_LEFT = 0;
    private static final int VIEW_TYPE_RIGHT = 1;
    private static final int MESSAGE_TYPE_SHARED_CONTENT = 7;
    private static final int MESSAGE_TYPE_FORWARD_CARD = 8;
    private static final int MESSAGE_TYPE_MULTI_IMAGE = 5; // 合并多图 + PhotoStackView

    // 多选模式状态
    private boolean isMultiSelectMode = false;
    private Set<Integer> selectedMessageIds = new HashSet<>();
    private Map<Integer, SharedContent> sharedContentCache = new HashMap<>();

    private static final DiffUtil.ItemCallback<Message> DIFF_CALLBACK = new DiffUtil.ItemCallback<Message>() {
        @Override
        public boolean areItemsTheSame(Message oldItem, Message newItem) {
            return oldItem.id == newItem.id;
        }
        @Override
        public boolean areContentsTheSame(Message oldItem, Message newItem) {
            // Compare all fields that affect visual output
            if (oldItem.type != newItem.type) return false;
            if (oldItem.isFromUser != newItem.isFromUser) return false;
            if (oldItem.timestamp != newItem.timestamp) return false;
            if (oldItem.quoteMessageId != newItem.quoteMessageId) return false;
            if (oldItem.content != null ? !oldItem.content.equals(newItem.content) : newItem.content != null) return false;
            if (oldItem.imageUrl != null ? !oldItem.imageUrl.equals(newItem.imageUrl) : newItem.imageUrl != null) return false;
            if (oldItem.imageDesc != null ? !oldItem.imageDesc.equals(newItem.imageDesc) : newItem.imageDesc != null) return false;
            if (oldItem.voiceUrl != null ? !oldItem.voiceUrl.equals(newItem.voiceUrl) : newItem.voiceUrl != null) return false;
            return true;
        }
    };

    private final AsyncListDiffer<Message> differ = new AsyncListDiffer<>(this, DIFF_CALLBACK);
    private String friendName;

    public ChatAdapter() {
        setHasStableIds(true);
        animateAfterTimestamp = System.currentTimeMillis() + 500;
    }

    public ChatAdapter(String friendName) {
        this.friendName = friendName;
        setHasStableIds(true);
        animateAfterTimestamp = System.currentTimeMillis() + 500;
    }

    @Override
    public void onAttachedToRecyclerView(@androidx.annotation.NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        recyclerView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
    }

    @Override
    public long getItemId(int position) {
        Message msg = differ.getCurrentList().get(position);
        return msg.id;
    }

    public void setMessages(List<Message> messages) {
        setMessages(messages, null);
    }

    public void setMessages(List<Message> messages, Runnable onCommitted) {
        differ.submitList(messages != null ? new ArrayList<>(messages) : null, () -> {
            if (onCommitted != null) {
                onCommitted.run();
            }
        });
    }

    public List<Message> getCurrentMessages() {
        return differ.getCurrentList();
    }

    /** 直接更新内存中的消息并通知 RecyclerView 刷新——用于即时 UI 反馈，DB 写入异步进行 */
    public void updateMessageImageUrl(int msgId, String newUrl, int newType) {
        List<Message> list = differ.getCurrentList();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == msgId) {
                list.get(i).type = newType;
                list.get(i).imageUrl = newUrl;
                notifyItemChanged(i);
                return;
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        Message message = differ.getCurrentList().get(position);
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

    public interface OnMessageSelectListener {
        void onMessageSelectChanged(Message msg, boolean isSelected);
    }

    private OnMessageSelectListener selectListener;

    public void setOnMessageSelectListener(OnMessageSelectListener listener) {
        this.selectListener = listener;
    }

    /**
     * 切换多选模式
     */
    public void setMultiSelectMode(boolean enabled, Set<Integer> selectedIds) {
        this.isMultiSelectMode = enabled;
        this.selectedMessageIds = selectedIds != null ? selectedIds : new HashSet<>();
        notifyDataSetChanged();
    }

    public boolean isMultiSelectMode() {
        return isMultiSelectMode;
    }

    /** 多图展开态中长按的单图索引（-1 = 整条消息操作），菜单消费后应重置为 -1 */
    public int consumePendingSingleImageIndex() {
        int idx = pendingSingleImageIndex;
        pendingSingleImageIndex = -1;
        return idx;
    }

    public Set<Integer> getSelectedMessageIds() {
        return selectedMessageIds;
    }

    /**
     * 预加载分享内容缓存，供adapter在绑定type=7消息时使用
     */
    public void prefetchSharedContent(List<SharedContent> contents) {
        // 不调用 clear() 以避免并发的 onBindViewHolder 缓存未命中；
        // 不调用 notifyDataSetChanged() —— AsyncListDiffer 已通过 setMessages() 精准派发 insert/change 通知，
        // 强制全量重绑会导致所有可见 item 的 visibility 切换触发不必要的 LayoutTransition 动画。
        if (contents != null) {
            for (SharedContent sc : contents) {
                sharedContentCache.put(sc.messageId, sc);
            }
        }
    }

    public void addSharedContentToCache(SharedContent sc) {
        if (sc != null) {
            sharedContentCache.put(sc.messageId, sc);
            // 触发对应 item 重新绑定，立即显示卡片内容
            List<Message> curList = differ.getCurrentList();
            for (int i = 0; i < curList.size(); i++) {
                if (curList.get(i).id == sc.messageId) {
                    notifyItemChanged(i);
                    break;
                }
            }
        }
    }

    // 心声相关
    private java.util.Map<Integer, Boolean> innerVoiceMap = new java.util.HashMap<>();

    public void setInnerVoiceMessageIds(java.util.Set<Integer> messageIds) {
        // 计算心声状态实际发生变化的 messageId 集合
        java.util.Set<Integer> changedIds = new java.util.HashSet<>();
        for (int id : messageIds) {
            if (!innerVoiceMap.containsKey(id)) {
                changedIds.add(id); // 新增有心声
            }
        }
        for (int id : innerVoiceMap.keySet()) {
            if (!messageIds.contains(id)) {
                changedIds.add(id); // 心声被移除
            }
        }

        // 更新缓存
        innerVoiceMap.clear();
        for (int id : messageIds) {
            innerVoiceMap.put(id, true);
        }

        // 只通知状态真正变化的 position
        List<Message> currentList = differ.getCurrentList();
        for (int i = 0; i < currentList.size(); i++) {
            if (changedIds.contains(currentList.get(i).id)) {
                notifyItemChanged(i);
            }
        }
    }

    public void removeInnerVoiceMessageId(int messageId) {
        if (innerVoiceMap.containsKey(messageId)) {
            innerVoiceMap.remove(messageId);
            // DiffUtil 需要精确通知——查找该 messageId 在列表中的位置
            List<Message> currentList = differ.getCurrentList();
            for (int i = 0; i < currentList.size(); i++) {
                if (currentList.get(i).id == messageId) {
                    notifyItemChanged(i);
                    return;
                }
            }
            // 找不到就 fallback
            notifyDataSetChanged();
        }
    }

    // 语音转文字展开状态（跨刷新持久化）
    private java.util.Set<Integer> expandedVoiceMessages = new java.util.HashSet<>();

    // 多图消息展开状态
    private final java.util.Set<Integer> expandedMultiImageMessages = new java.util.HashSet<>();

    /** 清除指定消息的展开状态（多图降级为单图或删除时调用，防止状态泄漏） */
    public void clearMultiImageExpandState(int msgId) {
        expandedMultiImageMessages.remove(msgId);
    }

    private Message currentBindMessage; // 当前正在绑定的消息，供 populateExpandedImages 长按使用
    private int pendingSingleImageIndex = -1; // 多图展开态中长按的单图索引，-1 表示整条消息操作

    // 入场动画：已动画过的消息 ID，避免重复播放
    private final java.util.Set<Integer> animatedMessageIds = new java.util.HashSet<>();
    // 时间戳阈值：只有 timestamp 在此时间之后的消息才播放入场动画
    // adapter 创建时设为当前时间+500ms，确保历史消息不动画，新回复才动画
    // 重回聊天时 adapter 重建，阈值更新，历史消息依然被排除
    private final long animateAfterTimestamp;

    public interface OnAvatarLongClickListener {
        void onAvatarLongClick(Message msg, View avatarView);
    }
    private OnAvatarLongClickListener avatarLongClickListener;

    public void setOnAvatarLongClickListener(OnAvatarLongClickListener listener) {
        this.avatarLongClickListener = listener;
    }

    private Message getMessageById(int messageId) {
        for (Message msg : differ.getCurrentList()) {
            if (msg.id == messageId) {
                return msg;
            }
        }
        return null;
    }

    /** 收集当前对话中所有图片 URL，用于详情页左右滑动浏览 */
    private java.util.ArrayList<String> collectImageUrls(Message currentMsg) {
        java.util.ArrayList<String> urls = new java.util.ArrayList<>();
        int targetIdx = 0;
        int idx = 0;
        for (Message msg : differ.getCurrentList()) {
            if (msg.type == 3 || msg.type == 4) {
                String url = msg.imageUrl;
                // type=4 虚拟图片在生成完成前 imageUrl 为空，提示词在 imageDesc 中
                if ((url == null || url.isEmpty()) && msg.type == 4
                        && msg.imageDesc != null && !msg.imageDesc.isEmpty()) {
                    url = msg.imageDesc.startsWith("error://")
                            ? msg.imageDesc
                            : "virtual://" + msg.imageDesc;
                }
                if (url != null && !url.isEmpty()) {
                    urls.add(url);
                    if (msg.id == currentMsg.id) targetIdx = idx;
                    idx++;
                }
            } else if (msg.type == MESSAGE_TYPE_MULTI_IMAGE) {
                // type=5 合并多图，拆分每张图
                if (msg.imageUrl != null && !msg.imageUrl.isEmpty()) {
                    String[] parts = msg.imageUrl.split(",");
                    for (String part : parts) {
                        if (!part.isEmpty()) {
                            urls.add(part);
                            if (msg.id == currentMsg.id) targetIdx = idx;
                            idx++;
                        }
                    }
                }
            }
        }
        // 把 targetIdx 存到 urls 的第一个位置？不，我们用 intent extra 传 start_index
        // 这里只是收集 urls，在调用方传 start_index
        return urls;
    }

    /** 在 urlList 中找到 currentMsg 对应图片的索引 */
    private int findImageIndex(java.util.ArrayList<String> urls, String targetUrl) {
        if (targetUrl == null) return 0;
        for (int i = 0; i < urls.size(); i++) {
            if (targetUrl.equals(urls.get(i))) return i;
        }
        return 0;
    }

    /** 打开图片详情，自动附带当前对话全部图片 URL 列表用于左右滑动 */
    private void launchImageDetail(View view, String imageUrl, Message message) {
        android.content.Intent intent = new android.content.Intent(view.getContext(), com.yoyo.jingxi.ui.activity.ImageDetailActivity.class);
        intent.putExtra("image_url", imageUrl != null ? imageUrl : "");
        java.util.ArrayList<String> allUrls = collectImageUrls(message);
        intent.putStringArrayListExtra("image_urls", allUrls);
        intent.putExtra("start_index", findImageIndex(allUrls, imageUrl));
        view.getContext().startActivity(intent);
    }

    private String characterAvatarPath;
    private String myAvatarPath;

    // 语音补生成相关
    private VoiceGenerateHelper voiceGenerateHelper;
    private AppDatabase db;
    private ExecutorService dbExecutor;

    public void setVoiceGenerateHelper(VoiceGenerateHelper helper) {
        this.voiceGenerateHelper = helper;
    }

    public void setDb(AppDatabase db) {
        this.db = db;
    }

    public void setDbExecutor(ExecutorService executor) {
        this.dbExecutor = executor;
    }

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

    private void generateAndPlay(Message msg, android.content.Context context) {
        if (voiceGenerateHelper == null || db == null) {
            android.widget.Toast.makeText(context, "无法生成语音", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        if (msg.characterId <= 0) {
            android.widget.Toast.makeText(context, "无法确定角色信息", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        voiceGenerateHelper.generateVoiceAsync(msg.characterId, msg.content, null,
                new VoiceGenerateHelper.GenerateCallback() {
                    @Override
                    public void onSuccess(String audioFilePath) {
                        // 更新 Message 的 voiceUrl 并回写 DB
                        msg.voiceUrl = audioFilePath;
                        if (dbExecutor != null) {
                            dbExecutor.execute(() -> db.messageDao().update(msg));
                        }
                        // 播放
                        playAudio(context, audioFilePath);
                    }

                    @Override
                    public void onError(String reason) {
                        android.widget.Toast.makeText(context, reason, android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * 展开/收起多图消息。用 ValueAnimator 做 clip 高度动画，干净顺滑。
     * 先 popoulate 图片再开启动画，Glide 提前加载无卡顿。
     */
    private void toggleMultiImageExpand(PhotoStackView photoStack, LinearLayout expandedContainer,
                                         String[] urls, java.util.ArrayList<String> urlArrayList,
                                         int imgW, float density, int messageId, android.content.Context ctx) {
        boolean currentlyExpanded = expandedMultiImageMessages.contains(messageId);
        if (currentlyExpanded) {
            // 收起：高度 → 0
            expandedMultiImageMessages.remove(messageId);
            if (expandedContainer != null) {
                int fromH = expandedContainer.getHeight();
                android.animation.ValueAnimator collapse = android.animation.ValueAnimator.ofInt(fromH, 0);
                collapse.setDuration(250);
                collapse.setInterpolator(new android.view.animation.DecelerateInterpolator());
                collapse.addUpdateListener(va -> {
                    expandedContainer.getLayoutParams().height = (int) va.getAnimatedValue();
                    expandedContainer.requestLayout();
                });
                collapse.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(android.animation.Animator a) {
                        expandedContainer.setVisibility(View.GONE);
                        expandedContainer.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                        if (photoStack != null) {
                            photoStack.setImages(urlArrayList);
                            photoStack.setVisibility(View.VISIBLE);
                        }
                        // 触发 rebind 更新按钮文字和 PhotoStack 状态
                        java.util.List<Message> curList = differ.getCurrentList();
                        for (int i = 0; i < curList.size(); i++) {
                            if (curList.get(i).id == messageId) {
                                notifyItemChanged(i);
                                break;
                            }
                        }
                    }
                });
                collapse.start();
            }
        } else {
            // 展开：高度 0 → full
            expandedMultiImageMessages.add(messageId);
            if (expandedContainer != null) {
                // 预构建图片列表（Glide 提前加载），图片数量变化时重建
                if (expandedContainer.getChildCount() != urls.length) {
                    Message msg = findMessageById(messageId);
                    populateExpandedImages(expandedContainer, urls, urlArrayList, imgW, density, ctx, msg);
                }
                // 测量目标高度
                int parentW = ((View) expandedContainer.getParent()).getWidth();
                if (parentW == 0) parentW = ctx.getResources().getDisplayMetrics().widthPixels;
                int wSpec = View.MeasureSpec.makeMeasureSpec(parentW, View.MeasureSpec.AT_MOST);
                int hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                expandedContainer.measure(wSpec, hSpec);
                int targetH = expandedContainer.getMeasuredHeight();

                expandedContainer.getLayoutParams().height = 0;
                expandedContainer.setVisibility(View.VISIBLE);
                if (photoStack != null) photoStack.setVisibility(View.GONE);

                android.animation.ValueAnimator expand = android.animation.ValueAnimator.ofInt(0, targetH);
                expand.setDuration(300);
                expand.setInterpolator(new android.view.animation.DecelerateInterpolator());
                expand.addUpdateListener(va -> {
                    expandedContainer.getLayoutParams().height = (int) va.getAnimatedValue();
                    expandedContainer.requestLayout();
                });
                expand.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(android.animation.Animator a) {
                        expandedContainer.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                        expandedContainer.requestLayout();
                        // 触发 rebind 更新按钮文字为"▲ 收起"
                        java.util.List<Message> curList = differ.getCurrentList();
                        for (int i = 0; i < curList.size(); i++) {
                            if (curList.get(i).id == messageId) {
                                notifyItemChanged(i);
                                break;
                            }
                        }
                    }
                });
                expand.start();
            }
        }
    }

    private Message findMessageById(int id) {
        for (Message m : differ.getCurrentList()) {
            if (m.id == id) return m;
        }
        return null;
    }

    /** 构建展开态的图片列表（带点击进入大图+长按菜单），仅首次调用时创建 View */
    private void populateExpandedImages(LinearLayout container, String[] urls,
                                         java.util.ArrayList<String> urlArrayList,
                                         int imgW, float density, android.content.Context ctx,
                                         Message bindMsg) {
        container.removeAllViews();
        for (int i = 0; i < urls.length; i++) {
            ImageView iv = new ImageView(ctx);
            iv.setAdjustViewBounds(true);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setMaxHeight((int) (300 * density));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(imgW, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = (int) (4 * density);
            iv.setLayoutParams(lp);
            iv.setBackgroundResource(R.drawable.bg_chat_image_rounded);
            iv.setClipToOutline(true);
            final int idx = i;
            iv.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(v.getContext(), com.yoyo.jingxi.ui.activity.ImageDetailActivity.class);
                intent.putExtra("image_url", urls[idx]);
                v.getContext().startActivity(intent);
            });
            iv.setOnLongClickListener(v -> {
                pendingSingleImageIndex = idx; // 标记为单图操作
                if (longClickListener != null) longClickListener.onMessageLongClick(bindMsg, v);
                return true;
            });
            container.addView(iv);
            boolean valid = !(ctx instanceof android.app.Activity) || (!((android.app.Activity) ctx).isFinishing() && !((android.app.Activity) ctx).isDestroyed());
            if (valid) {
                com.bumptech.glide.Glide.with(ctx).load(urls[i]).error(android.R.drawable.ic_menu_gallery).into(iv);
            }
        }
    }

    /**
     * 清理语音文本中的语态标签和括号内文字，提取纯文本用于转文字显示。
     * 委托到 AiReplyHelper.cleanNotificationText() 统一维护标签列表。
     */
    private String getCleanVoiceText(String content) {
        return com.yoyo.jingxi.utils.AiReplyHelper.cleanNotificationText(content);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        List<Message> currentMessages = differ.getCurrentList();
        Message message = currentMessages.get(position);
        currentBindMessage = message;

        // 多选模式：设置CheckBox和点击行为
        View.OnClickListener multiSelectClickListener = null;
        if (isMultiSelectMode && message.type != 99) {
            multiSelectClickListener = v -> {
                if (selectedMessageIds.contains(message.id)) {
                    selectedMessageIds.remove(message.id);
                } else {
                    selectedMessageIds.add(message.id);
                }
                // 精确刷新当前项（通过 message id 定位，避免 position 过期）
                List<Message> curList = differ.getCurrentList();
                for (int i = 0; i < curList.size(); i++) {
                    if (curList.get(i).id == message.id) {
                        notifyItemChanged(i);
                        break;
                    }
                }
                if (selectListener != null) {
                    selectListener.onMessageSelectChanged(message, selectedMessageIds.contains(message.id));
                }
            };
        }

        // 显示时间戳逻辑（距离上一条超过 5 分钟显示）
        boolean showTimestamp = false;
        if (position == 0) {
            showTimestamp = true;
        } else {
            Message prevMessage = currentMessages.get(position - 1);
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

            // 多选CheckBox + 点击行为
            if (rightHolder.cbSelectRight != null) {
                if (isMultiSelectMode) {
                    rightHolder.cbSelectRight.setVisibility(View.VISIBLE);
                    rightHolder.cbSelectRight.setChecked(selectedMessageIds.contains(message.id));
                    rightHolder.itemView.setOnClickListener(multiSelectClickListener);
                    // 禁用长按菜单中的其他操作
                    rightHolder.itemView.setOnLongClickListener(null);
                } else {
                    rightHolder.cbSelectRight.setVisibility(View.GONE);
                    rightHolder.itemView.setOnClickListener(null);
                }
            }
            // 分享内容卡片 + 合并转发卡片
            if (rightHolder.sharedContentCardRight != null) {
                rightHolder.sharedContentCardRight.setVisibility(message.type == MESSAGE_TYPE_SHARED_CONTENT ? View.VISIBLE : View.GONE);
            }
            if (rightHolder.forwardCardRight != null) {
                rightHolder.forwardCardRight.setVisibility(message.type == MESSAGE_TYPE_FORWARD_CARD ? View.VISIBLE : View.GONE);
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
            if (rightHolder.photoStackRight != null) rightHolder.photoStackRight.setVisibility(View.GONE);
            if (rightHolder.tvExpandToggleRight != null) rightHolder.tvExpandToggleRight.setVisibility(View.GONE);
            if (rightHolder.llExpandedImagesRight != null) rightHolder.llExpandedImagesRight.setVisibility(View.GONE);
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
                                if (expandedVoiceMessages.contains(message.id)) {
                                    expandedVoiceMessages.remove(message.id);
                                    rightHolder.tvVoiceTextRight.setVisibility(View.GONE);
                                } else {
                                    expandedVoiceMessages.add(message.id);
                                    rightHolder.tvVoiceTextRight.setVisibility(View.VISIBLE);
                                    rightHolder.tvVoiceTextRight.setText(getCleanVoiceText(message.content));
                                }
                            });
                        }

                        // 恢复之前展开的语音转文字状态
                        if (expandedVoiceMessages.contains(message.id)) {
                            rightHolder.tvVoiceTextRight.setVisibility(View.VISIBLE);
                            rightHolder.tvVoiceTextRight.setText(getCleanVoiceText(message.content));
                        }
                    }
                    
                    targetView = rightHolder.tvVoiceRight;
                } else if (message.type == 3 || message.type == 2) {
                    // 真实图片或自定义表情
                    rightHolder.ivImageRight.setVisibility(View.VISIBLE);
                    if (message.type == 3) {
                        // 真实图片可点击查看大图
                        rightHolder.ivImageRight.setOnClickListener(v -> launchImageDetail(v, message.imageUrl, message));
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
                    // 错误状态下降低透明度以示区分
                    if (message.imageDesc != null && message.imageDesc.startsWith("error://")) {
                        rightHolder.flVirtualImageRight.setAlpha(0.5f);
                    } else {
                        rightHolder.flVirtualImageRight.setAlpha(1.0f);
                    }
                    rightHolder.flVirtualImageRight.setOnClickListener(v -> {
                        android.content.Intent intent = new android.content.Intent(v.getContext(), com.yoyo.jingxi.ui.activity.ImageDetailActivity.class);
                        intent.putExtra("virtual_desc", message.imageDesc);
                        String imgUrl = (message.imageDesc != null && message.imageDesc.startsWith("error://"))
                                ? message.imageDesc : (message.imageDesc != null ? "virtual://" + message.imageDesc : "");
                        intent.putExtra("image_url", imgUrl);
                        intent.putExtra("message_id", message.id);
                        java.util.ArrayList<String> allUrls = collectImageUrls(message);
                        intent.putStringArrayListExtra("image_urls", allUrls);
                        intent.putExtra("start_index", findImageIndex(allUrls, imgUrl));
                        v.getContext().startActivity(intent);
                    });
                    targetView = rightHolder.flVirtualImageRight;
                } else if (message.type == MESSAGE_TYPE_MULTI_IMAGE) {
                    // type 5: 合并多图 → PhotoStackView + 展开/收起（平滑动画）
                    rightHolder.tvContentRight.setVisibility(View.GONE);
                    if (message.imageUrl != null) {
                        String[] urls = message.imageUrl.split(",");
                        java.util.ArrayList<String> urlArrayList = new java.util.ArrayList<>(java.util.Arrays.asList(urls));
                        boolean expanded = expandedMultiImageMessages.contains(message.id);
                        float density = rightHolder.itemView.getContext().getResources().getDisplayMetrics().density;
                        int imgW = (int) (200 * density);

                        // 展开/收起按钮
                        if (rightHolder.tvExpandToggleRight != null) {
                            rightHolder.tvExpandToggleRight.setVisibility(View.VISIBLE);
                            rightHolder.tvExpandToggleRight.setText(expanded ? "▲ 收起" : "▼ 展开全部");
                            rightHolder.tvExpandToggleRight.setOnClickListener(v -> toggleMultiImageExpand(
                                    rightHolder.photoStackRight, rightHolder.llExpandedImagesRight,
                                    urls, urlArrayList, imgW, density, message.id, rightHolder.itemView.getContext()));
                        }

                        // 预构建展开图片（Glide 提前加载），图片数量变化时重建
                        if (rightHolder.llExpandedImagesRight != null && rightHolder.llExpandedImagesRight.getChildCount() != urls.length) {
                            populateExpandedImages(rightHolder.llExpandedImagesRight, urls, urlArrayList, imgW, density, rightHolder.itemView.getContext(), message);
                        }
                        if (expanded) {
                            if (rightHolder.photoStackRight != null)
                                rightHolder.photoStackRight.setVisibility(View.GONE);
                            if (rightHolder.llExpandedImagesRight != null) {
                                rightHolder.llExpandedImagesRight.setVisibility(View.VISIBLE);
                                rightHolder.llExpandedImagesRight.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                            }
                            targetView = rightHolder.llExpandedImagesRight;
                        } else {
                            if (rightHolder.llExpandedImagesRight != null)
                                rightHolder.llExpandedImagesRight.setVisibility(View.GONE);
                            if (rightHolder.photoStackRight != null) {
                                rightHolder.photoStackRight.setVisibility(View.VISIBLE);
                                rightHolder.photoStackRight.setAlpha(1f);
                                rightHolder.photoStackRight.setImages(urlArrayList);
                                rightHolder.photoStackRight.setOnTapListener(index -> {
                                    if (index >= 0 && index < urls.length) {
                                        launchImageDetail(rightHolder.itemView, urls[index], message);
                                    }
                                });
                            }
                            targetView = rightHolder.photoStackRight;
                        }
                    }
                } else if (message.type == MESSAGE_TYPE_SHARED_CONTENT) {
                    // type 7: 分享内容卡片
                    rightHolder.tvContentRight.setVisibility(View.GONE);
                    if (rightHolder.sharedContentCardRight != null) {
                        rightHolder.sharedContentCardRight.setVisibility(View.VISIBLE);
                        SharedContent sc = sharedContentCache.get(message.id);
                        if (sc != null) {
                            rightHolder.sharedContentCardRight.setSharedContent(sc);
                        } else if (db != null && dbExecutor != null) {
                            // 缓存未命中，从DB加载
                            dbExecutor.execute(() -> {
                                SharedContent dbSc = db.sharedContentDao().getByMessageId(message.id);
                                if (dbSc != null) {
                                    sharedContentCache.put(message.id, dbSc);
                                    rightHolder.sharedContentCardRight.post(() ->
                                            rightHolder.sharedContentCardRight.setSharedContent(dbSc));
                                }
                            });
                        }
                    }
                    targetView = rightHolder.sharedContentCardRight;
                } else if (message.type == MESSAGE_TYPE_FORWARD_CARD) {
                    // type 8: 合并转发卡片
                    rightHolder.tvContentRight.setVisibility(View.GONE);
                    if (rightHolder.forwardCardRight != null) {
                        rightHolder.forwardCardRight.setVisibility(View.VISIBLE);
                        rightHolder.forwardCardRight.setForwardData(message.content);
                    }
                    targetView = rightHolder.forwardCardRight;
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

            // 多选CheckBox + 点击行为
            if (leftHolder.cbSelectLeft != null) {
                if (isMultiSelectMode) {
                    leftHolder.cbSelectLeft.setVisibility(View.VISIBLE);
                    leftHolder.cbSelectLeft.setChecked(selectedMessageIds.contains(message.id));
                    leftHolder.itemView.setOnClickListener(multiSelectClickListener);
                    leftHolder.itemView.setOnLongClickListener(null);
                } else {
                    leftHolder.cbSelectLeft.setVisibility(View.GONE);
                    leftHolder.itemView.setOnClickListener(null);
                }
            }
            // 分享内容卡片 + 合并转发卡片
            if (leftHolder.sharedContentCardLeft != null) {
                leftHolder.sharedContentCardLeft.setVisibility(message.type == MESSAGE_TYPE_SHARED_CONTENT ? View.VISIBLE : View.GONE);
            }
            if (leftHolder.forwardCardLeft != null) {
                leftHolder.forwardCardLeft.setVisibility(message.type == MESSAGE_TYPE_FORWARD_CARD ? View.VISIBLE : View.GONE);
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
            if (leftHolder.photoStackLeft != null) leftHolder.photoStackLeft.setVisibility(View.GONE);
            if (leftHolder.tvExpandToggleLeft != null) leftHolder.tvExpandToggleLeft.setVisibility(View.GONE);
            if (leftHolder.llExpandedImagesLeft != null) leftHolder.llExpandedImagesLeft.setVisibility(View.GONE);

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

            // 心声红点
            if (innerVoiceMap.containsKey(message.id)) {
                leftHolder.vInnerVoiceDotLeft.setVisibility(View.VISIBLE);
            } else {
                leftHolder.vInnerVoiceDotLeft.setVisibility(View.GONE);
            }

            // 头像长按 → 查看心声
            leftHolder.ivAvatarLeft.setOnLongClickListener(v -> {
                if (avatarLongClickListener != null) {
                    avatarLongClickListener.onAvatarLongClick(message, v);
                }
                return true;
            });

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
                    if (expandedVoiceMessages.contains(message.id)) {
                        expandedVoiceMessages.remove(message.id);
                        leftHolder.tvVoiceTextLeft.setVisibility(View.GONE);
                    } else {
                        expandedVoiceMessages.add(message.id);
                        leftHolder.tvVoiceTextLeft.setVisibility(View.VISIBLE);
                        leftHolder.tvVoiceTextLeft.setText(getCleanVoiceText(message.content));
                    }
                });

                // 点击语音条的简单提示
                leftHolder.tvVoiceLeft.setOnClickListener(v -> {
                    if (message.voiceUrl != null && !message.voiceUrl.isEmpty()) {
                        playAudio(v.getContext(), message.voiceUrl);
                    } else {
                        // 尝试补生成
                        generateAndPlay(message, v.getContext());
                    }
                });
                // 恢复之前展开的语音转文字状态
                if (expandedVoiceMessages.contains(message.id)) {
                    leftHolder.tvVoiceTextLeft.setVisibility(View.VISIBLE);
                    leftHolder.tvVoiceTextLeft.setText(getCleanVoiceText(message.content));
                }

                targetView = leftHolder.tvVoiceLeft;
                break;
                    case 2: // 表情
                        if (message.imageUrl != null && !message.imageUrl.isEmpty()) {
                            leftHolder.ivImageLeft.setVisibility(View.VISIBLE);
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
                        leftHolder.ivImageLeft.setOnClickListener(v -> launchImageDetail(v, message.imageUrl, message));
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
                        // 错误状态下降低透明度以示区分
                        if (message.imageDesc != null && message.imageDesc.startsWith("error://")) {
                            leftHolder.flVirtualImageLeft.setAlpha(0.5f);
                        } else {
                            leftHolder.flVirtualImageLeft.setAlpha(1.0f);
                        }
                        leftHolder.flVirtualImageLeft.setOnClickListener(v -> {
                        android.content.Intent intent = new android.content.Intent(v.getContext(), com.yoyo.jingxi.ui.activity.ImageDetailActivity.class);
                        intent.putExtra("virtual_desc", message.imageDesc);
                        String imgUrl = (message.imageDesc != null && message.imageDesc.startsWith("error://"))
                                ? message.imageDesc : (message.imageDesc != null ? "virtual://" + message.imageDesc : "");
                        intent.putExtra("image_url", imgUrl);
                        intent.putExtra("message_id", message.id);
                        java.util.ArrayList<String> allUrls = collectImageUrls(message);
                        intent.putStringArrayListExtra("image_urls", allUrls);
                        intent.putExtra("start_index", findImageIndex(allUrls, imgUrl));
                        v.getContext().startActivity(intent);
                        });
                        targetView = leftHolder.flVirtualImageLeft;
                        break;
                    case MESSAGE_TYPE_MULTI_IMAGE: // 合并多图 → PhotoStackView + 展开/收起（平滑动画）
                        if (message.imageUrl != null) {
                            String[] urls = message.imageUrl.split(",");
                            java.util.ArrayList<String> urlArrayList = new java.util.ArrayList<>(java.util.Arrays.asList(urls));
                            boolean expanded = expandedMultiImageMessages.contains(message.id);
                            float density = leftHolder.itemView.getContext().getResources().getDisplayMetrics().density;
                            int imgW = (int) (200 * density);

                            if (leftHolder.tvExpandToggleLeft != null) {
                                leftHolder.tvExpandToggleLeft.setVisibility(View.VISIBLE);
                                leftHolder.tvExpandToggleLeft.setText(expanded ? "▲ 收起" : "▼ 展开全部");
                                leftHolder.tvExpandToggleLeft.setOnClickListener(v -> toggleMultiImageExpand(
                                        leftHolder.photoStackLeft, leftHolder.llExpandedImagesLeft,
                                        urls, urlArrayList, imgW, density, message.id, leftHolder.itemView.getContext()));
                            }

                            // 预构建展开图片（Glide 提前加载），图片数量变化时重建
                            if (leftHolder.llExpandedImagesLeft != null && leftHolder.llExpandedImagesLeft.getChildCount() != urls.length) {
                                populateExpandedImages(leftHolder.llExpandedImagesLeft, urls, urlArrayList, imgW, density, leftHolder.itemView.getContext(), message);
                            }
                            if (expanded) {
                                if (leftHolder.photoStackLeft != null)
                                    leftHolder.photoStackLeft.setVisibility(View.GONE);
                                if (leftHolder.llExpandedImagesLeft != null) {
                                    leftHolder.llExpandedImagesLeft.setVisibility(View.VISIBLE);
                                    leftHolder.llExpandedImagesLeft.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                                }
                                targetView = leftHolder.llExpandedImagesLeft;
                            } else {
                                if (leftHolder.llExpandedImagesLeft != null)
                                    leftHolder.llExpandedImagesLeft.setVisibility(View.GONE);
                                if (leftHolder.photoStackLeft != null) {
                                    leftHolder.photoStackLeft.setVisibility(View.VISIBLE);
                                    leftHolder.photoStackLeft.setAlpha(1f);
                                    leftHolder.photoStackLeft.setImages(urlArrayList);
                                    leftHolder.photoStackLeft.setOnTapListener(index -> {
                                        if (index >= 0 && index < urls.length) {
                                            launchImageDetail(leftHolder.itemView, urls[index], message);
                                        }
                                    });
                                }
                                targetView = leftHolder.photoStackLeft;
                            }
                        }
                        break;
                    case 7: // 分享内容卡片
                        if (leftHolder.sharedContentCardLeft != null) {
                            leftHolder.sharedContentCardLeft.setVisibility(View.VISIBLE);
                            SharedContent sc = sharedContentCache.get(message.id);
                            if (sc != null) {
                                leftHolder.sharedContentCardLeft.setSharedContent(sc);
                            } else if (db != null && dbExecutor != null) {
                                dbExecutor.execute(() -> {
                                    SharedContent dbSc = db.sharedContentDao().getByMessageId(message.id);
                                    if (dbSc != null) {
                                        sharedContentCache.put(message.id, dbSc);
                                        leftHolder.sharedContentCardLeft.post(() ->
                                                leftHolder.sharedContentCardLeft.setSharedContent(dbSc));
                                    }
                                });
                            }
                        }
                        targetView = leftHolder.sharedContentCardLeft;
                        break;
                    case 8: // 合并转发卡片
                        if (leftHolder.forwardCardLeft != null) {
                            leftHolder.forwardCardLeft.setVisibility(View.VISIBLE);
                            leftHolder.forwardCardLeft.setForwardData(message.content);
                        }
                        targetView = leftHolder.forwardCardLeft;
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

        // 入场动画：仅对时间戳晚于 adapter 创建时间的消息（即进入聊天后新产生的消息）
        // 历史消息、重回聊天时 adapter 重建后的旧消息都不会动画
        if (message.timestamp > animateAfterTimestamp && !animatedMessageIds.contains(message.id)) {
            animatedMessageIds.add(message.id);
            float density = holder.itemView.getContext().getResources().getDisplayMetrics().density;
            holder.itemView.setTranslationY(60f * density);
            holder.itemView.setAlpha(0f);
            holder.itemView.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(300)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(2f))
                    .start();
        }
    }

    @Override
    public int getItemCount() {
        List<Message> current = differ.getCurrentList();
        return current != null ? current.size() : 0;
    }

    public static class LeftViewHolder extends RecyclerView.ViewHolder {
        public TextView tvTimestampLeft;
        android.widget.ImageView ivAvatarLeft;
        View vInnerVoiceDotLeft;

        android.widget.FrameLayout flBubbleLeft;
        TextView tvContentLeft;
        android.widget.LinearLayout llVoiceLeft;
        android.widget.ImageView ivVoiceIconLeft;
        TextView tvVoiceDurationLeft;

        TextView tvVoiceLeft;
        TextView tvEmojiLeft;
        android.widget.ImageView ivImageLeft;
        View flVirtualImageLeft;
        PhotoStackView photoStackLeft;
        TextView tvExpandToggleLeft;
        android.widget.LinearLayout llExpandedImagesLeft;
        View llQuoteLeft;
        TextView tvQuoteContentLeft;
        View llNormalContentLeft;
        TextView tvRevokeLeft;
        TextView tvSystemMessageLeft;

        View llVoiceContainerLeft;
        TextView tvVoiceToTextBtnLeft;
        TextView tvVoiceTextLeft;

        // 多选 + 分享卡片 + 合并转发
        android.widget.CheckBox cbSelectLeft;
        SharedContentCardView sharedContentCardLeft;
        ForwardCardView forwardCardLeft;

        public LeftViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTimestampLeft = itemView.findViewById(R.id.tvTimestampLeft);
            ivAvatarLeft = itemView.findViewById(R.id.ivAvatarLeft);
            vInnerVoiceDotLeft = itemView.findViewById(R.id.vInnerVoiceDotLeft);

            tvContentLeft = itemView.findViewById(R.id.tvContentLeft);

            tvVoiceLeft = itemView.findViewById(R.id.tvVoiceLeft);
            tvEmojiLeft = itemView.findViewById(R.id.tvEmojiLeft);
            ivImageLeft = itemView.findViewById(R.id.ivImageLeft);
            flVirtualImageLeft = itemView.findViewById(R.id.flVirtualImageLeft);
            photoStackLeft = itemView.findViewById(R.id.photoStackLeft);
            tvExpandToggleLeft = itemView.findViewById(R.id.tvExpandToggleLeft);
            llExpandedImagesLeft = itemView.findViewById(R.id.llExpandedImagesLeft);
            llQuoteLeft = itemView.findViewById(R.id.llQuoteLeft);
            tvQuoteContentLeft = itemView.findViewById(R.id.tvQuoteContentLeft);
            llNormalContentLeft = itemView.findViewById(R.id.llNormalContentLeft);
            tvRevokeLeft = itemView.findViewById(R.id.tvRevokeLeft);
            tvSystemMessageLeft = itemView.findViewById(R.id.tvSystemMessageLeft);
            llVoiceContainerLeft = itemView.findViewById(R.id.llVoiceContainerLeft);
            tvVoiceToTextBtnLeft = itemView.findViewById(R.id.tvVoiceToTextBtnLeft);
            tvVoiceTextLeft = itemView.findViewById(R.id.tvVoiceTextLeft);

            cbSelectLeft = itemView.findViewById(R.id.cbSelectLeft);
            sharedContentCardLeft = itemView.findViewById(R.id.sharedContentCardLeft);
            forwardCardLeft = itemView.findViewById(R.id.forwardCardLeft);
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
        PhotoStackView photoStackRight;
        TextView tvExpandToggleRight;
        android.widget.LinearLayout llExpandedImagesRight;
        View llQuoteRight;
        TextView tvQuoteContentRight;
        View llNormalContentRight;
        TextView tvRevokeRight;
        TextView tvSystemMessageRight;
        android.widget.ImageView ivAvatarRight;

        // 多选 + 分享卡片 + 合并转发
        android.widget.CheckBox cbSelectRight;
        SharedContentCardView sharedContentCardRight;
        ForwardCardView forwardCardRight;
        
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
            photoStackRight = itemView.findViewById(R.id.photoStackRight);
            tvExpandToggleRight = itemView.findViewById(R.id.tvExpandToggleRight);
            llExpandedImagesRight = itemView.findViewById(R.id.llExpandedImagesRight);
            llQuoteRight = itemView.findViewById(R.id.llQuoteRight);
            tvQuoteContentRight = itemView.findViewById(R.id.tvQuoteContentRight);
            llNormalContentRight = itemView.findViewById(R.id.llNormalContentRight);
            tvRevokeRight = itemView.findViewById(R.id.tvRevokeRight);
            tvSystemMessageRight = itemView.findViewById(R.id.tvSystemMessageRight);
            ivAvatarRight = itemView.findViewById(R.id.ivAvatarRight);

            cbSelectRight = itemView.findViewById(R.id.cbSelectRight);
            sharedContentCardRight = itemView.findViewById(R.id.sharedContentCardRight);
            forwardCardRight = itemView.findViewById(R.id.forwardCardRight);
        }
    }
}