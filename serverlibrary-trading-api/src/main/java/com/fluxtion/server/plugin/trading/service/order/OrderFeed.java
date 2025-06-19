package com.fluxtion.server.plugin.trading.service.order;

public interface OrderFeed {

    void addOrderListener(OrderListener orderListener);

    String getFeedName();
}
