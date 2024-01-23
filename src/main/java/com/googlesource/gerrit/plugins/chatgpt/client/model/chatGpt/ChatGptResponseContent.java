package com.googlesource.gerrit.plugins.chatgpt.client.model.chatGpt;

import lombok.Data;

import java.util.List;

@Data
public class ChatGptResponseContent {
    private List<ChatGptReplyItem> replies;
    private String changeId;
}
