// 文件: Message.java
package com.example.chronicdiseasemedmanager;

public class Message {
    public static final int TYPE_USER = 0;
    public static final int TYPE_ASSISTANT = 1;
    public static final int TYPE_LOADING = 2;

    private int type;
    private String content;
    private String time;

    public Message() {}

    public Message(int type, String content) {
        this.type = type;
        this.content = content;
        this.time = getCurrentTime();
    }

    public Message(int type, String content, String time) {
        this.type = type;
        this.content = content;
        this.time = time;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    private String getCurrentTime() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date());
    }
}