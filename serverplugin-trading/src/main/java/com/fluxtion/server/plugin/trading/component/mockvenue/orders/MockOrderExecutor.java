package com.fluxtion.server.plugin.trading.component.mockvenue.orders;

import com.fluxtion.agrona.concurrent.OneToOneConcurrentArrayQueue;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.server.plugin.trading.component.tradevenue.AbstractOrderExecutorWorker;
import com.fluxtion.server.plugin.trading.component.tradevenue.VenueOrderStateManager;
import com.fluxtion.server.plugin.trading.service.common.Direction;
import com.fluxtion.server.plugin.trading.service.common.OrderType;
import com.fluxtion.server.plugin.trading.service.order.Order;
import com.fluxtion.server.plugin.trading.service.order.OrderStatus;
import com.fluxtion.server.plugin.trading.service.order.OrderUpdateEvent;
import com.fluxtion.server.plugin.trading.service.order.OrderVenueConnectedEvent;
import com.fluxtion.server.plugin.trading.service.order.impl.MutableOrder;
import com.fluxtion.server.service.admin.AdminCommandRegistry;
import lombok.extern.log4j.Log4j2;
import org.agrona.collections.Long2ObjectHashMap;
import quickfix.field.*;
import quickfix.fix44.OrderCancelReplaceRequest;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Log4j2
public class MockOrderExecutor extends AbstractOrderExecutorWorker {

    private final Long2ObjectHashMap<MutableOrder> modifyMap = new Long2ObjectHashMap<>();
    private final OneToOneConcurrentArrayQueue<Runnable> actionQueue = new OneToOneConcurrentArrayQueue<>(512);
    private volatile boolean connectedSent = false;

    public MockOrderExecutor(String roleName) {
        setRoleName(roleName);
        setFeedName(roleName);
    }

    public MockOrderExecutor() {
        setRoleName("mockOrderExecutor");
    }

    @ServiceRegistered
    public void adminClient(AdminCommandRegistry adminCommandRegistry) {
        log.info("admin registered:{}", adminCommandRegistry);
        adminCommandRegistry.registerCommand("mockExecutor.acceptOrder", this::acceptOrder);
        adminCommandRegistry.registerCommand("mockExecutor.acceptAllOrders", this::acceptAllOrders);
        adminCommandRegistry.registerCommand("mockExecutor.acceptAllModifies", this::acceptAllModifies);
        adminCommandRegistry.registerCommand("mockExecutor.executeOrder", this::executeOrder);
        adminCommandRegistry.registerCommand("mockExecutor.executeAllOrders", this::executeAllOrders);
        adminCommandRegistry.registerCommand("mockExecutor.rejectOrder", this::rejectOrder);
        adminCommandRegistry.registerCommand("mockExecutor.currentOrders", this::currentOrders);
        adminCommandRegistry.registerCommand("mockExecutor.pendingModifies", this::pendingModifies);
    }

    @Override
    public void onStart() {
        super.onStart();
        log.info("onStart - send OrderVenueConnectedEvent:{}", getFeedName());
        actionQueue.add(() -> publish(new OrderVenueConnectedEvent(getFeedName())));
    }

    @Override
    public void startComplete() {
        super.startComplete();
        log.info("startComplete - send OrderVenueConnectedEvent:{}", getFeedName());
        actionQueue.add(() -> publish(new OrderVenueConnectedEvent(getFeedName())));
    }

    @Override
    public Order createOrder(String venue, String book, Direction direction, String symbol, double quantity, double price) {
        log.info("createOrder {} {} {}@{}", symbol, direction, quantity, price);

        MutableOrder mutableOrder = new MutableOrder()
                .bookName(book)
                .clOrdId(VenueOrderStateManager.nextClOrderId())
                .symbol(symbol)
                .direction(direction)
                .quantity(quantity)
                .price(price)
                .orderType(Double.isNaN(price) ? OrderType.MARKET : OrderType.LIMIT)
                .leavesQuantity(quantity);

        orderStateManager.newOrderRequest(mutableOrder, () -> log.info("new order {}", mutableOrder));
        return mutableOrder;
    }

