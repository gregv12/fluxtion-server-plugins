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
import com.fluxtion.runtime.callback.CallbackDispatcherImpl;
import com.fluxtion.runtime.callback.CallbackEvent;
import com.fluxtion.runtime.callback.CallbackImpl;
import com.fluxtion.runtime.callback.ExportFunctionAuditEvent;
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
 * eventProcessorGenerator version : 9.3.44
 * api version                     : 9.3.44
 * </pre>
 *
 * Event classes supported:
 *
 * <ul>
 *   <li>com.fluxtion.compiler.generation.model.ExportFunctionMarker
 *   <li>com.fluxtion.runtime.callback.CallbackEvent
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
  private final CallbackDispatcherImpl callbackDispatcher = new CallbackDispatcherImpl();
  private final CallbackImpl callbackImpl_52 = new CallbackImpl<>(1, callbackDispatcher);
  private final CallbackImpl callbackImpl_66 = new CallbackImpl<>(2, callbackDispatcher);
  public final Clock clock = new Clock();
  private final DerivedRateNode derivedRateNode_292 = new DerivedRateNode();
  private final EmptyGroupBy emptyGroupBy_58 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_12 = new DefaultValue<>(emptyGroupBy_58);
  private final EmptyGroupBy emptyGroupBy_106 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_29 = new DefaultValue<>(emptyGroupBy_106);
  private final EmptyGroupBy emptyGroupBy_230 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_44 = new DefaultValue<>(emptyGroupBy_230);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_2 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, InstrumentPosMtmAggregate::dealt);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_4 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, InstrumentPosMtmAggregate::contra);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_6 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, SingleInstrumentPosMtmAggregate::dealt);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_8 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, SingleInstrumentPosMtmAggregate::contra);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_10 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, FeeInstrumentPosMtmAggregate::new);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_17 =
      new GroupByFlowFunctionWrapper<>(
          InstrumentPosition::instrument, Mappers::identity, AggregateIdentityFlowFunction::new);
  private final GroupByMapFlowFunction groupByMapFlowFunction_14 =
      new GroupByMapFlowFunction(derivedRateNode_292::calculateFeeMtm);
  private final GroupByMapFlowFunction groupByMapFlowFunction_23 =
      new GroupByMapFlowFunction(derivedRateNode_292::calculateInstrumentPosMtm);
  private final GroupByMapFlowFunction groupByMapFlowFunction_42 =
      new GroupByMapFlowFunction(derivedRateNode_292::calculateInstrumentPosMtm);
  private final LeftJoin leftJoin_31 = new LeftJoin();
  private final LeftJoin leftJoin_46 = new LeftJoin();
  private final MapTuple mapTuple_195 = new MapTuple<>(NetMarkToMarket::combine);
  private final GroupByMapFlowFunction groupByMapFlowFunction_33 =
      new GroupByMapFlowFunction(mapTuple_195::mapTuple);
  private final MapTuple mapTuple_203 = new MapTuple<>(InstrumentPosMtm::addInstrumentPosition);
  private final GroupByMapFlowFunction groupByMapFlowFunction_27 =
      new GroupByMapFlowFunction(mapTuple_203::mapTuple);
  private final MapTuple mapTuple_209 = new MapTuple<>(InstrumentPosMtm::merge);
  private final GroupByMapFlowFunction groupByMapFlowFunction_21 =
      new GroupByMapFlowFunction(mapTuple_209::mapTuple);
  private final MapTuple mapTuple_284 = new MapTuple<>(NetMarkToMarket::combine);
  private final GroupByMapFlowFunction groupByMapFlowFunction_48 =
      new GroupByMapFlowFunction(mapTuple_284::mapTuple);
  private final MapTuple mapTuple_294 = new MapTuple<>(InstrumentPosMtm::merge);
  private final GroupByMapFlowFunction groupByMapFlowFunction_40 =
      new GroupByMapFlowFunction(mapTuple_294::mapTuple);
  public final NodeNameAuditor nodeNameLookup = new NodeNameAuditor();
  private final OuterJoin outerJoin_19 = new OuterJoin();
  private final OuterJoin outerJoin_25 = new OuterJoin();
  private final OuterJoin outerJoin_38 = new OuterJoin();
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
  private final FlatMapFlowFunction flatMapFlowFunction_16 =
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
  private final FlatMapFlowFunction flatMapFlowFunction_0 =
      new FlatMapFlowFunction<>(handlerTradeBatch, TradeBatch::getTrades);
  private final SinkPublisher instrumentNetMtmListener =
      new SinkPublisher<>("instrumentNetMtmListener");
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_11 =
      new MapRef2RefFlowFunction<>(handlerTrade, groupByFlowFunctionWrapper_10::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_13 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_11, defaultValue_12::getOrDefault);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_15 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_13, groupByMapFlowFunction_14::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_18 =
      new MapRef2RefFlowFunction<>(
          flatMapFlowFunction_16, groupByFlowFunctionWrapper_17::aggregate);
  private final MergeFlowFunction mergeFlowFunction_1 =
      new MergeFlowFunction<>(Arrays.asList(handlerTrade, flatMapFlowFunction_0));
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_3 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_1, groupByFlowFunctionWrapper_2::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_5 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_1, groupByFlowFunctionWrapper_4::aggregate);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_20 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_3, mapRef2RefFlowFunction_5, outerJoin_19::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_7 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_1, groupByFlowFunctionWrapper_6::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_9 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_1, groupByFlowFunctionWrapper_8::aggregate);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_39 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_7, mapRef2RefFlowFunction_9, outerJoin_38::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_22 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_20, groupByMapFlowFunction_21::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_24 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_22, groupByMapFlowFunction_23::mapValues);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_26 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_24, mapRef2RefFlowFunction_18, outerJoin_25::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_28 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_26, groupByMapFlowFunction_27::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_30 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_28, defaultValue_29::getOrDefault);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_32 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_30, mapRef2RefFlowFunction_15, leftJoin_31::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_34 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_32, groupByMapFlowFunction_33::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_35 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_34, GroupBy<Object, Object>::toMap);
  public final MapRef2RefFlowFunction globalNetMtm =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_35, MtmCalc::markToMarketSum);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_41 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_39, groupByMapFlowFunction_40::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_43 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_41, groupByMapFlowFunction_42::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_45 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_43, defaultValue_44::getOrDefault);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_47 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_45, mapRef2RefFlowFunction_15, leftJoin_46::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_49 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_47, groupByMapFlowFunction_48::mapValues);
  public final MapRef2RefFlowFunction instrumentNetMtm =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_49, GroupBy<Object, Object>::toMap);
  private final PushFlowFunction pushFlowFunction_37 =
      new PushFlowFunction<>(globalNetMtm, globalNetMtmListener::publish);
  private final PushFlowFunction pushFlowFunction_51 =
      new PushFlowFunction<>(instrumentNetMtm, instrumentNetMtmListener::publish);
  public final ServiceRegistryNode serviceRegistry = new ServiceRegistryNode();
  private final ExportFunctionAuditEvent functionAudit = new ExportFunctionAuditEvent();
  //Dirty flags
  private boolean initCalled = false;
  private boolean processing = false;
  private boolean buffering = false;
  private final IdentityHashMap<Object, BooleanSupplier> dirtyFlagSupplierMap =
      new IdentityHashMap<>(39);
  private final IdentityHashMap<Object, Consumer<Boolean>> dirtyFlagUpdateMap =
      new IdentityHashMap<>(39);

  private boolean isDirty_binaryMapToRefFlowFunction_20 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_26 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_32 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_39 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_47 = false;
  private boolean isDirty_callbackImpl_52 = false;
  private boolean isDirty_callbackImpl_66 = false;
  private boolean isDirty_clock = false;
  private boolean isDirty_derivedRateNode_292 = false;
  private boolean isDirty_flatMapFlowFunction_0 = false;
  private boolean isDirty_flatMapFlowFunction_16 = false;
  private boolean isDirty_globalNetMtm = false;
  private boolean isDirty_handlerPositionSnapshot = false;
  private boolean isDirty_handlerSignal_positionSnapshotReset = false;
  private boolean isDirty_handlerSignal_positionUpdate = false;
  private boolean isDirty_handlerTrade = false;
  private boolean isDirty_handlerTradeBatch = false;
  private boolean isDirty_instrumentNetMtm = false;
  private boolean isDirty_mapRef2RefFlowFunction_3 = false;
  private boolean isDirty_mapRef2RefFlowFunction_5 = false;
  private boolean isDirty_mapRef2RefFlowFunction_7 = false;
  private boolean isDirty_mapRef2RefFlowFunction_9 = false;
  private boolean isDirty_mapRef2RefFlowFunction_11 = false;
  private boolean isDirty_mapRef2RefFlowFunction_13 = false;
  private boolean isDirty_mapRef2RefFlowFunction_15 = false;
  private boolean isDirty_mapRef2RefFlowFunction_18 = false;
  private boolean isDirty_mapRef2RefFlowFunction_22 = false;
  private boolean isDirty_mapRef2RefFlowFunction_24 = false;
  private boolean isDirty_mapRef2RefFlowFunction_28 = false;
  private boolean isDirty_mapRef2RefFlowFunction_30 = false;
  private boolean isDirty_mapRef2RefFlowFunction_34 = false;
  private boolean isDirty_mapRef2RefFlowFunction_35 = false;
  private boolean isDirty_mapRef2RefFlowFunction_41 = false;
  private boolean isDirty_mapRef2RefFlowFunction_43 = false;
  private boolean isDirty_mapRef2RefFlowFunction_45 = false;
  private boolean isDirty_mapRef2RefFlowFunction_49 = false;
  private boolean isDirty_mergeFlowFunction_1 = false;
  private boolean isDirty_pushFlowFunction_37 = false;
  private boolean isDirty_pushFlowFunction_51 = false;

  //Forked declarations

  //Filter constants

  //unknown event handler
  private Consumer unKnownEventHandler = (e) -> {};

  public FluxtionPnlCalculator(Map<Object, Object> contextMap) {
    if (context != null) {
      context.replaceMappings(contextMap);
    }
    callbackImpl_52.dirtyStateMonitor = callbackDispatcher;
    callbackImpl_66.dirtyStateMonitor = callbackDispatcher;
    binaryMapToRefFlowFunction_20.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_26.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_32.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_39.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_47.setEventProcessorContext(context);
    flatMapFlowFunction_0.callback = callbackImpl_52;
    flatMapFlowFunction_0.dirtyStateMonitor = callbackDispatcher;
    flatMapFlowFunction_16.callback = callbackImpl_66;
    flatMapFlowFunction_16.dirtyStateMonitor = callbackDispatcher;
    globalNetMtm.setEventProcessorContext(context);
    instrumentNetMtm.setEventProcessorContext(context);
    mapRef2RefFlowFunction_3.setEventProcessorContext(context);
    mapRef2RefFlowFunction_3.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_5.setEventProcessorContext(context);
    mapRef2RefFlowFunction_5.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_7.setEventProcessorContext(context);
    mapRef2RefFlowFunction_7.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_9.setEventProcessorContext(context);
    mapRef2RefFlowFunction_9.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_11.setEventProcessorContext(context);
    mapRef2RefFlowFunction_11.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_13.setEventProcessorContext(context);
    mapRef2RefFlowFunction_15.setEventProcessorContext(context);
    mapRef2RefFlowFunction_15.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_15.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_18.setEventProcessorContext(context);
    mapRef2RefFlowFunction_18.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_18.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_22.setEventProcessorContext(context);
    mapRef2RefFlowFunction_24.setEventProcessorContext(context);
    mapRef2RefFlowFunction_24.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_28.setEventProcessorContext(context);
    mapRef2RefFlowFunction_30.setEventProcessorContext(context);
    mapRef2RefFlowFunction_30.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_34.setEventProcessorContext(context);
    mapRef2RefFlowFunction_34.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_35.setEventProcessorContext(context);
    mapRef2RefFlowFunction_41.setEventProcessorContext(context);
    mapRef2RefFlowFunction_43.setEventProcessorContext(context);
    mapRef2RefFlowFunction_45.setEventProcessorContext(context);
    mapRef2RefFlowFunction_45.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_45.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_49.setEventProcessorContext(context);
    mapRef2RefFlowFunction_49.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mergeFlowFunction_1.dirtyStateMonitor = callbackDispatcher;
    pushFlowFunction_37.setEventProcessorContext(context);
    pushFlowFunction_51.setEventProcessorContext(context);
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
    callbackImpl_52.init();
    callbackImpl_66.init();
    clock.init();
    handlerPositionSnapshot.init();
    handlerSignal_positionSnapshotReset.init();
    handlerSignal_positionUpdate.init();
    handlerTrade.init();
    handlerTradeBatch.init();
    mapRef2RefFlowFunction_11.initialiseEventStream();
    mapRef2RefFlowFunction_13.initialiseEventStream();
    mapRef2RefFlowFunction_15.initialiseEventStream();
    mapRef2RefFlowFunction_18.initialiseEventStream();
    mapRef2RefFlowFunction_3.initialiseEventStream();
    mapRef2RefFlowFunction_5.initialiseEventStream();
    binaryMapToRefFlowFunction_20.initialiseEventStream();
    mapRef2RefFlowFunction_7.initialiseEventStream();
    mapRef2RefFlowFunction_9.initialiseEventStream();
    binaryMapToRefFlowFunction_39.initialiseEventStream();
    mapRef2RefFlowFunction_22.initialiseEventStream();
    mapRef2RefFlowFunction_24.initialiseEventStream();
    binaryMapToRefFlowFunction_26.initialiseEventStream();
    mapRef2RefFlowFunction_28.initialiseEventStream();
    mapRef2RefFlowFunction_30.initialiseEventStream();
    binaryMapToRefFlowFunction_32.initialiseEventStream();
    mapRef2RefFlowFunction_34.initialiseEventStream();
    mapRef2RefFlowFunction_35.initialiseEventStream();
    globalNetMtm.initialiseEventStream();
    mapRef2RefFlowFunction_41.initialiseEventStream();
    mapRef2RefFlowFunction_43.initialiseEventStream();
    mapRef2RefFlowFunction_45.initialiseEventStream();
    binaryMapToRefFlowFunction_47.initialiseEventStream();
    mapRef2RefFlowFunction_49.initialiseEventStream();
    instrumentNetMtm.initialiseEventStream();
    pushFlowFunction_37.initialiseEventStream();
    pushFlowFunction_51.initialiseEventStream();
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
    if (event instanceof com.fluxtion.runtime.callback.CallbackEvent) {
      CallbackEvent typedEvent = (CallbackEvent) event;
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

  public void handleEvent(CallbackEvent typedEvent) {
    auditEvent(typedEvent);
    switch (typedEvent.filterId()) {
        //Event Class:[com.fluxtion.runtime.callback.CallbackEvent] filterId:[1]
      case (1):
        isDirty_callbackImpl_52 = callbackImpl_52.onEvent(typedEvent);
        if (guardCheck_flatMapFlowFunction_0()) {
          isDirty_flatMapFlowFunction_0 = true;
          flatMapFlowFunction_0.callbackReceived();
          if (isDirty_flatMapFlowFunction_0) {
            mergeFlowFunction_1.inputStreamUpdated(flatMapFlowFunction_0);
          }
        }
        if (guardCheck_mergeFlowFunction_1()) {
          isDirty_mergeFlowFunction_1 = mergeFlowFunction_1.publishMerge();
          if (isDirty_mergeFlowFunction_1) {
            mapRef2RefFlowFunction_3.inputUpdated(mergeFlowFunction_1);
            mapRef2RefFlowFunction_5.inputUpdated(mergeFlowFunction_1);
            mapRef2RefFlowFunction_7.inputUpdated(mergeFlowFunction_1);
            mapRef2RefFlowFunction_9.inputUpdated(mergeFlowFunction_1);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_3()) {
          isDirty_mapRef2RefFlowFunction_3 = mapRef2RefFlowFunction_3.map();
          if (isDirty_mapRef2RefFlowFunction_3) {
            binaryMapToRefFlowFunction_20.inputUpdated(mapRef2RefFlowFunction_3);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_5()) {
          isDirty_mapRef2RefFlowFunction_5 = mapRef2RefFlowFunction_5.map();
          if (isDirty_mapRef2RefFlowFunction_5) {
            binaryMapToRefFlowFunction_20.input2Updated(mapRef2RefFlowFunction_5);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_20()) {
          isDirty_binaryMapToRefFlowFunction_20 = binaryMapToRefFlowFunction_20.map();
          if (isDirty_binaryMapToRefFlowFunction_20) {
            mapRef2RefFlowFunction_22.inputUpdated(binaryMapToRefFlowFunction_20);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_7()) {
          isDirty_mapRef2RefFlowFunction_7 = mapRef2RefFlowFunction_7.map();
          if (isDirty_mapRef2RefFlowFunction_7) {
            binaryMapToRefFlowFunction_39.inputUpdated(mapRef2RefFlowFunction_7);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_9()) {
          isDirty_mapRef2RefFlowFunction_9 = mapRef2RefFlowFunction_9.map();
          if (isDirty_mapRef2RefFlowFunction_9) {
            binaryMapToRefFlowFunction_39.input2Updated(mapRef2RefFlowFunction_9);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_39()) {
          isDirty_binaryMapToRefFlowFunction_39 = binaryMapToRefFlowFunction_39.map();
          if (isDirty_binaryMapToRefFlowFunction_39) {
            mapRef2RefFlowFunction_41.inputUpdated(binaryMapToRefFlowFunction_39);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_22()) {
          isDirty_mapRef2RefFlowFunction_22 = mapRef2RefFlowFunction_22.map();
          if (isDirty_mapRef2RefFlowFunction_22) {
            mapRef2RefFlowFunction_24.inputUpdated(mapRef2RefFlowFunction_22);
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
            binaryMapToRefFlowFunction_32.inputUpdated(mapRef2RefFlowFunction_30);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_32()) {
          isDirty_binaryMapToRefFlowFunction_32 = binaryMapToRefFlowFunction_32.map();
          if (isDirty_binaryMapToRefFlowFunction_32) {
            mapRef2RefFlowFunction_34.inputUpdated(binaryMapToRefFlowFunction_32);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_34()) {
          isDirty_mapRef2RefFlowFunction_34 = mapRef2RefFlowFunction_34.map();
          if (isDirty_mapRef2RefFlowFunction_34) {
            mapRef2RefFlowFunction_35.inputUpdated(mapRef2RefFlowFunction_34);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_35()) {
          isDirty_mapRef2RefFlowFunction_35 = mapRef2RefFlowFunction_35.map();
          if (isDirty_mapRef2RefFlowFunction_35) {
            globalNetMtm.inputUpdated(mapRef2RefFlowFunction_35);
          }
        }
        if (guardCheck_globalNetMtm()) {
          isDirty_globalNetMtm = globalNetMtm.map();
          if (isDirty_globalNetMtm) {
            pushFlowFunction_37.inputUpdated(globalNetMtm);
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
            mapRef2RefFlowFunction_45.inputUpdated(mapRef2RefFlowFunction_43);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_45()) {
          isDirty_mapRef2RefFlowFunction_45 = mapRef2RefFlowFunction_45.map();
          if (isDirty_mapRef2RefFlowFunction_45) {
            binaryMapToRefFlowFunction_47.inputUpdated(mapRef2RefFlowFunction_45);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_47()) {
          isDirty_binaryMapToRefFlowFunction_47 = binaryMapToRefFlowFunction_47.map();
          if (isDirty_binaryMapToRefFlowFunction_47) {
            mapRef2RefFlowFunction_49.inputUpdated(binaryMapToRefFlowFunction_47);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_49()) {
          isDirty_mapRef2RefFlowFunction_49 = mapRef2RefFlowFunction_49.map();
          if (isDirty_mapRef2RefFlowFunction_49) {
            instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_49);
          }
        }
        if (guardCheck_instrumentNetMtm()) {
          isDirty_instrumentNetMtm = instrumentNetMtm.map();
          if (isDirty_instrumentNetMtm) {
            pushFlowFunction_51.inputUpdated(instrumentNetMtm);
          }
        }
        if (guardCheck_pushFlowFunction_37()) {
          isDirty_pushFlowFunction_37 = pushFlowFunction_37.push();
        }
        if (guardCheck_pushFlowFunction_51()) {
          isDirty_pushFlowFunction_51 = pushFlowFunction_51.push();
        }
        afterEvent();
        return;
        //Event Class:[com.fluxtion.runtime.callback.CallbackEvent] filterId:[2]
      case (2):
        isDirty_callbackImpl_66 = callbackImpl_66.onEvent(typedEvent);
        if (guardCheck_flatMapFlowFunction_16()) {
          isDirty_flatMapFlowFunction_16 = true;
          flatMapFlowFunction_16.callbackReceived();
          if (isDirty_flatMapFlowFunction_16) {
            mapRef2RefFlowFunction_18.inputUpdated(flatMapFlowFunction_16);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_18()) {
          isDirty_mapRef2RefFlowFunction_18 = mapRef2RefFlowFunction_18.map();
          if (isDirty_mapRef2RefFlowFunction_18) {
            binaryMapToRefFlowFunction_26.input2Updated(mapRef2RefFlowFunction_18);
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
            binaryMapToRefFlowFunction_32.inputUpdated(mapRef2RefFlowFunction_30);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_32()) {
          isDirty_binaryMapToRefFlowFunction_32 = binaryMapToRefFlowFunction_32.map();
          if (isDirty_binaryMapToRefFlowFunction_32) {
            mapRef2RefFlowFunction_34.inputUpdated(binaryMapToRefFlowFunction_32);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_34()) {
          isDirty_mapRef2RefFlowFunction_34 = mapRef2RefFlowFunction_34.map();
          if (isDirty_mapRef2RefFlowFunction_34) {
            mapRef2RefFlowFunction_35.inputUpdated(mapRef2RefFlowFunction_34);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_35()) {
          isDirty_mapRef2RefFlowFunction_35 = mapRef2RefFlowFunction_35.map();
          if (isDirty_mapRef2RefFlowFunction_35) {
            globalNetMtm.inputUpdated(mapRef2RefFlowFunction_35);
          }
        }
        if (guardCheck_globalNetMtm()) {
          isDirty_globalNetMtm = globalNetMtm.map();
          if (isDirty_globalNetMtm) {
            pushFlowFunction_37.inputUpdated(globalNetMtm);
          }
        }
        if (guardCheck_pushFlowFunction_37()) {
          isDirty_pushFlowFunction_37 = pushFlowFunction_37.push();
        }
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
        isDirty_handlerSignal_positionSnapshotReset =
            handlerSignal_positionSnapshotReset.onEvent(typedEvent);
        if (isDirty_handlerSignal_positionSnapshotReset) {
          mapRef2RefFlowFunction_11.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
          mapRef2RefFlowFunction_18.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
          mapRef2RefFlowFunction_3.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
          mapRef2RefFlowFunction_5.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
          mapRef2RefFlowFunction_7.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
          mapRef2RefFlowFunction_9.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
        }
        if (guardCheck_mapRef2RefFlowFunction_11()) {
          isDirty_mapRef2RefFlowFunction_11 = mapRef2RefFlowFunction_11.map();
          if (isDirty_mapRef2RefFlowFunction_11) {
            mapRef2RefFlowFunction_13.inputUpdated(mapRef2RefFlowFunction_11);
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
            binaryMapToRefFlowFunction_32.input2Updated(mapRef2RefFlowFunction_15);
            binaryMapToRefFlowFunction_47.input2Updated(mapRef2RefFlowFunction_15);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_18()) {
          isDirty_mapRef2RefFlowFunction_18 = mapRef2RefFlowFunction_18.map();
          if (isDirty_mapRef2RefFlowFunction_18) {
            binaryMapToRefFlowFunction_26.input2Updated(mapRef2RefFlowFunction_18);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_3()) {
          isDirty_mapRef2RefFlowFunction_3 = mapRef2RefFlowFunction_3.map();
          if (isDirty_mapRef2RefFlowFunction_3) {
            binaryMapToRefFlowFunction_20.inputUpdated(mapRef2RefFlowFunction_3);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_5()) {
          isDirty_mapRef2RefFlowFunction_5 = mapRef2RefFlowFunction_5.map();
          if (isDirty_mapRef2RefFlowFunction_5) {
            binaryMapToRefFlowFunction_20.input2Updated(mapRef2RefFlowFunction_5);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_20()) {
          isDirty_binaryMapToRefFlowFunction_20 = binaryMapToRefFlowFunction_20.map();
          if (isDirty_binaryMapToRefFlowFunction_20) {
            mapRef2RefFlowFunction_22.inputUpdated(binaryMapToRefFlowFunction_20);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_7()) {
          isDirty_mapRef2RefFlowFunction_7 = mapRef2RefFlowFunction_7.map();
          if (isDirty_mapRef2RefFlowFunction_7) {
            binaryMapToRefFlowFunction_39.inputUpdated(mapRef2RefFlowFunction_7);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_9()) {
          isDirty_mapRef2RefFlowFunction_9 = mapRef2RefFlowFunction_9.map();
          if (isDirty_mapRef2RefFlowFunction_9) {
            binaryMapToRefFlowFunction_39.input2Updated(mapRef2RefFlowFunction_9);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_39()) {
          isDirty_binaryMapToRefFlowFunction_39 = binaryMapToRefFlowFunction_39.map();
          if (isDirty_binaryMapToRefFlowFunction_39) {
            mapRef2RefFlowFunction_41.inputUpdated(binaryMapToRefFlowFunction_39);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_22()) {
          isDirty_mapRef2RefFlowFunction_22 = mapRef2RefFlowFunction_22.map();
          if (isDirty_mapRef2RefFlowFunction_22) {
            mapRef2RefFlowFunction_24.inputUpdated(mapRef2RefFlowFunction_22);
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
            binaryMapToRefFlowFunction_32.inputUpdated(mapRef2RefFlowFunction_30);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_32()) {
          isDirty_binaryMapToRefFlowFunction_32 = binaryMapToRefFlowFunction_32.map();
          if (isDirty_binaryMapToRefFlowFunction_32) {
            mapRef2RefFlowFunction_34.inputUpdated(binaryMapToRefFlowFunction_32);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_34()) {
          isDirty_mapRef2RefFlowFunction_34 = mapRef2RefFlowFunction_34.map();
          if (isDirty_mapRef2RefFlowFunction_34) {
            mapRef2RefFlowFunction_35.inputUpdated(mapRef2RefFlowFunction_34);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_35()) {
          isDirty_mapRef2RefFlowFunction_35 = mapRef2RefFlowFunction_35.map();
          if (isDirty_mapRef2RefFlowFunction_35) {
            globalNetMtm.inputUpdated(mapRef2RefFlowFunction_35);
          }
        }
        if (guardCheck_globalNetMtm()) {
          isDirty_globalNetMtm = globalNetMtm.map();
          if (isDirty_globalNetMtm) {
            pushFlowFunction_37.inputUpdated(globalNetMtm);
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
            mapRef2RefFlowFunction_45.inputUpdated(mapRef2RefFlowFunction_43);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_45()) {
          isDirty_mapRef2RefFlowFunction_45 = mapRef2RefFlowFunction_45.map();
          if (isDirty_mapRef2RefFlowFunction_45) {
            binaryMapToRefFlowFunction_47.inputUpdated(mapRef2RefFlowFunction_45);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_47()) {
          isDirty_binaryMapToRefFlowFunction_47 = binaryMapToRefFlowFunction_47.map();
          if (isDirty_binaryMapToRefFlowFunction_47) {
            mapRef2RefFlowFunction_49.inputUpdated(binaryMapToRefFlowFunction_47);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_49()) {
          isDirty_mapRef2RefFlowFunction_49 = mapRef2RefFlowFunction_49.map();
          if (isDirty_mapRef2RefFlowFunction_49) {
            instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_49);
          }
        }
        if (guardCheck_instrumentNetMtm()) {
          isDirty_instrumentNetMtm = instrumentNetMtm.map();
          if (isDirty_instrumentNetMtm) {
            pushFlowFunction_51.inputUpdated(instrumentNetMtm);
          }
        }
        if (guardCheck_pushFlowFunction_37()) {
          isDirty_pushFlowFunction_37 = pushFlowFunction_37.push();
        }
        if (guardCheck_pushFlowFunction_51()) {
          isDirty_pushFlowFunction_51 = pushFlowFunction_51.push();
        }
        afterEvent();
        return;
        //Event Class:[com.fluxtion.runtime.event.Signal] filterString:[positionUpdate]
      case ("positionUpdate"):
        isDirty_handlerSignal_positionUpdate = handlerSignal_positionUpdate.onEvent(typedEvent);
        if (isDirty_handlerSignal_positionUpdate) {
          mapRef2RefFlowFunction_15.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
          mapRef2RefFlowFunction_15.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
          mapRef2RefFlowFunction_18.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
          mapRef2RefFlowFunction_24.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
          mapRef2RefFlowFunction_30.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
          mapRef2RefFlowFunction_34.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
          mapRef2RefFlowFunction_45.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
          mapRef2RefFlowFunction_45.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
          mapRef2RefFlowFunction_49.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
        }
        if (guardCheck_mapRef2RefFlowFunction_15()) {
          isDirty_mapRef2RefFlowFunction_15 = mapRef2RefFlowFunction_15.map();
          if (isDirty_mapRef2RefFlowFunction_15) {
            binaryMapToRefFlowFunction_32.input2Updated(mapRef2RefFlowFunction_15);
            binaryMapToRefFlowFunction_47.input2Updated(mapRef2RefFlowFunction_15);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_18()) {
          isDirty_mapRef2RefFlowFunction_18 = mapRef2RefFlowFunction_18.map();
          if (isDirty_mapRef2RefFlowFunction_18) {
            binaryMapToRefFlowFunction_26.input2Updated(mapRef2RefFlowFunction_18);
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
            binaryMapToRefFlowFunction_32.inputUpdated(mapRef2RefFlowFunction_30);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_32()) {
          isDirty_binaryMapToRefFlowFunction_32 = binaryMapToRefFlowFunction_32.map();
          if (isDirty_binaryMapToRefFlowFunction_32) {
            mapRef2RefFlowFunction_34.inputUpdated(binaryMapToRefFlowFunction_32);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_34()) {
          isDirty_mapRef2RefFlowFunction_34 = mapRef2RefFlowFunction_34.map();
          if (isDirty_mapRef2RefFlowFunction_34) {
            mapRef2RefFlowFunction_35.inputUpdated(mapRef2RefFlowFunction_34);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_35()) {
          isDirty_mapRef2RefFlowFunction_35 = mapRef2RefFlowFunction_35.map();
          if (isDirty_mapRef2RefFlowFunction_35) {
            globalNetMtm.inputUpdated(mapRef2RefFlowFunction_35);
          }
        }
        if (guardCheck_globalNetMtm()) {
          isDirty_globalNetMtm = globalNetMtm.map();
          if (isDirty_globalNetMtm) {
            pushFlowFunction_37.inputUpdated(globalNetMtm);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_45()) {
          isDirty_mapRef2RefFlowFunction_45 = mapRef2RefFlowFunction_45.map();
          if (isDirty_mapRef2RefFlowFunction_45) {
            binaryMapToRefFlowFunction_47.inputUpdated(mapRef2RefFlowFunction_45);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_47()) {
          isDirty_binaryMapToRefFlowFunction_47 = binaryMapToRefFlowFunction_47.map();
          if (isDirty_binaryMapToRefFlowFunction_47) {
            mapRef2RefFlowFunction_49.inputUpdated(binaryMapToRefFlowFunction_47);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_49()) {
          isDirty_mapRef2RefFlowFunction_49 = mapRef2RefFlowFunction_49.map();
          if (isDirty_mapRef2RefFlowFunction_49) {
            instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_49);
          }
        }
        if (guardCheck_instrumentNetMtm()) {
          isDirty_instrumentNetMtm = instrumentNetMtm.map();
          if (isDirty_instrumentNetMtm) {
            pushFlowFunction_51.inputUpdated(instrumentNetMtm);
          }
        }
        if (guardCheck_pushFlowFunction_37()) {
          isDirty_pushFlowFunction_37 = pushFlowFunction_37.push();
        }
        if (guardCheck_pushFlowFunction_51()) {
          isDirty_pushFlowFunction_51 = pushFlowFunction_51.push();
        }
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
        globalNetMtmListener.unregisterSink(typedEvent);
        afterEvent();
        return;
        //Event Class:[com.fluxtion.runtime.output.SinkDeregister] filterString:[instrumentNetMtmListener]
      case ("instrumentNetMtmListener"):
        instrumentNetMtmListener.unregisterSink(typedEvent);
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
        globalNetMtmListener.sinkRegistration(typedEvent);
        afterEvent();
        return;
        //Event Class:[com.fluxtion.runtime.output.SinkRegistration] filterString:[instrumentNetMtmListener]
      case ("instrumentNetMtmListener"):
        instrumentNetMtmListener.sinkRegistration(typedEvent);
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
    isDirty_derivedRateNode_292 = derivedRateNode_292.midRate(typedEvent);
    afterEvent();
  }

  public void handleEvent(MtmInstrument typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_derivedRateNode_292 = derivedRateNode_292.updateMtmInstrument(typedEvent);
    afterEvent();
  }

  public void handleEvent(PositionSnapshot typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_handlerPositionSnapshot = handlerPositionSnapshot.onEvent(typedEvent);
    if (isDirty_handlerPositionSnapshot) {
      flatMapFlowFunction_16.inputUpdatedAndFlatMap(handlerPositionSnapshot);
    }
    afterEvent();
  }

  public void handleEvent(Trade typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_handlerTrade = handlerTrade.onEvent(typedEvent);
    if (isDirty_handlerTrade) {
      mapRef2RefFlowFunction_11.inputUpdated(handlerTrade);
      mergeFlowFunction_1.inputStreamUpdated(handlerTrade);
    }
    if (guardCheck_mapRef2RefFlowFunction_11()) {
      isDirty_mapRef2RefFlowFunction_11 = mapRef2RefFlowFunction_11.map();
      if (isDirty_mapRef2RefFlowFunction_11) {
        mapRef2RefFlowFunction_13.inputUpdated(mapRef2RefFlowFunction_11);
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
        binaryMapToRefFlowFunction_32.input2Updated(mapRef2RefFlowFunction_15);
        binaryMapToRefFlowFunction_47.input2Updated(mapRef2RefFlowFunction_15);
      }
    }
    if (guardCheck_mergeFlowFunction_1()) {
      isDirty_mergeFlowFunction_1 = mergeFlowFunction_1.publishMerge();
      if (isDirty_mergeFlowFunction_1) {
        mapRef2RefFlowFunction_3.inputUpdated(mergeFlowFunction_1);
        mapRef2RefFlowFunction_5.inputUpdated(mergeFlowFunction_1);
        mapRef2RefFlowFunction_7.inputUpdated(mergeFlowFunction_1);
        mapRef2RefFlowFunction_9.inputUpdated(mergeFlowFunction_1);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_3()) {
      isDirty_mapRef2RefFlowFunction_3 = mapRef2RefFlowFunction_3.map();
      if (isDirty_mapRef2RefFlowFunction_3) {
        binaryMapToRefFlowFunction_20.inputUpdated(mapRef2RefFlowFunction_3);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_5()) {
      isDirty_mapRef2RefFlowFunction_5 = mapRef2RefFlowFunction_5.map();
      if (isDirty_mapRef2RefFlowFunction_5) {
        binaryMapToRefFlowFunction_20.input2Updated(mapRef2RefFlowFunction_5);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_20()) {
      isDirty_binaryMapToRefFlowFunction_20 = binaryMapToRefFlowFunction_20.map();
      if (isDirty_binaryMapToRefFlowFunction_20) {
        mapRef2RefFlowFunction_22.inputUpdated(binaryMapToRefFlowFunction_20);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_7()) {
      isDirty_mapRef2RefFlowFunction_7 = mapRef2RefFlowFunction_7.map();
      if (isDirty_mapRef2RefFlowFunction_7) {
        binaryMapToRefFlowFunction_39.inputUpdated(mapRef2RefFlowFunction_7);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_9()) {
      isDirty_mapRef2RefFlowFunction_9 = mapRef2RefFlowFunction_9.map();
      if (isDirty_mapRef2RefFlowFunction_9) {
        binaryMapToRefFlowFunction_39.input2Updated(mapRef2RefFlowFunction_9);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_39()) {
      isDirty_binaryMapToRefFlowFunction_39 = binaryMapToRefFlowFunction_39.map();
      if (isDirty_binaryMapToRefFlowFunction_39) {
        mapRef2RefFlowFunction_41.inputUpdated(binaryMapToRefFlowFunction_39);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_22()) {
      isDirty_mapRef2RefFlowFunction_22 = mapRef2RefFlowFunction_22.map();
      if (isDirty_mapRef2RefFlowFunction_22) {
        mapRef2RefFlowFunction_24.inputUpdated(mapRef2RefFlowFunction_22);
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
        binaryMapToRefFlowFunction_32.inputUpdated(mapRef2RefFlowFunction_30);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_32()) {
      isDirty_binaryMapToRefFlowFunction_32 = binaryMapToRefFlowFunction_32.map();
      if (isDirty_binaryMapToRefFlowFunction_32) {
        mapRef2RefFlowFunction_34.inputUpdated(binaryMapToRefFlowFunction_32);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_34()) {
      isDirty_mapRef2RefFlowFunction_34 = mapRef2RefFlowFunction_34.map();
      if (isDirty_mapRef2RefFlowFunction_34) {
        mapRef2RefFlowFunction_35.inputUpdated(mapRef2RefFlowFunction_34);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_35()) {
      isDirty_mapRef2RefFlowFunction_35 = mapRef2RefFlowFunction_35.map();
      if (isDirty_mapRef2RefFlowFunction_35) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_35);
      }
    }
    if (guardCheck_globalNetMtm()) {
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_37.inputUpdated(globalNetMtm);
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
        mapRef2RefFlowFunction_45.inputUpdated(mapRef2RefFlowFunction_43);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_45()) {
      isDirty_mapRef2RefFlowFunction_45 = mapRef2RefFlowFunction_45.map();
      if (isDirty_mapRef2RefFlowFunction_45) {
        binaryMapToRefFlowFunction_47.inputUpdated(mapRef2RefFlowFunction_45);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_47()) {
      isDirty_binaryMapToRefFlowFunction_47 = binaryMapToRefFlowFunction_47.map();
      if (isDirty_binaryMapToRefFlowFunction_47) {
        mapRef2RefFlowFunction_49.inputUpdated(binaryMapToRefFlowFunction_47);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_49()) {
      isDirty_mapRef2RefFlowFunction_49 = mapRef2RefFlowFunction_49.map();
      if (isDirty_mapRef2RefFlowFunction_49) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_49);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_51.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_37()) {
      isDirty_pushFlowFunction_37 = pushFlowFunction_37.push();
    }
    if (guardCheck_pushFlowFunction_51()) {
      isDirty_pushFlowFunction_51 = pushFlowFunction_51.push();
    }
    afterEvent();
  }

  public void handleEvent(TradeBatch typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_handlerTradeBatch = handlerTradeBatch.onEvent(typedEvent);
    if (isDirty_handlerTradeBatch) {
      flatMapFlowFunction_0.inputUpdatedAndFlatMap(handlerTradeBatch);
    }
    afterEvent();
  }
  //EVENT DISPATCH - END

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

  public void bufferEvent(Object event) {
    throw new UnsupportedOperationException("bufferEvent not supported");
  }

  public void triggerCalculation() {
    throw new UnsupportedOperationException("triggerCalculation not supported");
  }

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
    auditor.nodeRegistered(callbackDispatcher, "callbackDispatcher");
    auditor.nodeRegistered(callbackImpl_52, "callbackImpl_52");
    auditor.nodeRegistered(callbackImpl_66, "callbackImpl_66");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_20, "binaryMapToRefFlowFunction_20");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_26, "binaryMapToRefFlowFunction_26");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_32, "binaryMapToRefFlowFunction_32");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_39, "binaryMapToRefFlowFunction_39");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_47, "binaryMapToRefFlowFunction_47");
    auditor.nodeRegistered(flatMapFlowFunction_0, "flatMapFlowFunction_0");
    auditor.nodeRegistered(flatMapFlowFunction_16, "flatMapFlowFunction_16");
    auditor.nodeRegistered(globalNetMtm, "globalNetMtm");
    auditor.nodeRegistered(instrumentNetMtm, "instrumentNetMtm");
    auditor.nodeRegistered(mapRef2RefFlowFunction_3, "mapRef2RefFlowFunction_3");
    auditor.nodeRegistered(mapRef2RefFlowFunction_5, "mapRef2RefFlowFunction_5");
    auditor.nodeRegistered(mapRef2RefFlowFunction_7, "mapRef2RefFlowFunction_7");
    auditor.nodeRegistered(mapRef2RefFlowFunction_9, "mapRef2RefFlowFunction_9");
    auditor.nodeRegistered(mapRef2RefFlowFunction_11, "mapRef2RefFlowFunction_11");
    auditor.nodeRegistered(mapRef2RefFlowFunction_13, "mapRef2RefFlowFunction_13");
    auditor.nodeRegistered(mapRef2RefFlowFunction_15, "mapRef2RefFlowFunction_15");
    auditor.nodeRegistered(mapRef2RefFlowFunction_18, "mapRef2RefFlowFunction_18");
    auditor.nodeRegistered(mapRef2RefFlowFunction_22, "mapRef2RefFlowFunction_22");
    auditor.nodeRegistered(mapRef2RefFlowFunction_24, "mapRef2RefFlowFunction_24");
    auditor.nodeRegistered(mapRef2RefFlowFunction_28, "mapRef2RefFlowFunction_28");
    auditor.nodeRegistered(mapRef2RefFlowFunction_30, "mapRef2RefFlowFunction_30");
    auditor.nodeRegistered(mapRef2RefFlowFunction_34, "mapRef2RefFlowFunction_34");
    auditor.nodeRegistered(mapRef2RefFlowFunction_35, "mapRef2RefFlowFunction_35");
    auditor.nodeRegistered(mapRef2RefFlowFunction_41, "mapRef2RefFlowFunction_41");
    auditor.nodeRegistered(mapRef2RefFlowFunction_43, "mapRef2RefFlowFunction_43");
    auditor.nodeRegistered(mapRef2RefFlowFunction_45, "mapRef2RefFlowFunction_45");
    auditor.nodeRegistered(mapRef2RefFlowFunction_49, "mapRef2RefFlowFunction_49");
    auditor.nodeRegistered(mergeFlowFunction_1, "mergeFlowFunction_1");
    auditor.nodeRegistered(pushFlowFunction_37, "pushFlowFunction_37");
    auditor.nodeRegistered(pushFlowFunction_51, "pushFlowFunction_51");
    auditor.nodeRegistered(emptyGroupBy_58, "emptyGroupBy_58");
    auditor.nodeRegistered(emptyGroupBy_106, "emptyGroupBy_106");
    auditor.nodeRegistered(emptyGroupBy_230, "emptyGroupBy_230");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_2, "groupByFlowFunctionWrapper_2");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_4, "groupByFlowFunctionWrapper_4");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_6, "groupByFlowFunctionWrapper_6");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_8, "groupByFlowFunctionWrapper_8");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_10, "groupByFlowFunctionWrapper_10");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_17, "groupByFlowFunctionWrapper_17");
    auditor.nodeRegistered(groupByMapFlowFunction_14, "groupByMapFlowFunction_14");
    auditor.nodeRegistered(groupByMapFlowFunction_21, "groupByMapFlowFunction_21");
    auditor.nodeRegistered(groupByMapFlowFunction_23, "groupByMapFlowFunction_23");
    auditor.nodeRegistered(groupByMapFlowFunction_27, "groupByMapFlowFunction_27");
    auditor.nodeRegistered(groupByMapFlowFunction_33, "groupByMapFlowFunction_33");
    auditor.nodeRegistered(groupByMapFlowFunction_40, "groupByMapFlowFunction_40");
    auditor.nodeRegistered(groupByMapFlowFunction_42, "groupByMapFlowFunction_42");
    auditor.nodeRegistered(groupByMapFlowFunction_48, "groupByMapFlowFunction_48");
    auditor.nodeRegistered(leftJoin_31, "leftJoin_31");
    auditor.nodeRegistered(leftJoin_46, "leftJoin_46");
    auditor.nodeRegistered(outerJoin_19, "outerJoin_19");
    auditor.nodeRegistered(outerJoin_25, "outerJoin_25");
    auditor.nodeRegistered(outerJoin_38, "outerJoin_38");
    auditor.nodeRegistered(defaultValue_12, "defaultValue_12");
    auditor.nodeRegistered(defaultValue_29, "defaultValue_29");
    auditor.nodeRegistered(defaultValue_44, "defaultValue_44");
    auditor.nodeRegistered(mapTuple_195, "mapTuple_195");
    auditor.nodeRegistered(mapTuple_203, "mapTuple_203");
    auditor.nodeRegistered(mapTuple_209, "mapTuple_209");
    auditor.nodeRegistered(mapTuple_284, "mapTuple_284");
    auditor.nodeRegistered(mapTuple_294, "mapTuple_294");
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
    auditor.nodeRegistered(derivedRateNode_292, "derivedRateNode_292");
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
    isDirty_binaryMapToRefFlowFunction_20 = false;
    isDirty_binaryMapToRefFlowFunction_26 = false;
    isDirty_binaryMapToRefFlowFunction_32 = false;
    isDirty_binaryMapToRefFlowFunction_39 = false;
    isDirty_binaryMapToRefFlowFunction_47 = false;
    isDirty_callbackImpl_52 = false;
    isDirty_callbackImpl_66 = false;
    isDirty_clock = false;
    isDirty_derivedRateNode_292 = false;
    isDirty_flatMapFlowFunction_0 = false;
    isDirty_flatMapFlowFunction_16 = false;
    isDirty_globalNetMtm = false;
    isDirty_handlerPositionSnapshot = false;
    isDirty_handlerSignal_positionSnapshotReset = false;
    isDirty_handlerSignal_positionUpdate = false;
    isDirty_handlerTrade = false;
    isDirty_handlerTradeBatch = false;
    isDirty_instrumentNetMtm = false;
    isDirty_mapRef2RefFlowFunction_3 = false;
    isDirty_mapRef2RefFlowFunction_5 = false;
    isDirty_mapRef2RefFlowFunction_7 = false;
    isDirty_mapRef2RefFlowFunction_9 = false;
    isDirty_mapRef2RefFlowFunction_11 = false;
    isDirty_mapRef2RefFlowFunction_13 = false;
    isDirty_mapRef2RefFlowFunction_15 = false;
    isDirty_mapRef2RefFlowFunction_18 = false;
    isDirty_mapRef2RefFlowFunction_22 = false;
    isDirty_mapRef2RefFlowFunction_24 = false;
    isDirty_mapRef2RefFlowFunction_28 = false;
    isDirty_mapRef2RefFlowFunction_30 = false;
    isDirty_mapRef2RefFlowFunction_34 = false;
    isDirty_mapRef2RefFlowFunction_35 = false;
    isDirty_mapRef2RefFlowFunction_41 = false;
    isDirty_mapRef2RefFlowFunction_43 = false;
    isDirty_mapRef2RefFlowFunction_45 = false;
    isDirty_mapRef2RefFlowFunction_49 = false;
    isDirty_mergeFlowFunction_1 = false;
    isDirty_pushFlowFunction_37 = false;
    isDirty_pushFlowFunction_51 = false;
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
          binaryMapToRefFlowFunction_20, () -> isDirty_binaryMapToRefFlowFunction_20);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_26, () -> isDirty_binaryMapToRefFlowFunction_26);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_32, () -> isDirty_binaryMapToRefFlowFunction_32);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_39, () -> isDirty_binaryMapToRefFlowFunction_39);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_47, () -> isDirty_binaryMapToRefFlowFunction_47);
      dirtyFlagSupplierMap.put(callbackImpl_52, () -> isDirty_callbackImpl_52);
      dirtyFlagSupplierMap.put(callbackImpl_66, () -> isDirty_callbackImpl_66);
      dirtyFlagSupplierMap.put(clock, () -> isDirty_clock);
      dirtyFlagSupplierMap.put(derivedRateNode_292, () -> isDirty_derivedRateNode_292);
      dirtyFlagSupplierMap.put(flatMapFlowFunction_0, () -> isDirty_flatMapFlowFunction_0);
      dirtyFlagSupplierMap.put(flatMapFlowFunction_16, () -> isDirty_flatMapFlowFunction_16);
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
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_13, () -> isDirty_mapRef2RefFlowFunction_13);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_15, () -> isDirty_mapRef2RefFlowFunction_15);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_18, () -> isDirty_mapRef2RefFlowFunction_18);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_22, () -> isDirty_mapRef2RefFlowFunction_22);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_24, () -> isDirty_mapRef2RefFlowFunction_24);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_28, () -> isDirty_mapRef2RefFlowFunction_28);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_3, () -> isDirty_mapRef2RefFlowFunction_3);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_30, () -> isDirty_mapRef2RefFlowFunction_30);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_34, () -> isDirty_mapRef2RefFlowFunction_34);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_35, () -> isDirty_mapRef2RefFlowFunction_35);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_41, () -> isDirty_mapRef2RefFlowFunction_41);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_43, () -> isDirty_mapRef2RefFlowFunction_43);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_45, () -> isDirty_mapRef2RefFlowFunction_45);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_49, () -> isDirty_mapRef2RefFlowFunction_49);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_5, () -> isDirty_mapRef2RefFlowFunction_5);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_7, () -> isDirty_mapRef2RefFlowFunction_7);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_9, () -> isDirty_mapRef2RefFlowFunction_9);
      dirtyFlagSupplierMap.put(mergeFlowFunction_1, () -> isDirty_mergeFlowFunction_1);
      dirtyFlagSupplierMap.put(pushFlowFunction_37, () -> isDirty_pushFlowFunction_37);
      dirtyFlagSupplierMap.put(pushFlowFunction_51, () -> isDirty_pushFlowFunction_51);
    }
    return dirtyFlagSupplierMap.getOrDefault(node, StaticEventProcessor.ALWAYS_FALSE);
  }

  @Override
  public void setDirty(Object node, boolean dirtyFlag) {
    if (dirtyFlagUpdateMap.isEmpty()) {
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_20, (b) -> isDirty_binaryMapToRefFlowFunction_20 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_26, (b) -> isDirty_binaryMapToRefFlowFunction_26 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_32, (b) -> isDirty_binaryMapToRefFlowFunction_32 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_39, (b) -> isDirty_binaryMapToRefFlowFunction_39 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_47, (b) -> isDirty_binaryMapToRefFlowFunction_47 = b);
      dirtyFlagUpdateMap.put(callbackImpl_52, (b) -> isDirty_callbackImpl_52 = b);
      dirtyFlagUpdateMap.put(callbackImpl_66, (b) -> isDirty_callbackImpl_66 = b);
      dirtyFlagUpdateMap.put(clock, (b) -> isDirty_clock = b);
      dirtyFlagUpdateMap.put(derivedRateNode_292, (b) -> isDirty_derivedRateNode_292 = b);
      dirtyFlagUpdateMap.put(flatMapFlowFunction_0, (b) -> isDirty_flatMapFlowFunction_0 = b);
      dirtyFlagUpdateMap.put(flatMapFlowFunction_16, (b) -> isDirty_flatMapFlowFunction_16 = b);
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
          mapRef2RefFlowFunction_13, (b) -> isDirty_mapRef2RefFlowFunction_13 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_15, (b) -> isDirty_mapRef2RefFlowFunction_15 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_18, (b) -> isDirty_mapRef2RefFlowFunction_18 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_22, (b) -> isDirty_mapRef2RefFlowFunction_22 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_24, (b) -> isDirty_mapRef2RefFlowFunction_24 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_28, (b) -> isDirty_mapRef2RefFlowFunction_28 = b);
      dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_3, (b) -> isDirty_mapRef2RefFlowFunction_3 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_30, (b) -> isDirty_mapRef2RefFlowFunction_30 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_34, (b) -> isDirty_mapRef2RefFlowFunction_34 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_35, (b) -> isDirty_mapRef2RefFlowFunction_35 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_41, (b) -> isDirty_mapRef2RefFlowFunction_41 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_43, (b) -> isDirty_mapRef2RefFlowFunction_43 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_45, (b) -> isDirty_mapRef2RefFlowFunction_45 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_49, (b) -> isDirty_mapRef2RefFlowFunction_49 = b);
      dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_5, (b) -> isDirty_mapRef2RefFlowFunction_5 = b);
      dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_7, (b) -> isDirty_mapRef2RefFlowFunction_7 = b);
      dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_9, (b) -> isDirty_mapRef2RefFlowFunction_9 = b);
      dirtyFlagUpdateMap.put(mergeFlowFunction_1, (b) -> isDirty_mergeFlowFunction_1 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_37, (b) -> isDirty_pushFlowFunction_37 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_51, (b) -> isDirty_pushFlowFunction_51 = b);
    }
    dirtyFlagUpdateMap.get(node).accept(dirtyFlag);
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_20() {
    return isDirty_mapRef2RefFlowFunction_3 | isDirty_mapRef2RefFlowFunction_5;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_26() {
    return isDirty_mapRef2RefFlowFunction_18 | isDirty_mapRef2RefFlowFunction_24;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_32() {
    return isDirty_mapRef2RefFlowFunction_15 | isDirty_mapRef2RefFlowFunction_30;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_39() {
    return isDirty_mapRef2RefFlowFunction_7 | isDirty_mapRef2RefFlowFunction_9;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_47() {
    return isDirty_mapRef2RefFlowFunction_15 | isDirty_mapRef2RefFlowFunction_45;
  }

  private boolean guardCheck_flatMapFlowFunction_0() {
    return isDirty_callbackImpl_52;
  }

  private boolean guardCheck_flatMapFlowFunction_16() {
    return isDirty_callbackImpl_66;
  }

  private boolean guardCheck_globalNetMtm() {
    return isDirty_mapRef2RefFlowFunction_35;
  }

  private boolean guardCheck_instrumentNetMtm() {
    return isDirty_mapRef2RefFlowFunction_49;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_3() {
    return isDirty_handlerSignal_positionSnapshotReset | isDirty_mergeFlowFunction_1;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_5() {
    return isDirty_handlerSignal_positionSnapshotReset | isDirty_mergeFlowFunction_1;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_7() {
    return isDirty_handlerSignal_positionSnapshotReset | isDirty_mergeFlowFunction_1;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_9() {
    return isDirty_handlerSignal_positionSnapshotReset | isDirty_mergeFlowFunction_1;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_11() {
    return isDirty_handlerSignal_positionSnapshotReset | isDirty_handlerTrade;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_13() {
    return isDirty_mapRef2RefFlowFunction_11;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_15() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_13;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_18() {
    return isDirty_flatMapFlowFunction_16
        | isDirty_handlerSignal_positionSnapshotReset
        | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_22() {
    return isDirty_binaryMapToRefFlowFunction_20;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_24() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_22;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_28() {
    return isDirty_binaryMapToRefFlowFunction_26;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_30() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_28;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_34() {
    return isDirty_binaryMapToRefFlowFunction_32 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_35() {
    return isDirty_mapRef2RefFlowFunction_34;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_41() {
    return isDirty_binaryMapToRefFlowFunction_39;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_43() {
    return isDirty_mapRef2RefFlowFunction_41;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_45() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_43;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_49() {
    return isDirty_binaryMapToRefFlowFunction_47 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mergeFlowFunction_1() {
    return isDirty_flatMapFlowFunction_0 | isDirty_handlerTrade;
  }

  private boolean guardCheck_pushFlowFunction_37() {
    return isDirty_globalNetMtm;
  }

  private boolean guardCheck_pushFlowFunction_51() {
    return isDirty_instrumentNetMtm;
  }

  private boolean guardCheck_groupByMapFlowFunction_14() {
    return isDirty_derivedRateNode_292;
  }

  private boolean guardCheck_groupByMapFlowFunction_23() {
    return isDirty_derivedRateNode_292;
  }

  private boolean guardCheck_groupByMapFlowFunction_42() {
    return isDirty_derivedRateNode_292;
  }

  private boolean guardCheck_context() {
    return isDirty_clock;
  }

  private boolean guardCheck_globalNetMtmListener() {
    return isDirty_pushFlowFunction_37;
  }

  private boolean guardCheck_instrumentNetMtmListener() {
    return isDirty_pushFlowFunction_51;
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
