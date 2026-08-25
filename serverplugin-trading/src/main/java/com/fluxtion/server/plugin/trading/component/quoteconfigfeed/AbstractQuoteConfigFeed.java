package com.fluxtion.server.plugin.trading.component.quoteconfigfeed;

import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.input.SubscriptionManager;
import com.fluxtion.server.dispatch.EventFlowManager;
import com.fluxtion.server.dispatch.EventToQueuePublisher;
import com.fluxtion.server.dispatch.ProcessorContext;
import com.fluxtion.server.plugin.trading.service.quotestream.BucketConfigEvent;
import com.fluxtion.server.plugin.trading.service.quotestream.IndicativePricingConfig;
import com.fluxtion.server.plugin.trading.service.quotestream.PricingConfigEvent;
import com.fluxtion.server.plugin.trading.service.quotestream.QuoteConfigEvent;
import com.fluxtion.server.plugin.trading.service.quotestream.QuoteConfigFeed;
import com.fluxtion.server.plugin.trading.service.quotestream.QuoteConfigListener;
import com.fluxtion.server.service.EventSourceKey;
import com.fluxtion.server.service.EventSubscriptionKey;
import com.fluxtion.server.service.LifeCycleEventSource;
import com.fluxtion.server.service.scheduler.SchedulerService;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

/**
 * Framework base for an indicative-quote config feed — twin of {@code AbstractQuoteRequestFeed}. Owns
 * the event-flow wiring so a concrete connector only supplies the config source:
 * {@link #setEventFlowManager} registers the {@link QuoteConfigListenerInvocationStrategy} and this
 * service as an event source; a self-rearming poll invokes {@link #requestConfig()}, and the subclass
 * publishes {@link #publishPricingConfig}/{@link #publishBucketConfig} (thread-safe hand-off via the
 * target queue).
 */
@Log4j2
public abstract class AbstractQuoteConfigFeed
        implements
        QuoteConfigFeed,
        LifeCycleEventSource<QuoteConfigEvent> {

    @Getter(AccessLevel.PROTECTED) private EventFlowManager eventFlowManager;
    @Getter(AccessLevel.PROTECTED) private String serviceName;
    @Getter(AccessLevel.PROTECTED) private EventToQueuePublisher<QuoteConfigEvent> targetQueue;
    private SchedulerService scheduler;
    @Getter @Setter private long pollIntervalMillis = 5000;

    @Override
    public void setEventFlowManager(EventFlowManager eventFlowManager, String serviceName) {
        log.info("setEventFlowManager serviceName:{}", serviceName);
        this.eventFlowManager = eventFlowManager;
        this.serviceName = serviceName;
        eventFlowManager.registerEventMapperFactory(
                QuoteConfigListenerInvocationStrategy::new, QuoteConfigListener.class);
        this.targetQueue = eventFlowManager.registerEventSource(serviceName, this);
    }

    @ServiceRegistered
    public void scheduler(SchedulerService scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public void init() {
        // no-op
    }

    @Override
    public void start() {
        scheduleNextPoll();
    }

    @Override
    public void tearDown() {
        // no-op; polling stops with the scheduler
    }

    @Override
    public void subscribe() {
        EventSubscriptionKey<QuoteConfigEvent> subscriptionKey = new EventSubscriptionKey<>(
                new EventSourceKey<>(serviceName),
                QuoteConfigListener.class);
        SubscriptionManager subscriptionManager = ProcessorContext.currentProcessor().getSubscriptionManager();
        subscriptionManager.subscribe(subscriptionKey);
        log.info("subscribe serviceName:{}", serviceName);
    }

    private void scheduleNextPoll() {
        if (scheduler != null) {
            scheduler.scheduleAfterDelay(pollIntervalMillis, this::pollTriggered);
        }
    }

    private void pollTriggered() {
        scheduleNextPoll();      // self-rearm
        requestConfig();
    }

    /**
     * Publish the current config. Called on the poll thread; the subclass reads its config source
     * (cache/admin/file) and calls {@link #publishPricingConfig}/{@link #publishBucketConfig} for each
     * entry. Republishing unchanged config is harmless — a config node overwrites idempotently.
     */
    protected abstract void requestConfig();

    @Override
    public void publishPricingConfig(String symbol, IndicativePricingConfig config) {
        targetQueue.publish(new PricingConfigEvent(symbol, config));
    }

    @Override
    public void publishBucketConfig(long account, String symbol, double spreadBps) {
        targetQueue.publish(new BucketConfigEvent(account, symbol, spreadBps));
    }
}
