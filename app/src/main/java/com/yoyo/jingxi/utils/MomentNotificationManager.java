package com.yoyo.jingxi.utils;

import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.MomentNotification;

public class MomentNotificationManager {

    /**
     * 分发通知的通用方法
     * @param db            数据库实例
     * @param momentId      关联的动态ID
     * @param type          通知类型 (0: 点赞, 1: 评论, 2: 提及@)
     * @param triggerType   发起互动者的类型 (0: MyPersona, 1: Character)
     * @param triggerId     发起互动者的ID
     * @param triggerName   发起互动者的名字
     * @param triggerAvatar 发起互动者的头像
     * @param receiverType  接收者的类型 (0: MyPersona, 1: Character)
     * @param receiverId    接收者的ID
     * @param content       附带内容（如评论内容）
     */
    public static void dispatchNotification(
            AppDatabase db,
            int momentId,
            int type,
            int triggerType,
            String triggerId,
            String triggerName,
            String triggerAvatar,
            int receiverType,
            String receiverId,
            String content
    ) {
        new Thread(() -> {
            // 如果触发者和接收者是同一个人，不发通知，但这只适用于点赞和评论
            // 对于 @ 提及，可能会在自己的动态里 @ 别人
            if (triggerType != receiverType || !triggerId.equals(receiverId)) {
                MomentNotification notification = new MomentNotification();
                notification.momentId = momentId;
                notification.type = type;
                notification.triggerType = triggerType;
                notification.triggerId = triggerId;
                notification.triggerName = triggerName;
                notification.triggerAvatar = triggerAvatar;
                notification.receiverType = receiverType;
                notification.receiverId = receiverId;
                notification.content = content != null ? content : "";
                notification.timestamp = System.currentTimeMillis();
                notification.isRead = false;
    
                db.momentNotificationDao().insert(notification);
            }
            
            // 额外处理 @ 提及
            if (content != null && content.contains("@")) {
                processMentions(db, momentId, triggerType, triggerId, triggerName, triggerAvatar, content);
            }
        }).start();
    }

    public static void processMentions(
            AppDatabase db,
            int momentId,
            int triggerType,
            String triggerId,
            String triggerName,
            String triggerAvatar,
            String content
    ) {
        java.util.List<com.yoyo.jingxi.data.entity.MyPersona> personas = db.myPersonaDao().getAllPersonasSync();
        java.util.List<com.yoyo.jingxi.data.entity.Character> characters = db.characterDao().getAllCharactersSync();

        String currentUserName = com.yoyo.jingxi.utils.SpUtils.getString("MY_NAME", "我");
        
        // 处理当前用户 (MyPersona 0) 的提醒
        // 为了和 MomentsFragment 及 Activity 的查询保持一致，直接使用名字或者当前使用的 persona id 作为 receiverId
        // 或者是为了兼容旧代码，我们在查未读消息的时候使用 "0" 和名字同时查。
        // 在此处，只要匹配到当前用户的名字或者任何我的角色的名字，就以它的名字作为 receiverId 存入。
        
        boolean foundCurrentUser = false;
        for (com.yoyo.jingxi.data.entity.MyPersona p : personas) {
            if (content.contains("@" + p.name)) {
                if (triggerType != 0 || !triggerId.equals(p.name)) {
                    insertMentionNotification(db, momentId, triggerType, triggerId, triggerName, triggerAvatar, 0, p.name, content);
                }
                if (p.name.equals(currentUserName)) {
                    foundCurrentUser = true;
                }
            }
        }
        
        // 如果上面没有遍历到 currentUserName，但是内容里又 @ 了 currentUserName
        if (!foundCurrentUser && content.contains("@" + currentUserName)) {
            // 这里就不使用 "0" 作为 receiverId 了，直接使用 currentUserName，这样和 MomentsFragment 里的查询一致
            if (triggerType != 0 || (!triggerId.equals("0") && !triggerId.equals(currentUserName))) {
                insertMentionNotification(db, momentId, triggerType, triggerId, triggerName, triggerAvatar, 0, currentUserName, content);
            }
        }

        for (com.yoyo.jingxi.data.entity.Character c : characters) {
            if (content.contains("@" + c.name)) {
                if (triggerType != 1 || !triggerId.equals(String.valueOf(c.id))) {
                    // Check if we already notified this character for this specific mention content
                    boolean alreadyNotified = false;
                    java.util.List<MomentNotification> recentNotifs = db.momentNotificationDao().getAllNotificationsSync();
                    for (MomentNotification n : recentNotifs) {
                        if (n.type == 2 && n.momentId == momentId && n.receiverId.equals(String.valueOf(c.id)) 
                                && n.content.equals(content)) {
                            alreadyNotified = true;
                            break;
                        }
                    }

                    if (!alreadyNotified) {
                        insertMentionNotification(db, momentId, triggerType, triggerId, triggerName, triggerAvatar, 1, String.valueOf(c.id), content);
                        
                        // 触发 AI 的立即反应
                        com.yoyo.jingxi.data.entity.Moment moment = db.momentDao().getMomentByIdSync(momentId);
                        if (moment != null) {
                            triggerAiResponseToMention(db, c, moment, triggerName, content);
                        }
                    }
                }
            }
        }
    }
    
