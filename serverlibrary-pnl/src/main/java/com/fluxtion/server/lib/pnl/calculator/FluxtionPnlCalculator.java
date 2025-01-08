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
 * eventProcessorGenerator version : 9.5.2
 * api version                     : 9.5.2
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
  private final CallBackNode callBackNode_110 = new CallBackNode<>(callBackTriggerEvent_0);
  private final InstanceCallbackEvent_1 callBackTriggerEvent_1 = new InstanceCallbackEvent_1();
  private final CallBackNode callBackNode_134 = new CallBackNode<>(callBackTriggerEvent_1);
  private final InstanceCallbackEvent_2 callBackTriggerEvent_2 = new InstanceCallbackEvent_2();
  private final CallBackNode callBackNode_260 = new CallBackNode<>(callBackTriggerEvent_2);
  private final CallbackDispatcherImpl callbackDispatcher = new CallbackDispatcherImpl();
  public final Clock clock = new Clock();
  private final EmptyGroupBy emptyGroupBy_157 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_24 = new DefaultValue<>(emptyGroupBy_157);
  private final EmptyGroupBy emptyGroupBy_201 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_30 = new DefaultValue<>(emptyGroupBy_201);
  private final EmptyGroupBy emptyGroupBy_239 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_34 = new DefaultValue<>(emptyGroupBy_239);
  private final EmptyGroupBy emptyGroupBy_266 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_41 = new DefaultValue<>(emptyGroupBy_266);
  private final EmptyGroupBy emptyGroupBy_289 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_47 = new DefaultValue<>(emptyGroupBy_289);
  private final EmptyGroupBy emptyGroupBy_516 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_66 = new DefaultValue<>(emptyGroupBy_516);
  private final EmptyGroupBy emptyGroupBy_530 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_71 = new DefaultValue<>(emptyGroupBy_530);
  private final EmptyGroupBy emptyGroupBy_566 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_77 = new DefaultValue<>(emptyGroupBy_566);
  private final EmptyGroupBy emptyGroupBy_627 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_85 = new DefaultValue<>(emptyGroupBy_627);
  private final EmptyGroupBy emptyGroupBy_641 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_90 = new DefaultValue<>(emptyGroupBy_641);
  private final EmptyGroupBy emptyGroupBy_697 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_98 = new DefaultValue<>(emptyGroupBy_697);
  private final EmptyGroupBy emptyGroupBy_718 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_100 = new DefaultValue<>(emptyGroupBy_718);
  public final EventLogManager eventLogger = new EventLogManager();
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_9 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, SingleInstrumentPosMtmAggregate::dealt);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_11 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getContraInstrument, Mappers::identity, SingleInstrumentPosMtmAggregate::contra);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_13 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, InstrumentPosMtmAggregate::dealt);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_15 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getContraInstrument, Mappers::identity, InstrumentPosMtmAggregate::contra);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_18 =
      new GroupByFlowFunctionWrapper<>(
          InstrumentPosition::instrument, Mappers::identity, AggregateIdentityFlowFunction::new);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_37 =
      new GroupByFlowFunctionWrapper<>(
          InstrumentPosition::instrument, Mappers::identity, AggregateIdentityFlowFunction::new);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_39 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, FeeInstrumentPosMtmAggregate::new);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_58 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, FeeInstrumentPosMtmAggregate::new);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_60 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getContraInstrument, Mappers::identity, FeeInstrumentPosMtmAggregate::new);
  private final GroupByHashMap groupByHashMap_69 = new GroupByHashMap<>();
  private final GroupByHashMap groupByHashMap_88 = new GroupByHashMap<>();
  private final LeftJoin leftJoin_51 = new LeftJoin();
  private final LeftJoin leftJoin_102 = new LeftJoin();
  private final MapTuple mapTuple_917 = new MapTuple<>(NetMarkToMarket::combineInst);
  private final GroupByMapFlowFunction groupByMapFlowFunction_104 =
      new GroupByMapFlowFunction(mapTuple_917::mapTuple);
  private final MapTuple mapTuple_923 = new MapTuple<>(FeeInstrumentPosMtmAggregate::merge);
  private final GroupByMapFlowFunction groupByMapFlowFunction_75 =
      new GroupByMapFlowFunction(mapTuple_923::mapTuple);
  private final MapTuple mapTuple_929 = new MapTuple<>(FeeInstrumentPosMtmAggregate::merge);
  private final GroupByMapFlowFunction groupByMapFlowFunction_64 =
      new GroupByMapFlowFunction(mapTuple_929::mapTuple);
  private final MapTuple mapTuple_944 = new MapTuple<>(InstrumentPosMtm::mergeSnapshot);
  private final GroupByMapFlowFunction groupByMapFlowFunction_94 =
      new GroupByMapFlowFunction(mapTuple_944::mapTuple);
  private final MapTuple mapTuple_950 = new MapTuple<>(InstrumentPosMtm::merge);
  private final GroupByMapFlowFunction groupByMapFlowFunction_83 =
      new GroupByMapFlowFunction(mapTuple_950::mapTuple);
  private final MapTuple mapTuple_961 = new MapTuple<>(NetMarkToMarket::combine);
  private final GroupByMapFlowFunction groupByMapFlowFunction_53 =
      new GroupByMapFlowFunction(mapTuple_961::mapTuple);
  private final MapTuple mapTuple_967 = new MapTuple<>(FeeInstrumentPosMtm::addSnapshot);
  private final GroupByMapFlowFunction groupByMapFlowFunction_45 =
      new GroupByMapFlowFunction(mapTuple_967::mapTuple);
  private final MapTuple mapTuple_980 = new MapTuple<>(InstrumentPosMtm::addSnapshot);
  private final GroupByMapFlowFunction groupByMapFlowFunction_28 =
      new GroupByMapFlowFunction(mapTuple_980::mapTuple);
  private final MapTuple mapTuple_985 = new MapTuple<>(InstrumentPosMtm::merge);
  private final GroupByMapFlowFunction groupByMapFlowFunction_22 =
      new GroupByMapFlowFunction(mapTuple_985::mapTuple);
  private final NamedFeedTableNode namedFeedTableNode_109 =
      new NamedFeedTableNode<>("symbolFeed", Symbol::symbolName);
  public final DerivedRateNode derivedRateNode = new DerivedRateNode(namedFeedTableNode_109);
  public final EventFeedConnector eventFeedBatcher = new EventFeedConnector(namedFeedTableNode_109);
  private final GroupByMapFlowFunction groupByMapFlowFunction_32 =
      new GroupByMapFlowFunction(derivedRateNode::calculateInstrumentPosMtm);
  private final GroupByMapFlowFunction groupByMapFlowFunction_49 =
      new GroupByMapFlowFunction(derivedRateNode::calculateFeeMtm);
  private final GroupByMapFlowFunction groupByMapFlowFunction_79 =
      new GroupByMapFlowFunction(derivedRateNode::calculateFeeMtm);
  private final GroupByMapFlowFunction groupByMapFlowFunction_96 =
      new GroupByMapFlowFunction(derivedRateNode::calculateInstrumentPosMtm_A);
  public final NodeNameAuditor nodeNameLookup = new NodeNameAuditor();
  private final OuterJoin outerJoin_20 = new OuterJoin();
  private final OuterJoin outerJoin_26 = new OuterJoin();
  private final OuterJoin outerJoin_43 = new OuterJoin();
  private final OuterJoin outerJoin_62 = new OuterJoin();
  private final OuterJoin outerJoin_73 = new OuterJoin();
  private final OuterJoin outerJoin_81 = new OuterJoin();
  private final OuterJoin outerJoin_92 = new OuterJoin();
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
  private final FlatMapFlowFunction flatMapFlowFunction_17 =
      new FlatMapFlowFunction<>(handlerPositionSnapshot, PositionSnapshot::getPositions);
  private final FlatMapFlowFunction flatMapFlowFunction_36 =
      new FlatMapFlowFunction<>(handlerPositionSnapshot, PositionSnapshot::getFeePositions);
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
  private final FlatMapFlowFunction flatMapFlowFunction_3 =
      new FlatMapFlowFunction<>(handlerTradeBatch, TradeBatch::getTrades);
  private final SinkPublisher instrumentNetMtmListener =
      new SinkPublisher<>("instrumentNetMtmListener");
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_4 =
      new MapRef2RefFlowFunction<>(flatMapFlowFunction_3, eventFeedBatcher::validateBatchTrade);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_5 =
      new MapRef2RefFlowFunction<>(handlerTrade, eventFeedBatcher::validateTrade);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_19 =
      new MapRef2RefFlowFunction<>(
          flatMapFlowFunction_17, groupByFlowFunctionWrapper_18::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_38 =
      new MapRef2RefFlowFunction<>(
          flatMapFlowFunction_36, groupByFlowFunctionWrapper_37::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_68 =
      new MapRef2RefFlowFunction<>(
          handlerPositionSnapshot, PositionSnapshot::getInstrumentFeePositionMap);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_70 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_68, groupByHashMap_69::fromMap);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_72 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_70, defaultValue_71::getOrDefault);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_87 =
      new MapRef2RefFlowFunction<>(
          handlerPositionSnapshot, PositionSnapshot::getInstrumentPositionMap);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_89 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_87, groupByHashMap_88::fromMap);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_91 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_89, defaultValue_90::getOrDefault);
  private final MergeFlowFunction mergeFlowFunction_6 =
      new MergeFlowFunction<>(Arrays.asList(mapRef2RefFlowFunction_5, mapRef2RefFlowFunction_4));
  public final ServiceRegistryNode serviceRegistry = new ServiceRegistryNode();
  private final TradeSequenceFilter tradeSequenceFilter_7 = new TradeSequenceFilter();
  private final FilterFlowFunction filterFlowFunction_8 =
      new FilterFlowFunction<>(
          mergeFlowFunction_6, tradeSequenceFilter_7::checkTradeSequenceNumber);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_10 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_8, groupByFlowFunctionWrapper_9::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_12 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_8, groupByFlowFunctionWrapper_11::aggregate);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_21 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_10, mapRef2RefFlowFunction_12, outerJoin_20::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_14 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_8, groupByFlowFunctionWrapper_13::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_16 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_8, groupByFlowFunctionWrapper_15::aggregate);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_82 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_14, mapRef2RefFlowFunction_16, outerJoin_81::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_23 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_21, groupByMapFlowFunction_22::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_25 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_23, defaultValue_24::getOrDefault);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_27 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_25, mapRef2RefFlowFunction_19, outerJoin_26::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_29 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_27, groupByMapFlowFunction_28::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_31 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_29, defaultValue_30::getOrDefault);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_33 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_31, groupByMapFlowFunction_32::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_35 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_33, defaultValue_34::getOrDefault);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_40 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_8, groupByFlowFunctionWrapper_39::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_42 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_40, defaultValue_41::getOrDefault);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_44 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_42, mapRef2RefFlowFunction_38, outerJoin_43::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_46 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_44, groupByMapFlowFunction_45::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_48 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_46, defaultValue_47::getOrDefault);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_50 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_48, groupByMapFlowFunction_49::mapValues);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_52 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_35, mapRef2RefFlowFunction_50, leftJoin_51::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_54 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_52, groupByMapFlowFunction_53::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_55 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_54, GroupBy<Object, Object>::toMap);
  public final MapRef2RefFlowFunction globalNetMtm =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_55, NetMarkToMarket::markToMarketSum);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_59 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_8, groupByFlowFunctionWrapper_58::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_61 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_8, groupByFlowFunctionWrapper_60::aggregate);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_63 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_59, mapRef2RefFlowFunction_61, outerJoin_62::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_65 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_63, groupByMapFlowFunction_64::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_67 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_65, defaultValue_66::getOrDefault);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_74 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_67, mapRef2RefFlowFunction_72, outerJoin_73::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_76 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_74, groupByMapFlowFunction_75::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_78 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_76, defaultValue_77::getOrDefault);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_80 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_78, groupByMapFlowFunction_79::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_84 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_82, groupByMapFlowFunction_83::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_86 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_84, defaultValue_85::getOrDefault);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_93 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_86, mapRef2RefFlowFunction_91, outerJoin_92::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_95 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_93, groupByMapFlowFunction_94::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_97 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_95, groupByMapFlowFunction_96::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_99 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_97, defaultValue_98::getOrDefault);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_101 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_99, defaultValue_100::getOrDefault);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_103 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_101, mapRef2RefFlowFunction_80, leftJoin_102::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_105 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_103, groupByMapFlowFunction_104::mapValues);
  public final MapRef2RefFlowFunction instrumentNetMtm =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_105, GroupBy<Object, Object>::toMap);
  private final PushFlowFunction pushFlowFunction_57 =
      new PushFlowFunction<>(globalNetMtm, globalNetMtmListener::publish);
  private final PushFlowFunction pushFlowFunction_107 =
      new PushFlowFunction<>(instrumentNetMtm, instrumentNetMtmListener::publish);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_108 =
      new BinaryMapToRefFlowFunction<>(
          pushFlowFunction_57, pushFlowFunction_107, positionCache::checkPoint);
  private final ExportFunctionAuditEvent functionAudit = new ExportFunctionAuditEvent();
  //Dirty flags
  private boolean initCalled = false;
  private boolean processing = false;
  private boolean buffering = false;
  private final IdentityHashMap<Object, BooleanSupplier> dirtyFlagSupplierMap =
      new IdentityHashMap<>(72);
  private final IdentityHashMap<Object, Consumer<Boolean>> dirtyFlagUpdateMap =
      new IdentityHashMap<>(72);

  private boolean isDirty_binaryMapToRefFlowFunction_21 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_27 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_44 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_52 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_63 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_74 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_82 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_93 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_103 = false;
  private boolean isDirty_callBackNode_110 = false;
  private boolean isDirty_callBackNode_134 = false;
  private boolean isDirty_callBackNode_260 = false;
  private boolean isDirty_clock = false;
  private boolean isDirty_derivedRateNode = false;
  private boolean isDirty_eventFeedBatcher = false;
  private boolean isDirty_filterFlowFunction_8 = false;
  private boolean isDirty_flatMapFlowFunction_3 = false;
  private boolean isDirty_flatMapFlowFunction_17 = false;
  private boolean isDirty_flatMapFlowFunction_36 = false;
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
  private boolean isDirty_mapRef2RefFlowFunction_25 = false;
  private boolean isDirty_mapRef2RefFlowFunction_29 = false;
  private boolean isDirty_mapRef2RefFlowFunction_31 = false;
  private boolean isDirty_mapRef2RefFlowFunction_33 = false;
  private boolean isDirty_mapRef2RefFlowFunction_35 = false;
  private boolean isDirty_mapRef2RefFlowFunction_38 = false;
  private boolean isDirty_mapRef2RefFlowFunction_40 = false;
  private boolean isDirty_mapRef2RefFlowFunction_42 = false;
  private boolean isDirty_mapRef2RefFlowFunction_46 = false;
  private boolean isDirty_mapRef2RefFlowFunction_48 = false;
  private boolean isDirty_mapRef2RefFlowFunction_50 = false;
  private boolean isDirty_mapRef2RefFlowFunction_54 = false;
  private boolean isDirty_mapRef2RefFlowFunction_55 = false;
  private boolean isDirty_mapRef2RefFlowFunction_59 = false;
  private boolean isDirty_mapRef2RefFlowFunction_61 = false;
  private boolean isDirty_mapRef2RefFlowFunction_65 = false;
  private boolean isDirty_mapRef2RefFlowFunction_67 = false;
  private boolean isDirty_mapRef2RefFlowFunction_68 = false;
  private boolean isDirty_mapRef2RefFlowFunction_70 = false;
  private boolean isDirty_mapRef2RefFlowFunction_72 = false;
  private boolean isDirty_mapRef2RefFlowFunction_76 = false;
  private boolean isDirty_mapRef2RefFlowFunction_78 = false;
  private boolean isDirty_mapRef2RefFlowFunction_80 = false;
  private boolean isDirty_mapRef2RefFlowFunction_84 = false;
  private boolean isDirty_mapRef2RefFlowFunction_86 = false;
  private boolean isDirty_mapRef2RefFlowFunction_87 = false;
  private boolean isDirty_mapRef2RefFlowFunction_89 = false;
  private boolean isDirty_mapRef2RefFlowFunction_91 = false;
  private boolean isDirty_mapRef2RefFlowFunction_95 = false;
  private boolean isDirty_mapRef2RefFlowFunction_97 = false;
  private boolean isDirty_mapRef2RefFlowFunction_99 = false;
  private boolean isDirty_mapRef2RefFlowFunction_101 = false;
  private boolean isDirty_mapRef2RefFlowFunction_105 = false;
  private boolean isDirty_mergeFlowFunction_6 = false;
  private boolean isDirty_namedFeedTableNode_109 = false;
  private boolean isDirty_positionCache = false;
  private boolean isDirty_pushFlowFunction_57 = false;
  private boolean isDirty_pushFlowFunction_107 = false;

  //Forked declarations

  //Filter constants

  //unknown event handler
  private Consumer unKnownEventHandler = (e) -> {};

  public FluxtionPnlCalculator(Map<Object, Object> contextMap) {
    if (context != null) {
      context.replaceMappings(contextMap);
    }
    eventLogger.trace = (boolean) true;
    eventLogger.printEventToString = (boolean) true;
    eventLogger.printThreadName = (boolean) true;
    eventLogger.traceLevel = com.fluxtion.runtime.audit.EventLogControlEvent.LogLevel.INFO;
    eventLogger.clock = clock;
    binaryMapToRefFlowFunction_21.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_27.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_44.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_52.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_63.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_74.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_82.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_93.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_103.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_108.setEventProcessorContext(context);
    filterFlowFunction_8.setEventProcessorContext(context);
    flatMapFlowFunction_3.setFlatMapCompleteSignal("positionUpdate");
    flatMapFlowFunction_3.callback = callBackNode_110;
    flatMapFlowFunction_3.dirtyStateMonitor = callbackDispatcher;
    flatMapFlowFunction_17.callback = callBackNode_134;
    flatMapFlowFunction_17.dirtyStateMonitor = callbackDispatcher;
    flatMapFlowFunction_36.callback = callBackNode_260;
    flatMapFlowFunction_36.dirtyStateMonitor = callbackDispatcher;
    globalNetMtm.setEventProcessorContext(context);
    instrumentNetMtm.setEventProcessorContext(context);
    mapRef2RefFlowFunction_4.setEventProcessorContext(context);
    mapRef2RefFlowFunction_5.setEventProcessorContext(context);
    mapRef2RefFlowFunction_10.setEventProcessorContext(context);
    mapRef2RefFlowFunction_10.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_12.setEventProcessorContext(context);
    mapRef2RefFlowFunction_12.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_14.setEventProcessorContext(context);
    mapRef2RefFlowFunction_14.setPublishTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_14.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_16.setEventProcessorContext(context);
    mapRef2RefFlowFunction_16.setPublishTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_16.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_19.setEventProcessorContext(context);
    mapRef2RefFlowFunction_19.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_19.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_23.setEventProcessorContext(context);
    mapRef2RefFlowFunction_25.setEventProcessorContext(context);
    mapRef2RefFlowFunction_25.setPublishTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_29.setEventProcessorContext(context);
    mapRef2RefFlowFunction_31.setEventProcessorContext(context);
    mapRef2RefFlowFunction_33.setEventProcessorContext(context);
    mapRef2RefFlowFunction_33.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_35.setEventProcessorContext(context);
    mapRef2RefFlowFunction_35.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_38.setEventProcessorContext(context);
    mapRef2RefFlowFunction_38.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_38.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_40.setEventProcessorContext(context);
    mapRef2RefFlowFunction_42.setEventProcessorContext(context);
    mapRef2RefFlowFunction_42.setPublishTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_46.setEventProcessorContext(context);
    mapRef2RefFlowFunction_46.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_48.setEventProcessorContext(context);
    mapRef2RefFlowFunction_50.setEventProcessorContext(context);
    mapRef2RefFlowFunction_50.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_50.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_54.setEventProcessorContext(context);
    mapRef2RefFlowFunction_54.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_55.setEventProcessorContext(context);
    mapRef2RefFlowFunction_59.setEventProcessorContext(context);
    mapRef2RefFlowFunction_61.setEventProcessorContext(context);
    mapRef2RefFlowFunction_65.setEventProcessorContext(context);
    mapRef2RefFlowFunction_67.setEventProcessorContext(context);
    mapRef2RefFlowFunction_67.setPublishTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_68.setEventProcessorContext(context);
    mapRef2RefFlowFunction_70.setEventProcessorContext(context);
    mapRef2RefFlowFunction_72.setEventProcessorContext(context);
    mapRef2RefFlowFunction_76.setEventProcessorContext(context);
    mapRef2RefFlowFunction_76.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_78.setEventProcessorContext(context);
    mapRef2RefFlowFunction_80.setEventProcessorContext(context);
    mapRef2RefFlowFunction_80.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_80.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_84.setEventProcessorContext(context);
    mapRef2RefFlowFunction_86.setEventProcessorContext(context);
    mapRef2RefFlowFunction_86.setPublishTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_87.setEventProcessorContext(context);
    mapRef2RefFlowFunction_89.setEventProcessorContext(context);
    mapRef2RefFlowFunction_91.setEventProcessorContext(context);
    mapRef2RefFlowFunction_95.setEventProcessorContext(context);
    mapRef2RefFlowFunction_97.setEventProcessorContext(context);
    mapRef2RefFlowFunction_99.setEventProcessorContext(context);
    mapRef2RefFlowFunction_99.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_101.setEventProcessorContext(context);
    mapRef2RefFlowFunction_101.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_105.setEventProcessorContext(context);
    mapRef2RefFlowFunction_105.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mergeFlowFunction_6.dirtyStateMonitor = callbackDispatcher;
    pushFlowFunction_57.setEventProcessorContext(context);
    pushFlowFunction_107.setEventProcessorContext(context);
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
    namedFeedTableNode_109.initialise();
    namedFeedTableNode_109.init();
    derivedRateNode.init();
    eventFeedBatcher.init();
    positionCache.init();
    handlerPositionSnapshot.init();
    flatMapFlowFunction_17.init();
    flatMapFlowFunction_36.init();
    handlerSignal_positionSnapshotReset.init();
    handlerSignal_positionUpdate.init();
    handlerTrade.init();
    handlerTradeBatch.init();
    flatMapFlowFunction_3.init();
    mapRef2RefFlowFunction_4.initialiseEventStream();
    mapRef2RefFlowFunction_5.initialiseEventStream();
    mapRef2RefFlowFunction_19.initialiseEventStream();
    mapRef2RefFlowFunction_38.initialiseEventStream();
    mapRef2RefFlowFunction_68.initialiseEventStream();
    mapRef2RefFlowFunction_70.initialiseEventStream();
    mapRef2RefFlowFunction_72.initialiseEventStream();
    mapRef2RefFlowFunction_87.initialiseEventStream();
    mapRef2RefFlowFunction_89.initialiseEventStream();
    mapRef2RefFlowFunction_91.initialiseEventStream();
    tradeSequenceFilter_7.init();
    filterFlowFunction_8.initialiseEventStream();
    mapRef2RefFlowFunction_10.initialiseEventStream();
    mapRef2RefFlowFunction_12.initialiseEventStream();
    binaryMapToRefFlowFunction_21.initialiseEventStream();
    mapRef2RefFlowFunction_14.initialiseEventStream();
    mapRef2RefFlowFunction_16.initialiseEventStream();
    binaryMapToRefFlowFunction_82.initialiseEventStream();
    mapRef2RefFlowFunction_23.initialiseEventStream();
    mapRef2RefFlowFunction_25.initialiseEventStream();
    binaryMapToRefFlowFunction_27.initialiseEventStream();
    mapRef2RefFlowFunction_29.initialiseEventStream();
    mapRef2RefFlowFunction_31.initialiseEventStream();
    mapRef2RefFlowFunction_33.initialiseEventStream();
    mapRef2RefFlowFunction_35.initialiseEventStream();
    mapRef2RefFlowFunction_40.initialiseEventStream();
    mapRef2RefFlowFunction_42.initialiseEventStream();
    binaryMapToRefFlowFunction_44.initialiseEventStream();
    mapRef2RefFlowFunction_46.initialiseEventStream();
    mapRef2RefFlowFunction_48.initialiseEventStream();
    mapRef2RefFlowFunction_50.initialiseEventStream();
    binaryMapToRefFlowFunction_52.initialiseEventStream();
    mapRef2RefFlowFunction_54.initialiseEventStream();
    mapRef2RefFlowFunction_55.initialiseEventStream();
    globalNetMtm.initialiseEventStream();
    mapRef2RefFlowFunction_59.initialiseEventStream();
    mapRef2RefFlowFunction_61.initialiseEventStream();
    binaryMapToRefFlowFunction_63.initialiseEventStream();
    mapRef2RefFlowFunction_65.initialiseEventStream();
    mapRef2RefFlowFunction_67.initialiseEventStream();
    binaryMapToRefFlowFunction_74.initialiseEventStream();
    mapRef2RefFlowFunction_76.initialiseEventStream();
    mapRef2RefFlowFunction_78.initialiseEventStream();
    mapRef2RefFlowFunction_80.initialiseEventStream();
    mapRef2RefFlowFunction_84.initialiseEventStream();
    mapRef2RefFlowFunction_86.initialiseEventStream();
    binaryMapToRefFlowFunction_93.initialiseEventStream();
    mapRef2RefFlowFunction_95.initialiseEventStream();
    mapRef2RefFlowFunction_97.initialiseEventStream();
    mapRef2RefFlowFunction_99.initialiseEventStream();
    mapRef2RefFlowFunction_101.initialiseEventStream();
    binaryMapToRefFlowFunction_103.initialiseEventStream();
    mapRef2RefFlowFunction_105.initialiseEventStream();
    instrumentNetMtm.initialiseEventStream();
    pushFlowFunction_57.initialiseEventStream();
    pushFlowFunction_107.initialiseEventStream();
    binaryMapToRefFlowFunction_108.initialiseEventStream();
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
    auditInvocation(callBackNode_110, "callBackNode_110", "onEvent", typedEvent);
    isDirty_callBackNode_110 = callBackNode_110.onEvent(typedEvent);
    if (guardCheck_flatMapFlowFunction_3()) {
      auditInvocation(
          flatMapFlowFunction_3, "flatMapFlowFunction_3", "callbackReceived", typedEvent);
      isDirty_flatMapFlowFunction_3 = true;
      flatMapFlowFunction_3.callbackReceived();
      if (isDirty_flatMapFlowFunction_3) {
        mapRef2RefFlowFunction_4.inputUpdated(flatMapFlowFunction_3);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_4()) {
      auditInvocation(mapRef2RefFlowFunction_4, "mapRef2RefFlowFunction_4", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_4 = mapRef2RefFlowFunction_4.map();
      if (isDirty_mapRef2RefFlowFunction_4) {
        mergeFlowFunction_6.inputStreamUpdated(mapRef2RefFlowFunction_4);
      }
    }
    if (guardCheck_mergeFlowFunction_6()) {
      auditInvocation(mergeFlowFunction_6, "mergeFlowFunction_6", "publishMerge", typedEvent);
      isDirty_mergeFlowFunction_6 = mergeFlowFunction_6.publishMerge();
      if (isDirty_mergeFlowFunction_6) {
        filterFlowFunction_8.inputUpdated(mergeFlowFunction_6);
      }
    }
    if (guardCheck_filterFlowFunction_8()) {
      auditInvocation(filterFlowFunction_8, "filterFlowFunction_8", "filter", typedEvent);
      isDirty_filterFlowFunction_8 = filterFlowFunction_8.filter();
      if (isDirty_filterFlowFunction_8) {
        mapRef2RefFlowFunction_10.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_12.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_14.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_16.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_40.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_59.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_61.inputUpdated(filterFlowFunction_8);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      auditInvocation(mapRef2RefFlowFunction_10, "mapRef2RefFlowFunction_10", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_21.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_21.input2Updated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_21()) {
      auditInvocation(
          binaryMapToRefFlowFunction_21, "binaryMapToRefFlowFunction_21", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_21 = binaryMapToRefFlowFunction_21.map();
      if (isDirty_binaryMapToRefFlowFunction_21) {
        mapRef2RefFlowFunction_23.inputUpdated(binaryMapToRefFlowFunction_21);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_82.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_82.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_82()) {
      auditInvocation(
          binaryMapToRefFlowFunction_82, "binaryMapToRefFlowFunction_82", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_82 = binaryMapToRefFlowFunction_82.map();
      if (isDirty_binaryMapToRefFlowFunction_82) {
        mapRef2RefFlowFunction_84.inputUpdated(binaryMapToRefFlowFunction_82);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_23()) {
      auditInvocation(mapRef2RefFlowFunction_23, "mapRef2RefFlowFunction_23", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_23 = mapRef2RefFlowFunction_23.map();
      if (isDirty_mapRef2RefFlowFunction_23) {
        mapRef2RefFlowFunction_25.inputUpdated(mapRef2RefFlowFunction_23);
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
        mapRef2RefFlowFunction_33.inputUpdated(mapRef2RefFlowFunction_31);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_33()) {
      auditInvocation(mapRef2RefFlowFunction_33, "mapRef2RefFlowFunction_33", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_33 = mapRef2RefFlowFunction_33.map();
      if (isDirty_mapRef2RefFlowFunction_33) {
        mapRef2RefFlowFunction_35.inputUpdated(mapRef2RefFlowFunction_33);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_35()) {
      auditInvocation(mapRef2RefFlowFunction_35, "mapRef2RefFlowFunction_35", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_35 = mapRef2RefFlowFunction_35.map();
      if (isDirty_mapRef2RefFlowFunction_35) {
        binaryMapToRefFlowFunction_52.inputUpdated(mapRef2RefFlowFunction_35);
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
        binaryMapToRefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_42);
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
        mapRef2RefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_48()) {
      auditInvocation(mapRef2RefFlowFunction_48, "mapRef2RefFlowFunction_48", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_48 = mapRef2RefFlowFunction_48.map();
      if (isDirty_mapRef2RefFlowFunction_48) {
        mapRef2RefFlowFunction_50.inputUpdated(mapRef2RefFlowFunction_48);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_50()) {
      auditInvocation(mapRef2RefFlowFunction_50, "mapRef2RefFlowFunction_50", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
      if (isDirty_mapRef2RefFlowFunction_50) {
        binaryMapToRefFlowFunction_52.input2Updated(mapRef2RefFlowFunction_50);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_52()) {
      auditInvocation(
          binaryMapToRefFlowFunction_52, "binaryMapToRefFlowFunction_52", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_52 = binaryMapToRefFlowFunction_52.map();
      if (isDirty_binaryMapToRefFlowFunction_52) {
        mapRef2RefFlowFunction_54.inputUpdated(binaryMapToRefFlowFunction_52);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      auditInvocation(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        mapRef2RefFlowFunction_55.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_55()) {
      auditInvocation(mapRef2RefFlowFunction_55, "mapRef2RefFlowFunction_55", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_55 = mapRef2RefFlowFunction_55.map();
      if (isDirty_mapRef2RefFlowFunction_55) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_55);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "map", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_57.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_59()) {
      auditInvocation(mapRef2RefFlowFunction_59, "mapRef2RefFlowFunction_59", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_59 = mapRef2RefFlowFunction_59.map();
      if (isDirty_mapRef2RefFlowFunction_59) {
        binaryMapToRefFlowFunction_63.inputUpdated(mapRef2RefFlowFunction_59);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_61()) {
      auditInvocation(mapRef2RefFlowFunction_61, "mapRef2RefFlowFunction_61", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_61 = mapRef2RefFlowFunction_61.map();
      if (isDirty_mapRef2RefFlowFunction_61) {
        binaryMapToRefFlowFunction_63.input2Updated(mapRef2RefFlowFunction_61);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_63()) {
      auditInvocation(
          binaryMapToRefFlowFunction_63, "binaryMapToRefFlowFunction_63", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_63 = binaryMapToRefFlowFunction_63.map();
      if (isDirty_binaryMapToRefFlowFunction_63) {
        mapRef2RefFlowFunction_65.inputUpdated(binaryMapToRefFlowFunction_63);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_65()) {
      auditInvocation(mapRef2RefFlowFunction_65, "mapRef2RefFlowFunction_65", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_65 = mapRef2RefFlowFunction_65.map();
      if (isDirty_mapRef2RefFlowFunction_65) {
        mapRef2RefFlowFunction_67.inputUpdated(mapRef2RefFlowFunction_65);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_67()) {
      auditInvocation(mapRef2RefFlowFunction_67, "mapRef2RefFlowFunction_67", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_67 = mapRef2RefFlowFunction_67.map();
      if (isDirty_mapRef2RefFlowFunction_67) {
        binaryMapToRefFlowFunction_74.inputUpdated(mapRef2RefFlowFunction_67);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_74()) {
      auditInvocation(
          binaryMapToRefFlowFunction_74, "binaryMapToRefFlowFunction_74", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_74 = binaryMapToRefFlowFunction_74.map();
      if (isDirty_binaryMapToRefFlowFunction_74) {
        mapRef2RefFlowFunction_76.inputUpdated(binaryMapToRefFlowFunction_74);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_76()) {
      auditInvocation(mapRef2RefFlowFunction_76, "mapRef2RefFlowFunction_76", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_76 = mapRef2RefFlowFunction_76.map();
      if (isDirty_mapRef2RefFlowFunction_76) {
        mapRef2RefFlowFunction_78.inputUpdated(mapRef2RefFlowFunction_76);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_78()) {
      auditInvocation(mapRef2RefFlowFunction_78, "mapRef2RefFlowFunction_78", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_78 = mapRef2RefFlowFunction_78.map();
      if (isDirty_mapRef2RefFlowFunction_78) {
        mapRef2RefFlowFunction_80.inputUpdated(mapRef2RefFlowFunction_78);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_80()) {
      auditInvocation(mapRef2RefFlowFunction_80, "mapRef2RefFlowFunction_80", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_80 = mapRef2RefFlowFunction_80.map();
      if (isDirty_mapRef2RefFlowFunction_80) {
        binaryMapToRefFlowFunction_103.input2Updated(mapRef2RefFlowFunction_80);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_84()) {
      auditInvocation(mapRef2RefFlowFunction_84, "mapRef2RefFlowFunction_84", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_84 = mapRef2RefFlowFunction_84.map();
      if (isDirty_mapRef2RefFlowFunction_84) {
        mapRef2RefFlowFunction_86.inputUpdated(mapRef2RefFlowFunction_84);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_86()) {
      auditInvocation(mapRef2RefFlowFunction_86, "mapRef2RefFlowFunction_86", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_86 = mapRef2RefFlowFunction_86.map();
      if (isDirty_mapRef2RefFlowFunction_86) {
        binaryMapToRefFlowFunction_93.inputUpdated(mapRef2RefFlowFunction_86);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_93()) {
      auditInvocation(
          binaryMapToRefFlowFunction_93, "binaryMapToRefFlowFunction_93", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_93 = binaryMapToRefFlowFunction_93.map();
      if (isDirty_binaryMapToRefFlowFunction_93) {
        mapRef2RefFlowFunction_95.inputUpdated(binaryMapToRefFlowFunction_93);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_95()) {
      auditInvocation(mapRef2RefFlowFunction_95, "mapRef2RefFlowFunction_95", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_95 = mapRef2RefFlowFunction_95.map();
      if (isDirty_mapRef2RefFlowFunction_95) {
        mapRef2RefFlowFunction_97.inputUpdated(mapRef2RefFlowFunction_95);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_97()) {
      auditInvocation(mapRef2RefFlowFunction_97, "mapRef2RefFlowFunction_97", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_97 = mapRef2RefFlowFunction_97.map();
      if (isDirty_mapRef2RefFlowFunction_97) {
        mapRef2RefFlowFunction_99.inputUpdated(mapRef2RefFlowFunction_97);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_99()) {
      auditInvocation(mapRef2RefFlowFunction_99, "mapRef2RefFlowFunction_99", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_99 = mapRef2RefFlowFunction_99.map();
      if (isDirty_mapRef2RefFlowFunction_99) {
        mapRef2RefFlowFunction_101.inputUpdated(mapRef2RefFlowFunction_99);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_101()) {
      auditInvocation(mapRef2RefFlowFunction_101, "mapRef2RefFlowFunction_101", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_101 = mapRef2RefFlowFunction_101.map();
      if (isDirty_mapRef2RefFlowFunction_101) {
        binaryMapToRefFlowFunction_103.inputUpdated(mapRef2RefFlowFunction_101);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_103()) {
      auditInvocation(
          binaryMapToRefFlowFunction_103, "binaryMapToRefFlowFunction_103", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_103 = binaryMapToRefFlowFunction_103.map();
      if (isDirty_binaryMapToRefFlowFunction_103) {
        mapRef2RefFlowFunction_105.inputUpdated(binaryMapToRefFlowFunction_103);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_105()) {
      auditInvocation(mapRef2RefFlowFunction_105, "mapRef2RefFlowFunction_105", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_105 = mapRef2RefFlowFunction_105.map();
      if (isDirty_mapRef2RefFlowFunction_105) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_105);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_107.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_57()) {
      auditInvocation(pushFlowFunction_57, "pushFlowFunction_57", "push", typedEvent);
      isDirty_pushFlowFunction_57 = pushFlowFunction_57.push();
      if (isDirty_pushFlowFunction_57) {
        binaryMapToRefFlowFunction_108.inputUpdated(pushFlowFunction_57);
      }
    }
    if (guardCheck_pushFlowFunction_107()) {
      auditInvocation(pushFlowFunction_107, "pushFlowFunction_107", "push", typedEvent);
      isDirty_pushFlowFunction_107 = pushFlowFunction_107.push();
      if (isDirty_pushFlowFunction_107) {
        binaryMapToRefFlowFunction_108.input2Updated(pushFlowFunction_107);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_108()) {
      auditInvocation(
          binaryMapToRefFlowFunction_108, "binaryMapToRefFlowFunction_108", "map", typedEvent);
      binaryMapToRefFlowFunction_108.map();
    }
    afterEvent();
  }

  public void handleEvent(InstanceCallbackEvent_1 typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(callBackNode_134, "callBackNode_134", "onEvent", typedEvent);
    isDirty_callBackNode_134 = callBackNode_134.onEvent(typedEvent);
    if (guardCheck_flatMapFlowFunction_17()) {
      auditInvocation(
          flatMapFlowFunction_17, "flatMapFlowFunction_17", "callbackReceived", typedEvent);
      isDirty_flatMapFlowFunction_17 = true;
      flatMapFlowFunction_17.callbackReceived();
      if (isDirty_flatMapFlowFunction_17) {
        mapRef2RefFlowFunction_19.inputUpdated(flatMapFlowFunction_17);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_19()) {
      auditInvocation(mapRef2RefFlowFunction_19, "mapRef2RefFlowFunction_19", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_19 = mapRef2RefFlowFunction_19.map();
      if (isDirty_mapRef2RefFlowFunction_19) {
        binaryMapToRefFlowFunction_27.input2Updated(mapRef2RefFlowFunction_19);
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
        mapRef2RefFlowFunction_33.inputUpdated(mapRef2RefFlowFunction_31);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_33()) {
      auditInvocation(mapRef2RefFlowFunction_33, "mapRef2RefFlowFunction_33", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_33 = mapRef2RefFlowFunction_33.map();
      if (isDirty_mapRef2RefFlowFunction_33) {
        mapRef2RefFlowFunction_35.inputUpdated(mapRef2RefFlowFunction_33);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_35()) {
      auditInvocation(mapRef2RefFlowFunction_35, "mapRef2RefFlowFunction_35", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_35 = mapRef2RefFlowFunction_35.map();
      if (isDirty_mapRef2RefFlowFunction_35) {
        binaryMapToRefFlowFunction_52.inputUpdated(mapRef2RefFlowFunction_35);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_52()) {
      auditInvocation(
          binaryMapToRefFlowFunction_52, "binaryMapToRefFlowFunction_52", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_52 = binaryMapToRefFlowFunction_52.map();
      if (isDirty_binaryMapToRefFlowFunction_52) {
        mapRef2RefFlowFunction_54.inputUpdated(binaryMapToRefFlowFunction_52);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      auditInvocation(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        mapRef2RefFlowFunction_55.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_55()) {
      auditInvocation(mapRef2RefFlowFunction_55, "mapRef2RefFlowFunction_55", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_55 = mapRef2RefFlowFunction_55.map();
      if (isDirty_mapRef2RefFlowFunction_55) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_55);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "map", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_57.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_57()) {
      auditInvocation(pushFlowFunction_57, "pushFlowFunction_57", "push", typedEvent);
      isDirty_pushFlowFunction_57 = pushFlowFunction_57.push();
      if (isDirty_pushFlowFunction_57) {
        binaryMapToRefFlowFunction_108.inputUpdated(pushFlowFunction_57);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_108()) {
      auditInvocation(
          binaryMapToRefFlowFunction_108, "binaryMapToRefFlowFunction_108", "map", typedEvent);
      binaryMapToRefFlowFunction_108.map();
    }
    afterEvent();
  }

  public void handleEvent(InstanceCallbackEvent_2 typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(callBackNode_260, "callBackNode_260", "onEvent", typedEvent);
    isDirty_callBackNode_260 = callBackNode_260.onEvent(typedEvent);
    if (guardCheck_flatMapFlowFunction_36()) {
      auditInvocation(
          flatMapFlowFunction_36, "flatMapFlowFunction_36", "callbackReceived", typedEvent);
      isDirty_flatMapFlowFunction_36 = true;
      flatMapFlowFunction_36.callbackReceived();
      if (isDirty_flatMapFlowFunction_36) {
        mapRef2RefFlowFunction_38.inputUpdated(flatMapFlowFunction_36);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_38()) {
      auditInvocation(mapRef2RefFlowFunction_38, "mapRef2RefFlowFunction_38", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_38 = mapRef2RefFlowFunction_38.map();
      if (isDirty_mapRef2RefFlowFunction_38) {
        binaryMapToRefFlowFunction_44.input2Updated(mapRef2RefFlowFunction_38);
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
        mapRef2RefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_48()) {
      auditInvocation(mapRef2RefFlowFunction_48, "mapRef2RefFlowFunction_48", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_48 = mapRef2RefFlowFunction_48.map();
      if (isDirty_mapRef2RefFlowFunction_48) {
        mapRef2RefFlowFunction_50.inputUpdated(mapRef2RefFlowFunction_48);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_50()) {
      auditInvocation(mapRef2RefFlowFunction_50, "mapRef2RefFlowFunction_50", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
      if (isDirty_mapRef2RefFlowFunction_50) {
        binaryMapToRefFlowFunction_52.input2Updated(mapRef2RefFlowFunction_50);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_52()) {
      auditInvocation(
          binaryMapToRefFlowFunction_52, "binaryMapToRefFlowFunction_52", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_52 = binaryMapToRefFlowFunction_52.map();
      if (isDirty_binaryMapToRefFlowFunction_52) {
        mapRef2RefFlowFunction_54.inputUpdated(binaryMapToRefFlowFunction_52);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      auditInvocation(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        mapRef2RefFlowFunction_55.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_55()) {
      auditInvocation(mapRef2RefFlowFunction_55, "mapRef2RefFlowFunction_55", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_55 = mapRef2RefFlowFunction_55.map();
      if (isDirty_mapRef2RefFlowFunction_55) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_55);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "map", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_57.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_57()) {
      auditInvocation(pushFlowFunction_57, "pushFlowFunction_57", "push", typedEvent);
      isDirty_pushFlowFunction_57 = pushFlowFunction_57.push();
      if (isDirty_pushFlowFunction_57) {
        binaryMapToRefFlowFunction_108.inputUpdated(pushFlowFunction_57);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_108()) {
      auditInvocation(
          binaryMapToRefFlowFunction_108, "binaryMapToRefFlowFunction_108", "map", typedEvent);
      binaryMapToRefFlowFunction_108.map();
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
      flatMapFlowFunction_17.inputUpdatedAndFlatMap(handlerPositionSnapshot);
      flatMapFlowFunction_36.inputUpdatedAndFlatMap(handlerPositionSnapshot);
      mapRef2RefFlowFunction_68.inputUpdated(handlerPositionSnapshot);
      mapRef2RefFlowFunction_87.inputUpdated(handlerPositionSnapshot);
    }
    if (guardCheck_mapRef2RefFlowFunction_68()) {
      auditInvocation(mapRef2RefFlowFunction_68, "mapRef2RefFlowFunction_68", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_68 = mapRef2RefFlowFunction_68.map();
      if (isDirty_mapRef2RefFlowFunction_68) {
        mapRef2RefFlowFunction_70.inputUpdated(mapRef2RefFlowFunction_68);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_70()) {
      auditInvocation(mapRef2RefFlowFunction_70, "mapRef2RefFlowFunction_70", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_70 = mapRef2RefFlowFunction_70.map();
      if (isDirty_mapRef2RefFlowFunction_70) {
        mapRef2RefFlowFunction_72.inputUpdated(mapRef2RefFlowFunction_70);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_72()) {
      auditInvocation(mapRef2RefFlowFunction_72, "mapRef2RefFlowFunction_72", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_72 = mapRef2RefFlowFunction_72.map();
      if (isDirty_mapRef2RefFlowFunction_72) {
        binaryMapToRefFlowFunction_74.input2Updated(mapRef2RefFlowFunction_72);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_87()) {
      auditInvocation(mapRef2RefFlowFunction_87, "mapRef2RefFlowFunction_87", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_87 = mapRef2RefFlowFunction_87.map();
      if (isDirty_mapRef2RefFlowFunction_87) {
        mapRef2RefFlowFunction_89.inputUpdated(mapRef2RefFlowFunction_87);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_89()) {
      auditInvocation(mapRef2RefFlowFunction_89, "mapRef2RefFlowFunction_89", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_89 = mapRef2RefFlowFunction_89.map();
      if (isDirty_mapRef2RefFlowFunction_89) {
        mapRef2RefFlowFunction_91.inputUpdated(mapRef2RefFlowFunction_89);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_91()) {
      auditInvocation(mapRef2RefFlowFunction_91, "mapRef2RefFlowFunction_91", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_91 = mapRef2RefFlowFunction_91.map();
      if (isDirty_mapRef2RefFlowFunction_91) {
        binaryMapToRefFlowFunction_93.input2Updated(mapRef2RefFlowFunction_91);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_74()) {
      auditInvocation(
          binaryMapToRefFlowFunction_74, "binaryMapToRefFlowFunction_74", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_74 = binaryMapToRefFlowFunction_74.map();
      if (isDirty_binaryMapToRefFlowFunction_74) {
        mapRef2RefFlowFunction_76.inputUpdated(binaryMapToRefFlowFunction_74);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_76()) {
      auditInvocation(mapRef2RefFlowFunction_76, "mapRef2RefFlowFunction_76", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_76 = mapRef2RefFlowFunction_76.map();
      if (isDirty_mapRef2RefFlowFunction_76) {
        mapRef2RefFlowFunction_78.inputUpdated(mapRef2RefFlowFunction_76);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_78()) {
      auditInvocation(mapRef2RefFlowFunction_78, "mapRef2RefFlowFunction_78", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_78 = mapRef2RefFlowFunction_78.map();
      if (isDirty_mapRef2RefFlowFunction_78) {
        mapRef2RefFlowFunction_80.inputUpdated(mapRef2RefFlowFunction_78);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_80()) {
      auditInvocation(mapRef2RefFlowFunction_80, "mapRef2RefFlowFunction_80", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_80 = mapRef2RefFlowFunction_80.map();
      if (isDirty_mapRef2RefFlowFunction_80) {
        binaryMapToRefFlowFunction_103.input2Updated(mapRef2RefFlowFunction_80);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_93()) {
      auditInvocation(
          binaryMapToRefFlowFunction_93, "binaryMapToRefFlowFunction_93", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_93 = binaryMapToRefFlowFunction_93.map();
      if (isDirty_binaryMapToRefFlowFunction_93) {
        mapRef2RefFlowFunction_95.inputUpdated(binaryMapToRefFlowFunction_93);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_95()) {
      auditInvocation(mapRef2RefFlowFunction_95, "mapRef2RefFlowFunction_95", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_95 = mapRef2RefFlowFunction_95.map();
      if (isDirty_mapRef2RefFlowFunction_95) {
        mapRef2RefFlowFunction_97.inputUpdated(mapRef2RefFlowFunction_95);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_97()) {
      auditInvocation(mapRef2RefFlowFunction_97, "mapRef2RefFlowFunction_97", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_97 = mapRef2RefFlowFunction_97.map();
      if (isDirty_mapRef2RefFlowFunction_97) {
        mapRef2RefFlowFunction_99.inputUpdated(mapRef2RefFlowFunction_97);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_99()) {
      auditInvocation(mapRef2RefFlowFunction_99, "mapRef2RefFlowFunction_99", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_99 = mapRef2RefFlowFunction_99.map();
      if (isDirty_mapRef2RefFlowFunction_99) {
        mapRef2RefFlowFunction_101.inputUpdated(mapRef2RefFlowFunction_99);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_101()) {
      auditInvocation(mapRef2RefFlowFunction_101, "mapRef2RefFlowFunction_101", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_101 = mapRef2RefFlowFunction_101.map();
      if (isDirty_mapRef2RefFlowFunction_101) {
        binaryMapToRefFlowFunction_103.inputUpdated(mapRef2RefFlowFunction_101);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_103()) {
      auditInvocation(
          binaryMapToRefFlowFunction_103, "binaryMapToRefFlowFunction_103", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_103 = binaryMapToRefFlowFunction_103.map();
      if (isDirty_binaryMapToRefFlowFunction_103) {
        mapRef2RefFlowFunction_105.inputUpdated(binaryMapToRefFlowFunction_103);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_105()) {
      auditInvocation(mapRef2RefFlowFunction_105, "mapRef2RefFlowFunction_105", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_105 = mapRef2RefFlowFunction_105.map();
      if (isDirty_mapRef2RefFlowFunction_105) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_105);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_107.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_107()) {
      auditInvocation(pushFlowFunction_107, "pushFlowFunction_107", "push", typedEvent);
      isDirty_pushFlowFunction_107 = pushFlowFunction_107.push();
      if (isDirty_pushFlowFunction_107) {
        binaryMapToRefFlowFunction_108.input2Updated(pushFlowFunction_107);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_108()) {
      auditInvocation(
          binaryMapToRefFlowFunction_108, "binaryMapToRefFlowFunction_108", "map", typedEvent);
      binaryMapToRefFlowFunction_108.map();
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
      mapRef2RefFlowFunction_5.inputUpdated(handlerTrade);
    }
    if (guardCheck_mapRef2RefFlowFunction_5()) {
      auditInvocation(mapRef2RefFlowFunction_5, "mapRef2RefFlowFunction_5", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_5 = mapRef2RefFlowFunction_5.map();
      if (isDirty_mapRef2RefFlowFunction_5) {
        mergeFlowFunction_6.inputStreamUpdated(mapRef2RefFlowFunction_5);
      }
    }
    if (guardCheck_mergeFlowFunction_6()) {
      auditInvocation(mergeFlowFunction_6, "mergeFlowFunction_6", "publishMerge", typedEvent);
      isDirty_mergeFlowFunction_6 = mergeFlowFunction_6.publishMerge();
      if (isDirty_mergeFlowFunction_6) {
        filterFlowFunction_8.inputUpdated(mergeFlowFunction_6);
      }
    }
    if (guardCheck_filterFlowFunction_8()) {
      auditInvocation(filterFlowFunction_8, "filterFlowFunction_8", "filter", typedEvent);
      isDirty_filterFlowFunction_8 = filterFlowFunction_8.filter();
      if (isDirty_filterFlowFunction_8) {
        mapRef2RefFlowFunction_10.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_12.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_14.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_16.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_40.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_59.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_61.inputUpdated(filterFlowFunction_8);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      auditInvocation(mapRef2RefFlowFunction_10, "mapRef2RefFlowFunction_10", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_21.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_21.input2Updated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_21()) {
      auditInvocation(
          binaryMapToRefFlowFunction_21, "binaryMapToRefFlowFunction_21", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_21 = binaryMapToRefFlowFunction_21.map();
      if (isDirty_binaryMapToRefFlowFunction_21) {
        mapRef2RefFlowFunction_23.inputUpdated(binaryMapToRefFlowFunction_21);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_82.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_82.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_82()) {
      auditInvocation(
          binaryMapToRefFlowFunction_82, "binaryMapToRefFlowFunction_82", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_82 = binaryMapToRefFlowFunction_82.map();
      if (isDirty_binaryMapToRefFlowFunction_82) {
        mapRef2RefFlowFunction_84.inputUpdated(binaryMapToRefFlowFunction_82);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_23()) {
      auditInvocation(mapRef2RefFlowFunction_23, "mapRef2RefFlowFunction_23", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_23 = mapRef2RefFlowFunction_23.map();
      if (isDirty_mapRef2RefFlowFunction_23) {
        mapRef2RefFlowFunction_25.inputUpdated(mapRef2RefFlowFunction_23);
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
        mapRef2RefFlowFunction_33.inputUpdated(mapRef2RefFlowFunction_31);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_33()) {
      auditInvocation(mapRef2RefFlowFunction_33, "mapRef2RefFlowFunction_33", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_33 = mapRef2RefFlowFunction_33.map();
      if (isDirty_mapRef2RefFlowFunction_33) {
        mapRef2RefFlowFunction_35.inputUpdated(mapRef2RefFlowFunction_33);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_35()) {
      auditInvocation(mapRef2RefFlowFunction_35, "mapRef2RefFlowFunction_35", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_35 = mapRef2RefFlowFunction_35.map();
      if (isDirty_mapRef2RefFlowFunction_35) {
        binaryMapToRefFlowFunction_52.inputUpdated(mapRef2RefFlowFunction_35);
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
        binaryMapToRefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_42);
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
        mapRef2RefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_48()) {
      auditInvocation(mapRef2RefFlowFunction_48, "mapRef2RefFlowFunction_48", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_48 = mapRef2RefFlowFunction_48.map();
      if (isDirty_mapRef2RefFlowFunction_48) {
        mapRef2RefFlowFunction_50.inputUpdated(mapRef2RefFlowFunction_48);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_50()) {
      auditInvocation(mapRef2RefFlowFunction_50, "mapRef2RefFlowFunction_50", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
      if (isDirty_mapRef2RefFlowFunction_50) {
        binaryMapToRefFlowFunction_52.input2Updated(mapRef2RefFlowFunction_50);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_52()) {
      auditInvocation(
          binaryMapToRefFlowFunction_52, "binaryMapToRefFlowFunction_52", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_52 = binaryMapToRefFlowFunction_52.map();
      if (isDirty_binaryMapToRefFlowFunction_52) {
        mapRef2RefFlowFunction_54.inputUpdated(binaryMapToRefFlowFunction_52);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      auditInvocation(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        mapRef2RefFlowFunction_55.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_55()) {
      auditInvocation(mapRef2RefFlowFunction_55, "mapRef2RefFlowFunction_55", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_55 = mapRef2RefFlowFunction_55.map();
      if (isDirty_mapRef2RefFlowFunction_55) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_55);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "map", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_57.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_59()) {
      auditInvocation(mapRef2RefFlowFunction_59, "mapRef2RefFlowFunction_59", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_59 = mapRef2RefFlowFunction_59.map();
      if (isDirty_mapRef2RefFlowFunction_59) {
        binaryMapToRefFlowFunction_63.inputUpdated(mapRef2RefFlowFunction_59);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_61()) {
      auditInvocation(mapRef2RefFlowFunction_61, "mapRef2RefFlowFunction_61", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_61 = mapRef2RefFlowFunction_61.map();
      if (isDirty_mapRef2RefFlowFunction_61) {
        binaryMapToRefFlowFunction_63.input2Updated(mapRef2RefFlowFunction_61);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_63()) {
      auditInvocation(
          binaryMapToRefFlowFunction_63, "binaryMapToRefFlowFunction_63", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_63 = binaryMapToRefFlowFunction_63.map();
      if (isDirty_binaryMapToRefFlowFunction_63) {
        mapRef2RefFlowFunction_65.inputUpdated(binaryMapToRefFlowFunction_63);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_65()) {
      auditInvocation(mapRef2RefFlowFunction_65, "mapRef2RefFlowFunction_65", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_65 = mapRef2RefFlowFunction_65.map();
      if (isDirty_mapRef2RefFlowFunction_65) {
        mapRef2RefFlowFunction_67.inputUpdated(mapRef2RefFlowFunction_65);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_67()) {
      auditInvocation(mapRef2RefFlowFunction_67, "mapRef2RefFlowFunction_67", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_67 = mapRef2RefFlowFunction_67.map();
      if (isDirty_mapRef2RefFlowFunction_67) {
        binaryMapToRefFlowFunction_74.inputUpdated(mapRef2RefFlowFunction_67);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_74()) {
      auditInvocation(
          binaryMapToRefFlowFunction_74, "binaryMapToRefFlowFunction_74", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_74 = binaryMapToRefFlowFunction_74.map();
      if (isDirty_binaryMapToRefFlowFunction_74) {
        mapRef2RefFlowFunction_76.inputUpdated(binaryMapToRefFlowFunction_74);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_76()) {
      auditInvocation(mapRef2RefFlowFunction_76, "mapRef2RefFlowFunction_76", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_76 = mapRef2RefFlowFunction_76.map();
      if (isDirty_mapRef2RefFlowFunction_76) {
        mapRef2RefFlowFunction_78.inputUpdated(mapRef2RefFlowFunction_76);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_78()) {
      auditInvocation(mapRef2RefFlowFunction_78, "mapRef2RefFlowFunction_78", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_78 = mapRef2RefFlowFunction_78.map();
      if (isDirty_mapRef2RefFlowFunction_78) {
        mapRef2RefFlowFunction_80.inputUpdated(mapRef2RefFlowFunction_78);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_80()) {
      auditInvocation(mapRef2RefFlowFunction_80, "mapRef2RefFlowFunction_80", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_80 = mapRef2RefFlowFunction_80.map();
      if (isDirty_mapRef2RefFlowFunction_80) {
        binaryMapToRefFlowFunction_103.input2Updated(mapRef2RefFlowFunction_80);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_84()) {
      auditInvocation(mapRef2RefFlowFunction_84, "mapRef2RefFlowFunction_84", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_84 = mapRef2RefFlowFunction_84.map();
      if (isDirty_mapRef2RefFlowFunction_84) {
        mapRef2RefFlowFunction_86.inputUpdated(mapRef2RefFlowFunction_84);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_86()) {
      auditInvocation(mapRef2RefFlowFunction_86, "mapRef2RefFlowFunction_86", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_86 = mapRef2RefFlowFunction_86.map();
      if (isDirty_mapRef2RefFlowFunction_86) {
        binaryMapToRefFlowFunction_93.inputUpdated(mapRef2RefFlowFunction_86);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_93()) {
      auditInvocation(
          binaryMapToRefFlowFunction_93, "binaryMapToRefFlowFunction_93", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_93 = binaryMapToRefFlowFunction_93.map();
      if (isDirty_binaryMapToRefFlowFunction_93) {
        mapRef2RefFlowFunction_95.inputUpdated(binaryMapToRefFlowFunction_93);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_95()) {
      auditInvocation(mapRef2RefFlowFunction_95, "mapRef2RefFlowFunction_95", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_95 = mapRef2RefFlowFunction_95.map();
      if (isDirty_mapRef2RefFlowFunction_95) {
        mapRef2RefFlowFunction_97.inputUpdated(mapRef2RefFlowFunction_95);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_97()) {
      auditInvocation(mapRef2RefFlowFunction_97, "mapRef2RefFlowFunction_97", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_97 = mapRef2RefFlowFunction_97.map();
      if (isDirty_mapRef2RefFlowFunction_97) {
        mapRef2RefFlowFunction_99.inputUpdated(mapRef2RefFlowFunction_97);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_99()) {
      auditInvocation(mapRef2RefFlowFunction_99, "mapRef2RefFlowFunction_99", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_99 = mapRef2RefFlowFunction_99.map();
      if (isDirty_mapRef2RefFlowFunction_99) {
        mapRef2RefFlowFunction_101.inputUpdated(mapRef2RefFlowFunction_99);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_101()) {
      auditInvocation(mapRef2RefFlowFunction_101, "mapRef2RefFlowFunction_101", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_101 = mapRef2RefFlowFunction_101.map();
      if (isDirty_mapRef2RefFlowFunction_101) {
        binaryMapToRefFlowFunction_103.inputUpdated(mapRef2RefFlowFunction_101);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_103()) {
      auditInvocation(
          binaryMapToRefFlowFunction_103, "binaryMapToRefFlowFunction_103", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_103 = binaryMapToRefFlowFunction_103.map();
      if (isDirty_binaryMapToRefFlowFunction_103) {
        mapRef2RefFlowFunction_105.inputUpdated(binaryMapToRefFlowFunction_103);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_105()) {
      auditInvocation(mapRef2RefFlowFunction_105, "mapRef2RefFlowFunction_105", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_105 = mapRef2RefFlowFunction_105.map();
      if (isDirty_mapRef2RefFlowFunction_105) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_105);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_107.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_57()) {
      auditInvocation(pushFlowFunction_57, "pushFlowFunction_57", "push", typedEvent);
      isDirty_pushFlowFunction_57 = pushFlowFunction_57.push();
      if (isDirty_pushFlowFunction_57) {
        binaryMapToRefFlowFunction_108.inputUpdated(pushFlowFunction_57);
      }
    }
    if (guardCheck_pushFlowFunction_107()) {
      auditInvocation(pushFlowFunction_107, "pushFlowFunction_107", "push", typedEvent);
      isDirty_pushFlowFunction_107 = pushFlowFunction_107.push();
      if (isDirty_pushFlowFunction_107) {
        binaryMapToRefFlowFunction_108.input2Updated(pushFlowFunction_107);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_108()) {
      auditInvocation(
          binaryMapToRefFlowFunction_108, "binaryMapToRefFlowFunction_108", "map", typedEvent);
      binaryMapToRefFlowFunction_108.map();
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
      mapRef2RefFlowFunction_19.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_38.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_10.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_12.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_14.publishTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_14.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_16.publishTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_16.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_46.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_76.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
    }
    if (guardCheck_mapRef2RefFlowFunction_19()) {
      auditInvocation(mapRef2RefFlowFunction_19, "mapRef2RefFlowFunction_19", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_19 = mapRef2RefFlowFunction_19.map();
      if (isDirty_mapRef2RefFlowFunction_19) {
        binaryMapToRefFlowFunction_27.input2Updated(mapRef2RefFlowFunction_19);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_38()) {
      auditInvocation(mapRef2RefFlowFunction_38, "mapRef2RefFlowFunction_38", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_38 = mapRef2RefFlowFunction_38.map();
      if (isDirty_mapRef2RefFlowFunction_38) {
        binaryMapToRefFlowFunction_44.input2Updated(mapRef2RefFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      auditInvocation(mapRef2RefFlowFunction_10, "mapRef2RefFlowFunction_10", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_21.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_21.input2Updated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_21()) {
      auditInvocation(
          binaryMapToRefFlowFunction_21, "binaryMapToRefFlowFunction_21", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_21 = binaryMapToRefFlowFunction_21.map();
      if (isDirty_binaryMapToRefFlowFunction_21) {
        mapRef2RefFlowFunction_23.inputUpdated(binaryMapToRefFlowFunction_21);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_82.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_82.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_82()) {
      auditInvocation(
          binaryMapToRefFlowFunction_82, "binaryMapToRefFlowFunction_82", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_82 = binaryMapToRefFlowFunction_82.map();
      if (isDirty_binaryMapToRefFlowFunction_82) {
        mapRef2RefFlowFunction_84.inputUpdated(binaryMapToRefFlowFunction_82);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_23()) {
      auditInvocation(mapRef2RefFlowFunction_23, "mapRef2RefFlowFunction_23", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_23 = mapRef2RefFlowFunction_23.map();
      if (isDirty_mapRef2RefFlowFunction_23) {
        mapRef2RefFlowFunction_25.inputUpdated(mapRef2RefFlowFunction_23);
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
        mapRef2RefFlowFunction_33.inputUpdated(mapRef2RefFlowFunction_31);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_33()) {
      auditInvocation(mapRef2RefFlowFunction_33, "mapRef2RefFlowFunction_33", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_33 = mapRef2RefFlowFunction_33.map();
      if (isDirty_mapRef2RefFlowFunction_33) {
        mapRef2RefFlowFunction_35.inputUpdated(mapRef2RefFlowFunction_33);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_35()) {
      auditInvocation(mapRef2RefFlowFunction_35, "mapRef2RefFlowFunction_35", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_35 = mapRef2RefFlowFunction_35.map();
      if (isDirty_mapRef2RefFlowFunction_35) {
        binaryMapToRefFlowFunction_52.inputUpdated(mapRef2RefFlowFunction_35);
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
        mapRef2RefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_48()) {
      auditInvocation(mapRef2RefFlowFunction_48, "mapRef2RefFlowFunction_48", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_48 = mapRef2RefFlowFunction_48.map();
      if (isDirty_mapRef2RefFlowFunction_48) {
        mapRef2RefFlowFunction_50.inputUpdated(mapRef2RefFlowFunction_48);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_50()) {
      auditInvocation(mapRef2RefFlowFunction_50, "mapRef2RefFlowFunction_50", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
      if (isDirty_mapRef2RefFlowFunction_50) {
        binaryMapToRefFlowFunction_52.input2Updated(mapRef2RefFlowFunction_50);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_52()) {
      auditInvocation(
          binaryMapToRefFlowFunction_52, "binaryMapToRefFlowFunction_52", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_52 = binaryMapToRefFlowFunction_52.map();
      if (isDirty_binaryMapToRefFlowFunction_52) {
        mapRef2RefFlowFunction_54.inputUpdated(binaryMapToRefFlowFunction_52);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      auditInvocation(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        mapRef2RefFlowFunction_55.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_55()) {
      auditInvocation(mapRef2RefFlowFunction_55, "mapRef2RefFlowFunction_55", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_55 = mapRef2RefFlowFunction_55.map();
      if (isDirty_mapRef2RefFlowFunction_55) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_55);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "map", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_57.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_76()) {
      auditInvocation(mapRef2RefFlowFunction_76, "mapRef2RefFlowFunction_76", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_76 = mapRef2RefFlowFunction_76.map();
      if (isDirty_mapRef2RefFlowFunction_76) {
        mapRef2RefFlowFunction_78.inputUpdated(mapRef2RefFlowFunction_76);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_78()) {
      auditInvocation(mapRef2RefFlowFunction_78, "mapRef2RefFlowFunction_78", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_78 = mapRef2RefFlowFunction_78.map();
      if (isDirty_mapRef2RefFlowFunction_78) {
        mapRef2RefFlowFunction_80.inputUpdated(mapRef2RefFlowFunction_78);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_80()) {
      auditInvocation(mapRef2RefFlowFunction_80, "mapRef2RefFlowFunction_80", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_80 = mapRef2RefFlowFunction_80.map();
      if (isDirty_mapRef2RefFlowFunction_80) {
        binaryMapToRefFlowFunction_103.input2Updated(mapRef2RefFlowFunction_80);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_84()) {
      auditInvocation(mapRef2RefFlowFunction_84, "mapRef2RefFlowFunction_84", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_84 = mapRef2RefFlowFunction_84.map();
      if (isDirty_mapRef2RefFlowFunction_84) {
        mapRef2RefFlowFunction_86.inputUpdated(mapRef2RefFlowFunction_84);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_86()) {
      auditInvocation(mapRef2RefFlowFunction_86, "mapRef2RefFlowFunction_86", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_86 = mapRef2RefFlowFunction_86.map();
      if (isDirty_mapRef2RefFlowFunction_86) {
        binaryMapToRefFlowFunction_93.inputUpdated(mapRef2RefFlowFunction_86);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_93()) {
      auditInvocation(
          binaryMapToRefFlowFunction_93, "binaryMapToRefFlowFunction_93", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_93 = binaryMapToRefFlowFunction_93.map();
      if (isDirty_binaryMapToRefFlowFunction_93) {
        mapRef2RefFlowFunction_95.inputUpdated(binaryMapToRefFlowFunction_93);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_95()) {
      auditInvocation(mapRef2RefFlowFunction_95, "mapRef2RefFlowFunction_95", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_95 = mapRef2RefFlowFunction_95.map();
      if (isDirty_mapRef2RefFlowFunction_95) {
        mapRef2RefFlowFunction_97.inputUpdated(mapRef2RefFlowFunction_95);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_97()) {
      auditInvocation(mapRef2RefFlowFunction_97, "mapRef2RefFlowFunction_97", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_97 = mapRef2RefFlowFunction_97.map();
      if (isDirty_mapRef2RefFlowFunction_97) {
        mapRef2RefFlowFunction_99.inputUpdated(mapRef2RefFlowFunction_97);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_99()) {
      auditInvocation(mapRef2RefFlowFunction_99, "mapRef2RefFlowFunction_99", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_99 = mapRef2RefFlowFunction_99.map();
      if (isDirty_mapRef2RefFlowFunction_99) {
        mapRef2RefFlowFunction_101.inputUpdated(mapRef2RefFlowFunction_99);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_101()) {
      auditInvocation(mapRef2RefFlowFunction_101, "mapRef2RefFlowFunction_101", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_101 = mapRef2RefFlowFunction_101.map();
      if (isDirty_mapRef2RefFlowFunction_101) {
        binaryMapToRefFlowFunction_103.inputUpdated(mapRef2RefFlowFunction_101);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_103()) {
      auditInvocation(
          binaryMapToRefFlowFunction_103, "binaryMapToRefFlowFunction_103", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_103 = binaryMapToRefFlowFunction_103.map();
      if (isDirty_binaryMapToRefFlowFunction_103) {
        mapRef2RefFlowFunction_105.inputUpdated(binaryMapToRefFlowFunction_103);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_105()) {
      auditInvocation(mapRef2RefFlowFunction_105, "mapRef2RefFlowFunction_105", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_105 = mapRef2RefFlowFunction_105.map();
      if (isDirty_mapRef2RefFlowFunction_105) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_105);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_107.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_57()) {
      auditInvocation(pushFlowFunction_57, "pushFlowFunction_57", "push", typedEvent);
      isDirty_pushFlowFunction_57 = pushFlowFunction_57.push();
      if (isDirty_pushFlowFunction_57) {
        binaryMapToRefFlowFunction_108.inputUpdated(pushFlowFunction_57);
      }
    }
    if (guardCheck_pushFlowFunction_107()) {
      auditInvocation(pushFlowFunction_107, "pushFlowFunction_107", "push", typedEvent);
      isDirty_pushFlowFunction_107 = pushFlowFunction_107.push();
      if (isDirty_pushFlowFunction_107) {
        binaryMapToRefFlowFunction_108.input2Updated(pushFlowFunction_107);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_108()) {
      auditInvocation(
          binaryMapToRefFlowFunction_108, "binaryMapToRefFlowFunction_108", "map", typedEvent);
      binaryMapToRefFlowFunction_108.map();
    }
  }

  private void handle_Signal_positionUpdate(Signal typedEvent) {
    auditInvocation(
        handlerSignal_positionUpdate, "handlerSignal_positionUpdate", "onEvent", typedEvent);
    isDirty_handlerSignal_positionUpdate = handlerSignal_positionUpdate.onEvent(typedEvent);
    if (isDirty_handlerSignal_positionUpdate) {
      mapRef2RefFlowFunction_19.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_38.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_25.publishTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_33.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_35.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_42.publishTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_50.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_50.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_54.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_67.publishTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_80.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_80.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_86.publishTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_99.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_101.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_105.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
    }
    if (guardCheck_mapRef2RefFlowFunction_19()) {
      auditInvocation(mapRef2RefFlowFunction_19, "mapRef2RefFlowFunction_19", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_19 = mapRef2RefFlowFunction_19.map();
      if (isDirty_mapRef2RefFlowFunction_19) {
        binaryMapToRefFlowFunction_27.input2Updated(mapRef2RefFlowFunction_19);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_38()) {
      auditInvocation(mapRef2RefFlowFunction_38, "mapRef2RefFlowFunction_38", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_38 = mapRef2RefFlowFunction_38.map();
      if (isDirty_mapRef2RefFlowFunction_38) {
        binaryMapToRefFlowFunction_44.input2Updated(mapRef2RefFlowFunction_38);
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
        mapRef2RefFlowFunction_33.inputUpdated(mapRef2RefFlowFunction_31);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_33()) {
      auditInvocation(mapRef2RefFlowFunction_33, "mapRef2RefFlowFunction_33", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_33 = mapRef2RefFlowFunction_33.map();
      if (isDirty_mapRef2RefFlowFunction_33) {
        mapRef2RefFlowFunction_35.inputUpdated(mapRef2RefFlowFunction_33);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_35()) {
      auditInvocation(mapRef2RefFlowFunction_35, "mapRef2RefFlowFunction_35", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_35 = mapRef2RefFlowFunction_35.map();
      if (isDirty_mapRef2RefFlowFunction_35) {
        binaryMapToRefFlowFunction_52.inputUpdated(mapRef2RefFlowFunction_35);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_42()) {
      auditInvocation(mapRef2RefFlowFunction_42, "mapRef2RefFlowFunction_42", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
      if (isDirty_mapRef2RefFlowFunction_42) {
        binaryMapToRefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_42);
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
        mapRef2RefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_48()) {
      auditInvocation(mapRef2RefFlowFunction_48, "mapRef2RefFlowFunction_48", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_48 = mapRef2RefFlowFunction_48.map();
      if (isDirty_mapRef2RefFlowFunction_48) {
        mapRef2RefFlowFunction_50.inputUpdated(mapRef2RefFlowFunction_48);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_50()) {
      auditInvocation(mapRef2RefFlowFunction_50, "mapRef2RefFlowFunction_50", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
      if (isDirty_mapRef2RefFlowFunction_50) {
        binaryMapToRefFlowFunction_52.input2Updated(mapRef2RefFlowFunction_50);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_52()) {
      auditInvocation(
          binaryMapToRefFlowFunction_52, "binaryMapToRefFlowFunction_52", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_52 = binaryMapToRefFlowFunction_52.map();
      if (isDirty_binaryMapToRefFlowFunction_52) {
        mapRef2RefFlowFunction_54.inputUpdated(binaryMapToRefFlowFunction_52);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      auditInvocation(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        mapRef2RefFlowFunction_55.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_55()) {
      auditInvocation(mapRef2RefFlowFunction_55, "mapRef2RefFlowFunction_55", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_55 = mapRef2RefFlowFunction_55.map();
      if (isDirty_mapRef2RefFlowFunction_55) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_55);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "map", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_57.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_67()) {
      auditInvocation(mapRef2RefFlowFunction_67, "mapRef2RefFlowFunction_67", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_67 = mapRef2RefFlowFunction_67.map();
      if (isDirty_mapRef2RefFlowFunction_67) {
        binaryMapToRefFlowFunction_74.inputUpdated(mapRef2RefFlowFunction_67);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_74()) {
      auditInvocation(
          binaryMapToRefFlowFunction_74, "binaryMapToRefFlowFunction_74", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_74 = binaryMapToRefFlowFunction_74.map();
      if (isDirty_binaryMapToRefFlowFunction_74) {
        mapRef2RefFlowFunction_76.inputUpdated(binaryMapToRefFlowFunction_74);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_76()) {
      auditInvocation(mapRef2RefFlowFunction_76, "mapRef2RefFlowFunction_76", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_76 = mapRef2RefFlowFunction_76.map();
      if (isDirty_mapRef2RefFlowFunction_76) {
        mapRef2RefFlowFunction_78.inputUpdated(mapRef2RefFlowFunction_76);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_78()) {
      auditInvocation(mapRef2RefFlowFunction_78, "mapRef2RefFlowFunction_78", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_78 = mapRef2RefFlowFunction_78.map();
      if (isDirty_mapRef2RefFlowFunction_78) {
        mapRef2RefFlowFunction_80.inputUpdated(mapRef2RefFlowFunction_78);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_80()) {
      auditInvocation(mapRef2RefFlowFunction_80, "mapRef2RefFlowFunction_80", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_80 = mapRef2RefFlowFunction_80.map();
      if (isDirty_mapRef2RefFlowFunction_80) {
        binaryMapToRefFlowFunction_103.input2Updated(mapRef2RefFlowFunction_80);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_86()) {
      auditInvocation(mapRef2RefFlowFunction_86, "mapRef2RefFlowFunction_86", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_86 = mapRef2RefFlowFunction_86.map();
      if (isDirty_mapRef2RefFlowFunction_86) {
        binaryMapToRefFlowFunction_93.inputUpdated(mapRef2RefFlowFunction_86);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_93()) {
      auditInvocation(
          binaryMapToRefFlowFunction_93, "binaryMapToRefFlowFunction_93", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_93 = binaryMapToRefFlowFunction_93.map();
      if (isDirty_binaryMapToRefFlowFunction_93) {
        mapRef2RefFlowFunction_95.inputUpdated(binaryMapToRefFlowFunction_93);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_95()) {
      auditInvocation(mapRef2RefFlowFunction_95, "mapRef2RefFlowFunction_95", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_95 = mapRef2RefFlowFunction_95.map();
      if (isDirty_mapRef2RefFlowFunction_95) {
        mapRef2RefFlowFunction_97.inputUpdated(mapRef2RefFlowFunction_95);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_97()) {
      auditInvocation(mapRef2RefFlowFunction_97, "mapRef2RefFlowFunction_97", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_97 = mapRef2RefFlowFunction_97.map();
      if (isDirty_mapRef2RefFlowFunction_97) {
        mapRef2RefFlowFunction_99.inputUpdated(mapRef2RefFlowFunction_97);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_99()) {
      auditInvocation(mapRef2RefFlowFunction_99, "mapRef2RefFlowFunction_99", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_99 = mapRef2RefFlowFunction_99.map();
      if (isDirty_mapRef2RefFlowFunction_99) {
        mapRef2RefFlowFunction_101.inputUpdated(mapRef2RefFlowFunction_99);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_101()) {
      auditInvocation(mapRef2RefFlowFunction_101, "mapRef2RefFlowFunction_101", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_101 = mapRef2RefFlowFunction_101.map();
      if (isDirty_mapRef2RefFlowFunction_101) {
        binaryMapToRefFlowFunction_103.inputUpdated(mapRef2RefFlowFunction_101);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_103()) {
      auditInvocation(
          binaryMapToRefFlowFunction_103, "binaryMapToRefFlowFunction_103", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_103 = binaryMapToRefFlowFunction_103.map();
      if (isDirty_binaryMapToRefFlowFunction_103) {
        mapRef2RefFlowFunction_105.inputUpdated(binaryMapToRefFlowFunction_103);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_105()) {
      auditInvocation(mapRef2RefFlowFunction_105, "mapRef2RefFlowFunction_105", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_105 = mapRef2RefFlowFunction_105.map();
      if (isDirty_mapRef2RefFlowFunction_105) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_105);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_107.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_57()) {
      auditInvocation(pushFlowFunction_57, "pushFlowFunction_57", "push", typedEvent);
      isDirty_pushFlowFunction_57 = pushFlowFunction_57.push();
      if (isDirty_pushFlowFunction_57) {
        binaryMapToRefFlowFunction_108.inputUpdated(pushFlowFunction_57);
      }
    }
    if (guardCheck_pushFlowFunction_107()) {
      auditInvocation(pushFlowFunction_107, "pushFlowFunction_107", "push", typedEvent);
      isDirty_pushFlowFunction_107 = pushFlowFunction_107.push();
      if (isDirty_pushFlowFunction_107) {
        binaryMapToRefFlowFunction_108.input2Updated(pushFlowFunction_107);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_108()) {
      auditInvocation(
          binaryMapToRefFlowFunction_108, "binaryMapToRefFlowFunction_108", "map", typedEvent);
      binaryMapToRefFlowFunction_108.map();
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
    auditInvocation(namedFeedTableNode_109, "namedFeedTableNode_109", "tableUpdate", typedEvent);
    isDirty_namedFeedTableNode_109 = namedFeedTableNode_109.tableUpdate(typedEvent);
    if (guardCheck_mapRef2RefFlowFunction_4()) {
      auditInvocation(mapRef2RefFlowFunction_4, "mapRef2RefFlowFunction_4", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_4 = mapRef2RefFlowFunction_4.map();
      if (isDirty_mapRef2RefFlowFunction_4) {
        mergeFlowFunction_6.inputStreamUpdated(mapRef2RefFlowFunction_4);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_5()) {
      auditInvocation(mapRef2RefFlowFunction_5, "mapRef2RefFlowFunction_5", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_5 = mapRef2RefFlowFunction_5.map();
      if (isDirty_mapRef2RefFlowFunction_5) {
        mergeFlowFunction_6.inputStreamUpdated(mapRef2RefFlowFunction_5);
      }
    }
    if (guardCheck_mergeFlowFunction_6()) {
      auditInvocation(mergeFlowFunction_6, "mergeFlowFunction_6", "publishMerge", typedEvent);
      isDirty_mergeFlowFunction_6 = mergeFlowFunction_6.publishMerge();
      if (isDirty_mergeFlowFunction_6) {
        filterFlowFunction_8.inputUpdated(mergeFlowFunction_6);
      }
    }
    if (guardCheck_filterFlowFunction_8()) {
      auditInvocation(filterFlowFunction_8, "filterFlowFunction_8", "filter", typedEvent);
      isDirty_filterFlowFunction_8 = filterFlowFunction_8.filter();
      if (isDirty_filterFlowFunction_8) {
        mapRef2RefFlowFunction_10.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_12.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_14.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_16.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_40.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_59.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_61.inputUpdated(filterFlowFunction_8);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      auditInvocation(mapRef2RefFlowFunction_10, "mapRef2RefFlowFunction_10", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_21.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_21.input2Updated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_21()) {
      auditInvocation(
          binaryMapToRefFlowFunction_21, "binaryMapToRefFlowFunction_21", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_21 = binaryMapToRefFlowFunction_21.map();
      if (isDirty_binaryMapToRefFlowFunction_21) {
        mapRef2RefFlowFunction_23.inputUpdated(binaryMapToRefFlowFunction_21);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_82.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_82.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_82()) {
      auditInvocation(
          binaryMapToRefFlowFunction_82, "binaryMapToRefFlowFunction_82", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_82 = binaryMapToRefFlowFunction_82.map();
      if (isDirty_binaryMapToRefFlowFunction_82) {
        mapRef2RefFlowFunction_84.inputUpdated(binaryMapToRefFlowFunction_82);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_23()) {
      auditInvocation(mapRef2RefFlowFunction_23, "mapRef2RefFlowFunction_23", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_23 = mapRef2RefFlowFunction_23.map();
      if (isDirty_mapRef2RefFlowFunction_23) {
        mapRef2RefFlowFunction_25.inputUpdated(mapRef2RefFlowFunction_23);
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
        mapRef2RefFlowFunction_33.inputUpdated(mapRef2RefFlowFunction_31);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_33()) {
      auditInvocation(mapRef2RefFlowFunction_33, "mapRef2RefFlowFunction_33", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_33 = mapRef2RefFlowFunction_33.map();
      if (isDirty_mapRef2RefFlowFunction_33) {
        mapRef2RefFlowFunction_35.inputUpdated(mapRef2RefFlowFunction_33);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_35()) {
      auditInvocation(mapRef2RefFlowFunction_35, "mapRef2RefFlowFunction_35", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_35 = mapRef2RefFlowFunction_35.map();
      if (isDirty_mapRef2RefFlowFunction_35) {
        binaryMapToRefFlowFunction_52.inputUpdated(mapRef2RefFlowFunction_35);
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
        binaryMapToRefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_42);
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
        mapRef2RefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_48()) {
      auditInvocation(mapRef2RefFlowFunction_48, "mapRef2RefFlowFunction_48", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_48 = mapRef2RefFlowFunction_48.map();
      if (isDirty_mapRef2RefFlowFunction_48) {
        mapRef2RefFlowFunction_50.inputUpdated(mapRef2RefFlowFunction_48);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_50()) {
      auditInvocation(mapRef2RefFlowFunction_50, "mapRef2RefFlowFunction_50", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
      if (isDirty_mapRef2RefFlowFunction_50) {
        binaryMapToRefFlowFunction_52.input2Updated(mapRef2RefFlowFunction_50);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_52()) {
      auditInvocation(
          binaryMapToRefFlowFunction_52, "binaryMapToRefFlowFunction_52", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_52 = binaryMapToRefFlowFunction_52.map();
      if (isDirty_binaryMapToRefFlowFunction_52) {
        mapRef2RefFlowFunction_54.inputUpdated(binaryMapToRefFlowFunction_52);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      auditInvocation(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        mapRef2RefFlowFunction_55.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_55()) {
      auditInvocation(mapRef2RefFlowFunction_55, "mapRef2RefFlowFunction_55", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_55 = mapRef2RefFlowFunction_55.map();
      if (isDirty_mapRef2RefFlowFunction_55) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_55);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "map", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_57.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_59()) {
      auditInvocation(mapRef2RefFlowFunction_59, "mapRef2RefFlowFunction_59", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_59 = mapRef2RefFlowFunction_59.map();
      if (isDirty_mapRef2RefFlowFunction_59) {
        binaryMapToRefFlowFunction_63.inputUpdated(mapRef2RefFlowFunction_59);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_61()) {
      auditInvocation(mapRef2RefFlowFunction_61, "mapRef2RefFlowFunction_61", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_61 = mapRef2RefFlowFunction_61.map();
      if (isDirty_mapRef2RefFlowFunction_61) {
        binaryMapToRefFlowFunction_63.input2Updated(mapRef2RefFlowFunction_61);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_63()) {
      auditInvocation(
          binaryMapToRefFlowFunction_63, "binaryMapToRefFlowFunction_63", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_63 = binaryMapToRefFlowFunction_63.map();
      if (isDirty_binaryMapToRefFlowFunction_63) {
        mapRef2RefFlowFunction_65.inputUpdated(binaryMapToRefFlowFunction_63);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_65()) {
      auditInvocation(mapRef2RefFlowFunction_65, "mapRef2RefFlowFunction_65", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_65 = mapRef2RefFlowFunction_65.map();
      if (isDirty_mapRef2RefFlowFunction_65) {
        mapRef2RefFlowFunction_67.inputUpdated(mapRef2RefFlowFunction_65);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_67()) {
      auditInvocation(mapRef2RefFlowFunction_67, "mapRef2RefFlowFunction_67", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_67 = mapRef2RefFlowFunction_67.map();
      if (isDirty_mapRef2RefFlowFunction_67) {
        binaryMapToRefFlowFunction_74.inputUpdated(mapRef2RefFlowFunction_67);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_74()) {
      auditInvocation(
          binaryMapToRefFlowFunction_74, "binaryMapToRefFlowFunction_74", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_74 = binaryMapToRefFlowFunction_74.map();
      if (isDirty_binaryMapToRefFlowFunction_74) {
        mapRef2RefFlowFunction_76.inputUpdated(binaryMapToRefFlowFunction_74);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_76()) {
      auditInvocation(mapRef2RefFlowFunction_76, "mapRef2RefFlowFunction_76", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_76 = mapRef2RefFlowFunction_76.map();
      if (isDirty_mapRef2RefFlowFunction_76) {
        mapRef2RefFlowFunction_78.inputUpdated(mapRef2RefFlowFunction_76);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_78()) {
      auditInvocation(mapRef2RefFlowFunction_78, "mapRef2RefFlowFunction_78", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_78 = mapRef2RefFlowFunction_78.map();
      if (isDirty_mapRef2RefFlowFunction_78) {
        mapRef2RefFlowFunction_80.inputUpdated(mapRef2RefFlowFunction_78);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_80()) {
      auditInvocation(mapRef2RefFlowFunction_80, "mapRef2RefFlowFunction_80", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_80 = mapRef2RefFlowFunction_80.map();
      if (isDirty_mapRef2RefFlowFunction_80) {
        binaryMapToRefFlowFunction_103.input2Updated(mapRef2RefFlowFunction_80);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_84()) {
      auditInvocation(mapRef2RefFlowFunction_84, "mapRef2RefFlowFunction_84", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_84 = mapRef2RefFlowFunction_84.map();
      if (isDirty_mapRef2RefFlowFunction_84) {
        mapRef2RefFlowFunction_86.inputUpdated(mapRef2RefFlowFunction_84);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_86()) {
      auditInvocation(mapRef2RefFlowFunction_86, "mapRef2RefFlowFunction_86", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_86 = mapRef2RefFlowFunction_86.map();
      if (isDirty_mapRef2RefFlowFunction_86) {
        binaryMapToRefFlowFunction_93.inputUpdated(mapRef2RefFlowFunction_86);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_93()) {
      auditInvocation(
          binaryMapToRefFlowFunction_93, "binaryMapToRefFlowFunction_93", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_93 = binaryMapToRefFlowFunction_93.map();
      if (isDirty_binaryMapToRefFlowFunction_93) {
        mapRef2RefFlowFunction_95.inputUpdated(binaryMapToRefFlowFunction_93);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_95()) {
      auditInvocation(mapRef2RefFlowFunction_95, "mapRef2RefFlowFunction_95", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_95 = mapRef2RefFlowFunction_95.map();
      if (isDirty_mapRef2RefFlowFunction_95) {
        mapRef2RefFlowFunction_97.inputUpdated(mapRef2RefFlowFunction_95);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_97()) {
      auditInvocation(mapRef2RefFlowFunction_97, "mapRef2RefFlowFunction_97", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_97 = mapRef2RefFlowFunction_97.map();
      if (isDirty_mapRef2RefFlowFunction_97) {
        mapRef2RefFlowFunction_99.inputUpdated(mapRef2RefFlowFunction_97);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_99()) {
      auditInvocation(mapRef2RefFlowFunction_99, "mapRef2RefFlowFunction_99", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_99 = mapRef2RefFlowFunction_99.map();
      if (isDirty_mapRef2RefFlowFunction_99) {
        mapRef2RefFlowFunction_101.inputUpdated(mapRef2RefFlowFunction_99);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_101()) {
      auditInvocation(mapRef2RefFlowFunction_101, "mapRef2RefFlowFunction_101", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_101 = mapRef2RefFlowFunction_101.map();
      if (isDirty_mapRef2RefFlowFunction_101) {
        binaryMapToRefFlowFunction_103.inputUpdated(mapRef2RefFlowFunction_101);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_103()) {
      auditInvocation(
          binaryMapToRefFlowFunction_103, "binaryMapToRefFlowFunction_103", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_103 = binaryMapToRefFlowFunction_103.map();
      if (isDirty_binaryMapToRefFlowFunction_103) {
        mapRef2RefFlowFunction_105.inputUpdated(binaryMapToRefFlowFunction_103);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_105()) {
      auditInvocation(mapRef2RefFlowFunction_105, "mapRef2RefFlowFunction_105", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_105 = mapRef2RefFlowFunction_105.map();
      if (isDirty_mapRef2RefFlowFunction_105) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_105);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_107.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_57()) {
      auditInvocation(pushFlowFunction_57, "pushFlowFunction_57", "push", typedEvent);
      isDirty_pushFlowFunction_57 = pushFlowFunction_57.push();
      if (isDirty_pushFlowFunction_57) {
        binaryMapToRefFlowFunction_108.inputUpdated(pushFlowFunction_57);
      }
    }
    if (guardCheck_pushFlowFunction_107()) {
      auditInvocation(pushFlowFunction_107, "pushFlowFunction_107", "push", typedEvent);
      isDirty_pushFlowFunction_107 = pushFlowFunction_107.push();
      if (isDirty_pushFlowFunction_107) {
        binaryMapToRefFlowFunction_108.input2Updated(pushFlowFunction_107);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_108()) {
      auditInvocation(
          binaryMapToRefFlowFunction_108, "binaryMapToRefFlowFunction_108", "map", typedEvent);
      binaryMapToRefFlowFunction_108.map();
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
    if (guardCheck_mapRef2RefFlowFunction_4()) {
      auditInvocation(mapRef2RefFlowFunction_4, "mapRef2RefFlowFunction_4", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_4 = mapRef2RefFlowFunction_4.map();
      if (isDirty_mapRef2RefFlowFunction_4) {
        mergeFlowFunction_6.inputStreamUpdated(mapRef2RefFlowFunction_4);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_5()) {
      auditInvocation(mapRef2RefFlowFunction_5, "mapRef2RefFlowFunction_5", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_5 = mapRef2RefFlowFunction_5.map();
      if (isDirty_mapRef2RefFlowFunction_5) {
        mergeFlowFunction_6.inputStreamUpdated(mapRef2RefFlowFunction_5);
      }
    }
    if (guardCheck_mergeFlowFunction_6()) {
      auditInvocation(mergeFlowFunction_6, "mergeFlowFunction_6", "publishMerge", typedEvent);
      isDirty_mergeFlowFunction_6 = mergeFlowFunction_6.publishMerge();
      if (isDirty_mergeFlowFunction_6) {
        filterFlowFunction_8.inputUpdated(mergeFlowFunction_6);
      }
    }
    if (guardCheck_filterFlowFunction_8()) {
      auditInvocation(filterFlowFunction_8, "filterFlowFunction_8", "filter", typedEvent);
      isDirty_filterFlowFunction_8 = filterFlowFunction_8.filter();
      if (isDirty_filterFlowFunction_8) {
        mapRef2RefFlowFunction_10.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_12.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_14.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_16.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_40.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_59.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_61.inputUpdated(filterFlowFunction_8);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      auditInvocation(mapRef2RefFlowFunction_10, "mapRef2RefFlowFunction_10", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_21.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_21.input2Updated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_21()) {
      auditInvocation(
          binaryMapToRefFlowFunction_21, "binaryMapToRefFlowFunction_21", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_21 = binaryMapToRefFlowFunction_21.map();
      if (isDirty_binaryMapToRefFlowFunction_21) {
        mapRef2RefFlowFunction_23.inputUpdated(binaryMapToRefFlowFunction_21);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_82.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_82.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_82()) {
      auditInvocation(
          binaryMapToRefFlowFunction_82, "binaryMapToRefFlowFunction_82", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_82 = binaryMapToRefFlowFunction_82.map();
      if (isDirty_binaryMapToRefFlowFunction_82) {
        mapRef2RefFlowFunction_84.inputUpdated(binaryMapToRefFlowFunction_82);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_23()) {
      auditInvocation(mapRef2RefFlowFunction_23, "mapRef2RefFlowFunction_23", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_23 = mapRef2RefFlowFunction_23.map();
      if (isDirty_mapRef2RefFlowFunction_23) {
        mapRef2RefFlowFunction_25.inputUpdated(mapRef2RefFlowFunction_23);
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
        mapRef2RefFlowFunction_33.inputUpdated(mapRef2RefFlowFunction_31);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_33()) {
      auditInvocation(mapRef2RefFlowFunction_33, "mapRef2RefFlowFunction_33", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_33 = mapRef2RefFlowFunction_33.map();
      if (isDirty_mapRef2RefFlowFunction_33) {
        mapRef2RefFlowFunction_35.inputUpdated(mapRef2RefFlowFunction_33);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_35()) {
      auditInvocation(mapRef2RefFlowFunction_35, "mapRef2RefFlowFunction_35", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_35 = mapRef2RefFlowFunction_35.map();
      if (isDirty_mapRef2RefFlowFunction_35) {
        binaryMapToRefFlowFunction_52.inputUpdated(mapRef2RefFlowFunction_35);
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
        binaryMapToRefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_42);
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
        mapRef2RefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_48()) {
      auditInvocation(mapRef2RefFlowFunction_48, "mapRef2RefFlowFunction_48", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_48 = mapRef2RefFlowFunction_48.map();
      if (isDirty_mapRef2RefFlowFunction_48) {
        mapRef2RefFlowFunction_50.inputUpdated(mapRef2RefFlowFunction_48);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_50()) {
      auditInvocation(mapRef2RefFlowFunction_50, "mapRef2RefFlowFunction_50", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
      if (isDirty_mapRef2RefFlowFunction_50) {
        binaryMapToRefFlowFunction_52.input2Updated(mapRef2RefFlowFunction_50);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_52()) {
      auditInvocation(
          binaryMapToRefFlowFunction_52, "binaryMapToRefFlowFunction_52", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_52 = binaryMapToRefFlowFunction_52.map();
      if (isDirty_binaryMapToRefFlowFunction_52) {
        mapRef2RefFlowFunction_54.inputUpdated(binaryMapToRefFlowFunction_52);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      auditInvocation(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        mapRef2RefFlowFunction_55.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_55()) {
      auditInvocation(mapRef2RefFlowFunction_55, "mapRef2RefFlowFunction_55", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_55 = mapRef2RefFlowFunction_55.map();
      if (isDirty_mapRef2RefFlowFunction_55) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_55);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "map", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_57.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_59()) {
      auditInvocation(mapRef2RefFlowFunction_59, "mapRef2RefFlowFunction_59", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_59 = mapRef2RefFlowFunction_59.map();
      if (isDirty_mapRef2RefFlowFunction_59) {
        binaryMapToRefFlowFunction_63.inputUpdated(mapRef2RefFlowFunction_59);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_61()) {
      auditInvocation(mapRef2RefFlowFunction_61, "mapRef2RefFlowFunction_61", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_61 = mapRef2RefFlowFunction_61.map();
      if (isDirty_mapRef2RefFlowFunction_61) {
        binaryMapToRefFlowFunction_63.input2Updated(mapRef2RefFlowFunction_61);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_63()) {
      auditInvocation(
          binaryMapToRefFlowFunction_63, "binaryMapToRefFlowFunction_63", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_63 = binaryMapToRefFlowFunction_63.map();
      if (isDirty_binaryMapToRefFlowFunction_63) {
        mapRef2RefFlowFunction_65.inputUpdated(binaryMapToRefFlowFunction_63);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_65()) {
      auditInvocation(mapRef2RefFlowFunction_65, "mapRef2RefFlowFunction_65", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_65 = mapRef2RefFlowFunction_65.map();
      if (isDirty_mapRef2RefFlowFunction_65) {
        mapRef2RefFlowFunction_67.inputUpdated(mapRef2RefFlowFunction_65);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_67()) {
      auditInvocation(mapRef2RefFlowFunction_67, "mapRef2RefFlowFunction_67", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_67 = mapRef2RefFlowFunction_67.map();
      if (isDirty_mapRef2RefFlowFunction_67) {
        binaryMapToRefFlowFunction_74.inputUpdated(mapRef2RefFlowFunction_67);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_74()) {
      auditInvocation(
          binaryMapToRefFlowFunction_74, "binaryMapToRefFlowFunction_74", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_74 = binaryMapToRefFlowFunction_74.map();
      if (isDirty_binaryMapToRefFlowFunction_74) {
        mapRef2RefFlowFunction_76.inputUpdated(binaryMapToRefFlowFunction_74);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_76()) {
      auditInvocation(mapRef2RefFlowFunction_76, "mapRef2RefFlowFunction_76", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_76 = mapRef2RefFlowFunction_76.map();
      if (isDirty_mapRef2RefFlowFunction_76) {
        mapRef2RefFlowFunction_78.inputUpdated(mapRef2RefFlowFunction_76);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_78()) {
      auditInvocation(mapRef2RefFlowFunction_78, "mapRef2RefFlowFunction_78", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_78 = mapRef2RefFlowFunction_78.map();
      if (isDirty_mapRef2RefFlowFunction_78) {
        mapRef2RefFlowFunction_80.inputUpdated(mapRef2RefFlowFunction_78);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_80()) {
      auditInvocation(mapRef2RefFlowFunction_80, "mapRef2RefFlowFunction_80", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_80 = mapRef2RefFlowFunction_80.map();
      if (isDirty_mapRef2RefFlowFunction_80) {
        binaryMapToRefFlowFunction_103.input2Updated(mapRef2RefFlowFunction_80);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_84()) {
      auditInvocation(mapRef2RefFlowFunction_84, "mapRef2RefFlowFunction_84", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_84 = mapRef2RefFlowFunction_84.map();
      if (isDirty_mapRef2RefFlowFunction_84) {
        mapRef2RefFlowFunction_86.inputUpdated(mapRef2RefFlowFunction_84);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_86()) {
      auditInvocation(mapRef2RefFlowFunction_86, "mapRef2RefFlowFunction_86", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_86 = mapRef2RefFlowFunction_86.map();
      if (isDirty_mapRef2RefFlowFunction_86) {
        binaryMapToRefFlowFunction_93.inputUpdated(mapRef2RefFlowFunction_86);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_93()) {
      auditInvocation(
          binaryMapToRefFlowFunction_93, "binaryMapToRefFlowFunction_93", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_93 = binaryMapToRefFlowFunction_93.map();
      if (isDirty_binaryMapToRefFlowFunction_93) {
        mapRef2RefFlowFunction_95.inputUpdated(binaryMapToRefFlowFunction_93);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_95()) {
      auditInvocation(mapRef2RefFlowFunction_95, "mapRef2RefFlowFunction_95", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_95 = mapRef2RefFlowFunction_95.map();
      if (isDirty_mapRef2RefFlowFunction_95) {
        mapRef2RefFlowFunction_97.inputUpdated(mapRef2RefFlowFunction_95);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_97()) {
      auditInvocation(mapRef2RefFlowFunction_97, "mapRef2RefFlowFunction_97", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_97 = mapRef2RefFlowFunction_97.map();
      if (isDirty_mapRef2RefFlowFunction_97) {
        mapRef2RefFlowFunction_99.inputUpdated(mapRef2RefFlowFunction_97);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_99()) {
      auditInvocation(mapRef2RefFlowFunction_99, "mapRef2RefFlowFunction_99", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_99 = mapRef2RefFlowFunction_99.map();
      if (isDirty_mapRef2RefFlowFunction_99) {
        mapRef2RefFlowFunction_101.inputUpdated(mapRef2RefFlowFunction_99);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_101()) {
      auditInvocation(mapRef2RefFlowFunction_101, "mapRef2RefFlowFunction_101", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_101 = mapRef2RefFlowFunction_101.map();
      if (isDirty_mapRef2RefFlowFunction_101) {
        binaryMapToRefFlowFunction_103.inputUpdated(mapRef2RefFlowFunction_101);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_103()) {
      auditInvocation(
          binaryMapToRefFlowFunction_103, "binaryMapToRefFlowFunction_103", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_103 = binaryMapToRefFlowFunction_103.map();
      if (isDirty_binaryMapToRefFlowFunction_103) {
        mapRef2RefFlowFunction_105.inputUpdated(binaryMapToRefFlowFunction_103);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_105()) {
      auditInvocation(mapRef2RefFlowFunction_105, "mapRef2RefFlowFunction_105", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_105 = mapRef2RefFlowFunction_105.map();
      if (isDirty_mapRef2RefFlowFunction_105) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_105);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_107.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_57()) {
      auditInvocation(pushFlowFunction_57, "pushFlowFunction_57", "push", typedEvent);
      isDirty_pushFlowFunction_57 = pushFlowFunction_57.push();
      if (isDirty_pushFlowFunction_57) {
        binaryMapToRefFlowFunction_108.inputUpdated(pushFlowFunction_57);
      }
    }
    if (guardCheck_pushFlowFunction_107()) {
      auditInvocation(pushFlowFunction_107, "pushFlowFunction_107", "push", typedEvent);
      isDirty_pushFlowFunction_107 = pushFlowFunction_107.push();
      if (isDirty_pushFlowFunction_107) {
        binaryMapToRefFlowFunction_108.input2Updated(pushFlowFunction_107);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_108()) {
      auditInvocation(
          binaryMapToRefFlowFunction_108, "binaryMapToRefFlowFunction_108", "map", typedEvent);
      binaryMapToRefFlowFunction_108.map();
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
    if (guardCheck_mapRef2RefFlowFunction_4()) {
      auditInvocation(mapRef2RefFlowFunction_4, "mapRef2RefFlowFunction_4", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_4 = mapRef2RefFlowFunction_4.map();
      if (isDirty_mapRef2RefFlowFunction_4) {
        mergeFlowFunction_6.inputStreamUpdated(mapRef2RefFlowFunction_4);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_5()) {
      auditInvocation(mapRef2RefFlowFunction_5, "mapRef2RefFlowFunction_5", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_5 = mapRef2RefFlowFunction_5.map();
      if (isDirty_mapRef2RefFlowFunction_5) {
        mergeFlowFunction_6.inputStreamUpdated(mapRef2RefFlowFunction_5);
      }
    }
    if (guardCheck_mergeFlowFunction_6()) {
      auditInvocation(mergeFlowFunction_6, "mergeFlowFunction_6", "publishMerge", typedEvent);
      isDirty_mergeFlowFunction_6 = mergeFlowFunction_6.publishMerge();
      if (isDirty_mergeFlowFunction_6) {
        filterFlowFunction_8.inputUpdated(mergeFlowFunction_6);
      }
    }
    if (guardCheck_filterFlowFunction_8()) {
      auditInvocation(filterFlowFunction_8, "filterFlowFunction_8", "filter", typedEvent);
      isDirty_filterFlowFunction_8 = filterFlowFunction_8.filter();
      if (isDirty_filterFlowFunction_8) {
        mapRef2RefFlowFunction_10.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_12.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_14.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_16.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_40.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_59.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_61.inputUpdated(filterFlowFunction_8);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      auditInvocation(mapRef2RefFlowFunction_10, "mapRef2RefFlowFunction_10", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_21.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_21.input2Updated(mapRef2RefFlowFunction_12);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_21()) {
      auditInvocation(
          binaryMapToRefFlowFunction_21, "binaryMapToRefFlowFunction_21", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_21 = binaryMapToRefFlowFunction_21.map();
      if (isDirty_binaryMapToRefFlowFunction_21) {
        mapRef2RefFlowFunction_23.inputUpdated(binaryMapToRefFlowFunction_21);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_82.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_82.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_82()) {
      auditInvocation(
          binaryMapToRefFlowFunction_82, "binaryMapToRefFlowFunction_82", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_82 = binaryMapToRefFlowFunction_82.map();
      if (isDirty_binaryMapToRefFlowFunction_82) {
        mapRef2RefFlowFunction_84.inputUpdated(binaryMapToRefFlowFunction_82);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_23()) {
      auditInvocation(mapRef2RefFlowFunction_23, "mapRef2RefFlowFunction_23", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_23 = mapRef2RefFlowFunction_23.map();
      if (isDirty_mapRef2RefFlowFunction_23) {
        mapRef2RefFlowFunction_25.inputUpdated(mapRef2RefFlowFunction_23);
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
        mapRef2RefFlowFunction_33.inputUpdated(mapRef2RefFlowFunction_31);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_33()) {
      auditInvocation(mapRef2RefFlowFunction_33, "mapRef2RefFlowFunction_33", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_33 = mapRef2RefFlowFunction_33.map();
      if (isDirty_mapRef2RefFlowFunction_33) {
        mapRef2RefFlowFunction_35.inputUpdated(mapRef2RefFlowFunction_33);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_35()) {
      auditInvocation(mapRef2RefFlowFunction_35, "mapRef2RefFlowFunction_35", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_35 = mapRef2RefFlowFunction_35.map();
      if (isDirty_mapRef2RefFlowFunction_35) {
        binaryMapToRefFlowFunction_52.inputUpdated(mapRef2RefFlowFunction_35);
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
        binaryMapToRefFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_42);
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
        mapRef2RefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_48()) {
      auditInvocation(mapRef2RefFlowFunction_48, "mapRef2RefFlowFunction_48", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_48 = mapRef2RefFlowFunction_48.map();
      if (isDirty_mapRef2RefFlowFunction_48) {
        mapRef2RefFlowFunction_50.inputUpdated(mapRef2RefFlowFunction_48);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_50()) {
      auditInvocation(mapRef2RefFlowFunction_50, "mapRef2RefFlowFunction_50", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
      if (isDirty_mapRef2RefFlowFunction_50) {
        binaryMapToRefFlowFunction_52.input2Updated(mapRef2RefFlowFunction_50);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_52()) {
      auditInvocation(
          binaryMapToRefFlowFunction_52, "binaryMapToRefFlowFunction_52", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_52 = binaryMapToRefFlowFunction_52.map();
      if (isDirty_binaryMapToRefFlowFunction_52) {
        mapRef2RefFlowFunction_54.inputUpdated(binaryMapToRefFlowFunction_52);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      auditInvocation(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        mapRef2RefFlowFunction_55.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_55()) {
      auditInvocation(mapRef2RefFlowFunction_55, "mapRef2RefFlowFunction_55", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_55 = mapRef2RefFlowFunction_55.map();
      if (isDirty_mapRef2RefFlowFunction_55) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_55);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "map", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_57.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_59()) {
      auditInvocation(mapRef2RefFlowFunction_59, "mapRef2RefFlowFunction_59", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_59 = mapRef2RefFlowFunction_59.map();
      if (isDirty_mapRef2RefFlowFunction_59) {
        binaryMapToRefFlowFunction_63.inputUpdated(mapRef2RefFlowFunction_59);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_61()) {
      auditInvocation(mapRef2RefFlowFunction_61, "mapRef2RefFlowFunction_61", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_61 = mapRef2RefFlowFunction_61.map();
      if (isDirty_mapRef2RefFlowFunction_61) {
        binaryMapToRefFlowFunction_63.input2Updated(mapRef2RefFlowFunction_61);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_63()) {
      auditInvocation(
          binaryMapToRefFlowFunction_63, "binaryMapToRefFlowFunction_63", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_63 = binaryMapToRefFlowFunction_63.map();
      if (isDirty_binaryMapToRefFlowFunction_63) {
        mapRef2RefFlowFunction_65.inputUpdated(binaryMapToRefFlowFunction_63);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_65()) {
      auditInvocation(mapRef2RefFlowFunction_65, "mapRef2RefFlowFunction_65", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_65 = mapRef2RefFlowFunction_65.map();
      if (isDirty_mapRef2RefFlowFunction_65) {
        mapRef2RefFlowFunction_67.inputUpdated(mapRef2RefFlowFunction_65);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_67()) {
      auditInvocation(mapRef2RefFlowFunction_67, "mapRef2RefFlowFunction_67", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_67 = mapRef2RefFlowFunction_67.map();
      if (isDirty_mapRef2RefFlowFunction_67) {
        binaryMapToRefFlowFunction_74.inputUpdated(mapRef2RefFlowFunction_67);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_74()) {
      auditInvocation(
          binaryMapToRefFlowFunction_74, "binaryMapToRefFlowFunction_74", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_74 = binaryMapToRefFlowFunction_74.map();
      if (isDirty_binaryMapToRefFlowFunction_74) {
        mapRef2RefFlowFunction_76.inputUpdated(binaryMapToRefFlowFunction_74);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_76()) {
      auditInvocation(mapRef2RefFlowFunction_76, "mapRef2RefFlowFunction_76", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_76 = mapRef2RefFlowFunction_76.map();
      if (isDirty_mapRef2RefFlowFunction_76) {
        mapRef2RefFlowFunction_78.inputUpdated(mapRef2RefFlowFunction_76);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_78()) {
      auditInvocation(mapRef2RefFlowFunction_78, "mapRef2RefFlowFunction_78", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_78 = mapRef2RefFlowFunction_78.map();
      if (isDirty_mapRef2RefFlowFunction_78) {
        mapRef2RefFlowFunction_80.inputUpdated(mapRef2RefFlowFunction_78);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_80()) {
      auditInvocation(mapRef2RefFlowFunction_80, "mapRef2RefFlowFunction_80", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_80 = mapRef2RefFlowFunction_80.map();
      if (isDirty_mapRef2RefFlowFunction_80) {
        binaryMapToRefFlowFunction_103.input2Updated(mapRef2RefFlowFunction_80);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_84()) {
      auditInvocation(mapRef2RefFlowFunction_84, "mapRef2RefFlowFunction_84", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_84 = mapRef2RefFlowFunction_84.map();
      if (isDirty_mapRef2RefFlowFunction_84) {
        mapRef2RefFlowFunction_86.inputUpdated(mapRef2RefFlowFunction_84);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_86()) {
      auditInvocation(mapRef2RefFlowFunction_86, "mapRef2RefFlowFunction_86", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_86 = mapRef2RefFlowFunction_86.map();
      if (isDirty_mapRef2RefFlowFunction_86) {
        binaryMapToRefFlowFunction_93.inputUpdated(mapRef2RefFlowFunction_86);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_93()) {
      auditInvocation(
          binaryMapToRefFlowFunction_93, "binaryMapToRefFlowFunction_93", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_93 = binaryMapToRefFlowFunction_93.map();
      if (isDirty_binaryMapToRefFlowFunction_93) {
        mapRef2RefFlowFunction_95.inputUpdated(binaryMapToRefFlowFunction_93);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_95()) {
      auditInvocation(mapRef2RefFlowFunction_95, "mapRef2RefFlowFunction_95", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_95 = mapRef2RefFlowFunction_95.map();
      if (isDirty_mapRef2RefFlowFunction_95) {
        mapRef2RefFlowFunction_97.inputUpdated(mapRef2RefFlowFunction_95);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_97()) {
      auditInvocation(mapRef2RefFlowFunction_97, "mapRef2RefFlowFunction_97", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_97 = mapRef2RefFlowFunction_97.map();
      if (isDirty_mapRef2RefFlowFunction_97) {
        mapRef2RefFlowFunction_99.inputUpdated(mapRef2RefFlowFunction_97);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_99()) {
      auditInvocation(mapRef2RefFlowFunction_99, "mapRef2RefFlowFunction_99", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_99 = mapRef2RefFlowFunction_99.map();
      if (isDirty_mapRef2RefFlowFunction_99) {
        mapRef2RefFlowFunction_101.inputUpdated(mapRef2RefFlowFunction_99);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_101()) {
      auditInvocation(mapRef2RefFlowFunction_101, "mapRef2RefFlowFunction_101", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_101 = mapRef2RefFlowFunction_101.map();
      if (isDirty_mapRef2RefFlowFunction_101) {
        binaryMapToRefFlowFunction_103.inputUpdated(mapRef2RefFlowFunction_101);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_103()) {
      auditInvocation(
          binaryMapToRefFlowFunction_103, "binaryMapToRefFlowFunction_103", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_103 = binaryMapToRefFlowFunction_103.map();
      if (isDirty_binaryMapToRefFlowFunction_103) {
        mapRef2RefFlowFunction_105.inputUpdated(binaryMapToRefFlowFunction_103);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_105()) {
      auditInvocation(mapRef2RefFlowFunction_105, "mapRef2RefFlowFunction_105", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_105 = mapRef2RefFlowFunction_105.map();
      if (isDirty_mapRef2RefFlowFunction_105) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_105);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_107.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_57()) {
      auditInvocation(pushFlowFunction_57, "pushFlowFunction_57", "push", typedEvent);
      isDirty_pushFlowFunction_57 = pushFlowFunction_57.push();
      if (isDirty_pushFlowFunction_57) {
        binaryMapToRefFlowFunction_108.inputUpdated(pushFlowFunction_57);
      }
    }
    if (guardCheck_pushFlowFunction_107()) {
      auditInvocation(pushFlowFunction_107, "pushFlowFunction_107", "push", typedEvent);
      isDirty_pushFlowFunction_107 = pushFlowFunction_107.push();
      if (isDirty_pushFlowFunction_107) {
        binaryMapToRefFlowFunction_108.input2Updated(pushFlowFunction_107);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_108()) {
      auditInvocation(
          binaryMapToRefFlowFunction_108, "binaryMapToRefFlowFunction_108", "map", typedEvent);
      binaryMapToRefFlowFunction_108.map();
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
    auditor.nodeRegistered(callBackNode_110, "callBackNode_110");
    auditor.nodeRegistered(callBackNode_134, "callBackNode_134");
    auditor.nodeRegistered(callBackNode_260, "callBackNode_260");
    auditor.nodeRegistered(callbackDispatcher, "callbackDispatcher");
    auditor.nodeRegistered(callBackTriggerEvent_1, "callBackTriggerEvent_1");
    auditor.nodeRegistered(callBackTriggerEvent_2, "callBackTriggerEvent_2");
    auditor.nodeRegistered(callBackTriggerEvent_0, "callBackTriggerEvent_0");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_21, "binaryMapToRefFlowFunction_21");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_27, "binaryMapToRefFlowFunction_27");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_44, "binaryMapToRefFlowFunction_44");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_52, "binaryMapToRefFlowFunction_52");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_63, "binaryMapToRefFlowFunction_63");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_74, "binaryMapToRefFlowFunction_74");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_82, "binaryMapToRefFlowFunction_82");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_93, "binaryMapToRefFlowFunction_93");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_103, "binaryMapToRefFlowFunction_103");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_108, "binaryMapToRefFlowFunction_108");
    auditor.nodeRegistered(filterFlowFunction_8, "filterFlowFunction_8");
    auditor.nodeRegistered(flatMapFlowFunction_3, "flatMapFlowFunction_3");
    auditor.nodeRegistered(flatMapFlowFunction_17, "flatMapFlowFunction_17");
    auditor.nodeRegistered(flatMapFlowFunction_36, "flatMapFlowFunction_36");
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
    auditor.nodeRegistered(mapRef2RefFlowFunction_25, "mapRef2RefFlowFunction_25");
    auditor.nodeRegistered(mapRef2RefFlowFunction_29, "mapRef2RefFlowFunction_29");
    auditor.nodeRegistered(mapRef2RefFlowFunction_31, "mapRef2RefFlowFunction_31");
    auditor.nodeRegistered(mapRef2RefFlowFunction_33, "mapRef2RefFlowFunction_33");
    auditor.nodeRegistered(mapRef2RefFlowFunction_35, "mapRef2RefFlowFunction_35");
    auditor.nodeRegistered(mapRef2RefFlowFunction_38, "mapRef2RefFlowFunction_38");
    auditor.nodeRegistered(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40");
    auditor.nodeRegistered(mapRef2RefFlowFunction_42, "mapRef2RefFlowFunction_42");
    auditor.nodeRegistered(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46");
    auditor.nodeRegistered(mapRef2RefFlowFunction_48, "mapRef2RefFlowFunction_48");
    auditor.nodeRegistered(mapRef2RefFlowFunction_50, "mapRef2RefFlowFunction_50");
    auditor.nodeRegistered(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54");
    auditor.nodeRegistered(mapRef2RefFlowFunction_55, "mapRef2RefFlowFunction_55");
    auditor.nodeRegistered(mapRef2RefFlowFunction_59, "mapRef2RefFlowFunction_59");
    auditor.nodeRegistered(mapRef2RefFlowFunction_61, "mapRef2RefFlowFunction_61");
    auditor.nodeRegistered(mapRef2RefFlowFunction_65, "mapRef2RefFlowFunction_65");
    auditor.nodeRegistered(mapRef2RefFlowFunction_67, "mapRef2RefFlowFunction_67");
    auditor.nodeRegistered(mapRef2RefFlowFunction_68, "mapRef2RefFlowFunction_68");
    auditor.nodeRegistered(mapRef2RefFlowFunction_70, "mapRef2RefFlowFunction_70");
    auditor.nodeRegistered(mapRef2RefFlowFunction_72, "mapRef2RefFlowFunction_72");
    auditor.nodeRegistered(mapRef2RefFlowFunction_76, "mapRef2RefFlowFunction_76");
    auditor.nodeRegistered(mapRef2RefFlowFunction_78, "mapRef2RefFlowFunction_78");
    auditor.nodeRegistered(mapRef2RefFlowFunction_80, "mapRef2RefFlowFunction_80");
    auditor.nodeRegistered(mapRef2RefFlowFunction_84, "mapRef2RefFlowFunction_84");
    auditor.nodeRegistered(mapRef2RefFlowFunction_86, "mapRef2RefFlowFunction_86");
    auditor.nodeRegistered(mapRef2RefFlowFunction_87, "mapRef2RefFlowFunction_87");
    auditor.nodeRegistered(mapRef2RefFlowFunction_89, "mapRef2RefFlowFunction_89");
    auditor.nodeRegistered(mapRef2RefFlowFunction_91, "mapRef2RefFlowFunction_91");
    auditor.nodeRegistered(mapRef2RefFlowFunction_95, "mapRef2RefFlowFunction_95");
    auditor.nodeRegistered(mapRef2RefFlowFunction_97, "mapRef2RefFlowFunction_97");
    auditor.nodeRegistered(mapRef2RefFlowFunction_99, "mapRef2RefFlowFunction_99");
    auditor.nodeRegistered(mapRef2RefFlowFunction_101, "mapRef2RefFlowFunction_101");
    auditor.nodeRegistered(mapRef2RefFlowFunction_105, "mapRef2RefFlowFunction_105");
    auditor.nodeRegistered(mergeFlowFunction_6, "mergeFlowFunction_6");
    auditor.nodeRegistered(pushFlowFunction_57, "pushFlowFunction_57");
    auditor.nodeRegistered(pushFlowFunction_107, "pushFlowFunction_107");
    auditor.nodeRegistered(emptyGroupBy_157, "emptyGroupBy_157");
    auditor.nodeRegistered(emptyGroupBy_201, "emptyGroupBy_201");
    auditor.nodeRegistered(emptyGroupBy_239, "emptyGroupBy_239");
    auditor.nodeRegistered(emptyGroupBy_266, "emptyGroupBy_266");
    auditor.nodeRegistered(emptyGroupBy_289, "emptyGroupBy_289");
    auditor.nodeRegistered(emptyGroupBy_516, "emptyGroupBy_516");
    auditor.nodeRegistered(emptyGroupBy_530, "emptyGroupBy_530");
    auditor.nodeRegistered(emptyGroupBy_566, "emptyGroupBy_566");
    auditor.nodeRegistered(emptyGroupBy_627, "emptyGroupBy_627");
    auditor.nodeRegistered(emptyGroupBy_641, "emptyGroupBy_641");
    auditor.nodeRegistered(emptyGroupBy_697, "emptyGroupBy_697");
    auditor.nodeRegistered(emptyGroupBy_718, "emptyGroupBy_718");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_9, "groupByFlowFunctionWrapper_9");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_11, "groupByFlowFunctionWrapper_11");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_13, "groupByFlowFunctionWrapper_13");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_15, "groupByFlowFunctionWrapper_15");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_18, "groupByFlowFunctionWrapper_18");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_37, "groupByFlowFunctionWrapper_37");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_39, "groupByFlowFunctionWrapper_39");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_58, "groupByFlowFunctionWrapper_58");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_60, "groupByFlowFunctionWrapper_60");
    auditor.nodeRegistered(groupByHashMap_69, "groupByHashMap_69");
    auditor.nodeRegistered(groupByHashMap_88, "groupByHashMap_88");
    auditor.nodeRegistered(groupByMapFlowFunction_22, "groupByMapFlowFunction_22");
    auditor.nodeRegistered(groupByMapFlowFunction_28, "groupByMapFlowFunction_28");
    auditor.nodeRegistered(groupByMapFlowFunction_32, "groupByMapFlowFunction_32");
    auditor.nodeRegistered(groupByMapFlowFunction_45, "groupByMapFlowFunction_45");
    auditor.nodeRegistered(groupByMapFlowFunction_49, "groupByMapFlowFunction_49");
    auditor.nodeRegistered(groupByMapFlowFunction_53, "groupByMapFlowFunction_53");
    auditor.nodeRegistered(groupByMapFlowFunction_64, "groupByMapFlowFunction_64");
    auditor.nodeRegistered(groupByMapFlowFunction_75, "groupByMapFlowFunction_75");
    auditor.nodeRegistered(groupByMapFlowFunction_79, "groupByMapFlowFunction_79");
    auditor.nodeRegistered(groupByMapFlowFunction_83, "groupByMapFlowFunction_83");
    auditor.nodeRegistered(groupByMapFlowFunction_94, "groupByMapFlowFunction_94");
    auditor.nodeRegistered(groupByMapFlowFunction_96, "groupByMapFlowFunction_96");
    auditor.nodeRegistered(groupByMapFlowFunction_104, "groupByMapFlowFunction_104");
    auditor.nodeRegistered(leftJoin_51, "leftJoin_51");
    auditor.nodeRegistered(leftJoin_102, "leftJoin_102");
    auditor.nodeRegistered(outerJoin_20, "outerJoin_20");
    auditor.nodeRegistered(outerJoin_26, "outerJoin_26");
    auditor.nodeRegistered(outerJoin_43, "outerJoin_43");
    auditor.nodeRegistered(outerJoin_62, "outerJoin_62");
    auditor.nodeRegistered(outerJoin_73, "outerJoin_73");
    auditor.nodeRegistered(outerJoin_81, "outerJoin_81");
    auditor.nodeRegistered(outerJoin_92, "outerJoin_92");
    auditor.nodeRegistered(defaultValue_24, "defaultValue_24");
    auditor.nodeRegistered(defaultValue_30, "defaultValue_30");
    auditor.nodeRegistered(defaultValue_34, "defaultValue_34");
    auditor.nodeRegistered(defaultValue_41, "defaultValue_41");
    auditor.nodeRegistered(defaultValue_47, "defaultValue_47");
    auditor.nodeRegistered(defaultValue_66, "defaultValue_66");
    auditor.nodeRegistered(defaultValue_71, "defaultValue_71");
    auditor.nodeRegistered(defaultValue_77, "defaultValue_77");
    auditor.nodeRegistered(defaultValue_85, "defaultValue_85");
    auditor.nodeRegistered(defaultValue_90, "defaultValue_90");
    auditor.nodeRegistered(defaultValue_98, "defaultValue_98");
    auditor.nodeRegistered(defaultValue_100, "defaultValue_100");
    auditor.nodeRegistered(mapTuple_917, "mapTuple_917");
    auditor.nodeRegistered(mapTuple_923, "mapTuple_923");
    auditor.nodeRegistered(mapTuple_929, "mapTuple_929");
    auditor.nodeRegistered(mapTuple_944, "mapTuple_944");
    auditor.nodeRegistered(mapTuple_950, "mapTuple_950");
    auditor.nodeRegistered(mapTuple_961, "mapTuple_961");
    auditor.nodeRegistered(mapTuple_967, "mapTuple_967");
    auditor.nodeRegistered(mapTuple_980, "mapTuple_980");
    auditor.nodeRegistered(mapTuple_985, "mapTuple_985");
    auditor.nodeRegistered(subscriptionManager, "subscriptionManager");
    auditor.nodeRegistered(handlerPositionSnapshot, "handlerPositionSnapshot");
    auditor.nodeRegistered(
        handlerSignal_positionSnapshotReset, "handlerSignal_positionSnapshotReset");
    auditor.nodeRegistered(handlerSignal_positionUpdate, "handlerSignal_positionUpdate");
    auditor.nodeRegistered(handlerTrade, "handlerTrade");
    auditor.nodeRegistered(handlerTradeBatch, "handlerTradeBatch");
    auditor.nodeRegistered(context, "context");
    auditor.nodeRegistered(namedFeedTableNode_109, "namedFeedTableNode_109");
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
    eventLogger.processingComplete();
    nodeNameLookup.processingComplete();
    serviceRegistry.processingComplete();
    isDirty_binaryMapToRefFlowFunction_21 = false;
    isDirty_binaryMapToRefFlowFunction_27 = false;
    isDirty_binaryMapToRefFlowFunction_44 = false;
    isDirty_binaryMapToRefFlowFunction_52 = false;
    isDirty_binaryMapToRefFlowFunction_63 = false;
    isDirty_binaryMapToRefFlowFunction_74 = false;
    isDirty_binaryMapToRefFlowFunction_82 = false;
    isDirty_binaryMapToRefFlowFunction_93 = false;
    isDirty_binaryMapToRefFlowFunction_103 = false;
    isDirty_callBackNode_110 = false;
    isDirty_callBackNode_134 = false;
    isDirty_callBackNode_260 = false;
    isDirty_clock = false;
    isDirty_derivedRateNode = false;
    isDirty_eventFeedBatcher = false;
    isDirty_filterFlowFunction_8 = false;
    isDirty_flatMapFlowFunction_3 = false;
    isDirty_flatMapFlowFunction_17 = false;
    isDirty_flatMapFlowFunction_36 = false;
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
    isDirty_mapRef2RefFlowFunction_25 = false;
    isDirty_mapRef2RefFlowFunction_29 = false;
    isDirty_mapRef2RefFlowFunction_31 = false;
    isDirty_mapRef2RefFlowFunction_33 = false;
    isDirty_mapRef2RefFlowFunction_35 = false;
    isDirty_mapRef2RefFlowFunction_38 = false;
    isDirty_mapRef2RefFlowFunction_40 = false;
    isDirty_mapRef2RefFlowFunction_42 = false;
    isDirty_mapRef2RefFlowFunction_46 = false;
    isDirty_mapRef2RefFlowFunction_48 = false;
    isDirty_mapRef2RefFlowFunction_50 = false;
    isDirty_mapRef2RefFlowFunction_54 = false;
    isDirty_mapRef2RefFlowFunction_55 = false;
    isDirty_mapRef2RefFlowFunction_59 = false;
    isDirty_mapRef2RefFlowFunction_61 = false;
    isDirty_mapRef2RefFlowFunction_65 = false;
    isDirty_mapRef2RefFlowFunction_67 = false;
    isDirty_mapRef2RefFlowFunction_68 = false;
    isDirty_mapRef2RefFlowFunction_70 = false;
    isDirty_mapRef2RefFlowFunction_72 = false;
    isDirty_mapRef2RefFlowFunction_76 = false;
    isDirty_mapRef2RefFlowFunction_78 = false;
    isDirty_mapRef2RefFlowFunction_80 = false;
    isDirty_mapRef2RefFlowFunction_84 = false;
    isDirty_mapRef2RefFlowFunction_86 = false;
    isDirty_mapRef2RefFlowFunction_87 = false;
    isDirty_mapRef2RefFlowFunction_89 = false;
    isDirty_mapRef2RefFlowFunction_91 = false;
    isDirty_mapRef2RefFlowFunction_95 = false;
    isDirty_mapRef2RefFlowFunction_97 = false;
    isDirty_mapRef2RefFlowFunction_99 = false;
    isDirty_mapRef2RefFlowFunction_101 = false;
    isDirty_mapRef2RefFlowFunction_105 = false;
    isDirty_mergeFlowFunction_6 = false;
    isDirty_namedFeedTableNode_109 = false;
    isDirty_positionCache = false;
    isDirty_pushFlowFunction_57 = false;
    isDirty_pushFlowFunction_107 = false;
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
          binaryMapToRefFlowFunction_103, () -> isDirty_binaryMapToRefFlowFunction_103);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_21, () -> isDirty_binaryMapToRefFlowFunction_21);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_27, () -> isDirty_binaryMapToRefFlowFunction_27);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_44, () -> isDirty_binaryMapToRefFlowFunction_44);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_52, () -> isDirty_binaryMapToRefFlowFunction_52);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_63, () -> isDirty_binaryMapToRefFlowFunction_63);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_74, () -> isDirty_binaryMapToRefFlowFunction_74);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_82, () -> isDirty_binaryMapToRefFlowFunction_82);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_93, () -> isDirty_binaryMapToRefFlowFunction_93);
      dirtyFlagSupplierMap.put(callBackNode_110, () -> isDirty_callBackNode_110);
      dirtyFlagSupplierMap.put(callBackNode_134, () -> isDirty_callBackNode_134);
      dirtyFlagSupplierMap.put(callBackNode_260, () -> isDirty_callBackNode_260);
      dirtyFlagSupplierMap.put(clock, () -> isDirty_clock);
      dirtyFlagSupplierMap.put(derivedRateNode, () -> isDirty_derivedRateNode);
      dirtyFlagSupplierMap.put(eventFeedBatcher, () -> isDirty_eventFeedBatcher);
      dirtyFlagSupplierMap.put(filterFlowFunction_8, () -> isDirty_filterFlowFunction_8);
      dirtyFlagSupplierMap.put(flatMapFlowFunction_17, () -> isDirty_flatMapFlowFunction_17);
      dirtyFlagSupplierMap.put(flatMapFlowFunction_3, () -> isDirty_flatMapFlowFunction_3);
      dirtyFlagSupplierMap.put(flatMapFlowFunction_36, () -> isDirty_flatMapFlowFunction_36);
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
      dirtyFlagSupplierMap.put(
          mapRef2RefFlowFunction_101, () -> isDirty_mapRef2RefFlowFunction_101);
      dirtyFlagSupplierMap.put(
          mapRef2RefFlowFunction_105, () -> isDirty_mapRef2RefFlowFunction_105);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_12, () -> isDirty_mapRef2RefFlowFunction_12);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_14, () -> isDirty_mapRef2RefFlowFunction_14);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_16, () -> isDirty_mapRef2RefFlowFunction_16);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_19, () -> isDirty_mapRef2RefFlowFunction_19);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_23, () -> isDirty_mapRef2RefFlowFunction_23);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_25, () -> isDirty_mapRef2RefFlowFunction_25);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_29, () -> isDirty_mapRef2RefFlowFunction_29);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_31, () -> isDirty_mapRef2RefFlowFunction_31);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_33, () -> isDirty_mapRef2RefFlowFunction_33);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_35, () -> isDirty_mapRef2RefFlowFunction_35);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_38, () -> isDirty_mapRef2RefFlowFunction_38);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_4, () -> isDirty_mapRef2RefFlowFunction_4);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_40, () -> isDirty_mapRef2RefFlowFunction_40);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_42, () -> isDirty_mapRef2RefFlowFunction_42);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_46, () -> isDirty_mapRef2RefFlowFunction_46);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_48, () -> isDirty_mapRef2RefFlowFunction_48);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_5, () -> isDirty_mapRef2RefFlowFunction_5);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_50, () -> isDirty_mapRef2RefFlowFunction_50);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_54, () -> isDirty_mapRef2RefFlowFunction_54);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_55, () -> isDirty_mapRef2RefFlowFunction_55);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_59, () -> isDirty_mapRef2RefFlowFunction_59);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_61, () -> isDirty_mapRef2RefFlowFunction_61);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_65, () -> isDirty_mapRef2RefFlowFunction_65);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_67, () -> isDirty_mapRef2RefFlowFunction_67);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_68, () -> isDirty_mapRef2RefFlowFunction_68);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_70, () -> isDirty_mapRef2RefFlowFunction_70);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_72, () -> isDirty_mapRef2RefFlowFunction_72);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_76, () -> isDirty_mapRef2RefFlowFunction_76);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_78, () -> isDirty_mapRef2RefFlowFunction_78);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_80, () -> isDirty_mapRef2RefFlowFunction_80);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_84, () -> isDirty_mapRef2RefFlowFunction_84);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_86, () -> isDirty_mapRef2RefFlowFunction_86);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_87, () -> isDirty_mapRef2RefFlowFunction_87);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_89, () -> isDirty_mapRef2RefFlowFunction_89);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_91, () -> isDirty_mapRef2RefFlowFunction_91);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_95, () -> isDirty_mapRef2RefFlowFunction_95);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_97, () -> isDirty_mapRef2RefFlowFunction_97);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_99, () -> isDirty_mapRef2RefFlowFunction_99);
      dirtyFlagSupplierMap.put(mergeFlowFunction_6, () -> isDirty_mergeFlowFunction_6);
      dirtyFlagSupplierMap.put(namedFeedTableNode_109, () -> isDirty_namedFeedTableNode_109);
      dirtyFlagSupplierMap.put(positionCache, () -> isDirty_positionCache);
      dirtyFlagSupplierMap.put(pushFlowFunction_107, () -> isDirty_pushFlowFunction_107);
      dirtyFlagSupplierMap.put(pushFlowFunction_57, () -> isDirty_pushFlowFunction_57);
    }
    return dirtyFlagSupplierMap.getOrDefault(node, StaticEventProcessor.ALWAYS_FALSE);
  }

  @Override
  public void setDirty(Object node, boolean dirtyFlag) {
    if (dirtyFlagUpdateMap.isEmpty()) {
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_103, (b) -> isDirty_binaryMapToRefFlowFunction_103 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_21, (b) -> isDirty_binaryMapToRefFlowFunction_21 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_27, (b) -> isDirty_binaryMapToRefFlowFunction_27 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_44, (b) -> isDirty_binaryMapToRefFlowFunction_44 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_52, (b) -> isDirty_binaryMapToRefFlowFunction_52 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_63, (b) -> isDirty_binaryMapToRefFlowFunction_63 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_74, (b) -> isDirty_binaryMapToRefFlowFunction_74 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_82, (b) -> isDirty_binaryMapToRefFlowFunction_82 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_93, (b) -> isDirty_binaryMapToRefFlowFunction_93 = b);
      dirtyFlagUpdateMap.put(callBackNode_110, (b) -> isDirty_callBackNode_110 = b);
      dirtyFlagUpdateMap.put(callBackNode_134, (b) -> isDirty_callBackNode_134 = b);
      dirtyFlagUpdateMap.put(callBackNode_260, (b) -> isDirty_callBackNode_260 = b);
      dirtyFlagUpdateMap.put(clock, (b) -> isDirty_clock = b);
      dirtyFlagUpdateMap.put(derivedRateNode, (b) -> isDirty_derivedRateNode = b);
      dirtyFlagUpdateMap.put(eventFeedBatcher, (b) -> isDirty_eventFeedBatcher = b);
      dirtyFlagUpdateMap.put(filterFlowFunction_8, (b) -> isDirty_filterFlowFunction_8 = b);
      dirtyFlagUpdateMap.put(flatMapFlowFunction_17, (b) -> isDirty_flatMapFlowFunction_17 = b);
      dirtyFlagUpdateMap.put(flatMapFlowFunction_3, (b) -> isDirty_flatMapFlowFunction_3 = b);
      dirtyFlagUpdateMap.put(flatMapFlowFunction_36, (b) -> isDirty_flatMapFlowFunction_36 = b);
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
          mapRef2RefFlowFunction_101, (b) -> isDirty_mapRef2RefFlowFunction_101 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_105, (b) -> isDirty_mapRef2RefFlowFunction_105 = b);
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
          mapRef2RefFlowFunction_33, (b) -> isDirty_mapRef2RefFlowFunction_33 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_35, (b) -> isDirty_mapRef2RefFlowFunction_35 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_38, (b) -> isDirty_mapRef2RefFlowFunction_38 = b);
      dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_4, (b) -> isDirty_mapRef2RefFlowFunction_4 = b);
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
          mapRef2RefFlowFunction_54, (b) -> isDirty_mapRef2RefFlowFunction_54 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_55, (b) -> isDirty_mapRef2RefFlowFunction_55 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_59, (b) -> isDirty_mapRef2RefFlowFunction_59 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_61, (b) -> isDirty_mapRef2RefFlowFunction_61 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_65, (b) -> isDirty_mapRef2RefFlowFunction_65 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_67, (b) -> isDirty_mapRef2RefFlowFunction_67 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_68, (b) -> isDirty_mapRef2RefFlowFunction_68 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_70, (b) -> isDirty_mapRef2RefFlowFunction_70 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_72, (b) -> isDirty_mapRef2RefFlowFunction_72 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_76, (b) -> isDirty_mapRef2RefFlowFunction_76 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_78, (b) -> isDirty_mapRef2RefFlowFunction_78 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_80, (b) -> isDirty_mapRef2RefFlowFunction_80 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_84, (b) -> isDirty_mapRef2RefFlowFunction_84 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_86, (b) -> isDirty_mapRef2RefFlowFunction_86 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_87, (b) -> isDirty_mapRef2RefFlowFunction_87 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_89, (b) -> isDirty_mapRef2RefFlowFunction_89 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_91, (b) -> isDirty_mapRef2RefFlowFunction_91 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_95, (b) -> isDirty_mapRef2RefFlowFunction_95 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_97, (b) -> isDirty_mapRef2RefFlowFunction_97 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_99, (b) -> isDirty_mapRef2RefFlowFunction_99 = b);
      dirtyFlagUpdateMap.put(mergeFlowFunction_6, (b) -> isDirty_mergeFlowFunction_6 = b);
      dirtyFlagUpdateMap.put(namedFeedTableNode_109, (b) -> isDirty_namedFeedTableNode_109 = b);
      dirtyFlagUpdateMap.put(positionCache, (b) -> isDirty_positionCache = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_107, (b) -> isDirty_pushFlowFunction_107 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_57, (b) -> isDirty_pushFlowFunction_57 = b);
    }
    dirtyFlagUpdateMap.get(node).accept(dirtyFlag);
  }

  private boolean guardCheck_eventLogger() {
    return isDirty_clock;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_21() {
    return isDirty_mapRef2RefFlowFunction_10 | isDirty_mapRef2RefFlowFunction_12;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_27() {
    return isDirty_mapRef2RefFlowFunction_19 | isDirty_mapRef2RefFlowFunction_25;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_44() {
    return isDirty_mapRef2RefFlowFunction_38 | isDirty_mapRef2RefFlowFunction_42;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_52() {
    return isDirty_mapRef2RefFlowFunction_35 | isDirty_mapRef2RefFlowFunction_50;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_63() {
    return isDirty_mapRef2RefFlowFunction_59 | isDirty_mapRef2RefFlowFunction_61;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_74() {
    return isDirty_mapRef2RefFlowFunction_67 | isDirty_mapRef2RefFlowFunction_72;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_82() {
    return isDirty_mapRef2RefFlowFunction_14 | isDirty_mapRef2RefFlowFunction_16;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_93() {
    return isDirty_mapRef2RefFlowFunction_86 | isDirty_mapRef2RefFlowFunction_91;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_103() {
    return isDirty_mapRef2RefFlowFunction_80 | isDirty_mapRef2RefFlowFunction_101;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_108() {
    return isDirty_positionCache | isDirty_pushFlowFunction_57 | isDirty_pushFlowFunction_107;
  }

  private boolean guardCheck_filterFlowFunction_8() {
    return isDirty_mergeFlowFunction_6;
  }

  private boolean guardCheck_flatMapFlowFunction_3() {
    return isDirty_callBackNode_110;
  }

  private boolean guardCheck_flatMapFlowFunction_17() {
    return isDirty_callBackNode_134;
  }

  private boolean guardCheck_flatMapFlowFunction_36() {
    return isDirty_callBackNode_260;
  }

  private boolean guardCheck_globalNetMtm() {
    return isDirty_mapRef2RefFlowFunction_55;
  }

  private boolean guardCheck_instrumentNetMtm() {
    return isDirty_mapRef2RefFlowFunction_105;
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
    return isDirty_binaryMapToRefFlowFunction_21;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_25() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_23;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_29() {
    return isDirty_binaryMapToRefFlowFunction_27;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_31() {
    return isDirty_mapRef2RefFlowFunction_29;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_33() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_31;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_35() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_33;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_38() {
    return isDirty_flatMapFlowFunction_36
        | isDirty_handlerSignal_positionSnapshotReset
        | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_40() {
    return isDirty_filterFlowFunction_8;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_42() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_40;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_46() {
    return isDirty_binaryMapToRefFlowFunction_44 | isDirty_handlerSignal_positionSnapshotReset;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_48() {
    return isDirty_mapRef2RefFlowFunction_46;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_50() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_48;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_54() {
    return isDirty_binaryMapToRefFlowFunction_52 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_55() {
    return isDirty_mapRef2RefFlowFunction_54;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_59() {
    return isDirty_filterFlowFunction_8;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_61() {
    return isDirty_filterFlowFunction_8;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_65() {
    return isDirty_binaryMapToRefFlowFunction_63;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_67() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_65;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_68() {
    return isDirty_handlerPositionSnapshot;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_70() {
    return isDirty_mapRef2RefFlowFunction_68;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_72() {
    return isDirty_mapRef2RefFlowFunction_70;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_76() {
    return isDirty_binaryMapToRefFlowFunction_74 | isDirty_handlerSignal_positionSnapshotReset;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_78() {
    return isDirty_mapRef2RefFlowFunction_76;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_80() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_78;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_84() {
    return isDirty_binaryMapToRefFlowFunction_82;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_86() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_84;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_87() {
    return isDirty_handlerPositionSnapshot;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_89() {
    return isDirty_mapRef2RefFlowFunction_87;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_91() {
    return isDirty_mapRef2RefFlowFunction_89;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_95() {
    return isDirty_binaryMapToRefFlowFunction_93;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_97() {
    return isDirty_mapRef2RefFlowFunction_95;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_99() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_97;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_101() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_99;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_105() {
    return isDirty_binaryMapToRefFlowFunction_103 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mergeFlowFunction_6() {
    return isDirty_mapRef2RefFlowFunction_4 | isDirty_mapRef2RefFlowFunction_5;
  }

  private boolean guardCheck_pushFlowFunction_57() {
    return isDirty_globalNetMtm;
  }

  private boolean guardCheck_pushFlowFunction_107() {
    return isDirty_instrumentNetMtm;
  }

  private boolean guardCheck_groupByMapFlowFunction_32() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_groupByMapFlowFunction_49() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_groupByMapFlowFunction_79() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_groupByMapFlowFunction_96() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_context() {
    return isDirty_clock;
  }

  private boolean guardCheck_globalNetMtmListener() {
    return isDirty_pushFlowFunction_57;
  }

  private boolean guardCheck_instrumentNetMtmListener() {
    return isDirty_pushFlowFunction_107;
  }

  private boolean guardCheck_derivedRateNode() {
    return isDirty_namedFeedTableNode_109;
  }

  private boolean guardCheck_eventFeedBatcher() {
    return isDirty_namedFeedTableNode_109;
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
