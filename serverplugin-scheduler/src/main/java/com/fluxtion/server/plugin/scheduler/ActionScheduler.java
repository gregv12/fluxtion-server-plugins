/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.scheduler;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.lifecycle.Lifecycle;
import com.fluxtion.server.service.admin.AdminCommandRegistry;
import com.fluxtion.server.service.admin.AdminCommandRequest;
import com.fluxtion.server.service.admin.AdminFunction;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Approval-gated scheduler for the server's own admin commands, exposed as {@code scheduler.*} admin commands.
 *
 * <p>Support builds a library of actions (an allowed admin command plus arguments, chosen from a catalogue); a
 * trader schedules them once, daily or after a restart; when one falls due it waits as an open item until someone
 * approves (it runs) or declines it, or it expires. Every closed item is written to an append-only outcome log in
 * the audit format; only the live library, schedules and open items are kept in the state directory.</p>
 *
 * <p>Commands run one at a time on a dedicated thread with a timeout; a run that times out is recorded Failed with
 * its result unknown. Answers are JSON on the output channel; refusals go to the error channel.</p>
 *
 * <pre>
 * - agentName: scheduler          # (or as a plain service)
 *   service: !!com.fluxtion.server.plugin.scheduler.ActionScheduler
 *     catalogueFile: app-config/scheduler-catalogue.yaml
 *     environment: uat
 * </pre>
 */
@Log4j2
@Getter
@Setter
public class ActionScheduler implements Lifecycle {

    private static final String BUSY = "command is busy";
    private static final String PROBLEM = "problem executing command";

    private String stateDirectory = "cache/scheduler";
    private String outcomeLogFile = "var/log/auditLog-scheduler.yaml";
    private long outcomeLogMaxBytes = 200L * 1024 * 1024;
    private int outcomeLogFiles = 5;
    private int historyCap = 5_000;
    private String catalogueFile;
    private String environment = "";
    private String zone = "UTC";
    private boolean enabled = true;
    private int timedExpiryMinutes = 15;
    private int restartExpiryMinutes = 30;
    private int commandTimeoutSeconds = 10;
    private long tickMillis = 1_000;
    /** Wait at least this long after start before offering the restart set. */
    private int restartReadyDelaySeconds = 10;
    /** Offer the restart set anyway (with a warning) if its commands are not all registered by then. */
    private int restartReadyTimeoutSeconds = 120;

    @Getter(lombok.AccessLevel.NONE)
    @Setter(lombok.AccessLevel.NONE)
    private final ObjectMapper json = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    private AdminCommandRegistry registry;
    private SchedulerEngine engine;
    private ScheduledExecutorService ticker;
    /** Approved and tested commands, one at a time. Replaced after a timeout so a stuck command blocks nothing else. */
    private volatile ExecutorService commandRunner;
    /** Choices lookups, so editing and approving never queue behind a slow approved command. */
    private volatile ExecutorService lookupRunner;
    private volatile String restartStatus = "not started";

