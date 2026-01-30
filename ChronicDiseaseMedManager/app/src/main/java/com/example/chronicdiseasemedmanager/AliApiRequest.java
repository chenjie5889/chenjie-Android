package com.example.chronicdiseasemedmanager;

import java.util.List;

public class AliApiRequest {
    private String model; // 添加model参数
    private List<MessageContent> messages;
    private boolean stream = false;

    public AliApiRequest() {}

    public AliApiRequest(String model, List<MessageContent> messages) {
        this.model = model;
        this.messages = messages;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<MessageContent> getMessages() {
        return messages;
    }

    public void setMessages(List<MessageContent> messages) {
        this.messages = messages;
    }

    public boolean isStream() {
        return stream;
    }

    public void setStream(boolean stream) {
        this.stream = stream;
    }

    public static class MessageContent {
        private String role;
        private String content;

        public MessageContent() {}

        public MessageContent(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}