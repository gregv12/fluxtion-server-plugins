package com.fluxtion.server.plugin.trading.component.tradevenue;

import com.fluxtion.agrona.concurrent.SnowflakeIdGenerator;
import com.fluxtion.server.plugin.trading.service.order.*;
import com.fluxtion.server.plugin.trading.service.order.impl.MutableOrder;
import lombok.Getter;
import org.agrona.collections.Long2ObjectHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

public class VenueOrderStateManager {

    private static final Logger log = LoggerFactory.getLogger(VenueOrderStateManager.class);
    @Getter
    private final Long2ObjectHashMap<MutableOrder> clOrderIdToOrderMap = new Long2ObjectHashMap<>();
    @Getter
    private final Long2ObjectHashMap<MutableOrder> requestIdToOrderMap = new Long2ObjectHashMap<>();
    private final Consumer<OrderEvent> orderEventPublisher;
    private static final SnowflakeIdGenerator idGeneratorGlobal = new SnowflakeIdGenerator(0);
    private LongSupplier clOrderIdCounter;


    public VenueOrderStateManager(Consumer<OrderEvent> orderEventPublisher) {
        this.orderEventPublisher = orderEventPublisher;
        clOrderIdCounter = idGeneratorGlobal::nextId;
    }

    public VenueOrderStateManager(Consumer<OrderEvent> orderEventPublisher, LongSupplier clOrderIdCounter) {
        this.orderEventPublisher = orderEventPublisher;
        this.clOrderIdCounter = clOrderIdCounter;
    }

    public VenueOrderStateManager(Consumer<OrderEvent> orderEventPublisher, int seedId) {
        if(seedId < 0) throw new IllegalArgumentException("seedId must be >= 0");
        if(seedId > 1023) throw new IllegalArgumentException("seedId must be <= 1023");
        this.orderEventPublisher = orderEventPublisher;
        this.clOrderIdCounter = new SnowflakeIdGenerator(seedId)::nextId;
    }

    public void setClOrderIdSeed(LongSupplier clOrderIdCounter) {
        Objects.requireNonNull(clOrderIdCounter, "clOrderIdCounter must not be null");
        this.clOrderIdCounter = clOrderIdCounter;
    }

    public void setClOrderIdSeed(long seedId) {
        if(seedId < 0) throw new IllegalArgumentException("seedId must be >= 0");
        if(seedId > 1023) throw new IllegalArgumentException("seedId must be <= 1023");
        this.clOrderIdCounter = new SnowflakeIdGenerator(seedId)::nextId;
    }

    //order responses
    public void orderAccepted(MutableOrder order) {
        publish(new OrderUpdateEvent(order.orderStatus(OrderStatus.NEW).copy()));
    }

    public void orderReplaced(MutableOrder mutableOrder, double price, double quantity) {
        log.debug("orderReplaces:{}", mutableOrder);
        mutableOrder.price(price);
        mutableOrder.quantity(quantity);

        //TODO remove old request id from map
        requestIdToOrderMap.put(mutableOrder.clOrdId(), mutableOrder);

        //recalculate quantities
        publish(new OrderUpdateEvent(mutableOrder.copy()));
    }

    public void orderPartiallyFilled(MutableOrder mutableOrder, double price, double quantity, String execId) {
        publish(new OrderFillEvent(mutableOrder.copy(), price, quantity, execId));
    }

    public void orderFilled(MutableOrder mutableOrder, double price, double quantity, String execId) {
        //TODO remove old request id from map
        publish(new OrderFilledEvent(mutableOrder.orderStatus(OrderStatus.FILLED).copy(), price, quantity, execId));
    }

    public void orderCancelled(MutableOrder mutableOrder) {
        //TODO remove old request id from map
        calculateCancelled(mutableOrder);
        publish(new OrderCancelEvent(mutableOrder.orderStatus(OrderStatus.CANCELLED).copy()));
    }

