package com.yoyo.jingxi.utils;

import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.CalendarEvent;
import com.yoyo.jingxi.data.entity.CourseEntry;
import com.yoyo.jingxi.data.entity.CycleRecord;
import com.yoyo.jingxi.data.entity.HolidayCache;
import com.yoyo.jingxi.data.entity.SemesterConfig;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 构建注入 AI 聊天的用户个人数据上下文。
 * 收集日历事件、课程表、经期状态、节假日信息，
 * 经 UserContextSettings 三维权限检查后格式化为精简文本。
 * 支持根据用户消息动态扩窗（如"下周有什么课"→扩展查询范围）。
 */
public class UserContextBuilder {

    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private static final SimpleDateFormat SHORT_FMT = new SimpleDateFormat("M/d", Locale.US);
    private static final int MAX_TOTAL_LEN = 1000;

    // ---- 用户意图解析结果 ----

    static class DateIntent {
        String dateFrom;          // yyyy-MM-dd, null=默认窗口
        String dateTo;            // yyyy-MM-dd, null=默认窗口
        boolean expandCalendar;   // 是否需要扩展日历
        boolean expandCourse;     // 是否需要扩展课程
        boolean expandPeriod;     // 是否需要扩展经期历史
        boolean needIds;          // 是否需要带事件/课程 ID（删/改时）
        boolean semesterSummary;  // "这学期有什么课" → 按星期汇总
        int targetWeek;           // "第N周" → 指定周号，0=未指定
        String explicitDate;      // "X月X日" → 具体日期 yyyy-MM-dd
    }

    // ---- 公共入口 ----

    /**
     * 构建用户上下文文本。根据用户消息动态调整查询范围。
     *
     * @param db           AppDatabase 实例
     * @param characterId  当前聊天的 AI 角色 ID
     * @param personaName  当前使用的人设名（可为 null）
     * @param userMessage  用户最新消息内容（可为 null，此时用默认窗口）
     */
    public static String buildUserContext(AppDatabase db, int characterId,
                                           String personaName, String userMessage) {
        UserContextSettings settings = UserContextSettings.load();
        String today = SDF.format(new Date());

        // 解析用户意图
        DateIntent intent = parseDateIntent(userMessage, today);

        // 确定日历查询范围
        String calFrom = intent.dateFrom != null ? intent.dateFrom : today;
        String calTo = intent.dateTo != null ? intent.dateTo : addDays(today, 3);
        if (intent.expandCalendar && intent.dateFrom == null) {
            // 情境扩窗（如"最近好忙"）：扩展到本周日
            calTo = getWeekEnd(today);
        }

        // 收集各子上下文
        List<Block> blocks = new ArrayList<>();

        if (settings.isEnabled("calendar", characterId, personaName)) {
            String ctx = buildCalendarContext(db, calFrom, calTo, today, intent.needIds);
            if (!ctx.isEmpty()) blocks.add(new Block(ctx, 1));
        }

        if (settings.isEnabled("course", characterId, personaName)) {
            String ctx;
            if (intent.semesterSummary) {
                ctx = buildCourseSummary(db);
            } else if (intent.targetWeek > 0) {
                ctx = buildCourseContextForWeek(db, intent.targetWeek, today, intent.needIds);
            } else if (intent.expandCourse && intent.dateFrom == null) {
                // 情境扩窗：本周课程
                ctx = buildCourseContext(db, today, getWeekEnd(today), today, intent.needIds);
            } else {
                String courseTo = intent.dateTo != null ? intent.dateTo : addDays(today, 1);
                ctx = buildCourseContext(db, today, courseTo, today, intent.needIds);
            }
            if (!ctx.isEmpty()) blocks.add(new Block(ctx, 2));
        }

        if (settings.isEnabled("period", characterId, personaName)) {
            String ctx = buildPeriodContext(db, today, intent.expandPeriod);
            if (!ctx.isEmpty()) blocks.add(new Block(ctx, 0));
        }

        // 节假日无隐私顾虑，始终注入
        String holidayCtx = buildHolidayContext(db, today);
        if (!holidayCtx.isEmpty()) blocks.add(new Block(holidayCtx, 3));

        if (blocks.isEmpty()) return "";

        // 组装
        StringBuilder sb = new StringBuilder();
        sb.append("【关于用户今天的真实背景】\n");
        for (Block b : blocks) {
            sb.append(b.text).append("\n");
        }
        sb.append("提示：以上是用户的真实日程和状态。对话中自然涉及时可随口关心，");
        sb.append("但不要生硬汇报或说教。");

        // 总量截断
        String result = sb.toString();
        if (result.length() > MAX_TOTAL_LEN) {
            result = truncateByPriority(result, blocks, MAX_TOTAL_LEN);
        }
        return result;
    }

