package com.fluxtion.server.plugin.scheduler;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static com.fluxtion.server.plugin.scheduler.SchedulerEngineTest.*;
import static org.junit.jupiter.api.Assertions.*;

class SchedulerDurabilityTest {
    @TempDir Path dir;

    static class Stop extends Error { }

    static class FailingStore extends SchedulerStore {
        final Path dir;
        int failSchedules, failState, failPending, failRemove, failActions;
        boolean partialHistory, completeHistoryThenFail;

        FailingStore(Path dir, int cap) {
            super(dir, cap);
            this.dir = dir;
            load();
        }

        @Override public void saveSchedules() {
            if (failSchedules-- > 0) throw failure();
            super.saveSchedules();
        }

        @Override public void saveActions() {
            if (failActions-- > 0) throw failure();
            super.saveActions();
        }

        @Override public void saveState() {
            if (failState-- > 0) throw failure();
            super.saveState();
        }

        @Override public void saveOpen() {
            if (failPending > 0 && open().values().stream().anyMatch(i -> i.getPendingOutcome() != null)) {
                failPending--;
                throw failure();
            }
            if (failRemove > 0 && open().isEmpty()) {
                failRemove--;
                throw failure();
            }
            super.saveOpen();
        }

        @Override protected void writeHistoryLine(byte[] bytes) throws IOException {
            if (partialHistory) {
                partialHistory = false;
                Files.write(dir.resolve("history.jsonl"), Arrays.copyOf(bytes, 12),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                throw new IOException("disk full after a partial append");
            }
            super.writeHistoryLine(bytes);
            if (completeHistoryThenFail) {
                completeHistoryThenFail = false;
                throw new IOException("flush failed after a complete append");
            }
        }

        static UncheckedIOException failure() { return new UncheckedIOException(new IOException("injected write failure")); }
    }

    class Fixture {
        final Path state, logFile;
        final FailingStore store;
        final AtomicLong now = new AtomicLong(T0);
        final FakeServer server = new FakeServer();
        SchedulerEngine engine;

        Fixture(String name, int cap) throws Exception {
            state = dir.resolve(name).resolve("state");
            logFile = dir.resolve(name).resolve("out.yaml");
            store = new FailingStore(state, cap);
            engine = engine(store, log());
        }

        OutcomeLog log() { return new OutcomeLog(logFile, 1_000_000, 3); }

        SchedulerEngine engine(SchedulerStore store, OutcomeLog log) throws Exception {
            return new SchedulerEngine(store, log, Catalogue.parse(CATALOGUE), server, now::get,
                    new SchedulerEngine.Settings("uat", "UTC", 15, 30));
        }

        SchedulerAction action() { return engine.saveAction(SchedulerEngineTest.action("feedA.connect"), null, "support"); }

        SchedulerSchedule schedule(SchedulerAction action) {
            SchedulerSchedule s = engine.saveSchedule(once(action, "2026-10-14T09:00"), "trader");
            now.set(s.getNextDueAt());
            return s;
        }

        OpenItem open() { schedule(action()); return engine.tick().getFirst(); }

        SchedulerStore reload(int cap) { SchedulerStore s = new SchedulerStore(state, cap); s.load(); return s; }
    }

    @Test void failedScheduleAdvanceCannotReplayAfterHistoryEviction() throws Exception {
        Fixture f = new Fixture("advance", 2);
        SchedulerAction a = f.action();
        SchedulerSchedule s = f.schedule(a);
        long due = s.getNextDueAt();
        f.store.failSchedules = 1;
        f.engine.tick();
        OpenItem item = f.engine.open().getFirst();
        assertTrue(s.isEnabled(), "failed advance is rolled back for retry");
        f.engine.approve(item.getId(), "trader");
        f.engine.testAction(a.getId(), "support");
        f.engine.testAction(a.getId(), "support");
        assertTrue(f.store.history().stream().noneMatch(o -> item.getId().equals(o.getItemId())));
        SchedulerStore reloaded = f.reload(2);
        assertEquals(due, reloaded.schedules().get(s.getId()).getLastFiredDue());
        SchedulerEngine restarted = f.engine(reloaded, f.log());
        restarted.recoverAfterStart();
        assertTrue(restarted.tick().isEmpty());
        assertTrue(restarted.open().isEmpty());
        assertEquals(3, f.server.runs.size(), "no second execution of the original item");
    }

