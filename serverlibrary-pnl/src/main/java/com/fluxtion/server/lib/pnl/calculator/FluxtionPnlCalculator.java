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
import com.fluxtion.server.lib.pnl.MtmInstrument;
import com.fluxtion.server.lib.pnl.NetMarkToMarket;
import com.fluxtion.server.lib.pnl.PositionSnapshot;
import com.fluxtion.server.lib.pnl.Trade;
import com.fluxtion.server.lib.pnl.TradeBatch;
import com.fluxtion.server.lib.pnl.dto.DtoHelper;
import com.fluxtion.server.lib.pnl.dto.MidPriceBatchDto;
import com.fluxtion.server.lib.pnl.dto.MidPriceDto;
import com.fluxtion.server.lib.pnl.dto.PositionSnapshotDto;
import com.fluxtion.server.lib.pnl.dto.SymbolDto;
import com.fluxtion.server.lib.pnl.dto.TradeBatchDto;
import com.fluxtion.server.lib.pnl.dto.TradeDto;
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
 *   <li>com.fluxtion.server.lib.pnl.MtmInstrument
 *   <li>com.fluxtion.server.lib.pnl.PositionSnapshot
 *   <li>com.fluxtion.server.lib.pnl.Trade
 *   <li>com.fluxtion.server.lib.pnl.TradeBatch
 *   <li>com.fluxtion.server.lib.pnl.dto.MidPriceBatchDto
 *   <li>com.fluxtion.server.lib.pnl.dto.MidPriceDto
 *   <li>com.fluxtion.server.lib.pnl.dto.PositionSnapshotDto
 *   <li>com.fluxtion.server.lib.pnl.dto.TradeBatchDto
 *   <li>com.fluxtion.server.lib.pnl.dto.TradeDto
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
  private final CallBackNode callBackNode_63 = new CallBackNode<>(callBackTriggerEvent_0);
  private final InstanceCallbackEvent_1 callBackTriggerEvent_1 = new InstanceCallbackEvent_1();
  private final CallBackNode callBackNode_82 = new CallBackNode<>(callBackTriggerEvent_1);
  private final CallbackDispatcherImpl callbackDispatcher = new CallbackDispatcherImpl();
  public final Clock clock = new Clock();
  public final DerivedRateNode derivedRateNode = new DerivedRateNode();
  private final EmptyGroupBy emptyGroupBy_74 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_18 = new DefaultValue<>(emptyGroupBy_74);
  private final EmptyGroupBy emptyGroupBy_132 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_35 = new DefaultValue<>(emptyGroupBy_132);
  private final EmptyGroupBy emptyGroupBy_316 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_52 = new DefaultValue<>(emptyGroupBy_316);
  public final EventLogManager eventLogger = new EventLogManager();
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_7 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, SingleInstrumentPosMtmAggregate::dealt);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_9 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getContraInstrument, Mappers::identity, SingleInstrumentPosMtmAggregate::contra);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_11 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, InstrumentPosMtmAggregate::dealt);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_13 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getContraInstrument, Mappers::identity, InstrumentPosMtmAggregate::contra);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_16 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, FeeInstrumentPosMtmAggregate::new);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_23 =
      new GroupByFlowFunctionWrapper<>(
          InstrumentPosition::instrument, Mappers::identity, AggregateIdentityFlowFunction::new);
  private final GroupByMapFlowFunction groupByMapFlowFunction_20 =
      new GroupByMapFlowFunction(derivedRateNode::calculateFeeMtm);
  private final GroupByMapFlowFunction groupByMapFlowFunction_33 =
      new GroupByMapFlowFunction(derivedRateNode::calculateInstrumentPosMtm);
  private final GroupByMapFlowFunction groupByMapFlowFunction_50 =
      new GroupByMapFlowFunction(derivedRateNode::calculateInstrumentPosMtm);
  private final LeftJoin leftJoin_37 = new LeftJoin();
  private final LeftJoin leftJoin_56 = new LeftJoin();
  private final MapTuple mapTuple_273 = new MapTuple<>(NetMarkToMarket::combine);
  private final GroupByMapFlowFunction groupByMapFlowFunction_39 =
      new GroupByMapFlowFunction(mapTuple_273::mapTuple);
  private final MapTuple mapTuple_283 =
      new MapTuple<>(InstrumentPosMtm::overwriteInstrumentPositionWithSnapshot);
  private final GroupByMapFlowFunction groupByMapFlowFunction_31 =
      new GroupByMapFlowFunction(mapTuple_283::mapTuple);
  private final MapTuple mapTuple_287 = new MapTuple<>(InstrumentPosMtm::merge);
  private final GroupByMapFlowFunction groupByMapFlowFunction_27 =
      new GroupByMapFlowFunction(mapTuple_287::mapTuple);
  private final MapTuple mapTuple_399 = new MapTuple<>(NetMarkToMarket::combine);
  private final GroupByMapFlowFunction groupByMapFlowFunction_58 =
      new GroupByMapFlowFunction(mapTuple_399::mapTuple);
  private final MapTuple mapTuple_409 = new MapTuple<>(InstrumentPosMtm::merge);
  private final GroupByMapFlowFunction groupByMapFlowFunction_48 =
      new GroupByMapFlowFunction(mapTuple_409::mapTuple);
  private final NamedFeedTableNode namedFeedTableNode_62 =
      new NamedFeedTableNode<>("symbolFeed", SymbolDto::getSymbol);
  public final EventFeedConnector eventFeedBatcher = new EventFeedConnector(namedFeedTableNode_62);
  public final NodeNameAuditor nodeNameLookup = new NodeNameAuditor();
  private final OuterJoin outerJoin_25 = new OuterJoin();
  private final OuterJoin outerJoin_29 = new OuterJoin();
  private final OuterJoin outerJoin_46 = new OuterJoin();
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
          flatMapSnapshotPositions, groupByFlowFunctionWrapper_23::aggregate);
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
  private final MergeFlowFunction mergeFlowFunction_4 =
      new MergeFlowFunction<>(Arrays.asList(handlerTrade, flatMapFlowFunction_3));
  private final MergeFlowFunction mergeFlowFunction_15 =
      new MergeFlowFunction<>(Arrays.asList(handlerTrade, flatMapFlowFunction_3));
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_17 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_15, groupByFlowFunctionWrapper_16::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_19 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_17, defaultValue_18::getOrDefault);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_21 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_19, groupByMapFlowFunction_20::mapValues);
  private final SinkPublisher positionSnapshotListener =
      new SinkPublisher<>("positionSnapshotListener");
  public final ServiceRegistryNode serviceRegistry = new ServiceRegistryNode();
  private final TradeSequenceFilter tradeSequenceFilter_5 = new TradeSequenceFilter();
  private final FilterFlowFunction filterFlowFunction_6 =
      new FilterFlowFunction<>(
          mergeFlowFunction_4, tradeSequenceFilter_5::checkTradeSequenceNumber);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_8 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_6, groupByFlowFunctionWrapper_7::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_10 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_6, groupByFlowFunctionWrapper_9::aggregate);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_26 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_8, mapRef2RefFlowFunction_10, outerJoin_25::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_12 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_6, groupByFlowFunctionWrapper_11::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_14 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_6, groupByFlowFunctionWrapper_13::aggregate);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_47 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_12, mapRef2RefFlowFunction_14, outerJoin_46::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_28 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_26, groupByMapFlowFunction_27::mapValues);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_30 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_28, groupBySnapshotPositions, outerJoin_29::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_32 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_30, groupByMapFlowFunction_31::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_34 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_32, groupByMapFlowFunction_33::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_36 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_34, defaultValue_35::getOrDefault);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_38 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_36, mapRef2RefFlowFunction_21, leftJoin_37::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_40 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_38, groupByMapFlowFunction_39::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_41 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_40, GroupBy<Object, Object>::toMap);
  public final MapRef2RefFlowFunction globalNetMtm =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_41, NetMarkToMarket::markToMarketSum);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_49 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_47, groupByMapFlowFunction_48::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_51 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_49, groupByMapFlowFunction_50::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_53 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_51, defaultValue_52::getOrDefault);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_57 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_53, mapRef2RefFlowFunction_21, leftJoin_56::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_54 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_53, GroupBy<Object, Object>::toMap);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_59 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_57, groupByMapFlowFunction_58::mapValues);
  public final MapRef2RefFlowFunction instrumentNetMtm =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_59, GroupBy<Object, Object>::toMap);
  private final PushFlowFunction pushFlowFunction_43 =
      new PushFlowFunction<>(globalNetMtm, globalNetMtmListener::publish);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_44 =
      new MapRef2RefFlowFunction<>(pushFlowFunction_43, DtoHelper::formatPosition);
  private final PushFlowFunction pushFlowFunction_45 =
      new PushFlowFunction<>(mapRef2RefFlowFunction_44, positionSnapshotListener::publish);
  private final PushFlowFunction pushFlowFunction_55 =
      new PushFlowFunction<>(mapRef2RefFlowFunction_54, positionCache::mtmUpdated);
  private final PushFlowFunction pushFlowFunction_61 =
      new PushFlowFunction<>(instrumentNetMtm, instrumentNetMtmListener::publish);
  private final ExportFunctionAuditEvent functionAudit = new ExportFunctionAuditEvent();
  //Dirty flags
  private boolean initCalled = false;
  private boolean processing = false;
  private boolean buffering = false;
  private final IdentityHashMap<Object, BooleanSupplier> dirtyFlagSupplierMap =
      new IdentityHashMap<>(46);
  private final IdentityHashMap<Object, Consumer<Boolean>> dirtyFlagUpdateMap =
      new IdentityHashMap<>(46);

  private boolean isDirty_binaryMapToRefFlowFunction_26 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_30 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_38 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_47 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_57 = false;
  private boolean isDirty_callBackNode_63 = false;
  private boolean isDirty_callBackNode_82 = false;
  private boolean isDirty_clock = false;
  private boolean isDirty_derivedRateNode = false;
  private boolean isDirty_filterFlowFunction_6 = false;
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
  private boolean isDirty_mapRef2RefFlowFunction_8 = false;
  private boolean isDirty_mapRef2RefFlowFunction_10 = false;
  private boolean isDirty_mapRef2RefFlowFunction_12 = false;
  private boolean isDirty_mapRef2RefFlowFunction_14 = false;
  private boolean isDirty_mapRef2RefFlowFunction_17 = false;
  private boolean isDirty_mapRef2RefFlowFunction_19 = false;
  private boolean isDirty_mapRef2RefFlowFunction_21 = false;
  private boolean isDirty_mapRef2RefFlowFunction_28 = false;
  private boolean isDirty_mapRef2RefFlowFunction_32 = false;
  private boolean isDirty_mapRef2RefFlowFunction_34 = false;
  private boolean isDirty_mapRef2RefFlowFunction_36 = false;
  private boolean isDirty_mapRef2RefFlowFunction_40 = false;
  private boolean isDirty_mapRef2RefFlowFunction_41 = false;
  private boolean isDirty_mapRef2RefFlowFunction_44 = false;
  private boolean isDirty_mapRef2RefFlowFunction_49 = false;
  private boolean isDirty_mapRef2RefFlowFunction_51 = false;
  private boolean isDirty_mapRef2RefFlowFunction_53 = false;
  private boolean isDirty_mapRef2RefFlowFunction_54 = false;
  private boolean isDirty_mapRef2RefFlowFunction_59 = false;
  private boolean isDirty_mergeFlowFunction_4 = false;
  private boolean isDirty_mergeFlowFunction_15 = false;
  private boolean isDirty_namedFeedTableNode_62 = false;
  private boolean isDirty_pushFlowFunction_43 = false;
  private boolean isDirty_pushFlowFunction_45 = false;
  private boolean isDirty_pushFlowFunction_55 = false;
  private boolean isDirty_pushFlowFunction_61 = false;

  //Forked declarations

  //Filter constants

  //unknown event handler
  private Consumer unKnownEventHandler = (e) -> {};

  public FluxtionPnlCalculator(Map<Object, Object> contextMap) {
    if (context != null) {
      context.replaceMappings(contextMap);
    }
    eventLogger.trace = (boolean) false;
    eventLogger.printEventToString = (boolean) true;
    eventLogger.printThreadName = (boolean) true;
    eventLogger.traceLevel = com.fluxtion.runtime.audit.EventLogControlEvent.LogLevel.NONE;
    eventLogger.clock = clock;
    binaryMapToRefFlowFunction_26.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_30.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_38.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_47.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_57.setEventProcessorContext(context);
    filterFlowFunction_6.setEventProcessorContext(context);
    flatMapFlowFunction_3.callback = callBackNode_63;
    flatMapFlowFunction_3.dirtyStateMonitor = callbackDispatcher;
    flatMapSnapshotPositions.callback = callBackNode_82;
    flatMapSnapshotPositions.dirtyStateMonitor = callbackDispatcher;
    globalNetMtm.setEventProcessorContext(context);
    groupBySnapshotPositions.setEventProcessorContext(context);
    groupBySnapshotPositions.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    groupBySnapshotPositions.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    instrumentNetMtm.setEventProcessorContext(context);
    mapRef2RefFlowFunction_8.setEventProcessorContext(context);
    mapRef2RefFlowFunction_8.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_10.setEventProcessorContext(context);
    mapRef2RefFlowFunction_10.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_12.setEventProcessorContext(context);
    mapRef2RefFlowFunction_12.setPublishTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_12.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_14.setEventProcessorContext(context);
    mapRef2RefFlowFunction_14.setPublishTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_14.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_17.setEventProcessorContext(context);
    mapRef2RefFlowFunction_17.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_19.setEventProcessorContext(context);
    mapRef2RefFlowFunction_21.setEventProcessorContext(context);
    mapRef2RefFlowFunction_21.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_21.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_28.setEventProcessorContext(context);
    mapRef2RefFlowFunction_32.setEventProcessorContext(context);
    mapRef2RefFlowFunction_34.setEventProcessorContext(context);
    mapRef2RefFlowFunction_34.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_36.setEventProcessorContext(context);
    mapRef2RefFlowFunction_36.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_40.setEventProcessorContext(context);
    mapRef2RefFlowFunction_40.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_41.setEventProcessorContext(context);
    mapRef2RefFlowFunction_44.setEventProcessorContext(context);
    mapRef2RefFlowFunction_49.setEventProcessorContext(context);
    mapRef2RefFlowFunction_51.setEventProcessorContext(context);
    mapRef2RefFlowFunction_51.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_53.setEventProcessorContext(context);
    mapRef2RefFlowFunction_53.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_54.setEventProcessorContext(context);
    mapRef2RefFlowFunction_59.setEventProcessorContext(context);
    mapRef2RefFlowFunction_59.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mergeFlowFunction_4.dirtyStateMonitor = callbackDispatcher;
    mergeFlowFunction_15.dirtyStateMonitor = callbackDispatcher;
    pushFlowFunction_43.setEventProcessorContext(context);
    pushFlowFunction_45.setEventProcessorContext(context);
    pushFlowFunction_55.setEventProcessorContext(context);
    pushFlowFunction_61.setEventProcessorContext(context);
    context.setClock(clock);
    globalNetMtmListener.setEventProcessorContext(context);
    instrumentNetMtmListener.setEventProcessorContext(context);
    positionSnapshotListener.setEventProcessorContext(context);
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
    namedFeedTableNode_62.initialise();
    namedFeedTableNode_62.init();
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
    mapRef2RefFlowFunction_17.initialiseEventStream();
    mapRef2RefFlowFunction_19.initialiseEventStream();
    mapRef2RefFlowFunction_21.initialiseEventStream();
    tradeSequenceFilter_5.init();
    filterFlowFunction_6.initialiseEventStream();
    mapRef2RefFlowFunction_8.initialiseEventStream();
    mapRef2RefFlowFunction_10.initialiseEventStream();
    binaryMapToRefFlowFunction_26.initialiseEventStream();
    mapRef2RefFlowFunction_12.initialiseEventStream();
    mapRef2RefFlowFunction_14.initialiseEventStream();
    binaryMapToRefFlowFunction_47.initialiseEventStream();
    mapRef2RefFlowFunction_28.initialiseEventStream();
    binaryMapToRefFlowFunction_30.initialiseEventStream();
    mapRef2RefFlowFunction_32.initialiseEventStream();
    mapRef2RefFlowFunction_34.initialiseEventStream();
    mapRef2RefFlowFunction_36.initialiseEventStream();
    binaryMapToRefFlowFunction_38.initialiseEventStream();
    mapRef2RefFlowFunction_40.initialiseEventStream();
    mapRef2RefFlowFunction_41.initialiseEventStream();
    globalNetMtm.initialiseEventStream();
    mapRef2RefFlowFunction_49.initialiseEventStream();
    mapRef2RefFlowFunction_51.initialiseEventStream();
    mapRef2RefFlowFunction_53.initialiseEventStream();
    binaryMapToRefFlowFunction_57.initialiseEventStream();
    mapRef2RefFlowFunction_54.initialiseEventStream();
    mapRef2RefFlowFunction_59.initialiseEventStream();
    instrumentNetMtm.initialiseEventStream();
    pushFlowFunction_43.initialiseEventStream();
    mapRef2RefFlowFunction_44.initialiseEventStream();
    pushFlowFunction_45.initialiseEventStream();
    pushFlowFunction_55.initialiseEventStream();
    pushFlowFunction_61.initialiseEventStream();
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
    } else if (event instanceof com.fluxtion.server.lib.pnl.dto.MidPriceBatchDto) {
      MidPriceBatchDto typedEvent = (MidPriceBatchDto) event;
      handleEvent(typedEvent);
    } else if (event instanceof com.fluxtion.server.lib.pnl.dto.MidPriceDto) {
      MidPriceDto typedEvent = (MidPriceDto) event;
      handleEvent(typedEvent);
    } else if (event instanceof com.fluxtion.server.lib.pnl.dto.PositionSnapshotDto) {
      PositionSnapshotDto typedEvent = (PositionSnapshotDto) event;
      handleEvent(typedEvent);
    } else if (event instanceof com.fluxtion.server.lib.pnl.dto.TradeBatchDto) {
      TradeBatchDto typedEvent = (TradeBatchDto) event;
      handleEvent(typedEvent);
    } else if (event instanceof com.fluxtion.server.lib.pnl.dto.TradeDto) {
      TradeDto typedEvent = (TradeDto) event;
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
    eventLogger.calculationLogConfig(typedEvent);
    afterEvent();
  }

  public void handleEvent(InstanceCallbackEvent_0 typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_callBackNode_63 = callBackNode_63.onEvent(typedEvent);
    if (guardCheck_flatMapFlowFunction_3()) {
      isDirty_flatMapFlowFunction_3 = true;
      flatMapFlowFunction_3.callbackReceived();
      if (isDirty_flatMapFlowFunction_3) {
        mergeFlowFunction_4.inputStreamUpdated(flatMapFlowFunction_3);
        mergeFlowFunction_15.inputStreamUpdated(flatMapFlowFunction_3);
      }
    }
    if (guardCheck_mergeFlowFunction_4()) {
      isDirty_mergeFlowFunction_4 = mergeFlowFunction_4.publishMerge();
      if (isDirty_mergeFlowFunction_4) {
        filterFlowFunction_6.inputUpdated(mergeFlowFunction_4);
      }
    }
    if (guardCheck_mergeFlowFunction_15()) {
      isDirty_mergeFlowFunction_15 = mergeFlowFunction_15.publishMerge();
      if (isDirty_mergeFlowFunction_15) {
        mapRef2RefFlowFunction_17.inputUpdated(mergeFlowFunction_15);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_17()) {
      isDirty_mapRef2RefFlowFunction_17 = mapRef2RefFlowFunction_17.map();
      if (isDirty_mapRef2RefFlowFunction_17) {
        mapRef2RefFlowFunction_19.inputUpdated(mapRef2RefFlowFunction_17);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_19()) {
      isDirty_mapRef2RefFlowFunction_19 = mapRef2RefFlowFunction_19.map();
      if (isDirty_mapRef2RefFlowFunction_19) {
        mapRef2RefFlowFunction_21.inputUpdated(mapRef2RefFlowFunction_19);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_21()) {
      isDirty_mapRef2RefFlowFunction_21 = mapRef2RefFlowFunction_21.map();
      if (isDirty_mapRef2RefFlowFunction_21) {
        binaryMapToRefFlowFunction_38.input2Updated(mapRef2RefFlowFunction_21);
        binaryMapToRefFlowFunction_57.input2Updated(mapRef2RefFlowFunction_21);
      }
    }
    if (guardCheck_filterFlowFunction_6()) {
      isDirty_filterFlowFunction_6 = filterFlowFunction_6.filter();
      if (isDirty_filterFlowFunction_6) {
        mapRef2RefFlowFunction_8.inputUpdated(filterFlowFunction_6);
        mapRef2RefFlowFunction_10.inputUpdated(filterFlowFunction_6);
        mapRef2RefFlowFunction_12.inputUpdated(filterFlowFunction_6);
        mapRef2RefFlowFunction_14.inputUpdated(filterFlowFunction_6);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_8()) {
      isDirty_mapRef2RefFlowFunction_8 = mapRef2RefFlowFunction_8.map();
      if (isDirty_mapRef2RefFlowFunction_8) {
        binaryMapToRefFlowFunction_26.inputUpdated(mapRef2RefFlowFunction_8);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_26.input2Updated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_26()) {
      isDirty_binaryMapToRefFlowFunction_26 = binaryMapToRefFlowFunction_26.map();
      if (isDirty_binaryMapToRefFlowFunction_26) {
        mapRef2RefFlowFunction_28.inputUpdated(binaryMapToRefFlowFunction_26);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_47.inputUpdated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_47.input2Updated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_47()) {
      isDirty_binaryMapToRefFlowFunction_47 = binaryMapToRefFlowFunction_47.map();
      if (isDirty_binaryMapToRefFlowFunction_47) {
        mapRef2RefFlowFunction_49.inputUpdated(binaryMapToRefFlowFunction_47);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_28()) {
      isDirty_mapRef2RefFlowFunction_28 = mapRef2RefFlowFunction_28.map();
      if (isDirty_mapRef2RefFlowFunction_28) {
        binaryMapToRefFlowFunction_30.inputUpdated(mapRef2RefFlowFunction_28);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_30()) {
      isDirty_binaryMapToRefFlowFunction_30 = binaryMapToRefFlowFunction_30.map();
      if (isDirty_binaryMapToRefFlowFunction_30) {
        mapRef2RefFlowFunction_32.inputUpdated(binaryMapToRefFlowFunction_30);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_32()) {
      isDirty_mapRef2RefFlowFunction_32 = mapRef2RefFlowFunction_32.map();
      if (isDirty_mapRef2RefFlowFunction_32) {
        mapRef2RefFlowFunction_34.inputUpdated(mapRef2RefFlowFunction_32);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_34()) {
      isDirty_mapRef2RefFlowFunction_34 = mapRef2RefFlowFunction_34.map();
      if (isDirty_mapRef2RefFlowFunction_34) {
        mapRef2RefFlowFunction_36.inputUpdated(mapRef2RefFlowFunction_34);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_36()) {
      isDirty_mapRef2RefFlowFunction_36 = mapRef2RefFlowFunction_36.map();
      if (isDirty_mapRef2RefFlowFunction_36) {
        binaryMapToRefFlowFunction_38.inputUpdated(mapRef2RefFlowFunction_36);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_38()) {
      isDirty_binaryMapToRefFlowFunction_38 = binaryMapToRefFlowFunction_38.map();
      if (isDirty_binaryMapToRefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(binaryMapToRefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        mapRef2RefFlowFunction_41.inputUpdated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_41()) {
      isDirty_mapRef2RefFlowFunction_41 = mapRef2RefFlowFunction_41.map();
      if (isDirty_mapRef2RefFlowFunction_41) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_41);
      }
    }
    if (guardCheck_globalNetMtm()) {
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_43.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_49()) {
      isDirty_mapRef2RefFlowFunction_49 = mapRef2RefFlowFunction_49.map();
      if (isDirty_mapRef2RefFlowFunction_49) {
        mapRef2RefFlowFunction_51.inputUpdated(mapRef2RefFlowFunction_49);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_51()) {
      isDirty_mapRef2RefFlowFunction_51 = mapRef2RefFlowFunction_51.map();
      if (isDirty_mapRef2RefFlowFunction_51) {
        mapRef2RefFlowFunction_53.inputUpdated(mapRef2RefFlowFunction_51);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_53()) {
      isDirty_mapRef2RefFlowFunction_53 = mapRef2RefFlowFunction_53.map();
      if (isDirty_mapRef2RefFlowFunction_53) {
        binaryMapToRefFlowFunction_57.inputUpdated(mapRef2RefFlowFunction_53);
        mapRef2RefFlowFunction_54.inputUpdated(mapRef2RefFlowFunction_53);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_57()) {
      isDirty_binaryMapToRefFlowFunction_57 = binaryMapToRefFlowFunction_57.map();
      if (isDirty_binaryMapToRefFlowFunction_57) {
        mapRef2RefFlowFunction_59.inputUpdated(binaryMapToRefFlowFunction_57);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        pushFlowFunction_55.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_59()) {
      isDirty_mapRef2RefFlowFunction_59 = mapRef2RefFlowFunction_59.map();
      if (isDirty_mapRef2RefFlowFunction_59) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_59);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_61.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_43()) {
      isDirty_pushFlowFunction_43 = pushFlowFunction_43.push();
      if (isDirty_pushFlowFunction_43) {
        mapRef2RefFlowFunction_44.inputUpdated(pushFlowFunction_43);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_44()) {
      isDirty_mapRef2RefFlowFunction_44 = mapRef2RefFlowFunction_44.map();
      if (isDirty_mapRef2RefFlowFunction_44) {
        pushFlowFunction_45.inputUpdated(mapRef2RefFlowFunction_44);
      }
    }
    if (guardCheck_pushFlowFunction_45()) {
      isDirty_pushFlowFunction_45 = pushFlowFunction_45.push();
    }
    if (guardCheck_pushFlowFunction_55()) {
      isDirty_pushFlowFunction_55 = pushFlowFunction_55.push();
    }
    if (guardCheck_pushFlowFunction_61()) {
      isDirty_pushFlowFunction_61 = pushFlowFunction_61.push();
    }
    afterEvent();
  }

  public void handleEvent(InstanceCallbackEvent_1 typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_callBackNode_82 = callBackNode_82.onEvent(typedEvent);
    if (guardCheck_flatMapSnapshotPositions()) {
      isDirty_flatMapSnapshotPositions = true;
      flatMapSnapshotPositions.callbackReceived();
      if (isDirty_flatMapSnapshotPositions) {
        groupBySnapshotPositions.inputUpdated(flatMapSnapshotPositions);
      }
    }
    if (guardCheck_groupBySnapshotPositions()) {
      isDirty_groupBySnapshotPositions = groupBySnapshotPositions.map();
      if (isDirty_groupBySnapshotPositions) {
        binaryMapToRefFlowFunction_30.input2Updated(groupBySnapshotPositions);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_30()) {
      isDirty_binaryMapToRefFlowFunction_30 = binaryMapToRefFlowFunction_30.map();
      if (isDirty_binaryMapToRefFlowFunction_30) {
        mapRef2RefFlowFunction_32.inputUpdated(binaryMapToRefFlowFunction_30);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_32()) {
      isDirty_mapRef2RefFlowFunction_32 = mapRef2RefFlowFunction_32.map();
      if (isDirty_mapRef2RefFlowFunction_32) {
        mapRef2RefFlowFunction_34.inputUpdated(mapRef2RefFlowFunction_32);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_34()) {
      isDirty_mapRef2RefFlowFunction_34 = mapRef2RefFlowFunction_34.map();
      if (isDirty_mapRef2RefFlowFunction_34) {
        mapRef2RefFlowFunction_36.inputUpdated(mapRef2RefFlowFunction_34);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_36()) {
      isDirty_mapRef2RefFlowFunction_36 = mapRef2RefFlowFunction_36.map();
      if (isDirty_mapRef2RefFlowFunction_36) {
        binaryMapToRefFlowFunction_38.inputUpdated(mapRef2RefFlowFunction_36);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_38()) {
      isDirty_binaryMapToRefFlowFunction_38 = binaryMapToRefFlowFunction_38.map();
      if (isDirty_binaryMapToRefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(binaryMapToRefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        mapRef2RefFlowFunction_41.inputUpdated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_41()) {
      isDirty_mapRef2RefFlowFunction_41 = mapRef2RefFlowFunction_41.map();
      if (isDirty_mapRef2RefFlowFunction_41) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_41);
      }
    }
    if (guardCheck_globalNetMtm()) {
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_43.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_43()) {
      isDirty_pushFlowFunction_43 = pushFlowFunction_43.push();
      if (isDirty_pushFlowFunction_43) {
        mapRef2RefFlowFunction_44.inputUpdated(pushFlowFunction_43);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_44()) {
      isDirty_mapRef2RefFlowFunction_44 = mapRef2RefFlowFunction_44.map();
      if (isDirty_mapRef2RefFlowFunction_44) {
        pushFlowFunction_45.inputUpdated(mapRef2RefFlowFunction_44);
      }
    }
    if (guardCheck_pushFlowFunction_45()) {
      isDirty_pushFlowFunction_45 = pushFlowFunction_45.push();
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
        //Event Class:[com.fluxtion.runtime.output.SinkDeregister] filterString:[positionSnapshotListener]
      case ("positionSnapshotListener"):
        handle_SinkDeregister_positionSnapshotListener(typedEvent);
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
        //Event Class:[com.fluxtion.runtime.output.SinkRegistration] filterString:[positionSnapshotListener]
      case ("positionSnapshotListener"):
        handle_SinkRegistration_positionSnapshotListener(typedEvent);
        afterEvent();
        return;
    }
    afterEvent();
  }

  public void handleEvent(ClockStrategyEvent typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_clock = true;
    clock.setClockStrategy(typedEvent);
    afterEvent();
  }

  public void handleEvent(MidPrice typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_derivedRateNode = derivedRateNode.midRate(typedEvent);
    afterEvent();
  }

  public void handleEvent(MtmInstrument typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_derivedRateNode = derivedRateNode.updateMtmInstrument(typedEvent);
    afterEvent();
  }

  public void handleEvent(PositionSnapshot typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_handlerPositionSnapshot = handlerPositionSnapshot.onEvent(typedEvent);
    if (isDirty_handlerPositionSnapshot) {
      flatMapSnapshotPositions.inputUpdatedAndFlatMap(handlerPositionSnapshot);
    }
    afterEvent();
  }

  public void handleEvent(Trade typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_handlerTrade = handlerTrade.onEvent(typedEvent);
    if (isDirty_handlerTrade) {
      mergeFlowFunction_4.inputStreamUpdated(handlerTrade);
      mergeFlowFunction_15.inputStreamUpdated(handlerTrade);
    }
    if (guardCheck_mergeFlowFunction_4()) {
      isDirty_mergeFlowFunction_4 = mergeFlowFunction_4.publishMerge();
      if (isDirty_mergeFlowFunction_4) {
        filterFlowFunction_6.inputUpdated(mergeFlowFunction_4);
      }
    }
    if (guardCheck_mergeFlowFunction_15()) {
      isDirty_mergeFlowFunction_15 = mergeFlowFunction_15.publishMerge();
      if (isDirty_mergeFlowFunction_15) {
        mapRef2RefFlowFunction_17.inputUpdated(mergeFlowFunction_15);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_17()) {
      isDirty_mapRef2RefFlowFunction_17 = mapRef2RefFlowFunction_17.map();
      if (isDirty_mapRef2RefFlowFunction_17) {
        mapRef2RefFlowFunction_19.inputUpdated(mapRef2RefFlowFunction_17);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_19()) {
      isDirty_mapRef2RefFlowFunction_19 = mapRef2RefFlowFunction_19.map();
      if (isDirty_mapRef2RefFlowFunction_19) {
        mapRef2RefFlowFunction_21.inputUpdated(mapRef2RefFlowFunction_19);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_21()) {
      isDirty_mapRef2RefFlowFunction_21 = mapRef2RefFlowFunction_21.map();
      if (isDirty_mapRef2RefFlowFunction_21) {
        binaryMapToRefFlowFunction_38.input2Updated(mapRef2RefFlowFunction_21);
        binaryMapToRefFlowFunction_57.input2Updated(mapRef2RefFlowFunction_21);
      }
    }
    if (guardCheck_filterFlowFunction_6()) {
      isDirty_filterFlowFunction_6 = filterFlowFunction_6.filter();
      if (isDirty_filterFlowFunction_6) {
        mapRef2RefFlowFunction_8.inputUpdated(filterFlowFunction_6);
        mapRef2RefFlowFunction_10.inputUpdated(filterFlowFunction_6);
        mapRef2RefFlowFunction_12.inputUpdated(filterFlowFunction_6);
        mapRef2RefFlowFunction_14.inputUpdated(filterFlowFunction_6);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_8()) {
      isDirty_mapRef2RefFlowFunction_8 = mapRef2RefFlowFunction_8.map();
      if (isDirty_mapRef2RefFlowFunction_8) {
        binaryMapToRefFlowFunction_26.inputUpdated(mapRef2RefFlowFunction_8);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_26.input2Updated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_26()) {
      isDirty_binaryMapToRefFlowFunction_26 = binaryMapToRefFlowFunction_26.map();
      if (isDirty_binaryMapToRefFlowFunction_26) {
        mapRef2RefFlowFunction_28.inputUpdated(binaryMapToRefFlowFunction_26);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_47.inputUpdated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_47.input2Updated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_47()) {
      isDirty_binaryMapToRefFlowFunction_47 = binaryMapToRefFlowFunction_47.map();
      if (isDirty_binaryMapToRefFlowFunction_47) {
        mapRef2RefFlowFunction_49.inputUpdated(binaryMapToRefFlowFunction_47);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_28()) {
      isDirty_mapRef2RefFlowFunction_28 = mapRef2RefFlowFunction_28.map();
      if (isDirty_mapRef2RefFlowFunction_28) {
        binaryMapToRefFlowFunction_30.inputUpdated(mapRef2RefFlowFunction_28);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_30()) {
      isDirty_binaryMapToRefFlowFunction_30 = binaryMapToRefFlowFunction_30.map();
      if (isDirty_binaryMapToRefFlowFunction_30) {
        mapRef2RefFlowFunction_32.inputUpdated(binaryMapToRefFlowFunction_30);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_32()) {
      isDirty_mapRef2RefFlowFunction_32 = mapRef2RefFlowFunction_32.map();
      if (isDirty_mapRef2RefFlowFunction_32) {
        mapRef2RefFlowFunction_34.inputUpdated(mapRef2RefFlowFunction_32);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_34()) {
      isDirty_mapRef2RefFlowFunction_34 = mapRef2RefFlowFunction_34.map();
      if (isDirty_mapRef2RefFlowFunction_34) {
        mapRef2RefFlowFunction_36.inputUpdated(mapRef2RefFlowFunction_34);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_36()) {
      isDirty_mapRef2RefFlowFunction_36 = mapRef2RefFlowFunction_36.map();
      if (isDirty_mapRef2RefFlowFunction_36) {
        binaryMapToRefFlowFunction_38.inputUpdated(mapRef2RefFlowFunction_36);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_38()) {
      isDirty_binaryMapToRefFlowFunction_38 = binaryMapToRefFlowFunction_38.map();
      if (isDirty_binaryMapToRefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(binaryMapToRefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        mapRef2RefFlowFunction_41.inputUpdated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_41()) {
      isDirty_mapRef2RefFlowFunction_41 = mapRef2RefFlowFunction_41.map();
      if (isDirty_mapRef2RefFlowFunction_41) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_41);
      }
    }
    if (guardCheck_globalNetMtm()) {
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_43.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_49()) {
      isDirty_mapRef2RefFlowFunction_49 = mapRef2RefFlowFunction_49.map();
      if (isDirty_mapRef2RefFlowFunction_49) {
        mapRef2RefFlowFunction_51.inputUpdated(mapRef2RefFlowFunction_49);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_51()) {
      isDirty_mapRef2RefFlowFunction_51 = mapRef2RefFlowFunction_51.map();
      if (isDirty_mapRef2RefFlowFunction_51) {
        mapRef2RefFlowFunction_53.inputUpdated(mapRef2RefFlowFunction_51);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_53()) {
      isDirty_mapRef2RefFlowFunction_53 = mapRef2RefFlowFunction_53.map();
      if (isDirty_mapRef2RefFlowFunction_53) {
        binaryMapToRefFlowFunction_57.inputUpdated(mapRef2RefFlowFunction_53);
        mapRef2RefFlowFunction_54.inputUpdated(mapRef2RefFlowFunction_53);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_57()) {
      isDirty_binaryMapToRefFlowFunction_57 = binaryMapToRefFlowFunction_57.map();
      if (isDirty_binaryMapToRefFlowFunction_57) {
        mapRef2RefFlowFunction_59.inputUpdated(binaryMapToRefFlowFunction_57);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        pushFlowFunction_55.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_59()) {
      isDirty_mapRef2RefFlowFunction_59 = mapRef2RefFlowFunction_59.map();
      if (isDirty_mapRef2RefFlowFunction_59) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_59);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_61.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_43()) {
      isDirty_pushFlowFunction_43 = pushFlowFunction_43.push();
      if (isDirty_pushFlowFunction_43) {
        mapRef2RefFlowFunction_44.inputUpdated(pushFlowFunction_43);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_44()) {
      isDirty_mapRef2RefFlowFunction_44 = mapRef2RefFlowFunction_44.map();
      if (isDirty_mapRef2RefFlowFunction_44) {
        pushFlowFunction_45.inputUpdated(mapRef2RefFlowFunction_44);
      }
    }
    if (guardCheck_pushFlowFunction_45()) {
      isDirty_pushFlowFunction_45 = pushFlowFunction_45.push();
    }
    if (guardCheck_pushFlowFunction_55()) {
      isDirty_pushFlowFunction_55 = pushFlowFunction_55.push();
    }
    positionCache.tradeIn(typedEvent);
    if (guardCheck_pushFlowFunction_61()) {
      isDirty_pushFlowFunction_61 = pushFlowFunction_61.push();
    }
    afterEvent();
  }

  public void handleEvent(TradeBatch typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_handlerTradeBatch = handlerTradeBatch.onEvent(typedEvent);
    if (isDirty_handlerTradeBatch) {
      flatMapFlowFunction_3.inputUpdatedAndFlatMap(handlerTradeBatch);
    }
    afterEvent();
  }

  public void handleEvent(MidPriceBatchDto typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    eventFeedBatcher.onMidPriceBatchDto(typedEvent);
    afterEvent();
  }

  public void handleEvent(MidPriceDto typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    eventFeedBatcher.onMidPriceDto(typedEvent);
    afterEvent();
  }

  public void handleEvent(PositionSnapshotDto typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    eventFeedBatcher.onPositionSnapshot(typedEvent);
    afterEvent();
  }

  public void handleEvent(TradeBatchDto typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    eventFeedBatcher.onTradeBatchDto(typedEvent);
    afterEvent();
  }

  public void handleEvent(TradeDto typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    eventFeedBatcher.onTradeDto(typedEvent);
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
    isDirty_handlerSignal_positionSnapshotReset =
        handlerSignal_positionSnapshotReset.onEvent(typedEvent);
    if (isDirty_handlerSignal_positionSnapshotReset) {
      groupBySnapshotPositions.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_17.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_8.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_10.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_12.publishTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_12.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_14.publishTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_14.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
    }
    if (guardCheck_groupBySnapshotPositions()) {
      isDirty_groupBySnapshotPositions = groupBySnapshotPositions.map();
      if (isDirty_groupBySnapshotPositions) {
        binaryMapToRefFlowFunction_30.input2Updated(groupBySnapshotPositions);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_17()) {
      isDirty_mapRef2RefFlowFunction_17 = mapRef2RefFlowFunction_17.map();
      if (isDirty_mapRef2RefFlowFunction_17) {
        mapRef2RefFlowFunction_19.inputUpdated(mapRef2RefFlowFunction_17);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_19()) {
      isDirty_mapRef2RefFlowFunction_19 = mapRef2RefFlowFunction_19.map();
      if (isDirty_mapRef2RefFlowFunction_19) {
        mapRef2RefFlowFunction_21.inputUpdated(mapRef2RefFlowFunction_19);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_21()) {
      isDirty_mapRef2RefFlowFunction_21 = mapRef2RefFlowFunction_21.map();
      if (isDirty_mapRef2RefFlowFunction_21) {
        binaryMapToRefFlowFunction_38.input2Updated(mapRef2RefFlowFunction_21);
        binaryMapToRefFlowFunction_57.input2Updated(mapRef2RefFlowFunction_21);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_8()) {
      isDirty_mapRef2RefFlowFunction_8 = mapRef2RefFlowFunction_8.map();
      if (isDirty_mapRef2RefFlowFunction_8) {
        binaryMapToRefFlowFunction_26.inputUpdated(mapRef2RefFlowFunction_8);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_26.input2Updated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_26()) {
      isDirty_binaryMapToRefFlowFunction_26 = binaryMapToRefFlowFunction_26.map();
      if (isDirty_binaryMapToRefFlowFunction_26) {
        mapRef2RefFlowFunction_28.inputUpdated(binaryMapToRefFlowFunction_26);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_47.inputUpdated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_47.input2Updated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_47()) {
      isDirty_binaryMapToRefFlowFunction_47 = binaryMapToRefFlowFunction_47.map();
      if (isDirty_binaryMapToRefFlowFunction_47) {
        mapRef2RefFlowFunction_49.inputUpdated(binaryMapToRefFlowFunction_47);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_28()) {
      isDirty_mapRef2RefFlowFunction_28 = mapRef2RefFlowFunction_28.map();
      if (isDirty_mapRef2RefFlowFunction_28) {
        binaryMapToRefFlowFunction_30.inputUpdated(mapRef2RefFlowFunction_28);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_30()) {
      isDirty_binaryMapToRefFlowFunction_30 = binaryMapToRefFlowFunction_30.map();
      if (isDirty_binaryMapToRefFlowFunction_30) {
        mapRef2RefFlowFunction_32.inputUpdated(binaryMapToRefFlowFunction_30);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_32()) {
      isDirty_mapRef2RefFlowFunction_32 = mapRef2RefFlowFunction_32.map();
      if (isDirty_mapRef2RefFlowFunction_32) {
        mapRef2RefFlowFunction_34.inputUpdated(mapRef2RefFlowFunction_32);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_34()) {
      isDirty_mapRef2RefFlowFunction_34 = mapRef2RefFlowFunction_34.map();
      if (isDirty_mapRef2RefFlowFunction_34) {
        mapRef2RefFlowFunction_36.inputUpdated(mapRef2RefFlowFunction_34);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_36()) {
      isDirty_mapRef2RefFlowFunction_36 = mapRef2RefFlowFunction_36.map();
      if (isDirty_mapRef2RefFlowFunction_36) {
        binaryMapToRefFlowFunction_38.inputUpdated(mapRef2RefFlowFunction_36);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_38()) {
      isDirty_binaryMapToRefFlowFunction_38 = binaryMapToRefFlowFunction_38.map();
      if (isDirty_binaryMapToRefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(binaryMapToRefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        mapRef2RefFlowFunction_41.inputUpdated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_41()) {
      isDirty_mapRef2RefFlowFunction_41 = mapRef2RefFlowFunction_41.map();
      if (isDirty_mapRef2RefFlowFunction_41) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_41);
      }
    }
    if (guardCheck_globalNetMtm()) {
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_43.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_49()) {
      isDirty_mapRef2RefFlowFunction_49 = mapRef2RefFlowFunction_49.map();
      if (isDirty_mapRef2RefFlowFunction_49) {
        mapRef2RefFlowFunction_51.inputUpdated(mapRef2RefFlowFunction_49);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_51()) {
      isDirty_mapRef2RefFlowFunction_51 = mapRef2RefFlowFunction_51.map();
      if (isDirty_mapRef2RefFlowFunction_51) {
        mapRef2RefFlowFunction_53.inputUpdated(mapRef2RefFlowFunction_51);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_53()) {
      isDirty_mapRef2RefFlowFunction_53 = mapRef2RefFlowFunction_53.map();
      if (isDirty_mapRef2RefFlowFunction_53) {
        binaryMapToRefFlowFunction_57.inputUpdated(mapRef2RefFlowFunction_53);
        mapRef2RefFlowFunction_54.inputUpdated(mapRef2RefFlowFunction_53);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_57()) {
      isDirty_binaryMapToRefFlowFunction_57 = binaryMapToRefFlowFunction_57.map();
      if (isDirty_binaryMapToRefFlowFunction_57) {
        mapRef2RefFlowFunction_59.inputUpdated(binaryMapToRefFlowFunction_57);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        pushFlowFunction_55.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_59()) {
      isDirty_mapRef2RefFlowFunction_59 = mapRef2RefFlowFunction_59.map();
      if (isDirty_mapRef2RefFlowFunction_59) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_59);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_61.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_43()) {
      isDirty_pushFlowFunction_43 = pushFlowFunction_43.push();
      if (isDirty_pushFlowFunction_43) {
        mapRef2RefFlowFunction_44.inputUpdated(pushFlowFunction_43);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_44()) {
      isDirty_mapRef2RefFlowFunction_44 = mapRef2RefFlowFunction_44.map();
      if (isDirty_mapRef2RefFlowFunction_44) {
        pushFlowFunction_45.inputUpdated(mapRef2RefFlowFunction_44);
      }
    }
    if (guardCheck_pushFlowFunction_45()) {
      isDirty_pushFlowFunction_45 = pushFlowFunction_45.push();
    }
    if (guardCheck_pushFlowFunction_55()) {
      isDirty_pushFlowFunction_55 = pushFlowFunction_55.push();
    }
    if (guardCheck_pushFlowFunction_61()) {
      isDirty_pushFlowFunction_61 = pushFlowFunction_61.push();
    }
  }

  private void handle_Signal_positionUpdate(Signal typedEvent) {
    isDirty_handlerSignal_positionUpdate = handlerSignal_positionUpdate.onEvent(typedEvent);
    if (isDirty_handlerSignal_positionUpdate) {
      groupBySnapshotPositions.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_21.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_21.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_34.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_36.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_40.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_51.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_53.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_59.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
    }
    if (guardCheck_groupBySnapshotPositions()) {
      isDirty_groupBySnapshotPositions = groupBySnapshotPositions.map();
      if (isDirty_groupBySnapshotPositions) {
        binaryMapToRefFlowFunction_30.input2Updated(groupBySnapshotPositions);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_21()) {
      isDirty_mapRef2RefFlowFunction_21 = mapRef2RefFlowFunction_21.map();
      if (isDirty_mapRef2RefFlowFunction_21) {
        binaryMapToRefFlowFunction_38.input2Updated(mapRef2RefFlowFunction_21);
        binaryMapToRefFlowFunction_57.input2Updated(mapRef2RefFlowFunction_21);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_30()) {
      isDirty_binaryMapToRefFlowFunction_30 = binaryMapToRefFlowFunction_30.map();
      if (isDirty_binaryMapToRefFlowFunction_30) {
        mapRef2RefFlowFunction_32.inputUpdated(binaryMapToRefFlowFunction_30);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_32()) {
      isDirty_mapRef2RefFlowFunction_32 = mapRef2RefFlowFunction_32.map();
      if (isDirty_mapRef2RefFlowFunction_32) {
        mapRef2RefFlowFunction_34.inputUpdated(mapRef2RefFlowFunction_32);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_34()) {
      isDirty_mapRef2RefFlowFunction_34 = mapRef2RefFlowFunction_34.map();
      if (isDirty_mapRef2RefFlowFunction_34) {
        mapRef2RefFlowFunction_36.inputUpdated(mapRef2RefFlowFunction_34);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_36()) {
      isDirty_mapRef2RefFlowFunction_36 = mapRef2RefFlowFunction_36.map();
      if (isDirty_mapRef2RefFlowFunction_36) {
        binaryMapToRefFlowFunction_38.inputUpdated(mapRef2RefFlowFunction_36);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_38()) {
      isDirty_binaryMapToRefFlowFunction_38 = binaryMapToRefFlowFunction_38.map();
      if (isDirty_binaryMapToRefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(binaryMapToRefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        mapRef2RefFlowFunction_41.inputUpdated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_41()) {
      isDirty_mapRef2RefFlowFunction_41 = mapRef2RefFlowFunction_41.map();
      if (isDirty_mapRef2RefFlowFunction_41) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_41);
      }
    }
    if (guardCheck_globalNetMtm()) {
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_43.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_51()) {
      isDirty_mapRef2RefFlowFunction_51 = mapRef2RefFlowFunction_51.map();
      if (isDirty_mapRef2RefFlowFunction_51) {
        mapRef2RefFlowFunction_53.inputUpdated(mapRef2RefFlowFunction_51);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_53()) {
      isDirty_mapRef2RefFlowFunction_53 = mapRef2RefFlowFunction_53.map();
      if (isDirty_mapRef2RefFlowFunction_53) {
        binaryMapToRefFlowFunction_57.inputUpdated(mapRef2RefFlowFunction_53);
        mapRef2RefFlowFunction_54.inputUpdated(mapRef2RefFlowFunction_53);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_57()) {
      isDirty_binaryMapToRefFlowFunction_57 = binaryMapToRefFlowFunction_57.map();
      if (isDirty_binaryMapToRefFlowFunction_57) {
        mapRef2RefFlowFunction_59.inputUpdated(binaryMapToRefFlowFunction_57);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        pushFlowFunction_55.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_59()) {
      isDirty_mapRef2RefFlowFunction_59 = mapRef2RefFlowFunction_59.map();
      if (isDirty_mapRef2RefFlowFunction_59) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_59);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_61.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_43()) {
      isDirty_pushFlowFunction_43 = pushFlowFunction_43.push();
      if (isDirty_pushFlowFunction_43) {
        mapRef2RefFlowFunction_44.inputUpdated(pushFlowFunction_43);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_44()) {
      isDirty_mapRef2RefFlowFunction_44 = mapRef2RefFlowFunction_44.map();
      if (isDirty_mapRef2RefFlowFunction_44) {
        pushFlowFunction_45.inputUpdated(mapRef2RefFlowFunction_44);
      }
    }
    if (guardCheck_pushFlowFunction_45()) {
      isDirty_pushFlowFunction_45 = pushFlowFunction_45.push();
    }
    if (guardCheck_pushFlowFunction_55()) {
      isDirty_pushFlowFunction_55 = pushFlowFunction_55.push();
    }
    if (guardCheck_pushFlowFunction_61()) {
      isDirty_pushFlowFunction_61 = pushFlowFunction_61.push();
    }
  }

  private void handle_SinkDeregister_globalNetMtmListener(SinkDeregister typedEvent) {
    globalNetMtmListener.unregisterSink(typedEvent);
  }

  private void handle_SinkDeregister_instrumentNetMtmListener(SinkDeregister typedEvent) {
    instrumentNetMtmListener.unregisterSink(typedEvent);
  }

  private void handle_SinkDeregister_positionSnapshotListener(SinkDeregister typedEvent) {
    positionSnapshotListener.unregisterSink(typedEvent);
  }

  private void handle_SinkRegistration_globalNetMtmListener(SinkRegistration typedEvent) {
    globalNetMtmListener.sinkRegistration(typedEvent);
  }

  private void handle_SinkRegistration_instrumentNetMtmListener(SinkRegistration typedEvent) {
    instrumentNetMtmListener.sinkRegistration(typedEvent);
  }

  private void handle_SinkRegistration_positionSnapshotListener(SinkRegistration typedEvent) {
    positionSnapshotListener.sinkRegistration(typedEvent);
  }

  private void handle_NamedFeedEvent_symbolFeed(NamedFeedEvent typedEvent) {
    isDirty_namedFeedTableNode_62 = namedFeedTableNode_62.tableUpdate(typedEvent);
  }
  //FILTERED DISPATCH - END

  //EXPORTED SERVICE FUNCTIONS - START
  @Override
  public boolean configChanged(com.fluxtion.server.config.ConfigUpdate arg0) {
    beforeServiceCall(
        "public default boolean com.fluxtion.server.config.ConfigListener.configChanged(com.fluxtion.server.config.ConfigUpdate)");
    ExportFunctionAuditEvent typedEvent = functionAudit;
    eventFeedBatcher.configChanged(arg0);
    afterServiceCall();
    return true;
  }

  @Override
  public boolean initialConfig(com.fluxtion.server.config.ConfigMap arg0) {
    beforeServiceCall(
        "public boolean com.fluxtion.server.lib.pnl.calculator.EventFeedConnector.initialConfig(com.fluxtion.server.config.ConfigMap)");
    ExportFunctionAuditEvent typedEvent = functionAudit;
    eventFeedBatcher.initialConfig(arg0);
    afterServiceCall();
    return true;
  }

  @Override
  public void deRegisterService(com.fluxtion.runtime.service.Service<?> arg0) {
    beforeServiceCall(
        "public void com.fluxtion.runtime.service.ServiceRegistryNode.deRegisterService(com.fluxtion.runtime.service.Service<?>)");
    ExportFunctionAuditEvent typedEvent = functionAudit;
    serviceRegistry.deRegisterService(arg0);
    afterServiceCall();
  }

  @Override
  public void registerService(com.fluxtion.runtime.service.Service<?> arg0) {
    beforeServiceCall(
        "public void com.fluxtion.runtime.service.ServiceRegistryNode.registerService(com.fluxtion.runtime.service.Service<?>)");
    ExportFunctionAuditEvent typedEvent = functionAudit;
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

  private void initialiseAuditor(Auditor auditor) {
    auditor.init();
    auditor.nodeRegistered(callBackNode_63, "callBackNode_63");
    auditor.nodeRegistered(callBackNode_82, "callBackNode_82");
    auditor.nodeRegistered(callbackDispatcher, "callbackDispatcher");
    auditor.nodeRegistered(callBackTriggerEvent_1, "callBackTriggerEvent_1");
    auditor.nodeRegistered(callBackTriggerEvent_0, "callBackTriggerEvent_0");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_26, "binaryMapToRefFlowFunction_26");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_30, "binaryMapToRefFlowFunction_30");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_38, "binaryMapToRefFlowFunction_38");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_47, "binaryMapToRefFlowFunction_47");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_57, "binaryMapToRefFlowFunction_57");
    auditor.nodeRegistered(filterFlowFunction_6, "filterFlowFunction_6");
    auditor.nodeRegistered(flatMapFlowFunction_3, "flatMapFlowFunction_3");
    auditor.nodeRegistered(flatMapSnapshotPositions, "flatMapSnapshotPositions");
    auditor.nodeRegistered(globalNetMtm, "globalNetMtm");
    auditor.nodeRegistered(groupBySnapshotPositions, "groupBySnapshotPositions");
    auditor.nodeRegistered(instrumentNetMtm, "instrumentNetMtm");
    auditor.nodeRegistered(mapRef2RefFlowFunction_8, "mapRef2RefFlowFunction_8");
    auditor.nodeRegistered(mapRef2RefFlowFunction_10, "mapRef2RefFlowFunction_10");
    auditor.nodeRegistered(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12");
    auditor.nodeRegistered(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14");
    auditor.nodeRegistered(mapRef2RefFlowFunction_17, "mapRef2RefFlowFunction_17");
    auditor.nodeRegistered(mapRef2RefFlowFunction_19, "mapRef2RefFlowFunction_19");
    auditor.nodeRegistered(mapRef2RefFlowFunction_21, "mapRef2RefFlowFunction_21");
    auditor.nodeRegistered(mapRef2RefFlowFunction_28, "mapRef2RefFlowFunction_28");
    auditor.nodeRegistered(mapRef2RefFlowFunction_32, "mapRef2RefFlowFunction_32");
    auditor.nodeRegistered(mapRef2RefFlowFunction_34, "mapRef2RefFlowFunction_34");
    auditor.nodeRegistered(mapRef2RefFlowFunction_36, "mapRef2RefFlowFunction_36");
    auditor.nodeRegistered(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40");
    auditor.nodeRegistered(mapRef2RefFlowFunction_41, "mapRef2RefFlowFunction_41");
    auditor.nodeRegistered(mapRef2RefFlowFunction_44, "mapRef2RefFlowFunction_44");
    auditor.nodeRegistered(mapRef2RefFlowFunction_49, "mapRef2RefFlowFunction_49");
    auditor.nodeRegistered(mapRef2RefFlowFunction_51, "mapRef2RefFlowFunction_51");
    auditor.nodeRegistered(mapRef2RefFlowFunction_53, "mapRef2RefFlowFunction_53");
    auditor.nodeRegistered(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54");
    auditor.nodeRegistered(mapRef2RefFlowFunction_59, "mapRef2RefFlowFunction_59");
    auditor.nodeRegistered(mergeFlowFunction_4, "mergeFlowFunction_4");
    auditor.nodeRegistered(mergeFlowFunction_15, "mergeFlowFunction_15");
    auditor.nodeRegistered(pushFlowFunction_43, "pushFlowFunction_43");
    auditor.nodeRegistered(pushFlowFunction_45, "pushFlowFunction_45");
    auditor.nodeRegistered(pushFlowFunction_55, "pushFlowFunction_55");
    auditor.nodeRegistered(pushFlowFunction_61, "pushFlowFunction_61");
    auditor.nodeRegistered(emptyGroupBy_74, "emptyGroupBy_74");
    auditor.nodeRegistered(emptyGroupBy_132, "emptyGroupBy_132");
    auditor.nodeRegistered(emptyGroupBy_316, "emptyGroupBy_316");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_7, "groupByFlowFunctionWrapper_7");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_9, "groupByFlowFunctionWrapper_9");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_11, "groupByFlowFunctionWrapper_11");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_13, "groupByFlowFunctionWrapper_13");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_16, "groupByFlowFunctionWrapper_16");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_23, "groupByFlowFunctionWrapper_23");
    auditor.nodeRegistered(groupByMapFlowFunction_20, "groupByMapFlowFunction_20");
    auditor.nodeRegistered(groupByMapFlowFunction_27, "groupByMapFlowFunction_27");
    auditor.nodeRegistered(groupByMapFlowFunction_31, "groupByMapFlowFunction_31");
    auditor.nodeRegistered(groupByMapFlowFunction_33, "groupByMapFlowFunction_33");
    auditor.nodeRegistered(groupByMapFlowFunction_39, "groupByMapFlowFunction_39");
    auditor.nodeRegistered(groupByMapFlowFunction_48, "groupByMapFlowFunction_48");
    auditor.nodeRegistered(groupByMapFlowFunction_50, "groupByMapFlowFunction_50");
    auditor.nodeRegistered(groupByMapFlowFunction_58, "groupByMapFlowFunction_58");
    auditor.nodeRegistered(leftJoin_37, "leftJoin_37");
    auditor.nodeRegistered(leftJoin_56, "leftJoin_56");
    auditor.nodeRegistered(outerJoin_25, "outerJoin_25");
    auditor.nodeRegistered(outerJoin_29, "outerJoin_29");
    auditor.nodeRegistered(outerJoin_46, "outerJoin_46");
    auditor.nodeRegistered(defaultValue_18, "defaultValue_18");
    auditor.nodeRegistered(defaultValue_35, "defaultValue_35");
    auditor.nodeRegistered(defaultValue_52, "defaultValue_52");
    auditor.nodeRegistered(mapTuple_273, "mapTuple_273");
    auditor.nodeRegistered(mapTuple_283, "mapTuple_283");
    auditor.nodeRegistered(mapTuple_287, "mapTuple_287");
    auditor.nodeRegistered(mapTuple_399, "mapTuple_399");
    auditor.nodeRegistered(mapTuple_409, "mapTuple_409");
    auditor.nodeRegistered(subscriptionManager, "subscriptionManager");
    auditor.nodeRegistered(handlerPositionSnapshot, "handlerPositionSnapshot");
    auditor.nodeRegistered(
        handlerSignal_positionSnapshotReset, "handlerSignal_positionSnapshotReset");
    auditor.nodeRegistered(handlerSignal_positionUpdate, "handlerSignal_positionUpdate");
    auditor.nodeRegistered(handlerTrade, "handlerTrade");
    auditor.nodeRegistered(handlerTradeBatch, "handlerTradeBatch");
    auditor.nodeRegistered(context, "context");
    auditor.nodeRegistered(namedFeedTableNode_62, "namedFeedTableNode_62");
    auditor.nodeRegistered(globalNetMtmListener, "globalNetMtmListener");
    auditor.nodeRegistered(instrumentNetMtmListener, "instrumentNetMtmListener");
    auditor.nodeRegistered(positionSnapshotListener, "positionSnapshotListener");
    auditor.nodeRegistered(derivedRateNode, "derivedRateNode");
    auditor.nodeRegistered(eventFeedBatcher, "eventFeedBatcher");
    auditor.nodeRegistered(positionCache, "positionCache");
    auditor.nodeRegistered(tradeSequenceFilter_5, "tradeSequenceFilter_5");
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
    isDirty_binaryMapToRefFlowFunction_26 = false;
    isDirty_binaryMapToRefFlowFunction_30 = false;
    isDirty_binaryMapToRefFlowFunction_38 = false;
    isDirty_binaryMapToRefFlowFunction_47 = false;
    isDirty_binaryMapToRefFlowFunction_57 = false;
    isDirty_callBackNode_63 = false;
    isDirty_callBackNode_82 = false;
    isDirty_clock = false;
    isDirty_derivedRateNode = false;
    isDirty_filterFlowFunction_6 = false;
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
    isDirty_mapRef2RefFlowFunction_8 = false;
    isDirty_mapRef2RefFlowFunction_10 = false;
    isDirty_mapRef2RefFlowFunction_12 = false;
    isDirty_mapRef2RefFlowFunction_14 = false;
    isDirty_mapRef2RefFlowFunction_17 = false;
    isDirty_mapRef2RefFlowFunction_19 = false;
    isDirty_mapRef2RefFlowFunction_21 = false;
    isDirty_mapRef2RefFlowFunction_28 = false;
    isDirty_mapRef2RefFlowFunction_32 = false;
    isDirty_mapRef2RefFlowFunction_34 = false;
    isDirty_mapRef2RefFlowFunction_36 = false;
    isDirty_mapRef2RefFlowFunction_40 = false;
    isDirty_mapRef2RefFlowFunction_41 = false;
    isDirty_mapRef2RefFlowFunction_44 = false;
    isDirty_mapRef2RefFlowFunction_49 = false;
    isDirty_mapRef2RefFlowFunction_51 = false;
    isDirty_mapRef2RefFlowFunction_53 = false;
    isDirty_mapRef2RefFlowFunction_54 = false;
    isDirty_mapRef2RefFlowFunction_59 = false;
    isDirty_mergeFlowFunction_4 = false;
    isDirty_mergeFlowFunction_15 = false;
    isDirty_namedFeedTableNode_62 = false;
    isDirty_pushFlowFunction_43 = false;
    isDirty_pushFlowFunction_45 = false;
    isDirty_pushFlowFunction_55 = false;
    isDirty_pushFlowFunction_61 = false;
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
          binaryMapToRefFlowFunction_26, () -> isDirty_binaryMapToRefFlowFunction_26);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_30, () -> isDirty_binaryMapToRefFlowFunction_30);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_38, () -> isDirty_binaryMapToRefFlowFunction_38);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_47, () -> isDirty_binaryMapToRefFlowFunction_47);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_57, () -> isDirty_binaryMapToRefFlowFunction_57);
      dirtyFlagSupplierMap.put(callBackNode_63, () -> isDirty_callBackNode_63);
      dirtyFlagSupplierMap.put(callBackNode_82, () -> isDirty_callBackNode_82);
      dirtyFlagSupplierMap.put(clock, () -> isDirty_clock);
      dirtyFlagSupplierMap.put(derivedRateNode, () -> isDirty_derivedRateNode);
      dirtyFlagSupplierMap.put(filterFlowFunction_6, () -> isDirty_filterFlowFunction_6);
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
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_17, () -> isDirty_mapRef2RefFlowFunction_17);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_19, () -> isDirty_mapRef2RefFlowFunction_19);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_21, () -> isDirty_mapRef2RefFlowFunction_21);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_28, () -> isDirty_mapRef2RefFlowFunction_28);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_32, () -> isDirty_mapRef2RefFlowFunction_32);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_34, () -> isDirty_mapRef2RefFlowFunction_34);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_36, () -> isDirty_mapRef2RefFlowFunction_36);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_40, () -> isDirty_mapRef2RefFlowFunction_40);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_41, () -> isDirty_mapRef2RefFlowFunction_41);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_44, () -> isDirty_mapRef2RefFlowFunction_44);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_49, () -> isDirty_mapRef2RefFlowFunction_49);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_51, () -> isDirty_mapRef2RefFlowFunction_51);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_53, () -> isDirty_mapRef2RefFlowFunction_53);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_54, () -> isDirty_mapRef2RefFlowFunction_54);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_59, () -> isDirty_mapRef2RefFlowFunction_59);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_8, () -> isDirty_mapRef2RefFlowFunction_8);
      dirtyFlagSupplierMap.put(mergeFlowFunction_15, () -> isDirty_mergeFlowFunction_15);
      dirtyFlagSupplierMap.put(mergeFlowFunction_4, () -> isDirty_mergeFlowFunction_4);
      dirtyFlagSupplierMap.put(namedFeedTableNode_62, () -> isDirty_namedFeedTableNode_62);
      dirtyFlagSupplierMap.put(pushFlowFunction_43, () -> isDirty_pushFlowFunction_43);
      dirtyFlagSupplierMap.put(pushFlowFunction_45, () -> isDirty_pushFlowFunction_45);
      dirtyFlagSupplierMap.put(pushFlowFunction_55, () -> isDirty_pushFlowFunction_55);
      dirtyFlagSupplierMap.put(pushFlowFunction_61, () -> isDirty_pushFlowFunction_61);
    }
    return dirtyFlagSupplierMap.getOrDefault(node, StaticEventProcessor.ALWAYS_FALSE);
  }

  @Override
  public void setDirty(Object node, boolean dirtyFlag) {
    if (dirtyFlagUpdateMap.isEmpty()) {
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_26, (b) -> isDirty_binaryMapToRefFlowFunction_26 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_30, (b) -> isDirty_binaryMapToRefFlowFunction_30 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_38, (b) -> isDirty_binaryMapToRefFlowFunction_38 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_47, (b) -> isDirty_binaryMapToRefFlowFunction_47 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_57, (b) -> isDirty_binaryMapToRefFlowFunction_57 = b);
      dirtyFlagUpdateMap.put(callBackNode_63, (b) -> isDirty_callBackNode_63 = b);
      dirtyFlagUpdateMap.put(callBackNode_82, (b) -> isDirty_callBackNode_82 = b);
      dirtyFlagUpdateMap.put(clock, (b) -> isDirty_clock = b);
      dirtyFlagUpdateMap.put(derivedRateNode, (b) -> isDirty_derivedRateNode = b);
      dirtyFlagUpdateMap.put(filterFlowFunction_6, (b) -> isDirty_filterFlowFunction_6 = b);
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
          mapRef2RefFlowFunction_17, (b) -> isDirty_mapRef2RefFlowFunction_17 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_19, (b) -> isDirty_mapRef2RefFlowFunction_19 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_21, (b) -> isDirty_mapRef2RefFlowFunction_21 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_28, (b) -> isDirty_mapRef2RefFlowFunction_28 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_32, (b) -> isDirty_mapRef2RefFlowFunction_32 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_34, (b) -> isDirty_mapRef2RefFlowFunction_34 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_36, (b) -> isDirty_mapRef2RefFlowFunction_36 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_40, (b) -> isDirty_mapRef2RefFlowFunction_40 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_41, (b) -> isDirty_mapRef2RefFlowFunction_41 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_44, (b) -> isDirty_mapRef2RefFlowFunction_44 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_49, (b) -> isDirty_mapRef2RefFlowFunction_49 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_51, (b) -> isDirty_mapRef2RefFlowFunction_51 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_53, (b) -> isDirty_mapRef2RefFlowFunction_53 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_54, (b) -> isDirty_mapRef2RefFlowFunction_54 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_59, (b) -> isDirty_mapRef2RefFlowFunction_59 = b);
      dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_8, (b) -> isDirty_mapRef2RefFlowFunction_8 = b);
      dirtyFlagUpdateMap.put(mergeFlowFunction_15, (b) -> isDirty_mergeFlowFunction_15 = b);
      dirtyFlagUpdateMap.put(mergeFlowFunction_4, (b) -> isDirty_mergeFlowFunction_4 = b);
      dirtyFlagUpdateMap.put(namedFeedTableNode_62, (b) -> isDirty_namedFeedTableNode_62 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_43, (b) -> isDirty_pushFlowFunction_43 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_45, (b) -> isDirty_pushFlowFunction_45 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_55, (b) -> isDirty_pushFlowFunction_55 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_61, (b) -> isDirty_pushFlowFunction_61 = b);
    }
    dirtyFlagUpdateMap.get(node).accept(dirtyFlag);
  }

  private boolean guardCheck_eventLogger() {
    return isDirty_clock;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_26() {
    return isDirty_mapRef2RefFlowFunction_8 | isDirty_mapRef2RefFlowFunction_10;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_30() {
    return isDirty_groupBySnapshotPositions | isDirty_mapRef2RefFlowFunction_28;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_38() {
    return isDirty_mapRef2RefFlowFunction_21 | isDirty_mapRef2RefFlowFunction_36;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_47() {
    return isDirty_mapRef2RefFlowFunction_12 | isDirty_mapRef2RefFlowFunction_14;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_57() {
    return isDirty_mapRef2RefFlowFunction_21 | isDirty_mapRef2RefFlowFunction_53;
  }

  private boolean guardCheck_filterFlowFunction_6() {
    return isDirty_mergeFlowFunction_4;
  }

  private boolean guardCheck_flatMapFlowFunction_3() {
    return isDirty_callBackNode_63;
  }

  private boolean guardCheck_flatMapSnapshotPositions() {
    return isDirty_callBackNode_82;
  }

  private boolean guardCheck_globalNetMtm() {
    return isDirty_mapRef2RefFlowFunction_41;
  }

  private boolean guardCheck_groupBySnapshotPositions() {
    return isDirty_flatMapSnapshotPositions
        | isDirty_handlerSignal_positionSnapshotReset
        | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_instrumentNetMtm() {
    return isDirty_mapRef2RefFlowFunction_59;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_8() {
    return isDirty_filterFlowFunction_6 | isDirty_handlerSignal_positionSnapshotReset;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_10() {
    return isDirty_filterFlowFunction_6 | isDirty_handlerSignal_positionSnapshotReset;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_12() {
    return isDirty_filterFlowFunction_6 | isDirty_handlerSignal_positionSnapshotReset;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_14() {
    return isDirty_filterFlowFunction_6 | isDirty_handlerSignal_positionSnapshotReset;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_17() {
    return isDirty_handlerSignal_positionSnapshotReset | isDirty_mergeFlowFunction_15;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_19() {
    return isDirty_mapRef2RefFlowFunction_17;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_21() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_19;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_28() {
    return isDirty_binaryMapToRefFlowFunction_26;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_32() {
    return isDirty_binaryMapToRefFlowFunction_30;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_34() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_32;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_36() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_34;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_40() {
    return isDirty_binaryMapToRefFlowFunction_38 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_41() {
    return isDirty_mapRef2RefFlowFunction_40;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_44() {
    return isDirty_pushFlowFunction_43;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_49() {
    return isDirty_binaryMapToRefFlowFunction_47;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_51() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_49;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_53() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_51;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_54() {
    return isDirty_mapRef2RefFlowFunction_53;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_59() {
    return isDirty_binaryMapToRefFlowFunction_57 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mergeFlowFunction_4() {
    return isDirty_flatMapFlowFunction_3 | isDirty_handlerTrade;
  }

  private boolean guardCheck_mergeFlowFunction_15() {
    return isDirty_flatMapFlowFunction_3 | isDirty_handlerTrade;
  }

  private boolean guardCheck_pushFlowFunction_43() {
    return isDirty_globalNetMtm;
  }

  private boolean guardCheck_pushFlowFunction_45() {
    return isDirty_mapRef2RefFlowFunction_44;
  }

  private boolean guardCheck_pushFlowFunction_55() {
    return isDirty_mapRef2RefFlowFunction_54;
  }

  private boolean guardCheck_pushFlowFunction_61() {
    return isDirty_instrumentNetMtm;
  }

  private boolean guardCheck_groupByMapFlowFunction_20() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_groupByMapFlowFunction_33() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_groupByMapFlowFunction_50() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_context() {
    return isDirty_clock;
  }

  private boolean guardCheck_globalNetMtmListener() {
    return isDirty_pushFlowFunction_43;
  }

  private boolean guardCheck_instrumentNetMtmListener() {
    return isDirty_pushFlowFunction_61;
  }

  private boolean guardCheck_positionSnapshotListener() {
    return isDirty_pushFlowFunction_45;
  }

  private boolean guardCheck_eventFeedBatcher() {
    return isDirty_namedFeedTableNode_62;
  }

  private boolean guardCheck_positionCache() {
    return isDirty_pushFlowFunction_55;
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
