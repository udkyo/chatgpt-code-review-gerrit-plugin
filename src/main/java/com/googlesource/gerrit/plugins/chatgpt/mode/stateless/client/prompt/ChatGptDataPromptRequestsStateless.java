package com.googlesource.gerrit.plugins.chatgpt.mode.stateless.client.prompt;

import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import com.googlesource.gerrit.plugins.chatgpt.interfaces.mode.common.client.prompt.IChatGptDataPrompt;
import com.googlesource.gerrit.plugins.chatgpt.localization.Localizer;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.client.prompt.ChatGptDataPromptRequests;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.model.api.chatgpt.ChatGptMessageItem;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.model.data.ChangeSetData;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.model.data.GerritClientData;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChatGptDataPromptRequestsStateless extends ChatGptDataPromptRequests implements IChatGptDataPrompt {
    public ChatGptDataPromptRequestsStateless(
            Configuration config,
            ChangeSetData changeSetData,
            GerritClientData gerritClientData,
            Localizer localizer
    ) {
        super(config, changeSetData, gerritClientData, localizer);
    }

    protected ChatGptMessageItem getMessageItem(int i) {
        super.getMessageItem(i);
        setHistory(messageItem, messageHistory);

        return messageItem;
    }
}
