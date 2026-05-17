package com.yoyo.jingxi.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.entity.Character;

import java.util.ArrayList;
import java.util.List;

public class CharacterListAdapter extends RecyclerView.Adapter<CharacterListAdapter.ViewHolder> {

    private List<Character> characterList = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(Character character);
        void onDeleteClick(Character character);
    }

    public void setCharacters(List<Character> characters) {
        this.characterList = characters;
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_list, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Character character = characterList.get(position);
        holder.tvName.setText(character.name);
        holder.tvSummary.setText(character.persona); // Simple summary placeholder
        
        if (character.avatarPath != null && !character.avatarPath.isEmpty()) {
            android.content.Context context = holder.itemView.getContext();
            if (!(context instanceof android.app.Activity) || (!((android.app.Activity) context).isFinishing() && !((android.app.Activity) context).isDestroyed())) {
                Glide.with(context)
                     .load(character.avatarPath)
                     .circleCrop()
                     .placeholder(R.drawable.ic_launcher_round)
                     .into(holder.ivAvatar);
            }
        } else {
            holder.ivAvatar.setImageResource(R.drawable.ic_launcher_round);
        }
        
        holder.llRoot.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(character);
            }
        });

        holder.tvDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteClick(character);
            }
        });
    }

    @Override
    public int getItemCount() {
        return characterList != null ? characterList.size() : 0;
    }

    public Character getCharacterAt(int position) {
        if (characterList != null && position >= 0 && position < characterList.size()) {
            return characterList.get(position);
        }
        return null;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvName;
        TextView tvSummary;
        View llRoot;
        TextView tvDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            tvName = itemView.findViewById(R.id.tvName);
            tvSummary = itemView.findViewById(R.id.tvSummary);
            llRoot = itemView.findViewById(R.id.llRoot);
            tvDelete = itemView.findViewById(R.id.tvDelete);
        }
    }
}