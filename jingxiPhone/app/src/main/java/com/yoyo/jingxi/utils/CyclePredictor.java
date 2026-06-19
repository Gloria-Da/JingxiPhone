package com.yoyo.jingxi.utils;

import com.yoyo.jingxi.data.entity.CycleRecord;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 经期预测算法。
 * 使用加权平均 + 标准差窗口的统计方法，基于历史记录预测下次经期和排卵期。
 */
public class CyclePredictor {

    /**
     * 预测结果
     */
    public static class CyclePrediction {
        public String earliestStart;    // 预测经期最早开始 yyyy-MM-dd
        public String latestStart;      // 预测经期最晚开始
        public String earliestEnd;      // 预测经期最早结束
        public String latestEnd;        // 预测经期最晚结束
        public String ovulationStart;   // 排卵窗口开始
        public String ovulationEnd;     // 排卵窗口结束
        public double avgCycleLength;   // 加权平均周期天数
        public double avgPeriodLength;  // 加权平均经期天数
        public String confidence;       // 置信度: 高/中/低/null

        public boolean isValid() {
            return earliestStart != null;
        }
    }

    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    /**
     * 基于历史经期记录预测下次经期和排卵期。
     *
     * @param records 历史经期记录列表（按 startDate 降序排列）
     * @return 预测结果，记录不足2条时返回 null
     */
    public static CyclePrediction predict(List<CycleRecord> records) {
        if (records == null || records.size() < 2) return null;

        // 1. 按 startDate 升序排列
        List<CycleRecord> sorted = new ArrayList<>(records);
        sorted.sort((a, b) -> {
            if (a.startDate == null || b.startDate == null) return 0;
            return a.startDate.compareTo(b.startDate);
        });

        // 2. 计算相邻记录的周期天数和经期天数
        List<Integer> cycleGaps = new ArrayList<>();
        List<Integer> periodLengths = new ArrayList<>();

        for (int i = 1; i < sorted.size(); i++) {
            try {
                Date prevStart = SDF.parse(sorted.get(i - 1).startDate);
                Date currStart = SDF.parse(sorted.get(i).startDate);
                long diffMs = currStart.getTime() - prevStart.getTime();
                int gapDays = (int) (diffMs / (1000 * 60 * 60 * 24));

                // 3. 过滤异常值
                if (gapDays >= 15 && gapDays <= 60) {
                    cycleGaps.add(gapDays);
                }

                // 经期持续天数
                if (sorted.get(i).endDate != null) {
                    Date endDate = SDF.parse(sorted.get(i).endDate);
                    long periodMs = endDate.getTime() - currStart.getTime();
                    int pDays = (int) (periodMs / (1000 * 60 * 60 * 24)) + 1;
                    if (pDays >= 2 && pDays <= 10) {
                        periodLengths.add(pDays);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (cycleGaps.isEmpty()) return null;

        // 4. 加权平均周期（越近权重越大）
        double weightedAvgCycle = weightedAverage(cycleGaps);
        double cycleStdDev = stdDev(cycleGaps, weightedAvgCycle);

        // 5. 去除离群值后重新计算（排除 >2σ 的离群值）
        List<Integer> filteredGaps = new ArrayList<>();
        for (int gap : cycleGaps) {
            if (Math.abs(gap - weightedAvgCycle) <= 2 * cycleStdDev) {
                filteredGaps.add(gap);
            }
        }
        if (filteredGaps.size() >= 2) {
            weightedAvgCycle = weightedAverage(filteredGaps);
            cycleStdDev = stdDev(filteredGaps, weightedAvgCycle);
        }

        // 6. 加权平均经期天数
        double weightedAvgPeriod = periodLengths.isEmpty() ? 5.0 : weightedAverage(periodLengths);

        // 7. 预测
        try {
            Date lastStart = SDF.parse(sorted.get(sorted.size() - 1).startDate);
            Calendar cal = Calendar.getInstance();

            // Predicted start window
            int avgCycle = (int) Math.round(weightedAvgCycle);
            int sigma = (int) Math.round(cycleStdDev);

            cal.setTime(lastStart);
            cal.add(Calendar.DAY_OF_MONTH, avgCycle - sigma);
            String earliestStart = SDF.format(cal.getTime());

            cal.setTime(lastStart);
            cal.add(Calendar.DAY_OF_MONTH, avgCycle + sigma);
            String latestStart = SDF.format(cal.getTime());

            // Predicted end window
            int avgPeriod = (int) Math.round(weightedAvgPeriod);
            Calendar endCal = Calendar.getInstance();
            endCal.setTime(SDF.parse(earliestStart));
            endCal.add(Calendar.DAY_OF_MONTH, avgPeriod - 1);
            String earliestEnd = SDF.format(endCal.getTime());

            endCal.setTime(SDF.parse(latestStart));
            endCal.add(Calendar.DAY_OF_MONTH, avgPeriod - 1);
            String latestEnd = SDF.format(endCal.getTime());

            // 8. Ovulation window (14 days before period, with window)
            cal.setTime(SDF.parse(earliestStart));
            cal.add(Calendar.DAY_OF_MONTH, -16);
            String ovulationStart = SDF.format(cal.getTime());

            cal.setTime(SDF.parse(earliestStart));
            cal.add(Calendar.DAY_OF_MONTH, -10);
            String ovulationEnd = SDF.format(cal.getTime());

            // 9. Confidence
            String confidence;
            if (records.size() >= 6) confidence = "高";
            else if (records.size() >= 3) confidence = "中";
            else confidence = "低";

            CyclePrediction prediction = new CyclePrediction();
            prediction.earliestStart = earliestStart;
            prediction.latestStart = latestStart;
            prediction.earliestEnd = earliestEnd;
            prediction.latestEnd = latestEnd;
            prediction.ovulationStart = ovulationStart;
            prediction.ovulationEnd = ovulationEnd;
            prediction.avgCycleLength = weightedAvgCycle;
            prediction.avgPeriodLength = weightedAvgPeriod;
            prediction.confidence = confidence;

            return prediction;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 加权平均：距离现在越近的数据权重越大。
     * weight[i] = i+1（第1个权重1，第二个权重2，…）
     */
    private static double weightedAverage(List<Integer> values) {
        if (values.isEmpty()) return 0;
        double sum = 0;
        double weightSum = 0;
        for (int i = 0; i < values.size(); i++) {
            double weight = i + 1;
            sum += values.get(i) * weight;
            weightSum += weight;
        }
        return sum / weightSum;
    }

    /**
     * 标准差
     */
    private static double stdDev(List<Integer> values, double mean) {
        if (values.size() < 2) return 0;
        double sumSq = 0;
        for (int v : values) {
            sumSq += Math.pow(v - mean, 2);
        }
        return Math.sqrt(sumSq / (values.size() - 1)); // 样本标准差
    }
}
