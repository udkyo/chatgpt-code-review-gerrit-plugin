package com.googlesource.gerrit.plugins.chatgpt.client.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class GerritCommentRange {

    public int start_line;
    public int end_line;
    public int start_character;
    public int end_character;
}
