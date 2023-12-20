package com.googlesource.gerrit.plugins.chatgpt.client;

import com.googlesource.gerrit.plugins.chatgpt.client.model.CodeFinderDiff;
import com.googlesource.gerrit.plugins.chatgpt.client.model.DiffContent;
import com.googlesource.gerrit.plugins.chatgpt.client.model.InputFileDiff;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

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
    private final boolean isCommitMessage;
    @Getter
    private List<CodeFinderDiff> codeFinderDiffs;
    @Getter
    private List<String> newContent;
    @Getter
    private List<DiffContent> outputDiffContent;
    private int lineNum;
    private DiffContent diffContentItem;
    private DiffContent outputDiffContentItem;
    private TreeMap<Integer, Integer> charToLineMapItem;

    public FileDiffProcessed(Configuration config, boolean isCommitMessage, InputFileDiff inputFileDiff) {
        this.config = config;
        this.isCommitMessage = isCommitMessage;
        newContent = new ArrayList<>() {{
            add("DUMMY LINE #0");
        }};
        lineNum = 1;
        outputDiffContent = new ArrayList<>();
        codeFinderDiffs = new ArrayList<>();
        List<InputFileDiff.Content> inputDiffContent = inputFileDiff.getContent();
        // Iterate over the items of the diff content
        for (InputFileDiff.Content inputContentItem : inputDiffContent) {
            diffContentItem = new DiffContent();
            outputDiffContentItem = new DiffContent();
            charToLineMapItem = new TreeMap<>();
            // Iterate over the fields `a`, `b` and `ab` of each diff content
            for (Field inputDiffField : InputFileDiff.Content.class.getDeclaredFields()) {
                processFileDiffItem(inputDiffField, inputContentItem);
            }
            outputDiffContent.add(outputDiffContentItem);
            codeFinderDiffs.add(new CodeFinderDiff(diffContentItem, charToLineMapItem));
        }
    }

    private void filterCommitMessageContent(List<String> fieldValue) {
        fieldValue.removeIf(s ->
                s.isEmpty() || Arrays.stream(COMMIT_MESSAGE_FILTER_OUT_PREFIXES).anyMatch(s::startsWith));
    }

    private void updateCodeEntities(Field diffField, List<String> diffLines) throws IllegalAccessException {
        String diffType = diffField.getName();
        String content = String.join("\n", diffLines);
        diffField.set(diffContentItem, content);
        // If the lines modified in the PatchSet are not deleted, they are utilized to populate newContent and
        // charToLineMapItem
        if (diffType.contains("b")) {
            int diffCharPointer = -1;
            for (String diffLine : diffLines) {
                // Increase of 1 to take into account of the newline character
                diffCharPointer++;
                charToLineMapItem.put(diffCharPointer, lineNum);
                diffCharPointer += diffLine.length();
                lineNum++;
            }
            // Add the last line to charToLineMapItem
            charToLineMapItem.put(diffCharPointer +1, lineNum);
            newContent.addAll(diffLines);
        }
        // If the lines modified in the PatchSet are deleted, they are mapped in charToLineMapItem to current lineNum
        else {
            charToLineMapItem.put(content.length(), lineNum);
        }

        if (config.getGptFullFileReview() || !diffType.equals("ab")) {
            // Store the new field's value in the output diff content `outputContentItem`
            diffField.set(outputDiffContentItem, content);
        }
    }

    private void processFileDiffItem(Field inputDiffField, InputFileDiff.Content contentItem) {
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
            // Get the corresponding `a`, `b` or `ab` field from the output diff class
            Field diffField = DiffContent.class.getDeclaredField(inputDiffField.getName());
            updateCodeEntities(diffField, diffLines);

        } catch (IllegalAccessException | NoSuchFieldException e) {
            log.error("Error while processing file difference (diff type: {})", inputDiffField.getName(), e);
        }
    }

}
