package com.googlesource.gerrit.plugins.chatgpt.client;

import lombok.Data;

@Data
public class GerritCommentRange {

    public int start_line;
    public int end_line;
    public int start_character;
    public int end_character;
}
