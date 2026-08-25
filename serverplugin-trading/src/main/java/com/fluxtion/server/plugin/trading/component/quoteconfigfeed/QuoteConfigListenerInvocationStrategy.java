package com.fluxtion.server.plugin.trading.component.quoteconfigfeed;

import com.fluxtion.runtime.StaticEventProcessor;
import com.fluxtion.server.dispatch.AbstractEventToInvocationStrategy;
import com.fluxtion.server.plugin.trading.service.quotestream.BucketConfigEvent;
import com.fluxtion.server.plugin.trading.service.quotestream.PricingConfigEvent;
import com.fluxtion.server.plugin.trading.service.quotestream.QuoteConfigEvent;
import com.fluxtion.server.plugin.trading.service.quotestream.QuoteConfigListener;

/**
 * Maps the sealed {@link QuoteConfigEvent} union onto the typed {@link QuoteConfigListener} callbacks.
 * Exhaustive switch (no {@code default}) — a new config-event variant is a compile error here.
 */
public class QuoteConfigListenerInvocationStrategy extends AbstractEventToInvocationStrategy {

    @Override
    protected void dispatchEvent(Object event, StaticEventProcessor eventProcessor) {
        QuoteConfigListener listener = (QuoteConfigListener) eventProcessor;
        QuoteConfigEvent configEvent = (QuoteConfigEvent) event;
        switch (configEvent) {
            case PricingConfigEvent e -> listener.onPricingConfig(e);
            case BucketConfigEvent e -> listener.onBucketConfig(e);
        }
    }

    @Override
    protected boolean isValidTarget(StaticEventProcessor eventProcessor) {
        return eventProcessor.exportsService(QuoteConfigListener.class);
    }
}