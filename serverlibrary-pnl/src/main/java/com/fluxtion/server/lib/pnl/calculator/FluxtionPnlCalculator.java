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
import com.fluxtion.server.lib.pnl.MarkToMarket;
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
  private final CallbackImpl callbackImpl_82 = new CallbackImpl<>(1, callbackDispatcher);
  private final CallbackImpl callbackImpl_83 = new CallbackImpl<>(2, callbackDispatcher);
  public final Clock clock = new Clock();
  private final DerivedRateNode derivedRateNode_27 = new DerivedRateNode();
  private final Double double_256 = Double.NaN;
  private final DefaultValue defaultValue_41 = new DefaultValue<>(Double.NaN);
  private final Double double_350 = 0.0d;
  private final DefaultValue defaultValue_49 = new DefaultValue<>(0.0);
  private final DoubleSumFlowFunction doubleSumFlowFunction_227 = new DoubleSumFlowFunction();
  private final DoubleSumFlowFunction doubleSumFlowFunction_327 = new DoubleSumFlowFunction();
  private final EmptyGroupBy emptyGroupBy_107 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_18 = new DefaultValue<>(emptyGroupBy_107);
  private final EmptyGroupBy emptyGroupBy_117 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_25 = new DefaultValue<>(emptyGroupBy_117);
  private final EmptyGroupBy emptyGroupBy_156 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_33 = new DefaultValue<>(emptyGroupBy_156);
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
  private final GroupByMapFlowFunction groupByMapFlowFunction_37 =
      new GroupByMapFlowFunction(MathUtil::mtmPositions);
  private final GroupByMapFlowFunction groupByMapFlowFunction_45 =
      new GroupByMapFlowFunction(MathUtil::mtmPositions);
  private final GroupByMapFlowFunction groupByMapFlowFunction_52 =
      new GroupByMapFlowFunction(Instrument::instrumentName);
  private final GroupByMapFlowFunction groupByMapFlowFunction_57 =
      new GroupByMapFlowFunction(Instrument::instrumentName);
  private final GroupByMapFlowFunction groupByMapFlowFunction_62 =
      new GroupByMapFlowFunction(Instrument::instrumentName);
  private final GroupByMapFlowFunction groupByMapFlowFunction_66 =
      new GroupByMapFlowFunction(Instrument::instrumentName);
  private final GroupByMapFlowFunction groupByMapFlowFunction_71 =
      new GroupByMapFlowFunction(Instrument::instrumentName);
  private final GroupByReduceFlowFunction groupByReduceFlowFunction_39 =
      new GroupByReduceFlowFunction(doubleSumFlowFunction_227);
  private final GroupByReduceFlowFunction groupByReduceFlowFunction_47 =
      new GroupByReduceFlowFunction(doubleSumFlowFunction_327);
  private final LeftJoin leftJoin_35 = new LeftJoin();
  private final LeftJoin leftJoin_43 = new LeftJoin();
  private final MarkToMarket markToMarket_897 = new MarkToMarket();
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
          mapRef2RefFlowFunction_30,
          mapRef2RefFlowFunction_19,
          derivedRateNode_27::trimDerivedRates);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_20 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_2, MathUtil::feePositionTrade);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_53 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_19, groupByMapFlowFunction_52::mapKeys);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_54 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_53, GroupBy<Object, Object>::toMap);
  private final MergeFlowFunction mergeFlowFunction_22 =
      new MergeFlowFunction<>(Arrays.asList(mapRef2RefFlowFunction_20, mapRef2RefFlowFunction_21));
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_24 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_22, groupByFlowFunctionWrapper_23::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_26 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_24, defaultValue_25::getOrDefault);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_32 =
      new BinaryMapToRefFlowFunction<>(
          binaryMapToRefFlowFunction_31,
          mapRef2RefFlowFunction_26,
          derivedRateNode_27::addMissingDerivedRates);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_34 =
      new MapRef2RefFlowFunction<>(binaryMapToRefFlowFunction_32, defaultValue_33::getOrDefault);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_36 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_19, mapRef2RefFlowFunction_34, leftJoin_35::join);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_44 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_26, mapRef2RefFlowFunction_34, leftJoin_43::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_38 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_36, groupByMapFlowFunction_37::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_40 =
      new MapRef2RefFlowFunction<>(
          mapRef2RefFlowFunction_38, groupByReduceFlowFunction_39::reduceValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_42 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_40, defaultValue_41::getOrDefault);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_46 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_44, groupByMapFlowFunction_45::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_48 =
      new MapRef2RefFlowFunction<>(
          mapRef2RefFlowFunction_46, groupByReduceFlowFunction_47::reduceValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_50 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_48, defaultValue_49::getOrDefault);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_51 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_42, mapRef2RefFlowFunction_50, Mappers::subtractDoubles);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_58 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_26, groupByMapFlowFunction_57::mapKeys);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_59 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_58, GroupBy<Object, Object>::toMap);
  public final PushFlowFunction feePositionMap =
      new PushFlowFunction<>(mapRef2RefFlowFunction_59, markToMarket_897::setFeesPositionMap);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_63 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_34, groupByMapFlowFunction_62::mapKeys);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_67 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_38, groupByMapFlowFunction_66::mapKeys);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_68 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_67, GroupBy<Object, Object>::toMap);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_72 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_46, groupByMapFlowFunction_71::mapKeys);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_73 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_72, GroupBy<Object, Object>::toMap);
  private final SinkPublisher mtmFeePositionListener =
      new SinkPublisher<>("mtmFeePositionListener");
  public final PushFlowFunction mtmFeePositionMap =
      new PushFlowFunction<>(mapRef2RefFlowFunction_73, markToMarket_897::setFeesMtmPositionMap);
  private final SinkPublisher mtmPositionListener = new SinkPublisher<>("mtmPositionListener");
  public final PushFlowFunction mtmPositionMap =
      new PushFlowFunction<>(mapRef2RefFlowFunction_68, markToMarket_897::setMtmPositionMap);
  public final PushFlowFunction netPnl =
      new PushFlowFunction<>(binaryMapToRefFlowFunction_51, markToMarket_897::setPnlNetFees);
  private final SinkPublisher netPnlListener = new SinkPublisher<>("netPnlListener");
  public final PushFlowFunction pnl =
      new PushFlowFunction<>(mapRef2RefFlowFunction_42, markToMarket_897::setTradePnl);
  private final SinkPublisher pnlListener = new SinkPublisher<>("pnlListener");
  private final SinkPublisher positionListener = new SinkPublisher<>("positionListener");
  public final PushFlowFunction positionMap =
      new PushFlowFunction<>(mapRef2RefFlowFunction_54, markToMarket_897::setPositionMap);
  private final PushFlowFunction pushFlowFunction_56 =
      new PushFlowFunction<>(positionMap, positionListener::publish);
  private final PushFlowFunction pushFlowFunction_61 =
      new PushFlowFunction<>(feePositionMap, feePositionListener::publish);
  private final PushFlowFunction pushFlowFunction_70 =
      new PushFlowFunction<>(mtmPositionMap, mtmPositionListener::publish);
  private final PushFlowFunction pushFlowFunction_75 =
      new PushFlowFunction<>(mtmFeePositionMap, mtmFeePositionListener::publish);
  private final PushFlowFunction pushFlowFunction_79 =
      new PushFlowFunction<>(pnl, pnlListener::publish);
  private final PushFlowFunction pushFlowFunction_81 =
      new PushFlowFunction<>(netPnl, netPnlListener::publish);
  private final SinkPublisher rateListener = new SinkPublisher<>("rateListener");
  public final MapRef2RefFlowFunction rates =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_63, GroupBy<Object, Object>::toMap);
  private final PushFlowFunction pushFlowFunction_65 =
      new PushFlowFunction<>(rates, rateListener::publish);
  public final ServiceRegistryNode serviceRegistry = new ServiceRegistryNode();
  public final PushFlowFunction tradeFees =
      new PushFlowFunction<>(mapRef2RefFlowFunction_50, markToMarket_897::setFees);
  private final SinkPublisher tradeFeesListener = new SinkPublisher<>("tradeFeesListener");
  private final PushFlowFunction pushFlowFunction_77 =
      new PushFlowFunction<>(tradeFees, tradeFeesListener::publish);
  private final SymbolLookupNode symbolLookupNode_0 = new SymbolLookupNode();
  private final ExportFunctionAuditEvent functionAudit = new ExportFunctionAuditEvent();
  //Dirty flags
  private boolean initCalled = false;
  private boolean processing = false;
  private boolean buffering = false;
  private final IdentityHashMap<Object, BooleanSupplier> dirtyFlagSupplierMap =
      new IdentityHashMap<>(66);
  private final IdentityHashMap<Object, Consumer<Boolean>> dirtyFlagUpdateMap =
      new IdentityHashMap<>(66);

  private boolean isDirty_binaryMapToRefFlowFunction_11 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_15 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_31 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_32 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_36 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_44 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_51 = false;
  private boolean isDirty_callbackImpl_82 = false;
  private boolean isDirty_callbackImpl_83 = false;
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
  private boolean isDirty_mapRef2RefFlowFunction_34 = false;
  private boolean isDirty_mapRef2RefFlowFunction_38 = false;
  private boolean isDirty_mapRef2RefFlowFunction_40 = false;
  private boolean isDirty_mapRef2RefFlowFunction_42 = false;
  private boolean isDirty_mapRef2RefFlowFunction_46 = false;
  private boolean isDirty_mapRef2RefFlowFunction_48 = false;
  private boolean isDirty_mapRef2RefFlowFunction_50 = false;
  private boolean isDirty_mapRef2RefFlowFunction_53 = false;
  private boolean isDirty_mapRef2RefFlowFunction_54 = false;
  private boolean isDirty_mapRef2RefFlowFunction_58 = false;
  private boolean isDirty_mapRef2RefFlowFunction_59 = false;
  private boolean isDirty_mapRef2RefFlowFunction_63 = false;
  private boolean isDirty_mapRef2RefFlowFunction_67 = false;
  private boolean isDirty_mapRef2RefFlowFunction_68 = false;
  private boolean isDirty_mapRef2RefFlowFunction_72 = false;
  private boolean isDirty_mapRef2RefFlowFunction_73 = false;
  private boolean isDirty_mergeFlowFunction_2 = false;
  private boolean isDirty_mergeFlowFunction_22 = false;
  private boolean isDirty_mtmFeePositionMap = false;
  private boolean isDirty_mtmPositionMap = false;
  private boolean isDirty_netPnl = false;
  private boolean isDirty_pnl = false;
  private boolean isDirty_positionMap = false;
  private boolean isDirty_pushFlowFunction_56 = false;
  private boolean isDirty_pushFlowFunction_61 = false;
  private boolean isDirty_pushFlowFunction_65 = false;
  private boolean isDirty_pushFlowFunction_70 = false;
  private boolean isDirty_pushFlowFunction_75 = false;
  private boolean isDirty_pushFlowFunction_77 = false;
  private boolean isDirty_pushFlowFunction_79 = false;
  private boolean isDirty_pushFlowFunction_81 = false;
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
    callbackImpl_82.dirtyStateMonitor = callbackDispatcher;
    callbackImpl_83.dirtyStateMonitor = callbackDispatcher;
    doubleSumFlowFunction_227.dirtyStateMonitor = callbackDispatcher;
    doubleSumFlowFunction_327.dirtyStateMonitor = callbackDispatcher;
    binaryMapToRefFlowFunction_11.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_11.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    binaryMapToRefFlowFunction_15.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_31.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_32.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_36.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_44.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_51.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_51.setUpdateTriggerNode(handlerSignal_positionUpdate);
    filterFlowFunction_28.setEventProcessorContext(context);
    flatMapFlowFunction_1.callback = callbackImpl_82;
    flatMapFlowFunction_1.dirtyStateMonitor = callbackDispatcher;
    flatMapFlowFunction_3.callback = callbackImpl_83;
    flatMapFlowFunction_3.dirtyStateMonitor = callbackDispatcher;
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
    mapRef2RefFlowFunction_34.setEventProcessorContext(context);
    mapRef2RefFlowFunction_38.setEventProcessorContext(context);
    mapRef2RefFlowFunction_38.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_40.setEventProcessorContext(context);
    mapRef2RefFlowFunction_42.setEventProcessorContext(context);
    mapRef2RefFlowFunction_42.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_46.setEventProcessorContext(context);
    mapRef2RefFlowFunction_46.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_48.setEventProcessorContext(context);
    mapRef2RefFlowFunction_50.setEventProcessorContext(context);
    mapRef2RefFlowFunction_50.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_53.setEventProcessorContext(context);
    mapRef2RefFlowFunction_54.setEventProcessorContext(context);
    mapRef2RefFlowFunction_58.setEventProcessorContext(context);
    mapRef2RefFlowFunction_59.setEventProcessorContext(context);
    mapRef2RefFlowFunction_63.setEventProcessorContext(context);
    mapRef2RefFlowFunction_67.setEventProcessorContext(context);
    mapRef2RefFlowFunction_68.setEventProcessorContext(context);
    mapRef2RefFlowFunction_72.setEventProcessorContext(context);
    mapRef2RefFlowFunction_73.setEventProcessorContext(context);
    rates.setEventProcessorContext(context);
    mergeFlowFunction_2.dirtyStateMonitor = callbackDispatcher;
    mergeFlowFunction_22.dirtyStateMonitor = callbackDispatcher;
    feePositionMap.setEventProcessorContext(context);
    mtmFeePositionMap.setEventProcessorContext(context);
    mtmPositionMap.setEventProcessorContext(context);
    netPnl.setEventProcessorContext(context);
    pnl.setEventProcessorContext(context);
    positionMap.setEventProcessorContext(context);
    pushFlowFunction_56.setEventProcessorContext(context);
    pushFlowFunction_61.setEventProcessorContext(context);
    pushFlowFunction_65.setEventProcessorContext(context);
    pushFlowFunction_70.setEventProcessorContext(context);
    pushFlowFunction_75.setEventProcessorContext(context);
    pushFlowFunction_77.setEventProcessorContext(context);
    pushFlowFunction_79.setEventProcessorContext(context);
    pushFlowFunction_81.setEventProcessorContext(context);
    tradeFees.setEventProcessorContext(context);
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
    markToMarket_897.setTradePnl(0.0);
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
    callbackImpl_82.init();
    callbackImpl_83.init();
    clock.init();
    doubleSumFlowFunction_227.init();
    doubleSumFlowFunction_327.init();
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
    mapRef2RefFlowFunction_53.initialiseEventStream();
    mapRef2RefFlowFunction_54.initialiseEventStream();
    mapRef2RefFlowFunction_24.initialiseEventStream();
    mapRef2RefFlowFunction_26.initialiseEventStream();
    binaryMapToRefFlowFunction_32.initialiseEventStream();
    mapRef2RefFlowFunction_34.initialiseEventStream();
    binaryMapToRefFlowFunction_36.initialiseEventStream();
    binaryMapToRefFlowFunction_44.initialiseEventStream();
    mapRef2RefFlowFunction_38.initialiseEventStream();
    mapRef2RefFlowFunction_40.initialiseEventStream();
    mapRef2RefFlowFunction_42.initialiseEventStream();
    mapRef2RefFlowFunction_46.initialiseEventStream();
    mapRef2RefFlowFunction_48.initialiseEventStream();
    mapRef2RefFlowFunction_50.initialiseEventStream();
    binaryMapToRefFlowFunction_51.initialiseEventStream();
    mapRef2RefFlowFunction_58.initialiseEventStream();
    mapRef2RefFlowFunction_59.initialiseEventStream();
    feePositionMap.initialiseEventStream();
    mapRef2RefFlowFunction_63.initialiseEventStream();
    mapRef2RefFlowFunction_67.initialiseEventStream();
    mapRef2RefFlowFunction_68.initialiseEventStream();
    mapRef2RefFlowFunction_72.initialiseEventStream();
    mapRef2RefFlowFunction_73.initialiseEventStream();
    mtmFeePositionMap.initialiseEventStream();
    mtmPositionMap.initialiseEventStream();
    netPnl.initialiseEventStream();
    pnl.initialiseEventStream();
    positionMap.initialiseEventStream();
    pushFlowFunction_56.initialiseEventStream();
    pushFlowFunction_61.initialiseEventStream();
    pushFlowFunction_70.initialiseEventStream();
    pushFlowFunction_75.initialiseEventStream();
    pushFlowFunction_79.initialiseEventStream();
    pushFlowFunction_81.initialiseEventStream();
    rates.initialiseEventStream();
    pushFlowFunction_65.initialiseEventStream();
    tradeFees.initialiseEventStream();
    pushFlowFunction_77.initialiseEventStream();
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
        isDirty_callbackImpl_82 = callbackImpl_82.onEvent(typedEvent);
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
            mapRef2RefFlowFunction_53.inputUpdated(mapRef2RefFlowFunction_19);
            binaryMapToRefFlowFunction_36.inputUpdated(mapRef2RefFlowFunction_19);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_31()) {
          isDirty_binaryMapToRefFlowFunction_31 = binaryMapToRefFlowFunction_31.map();
          if (isDirty_binaryMapToRefFlowFunction_31) {
            binaryMapToRefFlowFunction_32.inputUpdated(binaryMapToRefFlowFunction_31);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_20()) {
          isDirty_mapRef2RefFlowFunction_20 = mapRef2RefFlowFunction_20.map();
          if (isDirty_mapRef2RefFlowFunction_20) {
            mergeFlowFunction_22.inputStreamUpdated(mapRef2RefFlowFunction_20);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_53()) {
          isDirty_mapRef2RefFlowFunction_53 = mapRef2RefFlowFunction_53.map();
          if (isDirty_mapRef2RefFlowFunction_53) {
            mapRef2RefFlowFunction_54.inputUpdated(mapRef2RefFlowFunction_53);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_54()) {
          isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
          if (isDirty_mapRef2RefFlowFunction_54) {
            positionMap.inputUpdated(mapRef2RefFlowFunction_54);
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
            binaryMapToRefFlowFunction_32.input2Updated(mapRef2RefFlowFunction_26);
            binaryMapToRefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_26);
            mapRef2RefFlowFunction_58.inputUpdated(mapRef2RefFlowFunction_26);
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
            binaryMapToRefFlowFunction_36.input2Updated(mapRef2RefFlowFunction_34);
            binaryMapToRefFlowFunction_44.input2Updated(mapRef2RefFlowFunction_34);
            mapRef2RefFlowFunction_63.inputUpdated(mapRef2RefFlowFunction_34);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_36()) {
          isDirty_binaryMapToRefFlowFunction_36 = binaryMapToRefFlowFunction_36.map();
          if (isDirty_binaryMapToRefFlowFunction_36) {
            mapRef2RefFlowFunction_38.inputUpdated(binaryMapToRefFlowFunction_36);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_44()) {
          isDirty_binaryMapToRefFlowFunction_44 = binaryMapToRefFlowFunction_44.map();
          if (isDirty_binaryMapToRefFlowFunction_44) {
            mapRef2RefFlowFunction_46.inputUpdated(binaryMapToRefFlowFunction_44);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_38()) {
          isDirty_mapRef2RefFlowFunction_38 = mapRef2RefFlowFunction_38.map();
          if (isDirty_mapRef2RefFlowFunction_38) {
            mapRef2RefFlowFunction_40.inputUpdated(mapRef2RefFlowFunction_38);
            mapRef2RefFlowFunction_67.inputUpdated(mapRef2RefFlowFunction_38);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_40()) {
          isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
          if (isDirty_mapRef2RefFlowFunction_40) {
            mapRef2RefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_40);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_42()) {
          isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
          if (isDirty_mapRef2RefFlowFunction_42) {
            binaryMapToRefFlowFunction_51.inputUpdated(mapRef2RefFlowFunction_42);
            pnl.inputUpdated(mapRef2RefFlowFunction_42);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_46()) {
          isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
          if (isDirty_mapRef2RefFlowFunction_46) {
            mapRef2RefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
            mapRef2RefFlowFunction_72.inputUpdated(mapRef2RefFlowFunction_46);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_48()) {
          isDirty_mapRef2RefFlowFunction_48 = mapRef2RefFlowFunction_48.map();
          if (isDirty_mapRef2RefFlowFunction_48) {
            mapRef2RefFlowFunction_50.inputUpdated(mapRef2RefFlowFunction_48);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_50()) {
          isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
          if (isDirty_mapRef2RefFlowFunction_50) {
            binaryMapToRefFlowFunction_51.input2Updated(mapRef2RefFlowFunction_50);
            tradeFees.inputUpdated(mapRef2RefFlowFunction_50);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_51()) {
          isDirty_binaryMapToRefFlowFunction_51 = binaryMapToRefFlowFunction_51.map();
          if (isDirty_binaryMapToRefFlowFunction_51) {
            netPnl.inputUpdated(binaryMapToRefFlowFunction_51);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_58()) {
          isDirty_mapRef2RefFlowFunction_58 = mapRef2RefFlowFunction_58.map();
          if (isDirty_mapRef2RefFlowFunction_58) {
            mapRef2RefFlowFunction_59.inputUpdated(mapRef2RefFlowFunction_58);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_59()) {
          isDirty_mapRef2RefFlowFunction_59 = mapRef2RefFlowFunction_59.map();
          if (isDirty_mapRef2RefFlowFunction_59) {
            feePositionMap.inputUpdated(mapRef2RefFlowFunction_59);
          }
        }
        if (guardCheck_feePositionMap()) {
          isDirty_feePositionMap = feePositionMap.push();
          if (isDirty_feePositionMap) {
            pushFlowFunction_61.inputUpdated(feePositionMap);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_63()) {
          isDirty_mapRef2RefFlowFunction_63 = mapRef2RefFlowFunction_63.map();
          if (isDirty_mapRef2RefFlowFunction_63) {
            rates.inputUpdated(mapRef2RefFlowFunction_63);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_67()) {
          isDirty_mapRef2RefFlowFunction_67 = mapRef2RefFlowFunction_67.map();
          if (isDirty_mapRef2RefFlowFunction_67) {
            mapRef2RefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_67);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_68()) {
          isDirty_mapRef2RefFlowFunction_68 = mapRef2RefFlowFunction_68.map();
          if (isDirty_mapRef2RefFlowFunction_68) {
            mtmPositionMap.inputUpdated(mapRef2RefFlowFunction_68);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_72()) {
          isDirty_mapRef2RefFlowFunction_72 = mapRef2RefFlowFunction_72.map();
          if (isDirty_mapRef2RefFlowFunction_72) {
            mapRef2RefFlowFunction_73.inputUpdated(mapRef2RefFlowFunction_72);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_73()) {
          isDirty_mapRef2RefFlowFunction_73 = mapRef2RefFlowFunction_73.map();
          if (isDirty_mapRef2RefFlowFunction_73) {
            mtmFeePositionMap.inputUpdated(mapRef2RefFlowFunction_73);
          }
        }
        if (guardCheck_mtmFeePositionMap()) {
          isDirty_mtmFeePositionMap = mtmFeePositionMap.push();
          if (isDirty_mtmFeePositionMap) {
            pushFlowFunction_75.inputUpdated(mtmFeePositionMap);
          }
        }
        if (guardCheck_mtmPositionMap()) {
          isDirty_mtmPositionMap = mtmPositionMap.push();
          if (isDirty_mtmPositionMap) {
            pushFlowFunction_70.inputUpdated(mtmPositionMap);
          }
        }
        if (guardCheck_netPnl()) {
          isDirty_netPnl = netPnl.push();
          if (isDirty_netPnl) {
            pushFlowFunction_81.inputUpdated(netPnl);
          }
        }
        if (guardCheck_pnl()) {
          isDirty_pnl = pnl.push();
          if (isDirty_pnl) {
            pushFlowFunction_79.inputUpdated(pnl);
          }
        }
        if (guardCheck_positionMap()) {
          isDirty_positionMap = positionMap.push();
          if (isDirty_positionMap) {
            pushFlowFunction_56.inputUpdated(positionMap);
          }
        }
        if (guardCheck_pushFlowFunction_56()) {
          isDirty_pushFlowFunction_56 = pushFlowFunction_56.push();
        }
        if (guardCheck_pushFlowFunction_61()) {
          isDirty_pushFlowFunction_61 = pushFlowFunction_61.push();
        }
        if (guardCheck_pushFlowFunction_70()) {
          isDirty_pushFlowFunction_70 = pushFlowFunction_70.push();
        }
        if (guardCheck_pushFlowFunction_75()) {
          isDirty_pushFlowFunction_75 = pushFlowFunction_75.push();
        }
        if (guardCheck_pushFlowFunction_79()) {
          isDirty_pushFlowFunction_79 = pushFlowFunction_79.push();
        }
        if (guardCheck_pushFlowFunction_81()) {
          isDirty_pushFlowFunction_81 = pushFlowFunction_81.push();
        }
        if (guardCheck_rates()) {
          isDirty_rates = rates.map();
          if (isDirty_rates) {
            pushFlowFunction_65.inputUpdated(rates);
          }
        }
        if (guardCheck_pushFlowFunction_65()) {
          isDirty_pushFlowFunction_65 = pushFlowFunction_65.push();
        }
        if (guardCheck_tradeFees()) {
          isDirty_tradeFees = tradeFees.push();
          if (isDirty_tradeFees) {
            pushFlowFunction_77.inputUpdated(tradeFees);
          }
        }
        if (guardCheck_pushFlowFunction_77()) {
          isDirty_pushFlowFunction_77 = pushFlowFunction_77.push();
        }
        afterEvent();
        return;
        //Event Class:[com.fluxtion.runtime.callback.CallbackEvent] filterId:[2]
      case (2):
        isDirty_callbackImpl_83 = callbackImpl_83.onEvent(typedEvent);
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
            mapRef2RefFlowFunction_53.inputUpdated(mapRef2RefFlowFunction_19);
            binaryMapToRefFlowFunction_36.inputUpdated(mapRef2RefFlowFunction_19);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_31()) {
          isDirty_binaryMapToRefFlowFunction_31 = binaryMapToRefFlowFunction_31.map();
          if (isDirty_binaryMapToRefFlowFunction_31) {
            binaryMapToRefFlowFunction_32.inputUpdated(binaryMapToRefFlowFunction_31);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_53()) {
          isDirty_mapRef2RefFlowFunction_53 = mapRef2RefFlowFunction_53.map();
          if (isDirty_mapRef2RefFlowFunction_53) {
            mapRef2RefFlowFunction_54.inputUpdated(mapRef2RefFlowFunction_53);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_54()) {
          isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
          if (isDirty_mapRef2RefFlowFunction_54) {
            positionMap.inputUpdated(mapRef2RefFlowFunction_54);
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
            binaryMapToRefFlowFunction_36.input2Updated(mapRef2RefFlowFunction_34);
            binaryMapToRefFlowFunction_44.input2Updated(mapRef2RefFlowFunction_34);
            mapRef2RefFlowFunction_63.inputUpdated(mapRef2RefFlowFunction_34);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_36()) {
          isDirty_binaryMapToRefFlowFunction_36 = binaryMapToRefFlowFunction_36.map();
          if (isDirty_binaryMapToRefFlowFunction_36) {
            mapRef2RefFlowFunction_38.inputUpdated(binaryMapToRefFlowFunction_36);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_44()) {
          isDirty_binaryMapToRefFlowFunction_44 = binaryMapToRefFlowFunction_44.map();
          if (isDirty_binaryMapToRefFlowFunction_44) {
            mapRef2RefFlowFunction_46.inputUpdated(binaryMapToRefFlowFunction_44);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_38()) {
          isDirty_mapRef2RefFlowFunction_38 = mapRef2RefFlowFunction_38.map();
          if (isDirty_mapRef2RefFlowFunction_38) {
            mapRef2RefFlowFunction_40.inputUpdated(mapRef2RefFlowFunction_38);
            mapRef2RefFlowFunction_67.inputUpdated(mapRef2RefFlowFunction_38);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_40()) {
          isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
          if (isDirty_mapRef2RefFlowFunction_40) {
            mapRef2RefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_40);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_42()) {
          isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
          if (isDirty_mapRef2RefFlowFunction_42) {
            binaryMapToRefFlowFunction_51.inputUpdated(mapRef2RefFlowFunction_42);
            pnl.inputUpdated(mapRef2RefFlowFunction_42);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_46()) {
          isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
          if (isDirty_mapRef2RefFlowFunction_46) {
            mapRef2RefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
            mapRef2RefFlowFunction_72.inputUpdated(mapRef2RefFlowFunction_46);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_48()) {
          isDirty_mapRef2RefFlowFunction_48 = mapRef2RefFlowFunction_48.map();
          if (isDirty_mapRef2RefFlowFunction_48) {
            mapRef2RefFlowFunction_50.inputUpdated(mapRef2RefFlowFunction_48);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_50()) {
          isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
          if (isDirty_mapRef2RefFlowFunction_50) {
            binaryMapToRefFlowFunction_51.input2Updated(mapRef2RefFlowFunction_50);
            tradeFees.inputUpdated(mapRef2RefFlowFunction_50);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_51()) {
          isDirty_binaryMapToRefFlowFunction_51 = binaryMapToRefFlowFunction_51.map();
          if (isDirty_binaryMapToRefFlowFunction_51) {
            netPnl.inputUpdated(binaryMapToRefFlowFunction_51);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_63()) {
          isDirty_mapRef2RefFlowFunction_63 = mapRef2RefFlowFunction_63.map();
          if (isDirty_mapRef2RefFlowFunction_63) {
            rates.inputUpdated(mapRef2RefFlowFunction_63);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_67()) {
          isDirty_mapRef2RefFlowFunction_67 = mapRef2RefFlowFunction_67.map();
          if (isDirty_mapRef2RefFlowFunction_67) {
            mapRef2RefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_67);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_68()) {
          isDirty_mapRef2RefFlowFunction_68 = mapRef2RefFlowFunction_68.map();
          if (isDirty_mapRef2RefFlowFunction_68) {
            mtmPositionMap.inputUpdated(mapRef2RefFlowFunction_68);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_72()) {
          isDirty_mapRef2RefFlowFunction_72 = mapRef2RefFlowFunction_72.map();
          if (isDirty_mapRef2RefFlowFunction_72) {
            mapRef2RefFlowFunction_73.inputUpdated(mapRef2RefFlowFunction_72);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_73()) {
          isDirty_mapRef2RefFlowFunction_73 = mapRef2RefFlowFunction_73.map();
          if (isDirty_mapRef2RefFlowFunction_73) {
            mtmFeePositionMap.inputUpdated(mapRef2RefFlowFunction_73);
          }
        }
        if (guardCheck_mtmFeePositionMap()) {
          isDirty_mtmFeePositionMap = mtmFeePositionMap.push();
          if (isDirty_mtmFeePositionMap) {
            pushFlowFunction_75.inputUpdated(mtmFeePositionMap);
          }
        }
        if (guardCheck_mtmPositionMap()) {
          isDirty_mtmPositionMap = mtmPositionMap.push();
          if (isDirty_mtmPositionMap) {
            pushFlowFunction_70.inputUpdated(mtmPositionMap);
          }
        }
        if (guardCheck_netPnl()) {
          isDirty_netPnl = netPnl.push();
          if (isDirty_netPnl) {
            pushFlowFunction_81.inputUpdated(netPnl);
          }
        }
        if (guardCheck_pnl()) {
          isDirty_pnl = pnl.push();
          if (isDirty_pnl) {
            pushFlowFunction_79.inputUpdated(pnl);
          }
        }
        if (guardCheck_positionMap()) {
          isDirty_positionMap = positionMap.push();
          if (isDirty_positionMap) {
            pushFlowFunction_56.inputUpdated(positionMap);
          }
        }
        if (guardCheck_pushFlowFunction_56()) {
          isDirty_pushFlowFunction_56 = pushFlowFunction_56.push();
        }
        if (guardCheck_pushFlowFunction_70()) {
          isDirty_pushFlowFunction_70 = pushFlowFunction_70.push();
        }
        if (guardCheck_pushFlowFunction_75()) {
          isDirty_pushFlowFunction_75 = pushFlowFunction_75.push();
        }
        if (guardCheck_pushFlowFunction_79()) {
          isDirty_pushFlowFunction_79 = pushFlowFunction_79.push();
        }
        if (guardCheck_pushFlowFunction_81()) {
          isDirty_pushFlowFunction_81 = pushFlowFunction_81.push();
        }
        if (guardCheck_rates()) {
          isDirty_rates = rates.map();
          if (isDirty_rates) {
            pushFlowFunction_65.inputUpdated(rates);
          }
        }
        if (guardCheck_pushFlowFunction_65()) {
          isDirty_pushFlowFunction_65 = pushFlowFunction_65.push();
        }
        if (guardCheck_tradeFees()) {
          isDirty_tradeFees = tradeFees.push();
          if (isDirty_tradeFees) {
            pushFlowFunction_77.inputUpdated(tradeFees);
          }
        }
        if (guardCheck_pushFlowFunction_77()) {
          isDirty_pushFlowFunction_77 = pushFlowFunction_77.push();
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
            mapRef2RefFlowFunction_53.inputUpdated(mapRef2RefFlowFunction_19);
            binaryMapToRefFlowFunction_36.inputUpdated(mapRef2RefFlowFunction_19);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_31()) {
          isDirty_binaryMapToRefFlowFunction_31 = binaryMapToRefFlowFunction_31.map();
          if (isDirty_binaryMapToRefFlowFunction_31) {
            binaryMapToRefFlowFunction_32.inputUpdated(binaryMapToRefFlowFunction_31);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_53()) {
          isDirty_mapRef2RefFlowFunction_53 = mapRef2RefFlowFunction_53.map();
          if (isDirty_mapRef2RefFlowFunction_53) {
            mapRef2RefFlowFunction_54.inputUpdated(mapRef2RefFlowFunction_53);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_54()) {
          isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
          if (isDirty_mapRef2RefFlowFunction_54) {
            positionMap.inputUpdated(mapRef2RefFlowFunction_54);
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
            binaryMapToRefFlowFunction_36.input2Updated(mapRef2RefFlowFunction_34);
            binaryMapToRefFlowFunction_44.input2Updated(mapRef2RefFlowFunction_34);
            mapRef2RefFlowFunction_63.inputUpdated(mapRef2RefFlowFunction_34);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_36()) {
          isDirty_binaryMapToRefFlowFunction_36 = binaryMapToRefFlowFunction_36.map();
          if (isDirty_binaryMapToRefFlowFunction_36) {
            mapRef2RefFlowFunction_38.inputUpdated(binaryMapToRefFlowFunction_36);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_44()) {
          isDirty_binaryMapToRefFlowFunction_44 = binaryMapToRefFlowFunction_44.map();
          if (isDirty_binaryMapToRefFlowFunction_44) {
            mapRef2RefFlowFunction_46.inputUpdated(binaryMapToRefFlowFunction_44);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_38()) {
          isDirty_mapRef2RefFlowFunction_38 = mapRef2RefFlowFunction_38.map();
          if (isDirty_mapRef2RefFlowFunction_38) {
            mapRef2RefFlowFunction_40.inputUpdated(mapRef2RefFlowFunction_38);
            mapRef2RefFlowFunction_67.inputUpdated(mapRef2RefFlowFunction_38);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_40()) {
          isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
          if (isDirty_mapRef2RefFlowFunction_40) {
            mapRef2RefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_40);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_42()) {
          isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
          if (isDirty_mapRef2RefFlowFunction_42) {
            binaryMapToRefFlowFunction_51.inputUpdated(mapRef2RefFlowFunction_42);
            pnl.inputUpdated(mapRef2RefFlowFunction_42);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_46()) {
          isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
          if (isDirty_mapRef2RefFlowFunction_46) {
            mapRef2RefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
            mapRef2RefFlowFunction_72.inputUpdated(mapRef2RefFlowFunction_46);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_48()) {
          isDirty_mapRef2RefFlowFunction_48 = mapRef2RefFlowFunction_48.map();
          if (isDirty_mapRef2RefFlowFunction_48) {
            mapRef2RefFlowFunction_50.inputUpdated(mapRef2RefFlowFunction_48);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_50()) {
          isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
          if (isDirty_mapRef2RefFlowFunction_50) {
            binaryMapToRefFlowFunction_51.input2Updated(mapRef2RefFlowFunction_50);
            tradeFees.inputUpdated(mapRef2RefFlowFunction_50);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_51()) {
          isDirty_binaryMapToRefFlowFunction_51 = binaryMapToRefFlowFunction_51.map();
          if (isDirty_binaryMapToRefFlowFunction_51) {
            netPnl.inputUpdated(binaryMapToRefFlowFunction_51);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_63()) {
          isDirty_mapRef2RefFlowFunction_63 = mapRef2RefFlowFunction_63.map();
          if (isDirty_mapRef2RefFlowFunction_63) {
            rates.inputUpdated(mapRef2RefFlowFunction_63);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_67()) {
          isDirty_mapRef2RefFlowFunction_67 = mapRef2RefFlowFunction_67.map();
          if (isDirty_mapRef2RefFlowFunction_67) {
            mapRef2RefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_67);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_68()) {
          isDirty_mapRef2RefFlowFunction_68 = mapRef2RefFlowFunction_68.map();
          if (isDirty_mapRef2RefFlowFunction_68) {
            mtmPositionMap.inputUpdated(mapRef2RefFlowFunction_68);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_72()) {
          isDirty_mapRef2RefFlowFunction_72 = mapRef2RefFlowFunction_72.map();
          if (isDirty_mapRef2RefFlowFunction_72) {
            mapRef2RefFlowFunction_73.inputUpdated(mapRef2RefFlowFunction_72);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_73()) {
          isDirty_mapRef2RefFlowFunction_73 = mapRef2RefFlowFunction_73.map();
          if (isDirty_mapRef2RefFlowFunction_73) {
            mtmFeePositionMap.inputUpdated(mapRef2RefFlowFunction_73);
          }
        }
        if (guardCheck_mtmFeePositionMap()) {
          isDirty_mtmFeePositionMap = mtmFeePositionMap.push();
          if (isDirty_mtmFeePositionMap) {
            pushFlowFunction_75.inputUpdated(mtmFeePositionMap);
          }
        }
        if (guardCheck_mtmPositionMap()) {
          isDirty_mtmPositionMap = mtmPositionMap.push();
          if (isDirty_mtmPositionMap) {
            pushFlowFunction_70.inputUpdated(mtmPositionMap);
          }
        }
        if (guardCheck_netPnl()) {
          isDirty_netPnl = netPnl.push();
          if (isDirty_netPnl) {
            pushFlowFunction_81.inputUpdated(netPnl);
          }
        }
        if (guardCheck_pnl()) {
          isDirty_pnl = pnl.push();
          if (isDirty_pnl) {
            pushFlowFunction_79.inputUpdated(pnl);
          }
        }
        if (guardCheck_positionMap()) {
          isDirty_positionMap = positionMap.push();
          if (isDirty_positionMap) {
            pushFlowFunction_56.inputUpdated(positionMap);
          }
        }
        if (guardCheck_pushFlowFunction_56()) {
          isDirty_pushFlowFunction_56 = pushFlowFunction_56.push();
        }
        if (guardCheck_pushFlowFunction_70()) {
          isDirty_pushFlowFunction_70 = pushFlowFunction_70.push();
        }
        if (guardCheck_pushFlowFunction_75()) {
          isDirty_pushFlowFunction_75 = pushFlowFunction_75.push();
        }
        if (guardCheck_pushFlowFunction_79()) {
          isDirty_pushFlowFunction_79 = pushFlowFunction_79.push();
        }
        if (guardCheck_pushFlowFunction_81()) {
          isDirty_pushFlowFunction_81 = pushFlowFunction_81.push();
        }
        if (guardCheck_rates()) {
          isDirty_rates = rates.map();
          if (isDirty_rates) {
            pushFlowFunction_65.inputUpdated(rates);
          }
        }
        if (guardCheck_pushFlowFunction_65()) {
          isDirty_pushFlowFunction_65 = pushFlowFunction_65.push();
        }
        if (guardCheck_tradeFees()) {
          isDirty_tradeFees = tradeFees.push();
          if (isDirty_tradeFees) {
            pushFlowFunction_77.inputUpdated(tradeFees);
          }
        }
        if (guardCheck_pushFlowFunction_77()) {
          isDirty_pushFlowFunction_77 = pushFlowFunction_77.push();
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
          mapRef2RefFlowFunction_26.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
          mapRef2RefFlowFunction_26.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
          mapRef2RefFlowFunction_38.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
          mapRef2RefFlowFunction_42.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
          mapRef2RefFlowFunction_46.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
          mapRef2RefFlowFunction_50.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
          binaryMapToRefFlowFunction_51.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
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
            mapRef2RefFlowFunction_53.inputUpdated(mapRef2RefFlowFunction_19);
            binaryMapToRefFlowFunction_36.inputUpdated(mapRef2RefFlowFunction_19);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_31()) {
          isDirty_binaryMapToRefFlowFunction_31 = binaryMapToRefFlowFunction_31.map();
          if (isDirty_binaryMapToRefFlowFunction_31) {
            binaryMapToRefFlowFunction_32.inputUpdated(binaryMapToRefFlowFunction_31);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_53()) {
          isDirty_mapRef2RefFlowFunction_53 = mapRef2RefFlowFunction_53.map();
          if (isDirty_mapRef2RefFlowFunction_53) {
            mapRef2RefFlowFunction_54.inputUpdated(mapRef2RefFlowFunction_53);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_54()) {
          isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
          if (isDirty_mapRef2RefFlowFunction_54) {
            positionMap.inputUpdated(mapRef2RefFlowFunction_54);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_26()) {
          isDirty_mapRef2RefFlowFunction_26 = mapRef2RefFlowFunction_26.map();
          if (isDirty_mapRef2RefFlowFunction_26) {
            binaryMapToRefFlowFunction_32.input2Updated(mapRef2RefFlowFunction_26);
            binaryMapToRefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_26);
            mapRef2RefFlowFunction_58.inputUpdated(mapRef2RefFlowFunction_26);
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
            binaryMapToRefFlowFunction_36.input2Updated(mapRef2RefFlowFunction_34);
            binaryMapToRefFlowFunction_44.input2Updated(mapRef2RefFlowFunction_34);
            mapRef2RefFlowFunction_63.inputUpdated(mapRef2RefFlowFunction_34);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_36()) {
          isDirty_binaryMapToRefFlowFunction_36 = binaryMapToRefFlowFunction_36.map();
          if (isDirty_binaryMapToRefFlowFunction_36) {
            mapRef2RefFlowFunction_38.inputUpdated(binaryMapToRefFlowFunction_36);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_44()) {
          isDirty_binaryMapToRefFlowFunction_44 = binaryMapToRefFlowFunction_44.map();
          if (isDirty_binaryMapToRefFlowFunction_44) {
            mapRef2RefFlowFunction_46.inputUpdated(binaryMapToRefFlowFunction_44);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_38()) {
          isDirty_mapRef2RefFlowFunction_38 = mapRef2RefFlowFunction_38.map();
          if (isDirty_mapRef2RefFlowFunction_38) {
            mapRef2RefFlowFunction_40.inputUpdated(mapRef2RefFlowFunction_38);
            mapRef2RefFlowFunction_67.inputUpdated(mapRef2RefFlowFunction_38);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_40()) {
          isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
          if (isDirty_mapRef2RefFlowFunction_40) {
            mapRef2RefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_40);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_42()) {
          isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
          if (isDirty_mapRef2RefFlowFunction_42) {
            binaryMapToRefFlowFunction_51.inputUpdated(mapRef2RefFlowFunction_42);
            pnl.inputUpdated(mapRef2RefFlowFunction_42);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_46()) {
          isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
          if (isDirty_mapRef2RefFlowFunction_46) {
            mapRef2RefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
            mapRef2RefFlowFunction_72.inputUpdated(mapRef2RefFlowFunction_46);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_48()) {
          isDirty_mapRef2RefFlowFunction_48 = mapRef2RefFlowFunction_48.map();
          if (isDirty_mapRef2RefFlowFunction_48) {
            mapRef2RefFlowFunction_50.inputUpdated(mapRef2RefFlowFunction_48);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_50()) {
          isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
          if (isDirty_mapRef2RefFlowFunction_50) {
            binaryMapToRefFlowFunction_51.input2Updated(mapRef2RefFlowFunction_50);
            tradeFees.inputUpdated(mapRef2RefFlowFunction_50);
          }
        }
        if (guardCheck_binaryMapToRefFlowFunction_51()) {
          isDirty_binaryMapToRefFlowFunction_51 = binaryMapToRefFlowFunction_51.map();
          if (isDirty_binaryMapToRefFlowFunction_51) {
            netPnl.inputUpdated(binaryMapToRefFlowFunction_51);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_58()) {
          isDirty_mapRef2RefFlowFunction_58 = mapRef2RefFlowFunction_58.map();
          if (isDirty_mapRef2RefFlowFunction_58) {
            mapRef2RefFlowFunction_59.inputUpdated(mapRef2RefFlowFunction_58);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_59()) {
          isDirty_mapRef2RefFlowFunction_59 = mapRef2RefFlowFunction_59.map();
          if (isDirty_mapRef2RefFlowFunction_59) {
            feePositionMap.inputUpdated(mapRef2RefFlowFunction_59);
          }
        }
        if (guardCheck_feePositionMap()) {
          isDirty_feePositionMap = feePositionMap.push();
          if (isDirty_feePositionMap) {
            pushFlowFunction_61.inputUpdated(feePositionMap);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_63()) {
          isDirty_mapRef2RefFlowFunction_63 = mapRef2RefFlowFunction_63.map();
          if (isDirty_mapRef2RefFlowFunction_63) {
            rates.inputUpdated(mapRef2RefFlowFunction_63);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_67()) {
          isDirty_mapRef2RefFlowFunction_67 = mapRef2RefFlowFunction_67.map();
          if (isDirty_mapRef2RefFlowFunction_67) {
            mapRef2RefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_67);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_68()) {
          isDirty_mapRef2RefFlowFunction_68 = mapRef2RefFlowFunction_68.map();
          if (isDirty_mapRef2RefFlowFunction_68) {
            mtmPositionMap.inputUpdated(mapRef2RefFlowFunction_68);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_72()) {
          isDirty_mapRef2RefFlowFunction_72 = mapRef2RefFlowFunction_72.map();
          if (isDirty_mapRef2RefFlowFunction_72) {
            mapRef2RefFlowFunction_73.inputUpdated(mapRef2RefFlowFunction_72);
          }
        }
        if (guardCheck_mapRef2RefFlowFunction_73()) {
          isDirty_mapRef2RefFlowFunction_73 = mapRef2RefFlowFunction_73.map();
          if (isDirty_mapRef2RefFlowFunction_73) {
            mtmFeePositionMap.inputUpdated(mapRef2RefFlowFunction_73);
          }
        }
        if (guardCheck_mtmFeePositionMap()) {
          isDirty_mtmFeePositionMap = mtmFeePositionMap.push();
          if (isDirty_mtmFeePositionMap) {
            pushFlowFunction_75.inputUpdated(mtmFeePositionMap);
          }
        }
        if (guardCheck_mtmPositionMap()) {
          isDirty_mtmPositionMap = mtmPositionMap.push();
          if (isDirty_mtmPositionMap) {
            pushFlowFunction_70.inputUpdated(mtmPositionMap);
          }
        }
        if (guardCheck_netPnl()) {
          isDirty_netPnl = netPnl.push();
          if (isDirty_netPnl) {
            pushFlowFunction_81.inputUpdated(netPnl);
          }
        }
        if (guardCheck_pnl()) {
          isDirty_pnl = pnl.push();
          if (isDirty_pnl) {
            pushFlowFunction_79.inputUpdated(pnl);
          }
        }
        if (guardCheck_positionMap()) {
          isDirty_positionMap = positionMap.push();
          if (isDirty_positionMap) {
            pushFlowFunction_56.inputUpdated(positionMap);
          }
        }
        if (guardCheck_pushFlowFunction_56()) {
          isDirty_pushFlowFunction_56 = pushFlowFunction_56.push();
        }
        if (guardCheck_pushFlowFunction_61()) {
          isDirty_pushFlowFunction_61 = pushFlowFunction_61.push();
        }
        if (guardCheck_pushFlowFunction_70()) {
          isDirty_pushFlowFunction_70 = pushFlowFunction_70.push();
        }
        if (guardCheck_pushFlowFunction_75()) {
          isDirty_pushFlowFunction_75 = pushFlowFunction_75.push();
        }
        if (guardCheck_pushFlowFunction_79()) {
          isDirty_pushFlowFunction_79 = pushFlowFunction_79.push();
        }
        if (guardCheck_pushFlowFunction_81()) {
          isDirty_pushFlowFunction_81 = pushFlowFunction_81.push();
        }
        if (guardCheck_rates()) {
          isDirty_rates = rates.map();
          if (isDirty_rates) {
            pushFlowFunction_65.inputUpdated(rates);
          }
        }
        if (guardCheck_pushFlowFunction_65()) {
          isDirty_pushFlowFunction_65 = pushFlowFunction_65.push();
        }
        if (guardCheck_tradeFees()) {
          isDirty_tradeFees = tradeFees.push();
          if (isDirty_tradeFees) {
            pushFlowFunction_77.inputUpdated(tradeFees);
          }
        }
        if (guardCheck_pushFlowFunction_77()) {
          isDirty_pushFlowFunction_77 = pushFlowFunction_77.push();
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
        binaryMapToRefFlowFunction_32.inputUpdated(binaryMapToRefFlowFunction_31);
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
        binaryMapToRefFlowFunction_36.input2Updated(mapRef2RefFlowFunction_34);
        binaryMapToRefFlowFunction_44.input2Updated(mapRef2RefFlowFunction_34);
        mapRef2RefFlowFunction_63.inputUpdated(mapRef2RefFlowFunction_34);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_36()) {
      isDirty_binaryMapToRefFlowFunction_36 = binaryMapToRefFlowFunction_36.map();
      if (isDirty_binaryMapToRefFlowFunction_36) {
        mapRef2RefFlowFunction_38.inputUpdated(binaryMapToRefFlowFunction_36);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_44()) {
      isDirty_binaryMapToRefFlowFunction_44 = binaryMapToRefFlowFunction_44.map();
      if (isDirty_binaryMapToRefFlowFunction_44) {
        mapRef2RefFlowFunction_46.inputUpdated(binaryMapToRefFlowFunction_44);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_38()) {
      isDirty_mapRef2RefFlowFunction_38 = mapRef2RefFlowFunction_38.map();
      if (isDirty_mapRef2RefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(mapRef2RefFlowFunction_38);
        mapRef2RefFlowFunction_67.inputUpdated(mapRef2RefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        mapRef2RefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_42()) {
      isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
      if (isDirty_mapRef2RefFlowFunction_42) {
        binaryMapToRefFlowFunction_51.inputUpdated(mapRef2RefFlowFunction_42);
        pnl.inputUpdated(mapRef2RefFlowFunction_42);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        mapRef2RefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
        mapRef2RefFlowFunction_72.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_48()) {
      isDirty_mapRef2RefFlowFunction_48 = mapRef2RefFlowFunction_48.map();
      if (isDirty_mapRef2RefFlowFunction_48) {
        mapRef2RefFlowFunction_50.inputUpdated(mapRef2RefFlowFunction_48);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_50()) {
      isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
      if (isDirty_mapRef2RefFlowFunction_50) {
        binaryMapToRefFlowFunction_51.input2Updated(mapRef2RefFlowFunction_50);
        tradeFees.inputUpdated(mapRef2RefFlowFunction_50);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_51()) {
      isDirty_binaryMapToRefFlowFunction_51 = binaryMapToRefFlowFunction_51.map();
      if (isDirty_binaryMapToRefFlowFunction_51) {
        netPnl.inputUpdated(binaryMapToRefFlowFunction_51);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_63()) {
      isDirty_mapRef2RefFlowFunction_63 = mapRef2RefFlowFunction_63.map();
      if (isDirty_mapRef2RefFlowFunction_63) {
        rates.inputUpdated(mapRef2RefFlowFunction_63);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_67()) {
      isDirty_mapRef2RefFlowFunction_67 = mapRef2RefFlowFunction_67.map();
      if (isDirty_mapRef2RefFlowFunction_67) {
        mapRef2RefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_67);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_68()) {
      isDirty_mapRef2RefFlowFunction_68 = mapRef2RefFlowFunction_68.map();
      if (isDirty_mapRef2RefFlowFunction_68) {
        mtmPositionMap.inputUpdated(mapRef2RefFlowFunction_68);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_72()) {
      isDirty_mapRef2RefFlowFunction_72 = mapRef2RefFlowFunction_72.map();
      if (isDirty_mapRef2RefFlowFunction_72) {
        mapRef2RefFlowFunction_73.inputUpdated(mapRef2RefFlowFunction_72);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_73()) {
      isDirty_mapRef2RefFlowFunction_73 = mapRef2RefFlowFunction_73.map();
      if (isDirty_mapRef2RefFlowFunction_73) {
        mtmFeePositionMap.inputUpdated(mapRef2RefFlowFunction_73);
      }
    }
    if (guardCheck_mtmFeePositionMap()) {
      isDirty_mtmFeePositionMap = mtmFeePositionMap.push();
      if (isDirty_mtmFeePositionMap) {
        pushFlowFunction_75.inputUpdated(mtmFeePositionMap);
      }
    }
    if (guardCheck_mtmPositionMap()) {
      isDirty_mtmPositionMap = mtmPositionMap.push();
      if (isDirty_mtmPositionMap) {
        pushFlowFunction_70.inputUpdated(mtmPositionMap);
      }
    }
    if (guardCheck_netPnl()) {
      isDirty_netPnl = netPnl.push();
      if (isDirty_netPnl) {
        pushFlowFunction_81.inputUpdated(netPnl);
      }
    }
    if (guardCheck_pnl()) {
      isDirty_pnl = pnl.push();
      if (isDirty_pnl) {
        pushFlowFunction_79.inputUpdated(pnl);
      }
    }
    if (guardCheck_pushFlowFunction_70()) {
      isDirty_pushFlowFunction_70 = pushFlowFunction_70.push();
    }
    if (guardCheck_pushFlowFunction_75()) {
      isDirty_pushFlowFunction_75 = pushFlowFunction_75.push();
    }
    if (guardCheck_pushFlowFunction_79()) {
      isDirty_pushFlowFunction_79 = pushFlowFunction_79.push();
    }
    if (guardCheck_pushFlowFunction_81()) {
      isDirty_pushFlowFunction_81 = pushFlowFunction_81.push();
    }
    if (guardCheck_rates()) {
      isDirty_rates = rates.map();
      if (isDirty_rates) {
        pushFlowFunction_65.inputUpdated(rates);
      }
    }
    if (guardCheck_pushFlowFunction_65()) {
      isDirty_pushFlowFunction_65 = pushFlowFunction_65.push();
    }
    if (guardCheck_tradeFees()) {
      isDirty_tradeFees = tradeFees.push();
      if (isDirty_tradeFees) {
        pushFlowFunction_77.inputUpdated(tradeFees);
      }
    }
    if (guardCheck_pushFlowFunction_77()) {
      isDirty_pushFlowFunction_77 = pushFlowFunction_77.push();
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
        binaryMapToRefFlowFunction_32.inputUpdated(binaryMapToRefFlowFunction_31);
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
        binaryMapToRefFlowFunction_36.input2Updated(mapRef2RefFlowFunction_34);
        binaryMapToRefFlowFunction_44.input2Updated(mapRef2RefFlowFunction_34);
        mapRef2RefFlowFunction_63.inputUpdated(mapRef2RefFlowFunction_34);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_36()) {
      isDirty_binaryMapToRefFlowFunction_36 = binaryMapToRefFlowFunction_36.map();
      if (isDirty_binaryMapToRefFlowFunction_36) {
        mapRef2RefFlowFunction_38.inputUpdated(binaryMapToRefFlowFunction_36);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_44()) {
      isDirty_binaryMapToRefFlowFunction_44 = binaryMapToRefFlowFunction_44.map();
      if (isDirty_binaryMapToRefFlowFunction_44) {
        mapRef2RefFlowFunction_46.inputUpdated(binaryMapToRefFlowFunction_44);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_38()) {
      isDirty_mapRef2RefFlowFunction_38 = mapRef2RefFlowFunction_38.map();
      if (isDirty_mapRef2RefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(mapRef2RefFlowFunction_38);
        mapRef2RefFlowFunction_67.inputUpdated(mapRef2RefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        mapRef2RefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_42()) {
      isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
      if (isDirty_mapRef2RefFlowFunction_42) {
        binaryMapToRefFlowFunction_51.inputUpdated(mapRef2RefFlowFunction_42);
        pnl.inputUpdated(mapRef2RefFlowFunction_42);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        mapRef2RefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
        mapRef2RefFlowFunction_72.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_48()) {
      isDirty_mapRef2RefFlowFunction_48 = mapRef2RefFlowFunction_48.map();
      if (isDirty_mapRef2RefFlowFunction_48) {
        mapRef2RefFlowFunction_50.inputUpdated(mapRef2RefFlowFunction_48);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_50()) {
      isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
      if (isDirty_mapRef2RefFlowFunction_50) {
        binaryMapToRefFlowFunction_51.input2Updated(mapRef2RefFlowFunction_50);
        tradeFees.inputUpdated(mapRef2RefFlowFunction_50);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_51()) {
      isDirty_binaryMapToRefFlowFunction_51 = binaryMapToRefFlowFunction_51.map();
      if (isDirty_binaryMapToRefFlowFunction_51) {
        netPnl.inputUpdated(binaryMapToRefFlowFunction_51);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_63()) {
      isDirty_mapRef2RefFlowFunction_63 = mapRef2RefFlowFunction_63.map();
      if (isDirty_mapRef2RefFlowFunction_63) {
        rates.inputUpdated(mapRef2RefFlowFunction_63);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_67()) {
      isDirty_mapRef2RefFlowFunction_67 = mapRef2RefFlowFunction_67.map();
      if (isDirty_mapRef2RefFlowFunction_67) {
        mapRef2RefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_67);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_68()) {
      isDirty_mapRef2RefFlowFunction_68 = mapRef2RefFlowFunction_68.map();
      if (isDirty_mapRef2RefFlowFunction_68) {
        mtmPositionMap.inputUpdated(mapRef2RefFlowFunction_68);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_72()) {
      isDirty_mapRef2RefFlowFunction_72 = mapRef2RefFlowFunction_72.map();
      if (isDirty_mapRef2RefFlowFunction_72) {
        mapRef2RefFlowFunction_73.inputUpdated(mapRef2RefFlowFunction_72);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_73()) {
      isDirty_mapRef2RefFlowFunction_73 = mapRef2RefFlowFunction_73.map();
      if (isDirty_mapRef2RefFlowFunction_73) {
        mtmFeePositionMap.inputUpdated(mapRef2RefFlowFunction_73);
      }
    }
    if (guardCheck_mtmFeePositionMap()) {
      isDirty_mtmFeePositionMap = mtmFeePositionMap.push();
      if (isDirty_mtmFeePositionMap) {
        pushFlowFunction_75.inputUpdated(mtmFeePositionMap);
      }
    }
    if (guardCheck_mtmPositionMap()) {
      isDirty_mtmPositionMap = mtmPositionMap.push();
      if (isDirty_mtmPositionMap) {
        pushFlowFunction_70.inputUpdated(mtmPositionMap);
      }
    }
    if (guardCheck_netPnl()) {
      isDirty_netPnl = netPnl.push();
      if (isDirty_netPnl) {
        pushFlowFunction_81.inputUpdated(netPnl);
      }
    }
    if (guardCheck_pnl()) {
      isDirty_pnl = pnl.push();
      if (isDirty_pnl) {
        pushFlowFunction_79.inputUpdated(pnl);
      }
    }
    if (guardCheck_pushFlowFunction_70()) {
      isDirty_pushFlowFunction_70 = pushFlowFunction_70.push();
    }
    if (guardCheck_pushFlowFunction_75()) {
      isDirty_pushFlowFunction_75 = pushFlowFunction_75.push();
    }
    if (guardCheck_pushFlowFunction_79()) {
      isDirty_pushFlowFunction_79 = pushFlowFunction_79.push();
    }
    if (guardCheck_pushFlowFunction_81()) {
      isDirty_pushFlowFunction_81 = pushFlowFunction_81.push();
    }
    if (guardCheck_rates()) {
      isDirty_rates = rates.map();
      if (isDirty_rates) {
        pushFlowFunction_65.inputUpdated(rates);
      }
    }
    if (guardCheck_pushFlowFunction_65()) {
      isDirty_pushFlowFunction_65 = pushFlowFunction_65.push();
    }
    if (guardCheck_tradeFees()) {
      isDirty_tradeFees = tradeFees.push();
      if (isDirty_tradeFees) {
        pushFlowFunction_77.inputUpdated(tradeFees);
      }
    }
    if (guardCheck_pushFlowFunction_77()) {
      isDirty_pushFlowFunction_77 = pushFlowFunction_77.push();
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
        mapRef2RefFlowFunction_53.inputUpdated(mapRef2RefFlowFunction_19);
        binaryMapToRefFlowFunction_36.inputUpdated(mapRef2RefFlowFunction_19);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_31()) {
      isDirty_binaryMapToRefFlowFunction_31 = binaryMapToRefFlowFunction_31.map();
      if (isDirty_binaryMapToRefFlowFunction_31) {
        binaryMapToRefFlowFunction_32.inputUpdated(binaryMapToRefFlowFunction_31);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_20()) {
      isDirty_mapRef2RefFlowFunction_20 = mapRef2RefFlowFunction_20.map();
      if (isDirty_mapRef2RefFlowFunction_20) {
        mergeFlowFunction_22.inputStreamUpdated(mapRef2RefFlowFunction_20);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_53()) {
      isDirty_mapRef2RefFlowFunction_53 = mapRef2RefFlowFunction_53.map();
      if (isDirty_mapRef2RefFlowFunction_53) {
        mapRef2RefFlowFunction_54.inputUpdated(mapRef2RefFlowFunction_53);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        positionMap.inputUpdated(mapRef2RefFlowFunction_54);
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
        binaryMapToRefFlowFunction_32.input2Updated(mapRef2RefFlowFunction_26);
        binaryMapToRefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_26);
        mapRef2RefFlowFunction_58.inputUpdated(mapRef2RefFlowFunction_26);
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
        binaryMapToRefFlowFunction_36.input2Updated(mapRef2RefFlowFunction_34);
        binaryMapToRefFlowFunction_44.input2Updated(mapRef2RefFlowFunction_34);
        mapRef2RefFlowFunction_63.inputUpdated(mapRef2RefFlowFunction_34);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_36()) {
      isDirty_binaryMapToRefFlowFunction_36 = binaryMapToRefFlowFunction_36.map();
      if (isDirty_binaryMapToRefFlowFunction_36) {
        mapRef2RefFlowFunction_38.inputUpdated(binaryMapToRefFlowFunction_36);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_44()) {
      isDirty_binaryMapToRefFlowFunction_44 = binaryMapToRefFlowFunction_44.map();
      if (isDirty_binaryMapToRefFlowFunction_44) {
        mapRef2RefFlowFunction_46.inputUpdated(binaryMapToRefFlowFunction_44);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_38()) {
      isDirty_mapRef2RefFlowFunction_38 = mapRef2RefFlowFunction_38.map();
      if (isDirty_mapRef2RefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(mapRef2RefFlowFunction_38);
        mapRef2RefFlowFunction_67.inputUpdated(mapRef2RefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        mapRef2RefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_42()) {
      isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
      if (isDirty_mapRef2RefFlowFunction_42) {
        binaryMapToRefFlowFunction_51.inputUpdated(mapRef2RefFlowFunction_42);
        pnl.inputUpdated(mapRef2RefFlowFunction_42);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        mapRef2RefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
        mapRef2RefFlowFunction_72.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_48()) {
      isDirty_mapRef2RefFlowFunction_48 = mapRef2RefFlowFunction_48.map();
      if (isDirty_mapRef2RefFlowFunction_48) {
        mapRef2RefFlowFunction_50.inputUpdated(mapRef2RefFlowFunction_48);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_50()) {
      isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
      if (isDirty_mapRef2RefFlowFunction_50) {
        binaryMapToRefFlowFunction_51.input2Updated(mapRef2RefFlowFunction_50);
        tradeFees.inputUpdated(mapRef2RefFlowFunction_50);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_51()) {
      isDirty_binaryMapToRefFlowFunction_51 = binaryMapToRefFlowFunction_51.map();
      if (isDirty_binaryMapToRefFlowFunction_51) {
        netPnl.inputUpdated(binaryMapToRefFlowFunction_51);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_58()) {
      isDirty_mapRef2RefFlowFunction_58 = mapRef2RefFlowFunction_58.map();
      if (isDirty_mapRef2RefFlowFunction_58) {
        mapRef2RefFlowFunction_59.inputUpdated(mapRef2RefFlowFunction_58);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_59()) {
      isDirty_mapRef2RefFlowFunction_59 = mapRef2RefFlowFunction_59.map();
      if (isDirty_mapRef2RefFlowFunction_59) {
        feePositionMap.inputUpdated(mapRef2RefFlowFunction_59);
      }
    }
    if (guardCheck_feePositionMap()) {
      isDirty_feePositionMap = feePositionMap.push();
      if (isDirty_feePositionMap) {
        pushFlowFunction_61.inputUpdated(feePositionMap);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_63()) {
      isDirty_mapRef2RefFlowFunction_63 = mapRef2RefFlowFunction_63.map();
      if (isDirty_mapRef2RefFlowFunction_63) {
        rates.inputUpdated(mapRef2RefFlowFunction_63);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_67()) {
      isDirty_mapRef2RefFlowFunction_67 = mapRef2RefFlowFunction_67.map();
      if (isDirty_mapRef2RefFlowFunction_67) {
        mapRef2RefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_67);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_68()) {
      isDirty_mapRef2RefFlowFunction_68 = mapRef2RefFlowFunction_68.map();
      if (isDirty_mapRef2RefFlowFunction_68) {
        mtmPositionMap.inputUpdated(mapRef2RefFlowFunction_68);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_72()) {
      isDirty_mapRef2RefFlowFunction_72 = mapRef2RefFlowFunction_72.map();
      if (isDirty_mapRef2RefFlowFunction_72) {
        mapRef2RefFlowFunction_73.inputUpdated(mapRef2RefFlowFunction_72);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_73()) {
      isDirty_mapRef2RefFlowFunction_73 = mapRef2RefFlowFunction_73.map();
      if (isDirty_mapRef2RefFlowFunction_73) {
        mtmFeePositionMap.inputUpdated(mapRef2RefFlowFunction_73);
      }
    }
    if (guardCheck_mtmFeePositionMap()) {
      isDirty_mtmFeePositionMap = mtmFeePositionMap.push();
      if (isDirty_mtmFeePositionMap) {
        pushFlowFunction_75.inputUpdated(mtmFeePositionMap);
      }
    }
    if (guardCheck_mtmPositionMap()) {
      isDirty_mtmPositionMap = mtmPositionMap.push();
      if (isDirty_mtmPositionMap) {
        pushFlowFunction_70.inputUpdated(mtmPositionMap);
      }
    }
    if (guardCheck_netPnl()) {
      isDirty_netPnl = netPnl.push();
      if (isDirty_netPnl) {
        pushFlowFunction_81.inputUpdated(netPnl);
      }
    }
    if (guardCheck_pnl()) {
      isDirty_pnl = pnl.push();
      if (isDirty_pnl) {
        pushFlowFunction_79.inputUpdated(pnl);
      }
    }
    if (guardCheck_positionMap()) {
      isDirty_positionMap = positionMap.push();
      if (isDirty_positionMap) {
        pushFlowFunction_56.inputUpdated(positionMap);
      }
    }
    if (guardCheck_pushFlowFunction_56()) {
      isDirty_pushFlowFunction_56 = pushFlowFunction_56.push();
    }
    if (guardCheck_pushFlowFunction_61()) {
      isDirty_pushFlowFunction_61 = pushFlowFunction_61.push();
    }
    if (guardCheck_pushFlowFunction_70()) {
      isDirty_pushFlowFunction_70 = pushFlowFunction_70.push();
    }
    if (guardCheck_pushFlowFunction_75()) {
      isDirty_pushFlowFunction_75 = pushFlowFunction_75.push();
    }
    if (guardCheck_pushFlowFunction_79()) {
      isDirty_pushFlowFunction_79 = pushFlowFunction_79.push();
    }
    if (guardCheck_pushFlowFunction_81()) {
      isDirty_pushFlowFunction_81 = pushFlowFunction_81.push();
    }
    if (guardCheck_rates()) {
      isDirty_rates = rates.map();
      if (isDirty_rates) {
        pushFlowFunction_65.inputUpdated(rates);
      }
    }
    if (guardCheck_pushFlowFunction_65()) {
      isDirty_pushFlowFunction_65 = pushFlowFunction_65.push();
    }
    if (guardCheck_tradeFees()) {
      isDirty_tradeFees = tradeFees.push();
      if (isDirty_tradeFees) {
        pushFlowFunction_77.inputUpdated(tradeFees);
      }
    }
    if (guardCheck_pushFlowFunction_77()) {
      isDirty_pushFlowFunction_77 = pushFlowFunction_77.push();
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
        binaryMapToRefFlowFunction_32.input2Updated(mapRef2RefFlowFunction_26);
        binaryMapToRefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_26);
        mapRef2RefFlowFunction_58.inputUpdated(mapRef2RefFlowFunction_26);
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
        binaryMapToRefFlowFunction_36.input2Updated(mapRef2RefFlowFunction_34);
        binaryMapToRefFlowFunction_44.input2Updated(mapRef2RefFlowFunction_34);
        mapRef2RefFlowFunction_63.inputUpdated(mapRef2RefFlowFunction_34);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_36()) {
      isDirty_binaryMapToRefFlowFunction_36 = binaryMapToRefFlowFunction_36.map();
      if (isDirty_binaryMapToRefFlowFunction_36) {
        mapRef2RefFlowFunction_38.inputUpdated(binaryMapToRefFlowFunction_36);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_44()) {
      isDirty_binaryMapToRefFlowFunction_44 = binaryMapToRefFlowFunction_44.map();
      if (isDirty_binaryMapToRefFlowFunction_44) {
        mapRef2RefFlowFunction_46.inputUpdated(binaryMapToRefFlowFunction_44);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_38()) {
      isDirty_mapRef2RefFlowFunction_38 = mapRef2RefFlowFunction_38.map();
      if (isDirty_mapRef2RefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(mapRef2RefFlowFunction_38);
        mapRef2RefFlowFunction_67.inputUpdated(mapRef2RefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        mapRef2RefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_42()) {
      isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
      if (isDirty_mapRef2RefFlowFunction_42) {
        binaryMapToRefFlowFunction_51.inputUpdated(mapRef2RefFlowFunction_42);
        pnl.inputUpdated(mapRef2RefFlowFunction_42);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        mapRef2RefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
        mapRef2RefFlowFunction_72.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_48()) {
      isDirty_mapRef2RefFlowFunction_48 = mapRef2RefFlowFunction_48.map();
      if (isDirty_mapRef2RefFlowFunction_48) {
        mapRef2RefFlowFunction_50.inputUpdated(mapRef2RefFlowFunction_48);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_50()) {
      isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
      if (isDirty_mapRef2RefFlowFunction_50) {
        binaryMapToRefFlowFunction_51.input2Updated(mapRef2RefFlowFunction_50);
        tradeFees.inputUpdated(mapRef2RefFlowFunction_50);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_51()) {
      isDirty_binaryMapToRefFlowFunction_51 = binaryMapToRefFlowFunction_51.map();
      if (isDirty_binaryMapToRefFlowFunction_51) {
        netPnl.inputUpdated(binaryMapToRefFlowFunction_51);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_58()) {
      isDirty_mapRef2RefFlowFunction_58 = mapRef2RefFlowFunction_58.map();
      if (isDirty_mapRef2RefFlowFunction_58) {
        mapRef2RefFlowFunction_59.inputUpdated(mapRef2RefFlowFunction_58);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_59()) {
      isDirty_mapRef2RefFlowFunction_59 = mapRef2RefFlowFunction_59.map();
      if (isDirty_mapRef2RefFlowFunction_59) {
        feePositionMap.inputUpdated(mapRef2RefFlowFunction_59);
      }
    }
    if (guardCheck_feePositionMap()) {
      isDirty_feePositionMap = feePositionMap.push();
      if (isDirty_feePositionMap) {
        pushFlowFunction_61.inputUpdated(feePositionMap);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_63()) {
      isDirty_mapRef2RefFlowFunction_63 = mapRef2RefFlowFunction_63.map();
      if (isDirty_mapRef2RefFlowFunction_63) {
        rates.inputUpdated(mapRef2RefFlowFunction_63);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_67()) {
      isDirty_mapRef2RefFlowFunction_67 = mapRef2RefFlowFunction_67.map();
      if (isDirty_mapRef2RefFlowFunction_67) {
        mapRef2RefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_67);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_68()) {
      isDirty_mapRef2RefFlowFunction_68 = mapRef2RefFlowFunction_68.map();
      if (isDirty_mapRef2RefFlowFunction_68) {
        mtmPositionMap.inputUpdated(mapRef2RefFlowFunction_68);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_72()) {
      isDirty_mapRef2RefFlowFunction_72 = mapRef2RefFlowFunction_72.map();
      if (isDirty_mapRef2RefFlowFunction_72) {
        mapRef2RefFlowFunction_73.inputUpdated(mapRef2RefFlowFunction_72);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_73()) {
      isDirty_mapRef2RefFlowFunction_73 = mapRef2RefFlowFunction_73.map();
      if (isDirty_mapRef2RefFlowFunction_73) {
        mtmFeePositionMap.inputUpdated(mapRef2RefFlowFunction_73);
      }
    }
    if (guardCheck_mtmFeePositionMap()) {
      isDirty_mtmFeePositionMap = mtmFeePositionMap.push();
      if (isDirty_mtmFeePositionMap) {
        pushFlowFunction_75.inputUpdated(mtmFeePositionMap);
      }
    }
    if (guardCheck_mtmPositionMap()) {
      isDirty_mtmPositionMap = mtmPositionMap.push();
      if (isDirty_mtmPositionMap) {
        pushFlowFunction_70.inputUpdated(mtmPositionMap);
      }
    }
    if (guardCheck_netPnl()) {
      isDirty_netPnl = netPnl.push();
      if (isDirty_netPnl) {
        pushFlowFunction_81.inputUpdated(netPnl);
      }
    }
    if (guardCheck_pnl()) {
      isDirty_pnl = pnl.push();
      if (isDirty_pnl) {
        pushFlowFunction_79.inputUpdated(pnl);
      }
    }
    if (guardCheck_pushFlowFunction_61()) {
      isDirty_pushFlowFunction_61 = pushFlowFunction_61.push();
    }
    if (guardCheck_pushFlowFunction_70()) {
      isDirty_pushFlowFunction_70 = pushFlowFunction_70.push();
    }
    if (guardCheck_pushFlowFunction_75()) {
      isDirty_pushFlowFunction_75 = pushFlowFunction_75.push();
    }
    if (guardCheck_pushFlowFunction_79()) {
      isDirty_pushFlowFunction_79 = pushFlowFunction_79.push();
    }
    if (guardCheck_pushFlowFunction_81()) {
      isDirty_pushFlowFunction_81 = pushFlowFunction_81.push();
    }
    if (guardCheck_rates()) {
      isDirty_rates = rates.map();
      if (isDirty_rates) {
        pushFlowFunction_65.inputUpdated(rates);
      }
    }
    if (guardCheck_pushFlowFunction_65()) {
      isDirty_pushFlowFunction_65 = pushFlowFunction_65.push();
    }
    if (guardCheck_tradeFees()) {
      isDirty_tradeFees = tradeFees.push();
      if (isDirty_tradeFees) {
        pushFlowFunction_77.inputUpdated(tradeFees);
      }
    }
    if (guardCheck_pushFlowFunction_77()) {
      isDirty_pushFlowFunction_77 = pushFlowFunction_77.push();
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
    auditor.nodeRegistered(callbackImpl_82, "callbackImpl_82");
    auditor.nodeRegistered(callbackImpl_83, "callbackImpl_83");
    auditor.nodeRegistered(doubleSumFlowFunction_227, "doubleSumFlowFunction_227");
    auditor.nodeRegistered(doubleSumFlowFunction_327, "doubleSumFlowFunction_327");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_11, "binaryMapToRefFlowFunction_11");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_15, "binaryMapToRefFlowFunction_15");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_31, "binaryMapToRefFlowFunction_31");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_32, "binaryMapToRefFlowFunction_32");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_36, "binaryMapToRefFlowFunction_36");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_44, "binaryMapToRefFlowFunction_44");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_51, "binaryMapToRefFlowFunction_51");
    auditor.nodeRegistered(filterFlowFunction_28, "filterFlowFunction_28");
    auditor.nodeRegistered(flatMapFlowFunction_1, "flatMapFlowFunction_1");
    auditor.nodeRegistered(flatMapFlowFunction_3, "flatMapFlowFunction_3");
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
    auditor.nodeRegistered(mapRef2RefFlowFunction_34, "mapRef2RefFlowFunction_34");
    auditor.nodeRegistered(mapRef2RefFlowFunction_38, "mapRef2RefFlowFunction_38");
    auditor.nodeRegistered(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40");
    auditor.nodeRegistered(mapRef2RefFlowFunction_42, "mapRef2RefFlowFunction_42");
    auditor.nodeRegistered(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46");
    auditor.nodeRegistered(mapRef2RefFlowFunction_48, "mapRef2RefFlowFunction_48");
    auditor.nodeRegistered(mapRef2RefFlowFunction_50, "mapRef2RefFlowFunction_50");
    auditor.nodeRegistered(mapRef2RefFlowFunction_53, "mapRef2RefFlowFunction_53");
    auditor.nodeRegistered(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54");
    auditor.nodeRegistered(mapRef2RefFlowFunction_58, "mapRef2RefFlowFunction_58");
    auditor.nodeRegistered(mapRef2RefFlowFunction_59, "mapRef2RefFlowFunction_59");
    auditor.nodeRegistered(mapRef2RefFlowFunction_63, "mapRef2RefFlowFunction_63");
    auditor.nodeRegistered(mapRef2RefFlowFunction_67, "mapRef2RefFlowFunction_67");
    auditor.nodeRegistered(mapRef2RefFlowFunction_68, "mapRef2RefFlowFunction_68");
    auditor.nodeRegistered(mapRef2RefFlowFunction_72, "mapRef2RefFlowFunction_72");
    auditor.nodeRegistered(mapRef2RefFlowFunction_73, "mapRef2RefFlowFunction_73");
    auditor.nodeRegistered(rates, "rates");
    auditor.nodeRegistered(mergeFlowFunction_2, "mergeFlowFunction_2");
    auditor.nodeRegistered(mergeFlowFunction_22, "mergeFlowFunction_22");
    auditor.nodeRegistered(feePositionMap, "feePositionMap");
    auditor.nodeRegistered(mtmFeePositionMap, "mtmFeePositionMap");
    auditor.nodeRegistered(mtmPositionMap, "mtmPositionMap");
    auditor.nodeRegistered(netPnl, "netPnl");
    auditor.nodeRegistered(pnl, "pnl");
    auditor.nodeRegistered(positionMap, "positionMap");
    auditor.nodeRegistered(pushFlowFunction_56, "pushFlowFunction_56");
    auditor.nodeRegistered(pushFlowFunction_61, "pushFlowFunction_61");
    auditor.nodeRegistered(pushFlowFunction_65, "pushFlowFunction_65");
    auditor.nodeRegistered(pushFlowFunction_70, "pushFlowFunction_70");
    auditor.nodeRegistered(pushFlowFunction_75, "pushFlowFunction_75");
    auditor.nodeRegistered(pushFlowFunction_77, "pushFlowFunction_77");
    auditor.nodeRegistered(pushFlowFunction_79, "pushFlowFunction_79");
    auditor.nodeRegistered(pushFlowFunction_81, "pushFlowFunction_81");
    auditor.nodeRegistered(tradeFees, "tradeFees");
    auditor.nodeRegistered(emptyGroupBy_107, "emptyGroupBy_107");
    auditor.nodeRegistered(emptyGroupBy_117, "emptyGroupBy_117");
    auditor.nodeRegistered(emptyGroupBy_156, "emptyGroupBy_156");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_4, "groupByFlowFunctionWrapper_4");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_6, "groupByFlowFunctionWrapper_6");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_8, "groupByFlowFunctionWrapper_8");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_23, "groupByFlowFunctionWrapper_23");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_29, "groupByFlowFunctionWrapper_29");
    auditor.nodeRegistered(groupByMapFlowFunction_12, "groupByMapFlowFunction_12");
    auditor.nodeRegistered(groupByMapFlowFunction_16, "groupByMapFlowFunction_16");
    auditor.nodeRegistered(groupByMapFlowFunction_37, "groupByMapFlowFunction_37");
    auditor.nodeRegistered(groupByMapFlowFunction_45, "groupByMapFlowFunction_45");
    auditor.nodeRegistered(groupByMapFlowFunction_52, "groupByMapFlowFunction_52");
    auditor.nodeRegistered(groupByMapFlowFunction_57, "groupByMapFlowFunction_57");
    auditor.nodeRegistered(groupByMapFlowFunction_62, "groupByMapFlowFunction_62");
    auditor.nodeRegistered(groupByMapFlowFunction_66, "groupByMapFlowFunction_66");
    auditor.nodeRegistered(groupByMapFlowFunction_71, "groupByMapFlowFunction_71");
    auditor.nodeRegistered(groupByReduceFlowFunction_39, "groupByReduceFlowFunction_39");
    auditor.nodeRegistered(groupByReduceFlowFunction_47, "groupByReduceFlowFunction_47");
    auditor.nodeRegistered(leftJoin_35, "leftJoin_35");
    auditor.nodeRegistered(leftJoin_43, "leftJoin_43");
    auditor.nodeRegistered(outerJoin_10, "outerJoin_10");
    auditor.nodeRegistered(outerJoin_14, "outerJoin_14");
    auditor.nodeRegistered(defaultValue_18, "defaultValue_18");
    auditor.nodeRegistered(defaultValue_25, "defaultValue_25");
    auditor.nodeRegistered(defaultValue_33, "defaultValue_33");
    auditor.nodeRegistered(defaultValue_41, "defaultValue_41");
    auditor.nodeRegistered(defaultValue_49, "defaultValue_49");
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
    auditor.nodeRegistered(markToMarket_897, "markToMarket_897");
    auditor.nodeRegistered(derivedRateNode_27, "derivedRateNode_27");
    auditor.nodeRegistered(symbolLookupNode_0, "symbolLookupNode_0");
    auditor.nodeRegistered(double_256, "double_256");
    auditor.nodeRegistered(double_350, "double_350");
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
    isDirty_binaryMapToRefFlowFunction_32 = false;
    isDirty_binaryMapToRefFlowFunction_36 = false;
    isDirty_binaryMapToRefFlowFunction_44 = false;
    isDirty_binaryMapToRefFlowFunction_51 = false;
    isDirty_callbackImpl_82 = false;
    isDirty_callbackImpl_83 = false;
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
    isDirty_mapRef2RefFlowFunction_34 = false;
    isDirty_mapRef2RefFlowFunction_38 = false;
    isDirty_mapRef2RefFlowFunction_40 = false;
    isDirty_mapRef2RefFlowFunction_42 = false;
    isDirty_mapRef2RefFlowFunction_46 = false;
    isDirty_mapRef2RefFlowFunction_48 = false;
    isDirty_mapRef2RefFlowFunction_50 = false;
    isDirty_mapRef2RefFlowFunction_53 = false;
    isDirty_mapRef2RefFlowFunction_54 = false;
    isDirty_mapRef2RefFlowFunction_58 = false;
    isDirty_mapRef2RefFlowFunction_59 = false;
    isDirty_mapRef2RefFlowFunction_63 = false;
    isDirty_mapRef2RefFlowFunction_67 = false;
    isDirty_mapRef2RefFlowFunction_68 = false;
    isDirty_mapRef2RefFlowFunction_72 = false;
    isDirty_mapRef2RefFlowFunction_73 = false;
    isDirty_mergeFlowFunction_2 = false;
    isDirty_mergeFlowFunction_22 = false;
    isDirty_mtmFeePositionMap = false;
    isDirty_mtmPositionMap = false;
    isDirty_netPnl = false;
    isDirty_pnl = false;
    isDirty_positionMap = false;
    isDirty_pushFlowFunction_56 = false;
    isDirty_pushFlowFunction_61 = false;
    isDirty_pushFlowFunction_65 = false;
    isDirty_pushFlowFunction_70 = false;
    isDirty_pushFlowFunction_75 = false;
    isDirty_pushFlowFunction_77 = false;
    isDirty_pushFlowFunction_79 = false;
    isDirty_pushFlowFunction_81 = false;
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
          binaryMapToRefFlowFunction_32, () -> isDirty_binaryMapToRefFlowFunction_32);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_36, () -> isDirty_binaryMapToRefFlowFunction_36);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_44, () -> isDirty_binaryMapToRefFlowFunction_44);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_51, () -> isDirty_binaryMapToRefFlowFunction_51);
      dirtyFlagSupplierMap.put(callbackImpl_82, () -> isDirty_callbackImpl_82);
      dirtyFlagSupplierMap.put(callbackImpl_83, () -> isDirty_callbackImpl_83);
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
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_34, () -> isDirty_mapRef2RefFlowFunction_34);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_38, () -> isDirty_mapRef2RefFlowFunction_38);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_40, () -> isDirty_mapRef2RefFlowFunction_40);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_42, () -> isDirty_mapRef2RefFlowFunction_42);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_46, () -> isDirty_mapRef2RefFlowFunction_46);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_48, () -> isDirty_mapRef2RefFlowFunction_48);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_5, () -> isDirty_mapRef2RefFlowFunction_5);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_50, () -> isDirty_mapRef2RefFlowFunction_50);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_53, () -> isDirty_mapRef2RefFlowFunction_53);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_54, () -> isDirty_mapRef2RefFlowFunction_54);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_58, () -> isDirty_mapRef2RefFlowFunction_58);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_59, () -> isDirty_mapRef2RefFlowFunction_59);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_63, () -> isDirty_mapRef2RefFlowFunction_63);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_67, () -> isDirty_mapRef2RefFlowFunction_67);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_68, () -> isDirty_mapRef2RefFlowFunction_68);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_7, () -> isDirty_mapRef2RefFlowFunction_7);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_72, () -> isDirty_mapRef2RefFlowFunction_72);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_73, () -> isDirty_mapRef2RefFlowFunction_73);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_9, () -> isDirty_mapRef2RefFlowFunction_9);
      dirtyFlagSupplierMap.put(mergeFlowFunction_2, () -> isDirty_mergeFlowFunction_2);
      dirtyFlagSupplierMap.put(mergeFlowFunction_22, () -> isDirty_mergeFlowFunction_22);
      dirtyFlagSupplierMap.put(mtmFeePositionMap, () -> isDirty_mtmFeePositionMap);
      dirtyFlagSupplierMap.put(mtmPositionMap, () -> isDirty_mtmPositionMap);
      dirtyFlagSupplierMap.put(netPnl, () -> isDirty_netPnl);
      dirtyFlagSupplierMap.put(pnl, () -> isDirty_pnl);
      dirtyFlagSupplierMap.put(positionMap, () -> isDirty_positionMap);
      dirtyFlagSupplierMap.put(pushFlowFunction_56, () -> isDirty_pushFlowFunction_56);
      dirtyFlagSupplierMap.put(pushFlowFunction_61, () -> isDirty_pushFlowFunction_61);
      dirtyFlagSupplierMap.put(pushFlowFunction_65, () -> isDirty_pushFlowFunction_65);
      dirtyFlagSupplierMap.put(pushFlowFunction_70, () -> isDirty_pushFlowFunction_70);
      dirtyFlagSupplierMap.put(pushFlowFunction_75, () -> isDirty_pushFlowFunction_75);
      dirtyFlagSupplierMap.put(pushFlowFunction_77, () -> isDirty_pushFlowFunction_77);
      dirtyFlagSupplierMap.put(pushFlowFunction_79, () -> isDirty_pushFlowFunction_79);
      dirtyFlagSupplierMap.put(pushFlowFunction_81, () -> isDirty_pushFlowFunction_81);
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
          binaryMapToRefFlowFunction_32, (b) -> isDirty_binaryMapToRefFlowFunction_32 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_36, (b) -> isDirty_binaryMapToRefFlowFunction_36 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_44, (b) -> isDirty_binaryMapToRefFlowFunction_44 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_51, (b) -> isDirty_binaryMapToRefFlowFunction_51 = b);
      dirtyFlagUpdateMap.put(callbackImpl_82, (b) -> isDirty_callbackImpl_82 = b);
      dirtyFlagUpdateMap.put(callbackImpl_83, (b) -> isDirty_callbackImpl_83 = b);
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
          mapRef2RefFlowFunction_34, (b) -> isDirty_mapRef2RefFlowFunction_34 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_38, (b) -> isDirty_mapRef2RefFlowFunction_38 = b);
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
          mapRef2RefFlowFunction_53, (b) -> isDirty_mapRef2RefFlowFunction_53 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_54, (b) -> isDirty_mapRef2RefFlowFunction_54 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_58, (b) -> isDirty_mapRef2RefFlowFunction_58 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_59, (b) -> isDirty_mapRef2RefFlowFunction_59 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_63, (b) -> isDirty_mapRef2RefFlowFunction_63 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_67, (b) -> isDirty_mapRef2RefFlowFunction_67 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_68, (b) -> isDirty_mapRef2RefFlowFunction_68 = b);
      dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_7, (b) -> isDirty_mapRef2RefFlowFunction_7 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_72, (b) -> isDirty_mapRef2RefFlowFunction_72 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_73, (b) -> isDirty_mapRef2RefFlowFunction_73 = b);
      dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_9, (b) -> isDirty_mapRef2RefFlowFunction_9 = b);
      dirtyFlagUpdateMap.put(mergeFlowFunction_2, (b) -> isDirty_mergeFlowFunction_2 = b);
      dirtyFlagUpdateMap.put(mergeFlowFunction_22, (b) -> isDirty_mergeFlowFunction_22 = b);
      dirtyFlagUpdateMap.put(mtmFeePositionMap, (b) -> isDirty_mtmFeePositionMap = b);
      dirtyFlagUpdateMap.put(mtmPositionMap, (b) -> isDirty_mtmPositionMap = b);
      dirtyFlagUpdateMap.put(netPnl, (b) -> isDirty_netPnl = b);
      dirtyFlagUpdateMap.put(pnl, (b) -> isDirty_pnl = b);
      dirtyFlagUpdateMap.put(positionMap, (b) -> isDirty_positionMap = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_56, (b) -> isDirty_pushFlowFunction_56 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_61, (b) -> isDirty_pushFlowFunction_61 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_65, (b) -> isDirty_pushFlowFunction_65 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_70, (b) -> isDirty_pushFlowFunction_70 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_75, (b) -> isDirty_pushFlowFunction_75 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_77, (b) -> isDirty_pushFlowFunction_77 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_79, (b) -> isDirty_pushFlowFunction_79 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_81, (b) -> isDirty_pushFlowFunction_81 = b);
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

  private boolean guardCheck_binaryMapToRefFlowFunction_32() {
    return isDirty_binaryMapToRefFlowFunction_31
        | isDirty_derivedRateNode_27
        | isDirty_mapRef2RefFlowFunction_26;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_36() {
    return isDirty_mapRef2RefFlowFunction_19 | isDirty_mapRef2RefFlowFunction_34;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_44() {
    return isDirty_mapRef2RefFlowFunction_26 | isDirty_mapRef2RefFlowFunction_34;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_51() {
    return isDirty_handlerSignal_positionUpdate
        | isDirty_mapRef2RefFlowFunction_42
        | isDirty_mapRef2RefFlowFunction_50;
  }

  private boolean guardCheck_filterFlowFunction_28() {
    return isDirty_derivedRateNode_27 | isDirty_handlerMidPrice;
  }

  private boolean guardCheck_flatMapFlowFunction_1() {
    return isDirty_callbackImpl_82;
  }

  private boolean guardCheck_flatMapFlowFunction_3() {
    return isDirty_callbackImpl_83;
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

  private boolean guardCheck_mapRef2RefFlowFunction_34() {
    return isDirty_binaryMapToRefFlowFunction_32;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_38() {
    return isDirty_binaryMapToRefFlowFunction_36 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_40() {
    return isDirty_mapRef2RefFlowFunction_38;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_42() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_40;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_46() {
    return isDirty_binaryMapToRefFlowFunction_44 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_48() {
    return isDirty_mapRef2RefFlowFunction_46;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_50() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_48;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_53() {
    return isDirty_mapRef2RefFlowFunction_19;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_54() {
    return isDirty_mapRef2RefFlowFunction_53;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_58() {
    return isDirty_mapRef2RefFlowFunction_26;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_59() {
    return isDirty_mapRef2RefFlowFunction_58;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_63() {
    return isDirty_mapRef2RefFlowFunction_34;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_67() {
    return isDirty_mapRef2RefFlowFunction_38;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_68() {
    return isDirty_mapRef2RefFlowFunction_67;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_72() {
    return isDirty_mapRef2RefFlowFunction_46;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_73() {
    return isDirty_mapRef2RefFlowFunction_72;
  }

  private boolean guardCheck_rates() {
    return isDirty_mapRef2RefFlowFunction_63;
  }

  private boolean guardCheck_mergeFlowFunction_2() {
    return isDirty_flatMapFlowFunction_1 | isDirty_handlerTrade;
  }

  private boolean guardCheck_mergeFlowFunction_22() {
    return isDirty_mapRef2RefFlowFunction_20 | isDirty_mapRef2RefFlowFunction_21;
  }

  private boolean guardCheck_feePositionMap() {
    return isDirty_mapRef2RefFlowFunction_59;
  }

  private boolean guardCheck_mtmFeePositionMap() {
    return isDirty_mapRef2RefFlowFunction_73;
  }

  private boolean guardCheck_mtmPositionMap() {
    return isDirty_mapRef2RefFlowFunction_68;
  }

  private boolean guardCheck_netPnl() {
    return isDirty_binaryMapToRefFlowFunction_51;
  }

  private boolean guardCheck_pnl() {
    return isDirty_mapRef2RefFlowFunction_42;
  }

  private boolean guardCheck_positionMap() {
    return isDirty_mapRef2RefFlowFunction_54;
  }

  private boolean guardCheck_pushFlowFunction_56() {
    return isDirty_positionMap;
  }

  private boolean guardCheck_pushFlowFunction_61() {
    return isDirty_feePositionMap;
  }

  private boolean guardCheck_pushFlowFunction_65() {
    return isDirty_rates;
  }

  private boolean guardCheck_pushFlowFunction_70() {
    return isDirty_mtmPositionMap;
  }

  private boolean guardCheck_pushFlowFunction_75() {
    return isDirty_mtmFeePositionMap;
  }

  private boolean guardCheck_pushFlowFunction_77() {
    return isDirty_tradeFees;
  }

  private boolean guardCheck_pushFlowFunction_79() {
    return isDirty_pnl;
  }

  private boolean guardCheck_pushFlowFunction_81() {
    return isDirty_netPnl;
  }

  private boolean guardCheck_tradeFees() {
    return isDirty_mapRef2RefFlowFunction_50;
  }

  private boolean guardCheck_groupByFlowFunctionWrapper_29() {
    return isDirty_derivedRateNode_27;
  }

  private boolean guardCheck_context() {
    return isDirty_clock;
  }

  private boolean guardCheck_feePositionListener() {
    return isDirty_pushFlowFunction_61;
  }

  private boolean guardCheck_mtmFeePositionListener() {
    return isDirty_pushFlowFunction_75;
  }

  private boolean guardCheck_mtmPositionListener() {
    return isDirty_pushFlowFunction_70;
  }

  private boolean guardCheck_netPnlListener() {
    return isDirty_pushFlowFunction_81;
  }

  private boolean guardCheck_pnlListener() {
    return isDirty_pushFlowFunction_79;
  }

  private boolean guardCheck_positionListener() {
    return isDirty_pushFlowFunction_56;
  }

  private boolean guardCheck_rateListener() {
    return isDirty_pushFlowFunction_65;
  }

  private boolean guardCheck_tradeFeesListener() {
    return isDirty_pushFlowFunction_77;
  }

  private boolean guardCheck_markToMarket_897() {
    return isDirty_feePositionMap
        | isDirty_mtmFeePositionMap
        | isDirty_mtmPositionMap
        | isDirty_netPnl
        | isDirty_pnl
        | isDirty_positionMap
        | isDirty_tradeFees;
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
