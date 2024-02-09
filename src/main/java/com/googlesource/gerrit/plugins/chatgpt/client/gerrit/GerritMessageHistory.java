package com.googlesource.gerrit.plugins.chatgpt.client.gerrit;

import com.googlesource.gerrit.plugins.chatgpt.DynamicSettings;
import com.googlesource.gerrit.plugins.chatgpt.client.model.chatGpt.ChatGptRequest;
import com.googlesource.gerrit.plugins.chatgpt.client.model.gerrit.GerritComment;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import com.googlesource.gerrit.plugins.chatgpt.utils.SingletonManager;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

@Slf4j
public class GerritMessageHistory extends GerritMessage {
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";

    private final Integer gptAccountId;
    private final HashMap<String, GerritComment> commentMap;

    public GerritMessageHistory(Configuration config, GerritChange change, HashMap<String, GerritComment> commentMap) {
        super(config);
        DynamicSettings dynamicSettings = SingletonManager.getInstance(DynamicSettings.class, change);
        gptAccountId = dynamicSettings.getGptAccountId();
        this.commentMap = commentMap;
    }

    public String retrieveCommentMessage(GerritComment commentProperty) {
        if (commentProperty.getInReplyTo() != null) {
            return retrieveMessageHistory(commentProperty);
        }
        else {
            return getMessageWithoutMentions(commentProperty);
        }
    }

    private String getRoleFromComment(GerritComment currentComment) {
        return currentComment.getAuthor().getAccountId() == gptAccountId ? ROLE_ASSISTANT : ROLE_USER;
    }

    private String retrieveMessageHistory(GerritComment currentComment) {
        List<ChatGptRequest.Message> messageHistory = new ArrayList<>();
        while (currentComment != null) {
            ChatGptRequest.Message message = ChatGptRequest.Message.builder()
                    .role(getRoleFromComment(currentComment))
                    .content(getMessageWithoutMentions(currentComment))
                    .build();
            messageHistory.add(message);
            currentComment = commentMap.get(currentComment.getInReplyTo());
        }
        // Reverse the history sequence so that the oldest message appears first and the newest message is last
        Collections.reverse(messageHistory);

        return gson.toJson(messageHistory);
    }

}
