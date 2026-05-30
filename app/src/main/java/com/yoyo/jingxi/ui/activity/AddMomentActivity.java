package com.yoyo.jingxi.ui.activity;

import android.os.Bundle;
import android.app.AlertDialog;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yoyo.jingxi.ui.adapter.MomentImageAdapter;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.Character;
import com.yoyo.jingxi.data.entity.Moment;
import com.yoyo.jingxi.data.entity.MyPersona;

import java.util.ArrayList;
import java.util.List;

public class AddMomentActivity extends AppCompatActivity {

    private ImageView ivBack;
    private Button btnPublish;
    private Spinner spPublisher;
    private EditText etContent;
    private RecyclerView rvImages;
    private ImageView ivAddImage;

    private AppDatabase db;
    private List<PublisherItem> publisherItems = new ArrayList<>();
    private List<String> selectedImageUrls = new ArrayList<>();
    private MomentImageAdapter imageAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.yoyo.jingxi.utils.ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_moment);

        db = AppDatabase.getDatabase(this);

        ivBack = findViewById(R.id.iv_back);
        btnPublish = findViewById(R.id.btn_publish);
        spPublisher = findViewById(R.id.sp_publisher);
        etContent = findViewById(R.id.et_content);
        rvImages = findViewById(R.id.rv_images);
        ivAddImage = findViewById(R.id.iv_add_image);

        ivBack.setOnClickListener(v -> finish());

        loadPublishers();
        setupAutoComplete();
        setupImageRecyclerView();

        ivAddImage.setOnClickListener(v -> {
            showAddImageDialog();
        });

        btnPublish.setOnClickListener(v -> publishMoment());
    }

    private void setupImageRecyclerView() {
        GridLayoutManager layoutManager = new GridLayoutManager(this, 3);
        rvImages.setLayoutManager(layoutManager);
        
        imageAdapter = new MomentImageAdapter(this, selectedImageUrls);
        
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int padding = (int) (getResources().getDisplayMetrics().density * 16 * 2);
        int availableWidth = screenWidth - padding;
        int spacing = (int) (getResources().getDisplayMetrics().density * 4);
        int itemSize = (availableWidth - 2 * spacing) / 3;
        
        imageAdapter.setImageSize(itemSize);
        imageAdapter.setOnItemClickListener(new MomentImageAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(int position, String url) {
                // Ignore click in add moment, let long click handle deletion
            }
            
            @Override
            public void onItemLongClick(int position, String url) {
                new AlertDialog.Builder(AddMomentActivity.this)
                    .setTitle("删除图片")
                    .setMessage("确定要删除这张图片吗？")
                    .setPositiveButton("删除", (dialog, which) -> {
                        selectedImageUrls.remove(position);
                        imageAdapter.notifyDataSetChanged();
                        if (selectedImageUrls.size() < 9) {
                            ivAddImage.setVisibility(View.VISIBLE);
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
            }
        });
        rvImages.setAdapter(imageAdapter);
        
        rvImages.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(android.graphics.Rect outRect, android.view.View view, RecyclerView parent, RecyclerView.State state) {
                int space = (int) (getResources().getDisplayMetrics().density * 4);
                int position = parent.getChildAdapterPosition(view);
                int column = position % 3;
                
                outRect.left = column * space / 3;
                outRect.right = space - (column + 1) * space / 3;
                if (position >= 3) {
                    outRect.top = space;
                }
            }
        });
    }

    private void showAddImageDialog() {
        if (selectedImageUrls.size() >= 9) {
            Toast.makeText(this, "最多只能添加9张图片", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择图片来源");
        
        String[] options = {"真实图片(从相册选择)", "虚拟图片(输入文本描述)"};
        
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                // 真实图片
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                intent.setType("image/*");
                intent.putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(intent, 100);
            } else if (which == 1) {
                // 虚拟图片
                showVirtualImageDialog();
            }
        });
        
        builder.show();
    }
    
    private void showVirtualImageDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("添加虚拟图片");
        
        final EditText input = new EditText(this);
        input.setHint("输入图片描述(将使用AI生成)");
        builder.setView(input);
        
        builder.setPositiveButton("确定", (dialog, which) -> {
            String desc = input.getText().toString().trim();
            if (!desc.isEmpty()) {
                String finalUrl = "virtual://" + android.net.Uri.encode(desc);
                selectedImageUrls.add(finalUrl);
                imageAdapter.notifyDataSetChanged();
                
                if (selectedImageUrls.size() >= 9) {
                    ivAddImage.setVisibility(android.view.View.GONE);
                }
            }
        });
        
        builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                // 多选处理
                int count = data.getClipData().getItemCount();
                int remain = 9 - selectedImageUrls.size();
                int added = 0;
                
                for (int i = 0; i < count; i++) {
                    if (added >= remain) {
                        Toast.makeText(this, "最多只能添加9张图片，已截断多余选择", Toast.LENGTH_SHORT).show();
                        break;
                    }
                    
                    android.net.Uri selectedImage = data.getClipData().getItemAt(i).getUri();
                    try {
                        getContentResolver().takePersistableUriPermission(selectedImage, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (SecurityException e) {
                        e.printStackTrace();
                    }
                    
                    selectedImageUrls.add(selectedImage.toString());
                    added++;
                }
            } else if (data.getData() != null) {
                // 单选处理
                if (selectedImageUrls.size() >= 9) {
                    Toast.makeText(this, "最多只能添加9张图片", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                android.net.Uri selectedImage = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(selectedImage, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException e) {
                    e.printStackTrace();
                }
                
                selectedImageUrls.add(selectedImage.toString());
            }
            
            imageAdapter.notifyDataSetChanged();
            if (selectedImageUrls.size() >= 9) {
                ivAddImage.setVisibility(android.view.View.GONE);
            }
        }
    }

    private void setupAutoComplete() {
        etContent.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (count == 1 && s.charAt(start) == '@') {
                    showMentionDialog();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void showMentionDialog() {
        new Thread(() -> {
            List<MyPersona> personas = db.myPersonaDao().getAllPersonasSync();
            List<Character> characters = db.characterDao().getAllCharactersSync();
            
            List<String> names = new ArrayList<>();
            for (MyPersona p : personas) {
                names.add(p.name);
            }
            for (Character c : characters) {
                names.add(c.name);
            }
            
            runOnUiThread(() -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("选择要@的人");
                
                String[] nameArray = names.toArray(new String[0]);
                builder.setItems(nameArray, (dialog, which) -> {
                    String selectedName = nameArray[which];
                    
                    int cursorPosition = etContent.getSelectionStart();
                    Editable editable = etContent.getText();
                    
                    // Insert the name directly after the '@' character
                    editable.insert(cursorPosition, selectedName + " ");
                    
                    // Dismiss dialog
                    dialog.dismiss();
                });
                
                builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());
                builder.show();
            });
        }).start();
    }

    private void loadPublishers() {
        new Thread(() -> {
            // 1. 获取所有 MyPersona (用户)
            List<MyPersona> personas = db.myPersonaDao().getAllPersonasSync();

            runOnUiThread(() -> {
                for (MyPersona p : personas) {
                    // 对于 MyPersona，使用 name 作为 id 因为 @PrimaryKey 是 name
                    publisherItems.add(new PublisherItem(0, p.name, p.name, p.avatarPath));
                }

                List<String> names = new ArrayList<>();
                for (PublisherItem item : publisherItems) {
                    names.add(item.name + " (我)");
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spPublisher.setAdapter(adapter);
            });
        }).start();
    }

    private void publishMoment() {
        String content = etContent.getText().toString().trim();
        if (content.isEmpty() && selectedImageUrls.isEmpty()) {
            Toast.makeText(this, "请输入内容或选择图片", Toast.LENGTH_SHORT).show();
            return;
        }

        int selectedPosition = spPublisher.getSelectedItemPosition();
        if (selectedPosition < 0 || selectedPosition >= publisherItems.size()) {
            Toast.makeText(this, "请选择发布者", Toast.LENGTH_SHORT).show();
            return;
        }

        PublisherItem publisher = publisherItems.get(selectedPosition);

        Moment moment = new Moment();
        moment.publisherType = publisher.type;
        moment.publisherId = publisher.id;
        moment.publisherName = publisher.name;
        moment.publisherAvatar = publisher.avatar;
        moment.content = content;
        
        if (!selectedImageUrls.isEmpty()) {
            moment.imageUrl = String.join(",", selectedImageUrls);
        } else {
            moment.imageUrl = "";
        }
        
        moment.timestamp = System.currentTimeMillis();

        new Thread(() -> {
            long momentId = db.momentDao().insert(moment);
            moment.id = (int) momentId;
            
            // Check for @ mentions in the original post content
            if (content != null && content.contains("@")) {
                com.yoyo.jingxi.utils.MomentNotificationManager.dispatchNotification(
                        db, moment.id, 2, // 2 is for MENTION, but dispatchNotification handles internally
                        moment.publisherType, moment.publisherId, moment.publisherName, moment.publisherAvatar,
                        -1, "", content // Use dummy receiver as processMentions will scan the content
                );
            }
            
            runOnUiThread(() -> {
                Toast.makeText(this, "发布成功", Toast.LENGTH_SHORT).show();
                finish();
            });
            
            // 放到后台异步执行，不影响页面关闭
            triggerAiNetworkReaction(moment);
            
        }).start();
    }

    private void triggerAiNetworkReaction(Moment moment) {
        // AI 人际网络对动态的反应（点赞/评论）
        if (!com.yoyo.jingxi.utils.SpUtils.getBoolean("RELATIONSHIP_NETWORK_ENABLED", true)) {
            com.yoyo.jingxi.utils.MomentSimulator.simulateInteraction(db, moment);
            com.yoyo.jingxi.utils.ImageGenerationManager.getInstance().checkAndGenerateImages(moment);
            return;
        }
        
        List<com.yoyo.jingxi.data.entity.RelationshipNode> nodes = db.relationshipNodeDao().getAllNodesSync();
        List<com.yoyo.jingxi.data.entity.RelationshipEdge> edges = db.relationshipEdgeDao().getAllEdgesSync();
        if (nodes == null || nodes.isEmpty() || edges == null || edges.isEmpty()) {
            com.yoyo.jingxi.utils.MomentSimulator.simulateInteraction(db, moment);
            com.yoyo.jingxi.utils.ImageGenerationManager.getInstance().checkAndGenerateImages(moment);
            return;
        }

        // 找寻所有系统内的可交互角色 (AI 角色)
        List<com.yoyo.jingxi.data.entity.Character> allCharacters = db.characterDao().getAllCharactersSync();
        if (allCharacters == null || allCharacters.isEmpty()) return;

        // 构建关系网络描述
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
        String relationshipContent = relBuilder.toString();

        com.yoyo.jingxi.network.OpenAIManager aiManager = new com.yoyo.jingxi.network.OpenAIManager();
        String apiKey = com.yoyo.jingxi.utils.SpUtils.getString("OPENAI_API_KEY", "");
        String endpoint = com.yoyo.jingxi.utils.SpUtils.getString("API_ENDPOINT", "https://api.openai.com/");
        String model = com.yoyo.jingxi.utils.SpUtils.getString("API_MODEL", "gpt-4o-mini");

        if (apiKey.isEmpty()) return;
        if (!endpoint.endsWith("/")) endpoint += "/";
        
        // 构建带有超时设置的自定义 OkHttpClient
        okhttp3.OkHttpClient customClient = new okhttp3.OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();
            
        retrofit2.Retrofit retrofit = new retrofit2.Retrofit.Builder()
            .baseUrl(endpoint)
            .client(customClient)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build();
            
        com.yoyo.jingxi.network.OpenAiApi customApi = retrofit.create(com.yoyo.jingxi.network.OpenAiApi.class);

        // 如果是用户发的动态，我们要确保一定有 AI 互动（不冷场）
        List<com.yoyo.jingxi.data.entity.Character> mustInteractCharacters = new java.util.ArrayList<>();
        if (moment.publisherType == 0) {
            // 随机挑选 1~3 个角色强制互动
            java.util.Collections.shuffle(allCharacters);
            int count = Math.min(allCharacters.size(), 1 + new java.util.Random().nextInt(3));
            for (int i = 0; i < count; i++) {
                mustInteractCharacters.add(allCharacters.get(i));
            }
        }

        for (com.yoyo.jingxi.data.entity.Character character : allCharacters) {
            boolean isMustInteract = mustInteractCharacters.contains(character);
            
            if (moment.publisherType != 0) {
                if (!relationshipContent.contains(character.name) && !relationshipContent.contains(moment.publisherName)) {
                    // 如果是角色发的朋友圈，且角色和发布者都不在关系图里，只有 10% 的概率偶然看到
                    if (Math.random() > 0.1) continue;
                }
            } else {
                // 对于用户发的，除了被选中强制互动的，其他人也有较小概率（15%）偶然刷到
                if (!isMustInteract && Math.random() > 0.15) {
                    continue;
                }
            }

            try {
                com.yoyo.jingxi.network.OpenAiRequest request = new com.yoyo.jingxi.network.OpenAiRequest();
                request.model = model;
                request.messages = new java.util.ArrayList<>();
                
                StringBuilder systemPrompt = new StringBuilder();
                systemPrompt.append("你现在正在扮演: ").append(character.name).append("\n");
                systemPrompt.append("你的人设: ").append(character.persona).append("\n\n");
                systemPrompt.append("整体人际关系网络: \n").append(relationshipContent).append("\n\n");
                
                systemPrompt.append("【动态事件】\n");
                systemPrompt.append("用户「").append(moment.publisherName).append("」刚刚在朋友圈发布了一条新动态：\n");
                systemPrompt.append(moment.content).append("\n\n");
                
                if (isMustInteract) {
                    systemPrompt.append("【强制要求】：作为朋友或关注者，你必须对这条动态点赞，并且**必须**发表一条符合你人设的评论！不能忽略。\n");
                } else {
                    systemPrompt.append("请你根据你在关系网络中的定位、你的人设，以及动态的内容，决定你是否要点赞，是否要评论。\n")
                               .append("即使是完全不相关的人，也有可能恰好刷到朋友圈点个赞或评论，但如果是朋友，互动的可能性更高。\n");
                }
                
                systemPrompt.append("【关于@功能的严格限制】：\n")
                           .append("大部分情况下的评论不需要@任何人，非极特殊情况坚决不用@，不要把@当作普通称呼。不要为了@而@，不要使用 @大家 或 @所有人。\n");
                
                systemPrompt.append("【特别提示】\n");
                systemPrompt.append("如果你在评论中想专门提及某人（不论是发帖人还是其他人），你可以使用 '@名字 ' 的格式（例如 @").append(moment.publisherName).append(" ）。这会触发对他们的通知。\n\n");
                           
                systemPrompt.append("【绝对人设优先】：你所有的表达必须完全受限于你的核心人设。高冷的人绝不发大段文字或滥用表情，活泼的人才可以发梗。\n")
                           .append("[断句规则：一口气说得完的话不加逗号。主谓之间、动宾之间不无故断开。\"的地得\"和介词前不断句。紧张时短句用句号而非逗号。日常对话允许长句一气呵成。断句的疏密跟随情绪，不做机械等距切割。]\n\n")
                           .append("请你根据你在关系网络中的定位、你的人设，以及动态的内容，决定你是否要点赞，是否要评论。\n")
                           .append("要求：\n")
                           .append("1. 这是一个真实的社交网络，如果你决定评论，必须像活人刷朋友圈一样。极简，不要像客服一样长篇大论。高冷的人可以只回一个“？”或“嗯”，熟人可以互怼。严禁说教和过度关心。绝不是对发布者的私聊。\n")
                           .append("2. 不要每次都既点赞又评论，保持自然的随机性。如果觉得没啥好说的，就不要评论。\n")
                           .append("你必须严格返回以下 JSON 格式：\n")
                           .append("{\n")
                           .append("  \"should_like\": true/false,\n")
                           .append("  \"should_comment\": true/false,\n")
                           .append("  \"comment_content\": \"如果你决定评论，这里填入评论内容。如果不评论，填空字符串\"\n")
                           .append("}\n");

                request.messages.add(new com.yoyo.jingxi.network.OpenAiRequest.Message("system", systemPrompt.toString()));
                // 添加一条 user 消息，有些大模型 API (比如 Claude/Gemini 兼容层) 不允许只有 system 消息
                request.messages.add(new com.yoyo.jingxi.network.OpenAiRequest.Message("user", "请根据提供的信息，以 JSON 格式返回你的互动决定和评论内容。"));
                request.response_format = new com.yoyo.jingxi.network.OpenAiRequest.ResponseFormat();

                retrofit2.Response<com.yoyo.jingxi.network.OpenAiResponse> response = customApi.createChatCompletion(endpoint + "v1/chat/completions", "Bearer " + apiKey, request).execute();

                boolean actualCommented = false;
                if (response.isSuccessful() && response.body() != null && !response.body().choices.isEmpty()) {
                    String content = response.body().choices.get(0).message.content;
                    
                    // 清理可能包含的 Markdown 代码块标记，防止解析失败
                    if (content.startsWith("```json")) {
                        content = content.substring(7);
                    } else if (content.startsWith("```")) {
                        content = content.substring(3);
                    }
                    if (content.endsWith("```")) {
                        content = content.substring(0, content.length() - 3);
                    }
                    content = content.trim();

                    try {
                        org.json.JSONObject json = new org.json.JSONObject(content);
                        boolean shouldLike = json.optBoolean("should_like", false) || isMustInteract;
                        boolean shouldComment = json.optBoolean("should_comment", false) || isMustInteract;
                        String commentContent = json.optString("comment_content", "");

                        if (shouldLike) {
                            com.yoyo.jingxi.data.entity.MomentLike like = new com.yoyo.jingxi.data.entity.MomentLike();
                            like.momentId = moment.id;
                            like.likerType = 1;
                            like.likerId = String.valueOf(character.id);
                            like.likerName = character.name;
                            like.timestamp = System.currentTimeMillis();
                            db.momentLikeDao().insert(like);
                            
                            com.yoyo.jingxi.utils.MomentNotificationManager.dispatchNotification(
                                    db, moment.id, 0,
                                    1, String.valueOf(character.id), character.name, character.avatarPath,
                                    moment.publisherType, moment.publisherId, null
                            );
                        }

                        if (shouldComment) {
                            if ((commentContent == null || commentContent.trim().isEmpty()) && isMustInteract) {
                                // GPT 未遵守指令生成内容时的兜底回复
                                commentContent = "支持一下！";
                            }
                            
                            if (commentContent != null && !commentContent.trim().isEmpty()) {
                                com.yoyo.jingxi.data.entity.MomentComment aiComment = new com.yoyo.jingxi.data.entity.MomentComment();
                                aiComment.momentId = moment.id;
                                aiComment.authorId = String.valueOf(character.id);
                                aiComment.authorName = character.name;
                                aiComment.authorType = 1;
                                aiComment.content = commentContent;
                                aiComment.timestamp = System.currentTimeMillis() + 1000;
                                aiComment.replyToType = -1;
                                db.momentCommentDao().insert(aiComment);
                                actualCommented = true;
                                
                                com.yoyo.jingxi.utils.MomentNotificationManager.dispatchNotification(
                                        db, moment.id, 1,
                                        1, String.valueOf(character.id), character.name, character.avatarPath,
                                        moment.publisherType, moment.publisherId, commentContent
                                );
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        android.util.Log.e("MomentSimulator", "Failed to parse JSON: " + content, e);
                    }
                } else {
                    if (response.errorBody() != null) {
                        android.util.Log.e("MomentSimulator", "API Error: " + response.errorBody().string());
                    } else {
                        android.util.Log.e("MomentSimulator", "API Error: response is not successful or body is null");
                    }
                }
                
                // 完全兜底机制：网络错误或解析失败但又被强制要求互动的，硬编码插入赞评
                if (isMustInteract && !actualCommented) {
                    com.yoyo.jingxi.data.entity.MomentLike like = new com.yoyo.jingxi.data.entity.MomentLike();
                    like.momentId = moment.id;
                    like.likerType = 1;
                    like.likerId = String.valueOf(character.id);
                    like.likerName = character.name;
                    like.timestamp = System.currentTimeMillis();
                    db.momentLikeDao().insert(like);
                    
                    com.yoyo.jingxi.utils.MomentNotificationManager.dispatchNotification(
                            db, moment.id, 0,
                            1, String.valueOf(character.id), character.name, character.avatarPath,
                            moment.publisherType, moment.publisherId, null
                    );
                    
                    com.yoyo.jingxi.data.entity.MomentComment aiComment = new com.yoyo.jingxi.data.entity.MomentComment();
                    aiComment.momentId = moment.id;
                    aiComment.authorId = String.valueOf(character.id);
                    aiComment.authorName = character.name;
                    aiComment.authorType = 1;
                    aiComment.content = "太棒了！";
                    aiComment.timestamp = System.currentTimeMillis() + 1000;
                    aiComment.replyToType = -1;
                    db.momentCommentDao().insert(aiComment);
                    
                    com.yoyo.jingxi.utils.MomentNotificationManager.dispatchNotification(
                            db, moment.id, 1,
                            1, String.valueOf(character.id), character.name, character.avatarPath,
                            moment.publisherType, moment.publisherId, aiComment.content
                    );
                }
                
                // 间隔一小段时间，模拟不同人的浏览时间差
                Thread.sleep(2000);
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        // 触发列表刷新
        db.momentDao().update(moment);
    }

    private static class PublisherItem {
        int type;
        String id;
        String name;
        String avatar;

        public PublisherItem(int type, String id, String name, String avatar) {
            this.type = type;
            this.id = id;
            this.name = name;
            this.avatar = avatar;
        }
    }
}
