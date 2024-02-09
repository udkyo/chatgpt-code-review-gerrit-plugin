package com.googlesource.gerrit.plugins.chatgpt;

import com.googlesource.gerrit.plugins.chatgpt.client.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.chatgpt.utils.SingletonManager;

import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Data
@Slf4j
public class DynamicSettings {
    @NonNull
    private Integer gptAccountId;
    private String gptRequestUserPrompt;
    private Integer commentPropertiesSize;
    @NonNull
    private Integer votingMinScore;
    @NonNull
    private Integer votingMaxScore;
    private Boolean forcedReview = false;
    private Boolean forcedReviewChangeSet = false;

    public static void destroy(GerritChange change) {
        log.debug("Destroying DynamicSettings instance for change: {}", change.getFullChangeId());
        SingletonManager.removeInstance(DynamicSettings.class, change.getFullChangeId());
    }

}
