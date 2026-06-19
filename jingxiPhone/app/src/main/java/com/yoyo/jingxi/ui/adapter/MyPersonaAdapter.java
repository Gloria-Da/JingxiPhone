package com.yoyo.jingxi.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.entity.MyPersona;

import java.util.ArrayList;
import java.util.List;

public class MyPersonaAdapter extends RecyclerView.Adapter<MyPersonaAdapter.ViewHolder> {

    private List<MyPersona> personas = new ArrayList<>();
    private OnPersonaClickListener listener;

    public interface OnPersonaClickListener {
        void onEditClick(MyPersona persona);
        void onSetMainClick(MyPersona persona);
        void onDeleteClick(MyPersona persona);
    }

    public MyPersonaAdapter(OnPersonaClickListener listener) {
        this.listener = listener;
    }

    public void setPersonas(List<MyPersona> personas) {
        this.personas = personas;
        notifyDataSetChanged();
    }

    public MyPersona getPersonaAt(int position) {
        return personas.get(position);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_my_persona, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MyPersona persona = personas.get(position);
        holder.tvPersonaName.setText(persona.name);
        holder.tvPersonaDesc.setText(persona.persona);
        
        if (persona.avatarPath != null && !persona.avatarPath.isEmpty()) {
            android.content.Context ctx = holder.itemView.getContext();
            boolean isValid = true;
            if (ctx instanceof android.app.Activity) {
                android.app.Activity activity = (android.app.Activity) ctx;
                isValid = !activity.isFinishing() && !activity.isDestroyed();
            }
            if (isValid) {
                if (!(ctx instanceof android.app.Activity) || (!((android.app.Activity) ctx).isFinishing() && !((android.app.Activity) ctx).isDestroyed())) {
                    com.bumptech.glide.Glide.with(ctx)
                            .load(persona.avatarPath)
                            .circleCrop()
                            .placeholder(R.drawable.ic_launcher_round)
                            .into(holder.ivAvatar);
                }
            }
        } else {
            holder.ivAvatar.setImageResource(R.drawable.ic_launcher_round);
        }
        
        if (persona.isMainPersona) {
            holder.tvMainBadge.setVisibility(View.VISIBLE);
            holder.btnSetMain.setVisibility(View.GONE);
        } else {
            holder.tvMainBadge.setVisibility(View.GONE);
            holder.btnSetMain.setVisibility(View.VISIBLE);
        }

        holder.clMainContent.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEditClick(persona);
            }
        });

        holder.btnSetMain.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSetMainClick(persona);
            }
        });

        holder.tvDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteClick(persona);
            }
        });
    }

    @Override
    public int getItemCount() {
        return personas.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvPersonaName;
        TextView tvPersonaDesc;
        TextView tvMainBadge;
        Button btnSetMain;
        View clMainContent;
        TextView tvDelete;

        ViewHolder(View view) {
            super(view);
            ivAvatar = view.findViewById(R.id.ivAvatar);
            tvPersonaName = view.findViewById(R.id.tvPersonaName);
            tvPersonaDesc = view.findViewById(R.id.tvPersonaDesc);
            tvMainBadge = view.findViewById(R.id.tvMainBadge);
            btnSetMain = view.findViewById(R.id.btnSetMain);
            clMainContent = view.findViewById(R.id.clMainContent);
            tvDelete = view.findViewById(R.id.tvDelete);
        }
    }
}