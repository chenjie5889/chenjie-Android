// 文件: MessageAdapter.java
package com.example.chronicdiseasemedmanager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_USER = 0;
    private static final int TYPE_ASSISTANT = 1;
    private static final int TYPE_LOADING = 2;

    private List<Message> messageList;

    public MessageAdapter(List<Message> messageList) {
        this.messageList = messageList;
    }

    @Override
    public int getItemViewType(int position) {
        return messageList.get(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        if (viewType == TYPE_USER) {
            View view = inflater.inflate(R.layout.item_message_user, parent, false);
            return new UserMessageViewHolder(view);
        } else if (viewType == TYPE_ASSISTANT) {
            View view = inflater.inflate(R.layout.item_message_assistant, parent, false);
            return new AssistantMessageViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_message_loading, parent, false);
            return new LoadingMessageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message message = messageList.get(position);

        if (holder instanceof UserMessageViewHolder) {
            UserMessageViewHolder userHolder = (UserMessageViewHolder) holder;
            userHolder.tvContent.setText(message.getContent());
            userHolder.tvTime.setText(message.getTime());
        } else if (holder instanceof AssistantMessageViewHolder) {
            AssistantMessageViewHolder assistantHolder = (AssistantMessageViewHolder) holder;
            assistantHolder.tvContent.setText(message.getContent());
            assistantHolder.tvTime.setText(message.getTime());
        } else if (holder instanceof LoadingMessageViewHolder) {
            // 加载中的消息不需要设置内容
        }
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    // 用户消息ViewHolder
    static class UserMessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvContent;
        TextView tvTime;

        UserMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tvContent);
            tvTime = itemView.findViewById(R.id.tvTime);
        }
    }

    // 助手消息ViewHolder
    static class AssistantMessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvContent;
        TextView tvTime;

        AssistantMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tvContent);
            tvTime = itemView.findViewById(R.id.tvTime);
        }
    }

    // 加载中消息ViewHolder
    static class LoadingMessageViewHolder extends RecyclerView.ViewHolder {
        LoadingMessageViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}