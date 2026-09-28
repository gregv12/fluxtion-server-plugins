# Extending the WebSocket feed to a new schema — agent guide

**Audience:** a fresh LLM session (and the developer driving it) who did *not* build the original
feed and has *not* run the localhost deployment. Goal: get up to speed fast, then add a **new venue
schema** by (1) extending the mock WS server to emit it and (2) writing a new WS subscriber client
that parses it. This guide is the entry point — read it first, then the files it lists.

---

## 0 · Read these first (in order)

Load these into context before writing code. Paths are repo-relative unless noted.

1. **This file.** The task shape and the seams you will touch.
2. `serverplugin-trading/docs/websocket-market-data-feed.md` — the original spec + phase tracker
   (design decisions: JDK `java.net.http.WebSocket` client, hand-rolled RFC 6455 mock, thread model,
   per-symbol unsubscribe + empty-book signal).
3. The **contract** in `serverlibrary-trading-api`:
   - `.../service/marketdata/MarketDataFeed.java` — the feed service interface (`subscribe`,
     `unsubscribe` default no-op, `feedName`, `venues`, `aggregatedFeeds`).
   - `.../service/marketdata/MarketDataListener.java` — the callbacks a strategy implements.
   - `.../service/marketdata/MarketFeedEvent.java` (sealed) + `MarketDataBook`,
     `MultilevelMarketDataBook`, `MarketConnected`, `MarketDisconnected` — the domain events.
4. The **reference implementation** you will clone the shape of:
   - `serverplugin-trading/.../component/websocket/AbstractWsMarketDataFeed.java` — the base client.
   - `serverplugin-trading/.../component/websocket/GenericJsonWsMarketDataFeed.java` — the concrete
     "extend per exchange" example (the seam: `subscribeFrame` / `unsubscribeFrame` / `onMessage`).
   - `serverplugin-trading/.../component/websocket/WsMarketDataMessage.java` — the current JSON wire DTO.
   - `serverplugin-trading/.../component/mockvenue/wsmktdata/MockWsMarketDataPublisher.java` — the mock
     venue (server); `.../wsmktdata/WsFrames.java` — the RFC 6455 helpers.
5. The **witness + wiring** (how a feed is proven end-to-end):
   - `market-maker-lib/.../node/SubscribeAndLogNode.java` + `.../builder/SubscribeAndLogStrategyBuilder.java`.
   - `maker-fxoc/run_localhost/app-config/market_maker_app.yaml` — the three WS blocks
     (`simulatedWsVenue`, `simulatedWsMarketData` feed, `wsMarketDataLogger` strategy) + `log4j2.yaml`'s
     `EventAudit-WS-MKTDATA` appender.
6. The **existing tests** (mirror these for the new schema):
   `WsMarketDataMessageTest`, `MockWsPublisherLoopbackTest`, `WsMarketDataFeedIntegrationTest`,
   `WsFramesTest` (all under `serverplugin-trading/src/test/...`).

**Install the analyser first** (JDK 21+, jbang-based). Easiest: `./preflight.sh --install-analyser` in
`maker-fxoc` (installs jbang + the analyser **and** registers the MCP with Claude). By hand:
`jbang app install analyser@telaminai/fluxtionauditlog-analyser`. Launch `analyser` and enable
**Settings ▸ Assistant ▸ REST** (the MCP is silent otherwise). Install docs:
<https://telaminai.github.io/fluxtionauditlog-analyser/install/>.

> **JBang caches the jar from first use.** To pick up a newer analyser release, force a refresh —
> `jbang --fresh analyser@telaminai/fluxtionauditlog-analyser` — otherwise JBang keeps running the
> version it cached the first time. New analyser verbs (e.g. `spotlight`) won't appear until you do this.

**Skills to use** (Claude Code, in `maker-fxoc`): `/onboard` (env doctor + analyser ping),
`deploy-local` (build fat jar → deploy to `run_localhost` → run), `/analyser-investigate` and
`analyser-profile` (plot/verify). Run `./preflight.sh` in `maker-fxoc` first.

