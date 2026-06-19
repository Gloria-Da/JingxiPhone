package com.yoyo.jingxi.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.Moment;
import com.yoyo.jingxi.data.entity.MomentComment;
import com.yoyo.jingxi.data.entity.MomentLike;
import com.yoyo.jingxi.ui.adapter.MomentAdapter;
import com.yoyo.jingxi.ui.activity.AddMomentActivity;
import com.yoyo.jingxi.ui.activity.MomentNotificationsActivity;
import com.yoyo.jingxi.utils.SpUtils;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MomentsFragment extends Fragment {

    private RecyclerView rvMoments;
    private MomentAdapter adapter;
    private AppDatabase db;
    private ImageView ivAddMoment;
    private FrameLayout flNotifications;
    private TextView tvUnreadCount;
    private String currentPersonaId;
    
    private List<Moment> rawMoments;
    private android.os.Handler refreshHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (rawMoments != null) {
                updateVisibleMoments();
            }
            refreshHandler.postDelayed(this, 60000); // 每分钟刷新一次，检查是否有未来动态到了时间
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_moments, container, false);
        
        db = AppDatabase.getDatabase(requireContext());
        
        rvMoments = view.findViewById(R.id.rv_moments);
        
        // Use global views from Activity
        if (getActivity() != null) {
            ivAddMoment = getActivity().findViewById(R.id.iv_add_moment);
            flNotifications = getActivity().findViewById(R.id.flNotifications);
            tvUnreadCount = getActivity().findViewById(R.id.tvUnreadCount);
        }
        
        currentPersonaId = SpUtils.getString("current_persona_id", "");
        if (currentPersonaId.isEmpty()) {
            currentPersonaId = SpUtils.getString("MY_NAME", "我");
        }
        
        rvMoments.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new MomentAdapter(requireContext(), new MomentAdapter.OnMomentInteractionListener() {
            @Override
            public void onLikeClick(Moment moment) {
                handleLike(moment);
            }

            @Override
            public void onCommentClick(Moment moment) {
                showCommentDialog(moment, null);
            }

            @Override
            public void onReplyClick(Moment moment, MomentComment replyTo) {
                showCommentDialog(moment, replyTo);
            }

            @Override
            public void onMomentLongClick(Moment moment) {
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("删除动态")
                        .setMessage("确定要删除这条动态吗？")
                        .setPositiveButton("确定", (dialog, which) -> {
                            new Thread(() -> {
                                db.momentDao().delete(moment);
                                requireActivity().runOnUiThread(() -> loadMoments());
                            }).start();
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        });
        rvMoments.setAdapter(adapter);

        if (ivAddMoment != null) {
            ivAddMoment.setOnClickListener(v -> {
                startActivity(new Intent(requireContext(), AddMomentActivity.class));
            });
        }

        if (flNotifications != null) {
            flNotifications.setOnClickListener(v -> {
                startActivity(new Intent(requireContext(), MomentNotificationsActivity.class));
            });
        }
        
        if (!currentPersonaId.isEmpty() && tvUnreadCount != null) {
            db.momentNotificationDao().getUnreadCount(currentPersonaId, 0).observe(getViewLifecycleOwner(), count -> {
                if (count != null && count > 0) {
                    tvUnreadCount.setVisibility(View.VISIBLE);
                    tvUnreadCount.setText(String.valueOf(count > 99 ? "99+" : count));
                } else {
                    tvUnreadCount.setVisibility(View.GONE);
                }
            });
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadMoments();
        if (!isHidden()) {
            refreshHandler.post(refreshRunnable);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            refreshHandler.removeCallbacks(refreshRunnable);
        } else {
            // 重新显示时刷新列表并恢复定时器
            loadMoments();
            refreshHandler.removeCallbacks(refreshRunnable);
            refreshHandler.post(refreshRunnable);
        }
    }


    private void handleLike(Moment moment) {
        new Thread(() -> {
            List<com.yoyo.jingxi.data.entity.MyPersona> personas = db.myPersonaDao().getAllPersonasSync();
            if (!personas.isEmpty()) {
                com.yoyo.jingxi.data.entity.MyPersona me = personas.get(0);
                
                // 检查是否已经点赞
                MomentLike existingLike = db.momentLikeDao().getLikeByLiker(moment.id, me.name);
                
                if (existingLike != null) {
                    db.momentLikeDao().delete(existingLike);
                } else {
                    MomentLike like = new MomentLike();
                    like.momentId = moment.id;
                    like.likerType = 0; // 0 for MyPersona
                    like.likerId = me.name;
                    like.likerName = me.name;
                    like.timestamp = System.currentTimeMillis();
                    db.momentLikeDao().insert(like);
                    
                    // Trigger AI response for like
                    triggerAiResponseToInteraction(moment, false, null);
                }
                
                requireActivity().runOnUiThread(() -> {
                    loadMoments(); // 刷新列表
                });
            }
        }).start();
    }

    private void showCommentDialog(Moment moment, MomentComment replyTo) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext());
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_comment, null);
        builder.setView(view);
        android.app.AlertDialog dialog = builder.create();
        
        android.widget.TextView tvTitle = view.findViewById(R.id.tv_title);
        android.widget.MultiAutoCompleteTextView etComment = view.findViewById(R.id.et_comment);
        android.widget.Button btnCancel = view.findViewById(R.id.btn_cancel);
        android.widget.Button btnSend = view.findViewById(R.id.btn_send);
        
        // Setup AutoComplete for @ mentions
        new Thread(() -> {
            List<com.yoyo.jingxi.data.entity.MyPersona> personas = db.myPersonaDao().getAllPersonasSync();
            List<com.yoyo.jingxi.data.entity.Character> characters = db.characterDao().getAllCharactersSync();
            
            List<String> names = new ArrayList<>();
            for (com.yoyo.jingxi.data.entity.MyPersona p : personas) {
                names.add(p.name);
            }
            for (com.yoyo.jingxi.data.entity.Character c : characters) {
                names.add(c.name);
            }
            
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    android.widget.ArrayAdapter<String> arrayAdapter = new android.widget.ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, names);
                    etComment.setAdapter(arrayAdapter);
                    etComment.setTokenizer(new android.widget.MultiAutoCompleteTextView.CommaTokenizer() {
                        @Override
                        public int findTokenStart(CharSequence text, int cursor) {
                            int i = cursor;
                            while (i > 0 && text.charAt(i - 1) != '@') {
                                i--;
                            }
                            if (i > 0 && text.charAt(i - 1) == '@') {
                                return i;
                            }
                            return super.findTokenStart(text, cursor);
                        }

                        @Override
                        public int findTokenEnd(CharSequence text, int cursor) {
                            int i = cursor;
                            int len = text.length();
                            while (i < len) {
                                if (text.charAt(i) == ' ' || text.charAt(i) == '\n') {
                                    return i;
                                }
                                i++;
                            }
                            return len;
                        }

                        @Override
                        public CharSequence terminateToken(CharSequence text) {
                            int i = text.length();
                            if (i > 0 && text.charAt(i - 1) == ' ') {
                                return text;
                            } else {
                                if (text instanceof android.text.Spanned) {
                                    android.text.SpannableString sp = new android.text.SpannableString(text + " ");
                                    android.text.TextUtils.copySpansFrom((android.text.Spanned) text, 0, text.length(), Object.class, sp, 0);
                                    return sp;
                                } else {
                                    return text + " ";
                                }
                            }
                        }
                    });
                });
            }
        }).start();

        if (replyTo != null) {
            tvTitle.setText("回复 " + replyTo.authorName);
        } else {
            tvTitle.setText("评论");
        }
        
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSend.setOnClickListener(v -> {
            String text = etComment.getText().toString().trim();
            if (!text.isEmpty()) {
                new Thread(() -> {
                    List<com.yoyo.jingxi.data.entity.MyPersona> personas = db.myPersonaDao().getAllPersonasSync();
                    if (!personas.isEmpty()) {
                        com.yoyo.jingxi.data.entity.MyPersona me = personas.get(0);
                        MomentComment comment = new MomentComment();
                        comment.momentId = moment.id;
                        comment.authorType = 0;
                        comment.authorId = me.name;
                        comment.authorName = me.name;
                        comment.content = text;
                        comment.timestamp = System.currentTimeMillis();
                        if (replyTo != null) {
                            comment.replyToType = replyTo.authorType;
                            comment.replyToId = replyTo.authorId;
                            comment.replyToName = replyTo.authorName;
                        } else {
                            comment.replyToType = -1;
                        }
                        db.momentCommentDao().insert(comment);
                        
                        // Trigger AI response if needed
                        triggerAiResponseToInteraction(moment, true, comment.content);
                        
                        requireActivity().runOnUiThread(() -> {
                            loadMoments();
                        });
                    }
                }).start();
                dialog.dismiss();
            }
        });
        
        dialog.show();
    }

    private void triggerAiResponseToInteraction(Moment moment, boolean isComment, String commentContent) {
        // 如果发布者是角色，并且不是用户，尝试让其回复
        if (moment.publisherType == 1) {
            try {
                int characterId = Integer.parseInt(moment.publisherId);
                com.yoyo.jingxi.data.entity.Character publisher = db.characterDao().getCharacterByIdSync(characterId);
                if (publisher != null) {
                    com.yoyo.jingxi.network.OpenAIManager aiManager = new com.yoyo.jingxi.network.OpenAIManager();
                    String apiKey = com.yoyo.jingxi.utils.SpUtils.getString("OPENAI_API_KEY", "");
                    String endpoint = com.yoyo.jingxi.utils.SpUtils.getString("API_ENDPOINT", "https://api.openai.com/");
                    String model = com.yoyo.jingxi.utils.SpUtils.getString("API_MODEL", "gpt-4o-mini");
                    
                    if (apiKey.isEmpty()) return;
                    if (!endpoint.endsWith("/")) endpoint += "/";
                    
                    // 获取网络关系
                    String relationshipContent = "";
                    if (com.yoyo.jingxi.utils.SpUtils.getBoolean("RELATIONSHIP_NETWORK_ENABLED", true)) {
                        List<com.yoyo.jingxi.data.entity.RelationshipNode> nodes = db.relationshipNodeDao().getAllNodesSync();
                        List<com.yoyo.jingxi.data.entity.RelationshipEdge> edges = db.relationshipEdgeDao().getAllEdgesSync();
                        if (nodes != null && !nodes.isEmpty() && edges != null && !edges.isEmpty()) {
                            StringBuilder relBuilder = new StringBuilder();
                            for (com.yoyo.jingxi.data.entity.RelationshipEdge edge : edges) {
                                com.yoyo.jingxi.data.entity.RelationshipNode source = null;
                                com.yoyo.jingxi.data.entity.RelationshipNode target = null;
                                for (com.yoyo.jingxi.data.entity.RelationshipNode n : nodes) {
                                    if (n.id.equals(edge.sourceNodeId)) source = n;
                                    if (n.id.equals(edge.targetNodeId)) target = n;
                                }
                                if (source != null && target != null) {
                                    relBuilder.append("- ").append(source.name).append(" -> ").append(target.name).append(" : ").append(edge.relation).append("\n");
                                }
                            }
                            relationshipContent = relBuilder.toString();
                        }
                    }
                    
                    com.yoyo.jingxi.network.OpenAiRequest request = new com.yoyo.jingxi.network.OpenAiRequest();
                    request.model = model;
                    request.messages = new java.util.ArrayList<>();
                    
                    StringBuilder systemPrompt = new StringBuilder();
                    systemPrompt.append("【特别提示】这是朋友圈互动场景！你不是在私聊！\n");
                    systemPrompt.append("你现在的身份是: ").append(publisher.name).append("\n");
                    systemPrompt.append("你的人设: ").append(publisher.persona).append("\n\n");
                    if (!relationshipContent.isEmpty()) {
                        systemPrompt.append("你的人际关系: \n").append(relationshipContent).append("\n\n");
                    }
                    systemPrompt.append("【重要关系设定】：如果没有在上述人际关系中明确说明你与对方的关系，则默认你们仅为**普通的网络好友（网友）**，绝对不会在现实中互相约出门、一起游玩，也不会在动态里互相调侃或显得很熟络。\n\n");
                    systemPrompt.append("你（作为").append(publisher.name).append("）刚刚发布了一条朋友圈动态：\n").append(moment.content).append("\n\n");

                    String myName = com.yoyo.jingxi.utils.SpUtils.getString("MY_NAME", "我");
                    if (isComment) {
                        systemPrompt.append("你的朋友「").append(myName).append("」刚刚在你的这条朋友圈下面评论了你：\n")
                                   .append(commentContent).append("\n\n")
                                   .append("请你根据你的人设，决定是否回复这条评论，以及回复什么内容。注意，你是在回复你的一位好朋友，用符合人设和朋友圈语境的口吻。\n");
                    } else {
                        systemPrompt.append("你的朋友「").append(myName).append("」刚刚点赞了你的这条朋友圈。\n")
                                   .append("请你根据你的人设，决定是否要在动态下回复一句（类似于统一回复或单独回复）。\n");
                    }
                    systemPrompt.append("评论要求：必须像活人刷朋友圈一样。极简，不要像客服一样长篇大论。高冷的人可以只回一个“？”或“嗯”，熟人可以互怼。严禁说教和过度关心。\n");
                    systemPrompt.append("如果你想在回复中 @ 某人，可以在内容中直接使用 '@名字 ' 的格式。\n");
                    
                    systemPrompt.append("你必须严格返回以下 JSON 格式：\n")
                               .append("{\n")
                               .append("  \"should_reply\": true/false, // 是否要回复\n")
                               .append("  \"reply_content\": \"如果回复，你想说的话。如果不回复则留空\"\n")
                               .append("}\n");
                               
                    request.messages.add(new com.yoyo.jingxi.network.OpenAiRequest.Message("user", systemPrompt.toString()));
                    // request.response_format = new com.yoyo.jingxi.network.OpenAiRequest.ResponseFormat(); // 移除以提高兼容性
                    
                    retrofit2.Response<com.yoyo.jingxi.network.OpenAiResponse> response = aiManager.getApi().createChatCompletion(endpoint + "v1/chat/completions", "Bearer " + apiKey, request).execute();
                    
                    if (response.isSuccessful() && response.body() != null && !response.body().choices.isEmpty()) {
                        String content = response.body().choices.get(0).message.content;
                        try {
                            org.json.JSONObject json = new org.json.JSONObject(content);
                            boolean shouldReply = json.optBoolean("should_reply", false);
                            String replyContent = json.optString("reply_content", "");
                            
                            if (shouldReply && !replyContent.isEmpty()) {
                                MomentComment aiComment = new MomentComment();
                                aiComment.momentId = moment.id;
                                aiComment.authorId = String.valueOf(publisher.id);
                                aiComment.authorName = publisher.name;
                                aiComment.authorType = 1;
                                aiComment.content = replyContent;
                                aiComment.timestamp = System.currentTimeMillis();
                                
                                if (isComment) {
                                    // 回复当前互动的用户
                                    List<com.yoyo.jingxi.data.entity.MyPersona> myPersonas = db.myPersonaDao().getAllPersonasSync();
                                    if (!myPersonas.isEmpty()) {
                                        com.yoyo.jingxi.data.entity.MyPersona me = myPersonas.get(0);
                                        aiComment.replyToId = me.name;
                                        aiComment.replyToName = me.name;
                                        aiComment.replyToType = 0;
                                    } else {
                                        aiComment.replyToType = -1;
                                    }
                                } else {
                                    aiComment.replyToType = -1;
                                }
                                
                                db.momentCommentDao().insert(aiComment);
                                
                                // 生成通知给用户
                                List<com.yoyo.jingxi.data.entity.MyPersona> personas = db.myPersonaDao().getAllPersonasSync();
                                if (!personas.isEmpty()) {
                                    com.yoyo.jingxi.data.entity.MyPersona me = personas.get(0);
                                    if (isComment) {
                                        // 评论被回复
                                        com.yoyo.jingxi.data.entity.MomentNotification notif = new com.yoyo.jingxi.data.entity.MomentNotification();
                                        notif.momentId = moment.id;
                                        notif.receiverId = me.name;
                                        notif.receiverType = 0;
                                        notif.triggerId = String.valueOf(publisher.id);
                                        notif.triggerName = publisher.name;
                                        notif.triggerType = 1;
                                        notif.type = 1; // 1 means comment/reply
                                        notif.content = replyContent;
                                        notif.timestamp = System.currentTimeMillis();
                                        db.momentNotificationDao().insert(notif);
                                    } else {
                                        // 动态被评论
                                        com.yoyo.jingxi.data.entity.MomentNotification notif = new com.yoyo.jingxi.data.entity.MomentNotification();
                                        notif.momentId = moment.id;
                                        notif.receiverId = me.name;
                                        notif.receiverType = 0;
                                        notif.triggerId = String.valueOf(publisher.id);
                                        notif.triggerName = publisher.name;
                                        notif.triggerType = 1;
                                        notif.type = 1; // 1 means comment/reply
                                        notif.content = replyContent;
                                        notif.timestamp = System.currentTimeMillis();
                                        db.momentNotificationDao().insert(notif);
                                    }
                                }

                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() -> {
                                        android.content.Intent intent = new android.content.Intent("com.yoyo.jingxi.ACTION_MOMENT_UPDATED");
                                        requireContext().sendBroadcast(intent);
                                        loadMoments();
                                    });
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private LiveData<List<Moment>> momentsLiveData;
    private android.content.BroadcastReceiver momentUpdateReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, android.content.Intent intent) {
            if ("com.yoyo.jingxi.ACTION_MOMENT_UPDATED".equals(intent.getAction())) {
                loadMoments();
            }
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        android.content.IntentFilter filter = new android.content.IntentFilter("com.yoyo.jingxi.ACTION_MOMENT_UPDATED");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireActivity().registerReceiver(momentUpdateReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireActivity().registerReceiver(momentUpdateReceiver, filter);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            requireActivity().unregisterReceiver(momentUpdateReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadMoments() {
        // 避免重复挂载观察者
        if (momentsLiveData != null) {
            momentsLiveData.removeObservers(getViewLifecycleOwner());
        }
        
        momentsLiveData = db.momentDao().getVisibleMoments();
        momentsLiveData.observe(getViewLifecycleOwner(), moments -> {
            if (moments == null) return;
            this.rawMoments = moments;
            updateVisibleMoments();
        });
    }

    private void updateVisibleMoments() {
        if (rawMoments == null) return;
        // 允许未来 60 秒的误差，防止因为不同线程或生成时间与判断时间的微妙差异导致当前刚发的动态被错误过滤
        long currentTime = System.currentTimeMillis() + 60000;
        new Thread(() -> {
            // 对 rawMoments 进行按时间降序排序，确保最新动态在前面
            List<Moment> sortedMoments = new ArrayList<>(rawMoments);
            sortedMoments.sort((m1, m2) -> Long.compare(m2.timestamp, m1.timestamp));

            List<MomentAdapter.MomentWithDetails> items = new ArrayList<>();
            int generateCount = 0;
            for (Moment m : sortedMoments) {
                // 只有时间已经到达的动态才显示
                if (m.timestamp <= currentTime) {
                    // 取消在列表这里自动生图，只在点开详情或特定操作时生图以节省token
                    MomentAdapter.MomentWithDetails details = new MomentAdapter.MomentWithDetails();
                    details.moment = m;
                    details.likes = db.momentLikeDao().getLikesForMomentSync(m.id);
                    details.comments = db.momentCommentDao().getCommentsForMomentSync(m.id);
                    items.add(details);
                }
            }
            
            if (getActivity() != null) {
                requireActivity().runOnUiThread(() -> {
                    adapter.setMoments(items);
                });
            }
        }).start();
    }
}
