package com.googlesource.gerrit.plugins.chatgpt.config;

import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Pattern;

import static com.googlesource.gerrit.plugins.chatgpt.settings.Settings.MODES;

@Slf4j
public class Configuration {
    // Config Constants
    public static final String ENABLED_USERS_ALL = "ALL";
    public static final String ENABLED_GROUPS_ALL = "ALL";
    public static final String ENABLED_TOPICS_ALL = "ALL";
    public static final String NOT_CONFIGURED_ERROR_MSG = "%s is not configured";

    // Default Config values
    public static final String OPENAI_DOMAIN = "https://api.openai.com";
    public static final String DEFAULT_GPT_MODEL = "gpt-3.5-turbo";
    public static final double DEFAULT_GPT_REVIEW_TEMPERATURE = 0.2;
    public static final double DEFAULT_GPT_COMMENT_TEMPERATURE = 1.0;

    private static final String DEFAULT_GPT_MODE = "stateless";
    private static final boolean DEFAULT_REVIEW_PATCH_SET = true;
    private static final boolean DEFAULT_REVIEW_COMMIT_MESSAGES = true;
    private static final boolean DEFAULT_FULL_FILE_REVIEW = true;
    private static final boolean DEFAULT_STREAM_OUTPUT = false;
    private static final boolean DEFAULT_GLOBAL_ENABLE = false;
    private static final String DEFAULT_DISABLED_USERS = "";
    private static final String DEFAULT_ENABLED_USERS = ENABLED_USERS_ALL;
    private static final String DEFAULT_DISABLED_GROUPS = "";
    private static final String DEFAULT_ENABLED_GROUPS = ENABLED_GROUPS_ALL;
    private static final String DEFAULT_DISABLED_TOPIC_FILTER = "";
    private static final String DEFAULT_ENABLED_TOPIC_FILTER = ENABLED_TOPICS_ALL;
    private static final String DEFAULT_ENABLED_PROJECTS = "";
    private static final String DEFAULT_ENABLED_FILE_EXTENSIONS = String.join(",", new String[]{
            ".bat"
            ".c",
            ".cpp",
            ".cs",
            ".css",
            ".dart",
            ".go",
            ".h",
            ".html",
            ".java",
            ".jl",
            ".js",
            ".kt",
            ".lua",
            ".php",
            ".pl",
            ".pm",
            ".py",
            ".r",
            ".rb",
            ".rs",
            ".scala",
            ".swift",
            ".sh",
            ".ts",
            ".vb",
    });
    private static final boolean DEFAULT_PROJECT_ENABLE = false;
    private static final int DEFAULT_MAX_REVIEW_LINES = 1000;
    private static final int DEFAULT_MAX_REVIEW_FILE_SIZE = 10000;
    private static final boolean DEFAULT_ENABLED_VOTING = false;
    private static final boolean DEFAULT_FILTER_NEGATIVE_COMMENTS = true;
    private static final int DEFAULT_FILTER_COMMENTS_BELOW_SCORE = 0;
    private static final boolean DEFAULT_FILTER_RELEVANT_COMMENTS = true;
    private static final double DEFAULT_FILTER_COMMENTS_RELEVANCE_THRESHOLD = 0.6;
    private static final int DEFAULT_VOTING_MIN_SCORE = -1;
    private static final int DEFAULT_VOTING_MAX_SCORE = 1;
    private static final boolean DEFAULT_INLINE_COMMENTS_AS_RESOLVED = false;
    private static final boolean DEFAULT_PATCH_SET_COMMENTS_AS_RESOLVED = false;
    private static final boolean DEFAULT_IGNORE_OUTDATED_INLINE_COMMENTS = false;
    private static final boolean DEFAULT_IGNORE_RESOLVED_CHAT_GPT_COMMENTS = true;

    // Config setting keys
    public static final String KEY_GPT_SYSTEM_PROMPT = "gptSystemPrompt";
    public static final String KEY_GPT_RELEVANCE_RULES = "gptRelevanceRules";
    public static final String KEY_GPT_REVIEW_TEMPERATURE = "gptReviewTemperature";
    public static final String KEY_GPT_COMMENT_TEMPERATURE = "gptCommentTemperature";
    public static final String KEY_VOTING_MIN_SCORE = "votingMinScore";
    public static final String KEY_VOTING_MAX_SCORE = "votingMaxScore";
    public static final String KEY_GERRIT_USERNAME = "gerritUserName";

