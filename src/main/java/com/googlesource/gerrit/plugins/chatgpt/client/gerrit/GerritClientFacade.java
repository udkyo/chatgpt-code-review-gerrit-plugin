package com.googlesource.gerrit.plugins.chatgpt.client.gerrit;

import com.google.gerrit.server.events.Event;
import com.googlesource.gerrit.plugins.chatgpt.client.FileDiffProcessed;
import com.googlesource.gerrit.plugins.chatgpt.client.model.ReviewBatch;
import com.googlesource.gerrit.plugins.chatgpt.client.model.gerrit.GerritComment;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;

@Slf4j
public class GerritClientFacade {
    private final GerritClientPatchSet gerritClientPatchSet;
    private final GerritClientComments gerritClientComments;
    private final GerritClientReview gerritClientReview;

    public GerritClientFacade(Configuration config) {
        config.resetDynamicConfiguration();
        gerritClientPatchSet = new GerritClientPatchSet(config);
        gerritClientComments = new GerritClientComments(config);
        gerritClientReview = new GerritClientReview(config);
    }

    public String getPatchSet(String fullChangeId, boolean isCommentEvent) throws Exception {
        return gerritClientPatchSet.getPatchSet(fullChangeId, isCommentEvent);
    }

    public boolean getForcedReview() {
        return gerritClientComments.getForcedReview();
    }

    public boolean isDisabledUser(String authorUsername) {
        return gerritClientPatchSet.isDisabledUser(authorUsername);
    }

    public boolean isDisabledTopic(String topic) {
        return gerritClientPatchSet.isDisabledTopic(topic);
    }

    public HashMap<String, FileDiffProcessed> getFileDiffsProcessed() {
        return gerritClientPatchSet.getFileDiffsProcessed();
    }

    public Integer getGptAccountId() {
        return gerritClientComments.getGptAccountId();
    }

    public List<GerritComment> getCommentProperties() {
        return gerritClientComments.getCommentProperties();
    }

    public void setReview(String fullChangeId, List<ReviewBatch> reviewBatches, Integer reviewScore) throws Exception {
        gerritClientReview.setReview(fullChangeId, reviewBatches, reviewScore);
    }

    public boolean retrieveLastComments(String fullChangeId, Event event) {
        return gerritClientComments.retrieveLastComments(event, fullChangeId);
    }

    public String getUserRequests() {
        HashMap<String, FileDiffProcessed> fileDiffsProcessed = gerritClientPatchSet.getFileDiffsProcessed();
        return gerritClientComments.getUserRequests(fileDiffsProcessed);
    }

}
