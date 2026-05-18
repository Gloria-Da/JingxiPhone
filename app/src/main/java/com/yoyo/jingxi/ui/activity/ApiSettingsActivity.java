package com.yoyo.jingxi.ui.activity;

import android.os.Bundle;
import com.yoyo.jingxi.R;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.yoyo.jingxi.network.ModelListResponse;
import com.yoyo.jingxi.network.OpenAIManager;
import com.yoyo.jingxi.utils.SpUtils;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ApiSettingsActivity extends AppCompatActivity {

    private com.google.android.material.switchmaterial.SwitchMaterial switchEnableImageGen;
    private TextInputEditText etApiEndpoint;
    private TextInputEditText etApiKey;
    private TextInputEditText etImageApiEndpoint;
    private TextInputEditText etImageApiKey;
    private Spinner spinnerImageModel;
    private Button btnFetchImageModels;
    private Spinner spinnerModel;
    private Button btnFetchModels;
    
    private android.widget.SeekBar seekBarTemperature;
    private android.widget.EditText etTemperatureValue;
    
    private Button btnSave;

    private android.widget.EditText etPresetName;
    private Button btnSavePreset;
    private Spinner spinnerPreset;
    private TextInputEditText etQWeatherKey;
    private TextInputEditText etQWeatherHost;

    private ArrayAdapter<String> modelAdapter;
    private List<String> modelList = new ArrayList<>();

    private ArrayAdapter<String> imageModelAdapter;
    private List<String> imageModelList = new ArrayList<>();

    private ArrayAdapter<String> presetAdapter;
    private List<String> presetNames = new ArrayList<>();
    private Map<String, ApiConfig> presetsMap = new HashMap<>();
    private Gson gson = new Gson();

    private static class ApiConfig {
        String endpoint;
        String apiKey;
        String model;
        float temperature;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.yoyo.jingxi.utils.ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_api_settings);

        initViews();
        loadPresets();
        loadCurrentConfig();
        setupListeners();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("设置");
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        switchEnableImageGen = findViewById(R.id.switchEnableImageGen);
        etApiEndpoint = findViewById(R.id.etApiEndpoint);
        etApiKey = findViewById(R.id.etApiKey);
        etImageApiEndpoint = findViewById(R.id.etImageApiEndpoint);
        etImageApiKey = findViewById(R.id.etImageApiKey);
        spinnerImageModel = findViewById(R.id.spinnerImageModel);
        btnFetchImageModels = findViewById(R.id.btnFetchImageModels);
        spinnerModel = findViewById(R.id.spinnerModel);
        btnFetchModels = findViewById(R.id.btnFetchModels);
        etQWeatherKey = findViewById(R.id.etQWeatherKey);
        etQWeatherHost = findViewById(R.id.etQWeatherHost);
        
        seekBarTemperature = findViewById(R.id.seekBarTemperature);
        etTemperatureValue = findViewById(R.id.etTemperatureValue);
        
        btnSave = findViewById(R.id.btnSave);

        etPresetName = findViewById(R.id.etPresetName);
        btnSavePreset = findViewById(R.id.btnSavePreset);
        spinnerPreset = findViewById(R.id.spinnerPreset);

        modelAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, modelList);
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerModel.setAdapter(modelAdapter);

        imageModelAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, imageModelList);
        imageModelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerImageModel.setAdapter(imageModelAdapter);

        presetAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, presetNames);
        presetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPreset.setAdapter(presetAdapter);
    }

    private void loadCurrentConfig() {
        String endpoint = SpUtils.getString("API_ENDPOINT", "https://api.openai.com/");
        String key = SpUtils.getString("OPENAI_API_KEY", "");
        String model = SpUtils.getString("API_MODEL", "gpt-4o-mini");
        
        float temperature = SpUtils.getFloat("API_TEMPERATURE", 0.8f);
        boolean enableImageGen = SpUtils.getBoolean("ENABLE_IMAGE_GEN", false);
        String imageEndpoint = SpUtils.getString("IMAGE_API_ENDPOINT", "https://api.openai.com/");
        String imageKey = SpUtils.getString("IMAGE_API_KEY", "");
        String imageModel = SpUtils.getString("IMAGE_API_MODEL", "dall-e-3");
        String qWeatherKey = SpUtils.getString("QWEATHER_API_KEY", "");
        String qWeatherHost = SpUtils.getString("QWEATHER_API_HOST", "");

        switchEnableImageGen.setChecked(enableImageGen);
        etApiEndpoint.setText(endpoint);
        etApiKey.setText(key);
        etImageApiEndpoint.setText(imageEndpoint);
        etImageApiKey.setText(imageKey);
        etQWeatherKey.setText(qWeatherKey);
        etQWeatherHost.setText(qWeatherHost);
        
        if (!imageModelList.contains(imageModel)) {
            imageModelList.add(imageModel);
            imageModelAdapter.notifyDataSetChanged();
        }
        spinnerImageModel.setSelection(imageModelList.indexOf(imageModel));
        
        if (!modelList.contains(model)) {
            modelList.add(model);
            modelAdapter.notifyDataSetChanged();
        }
        spinnerModel.setSelection(modelList.indexOf(model));

        seekBarTemperature.setProgress((int) (temperature * 10));
        etTemperatureValue.setText(String.format(java.util.Locale.US, "%.1f", temperature));
    }

    private void loadPresets() {
        String presetsJson = SpUtils.getString("API_PRESETS", "{}");
        Type type = new TypeToken<Map<String, ApiConfig>>(){}.getType();
        Map<String, ApiConfig> loaded = gson.fromJson(presetsJson, type);
        if (loaded != null) {
            presetsMap.putAll(loaded);
        }
        
        presetNames.clear();
        presetNames.add("-- 选择预设 --");
        presetNames.addAll(presetsMap.keySet());
        presetAdapter.notifyDataSetChanged();
    }

    private void updateTemperatureFromInput() {
        String tempStr = etTemperatureValue.getText() != null ? etTemperatureValue.getText().toString().trim() : "";
        if (!TextUtils.isEmpty(tempStr)) {
            try {
                float temp = Float.parseFloat(tempStr);
                if (temp < 0.0f) temp = 0.0f;
                if (temp > 2.0f) temp = 2.0f;
                etTemperatureValue.setText(String.format(java.util.Locale.US, "%.1f", temp));
                seekBarTemperature.setProgress((int) (temp * 10));
            } catch (NumberFormatException e) {
                etTemperatureValue.setText(String.format(java.util.Locale.US, "%.1f", seekBarTemperature.getProgress() / 10f));
            }
        }
    }

    private void updateImageGenUI(boolean isEnabled) {
        etImageApiEndpoint.setEnabled(isEnabled);
        etImageApiKey.setEnabled(isEnabled);
        spinnerImageModel.setEnabled(isEnabled);
        btnFetchImageModels.setEnabled(isEnabled);
    }

    private void setupListeners() {
        switchEnableImageGen.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateImageGenUI(isChecked);
        });
        updateImageGenUI(switchEnableImageGen.isChecked());

        btnFetchModels.setOnClickListener(v -> fetchModels(false));
        btnFetchImageModels.setOnClickListener(v -> fetchModels(true));

        seekBarTemperature.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float temp = progress / 10f;
                    etTemperatureValue.setText(String.format(java.util.Locale.US, "%.1f", temp));
                }
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });

        etTemperatureValue.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                updateTemperatureFromInput();
            }
        });

        etTemperatureValue.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE || 
                (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER && event.getAction() == android.view.KeyEvent.ACTION_DOWN)) {
                updateTemperatureFromInput();
                return true;
            }
            return false;
        });

        btnSave.setOnClickListener(v -> {
            String endpoint = etApiEndpoint.getText() != null ? etApiEndpoint.getText().toString().trim() : "";
            String key = etApiKey.getText() != null ? etApiKey.getText().toString().trim() : "";
            String model = spinnerModel.getSelectedItem() != null ? spinnerModel.getSelectedItem().toString() : "gpt-4o-mini";

            float temperature = 0.8f;
            String tempStr = etTemperatureValue.getText() != null ? etTemperatureValue.getText().toString().trim() : "";
            if (!TextUtils.isEmpty(tempStr)) {
                try {
                    temperature = Float.parseFloat(tempStr);
                } catch (NumberFormatException e) {
                    temperature = seekBarTemperature.getProgress() / 10f;
                }
            }

            if (TextUtils.isEmpty(endpoint)) {
                endpoint = "https://api.openai.com/v1/"; // default
            }
            if (!endpoint.endsWith("/")) {
                endpoint += "/";
            }

            String imageEndpoint = etImageApiEndpoint.getText() != null ? etImageApiEndpoint.getText().toString().trim() : "";
            String imageKey = etImageApiKey.getText() != null ? etImageApiKey.getText().toString().trim() : "";
            String imageModel = spinnerImageModel.getSelectedItem() != null ? spinnerImageModel.getSelectedItem().toString() : "dall-e-3";
            String qWeatherKey = etQWeatherKey.getText() != null ? etQWeatherKey.getText().toString().trim() : "";
            String qWeatherHost = etQWeatherHost.getText() != null ? etQWeatherHost.getText().toString().trim() : "";

            SpUtils.putString("API_ENDPOINT", endpoint);
            SpUtils.putString("OPENAI_API_KEY", key);
            SpUtils.putString("API_MODEL", model);
            SpUtils.putFloat("API_TEMPERATURE", temperature);
            SpUtils.putBoolean("ENABLE_IMAGE_GEN", switchEnableImageGen.isChecked());
            SpUtils.putString("IMAGE_API_ENDPOINT", imageEndpoint);
            SpUtils.putString("IMAGE_API_KEY", imageKey);
            SpUtils.putString("IMAGE_API_MODEL", imageModel);
            SpUtils.putString("QWEATHER_API_KEY", qWeatherKey);
            SpUtils.putString("QWEATHER_API_HOST", qWeatherHost);

            Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show();
        });

        btnSavePreset.setOnClickListener(v -> {
            String name = etPresetName.getText() != null ? etPresetName.getText().toString().trim() : "";
            if (TextUtils.isEmpty(name)) {
                Toast.makeText(this, "请输入预设名称", Toast.LENGTH_SHORT).show();
                return;
            }

            ApiConfig config = new ApiConfig();
            config.endpoint = etApiEndpoint.getText() != null ? etApiEndpoint.getText().toString().trim() : "";
            config.apiKey = etApiKey.getText() != null ? etApiKey.getText().toString().trim() : "";
            config.model = spinnerModel.getSelectedItem() != null ? spinnerModel.getSelectedItem().toString() : "";
            
            float presetTemperature = 0.8f;
            String presetTempStr = etTemperatureValue.getText() != null ? etTemperatureValue.getText().toString().trim() : "";
            if (!TextUtils.isEmpty(presetTempStr)) {
                try {
                    presetTemperature = Float.parseFloat(presetTempStr);
                } catch (NumberFormatException e) {
                    presetTemperature = seekBarTemperature.getProgress() / 10f;
                }
            }
            config.temperature = presetTemperature;

            presetsMap.put(name, config);
            SpUtils.putString("API_PRESETS", gson.toJson(presetsMap));
            
            if (!presetNames.contains(name)) {
                presetNames.add(name);
                presetAdapter.notifyDataSetChanged();
            }
            etPresetName.setText("");
            Toast.makeText(this, "预设已保存", Toast.LENGTH_SHORT).show();
        });

        spinnerPreset.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) { // Skip "-- 选择预设 --"
                    String name = presetNames.get(position);
                    ApiConfig config = presetsMap.get(name);
                    if (config != null) {
                        etApiEndpoint.setText(config.endpoint);
                        etApiKey.setText(config.apiKey);
                        if (!TextUtils.isEmpty(config.model) && !modelList.contains(config.model)) {
                            modelList.add(config.model);
                            modelAdapter.notifyDataSetChanged();
                        }
                        if (!TextUtils.isEmpty(config.model)) {
                            spinnerModel.setSelection(modelList.indexOf(config.model));
                        }
                        
                        float t = config.temperature;
                        if (t == 0.0f) {
                            t = 0.8f; // Fallback for old presets
                        }
                        seekBarTemperature.setProgress((int) (t * 10));
                        etTemperatureValue.setText(String.format(java.util.Locale.US, "%.1f", t));
                    }
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void fetchModels(boolean isForImage) {
        String endpoint = isForImage ? (etImageApiEndpoint.getText() != null ? etImageApiEndpoint.getText().toString().trim() : "") : (etApiEndpoint.getText() != null ? etApiEndpoint.getText().toString().trim() : "");
        String key = isForImage ? (etImageApiKey.getText() != null ? etImageApiKey.getText().toString().trim() : "") : (etApiKey.getText() != null ? etApiKey.getText().toString().trim() : "");

        if (TextUtils.isEmpty(endpoint) || TextUtils.isEmpty(key)) {
            Toast.makeText(this, "请先填入地址和秘钥", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!endpoint.endsWith("/")) {
            endpoint += "/";
        }
        
        String modelsUrl = endpoint + "v1/models";

        if (isForImage) {
            btnFetchImageModels.setEnabled(false);
            btnFetchImageModels.setText("拉取中...");
        } else {
            btnFetchModels.setEnabled(false);
            btnFetchModels.setText("拉取中...");
        }

        OpenAIManager aiManager = new OpenAIManager(); // Just for using the initialized Retrofit Api
        aiManager.getApi().getModels(modelsUrl, "Bearer " + key).enqueue(new Callback<ModelListResponse>() {
            @Override
            public void onResponse(Call<ModelListResponse> call, Response<ModelListResponse> response) {
                if (isForImage) {
                    btnFetchImageModels.setEnabled(true);
                    btnFetchImageModels.setText("拉取模型");
                } else {
                    btnFetchModels.setEnabled(true);
                    btnFetchModels.setText("拉取模型");
                }
                
                if (response.isSuccessful() && response.body() != null && response.body().data != null) {
                    List<String> fetchedModels = new ArrayList<>();
                    for (ModelListResponse.Model m : response.body().data) {
                        fetchedModels.add(m.id);
                    }
                    
                    if (isForImage) {
                        imageModelList.clear();
                        imageModelList.addAll(fetchedModels);
                        imageModelAdapter.notifyDataSetChanged();
                        Toast.makeText(ApiSettingsActivity.this, "模型拉取成功", Toast.LENGTH_SHORT).show();
                        
                        String currentSelected = SpUtils.getString("IMAGE_API_MODEL", "");
                        if (!TextUtils.isEmpty(currentSelected) && imageModelList.contains(currentSelected)) {
                            spinnerImageModel.setSelection(imageModelList.indexOf(currentSelected));
                        }
                    } else {
                        modelList.clear();
                        modelList.addAll(fetchedModels);
                        modelAdapter.notifyDataSetChanged();
                        Toast.makeText(ApiSettingsActivity.this, "模型拉取成功", Toast.LENGTH_SHORT).show();
                        
                        // Maintain current selection if possible
                        String currentSelected = SpUtils.getString("API_MODEL", "");
                        if (!TextUtils.isEmpty(currentSelected) && modelList.contains(currentSelected)) {
                            spinnerModel.setSelection(modelList.indexOf(currentSelected));
                        }
                    }
                } else {
                    Toast.makeText(ApiSettingsActivity.this, "拉取失败: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ModelListResponse> call, Throwable t) {
                if (isForImage) {
                    btnFetchImageModels.setEnabled(true);
                    btnFetchImageModels.setText("拉取模型");
                } else {
                    btnFetchModels.setEnabled(true);
                    btnFetchModels.setText("拉取模型");
                }
                Toast.makeText(ApiSettingsActivity.this, "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

}
