package com.fluxtion.server.plugin.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxtion.server.service.admin.AdminCommandRegistry;
import com.fluxtion.server.service.admin.AdminCommandRequest;
import com.fluxtion.server.service.admin.AdminFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The service's admin commands against a registry that behaves like the server's (command name prepended). */
class ActionSchedulerTest {

    @TempDir
    Path dir;
    final FakeRegistry registry = new FakeRegistry();
    final ObjectMapper json = new ObjectMapper();
    final List<List<String>> calls = new CopyOnWriteArrayList<>();
    ActionScheduler scheduler;

    @BeforeEach
    void setUp() throws Exception {
        Path catalogue = dir.resolve("catalogue.yaml");
        Files.writeString(catalogue, SchedulerEngineTest.CATALOGUE);
        registry.registerCommand("strat.A.setMaxBookAge", (args, out, err) -> {
            calls.add(args);
            out.accept("maxBookAge set " + args.get(1));
        });
        registry.registerCommand("strat.A.getPriceSources", (args, out, err) ->
                out.accept("{\"supportedPriceSources\":[{\"feed\":\"feed1\",\"venue\":\"VENUE1\",\"symbol\":\"EURUSD\"}]}"));
        registry.registerCommand("strat.A.setPriceSource", (args, out, err) -> {
            calls.add(args);
            out.accept("price source set");
        });
        registry.registerCommand("feedA.connect", (args, out, err) -> {
            long end = System.currentTimeMillis() + 3_000; // ignores interrupts, like a stuck command
            while (System.currentTimeMillis() < end) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {
                    // keep going
                }
            }
            out.accept("connected");
        });
        registry.registerCommand("pricer.setLogLevel", (args, out, err) -> err.accept("no such level"));

