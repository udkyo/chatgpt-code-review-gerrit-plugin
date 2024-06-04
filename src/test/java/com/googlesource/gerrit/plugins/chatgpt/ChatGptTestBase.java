package com.googlesource.gerrit.plugins.chatgpt;

import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.client.api.gerrit.GerritChange;

public class ChatGptTestBase {
    protected static final Project.NameKey PROJECT_NAME = Project.NameKey.parse("myProject");
    protected static final Change.Key CHANGE_ID = Change.Key.parse("myChangeId");
    protected static final BranchNameKey BRANCH_NAME = BranchNameKey.create(PROJECT_NAME, "myBranchName");

    protected GerritChange getGerritChange() {
        return new GerritChange(ChatGptTestBase.PROJECT_NAME, ChatGptTestBase.BRANCH_NAME, ChatGptTestBase.CHANGE_ID);
    }
}
