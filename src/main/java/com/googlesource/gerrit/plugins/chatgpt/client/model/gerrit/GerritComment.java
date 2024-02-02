package com.googlesource.gerrit.plugins.chatgpt.client.model.gerrit;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class GerritComment {
    private Author author;
    @SerializedName("change_message_id")
    private String changeMessageId;
    private Boolean unresolved;
    @SerializedName("patch_set")
    private Integer patchSet;
    private String id;
    private Integer line;
    private GerritCodeRange range;
    @SerializedName("in_reply_to")
    private String inReplyTo;
    private String updated;
    private String message;
    @SerializedName("commit_id")
    private String commitId;
    // Metadata field that is set to the commented filename
    private String filename;

    @Data
    public static class Author {
        @SerializedName("_account_id")
        private int accountId;
        private String name;
        @SerializedName("display_name")
        private String displayName;
        private String email;
        private String username;
    }

}
