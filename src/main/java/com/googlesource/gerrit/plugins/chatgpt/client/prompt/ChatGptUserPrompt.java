package com.googlesource.gerrit.plugins.chatgpt.client.prompt;

import com.google.gson.Gson;
import com.googlesource.gerrit.plugins.chatgpt.client.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import com.googlesource.gerrit.plugins.chatgpt.model.chatgpt.ChatGptHistoryItem;
import com.googlesource.gerrit.plugins.chatgpt.model.common.GerritClientData;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class ChatGptUserPrompt {
    private final ChatGptUserPromptBase chatGptUserPromptBase;
    private final Gson gson = new Gson();

    public ChatGptUserPrompt(Configuration config, GerritChange change, GerritClientData gerritClientData) {
        if (change.getIsCommentEvent()) {
            chatGptUserPromptBase = new ChatGptUserPromptRequests(config, change, gerritClientData);
        }
        else {
            chatGptUserPromptBase = new ChatGptUserPromptReview(config, change, gerritClientData);
        }
    }

    public String buildPrompt() {
        for (int i = 0; i < chatGptUserPromptBase.getCommentProperties().size(); i++) {
            chatGptUserPromptBase.addHistoryItem(i);
        }
        List<ChatGptHistoryItem> historyItems = chatGptUserPromptBase.getHistoryItems();
        return historyItems.isEmpty() ? "" : gson.toJson(historyItems);
    }

}
