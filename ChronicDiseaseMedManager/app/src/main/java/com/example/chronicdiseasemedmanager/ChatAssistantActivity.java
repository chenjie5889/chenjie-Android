package com.example.chronicdiseasemedmanager;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ChatAssistantActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private EditText etInput;
    private ImageButton btnSend;
    private Toolbar toolbar; // 改为Toolbar类型
    private MessageAdapter messageAdapter;
    private List<Message> messageList = new ArrayList<>();

    private AliApiService aliApiService;
    private static final String BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/";
    private static final String API_KEY = "sk-9b90f9b46c024a19ae8d4acff9a1839c";
    private static final String MODEL = "qwen-turbo";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_assistant);

        // 初始化View
        initViews();
        // 初始化Retrofit
        initRetrofit();
        // 设置Toolbar
        setupToolbar();
        // 设置监听器
        setupListeners();
        // 添加欢迎消息
        addWelcomeMessage();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar); // 初始化Toolbar
        recyclerView = findViewById(R.id.recyclerView);
        etInput = findViewById(R.id.etInput);
        btnSend = findViewById(R.id.btnSend);

        // 设置RecyclerView
        messageAdapter = new MessageAdapter(messageList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(messageAdapter);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);

        // 设置返回按钮可见
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("智能用药助手");
        }

        // 设置返回按钮点击事件
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed(); // 调用返回方法
            }
        });
    }

    private void initRetrofit() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        aliApiService = retrofit.create(AliApiService.class);
    }

    private void setupListeners() {
        btnSend.setOnClickListener(v -> sendMessage());

        etInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnSend.setEnabled(!TextUtils.isEmpty(s.toString().trim()));
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // 点击发送按钮时关闭软键盘
        btnSend.setOnClickListener(v -> {
            sendMessage();
            // 关闭软键盘
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(etInput.getWindowToken(), 0);
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onBackPressed() {
        // 如果有软键盘打开，先关闭软键盘
        View view = this.getCurrentFocus();
        if (view != null) {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }

        // 执行返回操作
        super.onBackPressed();

        // 添加返回动画
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
    }

    private void addWelcomeMessage() {
        String welcomeMsg = "您好！我是您的慢性病用药管理助手。\n\n我可以帮助您解答关于：\n• 用药咨询\n• 药品信息\n• 服药时间安排\n• 副作用处理\n• 饮食注意事项\n• 健康管理建议\n\n请问有什么可以帮您的吗？";
        messageList.add(new Message(Message.TYPE_ASSISTANT, welcomeMsg));
        messageAdapter.notifyDataSetChanged();
        scrollToBottom();
    }

    private void sendMessage() {
        String inputText = etInput.getText().toString().trim();
        if (TextUtils.isEmpty(inputText)) {
            return;
        }

        // 添加用户消息
        Message userMessage = new Message(Message.TYPE_USER, inputText);
        messageList.add(userMessage);
        messageAdapter.notifyItemInserted(messageList.size() - 1);

        // 清空输入框
        etInput.setText("");

        // 添加加载中的消息
        Message loadingMessage = new Message(Message.TYPE_LOADING, "正在思考中...");
        messageList.add(loadingMessage);
        messageAdapter.notifyItemInserted(messageList.size() - 1);

        // 滚动到底部
        scrollToBottom();

        // 调用API
        callAliApi(inputText);
    }

    private void callAliApi(String userInput) {
        try {
            // 构建消息列表
            List<AliApiRequest.MessageContent> messages = new ArrayList<>();

            // 系统提示词 - 指定AI的角色
            String systemPrompt = "你是一位专业的慢性病用药管理助手，专门为慢性病患者提供用药指导和健康管理建议。" +
                    "请以专业、温暖、易懂的方式回答用户的问题。\n\n" +
                    "你的专长包括：\n" +
                    "1. 解释药品的作用和用法\n" +
                    "2. 安排合理的服药时间\n" +
                    "3. 提醒可能的副作用和应对方法\n" +
                    "4. 提供饮食和生活建议\n" +
                    "5. 解答用药疑惑\n" +
                    "6. 强调按时服药的重要性\n\n" +
                    "请记住：如果用户的问题超出你的知识范围或涉及紧急医疗情况，请提醒用户及时就医。";

            messages.add(new AliApiRequest.MessageContent("system", systemPrompt));

            // 添加上下文（最近的几条消息）
            int contextCount = Math.min(6, messageList.size() - 1); // 排除当前的消息
            for (int i = Math.max(0, messageList.size() - contextCount - 1); i < messageList.size() - 1; i++) {
                Message msg = messageList.get(i);
                String role = msg.getType() == Message.TYPE_USER ? "user" : "assistant";
                messages.add(new AliApiRequest.MessageContent(role, msg.getContent()));
            }

            // 添加当前用户消息
            messages.add(new AliApiRequest.MessageContent("user", userInput));

            // 构建请求
            AliApiRequest request = new AliApiRequest(MODEL, messages);

            // 打印请求日志（调试用）
            System.out.println("Sending request to Ali API: " + new Gson().toJson(request));

            // 设置请求头
            String authHeader = "Bearer " + API_KEY;
            String contentType = "application/json";

            // 发送请求
            Call<AliApiResponse> call = aliApiService.chatCompletion(authHeader, contentType, request);

            call.enqueue(new Callback<AliApiResponse>() {
                @Override
                public void onResponse(Call<AliApiResponse> call, Response<AliApiResponse> response) {
                    // 移除加载中的消息
                    messageList.remove(messageList.size() - 1);
                    messageAdapter.notifyItemRemoved(messageList.size());

                    if (response.isSuccessful() && response.body() != null) {
                        AliApiResponse apiResponse = response.body();
                        if (apiResponse.getChoices() != null && apiResponse.getChoices().length > 0) {
                            String assistantReply = apiResponse.getChoices()[0].getMessage().getContent();

                            // 添加助手回复
                            Message assistantMessage = new Message(Message.TYPE_ASSISTANT, assistantReply);
                            messageList.add(assistantMessage);
                            messageAdapter.notifyItemInserted(messageList.size() - 1);

                            // 滚动到底部
                            scrollToBottom();
                        } else {
                            showError("AI返回格式异常");
                        }
                    } else {
                        try {
                            String errorBody = response.errorBody() != null ?
                                    response.errorBody().string() : "Unknown error";
                            System.out.println("API Error: " + errorBody);
                            showError("请求失败: " + response.code() + " - " + errorBody);
                        } catch (Exception e) {
                            showError("请求失败: " + response.code());
                        }
                    }
                }

                @Override
                public void onFailure(Call<AliApiResponse> call, Throwable t) {
                    // 移除加载中的消息
                    messageList.remove(messageList.size() - 1);
                    messageAdapter.notifyItemRemoved(messageList.size());

                    System.out.println("API Call failed: " + t.getMessage());
                    t.printStackTrace();
                    showError("网络连接失败，请检查网络");
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            showError("请求异常: " + e.getMessage());

            // 移除加载中的消息
            if (messageList.size() > 0 && messageList.get(messageList.size() - 1).getType() == Message.TYPE_LOADING) {
                messageList.remove(messageList.size() - 1);
                messageAdapter.notifyItemRemoved(messageList.size());
            }
        }
    }

    private void scrollToBottom() {
        recyclerView.postDelayed(() -> {
            if (messageList.size() > 0) {
                recyclerView.smoothScrollToPosition(messageList.size() - 1);
            }
        }, 100);
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

        // 添加错误提示消息
        Message errorMessage = new Message(Message.TYPE_ASSISTANT,
                "抱歉，我遇到了点问题：" + message + "\n\n请检查网络连接后重试，或稍后再试。");
        messageList.add(errorMessage);
        messageAdapter.notifyItemInserted(messageList.size() - 1);
        scrollToBottom();
    }
}