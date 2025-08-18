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

        assertFalse(mgr.getRequestIdToOrderMap().containsKey(order.clOrdId()));
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
}