    // ---- 用户意图解析 ----

    /**
     * 解析用户消息中的日期引用、数据类别和操作意图。
     * 纯本地解析，不调用 AI。
     */
    static DateIntent parseDateIntent(String message, String today) {
        DateIntent intent = new DateIntent();
        if (message == null || message.trim().isEmpty()) return intent;

        String msg = message.trim();

        // === 日期引用检测 ===

        // "下周" / "下星期"
        if (matches(msg, "下周|下星期|下个星期")) {
            intent.dateFrom = getNextMonday(today);
            intent.dateTo = addDays(intent.dateFrom, 6);
            intent.expandCalendar = true;
            intent.expandCourse = true;
        }
        // "这周" / "这个星期" / "本周"
        else if (matches(msg, "这周|这个星期|本周")) {
            intent.dateFrom = getThisMonday(today);
            intent.dateTo = getWeekEnd(today);
            intent.expandCalendar = true;
            intent.expandCourse = true;
        }
        // "下周一/二/三/四/五/六/日"
        else if (matches(msg, "下周[一二三四五六日天]")) {
            String dayStr = extractFirst(msg, "下周([一二三四五六日天])");
            if (dayStr != null) {
                String date = getNextWeekDay(today, dayStr);
                intent.dateFrom = addDays(date, -1);
                intent.dateTo = addDays(date, 1);
                intent.expandCalendar = true;
                intent.explicitDate = date;
            }
        }
        // "这学期" + "课/课程"
        else if (matches(msg, "这学期") && matches(msg, "课|课程")) {
            intent.semesterSummary = true;
            intent.expandCourse = true;
        }
        // "第N周"（"第5周"、"第12周"等）
        else if (matches(msg, "第\\d+周")) {
            String numStr = extractFirst(msg, "第(\\d+)周");
            if (numStr != null) {
                try {
                    intent.targetWeek = Integer.parseInt(numStr);
                    intent.expandCourse = true;
                } catch (NumberFormatException ignored) {}
            }
        }
        // "X月X日" / "X号"
        else if (matches(msg, "\\d{1,2}月\\d{1,2}日|\\d{1,2}月\\d{1,2}号")) {
            String date = parseMonthDay(msg, today);
            if (date != null) {
                intent.dateFrom = addDays(date, -2);
                intent.dateTo = addDays(date, 2);
                intent.expandCalendar = true;
                intent.explicitDate = date;
            }
        }
        // "昨天" / "前天"
        else if (matches(msg, "昨天")) {
            intent.dateFrom = addDays(today, -1);
            intent.dateTo = addDays(today, 1);
            intent.expandCalendar = true;
        }
        else if (matches(msg, "前天")) {
            intent.dateFrom = addDays(today, -2);
            intent.dateTo = today;
            intent.expandCalendar = true;
        }

        // === 类别关键词 ===
        if (matches(msg, "课|课程|上课|下课|课表")) {
            intent.expandCourse = true;
        }
        if (matches(msg, "经期|例假|姨妈|月经|生理期")) {
            intent.expandPeriod = true;
        }

        // === 操作意图（删/改需要 ID） ===
        if (matches(msg, "删|取消|去掉|移除|撤销")) {
            intent.needIds = true;
            intent.expandCalendar = true; // 需要看到才能删
        }
        if (matches(msg, "改|修改|更换|换成")) {
            intent.needIds = true;
            intent.expandCalendar = true;
        }

        // === 情境关键词（无日期引用时自动扩到本周） ===
        if (intent.dateFrom == null && intent.dateTo == null
            && !intent.semesterSummary && intent.targetWeek == 0) {
            if (matches(msg, "忙|好忙|累|好累|最近|这段时间|这几天|压力|烦|忙死")) {
                intent.dateFrom = today;
                intent.dateTo = getWeekEnd(today);
                intent.expandCalendar = true;
                intent.expandCourse = true;
            }
        }

        return intent;
    }

