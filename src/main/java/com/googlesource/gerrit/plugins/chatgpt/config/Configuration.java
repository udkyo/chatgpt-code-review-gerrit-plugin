package com.googlesource.gerrit.plugins.chatgpt.config;

import com.google.common.collect.Maps;
import com.google.gerrit.server.config.PluginConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class Configuration {

    public static final String OPENAI_DOMAIN = "https://api.openai.com";
    public static final String DEFAULT_GPT_MODEL = "gpt-3.5-turbo";
    public static final String DEFAULT_GPT_SYSTEM_PROMPT = "Act as a Code Review Helper of Patchset Diffs. In a " +
            "Patchset Diff, the \"a\" items are the lines removed, the \"b\" items are the lines added, and the " +
            "\"ab\" items are the unchanged lines.";
    public static final String DEFAULT_GPT_USER_PROMPT = "Review the \"a\" and \"b\" items of the following Patchset " +
            "Diff, using the lines in the \"ab\" items as context.\n";
    public static final String DEFAULT_GPT_CUSTOM_USER_PROMPT_1 = "I have some requests about the following Patchset " +
            "Diff:\n";
    public static final String DEFAULT_GPT_CUSTOM_USER_PROMPT_2 = "Here are my requests:\n";
    public static final String DEFAULT_GPT_CUSTOM_USER_CONTEXT_PROMPT = "In reference to the code `%s` (from line %d " +
            "of file \"%s\"), ";
    public static final String DEFAULT_GPT_COMMIT_MESSAGES_REVIEW_USER_PROMPT = "Also, check if the content of the " +
            "commit message in the \"/COMMIT_MSG\" item matches with the changes. ";
    public static final String NOT_CONFIGURED_ERROR_MSG = "%s is not configured";
    public static final String KEY_GPT_SYSTEM_PROMPT = "gptSystemPrompt";
    public static final String KEY_GPT_USER_PROMPT = "gptUserPrompt";
    private static final String DEFAULT_GPT_TEMPERATURE = "1";
    private static final boolean DEFAULT_REVIEW_COMMIT_MESSAGES = false;
    private static final boolean DEFAULT_STREAM_OUTPUT = true;
    private static final boolean DEFAULT_GLOBAL_ENABLE = false;
    private static final String DEFAULT_ENABLED_PROJECTS = "";
    private static final boolean DEFAULT_PATCH_SET_REDUCTION = false;
    private static final boolean DEFAULT_PROJECT_ENABLE = false;
    private static final int DEFAULT_MAX_REVIEW_LINES = 1000;
    private static final int DEFAULT_MAX_REVIEW_FILE_SIZE = 10000;
    private static final String KEY_GPT_TOKEN = "gptToken";
    private static final String KEY_GERRIT_AUTH_BASE_URL = "gerritAuthBaseUrl";
    private static final String KEY_GERRIT_USERNAME = "gerritUserName";
    private static final String KEY_GERRIT_PASSWORD = "gerritPassword";
    private static final String KEY_GPT_DOMAIN = "gptDomain";
    private static final String KEY_GPT_MODEL = "gptModel";
    private static final String KEY_GPT_TEMPERATURE = "gptTemperature";
    private static final String KEY_STREAM_OUTPUT = "gptStreamOutput";
    private static final String KEY_REVIEW_COMMIT_MESSAGES = "gptReviewCommitMessages";
    private static final String KEY_PROJECT_ENABLE = "isEnabled";
    private static final String KEY_GLOBAL_ENABLE = "globalEnable";
    private static final String KEY_ENABLED_PROJECTS = "enabledProjects";
    private static final String KEY_PATCH_SET_REDUCTION = "patchSetReduction";
    private static final String KEY_MAX_REVIEW_LINES = "maxReviewLines";
    private static final String KEY_MAX_REVIEW_FILE_SIZE = "maxReviewFileSize";
    private final Map<String, Object> configsDynamically = Maps.newHashMap();
    private final PluginConfig globalConfig;
    private final PluginConfig projectConfig;

    public Configuration(PluginConfig globalConfig, PluginConfig projectConfig) {
        this.globalConfig = globalConfig;
        this.projectConfig = projectConfig;
    }

    public void resetDynamicConfiguration() {
        configsDynamically.clear();
        log.debug("configsDynamically initialized: {}", configsDynamically);
    }

    public <T> void configureDynamically(String key, T value) {
        configsDynamically.put(key, value);
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
        return getString(KEY_GPT_SYSTEM_PROMPT, DEFAULT_GPT_SYSTEM_PROMPT);
    }

    public String getGptUserPrompt(String patchSet) {
        StringBuilder prompt = new StringBuilder();
        String gptUserPrompt = configsDynamically.get(KEY_GPT_USER_PROMPT).toString();
        if (gptUserPrompt != null && !gptUserPrompt.isEmpty()) {
            log.debug("ConfigsDynamically value found: {}", gptUserPrompt);
            prompt.append(DEFAULT_GPT_CUSTOM_USER_PROMPT_1)
                    .append(patchSet)
                    .append(DEFAULT_GPT_CUSTOM_USER_PROMPT_2)
                    .append(gptUserPrompt);
        }
        else {
            prompt.append(getString(KEY_GPT_USER_PROMPT, DEFAULT_GPT_USER_PROMPT));
            if (getGptReviewCommitMessages()) {
                prompt.append(DEFAULT_GPT_COMMIT_MESSAGES_REVIEW_USER_PROMPT);
            }
            prompt.append(patchSet);
        }
        return prompt.toString();
    }

    public double getGptTemperature() {
        return Double.parseDouble(getString(KEY_GPT_TEMPERATURE, DEFAULT_GPT_TEMPERATURE));
    }

    public boolean getGptReviewCommitMessages() {
        return getBoolean(KEY_REVIEW_COMMIT_MESSAGES, DEFAULT_REVIEW_COMMIT_MESSAGES);
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

    public String getEnabledProjects() {
        return globalConfig.getString(KEY_ENABLED_PROJECTS, DEFAULT_ENABLED_PROJECTS);
    }

    public boolean isPatchSetReduction() {
        return getBoolean(KEY_PATCH_SET_REDUCTION, DEFAULT_PATCH_SET_REDUCTION);
    }

    public int getMaxReviewLines() {
        return getInt(KEY_MAX_REVIEW_LINES, DEFAULT_MAX_REVIEW_LINES);
    }

    public int getMaxReviewFileSize() {
        return getInt(KEY_MAX_REVIEW_FILE_SIZE, DEFAULT_MAX_REVIEW_FILE_SIZE);
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
        if (valueForProject != defaultValue) {
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


}