    @Test void approvalWaitsForDurableReplayProtection() throws Exception {
        Fixture f = new Fixture("approval", 10);
        OpenItem item = f.open();
        f.store.failSchedules = 1;
        assertThrows(UncheckedIOException.class, () -> f.engine.approve(item.getId(), "trader"));
        assertFalse(item.isRunning());
        assertTrue(f.server.runs.isEmpty());
        assertEquals(OutcomeState.RAN_OK, f.engine.approve(item.getId(), "trader").getState());
    }

    @Test void failedScheduleEditsLeaveLiveAndSavedStateUnchanged() throws Exception {
        Fixture f = new Fixture("edits", 10);
        SchedulerAction a = f.action();
        SchedulerSchedule s = f.engine.saveSchedule(once(a, "2026-10-14T09:00"), "trader");
        long changedAt = s.getChangedAt();
        long due = s.getNextDueAt();
        SchedulerSchedule edit = once(a, "2026-10-14T10:00");
        edit.setId(s.getId());
        f.store.failSchedules = 1;
        assertThrows(UncheckedIOException.class, () -> f.engine.saveSchedule(edit, changedAt, "other"));
        assertEquals("2026-10-14T09:00", f.engine.schedules().getFirst().getAt());
        f.store.failSchedules = 1;
        assertThrows(UncheckedIOException.class, () -> f.engine.enableSchedule(s.getId(), false, "other"));
        assertTrue(s.isEnabled());
        assertEquals(due, s.getNextDueAt());
        assertEquals(changedAt, s.getChangedAt());
        f.store.failSchedules = 1;
        assertThrows(UncheckedIOException.class, () -> f.engine.deleteSchedule(s.getId()));
        assertEquals(1, f.engine.schedules().size());
        f.store.failSchedules = 1;
        assertThrows(UncheckedIOException.class, () -> f.engine.saveSchedule(once(a, "2026-10-14T11:00"), "other"));
        assertEquals(1, f.engine.schedules().size());
        assertEquals(due, f.reload(10).schedules().get(s.getId()).getNextDueAt());
    }

    @Test void completedResultSurvivesFailedPendingSave() throws Exception {
        Fixture f = new Fixture("pending", 10);
        OpenItem item = f.open();
        f.store.failPending = 2;
        assertThrows(UncheckedIOException.class, () -> f.engine.approve(item.getId(), "trader"));
        assertEquals(OutcomeState.RAN_OK, item.getPendingOutcome().getState());
        f.engine.tick(); // a second failed pending save must not start recording
        assertFalse(Files.exists(f.logFile));
        assertTrue(f.store.history().isEmpty());
        f.now.addAndGet(60 * MIN);
        f.engine.tick();
        SchedulerStore reloaded = f.reload(10);
        assertTrue(reloaded.open().isEmpty());
        assertEquals(OutcomeState.RAN_OK, reloaded.history().getFirst().getState());
        assertEquals(1, f.server.runs.size());
    }

    @Test void recoveryRepairsTornAuditRecordAndUtf8() throws Exception {
        for (boolean utf8 : List.of(false, true)) {
            Fixture f = new Fixture("torn-" + utf8, 10);
            OpenItem item = f.open();
            f.engine = f.engine(f.store, new OutcomeLog(f.logFile, 1_000_000, 3) {
                @Override public synchronized void append(Outcome o) {
                    super.append(o);
                    try {
                        byte[] bytes = Files.readAllBytes(f.logFile);
                        int cut;
                        if (utf8) {
                            cut = 0;
                            while (bytes[cut] != (byte) 0xE2) cut++;
                            cut++;
                        } else {
                            cut = new String(bytes, StandardCharsets.UTF_8).indexOf(", detail:") + 1;
                        }
                        Files.write(f.logFile, Arrays.copyOf(bytes, cut));
                    } catch (IOException e) { throw new UncheckedIOException(e); }
                    throw new Stop();
                }
            });
            assertThrows(Stop.class, () -> f.engine.decline(item.getId(), utf8 ? "trader€" : "trader"));
            SchedulerStore reloaded = f.reload(10);
            f.engine(reloaded, f.log()).recoverAfterStart();
            assertTrue(reloaded.open().isEmpty());
            assertEquals(OutcomeState.DECLINED, reloaded.history().getFirst().getState());
            assertEquals(1, records(f.logFile).size());
            assertEquals(1, countItem(f.logFile, item.getId()));
        }
    }

