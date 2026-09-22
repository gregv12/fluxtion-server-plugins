# WebSocket market data feed — spec, tracking & test plan

**Status:** in progress. This doc is the restart anchor — read it first, then the Phase Tracker to see where to resume.

## Goal
A live WebSocket market-data **feed** (client) that plugs into the trading plugin like the FIX
feeds, plus a **mock WebSocket venue** (server) to drive it locally, wired into
`maker-fxoc/run_localhost`, with an admin control surface, tests both sides, and a minimal
audit-logging test strategy so the run is visible in the analyser.

## Constraints / decisions
- **Java 21, no WebSocket library.** Client uses the JDK's built-in `java.net.http.WebSocket`.
  The mock server has no JDK WS server, so it hand-rolls the RFC 6455 handshake + text framing on a
  raw `ServerSocket`.
- **JSON = Jackson** (already on the compile classpath — `MockMarketDataFeedWorker` uses it). No new deps.
- **Thread model — the feed is NOT an agent.** It extends `AbstractMarketDataFeed` (not
  `AbstractMarketDataFeedWorker`) and publishes straight from the WebSocket callback, exactly like
  `TalosQuickFixMarketDataFeed` publishes from the FIX I/O thread. The inherited `targetQueue`
  (`EventToQueuePublisher`) is the thread-safe boundary into the Fluxtion processor. The JDK
  `HttpClient` is built on a **dedicated single-thread executor we own** (named, lifecycle-managed)
  so delivery is single-threaded, ordered, and controllable — "our thread" without agent machinery.
  Reconnect/heartbeat ride `SchedulerService`; the agent Worker base is only for feeds that own a
  poll loop (the replay feeds), which we don't.
- **Mock server DOES own threads:** blocking `ServerSocket.accept()` + per-client reader can't live
  in a cooperative `doWork()`, so it spawns its own daemon threads and drives publication off
  `SchedulerService` (timer; `MockMarketDataFeedWorker` is the template, `doWork()`→0). It is a
  standalone service, not a feed, not an agent.
- **Per-symbol unsubscribe + end-of-subscription signal.** `MarketDataFeed.unsubscribe(feed,venue,symbol)`
  is a default no-op on the interface; the WS feed overrides it to drop the local subscription and send
  the venue `{"type":"unsubscribe","symbol":…}` frame (the mock venue stops streaming that symbol).
  **Short term (now):** on unsubscribe the feed also publishes an EMPTY `MarketDataBook` (all
  price/qty/order fields zero) for the symbol, so a listener sees the subscription end and its series
  flatlines to 0 rather than freezing at the last tick. **Deferred (not yet — decide the shape):** a
  dedicated `MarketDataListener` end-of-subscription callback (e.g. `marketDataSubscriptionEnded(...)`).
  The empty-book approach is the placeholder until that method is designed; `MarketDataListener` is left
  unchanged for now.

## Wire schema (JSON, our own DTO — decoupled from domain types)
`WsMarketDataMessage` (Jackson `@Data`, nullable fields):
```
type    : "book" | "multilevel" | "connected" | "disconnected"   (server→client)
          "subscribe"                                            (client→server)
feed, venue, symbol : String
id      : long
single-level: bid, bidQty (double), bidOrders (int); ask, askQty, askOrders
multilevel : bids[], asks[]  where each Level = {price, qty, orders}
```
Feed maps `book`→`new MarketDataBook(feed,venue,symbol,id,bid,bidQty,ask,askQty)` (+order counts);
`multilevel`→`new MultilevelMarketDataBook(...)` then `updateBid/updateAsk(price,qty,orders)` per
level; `connected`→`MarketConnected(feed)`; `disconnected`→`MarketDisconnected(feed)`.
Client sends `{"type":"subscribe","symbol":"USD-MXN"}` on subscribe.

## Components (target size ~150–250 lines for feed + mock)
Repo `serverplugin-trading`:
1. `component/websocket/WsMarketDataMessage.java` — the wire DTO + `Level`. Jackson `@Data`. (shared)
2. `component/websocket/AbstractWsMarketDataFeed.java` — extends `AbstractMarketDataFeed`. Owns the
   JDK `HttpClient`+`WebSocket` on a single-thread executor; `WebSocket.Listener` (onOpen→
   `MarketConnected`+replay cached subs; onText w/ fragment reassembly→`onMessage(text)`;
   onClose/onError→`MarketDisconnected`+schedule reconnect). Implements `subscribeToSymbol` (cache;
   send subscribe frame if open). `@ServiceRegistered scheduler(...)` (connect+reconnect watchdog),
   `@ServiceRegistered adminClient(...)`. Abstract hooks for the exchange:
   `String subscribeFrame(feed,venue,symbol)` and `void onMessage(String rawJson)` (parses→`publish`).
   Config setters: `url`, `feedName`, `venueNameSet`, `reconnectMillis`.
