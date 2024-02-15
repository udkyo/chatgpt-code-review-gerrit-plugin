package com.googlesource.gerrit.plugins.chatgpt.client.prompt;

import com.googlesource.gerrit.plugins.chatgpt.client.common.ClientBase;
import com.googlesource.gerrit.plugins.chatgpt.client.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.chatgpt.client.patch.code.InlineCode;
import com.googlesource.gerrit.plugins.chatgpt.client.patch.diff.FileDiffProcessed;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import com.googlesource.gerrit.plugins.chatgpt.model.chatgpt.ChatGptRequestItem;
import com.googlesource.gerrit.plugins.chatgpt.model.common.CommentData;
import com.googlesource.gerrit.plugins.chatgpt.model.gerrit.GerritComment;
import com.googlesource.gerrit.plugins.chatgpt.model.common.GerritClientData;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Slf4j
public class ChatGptUserPrompt extends ClientBase {
    private final GerritClientData gerritClientData;
    private final HashMap<String, FileDiffProcessed> fileDiffsProcessed;

    private ChatGptHistory gptMessageHistory;
    @Getter
    private List<GerritComment> commentProperties;

    public ChatGptUserPrompt(Configuration config, GerritClientData gerritClientData) {
        super(config);
        this.gerritClientData = gerritClientData;
        fileDiffsProcessed = gerritClientData.getFileDiffsProcessed();
        CommentData commentData = gerritClientData.getCommentData();
        commentProperties = commentData.getCommentProperties();
    }

    public String buildPrompt(GerritChange change) {
        gptMessageHistory = new ChatGptHistory(config, change, gerritClientData);
        List<ChatGptRequestItem> requestItems = new ArrayList<>();
        for (int i = 0; i < commentProperties.size(); i++) {
            requestItems.add(getRequestItem(i));
        }
        return requestItems.isEmpty() ? "" : gson.toJson(requestItems);
    }

    private ChatGptRequestItem getRequestItem(int i) {
        ChatGptRequestItem requestItem = new ChatGptRequestItem();
        GerritComment commentProperty = commentProperties.get(i);
        requestItem.setId(i);
        if (commentProperty.getLine() != null || commentProperty.getRange() != null) {
            String filename = commentProperty.getFilename();
            InlineCode inlineCode = new InlineCode(fileDiffsProcessed.get(filename));
            requestItem.setFilename(filename);
            requestItem.setLineNumber(commentProperty.getLine());
            requestItem.setCodeSnippet(inlineCode.getInlineCode(commentProperty));
        }
        requestItem.setRequest(gptMessageHistory.retrieveHistory(commentProperty));

        return requestItem;
    }

}
