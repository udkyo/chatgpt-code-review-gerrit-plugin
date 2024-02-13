package com.googlesource.gerrit.plugins.chatgpt.client.gerrit;

import com.googlesource.gerrit.plugins.chatgpt.client.ClientBase;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class GerritMessage extends ClientBase {
    public static final String GLOBAL_MESSAGES_FILENAME = "/PATCHSET_LEVEL";

    private static final Pattern MESSAGE_HEADING_PATTERN = Pattern.compile(
            "^(?:Patch Set \\d+:[^\\n]*\\s+(?:\\(\\d+ comments?\\)\\s+)?)+");

    private final Pattern botMentionPattern;

    public GerritMessage(Configuration config) {
        super(config);
        botMentionPattern = getBotMentionPattern();
    }

    public boolean isBotAddressed(String comment) {
        log.debug("Processing comment: {}", comment);
        Matcher userMatcher = botMentionPattern.matcher(comment);
        if (!userMatcher.find()) {
            log.debug("Skipping action since the comment does not mention the ChatGPT bot." +
                            " Expected bot name in comment: {}, Actual comment text: {}",
                    config.getGerritUserName(), comment);
            return false;
        }
        return true;
    }

    protected String removeHeadings(String message) {
        return MESSAGE_HEADING_PATTERN.matcher(message).replaceAll("");
    }

    protected String removeMentions(String message) {
        return botMentionPattern.matcher(message).replaceAll("").trim();
    }

    private Pattern getBotMentionPattern() {
        String escapedUserName = Pattern.quote(config.getGerritUserName());
        String emailRegex = "@" + escapedUserName + "(?:@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})?\\b";
        return Pattern.compile(emailRegex);
    }

}
