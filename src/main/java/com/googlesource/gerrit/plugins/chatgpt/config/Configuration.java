package com.googlesource.gerrit.plugins.chatgpt.config;

import com.google.common.collect.Maps;
import com.google.gerrit.server.config.PluginConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
public class Configuration {

    public static final String OPENAI_DOMAIN = "https://api.openai.com";
    public static final String DEFAULT_GPT_MODEL = "gpt-3.5-turbo";
    public static final String DEFAULT_GPT_SYSTEM_PROMPT = "Act as a PatchSet Reviewer. I will provide you with " +
            "PatchSet Diffs for various files in a JSON format. Each changed file's content will be detailed in the " +
            "\"content\" field of the JSON object. In this \"content\", the \"a\" items are the lines removed, the " +
            "\"b\" items are the lines added, and the \"ab\" items are the unchanged lines. In your response, avoid " +
            "explicitly referring to the \"a\", \"b\", and other fields from the JSON object. Instead, use more " +
            "intuitive terms like \"new lines\" for additions, \"removed lines\" for deletions, and \"unchanged " +
            "lines\" for the parts that haven't been altered.";
    public static final String DEFAULT_GPT_USER_PROMPT = "Focus your review on the \"a\" and \"b\" items, but use " +
            "the \"ab\" items as context to understand the changes better. Provide insights on whether the changes " +
            "make sense, any potential issues you foresee, and suggestions for improvements if necessary.";
    public static final String DEFAULT_GPT_USER_PROMPT_JSON = "Provide your response in a JSON format. Each " +
            "suggestion must be formatted as an individual object within an array. The object will always contain " +
            "the string field `suggestion`";
    public static final String DEFAULT_GPT_CUSTOM_USER_PROMPT_JSON = " along with the key `id`, which corresponds to " +
            "the `id` value from the related request in the request JSON array";
    public static final String DEFAULT_GPT_USER_PROMPT_JSON_2 = ". For suggestions that are specific to a certain " +
            "part of the code, the object should additionally include the keys `filename`, `lineNumber`, and " +
            "`codeSnippet` to precisely identify the relevant code section.";
    public static final String DEFAULT_GPT_CUSTOM_USER_PROMPT_1 = "I have some requests about the following PatchSet " +
            "Diff:";
    public static final String DEFAULT_GPT_CUSTOM_USER_PROMPT_2 = "My requests are given in an array and formatted " +
            "in JSON :";
    public static final String DEFAULT_GPT_COMMIT_MESSAGES_REVIEW_USER_PROMPT = "Also, perform a check on the commit " +
            "message of the PatchSet. The commit message is provided in the \"content\" field of \"/COMMIT_MSG\" in " +
            "the same way as the file changes. Ensure that the commit message accurately and succinctly describes " +
            "the changes made, and verify if it matches the nature and scope of the changes in the PatchSet.";
    public static final String NOT_CONFIGURED_ERROR_MSG = "%s is not configured";
    public static final String KEY_GPT_SYSTEM_PROMPT = "gptSystemPrompt";
    public static final String KEY_GPT_USER_PROMPT = "gptUserPrompt";
    public static final String ENABLED_USERS_ALL = "ALL";
    public static final String ENABLED_GROUPS_ALL = "ALL";
    public static final String ENABLED_TOPICS_ALL = "ALL";
    private static final String DEFAULT_GPT_TEMPERATURE = "1";
    private static final boolean DEFAULT_REVIEW_PATCHSET = true;
    private static final boolean DEFAULT_REVIEW_BY_POINTS = true;
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
    private static final String KEY_GPT_TOKEN = "gptToken";
    private static final String KEY_GERRIT_AUTH_BASE_URL = "gerritAuthBaseUrl";
    private static final String KEY_GERRIT_USERNAME = "gerritUserName";
    private static final String KEY_GERRIT_PASSWORD = "gerritPassword";
    private static final String KEY_GPT_DOMAIN = "gptDomain";
    private static final String KEY_GPT_MODEL = "gptModel";
    private static final String KEY_GPT_TEMPERATURE = "gptTemperature";
    private static final String KEY_STREAM_OUTPUT = "gptStreamOutput";
    private static final String KEY_REVIEW_BY_POINTS = "gptReviewByPoints";
    private static final String KEY_REVIEW_COMMIT_MESSAGES = "gptReviewCommitMessages";
    private static final String KEY_REVIEW_PATCHSET = "gptReviewPatchSet";
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
        List<String> prompt = new ArrayList<>();
        String gptUserPrompt = configsDynamically.get(KEY_GPT_USER_PROMPT).toString();
        if (gptUserPrompt != null && !gptUserPrompt.isEmpty()) {
            log.debug("ConfigsDynamically value found: {}", gptUserPrompt);
            prompt.addAll(Arrays.asList(
                    DEFAULT_GPT_CUSTOM_USER_PROMPT_1,
                    patchSet,
                    DEFAULT_GPT_CUSTOM_USER_PROMPT_2,
                    gptUserPrompt,
                    DEFAULT_GPT_USER_PROMPT_JSON + DEFAULT_GPT_CUSTOM_USER_PROMPT_JSON + DEFAULT_GPT_USER_PROMPT_JSON_2
            ));
        }
        else {
            prompt.add(getString(KEY_GPT_USER_PROMPT, DEFAULT_GPT_USER_PROMPT));
            if (getGptReviewByPoints()) {
                prompt.add(DEFAULT_GPT_USER_PROMPT_JSON + DEFAULT_GPT_USER_PROMPT_JSON_2);
            }
            if (getGptReviewCommitMessages()) {
                prompt.add(DEFAULT_GPT_COMMIT_MESSAGES_REVIEW_USER_PROMPT);
            }
            prompt.add(patchSet);
        }
        return String.join("\n", prompt);
    }

    public double getGptTemperature() {
        return Double.parseDouble(getString(KEY_GPT_TEMPERATURE, DEFAULT_GPT_TEMPERATURE));
    }

    public boolean getGptReviewPatchSet() {
        return getBoolean(KEY_REVIEW_PATCHSET, DEFAULT_REVIEW_PATCHSET);
    }

    public boolean getGptReviewByPoints() {
        return getBoolean(KEY_REVIEW_BY_POINTS, DEFAULT_REVIEW_BY_POINTS);
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

