package com.fluxtion.server.plugin.trading.component.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketFeedEvent;
import lombok.extern.log4j.Log4j2;

/**
 * Concrete {@link AbstractWsMarketDataFeed} for the {@link WsMarketDataMessage} JSON schema (the
 * schema the mock WS venue speaks). This is the "extend per exchange" example: a real venue feed
 * overrides {@link #subscribeFrame} and {@link #onMessage} with that venue's wire format.
 */
@Log4j2
public class GenericJsonWsMarketDataFeed extends AbstractWsMarketDataFeed {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected String subscribeFrame(String feedName, String venueName, String symbol) {
        try {
            return objectMapper.writeValueAsString(WsMarketDataMessage.subscribe(symbol));
        } catch (Exception e) {
            log.warn("failed to build subscribe frame for {}: {}", symbol, e.toString());
            return "{\"type\":\"subscribe\",\"symbol\":\"" + symbol + "\"}";
        }
    }

    @Override
    protected String unsubscribeFrame(String feedName, String venueName, String symbol) {
        try {
            return objectMapper.writeValueAsString(WsMarketDataMessage.unsubscribe(symbol));
        } catch (Exception e) {
            log.warn("failed to build unsubscribe frame for {}: {}", symbol, e.toString());
            return "{\"type\":\"unsubscribe\",\"symbol\":\"" + symbol + "\"}";
        }
    }

    @Override
    protected void onMessage(String rawJson) {
        try {
            MarketFeedEvent event = objectMapper.readValue(rawJson, WsMarketDataMessage.class).toEvent();
            if (event != null) {
                publish(event);
            }
        } catch (Exception e) {
            log.warn("{} unparseable message: {} ({})", getFeedName(), rawJson, e.toString());
        }
    }
}