3. `component/websocket/GenericJsonWsMarketDataFeed.java` — concrete exchange impl for our JSON
   schema (parses `WsMarketDataMessage`, builds subscribe frames). The "extend per exchange" example.
4. `component/mockvenue/wsmktdata/WsFrames.java` — RFC 6455 static helpers: `acceptKey(String)`
   (SHA-1+base64 of key+GUID), `encodeText(String)→byte[]`, `decodeClientFrame(InputStream)→String`.
5. `component/mockvenue/wsmktdata/MockWsMarketDataPublisher.java` — standalone service: `ServerSocket`
   accept loop + per-client reader threads; `SchedulerService` timer generates books via the existing
   `MarketDataBookGenerator`+`MarketDataBookConfig`, serializes to `WsMarketDataMessage` JSON,
   broadcasts. `@ServiceRegistered adminClient(...)` mirrors the mock surface. Config: `port`, `path`,
   `publishRateMillis`, `List<MarketDataBookConfig> marketDataBookConfigs`.

Repo `market-maker-lib`:
6. `node/SubscribeAndLogNode.java` — `extends SingleNamedNode implements @ExportService MarketDataListener`;
   `@ServiceRegistered marketDataFeed(MarketDataFeed,String)` + `subscribe`; each callback →
   `auditLog.info(...)`. (Modeled on trade-calculator `MarketDataBookNode`.)
7. `builder/SubscribeAndLogStrategyBuilder.java` — `FluxtionGraphBuilder`; `buildGraph`→addNode+
   `addEventAudit(INFO)`; `configureGeneration`→`setClassName("SubscribeAndLogStrategy")`,
   `setPackageName("com.nonco.marketmaker.strategy")`.
8. Generated (via `mvn -P"generate strategies"`, do not hand-edit):
   `strategy/SubscribeAndLogStrategy.java` + `resources/.../strategy/SubscribeAndLogStrategy.graphml`.

Repo `maker-fxoc` (`run_localhost`, localhost YAML):
9. Service block `simulatedWsVenue` → `!!...mockvenue.wsmktdata.MockWsMarketDataPublisher` (port, books).
10. Service block `simulatedWsMarketData` → `serviceClass MarketDataFeed`,
    `!!...websocket.GenericJsonWsMarketDataFeed` (url `ws://localhost:<port>`, feedName).
11. `eventHandlers: - agentName: wsMarketDataLogger` → `eventHandler: !!...strategy.SubscribeAndLogStrategy {}`,
    plus its agent-group declaration (mirrors `AquisMarketMakerStrategy` at YAML ~line 288 / group ~699).

## Admin command surface (`@ServiceRegistered adminClient(AdminCommandRegistry)`)
Handler shape: `void cmd(List<String> args, Consumer<String> out, Consumer<String> err)`; namespace by name.
- **Feed** (`<feedName>.` e.g. `simulatedWsMarketData.`): `connect`, `disconnect`, `reconnect`,
  `subscribe <symbol>`, `subscriptions`, `status` (connection state, url, subs, msgs received, last-msg
  time, reconnect count).
- **Mock venue** (`<venue>.`): `start`, `stop`, `pause`, `resume`, `setPublishRate <ms>`, `getStatus`,
  `listConfigs`, `addConfig ...`, `clients`. (mirror `MockMarketDataFeedWorker`.)

## Audit / diagnostics
Feeds are services, not graph nodes, so they can't write the structured `eventAudit` stream directly:
- operational diagnostics → `@Log4j2` `log.info/warn/error` + admin `status`.
- structured audit → flows through the strategy's `MarketDataListener` node
  (`SubscribeAndLogNode` audit-logs `marketConnected`/`marketDisconnected`/`onMarketData`), which is
  what the analyser reads. That's how a WS connection/subscription/tick becomes an audit record.

## Test plan (serverplugin-trading `src/test`, + market-maker-lib)
- `WsFramesTest` — RFC 6455 accept known vector (`dGhlIHNhbXBsZSBub25jZQ==`→`s3pPLMBiTxaQ9kYGzzhZRbK+xOo=`);
  text frame encode/decode round-trip (incl. masked client frame, 126/64k length paths).