    private static final String KEY_GPT_TOKEN = "gptToken";
    private static final String KEY_GPT_DOMAIN = "gptDomain";
    private static final String KEY_GPT_MODEL = "gptModel";
    private static final String KEY_STREAM_OUTPUT = "gptStreamOutput";
    private static final String KEY_GPT_MODE = "gptMode";
    private static final String KEY_REVIEW_COMMIT_MESSAGES = "gptReviewCommitMessages";
    private static final String KEY_REVIEW_PATCH_SET = "gptReviewPatchSet";
    private static final String KEY_FULL_FILE_REVIEW = "gptFullFileReview";
    private static final String KEY_PROJECT_ENABLE = "isEnabled";
    private static final String KEY_GLOBAL_ENABLE = "globalEnable";
    private static final String KEY_DISABLED_USERS = "disabledUsers";
    private static final String KEY_ENABLED_USERS = "enabledUsers";
    private static final String KEY_DISABLED_GROUPS = "disabledGroups";
    private static final String KEY_ENABLED_GROUPS = "enabledGroups";
    private static final String KEY_DISABLED_TOPIC_FILTER = "disabledTopicFilter";
    private static final String KEY_ENABLED_TOPIC_FILTER = "enabledTopicFilter";
    private static final String KEY_ENABLED_PROJECTS = "enabledProjects";
    private static final String KEY_MAX_REVIEW_LINES = "maxReviewLines";
    private static final String KEY_MAX_REVIEW_FILE_SIZE = "maxReviewFileSize";
    private static final String KEY_ENABLED_FILE_EXTENSIONS = "enabledFileExtensions";
    private static final String KEY_ENABLED_VOTING = "enabledVoting";
    private static final String KEY_FILTER_NEGATIVE_COMMENTS = "filterNegativeComments";
    private static final String KEY_FILTER_COMMENTS_BELOW_SCORE = "filterCommentsBelowScore";
    private static final String KEY_FILTER_RELEVANT_COMMENTS = "filterRelevantComments";
    private static final String KEY_FILTER_COMMENTS_RELEVANCE_THRESHOLD = "filterCommentsRelevanceThreshold";
    private static final String KEY_INLINE_COMMENTS_AS_RESOLVED = "inlineCommentsAsResolved";
    private static final String KEY_PATCH_SET_COMMENTS_AS_RESOLVED = "patchSetCommentsAsResolved";
    private static final String KEY_IGNORE_OUTDATED_INLINE_COMMENTS = "ignoreOutdatedInlineComments";
    private static final String KEY_IGNORE_RESOLVED_CHAT_GPT_COMMENTS = "ignoreResolvedChatGptComments";

    private final OneOffRequestContext context;
    @Getter
    private final Account.Id userId;
    @Getter
    private final PluginConfig globalConfig;
    @Getter
    private final PluginConfig projectConfig;
    @Getter
    private final String gerritUserEmail;
    @Getter
    private final GerritApi gerritApi;


    public Configuration(OneOffRequestContext context, GerritApi gerritApi, PluginConfig globalConfig, PluginConfig projectConfig, String gerritUserEmail, Account.Id userId) {
        this.context = context;
        this.gerritApi = gerritApi;
        this.globalConfig = globalConfig;
        this.projectConfig = projectConfig;
        this.gerritUserEmail = gerritUserEmail;
        this.userId = userId;
    }

    public ManualRequestContext openRequestContext() {
      return context.openAs(userId);
    }

    public String getGptToken() {
        return getValidatedOrThrow(KEY_GPT_TOKEN);
    }

    public String getGerritUserName() {
        return getValidatedOrThrow(KEY_GERRIT_USERNAME);
    }

    public String getGptDomain() {
        return getString(KEY_GPT_DOMAIN, OPENAI_DOMAIN);
    }

    public String getGptModel() {
        return getString(KEY_GPT_MODEL, DEFAULT_GPT_MODEL);
    }

    public boolean getGptReviewPatchSet() {
        return getBoolean(KEY_REVIEW_PATCH_SET, DEFAULT_REVIEW_PATCH_SET);
    }

    public MODES getGptMode() {
        String mode = getString(KEY_GPT_MODE, DEFAULT_GPT_MODE);
        try {
            return Enum.valueOf(MODES.class, mode);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Illegal mode: " + mode, e);
        }
    }

    public boolean getGptReviewCommitMessages() {
        return getBoolean(KEY_REVIEW_COMMIT_MESSAGES, DEFAULT_REVIEW_COMMIT_MESSAGES);
    }

    public boolean getGptFullFileReview() {
        return getBoolean(KEY_FULL_FILE_REVIEW, DEFAULT_FULL_FILE_REVIEW);
    }

    public boolean getGptStreamOutput() {
        return getBoolean(KEY_STREAM_OUTPUT, DEFAULT_STREAM_OUTPUT);
    }

    public boolean isProjectEnable() {
        return projectConfig.getBoolean(KEY_PROJECT_ENABLE, DEFAULT_PROJECT_ENABLE);
    }

    public boolean isGlobalEnable() {
        return globalConfig.getBoolean(KEY_GLOBAL_ENABLE, DEFAULT_GLOBAL_ENABLE);
    }

    public List<String> getDisabledUsers() {
        return splitConfig(globalConfig.getString(KEY_DISABLED_USERS, DEFAULT_DISABLED_USERS));
    }

    public List<String> getEnabledUsers() {
        return splitConfig(globalConfig.getString(KEY_ENABLED_USERS, DEFAULT_ENABLED_USERS));
    }

    public List<String> getDisabledGroups() {
        return splitConfig(globalConfig.getString(KEY_DISABLED_GROUPS, DEFAULT_DISABLED_GROUPS));
    }

