package com.googlesource.gerrit.plugins.chatgpt.mode.common.client.http;

import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.Map;

import static com.googlesource.gerrit.plugins.chatgpt.utils.GsonUtils.getGson;

@Slf4j
public class HttpClient {
    private final OkHttpClient client = new OkHttpClient();

    public String execute(Request request) {
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
            log.debug("HttpClient Response body: {}", response.body());
            if (response.body() != null) {
                return response.body().string();
            }
            else {
                log.error("Request {} returned an empty string", request);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public Request createRequest(String uri, String bearer, RequestBody body, Map<String, String> additionalHeaders) {
        // If body is null, a GET request is initiated. Otherwise, a POST request is sent with the specified body.
        Request.Builder builder = new Request.Builder()
                .url(uri)
                .header("Authorization", "Bearer " + bearer);

        if (body != null) {
            builder.post(body);
        }
        else {
            builder.get();
        }
        if (additionalHeaders != null) {
            for (Map.Entry<String, String> header : additionalHeaders.entrySet()) {
                builder.header(header.getKey(), header.getValue());
            }
        }
        return builder.build();
    }

    public Request createRequestFromJson(String uri, String bearer, Object requestObject,
                                         Map<String, String> additionalHeaders) {
        if (requestObject != null) {
            String bodyJson = getGson().toJson(requestObject);
            log.debug("Request body: {}", bodyJson);
            RequestBody body = RequestBody.create(bodyJson, MediaType.get("application/json"));

            return createRequest(uri, bearer, body, additionalHeaders);
        }
        else {
            return createRequest(uri, bearer, null, additionalHeaders);
        }
    }
}
