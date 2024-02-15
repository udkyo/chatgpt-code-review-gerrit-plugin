package com.googlesource.gerrit.plugins.chatgpt.client.prompt;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import com.googlesource.gerrit.plugins.chatgpt.model.settings.Settings;
import com.googlesource.gerrit.plugins.chatgpt.settings.DynamicSettings;
import com.googlesource.gerrit.plugins.chatgpt.utils.FileUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
public class ChatGptPrompt {
    public static final String SPACE = " ";
    public static final String DOT_SPACE = ". ";

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

    private final Configuration config;

    public ChatGptPrompt(Configuration config) {
        this.config = config;
        loadPrompts();
    }

    public static String getDefaultSystemPrompt() {
        return DEFAULT_GPT_SYSTEM_PROMPT + DOT_SPACE + DEFAULT_GPT_SYSTEM_PROMPT_INSTRUCTIONS;
    }

    public static String getPatchSetReviewUserPrompt() {
        return DEFAULT_GPT_JSON_USER_PROMPT + DOT_SPACE + DEFAULT_GPT_JSON_USER_PROMPT_2;
    }

    public static String getCommentRequestUserPrompt(int commentPropertiesSize) {
        return DEFAULT_GPT_JSON_USER_PROMPT + SPACE +
                DEFAULT_GPT_REQUEST_JSON_USER_PROMPT + DOT_SPACE +
                DEFAULT_GPT_JSON_USER_PROMPT_2 + SPACE +
                String.format(DEFAULT_GPT_JSON_USER_PROMPT_ENFORCE_RESPONSE_CHECK, commentPropertiesSize);
    }

    public String getGptSystemPrompt() {
        return config.getString(Configuration.KEY_GPT_SYSTEM_PROMPT, DEFAULT_GPT_SYSTEM_PROMPT) + DOT_SPACE +
                DEFAULT_GPT_SYSTEM_PROMPT_INSTRUCTIONS;
    }

    public String getGptUserPrompt(String patchSet, String changeId) {
        List<String> prompt = new ArrayList<>();
        Settings settings = DynamicSettings.getInstance(changeId);
        String gptRequestUserPrompt = settings.getGptRequestUserPrompt();
        if (gptRequestUserPrompt != null && !gptRequestUserPrompt.isEmpty()) {
            log.debug("ConfigsDynamically value found: {}", gptRequestUserPrompt);
            prompt.addAll(Arrays.asList(
                    DEFAULT_GPT_REQUEST_USER_PROMPT_1,
                    patchSet,
                    DEFAULT_GPT_REQUEST_USER_PROMPT_2,
                    gptRequestUserPrompt,
                    getCommentRequestUserPrompt(settings.getCommentPropertiesSize())
            ));
        }
        else {
            prompt.add(DEFAULT_GPT_REVIEW_USER_PROMPT);
            prompt.add(getPatchSetReviewUserPrompt());
            if (config.getGptReviewCommitMessages()) {
                prompt.add(DEFAULT_GPT_COMMIT_MESSAGES_REVIEW_USER_PROMPT);
            }
            if (config.isVotingEnabled()) {
                prompt.add(String.format(DEFAULT_GPT_VOTING_REVIEW_USER_PROMPT, config.getVotingMinScore(),
                        config.getVotingMaxScore()));
            }
            prompt.add(patchSet);
        }
        return String.join("\n", prompt);
    }

    private void loadPrompts() {
        // Avoid repeated loading of prompt constants
        if (DEFAULT_GPT_SYSTEM_PROMPT != null) return;
        Gson gson = new Gson();
        Class<? extends ChatGptPrompt> me = this.getClass();
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

}
