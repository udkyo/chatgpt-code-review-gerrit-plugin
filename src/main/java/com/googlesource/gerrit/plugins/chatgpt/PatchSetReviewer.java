package com.googlesource.gerrit.plugins.chatgpt;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.chatgpt.client.*;
import com.googlesource.gerrit.plugins.chatgpt.client.model.*;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class PatchSetReviewer {
    private static final String SPLIT_REVIEW_MSG = "Too many changes. Please consider splitting into patches smaller " +
            "than %s lines for review.";

    private final Gson gson = new Gson();
    private final GerritClient gerritClient;
    private final OpenAiClient openAiClient;

    private List<ReviewBatch> reviewBatches;
    private List<GerritComment> commentProperties;
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
        config.configureDynamically(Configuration.KEY_COMMENT_PROPERTIES_SIZE, commentProperties.size());

        String reviewReply = getReviewReply(config, fullChangeId, patchSet);
        log.debug("ChatGPT response: {}", reviewReply);
        retrieveReviewFromJson(reviewReply, fullChangeId);

        gerritClient.postComments(fullChangeId, reviewBatches);
    }

    private void addReviewBatch(Integer batchID, String batch) {
        ReviewBatch batchMap = new ReviewBatch();
        batchMap.setContent(batch);
        if (commentProperties != null && batchID < commentProperties.size()) {
            GerritComment commentProperty = commentProperties.get(batchID);
            if (commentProperty != null &&
                    (commentProperty.getLine() != null || commentProperty.getRange() != null)) {
                String id = commentProperty.getId();
                String filename = commentProperty.getFilename();
                Integer line = commentProperty.getLine();
                GerritCodeRange range = commentProperty.getRange();
                if (range != null) {
                    batchMap.setId(id);
                    batchMap.setFilename(filename);
                    batchMap.setLine(line);
                    batchMap.setRange(range);
                }
            }
        }
        reviewBatches.add(batchMap);
    }

    private Optional<GerritCodeRange> getGerritCommentCodeRange(ChatGptReplyPoint reply) {
        Optional<GerritCodeRange> gerritCommentRange = Optional.empty();
        if (reply.getFilename() == null) {
            return gerritCommentRange;
        }
        String filename = reply.getFilename();
        if (filename.equals("/COMMIT_MSG")) {
            return gerritCommentRange;
        }
        if (!fileDiffsProcessed.containsKey(filename)) {
            log.info("Filename '{}' not found for reply '{}'.\nFileDiffsProcessed = {}", filename, reply,
                    fileDiffsProcessed);
            return gerritCommentRange;
        }
        InlineCode inlineCode = new InlineCode(fileDiffsProcessed.get(filename));
        gerritCommentRange = inlineCode.findCommentRange(reply);
        if (gerritCommentRange.isEmpty()) {
            log.info("Inline code not found for reply {}", reply);
        }
        return gerritCommentRange;
    }

    private void retrieveReviewFromJson(String review, String fullChangeId) {
        ChatGptReplies reviewJson = gson.fromJson(review, ChatGptReplies.class);
        fileDiffsProcessed = gerritClient.getFileDiffsProcessed(fullChangeId);
        for (ChatGptReplyPoint reply : reviewJson.getReplies()) {
            ReviewBatch batchMap = new ReviewBatch();
            if (isCommentEvent && reply.getId() != null) {
                addReviewBatch(reply.getId(), reply.getReply());
            }
            else {
                batchMap.setContent(reply.getReply());
                Optional<GerritCodeRange> optGerritCommentRange = getGerritCommentCodeRange(reply);
                if (optGerritCommentRange.isPresent()) {
                    GerritCodeRange gerritCodeRange = optGerritCommentRange.get();
                    batchMap.setFilename(reply.getFilename());
                    batchMap.setLine(gerritCodeRange.getStart_line());
                    batchMap.setRange(gerritCodeRange);
                }
                reviewBatches.add(batchMap);
            }
        }
        log.debug("fileDiffsProcessed Keys: {}", fileDiffsProcessed.keySet());
    }

    private String getReviewReply(Configuration config, String changeId, String patchSet) throws Exception {
        List<String> patchLines = Arrays.asList(patchSet.split("\n"));
        if (patchLines.size() > config.getMaxReviewLines()) {
            log.warn("Patch set too large. Skipping review. changeId: {}", changeId);
            return String.format(SPLIT_REVIEW_MSG, config.getMaxReviewLines());
        }
        return openAiClient.ask(config, changeId, patchSet, isCommentEvent);
    }
}

