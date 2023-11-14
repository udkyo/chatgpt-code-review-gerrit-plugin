package com.googlesource.gerrit.plugins.chatgpt.client;

import com.google.gson.Gson;
import com.google.inject.Singleton;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.*;
import java.lang.reflect.Type;

@Slf4j
@Singleton
public class GerritClient extends GerritClientComments {
    private final Gson gson = new Gson();

    private List<String> getAffectedFiles(String fullChangeId) throws Exception {
        URI uri = URI.create(config.getGerritAuthBaseUrl()
                        + UriResourceLocator.gerritPatchSetFilesUri(fullChangeId));
        String responseBody = forwardGetRequest(uri);
        Type listType = new TypeToken<Map<String, Map<String, String>>>() {}.getType();
        Map<String, Map<String, String>> map = gson.fromJson(responseBody, listType);
        List<String> files = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> file : map.entrySet()) {
            String filename = file.getKey();
            if (!filename.equals("/COMMIT_MSG") || config.getGptReviewCommitMessages()) {
                int size = Integer.parseInt(file.getValue().get("size"));
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

    private List<String> getFileNewContent(String fileDiffJson) {
        List<String> newContent = new ArrayList<>() {{
            add("DUMMY LINE #0");
        }};
        Type diffType = new TypeToken<Map<String, ?>>() {}.getType();
        Map<String, ?> diff = gson.fromJson(fileDiffJson, diffType);
        @SuppressWarnings("unchecked")
        List<Map<String, List<String>>> diffContent = (List<Map<String, List<String>>>) diff.get("content");
        for (Map<String, List<String>> diffChunk : diffContent) {
            for (Map.Entry<String, List<String>> diffItem : diffChunk.entrySet()) {
                if (diffItem.getKey().contains("b")) {
                    newContent.addAll(diffItem.getValue());
                }
            }
        }
        return newContent;
    }

    private String getFileDiffsJson(String fullChangeId, List<String> files) throws Exception {
        List<String> diffs = new ArrayList<>();
        List<String> enabledFileExtensions = config.getEnabledFileExtensions();
        int totalReviewedFiles = 0;
        for (String filename : files) {
            boolean isCommitMessage = filename.equals("/COMMIT_MSG");
            if (!isCommitMessage && (filename.lastIndexOf(".") < 1 ||
                    !enabledFileExtensions.contains(filename.substring(filename.lastIndexOf("."))))) {
                continue;
            }
            URI uri = URI.create(config.getGerritAuthBaseUrl()
                    + UriResourceLocator.gerritPatchSetFilesUri(fullChangeId)
                    + UriResourceLocator.gerritDiffPostfixUri(filename));
            String fileDiffJson = forwardGetRequest(uri).replaceAll("^[')\\]}]+", "");
            log.debug("fileDiffJson: {}", fileDiffJson);
            filesNewContent.put(filename, getFileNewContent(fileDiffJson));
            diffs.add(fileDiffJson);
            if (!isCommitMessage) {
                totalReviewedFiles++;
            }
        }
        if (totalReviewedFiles == 0) {
            return "";
        }
        return "[" + String.join(",", diffs) + "]\n";
    }

    public String getTaggedPrompt() {
        StringBuilder taggedPrompt = new StringBuilder();
        for (int i = 0; i < commentProperties.size(); i++) {
            taggedPrompt.append(String.format("[ID:%d] %s\n", i, getCommentPrompt(i)));
        }
        return taggedPrompt.toString();
    }

    public String getPatchSet(String fullChangeId) throws Exception {
        List<String> files = getAffectedFiles(fullChangeId);
        log.debug("Patch files: {}", files);

        String fileDiffsJson = getFileDiffsJson(fullChangeId, files);
        log.debug("File diffs: {}", fileDiffsJson);

        return fileDiffsJson;
    }

}
