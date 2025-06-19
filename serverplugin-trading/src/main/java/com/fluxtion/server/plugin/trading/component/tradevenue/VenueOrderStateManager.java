package com.fluxtion.server.plugin.trading.component.tradevenue;

import com.fluxtion.server.plugin.trading.service.order.*;
import com.fluxtion.server.plugin.trading.service.order.impl.MutableOrder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.agrona.collections.Long2ObjectHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@RequiredArgsConstructor
public class VenueOrderStateManager {

    private static final Logger log = LoggerFactory.getLogger(VenueOrderStateManager.class);
    @Getter
    private final Long2ObjectHashMap<MutableOrder> clOrderIdToOrderMap = new Long2ObjectHashMap<>();
    @Getter
    private final Long2ObjectHashMap<MutableOrder> requestIdToOrderMap = new Long2ObjectHashMap<>();
    private final Consumer<OrderEvent> orderEventPublisher;
    private static final AtomicLong clOrderIdCounter = new AtomicLong(1000L * (System.currentTimeMillis() / 1000L));


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
            requestIdToOrderMap.remove(order.clOrdId());
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

    public static long nextClOrderId() {
        return clOrderIdCounter.incrementAndGet();
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