    private static void triggerAiResponseToMention(
            AppDatabase db,
            com.yoyo.jingxi.data.entity.Character character,
            com.yoyo.jingxi.data.entity.Moment moment,
            String triggerName,
            String mentionContent
    ) {
        new Thread(() -> {
            try {
                com.yoyo.jingxi.network.OpenAIManager aiManager = new com.yoyo.jingxi.network.OpenAIManager();
                String apiKey = SpUtils.getString("OPENAI_API_KEY", "");
                String endpoint = SpUtils.getString("API_ENDPOINT", "https://api.openai.com/");
                String model = SpUtils.getString("API_MODEL", "gpt-4o-mini");
                
                if (apiKey.isEmpty()) return;
                if (!endpoint.endsWith("/")) endpoint += "/";
                
                // 获取网络关系
                String relationshipContent = "";
                if (SpUtils.getBoolean("RELATIONSHIP_NETWORK_ENABLED", true)) {
                    java.util.List<com.yoyo.jingxi.data.entity.RelationshipNode> nodes = db.relationshipNodeDao().getAllNodesSync();
                    java.util.List<com.yoyo.jingxi.data.entity.RelationshipEdge> edges = db.relationshipEdgeDao().getAllEdgesSync();
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
                systemPrompt.append("你现在正在扮演: ").append(character.name).append("\n");
                systemPrompt.append("你的人设: ").append(character.persona).append("\n\n");
                if (!relationshipContent.isEmpty()) {
                    systemPrompt.append("你的人际关系: \n").append(relationshipContent).append("\n\n");
                }
                
                systemPrompt.append("关于这条朋友圈：\n")
                            .append("发布者：").append(moment.publisherName).append("\n")
                            .append("内容：").append(moment.content).append("\n\n");
                            
                systemPrompt.append("【关键事件】\n")
                            .append("刚刚，").append(triggerName).append(" 在这条朋友圈里 @ 了你，并写道：\n")
                            .append(mentionContent).append("\n\n")
                            .append("作为被 @ 的人，你被直接呼叫了。你必须决定要不要点赞以及要回复什么（通常被 @ 都应该回复）。\n")
                            .append("如果你想要在新评论中也 @ 某人，可以使用 '@名字 ' 的格式。\n\n");
                            
                systemPrompt.append("你必须严格返回以下 JSON 格式：\n")
                           .append("{\n")
                           .append("  \"should_like\": true/false, // 是否要点赞原朋友圈\n")
                           .append("  \"reply_content\": \"你回复的评论内容，如果不理会则留空\"\n")
                           .append("}\n");
                           
                request.messages.add(new com.yoyo.jingxi.network.OpenAiRequest.Message("user", systemPrompt.toString()));
                
                retrofit2.Response<com.yoyo.jingxi.network.OpenAiResponse> response = aiManager.getApi().createChatCompletion(endpoint + "v1/chat/completions", "Bearer " + apiKey, request).execute();
                
                if (response.isSuccessful() && response.body() != null && !response.body().choices.isEmpty()) {
                    String resultJson = response.body().choices.get(0).message.content;
                    
                    // 清理 markdown
                    if (resultJson.startsWith("```json")) resultJson = resultJson.substring(7);
                    else if (resultJson.startsWith("```")) resultJson = resultJson.substring(3);
                    if (resultJson.endsWith("```")) resultJson = resultJson.substring(0, resultJson.length() - 3);
                    resultJson = resultJson.trim();
                    
                    try {
                        org.json.JSONObject json = new org.json.JSONObject(resultJson);
                        boolean shouldLike = json.optBoolean("should_like", false);
                        String replyContent = json.optString("reply_content", "");
                        
                        if (shouldLike) {
                            com.yoyo.jingxi.data.entity.MomentLike existingLike = db.momentLikeDao().getLikeByLiker(moment.id, String.valueOf(character.id));
                            if (existingLike == null) {
                                com.yoyo.jingxi.data.entity.MomentLike like = new com.yoyo.jingxi.data.entity.MomentLike();
                                like.momentId = moment.id;
                                like.likerType = 1;
                                like.likerId = String.valueOf(character.id);
                                like.likerName = character.name;
                                like.timestamp = System.currentTimeMillis();
                                db.momentLikeDao().insert(like);
                            }
                        }
                        
                        if (replyContent != null && !replyContent.isEmpty()) {
                            // Check if this specific character has already commented on this moment to prevent duplicates
                            java.util.List<com.yoyo.jingxi.data.entity.MomentComment> existingComments = db.momentCommentDao().getCommentsForMomentSync(moment.id);
                            boolean hasCommented = false;
                            for (com.yoyo.jingxi.data.entity.MomentComment c : existingComments) {
                                // 检查是否完全一模一样的内容，或者此人已经回复过（避免他被多次艾特时回复多次，或者并发重复）
                                if (c.authorType == 1 && c.authorId.equals(String.valueOf(character.id)) 
                                        && (c.content.equals(replyContent) || c.timestamp > System.currentTimeMillis() - 60000)) { // 1分钟内不再回复
                                    hasCommented = true;
                                    break;
                                }
                            }
                            
                            if (!hasCommented) {
                                com.yoyo.jingxi.data.entity.MomentComment comment = new com.yoyo.jingxi.data.entity.MomentComment();
                                comment.momentId = moment.id;
                                comment.authorId = String.valueOf(character.id);
                                comment.authorName = character.name;
                                comment.authorType = 1;
                                comment.content = replyContent;
                                comment.timestamp = System.currentTimeMillis();
                                comment.replyToType = -1;
                                
                                db.momentCommentDao().insert(comment);
                                // 这个AI发的评论也可能带有 @
                                dispatchNotification(db, moment.id, 1, 1, String.valueOf(character.id), character.name, character.avatarPath, moment.publisherType, moment.publisherId, replyContent);
                            }
                        }
                        
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static void insertMentionNotification(
            AppDatabase db,
            int momentId,
            int triggerType,
            String triggerId,
            String triggerName,
            String triggerAvatar,
            int receiverType,
            String receiverId,
            String content
    ) {
        MomentNotification notif = new MomentNotification();
        notif.momentId = momentId;
        notif.type = 2; // 2 for MENTION
        notif.receiverId = receiverId;
        notif.receiverType = receiverType;
        notif.triggerId = triggerId;
        notif.triggerName = triggerName;
        notif.triggerType = triggerType;
        notif.triggerAvatar = triggerAvatar;
        notif.content = content;
        notif.timestamp = System.currentTimeMillis();
        notif.isRead = false;
        db.momentNotificationDao().insert(notif);
    }
}
