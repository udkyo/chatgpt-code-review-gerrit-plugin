package com.googlesource.gerrit.plugins.chatgpt.client.prompt;

import com.googlesource.gerrit.plugins.chatgpt.client.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import com.googlesource.gerrit.plugins.chatgpt.model.chatgpt.ChatGptHistoryItem;
import com.googlesource.gerrit.plugins.chatgpt.model.common.GerritClientData;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChatGptUserPromptRequests extends ChatGptUserPromptBase {
    public ChatGptUserPromptRequests(Configuration config, GerritChange change, GerritClientData gerritClientData) {
        super(config, change, gerritClientData);
        commentProperties = commentData.getCommentProperties();
    }

    public void addHistoryItem(int i) {
        ChatGptHistoryItem requestItem = getHistoryItem(i);
        requestItem.setId(i);
        historyItems.add(requestItem);
    }

    protected ChatGptHistoryItem getHistoryItem(int i) {
        ChatGptHistoryItem requestItem = super.getHistoryItem(i);
        requestItem.setRequest(gptMessageHistory.retrieveHistory(commentProperties.get(i)));

        return requestItem;
    }

}
