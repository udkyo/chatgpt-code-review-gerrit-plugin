package com.googlesource.gerrit.plugins.chatgpt.listener;

import com.google.common.base.Splitter;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.server.data.PatchSetAttribute;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.PatchSetEvent;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.chatgpt.PatchSetReviewer;
import com.googlesource.gerrit.plugins.chatgpt.client.GerritClient;
import com.googlesource.gerrit.plugins.chatgpt.config.Configuration;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Supplier;

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
    private CompletableFuture<Void> latestFuture;

    @Inject
    public EventListenerHandler(PatchSetReviewer reviewer, GerritClient gerritClient) {
        this.reviewer = reviewer;
        this.gerritClient = gerritClient;

        addShutdownHoot();
    }

    public static String buildFullChangeId(Project.NameKey projectName, BranchNameKey branchName, Change.Key changeKey) {
        return String.join("~", projectName.get(), branchName.shortName(), changeKey.get());
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

    private boolean isPatchsetReviewEnabled(Configuration config, PatchSetEvent patchSetEvent) {
        if (!config.getGptReviewPatchSet()) {
            log.debug("Disabled review function for created or updated Patchsets.");
            return false;
        }
        try {
            Supplier<PatchSetAttribute> patchSetAttribute = patchSetEvent.patchSet;
            ChangeKind patchSetEventKind = patchSetAttribute.get().kind;
            if (patchSetEventKind != REWORK) {
                log.info("Change kind '{}' not processed", patchSetEventKind);
                return false;
            }
        }
        catch (NullPointerException e) {
            log.debug("PatchSet event properties not retrieved");
            return false;
        }
        return true;
    }

    public void handleEvent(Configuration config, Event event) {
        PatchSetEvent patchSetEvent = (PatchSetEvent) event;
        String eventType = Optional.ofNullable(event.getType()).orElse("");
        log.info("Event type {}", eventType);
        Project.NameKey projectNameKey = patchSetEvent.getProjectNameKey();
        BranchNameKey branchNameKey = patchSetEvent.getBranchNameKey();
        Change.Key changeKey = patchSetEvent.getChangeKey();

        String fullChangeId = buildFullChangeId(projectNameKey, branchNameKey, changeKey);

        gerritClient.initialize(config);

        List<String> enabledProjects = Splitter.on(",").omitEmptyStrings()
                .splitToList(config.getEnabledProjects());
        if (!config.isGlobalEnable() &&
                !enabledProjects.contains(projectNameKey.get()) &&
                !config.isProjectEnable()) {
            log.info("The project {} is not enabled for review", projectNameKey);
            return;
        }

        switch (eventType) {
            case "patchset-created":
                if (!isPatchsetReviewEnabled(config, patchSetEvent)) {
                    return;
                }
                break;
            case "comment-added":
                if (!gerritClient.retrieveLastComments(event, fullChangeId)) {
                    log.info("Found no comment to review");
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
