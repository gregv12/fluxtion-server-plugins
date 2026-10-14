package com.fluxtion.server.plugin.trading.component.websocket;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocketHandshakeException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reconnect pacing and connect-failure reporting: a venue that keeps refusing the handshake (for example a
 * rejected API key) must not be retried every 500ms forever, and the log/status must say why it refused.
 */
class WsReconnectBackoffTest {

    // ---------------- delay schedule ----------------

    @Test
    void beforeTheFirstConnectTheFirstAttemptsRetryFast() {
        for (int failures = 1; failures <= 10; failures++) {
            assertEquals(500, AbstractWsMarketDataFeed.reconnectDelay(false, failures, 5000, 10, 60_000));
        }
        assertEquals(200, AbstractWsMarketDataFeed.reconnectDelay(false, 1, 200, 10, 60_000),
                "fast retry never slower than reconnectMillis");
    }

    @Test
    void afterTheFastAttemptsItBacksOffFromReconnectMillisToTheCap() {
        assertEquals(5_000, AbstractWsMarketDataFeed.reconnectDelay(false, 11, 5000, 10, 60_000));
        assertEquals(10_000, AbstractWsMarketDataFeed.reconnectDelay(false, 12, 5000, 10, 60_000));
        assertEquals(20_000, AbstractWsMarketDataFeed.reconnectDelay(false, 13, 5000, 10, 60_000));
        assertEquals(40_000, AbstractWsMarketDataFeed.reconnectDelay(false, 14, 5000, 10, 60_000));
        assertEquals(60_000, AbstractWsMarketDataFeed.reconnectDelay(false, 15, 5000, 10, 60_000));
        assertEquals(60_000, AbstractWsMarketDataFeed.reconnectDelay(false, 500, 5000, 10, 60_000), "capped, no overflow");
    }

    @Test
    void afterADropTheFirstRetryIsReconnectMillisThenItBacksOff() {
        assertEquals(5_000, AbstractWsMarketDataFeed.reconnectDelay(true, 0, 5000, 10, 60_000), "drop: no failures yet");
        assertEquals(5_000, AbstractWsMarketDataFeed.reconnectDelay(true, 1, 5000, 10, 60_000));
        assertEquals(10_000, AbstractWsMarketDataFeed.reconnectDelay(true, 2, 5000, 10, 60_000));
        assertEquals(60_000, AbstractWsMarketDataFeed.reconnectDelay(true, 10, 5000, 10, 60_000));
    }

    @Test
    void aCapAtReconnectMillisMeansAFixedInterval() {
        assertEquals(5_000, AbstractWsMarketDataFeed.reconnectDelay(true, 5, 5000, 10, 5000));
        assertEquals(5_000, AbstractWsMarketDataFeed.reconnectDelay(true, 5, 5000, 10, 1000),
                "a cap below reconnectMillis never shortens the interval");
    }

    // ---------------- failure description ----------------

    @Test
    void aRejectedHandshakeNamesItsHttpStatusAndBody() {
        Throwable ex = new CompletionException(new WebSocketHandshakeException(response(404, "Route not found")));
        assertEquals("handshake rejected: HTTP 404 Route not found", AbstractWsMarketDataFeed.describeConnectFailure(ex));
    }

    @Test
    void aRejectedHandshakeWithoutABodyStillNamesTheStatus() {
        Throwable ex = new CompletionException(new WebSocketHandshakeException(response(401, null)));
        assertEquals("handshake rejected: HTTP 401", AbstractWsMarketDataFeed.describeConnectFailure(ex));
    }

    @Test
    void otherFailuresReportTheUnderlyingCause() {
        Throwable ex = new CompletionException(new java.net.ConnectException("Connection refused"));
        assertEquals("java.net.ConnectException: Connection refused", AbstractWsMarketDataFeed.describeConnectFailure(ex));
    }

    // ---------------- against a server that refuses the upgrade ----------------

    @Test
    void aVenueRefusingTheHandshakeIsBackedOffAndTheStatusIsReported() throws Exception {
        List<Long> attempts = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            attempts.add(System.currentTimeMillis());
            byte[] body = "Route not found".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        WsMarketDataFeedIntegrationTest.CapturingFeed feed = new WsMarketDataFeedIntegrationTest.CapturingFeed();
        try {
            feed.setFeedName("refusedFeed");
            feed.setUrl("ws://localhost:" + server.getAddress().getPort() + "/");
            feed.setReconnectMillis(300);
            feed.setFastReconnectAttempts(2);
            feed.setMaxReconnectMillis(1_200);
            feed.connect();
            Thread.sleep(4_000);
        } finally {
            feed.tearDown();
            server.stop(0);
        }

        assertTrue(feed.lastConnectError() != null && feed.lastConnectError().startsWith("handshake rejected: HTTP 404"),
                "status code reported: " + feed.lastConnectError());
        assertTrue(feed.consecutiveConnectFailures() >= 4, "failures counted: " + feed.consecutiveConnectFailures());
        // fixed 300ms retries would make ~13 attempts in 4s; the backoff (300, 300, 300, 600, 1200, 1200) makes ~7
        assertTrue(attempts.size() <= 9, "backed off, not hammered: " + attempts.size() + " attempts");
        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < attempts.size(); i++) {
            gaps.add(attempts.get(i) - attempts.get(i - 1));
        }
        assertTrue(gaps.get(gaps.size() - 1) >= 1_000, "the interval grew to the cap: gaps " + gaps);
    }

    private static HttpResponse<String> response(int status, String body) {
        return new HttpResponse<>() {
            @Override public int statusCode() { return status; }
            @Override public HttpRequest request() { return HttpRequest.newBuilder(URI.create("ws://localhost/")).build(); }
            @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
            @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
            @Override public String body() { return body; }
            @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
            @Override public URI uri() { return URI.create("ws://localhost/"); }
            @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }
}
