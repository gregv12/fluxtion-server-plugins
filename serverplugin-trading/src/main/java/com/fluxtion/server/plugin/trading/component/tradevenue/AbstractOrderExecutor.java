package com.fluxtion.server.plugin.trading.component.tradevenue;

import com.fluxtion.runtime.input.SubscriptionManager;
import com.fluxtion.server.dispatch.*;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketDataBook;
import com.fluxtion.server.plugin.trading.service.order.OrderEvent;
import com.fluxtion.server.plugin.trading.service.order.OrderExecutor;
import com.fluxtion.server.plugin.trading.service.order.OrderListener;
import com.fluxtion.server.plugin.trading.service.order.impl.MutableOrder;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

import java.util.HashSet;
import java.util.Set;

@Log4j2
public abstract class AbstractOrderExecutor
        implements
        OrderExecutor,
        EventFlowService,
        LifeCycleEventSource<OrderEvent> {

    @Getter(AccessLevel.PROTECTED)
    private EventFlowManager eventFlowManager;
    @Getter(AccessLevel.PROTECTED)
    private String serviceName;
    @Getter(AccessLevel.PROTECTED)
    private EventToQueuePublisher<OrderEvent> targetQueue;
    @Getter
    @Setter
    protected String feedName;
    @Getter
    @Setter
    protected Set<String> venueNameSet = new HashSet<>();
    protected final VenueOrderStateManager orderStateManager = new VenueOrderStateManager(this::publish);

    @Override
    public void setEventFlowManager(EventFlowManager eventFlowManager, String serviceName) {
        log.info("setEventFlowManager serviceName:{}", serviceName);
        this.eventFlowManager = eventFlowManager;
        this.serviceName = serviceName;
        eventFlowManager.registerEventMapperFactory(OrderListenerInvocationStrategy::new, OrderListener.class);
        this.targetQueue = eventFlowManager.registerEventSource(serviceName, this);
    }

    @Override
    public void init() {
        log.info("init");
    }

    @Override
    public void start() {
        log.info("start");
    }

    @Override
    public void addOrderListener(OrderListener orderListener) {
        log.info("add orderListener");

        EventSubscriptionKey<MarketDataBook> subscriptionKey = new EventSubscriptionKey<>(
                new EventSourceKey<>(serviceName),
                OrderListener.class,
                "orders"
        );

        SubscriptionManager subscriptionManager = EventFlowManager.currentProcessor().getSubscriptionManager();
        subscriptionManager.subscribe(subscriptionKey);
    }

    @Override
    public Set<String> venues() {
        venueNameSet = venueNameSet == null ? new HashSet<>() : venueNameSet;
        venueNameSet.add(feedName);
        return venueNameSet;
    }

    protected void publish(OrderEvent orderEvent) {
        targetQueue.publish(orderEvent);
    }

    @Override
    public void stop() {
        log.info("stop");
    }

    @Override
    public void tearDown() {
        log.info("tearDown");
    }

    public MutableOrder getOrderByOriginalClOrderId(long clOrderId) {
        return orderStateManager.getClOrderIdToOrderMap().get(clOrderId);
    }

    public MutableOrder getOrderByCurrentClOrderId(long clOrderId) {
        return orderStateManager.getRequestIdToOrderMap().get(clOrderId);
    }
}

