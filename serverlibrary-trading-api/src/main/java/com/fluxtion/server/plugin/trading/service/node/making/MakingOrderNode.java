package com.fluxtion.server.plugin.trading.service.node.making;

import com.fluxtion.runtime.annotations.Initialise;
import com.fluxtion.runtime.annotations.Start;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.server.plugin.trading.service.common.Direction;
import com.fluxtion.server.plugin.trading.service.order.*;
import com.fluxtion.server.plugin.trading.service.node.TradeServiceListener;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

@ToString
@Log4j2
public class MakingOrderNode implements OrderListener, TradeServiceListener {

    private final Direction direction;
    @Getter
    private final String name;
    private final String book;
    private final String symbol;
    private final MakingVenueConfig makingVenueConfig;
    private final String makerVenue;
    private final String feedName;
    //current state
    private OrderExecutor orderExecutor;
    private Order liverOrder;
    private boolean connected;
    private double targetQuantity;
    private double targetPrice;
    private boolean pendingAck;

    public MakingOrderNode(
            String name,
            Direction direction,
            MakingVenueConfig makingVenueConfig) {
        this.name = name;
        this.direction = direction;
        this.makingVenueConfig = makingVenueConfig;

        book = makingVenueConfig.getBook();
        symbol = makingVenueConfig.getSymbol();
        feedName = makingVenueConfig.getFeedName() == null ? makingVenueConfig.getVenueName() : makingVenueConfig.getFeedName();
        makerVenue = makingVenueConfig.getVenueName() == null ? feedName : makingVenueConfig.getVenueName();

        log.info("name:{}, book:{}, symbol:{}, feedName:{}, makerVenue:{}, makingVenueConfig:{}",
                getName(), book, symbol, makerVenue, makerVenue, makingVenueConfig);
    }

    @Initialise
    public void init() {
        log.info("name:{}, lifecycle: init", getName());
        connected = false;
        targetQuantity = Double.NaN;
        targetPrice = Double.NaN;
    }

    @Start
    public void start() {
        log.info("name:{}, start", getName());
        if (orderExecutor != null) {
            orderExecutor.addOrderListener(this);
        }
    }

    @ServiceRegistered
    public void orderExecutor(OrderExecutor orderExecutor, String name) {
        if (orderExecutor.getFeedName().equals(feedName) && orderExecutor.isVenueRegistered(makerVenue)) {
            this.orderExecutor = orderExecutor;
            log.info("name:{}, orderExecutorRegistered:{}, feedName:{}, makerVenue:{}",
                    getName(), orderExecutor, feedName, makerVenue);
        } else {
            log.info("name:{}, orderExecutorIgnored:{}, feedName:{}, makerVenue:{}",
                    getName(), orderExecutor, feedName, makerVenue);
        }
    }

    public void modify(double quantity, double price) {

        targetQuantity = VenueOrderTransformer.transformQuantity(quantity, makingVenueConfig);
        targetPrice = VenueOrderTransformer.transformPrice(price, makingVenueConfig);
        log.info("name:{}, quantity:{}, transformedTargetQuantity:{}, price:{}, transformedTargetPrice:{}, pendingAckBefore:{}",
                getName(), quantity, targetQuantity, price, targetPrice, pendingAck);

        if (orderExecutor == null | !connected) {
            log.info("name:{}, modifyAction:cached, orderExecutor:{}, connected:{}",
                    getName(), orderExecutor, connected);
            return;
        }

        if (pendingAck & !validOrder()) {
            log.info("name:{}, modifyAction:cancelOrder, validOrder:{}", getName(), validOrder());
            cancelOrder();
        } else if (!validOrder()) {
            log.info("name:{}, modifyAction:none, invalidOrder:{}", getName(), validOrder());
        } else if (isOrderClosed()) {
            pendingAck = true;
            log.info("name:{}, modifyAction:createNewOrder", getName());
            liverOrder = orderExecutor.createOrder(makerVenue, book, direction, symbol, quantity, price);
        } else if (!pendingAck & (liverOrder.leavesQuantity() != targetQuantity | liverOrder.price() != targetPrice)) {
            pendingAck = true;
            log.info("name:{}, modifyAction:modifyExistingOrder", getName());
            orderExecutor.modifyOrder(liverOrder.clOrdId(), targetPrice, targetQuantity);
        } else {
            log.info("name:{}, modifyAction:none", getName());
        }
        log.info("name:{}, pendingAckAfter:{}", getName(), pendingAck);
    }

