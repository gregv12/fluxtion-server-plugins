package com.fluxtion.server.plugin.trading.service.booking;

import com.fluxtion.server.plugin.trading.service.common.Direction;

public interface TradeBooker {
    void bookTrade(String tradeId,
                   long clientId,
                   String symbol,
                   Direction side,
                   String currency,
                   double quantity,
                   double price,
                   double amount) throws Exception;
}
