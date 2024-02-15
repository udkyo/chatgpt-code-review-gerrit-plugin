package com.googlesource.gerrit.plugins.chatgpt.client.prompt;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.googlesource.gerrit.plugins.chatgpt.utils.StringUtils.parseOutOfDelimiters;

public class MessageSanitizer {
    private static final Pattern SANITIZE_REGEX = Pattern.compile("(\\*{1,2}|(?<!\\w)_{1,2})(.+?)\\1",
            Pattern.DOTALL);

    public static String sanitizeChatGptMessage(String message) {
        // Sanitize code blocks (delimited by "```") by stripping out the language for syntax highlighting and ensuring
        // that is preceded by two "\n" chars. Additionally, sanitize the content outside these blocks.
        return parseOutOfDelimiters(message, "\\s*```\\w*\\s*", MessageSanitizer::sanitizeOutsideInlineCodeBlocks,
                "\n\n```\n", "\n```\n");
    }

    private static String sanitizeOutsideInlineCodeBlocks(String message) {
        // Sanitize the content outside the inline code blocks (delimited by a single "`").
        return parseOutOfDelimiters(message, "`", MessageSanitizer::sanitizeGerritComment);
    }

    private static String sanitizeGerritComment(String message) {
        // Sanitize sequences of asterisks ("*") and underscores ("_") that would be incorrectly interpreted as
        // delimiters of Italic and Bold text
        Matcher sanitizeMatcher = SANITIZE_REGEX.matcher(message);
        return sanitizeMatcher.replaceAll("\\\\$1$2\\\\$1");
    }

}
