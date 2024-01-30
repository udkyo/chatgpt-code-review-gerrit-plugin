package com.googlesource.gerrit.plugins.chatgpt.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.googlesource.gerrit.plugins.chatgpt.client.model.InputFileDiff;
import com.googlesource.gerrit.plugins.chatgpt.client.model.OutputFileDiff;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.*;

@Slf4j
public class GerritClientPatchSet extends GerritClientAccount {
    private final Gson gson = new GsonBuilder()
            .disableHtmlEscaping()
            .create();
    private final List<String> diffs;
    private boolean isCommitMessage;

    public GerritClientPatchSet(Configuration config) {
        super(config);
        diffs = new ArrayList<>();
    }

    public String getPatchSet(String fullChangeId, boolean isCommentEvent) throws Exception {
        int revisionBase = isCommentEvent ? 0 : retrieveRevisionBase(fullChangeId);
        log.debug("Revision base: {}", revisionBase);

        List<String> files = getAffectedFiles(fullChangeId, revisionBase);
        log.debug("Patch files: {}", files);

        String fileDiffsJson = getFileDiffsJson(fullChangeId, files, revisionBase);
        log.debug("File diffs: {}", fileDiffsJson);

        return fileDiffsJson;
    }

    private int retrieveRevisionBase(String fullChangeId) throws Exception {
        URI uri = URI.create(config.getGerritAuthBaseUrl()
                + UriResourceLocator.gerritPatchSetRevisionsUri(fullChangeId));
        log.debug("Retrieve Revision URI: '{}'", uri);
        JsonObject reviews = forwardGetRequestReturnJsonObject(uri);
        try {
            Set<String> revisions = reviews.get("revisions").getAsJsonObject().keySet();
            return revisions.size() -1;
        }
        catch (Exception e) {
            log.error("Could not retrieve revisions for PatchSet with fullChangeId: {}", fullChangeId, e);
            throw e;
        }
    }

    private List<String> getAffectedFiles(String fullChangeId, int revisionBase) throws Exception {
        URI uri = URI.create(config.getGerritAuthBaseUrl()
                + UriResourceLocator.gerritPatchSetFilesUri(fullChangeId)
                + UriResourceLocator.gerritRevisionBasePostfixUri(revisionBase));
        log.debug("Affected Files URI: '{}'", uri);
        JsonObject affectedFileMap = forwardGetRequestReturnJsonObject(uri);
        List<String> files = new ArrayList<>();
        for (Map.Entry<String, JsonElement> file : affectedFileMap.entrySet()) {
            String filename = file.getKey();
            if (!filename.equals("/COMMIT_MSG") || config.getGptReviewCommitMessages()) {
                int size = Integer.parseInt(file.getValue().getAsJsonObject().get("size").getAsString());
                if (size > config.getMaxReviewFileSize()) {
                    log.info("File '{}' not reviewed because its size exceeds the fixed maximum allowable size.",
                            filename);
                }
                else {
                    files.add(filename);
                }
            }
        }
        return files;
    }

    private void processFileDiff(String filename, String fileDiffJson) {
        log.debug("FileDiff Json processed: {}", fileDiffJson);
        InputFileDiff inputFileDiff = gson.fromJson(fileDiffJson, InputFileDiff.class);
        // Initialize the reduced output file diff with fields `meta_a` and `meta_b`
        OutputFileDiff outputFileDiff = new OutputFileDiff(inputFileDiff.getMeta_a(), inputFileDiff.getMeta_b());
        FileDiffProcessed fileDiffProcessed = new FileDiffProcessed(config, isCommitMessage, inputFileDiff);
        fileDiffsProcessed.put(filename, fileDiffProcessed);
        outputFileDiff.setContent(fileDiffProcessed.getOutputDiffContent());
        diffs.add(gson.toJson(outputFileDiff));
    }

    private String getFileDiffsJson(String fullChangeId, List<String> files, int revisionBase) throws Exception {
        List<String> enabledFileExtensions = config.getEnabledFileExtensions();
        for (String filename : files) {
            isCommitMessage = filename.equals("/COMMIT_MSG");
            if (!isCommitMessage && (filename.lastIndexOf(".") < 1 ||
                    !enabledFileExtensions.contains(filename.substring(filename.lastIndexOf("."))))) {
                continue;
            }
            URI uri = URI.create(config.getGerritAuthBaseUrl()
                    + UriResourceLocator.gerritPatchSetFilesUri(fullChangeId)
                    + UriResourceLocator.gerritDiffPostfixUri(filename)
                    + UriResourceLocator.gerritRevisionBasePostfixUri(revisionBase));
            log.debug("getFileDiffsJson URI: '{}'", uri);
            String fileDiffJson = forwardGetRequest(uri).replaceAll("^[')\\]}]+", "");
            processFileDiff(filename, fileDiffJson);
        }
        diffs.add(String.format("{\"changeId\": \"%s\"}", fullChangeId));
        return "[" + String.join(",", diffs) + "]\n";
    }

}
