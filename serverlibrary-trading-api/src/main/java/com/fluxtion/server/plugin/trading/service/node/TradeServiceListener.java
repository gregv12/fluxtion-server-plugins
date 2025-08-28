package com.fluxtion.server.plugin.trading.service.node;

import com.fluxtion.server.plugin.trading.service.marketdata.MarketDataFeed;
import com.fluxtion.server.plugin.trading.service.order.OrderExecutor;
import com.fluxtion.server.service.admin.AdminCommandRegistry;

/**
 * TradeServiceListener defines lifecycle and service discovery hooks for trading-related
 * nodes within a Fluxtion graph. Implementors can react to:
 * - init/start/stop lifecycle stages,
 * - discovery of MarketDataFeed and OrderExecutor services,
 * - registration of an AdminCommandRegistry,
 * - generic events via onEvent(Object), and
 * - periodic compute via calculate().
 */
public interface TradeServiceListener {

    default void init(){}

    default void start(){}

    default void stop(){}

    default void marketFeedRegistered(MarketDataFeed marketDataFeed, String name){}

    default void orderExecutorRegistered(OrderExecutor orderExecutor, String serviceName){}

    default void adminClient(AdminCommandRegistry adminCommandRegistry){}

    default boolean onEvent(Object event){
        return false;
    }

    default void calculate(){
    }
}