- `WsMarketDataMessageTest` — Jackson round-trip; `book`/`multilevel` map to correct domain types.
- `MockWsPublisherLoopbackTest` — start publisher on ephemeral port; a raw JDK `WebSocket` client
  connects and receives the expected JSON book(s); admin `pause`/`setPublishRate` observable.
- `WsMarketDataFeedIntegrationTest` — publisher + `GenericJsonWsMarketDataFeed` end-to-end; assert
  emitted `MarketFeedEvent`s captured off the publish path.
- `SubscribeAndLogStrategyTest` (market-maker-lib) — fire a `MarketDataBook` at the REAL generated
  processor (per the project's Fluxtion testing rule: never mock the context) and assert it audit-logs.

## Phase Tracker  (resume at the first unchecked box)
- [x] **P0 Spec** — this document.
- [x] **P1 Transport** — `WsFrames` + `WsFramesTest` (7 green) — the RFC 6455 core, standalone.
- [x] **P2 Wire DTO** — `WsMarketDataMessage` (+`Level`) + `WsMarketDataMessageTest` (4 green).
- [x] **P3 Mock venue** — `MockWsMarketDataPublisher` + `MockWsPublisherLoopbackTest` (1 green, real JDK WS client).
- [x] **P4 Feed** — `AbstractWsMarketDataFeed` + `GenericJsonWsMarketDataFeed` + integration test (1 green, venue→feed end-to-end).
- [x] **P5 Test strategy** — `SubscribeAndLogNode` + `SubscribeAndLogStrategyBuilder`; generated
      `SubscribeAndLogStrategy.java`/`.graphml` (via `generate strategies`); `SubscribeAndLogStrategyTest`
      (4 green, fires at the real processor). NOTE: the scan also refreshed two STALE committed
      generated files (`IndicativeQuoteProducer.java`, `AquisMarketMakerStrategy.graphml`) — reverted
      as out-of-scope; the repo's committed generated artifacts have drifted from current node source.
- [x] **P6 Deploy wiring** — three YAML blocks added to localhost config: `simulatedWsVenue`
      (mock, agentGroup mockMarketDataFeed), `simulatedWsMarketData` (feed, no agentGroup like Talos),
      `agentName: wsMarketDataLogger` eventHandler + agentThread. Feed refactored self-contained
      (connect in start(), own reconnect executor, no SchedulerService); mock is now an `Agent`.
      Build: install fluxtion-server-plugins 0.2.17-SNAPSHOT + market-maker-lib as
      `-Drevision=1.9.2-reciprocal-pricing-candidate`, then `mvn -o package` fxoc. (NOTE: the m2
      candidate for market-maker-lib is overwritten with the local build for the test run.)
- [x] **P7 Run + verify** — deployed + ran on localhost. Confirmed live: mock venue listening on
      8451; feed connected (after one reconnect — see P8 race); **96 `onMarketData` + 1
      `marketConnected` WS books audit-logged** (`auditLog-maker-aquis.yaml`, feedName
      simulatedWsMarketData, px ~20.06); admin `.status` (connected, messagesReceived 98, reconnects 1)
      and venue `.getStatus` (clients 1) both work over REST. Analyser load pending (app was not
      running with REST at verify time; log is ready at run_localhost/var/log/auditLog-maker-aquis.yaml).
- [ ] **P8 Refine** — (a) startup race: feed `start()` fires ~66ms before the venue socket opens →
      one `ConnectException`, self-healed by the 3s reconnect; order the venue before the feed or add a
      short initial connect delay to avoid the first-failure noise. (b) dedicated audit appender
      `EventAudit-WS-MKTDATA` so WS records get their own file for clean analyser pairing.
      (c) multilevel book polish. (d) fold `serverplugin-trading` back to a released version once the WS
      feed ships (the fxoc pom currently pins 0.2.17-SNAPSHOT directly). (e) review + user feedback.

## Build / verify commands
- serverplugin-trading: `mvn -o -q -pl serverplugin-trading -am test` (or module dir `mvn -o -q test`).
- generate strategy: `cd market-maker-lib && mvn -P"generate strategies" -o -q install`.
- local deploy/run: `deploy-local` runbook (`build-local-fatjar.sh` → `deploy_jar_localhost.sh` →
  detached `run_maker.sh`), then analyser on `run_localhost/var/log/auditLog-*.yaml`.
