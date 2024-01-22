package com.googlesource.gerrit.plugins.chatgpt.client;

import com.google.gerrit.server.events.Event;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.chatgpt.client.model.GerritComment;
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

    private GerritClientPatchSet gerritClientPatchSet;
    private GerritClientComments gerritClientComments;

    public void initialize(Configuration config) {
        initialize(config, DEFAULT_CHANGE_ID);
    }

    public void initialize(Configuration config, String fullChangeId) {
        log.debug("Initializing client instances for change: {}", fullChangeId);
        gerritClientPatchSet = SingletonManager.getInstance(GerritClientPatchSet.class, fullChangeId, config);
        gerritClientComments = SingletonManager.getInstance(GerritClientComments.class, fullChangeId, config);
    }

    public String getPatchSet(String fullChangeId) throws Exception {
        return getPatchSet(fullChangeId, false);
    }

    public String getPatchSet(String fullChangeId, boolean isCommentEvent) throws Exception {
        updateGerritClientPatchSet(fullChangeId);
        return gerritClientPatchSet.getPatchSet(fullChangeId, isCommentEvent);
    }

    public boolean isDisabledUser(String authorUsername) {
        return gerritClientPatchSet.isDisabledUser(authorUsername);
    }

    public boolean isDisabledTopic(String topic) {
        return gerritClientPatchSet.isDisabledTopic(topic);
    }

    public HashMap<String, FileDiffProcessed> getFileDiffsProcessed(String fullChangeId) {
        updateGerritClientPatchSet(fullChangeId);
        return gerritClientPatchSet.getFileDiffsProcessed();
    }

    public List<GerritComment> getCommentProperties(String fullChangeId) {
        updateGerritClientComments(fullChangeId);
        return gerritClientComments.getCommentProperties();
    }

    public void postComments(String fullChangeId, List<ReviewBatch> reviewBatches) throws Exception {
        updateGerritClientComments(fullChangeId);
        gerritClientComments.postComments(fullChangeId, reviewBatches);
    }

    public boolean retrieveLastComments(Event event, String fullChangeId) {
        updateGerritClientComments(fullChangeId);
        return gerritClientComments.retrieveLastComments(event, fullChangeId);
    }

    public String getUserPrompt() {
        HashMap<String, FileDiffProcessed> fileDiffsProcessed = gerritClientPatchSet.getFileDiffsProcessed();
        return gerritClientComments.getUserPrompt(fileDiffsProcessed);
    }

    public void destroy(String fullChangeId) {
        log.debug("Destroying client instances for change: {}", fullChangeId);
        SingletonManager.removeInstance(GerritClientPatchSet.class, fullChangeId);
        SingletonManager.removeInstance(GerritClientComments.class, fullChangeId);
    }

    private void updateGerritClientPatchSet(String fullChangeId) {
        gerritClientPatchSet = SingletonManager.getInstance(GerritClientPatchSet.class, fullChangeId);
    }

    private void updateGerritClientComments(String fullChangeId) {
        gerritClientComments = SingletonManager.getInstance(GerritClientComments.class, fullChangeId);
    }

}
