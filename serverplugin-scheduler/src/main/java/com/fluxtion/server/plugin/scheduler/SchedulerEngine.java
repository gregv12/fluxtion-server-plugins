/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.scheduler;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * The scheduler's rules, with no threads of its own: the clock and the way commands run are injected, so every
 * rule is testable at an exact time. All state changes happen under this object's lock; a command runs outside it,
 * after its item has been marked running (so a second approval is refused, never run twice).
 *
 * <p><b>Outcome of a run.</b> Ran OK = the command exists, returned within the timeout, reported nothing on its
 * error channel and said nothing that marks it busy. Anything else is Failed, with all output kept.</p>
 *
 * <p><b>After a restart</b> ({@link #recoverAfterStart()}): an item that was running closes Failed ("interrupted by
 * restart", never re-run); an open item from an earlier restart set closes Expired ("superseded"); a timed item
 * past its expiry closes Expired ("server down"). Daily schedules are never backfilled.</p>
 */
@lombok.extern.log4j.Log4j2
public class SchedulerEngine {

    /** How the engine reaches the server's commands. */
    public interface CommandGateway {
        /** Names of every command the server registers now. */
        Collection<String> liveCommands();

        /** Run a command (known to exist) and report how it went. */
        CommandResult run(String command, List<String> arguments);

        /** Run a read-only lookup (a choices command); kept off the path of approved commands where possible. */
        default CommandResult lookup(String command) {
            return run(command, List.of());
        }

        /** Why no command can be run now (an earlier one timed out and is still running), or null. */
        default String busyReason() {
            return null;
        }
    }

    /** What a command run reported. */
    public record CommandResult(boolean ok, String output) { }

    /** A refused request: the reason goes back to the caller and nothing changed. */
    public static class Refused extends RuntimeException {
        public Refused(String message) {
            super(message);
        }
    }

    /** Settings the service passes from its config. */
    public record Settings(String environment, String defaultZone, int timedExpiryMinutes, int restartExpiryMinutes) {
        boolean isProduction() {
            String e = environment == null ? "" : environment.toLowerCase(Locale.ROOT);
            return e.equals("prod") || e.equals("production");
        }
    }

    private final SchedulerStore store;
    private final OutcomeLog outcomeLog;
    private final CommandGateway gateway;
    private final LongSupplier clock;
    private final Settings settings;
    private volatile Catalogue catalogue;

    public SchedulerEngine(SchedulerStore store, OutcomeLog outcomeLog, Catalogue catalogue, CommandGateway gateway,
                           LongSupplier clock, Settings settings) {
        this.store = store;
        this.outcomeLog = outcomeLog;
        this.catalogue = catalogue;
        this.gateway = gateway;
        this.clock = clock;
        this.settings = settings;
    }

    public Catalogue catalogue() {
        return catalogue;
    }

    public Settings settings() {
        return settings;
    }

    // ==================== library (support) ====================

    /**
     * Create or edit an action. The command must match a catalogue entry and exist on this server, and the
     * arguments must fit the entry (choices checked against the server's live answer). An edit must quote the
     * {@code changedAt} it was based on, so a concurrent edit is refused instead of overwritten.
     */
    public SchedulerAction saveAction(SchedulerAction in, Long basedOnChangedAt, String by) {
        if (in.getTitle() == null || in.getTitle().isBlank()) throw new Refused("an action needs a title");
        if (in.getArguments() == null) in.setArguments(new ArrayList<>());
        if (in.getEnvironments() == null) in.setEnvironments(new ArrayList<>());
        List<String> envs = new ArrayList<>();
        for (Object e : in.getEnvironments()) {
            if (!(e instanceof String name) || name.isBlank()) throw new Refused("environments must be names, e.g. [\"dev\", \"uat\"]");
            envs.add(name.trim());
        }
        in.setEnvironments(envs);
        Catalogue.Match match = liveMatch(in.getCommand());
        String invalid = Catalogue.validate(match, in.getArguments(), arg -> choices(match, arg));
        if (invalid != null) throw new Refused(invalid);
        // the checks above can run commands (choices), so they stay outside the lock
        synchronized (this) {
            return storeAction(in, basedOnChangedAt, by);
        }
    }

    private SchedulerAction storeAction(SchedulerAction in, Long basedOnChangedAt, String by) {
        SchedulerAction existing = in.getId() == null ? null : store.actions().get(in.getId());
        if (in.getId() != null && existing == null) throw new Refused("no action " + in.getId());
        if (existing != null && basedOnChangedAt != null && existing.getChangedAt() != basedOnChangedAt) {
            throw new Refused("action " + existing.getId() + " was changed by " + existing.getChangedBy() + " since you opened it; reload and edit again");
        }
        SchedulerAction a = new SchedulerAction();
        a.setId(existing == null ? newId() : existing.getId());
        a.setTitle(in.getTitle().trim());
        a.setDescription(in.getDescription());
        a.setCommand(in.getCommand());
        a.setArguments(new ArrayList<>(in.getArguments()));
        a.setEnvironments(new ArrayList<>(in.getEnvironments()));
        a.setRetired(existing != null && existing.isRetired());
        a.setChangedBy(by);
        a.setChangedAt(nextChangeStamp(existing == null ? 0 : existing.getChangedAt()));
        store.actions().put(a.getId(), a);
        store.saveActions();
        return a;
    }

    /** Retire an action: kept for history, no longer schedulable; its schedules are disabled. */
    public synchronized SchedulerAction retireAction(String id, String by) {
        SchedulerAction a = action(id);
        a.setRetired(true);
        a.setChangedBy(by);
        a.setChangedAt(nextChangeStamp(a.getChangedAt()));
        store.saveActions();
        boolean changed = false;
        for (SchedulerSchedule s : store.schedules().values()) {
            if (id.equals(s.getActionId()) && s.isEnabled()) {
                s.setEnabled(false);
                changed = true;
            }
        }
        if (changed) store.saveSchedules();
        return a;
    }

    /** Run an action once now, without approval: support's check that it works. Refused in production. */
    public Outcome testAction(String id, String by) {
        SchedulerAction a;
        synchronized (this) {
            if (settings.isProduction()) throw new Refused("test is not allowed in production; schedule it and approve it instead");
            if (store.state().isPaused()) throw new Refused("the scheduler is paused");
            String busy = gateway.busyReason();
            if (busy != null) throw new Refused("not run, try again: " + busy);
            a = action(id);
            if (a.isRetired()) throw new Refused("action " + id + " is retired");
            checkEnvironment(a);
            checkLive(a.getCommand());
        }
        OpenItem item = itemFor(a, null, clock.getAsLong(), clock.getAsLong(), null, 0);
        return execute(item, by + " (test)");
    }

    // ==================== schedules (trader) ====================

    /** Create or edit a schedule; computes its next due time. */
    public SchedulerSchedule saveSchedule(SchedulerSchedule in, String by) {
        return saveSchedule(in, null, by);
    }

    /** As {@link #saveSchedule(SchedulerSchedule, String)}; an edit quoting a stale {@code changedAt} is refused. */
    public synchronized SchedulerSchedule saveSchedule(SchedulerSchedule in, Long basedOnChangedAt, String by) {
        SchedulerAction a = action(in.getActionId());
        if (a.isRetired()) throw new Refused("action " + a.getId() + " is retired");
        checkEnvironment(a);
        if (in.getTrigger() == null) throw new Refused("a schedule needs a trigger: ONCE, DAILY or AFTER_RESTART");
        SchedulerSchedule existing = in.getId() == null ? null : store.schedules().get(in.getId());
        if (in.getId() != null && existing == null) throw new Refused("no schedule " + in.getId());
        if (existing != null && basedOnChangedAt != null && existing.getChangedAt() != basedOnChangedAt) {
            throw new Refused("schedule " + existing.getId() + " was changed by " + existing.getChangedBy() + " since you opened it; reload and edit again");
        }
        SchedulerSchedule s = new SchedulerSchedule();
        s.setId(existing == null ? newId() : existing.getId());
        s.setActionId(a.getId());
        s.setTrigger(in.getTrigger());
        s.setAt(in.getAt());
        s.setZone(in.getZone() == null || in.getZone().isBlank() ? settings.defaultZone() : in.getZone());
        s.setExpiryMinutes(in.getExpiryMinutes());
        s.setRestartOrder(in.getRestartOrder());
        s.setEnabled(in.isEnabled());
        s.setLastFiredDue(existing == null ? 0 : existing.getLastFiredDue());
        s.setChangedBy(by);
        s.setChangedAt(nextChangeStamp(existing == null ? 0 : existing.getChangedAt()));
        zone(s); // validates the zone
        s.setNextDueAt(s.isEnabled() ? firstDue(s, clock.getAsLong()) : 0);
        if (s.getTrigger() == TriggerType.ONCE && s.isEnabled()) {
            if (s.getNextDueAt() <= s.getLastFiredDue()) throw new Refused("that one-off has already been handled; give it a new time");
            if (s.getNextDueAt() <= clock.getAsLong()) throw new Refused("that time has already passed");
        }
        store.schedules().put(s.getId(), s);
        store.saveSchedules();
        return s;
    }

    public synchronized SchedulerSchedule enableSchedule(String id, boolean enabled, String by) {
        SchedulerSchedule s = store.schedules().get(id);
        if (s == null) throw new Refused("no schedule " + id);
        if (enabled && action(s.getActionId()).isRetired()) throw new Refused("its action is retired");
        long nextDue = enabled ? firstDue(s, clock.getAsLong()) : 0;
        if (enabled && s.getTrigger() == TriggerType.ONCE && (nextDue <= clock.getAsLong() || nextDue <= s.getLastFiredDue())) {
            throw new Refused("that time has already passed or been handled; edit the schedule with a new time");
        }
        s.setEnabled(enabled);
        s.setChangedBy(by);
        s.setChangedAt(nextChangeStamp(s.getChangedAt()));
        s.setNextDueAt(nextDue);
        store.saveSchedules();
        return s;
    }

    public synchronized SchedulerSchedule deleteSchedule(String id) {
        SchedulerSchedule s = store.schedules().remove(id);
        if (s == null) throw new Refused("no schedule " + id);
        store.saveSchedules();
        return s;
    }

    // ==================== time passing ====================

    /**
     * Move time on: timed schedules that are due become open items (or Expired, "missed", when the server was down
     * past their window), and open items past their expiry close Expired. Items due in one tick share a pop-up.
     * Returns the items opened.
     */
    public synchronized List<OpenItem> tick() {
        long now = clock.getAsLong();
        List<OpenItem> opened = new ArrayList<>();
        boolean paused = store.state().isPaused();
        retryPending();
        for (SchedulerSchedule s : store.schedules().values()) {
            if (!s.isEnabled() || s.getTrigger() == TriggerType.AFTER_RESTART || s.getNextDueAt() <= 0 || s.getNextDueAt() > now) {
                continue;
            }
            try {
                OpenItem item = occur(s, now, paused);
                if (item != null) opened.add(item);
            } catch (RuntimeException e) {
                // one bad schedule must not stop the others or the expiry pass; it is retried next tick
                log.error("scheduler: schedule {} could not be processed", s.getId(), e);
            }
        }
        boolean openChanged = false;
        for (OpenItem item : new ArrayList<>(store.open().values())) {
            if (!item.isRunning() && item.getPendingOutcome() == null && now >= item.getExpiresAt()) {
                try {
                    close(item, OutcomeState.EXPIRED, "scheduler", "expired: nobody approved or declined it", 0);
                } catch (RuntimeException e) {
                    log.error("scheduler: item {} could not be expired", item.getId(), e);
                }
            }
        }
        return opened;
    }

    /**
     * Handle a schedule's due occurrence. Each occurrence has a fixed id (schedule@due) and is recorded or opened
     * before the schedule is moved on, so a stop in between never loses it and never handles it twice.
     */
    private OpenItem occur(SchedulerSchedule s, long now, boolean paused) {
        long due = s.getNextDueAt();
        SchedulerAction a = store.actions().get(s.getActionId());
        if (a == null || a.isRetired()) {
            s.setEnabled(false);
            store.saveSchedules();
            return null;
        }
        if (s.getTrigger() == TriggerType.DAILY) {
            // a late tick (suspended process) still offers the latest occurrence; the ones before it are missed
            long latest = due;
            for (long next = nextDaily(s, latest); next <= now; next = nextDaily(s, latest)) latest = next;
            if (latest != due) {
                if (due > s.getLastFiredDue() && !handled(occurrenceId(s, due))) {
                    close(itemFor(a, s, due, due + expiryMillis(s), null, 0), OutcomeState.EXPIRED, "scheduler",
                            "missed: the scheduler did not run at the due time", 0);
                }
                due = latest;
            }
        }
        long expires = due + expiryMillis(s);
        OpenItem opened = null;
        if (due > s.getLastFiredDue() && !handled(occurrenceId(s, due))) {
            OpenItem item = itemFor(a, s, due, expires, null, 0);
            item.setId(occurrenceId(s, due));
            if (paused) {
                close(item, OutcomeState.EXPIRED, "scheduler", "skipped: the scheduler was paused", 0);
            } else if (now >= expires) {
                close(item, OutcomeState.EXPIRED, "scheduler", "missed: the server was not running at the due time", 0);
            } else if (hasOpenFor(s.getId())) {
                close(item, OutcomeState.EXPIRED, "scheduler", "missed: the previous occurrence was still open", 0);
            } else if (!allowedHere(a)) {
                close(item, OutcomeState.FAILED, "scheduler", "not allowed in environment " + settings.environment(), 0);
            } else {
                store.open().put(item.getId(), item);
                store.saveOpen();
                opened = item;
            }
        }
        s.setLastFiredDue(Math.max(s.getLastFiredDue(), due));
        advance(s, now);
        store.saveSchedules();
        return opened;
    }

    private static String occurrenceId(SchedulerSchedule s, long due) {
        return s.getId() + "@" + due;
    }

    private boolean handled(String itemId) {
        return store.open().containsKey(itemId) || recorded(itemId);
    }

    /** Finish closes whose recording failed earlier (disk full, for example). */
    private void retryPending() {
        for (OpenItem item : new ArrayList<>(store.open().values())) {
            if (item.getPendingOutcome() != null) {
                try {
                    finish(item, item.getPendingOutcome());
                } catch (RuntimeException e) {
                    log.error("scheduler: outcome of {} still not recorded", item.getId(), e);
                }
            }
        }
    }

    /** Close what a stop left behind, per the restart table in the class comment. */
    public synchronized void recoverAfterStart() {
        long now = clock.getAsLong();
        boolean changed = false;
        for (OpenItem item : new ArrayList<>(store.open().values())) {
            if (item.getPendingOutcome() != null) {
                // decided before the stop: finish recording it (history is written once)
                finish(item, item.getPendingOutcome());
            } else if (recorded(item.getId())) {
                store.open().remove(item.getId());
                changed = true;
            } else if (item.isRunning()) {
                close(item, OutcomeState.FAILED, "scheduler", "interrupted by restart: the result is unknown and it was not re-run", 0);
            } else if (item.getRestartSetId() != null) {
                close(item, OutcomeState.EXPIRED, "scheduler", "superseded: the server restarted before a decision", 0);
            } else if (now >= item.getExpiresAt()) {
                close(item, OutcomeState.EXPIRED, "scheduler", "expired: the server was down", 0);
            }
        }
        if (changed) store.saveOpen();
        boolean schedulesChanged = false;
        for (SchedulerSchedule s : store.schedules().values()) {
            if (s.isEnabled() && s.getTrigger() == TriggerType.DAILY && s.getNextDueAt() > 0
                    && now >= s.getNextDueAt() + expiryMillis(s)) {
                // no backfill: skip to the next occurrence still in reach (tick logs the latest missed one)
                long latestMissed = s.getNextDueAt();
                while (true) {
                    long next = nextDaily(s, latestMissed);
                    if (next > now) break;
                    latestMissed = next;
                }
                s.setNextDueAt(latestMissed);
                schedulesChanged = true;
            }
        }
        if (schedulesChanged) store.saveSchedules();
    }

    // ==================== restart sets ====================

    /** The restart set this start would offer: enabled after-restart schedules in restart order. */
    public synchronized List<SchedulerSchedule> restartSchedules() {
        return store.schedules().values().stream()
                .filter(s -> s.isEnabled() && s.getTrigger() == TriggerType.AFTER_RESTART)
                .sorted(Comparator.comparingInt(SchedulerSchedule::getRestartOrder).thenComparing(SchedulerSchedule::getId))
                .toList();
    }

    /** Commands the restart set needs, so the service can wait until the server has registered them. */
    public synchronized List<String> restartCommands() {
        List<String> out = new ArrayList<>();
        for (SchedulerSchedule s : restartSchedules()) {
            SchedulerAction a = store.actions().get(s.getActionId());
            if (a != null && !a.isRetired()) out.add(a.getCommand());
        }
        return out;
    }

    /**
     * Offer the restart set as open items sharing one set id, expiring {@code restartExpiryMinutes} after now.
     * Refused while an earlier set still has open items. Returns the set id, or null when there is nothing to offer.
     */
    public synchronized String offerRestartSet(String reason) {
        if (store.state().isPaused()) throw new Refused("the scheduler is paused");
        boolean setOpen = store.open().values().stream().anyMatch(i -> i.getRestartSetId() != null);
        if (setOpen) throw new Refused("a restart set is already open; decide it first");
        List<SchedulerSchedule> schedules = restartSchedules();
        if (schedules.isEmpty()) return null;
        long now = clock.getAsLong();
        String setId = "restart-" + newId();
        int sequence = 0; // a strict order even when schedules share a restart order
        for (SchedulerSchedule s : schedules) {
            SchedulerAction a = store.actions().get(s.getActionId());
            if (a == null || a.isRetired()) continue;
            long expires = now + (s.getExpiryMinutes() > 0 ? s.getExpiryMinutes() : settings.restartExpiryMinutes()) * 60_000L;
            OpenItem item = itemFor(a, s, now, expires, setId, ++sequence);
            item.setDescription((a.getDescription() == null ? "" : a.getDescription() + " ") + "(" + reason + ")");
            if (!allowedHere(a)) {
                close(item, OutcomeState.FAILED, "scheduler", "not allowed in environment " + settings.environment(), 0);
                continue;
            }
            store.open().put(item.getId(), item);
        }
        store.saveOpen();
        return setId;
    }

    // ==================== decisions ====================

    /**
     * Approve and run one open item. Refused (nothing changes) if it is no longer open, expired, running, or an
     * earlier item of its restart set is still open. The argument re-check runs outside the lock (it may run
     * choices commands); a lookup that fails refuses the approval and leaves the item open to try again.
     */
    public Outcome approve(String itemId, String by) {
        OpenItem item;
        synchronized (this) {
            item = approvable(itemId);
        }
        String invalid;
        try {
            invalid = revalidate(item);
        } catch (Refused r) {
            throw new Refused("not run, try again: " + r.getMessage());
        }
        synchronized (this) {
            item = approvable(itemId); // decided, expired or approved while the arguments were checked
            if (invalid != null) {
                close(item, OutcomeState.FAILED, by, "not run: " + invalid, 0);
                throw new Refused("not run: " + invalid);
            }
            item.setRunning(true);
            store.saveOpen();
        }
        return execute(item, by);
    }

    /** The open item if it may be approved now, else refused (an expired one is closed). Call under the lock. */
    private OpenItem approvable(String itemId) {
        if (store.state().isPaused()) throw new Refused("the scheduler is paused");
        OpenItem item = store.open().get(itemId);
        if (item == null) throw new Refused(alreadyDecided(itemId));
        if (item.isRunning() || item.getPendingOutcome() != null) throw new Refused("already approved: it is running");
        if (clock.getAsLong() >= item.getExpiresAt()) {
            close(item, OutcomeState.EXPIRED, "scheduler", "expired before the approval arrived", 0);
            throw new Refused("expired: it can no longer be approved");
        }
        SchedulerAction a = store.actions().get(item.getActionId());
        if (a != null && !allowedHere(a)) {
            throw new Refused("not allowed in environment " + settings.environment());
        }
        String busy = gateway.busyReason();
        if (busy != null) throw new Refused("not run, try again: " + busy);
        if (item.getRestartSetId() != null) {
            for (OpenItem other : store.open().values()) {
                if (item.getRestartSetId().equals(other.getRestartSetId()) && other.getRestartOrder() < item.getRestartOrder()) {
                    throw new Refused("restart actions run in order: decide '" + other.getTitle() + "' first");
                }
            }
        }
        return item;
    }

    public synchronized Outcome decline(String itemId, String by) {
        OpenItem item = store.open().get(itemId);
        if (item == null) throw new Refused(alreadyDecided(itemId));
        if (item.isRunning() || item.getPendingOutcome() != null) throw new Refused("already approved: it is running");
        return close(item, OutcomeState.DECLINED, by, "declined", 0);
    }

    /** Approve a restart set's open items in order, stopping at the first that does not run OK. */
    public List<Outcome> approveAll(String setId, String by) {
        List<Outcome> out = new ArrayList<>();
        for (OpenItem item : setItems(setId)) {
            Outcome o;
            try {
                o = approve(item.getId(), by);
            } catch (Refused r) {
                Outcome refused = new Outcome();
                refused.setItemId(item.getId());
                refused.setTitle(item.getTitle());
                refused.setState(OutcomeState.FAILED);
                refused.setDetail(r.getMessage());
                out.add(refused);
                break;
            }
            out.add(o);
            if (o.getState() != OutcomeState.RAN_OK) break;
        }
        return out;
    }

    public List<Outcome> declineAll(String setId, String by) {
        List<Outcome> out = new ArrayList<>();
        for (OpenItem item : setItems(setId)) {
            try {
                out.add(decline(item.getId(), by));
            } catch (Refused ignored) {
                // decided meanwhile
            }
        }
        return out;
    }

    private synchronized List<OpenItem> setItems(String setId) {
        List<OpenItem> items = store.open().values().stream()
                .filter(i -> Objects.equals(setId, i.getRestartSetId()))
                .sorted(Comparator.comparingInt(OpenItem::getRestartOrder))
                .toList();
        if (items.isEmpty()) throw new Refused("no open items in " + setId);
        return items;
    }

    // ==================== state ====================

    public synchronized void setPaused(boolean paused) {
        if (!paused && store.state().isPaused()) {
            tick(); // still paused: anything already due is recorded as skipped, not opened late
        }
        store.state().setPaused(paused);
        store.saveState();
    }

    public synchronized boolean isPaused() {
        return store.state().isPaused();
    }

    public synchronized void acknowledgeExpired() {
        store.state().setExpiredAcknowledgedUpTo(clock.getAsLong());
        store.saveState();
    }

    public synchronized List<SchedulerAction> actions() {
        return new ArrayList<>(store.actions().values());
    }

    public synchronized List<SchedulerSchedule> schedules() {
        return new ArrayList<>(store.schedules().values());
    }

    /** Open items, oldest due first. */
    public synchronized List<OpenItem> open() {
        return store.open().values().stream().sorted(Comparator.comparingLong(OpenItem::getDueAt)).toList();
    }

    /** Outcomes decided in [from, to], newest first. */
    public synchronized List<Outcome> history(long from, long to) {
        List<Outcome> out = new ArrayList<>();
        for (Outcome o : store.history()) {
            if (o.getDecidedAt() >= from && o.getDecidedAt() <= to) out.add(o);
        }
        out.sort(Comparator.comparingLong(Outcome::getDecidedAt).reversed());
        return out;
    }

    /** Counts since {@code since} by state, open count, unacknowledged expired, and the latest outcomes. */
    public synchronized Map<String, Object> summary(long since, int latest) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (OutcomeState st : OutcomeState.values()) counts.put(st.name(), 0);
        int unacknowledgedExpired = 0;
        for (Outcome o : store.history()) {
            if (o.getDecidedAt() >= since) counts.merge(o.getState().name(), 1, Integer::sum);
            if (o.getState() == OutcomeState.EXPIRED && o.getDecidedAt() > store.state().getExpiredAcknowledgedUpTo()) {
                unacknowledgedExpired++;
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("open", store.open().size());
        m.put("paused", store.state().isPaused());
        m.put("today", counts);
        m.put("unacknowledgedExpired", unacknowledgedExpired);
        List<Outcome> h = new ArrayList<>(store.history());
        h.sort(Comparator.comparingLong(Outcome::getDecidedAt).reversed());
        m.put("latest", h.subList(0, Math.min(latest, h.size())));
        m.put("environment", settings.environment());
        return m;
    }

    /** The server's live commands that match the catalogue, with each one's placeholders and argument specs. */
    public List<Map<String, Object>> liveCatalogue() {
        Collection<String> live = gateway.liveCommands();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Catalogue.Match m : catalogue.matches(live.stream().sorted().toList())) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("command", m.command());
            e.put("pattern", m.entry().getCommand());
            e.put("title", m.entry().getTitle());
            e.put("description", m.entry().getDescription());
            e.put("params", m.params());
            e.put("arguments", m.entry().getArguments());
            out.add(e);
        }
        return out;
    }

    /** The live choices for one CHOICE argument of a command. */
    public List<List<String>> choices(String command, String argName) {
        Catalogue.Match match = liveMatch(command);
        CatalogueArg arg = match.entry().getArguments().stream()
                .filter(a -> Objects.equals(a.getName(), argName)).findFirst()
                .orElseThrow(() -> new Refused(command + " has no argument " + argName));
        if (arg.getType() != CatalogueArg.Type.CHOICE) throw new Refused(argName + " is not a choice argument");
        return choices(match, arg);
    }

    // ==================== internals ====================

    private Outcome execute(OpenItem item, String by) {
        long start = clock.getAsLong();
        CommandResult r;
        if (!gateway.liveCommands().contains(item.getCommand())) {
            r = new CommandResult(false, "command not registered on this server: " + item.getCommand());
        } else if (gateway.busyReason() != null) {
            r = new CommandResult(false, "not run: " + gateway.busyReason());
        } else {
            try {
                r = gateway.run(item.getCommand(), item.getArguments());
            } catch (RuntimeException e) {
                r = new CommandResult(false, "error: " + e);
            }
        }
        synchronized (this) {
            return close(item, r.ok() ? OutcomeState.RAN_OK : OutcomeState.FAILED, by, r.output(),
                    Math.max(0, clock.getAsLong() - start));
        }
    }

    private Outcome close(OpenItem item, OutcomeState state, String by, String detail, long durationMillis) {
        Outcome o = new Outcome();
        o.setItemId(item.getId());
        o.setScheduleId(item.getScheduleId());
        o.setActionId(item.getActionId());
        o.setTitle(item.getTitle());
        o.setCommand(item.getCommand());
        o.setArguments(new ArrayList<>(item.getArguments()));
        o.setRestartSetId(item.getRestartSetId());
        o.setDueAt(item.getDueAt());
        o.setState(state);
        o.setDecidedBy(by);
        o.setDecidedAt(clock.getAsLong());
        o.setDetail(detail);
        o.setDurationMillis(durationMillis);
        finish(item, o);
        return o;
    }

    /**
     * Close durably: an open item first saves its outcome on itself, then the outcome is recorded (outcome log,
     * then history), then the item is removed. A stop at any point is finished by {@link #recoverAfterStart()};
     * a failure to record leaves the outcome pending and the next tick retries. History holds each item once;
     * after a stop between the two writes the outcome log can repeat a record.
     */
    private void finish(OpenItem item, Outcome o) {
        boolean open = store.open().containsKey(item.getId());
        if (open && item.getPendingOutcome() == null) {
            item.setPendingOutcome(o);
            store.saveOpen();
        }
        if (!recorded(o.getItemId())) {
            outcomeLog.append(o);
            store.appendHistory(o);
        }
        if (open) {
            store.open().remove(item.getId());
            store.saveOpen();
        }
    }

    private OpenItem itemFor(SchedulerAction a, SchedulerSchedule s, long due, long expires, String setId, int order) {
        OpenItem item = new OpenItem();
        item.setId(newId());
        item.setScheduleId(s == null ? null : s.getId());
        item.setActionId(a.getId());
        item.setTitle(a.getTitle());
        item.setDescription(a.getDescription());
        item.setCommand(a.getCommand());
        item.setArguments(new ArrayList<>(a.getArguments()));
        item.setDueAt(due);
        item.setExpiresAt(expires);
        item.setRestartSetId(setId);
        item.setRestartOrder(order);
        return item;
    }

    /** Re-check an item against the server as it is now: command present, arguments still valid. */
    private String revalidate(OpenItem item) {
        if (!gateway.liveCommands().contains(item.getCommand())) {
            return "command not registered on this server: " + item.getCommand();
        }
        Catalogue.Match match = catalogue.find(item.getCommand());
        if (match == null) return item.getCommand() + " is no longer in the catalogue";
        try {
            return Catalogue.validate(match, item.getArguments(), arg -> choices(match, arg));
        } catch (Refused r) {
            throw r;
        } catch (RuntimeException e) {
            throw new Refused("could not check the arguments: " + e);
        }
    }

    private Catalogue.Match liveMatch(String command) {
        if (command == null || command.isBlank()) throw new Refused("no command given");
        Catalogue.Match match = catalogue.find(command);
        if (match == null) throw new Refused(command + " is not in the catalogue of schedulable commands");
        checkLive(command);
        return match;
    }

    private void checkLive(String command) {
        if (!gateway.liveCommands().contains(command)) throw new Refused(command + " is not registered on this server");
    }

    private List<List<String>> choices(Catalogue.Match match, CatalogueArg arg) {
        try {
            return Catalogue.choices(match, arg, cmd -> {
                if (!gateway.liveCommands().contains(cmd)) throw new Refused("choices command " + cmd + " is not registered on this server");
                CommandResult r = gateway.lookup(cmd);
                if (!r.ok()) throw new Refused("choices command " + cmd + " failed: " + r.output());
                return r.output();
            });
        } catch (java.io.IOException e) {
            throw new Refused("choices for " + arg.getName() + " are not valid JSON: " + e.getMessage());
        } catch (Refused r) {
            throw r;
        } catch (RuntimeException e) {
            throw new Refused("choices for " + arg.getName() + " could not be read: " + e.getMessage());
        }
    }

    private void checkEnvironment(SchedulerAction a) {
        if (!allowedHere(a)) {
            throw new Refused("action '" + a.getTitle() + "' is not allowed in environment " + settings.environment()
                    + (settings.isProduction() ? " (production must be listed explicitly)" : ""));
        }
    }

    /** Listed environments win; an empty list means every environment except production. */
    boolean allowedHere(SchedulerAction a) {
        if (a.getEnvironments() == null || a.getEnvironments().isEmpty()) return !settings.isProduction();
        return a.getEnvironments().stream().anyMatch(e -> e != null && e.equalsIgnoreCase(settings.environment()));
    }

    private SchedulerAction action(String id) {
        SchedulerAction a = id == null ? null : store.actions().get(id);
        if (a == null) throw new Refused("no action " + id);
        return a;
    }

    private boolean hasOpenFor(String scheduleId) {
        return store.open().values().stream().anyMatch(i -> scheduleId.equals(i.getScheduleId()));
    }

    private boolean recorded(String itemId) {
        for (Outcome o : store.history()) {
            if (itemId.equals(o.getItemId())) return true;
        }
        return false;
    }

    private String alreadyDecided(String itemId) {
        for (int i = store.history().size() - 1; i >= 0; i--) {
            Outcome o = store.history().get(i);
            if (itemId.equals(o.getItemId())) {
                return switch (o.getState()) {
                    case RAN_OK, FAILED -> "already approved by " + o.getDecidedBy();
                    case DECLINED -> "already declined by " + o.getDecidedBy();
                    case EXPIRED -> "already expired";
                };
            }
        }
        return "no open item " + itemId;
    }

    private long expiryMillis(SchedulerSchedule s) {
        int minutes = s.getExpiryMinutes() > 0 ? s.getExpiryMinutes()
                : (s.getTrigger() == TriggerType.AFTER_RESTART ? settings.restartExpiryMinutes() : settings.timedExpiryMinutes());
        return minutes * 60_000L;
    }

    private ZoneId zone(SchedulerSchedule s) {
        try {
            return ZoneId.of(s.getZone() == null || s.getZone().isBlank() ? settings.defaultZone() : s.getZone());
        } catch (RuntimeException e) {
            throw new Refused("unknown time zone '" + s.getZone() + "'");
        }
    }

    /** First due time from now: ONCE = its date-time; DAILY = the next occurrence (today's if not yet past). */
    long firstDue(SchedulerSchedule s, long now) {
        try {
            return switch (s.getTrigger()) {
                case ONCE -> instantOf(LocalDateTime.parse(s.getAt()), zone(s));
                case DAILY -> nextDaily(s, now - 1);
                case AFTER_RESTART -> 0;
            };
        } catch (Refused r) {
            throw r;
        } catch (RuntimeException e) {
            throw new Refused("bad time '" + s.getAt() + "' for " + s.getTrigger()
                    + (s.getTrigger() == TriggerType.ONCE ? " (expected 2026-10-15T09:00)" : " (expected 09:00)"));
        }
    }

    /**
     * The next daily occurrence strictly after {@code after}. A local time inside a DST gap moves to the next
     * valid instant; one inside an overlap fires once, at the earlier offset.
     */
    long nextDaily(SchedulerSchedule s, long after) {
        ZoneId zone = zone(s);
        LocalTime time = LocalTime.parse(s.getAt());
        LocalDate day = Instant.ofEpochMilli(after).atZone(zone).toLocalDate();
        for (int i = 0; i < 3; i++) {
            long t = instantOf(LocalDateTime.of(day.plusDays(i), time), zone);
            if (t > after) return t;
        }
        throw new IllegalStateException("no daily occurrence found");
    }

    /** A local time in a zone: in a DST gap, the first valid instant after it; in an overlap, the earlier one. */
    static long instantOf(LocalDateTime local, ZoneId zone) {
        java.time.zone.ZoneRules rules = zone.getRules();
        if (rules.getValidOffsets(local).isEmpty()) {
            return rules.getTransition(local).getInstant().toEpochMilli();
        }
        return ZonedDateTime.ofLocal(local, zone, null).toInstant().toEpochMilli();
    }

    private void advance(SchedulerSchedule s, long now) {
        if (s.getTrigger() == TriggerType.DAILY) {
            s.setNextDueAt(nextDaily(s, Math.max(now, Math.max(s.getNextDueAt(), s.getLastFiredDue()))));
        } else {
            s.setNextDueAt(0);
            s.setEnabled(false);
        }
    }

    private long nextChangeStamp(long previous) {
        return Math.max(clock.getAsLong(), previous + 1);
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
