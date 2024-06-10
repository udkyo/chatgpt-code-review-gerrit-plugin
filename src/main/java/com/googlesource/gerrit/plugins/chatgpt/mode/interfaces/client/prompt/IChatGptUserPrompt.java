package com.googlesource.gerrit.plugins.chatgpt.mode.interfaces.client.prompt;

import com.googlesource.gerrit.plugins.chatgpt.mode.common.model.api.chatgpt.ChatGptMessageItem;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.model.api.gerrit.GerritComment;

import java.util.List;

public interface IChatGptUserPrompt {
    void addMessageItem(int i);
    List<GerritComment> getCommentProperties();
    List<ChatGptMessageItem> getMessageItems();
}
