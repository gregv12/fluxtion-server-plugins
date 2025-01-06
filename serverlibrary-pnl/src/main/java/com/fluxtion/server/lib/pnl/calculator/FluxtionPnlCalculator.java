/*
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the Server Side Public License, version 1,
* as published by MongoDB, Inc.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* Server Side License for more details.
*
* You should have received a copy of the Server Side Public License
* along with this program.  If not, see
*
<http://www.mongodb.com/licensing/server-side-public-license>.
*/
package com.fluxtion.server.lib.pnl.calculator;

import com.fluxtion.runtime.StaticEventProcessor;
import com.fluxtion.runtime.lifecycle.BatchHandler;
import com.fluxtion.runtime.lifecycle.Lifecycle;
import com.fluxtion.runtime.EventProcessor;
import com.fluxtion.runtime.callback.InternalEventProcessor;
import com.fluxtion.runtime.EventProcessorContext;
import com.fluxtion.runtime.audit.Auditor;
import com.fluxtion.runtime.audit.EventLogControlEvent;
import com.fluxtion.runtime.audit.EventLogManager;
import com.fluxtion.runtime.audit.NodeNameAuditor;
import com.fluxtion.runtime.callback.CallBackNode;
import com.fluxtion.runtime.callback.CallbackDispatcherImpl;
import com.fluxtion.runtime.callback.ExportFunctionAuditEvent;
import com.fluxtion.runtime.callback.InstanceCallbackEvent.InstanceCallbackEvent_0;
import com.fluxtion.runtime.callback.InstanceCallbackEvent.InstanceCallbackEvent_1;
import com.fluxtion.runtime.callback.InstanceCallbackEvent.InstanceCallbackEvent_2;
import com.fluxtion.runtime.dataflow.aggregate.function.AggregateIdentityFlowFunction;
import com.fluxtion.runtime.dataflow.function.BinaryMapFlowFunction.BinaryMapToRefFlowFunction;
import com.fluxtion.runtime.dataflow.function.FilterFlowFunction;
import com.fluxtion.runtime.dataflow.function.FlatMapFlowFunction;
import com.fluxtion.runtime.dataflow.function.MapFlowFunction.MapRef2RefFlowFunction;
import com.fluxtion.runtime.dataflow.function.MergeFlowFunction;
import com.fluxtion.runtime.dataflow.function.PeekFlowFunction;
import com.fluxtion.runtime.dataflow.function.PushFlowFunction;
import com.fluxtion.runtime.dataflow.groupby.GroupBy;
import com.fluxtion.runtime.dataflow.groupby.GroupBy.EmptyGroupBy;
import com.fluxtion.runtime.dataflow.groupby.GroupByFlowFunctionWrapper;
import com.fluxtion.runtime.dataflow.groupby.GroupByMapFlowFunction;
import com.fluxtion.runtime.dataflow.groupby.LeftJoin;
import com.fluxtion.runtime.dataflow.groupby.OuterJoin;
import com.fluxtion.runtime.dataflow.helpers.DefaultValue;
import com.fluxtion.runtime.dataflow.helpers.Mappers;
import com.fluxtion.runtime.dataflow.helpers.Peekers.TemplateMessage;
import com.fluxtion.runtime.dataflow.helpers.Tuples.MapTuple;
import com.fluxtion.runtime.event.Event;
import com.fluxtion.runtime.event.NamedFeedEvent;
import com.fluxtion.runtime.event.Signal;
import com.fluxtion.runtime.input.EventFeed;
import com.fluxtion.runtime.input.SubscriptionManager;
import com.fluxtion.runtime.input.SubscriptionManagerNode;
import com.fluxtion.runtime.node.DefaultEventHandlerNode;
import com.fluxtion.runtime.node.ForkedTriggerTask;
import com.fluxtion.runtime.node.MutableEventProcessorContext;
import com.fluxtion.runtime.node.NamedFeedTableNode;
import com.fluxtion.runtime.output.SinkDeregister;
import com.fluxtion.runtime.output.SinkPublisher;
import com.fluxtion.runtime.output.SinkRegistration;
import com.fluxtion.runtime.service.ServiceListener;
import com.fluxtion.runtime.service.ServiceRegistryNode;
import com.fluxtion.runtime.time.Clock;
import com.fluxtion.runtime.time.ClockStrategy.ClockStrategyEvent;
import com.fluxtion.server.config.ConfigListener;
import com.fluxtion.server.lib.pnl.FeeInstrumentPosMtm;
import com.fluxtion.server.lib.pnl.InstrumentPosMtm;
import com.fluxtion.server.lib.pnl.InstrumentPosition;
import com.fluxtion.server.lib.pnl.MidPrice;
import com.fluxtion.server.lib.pnl.MidPriceBatch;
import com.fluxtion.server.lib.pnl.MtmInstrument;
import com.fluxtion.server.lib.pnl.NetMarkToMarket;
import com.fluxtion.server.lib.pnl.PositionSnapshot;
import com.fluxtion.server.lib.pnl.Trade;
import com.fluxtion.server.lib.pnl.TradeBatch;
import com.fluxtion.server.lib.pnl.refdata.Symbol;
import java.io.File;
import java.util.Arrays;
import java.util.Map;

