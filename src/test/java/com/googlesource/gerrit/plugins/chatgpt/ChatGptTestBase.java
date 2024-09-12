package com.googlesource.gerrit.plugins.chatgpt;

import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.client.api.gerrit.GerritChange;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;

import java.nio.file.Path;

import static org.mockito.Mockito.when;

public class ChatGptTestBase {
    protected static final Project.NameKey PROJECT_NAME = Project.NameKey.parse("myProject");
    protected static final Change.Key CHANGE_ID = Change.Key.parse("myChangeId");
    protected static final BranchNameKey BRANCH_NAME = BranchNameKey.create(PROJECT_NAME, "myBranchName");

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Mock
    protected Path mockPluginDataPath;

    protected Path realPluginDataPath;

    protected void setupPluginData() {
        realPluginDataPath = tempFolder.getRoot().toPath().resolve("global.data");
        Path realProjectDataPath = tempFolder.getRoot().toPath().resolve(PROJECT_NAME + ".data");

        // Mock the PluginData annotation project behavior
        when(mockPluginDataPath.resolve(PROJECT_NAME + ".data")).thenReturn(realProjectDataPath);
    }

    protected GerritChange getGerritChange() {
        return new GerritChange(ChatGptTestBase.PROJECT_NAME, ChatGptTestBase.BRANCH_NAME, ChatGptTestBase.CHANGE_ID);
    }
}
