package com.yoyo.jingxi.ui.activity;

import android.os.Bundle;
import com.yoyo.jingxi.R;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.WorldbookEntry;

import java.util.concurrent.Executors;

public class WorldbookEditActivity extends AppCompatActivity {

    private Spinner spinnerType;
    private EditText etTitle;
    private EditText etKeyword;
    private EditText etContent;
    private TextView tvKeywordLabel;

    private WorldbookEntry currentEntry;
    private AppDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.yoyo.jingxi.utils.ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_worldbook_edit);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        db = AppDatabase.getDatabase(this);

        spinnerType = findViewById(R.id.spinnerType);
        etTitle = findViewById(R.id.etTitle);
        etKeyword = findViewById(R.id.etKeyword);
        etContent = findViewById(R.id.etContent);
        tvKeywordLabel = findViewById(R.id.tvKeywordLabel);
        Button btnSave = findViewById(R.id.btnSave);

        String[] types = {"前", "中", "后"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, types);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerType.setAdapter(adapter);

        spinnerType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 1) { // 中世界书
                    tvKeywordLabel.setVisibility(View.VISIBLE);
                    etKeyword.setVisibility(View.VISIBLE);
                } else {
                    tvKeywordLabel.setVisibility(View.GONE);
                    etKeyword.setVisibility(View.GONE);
                    etKeyword.setText("");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        if (getIntent().hasExtra("entry")) {
            currentEntry = (WorldbookEntry) getIntent().getSerializableExtra("entry");
            getSupportActionBar().setTitle("编辑词条");
            spinnerType.setSelection(currentEntry.type);
            etTitle.setText(currentEntry.title);
            etKeyword.setText(currentEntry.keyword);
            etContent.setText(currentEntry.content);
        } else {
            getSupportActionBar().setTitle("新建词条");
            int defaultType = getIntent().getIntExtra("type", 0);
            spinnerType.setSelection(defaultType);
        }

        btnSave.setOnClickListener(v -> saveEntry());
    }

    private void saveEntry() {
        int type = spinnerType.getSelectedItemPosition();
        String title = etTitle.getText().toString().trim();
        String keyword = etKeyword.getText().toString().trim();
        String content = etContent.getText().toString().trim();

        if (TextUtils.isEmpty(content)) {
            Toast.makeText(this, "词条内容不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        if (type == 1 && TextUtils.isEmpty(keyword)) {
            Toast.makeText(this, "中世界书必须填写触发关键词", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentEntry == null) {
            currentEntry = new WorldbookEntry();
            currentEntry.isEnabled = true;
        }

        currentEntry.type = type;
        currentEntry.title = title;
        currentEntry.keyword = type == 1 ? keyword : "";
        currentEntry.content = content;

        Executors.newSingleThreadExecutor().execute(() -> {
            if (currentEntry.id == 0) {
                db.worldbookDao().insert(currentEntry);
            } else {
                db.worldbookDao().update(currentEntry);
            }
            runOnUiThread(() -> {
                Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }
}
