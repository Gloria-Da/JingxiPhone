package com.yoyo.jingxi.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理 AI 聊天中用户个人数据的可见性配置。
 * 三维权限模型：类别 × 角色 × 人设。
 * 存储为 SharedPreferences 中的单个 JSON key。
 */
public class UserContextSettings {

    private static final String KEY = "USER_CONTEXT_CONFIG";
    private static final Gson gson = new Gson();

    /**
     * 单个类别的权限配置。
     */
    public static class CategoryConfig {
        public boolean enabled;        // 读取权限
        public boolean writeEnabled;   // 写入权限（AI能否修改此类数据，默认false）
        public List<Integer> characterIds = new ArrayList<>();
        public String persona = ""; // 空字符串 = 主人设

        public CategoryConfig() {}
    }

    public Map<String, CategoryConfig> configs = new HashMap<>();

    public UserContextSettings() {
        // 初始化三个默认类别（全部关闭）
        ensureDefaults();
    }

    private void ensureDefaults() {
        for (String cat : new String[]{"calendar", "period", "course"}) {
            if (!configs.containsKey(cat)) {
                configs.put(cat, new CategoryConfig());
            }
        }
    }

    /**
     * 从 SharedPreferences 加载配置。如果不存在或解析失败，返回全关闭的默认配置。
     */
    public static UserContextSettings load() {
        String json = SpUtils.getString(KEY, "");
        if (json.isEmpty()) {
            return new UserContextSettings();
        }
        try {
            Type type = new TypeToken<Map<String, CategoryConfig>>() {}.getType();
            Map<String, CategoryConfig> map = gson.fromJson(json, type);
            UserContextSettings settings = new UserContextSettings();
            if (map != null) {
                settings.configs = map;
            }
            settings.ensureDefaults();
            return settings;
        } catch (Exception e) {
            e.printStackTrace();
            return new UserContextSettings();
        }
    }

    /**
     * 保存配置到 SharedPreferences。
     */
    public void save() {
        ensureDefaults();
        String json = gson.toJson(configs);
        SpUtils.putString(KEY, json);
    }

    /**
     * 检查指定类别是否对指定角色和人设可见。
     *
     * @param category    "calendar" / "period" / "course"
     * @param characterId AI 角色 ID
     * @param personaName 当前使用的人设名（可为 null）
     * @return true 如果该类别数据应注入到此角色的 prompt 中
     */
    public boolean isEnabled(String category, int characterId, String personaName) {
        CategoryConfig cfg = configs.get(category);
        if (cfg == null || !cfg.enabled) return false;
        if (cfg.characterIds == null || !cfg.characterIds.contains(characterId)) return false;

        // persona 匹配规则：空字符串 = 任意人设都生效；非空 = 必须匹配
        if (!cfg.persona.isEmpty()) {
            String pn = personaName != null ? personaName : "";
            if (!cfg.persona.equals(pn)) return false;
        }
        return true;
    }

    /**
     * 检查指定类别是否允许 AI 写入（修改数据）。
     * 与 isEnabled 使用相同的角色/人设过滤规则，但检查 writeEnabled 而非 enabled。
     *
     * @param category    "calendar" / "period" / "course"
     * @param characterId AI 角色 ID
     * @param personaName 当前使用的人设名（可为 null）
     * @return true 如果 AI 可以修改此类别的数据
     */
    public boolean canWrite(String category, int characterId, String personaName) {
        CategoryConfig cfg = configs.get(category);
        if (cfg == null || !cfg.writeEnabled) return false;
        if (cfg.characterIds == null || !cfg.characterIds.contains(characterId)) return false;

        if (!cfg.persona.isEmpty()) {
            String pn = personaName != null ? personaName : "";
            if (!cfg.persona.equals(pn)) return false;
        }
        return true;
    }

    /**
     * 设置指定类别的写入权限是否启用。
     */
    public void setWriteEnabled(String category, boolean enabled) {
        getConfig(category).writeEnabled = enabled;
    }

    /**
     * 获取指定类别的 CategoryConfig（确保非 null）。
     */
    public CategoryConfig getConfig(String category) {
        ensureDefaults();
        CategoryConfig cfg = configs.get(category);
        if (cfg == null) {
            cfg = new CategoryConfig();
            configs.put(category, cfg);
        }
        return cfg;
    }

    /**
     * 设置指定类别是否启用。
     */
    public void setEnabled(String category, boolean enabled) {
        getConfig(category).enabled = enabled;
    }

    /**
     * 设置指定类别允许的角色 ID 列表。
     */
    public void setCharacterIds(String category, List<Integer> ids) {
        getConfig(category).characterIds = (ids != null) ? ids : new ArrayList<>();
    }

    /**
     * 设置指定类别关联的人设名（空=主人设）。
     */
    public void setPersona(String category, String persona) {
        getConfig(category).persona = (persona != null) ? persona : "";
    }
}
