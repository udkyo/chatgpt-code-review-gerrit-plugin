package com.googlesource.gerrit.plugins.chatgpt;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.chatgpt.client.chatgpt.ChatGptClient;
import com.googlesource.gerrit.plugins.chatgpt.client.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.chatgpt.client.gerrit.GerritClient;
import com.googlesource.gerrit.plugins.chatgpt.client.gerrit.GerritClientReview;
import com.googlesource.gerrit.plugins.chatgpt.client.patch.comment.GerritCommentRange;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import com.googlesource.gerrit.plugins.chatgpt.model.chatgpt.ChatGptReplyItem;
import com.googlesource.gerrit.plugins.chatgpt.model.chatgpt.ChatGptResponseContent;
import com.googlesource.gerrit.plugins.chatgpt.model.gerrit.GerritCodeRange;
import com.googlesource.gerrit.plugins.chatgpt.model.gerrit.GerritComment;
import com.googlesource.gerrit.plugins.chatgpt.model.review.ReviewBatch;
import com.googlesource.gerrit.plugins.chatgpt.settings.DynamicSettings;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class PatchSetReviewer {
    private static final String SPLIT_REVIEW_MSG = "Too many changes. Please consider splitting into patches smaller " +
            "than %s lines for review.";

    private final Gson gson = new Gson();
    private final GerritClient gerritClient;
    private final ChatGptClient chatGptClient;

    private GerritCommentRange gerritCommentRange;
    private List<ReviewBatch> reviewBatches;
    private List<GerritComment> commentProperties;

    @Inject
    PatchSetReviewer(GerritClient gerritClient, ChatGptClient chatGptClient) {
        this.gerritClient = gerritClient;
        this.chatGptClient = chatGptClient;
    }

    public void review(Configuration config, GerritChange change) throws Exception {
        reviewBatches = new ArrayList<>();
        commentProperties = gerritClient.getClientData(change).getCommentProperties();
        gerritCommentRange = new GerritCommentRange(gerritClient, change);
        GerritClientReview gerritClientReview = new GerritClientReview(config);
        String patchSet = gerritClient.getPatchSet(change);
        if (patchSet.isEmpty()) {
            log.info("No file to review has been found in the PatchSet");
            return;
        }
        DynamicSettings.update(config, change, gerritClient);

        String reviewReply = getReviewReply(config, change, patchSet);
        log.debug("ChatGPT response: {}", reviewReply);

        ChatGptResponseContent reviewJson = gson.fromJson(reviewReply, ChatGptResponseContent.class);
        retrieveReviewBatches(reviewJson, change);
        gerritClientReview.setReview(change.getFullChangeId(), reviewBatches, reviewJson.getScore());
    }

    private void setCommentBatchMap(ReviewBatch batchMap, Integer batchID) {
        if (commentProperties != null && batchID < commentProperties.size()) {
            GerritComment commentProperty = commentProperties.get(batchID);
            if (commentProperty != null && (commentProperty.getLine() != null || commentProperty.getRange() != null)) {
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
    }

    private void setPatchSetReviewBatchMap(ReviewBatch batchMap, ChatGptReplyItem replyItem) {
        Optional<GerritCodeRange> optGerritCommentRange = gerritCommentRange.getGerritCommentRange(replyItem);
        if (optGerritCommentRange.isPresent()) {
            GerritCodeRange gerritCodeRange = optGerritCommentRange.get();
            batchMap.setFilename(replyItem.getFilename());
            batchMap.setLine(gerritCodeRange.getStartLine());
            batchMap.setRange(gerritCodeRange);
        }
    }

    private void retrieveReviewBatches(ChatGptResponseContent reviewJson, GerritChange change) {
        for (ChatGptReplyItem replyItem : reviewJson.getReplies()) {
            if (replyItem.isRepeated() || replyItem.isConflicting()) {
                continue;
            }
            ReviewBatch batchMap = new ReviewBatch();
            batchMap.setContent(replyItem.getReply());
            if (change.getIsCommentEvent() && replyItem.getId() != null) {
                setCommentBatchMap(batchMap, replyItem.getId());
            }
            else {
                setPatchSetReviewBatchMap(batchMap, replyItem);
            }
            reviewBatches.add(batchMap);
        }
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
