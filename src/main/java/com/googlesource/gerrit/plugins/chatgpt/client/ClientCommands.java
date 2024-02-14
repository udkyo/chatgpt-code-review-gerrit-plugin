package com.googlesource.gerrit.plugins.chatgpt.client;

import com.googlesource.gerrit.plugins.chatgpt.client.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.chatgpt.model.settings.Settings;
import com.googlesource.gerrit.plugins.chatgpt.settings.DynamicSettings;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Getter
public class ClientCommands {
    private enum COMMAND_SET {
        REVIEW,
        REVIEW_LAST
    }

    private static final Map<String, COMMAND_SET> COMMAND_MAP = Map.of(
            "review", COMMAND_SET.REVIEW,
            "review_last", COMMAND_SET.REVIEW_LAST
    );
    private static final List<COMMAND_SET> REVIEW_COMMANDS = new ArrayList<>(List.of(
            COMMAND_SET.REVIEW,
            COMMAND_SET.REVIEW_LAST
    ));
    private static final Pattern COMMAND_PATTERN = Pattern.compile("/(" + String.join("|",
            COMMAND_MAP.keySet()) + ")\\b");

    public static boolean parseCommands(GerritChange change, String comment) {
        Settings settings = DynamicSettings.getInstance(change);
        Matcher reviewCommandMatcher = COMMAND_PATTERN.matcher(comment);
        if (reviewCommandMatcher.find()) {
            COMMAND_SET command = COMMAND_MAP.get(reviewCommandMatcher.group(1));
            if (REVIEW_COMMANDS.contains(command)) {
                log.debug("Forced review command detected in message {}", comment);
                settings.setForcedReview(true);
            }
            if (command == COMMAND_SET.REVIEW) {
                log.debug("Forced review command applied to the whole Change Set");
                settings.setForcedReviewChangeSet(true);
            }
            return true;
        }
        return false;
    }

}
