package com.googlesource.gerrit.plugins.chatgpt.client.gerrit;

import com.google.gerrit.server.events.CommentAddedEvent;
import com.google.gson.reflect.TypeToken;
import com.googlesource.gerrit.plugins.chatgpt.client.ClientCommands;
import com.googlesource.gerrit.plugins.chatgpt.client.UriResourceLocator;
import com.googlesource.gerrit.plugins.chatgpt.client.common.ClientMessage;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import com.googlesource.gerrit.plugins.chatgpt.model.common.CommentData;
import com.googlesource.gerrit.plugins.chatgpt.model.gerrit.GerritComment;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.*;

import static com.googlesource.gerrit.plugins.chatgpt.utils.TimeUtils.getTimeStamp;

@Slf4j
public class GerritClientComments extends GerritClientAccount {
    public static final String GLOBAL_MESSAGES_FILENAME = "/PATCHSET_LEVEL";
    private static final Integer MAX_SECS_GAP_BETWEEN_EVENT_AND_COMMENT = 2;

    private final HashMap<String, GerritComment> commentMap;
    private final HashMap<String, GerritComment> commentGlobalMap;

    private ClientMessage clientMessage;
    private String authorUsername;
    @Getter
    private List<GerritComment> commentProperties;

    public GerritClientComments(Configuration config) {
        super(config);
        commentProperties = new ArrayList<>();
        commentMap = new HashMap<>();
        commentGlobalMap = new HashMap<>();
    }

    public CommentData getCommentData() {
        return new CommentData(commentProperties, commentMap, commentGlobalMap);
    }

    public boolean retrieveLastComments(GerritChange change) {
        clientMessage = new ClientMessage(config);
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
                if (filename.equals(GLOBAL_MESSAGES_FILENAME)) {
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
                if (clientMessage.isBotAddressed(commentMessage)) {
                    commentProperties.add(latestComment);
                }
            }
        } catch (Exception e) {
            log.error("Error while retrieving comments for change: {}", change.getFullChangeId(), e);
        }
    }

}
