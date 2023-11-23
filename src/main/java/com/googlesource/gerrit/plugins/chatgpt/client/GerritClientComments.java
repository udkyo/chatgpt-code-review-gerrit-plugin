package com.googlesource.gerrit.plugins.chatgpt.client;

import com.google.common.net.HttpHeaders;
import com.google.gerrit.server.events.CommentAddedEvent;
import com.google.gerrit.server.events.Event;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.net.HttpURLConnection.HTTP_OK;

@Slf4j
public class GerritClientComments extends GerritClientBase {
    private static final Integer MAX_SECS_GAP_BETWEEN_EVENT_AND_COMMENT = 2;

    private final Gson gson = new Gson();
    private long commentsStartTimestamp;
    private String authorUsername;
    protected List<JsonObject> commentProperties;
    protected HashMap<String, List<String>> filesNewContent;

    public void initialize(Configuration config) {
        super.initialize(config);
        commentProperties  = new ArrayList<>();
        filesNewContent = new HashMap<>();
    }

    private long getTimeStamp(String updatedString) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSSSS");
        LocalDateTime updatedDateTime = LocalDateTime.parse(updatedString, formatter);
        long updatedTimeStamp = updatedDateTime.toInstant(ZoneOffset.UTC).getEpochSecond();
        log.debug("TimeStamp: {} ({})", updatedTimeStamp, updatedDateTime);
        return updatedTimeStamp;
    }

    private List<JsonObject> filterLastComments(String responseBody) {
        Type type = new TypeToken<Map<String, JsonArray>>(){}.getType();
        Map<String, JsonArray> jsonMap = gson.fromJson(responseBody, type);

        String latestChangeMessageId = null;
        HashMap<String, List<JsonObject>> latestComments = new HashMap<>();

        for (Map.Entry<String, JsonArray> entry : jsonMap.entrySet()) {
            String filename = entry.getKey();
            log.info("filename: {}", filename);

            JsonArray commentsArray = entry.getValue();

            for (JsonElement element : commentsArray) {
                JsonObject commentObject = element.getAsJsonObject();
                commentObject.addProperty("filename", filename);
                String changeMessageId = commentObject.get("change_message_id").getAsString();
                String commentAuthorUsername = commentObject.get("author").getAsJsonObject()
                        .get("username").getAsString();
                log.debug("Change Message Id: {} - Author: {}", latestChangeMessageId, commentAuthorUsername);
                long updatedTimeStamp = getTimeStamp(commentObject.get("updated").getAsString());
                if (commentAuthorUsername.equals(authorUsername) &&
                        updatedTimeStamp >= commentsStartTimestamp - MAX_SECS_GAP_BETWEEN_EVENT_AND_COMMENT) {
                    log.info("Found comment with updatedTimeStamp : {}", updatedTimeStamp);
                    latestChangeMessageId = changeMessageId;
                }
                latestComments.computeIfAbsent(changeMessageId, k -> new ArrayList<>()).add(commentObject);
            }
        }

        return latestComments.getOrDefault(latestChangeMessageId, null);
    }

    private List<JsonObject> getLastComments(String fullChangeId) throws Exception {
        URI uri = URI.create(config.getGerritAuthBaseUrl()
                + UriResourceLocator.gerritGetAllPatchSetCommentsUri(fullChangeId));

        return filterLastComments(forwardGetRequest(uri));
    }

    private boolean isBotAddressed(String comment) {
        log.info("Processing comment: {}", comment);

        if (comment == null || !comment.contains("@" + config.getGerritUserName())) {
            log.debug("Skipping action since the comment does not mention the ChatGpt bot." +
                            " Expected bot name in comment: {}, Actual comment text: {}",
                    config.getGerritUserName(), comment);
            return false;
        }
        return true;
    }

    private String removeTagsFromComment(String comment) {
        return comment.substring(comment.indexOf("@" + config.getGerritUserName())
                + config.getGerritUserName().length() + 1);
    }

    private void addAllComments(String fullChangeId) {
        try {
            List<JsonObject> latestComments = getLastComments(fullChangeId);
            if (latestComments == null) {
                return;
            }
            for (JsonObject latestComment : latestComments) {
                String commentMessage = latestComment.get("message").getAsString();
                if (isBotAddressed(commentMessage)) {
                    commentProperties.add(latestComment);
                }
            }
        } catch (Exception e) {
            log.error("Error while processing change: {}", fullChangeId, e);
        }
    }

    private Map<String, Object> getContextProperties(List<HashMap<String, Object>> reviewBatches) {
        Map<String, Object> map = new HashMap<>();
        Map<String, List<Map<String, Object>>> comments = new HashMap<>();
        for (HashMap<String, Object> reviewBatch : reviewBatches) {
            String message = (String) reviewBatch.get("content");
            if (message.trim().isEmpty()) {
                log.info("Post comment empty message not submitted.");
                continue;
            }
            if (reviewBatch.containsKey("line") || reviewBatch.containsKey("range")) {
                String filename = (String) reviewBatch.get("filename");
                List<Map<String, Object>> filenameComments = comments.getOrDefault(filename, new ArrayList<>());
                Map<String, Object> filenameComment = new HashMap<>();
                filenameComment.put("message", message);
                filenameComment.put("line", reviewBatch.get("line"));
                filenameComment.put("range", reviewBatch.get("range"));
                filenameComment.put("in_reply_to", reviewBatch.get("id"));
                filenameComments.add(filenameComment);
                comments.putIfAbsent(filename, filenameComments);
            }
            else {
                map.put("message", message);
            }
        }
        if (!comments.isEmpty()) {
            map.put("comments", comments);
        }
        return map;
    }

    protected String getCommentPrompt(int i) {
        StringBuilder commentString = new StringBuilder();
        JsonObject commentProperty = commentProperties.get(i);
        if (commentProperty.has("line") || commentProperty.has("range")) {
            String filename = commentProperty.get("filename").getAsString();
            InlineCode inlineCode = new InlineCode(filesNewContent.get(filename));
            commentString.append(String.format(Configuration.DEFAULT_GPT_CUSTOM_USER_CONTEXT_PROMPT,
                    inlineCode.getInlineCode(commentProperty),
                    commentProperty.get("line").getAsInt(),
                    filename
            ));
        }
        String commentMessage = commentProperty.get("message").getAsString();
        commentString.append(removeTagsFromComment(commentMessage).trim());

        return commentString.toString();
    }

    public List<JsonObject> getCommentProperties() {
        return commentProperties;
    }

    public boolean retrieveLastComments(Event event, String fullChangeId) {
        commentsStartTimestamp = event.eventCreatedOn;
        CommentAddedEvent commentAddedEvent = (CommentAddedEvent) event;
        authorUsername = commentAddedEvent.author.get().username;
        log.debug("Comments start datetime: {}", commentsStartTimestamp);
        log.debug("Author username: {} - ChatGPT username: {}", authorUsername, config.getGerritUserName());
        if (authorUsername.equals(config.getGerritUserName())) {
            log.debug("These are the Chatbot's own comments, do not process them.");
            return false;
        }
        if (isDisabledAuthor(authorUsername)) {
            log.info("Review of comments from author '{}' is disabled.", authorUsername);
            return false;
        }
        addAllComments(fullChangeId);

        return !commentProperties.isEmpty();
    }

    public void postComments(String fullChangeId, List<HashMap<String, Object>> reviewBatches)
            throws Exception {
        Map<String, Object> map = getContextProperties(reviewBatches);
        if (map.isEmpty()) {
            return;
        }
        String json = gson.toJson(map);

        URI uri = URI.create(config.getGerritAuthBaseUrl()
                + UriResourceLocator.gerritCommentUri(fullChangeId));
        String auth = generateBasicAuth(config.getGerritUserName(),
                config.getGerritPassword());
        log.debug("postComment uri: {}", uri);
        log.debug("postComment json: {}", json);
        HttpRequest request = HttpRequest.newBuilder()
                .header(HttpHeaders.AUTHORIZATION, auth)
                .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())
                .uri(uri)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClientWithRetry.execute(request);

        if (response.statusCode() != HTTP_OK) {
            log.error("Review post failed with status code: {}", response.statusCode());
        }
    }

}
