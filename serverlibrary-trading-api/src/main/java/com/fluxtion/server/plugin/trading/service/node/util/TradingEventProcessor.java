package com.fluxtion.server.plugin.trading.service.node.util;

import com.fluxtion.runtime.node.ObjectEventHandlerNode;
import com.fluxtion.server.internal.ConfigAwareEventProcessor;
import com.fluxtion.server.plugin.trading.service.marketdata.*;
import com.fluxtion.server.plugin.trading.service.order.*;
import com.fluxtion.server.service.admin.AdminCommandRegistry;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.List;

@Log4j2
public class TradingEventProcessor extends ConfigAwareEventProcessor implements MarketDataListener, OrderListener {

    @Getter
    private final List<MarketDataListener> marketDataListeners = new java.util.ArrayList<>();
    @Getter
    private final List<OrderListener> orderListeners = new java.util.ArrayList<>();
    @Getter
    private AdminCommandRegistry adminCommandRegistry;

    public TradingEventProcessor(ObjectEventHandlerNode allEventHandler) {
        super(allEventHandler);

        //add MarketDataListener
        if (allEventHandler instanceof MarketDataListener) {
            marketDataListeners.add((MarketDataListener) allEventHandler);
        }

        //add OrderListener
        if (allEventHandler instanceof OrderListener) {
            orderListeners.add((OrderListener) allEventHandler);
        }
    }


    @Override
    public boolean onMarketData(MarketDataBook marketDataBook) {
        boolean handled = false;
        for (int i = 0, n = marketDataListeners.size(); i < n; i++) {
            handled |= marketDataListeners.get(i).onMarketData(marketDataBook);
        }
        return handled;
    }

    @Override
    public boolean marketDataVenueConnected(MarketConnected marketConnected) {
        boolean handled = false;
        log.info("marketDataVenueConnected: {}", marketConnected);
        for (int i = 0, n = marketDataListeners.size(); i < n; i++) {
            handled |= marketDataListeners.get(i).marketDataVenueConnected(marketConnected);
        }
        return handled;
    }

    @Override
    public boolean marketDataVenueDisconnected(MarketDisconnected marketDisconnected) {
        boolean handled = false;
        log.info("marketDataVenueDisconnected: {}", marketDisconnected);
        for (int i = 0, n = marketDataListeners.size(); i < n; i++) {
            handled |= marketDataListeners.get(i).marketDataVenueDisconnected(marketDisconnected);
        }
        return handled;
    }

    @Override
    public boolean orderVenueConnected(OrderVenueConnectedEvent orderVenueConnectedEvent) {
        boolean handled = false;
        log.info("orderVenueConnected: {}", orderVenueConnectedEvent);
        for (int i = 0, n = orderListeners.size(); i < n; i++) {
            handled |= orderListeners.get(i).orderVenueConnected(orderVenueConnectedEvent);
        }
        return handled;
    }

    @Override
    public boolean orderVenueDisconnected(OrderVenueDisconnectedEvent orderVenueDisconnectedEvent) {
        boolean handled = false;
        log.info("orderVenueDisconnected: {}", orderVenueDisconnectedEvent);
        for (int i = 0, n = orderListeners.size(); i < n; i++) {
            handled |= orderListeners.get(i).orderVenueDisconnected(orderVenueDisconnectedEvent);
        }
        return handled;
    }

    @Override
    public boolean orderUpdate(Order order) {
        boolean handled = false;
        for (int i = 0, n = orderListeners.size(); i < n; i++) {
            handled |= orderListeners.get(i).orderUpdate(order);
        }
        return handled;
    }

    @Override
    public boolean orderRejected(Order order) {
        boolean handled = false;
        for (int i = 0, n = orderListeners.size(); i < n; i++) {
            handled |= orderListeners.get(i).orderRejected(order);
        }
        return handled;
    }

    @Override
    public boolean orderCancelled(Order order) {
        boolean handled = false;
        for (int i = 0, n = orderListeners.size(); i < n; i++) {
            handled |= orderListeners.get(i).orderCancelled(order);
        }
        return handled;
    }

    @Override
    public boolean orderDoneForDay(Order order) {
        boolean handled = false;
        for (int i = 0, n = orderListeners.size(); i < n; i++) {
            handled |= orderListeners.get(i).orderDoneForDay(order);
        }
        return handled;
    }

    @Override
    public boolean cancelRejected(Order order) {
        boolean handled = false;
        for (int i = 0, n = orderListeners.size(); i < n; i++) {
            handled |= orderListeners.get(i).cancelRejected(order);
        }
        return handled;
    }

    @Override
    public boolean orderFill(OrderFillEvent orderFillEvent) {
        boolean handled = false;
        for (int i = 0, n = orderListeners.size(); i < n; i++) {
            handled |= orderListeners.get(i).orderFill(orderFillEvent);
        }
        return handled;
    }

    @Override
    public boolean orderFilled(OrderFilledEvent orderFilledEvent) {
        boolean handled = false;
        for (int i = 0, n = orderListeners.size(); i < n; i++) {
            handled |= orderListeners.get(i).orderFilled(orderFilledEvent);
        }
        return handled;
    }
}