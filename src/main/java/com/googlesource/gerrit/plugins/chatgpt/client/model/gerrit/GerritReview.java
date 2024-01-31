package com.googlesource.gerrit.plugins.chatgpt.client.model.gerrit;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class GerritReview {
    private Map<String, List<GerritComment>> comments;
    private String message;
}
