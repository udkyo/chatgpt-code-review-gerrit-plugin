package com.googlesource.gerrit.plugins.chatgpt.model.common;

import com.googlesource.gerrit.plugins.chatgpt.model.gerrit.GerritComment;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.HashMap;
import java.util.List;

@AllArgsConstructor
@Data
public class CommentData {
    private List<GerritComment> commentProperties;
    private HashMap<String, GerritComment> commentMap;
    private HashMap<String, GerritComment> commentGlobalMap;
}
