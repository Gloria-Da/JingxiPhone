package com.yoyo.jingxi.ui.activity;

import android.app.AlertDialog;
import android.os.Bundle;
import com.yoyo.jingxi.R;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.Memo;
import com.yoyo.jingxi.ui.adapter.MemoAdapter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class MemoActivity extends AppCompatActivity {

    private int characterId;
    private RecyclerView rvMemo;
    private MemoAdapter memoAdapter;
    private AppDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.yoyo.jingxi.utils.ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_memo);

        characterId = getIntent().getIntExtra("character_id", -1);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("备忘录");
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        rvMemo = findViewById(R.id.rvMemo);
        rvMemo.setLayoutManager(new LinearLayoutManager(this));
        memoAdapter = new MemoAdapter();
        rvMemo.setAdapter(memoAdapter);

        db = AppDatabase.getDatabase(this);

        db.memoDao().getMemosByCharacterId(characterId).observe(this, memos -> {
            memoAdapter.setItems(memos);
        });

        FloatingActionButton fabAddMemo = findViewById(R.id.fabAddMemo);
        fabAddMemo.setOnClickListener(v -> showEditDialog(null));

        memoAdapter.setOnMemoClickListener(new MemoAdapter.OnMemoClickListener() {
            @Override
            public void onMemoClick(Memo memo) {
                showEditDialog(memo);
            }

            @Override
            public void onMemoLongClick(Memo memo, View view) {
                new AlertDialog.Builder(MemoActivity.this)
                        .setTitle("删除备忘录")
                        .setMessage("确定要删除这条备忘录吗？")
                        .setPositiveButton("删除", (dialog, which) -> {
                            Executors.newSingleThreadExecutor().execute(() -> {
                                db.memoDao().delete(memo);
                            });
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        });
    }

    private void showEditDialog(Memo existingMemo) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(existingMemo == null ? "添加备忘录" : "编辑备忘录");

        View view = getLayoutInflater().inflate(R.layout.dialog_edit_memo, null);
        EditText etContent = view.findViewById(R.id.etContent);
        EditText etDate = view.findViewById(R.id.etDate);
        RadioGroup rgStatus = view.findViewById(R.id.rgStatus);

        if (existingMemo != null) {
            etContent.setText(existingMemo.content);
            etDate.setText(existingMemo.targetDate);
            if (existingMemo.status == 1) {
                rgStatus.check(R.id.rbCompleted);
            } else if (existingMemo.status == 2) {
                rgStatus.check(R.id.rbRecord);
            } else {
                rgStatus.check(R.id.rbPending);
            }
        } else {
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            etDate.setText(today);
            rgStatus.check(R.id.rbPending);
        }

        builder.setView(view);

        builder.setPositiveButton("保存", (dialog, which) -> {
            String content = etContent.getText().toString().trim();
            String date = etDate.getText().toString().trim();
            int status = 0;
            if (rgStatus.getCheckedRadioButtonId() == R.id.rbCompleted) {
                status = 1;
            } else if (rgStatus.getCheckedRadioButtonId() == R.id.rbRecord) {
                status = 2;
            }

            if (TextUtils.isEmpty(content)) {
                Toast.makeText(this, "内容不能为空", Toast.LENGTH_SHORT).show();
                return;
            }

            final int finalStatus = status;
            Executors.newSingleThreadExecutor().execute(() -> {
                if (existingMemo == null) {
                    Memo newMemo = new Memo();
                    newMemo.characterId = characterId;
                    newMemo.content = content;
                    newMemo.targetDate = date;
                    newMemo.status = finalStatus;
                    newMemo.createdAt = System.currentTimeMillis();
                    db.memoDao().insert(newMemo);
                } else {
                    existingMemo.content = content;
                    existingMemo.targetDate = date;
                    existingMemo.status = finalStatus;
                    db.memoDao().update(existingMemo);
                }
            });
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }
}
