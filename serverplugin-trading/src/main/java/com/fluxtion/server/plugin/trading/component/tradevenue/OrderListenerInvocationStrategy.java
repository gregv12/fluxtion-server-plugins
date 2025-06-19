package com.fluxtion.server.plugin.trading.component.tradevenue;

import com.fluxtion.runtime.StaticEventProcessor;
import com.fluxtion.server.dispatch.AbstractEventToInvocationStrategy;
import com.fluxtion.server.plugin.trading.service.order.*;

public class OrderListenerInvocationStrategy extends AbstractEventToInvocationStrategy {

    @Override
    protected void dispatchEvent(Object event, StaticEventProcessor eventProcessor) {
        OrderListener orderListener = (OrderListener) eventProcessor;
        OrderEvent orderEvent = (OrderEvent) event;
        dispatch(orderEvent, orderListener);
    }

    public static void dispatch(OrderEvent orderEvent, OrderListener orderListener) {
        switch (orderEvent) {
            //order updates
            case OrderUpdateEvent orderUpdateEvent -> orderListener.orderUpdate(orderUpdateEvent.order());
            case OrderFilledEvent orderFilledEvent -> orderListener.orderFilled(orderFilledEvent);
            case OrderFillEvent orderFillEvent -> orderListener.orderFill(orderFillEvent);
            //end states
            case OrderRejectEvent orderRejectEvent -> orderListener.orderRejected(orderRejectEvent.order());
            case OrderCancelEvent orderCancelEvent -> orderListener.orderCancelled(orderCancelEvent.order());
            case OrderDoneForDayEvent orderDoneForDayEvent ->
                    orderListener.orderDoneForDay(orderDoneForDayEvent.order());
            //modify updates
            case OrderCancelRejectEvent orderCancelRejectEvent ->
                    orderListener.cancelRejected(orderCancelRejectEvent.order());
            //venue updates
            case OrderVenueConnectedEvent orderVenueConnectedEvent ->
                    orderListener.orderVenueConnected(orderVenueConnectedEvent);
            case OrderVenueDisconnectedEvent orderVenueDisconnectedEvent ->
                    orderListener.orderVenueDisconnected(orderVenueDisconnectedEvent);

        }
    }

    @Override
    protected boolean isValidTarget(StaticEventProcessor eventProcessor) {
        return eventProcessor.exportsService(OrderListener.class);
    }
}
