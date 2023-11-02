package com.googlesource.gerrit.plugins.chatgpt.client;

import lombok.Data;

import java.util.List;

@Data
public class ChatCompletionResponseMessage extends ChatCompletionBase {

    private List<MessageChoice> choices;


    @Data
    public static class MessageChoice extends ChatCompletionBase.Choice {
        private Message message;
    }

    @Data
    public static class Message {
        private String role;
        private String content;
    }
}
