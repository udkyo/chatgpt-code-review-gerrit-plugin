package com.googlesource.gerrit.plugins.chatgpt.client.model;

import lombok.Data;

@Data
public class ChatGptSuggestionPoint extends ChatGptPointBase {
    private String suggestion;
}
