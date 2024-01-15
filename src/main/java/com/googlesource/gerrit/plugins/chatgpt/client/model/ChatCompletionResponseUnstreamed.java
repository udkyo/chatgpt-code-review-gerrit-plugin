package com.googlesource.gerrit.plugins.chatgpt.client.model;

import lombok.Data;

import java.util.List;

@Data
public class ChatCompletionResponseUnstreamed {
    private List<MessageChoice> choices;


    @Data
    public static class MessageChoice {
        private Message message;
    }

    @Data
    public static class Message {
        private String role;
        private List<ChatCompletionBase.ToolCall> tool_calls;
    }
}
