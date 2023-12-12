package com.googlesource.gerrit.plugins.chatgpt.client.model;

import lombok.Data;

@Data
public class ChatGptRequestPoint extends ChatGptPointBase {
    private String request;
}
