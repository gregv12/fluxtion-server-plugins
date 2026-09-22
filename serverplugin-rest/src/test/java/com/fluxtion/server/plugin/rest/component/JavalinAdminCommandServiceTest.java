package com.fluxtion.server.plugin.rest.component;

import com.fluxtion.runtime.lifecycle.Lifecycle;
import com.fluxtion.server.service.LifeCycleEventSource;
import com.fluxtion.server.service.admin.AdminCommandRegistry;
import com.fluxtion.server.service.admin.AdminCommandRequest;
import com.fluxtion.server.service.admin.AdminFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class JavalinAdminCommandServiceTest {

    private JavalinAdminCommandService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.tearDown();
        }
    }

    /**
     * A valid request ({@code command} present) reaches the registry and the command's output is
     * returned as an escaped JSON {@code {"message": ...}} with HTTP 200.
     */
    @Test
    void validCommand_callsRegistry_andReturns200_withJsonBody() throws Exception {
        int port = findFreePort();
        service = new JavalinAdminCommandService();
        service.setListenPort(port);

        AtomicInteger calls = new AtomicInteger(0);
        AtomicReference<String> seenCommand = new AtomicReference<>();
        service.adminRegistry(echoingRegistry(calls, seenCommand), "admin");
        service.init();

        HttpResponse<String> resp = post(port, "{\"command\":\"status\",\"arguments\":[]}");

        assertEquals(200, resp.statusCode(), "valid command should return 200");
        assertEquals(1, calls.get(), "registry should have been invoked exactly once");
        assertEquals("status", seenCommand.get(), "the command name should be parsed and forwarded");
        assertTrue(resp.body().contains("\"message\""), "expected JSON body with a 'message' field");
        assertTrue(resp.body().contains("OK"), "expected the command output 'OK' in the body");
    }

    /**
     * The output is emitted via a real JSON mapper, so control characters (newlines, quotes) in a
     * command's output are correctly escaped and the response stays valid JSON.
     */
    @Test
    void commandOutputWithControlChars_isEscaped() throws Exception {
        int port = findFreePort();
        service = new JavalinAdminCommandService();
        service.setListenPort(port);

        AdminCommandRegistry registry = new AdminCommandRegistry() {
            @Override
            public <OUT, ERR> void registerCommand(String name, AdminFunction<OUT, ERR> command) { }

            @Override
            public void processAdminCommandRequest(AdminCommandRequest request) {
                writeToOutput(request, "line1\nline2 \"quoted\"");
            }

            @Override
            public List<String> commandList() { return List.of(); }
        };
        service.adminRegistry(registry, "admin");
        service.init();

        HttpResponse<String> resp = post(port, "{\"command\":\"status\"}");

        assertEquals(200, resp.statusCode());
        // A raw newline or unescaped quote would make this invalid JSON; the escaped forms must appear.
        assertTrue(resp.body().contains("line1\\nline2"), "newline should be escaped as \\n");
        assertTrue(resp.body().contains("\\\"quoted\\\""), "inner quotes should be escaped");
        assertFalse(resp.body().contains("line1\nline2"), "the body must not contain a raw newline");
    }

    /** A valid command with no registry wired returns 503, not 200. */
    @Test
    void validCommand_noRegistry_returns503() throws Exception {
        int port = findFreePort();
        service = new JavalinAdminCommandService();
        service.setListenPort(port);
        service.init();

        HttpResponse<String> resp = post(port, "{\"command\":\"status\"}");
        assertEquals(503, resp.statusCode(), "no registry wired should surface as 503");
        assertTrue(resp.body().contains("registry not available"), "503 body should explain why");
    }

    /** A body with no {@code command} field is a 400 that names the expected shape. */
    @Test
    void missingCommand_returns400() throws Exception {
        int port = findFreePort();
        service = new JavalinAdminCommandService();
        service.setListenPort(port);
        AtomicInteger calls = new AtomicInteger(0);
        service.adminRegistry(echoingRegistry(calls, new AtomicReference<>()), "admin");
        service.init();

        HttpResponse<String> resp = post(port, "{}");
        assertEquals(400, resp.statusCode(), "missing 'command' should be a 400");
        assertTrue(resp.body().contains("command"), "400 body should mention the missing 'command'");
        assertEquals(0, calls.get(), "registry must not be invoked for an invalid request");
    }

    /** A body that does not deserialize to a request is a 400, not a bare 500. */
    @Test
    void malformedBody_returns400() throws Exception {
        int port = findFreePort();
        service = new JavalinAdminCommandService();
        service.setListenPort(port);
        service.init();

        HttpResponse<String> resp = post(port, "[1,2,3]");
        assertEquals(400, resp.statusCode(), "a body that is not an admin request should be a 400");
        assertTrue(resp.body().contains("invalid request body"), "400 body should name the problem");
    }

    @Test
    void tearDown_stopsServer_andSubsequentConnectFails() throws Exception {
        int port = findFreePort();
        service = new JavalinAdminCommandService();
        service.setListenPort(port);
        service.init();
        service.tearDown();

        // Wait briefly to allow the server to release the port
        TimeUnit.MILLISECONDS.sleep(200);

        var client = HttpClient.newHttpClient();
        var req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin"))
                .timeout(java.time.Duration.ofSeconds(2))
                .POST(HttpRequest.BodyPublishers.ofString("{\"command\":\"status\"}"))
                .header("Content-Type", "application/json")
                .build();

        try {
            client.send(req, HttpResponse.BodyHandlers.ofString());
            fail("Expected connection to fail after tearDown");
        } catch (IOException e) {
            // On most platforms this will be a ConnectException
            assertTrue(e instanceof ConnectException || e.getCause() instanceof ConnectException,
                    "Expected a ConnectException after server is stopped");
        }
    }

    /**
     * Regression guard: the server's LifecycleManager only invokes init()/start()
     * for plain (non-LifeCycleEventSource) services. JavalinAdminCommandService binds
     * its HTTP listener in init(), so it MUST be a plain Lifecycle service and must NOT
     * be a LifeCycleEventSource — otherwise the webadmin silently never starts.
     */
    @Test
    void isPlainLifecycleService_notAnEventSource() {
        JavalinAdminCommandService svc = new JavalinAdminCommandService();
        assertTrue(svc instanceof Lifecycle,
                "must be a Lifecycle service so init()/start() are invoked by the server");
        assertFalse(svc instanceof LifeCycleEventSource,
                "must NOT be a LifeCycleEventSource — those are skipped by the plain-service "
                        + "lifecycle loop, so init() (the Javalin bind) would never run");
    }

    // ==================== helpers ====================

    /** A registry that records the call, captures the command name, and echoes "OK" to the output. */
    private static AdminCommandRegistry echoingRegistry(AtomicInteger calls, AtomicReference<String> seenCommand) {
        return new AdminCommandRegistry() {
            @Override
            public <OUT, ERR> void registerCommand(String name, AdminFunction<OUT, ERR> command) { }

            @Override
            public void processAdminCommandRequest(AdminCommandRequest request) {
                calls.incrementAndGet();
                try {
                    Method getCommand = request.getClass().getMethod("getCommand");
                    seenCommand.set((String) getCommand.invoke(request));
                } catch (Exception ignore) {
                    // command name not reflectable in this API shape; the count assertion still holds
                }
                writeToOutput(request, "OK");
            }

            @Override
            public List<String> commandList() { return List.of(); }
        };
    }

    /** Reflectively drive the output Consumer the service set on the request. */
    @SuppressWarnings("unchecked")
    private static void writeToOutput(AdminCommandRequest request, String value) {
        try {
            Method getOutput = request.getClass().getMethod("getOutput");
            Object outputConsumer = getOutput.invoke(request);
            if (outputConsumer instanceof java.util.function.Consumer) {
                ((java.util.function.Consumer<Object>) outputConsumer).accept(value);
            }
        } catch (NoSuchMethodException e) {
            // no output getter — handler still returns 200 without a body
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static HttpResponse<String> post(int port, String body) throws Exception {
        var client = HttpClient.newHttpClient();
        var req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin"))
                .timeout(java.time.Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setReuseAddress(true);
            return serverSocket.getLocalPort();
        }
    }
}
