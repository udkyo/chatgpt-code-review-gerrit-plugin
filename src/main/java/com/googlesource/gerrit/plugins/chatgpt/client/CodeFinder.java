package com.googlesource.gerrit.plugins.chatgpt.client;

import com.googlesource.gerrit.plugins.chatgpt.client.model.ChatGptSuggestionPoint;
import com.googlesource.gerrit.plugins.chatgpt.client.model.GerritCommentRange;
import com.googlesource.gerrit.plugins.chatgpt.client.model.InputFileDiff;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.List;

@Slf4j
public class CodeFinder {
    private final List<InputFileDiff.Content> diff;
    private int commentedLine;
    private String[] commentedCode;
    private int lastCommentedCodeLineNum ;
    private GerritCommentRange currentCommentRange;
    private GerritCommentRange closestCommentRange;
    private int lineNum;

    public CodeFinder(List<InputFileDiff.Content> diff) {
        this.diff = diff;
    }

    private double calcCodeDistance(GerritCommentRange range, int fromLine) {
        return Math.abs((range.end_line - range.start_line) / 2 - fromLine);
    }

    @SuppressWarnings("unchecked")
    private List<String> getDiffItem(Field diffField, InputFileDiff.Content diffItem) {
        try {
            return (List<String>) diffField.get(diffItem);
        }
        catch (IllegalAccessException e) {
            log.error("Error while processing file difference (diff type: {})", diffField.getName(), e);
            return null;
        }
    }

    private void findCodeLines(String diffType, List<String> diffLines) {
        int codeLinePointer = 0;
        for (String newContentLine : diffLines) {
            String commentedCodeLine = commentedCode[codeLinePointer];
            // Search for the commented code in the content
            int codeCharacter = newContentLine.indexOf(commentedCodeLine);
            if (codeCharacter != -1) {
                // If the beginning of a commented code is found, currentCommentRange is initialized
                if (codeLinePointer == 0) {
                    currentCommentRange = GerritCommentRange.builder()
                            .start_line(lineNum)
                            .start_character(codeCharacter)
                            .build();
                }
                // If the ending of a commented code is found, the currentCommentRange ending values are set
                if (codeLinePointer >= lastCommentedCodeLineNum) {
                    currentCommentRange.setEnd_line(lineNum);
                    currentCommentRange.setEnd_character(codeCharacter + commentedCodeLine.length());
                    // If multiple commented code portions are found and currentCommentRange is closer to the line
                    // number suggested by ChatGPT than closestCommentRange, it becomes the new closestCommentRange
                    if (closestCommentRange == null || calcCodeDistance(currentCommentRange, commentedLine) <
                            calcCodeDistance(closestCommentRange, commentedLine)) {
                        closestCommentRange = currentCommentRange.toBuilder().build();
                    }
                }
                else {
                    codeLinePointer++;
                }
            }
            else {
                codeLinePointer = 0;
            }
            if (diffType.contains("b")) {
                lineNum++;
            }
        }
    }

    public GerritCommentRange findCode(ChatGptSuggestionPoint suggestion, int commentedLine) {
        this.commentedLine = commentedLine;
        // Split the commented code into lines and remove the trailing spaces from each line
        commentedCode = suggestion.getCodeSnippet().trim().split("\\s*\n\\s*");
        lastCommentedCodeLineNum = commentedCode.length -1;
        currentCommentRange = null;
        closestCommentRange = null;
        lineNum = 1;
        for (InputFileDiff.Content diffItem : diff) {
            for (Field diffField : InputFileDiff.Content.class.getDeclaredFields()) {
                String diffType = diffField.getName();
                List<String> diffLines = getDiffItem(diffField, diffItem);
                if (diffLines != null) {
                    findCodeLines(diffType, diffLines);
                }
            }
        }

        return closestCommentRange;
    }

}
