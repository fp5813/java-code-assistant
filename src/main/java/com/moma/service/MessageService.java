package com.moma.service;

import com.moma.di.Component;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 消息历史管理服务。管理 Agent 与 LLM 之间的消息历史。
 */
@Component
public class MessageService {

    private static final int MAX_MESSAGE_COUNT = 50;

    private final List<ChatMessage> messages = Collections.synchronizedList(new ArrayList<>());

    /**
     * 添加消息。
     */
    public void addMessage(ChatMessage msg) {
        messages.add(msg);
    }

    /**
     * 批量添加消息。
     */
    public void addMessages(List<? extends ChatMessage> msgs) {
        messages.addAll(msgs);
    }

    /**
     * 获取所有消息。
     */
    public List<ChatMessage> getMessages() {
        return List.copyOf(messages);
    }

    /**
     * 裁剪消息历史，保留最近的 MAX_MESSAGE_COUNT 条。
     */
    public void trim() {
        synchronized (messages) {
            while (messages.size() > MAX_MESSAGE_COUNT) {
                for (int i = 1; i < messages.size(); i++) {
                    if (messages.get(i) instanceof UserMessage) {
                        int removeEnd = Math.min(i + 3, messages.size());
                        messages.subList(i, removeEnd).clear();
                        break;
                    }
                }
            }
        }
    }

    /**
     * 清空消息历史。
     */
    public void clear() {
        messages.clear();
    }

    /**
     * 获取消息数量。
     */
    public int size() {
        return messages.size();
    }
}
