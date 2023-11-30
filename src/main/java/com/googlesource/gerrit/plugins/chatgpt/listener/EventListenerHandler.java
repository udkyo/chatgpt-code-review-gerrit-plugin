package com.googlesource.gerrit.plugins.chatgpt.listener;

import com.google.common.base.Splitter;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.server.data.ChangeAttribute;
import com.google.gerrit.server.data.PatchSetAttribute;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.PatchSetEvent;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.chatgpt.PatchSetReviewer;
import com.googlesource.gerrit.plugins.chatgpt.client.GerritClient;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import lombok.extern.slf4j.Slf4j;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

import static com.google.gerrit.extensions.client.ChangeKind.REWORK;

@Slf4j
public class EventListenerHandler {

    private final PatchSetReviewer reviewer;
    private final BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(100);
    private final RejectedExecutionHandler handler = new ThreadPoolExecutor.CallerRunsPolicy();
    private final ThreadFactory threadFactory = new ThreadFactoryBuilder()
            .setNameFormat("EventListenerHandler-%d")
            .build();
    private final ExecutorService executorService = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, queue, threadFactory, handler);
    private final GerritClient gerritClient;
    private Configuration config;
    private CompletableFuture<Void> latestFuture;

    @Inject
    public EventListenerHandler(PatchSetReviewer reviewer, GerritClient gerritClient) {
        this.reviewer = reviewer;
        this.gerritClient = gerritClient;

        addShutdownHoot();
    }

    public static String buildFullChangeId(Project.NameKey projectName, BranchNameKey branchName, Change.Key changeKey) {
        return String.join("~", URLEncoder.encode(projectName.get(), StandardCharsets.UTF_8),
                branchName.shortName(), changeKey.get());
    }

    private void addShutdownHoot() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException ex) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }));
    }

    private Optional<PatchSetAttribute> getPatchSetAttribute(PatchSetEvent patchSetEvent) {
        try {
            return Optional.ofNullable(patchSetEvent.patchSet.get());
        }
        catch (NullPointerException e) {
            return Optional.empty();
        }
    }

    private Optional<String> getTopic(PatchSetEvent patchSetEvent) {
        try {
            ChangeAttribute changeAttribute = patchSetEvent.change.get();
            return Optional.ofNullable(changeAttribute.topic);
        }
        catch (NullPointerException e) {
            return Optional.empty();
        }
    }

    private boolean isReviewEnabled(PatchSetEvent patchSetEvent, Project.NameKey projectNameKey) {
        List<String> enabledProjects = Splitter.on(",").omitEmptyStrings()
                .splitToList(config.getEnabledProjects());
        if (!config.isGlobalEnable() &&
                !enabledProjects.contains(projectNameKey.get()) &&
                !config.isProjectEnable()) {
            log.info("The project {} is not enabled for review", projectNameKey);
            return false;
        }

        String topic = getTopic(patchSetEvent).orElse("");
        log.debug("PatchSet Topic retrieved: '{}'", topic);
        if (gerritClient.isDisabledTopic(topic)) {
            log.info("Disabled review for PatchSets with Topic '{}'", topic);
            return false;
        }

        return true;
    }

    private boolean isPatchSetReviewEnabled(PatchSetEvent patchSetEvent) {
        if (!config.getGptReviewPatchSet()) {
            log.info("Disabled review function for created or updated PatchSets.");
            return false;
        }
        Optional<PatchSetAttribute> patchSetAttributeOptional = getPatchSetAttribute(patchSetEvent);
        if (patchSetAttributeOptional.isEmpty()) {
            log.info("PatchSetAttribute event properties not retrieved");
            return false;
        }
        PatchSetAttribute patchSetAttribute = patchSetAttributeOptional.get();
        ChangeKind patchSetEventKind = patchSetAttribute.kind;
        if (patchSetEventKind != REWORK) {
            log.info("Change kind '{}' not processed", patchSetEventKind);
            return false;
        }
        String authorUsername = patchSetAttribute.author.username;
        if (gerritClient.isDisabledUser(authorUsername)) {
            log.info("Review of PatchSets from user '{}' is disabled.", authorUsername);
            return false;
        }
        return true;
    }

    public void handleEvent(Configuration config, Event event) {
        this.config = config;
        PatchSetEvent patchSetEvent = (PatchSetEvent) event;
        String eventType = Optional.ofNullable(event.getType()).orElse("");
        log.info("Event type {}", eventType);
        Project.NameKey projectNameKey = patchSetEvent.getProjectNameKey();
        BranchNameKey branchNameKey = patchSetEvent.getBranchNameKey();
        Change.Key changeKey = patchSetEvent.getChangeKey();

        String fullChangeId = buildFullChangeId(projectNameKey, branchNameKey, changeKey);

        gerritClient.initialize(config);

        if (!isReviewEnabled(patchSetEvent, projectNameKey)) {
            return;
        }
        switch (eventType) {
            case "patchset-created":
                if (!isPatchSetReviewEnabled(patchSetEvent)) {
                    return;
                }
                break;
            case "comment-added":
                if (!gerritClient.retrieveLastComments(event, fullChangeId)) {
                    log.info("No comments found for review");
                    return;
                }
                break;
            default:
                return;
        }

        // Execute the potentially time-consuming operation asynchronously
        latestFuture = CompletableFuture.runAsync(() -> {
            try {
                log.info("Processing change: {}", fullChangeId);
                reviewer.review(config, fullChangeId);
                log.info("Finished processing change: {}", fullChangeId);
            } catch (Exception e) {
                log.error("Error while processing change: {}", fullChangeId, e);
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }, executorService);
    }

    public CompletableFuture<Void> getLatestFuture() {
        return latestFuture;
    }

}
