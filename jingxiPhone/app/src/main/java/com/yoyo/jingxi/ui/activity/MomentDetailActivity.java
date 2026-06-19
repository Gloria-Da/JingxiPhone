package com.yoyo.jingxi.ui.activity;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.widget.FrameLayout;

import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.LiveData;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.Moment;
import com.yoyo.jingxi.data.entity.MomentComment;
import com.yoyo.jingxi.data.entity.MomentLike;
import com.yoyo.jingxi.ui.adapter.MomentAdapter;

import java.util.ArrayList;
import java.util.List;

public class MomentDetailActivity extends AppCompatActivity {

    private FrameLayout flMomentContainer;
    private AppDatabase db;
    private int momentId;
    private MomentAdapter adapter; // 利用 Adapter 内部的逻辑渲染单条

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.yoyo.jingxi.utils.ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moment_detail);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        flMomentContainer = findViewById(R.id.flMomentContainer);
        db = AppDatabase.getDatabase(this);

        momentId = getIntent().getIntExtra("moment_id", -1);
        if (momentId == -1) {
            finish();
            return;
        }

        // 我们这里投机取巧，直接复用 MomentAdapter 里的 onCreateViewHolder 和 onBindViewHolder 来渲染
        adapter = new MomentAdapter(this, new MomentAdapter.OnMomentInteractionListener() {
            @Override
            public void onLikeClick(Moment moment) {
                new Thread(() -> {
                    List<com.yoyo.jingxi.data.entity.MyPersona> personas = db.myPersonaDao().getAllPersonasSync();
                    if (!personas.isEmpty()) {
                        com.yoyo.jingxi.data.entity.MyPersona me = personas.get(0);
                        
                        MomentLike existingLike = db.momentLikeDao().getLikeByLiker(moment.id, me.name);
                        if (existingLike != null) {
                            db.momentLikeDao().delete(existingLike);
                        } else {
                            MomentLike like = new MomentLike();
                            like.momentId = moment.id;
                            like.likerType = 0;
                            like.likerId = me.name;
                            like.likerName = me.name;
                            like.timestamp = System.currentTimeMillis();
                            db.momentLikeDao().insert(like);
                            
                            com.yoyo.jingxi.utils.MomentNotificationManager.dispatchNotification(
                                    db, moment.id, 0,
                                    0, me.name, me.name, "",
                                    moment.publisherType, moment.publisherId, null
                            );
                            
                            // Trigger AI response for like
                            triggerAiResponseToInteraction(moment, false, null);
                        }
                        
                        // broadcast
                        android.content.Intent intent = new android.content.Intent("com.yoyo.jingxi.ACTION_MOMENT_UPDATED");
                        sendBroadcast(intent);
                        
                        loadMomentDetail();
                    }
                }).start();
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
            }
        });

        loadMomentDetail();
    }

    private void loadMomentDetail() {
        new Thread(() -> {
            // 为简化，直接从数据库取全部 visible moments，找对的那一条
            // 实际项目中最好加个 getMomentById 的 DAO 方法
            List<Moment> all = db.momentDao().getVisibleMomentsSync(System.currentTimeMillis() + 60000);
            Moment target = null;
            if (all != null) {
                for (Moment m : all) {
                    if (m.id == momentId) {
                        target = m;
                        break;
                    }
                }
            }
            
            if (target != null) {
                MomentAdapter.MomentWithDetails details = new MomentAdapter.MomentWithDetails();
                details.moment = target;
                details.likes = db.momentLikeDao().getLikesForMomentSync(target.id);
                details.comments = db.momentCommentDao().getCommentsForMomentSync(target.id);
                
                runOnUiThread(() -> renderMoment(details));
            } else {
                runOnUiThread(() -> {
                    android.widget.Toast.makeText(this, "该动态已删除或不可见", android.widget.Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        }).start();
    }
    
    private void renderMoment(MomentAdapter.MomentWithDetails details) {
        // 创建一个 ViewHolder 并绑定数据，然后将它的 itemView 添加到容器中
        MomentAdapter.ViewHolder holder = adapter.onCreateViewHolder(flMomentContainer, 0);
        
        // 为了避免 hack 太深，这里直接设置数据给 adapter，然后让 adapter 渲染，我们取第一条
        List<MomentAdapter.MomentWithDetails> list = new ArrayList<>();
        list.add(details);
        adapter.setMoments(list);
        
        adapter.onBindViewHolder(holder, 0, java.util.Collections.singletonList(details));
        adapter.onBindViewHolder(holder, 0);
        
        flMomentContainer.removeAllViews();
        flMomentContainer.addView(holder.itemView);
    }
    
    private void showCommentDialog(Moment moment, MomentComment replyTo) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        android.view.View view = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_comment, null);
        builder.setView(view);
        android.app.AlertDialog dialog = builder.create();
        
        android.widget.TextView tvTitle = view.findViewById(R.id.tv_title);
        android.widget.EditText etComment = view.findViewById(R.id.et_comment);
        android.widget.Button btnCancel = view.findViewById(R.id.btn_cancel);
        android.widget.Button btnSend = view.findViewById(R.id.btn_send);
        
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
                        
                        // Generate notification
                        if (replyTo != null) {
                            com.yoyo.jingxi.utils.MomentNotificationManager.dispatchNotification(
                                    db, moment.id, 1,
                                    0, me.name, me.name, "",
                                    replyTo.authorType, replyTo.authorId, text
                            );
                        } else {
                            com.yoyo.jingxi.utils.MomentNotificationManager.dispatchNotification(
                                    db, moment.id, 1,
                                    0, me.name, me.name, "",
                                    moment.publisherType, moment.publisherId, text
                            );
                        }

                        android.content.Intent intent = new android.content.Intent("com.yoyo.jingxi.ACTION_MOMENT_UPDATED");
                        sendBroadcast(intent);
                        
                        loadMomentDetail();
                    }
                }).start();
                dialog.dismiss();
            }
        });
        
        dialog.show();
    }

    private void triggerAiResponseToInteraction(Moment moment, boolean isComment, String commentContent) {
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
                                
                                List<com.yoyo.jingxi.data.entity.MyPersona> personas = db.myPersonaDao().getAllPersonasSync();
                                if (!personas.isEmpty()) {
                                    com.yoyo.jingxi.data.entity.MyPersona me = personas.get(0);
                                    com.yoyo.jingxi.data.entity.MomentNotification notif = new com.yoyo.jingxi.data.entity.MomentNotification();
                                    notif.momentId = moment.id;
                                    notif.receiverId = me.name;
                                    notif.receiverType = 0;
                                    notif.triggerId = String.valueOf(publisher.id);
                                    notif.triggerName = publisher.name;
                                    notif.triggerType = 1;
                                    notif.type = 1;
                                    notif.content = replyContent;
                                    notif.timestamp = System.currentTimeMillis();
                                    db.momentNotificationDao().insert(notif);
                                }

                                runOnUiThread(() -> {
                                    android.content.Intent intent = new android.content.Intent("com.yoyo.jingxi.ACTION_MOMENT_UPDATED");
                                    sendBroadcast(intent);
                                    loadMomentDetail();
                                });
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
}
