package com.fluxtion.server.plugin.trading.component.mockvenue.wsmktdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxtion.agrona.concurrent.Agent;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.server.plugin.trading.component.mockvenue.mktdata.MarketDataBookConfig;
import com.fluxtion.server.plugin.trading.component.mockvenue.mktdata.MarketDataBookGenerator;
import com.fluxtion.server.plugin.trading.component.websocket.WsMarketDataMessage;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketDataBook;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketFeedEvent;
import com.fluxtion.server.plugin.trading.service.marketdata.MultilevelMarketDataBook;
import com.fluxtion.server.service.admin.AdminCommandRegistry;
import com.fluxtion.server.service.scheduler.SchedulerService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mock WebSocket market-data venue (server side). Speaks the {@link WsMarketDataMessage} JSON schema
 * over a hand-rolled RFC 6455 endpoint on a raw {@link ServerSocket} (no WebSocket library). Generates
 * books on a {@link SchedulerService} timer using the shared {@link MarketDataBookGenerator} and
 * broadcasts them to all connected clients. Controlled via admin commands. Standalone service — not a
 * feed, not an agent; it owns its accept/reader threads because a blocking accept cannot live in doWork().
 */
@Log4j2
public class MockWsMarketDataPublisher implements Agent {

    private static final Pattern KEY = Pattern.compile("Sec-WebSocket-Key:\\s*(.+)\\r\\n", Pattern.CASE_INSENSITIVE);

    @Getter @Setter private String name = "simulatedWsVenue";
    @Getter @Setter private int port = 8451;
    @Getter @Setter private int publishRateMillis = 1000;
    @Getter @Setter private List<MarketDataBookConfig> marketDataBookConfigs = new ArrayList<>();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<ClientConnection> clients = new CopyOnWriteArraySet<>();
    private final AtomicLong bookId = new AtomicLong();

    private SchedulerService schedulerService;
    private volatile ServerSocket serverSocket;
    private volatile boolean started = false;
    private volatile boolean running = true;
    private volatile boolean paused = false;
    /** When true, only publish books whose symbol a client has subscribed to (a real venue). */
    @Getter @Setter private boolean requireSubscription = false;
    private final Set<String> subscribedSymbols = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @ServiceRegistered
    public void scheduler(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
        ensureStarted();
        this.schedulerService.scheduleAfterDelay(publishRateMillis, this::timerTriggered);
    }

    @ServiceRegistered
    public void adminClient(AdminCommandRegistry registry) {
        registry.registerCommand(name + ".start", this::startCmd);
        registry.registerCommand(name + ".stop", this::stopCmd);
        registry.registerCommand(name + ".pause", this::pauseCmd);
        registry.registerCommand(name + ".resume", this::resumeCmd);
        registry.registerCommand(name + ".setPublishRate", this::setPublishRateCmd);
        registry.registerCommand(name + ".getStatus", this::getStatusCmd);
        registry.registerCommand(name + ".listConfigs", this::listConfigsCmd);
        registry.registerCommand(name + ".clients", this::clientsCmd);
    }

    @Override
    public String roleName() {
        return name;
    }

    @Override
    public int doWork() {
        return 0; // publishing is timer-driven off SchedulerService; accept/read on own threads
    }

    public synchronized void ensureStarted() {
        if (started) {
            return;
        }
        try {
            serverSocket = new ServerSocket(port);
            started = true;
            Thread accept = new Thread(this::acceptLoop, name + "-accept");
            accept.setDaemon(true);
            accept.start();
            log.info("mock ws venue {} listening on port {}", name, port);
        } catch (IOException e) {
            log.error("failed to open mock ws venue on port {}", port, e);
        }
    }

    // ==================== socket accept + handshake ====================

