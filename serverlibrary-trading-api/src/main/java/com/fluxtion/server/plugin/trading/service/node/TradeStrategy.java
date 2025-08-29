/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.trading.service.node;

import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.node.ObjectEventHandlerNode;
import com.fluxtion.server.config.ConfigListener;
import com.fluxtion.server.config.ConfigMap;
import com.fluxtion.server.plugin.trading.service.marketdata.*;
import com.fluxtion.server.plugin.trading.service.order.*;
import com.fluxtion.server.service.admin.AdminCommandRegistry;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;

/**
 * TradeStrategy is a composite trading node that receives market data and order events,
 * dispatches them to managed listeners, and coordinates lifecycle of TradeServiceListener components.
 * <p>
 * Key features:
 * - Maintains collections for MarketDataListener, OrderListener, and TradeServiceListener.
 * - Provides addManagedNode(Object) to register components by interface type.
 * - Forwards callbacks using index-based loops to minimize allocations in hot paths.
 * - Invokes calculate() on all TradeServiceListener implementations after each callback via calculateAll().
 */
@Log4j2
public class TradeStrategy extends ObjectEventHandlerNode
        implements
        ConfigListener,
        MarketDataListener,
        OrderListener {

    @Getter
    private final List<MarketDataListener> marketDataListeners = new ArrayList<>();
    @Getter
    private final List<TradeServiceListener> tradeServiceListenerSet = new ArrayList<>();
    @Getter
    private final List<OrderListener> orderListenerList = new ArrayList<>();

    // Utility: call calculate() on all TradeServiceListener nodes
    private void calculateAll() {
        for (int i = 0, n = tradeServiceListenerSet.size(); i < n; i++) {
            TradeServiceListener l = tradeServiceListenerSet.get(i);
            try {
                l.calculate();
            } catch (Exception e) {
                log.error("error dispatching calculate to {}", l, e);
            }
        }
        for (int i = 0, n = tradeServiceListenerSet.size(); i < n; i++) {
            TradeServiceListener l = tradeServiceListenerSet.get(i);
            try {
                l.postCalculate();
            } catch (Exception e) {
                log.error("error dispatching postCalculate to {}", l, e);
            }
        }
    }


    // Accepts a single parameter and registers into the appropriate listener collection
    public void addManagedNode(Object node) {
        if (node == null) {
            return;
        }
        if (node instanceof MarketDataListener mdl) {
            marketDataListeners.add(mdl);
        }
        if (node instanceof TradeServiceListener tsl) {
            tradeServiceListenerSet.add(tsl);
        }
        if (node instanceof OrderListener ol) {
            orderListenerList.add(ol);
        }
    }

    @Override
    protected void _initialise() {
        log.info("_initialise");

        for (TradeServiceListener tradeServiceListener : tradeServiceListenerSet) {
            tradeServiceListener.init();
        }
    }


    @Override
    public boolean initialConfig(ConfigMap config) {
        log.info("Initial config: {}", config);
        return false;
    }

    @ServiceRegistered
    public void marketFeedRegistered(MarketDataFeed marketDataFeed, String name) {
        log.info("Market data feed registered name:{}, service:{}", marketDataFeed.feedName(), name);
        for (TradeServiceListener tradeServiceListener : tradeServiceListenerSet) {
            tradeServiceListener.marketFeedRegistered(marketDataFeed, name);
        }
    }

    @ServiceRegistered
    public void orderExecutorRegistered(OrderExecutor orderExecutor, String serviceName) {
        log.info("Order executor registered name:{}, service:{}", orderExecutor.getFeedName(), serviceName);
        for (TradeServiceListener tradeServiceListener : tradeServiceListenerSet) {
            tradeServiceListener.orderExecutorRegistered(orderExecutor, serviceName);
        }
    }

    @ServiceRegistered
    public void adminClient(AdminCommandRegistry adminCommandRegistry) {
        log.info("admin: {}", adminCommandRegistry);
        for (TradeServiceListener tradeServiceListener : tradeServiceListenerSet) {
            tradeServiceListener.adminClient(adminCommandRegistry);
        }
    }

    @Override
    public void start() {
        super.start();
        for (TradeServiceListener tradeServiceListener : tradeServiceListenerSet) {
            tradeServiceListener.start();
        }
    }

    @Override
    protected boolean handleEvent(Object event) {
        boolean handled = false;
        for (int i = 0, n = tradeServiceListenerSet.size(); i < n; i++) {
            TradeServiceListener l = tradeServiceListenerSet.get(i);
            try {
                handled |= l.onEvent(event);
            } catch (Exception e) {
                log.error("error dispatching onMarketData to {}", l, e);
            }
        }
        calculateAll();
        return handled;
    }

    @Override
    public void stop() {
        super.stop();
        for (TradeServiceListener tradeServiceListener : tradeServiceListenerSet) {
            tradeServiceListener.stop();
        }
    }

    @Override
    public boolean onMarketData(MarketDataBook marketDataBook) {
        log.info("onMarketData: {}", marketDataBook);
        boolean handled = false;
        for (int i = 0, n = marketDataListeners.size(); i < n; i++) {
            MarketDataListener l = marketDataListeners.get(i);
            try {
                handled |= l.onMarketData(marketDataBook);
            } catch (Exception e) {
                log.error("error dispatching onMarketData to {}", l, e);
            }
        }
        calculateAll();
        return handled;
    }

    @Override
    public boolean marketDataVenueConnected(MarketConnected marketConnected) {
        log.info("marketDataVenueConnected: {}", marketConnected);
        boolean handled = false;
        for (int i = 0, n = marketDataListeners.size(); i < n; i++) {
            MarketDataListener l = marketDataListeners.get(i);
            try {
                handled |= l.marketDataVenueConnected(marketConnected);
            } catch (Exception e) {
                log.error("error dispatching marketDataVenueConnected to {}", l, e);
            }
        }
        calculateAll();
        return handled;
    }

    @Override
    public boolean marketDataVenueDisconnected(MarketDisconnected marketDisconnected) {
        log.info("marketDataVenueDisconnected: {}", marketDisconnected);
        boolean handled = false;
        for (int i = 0, n = marketDataListeners.size(); i < n; i++) {
            MarketDataListener l = marketDataListeners.get(i);
            try {
                handled |= l.marketDataVenueDisconnected(marketDisconnected);
            } catch (Exception e) {
                log.error("error dispatching marketDataVenueDisconnected to {}", l, e);
            }
        }
        calculateAll();
        return handled;
    }

    @Override
    public boolean orderVenueConnected(OrderVenueConnectedEvent orderVenueConnectedEvent) {
        boolean handled = false;
        for (int i = 0, n = orderListenerList.size(); i < n; i++) {
            OrderListener l = orderListenerList.get(i);
            try {
                handled |= l.orderVenueConnected(orderVenueConnectedEvent);
            } catch (Exception e) {
                log.error("error dispatching orderVenueConnected to {}", l, e);
            }
        }
        calculateAll();
        return handled;
    }

    @Override
    public boolean orderVenueDisconnected(OrderVenueDisconnectedEvent orderVenueDisconnectedEvent) {
        boolean handled = false;
        for (int i = 0, n = orderListenerList.size(); i < n; i++) {
            OrderListener l = orderListenerList.get(i);
            try {
                handled |= l.orderVenueDisconnected(orderVenueDisconnectedEvent);
            } catch (Exception e) {
                log.error("error dispatching orderVenueDisconnected to {}", l, e);
            }
        }
        calculateAll();
        return handled;
    }

    @Override
    public boolean orderUpdate(Order order) {
        boolean handled = false;
        for (int i = 0, n = orderListenerList.size(); i < n; i++) {
            OrderListener l = orderListenerList.get(i);
            try {
                handled |= l.orderUpdate(order);
            } catch (Exception e) {
                log.error("error dispatching orderUpdate to {}", l, e);
            }
        }
        calculateAll();
        return handled;
    }

    @Override
    public boolean orderRejected(Order order) {
        boolean handled = false;
        for (int i = 0, n = orderListenerList.size(); i < n; i++) {
            OrderListener l = orderListenerList.get(i);
            try {
                handled |= l.orderRejected(order);
            } catch (Exception e) {
                log.error("error dispatching orderRejected to {}", l, e);
            }
        }
        calculateAll();
        return handled;
    }

    @Override
    public boolean orderCancelled(Order order) {
        boolean handled = false;
        for (int i = 0, n = orderListenerList.size(); i < n; i++) {
            OrderListener l = orderListenerList.get(i);
            try {
                handled |= l.orderCancelled(order);
            } catch (Exception e) {
                log.error("error dispatching orderCancelled to {}", l, e);
            }
        }
        calculateAll();
        return handled;
    }

    @Override
    public boolean orderDoneForDay(Order order) {
        boolean handled = false;
        for (int i = 0, n = orderListenerList.size(); i < n; i++) {
            OrderListener l = orderListenerList.get(i);
            try {
                handled |= l.orderDoneForDay(order);
            } catch (Exception e) {
                log.error("error dispatching orderDoneForDay to {}", l, e);
            }
        }
        calculateAll();
        return handled;
    }

    @Override
    public boolean cancelRejected(Order order) {
        boolean handled = false;
        for (int i = 0, n = orderListenerList.size(); i < n; i++) {
            OrderListener l = orderListenerList.get(i);
            try {
                handled |= l.cancelRejected(order);
            } catch (Exception e) {
                log.error("error dispatching cancelRejected to {}", l, e);
            }
        }
        calculateAll();
        return handled;
    }

    @Override
    public boolean orderFill(OrderFillEvent orderFillEvent) {
        boolean handled = false;
        for (int i = 0, n = orderListenerList.size(); i < n; i++) {
            OrderListener l = orderListenerList.get(i);
            try {
                handled |= l.orderFill(orderFillEvent);
            } catch (Exception e) {
                log.error("error dispatching orderFill to {}", l, e);
            }
        }
        calculateAll();
        return handled;
    }

    @Override
    public boolean orderFilled(OrderFilledEvent orderFilledEvent) {
        boolean handled = false;
        for (int i = 0, n = orderListenerList.size(); i < n; i++) {
            OrderListener l = orderListenerList.get(i);
            try {
                handled |= l.orderFilled(orderFilledEvent);
            } catch (Exception e) {
                log.error("error dispatching orderFilled to {}", l, e);
            }
        }
        calculateAll();
        return handled;
    }
}
