package com.yoyo.jingxi.ui.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.yoyo.jingxi.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LogActivity extends AppCompatActivity {

    private TextView tvLogs;
    private ProgressBar progressBar;
    private ScrollView scrollView;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.yoyo.jingxi.utils.ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("应用日志");
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        tvLogs = findViewById(R.id.tvLogs);
        progressBar = findViewById(R.id.progressBar);
        scrollView = findViewById(R.id.scrollView);

        findViewById(R.id.btnRefresh).setOnClickListener(v -> loadLogs());
        findViewById(R.id.btnClear).setOnClickListener(v -> clearLogs());

        loadLogs();
    }

    private void loadLogs() {
        progressBar.setVisibility(View.VISIBLE);
        tvLogs.setText("正在读取日志...");

        executorService.execute(() -> {
            StringBuilder logBuilder = new StringBuilder();
            Process process = null;
            BufferedReader reader = null;
            try {
                // -d: dump and exit
                // -v threadtime: include thread/time info
                // com.yoyo.jingxi:V: verbose for our app
                // *:S: silent for everything else (this filtering often doesn't work perfectly on newer Androids without PID filtering)
                // We'll just dump recent logs and maybe filter by PID if needed, but let's try simple first.
                
                // Let's get the current process ID
                int pid = android.os.Process.myPid();
                
                process = Runtime.getRuntime().exec("logcat -d -v threadtime");
                reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                
                String line;
                String pidString = String.valueOf(pid);
                while ((line = reader.readLine()) != null) {
                    // Filter logs to only show our own process
                    if (line.contains(pidString)) {
                        logBuilder.append(line).append("\n");
                    }
                }
            } catch (IOException e) {
                logBuilder.append("读取日志失败: ").append(e.getMessage());
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (process != null) {
                    process.destroy();
                }
            }

            final String finalLogs = logBuilder.toString();
            mainHandler.post(() -> {
                progressBar.setVisibility(View.GONE);
                if (finalLogs.isEmpty()) {
                    tvLogs.setText("暂无应用日志。");
                } else {
                    tvLogs.setText(finalLogs);
                    // Scroll to bottom
                    scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
                }
            });
        });
    }

    private void clearLogs() {
        executorService.execute(() -> {
            try {
                Runtime.getRuntime().exec("logcat -c").waitFor();
                mainHandler.post(() -> {
                    Toast.makeText(LogActivity.this, "日志已清空", Toast.LENGTH_SHORT).show();
                    tvLogs.setText("");
                });
            } catch (Exception e) {
                mainHandler.post(() -> 
                    Toast.makeText(LogActivity.this, "清空日志失败: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}