    public List<String> getEnabledGroups() {
        return splitConfig(globalConfig.getString(KEY_ENABLED_GROUPS, DEFAULT_ENABLED_GROUPS));
    }

    public List<String> getDisabledTopicFilter() {
        return splitConfig(globalConfig.getString(KEY_DISABLED_TOPIC_FILTER, DEFAULT_DISABLED_TOPIC_FILTER));
    }

    public List<String> getEnabledTopicFilter() {
        return splitConfig(globalConfig.getString(KEY_ENABLED_TOPIC_FILTER, DEFAULT_ENABLED_TOPIC_FILTER));
    }

    public String getEnabledProjects() {
        return globalConfig.getString(KEY_ENABLED_PROJECTS, DEFAULT_ENABLED_PROJECTS);
    }

    public int getMaxReviewLines() {
        return getInt(KEY_MAX_REVIEW_LINES, DEFAULT_MAX_REVIEW_LINES);
    }

    public int getMaxReviewFileSize() {
        return getInt(KEY_MAX_REVIEW_FILE_SIZE, DEFAULT_MAX_REVIEW_FILE_SIZE);
    }

    public List<String> getEnabledFileExtensions() {
        return splitConfig(globalConfig.getString(KEY_ENABLED_FILE_EXTENSIONS, DEFAULT_ENABLED_FILE_EXTENSIONS));
    }

    public boolean isVotingEnabled() {
        return getBoolean(KEY_ENABLED_VOTING, DEFAULT_ENABLED_VOTING);
    }

    public boolean getFilterNegativeComments() {
        return getBoolean(KEY_FILTER_NEGATIVE_COMMENTS, DEFAULT_FILTER_NEGATIVE_COMMENTS);
    }

    public int getFilterCommentsBelowScore() {
        return getInt(KEY_FILTER_COMMENTS_BELOW_SCORE, DEFAULT_FILTER_COMMENTS_BELOW_SCORE);
    }

    public boolean getFilterRelevantComments() {
        return getBoolean(KEY_FILTER_RELEVANT_COMMENTS, DEFAULT_FILTER_RELEVANT_COMMENTS);
    }

    public double getFilterCommentsRelevanceThreshold() {
        return getDouble(KEY_FILTER_COMMENTS_RELEVANCE_THRESHOLD, DEFAULT_FILTER_COMMENTS_RELEVANCE_THRESHOLD);
    }

    public int getVotingMinScore() {
        return getInt(KEY_VOTING_MIN_SCORE, DEFAULT_VOTING_MIN_SCORE);
    }

    public int getVotingMaxScore() {
        return getInt(KEY_VOTING_MAX_SCORE, DEFAULT_VOTING_MAX_SCORE);
    }

    public boolean getInlineCommentsAsResolved() {
        return getBoolean(KEY_INLINE_COMMENTS_AS_RESOLVED, DEFAULT_INLINE_COMMENTS_AS_RESOLVED);
    }

    public boolean getPatchSetCommentsAsResolved() {
        return getBoolean(KEY_PATCH_SET_COMMENTS_AS_RESOLVED, DEFAULT_PATCH_SET_COMMENTS_AS_RESOLVED);
    }

    public boolean getIgnoreResolvedChatGptComments() {
        return getBoolean(KEY_IGNORE_RESOLVED_CHAT_GPT_COMMENTS, DEFAULT_IGNORE_RESOLVED_CHAT_GPT_COMMENTS);
    }

    public boolean getIgnoreOutdatedInlineComments() {
        return getBoolean(KEY_IGNORE_OUTDATED_INLINE_COMMENTS, DEFAULT_IGNORE_OUTDATED_INLINE_COMMENTS);
    }

    public String getString(String key, String defaultValue) {
        String value = projectConfig.getString(key);
        if (value != null) {
            return value;
        }
        return globalConfig.getString(key, defaultValue);
    }

    private String getValidatedOrThrow(String key) {
        String value = projectConfig.getString(key);
        if (value == null) {
            value = globalConfig.getString(key);
        }
        if (value == null) {
            throw new RuntimeException(String.format(NOT_CONFIGURED_ERROR_MSG, key));
        }
        return value;
    }

    private int getInt(String key, int defaultValue) {
        int valueForProject = projectConfig.getInt(key, defaultValue);
        // To avoid misinterpreting an undefined value as zero, a secondary check is performed by retrieving the value
        // as a String.
        if (valueForProject != defaultValue && valueForProject != 0
                && projectConfig.getString(key, "") != null) {
            return valueForProject;
        }
        return globalConfig.getInt(key, defaultValue);
    }

    private boolean getBoolean(String key, boolean defaultValue) {
        boolean valueForProject = projectConfig.getBoolean(key, defaultValue);
        if (projectConfig.getString(key) != null) {
            return valueForProject;
        }
        return globalConfig.getBoolean(key, defaultValue);
    }

    private Double getDouble(String key, Double defaultValue) {
        return Double.parseDouble(getString(key, String.valueOf(defaultValue)));
    }

    private List<String> splitConfig(String value) {
        Pattern separator=Pattern.compile("\\s*,\\s*");
        return Arrays.asList(separator.split(value));
    }

}
