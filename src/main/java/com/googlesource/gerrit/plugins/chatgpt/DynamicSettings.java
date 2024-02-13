package com.googlesource.gerrit.plugins.chatgpt;

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

    public Boolean getForcedReviewLastPatchSet() {
        return forcedReview && !forcedReviewChangeSet;
    }

}
