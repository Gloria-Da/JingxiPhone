package com.yoyo.jingxi.ui.fragment;
import com.yoyo.jingxi.ui.activity.AddFriendActivity;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.CallRecord;
import com.yoyo.jingxi.data.entity.Character;
import com.yoyo.jingxi.data.entity.ChatSession;
import com.yoyo.jingxi.data.entity.Moment;
import com.yoyo.jingxi.data.entity.RelationshipNode;
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
        adapter.setUseSimpleLayout(true);
        adapter.setOnItemClickListener(new CharacterListAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(Character character) {
                Intent intent = new Intent(getContext(), com.yoyo.jingxi.ui.activity.AddFriendActivity.class);
                intent.putExtra("character_id", character.id);
                startActivity(intent);
            }

            @Override
            public void onDeleteClick(Character character) {
                new AlertDialog.Builder(getContext())
                    .setTitle("删除角色")
                    .setMessage("确定要删除「" + character.name + "」吗？该角色的所有聊天记录、通话记录、动态、日程、记忆等数据都将被永久删除，此操作不可撤销。")
                    .setPositiveButton("确定", (dialog, which) -> {
                        android.app.Activity activity = getActivity();
                        new Thread(() -> {
                            try {
                                String charIdStr = String.valueOf(character.id);

                                // 1. Delete all chat sessions and their associated data
                                List<ChatSession> sessions = db.chatSessionDao().getSessionsByCharacterId(character.id);
                                if (sessions != null) {
                                    for (ChatSession session : sessions) {
                                        List<CallRecord> callRecords = db.callRecordDao().getRecordsBySessionIdSync(session.id);
                                        if (callRecords != null) {
                                            for (CallRecord record : callRecords) {
                                                db.callMessageDao().deleteByCallId(record.id);
                                            }
                                        }
                                        db.callRecordDao().deleteBySessionId(session.id);
                                        db.messageDao().deleteMessagesBySessionId(session.id);
                                        db.chatSessionDao().deleteById(session.id);
                                    }
                                }

                                // 2. Delete moments and their comments, likes, notifications
                                List<Moment> moments = db.momentDao().getMomentsByCharacterPublisher(charIdStr);
                                if (moments != null) {
                                    for (Moment moment : moments) {
                                        db.momentCommentDao().deleteByMomentId(moment.id);
                                        db.momentLikeDao().deleteByMomentId(moment.id);
                                        db.momentNotificationDao().deleteByMomentId(moment.id);
                                    }
                                }
                                db.momentDao().deleteByCharacterPublisher(charIdStr);

                                // 3. Delete memories, memos, schedules
                                db.memoryDao().deleteByCharacterId(character.id);
                                db.memoDao().deleteByCharacterId(character.id);
                                db.scheduleDao().deleteByCharacterId(character.id);

                                // 4. Delete relationship node and edges
                                RelationshipNode node = db.relationshipNodeDao().getNodeByReference(0, charIdStr);
                                if (node != null) {
                                    db.relationshipEdgeDao().deleteEdgesByNodeId(node.id);
                                    db.relationshipNodeDao().deleteNodeById(node.id);
                                }

                                // 5. Finally delete the character itself
                                db.characterDao().delete(character);

                                if (activity != null) {
                                    activity.runOnUiThread(() ->
                                        Toast.makeText(activity, "已删除「" + character.name + "」", Toast.LENGTH_SHORT).show());
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                if (activity != null) {
                                    activity.runOnUiThread(() ->
                                        Toast.makeText(activity, "删除失败，请重试", Toast.LENGTH_SHORT).show());
                                }
                            }
                        }).start();
                    })
                    .setNegativeButton("取消", null)
                    .show();
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
