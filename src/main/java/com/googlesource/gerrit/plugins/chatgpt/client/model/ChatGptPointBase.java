package com.googlesource.gerrit.plugins.chatgpt.client.model;

import lombok.Data;

@Data
public class ChatGptPointBase {
    protected Integer id;
    protected String filename;
    protected Integer lineNumber;
    protected String codeSnippet;
}
