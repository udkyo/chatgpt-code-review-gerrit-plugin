package com.googlesource.gerrit.plugins.chatgpt;

import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.chatgpt.client.GerritClient;
import com.googlesource.gerrit.plugins.chatgpt.client.OpenAiClient;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

import static com.googlesource.gerrit.plugins.chatgpt.client.ReviewUtils.extractID;

@Slf4j
@Singleton
public class PatchSetReviewer {
    private static final String SPLIT_REVIEW_MSG = "Too many changes. Please consider splitting into patches smaller " +
            "than %s lines for review.";

    private final GerritClient gerritClient;
    private final OpenAiClient openAiClient;

    private List<HashMap<String, Object>> reviewBatches;
    private String currentTag;
    private List<JsonObject> commentProperties;

    @Inject
    PatchSetReviewer(GerritClient gerritClient, OpenAiClient openAiClient) {
        this.gerritClient = gerritClient;
        this.openAiClient = openAiClient;
    }

    public void review(Configuration config, String fullChangeId) throws Exception {
        reviewBatches = new ArrayList<>();
        commentProperties = gerritClient.getCommentProperties();
        String patchSet = gerritClient.getPatchSet(fullChangeId);
        if (patchSet.isEmpty()) {
            log.info("No file to review has been found in the Patchset");
            return;
        }
        config.configureDynamically(Configuration.KEY_GPT_USER_PROMPT, gerritClient.getTaggedPrompt());

        String reviewSuggestion = getReviewSuggestion(config, fullChangeId, patchSet);
        splitReviewIntoBatches(reviewSuggestion);

        gerritClient.postComments(fullChangeId, reviewBatches);
    }

    private Integer getBatchID() {
        try {
            return Integer.parseInt(currentTag);
        }
        catch (NumberFormatException ex){
            return -1;
        }
    }

    private void addReviewBatch(StringBuilder batch) {
        HashMap<String, Object> batchMap = new HashMap<>();
        batchMap.put("content", batch.toString());
        Integer batchID = getBatchID();
        if (batchID >= 0 && commentProperties != null && batchID < commentProperties.size()) {
            JsonObject commentProperty = commentProperties.get(batchID);
            if (commentProperty != null &&
                    (commentProperty.has("line") || commentProperty.has("range"))) {
                String id = commentProperty.get("id").getAsString();
                String filename = commentProperty.get("filename").getAsString();
                Integer line = commentProperty.get("line").getAsInt();
                JsonObject range = commentProperty.getAsJsonObject("range");
                if (range != null) {
                    batchMap.put("id", id);
                    batchMap.put("filename", filename);
                    batchMap.put("line", line);
                    batchMap.put("range", range);
                }
            }
        }
        reviewBatches.add(batchMap);
    }

    private void splitReviewIntoBatches(String review) {
        String[] lines = review.split("\n");
        currentTag = "0";
        StringBuilder batch = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String[] extractResult = extractID(lines[i]);
            if (extractResult != null) {
                log.debug("Captured '{}' from line '{}'", extractResult[0], lines[i]);
                addReviewBatch(batch);
                batch = new StringBuilder();
                currentTag = extractResult[0];
                lines[i] = extractResult[1];
            }
            batch.append(lines[i]).append("\n");
        }
        if (batch.length() > 0) {
            addReviewBatch(batch);
        }
        log.info("Review batches created: {}", reviewBatches.size());
        log.debug("batches: {}", reviewBatches);
    }

    private String getReviewSuggestion(Configuration config, String changeId, String patchSet) throws Exception {
        List<String> patchLines = Arrays.asList(patchSet.split("\n"));
        if (patchLines.size() > config.getMaxReviewLines()) {
            log.warn("Patch set too large. Skipping review. changeId: {}", changeId);
            return String.format(SPLIT_REVIEW_MSG, config.getMaxReviewLines());
        }
        return openAiClient.ask(config, patchSet);
    }
}