    private static boolean matches(String msg, String regex) {
        return Pattern.compile(regex).matcher(msg).find();
    }

    private static String extractFirst(String msg, String regex) {
        Matcher m = Pattern.compile(regex).matcher(msg);
        return m.find() ? m.group(1) : null;
    }

    // ---- 日历事件上下文（动态窗口） ----

    static String buildCalendarContext(AppDatabase db, String dateFrom, String dateTo,
                                        String today, boolean needIds) {
        try {
            List<CalendarEvent> events = db.calendarEventDao().getEventsInRangeSync(dateFrom, dateTo);
            if (events == null || events.isEmpty()) return "";

            // 按日期分组
            java.util.Map<String, List<CalendarEvent>> byDay = new java.util.LinkedHashMap<>();
            for (CalendarEvent e : events) {
                if (e.eventDate == null) continue;
                byDay.computeIfAbsent(e.eventDate, k -> new ArrayList<>()).add(e);
            }

            // 排序每组
            Comparator<CalendarEvent> cmp = (a, b) -> {
                if (a.allDay != b.allDay) return a.allDay ? -1 : 1;
                return Long.compare(a.startTime, b.startTime);
            };

            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (String date : byDay.keySet()) {
                List<CalendarEvent> dayEvents = byDay.get(date);
                dayEvents.sort(cmp);

                if (!first) sb.append("\n");
                first = false;

                boolean isToday = date.equals(today);
                boolean isTargetDay = date.equals(dateFrom) || date.equals(dateTo)
                    || (Math.abs(daysBetween(today, date)) <= 1);

                String label;
                if (isToday) label = "今天";
                else if (date.equals(addDays(today, 1))) label = "明天";
                else if (date.equals(addDays(today, 2))) label = "后天";
                else if (date.equals(addDays(today, -1))) label = "昨天";
                else label = formatDateShort(date);

                sb.append(label).append("：");
                int maxPerDay = isTargetDay ? 8 : 3;
                sb.append(formatEventList(dayEvents, maxPerDay, !isTargetDay, needIds));
            }
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private static String formatEventList(List<CalendarEvent> events, int max,
                                           boolean compact, boolean needIds) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.US);

        for (CalendarEvent e : events) {
            if (count >= max) {
                int remaining = events.size() - count;
                if (remaining > 0) sb.append(" 等").append(remaining).append("项");
                break;
            }
            if (count > 0) sb.append(" / ");
            if (e.allDay) {
                sb.append("全天 ").append(e.title);
            } else if (!compact && e.startTime > 0) {
                sb.append(timeFmt.format(new Date(e.startTime))).append(" ").append(e.title);
            } else {
                sb.append(e.title);
            }
            if (needIds) sb.append("[#").append(e.id).append("]");
            count++;
        }
        return sb.toString();
    }

    // ---- 课程上下文（动态窗口） ----