    private void acceptLoop() {
        while (started && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                if (handshake(socket)) {
                    ClientConnection client = new ClientConnection(socket);
                    clients.add(client);
                    Thread reader = new Thread(() -> readLoop(client), name + "-client");
                    reader.setDaemon(true);
                    reader.start();
                    log.info("mock ws venue {} client connected {}", name, socket.getRemoteSocketAddress());
                }
            } catch (IOException e) {
                if (started) {
                    log.debug("accept loop ended: {}", e.getMessage());
                }
                return;
            }
        }
    }

    private boolean handshake(Socket socket) throws IOException {
        InputStream in = socket.getInputStream();
        StringBuilder req = new StringBuilder();
        int b;
        while ((b = in.read()) != -1) {
            req.append((char) b);
            if (req.length() >= 4 && req.substring(req.length() - 4).equals("\r\n\r\n")) {
                break;
            }
        }
        Matcher m = KEY.matcher(req.toString());
        if (!m.find()) {
            socket.close();
            return false;
        }
        String accept = WsFrames.acceptKey(m.group(1).trim());
        String response = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";
        socket.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));
        socket.getOutputStream().flush();
        return true;
    }

    private void readLoop(ClientConnection client) {
        try {
            InputStream in = client.socket.getInputStream();
            WsFrames.Frame frame;
            while ((frame = WsFrames.readFrame(in)) != null) {
                switch (frame.opcode()) {
                    case WsFrames.OP_CLOSE -> {
                        disconnect(client);
                        return;
                    }
                    case WsFrames.OP_PING -> client.send(WsFrames.encode(WsFrames.OP_PONG, frame.payload()));
                    case WsFrames.OP_TEXT -> onClientText(frame.text());
                    default -> { /* ignore */ }
                }
            }
        } catch (IOException e) {
            log.debug("client read ended: {}", e.getMessage());
        } finally {
            disconnect(client);
        }
    }

    private void disconnect(ClientConnection client) {
        if (clients.remove(client)) {
            client.close();
            log.info("mock ws venue {} client disconnected", name);
        }
    }

    /** Handle an inbound client text frame — a {@code {"type":"subscribe","symbol":X}} request. */
    private void onClientText(String text) {
        try {
            WsMarketDataMessage msg = objectMapper.readValue(text, WsMarketDataMessage.class);
            if (msg.getSymbol() == null) {
                return;
            }
            if (WsMarketDataMessage.TYPE_SUBSCRIBE.equals(msg.getType())) {
                subscribedSymbols.add(msg.getSymbol());
                log.info("mock ws venue {} subscribed {} (now {})", name, msg.getSymbol(), subscribedSymbols);
            } else if (WsMarketDataMessage.TYPE_UNSUBSCRIBE.equals(msg.getType())) {
                subscribedSymbols.remove(msg.getSymbol());
                log.info("mock ws venue {} unsubscribed {} (now {})", name, msg.getSymbol(), subscribedSymbols);
            }
        } catch (Exception e) {
            log.debug("mock ws venue {} ignoring client message: {}", name, text);
        }
    }

    // ==================== publish timer ====================

    public void timerTriggered() {
        if (schedulerService != null) {
            schedulerService.scheduleAfterDelay(publishRateMillis, this::timerTriggered);
        }
        if (!running || paused || clients.isEmpty()) {
            return;
        }
        for (MarketDataBookConfig config : marketDataBookConfigs) {
            if (requireSubscription && !subscribedSymbols.contains(config.getSymbol())) {
                continue; // real-venue behaviour: only stream what a client has subscribed to
            }
            MarketFeedEvent event = config.isMultilevel()
                    ? MarketDataBookGenerator.generateRandomMultilevel(config, config.getMultilevelDepth(), config.getMultilevelBookConfig())
                    : MarketDataBookGenerator.generateRandom(config);
            if (event != null) {
                broadcast(toMessage(event));
            }
        }
    }

    private WsMarketDataMessage toMessage(MarketFeedEvent event) {
        if (event instanceof MarketDataBook book) {
            book.setId(bookId.incrementAndGet());
            return WsMarketDataMessage.fromBook(book);
        }
        MultilevelMarketDataBook book = (MultilevelMarketDataBook) event;
        book.setId(bookId.incrementAndGet());
        return WsMarketDataMessage.fromMultilevel(book);
    }

    public int clientCount() {
        return clients.size();
    }

    /** Symbols a client has subscribed to (live view). Exposed for tests. */
    java.util.Set<String> subscribedSymbols() {
        return subscribedSymbols;
    }

    void broadcast(WsMarketDataMessage message) {
        final byte[] frame;
        try {
            frame = WsFrames.encodeText(objectMapper.writeValueAsString(message));
        } catch (Exception e) {
            log.error("failed to serialize ws message", e);
            return;
        }
        for (ClientConnection client : clients) {
            try {
                client.send(frame);
            } catch (IOException e) {
                disconnect(client);
            }
        }
    }

    // ==================== admin commands ====================

    private void startCmd(List<String> a, Consumer<String> out, Consumer<String> err) {
        ensureStarted();
        running = true;
        paused = false;
        out.accept(name + " started; clients=" + clients.size());
    }

    private void stopCmd(List<String> a, Consumer<String> out, Consumer<String> err) {
        running = false;
        out.accept(name + " stopped (publishing halted; socket stays open)");
    }

    private void pauseCmd(List<String> a, Consumer<String> out, Consumer<String> err) {
        paused = true;
        out.accept(name + " paused");
    }

    private void resumeCmd(List<String> a, Consumer<String> out, Consumer<String> err) {
        paused = false;
        out.accept(name + " resumed");
    }

    private void setPublishRateCmd(List<String> a, Consumer<String> out, Consumer<String> err) {
        if (a.size() != 2) {
            err.accept("setPublishRate requires 1 argument [rateMillis]");
            return;
        }
        try {
            int rate = Integer.parseInt(a.get(1));
            if (rate < 1) {
                err.accept("rate must be positive");
                return;
            }
            int old = publishRateMillis;
            publishRateMillis = rate;
            out.accept("publish rate " + old + "ms -> " + rate + "ms");
        } catch (NumberFormatException e) {
            err.accept("invalid number: " + a.get(1));
        }
    }

    private void getStatusCmd(List<String> a, Consumer<String> out, Consumer<String> err) {
        out.accept(String.format(
                "%s%n  port: %d%n  running: %s%n  paused: %s%n  publishRateMillis: %d%n  clients: %d%n  configuredSymbols: %d%n  requireSubscription: %s%n  subscribed: %s",
                name, port, running, paused, publishRateMillis, clients.size(), marketDataBookConfigs.size(),
                requireSubscription, subscribedSymbols));
    }

    private void listConfigsCmd(List<String> a, Consumer<String> out, Consumer<String> err) {
        if (marketDataBookConfigs.isEmpty()) {
            out.accept("no book configs");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (MarketDataBookConfig c : marketDataBookConfigs) {
            sb.append(String.format("  %s [%.5f-%.5f] %s%n", c.getSymbol(), c.getMinPrice(), c.getMaxPrice(),
                    c.isMultilevel() ? "multilevel-" + c.getMultilevelDepth() : "single-level"));
        }
        out.accept(sb.toString());
    }

    private void clientsCmd(List<String> a, Consumer<String> out, Consumer<String> err) {
        out.accept(name + " connected clients: " + clients.size());
    }

    // ==================== client connection ====================

    private static final class ClientConnection {
        private final Socket socket;
        private final OutputStream out;

        ClientConnection(Socket socket) throws IOException {
            this.socket = socket;
            this.out = socket.getOutputStream();
        }

        synchronized void send(byte[] frame) throws IOException {
            out.write(frame);
            out.flush();
        }

        void close() {
            try {
                socket.close();
            } catch (IOException ignore) {
                // best effort
            }
        }
    }
}
