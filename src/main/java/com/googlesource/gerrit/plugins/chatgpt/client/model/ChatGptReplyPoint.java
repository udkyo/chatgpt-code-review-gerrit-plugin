package com.googlesource.gerrit.plugins.chatgpt.client.model;

import lombok.Data;

@Data
public class ChatGptReplyPoint extends ChatGptPointBase {
    private String reply;
}
