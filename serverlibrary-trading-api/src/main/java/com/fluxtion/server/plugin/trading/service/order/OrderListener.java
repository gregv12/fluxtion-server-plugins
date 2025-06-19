package com.fluxtion.server.plugin.trading.service.order;

public interface OrderListener extends ExecutionListener {

    boolean orderVenueConnected(OrderVenueConnectedEvent orderVenueConnectedEvent);

    boolean orderVenueDisconnected(OrderVenueDisconnectedEvent orderVenueDisconnectedEvent);

    boolean orderUpdate(Order order);

    boolean orderRejected(Order order);

    boolean orderCancelled(Order order);

    boolean orderDoneForDay(Order order);

    boolean cancelRejected(Order order);
}
