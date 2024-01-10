package com.googlesource.gerrit.plugins.chatgpt.client;

import com.google.common.net.HttpHeaders;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static java.net.HttpURLConnection.HTTP_OK;


@Slf4j
public class GerritClientBase {
    protected final Gson gson = new Gson();
    protected final HttpClientWithRetry httpClientWithRetry = new HttpClientWithRetry();
    @Getter
    protected HashMap<String, FileDiffProcessed> fileDiffsProcessed = new HashMap<>();
    protected Configuration config;

    public GerritClientBase(Configuration config) {
        this.config = config;
        config.resetDynamicConfiguration();
    }

    protected String generateBasicAuth(String username, String password) {
        String auth = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    }

    protected String forwardGetRequest(URI uri) throws Exception {
        log.debug("Uri: {}", uri);
        HttpRequest request = HttpRequest.newBuilder()
                .header(HttpHeaders.AUTHORIZATION, generateBasicAuth(config.getGerritUserName(),
                        config.getGerritPassword()))
                .uri(uri)
                .build();

        HttpResponse<String> response = httpClientWithRetry.execute(request);

        if (response.statusCode() != HTTP_OK) {
            log.error("Failed to get response from Gerrit. Response: {}", response);
            throw new IOException("Failed to get response from Gerrit");
        }
        log.debug("Successfully obtained response from Gerrit.");

        return response.body();
    }

    protected JsonArray forwardGetRequestReturnJsonArray(URI uri) throws Exception {
        String responseBody = forwardGetRequest(uri);
        return gson.fromJson(responseBody, JsonArray.class);
    }

    protected JsonObject forwardGetRequestReturnJsonObject(URI uri) throws Exception {
        String responseBody = forwardGetRequest(uri);
        return gson.fromJson(responseBody, JsonObject.class);
    }

}
