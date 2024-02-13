package com.googlesource.gerrit.plugins.chatgpt.client.model.chatgpt;

import lombok.Data;

import java.util.List;

@Data
public class ChatGptResponseContent {
    private List<ChatGptReplyItem> replies;
    private Integer score;
    private String changeId;
}
