package com.googlesource.gerrit.plugins.chatgpt;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.chatgpt.client.*;
import com.googlesource.gerrit.plugins.chatgpt.client.chatgpt.ChatGptClient;
import com.googlesource.gerrit.plugins.chatgpt.client.gerrit.GerritClient;
import com.googlesource.gerrit.plugins.chatgpt.client.gerrit.GerritClientDetail;
import com.googlesource.gerrit.plugins.chatgpt.client.model.chatGpt.ChatGptReplyItem;
import com.googlesource.gerrit.plugins.chatgpt.client.model.chatGpt.ChatGptResponseContent;
import com.googlesource.gerrit.plugins.chatgpt.client.model.gerrit.GerritPatchSetDetail;
import com.googlesource.gerrit.plugins.chatgpt.client.model.gerrit.GerritCodeRange;
import com.googlesource.gerrit.plugins.chatgpt.client.model.gerrit.GerritComment;
import com.googlesource.gerrit.plugins.chatgpt.client.model.ReviewBatch;
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
    private final ChatGptClient chatGptClient;

    private List<ReviewBatch> reviewBatches;
    private List<GerritComment> commentProperties;
    private HashMap<String, FileDiffProcessed> fileDiffsProcessed;
    @Setter
    private boolean isCommentEvent;

    @Inject
    PatchSetReviewer(GerritClient gerritClient, ChatGptClient chatGptClient) {
        this.gerritClient = gerritClient;
        this.chatGptClient = chatGptClient;
    }

    public void review(Configuration config, String fullChangeId) throws Exception {
        reviewBatches = new ArrayList<>();
        commentProperties = gerritClient.getCommentProperties(fullChangeId);
        String patchSet = gerritClient.getPatchSet(fullChangeId, isCommentEvent);
        if (patchSet.isEmpty()) {
            log.info("No file to review has been found in the PatchSet");
            return;
        }
        updateDynamicConfiguration(config, fullChangeId);

        String reviewReply = getReviewReply(config, fullChangeId, patchSet);
        log.debug("ChatGPT response: {}", reviewReply);
        ChatGptResponseContent reviewJson = gson.fromJson(reviewReply, ChatGptResponseContent.class);
        retrieveReviewFromJson(reviewJson, fullChangeId);

        gerritClient.setReview(fullChangeId, reviewBatches, reviewJson.getScore());
    }

    private void updateDynamicConfiguration(Configuration config, String fullChangeId) {
        config.configureDynamically(Configuration.KEY_GPT_REQUEST_USER_PROMPT,
                gerritClient.getUserRequests(fullChangeId));
        config.configureDynamically(Configuration.KEY_COMMENT_PROPERTIES_SIZE, commentProperties.size());
        if (config.isVotingEnabled() && !isCommentEvent) {
            GerritClientDetail gerritClientDetail = new GerritClientDetail(config,
                    gerritClient.getGptAccountId(fullChangeId));
            GerritPatchSetDetail.PermittedVotingRange permittedVotingRange = gerritClientDetail.getPermittedVotingRange(
                    fullChangeId);
            if (permittedVotingRange != null) {
                if (permittedVotingRange.getMin() > config.getVotingMinScore()) {
                    log.debug("Minimum ChatGPT voting score set to {}", permittedVotingRange.getMin());
                    config.configureDynamically(Configuration.KEY_VOTING_MIN_SCORE, permittedVotingRange.getMin());
                }
                if (permittedVotingRange.getMax() < config.getVotingMaxScore()) {
                    log.debug("Maximum ChatGPT voting score set to {}", permittedVotingRange.getMax());
                    config.configureDynamically(Configuration.KEY_VOTING_MAX_SCORE, permittedVotingRange.getMax());
                }
            }
        }
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

    private Optional<GerritCodeRange> getGerritCommentCodeRange(ChatGptReplyItem replyItem) {
        Optional<GerritCodeRange> gerritCommentRange = Optional.empty();
        if (replyItem.getFilename() == null) {
            return gerritCommentRange;
        }
        String filename = replyItem.getFilename();
        if (filename.equals("/COMMIT_MSG")) {
            return gerritCommentRange;
        }
        if (!fileDiffsProcessed.containsKey(filename)) {
            log.info("Filename '{}' not found for reply '{}'.\nFileDiffsProcessed = {}", filename, replyItem,
                    fileDiffsProcessed);
            return gerritCommentRange;
        }
        InlineCode inlineCode = new InlineCode(fileDiffsProcessed.get(filename));
        gerritCommentRange = inlineCode.findCommentRange(replyItem);
        if (gerritCommentRange.isEmpty()) {
            log.info("Inline code not found for reply {}", replyItem);
        }
        return gerritCommentRange;
    }

    private void retrieveReviewFromJson(ChatGptResponseContent reviewJson, String fullChangeId) {
        fileDiffsProcessed = gerritClient.getFileDiffsProcessed(fullChangeId);
        for (ChatGptReplyItem replyItem : reviewJson.getReplies()) {
            ReviewBatch batchMap = new ReviewBatch();
            if (isCommentEvent && replyItem.getId() != null) {
                addReviewBatch(replyItem.getId(), replyItem.getReply());
            }
            else {
                batchMap.setContent(replyItem.getReply());
                Optional<GerritCodeRange> optGerritCommentRange = getGerritCommentCodeRange(replyItem);
                if (optGerritCommentRange.isPresent()) {
                    GerritCodeRange gerritCodeRange = optGerritCommentRange.get();
                    batchMap.setFilename(replyItem.getFilename());
                    batchMap.setLine(gerritCodeRange.getStartLine());
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
        return chatGptClient.ask(config, changeId, patchSet, isCommentEvent);
    }
}

