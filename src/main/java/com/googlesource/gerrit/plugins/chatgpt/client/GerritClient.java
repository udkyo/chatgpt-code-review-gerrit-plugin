package com.googlesource.gerrit.plugins.chatgpt.client;

import com.google.gerrit.server.events.Event;
import com.google.inject.Singleton;
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
        GET_COMMENTS,
        POST_COMMENTS
    }

    private static final Map<GerritClientType, Class<?>> clientMap = Map.of(
        GerritClientType.PATCH_SET, GerritClientPatchSet.class,
        GerritClientType.GET_COMMENTS, GerritClientGetComments.class,
        GerritClientType.POST_COMMENTS, GerritClientPostComments.class
    );
    private static final String DEFAULT_CHANGE_ID = "DEFAULT_CHANGE_ID";

    private GerritClientPatchSet gerritClientPatchSet;
    private GerritClientGetComments gerritClientGetComments;
    private GerritClientPostComments gerritClientPostComments;

    public void initialize(Configuration config) {
        initialize(config, DEFAULT_CHANGE_ID);
    }

    public void initialize(Configuration config, String fullChangeId) {
        log.debug("Initializing client instances for change: {}", fullChangeId);
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

    public List<GerritComment> getCommentProperties(String fullChangeId) {
        updateGerritClient(GerritClientType.GET_COMMENTS, fullChangeId);
        return gerritClientGetComments.getCommentProperties();
    }

    public void postComments(String fullChangeId, List<ReviewBatch> reviewBatches) throws Exception {
        updateGerritClient(GerritClientType.POST_COMMENTS, fullChangeId);
        gerritClientPostComments.postComments(fullChangeId, reviewBatches);
    }

    public boolean retrieveLastComments(Event event, String fullChangeId) {
        updateGerritClient(GerritClientType.GET_COMMENTS, fullChangeId);
        return gerritClientGetComments.retrieveLastComments(event, fullChangeId);
    }

    public String getUserPrompt(String fullChangeId) {
        updateGerritClient(GerritClientType.GET_COMMENTS, fullChangeId);
        updateGerritClient(GerritClientType.PATCH_SET, fullChangeId);
        HashMap<String, FileDiffProcessed> fileDiffsProcessed = gerritClientPatchSet.getFileDiffsProcessed();
        return gerritClientGetComments.getUserPrompt(fileDiffsProcessed);
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
            case GET_COMMENTS:
                gerritClientGetComments = (GerritClientGetComments) clientInstance;
                break;
            case POST_COMMENTS:
                gerritClientPostComments = (GerritClientPostComments) clientInstance;
                break;
        }
    }

}