    @ServiceRegistered
    public void adminRegistry(AdminCommandRegistry registry, String name) {
        log.info("scheduler admin registry '{}'", name);
        this.registry = registry;
        register("scheduler.status", (a, out, err) -> answer(out, err, this::status));
        register("scheduler.catalogue", (a, out, err) -> answer(out, err, () -> engine.liveCatalogue()));
        register("scheduler.choices", (a, out, err) -> answer(out, err, () -> engine.choices(arg(a, 0, "command"), arg(a, 1, "argument"))));
        register("scheduler.actions", (a, out, err) -> answer(out, err, () -> engine.actions()));
        register("scheduler.saveAction", (a, out, err) -> answer(out, err, () -> {
            SchedulerAction in = parse(arg(a, 0, "action JSON"), SchedulerAction.class);
            Long basedOn = in.getId() == null ? null : in.getChangedAt();
            return engine.saveAction(in, basedOn, by(a, 1));
        }));
        register("scheduler.retireAction", (a, out, err) -> answer(out, err, () -> engine.retireAction(arg(a, 0, "action id"), by(a, 1))));
        register("scheduler.testAction", (a, out, err) -> answer(out, err, () -> engine.testAction(arg(a, 0, "action id"), by(a, 1))));
        register("scheduler.schedules", (a, out, err) -> answer(out, err, () -> engine.schedules()));
        register("scheduler.saveSchedule", (a, out, err) -> answer(out, err, () -> {
            SchedulerSchedule in = parse(arg(a, 0, "schedule JSON"), SchedulerSchedule.class);
            Long basedOn = in.getId() == null ? null : in.getChangedAt();
            return engine.saveSchedule(in, basedOn, by(a, 1));
        }));
        register("scheduler.enableSchedule", (a, out, err) -> answer(out, err,
                () -> engine.enableSchedule(arg(a, 0, "schedule id"), Boolean.parseBoolean(arg(a, 1, "true|false")), by(a, 2))));
        register("scheduler.deleteSchedule", (a, out, err) -> answer(out, err, () -> engine.deleteSchedule(arg(a, 0, "schedule id"))));
        register("scheduler.open", (a, out, err) -> answer(out, err, () -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("now", System.currentTimeMillis());
            m.put("items", engine.open());
            return m;
        }));
        register("scheduler.approve", (a, out, err) -> answer(out, err, () -> engine.approve(arg(a, 0, "item id"), by(a, 1))));
        register("scheduler.decline", (a, out, err) -> answer(out, err, () -> engine.decline(arg(a, 0, "item id"), by(a, 1))));
        register("scheduler.approveAll", (a, out, err) -> answer(out, err, () -> engine.approveAll(arg(a, 0, "restart set id"), by(a, 1))));
        register("scheduler.declineAll", (a, out, err) -> answer(out, err, () -> engine.declineAll(arg(a, 0, "restart set id"), by(a, 1))));
        register("scheduler.runRestartActions", (a, out, err) -> answer(out, err, () -> {
            String setId = engine.offerRestartSet("offered on request by " + by(a, 0));
            if (setId == null) throw new SchedulerEngine.Refused("there are no enabled after-restart schedules");
            return Map.of("restartSetId", setId);
        }));
        register("scheduler.summary", (a, out, err) -> answer(out, err, () -> {
            long since = LocalDate.now(ZoneId.of(zone)).atStartOfDay(ZoneId.of(zone)).toInstant().toEpochMilli();
            return engine.summary(since, 20);
        }));
        register("scheduler.history", (a, out, err) -> answer(out, err, () -> {
            long from = a.size() > 1 ? Long.parseLong(a.get(1)) : 0;
            long to = a.size() > 2 ? Long.parseLong(a.get(2)) : Long.MAX_VALUE;
            return engine.history(from, to);
        }));
        register("scheduler.pause", (a, out, err) -> answer(out, err, () -> {
            engine.setPaused(true);
            log.warn("scheduler paused by {}", by(a, 0));
            return status();
        }));
        register("scheduler.resume", (a, out, err) -> answer(out, err, () -> {
            engine.setPaused(false);
            log.info("scheduler resumed by {}", by(a, 0));
            return status();
        }));
        register("scheduler.acknowledgeExpired", (a, out, err) -> answer(out, err, () -> {
            engine.acknowledgeExpired();
            return Map.of("acknowledged", true);
        }));
    }

    @Override
    public void init() {
        Catalogue catalogue;
        try {
            catalogue = catalogueFile == null ? Catalogue.parse("entries: []") : Catalogue.load(Path.of(catalogueFile));
        } catch (Exception e) {
            throw new IllegalStateException("scheduler catalogue " + catalogueFile + " is invalid: " + e.getMessage(), e);
        }
        SchedulerStore store = new SchedulerStore(Path.of(stateDirectory), historyCap);
        store.load();
        OutcomeLog outcomeLog = new OutcomeLog(Path.of(outcomeLogFile), outcomeLogMaxBytes, outcomeLogFiles);
        if (store.skippedHistoryLines() > 0) {
            log.warn("scheduler history: skipped {} unreadable line(s), cut short by a crash; the outcome log has them", store.skippedHistoryLines());
        }
        commandRunner = newRunner("scheduler-command");
        lookupRunner = newRunner("scheduler-lookup");
        ticker = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "scheduler-tick"));
        engine = new SchedulerEngine(store, outcomeLog, catalogue, new RegistryGateway(), System::currentTimeMillis,
                new SchedulerEngine.Settings(environment, zone, timedExpiryMinutes, restartExpiryMinutes));
        engine.recoverAfterStart();
        log.info("scheduler init environment:'{}' zone:{} enabled:{} state:{} catalogue:{} entries",
                environment, zone, enabled, stateDirectory, catalogue.getCommands().size());
    }

    @Override
    public void start() {
        if (!enabled) {
            restartStatus = "scheduler disabled by config";
            log.warn("scheduler disabled by config: nothing will fall due");
            return;
        }
        ticker.scheduleWithFixedDelay(this::tick, tickMillis, tickMillis, TimeUnit.MILLISECONDS);
        long startedAt = System.currentTimeMillis();
        restartStatus = "waiting for the server to be ready";
        ticker.schedule(() -> offerRestartSetWhenReady(startedAt), restartReadyDelaySeconds, TimeUnit.SECONDS);
    }

    @Override
    public void tearDown() {
        if (ticker != null) ticker.shutdownNow();
        if (commandRunner != null) commandRunner.shutdownNow();
        if (lookupRunner != null) lookupRunner.shutdownNow();
    }

    private void tick() {
        try {
            for (OpenItem item : engine.tick()) {
                log.info("scheduler item due: '{}' {} {} expires {}", item.getTitle(), item.getCommand(), item.getArguments(), item.getExpiresAt());
            }
        } catch (Throwable t) {
            log.error("scheduler tick failed", t);
        }
    }

    private void offerRestartSetWhenReady(long startedAt) {
        try {
            Collection<String> live = liveCommands();
            List<String> missing = new ArrayList<>(engine.restartCommands());
            missing.removeAll(live);
            if (engine.isPaused()) {
                restartStatus = "waiting: the scheduler is paused";
                ticker.schedule(() -> offerRestartSetWhenReady(startedAt), 5, TimeUnit.SECONDS);
                return;
            }
            boolean timedOut = System.currentTimeMillis() - startedAt >= restartReadyTimeoutSeconds * 1000L;
            if (!missing.isEmpty() && !timedOut) {
                ticker.schedule(() -> offerRestartSetWhenReady(startedAt), 2, TimeUnit.SECONDS);
                return;
            }
            for (String pattern : engine.catalogue().unmatchedEntries(live)) {
                log.warn("scheduler catalogue entry '{}' matches no command on this server", pattern);
            }
            String reason = missing.isEmpty() ? "server restart"
                    : "server restart; WARNING not registered after " + restartReadyTimeoutSeconds + "s: " + missing;
            String setId = engine.offerRestartSet(reason);
            restartStatus = setId == null ? "no after-restart schedules" : "offered " + setId + (missing.isEmpty() ? "" : " with missing commands " + missing);
            log.info("scheduler restart set: {}", restartStatus);
        } catch (Throwable t) {
            restartStatus = "restart set not offered: " + t.getMessage();
            log.error("scheduler could not offer the restart set", t);
        }
    }

    private Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", enabled);
        m.put("paused", engine != null && engine.isPaused());
        m.put("environment", environment);
        m.put("zone", zone);
        m.put("timedExpiryMinutes", timedExpiryMinutes);
        m.put("restartExpiryMinutes", restartExpiryMinutes);
        m.put("commandTimeoutSeconds", commandTimeoutSeconds);
        m.put("restart", restartStatus);
        return m;
    }

    // ==================== admin command plumbing ====================

    private interface Answer {
        Object get() throws Exception;
    }

    private void register(String name, AdminFunction<Object, Object> fn) {
        registry.registerCommand(name, fn);
    }

    private void answer(java.util.function.Consumer<Object> out, java.util.function.Consumer<Object> err, Answer answer) {
        if (engine == null) {
            err.accept("scheduler not initialised");
            return;
        }
        try {
            out.accept(json.writeValueAsString(answer.get()));
        } catch (SchedulerEngine.Refused r) {
            err.accept(r.getMessage());
        } catch (Exception e) {
            log.warn("scheduler command failed", e);
            err.accept(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** Admin arguments arrive with the command name first. */
    private static String arg(List<String> args, int i, String what) {
        if (args == null || args.size() <= i + 1 || args.get(i + 1).isBlank()) throw new SchedulerEngine.Refused("missing argument: " + what);
        return args.get(i + 1);
    }

    private static String by(List<String> args, int i) {
        return args != null && args.size() > i + 1 && !args.get(i + 1).isBlank() ? args.get(i + 1) : "unnamed";
    }

    private <T> T parse(String text, Class<T> type) {
        try {
            return json.readValue(text, type);
        } catch (Exception e) {
            throw new SchedulerEngine.Refused("not valid " + type.getSimpleName() + " JSON: " + e.getMessage());
        }
    }

    private Collection<String> liveCommands() {
        return registry == null ? List.of() : registry.commandList();
    }

    private static ExecutorService newRunner(String name) {
        return Executors.newSingleThreadExecutor(r -> daemon(r, name));
    }

    private static Thread daemon(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }

    /** Runs commands through the registry on the single command thread, bounded by the timeout. */
    private class RegistryGateway implements SchedulerEngine.CommandGateway {
        @Override
        public Collection<String> liveCommands() {
            return ActionScheduler.this.liveCommands();
        }

        @Override
        public SchedulerEngine.CommandResult run(String command, List<String> arguments) {
            return execute(command, arguments, false);
        }

        @Override
        public SchedulerEngine.CommandResult lookup(String command) {
            return execute(command, List.of(), true);
        }

        private SchedulerEngine.CommandResult execute(String command, List<String> arguments, boolean lookup) {
            StringBuffer output = new StringBuffer();
            StringBuffer errors = new StringBuffer();
            AdminCommandRequest request = new AdminCommandRequest();
            request.setCommand(command);
            // the server puts the command name in front of these before calling the command
            request.setArguments(new ArrayList<>(arguments == null ? List.of() : arguments));
            request.setOutput(o -> append(output, o));
            request.setErrOutput(o -> append(errors, o));
            ExecutorService runner = lookup ? lookupRunner : commandRunner;
            Future<?> f = runner.submit(() -> registry.processAdminCommandRequest(request));
            try {
                f.get(commandTimeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                f.cancel(true);
                replace(runner, lookup);
                return new SchedulerEngine.CommandResult(false, "timed out after " + commandTimeoutSeconds + "s, result unknown. " + output + errors);
            } catch (Exception e) {
                return new SchedulerEngine.CommandResult(false, "error: " + e + " " + output + errors);
            }
            String out = output.toString();
            boolean ok = errors.isEmpty() && !out.contains(BUSY) && !out.contains(PROBLEM);
            return new SchedulerEngine.CommandResult(ok, ok ? out : (out + (errors.isEmpty() ? "" : " error: " + errors)).trim());
        }

        /** A command that ignored the interrupt may still be running: give later commands a fresh thread. */
        private synchronized void replace(ExecutorService stuck, boolean lookup) {
            String name = lookup ? "scheduler-lookup" : "scheduler-command";
            if (lookup && lookupRunner == stuck) {
                lookupRunner = newRunner(name);
            } else if (!lookup && commandRunner == stuck) {
                commandRunner = newRunner(name);
            } else {
                return;
            }
            stuck.shutdownNow();
            log.warn("scheduler {} thread replaced after a timeout; the timed-out command may still complete", name);
        }

        private void append(StringBuffer sb, Object o) {
            if (!sb.isEmpty()) sb.append('\n');
            sb.append(o);
        }
    }
}
