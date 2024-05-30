package com.googlesource.gerrit.plugins.chatgpt.mode.common.client.prompt;

import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import com.googlesource.gerrit.plugins.chatgpt.localization.Localizer;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.model.api.chatgpt.ChatGptRequestMessage;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.model.api.gerrit.GerritComment;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.model.data.ChangeSetData;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.model.data.CommentData;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.model.data.GerritClientData;
import com.googlesource.gerrit.plugins.chatgpt.settings.Settings;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class ChatGptHistory extends ChatGptComment {
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";

    private final Set<String> messagesExcludedFromHistory;
    private final HashMap<String, GerritComment> commentMap;
    private final HashMap<String, GerritComment> patchSetCommentMap;
    private final Set<String> patchSetCommentAdded;
    private final List<GerritComment> patchSetComments;
    private final int revisionBase;

    private boolean filterActive;

    public ChatGptHistory(
            Configuration config,
            ChangeSetData changeSetData,
            GerritClientData gerritClientData,
            Localizer localizer
    ) {
        super(config, changeSetData, localizer);
        CommentData commentData = gerritClientData.getCommentData();
        messagesExcludedFromHistory = Set.of(
            Settings.GERRIT_DEFAULT_MESSAGE_DONE,
            localizer.getText("message.empty.review")
        );
        commentMap = commentData.getCommentMap();
        patchSetCommentMap = commentData.getPatchSetCommentMap();
        patchSetComments = retrievePatchSetComments(gerritClientData);
        revisionBase = gerritClientData.getOneBasedRevisionBase();
        patchSetCommentAdded = new HashSet<>();
    }

    public List<ChatGptRequestMessage> retrieveHistory(GerritComment commentProperty, boolean filterActive) {
        this.filterActive = filterActive;
        if (commentProperty.isPatchSetComment()) {
            return retrievePatchSetMessageHistory();
        }
        else {
            return retrieveMessageHistory(commentProperty);
        }
    }

    public List<ChatGptRequestMessage> retrieveHistory(GerritComment commentProperty) {
        return retrieveHistory(commentProperty, false);
    }

    private List<GerritComment> retrievePatchSetComments(GerritClientData gerritClientData) {
        List<GerritComment> detailComments = gerritClientData.getDetailComments();
        // Normalize detailComments by setting the `update` field to match `date`
        detailComments.forEach(record -> record.setUpdated(record.getDate()));
        // Join the comments from patchSetCommentMap with detailComments
        List<GerritComment> patchSetComments = Stream.concat(patchSetCommentMap.values().stream(),
                        detailComments.stream())
                .collect(Collectors.toList());
        sortPatchSetComments(patchSetComments);
        log.debug("Patch Set Comments sorted by `update` datetime: {}", patchSetComments);

        return patchSetComments;
    }

    private void sortPatchSetComments(List<GerritComment> patchSetComments) {
        Comparator<GerritComment> byDateUpdated = (GerritComment o1, GerritComment o2) -> {
            String dateTime1 = o1.getUpdated();
            String dateTime2 = o2.getUpdated();
            if (dateTime1 == null && dateTime2 == null) return 0;
            if (dateTime1 == null) return 1;
            if (dateTime2 == null) return -1;

            return dateTime1.compareTo(dateTime2);
        };
        patchSetComments.sort(byDateUpdated);
    }

    private String getRoleFromComment(GerritComment currentComment) {
        return isFromAssistant(currentComment) ? ROLE_ASSISTANT : ROLE_USER;
    }

    private List<ChatGptRequestMessage> retrieveMessageHistory(GerritComment currentComment) {
        List<ChatGptRequestMessage> messageHistory = new ArrayList<>();
        while (currentComment != null) {
            addMessageToHistory(messageHistory, currentComment);
            currentComment = commentMap.get(currentComment.getInReplyTo());
        }
        // Reverse the history sequence so that the oldest message appears first and the newest message is last
        Collections.reverse(messageHistory);

        return messageHistory;
    }

    private List<ChatGptRequestMessage> retrievePatchSetMessageHistory() {
        List<ChatGptRequestMessage> messageHistory = new ArrayList<>();
        for (GerritComment patchSetComment : patchSetComments) {
            if (patchSetComment.isAutogenerated()) {
                continue;
            }
            if (!isFromAssistant(patchSetComment)) {
                GerritComment patchSetLevelMessage = patchSetCommentMap.get(patchSetComment.getId());
                if (patchSetLevelMessage != null) {
                    patchSetComment = patchSetLevelMessage;
                }
            }
            addMessageToHistory(messageHistory, patchSetComment);
        }
        return messageHistory;
    }

    private boolean isInactiveComment(GerritComment comment) {
        return config.getIgnoreResolvedChatGptComments() && isFromAssistant(comment) && comment.isResolved() ||
                config.getIgnoreOutdatedInlineComments() && comment.getOneBasedPatchSet() != revisionBase &&
                        !comment.isPatchSetComment();
    }

    private void addMessageToHistory(List<ChatGptRequestMessage> messageHistory, GerritComment comment) {
        String messageContent = getCleanedMessage(comment);
        boolean shouldNotProcessComment = messageContent.isEmpty() ||
                messagesExcludedFromHistory.contains(messageContent) ||
                patchSetCommentAdded.contains(messageContent) ||
                filterActive && isInactiveComment(comment);

        if (shouldNotProcessComment && !commentMessage.isContainingHistoryCommand()) {
            return;
        }
        patchSetCommentAdded.add(messageContent);
        if (commentMessage.isContainingHistoryCommand()) {
            commentMessage.processHistoryCommand();
            return;
        }
        ChatGptRequestMessage message = ChatGptRequestMessage.builder()
                .role(getRoleFromComment(comment))
                .content(messageContent)
                .build();
        messageHistory.add(message);
    }

}
