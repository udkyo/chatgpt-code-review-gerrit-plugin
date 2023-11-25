package com.googlesource.gerrit.plugins.chatgpt.client;

import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;


public class UriResourceLocator {

    private UriResourceLocator() {
        throw new IllegalStateException("Utility class");
    }

    private static String gerritSetChangesUri(String fullChangeId, String resourcePath) {
        return "/a/changes/" + fullChangeId + resourcePath;
    }

    public static String gerritDiffPostfixUri(String filename) {
        try {
            return "/" + URLEncoder.encode(filename, "UTF-8") + "/diff";
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    public static String gerritPatchSetFilesUri(String fullChangeId) {
        return gerritSetChangesUri(fullChangeId, "/revisions/current/files");
    }

    public static String gerritGetAllPatchSetCommentsUri(String fullChangeId) {
        return gerritSetChangesUri(fullChangeId, "/comments");
    }

    public static String gerritCommentUri(String fullChangeId) {
        return gerritSetChangesUri(fullChangeId, "/revisions/current/review");
    }

    public static String chatCompletionsUri() {
        return "/v1/chat/completions";
    }

}
