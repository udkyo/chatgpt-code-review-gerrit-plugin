package com.googlesource.gerrit.plugins.chatgpt.settings;

import com.googlesource.gerrit.plugins.chatgpt.client.gerrit.GerritChange;
import com.googlesource.gerrit.plugins.chatgpt.client.gerrit.GerritClient;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import com.googlesource.gerrit.plugins.chatgpt.model.gerrit.GerritComment;
import com.googlesource.gerrit.plugins.chatgpt.model.gerrit.GerritPermittedVotingRange;
import com.googlesource.gerrit.plugins.chatgpt.model.settings.Settings;
import com.googlesource.gerrit.plugins.chatgpt.utils.SingletonManager;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class DynamicSettings {

    public static Settings getNewInstance(Configuration config, GerritChange change, Integer gptAccountId) {
        return SingletonManager.getNewInstance(Settings.class, change,
                gptAccountId,
                config.getVotingMinScore(),
                config.getVotingMaxScore());
    }

    public static Settings getInstance(GerritChange change) {
        return SingletonManager.getInstance(Settings.class, change);
    }

    public static Settings getInstance(String changeId) {
        return SingletonManager.getInstance(Settings.class, changeId);
    }

    public static void update(Configuration config, GerritChange change, GerritClient gerritClient) {
        Settings settings = getInstance(change);

        List<GerritComment> commentProperties = gerritClient.getCommentProperties(change);
        settings.setCommentPropertiesSize(commentProperties.size());
        settings.setGptRequestUserPrompt(gerritClient.getUserRequests(change));
        if (config.isVotingEnabled() && !change.getIsCommentEvent()) {
            GerritPermittedVotingRange permittedVotingRange = gerritClient.getPermittedVotingRange(change);
            if (permittedVotingRange != null) {
                if (permittedVotingRange.getMin() > config.getVotingMinScore()) {
                    log.debug("Minimum ChatGPT voting score set to {}", permittedVotingRange.getMin());
                    settings.setVotingMinScore(permittedVotingRange.getMin());
                }
                if (permittedVotingRange.getMax() < config.getVotingMaxScore()) {
                    log.debug("Maximum ChatGPT voting score set to {}", permittedVotingRange.getMax());
                    settings.setVotingMaxScore(permittedVotingRange.getMax());
                }
            }
        }
    }

    public static void removeInstance(GerritChange change) {
        SingletonManager.removeInstance(Settings.class, change.getFullChangeId());
    }

}
