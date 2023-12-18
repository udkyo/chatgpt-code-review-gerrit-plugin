package com.googlesource.gerrit.plugins.chatgpt.client;

import com.google.gerrit.server.events.Event;
import com.google.gson.JsonObject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;

@Slf4j
@Singleton
public class GerritClient {

    private GerritClientPatchSet gerritClientPatchSet;
    private GerritClientComments gerritClientComments;

    public void initialize(Configuration config) {
        gerritClientPatchSet = new GerritClientPatchSet(config);
        gerritClientComments = new GerritClientComments(config);
    }

    public String getPatchSet(String fullChangeId) throws Exception {
        return getPatchSet(fullChangeId, false);
    }

    public String getPatchSet(String fullChangeId, boolean isCommentEvent) throws Exception {
        return gerritClientPatchSet.getPatchSet(fullChangeId, isCommentEvent);
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

    public List<JsonObject> getCommentProperties() {
        return gerritClientComments.getCommentProperties();
    }

    public void postComments(String fullChangeId, List<HashMap<String, Object>> reviewBatches) throws Exception {
        gerritClientComments.postComments(fullChangeId, reviewBatches);
    }

    public boolean retrieveLastComments(Event event, String fullChangeId) {
        return gerritClientComments.retrieveLastComments(event, fullChangeId);
    }

    public String getUserPrompt() {
        return gerritClientComments.getUserPrompt(getFileDiffsProcessed());
    }

}
