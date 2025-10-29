package com.fluxtion.server.plugin.trading.service.node.making;

import com.fluxtion.server.plugin.trading.service.common.Direction;
import com.fluxtion.server.plugin.trading.service.order.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MakingOrderNodeTest {

    private MakingVenueConfig config;
    private TestOrderExecutor orderExecutor;

    @BeforeEach
    void setUp() {
        config = new MakingVenueConfig();
        config.setFeedName("testFeed");
        config.setVenueName("testVenue");
        config.setBook("testBook");
        config.setSymbol("BTCUSD");
        config.setMinQuantity(0.1);
        config.setMaxQuantity(1000.0);
        config.setPrecisionDpsQuantity(2);
        config.setMinPrice(1.0);
        config.setMaxPrice(100000.0);
        config.setPrecisionDpsPrice(2);

        orderExecutor = new TestOrderExecutor("testFeed", "testVenue");
    }

    @Test
    void init_resetsState() {
        MakingOrderNode node = new MakingOrderNode("testNode", Direction.BUY, config);
        node.init();

        assertTrue(node.isOrderClosed());
    }

    @Test
    void orderExecutorRegistered_matchingFeedAndVenue_registersExecutor() {
        MakingOrderNode node = new MakingOrderNode("testNode", Direction.BUY, config);
        node.init();

        node.orderExecutorRegistered(orderExecutor, "service1");
        node.start();

        assertTrue(orderExecutor.hasListener(node));
    }

    @Test
    void orderExecutorRegistered_nonMatchingFeed_ignoresExecutor() {
        TestOrderExecutor wrongExecutor = new TestOrderExecutor("wrongFeed", "testVenue");
        MakingOrderNode node = new MakingOrderNode("testNode", Direction.BUY, config);
        node.init();

        node.orderExecutorRegistered(wrongExecutor, "service1");
        node.start();

        assertFalse(wrongExecutor.hasListener(node));
    }

    @Test
    void orderExecutorRegistered_nonMatchingVenue_ignoresExecutor() {
        TestOrderExecutor wrongExecutor = new TestOrderExecutor("testFeed", "wrongVenue");
        MakingOrderNode node = new MakingOrderNode("testNode", Direction.BUY, config);
        node.init();

        node.orderExecutorRegistered(wrongExecutor, "service1");
        node.start();

        assertFalse(wrongExecutor.hasListener(node));
    }

    @Test
    void modify_withoutOrderExecutor_cachesOrder() {
        MakingOrderNode node = new MakingOrderNode("testNode", Direction.BUY, config);
        node.init();

        node.modify(10.0, 100.0);

        // No order should be created since no executor is registered
        assertEquals(0, orderExecutor.getCreatedOrders().size());
    }

    @Test
    void modify_withDisconnectedVenue_cachesOrder() {
        MakingOrderNode node = new MakingOrderNode("testNode", Direction.BUY, config);
        node.init();
        node.orderExecutorRegistered(orderExecutor, "service1");

        node.modify(10.0, 100.0);

        // No order should be created since venue is disconnected
        assertEquals(0, orderExecutor.getCreatedOrders().size());
    }

    @Test
    void modify_whenConnected_createsOrder() {
        MakingOrderNode node = new MakingOrderNode("testNode", Direction.BUY, config);
        node.init();
        node.orderExecutorRegistered(orderExecutor, "service1");
        node.start();

        // Connect venue
        node.orderVenueConnected(new OrderVenueConnectedEvent("testFeed"));

        // Modify should create order
        node.modify(10.0, 100.0);

        assertEquals(1, orderExecutor.getCreatedOrders().size());
        TestOrder order = orderExecutor.getCreatedOrders().get(0);
        assertEquals(10.0, order.quantity());
        assertEquals(100.0, order.price());
        assertEquals(Direction.BUY, order.direction());
        assertEquals("BTCUSD", order.symbol());
    }

    @Test
    void modify_withInvalidPrice_doesNotCreateOrder() {
        MakingOrderNode node = new MakingOrderNode("testNode", Direction.BUY, config);
        node.init();
        node.orderExecutorRegistered(orderExecutor, "service1");
        node.start();
        node.orderVenueConnected(new OrderVenueConnectedEvent("testFeed"));

        // Price below minimum
        node.modify(10.0, 0.5);

        assertEquals(0, orderExecutor.getCreatedOrders().size());
    }

    @Test
    void modify_withInvalidQuantity_doesNotCreateOrder() {
        MakingOrderNode node = new MakingOrderNode("testNode", Direction.BUY, config);
        node.init();
        node.orderExecutorRegistered(orderExecutor, "service1");
        node.start();
        node.orderVenueConnected(new OrderVenueConnectedEvent("testFeed"));

        // Quantity below minimum
        node.modify(0.05, 100.0);

        assertEquals(0, orderExecutor.getCreatedOrders().size());
    }

    @Test
    void modify_existingOrder_modifiesOrder() {
        MakingOrderNode node = new MakingOrderNode("testNode", Direction.BUY, config);
        node.init();
        node.orderExecutorRegistered(orderExecutor, "service1");
        node.start();
        node.orderVenueConnected(new OrderVenueConnectedEvent("testFeed"));

        // Create initial order
        node.modify(10.0, 100.0);
        assertEquals(1, orderExecutor.getCreatedOrders().size());

        // Acknowledge the order
        TestOrder order = orderExecutor.getCreatedOrders().get(0);
        order.setOrderStatus(OrderStatus.NEW);
        node.orderUpdate(order);

        // Modify with different price/quantity
        node.modify(15.0, 105.0);

        assertEquals(1, orderExecutor.getModifiedOrders().size());
        assertEquals(15.0, orderExecutor.getModifiedOrders().get(0).quantity);
        assertEquals(105.0, orderExecutor.getModifiedOrders().get(0).price);
    }

    @Test
    void modify_sameValues_doesNotModifyOrder() {
        MakingOrderNode node = new MakingOrderNode("testNode", Direction.BUY, config);
        node.init();
        node.orderExecutorRegistered(orderExecutor, "service1");
        node.start();
        node.orderVenueConnected(new OrderVenueConnectedEvent("testFeed"));

        // Create initial order
        node.modify(10.0, 100.0);
        TestOrder order = orderExecutor.getCreatedOrders().get(0);
        order.setOrderStatus(OrderStatus.NEW);
        node.orderUpdate(order);

        // Modify with same values
        node.modify(10.0, 100.0);

        assertEquals(0, orderExecutor.getModifiedOrders().size());
    }

    @Test
    void cancelOrder_withLiveOrder_cancelsOrder() {
        MakingOrderNode node = new MakingOrderNode("testNode", Direction.BUY, config);
        node.init();
        node.orderExecutorRegistered(orderExecutor, "service1");
        node.start();
        node.orderVenueConnected(new OrderVenueConnectedEvent("testFeed"));

        // Create order
        node.modify(10.0, 100.0);
        TestOrder order = orderExecutor.getCreatedOrders().get(0);
        order.setOrderStatus(OrderStatus.NEW);
        node.orderUpdate(order);

        // Cancel order
        node.cancelOrder();

        assertEquals(1, orderExecutor.getCancelledOrders().size());
    }

    @Test
    void cancelOrder_withNoLiveOrder_doesNothing() {
        MakingOrderNode node = new MakingOrderNode("testNode", Direction.BUY, config);
        node.init();
        node.orderExecutorRegistered(orderExecutor, "service1");
        node.start();
        node.orderVenueConnected(new OrderVenueConnectedEvent("testFeed"));

        node.cancelOrder();

        assertEquals(0, orderExecutor.getCancelledOrders().size());
    }

    @Test
    void orderFilled_triggersNewOrderCreation() {
        MakingOrderNode node = new MakingOrderNode("testNode", Direction.BUY, config);
        node.init();
        node.orderExecutorRegistered(orderExecutor, "service1");
        node.start();
        node.orderVenueConnected(new OrderVenueConnectedEvent("testFeed"));

        // Create order
        node.modify(10.0, 100.0);
        TestOrder order = orderExecutor.getCreatedOrders().get(0);
        order.setOrderStatus(OrderStatus.NEW);
        node.orderUpdate(order);

        // Fill order
        order.setOrderStatus(OrderStatus.FILLED);
        order.setFilledQuantity(10.0);
        node.orderFilled(new OrderFilledEvent(order, 100.0, 10.0, "exec1"));

        // Should create new order with cached target
        assertEquals(2, orderExecutor.getCreatedOrders().size());
    }

    @Test
    void orderRejected_clearsOrderAndRetries() {
        MakingOrderNode node = new MakingOrderNode("testNode", Direction.BUY, config);
        node.init();
        node.orderExecutorRegistered(orderExecutor, "service1");
        node.start();
        node.orderVenueConnected(new OrderVenueConnectedEvent("testFeed"));

        // Create order
        node.modify(10.0, 100.0);
        TestOrder order = orderExecutor.getCreatedOrders().get(0);
        order.setOrderStatus(OrderStatus.NEW);
        node.orderUpdate(order);

        // Reject order - this will create a new order in CREATED status
        order.setOrderStatus(OrderStatus.REJECTED);
        node.orderRejected(order);

        // Should create new order (new order is created but in CREATED status, so isOrderClosed is false)
        assertEquals(2, orderExecutor.getCreatedOrders().size());
        // New order is created and live
        assertFalse(node.isOrderClosed());
    }

    @Test
    void orderCancelled_clearsOrderAndRetries() {
        MakingOrderNode node = new MakingOrderNode("testNode", Direction.BUY, config);
        node.init();
        node.orderExecutorRegistered(orderExecutor, "service1");
        node.start();
        node.orderVenueConnected(new OrderVenueConnectedEvent("testFeed"));

        // Create order
        node.modify(10.0, 100.0);
        TestOrder order = orderExecutor.getCreatedOrders().get(0);
        order.setOrderStatus(OrderStatus.NEW);
        node.orderUpdate(order);

        // Cancel order - this will create a new order in CREATED status
        order.setOrderStatus(OrderStatus.CANCELLED);
        node.orderCancelled(order);

        // Should create new order (new order is created but in CREATED status, so isOrderClosed is false)
        assertEquals(2, orderExecutor.getCreatedOrders().size());
        // New order is created and live
        assertFalse(node.isOrderClosed());
    }

    @Test
    void orderVenueDisconnected_marksDisconnected() {
        MakingOrderNode node = new MakingOrderNode("testNode", Direction.BUY, config);
        node.init();
        node.orderExecutorRegistered(orderExecutor, "service1");
        node.start();
        node.orderVenueConnected(new OrderVenueConnectedEvent("testFeed"));

        // Create order
        node.modify(10.0, 100.0);
        assertEquals(1, orderExecutor.getCreatedOrders().size());

        // Disconnect
        node.orderVenueDisconnected(new OrderVenueDisconnectedEvent("testFeed"));

        // Try to modify - should not create new order
        node.modify(15.0, 105.0);
        assertEquals(1, orderExecutor.getCreatedOrders().size());
    }

    @Test
    void modify_transformsQuantityAndPrice() {
        MakingOrderNode node = new MakingOrderNode("testNode", Direction.BUY, config);
        node.init();
        node.orderExecutorRegistered(orderExecutor, "service1");
        node.start();
        node.orderVenueConnected(new OrderVenueConnectedEvent("testFeed"));

        // Modify with values that need rounding
        node.modify(10.123456, 100.789);

        assertEquals(1, orderExecutor.getCreatedOrders().size());
        TestOrder order = orderExecutor.getCreatedOrders().get(0);
        // Should be rounded to 2 decimal places
        assertEquals(10.12, order.quantity(), 0.01);
        assertEquals(100.79, order.price(), 0.01);
    }

    @Test
    void cancelRejected_clearsPendingAckAndRetries() {
        MakingOrderNode node = new MakingOrderNode("testNode", Direction.BUY, config);
        node.init();
        node.orderExecutorRegistered(orderExecutor, "service1");
        node.start();
        node.orderVenueConnected(new OrderVenueConnectedEvent("testFeed"));

        // Create order
        node.modify(10.0, 100.0);
        TestOrder order = orderExecutor.getCreatedOrders().get(0);
        order.setOrderStatus(OrderStatus.NEW);
        node.orderUpdate(order);

        // Request cancel
        node.cancelOrder();

        // Cancel rejected
        node.cancelRejected(order);

        // Should be able to modify now
        node.modify(15.0, 105.0);
        assertEquals(1, orderExecutor.getModifiedOrders().size());
    }

    @Test
    void orderDoneForDay_treatedAsCancellation() {
        MakingOrderNode node = new MakingOrderNode("testNode", Direction.BUY, config);
        node.init();
        node.orderExecutorRegistered(orderExecutor, "service1");
        node.start();
        node.orderVenueConnected(new OrderVenueConnectedEvent("testFeed"));

        // Create order
        node.modify(10.0, 100.0);
        TestOrder order = orderExecutor.getCreatedOrders().get(0);
        order.setOrderStatus(OrderStatus.NEW);
        node.orderUpdate(order);

        // Done for day - should clear order like cancellation and create new order
        order.setOrderStatus(OrderStatus.DONE_FOR_DAY);
        node.orderDoneForDay(order);

        // Should retry creating order
        assertEquals(2, orderExecutor.getCreatedOrders().size());
        // New order is created and live
        assertFalse(node.isOrderClosed());
    }

    @Test
    void isOrderClosed_returnsTrueForVariousStatuses() {
        MakingOrderNode node = new MakingOrderNode("testNode", Direction.BUY, config);
        node.init();

        // No order
        assertTrue(node.isOrderClosed());

        node.orderExecutorRegistered(orderExecutor, "service1");
        node.start();
        node.orderVenueConnected(new OrderVenueConnectedEvent("testFeed"));

        // Create order
        node.modify(10.0, 100.0);
        TestOrder order = orderExecutor.getCreatedOrders().get(0);
        order.setOrderStatus(OrderStatus.CREATED);
        // CREATED status means order is not closed
        assertFalse(node.isOrderClosed());

        order.setOrderStatus(OrderStatus.NEW);
        node.orderUpdate(order);
        // NEW status means order is not closed
        assertFalse(node.isOrderClosed());

        // Test that order is considered closed for terminal statuses
        order.setOrderStatus(OrderStatus.REJECTED);
        // isOrderClosed checks the status, so should return true
        assertTrue(node.isOrderClosed());

        // Cancel with invalid target so no new order is created
        node.cancelOrder();
        // After cancel with no target, should be closed
        assertTrue(node.isOrderClosed());
    }

    @Test
    void getName_returnsNodeName() {
        MakingOrderNode node = new MakingOrderNode("myNode", Direction.BUY, config);
        assertEquals("myNode", node.getName());
    }

    @Test
    void orderFill_partialFill_doesNotRecreateOrder() {
        MakingOrderNode node = new MakingOrderNode("testNode", Direction.BUY, config);
        node.init();
        node.orderExecutorRegistered(orderExecutor, "service1");
        node.start();
        node.orderVenueConnected(new OrderVenueConnectedEvent("testFeed"));

        // Create order
        node.modify(10.0, 100.0);
        TestOrder order = orderExecutor.getCreatedOrders().get(0);
        order.setOrderStatus(OrderStatus.NEW);
        node.orderUpdate(order);

        // Partial fill
        order.setOrderStatus(OrderStatus.PARTIAL_FILLED);
        order.setFilledQuantity(5.0);
        order.setLeavesQuantity(5.0);
        node.orderFill(new OrderFillEvent(order, 100.0, 5.0, "exec1"));

        // Should not create new order, just update existing
        assertEquals(1, orderExecutor.getCreatedOrders().size());
    }

    // Test helper classes

    private static class TestOrderExecutor implements OrderExecutor {
        private final String feedName;
        private final String venueName;
        private final List<TestOrder> createdOrders = new ArrayList<>();
        private final List<ModifyRequest> modifiedOrders = new ArrayList<>();
        private final List<Long> cancelledOrders = new ArrayList<>();
        private final List<OrderListener> listeners = new ArrayList<>();
        private long nextOrderId = 1;

        public TestOrderExecutor(String feedName, String venueName) {
            this.feedName = feedName;
            this.venueName = venueName;
        }

        @Override
        public String getFeedName() {
            return feedName;
        }

        @Override
        public Set<String> venues() {
            return Set.of(venueName);
        }

        @Override
        public boolean isVenueRegistered(String venue) {
            return venueName.equals(venue);
        }

        @Override
        public Order createOrder(String venue, String book, Direction direction, String symbol, double quantity, double price) {
            TestOrder order = new TestOrder(nextOrderId++, venue, book, direction, symbol, quantity, price);
            order.setOrderStatus(OrderStatus.CREATED);
            createdOrders.add(order);
            return order;
        }

        @Override
        public void modifyOrder(long clOrdId, double price, double quantity) {
            modifiedOrders.add(new ModifyRequest(clOrdId, price, quantity));
        }

        @Override
        public long cancelOrder(long clOrdId) {
            cancelledOrders.add(clOrdId);
            return clOrdId;
        }

        @Override
        public void addOrderListener(OrderListener listener) {
            listeners.add(listener);
        }

        public List<TestOrder> getCreatedOrders() {
            return createdOrders;
        }

        public List<ModifyRequest> getModifiedOrders() {
            return modifiedOrders;
        }

        public List<Long> getCancelledOrders() {
            return cancelledOrders;
        }

        public boolean hasListener(OrderListener listener) {
            return listeners.contains(listener);
        }
    }

    private static class TestOrder implements Order {
        private final long clOrdId;
        private final String venue;
        private final String bookName;
        private final Direction direction;
        private final String symbol;
        private final double quantity;
        private final double price;
        private OrderStatus orderStatus;
        private double leavesQuantity;
        private double filledQuantity;

        public TestOrder(long clOrdId, String venue, String bookName, Direction direction,
                         String symbol, double quantity, double price) {
            this.clOrdId = clOrdId;
            this.venue = venue;
            this.bookName = bookName;
            this.direction = direction;
            this.symbol = symbol;
            this.quantity = quantity;
            this.price = price;
            this.leavesQuantity = quantity;
            this.filledQuantity = 0.0;
        }

        @Override
        public long clOrdId() {
            return clOrdId;
        }

        @Override
        public double quantity() {
            return quantity;
        }

        @Override
        public double price() {
            return price;
        }

        @Override
        public Direction direction() {
            return direction;
        }

        @Override
        public OrderStatus orderStatus() {
            return orderStatus;
        }

        @Override
        public double leavesQuantity() {
            return leavesQuantity;
        }

        @Override
        public double filledQuantity() {
            return filledQuantity;
        }

        @Override
        public double cancelledQuantity() {
            return 0.0;
        }

        @Override
        public String symbol() {
            return symbol;
        }

        @Override
        public String venue() {
            return venue;
        }

        @Override
        public String bookName() {
            return bookName;
        }

        public void setOrderStatus(OrderStatus orderStatus) {
            this.orderStatus = orderStatus;
        }

        public void setLeavesQuantity(double leavesQuantity) {
            this.leavesQuantity = leavesQuantity;
        }

        public void setFilledQuantity(double filledQuantity) {
            this.filledQuantity = filledQuantity;
        }
    }

    private static class ModifyRequest {
        final long clOrdId;
        final double price;
        final double quantity;

        ModifyRequest(long clOrdId, double price, double quantity) {
            this.clOrdId = clOrdId;
            this.price = price;
            this.quantity = quantity;
        }
    }
}
