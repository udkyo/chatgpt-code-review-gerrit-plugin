package com.googlesource.gerrit.plugins.chatgpt.client.chatgpt;

import com.google.gson.Gson;
import com.googlesource.gerrit.plugins.chatgpt.client.model.chatGpt.ChatGptRequest;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import com.googlesource.gerrit.plugins.chatgpt.utils.FileUtils;

import java.io.IOException;
import java.io.InputStreamReader;

class ChatGptTools {

    private final Gson gson = new Gson();
    private final boolean isCommentEvent;

    public ChatGptTools(boolean isCommentEvent) {
        this.isCommentEvent = isCommentEvent;
    }

    public ChatGptRequest retrieveTools(Configuration config) {
        ChatGptRequest tools;
        try (InputStreamReader reader = FileUtils.getInputStreamReader("Config/tools.json")) {
            tools = gson.fromJson(reader, ChatGptRequest.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load ChatGPT request tools", e);
        }
        if (!config.isVotingEnabled() || isCommentEvent) {
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