    public void cancelOrder() {
        targetQuantity = Double.NaN;
        targetPrice = Double.NaN;
        if (pendingAck) {
            log.info("name:{}, cacheCancelOrderRequest:{}, pendingAck:{}",
                    getName(), liverOrder == null ? "noLiveOrder" : liverOrder.clOrdId(), pendingAck);
        } else if (!isOrderClosed()) {
            log.info("name:{}, sendCancelOrderRequest:{}", getName(), liverOrder.clOrdId());
            pendingAck = true;
            orderExecutor.cancelOrder(liverOrder.clOrdId());
        } else {
            log.info("name:{}, ignoreCancelOrder:{}, orderStatus:{}, isOrderClosed:{}",
                    getName(), liverOrder == null ? "noLiveOrder" : liverOrder.clOrdId(),
                    liverOrder == null ? "noLiveOrder" : liverOrder.orderStatus(),
                    isOrderClosed());
            liverOrder = null;
        }
    }

    public boolean isOrderClosed() {

        return liverOrder == null
                || liverOrder.orderStatus() == OrderStatus.REJECTED
                | liverOrder.orderStatus() == OrderStatus.CANCELLED
                | liverOrder.orderStatus() == OrderStatus.FILLED;
    }

    @Override
    public boolean orderVenueConnected(OrderVenueConnectedEvent name) {
        if (orderExecutor != null && orderExecutor.getFeedName().equals(name.name())) {
            connected = true;
            log.info("name:{}, connected:{}", getName(), connected);
            if (validOrder()) {
                log.info("name:{}, sendCachedOrder:true", getName());
                modify(targetQuantity, targetPrice);
            }
        }
        return false;
    }

    @Override
    public boolean orderVenueDisconnected(OrderVenueDisconnectedEvent name) {
        if (orderExecutor != null && orderExecutor.getFeedName().equals(name.name())) {
            connected = false;
            log.info("name:{}, connected:{}", getName(), connected);
        }
        return false;
    }

    @Override
    public boolean orderFill(OrderFillEvent orderFillEvent) {
        Order order = orderFillEvent.order();
        if (liverOrder != null && liverOrder.clOrdId() == order.clOrdId()) {
            log.info("name:{}, orderFill:{}", getName(), order);
            modifyIfRequired();
        }
        return false;
    }

    @Override
    public boolean orderFilled(OrderFilledEvent orderFilledEvent) {
        Order order = orderFilledEvent.order();
        if (liverOrder != null && liverOrder.clOrdId() == order.clOrdId()) {
            log.info("name:{}, orderFilled:{}", getName(), order);
            liverOrder = null;
            modifyIfRequired();
        }

        return false;
    }

    @Override
    public boolean orderUpdate(Order order) {
        if (liverOrder != null && liverOrder.clOrdId() == order.clOrdId()) {
            liverOrder = order;
            log.info("name:{}, pendingAckBeforeUpdate:{}, orderStatus:{}",
                    getName(), pendingAck, liverOrder.orderStatus());
            pendingAck = liverOrder.orderStatus() == OrderStatus.PENDING_NEW | liverOrder.orderStatus() == OrderStatus.CREATED;
            log.info("name:{}, pendingAckAfterUpdate:{}", getName(), pendingAck);
            modifyIfRequired();
            log.info("name:{}, pendingAckAfterModify:{}, orderUpdate:{}",
                    getName(), pendingAck, order);
        }
        return false;
    }

    @Override
    public boolean orderRejected(Order order) {
        //can this be called with modify reject
        if (liverOrder != null && liverOrder.clOrdId() == order.clOrdId()) {
            log.info("name:{}, orderRejected:{}", getName(), order);
            liverOrder = null;
            pendingAck = false;
            modifyIfRequired();
        }
        return false;
    }

    @Override
    public boolean orderCancelled(Order order) {
        if (liverOrder != null && liverOrder.clOrdId() == order.clOrdId()) {
            log.info("name:{}, orderCancelled:{}", getName(), order);
            liverOrder = null;
            pendingAck = false;
            modifyIfRequired();
        }
        return false;
    }

    @Override
    public boolean orderDoneForDay(Order order) {
        return orderCancelled(order);
    }

    @Override
    public boolean cancelRejected(Order order) {
        if (liverOrder != null && liverOrder.clOrdId() == order.clOrdId()) {
            log.info("name:{}, cancelRejected:{}", getName(), order);
            pendingAck = false;
            modifyIfRequired();
        }
        return false;
    }

    private void modifyIfRequired() {
        if (!pendingAck) {
            modify(targetQuantity, targetPrice);
        } else {
            log.info("name:{}, modifyAction:{}", getName(), "none");
        }
    }

    private boolean validOrder() {
        return !Double.isNaN(targetPrice + targetQuantity) && Double.compare(targetPrice, 0) > 0 && Double.compare(targetQuantity, 0) > 0;
    }
}
