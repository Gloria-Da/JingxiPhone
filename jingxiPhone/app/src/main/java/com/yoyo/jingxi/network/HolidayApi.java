package com.yoyo.jingxi.network;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

/**
 * 中国节假日 API 接口。
 * 数据来源: NateScarlet/holiday-cn (GitHub 开源)
 * URL: https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/{year}.json
 * 完全免费，无需 API Key。
 */
public interface HolidayApi {

    @GET("NateScarlet/holiday-cn/master/{year}.json")
    Call<HolidayResponse> getHolidays(@Path("year") int year);

    /**
     * API 响应结构:
     * { "year": 2026, "days": [
     *     { "name": "元旦", "date": "2026-01-01", "isOffDay": true },
     *     { "name": "元旦", "date": "2026-01-04", "isOffDay": false },
     *     ...
     * ]}
     */
    class HolidayResponse {
        public int year;
        public List<HolidayDayItem> days;
    }

    class HolidayDayItem {
        public String name;      // 节日名称，如 "元旦"
        public String date;      // 日期 "yyyy-MM-dd"
        public boolean isOffDay; // true=休息日, false=调休补班日
    }
}
