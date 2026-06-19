package com.yoyo.jingxi.utils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户画像预设键（Profile Preset Keys）。
 * 定义了 8 个类别、83 个预设键项，用于判断某个 profile 条目是否为系统预设。
 * 从 MemoryManager 中提取，纯静态工具类。
 */
public class MemoryPresetKeys {

    private static final Map<String, List<String>> PRESET_KEYS = new LinkedHashMap<>();
    static {
        PRESET_KEYS.put("档案", java.util.Arrays.asList(
            "姓名", "昵称", "生日", "年龄", "性别", "职业",
            "MBTI", "性格特点", "兴趣爱好", "说话风格", "口头禅",
            "她喜欢我怎么称呼她", "外貌特征", "穿搭风格",
            "喜欢的颜色", "喜欢的音乐类型", "喜欢的季节", "喜欢的天气",
            "讨厌的颜色", "讨厌的声音", "日常发型"
        ));
        PRESET_KEYS.put("作息", java.util.Arrays.asList(
            "起床时间", "睡觉时间", "早起还是夜猫子", "午休习惯",
            "工作日节奏", "周末节奏", "精力最好时段", "失眠倾向", "熬夜频率"
        ));
        PRESET_KEYS.put("饮食", java.util.Arrays.asList(
            "喜欢的食物", "讨厌的食物", "过敏", "咖啡还是茶",
            "酒量", "甜食偏好", "咸食偏好", "辛辣接受度",
            "零食习惯", "挑食程度", "食量", "做饭能力",
            "最常吃的外卖", "早餐习惯"
        ));
        PRESET_KEYS.put("健康", java.util.Arrays.asList(
            "身体状况", "心理状态", "睡眠质量", "运动习惯",
            "容易感冒吗", "慢性问题", "晕车晕船", "怕冷还是怕热",
            "常用药", "经期状态"
        ));
        PRESET_KEYS.put("防线", java.util.Arrays.asList(
            "恐惧", "绝对不能提的雷区", "软肋", "安全区",
            "讨厌的行为", "压力来源", "情绪触发点",
            "生气时的表现", "伤心时的表现", "开心时的表现",
            "累的时候的表现", "需要被哄的方式",
            "她希望我什么时候出现", "我做什么会让她安心"
        ));
        PRESET_KEYS.put("人际", java.util.Arrays.asList(
            "家人", "最好的朋友", "社交状态",
            "最近的社交事件", "对社交的态度", "和她家人的关系",
            "和她朋友的关系", "和同事的关系", "常联系的人",
            "想联系但没联系的人"
        ));
        PRESET_KEYS.put("目标", java.util.Arrays.asList(
            "短期愿望", "长期目标", "最近在想的事",
            "正在努力的事", "焦虑的事", "期待的事",
            "想学的东西", "想去的地方", "想买的东西"
        ));
        PRESET_KEYS.put("日常", java.util.Arrays.asList(
            "职业状态", "上下班方式", "通勤时间",
            "空闲时做什么", "周末习惯", "喜欢的娱乐方式",
            "常用的App", "手机使用习惯", "游戏偏好",
            "追剧偏好", "阅读偏好", "宠物情况"
        ));
    }

    private MemoryPresetKeys() {}

    public static Map<String, List<String>> getPresetKeys() {
        return PRESET_KEYS;
    }

    public static boolean isPresetKeyItem(String category, String keyItem) {
        List<String> keys = PRESET_KEYS.get(category);
        return keys != null && keys.contains(keyItem);
    }
}
