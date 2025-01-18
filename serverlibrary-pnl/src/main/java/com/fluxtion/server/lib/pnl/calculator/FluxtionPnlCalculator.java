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
 * eventProcessorGenerator version : 9.7.5
 * api version                     : 9.7.5
 * </pre>
 *
 * Event classes supported:
 *
 * <ul>
 *   <li>com.fluxtion.compiler.generation.model.ExportFunctionMarker
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
public class FluxtionPnlCalculator
    implements EventProcessor<FluxtionPnlCalculator>,
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
  private final transient CallBackNode callBackNode_88 = new CallBackNode<>(callBackTriggerEvent_0);
  private final transient InstanceCallbackEvent_1 callBackTriggerEvent_1 =
      new InstanceCallbackEvent_1();
  private final transient CallBackNode callBackNode_112 =
      new CallBackNode<>(callBackTriggerEvent_1);
  private final transient InstanceCallbackEvent_2 callBackTriggerEvent_2 =
      new InstanceCallbackEvent_2();
  private final transient CallBackNode callBackNode_182 =
      new CallBackNode<>(callBackTriggerEvent_2);
  private final transient CallbackDispatcherImpl callbackDispatcher = new CallbackDispatcherImpl();
  public final transient Clock clock = new Clock();
  private final transient EmptyGroupBy emptyGroupBy_422 = new EmptyGroupBy<>();
  private final transient DefaultValue defaultValue_63 = new DefaultValue<>(emptyGroupBy_422);
  private final transient GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_9 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, SingleInstrumentPosMtmAggregate::dealt);
  private final transient GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_11 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getContraInstrument, Mappers::identity, SingleInstrumentPosMtmAggregate::contra);
  private final transient GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_13 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, InstrumentPosMtmAggregate::dealt);
  private final transient GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_15 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getContraInstrument, Mappers::identity, InstrumentPosMtmAggregate::contra);
  private final transient GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_18 =
      new GroupByFlowFunctionWrapper<>(
          InstrumentPosition::instrument, Mappers::identity, AggregateIdentityFlowFunction::new);
  private final transient GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_31 =
      new GroupByFlowFunctionWrapper<>(
          InstrumentPosition::instrument, Mappers::identity, AggregateIdentityFlowFunction::new);
  private final transient GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_33 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, FeeInstrumentPosMtmAggregate::new);
  private final transient GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_48 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, FeeInstrumentPosMtmAggregate::new);
  private final transient GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_50 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getContraInstrument, Mappers::identity, FeeInstrumentPosMtmAggregate::new);
  private final transient GroupByHashMap groupByHashMap_57 = new GroupByHashMap<>();
  private final transient GroupByHashMap groupByHashMap_72 = new GroupByHashMap<>();
  private final transient LeftJoin leftJoin_41 = new LeftJoin();
  private final transient LeftJoin leftJoin_80 = new LeftJoin();
  private final transient MapTuple mapTuple_680 = new MapTuple<>(NetMarkToMarket::combine);
  private final transient GroupByMapFlowFunction groupByMapFlowFunction_82 =
      new GroupByMapFlowFunction(mapTuple_680::mapTuple);
  private final transient MapTuple mapTuple_686 = new MapTuple<>(FeeInstrumentPosMtm::merge);
  private final transient GroupByMapFlowFunction groupByMapFlowFunction_61 =
      new GroupByMapFlowFunction(mapTuple_686::mapTuple);
  private final transient MapTuple mapTuple_690 = new MapTuple<>(FeeInstrumentPosMtm::merge);
  private final transient GroupByMapFlowFunction groupByMapFlowFunction_54 =
      new GroupByMapFlowFunction(mapTuple_690::mapTuple);
  private final transient MapTuple mapTuple_703 = new MapTuple<>(InstrumentPosMtm::mergeSnapshot);
  private final transient GroupByMapFlowFunction groupByMapFlowFunction_76 =
      new GroupByMapFlowFunction(mapTuple_703::mapTuple);
  private final transient MapTuple mapTuple_707 = new MapTuple<>(InstrumentPosMtm::merge);
  private final transient GroupByMapFlowFunction groupByMapFlowFunction_69 =
      new GroupByMapFlowFunction(mapTuple_707::mapTuple);
  private final transient MapTuple mapTuple_718 = new MapTuple<>(NetMarkToMarket::combine);
  private final transient GroupByMapFlowFunction groupByMapFlowFunction_43 =
      new GroupByMapFlowFunction(mapTuple_718::mapTuple);
  private final transient MapTuple mapTuple_723 = new MapTuple<>(FeeInstrumentPosMtm::addSnapshot);
  private final transient GroupByMapFlowFunction groupByMapFlowFunction_37 =
      new GroupByMapFlowFunction(mapTuple_723::mapTuple);
  private final transient MapTuple mapTuple_733 = new MapTuple<>(InstrumentPosMtm::addSnapshot);
  private final transient GroupByMapFlowFunction groupByMapFlowFunction_26 =
      new GroupByMapFlowFunction(mapTuple_733::mapTuple);
  private final transient MapTuple mapTuple_737 = new MapTuple<>(InstrumentPosMtm::merge);
  private final transient GroupByMapFlowFunction groupByMapFlowFunction_22 =
      new GroupByMapFlowFunction(mapTuple_737::mapTuple);
  private final transient NamedFeedTableNode namedFeedTableNode_87 =
      new NamedFeedTableNode<>("symbolFeed", Symbol::symbolName);
  public final transient DerivedRateNode derivedRateNode =
      new DerivedRateNode(namedFeedTableNode_87);
  public final transient EventFeedConnector eventFeedBatcher =
      new EventFeedConnector(namedFeedTableNode_87);
  private final transient GroupByMapFlowFunction groupByMapFlowFunction_28 =
      new GroupByMapFlowFunction(derivedRateNode::calculateInstrumentPosMtm);
  private final transient GroupByMapFlowFunction groupByMapFlowFunction_39 =
      new GroupByMapFlowFunction(derivedRateNode::calculateFeeMtm);
  private final transient GroupByMapFlowFunction groupByMapFlowFunction_65 =
      new GroupByMapFlowFunction(derivedRateNode::calculateFeeMtm);
  private final transient GroupByMapFlowFunction groupByMapFlowFunction_78 =
      new GroupByMapFlowFunction(derivedRateNode::calculateInstrumentPosMtm);
  public final transient NodeNameAuditor nodeNameLookup = new NodeNameAuditor();
  private final transient OuterJoin outerJoin_20 = new OuterJoin();
  private final transient OuterJoin outerJoin_24 = new OuterJoin();
  private final transient OuterJoin outerJoin_35 = new OuterJoin();
  private final transient OuterJoin outerJoin_52 = new OuterJoin();
  private final transient OuterJoin outerJoin_59 = new OuterJoin();
  private final transient OuterJoin outerJoin_67 = new OuterJoin();
  private final transient OuterJoin outerJoin_74 = new OuterJoin();
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
  private final transient FlatMapFlowFunction flatMapFlowFunction_17 =
      new FlatMapFlowFunction<>(handlerPositionSnapshot, PositionSnapshot::getPositions);
  private final transient FlatMapFlowFunction flatMapFlowFunction_30 =
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
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_4 =
      new MapRef2RefFlowFunction<>(flatMapFlowFunction_3, eventFeedBatcher::validateBatchTrade);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_5 =
      new MapRef2RefFlowFunction<>(handlerTrade, eventFeedBatcher::validateTrade);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_19 =
      new MapRef2RefFlowFunction<>(
          flatMapFlowFunction_17, groupByFlowFunctionWrapper_18::aggregate);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_32 =
      new MapRef2RefFlowFunction<>(
          flatMapFlowFunction_30, groupByFlowFunctionWrapper_31::aggregate);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_56 =
      new MapRef2RefFlowFunction<>(
          handlerPositionSnapshot, PositionSnapshot::getInstrumentFeePositionMap);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_58 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_56, groupByHashMap_57::fromMap);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_71 =
      new MapRef2RefFlowFunction<>(
          handlerPositionSnapshot, PositionSnapshot::getInstrumentPositionMap);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_73 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_71, groupByHashMap_72::fromMap);
  private final transient MergeFlowFunction mergeFlowFunction_6 =
      new MergeFlowFunction<>(Arrays.asList(mapRef2RefFlowFunction_5, mapRef2RefFlowFunction_4));
  public final transient ServiceRegistryNode serviceRegistry = new ServiceRegistryNode();
  private final transient TradeSequenceFilter tradeSequenceFilter_7 = new TradeSequenceFilter();
  private final transient FilterFlowFunction filterFlowFunction_8 =
      new FilterFlowFunction<>(
          mergeFlowFunction_6, tradeSequenceFilter_7::checkTradeSequenceNumber);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_10 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_8, groupByFlowFunctionWrapper_9::aggregate);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_12 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_8, groupByFlowFunctionWrapper_11::aggregate);
  private final transient BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_21 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_10, mapRef2RefFlowFunction_12, outerJoin_20::join);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_14 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_8, groupByFlowFunctionWrapper_13::aggregate);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_16 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_8, groupByFlowFunctionWrapper_15::aggregate);
  private final transient BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_68 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_14, mapRef2RefFlowFunction_16, outerJoin_67::join);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_23 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_21, groupByMapFlowFunction_22::mapValues);
  private final transient BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_25 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_23, mapRef2RefFlowFunction_19, outerJoin_24::join);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_27 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_25, groupByMapFlowFunction_26::mapValues);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_29 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_27, groupByMapFlowFunction_28::mapValues);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_34 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_8, groupByFlowFunctionWrapper_33::aggregate);
  private final transient BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_36 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_34, mapRef2RefFlowFunction_32, outerJoin_35::join);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_38 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_36, groupByMapFlowFunction_37::mapValues);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_40 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_38, groupByMapFlowFunction_39::mapValues);
  private final transient BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_42 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_29, mapRef2RefFlowFunction_40, leftJoin_41::join);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_44 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_42, groupByMapFlowFunction_43::mapValues);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_45 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_44, GroupBy<Object, Object>::toMap);
  public final transient MapRef2RefFlowFunction globalNetMtm =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_45, NetMarkToMarket::markToMarketSum);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_49 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_8, groupByFlowFunctionWrapper_48::aggregate);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_51 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_8, groupByFlowFunctionWrapper_50::aggregate);
  private final transient BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_53 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_49, mapRef2RefFlowFunction_51, outerJoin_52::join);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_55 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_53, groupByMapFlowFunction_54::mapValues);
  private final transient BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_60 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_55, mapRef2RefFlowFunction_58, outerJoin_59::join);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_62 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_60, groupByMapFlowFunction_61::mapValues);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_64 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_62, defaultValue_63::getOrDefault);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_66 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_64, groupByMapFlowFunction_65::mapValues);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_70 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_68, groupByMapFlowFunction_69::mapValues);
  private final transient BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_75 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_70, mapRef2RefFlowFunction_73, outerJoin_74::join);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_77 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_75, groupByMapFlowFunction_76::mapValues);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_79 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_77, groupByMapFlowFunction_78::mapValues);
  private final transient BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_81 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_79, mapRef2RefFlowFunction_66, leftJoin_80::join);
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_83 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_81, groupByMapFlowFunction_82::mapValues);
  public final transient MapRef2RefFlowFunction instrumentNetMtm =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_83, GroupBy<Object, Object>::toMap);
  private final transient PushFlowFunction pushFlowFunction_47 =
      new PushFlowFunction<>(globalNetMtm, globalNetMtmListener::publish);
  private final transient PushFlowFunction pushFlowFunction_85 =
      new PushFlowFunction<>(instrumentNetMtm, instrumentNetMtmListener::publish);
  private final transient BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_86 =
      new BinaryMapToRefFlowFunction<>(
          pushFlowFunction_47, pushFlowFunction_85, positionCache::checkPoint);
  private final transient ExportFunctionAuditEvent functionAudit = new ExportFunctionAuditEvent();
  //Dirty flags
  private boolean initCalled = false;
  private boolean processing = false;
  private boolean buffering = false;
  private final transient IdentityHashMap<Object, BooleanSupplier> dirtyFlagSupplierMap =
      new IdentityHashMap<>(61);
  private final transient IdentityHashMap<Object, Consumer<Boolean>> dirtyFlagUpdateMap =
      new IdentityHashMap<>(61);

  private boolean isDirty_binaryMapToRefFlowFunction_21 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_25 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_36 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_42 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_53 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_60 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_68 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_75 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_81 = false;
  private boolean isDirty_callBackNode_88 = false;
  private boolean isDirty_callBackNode_112 = false;
  private boolean isDirty_callBackNode_182 = false;
  private boolean isDirty_clock = false;
  private boolean isDirty_derivedRateNode = false;
  private boolean isDirty_eventFeedBatcher = false;
  private boolean isDirty_filterFlowFunction_8 = false;
  private boolean isDirty_flatMapFlowFunction_3 = false;
  private boolean isDirty_flatMapFlowFunction_17 = false;
  private boolean isDirty_flatMapFlowFunction_30 = false;
  private boolean isDirty_globalNetMtm = false;
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
  private boolean isDirty_mapRef2RefFlowFunction_19 = false;
  private boolean isDirty_mapRef2RefFlowFunction_23 = false;
  private boolean isDirty_mapRef2RefFlowFunction_27 = false;
  private boolean isDirty_mapRef2RefFlowFunction_29 = false;
  private boolean isDirty_mapRef2RefFlowFunction_32 = false;
  private boolean isDirty_mapRef2RefFlowFunction_34 = false;
  private boolean isDirty_mapRef2RefFlowFunction_38 = false;
  private boolean isDirty_mapRef2RefFlowFunction_40 = false;
  private boolean isDirty_mapRef2RefFlowFunction_44 = false;
  private boolean isDirty_mapRef2RefFlowFunction_45 = false;
  private boolean isDirty_mapRef2RefFlowFunction_49 = false;
  private boolean isDirty_mapRef2RefFlowFunction_51 = false;
  private boolean isDirty_mapRef2RefFlowFunction_55 = false;
  private boolean isDirty_mapRef2RefFlowFunction_56 = false;
  private boolean isDirty_mapRef2RefFlowFunction_58 = false;
  private boolean isDirty_mapRef2RefFlowFunction_62 = false;
  private boolean isDirty_mapRef2RefFlowFunction_64 = false;
  private boolean isDirty_mapRef2RefFlowFunction_66 = false;
  private boolean isDirty_mapRef2RefFlowFunction_70 = false;
  private boolean isDirty_mapRef2RefFlowFunction_71 = false;
  private boolean isDirty_mapRef2RefFlowFunction_73 = false;
  private boolean isDirty_mapRef2RefFlowFunction_77 = false;
  private boolean isDirty_mapRef2RefFlowFunction_79 = false;
  private boolean isDirty_mapRef2RefFlowFunction_83 = false;
  private boolean isDirty_mergeFlowFunction_6 = false;
  private boolean isDirty_namedFeedTableNode_87 = false;
  private boolean isDirty_positionCache = false;
  private boolean isDirty_pushFlowFunction_47 = false;
  private boolean isDirty_pushFlowFunction_85 = false;

  //Forked declarations

  //Filter constants

  //unknown event handler
  private Consumer unKnownEventHandler = (e) -> {};

  public FluxtionPnlCalculator(Map<Object, Object> contextMap) {
    if (context != null) {
      context.replaceMappings(contextMap);
    }
    binaryMapToRefFlowFunction_21.setDefaultValue(new EmptyGroupBy());
    binaryMapToRefFlowFunction_21.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_25.setDefaultValue(new EmptyGroupBy());
    binaryMapToRefFlowFunction_25.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_36.setDefaultValue(new EmptyGroupBy());
    binaryMapToRefFlowFunction_36.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_42.setDefaultValue(new EmptyGroupBy());
    binaryMapToRefFlowFunction_42.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_53.setDefaultValue(new EmptyGroupBy());
    binaryMapToRefFlowFunction_53.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_60.setDefaultValue(new EmptyGroupBy());
    binaryMapToRefFlowFunction_60.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_68.setDefaultValue(new EmptyGroupBy());
    binaryMapToRefFlowFunction_68.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_75.setDefaultValue(new EmptyGroupBy());
    binaryMapToRefFlowFunction_75.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_81.setDefaultValue(new EmptyGroupBy());
    binaryMapToRefFlowFunction_81.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_86.setEventProcessorContext(context);
    filterFlowFunction_8.setEventProcessorContext(context);
    flatMapFlowFunction_3.setFlatMapCompleteSignal("positionUpdate");
    flatMapFlowFunction_3.callback = callBackNode_88;
    flatMapFlowFunction_3.dirtyStateMonitor = callbackDispatcher;
    flatMapFlowFunction_17.callback = callBackNode_112;
    flatMapFlowFunction_17.dirtyStateMonitor = callbackDispatcher;
    flatMapFlowFunction_30.callback = callBackNode_182;
    flatMapFlowFunction_30.dirtyStateMonitor = callbackDispatcher;
    globalNetMtm.setEventProcessorContext(context);
    instrumentNetMtm.setEventProcessorContext(context);
    mapRef2RefFlowFunction_4.setEventProcessorContext(context);
    mapRef2RefFlowFunction_5.setEventProcessorContext(context);
    mapRef2RefFlowFunction_10.setDefaultValue(new EmptyGroupBy());
    mapRef2RefFlowFunction_10.setEventProcessorContext(context);
    mapRef2RefFlowFunction_10.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_12.setDefaultValue(new EmptyGroupBy());
    mapRef2RefFlowFunction_12.setEventProcessorContext(context);
    mapRef2RefFlowFunction_12.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_14.setDefaultValue(new EmptyGroupBy());
    mapRef2RefFlowFunction_14.setEventProcessorContext(context);
    mapRef2RefFlowFunction_14.setPublishTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_14.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_16.setDefaultValue(new EmptyGroupBy());
    mapRef2RefFlowFunction_16.setEventProcessorContext(context);
    mapRef2RefFlowFunction_16.setPublishTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_16.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_19.setDefaultValue(new EmptyGroupBy());
    mapRef2RefFlowFunction_19.setEventProcessorContext(context);
    mapRef2RefFlowFunction_19.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_19.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_23.setEventProcessorContext(context);
    mapRef2RefFlowFunction_23.setPublishTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_27.setEventProcessorContext(context);
    mapRef2RefFlowFunction_29.setEventProcessorContext(context);
    mapRef2RefFlowFunction_29.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_29.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_32.setDefaultValue(new EmptyGroupBy());
    mapRef2RefFlowFunction_32.setEventProcessorContext(context);
    mapRef2RefFlowFunction_32.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_32.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_34.setDefaultValue(new EmptyGroupBy());
    mapRef2RefFlowFunction_34.setEventProcessorContext(context);
    mapRef2RefFlowFunction_34.setPublishTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_38.setEventProcessorContext(context);
    mapRef2RefFlowFunction_38.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_40.setEventProcessorContext(context);
    mapRef2RefFlowFunction_40.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_40.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_44.setEventProcessorContext(context);
    mapRef2RefFlowFunction_44.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_45.setEventProcessorContext(context);
    mapRef2RefFlowFunction_49.setDefaultValue(new EmptyGroupBy());
    mapRef2RefFlowFunction_49.setEventProcessorContext(context);
    mapRef2RefFlowFunction_51.setDefaultValue(new EmptyGroupBy());
    mapRef2RefFlowFunction_51.setEventProcessorContext(context);
    mapRef2RefFlowFunction_55.setEventProcessorContext(context);
    mapRef2RefFlowFunction_55.setPublishTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_56.setEventProcessorContext(context);
    mapRef2RefFlowFunction_58.setDefaultValue(new EmptyGroupBy());
    mapRef2RefFlowFunction_58.setEventProcessorContext(context);
    mapRef2RefFlowFunction_62.setEventProcessorContext(context);
    mapRef2RefFlowFunction_62.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_64.setEventProcessorContext(context);
    mapRef2RefFlowFunction_66.setEventProcessorContext(context);
    mapRef2RefFlowFunction_66.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_66.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_70.setEventProcessorContext(context);
    mapRef2RefFlowFunction_70.setPublishTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_71.setEventProcessorContext(context);
    mapRef2RefFlowFunction_73.setDefaultValue(new EmptyGroupBy());
    mapRef2RefFlowFunction_73.setEventProcessorContext(context);
    mapRef2RefFlowFunction_77.setEventProcessorContext(context);
    mapRef2RefFlowFunction_79.setEventProcessorContext(context);
    mapRef2RefFlowFunction_79.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_79.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_83.setEventProcessorContext(context);
    mapRef2RefFlowFunction_83.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mergeFlowFunction_6.dirtyStateMonitor = callbackDispatcher;
    pushFlowFunction_47.setEventProcessorContext(context);
    pushFlowFunction_85.setEventProcessorContext(context);
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
    namedFeedTableNode_87.initialise();
    namedFeedTableNode_87.init();
    derivedRateNode.init();
    eventFeedBatcher.init();
    positionCache.init();
    handlerPositionSnapshot.init();
    flatMapFlowFunction_17.init();
    flatMapFlowFunction_30.init();
    handlerSignal_positionSnapshotReset.init();
    handlerSignal_positionUpdate.init();
    handlerTrade.init();
    handlerTradeBatch.init();
    flatMapFlowFunction_3.init();
    mapRef2RefFlowFunction_4.initialiseEventStream();
    mapRef2RefFlowFunction_5.initialiseEventStream();
    mapRef2RefFlowFunction_19.initialiseEventStream();
    mapRef2RefFlowFunction_32.initialiseEventStream();
    mapRef2RefFlowFunction_56.initialiseEventStream();
    mapRef2RefFlowFunction_58.initialiseEventStream();
    mapRef2RefFlowFunction_71.initialiseEventStream();
    mapRef2RefFlowFunction_73.initialiseEventStream();
    tradeSequenceFilter_7.init();
    filterFlowFunction_8.initialiseEventStream();
    mapRef2RefFlowFunction_10.initialiseEventStream();
    mapRef2RefFlowFunction_12.initialiseEventStream();
    binaryMapToRefFlowFunction_21.initialiseEventStream();
    mapRef2RefFlowFunction_14.initialiseEventStream();
    mapRef2RefFlowFunction_16.initialiseEventStream();
    binaryMapToRefFlowFunction_68.initialiseEventStream();
    mapRef2RefFlowFunction_23.initialiseEventStream();
    binaryMapToRefFlowFunction_25.initialiseEventStream();
    mapRef2RefFlowFunction_27.initialiseEventStream();
    mapRef2RefFlowFunction_29.initialiseEventStream();
    mapRef2RefFlowFunction_34.initialiseEventStream();
    binaryMapToRefFlowFunction_36.initialiseEventStream();
    mapRef2RefFlowFunction_38.initialiseEventStream();
    mapRef2RefFlowFunction_40.initialiseEventStream();
    binaryMapToRefFlowFunction_42.initialiseEventStream();
    mapRef2RefFlowFunction_44.initialiseEventStream();
    mapRef2RefFlowFunction_45.initialiseEventStream();
    globalNetMtm.initialiseEventStream();
    mapRef2RefFlowFunction_49.initialiseEventStream();
    mapRef2RefFlowFunction_51.initialiseEventStream();
    binaryMapToRefFlowFunction_53.initialiseEventStream();
    mapRef2RefFlowFunction_55.initialiseEventStream();
    binaryMapToRefFlowFunction_60.initialiseEventStream();
    mapRef2RefFlowFunction_62.initialiseEventStream();
    mapRef2RefFlowFunction_64.initialiseEventStream();
    mapRef2RefFlowFunction_66.initialiseEventStream();
    mapRef2RefFlowFunction_70.initialiseEventStream();
    binaryMapToRefFlowFunction_75.initialiseEventStream();
    mapRef2RefFlowFunction_77.initialiseEventStream();
    mapRef2RefFlowFunction_79.initialiseEventStream();
    binaryMapToRefFlowFunction_81.initialiseEventStream();
    mapRef2RefFlowFunction_83.initialiseEventStream();
    instrumentNetMtm.initialiseEventStream();
    pushFlowFunction_47.initialiseEventStream();
    pushFlowFunction_85.initialiseEventStream();
    binaryMapToRefFlowFunction_86.initialiseEventStream();
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
    if (event
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

  public void handleEvent(InstanceCallbackEvent_0 typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_callBackNode_88 = callBackNode_88.onEvent(typedEvent);
    if (guardCheck_flatMapFlowFunction_3()) {
      isDirty_flatMapFlowFunction_3 = true;
      flatMapFlowFunction_3.callbackReceived();
      if (isDirty_flatMapFlowFunction_3) {
        mapRef2RefFlowFunction_4.inputUpdated(flatMapFlowFunction_3);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_4()) {
      isDirty_mapRef2RefFlowFunction_4 = mapRef2RefFlowFunction_4.map();
      if (isDirty_mapRef2RefFlowFunction_4) {
        mergeFlowFunction_6.inputStreamUpdated(mapRef2RefFlowFunction_4);
      }
    }
    if (guardCheck_mergeFlowFunction_6()) {
      isDirty_mergeFlowFunction_6 = mergeFlowFunction_6.publishMerge();
      if (isDirty_mergeFlowFunction_6) {
        filterFlowFunction_8.inputUpdated(mergeFlowFunction_6);
      }
    }
    if (guardCheck_filterFlowFunction_8()) {
      isDirty_filterFlowFunction_8 = filterFlowFunction_8.filter();
      if (isDirty_filterFlowFunction_8) {
        mapRef2RefFlowFunction_10.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_12.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_14.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_16.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_34.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_49.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_51.inputUpdated(filterFlowFunction_8);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_21.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_21.input2Updated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_21()) {
      isDirty_binaryMapToRefFlowFunction_21 = binaryMapToRefFlowFunction_21.map();
      if (isDirty_binaryMapToRefFlowFunction_21) {
        mapRef2RefFlowFunction_23.inputUpdated(binaryMapToRefFlowFunction_21);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_68.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_68()) {
      isDirty_binaryMapToRefFlowFunction_68 = binaryMapToRefFlowFunction_68.map();
      if (isDirty_binaryMapToRefFlowFunction_68) {
        mapRef2RefFlowFunction_70.inputUpdated(binaryMapToRefFlowFunction_68);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_23()) {
      isDirty_mapRef2RefFlowFunction_23 = mapRef2RefFlowFunction_23.map();
      if (isDirty_mapRef2RefFlowFunction_23) {
        binaryMapToRefFlowFunction_25.inputUpdated(mapRef2RefFlowFunction_23);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_25()) {
      isDirty_binaryMapToRefFlowFunction_25 = binaryMapToRefFlowFunction_25.map();
      if (isDirty_binaryMapToRefFlowFunction_25) {
        mapRef2RefFlowFunction_27.inputUpdated(binaryMapToRefFlowFunction_25);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_27()) {
      isDirty_mapRef2RefFlowFunction_27 = mapRef2RefFlowFunction_27.map();
      if (isDirty_mapRef2RefFlowFunction_27) {
        mapRef2RefFlowFunction_29.inputUpdated(mapRef2RefFlowFunction_27);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_29()) {
      isDirty_mapRef2RefFlowFunction_29 = mapRef2RefFlowFunction_29.map();
      if (isDirty_mapRef2RefFlowFunction_29) {
        binaryMapToRefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_29);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_34()) {
      isDirty_mapRef2RefFlowFunction_34 = mapRef2RefFlowFunction_34.map();
      if (isDirty_mapRef2RefFlowFunction_34) {
        binaryMapToRefFlowFunction_36.inputUpdated(mapRef2RefFlowFunction_34);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_36()) {
      isDirty_binaryMapToRefFlowFunction_36 = binaryMapToRefFlowFunction_36.map();
      if (isDirty_binaryMapToRefFlowFunction_36) {
        mapRef2RefFlowFunction_38.inputUpdated(binaryMapToRefFlowFunction_36);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_38()) {
      isDirty_mapRef2RefFlowFunction_38 = mapRef2RefFlowFunction_38.map();
      if (isDirty_mapRef2RefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(mapRef2RefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        binaryMapToRefFlowFunction_42.input2Updated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_42()) {
      isDirty_binaryMapToRefFlowFunction_42 = binaryMapToRefFlowFunction_42.map();
      if (isDirty_binaryMapToRefFlowFunction_42) {
        mapRef2RefFlowFunction_44.inputUpdated(binaryMapToRefFlowFunction_42);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_44()) {
      isDirty_mapRef2RefFlowFunction_44 = mapRef2RefFlowFunction_44.map();
      if (isDirty_mapRef2RefFlowFunction_44) {
        mapRef2RefFlowFunction_45.inputUpdated(mapRef2RefFlowFunction_44);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_45()) {
      isDirty_mapRef2RefFlowFunction_45 = mapRef2RefFlowFunction_45.map();
      if (isDirty_mapRef2RefFlowFunction_45) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_45);
      }
    }
    if (guardCheck_globalNetMtm()) {
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_47.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_49()) {
      isDirty_mapRef2RefFlowFunction_49 = mapRef2RefFlowFunction_49.map();
      if (isDirty_mapRef2RefFlowFunction_49) {
        binaryMapToRefFlowFunction_53.inputUpdated(mapRef2RefFlowFunction_49);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_51()) {
      isDirty_mapRef2RefFlowFunction_51 = mapRef2RefFlowFunction_51.map();
      if (isDirty_mapRef2RefFlowFunction_51) {
        binaryMapToRefFlowFunction_53.input2Updated(mapRef2RefFlowFunction_51);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_53()) {
      isDirty_binaryMapToRefFlowFunction_53 = binaryMapToRefFlowFunction_53.map();
      if (isDirty_binaryMapToRefFlowFunction_53) {
        mapRef2RefFlowFunction_55.inputUpdated(binaryMapToRefFlowFunction_53);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_55()) {
      isDirty_mapRef2RefFlowFunction_55 = mapRef2RefFlowFunction_55.map();
      if (isDirty_mapRef2RefFlowFunction_55) {
        binaryMapToRefFlowFunction_60.inputUpdated(mapRef2RefFlowFunction_55);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_60()) {
      isDirty_binaryMapToRefFlowFunction_60 = binaryMapToRefFlowFunction_60.map();
      if (isDirty_binaryMapToRefFlowFunction_60) {
        mapRef2RefFlowFunction_62.inputUpdated(binaryMapToRefFlowFunction_60);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_62()) {
      isDirty_mapRef2RefFlowFunction_62 = mapRef2RefFlowFunction_62.map();
      if (isDirty_mapRef2RefFlowFunction_62) {
        mapRef2RefFlowFunction_64.inputUpdated(mapRef2RefFlowFunction_62);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_64()) {
      isDirty_mapRef2RefFlowFunction_64 = mapRef2RefFlowFunction_64.map();
      if (isDirty_mapRef2RefFlowFunction_64) {
        mapRef2RefFlowFunction_66.inputUpdated(mapRef2RefFlowFunction_64);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_66()) {
      isDirty_mapRef2RefFlowFunction_66 = mapRef2RefFlowFunction_66.map();
      if (isDirty_mapRef2RefFlowFunction_66) {
        binaryMapToRefFlowFunction_81.input2Updated(mapRef2RefFlowFunction_66);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_70()) {
      isDirty_mapRef2RefFlowFunction_70 = mapRef2RefFlowFunction_70.map();
      if (isDirty_mapRef2RefFlowFunction_70) {
        binaryMapToRefFlowFunction_75.inputUpdated(mapRef2RefFlowFunction_70);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_75()) {
      isDirty_binaryMapToRefFlowFunction_75 = binaryMapToRefFlowFunction_75.map();
      if (isDirty_binaryMapToRefFlowFunction_75) {
        mapRef2RefFlowFunction_77.inputUpdated(binaryMapToRefFlowFunction_75);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_77()) {
      isDirty_mapRef2RefFlowFunction_77 = mapRef2RefFlowFunction_77.map();
      if (isDirty_mapRef2RefFlowFunction_77) {
        mapRef2RefFlowFunction_79.inputUpdated(mapRef2RefFlowFunction_77);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_79()) {
      isDirty_mapRef2RefFlowFunction_79 = mapRef2RefFlowFunction_79.map();
      if (isDirty_mapRef2RefFlowFunction_79) {
        binaryMapToRefFlowFunction_81.inputUpdated(mapRef2RefFlowFunction_79);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_81()) {
      isDirty_binaryMapToRefFlowFunction_81 = binaryMapToRefFlowFunction_81.map();
      if (isDirty_binaryMapToRefFlowFunction_81) {
        mapRef2RefFlowFunction_83.inputUpdated(binaryMapToRefFlowFunction_81);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_83()) {
      isDirty_mapRef2RefFlowFunction_83 = mapRef2RefFlowFunction_83.map();
      if (isDirty_mapRef2RefFlowFunction_83) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_83);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_85.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_47()) {
      isDirty_pushFlowFunction_47 = pushFlowFunction_47.push();
      if (isDirty_pushFlowFunction_47) {
        binaryMapToRefFlowFunction_86.inputUpdated(pushFlowFunction_47);
      }
    }
    if (guardCheck_pushFlowFunction_85()) {
      isDirty_pushFlowFunction_85 = pushFlowFunction_85.push();
      if (isDirty_pushFlowFunction_85) {
        binaryMapToRefFlowFunction_86.input2Updated(pushFlowFunction_85);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_86()) {
      binaryMapToRefFlowFunction_86.map();
    }
    afterEvent();
  }

  public void handleEvent(InstanceCallbackEvent_1 typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_callBackNode_112 = callBackNode_112.onEvent(typedEvent);
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
        binaryMapToRefFlowFunction_25.input2Updated(mapRef2RefFlowFunction_19);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_25()) {
      isDirty_binaryMapToRefFlowFunction_25 = binaryMapToRefFlowFunction_25.map();
      if (isDirty_binaryMapToRefFlowFunction_25) {
        mapRef2RefFlowFunction_27.inputUpdated(binaryMapToRefFlowFunction_25);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_27()) {
      isDirty_mapRef2RefFlowFunction_27 = mapRef2RefFlowFunction_27.map();
      if (isDirty_mapRef2RefFlowFunction_27) {
        mapRef2RefFlowFunction_29.inputUpdated(mapRef2RefFlowFunction_27);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_29()) {
      isDirty_mapRef2RefFlowFunction_29 = mapRef2RefFlowFunction_29.map();
      if (isDirty_mapRef2RefFlowFunction_29) {
        binaryMapToRefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_29);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_42()) {
      isDirty_binaryMapToRefFlowFunction_42 = binaryMapToRefFlowFunction_42.map();
      if (isDirty_binaryMapToRefFlowFunction_42) {
        mapRef2RefFlowFunction_44.inputUpdated(binaryMapToRefFlowFunction_42);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_44()) {
      isDirty_mapRef2RefFlowFunction_44 = mapRef2RefFlowFunction_44.map();
      if (isDirty_mapRef2RefFlowFunction_44) {
        mapRef2RefFlowFunction_45.inputUpdated(mapRef2RefFlowFunction_44);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_45()) {
      isDirty_mapRef2RefFlowFunction_45 = mapRef2RefFlowFunction_45.map();
      if (isDirty_mapRef2RefFlowFunction_45) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_45);
      }
    }
    if (guardCheck_globalNetMtm()) {
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_47.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_47()) {
      isDirty_pushFlowFunction_47 = pushFlowFunction_47.push();
      if (isDirty_pushFlowFunction_47) {
        binaryMapToRefFlowFunction_86.inputUpdated(pushFlowFunction_47);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_86()) {
      binaryMapToRefFlowFunction_86.map();
    }
    afterEvent();
  }

  public void handleEvent(InstanceCallbackEvent_2 typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_callBackNode_182 = callBackNode_182.onEvent(typedEvent);
    if (guardCheck_flatMapFlowFunction_30()) {
      isDirty_flatMapFlowFunction_30 = true;
      flatMapFlowFunction_30.callbackReceived();
      if (isDirty_flatMapFlowFunction_30) {
        mapRef2RefFlowFunction_32.inputUpdated(flatMapFlowFunction_30);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_32()) {
      isDirty_mapRef2RefFlowFunction_32 = mapRef2RefFlowFunction_32.map();
      if (isDirty_mapRef2RefFlowFunction_32) {
        binaryMapToRefFlowFunction_36.input2Updated(mapRef2RefFlowFunction_32);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_36()) {
      isDirty_binaryMapToRefFlowFunction_36 = binaryMapToRefFlowFunction_36.map();
      if (isDirty_binaryMapToRefFlowFunction_36) {
        mapRef2RefFlowFunction_38.inputUpdated(binaryMapToRefFlowFunction_36);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_38()) {
      isDirty_mapRef2RefFlowFunction_38 = mapRef2RefFlowFunction_38.map();
      if (isDirty_mapRef2RefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(mapRef2RefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        binaryMapToRefFlowFunction_42.input2Updated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_42()) {
      isDirty_binaryMapToRefFlowFunction_42 = binaryMapToRefFlowFunction_42.map();
      if (isDirty_binaryMapToRefFlowFunction_42) {
        mapRef2RefFlowFunction_44.inputUpdated(binaryMapToRefFlowFunction_42);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_44()) {
      isDirty_mapRef2RefFlowFunction_44 = mapRef2RefFlowFunction_44.map();
      if (isDirty_mapRef2RefFlowFunction_44) {
        mapRef2RefFlowFunction_45.inputUpdated(mapRef2RefFlowFunction_44);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_45()) {
      isDirty_mapRef2RefFlowFunction_45 = mapRef2RefFlowFunction_45.map();
      if (isDirty_mapRef2RefFlowFunction_45) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_45);
      }
    }
    if (guardCheck_globalNetMtm()) {
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_47.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_47()) {
      isDirty_pushFlowFunction_47 = pushFlowFunction_47.push();
      if (isDirty_pushFlowFunction_47) {
        binaryMapToRefFlowFunction_86.inputUpdated(pushFlowFunction_47);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_86()) {
      binaryMapToRefFlowFunction_86.map();
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

  public void handleEvent(MidPriceBatch typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_derivedRateNode = derivedRateNode.midRateBatch(typedEvent);
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
      flatMapFlowFunction_30.inputUpdatedAndFlatMap(handlerPositionSnapshot);
      mapRef2RefFlowFunction_56.inputUpdated(handlerPositionSnapshot);
      mapRef2RefFlowFunction_71.inputUpdated(handlerPositionSnapshot);
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
        binaryMapToRefFlowFunction_60.input2Updated(mapRef2RefFlowFunction_58);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_71()) {
      isDirty_mapRef2RefFlowFunction_71 = mapRef2RefFlowFunction_71.map();
      if (isDirty_mapRef2RefFlowFunction_71) {
        mapRef2RefFlowFunction_73.inputUpdated(mapRef2RefFlowFunction_71);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_73()) {
      isDirty_mapRef2RefFlowFunction_73 = mapRef2RefFlowFunction_73.map();
      if (isDirty_mapRef2RefFlowFunction_73) {
        binaryMapToRefFlowFunction_75.input2Updated(mapRef2RefFlowFunction_73);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_60()) {
      isDirty_binaryMapToRefFlowFunction_60 = binaryMapToRefFlowFunction_60.map();
      if (isDirty_binaryMapToRefFlowFunction_60) {
        mapRef2RefFlowFunction_62.inputUpdated(binaryMapToRefFlowFunction_60);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_62()) {
      isDirty_mapRef2RefFlowFunction_62 = mapRef2RefFlowFunction_62.map();
      if (isDirty_mapRef2RefFlowFunction_62) {
        mapRef2RefFlowFunction_64.inputUpdated(mapRef2RefFlowFunction_62);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_64()) {
      isDirty_mapRef2RefFlowFunction_64 = mapRef2RefFlowFunction_64.map();
      if (isDirty_mapRef2RefFlowFunction_64) {
        mapRef2RefFlowFunction_66.inputUpdated(mapRef2RefFlowFunction_64);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_66()) {
      isDirty_mapRef2RefFlowFunction_66 = mapRef2RefFlowFunction_66.map();
      if (isDirty_mapRef2RefFlowFunction_66) {
        binaryMapToRefFlowFunction_81.input2Updated(mapRef2RefFlowFunction_66);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_75()) {
      isDirty_binaryMapToRefFlowFunction_75 = binaryMapToRefFlowFunction_75.map();
      if (isDirty_binaryMapToRefFlowFunction_75) {
        mapRef2RefFlowFunction_77.inputUpdated(binaryMapToRefFlowFunction_75);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_77()) {
      isDirty_mapRef2RefFlowFunction_77 = mapRef2RefFlowFunction_77.map();
      if (isDirty_mapRef2RefFlowFunction_77) {
        mapRef2RefFlowFunction_79.inputUpdated(mapRef2RefFlowFunction_77);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_79()) {
      isDirty_mapRef2RefFlowFunction_79 = mapRef2RefFlowFunction_79.map();
      if (isDirty_mapRef2RefFlowFunction_79) {
        binaryMapToRefFlowFunction_81.inputUpdated(mapRef2RefFlowFunction_79);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_81()) {
      isDirty_binaryMapToRefFlowFunction_81 = binaryMapToRefFlowFunction_81.map();
      if (isDirty_binaryMapToRefFlowFunction_81) {
        mapRef2RefFlowFunction_83.inputUpdated(binaryMapToRefFlowFunction_81);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_83()) {
      isDirty_mapRef2RefFlowFunction_83 = mapRef2RefFlowFunction_83.map();
      if (isDirty_mapRef2RefFlowFunction_83) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_83);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_85.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_85()) {
      isDirty_pushFlowFunction_85 = pushFlowFunction_85.push();
      if (isDirty_pushFlowFunction_85) {
        binaryMapToRefFlowFunction_86.input2Updated(pushFlowFunction_85);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_86()) {
      binaryMapToRefFlowFunction_86.map();
    }
    afterEvent();
  }

  public void handleEvent(Trade typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    isDirty_positionCache = positionCache.tradeIn(typedEvent);
    isDirty_handlerTrade = handlerTrade.onEvent(typedEvent);
    if (isDirty_handlerTrade) {
      mapRef2RefFlowFunction_5.inputUpdated(handlerTrade);
    }
    if (guardCheck_mapRef2RefFlowFunction_5()) {
      isDirty_mapRef2RefFlowFunction_5 = mapRef2RefFlowFunction_5.map();
      if (isDirty_mapRef2RefFlowFunction_5) {
        mergeFlowFunction_6.inputStreamUpdated(mapRef2RefFlowFunction_5);
      }
    }
    if (guardCheck_mergeFlowFunction_6()) {
      isDirty_mergeFlowFunction_6 = mergeFlowFunction_6.publishMerge();
      if (isDirty_mergeFlowFunction_6) {
        filterFlowFunction_8.inputUpdated(mergeFlowFunction_6);
      }
    }
    if (guardCheck_filterFlowFunction_8()) {
      isDirty_filterFlowFunction_8 = filterFlowFunction_8.filter();
      if (isDirty_filterFlowFunction_8) {
        mapRef2RefFlowFunction_10.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_12.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_14.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_16.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_34.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_49.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_51.inputUpdated(filterFlowFunction_8);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_21.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_21.input2Updated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_21()) {
      isDirty_binaryMapToRefFlowFunction_21 = binaryMapToRefFlowFunction_21.map();
      if (isDirty_binaryMapToRefFlowFunction_21) {
        mapRef2RefFlowFunction_23.inputUpdated(binaryMapToRefFlowFunction_21);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_68.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_68()) {
      isDirty_binaryMapToRefFlowFunction_68 = binaryMapToRefFlowFunction_68.map();
      if (isDirty_binaryMapToRefFlowFunction_68) {
        mapRef2RefFlowFunction_70.inputUpdated(binaryMapToRefFlowFunction_68);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_23()) {
      isDirty_mapRef2RefFlowFunction_23 = mapRef2RefFlowFunction_23.map();
      if (isDirty_mapRef2RefFlowFunction_23) {
        binaryMapToRefFlowFunction_25.inputUpdated(mapRef2RefFlowFunction_23);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_25()) {
      isDirty_binaryMapToRefFlowFunction_25 = binaryMapToRefFlowFunction_25.map();
      if (isDirty_binaryMapToRefFlowFunction_25) {
        mapRef2RefFlowFunction_27.inputUpdated(binaryMapToRefFlowFunction_25);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_27()) {
      isDirty_mapRef2RefFlowFunction_27 = mapRef2RefFlowFunction_27.map();
      if (isDirty_mapRef2RefFlowFunction_27) {
        mapRef2RefFlowFunction_29.inputUpdated(mapRef2RefFlowFunction_27);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_29()) {
      isDirty_mapRef2RefFlowFunction_29 = mapRef2RefFlowFunction_29.map();
      if (isDirty_mapRef2RefFlowFunction_29) {
        binaryMapToRefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_29);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_34()) {
      isDirty_mapRef2RefFlowFunction_34 = mapRef2RefFlowFunction_34.map();
      if (isDirty_mapRef2RefFlowFunction_34) {
        binaryMapToRefFlowFunction_36.inputUpdated(mapRef2RefFlowFunction_34);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_36()) {
      isDirty_binaryMapToRefFlowFunction_36 = binaryMapToRefFlowFunction_36.map();
      if (isDirty_binaryMapToRefFlowFunction_36) {
        mapRef2RefFlowFunction_38.inputUpdated(binaryMapToRefFlowFunction_36);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_38()) {
      isDirty_mapRef2RefFlowFunction_38 = mapRef2RefFlowFunction_38.map();
      if (isDirty_mapRef2RefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(mapRef2RefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        binaryMapToRefFlowFunction_42.input2Updated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_42()) {
      isDirty_binaryMapToRefFlowFunction_42 = binaryMapToRefFlowFunction_42.map();
      if (isDirty_binaryMapToRefFlowFunction_42) {
        mapRef2RefFlowFunction_44.inputUpdated(binaryMapToRefFlowFunction_42);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_44()) {
      isDirty_mapRef2RefFlowFunction_44 = mapRef2RefFlowFunction_44.map();
      if (isDirty_mapRef2RefFlowFunction_44) {
        mapRef2RefFlowFunction_45.inputUpdated(mapRef2RefFlowFunction_44);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_45()) {
      isDirty_mapRef2RefFlowFunction_45 = mapRef2RefFlowFunction_45.map();
      if (isDirty_mapRef2RefFlowFunction_45) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_45);
      }
    }
    if (guardCheck_globalNetMtm()) {
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_47.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_49()) {
      isDirty_mapRef2RefFlowFunction_49 = mapRef2RefFlowFunction_49.map();
      if (isDirty_mapRef2RefFlowFunction_49) {
        binaryMapToRefFlowFunction_53.inputUpdated(mapRef2RefFlowFunction_49);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_51()) {
      isDirty_mapRef2RefFlowFunction_51 = mapRef2RefFlowFunction_51.map();
      if (isDirty_mapRef2RefFlowFunction_51) {
        binaryMapToRefFlowFunction_53.input2Updated(mapRef2RefFlowFunction_51);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_53()) {
      isDirty_binaryMapToRefFlowFunction_53 = binaryMapToRefFlowFunction_53.map();
      if (isDirty_binaryMapToRefFlowFunction_53) {
        mapRef2RefFlowFunction_55.inputUpdated(binaryMapToRefFlowFunction_53);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_55()) {
      isDirty_mapRef2RefFlowFunction_55 = mapRef2RefFlowFunction_55.map();
      if (isDirty_mapRef2RefFlowFunction_55) {
        binaryMapToRefFlowFunction_60.inputUpdated(mapRef2RefFlowFunction_55);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_60()) {
      isDirty_binaryMapToRefFlowFunction_60 = binaryMapToRefFlowFunction_60.map();
      if (isDirty_binaryMapToRefFlowFunction_60) {
        mapRef2RefFlowFunction_62.inputUpdated(binaryMapToRefFlowFunction_60);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_62()) {
      isDirty_mapRef2RefFlowFunction_62 = mapRef2RefFlowFunction_62.map();
      if (isDirty_mapRef2RefFlowFunction_62) {
        mapRef2RefFlowFunction_64.inputUpdated(mapRef2RefFlowFunction_62);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_64()) {
      isDirty_mapRef2RefFlowFunction_64 = mapRef2RefFlowFunction_64.map();
      if (isDirty_mapRef2RefFlowFunction_64) {
        mapRef2RefFlowFunction_66.inputUpdated(mapRef2RefFlowFunction_64);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_66()) {
      isDirty_mapRef2RefFlowFunction_66 = mapRef2RefFlowFunction_66.map();
      if (isDirty_mapRef2RefFlowFunction_66) {
        binaryMapToRefFlowFunction_81.input2Updated(mapRef2RefFlowFunction_66);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_70()) {
      isDirty_mapRef2RefFlowFunction_70 = mapRef2RefFlowFunction_70.map();
      if (isDirty_mapRef2RefFlowFunction_70) {
        binaryMapToRefFlowFunction_75.inputUpdated(mapRef2RefFlowFunction_70);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_75()) {
      isDirty_binaryMapToRefFlowFunction_75 = binaryMapToRefFlowFunction_75.map();
      if (isDirty_binaryMapToRefFlowFunction_75) {
        mapRef2RefFlowFunction_77.inputUpdated(binaryMapToRefFlowFunction_75);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_77()) {
      isDirty_mapRef2RefFlowFunction_77 = mapRef2RefFlowFunction_77.map();
      if (isDirty_mapRef2RefFlowFunction_77) {
        mapRef2RefFlowFunction_79.inputUpdated(mapRef2RefFlowFunction_77);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_79()) {
      isDirty_mapRef2RefFlowFunction_79 = mapRef2RefFlowFunction_79.map();
      if (isDirty_mapRef2RefFlowFunction_79) {
        binaryMapToRefFlowFunction_81.inputUpdated(mapRef2RefFlowFunction_79);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_81()) {
      isDirty_binaryMapToRefFlowFunction_81 = binaryMapToRefFlowFunction_81.map();
      if (isDirty_binaryMapToRefFlowFunction_81) {
        mapRef2RefFlowFunction_83.inputUpdated(binaryMapToRefFlowFunction_81);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_83()) {
      isDirty_mapRef2RefFlowFunction_83 = mapRef2RefFlowFunction_83.map();
      if (isDirty_mapRef2RefFlowFunction_83) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_83);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_85.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_47()) {
      isDirty_pushFlowFunction_47 = pushFlowFunction_47.push();
      if (isDirty_pushFlowFunction_47) {
        binaryMapToRefFlowFunction_86.inputUpdated(pushFlowFunction_47);
      }
    }
    if (guardCheck_pushFlowFunction_85()) {
      isDirty_pushFlowFunction_85 = pushFlowFunction_85.push();
      if (isDirty_pushFlowFunction_85) {
        binaryMapToRefFlowFunction_86.input2Updated(pushFlowFunction_85);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_86()) {
      binaryMapToRefFlowFunction_86.map();
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
      mapRef2RefFlowFunction_19.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_32.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_10.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_12.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_14.publishTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_14.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_16.publishTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_16.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_38.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_62.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
    }
    if (guardCheck_mapRef2RefFlowFunction_19()) {
      isDirty_mapRef2RefFlowFunction_19 = mapRef2RefFlowFunction_19.map();
      if (isDirty_mapRef2RefFlowFunction_19) {
        binaryMapToRefFlowFunction_25.input2Updated(mapRef2RefFlowFunction_19);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_32()) {
      isDirty_mapRef2RefFlowFunction_32 = mapRef2RefFlowFunction_32.map();
      if (isDirty_mapRef2RefFlowFunction_32) {
        binaryMapToRefFlowFunction_36.input2Updated(mapRef2RefFlowFunction_32);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_21.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_21.input2Updated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_21()) {
      isDirty_binaryMapToRefFlowFunction_21 = binaryMapToRefFlowFunction_21.map();
      if (isDirty_binaryMapToRefFlowFunction_21) {
        mapRef2RefFlowFunction_23.inputUpdated(binaryMapToRefFlowFunction_21);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_68.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_68()) {
      isDirty_binaryMapToRefFlowFunction_68 = binaryMapToRefFlowFunction_68.map();
      if (isDirty_binaryMapToRefFlowFunction_68) {
        mapRef2RefFlowFunction_70.inputUpdated(binaryMapToRefFlowFunction_68);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_23()) {
      isDirty_mapRef2RefFlowFunction_23 = mapRef2RefFlowFunction_23.map();
      if (isDirty_mapRef2RefFlowFunction_23) {
        binaryMapToRefFlowFunction_25.inputUpdated(mapRef2RefFlowFunction_23);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_25()) {
      isDirty_binaryMapToRefFlowFunction_25 = binaryMapToRefFlowFunction_25.map();
      if (isDirty_binaryMapToRefFlowFunction_25) {
        mapRef2RefFlowFunction_27.inputUpdated(binaryMapToRefFlowFunction_25);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_27()) {
      isDirty_mapRef2RefFlowFunction_27 = mapRef2RefFlowFunction_27.map();
      if (isDirty_mapRef2RefFlowFunction_27) {
        mapRef2RefFlowFunction_29.inputUpdated(mapRef2RefFlowFunction_27);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_29()) {
      isDirty_mapRef2RefFlowFunction_29 = mapRef2RefFlowFunction_29.map();
      if (isDirty_mapRef2RefFlowFunction_29) {
        binaryMapToRefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_29);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_36()) {
      isDirty_binaryMapToRefFlowFunction_36 = binaryMapToRefFlowFunction_36.map();
      if (isDirty_binaryMapToRefFlowFunction_36) {
        mapRef2RefFlowFunction_38.inputUpdated(binaryMapToRefFlowFunction_36);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_38()) {
      isDirty_mapRef2RefFlowFunction_38 = mapRef2RefFlowFunction_38.map();
      if (isDirty_mapRef2RefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(mapRef2RefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        binaryMapToRefFlowFunction_42.input2Updated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_42()) {
      isDirty_binaryMapToRefFlowFunction_42 = binaryMapToRefFlowFunction_42.map();
      if (isDirty_binaryMapToRefFlowFunction_42) {
        mapRef2RefFlowFunction_44.inputUpdated(binaryMapToRefFlowFunction_42);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_44()) {
      isDirty_mapRef2RefFlowFunction_44 = mapRef2RefFlowFunction_44.map();
      if (isDirty_mapRef2RefFlowFunction_44) {
        mapRef2RefFlowFunction_45.inputUpdated(mapRef2RefFlowFunction_44);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_45()) {
      isDirty_mapRef2RefFlowFunction_45 = mapRef2RefFlowFunction_45.map();
      if (isDirty_mapRef2RefFlowFunction_45) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_45);
      }
    }
    if (guardCheck_globalNetMtm()) {
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_47.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_62()) {
      isDirty_mapRef2RefFlowFunction_62 = mapRef2RefFlowFunction_62.map();
      if (isDirty_mapRef2RefFlowFunction_62) {
        mapRef2RefFlowFunction_64.inputUpdated(mapRef2RefFlowFunction_62);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_64()) {
      isDirty_mapRef2RefFlowFunction_64 = mapRef2RefFlowFunction_64.map();
      if (isDirty_mapRef2RefFlowFunction_64) {
        mapRef2RefFlowFunction_66.inputUpdated(mapRef2RefFlowFunction_64);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_66()) {
      isDirty_mapRef2RefFlowFunction_66 = mapRef2RefFlowFunction_66.map();
      if (isDirty_mapRef2RefFlowFunction_66) {
        binaryMapToRefFlowFunction_81.input2Updated(mapRef2RefFlowFunction_66);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_70()) {
      isDirty_mapRef2RefFlowFunction_70 = mapRef2RefFlowFunction_70.map();
      if (isDirty_mapRef2RefFlowFunction_70) {
        binaryMapToRefFlowFunction_75.inputUpdated(mapRef2RefFlowFunction_70);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_75()) {
      isDirty_binaryMapToRefFlowFunction_75 = binaryMapToRefFlowFunction_75.map();
      if (isDirty_binaryMapToRefFlowFunction_75) {
        mapRef2RefFlowFunction_77.inputUpdated(binaryMapToRefFlowFunction_75);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_77()) {
      isDirty_mapRef2RefFlowFunction_77 = mapRef2RefFlowFunction_77.map();
      if (isDirty_mapRef2RefFlowFunction_77) {
        mapRef2RefFlowFunction_79.inputUpdated(mapRef2RefFlowFunction_77);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_79()) {
      isDirty_mapRef2RefFlowFunction_79 = mapRef2RefFlowFunction_79.map();
      if (isDirty_mapRef2RefFlowFunction_79) {
        binaryMapToRefFlowFunction_81.inputUpdated(mapRef2RefFlowFunction_79);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_81()) {
      isDirty_binaryMapToRefFlowFunction_81 = binaryMapToRefFlowFunction_81.map();
      if (isDirty_binaryMapToRefFlowFunction_81) {
        mapRef2RefFlowFunction_83.inputUpdated(binaryMapToRefFlowFunction_81);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_83()) {
      isDirty_mapRef2RefFlowFunction_83 = mapRef2RefFlowFunction_83.map();
      if (isDirty_mapRef2RefFlowFunction_83) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_83);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_85.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_47()) {
      isDirty_pushFlowFunction_47 = pushFlowFunction_47.push();
      if (isDirty_pushFlowFunction_47) {
        binaryMapToRefFlowFunction_86.inputUpdated(pushFlowFunction_47);
      }
    }
    if (guardCheck_pushFlowFunction_85()) {
      isDirty_pushFlowFunction_85 = pushFlowFunction_85.push();
      if (isDirty_pushFlowFunction_85) {
        binaryMapToRefFlowFunction_86.input2Updated(pushFlowFunction_85);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_86()) {
      binaryMapToRefFlowFunction_86.map();
    }
  }

  private void handle_Signal_positionUpdate(Signal typedEvent) {
    isDirty_handlerSignal_positionUpdate = handlerSignal_positionUpdate.onEvent(typedEvent);
    if (isDirty_handlerSignal_positionUpdate) {
      mapRef2RefFlowFunction_19.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_32.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_23.publishTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_29.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_29.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_34.publishTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_40.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_40.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_44.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_55.publishTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_66.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_66.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_70.publishTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_79.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_79.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_83.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
    }
    if (guardCheck_mapRef2RefFlowFunction_19()) {
      isDirty_mapRef2RefFlowFunction_19 = mapRef2RefFlowFunction_19.map();
      if (isDirty_mapRef2RefFlowFunction_19) {
        binaryMapToRefFlowFunction_25.input2Updated(mapRef2RefFlowFunction_19);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_32()) {
      isDirty_mapRef2RefFlowFunction_32 = mapRef2RefFlowFunction_32.map();
      if (isDirty_mapRef2RefFlowFunction_32) {
        binaryMapToRefFlowFunction_36.input2Updated(mapRef2RefFlowFunction_32);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_23()) {
      isDirty_mapRef2RefFlowFunction_23 = mapRef2RefFlowFunction_23.map();
      if (isDirty_mapRef2RefFlowFunction_23) {
        binaryMapToRefFlowFunction_25.inputUpdated(mapRef2RefFlowFunction_23);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_25()) {
      isDirty_binaryMapToRefFlowFunction_25 = binaryMapToRefFlowFunction_25.map();
      if (isDirty_binaryMapToRefFlowFunction_25) {
        mapRef2RefFlowFunction_27.inputUpdated(binaryMapToRefFlowFunction_25);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_27()) {
      isDirty_mapRef2RefFlowFunction_27 = mapRef2RefFlowFunction_27.map();
      if (isDirty_mapRef2RefFlowFunction_27) {
        mapRef2RefFlowFunction_29.inputUpdated(mapRef2RefFlowFunction_27);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_29()) {
      isDirty_mapRef2RefFlowFunction_29 = mapRef2RefFlowFunction_29.map();
      if (isDirty_mapRef2RefFlowFunction_29) {
        binaryMapToRefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_29);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_34()) {
      isDirty_mapRef2RefFlowFunction_34 = mapRef2RefFlowFunction_34.map();
      if (isDirty_mapRef2RefFlowFunction_34) {
        binaryMapToRefFlowFunction_36.inputUpdated(mapRef2RefFlowFunction_34);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_36()) {
      isDirty_binaryMapToRefFlowFunction_36 = binaryMapToRefFlowFunction_36.map();
      if (isDirty_binaryMapToRefFlowFunction_36) {
        mapRef2RefFlowFunction_38.inputUpdated(binaryMapToRefFlowFunction_36);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_38()) {
      isDirty_mapRef2RefFlowFunction_38 = mapRef2RefFlowFunction_38.map();
      if (isDirty_mapRef2RefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(mapRef2RefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        binaryMapToRefFlowFunction_42.input2Updated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_42()) {
      isDirty_binaryMapToRefFlowFunction_42 = binaryMapToRefFlowFunction_42.map();
      if (isDirty_binaryMapToRefFlowFunction_42) {
        mapRef2RefFlowFunction_44.inputUpdated(binaryMapToRefFlowFunction_42);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_44()) {
      isDirty_mapRef2RefFlowFunction_44 = mapRef2RefFlowFunction_44.map();
      if (isDirty_mapRef2RefFlowFunction_44) {
        mapRef2RefFlowFunction_45.inputUpdated(mapRef2RefFlowFunction_44);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_45()) {
      isDirty_mapRef2RefFlowFunction_45 = mapRef2RefFlowFunction_45.map();
      if (isDirty_mapRef2RefFlowFunction_45) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_45);
      }
    }
    if (guardCheck_globalNetMtm()) {
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_47.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_55()) {
      isDirty_mapRef2RefFlowFunction_55 = mapRef2RefFlowFunction_55.map();
      if (isDirty_mapRef2RefFlowFunction_55) {
        binaryMapToRefFlowFunction_60.inputUpdated(mapRef2RefFlowFunction_55);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_60()) {
      isDirty_binaryMapToRefFlowFunction_60 = binaryMapToRefFlowFunction_60.map();
      if (isDirty_binaryMapToRefFlowFunction_60) {
        mapRef2RefFlowFunction_62.inputUpdated(binaryMapToRefFlowFunction_60);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_62()) {
      isDirty_mapRef2RefFlowFunction_62 = mapRef2RefFlowFunction_62.map();
      if (isDirty_mapRef2RefFlowFunction_62) {
        mapRef2RefFlowFunction_64.inputUpdated(mapRef2RefFlowFunction_62);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_64()) {
      isDirty_mapRef2RefFlowFunction_64 = mapRef2RefFlowFunction_64.map();
      if (isDirty_mapRef2RefFlowFunction_64) {
        mapRef2RefFlowFunction_66.inputUpdated(mapRef2RefFlowFunction_64);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_66()) {
      isDirty_mapRef2RefFlowFunction_66 = mapRef2RefFlowFunction_66.map();
      if (isDirty_mapRef2RefFlowFunction_66) {
        binaryMapToRefFlowFunction_81.input2Updated(mapRef2RefFlowFunction_66);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_70()) {
      isDirty_mapRef2RefFlowFunction_70 = mapRef2RefFlowFunction_70.map();
      if (isDirty_mapRef2RefFlowFunction_70) {
        binaryMapToRefFlowFunction_75.inputUpdated(mapRef2RefFlowFunction_70);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_75()) {
      isDirty_binaryMapToRefFlowFunction_75 = binaryMapToRefFlowFunction_75.map();
      if (isDirty_binaryMapToRefFlowFunction_75) {
        mapRef2RefFlowFunction_77.inputUpdated(binaryMapToRefFlowFunction_75);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_77()) {
      isDirty_mapRef2RefFlowFunction_77 = mapRef2RefFlowFunction_77.map();
      if (isDirty_mapRef2RefFlowFunction_77) {
        mapRef2RefFlowFunction_79.inputUpdated(mapRef2RefFlowFunction_77);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_79()) {
      isDirty_mapRef2RefFlowFunction_79 = mapRef2RefFlowFunction_79.map();
      if (isDirty_mapRef2RefFlowFunction_79) {
        binaryMapToRefFlowFunction_81.inputUpdated(mapRef2RefFlowFunction_79);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_81()) {
      isDirty_binaryMapToRefFlowFunction_81 = binaryMapToRefFlowFunction_81.map();
      if (isDirty_binaryMapToRefFlowFunction_81) {
        mapRef2RefFlowFunction_83.inputUpdated(binaryMapToRefFlowFunction_81);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_83()) {
      isDirty_mapRef2RefFlowFunction_83 = mapRef2RefFlowFunction_83.map();
      if (isDirty_mapRef2RefFlowFunction_83) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_83);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_85.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_47()) {
      isDirty_pushFlowFunction_47 = pushFlowFunction_47.push();
      if (isDirty_pushFlowFunction_47) {
        binaryMapToRefFlowFunction_86.inputUpdated(pushFlowFunction_47);
      }
    }
    if (guardCheck_pushFlowFunction_85()) {
      isDirty_pushFlowFunction_85 = pushFlowFunction_85.push();
      if (isDirty_pushFlowFunction_85) {
        binaryMapToRefFlowFunction_86.input2Updated(pushFlowFunction_85);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_86()) {
      binaryMapToRefFlowFunction_86.map();
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

  private void handle_NamedFeedEvent_symbolFeed(NamedFeedEvent typedEvent) {
    isDirty_namedFeedTableNode_87 = namedFeedTableNode_87.tableUpdate(typedEvent);
    if (guardCheck_mapRef2RefFlowFunction_4()) {
      isDirty_mapRef2RefFlowFunction_4 = mapRef2RefFlowFunction_4.map();
      if (isDirty_mapRef2RefFlowFunction_4) {
        mergeFlowFunction_6.inputStreamUpdated(mapRef2RefFlowFunction_4);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_5()) {
      isDirty_mapRef2RefFlowFunction_5 = mapRef2RefFlowFunction_5.map();
      if (isDirty_mapRef2RefFlowFunction_5) {
        mergeFlowFunction_6.inputStreamUpdated(mapRef2RefFlowFunction_5);
      }
    }
    if (guardCheck_mergeFlowFunction_6()) {
      isDirty_mergeFlowFunction_6 = mergeFlowFunction_6.publishMerge();
      if (isDirty_mergeFlowFunction_6) {
        filterFlowFunction_8.inputUpdated(mergeFlowFunction_6);
      }
    }
    if (guardCheck_filterFlowFunction_8()) {
      isDirty_filterFlowFunction_8 = filterFlowFunction_8.filter();
      if (isDirty_filterFlowFunction_8) {
        mapRef2RefFlowFunction_10.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_12.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_14.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_16.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_34.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_49.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_51.inputUpdated(filterFlowFunction_8);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_21.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_21.input2Updated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_21()) {
      isDirty_binaryMapToRefFlowFunction_21 = binaryMapToRefFlowFunction_21.map();
      if (isDirty_binaryMapToRefFlowFunction_21) {
        mapRef2RefFlowFunction_23.inputUpdated(binaryMapToRefFlowFunction_21);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_68.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_68()) {
      isDirty_binaryMapToRefFlowFunction_68 = binaryMapToRefFlowFunction_68.map();
      if (isDirty_binaryMapToRefFlowFunction_68) {
        mapRef2RefFlowFunction_70.inputUpdated(binaryMapToRefFlowFunction_68);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_23()) {
      isDirty_mapRef2RefFlowFunction_23 = mapRef2RefFlowFunction_23.map();
      if (isDirty_mapRef2RefFlowFunction_23) {
        binaryMapToRefFlowFunction_25.inputUpdated(mapRef2RefFlowFunction_23);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_25()) {
      isDirty_binaryMapToRefFlowFunction_25 = binaryMapToRefFlowFunction_25.map();
      if (isDirty_binaryMapToRefFlowFunction_25) {
        mapRef2RefFlowFunction_27.inputUpdated(binaryMapToRefFlowFunction_25);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_27()) {
      isDirty_mapRef2RefFlowFunction_27 = mapRef2RefFlowFunction_27.map();
      if (isDirty_mapRef2RefFlowFunction_27) {
        mapRef2RefFlowFunction_29.inputUpdated(mapRef2RefFlowFunction_27);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_29()) {
      isDirty_mapRef2RefFlowFunction_29 = mapRef2RefFlowFunction_29.map();
      if (isDirty_mapRef2RefFlowFunction_29) {
        binaryMapToRefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_29);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_34()) {
      isDirty_mapRef2RefFlowFunction_34 = mapRef2RefFlowFunction_34.map();
      if (isDirty_mapRef2RefFlowFunction_34) {
        binaryMapToRefFlowFunction_36.inputUpdated(mapRef2RefFlowFunction_34);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_36()) {
      isDirty_binaryMapToRefFlowFunction_36 = binaryMapToRefFlowFunction_36.map();
      if (isDirty_binaryMapToRefFlowFunction_36) {
        mapRef2RefFlowFunction_38.inputUpdated(binaryMapToRefFlowFunction_36);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_38()) {
      isDirty_mapRef2RefFlowFunction_38 = mapRef2RefFlowFunction_38.map();
      if (isDirty_mapRef2RefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(mapRef2RefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        binaryMapToRefFlowFunction_42.input2Updated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_42()) {
      isDirty_binaryMapToRefFlowFunction_42 = binaryMapToRefFlowFunction_42.map();
      if (isDirty_binaryMapToRefFlowFunction_42) {
        mapRef2RefFlowFunction_44.inputUpdated(binaryMapToRefFlowFunction_42);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_44()) {
      isDirty_mapRef2RefFlowFunction_44 = mapRef2RefFlowFunction_44.map();
      if (isDirty_mapRef2RefFlowFunction_44) {
        mapRef2RefFlowFunction_45.inputUpdated(mapRef2RefFlowFunction_44);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_45()) {
      isDirty_mapRef2RefFlowFunction_45 = mapRef2RefFlowFunction_45.map();
      if (isDirty_mapRef2RefFlowFunction_45) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_45);
      }
    }
    if (guardCheck_globalNetMtm()) {
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_47.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_49()) {
      isDirty_mapRef2RefFlowFunction_49 = mapRef2RefFlowFunction_49.map();
      if (isDirty_mapRef2RefFlowFunction_49) {
        binaryMapToRefFlowFunction_53.inputUpdated(mapRef2RefFlowFunction_49);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_51()) {
      isDirty_mapRef2RefFlowFunction_51 = mapRef2RefFlowFunction_51.map();
      if (isDirty_mapRef2RefFlowFunction_51) {
        binaryMapToRefFlowFunction_53.input2Updated(mapRef2RefFlowFunction_51);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_53()) {
      isDirty_binaryMapToRefFlowFunction_53 = binaryMapToRefFlowFunction_53.map();
      if (isDirty_binaryMapToRefFlowFunction_53) {
        mapRef2RefFlowFunction_55.inputUpdated(binaryMapToRefFlowFunction_53);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_55()) {
      isDirty_mapRef2RefFlowFunction_55 = mapRef2RefFlowFunction_55.map();
      if (isDirty_mapRef2RefFlowFunction_55) {
        binaryMapToRefFlowFunction_60.inputUpdated(mapRef2RefFlowFunction_55);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_60()) {
      isDirty_binaryMapToRefFlowFunction_60 = binaryMapToRefFlowFunction_60.map();
      if (isDirty_binaryMapToRefFlowFunction_60) {
        mapRef2RefFlowFunction_62.inputUpdated(binaryMapToRefFlowFunction_60);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_62()) {
      isDirty_mapRef2RefFlowFunction_62 = mapRef2RefFlowFunction_62.map();
      if (isDirty_mapRef2RefFlowFunction_62) {
        mapRef2RefFlowFunction_64.inputUpdated(mapRef2RefFlowFunction_62);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_64()) {
      isDirty_mapRef2RefFlowFunction_64 = mapRef2RefFlowFunction_64.map();
      if (isDirty_mapRef2RefFlowFunction_64) {
        mapRef2RefFlowFunction_66.inputUpdated(mapRef2RefFlowFunction_64);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_66()) {
      isDirty_mapRef2RefFlowFunction_66 = mapRef2RefFlowFunction_66.map();
      if (isDirty_mapRef2RefFlowFunction_66) {
        binaryMapToRefFlowFunction_81.input2Updated(mapRef2RefFlowFunction_66);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_70()) {
      isDirty_mapRef2RefFlowFunction_70 = mapRef2RefFlowFunction_70.map();
      if (isDirty_mapRef2RefFlowFunction_70) {
        binaryMapToRefFlowFunction_75.inputUpdated(mapRef2RefFlowFunction_70);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_75()) {
      isDirty_binaryMapToRefFlowFunction_75 = binaryMapToRefFlowFunction_75.map();
      if (isDirty_binaryMapToRefFlowFunction_75) {
        mapRef2RefFlowFunction_77.inputUpdated(binaryMapToRefFlowFunction_75);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_77()) {
      isDirty_mapRef2RefFlowFunction_77 = mapRef2RefFlowFunction_77.map();
      if (isDirty_mapRef2RefFlowFunction_77) {
        mapRef2RefFlowFunction_79.inputUpdated(mapRef2RefFlowFunction_77);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_79()) {
      isDirty_mapRef2RefFlowFunction_79 = mapRef2RefFlowFunction_79.map();
      if (isDirty_mapRef2RefFlowFunction_79) {
        binaryMapToRefFlowFunction_81.inputUpdated(mapRef2RefFlowFunction_79);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_81()) {
      isDirty_binaryMapToRefFlowFunction_81 = binaryMapToRefFlowFunction_81.map();
      if (isDirty_binaryMapToRefFlowFunction_81) {
        mapRef2RefFlowFunction_83.inputUpdated(binaryMapToRefFlowFunction_81);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_83()) {
      isDirty_mapRef2RefFlowFunction_83 = mapRef2RefFlowFunction_83.map();
      if (isDirty_mapRef2RefFlowFunction_83) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_83);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_85.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_47()) {
      isDirty_pushFlowFunction_47 = pushFlowFunction_47.push();
      if (isDirty_pushFlowFunction_47) {
        binaryMapToRefFlowFunction_86.inputUpdated(pushFlowFunction_47);
      }
    }
    if (guardCheck_pushFlowFunction_85()) {
      isDirty_pushFlowFunction_85 = pushFlowFunction_85.push();
      if (isDirty_pushFlowFunction_85) {
        binaryMapToRefFlowFunction_86.input2Updated(pushFlowFunction_85);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_86()) {
      binaryMapToRefFlowFunction_86.map();
    }
  }
  //FILTERED DISPATCH - END

  //EXPORTED SERVICE FUNCTIONS - START
  @Override
  public boolean configChanged(com.fluxtion.server.config.ConfigUpdate arg0) {
    beforeServiceCall(
        "public default boolean com.fluxtion.server.config.ConfigListener.configChanged(com.fluxtion.server.config.ConfigUpdate)");
    ExportFunctionAuditEvent typedEvent = functionAudit;
    isDirty_eventFeedBatcher = eventFeedBatcher.configChanged(arg0);
    if (guardCheck_mapRef2RefFlowFunction_4()) {
      isDirty_mapRef2RefFlowFunction_4 = mapRef2RefFlowFunction_4.map();
      if (isDirty_mapRef2RefFlowFunction_4) {
        mergeFlowFunction_6.inputStreamUpdated(mapRef2RefFlowFunction_4);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_5()) {
      isDirty_mapRef2RefFlowFunction_5 = mapRef2RefFlowFunction_5.map();
      if (isDirty_mapRef2RefFlowFunction_5) {
        mergeFlowFunction_6.inputStreamUpdated(mapRef2RefFlowFunction_5);
      }
    }
    if (guardCheck_mergeFlowFunction_6()) {
      isDirty_mergeFlowFunction_6 = mergeFlowFunction_6.publishMerge();
      if (isDirty_mergeFlowFunction_6) {
        filterFlowFunction_8.inputUpdated(mergeFlowFunction_6);
      }
    }
    if (guardCheck_filterFlowFunction_8()) {
      isDirty_filterFlowFunction_8 = filterFlowFunction_8.filter();
      if (isDirty_filterFlowFunction_8) {
        mapRef2RefFlowFunction_10.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_12.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_14.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_16.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_34.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_49.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_51.inputUpdated(filterFlowFunction_8);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_21.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_21.input2Updated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_21()) {
      isDirty_binaryMapToRefFlowFunction_21 = binaryMapToRefFlowFunction_21.map();
      if (isDirty_binaryMapToRefFlowFunction_21) {
        mapRef2RefFlowFunction_23.inputUpdated(binaryMapToRefFlowFunction_21);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_68.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_68()) {
      isDirty_binaryMapToRefFlowFunction_68 = binaryMapToRefFlowFunction_68.map();
      if (isDirty_binaryMapToRefFlowFunction_68) {
        mapRef2RefFlowFunction_70.inputUpdated(binaryMapToRefFlowFunction_68);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_23()) {
      isDirty_mapRef2RefFlowFunction_23 = mapRef2RefFlowFunction_23.map();
      if (isDirty_mapRef2RefFlowFunction_23) {
        binaryMapToRefFlowFunction_25.inputUpdated(mapRef2RefFlowFunction_23);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_25()) {
      isDirty_binaryMapToRefFlowFunction_25 = binaryMapToRefFlowFunction_25.map();
      if (isDirty_binaryMapToRefFlowFunction_25) {
        mapRef2RefFlowFunction_27.inputUpdated(binaryMapToRefFlowFunction_25);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_27()) {
      isDirty_mapRef2RefFlowFunction_27 = mapRef2RefFlowFunction_27.map();
      if (isDirty_mapRef2RefFlowFunction_27) {
        mapRef2RefFlowFunction_29.inputUpdated(mapRef2RefFlowFunction_27);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_29()) {
      isDirty_mapRef2RefFlowFunction_29 = mapRef2RefFlowFunction_29.map();
      if (isDirty_mapRef2RefFlowFunction_29) {
        binaryMapToRefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_29);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_34()) {
      isDirty_mapRef2RefFlowFunction_34 = mapRef2RefFlowFunction_34.map();
      if (isDirty_mapRef2RefFlowFunction_34) {
        binaryMapToRefFlowFunction_36.inputUpdated(mapRef2RefFlowFunction_34);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_36()) {
      isDirty_binaryMapToRefFlowFunction_36 = binaryMapToRefFlowFunction_36.map();
      if (isDirty_binaryMapToRefFlowFunction_36) {
        mapRef2RefFlowFunction_38.inputUpdated(binaryMapToRefFlowFunction_36);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_38()) {
      isDirty_mapRef2RefFlowFunction_38 = mapRef2RefFlowFunction_38.map();
      if (isDirty_mapRef2RefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(mapRef2RefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        binaryMapToRefFlowFunction_42.input2Updated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_42()) {
      isDirty_binaryMapToRefFlowFunction_42 = binaryMapToRefFlowFunction_42.map();
      if (isDirty_binaryMapToRefFlowFunction_42) {
        mapRef2RefFlowFunction_44.inputUpdated(binaryMapToRefFlowFunction_42);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_44()) {
      isDirty_mapRef2RefFlowFunction_44 = mapRef2RefFlowFunction_44.map();
      if (isDirty_mapRef2RefFlowFunction_44) {
        mapRef2RefFlowFunction_45.inputUpdated(mapRef2RefFlowFunction_44);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_45()) {
      isDirty_mapRef2RefFlowFunction_45 = mapRef2RefFlowFunction_45.map();
      if (isDirty_mapRef2RefFlowFunction_45) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_45);
      }
    }
    if (guardCheck_globalNetMtm()) {
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_47.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_49()) {
      isDirty_mapRef2RefFlowFunction_49 = mapRef2RefFlowFunction_49.map();
      if (isDirty_mapRef2RefFlowFunction_49) {
        binaryMapToRefFlowFunction_53.inputUpdated(mapRef2RefFlowFunction_49);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_51()) {
      isDirty_mapRef2RefFlowFunction_51 = mapRef2RefFlowFunction_51.map();
      if (isDirty_mapRef2RefFlowFunction_51) {
        binaryMapToRefFlowFunction_53.input2Updated(mapRef2RefFlowFunction_51);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_53()) {
      isDirty_binaryMapToRefFlowFunction_53 = binaryMapToRefFlowFunction_53.map();
      if (isDirty_binaryMapToRefFlowFunction_53) {
        mapRef2RefFlowFunction_55.inputUpdated(binaryMapToRefFlowFunction_53);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_55()) {
      isDirty_mapRef2RefFlowFunction_55 = mapRef2RefFlowFunction_55.map();
      if (isDirty_mapRef2RefFlowFunction_55) {
        binaryMapToRefFlowFunction_60.inputUpdated(mapRef2RefFlowFunction_55);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_60()) {
      isDirty_binaryMapToRefFlowFunction_60 = binaryMapToRefFlowFunction_60.map();
      if (isDirty_binaryMapToRefFlowFunction_60) {
        mapRef2RefFlowFunction_62.inputUpdated(binaryMapToRefFlowFunction_60);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_62()) {
      isDirty_mapRef2RefFlowFunction_62 = mapRef2RefFlowFunction_62.map();
      if (isDirty_mapRef2RefFlowFunction_62) {
        mapRef2RefFlowFunction_64.inputUpdated(mapRef2RefFlowFunction_62);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_64()) {
      isDirty_mapRef2RefFlowFunction_64 = mapRef2RefFlowFunction_64.map();
      if (isDirty_mapRef2RefFlowFunction_64) {
        mapRef2RefFlowFunction_66.inputUpdated(mapRef2RefFlowFunction_64);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_66()) {
      isDirty_mapRef2RefFlowFunction_66 = mapRef2RefFlowFunction_66.map();
      if (isDirty_mapRef2RefFlowFunction_66) {
        binaryMapToRefFlowFunction_81.input2Updated(mapRef2RefFlowFunction_66);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_70()) {
      isDirty_mapRef2RefFlowFunction_70 = mapRef2RefFlowFunction_70.map();
      if (isDirty_mapRef2RefFlowFunction_70) {
        binaryMapToRefFlowFunction_75.inputUpdated(mapRef2RefFlowFunction_70);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_75()) {
      isDirty_binaryMapToRefFlowFunction_75 = binaryMapToRefFlowFunction_75.map();
      if (isDirty_binaryMapToRefFlowFunction_75) {
        mapRef2RefFlowFunction_77.inputUpdated(binaryMapToRefFlowFunction_75);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_77()) {
      isDirty_mapRef2RefFlowFunction_77 = mapRef2RefFlowFunction_77.map();
      if (isDirty_mapRef2RefFlowFunction_77) {
        mapRef2RefFlowFunction_79.inputUpdated(mapRef2RefFlowFunction_77);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_79()) {
      isDirty_mapRef2RefFlowFunction_79 = mapRef2RefFlowFunction_79.map();
      if (isDirty_mapRef2RefFlowFunction_79) {
        binaryMapToRefFlowFunction_81.inputUpdated(mapRef2RefFlowFunction_79);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_81()) {
      isDirty_binaryMapToRefFlowFunction_81 = binaryMapToRefFlowFunction_81.map();
      if (isDirty_binaryMapToRefFlowFunction_81) {
        mapRef2RefFlowFunction_83.inputUpdated(binaryMapToRefFlowFunction_81);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_83()) {
      isDirty_mapRef2RefFlowFunction_83 = mapRef2RefFlowFunction_83.map();
      if (isDirty_mapRef2RefFlowFunction_83) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_83);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_85.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_47()) {
      isDirty_pushFlowFunction_47 = pushFlowFunction_47.push();
      if (isDirty_pushFlowFunction_47) {
        binaryMapToRefFlowFunction_86.inputUpdated(pushFlowFunction_47);
      }
    }
    if (guardCheck_pushFlowFunction_85()) {
      isDirty_pushFlowFunction_85 = pushFlowFunction_85.push();
      if (isDirty_pushFlowFunction_85) {
        binaryMapToRefFlowFunction_86.input2Updated(pushFlowFunction_85);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_86()) {
      binaryMapToRefFlowFunction_86.map();
    }
    afterServiceCall();
    return true;
  }

  @Override
  public boolean initialConfig(com.fluxtion.server.config.ConfigMap arg0) {
    beforeServiceCall(
        "public boolean com.fluxtion.server.lib.pnl.calculator.EventFeedConnector.initialConfig(com.fluxtion.server.config.ConfigMap)");
    ExportFunctionAuditEvent typedEvent = functionAudit;
    isDirty_eventFeedBatcher = eventFeedBatcher.initialConfig(arg0);
    if (guardCheck_mapRef2RefFlowFunction_4()) {
      isDirty_mapRef2RefFlowFunction_4 = mapRef2RefFlowFunction_4.map();
      if (isDirty_mapRef2RefFlowFunction_4) {
        mergeFlowFunction_6.inputStreamUpdated(mapRef2RefFlowFunction_4);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_5()) {
      isDirty_mapRef2RefFlowFunction_5 = mapRef2RefFlowFunction_5.map();
      if (isDirty_mapRef2RefFlowFunction_5) {
        mergeFlowFunction_6.inputStreamUpdated(mapRef2RefFlowFunction_5);
      }
    }
    if (guardCheck_mergeFlowFunction_6()) {
      isDirty_mergeFlowFunction_6 = mergeFlowFunction_6.publishMerge();
      if (isDirty_mergeFlowFunction_6) {
        filterFlowFunction_8.inputUpdated(mergeFlowFunction_6);
      }
    }
    if (guardCheck_filterFlowFunction_8()) {
      isDirty_filterFlowFunction_8 = filterFlowFunction_8.filter();
      if (isDirty_filterFlowFunction_8) {
        mapRef2RefFlowFunction_10.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_12.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_14.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_16.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_34.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_49.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_51.inputUpdated(filterFlowFunction_8);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_21.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_21.input2Updated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_21()) {
      isDirty_binaryMapToRefFlowFunction_21 = binaryMapToRefFlowFunction_21.map();
      if (isDirty_binaryMapToRefFlowFunction_21) {
        mapRef2RefFlowFunction_23.inputUpdated(binaryMapToRefFlowFunction_21);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_68.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_68.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_68()) {
      isDirty_binaryMapToRefFlowFunction_68 = binaryMapToRefFlowFunction_68.map();
      if (isDirty_binaryMapToRefFlowFunction_68) {
        mapRef2RefFlowFunction_70.inputUpdated(binaryMapToRefFlowFunction_68);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_23()) {
      isDirty_mapRef2RefFlowFunction_23 = mapRef2RefFlowFunction_23.map();
      if (isDirty_mapRef2RefFlowFunction_23) {
        binaryMapToRefFlowFunction_25.inputUpdated(mapRef2RefFlowFunction_23);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_25()) {
      isDirty_binaryMapToRefFlowFunction_25 = binaryMapToRefFlowFunction_25.map();
      if (isDirty_binaryMapToRefFlowFunction_25) {
        mapRef2RefFlowFunction_27.inputUpdated(binaryMapToRefFlowFunction_25);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_27()) {
      isDirty_mapRef2RefFlowFunction_27 = mapRef2RefFlowFunction_27.map();
      if (isDirty_mapRef2RefFlowFunction_27) {
        mapRef2RefFlowFunction_29.inputUpdated(mapRef2RefFlowFunction_27);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_29()) {
      isDirty_mapRef2RefFlowFunction_29 = mapRef2RefFlowFunction_29.map();
      if (isDirty_mapRef2RefFlowFunction_29) {
        binaryMapToRefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_29);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_34()) {
      isDirty_mapRef2RefFlowFunction_34 = mapRef2RefFlowFunction_34.map();
      if (isDirty_mapRef2RefFlowFunction_34) {
        binaryMapToRefFlowFunction_36.inputUpdated(mapRef2RefFlowFunction_34);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_36()) {
      isDirty_binaryMapToRefFlowFunction_36 = binaryMapToRefFlowFunction_36.map();
      if (isDirty_binaryMapToRefFlowFunction_36) {
        mapRef2RefFlowFunction_38.inputUpdated(binaryMapToRefFlowFunction_36);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_38()) {
      isDirty_mapRef2RefFlowFunction_38 = mapRef2RefFlowFunction_38.map();
      if (isDirty_mapRef2RefFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(mapRef2RefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        binaryMapToRefFlowFunction_42.input2Updated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_42()) {
      isDirty_binaryMapToRefFlowFunction_42 = binaryMapToRefFlowFunction_42.map();
      if (isDirty_binaryMapToRefFlowFunction_42) {
        mapRef2RefFlowFunction_44.inputUpdated(binaryMapToRefFlowFunction_42);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_44()) {
      isDirty_mapRef2RefFlowFunction_44 = mapRef2RefFlowFunction_44.map();
      if (isDirty_mapRef2RefFlowFunction_44) {
        mapRef2RefFlowFunction_45.inputUpdated(mapRef2RefFlowFunction_44);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_45()) {
      isDirty_mapRef2RefFlowFunction_45 = mapRef2RefFlowFunction_45.map();
      if (isDirty_mapRef2RefFlowFunction_45) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_45);
      }
    }
    if (guardCheck_globalNetMtm()) {
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_47.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_49()) {
      isDirty_mapRef2RefFlowFunction_49 = mapRef2RefFlowFunction_49.map();
      if (isDirty_mapRef2RefFlowFunction_49) {
        binaryMapToRefFlowFunction_53.inputUpdated(mapRef2RefFlowFunction_49);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_51()) {
      isDirty_mapRef2RefFlowFunction_51 = mapRef2RefFlowFunction_51.map();
      if (isDirty_mapRef2RefFlowFunction_51) {
        binaryMapToRefFlowFunction_53.input2Updated(mapRef2RefFlowFunction_51);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_53()) {
      isDirty_binaryMapToRefFlowFunction_53 = binaryMapToRefFlowFunction_53.map();
      if (isDirty_binaryMapToRefFlowFunction_53) {
        mapRef2RefFlowFunction_55.inputUpdated(binaryMapToRefFlowFunction_53);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_55()) {
      isDirty_mapRef2RefFlowFunction_55 = mapRef2RefFlowFunction_55.map();
      if (isDirty_mapRef2RefFlowFunction_55) {
        binaryMapToRefFlowFunction_60.inputUpdated(mapRef2RefFlowFunction_55);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_60()) {
      isDirty_binaryMapToRefFlowFunction_60 = binaryMapToRefFlowFunction_60.map();
      if (isDirty_binaryMapToRefFlowFunction_60) {
        mapRef2RefFlowFunction_62.inputUpdated(binaryMapToRefFlowFunction_60);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_62()) {
      isDirty_mapRef2RefFlowFunction_62 = mapRef2RefFlowFunction_62.map();
      if (isDirty_mapRef2RefFlowFunction_62) {
        mapRef2RefFlowFunction_64.inputUpdated(mapRef2RefFlowFunction_62);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_64()) {
      isDirty_mapRef2RefFlowFunction_64 = mapRef2RefFlowFunction_64.map();
      if (isDirty_mapRef2RefFlowFunction_64) {
        mapRef2RefFlowFunction_66.inputUpdated(mapRef2RefFlowFunction_64);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_66()) {
      isDirty_mapRef2RefFlowFunction_66 = mapRef2RefFlowFunction_66.map();
      if (isDirty_mapRef2RefFlowFunction_66) {
        binaryMapToRefFlowFunction_81.input2Updated(mapRef2RefFlowFunction_66);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_70()) {
      isDirty_mapRef2RefFlowFunction_70 = mapRef2RefFlowFunction_70.map();
      if (isDirty_mapRef2RefFlowFunction_70) {
        binaryMapToRefFlowFunction_75.inputUpdated(mapRef2RefFlowFunction_70);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_75()) {
      isDirty_binaryMapToRefFlowFunction_75 = binaryMapToRefFlowFunction_75.map();
      if (isDirty_binaryMapToRefFlowFunction_75) {
        mapRef2RefFlowFunction_77.inputUpdated(binaryMapToRefFlowFunction_75);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_77()) {
      isDirty_mapRef2RefFlowFunction_77 = mapRef2RefFlowFunction_77.map();
      if (isDirty_mapRef2RefFlowFunction_77) {
        mapRef2RefFlowFunction_79.inputUpdated(mapRef2RefFlowFunction_77);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_79()) {
      isDirty_mapRef2RefFlowFunction_79 = mapRef2RefFlowFunction_79.map();
      if (isDirty_mapRef2RefFlowFunction_79) {
        binaryMapToRefFlowFunction_81.inputUpdated(mapRef2RefFlowFunction_79);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_81()) {
      isDirty_binaryMapToRefFlowFunction_81 = binaryMapToRefFlowFunction_81.map();
      if (isDirty_binaryMapToRefFlowFunction_81) {
        mapRef2RefFlowFunction_83.inputUpdated(binaryMapToRefFlowFunction_81);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_83()) {
      isDirty_mapRef2RefFlowFunction_83 = mapRef2RefFlowFunction_83.map();
      if (isDirty_mapRef2RefFlowFunction_83) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_83);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_85.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_47()) {
      isDirty_pushFlowFunction_47 = pushFlowFunction_47.push();
      if (isDirty_pushFlowFunction_47) {
        binaryMapToRefFlowFunction_86.inputUpdated(pushFlowFunction_47);
      }
    }
    if (guardCheck_pushFlowFunction_85()) {
      isDirty_pushFlowFunction_85 = pushFlowFunction_85.push();
      if (isDirty_pushFlowFunction_85) {
        binaryMapToRefFlowFunction_86.input2Updated(pushFlowFunction_85);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_86()) {
      binaryMapToRefFlowFunction_86.map();
    }
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
    auditor.nodeRegistered(callBackNode_88, "callBackNode_88");
    auditor.nodeRegistered(callBackNode_112, "callBackNode_112");
    auditor.nodeRegistered(callBackNode_182, "callBackNode_182");
    auditor.nodeRegistered(callbackDispatcher, "callbackDispatcher");
    auditor.nodeRegistered(callBackTriggerEvent_1, "callBackTriggerEvent_1");
    auditor.nodeRegistered(callBackTriggerEvent_2, "callBackTriggerEvent_2");
    auditor.nodeRegistered(callBackTriggerEvent_0, "callBackTriggerEvent_0");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_21, "binaryMapToRefFlowFunction_21");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_25, "binaryMapToRefFlowFunction_25");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_36, "binaryMapToRefFlowFunction_36");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_42, "binaryMapToRefFlowFunction_42");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_53, "binaryMapToRefFlowFunction_53");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_60, "binaryMapToRefFlowFunction_60");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_68, "binaryMapToRefFlowFunction_68");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_75, "binaryMapToRefFlowFunction_75");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_81, "binaryMapToRefFlowFunction_81");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_86, "binaryMapToRefFlowFunction_86");
    auditor.nodeRegistered(filterFlowFunction_8, "filterFlowFunction_8");
    auditor.nodeRegistered(flatMapFlowFunction_3, "flatMapFlowFunction_3");
    auditor.nodeRegistered(flatMapFlowFunction_17, "flatMapFlowFunction_17");
    auditor.nodeRegistered(flatMapFlowFunction_30, "flatMapFlowFunction_30");
    auditor.nodeRegistered(globalNetMtm, "globalNetMtm");
    auditor.nodeRegistered(instrumentNetMtm, "instrumentNetMtm");
    auditor.nodeRegistered(mapRef2RefFlowFunction_4, "mapRef2RefFlowFunction_4");
    auditor.nodeRegistered(mapRef2RefFlowFunction_5, "mapRef2RefFlowFunction_5");
    auditor.nodeRegistered(mapRef2RefFlowFunction_10, "mapRef2RefFlowFunction_10");
    auditor.nodeRegistered(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12");
    auditor.nodeRegistered(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14");
    auditor.nodeRegistered(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16");
    auditor.nodeRegistered(mapRef2RefFlowFunction_19, "mapRef2RefFlowFunction_19");
    auditor.nodeRegistered(mapRef2RefFlowFunction_23, "mapRef2RefFlowFunction_23");
    auditor.nodeRegistered(mapRef2RefFlowFunction_27, "mapRef2RefFlowFunction_27");
    auditor.nodeRegistered(mapRef2RefFlowFunction_29, "mapRef2RefFlowFunction_29");
    auditor.nodeRegistered(mapRef2RefFlowFunction_32, "mapRef2RefFlowFunction_32");
    auditor.nodeRegistered(mapRef2RefFlowFunction_34, "mapRef2RefFlowFunction_34");
    auditor.nodeRegistered(mapRef2RefFlowFunction_38, "mapRef2RefFlowFunction_38");
    auditor.nodeRegistered(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40");
    auditor.nodeRegistered(mapRef2RefFlowFunction_44, "mapRef2RefFlowFunction_44");
    auditor.nodeRegistered(mapRef2RefFlowFunction_45, "mapRef2RefFlowFunction_45");
    auditor.nodeRegistered(mapRef2RefFlowFunction_49, "mapRef2RefFlowFunction_49");
    auditor.nodeRegistered(mapRef2RefFlowFunction_51, "mapRef2RefFlowFunction_51");
    auditor.nodeRegistered(mapRef2RefFlowFunction_55, "mapRef2RefFlowFunction_55");
    auditor.nodeRegistered(mapRef2RefFlowFunction_56, "mapRef2RefFlowFunction_56");
    auditor.nodeRegistered(mapRef2RefFlowFunction_58, "mapRef2RefFlowFunction_58");
    auditor.nodeRegistered(mapRef2RefFlowFunction_62, "mapRef2RefFlowFunction_62");
    auditor.nodeRegistered(mapRef2RefFlowFunction_64, "mapRef2RefFlowFunction_64");
    auditor.nodeRegistered(mapRef2RefFlowFunction_66, "mapRef2RefFlowFunction_66");
    auditor.nodeRegistered(mapRef2RefFlowFunction_70, "mapRef2RefFlowFunction_70");
    auditor.nodeRegistered(mapRef2RefFlowFunction_71, "mapRef2RefFlowFunction_71");
    auditor.nodeRegistered(mapRef2RefFlowFunction_73, "mapRef2RefFlowFunction_73");
    auditor.nodeRegistered(mapRef2RefFlowFunction_77, "mapRef2RefFlowFunction_77");
    auditor.nodeRegistered(mapRef2RefFlowFunction_79, "mapRef2RefFlowFunction_79");
    auditor.nodeRegistered(mapRef2RefFlowFunction_83, "mapRef2RefFlowFunction_83");
    auditor.nodeRegistered(mergeFlowFunction_6, "mergeFlowFunction_6");
    auditor.nodeRegistered(pushFlowFunction_47, "pushFlowFunction_47");
    auditor.nodeRegistered(pushFlowFunction_85, "pushFlowFunction_85");
    auditor.nodeRegistered(emptyGroupBy_422, "emptyGroupBy_422");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_9, "groupByFlowFunctionWrapper_9");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_11, "groupByFlowFunctionWrapper_11");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_13, "groupByFlowFunctionWrapper_13");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_15, "groupByFlowFunctionWrapper_15");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_18, "groupByFlowFunctionWrapper_18");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_31, "groupByFlowFunctionWrapper_31");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_33, "groupByFlowFunctionWrapper_33");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_48, "groupByFlowFunctionWrapper_48");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_50, "groupByFlowFunctionWrapper_50");
    auditor.nodeRegistered(groupByHashMap_57, "groupByHashMap_57");
    auditor.nodeRegistered(groupByHashMap_72, "groupByHashMap_72");
    auditor.nodeRegistered(groupByMapFlowFunction_22, "groupByMapFlowFunction_22");
    auditor.nodeRegistered(groupByMapFlowFunction_26, "groupByMapFlowFunction_26");
    auditor.nodeRegistered(groupByMapFlowFunction_28, "groupByMapFlowFunction_28");
    auditor.nodeRegistered(groupByMapFlowFunction_37, "groupByMapFlowFunction_37");
    auditor.nodeRegistered(groupByMapFlowFunction_39, "groupByMapFlowFunction_39");
    auditor.nodeRegistered(groupByMapFlowFunction_43, "groupByMapFlowFunction_43");
    auditor.nodeRegistered(groupByMapFlowFunction_54, "groupByMapFlowFunction_54");
    auditor.nodeRegistered(groupByMapFlowFunction_61, "groupByMapFlowFunction_61");
    auditor.nodeRegistered(groupByMapFlowFunction_65, "groupByMapFlowFunction_65");
    auditor.nodeRegistered(groupByMapFlowFunction_69, "groupByMapFlowFunction_69");
    auditor.nodeRegistered(groupByMapFlowFunction_76, "groupByMapFlowFunction_76");
    auditor.nodeRegistered(groupByMapFlowFunction_78, "groupByMapFlowFunction_78");
    auditor.nodeRegistered(groupByMapFlowFunction_82, "groupByMapFlowFunction_82");
    auditor.nodeRegistered(leftJoin_41, "leftJoin_41");
    auditor.nodeRegistered(leftJoin_80, "leftJoin_80");
    auditor.nodeRegistered(outerJoin_20, "outerJoin_20");
    auditor.nodeRegistered(outerJoin_24, "outerJoin_24");
    auditor.nodeRegistered(outerJoin_35, "outerJoin_35");
    auditor.nodeRegistered(outerJoin_52, "outerJoin_52");
    auditor.nodeRegistered(outerJoin_59, "outerJoin_59");
    auditor.nodeRegistered(outerJoin_67, "outerJoin_67");
    auditor.nodeRegistered(outerJoin_74, "outerJoin_74");
    auditor.nodeRegistered(defaultValue_63, "defaultValue_63");
    auditor.nodeRegistered(mapTuple_680, "mapTuple_680");
    auditor.nodeRegistered(mapTuple_686, "mapTuple_686");
    auditor.nodeRegistered(mapTuple_690, "mapTuple_690");
    auditor.nodeRegistered(mapTuple_703, "mapTuple_703");
    auditor.nodeRegistered(mapTuple_707, "mapTuple_707");
    auditor.nodeRegistered(mapTuple_718, "mapTuple_718");
    auditor.nodeRegistered(mapTuple_723, "mapTuple_723");
    auditor.nodeRegistered(mapTuple_733, "mapTuple_733");
    auditor.nodeRegistered(mapTuple_737, "mapTuple_737");
    auditor.nodeRegistered(subscriptionManager, "subscriptionManager");
    auditor.nodeRegistered(handlerPositionSnapshot, "handlerPositionSnapshot");
    auditor.nodeRegistered(
        handlerSignal_positionSnapshotReset, "handlerSignal_positionSnapshotReset");
    auditor.nodeRegistered(handlerSignal_positionUpdate, "handlerSignal_positionUpdate");
    auditor.nodeRegistered(handlerTrade, "handlerTrade");
    auditor.nodeRegistered(handlerTradeBatch, "handlerTradeBatch");
    auditor.nodeRegistered(context, "context");
    auditor.nodeRegistered(namedFeedTableNode_87, "namedFeedTableNode_87");
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
    nodeNameLookup.processingComplete();
    serviceRegistry.processingComplete();
    isDirty_binaryMapToRefFlowFunction_21 = false;
    isDirty_binaryMapToRefFlowFunction_25 = false;
    isDirty_binaryMapToRefFlowFunction_36 = false;
    isDirty_binaryMapToRefFlowFunction_42 = false;
    isDirty_binaryMapToRefFlowFunction_53 = false;
    isDirty_binaryMapToRefFlowFunction_60 = false;
    isDirty_binaryMapToRefFlowFunction_68 = false;
    isDirty_binaryMapToRefFlowFunction_75 = false;
    isDirty_binaryMapToRefFlowFunction_81 = false;
    isDirty_callBackNode_88 = false;
    isDirty_callBackNode_112 = false;
    isDirty_callBackNode_182 = false;
    isDirty_clock = false;
    isDirty_derivedRateNode = false;
    isDirty_eventFeedBatcher = false;
    isDirty_filterFlowFunction_8 = false;
    isDirty_flatMapFlowFunction_3 = false;
    isDirty_flatMapFlowFunction_17 = false;
    isDirty_flatMapFlowFunction_30 = false;
    isDirty_globalNetMtm = false;
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
    isDirty_mapRef2RefFlowFunction_19 = false;
    isDirty_mapRef2RefFlowFunction_23 = false;
    isDirty_mapRef2RefFlowFunction_27 = false;
    isDirty_mapRef2RefFlowFunction_29 = false;
    isDirty_mapRef2RefFlowFunction_32 = false;
    isDirty_mapRef2RefFlowFunction_34 = false;
    isDirty_mapRef2RefFlowFunction_38 = false;
    isDirty_mapRef2RefFlowFunction_40 = false;
    isDirty_mapRef2RefFlowFunction_44 = false;
    isDirty_mapRef2RefFlowFunction_45 = false;
    isDirty_mapRef2RefFlowFunction_49 = false;
    isDirty_mapRef2RefFlowFunction_51 = false;
    isDirty_mapRef2RefFlowFunction_55 = false;
    isDirty_mapRef2RefFlowFunction_56 = false;
    isDirty_mapRef2RefFlowFunction_58 = false;
    isDirty_mapRef2RefFlowFunction_62 = false;
    isDirty_mapRef2RefFlowFunction_64 = false;
    isDirty_mapRef2RefFlowFunction_66 = false;
    isDirty_mapRef2RefFlowFunction_70 = false;
    isDirty_mapRef2RefFlowFunction_71 = false;
    isDirty_mapRef2RefFlowFunction_73 = false;
    isDirty_mapRef2RefFlowFunction_77 = false;
    isDirty_mapRef2RefFlowFunction_79 = false;
    isDirty_mapRef2RefFlowFunction_83 = false;
    isDirty_mergeFlowFunction_6 = false;
    isDirty_namedFeedTableNode_87 = false;
    isDirty_positionCache = false;
    isDirty_pushFlowFunction_47 = false;
    isDirty_pushFlowFunction_85 = false;
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
          binaryMapToRefFlowFunction_25, () -> isDirty_binaryMapToRefFlowFunction_25);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_36, () -> isDirty_binaryMapToRefFlowFunction_36);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_42, () -> isDirty_binaryMapToRefFlowFunction_42);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_53, () -> isDirty_binaryMapToRefFlowFunction_53);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_60, () -> isDirty_binaryMapToRefFlowFunction_60);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_68, () -> isDirty_binaryMapToRefFlowFunction_68);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_75, () -> isDirty_binaryMapToRefFlowFunction_75);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_81, () -> isDirty_binaryMapToRefFlowFunction_81);
      dirtyFlagSupplierMap.put(callBackNode_112, () -> isDirty_callBackNode_112);
      dirtyFlagSupplierMap.put(callBackNode_182, () -> isDirty_callBackNode_182);
      dirtyFlagSupplierMap.put(callBackNode_88, () -> isDirty_callBackNode_88);
      dirtyFlagSupplierMap.put(clock, () -> isDirty_clock);
      dirtyFlagSupplierMap.put(derivedRateNode, () -> isDirty_derivedRateNode);
      dirtyFlagSupplierMap.put(eventFeedBatcher, () -> isDirty_eventFeedBatcher);
      dirtyFlagSupplierMap.put(filterFlowFunction_8, () -> isDirty_filterFlowFunction_8);
      dirtyFlagSupplierMap.put(flatMapFlowFunction_17, () -> isDirty_flatMapFlowFunction_17);
      dirtyFlagSupplierMap.put(flatMapFlowFunction_3, () -> isDirty_flatMapFlowFunction_3);
      dirtyFlagSupplierMap.put(flatMapFlowFunction_30, () -> isDirty_flatMapFlowFunction_30);
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
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_27, () -> isDirty_mapRef2RefFlowFunction_27);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_29, () -> isDirty_mapRef2RefFlowFunction_29);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_32, () -> isDirty_mapRef2RefFlowFunction_32);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_34, () -> isDirty_mapRef2RefFlowFunction_34);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_38, () -> isDirty_mapRef2RefFlowFunction_38);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_4, () -> isDirty_mapRef2RefFlowFunction_4);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_40, () -> isDirty_mapRef2RefFlowFunction_40);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_44, () -> isDirty_mapRef2RefFlowFunction_44);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_45, () -> isDirty_mapRef2RefFlowFunction_45);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_49, () -> isDirty_mapRef2RefFlowFunction_49);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_5, () -> isDirty_mapRef2RefFlowFunction_5);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_51, () -> isDirty_mapRef2RefFlowFunction_51);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_55, () -> isDirty_mapRef2RefFlowFunction_55);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_56, () -> isDirty_mapRef2RefFlowFunction_56);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_58, () -> isDirty_mapRef2RefFlowFunction_58);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_62, () -> isDirty_mapRef2RefFlowFunction_62);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_64, () -> isDirty_mapRef2RefFlowFunction_64);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_66, () -> isDirty_mapRef2RefFlowFunction_66);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_70, () -> isDirty_mapRef2RefFlowFunction_70);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_71, () -> isDirty_mapRef2RefFlowFunction_71);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_73, () -> isDirty_mapRef2RefFlowFunction_73);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_77, () -> isDirty_mapRef2RefFlowFunction_77);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_79, () -> isDirty_mapRef2RefFlowFunction_79);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_83, () -> isDirty_mapRef2RefFlowFunction_83);
      dirtyFlagSupplierMap.put(mergeFlowFunction_6, () -> isDirty_mergeFlowFunction_6);
      dirtyFlagSupplierMap.put(namedFeedTableNode_87, () -> isDirty_namedFeedTableNode_87);
      dirtyFlagSupplierMap.put(positionCache, () -> isDirty_positionCache);
      dirtyFlagSupplierMap.put(pushFlowFunction_47, () -> isDirty_pushFlowFunction_47);
      dirtyFlagSupplierMap.put(pushFlowFunction_85, () -> isDirty_pushFlowFunction_85);
    }
    return dirtyFlagSupplierMap.getOrDefault(node, StaticEventProcessor.ALWAYS_FALSE);
  }

  @Override
  public void setDirty(Object node, boolean dirtyFlag) {
    if (dirtyFlagUpdateMap.isEmpty()) {
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_21, (b) -> isDirty_binaryMapToRefFlowFunction_21 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_25, (b) -> isDirty_binaryMapToRefFlowFunction_25 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_36, (b) -> isDirty_binaryMapToRefFlowFunction_36 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_42, (b) -> isDirty_binaryMapToRefFlowFunction_42 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_53, (b) -> isDirty_binaryMapToRefFlowFunction_53 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_60, (b) -> isDirty_binaryMapToRefFlowFunction_60 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_68, (b) -> isDirty_binaryMapToRefFlowFunction_68 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_75, (b) -> isDirty_binaryMapToRefFlowFunction_75 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_81, (b) -> isDirty_binaryMapToRefFlowFunction_81 = b);
      dirtyFlagUpdateMap.put(callBackNode_112, (b) -> isDirty_callBackNode_112 = b);
      dirtyFlagUpdateMap.put(callBackNode_182, (b) -> isDirty_callBackNode_182 = b);
      dirtyFlagUpdateMap.put(callBackNode_88, (b) -> isDirty_callBackNode_88 = b);
      dirtyFlagUpdateMap.put(clock, (b) -> isDirty_clock = b);
      dirtyFlagUpdateMap.put(derivedRateNode, (b) -> isDirty_derivedRateNode = b);
      dirtyFlagUpdateMap.put(eventFeedBatcher, (b) -> isDirty_eventFeedBatcher = b);
      dirtyFlagUpdateMap.put(filterFlowFunction_8, (b) -> isDirty_filterFlowFunction_8 = b);
      dirtyFlagUpdateMap.put(flatMapFlowFunction_17, (b) -> isDirty_flatMapFlowFunction_17 = b);
      dirtyFlagUpdateMap.put(flatMapFlowFunction_3, (b) -> isDirty_flatMapFlowFunction_3 = b);
      dirtyFlagUpdateMap.put(flatMapFlowFunction_30, (b) -> isDirty_flatMapFlowFunction_30 = b);
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
          mapRef2RefFlowFunction_27, (b) -> isDirty_mapRef2RefFlowFunction_27 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_29, (b) -> isDirty_mapRef2RefFlowFunction_29 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_32, (b) -> isDirty_mapRef2RefFlowFunction_32 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_34, (b) -> isDirty_mapRef2RefFlowFunction_34 = b);
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
          mapRef2RefFlowFunction_49, (b) -> isDirty_mapRef2RefFlowFunction_49 = b);
      dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_5, (b) -> isDirty_mapRef2RefFlowFunction_5 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_51, (b) -> isDirty_mapRef2RefFlowFunction_51 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_55, (b) -> isDirty_mapRef2RefFlowFunction_55 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_56, (b) -> isDirty_mapRef2RefFlowFunction_56 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_58, (b) -> isDirty_mapRef2RefFlowFunction_58 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_62, (b) -> isDirty_mapRef2RefFlowFunction_62 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_64, (b) -> isDirty_mapRef2RefFlowFunction_64 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_66, (b) -> isDirty_mapRef2RefFlowFunction_66 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_70, (b) -> isDirty_mapRef2RefFlowFunction_70 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_71, (b) -> isDirty_mapRef2RefFlowFunction_71 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_73, (b) -> isDirty_mapRef2RefFlowFunction_73 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_77, (b) -> isDirty_mapRef2RefFlowFunction_77 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_79, (b) -> isDirty_mapRef2RefFlowFunction_79 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_83, (b) -> isDirty_mapRef2RefFlowFunction_83 = b);
      dirtyFlagUpdateMap.put(mergeFlowFunction_6, (b) -> isDirty_mergeFlowFunction_6 = b);
      dirtyFlagUpdateMap.put(namedFeedTableNode_87, (b) -> isDirty_namedFeedTableNode_87 = b);
      dirtyFlagUpdateMap.put(positionCache, (b) -> isDirty_positionCache = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_47, (b) -> isDirty_pushFlowFunction_47 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_85, (b) -> isDirty_pushFlowFunction_85 = b);
    }
    dirtyFlagUpdateMap.get(node).accept(dirtyFlag);
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_21() {
    return isDirty_mapRef2RefFlowFunction_10 | isDirty_mapRef2RefFlowFunction_12;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_25() {
    return isDirty_mapRef2RefFlowFunction_19 | isDirty_mapRef2RefFlowFunction_23;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_36() {
    return isDirty_mapRef2RefFlowFunction_32 | isDirty_mapRef2RefFlowFunction_34;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_42() {
    return isDirty_mapRef2RefFlowFunction_29 | isDirty_mapRef2RefFlowFunction_40;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_53() {
    return isDirty_mapRef2RefFlowFunction_49 | isDirty_mapRef2RefFlowFunction_51;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_60() {
    return isDirty_mapRef2RefFlowFunction_55 | isDirty_mapRef2RefFlowFunction_58;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_68() {
    return isDirty_mapRef2RefFlowFunction_14 | isDirty_mapRef2RefFlowFunction_16;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_75() {
    return isDirty_mapRef2RefFlowFunction_70 | isDirty_mapRef2RefFlowFunction_73;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_81() {
    return isDirty_mapRef2RefFlowFunction_66 | isDirty_mapRef2RefFlowFunction_79;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_86() {
    return isDirty_positionCache | isDirty_pushFlowFunction_47 | isDirty_pushFlowFunction_85;
  }

  private boolean guardCheck_filterFlowFunction_8() {
    return isDirty_mergeFlowFunction_6;
  }

  private boolean guardCheck_flatMapFlowFunction_3() {
    return isDirty_callBackNode_88;
  }

  private boolean guardCheck_flatMapFlowFunction_17() {
    return isDirty_callBackNode_112;
  }

  private boolean guardCheck_flatMapFlowFunction_30() {
    return isDirty_callBackNode_182;
  }

  private boolean guardCheck_globalNetMtm() {
    return isDirty_mapRef2RefFlowFunction_45;
  }

  private boolean guardCheck_instrumentNetMtm() {
    return isDirty_mapRef2RefFlowFunction_83;
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

  private boolean guardCheck_mapRef2RefFlowFunction_19() {
    return isDirty_flatMapFlowFunction_17
        | isDirty_handlerSignal_positionSnapshotReset
        | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_23() {
    return isDirty_binaryMapToRefFlowFunction_21 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_27() {
    return isDirty_binaryMapToRefFlowFunction_25;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_29() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_27;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_32() {
    return isDirty_flatMapFlowFunction_30
        | isDirty_handlerSignal_positionSnapshotReset
        | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_34() {
    return isDirty_filterFlowFunction_8 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_38() {
    return isDirty_binaryMapToRefFlowFunction_36 | isDirty_handlerSignal_positionSnapshotReset;
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

  private boolean guardCheck_mapRef2RefFlowFunction_49() {
    return isDirty_filterFlowFunction_8;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_51() {
    return isDirty_filterFlowFunction_8;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_55() {
    return isDirty_binaryMapToRefFlowFunction_53 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_56() {
    return isDirty_handlerPositionSnapshot;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_58() {
    return isDirty_mapRef2RefFlowFunction_56;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_62() {
    return isDirty_binaryMapToRefFlowFunction_60 | isDirty_handlerSignal_positionSnapshotReset;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_64() {
    return isDirty_mapRef2RefFlowFunction_62;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_66() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_64;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_70() {
    return isDirty_binaryMapToRefFlowFunction_68 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_71() {
    return isDirty_handlerPositionSnapshot;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_73() {
    return isDirty_mapRef2RefFlowFunction_71;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_77() {
    return isDirty_binaryMapToRefFlowFunction_75;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_79() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_77;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_83() {
    return isDirty_binaryMapToRefFlowFunction_81 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mergeFlowFunction_6() {
    return isDirty_mapRef2RefFlowFunction_4 | isDirty_mapRef2RefFlowFunction_5;
  }

  private boolean guardCheck_pushFlowFunction_47() {
    return isDirty_globalNetMtm;
  }

  private boolean guardCheck_pushFlowFunction_85() {
    return isDirty_instrumentNetMtm;
  }

  private boolean guardCheck_groupByMapFlowFunction_28() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_groupByMapFlowFunction_39() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_groupByMapFlowFunction_65() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_groupByMapFlowFunction_78() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_context() {
    return isDirty_clock;
  }

  private boolean guardCheck_globalNetMtmListener() {
    return isDirty_pushFlowFunction_47;
  }

  private boolean guardCheck_instrumentNetMtmListener() {
    return isDirty_pushFlowFunction_85;
  }

  private boolean guardCheck_derivedRateNode() {
    return isDirty_namedFeedTableNode_87;
  }

  private boolean guardCheck_eventFeedBatcher() {
    return isDirty_namedFeedTableNode_87;
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
