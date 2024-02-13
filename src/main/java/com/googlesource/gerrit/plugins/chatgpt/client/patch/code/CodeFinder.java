package com.googlesource.gerrit.plugins.chatgpt.client.patch.code;

import com.googlesource.gerrit.plugins.chatgpt.client.model.chatGpt.ChatGptReplyItem;
import com.googlesource.gerrit.plugins.chatgpt.client.model.CodeFinderDiff;
import com.googlesource.gerrit.plugins.chatgpt.client.model.DiffContent;
import com.googlesource.gerrit.plugins.chatgpt.client.model.gerrit.GerritCodeRange;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class CodeFinder {
    private static final String PUNCTUATION_REGEX = "([()\\[\\]{}<>:;,?&+\\-*/%|=])";
    private final String NON_PRINTING_REPLACEMENT;
    private final String PUNCTUATION_REPLACEMENT;
    private final String PLACEHOLDER_REGEX;
    private final List<CodeFinderDiff> codeFinderDiffs;

    private int commentedLine;
    private Pattern commentedCodePattern;
    private GerritCodeRange currentCodeRange;
    private GerritCodeRange closestCodeRange;

    public CodeFinder(List<CodeFinderDiff> codeFinderDiffs, String randomPlaceholder) {
        this.codeFinderDiffs = codeFinderDiffs;
        NON_PRINTING_REPLACEMENT = "\\\\E" + randomPlaceholder +"\\\\Q";
        PUNCTUATION_REPLACEMENT = "\\\\E" + randomPlaceholder +"\\\\$1" + randomPlaceholder +"\\\\Q";
        PLACEHOLDER_REGEX = "(?:" + randomPlaceholder + ")+";
    }

    public GerritCodeRange findCommentedCode(ChatGptReplyItem replyItem, int commentedLine) {
        this.commentedLine = commentedLine;
        updateCodePattern(replyItem);
        currentCodeRange = null;
        closestCodeRange = null;
        for (CodeFinderDiff codeFinderDiff : codeFinderDiffs) {
            for (Field diffField : DiffContent.class.getDeclaredFields()) {
                String diffCode = getDiffItem(diffField, codeFinderDiff.getContent());
                if (diffCode != null) {
                    TreeMap<Integer, Integer> charToLineMapItem = codeFinderDiff.getCharToLineMap();
                    try {
                        findCodeLines(diffCode, charToLineMapItem);
                    }
                    catch (IllegalArgumentException e) {
                        log.warn("Could not retrieve line number from charToLineMap.\nDiff Code = {}", diffCode, e);
                    }
                }
            }
        }

        return closestCodeRange;
    }

    private void updateCodePattern(ChatGptReplyItem replyItem) {
        String commentedCode = replyItem.getCodeSnippet().trim();
        String commentedCodeRegex = Pattern.quote(commentedCode);
        // Generalize the regex to capture snippets where existing sequences of non-printing chars have been modified
        // from the original code
        commentedCodeRegex = commentedCodeRegex.replaceAll("\\s+", NON_PRINTING_REPLACEMENT);
        // Generalize the regex to capture snippets where non-printing chars have been removed from around the
        // punctuation marks of the original code
        commentedCodeRegex = commentedCodeRegex.replaceAll(PUNCTUATION_REGEX, PUNCTUATION_REPLACEMENT);
        // Remove redundant empty literal escape sequences that could have resulted from previous substitutions
        commentedCodeRegex = commentedCodeRegex.replaceAll("\\\\Q\\\\E", "");
        // Obtain a functional regex to match code snippets without relying on non-printing chars
        commentedCodeRegex = commentedCodeRegex.replaceAll(PLACEHOLDER_REGEX, "\\\\s*");
        // Remove any detected trailing matching sequence of non-printing chars
        commentedCodeRegex = commentedCodeRegex.replaceAll("\\\\s\\*$", "");
        commentedCodePattern = Pattern.compile(commentedCodeRegex);
    }

    private double calcCodeDistance(GerritCodeRange range, int fromLine) {
        return Math.abs((range.endLine - range.startLine) / 2 - fromLine);
    }

    private String getDiffItem(Field diffField, DiffContent diffItem) {
        try {
            return (String) diffField.get(diffItem);
        }
        catch (IllegalAccessException e) {
            log.error("Error while processing file difference (diff type: {})", diffField.getName(), e);
            return null;
        }
    }

    private int getLineNumber(TreeMap<Integer, Integer> charToLineMapItem, int position) {
        Integer floorPosition = charToLineMapItem.floorKey(position);
        if (floorPosition == null) {
            throw new IllegalArgumentException("Position: " + position);
        }
        return charToLineMapItem.get(floorPosition);
    }

    private int getLineCharacter(String diffCode, int position) {
        // Return the offset relative to the nearest preceding newline character if found, `position` otherwise
        return position - diffCode.substring(0, position).lastIndexOf("\n") -1;
    }

    private void findCodeLines(String diffCode, TreeMap<Integer, Integer> charToLineMapItem)
            throws IllegalArgumentException {
        Matcher codeMatcher = commentedCodePattern.matcher(diffCode);
        while (codeMatcher.find()) {
            int startPosition = codeMatcher.start();
            int endPosition = codeMatcher.end();
            currentCodeRange = GerritCodeRange.builder()
                    .startLine(getLineNumber(charToLineMapItem, startPosition))
                    .endLine(getLineNumber(charToLineMapItem, endPosition))
                    .startCharacter(getLineCharacter(diffCode, startPosition))
                    .endCharacter(getLineCharacter(diffCode, endPosition))
                    .build();
            // If multiple commented code portions are found and currentCommentRange is closer to the line
            // number suggested by ChatGPT than closestCommentRange, it becomes the new closestCommentRange
            if (closestCodeRange == null || calcCodeDistance(currentCodeRange, commentedLine) <
                    calcCodeDistance(closestCodeRange, commentedLine)) {
                closestCodeRange = currentCodeRange.toBuilder().build();
            }
        }
    }

}
