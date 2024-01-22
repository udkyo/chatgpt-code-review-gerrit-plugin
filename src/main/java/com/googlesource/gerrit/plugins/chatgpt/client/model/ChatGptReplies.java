package com.googlesource.gerrit.plugins.chatgpt.client.model;

import lombok.Data;

import java.util.List;

@Data
public class ChatGptReplies extends ChatGptReplyPoint {
    private List<ChatGptReplyPoint> replies;
    private String changeId;
}