import java.util.IdentityHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 *
 *
 * <pre>
 * generation time                 : Not available
 * eventProcessorGenerator version : 9.5.1
 * api version                     : 9.5.1
 * </pre>
 *
 * Event classes supported:
 *
 * <ul>
 *   <li>com.fluxtion.compiler.generation.model.ExportFunctionMarker
 *   <li>com.fluxtion.runtime.audit.EventLogControlEvent
 *   <li>com.fluxtion.runtime.callback.InstanceCallbackEvent.InstanceCallbackEvent_0
 *   <li>com.fluxtion.runtime.callback.InstanceCallbackEvent.InstanceCallbackEvent_1
 *   <li>com.fluxtion.runtime.callback.InstanceCallbackEvent.InstanceCallbackEvent_2
 *   <li>com.fluxtion.runtime.event.Signal
 *   <li>com.fluxtion.runtime.output.SinkDeregister
 *   <li>com.fluxtion.runtime.output.SinkRegistration
 *   <li>com.fluxtion.runtime.time.ClockStrategy.ClockStrategyEvent
 *   <li>com.fluxtion.server.lib.pnl.MidPrice
 *   <li>com.fluxtion.server.lib.pnl.MidPriceBatch
 *   <li>com.fluxtion.server.lib.pnl.MtmInstrument
 *   <li>com.fluxtion.server.lib.pnl.PositionSnapshot
 *   <li>com.fluxtion.server.lib.pnl.Trade
 *   <li>com.fluxtion.server.lib.pnl.TradeBatch
 *   <li>com.fluxtion.runtime.event.NamedFeedEvent
 * </ul>
 *
 * @author Greg Higgins
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class FluxtionPnlCalculator
    implements EventProcessor<FluxtionPnlCalculator>,
        /*--- @ExportService start ---*/
        ConfigListener,
        ServiceListener,
        /*--- @ExportService end ---*/
        StaticEventProcessor,
        InternalEventProcessor,
        BatchHandler,
        Lifecycle {

  //Node declarations
  private final InstanceCallbackEvent_0 callBackTriggerEvent_0 = new InstanceCallbackEvent_0();
  private final CallBackNode callBackNode_74 = new CallBackNode<>(callBackTriggerEvent_0);
  private final InstanceCallbackEvent_1 callBackTriggerEvent_1 = new InstanceCallbackEvent_1();
  private final CallBackNode callBackNode_98 = new CallBackNode<>(callBackTriggerEvent_1);
  private final InstanceCallbackEvent_2 callBackTriggerEvent_2 = new InstanceCallbackEvent_2();
  private final CallBackNode callBackNode_153 = new CallBackNode<>(callBackTriggerEvent_2);
  private final CallbackDispatcherImpl callbackDispatcher = new CallbackDispatcherImpl();
  public final Clock clock = new Clock();
  private final EmptyGroupBy emptyGroupBy_106 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_24 = new DefaultValue<>(emptyGroupBy_106);
  private final EmptyGroupBy emptyGroupBy_129 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_30 = new DefaultValue<>(emptyGroupBy_129);
  private final EmptyGroupBy emptyGroupBy_176 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_41 = new DefaultValue<>(emptyGroupBy_176);
  private final EmptyGroupBy emptyGroupBy_239 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_49 = new DefaultValue<>(emptyGroupBy_239);
  private final EmptyGroupBy emptyGroupBy_499 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_65 = new DefaultValue<>(emptyGroupBy_499);
  public final EventLogManager eventLogger = new EventLogManager();
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_9 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, SingleInstrumentPosMtmAggregate::dealt);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_11 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getContraInstrument, Mappers::identity, SingleInstrumentPosMtmAggregate::contra);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_13 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, InstrumentPosMtmAggregate::dealt);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_15 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getContraInstrument, Mappers::identity, InstrumentPosMtmAggregate::contra);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_18 =
      new GroupByFlowFunctionWrapper<>(
          InstrumentPosition::instrument, Mappers::identity, AggregateIdentityFlowFunction::new);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_22 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, FeeInstrumentPosMtmAggregate::new);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_35 =
      new GroupByFlowFunctionWrapper<>(
          InstrumentPosition::instrument, Mappers::identity, AggregateIdentityFlowFunction::new);
  private final LeftJoin leftJoin_51 = new LeftJoin();
  private final LeftJoin leftJoin_67 = new LeftJoin();
  private final MapTuple mapTuple_431 = new MapTuple<>(NetMarkToMarket::combine);
  private final GroupByMapFlowFunction groupByMapFlowFunction_53 =
      new GroupByMapFlowFunction(mapTuple_431::mapTuple);
  private final MapTuple mapTuple_449 = new MapTuple<>(InstrumentPosMtm::addSnapshot);
  private final GroupByMapFlowFunction groupByMapFlowFunction_45 =
      new GroupByMapFlowFunction(mapTuple_449::mapTuple);
  private final MapTuple mapTuple_454 = new MapTuple<>(InstrumentPosMtm::merge);
  private final GroupByMapFlowFunction groupByMapFlowFunction_39 =
      new GroupByMapFlowFunction(mapTuple_454::mapTuple);
  private final MapTuple mapTuple_601 = new MapTuple<>(NetMarkToMarket::combine);
  private final GroupByMapFlowFunction groupByMapFlowFunction_69 =
      new GroupByMapFlowFunction(mapTuple_601::mapTuple);
  private final MapTuple mapTuple_607 = new MapTuple<>(FeeInstrumentPosMtm::addSnapshot);
  private final GroupByMapFlowFunction groupByMapFlowFunction_28 =
      new GroupByMapFlowFunction(mapTuple_607::mapTuple);
  private final MapTuple mapTuple_619 = new MapTuple<>(InstrumentPosMtm::merge);
  private final GroupByMapFlowFunction groupByMapFlowFunction_61 =
      new GroupByMapFlowFunction(mapTuple_619::mapTuple);
  private final NamedFeedTableNode namedFeedTableNode_73 =
      new NamedFeedTableNode<>("symbolFeed", Symbol::symbolName);
  public final DerivedRateNode derivedRateNode = new DerivedRateNode(namedFeedTableNode_73);
  public final EventFeedConnector eventFeedBatcher = new EventFeedConnector(namedFeedTableNode_73);
  private final GroupByMapFlowFunction groupByMapFlowFunction_32 =
      new GroupByMapFlowFunction(derivedRateNode::calculateFeeMtm);
  private final GroupByMapFlowFunction groupByMapFlowFunction_47 =
      new GroupByMapFlowFunction(derivedRateNode::calculateInstrumentPosMtm);
  private final GroupByMapFlowFunction groupByMapFlowFunction_63 =
      new GroupByMapFlowFunction(derivedRateNode::calculateInstrumentPosMtm);
  public final NodeNameAuditor nodeNameLookup = new NodeNameAuditor();
  private final OuterJoin outerJoin_26 = new OuterJoin();
  private final OuterJoin outerJoin_37 = new OuterJoin();
  private final OuterJoin outerJoin_43 = new OuterJoin();
  private final OuterJoin outerJoin_59 = new OuterJoin();
  public final PositionCache positionCache = new PositionCache();
  private final SubscriptionManagerNode subscriptionManager = new SubscriptionManagerNode();
  private final MutableEventProcessorContext context =
      new MutableEventProcessorContext(
          nodeNameLookup, callbackDispatcher, subscriptionManager, callbackDispatcher);
  private final SinkPublisher globalNetMtmListener = new SinkPublisher<>("globalNetMtmListener");
  private final DefaultEventHandlerNode handlerPositionSnapshot =
      new DefaultEventHandlerNode<>(
          2147483647,
          "",
          com.fluxtion.server.lib.pnl.PositionSnapshot.class,
          "handlerPositionSnapshot",
          context);
  private final FlatMapFlowFunction flatMapFlowFunction_17 =
      new FlatMapFlowFunction<>(handlerPositionSnapshot, PositionSnapshot::getFeePositions);
  public final FlatMapFlowFunction flatMapSnapshotPositions =
      new FlatMapFlowFunction<>(handlerPositionSnapshot, PositionSnapshot::getPositions);
  private final DefaultEventHandlerNode handlerSignal_positionSnapshotReset =
      new DefaultEventHandlerNode<>(
          2147483647,
          "positionSnapshotReset",
          com.fluxtion.runtime.event.Signal.class,
          "handlerSignal_positionSnapshotReset",
          context);
  private final DefaultEventHandlerNode handlerSignal_positionUpdate =
      new DefaultEventHandlerNode<>(
          2147483647,
          "positionUpdate",
          com.fluxtion.runtime.event.Signal.class,
          "handlerSignal_positionUpdate",
          context);
  public final MapRef2RefFlowFunction feeSnapshot =
      new MapRef2RefFlowFunction<>(
          flatMapFlowFunction_17, groupByFlowFunctionWrapper_18::aggregate);
  public final MapRef2RefFlowFunction groupBySnapshotPositions =
      new MapRef2RefFlowFunction<>(
          flatMapSnapshotPositions, groupByFlowFunctionWrapper_35::aggregate);
  private final DefaultEventHandlerNode handlerTrade =
      new DefaultEventHandlerNode<>(
          2147483647, "", com.fluxtion.server.lib.pnl.Trade.class, "handlerTrade", context);
  private final DefaultEventHandlerNode handlerTradeBatch =
      new DefaultEventHandlerNode<>(
          2147483647,
          "",
          com.fluxtion.server.lib.pnl.TradeBatch.class,
          "handlerTradeBatch",
          context);
  private final FlatMapFlowFunction flatMapFlowFunction_3 =
      new FlatMapFlowFunction<>(handlerTradeBatch, TradeBatch::getTrades);
  private final SinkPublisher instrumentNetMtmListener =
      new SinkPublisher<>("instrumentNetMtmListener");
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_4 =
      new MapRef2RefFlowFunction<>(flatMapFlowFunction_3, eventFeedBatcher::validateBatchTrade);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_5 =
      new MapRef2RefFlowFunction<>(handlerTrade, eventFeedBatcher::validateTrade);
  private final MergeFlowFunction mergeFlowFunction_6 =
      new MergeFlowFunction<>(Arrays.asList(mapRef2RefFlowFunction_5, mapRef2RefFlowFunction_4));
  public final ServiceRegistryNode serviceRegistry = new ServiceRegistryNode();
  private final TemplateMessage templateMessage_20 = new TemplateMessage<>("feeSnapshot:{}");
  private final PeekFlowFunction peekFlowFunction_21 =
      new PeekFlowFunction<>(feeSnapshot, templateMessage_20::templateAndLogToConsole);
  private final TradeSequenceFilter tradeSequenceFilter_7 = new TradeSequenceFilter();
  private final FilterFlowFunction filterFlowFunction_8 =
      new FilterFlowFunction<>(
          mergeFlowFunction_6, tradeSequenceFilter_7::checkTradeSequenceNumber);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_10 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_8, groupByFlowFunctionWrapper_9::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_12 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_8, groupByFlowFunctionWrapper_11::aggregate);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_38 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_10, mapRef2RefFlowFunction_12, outerJoin_37::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_14 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_8, groupByFlowFunctionWrapper_13::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_16 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_8, groupByFlowFunctionWrapper_15::aggregate);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_60 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_14, mapRef2RefFlowFunction_16, outerJoin_59::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_23 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_8, groupByFlowFunctionWrapper_22::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_25 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_23, defaultValue_24::getOrDefault);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_27 =
      new BinaryMapToRefFlowFunction<>(mapRef2RefFlowFunction_25, feeSnapshot, outerJoin_26::join);
  public final MapRef2RefFlowFunction joinFeeSnapshot =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_27, groupByMapFlowFunction_28::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_31 =
      new MapRef2RefFlowFunction<>(joinFeeSnapshot, defaultValue_30::getOrDefault);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_33 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_31, groupByMapFlowFunction_32::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_40 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_38, groupByMapFlowFunction_39::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_42 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_40, defaultValue_41::getOrDefault);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_44 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_42, groupBySnapshotPositions, outerJoin_43::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_46 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_44, groupByMapFlowFunction_45::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_48 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_46, groupByMapFlowFunction_47::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_50 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_48, defaultValue_49::getOrDefault);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_52 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_50, mapRef2RefFlowFunction_33, leftJoin_51::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_54 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_52, groupByMapFlowFunction_53::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_55 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_54, GroupBy<Object, Object>::toMap);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_56 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_55, NetMarkToMarket::markToMarketSum);
  public final PushFlowFunction globalNetMtm =
      new PushFlowFunction<>(mapRef2RefFlowFunction_56, positionCache::mtmUpdated);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_62 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_60, groupByMapFlowFunction_61::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_64 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_62, groupByMapFlowFunction_63::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_66 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_64, defaultValue_65::getOrDefault);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_68 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_66, mapRef2RefFlowFunction_33, leftJoin_67::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_70 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_68, groupByMapFlowFunction_69::mapValues);
  public final MapRef2RefFlowFunction instrumentNetMtm =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_70, GroupBy<Object, Object>::toMap);
  private final PushFlowFunction pushFlowFunction_58 =
      new PushFlowFunction<>(globalNetMtm, globalNetMtmListener::publish);
  private final PushFlowFunction pushFlowFunction_72 =
      new PushFlowFunction<>(instrumentNetMtm, instrumentNetMtmListener::publish);
  private final ExportFunctionAuditEvent functionAudit = new ExportFunctionAuditEvent();
  //Dirty flags
  private boolean initCalled = false;
  private boolean processing = false;
  private boolean buffering = false;
  private final IdentityHashMap<Object, BooleanSupplier> dirtyFlagSupplierMap =
      new IdentityHashMap<>(52);
  private final IdentityHashMap<Object, Consumer<Boolean>> dirtyFlagUpdateMap =
      new IdentityHashMap<>(52);

  private boolean isDirty_binaryMapToRefFlowFunction_27 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_38 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_44 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_52 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_60 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_68 = false;
  private boolean isDirty_callBackNode_74 = false;
  private boolean isDirty_callBackNode_98 = false;
  private boolean isDirty_callBackNode_153 = false;
  private boolean isDirty_clock = false;
  private boolean isDirty_derivedRateNode = false;
  private boolean isDirty_eventFeedBatcher = false;
  private boolean isDirty_feeSnapshot = false;
  private boolean isDirty_filterFlowFunction_8 = false;
  private boolean isDirty_flatMapFlowFunction_3 = false;
  private boolean isDirty_flatMapFlowFunction_17 = false;
  private boolean isDirty_flatMapSnapshotPositions = false;
  private boolean isDirty_globalNetMtm = false;
  private boolean isDirty_groupBySnapshotPositions = false;
  private boolean isDirty_handlerPositionSnapshot = false;
  private boolean isDirty_handlerSignal_positionSnapshotReset = false;
  private boolean isDirty_handlerSignal_positionUpdate = false;
  private boolean isDirty_handlerTrade = false;
  private boolean isDirty_handlerTradeBatch = false;
  private boolean isDirty_instrumentNetMtm = false;
  private boolean isDirty_joinFeeSnapshot = false;
  private boolean isDirty_mapRef2RefFlowFunction_4 = false;
  private boolean isDirty_mapRef2RefFlowFunction_5 = false;
  private boolean isDirty_mapRef2RefFlowFunction_10 = false;
  private boolean isDirty_mapRef2RefFlowFunction_12 = false;
  private boolean isDirty_mapRef2RefFlowFunction_14 = false;
  private boolean isDirty_mapRef2RefFlowFunction_16 = false;
  private boolean isDirty_mapRef2RefFlowFunction_23 = false;
  private boolean isDirty_mapRef2RefFlowFunction_25 = false;
  private boolean isDirty_mapRef2RefFlowFunction_31 = false;
  private boolean isDirty_mapRef2RefFlowFunction_33 = false;
  private boolean isDirty_mapRef2RefFlowFunction_40 = false;
  private boolean isDirty_mapRef2RefFlowFunction_42 = false;
  private boolean isDirty_mapRef2RefFlowFunction_46 = false;
  private boolean isDirty_mapRef2RefFlowFunction_48 = false;
  private boolean isDirty_mapRef2RefFlowFunction_50 = false;
  private boolean isDirty_mapRef2RefFlowFunction_54 = false;
  private boolean isDirty_mapRef2RefFlowFunction_55 = false;
  private boolean isDirty_mapRef2RefFlowFunction_56 = false;
  private boolean isDirty_mapRef2RefFlowFunction_62 = false;
  private boolean isDirty_mapRef2RefFlowFunction_64 = false;
  private boolean isDirty_mapRef2RefFlowFunction_66 = false;
  private boolean isDirty_mapRef2RefFlowFunction_70 = false;
  private boolean isDirty_mergeFlowFunction_6 = false;
  private boolean isDirty_namedFeedTableNode_73 = false;
  private boolean isDirty_pushFlowFunction_58 = false;
  private boolean isDirty_pushFlowFunction_72 = false;

  //Forked declarations

  //Filter constants

  //unknown event handler
  private Consumer unKnownEventHandler = (e) -> {};

  public FluxtionPnlCalculator(Map<Object, Object> contextMap) {
    if (context != null) {
      context.replaceMappings(contextMap);
    }
    eventLogger.trace = (boolean) true;
    eventLogger.printEventToString = (boolean) true;
    eventLogger.printThreadName = (boolean) true;
    eventLogger.traceLevel = com.fluxtion.runtime.audit.EventLogControlEvent.LogLevel.INFO;
    eventLogger.clock = clock;
    binaryMapToRefFlowFunction_27.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_38.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_44.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_52.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_60.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_68.setEventProcessorContext(context);
    filterFlowFunction_8.setEventProcessorContext(context);
    flatMapFlowFunction_3.setFlatMapCompleteSignal("positionUpdate");
    flatMapFlowFunction_3.callback = callBackNode_74;
    flatMapFlowFunction_3.dirtyStateMonitor = callbackDispatcher;
    flatMapFlowFunction_17.callback = callBackNode_98;
    flatMapFlowFunction_17.dirtyStateMonitor = callbackDispatcher;
    flatMapSnapshotPositions.callback = callBackNode_153;
    flatMapSnapshotPositions.dirtyStateMonitor = callbackDispatcher;
    feeSnapshot.setEventProcessorContext(context);
    feeSnapshot.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    feeSnapshot.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    groupBySnapshotPositions.setEventProcessorContext(context);
    groupBySnapshotPositions.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    groupBySnapshotPositions.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    instrumentNetMtm.setEventProcessorContext(context);
    joinFeeSnapshot.setEventProcessorContext(context);
    joinFeeSnapshot.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_4.setEventProcessorContext(context);
    mapRef2RefFlowFunction_5.setEventProcessorContext(context);
    mapRef2RefFlowFunction_10.setEventProcessorContext(context);
    mapRef2RefFlowFunction_10.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_12.setEventProcessorContext(context);
    mapRef2RefFlowFunction_12.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_14.setEventProcessorContext(context);
    mapRef2RefFlowFunction_14.setPublishTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_14.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_16.setEventProcessorContext(context);
    mapRef2RefFlowFunction_16.setPublishTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_16.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_23.setEventProcessorContext(context);
    mapRef2RefFlowFunction_25.setEventProcessorContext(context);
    mapRef2RefFlowFunction_25.setPublishTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_31.setEventProcessorContext(context);
    mapRef2RefFlowFunction_33.setEventProcessorContext(context);
    mapRef2RefFlowFunction_33.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_33.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_40.setEventProcessorContext(context);
    mapRef2RefFlowFunction_42.setEventProcessorContext(context);
    mapRef2RefFlowFunction_42.setPublishTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_46.setEventProcessorContext(context);
    mapRef2RefFlowFunction_48.setEventProcessorContext(context);
    mapRef2RefFlowFunction_48.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_50.setEventProcessorContext(context);
    mapRef2RefFlowFunction_50.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_54.setEventProcessorContext(context);
    mapRef2RefFlowFunction_54.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_55.setEventProcessorContext(context);
    mapRef2RefFlowFunction_56.setEventProcessorContext(context);
    mapRef2RefFlowFunction_62.setEventProcessorContext(context);
    mapRef2RefFlowFunction_64.setEventProcessorContext(context);
    mapRef2RefFlowFunction_64.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_66.setEventProcessorContext(context);
    mapRef2RefFlowFunction_66.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_70.setEventProcessorContext(context);
    mapRef2RefFlowFunction_70.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mergeFlowFunction_6.dirtyStateMonitor = callbackDispatcher;
    peekFlowFunction_21.setEventProcessorContext(context);
    globalNetMtm.setEventProcessorContext(context);
    pushFlowFunction_58.setEventProcessorContext(context);
    pushFlowFunction_72.setEventProcessorContext(context);
    templateMessage_20.clock = clock;
    context.setClock(clock);
    globalNetMtmListener.setEventProcessorContext(context);
    instrumentNetMtmListener.setEventProcessorContext(context);
    serviceRegistry.setEventProcessorContext(context);
    //node auditors
    initialiseAuditor(clock);
    initialiseAuditor(eventLogger);
    initialiseAuditor(nodeNameLookup);
    initialiseAuditor(serviceRegistry);
    if (subscriptionManager != null) {
      subscriptionManager.setSubscribingEventProcessor(this);
    }
    if (context != null) {
      context.setEventProcessorCallback(this);
    }
  }

  public FluxtionPnlCalculator() {
    this(null);
  }

  @Override
  public void init() {
    initCalled = true;
    auditEvent(Lifecycle.LifecycleEvent.Init);
    //initialise dirty lookup map
    isDirty("test");
    clock.init();
    namedFeedTableNode_73.initialise();
    namedFeedTableNode_73.init();
    derivedRateNode.init();
    eventFeedBatcher.init();
    positionCache.init();
    handlerPositionSnapshot.init();
    flatMapFlowFunction_17.init();
    flatMapSnapshotPositions.init();
    handlerSignal_positionSnapshotReset.init();
    handlerSignal_positionUpdate.init();
    feeSnapshot.initialiseEventStream();
    groupBySnapshotPositions.initialiseEventStream();
    handlerTrade.init();
    handlerTradeBatch.init();
    flatMapFlowFunction_3.init();
    mapRef2RefFlowFunction_4.initialiseEventStream();
    mapRef2RefFlowFunction_5.initialiseEventStream();
    templateMessage_20.initialise();
    peekFlowFunction_21.initialiseEventStream();
    tradeSequenceFilter_7.init();
    filterFlowFunction_8.initialiseEventStream();
    mapRef2RefFlowFunction_10.initialiseEventStream();
    mapRef2RefFlowFunction_12.initialiseEventStream();
    binaryMapToRefFlowFunction_38.initialiseEventStream();
    mapRef2RefFlowFunction_14.initialiseEventStream();
    mapRef2RefFlowFunction_16.initialiseEventStream();
    binaryMapToRefFlowFunction_60.initialiseEventStream();
    mapRef2RefFlowFunction_23.initialiseEventStream();
    mapRef2RefFlowFunction_25.initialiseEventStream();
    binaryMapToRefFlowFunction_27.initialiseEventStream();
    joinFeeSnapshot.initialiseEventStream();
    mapRef2RefFlowFunction_31.initialiseEventStream();
    mapRef2RefFlowFunction_33.initialiseEventStream();
    mapRef2RefFlowFunction_40.initialiseEventStream();
    mapRef2RefFlowFunction_42.initialiseEventStream();
    binaryMapToRefFlowFunction_44.initialiseEventStream();
    mapRef2RefFlowFunction_46.initialiseEventStream();
    mapRef2RefFlowFunction_48.initialiseEventStream();
    mapRef2RefFlowFunction_50.initialiseEventStream();
    binaryMapToRefFlowFunction_52.initialiseEventStream();
    mapRef2RefFlowFunction_54.initialiseEventStream();
    mapRef2RefFlowFunction_55.initialiseEventStream();
    mapRef2RefFlowFunction_56.initialiseEventStream();
    globalNetMtm.initialiseEventStream();
    mapRef2RefFlowFunction_62.initialiseEventStream();
    mapRef2RefFlowFunction_64.initialiseEventStream();
    mapRef2RefFlowFunction_66.initialiseEventStream();
    binaryMapToRefFlowFunction_68.initialiseEventStream();
    mapRef2RefFlowFunction_70.initialiseEventStream();
    instrumentNetMtm.initialiseEventStream();
    pushFlowFunction_58.initialiseEventStream();
    pushFlowFunction_72.initialiseEventStream();
    afterEvent();
  }

  @Override
  public void start() {
    if (!initCalled) {
      throw new RuntimeException("init() must be called before start()");
    }
    processing = true;
    auditEvent(Lifecycle.LifecycleEvent.Start);
    eventFeedBatcher.start();
    templateMessage_20.start();
    afterEvent();
    callbackDispatcher.dispatchQueuedCallbacks();
    processing = false;
  }

  @Override
  public void startComplete() {
    if (!initCalled) {
      throw new RuntimeException("init() must be called before startComplete()");
    }
    processing = true;
    auditEvent(Lifecycle.LifecycleEvent.StartComplete);

    afterEvent();
    callbackDispatcher.dispatchQueuedCallbacks();
    processing = false;
  }

  @Override
  public void stop() {
    if (!initCalled) {
      throw new RuntimeException("init() must be called before stop()");
    }
    processing = true;
    auditEvent(Lifecycle.LifecycleEvent.Stop);

    afterEvent();
    callbackDispatcher.dispatchQueuedCallbacks();
    processing = false;
  }

  @Override
  public void tearDown() {
    initCalled = false;
    auditEvent(Lifecycle.LifecycleEvent.TearDown);
    serviceRegistry.tearDown();
    nodeNameLookup.tearDown();
    eventLogger.tearDown();
    clock.tearDown();
    handlerTradeBatch.tearDown();
    handlerTrade.tearDown();
    handlerSignal_positionUpdate.tearDown();
    handlerSignal_positionSnapshotReset.tearDown();
    handlerPositionSnapshot.tearDown();
    subscriptionManager.tearDown();
    afterEvent();
  }

  @Override
  public void setContextParameterMap(Map<Object, Object> newContextMapping) {
    context.replaceMappings(newContextMapping);
  }

  @Override
  public void addContextParameter(Object key, Object value) {
    context.addMapping(key, value);
  }

  //EVENT DISPATCH - START
  @Override
  public void onEvent(Object event) {
    if (buffering) {
      triggerCalculation();
    }
    if (processing) {
      callbackDispatcher.queueReentrantEvent(event);
    } else {
      processing = true;
      onEventInternal(event);
      callbackDispatcher.dispatchQueuedCallbacks();
      processing = false;
    }
  }

  @Override
  public void onEventInternal(Object event) {
    if (event instanceof com.fluxtion.runtime.audit.EventLogControlEvent) {
      EventLogControlEvent typedEvent = (EventLogControlEvent) event;
      handleEvent(typedEvent);
    } else if (event
        instanceof com.fluxtion.runtime.callback.InstanceCallbackEvent.InstanceCallbackEvent_0) {
      InstanceCallbackEvent_0 typedEvent = (InstanceCallbackEvent_0) event;
      handleEvent(typedEvent);
    } else if (event
        instanceof com.fluxtion.runtime.callback.InstanceCallbackEvent.InstanceCallbackEvent_1) {
      InstanceCallbackEvent_1 typedEvent = (InstanceCallbackEvent_1) event;
      handleEvent(typedEvent);
    } else if (event
        instanceof com.fluxtion.runtime.callback.InstanceCallbackEvent.InstanceCallbackEvent_2) {
      InstanceCallbackEvent_2 typedEvent = (InstanceCallbackEvent_2) event;
      handleEvent(typedEvent);
    } else if (event instanceof com.fluxtion.runtime.event.Signal) {
      Signal typedEvent = (Signal) event;
      handleEvent(typedEvent);
    } else if (event instanceof com.fluxtion.runtime.output.SinkDeregister) {
      SinkDeregister typedEvent = (SinkDeregister) event;
      handleEvent(typedEvent);
    } else if (event instanceof com.fluxtion.runtime.output.SinkRegistration) {
      SinkRegistration typedEvent = (SinkRegistration) event;
      handleEvent(typedEvent);
    } else if (event instanceof com.fluxtion.runtime.time.ClockStrategy.ClockStrategyEvent) {
      ClockStrategyEvent typedEvent = (ClockStrategyEvent) event;
      handleEvent(typedEvent);
    } else if (event instanceof com.fluxtion.server.lib.pnl.MidPrice) {
      MidPrice typedEvent = (MidPrice) event;
      handleEvent(typedEvent);
    } else if (event instanceof com.fluxtion.server.lib.pnl.MidPriceBatch) {
      MidPriceBatch typedEvent = (MidPriceBatch) event;
      handleEvent(typedEvent);
    } else if (event instanceof com.fluxtion.server.lib.pnl.MtmInstrument) {
      MtmInstrument typedEvent = (MtmInstrument) event;
      handleEvent(typedEvent);
    } else if (event instanceof com.fluxtion.server.lib.pnl.PositionSnapshot) {
      PositionSnapshot typedEvent = (PositionSnapshot) event;
      handleEvent(typedEvent);
    } else if (event instanceof com.fluxtion.server.lib.pnl.Trade) {
      Trade typedEvent = (Trade) event;
      handleEvent(typedEvent);
    } else if (event instanceof com.fluxtion.server.lib.pnl.TradeBatch) {
      TradeBatch typedEvent = (TradeBatch) event;
      handleEvent(typedEvent);
    } else if (event instanceof com.fluxtion.runtime.event.NamedFeedEvent) {
      NamedFeedEvent typedEvent = (NamedFeedEvent) event;
      handleEvent(typedEvent);
    } else {
      unKnownEventHandler(event);
    }
  }

  public void handleEvent(EventLogControlEvent typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(eventLogger, "eventLogger", "calculationLogConfig", typedEvent);
    eventLogger.calculationLogConfig(typedEvent);
    afterEvent();
  }

  public void handleEvent(InstanceCallbackEvent_0 typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(callBackNode_74, "callBackNode_74", "onEvent", typedEvent);
    isDirty_callBackNode_74 = callBackNode_74.onEvent(typedEvent);
    if (guardCheck_flatMapFlowFunction_3()) {
      auditInvocation(
          flatMapFlowFunction_3, "flatMapFlowFunction_3", "callbackReceived", typedEvent);
      isDirty_flatMapFlowFunction_3 = true;
      flatMapFlowFunction_3.callbackReceived();
      if (isDirty_flatMapFlowFunction_3) {
        mapRef2RefFlowFunction_4.inputUpdated(flatMapFlowFunction_3);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_4()) {
      auditInvocation(mapRef2RefFlowFunction_4, "mapRef2RefFlowFunction_4", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_4 = mapRef2RefFlowFunction_4.map();
      if (isDirty_mapRef2RefFlowFunction_4) {
        mergeFlowFunction_6.inputStreamUpdated(mapRef2RefFlowFunction_4);
      }
    }
    if (guardCheck_mergeFlowFunction_6()) {
      auditInvocation(mergeFlowFunction_6, "mergeFlowFunction_6", "publishMerge", typedEvent);
      isDirty_mergeFlowFunction_6 = mergeFlowFunction_6.publishMerge();
      if (isDirty_mergeFlowFunction_6) {
        filterFlowFunction_8.inputUpdated(mergeFlowFunction_6);
      }
    }
    if (guardCheck_filterFlowFunction_8()) {
      auditInvocation(filterFlowFunction_8, "filterFlowFunction_8", "filter", typedEvent);
      isDirty_filterFlowFunction_8 = filterFlowFunction_8.filter();
      if (isDirty_filterFlowFunction_8) {
        mapRef2RefFlowFunction_10.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_12.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_14.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_16.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_23.inputUpdated(filterFlowFunction_8);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      auditInvocation(mapRef2RefFlowFunction_10, "mapRef2RefFlowFunction_10", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_38.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_38.input2Updated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_38()) {
      auditInvocation(
          binaryMapToRefFlowFunction_38, "binaryMapToRefFlowFunction_38", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_38 = binaryMapToRefFlowFunction_38.map();
      if (isDirty_binaryMapToRefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(binaryMapToRefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_60.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_60.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_60()) {
      auditInvocation(
          binaryMapToRefFlowFunction_60, "binaryMapToRefFlowFunction_60", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_60 = binaryMapToRefFlowFunction_60.map();
      if (isDirty_binaryMapToRefFlowFunction_60) {
        mapRef2RefFlowFunction_62.inputUpdated(binaryMapToRefFlowFunction_60);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_23()) {
      auditInvocation(mapRef2RefFlowFunction_23, "mapRef2RefFlowFunction_23", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_23 = mapRef2RefFlowFunction_23.map();
      if (isDirty_mapRef2RefFlowFunction_23) {
        mapRef2RefFlowFunction_25.inputUpdated(mapRef2RefFlowFunction_23);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_25()) {
      auditInvocation(mapRef2RefFlowFunction_25, "mapRef2RefFlowFunction_25", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_25 = mapRef2RefFlowFunction_25.map();
      if (isDirty_mapRef2RefFlowFunction_25) {
        binaryMapToRefFlowFunction_27.inputUpdated(mapRef2RefFlowFunction_25);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_27()) {
      auditInvocation(
          binaryMapToRefFlowFunction_27, "binaryMapToRefFlowFunction_27", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_27 = binaryMapToRefFlowFunction_27.map();
      if (isDirty_binaryMapToRefFlowFunction_27) {
        joinFeeSnapshot.inputUpdated(binaryMapToRefFlowFunction_27);
      }
    }
    if (guardCheck_joinFeeSnapshot()) {
      auditInvocation(joinFeeSnapshot, "joinFeeSnapshot", "map", typedEvent);
      isDirty_joinFeeSnapshot = joinFeeSnapshot.map();
      if (isDirty_joinFeeSnapshot) {
        mapRef2RefFlowFunction_31.inputUpdated(joinFeeSnapshot);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_31()) {
      auditInvocation(mapRef2RefFlowFunction_31, "mapRef2RefFlowFunction_31", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_31 = mapRef2RefFlowFunction_31.map();
      if (isDirty_mapRef2RefFlowFunction_31) {
        mapRef2RefFlowFunction_33.inputUpdated(mapRef2RefFlowFunction_31);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_33()) {
      auditInvocation(mapRef2RefFlowFunction_33, "mapRef2RefFlowFunction_33", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_33 = mapRef2RefFlowFunction_33.map();
      if (isDirty_mapRef2RefFlowFunction_33) {
        binaryMapToRefFlowFunction_52.input2Updated(mapRef2RefFlowFunction_33);
        binaryMapToRefFlowFunction_68.input2Updated(mapRef2RefFlowFunction_33);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      auditInvocation(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        mapRef2RefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_42()) {
      auditInvocation(mapRef2RefFlowFunction_42, "mapRef2RefFlowFunction_42", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
      if (isDirty_mapRef2RefFlowFunction_42) {
        binaryMapToRefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_42);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_44()) {
      auditInvocation(
          binaryMapToRefFlowFunction_44, "binaryMapToRefFlowFunction_44", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_44 = binaryMapToRefFlowFunction_44.map();
      if (isDirty_binaryMapToRefFlowFunction_44) {
        mapRef2RefFlowFunction_46.inputUpdated(binaryMapToRefFlowFunction_44);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      auditInvocation(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        mapRef2RefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_48()) {
      auditInvocation(mapRef2RefFlowFunction_48, "mapRef2RefFlowFunction_48", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_48 = mapRef2RefFlowFunction_48.map();
      if (isDirty_mapRef2RefFlowFunction_48) {
        mapRef2RefFlowFunction_50.inputUpdated(mapRef2RefFlowFunction_48);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_50()) {
      auditInvocation(mapRef2RefFlowFunction_50, "mapRef2RefFlowFunction_50", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
      if (isDirty_mapRef2RefFlowFunction_50) {
        binaryMapToRefFlowFunction_52.inputUpdated(mapRef2RefFlowFunction_50);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_52()) {
      auditInvocation(
          binaryMapToRefFlowFunction_52, "binaryMapToRefFlowFunction_52", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_52 = binaryMapToRefFlowFunction_52.map();
      if (isDirty_binaryMapToRefFlowFunction_52) {
        mapRef2RefFlowFunction_54.inputUpdated(binaryMapToRefFlowFunction_52);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      auditInvocation(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        mapRef2RefFlowFunction_55.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_55()) {
      auditInvocation(mapRef2RefFlowFunction_55, "mapRef2RefFlowFunction_55", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_55 = mapRef2RefFlowFunction_55.map();
      if (isDirty_mapRef2RefFlowFunction_55) {
        mapRef2RefFlowFunction_56.inputUpdated(mapRef2RefFlowFunction_55);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_56()) {
      auditInvocation(mapRef2RefFlowFunction_56, "mapRef2RefFlowFunction_56", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_56 = mapRef2RefFlowFunction_56.map();
      if (isDirty_mapRef2RefFlowFunction_56) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_56);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "push", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.push();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_58.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_62()) {
      auditInvocation(mapRef2RefFlowFunction_62, "mapRef2RefFlowFunction_62", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_62 = mapRef2RefFlowFunction_62.map();
      if (isDirty_mapRef2RefFlowFunction_62) {
        mapRef2RefFlowFunction_64.inputUpdated(mapRef2RefFlowFunction_62);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_64()) {
      auditInvocation(mapRef2RefFlowFunction_64, "mapRef2RefFlowFunction_64", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_64 = mapRef2RefFlowFunction_64.map();
      if (isDirty_mapRef2RefFlowFunction_64) {
        mapRef2RefFlowFunction_66.inputUpdated(mapRef2RefFlowFunction_64);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_66()) {
      auditInvocation(mapRef2RefFlowFunction_66, "mapRef2RefFlowFunction_66", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_66 = mapRef2RefFlowFunction_66.map();
      if (isDirty_mapRef2RefFlowFunction_66) {
        binaryMapToRefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_66);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_68()) {
      auditInvocation(
          binaryMapToRefFlowFunction_68, "binaryMapToRefFlowFunction_68", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_68 = binaryMapToRefFlowFunction_68.map();
      if (isDirty_binaryMapToRefFlowFunction_68) {
        mapRef2RefFlowFunction_70.inputUpdated(binaryMapToRefFlowFunction_68);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_70()) {
      auditInvocation(mapRef2RefFlowFunction_70, "mapRef2RefFlowFunction_70", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_70 = mapRef2RefFlowFunction_70.map();
      if (isDirty_mapRef2RefFlowFunction_70) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_70);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_72.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_58()) {
      auditInvocation(pushFlowFunction_58, "pushFlowFunction_58", "push", typedEvent);
      isDirty_pushFlowFunction_58 = pushFlowFunction_58.push();
    }
    if (guardCheck_pushFlowFunction_72()) {
      auditInvocation(pushFlowFunction_72, "pushFlowFunction_72", "push", typedEvent);
      isDirty_pushFlowFunction_72 = pushFlowFunction_72.push();
    }
    afterEvent();
  }

  public void handleEvent(InstanceCallbackEvent_1 typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(callBackNode_98, "callBackNode_98", "onEvent", typedEvent);
    isDirty_callBackNode_98 = callBackNode_98.onEvent(typedEvent);
    if (guardCheck_flatMapFlowFunction_17()) {
      auditInvocation(
          flatMapFlowFunction_17, "flatMapFlowFunction_17", "callbackReceived", typedEvent);
      isDirty_flatMapFlowFunction_17 = true;
      flatMapFlowFunction_17.callbackReceived();
      if (isDirty_flatMapFlowFunction_17) {
        feeSnapshot.inputUpdated(flatMapFlowFunction_17);
      }
    }
    if (guardCheck_feeSnapshot()) {
      auditInvocation(feeSnapshot, "feeSnapshot", "map", typedEvent);
      isDirty_feeSnapshot = feeSnapshot.map();
      if (isDirty_feeSnapshot) {
        peekFlowFunction_21.inputUpdated(feeSnapshot);
        binaryMapToRefFlowFunction_27.input2Updated(feeSnapshot);
      }
    }
    if (guardCheck_peekFlowFunction_21()) {
      auditInvocation(peekFlowFunction_21, "peekFlowFunction_21", "peek", typedEvent);
      peekFlowFunction_21.peek();
    }
    if (guardCheck_binaryMapToRefFlowFunction_27()) {
      auditInvocation(
          binaryMapToRefFlowFunction_27, "binaryMapToRefFlowFunction_27", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_27 = binaryMapToRefFlowFunction_27.map();
      if (isDirty_binaryMapToRefFlowFunction_27) {
        joinFeeSnapshot.inputUpdated(binaryMapToRefFlowFunction_27);
      }
    }
    if (guardCheck_joinFeeSnapshot()) {
      auditInvocation(joinFeeSnapshot, "joinFeeSnapshot", "map", typedEvent);
      isDirty_joinFeeSnapshot = joinFeeSnapshot.map();
      if (isDirty_joinFeeSnapshot) {
        mapRef2RefFlowFunction_31.inputUpdated(joinFeeSnapshot);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_31()) {
      auditInvocation(mapRef2RefFlowFunction_31, "mapRef2RefFlowFunction_31", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_31 = mapRef2RefFlowFunction_31.map();
      if (isDirty_mapRef2RefFlowFunction_31) {
        mapRef2RefFlowFunction_33.inputUpdated(mapRef2RefFlowFunction_31);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_33()) {
      auditInvocation(mapRef2RefFlowFunction_33, "mapRef2RefFlowFunction_33", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_33 = mapRef2RefFlowFunction_33.map();
      if (isDirty_mapRef2RefFlowFunction_33) {
        binaryMapToRefFlowFunction_52.input2Updated(mapRef2RefFlowFunction_33);
        binaryMapToRefFlowFunction_68.input2Updated(mapRef2RefFlowFunction_33);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_52()) {
      auditInvocation(
          binaryMapToRefFlowFunction_52, "binaryMapToRefFlowFunction_52", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_52 = binaryMapToRefFlowFunction_52.map();
      if (isDirty_binaryMapToRefFlowFunction_52) {
        mapRef2RefFlowFunction_54.inputUpdated(binaryMapToRefFlowFunction_52);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      auditInvocation(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        mapRef2RefFlowFunction_55.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_55()) {
      auditInvocation(mapRef2RefFlowFunction_55, "mapRef2RefFlowFunction_55", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_55 = mapRef2RefFlowFunction_55.map();
      if (isDirty_mapRef2RefFlowFunction_55) {
        mapRef2RefFlowFunction_56.inputUpdated(mapRef2RefFlowFunction_55);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_56()) {
      auditInvocation(mapRef2RefFlowFunction_56, "mapRef2RefFlowFunction_56", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_56 = mapRef2RefFlowFunction_56.map();
      if (isDirty_mapRef2RefFlowFunction_56) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_56);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "push", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.push();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_58.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_68()) {
      auditInvocation(
          binaryMapToRefFlowFunction_68, "binaryMapToRefFlowFunction_68", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_68 = binaryMapToRefFlowFunction_68.map();
      if (isDirty_binaryMapToRefFlowFunction_68) {
        mapRef2RefFlowFunction_70.inputUpdated(binaryMapToRefFlowFunction_68);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_70()) {
      auditInvocation(mapRef2RefFlowFunction_70, "mapRef2RefFlowFunction_70", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_70 = mapRef2RefFlowFunction_70.map();
      if (isDirty_mapRef2RefFlowFunction_70) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_70);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_72.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_58()) {
      auditInvocation(pushFlowFunction_58, "pushFlowFunction_58", "push", typedEvent);
      isDirty_pushFlowFunction_58 = pushFlowFunction_58.push();
    }
    if (guardCheck_pushFlowFunction_72()) {
      auditInvocation(pushFlowFunction_72, "pushFlowFunction_72", "push", typedEvent);
      isDirty_pushFlowFunction_72 = pushFlowFunction_72.push();
    }
    afterEvent();
  }

  public void handleEvent(InstanceCallbackEvent_2 typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(callBackNode_153, "callBackNode_153", "onEvent", typedEvent);
    isDirty_callBackNode_153 = callBackNode_153.onEvent(typedEvent);
    if (guardCheck_flatMapSnapshotPositions()) {
      auditInvocation(
          flatMapSnapshotPositions, "flatMapSnapshotPositions", "callbackReceived", typedEvent);
      isDirty_flatMapSnapshotPositions = true;
      flatMapSnapshotPositions.callbackReceived();
      if (isDirty_flatMapSnapshotPositions) {
        groupBySnapshotPositions.inputUpdated(flatMapSnapshotPositions);
      }
    }
    if (guardCheck_groupBySnapshotPositions()) {
      auditInvocation(groupBySnapshotPositions, "groupBySnapshotPositions", "map", typedEvent);
      isDirty_groupBySnapshotPositions = groupBySnapshotPositions.map();
      if (isDirty_groupBySnapshotPositions) {
        binaryMapToRefFlowFunction_44.input2Updated(groupBySnapshotPositions);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_44()) {
      auditInvocation(
          binaryMapToRefFlowFunction_44, "binaryMapToRefFlowFunction_44", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_44 = binaryMapToRefFlowFunction_44.map();
      if (isDirty_binaryMapToRefFlowFunction_44) {
        mapRef2RefFlowFunction_46.inputUpdated(binaryMapToRefFlowFunction_44);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      auditInvocation(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        mapRef2RefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_48()) {
      auditInvocation(mapRef2RefFlowFunction_48, "mapRef2RefFlowFunction_48", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_48 = mapRef2RefFlowFunction_48.map();
      if (isDirty_mapRef2RefFlowFunction_48) {
        mapRef2RefFlowFunction_50.inputUpdated(mapRef2RefFlowFunction_48);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_50()) {
      auditInvocation(mapRef2RefFlowFunction_50, "mapRef2RefFlowFunction_50", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
      if (isDirty_mapRef2RefFlowFunction_50) {
        binaryMapToRefFlowFunction_52.inputUpdated(mapRef2RefFlowFunction_50);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_52()) {
      auditInvocation(
          binaryMapToRefFlowFunction_52, "binaryMapToRefFlowFunction_52", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_52 = binaryMapToRefFlowFunction_52.map();
      if (isDirty_binaryMapToRefFlowFunction_52) {
        mapRef2RefFlowFunction_54.inputUpdated(binaryMapToRefFlowFunction_52);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      auditInvocation(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        mapRef2RefFlowFunction_55.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_55()) {
      auditInvocation(mapRef2RefFlowFunction_55, "mapRef2RefFlowFunction_55", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_55 = mapRef2RefFlowFunction_55.map();
      if (isDirty_mapRef2RefFlowFunction_55) {
        mapRef2RefFlowFunction_56.inputUpdated(mapRef2RefFlowFunction_55);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_56()) {
      auditInvocation(mapRef2RefFlowFunction_56, "mapRef2RefFlowFunction_56", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_56 = mapRef2RefFlowFunction_56.map();
      if (isDirty_mapRef2RefFlowFunction_56) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_56);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "push", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.push();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_58.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_58()) {
      auditInvocation(pushFlowFunction_58, "pushFlowFunction_58", "push", typedEvent);
      isDirty_pushFlowFunction_58 = pushFlowFunction_58.push();
    }
    afterEvent();
  }

  public void handleEvent(Signal typedEvent) {
    auditEvent(typedEvent);
    switch (typedEvent.filterString()) {
        //Event Class:[com.fluxtion.runtime.event.Signal] filterString:[positionSnapshotReset]
      case ("positionSnapshotReset"):
        handle_Signal_positionSnapshotReset(typedEvent);
        afterEvent();
        return;
        //Event Class:[com.fluxtion.runtime.event.Signal] filterString:[positionUpdate]
      case ("positionUpdate"):
        handle_Signal_positionUpdate(typedEvent);
        afterEvent();
        return;
    }
    afterEvent();
  }

  public void handleEvent(SinkDeregister typedEvent) {
    auditEvent(typedEvent);
    switch (typedEvent.filterString()) {
        //Event Class:[com.fluxtion.runtime.output.SinkDeregister] filterString:[globalNetMtmListener]
      case ("globalNetMtmListener"):
        handle_SinkDeregister_globalNetMtmListener(typedEvent);
        afterEvent();
        return;
        //Event Class:[com.fluxtion.runtime.output.SinkDeregister] filterString:[instrumentNetMtmListener]
      case ("instrumentNetMtmListener"):
        handle_SinkDeregister_instrumentNetMtmListener(typedEvent);
        afterEvent();
        return;
    }
    afterEvent();
  }

  public void handleEvent(SinkRegistration typedEvent) {
    auditEvent(typedEvent);
    switch (typedEvent.filterString()) {
        //Event Class:[com.fluxtion.runtime.output.SinkRegistration] filterString:[globalNetMtmListener]
      case ("globalNetMtmListener"):
        handle_SinkRegistration_globalNetMtmListener(typedEvent);
        afterEvent();
        return;
        //Event Class:[com.fluxtion.runtime.output.SinkRegistration] filterString:[instrumentNetMtmListener]
      case ("instrumentNetMtmListener"):
        handle_SinkRegistration_instrumentNetMtmListener(typedEvent);
        afterEvent();
        return;
    }
    afterEvent();
  }

  public void handleEvent(ClockStrategyEvent typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(clock, "clock", "setClockStrategy", typedEvent);
    isDirty_clock = true;
    clock.setClockStrategy(typedEvent);
    afterEvent();
  }

  public void handleEvent(MidPrice typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(derivedRateNode, "derivedRateNode", "midRate", typedEvent);
    isDirty_derivedRateNode = derivedRateNode.midRate(typedEvent);
    afterEvent();
  }

  public void handleEvent(MidPriceBatch typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(derivedRateNode, "derivedRateNode", "midRateBatch", typedEvent);
    isDirty_derivedRateNode = derivedRateNode.midRateBatch(typedEvent);
    afterEvent();
  }

  public void handleEvent(MtmInstrument typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(derivedRateNode, "derivedRateNode", "updateMtmInstrument", typedEvent);
    isDirty_derivedRateNode = derivedRateNode.updateMtmInstrument(typedEvent);
    afterEvent();
  }

  public void handleEvent(PositionSnapshot typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(handlerPositionSnapshot, "handlerPositionSnapshot", "onEvent", typedEvent);
    isDirty_handlerPositionSnapshot = handlerPositionSnapshot.onEvent(typedEvent);
    if (isDirty_handlerPositionSnapshot) {
      flatMapFlowFunction_17.inputUpdatedAndFlatMap(handlerPositionSnapshot);
      flatMapSnapshotPositions.inputUpdatedAndFlatMap(handlerPositionSnapshot);
    }
    afterEvent();
  }

  public void handleEvent(Trade typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(handlerTrade, "handlerTrade", "onEvent", typedEvent);
    isDirty_handlerTrade = handlerTrade.onEvent(typedEvent);
    if (isDirty_handlerTrade) {
      mapRef2RefFlowFunction_5.inputUpdated(handlerTrade);
    }
    if (guardCheck_mapRef2RefFlowFunction_5()) {
      auditInvocation(mapRef2RefFlowFunction_5, "mapRef2RefFlowFunction_5", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_5 = mapRef2RefFlowFunction_5.map();
      if (isDirty_mapRef2RefFlowFunction_5) {
        mergeFlowFunction_6.inputStreamUpdated(mapRef2RefFlowFunction_5);
      }
    }
    if (guardCheck_mergeFlowFunction_6()) {
      auditInvocation(mergeFlowFunction_6, "mergeFlowFunction_6", "publishMerge", typedEvent);
      isDirty_mergeFlowFunction_6 = mergeFlowFunction_6.publishMerge();
      if (isDirty_mergeFlowFunction_6) {
        filterFlowFunction_8.inputUpdated(mergeFlowFunction_6);
      }
    }
    if (guardCheck_filterFlowFunction_8()) {
      auditInvocation(filterFlowFunction_8, "filterFlowFunction_8", "filter", typedEvent);
      isDirty_filterFlowFunction_8 = filterFlowFunction_8.filter();
      if (isDirty_filterFlowFunction_8) {
        mapRef2RefFlowFunction_10.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_12.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_14.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_16.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_23.inputUpdated(filterFlowFunction_8);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      auditInvocation(mapRef2RefFlowFunction_10, "mapRef2RefFlowFunction_10", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_38.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_38.input2Updated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_38()) {
      auditInvocation(
          binaryMapToRefFlowFunction_38, "binaryMapToRefFlowFunction_38", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_38 = binaryMapToRefFlowFunction_38.map();
      if (isDirty_binaryMapToRefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(binaryMapToRefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_60.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_60.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_60()) {
      auditInvocation(
          binaryMapToRefFlowFunction_60, "binaryMapToRefFlowFunction_60", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_60 = binaryMapToRefFlowFunction_60.map();
      if (isDirty_binaryMapToRefFlowFunction_60) {
        mapRef2RefFlowFunction_62.inputUpdated(binaryMapToRefFlowFunction_60);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_23()) {
      auditInvocation(mapRef2RefFlowFunction_23, "mapRef2RefFlowFunction_23", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_23 = mapRef2RefFlowFunction_23.map();
      if (isDirty_mapRef2RefFlowFunction_23) {
        mapRef2RefFlowFunction_25.inputUpdated(mapRef2RefFlowFunction_23);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_25()) {
      auditInvocation(mapRef2RefFlowFunction_25, "mapRef2RefFlowFunction_25", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_25 = mapRef2RefFlowFunction_25.map();
      if (isDirty_mapRef2RefFlowFunction_25) {
        binaryMapToRefFlowFunction_27.inputUpdated(mapRef2RefFlowFunction_25);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_27()) {
      auditInvocation(
          binaryMapToRefFlowFunction_27, "binaryMapToRefFlowFunction_27", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_27 = binaryMapToRefFlowFunction_27.map();
      if (isDirty_binaryMapToRefFlowFunction_27) {
        joinFeeSnapshot.inputUpdated(binaryMapToRefFlowFunction_27);
      }
    }
    if (guardCheck_joinFeeSnapshot()) {
      auditInvocation(joinFeeSnapshot, "joinFeeSnapshot", "map", typedEvent);
      isDirty_joinFeeSnapshot = joinFeeSnapshot.map();
      if (isDirty_joinFeeSnapshot) {
        mapRef2RefFlowFunction_31.inputUpdated(joinFeeSnapshot);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_31()) {
      auditInvocation(mapRef2RefFlowFunction_31, "mapRef2RefFlowFunction_31", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_31 = mapRef2RefFlowFunction_31.map();
      if (isDirty_mapRef2RefFlowFunction_31) {
        mapRef2RefFlowFunction_33.inputUpdated(mapRef2RefFlowFunction_31);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_33()) {
      auditInvocation(mapRef2RefFlowFunction_33, "mapRef2RefFlowFunction_33", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_33 = mapRef2RefFlowFunction_33.map();
      if (isDirty_mapRef2RefFlowFunction_33) {
        binaryMapToRefFlowFunction_52.input2Updated(mapRef2RefFlowFunction_33);
        binaryMapToRefFlowFunction_68.input2Updated(mapRef2RefFlowFunction_33);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      auditInvocation(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        mapRef2RefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_42()) {
      auditInvocation(mapRef2RefFlowFunction_42, "mapRef2RefFlowFunction_42", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
      if (isDirty_mapRef2RefFlowFunction_42) {
        binaryMapToRefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_42);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_44()) {
      auditInvocation(
          binaryMapToRefFlowFunction_44, "binaryMapToRefFlowFunction_44", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_44 = binaryMapToRefFlowFunction_44.map();
      if (isDirty_binaryMapToRefFlowFunction_44) {
        mapRef2RefFlowFunction_46.inputUpdated(binaryMapToRefFlowFunction_44);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      auditInvocation(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        mapRef2RefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_48()) {
      auditInvocation(mapRef2RefFlowFunction_48, "mapRef2RefFlowFunction_48", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_48 = mapRef2RefFlowFunction_48.map();
      if (isDirty_mapRef2RefFlowFunction_48) {
        mapRef2RefFlowFunction_50.inputUpdated(mapRef2RefFlowFunction_48);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_50()) {
      auditInvocation(mapRef2RefFlowFunction_50, "mapRef2RefFlowFunction_50", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
      if (isDirty_mapRef2RefFlowFunction_50) {
        binaryMapToRefFlowFunction_52.inputUpdated(mapRef2RefFlowFunction_50);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_52()) {
      auditInvocation(
          binaryMapToRefFlowFunction_52, "binaryMapToRefFlowFunction_52", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_52 = binaryMapToRefFlowFunction_52.map();
      if (isDirty_binaryMapToRefFlowFunction_52) {
        mapRef2RefFlowFunction_54.inputUpdated(binaryMapToRefFlowFunction_52);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      auditInvocation(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        mapRef2RefFlowFunction_55.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_55()) {
      auditInvocation(mapRef2RefFlowFunction_55, "mapRef2RefFlowFunction_55", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_55 = mapRef2RefFlowFunction_55.map();
      if (isDirty_mapRef2RefFlowFunction_55) {
        mapRef2RefFlowFunction_56.inputUpdated(mapRef2RefFlowFunction_55);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_56()) {
      auditInvocation(mapRef2RefFlowFunction_56, "mapRef2RefFlowFunction_56", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_56 = mapRef2RefFlowFunction_56.map();
      if (isDirty_mapRef2RefFlowFunction_56) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_56);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "push", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.push();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_58.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_62()) {
      auditInvocation(mapRef2RefFlowFunction_62, "mapRef2RefFlowFunction_62", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_62 = mapRef2RefFlowFunction_62.map();
      if (isDirty_mapRef2RefFlowFunction_62) {
        mapRef2RefFlowFunction_64.inputUpdated(mapRef2RefFlowFunction_62);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_64()) {
      auditInvocation(mapRef2RefFlowFunction_64, "mapRef2RefFlowFunction_64", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_64 = mapRef2RefFlowFunction_64.map();
      if (isDirty_mapRef2RefFlowFunction_64) {
        mapRef2RefFlowFunction_66.inputUpdated(mapRef2RefFlowFunction_64);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_66()) {
      auditInvocation(mapRef2RefFlowFunction_66, "mapRef2RefFlowFunction_66", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_66 = mapRef2RefFlowFunction_66.map();
      if (isDirty_mapRef2RefFlowFunction_66) {
        binaryMapToRefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_66);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_68()) {
      auditInvocation(
          binaryMapToRefFlowFunction_68, "binaryMapToRefFlowFunction_68", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_68 = binaryMapToRefFlowFunction_68.map();
      if (isDirty_binaryMapToRefFlowFunction_68) {
        mapRef2RefFlowFunction_70.inputUpdated(binaryMapToRefFlowFunction_68);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_70()) {
      auditInvocation(mapRef2RefFlowFunction_70, "mapRef2RefFlowFunction_70", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_70 = mapRef2RefFlowFunction_70.map();
      if (isDirty_mapRef2RefFlowFunction_70) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_70);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_72.inputUpdated(instrumentNetMtm);
      }
    }
    auditInvocation(positionCache, "positionCache", "tradeIn", typedEvent);
    positionCache.tradeIn(typedEvent);
    if (guardCheck_pushFlowFunction_58()) {
      auditInvocation(pushFlowFunction_58, "pushFlowFunction_58", "push", typedEvent);
      isDirty_pushFlowFunction_58 = pushFlowFunction_58.push();
    }
    if (guardCheck_pushFlowFunction_72()) {
      auditInvocation(pushFlowFunction_72, "pushFlowFunction_72", "push", typedEvent);
      isDirty_pushFlowFunction_72 = pushFlowFunction_72.push();
    }
    afterEvent();
  }

  public void handleEvent(TradeBatch typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(handlerTradeBatch, "handlerTradeBatch", "onEvent", typedEvent);
    isDirty_handlerTradeBatch = handlerTradeBatch.onEvent(typedEvent);
    if (isDirty_handlerTradeBatch) {
      flatMapFlowFunction_3.inputUpdatedAndFlatMap(handlerTradeBatch);
    }
    afterEvent();
  }

  public void handleEvent(NamedFeedEvent typedEvent) {
    auditEvent(typedEvent);
    switch (typedEvent.filterString()) {
        //Event Class:[com.fluxtion.runtime.event.NamedFeedEvent] filterString:[symbolFeed]
      case ("symbolFeed"):
        handle_NamedFeedEvent_symbolFeed(typedEvent);
        afterEvent();
        return;
    }
    afterEvent();
  }
  //EVENT DISPATCH - END

  //FILTERED DISPATCH - START
  private void handle_Signal_positionSnapshotReset(Signal typedEvent) {
    auditInvocation(
        handlerSignal_positionSnapshotReset,
        "handlerSignal_positionSnapshotReset",
        "onEvent",
        typedEvent);
    isDirty_handlerSignal_positionSnapshotReset =
        handlerSignal_positionSnapshotReset.onEvent(typedEvent);
    if (isDirty_handlerSignal_positionSnapshotReset) {
      feeSnapshot.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      groupBySnapshotPositions.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_10.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_12.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_14.publishTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_14.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_16.publishTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_16.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      joinFeeSnapshot.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
    }
    if (guardCheck_feeSnapshot()) {
      auditInvocation(feeSnapshot, "feeSnapshot", "map", typedEvent);
      isDirty_feeSnapshot = feeSnapshot.map();
      if (isDirty_feeSnapshot) {
        peekFlowFunction_21.inputUpdated(feeSnapshot);
        binaryMapToRefFlowFunction_27.input2Updated(feeSnapshot);
      }
    }
    if (guardCheck_groupBySnapshotPositions()) {
      auditInvocation(groupBySnapshotPositions, "groupBySnapshotPositions", "map", typedEvent);
      isDirty_groupBySnapshotPositions = groupBySnapshotPositions.map();
      if (isDirty_groupBySnapshotPositions) {
        binaryMapToRefFlowFunction_44.input2Updated(groupBySnapshotPositions);
      }
    }
    if (guardCheck_peekFlowFunction_21()) {
      auditInvocation(peekFlowFunction_21, "peekFlowFunction_21", "peek", typedEvent);
      peekFlowFunction_21.peek();
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      auditInvocation(mapRef2RefFlowFunction_10, "mapRef2RefFlowFunction_10", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_38.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_38.input2Updated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_38()) {
      auditInvocation(
          binaryMapToRefFlowFunction_38, "binaryMapToRefFlowFunction_38", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_38 = binaryMapToRefFlowFunction_38.map();
      if (isDirty_binaryMapToRefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(binaryMapToRefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_60.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_60.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_60()) {
      auditInvocation(
          binaryMapToRefFlowFunction_60, "binaryMapToRefFlowFunction_60", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_60 = binaryMapToRefFlowFunction_60.map();
      if (isDirty_binaryMapToRefFlowFunction_60) {
        mapRef2RefFlowFunction_62.inputUpdated(binaryMapToRefFlowFunction_60);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_27()) {
      auditInvocation(
          binaryMapToRefFlowFunction_27, "binaryMapToRefFlowFunction_27", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_27 = binaryMapToRefFlowFunction_27.map();
      if (isDirty_binaryMapToRefFlowFunction_27) {
        joinFeeSnapshot.inputUpdated(binaryMapToRefFlowFunction_27);
      }
    }
    if (guardCheck_joinFeeSnapshot()) {
      auditInvocation(joinFeeSnapshot, "joinFeeSnapshot", "map", typedEvent);
      isDirty_joinFeeSnapshot = joinFeeSnapshot.map();
      if (isDirty_joinFeeSnapshot) {
        mapRef2RefFlowFunction_31.inputUpdated(joinFeeSnapshot);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_31()) {
      auditInvocation(mapRef2RefFlowFunction_31, "mapRef2RefFlowFunction_31", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_31 = mapRef2RefFlowFunction_31.map();
      if (isDirty_mapRef2RefFlowFunction_31) {
        mapRef2RefFlowFunction_33.inputUpdated(mapRef2RefFlowFunction_31);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_33()) {
      auditInvocation(mapRef2RefFlowFunction_33, "mapRef2RefFlowFunction_33", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_33 = mapRef2RefFlowFunction_33.map();
      if (isDirty_mapRef2RefFlowFunction_33) {
        binaryMapToRefFlowFunction_52.input2Updated(mapRef2RefFlowFunction_33);
        binaryMapToRefFlowFunction_68.input2Updated(mapRef2RefFlowFunction_33);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      auditInvocation(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        mapRef2RefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_42()) {
      auditInvocation(mapRef2RefFlowFunction_42, "mapRef2RefFlowFunction_42", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
      if (isDirty_mapRef2RefFlowFunction_42) {
        binaryMapToRefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_42);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_44()) {
      auditInvocation(
          binaryMapToRefFlowFunction_44, "binaryMapToRefFlowFunction_44", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_44 = binaryMapToRefFlowFunction_44.map();
      if (isDirty_binaryMapToRefFlowFunction_44) {
        mapRef2RefFlowFunction_46.inputUpdated(binaryMapToRefFlowFunction_44);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      auditInvocation(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        mapRef2RefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_48()) {
      auditInvocation(mapRef2RefFlowFunction_48, "mapRef2RefFlowFunction_48", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_48 = mapRef2RefFlowFunction_48.map();
      if (isDirty_mapRef2RefFlowFunction_48) {
        mapRef2RefFlowFunction_50.inputUpdated(mapRef2RefFlowFunction_48);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_50()) {
      auditInvocation(mapRef2RefFlowFunction_50, "mapRef2RefFlowFunction_50", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
      if (isDirty_mapRef2RefFlowFunction_50) {
        binaryMapToRefFlowFunction_52.inputUpdated(mapRef2RefFlowFunction_50);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_52()) {
      auditInvocation(
          binaryMapToRefFlowFunction_52, "binaryMapToRefFlowFunction_52", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_52 = binaryMapToRefFlowFunction_52.map();
      if (isDirty_binaryMapToRefFlowFunction_52) {
        mapRef2RefFlowFunction_54.inputUpdated(binaryMapToRefFlowFunction_52);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      auditInvocation(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        mapRef2RefFlowFunction_55.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_55()) {
      auditInvocation(mapRef2RefFlowFunction_55, "mapRef2RefFlowFunction_55", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_55 = mapRef2RefFlowFunction_55.map();
      if (isDirty_mapRef2RefFlowFunction_55) {
        mapRef2RefFlowFunction_56.inputUpdated(mapRef2RefFlowFunction_55);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_56()) {
      auditInvocation(mapRef2RefFlowFunction_56, "mapRef2RefFlowFunction_56", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_56 = mapRef2RefFlowFunction_56.map();
      if (isDirty_mapRef2RefFlowFunction_56) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_56);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "push", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.push();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_58.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_62()) {
      auditInvocation(mapRef2RefFlowFunction_62, "mapRef2RefFlowFunction_62", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_62 = mapRef2RefFlowFunction_62.map();
      if (isDirty_mapRef2RefFlowFunction_62) {
        mapRef2RefFlowFunction_64.inputUpdated(mapRef2RefFlowFunction_62);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_64()) {
      auditInvocation(mapRef2RefFlowFunction_64, "mapRef2RefFlowFunction_64", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_64 = mapRef2RefFlowFunction_64.map();
      if (isDirty_mapRef2RefFlowFunction_64) {
        mapRef2RefFlowFunction_66.inputUpdated(mapRef2RefFlowFunction_64);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_66()) {
      auditInvocation(mapRef2RefFlowFunction_66, "mapRef2RefFlowFunction_66", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_66 = mapRef2RefFlowFunction_66.map();
      if (isDirty_mapRef2RefFlowFunction_66) {
        binaryMapToRefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_66);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_68()) {
      auditInvocation(
          binaryMapToRefFlowFunction_68, "binaryMapToRefFlowFunction_68", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_68 = binaryMapToRefFlowFunction_68.map();
      if (isDirty_binaryMapToRefFlowFunction_68) {
        mapRef2RefFlowFunction_70.inputUpdated(binaryMapToRefFlowFunction_68);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_70()) {
      auditInvocation(mapRef2RefFlowFunction_70, "mapRef2RefFlowFunction_70", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_70 = mapRef2RefFlowFunction_70.map();
      if (isDirty_mapRef2RefFlowFunction_70) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_70);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_72.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_58()) {
      auditInvocation(pushFlowFunction_58, "pushFlowFunction_58", "push", typedEvent);
      isDirty_pushFlowFunction_58 = pushFlowFunction_58.push();
    }
    if (guardCheck_pushFlowFunction_72()) {
      auditInvocation(pushFlowFunction_72, "pushFlowFunction_72", "push", typedEvent);
      isDirty_pushFlowFunction_72 = pushFlowFunction_72.push();
    }
  }

  private void handle_Signal_positionUpdate(Signal typedEvent) {
    auditInvocation(
        handlerSignal_positionUpdate, "handlerSignal_positionUpdate", "onEvent", typedEvent);
    isDirty_handlerSignal_positionUpdate = handlerSignal_positionUpdate.onEvent(typedEvent);
    if (isDirty_handlerSignal_positionUpdate) {
      feeSnapshot.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      groupBySnapshotPositions.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_25.publishTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_33.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_33.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_42.publishTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_48.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_50.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_54.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_64.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_66.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_70.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
    }
    if (guardCheck_feeSnapshot()) {
      auditInvocation(feeSnapshot, "feeSnapshot", "map", typedEvent);
      isDirty_feeSnapshot = feeSnapshot.map();
      if (isDirty_feeSnapshot) {
        peekFlowFunction_21.inputUpdated(feeSnapshot);
        binaryMapToRefFlowFunction_27.input2Updated(feeSnapshot);
      }
    }
    if (guardCheck_groupBySnapshotPositions()) {
      auditInvocation(groupBySnapshotPositions, "groupBySnapshotPositions", "map", typedEvent);
      isDirty_groupBySnapshotPositions = groupBySnapshotPositions.map();
      if (isDirty_groupBySnapshotPositions) {
        binaryMapToRefFlowFunction_44.input2Updated(groupBySnapshotPositions);
      }
    }
    if (guardCheck_peekFlowFunction_21()) {
      auditInvocation(peekFlowFunction_21, "peekFlowFunction_21", "peek", typedEvent);
      peekFlowFunction_21.peek();
    }
    if (guardCheck_mapRef2RefFlowFunction_25()) {
      auditInvocation(mapRef2RefFlowFunction_25, "mapRef2RefFlowFunction_25", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_25 = mapRef2RefFlowFunction_25.map();
      if (isDirty_mapRef2RefFlowFunction_25) {
        binaryMapToRefFlowFunction_27.inputUpdated(mapRef2RefFlowFunction_25);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_27()) {
      auditInvocation(
          binaryMapToRefFlowFunction_27, "binaryMapToRefFlowFunction_27", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_27 = binaryMapToRefFlowFunction_27.map();
      if (isDirty_binaryMapToRefFlowFunction_27) {
        joinFeeSnapshot.inputUpdated(binaryMapToRefFlowFunction_27);
      }
    }
    if (guardCheck_joinFeeSnapshot()) {
      auditInvocation(joinFeeSnapshot, "joinFeeSnapshot", "map", typedEvent);
      isDirty_joinFeeSnapshot = joinFeeSnapshot.map();
      if (isDirty_joinFeeSnapshot) {
        mapRef2RefFlowFunction_31.inputUpdated(joinFeeSnapshot);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_31()) {
      auditInvocation(mapRef2RefFlowFunction_31, "mapRef2RefFlowFunction_31", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_31 = mapRef2RefFlowFunction_31.map();
      if (isDirty_mapRef2RefFlowFunction_31) {
        mapRef2RefFlowFunction_33.inputUpdated(mapRef2RefFlowFunction_31);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_33()) {
      auditInvocation(mapRef2RefFlowFunction_33, "mapRef2RefFlowFunction_33", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_33 = mapRef2RefFlowFunction_33.map();
      if (isDirty_mapRef2RefFlowFunction_33) {
        binaryMapToRefFlowFunction_52.input2Updated(mapRef2RefFlowFunction_33);
        binaryMapToRefFlowFunction_68.input2Updated(mapRef2RefFlowFunction_33);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_42()) {
      auditInvocation(mapRef2RefFlowFunction_42, "mapRef2RefFlowFunction_42", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
      if (isDirty_mapRef2RefFlowFunction_42) {
        binaryMapToRefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_42);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_44()) {
      auditInvocation(
          binaryMapToRefFlowFunction_44, "binaryMapToRefFlowFunction_44", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_44 = binaryMapToRefFlowFunction_44.map();
      if (isDirty_binaryMapToRefFlowFunction_44) {
        mapRef2RefFlowFunction_46.inputUpdated(binaryMapToRefFlowFunction_44);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      auditInvocation(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        mapRef2RefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_48()) {
      auditInvocation(mapRef2RefFlowFunction_48, "mapRef2RefFlowFunction_48", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_48 = mapRef2RefFlowFunction_48.map();
      if (isDirty_mapRef2RefFlowFunction_48) {
        mapRef2RefFlowFunction_50.inputUpdated(mapRef2RefFlowFunction_48);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_50()) {
      auditInvocation(mapRef2RefFlowFunction_50, "mapRef2RefFlowFunction_50", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
      if (isDirty_mapRef2RefFlowFunction_50) {
        binaryMapToRefFlowFunction_52.inputUpdated(mapRef2RefFlowFunction_50);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_52()) {
      auditInvocation(
          binaryMapToRefFlowFunction_52, "binaryMapToRefFlowFunction_52", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_52 = binaryMapToRefFlowFunction_52.map();
      if (isDirty_binaryMapToRefFlowFunction_52) {
        mapRef2RefFlowFunction_54.inputUpdated(binaryMapToRefFlowFunction_52);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      auditInvocation(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        mapRef2RefFlowFunction_55.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_55()) {
      auditInvocation(mapRef2RefFlowFunction_55, "mapRef2RefFlowFunction_55", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_55 = mapRef2RefFlowFunction_55.map();
      if (isDirty_mapRef2RefFlowFunction_55) {
        mapRef2RefFlowFunction_56.inputUpdated(mapRef2RefFlowFunction_55);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_56()) {
      auditInvocation(mapRef2RefFlowFunction_56, "mapRef2RefFlowFunction_56", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_56 = mapRef2RefFlowFunction_56.map();
      if (isDirty_mapRef2RefFlowFunction_56) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_56);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "push", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.push();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_58.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_64()) {
      auditInvocation(mapRef2RefFlowFunction_64, "mapRef2RefFlowFunction_64", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_64 = mapRef2RefFlowFunction_64.map();
      if (isDirty_mapRef2RefFlowFunction_64) {
        mapRef2RefFlowFunction_66.inputUpdated(mapRef2RefFlowFunction_64);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_66()) {
      auditInvocation(mapRef2RefFlowFunction_66, "mapRef2RefFlowFunction_66", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_66 = mapRef2RefFlowFunction_66.map();
      if (isDirty_mapRef2RefFlowFunction_66) {
        binaryMapToRefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_66);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_68()) {
      auditInvocation(
          binaryMapToRefFlowFunction_68, "binaryMapToRefFlowFunction_68", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_68 = binaryMapToRefFlowFunction_68.map();
      if (isDirty_binaryMapToRefFlowFunction_68) {
        mapRef2RefFlowFunction_70.inputUpdated(binaryMapToRefFlowFunction_68);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_70()) {
      auditInvocation(mapRef2RefFlowFunction_70, "mapRef2RefFlowFunction_70", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_70 = mapRef2RefFlowFunction_70.map();
      if (isDirty_mapRef2RefFlowFunction_70) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_70);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_72.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_58()) {
      auditInvocation(pushFlowFunction_58, "pushFlowFunction_58", "push", typedEvent);
      isDirty_pushFlowFunction_58 = pushFlowFunction_58.push();
    }
    if (guardCheck_pushFlowFunction_72()) {
      auditInvocation(pushFlowFunction_72, "pushFlowFunction_72", "push", typedEvent);
      isDirty_pushFlowFunction_72 = pushFlowFunction_72.push();
    }
  }

  private void handle_SinkDeregister_globalNetMtmListener(SinkDeregister typedEvent) {
    auditInvocation(globalNetMtmListener, "globalNetMtmListener", "unregisterSink", typedEvent);
    globalNetMtmListener.unregisterSink(typedEvent);
  }

  private void handle_SinkDeregister_instrumentNetMtmListener(SinkDeregister typedEvent) {
    auditInvocation(
        instrumentNetMtmListener, "instrumentNetMtmListener", "unregisterSink", typedEvent);
    instrumentNetMtmListener.unregisterSink(typedEvent);
  }

  private void handle_SinkRegistration_globalNetMtmListener(SinkRegistration typedEvent) {
    auditInvocation(globalNetMtmListener, "globalNetMtmListener", "sinkRegistration", typedEvent);
    globalNetMtmListener.sinkRegistration(typedEvent);
  }

  private void handle_SinkRegistration_instrumentNetMtmListener(SinkRegistration typedEvent) {
    auditInvocation(
        instrumentNetMtmListener, "instrumentNetMtmListener", "sinkRegistration", typedEvent);
    instrumentNetMtmListener.sinkRegistration(typedEvent);
  }

  private void handle_NamedFeedEvent_symbolFeed(NamedFeedEvent typedEvent) {
    auditInvocation(namedFeedTableNode_73, "namedFeedTableNode_73", "tableUpdate", typedEvent);
    isDirty_namedFeedTableNode_73 = namedFeedTableNode_73.tableUpdate(typedEvent);
    if (guardCheck_mapRef2RefFlowFunction_4()) {
      auditInvocation(mapRef2RefFlowFunction_4, "mapRef2RefFlowFunction_4", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_4 = mapRef2RefFlowFunction_4.map();
      if (isDirty_mapRef2RefFlowFunction_4) {
        mergeFlowFunction_6.inputStreamUpdated(mapRef2RefFlowFunction_4);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_5()) {
      auditInvocation(mapRef2RefFlowFunction_5, "mapRef2RefFlowFunction_5", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_5 = mapRef2RefFlowFunction_5.map();
      if (isDirty_mapRef2RefFlowFunction_5) {
        mergeFlowFunction_6.inputStreamUpdated(mapRef2RefFlowFunction_5);
      }
    }
    if (guardCheck_mergeFlowFunction_6()) {
      auditInvocation(mergeFlowFunction_6, "mergeFlowFunction_6", "publishMerge", typedEvent);
      isDirty_mergeFlowFunction_6 = mergeFlowFunction_6.publishMerge();
      if (isDirty_mergeFlowFunction_6) {
        filterFlowFunction_8.inputUpdated(mergeFlowFunction_6);
      }
    }
    if (guardCheck_filterFlowFunction_8()) {
      auditInvocation(filterFlowFunction_8, "filterFlowFunction_8", "filter", typedEvent);
      isDirty_filterFlowFunction_8 = filterFlowFunction_8.filter();
      if (isDirty_filterFlowFunction_8) {
        mapRef2RefFlowFunction_10.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_12.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_14.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_16.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_23.inputUpdated(filterFlowFunction_8);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      auditInvocation(mapRef2RefFlowFunction_10, "mapRef2RefFlowFunction_10", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_38.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_38.input2Updated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_38()) {
      auditInvocation(
          binaryMapToRefFlowFunction_38, "binaryMapToRefFlowFunction_38", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_38 = binaryMapToRefFlowFunction_38.map();
      if (isDirty_binaryMapToRefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(binaryMapToRefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_60.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_60.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_60()) {
      auditInvocation(
          binaryMapToRefFlowFunction_60, "binaryMapToRefFlowFunction_60", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_60 = binaryMapToRefFlowFunction_60.map();
      if (isDirty_binaryMapToRefFlowFunction_60) {
        mapRef2RefFlowFunction_62.inputUpdated(binaryMapToRefFlowFunction_60);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_23()) {
      auditInvocation(mapRef2RefFlowFunction_23, "mapRef2RefFlowFunction_23", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_23 = mapRef2RefFlowFunction_23.map();
      if (isDirty_mapRef2RefFlowFunction_23) {
        mapRef2RefFlowFunction_25.inputUpdated(mapRef2RefFlowFunction_23);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_25()) {
      auditInvocation(mapRef2RefFlowFunction_25, "mapRef2RefFlowFunction_25", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_25 = mapRef2RefFlowFunction_25.map();
      if (isDirty_mapRef2RefFlowFunction_25) {
        binaryMapToRefFlowFunction_27.inputUpdated(mapRef2RefFlowFunction_25);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_27()) {
      auditInvocation(
          binaryMapToRefFlowFunction_27, "binaryMapToRefFlowFunction_27", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_27 = binaryMapToRefFlowFunction_27.map();
      if (isDirty_binaryMapToRefFlowFunction_27) {
        joinFeeSnapshot.inputUpdated(binaryMapToRefFlowFunction_27);
      }
    }
    if (guardCheck_joinFeeSnapshot()) {
      auditInvocation(joinFeeSnapshot, "joinFeeSnapshot", "map", typedEvent);
      isDirty_joinFeeSnapshot = joinFeeSnapshot.map();
      if (isDirty_joinFeeSnapshot) {
        mapRef2RefFlowFunction_31.inputUpdated(joinFeeSnapshot);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_31()) {
      auditInvocation(mapRef2RefFlowFunction_31, "mapRef2RefFlowFunction_31", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_31 = mapRef2RefFlowFunction_31.map();
      if (isDirty_mapRef2RefFlowFunction_31) {
        mapRef2RefFlowFunction_33.inputUpdated(mapRef2RefFlowFunction_31);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_33()) {
      auditInvocation(mapRef2RefFlowFunction_33, "mapRef2RefFlowFunction_33", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_33 = mapRef2RefFlowFunction_33.map();
      if (isDirty_mapRef2RefFlowFunction_33) {
        binaryMapToRefFlowFunction_52.input2Updated(mapRef2RefFlowFunction_33);
        binaryMapToRefFlowFunction_68.input2Updated(mapRef2RefFlowFunction_33);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      auditInvocation(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        mapRef2RefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_42()) {
      auditInvocation(mapRef2RefFlowFunction_42, "mapRef2RefFlowFunction_42", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
      if (isDirty_mapRef2RefFlowFunction_42) {
        binaryMapToRefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_42);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_44()) {
      auditInvocation(
          binaryMapToRefFlowFunction_44, "binaryMapToRefFlowFunction_44", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_44 = binaryMapToRefFlowFunction_44.map();
      if (isDirty_binaryMapToRefFlowFunction_44) {
        mapRef2RefFlowFunction_46.inputUpdated(binaryMapToRefFlowFunction_44);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      auditInvocation(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        mapRef2RefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_48()) {
      auditInvocation(mapRef2RefFlowFunction_48, "mapRef2RefFlowFunction_48", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_48 = mapRef2RefFlowFunction_48.map();
      if (isDirty_mapRef2RefFlowFunction_48) {
        mapRef2RefFlowFunction_50.inputUpdated(mapRef2RefFlowFunction_48);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_50()) {
      auditInvocation(mapRef2RefFlowFunction_50, "mapRef2RefFlowFunction_50", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
      if (isDirty_mapRef2RefFlowFunction_50) {
        binaryMapToRefFlowFunction_52.inputUpdated(mapRef2RefFlowFunction_50);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_52()) {
      auditInvocation(
          binaryMapToRefFlowFunction_52, "binaryMapToRefFlowFunction_52", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_52 = binaryMapToRefFlowFunction_52.map();
      if (isDirty_binaryMapToRefFlowFunction_52) {
        mapRef2RefFlowFunction_54.inputUpdated(binaryMapToRefFlowFunction_52);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      auditInvocation(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        mapRef2RefFlowFunction_55.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_55()) {
      auditInvocation(mapRef2RefFlowFunction_55, "mapRef2RefFlowFunction_55", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_55 = mapRef2RefFlowFunction_55.map();
      if (isDirty_mapRef2RefFlowFunction_55) {
        mapRef2RefFlowFunction_56.inputUpdated(mapRef2RefFlowFunction_55);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_56()) {
      auditInvocation(mapRef2RefFlowFunction_56, "mapRef2RefFlowFunction_56", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_56 = mapRef2RefFlowFunction_56.map();
      if (isDirty_mapRef2RefFlowFunction_56) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_56);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "push", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.push();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_58.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_62()) {
      auditInvocation(mapRef2RefFlowFunction_62, "mapRef2RefFlowFunction_62", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_62 = mapRef2RefFlowFunction_62.map();
      if (isDirty_mapRef2RefFlowFunction_62) {
        mapRef2RefFlowFunction_64.inputUpdated(mapRef2RefFlowFunction_62);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_64()) {
      auditInvocation(mapRef2RefFlowFunction_64, "mapRef2RefFlowFunction_64", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_64 = mapRef2RefFlowFunction_64.map();
      if (isDirty_mapRef2RefFlowFunction_64) {
        mapRef2RefFlowFunction_66.inputUpdated(mapRef2RefFlowFunction_64);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_66()) {
      auditInvocation(mapRef2RefFlowFunction_66, "mapRef2RefFlowFunction_66", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_66 = mapRef2RefFlowFunction_66.map();
      if (isDirty_mapRef2RefFlowFunction_66) {
        binaryMapToRefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_66);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_68()) {
      auditInvocation(
          binaryMapToRefFlowFunction_68, "binaryMapToRefFlowFunction_68", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_68 = binaryMapToRefFlowFunction_68.map();
      if (isDirty_binaryMapToRefFlowFunction_68) {
        mapRef2RefFlowFunction_70.inputUpdated(binaryMapToRefFlowFunction_68);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_70()) {
      auditInvocation(mapRef2RefFlowFunction_70, "mapRef2RefFlowFunction_70", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_70 = mapRef2RefFlowFunction_70.map();
      if (isDirty_mapRef2RefFlowFunction_70) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_70);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_72.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_58()) {
      auditInvocation(pushFlowFunction_58, "pushFlowFunction_58", "push", typedEvent);
      isDirty_pushFlowFunction_58 = pushFlowFunction_58.push();
    }
    if (guardCheck_pushFlowFunction_72()) {
      auditInvocation(pushFlowFunction_72, "pushFlowFunction_72", "push", typedEvent);
      isDirty_pushFlowFunction_72 = pushFlowFunction_72.push();
    }
  }
  //FILTERED DISPATCH - END

  //EXPORTED SERVICE FUNCTIONS - START
  @Override
  public boolean configChanged(com.fluxtion.server.config.ConfigUpdate arg0) {
    beforeServiceCall(
        "public default boolean com.fluxtion.server.config.ConfigListener.configChanged(com.fluxtion.server.config.ConfigUpdate)");
    ExportFunctionAuditEvent typedEvent = functionAudit;
    auditInvocation(eventFeedBatcher, "eventFeedBatcher", "configChanged", typedEvent);
    isDirty_eventFeedBatcher = eventFeedBatcher.configChanged(arg0);
    if (guardCheck_mapRef2RefFlowFunction_4()) {
      auditInvocation(mapRef2RefFlowFunction_4, "mapRef2RefFlowFunction_4", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_4 = mapRef2RefFlowFunction_4.map();
      if (isDirty_mapRef2RefFlowFunction_4) {
        mergeFlowFunction_6.inputStreamUpdated(mapRef2RefFlowFunction_4);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_5()) {
      auditInvocation(mapRef2RefFlowFunction_5, "mapRef2RefFlowFunction_5", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_5 = mapRef2RefFlowFunction_5.map();
      if (isDirty_mapRef2RefFlowFunction_5) {
        mergeFlowFunction_6.inputStreamUpdated(mapRef2RefFlowFunction_5);
      }
    }
    if (guardCheck_mergeFlowFunction_6()) {
      auditInvocation(mergeFlowFunction_6, "mergeFlowFunction_6", "publishMerge", typedEvent);
      isDirty_mergeFlowFunction_6 = mergeFlowFunction_6.publishMerge();
      if (isDirty_mergeFlowFunction_6) {
        filterFlowFunction_8.inputUpdated(mergeFlowFunction_6);
      }
    }
    if (guardCheck_filterFlowFunction_8()) {
      auditInvocation(filterFlowFunction_8, "filterFlowFunction_8", "filter", typedEvent);
      isDirty_filterFlowFunction_8 = filterFlowFunction_8.filter();
      if (isDirty_filterFlowFunction_8) {
        mapRef2RefFlowFunction_10.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_12.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_14.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_16.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_23.inputUpdated(filterFlowFunction_8);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      auditInvocation(mapRef2RefFlowFunction_10, "mapRef2RefFlowFunction_10", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_38.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_38.input2Updated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_38()) {
      auditInvocation(
          binaryMapToRefFlowFunction_38, "binaryMapToRefFlowFunction_38", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_38 = binaryMapToRefFlowFunction_38.map();
      if (isDirty_binaryMapToRefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(binaryMapToRefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_60.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_60.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_60()) {
      auditInvocation(
          binaryMapToRefFlowFunction_60, "binaryMapToRefFlowFunction_60", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_60 = binaryMapToRefFlowFunction_60.map();
      if (isDirty_binaryMapToRefFlowFunction_60) {
        mapRef2RefFlowFunction_62.inputUpdated(binaryMapToRefFlowFunction_60);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_23()) {
      auditInvocation(mapRef2RefFlowFunction_23, "mapRef2RefFlowFunction_23", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_23 = mapRef2RefFlowFunction_23.map();
      if (isDirty_mapRef2RefFlowFunction_23) {
        mapRef2RefFlowFunction_25.inputUpdated(mapRef2RefFlowFunction_23);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_25()) {
      auditInvocation(mapRef2RefFlowFunction_25, "mapRef2RefFlowFunction_25", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_25 = mapRef2RefFlowFunction_25.map();
      if (isDirty_mapRef2RefFlowFunction_25) {
        binaryMapToRefFlowFunction_27.inputUpdated(mapRef2RefFlowFunction_25);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_27()) {
      auditInvocation(
          binaryMapToRefFlowFunction_27, "binaryMapToRefFlowFunction_27", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_27 = binaryMapToRefFlowFunction_27.map();
      if (isDirty_binaryMapToRefFlowFunction_27) {
        joinFeeSnapshot.inputUpdated(binaryMapToRefFlowFunction_27);
      }
    }
    if (guardCheck_joinFeeSnapshot()) {
      auditInvocation(joinFeeSnapshot, "joinFeeSnapshot", "map", typedEvent);
      isDirty_joinFeeSnapshot = joinFeeSnapshot.map();
      if (isDirty_joinFeeSnapshot) {
        mapRef2RefFlowFunction_31.inputUpdated(joinFeeSnapshot);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_31()) {
      auditInvocation(mapRef2RefFlowFunction_31, "mapRef2RefFlowFunction_31", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_31 = mapRef2RefFlowFunction_31.map();
      if (isDirty_mapRef2RefFlowFunction_31) {
        mapRef2RefFlowFunction_33.inputUpdated(mapRef2RefFlowFunction_31);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_33()) {
      auditInvocation(mapRef2RefFlowFunction_33, "mapRef2RefFlowFunction_33", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_33 = mapRef2RefFlowFunction_33.map();
      if (isDirty_mapRef2RefFlowFunction_33) {
        binaryMapToRefFlowFunction_52.input2Updated(mapRef2RefFlowFunction_33);
        binaryMapToRefFlowFunction_68.input2Updated(mapRef2RefFlowFunction_33);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      auditInvocation(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        mapRef2RefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_42()) {
      auditInvocation(mapRef2RefFlowFunction_42, "mapRef2RefFlowFunction_42", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
      if (isDirty_mapRef2RefFlowFunction_42) {
        binaryMapToRefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_42);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_44()) {
      auditInvocation(
          binaryMapToRefFlowFunction_44, "binaryMapToRefFlowFunction_44", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_44 = binaryMapToRefFlowFunction_44.map();
      if (isDirty_binaryMapToRefFlowFunction_44) {
        mapRef2RefFlowFunction_46.inputUpdated(binaryMapToRefFlowFunction_44);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      auditInvocation(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        mapRef2RefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_48()) {
      auditInvocation(mapRef2RefFlowFunction_48, "mapRef2RefFlowFunction_48", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_48 = mapRef2RefFlowFunction_48.map();
      if (isDirty_mapRef2RefFlowFunction_48) {
        mapRef2RefFlowFunction_50.inputUpdated(mapRef2RefFlowFunction_48);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_50()) {
      auditInvocation(mapRef2RefFlowFunction_50, "mapRef2RefFlowFunction_50", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
      if (isDirty_mapRef2RefFlowFunction_50) {
        binaryMapToRefFlowFunction_52.inputUpdated(mapRef2RefFlowFunction_50);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_52()) {
      auditInvocation(
          binaryMapToRefFlowFunction_52, "binaryMapToRefFlowFunction_52", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_52 = binaryMapToRefFlowFunction_52.map();
      if (isDirty_binaryMapToRefFlowFunction_52) {
        mapRef2RefFlowFunction_54.inputUpdated(binaryMapToRefFlowFunction_52);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      auditInvocation(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        mapRef2RefFlowFunction_55.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_55()) {
      auditInvocation(mapRef2RefFlowFunction_55, "mapRef2RefFlowFunction_55", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_55 = mapRef2RefFlowFunction_55.map();
      if (isDirty_mapRef2RefFlowFunction_55) {
        mapRef2RefFlowFunction_56.inputUpdated(mapRef2RefFlowFunction_55);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_56()) {
      auditInvocation(mapRef2RefFlowFunction_56, "mapRef2RefFlowFunction_56", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_56 = mapRef2RefFlowFunction_56.map();
      if (isDirty_mapRef2RefFlowFunction_56) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_56);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "push", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.push();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_58.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_62()) {
      auditInvocation(mapRef2RefFlowFunction_62, "mapRef2RefFlowFunction_62", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_62 = mapRef2RefFlowFunction_62.map();
      if (isDirty_mapRef2RefFlowFunction_62) {
        mapRef2RefFlowFunction_64.inputUpdated(mapRef2RefFlowFunction_62);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_64()) {
      auditInvocation(mapRef2RefFlowFunction_64, "mapRef2RefFlowFunction_64", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_64 = mapRef2RefFlowFunction_64.map();
      if (isDirty_mapRef2RefFlowFunction_64) {
        mapRef2RefFlowFunction_66.inputUpdated(mapRef2RefFlowFunction_64);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_66()) {
      auditInvocation(mapRef2RefFlowFunction_66, "mapRef2RefFlowFunction_66", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_66 = mapRef2RefFlowFunction_66.map();
      if (isDirty_mapRef2RefFlowFunction_66) {
        binaryMapToRefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_66);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_68()) {
      auditInvocation(
          binaryMapToRefFlowFunction_68, "binaryMapToRefFlowFunction_68", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_68 = binaryMapToRefFlowFunction_68.map();
      if (isDirty_binaryMapToRefFlowFunction_68) {
        mapRef2RefFlowFunction_70.inputUpdated(binaryMapToRefFlowFunction_68);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_70()) {
      auditInvocation(mapRef2RefFlowFunction_70, "mapRef2RefFlowFunction_70", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_70 = mapRef2RefFlowFunction_70.map();
      if (isDirty_mapRef2RefFlowFunction_70) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_70);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_72.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_58()) {
      auditInvocation(pushFlowFunction_58, "pushFlowFunction_58", "push", typedEvent);
      isDirty_pushFlowFunction_58 = pushFlowFunction_58.push();
    }
    if (guardCheck_pushFlowFunction_72()) {
      auditInvocation(pushFlowFunction_72, "pushFlowFunction_72", "push", typedEvent);
      isDirty_pushFlowFunction_72 = pushFlowFunction_72.push();
    }
    afterServiceCall();
    return true;
  }

  @Override
  public boolean initialConfig(com.fluxtion.server.config.ConfigMap arg0) {
    beforeServiceCall(
        "public boolean com.fluxtion.server.lib.pnl.calculator.EventFeedConnector.initialConfig(com.fluxtion.server.config.ConfigMap)");
    ExportFunctionAuditEvent typedEvent = functionAudit;
    auditInvocation(eventFeedBatcher, "eventFeedBatcher", "initialConfig", typedEvent);
    isDirty_eventFeedBatcher = eventFeedBatcher.initialConfig(arg0);
    if (guardCheck_mapRef2RefFlowFunction_4()) {
      auditInvocation(mapRef2RefFlowFunction_4, "mapRef2RefFlowFunction_4", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_4 = mapRef2RefFlowFunction_4.map();
      if (isDirty_mapRef2RefFlowFunction_4) {
        mergeFlowFunction_6.inputStreamUpdated(mapRef2RefFlowFunction_4);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_5()) {
      auditInvocation(mapRef2RefFlowFunction_5, "mapRef2RefFlowFunction_5", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_5 = mapRef2RefFlowFunction_5.map();
      if (isDirty_mapRef2RefFlowFunction_5) {
        mergeFlowFunction_6.inputStreamUpdated(mapRef2RefFlowFunction_5);
      }
    }
    if (guardCheck_mergeFlowFunction_6()) {
      auditInvocation(mergeFlowFunction_6, "mergeFlowFunction_6", "publishMerge", typedEvent);
      isDirty_mergeFlowFunction_6 = mergeFlowFunction_6.publishMerge();
      if (isDirty_mergeFlowFunction_6) {
        filterFlowFunction_8.inputUpdated(mergeFlowFunction_6);
      }
    }
    if (guardCheck_filterFlowFunction_8()) {
      auditInvocation(filterFlowFunction_8, "filterFlowFunction_8", "filter", typedEvent);
      isDirty_filterFlowFunction_8 = filterFlowFunction_8.filter();
      if (isDirty_filterFlowFunction_8) {
        mapRef2RefFlowFunction_10.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_12.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_14.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_16.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_23.inputUpdated(filterFlowFunction_8);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      auditInvocation(mapRef2RefFlowFunction_10, "mapRef2RefFlowFunction_10", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_38.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_38.input2Updated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_38()) {
      auditInvocation(
          binaryMapToRefFlowFunction_38, "binaryMapToRefFlowFunction_38", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_38 = binaryMapToRefFlowFunction_38.map();
      if (isDirty_binaryMapToRefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(binaryMapToRefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_60.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_60.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_60()) {
      auditInvocation(
          binaryMapToRefFlowFunction_60, "binaryMapToRefFlowFunction_60", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_60 = binaryMapToRefFlowFunction_60.map();
      if (isDirty_binaryMapToRefFlowFunction_60) {
        mapRef2RefFlowFunction_62.inputUpdated(binaryMapToRefFlowFunction_60);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_23()) {
      auditInvocation(mapRef2RefFlowFunction_23, "mapRef2RefFlowFunction_23", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_23 = mapRef2RefFlowFunction_23.map();
      if (isDirty_mapRef2RefFlowFunction_23) {
        mapRef2RefFlowFunction_25.inputUpdated(mapRef2RefFlowFunction_23);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_25()) {
      auditInvocation(mapRef2RefFlowFunction_25, "mapRef2RefFlowFunction_25", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_25 = mapRef2RefFlowFunction_25.map();
      if (isDirty_mapRef2RefFlowFunction_25) {
        binaryMapToRefFlowFunction_27.inputUpdated(mapRef2RefFlowFunction_25);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_27()) {
      auditInvocation(
          binaryMapToRefFlowFunction_27, "binaryMapToRefFlowFunction_27", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_27 = binaryMapToRefFlowFunction_27.map();
      if (isDirty_binaryMapToRefFlowFunction_27) {
        joinFeeSnapshot.inputUpdated(binaryMapToRefFlowFunction_27);
      }
    }
    if (guardCheck_joinFeeSnapshot()) {
      auditInvocation(joinFeeSnapshot, "joinFeeSnapshot", "map", typedEvent);
      isDirty_joinFeeSnapshot = joinFeeSnapshot.map();
      if (isDirty_joinFeeSnapshot) {
        mapRef2RefFlowFunction_31.inputUpdated(joinFeeSnapshot);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_31()) {
      auditInvocation(mapRef2RefFlowFunction_31, "mapRef2RefFlowFunction_31", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_31 = mapRef2RefFlowFunction_31.map();
      if (isDirty_mapRef2RefFlowFunction_31) {
        mapRef2RefFlowFunction_33.inputUpdated(mapRef2RefFlowFunction_31);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_33()) {
      auditInvocation(mapRef2RefFlowFunction_33, "mapRef2RefFlowFunction_33", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_33 = mapRef2RefFlowFunction_33.map();
      if (isDirty_mapRef2RefFlowFunction_33) {
        binaryMapToRefFlowFunction_52.input2Updated(mapRef2RefFlowFunction_33);
        binaryMapToRefFlowFunction_68.input2Updated(mapRef2RefFlowFunction_33);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      auditInvocation(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        mapRef2RefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_42()) {
      auditInvocation(mapRef2RefFlowFunction_42, "mapRef2RefFlowFunction_42", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
      if (isDirty_mapRef2RefFlowFunction_42) {
        binaryMapToRefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_42);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_44()) {
      auditInvocation(
          binaryMapToRefFlowFunction_44, "binaryMapToRefFlowFunction_44", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_44 = binaryMapToRefFlowFunction_44.map();
      if (isDirty_binaryMapToRefFlowFunction_44) {
        mapRef2RefFlowFunction_46.inputUpdated(binaryMapToRefFlowFunction_44);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      auditInvocation(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        mapRef2RefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_48()) {
      auditInvocation(mapRef2RefFlowFunction_48, "mapRef2RefFlowFunction_48", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_48 = mapRef2RefFlowFunction_48.map();
      if (isDirty_mapRef2RefFlowFunction_48) {
        mapRef2RefFlowFunction_50.inputUpdated(mapRef2RefFlowFunction_48);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_50()) {
      auditInvocation(mapRef2RefFlowFunction_50, "mapRef2RefFlowFunction_50", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
      if (isDirty_mapRef2RefFlowFunction_50) {
        binaryMapToRefFlowFunction_52.inputUpdated(mapRef2RefFlowFunction_50);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_52()) {
      auditInvocation(
          binaryMapToRefFlowFunction_52, "binaryMapToRefFlowFunction_52", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_52 = binaryMapToRefFlowFunction_52.map();
      if (isDirty_binaryMapToRefFlowFunction_52) {
        mapRef2RefFlowFunction_54.inputUpdated(binaryMapToRefFlowFunction_52);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      auditInvocation(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        mapRef2RefFlowFunction_55.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_55()) {
      auditInvocation(mapRef2RefFlowFunction_55, "mapRef2RefFlowFunction_55", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_55 = mapRef2RefFlowFunction_55.map();
      if (isDirty_mapRef2RefFlowFunction_55) {
        mapRef2RefFlowFunction_56.inputUpdated(mapRef2RefFlowFunction_55);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_56()) {
      auditInvocation(mapRef2RefFlowFunction_56, "mapRef2RefFlowFunction_56", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_56 = mapRef2RefFlowFunction_56.map();
      if (isDirty_mapRef2RefFlowFunction_56) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_56);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "push", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.push();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_58.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_62()) {
      auditInvocation(mapRef2RefFlowFunction_62, "mapRef2RefFlowFunction_62", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_62 = mapRef2RefFlowFunction_62.map();
      if (isDirty_mapRef2RefFlowFunction_62) {
        mapRef2RefFlowFunction_64.inputUpdated(mapRef2RefFlowFunction_62);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_64()) {
      auditInvocation(mapRef2RefFlowFunction_64, "mapRef2RefFlowFunction_64", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_64 = mapRef2RefFlowFunction_64.map();
      if (isDirty_mapRef2RefFlowFunction_64) {
        mapRef2RefFlowFunction_66.inputUpdated(mapRef2RefFlowFunction_64);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_66()) {
      auditInvocation(mapRef2RefFlowFunction_66, "mapRef2RefFlowFunction_66", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_66 = mapRef2RefFlowFunction_66.map();
      if (isDirty_mapRef2RefFlowFunction_66) {
        binaryMapToRefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_66);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_68()) {
      auditInvocation(
          binaryMapToRefFlowFunction_68, "binaryMapToRefFlowFunction_68", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_68 = binaryMapToRefFlowFunction_68.map();
      if (isDirty_binaryMapToRefFlowFunction_68) {
        mapRef2RefFlowFunction_70.inputUpdated(binaryMapToRefFlowFunction_68);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_70()) {
      auditInvocation(mapRef2RefFlowFunction_70, "mapRef2RefFlowFunction_70", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_70 = mapRef2RefFlowFunction_70.map();
      if (isDirty_mapRef2RefFlowFunction_70) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_70);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_72.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_58()) {
      auditInvocation(pushFlowFunction_58, "pushFlowFunction_58", "push", typedEvent);
      isDirty_pushFlowFunction_58 = pushFlowFunction_58.push();
    }
    if (guardCheck_pushFlowFunction_72()) {
      auditInvocation(pushFlowFunction_72, "pushFlowFunction_72", "push", typedEvent);
      isDirty_pushFlowFunction_72 = pushFlowFunction_72.push();
    }
    afterServiceCall();
    return true;
  }

  @Override
  public void deRegisterService(com.fluxtion.runtime.service.Service<?> arg0) {
    beforeServiceCall(
        "public void com.fluxtion.runtime.service.ServiceRegistryNode.deRegisterService(com.fluxtion.runtime.service.Service<?>)");
    ExportFunctionAuditEvent typedEvent = functionAudit;
    auditInvocation(serviceRegistry, "serviceRegistry", "deRegisterService", typedEvent);
    serviceRegistry.deRegisterService(arg0);
    afterServiceCall();
  }

  @Override
  public void registerService(com.fluxtion.runtime.service.Service<?> arg0) {
    beforeServiceCall(
        "public void com.fluxtion.runtime.service.ServiceRegistryNode.registerService(com.fluxtion.runtime.service.Service<?>)");
    ExportFunctionAuditEvent typedEvent = functionAudit;
    auditInvocation(serviceRegistry, "serviceRegistry", "registerService", typedEvent);
    serviceRegistry.registerService(arg0);
    afterServiceCall();
  }
  //EXPORTED SERVICE FUNCTIONS - END

  //EVENT BUFFERING - START
  public void bufferEvent(Object event) {
    throw new UnsupportedOperationException("bufferEvent not supported");
  }

  public void triggerCalculation() {
    throw new UnsupportedOperationException("triggerCalculation not supported");
  }
  //EVENT BUFFERING - END

  private void auditEvent(Object typedEvent) {
    clock.eventReceived(typedEvent);
    eventLogger.eventReceived(typedEvent);
    nodeNameLookup.eventReceived(typedEvent);
    serviceRegistry.eventReceived(typedEvent);
  }

  private void auditEvent(Event typedEvent) {
    clock.eventReceived(typedEvent);
    eventLogger.eventReceived(typedEvent);
    nodeNameLookup.eventReceived(typedEvent);
    serviceRegistry.eventReceived(typedEvent);
  }

  private void auditInvocation(Object node, String nodeName, String methodName, Object typedEvent) {
    eventLogger.nodeInvoked(node, nodeName, methodName, typedEvent);
  }

  private void initialiseAuditor(Auditor auditor) {
    auditor.init();
    auditor.nodeRegistered(callBackNode_74, "callBackNode_74");
    auditor.nodeRegistered(callBackNode_98, "callBackNode_98");
    auditor.nodeRegistered(callBackNode_153, "callBackNode_153");
    auditor.nodeRegistered(callbackDispatcher, "callbackDispatcher");
    auditor.nodeRegistered(callBackTriggerEvent_1, "callBackTriggerEvent_1");
    auditor.nodeRegistered(callBackTriggerEvent_2, "callBackTriggerEvent_2");
    auditor.nodeRegistered(callBackTriggerEvent_0, "callBackTriggerEvent_0");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_27, "binaryMapToRefFlowFunction_27");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_38, "binaryMapToRefFlowFunction_38");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_44, "binaryMapToRefFlowFunction_44");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_52, "binaryMapToRefFlowFunction_52");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_60, "binaryMapToRefFlowFunction_60");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_68, "binaryMapToRefFlowFunction_68");
    auditor.nodeRegistered(filterFlowFunction_8, "filterFlowFunction_8");
    auditor.nodeRegistered(flatMapFlowFunction_3, "flatMapFlowFunction_3");
    auditor.nodeRegistered(flatMapFlowFunction_17, "flatMapFlowFunction_17");
    auditor.nodeRegistered(flatMapSnapshotPositions, "flatMapSnapshotPositions");
    auditor.nodeRegistered(feeSnapshot, "feeSnapshot");
    auditor.nodeRegistered(groupBySnapshotPositions, "groupBySnapshotPositions");
    auditor.nodeRegistered(instrumentNetMtm, "instrumentNetMtm");
    auditor.nodeRegistered(joinFeeSnapshot, "joinFeeSnapshot");
    auditor.nodeRegistered(mapRef2RefFlowFunction_4, "mapRef2RefFlowFunction_4");
    auditor.nodeRegistered(mapRef2RefFlowFunction_5, "mapRef2RefFlowFunction_5");
    auditor.nodeRegistered(mapRef2RefFlowFunction_10, "mapRef2RefFlowFunction_10");
    auditor.nodeRegistered(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12");
    auditor.nodeRegistered(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14");
    auditor.nodeRegistered(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16");
    auditor.nodeRegistered(mapRef2RefFlowFunction_23, "mapRef2RefFlowFunction_23");
    auditor.nodeRegistered(mapRef2RefFlowFunction_25, "mapRef2RefFlowFunction_25");
    auditor.nodeRegistered(mapRef2RefFlowFunction_31, "mapRef2RefFlowFunction_31");
    auditor.nodeRegistered(mapRef2RefFlowFunction_33, "mapRef2RefFlowFunction_33");
    auditor.nodeRegistered(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40");
    auditor.nodeRegistered(mapRef2RefFlowFunction_42, "mapRef2RefFlowFunction_42");
    auditor.nodeRegistered(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46");
    auditor.nodeRegistered(mapRef2RefFlowFunction_48, "mapRef2RefFlowFunction_48");
    auditor.nodeRegistered(mapRef2RefFlowFunction_50, "mapRef2RefFlowFunction_50");
    auditor.nodeRegistered(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54");
    auditor.nodeRegistered(mapRef2RefFlowFunction_55, "mapRef2RefFlowFunction_55");
    auditor.nodeRegistered(mapRef2RefFlowFunction_56, "mapRef2RefFlowFunction_56");
    auditor.nodeRegistered(mapRef2RefFlowFunction_62, "mapRef2RefFlowFunction_62");
    auditor.nodeRegistered(mapRef2RefFlowFunction_64, "mapRef2RefFlowFunction_64");
    auditor.nodeRegistered(mapRef2RefFlowFunction_66, "mapRef2RefFlowFunction_66");
    auditor.nodeRegistered(mapRef2RefFlowFunction_70, "mapRef2RefFlowFunction_70");
    auditor.nodeRegistered(mergeFlowFunction_6, "mergeFlowFunction_6");
    auditor.nodeRegistered(peekFlowFunction_21, "peekFlowFunction_21");
    auditor.nodeRegistered(globalNetMtm, "globalNetMtm");
    auditor.nodeRegistered(pushFlowFunction_58, "pushFlowFunction_58");
    auditor.nodeRegistered(pushFlowFunction_72, "pushFlowFunction_72");
    auditor.nodeRegistered(emptyGroupBy_106, "emptyGroupBy_106");
    auditor.nodeRegistered(emptyGroupBy_129, "emptyGroupBy_129");
    auditor.nodeRegistered(emptyGroupBy_176, "emptyGroupBy_176");
    auditor.nodeRegistered(emptyGroupBy_239, "emptyGroupBy_239");
    auditor.nodeRegistered(emptyGroupBy_499, "emptyGroupBy_499");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_9, "groupByFlowFunctionWrapper_9");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_11, "groupByFlowFunctionWrapper_11");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_13, "groupByFlowFunctionWrapper_13");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_15, "groupByFlowFunctionWrapper_15");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_18, "groupByFlowFunctionWrapper_18");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_22, "groupByFlowFunctionWrapper_22");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_35, "groupByFlowFunctionWrapper_35");
    auditor.nodeRegistered(groupByMapFlowFunction_28, "groupByMapFlowFunction_28");
    auditor.nodeRegistered(groupByMapFlowFunction_32, "groupByMapFlowFunction_32");
    auditor.nodeRegistered(groupByMapFlowFunction_39, "groupByMapFlowFunction_39");
    auditor.nodeRegistered(groupByMapFlowFunction_45, "groupByMapFlowFunction_45");
    auditor.nodeRegistered(groupByMapFlowFunction_47, "groupByMapFlowFunction_47");
    auditor.nodeRegistered(groupByMapFlowFunction_53, "groupByMapFlowFunction_53");
    auditor.nodeRegistered(groupByMapFlowFunction_61, "groupByMapFlowFunction_61");
    auditor.nodeRegistered(groupByMapFlowFunction_63, "groupByMapFlowFunction_63");
    auditor.nodeRegistered(groupByMapFlowFunction_69, "groupByMapFlowFunction_69");
    auditor.nodeRegistered(leftJoin_51, "leftJoin_51");
    auditor.nodeRegistered(leftJoin_67, "leftJoin_67");
    auditor.nodeRegistered(outerJoin_26, "outerJoin_26");
    auditor.nodeRegistered(outerJoin_37, "outerJoin_37");
    auditor.nodeRegistered(outerJoin_43, "outerJoin_43");
    auditor.nodeRegistered(outerJoin_59, "outerJoin_59");
    auditor.nodeRegistered(defaultValue_24, "defaultValue_24");
    auditor.nodeRegistered(defaultValue_30, "defaultValue_30");
    auditor.nodeRegistered(defaultValue_41, "defaultValue_41");
    auditor.nodeRegistered(defaultValue_49, "defaultValue_49");
    auditor.nodeRegistered(defaultValue_65, "defaultValue_65");
    auditor.nodeRegistered(templateMessage_20, "templateMessage_20");
    auditor.nodeRegistered(mapTuple_431, "mapTuple_431");
    auditor.nodeRegistered(mapTuple_449, "mapTuple_449");
    auditor.nodeRegistered(mapTuple_454, "mapTuple_454");
    auditor.nodeRegistered(mapTuple_601, "mapTuple_601");
    auditor.nodeRegistered(mapTuple_607, "mapTuple_607");
    auditor.nodeRegistered(mapTuple_619, "mapTuple_619");
    auditor.nodeRegistered(subscriptionManager, "subscriptionManager");
    auditor.nodeRegistered(handlerPositionSnapshot, "handlerPositionSnapshot");
    auditor.nodeRegistered(
        handlerSignal_positionSnapshotReset, "handlerSignal_positionSnapshotReset");
    auditor.nodeRegistered(handlerSignal_positionUpdate, "handlerSignal_positionUpdate");
    auditor.nodeRegistered(handlerTrade, "handlerTrade");
    auditor.nodeRegistered(handlerTradeBatch, "handlerTradeBatch");
    auditor.nodeRegistered(context, "context");
    auditor.nodeRegistered(namedFeedTableNode_73, "namedFeedTableNode_73");
    auditor.nodeRegistered(globalNetMtmListener, "globalNetMtmListener");
    auditor.nodeRegistered(instrumentNetMtmListener, "instrumentNetMtmListener");
    auditor.nodeRegistered(derivedRateNode, "derivedRateNode");
    auditor.nodeRegistered(eventFeedBatcher, "eventFeedBatcher");
    auditor.nodeRegistered(positionCache, "positionCache");
    auditor.nodeRegistered(tradeSequenceFilter_7, "tradeSequenceFilter_7");
  }

  private void beforeServiceCall(String functionDescription) {
    functionAudit.setFunctionDescription(functionDescription);
    auditEvent(functionAudit);
    if (buffering) {
      triggerCalculation();
    }
    processing = true;
  }

  private void afterServiceCall() {
    afterEvent();
    callbackDispatcher.dispatchQueuedCallbacks();
    processing = false;
  }

  private void afterEvent() {

    clock.processingComplete();
    eventLogger.processingComplete();
    nodeNameLookup.processingComplete();
    serviceRegistry.processingComplete();
    isDirty_binaryMapToRefFlowFunction_27 = false;
    isDirty_binaryMapToRefFlowFunction_38 = false;
    isDirty_binaryMapToRefFlowFunction_44 = false;
    isDirty_binaryMapToRefFlowFunction_52 = false;
    isDirty_binaryMapToRefFlowFunction_60 = false;
    isDirty_binaryMapToRefFlowFunction_68 = false;
    isDirty_callBackNode_74 = false;
    isDirty_callBackNode_98 = false;
    isDirty_callBackNode_153 = false;
    isDirty_clock = false;
    isDirty_derivedRateNode = false;
    isDirty_eventFeedBatcher = false;
    isDirty_feeSnapshot = false;
    isDirty_filterFlowFunction_8 = false;
    isDirty_flatMapFlowFunction_3 = false;
    isDirty_flatMapFlowFunction_17 = false;
    isDirty_flatMapSnapshotPositions = false;
    isDirty_globalNetMtm = false;
    isDirty_groupBySnapshotPositions = false;
    isDirty_handlerPositionSnapshot = false;
    isDirty_handlerSignal_positionSnapshotReset = false;
    isDirty_handlerSignal_positionUpdate = false;
    isDirty_handlerTrade = false;
    isDirty_handlerTradeBatch = false;
    isDirty_instrumentNetMtm = false;
    isDirty_joinFeeSnapshot = false;
    isDirty_mapRef2RefFlowFunction_4 = false;
    isDirty_mapRef2RefFlowFunction_5 = false;
    isDirty_mapRef2RefFlowFunction_10 = false;
    isDirty_mapRef2RefFlowFunction_12 = false;
    isDirty_mapRef2RefFlowFunction_14 = false;
    isDirty_mapRef2RefFlowFunction_16 = false;
    isDirty_mapRef2RefFlowFunction_23 = false;
    isDirty_mapRef2RefFlowFunction_25 = false;
    isDirty_mapRef2RefFlowFunction_31 = false;
    isDirty_mapRef2RefFlowFunction_33 = false;
    isDirty_mapRef2RefFlowFunction_40 = false;
    isDirty_mapRef2RefFlowFunction_42 = false;
    isDirty_mapRef2RefFlowFunction_46 = false;
    isDirty_mapRef2RefFlowFunction_48 = false;
    isDirty_mapRef2RefFlowFunction_50 = false;
    isDirty_mapRef2RefFlowFunction_54 = false;
    isDirty_mapRef2RefFlowFunction_55 = false;
    isDirty_mapRef2RefFlowFunction_56 = false;
    isDirty_mapRef2RefFlowFunction_62 = false;
    isDirty_mapRef2RefFlowFunction_64 = false;
    isDirty_mapRef2RefFlowFunction_66 = false;
    isDirty_mapRef2RefFlowFunction_70 = false;
    isDirty_mergeFlowFunction_6 = false;
    isDirty_namedFeedTableNode_73 = false;
    isDirty_pushFlowFunction_58 = false;
    isDirty_pushFlowFunction_72 = false;
  }

  @Override
  public void batchPause() {
    auditEvent(Lifecycle.LifecycleEvent.BatchPause);
    processing = true;

    afterEvent();
    callbackDispatcher.dispatchQueuedCallbacks();
    processing = false;
  }

  @Override
  public void batchEnd() {
    auditEvent(Lifecycle.LifecycleEvent.BatchEnd);
    processing = true;

    afterEvent();
    callbackDispatcher.dispatchQueuedCallbacks();
    processing = false;
  }

  @Override
  public boolean isDirty(Object node) {
    return dirtySupplier(node).getAsBoolean();
  }

  @Override
  public BooleanSupplier dirtySupplier(Object node) {
    if (dirtyFlagSupplierMap.isEmpty()) {
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_27, () -> isDirty_binaryMapToRefFlowFunction_27);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_38, () -> isDirty_binaryMapToRefFlowFunction_38);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_44, () -> isDirty_binaryMapToRefFlowFunction_44);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_52, () -> isDirty_binaryMapToRefFlowFunction_52);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_60, () -> isDirty_binaryMapToRefFlowFunction_60);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_68, () -> isDirty_binaryMapToRefFlowFunction_68);
      dirtyFlagSupplierMap.put(callBackNode_153, () -> isDirty_callBackNode_153);
      dirtyFlagSupplierMap.put(callBackNode_74, () -> isDirty_callBackNode_74);
      dirtyFlagSupplierMap.put(callBackNode_98, () -> isDirty_callBackNode_98);
      dirtyFlagSupplierMap.put(clock, () -> isDirty_clock);
      dirtyFlagSupplierMap.put(derivedRateNode, () -> isDirty_derivedRateNode);
      dirtyFlagSupplierMap.put(eventFeedBatcher, () -> isDirty_eventFeedBatcher);
      dirtyFlagSupplierMap.put(feeSnapshot, () -> isDirty_feeSnapshot);
      dirtyFlagSupplierMap.put(filterFlowFunction_8, () -> isDirty_filterFlowFunction_8);
      dirtyFlagSupplierMap.put(flatMapFlowFunction_17, () -> isDirty_flatMapFlowFunction_17);
      dirtyFlagSupplierMap.put(flatMapFlowFunction_3, () -> isDirty_flatMapFlowFunction_3);
      dirtyFlagSupplierMap.put(flatMapSnapshotPositions, () -> isDirty_flatMapSnapshotPositions);
      dirtyFlagSupplierMap.put(globalNetMtm, () -> isDirty_globalNetMtm);
      dirtyFlagSupplierMap.put(groupBySnapshotPositions, () -> isDirty_groupBySnapshotPositions);
      dirtyFlagSupplierMap.put(handlerPositionSnapshot, () -> isDirty_handlerPositionSnapshot);
      dirtyFlagSupplierMap.put(
          handlerSignal_positionSnapshotReset, () -> isDirty_handlerSignal_positionSnapshotReset);
      dirtyFlagSupplierMap.put(
          handlerSignal_positionUpdate, () -> isDirty_handlerSignal_positionUpdate);
      dirtyFlagSupplierMap.put(handlerTrade, () -> isDirty_handlerTrade);
      dirtyFlagSupplierMap.put(handlerTradeBatch, () -> isDirty_handlerTradeBatch);
      dirtyFlagSupplierMap.put(instrumentNetMtm, () -> isDirty_instrumentNetMtm);
      dirtyFlagSupplierMap.put(joinFeeSnapshot, () -> isDirty_joinFeeSnapshot);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_10, () -> isDirty_mapRef2RefFlowFunction_10);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_12, () -> isDirty_mapRef2RefFlowFunction_12);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_14, () -> isDirty_mapRef2RefFlowFunction_14);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_16, () -> isDirty_mapRef2RefFlowFunction_16);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_23, () -> isDirty_mapRef2RefFlowFunction_23);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_25, () -> isDirty_mapRef2RefFlowFunction_25);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_31, () -> isDirty_mapRef2RefFlowFunction_31);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_33, () -> isDirty_mapRef2RefFlowFunction_33);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_4, () -> isDirty_mapRef2RefFlowFunction_4);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_40, () -> isDirty_mapRef2RefFlowFunction_40);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_42, () -> isDirty_mapRef2RefFlowFunction_42);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_46, () -> isDirty_mapRef2RefFlowFunction_46);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_48, () -> isDirty_mapRef2RefFlowFunction_48);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_5, () -> isDirty_mapRef2RefFlowFunction_5);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_50, () -> isDirty_mapRef2RefFlowFunction_50);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_54, () -> isDirty_mapRef2RefFlowFunction_54);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_55, () -> isDirty_mapRef2RefFlowFunction_55);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_56, () -> isDirty_mapRef2RefFlowFunction_56);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_62, () -> isDirty_mapRef2RefFlowFunction_62);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_64, () -> isDirty_mapRef2RefFlowFunction_64);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_66, () -> isDirty_mapRef2RefFlowFunction_66);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_70, () -> isDirty_mapRef2RefFlowFunction_70);
      dirtyFlagSupplierMap.put(mergeFlowFunction_6, () -> isDirty_mergeFlowFunction_6);
      dirtyFlagSupplierMap.put(namedFeedTableNode_73, () -> isDirty_namedFeedTableNode_73);
      dirtyFlagSupplierMap.put(pushFlowFunction_58, () -> isDirty_pushFlowFunction_58);
      dirtyFlagSupplierMap.put(pushFlowFunction_72, () -> isDirty_pushFlowFunction_72);
    }
    return dirtyFlagSupplierMap.getOrDefault(node, StaticEventProcessor.ALWAYS_FALSE);
  }

  @Override
  public void setDirty(Object node, boolean dirtyFlag) {
    if (dirtyFlagUpdateMap.isEmpty()) {
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_27, (b) -> isDirty_binaryMapToRefFlowFunction_27 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_38, (b) -> isDirty_binaryMapToRefFlowFunction_38 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_44, (b) -> isDirty_binaryMapToRefFlowFunction_44 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_52, (b) -> isDirty_binaryMapToRefFlowFunction_52 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_60, (b) -> isDirty_binaryMapToRefFlowFunction_60 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_68, (b) -> isDirty_binaryMapToRefFlowFunction_68 = b);
      dirtyFlagUpdateMap.put(callBackNode_153, (b) -> isDirty_callBackNode_153 = b);
      dirtyFlagUpdateMap.put(callBackNode_74, (b) -> isDirty_callBackNode_74 = b);
      dirtyFlagUpdateMap.put(callBackNode_98, (b) -> isDirty_callBackNode_98 = b);
      dirtyFlagUpdateMap.put(clock, (b) -> isDirty_clock = b);
      dirtyFlagUpdateMap.put(derivedRateNode, (b) -> isDirty_derivedRateNode = b);
      dirtyFlagUpdateMap.put(eventFeedBatcher, (b) -> isDirty_eventFeedBatcher = b);
      dirtyFlagUpdateMap.put(feeSnapshot, (b) -> isDirty_feeSnapshot = b);
      dirtyFlagUpdateMap.put(filterFlowFunction_8, (b) -> isDirty_filterFlowFunction_8 = b);
      dirtyFlagUpdateMap.put(flatMapFlowFunction_17, (b) -> isDirty_flatMapFlowFunction_17 = b);
      dirtyFlagUpdateMap.put(flatMapFlowFunction_3, (b) -> isDirty_flatMapFlowFunction_3 = b);
      dirtyFlagUpdateMap.put(flatMapSnapshotPositions, (b) -> isDirty_flatMapSnapshotPositions = b);
      dirtyFlagUpdateMap.put(globalNetMtm, (b) -> isDirty_globalNetMtm = b);
      dirtyFlagUpdateMap.put(groupBySnapshotPositions, (b) -> isDirty_groupBySnapshotPositions = b);
      dirtyFlagUpdateMap.put(handlerPositionSnapshot, (b) -> isDirty_handlerPositionSnapshot = b);
      dirtyFlagUpdateMap.put(
          handlerSignal_positionSnapshotReset,
          (b) -> isDirty_handlerSignal_positionSnapshotReset = b);
      dirtyFlagUpdateMap.put(
          handlerSignal_positionUpdate, (b) -> isDirty_handlerSignal_positionUpdate = b);
      dirtyFlagUpdateMap.put(handlerTrade, (b) -> isDirty_handlerTrade = b);
      dirtyFlagUpdateMap.put(handlerTradeBatch, (b) -> isDirty_handlerTradeBatch = b);
      dirtyFlagUpdateMap.put(instrumentNetMtm, (b) -> isDirty_instrumentNetMtm = b);
      dirtyFlagUpdateMap.put(joinFeeSnapshot, (b) -> isDirty_joinFeeSnapshot = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_10, (b) -> isDirty_mapRef2RefFlowFunction_10 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_12, (b) -> isDirty_mapRef2RefFlowFunction_12 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_14, (b) -> isDirty_mapRef2RefFlowFunction_14 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_16, (b) -> isDirty_mapRef2RefFlowFunction_16 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_23, (b) -> isDirty_mapRef2RefFlowFunction_23 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_25, (b) -> isDirty_mapRef2RefFlowFunction_25 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_31, (b) -> isDirty_mapRef2RefFlowFunction_31 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_33, (b) -> isDirty_mapRef2RefFlowFunction_33 = b);
      dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_4, (b) -> isDirty_mapRef2RefFlowFunction_4 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_40, (b) -> isDirty_mapRef2RefFlowFunction_40 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_42, (b) -> isDirty_mapRef2RefFlowFunction_42 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_46, (b) -> isDirty_mapRef2RefFlowFunction_46 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_48, (b) -> isDirty_mapRef2RefFlowFunction_48 = b);
      dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_5, (b) -> isDirty_mapRef2RefFlowFunction_5 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_50, (b) -> isDirty_mapRef2RefFlowFunction_50 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_54, (b) -> isDirty_mapRef2RefFlowFunction_54 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_55, (b) -> isDirty_mapRef2RefFlowFunction_55 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_56, (b) -> isDirty_mapRef2RefFlowFunction_56 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_62, (b) -> isDirty_mapRef2RefFlowFunction_62 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_64, (b) -> isDirty_mapRef2RefFlowFunction_64 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_66, (b) -> isDirty_mapRef2RefFlowFunction_66 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_70, (b) -> isDirty_mapRef2RefFlowFunction_70 = b);
      dirtyFlagUpdateMap.put(mergeFlowFunction_6, (b) -> isDirty_mergeFlowFunction_6 = b);
      dirtyFlagUpdateMap.put(namedFeedTableNode_73, (b) -> isDirty_namedFeedTableNode_73 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_58, (b) -> isDirty_pushFlowFunction_58 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_72, (b) -> isDirty_pushFlowFunction_72 = b);
    }
    dirtyFlagUpdateMap.get(node).accept(dirtyFlag);
  }

  private boolean guardCheck_eventLogger() {
    return isDirty_clock;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_27() {
    return isDirty_feeSnapshot | isDirty_mapRef2RefFlowFunction_25;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_38() {
    return isDirty_mapRef2RefFlowFunction_10 | isDirty_mapRef2RefFlowFunction_12;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_44() {
    return isDirty_groupBySnapshotPositions | isDirty_mapRef2RefFlowFunction_42;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_52() {
    return isDirty_mapRef2RefFlowFunction_33 | isDirty_mapRef2RefFlowFunction_50;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_60() {
    return isDirty_mapRef2RefFlowFunction_14 | isDirty_mapRef2RefFlowFunction_16;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_68() {
    return isDirty_mapRef2RefFlowFunction_33 | isDirty_mapRef2RefFlowFunction_66;
  }

  private boolean guardCheck_filterFlowFunction_8() {
    return isDirty_mergeFlowFunction_6;
  }

  private boolean guardCheck_flatMapFlowFunction_3() {
    return isDirty_callBackNode_74;
  }

  private boolean guardCheck_flatMapFlowFunction_17() {
    return isDirty_callBackNode_98;
  }

  private boolean guardCheck_flatMapSnapshotPositions() {
    return isDirty_callBackNode_153;
  }

  private boolean guardCheck_feeSnapshot() {
    return isDirty_flatMapFlowFunction_17
        | isDirty_handlerSignal_positionSnapshotReset
        | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_groupBySnapshotPositions() {
    return isDirty_flatMapSnapshotPositions
        | isDirty_handlerSignal_positionSnapshotReset
        | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_instrumentNetMtm() {
    return isDirty_mapRef2RefFlowFunction_70;
  }

  private boolean guardCheck_joinFeeSnapshot() {
    return isDirty_binaryMapToRefFlowFunction_27 | isDirty_handlerSignal_positionSnapshotReset;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_4() {
    return isDirty_eventFeedBatcher | isDirty_flatMapFlowFunction_3;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_5() {
    return isDirty_eventFeedBatcher | isDirty_handlerTrade;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_10() {
    return isDirty_filterFlowFunction_8 | isDirty_handlerSignal_positionSnapshotReset;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_12() {
    return isDirty_filterFlowFunction_8 | isDirty_handlerSignal_positionSnapshotReset;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_14() {
    return isDirty_filterFlowFunction_8 | isDirty_handlerSignal_positionSnapshotReset;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_16() {
    return isDirty_filterFlowFunction_8 | isDirty_handlerSignal_positionSnapshotReset;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_23() {
    return isDirty_filterFlowFunction_8;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_25() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_23;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_31() {
    return isDirty_joinFeeSnapshot;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_33() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_31;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_40() {
    return isDirty_binaryMapToRefFlowFunction_38;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_42() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_40;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_46() {
    return isDirty_binaryMapToRefFlowFunction_44;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_48() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_46;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_50() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_48;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_54() {
    return isDirty_binaryMapToRefFlowFunction_52 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_55() {
    return isDirty_mapRef2RefFlowFunction_54;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_56() {
    return isDirty_mapRef2RefFlowFunction_55;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_62() {
    return isDirty_binaryMapToRefFlowFunction_60;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_64() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_62;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_66() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_64;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_70() {
    return isDirty_binaryMapToRefFlowFunction_68 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mergeFlowFunction_6() {
    return isDirty_mapRef2RefFlowFunction_4 | isDirty_mapRef2RefFlowFunction_5;
  }

  private boolean guardCheck_peekFlowFunction_21() {
    return isDirty_feeSnapshot;
  }

  private boolean guardCheck_globalNetMtm() {
    return isDirty_mapRef2RefFlowFunction_56;
  }

  private boolean guardCheck_pushFlowFunction_58() {
    return isDirty_globalNetMtm;
  }

  private boolean guardCheck_pushFlowFunction_72() {
    return isDirty_instrumentNetMtm;
  }

  private boolean guardCheck_groupByMapFlowFunction_32() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_groupByMapFlowFunction_47() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_groupByMapFlowFunction_63() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_context() {
    return isDirty_clock;
  }

  private boolean guardCheck_globalNetMtmListener() {
    return isDirty_pushFlowFunction_58;
  }

  private boolean guardCheck_instrumentNetMtmListener() {
    return isDirty_pushFlowFunction_72;
  }

  private boolean guardCheck_derivedRateNode() {
    return isDirty_namedFeedTableNode_73;
  }

  private boolean guardCheck_eventFeedBatcher() {
    return isDirty_namedFeedTableNode_73;
  }

  private boolean guardCheck_positionCache() {
    return isDirty_globalNetMtm;
  }

  @Override
  public <T> T getNodeById(String id) throws NoSuchFieldException {
    return nodeNameLookup.getInstanceById(id);
  }

  @Override
  public <A extends Auditor> A getAuditorById(String id)
      throws NoSuchFieldException, IllegalAccessException {
    return (A) this.getClass().getField(id).get(this);
  }

  @Override
  public void addEventFeed(EventFeed eventProcessorFeed) {
    subscriptionManager.addEventProcessorFeed(eventProcessorFeed);
  }

  @Override
  public void removeEventFeed(EventFeed eventProcessorFeed) {
    subscriptionManager.removeEventProcessorFeed(eventProcessorFeed);
  }

  @Override
  public FluxtionPnlCalculator newInstance() {
    return new FluxtionPnlCalculator();
  }

  @Override
  public FluxtionPnlCalculator newInstance(Map<Object, Object> contextMap) {
    return new FluxtionPnlCalculator();
  }

  @Override
  public String getLastAuditLogRecord() {
    try {
      EventLogManager eventLogManager =
          (EventLogManager) this.getClass().getField(EventLogManager.NODE_NAME).get(this);
      return eventLogManager.lastRecordAsString();
    } catch (Throwable e) {
      return "";
    }
  }

  public void unKnownEventHandler(Object object) {
    unKnownEventHandler.accept(object);
  }

  @Override
  public <T> void setUnKnownEventHandler(Consumer<T> consumer) {
    unKnownEventHandler = consumer;
  }

  @Override
  public SubscriptionManager getSubscriptionManager() {
    return subscriptionManager;
  }
}
