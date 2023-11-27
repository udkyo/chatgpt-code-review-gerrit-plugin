package com.googlesource.gerrit.plugins.chatgpt.client;

import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class GerritClientAccount extends GerritClientBase {

    public GerritClientAccount(Configuration config) {
        super(config);
    }

    public boolean isDisabledUser(String authorUsername) {
        List<String> enabledUsers = config.getEnabledUsers();
        List<String> disabledUsers = config.getDisabledUsers();
        return !enabledUsers.contains(Configuration.ENABLED_USERS_ALL)
                && !enabledUsers.contains(authorUsername)
                || disabledUsers.contains(authorUsername);
    }

    public boolean isDisabledTopic(String topic) {
        List<String> enabledTopicFilter = config.getEnabledTopicFilter();
        List<String> disabledTopicFilter = config.getDisabledTopicFilter();
        return !enabledTopicFilter.contains(Configuration.ENABLED_TOPICS_ALL)
                && enabledTopicFilter.stream().noneMatch(topic::contains)
                || !topic.isEmpty() && disabledTopicFilter.stream().anyMatch(topic::contains);
    }

}
