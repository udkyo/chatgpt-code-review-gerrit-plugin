package com.googlesource.gerrit.plugins.chatgpt.client;

import com.google.gerrit.server.events.CommentAddedEvent;
import com.google.gerrit.server.events.Event;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.googlesource.gerrit.plugins.chatgpt.client.model.chatGpt.ChatGptRequestItem;
import com.googlesource.gerrit.plugins.chatgpt.client.model.gerrit.GerritComment;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.googlesource.gerrit.plugins.chatgpt.utils.ReviewUtils.getTimeStamp;

@Slf4j
public class GerritClientGetComments extends GerritClientAccount {
    private static final Integer MAX_SECS_GAP_BETWEEN_EVENT_AND_COMMENT = 2;

    private final Gson gson = new Gson();
    private long commentsStartTimestamp;
    private String authorUsername;
    @Getter
    private List<GerritComment> commentProperties;

    public GerritClientGetComments(Configuration config) {
        super(config);
        commentProperties = new ArrayList<>();
    }

    private List<GerritComment> getLastComments(String fullChangeId) throws Exception {
        URI uri = URI.create(config.getGerritAuthBaseUrl()
                + UriResourceLocator.gerritGetAllPatchSetCommentsUri(fullChangeId));
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
                String changeMessageId = commentObject.getChange_message_id();
                String commentAuthorUsername = commentObject.getAuthor().getUsername();
                log.debug("Change Message Id: {} - Author: {}", latestChangeMessageId, commentAuthorUsername);
                long updatedTimeStamp = getTimeStamp(commentObject.getUpdated());
                if (commentAuthorUsername.equals(authorUsername) &&
                        updatedTimeStamp >= commentsStartTimestamp - MAX_SECS_GAP_BETWEEN_EVENT_AND_COMMENT) {
                    log.debug("Found comment with updatedTimeStamp : {}", updatedTimeStamp);
                    latestChangeMessageId = changeMessageId;
                }
                latestComments.computeIfAbsent(changeMessageId, k -> new ArrayList<>()).add(commentObject);
            }
        }

        return latestComments.getOrDefault(latestChangeMessageId, null);
    }

    private Pattern getBotMentionPattern() {
        String escapedUserName = Pattern.quote(config.getGerritUserName());
        String emailRegex = "@" + escapedUserName + "(?:@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})?\\b";
        return Pattern.compile(emailRegex);
    }

    private boolean isBotAddressed(String comment) {
        log.info("Processing comment: {}", comment);

        Matcher userMatcher = getBotMentionPattern().matcher(comment);
        if (!userMatcher.find()) {
            log.debug("Skipping action since the comment does not mention the ChatGPT bot." +
                            " Expected bot name in comment: {}, Actual comment text: {}",
                    config.getGerritUserName(), comment);
            return false;
        }
        return true;
    }

    private void addAllComments(String fullChangeId) {
        try {
            List<GerritComment> latestComments = getLastComments(fullChangeId);
            if (latestComments == null) {
                return;
            }
            for (GerritComment latestComment : latestComments) {
                String commentMessage = latestComment.getMessage();
                if (isBotAddressed(commentMessage)) {
                    commentProperties.add(latestComment);
                }
            }
        } catch (Exception e) {
            log.error("Error while retrieving comments for change: {}", fullChangeId, e);
        }
    }

    private String removeMentionsFromComment(String comment) {
        return comment.replaceAll(getBotMentionPattern().pattern(), "");
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
        String commentMessage = commentProperty.getMessage();
        requestItem.setRequest(removeMentionsFromComment(commentMessage).trim());

        return requestItem;
    }

    public boolean retrieveLastComments(Event event, String fullChangeId) {
        commentsStartTimestamp = event.eventCreatedOn;
        CommentAddedEvent commentAddedEvent = (CommentAddedEvent) event;
        authorUsername = commentAddedEvent.author.get().username;
        log.debug("Found comments by '{}' on {}", authorUsername, commentsStartTimestamp);
        if (authorUsername.equals(config.getGerritUserName())) {
            log.debug("These are the Bot's own comments, do not process them.");
            return false;
        }
        if (isDisabledUser(authorUsername)) {
            log.info("Review of comments from user '{}' is disabled.", authorUsername);
            return false;
        }
        addAllComments(fullChangeId);

        return !commentProperties.isEmpty();
    }


    public String getUserPrompt(HashMap<String, FileDiffProcessed> fileDiffsProcessed) {
        this.fileDiffsProcessed = fileDiffsProcessed;
        List<ChatGptRequestItem> requestItems = new ArrayList<>();
        for (int i = 0; i < commentProperties.size(); i++) {
            requestItems.add(getRequestItem(i));
        }
        return requestItems.isEmpty() ? "" : gson.toJson(requestItems);
    }

}
