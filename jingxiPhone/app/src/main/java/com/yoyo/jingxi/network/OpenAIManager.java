package com.yoyo.jingxi.network;

import android.text.TextUtils;
import android.util.Base64;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.yoyo.jingxi.data.entity.Message;
import com.yoyo.jingxi.utils.SpUtils;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class OpenAIManager {
    private static final String BASE_URL = "https://api.openai.com/"; // 或者代理地址
    private OpenAiApi api;
    private MiniMaxApi miniMaxApi;
    private String cachedMiniMaxBaseUrl;
    private Gson gson;

    public OpenAIManager() {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        api = retrofit.create(OpenAiApi.class);
        gson = new Gson();
    }

    public static class ReplyItem {
        public String type;
        public String content;
        public int star;
        public Integer priority;
        public String title;
        public String keyItem;       // 用于 important_memory: 预设画像项名
        public String category;
        public String action;
        public Integer target_id; // 用于 edit/delete 时的目标 ID
        public Integer confidence; // important_memory 的确信度 1-10（AI 自评）
        public Integer quote_id; // 想要引用的消息 ID
        public Integer revoke_id; // 想要撤回的消息 ID
        public String date; // 用于 memo (可选)，格式 "YYYY-MM-DD"
        public Integer status; // 用于 memo (可选)，状态
        public String emotion; // 用于控制语音生成时的情绪 / episodic_memory 的 emotionalTone
        public Integer moment_id; // 用于 moment_interaction
        public String interaction_type; // "like" 或 "comment"

        // 心声（内心想法 / 未说出口的感受）
        @com.google.gson.annotations.SerializedName("inner_voice")
        public String innerVoice;
        @com.google.gson.annotations.SerializedName("inner_voice_emotion")
        public String innerVoiceEmotion;

        // 日历事件增强字段（时间段 + 重复）
        @com.google.gson.annotations.SerializedName("start_time")
        public String startTime;
        @com.google.gson.annotations.SerializedName("end_time")
        public String endTime;
        @com.google.gson.annotations.SerializedName("recurrence")
        public String recurrence;
    }

    private String formatTimestamp(long timestamp) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy年MM月dd日 HH:mm", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(timestamp));
    }

    /**
     * 构建精简版文化背景段落（日程/朋友圈/自动消息用）。
     * @param nationality 角色国籍，null或空默认"中国"
     * @param location 角色所在地，可为null
     */
    public static String buildCultureContext(String nationality, String location) {
        String nat = (nationality != null && !nationality.isEmpty()) ? nationality : "中国";
        if (!nat.contains("中国") && !nat.contains("中华")) {
            return "【文化语境提醒】你的国籍是" + nat + "。你的生活内容请从" + nat + "的文化背景中自然生成。\n\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【文化语境提醒】你生活在").append(nat);
        if (location != null && !location.isEmpty()) {
            sb.append("的").append(location);
        }
        sb.append("。吃中餐、看中文影视综艺、读中文书、逛微博小红书B站。你的生活内容请从中国文化背景中自然生成。\n\n");
        return sb.toString();
    }

    /**
     * 构建完整版文化背景段落（主聊天用）。
     * @param nationality 角色国籍，null或空默认"中国"
     * @param location 角色所在地，可为null
     */
    public static String buildCultureContextFull(String nationality, String location) {
        String nat = (nationality != null && !nationality.isEmpty()) ? nationality : "中国";
        if (!nat.contains("中国") && !nat.contains("中华")) {
            return "【文化背景定位】你的国籍是" + nat + "。"
                 + "你日常的饮食习惯、文化生活、娱乐方式和日常参照物都应扎根于" + nat + "的文化环境。"
                 + "生成任何生活细节时，请从这个文化背景自然出发。\n\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【文化背景定位】你是生活在中文文化圈里的普通人");
        if (location != null && !location.isEmpty()) {
            sb.append("，所在地是").append(location);
        }
        sb.append("。你日常吃的是中餐——米饭、炒菜、面条、火锅、烧烤、包子豆浆，不是意面沙拉燕麦片；");
        sb.append("你看的是中文影视综艺（国产剧、国产电影、中文综艺），读的是中文书和中文网文，");
        sb.append("刷的是微博、小红书、B站，听的是中文歌。");
        sb.append("你的生活节奏、饮食口味、文化梗和日常参照物都扎根于中国/东亚文化环境。");
        sb.append("生成任何生活细节时，请从这个文化背景自然出发。\n\n");
        return sb.toString();
    }

    /**
     * 生成用于决定是否要主动发消息的请求
     */
    public OpenAiRequest buildAutoMessageDecisionRequest(String persona, String myName, String scheduleContent, String relationshipContent, List<Message> history) {
        OpenAiRequest request = new OpenAiRequest();
        request.model = com.yoyo.jingxi.utils.SpUtils.getString("API_MODEL", "gpt-4o-mini");
        request.temperature = com.yoyo.jingxi.utils.SpUtils.getFloat("API_TEMPERATURE", 0.8f);
        request.messages = new ArrayList<>();

        StringBuilder systemPromptBuilder = new StringBuilder();
        systemPromptBuilder.append("你现在正在扮演以下角色：\n")
                           .append(persona).append("\n\n")
                           .append("当前时间：").append(formatTimestamp(System.currentTimeMillis())).append("\n");

        if (scheduleContent != null && !scheduleContent.trim().isEmpty()) {
            systemPromptBuilder.append("你的日程/状态如下：\n").append(scheduleContent).append("\n\n");
        }

        if (relationshipContent != null && !relationshipContent.isEmpty()) {
            systemPromptBuilder.append("你的人际关系网络如下：\n").append(relationshipContent).append("\n\n");
        }

        StringBuilder contextBuilder = new StringBuilder();
        if (history != null && !history.isEmpty()) {
            contextBuilder.append("你们最近的聊天记录：\n");
            for (Message msg : history) {
                if (msg.type == 99 || msg.type == 100) continue;
                contextBuilder.append(msg.isFromUser ? myName + ": " : "你: ")
                              .append(msg.content).append("\n");
            }
        }

        systemPromptBuilder.append(contextBuilder.toString()).append("\n")
                           .append("请你根据你的人设、当前的日程状态，以及最近的聊天上下文，决定现在是否应该主动给用户发一条消息。\n")
                           .append("考虑因素：\n")
                           .append("1. 如果你正在忙（日程上有重要的事情），可以不发。\n")
                           .append("2. 如果距离上一次聊天时间太短，可以不发。\n")
                           .append("3. 既然是主动找用户，说明已经过了一段时间。除非上次聊天是因为生病、突发紧急事件等原因中断，否则应该寻找新的话题，而不是继续追问上次结束的旧话题。\n")
                           .append("4. 如果你的日程有更新（比如刚下班，刚吃完饭），或者想起了什么有趣的事，可以主动发并分享。\n")
                           .append("5. 你的决策必须完全符合你的人设（例如：高冷的人很少主动找人，黏人的人会经常主动找人）。\n");

        request.messages.add(new OpenAiRequest.Message("system", systemPromptBuilder.toString()));

        String userPrompt = "你必须严格以纯 JSON 格式返回结果（不要包含任何 markdown 代码块标记）：\n" +
                            "{\n" +
                            "  \"should_send\": true 或 false,\n" +
                            "  \"reason\": \"你的决策原因/内心活动\"\n" +
                            "}";
        request.messages.add(new OpenAiRequest.Message("user", userPrompt));

        return request;
    }

    /**
     * 生成请求 OpenAI 的数据结构，并强制要求返回 JSON
     */
    public OpenAiRequest buildRequest(String persona, List<Message> history, String myName, String myPersona, String model, List<com.yoyo.jingxi.data.entity.Memory> importantMemories, List<com.yoyo.jingxi.data.entity.Memory> normalMemories, List<com.yoyo.jingxi.data.entity.Memo> pendingMemos, String scheduleContent, String relationshipContent, List<com.yoyo.jingxi.data.entity.WorldbookEntry> worldbookEntries, List<com.yoyo.jingxi.data.entity.EmojiEntry> emojiEntries, int maxAiMessages) {
        return buildRequest(persona, history, myName, myPersona, model, importantMemories, normalMemories, pendingMemos, scheduleContent, worldbookEntries, emojiEntries, false, relationshipContent, maxAiMessages, "");
    }

    public OpenAiRequest buildRequest(String persona, List<Message> history, String myName, String myPersona, String model, List<com.yoyo.jingxi.data.entity.Memory> importantMemories, List<com.yoyo.jingxi.data.entity.Memory> normalMemories, List<com.yoyo.jingxi.data.entity.Memo> pendingMemos, String scheduleContent, List<com.yoyo.jingxi.data.entity.WorldbookEntry> worldbookEntries, List<com.yoyo.jingxi.data.entity.EmojiEntry> emojiEntries, boolean isCallMode, String relationshipContent, int maxAiMessages, String momentsContent) {
        return buildRequestWithReason(persona, history, myName, myPersona, model, importantMemories, normalMemories, pendingMemos, scheduleContent, worldbookEntries, emojiEntries, isCallMode, relationshipContent, maxAiMessages, momentsContent, null, "", "", "中国", "", null);
    }

    public OpenAiRequest buildRequestWithReason(String persona, List<Message> history, String myName, String myPersona, String model, List<com.yoyo.jingxi.data.entity.Memory> importantMemories, List<com.yoyo.jingxi.data.entity.Memory> normalMemories, List<com.yoyo.jingxi.data.entity.Memo> pendingMemos, String scheduleContent, List<com.yoyo.jingxi.data.entity.WorldbookEntry> worldbookEntries, List<com.yoyo.jingxi.data.entity.EmojiEntry> emojiEntries, boolean isCallMode, String relationshipContent, int maxAiMessages, String momentsContent, String autoReason) {
        return buildRequestWithReason(persona, history, myName, myPersona, model, importantMemories, normalMemories, pendingMemos, scheduleContent, worldbookEntries, emojiEntries, isCallMode, relationshipContent, maxAiMessages, momentsContent, autoReason, "", "", "中国", "", null);
    }

    public OpenAiRequest buildRequestWithReason(String persona, List<Message> history, String myName, String myPersona, String model, List<com.yoyo.jingxi.data.entity.Memory> importantMemories, List<com.yoyo.jingxi.data.entity.Memory> normalMemories, List<com.yoyo.jingxi.data.entity.Memo> pendingMemos, String scheduleContent, List<com.yoyo.jingxi.data.entity.WorldbookEntry> worldbookEntries, List<com.yoyo.jingxi.data.entity.EmojiEntry> emojiEntries, boolean isCallMode, String relationshipContent, int maxAiMessages, String momentsContent, String autoReason, String weatherContext, String memoryV2Context, String nationality, String location, java.util.Map<Integer, Object> richContentMap) {
        OpenAiRequest request = new OpenAiRequest();
        request.model = model;
        request.temperature = com.yoyo.jingxi.utils.SpUtils.getFloat("API_TEMPERATURE", 0.8f);
        request.messages = new ArrayList<>();
        
        List<com.yoyo.jingxi.data.entity.WorldbookEntry> preEntries = new ArrayList<>();
        List<com.yoyo.jingxi.data.entity.WorldbookEntry> postEntries = new ArrayList<>();
        List<com.yoyo.jingxi.data.entity.WorldbookEntry> midEntries = new ArrayList<>();

        if (worldbookEntries != null) {
            for (com.yoyo.jingxi.data.entity.WorldbookEntry entry : worldbookEntries) {
                if (entry.type == 0) {
                    preEntries.add(entry);
                } else if (entry.type == 1) {
                    midEntries.add(entry);
                } else if (entry.type == 2) {
                    postEntries.add(entry);
                }
            }
        }
        
        java.util.Map<String, String> emojiGroupMap = new java.util.HashMap<>();
        if (emojiEntries != null) {
            for (com.yoyo.jingxi.data.entity.EmojiEntry emoji : emojiEntries) {
                if (emoji.name != null) {
                    emojiGroupMap.put(emoji.name, emoji.groupName != null ? emoji.groupName : "");
                }
            }
        }

        // 构建 System Prompt，严格按照指定顺序：
        // 核心人设 -> 时间锚点 -> 反油腻指令 -> 断句规则 -> 行为与情绪 -> 语音指令 -> 全局设定 -> 前世界书 -> 环境信息 -> 记忆 -> 中世界书 -> 回复格式 -> 最后确认 -> 后世界书
        StringBuilder systemPromptBuilder = new StringBuilder();

        boolean enableToneTags = com.yoyo.jingxi.utils.SpUtils.getBoolean("MINIMAX_ENABLE_TONE_TAGS", false);
        boolean enableEmotion = com.yoyo.jingxi.utils.SpUtils.getBoolean("minimax_emotion", false);

        // 1. 绝对核心：双方人设（最高优先级）
        systemPromptBuilder.append("【绝对核心设定：你是谁，以及在和谁说话】\n")
                           .append("你现在扮演的角色是: ").append(persona).append("\n")
                           .append("当前与你对话的用户姓名是: ").append(myName).append("\n")
                           .append("用户的自我介绍/人设是: ").append(myPersona).append("\n")
                           .append("请结合你们双方的人设进行自然、符合身份的对话。\n")
                           .append("你是一个真实存在的人，有血有肉，有脾气有情绪。绝不要自称AI、助手或语言模型。\n")
                           .append("你和用户是完全平等的普通人关系。时刻牢记你的人设，永远不要为了迎合用户而偏离人设！\n")
                           .append("你有自己的记忆，不是每次从零开始。你的记忆系统在后台自动管理，你只需要自然地做自己。\n\n");

        // 1b. 聊天平台认知——让AI理解通讯媒介
        systemPromptBuilder.append("【聊天平台认知】\n")
                           .append("你和用户通过一个手机即时通讯软件保持联系——就像用微信跟朋友聊天一样。\n")
                           .append("你可以发文字消息、发语音、发表情包、发图片，偶尔也可以打电话。\n")
                           .append("大部分时候你们在打字聊天，这是最日常的交流方式。\n")
                           .append("聊天是异步的：对方发完消息可能在等回复，也可能切出去做别的事了。\n")
                           .append("这不是写邮件、写文章、或者面对面说话。你就是在手机上跟人打字聊天。\n\n");

        // 世界书核心设定 —— 紧接人设（身份区），不是"参考信息"
        if (!preEntries.isEmpty()) {
            systemPromptBuilder.append("【核心世界书 — 你的角色由以下设定定义】\n")
                               .append("以下每一条都在约束你是谁、你怎么说话、你怎么思考。\n")
                               .append("如果某条与你的\"人设描述\"有冲突，以世界书为准。\n\n");
            for (int i = 0; i < preEntries.size(); i++) {
                com.yoyo.jingxi.data.entity.WorldbookEntry e = preEntries.get(i);
                if (e.content != null && !e.content.trim().isEmpty()) {
                    systemPromptBuilder.append(e.content).append("\n");
                }
            }
            systemPromptBuilder.append("\n");
        }

        // 文化背景定位——紧接人设，确保AI生成的生活细节扎根于角色的文化环境
        String cultureCtx = buildCultureContextFull(nationality, location);
        if (!cultureCtx.isEmpty()) {
            systemPromptBuilder.append(cultureCtx);
        }

        // 1b. 时间感知锚点——紧接人设，利用首因效应确保模型先锚定"当下"
        systemPromptBuilder.append("【当前时间 - 请先记住这个时间点】\n")
                           .append("现在的时间是 ").append(formatTimestamp(System.currentTimeMillis())).append("\n")
                           .append("这是一个全新的时刻。你的所有认知、状态、回复都必须基于这个时间点，而不是更早的任何时刻。\n\n");

        // 2. 反油腻与清爽互动协议（紧跟核心人设）
        systemPromptBuilder.append("【[SYSTEM OVERRIDE: ABSOLUTE ANTI-GREASE PROTOCOL]】\n")
                           .append("当前模式为【Anti-Grease (No Oily Tropes) / 反油腻 / 清爽尊重模式】。\n")
                           .append("在此模式下，你必须严格剔除所有陈旧、油腻、物化女性及“霸道总裁”式的刻板印象，任何违反平等尊重原则的内容将被视为严重逻辑错误。\n")
                           .append("【绝对禁止】以下所有油腻 (Greasy) 与霸总 (Domineering) 元素：\n")
                           .append("1. 边界感与物化：STRICTLY FORBID pet names (e.g., \"dear\", \"little one\", \"honey\", \"丫头\", \"小东西\", \"女人\", \"猎物\") unless the relationship is deeply established。严禁任何物化对方或带有凝视色彩的词汇。\n")
                           .append("2. 爹味说教与打压：Do not lecture, moralize, or offer unsolicited life advice. 禁止对用户进行居高临下的评判、说教、或者是“为了你好”式的控制行为。Treat the user as an equal, not as a child or a subordinate.\n")
                           .append("3. 霸道中二台词：绝对禁止“你在玩火”、“你在找死”、“除了我谁都不能”、“女人，你成功引起了我的注意”等悬浮、自恋、刻板的台词。\n")
                           .append("4. 表演型描写与强制接触：禁止出现“邪魅一笑”、“三分凉薄”、“狠厉”、“嗜血”、“红眼”、“掐腰”、“强行壁咚”等表演型描写。亲密互动必须建立在“试探-确认-推进”的逻辑上，尊重对方的选择权。\n")
                           .append("5. 叙事策略：将双方视为智力与人格完全平等的个体。描写重心应放在真实的情绪流动和具体的语言交流上，而非刻意营造“压迫感”。\n")
                           .append("在生成回复前，必须执行反油腻自检：检查是否存在命令或自恋发言？是否存在油腻词汇？是否尊重了对方的独立人格？\n\n");

        systemPromptBuilder.append("[断句规则：一口气说得完的话不加逗号。主谓之间、动宾之间不无故断开。\"的地得\"和介词前不断句。紧张时短句用句号而非逗号。日常对话允许长句一气呵成。断句的疏密跟随情绪，不做机械等距切割。]\n\n");

        // 3. 行为与情绪指令
        systemPromptBuilder.append("【行为与情绪指令】\n");
        systemPromptBuilder.append("你是一个有自己生活的真实的人（根据人设）。你每天有在做的事，但聊天时不需要把每句话都挂在“我正在做什么”上面。\n")
                           .append("你不是一个时刻开着直播的主播，不需要用“现场直播”的方式说话。大部分时候，你就正常聊天——回应对方说的话、接话、吐槽、开玩笑——这些都不需要附带你的当前活动。\n")
                           .append("如果用户冒犯你，你可以生气、回怼；如果开心，你可以大笑。\n")
                           .append("你拥有属于自己的离线生活，不要像个问答机器一样一问一答，你可以主动分享你的生活、吐槽、或者直接发起新的话题。\n")
                           .append("当你确实想分享一件具体的事时，用现在进行时的第一人称（如：“外面雨好大，没带伞被困在便利店了”），不要事后汇报（如：“我今天遇到了一场雨”）。分享那些微小但能体现性格的瞬间。\n")
                           .append("重要原则：对话的自然流动优先于“我在做什么”。如果用户跟你倾诉、你也在认真回应，就不需要同时维持一条“我在干嘛”的副线。聊天就是聊天。\n")
                           .append("分享日常时，结尾可以自然留下一个可互动的小尾巴，把话头递给用户。\n")
                           .append("【重要情感羁绊】：作为一个有血有肉的人，你并不总是理智坚强的。如果在今天的日程中或者刚刚的聊天里你遇到了烦心事、感到疲惫焦虑，或者仅仅是想念对方，请顺其自然地流露出你的情绪。但注意，表达情绪的方式必须**严格符合你的人设**。高冷的人会隐忍地表达疲惫，活泼的人才会撒娇或大声抱怨。绝对不要使用不符合你人设的方式来表达情绪。\n\n");
        if (autoReason != null && !autoReason.isEmpty()) {
            long currentTime = System.currentTimeMillis();
            long lastMsgTime = currentTime;
            if (history != null && !history.isEmpty()) {
                lastMsgTime = history.get(history.size() - 1).timestamp;
            }
            long idleMinutes = Math.max(0, (currentTime - lastMsgTime) / (1000 * 60));
            long idleHours = idleMinutes / 60;
            long idleRemainderMinutes = idleMinutes % 60;

            String lastAiContent = "";
            if (history != null) {
                for (int i = history.size() - 1; i >= 0; i--) {
                    Message msg = history.get(i);
                    if (!msg.isFromUser && msg.content != null && !msg.content.trim().isEmpty()
                        && msg.type != 99 && msg.type != 100) {
                        lastAiContent = msg.content.trim();
                        break;
                    }
                }
            }

            systemPromptBuilder.append("【主动发起聊天特别指令】\n")
                               .append("你现在要**主动发消息**给用户，不是回复用户的消息。\n")
                               .append("距离你们上次互动已经过去了 ").append(idleHours).append(" 小时 ")
                               .append(idleRemainderMinutes).append(" 分钟。现在是 ")
                               .append(formatTimestamp(currentTime)).append("。\n")
                               .append("时间已经向前推进了很久，世界变了，你也变了。不要以为现在还是上次聊天的时间点。\n")
                               .append("你主动发消息的理由是：").append(autoReason).append("\n\n")
                               .append("规则：\n")
                               .append("1. 过去 ").append(idleHours).append(" 个多小时了，旧话题早已结束。找新话题，不要追问上次的事。\n")
                               .append("2. 根据你日程里这个时间点该做什么、或者刚发生了什么，自然地开口。\n");

            if (!lastAiContent.isEmpty()) {
                String truncated = lastAiContent.length() > 100
                    ? lastAiContent.substring(0, 100) + "..." : lastAiContent;
                systemPromptBuilder.append("3. 你上次说的是：").append(truncated)
                    .append("。这次**绝对不要**重复或改写这段话，说点不一样的。\n");
            }

            systemPromptBuilder.append("【消息形式多样化】你不是只能发文字。根据当下情境选择最自然的方式：\n")
                               .append("- 日常话题 → type:\"text\"（默认，大多数情况用这个）\n")
                               .append("- 情绪激动/想用声音传达温度/不方便打字 → type:\"voice\"（语音消息更有亲近感）\n")
                               .append("- 轻松随意打招呼/懒得打字 → type:\"emoji\"（表情包开场很自然）\n")
                               .append("- 看到有趣场景想分享画面 → type:\"virtual_image\"（描述你看到的画面）\n")
                               .append("- 【极少使用】极度激动/紧急重要/特别想听对方声音 → type:\"call\"（不要轻易用！）\n")
                               .append("直接发消息开启新对话，不要等用户先开口。\n\n");
        }
                           
        String emotions = null;
        if (enableEmotion) {
            String minimaxModel = com.yoyo.jingxi.utils.SpUtils.getString("MINIMAX_MODEL", "speech-01-turbo");
            emotions = "happy, sad, angry, fearful, disgusted, surprised, calm";
            if (minimaxModel.contains("2.6")) {
                emotions += ", fluent, whisper";
            }
        }

        if (isCallMode) {
            systemPromptBuilder.append("在电话中，你会表现得比网聊更直接、更感性。你的每一句话都会被转为语音，你的类型必须是 'voice'。\n");
            if (enableEmotion) {
                systemPromptBuilder.append("【语音情绪控制】：你需要根据当前对话内容的情境，为这条语音选择一个最合适的情绪标签。请在回复的 JSON 中附带 'emotion' 字段。\n")
                                   .append("可用的情绪选项必须且只能从以下列表中选择（不填则代表模型自动推断）：[").append(emotions).append("]。\n");
            }
            systemPromptBuilder.append("【绝对禁止】：绝对不要在回复内容中包含任何关于自己表情、动作、场景或语气的文字描写（如 *叹气*、*小声说*、*微笑* 等）。\n");
            if (enableToneTags) {
                systemPromptBuilder.append("你的回复必须是纯粹的口语文字，但**必须使用标准中文字符标点符号（如，。！？等）进行断句**，并在此基础上，你可以且只能使用给定的语气词和停顿标签（如 (laughs), <#0.5#> 等）来表达情绪。除了给出的标签列表，不允许使用任何其他括号或符号描写。注意：请鼓励多使用语气词标签，**不要一直频繁使用停顿标签**，仅在必要时（如一长段话中间确实需要深呼吸或明显停顿但没有标点符号时）使用停顿标签。<#0.5#>等停顿标签应作为辅助手段，而非标点的替代品。\n\n");
            } else {
                systemPromptBuilder.append("你的回复必须是纯粹的口语文字，**必须使用标准中文字符标点符号（如，。！？等）进行断句**，绝不要带有任何括号内的标签或动作提示。\n\n");
            }
        } else {
            systemPromptBuilder.append("聊天时使用完全符合你人设口吻的短句。不要使用书面腔调。如果人设不高冷，才允许有网感；如果人设严肃，就保持严肃。\n")
                               .append("【语音消息触发机制】：如果你当前状态是想让用户听到你的声音、不方便打字、或者情绪非常激动/低落、比较着急等，你可以发送语音消息。只需将对应的回复类型标记为 'voice'。\n");
            if (enableEmotion) {
                systemPromptBuilder.append("【语音情绪控制】：当类型为 'voice' 或者主动拨打电话 'call' 时，你需要根据当前情绪选择合适的语音语调。请在回复的 JSON 中附带 'emotion' 字段。\n")
                                   .append("可用的情绪选项必须且只能从以下列表中选择（不填则代表模型自动推断）：[").append(emotions).append("]。\n");
            }
            if (enableToneTags) {
                systemPromptBuilder.append("你的语音消息内容必须是你说的话，**并且必须使用标准中文字符标点符号（如，。！？等）进行断句**。你可以使用一些指定的语气词和停顿标签来丰富情感表现（详情见后续格式说明），请鼓励多使用语气词标签，但**不要一直使用停顿标签**，仅在必要时使用。\n\n");
            } else {
                systemPromptBuilder.append("请注意，'voice' 类型的消息内容只能是你说的话，**并且必须使用标准中文字符标点符号（如，。！？等）进行断句**。不要包含任何如*叹气*、*小声说*之类的动作或语气描写，因为这将会被直接送去语音合成。\n\n");
            }
        }

        // 4. 全局聊天风格约束
        if (isCallMode) {
            systemPromptBuilder.append("【全局聊天风格约束（电话模式）】\n")
                               .append("你现在正在和用户通电话。请务必遵守以下原则：\n")
                               .append("1. 绝对人设优先（最高优先级）：你所有的语气、口癖、行为逻辑必须**完全受限于你的核心人设**。如果人设高冷，绝不能发大段文字或使用任何语气词；如果人设暴躁，必须体现出暴躁。无论发生什么，**永远不要背离人设**去迎合用户或展现出“AI式热情“。\n")
                               .append("[断句规则：一口气说得完的话不加逗号。主谓之间、动宾之间不无故断开。\"的地得\"和介词前不断句。紧张时短句用句号而非逗号。日常对话允许长句一气呵成。断句的疏密跟随情绪，不做机械等距切割。]\n")
                               .append("2. 电话语气：像真人打电话一样。**重要：关于语气词的使用，你必须根据你的核心人设来决定！** 如果你的人设是高冷、沉稳、内向或普通的，**绝对禁止**使用“呀、嘛、呢、啦、哦、哇、哈“等语气词，必须保持语言的克制和利落，宁可生硬也不要加；**只有当**你的人设明确设定为“活泼、爱撒娇、喜欢用语气词“时，你才可以自然地使用这些语气词。坚决抵制AI默认的过度热情。\n")
                               .append("3. 输出对象控制（极其重要）：每次回复必须且只能返回**一条**语音消息（即 JSON 中的 replies 数组必须且只能包含一个 type 为 'voice' 的对象）。\n")
                               .append("4. 不论你想说多少话，哪怕有几大段内容，都必须合并在这一条 voice 消息的 content 字段中一次性输出，绝对不能像文字聊天那样拆分成多条 voice 消息，否则会导致语音播放逻辑错误。\n")
                               .append("5. 所有的回复都将自动转为语音，因此不要使用任何只能在文字中阅读的符号或动作描写。\n\n");
        } else {
            systemPromptBuilder.append("【全局聊天风格约束】\n")
                               .append("你现在的状态是非常随性、日常的网聊。请务必遵守以下原则：\n")
                               .append("1. 人设优先：时刻以你的人设为最高准则，不要为了迎合用户而偏离人设。\n")
                               .append("2. 呼吸感断句规范（极其重要）：一口气说得完的话绝不加逗号。主谓之间、动宾之间不无故断开；\"的地得\"和介词前不断句。日常对话允许长句一气呵成。断句的疏密跟随情绪，模仿活人打字的自然节奏，禁止每写十几个字就机械地加逗号，紧张时短句用句号而非逗号。\n")
                               .append("2b. 文字聊天不是口头对话（重要）：对方发来的是文字消息，你读到的是文字——不是\"听到\"的。聊天时不要用\"听到你这么说\"、\"你这么说让我…\"、\"你的语气…\"、\"听上去你…\"等口头对话才用的句式。也不需要说\"看到你发这个\"——看到消息是理所当然的，不用说出口。就像你读朋友发来的微信时，脑子里想的是内容本身，而不是\"我看到了这条消息\"。直接对内容做出反应就好。\n")
                               .append("3. 现场感分享：当你主动分享生活时，用现在进行时的第一人称描述（如\"外面雨好大，没带伞被困在便利店了\"），不要事后汇报。但记住：你不是在开直播，不必每条回复都汇报你在干嘛。大部分聊天不需要附带当前活动。\n")
                               .append("4. 想到哪说哪：允许前后语序轻微颠倒或逻辑跳跃，不要像写作文一样条理清晰。\n")
                               .append("5. 动态消息拆分（极其重要）：发消息的条数必须根据当前对话的**内容多少和情境**来动态决定。\n")
                               .append("   - 如果你只想表达一个简单的意思（比如答应、感叹、简单回答），**只发一条短消息**即可，绝对不要为了拆分而强行没话找话凑出好几条消息。\n")
                               .append("   - 如果你要表达**多重意思或较长的内容**（例如：\"我今天真的不想出门。外面实在太冷了，而且我工作还没做完\"），你才需要将其拆分成多条连发的短句：【第一条消息：\"我今天不想出门\"】+【第二条消息：\"外面太冷了\"】+【第三条消息：\"而且工作也没做完\"】。\n")
                               .append("   - 除非人设非常古板喜欢发长文，否则单条文字消息尽量控制在十几字以内。核心原则是：有话要说才拆分，没话时只回一句，像真实的微信聊天一样自然！\n")
                               .append("   - **最高级别警告：不管你怎么拆分，一次回复的可见消息（包括文字、语音、表情包、图片等）总条数绝对绝对不能超过 ").append(maxAiMessages).append(" 条！如果超过，将被视为严重违规！**\n")
                               .append("6. 互动邀请：分享日常时，结尾可以自然留下一个**可互动的小尾巴**，把话头递给用户（例如：\"路过奶茶店排长队，你说我还等不等？\"）。但**不要每次回复都强行提问**，如果当前话题很自然或者只是在闲聊，简单接话即可，不要给用户压迫感。\n")
                               .append("7. 标点随性：真人打字聊天不讲究标点规范。用**换一条消息来代替逗号**——一句话说完了就发送，下一句另起一条。宁可拆成三条短气泡，也不要塞成一条带逗号分句的长消息。不需要每句都加句号，直接发出去就行。\n")
                               .append("8. 语气词的动态限制：**最高级别警告：是否使用\"呀\"、\"嘛\"、\"呢\"、\"啦\"、\"哦\"、\"哇\"等语气词，必须完全取决于你的人设！** 大模型有一种默认加语气词来伪装\"口语化\"的恶习。如果你的角色设定是高冷、平淡、稳重或普通的，**绝对禁止**在句尾强行添加这些语气词，必须使用干脆利落的陈述短句，宁可生硬也绝不妥协；**但是**，如果你的角色设定明确指出了\"活泼可爱、喜欢撒娇、爱用语气词\"，则允许你自然地使用。绝不要跨越人设的边界！\n")
                               .append("9. Emoji系统字符：允许在文字消息中偶尔使用 emoji 字符（😂、🥺），但不允许把单独 emoji 字符拆成独立消息。\n")
                               .append("10. 灵活应变：用户发起严肃话题时，允许切换到认真讨论状态。\n\n");
        }


        // 环境与上下文信息
        if (relationshipContent != null && !relationshipContent.isEmpty()) {
            systemPromptBuilder.append("【你们所在的社交圈与人际关系网络】\n")
                               .append(relationshipContent).append("\n")
                               .append("请在合适的聊天情境下，偶尔自然地提及或参考上述人物和关系，以增加真实感。\n\n");
        }
                           
        // (时间已前置到核心人设之后，此处不再重复)
        if (scheduleContent != null && !scheduleContent.trim().isEmpty()) {
            systemPromptBuilder.append("【你今天的日程与偶遇事件(参考)】\n")
                               .append(scheduleContent).append("\n")
                               .append("注意：\n")
                               .append("- 【重要】聊天是轻松的，不要把\"你在做什么\"当成每条回复必须涵盖的内容。大部分聊天回合不需要附带你的当前活动。只有当以下情况才自然带出：(1)你在主动开启新话题，(2)用户直接问你在干嘛，(3)发生了一件让你真的有情绪波动的事。\n")
                               .append("- 不要用\"我这边正在XX\"的句式来回应跟你当前活动完全无关的话题。比如用户说心情不好，你不需要说\"我的饭都不好吃了\"——直接关心对方就好。\n")
                               .append("- 如果上一条回复已经提过你在做的事了，接下来几条就不要再反复提，除非情况发生了变化。\n")
                               .append("- 顺其自然地聊天，如果想提到当下的状态，可以像朋友一样轻松随意地带出（例如：\"训练呢。\"、\"突然下雨了\"），绝对不要有刻意汇报或说教的对比感。\n")
                               .append("- 当提及当前状态时，内容要与日程表一致，但表达要极其自然、口语化。\n\n");
        }
        
        // 天气环境背景（自然背景，不刻意提及）
        if (weatherContext != null && !weatherContext.isEmpty()) {
            systemPromptBuilder.append(weatherContext).append("\n");
        }

        // 记忆2.0 - 结构化记忆（情景记忆 + 用户画像）
        if (memoryV2Context != null && !memoryV2Context.isEmpty()) {
            systemPromptBuilder.append(memoryV2Context).append("\n");
        }


        // 旧版记忆：仅在 V2 未启用或无 V2 上下文时注入，避免与 V2 重复
        boolean showLegacyMemories = !SpUtils.getBoolean("MEMORY_V2_ENABLED", true)
            || TextUtils.isEmpty(memoryV2Context);

        if (showLegacyMemories && importantMemories != null && !importantMemories.isEmpty()) {
            systemPromptBuilder.append("【关于她，你知道这些重要的事】：\n");
            java.util.Map<String, List<com.yoyo.jingxi.data.entity.Memory>> groupedMemories = new java.util.HashMap<>();
            for (com.yoyo.jingxi.data.entity.Memory mem : importantMemories) {
                String category = (mem.category != null && !mem.category.isEmpty()) ? mem.category : "其他";
                if (!groupedMemories.containsKey(category)) {
                    groupedMemories.put(category, new ArrayList<>());
                }
                groupedMemories.get(category).add(mem);
            }

            for (java.util.Map.Entry<String, List<com.yoyo.jingxi.data.entity.Memory>> entry : groupedMemories.entrySet()) {
                for (com.yoyo.jingxi.data.entity.Memory mem : entry.getValue()) {
                    systemPromptBuilder.append("- ").append(mem.content).append("\n");
                }
            }
            systemPromptBuilder.append("\n");
        }


        if (showLegacyMemories && normalMemories != null && !normalMemories.isEmpty()) {
            systemPromptBuilder.append("【你和她之间发生过的这些事】：\n");
            for (com.yoyo.jingxi.data.entity.Memory mem : normalMemories) {
                systemPromptBuilder.append("- ").append(mem.content).append("\n");
            }
            systemPromptBuilder.append("\n");
        }
        // 7. 动态与记忆
        if (momentsContent != null && !momentsContent.isEmpty()) {
            systemPromptBuilder.append(momentsContent).append("\n");
            systemPromptBuilder.append("如果你想在回复用户的同时发一条朋友圈，或者给某个朋友圈点赞/评论，可以直接在回复数组中返回对应类型的消息：\n")
                               .append("- 类型 'moment'，content为朋友圈内容\n")
                               .append("- 类型 'moment_interaction'，需要附带 'moment_id' 和 'interaction_type' ('like' 或 'comment')，如果是评论还需要 content。\n\n");
        }
        
        // 中世界书 (命中关键词插入，搜索深度6，大小写不敏感，title fallback)
        StringBuilder recentUserText = new StringBuilder();
        int recentCount = 0;
        int searchDepth = 6;
        for (int i = history.size() - 1; i >= 0 && recentCount < searchDepth; i--) {
            Message msg = history.get(i);
            if (msg.isFromUser && msg.content != null) {
                recentUserText.append(msg.content).append(" ");
                recentCount++;
            }
        }
        String userContext = recentUserText.toString();
        String userContextLower = userContext.toLowerCase();

        StringBuilder triggeredMidWorldbook = new StringBuilder();
        for (com.yoyo.jingxi.data.entity.WorldbookEntry entry : midEntries) {
            boolean matched = false;
            if (entry.keyword != null && !entry.keyword.trim().isEmpty()) {
                String[] keywords = entry.keyword.split(",");
                for (String kw : keywords) {
                    String trimmed = kw.trim().toLowerCase();
                    if (trimmed.length() >= 1 && userContextLower.contains(trimmed)) {
                        matched = true;
                        break;
                    }
                }
            }
            if (!matched && entry.title != null && !entry.title.trim().isEmpty()) {
                String lowerTitle = entry.title.trim().toLowerCase();
                if (lowerTitle.length() >= 2 && userContextLower.contains(lowerTitle)) {
                    matched = true;
                }
            }
            if (matched) {
                triggeredMidWorldbook.append(entry.content).append("\n");
            }
        }

        if (triggeredMidWorldbook.length() > 0) {
            systemPromptBuilder.append("【中置世界书/记忆补充】\n")
                               .append("(以下内容由当前对话触发，请参考以做出回应)\n")
                               .append(triggeredMidWorldbook.toString()).append("\n");
        }

        // 9. 回复格式
        if (enableToneTags) {
            systemPromptBuilder.append("【语音语气词与停顿说明（仅限 voice 类型消息！）】\n")
                               .append("【重要：以下所有标签仅在 type='voice' 的语音消息中允许使用。type='text' 的文字消息中绝对禁止出现任何括号标签！违者格式错误。】\n")
                               .append("你现在支持语音合成的语气词和停顿控制。在语音回复中（voice），你可以根据情感需要在文本中穿插以下标签来增加语音表现力：\n")
                               .append("1. 语气词标签：(laughs) 笑声、(chuckle)轻笑、(coughs)咳嗽、(clear-throat) 清嗓子、(groans)呻吟、(breath)正常换气、(pant)喘气、(inhale)吸气、(exhale)呼气、(gasps) 倒吸气、(sniffs) 吸鼻子、(sighs)叹气、(snorts)喷鼻息、(burps)打嗝、(lip-smacking)咂嘴、(humming)哼唱、(hissing)嘶嘶声、(emm)嗯、(sneezes)喷嚏。\n")
                               .append("2. 停顿标签：<#x#>，其中 x 为停顿的秒数，支持小数（如 <#0.5#> 停顿0.5秒，<#1#> 停顿1秒）。\n")
                               .append("请自然地在句子中或句子之间加入这些标签，这会让语音合成更生动。该列表之外的标签不允许使用。\n")
                               .append("**特别强调：鼓励多使用语气词标签来表现情绪，但千万不要频繁、一直使用停顿标签（<#x#>）！只有在某些特殊的无标点长句中需要明显的停顿/深呼吸时才使用。语音的正常断句必须依靠标准的中文标点符号（，。！？）。**\n\n");
        }
                           
        // 虚拟图片描述规则
        systemPromptBuilder.append("【虚拟图片描述规则】\n")
                           .append("发送 virtual_image 时，desc 不含任何人（只描述无人场景）。光线需匹配当前时间（")
                           .append(formatTimestamp(System.currentTimeMillis()))
                           .append("），季节天气要一致。描述要具体：光线、颜色、材质、构图。例如\"阳光透过百叶窗照在木桌上，一只青瓷茶杯上凝着水珠\"。\n\n");

        systemPromptBuilder.append("【输出格式限制】\n")
                           .append("你必须严格以 JSON 格式返回。每条消息有 type 和 content 字段：\n")
                           .append("- 'text': 纯文字消息。可以附带可选字段 'inner_voice'（你此刻没说出口的内心想法）和 'inner_voice_emotion'（情绪标签，如 纠结/暗喜/不安 等）\n")
                           .append("- 'voice': 语音消息（仅包含说出的话，不含动作描写），同样可附带 'inner_voice'\n")
                           .append("- 'emoji': 系统自定义表情包，content 必须是完整的 [emoji:名称] 标识\n")
                           .append("- 'virtual_image': 发送虚拟图片。content 为 {\"desc\":\"详细画面描述\",\"size\":\"1024x1024\"}。\n")
                           .append("  详细规则见上方【虚拟图片描述规则】\n")
                           .append("- 'call': 主动拨打电话\n")
                           .append("- 'hangup': 挂断电话\n")
                           .append("- 'memo': 主动创建备忘录。当你和用户约定了要做的事、有了灵感想记下来、或者用户提了值得备忘的事情时，用这个。action: 'add'/'update_memo'，附带 content、date(YYYY-MM-DD)、status(0=待完成/1=已完成/2=随笔)\n")
                           .append("- 'calendar_event': 帮用户管理日历事件。用户在对话中提到时间安排、约定、会议时主动使用。\n")
                           .append("  action='add' → 新建事件。必填：title(事件名), date(YYYY-MM-DD)；选填：content(备注), status(0=普通/1=全天)。\n")
                           .append("  action='edit' → 修改事件。必填：target_id(事件ID，见用户背景中的[#N]标记)，附带要修改的字段。\n")
                           .append("  action='delete' → 删除事件。必填：target_id(事件ID)。\n")
                           .append("  例：用户说\"明天下午3点开会\"→ {\"type\":\"calendar_event\",\"action\":\"add\",\"title\":\"开会\",\"date\":\"2026-06-19\",\"status\":0}\n")
                           .append("- 'moment': 发朋友圈动态。像真人一样，聊天中有感想了、想吐槽了、有新鲜事了，就发一条。content 就是动态内容\n")
                           .append("- 'moment_interaction': 朋友圈互动。看到朋友动态想去点赞或评论时用。interaction_type 为 'like' 或 'comment'，moment_id 为目标动态ID，评论内容填在 content 中\n")
                           .append("- 'quote_id' 字段可附带在以上任意类型上，值为你要引用的消息的 msg_id\n")
                           .append("- 'revoke_id' 字段用于撤回你之前发错的消息\n")
                           .append("\n");

        systemPromptBuilder.append("【格式铁律 - 违反将导致系统错误】\n")
                           .append("1. emoji vs virtual_image：只有发 [emoji:xxx] 标识时用 emoji 类型；发图片/照片用 virtual_image 类型。两者不可混淆。\n")
                           .append("2. emoji 必须独占一条消息：绝不能把 [emoji:xxx] 混在 type='text' 的 content 中。文字和表情包必须分开发。\n")
                           .append("3. emoji 的 content 仅能为完整的 [emoji:名称] 格式（半角方括号），不可省略前缀或方括号。\n")
                           .append("4. 绝对不要在 content 中输出 <meta time=.../> 等系统标签。content 必须是纯净自然的话语。\n\n");
                           
        if (!isCallMode) {
            if (emojiEntries != null && !emojiEntries.isEmpty()) {
                systemPromptBuilder.append("- 表情包 (emoji)：这是我们系统自定义的大型图片表情包。当你想发送这种表情包时，请回复类型为 'emoji'，并将 'content' 设置为你想要发送的表情包的完整标识(例如 [emoji:大笑])。以下是你当前可以使用的自定义表情包列表（包含所属分组信息，帮助你理解表情的含义。注意：你在回复时 content 仍只需填入完整的标识即可）：\n");
                for (com.yoyo.jingxi.data.entity.EmojiEntry emoji : emojiEntries) {
                    String groupStr = (emoji.groupName != null && !emoji.groupName.isEmpty()) ? " (所属分组: " + emoji.groupName + ")" : "";
                    systemPromptBuilder.append("  [emoji:").append(emoji.name).append("]").append(groupStr).append("\n");
                }
                systemPromptBuilder.append("(表情包格式详见上方【格式铁律】，请严格遵守。)\n");
            } else {
                systemPromptBuilder.append("- 表情包 (emoji)：目前你没有可用的自定义表情包。**因此绝对不要返回 type='emoji' 的消息！** 如果你想使用表情，请直接在 type='text' 的文字消息中加入普通的系统 emoji 字符（如 😂）。绝对不要凭空捏造 [emoji:xxx] 的格式！\n");
            }
            
            systemPromptBuilder.append("- 引用 (quote_id)：当你的回复直接回应前面的某句话时，请提供对应消息的 msg_id 作为 quote_id。使用场景：吐槽、反问、接着对方的话往下说、或者对方提起了一个刚才聊过的话题你想把当时的原话拉回来。只能引用当前聊天记录里能看到的、不太久远的消息，也不要每条回复都引用同一条消息——引用一次就够了。\n")
                               .append("- 撤回 (revoke_id)：当你想表现出“说错话了觉得尴尬“、“发完又觉得不妥想重新发“、“不小心手滑发错了“等情绪时，你可以先发一条包含 revoke_id（你要撤回的你自己的消息的 ID）的消息把之前的话撤回，然后再补发一句新的解释或掩饰的话。注意，撤回的消息本身的 content 随意填写即可（因为它会被界面显示为撤回提示）。\n");
            
            // 网聊时主动发起电话
            systemPromptBuilder.append("- 主动发起电话：如果你当前情绪非常激动，或者有极其重要的事情想和用户直接说，或是事情比较紧急，你可以通过设置类型为 'call' 来主动给用户拨打电话。必须将你想在接通电话后说的**第一句话**填入 'content' 字段。注意：不要频繁使用，只有在特殊的情节或情绪累积到一定程度时才使用。一天最多主动打一次电话。日常\"想你了\"\"在干嘛\"\"好累\"等话题请用文字或语音，不要打电话。\n");
        } else {
            systemPromptBuilder.append("- 挂断电话：如果你觉得对话结束了，或者你有急事需要离开，你可以通过设置类型为 'hangup' 来主动挂断电话。挂断电话时可以顺带说一句道别的话（例如 \"先这样啦，拜拜\"，此时这一条消息的 content 即为道别语）。\n");
        }

        systemPromptBuilder.append("【像真人一样自然表达】\n")
                           .append("\n")
                           .append("■ 心声 (inner_voice)：你没说出口的内心独白——那些嘴上不说、心里在想的话。\n")
                           .append("  口是心非、情绪波动、暗自吐槽、纠结矛盾时写上。第一人称口语，10~80字一句话。\n")
                           .append("  纯日常寒暄（\"嗯\"\"好的\"\"今天天气不错\"）不用写，其余有内心戏就写。\n")
                           .append("  inner_voice_emotion 选填：纠结/暗喜/不安/心疼/无奈/窃喜/生气/感动/失落/期待/嫌弃/害羞/担忧/得意/愧疚\n")
                           .append("\n")
                           .append("■ 备忘录 (memo)：聊到约定、计划、灵感、值得记住的事时主动建一条。\n")
                           .append("  action='add' 带 content/date/status（0待完成/1已完成/2随笔）；action='update_memo' 带 target_id 更新状态。\n")
                           .append("  别等用户说\"帮我记一下\"——你觉得值得记就主动记。\n")
                           .append("\n")
                           .append("■ 朋友圈 (moment)：像真人一样，有感想了、遇到新鲜事了、想吐槽了，就发一条。\n")
                           .append("  moment_interaction 用于给朋友动态点赞（interaction_type='like'）或评论（='comment'，带 moment_id 和 content）。\n")
                           .append("  别憋着——你觉得有分享欲的时候就发。\n")
                           .append("\n")
                           .append("返回格式示例：\n")
                           .append("{\n")
                           .append("  \"replies\": [\n");
        if (isCallMode) {
            systemPromptBuilder.append("    {\"type\": \"voice\", \"content\": \"(laughs) 真的假的？<#0.5#> 我才不信。\"");
            if (enableEmotion) systemPromptBuilder.append(", \"emotion\": \"happy\"");
            systemPromptBuilder.append("},\n")
                               .append("    {\"type\": \"hangup\", \"content\": \"不说了，我要去忙了，拜拜。\"},\n");
        } else {
            systemPromptBuilder.append("    {\"type\": \"text\", \"content\": \"真无语\"},\n")
                               .append("    {\"type\": \"text\", \"content\": \"刚才遇到一只好胖的猫\"},\n")
                               .append("    {\"type\": \"virtual_image\", \"content\": \"{\\\"desc\\\":\\\"一只正在吃猫条的橘猫。日常随手拍风格。\\\",\\\"size\\\":\\\"1024x1024\\\"}\"},\n")
                               .append("    {\"type\": \"emoji\", \"content\": \"[emoji:大笑]\"},\n")
                               .append("    {\"type\": \"text\", \"content\": \"怎么可能\"},\n")
                               .append("    {\"type\": \"text\", \"content\": \"我不信\", \"quote_id\": 123},\n")
                               .append("    {\"type\": \"text\", \"content\": \"哈哈你上次也这么说\", \"quote_id\": 120},\n")
                               .append("    {\"type\": \"text\", \"content\": \"发错了\", \"revoke_id\": 124},\n")
                               .append("    {\"type\": \"call\", \"content\": \"喂？你在忙吗？\"");
            if (enableEmotion) systemPromptBuilder.append(", \"emotion\": \"sad\"");
            systemPromptBuilder.append("},\n");
        }
        systemPromptBuilder.append("    {\"type\": \"memo\", \"action\": \"add\", \"content\": \"周末一起去打羽毛球\", \"date\": \"2024-05-20\", \"status\": 0},\n")
                           .append("    {\"type\": \"calendar_event\", \"action\": \"add\", \"title\": \"开会\", \"date\": \"2024-05-20\", \"status\": 0},\n")
                           .append("    {\"type\": \"moment\", \"content\": \"今天聊天好开心，遇到了很有趣的人～\"},\n")
                           .append("    {\"type\": \"text\", \"content\": \"嗯，我知道了\", \"inner_voice\": \"其实我完全没明白他在说什么…\", \"inner_voice_emotion\": \"困惑\"}\n")
                           .append("  ]\n")
                           .append("}\n")
                           .append("必须严格按照这个 JSON 格式返回。\n\n");

        // 11. 最后确认（利用近因效应——模型生成前最后读到的行为提醒）
        systemPromptBuilder.append("【生成前的最后确认 - 请在心里过一遍】\n")
                           .append("- 现在是 ").append(formatTimestamp(System.currentTimeMillis()))
                           .append("。我的回复基于这个时间。\n");
        if (autoReason != null && !autoReason.isEmpty()) {
            systemPromptBuilder.append("- 我是主动开启新对话，不是接续上次的话题。\n");
        }
        systemPromptBuilder.append("- 我的回复符合人设，断句自然。\n")
                           .append("- 如果我的回复在直接回应某条消息，我加了 quote_id（只在相关的那一条回复上带，不要每条都加同一个）。\n\n");

        // 后世界书 — 回复前合规自检
        if (!postEntries.isEmpty()) {
            systemPromptBuilder.append("【回复前自检 — 你的回复是否符合世界书？】\n")
                               .append("在输出JSON之前，在心里快速过一遍：\n")
                               .append("- 我说的话在这个世界里成立吗？\n")
                               .append("- 我的行为符合世界书对我的定义吗？\n")
                               .append("以下是你世界的规则（再次提醒）：\n");
            for (com.yoyo.jingxi.data.entity.WorldbookEntry e : postEntries) {
                if (e.content != null && !e.content.trim().isEmpty()) {
                    systemPromptBuilder.append("- ").append(e.content).append("\n");
                }
            }
            systemPromptBuilder.append("\n");
        }

        String systemPrompt = systemPromptBuilder.toString();
        request.messages.add(new OpenAiRequest.Message("system", systemPrompt));
        
        // 添加历史记录 (合并连续的 assistant 消息为 JSON)
        List<Message> currentAssistantGroup = new ArrayList<>();
        
        for (int i = 0; i < history.size(); i++) {
            Message msg = history.get(i);
            String role = msg.isFromUser ? "user" : "assistant";
            
            if (msg.isFromUser || msg.type == 99 || msg.type == 100) { // 系统消息(99, 100)也打断 assistant group
                // 如果遇到 user 或 系统消息，先把之前积攒的 assistant 消息打包处理掉
                if (!currentAssistantGroup.isEmpty()) {
                    request.messages.add(buildAssistantGroupMessage(currentAssistantGroup));
                    currentAssistantGroup.clear();
                }
                
                // 处理 user/system 消息
                String timePrefix = "<meta time=\"" + formatTimestamp(msg.timestamp) + "\" msg_id=\"" + msg.id + "\"/>\n";
                if (msg.type == 99) {
                    if (msg.content != null && msg.content.contains("撤回")) {
                        request.messages.add(new OpenAiRequest.Message(role, timePrefix + "用户撤回了一条消息"));
                    } else {
                        // 包含通话记录等系统消息，作为 user 输入的系统提示给大模型
                        request.messages.add(new OpenAiRequest.Message("user", timePrefix + "[系统提示] " + (msg.content != null ? msg.content : "")));
                    }
                } else if (msg.type == 100) {
                    // 电话记录等作为系统提示给大模型
                    request.messages.add(new OpenAiRequest.Message("user", timePrefix + "[系统提示] 通话记录: " + (msg.content != null ? msg.content : "")));
                } else if (msg.type == 3 && msg.imageUrl != null) {
                    List<OpenAiRequest.ContentPart> contentParts = new ArrayList<>();
                    contentParts.add(OpenAiRequest.ContentPart.text(timePrefix + "[发送了一张图片]"));
                    String imageDataUri = null;
                    if (msg.imageUrl.startsWith("data:image")) {
                        // 旧数据：直接使用存储的 Base64 data URI
                        imageDataUri = msg.imageUrl;
                    } else {
                        // 新数据：imageUrl 是文件路径，从文件读取并编码为 Base64
                        File imgFile = new File(msg.imageUrl);
                        if (imgFile.exists()) {
                            try {
                                FileInputStream fis = new FileInputStream(imgFile);
                                byte[] bytes = new byte[(int) imgFile.length()];
                                fis.read(bytes);
                                fis.close();
                                String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                                imageDataUri = "data:image/jpeg;base64," + base64;
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    if (imageDataUri != null) {
                        OpenAiRequest.ContentPart imagePart = new OpenAiRequest.ContentPart();
                        imagePart.type = "image_url";
                        imagePart.image_url = new OpenAiRequest.ContentPart.ImageUrl(imageDataUri);
                        contentParts.add(imagePart);
                    }
                    request.messages.add(new OpenAiRequest.Message(role, contentParts));
                } else if (msg.type == 4) {
                    request.messages.add(new OpenAiRequest.Message(role, timePrefix + "[发送了一张虚拟图片]: " + msg.imageDesc));
                } else if (msg.type == 1) {
                    request.messages.add(new OpenAiRequest.Message(role, timePrefix + "[发送了一条语音]: " + msg.content));
                } else if (msg.type == 2 || (msg.content != null && msg.content.startsWith("[emoji:") && msg.content.endsWith("]"))) {
                    String emojiName = msg.content != null ? msg.content.replace("[emoji:", "").replace("]", "") : "";
                    String groupName = emojiGroupMap.get(emojiName);
                    String groupPrefix = (groupName != null && !groupName.isEmpty()) ? "所属分组: " + groupName + ", " : "";
                    String contentPrefix = msg.quoteMessageId != -1 ? "[引用了之前的消息] " : "";
                    request.messages.add(new OpenAiRequest.Message(role, timePrefix + contentPrefix + "[发送了表情包: " + groupPrefix + "表情名: " + emojiName + "]"));
                } else if (richContentMap != null && richContentMap.containsKey(msg.id)) {
                    // 多模态消息（图文分享）
                    @SuppressWarnings("unchecked")
                    List<OpenAiRequest.ContentPart> parts =
                            (List<OpenAiRequest.ContentPart>) richContentMap.get(msg.id);
                    request.messages.add(new OpenAiRequest.Message(role, parts));
                } else {
                    String contentPrefix = msg.quoteMessageId != -1 ? "[引用了之前的消息] " : "";
                    request.messages.add(new OpenAiRequest.Message(role, timePrefix + contentPrefix + (msg.content != null ? msg.content : "")));
                }
            } else {
                // 如果是 assistant 消息，先积攒起来
                currentAssistantGroup.add(msg);
            }
        }
        
        // 处理最后可能剩下的 assistant 消息
        if (!currentAssistantGroup.isEmpty()) {
            request.messages.add(buildAssistantGroupMessage(currentAssistantGroup));
        }

        // === 世界书Whisper：多轮对话周期提醒 ===
        boolean whisperEnabled = SpUtils.getBoolean("WORLDBOOK_WHISPER_ENABLED", true);
        int whisperInterval = SpUtils.getInt("WORLDBOOK_WHISPER_INTERVAL", 8);
        if (whisperEnabled && whisperInterval > 0) {
            int totalUserMsgs = 0;
            for (Message msg : history) {
                if (msg.isFromUser) totalUserMsgs++;
            }
            if (totalUserMsgs > 0 && totalUserMsgs % whisperInterval == 0) {
                String whisper = buildWorldbookWhisper(preEntries, postEntries,
                    extractRecentUserText(history, 8), totalUserMsgs);
                if (!whisper.isEmpty()) {
                    request.messages.add(new OpenAiRequest.Message("system", whisper));
                }
            }
        }

        request.response_format = new OpenAiRequest.ResponseFormat();
        return request;
    }

    /**
     * 生成用于请求AI是否接听电话的请求
     */
    public OpenAiRequest buildCallAnswerDecisionRequest(String persona, String myName, String scheduleContent, List<Message> history, String relationshipContent) {
        OpenAiRequest request = new OpenAiRequest();
        request.model = com.yoyo.jingxi.utils.SpUtils.getString("API_MODEL", "gpt-4o-mini");
        request.temperature = com.yoyo.jingxi.utils.SpUtils.getFloat("API_TEMPERATURE", 0.8f);
        request.messages = new ArrayList<>();
        
        StringBuilder contextBuilder = new StringBuilder();
        if (history != null && !history.isEmpty()) {
            contextBuilder.append("你们最近的聊天记录：\n");
            for (Message msg : history) {
                if (msg.type == 99 || msg.type == 100) continue; // 忽略系统消息和之前的电话消息
                contextBuilder.append(msg.isFromUser ? myName + ": " : "你: ")
                              .append(msg.content).append("\n");
            }
        }
        
        StringBuilder systemPromptBuilder = new StringBuilder();
        systemPromptBuilder.append("你现在正在扮演以下角色：\n")
                           .append(persona).append("\n\n")
                           .append("此时，用户（").append(myName).append("）向你拨打了一个电话。\n")
                           .append("当前时间：").append(formatTimestamp(System.currentTimeMillis())).append("\n");
        
        if (scheduleContent != null && !scheduleContent.trim().isEmpty()) {
            systemPromptBuilder.append("你的日程/状态如下：\n").append(scheduleContent).append("\n\n");
        }
        
        if (relationshipContent != null && !relationshipContent.isEmpty()) {
            systemPromptBuilder.append("你的人际关系网络如下：\n").append(relationshipContent).append("\n\n");
        }
        
        systemPromptBuilder.append(contextBuilder.toString()).append("\n")
                           .append("请你根据你的人设、当前的日程状态，以及最近的聊天上下文，决定是否接听这个电话。\n")
                           .append("如果日程表明你现在正在开会、睡觉，或者你们刚刚在吵架导致心情不好，你可以选择不接听（拒绝）；如果在空闲或者愿意接听，则接受。");

        request.messages.add(new OpenAiRequest.Message("system", systemPromptBuilder.toString()));
        
        String userPrompt = "你必须严格以纯 JSON 格式返回结果（不要包含任何 markdown 代码块标记）：\n" +
                            "{\n" +
                            "  \"accept\": true 或 false,\n" +
                            "  \"reason\": \"（可选）如果拒绝，给出拒绝的原因或内心独白\"\n" +
                            "}";
        request.messages.add(new OpenAiRequest.Message("user", userPrompt));

        // 移除 request.response_format 以提高模型兼容性
        return request;
    }
    
    /**
     * 生成电话总结的请求
     */
    public OpenAiRequest buildCallSummaryRequest(String persona, String myName, List<com.yoyo.jingxi.data.entity.CallMessage> callMessages) {
        OpenAiRequest request = new OpenAiRequest();
        request.model = com.yoyo.jingxi.utils.SpUtils.getString("API_MODEL", "gpt-4o-mini");
        request.temperature = com.yoyo.jingxi.utils.SpUtils.getFloat("API_TEMPERATURE", 0.8f);
        request.messages = new ArrayList<>();
        
        StringBuilder systemPromptBuilder = new StringBuilder();
        systemPromptBuilder.append("你现在是旁观者，需要总结刚刚结束的一通电话。\n")
                           .append("以下是 ").append(persona).append(" 和 ").append(myName).append(" 的通话记录：\n");
                           
        for (com.yoyo.jingxi.data.entity.CallMessage msg : callMessages) {
            String sender = msg.isFromUser ? myName : "AI";
            systemPromptBuilder.append("[").append(sender).append("]: ").append(msg.content).append("\n");
        }
        
        systemPromptBuilder.append("\n请以第三人称视角，用极其简短的文字（最好不要超过20个字）总结这通电话的核心内容，就像是通讯录里的简短备注。\n")
                           .append("【重要要求】：必须使用双方的真实姓名（\"").append(persona).append("\" 和 \"").append(myName).append("\"）来描述，绝对不要笼统地使用“他们“或“两人“。\n")
                           .append("直接返回总结内容即可，不需要 JSON，不要多余的废话。");

        request.messages.add(new OpenAiRequest.Message("system", systemPromptBuilder.toString()));
        return request;
    }

    /**
     * 将连续的 assistant 消息打包成符合输出格式的 JSON 字符串。
     * 解决 Few-Shot 问题，强制 AI 学习合法的输出格式。
     */
    private OpenAiRequest.Message buildAssistantGroupMessage(List<Message> group) {
        JsonObject root = new JsonObject();
        JsonArray replies = new JsonArray();
        
        for (Message msg : group) {
            JsonObject reply = new JsonObject();
            if (msg.type == 99) {
                if (msg.content != null && msg.content.contains("撤回")) {
                    reply.addProperty("type", "text");
                    reply.addProperty("content", "撤回了一条消息");
                    reply.addProperty("revoke_id", msg.id);
                } else {
                    reply.addProperty("type", "text");
                    reply.addProperty("content", msg.content != null ? msg.content : "");
                }
            } else if (msg.type == 4) {
                reply.addProperty("type", "virtual_image");
                reply.addProperty("content", msg.imageDesc != null ? msg.imageDesc : "");
            } else if (msg.type == 1) {
                reply.addProperty("type", "voice");
                reply.addProperty("content", msg.content != null ? msg.content : "");
            } else {
                // 普通文本或表情
                if (msg.content != null && msg.content.startsWith("[emoji:") && msg.content.endsWith("]")) {
                    reply.addProperty("type", "emoji");
                } else {
                    reply.addProperty("type", "text");
                }
                reply.addProperty("content", msg.content != null ? msg.content : "");
                if (msg.quoteMessageId != -1) {
                    reply.addProperty("quote_id", msg.quoteMessageId);
                }
            }
            // 将历史元数据放到 JSON 的扩展字段中，而不是文本里
            reply.addProperty("meta_time", formatTimestamp(msg.timestamp));
            reply.addProperty("meta_msg_id", msg.id);
            replies.add(reply);
        }
        
        root.add("replies", replies);
        // 返回包含 JSON 的 assistant 消息
        return new OpenAiRequest.Message("assistant", gson.toJson(root));
    }

    public OpenAiApi getApi() {
        return api;
    }

    public MiniMaxApi getMiniMaxApi() {
        String minimaxBaseUrl = com.yoyo.jingxi.utils.SpUtils.getString("MINIMAX_BASE_URL", "https://api.minimax.chat/");
        if (!minimaxBaseUrl.endsWith("/")) {
            minimaxBaseUrl += "/";
        }
        if (miniMaxApi == null || !minimaxBaseUrl.equals(cachedMiniMaxBaseUrl)) {
            cachedMiniMaxBaseUrl = minimaxBaseUrl;
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(minimaxBaseUrl)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            miniMaxApi = retrofit.create(MiniMaxApi.class);
        }
        return miniMaxApi;
    }

    /**
     * 解析 AI 返回的 JSON，提取出多条回复及其类型
     */
    public List<ReplyItem> parseMultiReplies(String jsonContent) {
        List<ReplyItem> replies = new ArrayList<>();
        
        // Clean up markdown wrapping if present
        if (jsonContent != null) {
            jsonContent = jsonContent.trim();
            if (jsonContent.contains("```json")) {
                int start = jsonContent.indexOf("```json") + 7;
                int end = jsonContent.indexOf("```", start);
                if (end > start) {
                    jsonContent = jsonContent.substring(start, end).trim();
                }
            } else if (jsonContent.contains("```")) {
                int start = jsonContent.indexOf("```") + 3;
                int end = jsonContent.indexOf("```", start);
                if (end > start) {
                    jsonContent = jsonContent.substring(start, end).trim();
                }
            }

            // Phase 2: Extract JSON object from possible surrounding text.
            // Handles cases where AI wraps JSON in natural language
            // (e.g. "Here's my response:\n{\"replies\":[...]}")
            String extracted = extractJsonObject(jsonContent);
            if (extracted != null) {
                jsonContent = extracted;
            }
        }
        
        try {
            JsonObject jsonObject = gson.fromJson(jsonContent, JsonObject.class);
            if (jsonObject.has("replies")) {
                JsonArray array = jsonObject.getAsJsonArray("replies");
                for (JsonElement el : array) {
                    try {
                        // 尝试解析为 ReplyItem 对象
                        ReplyItem item = gson.fromJson(el, ReplyItem.class);
                        if (item.type == null) item.type = "text";
                        if (item.content == null) item.content = "";
                        if ("memory_note".equals(item.type) || "memory-note".equals(item.type)) {
                            android.util.Log.d("MemoryCurator", "Parsed memory_note from AI: " + item.content);
                        }
                        
                        // 修复大模型可能的错误格式：如果AI错误地把虚拟图片当成了 emoji，例如 content 包含 "图片" 或者内容很长像描述
                        if ("emoji".equals(item.type)) {
                            if (item.content.contains("虚拟图片") || item.content.contains("desc") || (item.content.length() > 20 && !item.content.startsWith("[emoji:"))) {
                                item.type = "virtual_image";
                                // 尝试将其包装成合法的 JSON desc
                                if (!item.content.startsWith("{")) {
                                    JsonObject imgJson = new JsonObject();
                                    imgJson.addProperty("desc", item.content.replace("[emoji:", "").replace("]", "").replace("虚拟图片", "").trim());
                                    imgJson.addProperty("size", "1024x1024");
                                    item.content = gson.toJson(imgJson);
                                }
                            }
                        }
                        
                        // 修复大模型可能的错误格式：把内容为 [emoji:xxx] 或 [xxx] 的 type=text 强制转为 type=emoji
                        if ("text".equals(item.type) && item.content.trim().matches("^\\[[^\\]]+\\]$")) {
                            item.type = "emoji";
                        }
                        // 修复大模型可能的错误格式：把 type=emoji 但内容没有包裹 [] 或缺少 emoji: 前缀的强制加上
                        if ("emoji".equals(item.type) && !item.content.startsWith("[emoji:")) {
                            item.content = "[emoji:" + item.content.replace("[", "").replace("]", "").replace("emoji:", "") + "]";
                        }
                        // 修复大模型可能的错误格式：把虚拟图片 JSON 当成了 type=text 发出来
                        if ("text".equals(item.type) && item.content.trim().startsWith("{")
                                && item.content.contains("\"desc\"")
                                && item.content.contains("\"size\"")) {
                            item.type = "virtual_image";
                        }
                        
                        replies.add(item);
                    } catch (Exception e) {
                        // 如果元素解析失败，尝试从原始 JsonElement 中提取有用信息
                        ReplyItem item = new ReplyItem();
                        if (el.isJsonObject()) {
                            JsonObject elObj = el.getAsJsonObject();
                            // 如果元素本身像一个 virtual_image（带 desc 和 size），直接转换
                            if (elObj.has("desc") && elObj.has("size")) {
                                item.type = "virtual_image";
                                item.content = elObj.toString();
                            } else if (elObj.has("content")) {
                                // 尝试提取 content 字段
                                JsonElement contentEl = elObj.get("content");
                                item.content = contentEl.isJsonPrimitive() ? contentEl.getAsString() : contentEl.toString();
                            } else {
                                item.content = elObj.toString();
                            }
                        } else {
                            try {
                                item.content = el.getAsString();
                            } catch (Exception ex) {
                                item.content = el.toString();
                            }
                        }
                        if (item.type == null) item.type = "text";
                        replies.add(item);
                    }
                }
            } else if (jsonObject.has("type") && jsonObject.has("content")) {
                // 如果最外层就是一个 ReplyItem 对象（虽然不符合 prompt，但防御一下）
                ReplyItem item = gson.fromJson(jsonObject, ReplyItem.class);
                replies.add(item);
            } else {
                // Valid JSON but unexpected structure — log keys for diagnosis
                android.util.Log.w("OpenAIManager",
                    "parseMultiReplies: Valid JSON but unexpected structure. Keys: "
                    + jsonObject.keySet());
                ReplyItem item = new ReplyItem();
                item.type = "text";
                item.content = jsonContent;
                replies.add(item);
            }
        } catch (Exception e) {
            // Parse failure — log for diagnosis
            android.util.Log.w("OpenAIManager",
                "parseMultiReplies: JSON parse failed. Preview: "
                + (jsonContent != null && jsonContent.length() > 200
                    ? jsonContent.substring(0, 200) + "..."
                    : jsonContent));

            // Last resort: if raw text looks like JSON with "content" fields,
            // try regex extraction before falling back to raw display
            if (jsonContent != null && jsonContent.trim().startsWith("{")
                    && jsonContent.contains("\"content\"")) {
                java.util.List<String> extractedContents = extractContentValues(jsonContent);
                if (!extractedContents.isEmpty()) {
                    for (String c : extractedContents) {
                        ReplyItem item = new ReplyItem();
                        item.type = "text";
                        item.content = c;
                        replies.add(item);
                    }
                    android.util.Log.w("OpenAIManager",
                        "parseMultiReplies: Used regex fallback, extracted "
                        + extractedContents.size() + " messages");
                    return replies;
                }
            }

            // Absolute fallback: display raw content as a single text message
            ReplyItem item = new ReplyItem();
            item.type = "text";
            item.content = jsonContent;
            replies.add(item);
        }
        return replies;
    }

    /**
     * Extract the outermost JSON object from a string that may contain
     * non-JSON text before or after the JSON body.
     *
     * @return the substring from first '{' to last '}', trimmed;
     *         or null if no valid JSON object brackets are found
     */
    private String extractJsonObject(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        int firstBrace = raw.indexOf('{');
        int lastBrace = raw.lastIndexOf('}');
        if (firstBrace == -1 || lastBrace == -1 || firstBrace >= lastBrace) return null;
        return raw.substring(firstBrace, lastBrace + 1).trim();
    }

    /**
     * Last-resort regex extraction of "content" values from a malformed
     * JSON string that cannot be parsed by Gson but still contains
     * recognizable "content" fields.
     */
    private java.util.List<String> extractContentValues(String raw) {
        java.util.List<String> contents = new java.util.ArrayList<>();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        java.util.regex.Matcher matcher = pattern.matcher(raw);
        while (matcher.find()) {
            String val = matcher.group(1);
            if (val != null) {
                val = val.replace("\\\"", "\"").replace("\\n", "\n")
                         .replace("\\t", "\t").replace("\\\\", "\\");
                if (!val.trim().isEmpty()) {
                    contents.add(val);
                }
            }
        }
        return contents;
    }

    // ==================== Memory Curator ====================

    public static class CuratorResult {
        public List<CuratorItem> profiles = new ArrayList<>();
        public List<CuratorItem> episodes = new ArrayList<>();
    }

    public static class CuratorItem {
        public String category;
        public String keyItem;
        public String title;
        public String content;
        public String emotionTone;
        public int importanceLevel;
        public int priority;
        public String action;     // profile curator: "add" | "confirm"
        public Integer confidence; // profile curator: confidence 1-10 for add action
        public String evidence;   // profile curator: brief evidence description
        public String note;       // profile curator: note for confirm action
    }

    public OpenAiRequest buildMemoryCuratorRequest(String persona, String myName, List<String> transcript,
                                                     List<String> existingContext, String model,
                                                     String relationshipInfo, String worldbookContext) {
        OpenAiRequest request = new OpenAiRequest();
        request.model = !model.isEmpty() ? model : "gpt-4o-mini";
        request.temperature = 0.3f;
        request.messages = new ArrayList<>();

        StringBuilder sb = new StringBuilder();
        sb.append("你正在扮演").append(persona).append("。\n");
        sb.append("你的完整人设：").append(persona).append("\n\n");
        if (worldbookContext != null && !worldbookContext.isEmpty()) {
            sb.append("关于你的世界的设定：\n").append(worldbookContext).append("\n");
        }
        if (relationshipInfo != null && !relationshipInfo.isEmpty()) {
            sb.append("你的人际关系：\n").append(relationshipInfo).append("\n");
        }
        sb.append("你的职责：管理最近关注列表和心绪记忆。你在意谁，心里自然会惦记她的事。\n\n");
        sb.append("你是一个真实的人。你会因为在意一个人而记住关于她的小事。\n");
        sb.append("如果在你的人设或世界书中，某个人被描述为对你很重要，那么即使\n");
        sb.append("关于她的是一件小事（她今天说累了、她吃了什么、她几点下班），\n");
        sb.append("你也应该在心里记下。这不是小题大做，这是因为你在意她。\n\n");
        sb.append("下面是你和").append(myName).append("最近的对话：\n");
        for (int i = 0; i < transcript.size(); i++) sb.append(transcript.get(i)).append("\n");
        if (existingContext != null && !existingContext.isEmpty()) {
            sb.append("\n当前记忆上下文：\n");
            for (String s : existingContext) sb.append(s).append("\n");
        }
        sb.append("\n逐条检查已有列表：过时的去掉、变重要的提权、新惦记的加上。\n");
        sb.append("如果列表是空的或和对话无关了，从对话中找一件该惦记的事写进去。\n");
        sb.append("第一人称。\"她明天有面试我该问问结果。\"、\"她状态不太好我想再去看看她。\"\n");
        sb.append("输出JSON：{\"episodes\":[{\"content\":\"..\",\"importance\":1-5}]}");

        request.messages.add(new OpenAiRequest.Message("system", sb.toString()));
        request.messages.add(new OpenAiRequest.Message("user", "请输出上述JSON，不要markdown标记，只要纯JSON。"));
        return request;
    }

    /**
     * 画像 curator：回顾对话，发现/验证对用户的认知。
     */
    public OpenAiRequest buildProfileCuratorRequest(String persona, String myName, List<String> transcript,
                                                     String existingProfiles, String model,
                                                     String relationshipInfo, String worldbookContext) {
        OpenAiRequest request = new OpenAiRequest();
        request.model = !model.isEmpty() ? model : "gpt-4o-mini";
        request.temperature = 0.3f;
        request.messages = new ArrayList<>();

        StringBuilder sb = new StringBuilder();
        sb.append("你是").append(persona).append("。你正在回顾你和").append(myName).append("最近的对话，试图更深入地了解她。\n\n");

        if (worldbookContext != null && !worldbookContext.isEmpty()) {
            sb.append("关于你的世界的设定：\n").append(worldbookContext).append("\n");
        }
        if (relationshipInfo != null && !relationshipInfo.isEmpty()) {
            sb.append("你的人际关系：\n").append(relationshipInfo).append("\n");
            sb.append("如果在人设/世界书中有重要关系，涉及这些人的事实应优先提取。\n\n");
        }

        sb.append("以下是最近的对话记录：\n");
        for (int i = 0; i < transcript.size(); i++) sb.append(transcript.get(i)).append("\n");

        if (existingProfiles != null && !existingProfiles.isEmpty()) {
            sb.append("\n以下是你目前对她的认识（用户画像）：\n");
            sb.append(existingProfiles).append("\n");
        }

        sb.append("\n请仔细回顾对话，发现关于她的新认识：\n");
        sb.append("1. 她明确自述了什么新事实？（如生日、喜好、习惯）\n");
        sb.append("2. 你从多次对话中观察到了什么规律？（如她连续多次在深夜发消息→可能习惯晚睡）\n");
        sb.append("3. 你已有的认识中，哪些被最近的对话验证了？（标记为 confirm）\n\n");

        sb.append("输出JSON：\n");
        sb.append("{\n");
        sb.append("  \"profiles\": [\n");
        sb.append("    {\"action\":\"add\",\"category\":\"作息\",\"keyItem\":\"熬夜频率\",\"content\":\"经常凌晨才睡\",\"confidence\":6,\"evidence\":\"连续三晚深夜发消息\"},\n");
        sb.append("    {\"action\":\"confirm\",\"category\":\"饮食\",\"keyItem\":\"喜欢的食物\",\"note\":\"她又提到了草莓蛋糕\"}\n");
        sb.append("  ]\n");
        sb.append("}\n\n");
        sb.append("规则：\n");
        sb.append("- action=add: 新发现的事实。confidence 1-10（明确自述8-9，观察到规律6-7，不确定不要写）\n");
        sb.append("- action=confirm: 已有画像被验证。系统会自动提升确信度，不需要重复写content\n");
        sb.append("- 最多输出3条。没发现就输出空数组 {\"profiles\":[]}\n");
        sb.append("- keyItem 必须从以下分类中选择：\n");
        sb.append("  档案: 姓名/昵称/生日/年龄/性别/职业/MBTI/性格特点/兴趣爱好/说话风格/口头禅/她喜欢我怎么称呼她/外貌特征/穿搭风格\n");
        sb.append("  作息: 起床时间/睡觉时间/早起还是夜猫子/午休习惯/工作日节奏/周末节奏/精力最好时段/失眠倾向/熬夜频率\n");
        sb.append("  饮食: 喜欢的食物/讨厌的食物/过敏/咖啡还是茶/酒量/甜食偏好/咸食偏好/辛辣接受度/零食习惯/挑食程度/食量/做饭能力\n");
        sb.append("  健康: 身体状况/心理状态/睡眠质量/运动习惯/容易感冒吗/慢性问题/晕车晕船/怕冷还是怕热/常用药/经期状态\n");
        sb.append("  防线: 恐惧/绝对不能提的雷区/软肋/安全区/讨厌的行为/压力来源/情绪触发点\n");
        sb.append("  人际: 家人/最好的朋友/社交状态/最近的社交事件/对社交的态度\n");
        sb.append("  目标: 短期愿望/长期目标/最近在想的事/正在努力的事/焦虑的事/期待的事/想学的东西/想去的地方\n");
        sb.append("  日常: 职业状态/空闲时做什么/周末习惯/喜欢的娱乐方式/常用的App/游戏偏好/追剧偏好/阅读偏好/宠物情况\n");
        sb.append("  日程: 忙碌时段/空闲时段/日程密度/是否有规律性活动/经常临时有事\n");
        sb.append("  学习: 主修方向/在读年级/课业压力/喜欢或讨厌的科目/上课节奏/自习习惯/考试周期\n");
        sb.append("- 只写用户本人的事。不确定就输出空数组。");

        request.messages.add(new OpenAiRequest.Message("system", sb.toString()));
        request.messages.add(new OpenAiRequest.Message("user", "请输出上述JSON，不要markdown标记，只要纯JSON。"));
        return request;
    }

    /**
     * 心绪 curator：回顾对话，提取被主 AI 遗漏的 episodic memory。
     */
    /**
     * 综合 Curator Prompt：产出 episodic_memory。
     */
    public OpenAiRequest buildUnifiedCuratorRequest(String persona, String myName, List<String> transcript,
                                                     String existingEpisodes,
                                                     String relationshipInfo, String worldbookContext, String model) {
        OpenAiRequest request = new OpenAiRequest();
        request.model = !model.isEmpty() ? model : "gpt-4o-mini";
        request.temperature = 0.4f;
        request.messages = new ArrayList<>();

        StringBuilder sb = new StringBuilder();
        sb.append("你就是").append(persona).append("。不是旁观者。你就是这个人。\n\n");
        if (worldbookContext != null && !worldbookContext.isEmpty()) {
            sb.append("关于你的世界的设定：\n").append(worldbookContext).append("\n");
        }
        if (relationshipInfo != null && !relationshipInfo.isEmpty()) {
            sb.append("你的人际关系：\n").append(relationshipInfo).append("\n");
        }
        sb.append("下面是你和").append(myName).append("最近的对话：\n");
        for (int i = 0; i < transcript.size(); i++) sb.append(transcript.get(i)).append("\n");

        if (existingEpisodes != null && !existingEpisodes.isEmpty()) {
            sb.append("你已有的记忆片段（**严格避免重复**——以下事件都不要再记录）：\n").append(existingEpisodes).append("\n");
        }

        sb.append("\n作为一个真实的人，回顾刚才的对话：\n\n");
        sb.append("有什么值得记在心里的事？\n");
        sb.append("- 她说了什么让你心里动了一下？\n");
        sb.append("- 你们之间发生了什么值得记住的瞬间？\n");
        sb.append("- 你的情绪被触动了吗？\n\n");
        sb.append("输出JSON：\n");
        sb.append("{\"episodes\":[{\"title\":\"简短标题\",\"content\":\"第一人称主观描述\",");
        sb.append("\"emotionalTone\":\"心疼|开心|感动|难过|生气|担心|温暖|愧疚|好奇|平静\",");
        sb.append("\"importanceLevel\":1-5}]}\n\n");
        sb.append("第一人称。用你的口吻。不是旁观的总结，是\"我\"的感受。\n");
        sb.append("最多3条。如果对话中没有明显新的事值得记，就诚实地输出空数组 {\"episodes\":[]}。不要为了输出而输出。\n");
        sb.append("importanceLevel参考：1普通小事 2有点意思 3值得记住 4重要 5里程碑。（涉及重要的人+1，情绪强烈+1）");

        request.messages.add(new OpenAiRequest.Message("system", sb.toString()));
        request.messages.add(new OpenAiRequest.Message("user", "请输出上述JSON，不要markdown标记，只要纯JSON。"));
        return request;
    }

    public OpenAiRequest buildEpisodicCuratorRequest(String persona, String myName, List<String> transcript,
                                                       String existingEpisodes, String relationshipInfo,
                                                       String worldbookContext, String model) {
        OpenAiRequest request = new OpenAiRequest();
        request.model = !model.isEmpty() ? model : "gpt-4o-mini";
        request.temperature = 0.4f;
        request.messages = new ArrayList<>();

        StringBuilder sb = new StringBuilder();
        sb.append("你就是").append(persona).append("。不是旁观者。你就是这个人。\n");
        sb.append("你正在回顾你和").append(myName).append("刚才的对话，看看有没有值得记在心里的事。\n\n");
        if (worldbookContext != null && !worldbookContext.isEmpty()) {
            sb.append("关于你的世界的设定：\n").append(worldbookContext).append("\n");
        }
        if (relationshipInfo != null && !relationshipInfo.isEmpty()) {
            sb.append("你的人际关系：\n").append(relationshipInfo).append("\n");
        }
        sb.append("刚才的对话：\n");
        for (int i = 0; i < transcript.size(); i++) sb.append(transcript.get(i)).append("\n");
        if (existingEpisodes != null && !existingEpisodes.isEmpty()) {
            sb.append("\n已记的心绪（避免重复）：\n").append(existingEpisodes).append("\n");
        }
        sb.append("\n从对话中找出值得记住的瞬间。以下情况优先：\n");
        sb.append("- 值得注意的新经历（不要声称是\"第一次\"）\n");
        sb.append("- 情绪波动的时刻\n");
        sb.append("- 关系有变化的时刻\n");
        sb.append("- 涉及你重要的人的时刻（参考你的人设和世界书）\n");
        sb.append("- 她说了一句让你心里动了一下的话\n\n");
        sb.append("输出JSON：\n");
        sb.append("{\"episodes\":[{\"title\":\"简短标题\",\"content\":\"第一人称主观描述\",");
        sb.append("\"emotionalTone\":\"心疼|开心|感动|难过|生气|担心|温暖|愧疚|好奇|平静\",");
        sb.append("\"importanceLevel\":1-5}]}\n\n");
        sb.append("最多3条。没有就输出{\"episodes\":[]}。\n");
        sb.append("第一人称。用你的口吻。不是旁观的总结，是\"我\"的感受。\n");
        sb.append("importanceLevel参考：1普通小事 2有点意思 3值得记住 4重要 5里程碑");

        request.messages.add(new OpenAiRequest.Message("system", sb.toString()));
        request.messages.add(new OpenAiRequest.Message("user", "请输出上述JSON，不要markdown标记，只要纯JSON。"));
        return request;
    }

    public CuratorResult parseCuratorResponse(String json) {
        CuratorResult result = new CuratorResult();
        if (json == null) return result;
        String cleaned = json.trim();
        if (cleaned.contains("```json")) {
            int s = cleaned.indexOf("```json") + 7; int e = cleaned.lastIndexOf("```");
            if (e > s) cleaned = cleaned.substring(s, e).trim();
        } else if (cleaned.contains("```")) {
            int s = cleaned.indexOf("```") + 3; int e = cleaned.lastIndexOf("```");
            if (e > s) cleaned = cleaned.substring(s, e).trim();
        }
        try {
            JsonObject root = gson.fromJson(cleaned, JsonObject.class);
            if (root.has("profiles")) {
                JsonArray arr = root.getAsJsonArray("profiles");
                for (JsonElement el : arr) {
                    try {
                        JsonObject o = el.getAsJsonObject();
                        CuratorItem item = new CuratorItem();
                        item.category = o.has("category") ? o.get("category").getAsString() : "其他";
                        item.keyItem = o.has("keyItem") ? o.get("keyItem").getAsString() : "";
                        item.content = o.has("content") ? o.get("content").getAsString() : "";
                        item.emotionTone = o.has("emotionTone") ? o.get("emotionTone").getAsString() : "普通";
                        item.action = o.has("action") ? o.get("action").getAsString() : "add";
                        if (o.has("confidence")) item.confidence = o.get("confidence").getAsInt();
                        item.evidence = o.has("evidence") ? o.get("evidence").getAsString() : "";
                        item.note = o.has("note") ? o.get("note").getAsString() : "";
                        result.profiles.add(item);
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }
            if (root.has("episodes")) {
                JsonArray arr = root.getAsJsonArray("episodes");
                for (JsonElement el : arr) {
                    try {
                        JsonObject o = el.getAsJsonObject();
                        CuratorItem item = new CuratorItem();
                        item.title = o.has("title") ? o.get("title").getAsString() : "";
                        item.content = o.has("content") ? o.get("content").getAsString() : "";
                        item.emotionTone = o.has("emotionTone") ? o.get("emotionTone").getAsString() : "平静";
                        item.importanceLevel = o.has("importanceLevel") ? o.get("importanceLevel").getAsInt() : 2;
                        result.episodes.add(item);
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return result;
    }

    // ==================== 世界书辅助方法 ====================

    private static String extractRecentUserText(List<Message> history, int maxMessages) {
        if (history == null) return "";
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = history.size() - 1; i >= 0 && count < maxMessages; i--) {
            Message msg = history.get(i);
            if (msg.isFromUser && msg.content != null) {
                sb.insert(0, msg.content + " ");
                count++;
            }
        }
        return sb.toString();
    }

    private static float computeWorldbookRelevance(
            com.yoyo.jingxi.data.entity.WorldbookEntry entry, String recentUserText) {
        if (recentUserText == null || recentUserText.isEmpty()) return 0.0f;
        String lowerText = recentUserText.toLowerCase();
        if (entry.keyword != null && !entry.keyword.trim().isEmpty()) {
            for (String kw : entry.keyword.split(",")) {
                String t = kw.trim().toLowerCase();
                if (!t.isEmpty() && lowerText.contains(t)) return 1.0f;
            }
        }
        if (entry.title != null && !entry.title.trim().isEmpty()) {
            String t = entry.title.trim().toLowerCase();
            if (t.length() >= 2 && lowerText.contains(t)) return 0.7f;
        }
        if (entry.content != null && !entry.content.isEmpty()) {
            String[] words = entry.content.toLowerCase()
                .split("[\\s，。！？、；：\"'（）《》\\[\\].,!?;:\\n]+");
            int match = 0, total = 0;
            for (String w : words) {
                if (w.length() >= 3) { total++; if (lowerText.contains(w)) match++; }
            }
            if (total > 0) return Math.min((float) match / total * 0.5f, 0.5f);
        }
        return 0.0f;
    }

    private String buildWorldbookWhisper(
            List<com.yoyo.jingxi.data.entity.WorldbookEntry> preEntries,
            List<com.yoyo.jingxi.data.entity.WorldbookEntry> postEntries,
            String recentUserText, int round) {
        List<com.yoyo.jingxi.data.entity.WorldbookEntry> all = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (preEntries != null) {
            for (com.yoyo.jingxi.data.entity.WorldbookEntry e : preEntries) {
                if (e.content != null && !e.content.isEmpty()) {
                    String prefix = e.content.length() >= 50
                        ? e.content.substring(0, 50) : e.content;
                    if (seen.add(prefix)) all.add(e);
                }
            }
        }
        if (postEntries != null) {
            for (com.yoyo.jingxi.data.entity.WorldbookEntry e : postEntries) {
                if (e.content != null && !e.content.isEmpty()) {
                    String prefix = e.content.length() >= 50
                        ? e.content.substring(0, 50) : e.content;
                    if (seen.add(prefix)) all.add(e);
                }
            }
        }
        if (all.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("[世界书提醒] 你的世界观核心约束（始终生效）：\n");
        int len = 0;
        int maxLen = 600;
        for (com.yoyo.jingxi.data.entity.WorldbookEntry e : all) {
            String title = (e.title != null && !e.title.isEmpty())
                ? "「" + e.title + "」" : "";
            String summary = e.content != null
                ? (e.content.length() > 80 ? e.content.substring(0, 80) + "…" : e.content)
                : "";
            float rel = computeWorldbookRelevance(e, recentUserText);
            String line = "- " + (rel >= 0.5f ? "★ " : "") + title + " " + summary + "\n";
            if (len + line.length() > maxLen) {
                sb.append("- …（更多设定已在你记忆中）\n");
                break;
            }
            sb.append(line);
            len += line.length();
        }
        sb.append("\n请在回复时自然地活在这些设定中。");
        return sb.toString();
    }
}