---

## 1 · Architecture in one screen

```
new-schema venue (mock)  --WebSocket-->  new-schema feed (client)  --publish-->  Fluxtion processor
MockXxxWsPublisher                       XxxWsMarketDataFeed                      SubscribeAndLog* node
  RFC6455 (WsFrames)                       extends AbstractWsMarketDataFeed         @ExportService
  emits venue frames                       subscribeFrame/unsubscribeFrame          MarketDataListener
  gates on subscribedSymbols               onMessage -> MarketFeedEvent             audit-logs each event
```

Two layers never change: the **transport** (`WsFrames`, RFC 6455) and the **domain events**
(`MarketFeedEvent` and friends). What a new schema changes is only the **wire format in between** —
the bytes on the socket. So the whole task reduces to two seams:

- **Client seam** — a concrete subclass of `AbstractWsMarketDataFeed` that overrides `onMessage(String raw)`
  (parse the venue frame → `publish(MarketFeedEvent)`) and, for a venue with a subscribe handshake,
  `subscribeFrame(feed,venue,symbol)` / `unsubscribeFrame(feed,venue,symbol)` (both return `null` by
  default = push-only venue, subscriptions become a client-side filter via `isSubscribed(symbol)`).
  Optional hooks with defaults: `configureWebSocket(builder)` / `headers` (API-key headers),
  `isSubscribed(symbol)` (prefix/wildcard rules), `onConnected()` / `onDisconnected()`, `statusExtra()`,
  and the `staleConnectionMillis` watchdog (any frame incl. venue pings counts as liveness).
- **Server seam** — a mock venue that speaks the new schema, so you can drive the client locally
  without a real venue.

Thread model (do not re-derive it): the feed is **not** an Agrona agent — it publishes straight from
the JDK WebSocket callback on a single-thread executor it owns; `publish(...)` hands off to the
thread-safe `targetQueue`. The mock venue owns its own daemon threads (blocking `accept()` +
per-client reader) and publishes off a `SchedulerService` timer. Keep to these patterns.

---

## 2 · The task, step by step

### Step 1 — Define the new schema
Write down the venue's actual frames: subscribe/unsubscribe request shape, and the book/quote message
shape (single-level top-of-book vs multilevel depth). Decide the mapping to the domain:
`MarketDataBook` (top of book) or `MultilevelMarketDataBook` (depth). Note: `MultilevelMarketDataBook`
is **not** Jackson-round-trippable (final fields, private arrays) — if the schema is multilevel, use a
flat DTO as the serialization boundary and map both ways (see how `WsMarketDataMessage.toEvent()` and
the `from*` factories do it).

### Step 2 — Extend the mock venue to emit the new schema
Two options; pick per how different the schema is:
- **Small delta** (same JSON, different field names/types): parameterise `MockWsMarketDataPublisher`'s
  serialization — extract a `toWireText(MarketFeedEvent)` seam and inject a schema encoder.
- **Distinct schema** (recommended for a genuinely new venue): copy `MockWsMarketDataPublisher` to
  `MockXxxWsPublisher` in `component/mockvenue/<xxx>/`, reuse `WsFrames` and `MarketDataBookGenerator`,
  and change only `toMessage(...)`/`onClientText(...)` to the new schema. Keep the
  `requireSubscription` gate and the public `subscribedSymbols()` / `lastHandshakeHeaders()` /
  `pingClients()` / `closeClients()` test hooks.

### Step 3 — Write the subscriber client
Create `XxxWsMarketDataFeed extends AbstractWsMarketDataFeed` (mirror `GenericJsonWsMarketDataFeed`):
- `subscribeFrame` / `unsubscribeFrame` — build the venue's request frames.
- `onMessage(raw)` — parse a venue frame; on a book, `publish(book.toEvent())`; ignore control frames.
- Nothing else — connect/reconnect/admin/unsubscribe-empty-book/stale watchdog all live in the base
  class. Override the optional hooks above only when the venue needs them.

