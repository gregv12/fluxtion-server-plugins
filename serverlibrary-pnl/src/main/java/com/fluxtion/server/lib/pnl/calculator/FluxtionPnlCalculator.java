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
import com.fluxtion.runtime.dataflow.aggregate.function.primitive.DoubleSumFlowFunction;
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
import com.fluxtion.runtime.dataflow.groupby.GroupByReduceFlowFunction;
import com.fluxtion.runtime.dataflow.groupby.LeftJoin;
import com.fluxtion.runtime.dataflow.groupby.OuterJoin;
import com.fluxtion.runtime.dataflow.helpers.DefaultValue;
import com.fluxtion.runtime.dataflow.helpers.Mappers;
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
import com.fluxtion.server.lib.pnl.InstrumentPosition;
import com.fluxtion.server.lib.pnl.MathUtil;
import com.fluxtion.server.lib.pnl.MidPrice;
import com.fluxtion.server.lib.pnl.MtmInstrument;
import com.fluxtion.server.lib.pnl.PositionSnapshot;
import com.fluxtion.server.lib.pnl.Trade;
import com.fluxtion.server.lib.pnl.TradeBatch;
import com.fluxtion.server.lib.pnl.refdata.Instrument;
import com.fluxtion.server.lib.pnl.refdata.SymbolLookup;
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
 * eventProcessorGenerator version : 9.3.43
 * api version                     : 9.3.43
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
 *   <li>com.fluxtion.server.lib.pnl.refdata.SymbolLookup
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
  private final CallbackImpl callbackImpl_74 = new CallbackImpl<>(1, callbackDispatcher);
  private final CallbackImpl callbackImpl_75 = new CallbackImpl<>(2, callbackDispatcher);
  public final Clock clock = new Clock();
  private final DerivedRateNode derivedRateNode_27 = new DerivedRateNode();
  private final Double double_220 = Double.NaN;
  private final DefaultValue defaultValue_40 = new DefaultValue<>(Double.NaN);
  private final Double double_302 = 0.0d;
  private final DefaultValue defaultValue_48 = new DefaultValue<>(0.0);
  private final DoubleSumFlowFunction doubleSumFlowFunction_194 = new DoubleSumFlowFunction();
  private final DoubleSumFlowFunction doubleSumFlowFunction_282 = new DoubleSumFlowFunction();
  private final EmptyGroupBy emptyGroupBy_99 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_18 = new DefaultValue<>(emptyGroupBy_99);
  private final EmptyGroupBy emptyGroupBy_109 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_25 = new DefaultValue<>(emptyGroupBy_109);
  private final EmptyGroupBy emptyGroupBy_132 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_32 = new DefaultValue<>(emptyGroupBy_132);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_4 =
      new GroupByFlowFunctionWrapper<>(
          InstrumentPosition::instrument,
          InstrumentPosition::position,
          AggregateIdentityFlowFunction::new);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_6 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Trade::getDealtVolume, DoubleSumFlowFunction::new);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_8 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getContraInstrument, Trade::getContraVolume, DoubleSumFlowFunction::new);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_23 =
      new GroupByFlowFunctionWrapper<>(
          InstrumentPosition::instrument, InstrumentPosition::position, DoubleSumFlowFunction::new);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_29 =
      new GroupByFlowFunctionWrapper<>(
          derivedRateNode_27::getMtmContraInstrument,
          derivedRateNode_27::getMtMRate,
          AggregateIdentityFlowFunction::new);
  private final GroupByMapFlowFunction groupByMapFlowFunction_12 =
      new GroupByMapFlowFunction(MathUtil::addPositions);
  private final GroupByMapFlowFunction groupByMapFlowFunction_16 =
      new GroupByMapFlowFunction(MathUtil::addPositions);
  private final GroupByMapFlowFunction groupByMapFlowFunction_36 =
      new GroupByMapFlowFunction(MathUtil::mtmPositions);
  private final GroupByMapFlowFunction groupByMapFlowFunction_44 =
      new GroupByMapFlowFunction(MathUtil::mtmPositions);
  private final GroupByMapFlowFunction groupByMapFlowFunction_51 =
      new GroupByMapFlowFunction(Instrument::instrumentName);
  private final GroupByMapFlowFunction groupByMapFlowFunction_55 =
      new GroupByMapFlowFunction(Instrument::instrumentName);
  private final GroupByMapFlowFunction groupByMapFlowFunction_59 =
      new GroupByMapFlowFunction(Instrument::instrumentName);
  private final GroupByMapFlowFunction groupByMapFlowFunction_63 =
      new GroupByMapFlowFunction(Instrument::instrumentName);
  private final GroupByMapFlowFunction groupByMapFlowFunction_67 =
      new GroupByMapFlowFunction(Instrument::instrumentName);
  private final GroupByReduceFlowFunction groupByReduceFlowFunction_38 =
      new GroupByReduceFlowFunction(doubleSumFlowFunction_194);
  private final GroupByReduceFlowFunction groupByReduceFlowFunction_46 =
      new GroupByReduceFlowFunction(doubleSumFlowFunction_282);
  private final LeftJoin leftJoin_34 = new LeftJoin();
  private final LeftJoin leftJoin_42 = new LeftJoin();
  public final NodeNameAuditor nodeNameLookup = new NodeNameAuditor();
  private final OuterJoin outerJoin_10 = new OuterJoin();
  private final OuterJoin outerJoin_14 = new OuterJoin();
  private final SubscriptionManagerNode subscriptionManager = new SubscriptionManagerNode();
  private final MutableEventProcessorContext context =
      new MutableEventProcessorContext(
          nodeNameLookup, callbackDispatcher, subscriptionManager, callbackDispatcher);
  private final SinkPublisher feePositionListener = new SinkPublisher<>("feePositionListener");
  private final DefaultEventHandlerNode handlerMidPrice =
      new DefaultEventHandlerNode<>(
          2147483647, "", com.fluxtion.server.lib.pnl.MidPrice.class, "handlerMidPrice", context);
  private final FilterFlowFunction filterFlowFunction_28 =
      new FilterFlowFunction<>(handlerMidPrice, derivedRateNode_27::isMtmSymbol);
  private final DefaultEventHandlerNode handlerMtmInstrument =
      new DefaultEventHandlerNode<>(
          2147483647,
          "",
          com.fluxtion.server.lib.pnl.MtmInstrument.class,
          "handlerMtmInstrument",
          context);
  private final DefaultEventHandlerNode handlerPositionSnapshot =
      new DefaultEventHandlerNode<>(
          2147483647,
          "",
          com.fluxtion.server.lib.pnl.PositionSnapshot.class,
          "handlerPositionSnapshot",
          context);
  private final FlatMapFlowFunction flatMapFlowFunction_3 =
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
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_5 =
      new MapRef2RefFlowFunction<>(flatMapFlowFunction_3, groupByFlowFunctionWrapper_4::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_21 =
      new MapRef2RefFlowFunction<>(handlerTradeBatch, MathUtil::feePositionBatch);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_30 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_28, groupByFlowFunctionWrapper_29::aggregate);
  private final MergeFlowFunction mergeFlowFunction_2 =
      new MergeFlowFunction<>(Arrays.asList(handlerTrade, flatMapFlowFunction_1));
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_7 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_2, groupByFlowFunctionWrapper_6::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_9 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_2, groupByFlowFunctionWrapper_8::aggregate);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_11 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_7, mapRef2RefFlowFunction_9, outerJoin_10::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_13 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_11, groupByMapFlowFunction_12::mapValues);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_15 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_13, mapRef2RefFlowFunction_5, outerJoin_14::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_17 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_15, groupByMapFlowFunction_16::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_19 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_17, defaultValue_18::getOrDefault);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_31 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_30, mapRef2RefFlowFunction_19, derivedRateNode_27::addDerived);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_20 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_2, MathUtil::feePositionTrade);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_33 =
      new MapRef2RefFlowFunction<>(binaryMapToRefFlowFunction_31, defaultValue_32::getOrDefault);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_35 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_19, mapRef2RefFlowFunction_33, leftJoin_34::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_37 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_35, groupByMapFlowFunction_36::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_39 =
      new MapRef2RefFlowFunction<>(
          mapRef2RefFlowFunction_37, groupByReduceFlowFunction_38::reduceValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_52 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_19, groupByMapFlowFunction_51::mapKeys);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_60 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_33, groupByMapFlowFunction_59::mapKeys);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_64 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_37, groupByMapFlowFunction_63::mapKeys);
  private final MergeFlowFunction mergeFlowFunction_22 =
      new MergeFlowFunction<>(Arrays.asList(mapRef2RefFlowFunction_20, mapRef2RefFlowFunction_21));
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_24 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_22, groupByFlowFunctionWrapper_23::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_26 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_24, defaultValue_25::getOrDefault);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_43 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_26, mapRef2RefFlowFunction_33, leftJoin_42::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_45 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_43, groupByMapFlowFunction_44::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_47 =
      new MapRef2RefFlowFunction<>(
          mapRef2RefFlowFunction_45, groupByReduceFlowFunction_46::reduceValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_56 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_26, groupByMapFlowFunction_55::mapKeys);
  public final MapRef2RefFlowFunction feePositionMap =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_56, GroupBy<Object, Object>::toMap);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_68 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_45, groupByMapFlowFunction_67::mapKeys);
  private final SinkPublisher mtmFeePositionListener =
      new SinkPublisher<>("mtmFeePositionListener");
  public final MapRef2RefFlowFunction mtmFeePositionMap =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_68, GroupBy<Object, Object>::toMap);
  private final SinkPublisher mtmPositionListener = new SinkPublisher<>("mtmPositionListener");
  public final MapRef2RefFlowFunction mtmPositionMap =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_64, GroupBy<Object, Object>::toMap);
  private final SinkPublisher netPnlListener = new SinkPublisher<>("netPnlListener");
  public final MapRef2RefFlowFunction pnl =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_39, defaultValue_40::getOrDefault);
  private final SinkPublisher pnlListener = new SinkPublisher<>("pnlListener");
  private final SinkPublisher positionListener = new SinkPublisher<>("positionListener");
  public final MapRef2RefFlowFunction positionMap =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_52, GroupBy<Object, Object>::toMap);
  private final PushFlowFunction pushFlowFunction_54 =
      new PushFlowFunction<>(positionMap, positionListener::publish);
  private final PushFlowFunction pushFlowFunction_58 =
      new PushFlowFunction<>(feePositionMap, feePositionListener::publish);
  private final PushFlowFunction pushFlowFunction_66 =
      new PushFlowFunction<>(mtmPositionMap, mtmPositionListener::publish);
  private final PushFlowFunction pushFlowFunction_70 =
      new PushFlowFunction<>(mtmFeePositionMap, mtmFeePositionListener::publish);
  private final PushFlowFunction pushFlowFunction_72 =
      new PushFlowFunction<>(pnl, pnlListener::publish);
  private final SinkPublisher rateListener = new SinkPublisher<>("rateListener");
  public final MapRef2RefFlowFunction rates =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_60, GroupBy<Object, Object>::toMap);
  private final PushFlowFunction pushFlowFunction_62 =
      new PushFlowFunction<>(rates, rateListener::publish);
  public final ServiceRegistryNode serviceRegistry = new ServiceRegistryNode();
  public final MapRef2RefFlowFunction tradeFees =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_47, defaultValue_48::getOrDefault);
  public final BinaryMapToRefFlowFunction netPnl =
      new BinaryMapToRefFlowFunction<>(pnl, tradeFees, Mappers::subtractDoubles);
  private final PushFlowFunction pushFlowFunction_73 =
      new PushFlowFunction<>(netPnl, netPnlListener::publish);
  private final SinkPublisher tradeFeesListener = new SinkPublisher<>("tradeFeesListener");
  private final PushFlowFunction pushFlowFunction_71 =
      new PushFlowFunction<>(tradeFees, tradeFeesListener::publish);
  private final SymbolLookupNode symbolLookupNode_0 = new SymbolLookupNode();
  private final ExportFunctionAuditEvent functionAudit = new ExportFunctionAuditEvent();
  //Dirty flags
  private boolean initCalled = false;
  private boolean processing = false;
  private boolean buffering = false;
  private final IdentityHashMap<Object, BooleanSupplier> dirtyFlagSupplierMap =
      new IdentityHashMap<>(58);
  private final IdentityHashMap<Object, Consumer<Boolean>> dirtyFlagUpdateMap =
      new IdentityHashMap<>(58);

  private boolean isDirty_binaryMapToRefFlowFunction_11 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_15 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_31 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_35 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_43 = false;
  private boolean isDirty_callbackImpl_74 = false;
  private boolean isDirty_callbackImpl_75 = false;
  private boolean isDirty_clock = false;
  private boolean isDirty_derivedRateNode_27 = false;
  private boolean isDirty_feePositionMap = false;
  private boolean isDirty_filterFlowFunction_28 = false;
  private boolean isDirty_flatMapFlowFunction_1 = false;
  private boolean isDirty_flatMapFlowFunction_3 = false;
  private boolean isDirty_handlerMidPrice = false;
  private boolean isDirty_handlerMtmInstrument = false;
  private boolean isDirty_handlerPositionSnapshot = false;
  private boolean isDirty_handlerSignal_positionSnapshotReset = false;
  private boolean isDirty_handlerSignal_positionUpdate = false;
  private boolean isDirty_handlerTrade = false;
  private boolean isDirty_handlerTradeBatch = false;
  private boolean isDirty_mapRef2RefFlowFunction_5 = false;
  private boolean isDirty_mapRef2RefFlowFunction_7 = false;
  private boolean isDirty_mapRef2RefFlowFunction_9 = false;
  private boolean isDirty_mapRef2RefFlowFunction_13 = false;
  private boolean isDirty_mapRef2RefFlowFunction_17 = false;
  private boolean isDirty_mapRef2RefFlowFunction_19 = false;
  private boolean isDirty_mapRef2RefFlowFunction_20 = false;
  private boolean isDirty_mapRef2RefFlowFunction_21 = false;
  private boolean isDirty_mapRef2RefFlowFunction_24 = false;
  private boolean isDirty_mapRef2RefFlowFunction_26 = false;
  private boolean isDirty_mapRef2RefFlowFunction_30 = false;
  private boolean isDirty_mapRef2RefFlowFunction_33 = false;
  private boolean isDirty_mapRef2RefFlowFunction_37 = false;
  private boolean isDirty_mapRef2RefFlowFunction_39 = false;
  private boolean isDirty_mapRef2RefFlowFunction_45 = false;
  private boolean isDirty_mapRef2RefFlowFunction_47 = false;
  private boolean isDirty_mapRef2RefFlowFunction_52 = false;
  private boolean isDirty_mapRef2RefFlowFunction_56 = false;
  private boolean isDirty_mapRef2RefFlowFunction_60 = false;
  private boolean isDirty_mapRef2RefFlowFunction_64 = false;
  private boolean isDirty_mapRef2RefFlowFunction_68 = false;
  private boolean isDirty_mergeFlowFunction_2 = false;
  private boolean isDirty_mergeFlowFunction_22 = false;
  private boolean isDirty_mtmFeePositionMap = false;
  private boolean isDirty_mtmPositionMap = false;
  private boolean isDirty_netPnl = false;
  private boolean isDirty_pnl = false;
  private boolean isDirty_positionMap = false;
  private boolean isDirty_pushFlowFunction_54 = false;
  private boolean isDirty_pushFlowFunction_58 = false;
  private boolean isDirty_pushFlowFunction_62 = false;
  private boolean isDirty_pushFlowFunction_66 = false;
  private boolean isDirty_pushFlowFunction_70 = false;
  private boolean isDirty_pushFlowFunction_71 = false;
  private boolean isDirty_pushFlowFunction_72 = false;
  private boolean isDirty_pushFlowFunction_73 = false;
  private boolean isDirty_rates = false;
  private boolean isDirty_tradeFees = false;

  //Forked declarations

  //Filter constants

  //unknown event handler
  private Consumer unKnownEventHandler = (e) -> {};

  public FluxtionPnlCalculator(Map<Object, Object> contextMap) {
    if (context != null) {
      context.replaceMappings(contextMap);
    }
    callbackImpl_74.dirtyStateMonitor = callbackDispatcher;
    callbackImpl_75.dirtyStateMonitor = callbackDispatcher;
    doubleSumFlowFunction_194.dirtyStateMonitor = callbackDispatcher;
    doubleSumFlowFunction_282.dirtyStateMonitor = callbackDispatcher;
    binaryMapToRefFlowFunction_11.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_11.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    binaryMapToRefFlowFunction_15.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_31.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_35.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_43.setEventProcessorContext(context);
    netPnl.setEventProcessorContext(context);
    netPnl.setUpdateTriggerNode(handlerSignal_positionUpdate);
    filterFlowFunction_28.setEventProcessorContext(context);
    flatMapFlowFunction_1.callback = callbackImpl_74;
    flatMapFlowFunction_1.dirtyStateMonitor = callbackDispatcher;
    flatMapFlowFunction_3.callback = callbackImpl_75;
    flatMapFlowFunction_3.dirtyStateMonitor = callbackDispatcher;
    feePositionMap.setEventProcessorContext(context);
    mapRef2RefFlowFunction_5.setEventProcessorContext(context);
    mapRef2RefFlowFunction_5.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_5.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_7.setEventProcessorContext(context);
    mapRef2RefFlowFunction_7.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_9.setEventProcessorContext(context);
    mapRef2RefFlowFunction_9.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_13.setEventProcessorContext(context);
    mapRef2RefFlowFunction_17.setEventProcessorContext(context);
    mapRef2RefFlowFunction_19.setEventProcessorContext(context);
    mapRef2RefFlowFunction_19.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_20.setEventProcessorContext(context);
    mapRef2RefFlowFunction_21.setEventProcessorContext(context);
    mapRef2RefFlowFunction_24.setEventProcessorContext(context);
    mapRef2RefFlowFunction_26.setEventProcessorContext(context);
    mapRef2RefFlowFunction_26.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_26.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_30.setEventProcessorContext(context);
    mapRef2RefFlowFunction_30.setResetTriggerNode(handlerMtmInstrument);
    mapRef2RefFlowFunction_33.setEventProcessorContext(context);
    mapRef2RefFlowFunction_37.setEventProcessorContext(context);
    mapRef2RefFlowFunction_37.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_39.setEventProcessorContext(context);
    mapRef2RefFlowFunction_45.setEventProcessorContext(context);
    mapRef2RefFlowFunction_45.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_47.setEventProcessorContext(context);
    mapRef2RefFlowFunction_52.setEventProcessorContext(context);
    mapRef2RefFlowFunction_56.setEventProcessorContext(context);
    mapRef2RefFlowFunction_60.setEventProcessorContext(context);
    mapRef2RefFlowFunction_64.setEventProcessorContext(context);
    mapRef2RefFlowFunction_68.setEventProcessorContext(context);
    mtmFeePositionMap.setEventProcessorContext(context);
    mtmPositionMap.setEventProcessorContext(context);
    pnl.setEventProcessorContext(context);
    pnl.setUpdateTriggerNode(handlerSignal_positionUpdate);
    positionMap.setEventProcessorContext(context);
    rates.setEventProcessorContext(context);
    tradeFees.setEventProcessorContext(context);
    tradeFees.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mergeFlowFunction_2.dirtyStateMonitor = callbackDispatcher;
    mergeFlowFunction_22.dirtyStateMonitor = callbackDispatcher;
    pushFlowFunction_54.setEventProcessorContext(context);
    pushFlowFunction_58.setEventProcessorContext(context);
    pushFlowFunction_62.setEventProcessorContext(context);
    pushFlowFunction_66.setEventProcessorContext(context);
    pushFlowFunction_70.setEventProcessorContext(context);
    pushFlowFunction_71.setEventProcessorContext(context);
    pushFlowFunction_72.setEventProcessorContext(context);
    pushFlowFunction_73.setEventProcessorContext(context);
    context.setClock(clock);
    feePositionListener.setEventProcessorContext(context);
    mtmFeePositionListener.setEventProcessorContext(context);
    mtmPositionListener.setEventProcessorContext(context);
    netPnlListener.setEventProcessorContext(context);
    pnlListener.setEventProcessorContext(context);
    positionListener.setEventProcessorContext(context);
    rateListener.setEventProcessorContext(context);
    tradeFeesListener.setEventProcessorContext(context);
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
    callbackImpl_74.init();
    callbackImpl_75.init();
    clock.init();
    doubleSumFlowFunction_194.init();
    doubleSumFlowFunction_282.init();
    handlerMidPrice.init();
    filterFlowFunction_28.initialiseEventStream();
    handlerMtmInstrument.init();
    handlerPositionSnapshot.init();
    handlerSignal_positionSnapshotReset.init();
    handlerSignal_positionUpdate.init();
    handlerTrade.init();
    handlerTradeBatch.init();
    mapRef2RefFlowFunction_5.initialiseEventStream();
    mapRef2RefFlowFunction_21.initialiseEventStream();
    mapRef2RefFlowFunction_30.initialiseEventStream();
    mapRef2RefFlowFunction_7.initialiseEventStream();
    mapRef2RefFlowFunction_9.initialiseEventStream();
    binaryMapToRefFlowFunction_11.initialiseEventStream();
    mapRef2RefFlowFunction_13.initialiseEventStream();
    binaryMapToRefFlowFunction_15.initialiseEventStream();
    mapRef2RefFlowFunction_17.initialiseEventStream();
    mapRef2RefFlowFunction_19.initialiseEventStream();
    binaryMapToRefFlowFunction_31.initialiseEventStream();
    mapRef2RefFlowFunction_20.initialiseEventStream();
    mapRef2RefFlowFunction_33.initialiseEventStream();
    binaryMapToRefFlowFunction_35.initialiseEventStream();
    mapRef2RefFlowFunction_37.initialiseEventStream();
    mapRef2RefFlowFunction_39.initialiseEventStream();
    mapRef2RefFlowFunction_52.initialiseEventStream();
    mapRef2RefFlowFunction_60.initialiseEventStream();
    mapRef2RefFlowFunction_64.initialiseEventStream();
    mapRef2RefFlowFunction_24.initialiseEventStream();
    mapRef2RefFlowFunction_26.initialiseEventStream();
    binaryMapToRefFlowFunction_43.initialiseEventStream();
    mapRef2RefFlowFunction_45.initialiseEventStream();
    mapRef2RefFlowFunction_47.initialiseEventStream();
    mapRef2RefFlowFunction_56.initialiseEventStream();
    feePositionMap.initialiseEventStream();
    mapRef2RefFlowFunction_68.initialiseEventStream();
    mtmFeePositionMap.initialiseEventStream();
    mtmPositionMap.initialiseEventStream();
    pnl.initialiseEventStream();
    positionMap.initialiseEventStream();
    pushFlowFunction_54.initialiseEventStream();
    pushFlowFunction_58.initialiseEventStream();
    pushFlowFunction_66.initialiseEventStream();
    pushFlowFunction_70.initialiseEventStream();
    pushFlowFunction_72.initialiseEventStream();
    rates.initialiseEventStream();
    pushFlowFunction_62.initialiseEventStream();
    tradeFees.initialiseEventStream();
    netPnl.initialiseEventStream();
    pushFlowFunction_73.initialiseEventStream();
    pushFlowFunction_71.initialiseEventStream();
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
    handlerMtmInstrument.tearDown();
    handlerMidPrice.tearDown();
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
    } else if (event instanceof com.fluxtion.server.lib.pnl.refdata.SymbolLookup) {
      SymbolLookup typedEvent = (SymbolLookup) event;
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
        isDirty_callbackImpl_74 = callbackImpl_74.onEvent(typedEvent);
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
            mapRef2RefFlowFunction_7.inputUpdated(mergeFlowFunction_2);
            mapRef2RefFlowFunction_9.inputUpdated(mergeFlowFunction_2);
            mapRef2RefFlowFunction_20.inputUpdated(mergeFlowFunction_2);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_7()) {
          isDirty_mapRef2RefFlowFunction_7 = mapRef2RefFlowFunction_7.map();
          if (isDirty_mapRef2RefFlowFunction_7) {
            binaryMapToRefFlowFunction_11.inputUpdated(mapRef2RefFlowFunction_7);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_9()) {
          isDirty_mapRef2RefFlowFunction_9 = mapRef2RefFlowFunction_9.map();
          if (isDirty_mapRef2RefFlowFunction_9) {
            binaryMapToRefFlowFunction_11.input2Updated(mapRef2RefFlowFunction_9);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_11()) {
          isDirty_binaryMapToRefFlowFunction_11 = binaryMapToRefFlowFunction_11.map();
          if (isDirty_binaryMapToRefFlowFunction_11) {
            mapRef2RefFlowFunction_13.inputUpdated(binaryMapToRefFlowFunction_11);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_13()) {
          isDirty_mapRef2RefFlowFunction_13 = mapRef2RefFlowFunction_13.map();
          if (isDirty_mapRef2RefFlowFunction_13) {
            binaryMapToRefFlowFunction_15.inputUpdated(mapRef2RefFlowFunction_13);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_15()) {
          isDirty_binaryMapToRefFlowFunction_15 = binaryMapToRefFlowFunction_15.map();
          if (isDirty_binaryMapToRefFlowFunction_15) {
            mapRef2RefFlowFunction_17.inputUpdated(binaryMapToRefFlowFunction_15);
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
            binaryMapToRefFlowFunction_31.input2Updated(mapRef2RefFlowFunction_19);
            binaryMapToRefFlowFunction_35.inputUpdated(mapRef2RefFlowFunction_19);
            mapRef2RefFlowFunction_52.inputUpdated(mapRef2RefFlowFunction_19);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_31()) {
          isDirty_binaryMapToRefFlowFunction_31 = binaryMapToRefFlowFunction_31.map();
          if (isDirty_binaryMapToRefFlowFunction_31) {
            mapRef2RefFlowFunction_33.inputUpdated(binaryMapToRefFlowFunction_31);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_20()) {
          isDirty_mapRef2RefFlowFunction_20 = mapRef2RefFlowFunction_20.map();
          if (isDirty_mapRef2RefFlowFunction_20) {
            mergeFlowFunction_22.inputStreamUpdated(mapRef2RefFlowFunction_20);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_33()) {
          isDirty_mapRef2RefFlowFunction_33 = mapRef2RefFlowFunction_33.map();
          if (isDirty_mapRef2RefFlowFunction_33) {
            binaryMapToRefFlowFunction_35.input2Updated(mapRef2RefFlowFunction_33);
            mapRef2RefFlowFunction_60.inputUpdated(mapRef2RefFlowFunction_33);
            binaryMapToRefFlowFunction_43.input2Updated(mapRef2RefFlowFunction_33);
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
            mapRef2RefFlowFunction_39.inputUpdated(mapRef2RefFlowFunction_37);
            mapRef2RefFlowFunction_64.inputUpdated(mapRef2RefFlowFunction_37);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_39()) {
          isDirty_mapRef2RefFlowFunction_39 = mapRef2RefFlowFunction_39.map();
          if (isDirty_mapRef2RefFlowFunction_39) {
            pnl.inputUpdated(mapRef2RefFlowFunction_39);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_52()) {
          isDirty_mapRef2RefFlowFunction_52 = mapRef2RefFlowFunction_52.map();
          if (isDirty_mapRef2RefFlowFunction_52) {
            positionMap.inputUpdated(mapRef2RefFlowFunction_52);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_60()) {
          isDirty_mapRef2RefFlowFunction_60 = mapRef2RefFlowFunction_60.map();
          if (isDirty_mapRef2RefFlowFunction_60) {
            rates.inputUpdated(mapRef2RefFlowFunction_60);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_64()) {
          isDirty_mapRef2RefFlowFunction_64 = mapRef2RefFlowFunction_64.map();
          if (isDirty_mapRef2RefFlowFunction_64) {
            mtmPositionMap.inputUpdated(mapRef2RefFlowFunction_64);
          }
        }
        if (guardCheck_mergeFlowFunction_22()) {
          isDirty_mergeFlowFunction_22 = mergeFlowFunction_22.publishMerge();
          if (isDirty_mergeFlowFunction_22) {
            mapRef2RefFlowFunction_24.inputUpdated(mergeFlowFunction_22);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_24()) {
          isDirty_mapRef2RefFlowFunction_24 = mapRef2RefFlowFunction_24.map();
          if (isDirty_mapRef2RefFlowFunction_24) {
            mapRef2RefFlowFunction_26.inputUpdated(mapRef2RefFlowFunction_24);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_26()) {
          isDirty_mapRef2RefFlowFunction_26 = mapRef2RefFlowFunction_26.map();
          if (isDirty_mapRef2RefFlowFunction_26) {
            binaryMapToRefFlowFunction_43.inputUpdated(mapRef2RefFlowFunction_26);
            mapRef2RefFlowFunction_56.inputUpdated(mapRef2RefFlowFunction_26);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_43()) {
          isDirty_binaryMapToRefFlowFunction_43 = binaryMapToRefFlowFunction_43.map();
          if (isDirty_binaryMapToRefFlowFunction_43) {
            mapRef2RefFlowFunction_45.inputUpdated(binaryMapToRefFlowFunction_43);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_45()) {
          isDirty_mapRef2RefFlowFunction_45 = mapRef2RefFlowFunction_45.map();
          if (isDirty_mapRef2RefFlowFunction_45) {
            mapRef2RefFlowFunction_47.inputUpdated(mapRef2RefFlowFunction_45);
            mapRef2RefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_45);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_47()) {
          isDirty_mapRef2RefFlowFunction_47 = mapRef2RefFlowFunction_47.map();
          if (isDirty_mapRef2RefFlowFunction_47) {
            tradeFees.inputUpdated(mapRef2RefFlowFunction_47);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_56()) {
          isDirty_mapRef2RefFlowFunction_56 = mapRef2RefFlowFunction_56.map();
          if (isDirty_mapRef2RefFlowFunction_56) {
            feePositionMap.inputUpdated(mapRef2RefFlowFunction_56);
          }
        }
        if (guardCheck_feePositionMap()) {
          isDirty_feePositionMap = feePositionMap.map();
          if (isDirty_feePositionMap) {
            pushFlowFunction_58.inputUpdated(feePositionMap);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_68()) {
          isDirty_mapRef2RefFlowFunction_68 = mapRef2RefFlowFunction_68.map();
          if (isDirty_mapRef2RefFlowFunction_68) {
            mtmFeePositionMap.inputUpdated(mapRef2RefFlowFunction_68);
          }
        }
        if (guardCheck_mtmFeePositionMap()) {
          isDirty_mtmFeePositionMap = mtmFeePositionMap.map();
          if (isDirty_mtmFeePositionMap) {
            pushFlowFunction_70.inputUpdated(mtmFeePositionMap);
          }
        }
        if (guardCheck_mtmPositionMap()) {
          isDirty_mtmPositionMap = mtmPositionMap.map();
          if (isDirty_mtmPositionMap) {
            pushFlowFunction_66.inputUpdated(mtmPositionMap);
          }
        }
        if (guardCheck_pnl()) {
          isDirty_pnl = pnl.map();
          if (isDirty_pnl) {
            pushFlowFunction_72.inputUpdated(pnl);
            netPnl.inputUpdated(pnl);
          }
        }
        if (guardCheck_positionMap()) {
          isDirty_positionMap = positionMap.map();
          if (isDirty_positionMap) {
            pushFlowFunction_54.inputUpdated(positionMap);
          }
        }
        if (guardCheck_pushFlowFunction_54()) {
          isDirty_pushFlowFunction_54 = pushFlowFunction_54.push();
        }
        if (guardCheck_pushFlowFunction_58()) {
          isDirty_pushFlowFunction_58 = pushFlowFunction_58.push();
        }
        if (guardCheck_pushFlowFunction_66()) {
          isDirty_pushFlowFunction_66 = pushFlowFunction_66.push();
        }
        if (guardCheck_pushFlowFunction_70()) {
          isDirty_pushFlowFunction_70 = pushFlowFunction_70.push();
        }
        if (guardCheck_pushFlowFunction_72()) {
          isDirty_pushFlowFunction_72 = pushFlowFunction_72.push();
        }
        if (guardCheck_rates()) {
          isDirty_rates = rates.map();
          if (isDirty_rates) {
            pushFlowFunction_62.inputUpdated(rates);
          }
        }
        if (guardCheck_pushFlowFunction_62()) {
          isDirty_pushFlowFunction_62 = pushFlowFunction_62.push();
        }
        if (guardCheck_tradeFees()) {
          isDirty_tradeFees = tradeFees.map();
          if (isDirty_tradeFees) {
            netPnl.input2Updated(tradeFees);
            pushFlowFunction_71.inputUpdated(tradeFees);
          }
        }
        if (guardCheck_netPnl()) {
          isDirty_netPnl = netPnl.map();
          if (isDirty_netPnl) {
            pushFlowFunction_73.inputUpdated(netPnl);
          }
        }
        if (guardCheck_pushFlowFunction_71()) {
          isDirty_pushFlowFunction_71 = pushFlowFunction_71.push();
        }
        if (guardCheck_pushFlowFunction_73()) {
          isDirty_pushFlowFunction_73 = pushFlowFunction_73.push();
        }
        afterEvent();
        return;
        //Event Class:[com.fluxtion.runtime.callback.CallbackEvent] filterId:[2]
      case (2):
        isDirty_callbackImpl_75 = callbackImpl_75.onEvent(typedEvent);
        if (guardCheck_flatMapFlowFunction_3()) {
          isDirty_flatMapFlowFunction_3 = true;
          flatMapFlowFunction_3.callbackReceived();
          if (isDirty_flatMapFlowFunction_3) {
            mapRef2RefFlowFunction_5.inputUpdated(flatMapFlowFunction_3);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_5()) {
          isDirty_mapRef2RefFlowFunction_5 = mapRef2RefFlowFunction_5.map();
          if (isDirty_mapRef2RefFlowFunction_5) {
            binaryMapToRefFlowFunction_15.input2Updated(mapRef2RefFlowFunction_5);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_15()) {
          isDirty_binaryMapToRefFlowFunction_15 = binaryMapToRefFlowFunction_15.map();
          if (isDirty_binaryMapToRefFlowFunction_15) {
            mapRef2RefFlowFunction_17.inputUpdated(binaryMapToRefFlowFunction_15);
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
            binaryMapToRefFlowFunction_31.input2Updated(mapRef2RefFlowFunction_19);
            binaryMapToRefFlowFunction_35.inputUpdated(mapRef2RefFlowFunction_19);
            mapRef2RefFlowFunction_52.inputUpdated(mapRef2RefFlowFunction_19);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_31()) {
          isDirty_binaryMapToRefFlowFunction_31 = binaryMapToRefFlowFunction_31.map();
          if (isDirty_binaryMapToRefFlowFunction_31) {
            mapRef2RefFlowFunction_33.inputUpdated(binaryMapToRefFlowFunction_31);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_33()) {
          isDirty_mapRef2RefFlowFunction_33 = mapRef2RefFlowFunction_33.map();
          if (isDirty_mapRef2RefFlowFunction_33) {
            binaryMapToRefFlowFunction_35.input2Updated(mapRef2RefFlowFunction_33);
            mapRef2RefFlowFunction_60.inputUpdated(mapRef2RefFlowFunction_33);
            binaryMapToRefFlowFunction_43.input2Updated(mapRef2RefFlowFunction_33);
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
            mapRef2RefFlowFunction_39.inputUpdated(mapRef2RefFlowFunction_37);
            mapRef2RefFlowFunction_64.inputUpdated(mapRef2RefFlowFunction_37);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_39()) {
          isDirty_mapRef2RefFlowFunction_39 = mapRef2RefFlowFunction_39.map();
          if (isDirty_mapRef2RefFlowFunction_39) {
            pnl.inputUpdated(mapRef2RefFlowFunction_39);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_52()) {
          isDirty_mapRef2RefFlowFunction_52 = mapRef2RefFlowFunction_52.map();
          if (isDirty_mapRef2RefFlowFunction_52) {
            positionMap.inputUpdated(mapRef2RefFlowFunction_52);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_60()) {
          isDirty_mapRef2RefFlowFunction_60 = mapRef2RefFlowFunction_60.map();
          if (isDirty_mapRef2RefFlowFunction_60) {
            rates.inputUpdated(mapRef2RefFlowFunction_60);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_64()) {
          isDirty_mapRef2RefFlowFunction_64 = mapRef2RefFlowFunction_64.map();
          if (isDirty_mapRef2RefFlowFunction_64) {
            mtmPositionMap.inputUpdated(mapRef2RefFlowFunction_64);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_43()) {
          isDirty_binaryMapToRefFlowFunction_43 = binaryMapToRefFlowFunction_43.map();
          if (isDirty_binaryMapToRefFlowFunction_43) {
            mapRef2RefFlowFunction_45.inputUpdated(binaryMapToRefFlowFunction_43);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_45()) {
          isDirty_mapRef2RefFlowFunction_45 = mapRef2RefFlowFunction_45.map();
          if (isDirty_mapRef2RefFlowFunction_45) {
            mapRef2RefFlowFunction_47.inputUpdated(mapRef2RefFlowFunction_45);
            mapRef2RefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_45);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_47()) {
          isDirty_mapRef2RefFlowFunction_47 = mapRef2RefFlowFunction_47.map();
          if (isDirty_mapRef2RefFlowFunction_47) {
            tradeFees.inputUpdated(mapRef2RefFlowFunction_47);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_68()) {
          isDirty_mapRef2RefFlowFunction_68 = mapRef2RefFlowFunction_68.map();
          if (isDirty_mapRef2RefFlowFunction_68) {
            mtmFeePositionMap.inputUpdated(mapRef2RefFlowFunction_68);
          }
        }
        if (guardCheck_mtmFeePositionMap()) {
          isDirty_mtmFeePositionMap = mtmFeePositionMap.map();
          if (isDirty_mtmFeePositionMap) {
            pushFlowFunction_70.inputUpdated(mtmFeePositionMap);
          }
        }
        if (guardCheck_mtmPositionMap()) {
          isDirty_mtmPositionMap = mtmPositionMap.map();
          if (isDirty_mtmPositionMap) {
            pushFlowFunction_66.inputUpdated(mtmPositionMap);
          }
        }
        if (guardCheck_pnl()) {
          isDirty_pnl = pnl.map();
          if (isDirty_pnl) {
            pushFlowFunction_72.inputUpdated(pnl);
            netPnl.inputUpdated(pnl);
          }
        }
        if (guardCheck_positionMap()) {
          isDirty_positionMap = positionMap.map();
          if (isDirty_positionMap) {
            pushFlowFunction_54.inputUpdated(positionMap);
          }
        }
        if (guardCheck_pushFlowFunction_54()) {
          isDirty_pushFlowFunction_54 = pushFlowFunction_54.push();
        }
        if (guardCheck_pushFlowFunction_66()) {
          isDirty_pushFlowFunction_66 = pushFlowFunction_66.push();
        }
        if (guardCheck_pushFlowFunction_70()) {
          isDirty_pushFlowFunction_70 = pushFlowFunction_70.push();
        }
        if (guardCheck_pushFlowFunction_72()) {
          isDirty_pushFlowFunction_72 = pushFlowFunction_72.push();
        }
        if (guardCheck_rates()) {
          isDirty_rates = rates.map();
          if (isDirty_rates) {
            pushFlowFunction_62.inputUpdated(rates);
          }
        }
        if (guardCheck_pushFlowFunction_62()) {
          isDirty_pushFlowFunction_62 = pushFlowFunction_62.push();
        }
        if (guardCheck_tradeFees()) {
          isDirty_tradeFees = tradeFees.map();
          if (isDirty_tradeFees) {
            netPnl.input2Updated(tradeFees);
            pushFlowFunction_71.inputUpdated(tradeFees);
          }
        }
        if (guardCheck_netPnl()) {
          isDirty_netPnl = netPnl.map();
          if (isDirty_netPnl) {
            pushFlowFunction_73.inputUpdated(netPnl);
          }
        }
        if (guardCheck_pushFlowFunction_71()) {
          isDirty_pushFlowFunction_71 = pushFlowFunction_71.push();
        }
        if (guardCheck_pushFlowFunction_73()) {
          isDirty_pushFlowFunction_73 = pushFlowFunction_73.push();
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
          mapRef2RefFlowFunction_5.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
          mapRef2RefFlowFunction_7.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
          mapRef2RefFlowFunction_9.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
        }
        if (guardCheck_mapRef2RefFlowFunction_5()) {
          isDirty_mapRef2RefFlowFunction_5 = mapRef2RefFlowFunction_5.map();
          if (isDirty_mapRef2RefFlowFunction_5) {
            binaryMapToRefFlowFunction_15.input2Updated(mapRef2RefFlowFunction_5);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_7()) {
          isDirty_mapRef2RefFlowFunction_7 = mapRef2RefFlowFunction_7.map();
          if (isDirty_mapRef2RefFlowFunction_7) {
            binaryMapToRefFlowFunction_11.inputUpdated(mapRef2RefFlowFunction_7);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_9()) {
          isDirty_mapRef2RefFlowFunction_9 = mapRef2RefFlowFunction_9.map();
          if (isDirty_mapRef2RefFlowFunction_9) {
            binaryMapToRefFlowFunction_11.input2Updated(mapRef2RefFlowFunction_9);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_11()) {
          isDirty_binaryMapToRefFlowFunction_11 = binaryMapToRefFlowFunction_11.map();
          if (isDirty_binaryMapToRefFlowFunction_11) {
            mapRef2RefFlowFunction_13.inputUpdated(binaryMapToRefFlowFunction_11);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_13()) {
          isDirty_mapRef2RefFlowFunction_13 = mapRef2RefFlowFunction_13.map();
          if (isDirty_mapRef2RefFlowFunction_13) {
            binaryMapToRefFlowFunction_15.inputUpdated(mapRef2RefFlowFunction_13);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_15()) {
          isDirty_binaryMapToRefFlowFunction_15 = binaryMapToRefFlowFunction_15.map();
          if (isDirty_binaryMapToRefFlowFunction_15) {
            mapRef2RefFlowFunction_17.inputUpdated(binaryMapToRefFlowFunction_15);
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
            binaryMapToRefFlowFunction_31.input2Updated(mapRef2RefFlowFunction_19);
            binaryMapToRefFlowFunction_35.inputUpdated(mapRef2RefFlowFunction_19);
            mapRef2RefFlowFunction_52.inputUpdated(mapRef2RefFlowFunction_19);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_31()) {
          isDirty_binaryMapToRefFlowFunction_31 = binaryMapToRefFlowFunction_31.map();
          if (isDirty_binaryMapToRefFlowFunction_31) {
            mapRef2RefFlowFunction_33.inputUpdated(binaryMapToRefFlowFunction_31);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_33()) {
          isDirty_mapRef2RefFlowFunction_33 = mapRef2RefFlowFunction_33.map();
          if (isDirty_mapRef2RefFlowFunction_33) {
            binaryMapToRefFlowFunction_35.input2Updated(mapRef2RefFlowFunction_33);
            mapRef2RefFlowFunction_60.inputUpdated(mapRef2RefFlowFunction_33);
            binaryMapToRefFlowFunction_43.input2Updated(mapRef2RefFlowFunction_33);
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
            mapRef2RefFlowFunction_39.inputUpdated(mapRef2RefFlowFunction_37);
            mapRef2RefFlowFunction_64.inputUpdated(mapRef2RefFlowFunction_37);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_39()) {
          isDirty_mapRef2RefFlowFunction_39 = mapRef2RefFlowFunction_39.map();
          if (isDirty_mapRef2RefFlowFunction_39) {
            pnl.inputUpdated(mapRef2RefFlowFunction_39);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_52()) {
          isDirty_mapRef2RefFlowFunction_52 = mapRef2RefFlowFunction_52.map();
          if (isDirty_mapRef2RefFlowFunction_52) {
            positionMap.inputUpdated(mapRef2RefFlowFunction_52);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_60()) {
          isDirty_mapRef2RefFlowFunction_60 = mapRef2RefFlowFunction_60.map();
          if (isDirty_mapRef2RefFlowFunction_60) {
            rates.inputUpdated(mapRef2RefFlowFunction_60);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_64()) {
          isDirty_mapRef2RefFlowFunction_64 = mapRef2RefFlowFunction_64.map();
          if (isDirty_mapRef2RefFlowFunction_64) {
            mtmPositionMap.inputUpdated(mapRef2RefFlowFunction_64);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_43()) {
          isDirty_binaryMapToRefFlowFunction_43 = binaryMapToRefFlowFunction_43.map();
          if (isDirty_binaryMapToRefFlowFunction_43) {
            mapRef2RefFlowFunction_45.inputUpdated(binaryMapToRefFlowFunction_43);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_45()) {
          isDirty_mapRef2RefFlowFunction_45 = mapRef2RefFlowFunction_45.map();
          if (isDirty_mapRef2RefFlowFunction_45) {
            mapRef2RefFlowFunction_47.inputUpdated(mapRef2RefFlowFunction_45);
            mapRef2RefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_45);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_47()) {
          isDirty_mapRef2RefFlowFunction_47 = mapRef2RefFlowFunction_47.map();
          if (isDirty_mapRef2RefFlowFunction_47) {
            tradeFees.inputUpdated(mapRef2RefFlowFunction_47);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_68()) {
          isDirty_mapRef2RefFlowFunction_68 = mapRef2RefFlowFunction_68.map();
          if (isDirty_mapRef2RefFlowFunction_68) {
            mtmFeePositionMap.inputUpdated(mapRef2RefFlowFunction_68);
          }
        }
        if (guardCheck_mtmFeePositionMap()) {
          isDirty_mtmFeePositionMap = mtmFeePositionMap.map();
          if (isDirty_mtmFeePositionMap) {
            pushFlowFunction_70.inputUpdated(mtmFeePositionMap);
          }
        }
        if (guardCheck_mtmPositionMap()) {
          isDirty_mtmPositionMap = mtmPositionMap.map();
          if (isDirty_mtmPositionMap) {
            pushFlowFunction_66.inputUpdated(mtmPositionMap);
          }
        }
        if (guardCheck_pnl()) {
          isDirty_pnl = pnl.map();
          if (isDirty_pnl) {
            pushFlowFunction_72.inputUpdated(pnl);
            netPnl.inputUpdated(pnl);
          }
        }
        if (guardCheck_positionMap()) {
          isDirty_positionMap = positionMap.map();
          if (isDirty_positionMap) {
            pushFlowFunction_54.inputUpdated(positionMap);
          }
        }
        if (guardCheck_pushFlowFunction_54()) {
          isDirty_pushFlowFunction_54 = pushFlowFunction_54.push();
        }
        if (guardCheck_pushFlowFunction_66()) {
          isDirty_pushFlowFunction_66 = pushFlowFunction_66.push();
        }
        if (guardCheck_pushFlowFunction_70()) {
          isDirty_pushFlowFunction_70 = pushFlowFunction_70.push();
        }
        if (guardCheck_pushFlowFunction_72()) {
          isDirty_pushFlowFunction_72 = pushFlowFunction_72.push();
        }
        if (guardCheck_rates()) {
          isDirty_rates = rates.map();
          if (isDirty_rates) {
            pushFlowFunction_62.inputUpdated(rates);
          }
        }
        if (guardCheck_pushFlowFunction_62()) {
          isDirty_pushFlowFunction_62 = pushFlowFunction_62.push();
        }
        if (guardCheck_tradeFees()) {
          isDirty_tradeFees = tradeFees.map();
          if (isDirty_tradeFees) {
            netPnl.input2Updated(tradeFees);
            pushFlowFunction_71.inputUpdated(tradeFees);
          }
        }
        if (guardCheck_netPnl()) {
          isDirty_netPnl = netPnl.map();
          if (isDirty_netPnl) {
            pushFlowFunction_73.inputUpdated(netPnl);
          }
        }
        if (guardCheck_pushFlowFunction_71()) {
          isDirty_pushFlowFunction_71 = pushFlowFunction_71.push();
        }
        if (guardCheck_pushFlowFunction_73()) {
          isDirty_pushFlowFunction_73 = pushFlowFunction_73.push();
        }
        afterEvent();
        return;
        //Event Class:[com.fluxtion.runtime.event.Signal] filterString:[positionUpdate]
      case ("positionUpdate"):
        isDirty_handlerSignal_positionUpdate = handlerSignal_positionUpdate.onEvent(typedEvent);
        if (isDirty_handlerSignal_positionUpdate) {
          mapRef2RefFlowFunction_5.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
          binaryMapToRefFlowFunction_11.publishTriggerOverrideNodeUpdated(
              handlerSignal_positionUpdate);
          mapRef2RefFlowFunction_19.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
          mapRef2RefFlowFunction_37.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
          mapRef2RefFlowFunction_26.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
          mapRef2RefFlowFunction_26.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
          mapRef2RefFlowFunction_45.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
          pnl.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
          tradeFees.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
          netPnl.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
        }
        if (guardCheck_mapRef2RefFlowFunction_5()) {
          isDirty_mapRef2RefFlowFunction_5 = mapRef2RefFlowFunction_5.map();
          if (isDirty_mapRef2RefFlowFunction_5) {
            binaryMapToRefFlowFunction_15.input2Updated(mapRef2RefFlowFunction_5);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_11()) {
          isDirty_binaryMapToRefFlowFunction_11 = binaryMapToRefFlowFunction_11.map();
          if (isDirty_binaryMapToRefFlowFunction_11) {
            mapRef2RefFlowFunction_13.inputUpdated(binaryMapToRefFlowFunction_11);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_13()) {
          isDirty_mapRef2RefFlowFunction_13 = mapRef2RefFlowFunction_13.map();
          if (isDirty_mapRef2RefFlowFunction_13) {
            binaryMapToRefFlowFunction_15.inputUpdated(mapRef2RefFlowFunction_13);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_15()) {
          isDirty_binaryMapToRefFlowFunction_15 = binaryMapToRefFlowFunction_15.map();
          if (isDirty_binaryMapToRefFlowFunction_15) {
            mapRef2RefFlowFunction_17.inputUpdated(binaryMapToRefFlowFunction_15);
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
            binaryMapToRefFlowFunction_31.input2Updated(mapRef2RefFlowFunction_19);
            binaryMapToRefFlowFunction_35.inputUpdated(mapRef2RefFlowFunction_19);
            mapRef2RefFlowFunction_52.inputUpdated(mapRef2RefFlowFunction_19);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_31()) {
          isDirty_binaryMapToRefFlowFunction_31 = binaryMapToRefFlowFunction_31.map();
          if (isDirty_binaryMapToRefFlowFunction_31) {
            mapRef2RefFlowFunction_33.inputUpdated(binaryMapToRefFlowFunction_31);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_33()) {
          isDirty_mapRef2RefFlowFunction_33 = mapRef2RefFlowFunction_33.map();
          if (isDirty_mapRef2RefFlowFunction_33) {
            binaryMapToRefFlowFunction_35.input2Updated(mapRef2RefFlowFunction_33);
            mapRef2RefFlowFunction_60.inputUpdated(mapRef2RefFlowFunction_33);
            binaryMapToRefFlowFunction_43.input2Updated(mapRef2RefFlowFunction_33);
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
            mapRef2RefFlowFunction_39.inputUpdated(mapRef2RefFlowFunction_37);
            mapRef2RefFlowFunction_64.inputUpdated(mapRef2RefFlowFunction_37);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_39()) {
          isDirty_mapRef2RefFlowFunction_39 = mapRef2RefFlowFunction_39.map();
          if (isDirty_mapRef2RefFlowFunction_39) {
            pnl.inputUpdated(mapRef2RefFlowFunction_39);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_52()) {
          isDirty_mapRef2RefFlowFunction_52 = mapRef2RefFlowFunction_52.map();
          if (isDirty_mapRef2RefFlowFunction_52) {
            positionMap.inputUpdated(mapRef2RefFlowFunction_52);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_60()) {
          isDirty_mapRef2RefFlowFunction_60 = mapRef2RefFlowFunction_60.map();
          if (isDirty_mapRef2RefFlowFunction_60) {
            rates.inputUpdated(mapRef2RefFlowFunction_60);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_64()) {
          isDirty_mapRef2RefFlowFunction_64 = mapRef2RefFlowFunction_64.map();
          if (isDirty_mapRef2RefFlowFunction_64) {
            mtmPositionMap.inputUpdated(mapRef2RefFlowFunction_64);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_26()) {
          isDirty_mapRef2RefFlowFunction_26 = mapRef2RefFlowFunction_26.map();
          if (isDirty_mapRef2RefFlowFunction_26) {
            binaryMapToRefFlowFunction_43.inputUpdated(mapRef2RefFlowFunction_26);
            mapRef2RefFlowFunction_56.inputUpdated(mapRef2RefFlowFunction_26);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_43()) {
          isDirty_binaryMapToRefFlowFunction_43 = binaryMapToRefFlowFunction_43.map();
          if (isDirty_binaryMapToRefFlowFunction_43) {
            mapRef2RefFlowFunction_45.inputUpdated(binaryMapToRefFlowFunction_43);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_45()) {
          isDirty_mapRef2RefFlowFunction_45 = mapRef2RefFlowFunction_45.map();
          if (isDirty_mapRef2RefFlowFunction_45) {
            mapRef2RefFlowFunction_47.inputUpdated(mapRef2RefFlowFunction_45);
            mapRef2RefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_45);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_47()) {
          isDirty_mapRef2RefFlowFunction_47 = mapRef2RefFlowFunction_47.map();
          if (isDirty_mapRef2RefFlowFunction_47) {
            tradeFees.inputUpdated(mapRef2RefFlowFunction_47);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_56()) {
          isDirty_mapRef2RefFlowFunction_56 = mapRef2RefFlowFunction_56.map();
          if (isDirty_mapRef2RefFlowFunction_56) {
            feePositionMap.inputUpdated(mapRef2RefFlowFunction_56);
          }
        }
        if (guardCheck_feePositionMap()) {
          isDirty_feePositionMap = feePositionMap.map();
          if (isDirty_feePositionMap) {
            pushFlowFunction_58.inputUpdated(feePositionMap);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_68()) {
          isDirty_mapRef2RefFlowFunction_68 = mapRef2RefFlowFunction_68.map();
          if (isDirty_mapRef2RefFlowFunction_68) {
            mtmFeePositionMap.inputUpdated(mapRef2RefFlowFunction_68);
          }
        }
        if (guardCheck_mtmFeePositionMap()) {
          isDirty_mtmFeePositionMap = mtmFeePositionMap.map();
          if (isDirty_mtmFeePositionMap) {
            pushFlowFunction_70.inputUpdated(mtmFeePositionMap);
          }
        }
        if (guardCheck_mtmPositionMap()) {
          isDirty_mtmPositionMap = mtmPositionMap.map();
          if (isDirty_mtmPositionMap) {
            pushFlowFunction_66.inputUpdated(mtmPositionMap);
          }
        }
        if (guardCheck_pnl()) {
          isDirty_pnl = pnl.map();
          if (isDirty_pnl) {
            pushFlowFunction_72.inputUpdated(pnl);
            netPnl.inputUpdated(pnl);
          }
        }
        if (guardCheck_positionMap()) {
          isDirty_positionMap = positionMap.map();
          if (isDirty_positionMap) {
            pushFlowFunction_54.inputUpdated(positionMap);
          }
        }
        if (guardCheck_pushFlowFunction_54()) {
          isDirty_pushFlowFunction_54 = pushFlowFunction_54.push();
        }
        if (guardCheck_pushFlowFunction_58()) {
          isDirty_pushFlowFunction_58 = pushFlowFunction_58.push();
        }
        if (guardCheck_pushFlowFunction_66()) {
          isDirty_pushFlowFunction_66 = pushFlowFunction_66.push();
        }
        if (guardCheck_pushFlowFunction_70()) {
          isDirty_pushFlowFunction_70 = pushFlowFunction_70.push();
        }
        if (guardCheck_pushFlowFunction_72()) {
          isDirty_pushFlowFunction_72 = pushFlowFunction_72.push();
        }
        if (guardCheck_rates()) {
          isDirty_rates = rates.map();
          if (isDirty_rates) {
            pushFlowFunction_62.inputUpdated(rates);
          }
        }
        if (guardCheck_pushFlowFunction_62()) {
          isDirty_pushFlowFunction_62 = pushFlowFunction_62.push();
        }
        if (guardCheck_tradeFees()) {
          isDirty_tradeFees = tradeFees.map();
          if (isDirty_tradeFees) {
            netPnl.input2Updated(tradeFees);
            pushFlowFunction_71.inputUpdated(tradeFees);
          }
        }
        if (guardCheck_netPnl()) {
          isDirty_netPnl = netPnl.map();
          if (isDirty_netPnl) {
            pushFlowFunction_73.inputUpdated(netPnl);
          }
        }
        if (guardCheck_pushFlowFunction_71()) {
          isDirty_pushFlowFunction_71 = pushFlowFunction_71.push();
        }
        if (guardCheck_pushFlowFunction_73()) {
          isDirty_pushFlowFunction_73 = pushFlowFunction_73.push();
        }
        afterEvent();
        return;
    }
    afterEvent();
  }

  public void handleEvent(SinkDeregister typedEvent) {
    auditEvent(typedEvent);
    switch (typedEvent.filterString()) {
        //Event Class:[com.fluxtion.runtime.output.SinkDeregister] filterString:[feePositionListener]
      case ("feePositionListener"):
        feePositionListener.unregisterSink(typedEvent);
        afterEvent();
        return;
        //Event Class:[com.fluxtion.runtime.output.SinkDeregister] filterString:[mtmFeePositionListener]
      case ("mtmFeePositionListener"):
        mtmFeePositionListener.unregisterSink(typedEvent);
        afterEvent();
        return;
        //Event Class:[com.fluxtion.runtime.output.SinkDeregister] filterString:[mtmPositionListener]
      case ("mtmPositionListener"):
        mtmPositionListener.unregisterSink(typedEvent);
        afterEvent();
        return;
        //Event Class:[com.fluxtion.runtime.output.SinkDeregister] filterString:[netPnlListener]
      case ("netPnlListener"):
        netPnlListener.unregisterSink(typedEvent);
        afterEvent();
        return;
        //Event Class:[com.fluxtion.runtime.output.SinkDeregister] filterString:[pnlListener]
      case ("pnlListener"):
        pnlListener.unregisterSink(typedEvent);
        afterEvent();
        return;
        //Event Class:[com.fluxtion.runtime.output.SinkDeregister] filterString:[positionListener]
      case ("positionListener"):
        positionListener.unregisterSink(typedEvent);
        afterEvent();
        return;
        //Event Class:[com.fluxtion.runtime.output.SinkDeregister] filterString:[rateListener]
      case ("rateListener"):
        rateListener.unregisterSink(typedEvent);
        afterEvent();
        return;
        //Event Class:[com.fluxtion.runtime.output.SinkDeregister] filterString:[tradeFeesListener]
      case ("tradeFeesListener"):
        tradeFeesListener.unregisterSink(typedEvent);
        afterEvent();
        return;
    }
    afterEvent();
  }

  public void handleEvent(SinkRegistration typedEvent) {
    auditEvent(typedEvent);
    switch (typedEvent.filterString()) {
        //Event Class:[com.fluxtion.runtime.output.SinkRegistration] filterString:[feePositionListener]
      case ("feePositionListener"):
        feePositionListener.sinkRegistration(typedEvent);
        afterEvent();
        return;
        //Event Class:[com.fluxtion.runtime.output.SinkRegistration] filterString:[mtmFeePositionListener]
      case ("mtmFeePositionListener"):
        mtmFeePositionListener.sinkRegistration(typedEvent);
        afterEvent();
        return;
        //Event Class:[com.fluxtion.runtime.output.SinkRegistration] filterString:[mtmPositionListener]
      case ("mtmPositionListener"):
        mtmPositionListener.sinkRegistration(typedEvent);
        afterEvent();
        return;
        //Event Class:[com.fluxtion.runtime.output.SinkRegistration] filterString:[netPnlListener]
      case ("netPnlListener"):
        netPnlListener.sinkRegistration(typedEvent);
        afterEvent();
        return;
        //Event Class:[com.fluxtion.runtime.output.SinkRegistration] filterString:[pnlListener]
      case ("pnlListener"):
        pnlListener.sinkRegistration(typedEvent);
        afterEvent();
        return;
        //Event Class:[com.fluxtion.runtime.output.SinkRegistration] filterString:[positionListener]
      case ("positionListener"):
        positionListener.sinkRegistration(typedEvent);
        afterEvent();
        return;
        //Event Class:[com.fluxtion.runtime.output.SinkRegistration] filterString:[rateListener]
      case ("rateListener"):
        rateListener.sinkRegistration(typedEvent);
        afterEvent();
        return;
        //Event Class:[com.fluxtion.runtime.output.SinkRegistration] filterString:[tradeFeesListener]
      case ("tradeFeesListener"):
        tradeFeesListener.sinkRegistration(typedEvent);
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
    isDirty_derivedRateNode_27 = derivedRateNode_27.midRate(typedEvent);
    isDirty_handlerMidPrice = handlerMidPrice.onEvent(typedEvent);
    if (isDirty_handlerMidPrice) {
      filterFlowFunction_28.inputUpdated(handlerMidPrice);
    }
    if (guardCheck_filterFlowFunction_28()) {
      isDirty_filterFlowFunction_28 = filterFlowFunction_28.filter();
      if (isDirty_filterFlowFunction_28) {
        mapRef2RefFlowFunction_30.inputUpdated(filterFlowFunction_28);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_30()) {
      isDirty_mapRef2RefFlowFunction_30 = mapRef2RefFlowFunction_30.map();
      if (isDirty_mapRef2RefFlowFunction_30) {
        binaryMapToRefFlowFunction_31.inputUpdated(mapRef2RefFlowFunction_30);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_31()) {
      isDirty_binaryMapToRefFlowFunction_31 = binaryMapToRefFlowFunction_31.map();
      if (isDirty_binaryMapToRefFlowFunction_31) {
        mapRef2RefFlowFunction_33.inputUpdated(binaryMapToRefFlowFunction_31);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_33()) {
      isDirty_mapRef2RefFlowFunction_33 = mapRef2RefFlowFunction_33.map();
      if (isDirty_mapRef2RefFlowFunction_33) {
        binaryMapToRefFlowFunction_35.input2Updated(mapRef2RefFlowFunction_33);
        mapRef2RefFlowFunction_60.inputUpdated(mapRef2RefFlowFunction_33);
        binaryMapToRefFlowFunction_43.input2Updated(mapRef2RefFlowFunction_33);
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
        mapRef2RefFlowFunction_39.inputUpdated(mapRef2RefFlowFunction_37);
        mapRef2RefFlowFunction_64.inputUpdated(mapRef2RefFlowFunction_37);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_39()) {
      isDirty_mapRef2RefFlowFunction_39 = mapRef2RefFlowFunction_39.map();
      if (isDirty_mapRef2RefFlowFunction_39) {
        pnl.inputUpdated(mapRef2RefFlowFunction_39);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_60()) {
      isDirty_mapRef2RefFlowFunction_60 = mapRef2RefFlowFunction_60.map();
      if (isDirty_mapRef2RefFlowFunction_60) {
        rates.inputUpdated(mapRef2RefFlowFunction_60);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_64()) {
      isDirty_mapRef2RefFlowFunction_64 = mapRef2RefFlowFunction_64.map();
      if (isDirty_mapRef2RefFlowFunction_64) {
        mtmPositionMap.inputUpdated(mapRef2RefFlowFunction_64);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_43()) {
      isDirty_binaryMapToRefFlowFunction_43 = binaryMapToRefFlowFunction_43.map();
      if (isDirty_binaryMapToRefFlowFunction_43) {
        mapRef2RefFlowFunction_45.inputUpdated(binaryMapToRefFlowFunction_43);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_45()) {
      isDirty_mapRef2RefFlowFunction_45 = mapRef2RefFlowFunction_45.map();
      if (isDirty_mapRef2RefFlowFunction_45) {
        mapRef2RefFlowFunction_47.inputUpdated(mapRef2RefFlowFunction_45);
        mapRef2RefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_45);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_47()) {
      isDirty_mapRef2RefFlowFunction_47 = mapRef2RefFlowFunction_47.map();
      if (isDirty_mapRef2RefFlowFunction_47) {
        tradeFees.inputUpdated(mapRef2RefFlowFunction_47);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_68()) {
      isDirty_mapRef2RefFlowFunction_68 = mapRef2RefFlowFunction_68.map();
      if (isDirty_mapRef2RefFlowFunction_68) {
        mtmFeePositionMap.inputUpdated(mapRef2RefFlowFunction_68);
      }
    }
    if (guardCheck_mtmFeePositionMap()) {
      isDirty_mtmFeePositionMap = mtmFeePositionMap.map();
      if (isDirty_mtmFeePositionMap) {
        pushFlowFunction_70.inputUpdated(mtmFeePositionMap);
      }
    }
    if (guardCheck_mtmPositionMap()) {
      isDirty_mtmPositionMap = mtmPositionMap.map();
      if (isDirty_mtmPositionMap) {
        pushFlowFunction_66.inputUpdated(mtmPositionMap);
      }
    }
    if (guardCheck_pnl()) {
      isDirty_pnl = pnl.map();
      if (isDirty_pnl) {
        pushFlowFunction_72.inputUpdated(pnl);
        netPnl.inputUpdated(pnl);
      }
    }
    if (guardCheck_pushFlowFunction_66()) {
      isDirty_pushFlowFunction_66 = pushFlowFunction_66.push();
    }
    if (guardCheck_pushFlowFunction_70()) {
      isDirty_pushFlowFunction_70 = pushFlowFunction_70.push();
    }
    if (guardCheck_pushFlowFunction_72()) {
      isDirty_pushFlowFunction_72 = pushFlowFunction_72.push();
    }
    if (guardCheck_rates()) {
      isDirty_rates = rates.map();
      if (isDirty_rates) {
        pushFlowFunction_62.inputUpdated(rates);
      }
    }
    if (guardCheck_pushFlowFunction_62()) {
      isDirty_pushFlowFunction_62 = pushFlowFunction_62.push();
    }
    if (guardCheck_tradeFees()) {
      isDirty_tradeFees = tradeFees.map();
      if (isDirty_tradeFees) {
        netPnl.input2Updated(tradeFees);
        pushFlowFunction_71.inputUpdated(tradeFees);
      }
    }
    if (guardCheck_netPnl()) {
      isDirty_netPnl = netPnl.map();
      if (isDirty_netPnl) {
        pushFlowFunction_73.inputUpdated(netPnl);
      }
    }
    if (guardCheck_pushFlowFunction_71()) {
      isDirty_pushFlowFunction_71 = pushFlowFunction_71.push();
    }
    if (guardCheck_pushFlowFunction_73()) {
      isDirty_pushFlowFunction_73 = pushFlowFunction_73.push();
    }
    afterEvent();
  }

  public void handleEvent(MtmInstrument typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_derivedRateNode_27 = derivedRateNode_27.updateMtmInstrument(typedEvent);
    if (guardCheck_filterFlowFunction_28()) {
      isDirty_filterFlowFunction_28 = filterFlowFunction_28.filter();
      if (isDirty_filterFlowFunction_28) {
        mapRef2RefFlowFunction_30.inputUpdated(filterFlowFunction_28);
      }
    }
    isDirty_handlerMtmInstrument = handlerMtmInstrument.onEvent(typedEvent);
    if (isDirty_handlerMtmInstrument) {
      mapRef2RefFlowFunction_30.resetTriggerNodeUpdated(handlerMtmInstrument);
    }
    if (guardCheck_mapRef2RefFlowFunction_30()) {
      isDirty_mapRef2RefFlowFunction_30 = mapRef2RefFlowFunction_30.map();
      if (isDirty_mapRef2RefFlowFunction_30) {
        binaryMapToRefFlowFunction_31.inputUpdated(mapRef2RefFlowFunction_30);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_31()) {
      isDirty_binaryMapToRefFlowFunction_31 = binaryMapToRefFlowFunction_31.map();
      if (isDirty_binaryMapToRefFlowFunction_31) {
        mapRef2RefFlowFunction_33.inputUpdated(binaryMapToRefFlowFunction_31);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_33()) {
      isDirty_mapRef2RefFlowFunction_33 = mapRef2RefFlowFunction_33.map();
      if (isDirty_mapRef2RefFlowFunction_33) {
        binaryMapToRefFlowFunction_35.input2Updated(mapRef2RefFlowFunction_33);
        mapRef2RefFlowFunction_60.inputUpdated(mapRef2RefFlowFunction_33);
        binaryMapToRefFlowFunction_43.input2Updated(mapRef2RefFlowFunction_33);
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
        mapRef2RefFlowFunction_39.inputUpdated(mapRef2RefFlowFunction_37);
        mapRef2RefFlowFunction_64.inputUpdated(mapRef2RefFlowFunction_37);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_39()) {
      isDirty_mapRef2RefFlowFunction_39 = mapRef2RefFlowFunction_39.map();
      if (isDirty_mapRef2RefFlowFunction_39) {
        pnl.inputUpdated(mapRef2RefFlowFunction_39);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_60()) {
      isDirty_mapRef2RefFlowFunction_60 = mapRef2RefFlowFunction_60.map();
      if (isDirty_mapRef2RefFlowFunction_60) {
        rates.inputUpdated(mapRef2RefFlowFunction_60);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_64()) {
      isDirty_mapRef2RefFlowFunction_64 = mapRef2RefFlowFunction_64.map();
      if (isDirty_mapRef2RefFlowFunction_64) {
        mtmPositionMap.inputUpdated(mapRef2RefFlowFunction_64);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_43()) {
      isDirty_binaryMapToRefFlowFunction_43 = binaryMapToRefFlowFunction_43.map();
      if (isDirty_binaryMapToRefFlowFunction_43) {
        mapRef2RefFlowFunction_45.inputUpdated(binaryMapToRefFlowFunction_43);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_45()) {
      isDirty_mapRef2RefFlowFunction_45 = mapRef2RefFlowFunction_45.map();
      if (isDirty_mapRef2RefFlowFunction_45) {
        mapRef2RefFlowFunction_47.inputUpdated(mapRef2RefFlowFunction_45);
        mapRef2RefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_45);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_47()) {
      isDirty_mapRef2RefFlowFunction_47 = mapRef2RefFlowFunction_47.map();
      if (isDirty_mapRef2RefFlowFunction_47) {
        tradeFees.inputUpdated(mapRef2RefFlowFunction_47);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_68()) {
      isDirty_mapRef2RefFlowFunction_68 = mapRef2RefFlowFunction_68.map();
      if (isDirty_mapRef2RefFlowFunction_68) {
        mtmFeePositionMap.inputUpdated(mapRef2RefFlowFunction_68);
      }
    }
    if (guardCheck_mtmFeePositionMap()) {
      isDirty_mtmFeePositionMap = mtmFeePositionMap.map();
      if (isDirty_mtmFeePositionMap) {
        pushFlowFunction_70.inputUpdated(mtmFeePositionMap);
      }
    }
    if (guardCheck_mtmPositionMap()) {
      isDirty_mtmPositionMap = mtmPositionMap.map();
      if (isDirty_mtmPositionMap) {
        pushFlowFunction_66.inputUpdated(mtmPositionMap);
      }
    }
    if (guardCheck_pnl()) {
      isDirty_pnl = pnl.map();
      if (isDirty_pnl) {
        pushFlowFunction_72.inputUpdated(pnl);
        netPnl.inputUpdated(pnl);
      }
    }
    if (guardCheck_pushFlowFunction_66()) {
      isDirty_pushFlowFunction_66 = pushFlowFunction_66.push();
    }
    if (guardCheck_pushFlowFunction_70()) {
      isDirty_pushFlowFunction_70 = pushFlowFunction_70.push();
    }
    if (guardCheck_pushFlowFunction_72()) {
      isDirty_pushFlowFunction_72 = pushFlowFunction_72.push();
    }
    if (guardCheck_rates()) {
      isDirty_rates = rates.map();
      if (isDirty_rates) {
        pushFlowFunction_62.inputUpdated(rates);
      }
    }
    if (guardCheck_pushFlowFunction_62()) {
      isDirty_pushFlowFunction_62 = pushFlowFunction_62.push();
    }
    if (guardCheck_tradeFees()) {
      isDirty_tradeFees = tradeFees.map();
      if (isDirty_tradeFees) {
        netPnl.input2Updated(tradeFees);
        pushFlowFunction_71.inputUpdated(tradeFees);
      }
    }
    if (guardCheck_netPnl()) {
      isDirty_netPnl = netPnl.map();
      if (isDirty_netPnl) {
        pushFlowFunction_73.inputUpdated(netPnl);
      }
    }
    if (guardCheck_pushFlowFunction_71()) {
      isDirty_pushFlowFunction_71 = pushFlowFunction_71.push();
    }
    if (guardCheck_pushFlowFunction_73()) {
      isDirty_pushFlowFunction_73 = pushFlowFunction_73.push();
    }
    afterEvent();
  }

  public void handleEvent(PositionSnapshot typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_handlerPositionSnapshot = handlerPositionSnapshot.onEvent(typedEvent);
    if (isDirty_handlerPositionSnapshot) {
      flatMapFlowFunction_3.inputUpdatedAndFlatMap(handlerPositionSnapshot);
    }
    afterEvent();
  }

  public void handleEvent(Trade typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_handlerTrade = handlerTrade.onEvent(typedEvent);
    if (isDirty_handlerTrade) {
      mergeFlowFunction_2.inputStreamUpdated(handlerTrade);
    }
    if (guardCheck_mergeFlowFunction_2()) {
      isDirty_mergeFlowFunction_2 = mergeFlowFunction_2.publishMerge();
      if (isDirty_mergeFlowFunction_2) {
        mapRef2RefFlowFunction_7.inputUpdated(mergeFlowFunction_2);
        mapRef2RefFlowFunction_9.inputUpdated(mergeFlowFunction_2);
        mapRef2RefFlowFunction_20.inputUpdated(mergeFlowFunction_2);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_7()) {
      isDirty_mapRef2RefFlowFunction_7 = mapRef2RefFlowFunction_7.map();
      if (isDirty_mapRef2RefFlowFunction_7) {
        binaryMapToRefFlowFunction_11.inputUpdated(mapRef2RefFlowFunction_7);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_9()) {
      isDirty_mapRef2RefFlowFunction_9 = mapRef2RefFlowFunction_9.map();
      if (isDirty_mapRef2RefFlowFunction_9) {
        binaryMapToRefFlowFunction_11.input2Updated(mapRef2RefFlowFunction_9);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_11()) {
      isDirty_binaryMapToRefFlowFunction_11 = binaryMapToRefFlowFunction_11.map();
      if (isDirty_binaryMapToRefFlowFunction_11) {
        mapRef2RefFlowFunction_13.inputUpdated(binaryMapToRefFlowFunction_11);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_13()) {
      isDirty_mapRef2RefFlowFunction_13 = mapRef2RefFlowFunction_13.map();
      if (isDirty_mapRef2RefFlowFunction_13) {
        binaryMapToRefFlowFunction_15.inputUpdated(mapRef2RefFlowFunction_13);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_15()) {
      isDirty_binaryMapToRefFlowFunction_15 = binaryMapToRefFlowFunction_15.map();
      if (isDirty_binaryMapToRefFlowFunction_15) {
        mapRef2RefFlowFunction_17.inputUpdated(binaryMapToRefFlowFunction_15);
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
        binaryMapToRefFlowFunction_31.input2Updated(mapRef2RefFlowFunction_19);
        binaryMapToRefFlowFunction_35.inputUpdated(mapRef2RefFlowFunction_19);
        mapRef2RefFlowFunction_52.inputUpdated(mapRef2RefFlowFunction_19);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_31()) {
      isDirty_binaryMapToRefFlowFunction_31 = binaryMapToRefFlowFunction_31.map();
      if (isDirty_binaryMapToRefFlowFunction_31) {
        mapRef2RefFlowFunction_33.inputUpdated(binaryMapToRefFlowFunction_31);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_20()) {
      isDirty_mapRef2RefFlowFunction_20 = mapRef2RefFlowFunction_20.map();
      if (isDirty_mapRef2RefFlowFunction_20) {
        mergeFlowFunction_22.inputStreamUpdated(mapRef2RefFlowFunction_20);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_33()) {
      isDirty_mapRef2RefFlowFunction_33 = mapRef2RefFlowFunction_33.map();
      if (isDirty_mapRef2RefFlowFunction_33) {
        binaryMapToRefFlowFunction_35.input2Updated(mapRef2RefFlowFunction_33);
        mapRef2RefFlowFunction_60.inputUpdated(mapRef2RefFlowFunction_33);
        binaryMapToRefFlowFunction_43.input2Updated(mapRef2RefFlowFunction_33);
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
        mapRef2RefFlowFunction_39.inputUpdated(mapRef2RefFlowFunction_37);
        mapRef2RefFlowFunction_64.inputUpdated(mapRef2RefFlowFunction_37);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_39()) {
      isDirty_mapRef2RefFlowFunction_39 = mapRef2RefFlowFunction_39.map();
      if (isDirty_mapRef2RefFlowFunction_39) {
        pnl.inputUpdated(mapRef2RefFlowFunction_39);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_52()) {
      isDirty_mapRef2RefFlowFunction_52 = mapRef2RefFlowFunction_52.map();
      if (isDirty_mapRef2RefFlowFunction_52) {
        positionMap.inputUpdated(mapRef2RefFlowFunction_52);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_60()) {
      isDirty_mapRef2RefFlowFunction_60 = mapRef2RefFlowFunction_60.map();
      if (isDirty_mapRef2RefFlowFunction_60) {
        rates.inputUpdated(mapRef2RefFlowFunction_60);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_64()) {
      isDirty_mapRef2RefFlowFunction_64 = mapRef2RefFlowFunction_64.map();
      if (isDirty_mapRef2RefFlowFunction_64) {
        mtmPositionMap.inputUpdated(mapRef2RefFlowFunction_64);
      }
    }
    if (guardCheck_mergeFlowFunction_22()) {
      isDirty_mergeFlowFunction_22 = mergeFlowFunction_22.publishMerge();
      if (isDirty_mergeFlowFunction_22) {
        mapRef2RefFlowFunction_24.inputUpdated(mergeFlowFunction_22);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_24()) {
      isDirty_mapRef2RefFlowFunction_24 = mapRef2RefFlowFunction_24.map();
      if (isDirty_mapRef2RefFlowFunction_24) {
        mapRef2RefFlowFunction_26.inputUpdated(mapRef2RefFlowFunction_24);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_26()) {
      isDirty_mapRef2RefFlowFunction_26 = mapRef2RefFlowFunction_26.map();
      if (isDirty_mapRef2RefFlowFunction_26) {
        binaryMapToRefFlowFunction_43.inputUpdated(mapRef2RefFlowFunction_26);
        mapRef2RefFlowFunction_56.inputUpdated(mapRef2RefFlowFunction_26);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_43()) {
      isDirty_binaryMapToRefFlowFunction_43 = binaryMapToRefFlowFunction_43.map();
      if (isDirty_binaryMapToRefFlowFunction_43) {
        mapRef2RefFlowFunction_45.inputUpdated(binaryMapToRefFlowFunction_43);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_45()) {
      isDirty_mapRef2RefFlowFunction_45 = mapRef2RefFlowFunction_45.map();
      if (isDirty_mapRef2RefFlowFunction_45) {
        mapRef2RefFlowFunction_47.inputUpdated(mapRef2RefFlowFunction_45);
        mapRef2RefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_45);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_47()) {
      isDirty_mapRef2RefFlowFunction_47 = mapRef2RefFlowFunction_47.map();
      if (isDirty_mapRef2RefFlowFunction_47) {
        tradeFees.inputUpdated(mapRef2RefFlowFunction_47);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_56()) {
      isDirty_mapRef2RefFlowFunction_56 = mapRef2RefFlowFunction_56.map();
      if (isDirty_mapRef2RefFlowFunction_56) {
        feePositionMap.inputUpdated(mapRef2RefFlowFunction_56);
      }
    }
    if (guardCheck_feePositionMap()) {
      isDirty_feePositionMap = feePositionMap.map();
      if (isDirty_feePositionMap) {
        pushFlowFunction_58.inputUpdated(feePositionMap);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_68()) {
      isDirty_mapRef2RefFlowFunction_68 = mapRef2RefFlowFunction_68.map();
      if (isDirty_mapRef2RefFlowFunction_68) {
        mtmFeePositionMap.inputUpdated(mapRef2RefFlowFunction_68);
      }
    }
    if (guardCheck_mtmFeePositionMap()) {
      isDirty_mtmFeePositionMap = mtmFeePositionMap.map();
      if (isDirty_mtmFeePositionMap) {
        pushFlowFunction_70.inputUpdated(mtmFeePositionMap);
      }
    }
    if (guardCheck_mtmPositionMap()) {
      isDirty_mtmPositionMap = mtmPositionMap.map();
      if (isDirty_mtmPositionMap) {
        pushFlowFunction_66.inputUpdated(mtmPositionMap);
      }
    }
    if (guardCheck_pnl()) {
      isDirty_pnl = pnl.map();
      if (isDirty_pnl) {
        pushFlowFunction_72.inputUpdated(pnl);
        netPnl.inputUpdated(pnl);
      }
    }
    if (guardCheck_positionMap()) {
      isDirty_positionMap = positionMap.map();
      if (isDirty_positionMap) {
        pushFlowFunction_54.inputUpdated(positionMap);
      }
    }
    if (guardCheck_pushFlowFunction_54()) {
      isDirty_pushFlowFunction_54 = pushFlowFunction_54.push();
    }
    if (guardCheck_pushFlowFunction_58()) {
      isDirty_pushFlowFunction_58 = pushFlowFunction_58.push();
    }
    if (guardCheck_pushFlowFunction_66()) {
      isDirty_pushFlowFunction_66 = pushFlowFunction_66.push();
    }
    if (guardCheck_pushFlowFunction_70()) {
      isDirty_pushFlowFunction_70 = pushFlowFunction_70.push();
    }
    if (guardCheck_pushFlowFunction_72()) {
      isDirty_pushFlowFunction_72 = pushFlowFunction_72.push();
    }
    if (guardCheck_rates()) {
      isDirty_rates = rates.map();
      if (isDirty_rates) {
        pushFlowFunction_62.inputUpdated(rates);
      }
    }
    if (guardCheck_pushFlowFunction_62()) {
      isDirty_pushFlowFunction_62 = pushFlowFunction_62.push();
    }
    if (guardCheck_tradeFees()) {
      isDirty_tradeFees = tradeFees.map();
      if (isDirty_tradeFees) {
        netPnl.input2Updated(tradeFees);
        pushFlowFunction_71.inputUpdated(tradeFees);
      }
    }
    if (guardCheck_netPnl()) {
      isDirty_netPnl = netPnl.map();
      if (isDirty_netPnl) {
        pushFlowFunction_73.inputUpdated(netPnl);
      }
    }
    if (guardCheck_pushFlowFunction_71()) {
      isDirty_pushFlowFunction_71 = pushFlowFunction_71.push();
    }
    if (guardCheck_pushFlowFunction_73()) {
      isDirty_pushFlowFunction_73 = pushFlowFunction_73.push();
    }
    afterEvent();
  }

  public void handleEvent(TradeBatch typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_handlerTradeBatch = handlerTradeBatch.onEvent(typedEvent);
    if (isDirty_handlerTradeBatch) {
      flatMapFlowFunction_1.inputUpdatedAndFlatMap(handlerTradeBatch);
      mapRef2RefFlowFunction_21.inputUpdated(handlerTradeBatch);
    }
    if (guardCheck_mapRef2RefFlowFunction_21()) {
      isDirty_mapRef2RefFlowFunction_21 = mapRef2RefFlowFunction_21.map();
      if (isDirty_mapRef2RefFlowFunction_21) {
        mergeFlowFunction_22.inputStreamUpdated(mapRef2RefFlowFunction_21);
      }
    }
    if (guardCheck_mergeFlowFunction_22()) {
      isDirty_mergeFlowFunction_22 = mergeFlowFunction_22.publishMerge();
      if (isDirty_mergeFlowFunction_22) {
        mapRef2RefFlowFunction_24.inputUpdated(mergeFlowFunction_22);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_24()) {
      isDirty_mapRef2RefFlowFunction_24 = mapRef2RefFlowFunction_24.map();
      if (isDirty_mapRef2RefFlowFunction_24) {
        mapRef2RefFlowFunction_26.inputUpdated(mapRef2RefFlowFunction_24);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_26()) {
      isDirty_mapRef2RefFlowFunction_26 = mapRef2RefFlowFunction_26.map();
      if (isDirty_mapRef2RefFlowFunction_26) {
        binaryMapToRefFlowFunction_43.inputUpdated(mapRef2RefFlowFunction_26);
        mapRef2RefFlowFunction_56.inputUpdated(mapRef2RefFlowFunction_26);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_43()) {
      isDirty_binaryMapToRefFlowFunction_43 = binaryMapToRefFlowFunction_43.map();
      if (isDirty_binaryMapToRefFlowFunction_43) {
        mapRef2RefFlowFunction_45.inputUpdated(binaryMapToRefFlowFunction_43);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_45()) {
      isDirty_mapRef2RefFlowFunction_45 = mapRef2RefFlowFunction_45.map();
      if (isDirty_mapRef2RefFlowFunction_45) {
        mapRef2RefFlowFunction_47.inputUpdated(mapRef2RefFlowFunction_45);
        mapRef2RefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_45);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_47()) {
      isDirty_mapRef2RefFlowFunction_47 = mapRef2RefFlowFunction_47.map();
      if (isDirty_mapRef2RefFlowFunction_47) {
        tradeFees.inputUpdated(mapRef2RefFlowFunction_47);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_56()) {
      isDirty_mapRef2RefFlowFunction_56 = mapRef2RefFlowFunction_56.map();
      if (isDirty_mapRef2RefFlowFunction_56) {
        feePositionMap.inputUpdated(mapRef2RefFlowFunction_56);
      }
    }
    if (guardCheck_feePositionMap()) {
      isDirty_feePositionMap = feePositionMap.map();
      if (isDirty_feePositionMap) {
        pushFlowFunction_58.inputUpdated(feePositionMap);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_68()) {
      isDirty_mapRef2RefFlowFunction_68 = mapRef2RefFlowFunction_68.map();
      if (isDirty_mapRef2RefFlowFunction_68) {
        mtmFeePositionMap.inputUpdated(mapRef2RefFlowFunction_68);
      }
    }
    if (guardCheck_mtmFeePositionMap()) {
      isDirty_mtmFeePositionMap = mtmFeePositionMap.map();
      if (isDirty_mtmFeePositionMap) {
        pushFlowFunction_70.inputUpdated(mtmFeePositionMap);
      }
    }
    if (guardCheck_pushFlowFunction_58()) {
      isDirty_pushFlowFunction_58 = pushFlowFunction_58.push();
    }
    if (guardCheck_pushFlowFunction_70()) {
      isDirty_pushFlowFunction_70 = pushFlowFunction_70.push();
    }
    if (guardCheck_tradeFees()) {
      isDirty_tradeFees = tradeFees.map();
      if (isDirty_tradeFees) {
        netPnl.input2Updated(tradeFees);
        pushFlowFunction_71.inputUpdated(tradeFees);
      }
    }
    if (guardCheck_netPnl()) {
      isDirty_netPnl = netPnl.map();
      if (isDirty_netPnl) {
        pushFlowFunction_73.inputUpdated(netPnl);
      }
    }
    if (guardCheck_pushFlowFunction_71()) {
      isDirty_pushFlowFunction_71 = pushFlowFunction_71.push();
    }
    if (guardCheck_pushFlowFunction_73()) {
      isDirty_pushFlowFunction_73 = pushFlowFunction_73.push();
    }
    afterEvent();
  }

  public void handleEvent(SymbolLookup typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    symbolLookupNode_0.setSymbolLookup(typedEvent);
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
    auditor.nodeRegistered(callbackImpl_74, "callbackImpl_74");
    auditor.nodeRegistered(callbackImpl_75, "callbackImpl_75");
    auditor.nodeRegistered(doubleSumFlowFunction_194, "doubleSumFlowFunction_194");
    auditor.nodeRegistered(doubleSumFlowFunction_282, "doubleSumFlowFunction_282");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_11, "binaryMapToRefFlowFunction_11");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_15, "binaryMapToRefFlowFunction_15");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_31, "binaryMapToRefFlowFunction_31");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_35, "binaryMapToRefFlowFunction_35");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_43, "binaryMapToRefFlowFunction_43");
    auditor.nodeRegistered(netPnl, "netPnl");
    auditor.nodeRegistered(filterFlowFunction_28, "filterFlowFunction_28");
    auditor.nodeRegistered(flatMapFlowFunction_1, "flatMapFlowFunction_1");
    auditor.nodeRegistered(flatMapFlowFunction_3, "flatMapFlowFunction_3");
    auditor.nodeRegistered(feePositionMap, "feePositionMap");
    auditor.nodeRegistered(mapRef2RefFlowFunction_5, "mapRef2RefFlowFunction_5");
    auditor.nodeRegistered(mapRef2RefFlowFunction_7, "mapRef2RefFlowFunction_7");
    auditor.nodeRegistered(mapRef2RefFlowFunction_9, "mapRef2RefFlowFunction_9");
    auditor.nodeRegistered(mapRef2RefFlowFunction_13, "mapRef2RefFlowFunction_13");
    auditor.nodeRegistered(mapRef2RefFlowFunction_17, "mapRef2RefFlowFunction_17");
    auditor.nodeRegistered(mapRef2RefFlowFunction_19, "mapRef2RefFlowFunction_19");
    auditor.nodeRegistered(mapRef2RefFlowFunction_20, "mapRef2RefFlowFunction_20");
    auditor.nodeRegistered(mapRef2RefFlowFunction_21, "mapRef2RefFlowFunction_21");
    auditor.nodeRegistered(mapRef2RefFlowFunction_24, "mapRef2RefFlowFunction_24");
    auditor.nodeRegistered(mapRef2RefFlowFunction_26, "mapRef2RefFlowFunction_26");
    auditor.nodeRegistered(mapRef2RefFlowFunction_30, "mapRef2RefFlowFunction_30");
    auditor.nodeRegistered(mapRef2RefFlowFunction_33, "mapRef2RefFlowFunction_33");
    auditor.nodeRegistered(mapRef2RefFlowFunction_37, "mapRef2RefFlowFunction_37");
    auditor.nodeRegistered(mapRef2RefFlowFunction_39, "mapRef2RefFlowFunction_39");
    auditor.nodeRegistered(mapRef2RefFlowFunction_45, "mapRef2RefFlowFunction_45");
    auditor.nodeRegistered(mapRef2RefFlowFunction_47, "mapRef2RefFlowFunction_47");
    auditor.nodeRegistered(mapRef2RefFlowFunction_52, "mapRef2RefFlowFunction_52");
    auditor.nodeRegistered(mapRef2RefFlowFunction_56, "mapRef2RefFlowFunction_56");
    auditor.nodeRegistered(mapRef2RefFlowFunction_60, "mapRef2RefFlowFunction_60");
    auditor.nodeRegistered(mapRef2RefFlowFunction_64, "mapRef2RefFlowFunction_64");
    auditor.nodeRegistered(mapRef2RefFlowFunction_68, "mapRef2RefFlowFunction_68");
    auditor.nodeRegistered(mtmFeePositionMap, "mtmFeePositionMap");
    auditor.nodeRegistered(mtmPositionMap, "mtmPositionMap");
    auditor.nodeRegistered(pnl, "pnl");
    auditor.nodeRegistered(positionMap, "positionMap");
    auditor.nodeRegistered(rates, "rates");
    auditor.nodeRegistered(tradeFees, "tradeFees");
    auditor.nodeRegistered(mergeFlowFunction_2, "mergeFlowFunction_2");
    auditor.nodeRegistered(mergeFlowFunction_22, "mergeFlowFunction_22");
    auditor.nodeRegistered(pushFlowFunction_54, "pushFlowFunction_54");
    auditor.nodeRegistered(pushFlowFunction_58, "pushFlowFunction_58");
    auditor.nodeRegistered(pushFlowFunction_62, "pushFlowFunction_62");
    auditor.nodeRegistered(pushFlowFunction_66, "pushFlowFunction_66");
    auditor.nodeRegistered(pushFlowFunction_70, "pushFlowFunction_70");
    auditor.nodeRegistered(pushFlowFunction_71, "pushFlowFunction_71");
    auditor.nodeRegistered(pushFlowFunction_72, "pushFlowFunction_72");
    auditor.nodeRegistered(pushFlowFunction_73, "pushFlowFunction_73");
    auditor.nodeRegistered(emptyGroupBy_99, "emptyGroupBy_99");
    auditor.nodeRegistered(emptyGroupBy_109, "emptyGroupBy_109");
    auditor.nodeRegistered(emptyGroupBy_132, "emptyGroupBy_132");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_4, "groupByFlowFunctionWrapper_4");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_6, "groupByFlowFunctionWrapper_6");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_8, "groupByFlowFunctionWrapper_8");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_23, "groupByFlowFunctionWrapper_23");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_29, "groupByFlowFunctionWrapper_29");
    auditor.nodeRegistered(groupByMapFlowFunction_12, "groupByMapFlowFunction_12");
    auditor.nodeRegistered(groupByMapFlowFunction_16, "groupByMapFlowFunction_16");
    auditor.nodeRegistered(groupByMapFlowFunction_36, "groupByMapFlowFunction_36");
    auditor.nodeRegistered(groupByMapFlowFunction_44, "groupByMapFlowFunction_44");
    auditor.nodeRegistered(groupByMapFlowFunction_51, "groupByMapFlowFunction_51");
    auditor.nodeRegistered(groupByMapFlowFunction_55, "groupByMapFlowFunction_55");
    auditor.nodeRegistered(groupByMapFlowFunction_59, "groupByMapFlowFunction_59");
    auditor.nodeRegistered(groupByMapFlowFunction_63, "groupByMapFlowFunction_63");
    auditor.nodeRegistered(groupByMapFlowFunction_67, "groupByMapFlowFunction_67");
    auditor.nodeRegistered(groupByReduceFlowFunction_38, "groupByReduceFlowFunction_38");
    auditor.nodeRegistered(groupByReduceFlowFunction_46, "groupByReduceFlowFunction_46");
    auditor.nodeRegistered(leftJoin_34, "leftJoin_34");
    auditor.nodeRegistered(leftJoin_42, "leftJoin_42");
    auditor.nodeRegistered(outerJoin_10, "outerJoin_10");
    auditor.nodeRegistered(outerJoin_14, "outerJoin_14");
    auditor.nodeRegistered(defaultValue_18, "defaultValue_18");
    auditor.nodeRegistered(defaultValue_25, "defaultValue_25");
    auditor.nodeRegistered(defaultValue_32, "defaultValue_32");
    auditor.nodeRegistered(defaultValue_40, "defaultValue_40");
    auditor.nodeRegistered(defaultValue_48, "defaultValue_48");
    auditor.nodeRegistered(subscriptionManager, "subscriptionManager");
    auditor.nodeRegistered(handlerMidPrice, "handlerMidPrice");
    auditor.nodeRegistered(handlerMtmInstrument, "handlerMtmInstrument");
    auditor.nodeRegistered(handlerPositionSnapshot, "handlerPositionSnapshot");
    auditor.nodeRegistered(
        handlerSignal_positionSnapshotReset, "handlerSignal_positionSnapshotReset");
    auditor.nodeRegistered(handlerSignal_positionUpdate, "handlerSignal_positionUpdate");
    auditor.nodeRegistered(handlerTrade, "handlerTrade");
    auditor.nodeRegistered(handlerTradeBatch, "handlerTradeBatch");
    auditor.nodeRegistered(context, "context");
    auditor.nodeRegistered(feePositionListener, "feePositionListener");
    auditor.nodeRegistered(mtmFeePositionListener, "mtmFeePositionListener");
    auditor.nodeRegistered(mtmPositionListener, "mtmPositionListener");
    auditor.nodeRegistered(netPnlListener, "netPnlListener");
    auditor.nodeRegistered(pnlListener, "pnlListener");
    auditor.nodeRegistered(positionListener, "positionListener");
    auditor.nodeRegistered(rateListener, "rateListener");
    auditor.nodeRegistered(tradeFeesListener, "tradeFeesListener");
    auditor.nodeRegistered(derivedRateNode_27, "derivedRateNode_27");
    auditor.nodeRegistered(symbolLookupNode_0, "symbolLookupNode_0");
    auditor.nodeRegistered(double_220, "double_220");
    auditor.nodeRegistered(double_302, "double_302");
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
    isDirty_binaryMapToRefFlowFunction_11 = false;
    isDirty_binaryMapToRefFlowFunction_15 = false;
    isDirty_binaryMapToRefFlowFunction_31 = false;
    isDirty_binaryMapToRefFlowFunction_35 = false;
    isDirty_binaryMapToRefFlowFunction_43 = false;
    isDirty_callbackImpl_74 = false;
    isDirty_callbackImpl_75 = false;
    isDirty_clock = false;
    isDirty_derivedRateNode_27 = false;
    isDirty_feePositionMap = false;
    isDirty_filterFlowFunction_28 = false;
    isDirty_flatMapFlowFunction_1 = false;
    isDirty_flatMapFlowFunction_3 = false;
    isDirty_handlerMidPrice = false;
    isDirty_handlerMtmInstrument = false;
    isDirty_handlerPositionSnapshot = false;
    isDirty_handlerSignal_positionSnapshotReset = false;
    isDirty_handlerSignal_positionUpdate = false;
    isDirty_handlerTrade = false;
    isDirty_handlerTradeBatch = false;
    isDirty_mapRef2RefFlowFunction_5 = false;
    isDirty_mapRef2RefFlowFunction_7 = false;
    isDirty_mapRef2RefFlowFunction_9 = false;
    isDirty_mapRef2RefFlowFunction_13 = false;
    isDirty_mapRef2RefFlowFunction_17 = false;
    isDirty_mapRef2RefFlowFunction_19 = false;
    isDirty_mapRef2RefFlowFunction_20 = false;
    isDirty_mapRef2RefFlowFunction_21 = false;
    isDirty_mapRef2RefFlowFunction_24 = false;
    isDirty_mapRef2RefFlowFunction_26 = false;
    isDirty_mapRef2RefFlowFunction_30 = false;
    isDirty_mapRef2RefFlowFunction_33 = false;
    isDirty_mapRef2RefFlowFunction_37 = false;
    isDirty_mapRef2RefFlowFunction_39 = false;
    isDirty_mapRef2RefFlowFunction_45 = false;
    isDirty_mapRef2RefFlowFunction_47 = false;
    isDirty_mapRef2RefFlowFunction_52 = false;
    isDirty_mapRef2RefFlowFunction_56 = false;
    isDirty_mapRef2RefFlowFunction_60 = false;
    isDirty_mapRef2RefFlowFunction_64 = false;
    isDirty_mapRef2RefFlowFunction_68 = false;
    isDirty_mergeFlowFunction_2 = false;
    isDirty_mergeFlowFunction_22 = false;
    isDirty_mtmFeePositionMap = false;
    isDirty_mtmPositionMap = false;
    isDirty_netPnl = false;
    isDirty_pnl = false;
    isDirty_positionMap = false;
    isDirty_pushFlowFunction_54 = false;
    isDirty_pushFlowFunction_58 = false;
    isDirty_pushFlowFunction_62 = false;
    isDirty_pushFlowFunction_66 = false;
    isDirty_pushFlowFunction_70 = false;
    isDirty_pushFlowFunction_71 = false;
    isDirty_pushFlowFunction_72 = false;
    isDirty_pushFlowFunction_73 = false;
    isDirty_rates = false;
    isDirty_tradeFees = false;
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
          binaryMapToRefFlowFunction_11, () -> isDirty_binaryMapToRefFlowFunction_11);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_15, () -> isDirty_binaryMapToRefFlowFunction_15);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_31, () -> isDirty_binaryMapToRefFlowFunction_31);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_35, () -> isDirty_binaryMapToRefFlowFunction_35);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_43, () -> isDirty_binaryMapToRefFlowFunction_43);
      dirtyFlagSupplierMap.put(callbackImpl_74, () -> isDirty_callbackImpl_74);
      dirtyFlagSupplierMap.put(callbackImpl_75, () -> isDirty_callbackImpl_75);
      dirtyFlagSupplierMap.put(clock, () -> isDirty_clock);
      dirtyFlagSupplierMap.put(derivedRateNode_27, () -> isDirty_derivedRateNode_27);
      dirtyFlagSupplierMap.put(feePositionMap, () -> isDirty_feePositionMap);
      dirtyFlagSupplierMap.put(filterFlowFunction_28, () -> isDirty_filterFlowFunction_28);
      dirtyFlagSupplierMap.put(flatMapFlowFunction_1, () -> isDirty_flatMapFlowFunction_1);
      dirtyFlagSupplierMap.put(flatMapFlowFunction_3, () -> isDirty_flatMapFlowFunction_3);
      dirtyFlagSupplierMap.put(handlerMidPrice, () -> isDirty_handlerMidPrice);
      dirtyFlagSupplierMap.put(handlerMtmInstrument, () -> isDirty_handlerMtmInstrument);
      dirtyFlagSupplierMap.put(handlerPositionSnapshot, () -> isDirty_handlerPositionSnapshot);
      dirtyFlagSupplierMap.put(
          handlerSignal_positionSnapshotReset, () -> isDirty_handlerSignal_positionSnapshotReset);
      dirtyFlagSupplierMap.put(
          handlerSignal_positionUpdate, () -> isDirty_handlerSignal_positionUpdate);
      dirtyFlagSupplierMap.put(handlerTrade, () -> isDirty_handlerTrade);
      dirtyFlagSupplierMap.put(handlerTradeBatch, () -> isDirty_handlerTradeBatch);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_13, () -> isDirty_mapRef2RefFlowFunction_13);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_17, () -> isDirty_mapRef2RefFlowFunction_17);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_19, () -> isDirty_mapRef2RefFlowFunction_19);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_20, () -> isDirty_mapRef2RefFlowFunction_20);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_21, () -> isDirty_mapRef2RefFlowFunction_21);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_24, () -> isDirty_mapRef2RefFlowFunction_24);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_26, () -> isDirty_mapRef2RefFlowFunction_26);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_30, () -> isDirty_mapRef2RefFlowFunction_30);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_33, () -> isDirty_mapRef2RefFlowFunction_33);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_37, () -> isDirty_mapRef2RefFlowFunction_37);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_39, () -> isDirty_mapRef2RefFlowFunction_39);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_45, () -> isDirty_mapRef2RefFlowFunction_45);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_47, () -> isDirty_mapRef2RefFlowFunction_47);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_5, () -> isDirty_mapRef2RefFlowFunction_5);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_52, () -> isDirty_mapRef2RefFlowFunction_52);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_56, () -> isDirty_mapRef2RefFlowFunction_56);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_60, () -> isDirty_mapRef2RefFlowFunction_60);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_64, () -> isDirty_mapRef2RefFlowFunction_64);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_68, () -> isDirty_mapRef2RefFlowFunction_68);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_7, () -> isDirty_mapRef2RefFlowFunction_7);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_9, () -> isDirty_mapRef2RefFlowFunction_9);
      dirtyFlagSupplierMap.put(mergeFlowFunction_2, () -> isDirty_mergeFlowFunction_2);
      dirtyFlagSupplierMap.put(mergeFlowFunction_22, () -> isDirty_mergeFlowFunction_22);
      dirtyFlagSupplierMap.put(mtmFeePositionMap, () -> isDirty_mtmFeePositionMap);
      dirtyFlagSupplierMap.put(mtmPositionMap, () -> isDirty_mtmPositionMap);
      dirtyFlagSupplierMap.put(netPnl, () -> isDirty_netPnl);
      dirtyFlagSupplierMap.put(pnl, () -> isDirty_pnl);
      dirtyFlagSupplierMap.put(positionMap, () -> isDirty_positionMap);
      dirtyFlagSupplierMap.put(pushFlowFunction_54, () -> isDirty_pushFlowFunction_54);
      dirtyFlagSupplierMap.put(pushFlowFunction_58, () -> isDirty_pushFlowFunction_58);
      dirtyFlagSupplierMap.put(pushFlowFunction_62, () -> isDirty_pushFlowFunction_62);
      dirtyFlagSupplierMap.put(pushFlowFunction_66, () -> isDirty_pushFlowFunction_66);
      dirtyFlagSupplierMap.put(pushFlowFunction_70, () -> isDirty_pushFlowFunction_70);
      dirtyFlagSupplierMap.put(pushFlowFunction_71, () -> isDirty_pushFlowFunction_71);
      dirtyFlagSupplierMap.put(pushFlowFunction_72, () -> isDirty_pushFlowFunction_72);
      dirtyFlagSupplierMap.put(pushFlowFunction_73, () -> isDirty_pushFlowFunction_73);
      dirtyFlagSupplierMap.put(rates, () -> isDirty_rates);
      dirtyFlagSupplierMap.put(tradeFees, () -> isDirty_tradeFees);
    }
    return dirtyFlagSupplierMap.getOrDefault(node, StaticEventProcessor.ALWAYS_FALSE);
  }

  @Override
  public void setDirty(Object node, boolean dirtyFlag) {
    if (dirtyFlagUpdateMap.isEmpty()) {
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_11, (b) -> isDirty_binaryMapToRefFlowFunction_11 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_15, (b) -> isDirty_binaryMapToRefFlowFunction_15 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_31, (b) -> isDirty_binaryMapToRefFlowFunction_31 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_35, (b) -> isDirty_binaryMapToRefFlowFunction_35 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_43, (b) -> isDirty_binaryMapToRefFlowFunction_43 = b);
      dirtyFlagUpdateMap.put(callbackImpl_74, (b) -> isDirty_callbackImpl_74 = b);
      dirtyFlagUpdateMap.put(callbackImpl_75, (b) -> isDirty_callbackImpl_75 = b);
      dirtyFlagUpdateMap.put(clock, (b) -> isDirty_clock = b);
      dirtyFlagUpdateMap.put(derivedRateNode_27, (b) -> isDirty_derivedRateNode_27 = b);
      dirtyFlagUpdateMap.put(feePositionMap, (b) -> isDirty_feePositionMap = b);
      dirtyFlagUpdateMap.put(filterFlowFunction_28, (b) -> isDirty_filterFlowFunction_28 = b);
      dirtyFlagUpdateMap.put(flatMapFlowFunction_1, (b) -> isDirty_flatMapFlowFunction_1 = b);
      dirtyFlagUpdateMap.put(flatMapFlowFunction_3, (b) -> isDirty_flatMapFlowFunction_3 = b);
      dirtyFlagUpdateMap.put(handlerMidPrice, (b) -> isDirty_handlerMidPrice = b);
      dirtyFlagUpdateMap.put(handlerMtmInstrument, (b) -> isDirty_handlerMtmInstrument = b);
      dirtyFlagUpdateMap.put(handlerPositionSnapshot, (b) -> isDirty_handlerPositionSnapshot = b);
      dirtyFlagUpdateMap.put(
          handlerSignal_positionSnapshotReset,
          (b) -> isDirty_handlerSignal_positionSnapshotReset = b);
      dirtyFlagUpdateMap.put(
          handlerSignal_positionUpdate, (b) -> isDirty_handlerSignal_positionUpdate = b);
      dirtyFlagUpdateMap.put(handlerTrade, (b) -> isDirty_handlerTrade = b);
      dirtyFlagUpdateMap.put(handlerTradeBatch, (b) -> isDirty_handlerTradeBatch = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_13, (b) -> isDirty_mapRef2RefFlowFunction_13 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_17, (b) -> isDirty_mapRef2RefFlowFunction_17 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_19, (b) -> isDirty_mapRef2RefFlowFunction_19 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_20, (b) -> isDirty_mapRef2RefFlowFunction_20 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_21, (b) -> isDirty_mapRef2RefFlowFunction_21 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_24, (b) -> isDirty_mapRef2RefFlowFunction_24 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_26, (b) -> isDirty_mapRef2RefFlowFunction_26 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_30, (b) -> isDirty_mapRef2RefFlowFunction_30 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_33, (b) -> isDirty_mapRef2RefFlowFunction_33 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_37, (b) -> isDirty_mapRef2RefFlowFunction_37 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_39, (b) -> isDirty_mapRef2RefFlowFunction_39 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_45, (b) -> isDirty_mapRef2RefFlowFunction_45 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_47, (b) -> isDirty_mapRef2RefFlowFunction_47 = b);
      dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_5, (b) -> isDirty_mapRef2RefFlowFunction_5 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_52, (b) -> isDirty_mapRef2RefFlowFunction_52 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_56, (b) -> isDirty_mapRef2RefFlowFunction_56 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_60, (b) -> isDirty_mapRef2RefFlowFunction_60 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_64, (b) -> isDirty_mapRef2RefFlowFunction_64 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_68, (b) -> isDirty_mapRef2RefFlowFunction_68 = b);
      dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_7, (b) -> isDirty_mapRef2RefFlowFunction_7 = b);
      dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_9, (b) -> isDirty_mapRef2RefFlowFunction_9 = b);
      dirtyFlagUpdateMap.put(mergeFlowFunction_2, (b) -> isDirty_mergeFlowFunction_2 = b);
      dirtyFlagUpdateMap.put(mergeFlowFunction_22, (b) -> isDirty_mergeFlowFunction_22 = b);
      dirtyFlagUpdateMap.put(mtmFeePositionMap, (b) -> isDirty_mtmFeePositionMap = b);
      dirtyFlagUpdateMap.put(mtmPositionMap, (b) -> isDirty_mtmPositionMap = b);
      dirtyFlagUpdateMap.put(netPnl, (b) -> isDirty_netPnl = b);
      dirtyFlagUpdateMap.put(pnl, (b) -> isDirty_pnl = b);
      dirtyFlagUpdateMap.put(positionMap, (b) -> isDirty_positionMap = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_54, (b) -> isDirty_pushFlowFunction_54 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_58, (b) -> isDirty_pushFlowFunction_58 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_62, (b) -> isDirty_pushFlowFunction_62 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_66, (b) -> isDirty_pushFlowFunction_66 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_70, (b) -> isDirty_pushFlowFunction_70 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_71, (b) -> isDirty_pushFlowFunction_71 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_72, (b) -> isDirty_pushFlowFunction_72 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_73, (b) -> isDirty_pushFlowFunction_73 = b);
      dirtyFlagUpdateMap.put(rates, (b) -> isDirty_rates = b);
      dirtyFlagUpdateMap.put(tradeFees, (b) -> isDirty_tradeFees = b);
    }
    dirtyFlagUpdateMap.get(node).accept(dirtyFlag);
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_11() {
    return isDirty_handlerSignal_positionUpdate
        | isDirty_mapRef2RefFlowFunction_7
        | isDirty_mapRef2RefFlowFunction_9;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_15() {
    return isDirty_mapRef2RefFlowFunction_5 | isDirty_mapRef2RefFlowFunction_13;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_31() {
    return isDirty_derivedRateNode_27
        | isDirty_mapRef2RefFlowFunction_19
        | isDirty_mapRef2RefFlowFunction_30;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_35() {
    return isDirty_mapRef2RefFlowFunction_19 | isDirty_mapRef2RefFlowFunction_33;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_43() {
    return isDirty_mapRef2RefFlowFunction_26 | isDirty_mapRef2RefFlowFunction_33;
  }

  private boolean guardCheck_netPnl() {
    return isDirty_handlerSignal_positionUpdate | isDirty_pnl | isDirty_tradeFees;
  }

  private boolean guardCheck_filterFlowFunction_28() {
    return isDirty_derivedRateNode_27 | isDirty_handlerMidPrice;
  }

  private boolean guardCheck_flatMapFlowFunction_1() {
    return isDirty_callbackImpl_74;
  }

  private boolean guardCheck_flatMapFlowFunction_3() {
    return isDirty_callbackImpl_75;
  }

  private boolean guardCheck_feePositionMap() {
    return isDirty_mapRef2RefFlowFunction_56;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_5() {
    return isDirty_flatMapFlowFunction_3
        | isDirty_handlerSignal_positionSnapshotReset
        | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_7() {
    return isDirty_handlerSignal_positionSnapshotReset | isDirty_mergeFlowFunction_2;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_9() {
    return isDirty_handlerSignal_positionSnapshotReset | isDirty_mergeFlowFunction_2;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_13() {
    return isDirty_binaryMapToRefFlowFunction_11;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_17() {
    return isDirty_binaryMapToRefFlowFunction_15;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_19() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_17;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_20() {
    return isDirty_mergeFlowFunction_2;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_21() {
    return isDirty_handlerTradeBatch;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_24() {
    return isDirty_mergeFlowFunction_22;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_26() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_24;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_30() {
    return isDirty_filterFlowFunction_28 | isDirty_handlerMtmInstrument;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_33() {
    return isDirty_binaryMapToRefFlowFunction_31;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_37() {
    return isDirty_binaryMapToRefFlowFunction_35 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_39() {
    return isDirty_mapRef2RefFlowFunction_37;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_45() {
    return isDirty_binaryMapToRefFlowFunction_43 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_47() {
    return isDirty_mapRef2RefFlowFunction_45;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_52() {
    return isDirty_mapRef2RefFlowFunction_19;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_56() {
    return isDirty_mapRef2RefFlowFunction_26;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_60() {
    return isDirty_mapRef2RefFlowFunction_33;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_64() {
    return isDirty_mapRef2RefFlowFunction_37;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_68() {
    return isDirty_mapRef2RefFlowFunction_45;
  }

  private boolean guardCheck_mtmFeePositionMap() {
    return isDirty_mapRef2RefFlowFunction_68;
  }

  private boolean guardCheck_mtmPositionMap() {
    return isDirty_mapRef2RefFlowFunction_64;
  }

  private boolean guardCheck_pnl() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_39;
  }

  private boolean guardCheck_positionMap() {
    return isDirty_mapRef2RefFlowFunction_52;
  }

  private boolean guardCheck_rates() {
    return isDirty_mapRef2RefFlowFunction_60;
  }

  private boolean guardCheck_tradeFees() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_47;
  }

  private boolean guardCheck_mergeFlowFunction_2() {
    return isDirty_flatMapFlowFunction_1 | isDirty_handlerTrade;
  }

  private boolean guardCheck_mergeFlowFunction_22() {
    return isDirty_mapRef2RefFlowFunction_20 | isDirty_mapRef2RefFlowFunction_21;
  }

  private boolean guardCheck_pushFlowFunction_54() {
    return isDirty_positionMap;
  }

  private boolean guardCheck_pushFlowFunction_58() {
    return isDirty_feePositionMap;
  }

  private boolean guardCheck_pushFlowFunction_62() {
    return isDirty_rates;
  }

  private boolean guardCheck_pushFlowFunction_66() {
    return isDirty_mtmPositionMap;
  }

  private boolean guardCheck_pushFlowFunction_70() {
    return isDirty_mtmFeePositionMap;
  }

  private boolean guardCheck_pushFlowFunction_71() {
    return isDirty_tradeFees;
  }

  private boolean guardCheck_pushFlowFunction_72() {
    return isDirty_pnl;
  }

  private boolean guardCheck_pushFlowFunction_73() {
    return isDirty_netPnl;
  }

  private boolean guardCheck_groupByFlowFunctionWrapper_29() {
    return isDirty_derivedRateNode_27;
  }

  private boolean guardCheck_context() {
    return isDirty_clock;
  }

  private boolean guardCheck_feePositionListener() {
    return isDirty_pushFlowFunction_58;
  }

  private boolean guardCheck_mtmFeePositionListener() {
    return isDirty_pushFlowFunction_70;
  }

  private boolean guardCheck_mtmPositionListener() {
    return isDirty_pushFlowFunction_66;
  }

  private boolean guardCheck_netPnlListener() {
    return isDirty_pushFlowFunction_73;
  }

  private boolean guardCheck_pnlListener() {
    return isDirty_pushFlowFunction_72;
  }

  private boolean guardCheck_positionListener() {
    return isDirty_pushFlowFunction_54;
  }

  private boolean guardCheck_rateListener() {
    return isDirty_pushFlowFunction_62;
  }

  private boolean guardCheck_tradeFeesListener() {
    return isDirty_pushFlowFunction_71;
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