        scheduler = new ActionScheduler();
        scheduler.setStateDirectory(dir.resolve("state").toString());
        scheduler.setOutcomeLogFile(dir.resolve("log/auditLog-scheduler.yaml").toString());
        scheduler.setCatalogueFile(catalogue.toString());
        scheduler.setEnvironment("dev");
        scheduler.setCommandTimeoutSeconds(1);
        scheduler.adminRegistry(registry, "adminService");
        scheduler.init();
    }

    @AfterEach
    void tearDown() {
        scheduler.tearDown();
    }

    @Test
    void theCatalogueListsOnlyLiveMatchingCommands() throws Exception {
        JsonNode cat = ok("scheduler.catalogue");
        List<String> commands = new ArrayList<>();
        cat.forEach(e -> commands.add(e.get("command").asText()));
        assertEquals(List.of("feedA.connect", "pricer.setLogLevel", "strat.A.setMaxBookAge", "strat.A.setPriceSource"), commands);
        JsonNode choices = ok("scheduler.choices", "strat.A.setPriceSource", "source");
        assertEquals("[[\"feed1\",\"VENUE1\",\"EURUSD\"]]", choices.toString());
    }

    @Test
    void saveTestAndListAnActionThroughAdminCommands() throws Exception {
        JsonNode saved = ok("scheduler.saveAction",
                "{\"title\":\"Book age 500\",\"command\":\"strat.A.setPriceSource\",\"arguments\":[\"feed1\",\"VENUE1\",\"EURUSD\"]}", "support");
        String id = saved.get("id").asText();
        assertEquals("support", saved.get("changedBy").asText());
        JsonNode tested = ok("scheduler.testAction", id, "support");
        assertEquals("RAN_OK", tested.get("state").asText());
        assertEquals(List.of(List.of("strat.A.setPriceSource", "feed1", "VENUE1", "EURUSD")), calls,
                "arguments reach the command as a list, after its name");
        assertEquals(1, ok("scheduler.actions").size());
        assertEquals(1, ok("scheduler.history").size());
    }

    @Test
    void refusalsGoToTheErrorChannel() {
        Reply r = run("scheduler.saveAction", "{\"title\":\"x\",\"command\":\"strat.A.stop\"}");
        assertTrue(r.err.contains("not in the catalogue"), r.toString());
        assertTrue(r.out.isEmpty());
        assertTrue(run("scheduler.approve").err.contains("missing argument: item id"));
        assertTrue(run("scheduler.saveSchedule", "{not json").err.contains("not valid SchedulerSchedule JSON"));
    }

    @Test
    void aCommandReportingAnErrorIsFailed() throws Exception {
        JsonNode saved = ok("scheduler.saveAction", "{\"title\":\"debug\",\"command\":\"pricer.setLogLevel\",\"arguments\":[\"DEBUG\"]}");
        JsonNode tested = ok("scheduler.testAction", saved.get("id").asText());
        assertEquals("FAILED", tested.get("state").asText());
        assertTrue(tested.get("detail").asText().contains("no such level"));
    }

    @Test
    void aCommandThatOutrunsTheTimeoutIsFailedResultUnknown() throws Exception {
        JsonNode saved = ok("scheduler.saveAction", "{\"title\":\"connect\",\"command\":\"feedA.connect\"}");
        long start = System.currentTimeMillis();
        JsonNode tested = ok("scheduler.testAction", saved.get("id").asText());
        assertTrue(System.currentTimeMillis() - start < 2_500, "waited only for the timeout");
        assertEquals("FAILED", tested.get("state").asText());
        assertTrue(tested.get("detail").asText().startsWith("timed out after 1s, result unknown"), tested.toString());
    }

    @Test
    void afterATimeoutNoOtherCommandStartsUntilTheStuckOneFinishes() throws Exception {
        JsonNode slow = ok("scheduler.saveAction", "{\"title\":\"connect\",\"command\":\"feedA.connect\"}");
        JsonNode quick = ok("scheduler.saveAction",
                "{\"title\":\"age\",\"command\":\"strat.A.setMaxBookAge\",\"arguments\":[\"5\"]}");
        assertEquals("FAILED", ok("scheduler.testAction", slow.get("id").asText()).get("state").asText());
        Reply busy = run("scheduler.testAction", quick.get("id").asText());
        assertTrue(busy.err.contains("busy with feedA.connect, which timed out"), busy.toString());
        assertTrue(calls.isEmpty(), "never two commands at once");
        Thread.sleep(2_500); // the stuck command finishes (3 s in all)
        assertEquals("RAN_OK", ok("scheduler.testAction", quick.get("id").asText()).get("state").asText());
    }

    @Test
    void aTimedOutProcessorCommandIsNeverOverlapped() throws Exception {
        // as the server dispatches a processor's command: publish to the processor, then wait (interruptibly)
        ProcessorRegistry server = new ProcessorRegistry();
        List<String> log = new CopyOnWriteArrayList<>();
        server.registerCommand("feedA.connect", (args, out, err) -> {
            log.add("start connect");
            sleepQuietly(2_500);
            log.add("end connect");
            out.accept("connected");
        });
        server.registerCommand("strat.A.setMaxBookAge", (args, out, err) -> {
            log.add("age");
            out.accept("ok");
        });
        ActionScheduler s = schedulerOn(server, "state4");
        try {
            String slow = okOn(server, "scheduler.saveAction", "{\"title\":\"c\",\"command\":\"feedA.connect\"}").get("id").asText();
            String quick = okOn(server, "scheduler.saveAction", "{\"title\":\"a\",\"command\":\"strat.A.setMaxBookAge\",\"arguments\":[\"1\"]}").get("id").asText();
            assertTrue(okOn(server, "scheduler.testAction", slow).get("detail").asText().startsWith("timed out"));
            Reply busy = runOn(server, "scheduler.testAction", quick);
            assertTrue(busy.err.contains("busy with feedA.connect, which timed out"), busy.toString());
            assertTrue(okOn(server, "scheduler.status").get("busy").asText().contains("feedA.connect"));
            Thread.sleep(2_000);
            assertEquals("RAN_OK", okOn(server, "scheduler.testAction", quick).get("state").asText());
            assertEquals(List.of("start connect", "end connect", "age"), log, "never overlapped");
        } finally {
            s.tearDown();
            server.stop();
        }
    }

    @Test
    void aSecondCommandIsRefusedNotQueuedAndThePathFreesItself() throws Exception {
        ProcessorRegistry server = new ProcessorRegistry();
        server.registerCommand("feedA.connect", (args, out, err) -> {
            sleepQuietly(1_500);
            out.accept("connected");
        });
        server.registerCommand("strat.A.setMaxBookAge", (args, out, err) -> out.accept("ok"));
        ActionScheduler s = schedulerOn(server, "state5");
        s.setCommandTimeoutSeconds(5);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            String slow = okOn(server, "scheduler.saveAction", "{\"title\":\"c\",\"command\":\"feedA.connect\"}").get("id").asText();
            String quick = okOn(server, "scheduler.saveAction", "{\"title\":\"a\",\"command\":\"strat.A.setMaxBookAge\",\"arguments\":[\"1\"]}").get("id").asText();
            Future<JsonNode> first = pool.submit(() -> okOn(server, "scheduler.testAction", slow));
            Thread.sleep(300);
            Reply second = runOn(server, "scheduler.testAction", quick);
            assertTrue(second.err.contains("not run, try again"), second.toString());
            assertEquals("RAN_OK", first.get(5, TimeUnit.SECONDS).get("state").asText());
            assertEquals("RAN_OK", okOn(server, "scheduler.testAction", quick).get("state").asText(), "free again");
            assertEquals(2, okOn(server, "scheduler.history").size(), "the refused one was not recorded");
        } finally {
            pool.shutdownNow();
            s.tearDown();
            server.stop();
        }
    }

    @Test
    void aFailureMarkerPastTheOutputCapStillFails() throws Exception {
        registry.registerCommand("strat.A.setMaxBookAge", (args, out, err) -> {
            out.accept("x".repeat(101));
            out.accept("command is busy try again");
        });
        scheduler.setMaxOutputChars(100);
        JsonNode quick = ok("scheduler.saveAction",
                "{\"title\":\"age\",\"command\":\"strat.A.setMaxBookAge\",\"arguments\":[\"5\"]}");
        assertEquals("FAILED", ok("scheduler.testAction", quick.get("id").asText()).get("state").asText());
    }

    /** The previous start left a restart set undecided; at this start the outcome log is briefly unavailable. */
    @Test
    void aPreviousSetStillBeingRecordedDoesNotStopThisStartsOffer() throws Exception {
        Path state = dir.resolve("state6");
        String oldSet = leaveUndecidedRestartSet(state);
        Path log = Files.createDirectories(dir.resolve("log6/out.yaml")); // unavailable: a directory
        ActionScheduler s = restartScheduler(state, log);
        try {
            s.init();
            s.start();
            String newSet = waitForOpenSetOtherThan(s, oldSet, 5_000);
            assertTrue(newSet != null, "this start's set is offered although the old one is not yet recorded: "
                    + s.getEngine().open());
            Files.delete(log); // the log comes back: the old item is recorded and leaves
            long end = System.currentTimeMillis() + 5_000;
            while (System.currentTimeMillis() < end && s.getEngine().open().stream().anyMatch(i -> oldSet.equals(i.getRestartSetId()))) {
                Thread.sleep(100);
            }
            assertTrue(s.getEngine().open().stream().noneMatch(i -> oldSet.equals(i.getRestartSetId())));
            assertEquals(OutcomeState.EXPIRED, s.getEngine().history(0, Long.MAX_VALUE).get(0).getState());
        } finally {
            s.tearDown();
        }
    }

    @Test
    void aStartupOfferThatFailsIsRetried() throws Exception {
        Path state = dir.resolve("state7");
        leaveUndecidedRestartSet(state);
        Path blocker = Files.createDirectories(state.resolve("open.json.tmp")); // open.json cannot be written
        ActionScheduler s = restartScheduler(state, dir.resolve("log7/out.yaml"));
        try {
            s.init();
            s.start();
            Thread.sleep(2_500);
            assertTrue(okOn(registry, "scheduler.status").get("restart").asText().contains("retry 1"),
                    okOn(registry, "scheduler.status").toString());
            Files.delete(blocker);
            String set = waitForOpenSetOtherThan(s, "none", 8_000); // first retry after 5 s
            assertTrue(set != null, "offered on retry: " + okOn(registry, "scheduler.status"));
            assertTrue(okOn(registry, "scheduler.status").get("restart").asText().startsWith("offered"));
        } finally {
            s.tearDown();
        }
    }

    @Test
    void aManualOfferFulfilsTheStartupRetryEvenAfterItsSetIsCompleted() throws Exception {
        Path state = dir.resolve("manual-retry");
        leaveUndecidedRestartSet(state);
        Path blocker = Files.createDirectory(state.resolve("open.json.tmp"));
        ActionScheduler s = restartScheduler(state, dir.resolve("manual-retry.yaml"));
        try {
            s.init();
            invokeStartupOffer(s, System.currentTimeMillis());
            assertTrue(s.getRestartStatus().contains("retry 1"));
            Files.delete(blocker);
            String manual = ok("scheduler.runRestartActions", "operator").get("restartSetId").asText();
            s.getEngine().approveAll(manual, "operator");
            s.getEngine().tick(); // finish any old pending close
            assertTrue(s.getEngine().open().isEmpty());
            assertEquals(1, calls.size());
            s.start(); // a manual offer before lifecycle start must keep its fulfilled status
            assertTrue(s.getRestartStatus().endsWith("on request"));
            invokeStartupOffer(s, System.currentTimeMillis()); // the outstanding automatic callback
            assertTrue(s.getEngine().open().isEmpty(), "manual completion must not cause another automatic set");
            assertEquals(1, calls.size());
            // An explicit later request is still permitted.
            assertTrue(ok("scheduler.runRestartActions", "operator").has("restartSetId"));
        } finally {
            s.tearDown();
        }
    }

    @Test
    void aRetryDoesNotOfferAfterTheStartupDeadline() throws Exception {
        Path state = dir.resolve("deadline-retry");
        leaveUndecidedRestartSet(state);
        Path blocker = Files.createDirectory(state.resolve("open.json.tmp"));
        ActionScheduler s = restartScheduler(state, dir.resolve("deadline-retry.yaml"));
        s.setRestartExpiryMinutes(1);
        try {
            s.init();
            invokeStartupOffer(s, System.currentTimeMillis());
            assertTrue(s.getRestartStatus().contains("retry 1"));
            Files.delete(blocker);
            invokeStartupOffer(s, System.currentTimeMillis() - 60_001); // callback runs beyond its deadline
            assertTrue(s.getRestartStatus().contains("gave up after 1 min"));
            assertTrue(s.getEngine().open().stream().noneMatch(i -> i.getPendingOutcome() == null));
            assertTrue(ok("scheduler.runRestartActions", "operator").has("restartSetId"), "manual recovery stays available");
        } finally {
            s.tearDown();
        }
    }

    @Test
    void retryBackoffIsLimitedToTheRemainingStartupWindow() throws Exception {
        Path state = dir.resolve("clamped-retry");
        leaveUndecidedRestartSet(state);
        Files.createDirectory(state.resolve("open.json.tmp"));
        ActionScheduler s = restartScheduler(state, dir.resolve("clamped-retry.yaml"));
        s.setRestartExpiryMinutes(1);
        try {
            s.init();
            invokeStartupOffer(s, System.currentTimeMillis() - 59_000);
            assertTrue(s.getRestartStatus().contains("retry 1"));
            List<Runnable> queued = s.getTicker().shutdownNow();
            assertEquals(1, queued.size());
            long delay = ((java.util.concurrent.Delayed) queued.getFirst()).getDelay(TimeUnit.MILLISECONDS);
            assertTrue(delay <= 1_000, "five-second backoff must fit the remaining one second: " + delay);
        } finally {
            s.tearDown();
        }
    }

    @Test
    void pausedStartupAlsoStopsAtItsDeadline() throws Exception {
        Path state = dir.resolve("paused-deadline");
        leaveUndecidedRestartSet(state);
        ActionScheduler s = restartScheduler(state, dir.resolve("paused-deadline.yaml"));
        s.setRestartExpiryMinutes(1);
        try {
            s.init();
            s.getEngine().setPaused(true);
            invokeStartupOffer(s, System.currentTimeMillis() - 60_001);
            assertTrue(s.getRestartStatus().contains("gave up after 1 min"));
            s.getEngine().setPaused(false);
            invokeStartupOffer(s, System.currentTimeMillis());
            assertTrue(s.getEngine().open().isEmpty(), "resume must not revive an expired startup attempt");
        } finally {
            s.tearDown();
        }
    }

    private static void invokeStartupOffer(ActionScheduler s, long startedAt) throws Exception {
        var offer = ActionScheduler.class.getDeclaredMethod("offerRestartSetWhenReady", long.class);
        offer.setAccessible(true);
        offer.invoke(s, startedAt);
    }

    /** Readiness timeout (120 s) longer than the offer window: offer at the last chance, with a warning. */
    @Test
    void aReadinessWaitLongerThanTheWindowStillOffersBeforeItCloses() throws Exception {
        Path state = dir.resolve("state8");
        SchedulerStore store = new SchedulerStore(state, 100);
        store.load();
        SchedulerEngine previous = new SchedulerEngine(store, new OutcomeLog(state.resolve("prev.yaml"), 1_000_000, 3),
                Catalogue.parse(SchedulerEngineTest.CATALOGUE), new SchedulerEngineTest.FakeServer(), System::currentTimeMillis,
                new SchedulerEngine.Settings("dev", "UTC", 15, 30));
        SchedulerAction a = previous.saveAction(SchedulerEngineTest.action("strat.A.setMaxBookAge", "1"), null, "s");
        previous.saveSchedule(SchedulerEngineTest.restart(a, 1), "t");
        FakeRegistry late = new FakeRegistry(); // the restart command never registers
        ActionScheduler s = new ActionScheduler() {
            @Override
            long offerWindowMillis() {
                return 4_000; // last chance 400 ms before it closes
            }
        };
        s.setStateDirectory(state.toString());
        s.setOutcomeLogFile(dir.resolve("log8/out.yaml").toString());
        s.setCatalogueFile(dir.resolve("catalogue.yaml").toString());
        s.setEnvironment("dev");
        s.setTickMillis(200);
        s.setRestartReadyDelaySeconds(30);    // longer than the window too
        s.setRestartReadyTimeoutSeconds(120);
        s.adminRegistry(late, "admin");
        try {
            s.init();
            s.start();
            String set = waitForOpenSetOtherThan(s, "none", 6_000);
            assertTrue(set != null, "offered before the window closed: " + okOn(late, "scheduler.status"));
            String status = okOn(late, "scheduler.status").get("restart").asText();
            assertTrue(status.contains("with missing commands [strat.A.setMaxBookAge]"), status);
            assertTrue(s.getEngine().open().get(0).getDescription().contains("WARNING not registered"));
        } finally {
            s.tearDown();
        }
    }

    @Test
    void nothingIsOfferedOnceTornDownEvenByACallbackAlreadyRunning() throws Exception {
        Path state = dir.resolve("state9");
        leaveUndecidedRestartSet(state);
        // a fresh store with the old set recovered: make the callback stall inside commandList
        java.util.concurrent.CountDownLatch inCallback = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        FakeRegistry stalling = new FakeRegistry() {
            @Override
            public List<String> commandList() {
                if (Thread.currentThread().getName().startsWith("scheduler-tick") && inCallback.getCount() > 0) {
                    inCallback.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {
                        // tearDown interrupts the ticker: carry on, as a real lookup would
                    }
                }
                return super.commandList();
            }
        };
        stalling.registerCommand("strat.A.setMaxBookAge", (args, out, err) -> out.accept("ok"));
        ActionScheduler s = restartScheduler(state, dir.resolve("log9/out.yaml"));
        s.adminRegistry(stalling, "admin");
        s.init();
        s.start();
        assertTrue(inCallback.await(5, TimeUnit.SECONDS), "the startup callback is running");
        s.tearDown();
        release.countDown();
        Thread.sleep(500);
        assertTrue(s.getEngine().open().stream().allMatch(i -> i.getPendingOutcome() != null),
                "no new set after tearDown: " + s.getEngine().open());
        Reply manual = runOn(stalling, "scheduler.runRestartActions", "trader");
        assertTrue(manual.err.contains("stopped"), manual.toString());
    }

    /** A previous run that offered a one-item restart set and stopped before anyone decided it. */
    private String leaveUndecidedRestartSet(Path state) throws Exception {
        SchedulerStore store = new SchedulerStore(state, 100);
        store.load();
        SchedulerEngine previous = new SchedulerEngine(store, new OutcomeLog(state.resolve("prev.yaml"), 1_000_000, 3),
                Catalogue.parse(SchedulerEngineTest.CATALOGUE), new SchedulerEngineTest.FakeServer(), System::currentTimeMillis,
                new SchedulerEngine.Settings("dev", "UTC", 15, 30));
        SchedulerAction a = previous.saveAction(SchedulerEngineTest.action("strat.A.setMaxBookAge", "1"), null, "s");
        previous.saveSchedule(SchedulerEngineTest.restart(a, 1), "t");
        return previous.offerRestartSet("previous start");
    }

    private ActionScheduler restartScheduler(Path state, Path log) {
        ActionScheduler s = new ActionScheduler();
        s.setStateDirectory(state.toString());
        s.setOutcomeLogFile(log.toString());
        s.setCatalogueFile(dir.resolve("catalogue.yaml").toString());
        s.setEnvironment("dev");
        s.setTickMillis(200);
        s.setRestartReadyDelaySeconds(1);
        s.adminRegistry(registry, "admin");
        return s;
    }

    private static String waitForOpenSetOtherThan(ActionScheduler s, String setId, long millis) throws InterruptedException {
        long end = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < end) {
            for (OpenItem i : s.getEngine().open()) {
                if (i.getRestartSetId() != null && !i.getRestartSetId().equals(setId) && i.getPendingOutcome() == null) {
                    return i.getRestartSetId();
                }
            }
            Thread.sleep(100);
        }
        return null;
    }

    @Test
    void floodedOutputIsCapped() throws Exception {
        registry.registerCommand("strat.A.setMaxBookAge", (args, out, err) -> {
            for (int i = 0; i < 200; i++) out.accept("x".repeat(1_000));
        });
        scheduler.setMaxOutputChars(10_000);
        JsonNode quick = ok("scheduler.saveAction",
                "{\"title\":\"age\",\"command\":\"strat.A.setMaxBookAge\",\"arguments\":[\"5\"]}");
        String detail = ok("scheduler.testAction", quick.get("id").asText()).get("detail").asText();
        assertTrue(detail.length() < 10_100 && detail.endsWith("[output truncated]"), detail.length() + "");
    }

    @Test
    void noCatalogueFileMeansNothingSchedulableNotAFailedStart() {
        ActionScheduler s = new ActionScheduler();
        s.setStateDirectory(dir.resolve("state3").toString());
        s.setOutcomeLogFile(dir.resolve("log3/out.yaml").toString());
        s.adminRegistry(new FakeRegistry(), "admin");
        s.init();
        s.tearDown();
    }

    @Test
    void aBrokenCatalogueDoesNotStopTheServer() throws Exception {
        Path bad = dir.resolve("bad.yaml");
        Files.writeString(bad, "commands:\n  - command: a.b\n    argumnets: []\n");
        ActionScheduler s = new ActionScheduler();
        s.setStateDirectory(dir.resolve("state2").toString());
        s.setOutcomeLogFile(dir.resolve("log2/out.yaml").toString());
        s.setCatalogueFile(bad.toString());
        FakeRegistry r = new FakeRegistry();
        s.adminRegistry(r, "admin");
        s.init();
        try {
            Reply status = new Reply();
            AdminCommandRequest req = new AdminCommandRequest();
            req.setCommand("scheduler.status");
            req.setArguments(new ArrayList<>());
            req.setOutput(o -> status.out += o);
            req.setErrOutput(o -> status.err += o);
            r.processAdminCommandRequest(req);
            assertTrue(json.readTree(status.out).get("catalogueError").asText().contains("nothing can be scheduled"), status.out);
        } finally {
            s.tearDown();
        }
    }

    @Test
    void pauseAndStatus() throws Exception {
        assertEquals(false, ok("scheduler.status").get("paused").asBoolean());
        assertEquals(true, ok("scheduler.pause", "ops").get("paused").asBoolean());
        assertEquals(false, ok("scheduler.resume", "ops").get("paused").asBoolean());
        JsonNode summary = ok("scheduler.summary");
        assertEquals("dev", summary.get("environment").asText());
        assertEquals(0, summary.get("open").asInt());
    }

    @Test
    void anUnavailableOutcomeLogDoesNotPreventStartupAndRecoveryRetries() throws Exception {
        Path state = dir.resolve("recovery-state");
        SchedulerStore store = new SchedulerStore(state, 10);
        store.load();
        for (int i = 0; i < 2; i++) {
            OpenItem item = new OpenItem();
            item.setId("expired-" + i);
            item.setTitle("expired item");
            item.setCommand("feedA.connect");
            item.setExpiresAt(1);
            store.open().put(item.getId(), item);
        }
        store.saveOpen();
        Path log = Files.createDirectories(dir.resolve("unavailable-log.yaml"));
        ActionScheduler recovering = new ActionScheduler();
        recovering.setStateDirectory(state.toString());
        recovering.setOutcomeLogFile(log.toString());
        recovering.setCatalogueFile(dir.resolve("catalogue.yaml").toString());
        recovering.adminRegistry(registry, "admin");
        try {
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(recovering::init);
            assertEquals(2, recovering.getEngine().open().size());
            assertTrue(recovering.getEngine().open().stream().allMatch(i -> i.getPendingOutcome() != null));
            Files.delete(log);
            recovering.getEngine().tick();
            assertTrue(recovering.getEngine().open().isEmpty());
            assertEquals(2, recovering.getEngine().history(0, Long.MAX_VALUE).size());
        } finally {
            recovering.tearDown();
        }
    }

    @Test
    void runRestartActionsWithNothingScheduledIsRefused() {
        assertTrue(run("scheduler.runRestartActions", "trader").err.contains("no enabled after-restart"));
    }

    // ==================== helpers ====================

    static void sleepQuietly(long millis) {
        long end = System.currentTimeMillis() + millis; // ignores interrupts, like a real command
        while (System.currentTimeMillis() < end) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException ignored) {
                // keep going
            }
        }
    }

    ActionScheduler schedulerOn(AdminCommandRegistry server, String state) throws Exception {
        Path catalogue = dir.resolve("catalogue.yaml");
        ActionScheduler s = new ActionScheduler();
        s.setStateDirectory(dir.resolve(state).toString());
        s.setOutcomeLogFile(dir.resolve(state + "/out.yaml").toString());
        s.setCatalogueFile(catalogue.toString());
        s.setEnvironment("dev");
        s.setCommandTimeoutSeconds(1);
        s.adminRegistry(server, "admin");
        s.init();
        return s;
    }

    JsonNode okOn(AdminCommandRegistry server, String command, String... args) throws Exception {
        Reply r = runOn(server, command, args);
        assertTrue(r.err.isEmpty(), command + " refused: " + r.err);
        return json.readTree(r.out);
    }

    Reply runOn(AdminCommandRegistry server, String command, String... args) {
        Reply r = new Reply();
        AdminCommandRequest req = new AdminCommandRequest();
        req.setCommand(command);
        req.setArguments(new ArrayList<>(List.of(args)));
        req.setOutput(o -> r.out += o);
        req.setErrOutput(o -> r.err += o);
        server.processAdminCommandRequest(req);
        return r;
    }

    /**
     * Dispatches like the server does for a processor's command: publish it to the processor thread, then wait
     * on a semaphore (interruptibly, no timeout) until the processor has run it. The scheduler's own commands
     * run on the caller's thread, as commands registered outside a processor do.
     */
    static class ProcessorRegistry extends FakeRegistry {
        final ExecutorService processor = Executors.newSingleThreadExecutor();

        @Override
        public void processAdminCommandRequest(AdminCommandRequest request) {
            if (request.getCommand().startsWith("scheduler.")) {
                super.processAdminCommandRequest(request);
                return;
            }
            java.util.concurrent.Semaphore done = new java.util.concurrent.Semaphore(0);
            processor.execute(() -> {
                try {
                    super.processAdminCommandRequest(request);
                } finally {
                    done.release();
                }
            });
            try {
                done.acquire();
            } catch (InterruptedException e) {
                throw new IllegalStateException("Interrupted while publishing admin command", e);
            }
        }

        void stop() {
            processor.shutdownNow();
        }
    }

    JsonNode ok(String command, String... args) throws Exception {
        Reply r = run(command, args);
        assertTrue(r.err.isEmpty(), command + " refused: " + r.err);
        return json.readTree(r.out);
    }

    Reply run(String command, String... args) {
        Reply r = new Reply();
        AdminCommandRequest req = new AdminCommandRequest();
        req.setCommand(command);
        req.setArguments(new ArrayList<>(List.of(args)));
        req.setOutput(o -> r.out += o);
        req.setErrOutput(o -> r.err += o);
        registry.processAdminCommandRequest(req);
        return r;
    }

    static class Reply {
        String out = "";
        String err = "";

        @Override
        public String toString() {
            return "out=" + out + " err=" + err;
        }
    }

    /** Runs a command on the caller's thread with its name in front of the arguments, as the server does. */
    static class FakeRegistry implements AdminCommandRegistry {
        final Map<String, AdminFunction<Object, Object>> commands = new ConcurrentHashMap<>();

        @SuppressWarnings("unchecked")
        @Override
        public <OUT, ERR> void registerCommand(String name, AdminFunction<OUT, ERR> command) {
            commands.put(name, (AdminFunction<Object, Object>) (AdminFunction<?, ?>) command);
        }

        @Override
        public void processAdminCommandRequest(AdminCommandRequest request) {
            AdminFunction<Object, Object> fn = commands.get(request.getCommand());
            if (fn == null) return; // the server only logs an unknown command
            List<String> args = new ArrayList<>(request.getArguments());
            args.add(0, request.getCommand());
            fn.processAdminCommand(args, request.getOutput(), request.getErrOutput());
        }

        @Override
        public List<String> commandList() {
            return new ArrayList<>(commands.keySet());
        }
    }
}
