package com.googlesource.gerrit.plugins.chatgpt.client;

import com.google.common.net.HttpHeaders;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Singleton;
import com.googlesource.gerrit.plugins.chatgpt.client.model.ChatCompletionBase;
import com.googlesource.gerrit.plugins.chatgpt.client.model.ChatCompletionRequest;
import com.googlesource.gerrit.plugins.chatgpt.client.model.ChatCompletionResponseStreamed;
import com.googlesource.gerrit.plugins.chatgpt.client.model.ChatCompletionResponseUnstreamed;
import com.googlesource.gerrit.plugins.chatgpt.client.model.ChatGptSuggestions;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import com.googlesource.gerrit.plugins.chatgpt.utils.FileUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Singleton
public class OpenAiClient {
    private static final int REVIEW_ATTEMPT_LIMIT = 3;
    @Getter
    private String requestBody;
    private final Gson gson = new GsonBuilder()
            .disableHtmlEscaping()
            .create();
    private final HttpClientWithRetry httpClientWithRetry = new HttpClientWithRetry();
    private boolean isCommentEvent = false;

    public String ask(Configuration config, String changeId, String patchSet) throws Exception {
        for (int attemptInd = 0; attemptInd < REVIEW_ATTEMPT_LIMIT; attemptInd++) {
            HttpRequest request = createRequest(config, patchSet);
            log.debug("ChatGPT request: {}", request.toString());

            HttpResponse<String> response = httpClientWithRetry.execute(request);

            String body = response.body();
            log.debug("body: {}", body);
            if (body == null) {
                throw new IOException("ChatGPT response body is null");
            }

            String contentExtracted = extractContent(config, body);
            if (validateResponse(contentExtracted, changeId, attemptInd)) {
                return contentExtracted;
            }
        }
        throw new RuntimeException("Failed to receive valid ChatGPT response");
    }

    public String ask(Configuration config, String changeId, String patchSet, boolean isCommentEvent) throws Exception {
        this.isCommentEvent = isCommentEvent;

        return this.ask(config, changeId, patchSet);
    }

    public String extractContent(Configuration config, String body) throws Exception {
        if (config.getGptStreamOutput() && !isCommentEvent) {
            StringBuilder finalContent = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new StringReader(body))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    extractContentFromLine(line).ifPresent(finalContent::append);
                }
            }
            return finalContent.toString();
        }
        else {
            ChatCompletionResponseUnstreamed chatCompletionResponseUnstreamed =
                    gson.fromJson(body, ChatCompletionResponseUnstreamed.class);
            return getResponseContent(chatCompletionResponseUnstreamed.getChoices().get(0).getMessage().getTool_calls());
        }
    }

    private boolean validateResponse(String contentExtracted, String changeId, int attemptInd) {
        ChatGptSuggestions chatGptSuggestions = gson.fromJson(contentExtracted, ChatGptSuggestions.class);
        String returnedChangeId = chatGptSuggestions.getChangeId();
        // A response is considered valid if either no changeId is returned or the changeId returned matches the one
        // provided in the request
        boolean isValidated = returnedChangeId == null || changeId.equals(returnedChangeId);
        if (!isValidated) {
            log.error("ChangedId mismatch error (attempt #{}).\nExpected value: {}\nReturned value: {}", attemptInd,
                    changeId, returnedChangeId);
        }
        return isValidated;
    }

    private String getResponseContent(List<ChatCompletionBase.ToolCall> toolCalls) {
        return toolCalls.get(0).getFunction().getArguments();
    }

    private HttpRequest createRequest(Configuration config, String patchSet) {
        URI uri = URI.create(URI.create(config.getGptDomain()) + UriResourceLocator.chatCompletionsUri());
        log.debug("ChatGPT request URI: {}", uri);
        requestBody = createRequestBody(config, patchSet);
        log.debug("ChatGPT request body: {}", requestBody);

        return HttpRequest.newBuilder()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.getGptToken())
                .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())
                .uri(uri)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
    }

    private String createRequestBody(Configuration config, String patchSet) {
        ChatCompletionRequest.Message systemMessage = ChatCompletionRequest.Message.builder()
                .role("system")
                .content(config.getGptSystemPrompt())
                .build();
        ChatCompletionRequest.Message userMessage = ChatCompletionRequest.Message.builder()
                .role("user")
                .content(config.getGptUserPrompt(patchSet))
                .build();

        List<ChatCompletionRequest.Message> messages = List.of(systemMessage, userMessage);

        ChatCompletionRequest tools;
        try (InputStreamReader reader = FileUtils.getInputStreamReader("Config/tools.json")) {
            tools = gson.fromJson(reader, ChatCompletionRequest.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load ChatGPT request tools", e);
        }

        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                .model(config.getGptModel())
                .messages(messages)
                .temperature(config.getGptTemperature())
                .stream(config.getGptStreamOutput() && !isCommentEvent)
                // Seed value is Utilized to prevent ChatGPT from mixing up separate API calls that occur in close
                // temporal proximity.
                .seed(ThreadLocalRandom.current().nextInt())
                .tools(tools.getTools())
                .tool_choice(tools.getTool_choice())
                .build();

        return gson.toJson(chatCompletionRequest);
    }

    private Optional<String> extractContentFromLine(String line) {
        String dataPrefix = "data: {\"id\"";

        if (!line.startsWith(dataPrefix)) {
            return Optional.empty();
        }
        ChatCompletionResponseStreamed chatCompletionResponseStreamed =
                gson.fromJson(line.substring("data: ".length()), ChatCompletionResponseStreamed.class);
        ChatCompletionBase.Delta delta = chatCompletionResponseStreamed.getChoices().get(0).getDelta();
        if (delta == null || delta.getTool_calls() == null) {
            return Optional.empty();
        }
        String content = getResponseContent(delta.getTool_calls());
        return Optional.ofNullable(content);
    }

}
