package com.fluxtion.server.plugin.trading.service.balance;

public record BalanceEvent(String venueName, String accountName, double balance) {
}
