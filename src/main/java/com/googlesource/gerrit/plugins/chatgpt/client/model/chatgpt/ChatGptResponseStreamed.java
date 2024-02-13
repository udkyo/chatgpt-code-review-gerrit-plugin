package com.googlesource.gerrit.plugins.chatgpt.client.model.chatgpt;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class ChatGptResponseStreamed {
    private List<Choice> choices;

    @Data
    public static class Choice {
        protected ChatGptResponseMessage delta;
        protected int index;
        @SerializedName("finish_reason")
        protected String finishReason;
    }

}
