package com.yoyo.jingxi.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import com.yoyo.jingxi.R;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.Character;
import com.yoyo.jingxi.data.entity.ChatSession;
import com.yoyo.jingxi.data.entity.MyPersona;

import java.util.ArrayList;
import java.util.List;

public class CreateChatActivity extends AppCompatActivity {

    private Spinner spinnerFriend;
    private Spinner spinnerMyPersona;
    private Button btnStartChat;

    private List<Character> friends = new ArrayList<>();
    private List<MyPersona> personas = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.yoyo.jingxi.utils.ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_chat);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        spinnerFriend = findViewById(R.id.spinnerFriend);
        spinnerMyPersona = findViewById(R.id.spinnerMyPersona);
        btnStartChat = findViewById(R.id.btnStartChat);

        loadData();

        btnStartChat.setOnClickListener(v -> {
            int friendPos = spinnerFriend.getSelectedItemPosition();
            int personaPos = spinnerMyPersona.getSelectedItemPosition();

            if (friendPos < 0 || personaPos < 0) {
                Toast.makeText(this, "请选择好友和我的人设", Toast.LENGTH_SHORT).show();
                return;
            }

            Character selectedFriend = friends.get(friendPos);
            MyPersona selectedPersona = personas.get(personaPos);

            new Thread(() -> {
                AppDatabase db = AppDatabase.getDatabase(this);
                ChatSession session = new ChatSession();
                session.characterId = selectedFriend.id;
                session.myPersonaName = selectedPersona.name;
                
                long sessionId = db.chatSessionDao().insert(session);
                
                runOnUiThread(() -> {
                    Intent intent = new Intent(this, ChatActivity.class);
                    intent.putExtra("session_id", (int)sessionId);
                    intent.putExtra("friend_name", selectedFriend.name);
                    startActivity(intent);
                    finish();
                });
            }).start();
        });
    }

    private void loadData() {
        AppDatabase db = AppDatabase.getDatabase(this);
        
        db.characterDao().getAllCharacters().observe(this, chars -> {
            this.friends = chars;
            List<String> names = new ArrayList<>();
            for (Character c : chars) {
                names.add(c.name);
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerFriend.setAdapter(adapter);
        });

        db.myPersonaDao().getAllMyPersonas().observe(this, pList -> {
            this.personas = pList;
            List<String> names = new ArrayList<>();
            int mainIndex = -1;
            for (int i = 0; i < pList.size(); i++) {
                MyPersona p = pList.get(i);
                names.add(p.name + (p.isMainPersona ? " (主人设)" : ""));
                if (p.isMainPersona) mainIndex = i;
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerMyPersona.setAdapter(adapter);
            if (mainIndex >= 0) {
                spinnerMyPersona.setSelection(mainIndex);
            }
        });
    }
}