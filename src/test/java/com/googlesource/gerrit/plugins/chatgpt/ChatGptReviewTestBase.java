package com.googlesource.gerrit.plugins.chatgpt;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.accounts.AccountApi;
import com.google.gerrit.extensions.api.accounts.Accounts;
import com.google.gerrit.extensions.api.changes.*;
import com.google.gerrit.extensions.api.changes.ChangeApi.CommentsRequest;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.json.OutputFormat;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.data.AccountAttribute;
import com.google.gerrit.server.data.PatchSetAttribute;
import com.google.gerrit.server.events.*;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Providers;
import com.googlesource.gerrit.plugins.chatgpt.config.ConfigCreator;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import com.googlesource.gerrit.plugins.chatgpt.data.PluginDataHandler;
import com.googlesource.gerrit.plugins.chatgpt.data.PluginDataHandlerProvider;
import com.googlesource.gerrit.plugins.chatgpt.interfaces.mode.common.client.api.chatgpt.IChatGptClient;
import com.googlesource.gerrit.plugins.chatgpt.interfaces.mode.common.client.api.gerrit.IGerritClientPatchSet;
import com.googlesource.gerrit.plugins.chatgpt.listener.EventHandlerTask;
import com.googlesource.gerrit.plugins.chatgpt.localization.Localizer;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.client.api.gerrit.GerritClient;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.client.api.gerrit.GerritClientComments;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.client.api.gerrit.GerritClientFacade;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.client.api.gerrit.GerritClientReview;
import com.googlesource.gerrit.plugins.chatgpt.mode.common.model.data.ChangeSetData;
import com.googlesource.gerrit.plugins.chatgpt.mode.stateful.client.api.chatgpt.ChatGptClientStateful;
import com.googlesource.gerrit.plugins.chatgpt.mode.stateful.client.api.gerrit.GerritClientPatchSetStateful;
import com.googlesource.gerrit.plugins.chatgpt.mode.stateful.client.api.git.GitRepoFiles;
import com.googlesource.gerrit.plugins.chatgpt.mode.stateless.client.api.chatgpt.ChatGptClientStateless;
import com.googlesource.gerrit.plugins.chatgpt.mode.stateless.client.api.gerrit.GerritClientPatchSetStateless;
import lombok.NonNull;
import org.junit.Before;
import org.junit.Rule;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.function.Consumer;

