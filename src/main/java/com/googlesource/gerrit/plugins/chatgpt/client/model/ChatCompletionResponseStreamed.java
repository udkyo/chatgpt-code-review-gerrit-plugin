package com.googlesource.gerrit.plugins.chatgpt.client.model;

import lombok.Data;

import java.util.List;

@Data
public class ChatCompletionResponseStreamed {
    private List<ChatCompletionBase.Choice> choices;
}
