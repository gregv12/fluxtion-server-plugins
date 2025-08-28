package com.fluxtion.server.plugin.trading.service.node;

import com.fluxtion.runtime.node.ObjectEventHandlerNode;
import com.fluxtion.server.plugin.trading.service.marketdata.*;
import com.fluxtion.server.plugin.trading.service.order.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TradingEventProcessorTest {

    private static class DummyHandler extends ObjectEventHandlerNode implements MarketDataListener, OrderListener {
        boolean mdOn, mdConn, mdDisc;
        boolean ordConn, ordDisc, upd, rej, can, dfd, canRej, fill, filled;

        @Override
        protected boolean handleEvent(Object event) { return false; }

        @Override
        public boolean onMarketData(MarketDataBook marketDataBook) { mdOn = true; return true; }

        @Override
        public boolean marketDataVenueConnected(MarketConnected marketConnected) { mdConn = true; return true; }

        @Override
        public boolean marketDataVenueDisconnected(MarketDisconnected marketDisconnected) { mdDisc = true; return true; }

        @Override
        public boolean orderVenueConnected(OrderVenueConnectedEvent orderVenueConnectedEvent) { ordConn = true; return true; }

        @Override
        public boolean orderVenueDisconnected(OrderVenueDisconnectedEvent orderVenueDisconnectedEvent) { ordDisc = true; return true; }

        @Override
        public boolean orderUpdate(Order order) { upd = true; return true; }

        @Override
        public boolean orderRejected(Order order) { rej = true; return true; }

        @Override
        public boolean orderCancelled(Order order) { can = true; return true; }

        @Override
        public boolean orderDoneForDay(Order order) { dfd = true; return true; }

        @Override
        public boolean cancelRejected(Order order) { canRej = true; return true; }

        @Override
        public boolean orderFill(OrderFillEvent orderFillEvent) { fill = true; return true; }

        @Override
        public boolean orderFilled(OrderFilledEvent orderFilledEvent) { filled = true; return true; }
    }

    @Test
    void forwardsToUnderlyingListeners_andAggregatesReturn() {
        DummyHandler handler = new DummyHandler();
        TradingEventProcessor ep = new TradingEventProcessor(handler);

        // market data
        boolean md = ep.onMarketData(new MarketDataBook("feed","venue","BTCUSD",1, 1.0,1.0,2.0,2.0));
        assertTrue(md);
        assertTrue(handler.mdOn);
        assertTrue(ep.getMarketDataListeners().size() == 1);

        assertTrue(ep.marketDataVenueConnected(new MarketConnected("feed")));
        assertTrue(handler.mdConn);
        assertTrue(ep.marketDataVenueDisconnected(new MarketDisconnected("feed")));
        assertTrue(handler.mdDisc);

        // order side
        assertTrue(ep.orderVenueConnected(new OrderVenueConnectedEvent("feed")));
        assertTrue(handler.ordConn);
        assertTrue(ep.orderVenueDisconnected(new OrderVenueDisconnectedEvent("feed")));
        assertTrue(handler.ordDisc);

        Order dummyOrder = new Order() {
            @Override public long clOrdId() { return 1; }
            @Override public double quantity() { return 1; }
            @Override public double price() { return 1; }
            @Override public com.fluxtion.server.plugin.trading.service.common.Direction direction() { return com.fluxtion.server.plugin.trading.service.common.Direction.BUY; }
            @Override public OrderStatus orderStatus() { return OrderStatus.NEW; }
            @Override public double leavesQuantity() { return 1; }
            @Override public double filledQuantity() { return 0; }
            @Override public double cancelledQuantity() { return 0; }
            @Override public String symbol() { return "BTCUSD"; }
            @Override public String venue() { return "venue"; }
            @Override public String bookName() { return "book"; }
        };
        assertTrue(ep.orderUpdate(dummyOrder));
        assertTrue(handler.upd);
        assertTrue(ep.orderRejected(dummyOrder));
        assertTrue(handler.rej);
        assertTrue(ep.orderCancelled(dummyOrder));
        assertTrue(handler.can);
        assertTrue(ep.orderDoneForDay(dummyOrder));
        assertTrue(handler.dfd);
        assertTrue(ep.cancelRejected(dummyOrder));
        assertTrue(handler.canRej);
        assertTrue(ep.orderFill(new OrderFillEvent(dummyOrder, 1.0, 1.0, "x")));
        assertTrue(handler.fill);
        assertTrue(ep.orderFilled(new OrderFilledEvent(dummyOrder, 1.0, 1.0, "y")));
        assertTrue(handler.filled);
    }
}
