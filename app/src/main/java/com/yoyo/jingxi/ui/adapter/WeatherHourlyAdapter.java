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

public class WeatherHourlyAdapter extends RecyclerView.Adapter<WeatherHourlyAdapter.ViewHolder> {
    private List<HourlyData> data = new ArrayList<>();

    public void setQWeatherData(List<QWeatherApi.QWeatherHourlyResponse.Hourly> hourlyList) {
        data.clear();
        if (hourlyList != null) {
            SimpleDateFormat sdfIn = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault());
            int i = 0;
            for (QWeatherApi.QWeatherHourlyResponse.Hourly h : hourlyList) {
                if (i >= 24) break; // 最多取24小时
                HourlyData item = new HourlyData();
                if (i == 0) {
                    item.time = "现在";
                } else {
                    try {
                        Date date = sdfIn.parse(h.fxTime);
                        if (date != null) {
                            Calendar cal = Calendar.getInstance();
                            cal.setTime(date);
                            item.time = String.format(Locale.getDefault(), "%02d:00", cal.get(Calendar.HOUR_OF_DAY));
                        } else {
                            item.time = "--:--";
                        }
                    } catch (Exception e) {
                        item.time = "--:--";
                    }
                }
                item.temp = h.temp + "°";
                item.iconResId = com.yoyo.jingxi.utils.WeatherIconUtils.getQWeatherIcon(h.icon);
                data.add(item);
                i++;
            }
        }
        notifyDataSetChanged();
    }

    public void setData(List<String> times, List<Double> temps, List<Integer> weatherCodes, boolean isAmap, String amapTemp, String amapWeather) {
        data.clear();
        if (isAmap && times == null) {
            // 如果是高德只显示当前温度，没法显示24小时预测，对于和风走 setQWeatherData 不走这里
            HourlyData current = new HourlyData();
            current.time = "现在";
            current.temp = amapTemp + "°";
            current.iconResId = com.yoyo.jingxi.utils.WeatherIconUtils.getAmapIcon(amapWeather);
            data.add(current);
            notifyDataSetDataSetChanged();
            return;
        }

        if (times != null && temps != null && weatherCodes != null) {
            // 获取当前时间
            Calendar calendar = Calendar.getInstance();
            int currentHour = calendar.get(Calendar.HOUR_OF_DAY);
            
            // OpenMeteo 会返回过去几天到未来几天的所有小时数据
            // 我们只取从当前小时开始的24小时数据
            int startIndex = -1;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault());
            for (int i = 0; i < times.size(); i++) {
                try {
                    Date date = sdf.parse(times.get(i));
                    if (date != null) {
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(date);
                        if (cal.get(Calendar.DAY_OF_YEAR) == calendar.get(Calendar.DAY_OF_YEAR) &&
                            cal.get(Calendar.HOUR_OF_DAY) == currentHour) {
                            startIndex = i;
                            break;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (startIndex != -1) {
                int count = Math.min(24, times.size() - startIndex);
                for (int i = 0; i < count; i++) {
                    int index = startIndex + i;
                    HourlyData item = new HourlyData();
                    if (i == 0) {
                        item.time = "现在";
                    } else {
                        try {
                            Date date = sdf.parse(times.get(index));
                            if (date != null) {
                                Calendar cal = Calendar.getInstance();
                                cal.setTime(date);
                                item.time = String.format(Locale.getDefault(), "%02d:00", cal.get(Calendar.HOUR_OF_DAY));
                            }
                        } catch (Exception e) {
                            item.time = "--:--";
                        }
                    }
                    item.temp = Math.round(temps.get(index)) + "°";
                    item.iconResId = com.yoyo.jingxi.utils.WeatherIconUtils.getOpenMeteoIcon(weatherCodes.get(index));
                    data.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    private void notifyDataSetDataSetChanged() {
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_weather_hourly, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HourlyData item = data.get(position);
        holder.tvTime.setText(item.time);
        holder.tvTemp.setText(item.temp);
        holder.ivWeatherIcon.setImageResource(item.iconResId);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTime;
        ImageView ivWeatherIcon;
        TextView tvTemp;

        ViewHolder(View view) {
            super(view);
            tvTime = view.findViewById(R.id.tvTime);
            ivWeatherIcon = view.findViewById(R.id.ivWeatherIcon);
            tvTemp = view.findViewById(R.id.tvTemp);
        }
    }

    private static class HourlyData {
        String time;
        String temp;
        int iconResId;
    }
}
