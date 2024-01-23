package com.googlesource.gerrit.plugins.chatgpt.client.model.gerrit;

import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class GerritCodeRange {

    public int start_line;
    public int end_line;
    public int start_character;
    public int end_character;
}