import static com.google.gerrit.extensions.client.ChangeKind.REWORK;
import static com.googlesource.gerrit.plugins.chatgpt.listener.EventHandlerTask.EVENT_CLASS_MAP;
import static com.googlesource.gerrit.plugins.chatgpt.utils.GsonUtils.getGson;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ChatGptReviewTestBase extends ChatGptTestBase {
    protected static final Path basePath = Paths.get("src/test/resources");
    protected static final int GERRIT_GPT_ACCOUNT_ID = 1000000;
    protected static final String GERRIT_GPT_USERNAME = "gpt";
    protected static final int GERRIT_USER_ACCOUNT_ID = 1000001;
    protected static final String GERRIT_USER_ACCOUNT_NAME = "Test";
    protected static final String GERRIT_USER_ACCOUNT_EMAIL = "test@example.com";
    protected static final String GERRIT_USER_USERNAME = "test";
    protected static final String GERRIT_USER_GROUP = "Test";
    protected static final String GPT_TOKEN = "tk-test";
    protected static final String GPT_DOMAIN = "http://localhost:9527";
    protected static final boolean GPT_STREAM_OUTPUT = true;
    protected static final long TEST_TIMESTAMP = 1699270812;
    private static  final int GPT_USER_ACCOUNT_ID = 1000000;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(9527);

    @Mock
    protected GitRepoFiles gitRepoFiles;

    @Mock
    protected PluginDataHandlerProvider pluginDataHandlerProvider;

    @Mock
    protected PluginDataHandler pluginDataHandler;

    @Mock
    protected OneOffRequestContext context;
    @Mock
    protected GerritApi gerritApi;
    @Mock
    protected Changes changesMock;
    @Mock
    protected ChangeApi changeApiMock;
    @Mock
    protected RevisionApi revisionApiMock;
    @Mock
    protected ReviewResult reviewResult;
    @Mock
    protected CommentsRequest commentsRequestMock;
    @Mock
    protected AccountCache accountCacheMock;

    protected PluginConfig globalConfig;
    protected PluginConfig projectConfig;
    protected Configuration config;
    protected ChangeSetData changeSetData;
    protected GerritClient gerritClient;
    protected PatchSetReviewer patchSetReviewer;
    protected ConfigCreator mockConfigCreator;
    protected JsonObject gptRequestBody;
    protected String promptTagComments;

    @Before
    public void before() throws RestApiException {
        initGlobalAndProjectConfig();
        initConfig();
        setupMockRequests();
        initComparisonContent();
        initTest();
    }

    protected void initGlobalAndProjectConfig() {
        globalConfig = mock(PluginConfig.class);
        Answer<Object> returnDefaultArgument = invocation -> {
            // Return the second argument (i.e., the Default value) passed to the method
            return invocation.getArgument(1);
        };

        // Mock the Global Config values not provided by Default
        when(globalConfig.getString("gptToken")).thenReturn(GPT_TOKEN);

        // Mock the Global Config values to the Defaults passed as second arguments of the `get*` methods.
        when(globalConfig.getString(Mockito.anyString(), Mockito.anyString())).thenAnswer(returnDefaultArgument);
        when(globalConfig.getInt(Mockito.anyString(), Mockito.anyInt())).thenAnswer(returnDefaultArgument);
        when(globalConfig.getBoolean(Mockito.anyString(), Mockito.anyBoolean())).thenAnswer(returnDefaultArgument);

        // Mock the Global Config values that differ from the ones provided by Default
        when(globalConfig.getString(Mockito.eq("gptDomain"), Mockito.anyString()))
                .thenReturn(GPT_DOMAIN);
        when(globalConfig.getString("gerritUserName")).thenReturn(GERRIT_GPT_USERNAME);

        projectConfig = mock(PluginConfig.class);

        // Mock the Project Config values
        when(projectConfig.getBoolean(Mockito.eq("isEnabled"), Mockito.anyBoolean())).thenReturn(true);
    }

    protected void initConfig() {
        config = new Configuration(
                context,
                gerritApi,
                globalConfig,
                projectConfig,
                "gpt@email.com",
                Account.id(1000000)
        );
    }

    protected void setupMockRequests() throws RestApiException {
        Accounts accountsMock = mockGerritAccountsRestEndpoint();
        // Mock the behavior of the gerritAccountIdUri request
        mockGerritAccountsQueryApiCall(GERRIT_GPT_USERNAME, GERRIT_GPT_ACCOUNT_ID);

        // Mock the behavior of the gerritAccountIdUri request
        mockGerritAccountsQueryApiCall(GERRIT_USER_USERNAME, GERRIT_USER_ACCOUNT_ID);

        // Mock the behavior of the gerritAccountGroups request
        mockGerritAccountGroupsApiCall(accountsMock, GERRIT_USER_ACCOUNT_ID);

        mockGerritChangeApiRestEndpoint();

        // Mock the behavior of the gerritGetPatchSetDetailUri request
        mockGerritChangeDetailsApiCall();

        // Mock the behavior of the gerritPatchSet comments request
        mockGerritChangeCommentsApiCall();

        // Mock the behavior of the gerrit Review request
        mockGerritReviewApiCall();

        // Mock the GerritApi's revision API
        when(changeApiMock.current()).thenReturn(revisionApiMock);

        // Mock the pluginDataHandlerProvider to return the mocked Change pluginDataHandler
        when(pluginDataHandlerProvider.getChangeScope()).thenReturn(pluginDataHandler);
    }

    private Accounts mockGerritAccountsRestEndpoint() {
        Accounts accountsMock = mock(Accounts.class);
        when(gerritApi.accounts()).thenReturn(accountsMock);
        return accountsMock;
    }

    private void mockGerritAccountsQueryApiCall(String username, int expectedAccountId) {
        AccountState accountStateMock = mock(AccountState.class);
        Account accountMock = mock(Account.class);
        when(accountStateMock.account()).thenReturn(accountMock);
        when(accountMock.id()).thenReturn(Account.id(expectedAccountId));
        when(accountCacheMock.getByUsername(username)).thenReturn(Optional.of(accountStateMock));
    }

    private void mockGerritAccountGroupsApiCall(Accounts accountsMock, int accountId)
        throws RestApiException {
        Gson gson = OutputFormat.JSON.newGson();
        List<GroupInfo> groups =
            gson.fromJson(
                readTestFile("__files/gerritAccountGroups.json"),
                new TypeLiteral<List<GroupInfo>>() {}.getType());
        AccountApi accountApiMock = mock(AccountApi.class);
        when(accountsMock.id(accountId)).thenReturn(accountApiMock);
        when(accountApiMock.getGroups()).thenReturn(groups);
    }

    private void mockGerritChangeDetailsApiCall() throws RestApiException {
        ChangeInfo changeInfo = readTestFileToClass("__files/gerritPatchSetDetail.json", ChangeInfo.class);
        when(changeApiMock.get()).thenReturn(changeInfo);
    }

    private void mockGerritChangeCommentsApiCall() throws RestApiException {
        Map<String, List<CommentInfo>> comments =
            readTestFileToType(
                "__files/gerritPatchSetComments.json",
                new TypeLiteral<Map<String, List<CommentInfo>>>() {}.getType());
        when(changeApiMock.commentsRequest()).thenReturn(commentsRequestMock);
        when(commentsRequestMock.get()).thenReturn(comments);
    }

    private void mockGerritChangeApiRestEndpoint() throws RestApiException {
        when(gerritApi.changes()).thenReturn(changesMock);
        when(changesMock.id(PROJECT_NAME.get(), BRANCH_NAME.shortName(), CHANGE_ID.get())).thenReturn(changeApiMock);
    }

    private void mockGerritReviewApiCall() throws RestApiException {
        ArgumentCaptor<ReviewInput> reviewInputCaptor = ArgumentCaptor.forClass(ReviewInput.class);
        when(revisionApiMock.review(reviewInputCaptor.capture())).thenReturn(reviewResult);
    }

    protected void initComparisonContent() {}

    protected <T> T readTestFileToClass(String filename, Class<T> clazz) {
        Gson gson = OutputFormat.JSON.newGson();
        return gson.fromJson(readTestFile(filename), clazz);
    }

    protected <T> T readTestFileToType(String filename, Type type) {
        Gson gson = OutputFormat.JSON.newGson();
        return gson.fromJson(readTestFile(filename), type);
    }

    protected String readTestFile(String filename) {
        try {
            return new String(Files.readAllBytes(basePath.resolve(filename)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected EventHandlerTask.Result handleEventBasedOnType(EventHandlerTask.SupportedEvents triggeredEvent) {
        Consumer<Event> typeSpecificSetup = getTypeSpecificSetup(triggeredEvent);
        Event event = getMockedEvent(triggeredEvent);
        setupCommonEventMocks((PatchSetEvent) event); // Apply common mock configurations
        typeSpecificSetup.accept(event);

        EventHandlerTask task = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                install(new TestGerritEventContextModule(config, event));

                bind(GerritClient.class).toInstance(gerritClient);
                bind(GitRepoFiles.class).toInstance(gitRepoFiles);
                bind(ConfigCreator.class).toInstance(mockConfigCreator);
                bind(PatchSetReviewer.class).toInstance(patchSetReviewer);
                bind(PluginDataHandlerProvider.class).toInstance(pluginDataHandlerProvider);
                bind(AccountCache.class).toInstance(mockAccountCache());
            }
        }).getInstance(EventHandlerTask.class);
        return task.execute();
    }

    protected ArgumentCaptor<ReviewInput> testRequestSent() throws RestApiException {
        ArgumentCaptor<ReviewInput> reviewInputCaptor = ArgumentCaptor.forClass(ReviewInput.class);
        verify(revisionApiMock).review(reviewInputCaptor.capture());
        gptRequestBody = getGson().fromJson(patchSetReviewer.getChatGptClient().getRequestBody(), JsonObject.class);
        return reviewInputCaptor;
    }

    protected void initTest() {
        changeSetData = new ChangeSetData(
                GPT_USER_ACCOUNT_ID,
                config.getVotingMinScore(),
                config.getMaxReviewFileSize()
        );
        Localizer localizer = new Localizer(config);
        gerritClient =
            new GerritClient(
                new GerritClientFacade(
                    config,
                    changeSetData,
                    new GerritClientComments(
                            config,
                            accountCacheMock,
                            changeSetData,
                            pluginDataHandlerProvider,
                            localizer
                    ),
                    getGerritClientPatchSet()));
        patchSetReviewer =
            new PatchSetReviewer(
                gerritClient,
                config,
                changeSetData,
                Providers.of(new GerritClientReview(config, accountCacheMock, pluginDataHandlerProvider, localizer)),
                getChatGptClient(),
                localizer
            );
        mockConfigCreator = mock(ConfigCreator.class);
    }

    private AccountAttribute createTestAccountAttribute() {
        AccountAttribute accountAttribute = new AccountAttribute();
        accountAttribute.name = GERRIT_USER_ACCOUNT_NAME;
        accountAttribute.username = GERRIT_USER_USERNAME;
        accountAttribute.email = GERRIT_USER_ACCOUNT_EMAIL;
        return accountAttribute;
    }

    private PatchSetAttribute createPatchSetAttribute() {
        PatchSetAttribute patchSetAttribute = new PatchSetAttribute();
        patchSetAttribute.kind = REWORK;
        patchSetAttribute.author = createTestAccountAttribute();
        return patchSetAttribute;
    }

    @NonNull
    private Consumer<Event> getTypeSpecificSetup(EventHandlerTask.SupportedEvents triggeredEvent) {
        return switch (triggeredEvent) {
            case COMMENT_ADDED -> event -> {
                CommentAddedEvent commentEvent = (CommentAddedEvent) event;
                commentEvent.author = this::createTestAccountAttribute;
                commentEvent.patchSet = this::createPatchSetAttribute;
                commentEvent.eventCreatedOn = TEST_TIMESTAMP;
                when(commentEvent.getType()).thenReturn("comment-added");
            };
            case PATCH_SET_CREATED -> event -> {
                PatchSetCreatedEvent patchEvent = (PatchSetCreatedEvent) event;
                patchEvent.patchSet = this::createPatchSetAttribute;
                when(patchEvent.getType()).thenReturn("patchset-created");
            };
            case CHANGE_MERGED -> event -> {
                ChangeMergedEvent mergedEvent = (ChangeMergedEvent) event;
                when(mergedEvent.getType()).thenReturn("change-merged");
            };
        };
    }

    private Event getMockedEvent(EventHandlerTask.SupportedEvents triggeredEvent) {
        return (Event) mock(EVENT_CLASS_MAP.get(triggeredEvent));
    }

    private void setupCommonEventMocks(PatchSetEvent event) {
        when(event.getProjectNameKey()).thenReturn(PROJECT_NAME);
        when(event.getBranchNameKey()).thenReturn(BRANCH_NAME);
        when(event.getChangeKey()).thenReturn(CHANGE_ID);
    }

    private AccountCache mockAccountCache() {
        AccountCache accountCache = mock(AccountCache.class);
        Account account = Account.builder(Account.id(GPT_USER_ACCOUNT_ID), Instant.now()).build();
        AccountState accountState = AccountState.forAccount(account, Collections.emptyList());
        doReturn(Optional.of(accountState)).when(accountCache).getByUsername(GERRIT_GPT_USERNAME);

        return accountCache;
    }

    private IChatGptClient getChatGptClient() {
        return switch (config.getGptMode()) {
            case stateful -> new ChatGptClientStateful(config, gitRepoFiles, pluginDataHandlerProvider);
            case stateless -> new ChatGptClientStateless(config);
        };
    }

    private IGerritClientPatchSet getGerritClientPatchSet() {
        return switch (config.getGptMode()) {
            case stateful -> new GerritClientPatchSetStateful(config, accountCacheMock);
            case stateless -> new GerritClientPatchSetStateless(config, accountCacheMock);
        };
    }
}
