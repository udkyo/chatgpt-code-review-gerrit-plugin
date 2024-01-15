package com.googlesource.gerrit.plugins.chatgpt.client.model;

import lombok.Data;

import java.util.List;

@Data
public class ChatGptSuggestions extends ChatGptSuggestionPoint {
    private List<ChatGptSuggestionPoint> suggestions;
}