    @Override
    public void modifyOrder(long orderId, double price, double quantity, long nextClOrderId) {
        MutableOrder order = getOrderByOriginalClOrderId(orderId);
        log.info("modify newPrice:{} newQuantity:{} order:{}", price, quantity, order);

        nextClOrderId = nextClOrderId < 1 ? VenueOrderStateManager.nextClOrderId() : nextClOrderId;

        OrderCancelReplaceRequest orderCancelReplaceRequest = new OrderCancelReplaceRequest(
                new OrigClOrdID(String.valueOf(order.currentClOrdId())),
                new ClOrdID("" + nextClOrderId),
                new Side(order.direction() == Direction.BUY ? Side.BUY : Side.SELL),
                new TransactTime(),
                new OrdType(OrdType.LIMIT));

        orderCancelReplaceRequest.set(new Symbol(order.symbol()));
        orderCancelReplaceRequest.set(new OrderQty(quantity));
        orderCancelReplaceRequest.set(new Price(price));

//        order.currentClOrdId(nextClOrderId);

        MutableOrder modifiedOrder = order.copy()
                .currentClOrdId(nextClOrderId)
                .price(price)
                .quantity(quantity)
                .leavesQuantity(quantity - order.filledQuantity());
        modifyMap.put(order.clOrdId(), modifiedOrder);

        //time in force and expire time to add
        orderStateManager.modifyOrderRequest(order, nextClOrderId, () -> log.info("modify order request {}", orderCancelReplaceRequest));
    }

    @Override
    public long cancelOrder(long orderId, long nextClOrdId) {
        log.info("cancelOrder orderId:{}, nextClOrderId:{}", orderId, nextClOrdId);
        return 0;
    }

    @Override
    public int doWork() throws Exception {
        int executed = actionQueue.drain(Runnable::run);
        return executed;
    }

    private void acceptAllOrders(List<String> args, Consumer<String> out, Consumer<String> err) {
        log.info("acceptOrder all orders");

        orderStateManager.getClOrderIdToOrderMap().values().stream()
                .filter(mutableOrder -> mutableOrder.orderStatus() == OrderStatus.CREATED)
                .forEach(orderStateManager::orderAccepted);
    }

    private void acceptOrder(List<String> args, Consumer<String> out, Consumer<String> err) {
        log.info("acceptOrder");
        MutableOrder mutableOrder = getOrder(args, out);
        orderStateManager.orderAccepted(mutableOrder);
    }

    private void executeOrder(List<String> args, Consumer<String> out, Consumer<String> err) {
        log.info("executeOrder");
        MutableOrder order = getOrder(args, out);
        if (order != null) {
            final UUID uuid = UUID.randomUUID();
            order.leavesQuantity(0);
            order.filledQuantity(order.quantity());
            order.cancelledQuantity(0);
            orderStateManager.orderFilled(order, order.price(), order.quantity(), uuid.toString());
        } else {
            err.accept("no order found with args:" + args);
        }
    }

    private void executeAllOrders(List<String> args, Consumer<String> out, Consumer<String> err) {
        acceptAllOrders(args, out, err);
        log.info("executeAllOrders");

        orderStateManager.getClOrderIdToOrderMap().values().stream()
                .filter(mutableOrder -> mutableOrder.orderStatus() == OrderStatus.NEW)
                .forEach(order -> {
                    final UUID uuid = UUID.randomUUID();
                    order.leavesQuantity(0);
                    order.filledQuantity(order.quantity());
                    order.cancelledQuantity(0);
                    orderStateManager.orderFilled(order, order.price(), order.quantity(), uuid.toString());
                });
    }

    private void acceptAllModifies(List<String> args, Consumer<String> out, Consumer<String> err) {
        modifyMap.values().forEach(modifyOrder -> {
            MutableOrder originalOrder = getOrderByCurrentClOrderId(modifyOrder.clOrdId());
            originalOrder.currentClOrdId(modifyOrder.currentClOrdId());
            originalOrder.leavesQuantity(modifyOrder.leavesQuantity());
            orderStateManager.orderReplaced(originalOrder, modifyOrder.price(), modifyOrder.quantity());
        });
        modifyMap.clear();
    }


    private void rejectOrder(List<String> args, Consumer<String> out, Consumer<String> err) {
        log.info("rejectOrder");
        MutableOrder mutableOrder = getOrder(args, out);
        if (mutableOrder != null) {
            actionQueue.add(() -> publish(new OrderUpdateEvent(mutableOrder.orderStatus(OrderStatus.REJECTED))));
        }
    }

    private MutableOrder getOrder(List<String> args, Consumer<String> out) {
        long clOrderId = Long.parseLong(args.get(1));
        MutableOrder mutableOrder = getOrderByCurrentClOrderId(clOrderId);
        if (mutableOrder == null) {
            out.accept("no order found for id:" + clOrderId);
        }
        return mutableOrder;
    }

    protected void currentOrders(List<String> strings, Consumer<String> out, Consumer<String> err) {
        out.accept(orderStateManager.getRequestIdToOrderMap().values().stream()
                .map(Objects::toString)
                .collect(Collectors.joining("\n", getFeedName() + " current orders:\n", "\n----")));
    }

    protected void pendingModifies(List<String> strings, Consumer<String> out, Consumer<String> err) {
        out.accept(modifyMap.values().stream()
                .map(Objects::toString)
                .collect(Collectors.joining("\n", getFeedName() + " pending order modifies:\n", "\n----")));
    }
}
