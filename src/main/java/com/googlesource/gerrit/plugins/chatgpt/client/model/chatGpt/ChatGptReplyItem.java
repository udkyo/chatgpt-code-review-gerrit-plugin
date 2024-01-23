package com.googlesource.gerrit.plugins.chatgpt.client.model.chatGpt;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ChatGptReplyItem extends ChatGptDialogueItem {
    private String reply;
}
