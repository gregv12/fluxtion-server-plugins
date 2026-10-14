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
            try {
                Thread.sleep(3_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
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
    void pauseAndStatus() throws Exception {
        assertEquals(false, ok("scheduler.status").get("paused").asBoolean());
        assertEquals(true, ok("scheduler.pause", "ops").get("paused").asBoolean());
        assertEquals(false, ok("scheduler.resume", "ops").get("paused").asBoolean());
        JsonNode summary = ok("scheduler.summary");
        assertEquals("dev", summary.get("environment").asText());
        assertEquals(0, summary.get("open").asInt());
    }

    @Test
    void runRestartActionsWithNothingScheduledIsRefused() {
        assertTrue(run("scheduler.runRestartActions", "trader").err.contains("no enabled after-restart"));
    }

    // ==================== helpers ====================

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
