package com.googlesource.gerrit.plugins.chatgpt.client.gerrit;

import com.googlesource.gerrit.plugins.chatgpt.client.ClientBase;
import com.googlesource.gerrit.plugins.chatgpt.client.model.gerrit.GerritComment;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class GerritMessage extends ClientBase {

    public GerritMessage(Configuration config) {
        super(config);
    }

    public boolean isBotAddressed(String comment) {
        log.info("Processing comment: {}", comment);

        Matcher userMatcher = getBotMentionPattern().matcher(comment);
        if (!userMatcher.find()) {
            log.debug("Skipping action since the comment does not mention the ChatGPT bot." +
                            " Expected bot name in comment: {}, Actual comment text: {}",
                    config.getGerritUserName(), comment);
            return false;
        }
        return true;
    }

    protected String getMessageWithoutMentions(GerritComment commentProperty) {
        String commentMessage = commentProperty.getMessage();
        return commentMessage.replaceAll(getBotMentionPattern().pattern(), "").trim();
    }

    private Pattern getBotMentionPattern() {
        String escapedUserName = Pattern.quote(config.getGerritUserName());
        String emailRegex = "@" + escapedUserName + "(?:@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})?\\b";
        return Pattern.compile(emailRegex);
    }

}
