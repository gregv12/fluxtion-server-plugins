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
