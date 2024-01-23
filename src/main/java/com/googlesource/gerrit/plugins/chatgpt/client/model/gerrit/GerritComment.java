package com.googlesource.gerrit.plugins.chatgpt.client.model.gerrit;

import lombok.Data;

@Data
public class GerritComment {
    private Author author;
    private String change_message_id;
    private Boolean unresolved;
    private Integer patch_set;
    private String id;
    private Integer line;
    private GerritCodeRange range;
    private String in_reply_to;
    private String updated;
    private String message;
    private String commit_id;
    // Metadata field that is set to the commented filename
    private String filename;

    @Data
    public static class Author {
        private int _account_id;
        private String name;
        private String display_name;
        private String email;
        private String username;
    }

}