    static String buildCourseContext(AppDatabase db, String dateFrom, String dateTo,
                                      String today, boolean needIds) {
        try {
            SemesterConfig sem = db.semesterConfigDao().getActive();
            if (sem == null || sem.startDate == null) return "";

            int currentWeek = getWeekNumber(sem, today);
            if (currentWeek < 1) return "";

            Calendar cal = Calendar.getInstance();
            cal.setTime(SDF.parse(dateFrom));
            Calendar calEnd = Calendar.getInstance();
            calEnd.setTime(SDF.parse(dateTo));

            String[] dowNames = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
            StringBuilder sb = new StringBuilder();

            while (!cal.after(calEnd)) {
                int dow = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7;
                String dateStr = SDF.format(cal.getTime());
                int weekForThisDay = getWeekNumber(sem, dateStr);

                if (dow < 5 && weekForThisDay >= 1 && weekForThisDay <= sem.totalWeeks) {
                    List<CourseEntry> courses = db.courseEntryDao().getByDayOfWeek(sem.id, dow);
                    List<CourseEntry> active = new ArrayList<>();
                    if (courses != null) {
                        for (CourseEntry ce : courses) {
                            if (isActiveWeek(ce.weekPattern, weekForThisDay)) active.add(ce);
                        }
                    }

                    if (!active.isEmpty()) {
                        if (sb.length() > 0) sb.append("\n");
                        String label;
                        if (dateStr.equals(today)) label = "今天";
                        else if (dateStr.equals(addDays(today, 1))) label = "明天";
                        else label = formatDateShort(dateStr) + " " + dowNames[dow];
                        sb.append(label).append("：");
                        sb.append(formatCourseList(active, sem, 6, needIds));
                    }
                }
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }

            // Weekend fallback
            if (sb.length() == 0) {
                Calendar todayCal = Calendar.getInstance();
                todayCal.setTime(SDF.parse(today));
                int tdow = (todayCal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7;
                boolean isWeekend = (tdow == 5 || tdow == 6);
                if (isWeekend && daysBetween(dateFrom, today) < 7) {
                    sb.append("周末，无课程安排");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 查询指定周的课程。
     */
    static String buildCourseContextForWeek(AppDatabase db, int week, String today,
                                             boolean needIds) {
        try {
            SemesterConfig sem = db.semesterConfigDao().getActive();
            if (sem == null || sem.startDate == null) return "";
            if (week < 1 || week > sem.totalWeeks) return "";

            String[] dowNames = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
            StringBuilder sb = new StringBuilder();
            sb.append("第").append(week).append("周课程：\n");

            for (int dow = 0; dow < 5; dow++) {
                List<CourseEntry> courses = db.courseEntryDao().getByDayOfWeek(sem.id, dow);
                List<CourseEntry> active = new ArrayList<>();
                if (courses != null) {
                    for (CourseEntry ce : courses) {
                        if (isActiveWeek(ce.weekPattern, week)) active.add(ce);
                    }
                }
                if (!active.isEmpty()) {
                    sb.append(dowNames[dow]).append("：");
                    sb.append(formatCourseList(active, sem, 6, needIds));
                    sb.append("\n");
                }
            }
            return sb.toString().trim();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 按星期汇总整学期课程（去重，标注周模式）。
     */
    static String buildCourseSummary(AppDatabase db) {
        try {
            SemesterConfig sem = db.semesterConfigDao().getActive();
            if (sem == null) return "";

            List<CourseEntry> all = db.courseEntryDao().getAllBySemester(sem.id);
            if (all == null || all.isEmpty()) return "";

            // 按 dayOfWeek 分组
            java.util.Map<Integer, List<CourseEntry>> byDow = new java.util.LinkedHashMap<>();
            for (int d = 0; d < 7; d++) byDow.put(d, new ArrayList<>());
            for (CourseEntry ce : all) {
                List<CourseEntry> list = byDow.get(ce.dayOfWeek);
                if (list != null) list.add(ce);
            }

            String[] dowNames = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
            StringBuilder sb = new StringBuilder();
            sb.append("本学期课程汇总：\n");

            boolean hasAny = false;
            for (int d = 0; d < 5; d++) {
                List<CourseEntry> list = byDow.get(d);
                if (list.isEmpty()) continue;
                hasAny = true;
                sb.append(dowNames[d]).append("：");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append("、");
                    CourseEntry ce = list.get(i);
                    sb.append(ce.name);
                    // 标注周模式
                    String weekLabel = getWeekPatternLabel(ce.weekPattern);
                    if (!weekLabel.isEmpty()) sb.append("(").append(weekLabel).append(")");
                }
                sb.append("\n");
            }
            return hasAny ? sb.toString().trim() : "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 获取周模式的中文标签。
     */
    private static String getWeekPatternLabel(String pattern) {
        if (pattern == null || "EVERY".equals(pattern)) return "";
        if ("ODD".equals(pattern)) return "单周";
        if ("EVEN".equals(pattern)) return "双周";
        // custom: "1,3-5,8" → 如果长度合适直接展示，否则缩略
        if (pattern.length() <= 10) return pattern.replace(",", "、") + "周";
        return "部分周";
    }

    private static String formatCourseList(List<CourseEntry> courses, SemesterConfig sem,
                                            int max, boolean needIds) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (CourseEntry ce : courses) {
            if (count >= max) {
                int remaining = courses.size() - count;
                if (remaining > 0) sb.append(" 等").append(remaining).append("门");
                break;
            }
            if (count > 0) sb.append(" / ");
            String timeRange = getPeriodTimeRange(ce.startPeriod, ce.periodCount, sem);
            String location = (ce.location != null && !ce.location.isEmpty())
                ? "(" + ce.location + ")" : "";
            sb.append(timeRange).append(" ").append(ce.name).append(location);
            if (needIds) sb.append("[#").append(ce.id).append("]");
            count++;
        }
        return sb.toString();
    }

    // ---- 经期上下文 ----

    static String buildPeriodContext(AppDatabase db, String today, boolean showHistory) {
        try {
            List<CycleRecord> records = db.cycleRecordDao().getRecentRecords();
            if (records == null || records.size() < 2) return "";

            // 如果要求查看历史，列出最近的记录
            if (showHistory) {
                StringBuilder sb = new StringBuilder();
                sb.append("最近经期记录：");
                int count = 0;
                for (CycleRecord r : records) {
                    if (count >= 5) break;
                    if (count > 0) sb.append("、");
                    sb.append(formatDateShort(r.startDate));
                    if (r.endDate != null && !r.endDate.equals(r.startDate)) {
                        sb.append("-").append(formatDateShort(r.endDate));
                    }
                    count++;
                }
                return sb.toString();
            }

            // 检查今天是否在经期中
            CycleRecord todayRecord = db.cycleRecordDao().getPeriodOnDate(today);
            if (todayRecord != null) {
                try {
                    Date startDate = SDF.parse(todayRecord.startDate);
                    Date todayDate = SDF.parse(today);
                    long diffMs = todayDate.getTime() - startDate.getTime();
                    int dayNum = (int) (diffMs / (1000 * 60 * 60 * 24)) + 1;

                    StringBuilder sb = new StringBuilder();
                    sb.append("生理期第").append(dayNum).append("天");
                    String flow;
                    if (todayRecord.flowLevel == null) flow = "";
                    else if (todayRecord.flowLevel == 3) flow = "(流量多)";
                    else if (todayRecord.flowLevel == 1) flow = "(流量少)";
                    else flow = "(流量中)";
                    if (!flow.isEmpty()) sb.append(flow);
                    if (todayRecord.symptoms != null && !todayRecord.symptoms.isEmpty()
                        && !"none".equalsIgnoreCase(todayRecord.symptoms)) {
                        sb.append("，有").append(translateSymptoms(todayRecord.symptoms));
                    }
                    if (todayRecord.endDate != null && !todayRecord.endDate.equals(todayRecord.startDate)) {
                        Date endDate = SDF.parse(todayRecord.endDate);
                        long remainMs = endDate.getTime() - todayDate.getTime();
                        int remainDays = (int) (remainMs / (1000 * 60 * 60 * 24));
                        if (remainDays > 0) sb.append("。预计").append(remainDays).append("天后结束");
                        else if (remainDays == 0) sb.append("。预计今天结束");
                    }
                    return sb.toString();
                } catch (Exception e) { e.printStackTrace(); }
            }

            // 不在经期中，使用预测
            CyclePredictor.CyclePrediction prediction = CyclePredictor.predict(records);
            if (prediction == null || !prediction.isValid()) return "";
            return "预计下次经期 " + formatDateShort(prediction.earliestStart)
                + "-" + formatDateShort(prediction.latestStart)
                + "，周期约" + Math.round(prediction.avgCycleLength) + "天";

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    // ---- 节假日上下文 ----

    static String buildHolidayContext(AppDatabase db, String today) {
        try {
            String day3 = addDays(today, 3);
            List<HolidayCache> holidays = db.holidayCacheDao().getHolidaysInRange(today, day3);
            if (holidays == null || holidays.isEmpty()) return "";

            StringBuilder sb = new StringBuilder();
            for (HolidayCache h : holidays) {
                if (h.date == null || h.name == null) continue;
                String shortName = shortenHolidayName(h.name);
                String label = h.isOffDay ? "(休)" : "(补班)";
                if (h.date.equals(today)) {
                    sb.append("今天是").append(shortName).append(label);
                } else {
                    if (sb.length() > 0) sb.append("，");
                    try {
                        Date d = SDF.parse(h.date);
                        sb.append(SHORT_FMT.format(d)).append(" ").append(shortName).append(label);
                    } catch (Exception ignored) {}
                }
            }
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    // ---- 工具方法 ----

    static String addDays(String dateStr, int days) {
        try {
            Date d = SDF.parse(dateStr);
            Calendar cal = Calendar.getInstance();
            cal.setTime(d);
            cal.add(Calendar.DAY_OF_MONTH, days);
            return SDF.format(cal.getTime());
        } catch (Exception e) {
            return dateStr;
        }
    }

    private static int daysBetween(String d1, String d2) {
        try {
            long ms1 = SDF.parse(d1).getTime();
            long ms2 = SDF.parse(d2).getTime();
            return (int) ((ms2 - ms1) / (24L * 60 * 60 * 1000));
        } catch (Exception e) {
            return 0;
        }
    }

    private static String getThisMonday(String today) {
        Calendar cal = Calendar.getInstance();
        try { cal.setTime(SDF.parse(today)); } catch (Exception e) { return today; }
        int dow = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7;
        cal.add(Calendar.DAY_OF_MONTH, -dow);
        return SDF.format(cal.getTime());
    }

    private static String getWeekEnd(String today) {
        return addDays(getThisMonday(today), 6);
    }

    private static String getNextMonday(String today) {
        return addDays(getThisMonday(today), 7);
    }

    private static String getNextWeekDay(String today, String chineseDay) {
        String[] map = {"一", "二", "三", "四", "五", "六", "日"};
        int targetDow = -1;
        for (int i = 0; i < map.length; i++) {
            if (map[i].equals(chineseDay)) { targetDow = i; break; }
        }
        // "天" also means Sunday
        if ("天".equals(chineseDay)) targetDow = 6;
        if (targetDow < 0) return today;

        String nextMon = getNextMonday(today);
        return addDays(nextMon, targetDow);
    }

    /**
     * 解析 "X月X日" 或 "X号" 为 yyyy-MM-dd。
     */
    private static String parseMonthDay(String msg, String today) {
        try {
            Calendar cal = Calendar.getInstance();
            cal.setTime(SDF.parse(today));
            int currentYear = cal.get(Calendar.YEAR);

            // "X月X日"
            Matcher m = Pattern.compile("(\\d{1,2})月(\\d{1,2})[日号]").matcher(msg);
            if (m.find()) {
                int month = Integer.parseInt(m.group(1));
                int day = Integer.parseInt(m.group(2));
                cal.set(currentYear, month - 1, day);
                // If the date is more than 6 months in the past, assume next year
                Calendar todayCal = Calendar.getInstance();
                todayCal.setTime(SDF.parse(today));
                if (cal.before(todayCal) && todayCal.get(Calendar.MONTH) - (month - 1) > 6) {
                    cal.set(Calendar.YEAR, currentYear + 1);
                }
                return SDF.format(cal.getTime());
            }

            // "X号" (this month)
            m = Pattern.compile("(\\d{1,2})号").matcher(msg);
            if (m.find()) {
                int day = Integer.parseInt(m.group(1));
                cal.set(Calendar.DAY_OF_MONTH, day);
                return SDF.format(cal.getTime());
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    private static int getWeekNumber(SemesterConfig sem, String dateStr) {
        try {
            long start = SDF.parse(sem.startDate).getTime();
            long now = SDF.parse(dateStr).getTime();
            int w = (int) ((now - start) / (7L * 24 * 60 * 60 * 1000)) + 1;
            if (w < 1) w = 1;
            if (w > sem.totalWeeks) w = sem.totalWeeks;
            return w;
        } catch (Exception e) { return 1; }
    }

    private static String getPeriodTimeRange(int startPeriod, int periodCount, SemesterConfig sem) {
        try {
            if (sem.customPeriods != null && !sem.customPeriods.isEmpty()) {
                String trimmed = sem.customPeriods.trim();
                if (trimmed.startsWith("{")) {
                    com.google.gson.JsonObject obj = new com.google.gson.Gson().fromJson(trimmed, com.google.gson.JsonObject.class);
                    if (obj.has("periods") && obj.get("periods").isJsonArray()) {
                        com.google.gson.JsonArray arr = obj.getAsJsonArray("periods");
                        int idx = startPeriod - 1;
                        if (idx >= 0 && idx + periodCount - 1 < arr.size()) {
                            String start = arr.get(idx).getAsString();
                            String end = arr.get(idx + periodCount - 1).getAsString();
                            String startTime = start.contains("-") ? start.split("-")[0] : start;
                            String endTime = end.contains("-") ? end.split("-")[1] : end;
                            return startTime + "-" + endTime;
                        }
                    }
                } else {
                    String[] slots = trimmed.split(",");
                    int idx = startPeriod - 1;
                    if (idx >= 0 && idx + periodCount - 1 < slots.length) {
                        String startSlot = slots[idx].trim();
                        String endSlot = slots[idx + periodCount - 1].trim();
                        String startTime = startSlot.contains("-") ? startSlot.split("-")[0] : startSlot;
                        String endTime = endSlot.contains("-") ? endSlot.split("-")[1] : endSlot;
                        return startTime + "-" + endTime;
                    }
                }
            }
            String[] firstParts = sem.firstPeriodStart.split(":");
            int firstH = Integer.parseInt(firstParts[0]);
            int firstM = Integer.parseInt(firstParts[1]);
            int startMinutes = firstH * 60 + firstM + (startPeriod - 1) * (sem.periodDuration + sem.periodBreak);
            int endMinutes = startMinutes + sem.periodDuration * periodCount + (periodCount - 1) * sem.periodBreak;
            return String.format(Locale.US, "%02d:%02d-%02d:%02d",
                startMinutes / 60, startMinutes % 60, endMinutes / 60, endMinutes % 60);
        } catch (Exception e) {
            return "第" + startPeriod + "节";
        }
    }

    static boolean isActiveWeek(String pattern, int week) {
        if (pattern == null) return true;
        switch (pattern) {
            case "EVERY": return true;
            case "ODD": return week % 2 == 1;
            case "EVEN": return week % 2 == 0;
            default:
                try {
                    Set<Integer> set = new HashSet<>();
                    String[] parts = pattern.split(",");
                    for (String part : parts) {
                        part = part.trim();
                        if (part.contains("-")) {
                            String[] range = part.split("-");
                            int from = Integer.parseInt(range[0].trim());
                            int to = Integer.parseInt(range[1].trim());
                            for (int w = from; w <= to; w++) set.add(w);
                        } else set.add(Integer.parseInt(part));
                    }
                    return set.contains(week);
                } catch (Exception e) { return true; }
        }
    }

    private static String shortenHolidayName(String name) {
        if (name == null) return "";
        if (name.contains("元旦")) return "元旦";
        if (name.contains("春节")) return "春节";
        if (name.contains("清明")) return "清明节";
        if (name.contains("劳动")) return "劳动节";
        if (name.contains("端午")) return "端午节";
        if (name.contains("中秋")) return "中秋节";
        if (name.contains("国庆")) return "国庆节";
        return name.length() > 4 ? name.substring(0, 4) : name;
    }

    private static String translateSymptoms(String symptoms) {
        if (symptoms == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String s : symptoms.split(",")) {
            s = s.trim().toLowerCase();
            String cn;
            switch (s) {
                case "cramps": cn = "抽筋"; break;
                case "headache": cn = "头痛"; break;
                case "fatigue": cn = "疲劳"; break;
                case "bloating": cn = "腹胀"; break;
                case "mood": cn = "情绪波动"; break;
                case "other": cn = "其他不适"; break;
                default: cn = s; break;
            }
            if (!cn.isEmpty()) {
                if (sb.length() > 0) sb.append("、");
                sb.append(cn);
            }
        }
        return sb.toString();
    }

    private static String formatDateShort(String dateStr) {
        try {
            Date d = SDF.parse(dateStr);
            return SHORT_FMT.format(d);
        } catch (Exception e) { return dateStr; }
    }

    // ---- 截断逻辑 ----

    private static class Block {
        final String text;
        final int priority;
        Block(String text, int priority) { this.text = text; this.priority = priority; }
    }

    private static String truncateByPriority(String full, List<Block> blocks, int maxLen) {
        List<Block> sorted = new ArrayList<>(blocks);
        sorted.sort((a, b) -> Integer.compare(b.priority, a.priority));
        String result = full;
        for (Block b : sorted) {
            if (result.length() <= maxLen) break;
            if (b.priority == 0) continue;
            if (b.text.isEmpty()) continue;
            int halfLen = Math.max(20, b.text.length() / 2);
            String shortened = b.text.length() > halfLen + 2
                ? b.text.substring(0, halfLen) + "…" : b.text + "…";
            result = result.replace(b.text, shortened);
        }
        for (Block b : sorted) {
            if (result.length() <= maxLen) break;
            if (b.priority <= 1) continue;
            if (b.text.isEmpty()) continue;
            result = result.replace(b.text + "\n", "");
            result = result.replace(b.text, "");
        }
        return result;
    }
}
