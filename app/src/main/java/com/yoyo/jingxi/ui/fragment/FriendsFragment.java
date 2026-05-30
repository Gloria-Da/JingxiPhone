package com.yoyo.jingxi.ui.fragment;
import com.yoyo.jingxi.ui.activity.AddFriendActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.Character;
import com.yoyo.jingxi.ui.adapter.CharacterListAdapter;

public class FriendsFragment extends Fragment {

    private RecyclerView rvFriendsList;
    private CharacterListAdapter adapter;
    private AppDatabase db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_friends, container, false);
        

        View btnNetwork = view.findViewById(R.id.btn_relationship_network);
        if (btnNetwork != null) {
            btnNetwork.setOnClickListener(v -> {
                startActivity(new Intent(getContext(), com.yoyo.jingxi.ui.activity.RelationshipNetworkActivity.class));
            });
        }

        rvFriendsList = view.findViewById(R.id.rvFriendsList);
        rvFriendsList.setLayoutManager(new LinearLayoutManager(getContext()));
        
        adapter = new CharacterListAdapter();
        adapter.setOnItemClickListener(new CharacterListAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(Character character) {
                // 在好友列表点击好友时，可以选择编辑好友或者直接发起聊天
                // 简单起见，这里跳转到编辑页面，并且可以带参数发起聊天
                Intent intent = new Intent(getContext(), com.yoyo.jingxi.ui.activity.AddFriendActivity.class);
                intent.putExtra("character_id", character.id);
                startActivity(intent);
            }

            @Override
            public void onDeleteClick(Character character) {
                if (character != null) {
                    new Thread(() -> {
                        // Delete the character from database
                        db.characterDao().delete(character);
                    }).start();
                }
            }
        });
        rvFriendsList.setAdapter(adapter);

        db = AppDatabase.getDatabase(getContext());
        
        // Observe characters data from Room database
        db.characterDao().getAllCharacters().observe(getViewLifecycleOwner(), characters -> {
            adapter.setCharacters(characters);
        });

        return view;
    }
}
