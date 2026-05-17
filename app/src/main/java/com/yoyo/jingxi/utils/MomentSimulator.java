package com.yoyo.jingxi.utils;

import android.content.Context;

import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.Character;
import com.yoyo.jingxi.data.entity.Moment;
import com.yoyo.jingxi.data.entity.MomentComment;
import com.yoyo.jingxi.data.entity.MomentLike;
import com.yoyo.jingxi.data.entity.MyPersona;
import com.yoyo.jingxi.data.entity.RelationshipEdge;
import com.yoyo.jingxi.data.entity.RelationshipNode;
import com.yoyo.jingxi.network.OpenAIManager;
import com.yoyo.jingxi.network.OpenAiRequest;
import com.yoyo.jingxi.network.OpenAiResponse;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 用于模拟朋友圈中的点赞和评论互动
 */
public class MomentSimulator {

    public static void simulateInteraction(Context context, Moment moment) {
        new Thread(() -> {
            AppDatabase db = AppDatabase.getDatabase(context);
            simulateInteraction(db, moment);
        }).start();
    }
    
    public static void simulateInteraction(AppDatabase db, Moment moment) {
        new Thread(() -> {
            
            // 1. 收集潜在的互动者 (从角色和关系网节点中获取)
            List<Character> allCharacters = db.characterDao().getAllCharactersSync();
            List<RelationshipNode> allNodes = db.relationshipNodeDao().getAllNodesSync();
            
            List<Interactor> potentialInteractors = new ArrayList<>();
            
            // 收集现有真实角色 (排除自己)
            if (allCharacters != null) {
                for (Character c : allCharacters) {
                    if (moment.publisherType == 1 && moment.publisherId.equals(String.valueOf(c.id))) {
                        continue;
                    }
                    Interactor interactor = new Interactor();
                    interactor.id = String.valueOf(c.id);
                    interactor.name = c.name;
                    interactor.persona = c.persona;
                    interactor.isRealCharacter = true;
                    interactor.originalCharacter = c;
                    potentialInteractors.add(interactor);
                }
            }
            
            // 收集纯虚拟背景人物 (排除自己)
            if (allNodes != null) {
                for (RelationshipNode node : allNodes) {
                    if (node.type == 2) { // 纯虚拟背景人物
                        if (moment.publisherType == 1 && node.name.equals(moment.publisherName)) {
                            continue;
                        }
                        Interactor interactor = new Interactor();
                        interactor.id = "virtual_" + node.id;
                        interactor.name = node.name;
                        interactor.persona = node.description != null ? node.description : "普通朋友";
                        interactor.isRealCharacter = false;
                        potentialInteractors.add(interactor);
                    }
                }
            }
            
            // 如果连虚拟人物都没有，补充两个路人凑数，保证不为0
            if (potentialInteractors.isEmpty()) {
                Interactor i1 = new Interactor();
                i1.id = "dummy_1";
                i1.name = "热心网友";
                i1.persona = "喜欢凑热闹的网友";
                i1.isRealCharacter = false;
                potentialInteractors.add(i1);
                
                Interactor i2 = new Interactor();
                i2.id = "dummy_2";
                i2.name = "无名好友";
                i2.persona = "普通朋友";
                i2.isRealCharacter = false;
                potentialInteractors.add(i2);
            }

            // 1. 评估朋友圈的互动性 (1-3分)
            int interactivityScore = evaluateMomentInteractivity(moment);
            
            // 2. 决定互动总人数
            // 基础人数：三分之一的角色互动
            int baseInteractionCount = Math.max(1, potentialInteractors.size() / 3);
            if (potentialInteractors.size() >= 4) {
                baseInteractionCount = potentialInteractors.size() / 4 + new Random().nextInt(potentialInteractors.size() / 3 - potentialInteractors.size() / 4 + 1);
            }
            
            // 确保至少有 1-3 条互动，根据得分增加
            int minInteractions = Math.min(potentialInteractors.size(), Math.max(1, interactivityScore));
            int targetInteractionCount = Math.max(minInteractions, baseInteractionCount);
            if (interactivityScore == 3) {
                targetInteractionCount += 1; // 强互动性额外增加互动
            }
            targetInteractionCount = Math.min(targetInteractionCount, potentialInteractors.size());
            
            // 3. 根据关系网计算每个潜在互动角色的权重
            List<InteractorWeight> characterWeights = calculateInteractionWeights(db, moment, potentialInteractors);
            
            // 根据权重随机抽取互动者 (加权随机)
            List<Interactor> selectedInteractors = selectInteractorsByWeight(characterWeights, targetInteractionCount);
            
            // 4. 为选中的角色分配动作 (点赞/评论)
            List<Interactor> likers = new ArrayList<>();
            List<Interactor> commenters = new ArrayList<>();
            Random random = new Random();
            
            for (Interactor c : selectedInteractors) {
                // 如果朋友圈内容中明确@了该角色，强制点赞和评论
                boolean isMentioned = moment.content != null && (moment.content.contains("@" + c.name + " ") || moment.content.endsWith("@" + c.name));
                
                boolean willLike = isMentioned || random.nextFloat() < 0.9f; // 90%概率点赞
                boolean willComment = isMentioned || random.nextFloat() < (0.4f + (interactivityScore * 0.15f)); // 评论概率(55% - 85%)
                
                // 确保至少做一样
                if (!willLike && !willComment) {
                    if (random.nextBoolean()) willLike = true;
                    else willComment = true;
                }
                
                if (willLike) likers.add(c);
                if (willComment) commenters.add(c);
            }

            // 执行点赞（增加防重判断）
            for (Interactor liker : likers) {
                MomentLike existingLike = db.momentLikeDao().getLikeByLiker(moment.id, String.valueOf(liker.id));
                if (existingLike == null) {
                    MomentLike like = new MomentLike();
                    like.momentId = moment.id;
                    like.likerType = 1; // 角色
                    like.likerId = String.valueOf(liker.id);
                    like.likerName = liker.name;
                    
                    // 确保时间戳也是稍微过去的，而不是未来的
                    long likeDelay = (long) (Math.random() * 800); 
                    like.timestamp = moment.timestamp + likeDelay;
                    
                    db.momentLikeDao().insert(like);
                    
                    com.yoyo.jingxi.utils.MomentNotificationManager.dispatchNotification(
                            db, moment.id, 0,
                            like.likerType, like.likerId, like.likerName, liker.originalCharacter != null ? liker.originalCharacter.avatarPath : "",
                            moment.publisherType, moment.publisherId, null
                    );
                }
            }

            // 批量生成评论
            if (!commenters.isEmpty()) {
                boolean success = generateCommentsBatch(db, moment, commenters);
                if (!success) {
                    // 如果评论生成失败，让本来打算评论的人改为点赞（如果他们还没点赞的话）
                    for (Interactor commenter : commenters) {
                        if (!likers.contains(commenter)) {
                            MomentLike like = new MomentLike();
                            like.momentId = moment.id;
                            like.likerType = commenter.isRealCharacter ? 1 : 2;
                            like.likerId = commenter.id;
                            like.likerName = commenter.name;
                            like.timestamp = moment.timestamp + (long) (Math.random() * 800);
                            db.momentLikeDao().insert(like);
                        }
                    }
                }
            }

        }).start();
    }
    