    public void orderDoneForDay(MutableOrder mutableOrder) {
        //TODO remove old request id from map
        calculateCancelled(mutableOrder);
        publish(new OrderDoneForDayEvent(mutableOrder.orderStatus(OrderStatus.DONE_FOR_DAY).copy()));
    }

    public void orderRejected(MutableOrder mutableOrder) {
        //TODO remove old request id from map
        calculateCancelled(mutableOrder);
        publish(new OrderRejectEvent(mutableOrder.orderStatus(OrderStatus.REJECTED).copy()));
    }

    public void orderCancelRejected(MutableOrder order) {
//        requestIdToOrderMap.remove(order.currentClOrdId());
        publish(new OrderCancelRejectEvent(order.copy()));
    }

    //client requests
    public MutableOrder newOrderRequest(MutableOrder order, MessageVenueTask venuePublisherTask) {
        publish(new OrderUpdateEvent(order));
        try {
            venuePublisherTask.run();
            clOrderIdToOrderMap.put(order.clOrdId(), order);
            requestIdToOrderMap.put(order.clOrdId(), order);
        } catch (Exception e) {
            publish(new OrderUpdateEvent(order.orderStatus(OrderStatus.REJECTED).copy()));
        }

        return order;
    }

    public void cancelRequest(MutableOrder order, long requestId, MessageVenueTask venuePublisherTask) {
        try {
            venuePublisherTask.run();
            requestIdToOrderMap.put(requestId, order);
//            requestIdToOrderMap.remove(order.clOrdId());
        } catch (Exception e) {
            publish(new OrderUpdateEvent(order.orderStatus(OrderStatus.REJECTED).copy()));
        }
    }

    public void modifyOrderRequest(MutableOrder order, long requestId, MessageVenueTask venuePublisherTask) {
        try {
            venuePublisherTask.run();
            requestIdToOrderMap.put(requestId, order);
        } catch (Exception e) {
            orderCancelRejected(order);
        }
    }

    public long nextClOrderId() {
        return clOrderIdCounter.getAsLong();
    }

    private static final Set<OrderStatus> COMPLETED_STATUSES = Set.of(
            OrderStatus.FILLED,
            OrderStatus.CANCELLED,
            OrderStatus.DONE_FOR_DAY,
            OrderStatus.REJECTED
    );

    private static final Set<OrderStatus> LIVE_STATUSES = Set.of(
            OrderStatus.CREATED,
            OrderStatus.PENDING_NEW,
            OrderStatus.NEW,
            OrderStatus.PARTIAL_FILLED
    );

    public List<MutableOrder> getLiveOrders() {
        return clOrderIdToOrderMap.values().stream()
                .filter(order -> LIVE_STATUSES.contains(order.orderStatus()))
                .collect(Collectors.toList());
    }

    public List<MutableOrder> getCompletedOrders() {
        return clOrderIdToOrderMap.values().stream()
                .filter(order -> COMPLETED_STATUSES.contains(order.orderStatus()))
                .collect(Collectors.toList());
    }

    public int clearCompletedOrders() {
        List<MutableOrder> completedOrders = getCompletedOrders();
        int clearedCount = completedOrders.size();
        for (MutableOrder order : completedOrders) {
            clOrderIdToOrderMap.remove(order.clOrdId());
            requestIdToOrderMap.remove(order.clOrdId());
            requestIdToOrderMap.remove(order.currentClOrdId());
        }
        log.info("Cleared {} completed orders", clearedCount);
        return clearedCount;
    }

    private void publish(OrderEvent orderEvent) {
        orderEventPublisher.accept(orderEvent);
    }

    private void calculateCancelled(MutableOrder order) {
        order.cancelledQuantity(order.quantity() - order.filledQuantity())
                .leavesQuantity(0);
    }

    @FunctionalInterface
    public interface MessageVenueTask {
        /**
         * Runs this operation.
         */
        void run() throws Exception;
    }
}
