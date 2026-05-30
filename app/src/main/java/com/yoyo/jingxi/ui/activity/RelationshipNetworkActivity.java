package com.yoyo.jingxi.ui.activity;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import android.view.Menu;
import android.view.MenuItem;

import com.yoyo.jingxi.R;
import com.yoyo.jingxi.data.AppDatabase;
import com.yoyo.jingxi.data.entity.RelationshipEdge;
import com.yoyo.jingxi.data.entity.RelationshipNode;
import com.yoyo.jingxi.utils.RelationshipExtractionManager;

import java.util.List;

public class RelationshipNetworkActivity extends AppCompatActivity {

    private WebView webView;
    private AppDatabase db;
    private Gson gson = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.yoyo.jingxi.utils.ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_relationship_network);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        db = AppDatabase.getDatabase(this);
        webView = findViewById(R.id.webView);
        
        setupWebView();

        FloatingActionButton fab = findViewById(R.id.fab_add_relationship);
        fab.setOnClickListener(v -> showAddRelationshipDialog());
        
        loadNetworkData();
    }

    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        
        // 注入 Android 接口给 JS 调用
        webView.addJavascriptInterface(new WebAppInterface(this), "Android");
        
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // 页面加载完成后，如果数据已经准备好，可以再次刷新
                loadNetworkData();
            }
        });
        
        webView.loadUrl("file:///android_asset/relationship_graph.html");
    }

    private void loadNetworkData() {
        db.relationshipNodeDao().getAllNodes().observe(this, nodes -> {
            db.relationshipEdgeDao().getAllEdges().observe(this, edges -> {
                if (nodes != null && edges != null) {
                    String nodesJson = gson.toJson(nodes);
                    String edgesJson = gson.toJson(edges);
                    
                    // 将数据发送给 WebView
                    String jsCode = String.format("javascript:renderGraph('%s', '%s')", 
                            nodesJson.replace("'", "\\'"), 
                            edgesJson.replace("'", "\\'"));
                            
                    webView.evaluateJavascript(jsCode, null);
                }
            });
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_relationship, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_clear_all) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("清空全部")
                    .setMessage("确定要清空所有的人际关系网络数据吗？此操作不可恢复。")
                    .setPositiveButton("确定清空", (dialog, which) -> {
                        new Thread(() -> {
                            db.relationshipEdgeDao().deleteAll();
                            db.relationshipNodeDao().deleteAll();
                            runOnUiThread(() -> {
                                Toast.makeText(this, "数据已清空", Toast.LENGTH_SHORT).show();
                                loadNetworkData();
                            });
                        }).start();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showAddRelationshipDialog() {
        final EditText input = new EditText(this);
        input.setHint("输入一段描述让 AI 自动提取人物关系（支持创建新人物和更新现有角色信息），例如：张三是李四的师傅，张三性格沉稳，王五暗恋李四...");
        input.setMinLines(3);
        input.setMaxLines(10);
        input.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);

        new MaterialAlertDialogBuilder(this)
                .setTitle("AI 录入人际关系")
                .setMessage("输入一段描述，AI将自动提取人物和关系网络。")
                .setView(input)
                .setPositiveButton("提取", (dialog, which) -> {
                    String text = input.getText().toString().trim();
                    if (!text.isEmpty()) {
                        Toast.makeText(RelationshipNetworkActivity.this, "正在通过 AI 分析提取...", Toast.LENGTH_SHORT).show();
                        RelationshipExtractionManager.extractRelationships(text, db, new RelationshipExtractionManager.ExtractionCallback() {
                            @Override
                            public void onSuccess() {
                                Toast.makeText(RelationshipNetworkActivity.this, "提取成功！图谱已更新。", Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onError(String error) {
                                Toast.makeText(RelationshipNetworkActivity.this, "提取失败: " + error, Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 供 JS 调用的接口
    public class WebAppInterface {
        Context mContext;

        WebAppInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void onNodeClicked(String id, String name, String description, int category) {
            runOnUiThread(() -> {
                String finalDesc = (description == null || description.isEmpty()) ? "暂无简介" : description;
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(mContext)
                        .setTitle(name)
                        .setMessage(finalDesc);

                if (category == 2) {
                    // 如果是纯虚拟人物，允许编辑和删除
                    builder.setNeutralButton("删除", (dialog, which) -> {
                        new Thread(() -> {
                            db.relationshipNodeDao().deleteNodeById(id);
                            // 同时删除相关边
                            db.relationshipEdgeDao().deleteEdgesByNodeId(id);
                            runOnUiThread(() -> loadNetworkData()); // Refresh
                        }).start();
                        Toast.makeText(mContext, "已删除", Toast.LENGTH_SHORT).show();
                    });
                    
                    builder.setNegativeButton("编辑", (dialog, which) -> {
                        showEditNodeDialog(id, name, description, category);
                    });
                } else {
                    // 非虚拟人物也可以编辑简介
                    builder.setNegativeButton("编辑简介", (dialog, which) -> {
                        showEditNodeDialog(id, name, description, category);
                    });
                }
                builder.setPositiveButton("确定", null)
                       .show();
            });
        }
        
        @JavascriptInterface
        public void onEdgeClicked(int id, String sourceNodeId, String targetNodeId, String relation) {
            runOnUiThread(() -> {
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(mContext)
                        .setTitle("关系信息")
                        .setMessage(relation);
                        
                builder.setNeutralButton("删除此关系", (dialog, which) -> {
                    new Thread(() -> {
                        RelationshipEdge edge = new RelationshipEdge();
                        edge.id = id;
                        db.relationshipEdgeDao().delete(edge);
                        runOnUiThread(() -> loadNetworkData()); // Refresh
                    }).start();
                    Toast.makeText(mContext, "关系已删除", Toast.LENGTH_SHORT).show();
                });
                
                builder.setNegativeButton("编辑", (dialog, which) -> {
                    showEditEdgeDialog(id, sourceNodeId, targetNodeId, relation);
                });
                
                builder.setPositiveButton("确定", null).show();
            });
        }
    }
    
    private void showEditNodeDialog(String id, String name, String currentDescription, int category) {
        final EditText input = new EditText(this);
        input.setText(category == 2 ? name + "|" + currentDescription : currentDescription);
        input.setHint(category == 2 ? "输入格式: 名字|简介，或仅输入简介..." : "输入人物简介...");
        input.setMinLines(2);
        input.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);

        new MaterialAlertDialogBuilder(this)
                .setTitle("编辑 " + name + " 的简介")
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    String newDesc = input.getText().toString().trim();
                    new Thread(() -> {
                        RelationshipNode node = db.relationshipNodeDao().getNodeById(id);
                        if (node != null) {
                            if (category == 2 && newDesc.contains("|")) {
                                String[] parts = newDesc.split("\\|", 2);
                                node.name = parts[0].trim();
                                node.description = parts[1].trim();
                            } else {
                                node.description = newDesc;
                            }
                            db.relationshipNodeDao().update(node);
                            runOnUiThread(() -> {
                                Toast.makeText(this, "简介已更新", Toast.LENGTH_SHORT).show();
                                loadNetworkData();
                            });
                        }
                    }).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }
    
    private void showEditEdgeDialog(int edgeId, String sourceNodeId, String targetNodeId, String currentRelation) {
        final EditText input = new EditText(this);
        input.setText(currentRelation);
        input.setHint("输入他们之间的关系，例如：师徒、朋友...");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);

        new MaterialAlertDialogBuilder(this)
                .setTitle("编辑关系")
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    String newRelation = input.getText().toString().trim();
                    if (!newRelation.isEmpty()) {
                        new Thread(() -> {
                            // 由于Room在Dao中只提供了简单的update(RelationshipEdge edge)，需要构建完整对象
                            // 这里我们直接创建一个带ID的Edge进行Update。注意不要覆盖了其他必填字段。
                            RelationshipEdge edgeToUpdate = new RelationshipEdge();
                            edgeToUpdate.id = edgeId;
                            edgeToUpdate.sourceNodeId = sourceNodeId;
                            edgeToUpdate.targetNodeId = targetNodeId;
                            edgeToUpdate.relation = newRelation;
                            
                            db.relationshipEdgeDao().update(edgeToUpdate);
                            runOnUiThread(() -> {
                                Toast.makeText(this, "关系已更新", Toast.LENGTH_SHORT).show();
                                loadNetworkData();
                            });
                        }).start();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
}