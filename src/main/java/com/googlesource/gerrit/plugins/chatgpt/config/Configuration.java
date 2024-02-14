package com.googlesource.gerrit.plugins.chatgpt.config;

import com.google.gerrit.server.config.PluginConfig;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.googlesource.gerrit.plugins.chatgpt.DynamicSettings;
import com.googlesource.gerrit.plugins.chatgpt.utils.FileUtils;
import com.googlesource.gerrit.plugins.chatgpt.utils.SingletonManager;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
public class Configuration {
    public static final String ENABLED_USERS_ALL = "ALL";
    public static final String ENABLED_GROUPS_ALL = "ALL";
    public static final String ENABLED_TOPICS_ALL = "ALL";
    public static final String SPACE = " ";
    public static final String DOT_SPACE = ". ";
    public static final String NOT_CONFIGURED_ERROR_MSG = "%s is not configured";

    // Default Config values
    public static final String OPENAI_DOMAIN = "https://api.openai.com";
    public static final String DEFAULT_GPT_MODEL = "gpt-3.5-turbo";
    private static final String DEFAULT_GPT_TEMPERATURE = "1";
    private static final boolean DEFAULT_REVIEW_PATCH_SET = true;
    private static final boolean DEFAULT_REVIEW_COMMIT_MESSAGES = false;
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
            ".py",
            ".java",
            ".js",
            ".ts",
            ".html",
            ".css",
            ".cs",
            ".cpp",
            ".c",
            ".h",
            ".php",
            ".rb",
            ".swift",
            ".kt",
            ".r",
            ".jl",
            ".go",
            ".scala",
            ".pl",
            ".pm",
            ".rs",
            ".dart",
            ".lua",
            ".sh",
            ".vb",
            ".bat"
    });
    private static final boolean DEFAULT_PROJECT_ENABLE = false;
    private static final int DEFAULT_MAX_REVIEW_LINES = 1000;
    private static final int DEFAULT_MAX_REVIEW_FILE_SIZE = 10000;
    private static final boolean DEFAULT_ENABLED_VOTING = false;
    private static final int DEFAULT_VOTING_MIN_SCORE = -1;
    private static final int DEFAULT_VOTING_MAX_SCORE = 1;

    // Config setting keys
    public static final String KEY_GPT_SYSTEM_PROMPT = "gptSystemPrompt";
    public static final String KEY_VOTING_MIN_SCORE = "votingMinScore";
    public static final String KEY_VOTING_MAX_SCORE = "votingMaxScore";
    private static final String KEY_GPT_TOKEN = "gptToken";
    private static final String KEY_GERRIT_AUTH_BASE_URL = "gerritAuthBaseUrl";
    private static final String KEY_GERRIT_USERNAME = "gerritUserName";
    private static final String KEY_GERRIT_PASSWORD = "gerritPassword";
    private static final String KEY_GPT_DOMAIN = "gptDomain";
    private static final String KEY_GPT_MODEL = "gptModel";
    private static final String KEY_GPT_TEMPERATURE = "gptTemperature";
    private static final String KEY_STREAM_OUTPUT = "gptStreamOutput";
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

    // Prompt constants loaded from JSON file
    public static String DEFAULT_GPT_SYSTEM_PROMPT;
    public static String DEFAULT_GPT_SYSTEM_PROMPT_INSTRUCTIONS;
    public static String DEFAULT_GPT_REVIEW_USER_PROMPT;
    public static String DEFAULT_GPT_JSON_USER_PROMPT;
    public static String DEFAULT_GPT_REQUEST_JSON_USER_PROMPT;
    public static String DEFAULT_GPT_JSON_USER_PROMPT_2;
    public static String DEFAULT_GPT_JSON_USER_PROMPT_ENFORCE_RESPONSE_CHECK;
    public static String DEFAULT_GPT_REQUEST_USER_PROMPT_1;
    public static String DEFAULT_GPT_REQUEST_USER_PROMPT_2;
    public static String DEFAULT_GPT_COMMIT_MESSAGES_REVIEW_USER_PROMPT;
    public static String DEFAULT_GPT_VOTING_REVIEW_USER_PROMPT;

    private final PluginConfig globalConfig;
    private final PluginConfig projectConfig;

    public Configuration(PluginConfig globalConfig, PluginConfig projectConfig) {
        this.globalConfig = globalConfig;
        this.projectConfig = projectConfig;
        loadPrompts();
    }

    public static String getDefaultSystemPrompt() {
        return DEFAULT_GPT_SYSTEM_PROMPT + DOT_SPACE + DEFAULT_GPT_SYSTEM_PROMPT_INSTRUCTIONS;
    }

    public static String getPatchSetReviewUserPrompt() {
        return DEFAULT_GPT_JSON_USER_PROMPT + DOT_SPACE + DEFAULT_GPT_JSON_USER_PROMPT_2;
    }

    public String getCommentRequestUserPrompt(int commentPropertiesSize) {
        return DEFAULT_GPT_JSON_USER_PROMPT + SPACE +
                DEFAULT_GPT_REQUEST_JSON_USER_PROMPT + DOT_SPACE +
                DEFAULT_GPT_JSON_USER_PROMPT_2 + SPACE +
                String.format(DEFAULT_GPT_JSON_USER_PROMPT_ENFORCE_RESPONSE_CHECK, commentPropertiesSize);
    }

    public String getGptToken() {
        return getValidatedOrThrow(KEY_GPT_TOKEN);
    }

    public String getGerritAuthBaseUrl() {
        return getValidatedOrThrow(KEY_GERRIT_AUTH_BASE_URL);
    }

    public String getGerritUserName() {
        return getValidatedOrThrow(KEY_GERRIT_USERNAME);
    }

    public String getGerritPassword() {
        return getValidatedOrThrow(KEY_GERRIT_PASSWORD);
    }

    public String getGptDomain() {
        return getString(KEY_GPT_DOMAIN, OPENAI_DOMAIN);
    }

    public String getGptModel() {
        return getString(KEY_GPT_MODEL, DEFAULT_GPT_MODEL);
    }

    public String getGptSystemPrompt() {
        return getString(KEY_GPT_SYSTEM_PROMPT, DEFAULT_GPT_SYSTEM_PROMPT) + DOT_SPACE +
                DEFAULT_GPT_SYSTEM_PROMPT_INSTRUCTIONS;
    }

    public String getGptUserPrompt(String patchSet, String changeId) {
        List<String> prompt = new ArrayList<>();
        DynamicSettings dynamicSettings = SingletonManager.getInstance(DynamicSettings.class, changeId);
        String gptRequestUserPrompt = dynamicSettings.getGptRequestUserPrompt();
        if (gptRequestUserPrompt != null && !gptRequestUserPrompt.isEmpty()) {
            log.debug("ConfigsDynamically value found: {}", gptRequestUserPrompt);
            prompt.addAll(Arrays.asList(
                    DEFAULT_GPT_REQUEST_USER_PROMPT_1,
                    patchSet,
                    DEFAULT_GPT_REQUEST_USER_PROMPT_2,
                    gptRequestUserPrompt,
                    getCommentRequestUserPrompt(dynamicSettings.getCommentPropertiesSize())
            ));
        }
        else {
            prompt.add(DEFAULT_GPT_REVIEW_USER_PROMPT);
            prompt.add(getPatchSetReviewUserPrompt());
            if (getGptReviewCommitMessages()) {
                prompt.add(DEFAULT_GPT_COMMIT_MESSAGES_REVIEW_USER_PROMPT);
            }
            if (isVotingEnabled()) {
                prompt.add(String.format(DEFAULT_GPT_VOTING_REVIEW_USER_PROMPT, getVotingMinScore(),
                        getVotingMaxScore()));
            }
            prompt.add(patchSet);
        }
        return String.join("\n", prompt);
    }

    public double getGptTemperature() {
        return Double.parseDouble(getString(KEY_GPT_TEMPERATURE, DEFAULT_GPT_TEMPERATURE));
    }

    public boolean getGptReviewPatchSet() {
        return getBoolean(KEY_REVIEW_PATCH_SET, DEFAULT_REVIEW_PATCH_SET);
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

    public int getVotingMinScore() {
        return getInt(KEY_VOTING_MIN_SCORE, DEFAULT_VOTING_MIN_SCORE);
    }

    public int getVotingMaxScore() {
        return getInt(KEY_VOTING_MAX_SCORE, DEFAULT_VOTING_MAX_SCORE);
    }


    private void loadPrompts() {
        // Avoid repeated loading of prompt constants
        if (DEFAULT_GPT_SYSTEM_PROMPT != null) return;
        Gson gson = new Gson();
        Class<? extends Configuration> me = this.getClass();
        try (InputStreamReader reader = FileUtils.getInputStreamReader("Config/prompts.json")) {
            Map<String, String> values = gson.fromJson(reader, new TypeToken<Map<String, String>>(){}.getType());
            for (Map.Entry<String, String> entry : values.entrySet()) {
                try {
                    Field field = me.getDeclaredField(entry.getKey());
                    field.setAccessible(true);
                    field.set(null, entry.getValue());
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    log.error("Error setting prompt '{}'", entry.getKey(), e);
                    throw new IOException();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load prompts", e);
        }
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

    private String getString(String key, String defaultValue) {
        String value = projectConfig.getString(key);
        if (value != null) {
            return value;
        }
        return globalConfig.getString(key, defaultValue);
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

    private List<String> splitConfig(String value) {
        Pattern separator=Pattern.compile("\\s*,\\s*");
        return Arrays.asList(separator.split(value));
    }

}

