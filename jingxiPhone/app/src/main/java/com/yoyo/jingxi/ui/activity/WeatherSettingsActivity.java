package com.yoyo.jingxi.ui.activity;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.yoyo.jingxi.JingxiApplication;
import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.Character;
import com.yoyo.jingxi.utils.SpUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WeatherSettingsActivity extends AppCompatActivity {

    private SwitchMaterial swEnableNoteGeneration;
    private RecyclerView rvCharacterMultiSelect;
    private TextView btnPickTime;
    private SwitchMaterial swEnableHourly;
    private SwitchMaterial swEnableGlobalWeather;

    private View llCharacterSection;
    private View llTimeSection;
    private View llHourlySection;
    private View llGlobalSection;

    private CharacterMultiSelectAdapter characterAdapter;
    private List<Character> allCharacters = new ArrayList<>();
    private Set<Integer> selectedCharacterIds = new HashSet<>();
    private String generateTime = "08:00";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.yoyo.jingxi.utils.ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weather_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        swEnableNoteGeneration = findViewById(R.id.swEnableNoteGeneration);
        rvCharacterMultiSelect = findViewById(R.id.rvCharacterMultiSelect);
        btnPickTime = findViewById(R.id.btnPickTime);
        swEnableHourly = findViewById(R.id.swEnableHourly);
        swEnableGlobalWeather = findViewById(R.id.swEnableGlobalWeather);

        llCharacterSection = findViewById(R.id.llCharacterSection);
        llTimeSection = findViewById(R.id.llTimeSection);
        llHourlySection = findViewById(R.id.llHourlySection);
        llGlobalSection = findViewById(R.id.llGlobalSection);

        rvCharacterMultiSelect.setLayoutManager(new LinearLayoutManager(this));
        characterAdapter = new CharacterMultiSelectAdapter();
        rvCharacterMultiSelect.setAdapter(characterAdapter);

        loadSettings();
        setupListeners();
        loadCharacters();
    }

    private void loadSettings() {
        boolean enabled = SpUtils.getBoolean("WEATHER_NOTE_ENABLED", false);
        swEnableNoteGeneration.setChecked(enabled);

        String idsStr = SpUtils.getString("WEATHER_NOTE_CHARACTER_IDS", "");
        if (!idsStr.isEmpty()) {
            for (String s : idsStr.split(",")) {
                try {
                    selectedCharacterIds.add(Integer.parseInt(s.trim()));
                } catch (NumberFormatException ignored) {}
            }
        }

        generateTime = SpUtils.getString("WEATHER_NOTE_GENERATE_TIME", "08:00");
        btnPickTime.setText(generateTime);

        swEnableHourly.setChecked(SpUtils.getBoolean("WEATHER_NOTE_HOURLY_ENABLED", false));
        swEnableGlobalWeather.setChecked(SpUtils.getBoolean("WEATHER_GLOBAL_MAPPING_ENABLED", false));

        updateSectionStates(enabled);
    }

    private void updateSectionStates(boolean enabled) {
        float alpha = enabled ? 1.0f : 0.4f;
        llCharacterSection.setAlpha(alpha);
        llTimeSection.setAlpha(alpha);
        llHourlySection.setAlpha(alpha);
        llGlobalSection.setAlpha(alpha);

        for (int i = 0; i < rvCharacterMultiSelect.getChildCount(); i++) {
            rvCharacterMultiSelect.getChildAt(i).setEnabled(enabled);
        }
        btnPickTime.setEnabled(enabled);
        swEnableHourly.setEnabled(enabled);
        swEnableGlobalWeather.setEnabled(enabled);
    }

    private void setupListeners() {
        swEnableNoteGeneration.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SpUtils.putBoolean("WEATHER_NOTE_ENABLED", isChecked);
            updateSectionStates(isChecked);
            JingxiApplication.rescheduleWeatherReminderWorker();
        });

        btnPickTime.setOnClickListener(v -> {
            String[] parts = generateTime.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);

            TimePickerDialog dialog = new TimePickerDialog(this,
                    (view, hourOfDay, minuteOfDay) -> {
                        generateTime = String.format("%02d:%02d", hourOfDay, minuteOfDay);
                        btnPickTime.setText(generateTime);
                        SpUtils.putString("WEATHER_NOTE_GENERATE_TIME", generateTime);
                        JingxiApplication.rescheduleWeatherReminderWorker();
                    }, hour, minute, true);
            dialog.show();
        });

        swEnableHourly.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SpUtils.putBoolean("WEATHER_NOTE_HOURLY_ENABLED", isChecked);
        });

        swEnableGlobalWeather.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SpUtils.putBoolean("WEATHER_GLOBAL_MAPPING_ENABLED", isChecked);
        });
    }

    private void loadCharacters() {
        new Thread(() -> {
            List<Character> chars = AppDatabase.getDatabase(this).characterDao().getAllCharactersSync();
            allCharacters.clear();
            if (chars != null) allCharacters.addAll(chars);
            runOnUiThread(() -> characterAdapter.notifyDataSetChanged());
        }).start();
    }

    private void saveCharacterSelection() {
        StringBuilder sb = new StringBuilder();
        for (int id : selectedCharacterIds) {
            if (sb.length() > 0) sb.append(",");
            sb.append(id);
        }
        SpUtils.putString("WEATHER_NOTE_CHARACTER_IDS", sb.toString());
    }

    private class CharacterMultiSelectAdapter extends RecyclerView.Adapter<CharacterMultiSelectAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_character_checkbox, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Character character = allCharacters.get(position);
            holder.tvName.setText(character.name);
            holder.cbSelected.setChecked(selectedCharacterIds.contains(character.id));

            if (character.avatarPath != null && !character.avatarPath.isEmpty()) {
                Glide.with(holder.itemView.getContext())
                        .load(character.avatarPath)
                        .circleCrop()
                        .placeholder(R.drawable.ic_launcher_round)
                        .into(holder.ivAvatar);
            } else {
                holder.ivAvatar.setImageResource(R.drawable.ic_launcher_round);
            }

            holder.itemView.setOnClickListener(v -> {
                if (!swEnableNoteGeneration.isChecked()) return;
                if (selectedCharacterIds.contains(character.id)) {
                    selectedCharacterIds.remove(character.id);
                    holder.cbSelected.setChecked(false);
                } else {
                    selectedCharacterIds.add(character.id);
                    holder.cbSelected.setChecked(true);
                }
                saveCharacterSelection();
            });
        }

        @Override
        public int getItemCount() {
            return allCharacters.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivAvatar;
            TextView tvName;
            CheckBox cbSelected;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivAvatar = itemView.findViewById(R.id.ivAvatar);
                tvName = itemView.findViewById(R.id.tvName);
                cbSelected = itemView.findViewById(R.id.cbSelected);
            }
        }
    }
}
