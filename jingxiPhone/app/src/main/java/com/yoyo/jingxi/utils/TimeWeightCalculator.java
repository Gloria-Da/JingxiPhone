package com.yoyo.jingxi.utils;

/**
 * 艾宾浩斯遗忘曲线时间加权计算器。
 *
 * 核心原理：每次成功回忆，遗忘曲线变平坦。
 * λ(n) = λ_base / (1 + β × n)，且 λ(n) ≥ λ_min
 * retention = e^(-λ(n) × daysSinceLastRecall)
 *
 * 所有方法在 isEnabled()=false 时透传原值，零副作用。
 */
public class TimeWeightCalculator {

    // ==================== 配置常量 ====================

    /** 是否启用时间加权 */
    public static final String KEY_ENABLED = "MEMORY_TIME_WEIGHT_ENABLED";

    /** 初始衰减速率（episodic），默认 0.05 → 半衰期 ~14 天 */
    public static final String KEY_LAMBDA_BASE = "MEMORY_TIME_LAMBDA_BASE";

    /** 回忆平坦化因子，默认 0.5 */
    public static final String KEY_BETA = "MEMORY_TIME_BETA";

    /** 最低衰减速率，默认 0.001 → 半衰期 ~693 天 */
    public static final String KEY_LAMBDA_MIN = "MEMORY_TIME_LAMBDA_MIN";

    /** 衰减下限（retention 不会低于此值），默认 0.05 */
    public static final String KEY_RETENTION_FLOOR = "MEMORY_TIME_RETENTION_FLOOR";

    // ==================== 默认值 ====================

    private static final float DEFAULT_LAMBDA_BASE = 0.05f;
    private static final float DEFAULT_BETA = 0.5f;
    private static final float DEFAULT_LAMBDA_MIN = 0.001f;
    private static final float DEFAULT_RETENTION_FLOOR = 0.05f;

    private static final long MILLIS_PER_DAY = 86_400_000L;

    // ==================== 公共方法 ====================

    /**
     * 计算 episodic memory 的有效 retention。
     *
     * @param importanceLevel 基础重要度 1-5
     * @param createdAt       创建时间戳
     * @param lastRecalledAt  上次回忆时间戳（0=从未回忆）
     * @param recallCount     回忆次数
     * @param now             当前时间戳
     * @return 有效权重 = importanceLevel × retention
     */
    public static float computeEpisodicWeight(int importanceLevel, long createdAt,
                                               long lastRecalledAt, int recallCount, long now) {
        if (!isEnabled()) return importanceLevel;
        float retention = computeEpisodicRetention(createdAt, lastRecalledAt, recallCount, now);
        return importanceLevel * retention;
    }

    /**
     * 计算 episodic memory 的 retention（0~1）。
     */
    public static float computeEpisodicRetention(long createdAt, long lastRecalledAt,
                                                  int recallCount, long now) {
        if (!isEnabled()) return 1.0f;
        long referenceTime = (lastRecalledAt > 0) ? lastRecalledAt : createdAt;
        float lambda = computeDecayRate(recallCount);
        float days = Math.max(0, (now - referenceTime)) / (float) MILLIS_PER_DAY;
        float raw = (float) Math.exp(-lambda * days);
        float floor = SpUtils.getFloat(KEY_RETENTION_FLOOR, DEFAULT_RETENTION_FLOOR);
        return Math.max(floor, raw);
    }

    /**
     * 计算衰减速率 λ(n)。
     * λ(n) = λ_base / (1 + β × n)，且 λ(n) ≥ λ_min
     */
    public static float computeDecayRate(int recallCount) {
        float lambdaBase = SpUtils.getFloat(KEY_LAMBDA_BASE, DEFAULT_LAMBDA_BASE);
        float beta = SpUtils.getFloat(KEY_BETA, DEFAULT_BETA);
        float lambdaMin = SpUtils.getFloat(KEY_LAMBDA_MIN, DEFAULT_LAMBDA_MIN);
        float lambda = lambdaBase / (1.0f + beta * recallCount);
        return Math.max(lambdaMin, lambda);
    }

    /**
     * 计算 retention（通用，使用指定 lambda）。
     */
    public static float computeRetention(long timestamp, long now, float lambda, float floor) {
        if (!isEnabled()) return 1.0f;
        float days = Math.max(0, (now - timestamp)) / (float) MILLIS_PER_DAY;
        float raw = (float) Math.exp(-lambda * days);
        return Math.max(floor, raw);
    }

    // ==================== 配置读取 ====================

    public static boolean isEnabled() {
        return SpUtils.getBoolean(KEY_ENABLED, true);
    }

    /**
     * 获取半衰期描述文本（用于设置页显示）。
     */
    public static String getHalfLifeText(float lambda) {
        if (lambda <= 0) return "∞";
        float days = (float) (Math.log(2) / lambda);
        if (days < 1) return String.format("%.1f小时", days * 24);
        if (days < 30) return String.format("%.0f天", days);
        if (days < 365) return String.format("%.1f个月", days / 30);
        return String.format("%.1f年", days / 365);
    }
}
