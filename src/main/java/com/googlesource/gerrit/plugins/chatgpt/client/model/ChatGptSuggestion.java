package com.googlesource.gerrit.plugins.chatgpt.client.model;

import lombok.Data;

@Data
public class ChatGptSuggestion {
    private String suggestion;
    private String filename;
    private Integer lineNumber;
    private String codeSnippet;
}
