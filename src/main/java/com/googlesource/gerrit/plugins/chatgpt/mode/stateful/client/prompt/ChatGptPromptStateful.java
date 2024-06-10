package com.googlesource.gerrit.plugins.chatgpt.mode.stateful.client.prompt;

import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.client.api.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.client.prompt.ChatGptPrompt;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.model.data.ChangeSetData;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class ChatGptPromptStateful extends ChatGptPrompt {
    public static String DEFAULT_GPT_ASSISTANT_NAME;
    public static String DEFAULT_GPT_ASSISTANT_DESCRIPTION;
    public static String DEFAULT_GPT_ASSISTANT_INSTRUCTIONS;
    public static String DEFAULT_GPT_MESSAGE_REVIEW;

    private final GerritChange change;

    private ChangeSetData changeSetData;

    public ChatGptPromptStateful(Configuration config, GerritChange change) {
        super(config);
        this.change = change;
        this.isCommentEvent = change.getIsCommentEvent();
        // Avoid repeated loading of prompt constants
        if (DEFAULT_GPT_ASSISTANT_NAME == null) {
            loadPrompts("promptsStateful");
        }
    }

    public ChatGptPromptStateful(Configuration config, ChangeSetData changeSetData, GerritChange change) {
        this(config, change);
        this.changeSetData = changeSetData;
    }

    public String getDefaultGptAssistantDescription() {
        return String.format(DEFAULT_GPT_ASSISTANT_DESCRIPTION, change.getProjectName());
    }

    public String getDefaultGptAssistantInstructions() {
        String instructions = DEFAULT_GPT_SYSTEM_PROMPT + DOT +
                String.format(DEFAULT_GPT_ASSISTANT_INSTRUCTIONS, change.getProjectName()) + SPACE +
                getPatchSetReviewUserPrompt();
        if (config.getGptReviewCommitMessages()) {
            instructions += SPACE + getReviewPromptCommitMessages();
        }
        return instructions;
    }

    public String getDefaultGptThreadReviewMessage(String patchSet) {
        String gptRequestUserPrompt = getGptRequestUserPrompt();
        if (gptRequestUserPrompt != null && !gptRequestUserPrompt.isEmpty()) {
            log.debug("Request User Prompt retrieved: {}", gptRequestUserPrompt);
            return gptRequestUserPrompt;
        }
        else {
            return String.format(DEFAULT_GPT_MESSAGE_REVIEW, patchSet);
        }
    }

    private String getGptRequestUserPrompt() {
        if (changeSetData == null || !isCommentEvent) return null;
        return changeSetData.getGptRequestUserPrompt();
    }
}