    private static class Interactor {
        String id;
        String name;
        String persona;
        boolean isRealCharacter;
        Character originalCharacter;
        String relationshipWithPublisher; // 与发布者的具体关系描述
    }

    private static class InteractorWeight {
        Interactor interactor;
        double weight;
        public InteractorWeight(Interactor interactor, double weight) {
            this.interactor = interactor;
            this.weight = weight;
        }
    }

    private static int evaluateMomentInteractivity(Moment moment) {
        String apiKey = SpUtils.getString("OPENAI_API_KEY", "");
        String endpoint = SpUtils.getString("API_ENDPOINT", "https://api.openai.com/");
        String model = SpUtils.getString("API_MODEL", "gpt-4o-mini");

        if (apiKey.isEmpty()) return 2; // 默认中等
        if (!endpoint.endsWith("/")) endpoint += "/";

        OpenAIManager aiManager = new OpenAIManager();
        OpenAiRequest request = new OpenAiRequest();
        request.model = model;
        request.temperature = 0.1f;
        request.messages = new ArrayList<>();
        request.messages.add(new OpenAiRequest.Message("system", "评估以下朋友圈动态的情感强度和引发互动的可能性，考虑真人的互动习惯，不要每条都想回。只返回一个数字：1（弱/低）、2（中等）、3（强/高）。"));
        String contentToEval = moment.content != null && !moment.content.isEmpty() ? moment.content : "[发布了一张图片]";
        request.messages.add(new OpenAiRequest.Message("user", contentToEval));

        try {
            retrofit2.Response<OpenAiResponse> response = aiManager.getApi().createChatCompletion(endpoint + "v1/chat/completions", "Bearer " + apiKey, request).execute();
            if (response.isSuccessful() && response.body() != null && !response.body().choices.isEmpty()) {
                String content = response.body().choices.get(0).message.content.trim();
                if (content.contains("1")) return 1;
                if (content.contains("3")) return 3;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 2;
    }

    private static List<InteractorWeight> calculateInteractionWeights(AppDatabase db, Moment moment, List<Interactor> potentialInteractors) {
        List<InteractorWeight> weights = new ArrayList<>();
        List<RelationshipNode> nodes = db.relationshipNodeDao().getAllNodesSync();
        List<RelationshipEdge> edges = db.relationshipEdgeDao().getAllEdgesSync();
        
        // 尝试找到发布者在关系网中的节点ID
        String publisherNodeId = null;
        if (nodes != null) {
            for (RelationshipNode node : nodes) {
                if ((moment.publisherType == 1 && String.valueOf(moment.publisherId).equals(node.referenceId)) ||
                    moment.publisherName.equals(node.name)) {
                    publisherNodeId = node.id;
                    break;
                }
            }
        }

        String myName = SpUtils.getString("MY_NAME", "我");

        // 如果发布者根本不在关系网内（例如用户创建的游离测试角色），严格限制任何角色互动
        if (publisherNodeId == null) {
            return weights; // 只有在关系网里的角色才能互动
        }

        for (Interactor c : potentialInteractors) {
            double weight = 0.0; 
            c.relationshipWithPublisher = "";
            boolean hasRelationship = false;
            
            if (edges != null && nodes != null) {
                // 找到潜在互动者对应的节点
                String charNodeId = null;
                for (RelationshipNode node : nodes) {
                    if (c.isRealCharacter && String.valueOf(c.id).equals(node.referenceId)) {
                        charNodeId = node.id;
                        break;
                    } else if (c.name.equals(node.name)) {
                        charNodeId = node.id;
                        break;
                    }
                }
                
                if (charNodeId != null) {
                    // 查找边
                    for (RelationshipEdge edge : edges) {
                        if (edge.sourceNodeId.equals(publisherNodeId) && edge.targetNodeId.equals(charNodeId)) {
                            // 发布者 -> 互动者的关系
                            c.relationshipWithPublisher = edge.relation;
                            double intimacyFactor = Math.max(0.5, edge.intimacy / 50.0);
                            double probFactor = Math.max(0.5, edge.interactionProbability * 2.0);
                            weight = intimacyFactor * probFactor;
                            hasRelationship = true;
                            break;
                        } else if (edge.sourceNodeId.equals(charNodeId) && edge.targetNodeId.equals(publisherNodeId)) {
                            // 互动者 -> 发布者的关系
                            c.relationshipWithPublisher = edge.relation;
                            double intimacyFactor = Math.max(0.5, edge.intimacy / 50.0);
                            double probFactor = Math.max(0.5, edge.interactionProbability * 2.0);
                            weight = intimacyFactor * probFactor;
                            hasRelationship = true;
                            break;
                        }
                    }
                }
            }
            
            // 只有有直接关系的角色才赋予权重，否则严格为0，不参与互动（纯虚拟或真实角色都是如此）
            if (hasRelationship && weight > 0) {
                weights.add(new InteractorWeight(c, weight));
            }
        }
        return weights;
    }

    private static List<Interactor> selectInteractorsByWeight(List<InteractorWeight> weights, int count) {
        List<Interactor> selected = new ArrayList<>();
        List<InteractorWeight> pool = new ArrayList<>(weights);
        Random random = new Random();
        
        for (int i = 0; i < count && !pool.isEmpty(); i++) {
            double totalWeight = 0;
            for (InteractorWeight cw : pool) {
                totalWeight += cw.weight;
            }
            
            double r = random.nextDouble() * totalWeight;
            double currentSum = 0;
            
            for (int j = 0; j < pool.size(); j++) {
                currentSum += pool.get(j).weight;
                if (currentSum >= r) {
                    selected.add(pool.get(j).interactor);
                    pool.remove(j);
                    break;
                }
            }
        }
        
        return selected;
    }

    private static boolean generateCommentsBatch(AppDatabase db, Moment moment, List<Interactor> commenters) {
        try {
            OpenAIManager aiManager = new OpenAIManager();
            String apiKey = SpUtils.getString("OPENAI_API_KEY", "");
            String endpoint = SpUtils.getString("API_ENDPOINT", "https://api.openai.com/");
            String model = SpUtils.getString("API_MODEL", "gpt-4o-mini");

            if (apiKey.isEmpty()) return false;
            if (!endpoint.endsWith("/")) endpoint += "/";

            OpenAiRequest request = new OpenAiRequest();
            request.model = model;
            request.messages = new ArrayList<>();

            String myName = SpUtils.getString("MY_NAME", "我");
            StringBuilder systemPrompt = new StringBuilder();
            systemPrompt.append("这是一段社交网络（朋友圈）的情景模拟，你现在正在浏览朋友圈，而不是在一对一的私聊！\n\n");
            systemPrompt.append("你的朋友「").append(moment.publisherName).append("」刚刚发布了一条朋友圈动态：\n")
                       .append(moment.content != null && !moment.content.isEmpty() ? moment.content : "[图片动态]").append("\n\n");
            
            systemPrompt.append("【极度重要警告】：\n");
            systemPrompt.append("发布动态的「").append(moment.publisherName).append("」是你列表里的另一个朋友，绝对不是正在和你聊天的用户/玩家（").append(myName).append("）！\n");
            systemPrompt.append("不要把发帖人当成你的主人/用户，你现在的身份是一个刷朋友圈的旁观者/朋友！\n\n");
            
            systemPrompt.append("你需要同时扮演以下几个人物，分别为这条朋友圈生成一条符合各自人设和关系的评论。评论要简短、符合身份，像真正的微信朋友圈互动一样。\n");
            systemPrompt.append("发帖人（发布者）也可以参与回复别人的评论。大部分情况下的评论不需要@任何人，非极特殊情况坚决不用@，不要把@当作普通称呼。\n\n");
            
            for (Interactor commenter : commenters) {
                systemPrompt.append("【").append(commenter.name).append("】的人设: ").append(commenter.persona).append("\n");
                if (commenter.relationshipWithPublisher != null && !commenter.relationshipWithPublisher.isEmpty()) {
                    systemPrompt.append("与发布者「").append(moment.publisherName).append("」的关系: ").append(commenter.relationshipWithPublisher).append("\n");
                }
                systemPrompt.append("\n");
            }
            
            systemPrompt.append("【特别提示】\n");
            systemPrompt.append("1. 明确当前扮演的人设，不可错认角色或错扮演别的角色。**你是朋友圈里的朋友，绝不能认为自己是发帖人，更不能认为自己是正在玩手机的用户本人。**\n");
            systemPrompt.append("2. [断句规则：一口气说得完的话不加逗号。主谓之间、动宾之间不无故断开。\"的地得\"和介词前不断句。紧张时短句用句号而非逗号。日常对话允许长句一气呵成。断句的疏密跟随情绪，不做机械等距切割。]\n");
            systemPrompt.append("3. 大部分情况下的评论都不需要@任何人。非极特殊要求某人看见的情况不用@。\n");
            systemPrompt.append("4. **不同AI角色之间可以相互评论互动，发帖人也可以回复你们。** 如果你想回复列表里某个人的评论，请在 reply_to_character_id 中填入被回复者的 character_id，如果不回复任何人则留空或设为 null。\n\n");
                       
            systemPrompt.append("你必须严格返回以下 JSON 格式：\n")
                       .append("{\n")
                       .append("  \"comments\": [\n");
            
            for (int i = 0; i < commenters.size(); i++) {
                systemPrompt.append("    {\n")
                           .append("      \"character_id\": \"").append(commenters.get(i).id).append("\",\n")
                           .append("      \"reply_to_character_id\": \"被回复者的ID(可选)\",\n")
                           .append("      \"content\": \"评论内容\"\n")
                           .append("    }").append(i < commenters.size() - 1 ? "," : "").append("\n");
            }
            
            systemPrompt.append("  ]\n")
                       .append("}\n");

            request.messages.add(new OpenAiRequest.Message("user", systemPrompt.toString() + "\n请返回评论的 JSON 数据。"));

            retrofit2.Response<OpenAiResponse> response = aiManager.getApi().createChatCompletion(endpoint + "v1/chat/completions", "Bearer " + apiKey, request).execute();

            if (response.isSuccessful() && response.body() != null && !response.body().choices.isEmpty()) {
                String content = response.body().choices.get(0).message.content;
                android.util.Log.d("MomentSimulator", "Raw API response: " + content);
                
                // 彻底去除可能存在的 Markdown 标记并兼容数组或对象格式
                if (content != null) {
                    content = content.trim();
                    if (content.contains("```json")) {
                        content = content.substring(content.indexOf("```json") + 7);
                        if (content.contains("```")) {
                            content = content.substring(0, content.lastIndexOf("```"));
                        }
                    } else if (content.contains("```")) {
                        content = content.substring(content.indexOf("```") + 3);
                        content = content.substring(0, content.lastIndexOf("```"));
                    }
                    content = content.trim();
                }
                
                org.json.JSONArray commentsArray = null;
                try {
                    if (content != null && content.startsWith("[")) {
                        commentsArray = new org.json.JSONArray(content);
                    } else {
                        // 尝试寻找 {...}
                        int jsonStart = content != null ? content.indexOf("{") : -1;
                        int jsonEnd = content != null ? content.lastIndexOf("}") : -1;
                        if (jsonStart != -1 && jsonEnd != -1 && jsonEnd >= jsonStart) {
                            String jsonStr = content.substring(jsonStart, jsonEnd + 1);
                            JSONObject json = new JSONObject(jsonStr);
                            commentsArray = json.optJSONArray("comments");
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.e("MomentSimulator", "JSON parse error", e);
                }
                
                if (commentsArray != null && commentsArray.length() > 0) {
                    android.util.Log.d("MomentSimulator", "Parsed comments array length: " + commentsArray.length());
                    for (int i = 0; i < commentsArray.length(); i++) {
                        JSONObject commentObj = commentsArray.getJSONObject(i);
                        String charIdStr = commentObj.optString("character_id");
                        String commentContent = commentObj.optString("content");
                        android.util.Log.d("MomentSimulator", "Processing comment for charId: " + charIdStr + " content: " + commentContent);
                        
                        if (commentContent != null && !commentContent.isEmpty()) {
                            // Find the character
                            Interactor commenter = null;
                            for (Interactor c : commenters) {
                                if (c.id.equals(charIdStr)) {
                                    commenter = c;
                                    break;
                                }
                            }
                            
                            if (commenter != null) {
                                // Parse Mentions
                                com.yoyo.jingxi.utils.MomentNotificationManager.processMentions(
                                    db, moment.id, commenter.isRealCharacter ? 1 : 2, commenter.id, commenter.name, commenter.originalCharacter != null ? commenter.originalCharacter.avatarPath : "", commentContent
                                );

                                // 检查是否已经评论过
                                java.util.List<MomentComment> existingComments = db.momentCommentDao().getCommentsForMomentSync(moment.id);
                                boolean hasCommented = false;
                                for (MomentComment c : existingComments) {
                                    if (c.authorId.equals(commenter.id) && c.content.equals(commentContent)) {
                                        hasCommented = true;
                                        break;
                                    }
                                }

                                if (!hasCommented) {
                                    MomentComment comment = new MomentComment();
                                    comment.momentId = moment.id;
                                    comment.authorType = commenter.isRealCharacter ? 1 : 2;
                                    comment.authorId = commenter.id;
                                    comment.authorName = commenter.name;
                                    comment.content = commentContent;
                                    
                                    // 解析回复逻辑
                                    String replyToId = commentObj.optString("reply_to_character_id", null);
                                    if (replyToId != null && !replyToId.isEmpty() && !replyToId.equals("null")) {
                                        comment.replyToId = replyToId;
                                        // 尝试找到被回复人的名字
                                        for (Interactor c : commenters) {
                                            if (c.id.equals(replyToId)) {
                                                comment.replyToName = c.name;
                                                comment.replyToType = c.isRealCharacter ? 1 : 2;
                                                break;
                                            }
                                        }
                                        // 如果被回复的是发帖人
                                        if (replyToId.equals(String.valueOf(moment.publisherId))) {
                                            comment.replyToName = moment.publisherName;
                                            comment.replyToType = moment.publisherType;
                                        }
                                    } else {
                                        comment.replyToType = -1; // 不回复任何人，直接评论动态
                                    }
                                    
                                    // 确保时间戳按顺序递增，避免时间关系错乱
                                    long commentDelay = 1000 + (long) i * 60000; // 每条评论间隔一分钟
                                    comment.timestamp = moment.timestamp + commentDelay;
                                    
                                    db.momentCommentDao().insert(comment);
                                    android.util.Log.d("MomentSimulator", "Inserted comment from " + commenter.name);
                                    
                                    com.yoyo.jingxi.utils.MomentNotificationManager.dispatchNotification(
                                            db, moment.id, 1,
                                            comment.authorType, comment.authorId, comment.authorName, commenter.originalCharacter != null ? commenter.originalCharacter.avatarPath : "",
                                            moment.publisherType, moment.publisherId, comment.content
                                    );
                                } else {
                                    android.util.Log.d("MomentSimulator", "Commenter already commented with same content: " + commenter.name);
                                }
                            } else {
                                android.util.Log.e("MomentSimulator", "Commenter not found for id: " + charIdStr);
                            }
                        }
                    }
                    
                    // 模拟完点赞评论后通知 UI 刷新
                    android.content.Intent broadcastIntent = new android.content.Intent("com.yoyo.jingxi.ACTION_MOMENT_UPDATED");
                    com.yoyo.jingxi.JingxiApplication.getInstance().sendBroadcast(broadcastIntent);
                    
                    return true;
                } else {
                    android.util.Log.e("MomentSimulator", "No comments array in JSON: " + content);
                }
            } else {
                if (response.errorBody() != null) {
                    android.util.Log.e("MomentSimulator", "API Error: " + response.code() + " " + response.errorBody().string());
                } else {
                    android.util.Log.e("MomentSimulator", "API Error: " + response.code() + " " + response.message());
                }
            }
        } catch (Exception e) {
            android.util.Log.e("MomentSimulator", "Exception in generateCommentsBatch", e);
            e.printStackTrace();
        }
        return false;
    }
}
