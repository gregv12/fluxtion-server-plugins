package com.fluxtion.server.plugin.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The scheduler's rules at exact times, against a fake server. */
class SchedulerEngineTest {

    static final String CATALOGUE = """
            commands:
              - command: "strat.{strategy}.setPriceSource"
                title: Switch price source
                arguments:
                  - name: source
                    type: CHOICE
                    choicesFrom:
                      command: "strat.{strategy}.getPriceSources"
                      path: supportedPriceSources
                      values: [feed, venue, symbol]
              - command: "strat.{strategy}.setMaxBookAge"
                arguments:
                  - name: ms
                    type: NUMBER
                    min: 0
              - command: "{feed}.connect"
              - command: "{pricer}.setLogLevel"
                arguments:
                  - name: level
                    type: ENUM
                    values: [INFO, DEBUG]
            """;

    static final long T0 = Instant.parse("2026-10-14T08:00:00Z").toEpochMilli();
    static final long MIN = 60_000;

    @TempDir
    Path dir;
    final AtomicLong now = new AtomicLong(T0);
    final FakeServer server = new FakeServer();
    SchedulerEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        engine = newEngine("uat");
    }

    SchedulerEngine newEngine(String environment) throws Exception {
        SchedulerStore store = new SchedulerStore(dir.resolve("state"), 1000);
        store.load();
        return new SchedulerEngine(store, new OutcomeLog(dir.resolve("log/auditLog-scheduler.yaml"), 1_000_000, 3),
                Catalogue.parse(CATALOGUE), server, now::get,
                new SchedulerEngine.Settings(environment, "UTC", 15, 30));
    }

    // ==================== library ====================

    @Test
    void anActionMustBeACatalogueCommandLiveOnThisServer() {
        assertRefused("not in the catalogue", () -> engine.saveAction(action("strat.A.stop"), null, "support"));
        assertRefused("not registered on this server", () -> engine.saveAction(action("strat.Z.setMaxBookAge", "500"), null, "support"));
        assertRefused("must be >= 0", () -> engine.saveAction(action("strat.A.setMaxBookAge", "-1"), null, "support"));
        assertRefused("must be one of", () -> engine.saveAction(action("pricer.setLogLevel", "TRACE"), null, "support"));
        assertRefused("takes 1 argument", () -> engine.saveAction(action("strat.A.setMaxBookAge"), null, "support"));
        SchedulerAction a = engine.saveAction(action("strat.A.setMaxBookAge", "500"), null, "support");
        assertNotNull(a.getId());
        assertEquals("support", a.getChangedBy());
    }

    @Test
    void choiceArgumentsMustBeOneOfTheServersAnswers() {
        assertRefused("is not one of the server's choices",
                () -> engine.saveAction(action("strat.A.setPriceSource", "feed1", "VENUE1", "GBPUSD"), null, "support"));
        SchedulerAction a = engine.saveAction(action("strat.A.setPriceSource", "feed1", "VENUE1", "EURUSD"), null, "support");
        assertEquals(List.of("feed1", "VENUE1", "EURUSD"), a.getArguments());
        assertEquals(List.of(List.of("feed1", "VENUE1", "EURUSD"), List.of("feed2", "VENUE2", "EURUSD")),
                engine.choices("strat.A.setPriceSource", "source"));
    }

    @Test
    void anEmptyChoiceListIsNotTakenAsAnything() {
        server.priceSources = "{\"supportedPriceSources\":[]}";
        assertRefused("the server offers no choices",
                () -> engine.saveAction(action("strat.A.setPriceSource", "feed1", "VENUE1", "EURUSD"), null, "support"));
    }

    @Test
    void anEditBasedOnAStaleVersionIsRefused() {
        SchedulerAction a = engine.saveAction(action("strat.A.setMaxBookAge", "500"), null, "support");
        SchedulerAction edit = copy(a, "900");
        SchedulerAction saved = engine.saveAction(edit, a.getChangedAt(), "alice");
        assertTrue(saved.getChangedAt() > a.getChangedAt(), "version moves on even within one millisecond");
        assertRefused("was changed by alice", () -> engine.saveAction(copy(a, "700"), a.getChangedAt(), "bob"));
        assertEquals(List.of("900"), engine.actions().get(0).getArguments());
    }

    @Test
    void theLibrarySurvivesARestart() throws Exception {
        SchedulerAction a = engine.saveAction(action("strat.A.setMaxBookAge", "500"), null, "support");
        SchedulerEngine restarted = newEngine("uat");
        assertEquals(a, restarted.actions().get(0));
    }

    // ==================== timed items ====================

    @Test
    void aOneOffFallsDueThenRunsOnceWhenApproved() {
        SchedulerAction a = engine.saveAction(action("strat.A.setMaxBookAge", "500"), null, "support");
        engine.saveSchedule(once(a, "2026-10-14T09:00"), "trader");
        now.set(T0 + 59 * MIN);
        assertTrue(engine.tick().isEmpty());
        now.set(T0 + 60 * MIN);
        List<OpenItem> opened = engine.tick();
        assertEquals(1, opened.size());
        OpenItem item = opened.get(0);
        assertEquals(T0 + 75 * MIN, item.getExpiresAt(), "expiry is anchored to the due time");

        now.set(T0 + 61 * MIN);
        Outcome o = engine.approve(item.getId(), "trader-1");
        assertEquals(OutcomeState.RAN_OK, o.getState());
        assertEquals(List.of("strat.A.setMaxBookAge [500]"), server.runs);
        assertTrue(engine.open().isEmpty(), "closed items leave the cache");
        assertRefused("already approved by trader-1", () -> engine.approve(item.getId(), "trader-2"));
        assertRefused("already approved by trader-1", () -> engine.decline(item.getId(), "trader-2"));
        assertFalse(engine.schedules().get(0).isEnabled(), "a one-off is spent");
        now.set(T0 + 2 * 24 * 60 * MIN);
        assertTrue(engine.tick().isEmpty());
        assertEquals(1, server.runs.size());
    }

    @Test
    void anUnansweredItemExpiresAndIsNeverRun() throws Exception {
        SchedulerAction a = engine.saveAction(action("strat.A.setMaxBookAge", "500"), null, "support");
        engine.saveSchedule(once(a, "2026-10-14T09:00"), "trader");
        now.set(T0 + 60 * MIN);
        OpenItem item = engine.tick().get(0);
        now.set(T0 + 75 * MIN);
        engine.tick();
        assertTrue(engine.open().isEmpty());
        assertRefused("already expired", () -> engine.approve(item.getId(), "late"));
        assertTrue(server.runs.isEmpty());
        Outcome o = engine.history(0, Long.MAX_VALUE).get(0);
        assertEquals(OutcomeState.EXPIRED, o.getState());
        String log = Files.readString(dir.resolve("log/auditLog-scheduler.yaml"));
        assertTrue(log.contains("event: SchedulerOutcome"), log);
        assertTrue(log.contains("state: EXPIRED"), log);
        Map<String, Object> summary = engine.summary(T0, 20);
        assertEquals(1, summary.get("unacknowledgedExpired"));
        engine.acknowledgeExpired();
        assertEquals(0, engine.summary(T0, 20).get("unacknowledgedExpired"));
    }

    @Test
    void anApprovalArrivingAfterExpiryButBeforeTheTickIsRefused() {
        SchedulerAction a = engine.saveAction(action("strat.A.setMaxBookAge", "500"), null, "support");
        engine.saveSchedule(once(a, "2026-10-14T09:00"), "trader");
        now.set(T0 + 60 * MIN);
        OpenItem item = engine.tick().get(0);
        now.set(T0 + 80 * MIN);
        assertRefused("expired", () -> engine.approve(item.getId(), "late"));
        assertTrue(server.runs.isEmpty());
        assertEquals(OutcomeState.EXPIRED, engine.history(0, Long.MAX_VALUE).get(0).getState());
    }

    @Test
    void aOneOffMissedWhileTheServerWasDownIsLoggedMissedNotOpened() {
        SchedulerAction a = engine.saveAction(action("strat.A.setMaxBookAge", "500"), null, "support");
        engine.saveSchedule(once(a, "2026-10-14T09:00"), "trader");
        now.set(T0 + 70 * MIN);
        assertEquals(1, engine.tick().size(), "still inside due + expiry: offered");
        engine.decline(engine.open().get(0).getId(), "t");

        engine.saveSchedule(once(a, "2026-10-14T09:30"), "trader");
        now.set(T0 + 120 * MIN);
        assertTrue(engine.tick().isEmpty(), "past due + expiry: not offered");
        Outcome missed = engine.history(0, Long.MAX_VALUE).get(0);
        assertEquals(OutcomeState.EXPIRED, missed.getState());
        assertTrue(missed.getDetail().startsWith("missed"), missed.getDetail());
    }

    @Test
    void aOneOffInThePastIsRefused() {
        SchedulerAction a = engine.saveAction(action("strat.A.setMaxBookAge", "500"), null, "support");
        assertRefused("already passed", () -> engine.saveSchedule(once(a, "2026-10-13T09:00"), "trader"));
    }

    @Test
    void dailyAdvancesAndNeverBackfills() {
        SchedulerAction a = engine.saveAction(action("strat.A.setMaxBookAge", "500"), null, "support");
        SchedulerSchedule s = engine.saveSchedule(daily(a, "07:00", "UTC"), "trader");
        assertEquals(Instant.parse("2026-10-15T07:00:00Z").toEpochMilli(), s.getNextDueAt(), "07:00 today has passed");

        // server down for three days
        now.set(Instant.parse("2026-10-18T10:00:00Z").toEpochMilli());
        engine.recoverAfterStart();
        engine.tick();
        assertTrue(engine.open().isEmpty());
        List<Outcome> h = engine.history(0, Long.MAX_VALUE);
        assertEquals(1, h.size(), "one missed record, not three: " + h);
        assertEquals(Instant.parse("2026-10-18T07:00:00Z").toEpochMilli(), h.get(0).getDueAt());
        assertEquals(Instant.parse("2026-10-19T07:00:00Z").toEpochMilli(), engine.schedules().get(0).getNextDueAt());
    }

    @Test
    void aDailyItemStillOpenWhenTheNextFallsDueIsNotDoubled() {
        SchedulerAction a = engine.saveAction(action("strat.A.setMaxBookAge", "500"), null, "support");
        SchedulerSchedule in = daily(a, "09:00", "UTC");
        in.setExpiryMinutes(48 * 60);
        engine.saveSchedule(in, "trader");
        now.set(T0 + 60 * MIN);
        assertEquals(1, engine.tick().size());
        now.set(T0 + 60 * MIN + 24 * 60 * MIN);
        assertTrue(engine.tick().isEmpty());
        assertEquals(1, engine.open().size());
        assertTrue(engine.history(0, Long.MAX_VALUE).get(0).getDetail().contains("still open"));
    }

    @Test
    void dstGapFiresAtTheNextValidTimeAndOverlapFiresOnce() {
        SchedulerAction a = engine.saveAction(action("strat.A.setMaxBookAge", "500"), null, "support");
        ZoneId london = ZoneId.of("Europe/London");
        SchedulerSchedule gap = daily(a, "01:30", "Europe/London");
        // 2026-03-29 01:00 -> 02:00 in London: 01:30 does not exist that day
        long beforeGap = ZonedDateTime.of(2026, 3, 28, 12, 0, 0, 0, london).toInstant().toEpochMilli();
        long t = engine.nextDaily(gap, beforeGap);
        assertEquals(Instant.parse("2026-03-29T01:30:00Z").toEpochMilli(), t, "02:30 BST, the next valid instant");

        // 2026-10-25 01:00-02:00 happens twice in London
        long beforeOverlap = ZonedDateTime.of(2026, 10, 24, 12, 0, 0, 0, london).toInstant().toEpochMilli();
        long first = engine.nextDaily(gap, beforeOverlap);
        assertEquals(Instant.parse("2026-10-25T00:30:00Z").toEpochMilli(), first, "earlier offset (BST)");
        long next = engine.nextDaily(gap, first);
        assertEquals(Instant.parse("2026-10-26T01:30:00Z").toEpochMilli(), next, "not again at 01:30 GMT the same day");
    }

    @Test
    void aBadZoneOrTimeIsRefused() {
        SchedulerAction a = engine.saveAction(action("strat.A.setMaxBookAge", "500"), null, "support");
        assertRefused("unknown time zone", () -> engine.saveSchedule(daily(a, "09:00", "Mars/Olympus"), "t"));
        assertRefused("bad time", () -> engine.saveSchedule(daily(a, "9am", "UTC"), "t"));
    }

    // ==================== how a run turns out ====================

    @Test
    void busyErrorsExceptionsAndVanishedCommandsAreFailedWithOutputKept() {
        SchedulerAction a = engine.saveAction(action("strat.A.setMaxBookAge", "500"), null, "support");
        server.answer = c -> new SchedulerEngine.CommandResult(false, "command is busy try again");
        Outcome busy = approveDue(a, "09:00");
        assertEquals(OutcomeState.FAILED, busy.getState());
        assertEquals("command is busy try again", busy.getDetail());

        server.answer = c -> { throw new IllegalStateException("boom"); };
        Outcome thrown = approveDue(a, "10:00");
        assertEquals(OutcomeState.FAILED, thrown.getState());
        assertTrue(thrown.getDetail().contains("boom"));

        server.answer = null;
        SchedulerSchedule s = engine.saveSchedule(once(a, "2026-10-14T11:00"), "t");
        now.set(s.getNextDueAt());
        OpenItem item = engine.tick().get(0);
        server.live.remove("strat.A.setMaxBookAge");
        assertRefused("not registered on this server", () -> engine.approve(item.getId(), "t"));
        assertEquals(OutcomeState.FAILED, engine.history(0, Long.MAX_VALUE).get(0).getState());
        assertTrue(engine.open().isEmpty());
    }

    @Test
    void argumentsAreRecheckedAgainstTheServerAtApproval() {
        SchedulerAction a = engine.saveAction(action("strat.A.setPriceSource", "feed2", "VENUE2", "EURUSD"), null, "support");
        engine.saveSchedule(once(a, "2026-10-14T09:00"), "t");
        now.set(T0 + 60 * MIN);
        OpenItem item = engine.tick().get(0);
        server.priceSources = "{\"supportedPriceSources\":[{\"feed\":\"feed1\",\"venue\":\"VENUE1\",\"symbol\":\"EURUSD\"}]}";
        assertRefused("not one of the server's choices", () -> engine.approve(item.getId(), "t"));
        assertFalse(server.runs.stream().anyMatch(r -> r.startsWith("strat.A.setPriceSource")));
    }

    @Test
    void twoTradersApprovingAtOnceRunItOnce() throws Exception {
        SchedulerAction a = engine.saveAction(action("strat.A.setMaxBookAge", "500"), null, "support");
        engine.saveSchedule(once(a, "2026-10-14T09:00"), "t");
        now.set(T0 + 60 * MIN);
        OpenItem item = engine.tick().get(0);
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        server.answer = c -> {
            running.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new SchedulerEngine.CommandResult(true, "ok");
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Outcome> first = pool.submit(() -> engine.approve(item.getId(), "one"));
            assertTrue(running.await(5, TimeUnit.SECONDS));
            assertRefused("already approved: it is running", () -> engine.approve(item.getId(), "two"));
            assertRefused("already approved: it is running", () -> engine.decline(item.getId(), "two"));
            assertEquals(1, engine.open().size(), "the engine is not locked while the command runs");
            release.countDown();
            assertEquals(OutcomeState.RAN_OK, first.get(5, TimeUnit.SECONDS).getState());
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, server.runs.size());
    }

    @Test
    void pausedMeansNothingFallsDueAndNothingIsApproved() {
        SchedulerAction a = engine.saveAction(action("strat.A.setMaxBookAge", "500"), null, "support");
        engine.saveSchedule(once(a, "2026-10-14T09:00"), "t");
        now.set(T0 + 60 * MIN);
        OpenItem item = engine.tick().get(0);
        engine.setPaused(true);
        assertRefused("paused", () -> engine.approve(item.getId(), "t"));
        engine.saveSchedule(once(a, "2026-10-14T09:05"), "t");
        now.set(T0 + 65 * MIN);
        assertTrue(engine.tick().isEmpty());
        Outcome skipped = engine.history(0, Long.MAX_VALUE).get(0);
        assertEquals("skipped: the scheduler was paused", skipped.getDetail(), "recorded, not held back");
        engine.setPaused(false);
        assertTrue(engine.tick().isEmpty(), "nothing opens late after resume");
        assertRefused("paused", () -> { engine.setPaused(true); engine.testAction(a.getId(), "s"); });
    }

    @Test
    void aSpentOneOffCannotBeReArmed() {
        SchedulerAction a = engine.saveAction(action("strat.A.setMaxBookAge", "500"), null, "support");
        SchedulerSchedule s = engine.saveSchedule(once(a, "2026-10-14T09:00"), "t");
        now.set(T0 + 60 * MIN);
        engine.approve(engine.tick().get(0).getId(), "t");
        now.set(T0 + 65 * MIN);
        assertRefused("already passed", () -> engine.enableSchedule(s.getId(), true, "t"));
        SchedulerSchedule again = once(a, "2026-10-14T09:00");
        again.setId(s.getId());
        assertRefused("already passed", () -> engine.saveSchedule(again, "t"));
        assertTrue(engine.tick().isEmpty());
        assertEquals(1, server.runs.size());
    }

    @Test
    void aStaleScheduleEditIsRefused() {
        SchedulerAction a = engine.saveAction(action("strat.A.setMaxBookAge", "500"), null, "support");
        SchedulerSchedule s = engine.saveSchedule(daily(a, "09:00", "UTC"), "t");
        SchedulerSchedule edit = daily(a, "10:00", "UTC");
        edit.setId(s.getId());
        SchedulerSchedule saved = engine.saveSchedule(edit, s.getChangedAt(), "alice");
        assertTrue(saved.getChangedAt() > s.getChangedAt());
        SchedulerSchedule stale = daily(a, "11:00", "UTC");
        stale.setId(s.getId());
        assertRefused("was changed by alice", () -> engine.saveSchedule(stale, s.getChangedAt(), "bob"));
    }

    @Test
    void aFailedChoicesLookupRefusesTheApprovalAndLeavesTheItemOpen() {
        SchedulerAction a = engine.saveAction(action("strat.A.setPriceSource", "feed1", "VENUE1", "EURUSD"), null, "support");
        engine.saveSchedule(once(a, "2026-10-14T09:00"), "t");
        now.set(T0 + 60 * MIN);
        OpenItem item = engine.tick().get(0);
        server.lookupAnswer = new SchedulerEngine.CommandResult(false, "command is busy try again");
        assertRefused("not run, try again", () -> engine.approve(item.getId(), "t"));
        assertEquals(1, engine.open().size(), "still open");
        assertTrue(engine.history(0, Long.MAX_VALUE).isEmpty(), "nothing recorded");
        server.lookupAnswer = null;
        assertEquals(OutcomeState.RAN_OK, engine.approve(item.getId(), "t").getState());
    }

    @Test
    void theArgumentRecheckDoesNotHoldTheEngine() throws Exception {
        SchedulerAction a = engine.saveAction(action("strat.A.setPriceSource", "feed1", "VENUE1", "EURUSD"), null, "support");
        engine.saveSchedule(once(a, "2026-10-14T09:00"), "t");
        now.set(T0 + 60 * MIN);
        OpenItem item = engine.tick().get(0);
        CountDownLatch inLookup = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        server.lookupHook = () -> {
            inLookup.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Outcome> f = pool.submit(() -> engine.approve(item.getId(), "one"));
            assertTrue(inLookup.await(5, TimeUnit.SECONDS));
            server.lookupHook = null;
            assertEquals(1, engine.open().size(), "the engine answers while the lookup runs");
            engine.decline(item.getId(), "two");
            release.countDown();
            SchedulerEngine.Refused e = assertThrows(SchedulerEngine.Refused.class, () -> {
                try {
                    f.get(5, TimeUnit.SECONDS);
                } catch (java.util.concurrent.ExecutionException ex) {
                    throw ex.getCause();
                }
            });
            assertTrue(e.getMessage().contains("already declined by two"), e.getMessage());
        } finally {
            pool.shutdownNow();
        }
        assertFalse(server.runs.stream().anyMatch(r -> r.startsWith("strat.A.setPriceSource")), "declined meanwhile: not run");
    }

    @Test
    void restartActionsAreApprovedInOrder() {
        SchedulerAction first = engine.saveAction(action("feedA.connect"), null, "support");
        SchedulerAction second = engine.saveAction(action("strat.A.setMaxBookAge", "500"), null, "support");
        engine.saveSchedule(restart(first, 1), "t");
        engine.saveSchedule(restart(second, 2), "t");
        engine.offerRestartSet("server restart");
        OpenItem later = engine.open().stream().filter(i -> i.getRestartOrder() == 2).findFirst().orElseThrow();
        OpenItem earlier = engine.open().stream().filter(i -> i.getRestartOrder() == 1).findFirst().orElseThrow();
        assertRefused("decide 'do feedA.connect' first", () -> engine.approve(later.getId(), "t"));
        engine.decline(earlier.getId(), "t");
        assertEquals(OutcomeState.RAN_OK, engine.approve(later.getId(), "t").getState());
    }

    @Test
    void anItemRecordedJustBeforeACrashIsNotRecordedTwice() throws Exception {
        SchedulerAction a = engine.saveAction(action("strat.A.setMaxBookAge", "500"), null, "support");
        engine.saveSchedule(once(a, "2026-10-14T09:00"), "t");
        now.set(T0 + 60 * MIN);
        OpenItem item = engine.tick().get(0);
        Path open = dir.resolve("state/open.json");
        String openWhileRunning = Files.readString(open).replace("\"running\" : false", "\"running\" : true");
        engine.approve(item.getId(), "t");
        Files.writeString(open, openWhileRunning); // as if the stop came after the outcome was written
        SchedulerEngine restarted = newEngine("uat");
        restarted.recoverAfterStart();
        assertTrue(restarted.open().isEmpty());
        assertEquals(List.of(OutcomeState.RAN_OK), restarted.history(0, Long.MAX_VALUE).stream().map(Outcome::getState).toList());
    }

    @Test
    void aNonFiniteNumberIsRefused() {
        assertRefused("finite", () -> engine.saveAction(action("strat.A.setMaxBookAge", "NaN"), null, "support"));
        assertRefused("finite", () -> engine.saveAction(action("strat.A.setMaxBookAge", "Infinity"), null, "support"));
    }

    @Test
    void theSchedulersOwnCommandsAreNeverSchedulable() {
        assertRefused("not in the catalogue", () -> engine.saveAction(action("scheduler.open"), null, "support"));
        assertThrows(IllegalArgumentException.class, () -> Catalogue.parse("commands:\n  - command: scheduler.approveAll\n"));
        assertThrows(IllegalArgumentException.class, () -> Catalogue.parse("commands:\n  - command: \"{x}.y\"\n    arguments:\n      - name: a\n        type: CHOICE\n        choicesFrom: {command: scheduler.open}\n").find("p.y")
                .entry().getArguments().stream().findFirst().map(arg -> {
                    try {
                        return Catalogue.choices(new Catalogue.Match(null, "p.y", Map.of()), arg, c -> "[]");
                    } catch (java.io.IOException e) {
                        throw new RuntimeException(e);
                    }
                }).orElseThrow());
    }

    // ==================== environments ====================

    @Test
    void anActionWithNoEnvironmentsRunsAnywhereButProduction() throws Exception {
        SchedulerAction a = engine.saveAction(action("strat.A.setMaxBookAge", "500"), null, "support");
        SchedulerEngine prod = newEngine("prod");
        assertRefused("not allowed in environment prod", () -> prod.saveSchedule(once(a, "2026-10-14T09:00"), "t"));
        assertRefused("not allowed in production", () -> prod.testAction(a.getId(), "support"));

        SchedulerAction listed = action("strat.A.setMaxBookAge", "500");
        listed.setEnvironments(List.of("PROD"));
        SchedulerAction prodAction = prod.saveAction(listed, null, "support");
        assertNotNull(prod.saveSchedule(once(prodAction, "2026-10-14T09:00"), "t"));
        SchedulerEngine uat = newEngine("uat");
        assertRefused("not allowed in environment uat", () -> uat.testAction(prodAction.getId(), "s"));
    }

    @Test
    void testRunsOnceWithoutApprovalAndIsRecorded() {
        SchedulerAction a = engine.saveAction(action("feedA.connect"), null, "support");
        Outcome o = engine.testAction(a.getId(), "support");
        assertEquals(OutcomeState.RAN_OK, o.getState());
        assertEquals("support (test)", o.getDecidedBy());
        assertEquals(List.of("feedA.connect []"), server.runs);
    }

    @Test
    void retiringAnActionDisablesItsSchedules() {
        SchedulerAction a = engine.saveAction(action("strat.A.setMaxBookAge", "500"), null, "support");
        engine.saveSchedule(daily(a, "09:00", "UTC"), "t");
        engine.retireAction(a.getId(), "support");
        assertFalse(engine.schedules().get(0).isEnabled());
        assertRefused("retired", () -> engine.saveSchedule(daily(a, "10:00", "UTC"), "t"));
    }

    // ==================== restart ====================

    @Test
    void theRestartSetIsOfferedInOrderAndAllStopsAtTheFirstFailure() {
        SchedulerAction connect = engine.saveAction(action("feedA.connect"), null, "support");
        SchedulerAction age = engine.saveAction(action("strat.A.setMaxBookAge", "500"), null, "support");
        SchedulerAction level = engine.saveAction(action("pricer.setLogLevel", "DEBUG"), null, "support");
        engine.saveSchedule(restart(level, 3), "t");
        engine.saveSchedule(restart(connect, 1), "t");
        engine.saveSchedule(restart(age, 2), "t");
        assertEquals(List.of("feedA.connect", "strat.A.setMaxBookAge", "pricer.setLogLevel"), engine.restartCommands());

        String setId = engine.offerRestartSet("server restart");
        assertEquals(3, engine.open().size());
        assertEquals(T0 + 30 * MIN, engine.open().get(0).getExpiresAt(), "set expiry runs from the offer");
        assertRefused("already open", () -> engine.offerRestartSet("again"));

        server.answer = c -> c.startsWith("strat.A.setMaxBookAge")
                ? new SchedulerEngine.CommandResult(false, "problem executing command") : null;
        List<Outcome> all = engine.approveAll(setId, "t");
        assertEquals(List.of(OutcomeState.RAN_OK, OutcomeState.FAILED), all.stream().map(Outcome::getState).toList());
        assertEquals(1, engine.open().size(), "the rest stay open");
        assertEquals("pricer.setLogLevel", engine.open().get(0).getCommand());

        engine.declineAll(setId, "t");
        assertTrue(engine.open().isEmpty());
        assertNotNull(engine.offerRestartSet("on request"), "Run restart actions offers it again");
    }

    @Test
    void afterARestartRunningIsFailedOldSetsAreSupersededAndPastItemsExpire() throws Exception {
        SchedulerAction a = engine.saveAction(action("strat.A.setMaxBookAge", "500"), null, "support");
        SchedulerAction b = engine.saveAction(action("feedA.connect"), null, "support");
        engine.saveSchedule(once(a, "2026-10-14T09:00"), "t");
        engine.saveSchedule(once(a, "2026-10-14T09:10"), "t");
        now.set(T0 + 70 * MIN);
        assertEquals(2, engine.tick().size());
        engine.saveSchedule(restart(b, 1), "t");
        engine.offerRestartSet("server restart");
        assertEquals(3, engine.open().size());

        // a crash mid-run: the 09:00 item (first on disk) was running
        Path open = dir.resolve("state/open.json");
        Files.writeString(open, Files.readString(open).replaceFirst("\"running\" : false", "\"running\" : true"));

        now.set(T0 + 90 * MIN); // the 09:10 item expired while down; the restart set has not
        SchedulerEngine restarted = newEngine("uat");
        restarted.recoverAfterStart();
        assertTrue(restarted.open().isEmpty());
        Map<String, String> byCommand = new HashMap<>();
        for (Outcome o : restarted.history(0, Long.MAX_VALUE)) {
            byCommand.put(o.getCommand() + "@" + o.getDueAt(), o.getState() + " " + o.getDetail());
        }
        assertTrue(byCommand.get("strat.A.setMaxBookAge@" + (T0 + 60 * MIN)).startsWith("FAILED interrupted by restart"), byCommand.toString());
        assertTrue(byCommand.get("strat.A.setMaxBookAge@" + (T0 + 70 * MIN)).startsWith("EXPIRED expired: the server was down"), byCommand.toString());
        assertTrue(byCommand.get("feedA.connect@" + (T0 + 70 * MIN)).startsWith("EXPIRED superseded"), byCommand.toString());
        assertTrue(server.runs.isEmpty(), "nothing re-run");
    }

    @Test
    void anUnknownFieldInTheStateFilesIsIgnored() throws Exception {
        engine.saveAction(action("strat.A.setMaxBookAge", "500"), null, "support");
        Path actions = dir.resolve("state/actions.json");
        Files.writeString(actions, Files.readString(actions).replaceFirst("\"title\"", "\"addedLater\" : 1, \"title\""));
        assertEquals(1, newEngine("uat").actions().size());
        assertFalse(Files.exists(dir.resolve("state/actions.json.tmp")));
    }

    // ==================== helpers ====================

    Outcome approveDue(SchedulerAction a, String hhmm) {
        SchedulerSchedule s = engine.saveSchedule(once(a, "2026-10-14T" + hhmm), "t");
        now.set(s.getNextDueAt());
        OpenItem item = engine.tick().get(0);
        return engine.approve(item.getId(), "t");
    }

    static SchedulerAction action(String command, String... args) {
        SchedulerAction a = new SchedulerAction();
        a.setTitle("do " + command);
        a.setCommand(command);
        a.setArguments(new ArrayList<>(List.of(args)));
        return a;
    }

    static SchedulerAction copy(SchedulerAction a, String arg) {
        SchedulerAction c = action(a.getCommand(), arg);
        c.setId(a.getId());
        return c;
    }

    static SchedulerSchedule once(SchedulerAction a, String at) {
        SchedulerSchedule s = new SchedulerSchedule();
        s.setActionId(a.getId());
        s.setTrigger(TriggerType.ONCE);
        s.setAt(at);
        return s;
    }

    static SchedulerSchedule daily(SchedulerAction a, String at, String zone) {
        SchedulerSchedule s = once(a, at);
        s.setTrigger(TriggerType.DAILY);
        s.setZone(zone);
        return s;
    }

    static SchedulerSchedule restart(SchedulerAction a, int order) {
        SchedulerSchedule s = once(a, null);
        s.setTrigger(TriggerType.AFTER_RESTART);
        s.setRestartOrder(order);
        return s;
    }

    static void assertRefused(String expected, Runnable r) {
        SchedulerEngine.Refused e = assertThrows(SchedulerEngine.Refused.class, r::run);
        assertTrue(e.getMessage().contains(expected), "expected '" + expected + "' in: " + e.getMessage());
    }

    /** A server with a few commands; records every run except choice lookups. */
    static class FakeServer implements SchedulerEngine.CommandGateway {
        final Set<String> live = new LinkedHashSet<>(List.of(
                "strat.A.setPriceSource", "strat.A.getPriceSources", "strat.A.setMaxBookAge", "strat.A.stop",
                "feedA.connect", "pricer.setLogLevel", "scheduler.open"));
        final List<String> runs = new CopyOnWriteArrayList<>();
        volatile String priceSources = "{\"supportedPriceSources\":["
                + "{\"feed\":\"feed1\",\"venue\":\"VENUE1\",\"symbol\":\"EURUSD\"},"
                + "{\"feed\":\"feed2\",\"venue\":\"VENUE2\",\"symbol\":\"EURUSD\"}]}";
        /** Overrides the answer for a run; returning null means the default OK. */
        volatile Function<String, SchedulerEngine.CommandResult> answer;
        /** Overrides the answer for a choices lookup; null = the price sources. */
        volatile SchedulerEngine.CommandResult lookupAnswer;
        /** Runs inside a choices lookup (to hold it open). */
        volatile Runnable lookupHook;

        @Override
        public SchedulerEngine.CommandResult lookup(String command) {
            Runnable hook = lookupHook;
            if (hook != null) hook.run();
            SchedulerEngine.CommandResult r = lookupAnswer;
            return r != null ? r : run(command, List.of());
        }

        @Override
        public Collection<String> liveCommands() {
            return new ArrayList<>(live);
        }

        @Override
        public SchedulerEngine.CommandResult run(String command, List<String> arguments) {
            if (command.endsWith(".getPriceSources")) return new SchedulerEngine.CommandResult(true, priceSources);
            String call = command + " " + arguments;
            runs.add(call);
            SchedulerEngine.CommandResult r = answer == null ? null : answer.apply(call);
            return r == null ? new SchedulerEngine.CommandResult(true, "done") : r;
        }
    }

    @Test
    void noRestartSchedulesMeansNoSet() {
        assertNull(engine.offerRestartSet("server restart"));
    }
}
