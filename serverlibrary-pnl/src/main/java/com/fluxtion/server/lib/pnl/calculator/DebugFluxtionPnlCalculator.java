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
import com.fluxtion.runtime.annotations.OnEventHandler;
import com.fluxtion.runtime.lifecycle.BatchHandler;
import com.fluxtion.runtime.lifecycle.Lifecycle;
import com.fluxtion.runtime.EventProcessor;
import com.fluxtion.runtime.callback.InternalEventProcessor;
import com.fluxtion.runtime.EventProcessorContext;
import com.fluxtion.runtime.annotations.ExportService;
import com.fluxtion.runtime.audit.Auditor;
import com.fluxtion.runtime.audit.EventLogControlEvent;
import com.fluxtion.runtime.audit.EventLogManager;
import com.fluxtion.runtime.audit.NodeNameAuditor;
import com.fluxtion.runtime.callback.CallBackNode;
import com.fluxtion.runtime.callback.CallbackDispatcherImpl;
import com.fluxtion.runtime.callback.ExportFunctionAuditEvent;
import com.fluxtion.runtime.callback.InstanceCallbackEvent.InstanceCallbackEvent_0;
import com.fluxtion.runtime.callback.InstanceCallbackEvent.InstanceCallbackEvent_1;
import com.fluxtion.runtime.callback.InstanceCallbackEvent.InstanceCallbackEvent_2;
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
import com.fluxtion.runtime.dataflow.groupby.GroupByHashMap;
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
import com.fluxtion.server.lib.pnl.FeeInstrumentPosMtm;
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
 * eventProcessorGenerator version : 9.7.8
 * api version                     : 9.7.8
 * </pre>
 *
 * Event classes supported:
 *
 * <ul>
 *   <li>com.fluxtion.compiler.generation.model.ExportFunctionMarker
 *   <li>com.fluxtion.runtime.audit.EventLogControlEvent
 *   <li>com.fluxtion.runtime.callback.InstanceCallbackEvent.InstanceCallbackEvent_0
 *   <li>com.fluxtion.runtime.callback.InstanceCallbackEvent.InstanceCallbackEvent_1
 *   <li>com.fluxtion.runtime.callback.InstanceCallbackEvent.InstanceCallbackEvent_2
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
public class DebugFluxtionPnlCalculator
    implements EventProcessor<DebugFluxtionPnlCalculator>,
        /*--- @ExportService start ---*/
        @ExportService ConfigListener,
        @ExportService ServiceListener,
        /*--- @ExportService end ---*/
        StaticEventProcessor,
        InternalEventProcessor,
        BatchHandler,
        Lifecycle {

  //Node declarations
  private final transient InstanceCallbackEvent_0 callBackTriggerEvent_0 =
      new InstanceCallbackEvent_0();
  private final transient CallBackNode callBackNode_90 = new CallBackNode<>(callBackTriggerEvent_0);
  private final transient InstanceCallbackEvent_1 callBackTriggerEvent_1 =
      new InstanceCallbackEvent_1();
  private final transient CallBackNode callBackNode_121 =
      new CallBackNode<>(callBackTriggerEvent_1);
  private final transient InstanceCallbackEvent_2 callBackTriggerEvent_2 =
      new InstanceCallbackEvent_2();
  private final transient CallBackNode callBackNode_201 =
      new CallBackNode<>(callBackTriggerEvent_2);
  private final transient CallbackDispatcherImpl callbackDispatcher = new CallbackDispatcherImpl();
  public final transient Clock clock = new Clock();
  private final transient EmptyGroupBy emptyGroupBy_470 = new EmptyGroupBy<>();
  private final transient DefaultValue defaultValue_65 = new DefaultValue<>(emptyGroupBy_470);
  public final transient EventLogManager eventLogger = new EventLogManager();
  private final transient GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_11 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, SingleInstrumentPosMtmAggregate::dealt);
  private final transient GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_13 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getContraInstrument, Mappers::identity, SingleInstrumentPosMtmAggregate::contra);
  private final transient GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_15 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, InstrumentPosMtmAggregate::dealt);
  private final transient GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_17 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getContraInstrument, Mappers::identity, InstrumentPosMtmAggregate::contra);
  private final transient GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_20 =
      new GroupByFlowFunctionWrapper<>(
          InstrumentPosition::instrument, Mappers::identity, AggregateIdentityFlowFunction::new);
  private final transient GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_33 =
      new GroupByFlowFunctionWrapper<>(
          InstrumentPosition::instrument, Mappers::identity, AggregateIdentityFlowFunction::new);
  private final transient GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_35 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, FeeInstrumentPosMtmAggregate::new);
  private final transient GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_50 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, FeeInstrumentPosMtmAggregate::new);
  private final transient GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_52 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getContraInstrument, Mappers::identity, FeeInstrumentPosMtmAggregate::new);
  private final transient GroupByHashMap groupByHashMap_59 = new GroupByHashMap<>();
  private final transient GroupByHashMap groupByHashMap_74 = new GroupByHashMap<>();
  private final transient LeftJoin leftJoin_43 = new LeftJoin();
  private final transient LeftJoin leftJoin_82 = new LeftJoin();
  private final transient MapTuple mapTuple_758 = new MapTuple<>(NetMarkToMarket::combine);
  private final transient GroupByMapFlowFunction groupByMapFlowFunction_84 =
      new GroupByMapFlowFunction(mapTuple_758::mapTuple);
  private final transient MapTuple mapTuple_764 = new MapTuple<>(FeeInstrumentPosMtm::merge);
  private final transient GroupByMapFlowFunction groupByMapFlowFunction_63 =
      new GroupByMapFlowFunction(mapTuple_764::mapTuple);
  private final transient MapTuple mapTuple_768 = new MapTuple<>(FeeInstrumentPosMtm::merge);
  private final transient GroupByMapFlowFunction groupByMapFlowFunction_56 =
      new GroupByMapFlowFunction(mapTuple_768::mapTuple);
  private final transient MapTuple mapTuple_783 = new MapTuple<>(InstrumentPosMtm::mergeSnapshot);
  private final transient GroupByMapFlowFunction groupByMapFlowFunction_78 =
      new GroupByMapFlowFunction(mapTuple_783::mapTuple);
  private final transient MapTuple mapTuple_787 = new MapTuple<>(InstrumentPosMtm::merge);
  private final transient GroupByMapFlowFunction groupByMapFlowFunction_71 =
      new GroupByMapFlowFunction(mapTuple_787::mapTuple);
  private final transient MapTuple mapTuple_800 = new MapTuple<>(NetMarkToMarket::combine);
  private final transient GroupByMapFlowFunction groupByMapFlowFunction_45 =
      new GroupByMapFlowFunction(mapTuple_800::mapTuple);
  private final transient MapTuple mapTuple_805 = new MapTuple<>(FeeInstrumentPosMtm::addSnapshot);
  private final transient GroupByMapFlowFunction groupByMapFlowFunction_39 =
      new GroupByMapFlowFunction(mapTuple_805::mapTuple);
  private final transient MapTuple mapTuple_816 = new MapTuple<>(InstrumentPosMtm::addSnapshot);
  private final transient GroupByMapFlowFunction groupByMapFlowFunction_28 =
      new GroupByMapFlowFunction(mapTuple_816::mapTuple);
  private final transient MapTuple mapTuple_820 = new MapTuple<>(InstrumentPosMtm::merge);
  private final transient GroupByMapFlowFunction groupByMapFlowFunction_24 =
      new GroupByMapFlowFunction(mapTuple_820::mapTuple);
  private final transient NamedFeedTableNode namedFeedTableNode_89 =
      new NamedFeedTableNode<>("symbolFeed", Symbol::symbolName);
  public final transient DerivedRateNode derivedRateNode =
      new DerivedRateNode(namedFeedTableNode_89);
  public final transient EventFeedConnector eventFeedBatcher =
      new EventFeedConnector(namedFeedTableNode_89);
  private final transient GroupByMapFlowFunction groupByMapFlowFunction_30 =
      new GroupByMapFlowFunction(derivedRateNode::calculateInstrumentPosMtm);
  private final transient GroupByMapFlowFunction groupByMapFlowFunction_41 =
      new GroupByMapFlowFunction(derivedRateNode::calculateFeeMtm);
  private final transient GroupByMapFlowFunction groupByMapFlowFunction_67 =
      new GroupByMapFlowFunction(derivedRateNode::calculateFeeMtm);
  private final transient GroupByMapFlowFunction groupByMapFlowFunction_80 =
      new GroupByMapFlowFunction(derivedRateNode::calculateInstrumentPosMtm);
  public final transient NodeNameAuditor nodeNameLookup = new NodeNameAuditor();
  private final transient OuterJoin outerJoin_22 = new OuterJoin();
  private final transient OuterJoin outerJoin_26 = new OuterJoin();
  private final transient OuterJoin outerJoin_37 = new OuterJoin();
  private final transient OuterJoin outerJoin_54 = new OuterJoin();
  private final transient OuterJoin outerJoin_61 = new OuterJoin();
  private final transient OuterJoin outerJoin_69 = new OuterJoin();
  private final transient OuterJoin outerJoin_76 = new OuterJoin();
  public final transient PositionCache positionCache = new PositionCache();
  private final transient SubscriptionManagerNode subscriptionManager =
      new SubscriptionManagerNode();
  private final transient MutableEventProcessorContext context =
      new MutableEventProcessorContext(
          nodeNameLookup, callbackDispatcher, subscriptionManager, callbackDispatcher);
  private final transient SinkPublisher globalNetMtmListener =
      new SinkPublisher<>("globalNetMtmListener");
  private final transient DefaultEventHandlerNode handlerPositionSnapshot =
      new DefaultEventHandlerNode<>(
          2147483647,
          "",
          com.fluxtion.server.lib.pnl.PositionSnapshot.class,
          "handlerPositionSnapshot",
          context);
  private final transient FlatMapFlowFunction flatMapFlowFunction_19 =
      new FlatMapFlowFunction<>(handlerPositionSnapshot, PositionSnapshot::getPositions);
  private final transient FlatMapFlowFunction flatMapFlowFunction_32 =
      new FlatMapFlowFunction<>(handlerPositionSnapshot, PositionSnapshot::getFeePositions);
  private final transient DefaultEventHandlerNode handlerSignal_positionSnapshotReset =
      new DefaultEventHandlerNode<>(
          2147483647,
          "positionSnapshotReset",
          com.fluxtion.runtime.event.Signal.class,
          "handlerSignal_positionSnapshotReset",
          context);
  private final transient DefaultEventHandlerNode handlerSignal_positionUpdate =
      new DefaultEventHandlerNode<>(
          2147483647,
          "positionUpdate",
          com.fluxtion.runtime.event.Signal.class,
          "handlerSignal_positionUpdate",
          context);
  private final transient DefaultEventHandlerNode handlerTrade =
      new DefaultEventHandlerNode<>(
          2147483647, "", com.fluxtion.server.lib.pnl.Trade.class, "handlerTrade", context);
  private final transient DefaultEventHandlerNode handlerTradeBatch =
      new DefaultEventHandlerNode<>(
          2147483647,
          "",
          com.fluxtion.server.lib.pnl.TradeBatch.class,
          "handlerTradeBatch",
          context);
  private final transient FlatMapFlowFunction flatMapFlowFunction_3 =
      new FlatMapFlowFunction<>(handlerTradeBatch, TradeBatch::getTrades);
  private final transient SinkPublisher instrumentNetMtmListener =
      new SinkPublisher<>("instrumentNetMtmListener");
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_7 =
      new MapRef2RefFlowFunction<>(handlerTrade, eventFeedBatcher::validateTrade);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_21 =
      new MapRef2RefFlowFunction<>(
          flatMapFlowFunction_19, groupByFlowFunctionWrapper_20::aggregate);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_34 =
      new MapRef2RefFlowFunction<>(
          flatMapFlowFunction_32, groupByFlowFunctionWrapper_33::aggregate);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_58 =
      new MapRef2RefFlowFunction<>(
          handlerPositionSnapshot, PositionSnapshot::getInstrumentFeePositionMap);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_60 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_58, groupByHashMap_59::fromMap);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_73 =
      new MapRef2RefFlowFunction<>(
          handlerPositionSnapshot, PositionSnapshot::getInstrumentPositionMap);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_75 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_73, groupByHashMap_74::fromMap);
  public final transient ServiceRegistryNode serviceRegistry = new ServiceRegistryNode();
  private final transient TradeSequenceFilter tradeSequenceFilter_4 =
      new TradeSequenceFilter(false);
  private final transient FilterFlowFunction filterFlowFunction_5 =
      new FilterFlowFunction<>(
          flatMapFlowFunction_3, tradeSequenceFilter_4::checkTradeSequenceNumber);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_6 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_5, eventFeedBatcher::validateBatchTrade);
  private final transient TradeSequenceFilter tradeSequenceFilter_8 = new TradeSequenceFilter(true);
  private final transient FilterFlowFunction filterFlowFunction_9 =
      new FilterFlowFunction<>(
          mapRef2RefFlowFunction_7, tradeSequenceFilter_8::checkTradeSequenceNumber);
  private final transient MergeFlowFunction mergeFlowFunction_10 =
      new MergeFlowFunction<>(Arrays.asList(filterFlowFunction_9, mapRef2RefFlowFunction_6));
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_12 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_10, groupByFlowFunctionWrapper_11::aggregate);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_14 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_10, groupByFlowFunctionWrapper_13::aggregate);
  private final transient BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_23 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_12, mapRef2RefFlowFunction_14, outerJoin_22::join);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_16 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_10, groupByFlowFunctionWrapper_15::aggregate);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_18 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_10, groupByFlowFunctionWrapper_17::aggregate);
  private final transient BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_70 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_16, mapRef2RefFlowFunction_18, outerJoin_69::join);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_25 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_23, groupByMapFlowFunction_24::mapValues);
  private final transient BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_27 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_25, mapRef2RefFlowFunction_21, outerJoin_26::join);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_29 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_27, groupByMapFlowFunction_28::mapValues);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_31 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_29, groupByMapFlowFunction_30::mapValues);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_36 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_10, groupByFlowFunctionWrapper_35::aggregate);
  private final transient BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_38 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_36, mapRef2RefFlowFunction_34, outerJoin_37::join);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_40 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_38, groupByMapFlowFunction_39::mapValues);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_42 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_40, groupByMapFlowFunction_41::mapValues);
  private final transient BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_44 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_31, mapRef2RefFlowFunction_42, leftJoin_43::join);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_46 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_44, groupByMapFlowFunction_45::mapValues);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_47 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_46, GroupBy<Object, Object>::toMap);
  public final transient MapRef2RefFlowFunction globalNetMtm =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_47, NetMarkToMarket::markToMarketSum);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_51 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_10, groupByFlowFunctionWrapper_50::aggregate);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_53 =
      new MapRef2RefFlowFunction<>(mergeFlowFunction_10, groupByFlowFunctionWrapper_52::aggregate);
  private final transient BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_55 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_51, mapRef2RefFlowFunction_53, outerJoin_54::join);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_57 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_55, groupByMapFlowFunction_56::mapValues);
  private final transient BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_62 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_57, mapRef2RefFlowFunction_60, outerJoin_61::join);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_64 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_62, groupByMapFlowFunction_63::mapValues);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_66 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_64, defaultValue_65::getOrDefault);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_68 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_66, groupByMapFlowFunction_67::mapValues);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_72 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_70, groupByMapFlowFunction_71::mapValues);
  private final transient BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_77 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_72, mapRef2RefFlowFunction_75, outerJoin_76::join);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_79 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_77, groupByMapFlowFunction_78::mapValues);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_81 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_79, groupByMapFlowFunction_80::mapValues);
  private final transient BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_83 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_81, mapRef2RefFlowFunction_68, leftJoin_82::join);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_85 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_83, groupByMapFlowFunction_84::mapValues);
  public final transient MapRef2RefFlowFunction instrumentNetMtm =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_85, GroupBy<Object, Object>::toMap);
  private final transient PushFlowFunction pushFlowFunction_49 =
      new PushFlowFunction<>(globalNetMtm, globalNetMtmListener::publish);
  private final transient PushFlowFunction pushFlowFunction_87 =
      new PushFlowFunction<>(instrumentNetMtm, instrumentNetMtmListener::publish);
  private final transient BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_88 =
      new BinaryMapToRefFlowFunction<>(
          pushFlowFunction_49, pushFlowFunction_87, positionCache::checkPoint);
  private final transient ExportFunctionAuditEvent functionAudit = new ExportFunctionAuditEvent();
  //Dirty flags
  private boolean initCalled = false;
  private boolean processing = false;
  private boolean buffering = false;
  private final transient IdentityHashMap<Object, BooleanSupplier> dirtyFlagSupplierMap =
      new IdentityHashMap<>(62);
  private final transient IdentityHashMap<Object, Consumer<Boolean>> dirtyFlagUpdateMap =
      new IdentityHashMap<>(62);

  private boolean isDirty_binaryMapToRefFlowFunction_23 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_27 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_38 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_44 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_55 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_62 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_70 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_77 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_83 = false;
  private boolean isDirty_callBackNode_90 = false;
  private boolean isDirty_callBackNode_121 = false;
  private boolean isDirty_callBackNode_201 = false;
  private boolean isDirty_clock = false;
  private boolean isDirty_derivedRateNode = false;
  private boolean isDirty_eventFeedBatcher = false;
  private boolean isDirty_filterFlowFunction_5 = false;
  private boolean isDirty_filterFlowFunction_9 = false;
  private boolean isDirty_flatMapFlowFunction_3 = false;
  private boolean isDirty_flatMapFlowFunction_19 = false;
  private boolean isDirty_flatMapFlowFunction_32 = false;
  private boolean isDirty_globalNetMtm = false;
  private boolean isDirty_handlerPositionSnapshot = false;
  private boolean isDirty_handlerSignal_positionSnapshotReset = false;
  private boolean isDirty_handlerSignal_positionUpdate = false;
  private boolean isDirty_handlerTrade = false;
  private boolean isDirty_handlerTradeBatch = false;
  private boolean isDirty_instrumentNetMtm = false;
  private boolean isDirty_mapRef2RefFlowFunction_6 = false;
  private boolean isDirty_mapRef2RefFlowFunction_7 = false;
  private boolean isDirty_mapRef2RefFlowFunction_12 = false;
  private boolean isDirty_mapRef2RefFlowFunction_14 = false;
  private boolean isDirty_mapRef2RefFlowFunction_16 = false;
  private boolean isDirty_mapRef2RefFlowFunction_18 = false;
  private boolean isDirty_mapRef2RefFlowFunction_21 = false;
  private boolean isDirty_mapRef2RefFlowFunction_25 = false;
  private boolean isDirty_mapRef2RefFlowFunction_29 = false;
  private boolean isDirty_mapRef2RefFlowFunction_31 = false;
  private boolean isDirty_mapRef2RefFlowFunction_34 = false;
  private boolean isDirty_mapRef2RefFlowFunction_36 = false;
  private boolean isDirty_mapRef2RefFlowFunction_40 = false;
  private boolean isDirty_mapRef2RefFlowFunction_42 = false;
  private boolean isDirty_mapRef2RefFlowFunction_46 = false;
  private boolean isDirty_mapRef2RefFlowFunction_47 = false;
  private boolean isDirty_mapRef2RefFlowFunction_51 = false;
  private boolean isDirty_mapRef2RefFlowFunction_53 = false;
  private boolean isDirty_mapRef2RefFlowFunction_57 = false;
  private boolean isDirty_mapRef2RefFlowFunction_58 = false;
  private boolean isDirty_mapRef2RefFlowFunction_60 = false;
  private boolean isDirty_mapRef2RefFlowFunction_64 = false;
  private boolean isDirty_mapRef2RefFlowFunction_66 = false;
  private boolean isDirty_mapRef2RefFlowFunction_68 = false;
  private boolean isDirty_mapRef2RefFlowFunction_72 = false;
  private boolean isDirty_mapRef2RefFlowFunction_73 = false;
  private boolean isDirty_mapRef2RefFlowFunction_75 = false;
  private boolean isDirty_mapRef2RefFlowFunction_79 = false;
  private boolean isDirty_mapRef2RefFlowFunction_81 = false;
  private boolean isDirty_mapRef2RefFlowFunction_85 = false;
  private boolean isDirty_mergeFlowFunction_10 = false;
  private boolean isDirty_namedFeedTableNode_89 = false;
  private boolean isDirty_positionCache = false;
  private boolean isDirty_pushFlowFunction_49 = false;
  private boolean isDirty_pushFlowFunction_87 = false;

  //Forked declarations

  //Filter constants

  //unknown event handler
  private Consumer unKnownEventHandler = (e) -> {};

  public DebugFluxtionPnlCalculator(Map<Object, Object> contextMap) {
    if (context != null) {
      context.replaceMappings(contextMap);
    }
    eventLogger.trace = (boolean) true;
    eventLogger.printEventToString = (boolean) true;
    eventLogger.printThreadName = (boolean) true;
    eventLogger.traceLevel = com.fluxtion.runtime.audit.EventLogControlEvent.LogLevel.INFO;
    eventLogger.clock = clock;
    binaryMapToRefFlowFunction_23.setDefaultValue(new EmptyGroupBy());
    binaryMapToRefFlowFunction_23.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_27.setDefaultValue(new EmptyGroupBy());
    binaryMapToRefFlowFunction_27.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_38.setDefaultValue(new EmptyGroupBy());
    binaryMapToRefFlowFunction_38.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_44.setDefaultValue(new EmptyGroupBy());
    binaryMapToRefFlowFunction_44.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_55.setDefaultValue(new EmptyGroupBy());
    binaryMapToRefFlowFunction_55.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_62.setDefaultValue(new EmptyGroupBy());
    binaryMapToRefFlowFunction_62.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_70.setDefaultValue(new EmptyGroupBy());
    binaryMapToRefFlowFunction_70.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_77.setDefaultValue(new EmptyGroupBy());
    binaryMapToRefFlowFunction_77.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_83.setDefaultValue(new EmptyGroupBy());
    binaryMapToRefFlowFunction_83.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_88.setEventProcessorContext(context);
    filterFlowFunction_5.setEventProcessorContext(context);
    filterFlowFunction_9.setEventProcessorContext(context);
    flatMapFlowFunction_3.setFlatMapCompleteSignal("positionUpdate");
    flatMapFlowFunction_3.callback = callBackNode_90;
    flatMapFlowFunction_3.dirtyStateMonitor = callbackDispatcher;
    flatMapFlowFunction_19.callback = callBackNode_121;
    flatMapFlowFunction_19.dirtyStateMonitor = callbackDispatcher;
    flatMapFlowFunction_32.callback = callBackNode_201;
    flatMapFlowFunction_32.dirtyStateMonitor = callbackDispatcher;
    globalNetMtm.setEventProcessorContext(context);
    instrumentNetMtm.setEventProcessorContext(context);
    mapRef2RefFlowFunction_6.setEventProcessorContext(context);
    mapRef2RefFlowFunction_7.setEventProcessorContext(context);
    mapRef2RefFlowFunction_12.setDefaultValue(new EmptyGroupBy());
    mapRef2RefFlowFunction_12.setEventProcessorContext(context);
    mapRef2RefFlowFunction_12.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_14.setDefaultValue(new EmptyGroupBy());
    mapRef2RefFlowFunction_14.setEventProcessorContext(context);
    mapRef2RefFlowFunction_14.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_16.setDefaultValue(new EmptyGroupBy());
    mapRef2RefFlowFunction_16.setEventProcessorContext(context);
    mapRef2RefFlowFunction_16.setPublishTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_16.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_18.setDefaultValue(new EmptyGroupBy());
    mapRef2RefFlowFunction_18.setEventProcessorContext(context);
    mapRef2RefFlowFunction_18.setPublishTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_18.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_21.setDefaultValue(new EmptyGroupBy());
    mapRef2RefFlowFunction_21.setEventProcessorContext(context);
    mapRef2RefFlowFunction_21.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_21.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_25.setEventProcessorContext(context);
    mapRef2RefFlowFunction_25.setPublishTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_29.setEventProcessorContext(context);
    mapRef2RefFlowFunction_31.setEventProcessorContext(context);
    mapRef2RefFlowFunction_31.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_31.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_34.setDefaultValue(new EmptyGroupBy());
    mapRef2RefFlowFunction_34.setEventProcessorContext(context);
    mapRef2RefFlowFunction_34.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_34.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_36.setDefaultValue(new EmptyGroupBy());
    mapRef2RefFlowFunction_36.setEventProcessorContext(context);
    mapRef2RefFlowFunction_36.setPublishTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_40.setEventProcessorContext(context);
    mapRef2RefFlowFunction_40.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_42.setEventProcessorContext(context);
    mapRef2RefFlowFunction_42.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_42.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_46.setEventProcessorContext(context);
    mapRef2RefFlowFunction_46.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_47.setEventProcessorContext(context);
    mapRef2RefFlowFunction_51.setDefaultValue(new EmptyGroupBy());
    mapRef2RefFlowFunction_51.setEventProcessorContext(context);
    mapRef2RefFlowFunction_53.setDefaultValue(new EmptyGroupBy());
    mapRef2RefFlowFunction_53.setEventProcessorContext(context);
    mapRef2RefFlowFunction_57.setEventProcessorContext(context);
    mapRef2RefFlowFunction_57.setPublishTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_58.setEventProcessorContext(context);
    mapRef2RefFlowFunction_60.setDefaultValue(new EmptyGroupBy());
    mapRef2RefFlowFunction_60.setEventProcessorContext(context);
    mapRef2RefFlowFunction_64.setEventProcessorContext(context);
    mapRef2RefFlowFunction_64.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_66.setEventProcessorContext(context);
    mapRef2RefFlowFunction_68.setEventProcessorContext(context);
    mapRef2RefFlowFunction_68.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_68.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_72.setEventProcessorContext(context);
    mapRef2RefFlowFunction_72.setPublishTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_73.setEventProcessorContext(context);
    mapRef2RefFlowFunction_75.setDefaultValue(new EmptyGroupBy());
    mapRef2RefFlowFunction_75.setEventProcessorContext(context);
    mapRef2RefFlowFunction_79.setEventProcessorContext(context);
    mapRef2RefFlowFunction_81.setEventProcessorContext(context);
    mapRef2RefFlowFunction_81.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_81.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_85.setEventProcessorContext(context);
    mapRef2RefFlowFunction_85.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mergeFlowFunction_10.dirtyStateMonitor = callbackDispatcher;
    pushFlowFunction_49.setEventProcessorContext(context);
    pushFlowFunction_87.setEventProcessorContext(context);
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

  public DebugFluxtionPnlCalculator() {
    this(null);
  }

  @Override
  public void init() {
    initCalled = true;
    auditEvent(Lifecycle.LifecycleEvent.Init);
    //initialise dirty lookup map
    isDirty("test");
    clock.init();
    namedFeedTableNode_89.initialise();
    namedFeedTableNode_89.init();
    derivedRateNode.init();
    eventFeedBatcher.init();
    positionCache.init();
    handlerPositionSnapshot.init();
    flatMapFlowFunction_19.init();
    flatMapFlowFunction_32.init();
    handlerSignal_positionSnapshotReset.init();
    handlerSignal_positionUpdate.init();
    handlerTrade.init();
    handlerTradeBatch.init();
    flatMapFlowFunction_3.init();
    mapRef2RefFlowFunction_7.initialiseEventStream();
    mapRef2RefFlowFunction_21.initialiseEventStream();
    mapRef2RefFlowFunction_34.initialiseEventStream();
    mapRef2RefFlowFunction_58.initialiseEventStream();
    mapRef2RefFlowFunction_60.initialiseEventStream();
    mapRef2RefFlowFunction_73.initialiseEventStream();
    mapRef2RefFlowFunction_75.initialiseEventStream();
    tradeSequenceFilter_4.init();
    filterFlowFunction_5.initialiseEventStream();
    mapRef2RefFlowFunction_6.initialiseEventStream();
    tradeSequenceFilter_8.init();
    filterFlowFunction_9.initialiseEventStream();
    mapRef2RefFlowFunction_12.initialiseEventStream();
    mapRef2RefFlowFunction_14.initialiseEventStream();
    binaryMapToRefFlowFunction_23.initialiseEventStream();
    mapRef2RefFlowFunction_16.initialiseEventStream();
    mapRef2RefFlowFunction_18.initialiseEventStream();
    binaryMapToRefFlowFunction_70.initialiseEventStream();
    mapRef2RefFlowFunction_25.initialiseEventStream();
    binaryMapToRefFlowFunction_27.initialiseEventStream();
    mapRef2RefFlowFunction_29.initialiseEventStream();
    mapRef2RefFlowFunction_31.initialiseEventStream();
    mapRef2RefFlowFunction_36.initialiseEventStream();
    binaryMapToRefFlowFunction_38.initialiseEventStream();
    mapRef2RefFlowFunction_40.initialiseEventStream();
    mapRef2RefFlowFunction_42.initialiseEventStream();
    binaryMapToRefFlowFunction_44.initialiseEventStream();
    mapRef2RefFlowFunction_46.initialiseEventStream();
    mapRef2RefFlowFunction_47.initialiseEventStream();
    globalNetMtm.initialiseEventStream();
    mapRef2RefFlowFunction_51.initialiseEventStream();
    mapRef2RefFlowFunction_53.initialiseEventStream();
    binaryMapToRefFlowFunction_55.initialiseEventStream();
    mapRef2RefFlowFunction_57.initialiseEventStream();
    binaryMapToRefFlowFunction_62.initialiseEventStream();
    mapRef2RefFlowFunction_64.initialiseEventStream();
    mapRef2RefFlowFunction_66.initialiseEventStream();
    mapRef2RefFlowFunction_68.initialiseEventStream();
    mapRef2RefFlowFunction_72.initialiseEventStream();
    binaryMapToRefFlowFunction_77.initialiseEventStream();
    mapRef2RefFlowFunction_79.initialiseEventStream();
    mapRef2RefFlowFunction_81.initialiseEventStream();
    binaryMapToRefFlowFunction_83.initialiseEventStream();
    mapRef2RefFlowFunction_85.initialiseEventStream();
    instrumentNetMtm.initialiseEventStream();
    pushFlowFunction_49.initialiseEventStream();
    pushFlowFunction_87.initialiseEventStream();
    binaryMapToRefFlowFunction_88.initialiseEventStream();
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
  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
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
    } else if (event
        instanceof com.fluxtion.runtime.callback.InstanceCallbackEvent.InstanceCallbackEvent_2) {
      InstanceCallbackEvent_2 typedEvent = (InstanceCallbackEvent_2) event;
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
    auditInvocation(callBackNode_90, "callBackNode_90", "onEvent", typedEvent);
    isDirty_callBackNode_90 = callBackNode_90.onEvent(typedEvent);
    if (guardCheck_flatMapFlowFunction_3()) {
      auditInvocation(
          flatMapFlowFunction_3, "flatMapFlowFunction_3", "callbackReceived", typedEvent);
      isDirty_flatMapFlowFunction_3 = true;
      flatMapFlowFunction_3.callbackReceived();
      if (isDirty_flatMapFlowFunction_3) {
        filterFlowFunction_5.inputUpdated(flatMapFlowFunction_3);
      }
    }
    if (guardCheck_filterFlowFunction_5()) {
      auditInvocation(filterFlowFunction_5, "filterFlowFunction_5", "filter", typedEvent);
      isDirty_filterFlowFunction_5 = filterFlowFunction_5.filter();
      if (isDirty_filterFlowFunction_5) {
        mapRef2RefFlowFunction_6.inputUpdated(filterFlowFunction_5);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_6()) {
      auditInvocation(mapRef2RefFlowFunction_6, "mapRef2RefFlowFunction_6", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_6 = mapRef2RefFlowFunction_6.map();
      if (isDirty_mapRef2RefFlowFunction_6) {
        mergeFlowFunction_10.inputStreamUpdated(mapRef2RefFlowFunction_6);
      }
    }
    if (guardCheck_mergeFlowFunction_10()) {
      auditInvocation(mergeFlowFunction_10, "mergeFlowFunction_10", "publishMerge", typedEvent);
      isDirty_mergeFlowFunction_10 = mergeFlowFunction_10.publishMerge();
      if (isDirty_mergeFlowFunction_10) {
        mapRef2RefFlowFunction_12.inputUpdated(mergeFlowFunction_10);
        mapRef2RefFlowFunction_14.inputUpdated(mergeFlowFunction_10);
        mapRef2RefFlowFunction_16.inputUpdated(mergeFlowFunction_10);
        mapRef2RefFlowFunction_18.inputUpdated(mergeFlowFunction_10);
        mapRef2RefFlowFunction_36.inputUpdated(mergeFlowFunction_10);
        mapRef2RefFlowFunction_51.inputUpdated(mergeFlowFunction_10);
        mapRef2RefFlowFunction_53.inputUpdated(mergeFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_23.inputUpdated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_23.input2Updated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_23()) {
      auditInvocation(
          binaryMapToRefFlowFunction_23, "binaryMapToRefFlowFunction_23", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_23 = binaryMapToRefFlowFunction_23.map();
      if (isDirty_binaryMapToRefFlowFunction_23) {
        mapRef2RefFlowFunction_25.inputUpdated(binaryMapToRefFlowFunction_23);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_70.inputUpdated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_18()) {
      auditInvocation(mapRef2RefFlowFunction_18, "mapRef2RefFlowFunction_18", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_18 = mapRef2RefFlowFunction_18.map();
      if (isDirty_mapRef2RefFlowFunction_18) {
        binaryMapToRefFlowFunction_70.input2Updated(mapRef2RefFlowFunction_18);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_70()) {
      auditInvocation(
          binaryMapToRefFlowFunction_70, "binaryMapToRefFlowFunction_70", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_70 = binaryMapToRefFlowFunction_70.map();
      if (isDirty_binaryMapToRefFlowFunction_70) {
        mapRef2RefFlowFunction_72.inputUpdated(binaryMapToRefFlowFunction_70);
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
        binaryMapToRefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_31);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_36()) {
      auditInvocation(mapRef2RefFlowFunction_36, "mapRef2RefFlowFunction_36", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_36 = mapRef2RefFlowFunction_36.map();
      if (isDirty_mapRef2RefFlowFunction_36) {
        binaryMapToRefFlowFunction_38.inputUpdated(mapRef2RefFlowFunction_36);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_38()) {
      auditInvocation(
          binaryMapToRefFlowFunction_38, "binaryMapToRefFlowFunction_38", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_38 = binaryMapToRefFlowFunction_38.map();
      if (isDirty_binaryMapToRefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(binaryMapToRefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      auditInvocation(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        mapRef2RefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_42()) {
      auditInvocation(mapRef2RefFlowFunction_42, "mapRef2RefFlowFunction_42", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
      if (isDirty_mapRef2RefFlowFunction_42) {
        binaryMapToRefFlowFunction_44.input2Updated(mapRef2RefFlowFunction_42);
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
    if (guardCheck_mapRef2RefFlowFunction_51()) {
      auditInvocation(mapRef2RefFlowFunction_51, "mapRef2RefFlowFunction_51", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_51 = mapRef2RefFlowFunction_51.map();
      if (isDirty_mapRef2RefFlowFunction_51) {
        binaryMapToRefFlowFunction_55.inputUpdated(mapRef2RefFlowFunction_51);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_53()) {
      auditInvocation(mapRef2RefFlowFunction_53, "mapRef2RefFlowFunction_53", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_53 = mapRef2RefFlowFunction_53.map();
      if (isDirty_mapRef2RefFlowFunction_53) {
        binaryMapToRefFlowFunction_55.input2Updated(mapRef2RefFlowFunction_53);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_55()) {
      auditInvocation(
          binaryMapToRefFlowFunction_55, "binaryMapToRefFlowFunction_55", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_55 = binaryMapToRefFlowFunction_55.map();
      if (isDirty_binaryMapToRefFlowFunction_55) {
        mapRef2RefFlowFunction_57.inputUpdated(binaryMapToRefFlowFunction_55);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_57()) {
      auditInvocation(mapRef2RefFlowFunction_57, "mapRef2RefFlowFunction_57", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_57 = mapRef2RefFlowFunction_57.map();
      if (isDirty_mapRef2RefFlowFunction_57) {
        binaryMapToRefFlowFunction_62.inputUpdated(mapRef2RefFlowFunction_57);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_62()) {
      auditInvocation(
          binaryMapToRefFlowFunction_62, "binaryMapToRefFlowFunction_62", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_62 = binaryMapToRefFlowFunction_62.map();
      if (isDirty_binaryMapToRefFlowFunction_62) {
        mapRef2RefFlowFunction_64.inputUpdated(binaryMapToRefFlowFunction_62);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_64()) {
      auditInvocation(mapRef2RefFlowFunction_64, "mapRef2RefFlowFunction_64", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_64 = mapRef2RefFlowFunction_64.map();
      if (isDirty_mapRef2RefFlowFunction_64) {
        mapRef2RefFlowFunction_66.inputUpdated(mapRef2RefFlowFunction_64);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_66()) {
      auditInvocation(mapRef2RefFlowFunction_66, "mapRef2RefFlowFunction_66", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_66 = mapRef2RefFlowFunction_66.map();
      if (isDirty_mapRef2RefFlowFunction_66) {
        mapRef2RefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_66);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_68()) {
      auditInvocation(mapRef2RefFlowFunction_68, "mapRef2RefFlowFunction_68", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_68 = mapRef2RefFlowFunction_68.map();
      if (isDirty_mapRef2RefFlowFunction_68) {
        binaryMapToRefFlowFunction_83.input2Updated(mapRef2RefFlowFunction_68);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_72()) {
      auditInvocation(mapRef2RefFlowFunction_72, "mapRef2RefFlowFunction_72", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_72 = mapRef2RefFlowFunction_72.map();
      if (isDirty_mapRef2RefFlowFunction_72) {
        binaryMapToRefFlowFunction_77.inputUpdated(mapRef2RefFlowFunction_72);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_77()) {
      auditInvocation(
          binaryMapToRefFlowFunction_77, "binaryMapToRefFlowFunction_77", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_77 = binaryMapToRefFlowFunction_77.map();
      if (isDirty_binaryMapToRefFlowFunction_77) {
        mapRef2RefFlowFunction_79.inputUpdated(binaryMapToRefFlowFunction_77);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_79()) {
      auditInvocation(mapRef2RefFlowFunction_79, "mapRef2RefFlowFunction_79", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_79 = mapRef2RefFlowFunction_79.map();
      if (isDirty_mapRef2RefFlowFunction_79) {
        mapRef2RefFlowFunction_81.inputUpdated(mapRef2RefFlowFunction_79);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_81()) {
      auditInvocation(mapRef2RefFlowFunction_81, "mapRef2RefFlowFunction_81", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_81 = mapRef2RefFlowFunction_81.map();
      if (isDirty_mapRef2RefFlowFunction_81) {
        binaryMapToRefFlowFunction_83.inputUpdated(mapRef2RefFlowFunction_81);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_83()) {
      auditInvocation(
          binaryMapToRefFlowFunction_83, "binaryMapToRefFlowFunction_83", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_83 = binaryMapToRefFlowFunction_83.map();
      if (isDirty_binaryMapToRefFlowFunction_83) {
        mapRef2RefFlowFunction_85.inputUpdated(binaryMapToRefFlowFunction_83);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_85()) {
      auditInvocation(mapRef2RefFlowFunction_85, "mapRef2RefFlowFunction_85", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_85 = mapRef2RefFlowFunction_85.map();
      if (isDirty_mapRef2RefFlowFunction_85) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_85);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_87.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_49()) {
      auditInvocation(pushFlowFunction_49, "pushFlowFunction_49", "push", typedEvent);
      isDirty_pushFlowFunction_49 = pushFlowFunction_49.push();
      if (isDirty_pushFlowFunction_49) {
        binaryMapToRefFlowFunction_88.inputUpdated(pushFlowFunction_49);
      }
    }
    if (guardCheck_pushFlowFunction_87()) {
      auditInvocation(pushFlowFunction_87, "pushFlowFunction_87", "push", typedEvent);
      isDirty_pushFlowFunction_87 = pushFlowFunction_87.push();
      if (isDirty_pushFlowFunction_87) {
        binaryMapToRefFlowFunction_88.input2Updated(pushFlowFunction_87);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_88()) {
      auditInvocation(
          binaryMapToRefFlowFunction_88, "binaryMapToRefFlowFunction_88", "map", typedEvent);
      binaryMapToRefFlowFunction_88.map();
    }
    afterEvent();
  }

  public void handleEvent(InstanceCallbackEvent_1 typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(callBackNode_121, "callBackNode_121", "onEvent", typedEvent);
    isDirty_callBackNode_121 = callBackNode_121.onEvent(typedEvent);
    if (guardCheck_flatMapFlowFunction_19()) {
      auditInvocation(
          flatMapFlowFunction_19, "flatMapFlowFunction_19", "callbackReceived", typedEvent);
      isDirty_flatMapFlowFunction_19 = true;
      flatMapFlowFunction_19.callbackReceived();
      if (isDirty_flatMapFlowFunction_19) {
        mapRef2RefFlowFunction_21.inputUpdated(flatMapFlowFunction_19);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_21()) {
      auditInvocation(mapRef2RefFlowFunction_21, "mapRef2RefFlowFunction_21", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_21 = mapRef2RefFlowFunction_21.map();
      if (isDirty_mapRef2RefFlowFunction_21) {
        binaryMapToRefFlowFunction_27.input2Updated(mapRef2RefFlowFunction_21);
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
        binaryMapToRefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_31);
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
    if (guardCheck_pushFlowFunction_49()) {
      auditInvocation(pushFlowFunction_49, "pushFlowFunction_49", "push", typedEvent);
      isDirty_pushFlowFunction_49 = pushFlowFunction_49.push();
      if (isDirty_pushFlowFunction_49) {
        binaryMapToRefFlowFunction_88.inputUpdated(pushFlowFunction_49);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_88()) {
      auditInvocation(
          binaryMapToRefFlowFunction_88, "binaryMapToRefFlowFunction_88", "map", typedEvent);
      binaryMapToRefFlowFunction_88.map();
    }
    afterEvent();
  }

  public void handleEvent(InstanceCallbackEvent_2 typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(callBackNode_201, "callBackNode_201", "onEvent", typedEvent);
    isDirty_callBackNode_201 = callBackNode_201.onEvent(typedEvent);
    if (guardCheck_flatMapFlowFunction_32()) {
      auditInvocation(
          flatMapFlowFunction_32, "flatMapFlowFunction_32", "callbackReceived", typedEvent);
      isDirty_flatMapFlowFunction_32 = true;
      flatMapFlowFunction_32.callbackReceived();
      if (isDirty_flatMapFlowFunction_32) {
        mapRef2RefFlowFunction_34.inputUpdated(flatMapFlowFunction_32);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_34()) {
      auditInvocation(mapRef2RefFlowFunction_34, "mapRef2RefFlowFunction_34", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_34 = mapRef2RefFlowFunction_34.map();
      if (isDirty_mapRef2RefFlowFunction_34) {
        binaryMapToRefFlowFunction_38.input2Updated(mapRef2RefFlowFunction_34);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_38()) {
      auditInvocation(
          binaryMapToRefFlowFunction_38, "binaryMapToRefFlowFunction_38", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_38 = binaryMapToRefFlowFunction_38.map();
      if (isDirty_binaryMapToRefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(binaryMapToRefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      auditInvocation(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        mapRef2RefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_42()) {
      auditInvocation(mapRef2RefFlowFunction_42, "mapRef2RefFlowFunction_42", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
      if (isDirty_mapRef2RefFlowFunction_42) {
        binaryMapToRefFlowFunction_44.input2Updated(mapRef2RefFlowFunction_42);
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
    if (guardCheck_pushFlowFunction_49()) {
      auditInvocation(pushFlowFunction_49, "pushFlowFunction_49", "push", typedEvent);
      isDirty_pushFlowFunction_49 = pushFlowFunction_49.push();
      if (isDirty_pushFlowFunction_49) {
        binaryMapToRefFlowFunction_88.inputUpdated(pushFlowFunction_49);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_88()) {
      auditInvocation(
          binaryMapToRefFlowFunction_88, "binaryMapToRefFlowFunction_88", "map", typedEvent);
      binaryMapToRefFlowFunction_88.map();
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
      flatMapFlowFunction_19.inputUpdatedAndFlatMap(handlerPositionSnapshot);
      flatMapFlowFunction_32.inputUpdatedAndFlatMap(handlerPositionSnapshot);
      mapRef2RefFlowFunction_58.inputUpdated(handlerPositionSnapshot);
      mapRef2RefFlowFunction_73.inputUpdated(handlerPositionSnapshot);
    }
    if (guardCheck_mapRef2RefFlowFunction_58()) {
      auditInvocation(mapRef2RefFlowFunction_58, "mapRef2RefFlowFunction_58", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_58 = mapRef2RefFlowFunction_58.map();
      if (isDirty_mapRef2RefFlowFunction_58) {
        mapRef2RefFlowFunction_60.inputUpdated(mapRef2RefFlowFunction_58);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_60()) {
      auditInvocation(mapRef2RefFlowFunction_60, "mapRef2RefFlowFunction_60", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_60 = mapRef2RefFlowFunction_60.map();
      if (isDirty_mapRef2RefFlowFunction_60) {
        binaryMapToRefFlowFunction_62.input2Updated(mapRef2RefFlowFunction_60);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_73()) {
      auditInvocation(mapRef2RefFlowFunction_73, "mapRef2RefFlowFunction_73", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_73 = mapRef2RefFlowFunction_73.map();
      if (isDirty_mapRef2RefFlowFunction_73) {
        mapRef2RefFlowFunction_75.inputUpdated(mapRef2RefFlowFunction_73);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_75()) {
      auditInvocation(mapRef2RefFlowFunction_75, "mapRef2RefFlowFunction_75", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_75 = mapRef2RefFlowFunction_75.map();
      if (isDirty_mapRef2RefFlowFunction_75) {
        binaryMapToRefFlowFunction_77.input2Updated(mapRef2RefFlowFunction_75);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_62()) {
      auditInvocation(
          binaryMapToRefFlowFunction_62, "binaryMapToRefFlowFunction_62", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_62 = binaryMapToRefFlowFunction_62.map();
      if (isDirty_binaryMapToRefFlowFunction_62) {
        mapRef2RefFlowFunction_64.inputUpdated(binaryMapToRefFlowFunction_62);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_64()) {
      auditInvocation(mapRef2RefFlowFunction_64, "mapRef2RefFlowFunction_64", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_64 = mapRef2RefFlowFunction_64.map();
      if (isDirty_mapRef2RefFlowFunction_64) {
        mapRef2RefFlowFunction_66.inputUpdated(mapRef2RefFlowFunction_64);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_66()) {
      auditInvocation(mapRef2RefFlowFunction_66, "mapRef2RefFlowFunction_66", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_66 = mapRef2RefFlowFunction_66.map();
      if (isDirty_mapRef2RefFlowFunction_66) {
        mapRef2RefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_66);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_68()) {
      auditInvocation(mapRef2RefFlowFunction_68, "mapRef2RefFlowFunction_68", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_68 = mapRef2RefFlowFunction_68.map();
      if (isDirty_mapRef2RefFlowFunction_68) {
        binaryMapToRefFlowFunction_83.input2Updated(mapRef2RefFlowFunction_68);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_77()) {
      auditInvocation(
          binaryMapToRefFlowFunction_77, "binaryMapToRefFlowFunction_77", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_77 = binaryMapToRefFlowFunction_77.map();
      if (isDirty_binaryMapToRefFlowFunction_77) {
        mapRef2RefFlowFunction_79.inputUpdated(binaryMapToRefFlowFunction_77);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_79()) {
      auditInvocation(mapRef2RefFlowFunction_79, "mapRef2RefFlowFunction_79", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_79 = mapRef2RefFlowFunction_79.map();
      if (isDirty_mapRef2RefFlowFunction_79) {
        mapRef2RefFlowFunction_81.inputUpdated(mapRef2RefFlowFunction_79);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_81()) {
      auditInvocation(mapRef2RefFlowFunction_81, "mapRef2RefFlowFunction_81", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_81 = mapRef2RefFlowFunction_81.map();
      if (isDirty_mapRef2RefFlowFunction_81) {
        binaryMapToRefFlowFunction_83.inputUpdated(mapRef2RefFlowFunction_81);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_83()) {
      auditInvocation(
          binaryMapToRefFlowFunction_83, "binaryMapToRefFlowFunction_83", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_83 = binaryMapToRefFlowFunction_83.map();
      if (isDirty_binaryMapToRefFlowFunction_83) {
        mapRef2RefFlowFunction_85.inputUpdated(binaryMapToRefFlowFunction_83);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_85()) {
      auditInvocation(mapRef2RefFlowFunction_85, "mapRef2RefFlowFunction_85", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_85 = mapRef2RefFlowFunction_85.map();
      if (isDirty_mapRef2RefFlowFunction_85) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_85);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_87.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_87()) {
      auditInvocation(pushFlowFunction_87, "pushFlowFunction_87", "push", typedEvent);
      isDirty_pushFlowFunction_87 = pushFlowFunction_87.push();
      if (isDirty_pushFlowFunction_87) {
        binaryMapToRefFlowFunction_88.input2Updated(pushFlowFunction_87);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_88()) {
      auditInvocation(
          binaryMapToRefFlowFunction_88, "binaryMapToRefFlowFunction_88", "map", typedEvent);
      binaryMapToRefFlowFunction_88.map();
    }
    afterEvent();
  }

  public void handleEvent(Trade typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(positionCache, "positionCache", "tradeIn", typedEvent);
    isDirty_positionCache = positionCache.tradeIn(typedEvent);
    auditInvocation(handlerTrade, "handlerTrade", "onEvent", typedEvent);
    isDirty_handlerTrade = handlerTrade.onEvent(typedEvent);
    if (isDirty_handlerTrade) {
      mapRef2RefFlowFunction_7.inputUpdated(handlerTrade);
    }
    if (guardCheck_mapRef2RefFlowFunction_7()) {
      auditInvocation(mapRef2RefFlowFunction_7, "mapRef2RefFlowFunction_7", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_7 = mapRef2RefFlowFunction_7.map();
      if (isDirty_mapRef2RefFlowFunction_7) {
        filterFlowFunction_9.inputUpdated(mapRef2RefFlowFunction_7);
      }
    }
    if (guardCheck_filterFlowFunction_9()) {
      auditInvocation(filterFlowFunction_9, "filterFlowFunction_9", "filter", typedEvent);
      isDirty_filterFlowFunction_9 = filterFlowFunction_9.filter();
      if (isDirty_filterFlowFunction_9) {
        mergeFlowFunction_10.inputStreamUpdated(filterFlowFunction_9);
      }
    }
    if (guardCheck_mergeFlowFunction_10()) {
      auditInvocation(mergeFlowFunction_10, "mergeFlowFunction_10", "publishMerge", typedEvent);
      isDirty_mergeFlowFunction_10 = mergeFlowFunction_10.publishMerge();
      if (isDirty_mergeFlowFunction_10) {
        mapRef2RefFlowFunction_12.inputUpdated(mergeFlowFunction_10);
        mapRef2RefFlowFunction_14.inputUpdated(mergeFlowFunction_10);
        mapRef2RefFlowFunction_16.inputUpdated(mergeFlowFunction_10);
        mapRef2RefFlowFunction_18.inputUpdated(mergeFlowFunction_10);
        mapRef2RefFlowFunction_36.inputUpdated(mergeFlowFunction_10);
        mapRef2RefFlowFunction_51.inputUpdated(mergeFlowFunction_10);
        mapRef2RefFlowFunction_53.inputUpdated(mergeFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_23.inputUpdated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_23.input2Updated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_23()) {
      auditInvocation(
          binaryMapToRefFlowFunction_23, "binaryMapToRefFlowFunction_23", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_23 = binaryMapToRefFlowFunction_23.map();
      if (isDirty_binaryMapToRefFlowFunction_23) {
        mapRef2RefFlowFunction_25.inputUpdated(binaryMapToRefFlowFunction_23);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_70.inputUpdated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_18()) {
      auditInvocation(mapRef2RefFlowFunction_18, "mapRef2RefFlowFunction_18", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_18 = mapRef2RefFlowFunction_18.map();
      if (isDirty_mapRef2RefFlowFunction_18) {
        binaryMapToRefFlowFunction_70.input2Updated(mapRef2RefFlowFunction_18);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_70()) {
      auditInvocation(
          binaryMapToRefFlowFunction_70, "binaryMapToRefFlowFunction_70", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_70 = binaryMapToRefFlowFunction_70.map();
      if (isDirty_binaryMapToRefFlowFunction_70) {
        mapRef2RefFlowFunction_72.inputUpdated(binaryMapToRefFlowFunction_70);
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
        binaryMapToRefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_31);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_36()) {
      auditInvocation(mapRef2RefFlowFunction_36, "mapRef2RefFlowFunction_36", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_36 = mapRef2RefFlowFunction_36.map();
      if (isDirty_mapRef2RefFlowFunction_36) {
        binaryMapToRefFlowFunction_38.inputUpdated(mapRef2RefFlowFunction_36);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_38()) {
      auditInvocation(
          binaryMapToRefFlowFunction_38, "binaryMapToRefFlowFunction_38", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_38 = binaryMapToRefFlowFunction_38.map();
      if (isDirty_binaryMapToRefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(binaryMapToRefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      auditInvocation(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        mapRef2RefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_42()) {
      auditInvocation(mapRef2RefFlowFunction_42, "mapRef2RefFlowFunction_42", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
      if (isDirty_mapRef2RefFlowFunction_42) {
        binaryMapToRefFlowFunction_44.input2Updated(mapRef2RefFlowFunction_42);
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
    if (guardCheck_mapRef2RefFlowFunction_51()) {
      auditInvocation(mapRef2RefFlowFunction_51, "mapRef2RefFlowFunction_51", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_51 = mapRef2RefFlowFunction_51.map();
      if (isDirty_mapRef2RefFlowFunction_51) {
        binaryMapToRefFlowFunction_55.inputUpdated(mapRef2RefFlowFunction_51);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_53()) {
      auditInvocation(mapRef2RefFlowFunction_53, "mapRef2RefFlowFunction_53", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_53 = mapRef2RefFlowFunction_53.map();
      if (isDirty_mapRef2RefFlowFunction_53) {
        binaryMapToRefFlowFunction_55.input2Updated(mapRef2RefFlowFunction_53);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_55()) {
      auditInvocation(
          binaryMapToRefFlowFunction_55, "binaryMapToRefFlowFunction_55", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_55 = binaryMapToRefFlowFunction_55.map();
      if (isDirty_binaryMapToRefFlowFunction_55) {
        mapRef2RefFlowFunction_57.inputUpdated(binaryMapToRefFlowFunction_55);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_57()) {
      auditInvocation(mapRef2RefFlowFunction_57, "mapRef2RefFlowFunction_57", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_57 = mapRef2RefFlowFunction_57.map();
      if (isDirty_mapRef2RefFlowFunction_57) {
        binaryMapToRefFlowFunction_62.inputUpdated(mapRef2RefFlowFunction_57);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_62()) {
      auditInvocation(
          binaryMapToRefFlowFunction_62, "binaryMapToRefFlowFunction_62", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_62 = binaryMapToRefFlowFunction_62.map();
      if (isDirty_binaryMapToRefFlowFunction_62) {
        mapRef2RefFlowFunction_64.inputUpdated(binaryMapToRefFlowFunction_62);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_64()) {
      auditInvocation(mapRef2RefFlowFunction_64, "mapRef2RefFlowFunction_64", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_64 = mapRef2RefFlowFunction_64.map();
      if (isDirty_mapRef2RefFlowFunction_64) {
        mapRef2RefFlowFunction_66.inputUpdated(mapRef2RefFlowFunction_64);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_66()) {
      auditInvocation(mapRef2RefFlowFunction_66, "mapRef2RefFlowFunction_66", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_66 = mapRef2RefFlowFunction_66.map();
      if (isDirty_mapRef2RefFlowFunction_66) {
        mapRef2RefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_66);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_68()) {
      auditInvocation(mapRef2RefFlowFunction_68, "mapRef2RefFlowFunction_68", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_68 = mapRef2RefFlowFunction_68.map();
      if (isDirty_mapRef2RefFlowFunction_68) {
        binaryMapToRefFlowFunction_83.input2Updated(mapRef2RefFlowFunction_68);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_72()) {
      auditInvocation(mapRef2RefFlowFunction_72, "mapRef2RefFlowFunction_72", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_72 = mapRef2RefFlowFunction_72.map();
      if (isDirty_mapRef2RefFlowFunction_72) {
        binaryMapToRefFlowFunction_77.inputUpdated(mapRef2RefFlowFunction_72);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_77()) {
      auditInvocation(
          binaryMapToRefFlowFunction_77, "binaryMapToRefFlowFunction_77", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_77 = binaryMapToRefFlowFunction_77.map();
      if (isDirty_binaryMapToRefFlowFunction_77) {
        mapRef2RefFlowFunction_79.inputUpdated(binaryMapToRefFlowFunction_77);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_79()) {
      auditInvocation(mapRef2RefFlowFunction_79, "mapRef2RefFlowFunction_79", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_79 = mapRef2RefFlowFunction_79.map();
      if (isDirty_mapRef2RefFlowFunction_79) {
        mapRef2RefFlowFunction_81.inputUpdated(mapRef2RefFlowFunction_79);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_81()) {
      auditInvocation(mapRef2RefFlowFunction_81, "mapRef2RefFlowFunction_81", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_81 = mapRef2RefFlowFunction_81.map();
      if (isDirty_mapRef2RefFlowFunction_81) {
        binaryMapToRefFlowFunction_83.inputUpdated(mapRef2RefFlowFunction_81);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_83()) {
      auditInvocation(
          binaryMapToRefFlowFunction_83, "binaryMapToRefFlowFunction_83", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_83 = binaryMapToRefFlowFunction_83.map();
      if (isDirty_binaryMapToRefFlowFunction_83) {
        mapRef2RefFlowFunction_85.inputUpdated(binaryMapToRefFlowFunction_83);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_85()) {
      auditInvocation(mapRef2RefFlowFunction_85, "mapRef2RefFlowFunction_85", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_85 = mapRef2RefFlowFunction_85.map();
      if (isDirty_mapRef2RefFlowFunction_85) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_85);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_87.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_49()) {
      auditInvocation(pushFlowFunction_49, "pushFlowFunction_49", "push", typedEvent);
      isDirty_pushFlowFunction_49 = pushFlowFunction_49.push();
      if (isDirty_pushFlowFunction_49) {
        binaryMapToRefFlowFunction_88.inputUpdated(pushFlowFunction_49);
      }
    }
    if (guardCheck_pushFlowFunction_87()) {
      auditInvocation(pushFlowFunction_87, "pushFlowFunction_87", "push", typedEvent);
      isDirty_pushFlowFunction_87 = pushFlowFunction_87.push();
      if (isDirty_pushFlowFunction_87) {
        binaryMapToRefFlowFunction_88.input2Updated(pushFlowFunction_87);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_88()) {
      auditInvocation(
          binaryMapToRefFlowFunction_88, "binaryMapToRefFlowFunction_88", "map", typedEvent);
      binaryMapToRefFlowFunction_88.map();
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
      mapRef2RefFlowFunction_21.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_34.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_12.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_14.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_16.publishTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_16.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_18.publishTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_18.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_40.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_64.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
    }
    if (guardCheck_mapRef2RefFlowFunction_21()) {
      auditInvocation(mapRef2RefFlowFunction_21, "mapRef2RefFlowFunction_21", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_21 = mapRef2RefFlowFunction_21.map();
      if (isDirty_mapRef2RefFlowFunction_21) {
        binaryMapToRefFlowFunction_27.input2Updated(mapRef2RefFlowFunction_21);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_34()) {
      auditInvocation(mapRef2RefFlowFunction_34, "mapRef2RefFlowFunction_34", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_34 = mapRef2RefFlowFunction_34.map();
      if (isDirty_mapRef2RefFlowFunction_34) {
        binaryMapToRefFlowFunction_38.input2Updated(mapRef2RefFlowFunction_34);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_23.inputUpdated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_23.input2Updated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_23()) {
      auditInvocation(
          binaryMapToRefFlowFunction_23, "binaryMapToRefFlowFunction_23", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_23 = binaryMapToRefFlowFunction_23.map();
      if (isDirty_binaryMapToRefFlowFunction_23) {
        mapRef2RefFlowFunction_25.inputUpdated(binaryMapToRefFlowFunction_23);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_70.inputUpdated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_18()) {
      auditInvocation(mapRef2RefFlowFunction_18, "mapRef2RefFlowFunction_18", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_18 = mapRef2RefFlowFunction_18.map();
      if (isDirty_mapRef2RefFlowFunction_18) {
        binaryMapToRefFlowFunction_70.input2Updated(mapRef2RefFlowFunction_18);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_70()) {
      auditInvocation(
          binaryMapToRefFlowFunction_70, "binaryMapToRefFlowFunction_70", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_70 = binaryMapToRefFlowFunction_70.map();
      if (isDirty_binaryMapToRefFlowFunction_70) {
        mapRef2RefFlowFunction_72.inputUpdated(binaryMapToRefFlowFunction_70);
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
        binaryMapToRefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_31);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_38()) {
      auditInvocation(
          binaryMapToRefFlowFunction_38, "binaryMapToRefFlowFunction_38", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_38 = binaryMapToRefFlowFunction_38.map();
      if (isDirty_binaryMapToRefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(binaryMapToRefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      auditInvocation(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        mapRef2RefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_42()) {
      auditInvocation(mapRef2RefFlowFunction_42, "mapRef2RefFlowFunction_42", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
      if (isDirty_mapRef2RefFlowFunction_42) {
        binaryMapToRefFlowFunction_44.input2Updated(mapRef2RefFlowFunction_42);
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
    if (guardCheck_mapRef2RefFlowFunction_64()) {
      auditInvocation(mapRef2RefFlowFunction_64, "mapRef2RefFlowFunction_64", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_64 = mapRef2RefFlowFunction_64.map();
      if (isDirty_mapRef2RefFlowFunction_64) {
        mapRef2RefFlowFunction_66.inputUpdated(mapRef2RefFlowFunction_64);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_66()) {
      auditInvocation(mapRef2RefFlowFunction_66, "mapRef2RefFlowFunction_66", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_66 = mapRef2RefFlowFunction_66.map();
      if (isDirty_mapRef2RefFlowFunction_66) {
        mapRef2RefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_66);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_68()) {
      auditInvocation(mapRef2RefFlowFunction_68, "mapRef2RefFlowFunction_68", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_68 = mapRef2RefFlowFunction_68.map();
      if (isDirty_mapRef2RefFlowFunction_68) {
        binaryMapToRefFlowFunction_83.input2Updated(mapRef2RefFlowFunction_68);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_72()) {
      auditInvocation(mapRef2RefFlowFunction_72, "mapRef2RefFlowFunction_72", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_72 = mapRef2RefFlowFunction_72.map();
      if (isDirty_mapRef2RefFlowFunction_72) {
        binaryMapToRefFlowFunction_77.inputUpdated(mapRef2RefFlowFunction_72);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_77()) {
      auditInvocation(
          binaryMapToRefFlowFunction_77, "binaryMapToRefFlowFunction_77", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_77 = binaryMapToRefFlowFunction_77.map();
      if (isDirty_binaryMapToRefFlowFunction_77) {
        mapRef2RefFlowFunction_79.inputUpdated(binaryMapToRefFlowFunction_77);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_79()) {
      auditInvocation(mapRef2RefFlowFunction_79, "mapRef2RefFlowFunction_79", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_79 = mapRef2RefFlowFunction_79.map();
      if (isDirty_mapRef2RefFlowFunction_79) {
        mapRef2RefFlowFunction_81.inputUpdated(mapRef2RefFlowFunction_79);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_81()) {
      auditInvocation(mapRef2RefFlowFunction_81, "mapRef2RefFlowFunction_81", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_81 = mapRef2RefFlowFunction_81.map();
      if (isDirty_mapRef2RefFlowFunction_81) {
        binaryMapToRefFlowFunction_83.inputUpdated(mapRef2RefFlowFunction_81);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_83()) {
      auditInvocation(
          binaryMapToRefFlowFunction_83, "binaryMapToRefFlowFunction_83", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_83 = binaryMapToRefFlowFunction_83.map();
      if (isDirty_binaryMapToRefFlowFunction_83) {
        mapRef2RefFlowFunction_85.inputUpdated(binaryMapToRefFlowFunction_83);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_85()) {
      auditInvocation(mapRef2RefFlowFunction_85, "mapRef2RefFlowFunction_85", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_85 = mapRef2RefFlowFunction_85.map();
      if (isDirty_mapRef2RefFlowFunction_85) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_85);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_87.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_49()) {
      auditInvocation(pushFlowFunction_49, "pushFlowFunction_49", "push", typedEvent);
      isDirty_pushFlowFunction_49 = pushFlowFunction_49.push();
      if (isDirty_pushFlowFunction_49) {
        binaryMapToRefFlowFunction_88.inputUpdated(pushFlowFunction_49);
      }
    }
    if (guardCheck_pushFlowFunction_87()) {
      auditInvocation(pushFlowFunction_87, "pushFlowFunction_87", "push", typedEvent);
      isDirty_pushFlowFunction_87 = pushFlowFunction_87.push();
      if (isDirty_pushFlowFunction_87) {
        binaryMapToRefFlowFunction_88.input2Updated(pushFlowFunction_87);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_88()) {
      auditInvocation(
          binaryMapToRefFlowFunction_88, "binaryMapToRefFlowFunction_88", "map", typedEvent);
      binaryMapToRefFlowFunction_88.map();
    }
  }

  private void handle_Signal_positionUpdate(Signal typedEvent) {
    auditInvocation(
        handlerSignal_positionUpdate, "handlerSignal_positionUpdate", "onEvent", typedEvent);
    isDirty_handlerSignal_positionUpdate = handlerSignal_positionUpdate.onEvent(typedEvent);
    if (isDirty_handlerSignal_positionUpdate) {
      mapRef2RefFlowFunction_21.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_34.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_25.publishTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_31.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_31.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_36.publishTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_42.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_42.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_46.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_57.publishTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_68.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_68.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_72.publishTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_81.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_81.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_85.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
    }
    if (guardCheck_mapRef2RefFlowFunction_21()) {
      auditInvocation(mapRef2RefFlowFunction_21, "mapRef2RefFlowFunction_21", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_21 = mapRef2RefFlowFunction_21.map();
      if (isDirty_mapRef2RefFlowFunction_21) {
        binaryMapToRefFlowFunction_27.input2Updated(mapRef2RefFlowFunction_21);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_34()) {
      auditInvocation(mapRef2RefFlowFunction_34, "mapRef2RefFlowFunction_34", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_34 = mapRef2RefFlowFunction_34.map();
      if (isDirty_mapRef2RefFlowFunction_34) {
        binaryMapToRefFlowFunction_38.input2Updated(mapRef2RefFlowFunction_34);
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
        binaryMapToRefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_31);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_36()) {
      auditInvocation(mapRef2RefFlowFunction_36, "mapRef2RefFlowFunction_36", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_36 = mapRef2RefFlowFunction_36.map();
      if (isDirty_mapRef2RefFlowFunction_36) {
        binaryMapToRefFlowFunction_38.inputUpdated(mapRef2RefFlowFunction_36);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_38()) {
      auditInvocation(
          binaryMapToRefFlowFunction_38, "binaryMapToRefFlowFunction_38", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_38 = binaryMapToRefFlowFunction_38.map();
      if (isDirty_binaryMapToRefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(binaryMapToRefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      auditInvocation(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        mapRef2RefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_42()) {
      auditInvocation(mapRef2RefFlowFunction_42, "mapRef2RefFlowFunction_42", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
      if (isDirty_mapRef2RefFlowFunction_42) {
        binaryMapToRefFlowFunction_44.input2Updated(mapRef2RefFlowFunction_42);
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
    if (guardCheck_mapRef2RefFlowFunction_57()) {
      auditInvocation(mapRef2RefFlowFunction_57, "mapRef2RefFlowFunction_57", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_57 = mapRef2RefFlowFunction_57.map();
      if (isDirty_mapRef2RefFlowFunction_57) {
        binaryMapToRefFlowFunction_62.inputUpdated(mapRef2RefFlowFunction_57);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_62()) {
      auditInvocation(
          binaryMapToRefFlowFunction_62, "binaryMapToRefFlowFunction_62", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_62 = binaryMapToRefFlowFunction_62.map();
      if (isDirty_binaryMapToRefFlowFunction_62) {
        mapRef2RefFlowFunction_64.inputUpdated(binaryMapToRefFlowFunction_62);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_64()) {
      auditInvocation(mapRef2RefFlowFunction_64, "mapRef2RefFlowFunction_64", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_64 = mapRef2RefFlowFunction_64.map();
      if (isDirty_mapRef2RefFlowFunction_64) {
        mapRef2RefFlowFunction_66.inputUpdated(mapRef2RefFlowFunction_64);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_66()) {
      auditInvocation(mapRef2RefFlowFunction_66, "mapRef2RefFlowFunction_66", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_66 = mapRef2RefFlowFunction_66.map();
      if (isDirty_mapRef2RefFlowFunction_66) {
        mapRef2RefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_66);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_68()) {
      auditInvocation(mapRef2RefFlowFunction_68, "mapRef2RefFlowFunction_68", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_68 = mapRef2RefFlowFunction_68.map();
      if (isDirty_mapRef2RefFlowFunction_68) {
        binaryMapToRefFlowFunction_83.input2Updated(mapRef2RefFlowFunction_68);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_72()) {
      auditInvocation(mapRef2RefFlowFunction_72, "mapRef2RefFlowFunction_72", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_72 = mapRef2RefFlowFunction_72.map();
      if (isDirty_mapRef2RefFlowFunction_72) {
        binaryMapToRefFlowFunction_77.inputUpdated(mapRef2RefFlowFunction_72);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_77()) {
      auditInvocation(
          binaryMapToRefFlowFunction_77, "binaryMapToRefFlowFunction_77", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_77 = binaryMapToRefFlowFunction_77.map();
      if (isDirty_binaryMapToRefFlowFunction_77) {
        mapRef2RefFlowFunction_79.inputUpdated(binaryMapToRefFlowFunction_77);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_79()) {
      auditInvocation(mapRef2RefFlowFunction_79, "mapRef2RefFlowFunction_79", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_79 = mapRef2RefFlowFunction_79.map();
      if (isDirty_mapRef2RefFlowFunction_79) {
        mapRef2RefFlowFunction_81.inputUpdated(mapRef2RefFlowFunction_79);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_81()) {
      auditInvocation(mapRef2RefFlowFunction_81, "mapRef2RefFlowFunction_81", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_81 = mapRef2RefFlowFunction_81.map();
      if (isDirty_mapRef2RefFlowFunction_81) {
        binaryMapToRefFlowFunction_83.inputUpdated(mapRef2RefFlowFunction_81);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_83()) {
      auditInvocation(
          binaryMapToRefFlowFunction_83, "binaryMapToRefFlowFunction_83", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_83 = binaryMapToRefFlowFunction_83.map();
      if (isDirty_binaryMapToRefFlowFunction_83) {
        mapRef2RefFlowFunction_85.inputUpdated(binaryMapToRefFlowFunction_83);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_85()) {
      auditInvocation(mapRef2RefFlowFunction_85, "mapRef2RefFlowFunction_85", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_85 = mapRef2RefFlowFunction_85.map();
      if (isDirty_mapRef2RefFlowFunction_85) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_85);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_87.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_49()) {
      auditInvocation(pushFlowFunction_49, "pushFlowFunction_49", "push", typedEvent);
      isDirty_pushFlowFunction_49 = pushFlowFunction_49.push();
      if (isDirty_pushFlowFunction_49) {
        binaryMapToRefFlowFunction_88.inputUpdated(pushFlowFunction_49);
      }
    }
    if (guardCheck_pushFlowFunction_87()) {
      auditInvocation(pushFlowFunction_87, "pushFlowFunction_87", "push", typedEvent);
      isDirty_pushFlowFunction_87 = pushFlowFunction_87.push();
      if (isDirty_pushFlowFunction_87) {
        binaryMapToRefFlowFunction_88.input2Updated(pushFlowFunction_87);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_88()) {
      auditInvocation(
          binaryMapToRefFlowFunction_88, "binaryMapToRefFlowFunction_88", "map", typedEvent);
      binaryMapToRefFlowFunction_88.map();
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
    auditInvocation(namedFeedTableNode_89, "namedFeedTableNode_89", "tableUpdate", typedEvent);
    isDirty_namedFeedTableNode_89 = namedFeedTableNode_89.tableUpdate(typedEvent);
    if (guardCheck_mapRef2RefFlowFunction_7()) {
      auditInvocation(mapRef2RefFlowFunction_7, "mapRef2RefFlowFunction_7", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_7 = mapRef2RefFlowFunction_7.map();
      if (isDirty_mapRef2RefFlowFunction_7) {
        filterFlowFunction_9.inputUpdated(mapRef2RefFlowFunction_7);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_6()) {
      auditInvocation(mapRef2RefFlowFunction_6, "mapRef2RefFlowFunction_6", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_6 = mapRef2RefFlowFunction_6.map();
      if (isDirty_mapRef2RefFlowFunction_6) {
        mergeFlowFunction_10.inputStreamUpdated(mapRef2RefFlowFunction_6);
      }
    }
    if (guardCheck_filterFlowFunction_9()) {
      auditInvocation(filterFlowFunction_9, "filterFlowFunction_9", "filter", typedEvent);
      isDirty_filterFlowFunction_9 = filterFlowFunction_9.filter();
      if (isDirty_filterFlowFunction_9) {
        mergeFlowFunction_10.inputStreamUpdated(filterFlowFunction_9);
      }
    }
    if (guardCheck_mergeFlowFunction_10()) {
      auditInvocation(mergeFlowFunction_10, "mergeFlowFunction_10", "publishMerge", typedEvent);
      isDirty_mergeFlowFunction_10 = mergeFlowFunction_10.publishMerge();
      if (isDirty_mergeFlowFunction_10) {
        mapRef2RefFlowFunction_12.inputUpdated(mergeFlowFunction_10);
        mapRef2RefFlowFunction_14.inputUpdated(mergeFlowFunction_10);
        mapRef2RefFlowFunction_16.inputUpdated(mergeFlowFunction_10);
        mapRef2RefFlowFunction_18.inputUpdated(mergeFlowFunction_10);
        mapRef2RefFlowFunction_36.inputUpdated(mergeFlowFunction_10);
        mapRef2RefFlowFunction_51.inputUpdated(mergeFlowFunction_10);
        mapRef2RefFlowFunction_53.inputUpdated(mergeFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_23.inputUpdated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_23.input2Updated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_23()) {
      auditInvocation(
          binaryMapToRefFlowFunction_23, "binaryMapToRefFlowFunction_23", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_23 = binaryMapToRefFlowFunction_23.map();
      if (isDirty_binaryMapToRefFlowFunction_23) {
        mapRef2RefFlowFunction_25.inputUpdated(binaryMapToRefFlowFunction_23);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_70.inputUpdated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_18()) {
      auditInvocation(mapRef2RefFlowFunction_18, "mapRef2RefFlowFunction_18", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_18 = mapRef2RefFlowFunction_18.map();
      if (isDirty_mapRef2RefFlowFunction_18) {
        binaryMapToRefFlowFunction_70.input2Updated(mapRef2RefFlowFunction_18);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_70()) {
      auditInvocation(
          binaryMapToRefFlowFunction_70, "binaryMapToRefFlowFunction_70", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_70 = binaryMapToRefFlowFunction_70.map();
      if (isDirty_binaryMapToRefFlowFunction_70) {
        mapRef2RefFlowFunction_72.inputUpdated(binaryMapToRefFlowFunction_70);
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
        binaryMapToRefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_31);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_36()) {
      auditInvocation(mapRef2RefFlowFunction_36, "mapRef2RefFlowFunction_36", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_36 = mapRef2RefFlowFunction_36.map();
      if (isDirty_mapRef2RefFlowFunction_36) {
        binaryMapToRefFlowFunction_38.inputUpdated(mapRef2RefFlowFunction_36);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_38()) {
      auditInvocation(
          binaryMapToRefFlowFunction_38, "binaryMapToRefFlowFunction_38", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_38 = binaryMapToRefFlowFunction_38.map();
      if (isDirty_binaryMapToRefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(binaryMapToRefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      auditInvocation(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        mapRef2RefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_42()) {
      auditInvocation(mapRef2RefFlowFunction_42, "mapRef2RefFlowFunction_42", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
      if (isDirty_mapRef2RefFlowFunction_42) {
        binaryMapToRefFlowFunction_44.input2Updated(mapRef2RefFlowFunction_42);
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
    if (guardCheck_mapRef2RefFlowFunction_51()) {
      auditInvocation(mapRef2RefFlowFunction_51, "mapRef2RefFlowFunction_51", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_51 = mapRef2RefFlowFunction_51.map();
      if (isDirty_mapRef2RefFlowFunction_51) {
        binaryMapToRefFlowFunction_55.inputUpdated(mapRef2RefFlowFunction_51);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_53()) {
      auditInvocation(mapRef2RefFlowFunction_53, "mapRef2RefFlowFunction_53", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_53 = mapRef2RefFlowFunction_53.map();
      if (isDirty_mapRef2RefFlowFunction_53) {
        binaryMapToRefFlowFunction_55.input2Updated(mapRef2RefFlowFunction_53);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_55()) {
      auditInvocation(
          binaryMapToRefFlowFunction_55, "binaryMapToRefFlowFunction_55", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_55 = binaryMapToRefFlowFunction_55.map();
      if (isDirty_binaryMapToRefFlowFunction_55) {
        mapRef2RefFlowFunction_57.inputUpdated(binaryMapToRefFlowFunction_55);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_57()) {
      auditInvocation(mapRef2RefFlowFunction_57, "mapRef2RefFlowFunction_57", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_57 = mapRef2RefFlowFunction_57.map();
      if (isDirty_mapRef2RefFlowFunction_57) {
        binaryMapToRefFlowFunction_62.inputUpdated(mapRef2RefFlowFunction_57);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_62()) {
      auditInvocation(
          binaryMapToRefFlowFunction_62, "binaryMapToRefFlowFunction_62", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_62 = binaryMapToRefFlowFunction_62.map();
      if (isDirty_binaryMapToRefFlowFunction_62) {
        mapRef2RefFlowFunction_64.inputUpdated(binaryMapToRefFlowFunction_62);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_64()) {
      auditInvocation(mapRef2RefFlowFunction_64, "mapRef2RefFlowFunction_64", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_64 = mapRef2RefFlowFunction_64.map();
      if (isDirty_mapRef2RefFlowFunction_64) {
        mapRef2RefFlowFunction_66.inputUpdated(mapRef2RefFlowFunction_64);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_66()) {
      auditInvocation(mapRef2RefFlowFunction_66, "mapRef2RefFlowFunction_66", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_66 = mapRef2RefFlowFunction_66.map();
      if (isDirty_mapRef2RefFlowFunction_66) {
        mapRef2RefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_66);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_68()) {
      auditInvocation(mapRef2RefFlowFunction_68, "mapRef2RefFlowFunction_68", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_68 = mapRef2RefFlowFunction_68.map();
      if (isDirty_mapRef2RefFlowFunction_68) {
        binaryMapToRefFlowFunction_83.input2Updated(mapRef2RefFlowFunction_68);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_72()) {
      auditInvocation(mapRef2RefFlowFunction_72, "mapRef2RefFlowFunction_72", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_72 = mapRef2RefFlowFunction_72.map();
      if (isDirty_mapRef2RefFlowFunction_72) {
        binaryMapToRefFlowFunction_77.inputUpdated(mapRef2RefFlowFunction_72);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_77()) {
      auditInvocation(
          binaryMapToRefFlowFunction_77, "binaryMapToRefFlowFunction_77", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_77 = binaryMapToRefFlowFunction_77.map();
      if (isDirty_binaryMapToRefFlowFunction_77) {
        mapRef2RefFlowFunction_79.inputUpdated(binaryMapToRefFlowFunction_77);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_79()) {
      auditInvocation(mapRef2RefFlowFunction_79, "mapRef2RefFlowFunction_79", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_79 = mapRef2RefFlowFunction_79.map();
      if (isDirty_mapRef2RefFlowFunction_79) {
        mapRef2RefFlowFunction_81.inputUpdated(mapRef2RefFlowFunction_79);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_81()) {
      auditInvocation(mapRef2RefFlowFunction_81, "mapRef2RefFlowFunction_81", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_81 = mapRef2RefFlowFunction_81.map();
      if (isDirty_mapRef2RefFlowFunction_81) {
        binaryMapToRefFlowFunction_83.inputUpdated(mapRef2RefFlowFunction_81);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_83()) {
      auditInvocation(
          binaryMapToRefFlowFunction_83, "binaryMapToRefFlowFunction_83", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_83 = binaryMapToRefFlowFunction_83.map();
      if (isDirty_binaryMapToRefFlowFunction_83) {
        mapRef2RefFlowFunction_85.inputUpdated(binaryMapToRefFlowFunction_83);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_85()) {
      auditInvocation(mapRef2RefFlowFunction_85, "mapRef2RefFlowFunction_85", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_85 = mapRef2RefFlowFunction_85.map();
      if (isDirty_mapRef2RefFlowFunction_85) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_85);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_87.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_49()) {
      auditInvocation(pushFlowFunction_49, "pushFlowFunction_49", "push", typedEvent);
      isDirty_pushFlowFunction_49 = pushFlowFunction_49.push();
      if (isDirty_pushFlowFunction_49) {
        binaryMapToRefFlowFunction_88.inputUpdated(pushFlowFunction_49);
      }
    }
    if (guardCheck_pushFlowFunction_87()) {
      auditInvocation(pushFlowFunction_87, "pushFlowFunction_87", "push", typedEvent);
      isDirty_pushFlowFunction_87 = pushFlowFunction_87.push();
      if (isDirty_pushFlowFunction_87) {
        binaryMapToRefFlowFunction_88.input2Updated(pushFlowFunction_87);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_88()) {
      auditInvocation(
          binaryMapToRefFlowFunction_88, "binaryMapToRefFlowFunction_88", "map", typedEvent);
      binaryMapToRefFlowFunction_88.map();
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
    if (guardCheck_mapRef2RefFlowFunction_7()) {
      auditInvocation(mapRef2RefFlowFunction_7, "mapRef2RefFlowFunction_7", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_7 = mapRef2RefFlowFunction_7.map();
      if (isDirty_mapRef2RefFlowFunction_7) {
        filterFlowFunction_9.inputUpdated(mapRef2RefFlowFunction_7);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_6()) {
      auditInvocation(mapRef2RefFlowFunction_6, "mapRef2RefFlowFunction_6", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_6 = mapRef2RefFlowFunction_6.map();
      if (isDirty_mapRef2RefFlowFunction_6) {
        mergeFlowFunction_10.inputStreamUpdated(mapRef2RefFlowFunction_6);
      }
    }
    if (guardCheck_filterFlowFunction_9()) {
      auditInvocation(filterFlowFunction_9, "filterFlowFunction_9", "filter", typedEvent);
      isDirty_filterFlowFunction_9 = filterFlowFunction_9.filter();
      if (isDirty_filterFlowFunction_9) {
        mergeFlowFunction_10.inputStreamUpdated(filterFlowFunction_9);
      }
    }
    if (guardCheck_mergeFlowFunction_10()) {
      auditInvocation(mergeFlowFunction_10, "mergeFlowFunction_10", "publishMerge", typedEvent);
      isDirty_mergeFlowFunction_10 = mergeFlowFunction_10.publishMerge();
      if (isDirty_mergeFlowFunction_10) {
        mapRef2RefFlowFunction_12.inputUpdated(mergeFlowFunction_10);
        mapRef2RefFlowFunction_14.inputUpdated(mergeFlowFunction_10);
        mapRef2RefFlowFunction_16.inputUpdated(mergeFlowFunction_10);
        mapRef2RefFlowFunction_18.inputUpdated(mergeFlowFunction_10);
        mapRef2RefFlowFunction_36.inputUpdated(mergeFlowFunction_10);
        mapRef2RefFlowFunction_51.inputUpdated(mergeFlowFunction_10);
        mapRef2RefFlowFunction_53.inputUpdated(mergeFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_23.inputUpdated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_23.input2Updated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_23()) {
      auditInvocation(
          binaryMapToRefFlowFunction_23, "binaryMapToRefFlowFunction_23", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_23 = binaryMapToRefFlowFunction_23.map();
      if (isDirty_binaryMapToRefFlowFunction_23) {
        mapRef2RefFlowFunction_25.inputUpdated(binaryMapToRefFlowFunction_23);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_70.inputUpdated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_18()) {
      auditInvocation(mapRef2RefFlowFunction_18, "mapRef2RefFlowFunction_18", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_18 = mapRef2RefFlowFunction_18.map();
      if (isDirty_mapRef2RefFlowFunction_18) {
        binaryMapToRefFlowFunction_70.input2Updated(mapRef2RefFlowFunction_18);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_70()) {
      auditInvocation(
          binaryMapToRefFlowFunction_70, "binaryMapToRefFlowFunction_70", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_70 = binaryMapToRefFlowFunction_70.map();
      if (isDirty_binaryMapToRefFlowFunction_70) {
        mapRef2RefFlowFunction_72.inputUpdated(binaryMapToRefFlowFunction_70);
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
        binaryMapToRefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_31);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_36()) {
      auditInvocation(mapRef2RefFlowFunction_36, "mapRef2RefFlowFunction_36", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_36 = mapRef2RefFlowFunction_36.map();
      if (isDirty_mapRef2RefFlowFunction_36) {
        binaryMapToRefFlowFunction_38.inputUpdated(mapRef2RefFlowFunction_36);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_38()) {
      auditInvocation(
          binaryMapToRefFlowFunction_38, "binaryMapToRefFlowFunction_38", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_38 = binaryMapToRefFlowFunction_38.map();
      if (isDirty_binaryMapToRefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(binaryMapToRefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      auditInvocation(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        mapRef2RefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_42()) {
      auditInvocation(mapRef2RefFlowFunction_42, "mapRef2RefFlowFunction_42", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
      if (isDirty_mapRef2RefFlowFunction_42) {
        binaryMapToRefFlowFunction_44.input2Updated(mapRef2RefFlowFunction_42);
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
    if (guardCheck_mapRef2RefFlowFunction_51()) {
      auditInvocation(mapRef2RefFlowFunction_51, "mapRef2RefFlowFunction_51", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_51 = mapRef2RefFlowFunction_51.map();
      if (isDirty_mapRef2RefFlowFunction_51) {
        binaryMapToRefFlowFunction_55.inputUpdated(mapRef2RefFlowFunction_51);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_53()) {
      auditInvocation(mapRef2RefFlowFunction_53, "mapRef2RefFlowFunction_53", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_53 = mapRef2RefFlowFunction_53.map();
      if (isDirty_mapRef2RefFlowFunction_53) {
        binaryMapToRefFlowFunction_55.input2Updated(mapRef2RefFlowFunction_53);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_55()) {
      auditInvocation(
          binaryMapToRefFlowFunction_55, "binaryMapToRefFlowFunction_55", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_55 = binaryMapToRefFlowFunction_55.map();
      if (isDirty_binaryMapToRefFlowFunction_55) {
        mapRef2RefFlowFunction_57.inputUpdated(binaryMapToRefFlowFunction_55);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_57()) {
      auditInvocation(mapRef2RefFlowFunction_57, "mapRef2RefFlowFunction_57", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_57 = mapRef2RefFlowFunction_57.map();
      if (isDirty_mapRef2RefFlowFunction_57) {
        binaryMapToRefFlowFunction_62.inputUpdated(mapRef2RefFlowFunction_57);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_62()) {
      auditInvocation(
          binaryMapToRefFlowFunction_62, "binaryMapToRefFlowFunction_62", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_62 = binaryMapToRefFlowFunction_62.map();
      if (isDirty_binaryMapToRefFlowFunction_62) {
        mapRef2RefFlowFunction_64.inputUpdated(binaryMapToRefFlowFunction_62);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_64()) {
      auditInvocation(mapRef2RefFlowFunction_64, "mapRef2RefFlowFunction_64", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_64 = mapRef2RefFlowFunction_64.map();
      if (isDirty_mapRef2RefFlowFunction_64) {
        mapRef2RefFlowFunction_66.inputUpdated(mapRef2RefFlowFunction_64);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_66()) {
      auditInvocation(mapRef2RefFlowFunction_66, "mapRef2RefFlowFunction_66", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_66 = mapRef2RefFlowFunction_66.map();
      if (isDirty_mapRef2RefFlowFunction_66) {
        mapRef2RefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_66);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_68()) {
      auditInvocation(mapRef2RefFlowFunction_68, "mapRef2RefFlowFunction_68", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_68 = mapRef2RefFlowFunction_68.map();
      if (isDirty_mapRef2RefFlowFunction_68) {
        binaryMapToRefFlowFunction_83.input2Updated(mapRef2RefFlowFunction_68);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_72()) {
      auditInvocation(mapRef2RefFlowFunction_72, "mapRef2RefFlowFunction_72", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_72 = mapRef2RefFlowFunction_72.map();
      if (isDirty_mapRef2RefFlowFunction_72) {
        binaryMapToRefFlowFunction_77.inputUpdated(mapRef2RefFlowFunction_72);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_77()) {
      auditInvocation(
          binaryMapToRefFlowFunction_77, "binaryMapToRefFlowFunction_77", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_77 = binaryMapToRefFlowFunction_77.map();
      if (isDirty_binaryMapToRefFlowFunction_77) {
        mapRef2RefFlowFunction_79.inputUpdated(binaryMapToRefFlowFunction_77);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_79()) {
      auditInvocation(mapRef2RefFlowFunction_79, "mapRef2RefFlowFunction_79", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_79 = mapRef2RefFlowFunction_79.map();
      if (isDirty_mapRef2RefFlowFunction_79) {
        mapRef2RefFlowFunction_81.inputUpdated(mapRef2RefFlowFunction_79);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_81()) {
      auditInvocation(mapRef2RefFlowFunction_81, "mapRef2RefFlowFunction_81", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_81 = mapRef2RefFlowFunction_81.map();
      if (isDirty_mapRef2RefFlowFunction_81) {
        binaryMapToRefFlowFunction_83.inputUpdated(mapRef2RefFlowFunction_81);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_83()) {
      auditInvocation(
          binaryMapToRefFlowFunction_83, "binaryMapToRefFlowFunction_83", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_83 = binaryMapToRefFlowFunction_83.map();
      if (isDirty_binaryMapToRefFlowFunction_83) {
        mapRef2RefFlowFunction_85.inputUpdated(binaryMapToRefFlowFunction_83);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_85()) {
      auditInvocation(mapRef2RefFlowFunction_85, "mapRef2RefFlowFunction_85", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_85 = mapRef2RefFlowFunction_85.map();
      if (isDirty_mapRef2RefFlowFunction_85) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_85);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_87.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_49()) {
      auditInvocation(pushFlowFunction_49, "pushFlowFunction_49", "push", typedEvent);
      isDirty_pushFlowFunction_49 = pushFlowFunction_49.push();
      if (isDirty_pushFlowFunction_49) {
        binaryMapToRefFlowFunction_88.inputUpdated(pushFlowFunction_49);
      }
    }
    if (guardCheck_pushFlowFunction_87()) {
      auditInvocation(pushFlowFunction_87, "pushFlowFunction_87", "push", typedEvent);
      isDirty_pushFlowFunction_87 = pushFlowFunction_87.push();
      if (isDirty_pushFlowFunction_87) {
        binaryMapToRefFlowFunction_88.input2Updated(pushFlowFunction_87);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_88()) {
      auditInvocation(
          binaryMapToRefFlowFunction_88, "binaryMapToRefFlowFunction_88", "map", typedEvent);
      binaryMapToRefFlowFunction_88.map();
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
    if (guardCheck_mapRef2RefFlowFunction_7()) {
      auditInvocation(mapRef2RefFlowFunction_7, "mapRef2RefFlowFunction_7", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_7 = mapRef2RefFlowFunction_7.map();
      if (isDirty_mapRef2RefFlowFunction_7) {
        filterFlowFunction_9.inputUpdated(mapRef2RefFlowFunction_7);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_6()) {
      auditInvocation(mapRef2RefFlowFunction_6, "mapRef2RefFlowFunction_6", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_6 = mapRef2RefFlowFunction_6.map();
      if (isDirty_mapRef2RefFlowFunction_6) {
        mergeFlowFunction_10.inputStreamUpdated(mapRef2RefFlowFunction_6);
      }
    }
    if (guardCheck_filterFlowFunction_9()) {
      auditInvocation(filterFlowFunction_9, "filterFlowFunction_9", "filter", typedEvent);
      isDirty_filterFlowFunction_9 = filterFlowFunction_9.filter();
      if (isDirty_filterFlowFunction_9) {
        mergeFlowFunction_10.inputStreamUpdated(filterFlowFunction_9);
      }
    }
    if (guardCheck_mergeFlowFunction_10()) {
      auditInvocation(mergeFlowFunction_10, "mergeFlowFunction_10", "publishMerge", typedEvent);
      isDirty_mergeFlowFunction_10 = mergeFlowFunction_10.publishMerge();
      if (isDirty_mergeFlowFunction_10) {
        mapRef2RefFlowFunction_12.inputUpdated(mergeFlowFunction_10);
        mapRef2RefFlowFunction_14.inputUpdated(mergeFlowFunction_10);
        mapRef2RefFlowFunction_16.inputUpdated(mergeFlowFunction_10);
        mapRef2RefFlowFunction_18.inputUpdated(mergeFlowFunction_10);
        mapRef2RefFlowFunction_36.inputUpdated(mergeFlowFunction_10);
        mapRef2RefFlowFunction_51.inputUpdated(mergeFlowFunction_10);
        mapRef2RefFlowFunction_53.inputUpdated(mergeFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_23.inputUpdated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_23.input2Updated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_23()) {
      auditInvocation(
          binaryMapToRefFlowFunction_23, "binaryMapToRefFlowFunction_23", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_23 = binaryMapToRefFlowFunction_23.map();
      if (isDirty_binaryMapToRefFlowFunction_23) {
        mapRef2RefFlowFunction_25.inputUpdated(binaryMapToRefFlowFunction_23);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_70.inputUpdated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_18()) {
      auditInvocation(mapRef2RefFlowFunction_18, "mapRef2RefFlowFunction_18", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_18 = mapRef2RefFlowFunction_18.map();
      if (isDirty_mapRef2RefFlowFunction_18) {
        binaryMapToRefFlowFunction_70.input2Updated(mapRef2RefFlowFunction_18);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_70()) {
      auditInvocation(
          binaryMapToRefFlowFunction_70, "binaryMapToRefFlowFunction_70", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_70 = binaryMapToRefFlowFunction_70.map();
      if (isDirty_binaryMapToRefFlowFunction_70) {
        mapRef2RefFlowFunction_72.inputUpdated(binaryMapToRefFlowFunction_70);
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
        binaryMapToRefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_31);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_36()) {
      auditInvocation(mapRef2RefFlowFunction_36, "mapRef2RefFlowFunction_36", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_36 = mapRef2RefFlowFunction_36.map();
      if (isDirty_mapRef2RefFlowFunction_36) {
        binaryMapToRefFlowFunction_38.inputUpdated(mapRef2RefFlowFunction_36);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_38()) {
      auditInvocation(
          binaryMapToRefFlowFunction_38, "binaryMapToRefFlowFunction_38", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_38 = binaryMapToRefFlowFunction_38.map();
      if (isDirty_binaryMapToRefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(binaryMapToRefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      auditInvocation(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        mapRef2RefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_42()) {
      auditInvocation(mapRef2RefFlowFunction_42, "mapRef2RefFlowFunction_42", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
      if (isDirty_mapRef2RefFlowFunction_42) {
        binaryMapToRefFlowFunction_44.input2Updated(mapRef2RefFlowFunction_42);
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
    if (guardCheck_mapRef2RefFlowFunction_51()) {
      auditInvocation(mapRef2RefFlowFunction_51, "mapRef2RefFlowFunction_51", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_51 = mapRef2RefFlowFunction_51.map();
      if (isDirty_mapRef2RefFlowFunction_51) {
        binaryMapToRefFlowFunction_55.inputUpdated(mapRef2RefFlowFunction_51);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_53()) {
      auditInvocation(mapRef2RefFlowFunction_53, "mapRef2RefFlowFunction_53", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_53 = mapRef2RefFlowFunction_53.map();
      if (isDirty_mapRef2RefFlowFunction_53) {
        binaryMapToRefFlowFunction_55.input2Updated(mapRef2RefFlowFunction_53);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_55()) {
      auditInvocation(
          binaryMapToRefFlowFunction_55, "binaryMapToRefFlowFunction_55", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_55 = binaryMapToRefFlowFunction_55.map();
      if (isDirty_binaryMapToRefFlowFunction_55) {
        mapRef2RefFlowFunction_57.inputUpdated(binaryMapToRefFlowFunction_55);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_57()) {
      auditInvocation(mapRef2RefFlowFunction_57, "mapRef2RefFlowFunction_57", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_57 = mapRef2RefFlowFunction_57.map();
      if (isDirty_mapRef2RefFlowFunction_57) {
        binaryMapToRefFlowFunction_62.inputUpdated(mapRef2RefFlowFunction_57);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_62()) {
      auditInvocation(
          binaryMapToRefFlowFunction_62, "binaryMapToRefFlowFunction_62", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_62 = binaryMapToRefFlowFunction_62.map();
      if (isDirty_binaryMapToRefFlowFunction_62) {
        mapRef2RefFlowFunction_64.inputUpdated(binaryMapToRefFlowFunction_62);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_64()) {
      auditInvocation(mapRef2RefFlowFunction_64, "mapRef2RefFlowFunction_64", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_64 = mapRef2RefFlowFunction_64.map();
      if (isDirty_mapRef2RefFlowFunction_64) {
        mapRef2RefFlowFunction_66.inputUpdated(mapRef2RefFlowFunction_64);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_66()) {
      auditInvocation(mapRef2RefFlowFunction_66, "mapRef2RefFlowFunction_66", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_66 = mapRef2RefFlowFunction_66.map();
      if (isDirty_mapRef2RefFlowFunction_66) {
        mapRef2RefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_66);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_68()) {
      auditInvocation(mapRef2RefFlowFunction_68, "mapRef2RefFlowFunction_68", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_68 = mapRef2RefFlowFunction_68.map();
      if (isDirty_mapRef2RefFlowFunction_68) {
        binaryMapToRefFlowFunction_83.input2Updated(mapRef2RefFlowFunction_68);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_72()) {
      auditInvocation(mapRef2RefFlowFunction_72, "mapRef2RefFlowFunction_72", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_72 = mapRef2RefFlowFunction_72.map();
      if (isDirty_mapRef2RefFlowFunction_72) {
        binaryMapToRefFlowFunction_77.inputUpdated(mapRef2RefFlowFunction_72);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_77()) {
      auditInvocation(
          binaryMapToRefFlowFunction_77, "binaryMapToRefFlowFunction_77", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_77 = binaryMapToRefFlowFunction_77.map();
      if (isDirty_binaryMapToRefFlowFunction_77) {
        mapRef2RefFlowFunction_79.inputUpdated(binaryMapToRefFlowFunction_77);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_79()) {
      auditInvocation(mapRef2RefFlowFunction_79, "mapRef2RefFlowFunction_79", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_79 = mapRef2RefFlowFunction_79.map();
      if (isDirty_mapRef2RefFlowFunction_79) {
        mapRef2RefFlowFunction_81.inputUpdated(mapRef2RefFlowFunction_79);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_81()) {
      auditInvocation(mapRef2RefFlowFunction_81, "mapRef2RefFlowFunction_81", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_81 = mapRef2RefFlowFunction_81.map();
      if (isDirty_mapRef2RefFlowFunction_81) {
        binaryMapToRefFlowFunction_83.inputUpdated(mapRef2RefFlowFunction_81);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_83()) {
      auditInvocation(
          binaryMapToRefFlowFunction_83, "binaryMapToRefFlowFunction_83", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_83 = binaryMapToRefFlowFunction_83.map();
      if (isDirty_binaryMapToRefFlowFunction_83) {
        mapRef2RefFlowFunction_85.inputUpdated(binaryMapToRefFlowFunction_83);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_85()) {
      auditInvocation(mapRef2RefFlowFunction_85, "mapRef2RefFlowFunction_85", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_85 = mapRef2RefFlowFunction_85.map();
      if (isDirty_mapRef2RefFlowFunction_85) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_85);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_87.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_49()) {
      auditInvocation(pushFlowFunction_49, "pushFlowFunction_49", "push", typedEvent);
      isDirty_pushFlowFunction_49 = pushFlowFunction_49.push();
      if (isDirty_pushFlowFunction_49) {
        binaryMapToRefFlowFunction_88.inputUpdated(pushFlowFunction_49);
      }
    }
    if (guardCheck_pushFlowFunction_87()) {
      auditInvocation(pushFlowFunction_87, "pushFlowFunction_87", "push", typedEvent);
      isDirty_pushFlowFunction_87 = pushFlowFunction_87.push();
      if (isDirty_pushFlowFunction_87) {
        binaryMapToRefFlowFunction_88.input2Updated(pushFlowFunction_87);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_88()) {
      auditInvocation(
          binaryMapToRefFlowFunction_88, "binaryMapToRefFlowFunction_88", "map", typedEvent);
      binaryMapToRefFlowFunction_88.map();
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
    auditor.nodeRegistered(callBackNode_90, "callBackNode_90");
    auditor.nodeRegistered(callBackNode_121, "callBackNode_121");
    auditor.nodeRegistered(callBackNode_201, "callBackNode_201");
    auditor.nodeRegistered(callbackDispatcher, "callbackDispatcher");
    auditor.nodeRegistered(callBackTriggerEvent_1, "callBackTriggerEvent_1");
    auditor.nodeRegistered(callBackTriggerEvent_2, "callBackTriggerEvent_2");
    auditor.nodeRegistered(callBackTriggerEvent_0, "callBackTriggerEvent_0");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_23, "binaryMapToRefFlowFunction_23");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_27, "binaryMapToRefFlowFunction_27");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_38, "binaryMapToRefFlowFunction_38");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_44, "binaryMapToRefFlowFunction_44");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_55, "binaryMapToRefFlowFunction_55");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_62, "binaryMapToRefFlowFunction_62");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_70, "binaryMapToRefFlowFunction_70");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_77, "binaryMapToRefFlowFunction_77");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_83, "binaryMapToRefFlowFunction_83");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_88, "binaryMapToRefFlowFunction_88");
    auditor.nodeRegistered(filterFlowFunction_5, "filterFlowFunction_5");
    auditor.nodeRegistered(filterFlowFunction_9, "filterFlowFunction_9");
    auditor.nodeRegistered(flatMapFlowFunction_3, "flatMapFlowFunction_3");
    auditor.nodeRegistered(flatMapFlowFunction_19, "flatMapFlowFunction_19");
    auditor.nodeRegistered(flatMapFlowFunction_32, "flatMapFlowFunction_32");
    auditor.nodeRegistered(globalNetMtm, "globalNetMtm");
    auditor.nodeRegistered(instrumentNetMtm, "instrumentNetMtm");
    auditor.nodeRegistered(mapRef2RefFlowFunction_6, "mapRef2RefFlowFunction_6");
    auditor.nodeRegistered(mapRef2RefFlowFunction_7, "mapRef2RefFlowFunction_7");
    auditor.nodeRegistered(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12");
    auditor.nodeRegistered(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14");
    auditor.nodeRegistered(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16");
    auditor.nodeRegistered(mapRef2RefFlowFunction_18, "mapRef2RefFlowFunction_18");
    auditor.nodeRegistered(mapRef2RefFlowFunction_21, "mapRef2RefFlowFunction_21");
    auditor.nodeRegistered(mapRef2RefFlowFunction_25, "mapRef2RefFlowFunction_25");
    auditor.nodeRegistered(mapRef2RefFlowFunction_29, "mapRef2RefFlowFunction_29");
    auditor.nodeRegistered(mapRef2RefFlowFunction_31, "mapRef2RefFlowFunction_31");
    auditor.nodeRegistered(mapRef2RefFlowFunction_34, "mapRef2RefFlowFunction_34");
    auditor.nodeRegistered(mapRef2RefFlowFunction_36, "mapRef2RefFlowFunction_36");
    auditor.nodeRegistered(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40");
    auditor.nodeRegistered(mapRef2RefFlowFunction_42, "mapRef2RefFlowFunction_42");
    auditor.nodeRegistered(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46");
    auditor.nodeRegistered(mapRef2RefFlowFunction_47, "mapRef2RefFlowFunction_47");
    auditor.nodeRegistered(mapRef2RefFlowFunction_51, "mapRef2RefFlowFunction_51");
    auditor.nodeRegistered(mapRef2RefFlowFunction_53, "mapRef2RefFlowFunction_53");
    auditor.nodeRegistered(mapRef2RefFlowFunction_57, "mapRef2RefFlowFunction_57");
    auditor.nodeRegistered(mapRef2RefFlowFunction_58, "mapRef2RefFlowFunction_58");
    auditor.nodeRegistered(mapRef2RefFlowFunction_60, "mapRef2RefFlowFunction_60");
    auditor.nodeRegistered(mapRef2RefFlowFunction_64, "mapRef2RefFlowFunction_64");
    auditor.nodeRegistered(mapRef2RefFlowFunction_66, "mapRef2RefFlowFunction_66");
    auditor.nodeRegistered(mapRef2RefFlowFunction_68, "mapRef2RefFlowFunction_68");
    auditor.nodeRegistered(mapRef2RefFlowFunction_72, "mapRef2RefFlowFunction_72");
    auditor.nodeRegistered(mapRef2RefFlowFunction_73, "mapRef2RefFlowFunction_73");
    auditor.nodeRegistered(mapRef2RefFlowFunction_75, "mapRef2RefFlowFunction_75");
    auditor.nodeRegistered(mapRef2RefFlowFunction_79, "mapRef2RefFlowFunction_79");
    auditor.nodeRegistered(mapRef2RefFlowFunction_81, "mapRef2RefFlowFunction_81");
    auditor.nodeRegistered(mapRef2RefFlowFunction_85, "mapRef2RefFlowFunction_85");
    auditor.nodeRegistered(mergeFlowFunction_10, "mergeFlowFunction_10");
    auditor.nodeRegistered(pushFlowFunction_49, "pushFlowFunction_49");
    auditor.nodeRegistered(pushFlowFunction_87, "pushFlowFunction_87");
    auditor.nodeRegistered(emptyGroupBy_470, "emptyGroupBy_470");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_11, "groupByFlowFunctionWrapper_11");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_13, "groupByFlowFunctionWrapper_13");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_15, "groupByFlowFunctionWrapper_15");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_17, "groupByFlowFunctionWrapper_17");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_20, "groupByFlowFunctionWrapper_20");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_33, "groupByFlowFunctionWrapper_33");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_35, "groupByFlowFunctionWrapper_35");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_50, "groupByFlowFunctionWrapper_50");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_52, "groupByFlowFunctionWrapper_52");
    auditor.nodeRegistered(groupByHashMap_59, "groupByHashMap_59");
    auditor.nodeRegistered(groupByHashMap_74, "groupByHashMap_74");
    auditor.nodeRegistered(groupByMapFlowFunction_24, "groupByMapFlowFunction_24");
    auditor.nodeRegistered(groupByMapFlowFunction_28, "groupByMapFlowFunction_28");
    auditor.nodeRegistered(groupByMapFlowFunction_30, "groupByMapFlowFunction_30");
    auditor.nodeRegistered(groupByMapFlowFunction_39, "groupByMapFlowFunction_39");
    auditor.nodeRegistered(groupByMapFlowFunction_41, "groupByMapFlowFunction_41");
    auditor.nodeRegistered(groupByMapFlowFunction_45, "groupByMapFlowFunction_45");
    auditor.nodeRegistered(groupByMapFlowFunction_56, "groupByMapFlowFunction_56");
    auditor.nodeRegistered(groupByMapFlowFunction_63, "groupByMapFlowFunction_63");
    auditor.nodeRegistered(groupByMapFlowFunction_67, "groupByMapFlowFunction_67");
    auditor.nodeRegistered(groupByMapFlowFunction_71, "groupByMapFlowFunction_71");
    auditor.nodeRegistered(groupByMapFlowFunction_78, "groupByMapFlowFunction_78");
    auditor.nodeRegistered(groupByMapFlowFunction_80, "groupByMapFlowFunction_80");
    auditor.nodeRegistered(groupByMapFlowFunction_84, "groupByMapFlowFunction_84");
    auditor.nodeRegistered(leftJoin_43, "leftJoin_43");
    auditor.nodeRegistered(leftJoin_82, "leftJoin_82");
    auditor.nodeRegistered(outerJoin_22, "outerJoin_22");
    auditor.nodeRegistered(outerJoin_26, "outerJoin_26");
    auditor.nodeRegistered(outerJoin_37, "outerJoin_37");
    auditor.nodeRegistered(outerJoin_54, "outerJoin_54");
    auditor.nodeRegistered(outerJoin_61, "outerJoin_61");
    auditor.nodeRegistered(outerJoin_69, "outerJoin_69");
    auditor.nodeRegistered(outerJoin_76, "outerJoin_76");
    auditor.nodeRegistered(defaultValue_65, "defaultValue_65");
    auditor.nodeRegistered(mapTuple_758, "mapTuple_758");
    auditor.nodeRegistered(mapTuple_764, "mapTuple_764");
    auditor.nodeRegistered(mapTuple_768, "mapTuple_768");
    auditor.nodeRegistered(mapTuple_783, "mapTuple_783");
    auditor.nodeRegistered(mapTuple_787, "mapTuple_787");
    auditor.nodeRegistered(mapTuple_800, "mapTuple_800");
    auditor.nodeRegistered(mapTuple_805, "mapTuple_805");
    auditor.nodeRegistered(mapTuple_816, "mapTuple_816");
    auditor.nodeRegistered(mapTuple_820, "mapTuple_820");
    auditor.nodeRegistered(subscriptionManager, "subscriptionManager");
    auditor.nodeRegistered(handlerPositionSnapshot, "handlerPositionSnapshot");
    auditor.nodeRegistered(
        handlerSignal_positionSnapshotReset, "handlerSignal_positionSnapshotReset");
    auditor.nodeRegistered(handlerSignal_positionUpdate, "handlerSignal_positionUpdate");
    auditor.nodeRegistered(handlerTrade, "handlerTrade");
    auditor.nodeRegistered(handlerTradeBatch, "handlerTradeBatch");
    auditor.nodeRegistered(context, "context");
    auditor.nodeRegistered(namedFeedTableNode_89, "namedFeedTableNode_89");
    auditor.nodeRegistered(globalNetMtmListener, "globalNetMtmListener");
    auditor.nodeRegistered(instrumentNetMtmListener, "instrumentNetMtmListener");
    auditor.nodeRegistered(derivedRateNode, "derivedRateNode");
    auditor.nodeRegistered(eventFeedBatcher, "eventFeedBatcher");
    auditor.nodeRegistered(positionCache, "positionCache");
    auditor.nodeRegistered(tradeSequenceFilter_4, "tradeSequenceFilter_4");
    auditor.nodeRegistered(tradeSequenceFilter_8, "tradeSequenceFilter_8");
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
    isDirty_binaryMapToRefFlowFunction_23 = false;
    isDirty_binaryMapToRefFlowFunction_27 = false;
    isDirty_binaryMapToRefFlowFunction_38 = false;
    isDirty_binaryMapToRefFlowFunction_44 = false;
    isDirty_binaryMapToRefFlowFunction_55 = false;
    isDirty_binaryMapToRefFlowFunction_62 = false;
    isDirty_binaryMapToRefFlowFunction_70 = false;
    isDirty_binaryMapToRefFlowFunction_77 = false;
    isDirty_binaryMapToRefFlowFunction_83 = false;
    isDirty_callBackNode_90 = false;
    isDirty_callBackNode_121 = false;
    isDirty_callBackNode_201 = false;
    isDirty_clock = false;
    isDirty_derivedRateNode = false;
    isDirty_eventFeedBatcher = false;
    isDirty_filterFlowFunction_5 = false;
    isDirty_filterFlowFunction_9 = false;
    isDirty_flatMapFlowFunction_3 = false;
    isDirty_flatMapFlowFunction_19 = false;
    isDirty_flatMapFlowFunction_32 = false;
    isDirty_globalNetMtm = false;
    isDirty_handlerPositionSnapshot = false;
    isDirty_handlerSignal_positionSnapshotReset = false;
    isDirty_handlerSignal_positionUpdate = false;
    isDirty_handlerTrade = false;
    isDirty_handlerTradeBatch = false;
    isDirty_instrumentNetMtm = false;
    isDirty_mapRef2RefFlowFunction_6 = false;
    isDirty_mapRef2RefFlowFunction_7 = false;
    isDirty_mapRef2RefFlowFunction_12 = false;
    isDirty_mapRef2RefFlowFunction_14 = false;
    isDirty_mapRef2RefFlowFunction_16 = false;
    isDirty_mapRef2RefFlowFunction_18 = false;
    isDirty_mapRef2RefFlowFunction_21 = false;
    isDirty_mapRef2RefFlowFunction_25 = false;
    isDirty_mapRef2RefFlowFunction_29 = false;
    isDirty_mapRef2RefFlowFunction_31 = false;
    isDirty_mapRef2RefFlowFunction_34 = false;
    isDirty_mapRef2RefFlowFunction_36 = false;
    isDirty_mapRef2RefFlowFunction_40 = false;
    isDirty_mapRef2RefFlowFunction_42 = false;
    isDirty_mapRef2RefFlowFunction_46 = false;
    isDirty_mapRef2RefFlowFunction_47 = false;
    isDirty_mapRef2RefFlowFunction_51 = false;
    isDirty_mapRef2RefFlowFunction_53 = false;
    isDirty_mapRef2RefFlowFunction_57 = false;
    isDirty_mapRef2RefFlowFunction_58 = false;
    isDirty_mapRef2RefFlowFunction_60 = false;
    isDirty_mapRef2RefFlowFunction_64 = false;
    isDirty_mapRef2RefFlowFunction_66 = false;
    isDirty_mapRef2RefFlowFunction_68 = false;
    isDirty_mapRef2RefFlowFunction_72 = false;
    isDirty_mapRef2RefFlowFunction_73 = false;
    isDirty_mapRef2RefFlowFunction_75 = false;
    isDirty_mapRef2RefFlowFunction_79 = false;
    isDirty_mapRef2RefFlowFunction_81 = false;
    isDirty_mapRef2RefFlowFunction_85 = false;
    isDirty_mergeFlowFunction_10 = false;
    isDirty_namedFeedTableNode_89 = false;
    isDirty_positionCache = false;
    isDirty_pushFlowFunction_49 = false;
    isDirty_pushFlowFunction_87 = false;
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
          binaryMapToRefFlowFunction_38, () -> isDirty_binaryMapToRefFlowFunction_38);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_44, () -> isDirty_binaryMapToRefFlowFunction_44);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_55, () -> isDirty_binaryMapToRefFlowFunction_55);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_62, () -> isDirty_binaryMapToRefFlowFunction_62);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_70, () -> isDirty_binaryMapToRefFlowFunction_70);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_77, () -> isDirty_binaryMapToRefFlowFunction_77);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_83, () -> isDirty_binaryMapToRefFlowFunction_83);
      dirtyFlagSupplierMap.put(callBackNode_121, () -> isDirty_callBackNode_121);
      dirtyFlagSupplierMap.put(callBackNode_201, () -> isDirty_callBackNode_201);
      dirtyFlagSupplierMap.put(callBackNode_90, () -> isDirty_callBackNode_90);
      dirtyFlagSupplierMap.put(clock, () -> isDirty_clock);
      dirtyFlagSupplierMap.put(derivedRateNode, () -> isDirty_derivedRateNode);
      dirtyFlagSupplierMap.put(eventFeedBatcher, () -> isDirty_eventFeedBatcher);
      dirtyFlagSupplierMap.put(filterFlowFunction_5, () -> isDirty_filterFlowFunction_5);
      dirtyFlagSupplierMap.put(filterFlowFunction_9, () -> isDirty_filterFlowFunction_9);
      dirtyFlagSupplierMap.put(flatMapFlowFunction_19, () -> isDirty_flatMapFlowFunction_19);
      dirtyFlagSupplierMap.put(flatMapFlowFunction_3, () -> isDirty_flatMapFlowFunction_3);
      dirtyFlagSupplierMap.put(flatMapFlowFunction_32, () -> isDirty_flatMapFlowFunction_32);
      dirtyFlagSupplierMap.put(globalNetMtm, () -> isDirty_globalNetMtm);
      dirtyFlagSupplierMap.put(handlerPositionSnapshot, () -> isDirty_handlerPositionSnapshot);
      dirtyFlagSupplierMap.put(
          handlerSignal_positionSnapshotReset, () -> isDirty_handlerSignal_positionSnapshotReset);
      dirtyFlagSupplierMap.put(
          handlerSignal_positionUpdate, () -> isDirty_handlerSignal_positionUpdate);
      dirtyFlagSupplierMap.put(handlerTrade, () -> isDirty_handlerTrade);
      dirtyFlagSupplierMap.put(handlerTradeBatch, () -> isDirty_handlerTradeBatch);
      dirtyFlagSupplierMap.put(instrumentNetMtm, () -> isDirty_instrumentNetMtm);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_12, () -> isDirty_mapRef2RefFlowFunction_12);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_14, () -> isDirty_mapRef2RefFlowFunction_14);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_16, () -> isDirty_mapRef2RefFlowFunction_16);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_18, () -> isDirty_mapRef2RefFlowFunction_18);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_21, () -> isDirty_mapRef2RefFlowFunction_21);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_25, () -> isDirty_mapRef2RefFlowFunction_25);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_29, () -> isDirty_mapRef2RefFlowFunction_29);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_31, () -> isDirty_mapRef2RefFlowFunction_31);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_34, () -> isDirty_mapRef2RefFlowFunction_34);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_36, () -> isDirty_mapRef2RefFlowFunction_36);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_40, () -> isDirty_mapRef2RefFlowFunction_40);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_42, () -> isDirty_mapRef2RefFlowFunction_42);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_46, () -> isDirty_mapRef2RefFlowFunction_46);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_47, () -> isDirty_mapRef2RefFlowFunction_47);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_51, () -> isDirty_mapRef2RefFlowFunction_51);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_53, () -> isDirty_mapRef2RefFlowFunction_53);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_57, () -> isDirty_mapRef2RefFlowFunction_57);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_58, () -> isDirty_mapRef2RefFlowFunction_58);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_6, () -> isDirty_mapRef2RefFlowFunction_6);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_60, () -> isDirty_mapRef2RefFlowFunction_60);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_64, () -> isDirty_mapRef2RefFlowFunction_64);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_66, () -> isDirty_mapRef2RefFlowFunction_66);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_68, () -> isDirty_mapRef2RefFlowFunction_68);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_7, () -> isDirty_mapRef2RefFlowFunction_7);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_72, () -> isDirty_mapRef2RefFlowFunction_72);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_73, () -> isDirty_mapRef2RefFlowFunction_73);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_75, () -> isDirty_mapRef2RefFlowFunction_75);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_79, () -> isDirty_mapRef2RefFlowFunction_79);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_81, () -> isDirty_mapRef2RefFlowFunction_81);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_85, () -> isDirty_mapRef2RefFlowFunction_85);
      dirtyFlagSupplierMap.put(mergeFlowFunction_10, () -> isDirty_mergeFlowFunction_10);
      dirtyFlagSupplierMap.put(namedFeedTableNode_89, () -> isDirty_namedFeedTableNode_89);
      dirtyFlagSupplierMap.put(positionCache, () -> isDirty_positionCache);
      dirtyFlagSupplierMap.put(pushFlowFunction_49, () -> isDirty_pushFlowFunction_49);
      dirtyFlagSupplierMap.put(pushFlowFunction_87, () -> isDirty_pushFlowFunction_87);
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
          binaryMapToRefFlowFunction_38, (b) -> isDirty_binaryMapToRefFlowFunction_38 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_44, (b) -> isDirty_binaryMapToRefFlowFunction_44 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_55, (b) -> isDirty_binaryMapToRefFlowFunction_55 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_62, (b) -> isDirty_binaryMapToRefFlowFunction_62 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_70, (b) -> isDirty_binaryMapToRefFlowFunction_70 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_77, (b) -> isDirty_binaryMapToRefFlowFunction_77 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_83, (b) -> isDirty_binaryMapToRefFlowFunction_83 = b);
      dirtyFlagUpdateMap.put(callBackNode_121, (b) -> isDirty_callBackNode_121 = b);
      dirtyFlagUpdateMap.put(callBackNode_201, (b) -> isDirty_callBackNode_201 = b);
      dirtyFlagUpdateMap.put(callBackNode_90, (b) -> isDirty_callBackNode_90 = b);
      dirtyFlagUpdateMap.put(clock, (b) -> isDirty_clock = b);
      dirtyFlagUpdateMap.put(derivedRateNode, (b) -> isDirty_derivedRateNode = b);
      dirtyFlagUpdateMap.put(eventFeedBatcher, (b) -> isDirty_eventFeedBatcher = b);
      dirtyFlagUpdateMap.put(filterFlowFunction_5, (b) -> isDirty_filterFlowFunction_5 = b);
      dirtyFlagUpdateMap.put(filterFlowFunction_9, (b) -> isDirty_filterFlowFunction_9 = b);
      dirtyFlagUpdateMap.put(flatMapFlowFunction_19, (b) -> isDirty_flatMapFlowFunction_19 = b);
      dirtyFlagUpdateMap.put(flatMapFlowFunction_3, (b) -> isDirty_flatMapFlowFunction_3 = b);
      dirtyFlagUpdateMap.put(flatMapFlowFunction_32, (b) -> isDirty_flatMapFlowFunction_32 = b);
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
          mapRef2RefFlowFunction_12, (b) -> isDirty_mapRef2RefFlowFunction_12 = b);
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
          mapRef2RefFlowFunction_34, (b) -> isDirty_mapRef2RefFlowFunction_34 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_36, (b) -> isDirty_mapRef2RefFlowFunction_36 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_40, (b) -> isDirty_mapRef2RefFlowFunction_40 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_42, (b) -> isDirty_mapRef2RefFlowFunction_42 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_46, (b) -> isDirty_mapRef2RefFlowFunction_46 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_47, (b) -> isDirty_mapRef2RefFlowFunction_47 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_51, (b) -> isDirty_mapRef2RefFlowFunction_51 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_53, (b) -> isDirty_mapRef2RefFlowFunction_53 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_57, (b) -> isDirty_mapRef2RefFlowFunction_57 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_58, (b) -> isDirty_mapRef2RefFlowFunction_58 = b);
      dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_6, (b) -> isDirty_mapRef2RefFlowFunction_6 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_60, (b) -> isDirty_mapRef2RefFlowFunction_60 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_64, (b) -> isDirty_mapRef2RefFlowFunction_64 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_66, (b) -> isDirty_mapRef2RefFlowFunction_66 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_68, (b) -> isDirty_mapRef2RefFlowFunction_68 = b);
      dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_7, (b) -> isDirty_mapRef2RefFlowFunction_7 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_72, (b) -> isDirty_mapRef2RefFlowFunction_72 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_73, (b) -> isDirty_mapRef2RefFlowFunction_73 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_75, (b) -> isDirty_mapRef2RefFlowFunction_75 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_79, (b) -> isDirty_mapRef2RefFlowFunction_79 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_81, (b) -> isDirty_mapRef2RefFlowFunction_81 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_85, (b) -> isDirty_mapRef2RefFlowFunction_85 = b);
      dirtyFlagUpdateMap.put(mergeFlowFunction_10, (b) -> isDirty_mergeFlowFunction_10 = b);
      dirtyFlagUpdateMap.put(namedFeedTableNode_89, (b) -> isDirty_namedFeedTableNode_89 = b);
      dirtyFlagUpdateMap.put(positionCache, (b) -> isDirty_positionCache = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_49, (b) -> isDirty_pushFlowFunction_49 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_87, (b) -> isDirty_pushFlowFunction_87 = b);
    }
    dirtyFlagUpdateMap.get(node).accept(dirtyFlag);
  }

  private boolean guardCheck_eventLogger() {
    return isDirty_clock;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_23() {
    return isDirty_mapRef2RefFlowFunction_12 | isDirty_mapRef2RefFlowFunction_14;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_27() {
    return isDirty_mapRef2RefFlowFunction_21 | isDirty_mapRef2RefFlowFunction_25;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_38() {
    return isDirty_mapRef2RefFlowFunction_34 | isDirty_mapRef2RefFlowFunction_36;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_44() {
    return isDirty_mapRef2RefFlowFunction_31 | isDirty_mapRef2RefFlowFunction_42;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_55() {
    return isDirty_mapRef2RefFlowFunction_51 | isDirty_mapRef2RefFlowFunction_53;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_62() {
    return isDirty_mapRef2RefFlowFunction_57 | isDirty_mapRef2RefFlowFunction_60;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_70() {
    return isDirty_mapRef2RefFlowFunction_16 | isDirty_mapRef2RefFlowFunction_18;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_77() {
    return isDirty_mapRef2RefFlowFunction_72 | isDirty_mapRef2RefFlowFunction_75;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_83() {
    return isDirty_mapRef2RefFlowFunction_68 | isDirty_mapRef2RefFlowFunction_81;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_88() {
    return isDirty_positionCache | isDirty_pushFlowFunction_49 | isDirty_pushFlowFunction_87;
  }

  private boolean guardCheck_filterFlowFunction_5() {
    return isDirty_flatMapFlowFunction_3;
  }

  private boolean guardCheck_filterFlowFunction_9() {
    return isDirty_mapRef2RefFlowFunction_7;
  }

  private boolean guardCheck_flatMapFlowFunction_3() {
    return isDirty_callBackNode_90;
  }

  private boolean guardCheck_flatMapFlowFunction_19() {
    return isDirty_callBackNode_121;
  }

  private boolean guardCheck_flatMapFlowFunction_32() {
    return isDirty_callBackNode_201;
  }

  private boolean guardCheck_globalNetMtm() {
    return isDirty_mapRef2RefFlowFunction_47;
  }

  private boolean guardCheck_instrumentNetMtm() {
    return isDirty_mapRef2RefFlowFunction_85;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_6() {
    return isDirty_eventFeedBatcher | isDirty_filterFlowFunction_5;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_7() {
    return isDirty_eventFeedBatcher | isDirty_handlerTrade;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_12() {
    return isDirty_handlerSignal_positionSnapshotReset | isDirty_mergeFlowFunction_10;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_14() {
    return isDirty_handlerSignal_positionSnapshotReset | isDirty_mergeFlowFunction_10;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_16() {
    return isDirty_handlerSignal_positionSnapshotReset | isDirty_mergeFlowFunction_10;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_18() {
    return isDirty_handlerSignal_positionSnapshotReset | isDirty_mergeFlowFunction_10;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_21() {
    return isDirty_flatMapFlowFunction_19
        | isDirty_handlerSignal_positionSnapshotReset
        | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_25() {
    return isDirty_binaryMapToRefFlowFunction_23 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_29() {
    return isDirty_binaryMapToRefFlowFunction_27;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_31() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_29;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_34() {
    return isDirty_flatMapFlowFunction_32
        | isDirty_handlerSignal_positionSnapshotReset
        | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_36() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mergeFlowFunction_10;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_40() {
    return isDirty_binaryMapToRefFlowFunction_38 | isDirty_handlerSignal_positionSnapshotReset;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_42() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_40;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_46() {
    return isDirty_binaryMapToRefFlowFunction_44 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_47() {
    return isDirty_mapRef2RefFlowFunction_46;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_51() {
    return isDirty_mergeFlowFunction_10;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_53() {
    return isDirty_mergeFlowFunction_10;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_57() {
    return isDirty_binaryMapToRefFlowFunction_55 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_58() {
    return isDirty_handlerPositionSnapshot;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_60() {
    return isDirty_mapRef2RefFlowFunction_58;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_64() {
    return isDirty_binaryMapToRefFlowFunction_62 | isDirty_handlerSignal_positionSnapshotReset;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_66() {
    return isDirty_mapRef2RefFlowFunction_64;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_68() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_66;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_72() {
    return isDirty_binaryMapToRefFlowFunction_70 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_73() {
    return isDirty_handlerPositionSnapshot;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_75() {
    return isDirty_mapRef2RefFlowFunction_73;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_79() {
    return isDirty_binaryMapToRefFlowFunction_77;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_81() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_79;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_85() {
    return isDirty_binaryMapToRefFlowFunction_83 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mergeFlowFunction_10() {
    return isDirty_filterFlowFunction_9 | isDirty_mapRef2RefFlowFunction_6;
  }

  private boolean guardCheck_pushFlowFunction_49() {
    return isDirty_globalNetMtm;
  }

  private boolean guardCheck_pushFlowFunction_87() {
    return isDirty_instrumentNetMtm;
  }

  private boolean guardCheck_groupByMapFlowFunction_30() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_groupByMapFlowFunction_41() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_groupByMapFlowFunction_67() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_groupByMapFlowFunction_80() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_context() {
    return isDirty_clock;
  }

  private boolean guardCheck_globalNetMtmListener() {
    return isDirty_pushFlowFunction_49;
  }

  private boolean guardCheck_instrumentNetMtmListener() {
    return isDirty_pushFlowFunction_87;
  }

  private boolean guardCheck_derivedRateNode() {
    return isDirty_namedFeedTableNode_89;
  }

  private boolean guardCheck_eventFeedBatcher() {
    return isDirty_namedFeedTableNode_89;
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
  public DebugFluxtionPnlCalculator newInstance() {
    return new DebugFluxtionPnlCalculator();
  }

  @Override
  public DebugFluxtionPnlCalculator newInstance(Map<Object, Object> contextMap) {
    return new DebugFluxtionPnlCalculator();
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
