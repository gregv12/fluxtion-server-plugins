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
import com.fluxtion.runtime.dataflow.aggregate.function.AggregateIdentityFlowFunction;
import com.fluxtion.runtime.dataflow.function.BinaryMapFlowFunction.BinaryMapToRefFlowFunction;
import com.fluxtion.runtime.dataflow.function.FilterFlowFunction;
import com.fluxtion.runtime.dataflow.function.FlatMapFlowFunction;
import com.fluxtion.runtime.dataflow.function.MapFlowFunction.MapRef2RefFlowFunction;
import com.fluxtion.runtime.dataflow.function.MergeFlowFunction;
import com.fluxtion.runtime.dataflow.function.PushFlowFunction;
import com.fluxtion.runtime.dataflow.groupby.GroupBy;
import com.fluxtion.runtime.dataflow.groupby.GroupBy.EmptyGroupBy;
import com.fluxtion.runtime.dataflow.groupby.GroupByFlowFunctionWrapper;
import com.fluxtion.runtime.dataflow.groupby.GroupByMapFlowFunction;
import com.fluxtion.runtime.dataflow.groupby.LeftJoin;
import com.fluxtion.runtime.dataflow.groupby.OuterJoin;
import com.fluxtion.runtime.dataflow.helpers.DefaultValue;
import com.fluxtion.runtime.dataflow.helpers.Mappers;
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
  private final CallBackNode callBackNode_65 = new CallBackNode<>(callBackTriggerEvent_0);
  private final InstanceCallbackEvent_1 callBackTriggerEvent_1 = new InstanceCallbackEvent_1();
  private final CallBackNode callBackNode_102 = new CallBackNode<>(callBackTriggerEvent_1);
  private final CallbackDispatcherImpl callbackDispatcher = new CallbackDispatcherImpl();
  public final Clock clock = new Clock();
  private final EmptyGroupBy emptyGroupBy_92 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_20 = new DefaultValue<>(emptyGroupBy_92);
  private final EmptyGroupBy emptyGroupBy_125 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_31 = new DefaultValue<>(emptyGroupBy_125);
  private final EmptyGroupBy emptyGroupBy_188 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_39 = new DefaultValue<>(emptyGroupBy_188);
  private final EmptyGroupBy emptyGroupBy_416 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_56 = new DefaultValue<>(emptyGroupBy_416);
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
          Trade::getDealtInstrument, Mappers::identity, FeeInstrumentPosMtmAggregate::new);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_25 =
      new GroupByFlowFunctionWrapper<>(
          InstrumentPosition::instrument, Mappers::identity, AggregateIdentityFlowFunction::new);
  private final LeftJoin leftJoin_43 = new LeftJoin();
  private final LeftJoin leftJoin_58 = new LeftJoin();
  private final MapTuple mapTuple_355 = new MapTuple<>(NetMarkToMarket::combine);
  private final GroupByMapFlowFunction groupByMapFlowFunction_45 =
      new GroupByMapFlowFunction(mapTuple_355::mapTuple);
  private final MapTuple mapTuple_366 =
      new MapTuple<>(InstrumentPosMtm::overwriteInstrumentPositionWithSnapshot);
  private final GroupByMapFlowFunction groupByMapFlowFunction_35 =
      new GroupByMapFlowFunction(mapTuple_366::mapTuple);
  private final MapTuple mapTuple_371 = new MapTuple<>(InstrumentPosMtm::merge);
  private final GroupByMapFlowFunction groupByMapFlowFunction_29 =
      new GroupByMapFlowFunction(mapTuple_371::mapTuple);
  private final MapTuple mapTuple_497 = new MapTuple<>(NetMarkToMarket::combine);
  private final GroupByMapFlowFunction groupByMapFlowFunction_60 =
      new GroupByMapFlowFunction(mapTuple_497::mapTuple);
  private final MapTuple mapTuple_508 = new MapTuple<>(InstrumentPosMtm::merge);
  private final GroupByMapFlowFunction groupByMapFlowFunction_52 =
      new GroupByMapFlowFunction(mapTuple_508::mapTuple);
  private final NamedFeedTableNode namedFeedTableNode_64 =
      new NamedFeedTableNode<>("symbolFeed", Symbol::symbolName);
  public final DerivedRateNode derivedRateNode = new DerivedRateNode(namedFeedTableNode_64);
  public final EventFeedConnector eventFeedBatcher = new EventFeedConnector(namedFeedTableNode_64);
  private final GroupByMapFlowFunction groupByMapFlowFunction_22 =
      new GroupByMapFlowFunction(derivedRateNode::calculateFeeMtm);
  private final GroupByMapFlowFunction groupByMapFlowFunction_37 =
      new GroupByMapFlowFunction(derivedRateNode::calculateInstrumentPosMtm);
  private final GroupByMapFlowFunction groupByMapFlowFunction_54 =
      new GroupByMapFlowFunction(derivedRateNode::calculateInstrumentPosMtm);
  public final NodeNameAuditor nodeNameLookup = new NodeNameAuditor();
  private final OuterJoin outerJoin_27 = new OuterJoin();
  private final OuterJoin outerJoin_33 = new OuterJoin();
  private final OuterJoin outerJoin_50 = new OuterJoin();
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
  public final MapRef2RefFlowFunction groupBySnapshotPositions =
      new MapRef2RefFlowFunction<>(
          flatMapSnapshotPositions, groupByFlowFunctionWrapper_25::aggregate);
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
  private final MergeFlowFunction mergeFlowFunction_17 =
      new MergeFlowFunction<>(Arrays.asList(handlerTrade, mapRef2RefFlowFunction_4));
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_19 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_17, groupByFlowFunctionWrapper_18::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_21 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_19, defaultValue_20::getOrDefault);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_23 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_21, groupByMapFlowFunction_22::mapValues);
  public final ServiceRegistryNode serviceRegistry = new ServiceRegistryNode();
  private final TradeSequenceFilter tradeSequenceFilter_7 = new TradeSequenceFilter();
  private final FilterFlowFunction filterFlowFunction_8 =
      new FilterFlowFunction<>(
          mergeFlowFunction_6, tradeSequenceFilter_7::checkTradeSequenceNumber);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_10 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_8, groupByFlowFunctionWrapper_9::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_12 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_8, groupByFlowFunctionWrapper_11::aggregate);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_28 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_10, mapRef2RefFlowFunction_12, outerJoin_27::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_14 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_8, groupByFlowFunctionWrapper_13::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_16 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_8, groupByFlowFunctionWrapper_15::aggregate);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_51 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_14, mapRef2RefFlowFunction_16, outerJoin_50::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_30 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_28, groupByMapFlowFunction_29::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_32 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_30, defaultValue_31::getOrDefault);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_34 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_32, groupBySnapshotPositions, outerJoin_33::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_36 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_34, groupByMapFlowFunction_35::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_38 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_36, groupByMapFlowFunction_37::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_40 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_38, defaultValue_39::getOrDefault);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_44 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_40, mapRef2RefFlowFunction_23, leftJoin_43::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_41 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_40, GroupBy<Object, Object>::toMap);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_46 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_44, groupByMapFlowFunction_45::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_47 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_46, GroupBy<Object, Object>::toMap);
  public final MapRef2RefFlowFunction globalNetMtm =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_47, NetMarkToMarket::markToMarketSum);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_53 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_51, groupByMapFlowFunction_52::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_55 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_53, groupByMapFlowFunction_54::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_57 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_55, defaultValue_56::getOrDefault);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_59 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_57, mapRef2RefFlowFunction_23, leftJoin_58::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_61 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_59, groupByMapFlowFunction_60::mapValues);
  public final MapRef2RefFlowFunction instrumentNetMtm =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_61, GroupBy<Object, Object>::toMap);
  private final PushFlowFunction pushFlowFunction_42 =
      new PushFlowFunction<>(mapRef2RefFlowFunction_41, positionCache::mtmUpdated);
  private final PushFlowFunction pushFlowFunction_49 =
      new PushFlowFunction<>(globalNetMtm, globalNetMtmListener::publish);
  private final PushFlowFunction pushFlowFunction_63 =
      new PushFlowFunction<>(instrumentNetMtm, instrumentNetMtmListener::publish);
  private final ExportFunctionAuditEvent functionAudit = new ExportFunctionAuditEvent();
  //Dirty flags
  private boolean initCalled = false;
  private boolean processing = false;
  private boolean buffering = false;
  private final IdentityHashMap<Object, BooleanSupplier> dirtyFlagSupplierMap =
      new IdentityHashMap<>(48);
  private final IdentityHashMap<Object, Consumer<Boolean>> dirtyFlagUpdateMap =
      new IdentityHashMap<>(48);

  private boolean isDirty_binaryMapToRefFlowFunction_28 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_34 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_44 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_51 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_59 = false;
  private boolean isDirty_callBackNode_65 = false;
  private boolean isDirty_callBackNode_102 = false;
  private boolean isDirty_clock = false;
  private boolean isDirty_derivedRateNode = false;
  private boolean isDirty_eventFeedBatcher = false;
  private boolean isDirty_filterFlowFunction_8 = false;
  private boolean isDirty_flatMapFlowFunction_3 = false;
  private boolean isDirty_flatMapSnapshotPositions = false;
  private boolean isDirty_globalNetMtm = false;
  private boolean isDirty_groupBySnapshotPositions = false;
  private boolean isDirty_handlerPositionSnapshot = false;
  private boolean isDirty_handlerSignal_positionSnapshotReset = false;
  private boolean isDirty_handlerSignal_positionUpdate = false;
  private boolean isDirty_handlerTrade = false;
  private boolean isDirty_handlerTradeBatch = false;
  private boolean isDirty_instrumentNetMtm = false;
  private boolean isDirty_mapRef2RefFlowFunction_4 = false;
  private boolean isDirty_mapRef2RefFlowFunction_5 = false;
  private boolean isDirty_mapRef2RefFlowFunction_10 = false;
  private boolean isDirty_mapRef2RefFlowFunction_12 = false;
  private boolean isDirty_mapRef2RefFlowFunction_14 = false;
  private boolean isDirty_mapRef2RefFlowFunction_16 = false;
  private boolean isDirty_mapRef2RefFlowFunction_19 = false;
  private boolean isDirty_mapRef2RefFlowFunction_21 = false;
  private boolean isDirty_mapRef2RefFlowFunction_23 = false;
  private boolean isDirty_mapRef2RefFlowFunction_30 = false;
  private boolean isDirty_mapRef2RefFlowFunction_32 = false;
  private boolean isDirty_mapRef2RefFlowFunction_36 = false;
  private boolean isDirty_mapRef2RefFlowFunction_38 = false;
  private boolean isDirty_mapRef2RefFlowFunction_40 = false;
  private boolean isDirty_mapRef2RefFlowFunction_41 = false;
  private boolean isDirty_mapRef2RefFlowFunction_46 = false;
  private boolean isDirty_mapRef2RefFlowFunction_47 = false;
  private boolean isDirty_mapRef2RefFlowFunction_53 = false;
  private boolean isDirty_mapRef2RefFlowFunction_55 = false;
  private boolean isDirty_mapRef2RefFlowFunction_57 = false;
  private boolean isDirty_mapRef2RefFlowFunction_61 = false;
  private boolean isDirty_mergeFlowFunction_6 = false;
  private boolean isDirty_mergeFlowFunction_17 = false;
  private boolean isDirty_namedFeedTableNode_64 = false;
  private boolean isDirty_pushFlowFunction_42 = false;
  private boolean isDirty_pushFlowFunction_49 = false;
  private boolean isDirty_pushFlowFunction_63 = false;

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
    binaryMapToRefFlowFunction_28.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_34.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_44.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_51.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_59.setEventProcessorContext(context);
    filterFlowFunction_8.setEventProcessorContext(context);
    flatMapFlowFunction_3.setFlatMapCompleteSignal("positionUpdate");
    flatMapFlowFunction_3.callback = callBackNode_65;
    flatMapFlowFunction_3.dirtyStateMonitor = callbackDispatcher;
    flatMapSnapshotPositions.callback = callBackNode_102;
    flatMapSnapshotPositions.dirtyStateMonitor = callbackDispatcher;
    globalNetMtm.setEventProcessorContext(context);
    groupBySnapshotPositions.setEventProcessorContext(context);
    groupBySnapshotPositions.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    groupBySnapshotPositions.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    instrumentNetMtm.setEventProcessorContext(context);
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
    mapRef2RefFlowFunction_19.setEventProcessorContext(context);
    mapRef2RefFlowFunction_19.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_21.setEventProcessorContext(context);
    mapRef2RefFlowFunction_23.setEventProcessorContext(context);
    mapRef2RefFlowFunction_23.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_23.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_30.setEventProcessorContext(context);
    mapRef2RefFlowFunction_32.setEventProcessorContext(context);
    mapRef2RefFlowFunction_32.setPublishTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_36.setEventProcessorContext(context);
    mapRef2RefFlowFunction_38.setEventProcessorContext(context);
    mapRef2RefFlowFunction_38.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_40.setEventProcessorContext(context);
    mapRef2RefFlowFunction_40.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_41.setEventProcessorContext(context);
    mapRef2RefFlowFunction_46.setEventProcessorContext(context);
    mapRef2RefFlowFunction_46.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_47.setEventProcessorContext(context);
    mapRef2RefFlowFunction_53.setEventProcessorContext(context);
    mapRef2RefFlowFunction_55.setEventProcessorContext(context);
    mapRef2RefFlowFunction_55.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_57.setEventProcessorContext(context);
    mapRef2RefFlowFunction_57.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_61.setEventProcessorContext(context);
    mapRef2RefFlowFunction_61.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mergeFlowFunction_6.dirtyStateMonitor = callbackDispatcher;
    mergeFlowFunction_17.dirtyStateMonitor = callbackDispatcher;
    pushFlowFunction_42.setEventProcessorContext(context);
    pushFlowFunction_49.setEventProcessorContext(context);
    pushFlowFunction_63.setEventProcessorContext(context);
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
    namedFeedTableNode_64.initialise();
    namedFeedTableNode_64.init();
    derivedRateNode.init();
    eventFeedBatcher.init();
    positionCache.init();
    handlerPositionSnapshot.init();
    flatMapSnapshotPositions.init();
    handlerSignal_positionSnapshotReset.init();
    handlerSignal_positionUpdate.init();
    groupBySnapshotPositions.initialiseEventStream();
    handlerTrade.init();
    handlerTradeBatch.init();
    flatMapFlowFunction_3.init();
    mapRef2RefFlowFunction_4.initialiseEventStream();
    mapRef2RefFlowFunction_5.initialiseEventStream();
    mapRef2RefFlowFunction_19.initialiseEventStream();
    mapRef2RefFlowFunction_21.initialiseEventStream();
    mapRef2RefFlowFunction_23.initialiseEventStream();
    tradeSequenceFilter_7.init();
    filterFlowFunction_8.initialiseEventStream();
    mapRef2RefFlowFunction_10.initialiseEventStream();
    mapRef2RefFlowFunction_12.initialiseEventStream();
    binaryMapToRefFlowFunction_28.initialiseEventStream();
    mapRef2RefFlowFunction_14.initialiseEventStream();
    mapRef2RefFlowFunction_16.initialiseEventStream();
    binaryMapToRefFlowFunction_51.initialiseEventStream();
    mapRef2RefFlowFunction_30.initialiseEventStream();
    mapRef2RefFlowFunction_32.initialiseEventStream();
    binaryMapToRefFlowFunction_34.initialiseEventStream();
    mapRef2RefFlowFunction_36.initialiseEventStream();
    mapRef2RefFlowFunction_38.initialiseEventStream();
    mapRef2RefFlowFunction_40.initialiseEventStream();
    binaryMapToRefFlowFunction_44.initialiseEventStream();
    mapRef2RefFlowFunction_41.initialiseEventStream();
    mapRef2RefFlowFunction_46.initialiseEventStream();
    mapRef2RefFlowFunction_47.initialiseEventStream();
    globalNetMtm.initialiseEventStream();
    mapRef2RefFlowFunction_53.initialiseEventStream();
    mapRef2RefFlowFunction_55.initialiseEventStream();
    mapRef2RefFlowFunction_57.initialiseEventStream();
    binaryMapToRefFlowFunction_59.initialiseEventStream();
    mapRef2RefFlowFunction_61.initialiseEventStream();
    instrumentNetMtm.initialiseEventStream();
    pushFlowFunction_42.initialiseEventStream();
    pushFlowFunction_49.initialiseEventStream();
    pushFlowFunction_63.initialiseEventStream();
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
    auditInvocation(callBackNode_65, "callBackNode_65", "onEvent", typedEvent);
    isDirty_callBackNode_65 = callBackNode_65.onEvent(typedEvent);
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
        mergeFlowFunction_17.inputStreamUpdated(mapRef2RefFlowFunction_4);
      }
    }
    if (guardCheck_mergeFlowFunction_6()) {
      auditInvocation(mergeFlowFunction_6, "mergeFlowFunction_6", "publishMerge", typedEvent);
      isDirty_mergeFlowFunction_6 = mergeFlowFunction_6.publishMerge();
      if (isDirty_mergeFlowFunction_6) {
        filterFlowFunction_8.inputUpdated(mergeFlowFunction_6);
      }
    }
    if (guardCheck_mergeFlowFunction_17()) {
      auditInvocation(mergeFlowFunction_17, "mergeFlowFunction_17", "publishMerge", typedEvent);
      isDirty_mergeFlowFunction_17 = mergeFlowFunction_17.publishMerge();
      if (isDirty_mergeFlowFunction_17) {
        mapRef2RefFlowFunction_19.inputUpdated(mergeFlowFunction_17);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_19()) {
      auditInvocation(mapRef2RefFlowFunction_19, "mapRef2RefFlowFunction_19", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_19 = mapRef2RefFlowFunction_19.map();
      if (isDirty_mapRef2RefFlowFunction_19) {
        mapRef2RefFlowFunction_21.inputUpdated(mapRef2RefFlowFunction_19);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_21()) {
      auditInvocation(mapRef2RefFlowFunction_21, "mapRef2RefFlowFunction_21", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_21 = mapRef2RefFlowFunction_21.map();
      if (isDirty_mapRef2RefFlowFunction_21) {
        mapRef2RefFlowFunction_23.inputUpdated(mapRef2RefFlowFunction_21);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_23()) {
      auditInvocation(mapRef2RefFlowFunction_23, "mapRef2RefFlowFunction_23", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_23 = mapRef2RefFlowFunction_23.map();
      if (isDirty_mapRef2RefFlowFunction_23) {
        binaryMapToRefFlowFunction_44.input2Updated(mapRef2RefFlowFunction_23);
        binaryMapToRefFlowFunction_59.input2Updated(mapRef2RefFlowFunction_23);
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
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      auditInvocation(mapRef2RefFlowFunction_10, "mapRef2RefFlowFunction_10", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_28.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_28.input2Updated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_28()) {
      auditInvocation(
          binaryMapToRefFlowFunction_28, "binaryMapToRefFlowFunction_28", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_28 = binaryMapToRefFlowFunction_28.map();
      if (isDirty_binaryMapToRefFlowFunction_28) {
        mapRef2RefFlowFunction_30.inputUpdated(binaryMapToRefFlowFunction_28);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_51.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_51.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_51()) {
      auditInvocation(
          binaryMapToRefFlowFunction_51, "binaryMapToRefFlowFunction_51", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_51 = binaryMapToRefFlowFunction_51.map();
      if (isDirty_binaryMapToRefFlowFunction_51) {
        mapRef2RefFlowFunction_53.inputUpdated(binaryMapToRefFlowFunction_51);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_30()) {
      auditInvocation(mapRef2RefFlowFunction_30, "mapRef2RefFlowFunction_30", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_30 = mapRef2RefFlowFunction_30.map();
      if (isDirty_mapRef2RefFlowFunction_30) {
        mapRef2RefFlowFunction_32.inputUpdated(mapRef2RefFlowFunction_30);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_32()) {
      auditInvocation(mapRef2RefFlowFunction_32, "mapRef2RefFlowFunction_32", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_32 = mapRef2RefFlowFunction_32.map();
      if (isDirty_mapRef2RefFlowFunction_32) {
        binaryMapToRefFlowFunction_34.inputUpdated(mapRef2RefFlowFunction_32);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_34()) {
      auditInvocation(
          binaryMapToRefFlowFunction_34, "binaryMapToRefFlowFunction_34", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_34 = binaryMapToRefFlowFunction_34.map();
      if (isDirty_binaryMapToRefFlowFunction_34) {
        mapRef2RefFlowFunction_36.inputUpdated(binaryMapToRefFlowFunction_34);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_36()) {
      auditInvocation(mapRef2RefFlowFunction_36, "mapRef2RefFlowFunction_36", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_36 = mapRef2RefFlowFunction_36.map();
      if (isDirty_mapRef2RefFlowFunction_36) {
        mapRef2RefFlowFunction_38.inputUpdated(mapRef2RefFlowFunction_36);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_38()) {
      auditInvocation(mapRef2RefFlowFunction_38, "mapRef2RefFlowFunction_38", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_38 = mapRef2RefFlowFunction_38.map();
      if (isDirty_mapRef2RefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(mapRef2RefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      auditInvocation(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        binaryMapToRefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_40);
        mapRef2RefFlowFunction_41.inputUpdated(mapRef2RefFlowFunction_40);
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
    if (guardCheck_mapRef2RefFlowFunction_41()) {
      auditInvocation(mapRef2RefFlowFunction_41, "mapRef2RefFlowFunction_41", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_41 = mapRef2RefFlowFunction_41.map();
      if (isDirty_mapRef2RefFlowFunction_41) {
        pushFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_41);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      auditInvocation(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        mapRef2RefFlowFunction_47.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_47()) {
      auditInvocation(mapRef2RefFlowFunction_47, "mapRef2RefFlowFunction_47", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_47 = mapRef2RefFlowFunction_47.map();
      if (isDirty_mapRef2RefFlowFunction_47) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_47);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "map", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_49.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_53()) {
      auditInvocation(mapRef2RefFlowFunction_53, "mapRef2RefFlowFunction_53", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_53 = mapRef2RefFlowFunction_53.map();
      if (isDirty_mapRef2RefFlowFunction_53) {
        mapRef2RefFlowFunction_55.inputUpdated(mapRef2RefFlowFunction_53);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_55()) {
      auditInvocation(mapRef2RefFlowFunction_55, "mapRef2RefFlowFunction_55", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_55 = mapRef2RefFlowFunction_55.map();
      if (isDirty_mapRef2RefFlowFunction_55) {
        mapRef2RefFlowFunction_57.inputUpdated(mapRef2RefFlowFunction_55);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_57()) {
      auditInvocation(mapRef2RefFlowFunction_57, "mapRef2RefFlowFunction_57", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_57 = mapRef2RefFlowFunction_57.map();
      if (isDirty_mapRef2RefFlowFunction_57) {
        binaryMapToRefFlowFunction_59.inputUpdated(mapRef2RefFlowFunction_57);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_59()) {
      auditInvocation(
          binaryMapToRefFlowFunction_59, "binaryMapToRefFlowFunction_59", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_59 = binaryMapToRefFlowFunction_59.map();
      if (isDirty_binaryMapToRefFlowFunction_59) {
        mapRef2RefFlowFunction_61.inputUpdated(binaryMapToRefFlowFunction_59);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_61()) {
      auditInvocation(mapRef2RefFlowFunction_61, "mapRef2RefFlowFunction_61", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_61 = mapRef2RefFlowFunction_61.map();
      if (isDirty_mapRef2RefFlowFunction_61) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_61);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_63.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_42()) {
      auditInvocation(pushFlowFunction_42, "pushFlowFunction_42", "push", typedEvent);
      isDirty_pushFlowFunction_42 = pushFlowFunction_42.push();
    }
    if (guardCheck_pushFlowFunction_49()) {
      auditInvocation(pushFlowFunction_49, "pushFlowFunction_49", "push", typedEvent);
      isDirty_pushFlowFunction_49 = pushFlowFunction_49.push();
    }
    if (guardCheck_pushFlowFunction_63()) {
      auditInvocation(pushFlowFunction_63, "pushFlowFunction_63", "push", typedEvent);
      isDirty_pushFlowFunction_63 = pushFlowFunction_63.push();
    }
    afterEvent();
  }

  public void handleEvent(InstanceCallbackEvent_1 typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(callBackNode_102, "callBackNode_102", "onEvent", typedEvent);
    isDirty_callBackNode_102 = callBackNode_102.onEvent(typedEvent);
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
        binaryMapToRefFlowFunction_34.input2Updated(groupBySnapshotPositions);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_34()) {
      auditInvocation(
          binaryMapToRefFlowFunction_34, "binaryMapToRefFlowFunction_34", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_34 = binaryMapToRefFlowFunction_34.map();
      if (isDirty_binaryMapToRefFlowFunction_34) {
        mapRef2RefFlowFunction_36.inputUpdated(binaryMapToRefFlowFunction_34);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_36()) {
      auditInvocation(mapRef2RefFlowFunction_36, "mapRef2RefFlowFunction_36", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_36 = mapRef2RefFlowFunction_36.map();
      if (isDirty_mapRef2RefFlowFunction_36) {
        mapRef2RefFlowFunction_38.inputUpdated(mapRef2RefFlowFunction_36);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_38()) {
      auditInvocation(mapRef2RefFlowFunction_38, "mapRef2RefFlowFunction_38", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_38 = mapRef2RefFlowFunction_38.map();
      if (isDirty_mapRef2RefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(mapRef2RefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      auditInvocation(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        binaryMapToRefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_40);
        mapRef2RefFlowFunction_41.inputUpdated(mapRef2RefFlowFunction_40);
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
    if (guardCheck_mapRef2RefFlowFunction_41()) {
      auditInvocation(mapRef2RefFlowFunction_41, "mapRef2RefFlowFunction_41", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_41 = mapRef2RefFlowFunction_41.map();
      if (isDirty_mapRef2RefFlowFunction_41) {
        pushFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_41);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      auditInvocation(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        mapRef2RefFlowFunction_47.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_47()) {
      auditInvocation(mapRef2RefFlowFunction_47, "mapRef2RefFlowFunction_47", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_47 = mapRef2RefFlowFunction_47.map();
      if (isDirty_mapRef2RefFlowFunction_47) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_47);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "map", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_49.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_42()) {
      auditInvocation(pushFlowFunction_42, "pushFlowFunction_42", "push", typedEvent);
      isDirty_pushFlowFunction_42 = pushFlowFunction_42.push();
    }
    if (guardCheck_pushFlowFunction_49()) {
      auditInvocation(pushFlowFunction_49, "pushFlowFunction_49", "push", typedEvent);
      isDirty_pushFlowFunction_49 = pushFlowFunction_49.push();
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
      mergeFlowFunction_17.inputStreamUpdated(handlerTrade);
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
    if (guardCheck_mergeFlowFunction_17()) {
      auditInvocation(mergeFlowFunction_17, "mergeFlowFunction_17", "publishMerge", typedEvent);
      isDirty_mergeFlowFunction_17 = mergeFlowFunction_17.publishMerge();
      if (isDirty_mergeFlowFunction_17) {
        mapRef2RefFlowFunction_19.inputUpdated(mergeFlowFunction_17);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_19()) {
      auditInvocation(mapRef2RefFlowFunction_19, "mapRef2RefFlowFunction_19", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_19 = mapRef2RefFlowFunction_19.map();
      if (isDirty_mapRef2RefFlowFunction_19) {
        mapRef2RefFlowFunction_21.inputUpdated(mapRef2RefFlowFunction_19);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_21()) {
      auditInvocation(mapRef2RefFlowFunction_21, "mapRef2RefFlowFunction_21", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_21 = mapRef2RefFlowFunction_21.map();
      if (isDirty_mapRef2RefFlowFunction_21) {
        mapRef2RefFlowFunction_23.inputUpdated(mapRef2RefFlowFunction_21);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_23()) {
      auditInvocation(mapRef2RefFlowFunction_23, "mapRef2RefFlowFunction_23", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_23 = mapRef2RefFlowFunction_23.map();
      if (isDirty_mapRef2RefFlowFunction_23) {
        binaryMapToRefFlowFunction_44.input2Updated(mapRef2RefFlowFunction_23);
        binaryMapToRefFlowFunction_59.input2Updated(mapRef2RefFlowFunction_23);
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
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      auditInvocation(mapRef2RefFlowFunction_10, "mapRef2RefFlowFunction_10", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_28.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_28.input2Updated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_28()) {
      auditInvocation(
          binaryMapToRefFlowFunction_28, "binaryMapToRefFlowFunction_28", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_28 = binaryMapToRefFlowFunction_28.map();
      if (isDirty_binaryMapToRefFlowFunction_28) {
        mapRef2RefFlowFunction_30.inputUpdated(binaryMapToRefFlowFunction_28);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_51.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_51.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_51()) {
      auditInvocation(
          binaryMapToRefFlowFunction_51, "binaryMapToRefFlowFunction_51", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_51 = binaryMapToRefFlowFunction_51.map();
      if (isDirty_binaryMapToRefFlowFunction_51) {
        mapRef2RefFlowFunction_53.inputUpdated(binaryMapToRefFlowFunction_51);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_30()) {
      auditInvocation(mapRef2RefFlowFunction_30, "mapRef2RefFlowFunction_30", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_30 = mapRef2RefFlowFunction_30.map();
      if (isDirty_mapRef2RefFlowFunction_30) {
        mapRef2RefFlowFunction_32.inputUpdated(mapRef2RefFlowFunction_30);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_32()) {
      auditInvocation(mapRef2RefFlowFunction_32, "mapRef2RefFlowFunction_32", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_32 = mapRef2RefFlowFunction_32.map();
      if (isDirty_mapRef2RefFlowFunction_32) {
        binaryMapToRefFlowFunction_34.inputUpdated(mapRef2RefFlowFunction_32);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_34()) {
      auditInvocation(
          binaryMapToRefFlowFunction_34, "binaryMapToRefFlowFunction_34", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_34 = binaryMapToRefFlowFunction_34.map();
      if (isDirty_binaryMapToRefFlowFunction_34) {
        mapRef2RefFlowFunction_36.inputUpdated(binaryMapToRefFlowFunction_34);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_36()) {
      auditInvocation(mapRef2RefFlowFunction_36, "mapRef2RefFlowFunction_36", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_36 = mapRef2RefFlowFunction_36.map();
      if (isDirty_mapRef2RefFlowFunction_36) {
        mapRef2RefFlowFunction_38.inputUpdated(mapRef2RefFlowFunction_36);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_38()) {
      auditInvocation(mapRef2RefFlowFunction_38, "mapRef2RefFlowFunction_38", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_38 = mapRef2RefFlowFunction_38.map();
      if (isDirty_mapRef2RefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(mapRef2RefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      auditInvocation(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        binaryMapToRefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_40);
        mapRef2RefFlowFunction_41.inputUpdated(mapRef2RefFlowFunction_40);
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
    if (guardCheck_mapRef2RefFlowFunction_41()) {
      auditInvocation(mapRef2RefFlowFunction_41, "mapRef2RefFlowFunction_41", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_41 = mapRef2RefFlowFunction_41.map();
      if (isDirty_mapRef2RefFlowFunction_41) {
        pushFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_41);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      auditInvocation(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        mapRef2RefFlowFunction_47.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_47()) {
      auditInvocation(mapRef2RefFlowFunction_47, "mapRef2RefFlowFunction_47", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_47 = mapRef2RefFlowFunction_47.map();
      if (isDirty_mapRef2RefFlowFunction_47) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_47);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "map", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_49.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_53()) {
      auditInvocation(mapRef2RefFlowFunction_53, "mapRef2RefFlowFunction_53", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_53 = mapRef2RefFlowFunction_53.map();
      if (isDirty_mapRef2RefFlowFunction_53) {
        mapRef2RefFlowFunction_55.inputUpdated(mapRef2RefFlowFunction_53);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_55()) {
      auditInvocation(mapRef2RefFlowFunction_55, "mapRef2RefFlowFunction_55", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_55 = mapRef2RefFlowFunction_55.map();
      if (isDirty_mapRef2RefFlowFunction_55) {
        mapRef2RefFlowFunction_57.inputUpdated(mapRef2RefFlowFunction_55);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_57()) {
      auditInvocation(mapRef2RefFlowFunction_57, "mapRef2RefFlowFunction_57", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_57 = mapRef2RefFlowFunction_57.map();
      if (isDirty_mapRef2RefFlowFunction_57) {
        binaryMapToRefFlowFunction_59.inputUpdated(mapRef2RefFlowFunction_57);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_59()) {
      auditInvocation(
          binaryMapToRefFlowFunction_59, "binaryMapToRefFlowFunction_59", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_59 = binaryMapToRefFlowFunction_59.map();
      if (isDirty_binaryMapToRefFlowFunction_59) {
        mapRef2RefFlowFunction_61.inputUpdated(binaryMapToRefFlowFunction_59);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_61()) {
      auditInvocation(mapRef2RefFlowFunction_61, "mapRef2RefFlowFunction_61", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_61 = mapRef2RefFlowFunction_61.map();
      if (isDirty_mapRef2RefFlowFunction_61) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_61);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_63.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_42()) {
      auditInvocation(pushFlowFunction_42, "pushFlowFunction_42", "push", typedEvent);
      isDirty_pushFlowFunction_42 = pushFlowFunction_42.push();
    }
    auditInvocation(positionCache, "positionCache", "tradeIn", typedEvent);
    positionCache.tradeIn(typedEvent);
    if (guardCheck_pushFlowFunction_49()) {
      auditInvocation(pushFlowFunction_49, "pushFlowFunction_49", "push", typedEvent);
      isDirty_pushFlowFunction_49 = pushFlowFunction_49.push();
    }
    if (guardCheck_pushFlowFunction_63()) {
      auditInvocation(pushFlowFunction_63, "pushFlowFunction_63", "push", typedEvent);
      isDirty_pushFlowFunction_63 = pushFlowFunction_63.push();
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
      groupBySnapshotPositions.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_19.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_10.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_12.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_14.publishTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_14.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_16.publishTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_16.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
    }
    if (guardCheck_groupBySnapshotPositions()) {
      auditInvocation(groupBySnapshotPositions, "groupBySnapshotPositions", "map", typedEvent);
      isDirty_groupBySnapshotPositions = groupBySnapshotPositions.map();
      if (isDirty_groupBySnapshotPositions) {
        binaryMapToRefFlowFunction_34.input2Updated(groupBySnapshotPositions);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_19()) {
      auditInvocation(mapRef2RefFlowFunction_19, "mapRef2RefFlowFunction_19", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_19 = mapRef2RefFlowFunction_19.map();
      if (isDirty_mapRef2RefFlowFunction_19) {
        mapRef2RefFlowFunction_21.inputUpdated(mapRef2RefFlowFunction_19);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_21()) {
      auditInvocation(mapRef2RefFlowFunction_21, "mapRef2RefFlowFunction_21", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_21 = mapRef2RefFlowFunction_21.map();
      if (isDirty_mapRef2RefFlowFunction_21) {
        mapRef2RefFlowFunction_23.inputUpdated(mapRef2RefFlowFunction_21);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_23()) {
      auditInvocation(mapRef2RefFlowFunction_23, "mapRef2RefFlowFunction_23", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_23 = mapRef2RefFlowFunction_23.map();
      if (isDirty_mapRef2RefFlowFunction_23) {
        binaryMapToRefFlowFunction_44.input2Updated(mapRef2RefFlowFunction_23);
        binaryMapToRefFlowFunction_59.input2Updated(mapRef2RefFlowFunction_23);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      auditInvocation(mapRef2RefFlowFunction_10, "mapRef2RefFlowFunction_10", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_28.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_28.input2Updated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_28()) {
      auditInvocation(
          binaryMapToRefFlowFunction_28, "binaryMapToRefFlowFunction_28", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_28 = binaryMapToRefFlowFunction_28.map();
      if (isDirty_binaryMapToRefFlowFunction_28) {
        mapRef2RefFlowFunction_30.inputUpdated(binaryMapToRefFlowFunction_28);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_51.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_51.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_51()) {
      auditInvocation(
          binaryMapToRefFlowFunction_51, "binaryMapToRefFlowFunction_51", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_51 = binaryMapToRefFlowFunction_51.map();
      if (isDirty_binaryMapToRefFlowFunction_51) {
        mapRef2RefFlowFunction_53.inputUpdated(binaryMapToRefFlowFunction_51);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_30()) {
      auditInvocation(mapRef2RefFlowFunction_30, "mapRef2RefFlowFunction_30", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_30 = mapRef2RefFlowFunction_30.map();
      if (isDirty_mapRef2RefFlowFunction_30) {
        mapRef2RefFlowFunction_32.inputUpdated(mapRef2RefFlowFunction_30);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_32()) {
      auditInvocation(mapRef2RefFlowFunction_32, "mapRef2RefFlowFunction_32", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_32 = mapRef2RefFlowFunction_32.map();
      if (isDirty_mapRef2RefFlowFunction_32) {
        binaryMapToRefFlowFunction_34.inputUpdated(mapRef2RefFlowFunction_32);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_34()) {
      auditInvocation(
          binaryMapToRefFlowFunction_34, "binaryMapToRefFlowFunction_34", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_34 = binaryMapToRefFlowFunction_34.map();
      if (isDirty_binaryMapToRefFlowFunction_34) {
        mapRef2RefFlowFunction_36.inputUpdated(binaryMapToRefFlowFunction_34);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_36()) {
      auditInvocation(mapRef2RefFlowFunction_36, "mapRef2RefFlowFunction_36", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_36 = mapRef2RefFlowFunction_36.map();
      if (isDirty_mapRef2RefFlowFunction_36) {
        mapRef2RefFlowFunction_38.inputUpdated(mapRef2RefFlowFunction_36);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_38()) {
      auditInvocation(mapRef2RefFlowFunction_38, "mapRef2RefFlowFunction_38", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_38 = mapRef2RefFlowFunction_38.map();
      if (isDirty_mapRef2RefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(mapRef2RefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      auditInvocation(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        binaryMapToRefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_40);
        mapRef2RefFlowFunction_41.inputUpdated(mapRef2RefFlowFunction_40);
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
    if (guardCheck_mapRef2RefFlowFunction_41()) {
      auditInvocation(mapRef2RefFlowFunction_41, "mapRef2RefFlowFunction_41", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_41 = mapRef2RefFlowFunction_41.map();
      if (isDirty_mapRef2RefFlowFunction_41) {
        pushFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_41);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      auditInvocation(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        mapRef2RefFlowFunction_47.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_47()) {
      auditInvocation(mapRef2RefFlowFunction_47, "mapRef2RefFlowFunction_47", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_47 = mapRef2RefFlowFunction_47.map();
      if (isDirty_mapRef2RefFlowFunction_47) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_47);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "map", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_49.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_53()) {
      auditInvocation(mapRef2RefFlowFunction_53, "mapRef2RefFlowFunction_53", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_53 = mapRef2RefFlowFunction_53.map();
      if (isDirty_mapRef2RefFlowFunction_53) {
        mapRef2RefFlowFunction_55.inputUpdated(mapRef2RefFlowFunction_53);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_55()) {
      auditInvocation(mapRef2RefFlowFunction_55, "mapRef2RefFlowFunction_55", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_55 = mapRef2RefFlowFunction_55.map();
      if (isDirty_mapRef2RefFlowFunction_55) {
        mapRef2RefFlowFunction_57.inputUpdated(mapRef2RefFlowFunction_55);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_57()) {
      auditInvocation(mapRef2RefFlowFunction_57, "mapRef2RefFlowFunction_57", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_57 = mapRef2RefFlowFunction_57.map();
      if (isDirty_mapRef2RefFlowFunction_57) {
        binaryMapToRefFlowFunction_59.inputUpdated(mapRef2RefFlowFunction_57);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_59()) {
      auditInvocation(
          binaryMapToRefFlowFunction_59, "binaryMapToRefFlowFunction_59", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_59 = binaryMapToRefFlowFunction_59.map();
      if (isDirty_binaryMapToRefFlowFunction_59) {
        mapRef2RefFlowFunction_61.inputUpdated(binaryMapToRefFlowFunction_59);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_61()) {
      auditInvocation(mapRef2RefFlowFunction_61, "mapRef2RefFlowFunction_61", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_61 = mapRef2RefFlowFunction_61.map();
      if (isDirty_mapRef2RefFlowFunction_61) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_61);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_63.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_42()) {
      auditInvocation(pushFlowFunction_42, "pushFlowFunction_42", "push", typedEvent);
      isDirty_pushFlowFunction_42 = pushFlowFunction_42.push();
    }
    if (guardCheck_pushFlowFunction_49()) {
      auditInvocation(pushFlowFunction_49, "pushFlowFunction_49", "push", typedEvent);
      isDirty_pushFlowFunction_49 = pushFlowFunction_49.push();
    }
    if (guardCheck_pushFlowFunction_63()) {
      auditInvocation(pushFlowFunction_63, "pushFlowFunction_63", "push", typedEvent);
      isDirty_pushFlowFunction_63 = pushFlowFunction_63.push();
    }
  }

  private void handle_Signal_positionUpdate(Signal typedEvent) {
    auditInvocation(
        handlerSignal_positionUpdate, "handlerSignal_positionUpdate", "onEvent", typedEvent);
    isDirty_handlerSignal_positionUpdate = handlerSignal_positionUpdate.onEvent(typedEvent);
    if (isDirty_handlerSignal_positionUpdate) {
      groupBySnapshotPositions.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_23.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_23.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_32.publishTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_38.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_40.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_46.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_55.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_57.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_61.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
    }
    if (guardCheck_groupBySnapshotPositions()) {
      auditInvocation(groupBySnapshotPositions, "groupBySnapshotPositions", "map", typedEvent);
      isDirty_groupBySnapshotPositions = groupBySnapshotPositions.map();
      if (isDirty_groupBySnapshotPositions) {
        binaryMapToRefFlowFunction_34.input2Updated(groupBySnapshotPositions);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_23()) {
      auditInvocation(mapRef2RefFlowFunction_23, "mapRef2RefFlowFunction_23", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_23 = mapRef2RefFlowFunction_23.map();
      if (isDirty_mapRef2RefFlowFunction_23) {
        binaryMapToRefFlowFunction_44.input2Updated(mapRef2RefFlowFunction_23);
        binaryMapToRefFlowFunction_59.input2Updated(mapRef2RefFlowFunction_23);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_32()) {
      auditInvocation(mapRef2RefFlowFunction_32, "mapRef2RefFlowFunction_32", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_32 = mapRef2RefFlowFunction_32.map();
      if (isDirty_mapRef2RefFlowFunction_32) {
        binaryMapToRefFlowFunction_34.inputUpdated(mapRef2RefFlowFunction_32);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_34()) {
      auditInvocation(
          binaryMapToRefFlowFunction_34, "binaryMapToRefFlowFunction_34", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_34 = binaryMapToRefFlowFunction_34.map();
      if (isDirty_binaryMapToRefFlowFunction_34) {
        mapRef2RefFlowFunction_36.inputUpdated(binaryMapToRefFlowFunction_34);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_36()) {
      auditInvocation(mapRef2RefFlowFunction_36, "mapRef2RefFlowFunction_36", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_36 = mapRef2RefFlowFunction_36.map();
      if (isDirty_mapRef2RefFlowFunction_36) {
        mapRef2RefFlowFunction_38.inputUpdated(mapRef2RefFlowFunction_36);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_38()) {
      auditInvocation(mapRef2RefFlowFunction_38, "mapRef2RefFlowFunction_38", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_38 = mapRef2RefFlowFunction_38.map();
      if (isDirty_mapRef2RefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(mapRef2RefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      auditInvocation(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        binaryMapToRefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_40);
        mapRef2RefFlowFunction_41.inputUpdated(mapRef2RefFlowFunction_40);
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
    if (guardCheck_mapRef2RefFlowFunction_41()) {
      auditInvocation(mapRef2RefFlowFunction_41, "mapRef2RefFlowFunction_41", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_41 = mapRef2RefFlowFunction_41.map();
      if (isDirty_mapRef2RefFlowFunction_41) {
        pushFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_41);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      auditInvocation(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        mapRef2RefFlowFunction_47.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_47()) {
      auditInvocation(mapRef2RefFlowFunction_47, "mapRef2RefFlowFunction_47", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_47 = mapRef2RefFlowFunction_47.map();
      if (isDirty_mapRef2RefFlowFunction_47) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_47);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "map", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_49.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_55()) {
      auditInvocation(mapRef2RefFlowFunction_55, "mapRef2RefFlowFunction_55", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_55 = mapRef2RefFlowFunction_55.map();
      if (isDirty_mapRef2RefFlowFunction_55) {
        mapRef2RefFlowFunction_57.inputUpdated(mapRef2RefFlowFunction_55);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_57()) {
      auditInvocation(mapRef2RefFlowFunction_57, "mapRef2RefFlowFunction_57", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_57 = mapRef2RefFlowFunction_57.map();
      if (isDirty_mapRef2RefFlowFunction_57) {
        binaryMapToRefFlowFunction_59.inputUpdated(mapRef2RefFlowFunction_57);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_59()) {
      auditInvocation(
          binaryMapToRefFlowFunction_59, "binaryMapToRefFlowFunction_59", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_59 = binaryMapToRefFlowFunction_59.map();
      if (isDirty_binaryMapToRefFlowFunction_59) {
        mapRef2RefFlowFunction_61.inputUpdated(binaryMapToRefFlowFunction_59);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_61()) {
      auditInvocation(mapRef2RefFlowFunction_61, "mapRef2RefFlowFunction_61", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_61 = mapRef2RefFlowFunction_61.map();
      if (isDirty_mapRef2RefFlowFunction_61) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_61);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_63.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_42()) {
      auditInvocation(pushFlowFunction_42, "pushFlowFunction_42", "push", typedEvent);
      isDirty_pushFlowFunction_42 = pushFlowFunction_42.push();
    }
    if (guardCheck_pushFlowFunction_49()) {
      auditInvocation(pushFlowFunction_49, "pushFlowFunction_49", "push", typedEvent);
      isDirty_pushFlowFunction_49 = pushFlowFunction_49.push();
    }
    if (guardCheck_pushFlowFunction_63()) {
      auditInvocation(pushFlowFunction_63, "pushFlowFunction_63", "push", typedEvent);
      isDirty_pushFlowFunction_63 = pushFlowFunction_63.push();
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
    auditInvocation(namedFeedTableNode_64, "namedFeedTableNode_64", "tableUpdate", typedEvent);
    isDirty_namedFeedTableNode_64 = namedFeedTableNode_64.tableUpdate(typedEvent);
    if (guardCheck_mapRef2RefFlowFunction_4()) {
      auditInvocation(mapRef2RefFlowFunction_4, "mapRef2RefFlowFunction_4", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_4 = mapRef2RefFlowFunction_4.map();
      if (isDirty_mapRef2RefFlowFunction_4) {
        mergeFlowFunction_6.inputStreamUpdated(mapRef2RefFlowFunction_4);
        mergeFlowFunction_17.inputStreamUpdated(mapRef2RefFlowFunction_4);
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
    if (guardCheck_mergeFlowFunction_17()) {
      auditInvocation(mergeFlowFunction_17, "mergeFlowFunction_17", "publishMerge", typedEvent);
      isDirty_mergeFlowFunction_17 = mergeFlowFunction_17.publishMerge();
      if (isDirty_mergeFlowFunction_17) {
        mapRef2RefFlowFunction_19.inputUpdated(mergeFlowFunction_17);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_19()) {
      auditInvocation(mapRef2RefFlowFunction_19, "mapRef2RefFlowFunction_19", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_19 = mapRef2RefFlowFunction_19.map();
      if (isDirty_mapRef2RefFlowFunction_19) {
        mapRef2RefFlowFunction_21.inputUpdated(mapRef2RefFlowFunction_19);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_21()) {
      auditInvocation(mapRef2RefFlowFunction_21, "mapRef2RefFlowFunction_21", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_21 = mapRef2RefFlowFunction_21.map();
      if (isDirty_mapRef2RefFlowFunction_21) {
        mapRef2RefFlowFunction_23.inputUpdated(mapRef2RefFlowFunction_21);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_23()) {
      auditInvocation(mapRef2RefFlowFunction_23, "mapRef2RefFlowFunction_23", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_23 = mapRef2RefFlowFunction_23.map();
      if (isDirty_mapRef2RefFlowFunction_23) {
        binaryMapToRefFlowFunction_44.input2Updated(mapRef2RefFlowFunction_23);
        binaryMapToRefFlowFunction_59.input2Updated(mapRef2RefFlowFunction_23);
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
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      auditInvocation(mapRef2RefFlowFunction_10, "mapRef2RefFlowFunction_10", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_28.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_28.input2Updated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_28()) {
      auditInvocation(
          binaryMapToRefFlowFunction_28, "binaryMapToRefFlowFunction_28", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_28 = binaryMapToRefFlowFunction_28.map();
      if (isDirty_binaryMapToRefFlowFunction_28) {
        mapRef2RefFlowFunction_30.inputUpdated(binaryMapToRefFlowFunction_28);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_51.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_51.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_51()) {
      auditInvocation(
          binaryMapToRefFlowFunction_51, "binaryMapToRefFlowFunction_51", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_51 = binaryMapToRefFlowFunction_51.map();
      if (isDirty_binaryMapToRefFlowFunction_51) {
        mapRef2RefFlowFunction_53.inputUpdated(binaryMapToRefFlowFunction_51);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_30()) {
      auditInvocation(mapRef2RefFlowFunction_30, "mapRef2RefFlowFunction_30", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_30 = mapRef2RefFlowFunction_30.map();
      if (isDirty_mapRef2RefFlowFunction_30) {
        mapRef2RefFlowFunction_32.inputUpdated(mapRef2RefFlowFunction_30);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_32()) {
      auditInvocation(mapRef2RefFlowFunction_32, "mapRef2RefFlowFunction_32", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_32 = mapRef2RefFlowFunction_32.map();
      if (isDirty_mapRef2RefFlowFunction_32) {
        binaryMapToRefFlowFunction_34.inputUpdated(mapRef2RefFlowFunction_32);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_34()) {
      auditInvocation(
          binaryMapToRefFlowFunction_34, "binaryMapToRefFlowFunction_34", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_34 = binaryMapToRefFlowFunction_34.map();
      if (isDirty_binaryMapToRefFlowFunction_34) {
        mapRef2RefFlowFunction_36.inputUpdated(binaryMapToRefFlowFunction_34);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_36()) {
      auditInvocation(mapRef2RefFlowFunction_36, "mapRef2RefFlowFunction_36", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_36 = mapRef2RefFlowFunction_36.map();
      if (isDirty_mapRef2RefFlowFunction_36) {
        mapRef2RefFlowFunction_38.inputUpdated(mapRef2RefFlowFunction_36);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_38()) {
      auditInvocation(mapRef2RefFlowFunction_38, "mapRef2RefFlowFunction_38", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_38 = mapRef2RefFlowFunction_38.map();
      if (isDirty_mapRef2RefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(mapRef2RefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      auditInvocation(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        binaryMapToRefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_40);
        mapRef2RefFlowFunction_41.inputUpdated(mapRef2RefFlowFunction_40);
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
    if (guardCheck_mapRef2RefFlowFunction_41()) {
      auditInvocation(mapRef2RefFlowFunction_41, "mapRef2RefFlowFunction_41", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_41 = mapRef2RefFlowFunction_41.map();
      if (isDirty_mapRef2RefFlowFunction_41) {
        pushFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_41);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      auditInvocation(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        mapRef2RefFlowFunction_47.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_47()) {
      auditInvocation(mapRef2RefFlowFunction_47, "mapRef2RefFlowFunction_47", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_47 = mapRef2RefFlowFunction_47.map();
      if (isDirty_mapRef2RefFlowFunction_47) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_47);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "map", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_49.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_53()) {
      auditInvocation(mapRef2RefFlowFunction_53, "mapRef2RefFlowFunction_53", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_53 = mapRef2RefFlowFunction_53.map();
      if (isDirty_mapRef2RefFlowFunction_53) {
        mapRef2RefFlowFunction_55.inputUpdated(mapRef2RefFlowFunction_53);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_55()) {
      auditInvocation(mapRef2RefFlowFunction_55, "mapRef2RefFlowFunction_55", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_55 = mapRef2RefFlowFunction_55.map();
      if (isDirty_mapRef2RefFlowFunction_55) {
        mapRef2RefFlowFunction_57.inputUpdated(mapRef2RefFlowFunction_55);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_57()) {
      auditInvocation(mapRef2RefFlowFunction_57, "mapRef2RefFlowFunction_57", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_57 = mapRef2RefFlowFunction_57.map();
      if (isDirty_mapRef2RefFlowFunction_57) {
        binaryMapToRefFlowFunction_59.inputUpdated(mapRef2RefFlowFunction_57);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_59()) {
      auditInvocation(
          binaryMapToRefFlowFunction_59, "binaryMapToRefFlowFunction_59", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_59 = binaryMapToRefFlowFunction_59.map();
      if (isDirty_binaryMapToRefFlowFunction_59) {
        mapRef2RefFlowFunction_61.inputUpdated(binaryMapToRefFlowFunction_59);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_61()) {
      auditInvocation(mapRef2RefFlowFunction_61, "mapRef2RefFlowFunction_61", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_61 = mapRef2RefFlowFunction_61.map();
      if (isDirty_mapRef2RefFlowFunction_61) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_61);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_63.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_42()) {
      auditInvocation(pushFlowFunction_42, "pushFlowFunction_42", "push", typedEvent);
      isDirty_pushFlowFunction_42 = pushFlowFunction_42.push();
    }
    if (guardCheck_pushFlowFunction_49()) {
      auditInvocation(pushFlowFunction_49, "pushFlowFunction_49", "push", typedEvent);
      isDirty_pushFlowFunction_49 = pushFlowFunction_49.push();
    }
    if (guardCheck_pushFlowFunction_63()) {
      auditInvocation(pushFlowFunction_63, "pushFlowFunction_63", "push", typedEvent);
      isDirty_pushFlowFunction_63 = pushFlowFunction_63.push();
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
        mergeFlowFunction_17.inputStreamUpdated(mapRef2RefFlowFunction_4);
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
    if (guardCheck_mergeFlowFunction_17()) {
      auditInvocation(mergeFlowFunction_17, "mergeFlowFunction_17", "publishMerge", typedEvent);
      isDirty_mergeFlowFunction_17 = mergeFlowFunction_17.publishMerge();
      if (isDirty_mergeFlowFunction_17) {
        mapRef2RefFlowFunction_19.inputUpdated(mergeFlowFunction_17);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_19()) {
      auditInvocation(mapRef2RefFlowFunction_19, "mapRef2RefFlowFunction_19", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_19 = mapRef2RefFlowFunction_19.map();
      if (isDirty_mapRef2RefFlowFunction_19) {
        mapRef2RefFlowFunction_21.inputUpdated(mapRef2RefFlowFunction_19);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_21()) {
      auditInvocation(mapRef2RefFlowFunction_21, "mapRef2RefFlowFunction_21", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_21 = mapRef2RefFlowFunction_21.map();
      if (isDirty_mapRef2RefFlowFunction_21) {
        mapRef2RefFlowFunction_23.inputUpdated(mapRef2RefFlowFunction_21);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_23()) {
      auditInvocation(mapRef2RefFlowFunction_23, "mapRef2RefFlowFunction_23", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_23 = mapRef2RefFlowFunction_23.map();
      if (isDirty_mapRef2RefFlowFunction_23) {
        binaryMapToRefFlowFunction_44.input2Updated(mapRef2RefFlowFunction_23);
        binaryMapToRefFlowFunction_59.input2Updated(mapRef2RefFlowFunction_23);
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
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      auditInvocation(mapRef2RefFlowFunction_10, "mapRef2RefFlowFunction_10", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_28.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_28.input2Updated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_28()) {
      auditInvocation(
          binaryMapToRefFlowFunction_28, "binaryMapToRefFlowFunction_28", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_28 = binaryMapToRefFlowFunction_28.map();
      if (isDirty_binaryMapToRefFlowFunction_28) {
        mapRef2RefFlowFunction_30.inputUpdated(binaryMapToRefFlowFunction_28);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_51.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_51.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_51()) {
      auditInvocation(
          binaryMapToRefFlowFunction_51, "binaryMapToRefFlowFunction_51", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_51 = binaryMapToRefFlowFunction_51.map();
      if (isDirty_binaryMapToRefFlowFunction_51) {
        mapRef2RefFlowFunction_53.inputUpdated(binaryMapToRefFlowFunction_51);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_30()) {
      auditInvocation(mapRef2RefFlowFunction_30, "mapRef2RefFlowFunction_30", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_30 = mapRef2RefFlowFunction_30.map();
      if (isDirty_mapRef2RefFlowFunction_30) {
        mapRef2RefFlowFunction_32.inputUpdated(mapRef2RefFlowFunction_30);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_32()) {
      auditInvocation(mapRef2RefFlowFunction_32, "mapRef2RefFlowFunction_32", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_32 = mapRef2RefFlowFunction_32.map();
      if (isDirty_mapRef2RefFlowFunction_32) {
        binaryMapToRefFlowFunction_34.inputUpdated(mapRef2RefFlowFunction_32);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_34()) {
      auditInvocation(
          binaryMapToRefFlowFunction_34, "binaryMapToRefFlowFunction_34", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_34 = binaryMapToRefFlowFunction_34.map();
      if (isDirty_binaryMapToRefFlowFunction_34) {
        mapRef2RefFlowFunction_36.inputUpdated(binaryMapToRefFlowFunction_34);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_36()) {
      auditInvocation(mapRef2RefFlowFunction_36, "mapRef2RefFlowFunction_36", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_36 = mapRef2RefFlowFunction_36.map();
      if (isDirty_mapRef2RefFlowFunction_36) {
        mapRef2RefFlowFunction_38.inputUpdated(mapRef2RefFlowFunction_36);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_38()) {
      auditInvocation(mapRef2RefFlowFunction_38, "mapRef2RefFlowFunction_38", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_38 = mapRef2RefFlowFunction_38.map();
      if (isDirty_mapRef2RefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(mapRef2RefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      auditInvocation(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        binaryMapToRefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_40);
        mapRef2RefFlowFunction_41.inputUpdated(mapRef2RefFlowFunction_40);
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
    if (guardCheck_mapRef2RefFlowFunction_41()) {
      auditInvocation(mapRef2RefFlowFunction_41, "mapRef2RefFlowFunction_41", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_41 = mapRef2RefFlowFunction_41.map();
      if (isDirty_mapRef2RefFlowFunction_41) {
        pushFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_41);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      auditInvocation(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        mapRef2RefFlowFunction_47.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_47()) {
      auditInvocation(mapRef2RefFlowFunction_47, "mapRef2RefFlowFunction_47", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_47 = mapRef2RefFlowFunction_47.map();
      if (isDirty_mapRef2RefFlowFunction_47) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_47);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "map", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_49.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_53()) {
      auditInvocation(mapRef2RefFlowFunction_53, "mapRef2RefFlowFunction_53", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_53 = mapRef2RefFlowFunction_53.map();
      if (isDirty_mapRef2RefFlowFunction_53) {
        mapRef2RefFlowFunction_55.inputUpdated(mapRef2RefFlowFunction_53);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_55()) {
      auditInvocation(mapRef2RefFlowFunction_55, "mapRef2RefFlowFunction_55", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_55 = mapRef2RefFlowFunction_55.map();
      if (isDirty_mapRef2RefFlowFunction_55) {
        mapRef2RefFlowFunction_57.inputUpdated(mapRef2RefFlowFunction_55);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_57()) {
      auditInvocation(mapRef2RefFlowFunction_57, "mapRef2RefFlowFunction_57", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_57 = mapRef2RefFlowFunction_57.map();
      if (isDirty_mapRef2RefFlowFunction_57) {
        binaryMapToRefFlowFunction_59.inputUpdated(mapRef2RefFlowFunction_57);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_59()) {
      auditInvocation(
          binaryMapToRefFlowFunction_59, "binaryMapToRefFlowFunction_59", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_59 = binaryMapToRefFlowFunction_59.map();
      if (isDirty_binaryMapToRefFlowFunction_59) {
        mapRef2RefFlowFunction_61.inputUpdated(binaryMapToRefFlowFunction_59);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_61()) {
      auditInvocation(mapRef2RefFlowFunction_61, "mapRef2RefFlowFunction_61", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_61 = mapRef2RefFlowFunction_61.map();
      if (isDirty_mapRef2RefFlowFunction_61) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_61);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_63.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_42()) {
      auditInvocation(pushFlowFunction_42, "pushFlowFunction_42", "push", typedEvent);
      isDirty_pushFlowFunction_42 = pushFlowFunction_42.push();
    }
    if (guardCheck_pushFlowFunction_49()) {
      auditInvocation(pushFlowFunction_49, "pushFlowFunction_49", "push", typedEvent);
      isDirty_pushFlowFunction_49 = pushFlowFunction_49.push();
    }
    if (guardCheck_pushFlowFunction_63()) {
      auditInvocation(pushFlowFunction_63, "pushFlowFunction_63", "push", typedEvent);
      isDirty_pushFlowFunction_63 = pushFlowFunction_63.push();
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
        mergeFlowFunction_17.inputStreamUpdated(mapRef2RefFlowFunction_4);
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
    if (guardCheck_mergeFlowFunction_17()) {
      auditInvocation(mergeFlowFunction_17, "mergeFlowFunction_17", "publishMerge", typedEvent);
      isDirty_mergeFlowFunction_17 = mergeFlowFunction_17.publishMerge();
      if (isDirty_mergeFlowFunction_17) {
        mapRef2RefFlowFunction_19.inputUpdated(mergeFlowFunction_17);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_19()) {
      auditInvocation(mapRef2RefFlowFunction_19, "mapRef2RefFlowFunction_19", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_19 = mapRef2RefFlowFunction_19.map();
      if (isDirty_mapRef2RefFlowFunction_19) {
        mapRef2RefFlowFunction_21.inputUpdated(mapRef2RefFlowFunction_19);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_21()) {
      auditInvocation(mapRef2RefFlowFunction_21, "mapRef2RefFlowFunction_21", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_21 = mapRef2RefFlowFunction_21.map();
      if (isDirty_mapRef2RefFlowFunction_21) {
        mapRef2RefFlowFunction_23.inputUpdated(mapRef2RefFlowFunction_21);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_23()) {
      auditInvocation(mapRef2RefFlowFunction_23, "mapRef2RefFlowFunction_23", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_23 = mapRef2RefFlowFunction_23.map();
      if (isDirty_mapRef2RefFlowFunction_23) {
        binaryMapToRefFlowFunction_44.input2Updated(mapRef2RefFlowFunction_23);
        binaryMapToRefFlowFunction_59.input2Updated(mapRef2RefFlowFunction_23);
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
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      auditInvocation(mapRef2RefFlowFunction_10, "mapRef2RefFlowFunction_10", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_28.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_28.input2Updated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_28()) {
      auditInvocation(
          binaryMapToRefFlowFunction_28, "binaryMapToRefFlowFunction_28", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_28 = binaryMapToRefFlowFunction_28.map();
      if (isDirty_binaryMapToRefFlowFunction_28) {
        mapRef2RefFlowFunction_30.inputUpdated(binaryMapToRefFlowFunction_28);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_51.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_51.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_51()) {
      auditInvocation(
          binaryMapToRefFlowFunction_51, "binaryMapToRefFlowFunction_51", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_51 = binaryMapToRefFlowFunction_51.map();
      if (isDirty_binaryMapToRefFlowFunction_51) {
        mapRef2RefFlowFunction_53.inputUpdated(binaryMapToRefFlowFunction_51);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_30()) {
      auditInvocation(mapRef2RefFlowFunction_30, "mapRef2RefFlowFunction_30", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_30 = mapRef2RefFlowFunction_30.map();
      if (isDirty_mapRef2RefFlowFunction_30) {
        mapRef2RefFlowFunction_32.inputUpdated(mapRef2RefFlowFunction_30);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_32()) {
      auditInvocation(mapRef2RefFlowFunction_32, "mapRef2RefFlowFunction_32", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_32 = mapRef2RefFlowFunction_32.map();
      if (isDirty_mapRef2RefFlowFunction_32) {
        binaryMapToRefFlowFunction_34.inputUpdated(mapRef2RefFlowFunction_32);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_34()) {
      auditInvocation(
          binaryMapToRefFlowFunction_34, "binaryMapToRefFlowFunction_34", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_34 = binaryMapToRefFlowFunction_34.map();
      if (isDirty_binaryMapToRefFlowFunction_34) {
        mapRef2RefFlowFunction_36.inputUpdated(binaryMapToRefFlowFunction_34);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_36()) {
      auditInvocation(mapRef2RefFlowFunction_36, "mapRef2RefFlowFunction_36", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_36 = mapRef2RefFlowFunction_36.map();
      if (isDirty_mapRef2RefFlowFunction_36) {
        mapRef2RefFlowFunction_38.inputUpdated(mapRef2RefFlowFunction_36);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_38()) {
      auditInvocation(mapRef2RefFlowFunction_38, "mapRef2RefFlowFunction_38", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_38 = mapRef2RefFlowFunction_38.map();
      if (isDirty_mapRef2RefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(mapRef2RefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      auditInvocation(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        binaryMapToRefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_40);
        mapRef2RefFlowFunction_41.inputUpdated(mapRef2RefFlowFunction_40);
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
    if (guardCheck_mapRef2RefFlowFunction_41()) {
      auditInvocation(mapRef2RefFlowFunction_41, "mapRef2RefFlowFunction_41", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_41 = mapRef2RefFlowFunction_41.map();
      if (isDirty_mapRef2RefFlowFunction_41) {
        pushFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_41);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      auditInvocation(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        mapRef2RefFlowFunction_47.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_47()) {
      auditInvocation(mapRef2RefFlowFunction_47, "mapRef2RefFlowFunction_47", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_47 = mapRef2RefFlowFunction_47.map();
      if (isDirty_mapRef2RefFlowFunction_47) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_47);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "map", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_49.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_53()) {
      auditInvocation(mapRef2RefFlowFunction_53, "mapRef2RefFlowFunction_53", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_53 = mapRef2RefFlowFunction_53.map();
      if (isDirty_mapRef2RefFlowFunction_53) {
        mapRef2RefFlowFunction_55.inputUpdated(mapRef2RefFlowFunction_53);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_55()) {
      auditInvocation(mapRef2RefFlowFunction_55, "mapRef2RefFlowFunction_55", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_55 = mapRef2RefFlowFunction_55.map();
      if (isDirty_mapRef2RefFlowFunction_55) {
        mapRef2RefFlowFunction_57.inputUpdated(mapRef2RefFlowFunction_55);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_57()) {
      auditInvocation(mapRef2RefFlowFunction_57, "mapRef2RefFlowFunction_57", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_57 = mapRef2RefFlowFunction_57.map();
      if (isDirty_mapRef2RefFlowFunction_57) {
        binaryMapToRefFlowFunction_59.inputUpdated(mapRef2RefFlowFunction_57);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_59()) {
      auditInvocation(
          binaryMapToRefFlowFunction_59, "binaryMapToRefFlowFunction_59", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_59 = binaryMapToRefFlowFunction_59.map();
      if (isDirty_binaryMapToRefFlowFunction_59) {
        mapRef2RefFlowFunction_61.inputUpdated(binaryMapToRefFlowFunction_59);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_61()) {
      auditInvocation(mapRef2RefFlowFunction_61, "mapRef2RefFlowFunction_61", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_61 = mapRef2RefFlowFunction_61.map();
      if (isDirty_mapRef2RefFlowFunction_61) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_61);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_63.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_42()) {
      auditInvocation(pushFlowFunction_42, "pushFlowFunction_42", "push", typedEvent);
      isDirty_pushFlowFunction_42 = pushFlowFunction_42.push();
    }
    if (guardCheck_pushFlowFunction_49()) {
      auditInvocation(pushFlowFunction_49, "pushFlowFunction_49", "push", typedEvent);
      isDirty_pushFlowFunction_49 = pushFlowFunction_49.push();
    }
    if (guardCheck_pushFlowFunction_63()) {
      auditInvocation(pushFlowFunction_63, "pushFlowFunction_63", "push", typedEvent);
      isDirty_pushFlowFunction_63 = pushFlowFunction_63.push();
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
    auditor.nodeRegistered(callBackNode_65, "callBackNode_65");
    auditor.nodeRegistered(callBackNode_102, "callBackNode_102");
    auditor.nodeRegistered(callbackDispatcher, "callbackDispatcher");
    auditor.nodeRegistered(callBackTriggerEvent_1, "callBackTriggerEvent_1");
    auditor.nodeRegistered(callBackTriggerEvent_0, "callBackTriggerEvent_0");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_28, "binaryMapToRefFlowFunction_28");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_34, "binaryMapToRefFlowFunction_34");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_44, "binaryMapToRefFlowFunction_44");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_51, "binaryMapToRefFlowFunction_51");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_59, "binaryMapToRefFlowFunction_59");
    auditor.nodeRegistered(filterFlowFunction_8, "filterFlowFunction_8");
    auditor.nodeRegistered(flatMapFlowFunction_3, "flatMapFlowFunction_3");
    auditor.nodeRegistered(flatMapSnapshotPositions, "flatMapSnapshotPositions");
    auditor.nodeRegistered(globalNetMtm, "globalNetMtm");
    auditor.nodeRegistered(groupBySnapshotPositions, "groupBySnapshotPositions");
    auditor.nodeRegistered(instrumentNetMtm, "instrumentNetMtm");
    auditor.nodeRegistered(mapRef2RefFlowFunction_4, "mapRef2RefFlowFunction_4");
    auditor.nodeRegistered(mapRef2RefFlowFunction_5, "mapRef2RefFlowFunction_5");
    auditor.nodeRegistered(mapRef2RefFlowFunction_10, "mapRef2RefFlowFunction_10");
    auditor.nodeRegistered(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12");
    auditor.nodeRegistered(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14");
    auditor.nodeRegistered(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16");
    auditor.nodeRegistered(mapRef2RefFlowFunction_19, "mapRef2RefFlowFunction_19");
    auditor.nodeRegistered(mapRef2RefFlowFunction_21, "mapRef2RefFlowFunction_21");
    auditor.nodeRegistered(mapRef2RefFlowFunction_23, "mapRef2RefFlowFunction_23");
    auditor.nodeRegistered(mapRef2RefFlowFunction_30, "mapRef2RefFlowFunction_30");
    auditor.nodeRegistered(mapRef2RefFlowFunction_32, "mapRef2RefFlowFunction_32");
    auditor.nodeRegistered(mapRef2RefFlowFunction_36, "mapRef2RefFlowFunction_36");
    auditor.nodeRegistered(mapRef2RefFlowFunction_38, "mapRef2RefFlowFunction_38");
    auditor.nodeRegistered(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40");
    auditor.nodeRegistered(mapRef2RefFlowFunction_41, "mapRef2RefFlowFunction_41");
    auditor.nodeRegistered(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46");
    auditor.nodeRegistered(mapRef2RefFlowFunction_47, "mapRef2RefFlowFunction_47");
    auditor.nodeRegistered(mapRef2RefFlowFunction_53, "mapRef2RefFlowFunction_53");
    auditor.nodeRegistered(mapRef2RefFlowFunction_55, "mapRef2RefFlowFunction_55");
    auditor.nodeRegistered(mapRef2RefFlowFunction_57, "mapRef2RefFlowFunction_57");
    auditor.nodeRegistered(mapRef2RefFlowFunction_61, "mapRef2RefFlowFunction_61");
    auditor.nodeRegistered(mergeFlowFunction_6, "mergeFlowFunction_6");
    auditor.nodeRegistered(mergeFlowFunction_17, "mergeFlowFunction_17");
    auditor.nodeRegistered(pushFlowFunction_42, "pushFlowFunction_42");
    auditor.nodeRegistered(pushFlowFunction_49, "pushFlowFunction_49");
    auditor.nodeRegistered(pushFlowFunction_63, "pushFlowFunction_63");
    auditor.nodeRegistered(emptyGroupBy_92, "emptyGroupBy_92");
    auditor.nodeRegistered(emptyGroupBy_125, "emptyGroupBy_125");
    auditor.nodeRegistered(emptyGroupBy_188, "emptyGroupBy_188");
    auditor.nodeRegistered(emptyGroupBy_416, "emptyGroupBy_416");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_9, "groupByFlowFunctionWrapper_9");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_11, "groupByFlowFunctionWrapper_11");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_13, "groupByFlowFunctionWrapper_13");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_15, "groupByFlowFunctionWrapper_15");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_18, "groupByFlowFunctionWrapper_18");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_25, "groupByFlowFunctionWrapper_25");
    auditor.nodeRegistered(groupByMapFlowFunction_22, "groupByMapFlowFunction_22");
    auditor.nodeRegistered(groupByMapFlowFunction_29, "groupByMapFlowFunction_29");
    auditor.nodeRegistered(groupByMapFlowFunction_35, "groupByMapFlowFunction_35");
    auditor.nodeRegistered(groupByMapFlowFunction_37, "groupByMapFlowFunction_37");
    auditor.nodeRegistered(groupByMapFlowFunction_45, "groupByMapFlowFunction_45");
    auditor.nodeRegistered(groupByMapFlowFunction_52, "groupByMapFlowFunction_52");
    auditor.nodeRegistered(groupByMapFlowFunction_54, "groupByMapFlowFunction_54");
    auditor.nodeRegistered(groupByMapFlowFunction_60, "groupByMapFlowFunction_60");
    auditor.nodeRegistered(leftJoin_43, "leftJoin_43");
    auditor.nodeRegistered(leftJoin_58, "leftJoin_58");
    auditor.nodeRegistered(outerJoin_27, "outerJoin_27");
    auditor.nodeRegistered(outerJoin_33, "outerJoin_33");
    auditor.nodeRegistered(outerJoin_50, "outerJoin_50");
    auditor.nodeRegistered(defaultValue_20, "defaultValue_20");
    auditor.nodeRegistered(defaultValue_31, "defaultValue_31");
    auditor.nodeRegistered(defaultValue_39, "defaultValue_39");
    auditor.nodeRegistered(defaultValue_56, "defaultValue_56");
    auditor.nodeRegistered(mapTuple_355, "mapTuple_355");
    auditor.nodeRegistered(mapTuple_366, "mapTuple_366");
    auditor.nodeRegistered(mapTuple_371, "mapTuple_371");
    auditor.nodeRegistered(mapTuple_497, "mapTuple_497");
    auditor.nodeRegistered(mapTuple_508, "mapTuple_508");
    auditor.nodeRegistered(subscriptionManager, "subscriptionManager");
    auditor.nodeRegistered(handlerPositionSnapshot, "handlerPositionSnapshot");
    auditor.nodeRegistered(
        handlerSignal_positionSnapshotReset, "handlerSignal_positionSnapshotReset");
    auditor.nodeRegistered(handlerSignal_positionUpdate, "handlerSignal_positionUpdate");
    auditor.nodeRegistered(handlerTrade, "handlerTrade");
    auditor.nodeRegistered(handlerTradeBatch, "handlerTradeBatch");
    auditor.nodeRegistered(context, "context");
    auditor.nodeRegistered(namedFeedTableNode_64, "namedFeedTableNode_64");
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
    isDirty_binaryMapToRefFlowFunction_28 = false;
    isDirty_binaryMapToRefFlowFunction_34 = false;
    isDirty_binaryMapToRefFlowFunction_44 = false;
    isDirty_binaryMapToRefFlowFunction_51 = false;
    isDirty_binaryMapToRefFlowFunction_59 = false;
    isDirty_callBackNode_65 = false;
    isDirty_callBackNode_102 = false;
    isDirty_clock = false;
    isDirty_derivedRateNode = false;
    isDirty_eventFeedBatcher = false;
    isDirty_filterFlowFunction_8 = false;
    isDirty_flatMapFlowFunction_3 = false;
    isDirty_flatMapSnapshotPositions = false;
    isDirty_globalNetMtm = false;
    isDirty_groupBySnapshotPositions = false;
    isDirty_handlerPositionSnapshot = false;
    isDirty_handlerSignal_positionSnapshotReset = false;
    isDirty_handlerSignal_positionUpdate = false;
    isDirty_handlerTrade = false;
    isDirty_handlerTradeBatch = false;
    isDirty_instrumentNetMtm = false;
    isDirty_mapRef2RefFlowFunction_4 = false;
    isDirty_mapRef2RefFlowFunction_5 = false;
    isDirty_mapRef2RefFlowFunction_10 = false;
    isDirty_mapRef2RefFlowFunction_12 = false;
    isDirty_mapRef2RefFlowFunction_14 = false;
    isDirty_mapRef2RefFlowFunction_16 = false;
    isDirty_mapRef2RefFlowFunction_19 = false;
    isDirty_mapRef2RefFlowFunction_21 = false;
    isDirty_mapRef2RefFlowFunction_23 = false;
    isDirty_mapRef2RefFlowFunction_30 = false;
    isDirty_mapRef2RefFlowFunction_32 = false;
    isDirty_mapRef2RefFlowFunction_36 = false;
    isDirty_mapRef2RefFlowFunction_38 = false;
    isDirty_mapRef2RefFlowFunction_40 = false;
    isDirty_mapRef2RefFlowFunction_41 = false;
    isDirty_mapRef2RefFlowFunction_46 = false;
    isDirty_mapRef2RefFlowFunction_47 = false;
    isDirty_mapRef2RefFlowFunction_53 = false;
    isDirty_mapRef2RefFlowFunction_55 = false;
    isDirty_mapRef2RefFlowFunction_57 = false;
    isDirty_mapRef2RefFlowFunction_61 = false;
    isDirty_mergeFlowFunction_6 = false;
    isDirty_mergeFlowFunction_17 = false;
    isDirty_namedFeedTableNode_64 = false;
    isDirty_pushFlowFunction_42 = false;
    isDirty_pushFlowFunction_49 = false;
    isDirty_pushFlowFunction_63 = false;
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
          binaryMapToRefFlowFunction_28, () -> isDirty_binaryMapToRefFlowFunction_28);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_34, () -> isDirty_binaryMapToRefFlowFunction_34);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_44, () -> isDirty_binaryMapToRefFlowFunction_44);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_51, () -> isDirty_binaryMapToRefFlowFunction_51);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_59, () -> isDirty_binaryMapToRefFlowFunction_59);
      dirtyFlagSupplierMap.put(callBackNode_102, () -> isDirty_callBackNode_102);
      dirtyFlagSupplierMap.put(callBackNode_65, () -> isDirty_callBackNode_65);
      dirtyFlagSupplierMap.put(clock, () -> isDirty_clock);
      dirtyFlagSupplierMap.put(derivedRateNode, () -> isDirty_derivedRateNode);
      dirtyFlagSupplierMap.put(eventFeedBatcher, () -> isDirty_eventFeedBatcher);
      dirtyFlagSupplierMap.put(filterFlowFunction_8, () -> isDirty_filterFlowFunction_8);
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
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_10, () -> isDirty_mapRef2RefFlowFunction_10);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_12, () -> isDirty_mapRef2RefFlowFunction_12);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_14, () -> isDirty_mapRef2RefFlowFunction_14);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_16, () -> isDirty_mapRef2RefFlowFunction_16);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_19, () -> isDirty_mapRef2RefFlowFunction_19);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_21, () -> isDirty_mapRef2RefFlowFunction_21);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_23, () -> isDirty_mapRef2RefFlowFunction_23);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_30, () -> isDirty_mapRef2RefFlowFunction_30);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_32, () -> isDirty_mapRef2RefFlowFunction_32);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_36, () -> isDirty_mapRef2RefFlowFunction_36);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_38, () -> isDirty_mapRef2RefFlowFunction_38);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_4, () -> isDirty_mapRef2RefFlowFunction_4);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_40, () -> isDirty_mapRef2RefFlowFunction_40);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_41, () -> isDirty_mapRef2RefFlowFunction_41);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_46, () -> isDirty_mapRef2RefFlowFunction_46);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_47, () -> isDirty_mapRef2RefFlowFunction_47);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_5, () -> isDirty_mapRef2RefFlowFunction_5);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_53, () -> isDirty_mapRef2RefFlowFunction_53);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_55, () -> isDirty_mapRef2RefFlowFunction_55);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_57, () -> isDirty_mapRef2RefFlowFunction_57);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_61, () -> isDirty_mapRef2RefFlowFunction_61);
      dirtyFlagSupplierMap.put(mergeFlowFunction_17, () -> isDirty_mergeFlowFunction_17);
      dirtyFlagSupplierMap.put(mergeFlowFunction_6, () -> isDirty_mergeFlowFunction_6);
      dirtyFlagSupplierMap.put(namedFeedTableNode_64, () -> isDirty_namedFeedTableNode_64);
      dirtyFlagSupplierMap.put(pushFlowFunction_42, () -> isDirty_pushFlowFunction_42);
      dirtyFlagSupplierMap.put(pushFlowFunction_49, () -> isDirty_pushFlowFunction_49);
      dirtyFlagSupplierMap.put(pushFlowFunction_63, () -> isDirty_pushFlowFunction_63);
    }
    return dirtyFlagSupplierMap.getOrDefault(node, StaticEventProcessor.ALWAYS_FALSE);
  }

  @Override
  public void setDirty(Object node, boolean dirtyFlag) {
    if (dirtyFlagUpdateMap.isEmpty()) {
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_28, (b) -> isDirty_binaryMapToRefFlowFunction_28 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_34, (b) -> isDirty_binaryMapToRefFlowFunction_34 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_44, (b) -> isDirty_binaryMapToRefFlowFunction_44 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_51, (b) -> isDirty_binaryMapToRefFlowFunction_51 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_59, (b) -> isDirty_binaryMapToRefFlowFunction_59 = b);
      dirtyFlagUpdateMap.put(callBackNode_102, (b) -> isDirty_callBackNode_102 = b);
      dirtyFlagUpdateMap.put(callBackNode_65, (b) -> isDirty_callBackNode_65 = b);
      dirtyFlagUpdateMap.put(clock, (b) -> isDirty_clock = b);
      dirtyFlagUpdateMap.put(derivedRateNode, (b) -> isDirty_derivedRateNode = b);
      dirtyFlagUpdateMap.put(eventFeedBatcher, (b) -> isDirty_eventFeedBatcher = b);
      dirtyFlagUpdateMap.put(filterFlowFunction_8, (b) -> isDirty_filterFlowFunction_8 = b);
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
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_10, (b) -> isDirty_mapRef2RefFlowFunction_10 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_12, (b) -> isDirty_mapRef2RefFlowFunction_12 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_14, (b) -> isDirty_mapRef2RefFlowFunction_14 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_16, (b) -> isDirty_mapRef2RefFlowFunction_16 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_19, (b) -> isDirty_mapRef2RefFlowFunction_19 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_21, (b) -> isDirty_mapRef2RefFlowFunction_21 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_23, (b) -> isDirty_mapRef2RefFlowFunction_23 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_30, (b) -> isDirty_mapRef2RefFlowFunction_30 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_32, (b) -> isDirty_mapRef2RefFlowFunction_32 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_36, (b) -> isDirty_mapRef2RefFlowFunction_36 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_38, (b) -> isDirty_mapRef2RefFlowFunction_38 = b);
      dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_4, (b) -> isDirty_mapRef2RefFlowFunction_4 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_40, (b) -> isDirty_mapRef2RefFlowFunction_40 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_41, (b) -> isDirty_mapRef2RefFlowFunction_41 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_46, (b) -> isDirty_mapRef2RefFlowFunction_46 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_47, (b) -> isDirty_mapRef2RefFlowFunction_47 = b);
      dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_5, (b) -> isDirty_mapRef2RefFlowFunction_5 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_53, (b) -> isDirty_mapRef2RefFlowFunction_53 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_55, (b) -> isDirty_mapRef2RefFlowFunction_55 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_57, (b) -> isDirty_mapRef2RefFlowFunction_57 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_61, (b) -> isDirty_mapRef2RefFlowFunction_61 = b);
      dirtyFlagUpdateMap.put(mergeFlowFunction_17, (b) -> isDirty_mergeFlowFunction_17 = b);
      dirtyFlagUpdateMap.put(mergeFlowFunction_6, (b) -> isDirty_mergeFlowFunction_6 = b);
      dirtyFlagUpdateMap.put(namedFeedTableNode_64, (b) -> isDirty_namedFeedTableNode_64 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_42, (b) -> isDirty_pushFlowFunction_42 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_49, (b) -> isDirty_pushFlowFunction_49 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_63, (b) -> isDirty_pushFlowFunction_63 = b);
    }
    dirtyFlagUpdateMap.get(node).accept(dirtyFlag);
  }

  private boolean guardCheck_eventLogger() {
    return isDirty_clock;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_28() {
    return isDirty_mapRef2RefFlowFunction_10 | isDirty_mapRef2RefFlowFunction_12;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_34() {
    return isDirty_groupBySnapshotPositions | isDirty_mapRef2RefFlowFunction_32;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_44() {
    return isDirty_mapRef2RefFlowFunction_23 | isDirty_mapRef2RefFlowFunction_40;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_51() {
    return isDirty_mapRef2RefFlowFunction_14 | isDirty_mapRef2RefFlowFunction_16;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_59() {
    return isDirty_mapRef2RefFlowFunction_23 | isDirty_mapRef2RefFlowFunction_57;
  }

  private boolean guardCheck_filterFlowFunction_8() {
    return isDirty_mergeFlowFunction_6;
  }

  private boolean guardCheck_flatMapFlowFunction_3() {
    return isDirty_callBackNode_65;
  }

  private boolean guardCheck_flatMapSnapshotPositions() {
    return isDirty_callBackNode_102;
  }

  private boolean guardCheck_globalNetMtm() {
    return isDirty_mapRef2RefFlowFunction_47;
  }

  private boolean guardCheck_groupBySnapshotPositions() {
    return isDirty_flatMapSnapshotPositions
        | isDirty_handlerSignal_positionSnapshotReset
        | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_instrumentNetMtm() {
    return isDirty_mapRef2RefFlowFunction_61;
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

  private boolean guardCheck_mapRef2RefFlowFunction_19() {
    return isDirty_handlerSignal_positionSnapshotReset | isDirty_mergeFlowFunction_17;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_21() {
    return isDirty_mapRef2RefFlowFunction_19;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_23() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_21;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_30() {
    return isDirty_binaryMapToRefFlowFunction_28;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_32() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_30;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_36() {
    return isDirty_binaryMapToRefFlowFunction_34;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_38() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_36;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_40() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_38;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_41() {
    return isDirty_mapRef2RefFlowFunction_40;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_46() {
    return isDirty_binaryMapToRefFlowFunction_44 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_47() {
    return isDirty_mapRef2RefFlowFunction_46;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_53() {
    return isDirty_binaryMapToRefFlowFunction_51;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_55() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_53;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_57() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_55;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_61() {
    return isDirty_binaryMapToRefFlowFunction_59 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mergeFlowFunction_6() {
    return isDirty_mapRef2RefFlowFunction_4 | isDirty_mapRef2RefFlowFunction_5;
  }

  private boolean guardCheck_mergeFlowFunction_17() {
    return isDirty_handlerTrade | isDirty_mapRef2RefFlowFunction_4;
  }

  private boolean guardCheck_pushFlowFunction_42() {
    return isDirty_mapRef2RefFlowFunction_41;
  }

  private boolean guardCheck_pushFlowFunction_49() {
    return isDirty_globalNetMtm;
  }

  private boolean guardCheck_pushFlowFunction_63() {
    return isDirty_instrumentNetMtm;
  }

  private boolean guardCheck_groupByMapFlowFunction_22() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_groupByMapFlowFunction_37() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_groupByMapFlowFunction_54() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_context() {
    return isDirty_clock;
  }

  private boolean guardCheck_globalNetMtmListener() {
    return isDirty_pushFlowFunction_49;
  }

  private boolean guardCheck_instrumentNetMtmListener() {
    return isDirty_pushFlowFunction_63;
  }

  private boolean guardCheck_derivedRateNode() {
    return isDirty_namedFeedTableNode_64;
  }

  private boolean guardCheck_eventFeedBatcher() {
    return isDirty_namedFeedTableNode_64;
  }

  private boolean guardCheck_positionCache() {
    return isDirty_pushFlowFunction_42;
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
