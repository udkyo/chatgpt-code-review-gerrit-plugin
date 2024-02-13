package com.googlesource.gerrit.plugins.chatgpt;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.chatgpt.client.*;
import com.googlesource.gerrit.plugins.chatgpt.client.chatgpt.ChatGptClient;
import com.googlesource.gerrit.plugins.chatgpt.client.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.chatgpt.client.gerrit.GerritClient;
import com.googlesource.gerrit.plugins.chatgpt.client.model.chatGpt.ChatGptReplyItem;
import com.googlesource.gerrit.plugins.chatgpt.client.model.chatGpt.ChatGptResponseContent;
import com.googlesource.gerrit.plugins.chatgpt.client.model.gerrit.GerritCodeRange;
import com.googlesource.gerrit.plugins.chatgpt.client.model.gerrit.GerritComment;
import com.googlesource.gerrit.plugins.chatgpt.client.model.ReviewBatch;
import com.googlesource.gerrit.plugins.chatgpt.client.model.gerrit.GerritPermittedVotingRange;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import com.googlesource.gerrit.plugins.chatgpt.utils.SingletonManager;
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

    @Inject
    PatchSetReviewer(GerritClient gerritClient, ChatGptClient chatGptClient) {
        this.gerritClient = gerritClient;
        this.chatGptClient = chatGptClient;
    }

    public void review(Configuration config, GerritChange change) throws Exception {
        reviewBatches = new ArrayList<>();
        commentProperties = gerritClient.getCommentProperties(change);
        String patchSet = gerritClient.getPatchSet(change);
        if (patchSet.isEmpty()) {
            log.info("No file to review has been found in the PatchSet");
            return;
        }
        updateDynamicSettings(config, change);

        String reviewReply = getReviewReply(config, change, patchSet);
        log.debug("ChatGPT response: {}", reviewReply);
        ChatGptResponseContent reviewJson = gson.fromJson(reviewReply, ChatGptResponseContent.class);
        retrieveReviewFromJson(reviewJson, change);

        gerritClient.setReview(change, reviewBatches, reviewJson.getScore());
    }

    private void updateDynamicSettings(Configuration config, GerritChange change) {
        DynamicSettings dynamicSettings = SingletonManager.getInstance(DynamicSettings.class, change);

        dynamicSettings.setCommentPropertiesSize(commentProperties.size());
        dynamicSettings.setGptRequestUserPrompt(gerritClient.getUserRequests(change));
        if (config.isVotingEnabled() && !change.getIsCommentEvent()) {
            GerritPermittedVotingRange permittedVotingRange = gerritClient.getPermittedVotingRange(change);
            if (permittedVotingRange != null) {
                if (permittedVotingRange.getMin() > config.getVotingMinScore()) {
                    log.debug("Minimum ChatGPT voting score set to {}", permittedVotingRange.getMin());
                    dynamicSettings.setVotingMinScore(permittedVotingRange.getMin());
                }
                if (permittedVotingRange.getMax() < config.getVotingMaxScore()) {
                    log.debug("Maximum ChatGPT voting score set to {}", permittedVotingRange.getMax());
                    dynamicSettings.setVotingMaxScore(permittedVotingRange.getMax());
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
        String filename = replyItem.getFilename();
        if (filename == null || filename.equals("/COMMIT_MSG")) {
            return gerritCommentRange;
        }
        if (replyItem.getCodeSnippet() == null) {
            log.info("CodeSnippet is null in reply '{}'.", replyItem);
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

    private void retrieveReviewFromJson(ChatGptResponseContent reviewJson, GerritChange change) {
        fileDiffsProcessed = gerritClient.getFileDiffsProcessed(change);
        for (ChatGptReplyItem replyItem : reviewJson.getReplies()) {
            ReviewBatch batchMap = new ReviewBatch();
            if (change.getIsCommentEvent() && replyItem.getId() != null) {
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

    private String getReviewReply(Configuration config, GerritChange change, String patchSet) throws Exception {
        List<String> patchLines = Arrays.asList(patchSet.split("\n"));
        if (patchLines.size() > config.getMaxReviewLines()) {
            log.warn("Patch set too large. Skipping review. changeId: {}", change.getFullChangeId());
            return String.format(SPLIT_REVIEW_MSG, config.getMaxReviewLines());
        }
        return chatGptClient.ask(config, change, patchSet);
    }
}

