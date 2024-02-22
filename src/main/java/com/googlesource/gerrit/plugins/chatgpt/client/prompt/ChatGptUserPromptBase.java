package com.googlesource.gerrit.plugins.chatgpt.client.prompt;

import com.googlesource.gerrit.plugins.chatgpt.client.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.chatgpt.client.patch.code.InlineCode;
import com.googlesource.gerrit.plugins.chatgpt.client.patch.diff.FileDiffProcessed;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import com.googlesource.gerrit.plugins.chatgpt.model.chatgpt.ChatGptHistoryItem;
import com.googlesource.gerrit.plugins.chatgpt.model.common.CommentData;
import com.googlesource.gerrit.plugins.chatgpt.model.gerrit.GerritComment;
import com.googlesource.gerrit.plugins.chatgpt.model.common.GerritClientData;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Slf4j
public abstract class ChatGptUserPromptBase {
    protected final GerritClientData gerritClientData;
    protected final HashMap<String, FileDiffProcessed> fileDiffsProcessed;
    protected final CommentData commentData;
    @Getter
    protected final List<ChatGptHistoryItem> historyItems;

    protected ChatGptHistory gptMessageHistory;
    @Getter
    protected List<GerritComment> commentProperties;

    public ChatGptUserPromptBase(Configuration config, GerritChange change, GerritClientData gerritClientData) {
        this.gerritClientData = gerritClientData;
        fileDiffsProcessed = gerritClientData.getFileDiffsProcessed();
        commentData = gerritClientData.getCommentData();
        gptMessageHistory = new ChatGptHistory(config, change, gerritClientData);
        historyItems = new ArrayList<>();
    }

    abstract void addHistoryItem(int i);

    protected ChatGptHistoryItem getHistoryItem(int i) {
        ChatGptHistoryItem historyItem = new ChatGptHistoryItem();
        GerritComment commentProperty = commentProperties.get(i);
        if (commentProperty.getLine() != null || commentProperty.getRange() != null) {
            String filename = commentProperty.getFilename();
            InlineCode inlineCode = new InlineCode(fileDiffsProcessed.get(filename));
            historyItem.setFilename(filename);
            historyItem.setLineNumber(commentProperty.getLine());
            historyItem.setCodeSnippet(inlineCode.getInlineCode(commentProperty));
        }

        return historyItem;
    }

}
