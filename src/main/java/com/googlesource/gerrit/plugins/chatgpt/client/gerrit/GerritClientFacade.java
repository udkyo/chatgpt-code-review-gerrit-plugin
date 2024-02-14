package com.googlesource.gerrit.plugins.chatgpt.client.gerrit;

import com.googlesource.gerrit.plugins.chatgpt.client.patch.diff.FileDiffProcessed;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import com.googlesource.gerrit.plugins.chatgpt.model.gerrit.GerritComment;
import com.googlesource.gerrit.plugins.chatgpt.model.gerrit.GerritPermittedVotingRange;
import com.googlesource.gerrit.plugins.chatgpt.model.review.ReviewBatch;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;

@Slf4j
public class GerritClientFacade {
    private final GerritClientDetail gerritClientDetail;
    private final GerritClientPatchSet gerritClientPatchSet;
    private final GerritClientComments gerritClientComments;
    private final GerritClientReview gerritClientReview;

    public GerritClientFacade(Configuration config) {
        gerritClientDetail = new GerritClientDetail(config);
        gerritClientPatchSet = new GerritClientPatchSet(config);
        gerritClientComments = new GerritClientComments(config);
        gerritClientReview = new GerritClientReview(config);
    }

    public void loadClientDetail(GerritChange change, Integer gptAccountId) {
        gerritClientDetail.loadClientDetail(change, gptAccountId);
    }

    public GerritPermittedVotingRange getPermittedVotingRange() {
        return gerritClientDetail.getPermittedVotingRange();
    }

    public String getPatchSet(GerritChange change) throws Exception {
        return gerritClientPatchSet.getPatchSet(change);
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

    public Integer getNotNullAccountId(String authorUsername) {
        return gerritClientPatchSet.getNotNullAccountId(authorUsername);
    }

    public List<GerritComment> getCommentProperties() {
        return gerritClientComments.getCommentProperties();
    }

    public void setReview(String fullChangeId, List<ReviewBatch> reviewBatches, Integer reviewScore) throws Exception {
        gerritClientReview.setReview(fullChangeId, reviewBatches, reviewScore);
    }

    public boolean retrieveLastComments(GerritChange change) {
        return gerritClientComments.retrieveLastComments(change);
    }

    public String getUserRequests(GerritChange change) {
        HashMap<String, FileDiffProcessed> fileDiffsProcessed = gerritClientPatchSet.getFileDiffsProcessed();
        List<GerritComment> detailComments = gerritClientDetail.getMessages();
        return gerritClientComments.getUserRequests(change, fileDiffsProcessed, detailComments);
    }

}
