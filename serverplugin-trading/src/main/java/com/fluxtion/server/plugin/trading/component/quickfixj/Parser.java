package com.fluxtion.server.plugin.trading.component.quickfixj;

import quickfix.FieldNotFound;
import quickfix.Group;
import quickfix.Message;
import quickfix.field.*;

public class Parser {
    public static String msgType(final Message message) throws FieldNotFound {
        return message.getHeader().getString(MsgType.FIELD);
    }

    public static String symbol(final Group group) throws Exception {
        return group.getString(Symbol.FIELD);
    }

    public static char mdEntryType(final Group group) throws Exception {
        return group.getChar(MDEntryType.FIELD);
    }

    public static double mdEntryPx(final Group group) throws Exception {
        return group.getDouble(MDEntryPx.FIELD);
    }

    public static char mdEntryUpdateAction(final Group group) throws Exception {
        return group.getChar(MDUpdateAction.FIELD);
    }

    public static String symbol(final Message message) throws Exception {
        return message.getString(Symbol.FIELD);
    }

    public static char side(final Message message) throws Exception {
        return message.getChar(Side.FIELD);
    }

    public static String clOrdId(final Message message) throws Exception {
        return message.getString(ClOrdID.FIELD);
    }

    public static String origClOrdId(final Message message) throws Exception {
        return message.getString(OrigClOrdID.FIELD);
    }

    public static String orderId(final Message message) throws Exception {
        return message.getString(OrderID.FIELD);
    }

    public static char ordStatus(final Message message) throws Exception {
        return message.getChar(OrdStatus.FIELD);
    }

    public static char execType(final Message message) throws Exception {
        return message.getChar(ExecType.FIELD);
    }

    public static String execId(final Message message) throws Exception {
        return message.getString(ExecID.FIELD);
    }

    public static double price(final Message message) throws Exception {
        return message.getDouble(Price.FIELD);
    }

    public static double orderQty(final Message message) throws Exception {
        return message.getDouble(OrderQty.FIELD);
    }

    public static double cumQty(final Message message) throws Exception {
        return message.getDouble(CumQty.FIELD);
    }

    public static double leavesQty(final Message message) throws Exception {
        return message.getDouble(LeavesQty.FIELD);
    }

    public static double lastQty(final Message message) throws Exception {
        return message.getDouble(LastQty.FIELD);
    }

    public static double lastPrice(final Message message) throws Exception {
        return message.getDouble(LastPx.FIELD);
    }

    public static String businessRejectId(final Message message) throws Exception {
        return message.getString(BusinessRejectRefID.FIELD);
    }

    public static int cxlRejResponseTo(final Message message) throws Exception {
        return message.getInt(CxlRejResponseTo.FIELD);
    }

    public static String text(final Message message) throws Exception {
        return message.getString(Text.FIELD);
    }
}
