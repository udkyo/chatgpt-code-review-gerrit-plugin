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

@Slf4j
@Singleton
public class GerritClient {
    private static final String DEFAULT_CHANGE_ID = "DEFAULT_CHANGE_ID";
    private static GerritClientFacade gerritClientFacade;

    public void initialize(Configuration config) {
        initialize(config, DEFAULT_CHANGE_ID);
    }

    public void initialize(Configuration config, String fullChangeId) {
        log.debug("Initializing client instances for change: {}", fullChangeId);
        config.resetDynamicConfiguration();
        gerritClientFacade = SingletonManager.getInstance(GerritClientFacade.class, fullChangeId, config);
    }

    public String getPatchSet(String fullChangeId) throws Exception {
        return getPatchSet(fullChangeId, false);
    }

    public String getPatchSet(String fullChangeId, boolean isCommentEvent) throws Exception {
        updateGerritClientFacade(fullChangeId);
        return gerritClientFacade.getPatchSet(fullChangeId, isCommentEvent);
    }

    public boolean getForcedReview(String fullChangeId) {
        updateGerritClientFacade(fullChangeId);
        return gerritClientFacade.getForcedReview();
    }

    public boolean isDisabledUser(String authorUsername) {
        return gerritClientFacade.isDisabledUser(authorUsername);
    }

    public boolean isDisabledTopic(String topic) {
        return gerritClientFacade.isDisabledTopic(topic);
    }

    public HashMap<String, FileDiffProcessed> getFileDiffsProcessed(String fullChangeId) {
        updateGerritClientFacade(fullChangeId);
        return gerritClientFacade.getFileDiffsProcessed();
    }

    public Integer getGptAccountId(String fullChangeId) {
        updateGerritClientFacade(fullChangeId);
        return gerritClientFacade.getGptAccountId();
    }

    public List<GerritComment> getCommentProperties(String fullChangeId) {
        updateGerritClientFacade(fullChangeId);
        return gerritClientFacade.getCommentProperties();
    }

    public void setReview(String fullChangeId, List<ReviewBatch> reviewBatches, Integer reviewScore) throws Exception {
        updateGerritClientFacade(fullChangeId);
        gerritClientFacade.setReview(fullChangeId, reviewBatches, reviewScore);
    }

    public void setReview(String fullChangeId, List<ReviewBatch> reviewBatches) throws Exception {
        setReview(fullChangeId, reviewBatches, null);
    }

    public boolean retrieveLastComments(String fullChangeId, Event event) {
        updateGerritClientFacade(fullChangeId);
        return gerritClientFacade.retrieveLastComments(fullChangeId, event);
    }

    public String getUserRequests(String fullChangeId) {
        updateGerritClientFacade(fullChangeId);
        return gerritClientFacade.getUserRequests();
    }

    public void destroy(String fullChangeId) {
        log.debug("Destroying GerritClientFacade instance for change: {}", fullChangeId);
        SingletonManager.removeInstance(GerritClientFacade.class, fullChangeId);
    }

    private void updateGerritClientFacade(String fullChangeId) {
        gerritClientFacade = SingletonManager.getInstance(GerritClientFacade.class, fullChangeId);
    }

}
