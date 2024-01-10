package com.googlesource.gerrit.plugins.chatgpt;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.chatgpt.client.*;
import com.googlesource.gerrit.plugins.chatgpt.client.model.ChatGptSuggestionPoint;
import com.googlesource.gerrit.plugins.chatgpt.client.model.GerritCodeRange;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Type;
import java.util.*;

import static com.googlesource.gerrit.plugins.chatgpt.utils.ReviewUtils.extractID;

@Slf4j
public class PatchSetReviewer {
    private static final String SPLIT_REVIEW_MSG = "Too many changes. Please consider splitting into patches smaller " +
            "than %s lines for review.";

    private final Gson gson = new Gson();
    private final GerritClient gerritClient;
    private final OpenAiClient openAiClient;

    private List<HashMap<String, Object>> reviewBatches;
    private List<JsonObject> commentProperties;
    private HashMap<String, FileDiffProcessed> fileDiffsProcessed;
    @Setter
    private boolean isCommentEvent;

    @Inject
    PatchSetReviewer(GerritClient gerritClient, OpenAiClient openAiClient) {
        this.gerritClient = gerritClient;
        this.openAiClient = openAiClient;
    }

    public void review(Configuration config, String fullChangeId) throws Exception {
        reviewBatches = new ArrayList<>();
        commentProperties = gerritClient.getCommentProperties(fullChangeId);
        String patchSet = gerritClient.getPatchSet(fullChangeId, isCommentEvent);
        if (patchSet.isEmpty()) {
            log.info("No file to review has been found in the PatchSet");
            return;
        }
        config.configureDynamically(Configuration.KEY_GPT_USER_PROMPT, gerritClient.getUserPrompt());

        String reviewSuggestion = getReviewSuggestion(config, fullChangeId, patchSet);
        log.debug("ChatGPT response: {}", reviewSuggestion);
        if (isCommentEvent || config.getGptReviewByPoints()) {
            retrieveReviewFromJson(reviewSuggestion, fullChangeId);
        }
        else {
            splitReviewIntoBatches(reviewSuggestion);
        }

        gerritClient.postComments(fullChangeId, reviewBatches);
    }

    private Integer getBatchId(String currentTag) {
        try {
            return Integer.parseInt(currentTag);
        }
        catch (NumberFormatException ex){
            return null;
        }
    }

    private void addReviewBatch(Integer batchID, String batch) {
        HashMap<String, Object> batchMap = new HashMap<>();
        batchMap.put("content", batch);
        if (commentProperties != null && batchID < commentProperties.size()) {
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

    private Optional<GerritCodeRange> getGerritCommentCodeRange(ChatGptSuggestionPoint suggestion) {
        Optional<GerritCodeRange> gerritCommentRange = Optional.empty();
        if (suggestion.getFilename() == null) {
            return gerritCommentRange;
        }
        String filename = suggestion.getFilename();
        if (filename.equals("/COMMIT_MSG")) {
            return gerritCommentRange;
        }
        if (!fileDiffsProcessed.containsKey(filename)) {
            log.info("Filename '{}' not found for suggestion '{}'.\nFileDiffsProcessed = {}", filename, suggestion,
                    fileDiffsProcessed);
            return gerritCommentRange;
        }
        InlineCode inlineCode = new InlineCode(fileDiffsProcessed.get(filename));
        gerritCommentRange = inlineCode.findCommentRange(suggestion);
        if (gerritCommentRange.isEmpty()) {
            log.info("Inline code not found for suggestion {}", suggestion);
        }
        return gerritCommentRange;
    }

    private void retrieveReviewFromJson(String review, String fullChangeId) {
        review = review.replaceAll("^`*(?:json)?\\s*|\\s*`+$", "");
        Type chatGptResponseListType = new TypeToken<List<ChatGptSuggestionPoint>>(){}.getType();
        List<ChatGptSuggestionPoint> reviewJson = gson.fromJson(review, chatGptResponseListType);
        fileDiffsProcessed = gerritClient.getFileDiffsProcessed(fullChangeId);
        for (ChatGptSuggestionPoint suggestion : reviewJson) {
            HashMap<String, Object> batchMap = new HashMap<>();
            if (suggestion.getId() != null) {
                addReviewBatch(suggestion.getId(), suggestion.getSuggestion());
            }
            else {
                batchMap.put("content", suggestion.getSuggestion());
                Optional<GerritCodeRange> optGerritCommentRange = getGerritCommentCodeRange(suggestion);
                if (optGerritCommentRange.isPresent()) {
                    GerritCodeRange gerritCodeRange = optGerritCommentRange.get();
                    batchMap.put("filename", suggestion.getFilename());
                    batchMap.put("line", gerritCodeRange.getStart_line());
                    batchMap.put("range", gerritCodeRange);
                }
                reviewBatches.add(batchMap);
            }
        }
    }

    private void splitReviewIntoBatches(String review) {
        String[] lines = review.split("\n");
        Integer currentTag = 0;
        StringBuilder batch = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String[] extractResult = extractID(lines[i]);
            if (extractResult != null) {
                log.debug("Captured '{}' from line '{}'", extractResult[0], lines[i]);
                addReviewBatch(currentTag, batch.toString());
                batch = new StringBuilder();
                currentTag = getBatchId(extractResult[0]);
                lines[i] = extractResult[1];
            }
            batch.append(lines[i]).append("\n");
        }
        if (batch.length() > 0) {
            addReviewBatch(currentTag, batch.toString());
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
        return openAiClient.ask(config, patchSet, isCommentEvent);
    }
}

