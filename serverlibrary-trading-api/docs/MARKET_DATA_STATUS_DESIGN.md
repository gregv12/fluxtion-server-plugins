# Market Data Venue Status Design Specification

**Project:** fluxtion-server-plugins
**Module:** serverlibrary-trading-api
**Date:** 2026-10-05
**Status:** Design Proposal

## Executive Summary

A market-data feed can refuse to publish a book. Today it has no way to say so. The only observable
consequence is that books stop arriving — and absence is not an observation: a venue outside its
trading session, a subscription filter that matches nothing, and a parser quietly dropping every frame
all produce byte-identical evidence.

This document proposes `MarketVenueStatus`, a `MarketFeedEvent` carrying the venue's own session state
plus the feed's frame tallies, delivered through a new `MarketDataStatusListener` that
`MarketDataListener` extends. It is published **on change**, not per frame.

**Key recommendation:** model venue tradability as an event, the way the order side already models
venue lifecycle and rejections. The market-data side is the asymmetry, not the order side.

---

## Table of Contents

1. [Motivation](#1-motivation)
2. [The asymmetry with the order side](#2-the-asymmetry-with-the-order-side)
3. [Proposed API](#3-proposed-api)
4. [Publication semantics](#4-publication-semantics)
5. [Relationship to MarketConnected / MarketDisconnected](#5-relationship-to-marketconnected--marketdisconnected)
6. [Why not the alternatives](#6-why-not-the-alternatives)
7. [Migration and compatibility](#7-migration-and-compatibility)
8. [Open questions](#8-open-questions)

---

## 1. Motivation

A feed that gates publication on venue health is doing the right thing: a price from a venue that is
not trading is not a price. But the gate is currently invisible.

**Observed, 2026-10-05, on a production-bound futures feed.** Between connecting and the instrument
opening, the feed received ~1,600 frames and declined to publish 1,068 books. Over the same window its
audit log contained **three records**, all lifecycle. The information needed to tell "the market is
shut, correctly handled" from "our subscription filter is broken" existed only as a counter inside the
feed object, reachable by an admin command nobody had reason to run.

Diagnosis required opening a raw socket to the venue by hand and reading the frames directly — which is
precisely the thing an audit log is supposed to make unnecessary.

The general shape of the problem:

- The **event processor** is audited. Every dispatch is framed, ordered and recorded.
- A **feed is a service**, outside the processor. Its state is not in the audit log unless it is
  published as an event.
- A feed that publishes nothing therefore contributes nothing — exactly when it is most interesting.

Three consequences, in increasing order of cost:

1. **Diagnosis is manual.** The evidence chain has a hole at the boundary where most integration
   failures live.
2. **No consumer can react to tradability.** A strategy cannot stop quoting on "venue entered
   pre-open", because it is never told. It can only infer staleness from silence, which is
   indistinguishable from a quiet market.
3. **Recovery is unobservable.** "The venue came back" is as invisible as the loss.

## 2. The asymmetry with the order side

The order side of this API already models exactly this, and models it well:

| Concern | Order side | Market-data side |
| --- | --- | --- |
| Venue lifecycle | `OrderVenueConnectedEvent`, `OrderVenueDisconnectedEvent` | `MarketConnected`, `MarketDisconnected` |
| Payload | `OrderEvent`, `OrderUpdateEvent`, `OrderFillEvent` | `MarketDataBook`, `MultilevelMarketDataBook` |
| **Refusal** | `OrderRejectEvent`, `OrderCancelRejectEvent` | **— nothing —** |
| **State** | `OrderStatus` | **— nothing —** |
| Listener split | `OrderListener` + `ExecutionListener` | `MarketDataListener` only |

An order that is refused produces an event naming the refusal. A book that is refused produces
silence. This proposal closes that gap using the same vocabulary.

## 3. Proposed API

### 3.1 `MarketVenueStatus`

```java
package com.fluxtion.server.plugin.trading.service.marketdata;

/**
 * The venue's own view of whether its market is usable, as reported by the venue.
 *
 * <p>Published by a feed when the answer CHANGES, not per frame. A feed that gates publication on
 * venue health must publish this, or the gate is unobservable: a closed market and a broken
 * subscription filter are otherwise indistinguishable downstream.</p>
 */
public class MarketVenueStatus extends MarketFeedEvent {

    /** Null when the venue reports a single state for the whole session; set when per-instrument. */
    private String symbol;

    /** The normalised answer, and the only field a consumer should branch on by default. */
    private boolean tradable;

    /** The venue's own code, UNNORMALISED and uninterpreted. Diagnostics and venue-specific rules. */
    private int venueCode;

    /** The venue's own name for that code, its vocabulary not ours, e.g. "Pre-open". */
    private String venueState;

    /** The venue's upstream link state where it reports one separately from session state. */
    private String upstreamLink;

    /** The code this replaced, so a listener sees the transition and not just the level. */
    private int previousVenueCode;

    /** Why the feed concluded this, for the audit record. Free text, not a control value. */
    private String reason;

    /** What the feed has seen since the last change. Keys are feed-defined; see 3.3. */
    private Map<String, Long> counters;
}
```

**`tradable` is the contract; `venueCode` is the evidence.** Consumers branch on `tradable`. Keeping
the raw code alongside it means a venue-specific consumer can still make a finer distinction (treating
a closing auction differently from a halt), and means the audit record shows what the venue actually
said rather than only our interpretation of it. A normalised-only event throws away the ability to
audit the normalisation.

### 3.2 `MarketDataStatusListener`

```java
public interface MarketDataStatusListener {

    /**
     * The venue's tradability changed. Published on change, so a listener that only wants to know
     * "may I price off this feed" need implement nothing else.
     */
    default boolean marketDataVenueStatus(MarketVenueStatus status) {
        return false;
    }
}

public interface MarketDataListener extends MarketDataStatusListener {
    // unchanged
}
```

**Why a separate interface that `MarketDataListener` extends**, rather than one more method on
`MarketDataListener`:

- Every existing listener inherits the callback at zero cost, exactly as `onMultilevelMarketData` did
  when it was added as a `default`.
- A consumer that cares *only* about tradability — a kill switch, a risk gate, a monitor, a venue
  dashboard — can `@ExportService MarketDataStatusListener` and implement one method, instead of
  implementing book callbacks it has no use for and must remember to return `false` from.
- It mirrors the order side's `OrderListener` / `ExecutionListener` split: different concern, different
  cadence, separate interface.

### 3.3 Counters

`counters` is an open `Map<String, Long>` rather than a fixed type. Feeds differ in what they can
count, and a fixed schema would either be the intersection (useless) or the union (mostly nulls).

Recommended keys where a feed can supply them, so dashboards and audit queries can rely on names:

| Key | Meaning |
| --- | --- |
| `framesReceived` | frames off the transport, before parsing |
| `unparseable` | frames that failed to parse |
| `booksPublished` | books passed downstream |
| `booksRejectedNotSubscribed` | symbol matched no subscription |
| `booksRejectedNotTradable` | refused by the venue-health gate |
| `millisToFirstBook` | connect to first published book; `-1` until there is one |
| `millisInPreviousState` | time spent in the state being left |

Keys a feed cannot supply are omitted, not zeroed — absent and zero mean different things, and
conflating them reintroduces the problem this spec exists to solve.

## 4. Publication semantics

**On change.** In the observed case that is three events in a session (`closed → pre-open → trading`)
rather than 1,068. Per-frame rejection events would be the `OrderRejectEvent` analogue, but a book
stream is not a request stream: rejections are correlated and uninteresting individually. The counters
carry the volume; the events carry the transitions.

**Also on first observation**, so a listener attaching to an already-open venue learns the state
without waiting for it to change.

**Also at gate close specifically**, because that is when downstream must act. A feed that gates should
continue to publish an empty book per live symbol at close — this event explains *why*, it does not
replace the mechanism.

**Ordering.** Published before the empty books that a gate close triggers, so a listener sees the cause
before the effect in a single dispatch order.

**Not retroactive.** A status event describes the venue from now; it says nothing about books already
published.

## 5. Relationship to `MarketConnected` / `MarketDisconnected`

These overlap and the boundary must be stated or implementors will guess differently.

- `MarketConnected` / `MarketDisconnected` are about **our transport**: the socket or session between
  this process and the venue.
- `MarketVenueStatus` is about **the venue's market**: whether the venue says its instrument is
  tradable, which it can answer perfectly well over a healthy socket.

They are independent. A connected feed can report a closed market; a disconnected feed has no current
view of the market at all.

**Recommendation: keep both.** A feed that loses its transport should publish `MarketDisconnected` as
today, and *may* publish a status with `tradable=false, reason="transport lost"` so that a status-only
listener is not left believing the last-known state. The javadoc must say which is authoritative for
which question.

## 6. Why not the alternatives

| Alternative | Why not |
| --- | --- |
| **Admin command that prints counters** | Pull, not push. Nobody runs it at the moment that matters, and it cannot drive a consumer. Useful as a supplement; it was the stopgap that prompted this spec. |
| **A diagnostic method on `MarketDataFeed`** | Still pull. Makes the feed interface a diagnostics surface, and a listener must poll to notice a change. |
| **Duck-typing the feed for counters by reflection** | What the stopgap did, to avoid a release of this module. Works, is not type-safe, is invisible to tooling, and silently yields nothing against a feed that does not happen to expose the right method shape. |
| **Log it and read the log** | Puts the information in text, outside the event stream, so no consumer can react and the record is not framed by an event cycle. |
| **Per-frame rejection events** | Correct shape, wrong granularity: ~1,068 events for one session open, each individually uninformative. |

## 7. Migration and compatibility

**Source and binary compatible for consumers.** `marketDataVenueStatus` is a `default` method;
existing `MarketDataListener` implementations compile and run unchanged, and receive the callback as a
no-op returning `false`.

**Feeds opt in.** A feed that publishes no status behaves exactly as today. There is no requirement to
retrofit every feed.

**Staged adoption:**

1. Add `MarketVenueStatus` and `MarketDataStatusListener` to `serverlibrary-trading-api`.
2. Publish from one gating feed; assert the transitions in its integration test.
3. Consume in a witness node, so the transitions land in the audit log and are plottable.
4. Only then consider a consumer that *acts* on it (a quoting gate), which is a behavioural change and
   wants its own review.

**Cost.** This is a change to the shared API module, so it needs a plugins release and a version bump
in every dependent venue library and application. That cost is the reason a reflective stopgap existed;
it is not a reason to keep it.

## 8. Open questions

1. **Should `tradable=false` imply the empty-book convention**, or stay orthogonal? Currently a gating
   feed publishes an empty book per live symbol on close. If status becomes mandatory, the empty book
   could be derived — but that couples two mechanisms with different audiences.
2. **Per-instrument or per-venue?** `symbol` is nullable to allow both. Venues that report
   per-instrument state will emit one event per instrument per transition; at a session open across
   many instruments that could be a burst. A venue-level roll-up may be wanted.
3. **Should the counters ride on this event at all**, or be a separate periodic `MarketFeedMetrics`?
   Attaching them means a transition carries its own context, which is what made the observed case
   diagnosable; it also means counters are only visible when something changes, which is wrong for a
   venue that stays shut all day.
4. **Normalisation table.** `tradable` is computed by the feed from venue codes. Should the mapping be
   declarable in configuration rather than compiled in, so a venue adding a session state does not need
   a code change?
