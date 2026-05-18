package com.yoyo.jingxi.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.yoyo.jingxi.R;
import com.yoyo.jingxi.network.QWeatherApi;
import java.util.ArrayList;
import java.util.List;
import java.util.Calendar;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class WeatherDailyAdapter extends RecyclerView.Adapter<WeatherDailyAdapter.ViewHolder> {
    private List<DailyData> data = new ArrayList<>();

    public void setQWeatherData(List<QWeatherApi.QWeatherDailyResponse.Daily> dailyList) {
        data.clear();
        if (dailyList != null) {
            Calendar calendar = Calendar.getInstance();
            int today = calendar.get(Calendar.DAY_OF_YEAR);
            
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat outSdf = new SimpleDateFormat("MM月dd日 EEE", Locale.CHINESE);
            
            for (QWeatherApi.QWeatherDailyResponse.Daily daily : dailyList) {
                DailyData item = new DailyData();
                try {
                    Date date = sdf.parse(daily.fxDate);
                    if (date != null) {
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(date);
                        
                        String prefix = "";
                        if (cal.get(Calendar.DAY_OF_YEAR) == today) {
                            prefix = "今天 ";
                        } else if (cal.get(Calendar.DAY_OF_YEAR) == today - 1) {
                            prefix = "昨天 ";
                        } else if (cal.get(Calendar.DAY_OF_YEAR) == today + 1) {
                            prefix = "明天 ";
                        }
                        
                        item.date = prefix + outSdf.format(date);
                    }
                } catch (Exception e) {
                    item.date = daily.fxDate;
                }
                
                item.tempRange = daily.tempMin + "° / " + daily.tempMax + "°";
                item.iconResId = com.yoyo.jingxi.utils.WeatherIconUtils.getQWeatherIcon(daily.iconDay);
                data.add(item);
            }
        }
        notifyDataSetChanged();
    }

    public void setData(List<String> times, List<Double> maxTemps, List<Double> minTemps, List<Integer> weatherCodes, 
                        boolean isAmap, List<com.yoyo.jingxi.network.AmapWeatherApi.AmapForecastResponse.Casts> amapCasts) {
        data.clear();
        
        if (isAmap && amapCasts != null) {
            // 高德天气预报数据
            Calendar calendar = Calendar.getInstance();
            int today = calendar.get(Calendar.DAY_OF_YEAR);
            
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat outSdf = new SimpleDateFormat("MM月dd日 EEE", Locale.CHINESE);
            
            for (com.yoyo.jingxi.network.AmapWeatherApi.AmapForecastResponse.Casts cast : amapCasts) {
                DailyData item = new DailyData();
                try {
                    Date date = sdf.parse(cast.date);
                    if (date != null) {
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(date);
                        
                        String prefix = "";
                        if (cal.get(Calendar.DAY_OF_YEAR) == today) {
                            prefix = "今天 ";
                        } else if (cal.get(Calendar.DAY_OF_YEAR) == today - 1) {
                            prefix = "昨天 ";
                        } else if (cal.get(Calendar.DAY_OF_YEAR) == today + 1) {
                            prefix = "明天 ";
                        }
                        
                        item.date = prefix + outSdf.format(date);
                    }
                } catch (Exception e) {
                    item.date = cast.date;
                }
                
                item.tempRange = cast.nighttemp + "° / " + cast.daytemp + "°";
                item.iconResId = com.yoyo.jingxi.utils.WeatherIconUtils.getAmapIcon(cast.dayweather);
                data.add(item);
            }
            notifyDataSetChanged();
            return;
        }

        if (times != null && maxTemps != null && minTemps != null) {
            // OpenMeteo 数据
            Calendar calendar = Calendar.getInstance();
            int today = calendar.get(Calendar.DAY_OF_YEAR);
            
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat outSdf = new SimpleDateFormat("MM月dd日 EEE", Locale.CHINESE);

            int count = Math.min(times.size(), maxTemps.size());
            count = Math.min(count, minTemps.size());

            for (int i = 0; i < count; i++) {
                DailyData item = new DailyData();
                try {
                    Date date = sdf.parse(times.get(i));
                    if (date != null) {
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(date);
                        
                        String prefix = "";
                        if (cal.get(Calendar.DAY_OF_YEAR) == today) {
                            prefix = "今天 ";
                        } else if (cal.get(Calendar.DAY_OF_YEAR) == today - 1) {
                            prefix = "昨天 ";
                        } else if (cal.get(Calendar.DAY_OF_YEAR) == today + 1) {
                            prefix = "明天 ";
                        }
                        
                        item.date = prefix + outSdf.format(date);
                    }
                } catch (Exception e) {
                    item.date = times.get(i);
                }

                item.tempRange = Math.round(minTemps.get(i)) + "° / " + Math.round(maxTemps.get(i)) + "°";
                if (weatherCodes != null && i < weatherCodes.size()) {
                    item.iconResId = com.yoyo.jingxi.utils.WeatherIconUtils.getOpenMeteoIcon(weatherCodes.get(i));
                } else {
                    item.iconResId = R.drawable.ic_weather_cloudy;
                }
                data.add(item);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_weather_daily, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DailyData item = data.get(position);
        holder.tvDate.setText(item.date);
        holder.tvTempRange.setText(item.tempRange);
        
        holder.ivWeatherIcon.setImageResource(item.iconResId);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate;
        ImageView ivWeatherIcon;
        TextView tvTempRange;

        ViewHolder(View view) {
            super(view);
            tvDate = view.findViewById(R.id.tvDate);
            ivWeatherIcon = view.findViewById(R.id.ivWeatherIcon);
            tvTempRange = view.findViewById(R.id.tvTempRange);
        }
    }

    private String getWeatherDesc(int code) {
        if (code == -1) return "未知";
        if (code == 0) return "晴";
        if (code >= 1 && code <= 3) return "多云";
        if (code >= 45 && code <= 48) return "雾";
        if (code >= 51 && code <= 55) return "毛毛雨";
        if (code >= 61 && code <= 65) return "雨";
        if (code >= 71 && code <= 75) return "雪";
        if (code >= 80 && code <= 82) return "阵雨";
        if (code >= 95 && code <= 99) return "雷暴";
        return "未知";
    }

    private static class DailyData {
        String date;
        String tempRange;
        int iconResId;
    }
}
