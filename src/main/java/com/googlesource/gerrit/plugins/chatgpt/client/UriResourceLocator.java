package com.googlesource.gerrit.plugins.chatgpt.client;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;


public class UriResourceLocator {
    private static final String AUTH_PREFIX_URI = "/a";

    private UriResourceLocator() {
        throw new IllegalStateException("Utility class");
    }

    private static String gerritSetChangesUri(String fullChangeId, String resourcePath) {
        return AUTH_PREFIX_URI + "/changes/" + fullChangeId + resourcePath;
    }

    public static String gerritAccountsUri() {
        return AUTH_PREFIX_URI + "/accounts";
    }

    public static String gerritAccountIdUri(String userName) {
        return gerritAccountsUri() + "/?q=username:" + URLEncoder.encode(userName, StandardCharsets.UTF_8);
    }

    public static String gerritGroupPostfixUri(int accountId) {
        return "/" + accountId + "/groups";
    }

    public static String gerritDiffPostfixUri(String filename) {
        return "/" + URLEncoder.encode(filename, StandardCharsets.UTF_8) + "/diff";
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
