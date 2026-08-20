package com.fluxtion.server.plugin.trading.component.quoterequestfeed;

import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.input.SubscriptionManager;
import com.fluxtion.server.dispatch.EventFlowManager;
import com.fluxtion.server.dispatch.EventToQueuePublisher;
import com.fluxtion.server.dispatch.ProcessorContext;
import com.fluxtion.server.plugin.trading.service.quotestream.LiveQuoteNoData;
import com.fluxtion.server.plugin.trading.service.quotestream.LiveQuoteSnapshot;
import com.fluxtion.server.plugin.trading.service.quotestream.QuoteRequestEvent;
import com.fluxtion.server.plugin.trading.service.quotestream.QuoteRequestFeed;
import com.fluxtion.server.plugin.trading.service.quotestream.QuoteRequestListener;
import com.fluxtion.server.service.EventSourceKey;
import com.fluxtion.server.service.EventSubscriptionKey;
import com.fluxtion.server.service.LifeCycleEventSource;
import com.fluxtion.server.service.scheduler.SchedulerService;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

import java.util.Map;
import java.util.Set;

/**
 * Framework base for a live-quote discovery feed — the twin of
 * {@code component.marketdatafeed.AbstractMarketDataFeed}. It owns all the event-flow wiring so a
 * concrete connector only supplies the transport:
 *
 * <ul>
 *   <li>{@link #setEventFlowManager} registers the {@link QuoteRequestListenerInvocationStrategy}
 *       (mapping {@link QuoteRequestEvent} → {@link QuoteRequestListener}) and registers this
 *       service as an event source, capturing the {@link EventToQueuePublisher target queue};</li>
 *   <li>{@link #subscribe()} registers the calling processor as a listener target;</li>
 *   <li>a self-rearming poll ({@link SchedulerService}) invokes {@link #requestLiveQuotes()} every
 *       {@code pollIntervalMillis}; the subclass fetches (typically async) and calls
 *       {@link #publishSnapshot}/{@link #publishNoData} on completion.</li>
 * </ul>
 *
 * <p>The fetch may complete on a foreign thread; {@link #publishSnapshot}/{@link #publishNoData}
 * hand off via the thread-safe {@code targetQueue}, which the owning processor drains on its agent
 * thread. Concretes must never call {@code onEvent} directly from a fetch-completion thread.
 */
@Log4j2
public abstract class AbstractQuoteRequestFeed
        implements
        QuoteRequestFeed,
        LifeCycleEventSource<QuoteRequestEvent> {

    @Getter(AccessLevel.PROTECTED) private EventFlowManager eventFlowManager;
    @Getter(AccessLevel.PROTECTED) private String serviceName;
    @Getter(AccessLevel.PROTECTED) private EventToQueuePublisher<QuoteRequestEvent> targetQueue;
    private SchedulerService scheduler;
    @Getter @Setter private long pollIntervalMillis = 2000;

    @Override
    public void setEventFlowManager(EventFlowManager eventFlowManager, String serviceName) {
        log.info("setEventFlowManager serviceName:{}", serviceName);
        this.eventFlowManager = eventFlowManager;
        this.serviceName = serviceName;
        eventFlowManager.registerEventMapperFactory(
                QuoteRequestListenerInvocationStrategy::new, QuoteRequestListener.class);
        this.targetQueue = eventFlowManager.registerEventSource(serviceName, this);
    }

    @ServiceRegistered
    public void scheduler(SchedulerService scheduler) {
        this.scheduler = scheduler;
        log.info("scheduler registered serviceName:{} scheduler:{}", serviceName, scheduler);
    }

    @Override
    public void init() {
        // no-op; wiring happens in setEventFlowManager and polling starts in start()
        log.info("init serviceName:{}", serviceName);
    }

    @Override
    public void start() {
        log.info("start serviceName:{} pollIntervalMillis:{} schedulerRegistered:{}",
                serviceName, pollIntervalMillis, scheduler != null);
        scheduleNextPoll();
    }

    @Override
    public void tearDown() {
        // no-op by default; the self-rearming poll stops when the scheduler shuts down.
        // Subclasses may override to release transport resources (e.g. close an HTTP client).
        log.info("tearDown serviceName:{}", serviceName);
    }

    @Override
    public void subscribe() {
        EventSubscriptionKey<QuoteRequestEvent> subscriptionKey = new EventSubscriptionKey<>(
                new EventSourceKey<>(serviceName),
                QuoteRequestListener.class);
        SubscriptionManager subscriptionManager = ProcessorContext.currentProcessor().getSubscriptionManager();
        subscriptionManager.subscribe(subscriptionKey);
        log.info("subscribe serviceName:{}", serviceName);
    }

    private void scheduleNextPoll() {
        if (scheduler != null) {
            log.info("scheduleNextPoll serviceName:{} delayMs:{}", serviceName, pollIntervalMillis);
            scheduler.scheduleAfterDelay(pollIntervalMillis, this::pollTriggered);
        } else {
            log.warn("scheduleNextPoll skipped serviceName:{} — scheduler not registered, polling will not start",
                    serviceName);
        }
    }

    private void pollTriggered() {
        log.info("pollTriggered serviceName:{}", serviceName);
        scheduleNextPoll();      // self-rearm regardless of outcome
        requestLiveQuotes();
    }

    /**
     * Kick off the (typically async) discovery fetch. On completion the subclass calls
     * {@link #publishSnapshot} (a real 200 response, possibly empty) or {@link #publishNoData}
     * (503 / error). Runs on the poll thread; the actual I/O should not block it.
     */
    protected abstract void requestLiveQuotes();

    /** Hand a discovery snapshot to the owning processor (thread-safe). */
    protected void publishSnapshot(Map<String, Set<Long>> symbolToAccounts) {
        targetQueue.publish(new LiveQuoteSnapshot(symbolToAccounts));
    }

    /** Signal "no data yet" / error — a HOLD (no cancels downstream). Thread-safe. */
    protected void publishNoData() {
        targetQueue.publish(new LiveQuoteNoData());
    }
}