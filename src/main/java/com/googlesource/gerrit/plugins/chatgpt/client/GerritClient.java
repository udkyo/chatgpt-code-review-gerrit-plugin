package com.googlesource.gerrit.plugins.chatgpt.client;

import com.google.common.net.HttpHeaders;
import com.google.gson.Gson;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.lang.reflect.Type;

import static java.net.HttpURLConnection.HTTP_OK;

@Slf4j
@Singleton
public class GerritClient {
    private final Gson gson = new Gson();
    private final HttpClientWithRetry httpClientWithRetry = new HttpClientWithRetry();

    private List<String> getAffectedFiles(Configuration config, String fullChangeId) throws Exception {
        URI uri = URI.create(config.getGerritAuthBaseUrl()
                        + UriResourceLocator.gerritPatchSetFilesUri(fullChangeId));
        log.debug("patchSet uri: {}", uri.toString());
        HttpRequest request = HttpRequest.newBuilder()
                .header(HttpHeaders.AUTHORIZATION, generateBasicAuth(config.getGerritUserName(),
                        config.getGerritPassword()))
                .uri(uri)
                .build();

        HttpResponse<String> response = httpClientWithRetry.execute(request);

        if (response.statusCode() != HTTP_OK) {
            log.error("Failed to get patch. Response: {}", response);
            throw new IOException("Failed to get patch from Gerrit");
        }

        String responseBody = response.body();
        log.info("Successfully obtained patch. Decoding response body.");

        Type listType = new TypeToken<Map<String, Map<String, String>>>() {}.getType();
        Map<String, Map<String, String>> map = gson.fromJson(responseBody, listType);
        List<String> files = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> file : map.entrySet()) {
            String filename = file.getKey();
            if (!filename.equals("/COMMIT_MSG") || config.getGptReviewCommitMessages()) {
                Integer size = Integer.valueOf(file.getValue().get("size"));
                if (size > config.getMaxReviewFileSize()) {
                    log.info("File '{}' not reviewed because its size exceeds the fixed maximum allowable size.",
                            filename);
                }
                else {
                    files.add(filename);
                }
            }
        }

        return files;
    }

    private String getFileDiffs(Configuration config, String fullChangeId, List<String> files) throws Exception {
        List<String> diffs = new ArrayList<>();
        for (String filename : files) {
            URI uri = URI.create(config.getGerritAuthBaseUrl()
                    + UriResourceLocator.gerritPatchSetFilesUri(fullChangeId)
                    + UriResourceLocator.gerritDiffPostfixUri(filename));
            log.debug("Diff uri: {}", uri.toString());
            HttpRequest request = HttpRequest.newBuilder()
                    .header(HttpHeaders.AUTHORIZATION, generateBasicAuth(config.getGerritUserName(),
                            config.getGerritPassword()))
                    .uri(uri)
                    .build();

            HttpResponse<String> response = httpClientWithRetry.execute(request);

            if (response.statusCode() != HTTP_OK) {
                log.error("Failed to get patch. Response: {}", response);
                throw new IOException("Failed to get patch from Gerrit");
            }

            diffs.add(response.body().replaceAll("^[')\\]}']+", ""));
        }
        return "[" + String.join(",", diffs) + "]";
    }

    public String getPatchSet(Configuration config, String fullChangeId) throws Exception {
        List<String> files = getAffectedFiles(config, fullChangeId);
        log.debug("Patch files: {}", files.toString());

        String fileDiffs = getFileDiffs(config, fullChangeId, files);
        log.debug("File diffs: {}", fileDiffs);

        return fileDiffs;
    }

    private String generateBasicAuth(String username, String password) {
        String auth = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    }

    public void postComment(Configuration config, String fullChangeId, String message) throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("message", message);
        String json = gson.toJson(map);

        URI uri = URI.create(config.getGerritAuthBaseUrl()
                        + UriResourceLocator.gerritCommentUri(fullChangeId));
        String auth = generateBasicAuth(config.getGerritUserName(),
                        config.getGerritPassword());
        log.debug("postComment uri: {}", uri);
        log.debug("postComment auth: {}", auth);
        log.debug("postComment json: {}", json);
        HttpRequest request = HttpRequest.newBuilder()
                .header(HttpHeaders.AUTHORIZATION, auth)
                .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())
                .uri(uri)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClientWithRetry.execute(request);

        if (response.statusCode() != HTTP_OK) {
            log.error("Review post failed with status code: {}", response.statusCode());
        }
    }

}
