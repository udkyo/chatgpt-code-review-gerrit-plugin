package com.googlesource.gerrit.plugins.chatgpt.client.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class OutputFileDiff extends FileDiff {
    private List<DiffContent> content;

    public OutputFileDiff(Meta metaA, Meta metaB) {
        this.metaA = metaA;
        this.metaB = metaB;
    }
}
