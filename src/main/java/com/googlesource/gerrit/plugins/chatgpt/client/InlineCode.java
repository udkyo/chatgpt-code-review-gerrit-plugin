package com.googlesource.gerrit.plugins.chatgpt.client;

import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.googlesource.gerrit.plugins.chatgpt.client.model.ChatGptSuggestion;
import com.googlesource.gerrit.plugins.chatgpt.client.model.GerritCommentRange;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class InlineCode {
    private final Gson gson = new Gson();
    private final List<String> newContent;
    private GerritCommentRange range;

    public InlineCode(List<String> newContent) {
        this.newContent = newContent;
    }

    private String getLineSlice(int line_num) {
        String line = newContent.get(line_num);
        if (line_num == range.end_line) {
            line = line.substring(0, range.end_character);
        }
        if (line_num == range.start_line) {
            line = line.substring(range.start_character);
        }
        return line;
    }

    public String getInlineCode(JsonObject commentProperty) {
        if (commentProperty.has("range")) {
            List<String> codeByRange = new ArrayList<>();
            range = gson.fromJson(commentProperty.get("range"), GerritCommentRange.class);
            for (int line_num = range.start_line; line_num <= range.end_line; line_num++) {
                codeByRange.add(getLineSlice(line_num));
            }
            return String.join("\n", codeByRange);
        }
        else {
            return newContent.get(commentProperty.get("line").getAsInt());
        }
    }

    public double calcCodeDistance(GerritCommentRange range, int fromLine) {
        return Math.abs((range.end_line - range.start_line) / 2 - fromLine);
    }

    public Optional<GerritCommentRange> findCommentRange(ChatGptSuggestion suggestion) {
        int commentedLine;
        try {
            commentedLine = suggestion.getLineNumber();
        }
        catch (NumberFormatException ex){
            // If the line number is not passed, a line in the middle of the code is used as best guess
            commentedLine = newContent.size() / 2;
        }
        // Split the commented code into lines and remove the trailing spaces from each line
        String[] commentedCode = suggestion.getCodeSnippet().trim().split("\\s*\n\\s*");
        int codeLinePointer = 0;
        int lastCommentedCodeLineNum = commentedCode.length -1;
        GerritCommentRange currentCommentRange = null;
        GerritCommentRange closestCommentRange = null;

        for (int lineNum = 1; lineNum < newContent.size(); lineNum++) {
            String commentedCodeLine = commentedCode[codeLinePointer];
            String newContentLine = newContent.get(lineNum);
            // Search for the commented code in the new content
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
        }
        return Optional.ofNullable(closestCommentRange);
    }

}
