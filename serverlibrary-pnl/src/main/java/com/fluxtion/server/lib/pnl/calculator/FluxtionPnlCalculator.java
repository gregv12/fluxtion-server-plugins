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
  private final CallBackNode callBackNode_64 = new CallBackNode<>(callBackTriggerEvent_0);
  private final InstanceCallbackEvent_1 callBackTriggerEvent_1 = new InstanceCallbackEvent_1();
  private final CallBackNode callBackNode_88 = new CallBackNode<>(callBackTriggerEvent_1);
  private final CallbackDispatcherImpl callbackDispatcher = new CallbackDispatcherImpl();
  public final Clock clock = new Clock();
  private final EmptyGroupBy emptyGroupBy_111 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_24 = new DefaultValue<>(emptyGroupBy_111);
  private final EmptyGroupBy emptyGroupBy_174 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_32 = new DefaultValue<>(emptyGroupBy_174);
  private final EmptyGroupBy emptyGroupBy_197 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_37 = new DefaultValue<>(emptyGroupBy_197);
  private final EmptyGroupBy emptyGroupBy_405 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_55 = new DefaultValue<>(emptyGroupBy_405);
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
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_35 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, FeeInstrumentPosMtmAggregate::new);
  private final LeftJoin leftJoin_41 = new LeftJoin();
  private final LeftJoin leftJoin_57 = new LeftJoin();
  private final MapTuple mapTuple_344 = new MapTuple<>(NetMarkToMarket::combine);
  private final GroupByMapFlowFunction groupByMapFlowFunction_43 =
      new GroupByMapFlowFunction(mapTuple_344::mapTuple);
  private final MapTuple mapTuple_355 =
      new MapTuple<>(InstrumentPosMtm::overwriteInstrumentPositionWithSnapshot);
  private final GroupByMapFlowFunction groupByMapFlowFunction_28 =
      new GroupByMapFlowFunction(mapTuple_355::mapTuple);
  private final MapTuple mapTuple_360 = new MapTuple<>(InstrumentPosMtm::merge);
  private final GroupByMapFlowFunction groupByMapFlowFunction_22 =
      new GroupByMapFlowFunction(mapTuple_360::mapTuple);
  private final MapTuple mapTuple_486 = new MapTuple<>(NetMarkToMarket::combine);
  private final GroupByMapFlowFunction groupByMapFlowFunction_59 =
      new GroupByMapFlowFunction(mapTuple_486::mapTuple);
  private final MapTuple mapTuple_497 = new MapTuple<>(InstrumentPosMtm::merge);
  private final GroupByMapFlowFunction groupByMapFlowFunction_51 =
      new GroupByMapFlowFunction(mapTuple_497::mapTuple);
  private final NamedFeedTableNode namedFeedTableNode_63 =
      new NamedFeedTableNode<>("symbolFeed", Symbol::symbolName);
  public final DerivedRateNode derivedRateNode = new DerivedRateNode(namedFeedTableNode_63);
  public final EventFeedConnector eventFeedBatcher = new EventFeedConnector(namedFeedTableNode_63);
  private final GroupByMapFlowFunction groupByMapFlowFunction_30 =
      new GroupByMapFlowFunction(derivedRateNode::calculateInstrumentPosMtm);
  private final GroupByMapFlowFunction groupByMapFlowFunction_39 =
      new GroupByMapFlowFunction(derivedRateNode::calculateFeeMtm);
  private final GroupByMapFlowFunction groupByMapFlowFunction_53 =
      new GroupByMapFlowFunction(derivedRateNode::calculateInstrumentPosMtm);
  public final NodeNameAuditor nodeNameLookup = new NodeNameAuditor();
  private final OuterJoin outerJoin_20 = new OuterJoin();
  private final OuterJoin outerJoin_26 = new OuterJoin();
  private final OuterJoin outerJoin_49 = new OuterJoin();
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
          flatMapSnapshotPositions, groupByFlowFunctionWrapper_18::aggregate);
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
  private final MergeFlowFunction mergeFlowFunction_34 =
      new MergeFlowFunction<>(Arrays.asList(handlerTrade, mapRef2RefFlowFunction_4));
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_36 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_34, groupByFlowFunctionWrapper_35::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_38 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_36, defaultValue_37::getOrDefault);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_40 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_38, groupByMapFlowFunction_39::mapValues);
  public final ServiceRegistryNode serviceRegistry = new ServiceRegistryNode();
  private final TradeSequenceFilter tradeSequenceFilter_7 = new TradeSequenceFilter();
  private final FilterFlowFunction filterFlowFunction_8 =
      new FilterFlowFunction<>(
          mergeFlowFunction_6, tradeSequenceFilter_7::checkTradeSequenceNumber);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_10 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_8, groupByFlowFunctionWrapper_9::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_12 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_8, groupByFlowFunctionWrapper_11::aggregate);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_21 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_10, mapRef2RefFlowFunction_12, outerJoin_20::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_14 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_8, groupByFlowFunctionWrapper_13::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_16 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_8, groupByFlowFunctionWrapper_15::aggregate);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_50 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_14, mapRef2RefFlowFunction_16, outerJoin_49::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_23 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_21, groupByMapFlowFunction_22::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_25 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_23, defaultValue_24::getOrDefault);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_27 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_25, groupBySnapshotPositions, outerJoin_26::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_29 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_27, groupByMapFlowFunction_28::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_31 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_29, groupByMapFlowFunction_30::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_33 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_31, defaultValue_32::getOrDefault);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_42 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_33, mapRef2RefFlowFunction_40, leftJoin_41::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_44 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_42, groupByMapFlowFunction_43::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_45 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_44, GroupBy<Object, Object>::toMap);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_46 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_45, NetMarkToMarket::markToMarketSum);
  public final PushFlowFunction globalNetMtm =
      new PushFlowFunction<>(mapRef2RefFlowFunction_46, positionCache::mtmUpdated);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_52 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_50, groupByMapFlowFunction_51::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_54 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_52, groupByMapFlowFunction_53::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_56 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_54, defaultValue_55::getOrDefault);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_58 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_56, mapRef2RefFlowFunction_40, leftJoin_57::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_60 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_58, groupByMapFlowFunction_59::mapValues);
  public final MapRef2RefFlowFunction instrumentNetMtm =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_60, GroupBy<Object, Object>::toMap);
  private final PushFlowFunction pushFlowFunction_48 =
      new PushFlowFunction<>(globalNetMtm, globalNetMtmListener::publish);
  private final PushFlowFunction pushFlowFunction_62 =
      new PushFlowFunction<>(instrumentNetMtm, instrumentNetMtmListener::publish);
  private final ExportFunctionAuditEvent functionAudit = new ExportFunctionAuditEvent();
  //Dirty flags
  private boolean initCalled = false;
  private boolean processing = false;
  private boolean buffering = false;
  private final IdentityHashMap<Object, BooleanSupplier> dirtyFlagSupplierMap =
      new IdentityHashMap<>(47);
  private final IdentityHashMap<Object, Consumer<Boolean>> dirtyFlagUpdateMap =
      new IdentityHashMap<>(47);

  private boolean isDirty_binaryMapToRefFlowFunction_21 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_27 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_42 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_50 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_58 = false;
  private boolean isDirty_callBackNode_64 = false;
  private boolean isDirty_callBackNode_88 = false;
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
  private boolean isDirty_mapRef2RefFlowFunction_23 = false;
  private boolean isDirty_mapRef2RefFlowFunction_25 = false;
  private boolean isDirty_mapRef2RefFlowFunction_29 = false;
  private boolean isDirty_mapRef2RefFlowFunction_31 = false;
  private boolean isDirty_mapRef2RefFlowFunction_33 = false;
  private boolean isDirty_mapRef2RefFlowFunction_36 = false;
  private boolean isDirty_mapRef2RefFlowFunction_38 = false;
  private boolean isDirty_mapRef2RefFlowFunction_40 = false;
  private boolean isDirty_mapRef2RefFlowFunction_44 = false;
  private boolean isDirty_mapRef2RefFlowFunction_45 = false;
  private boolean isDirty_mapRef2RefFlowFunction_46 = false;
  private boolean isDirty_mapRef2RefFlowFunction_52 = false;
  private boolean isDirty_mapRef2RefFlowFunction_54 = false;
  private boolean isDirty_mapRef2RefFlowFunction_56 = false;
  private boolean isDirty_mapRef2RefFlowFunction_60 = false;
  private boolean isDirty_mergeFlowFunction_6 = false;
  private boolean isDirty_mergeFlowFunction_34 = false;
  private boolean isDirty_namedFeedTableNode_63 = false;
  private boolean isDirty_pushFlowFunction_48 = false;
  private boolean isDirty_pushFlowFunction_62 = false;

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
    binaryMapToRefFlowFunction_21.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_27.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_42.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_50.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_58.setEventProcessorContext(context);
    filterFlowFunction_8.setEventProcessorContext(context);
    flatMapFlowFunction_3.setFlatMapCompleteSignal("positionUpdate");
    flatMapFlowFunction_3.callback = callBackNode_64;
    flatMapFlowFunction_3.dirtyStateMonitor = callbackDispatcher;
    flatMapSnapshotPositions.callback = callBackNode_88;
    flatMapSnapshotPositions.dirtyStateMonitor = callbackDispatcher;
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
    mapRef2RefFlowFunction_23.setEventProcessorContext(context);
    mapRef2RefFlowFunction_25.setEventProcessorContext(context);
    mapRef2RefFlowFunction_25.setPublishTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_29.setEventProcessorContext(context);
    mapRef2RefFlowFunction_31.setEventProcessorContext(context);
    mapRef2RefFlowFunction_31.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_33.setEventProcessorContext(context);
    mapRef2RefFlowFunction_33.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_36.setEventProcessorContext(context);
    mapRef2RefFlowFunction_36.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_38.setEventProcessorContext(context);
    mapRef2RefFlowFunction_40.setEventProcessorContext(context);
    mapRef2RefFlowFunction_40.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_40.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_44.setEventProcessorContext(context);
    mapRef2RefFlowFunction_44.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_45.setEventProcessorContext(context);
    mapRef2RefFlowFunction_46.setEventProcessorContext(context);
    mapRef2RefFlowFunction_52.setEventProcessorContext(context);
    mapRef2RefFlowFunction_54.setEventProcessorContext(context);
    mapRef2RefFlowFunction_54.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_56.setEventProcessorContext(context);
    mapRef2RefFlowFunction_56.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_60.setEventProcessorContext(context);
    mapRef2RefFlowFunction_60.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mergeFlowFunction_6.dirtyStateMonitor = callbackDispatcher;
    mergeFlowFunction_34.dirtyStateMonitor = callbackDispatcher;
    globalNetMtm.setEventProcessorContext(context);
    pushFlowFunction_48.setEventProcessorContext(context);
    pushFlowFunction_62.setEventProcessorContext(context);
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
    namedFeedTableNode_63.initialise();
    namedFeedTableNode_63.init();
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
    mapRef2RefFlowFunction_36.initialiseEventStream();
    mapRef2RefFlowFunction_38.initialiseEventStream();
    mapRef2RefFlowFunction_40.initialiseEventStream();
    tradeSequenceFilter_7.init();
    filterFlowFunction_8.initialiseEventStream();
    mapRef2RefFlowFunction_10.initialiseEventStream();
    mapRef2RefFlowFunction_12.initialiseEventStream();
    binaryMapToRefFlowFunction_21.initialiseEventStream();
    mapRef2RefFlowFunction_14.initialiseEventStream();
    mapRef2RefFlowFunction_16.initialiseEventStream();
    binaryMapToRefFlowFunction_50.initialiseEventStream();
    mapRef2RefFlowFunction_23.initialiseEventStream();
    mapRef2RefFlowFunction_25.initialiseEventStream();
    binaryMapToRefFlowFunction_27.initialiseEventStream();
    mapRef2RefFlowFunction_29.initialiseEventStream();
    mapRef2RefFlowFunction_31.initialiseEventStream();
    mapRef2RefFlowFunction_33.initialiseEventStream();
    binaryMapToRefFlowFunction_42.initialiseEventStream();
    mapRef2RefFlowFunction_44.initialiseEventStream();
    mapRef2RefFlowFunction_45.initialiseEventStream();
    mapRef2RefFlowFunction_46.initialiseEventStream();
    globalNetMtm.initialiseEventStream();
    mapRef2RefFlowFunction_52.initialiseEventStream();
    mapRef2RefFlowFunction_54.initialiseEventStream();
    mapRef2RefFlowFunction_56.initialiseEventStream();
    binaryMapToRefFlowFunction_58.initialiseEventStream();
    mapRef2RefFlowFunction_60.initialiseEventStream();
    instrumentNetMtm.initialiseEventStream();
    pushFlowFunction_48.initialiseEventStream();
    pushFlowFunction_62.initialiseEventStream();
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
    auditInvocation(callBackNode_64, "callBackNode_64", "onEvent", typedEvent);
    isDirty_callBackNode_64 = callBackNode_64.onEvent(typedEvent);
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
        mergeFlowFunction_34.inputStreamUpdated(mapRef2RefFlowFunction_4);
      }
    }
    if (guardCheck_mergeFlowFunction_6()) {
      auditInvocation(mergeFlowFunction_6, "mergeFlowFunction_6", "publishMerge", typedEvent);
      isDirty_mergeFlowFunction_6 = mergeFlowFunction_6.publishMerge();
      if (isDirty_mergeFlowFunction_6) {
        filterFlowFunction_8.inputUpdated(mergeFlowFunction_6);
      }
    }
    if (guardCheck_mergeFlowFunction_34()) {
      auditInvocation(mergeFlowFunction_34, "mergeFlowFunction_34", "publishMerge", typedEvent);
      isDirty_mergeFlowFunction_34 = mergeFlowFunction_34.publishMerge();
      if (isDirty_mergeFlowFunction_34) {
        mapRef2RefFlowFunction_36.inputUpdated(mergeFlowFunction_34);
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
        binaryMapToRefFlowFunction_42.input2Updated(mapRef2RefFlowFunction_40);
        binaryMapToRefFlowFunction_58.input2Updated(mapRef2RefFlowFunction_40);
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
        binaryMapToRefFlowFunction_21.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_21.input2Updated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_21()) {
      auditInvocation(
          binaryMapToRefFlowFunction_21, "binaryMapToRefFlowFunction_21", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_21 = binaryMapToRefFlowFunction_21.map();
      if (isDirty_binaryMapToRefFlowFunction_21) {
        mapRef2RefFlowFunction_23.inputUpdated(binaryMapToRefFlowFunction_21);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_50.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_50.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_50()) {
      auditInvocation(
          binaryMapToRefFlowFunction_50, "binaryMapToRefFlowFunction_50", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_50 = binaryMapToRefFlowFunction_50.map();
      if (isDirty_binaryMapToRefFlowFunction_50) {
        mapRef2RefFlowFunction_52.inputUpdated(binaryMapToRefFlowFunction_50);
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
        mapRef2RefFlowFunction_29.inputUpdated(binaryMapToRefFlowFunction_27);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_29()) {
      auditInvocation(mapRef2RefFlowFunction_29, "mapRef2RefFlowFunction_29", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_29 = mapRef2RefFlowFunction_29.map();
      if (isDirty_mapRef2RefFlowFunction_29) {
        mapRef2RefFlowFunction_31.inputUpdated(mapRef2RefFlowFunction_29);
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
        binaryMapToRefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_33);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_42()) {
      auditInvocation(
          binaryMapToRefFlowFunction_42, "binaryMapToRefFlowFunction_42", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_42 = binaryMapToRefFlowFunction_42.map();
      if (isDirty_binaryMapToRefFlowFunction_42) {
        mapRef2RefFlowFunction_44.inputUpdated(binaryMapToRefFlowFunction_42);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_44()) {
      auditInvocation(mapRef2RefFlowFunction_44, "mapRef2RefFlowFunction_44", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_44 = mapRef2RefFlowFunction_44.map();
      if (isDirty_mapRef2RefFlowFunction_44) {
        mapRef2RefFlowFunction_45.inputUpdated(mapRef2RefFlowFunction_44);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_45()) {
      auditInvocation(mapRef2RefFlowFunction_45, "mapRef2RefFlowFunction_45", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_45 = mapRef2RefFlowFunction_45.map();
      if (isDirty_mapRef2RefFlowFunction_45) {
        mapRef2RefFlowFunction_46.inputUpdated(mapRef2RefFlowFunction_45);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      auditInvocation(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "push", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.push();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_48.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_52()) {
      auditInvocation(mapRef2RefFlowFunction_52, "mapRef2RefFlowFunction_52", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_52 = mapRef2RefFlowFunction_52.map();
      if (isDirty_mapRef2RefFlowFunction_52) {
        mapRef2RefFlowFunction_54.inputUpdated(mapRef2RefFlowFunction_52);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      auditInvocation(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        mapRef2RefFlowFunction_56.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_56()) {
      auditInvocation(mapRef2RefFlowFunction_56, "mapRef2RefFlowFunction_56", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_56 = mapRef2RefFlowFunction_56.map();
      if (isDirty_mapRef2RefFlowFunction_56) {
        binaryMapToRefFlowFunction_58.inputUpdated(mapRef2RefFlowFunction_56);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_58()) {
      auditInvocation(
          binaryMapToRefFlowFunction_58, "binaryMapToRefFlowFunction_58", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_58 = binaryMapToRefFlowFunction_58.map();
      if (isDirty_binaryMapToRefFlowFunction_58) {
        mapRef2RefFlowFunction_60.inputUpdated(binaryMapToRefFlowFunction_58);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_60()) {
      auditInvocation(mapRef2RefFlowFunction_60, "mapRef2RefFlowFunction_60", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_60 = mapRef2RefFlowFunction_60.map();
      if (isDirty_mapRef2RefFlowFunction_60) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_60);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_62.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_48()) {
      auditInvocation(pushFlowFunction_48, "pushFlowFunction_48", "push", typedEvent);
      isDirty_pushFlowFunction_48 = pushFlowFunction_48.push();
    }
    if (guardCheck_pushFlowFunction_62()) {
      auditInvocation(pushFlowFunction_62, "pushFlowFunction_62", "push", typedEvent);
      isDirty_pushFlowFunction_62 = pushFlowFunction_62.push();
    }
    afterEvent();
  }

  public void handleEvent(InstanceCallbackEvent_1 typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(callBackNode_88, "callBackNode_88", "onEvent", typedEvent);
    isDirty_callBackNode_88 = callBackNode_88.onEvent(typedEvent);
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
        binaryMapToRefFlowFunction_27.input2Updated(groupBySnapshotPositions);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_27()) {
      auditInvocation(
          binaryMapToRefFlowFunction_27, "binaryMapToRefFlowFunction_27", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_27 = binaryMapToRefFlowFunction_27.map();
      if (isDirty_binaryMapToRefFlowFunction_27) {
        mapRef2RefFlowFunction_29.inputUpdated(binaryMapToRefFlowFunction_27);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_29()) {
      auditInvocation(mapRef2RefFlowFunction_29, "mapRef2RefFlowFunction_29", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_29 = mapRef2RefFlowFunction_29.map();
      if (isDirty_mapRef2RefFlowFunction_29) {
        mapRef2RefFlowFunction_31.inputUpdated(mapRef2RefFlowFunction_29);
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
        binaryMapToRefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_33);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_42()) {
      auditInvocation(
          binaryMapToRefFlowFunction_42, "binaryMapToRefFlowFunction_42", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_42 = binaryMapToRefFlowFunction_42.map();
      if (isDirty_binaryMapToRefFlowFunction_42) {
        mapRef2RefFlowFunction_44.inputUpdated(binaryMapToRefFlowFunction_42);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_44()) {
      auditInvocation(mapRef2RefFlowFunction_44, "mapRef2RefFlowFunction_44", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_44 = mapRef2RefFlowFunction_44.map();
      if (isDirty_mapRef2RefFlowFunction_44) {
        mapRef2RefFlowFunction_45.inputUpdated(mapRef2RefFlowFunction_44);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_45()) {
      auditInvocation(mapRef2RefFlowFunction_45, "mapRef2RefFlowFunction_45", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_45 = mapRef2RefFlowFunction_45.map();
      if (isDirty_mapRef2RefFlowFunction_45) {
        mapRef2RefFlowFunction_46.inputUpdated(mapRef2RefFlowFunction_45);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      auditInvocation(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "push", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.push();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_48.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_48()) {
      auditInvocation(pushFlowFunction_48, "pushFlowFunction_48", "push", typedEvent);
      isDirty_pushFlowFunction_48 = pushFlowFunction_48.push();
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
      mergeFlowFunction_34.inputStreamUpdated(handlerTrade);
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
    if (guardCheck_mergeFlowFunction_34()) {
      auditInvocation(mergeFlowFunction_34, "mergeFlowFunction_34", "publishMerge", typedEvent);
      isDirty_mergeFlowFunction_34 = mergeFlowFunction_34.publishMerge();
      if (isDirty_mergeFlowFunction_34) {
        mapRef2RefFlowFunction_36.inputUpdated(mergeFlowFunction_34);
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
        binaryMapToRefFlowFunction_42.input2Updated(mapRef2RefFlowFunction_40);
        binaryMapToRefFlowFunction_58.input2Updated(mapRef2RefFlowFunction_40);
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
        binaryMapToRefFlowFunction_21.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_21.input2Updated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_21()) {
      auditInvocation(
          binaryMapToRefFlowFunction_21, "binaryMapToRefFlowFunction_21", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_21 = binaryMapToRefFlowFunction_21.map();
      if (isDirty_binaryMapToRefFlowFunction_21) {
        mapRef2RefFlowFunction_23.inputUpdated(binaryMapToRefFlowFunction_21);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_50.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_50.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_50()) {
      auditInvocation(
          binaryMapToRefFlowFunction_50, "binaryMapToRefFlowFunction_50", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_50 = binaryMapToRefFlowFunction_50.map();
      if (isDirty_binaryMapToRefFlowFunction_50) {
        mapRef2RefFlowFunction_52.inputUpdated(binaryMapToRefFlowFunction_50);
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
        mapRef2RefFlowFunction_29.inputUpdated(binaryMapToRefFlowFunction_27);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_29()) {
      auditInvocation(mapRef2RefFlowFunction_29, "mapRef2RefFlowFunction_29", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_29 = mapRef2RefFlowFunction_29.map();
      if (isDirty_mapRef2RefFlowFunction_29) {
        mapRef2RefFlowFunction_31.inputUpdated(mapRef2RefFlowFunction_29);
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
        binaryMapToRefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_33);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_42()) {
      auditInvocation(
          binaryMapToRefFlowFunction_42, "binaryMapToRefFlowFunction_42", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_42 = binaryMapToRefFlowFunction_42.map();
      if (isDirty_binaryMapToRefFlowFunction_42) {
        mapRef2RefFlowFunction_44.inputUpdated(binaryMapToRefFlowFunction_42);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_44()) {
      auditInvocation(mapRef2RefFlowFunction_44, "mapRef2RefFlowFunction_44", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_44 = mapRef2RefFlowFunction_44.map();
      if (isDirty_mapRef2RefFlowFunction_44) {
        mapRef2RefFlowFunction_45.inputUpdated(mapRef2RefFlowFunction_44);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_45()) {
      auditInvocation(mapRef2RefFlowFunction_45, "mapRef2RefFlowFunction_45", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_45 = mapRef2RefFlowFunction_45.map();
      if (isDirty_mapRef2RefFlowFunction_45) {
        mapRef2RefFlowFunction_46.inputUpdated(mapRef2RefFlowFunction_45);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      auditInvocation(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "push", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.push();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_48.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_52()) {
      auditInvocation(mapRef2RefFlowFunction_52, "mapRef2RefFlowFunction_52", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_52 = mapRef2RefFlowFunction_52.map();
      if (isDirty_mapRef2RefFlowFunction_52) {
        mapRef2RefFlowFunction_54.inputUpdated(mapRef2RefFlowFunction_52);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      auditInvocation(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        mapRef2RefFlowFunction_56.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_56()) {
      auditInvocation(mapRef2RefFlowFunction_56, "mapRef2RefFlowFunction_56", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_56 = mapRef2RefFlowFunction_56.map();
      if (isDirty_mapRef2RefFlowFunction_56) {
        binaryMapToRefFlowFunction_58.inputUpdated(mapRef2RefFlowFunction_56);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_58()) {
      auditInvocation(
          binaryMapToRefFlowFunction_58, "binaryMapToRefFlowFunction_58", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_58 = binaryMapToRefFlowFunction_58.map();
      if (isDirty_binaryMapToRefFlowFunction_58) {
        mapRef2RefFlowFunction_60.inputUpdated(binaryMapToRefFlowFunction_58);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_60()) {
      auditInvocation(mapRef2RefFlowFunction_60, "mapRef2RefFlowFunction_60", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_60 = mapRef2RefFlowFunction_60.map();
      if (isDirty_mapRef2RefFlowFunction_60) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_60);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_62.inputUpdated(instrumentNetMtm);
      }
    }
    auditInvocation(positionCache, "positionCache", "tradeIn", typedEvent);
    positionCache.tradeIn(typedEvent);
    if (guardCheck_pushFlowFunction_48()) {
      auditInvocation(pushFlowFunction_48, "pushFlowFunction_48", "push", typedEvent);
      isDirty_pushFlowFunction_48 = pushFlowFunction_48.push();
    }
    if (guardCheck_pushFlowFunction_62()) {
      auditInvocation(pushFlowFunction_62, "pushFlowFunction_62", "push", typedEvent);
      isDirty_pushFlowFunction_62 = pushFlowFunction_62.push();
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
      mapRef2RefFlowFunction_36.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
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
        binaryMapToRefFlowFunction_27.input2Updated(groupBySnapshotPositions);
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
        binaryMapToRefFlowFunction_42.input2Updated(mapRef2RefFlowFunction_40);
        binaryMapToRefFlowFunction_58.input2Updated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      auditInvocation(mapRef2RefFlowFunction_10, "mapRef2RefFlowFunction_10", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_21.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_21.input2Updated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_21()) {
      auditInvocation(
          binaryMapToRefFlowFunction_21, "binaryMapToRefFlowFunction_21", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_21 = binaryMapToRefFlowFunction_21.map();
      if (isDirty_binaryMapToRefFlowFunction_21) {
        mapRef2RefFlowFunction_23.inputUpdated(binaryMapToRefFlowFunction_21);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_50.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_50.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_50()) {
      auditInvocation(
          binaryMapToRefFlowFunction_50, "binaryMapToRefFlowFunction_50", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_50 = binaryMapToRefFlowFunction_50.map();
      if (isDirty_binaryMapToRefFlowFunction_50) {
        mapRef2RefFlowFunction_52.inputUpdated(binaryMapToRefFlowFunction_50);
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
        mapRef2RefFlowFunction_29.inputUpdated(binaryMapToRefFlowFunction_27);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_29()) {
      auditInvocation(mapRef2RefFlowFunction_29, "mapRef2RefFlowFunction_29", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_29 = mapRef2RefFlowFunction_29.map();
      if (isDirty_mapRef2RefFlowFunction_29) {
        mapRef2RefFlowFunction_31.inputUpdated(mapRef2RefFlowFunction_29);
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
        binaryMapToRefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_33);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_42()) {
      auditInvocation(
          binaryMapToRefFlowFunction_42, "binaryMapToRefFlowFunction_42", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_42 = binaryMapToRefFlowFunction_42.map();
      if (isDirty_binaryMapToRefFlowFunction_42) {
        mapRef2RefFlowFunction_44.inputUpdated(binaryMapToRefFlowFunction_42);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_44()) {
      auditInvocation(mapRef2RefFlowFunction_44, "mapRef2RefFlowFunction_44", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_44 = mapRef2RefFlowFunction_44.map();
      if (isDirty_mapRef2RefFlowFunction_44) {
        mapRef2RefFlowFunction_45.inputUpdated(mapRef2RefFlowFunction_44);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_45()) {
      auditInvocation(mapRef2RefFlowFunction_45, "mapRef2RefFlowFunction_45", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_45 = mapRef2RefFlowFunction_45.map();
      if (isDirty_mapRef2RefFlowFunction_45) {
        mapRef2RefFlowFunction_46.inputUpdated(mapRef2RefFlowFunction_45);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      auditInvocation(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "push", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.push();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_48.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_52()) {
      auditInvocation(mapRef2RefFlowFunction_52, "mapRef2RefFlowFunction_52", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_52 = mapRef2RefFlowFunction_52.map();
      if (isDirty_mapRef2RefFlowFunction_52) {
        mapRef2RefFlowFunction_54.inputUpdated(mapRef2RefFlowFunction_52);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      auditInvocation(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        mapRef2RefFlowFunction_56.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_56()) {
      auditInvocation(mapRef2RefFlowFunction_56, "mapRef2RefFlowFunction_56", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_56 = mapRef2RefFlowFunction_56.map();
      if (isDirty_mapRef2RefFlowFunction_56) {
        binaryMapToRefFlowFunction_58.inputUpdated(mapRef2RefFlowFunction_56);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_58()) {
      auditInvocation(
          binaryMapToRefFlowFunction_58, "binaryMapToRefFlowFunction_58", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_58 = binaryMapToRefFlowFunction_58.map();
      if (isDirty_binaryMapToRefFlowFunction_58) {
        mapRef2RefFlowFunction_60.inputUpdated(binaryMapToRefFlowFunction_58);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_60()) {
      auditInvocation(mapRef2RefFlowFunction_60, "mapRef2RefFlowFunction_60", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_60 = mapRef2RefFlowFunction_60.map();
      if (isDirty_mapRef2RefFlowFunction_60) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_60);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_62.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_48()) {
      auditInvocation(pushFlowFunction_48, "pushFlowFunction_48", "push", typedEvent);
      isDirty_pushFlowFunction_48 = pushFlowFunction_48.push();
    }
    if (guardCheck_pushFlowFunction_62()) {
      auditInvocation(pushFlowFunction_62, "pushFlowFunction_62", "push", typedEvent);
      isDirty_pushFlowFunction_62 = pushFlowFunction_62.push();
    }
  }

  private void handle_Signal_positionUpdate(Signal typedEvent) {
    auditInvocation(
        handlerSignal_positionUpdate, "handlerSignal_positionUpdate", "onEvent", typedEvent);
    isDirty_handlerSignal_positionUpdate = handlerSignal_positionUpdate.onEvent(typedEvent);
    if (isDirty_handlerSignal_positionUpdate) {
      groupBySnapshotPositions.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_40.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_40.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_25.publishTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_31.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_33.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_44.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_54.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_56.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_60.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
    }
    if (guardCheck_groupBySnapshotPositions()) {
      auditInvocation(groupBySnapshotPositions, "groupBySnapshotPositions", "map", typedEvent);
      isDirty_groupBySnapshotPositions = groupBySnapshotPositions.map();
      if (isDirty_groupBySnapshotPositions) {
        binaryMapToRefFlowFunction_27.input2Updated(groupBySnapshotPositions);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      auditInvocation(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        binaryMapToRefFlowFunction_42.input2Updated(mapRef2RefFlowFunction_40);
        binaryMapToRefFlowFunction_58.input2Updated(mapRef2RefFlowFunction_40);
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
        mapRef2RefFlowFunction_29.inputUpdated(binaryMapToRefFlowFunction_27);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_29()) {
      auditInvocation(mapRef2RefFlowFunction_29, "mapRef2RefFlowFunction_29", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_29 = mapRef2RefFlowFunction_29.map();
      if (isDirty_mapRef2RefFlowFunction_29) {
        mapRef2RefFlowFunction_31.inputUpdated(mapRef2RefFlowFunction_29);
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
        binaryMapToRefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_33);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_42()) {
      auditInvocation(
          binaryMapToRefFlowFunction_42, "binaryMapToRefFlowFunction_42", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_42 = binaryMapToRefFlowFunction_42.map();
      if (isDirty_binaryMapToRefFlowFunction_42) {
        mapRef2RefFlowFunction_44.inputUpdated(binaryMapToRefFlowFunction_42);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_44()) {
      auditInvocation(mapRef2RefFlowFunction_44, "mapRef2RefFlowFunction_44", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_44 = mapRef2RefFlowFunction_44.map();
      if (isDirty_mapRef2RefFlowFunction_44) {
        mapRef2RefFlowFunction_45.inputUpdated(mapRef2RefFlowFunction_44);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_45()) {
      auditInvocation(mapRef2RefFlowFunction_45, "mapRef2RefFlowFunction_45", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_45 = mapRef2RefFlowFunction_45.map();
      if (isDirty_mapRef2RefFlowFunction_45) {
        mapRef2RefFlowFunction_46.inputUpdated(mapRef2RefFlowFunction_45);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      auditInvocation(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "push", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.push();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_48.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      auditInvocation(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        mapRef2RefFlowFunction_56.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_56()) {
      auditInvocation(mapRef2RefFlowFunction_56, "mapRef2RefFlowFunction_56", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_56 = mapRef2RefFlowFunction_56.map();
      if (isDirty_mapRef2RefFlowFunction_56) {
        binaryMapToRefFlowFunction_58.inputUpdated(mapRef2RefFlowFunction_56);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_58()) {
      auditInvocation(
          binaryMapToRefFlowFunction_58, "binaryMapToRefFlowFunction_58", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_58 = binaryMapToRefFlowFunction_58.map();
      if (isDirty_binaryMapToRefFlowFunction_58) {
        mapRef2RefFlowFunction_60.inputUpdated(binaryMapToRefFlowFunction_58);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_60()) {
      auditInvocation(mapRef2RefFlowFunction_60, "mapRef2RefFlowFunction_60", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_60 = mapRef2RefFlowFunction_60.map();
      if (isDirty_mapRef2RefFlowFunction_60) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_60);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_62.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_48()) {
      auditInvocation(pushFlowFunction_48, "pushFlowFunction_48", "push", typedEvent);
      isDirty_pushFlowFunction_48 = pushFlowFunction_48.push();
    }
    if (guardCheck_pushFlowFunction_62()) {
      auditInvocation(pushFlowFunction_62, "pushFlowFunction_62", "push", typedEvent);
      isDirty_pushFlowFunction_62 = pushFlowFunction_62.push();
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
    auditInvocation(namedFeedTableNode_63, "namedFeedTableNode_63", "tableUpdate", typedEvent);
    isDirty_namedFeedTableNode_63 = namedFeedTableNode_63.tableUpdate(typedEvent);
    if (guardCheck_mapRef2RefFlowFunction_4()) {
      auditInvocation(mapRef2RefFlowFunction_4, "mapRef2RefFlowFunction_4", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_4 = mapRef2RefFlowFunction_4.map();
      if (isDirty_mapRef2RefFlowFunction_4) {
        mergeFlowFunction_6.inputStreamUpdated(mapRef2RefFlowFunction_4);
        mergeFlowFunction_34.inputStreamUpdated(mapRef2RefFlowFunction_4);
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
    if (guardCheck_mergeFlowFunction_34()) {
      auditInvocation(mergeFlowFunction_34, "mergeFlowFunction_34", "publishMerge", typedEvent);
      isDirty_mergeFlowFunction_34 = mergeFlowFunction_34.publishMerge();
      if (isDirty_mergeFlowFunction_34) {
        mapRef2RefFlowFunction_36.inputUpdated(mergeFlowFunction_34);
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
        binaryMapToRefFlowFunction_42.input2Updated(mapRef2RefFlowFunction_40);
        binaryMapToRefFlowFunction_58.input2Updated(mapRef2RefFlowFunction_40);
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
        binaryMapToRefFlowFunction_21.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_21.input2Updated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_21()) {
      auditInvocation(
          binaryMapToRefFlowFunction_21, "binaryMapToRefFlowFunction_21", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_21 = binaryMapToRefFlowFunction_21.map();
      if (isDirty_binaryMapToRefFlowFunction_21) {
        mapRef2RefFlowFunction_23.inputUpdated(binaryMapToRefFlowFunction_21);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_50.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_50.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_50()) {
      auditInvocation(
          binaryMapToRefFlowFunction_50, "binaryMapToRefFlowFunction_50", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_50 = binaryMapToRefFlowFunction_50.map();
      if (isDirty_binaryMapToRefFlowFunction_50) {
        mapRef2RefFlowFunction_52.inputUpdated(binaryMapToRefFlowFunction_50);
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
        mapRef2RefFlowFunction_29.inputUpdated(binaryMapToRefFlowFunction_27);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_29()) {
      auditInvocation(mapRef2RefFlowFunction_29, "mapRef2RefFlowFunction_29", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_29 = mapRef2RefFlowFunction_29.map();
      if (isDirty_mapRef2RefFlowFunction_29) {
        mapRef2RefFlowFunction_31.inputUpdated(mapRef2RefFlowFunction_29);
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
        binaryMapToRefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_33);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_42()) {
      auditInvocation(
          binaryMapToRefFlowFunction_42, "binaryMapToRefFlowFunction_42", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_42 = binaryMapToRefFlowFunction_42.map();
      if (isDirty_binaryMapToRefFlowFunction_42) {
        mapRef2RefFlowFunction_44.inputUpdated(binaryMapToRefFlowFunction_42);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_44()) {
      auditInvocation(mapRef2RefFlowFunction_44, "mapRef2RefFlowFunction_44", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_44 = mapRef2RefFlowFunction_44.map();
      if (isDirty_mapRef2RefFlowFunction_44) {
        mapRef2RefFlowFunction_45.inputUpdated(mapRef2RefFlowFunction_44);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_45()) {
      auditInvocation(mapRef2RefFlowFunction_45, "mapRef2RefFlowFunction_45", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_45 = mapRef2RefFlowFunction_45.map();
      if (isDirty_mapRef2RefFlowFunction_45) {
        mapRef2RefFlowFunction_46.inputUpdated(mapRef2RefFlowFunction_45);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      auditInvocation(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "push", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.push();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_48.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_52()) {
      auditInvocation(mapRef2RefFlowFunction_52, "mapRef2RefFlowFunction_52", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_52 = mapRef2RefFlowFunction_52.map();
      if (isDirty_mapRef2RefFlowFunction_52) {
        mapRef2RefFlowFunction_54.inputUpdated(mapRef2RefFlowFunction_52);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      auditInvocation(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        mapRef2RefFlowFunction_56.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_56()) {
      auditInvocation(mapRef2RefFlowFunction_56, "mapRef2RefFlowFunction_56", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_56 = mapRef2RefFlowFunction_56.map();
      if (isDirty_mapRef2RefFlowFunction_56) {
        binaryMapToRefFlowFunction_58.inputUpdated(mapRef2RefFlowFunction_56);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_58()) {
      auditInvocation(
          binaryMapToRefFlowFunction_58, "binaryMapToRefFlowFunction_58", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_58 = binaryMapToRefFlowFunction_58.map();
      if (isDirty_binaryMapToRefFlowFunction_58) {
        mapRef2RefFlowFunction_60.inputUpdated(binaryMapToRefFlowFunction_58);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_60()) {
      auditInvocation(mapRef2RefFlowFunction_60, "mapRef2RefFlowFunction_60", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_60 = mapRef2RefFlowFunction_60.map();
      if (isDirty_mapRef2RefFlowFunction_60) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_60);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_62.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_48()) {
      auditInvocation(pushFlowFunction_48, "pushFlowFunction_48", "push", typedEvent);
      isDirty_pushFlowFunction_48 = pushFlowFunction_48.push();
    }
    if (guardCheck_pushFlowFunction_62()) {
      auditInvocation(pushFlowFunction_62, "pushFlowFunction_62", "push", typedEvent);
      isDirty_pushFlowFunction_62 = pushFlowFunction_62.push();
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
        mergeFlowFunction_34.inputStreamUpdated(mapRef2RefFlowFunction_4);
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
    if (guardCheck_mergeFlowFunction_34()) {
      auditInvocation(mergeFlowFunction_34, "mergeFlowFunction_34", "publishMerge", typedEvent);
      isDirty_mergeFlowFunction_34 = mergeFlowFunction_34.publishMerge();
      if (isDirty_mergeFlowFunction_34) {
        mapRef2RefFlowFunction_36.inputUpdated(mergeFlowFunction_34);
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
        binaryMapToRefFlowFunction_42.input2Updated(mapRef2RefFlowFunction_40);
        binaryMapToRefFlowFunction_58.input2Updated(mapRef2RefFlowFunction_40);
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
        binaryMapToRefFlowFunction_21.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_21.input2Updated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_21()) {
      auditInvocation(
          binaryMapToRefFlowFunction_21, "binaryMapToRefFlowFunction_21", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_21 = binaryMapToRefFlowFunction_21.map();
      if (isDirty_binaryMapToRefFlowFunction_21) {
        mapRef2RefFlowFunction_23.inputUpdated(binaryMapToRefFlowFunction_21);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_50.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_50.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_50()) {
      auditInvocation(
          binaryMapToRefFlowFunction_50, "binaryMapToRefFlowFunction_50", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_50 = binaryMapToRefFlowFunction_50.map();
      if (isDirty_binaryMapToRefFlowFunction_50) {
        mapRef2RefFlowFunction_52.inputUpdated(binaryMapToRefFlowFunction_50);
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
        mapRef2RefFlowFunction_29.inputUpdated(binaryMapToRefFlowFunction_27);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_29()) {
      auditInvocation(mapRef2RefFlowFunction_29, "mapRef2RefFlowFunction_29", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_29 = mapRef2RefFlowFunction_29.map();
      if (isDirty_mapRef2RefFlowFunction_29) {
        mapRef2RefFlowFunction_31.inputUpdated(mapRef2RefFlowFunction_29);
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
        binaryMapToRefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_33);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_42()) {
      auditInvocation(
          binaryMapToRefFlowFunction_42, "binaryMapToRefFlowFunction_42", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_42 = binaryMapToRefFlowFunction_42.map();
      if (isDirty_binaryMapToRefFlowFunction_42) {
        mapRef2RefFlowFunction_44.inputUpdated(binaryMapToRefFlowFunction_42);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_44()) {
      auditInvocation(mapRef2RefFlowFunction_44, "mapRef2RefFlowFunction_44", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_44 = mapRef2RefFlowFunction_44.map();
      if (isDirty_mapRef2RefFlowFunction_44) {
        mapRef2RefFlowFunction_45.inputUpdated(mapRef2RefFlowFunction_44);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_45()) {
      auditInvocation(mapRef2RefFlowFunction_45, "mapRef2RefFlowFunction_45", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_45 = mapRef2RefFlowFunction_45.map();
      if (isDirty_mapRef2RefFlowFunction_45) {
        mapRef2RefFlowFunction_46.inputUpdated(mapRef2RefFlowFunction_45);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      auditInvocation(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "push", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.push();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_48.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_52()) {
      auditInvocation(mapRef2RefFlowFunction_52, "mapRef2RefFlowFunction_52", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_52 = mapRef2RefFlowFunction_52.map();
      if (isDirty_mapRef2RefFlowFunction_52) {
        mapRef2RefFlowFunction_54.inputUpdated(mapRef2RefFlowFunction_52);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      auditInvocation(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        mapRef2RefFlowFunction_56.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_56()) {
      auditInvocation(mapRef2RefFlowFunction_56, "mapRef2RefFlowFunction_56", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_56 = mapRef2RefFlowFunction_56.map();
      if (isDirty_mapRef2RefFlowFunction_56) {
        binaryMapToRefFlowFunction_58.inputUpdated(mapRef2RefFlowFunction_56);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_58()) {
      auditInvocation(
          binaryMapToRefFlowFunction_58, "binaryMapToRefFlowFunction_58", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_58 = binaryMapToRefFlowFunction_58.map();
      if (isDirty_binaryMapToRefFlowFunction_58) {
        mapRef2RefFlowFunction_60.inputUpdated(binaryMapToRefFlowFunction_58);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_60()) {
      auditInvocation(mapRef2RefFlowFunction_60, "mapRef2RefFlowFunction_60", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_60 = mapRef2RefFlowFunction_60.map();
      if (isDirty_mapRef2RefFlowFunction_60) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_60);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_62.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_48()) {
      auditInvocation(pushFlowFunction_48, "pushFlowFunction_48", "push", typedEvent);
      isDirty_pushFlowFunction_48 = pushFlowFunction_48.push();
    }
    if (guardCheck_pushFlowFunction_62()) {
      auditInvocation(pushFlowFunction_62, "pushFlowFunction_62", "push", typedEvent);
      isDirty_pushFlowFunction_62 = pushFlowFunction_62.push();
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
        mergeFlowFunction_34.inputStreamUpdated(mapRef2RefFlowFunction_4);
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
    if (guardCheck_mergeFlowFunction_34()) {
      auditInvocation(mergeFlowFunction_34, "mergeFlowFunction_34", "publishMerge", typedEvent);
      isDirty_mergeFlowFunction_34 = mergeFlowFunction_34.publishMerge();
      if (isDirty_mergeFlowFunction_34) {
        mapRef2RefFlowFunction_36.inputUpdated(mergeFlowFunction_34);
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
        binaryMapToRefFlowFunction_42.input2Updated(mapRef2RefFlowFunction_40);
        binaryMapToRefFlowFunction_58.input2Updated(mapRef2RefFlowFunction_40);
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
        binaryMapToRefFlowFunction_21.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_21.input2Updated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_21()) {
      auditInvocation(
          binaryMapToRefFlowFunction_21, "binaryMapToRefFlowFunction_21", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_21 = binaryMapToRefFlowFunction_21.map();
      if (isDirty_binaryMapToRefFlowFunction_21) {
        mapRef2RefFlowFunction_23.inputUpdated(binaryMapToRefFlowFunction_21);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_50.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_50.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_50()) {
      auditInvocation(
          binaryMapToRefFlowFunction_50, "binaryMapToRefFlowFunction_50", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_50 = binaryMapToRefFlowFunction_50.map();
      if (isDirty_binaryMapToRefFlowFunction_50) {
        mapRef2RefFlowFunction_52.inputUpdated(binaryMapToRefFlowFunction_50);
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
        mapRef2RefFlowFunction_29.inputUpdated(binaryMapToRefFlowFunction_27);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_29()) {
      auditInvocation(mapRef2RefFlowFunction_29, "mapRef2RefFlowFunction_29", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_29 = mapRef2RefFlowFunction_29.map();
      if (isDirty_mapRef2RefFlowFunction_29) {
        mapRef2RefFlowFunction_31.inputUpdated(mapRef2RefFlowFunction_29);
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
        binaryMapToRefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_33);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_42()) {
      auditInvocation(
          binaryMapToRefFlowFunction_42, "binaryMapToRefFlowFunction_42", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_42 = binaryMapToRefFlowFunction_42.map();
      if (isDirty_binaryMapToRefFlowFunction_42) {
        mapRef2RefFlowFunction_44.inputUpdated(binaryMapToRefFlowFunction_42);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_44()) {
      auditInvocation(mapRef2RefFlowFunction_44, "mapRef2RefFlowFunction_44", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_44 = mapRef2RefFlowFunction_44.map();
      if (isDirty_mapRef2RefFlowFunction_44) {
        mapRef2RefFlowFunction_45.inputUpdated(mapRef2RefFlowFunction_44);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_45()) {
      auditInvocation(mapRef2RefFlowFunction_45, "mapRef2RefFlowFunction_45", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_45 = mapRef2RefFlowFunction_45.map();
      if (isDirty_mapRef2RefFlowFunction_45) {
        mapRef2RefFlowFunction_46.inputUpdated(mapRef2RefFlowFunction_45);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      auditInvocation(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "push", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.push();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_48.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_52()) {
      auditInvocation(mapRef2RefFlowFunction_52, "mapRef2RefFlowFunction_52", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_52 = mapRef2RefFlowFunction_52.map();
      if (isDirty_mapRef2RefFlowFunction_52) {
        mapRef2RefFlowFunction_54.inputUpdated(mapRef2RefFlowFunction_52);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      auditInvocation(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        mapRef2RefFlowFunction_56.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_56()) {
      auditInvocation(mapRef2RefFlowFunction_56, "mapRef2RefFlowFunction_56", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_56 = mapRef2RefFlowFunction_56.map();
      if (isDirty_mapRef2RefFlowFunction_56) {
        binaryMapToRefFlowFunction_58.inputUpdated(mapRef2RefFlowFunction_56);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_58()) {
      auditInvocation(
          binaryMapToRefFlowFunction_58, "binaryMapToRefFlowFunction_58", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_58 = binaryMapToRefFlowFunction_58.map();
      if (isDirty_binaryMapToRefFlowFunction_58) {
        mapRef2RefFlowFunction_60.inputUpdated(binaryMapToRefFlowFunction_58);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_60()) {
      auditInvocation(mapRef2RefFlowFunction_60, "mapRef2RefFlowFunction_60", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_60 = mapRef2RefFlowFunction_60.map();
      if (isDirty_mapRef2RefFlowFunction_60) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_60);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_62.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_48()) {
      auditInvocation(pushFlowFunction_48, "pushFlowFunction_48", "push", typedEvent);
      isDirty_pushFlowFunction_48 = pushFlowFunction_48.push();
    }
    if (guardCheck_pushFlowFunction_62()) {
      auditInvocation(pushFlowFunction_62, "pushFlowFunction_62", "push", typedEvent);
      isDirty_pushFlowFunction_62 = pushFlowFunction_62.push();
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
    auditor.nodeRegistered(callBackNode_64, "callBackNode_64");
    auditor.nodeRegistered(callBackNode_88, "callBackNode_88");
    auditor.nodeRegistered(callbackDispatcher, "callbackDispatcher");
    auditor.nodeRegistered(callBackTriggerEvent_1, "callBackTriggerEvent_1");
    auditor.nodeRegistered(callBackTriggerEvent_0, "callBackTriggerEvent_0");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_21, "binaryMapToRefFlowFunction_21");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_27, "binaryMapToRefFlowFunction_27");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_42, "binaryMapToRefFlowFunction_42");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_50, "binaryMapToRefFlowFunction_50");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_58, "binaryMapToRefFlowFunction_58");
    auditor.nodeRegistered(filterFlowFunction_8, "filterFlowFunction_8");
    auditor.nodeRegistered(flatMapFlowFunction_3, "flatMapFlowFunction_3");
    auditor.nodeRegistered(flatMapSnapshotPositions, "flatMapSnapshotPositions");
    auditor.nodeRegistered(groupBySnapshotPositions, "groupBySnapshotPositions");
    auditor.nodeRegistered(instrumentNetMtm, "instrumentNetMtm");
    auditor.nodeRegistered(mapRef2RefFlowFunction_4, "mapRef2RefFlowFunction_4");
    auditor.nodeRegistered(mapRef2RefFlowFunction_5, "mapRef2RefFlowFunction_5");
    auditor.nodeRegistered(mapRef2RefFlowFunction_10, "mapRef2RefFlowFunction_10");
    auditor.nodeRegistered(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12");
    auditor.nodeRegistered(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14");
    auditor.nodeRegistered(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16");
    auditor.nodeRegistered(mapRef2RefFlowFunction_23, "mapRef2RefFlowFunction_23");
    auditor.nodeRegistered(mapRef2RefFlowFunction_25, "mapRef2RefFlowFunction_25");
    auditor.nodeRegistered(mapRef2RefFlowFunction_29, "mapRef2RefFlowFunction_29");
    auditor.nodeRegistered(mapRef2RefFlowFunction_31, "mapRef2RefFlowFunction_31");
    auditor.nodeRegistered(mapRef2RefFlowFunction_33, "mapRef2RefFlowFunction_33");
    auditor.nodeRegistered(mapRef2RefFlowFunction_36, "mapRef2RefFlowFunction_36");
    auditor.nodeRegistered(mapRef2RefFlowFunction_38, "mapRef2RefFlowFunction_38");
    auditor.nodeRegistered(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40");
    auditor.nodeRegistered(mapRef2RefFlowFunction_44, "mapRef2RefFlowFunction_44");
    auditor.nodeRegistered(mapRef2RefFlowFunction_45, "mapRef2RefFlowFunction_45");
    auditor.nodeRegistered(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46");
    auditor.nodeRegistered(mapRef2RefFlowFunction_52, "mapRef2RefFlowFunction_52");
    auditor.nodeRegistered(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54");
    auditor.nodeRegistered(mapRef2RefFlowFunction_56, "mapRef2RefFlowFunction_56");
    auditor.nodeRegistered(mapRef2RefFlowFunction_60, "mapRef2RefFlowFunction_60");
    auditor.nodeRegistered(mergeFlowFunction_6, "mergeFlowFunction_6");
    auditor.nodeRegistered(mergeFlowFunction_34, "mergeFlowFunction_34");
    auditor.nodeRegistered(globalNetMtm, "globalNetMtm");
    auditor.nodeRegistered(pushFlowFunction_48, "pushFlowFunction_48");
    auditor.nodeRegistered(pushFlowFunction_62, "pushFlowFunction_62");
    auditor.nodeRegistered(emptyGroupBy_111, "emptyGroupBy_111");
    auditor.nodeRegistered(emptyGroupBy_174, "emptyGroupBy_174");
    auditor.nodeRegistered(emptyGroupBy_197, "emptyGroupBy_197");
    auditor.nodeRegistered(emptyGroupBy_405, "emptyGroupBy_405");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_9, "groupByFlowFunctionWrapper_9");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_11, "groupByFlowFunctionWrapper_11");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_13, "groupByFlowFunctionWrapper_13");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_15, "groupByFlowFunctionWrapper_15");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_18, "groupByFlowFunctionWrapper_18");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_35, "groupByFlowFunctionWrapper_35");
    auditor.nodeRegistered(groupByMapFlowFunction_22, "groupByMapFlowFunction_22");
    auditor.nodeRegistered(groupByMapFlowFunction_28, "groupByMapFlowFunction_28");
    auditor.nodeRegistered(groupByMapFlowFunction_30, "groupByMapFlowFunction_30");
    auditor.nodeRegistered(groupByMapFlowFunction_39, "groupByMapFlowFunction_39");
    auditor.nodeRegistered(groupByMapFlowFunction_43, "groupByMapFlowFunction_43");
    auditor.nodeRegistered(groupByMapFlowFunction_51, "groupByMapFlowFunction_51");
    auditor.nodeRegistered(groupByMapFlowFunction_53, "groupByMapFlowFunction_53");
    auditor.nodeRegistered(groupByMapFlowFunction_59, "groupByMapFlowFunction_59");
    auditor.nodeRegistered(leftJoin_41, "leftJoin_41");
    auditor.nodeRegistered(leftJoin_57, "leftJoin_57");
    auditor.nodeRegistered(outerJoin_20, "outerJoin_20");
    auditor.nodeRegistered(outerJoin_26, "outerJoin_26");
    auditor.nodeRegistered(outerJoin_49, "outerJoin_49");
    auditor.nodeRegistered(defaultValue_24, "defaultValue_24");
    auditor.nodeRegistered(defaultValue_32, "defaultValue_32");
    auditor.nodeRegistered(defaultValue_37, "defaultValue_37");
    auditor.nodeRegistered(defaultValue_55, "defaultValue_55");
    auditor.nodeRegistered(mapTuple_344, "mapTuple_344");
    auditor.nodeRegistered(mapTuple_355, "mapTuple_355");
    auditor.nodeRegistered(mapTuple_360, "mapTuple_360");
    auditor.nodeRegistered(mapTuple_486, "mapTuple_486");
    auditor.nodeRegistered(mapTuple_497, "mapTuple_497");
    auditor.nodeRegistered(subscriptionManager, "subscriptionManager");
    auditor.nodeRegistered(handlerPositionSnapshot, "handlerPositionSnapshot");
    auditor.nodeRegistered(
        handlerSignal_positionSnapshotReset, "handlerSignal_positionSnapshotReset");
    auditor.nodeRegistered(handlerSignal_positionUpdate, "handlerSignal_positionUpdate");
    auditor.nodeRegistered(handlerTrade, "handlerTrade");
    auditor.nodeRegistered(handlerTradeBatch, "handlerTradeBatch");
    auditor.nodeRegistered(context, "context");
    auditor.nodeRegistered(namedFeedTableNode_63, "namedFeedTableNode_63");
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
    isDirty_binaryMapToRefFlowFunction_21 = false;
    isDirty_binaryMapToRefFlowFunction_27 = false;
    isDirty_binaryMapToRefFlowFunction_42 = false;
    isDirty_binaryMapToRefFlowFunction_50 = false;
    isDirty_binaryMapToRefFlowFunction_58 = false;
    isDirty_callBackNode_64 = false;
    isDirty_callBackNode_88 = false;
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
    isDirty_mapRef2RefFlowFunction_23 = false;
    isDirty_mapRef2RefFlowFunction_25 = false;
    isDirty_mapRef2RefFlowFunction_29 = false;
    isDirty_mapRef2RefFlowFunction_31 = false;
    isDirty_mapRef2RefFlowFunction_33 = false;
    isDirty_mapRef2RefFlowFunction_36 = false;
    isDirty_mapRef2RefFlowFunction_38 = false;
    isDirty_mapRef2RefFlowFunction_40 = false;
    isDirty_mapRef2RefFlowFunction_44 = false;
    isDirty_mapRef2RefFlowFunction_45 = false;
    isDirty_mapRef2RefFlowFunction_46 = false;
    isDirty_mapRef2RefFlowFunction_52 = false;
    isDirty_mapRef2RefFlowFunction_54 = false;
    isDirty_mapRef2RefFlowFunction_56 = false;
    isDirty_mapRef2RefFlowFunction_60 = false;
    isDirty_mergeFlowFunction_6 = false;
    isDirty_mergeFlowFunction_34 = false;
    isDirty_namedFeedTableNode_63 = false;
    isDirty_pushFlowFunction_48 = false;
    isDirty_pushFlowFunction_62 = false;
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
          binaryMapToRefFlowFunction_21, () -> isDirty_binaryMapToRefFlowFunction_21);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_27, () -> isDirty_binaryMapToRefFlowFunction_27);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_42, () -> isDirty_binaryMapToRefFlowFunction_42);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_50, () -> isDirty_binaryMapToRefFlowFunction_50);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_58, () -> isDirty_binaryMapToRefFlowFunction_58);
      dirtyFlagSupplierMap.put(callBackNode_64, () -> isDirty_callBackNode_64);
      dirtyFlagSupplierMap.put(callBackNode_88, () -> isDirty_callBackNode_88);
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
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_23, () -> isDirty_mapRef2RefFlowFunction_23);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_25, () -> isDirty_mapRef2RefFlowFunction_25);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_29, () -> isDirty_mapRef2RefFlowFunction_29);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_31, () -> isDirty_mapRef2RefFlowFunction_31);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_33, () -> isDirty_mapRef2RefFlowFunction_33);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_36, () -> isDirty_mapRef2RefFlowFunction_36);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_38, () -> isDirty_mapRef2RefFlowFunction_38);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_4, () -> isDirty_mapRef2RefFlowFunction_4);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_40, () -> isDirty_mapRef2RefFlowFunction_40);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_44, () -> isDirty_mapRef2RefFlowFunction_44);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_45, () -> isDirty_mapRef2RefFlowFunction_45);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_46, () -> isDirty_mapRef2RefFlowFunction_46);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_5, () -> isDirty_mapRef2RefFlowFunction_5);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_52, () -> isDirty_mapRef2RefFlowFunction_52);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_54, () -> isDirty_mapRef2RefFlowFunction_54);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_56, () -> isDirty_mapRef2RefFlowFunction_56);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_60, () -> isDirty_mapRef2RefFlowFunction_60);
      dirtyFlagSupplierMap.put(mergeFlowFunction_34, () -> isDirty_mergeFlowFunction_34);
      dirtyFlagSupplierMap.put(mergeFlowFunction_6, () -> isDirty_mergeFlowFunction_6);
      dirtyFlagSupplierMap.put(namedFeedTableNode_63, () -> isDirty_namedFeedTableNode_63);
      dirtyFlagSupplierMap.put(pushFlowFunction_48, () -> isDirty_pushFlowFunction_48);
      dirtyFlagSupplierMap.put(pushFlowFunction_62, () -> isDirty_pushFlowFunction_62);
    }
    return dirtyFlagSupplierMap.getOrDefault(node, StaticEventProcessor.ALWAYS_FALSE);
  }

  @Override
  public void setDirty(Object node, boolean dirtyFlag) {
    if (dirtyFlagUpdateMap.isEmpty()) {
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_21, (b) -> isDirty_binaryMapToRefFlowFunction_21 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_27, (b) -> isDirty_binaryMapToRefFlowFunction_27 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_42, (b) -> isDirty_binaryMapToRefFlowFunction_42 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_50, (b) -> isDirty_binaryMapToRefFlowFunction_50 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_58, (b) -> isDirty_binaryMapToRefFlowFunction_58 = b);
      dirtyFlagUpdateMap.put(callBackNode_64, (b) -> isDirty_callBackNode_64 = b);
      dirtyFlagUpdateMap.put(callBackNode_88, (b) -> isDirty_callBackNode_88 = b);
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
          mapRef2RefFlowFunction_23, (b) -> isDirty_mapRef2RefFlowFunction_23 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_25, (b) -> isDirty_mapRef2RefFlowFunction_25 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_29, (b) -> isDirty_mapRef2RefFlowFunction_29 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_31, (b) -> isDirty_mapRef2RefFlowFunction_31 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_33, (b) -> isDirty_mapRef2RefFlowFunction_33 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_36, (b) -> isDirty_mapRef2RefFlowFunction_36 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_38, (b) -> isDirty_mapRef2RefFlowFunction_38 = b);
      dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_4, (b) -> isDirty_mapRef2RefFlowFunction_4 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_40, (b) -> isDirty_mapRef2RefFlowFunction_40 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_44, (b) -> isDirty_mapRef2RefFlowFunction_44 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_45, (b) -> isDirty_mapRef2RefFlowFunction_45 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_46, (b) -> isDirty_mapRef2RefFlowFunction_46 = b);
      dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_5, (b) -> isDirty_mapRef2RefFlowFunction_5 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_52, (b) -> isDirty_mapRef2RefFlowFunction_52 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_54, (b) -> isDirty_mapRef2RefFlowFunction_54 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_56, (b) -> isDirty_mapRef2RefFlowFunction_56 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_60, (b) -> isDirty_mapRef2RefFlowFunction_60 = b);
      dirtyFlagUpdateMap.put(mergeFlowFunction_34, (b) -> isDirty_mergeFlowFunction_34 = b);
      dirtyFlagUpdateMap.put(mergeFlowFunction_6, (b) -> isDirty_mergeFlowFunction_6 = b);
      dirtyFlagUpdateMap.put(namedFeedTableNode_63, (b) -> isDirty_namedFeedTableNode_63 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_48, (b) -> isDirty_pushFlowFunction_48 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_62, (b) -> isDirty_pushFlowFunction_62 = b);
    }
    dirtyFlagUpdateMap.get(node).accept(dirtyFlag);
  }

  private boolean guardCheck_eventLogger() {
    return isDirty_clock;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_21() {
    return isDirty_mapRef2RefFlowFunction_10 | isDirty_mapRef2RefFlowFunction_12;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_27() {
    return isDirty_groupBySnapshotPositions | isDirty_mapRef2RefFlowFunction_25;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_42() {
    return isDirty_mapRef2RefFlowFunction_33 | isDirty_mapRef2RefFlowFunction_40;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_50() {
    return isDirty_mapRef2RefFlowFunction_14 | isDirty_mapRef2RefFlowFunction_16;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_58() {
    return isDirty_mapRef2RefFlowFunction_40 | isDirty_mapRef2RefFlowFunction_56;
  }

  private boolean guardCheck_filterFlowFunction_8() {
    return isDirty_mergeFlowFunction_6;
  }

  private boolean guardCheck_flatMapFlowFunction_3() {
    return isDirty_callBackNode_64;
  }

  private boolean guardCheck_flatMapSnapshotPositions() {
    return isDirty_callBackNode_88;
  }

  private boolean guardCheck_groupBySnapshotPositions() {
    return isDirty_flatMapSnapshotPositions
        | isDirty_handlerSignal_positionSnapshotReset
        | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_instrumentNetMtm() {
    return isDirty_mapRef2RefFlowFunction_60;
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
    return isDirty_binaryMapToRefFlowFunction_21;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_25() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_23;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_29() {
    return isDirty_binaryMapToRefFlowFunction_27;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_31() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_29;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_33() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_31;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_36() {
    return isDirty_handlerSignal_positionSnapshotReset | isDirty_mergeFlowFunction_34;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_38() {
    return isDirty_mapRef2RefFlowFunction_36;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_40() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_38;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_44() {
    return isDirty_binaryMapToRefFlowFunction_42 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_45() {
    return isDirty_mapRef2RefFlowFunction_44;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_46() {
    return isDirty_mapRef2RefFlowFunction_45;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_52() {
    return isDirty_binaryMapToRefFlowFunction_50;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_54() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_52;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_56() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_54;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_60() {
    return isDirty_binaryMapToRefFlowFunction_58 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mergeFlowFunction_6() {
    return isDirty_mapRef2RefFlowFunction_4 | isDirty_mapRef2RefFlowFunction_5;
  }

  private boolean guardCheck_mergeFlowFunction_34() {
    return isDirty_handlerTrade | isDirty_mapRef2RefFlowFunction_4;
  }

  private boolean guardCheck_globalNetMtm() {
    return isDirty_mapRef2RefFlowFunction_46;
  }

  private boolean guardCheck_pushFlowFunction_48() {
    return isDirty_globalNetMtm;
  }

  private boolean guardCheck_pushFlowFunction_62() {
    return isDirty_instrumentNetMtm;
  }

  private boolean guardCheck_groupByMapFlowFunction_30() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_groupByMapFlowFunction_39() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_groupByMapFlowFunction_53() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_context() {
    return isDirty_clock;
  }

  private boolean guardCheck_globalNetMtmListener() {
    return isDirty_pushFlowFunction_48;
  }

  private boolean guardCheck_instrumentNetMtmListener() {
    return isDirty_pushFlowFunction_62;
  }

  private boolean guardCheck_derivedRateNode() {
    return isDirty_namedFeedTableNode_63;
  }

  private boolean guardCheck_eventFeedBatcher() {
    return isDirty_namedFeedTableNode_63;
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
