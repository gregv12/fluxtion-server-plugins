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
    /** Approved and tested commands, strictly one at a time. */
    private ExecutorService commandRunner;
    /** Choices lookups, so editing and approving never queue behind a slow approved command. */
    private ExecutorService lookupRunner;
    /** Most output kept from one command; the rest is dropped and marked. */
    private int maxOutputChars = 64 * 1024;
    private volatile String restartStatus = "not started";
    private String catalogueError;
    private RegistryGateway gateway;

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
            catalogue = catalogueFile == null ? new Catalogue() : Catalogue.load(Path.of(catalogueFile));
        } catch (Exception e) {
            // a bad catalogue must not stop the server: run with nothing schedulable and say why
            catalogueError = "catalogue " + catalogueFile + " not loaded, nothing can be scheduled: " + e.getMessage();
            log.error("scheduler {}", catalogueError, e);
            catalogue = new Catalogue();
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
        gateway = new RegistryGateway();
        engine = new SchedulerEngine(store, outcomeLog, catalogue, gateway, System::currentTimeMillis,
                new SchedulerEngine.Settings(environment, zone, timedExpiryMinutes, restartExpiryMinutes));
        try {
            engine.recoverAfterStart();
        } catch (RuntimeException e) {
            log.error("scheduler recovery incomplete; pending decisions will be retried by tick", e);
        }
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
        } catch (SchedulerEngine.Refused r) {
            if (engine.isPaused()) { // paused between the check and the offer: wait for resume
                restartStatus = "waiting: the scheduler is paused";
                ticker.schedule(() -> offerRestartSetWhenReady(startedAt), 5, TimeUnit.SECONDS);
            } else {
                restartStatus = "restart set not offered: " + r.getMessage();
                log.warn("scheduler restart set not offered: {}", r.getMessage());
            }
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
        if (catalogueError != null) m.put("catalogueError", catalogueError);
        if (gateway != null && gateway.busyReason() != null) m.put("busy", gateway.busyReason());
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
        /** One execution slot per path, held from reservation until the command has really returned. */
        private final Slot commands = new Slot("command");
        private final Slot lookups = new Slot("lookup");

        @Override
        public Collection<String> liveCommands() {
            return ActionScheduler.this.liveCommands();
        }

        @Override
        public boolean tryReserve(String command) {
            return commands.tryReserve(command);
        }

        @Override
        public void release() {
            commands.release();
        }

        @Override
        public String busyReason() {
            return commands.busyReason();
        }

        /** Runs on the slot the engine reserved; the slot is freed when the command returns, not before. */
        @Override
        public SchedulerEngine.CommandResult run(String command, List<String> arguments) {
            return execute(command, arguments, commands, commandRunner);
        }

        /** A lookup never queues: if one is still in flight this one is refused (the caller can try again). */
        @Override
        public SchedulerEngine.CommandResult lookup(String command) {
            if (!lookups.tryReserve(command)) {
                return new SchedulerEngine.CommandResult(false, "not run: " + lookups.busyReason());
            }
            return execute(command, List.of(), lookups, lookupRunner);
        }

        private SchedulerEngine.CommandResult execute(String command, List<String> arguments, Slot slot, ExecutorService runner) {
            Output output = new Output(maxOutputChars);
            Output errors = new Output(maxOutputChars);
            AdminCommandRequest request = new AdminCommandRequest();
            request.setCommand(command);
            // the server puts the command name in front of these before calling the command
            request.setArguments(new ArrayList<>(arguments == null ? List.of() : arguments));
            request.setOutput(output::add);
            request.setErrOutput(errors::add);
            Future<?> f;
            try {
                f = runner.submit(() -> {
                    try {
                        registry.processAdminCommandRequest(request);
                    } finally {
                        slot.release();
                    }
                });
            } catch (RuntimeException e) {
                slot.release();
                return new SchedulerEngine.CommandResult(false, "not run: " + e);
            }
            try {
                f.get(commandTimeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                // Never interrupted: for a processor's command the server publishes it and then waits for the
                // processor to run it, so an interrupt would end the wait, not the command. The slot stays held
                // until the call really returns, so no other command can overlap it.
                slot.timedOut();
                log.warn("scheduler {} {} timed out after {}s; nothing else runs on that path until it returns",
                        slot.path, command, commandTimeoutSeconds);
                return new SchedulerEngine.CommandResult(false, "timed out after " + commandTimeoutSeconds + "s, result unknown. " + output + errors);
            } catch (java.util.concurrent.ExecutionException e) {
                return new SchedulerEngine.CommandResult(false, "error: " + e.getCause() + " " + output + errors);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new SchedulerEngine.CommandResult(false, "interrupted while waiting, result unknown. " + output + errors);
            }
            String out = output.toString();
            boolean ok = errors.isEmpty() && !output.sawFailureMarker();
            return new SchedulerEngine.CommandResult(ok, ok ? out : (out + (errors.isEmpty() ? "" : " error: " + errors)).trim());
        }
    }

    /** Who holds an execution path, and whether they overran the timeout. */
    private static final class Slot {
        private final String path;
        private String holder;
        private boolean overran;

        Slot(String path) {
            this.path = path;
        }

        synchronized boolean tryReserve(String command) {
            if (holder != null) return false;
            holder = command;
            overran = false;
            return true;
        }

        synchronized void release() {
            holder = null;
            overran = false;
        }

        synchronized void timedOut() {
            overran = true;
        }

        synchronized String busyReason() {
            return holder == null ? null : "the " + path + " path is busy with " + holder
                    + (overran ? ", which timed out (result unknown)" : "");
        }
    }

    /** Command output, kept up to a limit (a command that floods its output cannot exhaust the heap). */
    private static final class Output {
        private final StringBuilder sb = new StringBuilder();
        private final int max;
        private boolean truncated;
        private boolean failureMarker;

        Output(int max) {
            this.max = max;
        }

        synchronized void add(Object o) {
            String text = String.valueOf(o);
            if (text.contains(BUSY) || text.contains(PROBLEM)) failureMarker = true; // even if not kept
            if (truncated) return;
            if (!sb.isEmpty()) sb.append('\n');
            if (sb.length() + text.length() > max) {
                sb.append(text, 0, Math.max(0, max - sb.length())).append(" ...[output truncated]");
                truncated = true;
            } else {
                sb.append(text);
            }
        }

        synchronized boolean isEmpty() {
            return sb.isEmpty();
        }

        synchronized boolean sawFailureMarker() {
            return failureMarker;
        }

        @Override
        public synchronized String toString() {
            return sb.toString();
        }
    }
}
