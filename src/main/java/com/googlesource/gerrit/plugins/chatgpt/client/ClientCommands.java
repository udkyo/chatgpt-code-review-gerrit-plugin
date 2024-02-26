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
    private enum OPTION_SET {
        FILTER
    }

    private static final Map<String, COMMAND_SET> COMMAND_MAP = Map.of(
            "review", COMMAND_SET.REVIEW,
            "review_last", COMMAND_SET.REVIEW_LAST
    );
    private static final Map<String, OPTION_SET> OPTION_MAP = Map.of(
            "filter", OPTION_SET.FILTER
    );
    private static final List<COMMAND_SET> REVIEW_COMMANDS = new ArrayList<>(List.of(
            COMMAND_SET.REVIEW,
            COMMAND_SET.REVIEW_LAST
    ));
    private static final Pattern COMMAND_PATTERN = Pattern.compile("/(" + String.join("|",
            COMMAND_MAP.keySet()) + ")\\b((?:\\s+--\\w+(?:=\\w+)?)+)?");
    private static final Pattern OPTIONS_PATTERN = Pattern.compile("--(\\w+)(?:=(\\w+))?");

    private static Settings settings;

    public static boolean parseCommands(GerritChange change, String comment) {
        settings = DynamicSettings.getInstance(change);
        Matcher reviewCommandMatcher = COMMAND_PATTERN.matcher(comment);
        if (reviewCommandMatcher.find()) {
            parseCommand(reviewCommandMatcher.group(1));
            if (reviewCommandMatcher.group(2) != null) {
                parseOption(reviewCommandMatcher.group(2));
            }
            return true;
        }
        return false;
    }

    public static String removeCommands(String comment) {
        Matcher reviewCommandMatcher = COMMAND_PATTERN.matcher(comment);
        return reviewCommandMatcher.replaceAll("");
    }

    private static void parseCommand(String commandString) {
        COMMAND_SET command = COMMAND_MAP.get(commandString);
        if (REVIEW_COMMANDS.contains(command)) {
            settings.setForcedReview(true);
        }
        if (command == COMMAND_SET.REVIEW_LAST) {
            log.info("Forced review command applied to the last Patch Set");
            settings.setForcedReviewLastPatchSet(true);
        }
        else {
            log.info("Forced review command applied to the entire Change Set");
        }
    }

    private static void parseOption(String options) {
        Matcher reviewOptionsMatcher = OPTIONS_PATTERN.matcher(options);
        while (reviewOptionsMatcher.find()) {
            OPTION_SET option = OPTION_MAP.get(reviewOptionsMatcher.group(1));
            if (option == OPTION_SET.FILTER) {
                boolean value = Boolean.parseBoolean(reviewOptionsMatcher.group(2));
                log.info("Option 'filter' set to {}", value);
                settings.setForcedReviewFilter(value);
            }
        }
    }

}
