/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl.talos;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TradeDetails {

    @JsonAlias("Symbol")
    private String symbol;
    @JsonAlias("TradeID")
    private String TradeID;
    @JsonAlias("Timestamp")
    private String Timestamp;
    @JsonAlias("Side")
    private String Side;
    @JsonAlias("TransactTime")
    private String TransactTime;
    @JsonAlias("Market")
    private String Market;
    @JsonAlias("Price")
    private String Price;
    @JsonAlias("Quantity")
    private String Quantity;
    @JsonAlias("Amount")
    private String Amount;
    @JsonAlias("Fee")
    private String Fee;
    @JsonAlias("FeeCurrency")
    private String FeeCurrency;
    @JsonAlias("MarketTradeID")
    private String MarketTradeID;
    @JsonAlias("TradeStatus")
    private String TradeStatus;
    @JsonAlias("AggressorSide")
    private String AggressorSide;
    @JsonAlias("OID")
    private String OID;
    @JsonAlias("EID")
    private String EID;
    @JsonAlias("AmountCurrency")
    private String AmountCurrency;
    @JsonAlias("PriceAllIn")
    private String PriceAllIn;
    @JsonAlias("TradeSource")
    private String TradeSource;
    @JsonAlias("Revision")
    private String Revision;
    @JsonAlias("MarketAccount")
    private String MarketAccount;
}
