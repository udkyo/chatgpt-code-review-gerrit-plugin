package com.googlesource.gerrit.plugins.chatgpt.client;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class ChatCompletionBase {

    protected String id;
    protected String object;
    protected long created;
    protected String model;


    @Data
    public static class Choice {
        protected Delta delta;
        protected int index;
        @SerializedName("finish_reason")
        protected String finishReason;
    }

    @Data
    public static class Delta {
        private String role;
        private String content;
    }
}
