
package com.yoyo.jingxi.ui.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.yoyo.jingxi.R;
import com.yoyo.jingxi.ui.activity.ImageDetailActivity;

import java.util.List;

public class MomentImageAdapter extends RecyclerView.Adapter<MomentImageAdapter.ImageViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(int position, String url);
        void onItemLongClick(int position, String url);
    }

    private Context context;
    private List<String> imageUrls;
    private int imageSize;
    private OnItemClickListener listener;

    public MomentImageAdapter(Context context, List<String> imageUrls) {
        this.context = context;
        this.imageUrls = imageUrls;
    }

    public void setImageSize(int size) {
        this.imageSize = size;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_moment_image, parent, false);
        
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.width = imageSize;
        params.height = imageSize;
        view.setLayoutParams(params);
        
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        String url = imageUrls.get(position);
        
        if (url.startsWith("virtual://") || url.startsWith("error://")) {
            if (!(context instanceof android.app.Activity) || (!((android.app.Activity) context).isFinishing() && !((android.app.Activity) context).isDestroyed())) {
                Glide.with(context)
                        .load(R.drawable.bg_virtual_image)
                        .centerCrop()
                        .into(holder.ivImage);
            }
        } else {
            if (!(context instanceof android.app.Activity) || (!((android.app.Activity) context).isFinishing() && !((android.app.Activity) context).isDestroyed())) {
                Glide.with(context)
                        .load(url)
                        .placeholder(R.drawable.bg_avatar_placeholder)
                        .centerCrop()
                        .into(holder.ivImage);
            }
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(position, url);
            } else {
                Intent intent = new Intent(context, ImageDetailActivity.class);
                intent.putExtra("image_url", url);
                if (url.startsWith("virtual://")) {
                    String desc = android.net.Uri.decode(url.substring("virtual://".length()));
                    try {
                        org.json.JSONObject json = new org.json.JSONObject(desc);
                        desc = json.optString("desc", "虚拟图片");
                    } catch (Exception e) {
                        // ignore
                    }
                    intent.putExtra("virtual_desc", desc);
                } else if (url.startsWith("error://")) {
                    String desc = android.net.Uri.decode(url.substring("error://".length()));
                    try {
                        org.json.JSONObject json = new org.json.JSONObject(desc);
                        desc = json.optString("desc", "虚拟图片");
                    } catch (Exception e) {
                        // ignore
                    }
                    intent.putExtra("virtual_desc", desc);
                }
                intent.putExtra("image_index", position);
                context.startActivity(intent);
            }
        });
        
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onItemLongClick(position, url);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return imageUrls == null ? 0 : imageUrls.size();
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView ivImage;

        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            ivImage = itemView.findViewById(R.id.iv_moment_image);
        }
    }
}