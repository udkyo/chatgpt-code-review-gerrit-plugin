package com.googlesource.gerrit.plugins.chatgpt.client.model.chatGpt;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class ChatGptResponseStreamed {
    private List<Choice> choices;


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
        private List<ChatGptToolCall> tool_calls;
    }

}
