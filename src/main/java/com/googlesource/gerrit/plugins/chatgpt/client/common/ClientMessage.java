package com.googlesource.gerrit.plugins.chatgpt.client.common;

import com.googlesource.gerrit.plugins.chatgpt.client.ClientCommands;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class ClientMessage extends ClientBase {
    private static final Pattern MESSAGE_HEADING_PATTERN = Pattern.compile(
            "^(?:Patch Set \\d+:[^\\n]*\\s+(?:\\(\\d+ comments?\\)\\s*)?)+");

    private final Pattern botMentionPattern;

    @Getter
    private String message;

    public ClientMessage(Configuration config) {
        super(config);
        botMentionPattern = getBotMentionPattern();
    }

    public ClientMessage(Configuration config, String message) {
        this(config);
        this.message = message;
    }

    public boolean isBotAddressed(String message) {
        log.debug("Processing comment: {}", message);
        Matcher userMatcher = botMentionPattern.matcher(message);
        if (!userMatcher.find()) {
            log.debug("Skipping action since the comment does not mention the ChatGPT bot." +
                            " Expected bot name in comment: {}, Actual comment text: {}",
                    config.getGerritUserName(), message);
            return false;
        }
        return true;
    }

    public ClientMessage removeHeadings() {
        message = MESSAGE_HEADING_PATTERN.matcher(message).replaceAll("");
        return this;
    }

    public ClientMessage removeMentions() {
        message = botMentionPattern.matcher(message).replaceAll("").trim();
        return this;
    }

    public ClientMessage removeCommands() {
        message = ClientCommands.removeCommands(message);
        return this;
    }

    private Pattern getBotMentionPattern() {
        String escapedUserName = Pattern.quote(config.getGerritUserName());
        String emailRegex = "@" + escapedUserName + "(?:@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})?\\b";
        return Pattern.compile(emailRegex);
    }

}
