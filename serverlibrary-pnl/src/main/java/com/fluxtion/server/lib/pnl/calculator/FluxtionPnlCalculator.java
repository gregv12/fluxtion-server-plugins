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
import com.fluxtion.runtime.output.SinkDeregister;
import com.fluxtion.runtime.output.SinkPublisher;
import com.fluxtion.runtime.output.SinkRegistration;
import com.fluxtion.runtime.service.ServiceListener;
import com.fluxtion.runtime.service.ServiceRegistryNode;
import com.fluxtion.runtime.time.Clock;
import com.fluxtion.runtime.time.ClockStrategy.ClockStrategyEvent;
import com.fluxtion.server.lib.pnl.InstrumentPosMtm;
import com.fluxtion.server.lib.pnl.InstrumentPosition;
import com.fluxtion.server.lib.pnl.MidPrice;
import com.fluxtion.server.lib.pnl.MtmInstrument;
import com.fluxtion.server.lib.pnl.NetMarkToMarket;
import com.fluxtion.server.lib.pnl.PositionSnapshot;
import com.fluxtion.server.lib.pnl.Trade;
import com.fluxtion.server.lib.pnl.TradeBatch;
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
 * eventProcessorGenerator version : 9.4.1-SNAPSHOT
 * api version                     : 9.4.1-SNAPSHOT
 * </pre>
 *
 * Event classes supported:
 *
 * <ul>
 *   <li>com.fluxtion.compiler.generation.model.ExportFunctionMarker
 *   <li>com.fluxtion.runtime.callback.InstanceCallbackEvent.InstanceCallbackEvent_0
 *   <li>com.fluxtion.runtime.callback.InstanceCallbackEvent.InstanceCallbackEvent_1
 *   <li>com.fluxtion.runtime.event.NamedFeedEvent
 *   <li>com.fluxtion.runtime.event.Signal
 *   <li>com.fluxtion.runtime.output.SinkDeregister
 *   <li>com.fluxtion.runtime.output.SinkRegistration
 *   <li>com.fluxtion.runtime.time.ClockStrategy.ClockStrategyEvent
 *   <li>com.fluxtion.server.lib.pnl.MidPrice
 *   <li>com.fluxtion.server.lib.pnl.MtmInstrument
 *   <li>com.fluxtion.server.lib.pnl.PositionSnapshot
 *   <li>com.fluxtion.server.lib.pnl.Trade
 *   <li>com.fluxtion.server.lib.pnl.TradeBatch
 * </ul>
 *
 * @author Greg Higgins
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class FluxtionPnlCalculator
    implements EventProcessor<FluxtionPnlCalculator>,
        /*--- @ExportService start ---*/
        ServiceListener,
        /*--- @ExportService end ---*/
        StaticEventProcessor,
        InternalEventProcessor,
        BatchHandler,
        Lifecycle {

  //Node declarations
  private final InstanceCallbackEvent_0 callBackTriggerEvent_0 = new InstanceCallbackEvent_0();
  private final CallBackNode callBackNode_55 = new CallBackNode<>(callBackTriggerEvent_0);
  private final InstanceCallbackEvent_1 callBackTriggerEvent_1 = new InstanceCallbackEvent_1();
  private final CallBackNode callBackNode_69 = new CallBackNode<>(callBackTriggerEvent_1);
  private final CallbackDispatcherImpl callbackDispatcher = new CallbackDispatcherImpl();
  public final Clock clock = new Clock();
  public final DerivedRateNode derivedRateNode = new DerivedRateNode();
  private final EmptyGroupBy emptyGroupBy_61 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_15 = new DefaultValue<>(emptyGroupBy_61);
  private final EmptyGroupBy emptyGroupBy_109 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_32 = new DefaultValue<>(emptyGroupBy_109);
  private final EmptyGroupBy emptyGroupBy_233 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_47 = new DefaultValue<>(emptyGroupBy_233);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_4 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, SingleInstrumentPosMtmAggregate::dealt);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_6 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getContraInstrument, Mappers::identity, SingleInstrumentPosMtmAggregate::contra);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_8 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, InstrumentPosMtmAggregate::dealt);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_10 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getContraInstrument, Mappers::identity, InstrumentPosMtmAggregate::contra);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_13 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, FeeInstrumentPosMtmAggregate::new);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_20 =
      new GroupByFlowFunctionWrapper<>(
          InstrumentPosition::instrument, Mappers::identity, AggregateIdentityFlowFunction::new);
  private final GroupByMapFlowFunction groupByMapFlowFunction_17 =
      new GroupByMapFlowFunction(derivedRateNode::calculateFeeMtm);
  private final GroupByMapFlowFunction groupByMapFlowFunction_30 =
      new GroupByMapFlowFunction(derivedRateNode::calculateInstrumentPosMtm);
  private final GroupByMapFlowFunction groupByMapFlowFunction_45 =
      new GroupByMapFlowFunction(derivedRateNode::calculateInstrumentPosMtm);
  private final LeftJoin leftJoin_34 = new LeftJoin();
  private final LeftJoin leftJoin_49 = new LeftJoin();
  private final MapTuple mapTuple_198 = new MapTuple<>(NetMarkToMarket::combine);
  private final GroupByMapFlowFunction groupByMapFlowFunction_36 =
      new GroupByMapFlowFunction(mapTuple_198::mapTuple);
  private final MapTuple mapTuple_208 =
      new MapTuple<>(InstrumentPosMtm::overwriteInstrumentPositionWithSnapshot);
  private final GroupByMapFlowFunction groupByMapFlowFunction_28 =
      new GroupByMapFlowFunction(mapTuple_208::mapTuple);
  private final MapTuple mapTuple_212 = new MapTuple<>(InstrumentPosMtm::merge);
  private final GroupByMapFlowFunction groupByMapFlowFunction_24 =
      new GroupByMapFlowFunction(mapTuple_212::mapTuple);
  private final MapTuple mapTuple_287 = new MapTuple<>(NetMarkToMarket::combine);
  private final GroupByMapFlowFunction groupByMapFlowFunction_51 =
      new GroupByMapFlowFunction(mapTuple_287::mapTuple);
  private final MapTuple mapTuple_297 = new MapTuple<>(InstrumentPosMtm::merge);
  private final GroupByMapFlowFunction groupByMapFlowFunction_43 =
      new GroupByMapFlowFunction(mapTuple_297::mapTuple);
  public final NodeNameAuditor nodeNameLookup = new NodeNameAuditor();
  private final OuterJoin outerJoin_22 = new OuterJoin();
  private final OuterJoin outerJoin_26 = new OuterJoin();
  private final OuterJoin outerJoin_41 = new OuterJoin();
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
  private final FlatMapFlowFunction flatMapFlowFunction_19 =
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
  private final FlatMapFlowFunction flatMapFlowFunction_2 =
      new FlatMapFlowFunction<>(handlerTradeBatch, TradeBatch::getTrades);
  private final SinkPublisher instrumentNetMtmListener =
      new SinkPublisher<>("instrumentNetMtmListener");
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_21 =
      new MapRef2RefFlowFunction<>(
          flatMapFlowFunction_19, groupByFlowFunctionWrapper_20::aggregate);
  private final MergeFlowFunction mergeFlowFunction_3 =
      new MergeFlowFunction<>(Arrays.asList(handlerTrade, flatMapFlowFunction_2));
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_5 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_3, groupByFlowFunctionWrapper_4::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_7 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_3, groupByFlowFunctionWrapper_6::aggregate);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_23 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_5, mapRef2RefFlowFunction_7, outerJoin_22::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_9 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_3, groupByFlowFunctionWrapper_8::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_11 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_3, groupByFlowFunctionWrapper_10::aggregate);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_42 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_9, mapRef2RefFlowFunction_11, outerJoin_41::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_25 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_23, groupByMapFlowFunction_24::mapValues);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_27 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_25, mapRef2RefFlowFunction_21, outerJoin_26::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_29 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_27, groupByMapFlowFunction_28::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_31 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_29, groupByMapFlowFunction_30::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_33 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_31, defaultValue_32::getOrDefault);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_44 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_42, groupByMapFlowFunction_43::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_46 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_44, groupByMapFlowFunction_45::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_48 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_46, defaultValue_47::getOrDefault);
  private final MergeFlowFunction mergeFlowFunction_12 =
      new MergeFlowFunction<>(Arrays.asList(handlerTrade, flatMapFlowFunction_2));
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_14 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_12, groupByFlowFunctionWrapper_13::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_16 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_14, defaultValue_15::getOrDefault);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_18 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_16, groupByMapFlowFunction_17::mapValues);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_35 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_33, mapRef2RefFlowFunction_18, leftJoin_34::join);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_50 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_48, mapRef2RefFlowFunction_18, leftJoin_49::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_37 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_35, groupByMapFlowFunction_36::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_38 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_37, GroupBy<Object, Object>::toMap);
  public final MapRef2RefFlowFunction globalNetMtm =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_38, NetMarkToMarket::markToMarketSum);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_52 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_50, groupByMapFlowFunction_51::mapValues);
  public final MapRef2RefFlowFunction instrumentNetMtm =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_52, GroupBy<Object, Object>::toMap);
  private final PushFlowFunction pushFlowFunction_40 =
      new PushFlowFunction<>(globalNetMtm, globalNetMtmListener::publish);
  private final PushFlowFunction pushFlowFunction_54 =
      new PushFlowFunction<>(instrumentNetMtm, instrumentNetMtmListener::publish);
  public final ServiceRegistryNode serviceRegistry = new ServiceRegistryNode();
  public final EventFeedConnector eventFeedBatcher = new EventFeedConnector();
  private final ExportFunctionAuditEvent functionAudit = new ExportFunctionAuditEvent();
  //Dirty flags
  private boolean initCalled = false;
  private boolean processing = false;
  private boolean buffering = false;
  private final IdentityHashMap<Object, BooleanSupplier> dirtyFlagSupplierMap =
      new IdentityHashMap<>(40);
  private final IdentityHashMap<Object, Consumer<Boolean>> dirtyFlagUpdateMap =
      new IdentityHashMap<>(40);

  private boolean isDirty_binaryMapToRefFlowFunction_23 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_27 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_35 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_42 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_50 = false;
  private boolean isDirty_callBackNode_55 = false;
  private boolean isDirty_callBackNode_69 = false;
  private boolean isDirty_clock = false;
  private boolean isDirty_derivedRateNode = false;
  private boolean isDirty_flatMapFlowFunction_2 = false;
  private boolean isDirty_flatMapFlowFunction_19 = false;
  private boolean isDirty_globalNetMtm = false;
  private boolean isDirty_handlerPositionSnapshot = false;
  private boolean isDirty_handlerSignal_positionSnapshotReset = false;
  private boolean isDirty_handlerSignal_positionUpdate = false;
  private boolean isDirty_handlerTrade = false;
  private boolean isDirty_handlerTradeBatch = false;
  private boolean isDirty_instrumentNetMtm = false;
  private boolean isDirty_mapRef2RefFlowFunction_5 = false;
  private boolean isDirty_mapRef2RefFlowFunction_7 = false;
  private boolean isDirty_mapRef2RefFlowFunction_9 = false;
  private boolean isDirty_mapRef2RefFlowFunction_11 = false;
  private boolean isDirty_mapRef2RefFlowFunction_14 = false;
  private boolean isDirty_mapRef2RefFlowFunction_16 = false;
  private boolean isDirty_mapRef2RefFlowFunction_18 = false;
  private boolean isDirty_mapRef2RefFlowFunction_21 = false;
  private boolean isDirty_mapRef2RefFlowFunction_25 = false;
  private boolean isDirty_mapRef2RefFlowFunction_29 = false;
  private boolean isDirty_mapRef2RefFlowFunction_31 = false;
  private boolean isDirty_mapRef2RefFlowFunction_33 = false;
  private boolean isDirty_mapRef2RefFlowFunction_37 = false;
  private boolean isDirty_mapRef2RefFlowFunction_38 = false;
  private boolean isDirty_mapRef2RefFlowFunction_44 = false;
  private boolean isDirty_mapRef2RefFlowFunction_46 = false;
  private boolean isDirty_mapRef2RefFlowFunction_48 = false;
  private boolean isDirty_mapRef2RefFlowFunction_52 = false;
  private boolean isDirty_mergeFlowFunction_3 = false;
  private boolean isDirty_mergeFlowFunction_12 = false;
  private boolean isDirty_pushFlowFunction_40 = false;
  private boolean isDirty_pushFlowFunction_54 = false;

  //Forked declarations

  //Filter constants

  //unknown event handler
  private Consumer unKnownEventHandler = (e) -> {};

  public FluxtionPnlCalculator(Map<Object, Object> contextMap) {
    if (context != null) {
      context.replaceMappings(contextMap);
    }
    binaryMapToRefFlowFunction_23.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_27.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_35.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_42.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_50.setEventProcessorContext(context);
    flatMapFlowFunction_2.callback = callBackNode_55;
    flatMapFlowFunction_2.dirtyStateMonitor = callbackDispatcher;
    flatMapFlowFunction_19.callback = callBackNode_69;
    flatMapFlowFunction_19.dirtyStateMonitor = callbackDispatcher;
    globalNetMtm.setEventProcessorContext(context);
    instrumentNetMtm.setEventProcessorContext(context);
    mapRef2RefFlowFunction_5.setEventProcessorContext(context);
    mapRef2RefFlowFunction_5.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_7.setEventProcessorContext(context);
    mapRef2RefFlowFunction_7.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_9.setEventProcessorContext(context);
    mapRef2RefFlowFunction_9.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_11.setEventProcessorContext(context);
    mapRef2RefFlowFunction_11.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_14.setEventProcessorContext(context);
    mapRef2RefFlowFunction_14.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_16.setEventProcessorContext(context);
    mapRef2RefFlowFunction_18.setEventProcessorContext(context);
    mapRef2RefFlowFunction_18.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_18.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_21.setEventProcessorContext(context);
    mapRef2RefFlowFunction_21.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_21.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_25.setEventProcessorContext(context);
    mapRef2RefFlowFunction_29.setEventProcessorContext(context);
    mapRef2RefFlowFunction_31.setEventProcessorContext(context);
    mapRef2RefFlowFunction_31.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_33.setEventProcessorContext(context);
    mapRef2RefFlowFunction_33.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_37.setEventProcessorContext(context);
    mapRef2RefFlowFunction_37.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_38.setEventProcessorContext(context);
    mapRef2RefFlowFunction_44.setEventProcessorContext(context);
    mapRef2RefFlowFunction_46.setEventProcessorContext(context);
    mapRef2RefFlowFunction_46.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_48.setEventProcessorContext(context);
    mapRef2RefFlowFunction_48.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_52.setEventProcessorContext(context);
    mapRef2RefFlowFunction_52.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mergeFlowFunction_3.dirtyStateMonitor = callbackDispatcher;
    mergeFlowFunction_12.dirtyStateMonitor = callbackDispatcher;
    pushFlowFunction_40.setEventProcessorContext(context);
    pushFlowFunction_54.setEventProcessorContext(context);
    context.setClock(clock);
    globalNetMtmListener.setEventProcessorContext(context);
    instrumentNetMtmListener.setEventProcessorContext(context);
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
    handlerTrade.init();
    handlerTradeBatch.init();
    mapRef2RefFlowFunction_21.initialiseEventStream();
    mapRef2RefFlowFunction_5.initialiseEventStream();
    mapRef2RefFlowFunction_7.initialiseEventStream();
    binaryMapToRefFlowFunction_23.initialiseEventStream();
    mapRef2RefFlowFunction_9.initialiseEventStream();
    mapRef2RefFlowFunction_11.initialiseEventStream();
    binaryMapToRefFlowFunction_42.initialiseEventStream();
    mapRef2RefFlowFunction_25.initialiseEventStream();
    binaryMapToRefFlowFunction_27.initialiseEventStream();
    mapRef2RefFlowFunction_29.initialiseEventStream();
    mapRef2RefFlowFunction_31.initialiseEventStream();
    mapRef2RefFlowFunction_33.initialiseEventStream();
    mapRef2RefFlowFunction_44.initialiseEventStream();
    mapRef2RefFlowFunction_46.initialiseEventStream();
    mapRef2RefFlowFunction_48.initialiseEventStream();
    mapRef2RefFlowFunction_14.initialiseEventStream();
    mapRef2RefFlowFunction_16.initialiseEventStream();
    mapRef2RefFlowFunction_18.initialiseEventStream();
    binaryMapToRefFlowFunction_35.initialiseEventStream();
    binaryMapToRefFlowFunction_50.initialiseEventStream();
    mapRef2RefFlowFunction_37.initialiseEventStream();
    mapRef2RefFlowFunction_38.initialiseEventStream();
    globalNetMtm.initialiseEventStream();
    mapRef2RefFlowFunction_52.initialiseEventStream();
    instrumentNetMtm.initialiseEventStream();
    pushFlowFunction_40.initialiseEventStream();
    pushFlowFunction_54.initialiseEventStream();
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
      callbackDispatcher.processReentrantEvent(event);
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
    } else if (event instanceof com.fluxtion.runtime.event.NamedFeedEvent) {
      NamedFeedEvent typedEvent = (NamedFeedEvent) event;
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
    } else {
      unKnownEventHandler(event);
    }
  }

  public void handleEvent(InstanceCallbackEvent_0 typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_callBackNode_55 = callBackNode_55.onEvent(typedEvent);
    if (guardCheck_flatMapFlowFunction_2()) {
      isDirty_flatMapFlowFunction_2 = true;
      flatMapFlowFunction_2.callbackReceived();
      if (isDirty_flatMapFlowFunction_2) {
        mergeFlowFunction_3.inputStreamUpdated(flatMapFlowFunction_2);
        mergeFlowFunction_12.inputStreamUpdated(flatMapFlowFunction_2);
      }
    }
    if (guardCheck_mergeFlowFunction_3()) {
      isDirty_mergeFlowFunction_3 = mergeFlowFunction_3.publishMerge();
      if (isDirty_mergeFlowFunction_3) {
        mapRef2RefFlowFunction_5.inputUpdated(mergeFlowFunction_3);
        mapRef2RefFlowFunction_7.inputUpdated(mergeFlowFunction_3);
        mapRef2RefFlowFunction_9.inputUpdated(mergeFlowFunction_3);
        mapRef2RefFlowFunction_11.inputUpdated(mergeFlowFunction_3);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_5()) {
      isDirty_mapRef2RefFlowFunction_5 = mapRef2RefFlowFunction_5.map();
      if (isDirty_mapRef2RefFlowFunction_5) {
        binaryMapToRefFlowFunction_23.inputUpdated(mapRef2RefFlowFunction_5);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_7()) {
      isDirty_mapRef2RefFlowFunction_7 = mapRef2RefFlowFunction_7.map();
      if (isDirty_mapRef2RefFlowFunction_7) {
        binaryMapToRefFlowFunction_23.input2Updated(mapRef2RefFlowFunction_7);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_23()) {
      isDirty_binaryMapToRefFlowFunction_23 = binaryMapToRefFlowFunction_23.map();
      if (isDirty_binaryMapToRefFlowFunction_23) {
        mapRef2RefFlowFunction_25.inputUpdated(binaryMapToRefFlowFunction_23);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_9()) {
      isDirty_mapRef2RefFlowFunction_9 = mapRef2RefFlowFunction_9.map();
      if (isDirty_mapRef2RefFlowFunction_9) {
        binaryMapToRefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_9);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_11()) {
      isDirty_mapRef2RefFlowFunction_11 = mapRef2RefFlowFunction_11.map();
      if (isDirty_mapRef2RefFlowFunction_11) {
        binaryMapToRefFlowFunction_42.input2Updated(mapRef2RefFlowFunction_11);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_42()) {
      isDirty_binaryMapToRefFlowFunction_42 = binaryMapToRefFlowFunction_42.map();
      if (isDirty_binaryMapToRefFlowFunction_42) {
        mapRef2RefFlowFunction_44.inputUpdated(binaryMapToRefFlowFunction_42);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_25()) {
      isDirty_mapRef2RefFlowFunction_25 = mapRef2RefFlowFunction_25.map();
      if (isDirty_mapRef2RefFlowFunction_25) {
        binaryMapToRefFlowFunction_27.inputUpdated(mapRef2RefFlowFunction_25);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_27()) {
      isDirty_binaryMapToRefFlowFunction_27 = binaryMapToRefFlowFunction_27.map();
      if (isDirty_binaryMapToRefFlowFunction_27) {
        mapRef2RefFlowFunction_29.inputUpdated(binaryMapToRefFlowFunction_27);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_29()) {
      isDirty_mapRef2RefFlowFunction_29 = mapRef2RefFlowFunction_29.map();
      if (isDirty_mapRef2RefFlowFunction_29) {
        mapRef2RefFlowFunction_31.inputUpdated(mapRef2RefFlowFunction_29);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_31()) {
      isDirty_mapRef2RefFlowFunction_31 = mapRef2RefFlowFunction_31.map();
      if (isDirty_mapRef2RefFlowFunction_31) {
        mapRef2RefFlowFunction_33.inputUpdated(mapRef2RefFlowFunction_31);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_33()) {
      isDirty_mapRef2RefFlowFunction_33 = mapRef2RefFlowFunction_33.map();
      if (isDirty_mapRef2RefFlowFunction_33) {
        binaryMapToRefFlowFunction_35.inputUpdated(mapRef2RefFlowFunction_33);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_44()) {
      isDirty_mapRef2RefFlowFunction_44 = mapRef2RefFlowFunction_44.map();
      if (isDirty_mapRef2RefFlowFunction_44) {
        mapRef2RefFlowFunction_46.inputUpdated(mapRef2RefFlowFunction_44);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        mapRef2RefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_48()) {
      isDirty_mapRef2RefFlowFunction_48 = mapRef2RefFlowFunction_48.map();
      if (isDirty_mapRef2RefFlowFunction_48) {
        binaryMapToRefFlowFunction_50.inputUpdated(mapRef2RefFlowFunction_48);
      }
    }
    if (guardCheck_mergeFlowFunction_12()) {
      isDirty_mergeFlowFunction_12 = mergeFlowFunction_12.publishMerge();
      if (isDirty_mergeFlowFunction_12) {
        mapRef2RefFlowFunction_14.inputUpdated(mergeFlowFunction_12);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        mapRef2RefFlowFunction_16.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        mapRef2RefFlowFunction_18.inputUpdated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_18()) {
      isDirty_mapRef2RefFlowFunction_18 = mapRef2RefFlowFunction_18.map();
      if (isDirty_mapRef2RefFlowFunction_18) {
        binaryMapToRefFlowFunction_35.input2Updated(mapRef2RefFlowFunction_18);
        binaryMapToRefFlowFunction_50.input2Updated(mapRef2RefFlowFunction_18);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_35()) {
      isDirty_binaryMapToRefFlowFunction_35 = binaryMapToRefFlowFunction_35.map();
      if (isDirty_binaryMapToRefFlowFunction_35) {
        mapRef2RefFlowFunction_37.inputUpdated(binaryMapToRefFlowFunction_35);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_50()) {
      isDirty_binaryMapToRefFlowFunction_50 = binaryMapToRefFlowFunction_50.map();
      if (isDirty_binaryMapToRefFlowFunction_50) {
        mapRef2RefFlowFunction_52.inputUpdated(binaryMapToRefFlowFunction_50);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_37()) {
      isDirty_mapRef2RefFlowFunction_37 = mapRef2RefFlowFunction_37.map();
      if (isDirty_mapRef2RefFlowFunction_37) {
        mapRef2RefFlowFunction_38.inputUpdated(mapRef2RefFlowFunction_37);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_38()) {
      isDirty_mapRef2RefFlowFunction_38 = mapRef2RefFlowFunction_38.map();
      if (isDirty_mapRef2RefFlowFunction_38) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_38);
      }
    }
    if (guardCheck_globalNetMtm()) {
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_40.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_52()) {
      isDirty_mapRef2RefFlowFunction_52 = mapRef2RefFlowFunction_52.map();
      if (isDirty_mapRef2RefFlowFunction_52) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_52);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_54.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_40()) {
      isDirty_pushFlowFunction_40 = pushFlowFunction_40.push();
    }
    if (guardCheck_pushFlowFunction_54()) {
      isDirty_pushFlowFunction_54 = pushFlowFunction_54.push();
    }
    afterEvent();
  }

  public void handleEvent(InstanceCallbackEvent_1 typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_callBackNode_69 = callBackNode_69.onEvent(typedEvent);
    if (guardCheck_flatMapFlowFunction_19()) {
      isDirty_flatMapFlowFunction_19 = true;
      flatMapFlowFunction_19.callbackReceived();
      if (isDirty_flatMapFlowFunction_19) {
        mapRef2RefFlowFunction_21.inputUpdated(flatMapFlowFunction_19);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_21()) {
      isDirty_mapRef2RefFlowFunction_21 = mapRef2RefFlowFunction_21.map();
      if (isDirty_mapRef2RefFlowFunction_21) {
        binaryMapToRefFlowFunction_27.input2Updated(mapRef2RefFlowFunction_21);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_27()) {
      isDirty_binaryMapToRefFlowFunction_27 = binaryMapToRefFlowFunction_27.map();
      if (isDirty_binaryMapToRefFlowFunction_27) {
        mapRef2RefFlowFunction_29.inputUpdated(binaryMapToRefFlowFunction_27);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_29()) {
      isDirty_mapRef2RefFlowFunction_29 = mapRef2RefFlowFunction_29.map();
      if (isDirty_mapRef2RefFlowFunction_29) {
        mapRef2RefFlowFunction_31.inputUpdated(mapRef2RefFlowFunction_29);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_31()) {
      isDirty_mapRef2RefFlowFunction_31 = mapRef2RefFlowFunction_31.map();
      if (isDirty_mapRef2RefFlowFunction_31) {
        mapRef2RefFlowFunction_33.inputUpdated(mapRef2RefFlowFunction_31);
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
        mapRef2RefFlowFunction_38.inputUpdated(mapRef2RefFlowFunction_37);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_38()) {
      isDirty_mapRef2RefFlowFunction_38 = mapRef2RefFlowFunction_38.map();
      if (isDirty_mapRef2RefFlowFunction_38) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_38);
      }
    }
    if (guardCheck_globalNetMtm()) {
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_40.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_40()) {
      isDirty_pushFlowFunction_40 = pushFlowFunction_40.push();
    }
    afterEvent();
  }

  public void handleEvent(NamedFeedEvent typedEvent) {
    auditEvent(typedEvent);
    switch (typedEvent.filterString()) {
        //Event Class:[com.fluxtion.runtime.event.NamedFeedEvent] filterString:[tradeEventFeed]
      case ("tradeEventFeed"):
        handle_NamedFeedEvent_tradeEventFeed(typedEvent);
        afterEvent();
        return;
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
      flatMapFlowFunction_19.inputUpdatedAndFlatMap(handlerPositionSnapshot);
    }
    afterEvent();
  }

  public void handleEvent(Trade typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_handlerTrade = handlerTrade.onEvent(typedEvent);
    if (isDirty_handlerTrade) {
      mergeFlowFunction_3.inputStreamUpdated(handlerTrade);
      mergeFlowFunction_12.inputStreamUpdated(handlerTrade);
    }
    if (guardCheck_mergeFlowFunction_3()) {
      isDirty_mergeFlowFunction_3 = mergeFlowFunction_3.publishMerge();
      if (isDirty_mergeFlowFunction_3) {
        mapRef2RefFlowFunction_5.inputUpdated(mergeFlowFunction_3);
        mapRef2RefFlowFunction_7.inputUpdated(mergeFlowFunction_3);
        mapRef2RefFlowFunction_9.inputUpdated(mergeFlowFunction_3);
        mapRef2RefFlowFunction_11.inputUpdated(mergeFlowFunction_3);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_5()) {
      isDirty_mapRef2RefFlowFunction_5 = mapRef2RefFlowFunction_5.map();
      if (isDirty_mapRef2RefFlowFunction_5) {
        binaryMapToRefFlowFunction_23.inputUpdated(mapRef2RefFlowFunction_5);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_7()) {
      isDirty_mapRef2RefFlowFunction_7 = mapRef2RefFlowFunction_7.map();
      if (isDirty_mapRef2RefFlowFunction_7) {
        binaryMapToRefFlowFunction_23.input2Updated(mapRef2RefFlowFunction_7);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_23()) {
      isDirty_binaryMapToRefFlowFunction_23 = binaryMapToRefFlowFunction_23.map();
      if (isDirty_binaryMapToRefFlowFunction_23) {
        mapRef2RefFlowFunction_25.inputUpdated(binaryMapToRefFlowFunction_23);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_9()) {
      isDirty_mapRef2RefFlowFunction_9 = mapRef2RefFlowFunction_9.map();
      if (isDirty_mapRef2RefFlowFunction_9) {
        binaryMapToRefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_9);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_11()) {
      isDirty_mapRef2RefFlowFunction_11 = mapRef2RefFlowFunction_11.map();
      if (isDirty_mapRef2RefFlowFunction_11) {
        binaryMapToRefFlowFunction_42.input2Updated(mapRef2RefFlowFunction_11);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_42()) {
      isDirty_binaryMapToRefFlowFunction_42 = binaryMapToRefFlowFunction_42.map();
      if (isDirty_binaryMapToRefFlowFunction_42) {
        mapRef2RefFlowFunction_44.inputUpdated(binaryMapToRefFlowFunction_42);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_25()) {
      isDirty_mapRef2RefFlowFunction_25 = mapRef2RefFlowFunction_25.map();
      if (isDirty_mapRef2RefFlowFunction_25) {
        binaryMapToRefFlowFunction_27.inputUpdated(mapRef2RefFlowFunction_25);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_27()) {
      isDirty_binaryMapToRefFlowFunction_27 = binaryMapToRefFlowFunction_27.map();
      if (isDirty_binaryMapToRefFlowFunction_27) {
        mapRef2RefFlowFunction_29.inputUpdated(binaryMapToRefFlowFunction_27);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_29()) {
      isDirty_mapRef2RefFlowFunction_29 = mapRef2RefFlowFunction_29.map();
      if (isDirty_mapRef2RefFlowFunction_29) {
        mapRef2RefFlowFunction_31.inputUpdated(mapRef2RefFlowFunction_29);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_31()) {
      isDirty_mapRef2RefFlowFunction_31 = mapRef2RefFlowFunction_31.map();
      if (isDirty_mapRef2RefFlowFunction_31) {
        mapRef2RefFlowFunction_33.inputUpdated(mapRef2RefFlowFunction_31);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_33()) {
      isDirty_mapRef2RefFlowFunction_33 = mapRef2RefFlowFunction_33.map();
      if (isDirty_mapRef2RefFlowFunction_33) {
        binaryMapToRefFlowFunction_35.inputUpdated(mapRef2RefFlowFunction_33);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_44()) {
      isDirty_mapRef2RefFlowFunction_44 = mapRef2RefFlowFunction_44.map();
      if (isDirty_mapRef2RefFlowFunction_44) {
        mapRef2RefFlowFunction_46.inputUpdated(mapRef2RefFlowFunction_44);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        mapRef2RefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_48()) {
      isDirty_mapRef2RefFlowFunction_48 = mapRef2RefFlowFunction_48.map();
      if (isDirty_mapRef2RefFlowFunction_48) {
        binaryMapToRefFlowFunction_50.inputUpdated(mapRef2RefFlowFunction_48);
      }
    }
    if (guardCheck_mergeFlowFunction_12()) {
      isDirty_mergeFlowFunction_12 = mergeFlowFunction_12.publishMerge();
      if (isDirty_mergeFlowFunction_12) {
        mapRef2RefFlowFunction_14.inputUpdated(mergeFlowFunction_12);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        mapRef2RefFlowFunction_16.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        mapRef2RefFlowFunction_18.inputUpdated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_18()) {
      isDirty_mapRef2RefFlowFunction_18 = mapRef2RefFlowFunction_18.map();
      if (isDirty_mapRef2RefFlowFunction_18) {
        binaryMapToRefFlowFunction_35.input2Updated(mapRef2RefFlowFunction_18);
        binaryMapToRefFlowFunction_50.input2Updated(mapRef2RefFlowFunction_18);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_35()) {
      isDirty_binaryMapToRefFlowFunction_35 = binaryMapToRefFlowFunction_35.map();
      if (isDirty_binaryMapToRefFlowFunction_35) {
        mapRef2RefFlowFunction_37.inputUpdated(binaryMapToRefFlowFunction_35);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_50()) {
      isDirty_binaryMapToRefFlowFunction_50 = binaryMapToRefFlowFunction_50.map();
      if (isDirty_binaryMapToRefFlowFunction_50) {
        mapRef2RefFlowFunction_52.inputUpdated(binaryMapToRefFlowFunction_50);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_37()) {
      isDirty_mapRef2RefFlowFunction_37 = mapRef2RefFlowFunction_37.map();
      if (isDirty_mapRef2RefFlowFunction_37) {
        mapRef2RefFlowFunction_38.inputUpdated(mapRef2RefFlowFunction_37);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_38()) {
      isDirty_mapRef2RefFlowFunction_38 = mapRef2RefFlowFunction_38.map();
      if (isDirty_mapRef2RefFlowFunction_38) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_38);
      }
    }
    if (guardCheck_globalNetMtm()) {
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_40.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_52()) {
      isDirty_mapRef2RefFlowFunction_52 = mapRef2RefFlowFunction_52.map();
      if (isDirty_mapRef2RefFlowFunction_52) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_52);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_54.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_40()) {
      isDirty_pushFlowFunction_40 = pushFlowFunction_40.push();
    }
    if (guardCheck_pushFlowFunction_54()) {
      isDirty_pushFlowFunction_54 = pushFlowFunction_54.push();
    }
    afterEvent();
  }

  public void handleEvent(TradeBatch typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_handlerTradeBatch = handlerTradeBatch.onEvent(typedEvent);
    if (isDirty_handlerTradeBatch) {
      flatMapFlowFunction_2.inputUpdatedAndFlatMap(handlerTradeBatch);
    }
    afterEvent();
  }
  //EVENT DISPATCH - END

  //FILTERED DISPATCH - START
  private void handle_NamedFeedEvent_tradeEventFeed(NamedFeedEvent typedEvent) {
    eventFeedBatcher.onEvent(typedEvent);
  }

  private void handle_Signal_positionSnapshotReset(Signal typedEvent) {
    isDirty_handlerSignal_positionSnapshotReset =
        handlerSignal_positionSnapshotReset.onEvent(typedEvent);
    if (isDirty_handlerSignal_positionSnapshotReset) {
      mapRef2RefFlowFunction_21.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_5.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_7.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_9.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_11.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_14.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
    }
    if (guardCheck_mapRef2RefFlowFunction_21()) {
      isDirty_mapRef2RefFlowFunction_21 = mapRef2RefFlowFunction_21.map();
      if (isDirty_mapRef2RefFlowFunction_21) {
        binaryMapToRefFlowFunction_27.input2Updated(mapRef2RefFlowFunction_21);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_5()) {
      isDirty_mapRef2RefFlowFunction_5 = mapRef2RefFlowFunction_5.map();
      if (isDirty_mapRef2RefFlowFunction_5) {
        binaryMapToRefFlowFunction_23.inputUpdated(mapRef2RefFlowFunction_5);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_7()) {
      isDirty_mapRef2RefFlowFunction_7 = mapRef2RefFlowFunction_7.map();
      if (isDirty_mapRef2RefFlowFunction_7) {
        binaryMapToRefFlowFunction_23.input2Updated(mapRef2RefFlowFunction_7);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_23()) {
      isDirty_binaryMapToRefFlowFunction_23 = binaryMapToRefFlowFunction_23.map();
      if (isDirty_binaryMapToRefFlowFunction_23) {
        mapRef2RefFlowFunction_25.inputUpdated(binaryMapToRefFlowFunction_23);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_9()) {
      isDirty_mapRef2RefFlowFunction_9 = mapRef2RefFlowFunction_9.map();
      if (isDirty_mapRef2RefFlowFunction_9) {
        binaryMapToRefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_9);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_11()) {
      isDirty_mapRef2RefFlowFunction_11 = mapRef2RefFlowFunction_11.map();
      if (isDirty_mapRef2RefFlowFunction_11) {
        binaryMapToRefFlowFunction_42.input2Updated(mapRef2RefFlowFunction_11);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_42()) {
      isDirty_binaryMapToRefFlowFunction_42 = binaryMapToRefFlowFunction_42.map();
      if (isDirty_binaryMapToRefFlowFunction_42) {
        mapRef2RefFlowFunction_44.inputUpdated(binaryMapToRefFlowFunction_42);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_25()) {
      isDirty_mapRef2RefFlowFunction_25 = mapRef2RefFlowFunction_25.map();
      if (isDirty_mapRef2RefFlowFunction_25) {
        binaryMapToRefFlowFunction_27.inputUpdated(mapRef2RefFlowFunction_25);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_27()) {
      isDirty_binaryMapToRefFlowFunction_27 = binaryMapToRefFlowFunction_27.map();
      if (isDirty_binaryMapToRefFlowFunction_27) {
        mapRef2RefFlowFunction_29.inputUpdated(binaryMapToRefFlowFunction_27);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_29()) {
      isDirty_mapRef2RefFlowFunction_29 = mapRef2RefFlowFunction_29.map();
      if (isDirty_mapRef2RefFlowFunction_29) {
        mapRef2RefFlowFunction_31.inputUpdated(mapRef2RefFlowFunction_29);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_31()) {
      isDirty_mapRef2RefFlowFunction_31 = mapRef2RefFlowFunction_31.map();
      if (isDirty_mapRef2RefFlowFunction_31) {
        mapRef2RefFlowFunction_33.inputUpdated(mapRef2RefFlowFunction_31);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_33()) {
      isDirty_mapRef2RefFlowFunction_33 = mapRef2RefFlowFunction_33.map();
      if (isDirty_mapRef2RefFlowFunction_33) {
        binaryMapToRefFlowFunction_35.inputUpdated(mapRef2RefFlowFunction_33);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_44()) {
      isDirty_mapRef2RefFlowFunction_44 = mapRef2RefFlowFunction_44.map();
      if (isDirty_mapRef2RefFlowFunction_44) {
        mapRef2RefFlowFunction_46.inputUpdated(mapRef2RefFlowFunction_44);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        mapRef2RefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_48()) {
      isDirty_mapRef2RefFlowFunction_48 = mapRef2RefFlowFunction_48.map();
      if (isDirty_mapRef2RefFlowFunction_48) {
        binaryMapToRefFlowFunction_50.inputUpdated(mapRef2RefFlowFunction_48);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        mapRef2RefFlowFunction_16.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        mapRef2RefFlowFunction_18.inputUpdated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_18()) {
      isDirty_mapRef2RefFlowFunction_18 = mapRef2RefFlowFunction_18.map();
      if (isDirty_mapRef2RefFlowFunction_18) {
        binaryMapToRefFlowFunction_35.input2Updated(mapRef2RefFlowFunction_18);
        binaryMapToRefFlowFunction_50.input2Updated(mapRef2RefFlowFunction_18);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_35()) {
      isDirty_binaryMapToRefFlowFunction_35 = binaryMapToRefFlowFunction_35.map();
      if (isDirty_binaryMapToRefFlowFunction_35) {
        mapRef2RefFlowFunction_37.inputUpdated(binaryMapToRefFlowFunction_35);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_50()) {
      isDirty_binaryMapToRefFlowFunction_50 = binaryMapToRefFlowFunction_50.map();
      if (isDirty_binaryMapToRefFlowFunction_50) {
        mapRef2RefFlowFunction_52.inputUpdated(binaryMapToRefFlowFunction_50);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_37()) {
      isDirty_mapRef2RefFlowFunction_37 = mapRef2RefFlowFunction_37.map();
      if (isDirty_mapRef2RefFlowFunction_37) {
        mapRef2RefFlowFunction_38.inputUpdated(mapRef2RefFlowFunction_37);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_38()) {
      isDirty_mapRef2RefFlowFunction_38 = mapRef2RefFlowFunction_38.map();
      if (isDirty_mapRef2RefFlowFunction_38) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_38);
      }
    }
    if (guardCheck_globalNetMtm()) {
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_40.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_52()) {
      isDirty_mapRef2RefFlowFunction_52 = mapRef2RefFlowFunction_52.map();
      if (isDirty_mapRef2RefFlowFunction_52) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_52);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_54.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_40()) {
      isDirty_pushFlowFunction_40 = pushFlowFunction_40.push();
    }
    if (guardCheck_pushFlowFunction_54()) {
      isDirty_pushFlowFunction_54 = pushFlowFunction_54.push();
    }
  }

  private void handle_Signal_positionUpdate(Signal typedEvent) {
    isDirty_handlerSignal_positionUpdate = handlerSignal_positionUpdate.onEvent(typedEvent);
    if (isDirty_handlerSignal_positionUpdate) {
      mapRef2RefFlowFunction_21.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_31.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_33.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_46.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_48.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_18.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_18.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_37.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_52.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
    }
    if (guardCheck_mapRef2RefFlowFunction_21()) {
      isDirty_mapRef2RefFlowFunction_21 = mapRef2RefFlowFunction_21.map();
      if (isDirty_mapRef2RefFlowFunction_21) {
        binaryMapToRefFlowFunction_27.input2Updated(mapRef2RefFlowFunction_21);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_27()) {
      isDirty_binaryMapToRefFlowFunction_27 = binaryMapToRefFlowFunction_27.map();
      if (isDirty_binaryMapToRefFlowFunction_27) {
        mapRef2RefFlowFunction_29.inputUpdated(binaryMapToRefFlowFunction_27);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_29()) {
      isDirty_mapRef2RefFlowFunction_29 = mapRef2RefFlowFunction_29.map();
      if (isDirty_mapRef2RefFlowFunction_29) {
        mapRef2RefFlowFunction_31.inputUpdated(mapRef2RefFlowFunction_29);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_31()) {
      isDirty_mapRef2RefFlowFunction_31 = mapRef2RefFlowFunction_31.map();
      if (isDirty_mapRef2RefFlowFunction_31) {
        mapRef2RefFlowFunction_33.inputUpdated(mapRef2RefFlowFunction_31);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_33()) {
      isDirty_mapRef2RefFlowFunction_33 = mapRef2RefFlowFunction_33.map();
      if (isDirty_mapRef2RefFlowFunction_33) {
        binaryMapToRefFlowFunction_35.inputUpdated(mapRef2RefFlowFunction_33);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        mapRef2RefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_48()) {
      isDirty_mapRef2RefFlowFunction_48 = mapRef2RefFlowFunction_48.map();
      if (isDirty_mapRef2RefFlowFunction_48) {
        binaryMapToRefFlowFunction_50.inputUpdated(mapRef2RefFlowFunction_48);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_18()) {
      isDirty_mapRef2RefFlowFunction_18 = mapRef2RefFlowFunction_18.map();
      if (isDirty_mapRef2RefFlowFunction_18) {
        binaryMapToRefFlowFunction_35.input2Updated(mapRef2RefFlowFunction_18);
        binaryMapToRefFlowFunction_50.input2Updated(mapRef2RefFlowFunction_18);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_35()) {
      isDirty_binaryMapToRefFlowFunction_35 = binaryMapToRefFlowFunction_35.map();
      if (isDirty_binaryMapToRefFlowFunction_35) {
        mapRef2RefFlowFunction_37.inputUpdated(binaryMapToRefFlowFunction_35);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_50()) {
      isDirty_binaryMapToRefFlowFunction_50 = binaryMapToRefFlowFunction_50.map();
      if (isDirty_binaryMapToRefFlowFunction_50) {
        mapRef2RefFlowFunction_52.inputUpdated(binaryMapToRefFlowFunction_50);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_37()) {
      isDirty_mapRef2RefFlowFunction_37 = mapRef2RefFlowFunction_37.map();
      if (isDirty_mapRef2RefFlowFunction_37) {
        mapRef2RefFlowFunction_38.inputUpdated(mapRef2RefFlowFunction_37);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_38()) {
      isDirty_mapRef2RefFlowFunction_38 = mapRef2RefFlowFunction_38.map();
      if (isDirty_mapRef2RefFlowFunction_38) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_38);
      }
    }
    if (guardCheck_globalNetMtm()) {
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_40.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_52()) {
      isDirty_mapRef2RefFlowFunction_52 = mapRef2RefFlowFunction_52.map();
      if (isDirty_mapRef2RefFlowFunction_52) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_52);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_54.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_40()) {
      isDirty_pushFlowFunction_40 = pushFlowFunction_40.push();
    }
    if (guardCheck_pushFlowFunction_54()) {
      isDirty_pushFlowFunction_54 = pushFlowFunction_54.push();
    }
  }

  private void handle_SinkDeregister_globalNetMtmListener(SinkDeregister typedEvent) {
    globalNetMtmListener.unregisterSink(typedEvent);
  }

  private void handle_SinkDeregister_instrumentNetMtmListener(SinkDeregister typedEvent) {
    instrumentNetMtmListener.unregisterSink(typedEvent);
  }

  private void handle_SinkRegistration_globalNetMtmListener(SinkRegistration typedEvent) {
    globalNetMtmListener.sinkRegistration(typedEvent);
  }

  private void handle_SinkRegistration_instrumentNetMtmListener(SinkRegistration typedEvent) {
    instrumentNetMtmListener.sinkRegistration(typedEvent);
  }
  //FILTERED DISPATCH - END

  //EXPORTED SERVICE FUNCTIONS - START
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
    auditor.nodeRegistered(callBackNode_55, "callBackNode_55");
    auditor.nodeRegistered(callBackNode_69, "callBackNode_69");
    auditor.nodeRegistered(callbackDispatcher, "callbackDispatcher");
    auditor.nodeRegistered(callBackTriggerEvent_1, "callBackTriggerEvent_1");
    auditor.nodeRegistered(callBackTriggerEvent_0, "callBackTriggerEvent_0");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_23, "binaryMapToRefFlowFunction_23");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_27, "binaryMapToRefFlowFunction_27");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_35, "binaryMapToRefFlowFunction_35");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_42, "binaryMapToRefFlowFunction_42");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_50, "binaryMapToRefFlowFunction_50");
    auditor.nodeRegistered(flatMapFlowFunction_2, "flatMapFlowFunction_2");
    auditor.nodeRegistered(flatMapFlowFunction_19, "flatMapFlowFunction_19");
    auditor.nodeRegistered(globalNetMtm, "globalNetMtm");
    auditor.nodeRegistered(instrumentNetMtm, "instrumentNetMtm");
    auditor.nodeRegistered(mapRef2RefFlowFunction_5, "mapRef2RefFlowFunction_5");
    auditor.nodeRegistered(mapRef2RefFlowFunction_7, "mapRef2RefFlowFunction_7");
    auditor.nodeRegistered(mapRef2RefFlowFunction_9, "mapRef2RefFlowFunction_9");
    auditor.nodeRegistered(mapRef2RefFlowFunction_11, "mapRef2RefFlowFunction_11");
    auditor.nodeRegistered(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14");
    auditor.nodeRegistered(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16");
    auditor.nodeRegistered(mapRef2RefFlowFunction_18, "mapRef2RefFlowFunction_18");
    auditor.nodeRegistered(mapRef2RefFlowFunction_21, "mapRef2RefFlowFunction_21");
    auditor.nodeRegistered(mapRef2RefFlowFunction_25, "mapRef2RefFlowFunction_25");
    auditor.nodeRegistered(mapRef2RefFlowFunction_29, "mapRef2RefFlowFunction_29");
    auditor.nodeRegistered(mapRef2RefFlowFunction_31, "mapRef2RefFlowFunction_31");
    auditor.nodeRegistered(mapRef2RefFlowFunction_33, "mapRef2RefFlowFunction_33");
    auditor.nodeRegistered(mapRef2RefFlowFunction_37, "mapRef2RefFlowFunction_37");
    auditor.nodeRegistered(mapRef2RefFlowFunction_38, "mapRef2RefFlowFunction_38");
    auditor.nodeRegistered(mapRef2RefFlowFunction_44, "mapRef2RefFlowFunction_44");
    auditor.nodeRegistered(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46");
    auditor.nodeRegistered(mapRef2RefFlowFunction_48, "mapRef2RefFlowFunction_48");
    auditor.nodeRegistered(mapRef2RefFlowFunction_52, "mapRef2RefFlowFunction_52");
    auditor.nodeRegistered(mergeFlowFunction_3, "mergeFlowFunction_3");
    auditor.nodeRegistered(mergeFlowFunction_12, "mergeFlowFunction_12");
    auditor.nodeRegistered(pushFlowFunction_40, "pushFlowFunction_40");
    auditor.nodeRegistered(pushFlowFunction_54, "pushFlowFunction_54");
    auditor.nodeRegistered(emptyGroupBy_61, "emptyGroupBy_61");
    auditor.nodeRegistered(emptyGroupBy_109, "emptyGroupBy_109");
    auditor.nodeRegistered(emptyGroupBy_233, "emptyGroupBy_233");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_4, "groupByFlowFunctionWrapper_4");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_6, "groupByFlowFunctionWrapper_6");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_8, "groupByFlowFunctionWrapper_8");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_10, "groupByFlowFunctionWrapper_10");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_13, "groupByFlowFunctionWrapper_13");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_20, "groupByFlowFunctionWrapper_20");
    auditor.nodeRegistered(groupByMapFlowFunction_17, "groupByMapFlowFunction_17");
    auditor.nodeRegistered(groupByMapFlowFunction_24, "groupByMapFlowFunction_24");
    auditor.nodeRegistered(groupByMapFlowFunction_28, "groupByMapFlowFunction_28");
    auditor.nodeRegistered(groupByMapFlowFunction_30, "groupByMapFlowFunction_30");
    auditor.nodeRegistered(groupByMapFlowFunction_36, "groupByMapFlowFunction_36");
    auditor.nodeRegistered(groupByMapFlowFunction_43, "groupByMapFlowFunction_43");
    auditor.nodeRegistered(groupByMapFlowFunction_45, "groupByMapFlowFunction_45");
    auditor.nodeRegistered(groupByMapFlowFunction_51, "groupByMapFlowFunction_51");
    auditor.nodeRegistered(leftJoin_34, "leftJoin_34");
    auditor.nodeRegistered(leftJoin_49, "leftJoin_49");
    auditor.nodeRegistered(outerJoin_22, "outerJoin_22");
    auditor.nodeRegistered(outerJoin_26, "outerJoin_26");
    auditor.nodeRegistered(outerJoin_41, "outerJoin_41");
    auditor.nodeRegistered(defaultValue_15, "defaultValue_15");
    auditor.nodeRegistered(defaultValue_32, "defaultValue_32");
    auditor.nodeRegistered(defaultValue_47, "defaultValue_47");
    auditor.nodeRegistered(mapTuple_198, "mapTuple_198");
    auditor.nodeRegistered(mapTuple_208, "mapTuple_208");
    auditor.nodeRegistered(mapTuple_212, "mapTuple_212");
    auditor.nodeRegistered(mapTuple_287, "mapTuple_287");
    auditor.nodeRegistered(mapTuple_297, "mapTuple_297");
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
    isDirty_binaryMapToRefFlowFunction_23 = false;
    isDirty_binaryMapToRefFlowFunction_27 = false;
    isDirty_binaryMapToRefFlowFunction_35 = false;
    isDirty_binaryMapToRefFlowFunction_42 = false;
    isDirty_binaryMapToRefFlowFunction_50 = false;
    isDirty_callBackNode_55 = false;
    isDirty_callBackNode_69 = false;
    isDirty_clock = false;
    isDirty_derivedRateNode = false;
    isDirty_flatMapFlowFunction_2 = false;
    isDirty_flatMapFlowFunction_19 = false;
    isDirty_globalNetMtm = false;
    isDirty_handlerPositionSnapshot = false;
    isDirty_handlerSignal_positionSnapshotReset = false;
    isDirty_handlerSignal_positionUpdate = false;
    isDirty_handlerTrade = false;
    isDirty_handlerTradeBatch = false;
    isDirty_instrumentNetMtm = false;
    isDirty_mapRef2RefFlowFunction_5 = false;
    isDirty_mapRef2RefFlowFunction_7 = false;
    isDirty_mapRef2RefFlowFunction_9 = false;
    isDirty_mapRef2RefFlowFunction_11 = false;
    isDirty_mapRef2RefFlowFunction_14 = false;
    isDirty_mapRef2RefFlowFunction_16 = false;
    isDirty_mapRef2RefFlowFunction_18 = false;
    isDirty_mapRef2RefFlowFunction_21 = false;
    isDirty_mapRef2RefFlowFunction_25 = false;
    isDirty_mapRef2RefFlowFunction_29 = false;
    isDirty_mapRef2RefFlowFunction_31 = false;
    isDirty_mapRef2RefFlowFunction_33 = false;
    isDirty_mapRef2RefFlowFunction_37 = false;
    isDirty_mapRef2RefFlowFunction_38 = false;
    isDirty_mapRef2RefFlowFunction_44 = false;
    isDirty_mapRef2RefFlowFunction_46 = false;
    isDirty_mapRef2RefFlowFunction_48 = false;
    isDirty_mapRef2RefFlowFunction_52 = false;
    isDirty_mergeFlowFunction_3 = false;
    isDirty_mergeFlowFunction_12 = false;
    isDirty_pushFlowFunction_40 = false;
    isDirty_pushFlowFunction_54 = false;
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
          binaryMapToRefFlowFunction_23, () -> isDirty_binaryMapToRefFlowFunction_23);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_27, () -> isDirty_binaryMapToRefFlowFunction_27);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_35, () -> isDirty_binaryMapToRefFlowFunction_35);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_42, () -> isDirty_binaryMapToRefFlowFunction_42);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_50, () -> isDirty_binaryMapToRefFlowFunction_50);
      dirtyFlagSupplierMap.put(callBackNode_55, () -> isDirty_callBackNode_55);
      dirtyFlagSupplierMap.put(callBackNode_69, () -> isDirty_callBackNode_69);
      dirtyFlagSupplierMap.put(clock, () -> isDirty_clock);
      dirtyFlagSupplierMap.put(derivedRateNode, () -> isDirty_derivedRateNode);
      dirtyFlagSupplierMap.put(flatMapFlowFunction_19, () -> isDirty_flatMapFlowFunction_19);
      dirtyFlagSupplierMap.put(flatMapFlowFunction_2, () -> isDirty_flatMapFlowFunction_2);
      dirtyFlagSupplierMap.put(globalNetMtm, () -> isDirty_globalNetMtm);
      dirtyFlagSupplierMap.put(handlerPositionSnapshot, () -> isDirty_handlerPositionSnapshot);
      dirtyFlagSupplierMap.put(
          handlerSignal_positionSnapshotReset, () -> isDirty_handlerSignal_positionSnapshotReset);
      dirtyFlagSupplierMap.put(
          handlerSignal_positionUpdate, () -> isDirty_handlerSignal_positionUpdate);
      dirtyFlagSupplierMap.put(handlerTrade, () -> isDirty_handlerTrade);
      dirtyFlagSupplierMap.put(handlerTradeBatch, () -> isDirty_handlerTradeBatch);
      dirtyFlagSupplierMap.put(instrumentNetMtm, () -> isDirty_instrumentNetMtm);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_11, () -> isDirty_mapRef2RefFlowFunction_11);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_14, () -> isDirty_mapRef2RefFlowFunction_14);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_16, () -> isDirty_mapRef2RefFlowFunction_16);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_18, () -> isDirty_mapRef2RefFlowFunction_18);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_21, () -> isDirty_mapRef2RefFlowFunction_21);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_25, () -> isDirty_mapRef2RefFlowFunction_25);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_29, () -> isDirty_mapRef2RefFlowFunction_29);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_31, () -> isDirty_mapRef2RefFlowFunction_31);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_33, () -> isDirty_mapRef2RefFlowFunction_33);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_37, () -> isDirty_mapRef2RefFlowFunction_37);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_38, () -> isDirty_mapRef2RefFlowFunction_38);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_44, () -> isDirty_mapRef2RefFlowFunction_44);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_46, () -> isDirty_mapRef2RefFlowFunction_46);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_48, () -> isDirty_mapRef2RefFlowFunction_48);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_5, () -> isDirty_mapRef2RefFlowFunction_5);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_52, () -> isDirty_mapRef2RefFlowFunction_52);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_7, () -> isDirty_mapRef2RefFlowFunction_7);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_9, () -> isDirty_mapRef2RefFlowFunction_9);
      dirtyFlagSupplierMap.put(mergeFlowFunction_12, () -> isDirty_mergeFlowFunction_12);
      dirtyFlagSupplierMap.put(mergeFlowFunction_3, () -> isDirty_mergeFlowFunction_3);
      dirtyFlagSupplierMap.put(pushFlowFunction_40, () -> isDirty_pushFlowFunction_40);
      dirtyFlagSupplierMap.put(pushFlowFunction_54, () -> isDirty_pushFlowFunction_54);
    }
    return dirtyFlagSupplierMap.getOrDefault(node, StaticEventProcessor.ALWAYS_FALSE);
  }

  @Override
  public void setDirty(Object node, boolean dirtyFlag) {
    if (dirtyFlagUpdateMap.isEmpty()) {
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_23, (b) -> isDirty_binaryMapToRefFlowFunction_23 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_27, (b) -> isDirty_binaryMapToRefFlowFunction_27 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_35, (b) -> isDirty_binaryMapToRefFlowFunction_35 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_42, (b) -> isDirty_binaryMapToRefFlowFunction_42 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_50, (b) -> isDirty_binaryMapToRefFlowFunction_50 = b);
      dirtyFlagUpdateMap.put(callBackNode_55, (b) -> isDirty_callBackNode_55 = b);
      dirtyFlagUpdateMap.put(callBackNode_69, (b) -> isDirty_callBackNode_69 = b);
      dirtyFlagUpdateMap.put(clock, (b) -> isDirty_clock = b);
      dirtyFlagUpdateMap.put(derivedRateNode, (b) -> isDirty_derivedRateNode = b);
      dirtyFlagUpdateMap.put(flatMapFlowFunction_19, (b) -> isDirty_flatMapFlowFunction_19 = b);
      dirtyFlagUpdateMap.put(flatMapFlowFunction_2, (b) -> isDirty_flatMapFlowFunction_2 = b);
      dirtyFlagUpdateMap.put(globalNetMtm, (b) -> isDirty_globalNetMtm = b);
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
          mapRef2RefFlowFunction_14, (b) -> isDirty_mapRef2RefFlowFunction_14 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_16, (b) -> isDirty_mapRef2RefFlowFunction_16 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_18, (b) -> isDirty_mapRef2RefFlowFunction_18 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_21, (b) -> isDirty_mapRef2RefFlowFunction_21 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_25, (b) -> isDirty_mapRef2RefFlowFunction_25 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_29, (b) -> isDirty_mapRef2RefFlowFunction_29 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_31, (b) -> isDirty_mapRef2RefFlowFunction_31 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_33, (b) -> isDirty_mapRef2RefFlowFunction_33 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_37, (b) -> isDirty_mapRef2RefFlowFunction_37 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_38, (b) -> isDirty_mapRef2RefFlowFunction_38 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_44, (b) -> isDirty_mapRef2RefFlowFunction_44 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_46, (b) -> isDirty_mapRef2RefFlowFunction_46 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_48, (b) -> isDirty_mapRef2RefFlowFunction_48 = b);
      dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_5, (b) -> isDirty_mapRef2RefFlowFunction_5 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_52, (b) -> isDirty_mapRef2RefFlowFunction_52 = b);
      dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_7, (b) -> isDirty_mapRef2RefFlowFunction_7 = b);
      dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_9, (b) -> isDirty_mapRef2RefFlowFunction_9 = b);
      dirtyFlagUpdateMap.put(mergeFlowFunction_12, (b) -> isDirty_mergeFlowFunction_12 = b);
      dirtyFlagUpdateMap.put(mergeFlowFunction_3, (b) -> isDirty_mergeFlowFunction_3 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_40, (b) -> isDirty_pushFlowFunction_40 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_54, (b) -> isDirty_pushFlowFunction_54 = b);
    }
    dirtyFlagUpdateMap.get(node).accept(dirtyFlag);
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_23() {
    return isDirty_mapRef2RefFlowFunction_5 | isDirty_mapRef2RefFlowFunction_7;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_27() {
    return isDirty_mapRef2RefFlowFunction_21 | isDirty_mapRef2RefFlowFunction_25;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_35() {
    return isDirty_mapRef2RefFlowFunction_18 | isDirty_mapRef2RefFlowFunction_33;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_42() {
    return isDirty_mapRef2RefFlowFunction_9 | isDirty_mapRef2RefFlowFunction_11;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_50() {
    return isDirty_mapRef2RefFlowFunction_18 | isDirty_mapRef2RefFlowFunction_48;
  }

  private boolean guardCheck_flatMapFlowFunction_2() {
    return isDirty_callBackNode_55;
  }

  private boolean guardCheck_flatMapFlowFunction_19() {
    return isDirty_callBackNode_69;
  }

  private boolean guardCheck_globalNetMtm() {
    return isDirty_mapRef2RefFlowFunction_38;
  }

  private boolean guardCheck_instrumentNetMtm() {
    return isDirty_mapRef2RefFlowFunction_52;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_5() {
    return isDirty_handlerSignal_positionSnapshotReset | isDirty_mergeFlowFunction_3;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_7() {
    return isDirty_handlerSignal_positionSnapshotReset | isDirty_mergeFlowFunction_3;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_9() {
    return isDirty_handlerSignal_positionSnapshotReset | isDirty_mergeFlowFunction_3;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_11() {
    return isDirty_handlerSignal_positionSnapshotReset | isDirty_mergeFlowFunction_3;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_14() {
    return isDirty_handlerSignal_positionSnapshotReset | isDirty_mergeFlowFunction_12;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_16() {
    return isDirty_mapRef2RefFlowFunction_14;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_18() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_16;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_21() {
    return isDirty_flatMapFlowFunction_19
        | isDirty_handlerSignal_positionSnapshotReset
        | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_25() {
    return isDirty_binaryMapToRefFlowFunction_23;
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

  private boolean guardCheck_mapRef2RefFlowFunction_37() {
    return isDirty_binaryMapToRefFlowFunction_35 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_38() {
    return isDirty_mapRef2RefFlowFunction_37;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_44() {
    return isDirty_binaryMapToRefFlowFunction_42;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_46() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_44;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_48() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_46;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_52() {
    return isDirty_binaryMapToRefFlowFunction_50 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mergeFlowFunction_3() {
    return isDirty_flatMapFlowFunction_2 | isDirty_handlerTrade;
  }

  private boolean guardCheck_mergeFlowFunction_12() {
    return isDirty_flatMapFlowFunction_2 | isDirty_handlerTrade;
  }

  private boolean guardCheck_pushFlowFunction_40() {
    return isDirty_globalNetMtm;
  }

  private boolean guardCheck_pushFlowFunction_54() {
    return isDirty_instrumentNetMtm;
  }

  private boolean guardCheck_groupByMapFlowFunction_17() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_groupByMapFlowFunction_30() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_groupByMapFlowFunction_45() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_context() {
    return isDirty_clock;
  }

  private boolean guardCheck_globalNetMtmListener() {
    return isDirty_pushFlowFunction_40;
  }

  private boolean guardCheck_instrumentNetMtmListener() {
    return isDirty_pushFlowFunction_54;
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
