package com.yoyo.jingxi.utils;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.Character;
import com.yoyo.jingxi.data.entity.MyPersona;
import com.yoyo.jingxi.data.entity.RelationshipEdge;
import com.yoyo.jingxi.data.entity.RelationshipNode;
import com.yoyo.jingxi.network.OpenAIManager;
import com.yoyo.jingxi.network.OpenAiRequest;
import com.yoyo.jingxi.network.OpenAiResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import retrofit2.Response;

public class RelationshipExtractionManager {
    private static final String TAG = "RelationshipExtMgr";
    private static final Gson gson = new Gson();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final OpenAIManager aiManager = new OpenAIManager();

    public interface ExtractionCallback {
        void onSuccess();
        void onError(String error);
    }

    public static void extractRelationships(String inputContext, AppDatabase db, ExtractionCallback callback) {
        if (TextUtils.isEmpty(inputContext)) {
            if (callback != null) mainHandler.post(() -> callback.onError("输入为空"));
            return;
        }

        new Thread(() -> {
            String apiKey = SpUtils.getString("OPENAI_API_KEY", "");
            String endpoint = SpUtils.getString("API_ENDPOINT", "https://api.openai.com/");
            String model = SpUtils.getString("API_MODEL", "gpt-4o-mini");

            if (TextUtils.isEmpty(apiKey)) {
                if (callback != null) mainHandler.post(() -> callback.onError("未配置API Key"));
                return;
            }
            if (!endpoint.endsWith("/")) endpoint += "/";
            String finalUrl = endpoint + "v1/chat/completions";

            String prompt = "请分析以下文本，提取出其中所有角色之间的人际关系网络。用户可能会描述创建新的人物，也可能是在补充现有角色的信息或他们之间的关系。\n" +
                    "文本：\n" + inputContext + "\n\n" +
                    "要求：\n" +
                    "1. 将人物作为节点(nodes)，人物之间的关系作为连线(edges)。\n" +
                    "2. nodes 数组中的每个对象包含：'name' (人物名称), 'description' (人物身份或性格的一句话简介)。如果文本中补充了关于该人物的新信息，请将其融入简介中。\n" +
                    "3. edges 数组中的每个对象包含：'source' (人物A的名称), 'target' (人物B的名称), 'relation' (A和B的关系，如'宿敌','师徒','暗恋'等，用简短词语), 'intimacy' (亲密程度，0到100的整数，仇人为0，陌生人为50，至交为100), 'interactionProbability' (互动概率，0.0到1.0的小数，仇人可能为0.1，好友可能为0.9)。\n" +
                    "4. 请严格遵守以下 JSON 格式，不要带任何 Markdown 标记，只要纯JSON字符串：\n" +
                    "{\n" +
                    "  \"nodes\": [\n" +
                    "    {\"name\": \"张三\", \"description\": \"武林盟主\"},\n" +
                    "    {\"name\": \"李四\", \"description\": \"神秘剑客\"}\n" +
                    "  ],\n" +
                    "  \"edges\": [\n" +
                    "    {\"source\": \"张三\", \"target\": \"李四\", \"relation\": \"宿敌\", \"intimacy\": 10, \"interactionProbability\": 0.2}\n" +
                    "  ]\n" +
                    "}";

            OpenAiRequest request = new OpenAiRequest();
            request.model = model;
            request.temperature = 0.3f; // 低 temperature 保证格式稳定
            request.messages = new ArrayList<>();
            request.messages.add(new OpenAiRequest.Message("user", prompt));

            try {
                Response<OpenAiResponse> response = aiManager.getApi().createChatCompletion(finalUrl, "Bearer " + apiKey, request).execute();
                if (response.isSuccessful() && response.body() != null && response.body().choices != null && !response.body().choices.isEmpty()) {
                    String jsonResponse = response.body().choices.get(0).message.content.trim();
                    jsonResponse = cleanJsonResponse(jsonResponse);
                    
                    try {
                        JsonObject resultObj = gson.fromJson(jsonResponse, JsonObject.class);
                        
                        // 开始处理并存入数据库
                        if (resultObj.has("nodes") && resultObj.has("edges")) {
                            JsonArray nodesArray = resultObj.getAsJsonArray("nodes");
                            JsonArray edgesArray = resultObj.getAsJsonArray("edges");
                            
                            // 第一步：处理 Nodes
                            for (JsonElement nodeElement : nodesArray) {
                                JsonObject nodeObj = nodeElement.getAsJsonObject();
                                String name = nodeObj.has("name") ? nodeObj.get("name").getAsString() : "";
                                String desc = nodeObj.has("description") ? nodeObj.get("description").getAsString() : "";
                                
                                if (TextUtils.isEmpty(name)) continue;
                                
                                // 检查是否已存在该名称的节点，避免重复创建虚拟节点
                                boolean nodeExists = false;
                                List<RelationshipNode> existingNodes = db.relationshipNodeDao().getAllNodesSync();
                                if (existingNodes != null) {
                                    for(RelationshipNode en : existingNodes) {
                                        if (name.equals(en.name)) {
                                            nodeExists = true;
                                            break;
                                        }
                                    }
                                }
                                
                                if (!nodeExists) {
                                    RelationshipNode newNode = new RelationshipNode();
                                    newNode.id = UUID.randomUUID().toString();
                                    newNode.name = name;
                                    newNode.description = desc;
                                    newNode.type = 2; // 默认视为纯虚拟背景人物
                                    
                                    // 尝试与现有的 Character 或 MyPersona 匹配
                                    List<Character> chars = db.characterDao().getAllCharactersSync();
                                    if (chars != null) {
                                        for(Character c : chars) {
                                            if (c.name.equals(name) || (c.nickname != null && c.nickname.equals(name))) {
                                                newNode.type = 0;
                                                newNode.referenceId = String.valueOf(c.id);
                                                newNode.avatarPath = c.avatarPath;
                                                break;
                                            }
                                        }
                                    }
                                    
                                    if (newNode.type == 2) {
                                        List<MyPersona> personas = db.myPersonaDao().getAllPersonasSync();
                                        if (personas != null) {
                                            for(MyPersona p : personas) {
                                                if (p.name.equals(name)) {
                                                    newNode.type = 1;
                                                    newNode.referenceId = p.name;
                                                    newNode.avatarPath = p.avatarPath;
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                    
                                    db.relationshipNodeDao().insert(newNode);
                                } else {
                                    // 如果节点已存在，但这次提取到了新的描述，我们可以选择更新它
                                    if (!TextUtils.isEmpty(desc)) {
                                        RelationshipNode existingNode = db.relationshipNodeDao().getNodeByName(name);
                                        if (existingNode != null) {
                                            // 简单处理：将新描述附加到原有描述后面，或者替换。
                                            // 这里采取：如果原有描述为空则替换，否则如果新描述不包含在旧描述中，则追加
                                            if (TextUtils.isEmpty(existingNode.description)) {
                                                existingNode.description = desc;
                                                db.relationshipNodeDao().update(existingNode);
                                            } else if (!existingNode.description.contains(desc) && !desc.contains(existingNode.description)) {
                                                existingNode.description = existingNode.description + "；" + desc;
                                                db.relationshipNodeDao().update(existingNode);
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // 由于需要等LiveData，为了同步操作，最好用DAO同步查询，但为了简化，这里再查一次可能需要写一个新的DAO方法，
                            // 我们可以直接从数据库同步查出所有nodes来映射名称到ID。为了保证能获取到刚插入的数据，我们修改Dao加一个Sync方法，
                            // 或者通过一个简单的查询解决。我们直接使用名称进行临时映射（实际中最好用ID关联，如果名字可能重复）。
                            
                            // 第二步：处理 Edges
                            for (JsonElement edgeElement : edgesArray) {
                                JsonObject edgeObj = edgeElement.getAsJsonObject();
                                String sourceName = edgeObj.has("source") ? edgeObj.get("source").getAsString() : "";
                                String targetName = edgeObj.has("target") ? edgeObj.get("target").getAsString() : "";
                                String relation = edgeObj.has("relation") ? edgeObj.get("relation").getAsString() : "";
                                int intimacy = edgeObj.has("intimacy") ? edgeObj.get("intimacy").getAsInt() : 50;
                                double interactionProbability = edgeObj.has("interactionProbability") ? edgeObj.get("interactionProbability").getAsDouble() : 0.5;
                                
                                if (TextUtils.isEmpty(sourceName) || TextUtils.isEmpty(targetName)) continue;
                                
                                RelationshipNode sourceNode = db.relationshipNodeDao().getNodeByName(sourceName);
                                RelationshipNode targetNode = db.relationshipNodeDao().getNodeByName(targetName);
                                
                                if (sourceNode != null && targetNode != null) {
                                    RelationshipEdge newEdge = new RelationshipEdge();
                                    newEdge.sourceNodeId = sourceNode.id;
                                    newEdge.targetNodeId = targetNode.id;
                                    newEdge.relation = relation;
                                    newEdge.intimacy = intimacy;
                                    newEdge.interactionProbability = interactionProbability;
                                    
                                    // 避免重复插入完全相同的关系
                                    boolean edgeExists = false;
                                    List<RelationshipEdge> existingEdges = db.relationshipEdgeDao().getEdgesForNode(sourceNode.id);
                                    if (existingEdges != null) {
                                        for (RelationshipEdge ee : existingEdges) {
                                            if ((ee.sourceNodeId.equals(sourceNode.id) && ee.targetNodeId.equals(targetNode.id)) ||
                                                (ee.sourceNodeId.equals(targetNode.id) && ee.targetNodeId.equals(sourceNode.id))) {
                                                edgeExists = true;
                                                // 更新现有关系
                                                ee.relation = relation;
                                                ee.intimacy = intimacy;
                                                ee.interactionProbability = interactionProbability;
                                                db.relationshipEdgeDao().update(ee);
                                                break;
                                            }
                                        }
                                    }
                                    if (!edgeExists) {
                                        db.relationshipEdgeDao().insert(newEdge);
                                    }
                                }
                            }
                            
                            if (callback != null) mainHandler.post(callback::onSuccess);
                        } else {
                            if (callback != null) mainHandler.post(() -> callback.onError("返回的JSON格式不正确"));
                        }
                    } catch (JsonSyntaxException e) {
                        Log.e(TAG, "解析JSON失败", e);
                        if (callback != null) mainHandler.post(() -> callback.onError("解析关系数据失败"));
                    }
                } else {
                    if (callback != null) mainHandler.post(() -> callback.onError("API请求失败"));
                }
            } catch (Exception e) {
                Log.e(TAG, "网络错误", e);
                if (callback != null) mainHandler.post(() -> callback.onError("网络或处理异常: " + e.getMessage()));
            }
        }).start();
    }

    private static String cleanJsonResponse(String jsonResponse) {
        if (jsonResponse.contains("```json")) {
            int start = jsonResponse.indexOf("```json") + 7;
            int end = jsonResponse.lastIndexOf("```");
            if (end > start) {
                jsonResponse = jsonResponse.substring(start, end);
            }
        } else if (jsonResponse.contains("```")) {
            int start = jsonResponse.indexOf("```") + 3;
            int end = jsonResponse.lastIndexOf("```");
            if (end > start) {
                jsonResponse = jsonResponse.substring(start, end);
            }
        }
        return jsonResponse.trim();
    }
}