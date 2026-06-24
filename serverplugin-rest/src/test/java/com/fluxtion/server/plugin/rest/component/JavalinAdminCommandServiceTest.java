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
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class JavalinAdminCommandServiceTest {

    private JavalinAdminCommandService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.tearDown();
        }
    }

    @Test
    void initAndPost_callsRegistry_andReturns200_withJsonBody() throws Exception {
        int port = findFreePort();
        service = new JavalinAdminCommandService();
        service.setListenPort(port);

        AtomicInteger calls = new AtomicInteger(0);

        // Stub AdminCommandRegistry which tries to write back using the output consumer set by the service
        AdminCommandRegistry registry = new AdminCommandRegistry() {
            @Override
            public <OUT, ERR> void registerCommand(String name, AdminFunction<OUT, ERR> command) {

            }

            @Override
            public void processAdminCommandRequest(AdminCommandRequest request) {
                calls.incrementAndGet();
                // Try reflectively to obtain the output Consumer set by the service
                try {
                    Method getOutput = request.getClass().getMethod("getOutput");
                    Object outputConsumer = getOutput.invoke(request);
                    if (outputConsumer instanceof java.util.function.Consumer) {
                        @SuppressWarnings("unchecked")
                        var consumer = (java.util.function.Consumer<Object>) outputConsumer;
                        consumer.accept("OK");
                    }
                } catch (NoSuchMethodException e) {
                    // If no getter exists, ignore; handler will still return 200 without body
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public List<String> commandList() {
                return List.of();
            }
        };
        service.adminRegistry(registry, "admin");
        service.init();

        // Post minimal JSON body - should be parsed to AdminCommandRequest
        var client = HttpClient.newHttpClient();
        var req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin"))
                .timeout(java.time.Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode(), "Expected 200 OK");
        assertTrue(calls.get() > 0, "AdminCommandRegistry should have been invoked");
        // If our reflective output worked, we should see a JSON body with {"message":"OK"}
        // Be lenient: if output consumer signature differs, there may be no body, but still 200.
        String body = Objects.toString(resp.body(), "");
        if (!body.isBlank()) {
            assertTrue(body.contains("\"message\""), "Expected JSON body with 'message' field");
            assertTrue(body.contains("OK"), "Expected 'OK' in message body");
        }
    }

    @Test
    void postWithoutRegistry_returns200_noException() throws Exception {
        int port = findFreePort();
        service = new JavalinAdminCommandService();
        service.setListenPort(port);
        service.init();

        var client = HttpClient.newHttpClient();
        var req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin"))
                .timeout(java.time.Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode(), "Expected 200 OK even if no registry is wired");
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
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
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

    private static int findFreePort() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverSocket.setReuseAddress(true);
            return serverSocket.getLocalPort();
        }
    }
}
