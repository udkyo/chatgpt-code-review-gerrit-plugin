package com.googlesource.gerrit.plugins.chatgpt.client.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class OutputFileDiff extends FileDiff {
    private List<DiffContent> content;

    public OutputFileDiff(Meta meta_a, Meta meta_b) {
        this.meta_a = meta_a;
        this.meta_b = meta_b;
    }
}
