package com.googlesource.gerrit.plugins.chatgpt.client.gerrit;

import com.google.common.net.HttpHeaders;
import com.googlesource.gerrit.plugins.chatgpt.client.UriResourceLocator;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import com.googlesource.gerrit.plugins.chatgpt.model.gerrit.GerritComment;
import com.googlesource.gerrit.plugins.chatgpt.model.gerrit.GerritReview;
import com.googlesource.gerrit.plugins.chatgpt.model.review.ReviewBatch;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.googlesource.gerrit.plugins.chatgpt.client.prompt.MessageSanitizer.sanitizeChatGptMessage;
import static java.net.HttpURLConnection.HTTP_OK;

@Slf4j
public class GerritClientReview extends GerritClientAccount {
    private static final String BULLET_POINT = "* ";

    public GerritClientReview(Configuration config) {
        super(config);
    }

    public void setReview(String fullChangeId, List<ReviewBatch> reviewBatches, Integer reviewScore) throws Exception {
        GerritReview reviewMap = buildReview(reviewBatches, reviewScore);
        if (reviewMap.getComments() == null && reviewMap.getMessage() == null) {
            return;
        }
        URI uri = URI.create(config.getGerritAuthBaseUrl()
                + UriResourceLocator.gerritSetReviewUri(fullChangeId));
        log.debug("Set-Review uri: {}", uri);
        String auth = generateBasicAuth(config.getGerritUserName(),
                config.getGerritPassword());
        String json = gson.toJson(reviewMap);
        log.debug("Set-Review JSON: {}", json);
        HttpRequest request = HttpRequest.newBuilder()
                .header(HttpHeaders.AUTHORIZATION, auth)
                .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())
                .uri(uri)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClientWithRetry.execute(request);

        if (response.statusCode() != HTTP_OK) {
            log.error("Review setting failed with status code: {}", response.statusCode());
        }
    }

    public void setReview(String fullChangeId, List<ReviewBatch> reviewBatches) throws Exception {
        setReview(fullChangeId, reviewBatches, null);
    }

    private String joinMessages(List<String> messages) {
        if (messages.size() == 1) {
            return messages.get(0);
        }
        return BULLET_POINT + String.join("\n\n" + BULLET_POINT, messages);
    }

    private GerritReview buildReview(List<ReviewBatch> reviewBatches, Integer reviewScore) {
        GerritReview reviewMap = new GerritReview();
        List<String> messages = new ArrayList<>();
        Map<String, List<GerritComment>> comments = new HashMap<>();
        for (ReviewBatch reviewBatch : reviewBatches) {
            String message = sanitizeChatGptMessage(reviewBatch.getContent());
            if (message.trim().isEmpty()) {
                log.info("Empty message from review not submitted.");
                continue;
            }
            if (reviewBatch.getLine() != null || reviewBatch.getRange() != null ) {
                String filename = reviewBatch.getFilename();
                List<GerritComment> filenameComments = comments.getOrDefault(filename, new ArrayList<>());
                GerritComment filenameComment = new GerritComment();
                filenameComment.setMessage(message);
                filenameComment.setLine(reviewBatch.getLine());
                filenameComment.setRange(reviewBatch.getRange());
                filenameComment.setInReplyTo(reviewBatch.getId());
                filenameComment.setUnresolved(!config.getInlineCommentsAsResolved());
                filenameComments.add(filenameComment);
                comments.putIfAbsent(filename, filenameComments);
            }
            else {
                messages.add(message);
            }
        }
        if (!messages.isEmpty()) {
            reviewMap.setMessage(joinMessages(messages));
        }
        if (!comments.isEmpty()) {
            reviewMap.setComments(comments);
        }
        if (reviewScore != null) {
            reviewMap.setLabels(new GerritReview.Labels(reviewScore));
        }
        return reviewMap;
    }

}
