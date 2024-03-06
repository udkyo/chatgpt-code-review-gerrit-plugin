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
    protected ClientMessage commentMessage;

    private final Integer gptAccountId;
    private final GerritChange change;

    public ChatGptComment(Configuration config, GerritChange change) {
        super(config);
        this.change = change;
        gptAccountId = DynamicSettings.getInstance(change).getGptAccountId();
    }

    protected String getCleanedMessage(GerritComment commentProperty) {
        commentMessage = new ClientMessage(config, change, commentProperty.getMessage());
        if (!isFromAssistant(commentProperty)) {
            commentMessage.removeMentions().parseRemoveCommands();
        }
        return commentMessage.removeHeadings().getMessage();
    }

    protected boolean isFromAssistant(GerritComment commentProperty) {
        return commentProperty.getAuthor().getAccountId() == gptAccountId;
    }

}
