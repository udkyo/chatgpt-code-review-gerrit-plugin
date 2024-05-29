package com.googlesource.gerrit.plugins.chatgpt.mode.common.client.prompt;

import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.client.ClientBase;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.client.messages.ClientMessage;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.model.api.gerrit.GerritComment;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.model.data.ChangeSetData;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChatGptComment extends ClientBase {
    protected ClientMessage commentMessage;

    private final ChangeSetData changeSetData;

    public ChatGptComment(Configuration config, ChangeSetData changeSetData) {
        super(config);
        this.changeSetData = changeSetData;
    }

    protected String getCleanedMessage(GerritComment commentProperty) {
        commentMessage = new ClientMessage(config, changeSetData, commentProperty.getMessage());
        if (isFromAssistant(commentProperty)) {
            commentMessage.removeDebugMessages();
        }
        else {
            commentMessage.removeMentions().parseRemoveCommands();
        }
        return commentMessage.removeHeadings().getMessage();
    }

    protected boolean isFromAssistant(GerritComment commentProperty) {
        return commentProperty.getAuthor().getAccountId() == changeSetData.getGptAccountId();
    }

}
