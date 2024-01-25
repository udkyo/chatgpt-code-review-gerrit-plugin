package com.googlesource.gerrit.plugins.chatgpt.utils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.googlesource.gerrit.plugins.chatgpt.utils.StringUtils.parseOutOfDelimiters;

public class ReviewUtils {
    private static final Pattern SANITIZE_REGEX = Pattern.compile("(\\*{1,2}|(?<!\\w)_{1,2})(.+?)\\1",
            Pattern.DOTALL);

    private static String sanitizeGerritComment(String message) {
        // Sanitize sequences of asterisks ("*") and underscores ("_") that would be incorrectly interpreted as
        // delimiters of Italic and Bold text
        Matcher sanitizeMatcher = SANITIZE_REGEX.matcher(message);
        return sanitizeMatcher.replaceAll("\\\\$1$2\\\\$1");
    }

    private static String sanitizeOutsideInlineCodeBlocks(String message) {
        // Sanitize the content outside the inline code blocks (delimited by a single "`").
        return parseOutOfDelimiters(message, "`", ReviewUtils::sanitizeGerritComment);
    }

    public static String processChatGptMessage(String message) {
        // Sanitize code blocks (delimited by "```") by stripping out the language for syntax highlighting and ensuring
        // that is preceded by two "\n" chars. Additionally, sanitize the content outside these blocks.
        return parseOutOfDelimiters(message, "\\s*```\\w*\\s*", ReviewUtils::sanitizeOutsideInlineCodeBlocks,
                "\n\n```\n", "\n```\n");
    }

    public static long getTimeStamp(String updatedString) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSSSS");
        LocalDateTime updatedDateTime = LocalDateTime.parse(updatedString, formatter);
        return updatedDateTime.toInstant(ZoneOffset.UTC).getEpochSecond();
    }

}
