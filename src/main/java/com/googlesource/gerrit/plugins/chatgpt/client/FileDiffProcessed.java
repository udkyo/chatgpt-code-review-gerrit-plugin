package com.googlesource.gerrit.plugins.chatgpt.client;

import com.googlesource.gerrit.plugins.chatgpt.client.model.InputFileDiff;
import com.googlesource.gerrit.plugins.chatgpt.client.model.InputFileDiff.Content;
import com.googlesource.gerrit.plugins.chatgpt.client.model.OutputFileDiff;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class FileDiffProcessed {
    private static final String[] COMMIT_MESSAGE_FILTER_OUT_PREFIXES = {
            "Parent:",
            "Author:",
            "AuthorDate:",
            "Commit:",
            "CommitDate:",
            "Change-Id:"
    };

    private final Configuration config;
    @Getter
    private List<Content> diff;
    @Getter
    private List<String> newContent;
    @Getter
    private List<OutputFileDiff.Content> outputDiffContent;
    private final boolean isCommitMessage;

    public FileDiffProcessed(Configuration config, boolean isCommitMessage, InputFileDiff inputFileDiff) {
        this.config = config;
        this.isCommitMessage = isCommitMessage;
        newContent = new ArrayList<>() {{
            add("DUMMY LINE #0");
        }};
        diff = inputFileDiff.getContent();
        outputDiffContent = new ArrayList<>();
        List<Content> inputDiffContent = inputFileDiff.getContent();
        // Iterate over the items of the diff content
        for (Content inputContentItem : inputDiffContent) {
            OutputFileDiff.Content outputContentItem = new OutputFileDiff.Content();
            // Iterate over the fields `a`, `b` and `ab` of each diff content
            for (Field inputDiffField : Content.class.getDeclaredFields()) {
                processFileDiffItem(inputDiffField, inputContentItem, outputContentItem);
            }
            outputDiffContent.add(outputContentItem);
        }
    }

    private void filterCommitMessageContent(List<String> fieldValue) {
        fieldValue.removeIf(s ->
                s.isEmpty() || Arrays.stream(COMMIT_MESSAGE_FILTER_OUT_PREFIXES).anyMatch(s::startsWith));
    }

    private void processFileDiffItem(Field inputDiffField, Content contentItem,
                                     OutputFileDiff.Content outputContentItem) {
        String diffType = inputDiffField.getName();
        try {
            // Get the `a`, `b` or `ab` field's value from the input diff content
            @SuppressWarnings("unchecked")
            List<String> diffLines = (List<String>) inputDiffField.get(contentItem);
            if (diffLines == null) {
                return;
            }
            if (isCommitMessage) {
                filterCommitMessageContent(diffLines);
            }
            if (config.getGptFullFileReview() || !diffType.equals("ab")) {
                // Get the corresponding `a`, `b` or `ab` field from the output diff class
                Field outputDiffField = OutputFileDiff.Content.class.getDeclaredField(diffType);
                // Store the new field's value in the output diff content `outputContentItem`
                outputDiffField.set(outputContentItem, String.join("\n", diffLines));
            }
            // If the lines modified in the PatchSet are not deleted, they are utilized to populate newContent
            if (diffType.contains("b")) {
                newContent.addAll(diffLines);
            }
        } catch (IllegalAccessException | NoSuchFieldException e) {
            log.error("Error while processing file difference (diff type: {})", diffType, e);
        }
    }

}
