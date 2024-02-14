package com.googlesource.gerrit.plugins.chatgpt.client.gerrit;

import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import com.googlesource.gerrit.plugins.chatgpt.model.gerrit.GerritComment;
import com.googlesource.gerrit.plugins.chatgpt.settings.DynamicSettings;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GerritMessageComment extends GerritMessage {
    private static final String AUTOGENERATED_PREFIX = "autogenerated:";

    private final Integer gptAccountId;

    public GerritMessageComment(Configuration config, GerritChange change) {
        super(config);
        gptAccountId = DynamicSettings.getInstance(change).getGptAccountId();
    }

    protected String getCleanedMessage(GerritComment commentProperty) {
        String commentMessage = commentProperty.getMessage();
        if (isFromAssistant(commentProperty)) {
            return removeHeadings(commentMessage);
        }
        else {
            return removeMentions(commentMessage);
        }
    }

    protected String getMessageWithoutMentions(GerritComment commentProperty) {
        return removeMentions(commentProperty.getMessage());
    }

    protected boolean isAutogenerated(GerritComment commentProperty) {
        return commentProperty.getTag() != null && commentProperty.getTag().startsWith(AUTOGENERATED_PREFIX);
    }

    protected boolean isFromAssistant(GerritComment commentProperty) {
        return commentProperty.getAuthor().getAccountId() == gptAccountId;
    }

}
