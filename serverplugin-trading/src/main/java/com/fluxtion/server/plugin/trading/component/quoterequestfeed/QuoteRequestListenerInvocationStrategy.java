package com.fluxtion.server.plugin.trading.component.quoterequestfeed;

import com.fluxtion.runtime.StaticEventProcessor;
import com.fluxtion.server.dispatch.AbstractEventToInvocationStrategy;
import com.fluxtion.server.plugin.trading.service.quotestream.LiveQuoteNoData;
import com.fluxtion.server.plugin.trading.service.quotestream.LiveQuoteSnapshot;
import com.fluxtion.server.plugin.trading.service.quotestream.QuoteRequestEvent;
import com.fluxtion.server.plugin.trading.service.quotestream.QuoteRequestListener;

/**
 * Maps the sealed {@link QuoteRequestEvent} union onto the typed {@link QuoteRequestListener}
 * callbacks — the twin of {@code MarketListenerInvocationStrategy}. The exhaustive switch (no
 * {@code default}) means a new event variant is a compile error here, not a silent drop.
 */
public class QuoteRequestListenerInvocationStrategy extends AbstractEventToInvocationStrategy {

    @Override
    protected void dispatchEvent(Object event, StaticEventProcessor eventProcessor) {
        QuoteRequestListener listener = (QuoteRequestListener) eventProcessor;
        QuoteRequestEvent quoteRequestEvent = (QuoteRequestEvent) event;
        switch (quoteRequestEvent) {
            case LiveQuoteSnapshot snapshot -> listener.onSnapshot(snapshot);
            case LiveQuoteNoData noData -> listener.onNoData(noData);
        }
    }

    @Override
    protected boolean isValidTarget(StaticEventProcessor eventProcessor) {
        return eventProcessor.exportsService(QuoteRequestListener.class);
    }
}