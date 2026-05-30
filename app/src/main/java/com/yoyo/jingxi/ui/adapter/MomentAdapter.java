package com.yoyo.jingxi.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import java.util.Arrays;
import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.Character;
import com.yoyo.jingxi.data.entity.Moment;
import com.yoyo.jingxi.data.entity.MomentComment;
import com.yoyo.jingxi.data.entity.MomentLike;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MomentAdapter extends RecyclerView.Adapter<MomentAdapter.ViewHolder> {

    private final Context context;
    private List<MomentWithDetails> moments = new ArrayList<>();
    private final OnMomentInteractionListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    public interface OnMomentInteractionListener {
        void onLikeClick(Moment moment);
        void onCommentClick(Moment moment);
        void onReplyClick(Moment moment, MomentComment replyTo);
        void onMomentLongClick(Moment moment);
    }

    public static class MomentWithDetails {
        public Moment moment;
        public List<MomentLike> likes = new ArrayList<>();
        public List<MomentComment> comments = new ArrayList<>();
    }

    public MomentAdapter(Context context, OnMomentInteractionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setMoments(List<MomentWithDetails> moments) {
        this.moments = moments;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_moment, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MomentWithDetails item = moments.get(position);
        Moment moment = item.moment;

        // 设置发布者信息
        holder.tvName.setText(moment.publisherName != null ? moment.publisherName : "未知");
        
        // 我们需要根据publisherType和publisherId来加载头像。由于这里是Adapter的onBindViewHolder，
        // 优先使用动态表里存的头像路径
        if (moment.publisherAvatar != null && !moment.publisherAvatar.isEmpty()) {
            if (context instanceof android.app.Activity) {
                if (((android.app.Activity) context).isFinishing() || ((android.app.Activity) context).isDestroyed()) {
                    return;
                }
            }
            Glide.with(context)
                .load(moment.publisherAvatar)
                .placeholder(R.drawable.bg_avatar_placeholder)
                .error(R.drawable.bg_avatar_placeholder)
                .circleCrop()
                .into(holder.ivAvatar);
        } else {
            // 没有存头像则根据类型降级查询
            if (moment.publisherType == 0) { // 用户
                holder.ivAvatar.setImageResource(R.drawable.bg_avatar_placeholder); // TODO 使用用户的真实头像
            } else { // 角色
                new Thread(() -> {
                    com.yoyo.jingxi.data.entity.Character c = null;
                    try {
                        c = AppDatabase.getDatabase(context).characterDao().getCharacterByIdSync(Integer.parseInt(moment.publisherId));
                    } catch (NumberFormatException e) {
                        // publisherId may not be an integer
                    }
                    final String avatarPath = (c != null) ? c.avatarPath : null;
                    if (avatarPath != null && !avatarPath.isEmpty()) {
                        ((android.app.Activity) context).runOnUiThread(() -> {
                            if (((android.app.Activity) context).isFinishing() || ((android.app.Activity) context).isDestroyed()) return;
                            if (((android.app.Activity) context).isFinishing() || ((android.app.Activity) context).isDestroyed()) return;
                            Glide.with(context)
                                .load(avatarPath)
                                .placeholder(R.drawable.bg_avatar_placeholder)
                                .error(R.drawable.bg_avatar_placeholder)
                                .circleCrop()
                                .into(holder.ivAvatar);
                        });
                    } else {
                        ((android.app.Activity) context).runOnUiThread(() -> {
                             if (((android.app.Activity) context).isFinishing() || ((android.app.Activity) context).isDestroyed()) return;
                             holder.ivAvatar.setImageResource(R.drawable.bg_avatar_placeholder);
                        });
                    }
                }).start();
            }
        }

        // 设置内容
        if (moment.content != null && !moment.content.isEmpty()) {
            holder.tvContent.setText(moment.content);
            holder.tvContent.setVisibility(View.VISIBLE);
        } else {
            holder.tvContent.setVisibility(View.GONE);
        }

        // 设置图片（支持1、4、N图排版）
        if (moment.imageUrl != null && !moment.imageUrl.isEmpty()) {
            holder.rvImages.setVisibility(View.VISIBLE);
            
            // 隐藏可能存在的"图片正在后台生成中"的文字，因为现在我们用独立的图片占位了
            String contentStr = moment.content;
            if (contentStr != null && contentStr.contains("图片正在后台生成中...")) {
                contentStr = contentStr.replace("图片正在后台生成中...\n", "").replace("图片正在后台生成中...", "").trim();
                if (contentStr.isEmpty()) {
                    holder.tvContent.setVisibility(View.GONE);
                } else {
                    holder.tvContent.setText(contentStr);
                }
            }

            String[] urls = moment.imageUrl.split(",");
            List<String> urlList = new ArrayList<>(Arrays.asList(urls));
            
            int spanCount = 3;
            if (urlList.size() == 1) {
                spanCount = 1;
            } else if (urlList.size() == 2 || urlList.size() == 4) {
                spanCount = 2;
            }
            
            GridLayoutManager layoutManager = new GridLayoutManager(context, spanCount);
            holder.rvImages.setLayoutManager(layoutManager);
            
            // 计算图片大小
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int padding = (int) (context.getResources().getDisplayMetrics().density * 16 * 2); // 左右各16dp
            int avatarArea = (int) (context.getResources().getDisplayMetrics().density * (48 + 12)); // 头像48dp + 边距12dp
            int availableWidth = screenWidth - padding - avatarArea;
            
            int itemSize;
            if (urlList.size() == 1) {
                itemSize = availableWidth * 2 / 3; // 单图最大宽度为可用宽度的2/3
            } else if (urlList.size() == 2 || urlList.size() == 4) {
                int spacing = (int) (context.getResources().getDisplayMetrics().density * 4); // 间距为4dp
                itemSize = (availableWidth - spacing) / 2; // 2列宽
            } else {
                int spacing = (int) (context.getResources().getDisplayMetrics().density * 4); // 间距为4dp
                itemSize = (availableWidth - 2 * spacing) / 3; // 3列宽
            }
            
            // 移除旧的ItemDecoration防止重复添加
            while (holder.rvImages.getItemDecorationCount() > 0) {
                holder.rvImages.removeItemDecorationAt(0);
            }
            
            final int finalSpanCount = spanCount;
            holder.rvImages.addItemDecoration(new RecyclerView.ItemDecoration() {
                @Override
                public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                    int space = (int) (context.getResources().getDisplayMetrics().density * 4);
                    int position = parent.getChildAdapterPosition(view);
                    int column = position % finalSpanCount;
                    
                    outRect.left = column * space / finalSpanCount;
                    outRect.right = space - (column + 1) * space / finalSpanCount;
                    if (position >= finalSpanCount) {
                        outRect.top = space;
                    }
                }
            });

            MomentImageAdapter imageAdapter = new MomentImageAdapter(context, urlList);
            imageAdapter.setImageSize(itemSize);
            imageAdapter.setOnItemClickListener(new MomentImageAdapter.OnItemClickListener() {
                @Override
                public void onItemClick(int position, String url) {
                    android.content.Intent intent = new android.content.Intent(context, com.yoyo.jingxi.ui.activity.ImageDetailActivity.class);
                    intent.putExtra("image_url", url);
                    intent.putExtra("image_index", position);
                    intent.putExtra("moment_id", moment.id);
                    if (url.startsWith("virtual://")) {
                        String desc = android.net.Uri.decode(url.substring("virtual://".length()));
                        try {
                            org.json.JSONObject json = new org.json.JSONObject(desc);
                            desc = json.optString("desc", "虚拟图片");
                        } catch (Exception e) {}
                        intent.putExtra("virtual_desc", desc);
                    } else if (url.startsWith("error://")) {
                        String desc = android.net.Uri.decode(url.substring("error://".length()));
                        try {
                            org.json.JSONObject json = new org.json.JSONObject(desc);
                            desc = json.optString("desc", "虚拟图片");
                        } catch (Exception e) {}
                        intent.putExtra("virtual_desc", desc);
                    }
                    context.startActivity(intent);
                }

                @Override
                public void onItemLongClick(int position, String url) {
                    // do nothing
                }
            });
            holder.rvImages.setAdapter(imageAdapter);
        } else {
            holder.rvImages.setVisibility(View.GONE);
        }

        // 设置时间
        holder.tvTime.setText(dateFormat.format(new Date(moment.timestamp)));

        // 处理点赞和评论区域的显示
        boolean hasLikes = item.likes != null && !item.likes.isEmpty();
        boolean hasComments = item.comments != null && !item.comments.isEmpty();

        if (hasLikes || hasComments) {
            holder.llInteractionArea.setVisibility(View.VISIBLE);
            
            // 点赞处理
            if (hasLikes) {
                holder.tvLikes.setVisibility(View.VISIBLE);
                StringBuilder likesBuilder = new StringBuilder("❤️ ");
                for (int i = 0; i < item.likes.size(); i++) {
                    likesBuilder.append(item.likes.get(i).likerName);
                    if (i < item.likes.size() - 1) {
                        likesBuilder.append(", ");
                    }
                }
                holder.tvLikes.setText(likesBuilder.toString());
            } else {
                holder.tvLikes.setVisibility(View.GONE);
            }

            // 分割线
            if (hasLikes && hasComments) {
                holder.vDivider.setVisibility(View.VISIBLE);
            } else {
                holder.vDivider.setVisibility(View.GONE);
            }

            // 评论处理
            if (hasComments) {
                holder.rvComments.setVisibility(View.VISIBLE);
                MomentCommentAdapter commentAdapter = new MomentCommentAdapter(context, comment -> {
                    if (listener != null) {
                        listener.onReplyClick(moment, comment);
                    }
                });
                holder.rvComments.setLayoutManager(new LinearLayoutManager(context));
                holder.rvComments.setAdapter(commentAdapter);
                commentAdapter.setComments(item.comments);
            } else {
                holder.rvComments.setVisibility(View.GONE);
            }
        } else {
            holder.llInteractionArea.setVisibility(View.GONE);
        }

        // 判断当前用户是否已点赞
        boolean isLiked = false;
        long currentUserId = com.yoyo.jingxi.utils.SpUtils.getLong("user_id", -1L); // 假设通过SpUtils获取当前用户ID
        if (item.likes != null) {
            for (MomentLike like : item.likes) {
                if (like.likerId.equals(String.valueOf(currentUserId))) {
                    isLiked = true;
                    break;
                }
            }
        }

        if (isLiked) {
            holder.ivLikeBtn.setImageResource(R.drawable.ic_like_filled);
        } else {
            holder.ivLikeBtn.setImageResource(R.drawable.ic_like_outline);
        }

        // 点击操作按钮
        boolean finalIsLiked = isLiked;
        holder.ivLikeBtn.setOnClickListener(v -> {
            // 乐观更新 UI
            boolean newLikeState = !finalIsLiked;
            if (newLikeState) {
                holder.ivLikeBtn.setImageResource(R.drawable.ic_like_filled);
            } else {
                holder.ivLikeBtn.setImageResource(R.drawable.ic_like_outline);
            }

            if (listener != null) {
                listener.onLikeClick(moment);
            }
        });
        
        holder.ivCommentBtn.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCommentClick(moment);
            }
        });

        // 长按删除动态
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onMomentLongClick(moment);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return moments.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvName;
        TextView tvContent;
        RecyclerView rvImages;
        TextView tvTime;
        ImageView ivLikeBtn;
        ImageView ivCommentBtn;
        
        LinearLayout llInteractionArea;
        TextView tvLikes;
        View vDivider;
        RecyclerView rvComments;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
            tvName = itemView.findViewById(R.id.tv_name);
            tvContent = itemView.findViewById(R.id.tv_content);
            rvImages = itemView.findViewById(R.id.rv_images);
            tvTime = itemView.findViewById(R.id.tv_time);
            ivLikeBtn = itemView.findViewById(R.id.iv_like_btn);
            ivCommentBtn = itemView.findViewById(R.id.iv_comment_btn);
            
            llInteractionArea = itemView.findViewById(R.id.ll_interaction_area);
            tvLikes = itemView.findViewById(R.id.tv_likes);
            vDivider = itemView.findViewById(R.id.v_divider);
            rvComments = itemView.findViewById(R.id.rv_comments);
        }
    }
}
