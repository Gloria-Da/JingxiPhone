package com.yoyo.jingxi.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import com.yoyo.jingxi.R;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.yoyo.jingxi.ui.fragment.ChatFragment;
import com.yoyo.jingxi.ui.fragment.FriendsFragment;
import com.yoyo.jingxi.ui.fragment.MeFragment;
import com.yoyo.jingxi.ui.fragment.MomentsFragment;

public class ChatMainActivity extends AppCompatActivity {

    private Toolbar toolbar;
    
    private Fragment chatFragment;
    private Fragment friendsFragment;
    private Fragment momentsFragment;
    private Fragment meFragment;
    private Fragment activeFragment;
    private int activeIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.yoyo.jingxi.utils.ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_main);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("消息");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        
        // 观察总未读消息数并在底部导航栏显示小红点
        com.yoyo.jingxi.data.AppDatabase.getDatabase(this).chatSessionDao().getAllSessions().observe(this, sessions -> {
            if (sessions != null) {
                int count = 0;
                for (com.yoyo.jingxi.data.entity.ChatSession session : sessions) {
                    count += session.unreadCount;
                }
                if (count > 0) {
                    com.google.android.material.badge.BadgeDrawable badge = bottomNav.getOrCreateBadge(R.id.nav_chat);
                    badge.setVisible(true);
                    badge.setNumber(count);
                    badge.setBackgroundColor(getResources().getColor(android.R.color.holo_red_dark, null));
                    badge.setBadgeTextColor(getResources().getColor(android.R.color.white, null));
                } else {
                    bottomNav.removeBadge(R.id.nav_chat);
                }
            }
        });

        if (savedInstanceState == null) {
            chatFragment = new ChatFragment();
            friendsFragment = new FriendsFragment();
            momentsFragment = new MomentsFragment();
            meFragment = new MeFragment();

            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, meFragment, "ME").hide(meFragment)
                    .add(R.id.fragment_container, momentsFragment, "MOMENTS").hide(momentsFragment)
                    .add(R.id.fragment_container, friendsFragment, "FRIENDS").hide(friendsFragment)
                    .add(R.id.fragment_container, chatFragment, "CHAT")
                    .commit();
            
            activeFragment = chatFragment;
            activeIndex = 0;
            bottomNav.setSelectedItemId(R.id.nav_chat);
        } else {
            chatFragment = getSupportFragmentManager().findFragmentByTag("CHAT");
            friendsFragment = getSupportFragmentManager().findFragmentByTag("FRIENDS");
            momentsFragment = getSupportFragmentManager().findFragmentByTag("MOMENTS");
            meFragment = getSupportFragmentManager().findFragmentByTag("ME");
            
            // 恢复 activeFragment
            int selectedId = bottomNav.getSelectedItemId();
            if (selectedId == R.id.nav_chat) { activeFragment = chatFragment; activeIndex = 0; }
            else if (selectedId == R.id.nav_friends) { activeFragment = friendsFragment; activeIndex = 1; }
            else if (selectedId == R.id.nav_moments) { activeFragment = momentsFragment; activeIndex = 2; }
            else if (selectedId == R.id.nav_me) { activeFragment = meFragment; activeIndex = 3; }
        }

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int newIndex = 0;
            int itemId = item.getItemId();
            if (itemId == R.id.nav_chat) {
                selectedFragment = chatFragment;
                newIndex = 0;
                if (getSupportActionBar() != null) {
                    getSupportActionBar().show();
                    getSupportActionBar().setTitle("消息");
                }
                invalidateOptionsMenu(); // Force recreate options menu
            } else if (itemId == R.id.nav_friends) {
                selectedFragment = friendsFragment;
                newIndex = 1;
                if (getSupportActionBar() != null) {
                    getSupportActionBar().show();
                    getSupportActionBar().setTitle("通讯录");
                }
                invalidateOptionsMenu();
            } else if (itemId == R.id.nav_moments) {
                selectedFragment = momentsFragment;
                newIndex = 2;
                if (getSupportActionBar() != null) {
                    getSupportActionBar().show();
                    getSupportActionBar().setTitle("动态");
                }
                invalidateOptionsMenu();
            } else if (itemId == R.id.nav_me) {
                selectedFragment = meFragment;
                newIndex = 3;
                if (getSupportActionBar() != null) {
                    getSupportActionBar().show();
                    getSupportActionBar().setTitle("我的");
                }
                invalidateOptionsMenu();
            }
            
            if (selectedFragment != null && activeFragment != selectedFragment) {
                int enterAnim = 0;
                int exitAnim = 0;
                if (newIndex > activeIndex) {
                    enterAnim = R.anim.slide_in_right;
                    exitAnim = R.anim.slide_out_left;
                } else if (newIndex < activeIndex) {
                    enterAnim = R.anim.slide_in_left;
                    exitAnim = R.anim.slide_out_right;
                }

                getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(enterAnim, exitAnim)
                        .hide(activeFragment)
                        .show(selectedFragment)
                        .commit();
                activeFragment = selectedFragment;
                activeIndex = newIndex;
                return true;
            }
            return true;
        });
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // Only show add button when in Chat or Friends fragment
        MenuItem addItem = menu.findItem(R.id.action_add);
        if (addItem != null) {
            BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
            int selectedId = bottomNav.getSelectedItemId();
            addItem.setVisible(selectedId == R.id.nav_chat || selectedId == R.id.nav_friends);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.chat_main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_add) {
            BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
            if (bottomNav.getSelectedItemId() == R.id.nav_chat) {
                startActivity(new Intent(this, CreateChatActivity.class));
            } else if (bottomNav.getSelectedItemId() == R.id.nav_friends) {
                startActivity(new Intent(this, AddFriendActivity.class));
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