### Step 4 — Tests (mirror the existing four)
- `XxxWireMessageTest` — round-trip the new schema; assert book/multilevel map to the right domain type;
  subscribe/unsubscribe are control frames that map to no domain event.
- `MockXxxPublisherLoopbackTest` — start the venue on an ephemeral port, connect a raw JDK `WebSocket`,
  assert a frame arrives; with `requireSubscription`, assert streaming starts on subscribe and stops on
  unsubscribe (poll `venue.subscribedSymbols()` for determinism).
- `XxxWsMarketDataFeedIntegrationTest` — venue + feed end-to-end; capture `publish(...)` via a test
  subclass (no `EventFlowManager` wired) and assert the emitted `MarketFeedEvent`s; assert
  `unsubscribe(...)` publishes the empty `MarketDataBook` and drops the subscription. See
  `WsMarketDataFeedVenueHooksTest` for the hook/watchdog patterns (headers via
  `venue.lastHandshakeHeaders()`, status via a stub `AdminCommandRegistry`, liveness via
  `venue.pingClients()`, venue drop via `venue.closeClients()`).
- Run: `mvn -o -pl serverplugin-trading test` (from the plugins reactor root).

### Step 5 — Wire into the app + prove it
- Add a venue + feed + witness block to `maker-fxoc/run_localhost/app-config/market_maker_app.yaml`
  (copy the `simulatedWsVenue` / `simulatedWsMarketData` / `wsMarketDataLogger` trio; new names, new
  service classes). Give the witness its own audit appender in `log4j2.yaml` if you want a clean file.
- If you add a new witness/strategy node, regenerate: `cd market-maker-lib && mvn -P"generate strategies" -o -q install`
  (commit the generated `.java`/`.graphml`).
- Deploy + run: the `deploy-local` skill (`build-local-fatjar.sh` → deploy to `run_localhost` →
  `run_maker.sh`). **Stop the server before copying the jar** (cp over a live JVM fails silently);
  verify the deployed bytes; then start.
- Drive it over admin REST (`POST localhost:8001/admin` `{"command":"<feed>.subscribe","arguments":["SYM"]}`),
  then plot ticks in the analyser (`/analyser-investigate`) and toggle subscribe/unsubscribe.

### Step 6 — Build/version note
You are **editing** `serverplugin-trading`, so build it locally. `0.2.17` is released to Maven Central;
bump the reactor to the next `-SNAPSHOT` for your work, `mvn -o install` the plugin, and point
`maker-fxoc`/`market-maker-lib` at that snapshot (they carry a **direct** `serverlibrary-trading-api`
pin — see gotchas). Fold the pins back to a released version when your schema ships.

---

## 2b · Prompts to copy — add a new schema (drive an LLM)

Paste these into Claude Code one at a time, with this repo open. Replace `<VENUE>` with a name (e.g.
`Coinbase`) and paste the venue's actual frames where shown. The new schema is fundamentally **a new
feed class** (a concrete `AbstractWsMarketDataFeed` subclass) plus a mock venue that speaks it.

