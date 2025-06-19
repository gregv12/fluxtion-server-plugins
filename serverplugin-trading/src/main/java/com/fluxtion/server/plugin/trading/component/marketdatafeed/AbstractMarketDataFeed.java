package com.fluxtion.server.plugin.trading.component.marketdatafeed;

import com.fluxtion.runtime.input.SubscriptionManager;
import com.fluxtion.server.dispatch.*;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketDataBook;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketDataFeed;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketDataListener;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketFeedEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

import java.util.HashSet;
import java.util.Set;

@Log4j2
public abstract class AbstractMarketDataFeed
        implements
        MarketDataFeed,
        EventFlowService,
        LifeCycleEventSource<MarketFeedEvent> {

    @Getter(AccessLevel.PROTECTED)
    private EventFlowManager eventFlowManager;
    @Getter(AccessLevel.PROTECTED)
    private String serviceName;
    @Getter(AccessLevel.PROTECTED)
    private EventToQueuePublisher<MarketFeedEvent> targetQueue;
    protected final Set<String> subscriptions = new HashSet<>();
    @Getter
    @Setter
    protected String feedName;
    @Getter
    @Setter
    protected Set<String> venueNameSet = new HashSet<>();
    @Getter
    @Setter
    protected Set<String> feedNameSet = new HashSet<>();


    @Override
    public void setEventFlowManager(EventFlowManager eventFlowManager, String serviceName) {
        log.info("setEventFlowManager  feedName:{}, serviceName:{}", feedName, serviceName);
        this.eventFlowManager = eventFlowManager;
        this.serviceName = serviceName;
        eventFlowManager.registerEventMapperFactory(MarketListenerInvocationStrategy::new, MarketDataListener.class);
        this.targetQueue = eventFlowManager.registerEventSource(serviceName, this);
    }

    @Override
    public void init() {
        log.info("init feedName:{}", feedName);
    }

    @Override
    public void start() {
        log.info("start feedName:{}, serviceName:{}", feedName, serviceName);
    }

    @Override
    public void startComplete() {
        log.info("startComplete feedName:{}, serviceName:{}", feedName, serviceName);
    }

    @Override
    public void subscribe(String feedName, String venueName, String symbol) {
        log.info("subscribe symbol:{} venue:{}, feedName:{}, serviceName:{}", symbol, venueName, feedName, serviceName);

        EventSubscriptionKey<MarketDataBook> subscriptionKey = new EventSubscriptionKey<>(
                new EventSourceKey<>(serviceName),
                MarketDataListener.class,
                symbol
        );
        SubscriptionManager subscriptionManager = EventFlowManager.currentProcessor().getSubscriptionManager();
        subscriptionManager.subscribe(subscriptionKey);

        subscribeToSymbol(feedName, venueName, symbol);
    }

    protected abstract void subscribeToSymbol(String feedName, String venueName, String symbol);

    protected void publish(MarketFeedEvent marketFeedEvent) {
        targetQueue.publish(marketFeedEvent);
    }

    @Override
    public String feedName() {
        return feedName;
    }

    @Override
    public Set<String> aggregatedFeeds() {
        feedNameSet = feedNameSet == null ? new HashSet<>() : feedNameSet;
        feedNameSet.add(feedName);
        return feedNameSet;
    }

    @Override
    public Set<String> venues() {
        venueNameSet = venueNameSet == null ? new HashSet<>() : venueNameSet;
        venueNameSet.add(feedName);
        return venueNameSet;
    }

    @Override
    public void stop() {
        log.info("stop venue:{}, serviceName:{}", feedName, serviceName);
    }

    @Override
    public void tearDown() {
        log.info("tearDown venue:{}, serviceName:{}", feedName, serviceName);
    }
}

