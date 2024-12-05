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
import com.fluxtion.runtime.audit.EventLogManager;
import com.fluxtion.runtime.audit.NodeNameAuditor;
import com.fluxtion.runtime.callback.CallBackNode;
import com.fluxtion.runtime.callback.CallbackDispatcherImpl;
import com.fluxtion.runtime.callback.ExportFunctionAuditEvent;
import com.fluxtion.runtime.callback.InstanceCallbackEvent.InstanceCallbackEvent_0;
import com.fluxtion.runtime.callback.InstanceCallbackEvent.InstanceCallbackEvent_1;
import com.fluxtion.runtime.dataflow.aggregate.function.AggregateIdentityFlowFunction;
import com.fluxtion.runtime.dataflow.function.BinaryMapFlowFunction.BinaryMapToRefFlowFunction;
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
import com.fluxtion.runtime.event.Signal;
import com.fluxtion.runtime.input.EventFeed;
import com.fluxtion.runtime.input.SubscriptionManager;
import com.fluxtion.runtime.input.SubscriptionManagerNode;
import com.fluxtion.runtime.node.DefaultEventHandlerNode;
import com.fluxtion.runtime.node.ForkedTriggerTask;
import com.fluxtion.runtime.node.MutableEventProcessorContext;
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
import com.fluxtion.server.lib.pnl.dto.SymbolBatchDto;
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
 * eventProcessorGenerator version : 9.4.3-SNAPSHOT
 * api version                     : 9.4.3-SNAPSHOT
 * </pre>
 *
 * Event classes supported:
 *
 * <ul>
 *   <li>com.fluxtion.compiler.generation.model.ExportFunctionMarker
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
 *   <li>com.fluxtion.server.lib.pnl.dto.SymbolBatchDto
 *   <li>com.fluxtion.server.lib.pnl.dto.SymbolDto
 *   <li>com.fluxtion.server.lib.pnl.dto.TradeBatchDto
 *   <li>com.fluxtion.server.lib.pnl.dto.TradeDto
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
  private final CallBackNode callBackNode_69 = new CallBackNode<>(callBackTriggerEvent_0);
  private final InstanceCallbackEvent_1 callBackTriggerEvent_1 = new InstanceCallbackEvent_1();
  private final CallBackNode callBackNode_84 = new CallBackNode<>(callBackTriggerEvent_1);
  private final CallbackDispatcherImpl callbackDispatcher = new CallbackDispatcherImpl();
  public final Clock clock = new Clock();
  public final DerivedRateNode derivedRateNode = new DerivedRateNode();
  private final EmptyGroupBy emptyGroupBy_75 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_19 = new DefaultValue<>(emptyGroupBy_75);
  private final EmptyGroupBy emptyGroupBy_136 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_42 = new DefaultValue<>(emptyGroupBy_136);
  private final EmptyGroupBy emptyGroupBy_298 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_59 = new DefaultValue<>(emptyGroupBy_298);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_8 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, SingleInstrumentPosMtmAggregate::dealt);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_10 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getContraInstrument, Mappers::identity, SingleInstrumentPosMtmAggregate::contra);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_12 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, InstrumentPosMtmAggregate::dealt);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_14 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getContraInstrument, Mappers::identity, InstrumentPosMtmAggregate::contra);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_17 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, FeeInstrumentPosMtmAggregate::new);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_26 =
      new GroupByFlowFunctionWrapper<>(
          InstrumentPosition::instrument, Mappers::identity, AggregateIdentityFlowFunction::new);
  private final GroupByMapFlowFunction groupByMapFlowFunction_21 =
      new GroupByMapFlowFunction(derivedRateNode::calculateFeeMtm);
  private final GroupByMapFlowFunction groupByMapFlowFunction_40 =
      new GroupByMapFlowFunction(derivedRateNode::calculateInstrumentPosMtm);
  private final GroupByMapFlowFunction groupByMapFlowFunction_57 =
      new GroupByMapFlowFunction(derivedRateNode::calculateInstrumentPosMtm);
  private final LeftJoin leftJoin_44 = new LeftJoin();
  private final LeftJoin leftJoin_61 = new LeftJoin();
  private final MapTuple mapTuple_263 = new MapTuple<>(NetMarkToMarket::combine);
  private final GroupByMapFlowFunction groupByMapFlowFunction_46 =
      new GroupByMapFlowFunction(mapTuple_263::mapTuple);
  private final MapTuple mapTuple_273 =
      new MapTuple<>(InstrumentPosMtm::overwriteInstrumentPositionWithSnapshot);
  private final GroupByMapFlowFunction groupByMapFlowFunction_36 =
      new GroupByMapFlowFunction(mapTuple_273::mapTuple);
  private final MapTuple mapTuple_277 = new MapTuple<>(InstrumentPosMtm::merge);
  private final GroupByMapFlowFunction groupByMapFlowFunction_32 =
      new GroupByMapFlowFunction(mapTuple_277::mapTuple);
  private final MapTuple mapTuple_352 = new MapTuple<>(NetMarkToMarket::combine);
  private final GroupByMapFlowFunction groupByMapFlowFunction_63 =
      new GroupByMapFlowFunction(mapTuple_352::mapTuple);
  private final MapTuple mapTuple_362 = new MapTuple<>(InstrumentPosMtm::merge);
  private final GroupByMapFlowFunction groupByMapFlowFunction_55 =
      new GroupByMapFlowFunction(mapTuple_362::mapTuple);
  public final NodeNameAuditor nodeNameLookup = new NodeNameAuditor();
  private final OuterJoin outerJoin_30 = new OuterJoin();
  private final OuterJoin outerJoin_34 = new OuterJoin();
  private final OuterJoin outerJoin_53 = new OuterJoin();
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
          flatMapSnapshotPositions, groupByFlowFunctionWrapper_26::aggregate);
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
  private final FlatMapFlowFunction flatMapFlowFunction_6 =
      new FlatMapFlowFunction<>(handlerTradeBatch, TradeBatch::getTrades);
  private final SinkPublisher instrumentNetMtmListener =
      new SinkPublisher<>("instrumentNetMtmListener");
  private final MergeFlowFunction mergeFlowFunction_7 =
      new MergeFlowFunction<>(Arrays.asList(handlerTrade, flatMapFlowFunction_6));
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_9 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_7, groupByFlowFunctionWrapper_8::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_11 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_7, groupByFlowFunctionWrapper_10::aggregate);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_31 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_9, mapRef2RefFlowFunction_11, outerJoin_30::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_13 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_7, groupByFlowFunctionWrapper_12::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_15 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_7, groupByFlowFunctionWrapper_14::aggregate);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_54 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_13, mapRef2RefFlowFunction_15, outerJoin_53::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_33 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_31, groupByMapFlowFunction_32::mapValues);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_35 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_33, groupBySnapshotPositions, outerJoin_34::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_37 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_35, groupByMapFlowFunction_36::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_41 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_37, groupByMapFlowFunction_40::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_43 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_41, defaultValue_42::getOrDefault);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_56 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_54, groupByMapFlowFunction_55::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_58 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_56, groupByMapFlowFunction_57::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_60 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_58, defaultValue_59::getOrDefault);
  private final MergeFlowFunction mergeFlowFunction_16 =
      new MergeFlowFunction<>(Arrays.asList(handlerTrade, flatMapFlowFunction_6));
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_18 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_16, groupByFlowFunctionWrapper_17::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_20 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_18, defaultValue_19::getOrDefault);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_22 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_20, groupByMapFlowFunction_21::mapValues);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_45 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_43, mapRef2RefFlowFunction_22, leftJoin_44::join);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_62 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_60, mapRef2RefFlowFunction_22, leftJoin_61::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_47 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_45, groupByMapFlowFunction_46::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_48 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_47, GroupBy<Object, Object>::toMap);
  public final MapRef2RefFlowFunction globalNetMtm =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_48, NetMarkToMarket::markToMarketSum);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_64 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_62, groupByMapFlowFunction_63::mapValues);
  public final MapRef2RefFlowFunction instrumentNetMtm =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_64, GroupBy<Object, Object>::toMap);
  private final SinkPublisher positionSnapshotListener =
      new SinkPublisher<>("positionSnapshotListener");
  private final PushFlowFunction pushFlowFunction_50 =
      new PushFlowFunction<>(globalNetMtm, globalNetMtmListener::publish);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_51 =
      new MapRef2RefFlowFunction<>(pushFlowFunction_50, DtoHelper::formatPosition);
  private final PushFlowFunction pushFlowFunction_52 =
      new PushFlowFunction<>(mapRef2RefFlowFunction_51, positionSnapshotListener::publish);
  private final PushFlowFunction pushFlowFunction_66 =
      new PushFlowFunction<>(instrumentNetMtm, instrumentNetMtmListener::publish);
  public final ServiceRegistryNode serviceRegistry = new ServiceRegistryNode();
  private final TemplateMessage templateMessage_2 = new TemplateMessage<>("--- signal EOB ------");
  private final PeekFlowFunction peekFlowFunction_3 =
      new PeekFlowFunction<>(
          handlerSignal_positionUpdate, templateMessage_2::templateAndLogToConsole);
  private final TemplateMessage templateMessage_4 =
      new TemplateMessage<>("--- signal POS RESET ------");
  private final PeekFlowFunction peekFlowFunction_5 =
      new PeekFlowFunction<>(
          handlerSignal_positionSnapshotReset, templateMessage_4::templateAndLogToConsole);
  private final TemplateMessage templateMessage_23 = new TemplateMessage<>("snapshot in:{}");
  private final PeekFlowFunction peekFlowFunction_24 =
      new PeekFlowFunction<>(handlerPositionSnapshot, templateMessage_23::templateAndLogToConsole);
  private final TemplateMessage templateMessage_28 = new TemplateMessage<>("new snapshot:{}");
  private final PeekFlowFunction peekFlowFunction_29 =
      new PeekFlowFunction<>(groupBySnapshotPositions, templateMessage_28::templateAndLogToConsole);
  private final TemplateMessage templateMessage_38 = new TemplateMessage<>("merged snapshot:{}");
  private final PeekFlowFunction peekFlowFunction_39 =
      new PeekFlowFunction<>(
          mapRef2RefFlowFunction_37, templateMessage_38::templateAndLogToConsole);
  public final EventFeedConnector eventFeedBatcher = new EventFeedConnector();
  private final ExportFunctionAuditEvent functionAudit = new ExportFunctionAuditEvent();
  //Dirty flags
  private boolean initCalled = false;
  private boolean processing = false;
  private boolean buffering = false;
  private final IdentityHashMap<Object, BooleanSupplier> dirtyFlagSupplierMap =
      new IdentityHashMap<>(42);
  private final IdentityHashMap<Object, Consumer<Boolean>> dirtyFlagUpdateMap =
      new IdentityHashMap<>(42);

  private boolean isDirty_binaryMapToRefFlowFunction_31 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_35 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_45 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_54 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_62 = false;
  private boolean isDirty_callBackNode_69 = false;
  private boolean isDirty_callBackNode_84 = false;
  private boolean isDirty_clock = false;
  private boolean isDirty_derivedRateNode = false;
  private boolean isDirty_flatMapFlowFunction_6 = false;
  private boolean isDirty_flatMapSnapshotPositions = false;
  private boolean isDirty_globalNetMtm = false;
  private boolean isDirty_groupBySnapshotPositions = false;
  private boolean isDirty_handlerPositionSnapshot = false;
  private boolean isDirty_handlerSignal_positionSnapshotReset = false;
  private boolean isDirty_handlerSignal_positionUpdate = false;
  private boolean isDirty_handlerTrade = false;
  private boolean isDirty_handlerTradeBatch = false;
  private boolean isDirty_instrumentNetMtm = false;
  private boolean isDirty_mapRef2RefFlowFunction_9 = false;
  private boolean isDirty_mapRef2RefFlowFunction_11 = false;
  private boolean isDirty_mapRef2RefFlowFunction_13 = false;
  private boolean isDirty_mapRef2RefFlowFunction_15 = false;
  private boolean isDirty_mapRef2RefFlowFunction_18 = false;
  private boolean isDirty_mapRef2RefFlowFunction_20 = false;
  private boolean isDirty_mapRef2RefFlowFunction_22 = false;
  private boolean isDirty_mapRef2RefFlowFunction_33 = false;
  private boolean isDirty_mapRef2RefFlowFunction_37 = false;
  private boolean isDirty_mapRef2RefFlowFunction_41 = false;
  private boolean isDirty_mapRef2RefFlowFunction_43 = false;
  private boolean isDirty_mapRef2RefFlowFunction_47 = false;
  private boolean isDirty_mapRef2RefFlowFunction_48 = false;
  private boolean isDirty_mapRef2RefFlowFunction_51 = false;
  private boolean isDirty_mapRef2RefFlowFunction_56 = false;
  private boolean isDirty_mapRef2RefFlowFunction_58 = false;
  private boolean isDirty_mapRef2RefFlowFunction_60 = false;
  private boolean isDirty_mapRef2RefFlowFunction_64 = false;
  private boolean isDirty_mergeFlowFunction_7 = false;
  private boolean isDirty_mergeFlowFunction_16 = false;
  private boolean isDirty_pushFlowFunction_50 = false;
  private boolean isDirty_pushFlowFunction_52 = false;
  private boolean isDirty_pushFlowFunction_66 = false;

  //Forked declarations

  //Filter constants

  //unknown event handler
  private Consumer unKnownEventHandler = (e) -> {};

  public FluxtionPnlCalculator(Map<Object, Object> contextMap) {
    if (context != null) {
      context.replaceMappings(contextMap);
    }
    binaryMapToRefFlowFunction_31.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_35.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_45.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_54.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_62.setEventProcessorContext(context);
    flatMapFlowFunction_6.callback = callBackNode_69;
    flatMapFlowFunction_6.dirtyStateMonitor = callbackDispatcher;
    flatMapSnapshotPositions.callback = callBackNode_84;
    flatMapSnapshotPositions.dirtyStateMonitor = callbackDispatcher;
    globalNetMtm.setEventProcessorContext(context);
    groupBySnapshotPositions.setEventProcessorContext(context);
    groupBySnapshotPositions.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    groupBySnapshotPositions.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    instrumentNetMtm.setEventProcessorContext(context);
    mapRef2RefFlowFunction_9.setEventProcessorContext(context);
    mapRef2RefFlowFunction_9.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_11.setEventProcessorContext(context);
    mapRef2RefFlowFunction_11.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_13.setEventProcessorContext(context);
    mapRef2RefFlowFunction_13.setPublishTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_13.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_15.setEventProcessorContext(context);
    mapRef2RefFlowFunction_15.setPublishTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_15.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_18.setEventProcessorContext(context);
    mapRef2RefFlowFunction_18.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_20.setEventProcessorContext(context);
    mapRef2RefFlowFunction_22.setEventProcessorContext(context);
    mapRef2RefFlowFunction_22.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_22.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_33.setEventProcessorContext(context);
    mapRef2RefFlowFunction_37.setEventProcessorContext(context);
    mapRef2RefFlowFunction_41.setEventProcessorContext(context);
    mapRef2RefFlowFunction_41.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_43.setEventProcessorContext(context);
    mapRef2RefFlowFunction_43.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_47.setEventProcessorContext(context);
    mapRef2RefFlowFunction_47.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_48.setEventProcessorContext(context);
    mapRef2RefFlowFunction_51.setEventProcessorContext(context);
    mapRef2RefFlowFunction_56.setEventProcessorContext(context);
    mapRef2RefFlowFunction_58.setEventProcessorContext(context);
    mapRef2RefFlowFunction_58.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_60.setEventProcessorContext(context);
    mapRef2RefFlowFunction_60.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_64.setEventProcessorContext(context);
    mapRef2RefFlowFunction_64.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mergeFlowFunction_7.dirtyStateMonitor = callbackDispatcher;
    mergeFlowFunction_16.dirtyStateMonitor = callbackDispatcher;
    peekFlowFunction_3.setEventProcessorContext(context);
    peekFlowFunction_5.setEventProcessorContext(context);
    peekFlowFunction_24.setEventProcessorContext(context);
    peekFlowFunction_29.setEventProcessorContext(context);
    peekFlowFunction_39.setEventProcessorContext(context);
    pushFlowFunction_50.setEventProcessorContext(context);
    pushFlowFunction_52.setEventProcessorContext(context);
    pushFlowFunction_66.setEventProcessorContext(context);
    templateMessage_2.clock = clock;
    templateMessage_4.clock = clock;
    templateMessage_23.clock = clock;
    templateMessage_28.clock = clock;
    templateMessage_38.clock = clock;
    context.setClock(clock);
    globalNetMtmListener.setEventProcessorContext(context);
    instrumentNetMtmListener.setEventProcessorContext(context);
    positionSnapshotListener.setEventProcessorContext(context);
    serviceRegistry.setEventProcessorContext(context);
    //node auditors
    initialiseAuditor(clock);
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
    handlerPositionSnapshot.init();
    handlerSignal_positionSnapshotReset.init();
    handlerSignal_positionUpdate.init();
    groupBySnapshotPositions.initialiseEventStream();
    handlerTrade.init();
    handlerTradeBatch.init();
    mapRef2RefFlowFunction_9.initialiseEventStream();
    mapRef2RefFlowFunction_11.initialiseEventStream();
    binaryMapToRefFlowFunction_31.initialiseEventStream();
    mapRef2RefFlowFunction_13.initialiseEventStream();
    mapRef2RefFlowFunction_15.initialiseEventStream();
    binaryMapToRefFlowFunction_54.initialiseEventStream();
    mapRef2RefFlowFunction_33.initialiseEventStream();
    binaryMapToRefFlowFunction_35.initialiseEventStream();
    mapRef2RefFlowFunction_37.initialiseEventStream();
    mapRef2RefFlowFunction_41.initialiseEventStream();
    mapRef2RefFlowFunction_43.initialiseEventStream();
    mapRef2RefFlowFunction_56.initialiseEventStream();
    mapRef2RefFlowFunction_58.initialiseEventStream();
    mapRef2RefFlowFunction_60.initialiseEventStream();
    mapRef2RefFlowFunction_18.initialiseEventStream();
    mapRef2RefFlowFunction_20.initialiseEventStream();
    mapRef2RefFlowFunction_22.initialiseEventStream();
    binaryMapToRefFlowFunction_45.initialiseEventStream();
    binaryMapToRefFlowFunction_62.initialiseEventStream();
    mapRef2RefFlowFunction_47.initialiseEventStream();
    mapRef2RefFlowFunction_48.initialiseEventStream();
    globalNetMtm.initialiseEventStream();
    mapRef2RefFlowFunction_64.initialiseEventStream();
    instrumentNetMtm.initialiseEventStream();
    pushFlowFunction_50.initialiseEventStream();
    mapRef2RefFlowFunction_51.initialiseEventStream();
    pushFlowFunction_52.initialiseEventStream();
    pushFlowFunction_66.initialiseEventStream();
    templateMessage_2.initialise();
    peekFlowFunction_3.initialiseEventStream();
    templateMessage_4.initialise();
    peekFlowFunction_5.initialiseEventStream();
    templateMessage_23.initialise();
    peekFlowFunction_24.initialiseEventStream();
    templateMessage_28.initialise();
    peekFlowFunction_29.initialiseEventStream();
    templateMessage_38.initialise();
    peekFlowFunction_39.initialiseEventStream();
    eventFeedBatcher.init();
    afterEvent();
  }

  @Override
  public void start() {
    if (!initCalled) {
      throw new RuntimeException("init() must be called before start()");
    }
    processing = true;
    auditEvent(Lifecycle.LifecycleEvent.Start);
    templateMessage_2.start();
    templateMessage_4.start();
    templateMessage_23.start();
    templateMessage_28.start();
    templateMessage_38.start();
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
    if (event
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
    } else if (event instanceof com.fluxtion.server.lib.pnl.dto.SymbolBatchDto) {
      SymbolBatchDto typedEvent = (SymbolBatchDto) event;
      handleEvent(typedEvent);
    } else if (event instanceof com.fluxtion.server.lib.pnl.dto.SymbolDto) {
      SymbolDto typedEvent = (SymbolDto) event;
      handleEvent(typedEvent);
    } else if (event instanceof com.fluxtion.server.lib.pnl.dto.TradeBatchDto) {
      TradeBatchDto typedEvent = (TradeBatchDto) event;
      handleEvent(typedEvent);
    } else if (event instanceof com.fluxtion.server.lib.pnl.dto.TradeDto) {
      TradeDto typedEvent = (TradeDto) event;
      handleEvent(typedEvent);
    } else {
      unKnownEventHandler(event);
    }
  }

  public void handleEvent(InstanceCallbackEvent_0 typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_callBackNode_69 = callBackNode_69.onEvent(typedEvent);
    if (guardCheck_flatMapFlowFunction_6()) {
      isDirty_flatMapFlowFunction_6 = true;
      flatMapFlowFunction_6.callbackReceived();
      if (isDirty_flatMapFlowFunction_6) {
        mergeFlowFunction_7.inputStreamUpdated(flatMapFlowFunction_6);
        mergeFlowFunction_16.inputStreamUpdated(flatMapFlowFunction_6);
      }
    }
    if (guardCheck_mergeFlowFunction_7()) {
      isDirty_mergeFlowFunction_7 = mergeFlowFunction_7.publishMerge();
      if (isDirty_mergeFlowFunction_7) {
        mapRef2RefFlowFunction_9.inputUpdated(mergeFlowFunction_7);
        mapRef2RefFlowFunction_11.inputUpdated(mergeFlowFunction_7);
        mapRef2RefFlowFunction_13.inputUpdated(mergeFlowFunction_7);
        mapRef2RefFlowFunction_15.inputUpdated(mergeFlowFunction_7);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_9()) {
      isDirty_mapRef2RefFlowFunction_9 = mapRef2RefFlowFunction_9.map();
      if (isDirty_mapRef2RefFlowFunction_9) {
        binaryMapToRefFlowFunction_31.inputUpdated(mapRef2RefFlowFunction_9);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_11()) {
      isDirty_mapRef2RefFlowFunction_11 = mapRef2RefFlowFunction_11.map();
      if (isDirty_mapRef2RefFlowFunction_11) {
        binaryMapToRefFlowFunction_31.input2Updated(mapRef2RefFlowFunction_11);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_31()) {
      isDirty_binaryMapToRefFlowFunction_31 = binaryMapToRefFlowFunction_31.map();
      if (isDirty_binaryMapToRefFlowFunction_31) {
        mapRef2RefFlowFunction_33.inputUpdated(binaryMapToRefFlowFunction_31);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_13()) {
      isDirty_mapRef2RefFlowFunction_13 = mapRef2RefFlowFunction_13.map();
      if (isDirty_mapRef2RefFlowFunction_13) {
        binaryMapToRefFlowFunction_54.inputUpdated(mapRef2RefFlowFunction_13);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_15()) {
      isDirty_mapRef2RefFlowFunction_15 = mapRef2RefFlowFunction_15.map();
      if (isDirty_mapRef2RefFlowFunction_15) {
        binaryMapToRefFlowFunction_54.input2Updated(mapRef2RefFlowFunction_15);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_54()) {
      isDirty_binaryMapToRefFlowFunction_54 = binaryMapToRefFlowFunction_54.map();
      if (isDirty_binaryMapToRefFlowFunction_54) {
        mapRef2RefFlowFunction_56.inputUpdated(binaryMapToRefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_33()) {
      isDirty_mapRef2RefFlowFunction_33 = mapRef2RefFlowFunction_33.map();
      if (isDirty_mapRef2RefFlowFunction_33) {
        binaryMapToRefFlowFunction_35.inputUpdated(mapRef2RefFlowFunction_33);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_35()) {
      isDirty_binaryMapToRefFlowFunction_35 = binaryMapToRefFlowFunction_35.map();
      if (isDirty_binaryMapToRefFlowFunction_35) {
        mapRef2RefFlowFunction_37.inputUpdated(binaryMapToRefFlowFunction_35);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_37()) {
      isDirty_mapRef2RefFlowFunction_37 = mapRef2RefFlowFunction_37.map();
      if (isDirty_mapRef2RefFlowFunction_37) {
        mapRef2RefFlowFunction_41.inputUpdated(mapRef2RefFlowFunction_37);
        peekFlowFunction_39.inputUpdated(mapRef2RefFlowFunction_37);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_41()) {
      isDirty_mapRef2RefFlowFunction_41 = mapRef2RefFlowFunction_41.map();
      if (isDirty_mapRef2RefFlowFunction_41) {
        mapRef2RefFlowFunction_43.inputUpdated(mapRef2RefFlowFunction_41);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_43()) {
      isDirty_mapRef2RefFlowFunction_43 = mapRef2RefFlowFunction_43.map();
      if (isDirty_mapRef2RefFlowFunction_43) {
        binaryMapToRefFlowFunction_45.inputUpdated(mapRef2RefFlowFunction_43);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_56()) {
      isDirty_mapRef2RefFlowFunction_56 = mapRef2RefFlowFunction_56.map();
      if (isDirty_mapRef2RefFlowFunction_56) {
        mapRef2RefFlowFunction_58.inputUpdated(mapRef2RefFlowFunction_56);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_58()) {
      isDirty_mapRef2RefFlowFunction_58 = mapRef2RefFlowFunction_58.map();
      if (isDirty_mapRef2RefFlowFunction_58) {
        mapRef2RefFlowFunction_60.inputUpdated(mapRef2RefFlowFunction_58);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_60()) {
      isDirty_mapRef2RefFlowFunction_60 = mapRef2RefFlowFunction_60.map();
      if (isDirty_mapRef2RefFlowFunction_60) {
        binaryMapToRefFlowFunction_62.inputUpdated(mapRef2RefFlowFunction_60);
      }
    }
    if (guardCheck_mergeFlowFunction_16()) {
      isDirty_mergeFlowFunction_16 = mergeFlowFunction_16.publishMerge();
      if (isDirty_mergeFlowFunction_16) {
        mapRef2RefFlowFunction_18.inputUpdated(mergeFlowFunction_16);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_18()) {
      isDirty_mapRef2RefFlowFunction_18 = mapRef2RefFlowFunction_18.map();
      if (isDirty_mapRef2RefFlowFunction_18) {
        mapRef2RefFlowFunction_20.inputUpdated(mapRef2RefFlowFunction_18);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_20()) {
      isDirty_mapRef2RefFlowFunction_20 = mapRef2RefFlowFunction_20.map();
      if (isDirty_mapRef2RefFlowFunction_20) {
        mapRef2RefFlowFunction_22.inputUpdated(mapRef2RefFlowFunction_20);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_22()) {
      isDirty_mapRef2RefFlowFunction_22 = mapRef2RefFlowFunction_22.map();
      if (isDirty_mapRef2RefFlowFunction_22) {
        binaryMapToRefFlowFunction_45.input2Updated(mapRef2RefFlowFunction_22);
        binaryMapToRefFlowFunction_62.input2Updated(mapRef2RefFlowFunction_22);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_45()) {
      isDirty_binaryMapToRefFlowFunction_45 = binaryMapToRefFlowFunction_45.map();
      if (isDirty_binaryMapToRefFlowFunction_45) {
        mapRef2RefFlowFunction_47.inputUpdated(binaryMapToRefFlowFunction_45);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_62()) {
      isDirty_binaryMapToRefFlowFunction_62 = binaryMapToRefFlowFunction_62.map();
      if (isDirty_binaryMapToRefFlowFunction_62) {
        mapRef2RefFlowFunction_64.inputUpdated(binaryMapToRefFlowFunction_62);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_47()) {
      isDirty_mapRef2RefFlowFunction_47 = mapRef2RefFlowFunction_47.map();
      if (isDirty_mapRef2RefFlowFunction_47) {
        mapRef2RefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_47);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_48()) {
      isDirty_mapRef2RefFlowFunction_48 = mapRef2RefFlowFunction_48.map();
      if (isDirty_mapRef2RefFlowFunction_48) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_48);
      }
    }
    if (guardCheck_globalNetMtm()) {
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_50.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_64()) {
      isDirty_mapRef2RefFlowFunction_64 = mapRef2RefFlowFunction_64.map();
      if (isDirty_mapRef2RefFlowFunction_64) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_64);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_66.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_50()) {
      isDirty_pushFlowFunction_50 = pushFlowFunction_50.push();
      if (isDirty_pushFlowFunction_50) {
        mapRef2RefFlowFunction_51.inputUpdated(pushFlowFunction_50);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_51()) {
      isDirty_mapRef2RefFlowFunction_51 = mapRef2RefFlowFunction_51.map();
      if (isDirty_mapRef2RefFlowFunction_51) {
        pushFlowFunction_52.inputUpdated(mapRef2RefFlowFunction_51);
      }
    }
    if (guardCheck_pushFlowFunction_52()) {
      isDirty_pushFlowFunction_52 = pushFlowFunction_52.push();
    }
    if (guardCheck_pushFlowFunction_66()) {
      isDirty_pushFlowFunction_66 = pushFlowFunction_66.push();
    }
    if (guardCheck_peekFlowFunction_39()) {
      peekFlowFunction_39.peek();
    }
    afterEvent();
  }

  public void handleEvent(InstanceCallbackEvent_1 typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_callBackNode_84 = callBackNode_84.onEvent(typedEvent);
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
        binaryMapToRefFlowFunction_35.input2Updated(groupBySnapshotPositions);
        peekFlowFunction_29.inputUpdated(groupBySnapshotPositions);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_35()) {
      isDirty_binaryMapToRefFlowFunction_35 = binaryMapToRefFlowFunction_35.map();
      if (isDirty_binaryMapToRefFlowFunction_35) {
        mapRef2RefFlowFunction_37.inputUpdated(binaryMapToRefFlowFunction_35);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_37()) {
      isDirty_mapRef2RefFlowFunction_37 = mapRef2RefFlowFunction_37.map();
      if (isDirty_mapRef2RefFlowFunction_37) {
        mapRef2RefFlowFunction_41.inputUpdated(mapRef2RefFlowFunction_37);
        peekFlowFunction_39.inputUpdated(mapRef2RefFlowFunction_37);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_41()) {
      isDirty_mapRef2RefFlowFunction_41 = mapRef2RefFlowFunction_41.map();
      if (isDirty_mapRef2RefFlowFunction_41) {
        mapRef2RefFlowFunction_43.inputUpdated(mapRef2RefFlowFunction_41);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_43()) {
      isDirty_mapRef2RefFlowFunction_43 = mapRef2RefFlowFunction_43.map();
      if (isDirty_mapRef2RefFlowFunction_43) {
        binaryMapToRefFlowFunction_45.inputUpdated(mapRef2RefFlowFunction_43);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_45()) {
      isDirty_binaryMapToRefFlowFunction_45 = binaryMapToRefFlowFunction_45.map();
      if (isDirty_binaryMapToRefFlowFunction_45) {
        mapRef2RefFlowFunction_47.inputUpdated(binaryMapToRefFlowFunction_45);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_47()) {
      isDirty_mapRef2RefFlowFunction_47 = mapRef2RefFlowFunction_47.map();
      if (isDirty_mapRef2RefFlowFunction_47) {
        mapRef2RefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_47);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_48()) {
      isDirty_mapRef2RefFlowFunction_48 = mapRef2RefFlowFunction_48.map();
      if (isDirty_mapRef2RefFlowFunction_48) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_48);
      }
    }
    if (guardCheck_globalNetMtm()) {
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_50.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_50()) {
      isDirty_pushFlowFunction_50 = pushFlowFunction_50.push();
      if (isDirty_pushFlowFunction_50) {
        mapRef2RefFlowFunction_51.inputUpdated(pushFlowFunction_50);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_51()) {
      isDirty_mapRef2RefFlowFunction_51 = mapRef2RefFlowFunction_51.map();
      if (isDirty_mapRef2RefFlowFunction_51) {
        pushFlowFunction_52.inputUpdated(mapRef2RefFlowFunction_51);
      }
    }
    if (guardCheck_pushFlowFunction_52()) {
      isDirty_pushFlowFunction_52 = pushFlowFunction_52.push();
    }
    if (guardCheck_peekFlowFunction_29()) {
      peekFlowFunction_29.peek();
    }
    if (guardCheck_peekFlowFunction_39()) {
      peekFlowFunction_39.peek();
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
      peekFlowFunction_24.inputUpdated(handlerPositionSnapshot);
    }
    if (guardCheck_peekFlowFunction_24()) {
      peekFlowFunction_24.peek();
    }
    afterEvent();
  }

  public void handleEvent(Trade typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_handlerTrade = handlerTrade.onEvent(typedEvent);
    if (isDirty_handlerTrade) {
      mergeFlowFunction_7.inputStreamUpdated(handlerTrade);
      mergeFlowFunction_16.inputStreamUpdated(handlerTrade);
    }
    if (guardCheck_mergeFlowFunction_7()) {
      isDirty_mergeFlowFunction_7 = mergeFlowFunction_7.publishMerge();
      if (isDirty_mergeFlowFunction_7) {
        mapRef2RefFlowFunction_9.inputUpdated(mergeFlowFunction_7);
        mapRef2RefFlowFunction_11.inputUpdated(mergeFlowFunction_7);
        mapRef2RefFlowFunction_13.inputUpdated(mergeFlowFunction_7);
        mapRef2RefFlowFunction_15.inputUpdated(mergeFlowFunction_7);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_9()) {
      isDirty_mapRef2RefFlowFunction_9 = mapRef2RefFlowFunction_9.map();
      if (isDirty_mapRef2RefFlowFunction_9) {
        binaryMapToRefFlowFunction_31.inputUpdated(mapRef2RefFlowFunction_9);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_11()) {
      isDirty_mapRef2RefFlowFunction_11 = mapRef2RefFlowFunction_11.map();
      if (isDirty_mapRef2RefFlowFunction_11) {
        binaryMapToRefFlowFunction_31.input2Updated(mapRef2RefFlowFunction_11);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_31()) {
      isDirty_binaryMapToRefFlowFunction_31 = binaryMapToRefFlowFunction_31.map();
      if (isDirty_binaryMapToRefFlowFunction_31) {
        mapRef2RefFlowFunction_33.inputUpdated(binaryMapToRefFlowFunction_31);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_13()) {
      isDirty_mapRef2RefFlowFunction_13 = mapRef2RefFlowFunction_13.map();
      if (isDirty_mapRef2RefFlowFunction_13) {
        binaryMapToRefFlowFunction_54.inputUpdated(mapRef2RefFlowFunction_13);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_15()) {
      isDirty_mapRef2RefFlowFunction_15 = mapRef2RefFlowFunction_15.map();
      if (isDirty_mapRef2RefFlowFunction_15) {
        binaryMapToRefFlowFunction_54.input2Updated(mapRef2RefFlowFunction_15);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_54()) {
      isDirty_binaryMapToRefFlowFunction_54 = binaryMapToRefFlowFunction_54.map();
      if (isDirty_binaryMapToRefFlowFunction_54) {
        mapRef2RefFlowFunction_56.inputUpdated(binaryMapToRefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_33()) {
      isDirty_mapRef2RefFlowFunction_33 = mapRef2RefFlowFunction_33.map();
      if (isDirty_mapRef2RefFlowFunction_33) {
        binaryMapToRefFlowFunction_35.inputUpdated(mapRef2RefFlowFunction_33);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_35()) {
      isDirty_binaryMapToRefFlowFunction_35 = binaryMapToRefFlowFunction_35.map();
      if (isDirty_binaryMapToRefFlowFunction_35) {
        mapRef2RefFlowFunction_37.inputUpdated(binaryMapToRefFlowFunction_35);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_37()) {
      isDirty_mapRef2RefFlowFunction_37 = mapRef2RefFlowFunction_37.map();
      if (isDirty_mapRef2RefFlowFunction_37) {
        mapRef2RefFlowFunction_41.inputUpdated(mapRef2RefFlowFunction_37);
        peekFlowFunction_39.inputUpdated(mapRef2RefFlowFunction_37);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_41()) {
      isDirty_mapRef2RefFlowFunction_41 = mapRef2RefFlowFunction_41.map();
      if (isDirty_mapRef2RefFlowFunction_41) {
        mapRef2RefFlowFunction_43.inputUpdated(mapRef2RefFlowFunction_41);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_43()) {
      isDirty_mapRef2RefFlowFunction_43 = mapRef2RefFlowFunction_43.map();
      if (isDirty_mapRef2RefFlowFunction_43) {
        binaryMapToRefFlowFunction_45.inputUpdated(mapRef2RefFlowFunction_43);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_56()) {
      isDirty_mapRef2RefFlowFunction_56 = mapRef2RefFlowFunction_56.map();
      if (isDirty_mapRef2RefFlowFunction_56) {
        mapRef2RefFlowFunction_58.inputUpdated(mapRef2RefFlowFunction_56);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_58()) {
      isDirty_mapRef2RefFlowFunction_58 = mapRef2RefFlowFunction_58.map();
      if (isDirty_mapRef2RefFlowFunction_58) {
        mapRef2RefFlowFunction_60.inputUpdated(mapRef2RefFlowFunction_58);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_60()) {
      isDirty_mapRef2RefFlowFunction_60 = mapRef2RefFlowFunction_60.map();
      if (isDirty_mapRef2RefFlowFunction_60) {
        binaryMapToRefFlowFunction_62.inputUpdated(mapRef2RefFlowFunction_60);
      }
    }
    if (guardCheck_mergeFlowFunction_16()) {
      isDirty_mergeFlowFunction_16 = mergeFlowFunction_16.publishMerge();
      if (isDirty_mergeFlowFunction_16) {
        mapRef2RefFlowFunction_18.inputUpdated(mergeFlowFunction_16);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_18()) {
      isDirty_mapRef2RefFlowFunction_18 = mapRef2RefFlowFunction_18.map();
      if (isDirty_mapRef2RefFlowFunction_18) {
        mapRef2RefFlowFunction_20.inputUpdated(mapRef2RefFlowFunction_18);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_20()) {
      isDirty_mapRef2RefFlowFunction_20 = mapRef2RefFlowFunction_20.map();
      if (isDirty_mapRef2RefFlowFunction_20) {
        mapRef2RefFlowFunction_22.inputUpdated(mapRef2RefFlowFunction_20);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_22()) {
      isDirty_mapRef2RefFlowFunction_22 = mapRef2RefFlowFunction_22.map();
      if (isDirty_mapRef2RefFlowFunction_22) {
        binaryMapToRefFlowFunction_45.input2Updated(mapRef2RefFlowFunction_22);
        binaryMapToRefFlowFunction_62.input2Updated(mapRef2RefFlowFunction_22);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_45()) {
      isDirty_binaryMapToRefFlowFunction_45 = binaryMapToRefFlowFunction_45.map();
      if (isDirty_binaryMapToRefFlowFunction_45) {
        mapRef2RefFlowFunction_47.inputUpdated(binaryMapToRefFlowFunction_45);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_62()) {
      isDirty_binaryMapToRefFlowFunction_62 = binaryMapToRefFlowFunction_62.map();
      if (isDirty_binaryMapToRefFlowFunction_62) {
        mapRef2RefFlowFunction_64.inputUpdated(binaryMapToRefFlowFunction_62);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_47()) {
      isDirty_mapRef2RefFlowFunction_47 = mapRef2RefFlowFunction_47.map();
      if (isDirty_mapRef2RefFlowFunction_47) {
        mapRef2RefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_47);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_48()) {
      isDirty_mapRef2RefFlowFunction_48 = mapRef2RefFlowFunction_48.map();
      if (isDirty_mapRef2RefFlowFunction_48) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_48);
      }
    }
    if (guardCheck_globalNetMtm()) {
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_50.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_64()) {
      isDirty_mapRef2RefFlowFunction_64 = mapRef2RefFlowFunction_64.map();
      if (isDirty_mapRef2RefFlowFunction_64) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_64);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_66.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_50()) {
      isDirty_pushFlowFunction_50 = pushFlowFunction_50.push();
      if (isDirty_pushFlowFunction_50) {
        mapRef2RefFlowFunction_51.inputUpdated(pushFlowFunction_50);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_51()) {
      isDirty_mapRef2RefFlowFunction_51 = mapRef2RefFlowFunction_51.map();
      if (isDirty_mapRef2RefFlowFunction_51) {
        pushFlowFunction_52.inputUpdated(mapRef2RefFlowFunction_51);
      }
    }
    if (guardCheck_pushFlowFunction_52()) {
      isDirty_pushFlowFunction_52 = pushFlowFunction_52.push();
    }
    if (guardCheck_pushFlowFunction_66()) {
      isDirty_pushFlowFunction_66 = pushFlowFunction_66.push();
    }
    if (guardCheck_peekFlowFunction_39()) {
      peekFlowFunction_39.peek();
    }
    afterEvent();
  }

  public void handleEvent(TradeBatch typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_handlerTradeBatch = handlerTradeBatch.onEvent(typedEvent);
    if (isDirty_handlerTradeBatch) {
      flatMapFlowFunction_6.inputUpdatedAndFlatMap(handlerTradeBatch);
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

  public void handleEvent(SymbolBatchDto typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    eventFeedBatcher.onSymbolBatchDto(typedEvent);
    afterEvent();
  }

  public void handleEvent(SymbolDto typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    eventFeedBatcher.onSymbolDto(typedEvent);
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
  //EVENT DISPATCH - END

  //FILTERED DISPATCH - START
  private void handle_Signal_positionSnapshotReset(Signal typedEvent) {
    isDirty_handlerSignal_positionSnapshotReset =
        handlerSignal_positionSnapshotReset.onEvent(typedEvent);
    if (isDirty_handlerSignal_positionSnapshotReset) {
      groupBySnapshotPositions.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_9.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_11.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_13.publishTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_13.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_15.publishTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_15.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_18.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      peekFlowFunction_5.inputUpdated(handlerSignal_positionSnapshotReset);
    }
    if (guardCheck_groupBySnapshotPositions()) {
      isDirty_groupBySnapshotPositions = groupBySnapshotPositions.map();
      if (isDirty_groupBySnapshotPositions) {
        binaryMapToRefFlowFunction_35.input2Updated(groupBySnapshotPositions);
        peekFlowFunction_29.inputUpdated(groupBySnapshotPositions);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_9()) {
      isDirty_mapRef2RefFlowFunction_9 = mapRef2RefFlowFunction_9.map();
      if (isDirty_mapRef2RefFlowFunction_9) {
        binaryMapToRefFlowFunction_31.inputUpdated(mapRef2RefFlowFunction_9);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_11()) {
      isDirty_mapRef2RefFlowFunction_11 = mapRef2RefFlowFunction_11.map();
      if (isDirty_mapRef2RefFlowFunction_11) {
        binaryMapToRefFlowFunction_31.input2Updated(mapRef2RefFlowFunction_11);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_31()) {
      isDirty_binaryMapToRefFlowFunction_31 = binaryMapToRefFlowFunction_31.map();
      if (isDirty_binaryMapToRefFlowFunction_31) {
        mapRef2RefFlowFunction_33.inputUpdated(binaryMapToRefFlowFunction_31);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_13()) {
      isDirty_mapRef2RefFlowFunction_13 = mapRef2RefFlowFunction_13.map();
      if (isDirty_mapRef2RefFlowFunction_13) {
        binaryMapToRefFlowFunction_54.inputUpdated(mapRef2RefFlowFunction_13);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_15()) {
      isDirty_mapRef2RefFlowFunction_15 = mapRef2RefFlowFunction_15.map();
      if (isDirty_mapRef2RefFlowFunction_15) {
        binaryMapToRefFlowFunction_54.input2Updated(mapRef2RefFlowFunction_15);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_54()) {
      isDirty_binaryMapToRefFlowFunction_54 = binaryMapToRefFlowFunction_54.map();
      if (isDirty_binaryMapToRefFlowFunction_54) {
        mapRef2RefFlowFunction_56.inputUpdated(binaryMapToRefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_33()) {
      isDirty_mapRef2RefFlowFunction_33 = mapRef2RefFlowFunction_33.map();
      if (isDirty_mapRef2RefFlowFunction_33) {
        binaryMapToRefFlowFunction_35.inputUpdated(mapRef2RefFlowFunction_33);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_35()) {
      isDirty_binaryMapToRefFlowFunction_35 = binaryMapToRefFlowFunction_35.map();
      if (isDirty_binaryMapToRefFlowFunction_35) {
        mapRef2RefFlowFunction_37.inputUpdated(binaryMapToRefFlowFunction_35);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_37()) {
      isDirty_mapRef2RefFlowFunction_37 = mapRef2RefFlowFunction_37.map();
      if (isDirty_mapRef2RefFlowFunction_37) {
        mapRef2RefFlowFunction_41.inputUpdated(mapRef2RefFlowFunction_37);
        peekFlowFunction_39.inputUpdated(mapRef2RefFlowFunction_37);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_41()) {
      isDirty_mapRef2RefFlowFunction_41 = mapRef2RefFlowFunction_41.map();
      if (isDirty_mapRef2RefFlowFunction_41) {
        mapRef2RefFlowFunction_43.inputUpdated(mapRef2RefFlowFunction_41);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_43()) {
      isDirty_mapRef2RefFlowFunction_43 = mapRef2RefFlowFunction_43.map();
      if (isDirty_mapRef2RefFlowFunction_43) {
        binaryMapToRefFlowFunction_45.inputUpdated(mapRef2RefFlowFunction_43);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_56()) {
      isDirty_mapRef2RefFlowFunction_56 = mapRef2RefFlowFunction_56.map();
      if (isDirty_mapRef2RefFlowFunction_56) {
        mapRef2RefFlowFunction_58.inputUpdated(mapRef2RefFlowFunction_56);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_58()) {
      isDirty_mapRef2RefFlowFunction_58 = mapRef2RefFlowFunction_58.map();
      if (isDirty_mapRef2RefFlowFunction_58) {
        mapRef2RefFlowFunction_60.inputUpdated(mapRef2RefFlowFunction_58);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_60()) {
      isDirty_mapRef2RefFlowFunction_60 = mapRef2RefFlowFunction_60.map();
      if (isDirty_mapRef2RefFlowFunction_60) {
        binaryMapToRefFlowFunction_62.inputUpdated(mapRef2RefFlowFunction_60);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_18()) {
      isDirty_mapRef2RefFlowFunction_18 = mapRef2RefFlowFunction_18.map();
      if (isDirty_mapRef2RefFlowFunction_18) {
        mapRef2RefFlowFunction_20.inputUpdated(mapRef2RefFlowFunction_18);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_20()) {
      isDirty_mapRef2RefFlowFunction_20 = mapRef2RefFlowFunction_20.map();
      if (isDirty_mapRef2RefFlowFunction_20) {
        mapRef2RefFlowFunction_22.inputUpdated(mapRef2RefFlowFunction_20);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_22()) {
      isDirty_mapRef2RefFlowFunction_22 = mapRef2RefFlowFunction_22.map();
      if (isDirty_mapRef2RefFlowFunction_22) {
        binaryMapToRefFlowFunction_45.input2Updated(mapRef2RefFlowFunction_22);
        binaryMapToRefFlowFunction_62.input2Updated(mapRef2RefFlowFunction_22);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_45()) {
      isDirty_binaryMapToRefFlowFunction_45 = binaryMapToRefFlowFunction_45.map();
      if (isDirty_binaryMapToRefFlowFunction_45) {
        mapRef2RefFlowFunction_47.inputUpdated(binaryMapToRefFlowFunction_45);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_62()) {
      isDirty_binaryMapToRefFlowFunction_62 = binaryMapToRefFlowFunction_62.map();
      if (isDirty_binaryMapToRefFlowFunction_62) {
        mapRef2RefFlowFunction_64.inputUpdated(binaryMapToRefFlowFunction_62);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_47()) {
      isDirty_mapRef2RefFlowFunction_47 = mapRef2RefFlowFunction_47.map();
      if (isDirty_mapRef2RefFlowFunction_47) {
        mapRef2RefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_47);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_48()) {
      isDirty_mapRef2RefFlowFunction_48 = mapRef2RefFlowFunction_48.map();
      if (isDirty_mapRef2RefFlowFunction_48) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_48);
      }
    }
    if (guardCheck_globalNetMtm()) {
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_50.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_64()) {
      isDirty_mapRef2RefFlowFunction_64 = mapRef2RefFlowFunction_64.map();
      if (isDirty_mapRef2RefFlowFunction_64) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_64);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_66.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_50()) {
      isDirty_pushFlowFunction_50 = pushFlowFunction_50.push();
      if (isDirty_pushFlowFunction_50) {
        mapRef2RefFlowFunction_51.inputUpdated(pushFlowFunction_50);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_51()) {
      isDirty_mapRef2RefFlowFunction_51 = mapRef2RefFlowFunction_51.map();
      if (isDirty_mapRef2RefFlowFunction_51) {
        pushFlowFunction_52.inputUpdated(mapRef2RefFlowFunction_51);
      }
    }
    if (guardCheck_pushFlowFunction_52()) {
      isDirty_pushFlowFunction_52 = pushFlowFunction_52.push();
    }
    if (guardCheck_pushFlowFunction_66()) {
      isDirty_pushFlowFunction_66 = pushFlowFunction_66.push();
    }
    if (guardCheck_peekFlowFunction_5()) {
      peekFlowFunction_5.peek();
    }
    if (guardCheck_peekFlowFunction_29()) {
      peekFlowFunction_29.peek();
    }
    if (guardCheck_peekFlowFunction_39()) {
      peekFlowFunction_39.peek();
    }
  }

  private void handle_Signal_positionUpdate(Signal typedEvent) {
    isDirty_handlerSignal_positionUpdate = handlerSignal_positionUpdate.onEvent(typedEvent);
    if (isDirty_handlerSignal_positionUpdate) {
      groupBySnapshotPositions.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_41.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_43.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_58.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_60.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_22.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_22.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_47.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_64.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      peekFlowFunction_3.inputUpdated(handlerSignal_positionUpdate);
    }
    if (guardCheck_groupBySnapshotPositions()) {
      isDirty_groupBySnapshotPositions = groupBySnapshotPositions.map();
      if (isDirty_groupBySnapshotPositions) {
        binaryMapToRefFlowFunction_35.input2Updated(groupBySnapshotPositions);
        peekFlowFunction_29.inputUpdated(groupBySnapshotPositions);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_35()) {
      isDirty_binaryMapToRefFlowFunction_35 = binaryMapToRefFlowFunction_35.map();
      if (isDirty_binaryMapToRefFlowFunction_35) {
        mapRef2RefFlowFunction_37.inputUpdated(binaryMapToRefFlowFunction_35);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_37()) {
      isDirty_mapRef2RefFlowFunction_37 = mapRef2RefFlowFunction_37.map();
      if (isDirty_mapRef2RefFlowFunction_37) {
        mapRef2RefFlowFunction_41.inputUpdated(mapRef2RefFlowFunction_37);
        peekFlowFunction_39.inputUpdated(mapRef2RefFlowFunction_37);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_41()) {
      isDirty_mapRef2RefFlowFunction_41 = mapRef2RefFlowFunction_41.map();
      if (isDirty_mapRef2RefFlowFunction_41) {
        mapRef2RefFlowFunction_43.inputUpdated(mapRef2RefFlowFunction_41);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_43()) {
      isDirty_mapRef2RefFlowFunction_43 = mapRef2RefFlowFunction_43.map();
      if (isDirty_mapRef2RefFlowFunction_43) {
        binaryMapToRefFlowFunction_45.inputUpdated(mapRef2RefFlowFunction_43);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_58()) {
      isDirty_mapRef2RefFlowFunction_58 = mapRef2RefFlowFunction_58.map();
      if (isDirty_mapRef2RefFlowFunction_58) {
        mapRef2RefFlowFunction_60.inputUpdated(mapRef2RefFlowFunction_58);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_60()) {
      isDirty_mapRef2RefFlowFunction_60 = mapRef2RefFlowFunction_60.map();
      if (isDirty_mapRef2RefFlowFunction_60) {
        binaryMapToRefFlowFunction_62.inputUpdated(mapRef2RefFlowFunction_60);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_22()) {
      isDirty_mapRef2RefFlowFunction_22 = mapRef2RefFlowFunction_22.map();
      if (isDirty_mapRef2RefFlowFunction_22) {
        binaryMapToRefFlowFunction_45.input2Updated(mapRef2RefFlowFunction_22);
        binaryMapToRefFlowFunction_62.input2Updated(mapRef2RefFlowFunction_22);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_45()) {
      isDirty_binaryMapToRefFlowFunction_45 = binaryMapToRefFlowFunction_45.map();
      if (isDirty_binaryMapToRefFlowFunction_45) {
        mapRef2RefFlowFunction_47.inputUpdated(binaryMapToRefFlowFunction_45);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_62()) {
      isDirty_binaryMapToRefFlowFunction_62 = binaryMapToRefFlowFunction_62.map();
      if (isDirty_binaryMapToRefFlowFunction_62) {
        mapRef2RefFlowFunction_64.inputUpdated(binaryMapToRefFlowFunction_62);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_47()) {
      isDirty_mapRef2RefFlowFunction_47 = mapRef2RefFlowFunction_47.map();
      if (isDirty_mapRef2RefFlowFunction_47) {
        mapRef2RefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_47);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_48()) {
      isDirty_mapRef2RefFlowFunction_48 = mapRef2RefFlowFunction_48.map();
      if (isDirty_mapRef2RefFlowFunction_48) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_48);
      }
    }
    if (guardCheck_globalNetMtm()) {
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_50.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_64()) {
      isDirty_mapRef2RefFlowFunction_64 = mapRef2RefFlowFunction_64.map();
      if (isDirty_mapRef2RefFlowFunction_64) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_64);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_66.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_50()) {
      isDirty_pushFlowFunction_50 = pushFlowFunction_50.push();
      if (isDirty_pushFlowFunction_50) {
        mapRef2RefFlowFunction_51.inputUpdated(pushFlowFunction_50);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_51()) {
      isDirty_mapRef2RefFlowFunction_51 = mapRef2RefFlowFunction_51.map();
      if (isDirty_mapRef2RefFlowFunction_51) {
        pushFlowFunction_52.inputUpdated(mapRef2RefFlowFunction_51);
      }
    }
    if (guardCheck_pushFlowFunction_52()) {
      isDirty_pushFlowFunction_52 = pushFlowFunction_52.push();
    }
    if (guardCheck_pushFlowFunction_66()) {
      isDirty_pushFlowFunction_66 = pushFlowFunction_66.push();
    }
    if (guardCheck_peekFlowFunction_3()) {
      peekFlowFunction_3.peek();
    }
    if (guardCheck_peekFlowFunction_29()) {
      peekFlowFunction_29.peek();
    }
    if (guardCheck_peekFlowFunction_39()) {
      peekFlowFunction_39.peek();
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
    nodeNameLookup.eventReceived(typedEvent);
    serviceRegistry.eventReceived(typedEvent);
  }

  private void auditEvent(Event typedEvent) {
    clock.eventReceived(typedEvent);
    nodeNameLookup.eventReceived(typedEvent);
    serviceRegistry.eventReceived(typedEvent);
  }

  private void initialiseAuditor(Auditor auditor) {
    auditor.init();
    auditor.nodeRegistered(callBackNode_69, "callBackNode_69");
    auditor.nodeRegistered(callBackNode_84, "callBackNode_84");
    auditor.nodeRegistered(callbackDispatcher, "callbackDispatcher");
    auditor.nodeRegistered(callBackTriggerEvent_1, "callBackTriggerEvent_1");
    auditor.nodeRegistered(callBackTriggerEvent_0, "callBackTriggerEvent_0");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_31, "binaryMapToRefFlowFunction_31");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_35, "binaryMapToRefFlowFunction_35");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_45, "binaryMapToRefFlowFunction_45");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_54, "binaryMapToRefFlowFunction_54");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_62, "binaryMapToRefFlowFunction_62");
    auditor.nodeRegistered(flatMapFlowFunction_6, "flatMapFlowFunction_6");
    auditor.nodeRegistered(flatMapSnapshotPositions, "flatMapSnapshotPositions");
    auditor.nodeRegistered(globalNetMtm, "globalNetMtm");
    auditor.nodeRegistered(groupBySnapshotPositions, "groupBySnapshotPositions");
    auditor.nodeRegistered(instrumentNetMtm, "instrumentNetMtm");
    auditor.nodeRegistered(mapRef2RefFlowFunction_9, "mapRef2RefFlowFunction_9");
    auditor.nodeRegistered(mapRef2RefFlowFunction_11, "mapRef2RefFlowFunction_11");
    auditor.nodeRegistered(mapRef2RefFlowFunction_13, "mapRef2RefFlowFunction_13");
    auditor.nodeRegistered(mapRef2RefFlowFunction_15, "mapRef2RefFlowFunction_15");
    auditor.nodeRegistered(mapRef2RefFlowFunction_18, "mapRef2RefFlowFunction_18");
    auditor.nodeRegistered(mapRef2RefFlowFunction_20, "mapRef2RefFlowFunction_20");
    auditor.nodeRegistered(mapRef2RefFlowFunction_22, "mapRef2RefFlowFunction_22");
    auditor.nodeRegistered(mapRef2RefFlowFunction_33, "mapRef2RefFlowFunction_33");
    auditor.nodeRegistered(mapRef2RefFlowFunction_37, "mapRef2RefFlowFunction_37");
    auditor.nodeRegistered(mapRef2RefFlowFunction_41, "mapRef2RefFlowFunction_41");
    auditor.nodeRegistered(mapRef2RefFlowFunction_43, "mapRef2RefFlowFunction_43");
    auditor.nodeRegistered(mapRef2RefFlowFunction_47, "mapRef2RefFlowFunction_47");
    auditor.nodeRegistered(mapRef2RefFlowFunction_48, "mapRef2RefFlowFunction_48");
    auditor.nodeRegistered(mapRef2RefFlowFunction_51, "mapRef2RefFlowFunction_51");
    auditor.nodeRegistered(mapRef2RefFlowFunction_56, "mapRef2RefFlowFunction_56");
    auditor.nodeRegistered(mapRef2RefFlowFunction_58, "mapRef2RefFlowFunction_58");
    auditor.nodeRegistered(mapRef2RefFlowFunction_60, "mapRef2RefFlowFunction_60");
    auditor.nodeRegistered(mapRef2RefFlowFunction_64, "mapRef2RefFlowFunction_64");
    auditor.nodeRegistered(mergeFlowFunction_7, "mergeFlowFunction_7");
    auditor.nodeRegistered(mergeFlowFunction_16, "mergeFlowFunction_16");
    auditor.nodeRegistered(peekFlowFunction_3, "peekFlowFunction_3");
    auditor.nodeRegistered(peekFlowFunction_5, "peekFlowFunction_5");
    auditor.nodeRegistered(peekFlowFunction_24, "peekFlowFunction_24");
    auditor.nodeRegistered(peekFlowFunction_29, "peekFlowFunction_29");
    auditor.nodeRegistered(peekFlowFunction_39, "peekFlowFunction_39");
    auditor.nodeRegistered(pushFlowFunction_50, "pushFlowFunction_50");
    auditor.nodeRegistered(pushFlowFunction_52, "pushFlowFunction_52");
    auditor.nodeRegistered(pushFlowFunction_66, "pushFlowFunction_66");
    auditor.nodeRegistered(emptyGroupBy_75, "emptyGroupBy_75");
    auditor.nodeRegistered(emptyGroupBy_136, "emptyGroupBy_136");
    auditor.nodeRegistered(emptyGroupBy_298, "emptyGroupBy_298");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_8, "groupByFlowFunctionWrapper_8");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_10, "groupByFlowFunctionWrapper_10");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_12, "groupByFlowFunctionWrapper_12");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_14, "groupByFlowFunctionWrapper_14");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_17, "groupByFlowFunctionWrapper_17");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_26, "groupByFlowFunctionWrapper_26");
    auditor.nodeRegistered(groupByMapFlowFunction_21, "groupByMapFlowFunction_21");
    auditor.nodeRegistered(groupByMapFlowFunction_32, "groupByMapFlowFunction_32");
    auditor.nodeRegistered(groupByMapFlowFunction_36, "groupByMapFlowFunction_36");
    auditor.nodeRegistered(groupByMapFlowFunction_40, "groupByMapFlowFunction_40");
    auditor.nodeRegistered(groupByMapFlowFunction_46, "groupByMapFlowFunction_46");
    auditor.nodeRegistered(groupByMapFlowFunction_55, "groupByMapFlowFunction_55");
    auditor.nodeRegistered(groupByMapFlowFunction_57, "groupByMapFlowFunction_57");
    auditor.nodeRegistered(groupByMapFlowFunction_63, "groupByMapFlowFunction_63");
    auditor.nodeRegistered(leftJoin_44, "leftJoin_44");
    auditor.nodeRegistered(leftJoin_61, "leftJoin_61");
    auditor.nodeRegistered(outerJoin_30, "outerJoin_30");
    auditor.nodeRegistered(outerJoin_34, "outerJoin_34");
    auditor.nodeRegistered(outerJoin_53, "outerJoin_53");
    auditor.nodeRegistered(defaultValue_19, "defaultValue_19");
    auditor.nodeRegistered(defaultValue_42, "defaultValue_42");
    auditor.nodeRegistered(defaultValue_59, "defaultValue_59");
    auditor.nodeRegistered(templateMessage_2, "templateMessage_2");
    auditor.nodeRegistered(templateMessage_4, "templateMessage_4");
    auditor.nodeRegistered(templateMessage_23, "templateMessage_23");
    auditor.nodeRegistered(templateMessage_28, "templateMessage_28");
    auditor.nodeRegistered(templateMessage_38, "templateMessage_38");
    auditor.nodeRegistered(mapTuple_263, "mapTuple_263");
    auditor.nodeRegistered(mapTuple_273, "mapTuple_273");
    auditor.nodeRegistered(mapTuple_277, "mapTuple_277");
    auditor.nodeRegistered(mapTuple_352, "mapTuple_352");
    auditor.nodeRegistered(mapTuple_362, "mapTuple_362");
    auditor.nodeRegistered(subscriptionManager, "subscriptionManager");
    auditor.nodeRegistered(handlerPositionSnapshot, "handlerPositionSnapshot");
    auditor.nodeRegistered(
        handlerSignal_positionSnapshotReset, "handlerSignal_positionSnapshotReset");
    auditor.nodeRegistered(handlerSignal_positionUpdate, "handlerSignal_positionUpdate");
    auditor.nodeRegistered(handlerTrade, "handlerTrade");
    auditor.nodeRegistered(handlerTradeBatch, "handlerTradeBatch");
    auditor.nodeRegistered(context, "context");
    auditor.nodeRegistered(globalNetMtmListener, "globalNetMtmListener");
    auditor.nodeRegistered(instrumentNetMtmListener, "instrumentNetMtmListener");
    auditor.nodeRegistered(positionSnapshotListener, "positionSnapshotListener");
    auditor.nodeRegistered(derivedRateNode, "derivedRateNode");
    auditor.nodeRegistered(eventFeedBatcher, "eventFeedBatcher");
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
    nodeNameLookup.processingComplete();
    serviceRegistry.processingComplete();
    isDirty_binaryMapToRefFlowFunction_31 = false;
    isDirty_binaryMapToRefFlowFunction_35 = false;
    isDirty_binaryMapToRefFlowFunction_45 = false;
    isDirty_binaryMapToRefFlowFunction_54 = false;
    isDirty_binaryMapToRefFlowFunction_62 = false;
    isDirty_callBackNode_69 = false;
    isDirty_callBackNode_84 = false;
    isDirty_clock = false;
    isDirty_derivedRateNode = false;
    isDirty_flatMapFlowFunction_6 = false;
    isDirty_flatMapSnapshotPositions = false;
    isDirty_globalNetMtm = false;
    isDirty_groupBySnapshotPositions = false;
    isDirty_handlerPositionSnapshot = false;
    isDirty_handlerSignal_positionSnapshotReset = false;
    isDirty_handlerSignal_positionUpdate = false;
    isDirty_handlerTrade = false;
    isDirty_handlerTradeBatch = false;
    isDirty_instrumentNetMtm = false;
    isDirty_mapRef2RefFlowFunction_9 = false;
    isDirty_mapRef2RefFlowFunction_11 = false;
    isDirty_mapRef2RefFlowFunction_13 = false;
    isDirty_mapRef2RefFlowFunction_15 = false;
    isDirty_mapRef2RefFlowFunction_18 = false;
    isDirty_mapRef2RefFlowFunction_20 = false;
    isDirty_mapRef2RefFlowFunction_22 = false;
    isDirty_mapRef2RefFlowFunction_33 = false;
    isDirty_mapRef2RefFlowFunction_37 = false;
    isDirty_mapRef2RefFlowFunction_41 = false;
    isDirty_mapRef2RefFlowFunction_43 = false;
    isDirty_mapRef2RefFlowFunction_47 = false;
    isDirty_mapRef2RefFlowFunction_48 = false;
    isDirty_mapRef2RefFlowFunction_51 = false;
    isDirty_mapRef2RefFlowFunction_56 = false;
    isDirty_mapRef2RefFlowFunction_58 = false;
    isDirty_mapRef2RefFlowFunction_60 = false;
    isDirty_mapRef2RefFlowFunction_64 = false;
    isDirty_mergeFlowFunction_7 = false;
    isDirty_mergeFlowFunction_16 = false;
    isDirty_pushFlowFunction_50 = false;
    isDirty_pushFlowFunction_52 = false;
    isDirty_pushFlowFunction_66 = false;
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
          binaryMapToRefFlowFunction_31, () -> isDirty_binaryMapToRefFlowFunction_31);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_35, () -> isDirty_binaryMapToRefFlowFunction_35);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_45, () -> isDirty_binaryMapToRefFlowFunction_45);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_54, () -> isDirty_binaryMapToRefFlowFunction_54);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_62, () -> isDirty_binaryMapToRefFlowFunction_62);
      dirtyFlagSupplierMap.put(callBackNode_69, () -> isDirty_callBackNode_69);
      dirtyFlagSupplierMap.put(callBackNode_84, () -> isDirty_callBackNode_84);
      dirtyFlagSupplierMap.put(clock, () -> isDirty_clock);
      dirtyFlagSupplierMap.put(derivedRateNode, () -> isDirty_derivedRateNode);
      dirtyFlagSupplierMap.put(flatMapFlowFunction_6, () -> isDirty_flatMapFlowFunction_6);
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
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_11, () -> isDirty_mapRef2RefFlowFunction_11);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_13, () -> isDirty_mapRef2RefFlowFunction_13);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_15, () -> isDirty_mapRef2RefFlowFunction_15);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_18, () -> isDirty_mapRef2RefFlowFunction_18);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_20, () -> isDirty_mapRef2RefFlowFunction_20);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_22, () -> isDirty_mapRef2RefFlowFunction_22);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_33, () -> isDirty_mapRef2RefFlowFunction_33);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_37, () -> isDirty_mapRef2RefFlowFunction_37);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_41, () -> isDirty_mapRef2RefFlowFunction_41);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_43, () -> isDirty_mapRef2RefFlowFunction_43);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_47, () -> isDirty_mapRef2RefFlowFunction_47);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_48, () -> isDirty_mapRef2RefFlowFunction_48);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_51, () -> isDirty_mapRef2RefFlowFunction_51);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_56, () -> isDirty_mapRef2RefFlowFunction_56);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_58, () -> isDirty_mapRef2RefFlowFunction_58);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_60, () -> isDirty_mapRef2RefFlowFunction_60);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_64, () -> isDirty_mapRef2RefFlowFunction_64);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_9, () -> isDirty_mapRef2RefFlowFunction_9);
      dirtyFlagSupplierMap.put(mergeFlowFunction_16, () -> isDirty_mergeFlowFunction_16);
      dirtyFlagSupplierMap.put(mergeFlowFunction_7, () -> isDirty_mergeFlowFunction_7);
      dirtyFlagSupplierMap.put(pushFlowFunction_50, () -> isDirty_pushFlowFunction_50);
      dirtyFlagSupplierMap.put(pushFlowFunction_52, () -> isDirty_pushFlowFunction_52);
      dirtyFlagSupplierMap.put(pushFlowFunction_66, () -> isDirty_pushFlowFunction_66);
    }
    return dirtyFlagSupplierMap.getOrDefault(node, StaticEventProcessor.ALWAYS_FALSE);
  }

  @Override
  public void setDirty(Object node, boolean dirtyFlag) {
    if (dirtyFlagUpdateMap.isEmpty()) {
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_31, (b) -> isDirty_binaryMapToRefFlowFunction_31 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_35, (b) -> isDirty_binaryMapToRefFlowFunction_35 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_45, (b) -> isDirty_binaryMapToRefFlowFunction_45 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_54, (b) -> isDirty_binaryMapToRefFlowFunction_54 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_62, (b) -> isDirty_binaryMapToRefFlowFunction_62 = b);
      dirtyFlagUpdateMap.put(callBackNode_69, (b) -> isDirty_callBackNode_69 = b);
      dirtyFlagUpdateMap.put(callBackNode_84, (b) -> isDirty_callBackNode_84 = b);
      dirtyFlagUpdateMap.put(clock, (b) -> isDirty_clock = b);
      dirtyFlagUpdateMap.put(derivedRateNode, (b) -> isDirty_derivedRateNode = b);
      dirtyFlagUpdateMap.put(flatMapFlowFunction_6, (b) -> isDirty_flatMapFlowFunction_6 = b);
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
          mapRef2RefFlowFunction_11, (b) -> isDirty_mapRef2RefFlowFunction_11 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_13, (b) -> isDirty_mapRef2RefFlowFunction_13 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_15, (b) -> isDirty_mapRef2RefFlowFunction_15 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_18, (b) -> isDirty_mapRef2RefFlowFunction_18 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_20, (b) -> isDirty_mapRef2RefFlowFunction_20 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_22, (b) -> isDirty_mapRef2RefFlowFunction_22 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_33, (b) -> isDirty_mapRef2RefFlowFunction_33 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_37, (b) -> isDirty_mapRef2RefFlowFunction_37 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_41, (b) -> isDirty_mapRef2RefFlowFunction_41 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_43, (b) -> isDirty_mapRef2RefFlowFunction_43 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_47, (b) -> isDirty_mapRef2RefFlowFunction_47 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_48, (b) -> isDirty_mapRef2RefFlowFunction_48 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_51, (b) -> isDirty_mapRef2RefFlowFunction_51 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_56, (b) -> isDirty_mapRef2RefFlowFunction_56 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_58, (b) -> isDirty_mapRef2RefFlowFunction_58 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_60, (b) -> isDirty_mapRef2RefFlowFunction_60 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_64, (b) -> isDirty_mapRef2RefFlowFunction_64 = b);
      dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_9, (b) -> isDirty_mapRef2RefFlowFunction_9 = b);
      dirtyFlagUpdateMap.put(mergeFlowFunction_16, (b) -> isDirty_mergeFlowFunction_16 = b);
      dirtyFlagUpdateMap.put(mergeFlowFunction_7, (b) -> isDirty_mergeFlowFunction_7 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_50, (b) -> isDirty_pushFlowFunction_50 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_52, (b) -> isDirty_pushFlowFunction_52 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_66, (b) -> isDirty_pushFlowFunction_66 = b);
    }
    dirtyFlagUpdateMap.get(node).accept(dirtyFlag);
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_31() {
    return isDirty_mapRef2RefFlowFunction_9 | isDirty_mapRef2RefFlowFunction_11;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_35() {
    return isDirty_groupBySnapshotPositions | isDirty_mapRef2RefFlowFunction_33;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_45() {
    return isDirty_mapRef2RefFlowFunction_22 | isDirty_mapRef2RefFlowFunction_43;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_54() {
    return isDirty_mapRef2RefFlowFunction_13 | isDirty_mapRef2RefFlowFunction_15;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_62() {
    return isDirty_mapRef2RefFlowFunction_22 | isDirty_mapRef2RefFlowFunction_60;
  }

  private boolean guardCheck_flatMapFlowFunction_6() {
    return isDirty_callBackNode_69;
  }

  private boolean guardCheck_flatMapSnapshotPositions() {
    return isDirty_callBackNode_84;
  }

  private boolean guardCheck_globalNetMtm() {
    return isDirty_mapRef2RefFlowFunction_48;
  }

  private boolean guardCheck_groupBySnapshotPositions() {
    return isDirty_flatMapSnapshotPositions
        | isDirty_handlerSignal_positionSnapshotReset
        | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_instrumentNetMtm() {
    return isDirty_mapRef2RefFlowFunction_64;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_9() {
    return isDirty_handlerSignal_positionSnapshotReset | isDirty_mergeFlowFunction_7;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_11() {
    return isDirty_handlerSignal_positionSnapshotReset | isDirty_mergeFlowFunction_7;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_13() {
    return isDirty_handlerSignal_positionSnapshotReset | isDirty_mergeFlowFunction_7;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_15() {
    return isDirty_handlerSignal_positionSnapshotReset | isDirty_mergeFlowFunction_7;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_18() {
    return isDirty_handlerSignal_positionSnapshotReset | isDirty_mergeFlowFunction_16;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_20() {
    return isDirty_mapRef2RefFlowFunction_18;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_22() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_20;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_33() {
    return isDirty_binaryMapToRefFlowFunction_31;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_37() {
    return isDirty_binaryMapToRefFlowFunction_35;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_41() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_37;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_43() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_41;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_47() {
    return isDirty_binaryMapToRefFlowFunction_45 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_48() {
    return isDirty_mapRef2RefFlowFunction_47;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_51() {
    return isDirty_pushFlowFunction_50;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_56() {
    return isDirty_binaryMapToRefFlowFunction_54;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_58() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_56;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_60() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_58;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_64() {
    return isDirty_binaryMapToRefFlowFunction_62 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mergeFlowFunction_7() {
    return isDirty_flatMapFlowFunction_6 | isDirty_handlerTrade;
  }

  private boolean guardCheck_mergeFlowFunction_16() {
    return isDirty_flatMapFlowFunction_6 | isDirty_handlerTrade;
  }

  private boolean guardCheck_peekFlowFunction_3() {
    return isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_peekFlowFunction_5() {
    return isDirty_handlerSignal_positionSnapshotReset;
  }

  private boolean guardCheck_peekFlowFunction_24() {
    return isDirty_handlerPositionSnapshot;
  }

  private boolean guardCheck_peekFlowFunction_29() {
    return isDirty_groupBySnapshotPositions;
  }

  private boolean guardCheck_peekFlowFunction_39() {
    return isDirty_mapRef2RefFlowFunction_37;
  }

  private boolean guardCheck_pushFlowFunction_50() {
    return isDirty_globalNetMtm;
  }

  private boolean guardCheck_pushFlowFunction_52() {
    return isDirty_mapRef2RefFlowFunction_51;
  }

  private boolean guardCheck_pushFlowFunction_66() {
    return isDirty_instrumentNetMtm;
  }

  private boolean guardCheck_groupByMapFlowFunction_21() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_groupByMapFlowFunction_40() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_groupByMapFlowFunction_57() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_context() {
    return isDirty_clock;
  }

  private boolean guardCheck_globalNetMtmListener() {
    return isDirty_pushFlowFunction_50;
  }

  private boolean guardCheck_instrumentNetMtmListener() {
    return isDirty_pushFlowFunction_66;
  }

  private boolean guardCheck_positionSnapshotListener() {
    return isDirty_pushFlowFunction_52;
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
