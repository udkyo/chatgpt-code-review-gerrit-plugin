package com.googlesource.gerrit.plugins.chatgpt.client.prompt;

import com.googlesource.gerrit.plugins.chatgpt.client.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import com.googlesource.gerrit.plugins.chatgpt.model.chatgpt.ChatGptHistoryItem;
import com.googlesource.gerrit.plugins.chatgpt.model.common.GerritClientData;
import com.googlesource.gerrit.plugins.chatgpt.model.gerrit.GerritComment;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;

import static com.googlesource.gerrit.plugins.chatgpt.client.gerrit.GerritClientComments.GLOBAL_MESSAGES_FILENAME;

@Slf4j
public class ChatGptUserPromptReview extends ChatGptUserPromptBase {
    private boolean patchSetDone;

    public ChatGptUserPromptReview(Configuration config, GerritChange change, GerritClientData gerritClientData) {
        super(config, change, gerritClientData);
        commentProperties = new ArrayList<>(commentData.getCommentMap().values());
        patchSetDone = false;
    }

    public void addHistoryItem(int i) {
        ChatGptHistoryItem messageItem = getHistoryItem(i);
        if (messageItem.getMessage() != null) {
            historyItems.add(messageItem);
        }
    }

    protected ChatGptHistoryItem getHistoryItem(int i) {
        ChatGptHistoryItem messageItem = super.getHistoryItem(i);
        GerritComment commentProperty = commentProperties.get(i);
        if (commentProperty.getFilename().equals(GLOBAL_MESSAGES_FILENAME)) {
            if (!patchSetDone) {
                messageItem.setMessage(gptMessageHistory.retrieveHistory(commentProperty));
                patchSetDone = true;
            }
        }
        else {
            messageItem.setMessage(gptMessageHistory.retrieveHistory(commentProperty));
        }

        return messageItem;
    }

}
