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
 * eventProcessorGenerator version : 9.3.50
 * api version                     : 9.3.50
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
  private final CallBackNode callBackNode_54 = new CallBackNode<>(callBackTriggerEvent_0);
  private final InstanceCallbackEvent_1 callBackTriggerEvent_1 = new InstanceCallbackEvent_1();
  private final CallBackNode callBackNode_68 = new CallBackNode<>(callBackTriggerEvent_1);
  private final CallbackDispatcherImpl callbackDispatcher = new CallbackDispatcherImpl();
  public final Clock clock = new Clock();
  public final DerivedRateNode derivedRateNode = new DerivedRateNode();
  private final EmptyGroupBy emptyGroupBy_60 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_14 = new DefaultValue<>(emptyGroupBy_60);
  private final EmptyGroupBy emptyGroupBy_108 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_31 = new DefaultValue<>(emptyGroupBy_108);
  private final EmptyGroupBy emptyGroupBy_232 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_46 = new DefaultValue<>(emptyGroupBy_232);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_3 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, SingleInstrumentPosMtmAggregate::dealt);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_5 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getContraInstrument, Mappers::identity, SingleInstrumentPosMtmAggregate::contra);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_7 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, InstrumentPosMtmAggregate::dealt);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_9 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getContraInstrument, Mappers::identity, InstrumentPosMtmAggregate::contra);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_12 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, FeeInstrumentPosMtmAggregate::new);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_19 =
      new GroupByFlowFunctionWrapper<>(
          InstrumentPosition::instrument, Mappers::identity, AggregateIdentityFlowFunction::new);
  private final GroupByMapFlowFunction groupByMapFlowFunction_16 =
      new GroupByMapFlowFunction(derivedRateNode::calculateFeeMtm);
  private final GroupByMapFlowFunction groupByMapFlowFunction_29 =
      new GroupByMapFlowFunction(derivedRateNode::calculateInstrumentPosMtm);
  private final GroupByMapFlowFunction groupByMapFlowFunction_44 =
      new GroupByMapFlowFunction(derivedRateNode::calculateInstrumentPosMtm);
  private final LeftJoin leftJoin_33 = new LeftJoin();
  private final LeftJoin leftJoin_48 = new LeftJoin();
  private final MapTuple mapTuple_197 = new MapTuple<>(NetMarkToMarket::combine);
  private final GroupByMapFlowFunction groupByMapFlowFunction_35 =
      new GroupByMapFlowFunction(mapTuple_197::mapTuple);
  private final MapTuple mapTuple_207 =
      new MapTuple<>(InstrumentPosMtm::overwriteInstrumentPositionWithSnapshot);
  private final GroupByMapFlowFunction groupByMapFlowFunction_27 =
      new GroupByMapFlowFunction(mapTuple_207::mapTuple);
  private final MapTuple mapTuple_211 = new MapTuple<>(InstrumentPosMtm::merge);
  private final GroupByMapFlowFunction groupByMapFlowFunction_23 =
      new GroupByMapFlowFunction(mapTuple_211::mapTuple);
  private final MapTuple mapTuple_286 = new MapTuple<>(NetMarkToMarket::combine);
  private final GroupByMapFlowFunction groupByMapFlowFunction_50 =
      new GroupByMapFlowFunction(mapTuple_286::mapTuple);
  private final MapTuple mapTuple_296 = new MapTuple<>(InstrumentPosMtm::merge);
  private final GroupByMapFlowFunction groupByMapFlowFunction_42 =
      new GroupByMapFlowFunction(mapTuple_296::mapTuple);
  public final NodeNameAuditor nodeNameLookup = new NodeNameAuditor();
  private final OuterJoin outerJoin_21 = new OuterJoin();
  private final OuterJoin outerJoin_25 = new OuterJoin();
  private final OuterJoin outerJoin_40 = new OuterJoin();
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
  private final FlatMapFlowFunction flatMapFlowFunction_18 =
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
  private final FlatMapFlowFunction flatMapFlowFunction_1 =
      new FlatMapFlowFunction<>(handlerTradeBatch, TradeBatch::getTrades);
  private final SinkPublisher instrumentNetMtmListener =
      new SinkPublisher<>("instrumentNetMtmListener");
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_20 =
      new MapRef2RefFlowFunction<>(
          flatMapFlowFunction_18, groupByFlowFunctionWrapper_19::aggregate);
  private final MergeFlowFunction mergeFlowFunction_2 =
      new MergeFlowFunction<>(Arrays.asList(handlerTrade, flatMapFlowFunction_1));
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_4 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_2, groupByFlowFunctionWrapper_3::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_6 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_2, groupByFlowFunctionWrapper_5::aggregate);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_22 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_4, mapRef2RefFlowFunction_6, outerJoin_21::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_8 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_2, groupByFlowFunctionWrapper_7::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_10 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_2, groupByFlowFunctionWrapper_9::aggregate);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_41 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_8, mapRef2RefFlowFunction_10, outerJoin_40::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_24 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_22, groupByMapFlowFunction_23::mapValues);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_26 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_24, mapRef2RefFlowFunction_20, outerJoin_25::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_28 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_26, groupByMapFlowFunction_27::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_30 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_28, groupByMapFlowFunction_29::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_32 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_30, defaultValue_31::getOrDefault);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_43 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_41, groupByMapFlowFunction_42::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_45 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_43, groupByMapFlowFunction_44::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_47 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_45, defaultValue_46::getOrDefault);
  private final MergeFlowFunction mergeFlowFunction_11 =
      new MergeFlowFunction<>(Arrays.asList(handlerTrade, flatMapFlowFunction_1));
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_13 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_11, groupByFlowFunctionWrapper_12::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_15 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_13, defaultValue_14::getOrDefault);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_17 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_15, groupByMapFlowFunction_16::mapValues);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_34 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_32, mapRef2RefFlowFunction_17, leftJoin_33::join);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_49 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_47, mapRef2RefFlowFunction_17, leftJoin_48::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_36 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_34, groupByMapFlowFunction_35::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_37 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_36, GroupBy<Object, Object>::toMap);
  public final MapRef2RefFlowFunction globalNetMtm =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_37, NetMarkToMarket::markToMarketSum);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_51 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_49, groupByMapFlowFunction_50::mapValues);
  public final MapRef2RefFlowFunction instrumentNetMtm =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_51, GroupBy<Object, Object>::toMap);
  private final PushFlowFunction pushFlowFunction_39 =
      new PushFlowFunction<>(globalNetMtm, globalNetMtmListener::publish);
  private final PushFlowFunction pushFlowFunction_53 =
      new PushFlowFunction<>(instrumentNetMtm, instrumentNetMtmListener::publish);
  public final ServiceRegistryNode serviceRegistry = new ServiceRegistryNode();
  private final ExportFunctionAuditEvent functionAudit = new ExportFunctionAuditEvent();
  //Dirty flags
  private boolean initCalled = false;
  private boolean processing = false;
  private boolean buffering = false;
  private final IdentityHashMap<Object, BooleanSupplier> dirtyFlagSupplierMap =
      new IdentityHashMap<>(40);
  private final IdentityHashMap<Object, Consumer<Boolean>> dirtyFlagUpdateMap =
      new IdentityHashMap<>(40);

  private boolean isDirty_binaryMapToRefFlowFunction_22 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_26 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_34 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_41 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_49 = false;
  private boolean isDirty_callBackNode_54 = false;
  private boolean isDirty_callBackNode_68 = false;
  private boolean isDirty_clock = false;
  private boolean isDirty_derivedRateNode = false;
  private boolean isDirty_flatMapFlowFunction_1 = false;
  private boolean isDirty_flatMapFlowFunction_18 = false;
  private boolean isDirty_globalNetMtm = false;
  private boolean isDirty_handlerPositionSnapshot = false;
  private boolean isDirty_handlerSignal_positionSnapshotReset = false;
  private boolean isDirty_handlerSignal_positionUpdate = false;
  private boolean isDirty_handlerTrade = false;
  private boolean isDirty_handlerTradeBatch = false;
  private boolean isDirty_instrumentNetMtm = false;
  private boolean isDirty_mapRef2RefFlowFunction_4 = false;
  private boolean isDirty_mapRef2RefFlowFunction_6 = false;
  private boolean isDirty_mapRef2RefFlowFunction_8 = false;
  private boolean isDirty_mapRef2RefFlowFunction_10 = false;
  private boolean isDirty_mapRef2RefFlowFunction_13 = false;
  private boolean isDirty_mapRef2RefFlowFunction_15 = false;
  private boolean isDirty_mapRef2RefFlowFunction_17 = false;
  private boolean isDirty_mapRef2RefFlowFunction_20 = false;
  private boolean isDirty_mapRef2RefFlowFunction_24 = false;
  private boolean isDirty_mapRef2RefFlowFunction_28 = false;
  private boolean isDirty_mapRef2RefFlowFunction_30 = false;
  private boolean isDirty_mapRef2RefFlowFunction_32 = false;
  private boolean isDirty_mapRef2RefFlowFunction_36 = false;
  private boolean isDirty_mapRef2RefFlowFunction_37 = false;
  private boolean isDirty_mapRef2RefFlowFunction_43 = false;
  private boolean isDirty_mapRef2RefFlowFunction_45 = false;
  private boolean isDirty_mapRef2RefFlowFunction_47 = false;
  private boolean isDirty_mapRef2RefFlowFunction_51 = false;
  private boolean isDirty_mergeFlowFunction_2 = false;
  private boolean isDirty_mergeFlowFunction_11 = false;
  private boolean isDirty_pushFlowFunction_39 = false;
  private boolean isDirty_pushFlowFunction_53 = false;

  //Forked declarations

  //Filter constants

  //unknown event handler
  private Consumer unKnownEventHandler = (e) -> {};

  public FluxtionPnlCalculator(Map<Object, Object> contextMap) {
    if (context != null) {
      context.replaceMappings(contextMap);
    }
    binaryMapToRefFlowFunction_22.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_26.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_34.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_41.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_49.setEventProcessorContext(context);
    flatMapFlowFunction_1.callback = callBackNode_54;
    flatMapFlowFunction_1.dirtyStateMonitor = callbackDispatcher;
    flatMapFlowFunction_18.callback = callBackNode_68;
    flatMapFlowFunction_18.dirtyStateMonitor = callbackDispatcher;
    globalNetMtm.setEventProcessorContext(context);
    instrumentNetMtm.setEventProcessorContext(context);
    mapRef2RefFlowFunction_4.setEventProcessorContext(context);
    mapRef2RefFlowFunction_4.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_6.setEventProcessorContext(context);
    mapRef2RefFlowFunction_6.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_8.setEventProcessorContext(context);
    mapRef2RefFlowFunction_8.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_10.setEventProcessorContext(context);
    mapRef2RefFlowFunction_10.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_13.setEventProcessorContext(context);
    mapRef2RefFlowFunction_13.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_15.setEventProcessorContext(context);
    mapRef2RefFlowFunction_17.setEventProcessorContext(context);
    mapRef2RefFlowFunction_17.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_17.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_20.setEventProcessorContext(context);
    mapRef2RefFlowFunction_20.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_20.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_24.setEventProcessorContext(context);
    mapRef2RefFlowFunction_28.setEventProcessorContext(context);
    mapRef2RefFlowFunction_30.setEventProcessorContext(context);
    mapRef2RefFlowFunction_30.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_32.setEventProcessorContext(context);
    mapRef2RefFlowFunction_32.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_36.setEventProcessorContext(context);
    mapRef2RefFlowFunction_36.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_37.setEventProcessorContext(context);
    mapRef2RefFlowFunction_43.setEventProcessorContext(context);
    mapRef2RefFlowFunction_45.setEventProcessorContext(context);
    mapRef2RefFlowFunction_45.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_47.setEventProcessorContext(context);
    mapRef2RefFlowFunction_47.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_51.setEventProcessorContext(context);
    mapRef2RefFlowFunction_51.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mergeFlowFunction_2.dirtyStateMonitor = callbackDispatcher;
    mergeFlowFunction_11.dirtyStateMonitor = callbackDispatcher;
    pushFlowFunction_39.setEventProcessorContext(context);
    pushFlowFunction_53.setEventProcessorContext(context);
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
    mapRef2RefFlowFunction_20.initialiseEventStream();
    mapRef2RefFlowFunction_4.initialiseEventStream();
    mapRef2RefFlowFunction_6.initialiseEventStream();
    binaryMapToRefFlowFunction_22.initialiseEventStream();
    mapRef2RefFlowFunction_8.initialiseEventStream();
    mapRef2RefFlowFunction_10.initialiseEventStream();
    binaryMapToRefFlowFunction_41.initialiseEventStream();
    mapRef2RefFlowFunction_24.initialiseEventStream();
    binaryMapToRefFlowFunction_26.initialiseEventStream();
    mapRef2RefFlowFunction_28.initialiseEventStream();
    mapRef2RefFlowFunction_30.initialiseEventStream();
    mapRef2RefFlowFunction_32.initialiseEventStream();
    mapRef2RefFlowFunction_43.initialiseEventStream();
    mapRef2RefFlowFunction_45.initialiseEventStream();
    mapRef2RefFlowFunction_47.initialiseEventStream();
    mapRef2RefFlowFunction_13.initialiseEventStream();
    mapRef2RefFlowFunction_15.initialiseEventStream();
    mapRef2RefFlowFunction_17.initialiseEventStream();
    binaryMapToRefFlowFunction_34.initialiseEventStream();
    binaryMapToRefFlowFunction_49.initialiseEventStream();
    mapRef2RefFlowFunction_36.initialiseEventStream();
    mapRef2RefFlowFunction_37.initialiseEventStream();
    globalNetMtm.initialiseEventStream();
    mapRef2RefFlowFunction_51.initialiseEventStream();
    instrumentNetMtm.initialiseEventStream();
    pushFlowFunction_39.initialiseEventStream();
    pushFlowFunction_53.initialiseEventStream();
    afterEvent();
  }

  @Override
  public void start() {
    if (!initCalled) {
      throw new RuntimeException("init() must be called before start()");
    }
    processing = true;
    auditEvent(Lifecycle.LifecycleEvent.Start);

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
    isDirty_callBackNode_54 = callBackNode_54.onEvent(typedEvent);
    if (guardCheck_flatMapFlowFunction_1()) {
      isDirty_flatMapFlowFunction_1 = true;
      flatMapFlowFunction_1.callbackReceived();
      if (isDirty_flatMapFlowFunction_1) {
        mergeFlowFunction_2.inputStreamUpdated(flatMapFlowFunction_1);
        mergeFlowFunction_11.inputStreamUpdated(flatMapFlowFunction_1);
      }
    }
    if (guardCheck_mergeFlowFunction_2()) {
      isDirty_mergeFlowFunction_2 = mergeFlowFunction_2.publishMerge();
      if (isDirty_mergeFlowFunction_2) {
        mapRef2RefFlowFunction_4.inputUpdated(mergeFlowFunction_2);
        mapRef2RefFlowFunction_6.inputUpdated(mergeFlowFunction_2);
        mapRef2RefFlowFunction_8.inputUpdated(mergeFlowFunction_2);
        mapRef2RefFlowFunction_10.inputUpdated(mergeFlowFunction_2);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_4()) {
      isDirty_mapRef2RefFlowFunction_4 = mapRef2RefFlowFunction_4.map();
      if (isDirty_mapRef2RefFlowFunction_4) {
        binaryMapToRefFlowFunction_22.inputUpdated(mapRef2RefFlowFunction_4);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_6()) {
      isDirty_mapRef2RefFlowFunction_6 = mapRef2RefFlowFunction_6.map();
      if (isDirty_mapRef2RefFlowFunction_6) {
        binaryMapToRefFlowFunction_22.input2Updated(mapRef2RefFlowFunction_6);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_22()) {
      isDirty_binaryMapToRefFlowFunction_22 = binaryMapToRefFlowFunction_22.map();
      if (isDirty_binaryMapToRefFlowFunction_22) {
        mapRef2RefFlowFunction_24.inputUpdated(binaryMapToRefFlowFunction_22);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_8()) {
      isDirty_mapRef2RefFlowFunction_8 = mapRef2RefFlowFunction_8.map();
      if (isDirty_mapRef2RefFlowFunction_8) {
        binaryMapToRefFlowFunction_41.inputUpdated(mapRef2RefFlowFunction_8);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_41.input2Updated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_41()) {
      isDirty_binaryMapToRefFlowFunction_41 = binaryMapToRefFlowFunction_41.map();
      if (isDirty_binaryMapToRefFlowFunction_41) {
        mapRef2RefFlowFunction_43.inputUpdated(binaryMapToRefFlowFunction_41);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_24()) {
      isDirty_mapRef2RefFlowFunction_24 = mapRef2RefFlowFunction_24.map();
      if (isDirty_mapRef2RefFlowFunction_24) {
        binaryMapToRefFlowFunction_26.inputUpdated(mapRef2RefFlowFunction_24);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_26()) {
      isDirty_binaryMapToRefFlowFunction_26 = binaryMapToRefFlowFunction_26.map();
      if (isDirty_binaryMapToRefFlowFunction_26) {
        mapRef2RefFlowFunction_28.inputUpdated(binaryMapToRefFlowFunction_26);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_28()) {
      isDirty_mapRef2RefFlowFunction_28 = mapRef2RefFlowFunction_28.map();
      if (isDirty_mapRef2RefFlowFunction_28) {
        mapRef2RefFlowFunction_30.inputUpdated(mapRef2RefFlowFunction_28);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_30()) {
      isDirty_mapRef2RefFlowFunction_30 = mapRef2RefFlowFunction_30.map();
      if (isDirty_mapRef2RefFlowFunction_30) {
        mapRef2RefFlowFunction_32.inputUpdated(mapRef2RefFlowFunction_30);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_32()) {
      isDirty_mapRef2RefFlowFunction_32 = mapRef2RefFlowFunction_32.map();
      if (isDirty_mapRef2RefFlowFunction_32) {
        binaryMapToRefFlowFunction_34.inputUpdated(mapRef2RefFlowFunction_32);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_43()) {
      isDirty_mapRef2RefFlowFunction_43 = mapRef2RefFlowFunction_43.map();
      if (isDirty_mapRef2RefFlowFunction_43) {
        mapRef2RefFlowFunction_45.inputUpdated(mapRef2RefFlowFunction_43);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_45()) {
      isDirty_mapRef2RefFlowFunction_45 = mapRef2RefFlowFunction_45.map();
      if (isDirty_mapRef2RefFlowFunction_45) {
        mapRef2RefFlowFunction_47.inputUpdated(mapRef2RefFlowFunction_45);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_47()) {
      isDirty_mapRef2RefFlowFunction_47 = mapRef2RefFlowFunction_47.map();
      if (isDirty_mapRef2RefFlowFunction_47) {
        binaryMapToRefFlowFunction_49.inputUpdated(mapRef2RefFlowFunction_47);
      }
    }
    if (guardCheck_mergeFlowFunction_11()) {
      isDirty_mergeFlowFunction_11 = mergeFlowFunction_11.publishMerge();
      if (isDirty_mergeFlowFunction_11) {
        mapRef2RefFlowFunction_13.inputUpdated(mergeFlowFunction_11);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_13()) {
      isDirty_mapRef2RefFlowFunction_13 = mapRef2RefFlowFunction_13.map();
      if (isDirty_mapRef2RefFlowFunction_13) {
        mapRef2RefFlowFunction_15.inputUpdated(mapRef2RefFlowFunction_13);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_15()) {
      isDirty_mapRef2RefFlowFunction_15 = mapRef2RefFlowFunction_15.map();
      if (isDirty_mapRef2RefFlowFunction_15) {
        mapRef2RefFlowFunction_17.inputUpdated(mapRef2RefFlowFunction_15);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_17()) {
      isDirty_mapRef2RefFlowFunction_17 = mapRef2RefFlowFunction_17.map();
      if (isDirty_mapRef2RefFlowFunction_17) {
        binaryMapToRefFlowFunction_34.input2Updated(mapRef2RefFlowFunction_17);
        binaryMapToRefFlowFunction_49.input2Updated(mapRef2RefFlowFunction_17);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_34()) {
      isDirty_binaryMapToRefFlowFunction_34 = binaryMapToRefFlowFunction_34.map();
      if (isDirty_binaryMapToRefFlowFunction_34) {
        mapRef2RefFlowFunction_36.inputUpdated(binaryMapToRefFlowFunction_34);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_49()) {
      isDirty_binaryMapToRefFlowFunction_49 = binaryMapToRefFlowFunction_49.map();
      if (isDirty_binaryMapToRefFlowFunction_49) {
        mapRef2RefFlowFunction_51.inputUpdated(binaryMapToRefFlowFunction_49);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_36()) {
      isDirty_mapRef2RefFlowFunction_36 = mapRef2RefFlowFunction_36.map();
      if (isDirty_mapRef2RefFlowFunction_36) {
        mapRef2RefFlowFunction_37.inputUpdated(mapRef2RefFlowFunction_36);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_37()) {
      isDirty_mapRef2RefFlowFunction_37 = mapRef2RefFlowFunction_37.map();
      if (isDirty_mapRef2RefFlowFunction_37) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_37);
      }
    }
    if (guardCheck_globalNetMtm()) {
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_39.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_51()) {
      isDirty_mapRef2RefFlowFunction_51 = mapRef2RefFlowFunction_51.map();
      if (isDirty_mapRef2RefFlowFunction_51) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_51);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_53.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_39()) {
      isDirty_pushFlowFunction_39 = pushFlowFunction_39.push();
    }
    if (guardCheck_pushFlowFunction_53()) {
      isDirty_pushFlowFunction_53 = pushFlowFunction_53.push();
    }
    afterEvent();
  }

  public void handleEvent(InstanceCallbackEvent_1 typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_callBackNode_68 = callBackNode_68.onEvent(typedEvent);
    if (guardCheck_flatMapFlowFunction_18()) {
      isDirty_flatMapFlowFunction_18 = true;
      flatMapFlowFunction_18.callbackReceived();
      if (isDirty_flatMapFlowFunction_18) {
        mapRef2RefFlowFunction_20.inputUpdated(flatMapFlowFunction_18);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_20()) {
      isDirty_mapRef2RefFlowFunction_20 = mapRef2RefFlowFunction_20.map();
      if (isDirty_mapRef2RefFlowFunction_20) {
        binaryMapToRefFlowFunction_26.input2Updated(mapRef2RefFlowFunction_20);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_26()) {
      isDirty_binaryMapToRefFlowFunction_26 = binaryMapToRefFlowFunction_26.map();
      if (isDirty_binaryMapToRefFlowFunction_26) {
        mapRef2RefFlowFunction_28.inputUpdated(binaryMapToRefFlowFunction_26);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_28()) {
      isDirty_mapRef2RefFlowFunction_28 = mapRef2RefFlowFunction_28.map();
      if (isDirty_mapRef2RefFlowFunction_28) {
        mapRef2RefFlowFunction_30.inputUpdated(mapRef2RefFlowFunction_28);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_30()) {
      isDirty_mapRef2RefFlowFunction_30 = mapRef2RefFlowFunction_30.map();
      if (isDirty_mapRef2RefFlowFunction_30) {
        mapRef2RefFlowFunction_32.inputUpdated(mapRef2RefFlowFunction_30);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_32()) {
      isDirty_mapRef2RefFlowFunction_32 = mapRef2RefFlowFunction_32.map();
      if (isDirty_mapRef2RefFlowFunction_32) {
        binaryMapToRefFlowFunction_34.inputUpdated(mapRef2RefFlowFunction_32);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_34()) {
      isDirty_binaryMapToRefFlowFunction_34 = binaryMapToRefFlowFunction_34.map();
      if (isDirty_binaryMapToRefFlowFunction_34) {
        mapRef2RefFlowFunction_36.inputUpdated(binaryMapToRefFlowFunction_34);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_36()) {
      isDirty_mapRef2RefFlowFunction_36 = mapRef2RefFlowFunction_36.map();
      if (isDirty_mapRef2RefFlowFunction_36) {
        mapRef2RefFlowFunction_37.inputUpdated(mapRef2RefFlowFunction_36);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_37()) {
      isDirty_mapRef2RefFlowFunction_37 = mapRef2RefFlowFunction_37.map();
      if (isDirty_mapRef2RefFlowFunction_37) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_37);
      }
    }
    if (guardCheck_globalNetMtm()) {
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_39.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_39()) {
      isDirty_pushFlowFunction_39 = pushFlowFunction_39.push();
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
      flatMapFlowFunction_18.inputUpdatedAndFlatMap(handlerPositionSnapshot);
    }
    afterEvent();
  }

  public void handleEvent(Trade typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_handlerTrade = handlerTrade.onEvent(typedEvent);
    if (isDirty_handlerTrade) {
      mergeFlowFunction_2.inputStreamUpdated(handlerTrade);
      mergeFlowFunction_11.inputStreamUpdated(handlerTrade);
    }
    if (guardCheck_mergeFlowFunction_2()) {
      isDirty_mergeFlowFunction_2 = mergeFlowFunction_2.publishMerge();
      if (isDirty_mergeFlowFunction_2) {
        mapRef2RefFlowFunction_4.inputUpdated(mergeFlowFunction_2);
        mapRef2RefFlowFunction_6.inputUpdated(mergeFlowFunction_2);
        mapRef2RefFlowFunction_8.inputUpdated(mergeFlowFunction_2);
        mapRef2RefFlowFunction_10.inputUpdated(mergeFlowFunction_2);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_4()) {
      isDirty_mapRef2RefFlowFunction_4 = mapRef2RefFlowFunction_4.map();
      if (isDirty_mapRef2RefFlowFunction_4) {
        binaryMapToRefFlowFunction_22.inputUpdated(mapRef2RefFlowFunction_4);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_6()) {
      isDirty_mapRef2RefFlowFunction_6 = mapRef2RefFlowFunction_6.map();
      if (isDirty_mapRef2RefFlowFunction_6) {
        binaryMapToRefFlowFunction_22.input2Updated(mapRef2RefFlowFunction_6);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_22()) {
      isDirty_binaryMapToRefFlowFunction_22 = binaryMapToRefFlowFunction_22.map();
      if (isDirty_binaryMapToRefFlowFunction_22) {
        mapRef2RefFlowFunction_24.inputUpdated(binaryMapToRefFlowFunction_22);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_8()) {
      isDirty_mapRef2RefFlowFunction_8 = mapRef2RefFlowFunction_8.map();
      if (isDirty_mapRef2RefFlowFunction_8) {
        binaryMapToRefFlowFunction_41.inputUpdated(mapRef2RefFlowFunction_8);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_41.input2Updated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_41()) {
      isDirty_binaryMapToRefFlowFunction_41 = binaryMapToRefFlowFunction_41.map();
      if (isDirty_binaryMapToRefFlowFunction_41) {
        mapRef2RefFlowFunction_43.inputUpdated(binaryMapToRefFlowFunction_41);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_24()) {
      isDirty_mapRef2RefFlowFunction_24 = mapRef2RefFlowFunction_24.map();
      if (isDirty_mapRef2RefFlowFunction_24) {
        binaryMapToRefFlowFunction_26.inputUpdated(mapRef2RefFlowFunction_24);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_26()) {
      isDirty_binaryMapToRefFlowFunction_26 = binaryMapToRefFlowFunction_26.map();
      if (isDirty_binaryMapToRefFlowFunction_26) {
        mapRef2RefFlowFunction_28.inputUpdated(binaryMapToRefFlowFunction_26);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_28()) {
      isDirty_mapRef2RefFlowFunction_28 = mapRef2RefFlowFunction_28.map();
      if (isDirty_mapRef2RefFlowFunction_28) {
        mapRef2RefFlowFunction_30.inputUpdated(mapRef2RefFlowFunction_28);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_30()) {
      isDirty_mapRef2RefFlowFunction_30 = mapRef2RefFlowFunction_30.map();
      if (isDirty_mapRef2RefFlowFunction_30) {
        mapRef2RefFlowFunction_32.inputUpdated(mapRef2RefFlowFunction_30);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_32()) {
      isDirty_mapRef2RefFlowFunction_32 = mapRef2RefFlowFunction_32.map();
      if (isDirty_mapRef2RefFlowFunction_32) {
        binaryMapToRefFlowFunction_34.inputUpdated(mapRef2RefFlowFunction_32);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_43()) {
      isDirty_mapRef2RefFlowFunction_43 = mapRef2RefFlowFunction_43.map();
      if (isDirty_mapRef2RefFlowFunction_43) {
        mapRef2RefFlowFunction_45.inputUpdated(mapRef2RefFlowFunction_43);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_45()) {
      isDirty_mapRef2RefFlowFunction_45 = mapRef2RefFlowFunction_45.map();
      if (isDirty_mapRef2RefFlowFunction_45) {
        mapRef2RefFlowFunction_47.inputUpdated(mapRef2RefFlowFunction_45);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_47()) {
      isDirty_mapRef2RefFlowFunction_47 = mapRef2RefFlowFunction_47.map();
      if (isDirty_mapRef2RefFlowFunction_47) {
        binaryMapToRefFlowFunction_49.inputUpdated(mapRef2RefFlowFunction_47);
      }
    }
    if (guardCheck_mergeFlowFunction_11()) {
      isDirty_mergeFlowFunction_11 = mergeFlowFunction_11.publishMerge();
      if (isDirty_mergeFlowFunction_11) {
        mapRef2RefFlowFunction_13.inputUpdated(mergeFlowFunction_11);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_13()) {
      isDirty_mapRef2RefFlowFunction_13 = mapRef2RefFlowFunction_13.map();
      if (isDirty_mapRef2RefFlowFunction_13) {
        mapRef2RefFlowFunction_15.inputUpdated(mapRef2RefFlowFunction_13);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_15()) {
      isDirty_mapRef2RefFlowFunction_15 = mapRef2RefFlowFunction_15.map();
      if (isDirty_mapRef2RefFlowFunction_15) {
        mapRef2RefFlowFunction_17.inputUpdated(mapRef2RefFlowFunction_15);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_17()) {
      isDirty_mapRef2RefFlowFunction_17 = mapRef2RefFlowFunction_17.map();
      if (isDirty_mapRef2RefFlowFunction_17) {
        binaryMapToRefFlowFunction_34.input2Updated(mapRef2RefFlowFunction_17);
        binaryMapToRefFlowFunction_49.input2Updated(mapRef2RefFlowFunction_17);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_34()) {
      isDirty_binaryMapToRefFlowFunction_34 = binaryMapToRefFlowFunction_34.map();
      if (isDirty_binaryMapToRefFlowFunction_34) {
        mapRef2RefFlowFunction_36.inputUpdated(binaryMapToRefFlowFunction_34);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_49()) {
      isDirty_binaryMapToRefFlowFunction_49 = binaryMapToRefFlowFunction_49.map();
      if (isDirty_binaryMapToRefFlowFunction_49) {
        mapRef2RefFlowFunction_51.inputUpdated(binaryMapToRefFlowFunction_49);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_36()) {
      isDirty_mapRef2RefFlowFunction_36 = mapRef2RefFlowFunction_36.map();
      if (isDirty_mapRef2RefFlowFunction_36) {
        mapRef2RefFlowFunction_37.inputUpdated(mapRef2RefFlowFunction_36);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_37()) {
      isDirty_mapRef2RefFlowFunction_37 = mapRef2RefFlowFunction_37.map();
      if (isDirty_mapRef2RefFlowFunction_37) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_37);
      }
    }
    if (guardCheck_globalNetMtm()) {
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_39.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_51()) {
      isDirty_mapRef2RefFlowFunction_51 = mapRef2RefFlowFunction_51.map();
      if (isDirty_mapRef2RefFlowFunction_51) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_51);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_53.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_39()) {
      isDirty_pushFlowFunction_39 = pushFlowFunction_39.push();
    }
    if (guardCheck_pushFlowFunction_53()) {
      isDirty_pushFlowFunction_53 = pushFlowFunction_53.push();
    }
    afterEvent();
  }

  public void handleEvent(TradeBatch typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_handlerTradeBatch = handlerTradeBatch.onEvent(typedEvent);
    if (isDirty_handlerTradeBatch) {
      flatMapFlowFunction_1.inputUpdatedAndFlatMap(handlerTradeBatch);
    }
    afterEvent();
  }
  //EVENT DISPATCH - END

  //FILTERED DISPATCH - START
  private void handle_Signal_positionSnapshotReset(Signal typedEvent) {
    isDirty_handlerSignal_positionSnapshotReset =
        handlerSignal_positionSnapshotReset.onEvent(typedEvent);
    if (isDirty_handlerSignal_positionSnapshotReset) {
      mapRef2RefFlowFunction_20.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_4.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_6.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_8.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_10.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_13.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
    }
    if (guardCheck_mapRef2RefFlowFunction_20()) {
      isDirty_mapRef2RefFlowFunction_20 = mapRef2RefFlowFunction_20.map();
      if (isDirty_mapRef2RefFlowFunction_20) {
        binaryMapToRefFlowFunction_26.input2Updated(mapRef2RefFlowFunction_20);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_4()) {
      isDirty_mapRef2RefFlowFunction_4 = mapRef2RefFlowFunction_4.map();
      if (isDirty_mapRef2RefFlowFunction_4) {
        binaryMapToRefFlowFunction_22.inputUpdated(mapRef2RefFlowFunction_4);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_6()) {
      isDirty_mapRef2RefFlowFunction_6 = mapRef2RefFlowFunction_6.map();
      if (isDirty_mapRef2RefFlowFunction_6) {
        binaryMapToRefFlowFunction_22.input2Updated(mapRef2RefFlowFunction_6);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_22()) {
      isDirty_binaryMapToRefFlowFunction_22 = binaryMapToRefFlowFunction_22.map();
      if (isDirty_binaryMapToRefFlowFunction_22) {
        mapRef2RefFlowFunction_24.inputUpdated(binaryMapToRefFlowFunction_22);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_8()) {
      isDirty_mapRef2RefFlowFunction_8 = mapRef2RefFlowFunction_8.map();
      if (isDirty_mapRef2RefFlowFunction_8) {
        binaryMapToRefFlowFunction_41.inputUpdated(mapRef2RefFlowFunction_8);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_41.input2Updated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_41()) {
      isDirty_binaryMapToRefFlowFunction_41 = binaryMapToRefFlowFunction_41.map();
      if (isDirty_binaryMapToRefFlowFunction_41) {
        mapRef2RefFlowFunction_43.inputUpdated(binaryMapToRefFlowFunction_41);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_24()) {
      isDirty_mapRef2RefFlowFunction_24 = mapRef2RefFlowFunction_24.map();
      if (isDirty_mapRef2RefFlowFunction_24) {
        binaryMapToRefFlowFunction_26.inputUpdated(mapRef2RefFlowFunction_24);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_26()) {
      isDirty_binaryMapToRefFlowFunction_26 = binaryMapToRefFlowFunction_26.map();
      if (isDirty_binaryMapToRefFlowFunction_26) {
        mapRef2RefFlowFunction_28.inputUpdated(binaryMapToRefFlowFunction_26);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_28()) {
      isDirty_mapRef2RefFlowFunction_28 = mapRef2RefFlowFunction_28.map();
      if (isDirty_mapRef2RefFlowFunction_28) {
        mapRef2RefFlowFunction_30.inputUpdated(mapRef2RefFlowFunction_28);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_30()) {
      isDirty_mapRef2RefFlowFunction_30 = mapRef2RefFlowFunction_30.map();
      if (isDirty_mapRef2RefFlowFunction_30) {
        mapRef2RefFlowFunction_32.inputUpdated(mapRef2RefFlowFunction_30);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_32()) {
      isDirty_mapRef2RefFlowFunction_32 = mapRef2RefFlowFunction_32.map();
      if (isDirty_mapRef2RefFlowFunction_32) {
        binaryMapToRefFlowFunction_34.inputUpdated(mapRef2RefFlowFunction_32);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_43()) {
      isDirty_mapRef2RefFlowFunction_43 = mapRef2RefFlowFunction_43.map();
      if (isDirty_mapRef2RefFlowFunction_43) {
        mapRef2RefFlowFunction_45.inputUpdated(mapRef2RefFlowFunction_43);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_45()) {
      isDirty_mapRef2RefFlowFunction_45 = mapRef2RefFlowFunction_45.map();
      if (isDirty_mapRef2RefFlowFunction_45) {
        mapRef2RefFlowFunction_47.inputUpdated(mapRef2RefFlowFunction_45);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_47()) {
      isDirty_mapRef2RefFlowFunction_47 = mapRef2RefFlowFunction_47.map();
      if (isDirty_mapRef2RefFlowFunction_47) {
        binaryMapToRefFlowFunction_49.inputUpdated(mapRef2RefFlowFunction_47);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_13()) {
      isDirty_mapRef2RefFlowFunction_13 = mapRef2RefFlowFunction_13.map();
      if (isDirty_mapRef2RefFlowFunction_13) {
        mapRef2RefFlowFunction_15.inputUpdated(mapRef2RefFlowFunction_13);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_15()) {
      isDirty_mapRef2RefFlowFunction_15 = mapRef2RefFlowFunction_15.map();
      if (isDirty_mapRef2RefFlowFunction_15) {
        mapRef2RefFlowFunction_17.inputUpdated(mapRef2RefFlowFunction_15);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_17()) {
      isDirty_mapRef2RefFlowFunction_17 = mapRef2RefFlowFunction_17.map();
      if (isDirty_mapRef2RefFlowFunction_17) {
        binaryMapToRefFlowFunction_34.input2Updated(mapRef2RefFlowFunction_17);
        binaryMapToRefFlowFunction_49.input2Updated(mapRef2RefFlowFunction_17);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_34()) {
      isDirty_binaryMapToRefFlowFunction_34 = binaryMapToRefFlowFunction_34.map();
      if (isDirty_binaryMapToRefFlowFunction_34) {
        mapRef2RefFlowFunction_36.inputUpdated(binaryMapToRefFlowFunction_34);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_49()) {
      isDirty_binaryMapToRefFlowFunction_49 = binaryMapToRefFlowFunction_49.map();
      if (isDirty_binaryMapToRefFlowFunction_49) {
        mapRef2RefFlowFunction_51.inputUpdated(binaryMapToRefFlowFunction_49);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_36()) {
      isDirty_mapRef2RefFlowFunction_36 = mapRef2RefFlowFunction_36.map();
      if (isDirty_mapRef2RefFlowFunction_36) {
        mapRef2RefFlowFunction_37.inputUpdated(mapRef2RefFlowFunction_36);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_37()) {
      isDirty_mapRef2RefFlowFunction_37 = mapRef2RefFlowFunction_37.map();
      if (isDirty_mapRef2RefFlowFunction_37) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_37);
      }
    }
    if (guardCheck_globalNetMtm()) {
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_39.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_51()) {
      isDirty_mapRef2RefFlowFunction_51 = mapRef2RefFlowFunction_51.map();
      if (isDirty_mapRef2RefFlowFunction_51) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_51);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_53.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_39()) {
      isDirty_pushFlowFunction_39 = pushFlowFunction_39.push();
    }
    if (guardCheck_pushFlowFunction_53()) {
      isDirty_pushFlowFunction_53 = pushFlowFunction_53.push();
    }
  }

  private void handle_Signal_positionUpdate(Signal typedEvent) {
    isDirty_handlerSignal_positionUpdate = handlerSignal_positionUpdate.onEvent(typedEvent);
    if (isDirty_handlerSignal_positionUpdate) {
      mapRef2RefFlowFunction_20.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_30.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_32.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_45.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_47.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_17.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_17.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_36.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_51.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
    }
    if (guardCheck_mapRef2RefFlowFunction_20()) {
      isDirty_mapRef2RefFlowFunction_20 = mapRef2RefFlowFunction_20.map();
      if (isDirty_mapRef2RefFlowFunction_20) {
        binaryMapToRefFlowFunction_26.input2Updated(mapRef2RefFlowFunction_20);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_26()) {
      isDirty_binaryMapToRefFlowFunction_26 = binaryMapToRefFlowFunction_26.map();
      if (isDirty_binaryMapToRefFlowFunction_26) {
        mapRef2RefFlowFunction_28.inputUpdated(binaryMapToRefFlowFunction_26);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_28()) {
      isDirty_mapRef2RefFlowFunction_28 = mapRef2RefFlowFunction_28.map();
      if (isDirty_mapRef2RefFlowFunction_28) {
        mapRef2RefFlowFunction_30.inputUpdated(mapRef2RefFlowFunction_28);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_30()) {
      isDirty_mapRef2RefFlowFunction_30 = mapRef2RefFlowFunction_30.map();
      if (isDirty_mapRef2RefFlowFunction_30) {
        mapRef2RefFlowFunction_32.inputUpdated(mapRef2RefFlowFunction_30);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_32()) {
      isDirty_mapRef2RefFlowFunction_32 = mapRef2RefFlowFunction_32.map();
      if (isDirty_mapRef2RefFlowFunction_32) {
        binaryMapToRefFlowFunction_34.inputUpdated(mapRef2RefFlowFunction_32);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_45()) {
      isDirty_mapRef2RefFlowFunction_45 = mapRef2RefFlowFunction_45.map();
      if (isDirty_mapRef2RefFlowFunction_45) {
        mapRef2RefFlowFunction_47.inputUpdated(mapRef2RefFlowFunction_45);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_47()) {
      isDirty_mapRef2RefFlowFunction_47 = mapRef2RefFlowFunction_47.map();
      if (isDirty_mapRef2RefFlowFunction_47) {
        binaryMapToRefFlowFunction_49.inputUpdated(mapRef2RefFlowFunction_47);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_17()) {
      isDirty_mapRef2RefFlowFunction_17 = mapRef2RefFlowFunction_17.map();
      if (isDirty_mapRef2RefFlowFunction_17) {
        binaryMapToRefFlowFunction_34.input2Updated(mapRef2RefFlowFunction_17);
        binaryMapToRefFlowFunction_49.input2Updated(mapRef2RefFlowFunction_17);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_34()) {
      isDirty_binaryMapToRefFlowFunction_34 = binaryMapToRefFlowFunction_34.map();
      if (isDirty_binaryMapToRefFlowFunction_34) {
        mapRef2RefFlowFunction_36.inputUpdated(binaryMapToRefFlowFunction_34);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_49()) {
      isDirty_binaryMapToRefFlowFunction_49 = binaryMapToRefFlowFunction_49.map();
      if (isDirty_binaryMapToRefFlowFunction_49) {
        mapRef2RefFlowFunction_51.inputUpdated(binaryMapToRefFlowFunction_49);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_36()) {
      isDirty_mapRef2RefFlowFunction_36 = mapRef2RefFlowFunction_36.map();
      if (isDirty_mapRef2RefFlowFunction_36) {
        mapRef2RefFlowFunction_37.inputUpdated(mapRef2RefFlowFunction_36);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_37()) {
      isDirty_mapRef2RefFlowFunction_37 = mapRef2RefFlowFunction_37.map();
      if (isDirty_mapRef2RefFlowFunction_37) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_37);
      }
    }
    if (guardCheck_globalNetMtm()) {
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_39.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_51()) {
      isDirty_mapRef2RefFlowFunction_51 = mapRef2RefFlowFunction_51.map();
      if (isDirty_mapRef2RefFlowFunction_51) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_51);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_53.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_39()) {
      isDirty_pushFlowFunction_39 = pushFlowFunction_39.push();
    }
    if (guardCheck_pushFlowFunction_53()) {
      isDirty_pushFlowFunction_53 = pushFlowFunction_53.push();
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
    auditor.nodeRegistered(callBackNode_54, "callBackNode_54");
    auditor.nodeRegistered(callBackNode_68, "callBackNode_68");
    auditor.nodeRegistered(callbackDispatcher, "callbackDispatcher");
    auditor.nodeRegistered(callBackTriggerEvent_1, "callBackTriggerEvent_1");
    auditor.nodeRegistered(callBackTriggerEvent_0, "callBackTriggerEvent_0");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_22, "binaryMapToRefFlowFunction_22");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_26, "binaryMapToRefFlowFunction_26");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_34, "binaryMapToRefFlowFunction_34");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_41, "binaryMapToRefFlowFunction_41");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_49, "binaryMapToRefFlowFunction_49");
    auditor.nodeRegistered(flatMapFlowFunction_1, "flatMapFlowFunction_1");
    auditor.nodeRegistered(flatMapFlowFunction_18, "flatMapFlowFunction_18");
    auditor.nodeRegistered(globalNetMtm, "globalNetMtm");
    auditor.nodeRegistered(instrumentNetMtm, "instrumentNetMtm");
    auditor.nodeRegistered(mapRef2RefFlowFunction_4, "mapRef2RefFlowFunction_4");
    auditor.nodeRegistered(mapRef2RefFlowFunction_6, "mapRef2RefFlowFunction_6");
    auditor.nodeRegistered(mapRef2RefFlowFunction_8, "mapRef2RefFlowFunction_8");
    auditor.nodeRegistered(mapRef2RefFlowFunction_10, "mapRef2RefFlowFunction_10");
    auditor.nodeRegistered(mapRef2RefFlowFunction_13, "mapRef2RefFlowFunction_13");
    auditor.nodeRegistered(mapRef2RefFlowFunction_15, "mapRef2RefFlowFunction_15");
    auditor.nodeRegistered(mapRef2RefFlowFunction_17, "mapRef2RefFlowFunction_17");
    auditor.nodeRegistered(mapRef2RefFlowFunction_20, "mapRef2RefFlowFunction_20");
    auditor.nodeRegistered(mapRef2RefFlowFunction_24, "mapRef2RefFlowFunction_24");
    auditor.nodeRegistered(mapRef2RefFlowFunction_28, "mapRef2RefFlowFunction_28");
    auditor.nodeRegistered(mapRef2RefFlowFunction_30, "mapRef2RefFlowFunction_30");
    auditor.nodeRegistered(mapRef2RefFlowFunction_32, "mapRef2RefFlowFunction_32");
    auditor.nodeRegistered(mapRef2RefFlowFunction_36, "mapRef2RefFlowFunction_36");
    auditor.nodeRegistered(mapRef2RefFlowFunction_37, "mapRef2RefFlowFunction_37");
    auditor.nodeRegistered(mapRef2RefFlowFunction_43, "mapRef2RefFlowFunction_43");
    auditor.nodeRegistered(mapRef2RefFlowFunction_45, "mapRef2RefFlowFunction_45");
    auditor.nodeRegistered(mapRef2RefFlowFunction_47, "mapRef2RefFlowFunction_47");
    auditor.nodeRegistered(mapRef2RefFlowFunction_51, "mapRef2RefFlowFunction_51");
    auditor.nodeRegistered(mergeFlowFunction_2, "mergeFlowFunction_2");
    auditor.nodeRegistered(mergeFlowFunction_11, "mergeFlowFunction_11");
    auditor.nodeRegistered(pushFlowFunction_39, "pushFlowFunction_39");
    auditor.nodeRegistered(pushFlowFunction_53, "pushFlowFunction_53");
    auditor.nodeRegistered(emptyGroupBy_60, "emptyGroupBy_60");
    auditor.nodeRegistered(emptyGroupBy_108, "emptyGroupBy_108");
    auditor.nodeRegistered(emptyGroupBy_232, "emptyGroupBy_232");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_3, "groupByFlowFunctionWrapper_3");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_5, "groupByFlowFunctionWrapper_5");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_7, "groupByFlowFunctionWrapper_7");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_9, "groupByFlowFunctionWrapper_9");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_12, "groupByFlowFunctionWrapper_12");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_19, "groupByFlowFunctionWrapper_19");
    auditor.nodeRegistered(groupByMapFlowFunction_16, "groupByMapFlowFunction_16");
    auditor.nodeRegistered(groupByMapFlowFunction_23, "groupByMapFlowFunction_23");
    auditor.nodeRegistered(groupByMapFlowFunction_27, "groupByMapFlowFunction_27");
    auditor.nodeRegistered(groupByMapFlowFunction_29, "groupByMapFlowFunction_29");
    auditor.nodeRegistered(groupByMapFlowFunction_35, "groupByMapFlowFunction_35");
    auditor.nodeRegistered(groupByMapFlowFunction_42, "groupByMapFlowFunction_42");
    auditor.nodeRegistered(groupByMapFlowFunction_44, "groupByMapFlowFunction_44");
    auditor.nodeRegistered(groupByMapFlowFunction_50, "groupByMapFlowFunction_50");
    auditor.nodeRegistered(leftJoin_33, "leftJoin_33");
    auditor.nodeRegistered(leftJoin_48, "leftJoin_48");
    auditor.nodeRegistered(outerJoin_21, "outerJoin_21");
    auditor.nodeRegistered(outerJoin_25, "outerJoin_25");
    auditor.nodeRegistered(outerJoin_40, "outerJoin_40");
    auditor.nodeRegistered(defaultValue_14, "defaultValue_14");
    auditor.nodeRegistered(defaultValue_31, "defaultValue_31");
    auditor.nodeRegistered(defaultValue_46, "defaultValue_46");
    auditor.nodeRegistered(mapTuple_197, "mapTuple_197");
    auditor.nodeRegistered(mapTuple_207, "mapTuple_207");
    auditor.nodeRegistered(mapTuple_211, "mapTuple_211");
    auditor.nodeRegistered(mapTuple_286, "mapTuple_286");
    auditor.nodeRegistered(mapTuple_296, "mapTuple_296");
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
    isDirty_binaryMapToRefFlowFunction_22 = false;
    isDirty_binaryMapToRefFlowFunction_26 = false;
    isDirty_binaryMapToRefFlowFunction_34 = false;
    isDirty_binaryMapToRefFlowFunction_41 = false;
    isDirty_binaryMapToRefFlowFunction_49 = false;
    isDirty_callBackNode_54 = false;
    isDirty_callBackNode_68 = false;
    isDirty_clock = false;
    isDirty_derivedRateNode = false;
    isDirty_flatMapFlowFunction_1 = false;
    isDirty_flatMapFlowFunction_18 = false;
    isDirty_globalNetMtm = false;
    isDirty_handlerPositionSnapshot = false;
    isDirty_handlerSignal_positionSnapshotReset = false;
    isDirty_handlerSignal_positionUpdate = false;
    isDirty_handlerTrade = false;
    isDirty_handlerTradeBatch = false;
    isDirty_instrumentNetMtm = false;
    isDirty_mapRef2RefFlowFunction_4 = false;
    isDirty_mapRef2RefFlowFunction_6 = false;
    isDirty_mapRef2RefFlowFunction_8 = false;
    isDirty_mapRef2RefFlowFunction_10 = false;
    isDirty_mapRef2RefFlowFunction_13 = false;
    isDirty_mapRef2RefFlowFunction_15 = false;
    isDirty_mapRef2RefFlowFunction_17 = false;
    isDirty_mapRef2RefFlowFunction_20 = false;
    isDirty_mapRef2RefFlowFunction_24 = false;
    isDirty_mapRef2RefFlowFunction_28 = false;
    isDirty_mapRef2RefFlowFunction_30 = false;
    isDirty_mapRef2RefFlowFunction_32 = false;
    isDirty_mapRef2RefFlowFunction_36 = false;
    isDirty_mapRef2RefFlowFunction_37 = false;
    isDirty_mapRef2RefFlowFunction_43 = false;
    isDirty_mapRef2RefFlowFunction_45 = false;
    isDirty_mapRef2RefFlowFunction_47 = false;
    isDirty_mapRef2RefFlowFunction_51 = false;
    isDirty_mergeFlowFunction_2 = false;
    isDirty_mergeFlowFunction_11 = false;
    isDirty_pushFlowFunction_39 = false;
    isDirty_pushFlowFunction_53 = false;
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
          binaryMapToRefFlowFunction_22, () -> isDirty_binaryMapToRefFlowFunction_22);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_26, () -> isDirty_binaryMapToRefFlowFunction_26);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_34, () -> isDirty_binaryMapToRefFlowFunction_34);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_41, () -> isDirty_binaryMapToRefFlowFunction_41);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_49, () -> isDirty_binaryMapToRefFlowFunction_49);
      dirtyFlagSupplierMap.put(callBackNode_54, () -> isDirty_callBackNode_54);
      dirtyFlagSupplierMap.put(callBackNode_68, () -> isDirty_callBackNode_68);
      dirtyFlagSupplierMap.put(clock, () -> isDirty_clock);
      dirtyFlagSupplierMap.put(derivedRateNode, () -> isDirty_derivedRateNode);
      dirtyFlagSupplierMap.put(flatMapFlowFunction_1, () -> isDirty_flatMapFlowFunction_1);
      dirtyFlagSupplierMap.put(flatMapFlowFunction_18, () -> isDirty_flatMapFlowFunction_18);
      dirtyFlagSupplierMap.put(globalNetMtm, () -> isDirty_globalNetMtm);
      dirtyFlagSupplierMap.put(handlerPositionSnapshot, () -> isDirty_handlerPositionSnapshot);
      dirtyFlagSupplierMap.put(
          handlerSignal_positionSnapshotReset, () -> isDirty_handlerSignal_positionSnapshotReset);
      dirtyFlagSupplierMap.put(
          handlerSignal_positionUpdate, () -> isDirty_handlerSignal_positionUpdate);
      dirtyFlagSupplierMap.put(handlerTrade, () -> isDirty_handlerTrade);
      dirtyFlagSupplierMap.put(handlerTradeBatch, () -> isDirty_handlerTradeBatch);
      dirtyFlagSupplierMap.put(instrumentNetMtm, () -> isDirty_instrumentNetMtm);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_10, () -> isDirty_mapRef2RefFlowFunction_10);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_13, () -> isDirty_mapRef2RefFlowFunction_13);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_15, () -> isDirty_mapRef2RefFlowFunction_15);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_17, () -> isDirty_mapRef2RefFlowFunction_17);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_20, () -> isDirty_mapRef2RefFlowFunction_20);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_24, () -> isDirty_mapRef2RefFlowFunction_24);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_28, () -> isDirty_mapRef2RefFlowFunction_28);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_30, () -> isDirty_mapRef2RefFlowFunction_30);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_32, () -> isDirty_mapRef2RefFlowFunction_32);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_36, () -> isDirty_mapRef2RefFlowFunction_36);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_37, () -> isDirty_mapRef2RefFlowFunction_37);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_4, () -> isDirty_mapRef2RefFlowFunction_4);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_43, () -> isDirty_mapRef2RefFlowFunction_43);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_45, () -> isDirty_mapRef2RefFlowFunction_45);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_47, () -> isDirty_mapRef2RefFlowFunction_47);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_51, () -> isDirty_mapRef2RefFlowFunction_51);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_6, () -> isDirty_mapRef2RefFlowFunction_6);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_8, () -> isDirty_mapRef2RefFlowFunction_8);
      dirtyFlagSupplierMap.put(mergeFlowFunction_11, () -> isDirty_mergeFlowFunction_11);
      dirtyFlagSupplierMap.put(mergeFlowFunction_2, () -> isDirty_mergeFlowFunction_2);
      dirtyFlagSupplierMap.put(pushFlowFunction_39, () -> isDirty_pushFlowFunction_39);
      dirtyFlagSupplierMap.put(pushFlowFunction_53, () -> isDirty_pushFlowFunction_53);
    }
    return dirtyFlagSupplierMap.getOrDefault(node, StaticEventProcessor.ALWAYS_FALSE);
  }

  @Override
  public void setDirty(Object node, boolean dirtyFlag) {
    if (dirtyFlagUpdateMap.isEmpty()) {
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_22, (b) -> isDirty_binaryMapToRefFlowFunction_22 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_26, (b) -> isDirty_binaryMapToRefFlowFunction_26 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_34, (b) -> isDirty_binaryMapToRefFlowFunction_34 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_41, (b) -> isDirty_binaryMapToRefFlowFunction_41 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_49, (b) -> isDirty_binaryMapToRefFlowFunction_49 = b);
      dirtyFlagUpdateMap.put(callBackNode_54, (b) -> isDirty_callBackNode_54 = b);
      dirtyFlagUpdateMap.put(callBackNode_68, (b) -> isDirty_callBackNode_68 = b);
      dirtyFlagUpdateMap.put(clock, (b) -> isDirty_clock = b);
      dirtyFlagUpdateMap.put(derivedRateNode, (b) -> isDirty_derivedRateNode = b);
      dirtyFlagUpdateMap.put(flatMapFlowFunction_1, (b) -> isDirty_flatMapFlowFunction_1 = b);
      dirtyFlagUpdateMap.put(flatMapFlowFunction_18, (b) -> isDirty_flatMapFlowFunction_18 = b);
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
          mapRef2RefFlowFunction_10, (b) -> isDirty_mapRef2RefFlowFunction_10 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_13, (b) -> isDirty_mapRef2RefFlowFunction_13 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_15, (b) -> isDirty_mapRef2RefFlowFunction_15 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_17, (b) -> isDirty_mapRef2RefFlowFunction_17 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_20, (b) -> isDirty_mapRef2RefFlowFunction_20 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_24, (b) -> isDirty_mapRef2RefFlowFunction_24 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_28, (b) -> isDirty_mapRef2RefFlowFunction_28 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_30, (b) -> isDirty_mapRef2RefFlowFunction_30 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_32, (b) -> isDirty_mapRef2RefFlowFunction_32 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_36, (b) -> isDirty_mapRef2RefFlowFunction_36 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_37, (b) -> isDirty_mapRef2RefFlowFunction_37 = b);
      dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_4, (b) -> isDirty_mapRef2RefFlowFunction_4 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_43, (b) -> isDirty_mapRef2RefFlowFunction_43 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_45, (b) -> isDirty_mapRef2RefFlowFunction_45 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_47, (b) -> isDirty_mapRef2RefFlowFunction_47 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_51, (b) -> isDirty_mapRef2RefFlowFunction_51 = b);
      dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_6, (b) -> isDirty_mapRef2RefFlowFunction_6 = b);
      dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_8, (b) -> isDirty_mapRef2RefFlowFunction_8 = b);
      dirtyFlagUpdateMap.put(mergeFlowFunction_11, (b) -> isDirty_mergeFlowFunction_11 = b);
      dirtyFlagUpdateMap.put(mergeFlowFunction_2, (b) -> isDirty_mergeFlowFunction_2 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_39, (b) -> isDirty_pushFlowFunction_39 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_53, (b) -> isDirty_pushFlowFunction_53 = b);
    }
    dirtyFlagUpdateMap.get(node).accept(dirtyFlag);
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_22() {
    return isDirty_mapRef2RefFlowFunction_4 | isDirty_mapRef2RefFlowFunction_6;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_26() {
    return isDirty_mapRef2RefFlowFunction_20 | isDirty_mapRef2RefFlowFunction_24;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_34() {
    return isDirty_mapRef2RefFlowFunction_17 | isDirty_mapRef2RefFlowFunction_32;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_41() {
    return isDirty_mapRef2RefFlowFunction_8 | isDirty_mapRef2RefFlowFunction_10;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_49() {
    return isDirty_mapRef2RefFlowFunction_17 | isDirty_mapRef2RefFlowFunction_47;
  }

  private boolean guardCheck_flatMapFlowFunction_1() {
    return isDirty_callBackNode_54;
  }

  private boolean guardCheck_flatMapFlowFunction_18() {
    return isDirty_callBackNode_68;
  }

  private boolean guardCheck_globalNetMtm() {
    return isDirty_mapRef2RefFlowFunction_37;
  }

  private boolean guardCheck_instrumentNetMtm() {
    return isDirty_mapRef2RefFlowFunction_51;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_4() {
    return isDirty_handlerSignal_positionSnapshotReset | isDirty_mergeFlowFunction_2;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_6() {
    return isDirty_handlerSignal_positionSnapshotReset | isDirty_mergeFlowFunction_2;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_8() {
    return isDirty_handlerSignal_positionSnapshotReset | isDirty_mergeFlowFunction_2;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_10() {
    return isDirty_handlerSignal_positionSnapshotReset | isDirty_mergeFlowFunction_2;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_13() {
    return isDirty_handlerSignal_positionSnapshotReset | isDirty_mergeFlowFunction_11;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_15() {
    return isDirty_mapRef2RefFlowFunction_13;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_17() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_15;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_20() {
    return isDirty_flatMapFlowFunction_18
        | isDirty_handlerSignal_positionSnapshotReset
        | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_24() {
    return isDirty_binaryMapToRefFlowFunction_22;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_28() {
    return isDirty_binaryMapToRefFlowFunction_26;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_30() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_28;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_32() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_30;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_36() {
    return isDirty_binaryMapToRefFlowFunction_34 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_37() {
    return isDirty_mapRef2RefFlowFunction_36;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_43() {
    return isDirty_binaryMapToRefFlowFunction_41;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_45() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_43;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_47() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_45;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_51() {
    return isDirty_binaryMapToRefFlowFunction_49 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mergeFlowFunction_2() {
    return isDirty_flatMapFlowFunction_1 | isDirty_handlerTrade;
  }

  private boolean guardCheck_mergeFlowFunction_11() {
    return isDirty_flatMapFlowFunction_1 | isDirty_handlerTrade;
  }

  private boolean guardCheck_pushFlowFunction_39() {
    return isDirty_globalNetMtm;
  }

  private boolean guardCheck_pushFlowFunction_53() {
    return isDirty_instrumentNetMtm;
  }

  private boolean guardCheck_groupByMapFlowFunction_16() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_groupByMapFlowFunction_29() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_groupByMapFlowFunction_44() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_context() {
    return isDirty_clock;
  }

  private boolean guardCheck_globalNetMtmListener() {
    return isDirty_pushFlowFunction_39;
  }

  private boolean guardCheck_instrumentNetMtmListener() {
    return isDirty_pushFlowFunction_53;
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
