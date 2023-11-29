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
import java.lang.reflect.Field;

@Slf4j
public class GerritClientPatchSet extends GerritClientAccount {
    private static final String[] COMMIT_MESSAGE_FILTER_OUT_PREFIXES = {
        "Parent:",
        "Author:",
        "AuthorDate:",
        "Commit:",
        "CommitDate:",
        "Change-Id:"
    };

    private final Gson gson = new GsonBuilder()
            .disableHtmlEscaping()
            .create();

    private boolean isCommitMessage;
    private final List<String> diffs;
    private List<String> newFileContent;
    private OutputFileDiff.Content outputContentItem;

    public GerritClientPatchSet(Configuration config) {
        super(config);
        diffs = new ArrayList<>();
    }

    private List<String> getAffectedFiles(String fullChangeId) throws Exception {
        URI uri = URI.create(config.getGerritAuthBaseUrl()
                        + UriResourceLocator.gerritPatchSetFilesUri(fullChangeId));
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

    private List<String> filterCommitMessageContent(List<String> fieldValue) {
        fieldValue.removeIf(s ->
                s.isEmpty() || Arrays.stream(COMMIT_MESSAGE_FILTER_OUT_PREFIXES).anyMatch(s::startsWith));
        return fieldValue;
    }

    private void processFileDiffItem(InputFileDiff.Content contentItem, Field inputDiffField) {
        String fieldName = inputDiffField.getName();
        try {
            // Get the `a`, `b` or `ab` field's value from the input diff content
            @SuppressWarnings("unchecked")
            List<String> fieldValue = (List<String>) inputDiffField.get(contentItem);
            if (fieldValue == null) {
                return;
            }
            if (isCommitMessage) {
                fieldValue = filterCommitMessageContent(fieldValue);
            }
            if (config.getGptFullFileReview() || !fieldName.equals("ab")) {
                // Get the corresponding `a`, `b` or `ab` field from the output diff class
                Field outputDiffField = OutputFileDiff.Content.class.getDeclaredField(fieldName);
                // Store the new field's value in the output diff content `outputContentItem`
                outputDiffField.set(outputContentItem, String.join("\n", fieldValue));
            }
            // If the lines modified in the PatchSet are not deleted, they are utilized to populate newFileContent
            if (fieldName.contains("b")) {
                newFileContent.addAll(fieldValue);
            }
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    private void processFileDiff(String filename, String fileDiffJson) {
        log.debug("FileDiff Json processed: {}", fileDiffJson);
        newFileContent = new ArrayList<>() {{
            add("DUMMY LINE #0");
        }};
        InputFileDiff inputFileDiff = gson.fromJson(fileDiffJson, InputFileDiff.class);
        // Initialize the reduced output file diff with fields `meta_a` and `meta_b`
        OutputFileDiff outputFileDiff = new OutputFileDiff(inputFileDiff.getMeta_a(), inputFileDiff.getMeta_b());
        List<OutputFileDiff.Content> outputDiffContent = new ArrayList<>();
        List<InputFileDiff.Content> inputDiffContent = inputFileDiff.getContent();
        // Iterate over the items of the diff content
        for (InputFileDiff.Content contentItem : inputDiffContent) {
            outputContentItem = new OutputFileDiff.Content();
            // Iterate over the fields `a`, `b` and `ab` of each diff content
            for (Field inputDiffField : InputFileDiff.Content.class.getDeclaredFields()) {
                processFileDiffItem(contentItem, inputDiffField);
            }
            outputDiffContent.add(outputContentItem);
        }
        filesNewContent.put(filename, newFileContent);
        outputFileDiff.setContent(outputDiffContent);
        diffs.add(gson.toJson(outputFileDiff));
    }

    private String getFileDiffsJson(String fullChangeId, List<String> files) throws Exception {

        List<String> enabledFileExtensions = config.getEnabledFileExtensions();
        for (String filename : files) {
            isCommitMessage = filename.equals("/COMMIT_MSG");
            if (!isCommitMessage && (filename.lastIndexOf(".") < 1 ||
                    !enabledFileExtensions.contains(filename.substring(filename.lastIndexOf("."))))) {
                continue;
            }
            URI uri = URI.create(config.getGerritAuthBaseUrl()
                    + UriResourceLocator.gerritPatchSetFilesUri(fullChangeId)
                    + UriResourceLocator.gerritDiffPostfixUri(filename));
            String fileDiffJson = forwardGetRequest(uri).replaceAll("^[')\\]}]+", "");
            processFileDiff(filename, fileDiffJson);
        }
        return "[" + String.join(",", diffs) + "]\n";
    }

    public String getPatchSet(String fullChangeId) throws Exception {
        List<String> files = getAffectedFiles(fullChangeId);
        log.debug("Patch files: {}", files);

        String fileDiffsJson = getFileDiffsJson(fullChangeId, files);
        log.debug("File diffs: {}", fileDiffsJson);

        return fileDiffsJson;
    }

}
