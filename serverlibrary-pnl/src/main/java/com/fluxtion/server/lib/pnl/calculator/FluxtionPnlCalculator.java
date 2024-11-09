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
  private final CallbackImpl callbackImpl_53 = new CallbackImpl<>(1, callbackDispatcher);
  private final CallbackImpl callbackImpl_67 = new CallbackImpl<>(2, callbackDispatcher);
  public final Clock clock = new Clock();
  public final DerivedRateNode derivedRateNode = new DerivedRateNode();
  private final EmptyGroupBy emptyGroupBy_59 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_13 = new DefaultValue<>(emptyGroupBy_59);
  private final EmptyGroupBy emptyGroupBy_107 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_30 = new DefaultValue<>(emptyGroupBy_107);
  private final EmptyGroupBy emptyGroupBy_231 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_45 = new DefaultValue<>(emptyGroupBy_231);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_3 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, InstrumentPosMtmAggregate::dealt);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_5 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, InstrumentPosMtmAggregate::contra);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_7 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, SingleInstrumentPosMtmAggregate::dealt);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_9 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, SingleInstrumentPosMtmAggregate::contra);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_11 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, FeeInstrumentPosMtmAggregate::new);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_18 =
      new GroupByFlowFunctionWrapper<>(
          InstrumentPosition::instrument, Mappers::identity, AggregateIdentityFlowFunction::new);
  private final GroupByMapFlowFunction groupByMapFlowFunction_15 =
      new GroupByMapFlowFunction(derivedRateNode::calculateFeeMtm);
  private final GroupByMapFlowFunction groupByMapFlowFunction_24 =
      new GroupByMapFlowFunction(derivedRateNode::calculateInstrumentPosMtm);
  private final GroupByMapFlowFunction groupByMapFlowFunction_43 =
      new GroupByMapFlowFunction(derivedRateNode::calculateInstrumentPosMtm);
  private final LeftJoin leftJoin_32 = new LeftJoin();
  private final LeftJoin leftJoin_47 = new LeftJoin();
  private final MapTuple mapTuple_196 = new MapTuple<>(NetMarkToMarket::combine);
  private final GroupByMapFlowFunction groupByMapFlowFunction_34 =
      new GroupByMapFlowFunction(mapTuple_196::mapTuple);
  private final MapTuple mapTuple_204 = new MapTuple<>(InstrumentPosMtm::addInstrumentPosition);
  private final GroupByMapFlowFunction groupByMapFlowFunction_28 =
      new GroupByMapFlowFunction(mapTuple_204::mapTuple);
  private final MapTuple mapTuple_210 = new MapTuple<>(InstrumentPosMtm::merge);
  private final GroupByMapFlowFunction groupByMapFlowFunction_22 =
      new GroupByMapFlowFunction(mapTuple_210::mapTuple);
  private final MapTuple mapTuple_285 = new MapTuple<>(NetMarkToMarket::combine);
  private final GroupByMapFlowFunction groupByMapFlowFunction_49 =
      new GroupByMapFlowFunction(mapTuple_285::mapTuple);
  private final MapTuple mapTuple_295 = new MapTuple<>(InstrumentPosMtm::merge);
  private final GroupByMapFlowFunction groupByMapFlowFunction_41 =
      new GroupByMapFlowFunction(mapTuple_295::mapTuple);
  public final NodeNameAuditor nodeNameLookup = new NodeNameAuditor();
  private final OuterJoin outerJoin_20 = new OuterJoin();
  private final OuterJoin outerJoin_26 = new OuterJoin();
  private final OuterJoin outerJoin_39 = new OuterJoin();
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
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_12 =
      new MapRef2RefFlowFunction<>(handlerTrade, groupByFlowFunctionWrapper_11::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_14 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_12, defaultValue_13::getOrDefault);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_16 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_14, groupByMapFlowFunction_15::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_19 =
      new MapRef2RefFlowFunction<>(
          flatMapFlowFunction_17, groupByFlowFunctionWrapper_18::aggregate);
  private final MergeFlowFunction mergeFlowFunction_2 =
      new MergeFlowFunction<>(Arrays.asList(handlerTrade, flatMapFlowFunction_1));
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_4 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_2, groupByFlowFunctionWrapper_3::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_6 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_2, groupByFlowFunctionWrapper_5::aggregate);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_21 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_4, mapRef2RefFlowFunction_6, outerJoin_20::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_8 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_2, groupByFlowFunctionWrapper_7::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_10 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_2, groupByFlowFunctionWrapper_9::aggregate);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_40 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_8, mapRef2RefFlowFunction_10, outerJoin_39::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_23 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_21, groupByMapFlowFunction_22::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_25 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_23, groupByMapFlowFunction_24::mapValues);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_27 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_25, mapRef2RefFlowFunction_19, outerJoin_26::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_29 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_27, groupByMapFlowFunction_28::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_31 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_29, defaultValue_30::getOrDefault);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_33 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_31, mapRef2RefFlowFunction_16, leftJoin_32::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_35 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_33, groupByMapFlowFunction_34::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_36 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_35, GroupBy<Object, Object>::toMap);
  public final MapRef2RefFlowFunction globalNetMtm =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_36, MtmCalc::markToMarketSum);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_42 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_40, groupByMapFlowFunction_41::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_44 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_42, groupByMapFlowFunction_43::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_46 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_44, defaultValue_45::getOrDefault);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_48 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_46, mapRef2RefFlowFunction_16, leftJoin_47::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_50 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_48, groupByMapFlowFunction_49::mapValues);
  public final MapRef2RefFlowFunction instrumentNetMtm =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_50, GroupBy<Object, Object>::toMap);
  private final PushFlowFunction pushFlowFunction_38 =
      new PushFlowFunction<>(globalNetMtm, globalNetMtmListener::publish);
  private final PushFlowFunction pushFlowFunction_52 =
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

  private boolean isDirty_binaryMapToRefFlowFunction_21 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_27 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_33 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_40 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_48 = false;
  private boolean isDirty_callbackImpl_53 = false;
  private boolean isDirty_callbackImpl_67 = false;
  private boolean isDirty_clock = false;
  private boolean isDirty_derivedRateNode = false;
  private boolean isDirty_flatMapFlowFunction_1 = false;
  private boolean isDirty_flatMapFlowFunction_17 = false;
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
  private boolean isDirty_mapRef2RefFlowFunction_12 = false;
  private boolean isDirty_mapRef2RefFlowFunction_14 = false;
  private boolean isDirty_mapRef2RefFlowFunction_16 = false;
  private boolean isDirty_mapRef2RefFlowFunction_19 = false;
  private boolean isDirty_mapRef2RefFlowFunction_23 = false;
  private boolean isDirty_mapRef2RefFlowFunction_25 = false;
  private boolean isDirty_mapRef2RefFlowFunction_29 = false;
  private boolean isDirty_mapRef2RefFlowFunction_31 = false;
  private boolean isDirty_mapRef2RefFlowFunction_35 = false;
  private boolean isDirty_mapRef2RefFlowFunction_36 = false;
  private boolean isDirty_mapRef2RefFlowFunction_42 = false;
  private boolean isDirty_mapRef2RefFlowFunction_44 = false;
  private boolean isDirty_mapRef2RefFlowFunction_46 = false;
  private boolean isDirty_mapRef2RefFlowFunction_50 = false;
  private boolean isDirty_mergeFlowFunction_2 = false;
  private boolean isDirty_pushFlowFunction_38 = false;
  private boolean isDirty_pushFlowFunction_52 = false;

  //Forked declarations

  //Filter constants

  //unknown event handler
  private Consumer unKnownEventHandler = (e) -> {};

  public FluxtionPnlCalculator(Map<Object, Object> contextMap) {
    if (context != null) {
      context.replaceMappings(contextMap);
    }
    callbackImpl_53.dirtyStateMonitor = callbackDispatcher;
    callbackImpl_67.dirtyStateMonitor = callbackDispatcher;
    binaryMapToRefFlowFunction_21.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_27.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_33.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_40.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_48.setEventProcessorContext(context);
    flatMapFlowFunction_1.callback = callbackImpl_53;
    flatMapFlowFunction_1.dirtyStateMonitor = callbackDispatcher;
    flatMapFlowFunction_17.callback = callbackImpl_67;
    flatMapFlowFunction_17.dirtyStateMonitor = callbackDispatcher;
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
    mapRef2RefFlowFunction_12.setEventProcessorContext(context);
    mapRef2RefFlowFunction_12.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_14.setEventProcessorContext(context);
    mapRef2RefFlowFunction_16.setEventProcessorContext(context);
    mapRef2RefFlowFunction_16.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_16.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_19.setEventProcessorContext(context);
    mapRef2RefFlowFunction_19.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_19.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_23.setEventProcessorContext(context);
    mapRef2RefFlowFunction_25.setEventProcessorContext(context);
    mapRef2RefFlowFunction_25.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_29.setEventProcessorContext(context);
    mapRef2RefFlowFunction_31.setEventProcessorContext(context);
    mapRef2RefFlowFunction_31.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_35.setEventProcessorContext(context);
    mapRef2RefFlowFunction_35.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_36.setEventProcessorContext(context);
    mapRef2RefFlowFunction_42.setEventProcessorContext(context);
    mapRef2RefFlowFunction_44.setEventProcessorContext(context);
    mapRef2RefFlowFunction_46.setEventProcessorContext(context);
    mapRef2RefFlowFunction_46.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_46.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_50.setEventProcessorContext(context);
    mapRef2RefFlowFunction_50.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mergeFlowFunction_2.dirtyStateMonitor = callbackDispatcher;
    pushFlowFunction_38.setEventProcessorContext(context);
    pushFlowFunction_52.setEventProcessorContext(context);
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
    callbackImpl_53.init();
    callbackImpl_67.init();
    clock.init();
    handlerPositionSnapshot.init();
    handlerSignal_positionSnapshotReset.init();
    handlerSignal_positionUpdate.init();
    handlerTrade.init();
    handlerTradeBatch.init();
    mapRef2RefFlowFunction_12.initialiseEventStream();
    mapRef2RefFlowFunction_14.initialiseEventStream();
    mapRef2RefFlowFunction_16.initialiseEventStream();
    mapRef2RefFlowFunction_19.initialiseEventStream();
    mapRef2RefFlowFunction_4.initialiseEventStream();
    mapRef2RefFlowFunction_6.initialiseEventStream();
    binaryMapToRefFlowFunction_21.initialiseEventStream();
    mapRef2RefFlowFunction_8.initialiseEventStream();
    mapRef2RefFlowFunction_10.initialiseEventStream();
    binaryMapToRefFlowFunction_40.initialiseEventStream();
    mapRef2RefFlowFunction_23.initialiseEventStream();
    mapRef2RefFlowFunction_25.initialiseEventStream();
    binaryMapToRefFlowFunction_27.initialiseEventStream();
    mapRef2RefFlowFunction_29.initialiseEventStream();
    mapRef2RefFlowFunction_31.initialiseEventStream();
    binaryMapToRefFlowFunction_33.initialiseEventStream();
    mapRef2RefFlowFunction_35.initialiseEventStream();
    mapRef2RefFlowFunction_36.initialiseEventStream();
    globalNetMtm.initialiseEventStream();
    mapRef2RefFlowFunction_42.initialiseEventStream();
    mapRef2RefFlowFunction_44.initialiseEventStream();
    mapRef2RefFlowFunction_46.initialiseEventStream();
    binaryMapToRefFlowFunction_48.initialiseEventStream();
    mapRef2RefFlowFunction_50.initialiseEventStream();
    instrumentNetMtm.initialiseEventStream();
    pushFlowFunction_38.initialiseEventStream();
    pushFlowFunction_52.initialiseEventStream();
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
        isDirty_callbackImpl_53 = callbackImpl_53.onEvent(typedEvent);
        if (guardCheck_flatMapFlowFunction_1()) {
          isDirty_flatMapFlowFunction_1 = true;
          flatMapFlowFunction_1.callbackReceived();
          if (isDirty_flatMapFlowFunction_1) {
            mergeFlowFunction_2.inputStreamUpdated(flatMapFlowFunction_1);
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
            binaryMapToRefFlowFunction_21.inputUpdated(mapRef2RefFlowFunction_4);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_6()) {
          isDirty_mapRef2RefFlowFunction_6 = mapRef2RefFlowFunction_6.map();
          if (isDirty_mapRef2RefFlowFunction_6) {
            binaryMapToRefFlowFunction_21.input2Updated(mapRef2RefFlowFunction_6);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_21()) {
          isDirty_binaryMapToRefFlowFunction_21 = binaryMapToRefFlowFunction_21.map();
          if (isDirty_binaryMapToRefFlowFunction_21) {
            mapRef2RefFlowFunction_23.inputUpdated(binaryMapToRefFlowFunction_21);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_8()) {
          isDirty_mapRef2RefFlowFunction_8 = mapRef2RefFlowFunction_8.map();
          if (isDirty_mapRef2RefFlowFunction_8) {
            binaryMapToRefFlowFunction_40.inputUpdated(mapRef2RefFlowFunction_8);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_10()) {
          isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
          if (isDirty_mapRef2RefFlowFunction_10) {
            binaryMapToRefFlowFunction_40.input2Updated(mapRef2RefFlowFunction_10);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_40()) {
          isDirty_binaryMapToRefFlowFunction_40 = binaryMapToRefFlowFunction_40.map();
          if (isDirty_binaryMapToRefFlowFunction_40) {
            mapRef2RefFlowFunction_42.inputUpdated(binaryMapToRefFlowFunction_40);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_23()) {
          isDirty_mapRef2RefFlowFunction_23 = mapRef2RefFlowFunction_23.map();
          if (isDirty_mapRef2RefFlowFunction_23) {
            mapRef2RefFlowFunction_25.inputUpdated(mapRef2RefFlowFunction_23);
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
            binaryMapToRefFlowFunction_33.inputUpdated(mapRef2RefFlowFunction_31);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_33()) {
          isDirty_binaryMapToRefFlowFunction_33 = binaryMapToRefFlowFunction_33.map();
          if (isDirty_binaryMapToRefFlowFunction_33) {
            mapRef2RefFlowFunction_35.inputUpdated(binaryMapToRefFlowFunction_33);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_35()) {
          isDirty_mapRef2RefFlowFunction_35 = mapRef2RefFlowFunction_35.map();
          if (isDirty_mapRef2RefFlowFunction_35) {
            mapRef2RefFlowFunction_36.inputUpdated(mapRef2RefFlowFunction_35);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_36()) {
          isDirty_mapRef2RefFlowFunction_36 = mapRef2RefFlowFunction_36.map();
          if (isDirty_mapRef2RefFlowFunction_36) {
            globalNetMtm.inputUpdated(mapRef2RefFlowFunction_36);
          }
        }
        if (guardCheck_globalNetMtm()) {
          isDirty_globalNetMtm = globalNetMtm.map();
          if (isDirty_globalNetMtm) {
            pushFlowFunction_38.inputUpdated(globalNetMtm);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_42()) {
          isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
          if (isDirty_mapRef2RefFlowFunction_42) {
            mapRef2RefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_42);
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
            binaryMapToRefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_48()) {
          isDirty_binaryMapToRefFlowFunction_48 = binaryMapToRefFlowFunction_48.map();
          if (isDirty_binaryMapToRefFlowFunction_48) {
            mapRef2RefFlowFunction_50.inputUpdated(binaryMapToRefFlowFunction_48);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_50()) {
          isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
          if (isDirty_mapRef2RefFlowFunction_50) {
            instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_50);
          }
        }
        if (guardCheck_instrumentNetMtm()) {
          isDirty_instrumentNetMtm = instrumentNetMtm.map();
          if (isDirty_instrumentNetMtm) {
            pushFlowFunction_52.inputUpdated(instrumentNetMtm);
          }
        }
        if (guardCheck_pushFlowFunction_38()) {
          isDirty_pushFlowFunction_38 = pushFlowFunction_38.push();
        }
        if (guardCheck_pushFlowFunction_52()) {
          isDirty_pushFlowFunction_52 = pushFlowFunction_52.push();
        }
        afterEvent();
        return;
        //Event Class:[com.fluxtion.runtime.callback.CallbackEvent] filterId:[2]
      case (2):
        isDirty_callbackImpl_67 = callbackImpl_67.onEvent(typedEvent);
        if (guardCheck_flatMapFlowFunction_17()) {
          isDirty_flatMapFlowFunction_17 = true;
          flatMapFlowFunction_17.callbackReceived();
          if (isDirty_flatMapFlowFunction_17) {
            mapRef2RefFlowFunction_19.inputUpdated(flatMapFlowFunction_17);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_19()) {
          isDirty_mapRef2RefFlowFunction_19 = mapRef2RefFlowFunction_19.map();
          if (isDirty_mapRef2RefFlowFunction_19) {
            binaryMapToRefFlowFunction_27.input2Updated(mapRef2RefFlowFunction_19);
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
            binaryMapToRefFlowFunction_33.inputUpdated(mapRef2RefFlowFunction_31);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_33()) {
          isDirty_binaryMapToRefFlowFunction_33 = binaryMapToRefFlowFunction_33.map();
          if (isDirty_binaryMapToRefFlowFunction_33) {
            mapRef2RefFlowFunction_35.inputUpdated(binaryMapToRefFlowFunction_33);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_35()) {
          isDirty_mapRef2RefFlowFunction_35 = mapRef2RefFlowFunction_35.map();
          if (isDirty_mapRef2RefFlowFunction_35) {
            mapRef2RefFlowFunction_36.inputUpdated(mapRef2RefFlowFunction_35);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_36()) {
          isDirty_mapRef2RefFlowFunction_36 = mapRef2RefFlowFunction_36.map();
          if (isDirty_mapRef2RefFlowFunction_36) {
            globalNetMtm.inputUpdated(mapRef2RefFlowFunction_36);
          }
        }
        if (guardCheck_globalNetMtm()) {
          isDirty_globalNetMtm = globalNetMtm.map();
          if (isDirty_globalNetMtm) {
            pushFlowFunction_38.inputUpdated(globalNetMtm);
          }
        }
        if (guardCheck_pushFlowFunction_38()) {
          isDirty_pushFlowFunction_38 = pushFlowFunction_38.push();
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
          mapRef2RefFlowFunction_12.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
          mapRef2RefFlowFunction_19.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
          mapRef2RefFlowFunction_4.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
          mapRef2RefFlowFunction_6.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
          mapRef2RefFlowFunction_8.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
          mapRef2RefFlowFunction_10.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
        }
        if (guardCheck_mapRef2RefFlowFunction_12()) {
          isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
          if (isDirty_mapRef2RefFlowFunction_12) {
            mapRef2RefFlowFunction_14.inputUpdated(mapRef2RefFlowFunction_12);
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
            binaryMapToRefFlowFunction_33.input2Updated(mapRef2RefFlowFunction_16);
            binaryMapToRefFlowFunction_48.input2Updated(mapRef2RefFlowFunction_16);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_19()) {
          isDirty_mapRef2RefFlowFunction_19 = mapRef2RefFlowFunction_19.map();
          if (isDirty_mapRef2RefFlowFunction_19) {
            binaryMapToRefFlowFunction_27.input2Updated(mapRef2RefFlowFunction_19);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_4()) {
          isDirty_mapRef2RefFlowFunction_4 = mapRef2RefFlowFunction_4.map();
          if (isDirty_mapRef2RefFlowFunction_4) {
            binaryMapToRefFlowFunction_21.inputUpdated(mapRef2RefFlowFunction_4);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_6()) {
          isDirty_mapRef2RefFlowFunction_6 = mapRef2RefFlowFunction_6.map();
          if (isDirty_mapRef2RefFlowFunction_6) {
            binaryMapToRefFlowFunction_21.input2Updated(mapRef2RefFlowFunction_6);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_21()) {
          isDirty_binaryMapToRefFlowFunction_21 = binaryMapToRefFlowFunction_21.map();
          if (isDirty_binaryMapToRefFlowFunction_21) {
            mapRef2RefFlowFunction_23.inputUpdated(binaryMapToRefFlowFunction_21);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_8()) {
          isDirty_mapRef2RefFlowFunction_8 = mapRef2RefFlowFunction_8.map();
          if (isDirty_mapRef2RefFlowFunction_8) {
            binaryMapToRefFlowFunction_40.inputUpdated(mapRef2RefFlowFunction_8);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_10()) {
          isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
          if (isDirty_mapRef2RefFlowFunction_10) {
            binaryMapToRefFlowFunction_40.input2Updated(mapRef2RefFlowFunction_10);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_40()) {
          isDirty_binaryMapToRefFlowFunction_40 = binaryMapToRefFlowFunction_40.map();
          if (isDirty_binaryMapToRefFlowFunction_40) {
            mapRef2RefFlowFunction_42.inputUpdated(binaryMapToRefFlowFunction_40);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_23()) {
          isDirty_mapRef2RefFlowFunction_23 = mapRef2RefFlowFunction_23.map();
          if (isDirty_mapRef2RefFlowFunction_23) {
            mapRef2RefFlowFunction_25.inputUpdated(mapRef2RefFlowFunction_23);
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
            binaryMapToRefFlowFunction_33.inputUpdated(mapRef2RefFlowFunction_31);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_33()) {
          isDirty_binaryMapToRefFlowFunction_33 = binaryMapToRefFlowFunction_33.map();
          if (isDirty_binaryMapToRefFlowFunction_33) {
            mapRef2RefFlowFunction_35.inputUpdated(binaryMapToRefFlowFunction_33);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_35()) {
          isDirty_mapRef2RefFlowFunction_35 = mapRef2RefFlowFunction_35.map();
          if (isDirty_mapRef2RefFlowFunction_35) {
            mapRef2RefFlowFunction_36.inputUpdated(mapRef2RefFlowFunction_35);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_36()) {
          isDirty_mapRef2RefFlowFunction_36 = mapRef2RefFlowFunction_36.map();
          if (isDirty_mapRef2RefFlowFunction_36) {
            globalNetMtm.inputUpdated(mapRef2RefFlowFunction_36);
          }
        }
        if (guardCheck_globalNetMtm()) {
          isDirty_globalNetMtm = globalNetMtm.map();
          if (isDirty_globalNetMtm) {
            pushFlowFunction_38.inputUpdated(globalNetMtm);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_42()) {
          isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
          if (isDirty_mapRef2RefFlowFunction_42) {
            mapRef2RefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_42);
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
            binaryMapToRefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_48()) {
          isDirty_binaryMapToRefFlowFunction_48 = binaryMapToRefFlowFunction_48.map();
          if (isDirty_binaryMapToRefFlowFunction_48) {
            mapRef2RefFlowFunction_50.inputUpdated(binaryMapToRefFlowFunction_48);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_50()) {
          isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
          if (isDirty_mapRef2RefFlowFunction_50) {
            instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_50);
          }
        }
        if (guardCheck_instrumentNetMtm()) {
          isDirty_instrumentNetMtm = instrumentNetMtm.map();
          if (isDirty_instrumentNetMtm) {
            pushFlowFunction_52.inputUpdated(instrumentNetMtm);
          }
        }
        if (guardCheck_pushFlowFunction_38()) {
          isDirty_pushFlowFunction_38 = pushFlowFunction_38.push();
        }
        if (guardCheck_pushFlowFunction_52()) {
          isDirty_pushFlowFunction_52 = pushFlowFunction_52.push();
        }
        afterEvent();
        return;
        //Event Class:[com.fluxtion.runtime.event.Signal] filterString:[positionUpdate]
      case ("positionUpdate"):
        isDirty_handlerSignal_positionUpdate = handlerSignal_positionUpdate.onEvent(typedEvent);
        if (isDirty_handlerSignal_positionUpdate) {
          mapRef2RefFlowFunction_16.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
          mapRef2RefFlowFunction_16.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
          mapRef2RefFlowFunction_19.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
          mapRef2RefFlowFunction_25.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
          mapRef2RefFlowFunction_31.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
          mapRef2RefFlowFunction_35.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
          mapRef2RefFlowFunction_46.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
          mapRef2RefFlowFunction_46.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
          mapRef2RefFlowFunction_50.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
        }
        if (guardCheck_mapRef2RefFlowFunction_16()) {
          isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
          if (isDirty_mapRef2RefFlowFunction_16) {
            binaryMapToRefFlowFunction_33.input2Updated(mapRef2RefFlowFunction_16);
            binaryMapToRefFlowFunction_48.input2Updated(mapRef2RefFlowFunction_16);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_19()) {
          isDirty_mapRef2RefFlowFunction_19 = mapRef2RefFlowFunction_19.map();
          if (isDirty_mapRef2RefFlowFunction_19) {
            binaryMapToRefFlowFunction_27.input2Updated(mapRef2RefFlowFunction_19);
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
            binaryMapToRefFlowFunction_33.inputUpdated(mapRef2RefFlowFunction_31);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_33()) {
          isDirty_binaryMapToRefFlowFunction_33 = binaryMapToRefFlowFunction_33.map();
          if (isDirty_binaryMapToRefFlowFunction_33) {
            mapRef2RefFlowFunction_35.inputUpdated(binaryMapToRefFlowFunction_33);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_35()) {
          isDirty_mapRef2RefFlowFunction_35 = mapRef2RefFlowFunction_35.map();
          if (isDirty_mapRef2RefFlowFunction_35) {
            mapRef2RefFlowFunction_36.inputUpdated(mapRef2RefFlowFunction_35);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_36()) {
          isDirty_mapRef2RefFlowFunction_36 = mapRef2RefFlowFunction_36.map();
          if (isDirty_mapRef2RefFlowFunction_36) {
            globalNetMtm.inputUpdated(mapRef2RefFlowFunction_36);
          }
        }
        if (guardCheck_globalNetMtm()) {
          isDirty_globalNetMtm = globalNetMtm.map();
          if (isDirty_globalNetMtm) {
            pushFlowFunction_38.inputUpdated(globalNetMtm);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_46()) {
          isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
          if (isDirty_mapRef2RefFlowFunction_46) {
            binaryMapToRefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_48()) {
          isDirty_binaryMapToRefFlowFunction_48 = binaryMapToRefFlowFunction_48.map();
          if (isDirty_binaryMapToRefFlowFunction_48) {
            mapRef2RefFlowFunction_50.inputUpdated(binaryMapToRefFlowFunction_48);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_50()) {
          isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
          if (isDirty_mapRef2RefFlowFunction_50) {
            instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_50);
          }
        }
        if (guardCheck_instrumentNetMtm()) {
          isDirty_instrumentNetMtm = instrumentNetMtm.map();
          if (isDirty_instrumentNetMtm) {
            pushFlowFunction_52.inputUpdated(instrumentNetMtm);
          }
        }
        if (guardCheck_pushFlowFunction_38()) {
          isDirty_pushFlowFunction_38 = pushFlowFunction_38.push();
        }
        if (guardCheck_pushFlowFunction_52()) {
          isDirty_pushFlowFunction_52 = pushFlowFunction_52.push();
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
      flatMapFlowFunction_17.inputUpdatedAndFlatMap(handlerPositionSnapshot);
    }
    afterEvent();
  }

  public void handleEvent(Trade typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_handlerTrade = handlerTrade.onEvent(typedEvent);
    if (isDirty_handlerTrade) {
      mapRef2RefFlowFunction_12.inputUpdated(handlerTrade);
      mergeFlowFunction_2.inputStreamUpdated(handlerTrade);
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        mapRef2RefFlowFunction_14.inputUpdated(mapRef2RefFlowFunction_12);
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
        binaryMapToRefFlowFunction_33.input2Updated(mapRef2RefFlowFunction_16);
        binaryMapToRefFlowFunction_48.input2Updated(mapRef2RefFlowFunction_16);
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
        binaryMapToRefFlowFunction_21.inputUpdated(mapRef2RefFlowFunction_4);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_6()) {
      isDirty_mapRef2RefFlowFunction_6 = mapRef2RefFlowFunction_6.map();
      if (isDirty_mapRef2RefFlowFunction_6) {
        binaryMapToRefFlowFunction_21.input2Updated(mapRef2RefFlowFunction_6);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_21()) {
      isDirty_binaryMapToRefFlowFunction_21 = binaryMapToRefFlowFunction_21.map();
      if (isDirty_binaryMapToRefFlowFunction_21) {
        mapRef2RefFlowFunction_23.inputUpdated(binaryMapToRefFlowFunction_21);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_8()) {
      isDirty_mapRef2RefFlowFunction_8 = mapRef2RefFlowFunction_8.map();
      if (isDirty_mapRef2RefFlowFunction_8) {
        binaryMapToRefFlowFunction_40.inputUpdated(mapRef2RefFlowFunction_8);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_40.input2Updated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_40()) {
      isDirty_binaryMapToRefFlowFunction_40 = binaryMapToRefFlowFunction_40.map();
      if (isDirty_binaryMapToRefFlowFunction_40) {
        mapRef2RefFlowFunction_42.inputUpdated(binaryMapToRefFlowFunction_40);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_23()) {
      isDirty_mapRef2RefFlowFunction_23 = mapRef2RefFlowFunction_23.map();
      if (isDirty_mapRef2RefFlowFunction_23) {
        mapRef2RefFlowFunction_25.inputUpdated(mapRef2RefFlowFunction_23);
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
        binaryMapToRefFlowFunction_33.inputUpdated(mapRef2RefFlowFunction_31);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_33()) {
      isDirty_binaryMapToRefFlowFunction_33 = binaryMapToRefFlowFunction_33.map();
      if (isDirty_binaryMapToRefFlowFunction_33) {
        mapRef2RefFlowFunction_35.inputUpdated(binaryMapToRefFlowFunction_33);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_35()) {
      isDirty_mapRef2RefFlowFunction_35 = mapRef2RefFlowFunction_35.map();
      if (isDirty_mapRef2RefFlowFunction_35) {
        mapRef2RefFlowFunction_36.inputUpdated(mapRef2RefFlowFunction_35);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_36()) {
      isDirty_mapRef2RefFlowFunction_36 = mapRef2RefFlowFunction_36.map();
      if (isDirty_mapRef2RefFlowFunction_36) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_36);
      }
    }
    if (guardCheck_globalNetMtm()) {
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_38.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_42()) {
      isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
      if (isDirty_mapRef2RefFlowFunction_42) {
        mapRef2RefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_42);
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
        binaryMapToRefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_48()) {
      isDirty_binaryMapToRefFlowFunction_48 = binaryMapToRefFlowFunction_48.map();
      if (isDirty_binaryMapToRefFlowFunction_48) {
        mapRef2RefFlowFunction_50.inputUpdated(binaryMapToRefFlowFunction_48);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_50()) {
      isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
      if (isDirty_mapRef2RefFlowFunction_50) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_50);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_52.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_38()) {
      isDirty_pushFlowFunction_38 = pushFlowFunction_38.push();
    }
    if (guardCheck_pushFlowFunction_52()) {
      isDirty_pushFlowFunction_52 = pushFlowFunction_52.push();
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
    auditor.nodeRegistered(callbackImpl_53, "callbackImpl_53");
    auditor.nodeRegistered(callbackImpl_67, "callbackImpl_67");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_21, "binaryMapToRefFlowFunction_21");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_27, "binaryMapToRefFlowFunction_27");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_33, "binaryMapToRefFlowFunction_33");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_40, "binaryMapToRefFlowFunction_40");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_48, "binaryMapToRefFlowFunction_48");
    auditor.nodeRegistered(flatMapFlowFunction_1, "flatMapFlowFunction_1");
    auditor.nodeRegistered(flatMapFlowFunction_17, "flatMapFlowFunction_17");
    auditor.nodeRegistered(globalNetMtm, "globalNetMtm");
    auditor.nodeRegistered(instrumentNetMtm, "instrumentNetMtm");
    auditor.nodeRegistered(mapRef2RefFlowFunction_4, "mapRef2RefFlowFunction_4");
    auditor.nodeRegistered(mapRef2RefFlowFunction_6, "mapRef2RefFlowFunction_6");
    auditor.nodeRegistered(mapRef2RefFlowFunction_8, "mapRef2RefFlowFunction_8");
    auditor.nodeRegistered(mapRef2RefFlowFunction_10, "mapRef2RefFlowFunction_10");
    auditor.nodeRegistered(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12");
    auditor.nodeRegistered(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14");
    auditor.nodeRegistered(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16");
    auditor.nodeRegistered(mapRef2RefFlowFunction_19, "mapRef2RefFlowFunction_19");
    auditor.nodeRegistered(mapRef2RefFlowFunction_23, "mapRef2RefFlowFunction_23");
    auditor.nodeRegistered(mapRef2RefFlowFunction_25, "mapRef2RefFlowFunction_25");
    auditor.nodeRegistered(mapRef2RefFlowFunction_29, "mapRef2RefFlowFunction_29");
    auditor.nodeRegistered(mapRef2RefFlowFunction_31, "mapRef2RefFlowFunction_31");
    auditor.nodeRegistered(mapRef2RefFlowFunction_35, "mapRef2RefFlowFunction_35");
    auditor.nodeRegistered(mapRef2RefFlowFunction_36, "mapRef2RefFlowFunction_36");
    auditor.nodeRegistered(mapRef2RefFlowFunction_42, "mapRef2RefFlowFunction_42");
    auditor.nodeRegistered(mapRef2RefFlowFunction_44, "mapRef2RefFlowFunction_44");
    auditor.nodeRegistered(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46");
    auditor.nodeRegistered(mapRef2RefFlowFunction_50, "mapRef2RefFlowFunction_50");
    auditor.nodeRegistered(mergeFlowFunction_2, "mergeFlowFunction_2");
    auditor.nodeRegistered(pushFlowFunction_38, "pushFlowFunction_38");
    auditor.nodeRegistered(pushFlowFunction_52, "pushFlowFunction_52");
    auditor.nodeRegistered(emptyGroupBy_59, "emptyGroupBy_59");
    auditor.nodeRegistered(emptyGroupBy_107, "emptyGroupBy_107");
    auditor.nodeRegistered(emptyGroupBy_231, "emptyGroupBy_231");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_3, "groupByFlowFunctionWrapper_3");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_5, "groupByFlowFunctionWrapper_5");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_7, "groupByFlowFunctionWrapper_7");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_9, "groupByFlowFunctionWrapper_9");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_11, "groupByFlowFunctionWrapper_11");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_18, "groupByFlowFunctionWrapper_18");
    auditor.nodeRegistered(groupByMapFlowFunction_15, "groupByMapFlowFunction_15");
    auditor.nodeRegistered(groupByMapFlowFunction_22, "groupByMapFlowFunction_22");
    auditor.nodeRegistered(groupByMapFlowFunction_24, "groupByMapFlowFunction_24");
    auditor.nodeRegistered(groupByMapFlowFunction_28, "groupByMapFlowFunction_28");
    auditor.nodeRegistered(groupByMapFlowFunction_34, "groupByMapFlowFunction_34");
    auditor.nodeRegistered(groupByMapFlowFunction_41, "groupByMapFlowFunction_41");
    auditor.nodeRegistered(groupByMapFlowFunction_43, "groupByMapFlowFunction_43");
    auditor.nodeRegistered(groupByMapFlowFunction_49, "groupByMapFlowFunction_49");
    auditor.nodeRegistered(leftJoin_32, "leftJoin_32");
    auditor.nodeRegistered(leftJoin_47, "leftJoin_47");
    auditor.nodeRegistered(outerJoin_20, "outerJoin_20");
    auditor.nodeRegistered(outerJoin_26, "outerJoin_26");
    auditor.nodeRegistered(outerJoin_39, "outerJoin_39");
    auditor.nodeRegistered(defaultValue_13, "defaultValue_13");
    auditor.nodeRegistered(defaultValue_30, "defaultValue_30");
    auditor.nodeRegistered(defaultValue_45, "defaultValue_45");
    auditor.nodeRegistered(mapTuple_196, "mapTuple_196");
    auditor.nodeRegistered(mapTuple_204, "mapTuple_204");
    auditor.nodeRegistered(mapTuple_210, "mapTuple_210");
    auditor.nodeRegistered(mapTuple_285, "mapTuple_285");
    auditor.nodeRegistered(mapTuple_295, "mapTuple_295");
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
    isDirty_binaryMapToRefFlowFunction_21 = false;
    isDirty_binaryMapToRefFlowFunction_27 = false;
    isDirty_binaryMapToRefFlowFunction_33 = false;
    isDirty_binaryMapToRefFlowFunction_40 = false;
    isDirty_binaryMapToRefFlowFunction_48 = false;
    isDirty_callbackImpl_53 = false;
    isDirty_callbackImpl_67 = false;
    isDirty_clock = false;
    isDirty_derivedRateNode = false;
    isDirty_flatMapFlowFunction_1 = false;
    isDirty_flatMapFlowFunction_17 = false;
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
    isDirty_mapRef2RefFlowFunction_12 = false;
    isDirty_mapRef2RefFlowFunction_14 = false;
    isDirty_mapRef2RefFlowFunction_16 = false;
    isDirty_mapRef2RefFlowFunction_19 = false;
    isDirty_mapRef2RefFlowFunction_23 = false;
    isDirty_mapRef2RefFlowFunction_25 = false;
    isDirty_mapRef2RefFlowFunction_29 = false;
    isDirty_mapRef2RefFlowFunction_31 = false;
    isDirty_mapRef2RefFlowFunction_35 = false;
    isDirty_mapRef2RefFlowFunction_36 = false;
    isDirty_mapRef2RefFlowFunction_42 = false;
    isDirty_mapRef2RefFlowFunction_44 = false;
    isDirty_mapRef2RefFlowFunction_46 = false;
    isDirty_mapRef2RefFlowFunction_50 = false;
    isDirty_mergeFlowFunction_2 = false;
    isDirty_pushFlowFunction_38 = false;
    isDirty_pushFlowFunction_52 = false;
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
          binaryMapToRefFlowFunction_33, () -> isDirty_binaryMapToRefFlowFunction_33);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_40, () -> isDirty_binaryMapToRefFlowFunction_40);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_48, () -> isDirty_binaryMapToRefFlowFunction_48);
      dirtyFlagSupplierMap.put(callbackImpl_53, () -> isDirty_callbackImpl_53);
      dirtyFlagSupplierMap.put(callbackImpl_67, () -> isDirty_callbackImpl_67);
      dirtyFlagSupplierMap.put(clock, () -> isDirty_clock);
      dirtyFlagSupplierMap.put(derivedRateNode, () -> isDirty_derivedRateNode);
      dirtyFlagSupplierMap.put(flatMapFlowFunction_1, () -> isDirty_flatMapFlowFunction_1);
      dirtyFlagSupplierMap.put(flatMapFlowFunction_17, () -> isDirty_flatMapFlowFunction_17);
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
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_12, () -> isDirty_mapRef2RefFlowFunction_12);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_14, () -> isDirty_mapRef2RefFlowFunction_14);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_16, () -> isDirty_mapRef2RefFlowFunction_16);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_19, () -> isDirty_mapRef2RefFlowFunction_19);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_23, () -> isDirty_mapRef2RefFlowFunction_23);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_25, () -> isDirty_mapRef2RefFlowFunction_25);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_29, () -> isDirty_mapRef2RefFlowFunction_29);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_31, () -> isDirty_mapRef2RefFlowFunction_31);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_35, () -> isDirty_mapRef2RefFlowFunction_35);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_36, () -> isDirty_mapRef2RefFlowFunction_36);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_4, () -> isDirty_mapRef2RefFlowFunction_4);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_42, () -> isDirty_mapRef2RefFlowFunction_42);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_44, () -> isDirty_mapRef2RefFlowFunction_44);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_46, () -> isDirty_mapRef2RefFlowFunction_46);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_50, () -> isDirty_mapRef2RefFlowFunction_50);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_6, () -> isDirty_mapRef2RefFlowFunction_6);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_8, () -> isDirty_mapRef2RefFlowFunction_8);
      dirtyFlagSupplierMap.put(mergeFlowFunction_2, () -> isDirty_mergeFlowFunction_2);
      dirtyFlagSupplierMap.put(pushFlowFunction_38, () -> isDirty_pushFlowFunction_38);
      dirtyFlagSupplierMap.put(pushFlowFunction_52, () -> isDirty_pushFlowFunction_52);
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
          binaryMapToRefFlowFunction_33, (b) -> isDirty_binaryMapToRefFlowFunction_33 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_40, (b) -> isDirty_binaryMapToRefFlowFunction_40 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_48, (b) -> isDirty_binaryMapToRefFlowFunction_48 = b);
      dirtyFlagUpdateMap.put(callbackImpl_53, (b) -> isDirty_callbackImpl_53 = b);
      dirtyFlagUpdateMap.put(callbackImpl_67, (b) -> isDirty_callbackImpl_67 = b);
      dirtyFlagUpdateMap.put(clock, (b) -> isDirty_clock = b);
      dirtyFlagUpdateMap.put(derivedRateNode, (b) -> isDirty_derivedRateNode = b);
      dirtyFlagUpdateMap.put(flatMapFlowFunction_1, (b) -> isDirty_flatMapFlowFunction_1 = b);
      dirtyFlagUpdateMap.put(flatMapFlowFunction_17, (b) -> isDirty_flatMapFlowFunction_17 = b);
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
          mapRef2RefFlowFunction_12, (b) -> isDirty_mapRef2RefFlowFunction_12 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_14, (b) -> isDirty_mapRef2RefFlowFunction_14 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_16, (b) -> isDirty_mapRef2RefFlowFunction_16 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_19, (b) -> isDirty_mapRef2RefFlowFunction_19 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_23, (b) -> isDirty_mapRef2RefFlowFunction_23 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_25, (b) -> isDirty_mapRef2RefFlowFunction_25 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_29, (b) -> isDirty_mapRef2RefFlowFunction_29 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_31, (b) -> isDirty_mapRef2RefFlowFunction_31 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_35, (b) -> isDirty_mapRef2RefFlowFunction_35 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_36, (b) -> isDirty_mapRef2RefFlowFunction_36 = b);
      dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_4, (b) -> isDirty_mapRef2RefFlowFunction_4 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_42, (b) -> isDirty_mapRef2RefFlowFunction_42 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_44, (b) -> isDirty_mapRef2RefFlowFunction_44 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_46, (b) -> isDirty_mapRef2RefFlowFunction_46 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_50, (b) -> isDirty_mapRef2RefFlowFunction_50 = b);
      dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_6, (b) -> isDirty_mapRef2RefFlowFunction_6 = b);
      dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_8, (b) -> isDirty_mapRef2RefFlowFunction_8 = b);
      dirtyFlagUpdateMap.put(mergeFlowFunction_2, (b) -> isDirty_mergeFlowFunction_2 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_38, (b) -> isDirty_pushFlowFunction_38 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_52, (b) -> isDirty_pushFlowFunction_52 = b);
    }
    dirtyFlagUpdateMap.get(node).accept(dirtyFlag);
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_21() {
    return isDirty_mapRef2RefFlowFunction_4 | isDirty_mapRef2RefFlowFunction_6;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_27() {
    return isDirty_mapRef2RefFlowFunction_19 | isDirty_mapRef2RefFlowFunction_25;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_33() {
    return isDirty_mapRef2RefFlowFunction_16 | isDirty_mapRef2RefFlowFunction_31;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_40() {
    return isDirty_mapRef2RefFlowFunction_8 | isDirty_mapRef2RefFlowFunction_10;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_48() {
    return isDirty_mapRef2RefFlowFunction_16 | isDirty_mapRef2RefFlowFunction_46;
  }

  private boolean guardCheck_flatMapFlowFunction_1() {
    return isDirty_callbackImpl_53;
  }

  private boolean guardCheck_flatMapFlowFunction_17() {
    return isDirty_callbackImpl_67;
  }

  private boolean guardCheck_globalNetMtm() {
    return isDirty_mapRef2RefFlowFunction_36;
  }

  private boolean guardCheck_instrumentNetMtm() {
    return isDirty_mapRef2RefFlowFunction_50;
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

  private boolean guardCheck_mapRef2RefFlowFunction_12() {
    return isDirty_handlerSignal_positionSnapshotReset | isDirty_handlerTrade;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_14() {
    return isDirty_mapRef2RefFlowFunction_12;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_16() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_14;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_19() {
    return isDirty_flatMapFlowFunction_17
        | isDirty_handlerSignal_positionSnapshotReset
        | isDirty_handlerSignal_positionUpdate;
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

  private boolean guardCheck_mapRef2RefFlowFunction_35() {
    return isDirty_binaryMapToRefFlowFunction_33 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_36() {
    return isDirty_mapRef2RefFlowFunction_35;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_42() {
    return isDirty_binaryMapToRefFlowFunction_40;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_44() {
    return isDirty_mapRef2RefFlowFunction_42;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_46() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_44;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_50() {
    return isDirty_binaryMapToRefFlowFunction_48 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mergeFlowFunction_2() {
    return isDirty_flatMapFlowFunction_1 | isDirty_handlerTrade;
  }

  private boolean guardCheck_pushFlowFunction_38() {
    return isDirty_globalNetMtm;
  }

  private boolean guardCheck_pushFlowFunction_52() {
    return isDirty_instrumentNetMtm;
  }

  private boolean guardCheck_groupByMapFlowFunction_15() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_groupByMapFlowFunction_24() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_groupByMapFlowFunction_43() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_context() {
    return isDirty_clock;
  }

  private boolean guardCheck_globalNetMtmListener() {
    return isDirty_pushFlowFunction_38;
  }

  private boolean guardCheck_instrumentNetMtmListener() {
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
