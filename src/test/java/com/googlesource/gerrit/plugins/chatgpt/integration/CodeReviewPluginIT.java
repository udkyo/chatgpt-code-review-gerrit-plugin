package com.googlesource.gerrit.plugins.chatgpt.integration;

import com.googlesource.gerrit.plugins.chatgpt.client.gerrit.GerritClient;
import com.googlesource.gerrit.plugins.chatgpt.client.chatgpt.ChatGptClient;
import com.googlesource.gerrit.plugins.chatgpt.client.prompt.ChatGptPrompt;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import com.googlesource.gerrit.plugins.chatgpt.model.review.ReviewBatch;
import lombok.extern.slf4j.Slf4j;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

import static junit.framework.TestCase.assertNotNull;
import static org.mockito.Mockito.when;

@Ignore("This test suite is designed to demonstrate how to test the Gerrit and GPT interfaces in a real environment. " +
        "It is not intended to be executed during the regular build process")
@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class CodeReviewPluginIT {
    @Mock
    private Configuration config;

    @InjectMocks
    private GerritClient gerritClient;

    @InjectMocks
    private ChatGptClient chatGptClient;

    @Test
    public void sayHelloToGPT() throws Exception {
        ChatGptPrompt chatGptPrompt = new ChatGptPrompt(config);
        when(config.getGptDomain()).thenReturn(Configuration.OPENAI_DOMAIN);
        when(config.getGptToken()).thenReturn("Your GPT token");
        when(config.getGptModel()).thenReturn(Configuration.DEFAULT_GPT_MODEL);
        when(chatGptPrompt.getGptSystemPrompt()).thenReturn(ChatGptPrompt.DEFAULT_GPT_SYSTEM_PROMPT);

        String answer = chatGptClient.ask(config, "", "hello");
        log.info("answer: {}", answer);
        assertNotNull(answer);
    }

    @Test
    public void getPatchSet() throws Exception {
        when(config.getGerritAuthBaseUrl()).thenReturn("Your Gerrit URL");
        when(config.getGerritUserName()).thenReturn("Your Gerrit username");
        when(config.getGerritPassword()).thenReturn("Your Gerrit password");

        gerritClient.initialize(config);
        String patchSet = gerritClient.getPatchSet("${changeId}");
        log.info("patchSet: {}", patchSet);
        assertNotNull(patchSet);
    }

    @Test
    public void setReview() throws Exception {
        when(config.getGerritAuthBaseUrl()).thenReturn("Your Gerrit URL");
        when(config.getGerritUserName()).thenReturn("Your Gerrit username");
        when(config.getGerritPassword()).thenReturn("Your Gerrit password");

        List<ReviewBatch> reviewBatches = new ArrayList<>();
        reviewBatches.add(new ReviewBatch());
        reviewBatches.get(0).setContent("message");

        gerritClient.initialize(config);
        gerritClient.setReview("Your changeId", reviewBatches);
    }
}
