package com.fluxtion.server.plugin.trading.component.tradevenue;

import com.fluxtion.server.plugin.trading.service.order.*;
import com.fluxtion.server.plugin.trading.service.order.impl.MutableOrder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class VenueOrderStateManagerTest {

    @Test
    void newOrderRequest_publishesUpdate_andAddsToMaps_onSuccess() {
        List<OrderEvent> events = new ArrayList<>();
        VenueOrderStateManager mgr = new VenueOrderStateManager(events::add, (java.util.function.LongSupplier) new AtomicLong(100)::incrementAndGet);

        MutableOrder order = new MutableOrder()
                .clOrdId(101)
                .symbol("SYM")
                .venue("VENUE")
                .price(10.5)
                .quantity(5.0);

        MutableOrder ret = mgr.newOrderRequest(order, () -> { /* success no-op */});
        assertSame(order, ret);

        // First event is an OrderUpdateEvent with the same order reference
        assertFalse(events.isEmpty());
        assertTrue(events.get(0) instanceof OrderUpdateEvent);
        OrderUpdateEvent upd = (OrderUpdateEvent) events.get(0);
        assertEquals(order, upd.order());

        // Maps contain entries
        assertTrue(mgr.getClOrderIdToOrderMap().containsKey(order.clOrdId()));
        assertTrue(mgr.getRequestIdToOrderMap().containsKey(order.clOrdId()));
    }

    @Test
    void newOrderRequest_onVenueException_setsRejected() {
        List<OrderEvent> events = new ArrayList<>();
        VenueOrderStateManager mgr = new VenueOrderStateManager(events::add, (java.util.function.LongSupplier) new AtomicLong(200)::incrementAndGet);

        MutableOrder order = new MutableOrder().clOrdId(201).quantity(1).price(1);

        mgr.newOrderRequest(order, () -> { throw new RuntimeException("venue down"); });

        // Expect a second update with REJECTED status
        assertTrue(events.size() >= 2);
        assertTrue(events.get(1) instanceof OrderUpdateEvent);
        OrderUpdateEvent upd2 = (OrderUpdateEvent) events.get(1);
        assertEquals(OrderStatus.REJECTED, upd2.order().orderStatus());
    }

    @Test
    void cancelRequest_movesEntryToNewRequestId() {
        List<OrderEvent> events = new ArrayList<>();
        VenueOrderStateManager mgr = new VenueOrderStateManager(events::add, (java.util.function.LongSupplier) new AtomicLong(300)::incrementAndGet);
        MutableOrder order = new MutableOrder().clOrdId(301).quantity(2).price(2);

        mgr.newOrderRequest(order, () -> {});
        long newRequestId = 9999L;
        mgr.cancelRequest(order, newRequestId, () -> {});

        assertTrue(mgr.getRequestIdToOrderMap().containsKey(order.clOrdId()));
        assertTrue(mgr.getRequestIdToOrderMap().containsKey(newRequestId));
        assertSame(order, mgr.getRequestIdToOrderMap().get(newRequestId));
    }

    @Test
    void orderFilled_publishesFilledEvent_withFilledStatus() {
        List<OrderEvent> events = new ArrayList<>();
        VenueOrderStateManager mgr = new VenueOrderStateManager(events::add, (java.util.function.LongSupplier) new AtomicLong(400)::incrementAndGet);
        MutableOrder order = new MutableOrder().clOrdId(401).quantity(3).price(3);

        mgr.orderFilled(order, 3.0, 3.0, "exec-1");

        assertFalse(events.isEmpty());
        assertTrue(events.get(0) instanceof OrderFilledEvent);
        OrderFilledEvent filled = (OrderFilledEvent) events.get(0);
        assertEquals(OrderStatus.FILLED, filled.order().orderStatus());
        assertEquals("exec-1", filled.execId());
    }

    @Test
    void seedValidation_throwsOnOutOfRange() {
        // Constructor path
        assertThrows(IllegalArgumentException.class,
                () -> new VenueOrderStateManager(e -> {}, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new VenueOrderStateManager(e -> {}, 2048));

        // Setter path
        VenueOrderStateManager mgr = new VenueOrderStateManager(e -> {});
        assertThrows(IllegalArgumentException.class, () -> mgr.setClOrderIdSeed(-1));
        assertThrows(IllegalArgumentException.class, () -> mgr.setClOrderIdSeed(2048));
    }

    @Test
    void orderLifecycle_newOrder_accepted_cancelRequest_thenFilled_validatesCorrectly() {
        List<OrderEvent> events = new ArrayList<>();
        VenueOrderStateManager mgr = new VenueOrderStateManager(events::add, (java.util.function.LongSupplier) new AtomicLong(500)::incrementAndGet);

        // 1. Create and place a new order
        MutableOrder order = new MutableOrder()
                .clOrdId(501)
                .symbol("BTCUSD")
                .venue("TALOS")
                .bookName("book1")
                .price(50000.0)
                .quantity(1.0);

        mgr.newOrderRequest(order, () -> {});

        // Validate new order request event
        assertEquals(1, events.size());
        assertTrue(events.get(0) instanceof OrderUpdateEvent);
        OrderUpdateEvent newOrderEvent = (OrderUpdateEvent) events.get(0);
        assertEquals(order, newOrderEvent.order());
        assertEquals("BTCUSD", newOrderEvent.order().symbol());
        assertEquals(1.0, newOrderEvent.order().quantity());

        // Validate order is in maps
        assertTrue(mgr.getClOrderIdToOrderMap().containsKey(501L));
        assertTrue(mgr.getRequestIdToOrderMap().containsKey(501L));

        // 2. Accept the order (simulates venue acknowledgement)
        mgr.orderAccepted(order);

        assertEquals(2, events.size());
        assertTrue(events.get(1) instanceof OrderUpdateEvent);
        OrderUpdateEvent acceptedEvent = (OrderUpdateEvent) events.get(1);
        assertEquals(OrderStatus.NEW, acceptedEvent.order().orderStatus());
        assertEquals("BTCUSD", acceptedEvent.order().symbol());
        assertEquals(50000.0, acceptedEvent.order().price());

        // 3. Request to cancel the order
        long cancelRequestId = 9001L;
        mgr.cancelRequest(order, cancelRequestId, () -> {});

        // Validate cancel request updates the requestId mapping
        assertTrue(mgr.getRequestIdToOrderMap().containsKey(cancelRequestId));
        assertSame(order, mgr.getRequestIdToOrderMap().get(cancelRequestId));

        // 4. Order gets filled (despite cancel request - race condition scenario)
        double fillPrice = 50000.0;
        double fillQuantity = 1.0;
        String execId = "exec-12345";

        mgr.orderFilled(order, fillPrice, fillQuantity, execId);

        // Validate filled event
        assertEquals(3, events.size());
        assertTrue(events.get(2) instanceof OrderFilledEvent);
        OrderFilledEvent filledEvent = (OrderFilledEvent) events.get(2);

        // Validate fill details
        assertEquals(OrderStatus.FILLED, filledEvent.order().orderStatus());
        assertEquals(fillPrice, filledEvent.price());
        assertEquals(fillQuantity, filledEvent.quantity());
        assertEquals(execId, filledEvent.execId());

        // Validate order details preserved in fill event
        assertEquals("BTCUSD", filledEvent.order().symbol());
        assertEquals("TALOS", filledEvent.order().venue());
        assertEquals("book1", filledEvent.order().bookName());
        assertEquals(501L, filledEvent.order().clOrdId());
    }

    @Test
    void orderLifecycle_partialFill_thenFullFill_validatesQuantities() {
        List<OrderEvent> events = new ArrayList<>();
        VenueOrderStateManager mgr = new VenueOrderStateManager(events::add, (java.util.function.LongSupplier) new AtomicLong(600)::incrementAndGet);

        MutableOrder order = new MutableOrder()
                .clOrdId(601)
                .symbol("ETHUSD")
                .venue("TALOS")
                .price(3000.0)
                .quantity(10.0)
                .filledQuantity(0)
                .leavesQuantity(10.0);

        // Place order
        mgr.newOrderRequest(order, () -> {});
        mgr.orderAccepted(order);

        // Partial fill - 3 units at 3000
        mgr.orderPartiallyFilled(order, 3000.0, 3.0, "exec-partial-1");

        assertEquals(3, events.size());
        assertTrue(events.get(2) instanceof OrderFillEvent);
        OrderFillEvent partialFill = (OrderFillEvent) events.get(2);
        assertEquals(3000.0, partialFill.price());
        assertEquals(3.0, partialFill.quantity());
        assertEquals("exec-partial-1", partialFill.execId());

        // Update order state for remaining quantity
        order.filledQuantity(3.0).leavesQuantity(7.0);

        // Final fill - remaining 7 units at 3010
        mgr.orderFilled(order, 3010.0, 7.0, "exec-final");

        assertEquals(4, events.size());
        assertTrue(events.get(3) instanceof OrderFilledEvent);
        OrderFilledEvent finalFill = (OrderFilledEvent) events.get(3);
        assertEquals(OrderStatus.FILLED, finalFill.order().orderStatus());
        assertEquals(3010.0, finalFill.price());
        assertEquals(7.0, finalFill.quantity());
        assertEquals("exec-final", finalFill.execId());
    }

    @Test
    void orderCancelled_validatesQuantitiesCalculated() {
        List<OrderEvent> events = new ArrayList<>();
        VenueOrderStateManager mgr = new VenueOrderStateManager(events::add, (java.util.function.LongSupplier) new AtomicLong(700)::incrementAndGet);

        MutableOrder order = new MutableOrder()
                .clOrdId(701)
                .symbol("XRPUSD")
                .venue("TALOS")
                .price(0.5)
                .quantity(100.0)
                .filledQuantity(30.0)
                .leavesQuantity(70.0);

        // Place and accept order
        mgr.newOrderRequest(order, () -> {});
        mgr.orderAccepted(order);

        // Cancel the order after partial fill
        mgr.orderCancelled(order);

        assertEquals(3, events.size());
        assertTrue(events.get(2) instanceof OrderCancelEvent);
        OrderCancelEvent cancelEvent = (OrderCancelEvent) events.get(2);

        // Validate cancelled quantities are calculated correctly
        assertEquals(OrderStatus.CANCELLED, cancelEvent.order().orderStatus());
        assertEquals(70.0, cancelEvent.order().cancelledQuantity()); // quantity - filledQuantity
        assertEquals(0.0, cancelEvent.order().leavesQuantity()); // should be 0 after cancel
    }

    @Test
    void getLiveOrders_returnsOnlyLiveStatusOrders() {
        List<OrderEvent> events = new ArrayList<>();
        VenueOrderStateManager mgr = new VenueOrderStateManager(events::add, (java.util.function.LongSupplier) new AtomicLong(800)::incrementAndGet);

        // Create orders with different statuses
        MutableOrder createdOrder = new MutableOrder().clOrdId(801).symbol("SYM1").quantity(1).price(1);
        MutableOrder newOrder = new MutableOrder().clOrdId(802).symbol("SYM2").quantity(1).price(1);
        MutableOrder filledOrder = new MutableOrder().clOrdId(803).symbol("SYM3").quantity(1).price(1);
        MutableOrder cancelledOrder = new MutableOrder().clOrdId(804).symbol("SYM4").quantity(1).price(1);

        // Add all orders to the manager
        mgr.newOrderRequest(createdOrder, () -> {});
        mgr.newOrderRequest(newOrder, () -> {});
        mgr.newOrderRequest(filledOrder, () -> {});
        mgr.newOrderRequest(cancelledOrder, () -> {});

        // Set different statuses
        mgr.orderAccepted(newOrder); // NEW status
        mgr.orderFilled(filledOrder, 1.0, 1.0, "exec-1"); // FILLED status
        mgr.orderCancelled(cancelledOrder); // CANCELLED status

        // Get live orders - should only include CREATED and NEW
        List<MutableOrder> liveOrders = mgr.getLiveOrders();

        assertEquals(2, liveOrders.size());
        assertTrue(liveOrders.stream().anyMatch(o -> o.clOrdId() == 801)); // CREATED
        assertTrue(liveOrders.stream().anyMatch(o -> o.clOrdId() == 802)); // NEW
        assertFalse(liveOrders.stream().anyMatch(o -> o.clOrdId() == 803)); // FILLED - not live
        assertFalse(liveOrders.stream().anyMatch(o -> o.clOrdId() == 804)); // CANCELLED - not live
    }

    @Test
    void getCompletedOrders_returnsOnlyCompletedStatusOrders() {
        List<OrderEvent> events = new ArrayList<>();
        VenueOrderStateManager mgr = new VenueOrderStateManager(events::add, (java.util.function.LongSupplier) new AtomicLong(900)::incrementAndGet);

        // Create orders with different statuses
        MutableOrder newOrder = new MutableOrder().clOrdId(901).symbol("SYM1").quantity(1).price(1);
        MutableOrder filledOrder = new MutableOrder().clOrdId(902).symbol("SYM2").quantity(1).price(1);
        MutableOrder cancelledOrder = new MutableOrder().clOrdId(903).symbol("SYM3").quantity(1).price(1);
        MutableOrder rejectedOrder = new MutableOrder().clOrdId(904).symbol("SYM4").quantity(1).price(1);

        // Add all orders to the manager
        mgr.newOrderRequest(newOrder, () -> {});
        mgr.newOrderRequest(filledOrder, () -> {});
        mgr.newOrderRequest(cancelledOrder, () -> {});
        mgr.newOrderRequest(rejectedOrder, () -> {});

        // Set different statuses
        mgr.orderAccepted(newOrder); // NEW status - live
        mgr.orderFilled(filledOrder, 1.0, 1.0, "exec-1"); // FILLED status - completed
        mgr.orderCancelled(cancelledOrder); // CANCELLED status - completed
        mgr.orderRejected(rejectedOrder); // REJECTED status - completed

        // Get completed orders
        List<MutableOrder> completedOrders = mgr.getCompletedOrders();

        assertEquals(3, completedOrders.size());
        assertFalse(completedOrders.stream().anyMatch(o -> o.clOrdId() == 901)); // NEW - not completed
        assertTrue(completedOrders.stream().anyMatch(o -> o.clOrdId() == 902)); // FILLED
        assertTrue(completedOrders.stream().anyMatch(o -> o.clOrdId() == 903)); // CANCELLED
        assertTrue(completedOrders.stream().anyMatch(o -> o.clOrdId() == 904)); // REJECTED
    }

    @Test
    void clearCompletedOrders_removesCompletedOrdersFromMaps() {
        List<OrderEvent> events = new ArrayList<>();
        VenueOrderStateManager mgr = new VenueOrderStateManager(events::add, (java.util.function.LongSupplier) new AtomicLong(1000)::incrementAndGet);

        // Create orders
        MutableOrder liveOrder = new MutableOrder().clOrdId(1001).symbol("LIVE").quantity(1).price(1);
        MutableOrder filledOrder = new MutableOrder().clOrdId(1002).symbol("FILLED").quantity(1).price(1);
        MutableOrder cancelledOrder = new MutableOrder().clOrdId(1003).symbol("CANCELLED").quantity(1).price(1);

        // Add all orders
        mgr.newOrderRequest(liveOrder, () -> {});
        mgr.newOrderRequest(filledOrder, () -> {});
        mgr.newOrderRequest(cancelledOrder, () -> {});

        // Set statuses
        mgr.orderAccepted(liveOrder); // NEW - live
        mgr.orderFilled(filledOrder, 1.0, 1.0, "exec-1"); // FILLED - completed
        mgr.orderCancelled(cancelledOrder); // CANCELLED - completed

        // Verify all orders are in maps before clearing
        assertEquals(3, mgr.getClOrderIdToOrderMap().size());
        assertTrue(mgr.getClOrderIdToOrderMap().containsKey(1001L));
        assertTrue(mgr.getClOrderIdToOrderMap().containsKey(1002L));
        assertTrue(mgr.getClOrderIdToOrderMap().containsKey(1003L));

        // Clear completed orders
        int clearedCount = mgr.clearCompletedOrders();

        // Verify results
        assertEquals(2, clearedCount);
        assertEquals(1, mgr.getClOrderIdToOrderMap().size());
        assertTrue(mgr.getClOrderIdToOrderMap().containsKey(1001L)); // Live order remains
        assertFalse(mgr.getClOrderIdToOrderMap().containsKey(1002L)); // Filled order removed
        assertFalse(mgr.getClOrderIdToOrderMap().containsKey(1003L)); // Cancelled order removed
    }

    @Test
    void clearCompletedOrders_returnsZeroWhenNoCompletedOrders() {
        List<OrderEvent> events = new ArrayList<>();
        VenueOrderStateManager mgr = new VenueOrderStateManager(events::add, (java.util.function.LongSupplier) new AtomicLong(1100)::incrementAndGet);

        // Create only live orders
        MutableOrder order1 = new MutableOrder().clOrdId(1101).symbol("SYM1").quantity(1).price(1);
        MutableOrder order2 = new MutableOrder().clOrdId(1102).symbol("SYM2").quantity(1).price(1);

        mgr.newOrderRequest(order1, () -> {});
        mgr.newOrderRequest(order2, () -> {});

        // Accept orders (NEW status - still live)
        mgr.orderAccepted(order1);
        mgr.orderAccepted(order2);

        // Clear completed orders - should return 0
        int clearedCount = mgr.clearCompletedOrders();

        assertEquals(0, clearedCount);
        assertEquals(2, mgr.getClOrderIdToOrderMap().size());
    }

    @Test
    void clearCompletedOrders_removesFromBothMaps() {
        List<OrderEvent> events = new ArrayList<>();
        VenueOrderStateManager mgr = new VenueOrderStateManager(events::add, (java.util.function.LongSupplier) new AtomicLong(1200)::incrementAndGet);

        // Create and fill an order
        MutableOrder order = new MutableOrder().clOrdId(1201).currentClOrdId(1201).symbol("SYM").quantity(1).price(1);
        mgr.newOrderRequest(order, () -> {});
        mgr.orderFilled(order, 1.0, 1.0, "exec-1");

        // Verify order is in both maps
        assertTrue(mgr.getClOrderIdToOrderMap().containsKey(1201L));
        assertTrue(mgr.getRequestIdToOrderMap().containsKey(1201L));

        // Clear completed orders
        mgr.clearCompletedOrders();

        // Verify removed from both maps
        assertFalse(mgr.getClOrderIdToOrderMap().containsKey(1201L));
        assertFalse(mgr.getRequestIdToOrderMap().containsKey(1201L));
    }
}
