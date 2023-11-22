package com.googlesource.gerrit.plugins.chatgpt.client.model;

import lombok.Data;


@Data
public class FileDiff {
    protected Meta meta_a;
    protected Meta meta_b;


    @Data
    public static class Meta {
        String name;
        String content_type;
    }
}
