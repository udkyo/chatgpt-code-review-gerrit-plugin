package com.googlesource.gerrit.plugins.chatgpt.client.prompt;

import com.googlesource.gerrit.plugins.chatgpt.client.common.ClientBase;
import com.googlesource.gerrit.plugins.chatgpt.client.common.ClientMessage;
import com.googlesource.gerrit.plugins.chatgpt.client.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import com.googlesource.gerrit.plugins.chatgpt.model.gerrit.GerritComment;
import com.googlesource.gerrit.plugins.chatgpt.settings.DynamicSettings;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChatGptComment extends ClientBase {
    private final Integer gptAccountId;

    public ChatGptComment(Configuration config, GerritChange change) {
        super(config);
        gptAccountId = DynamicSettings.getInstance(change).getGptAccountId();
    }

    protected String getCleanedMessage(GerritComment commentProperty) {
        ClientMessage commentMessage = new ClientMessage(config, commentProperty.getMessage());
        if (!isFromAssistant(commentProperty)) {
            commentMessage.removeCommands().removeMentions();
        }
        return commentMessage.removeHeadings().getMessage();
    }

    protected boolean isFromAssistant(GerritComment commentProperty) {
        return commentProperty.getAuthor().getAccountId() == gptAccountId;
    }

}
