package com.yoyo.jingxi.data.entity;

import java.util.List;

public class DailySchedule {
    public String date; // 某年某月某日 星期几
    public String weather; // 天气
    public String overallPlan; // 对这一天的总体规划
    public List<ScheduleItem> items;
    public List<PlannedMoment> plannedMoments;

    public static class PlannedMoment {
        public String scheduled_time; // 计划发布的时间，如 "15:30"
        public String content; // 朋友圈内容
        public String image_desc; // 配图描述，如果没有则为空
    }

    public static class ScheduleItem {
        public String time; // 几点几分 (例如 "08:30")
        public String action; // 做什么
        public boolean completed; // 是否完成 (√ 还是 ×)
        public String feeling; // 感受或原因
        public boolean isRandomEvent; // 是否是随机事件
    }
}
