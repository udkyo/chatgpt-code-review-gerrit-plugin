package com.googlesource.gerrit.plugins.chatgpt.client;

public class UriResourceLocator {

    private UriResourceLocator() {
        throw new IllegalStateException("Utility class");
    }

    private static String gerritAuthPrefixUri() {
        return "/a";
    }

    public static String gerritDiffPostfixUri(String filename) {
        return "/" + filename + "/diff";
    }

    public static String gerritPatchSetFilesUri(String fullChangeId) {
        return "/changes/" + fullChangeId + "/revisions/current/files";
    }

    public static String gerritCommentUri(String fullChangeId) {
        return gerritAuthPrefixUri() + "/changes/" + fullChangeId + "/revisions/current/review";
    }

    public static String chatCompletionsUri() {
        return "/v1/chat/completions";
    }

}
