package com.googlesource.gerrit.plugins.chatgpt.model.chatgpt;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ChatGptHistoryItem extends ChatGptDialogueItem {
    private String request;
    private String message;
}
