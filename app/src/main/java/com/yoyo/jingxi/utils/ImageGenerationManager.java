package com.yoyo.jingxi.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.Moment;
import com.yoyo.jingxi.network.ImageGenerationRequest;
import com.yoyo.jingxi.network.ImageGenerationResponse;
import com.yoyo.jingxi.network.OpenAiApi;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ImageGenerationManager {
    private static final String TAG = "ImageGenerationManager";
    private static ImageGenerationManager instance;
    private final ExecutorService executorService;
    private Context context;
    private final java.util.Set<String> generatingTasks;
    private final java.util.Set<String> processedTasks;

    private ImageGenerationManager(Context context) {
        this.context = context.getApplicationContext();
        this.executorService = Executors.newSingleThreadExecutor();
        this.generatingTasks = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
        this.processedTasks = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    }

    public static synchronized void init(Context context) {
        if (instance == null) {
            instance = new ImageGenerationManager(context);
        }
    }

    public static ImageGenerationManager getInstance() {
        return instance;
    }

    public void checkAndGenerateImages(Moment moment) {
        if (!SpUtils.getBoolean("ENABLE_IMAGE_GEN", false)) {
            return;
        }

        if (moment == null || TextUtils.isEmpty(moment.imageUrl)) {
            return;
        }
        
        boolean hasVirtualImages = false;

        String[] urls = moment.imageUrl.split(",");
        for (int i = 0; i < urls.length; i++) {
            String url = urls[i];
            if (url.startsWith("virtual://")) {
                hasVirtualImages = true;
                String descStr = android.net.Uri.decode(url.substring("virtual://".length()));
                String prompt = descStr;
                try {
                    // 尝试解析 JSON 格式的描述
                    JSONObject json = new JSONObject(descStr);
                    if (json.has("desc")) {
                        prompt = json.getString("desc");
                    }
                } catch (Exception e) {
                    // 如果不是 JSON，直接使用整个字符串
                }
                
                final int index = i;
                final String finalPrompt = prompt;
                final String originalUrl = url;
                
                String taskId = moment.id + "_" + index;
                if (!processedTasks.contains(taskId) && generatingTasks.add(taskId)) {
                    executorService.submit(() -> generateImage(moment, index, finalPrompt, originalUrl, taskId));
                }
            }
        }
        
        // 如果没有虚拟图片了，且不是消息相关的生图，可以考虑停止服务
        if (!hasVirtualImages) {
            Intent serviceIntent = new Intent(context, com.yoyo.jingxi.service.ImageGenForegroundService.class);
            context.stopService(serviceIntent);
        }
    }

    public void checkAndGenerateImagesForMessage(com.yoyo.jingxi.data.entity.Message message) {
        if (!SpUtils.getBoolean("ENABLE_IMAGE_GEN", false)) {
            return;
        }

        if (message == null || TextUtils.isEmpty(message.imageDesc)) {
            return;
        }
        
        // 如果已经有真实的图片地址，说明已经生成过了
        if (!TextUtils.isEmpty(message.imageUrl) && !message.imageUrl.startsWith("virtual://") && !message.imageUrl.startsWith("error://")) {
            return;
        }
        
        // 如果之前生成失败，就不再自动重试
        if (message.imageDesc != null && message.imageDesc.startsWith("error://")) {
            return;
        }

        // 只有类型是 3（接收到的消息且尚未生图）或 4（正在生图）才处理
        if (message.type != 3 && message.type != 4) {
            return;
        }
        
        boolean hasVirtualImages = false;
        if (message.imageDesc != null && message.imageDesc.contains("virtual://")) {
            hasVirtualImages = true;
        }

        String prompt = message.imageDesc;
        try {
            // 尝试解析 JSON 格式的描述
            JSONObject json = new JSONObject(message.imageDesc);
            if (json.has("desc")) {
                prompt = json.getString("desc");
            }
        } catch (Exception e) {
            // 如果不是 JSON，直接使用整个字符串
        }

        final String finalPrompt = prompt;
        final String originalDesc = message.imageDesc;

        String taskId = "msg_" + message.id;
        if (!processedTasks.contains(taskId) && generatingTasks.add(taskId)) {
            executorService.submit(() -> generateImageForMessage(message, finalPrompt, originalDesc, taskId));
        }
        
        // 如果没有虚拟图片了，且不是动态相关的生图，可以考虑停止服务
        if (!hasVirtualImages) {
            Intent serviceIntent = new Intent(context, com.yoyo.jingxi.service.ImageGenForegroundService.class);
            context.stopService(serviceIntent);
        }
    }

    private void generateImage(Moment moment, int index, String prompt, String originalUrl, String taskId) {
        try {
            doGenerateImage(moment, index, prompt, originalUrl);
        } finally {
            generatingTasks.remove(taskId);
            processedTasks.add(taskId);
        }
    }

    private void generateImageForMessage(com.yoyo.jingxi.data.entity.Message message, String prompt, String originalDesc, String taskId) {
        try {
            // 如果是虚拟图片，更新消息状态
            if (originalDesc != null && originalDesc.contains("virtual://")) {
                message.type = 4;
                message.imageDesc = originalDesc;
                AppDatabase db = AppDatabase.getDatabase(context);
                db.messageDao().update(message);
                
                // 发送广播通知更新UI，把状态从 AI的消息 变成 发送中
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    Intent broadcastIntent = new Intent("com.yoyo.jingxi.ACTION_MESSAGE_UPDATED");
                    context.sendBroadcast(broadcastIntent);
                });
            }
            doGenerateImageForMessage(message, prompt, originalDesc);
        } finally {
            generatingTasks.remove(taskId);
            processedTasks.add(taskId);
        }
    }

    private void doGenerateImageForMessage(com.yoyo.jingxi.data.entity.Message message, String prompt, String originalDesc) {
        if (!SpUtils.getBoolean("ENABLE_IMAGE_GEN", false)) {
            Log.d(TAG, "Image generation is disabled, skipping doGenerateImageForMessage");
            markMessageImageGenerationError(message.id, originalDesc);
            return;
        }

        String endpoint = SpUtils.getString("IMAGE_API_ENDPOINT", "https://api.openai.com/");
        String key = SpUtils.getString("IMAGE_API_KEY", "");
        String model = SpUtils.getString("IMAGE_API_MODEL", "dall-e-3");

        if (TextUtils.isEmpty(endpoint) || TextUtils.isEmpty(key)) {
            Log.e(TAG, "API not configured properly. endpoint=" + endpoint + ", key=" + (TextUtils.isEmpty(key) ? "empty" : "has_value"));
            return;
        }

        if (!endpoint.endsWith("/")) {
            endpoint += "/";
        }
        String requestUrl = endpoint + "v1/images/generations";
        
        String size = "1024x1024";
        try {
            JSONObject json = new JSONObject(originalDesc);
            if (json.has("size")) {
                size = json.getString("size");
            }
        } catch (Exception e) {
            // Not JSON or size not found
        }

        ImageGenerationRequest request = new ImageGenerationRequest(model, prompt, size);
        request.response_format = "b64_json";

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.openai.com/") // Dummy base
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        OpenAiApi api = retrofit.create(OpenAiApi.class);

        try {
            Log.d(TAG, "Requesting image generation for message from: " + requestUrl);
            
            Call<ImageGenerationResponse> call = api.generateImage(requestUrl, "Bearer " + key, request);
            Response<ImageGenerationResponse> response = call.execute();
            
            if (response.isSuccessful() && response.body() != null && response.body().data != null && !response.body().data.isEmpty()) {
                ImageGenerationResponse.ImageData imageData = response.body().data.get(0);
                String generatedImageUrl = imageData.url;
                String generatedB64Json = imageData.b64_json;

                if (!TextUtils.isEmpty(generatedB64Json)) {
                    String base64Data = generatedB64Json;
                    if (base64Data.contains(",")) {
                        base64Data = base64Data.substring(base64Data.indexOf(",") + 1);
                    }
                    byte[] imageBytes = Base64.decode(base64Data, Base64.DEFAULT);
                    saveImageAndUpdateMessage(message.id, imageBytes);
                } else if (!TextUtils.isEmpty(generatedImageUrl)) {
                    downloadImageAndUpdateMessage(message.id, generatedImageUrl);
                }
            } else {
                markMessageImageGenerationError(message.id, originalDesc);
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception during message image generation", e);
            markMessageImageGenerationError(message.id, originalDesc);
        }
    }

    private void markMessageImageGenerationError(int messageId, String originalDesc) {
        AppDatabase db = AppDatabase.getDatabase(context);
        com.yoyo.jingxi.data.entity.Message message = db.messageDao().getMessageByIdSync(messageId);
        if (message != null) {
            message.imageDesc = "error://" + originalDesc;
            db.messageDao().update(message);
            // 这里可以发送广播通知更新UI，如果需要的话
        }
    }

    private void saveImageAndUpdateMessage(int messageId, byte[] imageBytes) {
        try {
            File dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "messages");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String fileName = "msg_gen_" + System.currentTimeMillis() + ".png";
            File file = new File(dir, fileName);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(imageBytes);
            fos.close();

            updateMessageDatabase(messageId, file.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to save generated message image", e);
        }
    }

    private void downloadImageAndUpdateMessage(int messageId, String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            Bitmap bitmap = BitmapFactory.decodeStream(input);

            File dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "messages");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String fileName = "msg_gen_" + System.currentTimeMillis() + ".png";
            File file = new File(dir, fileName);
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();

            updateMessageDatabase(messageId, file.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to download generated message image", e);
        }
    }

    private void updateMessageDatabase(int messageId, String localPath) {
        AppDatabase db = AppDatabase.getDatabase(context);
        com.yoyo.jingxi.data.entity.Message message = db.messageDao().getMessageByIdSync(messageId);
        
        if (message != null) {
            message.type = 3; // 转换为真实图片
            message.imageUrl = localPath;
            db.messageDao().update(message);
            // Notify UI
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                Intent broadcastIntent = new Intent("com.yoyo.jingxi.ACTION_MESSAGE_UPDATED");
                context.sendBroadcast(broadcastIntent);
            });
        }
    }

    private void doGenerateImage(Moment moment, int index, String prompt, String originalUrl) {
        if (!SpUtils.getBoolean("ENABLE_IMAGE_GEN", false)) {
            Log.d(TAG, "Image generation is disabled, skipping doGenerateImage");
            markImageGenerationError(moment.id, index, originalUrl);
            return;
        }

        String endpoint = SpUtils.getString("IMAGE_API_ENDPOINT", "https://api.openai.com/");
        String key = SpUtils.getString("IMAGE_API_KEY", "");
        String model = SpUtils.getString("IMAGE_API_MODEL", "dall-e-3");

        if (TextUtils.isEmpty(endpoint) || TextUtils.isEmpty(key)) {
            Log.e(TAG, "API not configured properly. endpoint=" + endpoint + ", key=" + (TextUtils.isEmpty(key) ? "empty" : "has_value"));
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                android.widget.Toast.makeText(context, "生图API未配置，请检查设置。Endpoint/Key是否为空", android.widget.Toast.LENGTH_LONG).show();
            });
            return;
        }
        
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            android.widget.Toast.makeText(context, "开始请求生图API: " + model, android.widget.Toast.LENGTH_SHORT).show();
        });

        if (!endpoint.endsWith("/")) {
            endpoint += "/";
        }
        String requestUrl = endpoint + "v1/images/generations";

        String size = "1024x1024";
        try {
            // originalUrl is like "virtual://{"desc":"...","size":"..."}"
            String jsonStr = originalUrl;
            if (jsonStr.startsWith("virtual://")) {
                jsonStr = android.net.Uri.decode(jsonStr.substring("virtual://".length()));
            }
            JSONObject json = new JSONObject(jsonStr);
            if (json.has("size")) {
                size = json.getString("size");
            }
        } catch (Exception e) {
            // Not JSON or size not found
        }

        ImageGenerationRequest request = new ImageGenerationRequest(model, prompt, size);
        request.response_format = "b64_json";

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.openai.com/") // Dummy base
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        OpenAiApi api = retrofit.create(OpenAiApi.class);

        try {
            Log.d(TAG, "Requesting image generation from: " + requestUrl);
            Log.d(TAG, "Model: " + model + ", Prompt: " + prompt);
            
            Call<ImageGenerationResponse> call = api.generateImage(requestUrl, "Bearer " + key, request);
            Response<ImageGenerationResponse> response = call.execute();
            
            if (response.isSuccessful() && response.body() != null && response.body().data != null && !response.body().data.isEmpty()) {
                // 不再在此处进行生图 Toast 提示，以免在后台一直弹窗
                ImageGenerationResponse.ImageData imageData = response.body().data.get(0);
                String generatedImageUrl = imageData.url;
                String generatedB64Json = imageData.b64_json;

                if (!TextUtils.isEmpty(generatedB64Json)) {
                    // 处理可能包含的 data:image/png;base64, 前缀
                    String base64Data = generatedB64Json;
                    if (base64Data.contains(",")) {
                        base64Data = base64Data.substring(base64Data.indexOf(",") + 1);
                    }
                    byte[] imageBytes = Base64.decode(base64Data, Base64.DEFAULT);
                    saveImageAndUpdateMoment(moment.id, index, imageBytes);
                } else if (!TextUtils.isEmpty(generatedImageUrl)) {
                    downloadImageAndUpdateMoment(moment.id, index, generatedImageUrl);
                }
            } else {
                String errorBody = "";
                try {
                    if (response.errorBody() != null) {
                        errorBody = response.errorBody().string();
                    }
                } catch (Exception ignored) {}
                Log.e(TAG, "Failed to generate image: " + response.code() + ", errorBody: " + errorBody);
                // final String finalError = errorBody;
                // new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                //     android.widget.Toast.makeText(context, "生图失败: " + response.code() + " " + finalError, android.widget.Toast.LENGTH_LONG).show();
                // });
                markImageGenerationError(moment.id, index, originalUrl);
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception during image generation", e);
            // final String errMsg = e.getMessage();
            // new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            //     android.widget.Toast.makeText(context, "生图异常: " + errMsg, android.widget.Toast.LENGTH_LONG).show();
            // });
            markImageGenerationError(moment.id, index, originalUrl);
        }
    }

    private void markImageGenerationError(int momentId, int imageIndex, String originalUrl) {
        String errorUrl = originalUrl.replace("virtual://", "error://");
        updateDatabase(momentId, imageIndex, errorUrl);
    }

    private void saveImageAndUpdateMoment(int momentId, int imageIndex, byte[] imageBytes) {
        try {
            File dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "moments");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String fileName = "moment_gen_" + System.currentTimeMillis() + ".png";
            File file = new File(dir, fileName);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(imageBytes);
            fos.close();

            updateDatabase(momentId, imageIndex, file.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to save generated image", e);
        }
    }

    private void downloadImageAndUpdateMoment(int momentId, int imageIndex, String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            Bitmap bitmap = BitmapFactory.decodeStream(input);

            File dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "moments");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String fileName = "moment_gen_" + System.currentTimeMillis() + ".png";
            File file = new File(dir, fileName);
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();

            updateDatabase(momentId, imageIndex, file.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to download generated image", e);
        }
    }

    private void updateDatabase(int momentId, int imageIndex, String localPath) {
        AppDatabase db = AppDatabase.getDatabase(context);
        Moment moment = db.momentDao().getMomentByIdSync(momentId);
        
        if (moment != null && moment.imageUrl != null) {
            String[] urls = moment.imageUrl.split(",");
            if (imageIndex >= 0 && imageIndex < urls.length) {
                urls[imageIndex] = localPath;
                
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < urls.length; i++) {
                    sb.append(urls[i]);
                    if (i < urls.length - 1) {
                        sb.append(",");
                    }
                }
                moment.imageUrl = sb.toString();
                db.momentDao().update(moment);
                
                // Notify UI
                Intent broadcastIntent = new Intent("com.yoyo.jingxi.ACTION_MOMENT_UPDATED");
                context.sendBroadcast(broadcastIntent);
            }
        }
    }
}
