package com.googlesource.gerrit.plugins.chatgpt.client.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public abstract class FileDiff {
    @SerializedName("meta_a")
    protected Meta metaA;
    @SerializedName("meta_b")
    protected Meta metaB;

    @Data
    public static class Meta {
        String name;
        @SerializedName("content_type")
        String contentType;
    }

}