```text
1. Read serverplugin-trading/docs/extending-the-ws-feed.md and the files under its "Read these first"
   list. I want to add a new WebSocket venue schema called <VENUE>. Here are its frames:
     subscribe request: <paste JSON/text>
     book / quote message: <paste JSON/text>
     unsubscribe request: <paste JSON/text>
   Map each to the domain (MarketDataBook top-of-book, or MultilevelMarketDataBook for depth) and tell
   me the plan for the two seams (feed subclass + mock venue) before writing code.

2. Implement the subscriber client as a NEW CLASS: <VENUE>WsMarketDataFeed extends
   AbstractWsMarketDataFeed, in component/websocket/. Override subscribeFrame, unsubscribeFrame and
   onMessage for the <VENUE> schema; parse a book message and publish the domain event. Mirror
   GenericJsonWsMarketDataFeed. If the schema isn't Jackson-round-trippable, add a flat wire DTO like
   WsMarketDataMessage and map both ways.

3. Add a mock venue so I can run it locally: Mock<VENUE>WsPublisher in component/mockvenue/<venue>/,
   modelled on MockWsMarketDataPublisher — reuse WsFrames and MarketDataBookGenerator, emit the <VENUE>
   book schema in toMessage(...), and handle subscribe/unsubscribe in onClientText(...). Keep the
   requireSubscription gate and the public subscribedSymbols() / lastHandshakeHeaders() / pingClients()
   / closeClients() test hooks.

4. Write the four tests, mirroring the existing ones: <VENUE> wire round-trip (book/multilevel map to
   the right domain type; subscribe/unsubscribe are control frames -> no event); mock loopback with
   requireSubscription (streams after subscribe, stops after unsubscribe); feed integration (venue+feed
   end to end, capturing publish; unsubscribe publishes the empty book and drops the subscription).
   Run: mvn -o -pl serverplugin-trading test.

5. Wire a <venue> venue + feed + a SubscribeAndLog witness into maker-fxoc run_localhost
   (app-config/market_maker_app.yaml + a WS-MKTDATA-style appender), regenerate the strategy
   (remember the fluxtion scan double-build — run a plain install after the generate build), then use
   the deploy-local skill to build, deploy and run it. Verify in the analyser with the guided tour in
   docs/ws-feed-example-quickstart.md.
```

If a step's admin command needs to touch the audit log, re-dispatch it as an event
(`processAsNewEventCycle`) — never call `auditLog` from a raw admin lambda (see §3).

## 3 · Gotchas that cost time last round (read before you build)

- **Multi-repo version drift.** `trade-calculator-api-lib` drags in an *older* `serverlibrary-trading-api`
  transitively at compile scope; it wins on the classpath and the runtime interface then lacks your new
  method → a swallowed `NoSuchMethodError` that surfaces only as an **empty admin response**. Fix: a
  **direct** compile-scope dep on `serverlibrary-trading-api` (depth-1 nearest-wins) in every consumer
  (`market-maker-lib`, `maker-fxoc`). Verify the *fat jar* (not the module jar):
  `unzip -p app.jar '.../MarketDataFeed.class' | grep -aoc <method>`.
- **Admin returns an empty body when a handler throws.** An empty REST response == the command is
  unregistered **or** the handler threw (an `Error` too). Confirm registration with the `commands`
  admin listing, and use a distinct dotted literal (`grep -aoc '\.unsubscribe'`) not a bare substring
  when verifying a deploy — a stale string gives false positives.
- **Javalin 6 unbundles JSON.** `serverplugin-rest` needs `jackson-databind` on the classpath or the
  admin endpoint 500s. Already fixed there; don't remove it.
- **Deploy ≠ restart, and `cp -p` over a running JVM fails silently.** Stop → deploy → verify bytes →
  start.
- **Analyser Follow does not survive a log file being replaced** (a restart writes a new inode) —
  explicitly re-open the log path. Query verbs read; render verbs are reversible.
- **Analyser expression language:** duration literals are **quoted** (`sum(x/x,"5s")`, not `5s`); series
  keys must be flat alphanumeric tokens (no `.` or `-`) — the witness logs `bid<KEY>` per symbol for
  exactly this reason.
- **Mock rate control:** `MarketDataBookConfig.publishProbability` (0..1) gates each tick — give two
  symbols different values so their series read distinctly on a plot.
- **Never** `pkill -f maker-fxoc.jar` unbracketed (it self-matches); use `pkill -f "[m]aker-fxoc\.jar"`.
  Deploy/skills target DEV/UAT only — **never prod**.

## 4 · Definition of done
New mock venue + new feed compile with tests green (`mvn -o -pl serverplugin-trading test`); the feed
wired into `run_localhost` connects, streams, and audit-logs; subscribe/unsubscribe toggles the per-symbol
tick rate to and from zero on the analyser plot (empty book is the last record before it goes silent).
