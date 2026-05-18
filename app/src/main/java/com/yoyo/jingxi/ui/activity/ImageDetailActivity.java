package com.yoyo.jingxi.ui.activity;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.github.chrisbanes.photoview.PhotoView;
import com.yoyo.jingxi.R;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.Moment;
import com.yoyo.jingxi.data.entity.Message;

public class ImageDetailActivity extends AppCompatActivity {

    private String currentImageUrl;
    private int momentId = -1;
    private int messageId = -1;
    private int imageIndex = -1;
    private PhotoView photoView;
    private android.widget.TextView tvDesc;

    private BroadcastReceiver momentUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.yoyo.jingxi.ACTION_MOMENT_UPDATED".equals(intent.getAction()) ||
                "com.yoyo.jingxi.ACTION_MESSAGE_UPDATED".equals(intent.getAction())) {
                checkAndUpdateImage();
            }
        }
    };

    private void checkAndUpdateImage() {
        if (momentId != -1 && imageIndex != -1) {
            new Thread(() -> {
                Moment moment = AppDatabase.getDatabase(ImageDetailActivity.this).momentDao().getMomentByIdSync(momentId);
                if (moment != null && moment.imageUrl != null) {
                    String[] urls = moment.imageUrl.split(",");
                    if (imageIndex >= 0 && imageIndex < urls.length) {
                        String newUrl = urls[imageIndex];
                        updateImageOnMainThread(newUrl);
                    }
                }
            }).start();
        } else if (messageId != -1) {
            new Thread(() -> {
                Message message = AppDatabase.getDatabase(ImageDetailActivity.this).messageDao().getMessageByIdSync(messageId);
                if (message != null) {
                    String newUrl = message.imageUrl;
                    if (newUrl == null || newUrl.isEmpty()) {
                        newUrl = message.imageDesc;
                    }
                    if (newUrl != null && !newUrl.isEmpty()) {
                        updateImageOnMainThread(newUrl);
                    }
                }
            }).start();
        }
    }

    private void updateImageOnMainThread(String newUrl) {
        if (!newUrl.equals(currentImageUrl)) {
            currentImageUrl = newUrl;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                android.widget.Button btnRetry = findViewById(R.id.btn_retry_generate);
                btnRetry.setVisibility(android.view.View.GONE);
                
                if (currentImageUrl.startsWith("virtual://")) {
                    if (tvDesc != null) {
                        tvDesc.setVisibility(android.view.View.VISIBLE);
                        String desc = android.net.Uri.decode(currentImageUrl.substring("virtual://".length()));
                        try {
                            org.json.JSONObject json = new org.json.JSONObject(desc);
                            if (json.has("desc")) {
                                tvDesc.setText("描述：" + json.getString("desc"));
                            } else {
                                tvDesc.setText("描述：" + desc);
                            }
                        } catch (Exception e) {
                            tvDesc.setText("描述：" + desc);
                        }
                    }
                    if (!isFinishing() && !isDestroyed()) {
                        Glide.with(ImageDetailActivity.this.getApplicationContext()).load(R.drawable.bg_virtual_image).into(photoView);
                    }
                } else if (currentImageUrl.startsWith("error://")) {
                    if (tvDesc != null) {
                        tvDesc.setVisibility(android.view.View.VISIBLE);
                        String desc = android.net.Uri.decode(currentImageUrl.substring("error://".length()));
                        try {
                            org.json.JSONObject json = new org.json.JSONObject(desc);
                            if (json.has("desc")) {
                                tvDesc.setText("图片生成失败！\n描述：" + json.getString("desc"));
                            } else {
                                tvDesc.setText("图片生成失败！\n描述：" + desc);
                            }
                        } catch (Exception e) {
                            tvDesc.setText("图片生成失败！\n描述：" + desc);
                        }
                    }
                    if (!isFinishing() && !isDestroyed()) {
                        Glide.with(ImageDetailActivity.this.getApplicationContext()).load(R.drawable.bg_virtual_image).into(photoView);
                    }
                    btnRetry.setVisibility(android.view.View.VISIBLE);
                    btnRetry.setOnClickListener(v -> retryGenerateImage(currentImageUrl, android.net.Uri.decode(currentImageUrl.substring("error://".length()))));
                } else {
                    if (tvDesc != null) tvDesc.setVisibility(android.view.View.GONE);
                    if (!isFinishing() && !isDestroyed()) {
                        Glide.with(ImageDetailActivity.this.getApplicationContext())
                                .load(currentImageUrl)
                                .into(photoView);
                    }
                }
            });
        }
    }

    private void retryGenerateImage(String errorUrl, String prompt) {
        if (momentId == -1 && messageId == -1) return;
        
        android.widget.Toast.makeText(ImageDetailActivity.this, "已加入后台重新生成队列", android.widget.Toast.LENGTH_SHORT).show();
        
        new Thread(() -> {
            AppDatabase db = AppDatabase.getDatabase(ImageDetailActivity.this);
            if (momentId != -1) {
                Moment moment = db.momentDao().getMomentByIdSync(momentId);
                if (moment != null && moment.imageUrl != null) {
                    String[] urls = moment.imageUrl.split(",");
                    if (imageIndex >= 0 && imageIndex < urls.length) {
                        // 把 error:// 还原成 virtual://
                        String virtualUrl = errorUrl.replace("error://", "virtual://");
                        urls[imageIndex] = virtualUrl;
                        
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < urls.length; i++) {
                            sb.append(urls[i]);
                            if (i < urls.length - 1) {
                                sb.append(",");
                            }
                        }
                        moment.imageUrl = sb.toString();
                        db.momentDao().update(moment);
                        
                        // 通知更新
                        Intent broadcastIntent = new Intent("com.yoyo.jingxi.ACTION_MOMENT_UPDATED");
                        sendBroadcast(broadcastIntent);
                        
                        // 触发后台重新生成
                        com.yoyo.jingxi.utils.ImageGenerationManager.getInstance().checkAndGenerateImages(moment);
                    }
                }
            } else if (messageId != -1) {
                Message message = db.messageDao().getMessageByIdSync(messageId);
                if (message != null && message.imageDesc != null) {
                    message.imageDesc = message.imageDesc.replace("error://", "virtual://");
                    db.messageDao().update(message);
                    
                    Intent broadcastIntent = new Intent("com.yoyo.jingxi.ACTION_MESSAGE_UPDATED");
                    sendBroadcast(broadcastIntent);
                    
                    com.yoyo.jingxi.utils.ImageGenerationManager.getInstance().checkAndGenerateImagesForMessage(message);
                }
            }
        }).start();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.yoyo.jingxi.utils.ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_detail);

        photoView = findViewById(R.id.photo_view);
        tvDesc = findViewById(R.id.tv_virtual_desc);
        currentImageUrl = getIntent().getStringExtra("image_url");
        
        momentId = getIntent().getIntExtra("moment_id", -1);
        if (momentId == -1) {
            long mIdLong = getIntent().getLongExtra("moment_id", -1L);
            if (mIdLong != -1L) momentId = (int) mIdLong;
        }
        imageIndex = getIntent().getIntExtra("image_index", -1);
        messageId = getIntent().getIntExtra("message_id", -1);

        String virtualDesc = getIntent().getStringExtra("virtual_desc");

        android.widget.Button btnRetry = findViewById(R.id.btn_retry_generate);
        btnRetry.setVisibility(android.view.View.GONE);

        if (currentImageUrl != null && !currentImageUrl.isEmpty()) {
            if (currentImageUrl.startsWith("virtual://")) {
                String desc = android.net.Uri.decode(currentImageUrl.substring("virtual://".length()));
                if (tvDesc != null) {
                    tvDesc.setVisibility(android.view.View.VISIBLE);
                    try {
                        org.json.JSONObject json = new org.json.JSONObject(desc);
                        if (json.has("desc")) {
                            tvDesc.setText("描述：" + json.getString("desc"));
                        } else {
                            tvDesc.setText("描述：" + desc);
                        }
                    } catch (Exception e) {
                        tvDesc.setText("描述：" + desc);
                    }
                }

                boolean enableImageGen = com.yoyo.jingxi.utils.SpUtils.getBoolean("ENABLE_IMAGE_GEN", false);
                if (enableImageGen) {
                    android.widget.Toast.makeText(this, "图片正在后台生成中..", android.widget.Toast.LENGTH_SHORT).show();
                    
                    // 触发后台生成
                    new Thread(() -> {
                        com.yoyo.jingxi.data.AppDatabase db = com.yoyo.jingxi.data.AppDatabase.getDatabase(this);
                        if (momentId != -1) {
                            com.yoyo.jingxi.data.entity.Moment moment = db.momentDao().getMomentByIdSync(momentId);
                            if (moment != null) {
                                com.yoyo.jingxi.utils.ImageGenerationManager.getInstance().checkAndGenerateImages(moment);
                            }
                        } else if (messageId != -1) {
                            com.yoyo.jingxi.data.entity.Message message = db.messageDao().getMessageByIdSync(messageId);
                            if (message != null) {
                                com.yoyo.jingxi.utils.ImageGenerationManager.getInstance().checkAndGenerateImagesForMessage(message);
                            }
                        }
                    }).start();
                }
                if (!isFinishing() && !isDestroyed()) {
                    Glide.with(this.getApplicationContext()).load(R.drawable.bg_virtual_image).into(photoView);
                }
            } else if (currentImageUrl.startsWith("error://")) {
                String desc = android.net.Uri.decode(currentImageUrl.substring("error://".length()));
                if (tvDesc != null) {
                    tvDesc.setVisibility(android.view.View.VISIBLE);
                    try {
                        org.json.JSONObject json = new org.json.JSONObject(desc);
                        if (json.has("desc")) {
                            tvDesc.setText("图片生成失败！\n描述：" + json.getString("desc"));
                        } else {
                            tvDesc.setText("图片生成失败！\n描述：" + desc);
                        }
                    } catch (Exception e) {
                        tvDesc.setText("图片生成失败！\n描述：" + desc);
                    }
                }
                if (!isFinishing() && !isDestroyed()) {
                    Glide.with(this.getApplicationContext()).load(R.drawable.bg_virtual_image).into(photoView);
                }
                
                btnRetry.setVisibility(android.view.View.VISIBLE);
                btnRetry.setOnClickListener(v -> retryGenerateImage(currentImageUrl, desc));
            } else {
                if (!isFinishing() && !isDestroyed()) {
                    Glide.with(ImageDetailActivity.this.getApplicationContext()).load(currentImageUrl).into(photoView);
                }
            }
        }

        photoView.setOnClickListener(v -> finish());

        photoView.setOnLongClickListener(v -> {
            if (currentImageUrl != null) {
                if (currentImageUrl.startsWith("virtual://") || currentImageUrl.startsWith("error://")) {
                    android.widget.Toast.makeText(ImageDetailActivity.this, "当前为虚拟图片，无法保存", android.widget.Toast.LENGTH_SHORT).show();
                    return true;
                }
                
                // Show a confirmation dialog before saving
                new android.app.AlertDialog.Builder(ImageDetailActivity.this)
                    .setTitle("保存图片")
                    .setMessage("确定要将此图片保存到系统相册吗？")
                    .setPositiveButton("确定", (dialog, which) -> saveImageToGallery(currentImageUrl))
                    .setNegativeButton("取消", null)
                    .show();
                    
                return true;
            }
            return false;
        });

        IntentFilter filter = new IntentFilter("com.yoyo.jingxi.ACTION_MOMENT_UPDATED");
        filter.addAction("com.yoyo.jingxi.ACTION_MESSAGE_UPDATED");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(momentUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(momentUpdateReceiver, filter);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(momentUpdateReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void saveImageToGallery(String imagePath) {
        new Thread(() -> {
            try {
                if (imagePath.startsWith("http://") || imagePath.startsWith("https://")) {
                    // Handle network images
                    try {
                        java.net.URL url = new java.net.URL(imagePath);
                        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                        connection.setDoInput(true);
                        connection.connect();
                        java.io.InputStream input = connection.getInputStream();
                        android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(input);
                        
                        if (bitmap != null) {
                            android.content.ContentValues values = new android.content.ContentValues();
                            values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "jingxi_" + System.currentTimeMillis() + ".png");
                            values.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png");
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/Jingxi");
                                values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 1);
                            }

                            android.net.Uri uri = getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                            if (uri != null) {
                                try (java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
                                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
                                }
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                    values.clear();
                                    values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0);
                                    getContentResolver().update(uri, values, null, null);
                                }
                                runOnUiThread(() -> android.widget.Toast.makeText(this, "已保存到相册", android.widget.Toast.LENGTH_SHORT).show());
                            } else {
                                runOnUiThread(() -> android.widget.Toast.makeText(this, "保存失败", android.widget.Toast.LENGTH_SHORT).show());
                            }
                        } else {
                            runOnUiThread(() -> android.widget.Toast.makeText(this, "图片下载失败", android.widget.Toast.LENGTH_SHORT).show());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        runOnUiThread(() -> android.widget.Toast.makeText(this, "保存网络图片失败: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show());
                    }
                    return;
                }

                // Handle local files
                String path = imagePath;
                if (path.startsWith("file://")) {
                    path = path.substring("file://".length());
                }

                java.io.File sourceFile = new java.io.File(path);
                if (!sourceFile.exists()) {
                    runOnUiThread(() -> android.widget.Toast.makeText(this, "图片文件不存在", android.widget.Toast.LENGTH_SHORT).show());
                    return;
                }

                android.content.ContentValues values = new android.content.ContentValues();
                values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "jingxi_" + System.currentTimeMillis() + ".png");
                values.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png");
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/Jingxi");
                    values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 1);
                }

                android.net.Uri uri = getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (java.io.OutputStream out = getContentResolver().openOutputStream(uri);
                         java.io.FileInputStream in = new java.io.FileInputStream(sourceFile)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        values.clear();
                        values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0);
                        getContentResolver().update(uri, values, null, null);
                    }
                    runOnUiThread(() -> android.widget.Toast.makeText(this, "已保存到相册", android.widget.Toast.LENGTH_SHORT).show());
                } else {
                     runOnUiThread(() -> android.widget.Toast.makeText(this, "保存失败", android.widget.Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> android.widget.Toast.makeText(this, "保存失败: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}
