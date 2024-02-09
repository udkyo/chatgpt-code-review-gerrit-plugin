package com.googlesource.gerrit.plugins.chatgpt.client.gerrit;

import com.googlesource.gerrit.plugins.chatgpt.client.UriResourceLocator;
import com.googlesource.gerrit.plugins.chatgpt.client.model.gerrit.GerritPatchSetDetail;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.List;

@Slf4j
public class GerritClientDetail extends GerritClientAccount {
    private final Integer gptAccountId;

    public GerritClientDetail(Configuration config, Integer gptAccountId) {
        super(config);
        this.gptAccountId = gptAccountId;
    }

    public GerritPatchSetDetail.PermittedVotingRange getPermittedVotingRange(String fullChangeId) {
        GerritPatchSetDetail gerritPatchSetDetail;
        try {
            gerritPatchSetDetail = getReviewDetail(fullChangeId);
        }
        catch (Exception e) {
            log.debug("Error retrieving PatchSet details", e);
            return null;
        }
        List<GerritPatchSetDetail.Permission> permissions = gerritPatchSetDetail.getLabels().getCodeReview().getAll();
        if (permissions == null) {
            log.debug("No limitations on the ChatGPT voting range were detected");
            return null;
        }
        for (GerritPatchSetDetail.Permission permission : permissions) {
            if (permission.getAccountId() == gptAccountId) {
                log.debug("PatchSet voting range detected for ChatGPT user: {}", permission.getPermittedVotingRange());
                return permission.getPermittedVotingRange();
            }
        }
        return null;
    }

    private GerritPatchSetDetail getReviewDetail(String fullChangeId) throws Exception {
        URI uri = URI.create(config.getGerritAuthBaseUrl()
                + UriResourceLocator.gerritGetPatchSetDetailUri(fullChangeId));
        String responseBody = forwardGetRequest(uri);
        return gson.fromJson(responseBody, GerritPatchSetDetail.class);
    }

}
