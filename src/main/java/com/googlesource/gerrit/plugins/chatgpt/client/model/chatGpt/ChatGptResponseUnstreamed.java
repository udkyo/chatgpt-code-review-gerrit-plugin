package com.googlesource.gerrit.plugins.chatgpt.client.model.chatGpt;

import lombok.Data;

import java.util.List;

@Data
public class ChatGptResponseUnstreamed {
    private List<MessageChoice> choices;

    @Data
    public static class MessageChoice {
        private Message message;
    }

    @Data
    public static class Message {
        private String role;
        private List<ChatGptToolCall> tool_calls;
    }

}
