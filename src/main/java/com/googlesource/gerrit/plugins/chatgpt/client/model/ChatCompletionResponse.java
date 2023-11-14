package com.googlesource.gerrit.plugins.chatgpt.client.model;

import lombok.Data;

import java.util.List;

@Data
public class ChatCompletionResponse extends ChatCompletionBase {

    private List<Choice> choices;
}
