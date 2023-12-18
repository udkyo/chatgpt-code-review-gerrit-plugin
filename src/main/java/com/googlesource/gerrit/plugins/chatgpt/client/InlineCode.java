package com.googlesource.gerrit.plugins.chatgpt.client;

import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.googlesource.gerrit.plugins.chatgpt.client.model.ChatGptSuggestionPoint;
import com.googlesource.gerrit.plugins.chatgpt.client.model.GerritCommentRange;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class InlineCode {
    private final Gson gson = new Gson();
    private final CodeFinder codeFinder;
    private final List<String> newContent;
    private GerritCommentRange range;

    public InlineCode(FileDiffProcessed fileDiffProcessed) {
        codeFinder = new CodeFinder(fileDiffProcessed.getDiff());
        newContent = fileDiffProcessed.getNewContent();
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

    public Optional<GerritCommentRange> findCommentRange(ChatGptSuggestionPoint suggestion) {
        int commentedLine;
        try {
            commentedLine = suggestion.getLineNumber();
        }
        catch (NumberFormatException ex){
            // If the line number is not passed, a line in the middle of the code is used as best guess
            commentedLine = newContent.size() / 2;
        }

        return Optional.ofNullable(codeFinder.findCode(suggestion, commentedLine));
    }

}
