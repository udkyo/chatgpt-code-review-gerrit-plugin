package com.googlesource.gerrit.plugins.chatgpt.client.chatgpt;

import com.google.gson.Gson;
import com.googlesource.gerrit.plugins.chatgpt.client.common.ClientBase;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import com.googlesource.gerrit.plugins.chatgpt.model.chatgpt.ChatGptRequest;
import com.googlesource.gerrit.plugins.chatgpt.settings.DynamicSettings;
import com.googlesource.gerrit.plugins.chatgpt.utils.FileUtils;

import java.io.IOException;
import java.io.InputStreamReader;

class ChatGptTools extends ClientBase {
    private final boolean isCommentEvent;
    private final Gson gson = new Gson();

    public ChatGptTools(Configuration config, Boolean isCommentEvent) {
        super(config);
        this.isCommentEvent = isCommentEvent;
    }

    public ChatGptRequest retrieveTools(String changeId) {
        ChatGptRequest tools;
        try (InputStreamReader reader = FileUtils.getInputStreamReader("Config/tools.json")) {
            tools = gson.fromJson(reader, ChatGptRequest.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load ChatGPT request tools", e);
        }
        if (!config.isVotingEnabled() || isCommentEvent
                || DynamicSettings.getInstance(changeId).getForcedReviewLastPatchSet()) {
            removeScoreFromTools(tools);
        }
        return tools;
    }

    private void removeScoreFromTools(ChatGptRequest tools) {
        ChatGptRequest.Tool.Function.Parameters parameters = tools.getTools().get(0).getFunction().getParameters();
        parameters.getProperties().setScore(null);
        parameters.getRequired().remove("score");
    }

}
