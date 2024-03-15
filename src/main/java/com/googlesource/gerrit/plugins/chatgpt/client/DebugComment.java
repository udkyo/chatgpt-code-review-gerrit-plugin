package com.googlesource.gerrit.plugins.chatgpt.client;

import com.googlesource.gerrit.plugins.chatgpt.model.chatgpt.ChatGptReplyItem;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.googlesource.gerrit.plugins.chatgpt.utils.StringUtils.joinWithNewLine;
import static com.googlesource.gerrit.plugins.chatgpt.utils.StringUtils.prettyStringifyObject;

public class DebugComment {
    private static final String OPENING_TITLE = "DEBUGGING DETAILS";
    private static final String COMMENT_OPENING = "\n\n```\n" + OPENING_TITLE + "\n";
    private static final String HIDDEN_REPLY = "hidden: %s";
    private static final String COMMENT_CLOSING = "```";
    private static final Pattern DEBUG_MESSAGE_PATTERN = Pattern.compile("\\s+```\\s*" + OPENING_TITLE + ".*```\\s*",
            Pattern.DOTALL);

    public static String getDebugMessage(ChatGptReplyItem replyItem, boolean isHidden) {
        return joinWithNewLine(new ArrayList<>() {{
            add(COMMENT_OPENING);
            add(String.format(HIDDEN_REPLY, isHidden));
            add(prettyStringifyObject(replyItem));
            add(COMMENT_CLOSING);
        }});
    }

    public static String removeDebugMessages(String message) {
        Matcher debugMessagematcher = DEBUG_MESSAGE_PATTERN.matcher(message);
        return debugMessagematcher.replaceAll("");
    }

}
