package com.googlesource.gerrit.plugins.chatgpt.client.model;

import com.googlesource.gerrit.plugins.chatgpt.client.model.gerrit.GerritCodeRange;
import lombok.Data;

@Data
public class ReviewBatch {
    private String id;
    private String content;
    private String filename;
    private Integer line;
    private GerritCodeRange range;
}
