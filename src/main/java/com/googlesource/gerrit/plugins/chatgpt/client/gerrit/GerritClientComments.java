package com.googlesource.gerrit.plugins.chatgpt.client.gerrit;

import com.google.gerrit.server.events.CommentAddedEvent;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.googlesource.gerrit.plugins.chatgpt.client.FileDiffProcessed;
import com.googlesource.gerrit.plugins.chatgpt.client.InlineCode;
import com.googlesource.gerrit.plugins.chatgpt.client.UriResourceLocator;
import com.googlesource.gerrit.plugins.chatgpt.client.model.chatGpt.ChatGptRequest;
import com.googlesource.gerrit.plugins.chatgpt.client.model.chatGpt.ChatGptRequestItem;
import com.googlesource.gerrit.plugins.chatgpt.client.model.gerrit.GerritComment;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.googlesource.gerrit.plugins.chatgpt.utils.ReviewUtils.getTimeStamp;

@Slf4j
public class GerritClientComments extends GerritClientAccount {
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final Pattern REVIEW_LAST_COMMAND_PATTERN = Pattern.compile("/review_last\\b");
    private static final Integer MAX_SECS_GAP_BETWEEN_EVENT_AND_COMMENT = 2;

    private final Gson gson = new Gson();
    @Getter
    private final Integer gptAccountId;
    private final HashMap<String, GerritComment> commentMap;
    private String authorUsername;
    @Getter
    private List<GerritComment> commentProperties;
    @Getter
    private Boolean forcedReview;

    public GerritClientComments(Configuration config) {
        super(config);
        commentProperties = new ArrayList<>();
        commentMap = new HashMap<>();
        gptAccountId = getAccountId(config.getGerritUserName()).orElseThrow(() -> new NoSuchElementException(
                "Error retrieving ChatGPT account ID in Gerrit"));
    }

    public boolean retrieveLastComments(GerritChange change) {
        forcedReview = false;
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


    public String getUserRequests(HashMap<String, FileDiffProcessed> fileDiffsProcessed) {
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

    private boolean isForcingReview(String comment) {
        Matcher reviewCommandMatcher = REVIEW_LAST_COMMAND_PATTERN.matcher(comment);
        return reviewCommandMatcher.find();
    }

    private void addAllComments(GerritChange change) {
        try {
            List<GerritComment> latestComments = getLastComments(change);
            if (latestComments == null) {
                return;
            }
            for (GerritComment latestComment : latestComments) {
                String commentMessage = latestComment.getMessage();
                if (isForcingReview(commentMessage)) {
                    log.debug("Forced review command detected in message {}", commentMessage);
                    forcedReview = true;
                    commentProperties.clear();
                    return;
                }
                if (isBotAddressed(commentMessage)) {
                    commentProperties.add(latestComment);
                }
            }
        } catch (Exception e) {
            log.error("Error while retrieving comments for change: {}", change.getFullChangeId(), e);
        }
    }

    private String getMessageWithoutMentions(GerritComment commentProperty) {
        String commentMessage = commentProperty.getMessage();
        return commentMessage.replaceAll(getBotMentionPattern().pattern(), "").trim();
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

    private String retrieveCommentMessage(GerritComment commentProperty) {
        if (commentProperty.getInReplyTo() != null) {
            return retrieveMessageHistory(commentProperty);
        }
        else {
            return getMessageWithoutMentions(commentProperty);
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
        requestItem.setRequest(retrieveCommentMessage(commentProperty));

        return requestItem;
    }

}
