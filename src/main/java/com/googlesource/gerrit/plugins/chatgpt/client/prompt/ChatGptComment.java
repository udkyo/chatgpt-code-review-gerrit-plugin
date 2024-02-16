package com.googlesource.gerrit.plugins.chatgpt.client.prompt;

import com.googlesource.gerrit.plugins.chatgpt.client.common.ClientMessage;
import com.googlesource.gerrit.plugins.chatgpt.client.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import com.googlesource.gerrit.plugins.chatgpt.model.gerrit.GerritComment;
import com.googlesource.gerrit.plugins.chatgpt.settings.DynamicSettings;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChatGptComment extends ClientMessage {
    private final Integer gptAccountId;

    public ChatGptComment(Configuration config, GerritChange change) {
        super(config);
        gptAccountId = DynamicSettings.getInstance(change).getGptAccountId();
    }

    protected String getCleanedMessage(GerritComment commentProperty) {
        String commentMessage = commentProperty.getMessage();
        if (isFromAssistant(commentProperty)) {
            return removeHeadings(commentMessage);
        }
        else {
            return removeHeadings(removeMentions(commentMessage));
        }
    }

    protected String getMessageWithoutMentions(GerritComment commentProperty) {
        return removeMentions(commentProperty.getMessage());
    }

    protected boolean isFromAssistant(GerritComment commentProperty) {
        return commentProperty.getAuthor().getAccountId() == gptAccountId;
    }

}
