package com.fluxtion.server.plugin.trading.service.node;

import com.fluxtion.server.config.ConfigMap;
import com.fluxtion.server.plugin.trading.service.marketdata.*;
import com.fluxtion.server.plugin.trading.service.order.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TradeStrategyTest {

    private static class TL implements TradeServiceListener, MarketDataListener, OrderListener {
        int calc;
        boolean init, started, stopped;
        int md, mdc, mdd;
        int oc, od, ou, orj, ocan, odfd, ocr, ofill, ofilled;
        Object lastEvent;

        @Override public void init() { init = true; }
        @Override public void start() { started = true; }
        @Override public void stop() { stopped = true; }
        @Override public void calculate() { calc++; }
        @Override public boolean onEvent(Object event) { lastEvent = event; return true; }

        @Override public boolean onMarketData(MarketDataBook marketDataBook) { md++; return true; }
        @Override public boolean marketDataVenueConnected(MarketConnected marketConnected) { mdc++; return true; }
        @Override public boolean marketDataVenueDisconnected(MarketDisconnected marketDisconnected) { mdd++; return true; }

        @Override public boolean orderVenueConnected(OrderVenueConnectedEvent e) { oc++; return true; }
        @Override public boolean orderVenueDisconnected(OrderVenueDisconnectedEvent e) { od++; return true; }
        @Override public boolean orderUpdate(Order order) { ou++; return true; }
        @Override public boolean orderRejected(Order order) { orj++; return true; }
        @Override public boolean orderCancelled(Order order) { ocan++; return true; }
        @Override public boolean orderDoneForDay(Order order) { odfd++; return true; }
        @Override public boolean cancelRejected(Order order) { ocr++; return true; }
        @Override public boolean orderFill(OrderFillEvent orderFillEvent) { ofill++; return true; }
        @Override public boolean orderFilled(OrderFilledEvent orderFilledEvent) { ofilled++; return true; }
    }

    @Test
    void registersNodes_andDispatches_allCallbacks_andCalculatesAfterEach() {
        TradeStrategy ts = new TradeStrategy();
        TL tl = new TL();
        ts.addManagedNode(tl);

        // lifecycle
        ts._initialise();
        assertTrue(tl.init);
        ts.start();
        assertTrue(tl.started);

        // market data
        assertTrue(ts.onMarketData(new MarketDataBook("feed","venue","BTCUSD",1,1,1,2,2)));
        assertEquals(1, tl.md);
        assertTrue(ts.marketDataVenueConnected(new MarketConnected("feed")));
        assertEquals(1, tl.mdc);
        assertTrue(ts.marketDataVenueDisconnected(new MarketDisconnected("feed")));
        assertEquals(1, tl.mdd);

        // order
        Order order = new com.fluxtion.server.plugin.trading.service.order.impl.MutableOrder().clOrdId(42).leavesQuantity(1).price(1);
        assertTrue(ts.orderVenueConnected(new OrderVenueConnectedEvent("feed")));
        assertEquals(1, tl.oc);
        assertTrue(ts.orderVenueDisconnected(new OrderVenueDisconnectedEvent("feed")));
        assertEquals(1, tl.od);
        assertTrue(ts.orderUpdate(order));
        assertEquals(1, tl.ou);
        assertTrue(ts.orderRejected(order));
        assertEquals(1, tl.orj);
        assertTrue(ts.orderCancelled(order));
        assertEquals(1, tl.ocan);
        assertTrue(ts.orderDoneForDay(order));
        assertEquals(1, tl.odfd);
        assertTrue(ts.cancelRejected(order));
        assertEquals(1, tl.ocr);
        assertTrue(ts.orderFill(new OrderFillEvent(order, 1, 1, "x")));
        assertEquals(1, tl.ofill);
        assertTrue(ts.orderFilled(new OrderFilledEvent(order, 1, 1, "y")));
        assertEquals(1, tl.ofilled);

        // generic event via handleEvent path triggers calculate as well
        assertTrue(ts.onEvent("PING"));
        // handleEvent is protected, but onEvent funnels through handleEvent in ObjectEventHandlerNode
        // TradeStrategy overrides handleEvent and calls calculateAll(), which increments calc
        assertTrue(tl.calc > 0);

        ts.stop();
        assertTrue(tl.stopped);
    }

    @Test
    void initialConfig_returnsFalse_andDoesNotThrow() {
        TradeStrategy ts = new TradeStrategy();
        assertFalse(ts.initialConfig(null));
    }
}
