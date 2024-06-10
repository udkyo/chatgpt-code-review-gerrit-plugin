package com.googlesource.gerrit.plugins.chatgpt.mode.stateful.client.api.chatgpt;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import com.googlesource.gerrit.plugins.chatgpt.data.PluginDataHandlerProvider;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.client.api.chatgpt.ChatGptClient;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.client.api.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.model.api.chatgpt.ChatGptResponseContent;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.model.data.ChangeSetData;
import com.googlesource.gerrit.plugins.chatgpt.mode.interfaces.client.api.chatgpt.IChatGptClient;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class ChatGptClientStateful extends ChatGptClient implements IChatGptClient {
    private final PluginDataHandlerProvider pluginDataHandlerProvider;

    @VisibleForTesting
    @Inject
    public ChatGptClientStateful(Configuration config, PluginDataHandlerProvider pluginDataHandlerProvider) {
        super(config);
        this.pluginDataHandlerProvider = pluginDataHandlerProvider;
    }

    public ChatGptResponseContent ask(ChangeSetData changeSetData, GerritChange change, String patchSet) {
        isCommentEvent = change.getIsCommentEvent();
        String changeId = change.getFullChangeId();
        log.info("Processing STATEFUL ChatGPT Request with changeId: {}, Patch Set: {}", changeId, patchSet);

        ChatGptThread chatGptThread = new ChatGptThread(
                config,
                changeSetData,
                change,
                patchSet,
                pluginDataHandlerProvider
        );
        String threadId = chatGptThread.createThread();
        chatGptThread.addMessage();

        ChatGptRun chatGptRun = new ChatGptRun(threadId, config, pluginDataHandlerProvider);
        chatGptRun.createRun();
        chatGptRun.pollRun();
        // Attribute `requestBody` is valued for testing purposes
        requestBody = chatGptThread.getAddMessageRequestBody();
        log.debug("ChatGPT request body: {}", requestBody);

        return getResponseContent(chatGptRun.getFirstStep());
    }
}
