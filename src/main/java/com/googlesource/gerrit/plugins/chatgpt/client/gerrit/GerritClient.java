package com.googlesource.gerrit.plugins.chatgpt.client.gerrit;

import com.google.gerrit.server.events.Event;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.chatgpt.client.FileDiffProcessed;
import com.googlesource.gerrit.plugins.chatgpt.client.model.gerrit.GerritComment;
import com.googlesource.gerrit.plugins.chatgpt.client.model.ReviewBatch;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import com.googlesource.gerrit.plugins.chatgpt.utils.SingletonManager;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Singleton
public class GerritClient {
    private enum GerritClientType {
        PATCH_SET,
        COMMENTS,
        REVIEW
    }

    private static final Map<GerritClientType, Class<?>> clientMap = Map.of(
        GerritClientType.PATCH_SET, GerritClientPatchSet.class,
        GerritClientType.COMMENTS, GerritClientComments.class,
        GerritClientType.REVIEW, GerritClientReview.class
    );
    private static final String DEFAULT_CHANGE_ID = "DEFAULT_CHANGE_ID";

    private GerritClientPatchSet gerritClientPatchSet;
    private GerritClientComments gerritClientComments;
    private GerritClientReview gerritClientReview;

    public void initialize(Configuration config) {
        initialize(config, DEFAULT_CHANGE_ID);
    }

    public void initialize(Configuration config, String fullChangeId) {
        log.debug("Initializing client instances for change: {}", fullChangeId);
        config.resetDynamicConfiguration();
        for (GerritClientType clientTypes : clientMap.keySet()) {
            updateGerritClient(clientTypes, fullChangeId, config);
        }
    }

    public String getPatchSet(String fullChangeId) throws Exception {
        return getPatchSet(fullChangeId, false);
    }

    public String getPatchSet(String fullChangeId, boolean isCommentEvent) throws Exception {
        updateGerritClient(GerritClientType.PATCH_SET, fullChangeId);
        return gerritClientPatchSet.getPatchSet(fullChangeId, isCommentEvent);
    }

    public boolean isDisabledUser(String authorUsername) {
        return gerritClientPatchSet.isDisabledUser(authorUsername);
    }

    public boolean isDisabledTopic(String topic) {
        return gerritClientPatchSet.isDisabledTopic(topic);
    }

    public HashMap<String, FileDiffProcessed> getFileDiffsProcessed(String fullChangeId) {
        updateGerritClient(GerritClientType.PATCH_SET, fullChangeId);
        return gerritClientPatchSet.getFileDiffsProcessed();
    }

    public Integer getGptAccountId(String fullChangeId) {
        updateGerritClient(GerritClientType.COMMENTS, fullChangeId);
        return gerritClientComments.getGptAccountId();
    }

    public List<GerritComment> getCommentProperties(String fullChangeId) {
        updateGerritClient(GerritClientType.COMMENTS, fullChangeId);
        return gerritClientComments.getCommentProperties();
    }

    public void setReview(String fullChangeId, List<ReviewBatch> reviewBatches, Integer reviewScore) throws Exception {
        updateGerritClient(GerritClientType.REVIEW, fullChangeId);
        gerritClientReview.setReview(fullChangeId, reviewBatches, reviewScore);
    }

    public void setReview(String fullChangeId, List<ReviewBatch> reviewBatches) throws Exception {
        setReview(fullChangeId, reviewBatches, null);
    }

    public boolean retrieveLastComments(Event event, String fullChangeId) {
        updateGerritClient(GerritClientType.COMMENTS, fullChangeId);
        return gerritClientComments.retrieveLastComments(event, fullChangeId);
    }

    public String getUserRequests(String fullChangeId) {
        updateGerritClient(GerritClientType.COMMENTS, fullChangeId);
        updateGerritClient(GerritClientType.PATCH_SET, fullChangeId);
        HashMap<String, FileDiffProcessed> fileDiffsProcessed = gerritClientPatchSet.getFileDiffsProcessed();
        return gerritClientComments.getUserRequests(fileDiffsProcessed);
    }

    public void destroy(String fullChangeId) {
        log.debug("Destroying client instances for change: {}", fullChangeId);
        for (Map.Entry<GerritClientType, Class<?>> client : clientMap.entrySet()) {
            SingletonManager.removeInstance(client.getValue(), fullChangeId);
        }
    }

    private void updateGerritClient(GerritClientType clientType, String fullChangeId, Object... constructorArgs) {
        Object clientInstance = SingletonManager.getInstance(clientMap.get(clientType), fullChangeId, constructorArgs);
        switch (clientType) {
            case PATCH_SET:
                gerritClientPatchSet = (GerritClientPatchSet) clientInstance;
                break;
            case COMMENTS:
                gerritClientComments = (GerritClientComments) clientInstance;
                break;
            case REVIEW:
                gerritClientReview = (GerritClientReview) clientInstance;
                break;
        }
    }

}
