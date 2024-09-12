package com.googlesource.gerrit.plugins.chatgpt.mode.common.client.messages;

import com.googlesource.gerrit.plugins.chatgpt.localization.Localizer;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.model.api.chatgpt.ChatGptReplyItem;

import java.util.List;

import static com.googlesource.gerrit.plugins.chatgpt.utils.TextUtils.prettyStringifyObject;

public class DebugCodeBlocksReview extends DebugCodeBlocks {
    private static final String HIDDEN_REPLY = "hidden: %s";

    public DebugCodeBlocksReview(Localizer localizer) {
        super(localizer.getText("message.debugging.review.title"));
    }

    public String getDebugCodeBlock(ChatGptReplyItem replyItem, boolean isHidden) {
        return super.getDebugCodeBlock(List.of(
                String.format(HIDDEN_REPLY, isHidden),
                prettyStringifyObject(replyItem)
        ));
    }
}
