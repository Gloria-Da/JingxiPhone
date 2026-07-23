package com.yoyo.jingxi.ui.widget;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.entity.SharedContent;
import com.yoyo.jingxi.ui.activity.SharedContentBrowserActivity;

/**
 * 分享链接的富媒体卡片组件。
 * 显示在聊天消息中，代替纯文本展示外部分享内容。
 * 左侧站点图标 + 中间站点名/标题 + 右侧缩略图，整卡可点击打开内置浏览器。
 */
public class SharedContentCardView extends LinearLayout {

    private LinearLayout cardContainer;
    private ImageView ivFavicon;
    private TextView tvSiteName;
    private TextView tvTitle;
    private ImageView ivThumbnail;

    private SharedContent sharedContent;

    public SharedContentCardView(Context context) {
        this(context, null);
    }

    public SharedContentCardView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SharedContentCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.widget_shared_content_card, this, true);
        cardContainer = findViewById(R.id.cardContainer);
        ivFavicon = findViewById(R.id.ivFavicon);
        tvSiteName = findViewById(R.id.tvSiteName);
        tvTitle = findViewById(R.id.tvTitle);
        ivThumbnail = findViewById(R.id.ivThumbnail);

        cardContainer.setOnClickListener(v -> {
            if (sharedContent != null && !TextUtils.isEmpty(sharedContent.sourceUrl)) {
                Intent intent = new Intent(getContext(), SharedContentBrowserActivity.class);
                intent.putExtra("url", sharedContent.sourceUrl);
                getContext().startActivity(intent);
            }
        });
    }

    /**
     * 将长按监听器代理到内部可点击的 cardContainer，
     * 否则 ChatAdapter 设置的长按无法穿透到 clickable 的 child。
     */
    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        cardContainer.setOnLongClickListener(l);
    }

    /**
     * 绑定 SharedContent 数据到卡片视图
     */
    public void setSharedContent(SharedContent content) {
        this.sharedContent = content;
        if (content == null) return;

        // 站点名
        if (!TextUtils.isEmpty(content.siteName)) {
            tvSiteName.setText(content.siteName);
            tvSiteName.setVisibility(View.VISIBLE);
        } else {
            tvSiteName.setVisibility(View.GONE);
        }

        // 标题
        if (!TextUtils.isEmpty(content.contentTitle)) {
            tvTitle.setText(content.contentTitle);
        } else {
            tvTitle.setText(content.sourceUrl);
        }

        // Favicon
        if (!TextUtils.isEmpty(content.faviconUrl)) {
            Glide.with(getContext())
                    .load(content.faviconUrl)
                    .placeholder(R.drawable.ic_launcher_round)
                    .error(R.drawable.ic_launcher_round)
                    .circleCrop()
                    .into(ivFavicon);
        }

        // 缩略图
        if (!TextUtils.isEmpty(content.thumbnailUrl)) {
            ivThumbnail.setVisibility(View.VISIBLE);
            Glide.with(getContext())
                    .load(content.thumbnailUrl)
                    .placeholder(android.R.color.darker_gray)
                    .centerCrop()
                    .into(ivThumbnail);
        } else {
            ivThumbnail.setVisibility(View.GONE);
        }
    }

    public SharedContent getSharedContent() {
        return sharedContent;
    }
}
