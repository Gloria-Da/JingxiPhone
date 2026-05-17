package com.yoyo.jingxi.ui.activity;

import android.os.Bundle;
import com.yoyo.jingxi.R;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.content.Intent;
import android.net.Uri;
import android.widget.ImageView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.bumptech.glide.Glide;
import com.google.android.material.textfield.TextInputEditText;
import com.yalantis.ucrop.UCrop;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.MyPersona;

import java.io.File;

public class AddPersonaActivity extends AppCompatActivity {

    private ImageView ivAvatarInput;
    private TextInputEditText etPersonaName;
    private TextInputEditText etGender;
    private TextInputEditText etPersonaDesc;
    private CheckBox cbIsMain;
    private Button btnSavePersona;

    private String currentAvatarUri = null;

    private final ActivityResultLauncher<String> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    startCrop(uri);
                }
            }
    );

    private final ActivityResultLauncher<Intent> cropImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri resultUri = UCrop.getOutput(result.getData());
                    if (resultUri != null) {
                        currentAvatarUri = resultUri.toString();
                        if (!isFinishing() && !isDestroyed()) {
                            Glide.with(AddPersonaActivity.this.getApplicationContext())
                                    .load(currentAvatarUri)
                                    .circleCrop()
                                    .placeholder(R.drawable.ic_launcher_round)
                                    .into(ivAvatarInput);
                        }
                    }
                } else if (result.getResultCode() == UCrop.RESULT_ERROR && result.getData() != null) {
                    Throwable cropError = UCrop.getError(result.getData());
                    if (cropError != null) {
                        Toast.makeText(this, "裁剪失败: " + cropError.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    private void startCrop(Uri sourceUri) {
        String destinationFileName = "avatar_persona_" + System.currentTimeMillis() + ".jpg";
        Uri destinationUri = Uri.fromFile(new File(getCacheDir(), destinationFileName));

        UCrop.Options options = new UCrop.Options();
        options.setCircleDimmedLayer(true);
        options.setShowCropFrame(false);
        options.setShowCropGrid(false);

        Intent intent = UCrop.of(sourceUri, destinationUri)
                .withAspectRatio(1, 1)
                .withMaxResultSize(500, 500)
                .withOptions(options)
                .getIntent(this);

        cropImageLauncher.launch(intent);
    }

    private boolean isEditMode = false;
    private String originalName = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.yoyo.jingxi.utils.ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_persona);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        ivAvatarInput = findViewById(R.id.ivAvatarInput);
        etPersonaName = findViewById(R.id.etPersonaName);
        etGender = findViewById(R.id.etGender);
        etPersonaDesc = findViewById(R.id.etPersonaDesc);
        cbIsMain = findViewById(R.id.cbIsMain);
        btnSavePersona = findViewById(R.id.btnSavePersona);

        ivAvatarInput.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        if (getIntent().hasExtra("persona_name")) {
            isEditMode = true;
            originalName = getIntent().getStringExtra("persona_name");
            etPersonaName.setText(originalName);
            etPersonaName.setEnabled(false); // Edit mode cannot change primary key name easily right now
            toolbar.setTitle("编辑人设");
            loadPersonaData(originalName);
        } else {
            toolbar.setTitle("新建人设");
        }

        btnSavePersona.setOnClickListener(v -> savePersona());
    }

    private void loadPersonaData(String name) {
        new Thread(() -> {
            MyPersona persona = AppDatabase.getDatabase(this).myPersonaDao().getMyPersonaByName(name);
            if (persona != null) {
                runOnUiThread(() -> {
                    String loadedPersona = persona.persona;
                    if (loadedPersona != null && loadedPersona.startsWith("[性别: ")) {
                        int endIdx = loadedPersona.indexOf("] ");
                        if (endIdx != -1) {
                            String genderStr = loadedPersona.substring(5, endIdx);
                            etGender.setText(genderStr);
                            loadedPersona = loadedPersona.substring(endIdx + 2);
                        }
                    }
                    etPersonaDesc.setText(loadedPersona);
                    cbIsMain.setChecked(persona.isMainPersona);
                    if (persona.avatarPath != null && !persona.avatarPath.isEmpty()) {
                        currentAvatarUri = persona.avatarPath;
                        if (!isFinishing() && !isDestroyed()) {
                            Glide.with(AddPersonaActivity.this.getApplicationContext())
                                    .load(currentAvatarUri)
                                    .circleCrop()
                                    .placeholder(R.drawable.ic_launcher_round)
                                    .into(ivAvatarInput);
                        }
                    }
                });
            }
        }).start();
    }

    private void savePersona() {
        String name = etPersonaName.getText() != null ? etPersonaName.getText().toString().trim() : "";
        String gender = etGender.getText() != null ? etGender.getText().toString().trim() : "";
        String desc = etPersonaDesc.getText() != null ? etPersonaDesc.getText().toString().trim() : "";
        boolean isMain = cbIsMain.isChecked();

        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "请输入人设名称", Toast.LENGTH_SHORT).show();
            return;
        }
        
        final String finalPersonaStr;
        if (!gender.isEmpty()) {
            finalPersonaStr = "[性别: " + gender + "] " + desc;
        } else {
            finalPersonaStr = desc;
        }

        new Thread(() -> {
            AppDatabase db = AppDatabase.getDatabase(this);
            
            if (isMain) {
                db.myPersonaDao().clearAllMainStatus();
            }

            MyPersona persona = new MyPersona();
            persona.name = name;
            persona.persona = finalPersonaStr;
            persona.isMainPersona = isMain;
            persona.avatarPath = currentAvatarUri;

            if (isEditMode) {
                MyPersona existing = db.myPersonaDao().getMyPersonaByName(name);
                if (existing != null) {
                    existing.persona = desc;
                    existing.isMainPersona = isMain;
                    existing.avatarPath = currentAvatarUri;
                    db.myPersonaDao().update(existing);
                } else {
                    db.myPersonaDao().update(persona);
                }
            } else {
                MyPersona existing = db.myPersonaDao().getMyPersonaByName(name);
                if (existing != null) {
                    runOnUiThread(() -> Toast.makeText(this, "该人设名称已存在", Toast.LENGTH_SHORT).show());
                    return;
                }
                db.myPersonaDao().insert(persona);
            }

            // Trigger an update for MeFragment if it's currently showing
            Intent updateIntent = new Intent("com.yoyo.jingxi.UPDATE_MAIN_PERSONA");
            sendBroadcast(updateIntent);

            runOnUiThread(() -> {
                Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
                finish();
            });
        }).start();
    }
}