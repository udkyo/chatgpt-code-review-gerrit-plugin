package com.googlesource.gerrit.plugins.chatgpt.client;

import com.google.common.net.HttpHeaders;
import com.google.gson.Gson;
import com.googlesource.gerrit.plugins.chatgpt.client.model.ReviewBatch;
import com.googlesource.gerrit.plugins.chatgpt.client.model.gerrit.GerritComment;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.googlesource.gerrit.plugins.chatgpt.utils.ReviewUtils.processGerritMessage;
import static java.net.HttpURLConnection.HTTP_OK;

@Slf4j
public class GerritClientPostComments extends GerritClientAccount {
    private static final String BULLET_POINT = "* ";

    private final Gson gson = new Gson();
    @Getter
    private List<GerritComment> commentProperties;

    public GerritClientPostComments(Configuration config) {
        super(config);
    }

    private String joinMessages(List<String> messages) {
        if (messages.size() == 1) {
            return messages.get(0);
        }
        return BULLET_POINT + String.join("\n\n" + BULLET_POINT, messages);
    }

    private Map<String, Object> getContextProperties(List<ReviewBatch> reviewBatches) {
        Map<String, Object> map = new HashMap<>();
        List<String> messages = new ArrayList<>();
        Map<String, List<GerritComment>> comments = new HashMap<>();
        for (ReviewBatch reviewBatch : reviewBatches) {
            String message = processGerritMessage(reviewBatch.getContent());
            if (message.trim().isEmpty()) {
                log.info("Empty message from post comment not submitted.");
                continue;
            }
            if (reviewBatch.getLine() != null || reviewBatch.getRange() != null ) {
                String filename = reviewBatch.getFilename();
                List<GerritComment> filenameComments = comments.getOrDefault(filename, new ArrayList<>());
                GerritComment filenameComment = new GerritComment();
                filenameComment.setMessage(message);
                filenameComment.setLine(reviewBatch.getLine());
                filenameComment.setRange(reviewBatch.getRange());
                filenameComment.setIn_reply_to(reviewBatch.getId());
                filenameComments.add(filenameComment);
                comments.putIfAbsent(filename, filenameComments);
            }
            else {
                messages.add(message);
            }
        }
        if (!messages.isEmpty()) {
            map.put("message", joinMessages(messages));
        }
        if (!comments.isEmpty()) {
            map.put("comments", comments);
        }
        return map;
    }


    public void postComments(String fullChangeId, List<ReviewBatch> reviewBatches) throws Exception {
        Map<String, Object> map = getContextProperties(reviewBatches);
        if (map.isEmpty()) {
            return;
        }
        URI uri = URI.create(config.getGerritAuthBaseUrl()
                + UriResourceLocator.gerritPostCommentsUri(fullChangeId));
        log.debug("Post-Comment uri: {}", uri);
        String auth = generateBasicAuth(config.getGerritUserName(),
                config.getGerritPassword());
        String json = gson.toJson(map);
        log.debug("Post-Comment JSON: {}", json);
        HttpRequest request = HttpRequest.newBuilder()
                .header(HttpHeaders.AUTHORIZATION, auth)
                .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())
                .uri(uri)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClientWithRetry.execute(request);

        if (response.statusCode() != HTTP_OK) {
            log.error("Comment posting failed with status code: {}", response.statusCode());
        }
    }

}
