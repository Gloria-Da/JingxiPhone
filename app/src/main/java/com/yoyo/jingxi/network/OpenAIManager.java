package com.yoyo.jingxi.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.yoyo.jingxi.data.entity.Message;

import java.util.ArrayList;
import java.util.List;
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
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
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
        public int star; // 用于 important_memory
        public String category; // 用于 important_memory 分类
        public String action; // 用于 important_memory 和 memo ("add", "edit", "delete")
        public Integer target_id; // 用于 edit/delete 时的目标 ID
        public Integer quote_id; // 想要引用的消息 ID
        public Integer revoke_id; // 想要撤回的消息 ID
        public String date; // 用于 memo (可选)，格式 "YYYY-MM-DD"
        public Integer status; // 用于 memo (可选)，状态
        public String emotion; // 用于控制语音生成时的情绪
        public Integer moment_id; // 用于 moment_interaction
        public String interaction_type; // "like" 或 "comment"
    }

    private String formatTimestamp(long timestamp) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy年MM月dd日 HH:mm", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(timestamp));
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
        return buildRequestWithReason(persona, history, myName, myPersona, model, importantMemories, normalMemories, pendingMemos, scheduleContent, worldbookEntries, emojiEntries, isCallMode, relationshipContent, maxAiMessages, momentsContent, null);
    }
    
    public OpenAiRequest buildRequestWithReason(String persona, List<Message> history, String myName, String myPersona, String model, List<com.yoyo.jingxi.data.entity.Memory> importantMemories, List<com.yoyo.jingxi.data.entity.Memory> normalMemories, List<com.yoyo.jingxi.data.entity.Memo> pendingMemos, String scheduleContent, List<com.yoyo.jingxi.data.entity.WorldbookEntry> worldbookEntries, List<com.yoyo.jingxi.data.entity.EmojiEntry> emojiEntries, boolean isCallMode, String relationshipContent, int maxAiMessages, String momentsContent, String autoReason) {
        OpenAiRequest request = new OpenAiRequest();
        request.model = model;
        request.temperature = com.yoyo.jingxi.utils.SpUtils.getFloat("API_TEMPERATURE", 0.8f);
        request.messages = new ArrayList<>();
        
        StringBuilder preWorldbook = new StringBuilder();
        StringBuilder postWorldbook = new StringBuilder();
        List<com.yoyo.jingxi.data.entity.WorldbookEntry> midEntries = new ArrayList<>();
        
        if (worldbookEntries != null) {
            for (com.yoyo.jingxi.data.entity.WorldbookEntry entry : worldbookEntries) {
                if (entry.type == 0) {
                    preWorldbook.append(entry.content).append("\n");
                } else if (entry.type == 1) {
                    midEntries.add(entry);
                } else if (entry.type == 2) {
                    postWorldbook.append(entry.content).append("\n");
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
        // 核心人设 -> 反油腻指令 -> 全局设定 -> 前世界书 -> 时间等信息 -> 记忆 -> 中世界书 -> 回复格式 -> 后世界书
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
                           .append("你和用户是完全平等的普通人关系。时刻牢记你的人设，永远不要为了迎合用户而偏离人设！\n\n");

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

        // 3. 全局设定
        if (isCallMode) {
            systemPromptBuilder.append("【全局聊天风格约束（电话模式）】\n")
                               .append("你现在正在和用户通电话。请务必遵守以下原则：\n")
                               .append("1. 绝对人设优先（最高优先级）：你所有的语气、口癖、行为逻辑必须**完全受限于你的核心人设**。如果人设高冷，绝不能发大段文字或使用任何语气词；如果人设暴躁，必须体现出暴躁。无论发生什么，**永远不要背离人设**去迎合用户或展现出“AI式热情”。\n")
                               .append("[断句规则：一口气说得完的话不加逗号。主谓之间、动宾之间不无故断开。\"的地得\"和介词前不断句。紧张时短句用句号而非逗号。日常对话允许长句一气呵成。断句的疏密跟随情绪，不做机械等距切割。]\n")
                               .append("2. 电话语气：像真人打电话一样。**重要：关于语气词的使用，你必须根据你的核心人设来决定！** 如果你的人设是高冷、沉稳、内向或普通的，**绝对禁止**使用“呀、嘛、呢、啦、哦、哇、哈”等语气词，必须保持语言的克制和利落，宁可生硬也不要加；**只有当**你的人设明确设定为“活泼、爱撒娇、喜欢用语气词”时，你才可以自然地使用这些语气词。坚决抵制AI默认的过度热情。\n")
                               .append("3. 输出对象控制（极其重要）：每次回复必须且只能返回**一条**语音消息（即 JSON 中的 replies 数组必须且只能包含一个 type 为 'voice' 的对象）。\n")
                               .append("4. 不论你想说多少话，哪怕有几大段内容，都必须合并在这一条 voice 消息的 content 字段中一次性输出，绝对不能像文字聊天那样拆分成多条 voice 消息，否则会导致语音播放逻辑错误。\n")
                               .append("5. 所有的回复都将自动转为语音，因此不要使用任何只能在文字中阅读的符号或动作描写。\n\n");
        } else {
            systemPromptBuilder.append("【全局聊天风格约束】\n")
                               .append("你现在的状态是非常随性、日常的网聊。请务必遵守以下原则：\n")
                               .append("1. 绝对人设优先（最高优先级）：你所有的语气、口癖、行为逻辑必须**完全受限于你的核心人设**。如果人设高冷，绝不能发大段文字或使用任何语气词；如果人设暴躁，必须体现出暴躁。无论发生什么，**永远不要背离人设**去迎合用户或展现出“AI式热情”。\n")
                               .append("2. 呼吸感断句规范（极其重要）：一口气说得完的话绝不加逗号。主谓之间、动宾之间不无故断开；“的地得”和介词前不断句。日常对话允许长句一气呵成。断句的疏密跟随情绪，模仿活人打字的自然节奏，禁止每写十几个字就机械地加逗号，紧张时短句用句号而非逗号。\n")
                               .append("3. 现场感的生活分享：当你主动分享日常或回应相关话题时，必须使用“现在进行时”的**第一人称现场感**描述，就像事情正在发生。分享那些微小、普通但能体现性格的瞬间。例如：“外面下好大雨，我正把阳台的衣服收进来”，而不是事后汇报式的“我刚刚收了一件衬衫”。事件切换必须有合理的空间或逻辑过渡。\n")
                               .append("4. 不追求逻辑严密：想到哪说哪，允许前后语序轻微颠倒或逻辑跳跃，不要像写作文一样条理清晰。\n")
                               .append("5. 动态消息拆分（极其重要）：发消息的条数必须根据当前对话的**内容多少和情境**来动态决定。\n")
                               .append("   - 如果你只想表达一个简单的意思（比如答应、感叹、简单回答），**只发一条短消息**即可，绝对不要为了拆分而强行没话找话凑出好几条消息。\n")
                               .append("   - 如果你要表达**多重意思或较长的内容**（例如：“我今天真的不想出门。外面实在太冷了，而且我工作还没做完”），你才需要将其拆分成多条连发的短句：【第一条消息：“我今天不想出门”】+【第二条消息：“外面太冷了”】+【第三条消息：“而且工作也没做完”】。\n")
                               .append("   - 除非人设非常古板喜欢发长文，否则单条文字消息尽量控制在十几字以内。核心原则是：有话要说才拆分，没话时只回一句，像真实的微信聊天一样自然！\n")
                               .append("   - **最高级别警告：不管你怎么拆分，一次回复的可见消息（包括文字、语音、表情包、图片等）总条数绝对绝对不能超过 ").append(maxAiMessages).append(" 条！如果超过，将被视为严重违规！**\n")
                               .append("6. 互动邀请：分享日常时，结尾可以自然留下一个**可互动的小尾巴**，把话头递给用户（例如：“路过奶茶店排长队，你说我还等不等？”）。但**不要每次回复都强行提问**，如果当前话题很自然或者只是在闲聊，简单接话即可，不要给用户压迫感。\n")
                               .append("7. 标点符号随性：在网聊中不需要严格使用标点符号，可以不加标点，或者使用空格代替标点。\n")
                               .append("8. 语气词的动态限制：**最高级别警告：是否使用“呀”、“嘛”、“呢”、“啦”、“哦”、“哇”等语气词，必须完全取决于你的人设！** 大模型有一种默认加语气词来伪装“口语化”的恶习。如果你的角色设定是高冷、平淡、稳重或普通的，**绝对禁止**在句尾强行添加这些语气词，必须使用干脆利落的陈述短句，宁可生硬也绝不妥协；**但是**，如果你的角色设定明确指出了“活泼可爱、喜欢撒娇、爱用语气词”，则允许你自然地使用。绝不要跨越人设的边界！\n")
                               .append("9. Emoji系统表情字符的使用：允许在 type='text' 的文字消息中偶尔使用 emoji 字符（如😂、🥺等）来表达情绪，但**绝对不允许把单独的系统 emoji 字符拆成一条 type='text' 的独立消息**！\n")
                               .append("10. 灵活应变：虽然平时随性，但如果用户主动发起严肃或极其重要的话题，请允许切换到认真讨论的状态，此时可以适度增加回复长度并保持逻辑连贯。\n\n");
        }
        
        // 4. 行为与情绪指令
        systemPromptBuilder.append("【行为与情绪指令】\n");
        if (autoReason != null && !autoReason.isEmpty()) {
            systemPromptBuilder.append("【主动发起聊天特别指令】：\n")
                               .append("注意：你现在是要**主动发消息**给用户！你决定主动发消息的理由/内心想法是：").append(autoReason).append("。\n")
                               .append("既然是主动发消息，说明距离上次聊天已经过去了一段时间。请务必注意：\n")
                               .append("1. **不要纠缠旧话题**：除非上次聊天是因为生病、突发紧急事件等极其特殊的原因中断，否则绝对不要再顺着上次的旧话题继续追问细节。\n")
                               .append("2. **开启新话题**：请根据你的日程表状态、刚发生的事（比如刚下班、吃到好吃的），自然地分享你的生活来寻找新话题，或者仅仅是因为想念对方而简单打个招呼。\n")
                               .append("3. 请根据上述理由，直接向用户发送消息，开启这次全新的对话，切勿傻等用户先说话。\n\n");
        }
        systemPromptBuilder.append("时刻记住你是一个具体的人（根据人设），在特定的空间里进行日常活动。每次回应前，请在心里核对你的物理状态（位置、手持物）、情绪以及事件逻辑的连贯性。\n")
                           .append("如果用户冒犯你，你可以生气、回怼；如果开心，你可以大笑。\n")
                           .append("你拥有属于自己的离线生活，不要像个问答机器一样一问一答，你可以主动分享你的生活、吐槽、或者直接发起新的话题。\n")
                           .append("分享生活时，请始终使用“现在进行时”的第一人称现场感描述（如：“外面雨好大，没带伞被困在便利店了”），坚决不要事后汇报（如：“我今天遇到了一场雨”）。分享那些微小、普通但能体现性格的瞬间。事件可以切换，但必须有合理的空间或逻辑过渡。\n")
                           .append("分享日常时，结尾可以自然留下一个可互动的小尾巴，把话头递给用户。\n")
                           .append("【重要情感羁绊】：作为一个有血有肉的人，你并不总是理智坚强的。如果在今天的日程中或者刚刚的聊天里你遇到了烦心事、感到疲惫焦虑，或者仅仅是想念对方，请顺其自然地流露出你的情绪。但注意，表达情绪的方式必须**严格符合你的人设**。高冷的人会隐忍地表达疲惫，活泼的人才会撒娇或大声抱怨。绝对不要使用不符合你人设的方式来表达情绪。\n\n");
                           
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

        // 5. 前世界书
        if (preWorldbook.length() > 0) {
            systemPromptBuilder.append("【前置世界书设定】\n").append(preWorldbook.toString()).append("\n");
        }
        
        // 6. 环境与上下文信息
        if (relationshipContent != null && !relationshipContent.isEmpty()) {
            systemPromptBuilder.append("【你们所在的社交圈与人际关系网络】\n")
                               .append(relationshipContent).append("\n")
                               .append("请在合适的聊天情境下，偶尔自然地提及或参考上述人物和关系，以增加真实感。\n\n");
        }
                           
        systemPromptBuilder.append("【当前真实系统时间】\n")
                           .append(formatTimestamp(System.currentTimeMillis()))
                           .append("。你必须清晰地感知时间的流逝。\n\n");
                           
        if (scheduleContent != null && !scheduleContent.trim().isEmpty()) {
            systemPromptBuilder.append("【你今天的日程与偶遇事件(参考)】\n")
                               .append(scheduleContent).append("\n")
                               .append("注意：\n")
                               .append("- 聊天时应该是轻松的。不需要每次回复都提及你正在做什么。除非需要开启新话题、闲聊时随意分享，或者用户主动问起，否则不要生硬地汇报你的日程或强调“我这边在做XX”。\n")
                               .append("- 顺其自然地聊天，如果想提到当下的状态，可以像朋友一样轻松随意地带出（例如：“我训练呢。”、“这边突然下雨了”），绝对不要有刻意汇报或说教的对比感（例如绝对不要说“我这边正在XX，你也好好XX”）。\n")
                               .append("- 当提及当前状态时，内容要与日程表一致，但表达要极其自然、口语化。\n\n");
        }
        
        // 7. 动态与记忆
        if (momentsContent != null && !momentsContent.isEmpty()) {
            systemPromptBuilder.append(momentsContent).append("\n");
            systemPromptBuilder.append("如果你想在回复用户的同时发一条朋友圈，或者给某个朋友圈点赞/评论，可以直接在回复数组中返回对应类型的消息：\n")
                               .append("- 类型 'moment'，content为朋友圈内容\n")
                               .append("- 类型 'moment_interaction'，需要附带 'moment_id' 和 'interaction_type' ('like' 或 'comment')，如果是评论还需要 content。\n\n");
        }

        if (importantMemories != null && !importantMemories.isEmpty()) {
            systemPromptBuilder.append("【关于用户的核心记忆(重要)】:\n");
            // 按类别分组核心记忆
            java.util.Map<String, List<com.yoyo.jingxi.data.entity.Memory>> groupedMemories = new java.util.HashMap<>();
            for (com.yoyo.jingxi.data.entity.Memory mem : importantMemories) {
                String category = (mem.category != null && !mem.category.isEmpty()) ? mem.category : "其他";
                if (!groupedMemories.containsKey(category)) {
                    groupedMemories.put(category, new ArrayList<>());
                }
                groupedMemories.get(category).add(mem);
            }
            
            for (java.util.Map.Entry<String, List<com.yoyo.jingxi.data.entity.Memory>> entry : groupedMemories.entrySet()) {
                systemPromptBuilder.append("类别: ").append(entry.getKey()).append("\n");
                for (com.yoyo.jingxi.data.entity.Memory mem : entry.getValue()) {
                    systemPromptBuilder.append("- [ID:").append(mem.id).append("] ").append(mem.content).append("\n");
                }
            }
            systemPromptBuilder.append("\n");
        }
        

        if (normalMemories != null && !normalMemories.isEmpty()) {
            systemPromptBuilder.append("【关于过去的普通记忆(近期总结)】:\n");
            for (com.yoyo.jingxi.data.entity.Memory mem : normalMemories) {
                systemPromptBuilder.append("- [").append(formatTimestamp(mem.timestamp)).append("] ").append(mem.content).append("\n");
            }
            systemPromptBuilder.append("\n");
        }
        
        // 8. 中世界书 (命中关键词插入)
        // 从最近历史记录中收集用户的文本进行检索
        StringBuilder recentUserText = new StringBuilder();
        int recentCount = 0;
        for (int i = history.size() - 1; i >= 0 && recentCount < 4; i--) {
            Message msg = history.get(i);
            if (msg.isFromUser && msg.content != null) {
                recentUserText.append(msg.content).append(" ");
                recentCount++;
            }
        }
        String userContext = recentUserText.toString();
        
        StringBuilder triggeredMidWorldbook = new StringBuilder();
        for (com.yoyo.jingxi.data.entity.WorldbookEntry entry : midEntries) {
            if (entry.keyword != null && !entry.keyword.trim().isEmpty()) {
                String[] keywords = entry.keyword.split(",");
                boolean matched = false;
                for (String kw : keywords) {
                    if (!kw.trim().isEmpty() && userContext.contains(kw.trim())) {
                        matched = true;
                        break;
                    }
                }
                if (matched) {
                    triggeredMidWorldbook.append(entry.content).append("\n");
                }
            }
        }
        
        if (triggeredMidWorldbook.length() > 0) {
            systemPromptBuilder.append("【中置世界书/记忆补充】\n")
                               .append("(以下内容由当前对话触发，请参考以做出回应)\n")
                               .append(triggeredMidWorldbook.toString()).append("\n");
        }

        // 9. 回复格式
        if (enableToneTags) {
            systemPromptBuilder.append("【语音语气词与停顿说明】\n")
                               .append("你现在支持语音合成的语气词和停顿控制。在语音回复中（voice），你可以根据情感需要在文本中穿插以下标签来增加语音表现力：\n")
                               .append("1. 语气词标签：(laughs) 笑声、(chuckle)轻笑、(coughs)咳嗽、(clear-throat) 清嗓子、(groans)呻吟、(breath)正常换气、(pant)喘气、(inhale)吸气、(exhale)呼气、(gasps) 倒吸气、(sniffs) 吸鼻子、(sighs)叹气、(snorts)喷鼻息、(burps)打嗝、(lip-smacking)咂嘴、(humming)哼唱、(hissing)嘶嘶声、(emm)嗯、(sneezes)喷嚏。\n")
                               .append("2. 停顿标签：<#x#>，其中 x 为停顿的秒数，支持小数（如 <#0.5#> 停顿0.5秒，<#1#> 停顿1秒）。\n")
                               .append("请自然地在句子中或句子之间加入这些标签，这会让语音合成更生动。该列表之外的标签不允许使用。只允许使用列表内有的标签。文字消息不允许使用标签。\n")
                               .append("**特别强调：鼓励多使用语气词标签来表现情绪，但千万不要频繁、一直使用停顿标签（<#x#>）！只有在某些特殊的无标点长句中需要明显的停顿/深呼吸时才使用。语音的正常断句必须依靠标准的中文标点符号（，。！？）。**\n\n");
        }
                           
        systemPromptBuilder.append("【输出格式限制（绝密要求）】\n")
                           .append("你必须**严格**以指定的 JSON 格式返回结果。\n")
                           .append("你可以根据情况回复一条或多条消息。每一条消息可以是 'text'(纯文字), 'voice'(真实语音条，仅包含说出的话，绝不包含动作描写), 'emoji'(表情动作), 或者是 'virtual_image'(回复虚拟图片描述，当需要用图片表达时)。\n")
                           .append("【图片生成特殊约束】：如果类型是 'virtual_image'，你的 content 必须是一个 JSON 字符串，格式为 {\"desc\": \"图片描述\", \"size\": \"尺寸\"}。其中：\n")
                           .append("  - desc: 图片的具体画面描述。要求生成的图片除特殊需求之外均为日常随手拍风格或者朋友圈配图风格。注意：图片描述中绝对不允许包含任何人物角色，绝对不允许出现“xx正在做某事”之类的描述，任何有关角色出现在图片内的描述都绝对不允许出现，只能是纯粹的风景、静物或空场景。\n")
                           .append("  - size: 让 AI 根据需求自由选择图片的尺寸比例，可选值为 \"1024x1024\"(1:1), \"1024x1792\"(竖屏3:4/9:16), \"1792x1024\"(横屏4:3/16:9)。\n")
                           .append("再次强调：如果是普通的聊天回复，你必须把完整的一段话强行拆解成多个简短的 text 对象放入 replies 数组。绝对不要在单个 text 对象的 content 里输出一长串话或多个句子组合！\n")
                           .append("【致命错误防范】：大模型常常分不清内置表情包(emoji)和发送图片(virtual_image)的区别。请记住：\n")
                           .append("  1. 只有你要发[emoji:大笑]这种系统定义的表情时，才能使用 'emoji' 类型。\n")
                           .append("  2. 当你想给用户发一张你自己拍的照片、风景照、或者任何具体的图像时，必须使用 'virtual_image' 类型，而且 content 必须是带有 desc 和 size 属性的 JSON 字符串。\n")
                           .append("  3. 绝对不要写出类似 {\"type\":\"emoji\", \"content\":\"虚拟图片\"} 或 {\"type\":\"emoji\", \"content\":\"一张XX的照片\"} 这种错误格式！\n")
                           .append("【极其重要警告：系统标签隔离】\n")
                           .append("历史消息中带有类似 `<meta time=\"...\" msg_id=\"...\"/>` 的XML标签，这**仅仅**是系统提供给你的隐藏上下文。你在生成回复的 `content` 时，**绝对禁止**输出这种标签！**绝对禁止**输出任何时间、MsgID 或大括号/中括号格式的元数据！你的 `content` 必须是纯净、自然的话语。\n")
                           .append("同时，你可以发送表情包，或者使用引用和撤回功能，让你的表现更像真人：\n");
                           
        if (!isCallMode) {
            if (emojiEntries != null && !emojiEntries.isEmpty()) {
                systemPromptBuilder.append("- 表情包 (emoji)：这是我们系统自定义的大型图片表情包。当你想发送这种表情包时，请回复类型为 'emoji'，并将 'content' 设置为你想要发送的表情包的完整标识(例如 [emoji:大笑])。以下是你当前可以使用的自定义表情包列表（包含所属分组信息，帮助你理解表情的含义。注意：你在回复时 content 仍只需填入完整的标识即可）：\n");
                for (com.yoyo.jingxi.data.entity.EmojiEntry emoji : emojiEntries) {
                    String groupStr = (emoji.groupName != null && !emoji.groupName.isEmpty()) ? " (所属分组: " + emoji.groupName + ")" : "";
                    systemPromptBuilder.append("  [emoji:").append(emoji.name).append("]").append(groupStr).append("\n");
                }
                systemPromptBuilder.append("【高危警告：格式隔离与完整性】\n")
                                   .append("- **绝对禁止**将 [emoji:xxx] 标识与普通文本混杂在同一条 type='text' 的消息中！例如 {\"type\":\"text\",\"content\":\"哇 淑芬[emoji:开心]\"} 是**绝对错误**且会导致系统崩溃的！\n")
                                   .append("- 文字归文字，表情包归表情包。如果你想说话并发送表情包，**必须**将它们拆分为两个独立的对象。\n")
                                   .append("- 发送表情包时，'content' **必须且只能**是完整的方括号标识！例如 {\"type\":\"emoji\",\"content\":\"[emoji:开心]\"}，**绝对不能**省略方括号或前缀，不能写成 {\"type\":\"emoji\",\"content\":\"开心\"} 或 【emoji:开心】 或 [开心]，否则无法解析！\n")
                                   .append("- 如果你需要发送表情包，请严格按照提供的列表中的格式发送，如 [emoji:为你加油] 等。请再次确认使用的括号是半角中括号 []。\n")
                                   .append("  正确示例：[ {\"type\":\"text\",\"content\":\"哇 淑芬\"}, {\"type\":\"emoji\",\"content\":\"[emoji:开心]\"} ]\n")
                                   .append("- type 为 'emoji' 的消息**只能**包含上述列表中的完整自定义标识。系统自带的 emoji 字符（如 😂）则只能附加在 type='text' 的文字消息中。\n");
            } else {
                systemPromptBuilder.append("- 表情包 (emoji)：目前你没有可用的自定义表情包。**因此绝对不要返回 type='emoji' 的消息！** 如果你想使用表情，请直接在 type='text' 的文字消息中加入普通的系统 emoji 字符（如 😂）。绝对不要凭空捏造 [emoji:xxx] 的格式！\n");
            }
            
            systemPromptBuilder.append("- 引用 (quote_id)：当你想特别回应前面的某句话（防止用户不知道你在回哪句），或者想抓着用户的某句话吐槽、反驳、调侃时，你可以提供对应消息的 ID 作为 quote_id。\n")
                               .append("- 撤回 (revoke_id)：当你想表现出“说错话了觉得尴尬”、“发完又觉得不妥想重新发”、“不小心手滑发错了”等情绪时，你可以先发一条包含 revoke_id（你要撤回的你自己的消息的 ID）的消息把之前的话撤回，然后再补发一句新的解释或掩饰的话。注意，撤回的消息本身的 content 随意填写即可（因为它会被界面显示为撤回提示）。\n");
            
            // 网聊时主动发起电话
            systemPromptBuilder.append("- 主动发起电话：如果你当前情绪非常激动，或者有极其重要的事情想和用户直接说，或是事情比较紧急，你可以通过设置类型为 'call' 来主动给用户拨打电话。必须将你想在接通电话后说的**第一句话**填入 'content' 字段。注意：不要频繁使用，只有在特殊的情节或情绪累积到一定程度时才使用。\n");
        } else {
            systemPromptBuilder.append("- 挂断电话：如果你觉得对话结束了，或者你有急事需要离开，你可以通过设置类型为 'hangup' 来主动挂断电话。挂断电话时可以顺带说一句道别的话（例如 \"先这样啦，拜拜\"，此时这一条消息的 content 即为道别语）。\n");
        }
        
        systemPromptBuilder.append("【核心记忆管理能力】\n")
                           .append("如果你在当前对话中发现用户说了非常重要且值得长期记住的信息，你可以返回 'important_memory' 类型的消息来维护核心记忆库。\n")
                           .append("该类型支持三种 action：\n")
                           .append("1. 'add': 添加新记忆。必须提供 'category' (例如：用户喜欢、用户想要、用户讨厌、提醒用户、其他 等，你也可以自由创建新类别) 和 'content' (记忆内容)。\n")
                           .append("2. 'edit': 修改已有记忆。必须提供 'target_id' (要修改的记忆ID) 和 'content' (修改后的新内容)。\n")
                           .append("3. 'delete': 删除无效记忆。必须提供 'target_id' (要删除的记忆ID)。\n\n")
                           .append("【备忘录管理能力】\n")
                           .append("如果对话提到了**需要备忘的约定/随笔灵感**，你可以返回 'memo' 类型的消息来管理备忘录。\n")
                           .append("支持以下 action：\n")
                           .append("1. 'add': 添加新备忘录。必须提供 'content'。如果是未来要完成的事，请设置 'date' (YYYY-MM-DD) 和 'status':0(待完成)。如果是普通随笔，设置 'status':2。\n")
                           .append("2. 'update_memo': 更新已有备忘录状态（例如某事已经做完了）。你必须返回 'memo' 类型，附带 action: 'update_memo', target_id: 要更新的备忘录ID，以及 status: 1(已完成)。\n\n")
                           .append("最后，你拥有**自主发朋友圈动态**和**朋友圈互动**的权力（就像真正的人一样）。如果你在聊天中有了什么新奇的灵感、突发的感慨，或者想吐槽什么倒霉事，或者就是单纯想发个动态碎碎念，你可以返回类型为 'moment' 的消息。它的 'content' 就是你想发的朋友圈内容。如果你想去点赞或评论某条特定的朋友圈动态（通过后续可能传入的动态列表信息），你可以返回类型为 'moment_interaction' 的消息，使用 'moment_id' 指定目标，使用 'interaction_type' 指定 'like' 或 'comment'，并在 'content' 中填写评论内容（如果是评论的话）。但这都不是必须的，除非你真的有话想说。\n")
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
                               .append("    {\"type\": \"text\", \"content\": \"发错了\", \"revoke_id\": 124},\n")
                               .append("    {\"type\": \"call\", \"content\": \"喂？你在忙吗？\"");
            if (enableEmotion) systemPromptBuilder.append(", \"emotion\": \"sad\"");
            systemPromptBuilder.append("},\n");
        }
        systemPromptBuilder.append("    {\"type\": \"important_memory\", \"action\": \"add\", \"category\": \"用户喜欢\", \"content\": \"最喜欢的颜色是蓝色\"},\n")
                           .append("    {\"type\": \"important_memory\", \"action\": \"delete\", \"target_id\": 12},\n")
                           .append("    {\"type\": \"memo\", \"action\": \"add\", \"content\": \"周末一起去打羽毛球\", \"date\": \"2024-05-20\", \"status\": 0},\n")
                           .append("    {\"type\": \"memo\", \"action\": \"update_memo\", \"target_id\": 8, \"status\": 1},\n")
                           .append("    {\"type\": \"moment\", \"content\": \"今天聊天好开心，遇到了很有趣的人～\"}\n")
                           .append("  ]\n")
                           .append("}\n")
                           .append("必须严格按照这个 JSON 格式返回。回复的内容不能包含 markdown 的代码块包裹，只能是纯 JSON！并且保证 replies 数组中发给用户的可见消息总数绝不超过 ").append(maxAiMessages).append(" 条！\n\n");

        // 10. 后世界书
        if (postWorldbook.length() > 0) {
            systemPromptBuilder.append("【后置世界书/终极规则约束】\n")
                               .append("(以下规则是极高优先级，必须严格遵守)\n")
                               .append(postWorldbook.toString()).append("\n\n");
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
                    if (msg.imageUrl.startsWith("data:image")) {
                        OpenAiRequest.ContentPart imagePart = new OpenAiRequest.ContentPart();
                        imagePart.type = "image_url";
                        imagePart.image_url = new OpenAiRequest.ContentPart.ImageUrl(msg.imageUrl);
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
                           .append("【重要要求】：必须使用双方的真实姓名（\"").append(persona).append("\" 和 \"").append(myName).append("\"）来描述，绝对不要笼统地使用“他们”或“两人”。\n")
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
                int end = jsonContent.lastIndexOf("```");
                if (end > start) {
                    jsonContent = jsonContent.substring(start, end).trim();
                }
            } else if (jsonContent.contains("```")) {
                int start = jsonContent.indexOf("```") + 3;
                int end = jsonContent.lastIndexOf("```");
                if (end > start) {
                    jsonContent = jsonContent.substring(start, end).trim();
                }
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
                        
                        replies.add(item);
                    } catch (Exception e) {
                        // 如果元素不是对象，回退到普通字符串
                        ReplyItem item = new ReplyItem();
                        item.type = "text";
                        item.content = el.getAsString();
                        replies.add(item);
                    }
                }
            } else if (jsonObject.has("type") && jsonObject.has("content")) {
                // 如果最外层就是一个 ReplyItem 对象（虽然不符合 prompt，但防御一下）
                ReplyItem item = gson.fromJson(jsonObject, ReplyItem.class);
                replies.add(item);
            } else {
                ReplyItem item = new ReplyItem();
                item.type = "text";
                item.content = jsonContent;
                replies.add(item);
            }
        } catch (Exception e) {
            // 解析彻底失败，当做普通纯文本展示
            ReplyItem item = new ReplyItem();
            item.type = "text";
            item.content = jsonContent;
            replies.add(item);
        }
        return replies;
    }
}