package com.googlesource.gerrit.plugins.chatgpt.client.prompt;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import com.googlesource.gerrit.plugins.chatgpt.model.settings.Settings;
import com.googlesource.gerrit.plugins.chatgpt.settings.DynamicSettings;
import com.googlesource.gerrit.plugins.chatgpt.utils.FileUtils;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

import static com.googlesource.gerrit.plugins.chatgpt.utils.StringUtils.*;

@Slf4j
public class ChatGptPrompt {
    public static final String SPACE = " ";
    public static final String COMMA = ", ";
    public static final String SEMICOLON = "; ";
    public static final String DOT = ". ";
    public static final String BACKTICK = "`";
    public static final String[] PATCH_SET_REVIEW_REPLIES_ATTRIBUTES = {"reply", "repeated", "conflicting"};
    public static final String[] REQUEST_REPLIES_ATTRIBUTES = {"reply", "id", "changeId"};

    // Prompt constants loaded from JSON file
    public static String DEFAULT_GPT_SYSTEM_PROMPT;
    public static String DEFAULT_GPT_SYSTEM_PROMPT_INPUT_DESCRIPTION;
    public static String DEFAULT_GPT_SYSTEM_PROMPT_INPUT_DESCRIPTION_REVIEW;
    public static String DEFAULT_GPT_REVIEW_PROMPT;
    public static String DEFAULT_GPT_REVIEW_PROMPT_REVIEW;
    public static String DEFAULT_GPT_REVIEW_PROMPT_MESSAGE_HISTORY;
    public static String DEFAULT_GPT_REVIEW_PROMPT_DIFF;
    public static String DEFAULT_GPT_REPLIES_PROMPT;
    public static String DEFAULT_GPT_REPLIES_PROMPT_INLINE;
    public static String DEFAULT_GPT_REPLIES_PROMPT_ENFORCE_RESPONSE_CHECK;
    public static String DEFAULT_GPT_REQUEST_PROMPT_DIFF;
    public static String DEFAULT_GPT_REQUEST_PROMPT_REQUESTS;
    public static String DEFAULT_GPT_REVIEW_PROMPT_COMMIT_MESSAGES;
    public static String DEFAULT_GPT_REVIEW_PROMPT_VOTING;
    public static Map<String, String> DEFAULT_GPT_REPLIES_ATTRIBUTES;

    private final Configuration config;
    @Setter
    private boolean isCommentEvent;

    public ChatGptPrompt(Configuration config) {
        this.config = config;
        loadPrompts();
    }

    public ChatGptPrompt(Configuration config, boolean isCommentEvent) {
        this(config);
        this.isCommentEvent = isCommentEvent;
    }

    public static String getDefaultGptReviewSystemPrompt() {
        return DEFAULT_GPT_SYSTEM_PROMPT + DOT +
                DEFAULT_GPT_SYSTEM_PROMPT_INPUT_DESCRIPTION + SPACE +
                DEFAULT_GPT_SYSTEM_PROMPT_INPUT_DESCRIPTION_REVIEW;
    }

    public static String getPatchSetReviewUserPrompt() {
        return buildFieldSpecifications(PATCH_SET_REVIEW_REPLIES_ATTRIBUTES) + SPACE +
                DEFAULT_GPT_REPLIES_PROMPT_INLINE;
    }

    public static String getCommentRequestUserPrompt(int commentPropertiesSize) {
        return buildFieldSpecifications(REQUEST_REPLIES_ATTRIBUTES) + SPACE +
                DEFAULT_GPT_REPLIES_PROMPT_INLINE + SPACE +
                String.format(DEFAULT_GPT_REPLIES_PROMPT_ENFORCE_RESPONSE_CHECK, commentPropertiesSize);
    }

    public String getGptSystemPrompt() {
        List<String> prompt = new ArrayList<>(Arrays.asList(
                config.getString(Configuration.KEY_GPT_SYSTEM_PROMPT, DEFAULT_GPT_SYSTEM_PROMPT), DOT,
                DEFAULT_GPT_SYSTEM_PROMPT_INPUT_DESCRIPTION
        ));
        if (!isCommentEvent) {
            prompt.addAll(Arrays.asList(SPACE, DEFAULT_GPT_SYSTEM_PROMPT_INPUT_DESCRIPTION_REVIEW));
        }
        return concatenate(prompt);
    }