    @Test void auditTailRepairKeepsPreviousCompleteRecords() throws Exception {
        Fixture f = new Fixture("audit-tail", 10);
        f.engine.decline(f.open().getId(), "trader");
        String complete = Files.readString(f.logFile);
        Files.write(f.logFile, "---\neventLogRecord:\n    eventToString: \"caf".getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
        Files.write(f.logFile, new byte[] {(byte) 0xC3}, StandardOpenOption.APPEND);
        assertTrue(f.log().contains(f.store.history().getFirst().getItemId()));
        assertEquals(complete, Files.readString(f.logFile));
        assertEquals(1, records(f.logFile).size());
    }

    @Test void partialHistoryAppendRetriesWithoutLosingTheOutcome() throws Exception {
        checkFailedHistoryAppend(false);
    }

    @Test void completeHistoryAppendWithFlushFailureDoesNotDuplicate() throws Exception {
        checkFailedHistoryAppend(true);
    }

    private void checkFailedHistoryAppend(boolean complete) throws Exception {
        Fixture f = new Fixture("history-" + complete, 10);
        OpenItem item = f.open();
        f.store.partialHistory = !complete;
        f.store.completeHistoryThenFail = complete;
        assertThrows(UncheckedIOException.class, () -> f.engine.decline(item.getId(), "trader"));
        assertTrue(f.store.history().isEmpty());
        assertNotNull(item.getPendingOutcome());
        f.engine.tick();
        SchedulerStore reloaded = f.reload(10);
        assertTrue(reloaded.open().isEmpty());
        assertEquals(List.of(item.getId()), reloaded.history().stream().map(Outcome::getItemId).toList());
        assertEquals(1, countItem(f.logFile, item.getId()));
        assertEquals(0, reloaded.skippedHistoryLines());
    }

    @Test void appendRepairsAnAlreadyTornHistoryTailWithoutRestart() throws Exception {
        Fixture f = new Fixture("history-tail", 10);
        f.engine.decline(f.open().getId(), "trader");
        Files.writeString(f.state.resolve("history.jsonl"), "{\"itemId\":\"", StandardOpenOption.APPEND);
        f.engine.testAction(f.store.actions().values().iterator().next().getId(), "support");
        assertEquals(2, f.reload(10).history().size());
        assertEquals(0, f.reload(10).skippedHistoryLines());
    }

    @Test void skippedOccurrenceResumesAfterStopBetweenAuditAndHistory() throws Exception {
        Fixture f = new Fixture("skip", 10);
        SchedulerSchedule schedule = f.schedule(f.action());
        String id = schedule.getId() + "@" + schedule.getNextDueAt();
        f.engine.setPaused(true);
        f.engine = f.engine(f.store, new OutcomeLog(f.logFile, 1_000_000, 3) {
            @Override public synchronized void append(Outcome o) { super.append(o); throw new Stop(); }
        });
        assertThrows(Stop.class, () -> f.engine.tick());
        assertNotNull(f.reload(10).open().get(id).getPendingOutcome());
        SchedulerStore reloaded = f.reload(10);
        SchedulerEngine restarted = f.engine(reloaded, f.log());
        restarted.recoverAfterStart();
        restarted.tick();
        assertEquals(1, reloaded.history().size());
        assertEquals(1, countItem(f.logFile, id));
        assertTrue(reloaded.open().isEmpty());
    }

    @Test void lateDailyMissHasStableIdentityWhenRecordingRetries() throws Exception {
        Fixture f = new Fixture("daily", 10);
        SchedulerSchedule s = f.engine.saveSchedule(daily(f.action(), "09:00", "UTC"), "trader");
        long first = s.getNextDueAt();
        f.now.set(first + 24 * 60 * MIN);
        f.store.failPending = 1;
        f.engine.tick();
        assertEquals(s.getId() + "@" + first, f.engine.open().getFirst().getId());
        f.engine.tick();
        assertEquals(1, f.store.history().size());
        assertEquals(s.getId() + "@" + first, f.store.history().getFirst().getItemId());
        assertEquals(1, countItem(f.logFile, s.getId() + "@" + first));
    }

    @Test void failedPauseOrResumeLeavesThePreviousStateActive() throws Exception {
        Fixture f = new Fixture("pause", 10);
        SchedulerAction a = f.action();
        f.store.failState = 1;
        assertThrows(UncheckedIOException.class, () -> f.engine.setPaused(true));
        assertFalse(f.engine.isPaused());
        f.engine.setPaused(true);
        f.store.failState = 1;
        assertThrows(UncheckedIOException.class, () -> f.engine.setPaused(false));
        assertTrue(f.engine.isPaused());
        assertTrue(f.reload(10).state().isPaused());
        assertThrows(SchedulerEngine.Refused.class, () -> f.engine.testAction(a.getId(), "support"));
    }

    @Test void restartDeclinesAndBulkDeclinesRespectSequence() throws Exception {
        Fixture f = new Fixture("order", 10);
        SchedulerAction a = f.action();
        f.engine.saveSchedule(restart(a, 1), "trader");
        f.engine.saveSchedule(restart(a, 2), "trader");
        String set = f.engine.offerRestartSet("test");
        List<OpenItem> items = f.engine.open();
        OpenItem first = items.stream().filter(i -> i.getRestartOrder() == 1).findFirst().orElseThrow();
        OpenItem second = items.stream().filter(i -> i.getRestartOrder() == 2).findFirst().orElseThrow();
        assertThrows(SchedulerEngine.Refused.class, () -> f.engine.decline(second.getId(), "trader"));
        first.setRunning(true); // simulate a concurrent approval holding the head of this set
        assertTrue(f.engine.declineAll(set, "trader").isEmpty());
        assertEquals(2, f.engine.open().size());
        first.setRunning(false);
        assertEquals(2, f.engine.declineAll(set, "trader").size());
        assertTrue(f.engine.open().isEmpty());
    }

    @Test void removalRetryDoesNotRecordAgainAfterHistoryEvictionAndLogRolls() throws Exception {
        Fixture f = new Fixture("remove", 2);
        f.engine = f.engine(f.store, new OutcomeLog(f.logFile, 1, 3));
        OpenItem item = f.open();
        f.store.failRemove = 1;
        assertThrows(UncheckedIOException.class, () -> f.engine.decline(item.getId(), "trader"));
        assertEquals(1, f.engine.open().size());
        String action = item.getActionId();
        for (int i = 0; i < 3; i++) f.engine.testAction(action, "support");
        SchedulerStore reloaded = f.reload(2);
        assertTrue(reloaded.history().stream().noneMatch(o -> item.getId().equals(o.getItemId())));
        assertTrue(reloaded.open().get(item.getId()).isHistoryRecorded());
        f.engine(reloaded, new OutcomeLog(f.logFile, 1, 3)).recoverAfterStart();
        assertTrue(reloaded.open().isEmpty());
        assertTrue(reloaded.history().stream().noneMatch(o -> item.getId().equals(o.getItemId())));
        assertEquals(1, countItem(f.logFile.resolveSibling("out-3.yaml"), item.getId()));
        assertEquals(0, countItem(f.logFile, item.getId()));
    }

    @Test void anInterruptedTestIsRecoveredWithoutReexecution() throws Exception {
        Fixture f = new Fixture("test", 10);
        SchedulerAction a = f.action();
        f.server.answer = command -> { throw new Stop(); };
        assertThrows(Stop.class, () -> f.engine.testAction(a.getId(), "support"));
        SchedulerStore reloaded = f.reload(10);
        assertTrue(reloaded.open().values().iterator().next().isRunning());
        f.engine(reloaded, f.log()).recoverAfterStart();
        assertTrue(reloaded.open().isEmpty());
        assertEquals(OutcomeState.FAILED, reloaded.history().getFirst().getState());
        assertTrue(reloaded.history().getFirst().getDetail().contains("interrupted"));
    }

    @Test void restartSetIsCompleteWhenAnEnvironmentFailureCannotBeLogged() throws Exception {
        Fixture f = new Fixture("restart-log-failure", 10);
        SchedulerAction first = f.action();
        SchedulerAction denied = f.engine.saveAction(SchedulerEngineTest.action("pricer.setLogLevel", "INFO"), null, "support");
        SchedulerAction last = f.engine.saveAction(SchedulerEngineTest.action("strat.A.setMaxBookAge", "5"), null, "support");
        f.engine.saveSchedule(restart(first, 1), "trader");
        f.engine.saveSchedule(restart(denied, 2), "trader");
        f.engine.saveSchedule(restart(last, 3), "trader");
        SchedulerAction edit = SchedulerEngineTest.action(denied.getCommand(), "INFO");
        edit.setId(denied.getId());
        edit.setEnvironments(List.of("dev"));
        f.engine.saveAction(edit, denied.getChangedAt(), "support");
        f.engine = f.engine(f.store, new OutcomeLog(f.logFile, 1_000_000, 3) {
            boolean fail = true;
            @Override public synchronized void append(Outcome o) {
                if (fail) { fail = false; throw FailingStore.failure(); }
                super.append(o);
            }
        });
        String set = assertDoesNotThrow(() -> f.engine.offerRestartSet("test"));
        assertEquals(3, f.reload(10).open().size(), "all items are durable before any outcome is recorded");
        assertNotNull(f.engine.open().stream().filter(i -> denied.getId().equals(i.getActionId()))
                .findFirst().orElseThrow().getPendingOutcome());
        f.engine.tick();
        assertEquals(2, f.engine.open().size());
        assertTrue(f.engine.open().stream().anyMatch(i -> last.getId().equals(i.getActionId())));
        assertEquals(List.of(OutcomeState.RAN_OK, OutcomeState.RAN_OK),
                f.engine.approveAll(set, "trader").stream().map(Outcome::getState).toList());
        assertTrue(f.server.runs.stream().anyMatch(s -> s.startsWith(last.getCommand())));
    }

    @Test void restartSetInitialWriteFailureRollsBackEveryItem() throws Exception {
        Fixture f = new Fixture("restart-initial-failure", 10);
        SchedulerAction allowed = f.action();
        SchedulerAction denied = f.engine.saveAction(SchedulerEngineTest.action("strat.A.setMaxBookAge", "5"), null, "support");
        f.engine.saveSchedule(restart(allowed, 1), "trader");
        f.engine.saveSchedule(restart(denied, 2), "trader");
        SchedulerAction edit = SchedulerEngineTest.action(denied.getCommand(), "5");
        edit.setId(denied.getId());
        edit.setEnvironments(List.of("dev"));
        f.engine.saveAction(edit, denied.getChangedAt(), "support");
        f.store.failPending = 1;
        assertThrows(UncheckedIOException.class, () -> f.engine.offerRestartSet("test"));
        assertTrue(f.engine.open().isEmpty());
        assertTrue(f.reload(10).open().isEmpty());
        assertFalse(Files.exists(f.logFile));
        assertNotNull(f.engine.offerRestartSet("retry"));
        assertEquals(1, f.engine.open().size());
        assertEquals(OutcomeState.FAILED, f.store.history().getFirst().getState());
    }

    @Test void restartSetSurvivesAStopWhileRecordingAnEnvironmentFailure() throws Exception {
        Fixture f = new Fixture("restart-stop", 10);
        SchedulerAction a = f.action();
        SchedulerAction denied = f.engine.saveAction(SchedulerEngineTest.action("strat.A.setMaxBookAge", "5"), null, "support");
        f.engine.saveSchedule(restart(a, 1), "trader");
        f.engine.saveSchedule(restart(denied, 2), "trader");
        f.engine.saveSchedule(restart(a, 3), "trader");
        SchedulerAction edit = SchedulerEngineTest.action(denied.getCommand(), "5");
        edit.setId(denied.getId());
        edit.setEnvironments(List.of("dev"));
        f.engine.saveAction(edit, denied.getChangedAt(), "support");
        f.engine = f.engine(f.store, new OutcomeLog(f.logFile, 1_000_000, 3) {
            @Override public synchronized void append(Outcome o) { super.append(o); throw new Stop(); }
        });
        assertThrows(Stop.class, () -> f.engine.offerRestartSet("test"));
        SchedulerStore reloaded = f.reload(10);
        assertEquals(3, reloaded.open().size());
        String lastId = reloaded.open().values().stream().filter(i -> i.getRestartOrder() == 3)
                .findFirst().orElseThrow().getId();
        f.engine(reloaded, f.log()).recoverAfterStart();
        assertTrue(reloaded.open().isEmpty());
        assertEquals(3, reloaded.history().size());
        assertTrue(reloaded.history().stream().anyMatch(o -> o.getItemId().equals(lastId)
                && o.getState() == OutcomeState.EXPIRED));
        assertEquals(3, records(f.logFile).size());
    }

    @Test void recoveryContinuesAfterAnOutcomeWriteFailsAndTickCompletesBothItems() throws Exception {
        for (boolean persistent : List.of(false, true)) {
            Fixture f = new Fixture("recovery-" + persistent, 10);
            SchedulerAction a = f.action();
            f.engine.saveSchedule(once(a, "2026-10-14T09:00"), "trader");
            f.engine.saveSchedule(once(a, "2026-10-14T09:01"), "trader");
            f.now.set(T0 + 61 * MIN);
            assertEquals(2, f.engine.tick().size());
            f.now.addAndGet(60 * MIN);
            SchedulerStore reloaded = f.reload(10);
            SchedulerEngine recovering = f.engine(reloaded, new OutcomeLog(f.logFile, 1_000_000, 3) {
                boolean first = true;
                @Override public synchronized void append(Outcome o) {
                    if (persistent || first) { first = false; throw FailingStore.failure(); }
                    super.append(o);
                }
            });
            assertDoesNotThrow(recovering::recoverAfterStart);
            assertEquals(persistent ? 2 : 1, reloaded.open().size());
            assertTrue(reloaded.open().values().stream().allMatch(i -> i.getPendingOutcome() != null));
            assertEquals(persistent ? 0 : 1, reloaded.history().size(), "later items are still processed");
            f.engine(reloaded, f.log()).tick();
            assertTrue(f.reload(10).open().isEmpty());
            assertEquals(2, f.reload(10).history().size());
            assertEquals(2, records(f.logFile).size());
        }
    }

    @Test void failedActionCreateEditAndRetirementRestoreThePreviousState() throws Exception {
        Fixture f = new Fixture("action-rollback", 10);
        f.store.failActions = 1;
        assertThrows(UncheckedIOException.class, () -> f.action());
        assertTrue(f.engine.actions().isEmpty());
        SchedulerAction a = f.engine.saveAction(SchedulerEngineTest.action("strat.A.setMaxBookAge", "5"), null, "support");
        long stamp = a.getChangedAt();
        SchedulerAction edit = SchedulerEngineTest.action(a.getCommand(), "999999");
        edit.setId(a.getId());
        f.store.failActions = 1;
        assertThrows(UncheckedIOException.class, () -> f.engine.saveAction(edit, stamp, "other"));
        assertEquals(List.of("5"), f.engine.actions().getFirst().getArguments());
        assertEquals(stamp, f.engine.actions().getFirst().getChangedAt());
        assertEquals(List.of("5"), f.reload(10).actions().get(a.getId()).getArguments());
        assertDoesNotThrow(() -> f.engine.saveAction(edit, stamp, "other"));
        SchedulerAction current = f.engine.actions().getFirst();
        long editedStamp = current.getChangedAt();
        f.store.failActions = 1;
        assertThrows(UncheckedIOException.class, () -> f.engine.retireAction(a.getId(), "support"));
        assertFalse(current.isRetired());
        assertEquals(editedStamp, current.getChangedAt());
        assertFalse(f.reload(10).actions().get(a.getId()).isRetired());
    }

    @Test void retirementCommitsOnceAndRetriesScheduleCleanup() throws Exception {
        Fixture f = new Fixture("retirement-cleanup", 10);
        SchedulerAction a = f.action();
        f.engine.saveSchedule(once(a, "2026-10-14T09:00"), "trader");
        f.engine.saveSchedule(restart(a, 1), "trader");
        f.store.failSchedules = 1;
        assertTrue(assertDoesNotThrow(() -> f.engine.retireAction(a.getId(), "support")).isRetired());
        assertTrue(f.reload(10).actions().get(a.getId()).isRetired());
        assertTrue(f.engine.schedules().stream().allMatch(SchedulerSchedule::isEnabled), "failed cleanup is rolled back");
        assertNull(f.engine.offerRestartSet("cleanup pending"), "retired schedules do not create an empty restart set");
        f.engine.tick();
        assertTrue(f.engine.schedules().stream().noneMatch(SchedulerSchedule::isEnabled));
        assertTrue(f.reload(10).schedules().values().stream().noneMatch(SchedulerSchedule::isEnabled));
        assertTrue(f.server.runs.isEmpty());
    }

    @Test void failedDisablingOfAnOrphanedDueScheduleRetries() throws Exception {
        Fixture f = new Fixture("orphan-disable", 10);
        SchedulerAction a = f.action();
        SchedulerSchedule s = f.schedule(a);
        f.store.actions().remove(a.getId());
        f.store.saveActions();
        f.store.failSchedules = 1;
        f.engine.tick();
        assertTrue(s.isEnabled());
        assertTrue(f.reload(10).schedules().get(s.getId()).isEnabled());
        f.engine.tick();
        assertFalse(s.isEnabled());
        assertFalse(f.reload(10).schedules().get(s.getId()).isEnabled());
        assertTrue(f.server.runs.isEmpty());
    }

    @Test void supplementaryCharactersRoundTripAcrossTheYamlParserBufferBoundary() throws Exception {
        Path file = dir.resolve("emoji.yaml");
        Outcome o = new Outcome();
        o.setItemId("id");
        o.setState(OutcomeState.FAILED);
        o.setTitle("a".repeat(398) + "😀");
        o.setCommand("x".repeat(499));
        o.setDecidedBy("trader");
        o.setDetail("b".repeat(398) + "😀");
        new OutcomeLog(file, 1_000_000, 3).append(o);
        List<Map<String, Object>> parsed = records(file);
        assertEquals(1, parsed.size());
        Map<?, ?> record = (Map<?, ?>) parsed.getFirst().get("eventLogRecord");
        Map<?, ?> fields = (Map<?, ?>) ((Map<?, ?>) ((List<?>) record.get("nodeLogs")).getFirst()).get("scheduler");
        assertEquals(o.getTitle(), fields.get("title"));
        assertEquals(o.getDetail(), fields.get("detail"));
    }

    @Test void uncertainAuditWriteDeduplicatesAfterMoreThanOneLogRoll() throws Exception {
        Fixture f = new Fixture("uncertain-roll", 10);
        f.engine = f.engine(f.store, new OutcomeLog(f.logFile, 1, 3) {
            boolean first = true;
            @Override protected void writeRecord(byte[] bytes) throws IOException {
                super.writeRecord(bytes);
                if (first) { first = false; throw new IOException("flush result unknown"); }
            }
        });
        OpenItem item = f.open();
        assertThrows(UncheckedIOException.class, () -> f.engine.decline(item.getId(), "trader"));
        f.engine.testAction(item.getActionId(), "support");
        f.engine.testAction(item.getActionId(), "support");
        assertEquals(1, countItem(f.logFile.resolveSibling("out-2.yaml"), item.getId()));
        f.engine.tick();
        assertTrue(f.reload(10).open().isEmpty());
        assertEquals(3, f.reload(10).history().size());
        assertEquals(1, countItem(f.logFile.resolveSibling("out-2.yaml"), item.getId()));
        assertEquals(0, countItem(f.logFile, item.getId()));
    }

    private static long countItem(Path file, String id) throws IOException {
        String needle = "itemId: " + OutcomeLog.quote(id) + ",";
        return Files.readAllLines(file).stream().filter(line -> line.contains(needle)).count();
    }

    private static List<Map<String, Object>> records(Path file) throws IOException {
        return new YAMLMapper().readerFor(Map.class).<Map<String, Object>>readValues(Files.readString(file)).readAll();
    }
}
