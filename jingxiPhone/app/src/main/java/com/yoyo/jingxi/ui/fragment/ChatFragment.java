package com.yoyo.jingxi.ui.fragment;
import com.yoyo.jingxi.ui.activity.ChatMainActivity;
import com.yoyo.jingxi.ui.activity.ChatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yoyo.jingxi.ui.activity.ChatActivity;
import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.ui.adapter.SessionListAdapter;

public class ChatFragment extends Fragment {

    private RecyclerView rvSessionList;
    private SessionListAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat, container, false);

        rvSessionList = view.findViewById(R.id.rvSessionList);
        rvSessionList.setLayoutManager(new LinearLayoutManager(getContext()));
        
        adapter = new SessionListAdapter();
        adapter.setOnItemClickListener(session -> {
            // 清除未读消息数
            new Thread(() -> {
                if (getContext() != null) {
                    AppDatabase.getDatabase(getContext()).chatSessionDao().updateUnreadCount(session.sessionId, 0);
                }
            }).start();
            Intent intent = new Intent(getContext(), ChatActivity.class);
            intent.putExtra("session_id", session.sessionId);
            intent.putExtra("friend_name", session.friendName);
            startActivity(intent);
        });
        
        adapter.setOnItemLongClickListener(session -> {
            showSessionOptionsDialog(session);
        });
        
        rvSessionList.setAdapter(adapter);

        // Create chat is now handled by the ChatMainActivity's top menu

        AppDatabase.getDatabase(getContext()).sessionWithLastMessageDao()
                .getSessionsWithLastMessage()
                .observe(getViewLifecycleOwner(), sessions -> {
                    adapter.setSessions(sessions);
                });

        return view;
    }

    private void showSessionOptionsDialog(com.yoyo.jingxi.data.dao.SessionWithLastMessageDao.SessionWithLastMessage session) {
        if (getContext() == null) return;
        
        String[] options = {
            session.isPinned ? "取消置顶" : "置顶",
            "删除会话"
        };
        
        new AlertDialog.Builder(getContext())
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    // Toggle Pin
                    new Thread(() -> {
                        AppDatabase.getDatabase(getContext()).chatSessionDao()
                            .updatePinnedStatus(session.sessionId, !session.isPinned);
                    }).start();
                } else if (which == 1) {
                    // Delete Session
                    showDeleteConfirmDialog(session);
                }
            })
            .show();
    }

    private void showDeleteConfirmDialog(com.yoyo.jingxi.data.dao.SessionWithLastMessageDao.SessionWithLastMessage session) {
        if (getContext() == null) return;
        
        new AlertDialog.Builder(getContext())
            .setTitle("提示")
            .setMessage("确定要删除与 " + session.friendName + " 的会话吗？聊天记录也将被删除。")
            .setPositiveButton("确定", (dialog, which) -> {
                new Thread(() -> {
                    // Delete all messages in the session
                    AppDatabase.getDatabase(getContext()).messageDao().deleteMessagesBySessionId(session.sessionId);
                    // Delete the session itself
                    AppDatabase.getDatabase(getContext()).chatSessionDao().deleteById(session.sessionId);
                }).start();
            })
            .setNegativeButton("取消", null)
            .show();
    }
}