    public String getGptUserPrompt(String patchSet, String changeId) {
        List<String> prompt = new ArrayList<>();
        Settings settings = DynamicSettings.getInstance(changeId);
        String gptRequestUserPrompt = settings.getGptRequestUserPrompt();
        boolean isValidRequestUserPrompt = gptRequestUserPrompt != null && !gptRequestUserPrompt.isEmpty();
        if (isCommentEvent && isValidRequestUserPrompt) {
            log.debug("ConfigsDynamically value found: {}", gptRequestUserPrompt);
            prompt.addAll(Arrays.asList(
                    DEFAULT_GPT_REQUEST_PROMPT_DIFF,
                    patchSet,
                    DEFAULT_GPT_REQUEST_PROMPT_REQUESTS,
                    gptRequestUserPrompt,
                    getCommentRequestUserPrompt(settings.getCommentPropertiesSize())
            ));
        }
        else {
            prompt.add(DEFAULT_GPT_REVIEW_PROMPT);
            prompt.addAll(getReviewSteps());
            prompt.add(DEFAULT_GPT_REVIEW_PROMPT_DIFF);
            prompt.add(patchSet);
            if (isValidRequestUserPrompt) {
                prompt.add(DEFAULT_GPT_REVIEW_PROMPT_MESSAGE_HISTORY);
                prompt.add(gptRequestUserPrompt);
            }
        }
        return joinWithNewLine(prompt);
    }

    private static String buildFieldSpecifications(String[] filterFields) {
        Set<String> orderedFilterFields = new LinkedHashSet<>(Arrays.asList(filterFields));
        Map<String, String> attributes = DEFAULT_GPT_REPLIES_ATTRIBUTES.entrySet().stream()
                .filter(entry -> orderedFilterFields.contains(entry.getKey()))
                .collect(Collectors.toMap(
                        entry -> BACKTICK + entry.getKey() + BACKTICK,
                        Map.Entry::getValue,
                        (oldValue, newValue) -> oldValue,
                        LinkedHashMap::new
                ));
        List<String> fieldDescription = attributes.entrySet().stream()
                .map(entry -> entry.getKey() + SPACE + entry.getValue())
                .collect(Collectors.toList());

        return String.format(DEFAULT_GPT_REPLIES_PROMPT,
                String.join(COMMA, attributes.keySet()),
                String.join(SEMICOLON, fieldDescription)
        );
    }

    private List<String> getReviewSteps() {
        List<String> steps = new ArrayList<>(){};
        steps.add(DEFAULT_GPT_REVIEW_PROMPT_REVIEW + SPACE + getPatchSetReviewUserPrompt());
        if (config.getGptReviewCommitMessages()) {
            steps.add(DEFAULT_GPT_REVIEW_PROMPT_COMMIT_MESSAGES);
        }
        if (config.isVotingEnabled()) {
            steps.add(String.format(DEFAULT_GPT_REVIEW_PROMPT_VOTING, config.getVotingMinScore(),
                    config.getVotingMaxScore()));
        }
        return getNumberedList(steps);
    }

    private void loadPrompts() {
        // Avoid repeated loading of prompt constants
        if (DEFAULT_GPT_SYSTEM_PROMPT != null) return;
        Gson gson = new Gson();
        Class<? extends ChatGptPrompt> me = this.getClass();
        try (InputStreamReader reader = FileUtils.getInputStreamReader("Config/prompts.json")) {
            Map<String, Object> values = gson.fromJson(reader, new TypeToken<Map<String, Object>>(){}.getType());
            for (Map.Entry<String, Object> entry : values.entrySet()) {
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
        // Keep the given order of attributes
        DEFAULT_GPT_REPLIES_ATTRIBUTES = new LinkedHashMap<>(DEFAULT_GPT_REPLIES_ATTRIBUTES);
    }

}
