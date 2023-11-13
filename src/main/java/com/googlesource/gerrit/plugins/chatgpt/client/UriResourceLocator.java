package com.googlesource.gerrit.plugins.chatgpt.client;

import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;


public class UriResourceLocator {

    private UriResourceLocator() {
        throw new IllegalStateException("Utility class");
    }

    private static String gerritAuthPrefixUri() {
        return "/a";
    }

    public static String gerritDiffPostfixUri(String filename) {
        try {
            return "/" + URLEncoder.encode(filename, "UTF-8") + "/diff";
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    public static String gerritPatchSetFilesUri(String fullChangeId) {
        return "/changes/" + fullChangeId + "/revisions/current/files";
    }

    public static String gerritGetAllPatchSetCommentsUri(String fullChangeId) {
        return "/changes/" + fullChangeId + "/comments";
    }

    public static String gerritCommentUri(String fullChangeId) {
        return gerritAuthPrefixUri() + "/changes/" + fullChangeId + "/revisions/current/review";
    }

    public static String chatCompletionsUri() {
        return "/v1/chat/completions";
    }

}
