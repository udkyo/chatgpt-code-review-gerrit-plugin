package com.googlesource.gerrit.plugins.chatgpt.client.gerrit;

import com.google.gerrit.server.events.CommentAddedEvent;
import com.google.gson.reflect.TypeToken;
import com.googlesource.gerrit.plugins.chatgpt.client.ClientCommands;
import com.googlesource.gerrit.plugins.chatgpt.client.UriResourceLocator;
import com.googlesource.gerrit.plugins.chatgpt.client.patch.diff.FileDiffProcessed;
import com.googlesource.gerrit.plugins.chatgpt.client.patch.code.InlineCode;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import com.googlesource.gerrit.plugins.chatgpt.model.chatgpt.ChatGptRequestItem;
import com.googlesource.gerrit.plugins.chatgpt.model.gerrit.GerritComment;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.*;

import static com.googlesource.gerrit.plugins.chatgpt.utils.ReviewUtils.getTimeStamp;

@Slf4j
public class GerritClientComments extends GerritClientAccount {
    private static final Integer MAX_SECS_GAP_BETWEEN_EVENT_AND_COMMENT = 2;

    private final HashMap<String, GerritComment> commentMap;
    private final HashMap<String, GerritComment> commentGlobalMap;

    private GerritMessage gerritMessage;
    private GerritMessageHistory gerritMessageHistory;
    private String authorUsername;
    @Getter
    private List<GerritComment> commentProperties;

    public GerritClientComments(Configuration config) {
        super(config);
        commentProperties = new ArrayList<>();
        commentMap = new HashMap<>();
        commentGlobalMap = new HashMap<>();
    }

    public boolean retrieveLastComments(GerritChange change) {
        gerritMessage = new GerritMessage(config);
        CommentAddedEvent commentAddedEvent = (CommentAddedEvent) change.getEvent();
        authorUsername = commentAddedEvent.author.get().username;
        log.debug("Found comments by '{}' on {}", authorUsername, change.getEventTimeStamp());
        if (authorUsername.equals(config.getGerritUserName())) {
            log.debug("These are the Bot's own comments, do not process them.");
            return false;
        }
        if (isDisabledUser(authorUsername)) {
            log.info("Review of comments from user '{}' is disabled.", authorUsername);
            return false;
        }
        addAllComments(change);

        return !commentProperties.isEmpty();
    }


    public String getUserRequests(GerritChange change, HashMap<String, FileDiffProcessed> fileDiffsProcessed,
                                  List<GerritComment> detailComments) {
        gerritMessageHistory = new GerritMessageHistory(config, change, commentMap, commentGlobalMap, detailComments);
        this.fileDiffsProcessed = fileDiffsProcessed;
        List<ChatGptRequestItem> requestItems = new ArrayList<>();
        for (int i = 0; i < commentProperties.size(); i++) {
            requestItems.add(getRequestItem(i));
        }
        return requestItems.isEmpty() ? "" : gson.toJson(requestItems);
    }

    private List<GerritComment> getLastComments(GerritChange change) throws Exception {
        URI uri = URI.create(config.getGerritAuthBaseUrl()
                + UriResourceLocator.gerritGetAllPatchSetCommentsUri(change.getFullChangeId()));
        String responseBody = forwardGetRequest(uri);
        Type mapEntryType = new TypeToken<Map<String, List<GerritComment>>>(){}.getType();
        Map<String, List<GerritComment>> lastCommentMap = gson.fromJson(responseBody, mapEntryType);

        String latestChangeMessageId = null;
        HashMap<String, List<GerritComment>> latestComments = new HashMap<>();
        for (Map.Entry<String, List<GerritComment>> entry : lastCommentMap.entrySet()) {
            String filename = entry.getKey();
            log.info("Commented filename: {}", filename);

            List<GerritComment> commentsArray = entry.getValue();

            for (GerritComment commentObject : commentsArray) {
                commentObject.setFilename(filename);
                String commentId = commentObject.getId();
                String changeMessageId = commentObject.getChangeMessageId();
                String commentAuthorUsername = commentObject.getAuthor().getUsername();
                log.debug("Change Message Id: {} - Author: {}", latestChangeMessageId, commentAuthorUsername);
                long updatedTimeStamp = getTimeStamp(commentObject.getUpdated());
                if (commentAuthorUsername.equals(authorUsername) &&
                        updatedTimeStamp >= change.getEventTimeStamp() - MAX_SECS_GAP_BETWEEN_EVENT_AND_COMMENT) {
                    log.debug("Found comment with updatedTimeStamp : {}", updatedTimeStamp);
                    latestChangeMessageId = changeMessageId;
                }
                latestComments.computeIfAbsent(changeMessageId, k -> new ArrayList<>()).add(commentObject);
                commentMap.put(commentId, commentObject);
                if (filename.equals(GerritMessage.GLOBAL_MESSAGES_FILENAME)) {
                    commentGlobalMap.put(changeMessageId, commentObject);
                }
            }
        }

        return latestComments.getOrDefault(latestChangeMessageId, null);
    }

    private void addAllComments(GerritChange change) {
        try {
            List<GerritComment> latestComments = getLastComments(change);
            if (latestComments == null) {
                return;
            }
            for (GerritComment latestComment : latestComments) {
                String commentMessage = latestComment.getMessage();
                if (ClientCommands.parseCommands(change, commentMessage)) {
                    commentProperties.clear();
                    return;
                }
                if (gerritMessage.isBotAddressed(commentMessage)) {
                    commentProperties.add(latestComment);
                }
            }
        } catch (Exception e) {
            log.error("Error while retrieving comments for change: {}", change.getFullChangeId(), e);
        }
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
        requestItem.setRequest(gerritMessageHistory.retrieveCommentMessage(commentProperty));

        return requestItem;
    }

}
