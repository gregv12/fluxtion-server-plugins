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
import com.fluxtion.runtime.dataflow.function.PeekFlowFunction;
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
import com.fluxtion.runtime.dataflow.helpers.Peekers.TemplateMessage;
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
  private final CallBackNode callBackNode_118 = new CallBackNode<>(callBackTriggerEvent_0);
  private final InstanceCallbackEvent_1 callBackTriggerEvent_1 = new InstanceCallbackEvent_1();
  private final CallBackNode callBackNode_142 = new CallBackNode<>(callBackTriggerEvent_1);
  private final InstanceCallbackEvent_2 callBackTriggerEvent_2 = new InstanceCallbackEvent_2();
  private final CallBackNode callBackNode_270 = new CallBackNode<>(callBackTriggerEvent_2);
  private final CallbackDispatcherImpl callbackDispatcher = new CallbackDispatcherImpl();
  public final Clock clock = new Clock();
  private final EmptyGroupBy emptyGroupBy_167 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_26 = new DefaultValue<>(emptyGroupBy_167);
  private final EmptyGroupBy emptyGroupBy_211 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_32 = new DefaultValue<>(emptyGroupBy_211);
  private final EmptyGroupBy emptyGroupBy_249 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_36 = new DefaultValue<>(emptyGroupBy_249);
  private final EmptyGroupBy emptyGroupBy_281 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_45 = new DefaultValue<>(emptyGroupBy_281);
  private final EmptyGroupBy emptyGroupBy_314 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_53 = new DefaultValue<>(emptyGroupBy_314);
  private final EmptyGroupBy emptyGroupBy_541 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_72 = new DefaultValue<>(emptyGroupBy_541);
  private final EmptyGroupBy emptyGroupBy_555 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_77 = new DefaultValue<>(emptyGroupBy_555);
  private final EmptyGroupBy emptyGroupBy_609 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_85 = new DefaultValue<>(emptyGroupBy_609);
  private final EmptyGroupBy emptyGroupBy_670 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_93 = new DefaultValue<>(emptyGroupBy_670);
  private final EmptyGroupBy emptyGroupBy_684 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_98 = new DefaultValue<>(emptyGroupBy_684);
  private final EmptyGroupBy emptyGroupBy_740 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_106 = new DefaultValue<>(emptyGroupBy_740);
  private final EmptyGroupBy emptyGroupBy_761 = new EmptyGroupBy<>();
  private final DefaultValue defaultValue_108 = new DefaultValue<>(emptyGroupBy_761);
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
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_39 =
      new GroupByFlowFunctionWrapper<>(
          InstrumentPosition::instrument, Mappers::identity, AggregateIdentityFlowFunction::new);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_41 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, FeeInstrumentPosMtmAggregate::new);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_64 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getDealtInstrument, Mappers::identity, FeeInstrumentPosMtmAggregate::new);
  private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_66 =
      new GroupByFlowFunctionWrapper<>(
          Trade::getContraInstrument, Mappers::identity, FeeInstrumentPosMtmAggregate::new);
  private final GroupByHashMap groupByHashMap_75 = new GroupByHashMap<>();
  private final GroupByHashMap groupByHashMap_96 = new GroupByHashMap<>();
  private final LeftJoin leftJoin_57 = new LeftJoin();
  private final LeftJoin leftJoin_110 = new LeftJoin();
  private final MapTuple mapTuple_960 = new MapTuple<>(NetMarkToMarket::combineInst);
  private final GroupByMapFlowFunction groupByMapFlowFunction_112 =
      new GroupByMapFlowFunction(mapTuple_960::mapTuple);
  private final MapTuple mapTuple_966 = new MapTuple<>(FeeInstrumentPosMtmAggregate::merge);
  private final GroupByMapFlowFunction groupByMapFlowFunction_81 =
      new GroupByMapFlowFunction(mapTuple_966::mapTuple);
  private final MapTuple mapTuple_972 = new MapTuple<>(FeeInstrumentPosMtmAggregate::merge);
  private final GroupByMapFlowFunction groupByMapFlowFunction_70 =
      new GroupByMapFlowFunction(mapTuple_972::mapTuple);
  private final MapTuple mapTuple_987 = new MapTuple<>(InstrumentPosMtm::mergeSnapshot);
  private final GroupByMapFlowFunction groupByMapFlowFunction_102 =
      new GroupByMapFlowFunction(mapTuple_987::mapTuple);
  private final MapTuple mapTuple_993 = new MapTuple<>(InstrumentPosMtm::merge);
  private final GroupByMapFlowFunction groupByMapFlowFunction_91 =
      new GroupByMapFlowFunction(mapTuple_993::mapTuple);
  private final MapTuple mapTuple_1004 = new MapTuple<>(NetMarkToMarket::combine);
  private final GroupByMapFlowFunction groupByMapFlowFunction_59 =
      new GroupByMapFlowFunction(mapTuple_1004::mapTuple);
  private final MapTuple mapTuple_1010 = new MapTuple<>(FeeInstrumentPosMtm::addSnapshot);
  private final GroupByMapFlowFunction groupByMapFlowFunction_49 =
      new GroupByMapFlowFunction(mapTuple_1010::mapTuple);
  private final MapTuple mapTuple_1023 = new MapTuple<>(InstrumentPosMtm::addSnapshot);
  private final GroupByMapFlowFunction groupByMapFlowFunction_30 =
      new GroupByMapFlowFunction(mapTuple_1023::mapTuple);
  private final MapTuple mapTuple_1028 = new MapTuple<>(InstrumentPosMtm::merge);
  private final GroupByMapFlowFunction groupByMapFlowFunction_24 =
      new GroupByMapFlowFunction(mapTuple_1028::mapTuple);
  private final NamedFeedTableNode namedFeedTableNode_117 =
      new NamedFeedTableNode<>("symbolFeed", Symbol::symbolName);
  public final DerivedRateNode derivedRateNode = new DerivedRateNode(namedFeedTableNode_117);
  public final EventFeedConnector eventFeedBatcher = new EventFeedConnector(namedFeedTableNode_117);
  private final GroupByMapFlowFunction groupByMapFlowFunction_34 =
      new GroupByMapFlowFunction(derivedRateNode::calculateInstrumentPosMtm);
  private final GroupByMapFlowFunction groupByMapFlowFunction_55 =
      new GroupByMapFlowFunction(derivedRateNode::calculateFeeMtm);
  private final GroupByMapFlowFunction groupByMapFlowFunction_87 =
      new GroupByMapFlowFunction(derivedRateNode::calculateFeeMtm);
  private final GroupByMapFlowFunction groupByMapFlowFunction_104 =
      new GroupByMapFlowFunction(derivedRateNode::calculateInstrumentPosMtm_A);
  public final NodeNameAuditor nodeNameLookup = new NodeNameAuditor();
  private final OuterJoin outerJoin_22 = new OuterJoin();
  private final OuterJoin outerJoin_28 = new OuterJoin();
  private final OuterJoin outerJoin_47 = new OuterJoin();
  private final OuterJoin outerJoin_68 = new OuterJoin();
  private final OuterJoin outerJoin_79 = new OuterJoin();
  private final OuterJoin outerJoin_89 = new OuterJoin();
  private final OuterJoin outerJoin_100 = new OuterJoin();
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
  private final FlatMapFlowFunction flatMapFlowFunction_38 =
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
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_40 =
      new MapRef2RefFlowFunction<>(
          flatMapFlowFunction_38, groupByFlowFunctionWrapper_39::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_74 =
      new MapRef2RefFlowFunction<>(
          handlerPositionSnapshot, PositionSnapshot::getInstrumentFeePositionMap);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_76 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_74, groupByHashMap_75::fromMap);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_78 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_76, defaultValue_77::getOrDefault);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_95 =
      new MapRef2RefFlowFunction<>(
          handlerPositionSnapshot, PositionSnapshot::getInstrumentPositionMap);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_97 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_95, groupByHashMap_96::fromMap);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_99 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_97, defaultValue_98::getOrDefault);
  private final MergeFlowFunction mergeFlowFunction_6 =
      new MergeFlowFunction<>(Arrays.asList(mapRef2RefFlowFunction_5, mapRef2RefFlowFunction_4));
  public final ServiceRegistryNode serviceRegistry = new ServiceRegistryNode();
  private final TemplateMessage templateMessage_20 =
      new TemplateMessage<>("positionSnapshotGlobal -> {}\n");
  private final PeekFlowFunction peekFlowFunction_21 =
      new PeekFlowFunction<>(
          mapRef2RefFlowFunction_19, templateMessage_20::templateAndLogToConsole);
  private final TemplateMessage templateMessage_43 = new TemplateMessage<>("globalFeeMap -> {}\n");
  private final TemplateMessage templateMessage_51 =
      new TemplateMessage<>("mergedGlobalFeeMap -> {}\n");
  private final TemplateMessage templateMessage_83 =
      new TemplateMessage<>("mergedFeeByInstrument -> {}\n");
  private final TradeSequenceFilter tradeSequenceFilter_7 = new TradeSequenceFilter();
  private final FilterFlowFunction filterFlowFunction_8 =
      new FilterFlowFunction<>(
          mergeFlowFunction_6, tradeSequenceFilter_7::checkTradeSequenceNumber);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_10 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_8, groupByFlowFunctionWrapper_9::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_12 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_8, groupByFlowFunctionWrapper_11::aggregate);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_23 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_10, mapRef2RefFlowFunction_12, outerJoin_22::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_14 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_8, groupByFlowFunctionWrapper_13::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_16 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_8, groupByFlowFunctionWrapper_15::aggregate);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_90 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_14, mapRef2RefFlowFunction_16, outerJoin_89::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_25 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_23, groupByMapFlowFunction_24::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_27 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_25, defaultValue_26::getOrDefault);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_29 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_27, mapRef2RefFlowFunction_19, outerJoin_28::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_31 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_29, groupByMapFlowFunction_30::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_33 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_31, defaultValue_32::getOrDefault);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_35 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_33, groupByMapFlowFunction_34::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_37 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_35, defaultValue_36::getOrDefault);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_42 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_8, groupByFlowFunctionWrapper_41::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_46 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_42, defaultValue_45::getOrDefault);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_48 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_46, mapRef2RefFlowFunction_40, outerJoin_47::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_50 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_48, groupByMapFlowFunction_49::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_54 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_50, defaultValue_53::getOrDefault);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_56 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_54, groupByMapFlowFunction_55::mapValues);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_58 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_37, mapRef2RefFlowFunction_56, leftJoin_57::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_60 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_58, groupByMapFlowFunction_59::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_61 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_60, GroupBy<Object, Object>::toMap);
  public final MapRef2RefFlowFunction globalNetMtm =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_61, NetMarkToMarket::markToMarketSum);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_65 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_8, groupByFlowFunctionWrapper_64::aggregate);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_67 =
      new MapRef2RefFlowFunction<>(filterFlowFunction_8, groupByFlowFunctionWrapper_66::aggregate);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_69 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_65, mapRef2RefFlowFunction_67, outerJoin_68::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_71 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_69, groupByMapFlowFunction_70::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_73 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_71, defaultValue_72::getOrDefault);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_80 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_73, mapRef2RefFlowFunction_78, outerJoin_79::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_82 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_80, groupByMapFlowFunction_81::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_86 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_82, defaultValue_85::getOrDefault);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_88 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_86, groupByMapFlowFunction_87::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_92 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_90, groupByMapFlowFunction_91::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_94 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_92, defaultValue_93::getOrDefault);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_101 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_94, mapRef2RefFlowFunction_99, outerJoin_100::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_103 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_101, groupByMapFlowFunction_102::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_105 =
      new MapRef2RefFlowFunction<>(
          mapRef2RefFlowFunction_103, groupByMapFlowFunction_104::mapValues);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_107 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_105, defaultValue_106::getOrDefault);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_109 =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_107, defaultValue_108::getOrDefault);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_111 =
      new BinaryMapToRefFlowFunction<>(
          mapRef2RefFlowFunction_109, mapRef2RefFlowFunction_88, leftJoin_110::join);
  private final MapRef2RefFlowFunction mapRef2RefFlowFunction_113 =
      new MapRef2RefFlowFunction<>(
          binaryMapToRefFlowFunction_111, groupByMapFlowFunction_112::mapValues);
  public final MapRef2RefFlowFunction instrumentNetMtm =
      new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_113, GroupBy<Object, Object>::toMap);
  private final PeekFlowFunction peekFlowFunction_44 =
      new PeekFlowFunction<>(
          mapRef2RefFlowFunction_42, templateMessage_43::templateAndLogToConsole);
  private final PeekFlowFunction peekFlowFunction_52 =
      new PeekFlowFunction<>(
          mapRef2RefFlowFunction_50, templateMessage_51::templateAndLogToConsole);
  private final PeekFlowFunction peekFlowFunction_84 =
      new PeekFlowFunction<>(
          mapRef2RefFlowFunction_82, templateMessage_83::templateAndLogToConsole);
  private final PushFlowFunction pushFlowFunction_63 =
      new PushFlowFunction<>(globalNetMtm, globalNetMtmListener::publish);
  private final PushFlowFunction pushFlowFunction_115 =
      new PushFlowFunction<>(instrumentNetMtm, instrumentNetMtmListener::publish);
  private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_116 =
      new BinaryMapToRefFlowFunction<>(
          pushFlowFunction_63, pushFlowFunction_115, positionCache::checkPoint);
  private final ExportFunctionAuditEvent functionAudit = new ExportFunctionAuditEvent();
  //Dirty flags
  private boolean initCalled = false;
  private boolean processing = false;
  private boolean buffering = false;
  private final IdentityHashMap<Object, BooleanSupplier> dirtyFlagSupplierMap =
      new IdentityHashMap<>(72);
  private final IdentityHashMap<Object, Consumer<Boolean>> dirtyFlagUpdateMap =
      new IdentityHashMap<>(72);

  private boolean isDirty_binaryMapToRefFlowFunction_23 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_29 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_48 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_58 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_69 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_80 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_90 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_101 = false;
  private boolean isDirty_binaryMapToRefFlowFunction_111 = false;
  private boolean isDirty_callBackNode_118 = false;
  private boolean isDirty_callBackNode_142 = false;
  private boolean isDirty_callBackNode_270 = false;
  private boolean isDirty_clock = false;
  private boolean isDirty_derivedRateNode = false;
  private boolean isDirty_eventFeedBatcher = false;
  private boolean isDirty_filterFlowFunction_8 = false;
  private boolean isDirty_flatMapFlowFunction_3 = false;
  private boolean isDirty_flatMapFlowFunction_17 = false;
  private boolean isDirty_flatMapFlowFunction_38 = false;
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
  private boolean isDirty_mapRef2RefFlowFunction_25 = false;
  private boolean isDirty_mapRef2RefFlowFunction_27 = false;
  private boolean isDirty_mapRef2RefFlowFunction_31 = false;
  private boolean isDirty_mapRef2RefFlowFunction_33 = false;
  private boolean isDirty_mapRef2RefFlowFunction_35 = false;
  private boolean isDirty_mapRef2RefFlowFunction_37 = false;
  private boolean isDirty_mapRef2RefFlowFunction_40 = false;
  private boolean isDirty_mapRef2RefFlowFunction_42 = false;
  private boolean isDirty_mapRef2RefFlowFunction_46 = false;
  private boolean isDirty_mapRef2RefFlowFunction_50 = false;
  private boolean isDirty_mapRef2RefFlowFunction_54 = false;
  private boolean isDirty_mapRef2RefFlowFunction_56 = false;
  private boolean isDirty_mapRef2RefFlowFunction_60 = false;
  private boolean isDirty_mapRef2RefFlowFunction_61 = false;
  private boolean isDirty_mapRef2RefFlowFunction_65 = false;
  private boolean isDirty_mapRef2RefFlowFunction_67 = false;
  private boolean isDirty_mapRef2RefFlowFunction_71 = false;
  private boolean isDirty_mapRef2RefFlowFunction_73 = false;
  private boolean isDirty_mapRef2RefFlowFunction_74 = false;
  private boolean isDirty_mapRef2RefFlowFunction_76 = false;
  private boolean isDirty_mapRef2RefFlowFunction_78 = false;
  private boolean isDirty_mapRef2RefFlowFunction_82 = false;
  private boolean isDirty_mapRef2RefFlowFunction_86 = false;
  private boolean isDirty_mapRef2RefFlowFunction_88 = false;
  private boolean isDirty_mapRef2RefFlowFunction_92 = false;
  private boolean isDirty_mapRef2RefFlowFunction_94 = false;
  private boolean isDirty_mapRef2RefFlowFunction_95 = false;
  private boolean isDirty_mapRef2RefFlowFunction_97 = false;
  private boolean isDirty_mapRef2RefFlowFunction_99 = false;
  private boolean isDirty_mapRef2RefFlowFunction_103 = false;
  private boolean isDirty_mapRef2RefFlowFunction_105 = false;
  private boolean isDirty_mapRef2RefFlowFunction_107 = false;
  private boolean isDirty_mapRef2RefFlowFunction_109 = false;
  private boolean isDirty_mapRef2RefFlowFunction_113 = false;
  private boolean isDirty_mergeFlowFunction_6 = false;
  private boolean isDirty_namedFeedTableNode_117 = false;
  private boolean isDirty_positionCache = false;
  private boolean isDirty_pushFlowFunction_63 = false;
  private boolean isDirty_pushFlowFunction_115 = false;

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
    binaryMapToRefFlowFunction_23.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_29.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_48.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_58.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_69.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_80.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_90.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_101.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_111.setEventProcessorContext(context);
    binaryMapToRefFlowFunction_116.setEventProcessorContext(context);
    filterFlowFunction_8.setEventProcessorContext(context);
    flatMapFlowFunction_3.setFlatMapCompleteSignal("positionUpdate");
    flatMapFlowFunction_3.callback = callBackNode_118;
    flatMapFlowFunction_3.dirtyStateMonitor = callbackDispatcher;
    flatMapFlowFunction_17.callback = callBackNode_142;
    flatMapFlowFunction_17.dirtyStateMonitor = callbackDispatcher;
    flatMapFlowFunction_38.callback = callBackNode_270;
    flatMapFlowFunction_38.dirtyStateMonitor = callbackDispatcher;
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
    mapRef2RefFlowFunction_25.setEventProcessorContext(context);
    mapRef2RefFlowFunction_27.setEventProcessorContext(context);
    mapRef2RefFlowFunction_27.setPublishTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_31.setEventProcessorContext(context);
    mapRef2RefFlowFunction_33.setEventProcessorContext(context);
    mapRef2RefFlowFunction_35.setEventProcessorContext(context);
    mapRef2RefFlowFunction_35.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_37.setEventProcessorContext(context);
    mapRef2RefFlowFunction_37.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_40.setEventProcessorContext(context);
    mapRef2RefFlowFunction_40.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_40.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_42.setEventProcessorContext(context);
    mapRef2RefFlowFunction_46.setEventProcessorContext(context);
    mapRef2RefFlowFunction_46.setPublishTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_50.setEventProcessorContext(context);
    mapRef2RefFlowFunction_50.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_54.setEventProcessorContext(context);
    mapRef2RefFlowFunction_56.setEventProcessorContext(context);
    mapRef2RefFlowFunction_56.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_56.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_60.setEventProcessorContext(context);
    mapRef2RefFlowFunction_60.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_61.setEventProcessorContext(context);
    mapRef2RefFlowFunction_65.setEventProcessorContext(context);
    mapRef2RefFlowFunction_67.setEventProcessorContext(context);
    mapRef2RefFlowFunction_71.setEventProcessorContext(context);
    mapRef2RefFlowFunction_73.setEventProcessorContext(context);
    mapRef2RefFlowFunction_73.setPublishTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_74.setEventProcessorContext(context);
    mapRef2RefFlowFunction_76.setEventProcessorContext(context);
    mapRef2RefFlowFunction_78.setEventProcessorContext(context);
    mapRef2RefFlowFunction_82.setEventProcessorContext(context);
    mapRef2RefFlowFunction_82.setResetTriggerNode(handlerSignal_positionSnapshotReset);
    mapRef2RefFlowFunction_86.setEventProcessorContext(context);
    mapRef2RefFlowFunction_88.setEventProcessorContext(context);
    mapRef2RefFlowFunction_88.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_88.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_92.setEventProcessorContext(context);
    mapRef2RefFlowFunction_94.setEventProcessorContext(context);
    mapRef2RefFlowFunction_94.setPublishTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_95.setEventProcessorContext(context);
    mapRef2RefFlowFunction_97.setEventProcessorContext(context);
    mapRef2RefFlowFunction_99.setEventProcessorContext(context);
    mapRef2RefFlowFunction_103.setEventProcessorContext(context);
    mapRef2RefFlowFunction_105.setEventProcessorContext(context);
    mapRef2RefFlowFunction_107.setEventProcessorContext(context);
    mapRef2RefFlowFunction_107.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_109.setEventProcessorContext(context);
    mapRef2RefFlowFunction_109.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
    mapRef2RefFlowFunction_113.setEventProcessorContext(context);
    mapRef2RefFlowFunction_113.setUpdateTriggerNode(handlerSignal_positionUpdate);
    mergeFlowFunction_6.dirtyStateMonitor = callbackDispatcher;
    peekFlowFunction_21.setEventProcessorContext(context);
    peekFlowFunction_44.setEventProcessorContext(context);
    peekFlowFunction_52.setEventProcessorContext(context);
    peekFlowFunction_84.setEventProcessorContext(context);
    pushFlowFunction_63.setEventProcessorContext(context);
    pushFlowFunction_115.setEventProcessorContext(context);
    templateMessage_20.clock = clock;
    templateMessage_43.clock = clock;
    templateMessage_51.clock = clock;
    templateMessage_83.clock = clock;
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
    namedFeedTableNode_117.initialise();
    namedFeedTableNode_117.init();
    derivedRateNode.init();
    eventFeedBatcher.init();
    positionCache.init();
    handlerPositionSnapshot.init();
    flatMapFlowFunction_17.init();
    flatMapFlowFunction_38.init();
    handlerSignal_positionSnapshotReset.init();
    handlerSignal_positionUpdate.init();
    handlerTrade.init();
    handlerTradeBatch.init();
    flatMapFlowFunction_3.init();
    mapRef2RefFlowFunction_4.initialiseEventStream();
    mapRef2RefFlowFunction_5.initialiseEventStream();
    mapRef2RefFlowFunction_19.initialiseEventStream();
    mapRef2RefFlowFunction_40.initialiseEventStream();
    mapRef2RefFlowFunction_74.initialiseEventStream();
    mapRef2RefFlowFunction_76.initialiseEventStream();
    mapRef2RefFlowFunction_78.initialiseEventStream();
    mapRef2RefFlowFunction_95.initialiseEventStream();
    mapRef2RefFlowFunction_97.initialiseEventStream();
    mapRef2RefFlowFunction_99.initialiseEventStream();
    templateMessage_20.initialise();
    peekFlowFunction_21.initialiseEventStream();
    templateMessage_43.initialise();
    templateMessage_51.initialise();
    templateMessage_83.initialise();
    tradeSequenceFilter_7.init();
    filterFlowFunction_8.initialiseEventStream();
    mapRef2RefFlowFunction_10.initialiseEventStream();
    mapRef2RefFlowFunction_12.initialiseEventStream();
    binaryMapToRefFlowFunction_23.initialiseEventStream();
    mapRef2RefFlowFunction_14.initialiseEventStream();
    mapRef2RefFlowFunction_16.initialiseEventStream();
    binaryMapToRefFlowFunction_90.initialiseEventStream();
    mapRef2RefFlowFunction_25.initialiseEventStream();
    mapRef2RefFlowFunction_27.initialiseEventStream();
    binaryMapToRefFlowFunction_29.initialiseEventStream();
    mapRef2RefFlowFunction_31.initialiseEventStream();
    mapRef2RefFlowFunction_33.initialiseEventStream();
    mapRef2RefFlowFunction_35.initialiseEventStream();
    mapRef2RefFlowFunction_37.initialiseEventStream();
    mapRef2RefFlowFunction_42.initialiseEventStream();
    mapRef2RefFlowFunction_46.initialiseEventStream();
    binaryMapToRefFlowFunction_48.initialiseEventStream();
    mapRef2RefFlowFunction_50.initialiseEventStream();
    mapRef2RefFlowFunction_54.initialiseEventStream();
    mapRef2RefFlowFunction_56.initialiseEventStream();
    binaryMapToRefFlowFunction_58.initialiseEventStream();
    mapRef2RefFlowFunction_60.initialiseEventStream();
    mapRef2RefFlowFunction_61.initialiseEventStream();
    globalNetMtm.initialiseEventStream();
    mapRef2RefFlowFunction_65.initialiseEventStream();
    mapRef2RefFlowFunction_67.initialiseEventStream();
    binaryMapToRefFlowFunction_69.initialiseEventStream();
    mapRef2RefFlowFunction_71.initialiseEventStream();
    mapRef2RefFlowFunction_73.initialiseEventStream();
    binaryMapToRefFlowFunction_80.initialiseEventStream();
    mapRef2RefFlowFunction_82.initialiseEventStream();
    mapRef2RefFlowFunction_86.initialiseEventStream();
    mapRef2RefFlowFunction_88.initialiseEventStream();
    mapRef2RefFlowFunction_92.initialiseEventStream();
    mapRef2RefFlowFunction_94.initialiseEventStream();
    binaryMapToRefFlowFunction_101.initialiseEventStream();
    mapRef2RefFlowFunction_103.initialiseEventStream();
    mapRef2RefFlowFunction_105.initialiseEventStream();
    mapRef2RefFlowFunction_107.initialiseEventStream();
    mapRef2RefFlowFunction_109.initialiseEventStream();
    binaryMapToRefFlowFunction_111.initialiseEventStream();
    mapRef2RefFlowFunction_113.initialiseEventStream();
    instrumentNetMtm.initialiseEventStream();
    peekFlowFunction_44.initialiseEventStream();
    peekFlowFunction_52.initialiseEventStream();
    peekFlowFunction_84.initialiseEventStream();
    pushFlowFunction_63.initialiseEventStream();
    pushFlowFunction_115.initialiseEventStream();
    binaryMapToRefFlowFunction_116.initialiseEventStream();
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
    templateMessage_20.start();
    templateMessage_43.start();
    templateMessage_51.start();
    templateMessage_83.start();
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
    auditInvocation(callBackNode_118, "callBackNode_118", "onEvent", typedEvent);
    isDirty_callBackNode_118 = callBackNode_118.onEvent(typedEvent);
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
        mapRef2RefFlowFunction_42.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_65.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_67.inputUpdated(filterFlowFunction_8);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      auditInvocation(mapRef2RefFlowFunction_10, "mapRef2RefFlowFunction_10", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_23.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_23.input2Updated(mapRef2RefFlowFunction_12);
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
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_90.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_90.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_90()) {
      auditInvocation(
          binaryMapToRefFlowFunction_90, "binaryMapToRefFlowFunction_90", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_90 = binaryMapToRefFlowFunction_90.map();
      if (isDirty_binaryMapToRefFlowFunction_90) {
        mapRef2RefFlowFunction_92.inputUpdated(binaryMapToRefFlowFunction_90);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_25()) {
      auditInvocation(mapRef2RefFlowFunction_25, "mapRef2RefFlowFunction_25", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_25 = mapRef2RefFlowFunction_25.map();
      if (isDirty_mapRef2RefFlowFunction_25) {
        mapRef2RefFlowFunction_27.inputUpdated(mapRef2RefFlowFunction_25);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_27()) {
      auditInvocation(mapRef2RefFlowFunction_27, "mapRef2RefFlowFunction_27", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_27 = mapRef2RefFlowFunction_27.map();
      if (isDirty_mapRef2RefFlowFunction_27) {
        binaryMapToRefFlowFunction_29.inputUpdated(mapRef2RefFlowFunction_27);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_29()) {
      auditInvocation(
          binaryMapToRefFlowFunction_29, "binaryMapToRefFlowFunction_29", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_29 = binaryMapToRefFlowFunction_29.map();
      if (isDirty_binaryMapToRefFlowFunction_29) {
        mapRef2RefFlowFunction_31.inputUpdated(binaryMapToRefFlowFunction_29);
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
        mapRef2RefFlowFunction_37.inputUpdated(mapRef2RefFlowFunction_35);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_37()) {
      auditInvocation(mapRef2RefFlowFunction_37, "mapRef2RefFlowFunction_37", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_37 = mapRef2RefFlowFunction_37.map();
      if (isDirty_mapRef2RefFlowFunction_37) {
        binaryMapToRefFlowFunction_58.inputUpdated(mapRef2RefFlowFunction_37);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_42()) {
      auditInvocation(mapRef2RefFlowFunction_42, "mapRef2RefFlowFunction_42", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
      if (isDirty_mapRef2RefFlowFunction_42) {
        mapRef2RefFlowFunction_46.inputUpdated(mapRef2RefFlowFunction_42);
        peekFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_42);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      auditInvocation(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        binaryMapToRefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_48()) {
      auditInvocation(
          binaryMapToRefFlowFunction_48, "binaryMapToRefFlowFunction_48", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_48 = binaryMapToRefFlowFunction_48.map();
      if (isDirty_binaryMapToRefFlowFunction_48) {
        mapRef2RefFlowFunction_50.inputUpdated(binaryMapToRefFlowFunction_48);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_50()) {
      auditInvocation(mapRef2RefFlowFunction_50, "mapRef2RefFlowFunction_50", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
      if (isDirty_mapRef2RefFlowFunction_50) {
        mapRef2RefFlowFunction_54.inputUpdated(mapRef2RefFlowFunction_50);
        peekFlowFunction_52.inputUpdated(mapRef2RefFlowFunction_50);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      auditInvocation(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        mapRef2RefFlowFunction_56.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_56()) {
      auditInvocation(mapRef2RefFlowFunction_56, "mapRef2RefFlowFunction_56", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_56 = mapRef2RefFlowFunction_56.map();
      if (isDirty_mapRef2RefFlowFunction_56) {
        binaryMapToRefFlowFunction_58.input2Updated(mapRef2RefFlowFunction_56);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_58()) {
      auditInvocation(
          binaryMapToRefFlowFunction_58, "binaryMapToRefFlowFunction_58", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_58 = binaryMapToRefFlowFunction_58.map();
      if (isDirty_binaryMapToRefFlowFunction_58) {
        mapRef2RefFlowFunction_60.inputUpdated(binaryMapToRefFlowFunction_58);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_60()) {
      auditInvocation(mapRef2RefFlowFunction_60, "mapRef2RefFlowFunction_60", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_60 = mapRef2RefFlowFunction_60.map();
      if (isDirty_mapRef2RefFlowFunction_60) {
        mapRef2RefFlowFunction_61.inputUpdated(mapRef2RefFlowFunction_60);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_61()) {
      auditInvocation(mapRef2RefFlowFunction_61, "mapRef2RefFlowFunction_61", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_61 = mapRef2RefFlowFunction_61.map();
      if (isDirty_mapRef2RefFlowFunction_61) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_61);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "map", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_63.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_65()) {
      auditInvocation(mapRef2RefFlowFunction_65, "mapRef2RefFlowFunction_65", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_65 = mapRef2RefFlowFunction_65.map();
      if (isDirty_mapRef2RefFlowFunction_65) {
        binaryMapToRefFlowFunction_69.inputUpdated(mapRef2RefFlowFunction_65);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_67()) {
      auditInvocation(mapRef2RefFlowFunction_67, "mapRef2RefFlowFunction_67", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_67 = mapRef2RefFlowFunction_67.map();
      if (isDirty_mapRef2RefFlowFunction_67) {
        binaryMapToRefFlowFunction_69.input2Updated(mapRef2RefFlowFunction_67);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_69()) {
      auditInvocation(
          binaryMapToRefFlowFunction_69, "binaryMapToRefFlowFunction_69", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_69 = binaryMapToRefFlowFunction_69.map();
      if (isDirty_binaryMapToRefFlowFunction_69) {
        mapRef2RefFlowFunction_71.inputUpdated(binaryMapToRefFlowFunction_69);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_71()) {
      auditInvocation(mapRef2RefFlowFunction_71, "mapRef2RefFlowFunction_71", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_71 = mapRef2RefFlowFunction_71.map();
      if (isDirty_mapRef2RefFlowFunction_71) {
        mapRef2RefFlowFunction_73.inputUpdated(mapRef2RefFlowFunction_71);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_73()) {
      auditInvocation(mapRef2RefFlowFunction_73, "mapRef2RefFlowFunction_73", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_73 = mapRef2RefFlowFunction_73.map();
      if (isDirty_mapRef2RefFlowFunction_73) {
        binaryMapToRefFlowFunction_80.inputUpdated(mapRef2RefFlowFunction_73);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_80()) {
      auditInvocation(
          binaryMapToRefFlowFunction_80, "binaryMapToRefFlowFunction_80", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_80 = binaryMapToRefFlowFunction_80.map();
      if (isDirty_binaryMapToRefFlowFunction_80) {
        mapRef2RefFlowFunction_82.inputUpdated(binaryMapToRefFlowFunction_80);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_82()) {
      auditInvocation(mapRef2RefFlowFunction_82, "mapRef2RefFlowFunction_82", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_82 = mapRef2RefFlowFunction_82.map();
      if (isDirty_mapRef2RefFlowFunction_82) {
        mapRef2RefFlowFunction_86.inputUpdated(mapRef2RefFlowFunction_82);
        peekFlowFunction_84.inputUpdated(mapRef2RefFlowFunction_82);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_86()) {
      auditInvocation(mapRef2RefFlowFunction_86, "mapRef2RefFlowFunction_86", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_86 = mapRef2RefFlowFunction_86.map();
      if (isDirty_mapRef2RefFlowFunction_86) {
        mapRef2RefFlowFunction_88.inputUpdated(mapRef2RefFlowFunction_86);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_88()) {
      auditInvocation(mapRef2RefFlowFunction_88, "mapRef2RefFlowFunction_88", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_88 = mapRef2RefFlowFunction_88.map();
      if (isDirty_mapRef2RefFlowFunction_88) {
        binaryMapToRefFlowFunction_111.input2Updated(mapRef2RefFlowFunction_88);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_92()) {
      auditInvocation(mapRef2RefFlowFunction_92, "mapRef2RefFlowFunction_92", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_92 = mapRef2RefFlowFunction_92.map();
      if (isDirty_mapRef2RefFlowFunction_92) {
        mapRef2RefFlowFunction_94.inputUpdated(mapRef2RefFlowFunction_92);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_94()) {
      auditInvocation(mapRef2RefFlowFunction_94, "mapRef2RefFlowFunction_94", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_94 = mapRef2RefFlowFunction_94.map();
      if (isDirty_mapRef2RefFlowFunction_94) {
        binaryMapToRefFlowFunction_101.inputUpdated(mapRef2RefFlowFunction_94);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_101()) {
      auditInvocation(
          binaryMapToRefFlowFunction_101, "binaryMapToRefFlowFunction_101", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_101 = binaryMapToRefFlowFunction_101.map();
      if (isDirty_binaryMapToRefFlowFunction_101) {
        mapRef2RefFlowFunction_103.inputUpdated(binaryMapToRefFlowFunction_101);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_103()) {
      auditInvocation(mapRef2RefFlowFunction_103, "mapRef2RefFlowFunction_103", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_103 = mapRef2RefFlowFunction_103.map();
      if (isDirty_mapRef2RefFlowFunction_103) {
        mapRef2RefFlowFunction_105.inputUpdated(mapRef2RefFlowFunction_103);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_105()) {
      auditInvocation(mapRef2RefFlowFunction_105, "mapRef2RefFlowFunction_105", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_105 = mapRef2RefFlowFunction_105.map();
      if (isDirty_mapRef2RefFlowFunction_105) {
        mapRef2RefFlowFunction_107.inputUpdated(mapRef2RefFlowFunction_105);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_107()) {
      auditInvocation(mapRef2RefFlowFunction_107, "mapRef2RefFlowFunction_107", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_107 = mapRef2RefFlowFunction_107.map();
      if (isDirty_mapRef2RefFlowFunction_107) {
        mapRef2RefFlowFunction_109.inputUpdated(mapRef2RefFlowFunction_107);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_109()) {
      auditInvocation(mapRef2RefFlowFunction_109, "mapRef2RefFlowFunction_109", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_109 = mapRef2RefFlowFunction_109.map();
      if (isDirty_mapRef2RefFlowFunction_109) {
        binaryMapToRefFlowFunction_111.inputUpdated(mapRef2RefFlowFunction_109);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_111()) {
      auditInvocation(
          binaryMapToRefFlowFunction_111, "binaryMapToRefFlowFunction_111", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_111 = binaryMapToRefFlowFunction_111.map();
      if (isDirty_binaryMapToRefFlowFunction_111) {
        mapRef2RefFlowFunction_113.inputUpdated(binaryMapToRefFlowFunction_111);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_113()) {
      auditInvocation(mapRef2RefFlowFunction_113, "mapRef2RefFlowFunction_113", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_113 = mapRef2RefFlowFunction_113.map();
      if (isDirty_mapRef2RefFlowFunction_113) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_113);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_115.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_peekFlowFunction_44()) {
      auditInvocation(peekFlowFunction_44, "peekFlowFunction_44", "peek", typedEvent);
      peekFlowFunction_44.peek();
    }
    if (guardCheck_peekFlowFunction_52()) {
      auditInvocation(peekFlowFunction_52, "peekFlowFunction_52", "peek", typedEvent);
      peekFlowFunction_52.peek();
    }
    if (guardCheck_peekFlowFunction_84()) {
      auditInvocation(peekFlowFunction_84, "peekFlowFunction_84", "peek", typedEvent);
      peekFlowFunction_84.peek();
    }
    if (guardCheck_pushFlowFunction_63()) {
      auditInvocation(pushFlowFunction_63, "pushFlowFunction_63", "push", typedEvent);
      isDirty_pushFlowFunction_63 = pushFlowFunction_63.push();
      if (isDirty_pushFlowFunction_63) {
        binaryMapToRefFlowFunction_116.inputUpdated(pushFlowFunction_63);
      }
    }
    if (guardCheck_pushFlowFunction_115()) {
      auditInvocation(pushFlowFunction_115, "pushFlowFunction_115", "push", typedEvent);
      isDirty_pushFlowFunction_115 = pushFlowFunction_115.push();
      if (isDirty_pushFlowFunction_115) {
        binaryMapToRefFlowFunction_116.input2Updated(pushFlowFunction_115);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_116()) {
      auditInvocation(
          binaryMapToRefFlowFunction_116, "binaryMapToRefFlowFunction_116", "map", typedEvent);
      binaryMapToRefFlowFunction_116.map();
    }
    afterEvent();
  }

  public void handleEvent(InstanceCallbackEvent_1 typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(callBackNode_142, "callBackNode_142", "onEvent", typedEvent);
    isDirty_callBackNode_142 = callBackNode_142.onEvent(typedEvent);
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
        peekFlowFunction_21.inputUpdated(mapRef2RefFlowFunction_19);
        binaryMapToRefFlowFunction_29.input2Updated(mapRef2RefFlowFunction_19);
      }
    }
    if (guardCheck_peekFlowFunction_21()) {
      auditInvocation(peekFlowFunction_21, "peekFlowFunction_21", "peek", typedEvent);
      peekFlowFunction_21.peek();
    }
    if (guardCheck_binaryMapToRefFlowFunction_29()) {
      auditInvocation(
          binaryMapToRefFlowFunction_29, "binaryMapToRefFlowFunction_29", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_29 = binaryMapToRefFlowFunction_29.map();
      if (isDirty_binaryMapToRefFlowFunction_29) {
        mapRef2RefFlowFunction_31.inputUpdated(binaryMapToRefFlowFunction_29);
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
        mapRef2RefFlowFunction_37.inputUpdated(mapRef2RefFlowFunction_35);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_37()) {
      auditInvocation(mapRef2RefFlowFunction_37, "mapRef2RefFlowFunction_37", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_37 = mapRef2RefFlowFunction_37.map();
      if (isDirty_mapRef2RefFlowFunction_37) {
        binaryMapToRefFlowFunction_58.inputUpdated(mapRef2RefFlowFunction_37);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_58()) {
      auditInvocation(
          binaryMapToRefFlowFunction_58, "binaryMapToRefFlowFunction_58", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_58 = binaryMapToRefFlowFunction_58.map();
      if (isDirty_binaryMapToRefFlowFunction_58) {
        mapRef2RefFlowFunction_60.inputUpdated(binaryMapToRefFlowFunction_58);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_60()) {
      auditInvocation(mapRef2RefFlowFunction_60, "mapRef2RefFlowFunction_60", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_60 = mapRef2RefFlowFunction_60.map();
      if (isDirty_mapRef2RefFlowFunction_60) {
        mapRef2RefFlowFunction_61.inputUpdated(mapRef2RefFlowFunction_60);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_61()) {
      auditInvocation(mapRef2RefFlowFunction_61, "mapRef2RefFlowFunction_61", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_61 = mapRef2RefFlowFunction_61.map();
      if (isDirty_mapRef2RefFlowFunction_61) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_61);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "map", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_63.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_pushFlowFunction_63()) {
      auditInvocation(pushFlowFunction_63, "pushFlowFunction_63", "push", typedEvent);
      isDirty_pushFlowFunction_63 = pushFlowFunction_63.push();
      if (isDirty_pushFlowFunction_63) {
        binaryMapToRefFlowFunction_116.inputUpdated(pushFlowFunction_63);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_116()) {
      auditInvocation(
          binaryMapToRefFlowFunction_116, "binaryMapToRefFlowFunction_116", "map", typedEvent);
      binaryMapToRefFlowFunction_116.map();
    }
    afterEvent();
  }

  public void handleEvent(InstanceCallbackEvent_2 typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(callBackNode_270, "callBackNode_270", "onEvent", typedEvent);
    isDirty_callBackNode_270 = callBackNode_270.onEvent(typedEvent);
    if (guardCheck_flatMapFlowFunction_38()) {
      auditInvocation(
          flatMapFlowFunction_38, "flatMapFlowFunction_38", "callbackReceived", typedEvent);
      isDirty_flatMapFlowFunction_38 = true;
      flatMapFlowFunction_38.callbackReceived();
      if (isDirty_flatMapFlowFunction_38) {
        mapRef2RefFlowFunction_40.inputUpdated(flatMapFlowFunction_38);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      auditInvocation(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        binaryMapToRefFlowFunction_48.input2Updated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_48()) {
      auditInvocation(
          binaryMapToRefFlowFunction_48, "binaryMapToRefFlowFunction_48", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_48 = binaryMapToRefFlowFunction_48.map();
      if (isDirty_binaryMapToRefFlowFunction_48) {
        mapRef2RefFlowFunction_50.inputUpdated(binaryMapToRefFlowFunction_48);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_50()) {
      auditInvocation(mapRef2RefFlowFunction_50, "mapRef2RefFlowFunction_50", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
      if (isDirty_mapRef2RefFlowFunction_50) {
        mapRef2RefFlowFunction_54.inputUpdated(mapRef2RefFlowFunction_50);
        peekFlowFunction_52.inputUpdated(mapRef2RefFlowFunction_50);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      auditInvocation(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        mapRef2RefFlowFunction_56.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_56()) {
      auditInvocation(mapRef2RefFlowFunction_56, "mapRef2RefFlowFunction_56", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_56 = mapRef2RefFlowFunction_56.map();
      if (isDirty_mapRef2RefFlowFunction_56) {
        binaryMapToRefFlowFunction_58.input2Updated(mapRef2RefFlowFunction_56);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_58()) {
      auditInvocation(
          binaryMapToRefFlowFunction_58, "binaryMapToRefFlowFunction_58", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_58 = binaryMapToRefFlowFunction_58.map();
      if (isDirty_binaryMapToRefFlowFunction_58) {
        mapRef2RefFlowFunction_60.inputUpdated(binaryMapToRefFlowFunction_58);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_60()) {
      auditInvocation(mapRef2RefFlowFunction_60, "mapRef2RefFlowFunction_60", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_60 = mapRef2RefFlowFunction_60.map();
      if (isDirty_mapRef2RefFlowFunction_60) {
        mapRef2RefFlowFunction_61.inputUpdated(mapRef2RefFlowFunction_60);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_61()) {
      auditInvocation(mapRef2RefFlowFunction_61, "mapRef2RefFlowFunction_61", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_61 = mapRef2RefFlowFunction_61.map();
      if (isDirty_mapRef2RefFlowFunction_61) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_61);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "map", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_63.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_peekFlowFunction_52()) {
      auditInvocation(peekFlowFunction_52, "peekFlowFunction_52", "peek", typedEvent);
      peekFlowFunction_52.peek();
    }
    if (guardCheck_pushFlowFunction_63()) {
      auditInvocation(pushFlowFunction_63, "pushFlowFunction_63", "push", typedEvent);
      isDirty_pushFlowFunction_63 = pushFlowFunction_63.push();
      if (isDirty_pushFlowFunction_63) {
        binaryMapToRefFlowFunction_116.inputUpdated(pushFlowFunction_63);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_116()) {
      auditInvocation(
          binaryMapToRefFlowFunction_116, "binaryMapToRefFlowFunction_116", "map", typedEvent);
      binaryMapToRefFlowFunction_116.map();
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
      flatMapFlowFunction_38.inputUpdatedAndFlatMap(handlerPositionSnapshot);
      mapRef2RefFlowFunction_74.inputUpdated(handlerPositionSnapshot);
      mapRef2RefFlowFunction_95.inputUpdated(handlerPositionSnapshot);
    }
    if (guardCheck_mapRef2RefFlowFunction_74()) {
      auditInvocation(mapRef2RefFlowFunction_74, "mapRef2RefFlowFunction_74", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_74 = mapRef2RefFlowFunction_74.map();
      if (isDirty_mapRef2RefFlowFunction_74) {
        mapRef2RefFlowFunction_76.inputUpdated(mapRef2RefFlowFunction_74);
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
        binaryMapToRefFlowFunction_80.input2Updated(mapRef2RefFlowFunction_78);
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
        binaryMapToRefFlowFunction_101.input2Updated(mapRef2RefFlowFunction_99);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_80()) {
      auditInvocation(
          binaryMapToRefFlowFunction_80, "binaryMapToRefFlowFunction_80", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_80 = binaryMapToRefFlowFunction_80.map();
      if (isDirty_binaryMapToRefFlowFunction_80) {
        mapRef2RefFlowFunction_82.inputUpdated(binaryMapToRefFlowFunction_80);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_82()) {
      auditInvocation(mapRef2RefFlowFunction_82, "mapRef2RefFlowFunction_82", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_82 = mapRef2RefFlowFunction_82.map();
      if (isDirty_mapRef2RefFlowFunction_82) {
        mapRef2RefFlowFunction_86.inputUpdated(mapRef2RefFlowFunction_82);
        peekFlowFunction_84.inputUpdated(mapRef2RefFlowFunction_82);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_86()) {
      auditInvocation(mapRef2RefFlowFunction_86, "mapRef2RefFlowFunction_86", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_86 = mapRef2RefFlowFunction_86.map();
      if (isDirty_mapRef2RefFlowFunction_86) {
        mapRef2RefFlowFunction_88.inputUpdated(mapRef2RefFlowFunction_86);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_88()) {
      auditInvocation(mapRef2RefFlowFunction_88, "mapRef2RefFlowFunction_88", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_88 = mapRef2RefFlowFunction_88.map();
      if (isDirty_mapRef2RefFlowFunction_88) {
        binaryMapToRefFlowFunction_111.input2Updated(mapRef2RefFlowFunction_88);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_101()) {
      auditInvocation(
          binaryMapToRefFlowFunction_101, "binaryMapToRefFlowFunction_101", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_101 = binaryMapToRefFlowFunction_101.map();
      if (isDirty_binaryMapToRefFlowFunction_101) {
        mapRef2RefFlowFunction_103.inputUpdated(binaryMapToRefFlowFunction_101);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_103()) {
      auditInvocation(mapRef2RefFlowFunction_103, "mapRef2RefFlowFunction_103", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_103 = mapRef2RefFlowFunction_103.map();
      if (isDirty_mapRef2RefFlowFunction_103) {
        mapRef2RefFlowFunction_105.inputUpdated(mapRef2RefFlowFunction_103);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_105()) {
      auditInvocation(mapRef2RefFlowFunction_105, "mapRef2RefFlowFunction_105", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_105 = mapRef2RefFlowFunction_105.map();
      if (isDirty_mapRef2RefFlowFunction_105) {
        mapRef2RefFlowFunction_107.inputUpdated(mapRef2RefFlowFunction_105);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_107()) {
      auditInvocation(mapRef2RefFlowFunction_107, "mapRef2RefFlowFunction_107", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_107 = mapRef2RefFlowFunction_107.map();
      if (isDirty_mapRef2RefFlowFunction_107) {
        mapRef2RefFlowFunction_109.inputUpdated(mapRef2RefFlowFunction_107);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_109()) {
      auditInvocation(mapRef2RefFlowFunction_109, "mapRef2RefFlowFunction_109", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_109 = mapRef2RefFlowFunction_109.map();
      if (isDirty_mapRef2RefFlowFunction_109) {
        binaryMapToRefFlowFunction_111.inputUpdated(mapRef2RefFlowFunction_109);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_111()) {
      auditInvocation(
          binaryMapToRefFlowFunction_111, "binaryMapToRefFlowFunction_111", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_111 = binaryMapToRefFlowFunction_111.map();
      if (isDirty_binaryMapToRefFlowFunction_111) {
        mapRef2RefFlowFunction_113.inputUpdated(binaryMapToRefFlowFunction_111);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_113()) {
      auditInvocation(mapRef2RefFlowFunction_113, "mapRef2RefFlowFunction_113", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_113 = mapRef2RefFlowFunction_113.map();
      if (isDirty_mapRef2RefFlowFunction_113) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_113);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_115.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_peekFlowFunction_84()) {
      auditInvocation(peekFlowFunction_84, "peekFlowFunction_84", "peek", typedEvent);
      peekFlowFunction_84.peek();
    }
    if (guardCheck_pushFlowFunction_115()) {
      auditInvocation(pushFlowFunction_115, "pushFlowFunction_115", "push", typedEvent);
      isDirty_pushFlowFunction_115 = pushFlowFunction_115.push();
      if (isDirty_pushFlowFunction_115) {
        binaryMapToRefFlowFunction_116.input2Updated(pushFlowFunction_115);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_116()) {
      auditInvocation(
          binaryMapToRefFlowFunction_116, "binaryMapToRefFlowFunction_116", "map", typedEvent);
      binaryMapToRefFlowFunction_116.map();
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
        mapRef2RefFlowFunction_42.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_65.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_67.inputUpdated(filterFlowFunction_8);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      auditInvocation(mapRef2RefFlowFunction_10, "mapRef2RefFlowFunction_10", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_23.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_23.input2Updated(mapRef2RefFlowFunction_12);
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
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_90.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_90.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_90()) {
      auditInvocation(
          binaryMapToRefFlowFunction_90, "binaryMapToRefFlowFunction_90", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_90 = binaryMapToRefFlowFunction_90.map();
      if (isDirty_binaryMapToRefFlowFunction_90) {
        mapRef2RefFlowFunction_92.inputUpdated(binaryMapToRefFlowFunction_90);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_25()) {
      auditInvocation(mapRef2RefFlowFunction_25, "mapRef2RefFlowFunction_25", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_25 = mapRef2RefFlowFunction_25.map();
      if (isDirty_mapRef2RefFlowFunction_25) {
        mapRef2RefFlowFunction_27.inputUpdated(mapRef2RefFlowFunction_25);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_27()) {
      auditInvocation(mapRef2RefFlowFunction_27, "mapRef2RefFlowFunction_27", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_27 = mapRef2RefFlowFunction_27.map();
      if (isDirty_mapRef2RefFlowFunction_27) {
        binaryMapToRefFlowFunction_29.inputUpdated(mapRef2RefFlowFunction_27);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_29()) {
      auditInvocation(
          binaryMapToRefFlowFunction_29, "binaryMapToRefFlowFunction_29", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_29 = binaryMapToRefFlowFunction_29.map();
      if (isDirty_binaryMapToRefFlowFunction_29) {
        mapRef2RefFlowFunction_31.inputUpdated(binaryMapToRefFlowFunction_29);
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
        mapRef2RefFlowFunction_37.inputUpdated(mapRef2RefFlowFunction_35);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_37()) {
      auditInvocation(mapRef2RefFlowFunction_37, "mapRef2RefFlowFunction_37", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_37 = mapRef2RefFlowFunction_37.map();
      if (isDirty_mapRef2RefFlowFunction_37) {
        binaryMapToRefFlowFunction_58.inputUpdated(mapRef2RefFlowFunction_37);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_42()) {
      auditInvocation(mapRef2RefFlowFunction_42, "mapRef2RefFlowFunction_42", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
      if (isDirty_mapRef2RefFlowFunction_42) {
        mapRef2RefFlowFunction_46.inputUpdated(mapRef2RefFlowFunction_42);
        peekFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_42);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      auditInvocation(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        binaryMapToRefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_48()) {
      auditInvocation(
          binaryMapToRefFlowFunction_48, "binaryMapToRefFlowFunction_48", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_48 = binaryMapToRefFlowFunction_48.map();
      if (isDirty_binaryMapToRefFlowFunction_48) {
        mapRef2RefFlowFunction_50.inputUpdated(binaryMapToRefFlowFunction_48);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_50()) {
      auditInvocation(mapRef2RefFlowFunction_50, "mapRef2RefFlowFunction_50", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
      if (isDirty_mapRef2RefFlowFunction_50) {
        mapRef2RefFlowFunction_54.inputUpdated(mapRef2RefFlowFunction_50);
        peekFlowFunction_52.inputUpdated(mapRef2RefFlowFunction_50);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      auditInvocation(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        mapRef2RefFlowFunction_56.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_56()) {
      auditInvocation(mapRef2RefFlowFunction_56, "mapRef2RefFlowFunction_56", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_56 = mapRef2RefFlowFunction_56.map();
      if (isDirty_mapRef2RefFlowFunction_56) {
        binaryMapToRefFlowFunction_58.input2Updated(mapRef2RefFlowFunction_56);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_58()) {
      auditInvocation(
          binaryMapToRefFlowFunction_58, "binaryMapToRefFlowFunction_58", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_58 = binaryMapToRefFlowFunction_58.map();
      if (isDirty_binaryMapToRefFlowFunction_58) {
        mapRef2RefFlowFunction_60.inputUpdated(binaryMapToRefFlowFunction_58);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_60()) {
      auditInvocation(mapRef2RefFlowFunction_60, "mapRef2RefFlowFunction_60", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_60 = mapRef2RefFlowFunction_60.map();
      if (isDirty_mapRef2RefFlowFunction_60) {
        mapRef2RefFlowFunction_61.inputUpdated(mapRef2RefFlowFunction_60);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_61()) {
      auditInvocation(mapRef2RefFlowFunction_61, "mapRef2RefFlowFunction_61", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_61 = mapRef2RefFlowFunction_61.map();
      if (isDirty_mapRef2RefFlowFunction_61) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_61);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "map", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_63.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_65()) {
      auditInvocation(mapRef2RefFlowFunction_65, "mapRef2RefFlowFunction_65", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_65 = mapRef2RefFlowFunction_65.map();
      if (isDirty_mapRef2RefFlowFunction_65) {
        binaryMapToRefFlowFunction_69.inputUpdated(mapRef2RefFlowFunction_65);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_67()) {
      auditInvocation(mapRef2RefFlowFunction_67, "mapRef2RefFlowFunction_67", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_67 = mapRef2RefFlowFunction_67.map();
      if (isDirty_mapRef2RefFlowFunction_67) {
        binaryMapToRefFlowFunction_69.input2Updated(mapRef2RefFlowFunction_67);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_69()) {
      auditInvocation(
          binaryMapToRefFlowFunction_69, "binaryMapToRefFlowFunction_69", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_69 = binaryMapToRefFlowFunction_69.map();
      if (isDirty_binaryMapToRefFlowFunction_69) {
        mapRef2RefFlowFunction_71.inputUpdated(binaryMapToRefFlowFunction_69);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_71()) {
      auditInvocation(mapRef2RefFlowFunction_71, "mapRef2RefFlowFunction_71", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_71 = mapRef2RefFlowFunction_71.map();
      if (isDirty_mapRef2RefFlowFunction_71) {
        mapRef2RefFlowFunction_73.inputUpdated(mapRef2RefFlowFunction_71);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_73()) {
      auditInvocation(mapRef2RefFlowFunction_73, "mapRef2RefFlowFunction_73", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_73 = mapRef2RefFlowFunction_73.map();
      if (isDirty_mapRef2RefFlowFunction_73) {
        binaryMapToRefFlowFunction_80.inputUpdated(mapRef2RefFlowFunction_73);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_80()) {
      auditInvocation(
          binaryMapToRefFlowFunction_80, "binaryMapToRefFlowFunction_80", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_80 = binaryMapToRefFlowFunction_80.map();
      if (isDirty_binaryMapToRefFlowFunction_80) {
        mapRef2RefFlowFunction_82.inputUpdated(binaryMapToRefFlowFunction_80);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_82()) {
      auditInvocation(mapRef2RefFlowFunction_82, "mapRef2RefFlowFunction_82", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_82 = mapRef2RefFlowFunction_82.map();
      if (isDirty_mapRef2RefFlowFunction_82) {
        mapRef2RefFlowFunction_86.inputUpdated(mapRef2RefFlowFunction_82);
        peekFlowFunction_84.inputUpdated(mapRef2RefFlowFunction_82);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_86()) {
      auditInvocation(mapRef2RefFlowFunction_86, "mapRef2RefFlowFunction_86", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_86 = mapRef2RefFlowFunction_86.map();
      if (isDirty_mapRef2RefFlowFunction_86) {
        mapRef2RefFlowFunction_88.inputUpdated(mapRef2RefFlowFunction_86);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_88()) {
      auditInvocation(mapRef2RefFlowFunction_88, "mapRef2RefFlowFunction_88", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_88 = mapRef2RefFlowFunction_88.map();
      if (isDirty_mapRef2RefFlowFunction_88) {
        binaryMapToRefFlowFunction_111.input2Updated(mapRef2RefFlowFunction_88);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_92()) {
      auditInvocation(mapRef2RefFlowFunction_92, "mapRef2RefFlowFunction_92", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_92 = mapRef2RefFlowFunction_92.map();
      if (isDirty_mapRef2RefFlowFunction_92) {
        mapRef2RefFlowFunction_94.inputUpdated(mapRef2RefFlowFunction_92);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_94()) {
      auditInvocation(mapRef2RefFlowFunction_94, "mapRef2RefFlowFunction_94", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_94 = mapRef2RefFlowFunction_94.map();
      if (isDirty_mapRef2RefFlowFunction_94) {
        binaryMapToRefFlowFunction_101.inputUpdated(mapRef2RefFlowFunction_94);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_101()) {
      auditInvocation(
          binaryMapToRefFlowFunction_101, "binaryMapToRefFlowFunction_101", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_101 = binaryMapToRefFlowFunction_101.map();
      if (isDirty_binaryMapToRefFlowFunction_101) {
        mapRef2RefFlowFunction_103.inputUpdated(binaryMapToRefFlowFunction_101);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_103()) {
      auditInvocation(mapRef2RefFlowFunction_103, "mapRef2RefFlowFunction_103", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_103 = mapRef2RefFlowFunction_103.map();
      if (isDirty_mapRef2RefFlowFunction_103) {
        mapRef2RefFlowFunction_105.inputUpdated(mapRef2RefFlowFunction_103);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_105()) {
      auditInvocation(mapRef2RefFlowFunction_105, "mapRef2RefFlowFunction_105", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_105 = mapRef2RefFlowFunction_105.map();
      if (isDirty_mapRef2RefFlowFunction_105) {
        mapRef2RefFlowFunction_107.inputUpdated(mapRef2RefFlowFunction_105);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_107()) {
      auditInvocation(mapRef2RefFlowFunction_107, "mapRef2RefFlowFunction_107", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_107 = mapRef2RefFlowFunction_107.map();
      if (isDirty_mapRef2RefFlowFunction_107) {
        mapRef2RefFlowFunction_109.inputUpdated(mapRef2RefFlowFunction_107);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_109()) {
      auditInvocation(mapRef2RefFlowFunction_109, "mapRef2RefFlowFunction_109", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_109 = mapRef2RefFlowFunction_109.map();
      if (isDirty_mapRef2RefFlowFunction_109) {
        binaryMapToRefFlowFunction_111.inputUpdated(mapRef2RefFlowFunction_109);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_111()) {
      auditInvocation(
          binaryMapToRefFlowFunction_111, "binaryMapToRefFlowFunction_111", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_111 = binaryMapToRefFlowFunction_111.map();
      if (isDirty_binaryMapToRefFlowFunction_111) {
        mapRef2RefFlowFunction_113.inputUpdated(binaryMapToRefFlowFunction_111);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_113()) {
      auditInvocation(mapRef2RefFlowFunction_113, "mapRef2RefFlowFunction_113", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_113 = mapRef2RefFlowFunction_113.map();
      if (isDirty_mapRef2RefFlowFunction_113) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_113);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_115.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_peekFlowFunction_44()) {
      auditInvocation(peekFlowFunction_44, "peekFlowFunction_44", "peek", typedEvent);
      peekFlowFunction_44.peek();
    }
    if (guardCheck_peekFlowFunction_52()) {
      auditInvocation(peekFlowFunction_52, "peekFlowFunction_52", "peek", typedEvent);
      peekFlowFunction_52.peek();
    }
    if (guardCheck_peekFlowFunction_84()) {
      auditInvocation(peekFlowFunction_84, "peekFlowFunction_84", "peek", typedEvent);
      peekFlowFunction_84.peek();
    }
    if (guardCheck_pushFlowFunction_63()) {
      auditInvocation(pushFlowFunction_63, "pushFlowFunction_63", "push", typedEvent);
      isDirty_pushFlowFunction_63 = pushFlowFunction_63.push();
      if (isDirty_pushFlowFunction_63) {
        binaryMapToRefFlowFunction_116.inputUpdated(pushFlowFunction_63);
      }
    }
    if (guardCheck_pushFlowFunction_115()) {
      auditInvocation(pushFlowFunction_115, "pushFlowFunction_115", "push", typedEvent);
      isDirty_pushFlowFunction_115 = pushFlowFunction_115.push();
      if (isDirty_pushFlowFunction_115) {
        binaryMapToRefFlowFunction_116.input2Updated(pushFlowFunction_115);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_116()) {
      auditInvocation(
          binaryMapToRefFlowFunction_116, "binaryMapToRefFlowFunction_116", "map", typedEvent);
      binaryMapToRefFlowFunction_116.map();
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
      mapRef2RefFlowFunction_40.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_10.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_12.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_14.publishTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_14.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_16.publishTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_16.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_50.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
      mapRef2RefFlowFunction_82.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
    }
    if (guardCheck_mapRef2RefFlowFunction_19()) {
      auditInvocation(mapRef2RefFlowFunction_19, "mapRef2RefFlowFunction_19", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_19 = mapRef2RefFlowFunction_19.map();
      if (isDirty_mapRef2RefFlowFunction_19) {
        peekFlowFunction_21.inputUpdated(mapRef2RefFlowFunction_19);
        binaryMapToRefFlowFunction_29.input2Updated(mapRef2RefFlowFunction_19);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      auditInvocation(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        binaryMapToRefFlowFunction_48.input2Updated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_peekFlowFunction_21()) {
      auditInvocation(peekFlowFunction_21, "peekFlowFunction_21", "peek", typedEvent);
      peekFlowFunction_21.peek();
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      auditInvocation(mapRef2RefFlowFunction_10, "mapRef2RefFlowFunction_10", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_23.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_23.input2Updated(mapRef2RefFlowFunction_12);
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
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_90.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_90.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_90()) {
      auditInvocation(
          binaryMapToRefFlowFunction_90, "binaryMapToRefFlowFunction_90", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_90 = binaryMapToRefFlowFunction_90.map();
      if (isDirty_binaryMapToRefFlowFunction_90) {
        mapRef2RefFlowFunction_92.inputUpdated(binaryMapToRefFlowFunction_90);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_25()) {
      auditInvocation(mapRef2RefFlowFunction_25, "mapRef2RefFlowFunction_25", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_25 = mapRef2RefFlowFunction_25.map();
      if (isDirty_mapRef2RefFlowFunction_25) {
        mapRef2RefFlowFunction_27.inputUpdated(mapRef2RefFlowFunction_25);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_27()) {
      auditInvocation(mapRef2RefFlowFunction_27, "mapRef2RefFlowFunction_27", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_27 = mapRef2RefFlowFunction_27.map();
      if (isDirty_mapRef2RefFlowFunction_27) {
        binaryMapToRefFlowFunction_29.inputUpdated(mapRef2RefFlowFunction_27);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_29()) {
      auditInvocation(
          binaryMapToRefFlowFunction_29, "binaryMapToRefFlowFunction_29", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_29 = binaryMapToRefFlowFunction_29.map();
      if (isDirty_binaryMapToRefFlowFunction_29) {
        mapRef2RefFlowFunction_31.inputUpdated(binaryMapToRefFlowFunction_29);
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
        mapRef2RefFlowFunction_37.inputUpdated(mapRef2RefFlowFunction_35);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_37()) {
      auditInvocation(mapRef2RefFlowFunction_37, "mapRef2RefFlowFunction_37", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_37 = mapRef2RefFlowFunction_37.map();
      if (isDirty_mapRef2RefFlowFunction_37) {
        binaryMapToRefFlowFunction_58.inputUpdated(mapRef2RefFlowFunction_37);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_48()) {
      auditInvocation(
          binaryMapToRefFlowFunction_48, "binaryMapToRefFlowFunction_48", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_48 = binaryMapToRefFlowFunction_48.map();
      if (isDirty_binaryMapToRefFlowFunction_48) {
        mapRef2RefFlowFunction_50.inputUpdated(binaryMapToRefFlowFunction_48);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_50()) {
      auditInvocation(mapRef2RefFlowFunction_50, "mapRef2RefFlowFunction_50", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
      if (isDirty_mapRef2RefFlowFunction_50) {
        mapRef2RefFlowFunction_54.inputUpdated(mapRef2RefFlowFunction_50);
        peekFlowFunction_52.inputUpdated(mapRef2RefFlowFunction_50);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      auditInvocation(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        mapRef2RefFlowFunction_56.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_56()) {
      auditInvocation(mapRef2RefFlowFunction_56, "mapRef2RefFlowFunction_56", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_56 = mapRef2RefFlowFunction_56.map();
      if (isDirty_mapRef2RefFlowFunction_56) {
        binaryMapToRefFlowFunction_58.input2Updated(mapRef2RefFlowFunction_56);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_58()) {
      auditInvocation(
          binaryMapToRefFlowFunction_58, "binaryMapToRefFlowFunction_58", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_58 = binaryMapToRefFlowFunction_58.map();
      if (isDirty_binaryMapToRefFlowFunction_58) {
        mapRef2RefFlowFunction_60.inputUpdated(binaryMapToRefFlowFunction_58);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_60()) {
      auditInvocation(mapRef2RefFlowFunction_60, "mapRef2RefFlowFunction_60", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_60 = mapRef2RefFlowFunction_60.map();
      if (isDirty_mapRef2RefFlowFunction_60) {
        mapRef2RefFlowFunction_61.inputUpdated(mapRef2RefFlowFunction_60);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_61()) {
      auditInvocation(mapRef2RefFlowFunction_61, "mapRef2RefFlowFunction_61", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_61 = mapRef2RefFlowFunction_61.map();
      if (isDirty_mapRef2RefFlowFunction_61) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_61);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "map", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_63.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_82()) {
      auditInvocation(mapRef2RefFlowFunction_82, "mapRef2RefFlowFunction_82", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_82 = mapRef2RefFlowFunction_82.map();
      if (isDirty_mapRef2RefFlowFunction_82) {
        mapRef2RefFlowFunction_86.inputUpdated(mapRef2RefFlowFunction_82);
        peekFlowFunction_84.inputUpdated(mapRef2RefFlowFunction_82);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_86()) {
      auditInvocation(mapRef2RefFlowFunction_86, "mapRef2RefFlowFunction_86", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_86 = mapRef2RefFlowFunction_86.map();
      if (isDirty_mapRef2RefFlowFunction_86) {
        mapRef2RefFlowFunction_88.inputUpdated(mapRef2RefFlowFunction_86);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_88()) {
      auditInvocation(mapRef2RefFlowFunction_88, "mapRef2RefFlowFunction_88", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_88 = mapRef2RefFlowFunction_88.map();
      if (isDirty_mapRef2RefFlowFunction_88) {
        binaryMapToRefFlowFunction_111.input2Updated(mapRef2RefFlowFunction_88);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_92()) {
      auditInvocation(mapRef2RefFlowFunction_92, "mapRef2RefFlowFunction_92", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_92 = mapRef2RefFlowFunction_92.map();
      if (isDirty_mapRef2RefFlowFunction_92) {
        mapRef2RefFlowFunction_94.inputUpdated(mapRef2RefFlowFunction_92);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_94()) {
      auditInvocation(mapRef2RefFlowFunction_94, "mapRef2RefFlowFunction_94", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_94 = mapRef2RefFlowFunction_94.map();
      if (isDirty_mapRef2RefFlowFunction_94) {
        binaryMapToRefFlowFunction_101.inputUpdated(mapRef2RefFlowFunction_94);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_101()) {
      auditInvocation(
          binaryMapToRefFlowFunction_101, "binaryMapToRefFlowFunction_101", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_101 = binaryMapToRefFlowFunction_101.map();
      if (isDirty_binaryMapToRefFlowFunction_101) {
        mapRef2RefFlowFunction_103.inputUpdated(binaryMapToRefFlowFunction_101);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_103()) {
      auditInvocation(mapRef2RefFlowFunction_103, "mapRef2RefFlowFunction_103", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_103 = mapRef2RefFlowFunction_103.map();
      if (isDirty_mapRef2RefFlowFunction_103) {
        mapRef2RefFlowFunction_105.inputUpdated(mapRef2RefFlowFunction_103);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_105()) {
      auditInvocation(mapRef2RefFlowFunction_105, "mapRef2RefFlowFunction_105", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_105 = mapRef2RefFlowFunction_105.map();
      if (isDirty_mapRef2RefFlowFunction_105) {
        mapRef2RefFlowFunction_107.inputUpdated(mapRef2RefFlowFunction_105);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_107()) {
      auditInvocation(mapRef2RefFlowFunction_107, "mapRef2RefFlowFunction_107", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_107 = mapRef2RefFlowFunction_107.map();
      if (isDirty_mapRef2RefFlowFunction_107) {
        mapRef2RefFlowFunction_109.inputUpdated(mapRef2RefFlowFunction_107);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_109()) {
      auditInvocation(mapRef2RefFlowFunction_109, "mapRef2RefFlowFunction_109", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_109 = mapRef2RefFlowFunction_109.map();
      if (isDirty_mapRef2RefFlowFunction_109) {
        binaryMapToRefFlowFunction_111.inputUpdated(mapRef2RefFlowFunction_109);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_111()) {
      auditInvocation(
          binaryMapToRefFlowFunction_111, "binaryMapToRefFlowFunction_111", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_111 = binaryMapToRefFlowFunction_111.map();
      if (isDirty_binaryMapToRefFlowFunction_111) {
        mapRef2RefFlowFunction_113.inputUpdated(binaryMapToRefFlowFunction_111);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_113()) {
      auditInvocation(mapRef2RefFlowFunction_113, "mapRef2RefFlowFunction_113", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_113 = mapRef2RefFlowFunction_113.map();
      if (isDirty_mapRef2RefFlowFunction_113) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_113);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_115.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_peekFlowFunction_52()) {
      auditInvocation(peekFlowFunction_52, "peekFlowFunction_52", "peek", typedEvent);
      peekFlowFunction_52.peek();
    }
    if (guardCheck_peekFlowFunction_84()) {
      auditInvocation(peekFlowFunction_84, "peekFlowFunction_84", "peek", typedEvent);
      peekFlowFunction_84.peek();
    }
    if (guardCheck_pushFlowFunction_63()) {
      auditInvocation(pushFlowFunction_63, "pushFlowFunction_63", "push", typedEvent);
      isDirty_pushFlowFunction_63 = pushFlowFunction_63.push();
      if (isDirty_pushFlowFunction_63) {
        binaryMapToRefFlowFunction_116.inputUpdated(pushFlowFunction_63);
      }
    }
    if (guardCheck_pushFlowFunction_115()) {
      auditInvocation(pushFlowFunction_115, "pushFlowFunction_115", "push", typedEvent);
      isDirty_pushFlowFunction_115 = pushFlowFunction_115.push();
      if (isDirty_pushFlowFunction_115) {
        binaryMapToRefFlowFunction_116.input2Updated(pushFlowFunction_115);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_116()) {
      auditInvocation(
          binaryMapToRefFlowFunction_116, "binaryMapToRefFlowFunction_116", "map", typedEvent);
      binaryMapToRefFlowFunction_116.map();
    }
  }

  private void handle_Signal_positionUpdate(Signal typedEvent) {
    auditInvocation(
        handlerSignal_positionUpdate, "handlerSignal_positionUpdate", "onEvent", typedEvent);
    isDirty_handlerSignal_positionUpdate = handlerSignal_positionUpdate.onEvent(typedEvent);
    if (isDirty_handlerSignal_positionUpdate) {
      mapRef2RefFlowFunction_19.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_40.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_27.publishTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_35.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_37.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_46.publishTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_56.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_56.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_60.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_73.publishTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_88.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_88.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_94.publishTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_107.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_109.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
      mapRef2RefFlowFunction_113.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
    }
    if (guardCheck_mapRef2RefFlowFunction_19()) {
      auditInvocation(mapRef2RefFlowFunction_19, "mapRef2RefFlowFunction_19", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_19 = mapRef2RefFlowFunction_19.map();
      if (isDirty_mapRef2RefFlowFunction_19) {
        peekFlowFunction_21.inputUpdated(mapRef2RefFlowFunction_19);
        binaryMapToRefFlowFunction_29.input2Updated(mapRef2RefFlowFunction_19);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_40()) {
      auditInvocation(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_40 = mapRef2RefFlowFunction_40.map();
      if (isDirty_mapRef2RefFlowFunction_40) {
        binaryMapToRefFlowFunction_48.input2Updated(mapRef2RefFlowFunction_40);
      }
    }
    if (guardCheck_peekFlowFunction_21()) {
      auditInvocation(peekFlowFunction_21, "peekFlowFunction_21", "peek", typedEvent);
      peekFlowFunction_21.peek();
    }
    if (guardCheck_mapRef2RefFlowFunction_27()) {
      auditInvocation(mapRef2RefFlowFunction_27, "mapRef2RefFlowFunction_27", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_27 = mapRef2RefFlowFunction_27.map();
      if (isDirty_mapRef2RefFlowFunction_27) {
        binaryMapToRefFlowFunction_29.inputUpdated(mapRef2RefFlowFunction_27);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_29()) {
      auditInvocation(
          binaryMapToRefFlowFunction_29, "binaryMapToRefFlowFunction_29", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_29 = binaryMapToRefFlowFunction_29.map();
      if (isDirty_binaryMapToRefFlowFunction_29) {
        mapRef2RefFlowFunction_31.inputUpdated(binaryMapToRefFlowFunction_29);
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
        mapRef2RefFlowFunction_37.inputUpdated(mapRef2RefFlowFunction_35);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_37()) {
      auditInvocation(mapRef2RefFlowFunction_37, "mapRef2RefFlowFunction_37", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_37 = mapRef2RefFlowFunction_37.map();
      if (isDirty_mapRef2RefFlowFunction_37) {
        binaryMapToRefFlowFunction_58.inputUpdated(mapRef2RefFlowFunction_37);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      auditInvocation(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        binaryMapToRefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_48()) {
      auditInvocation(
          binaryMapToRefFlowFunction_48, "binaryMapToRefFlowFunction_48", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_48 = binaryMapToRefFlowFunction_48.map();
      if (isDirty_binaryMapToRefFlowFunction_48) {
        mapRef2RefFlowFunction_50.inputUpdated(binaryMapToRefFlowFunction_48);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_50()) {
      auditInvocation(mapRef2RefFlowFunction_50, "mapRef2RefFlowFunction_50", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
      if (isDirty_mapRef2RefFlowFunction_50) {
        mapRef2RefFlowFunction_54.inputUpdated(mapRef2RefFlowFunction_50);
        peekFlowFunction_52.inputUpdated(mapRef2RefFlowFunction_50);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      auditInvocation(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        mapRef2RefFlowFunction_56.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_56()) {
      auditInvocation(mapRef2RefFlowFunction_56, "mapRef2RefFlowFunction_56", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_56 = mapRef2RefFlowFunction_56.map();
      if (isDirty_mapRef2RefFlowFunction_56) {
        binaryMapToRefFlowFunction_58.input2Updated(mapRef2RefFlowFunction_56);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_58()) {
      auditInvocation(
          binaryMapToRefFlowFunction_58, "binaryMapToRefFlowFunction_58", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_58 = binaryMapToRefFlowFunction_58.map();
      if (isDirty_binaryMapToRefFlowFunction_58) {
        mapRef2RefFlowFunction_60.inputUpdated(binaryMapToRefFlowFunction_58);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_60()) {
      auditInvocation(mapRef2RefFlowFunction_60, "mapRef2RefFlowFunction_60", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_60 = mapRef2RefFlowFunction_60.map();
      if (isDirty_mapRef2RefFlowFunction_60) {
        mapRef2RefFlowFunction_61.inputUpdated(mapRef2RefFlowFunction_60);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_61()) {
      auditInvocation(mapRef2RefFlowFunction_61, "mapRef2RefFlowFunction_61", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_61 = mapRef2RefFlowFunction_61.map();
      if (isDirty_mapRef2RefFlowFunction_61) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_61);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "map", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_63.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_73()) {
      auditInvocation(mapRef2RefFlowFunction_73, "mapRef2RefFlowFunction_73", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_73 = mapRef2RefFlowFunction_73.map();
      if (isDirty_mapRef2RefFlowFunction_73) {
        binaryMapToRefFlowFunction_80.inputUpdated(mapRef2RefFlowFunction_73);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_80()) {
      auditInvocation(
          binaryMapToRefFlowFunction_80, "binaryMapToRefFlowFunction_80", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_80 = binaryMapToRefFlowFunction_80.map();
      if (isDirty_binaryMapToRefFlowFunction_80) {
        mapRef2RefFlowFunction_82.inputUpdated(binaryMapToRefFlowFunction_80);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_82()) {
      auditInvocation(mapRef2RefFlowFunction_82, "mapRef2RefFlowFunction_82", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_82 = mapRef2RefFlowFunction_82.map();
      if (isDirty_mapRef2RefFlowFunction_82) {
        mapRef2RefFlowFunction_86.inputUpdated(mapRef2RefFlowFunction_82);
        peekFlowFunction_84.inputUpdated(mapRef2RefFlowFunction_82);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_86()) {
      auditInvocation(mapRef2RefFlowFunction_86, "mapRef2RefFlowFunction_86", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_86 = mapRef2RefFlowFunction_86.map();
      if (isDirty_mapRef2RefFlowFunction_86) {
        mapRef2RefFlowFunction_88.inputUpdated(mapRef2RefFlowFunction_86);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_88()) {
      auditInvocation(mapRef2RefFlowFunction_88, "mapRef2RefFlowFunction_88", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_88 = mapRef2RefFlowFunction_88.map();
      if (isDirty_mapRef2RefFlowFunction_88) {
        binaryMapToRefFlowFunction_111.input2Updated(mapRef2RefFlowFunction_88);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_94()) {
      auditInvocation(mapRef2RefFlowFunction_94, "mapRef2RefFlowFunction_94", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_94 = mapRef2RefFlowFunction_94.map();
      if (isDirty_mapRef2RefFlowFunction_94) {
        binaryMapToRefFlowFunction_101.inputUpdated(mapRef2RefFlowFunction_94);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_101()) {
      auditInvocation(
          binaryMapToRefFlowFunction_101, "binaryMapToRefFlowFunction_101", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_101 = binaryMapToRefFlowFunction_101.map();
      if (isDirty_binaryMapToRefFlowFunction_101) {
        mapRef2RefFlowFunction_103.inputUpdated(binaryMapToRefFlowFunction_101);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_103()) {
      auditInvocation(mapRef2RefFlowFunction_103, "mapRef2RefFlowFunction_103", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_103 = mapRef2RefFlowFunction_103.map();
      if (isDirty_mapRef2RefFlowFunction_103) {
        mapRef2RefFlowFunction_105.inputUpdated(mapRef2RefFlowFunction_103);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_105()) {
      auditInvocation(mapRef2RefFlowFunction_105, "mapRef2RefFlowFunction_105", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_105 = mapRef2RefFlowFunction_105.map();
      if (isDirty_mapRef2RefFlowFunction_105) {
        mapRef2RefFlowFunction_107.inputUpdated(mapRef2RefFlowFunction_105);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_107()) {
      auditInvocation(mapRef2RefFlowFunction_107, "mapRef2RefFlowFunction_107", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_107 = mapRef2RefFlowFunction_107.map();
      if (isDirty_mapRef2RefFlowFunction_107) {
        mapRef2RefFlowFunction_109.inputUpdated(mapRef2RefFlowFunction_107);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_109()) {
      auditInvocation(mapRef2RefFlowFunction_109, "mapRef2RefFlowFunction_109", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_109 = mapRef2RefFlowFunction_109.map();
      if (isDirty_mapRef2RefFlowFunction_109) {
        binaryMapToRefFlowFunction_111.inputUpdated(mapRef2RefFlowFunction_109);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_111()) {
      auditInvocation(
          binaryMapToRefFlowFunction_111, "binaryMapToRefFlowFunction_111", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_111 = binaryMapToRefFlowFunction_111.map();
      if (isDirty_binaryMapToRefFlowFunction_111) {
        mapRef2RefFlowFunction_113.inputUpdated(binaryMapToRefFlowFunction_111);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_113()) {
      auditInvocation(mapRef2RefFlowFunction_113, "mapRef2RefFlowFunction_113", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_113 = mapRef2RefFlowFunction_113.map();
      if (isDirty_mapRef2RefFlowFunction_113) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_113);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_115.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_peekFlowFunction_52()) {
      auditInvocation(peekFlowFunction_52, "peekFlowFunction_52", "peek", typedEvent);
      peekFlowFunction_52.peek();
    }
    if (guardCheck_peekFlowFunction_84()) {
      auditInvocation(peekFlowFunction_84, "peekFlowFunction_84", "peek", typedEvent);
      peekFlowFunction_84.peek();
    }
    if (guardCheck_pushFlowFunction_63()) {
      auditInvocation(pushFlowFunction_63, "pushFlowFunction_63", "push", typedEvent);
      isDirty_pushFlowFunction_63 = pushFlowFunction_63.push();
      if (isDirty_pushFlowFunction_63) {
        binaryMapToRefFlowFunction_116.inputUpdated(pushFlowFunction_63);
      }
    }
    if (guardCheck_pushFlowFunction_115()) {
      auditInvocation(pushFlowFunction_115, "pushFlowFunction_115", "push", typedEvent);
      isDirty_pushFlowFunction_115 = pushFlowFunction_115.push();
      if (isDirty_pushFlowFunction_115) {
        binaryMapToRefFlowFunction_116.input2Updated(pushFlowFunction_115);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_116()) {
      auditInvocation(
          binaryMapToRefFlowFunction_116, "binaryMapToRefFlowFunction_116", "map", typedEvent);
      binaryMapToRefFlowFunction_116.map();
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
    auditInvocation(namedFeedTableNode_117, "namedFeedTableNode_117", "tableUpdate", typedEvent);
    isDirty_namedFeedTableNode_117 = namedFeedTableNode_117.tableUpdate(typedEvent);
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
        mapRef2RefFlowFunction_42.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_65.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_67.inputUpdated(filterFlowFunction_8);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      auditInvocation(mapRef2RefFlowFunction_10, "mapRef2RefFlowFunction_10", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_23.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_23.input2Updated(mapRef2RefFlowFunction_12);
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
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_90.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_90.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_90()) {
      auditInvocation(
          binaryMapToRefFlowFunction_90, "binaryMapToRefFlowFunction_90", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_90 = binaryMapToRefFlowFunction_90.map();
      if (isDirty_binaryMapToRefFlowFunction_90) {
        mapRef2RefFlowFunction_92.inputUpdated(binaryMapToRefFlowFunction_90);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_25()) {
      auditInvocation(mapRef2RefFlowFunction_25, "mapRef2RefFlowFunction_25", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_25 = mapRef2RefFlowFunction_25.map();
      if (isDirty_mapRef2RefFlowFunction_25) {
        mapRef2RefFlowFunction_27.inputUpdated(mapRef2RefFlowFunction_25);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_27()) {
      auditInvocation(mapRef2RefFlowFunction_27, "mapRef2RefFlowFunction_27", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_27 = mapRef2RefFlowFunction_27.map();
      if (isDirty_mapRef2RefFlowFunction_27) {
        binaryMapToRefFlowFunction_29.inputUpdated(mapRef2RefFlowFunction_27);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_29()) {
      auditInvocation(
          binaryMapToRefFlowFunction_29, "binaryMapToRefFlowFunction_29", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_29 = binaryMapToRefFlowFunction_29.map();
      if (isDirty_binaryMapToRefFlowFunction_29) {
        mapRef2RefFlowFunction_31.inputUpdated(binaryMapToRefFlowFunction_29);
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
        mapRef2RefFlowFunction_37.inputUpdated(mapRef2RefFlowFunction_35);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_37()) {
      auditInvocation(mapRef2RefFlowFunction_37, "mapRef2RefFlowFunction_37", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_37 = mapRef2RefFlowFunction_37.map();
      if (isDirty_mapRef2RefFlowFunction_37) {
        binaryMapToRefFlowFunction_58.inputUpdated(mapRef2RefFlowFunction_37);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_42()) {
      auditInvocation(mapRef2RefFlowFunction_42, "mapRef2RefFlowFunction_42", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
      if (isDirty_mapRef2RefFlowFunction_42) {
        mapRef2RefFlowFunction_46.inputUpdated(mapRef2RefFlowFunction_42);
        peekFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_42);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      auditInvocation(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        binaryMapToRefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_48()) {
      auditInvocation(
          binaryMapToRefFlowFunction_48, "binaryMapToRefFlowFunction_48", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_48 = binaryMapToRefFlowFunction_48.map();
      if (isDirty_binaryMapToRefFlowFunction_48) {
        mapRef2RefFlowFunction_50.inputUpdated(binaryMapToRefFlowFunction_48);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_50()) {
      auditInvocation(mapRef2RefFlowFunction_50, "mapRef2RefFlowFunction_50", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
      if (isDirty_mapRef2RefFlowFunction_50) {
        mapRef2RefFlowFunction_54.inputUpdated(mapRef2RefFlowFunction_50);
        peekFlowFunction_52.inputUpdated(mapRef2RefFlowFunction_50);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      auditInvocation(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        mapRef2RefFlowFunction_56.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_56()) {
      auditInvocation(mapRef2RefFlowFunction_56, "mapRef2RefFlowFunction_56", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_56 = mapRef2RefFlowFunction_56.map();
      if (isDirty_mapRef2RefFlowFunction_56) {
        binaryMapToRefFlowFunction_58.input2Updated(mapRef2RefFlowFunction_56);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_58()) {
      auditInvocation(
          binaryMapToRefFlowFunction_58, "binaryMapToRefFlowFunction_58", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_58 = binaryMapToRefFlowFunction_58.map();
      if (isDirty_binaryMapToRefFlowFunction_58) {
        mapRef2RefFlowFunction_60.inputUpdated(binaryMapToRefFlowFunction_58);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_60()) {
      auditInvocation(mapRef2RefFlowFunction_60, "mapRef2RefFlowFunction_60", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_60 = mapRef2RefFlowFunction_60.map();
      if (isDirty_mapRef2RefFlowFunction_60) {
        mapRef2RefFlowFunction_61.inputUpdated(mapRef2RefFlowFunction_60);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_61()) {
      auditInvocation(mapRef2RefFlowFunction_61, "mapRef2RefFlowFunction_61", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_61 = mapRef2RefFlowFunction_61.map();
      if (isDirty_mapRef2RefFlowFunction_61) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_61);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "map", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_63.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_65()) {
      auditInvocation(mapRef2RefFlowFunction_65, "mapRef2RefFlowFunction_65", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_65 = mapRef2RefFlowFunction_65.map();
      if (isDirty_mapRef2RefFlowFunction_65) {
        binaryMapToRefFlowFunction_69.inputUpdated(mapRef2RefFlowFunction_65);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_67()) {
      auditInvocation(mapRef2RefFlowFunction_67, "mapRef2RefFlowFunction_67", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_67 = mapRef2RefFlowFunction_67.map();
      if (isDirty_mapRef2RefFlowFunction_67) {
        binaryMapToRefFlowFunction_69.input2Updated(mapRef2RefFlowFunction_67);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_69()) {
      auditInvocation(
          binaryMapToRefFlowFunction_69, "binaryMapToRefFlowFunction_69", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_69 = binaryMapToRefFlowFunction_69.map();
      if (isDirty_binaryMapToRefFlowFunction_69) {
        mapRef2RefFlowFunction_71.inputUpdated(binaryMapToRefFlowFunction_69);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_71()) {
      auditInvocation(mapRef2RefFlowFunction_71, "mapRef2RefFlowFunction_71", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_71 = mapRef2RefFlowFunction_71.map();
      if (isDirty_mapRef2RefFlowFunction_71) {
        mapRef2RefFlowFunction_73.inputUpdated(mapRef2RefFlowFunction_71);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_73()) {
      auditInvocation(mapRef2RefFlowFunction_73, "mapRef2RefFlowFunction_73", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_73 = mapRef2RefFlowFunction_73.map();
      if (isDirty_mapRef2RefFlowFunction_73) {
        binaryMapToRefFlowFunction_80.inputUpdated(mapRef2RefFlowFunction_73);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_80()) {
      auditInvocation(
          binaryMapToRefFlowFunction_80, "binaryMapToRefFlowFunction_80", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_80 = binaryMapToRefFlowFunction_80.map();
      if (isDirty_binaryMapToRefFlowFunction_80) {
        mapRef2RefFlowFunction_82.inputUpdated(binaryMapToRefFlowFunction_80);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_82()) {
      auditInvocation(mapRef2RefFlowFunction_82, "mapRef2RefFlowFunction_82", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_82 = mapRef2RefFlowFunction_82.map();
      if (isDirty_mapRef2RefFlowFunction_82) {
        mapRef2RefFlowFunction_86.inputUpdated(mapRef2RefFlowFunction_82);
        peekFlowFunction_84.inputUpdated(mapRef2RefFlowFunction_82);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_86()) {
      auditInvocation(mapRef2RefFlowFunction_86, "mapRef2RefFlowFunction_86", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_86 = mapRef2RefFlowFunction_86.map();
      if (isDirty_mapRef2RefFlowFunction_86) {
        mapRef2RefFlowFunction_88.inputUpdated(mapRef2RefFlowFunction_86);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_88()) {
      auditInvocation(mapRef2RefFlowFunction_88, "mapRef2RefFlowFunction_88", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_88 = mapRef2RefFlowFunction_88.map();
      if (isDirty_mapRef2RefFlowFunction_88) {
        binaryMapToRefFlowFunction_111.input2Updated(mapRef2RefFlowFunction_88);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_92()) {
      auditInvocation(mapRef2RefFlowFunction_92, "mapRef2RefFlowFunction_92", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_92 = mapRef2RefFlowFunction_92.map();
      if (isDirty_mapRef2RefFlowFunction_92) {
        mapRef2RefFlowFunction_94.inputUpdated(mapRef2RefFlowFunction_92);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_94()) {
      auditInvocation(mapRef2RefFlowFunction_94, "mapRef2RefFlowFunction_94", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_94 = mapRef2RefFlowFunction_94.map();
      if (isDirty_mapRef2RefFlowFunction_94) {
        binaryMapToRefFlowFunction_101.inputUpdated(mapRef2RefFlowFunction_94);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_101()) {
      auditInvocation(
          binaryMapToRefFlowFunction_101, "binaryMapToRefFlowFunction_101", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_101 = binaryMapToRefFlowFunction_101.map();
      if (isDirty_binaryMapToRefFlowFunction_101) {
        mapRef2RefFlowFunction_103.inputUpdated(binaryMapToRefFlowFunction_101);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_103()) {
      auditInvocation(mapRef2RefFlowFunction_103, "mapRef2RefFlowFunction_103", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_103 = mapRef2RefFlowFunction_103.map();
      if (isDirty_mapRef2RefFlowFunction_103) {
        mapRef2RefFlowFunction_105.inputUpdated(mapRef2RefFlowFunction_103);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_105()) {
      auditInvocation(mapRef2RefFlowFunction_105, "mapRef2RefFlowFunction_105", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_105 = mapRef2RefFlowFunction_105.map();
      if (isDirty_mapRef2RefFlowFunction_105) {
        mapRef2RefFlowFunction_107.inputUpdated(mapRef2RefFlowFunction_105);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_107()) {
      auditInvocation(mapRef2RefFlowFunction_107, "mapRef2RefFlowFunction_107", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_107 = mapRef2RefFlowFunction_107.map();
      if (isDirty_mapRef2RefFlowFunction_107) {
        mapRef2RefFlowFunction_109.inputUpdated(mapRef2RefFlowFunction_107);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_109()) {
      auditInvocation(mapRef2RefFlowFunction_109, "mapRef2RefFlowFunction_109", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_109 = mapRef2RefFlowFunction_109.map();
      if (isDirty_mapRef2RefFlowFunction_109) {
        binaryMapToRefFlowFunction_111.inputUpdated(mapRef2RefFlowFunction_109);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_111()) {
      auditInvocation(
          binaryMapToRefFlowFunction_111, "binaryMapToRefFlowFunction_111", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_111 = binaryMapToRefFlowFunction_111.map();
      if (isDirty_binaryMapToRefFlowFunction_111) {
        mapRef2RefFlowFunction_113.inputUpdated(binaryMapToRefFlowFunction_111);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_113()) {
      auditInvocation(mapRef2RefFlowFunction_113, "mapRef2RefFlowFunction_113", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_113 = mapRef2RefFlowFunction_113.map();
      if (isDirty_mapRef2RefFlowFunction_113) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_113);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_115.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_peekFlowFunction_44()) {
      auditInvocation(peekFlowFunction_44, "peekFlowFunction_44", "peek", typedEvent);
      peekFlowFunction_44.peek();
    }
    if (guardCheck_peekFlowFunction_52()) {
      auditInvocation(peekFlowFunction_52, "peekFlowFunction_52", "peek", typedEvent);
      peekFlowFunction_52.peek();
    }
    if (guardCheck_peekFlowFunction_84()) {
      auditInvocation(peekFlowFunction_84, "peekFlowFunction_84", "peek", typedEvent);
      peekFlowFunction_84.peek();
    }
    if (guardCheck_pushFlowFunction_63()) {
      auditInvocation(pushFlowFunction_63, "pushFlowFunction_63", "push", typedEvent);
      isDirty_pushFlowFunction_63 = pushFlowFunction_63.push();
      if (isDirty_pushFlowFunction_63) {
        binaryMapToRefFlowFunction_116.inputUpdated(pushFlowFunction_63);
      }
    }
    if (guardCheck_pushFlowFunction_115()) {
      auditInvocation(pushFlowFunction_115, "pushFlowFunction_115", "push", typedEvent);
      isDirty_pushFlowFunction_115 = pushFlowFunction_115.push();
      if (isDirty_pushFlowFunction_115) {
        binaryMapToRefFlowFunction_116.input2Updated(pushFlowFunction_115);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_116()) {
      auditInvocation(
          binaryMapToRefFlowFunction_116, "binaryMapToRefFlowFunction_116", "map", typedEvent);
      binaryMapToRefFlowFunction_116.map();
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
        mapRef2RefFlowFunction_42.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_65.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_67.inputUpdated(filterFlowFunction_8);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      auditInvocation(mapRef2RefFlowFunction_10, "mapRef2RefFlowFunction_10", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_23.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_23.input2Updated(mapRef2RefFlowFunction_12);
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
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_90.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_90.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_90()) {
      auditInvocation(
          binaryMapToRefFlowFunction_90, "binaryMapToRefFlowFunction_90", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_90 = binaryMapToRefFlowFunction_90.map();
      if (isDirty_binaryMapToRefFlowFunction_90) {
        mapRef2RefFlowFunction_92.inputUpdated(binaryMapToRefFlowFunction_90);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_25()) {
      auditInvocation(mapRef2RefFlowFunction_25, "mapRef2RefFlowFunction_25", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_25 = mapRef2RefFlowFunction_25.map();
      if (isDirty_mapRef2RefFlowFunction_25) {
        mapRef2RefFlowFunction_27.inputUpdated(mapRef2RefFlowFunction_25);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_27()) {
      auditInvocation(mapRef2RefFlowFunction_27, "mapRef2RefFlowFunction_27", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_27 = mapRef2RefFlowFunction_27.map();
      if (isDirty_mapRef2RefFlowFunction_27) {
        binaryMapToRefFlowFunction_29.inputUpdated(mapRef2RefFlowFunction_27);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_29()) {
      auditInvocation(
          binaryMapToRefFlowFunction_29, "binaryMapToRefFlowFunction_29", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_29 = binaryMapToRefFlowFunction_29.map();
      if (isDirty_binaryMapToRefFlowFunction_29) {
        mapRef2RefFlowFunction_31.inputUpdated(binaryMapToRefFlowFunction_29);
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
        mapRef2RefFlowFunction_37.inputUpdated(mapRef2RefFlowFunction_35);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_37()) {
      auditInvocation(mapRef2RefFlowFunction_37, "mapRef2RefFlowFunction_37", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_37 = mapRef2RefFlowFunction_37.map();
      if (isDirty_mapRef2RefFlowFunction_37) {
        binaryMapToRefFlowFunction_58.inputUpdated(mapRef2RefFlowFunction_37);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_42()) {
      auditInvocation(mapRef2RefFlowFunction_42, "mapRef2RefFlowFunction_42", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
      if (isDirty_mapRef2RefFlowFunction_42) {
        mapRef2RefFlowFunction_46.inputUpdated(mapRef2RefFlowFunction_42);
        peekFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_42);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      auditInvocation(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        binaryMapToRefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_48()) {
      auditInvocation(
          binaryMapToRefFlowFunction_48, "binaryMapToRefFlowFunction_48", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_48 = binaryMapToRefFlowFunction_48.map();
      if (isDirty_binaryMapToRefFlowFunction_48) {
        mapRef2RefFlowFunction_50.inputUpdated(binaryMapToRefFlowFunction_48);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_50()) {
      auditInvocation(mapRef2RefFlowFunction_50, "mapRef2RefFlowFunction_50", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
      if (isDirty_mapRef2RefFlowFunction_50) {
        mapRef2RefFlowFunction_54.inputUpdated(mapRef2RefFlowFunction_50);
        peekFlowFunction_52.inputUpdated(mapRef2RefFlowFunction_50);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      auditInvocation(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        mapRef2RefFlowFunction_56.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_56()) {
      auditInvocation(mapRef2RefFlowFunction_56, "mapRef2RefFlowFunction_56", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_56 = mapRef2RefFlowFunction_56.map();
      if (isDirty_mapRef2RefFlowFunction_56) {
        binaryMapToRefFlowFunction_58.input2Updated(mapRef2RefFlowFunction_56);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_58()) {
      auditInvocation(
          binaryMapToRefFlowFunction_58, "binaryMapToRefFlowFunction_58", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_58 = binaryMapToRefFlowFunction_58.map();
      if (isDirty_binaryMapToRefFlowFunction_58) {
        mapRef2RefFlowFunction_60.inputUpdated(binaryMapToRefFlowFunction_58);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_60()) {
      auditInvocation(mapRef2RefFlowFunction_60, "mapRef2RefFlowFunction_60", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_60 = mapRef2RefFlowFunction_60.map();
      if (isDirty_mapRef2RefFlowFunction_60) {
        mapRef2RefFlowFunction_61.inputUpdated(mapRef2RefFlowFunction_60);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_61()) {
      auditInvocation(mapRef2RefFlowFunction_61, "mapRef2RefFlowFunction_61", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_61 = mapRef2RefFlowFunction_61.map();
      if (isDirty_mapRef2RefFlowFunction_61) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_61);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "map", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_63.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_65()) {
      auditInvocation(mapRef2RefFlowFunction_65, "mapRef2RefFlowFunction_65", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_65 = mapRef2RefFlowFunction_65.map();
      if (isDirty_mapRef2RefFlowFunction_65) {
        binaryMapToRefFlowFunction_69.inputUpdated(mapRef2RefFlowFunction_65);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_67()) {
      auditInvocation(mapRef2RefFlowFunction_67, "mapRef2RefFlowFunction_67", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_67 = mapRef2RefFlowFunction_67.map();
      if (isDirty_mapRef2RefFlowFunction_67) {
        binaryMapToRefFlowFunction_69.input2Updated(mapRef2RefFlowFunction_67);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_69()) {
      auditInvocation(
          binaryMapToRefFlowFunction_69, "binaryMapToRefFlowFunction_69", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_69 = binaryMapToRefFlowFunction_69.map();
      if (isDirty_binaryMapToRefFlowFunction_69) {
        mapRef2RefFlowFunction_71.inputUpdated(binaryMapToRefFlowFunction_69);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_71()) {
      auditInvocation(mapRef2RefFlowFunction_71, "mapRef2RefFlowFunction_71", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_71 = mapRef2RefFlowFunction_71.map();
      if (isDirty_mapRef2RefFlowFunction_71) {
        mapRef2RefFlowFunction_73.inputUpdated(mapRef2RefFlowFunction_71);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_73()) {
      auditInvocation(mapRef2RefFlowFunction_73, "mapRef2RefFlowFunction_73", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_73 = mapRef2RefFlowFunction_73.map();
      if (isDirty_mapRef2RefFlowFunction_73) {
        binaryMapToRefFlowFunction_80.inputUpdated(mapRef2RefFlowFunction_73);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_80()) {
      auditInvocation(
          binaryMapToRefFlowFunction_80, "binaryMapToRefFlowFunction_80", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_80 = binaryMapToRefFlowFunction_80.map();
      if (isDirty_binaryMapToRefFlowFunction_80) {
        mapRef2RefFlowFunction_82.inputUpdated(binaryMapToRefFlowFunction_80);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_82()) {
      auditInvocation(mapRef2RefFlowFunction_82, "mapRef2RefFlowFunction_82", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_82 = mapRef2RefFlowFunction_82.map();
      if (isDirty_mapRef2RefFlowFunction_82) {
        mapRef2RefFlowFunction_86.inputUpdated(mapRef2RefFlowFunction_82);
        peekFlowFunction_84.inputUpdated(mapRef2RefFlowFunction_82);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_86()) {
      auditInvocation(mapRef2RefFlowFunction_86, "mapRef2RefFlowFunction_86", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_86 = mapRef2RefFlowFunction_86.map();
      if (isDirty_mapRef2RefFlowFunction_86) {
        mapRef2RefFlowFunction_88.inputUpdated(mapRef2RefFlowFunction_86);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_88()) {
      auditInvocation(mapRef2RefFlowFunction_88, "mapRef2RefFlowFunction_88", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_88 = mapRef2RefFlowFunction_88.map();
      if (isDirty_mapRef2RefFlowFunction_88) {
        binaryMapToRefFlowFunction_111.input2Updated(mapRef2RefFlowFunction_88);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_92()) {
      auditInvocation(mapRef2RefFlowFunction_92, "mapRef2RefFlowFunction_92", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_92 = mapRef2RefFlowFunction_92.map();
      if (isDirty_mapRef2RefFlowFunction_92) {
        mapRef2RefFlowFunction_94.inputUpdated(mapRef2RefFlowFunction_92);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_94()) {
      auditInvocation(mapRef2RefFlowFunction_94, "mapRef2RefFlowFunction_94", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_94 = mapRef2RefFlowFunction_94.map();
      if (isDirty_mapRef2RefFlowFunction_94) {
        binaryMapToRefFlowFunction_101.inputUpdated(mapRef2RefFlowFunction_94);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_101()) {
      auditInvocation(
          binaryMapToRefFlowFunction_101, "binaryMapToRefFlowFunction_101", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_101 = binaryMapToRefFlowFunction_101.map();
      if (isDirty_binaryMapToRefFlowFunction_101) {
        mapRef2RefFlowFunction_103.inputUpdated(binaryMapToRefFlowFunction_101);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_103()) {
      auditInvocation(mapRef2RefFlowFunction_103, "mapRef2RefFlowFunction_103", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_103 = mapRef2RefFlowFunction_103.map();
      if (isDirty_mapRef2RefFlowFunction_103) {
        mapRef2RefFlowFunction_105.inputUpdated(mapRef2RefFlowFunction_103);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_105()) {
      auditInvocation(mapRef2RefFlowFunction_105, "mapRef2RefFlowFunction_105", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_105 = mapRef2RefFlowFunction_105.map();
      if (isDirty_mapRef2RefFlowFunction_105) {
        mapRef2RefFlowFunction_107.inputUpdated(mapRef2RefFlowFunction_105);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_107()) {
      auditInvocation(mapRef2RefFlowFunction_107, "mapRef2RefFlowFunction_107", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_107 = mapRef2RefFlowFunction_107.map();
      if (isDirty_mapRef2RefFlowFunction_107) {
        mapRef2RefFlowFunction_109.inputUpdated(mapRef2RefFlowFunction_107);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_109()) {
      auditInvocation(mapRef2RefFlowFunction_109, "mapRef2RefFlowFunction_109", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_109 = mapRef2RefFlowFunction_109.map();
      if (isDirty_mapRef2RefFlowFunction_109) {
        binaryMapToRefFlowFunction_111.inputUpdated(mapRef2RefFlowFunction_109);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_111()) {
      auditInvocation(
          binaryMapToRefFlowFunction_111, "binaryMapToRefFlowFunction_111", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_111 = binaryMapToRefFlowFunction_111.map();
      if (isDirty_binaryMapToRefFlowFunction_111) {
        mapRef2RefFlowFunction_113.inputUpdated(binaryMapToRefFlowFunction_111);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_113()) {
      auditInvocation(mapRef2RefFlowFunction_113, "mapRef2RefFlowFunction_113", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_113 = mapRef2RefFlowFunction_113.map();
      if (isDirty_mapRef2RefFlowFunction_113) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_113);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_115.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_peekFlowFunction_44()) {
      auditInvocation(peekFlowFunction_44, "peekFlowFunction_44", "peek", typedEvent);
      peekFlowFunction_44.peek();
    }
    if (guardCheck_peekFlowFunction_52()) {
      auditInvocation(peekFlowFunction_52, "peekFlowFunction_52", "peek", typedEvent);
      peekFlowFunction_52.peek();
    }
    if (guardCheck_peekFlowFunction_84()) {
      auditInvocation(peekFlowFunction_84, "peekFlowFunction_84", "peek", typedEvent);
      peekFlowFunction_84.peek();
    }
    if (guardCheck_pushFlowFunction_63()) {
      auditInvocation(pushFlowFunction_63, "pushFlowFunction_63", "push", typedEvent);
      isDirty_pushFlowFunction_63 = pushFlowFunction_63.push();
      if (isDirty_pushFlowFunction_63) {
        binaryMapToRefFlowFunction_116.inputUpdated(pushFlowFunction_63);
      }
    }
    if (guardCheck_pushFlowFunction_115()) {
      auditInvocation(pushFlowFunction_115, "pushFlowFunction_115", "push", typedEvent);
      isDirty_pushFlowFunction_115 = pushFlowFunction_115.push();
      if (isDirty_pushFlowFunction_115) {
        binaryMapToRefFlowFunction_116.input2Updated(pushFlowFunction_115);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_116()) {
      auditInvocation(
          binaryMapToRefFlowFunction_116, "binaryMapToRefFlowFunction_116", "map", typedEvent);
      binaryMapToRefFlowFunction_116.map();
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
        mapRef2RefFlowFunction_42.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_65.inputUpdated(filterFlowFunction_8);
        mapRef2RefFlowFunction_67.inputUpdated(filterFlowFunction_8);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_10()) {
      auditInvocation(mapRef2RefFlowFunction_10, "mapRef2RefFlowFunction_10", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_10 = mapRef2RefFlowFunction_10.map();
      if (isDirty_mapRef2RefFlowFunction_10) {
        binaryMapToRefFlowFunction_23.inputUpdated(mapRef2RefFlowFunction_10);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_12()) {
      auditInvocation(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
      if (isDirty_mapRef2RefFlowFunction_12) {
        binaryMapToRefFlowFunction_23.input2Updated(mapRef2RefFlowFunction_12);
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
    if (guardCheck_mapRef2RefFlowFunction_14()) {
      auditInvocation(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
      if (isDirty_mapRef2RefFlowFunction_14) {
        binaryMapToRefFlowFunction_90.inputUpdated(mapRef2RefFlowFunction_14);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_16()) {
      auditInvocation(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
      if (isDirty_mapRef2RefFlowFunction_16) {
        binaryMapToRefFlowFunction_90.input2Updated(mapRef2RefFlowFunction_16);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_90()) {
      auditInvocation(
          binaryMapToRefFlowFunction_90, "binaryMapToRefFlowFunction_90", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_90 = binaryMapToRefFlowFunction_90.map();
      if (isDirty_binaryMapToRefFlowFunction_90) {
        mapRef2RefFlowFunction_92.inputUpdated(binaryMapToRefFlowFunction_90);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_25()) {
      auditInvocation(mapRef2RefFlowFunction_25, "mapRef2RefFlowFunction_25", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_25 = mapRef2RefFlowFunction_25.map();
      if (isDirty_mapRef2RefFlowFunction_25) {
        mapRef2RefFlowFunction_27.inputUpdated(mapRef2RefFlowFunction_25);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_27()) {
      auditInvocation(mapRef2RefFlowFunction_27, "mapRef2RefFlowFunction_27", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_27 = mapRef2RefFlowFunction_27.map();
      if (isDirty_mapRef2RefFlowFunction_27) {
        binaryMapToRefFlowFunction_29.inputUpdated(mapRef2RefFlowFunction_27);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_29()) {
      auditInvocation(
          binaryMapToRefFlowFunction_29, "binaryMapToRefFlowFunction_29", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_29 = binaryMapToRefFlowFunction_29.map();
      if (isDirty_binaryMapToRefFlowFunction_29) {
        mapRef2RefFlowFunction_31.inputUpdated(binaryMapToRefFlowFunction_29);
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
        mapRef2RefFlowFunction_37.inputUpdated(mapRef2RefFlowFunction_35);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_37()) {
      auditInvocation(mapRef2RefFlowFunction_37, "mapRef2RefFlowFunction_37", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_37 = mapRef2RefFlowFunction_37.map();
      if (isDirty_mapRef2RefFlowFunction_37) {
        binaryMapToRefFlowFunction_58.inputUpdated(mapRef2RefFlowFunction_37);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_42()) {
      auditInvocation(mapRef2RefFlowFunction_42, "mapRef2RefFlowFunction_42", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
      if (isDirty_mapRef2RefFlowFunction_42) {
        mapRef2RefFlowFunction_46.inputUpdated(mapRef2RefFlowFunction_42);
        peekFlowFunction_44.inputUpdated(mapRef2RefFlowFunction_42);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_46()) {
      auditInvocation(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_46 = mapRef2RefFlowFunction_46.map();
      if (isDirty_mapRef2RefFlowFunction_46) {
        binaryMapToRefFlowFunction_48.inputUpdated(mapRef2RefFlowFunction_46);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_48()) {
      auditInvocation(
          binaryMapToRefFlowFunction_48, "binaryMapToRefFlowFunction_48", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_48 = binaryMapToRefFlowFunction_48.map();
      if (isDirty_binaryMapToRefFlowFunction_48) {
        mapRef2RefFlowFunction_50.inputUpdated(binaryMapToRefFlowFunction_48);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_50()) {
      auditInvocation(mapRef2RefFlowFunction_50, "mapRef2RefFlowFunction_50", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_50 = mapRef2RefFlowFunction_50.map();
      if (isDirty_mapRef2RefFlowFunction_50) {
        mapRef2RefFlowFunction_54.inputUpdated(mapRef2RefFlowFunction_50);
        peekFlowFunction_52.inputUpdated(mapRef2RefFlowFunction_50);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_54()) {
      auditInvocation(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_54 = mapRef2RefFlowFunction_54.map();
      if (isDirty_mapRef2RefFlowFunction_54) {
        mapRef2RefFlowFunction_56.inputUpdated(mapRef2RefFlowFunction_54);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_56()) {
      auditInvocation(mapRef2RefFlowFunction_56, "mapRef2RefFlowFunction_56", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_56 = mapRef2RefFlowFunction_56.map();
      if (isDirty_mapRef2RefFlowFunction_56) {
        binaryMapToRefFlowFunction_58.input2Updated(mapRef2RefFlowFunction_56);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_58()) {
      auditInvocation(
          binaryMapToRefFlowFunction_58, "binaryMapToRefFlowFunction_58", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_58 = binaryMapToRefFlowFunction_58.map();
      if (isDirty_binaryMapToRefFlowFunction_58) {
        mapRef2RefFlowFunction_60.inputUpdated(binaryMapToRefFlowFunction_58);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_60()) {
      auditInvocation(mapRef2RefFlowFunction_60, "mapRef2RefFlowFunction_60", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_60 = mapRef2RefFlowFunction_60.map();
      if (isDirty_mapRef2RefFlowFunction_60) {
        mapRef2RefFlowFunction_61.inputUpdated(mapRef2RefFlowFunction_60);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_61()) {
      auditInvocation(mapRef2RefFlowFunction_61, "mapRef2RefFlowFunction_61", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_61 = mapRef2RefFlowFunction_61.map();
      if (isDirty_mapRef2RefFlowFunction_61) {
        globalNetMtm.inputUpdated(mapRef2RefFlowFunction_61);
      }
    }
    if (guardCheck_globalNetMtm()) {
      auditInvocation(globalNetMtm, "globalNetMtm", "map", typedEvent);
      isDirty_globalNetMtm = globalNetMtm.map();
      if (isDirty_globalNetMtm) {
        pushFlowFunction_63.inputUpdated(globalNetMtm);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_65()) {
      auditInvocation(mapRef2RefFlowFunction_65, "mapRef2RefFlowFunction_65", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_65 = mapRef2RefFlowFunction_65.map();
      if (isDirty_mapRef2RefFlowFunction_65) {
        binaryMapToRefFlowFunction_69.inputUpdated(mapRef2RefFlowFunction_65);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_67()) {
      auditInvocation(mapRef2RefFlowFunction_67, "mapRef2RefFlowFunction_67", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_67 = mapRef2RefFlowFunction_67.map();
      if (isDirty_mapRef2RefFlowFunction_67) {
        binaryMapToRefFlowFunction_69.input2Updated(mapRef2RefFlowFunction_67);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_69()) {
      auditInvocation(
          binaryMapToRefFlowFunction_69, "binaryMapToRefFlowFunction_69", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_69 = binaryMapToRefFlowFunction_69.map();
      if (isDirty_binaryMapToRefFlowFunction_69) {
        mapRef2RefFlowFunction_71.inputUpdated(binaryMapToRefFlowFunction_69);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_71()) {
      auditInvocation(mapRef2RefFlowFunction_71, "mapRef2RefFlowFunction_71", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_71 = mapRef2RefFlowFunction_71.map();
      if (isDirty_mapRef2RefFlowFunction_71) {
        mapRef2RefFlowFunction_73.inputUpdated(mapRef2RefFlowFunction_71);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_73()) {
      auditInvocation(mapRef2RefFlowFunction_73, "mapRef2RefFlowFunction_73", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_73 = mapRef2RefFlowFunction_73.map();
      if (isDirty_mapRef2RefFlowFunction_73) {
        binaryMapToRefFlowFunction_80.inputUpdated(mapRef2RefFlowFunction_73);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_80()) {
      auditInvocation(
          binaryMapToRefFlowFunction_80, "binaryMapToRefFlowFunction_80", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_80 = binaryMapToRefFlowFunction_80.map();
      if (isDirty_binaryMapToRefFlowFunction_80) {
        mapRef2RefFlowFunction_82.inputUpdated(binaryMapToRefFlowFunction_80);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_82()) {
      auditInvocation(mapRef2RefFlowFunction_82, "mapRef2RefFlowFunction_82", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_82 = mapRef2RefFlowFunction_82.map();
      if (isDirty_mapRef2RefFlowFunction_82) {
        mapRef2RefFlowFunction_86.inputUpdated(mapRef2RefFlowFunction_82);
        peekFlowFunction_84.inputUpdated(mapRef2RefFlowFunction_82);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_86()) {
      auditInvocation(mapRef2RefFlowFunction_86, "mapRef2RefFlowFunction_86", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_86 = mapRef2RefFlowFunction_86.map();
      if (isDirty_mapRef2RefFlowFunction_86) {
        mapRef2RefFlowFunction_88.inputUpdated(mapRef2RefFlowFunction_86);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_88()) {
      auditInvocation(mapRef2RefFlowFunction_88, "mapRef2RefFlowFunction_88", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_88 = mapRef2RefFlowFunction_88.map();
      if (isDirty_mapRef2RefFlowFunction_88) {
        binaryMapToRefFlowFunction_111.input2Updated(mapRef2RefFlowFunction_88);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_92()) {
      auditInvocation(mapRef2RefFlowFunction_92, "mapRef2RefFlowFunction_92", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_92 = mapRef2RefFlowFunction_92.map();
      if (isDirty_mapRef2RefFlowFunction_92) {
        mapRef2RefFlowFunction_94.inputUpdated(mapRef2RefFlowFunction_92);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_94()) {
      auditInvocation(mapRef2RefFlowFunction_94, "mapRef2RefFlowFunction_94", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_94 = mapRef2RefFlowFunction_94.map();
      if (isDirty_mapRef2RefFlowFunction_94) {
        binaryMapToRefFlowFunction_101.inputUpdated(mapRef2RefFlowFunction_94);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_101()) {
      auditInvocation(
          binaryMapToRefFlowFunction_101, "binaryMapToRefFlowFunction_101", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_101 = binaryMapToRefFlowFunction_101.map();
      if (isDirty_binaryMapToRefFlowFunction_101) {
        mapRef2RefFlowFunction_103.inputUpdated(binaryMapToRefFlowFunction_101);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_103()) {
      auditInvocation(mapRef2RefFlowFunction_103, "mapRef2RefFlowFunction_103", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_103 = mapRef2RefFlowFunction_103.map();
      if (isDirty_mapRef2RefFlowFunction_103) {
        mapRef2RefFlowFunction_105.inputUpdated(mapRef2RefFlowFunction_103);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_105()) {
      auditInvocation(mapRef2RefFlowFunction_105, "mapRef2RefFlowFunction_105", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_105 = mapRef2RefFlowFunction_105.map();
      if (isDirty_mapRef2RefFlowFunction_105) {
        mapRef2RefFlowFunction_107.inputUpdated(mapRef2RefFlowFunction_105);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_107()) {
      auditInvocation(mapRef2RefFlowFunction_107, "mapRef2RefFlowFunction_107", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_107 = mapRef2RefFlowFunction_107.map();
      if (isDirty_mapRef2RefFlowFunction_107) {
        mapRef2RefFlowFunction_109.inputUpdated(mapRef2RefFlowFunction_107);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_109()) {
      auditInvocation(mapRef2RefFlowFunction_109, "mapRef2RefFlowFunction_109", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_109 = mapRef2RefFlowFunction_109.map();
      if (isDirty_mapRef2RefFlowFunction_109) {
        binaryMapToRefFlowFunction_111.inputUpdated(mapRef2RefFlowFunction_109);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_111()) {
      auditInvocation(
          binaryMapToRefFlowFunction_111, "binaryMapToRefFlowFunction_111", "map", typedEvent);
      isDirty_binaryMapToRefFlowFunction_111 = binaryMapToRefFlowFunction_111.map();
      if (isDirty_binaryMapToRefFlowFunction_111) {
        mapRef2RefFlowFunction_113.inputUpdated(binaryMapToRefFlowFunction_111);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_113()) {
      auditInvocation(mapRef2RefFlowFunction_113, "mapRef2RefFlowFunction_113", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_113 = mapRef2RefFlowFunction_113.map();
      if (isDirty_mapRef2RefFlowFunction_113) {
        instrumentNetMtm.inputUpdated(mapRef2RefFlowFunction_113);
      }
    }
    if (guardCheck_instrumentNetMtm()) {
      auditInvocation(instrumentNetMtm, "instrumentNetMtm", "map", typedEvent);
      isDirty_instrumentNetMtm = instrumentNetMtm.map();
      if (isDirty_instrumentNetMtm) {
        pushFlowFunction_115.inputUpdated(instrumentNetMtm);
      }
    }
    if (guardCheck_peekFlowFunction_44()) {
      auditInvocation(peekFlowFunction_44, "peekFlowFunction_44", "peek", typedEvent);
      peekFlowFunction_44.peek();
    }
    if (guardCheck_peekFlowFunction_52()) {
      auditInvocation(peekFlowFunction_52, "peekFlowFunction_52", "peek", typedEvent);
      peekFlowFunction_52.peek();
    }
    if (guardCheck_peekFlowFunction_84()) {
      auditInvocation(peekFlowFunction_84, "peekFlowFunction_84", "peek", typedEvent);
      peekFlowFunction_84.peek();
    }
    if (guardCheck_pushFlowFunction_63()) {
      auditInvocation(pushFlowFunction_63, "pushFlowFunction_63", "push", typedEvent);
      isDirty_pushFlowFunction_63 = pushFlowFunction_63.push();
      if (isDirty_pushFlowFunction_63) {
        binaryMapToRefFlowFunction_116.inputUpdated(pushFlowFunction_63);
      }
    }
    if (guardCheck_pushFlowFunction_115()) {
      auditInvocation(pushFlowFunction_115, "pushFlowFunction_115", "push", typedEvent);
      isDirty_pushFlowFunction_115 = pushFlowFunction_115.push();
      if (isDirty_pushFlowFunction_115) {
        binaryMapToRefFlowFunction_116.input2Updated(pushFlowFunction_115);
      }
    }
    if (guardCheck_binaryMapToRefFlowFunction_116()) {
      auditInvocation(
          binaryMapToRefFlowFunction_116, "binaryMapToRefFlowFunction_116", "map", typedEvent);
      binaryMapToRefFlowFunction_116.map();
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
    auditor.nodeRegistered(callBackNode_118, "callBackNode_118");
    auditor.nodeRegistered(callBackNode_142, "callBackNode_142");
    auditor.nodeRegistered(callBackNode_270, "callBackNode_270");
    auditor.nodeRegistered(callbackDispatcher, "callbackDispatcher");
    auditor.nodeRegistered(callBackTriggerEvent_1, "callBackTriggerEvent_1");
    auditor.nodeRegistered(callBackTriggerEvent_2, "callBackTriggerEvent_2");
    auditor.nodeRegistered(callBackTriggerEvent_0, "callBackTriggerEvent_0");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_23, "binaryMapToRefFlowFunction_23");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_29, "binaryMapToRefFlowFunction_29");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_48, "binaryMapToRefFlowFunction_48");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_58, "binaryMapToRefFlowFunction_58");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_69, "binaryMapToRefFlowFunction_69");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_80, "binaryMapToRefFlowFunction_80");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_90, "binaryMapToRefFlowFunction_90");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_101, "binaryMapToRefFlowFunction_101");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_111, "binaryMapToRefFlowFunction_111");
    auditor.nodeRegistered(binaryMapToRefFlowFunction_116, "binaryMapToRefFlowFunction_116");
    auditor.nodeRegistered(filterFlowFunction_8, "filterFlowFunction_8");
    auditor.nodeRegistered(flatMapFlowFunction_3, "flatMapFlowFunction_3");
    auditor.nodeRegistered(flatMapFlowFunction_17, "flatMapFlowFunction_17");
    auditor.nodeRegistered(flatMapFlowFunction_38, "flatMapFlowFunction_38");
    auditor.nodeRegistered(globalNetMtm, "globalNetMtm");
    auditor.nodeRegistered(instrumentNetMtm, "instrumentNetMtm");
    auditor.nodeRegistered(mapRef2RefFlowFunction_4, "mapRef2RefFlowFunction_4");
    auditor.nodeRegistered(mapRef2RefFlowFunction_5, "mapRef2RefFlowFunction_5");
    auditor.nodeRegistered(mapRef2RefFlowFunction_10, "mapRef2RefFlowFunction_10");
    auditor.nodeRegistered(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12");
    auditor.nodeRegistered(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14");
    auditor.nodeRegistered(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16");
    auditor.nodeRegistered(mapRef2RefFlowFunction_19, "mapRef2RefFlowFunction_19");
    auditor.nodeRegistered(mapRef2RefFlowFunction_25, "mapRef2RefFlowFunction_25");
    auditor.nodeRegistered(mapRef2RefFlowFunction_27, "mapRef2RefFlowFunction_27");
    auditor.nodeRegistered(mapRef2RefFlowFunction_31, "mapRef2RefFlowFunction_31");
    auditor.nodeRegistered(mapRef2RefFlowFunction_33, "mapRef2RefFlowFunction_33");
    auditor.nodeRegistered(mapRef2RefFlowFunction_35, "mapRef2RefFlowFunction_35");
    auditor.nodeRegistered(mapRef2RefFlowFunction_37, "mapRef2RefFlowFunction_37");
    auditor.nodeRegistered(mapRef2RefFlowFunction_40, "mapRef2RefFlowFunction_40");
    auditor.nodeRegistered(mapRef2RefFlowFunction_42, "mapRef2RefFlowFunction_42");
    auditor.nodeRegistered(mapRef2RefFlowFunction_46, "mapRef2RefFlowFunction_46");
    auditor.nodeRegistered(mapRef2RefFlowFunction_50, "mapRef2RefFlowFunction_50");
    auditor.nodeRegistered(mapRef2RefFlowFunction_54, "mapRef2RefFlowFunction_54");
    auditor.nodeRegistered(mapRef2RefFlowFunction_56, "mapRef2RefFlowFunction_56");
    auditor.nodeRegistered(mapRef2RefFlowFunction_60, "mapRef2RefFlowFunction_60");
    auditor.nodeRegistered(mapRef2RefFlowFunction_61, "mapRef2RefFlowFunction_61");
    auditor.nodeRegistered(mapRef2RefFlowFunction_65, "mapRef2RefFlowFunction_65");
    auditor.nodeRegistered(mapRef2RefFlowFunction_67, "mapRef2RefFlowFunction_67");
    auditor.nodeRegistered(mapRef2RefFlowFunction_71, "mapRef2RefFlowFunction_71");
    auditor.nodeRegistered(mapRef2RefFlowFunction_73, "mapRef2RefFlowFunction_73");
    auditor.nodeRegistered(mapRef2RefFlowFunction_74, "mapRef2RefFlowFunction_74");
    auditor.nodeRegistered(mapRef2RefFlowFunction_76, "mapRef2RefFlowFunction_76");
    auditor.nodeRegistered(mapRef2RefFlowFunction_78, "mapRef2RefFlowFunction_78");
    auditor.nodeRegistered(mapRef2RefFlowFunction_82, "mapRef2RefFlowFunction_82");
    auditor.nodeRegistered(mapRef2RefFlowFunction_86, "mapRef2RefFlowFunction_86");
    auditor.nodeRegistered(mapRef2RefFlowFunction_88, "mapRef2RefFlowFunction_88");
    auditor.nodeRegistered(mapRef2RefFlowFunction_92, "mapRef2RefFlowFunction_92");
    auditor.nodeRegistered(mapRef2RefFlowFunction_94, "mapRef2RefFlowFunction_94");
    auditor.nodeRegistered(mapRef2RefFlowFunction_95, "mapRef2RefFlowFunction_95");
    auditor.nodeRegistered(mapRef2RefFlowFunction_97, "mapRef2RefFlowFunction_97");
    auditor.nodeRegistered(mapRef2RefFlowFunction_99, "mapRef2RefFlowFunction_99");
    auditor.nodeRegistered(mapRef2RefFlowFunction_103, "mapRef2RefFlowFunction_103");
    auditor.nodeRegistered(mapRef2RefFlowFunction_105, "mapRef2RefFlowFunction_105");
    auditor.nodeRegistered(mapRef2RefFlowFunction_107, "mapRef2RefFlowFunction_107");
    auditor.nodeRegistered(mapRef2RefFlowFunction_109, "mapRef2RefFlowFunction_109");
    auditor.nodeRegistered(mapRef2RefFlowFunction_113, "mapRef2RefFlowFunction_113");
    auditor.nodeRegistered(mergeFlowFunction_6, "mergeFlowFunction_6");
    auditor.nodeRegistered(peekFlowFunction_21, "peekFlowFunction_21");
    auditor.nodeRegistered(peekFlowFunction_44, "peekFlowFunction_44");
    auditor.nodeRegistered(peekFlowFunction_52, "peekFlowFunction_52");
    auditor.nodeRegistered(peekFlowFunction_84, "peekFlowFunction_84");
    auditor.nodeRegistered(pushFlowFunction_63, "pushFlowFunction_63");
    auditor.nodeRegistered(pushFlowFunction_115, "pushFlowFunction_115");
    auditor.nodeRegistered(emptyGroupBy_167, "emptyGroupBy_167");
    auditor.nodeRegistered(emptyGroupBy_211, "emptyGroupBy_211");
    auditor.nodeRegistered(emptyGroupBy_249, "emptyGroupBy_249");
    auditor.nodeRegistered(emptyGroupBy_281, "emptyGroupBy_281");
    auditor.nodeRegistered(emptyGroupBy_314, "emptyGroupBy_314");
    auditor.nodeRegistered(emptyGroupBy_541, "emptyGroupBy_541");
    auditor.nodeRegistered(emptyGroupBy_555, "emptyGroupBy_555");
    auditor.nodeRegistered(emptyGroupBy_609, "emptyGroupBy_609");
    auditor.nodeRegistered(emptyGroupBy_670, "emptyGroupBy_670");
    auditor.nodeRegistered(emptyGroupBy_684, "emptyGroupBy_684");
    auditor.nodeRegistered(emptyGroupBy_740, "emptyGroupBy_740");
    auditor.nodeRegistered(emptyGroupBy_761, "emptyGroupBy_761");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_9, "groupByFlowFunctionWrapper_9");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_11, "groupByFlowFunctionWrapper_11");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_13, "groupByFlowFunctionWrapper_13");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_15, "groupByFlowFunctionWrapper_15");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_18, "groupByFlowFunctionWrapper_18");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_39, "groupByFlowFunctionWrapper_39");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_41, "groupByFlowFunctionWrapper_41");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_64, "groupByFlowFunctionWrapper_64");
    auditor.nodeRegistered(groupByFlowFunctionWrapper_66, "groupByFlowFunctionWrapper_66");
    auditor.nodeRegistered(groupByHashMap_75, "groupByHashMap_75");
    auditor.nodeRegistered(groupByHashMap_96, "groupByHashMap_96");
    auditor.nodeRegistered(groupByMapFlowFunction_24, "groupByMapFlowFunction_24");
    auditor.nodeRegistered(groupByMapFlowFunction_30, "groupByMapFlowFunction_30");
    auditor.nodeRegistered(groupByMapFlowFunction_34, "groupByMapFlowFunction_34");
    auditor.nodeRegistered(groupByMapFlowFunction_49, "groupByMapFlowFunction_49");
    auditor.nodeRegistered(groupByMapFlowFunction_55, "groupByMapFlowFunction_55");
    auditor.nodeRegistered(groupByMapFlowFunction_59, "groupByMapFlowFunction_59");
    auditor.nodeRegistered(groupByMapFlowFunction_70, "groupByMapFlowFunction_70");
    auditor.nodeRegistered(groupByMapFlowFunction_81, "groupByMapFlowFunction_81");
    auditor.nodeRegistered(groupByMapFlowFunction_87, "groupByMapFlowFunction_87");
    auditor.nodeRegistered(groupByMapFlowFunction_91, "groupByMapFlowFunction_91");
    auditor.nodeRegistered(groupByMapFlowFunction_102, "groupByMapFlowFunction_102");
    auditor.nodeRegistered(groupByMapFlowFunction_104, "groupByMapFlowFunction_104");
    auditor.nodeRegistered(groupByMapFlowFunction_112, "groupByMapFlowFunction_112");
    auditor.nodeRegistered(leftJoin_57, "leftJoin_57");
    auditor.nodeRegistered(leftJoin_110, "leftJoin_110");
    auditor.nodeRegistered(outerJoin_22, "outerJoin_22");
    auditor.nodeRegistered(outerJoin_28, "outerJoin_28");
    auditor.nodeRegistered(outerJoin_47, "outerJoin_47");
    auditor.nodeRegistered(outerJoin_68, "outerJoin_68");
    auditor.nodeRegistered(outerJoin_79, "outerJoin_79");
    auditor.nodeRegistered(outerJoin_89, "outerJoin_89");
    auditor.nodeRegistered(outerJoin_100, "outerJoin_100");
    auditor.nodeRegistered(defaultValue_26, "defaultValue_26");
    auditor.nodeRegistered(defaultValue_32, "defaultValue_32");
    auditor.nodeRegistered(defaultValue_36, "defaultValue_36");
    auditor.nodeRegistered(defaultValue_45, "defaultValue_45");
    auditor.nodeRegistered(defaultValue_53, "defaultValue_53");
    auditor.nodeRegistered(defaultValue_72, "defaultValue_72");
    auditor.nodeRegistered(defaultValue_77, "defaultValue_77");
    auditor.nodeRegistered(defaultValue_85, "defaultValue_85");
    auditor.nodeRegistered(defaultValue_93, "defaultValue_93");
    auditor.nodeRegistered(defaultValue_98, "defaultValue_98");
    auditor.nodeRegistered(defaultValue_106, "defaultValue_106");
    auditor.nodeRegistered(defaultValue_108, "defaultValue_108");
    auditor.nodeRegistered(templateMessage_20, "templateMessage_20");
    auditor.nodeRegistered(templateMessage_43, "templateMessage_43");
    auditor.nodeRegistered(templateMessage_51, "templateMessage_51");
    auditor.nodeRegistered(templateMessage_83, "templateMessage_83");
    auditor.nodeRegistered(mapTuple_960, "mapTuple_960");
    auditor.nodeRegistered(mapTuple_966, "mapTuple_966");
    auditor.nodeRegistered(mapTuple_972, "mapTuple_972");
    auditor.nodeRegistered(mapTuple_987, "mapTuple_987");
    auditor.nodeRegistered(mapTuple_993, "mapTuple_993");
    auditor.nodeRegistered(mapTuple_1004, "mapTuple_1004");
    auditor.nodeRegistered(mapTuple_1010, "mapTuple_1010");
    auditor.nodeRegistered(mapTuple_1023, "mapTuple_1023");
    auditor.nodeRegistered(mapTuple_1028, "mapTuple_1028");
    auditor.nodeRegistered(subscriptionManager, "subscriptionManager");
    auditor.nodeRegistered(handlerPositionSnapshot, "handlerPositionSnapshot");
    auditor.nodeRegistered(
        handlerSignal_positionSnapshotReset, "handlerSignal_positionSnapshotReset");
    auditor.nodeRegistered(handlerSignal_positionUpdate, "handlerSignal_positionUpdate");
    auditor.nodeRegistered(handlerTrade, "handlerTrade");
    auditor.nodeRegistered(handlerTradeBatch, "handlerTradeBatch");
    auditor.nodeRegistered(context, "context");
    auditor.nodeRegistered(namedFeedTableNode_117, "namedFeedTableNode_117");
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
    isDirty_binaryMapToRefFlowFunction_23 = false;
    isDirty_binaryMapToRefFlowFunction_29 = false;
    isDirty_binaryMapToRefFlowFunction_48 = false;
    isDirty_binaryMapToRefFlowFunction_58 = false;
    isDirty_binaryMapToRefFlowFunction_69 = false;
    isDirty_binaryMapToRefFlowFunction_80 = false;
    isDirty_binaryMapToRefFlowFunction_90 = false;
    isDirty_binaryMapToRefFlowFunction_101 = false;
    isDirty_binaryMapToRefFlowFunction_111 = false;
    isDirty_callBackNode_118 = false;
    isDirty_callBackNode_142 = false;
    isDirty_callBackNode_270 = false;
    isDirty_clock = false;
    isDirty_derivedRateNode = false;
    isDirty_eventFeedBatcher = false;
    isDirty_filterFlowFunction_8 = false;
    isDirty_flatMapFlowFunction_3 = false;
    isDirty_flatMapFlowFunction_17 = false;
    isDirty_flatMapFlowFunction_38 = false;
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
    isDirty_mapRef2RefFlowFunction_25 = false;
    isDirty_mapRef2RefFlowFunction_27 = false;
    isDirty_mapRef2RefFlowFunction_31 = false;
    isDirty_mapRef2RefFlowFunction_33 = false;
    isDirty_mapRef2RefFlowFunction_35 = false;
    isDirty_mapRef2RefFlowFunction_37 = false;
    isDirty_mapRef2RefFlowFunction_40 = false;
    isDirty_mapRef2RefFlowFunction_42 = false;
    isDirty_mapRef2RefFlowFunction_46 = false;
    isDirty_mapRef2RefFlowFunction_50 = false;
    isDirty_mapRef2RefFlowFunction_54 = false;
    isDirty_mapRef2RefFlowFunction_56 = false;
    isDirty_mapRef2RefFlowFunction_60 = false;
    isDirty_mapRef2RefFlowFunction_61 = false;
    isDirty_mapRef2RefFlowFunction_65 = false;
    isDirty_mapRef2RefFlowFunction_67 = false;
    isDirty_mapRef2RefFlowFunction_71 = false;
    isDirty_mapRef2RefFlowFunction_73 = false;
    isDirty_mapRef2RefFlowFunction_74 = false;
    isDirty_mapRef2RefFlowFunction_76 = false;
    isDirty_mapRef2RefFlowFunction_78 = false;
    isDirty_mapRef2RefFlowFunction_82 = false;
    isDirty_mapRef2RefFlowFunction_86 = false;
    isDirty_mapRef2RefFlowFunction_88 = false;
    isDirty_mapRef2RefFlowFunction_92 = false;
    isDirty_mapRef2RefFlowFunction_94 = false;
    isDirty_mapRef2RefFlowFunction_95 = false;
    isDirty_mapRef2RefFlowFunction_97 = false;
    isDirty_mapRef2RefFlowFunction_99 = false;
    isDirty_mapRef2RefFlowFunction_103 = false;
    isDirty_mapRef2RefFlowFunction_105 = false;
    isDirty_mapRef2RefFlowFunction_107 = false;
    isDirty_mapRef2RefFlowFunction_109 = false;
    isDirty_mapRef2RefFlowFunction_113 = false;
    isDirty_mergeFlowFunction_6 = false;
    isDirty_namedFeedTableNode_117 = false;
    isDirty_positionCache = false;
    isDirty_pushFlowFunction_63 = false;
    isDirty_pushFlowFunction_115 = false;
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
          binaryMapToRefFlowFunction_101, () -> isDirty_binaryMapToRefFlowFunction_101);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_111, () -> isDirty_binaryMapToRefFlowFunction_111);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_23, () -> isDirty_binaryMapToRefFlowFunction_23);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_29, () -> isDirty_binaryMapToRefFlowFunction_29);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_48, () -> isDirty_binaryMapToRefFlowFunction_48);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_58, () -> isDirty_binaryMapToRefFlowFunction_58);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_69, () -> isDirty_binaryMapToRefFlowFunction_69);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_80, () -> isDirty_binaryMapToRefFlowFunction_80);
      dirtyFlagSupplierMap.put(
          binaryMapToRefFlowFunction_90, () -> isDirty_binaryMapToRefFlowFunction_90);
      dirtyFlagSupplierMap.put(callBackNode_118, () -> isDirty_callBackNode_118);
      dirtyFlagSupplierMap.put(callBackNode_142, () -> isDirty_callBackNode_142);
      dirtyFlagSupplierMap.put(callBackNode_270, () -> isDirty_callBackNode_270);
      dirtyFlagSupplierMap.put(clock, () -> isDirty_clock);
      dirtyFlagSupplierMap.put(derivedRateNode, () -> isDirty_derivedRateNode);
      dirtyFlagSupplierMap.put(eventFeedBatcher, () -> isDirty_eventFeedBatcher);
      dirtyFlagSupplierMap.put(filterFlowFunction_8, () -> isDirty_filterFlowFunction_8);
      dirtyFlagSupplierMap.put(flatMapFlowFunction_17, () -> isDirty_flatMapFlowFunction_17);
      dirtyFlagSupplierMap.put(flatMapFlowFunction_3, () -> isDirty_flatMapFlowFunction_3);
      dirtyFlagSupplierMap.put(flatMapFlowFunction_38, () -> isDirty_flatMapFlowFunction_38);
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
          mapRef2RefFlowFunction_103, () -> isDirty_mapRef2RefFlowFunction_103);
      dirtyFlagSupplierMap.put(
          mapRef2RefFlowFunction_105, () -> isDirty_mapRef2RefFlowFunction_105);
      dirtyFlagSupplierMap.put(
          mapRef2RefFlowFunction_107, () -> isDirty_mapRef2RefFlowFunction_107);
      dirtyFlagSupplierMap.put(
          mapRef2RefFlowFunction_109, () -> isDirty_mapRef2RefFlowFunction_109);
      dirtyFlagSupplierMap.put(
          mapRef2RefFlowFunction_113, () -> isDirty_mapRef2RefFlowFunction_113);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_12, () -> isDirty_mapRef2RefFlowFunction_12);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_14, () -> isDirty_mapRef2RefFlowFunction_14);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_16, () -> isDirty_mapRef2RefFlowFunction_16);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_19, () -> isDirty_mapRef2RefFlowFunction_19);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_25, () -> isDirty_mapRef2RefFlowFunction_25);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_27, () -> isDirty_mapRef2RefFlowFunction_27);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_31, () -> isDirty_mapRef2RefFlowFunction_31);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_33, () -> isDirty_mapRef2RefFlowFunction_33);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_35, () -> isDirty_mapRef2RefFlowFunction_35);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_37, () -> isDirty_mapRef2RefFlowFunction_37);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_4, () -> isDirty_mapRef2RefFlowFunction_4);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_40, () -> isDirty_mapRef2RefFlowFunction_40);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_42, () -> isDirty_mapRef2RefFlowFunction_42);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_46, () -> isDirty_mapRef2RefFlowFunction_46);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_5, () -> isDirty_mapRef2RefFlowFunction_5);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_50, () -> isDirty_mapRef2RefFlowFunction_50);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_54, () -> isDirty_mapRef2RefFlowFunction_54);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_56, () -> isDirty_mapRef2RefFlowFunction_56);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_60, () -> isDirty_mapRef2RefFlowFunction_60);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_61, () -> isDirty_mapRef2RefFlowFunction_61);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_65, () -> isDirty_mapRef2RefFlowFunction_65);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_67, () -> isDirty_mapRef2RefFlowFunction_67);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_71, () -> isDirty_mapRef2RefFlowFunction_71);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_73, () -> isDirty_mapRef2RefFlowFunction_73);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_74, () -> isDirty_mapRef2RefFlowFunction_74);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_76, () -> isDirty_mapRef2RefFlowFunction_76);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_78, () -> isDirty_mapRef2RefFlowFunction_78);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_82, () -> isDirty_mapRef2RefFlowFunction_82);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_86, () -> isDirty_mapRef2RefFlowFunction_86);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_88, () -> isDirty_mapRef2RefFlowFunction_88);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_92, () -> isDirty_mapRef2RefFlowFunction_92);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_94, () -> isDirty_mapRef2RefFlowFunction_94);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_95, () -> isDirty_mapRef2RefFlowFunction_95);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_97, () -> isDirty_mapRef2RefFlowFunction_97);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_99, () -> isDirty_mapRef2RefFlowFunction_99);
      dirtyFlagSupplierMap.put(mergeFlowFunction_6, () -> isDirty_mergeFlowFunction_6);
      dirtyFlagSupplierMap.put(namedFeedTableNode_117, () -> isDirty_namedFeedTableNode_117);
      dirtyFlagSupplierMap.put(positionCache, () -> isDirty_positionCache);
      dirtyFlagSupplierMap.put(pushFlowFunction_115, () -> isDirty_pushFlowFunction_115);
      dirtyFlagSupplierMap.put(pushFlowFunction_63, () -> isDirty_pushFlowFunction_63);
    }
    return dirtyFlagSupplierMap.getOrDefault(node, StaticEventProcessor.ALWAYS_FALSE);
  }

  @Override
  public void setDirty(Object node, boolean dirtyFlag) {
    if (dirtyFlagUpdateMap.isEmpty()) {
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_101, (b) -> isDirty_binaryMapToRefFlowFunction_101 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_111, (b) -> isDirty_binaryMapToRefFlowFunction_111 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_23, (b) -> isDirty_binaryMapToRefFlowFunction_23 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_29, (b) -> isDirty_binaryMapToRefFlowFunction_29 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_48, (b) -> isDirty_binaryMapToRefFlowFunction_48 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_58, (b) -> isDirty_binaryMapToRefFlowFunction_58 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_69, (b) -> isDirty_binaryMapToRefFlowFunction_69 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_80, (b) -> isDirty_binaryMapToRefFlowFunction_80 = b);
      dirtyFlagUpdateMap.put(
          binaryMapToRefFlowFunction_90, (b) -> isDirty_binaryMapToRefFlowFunction_90 = b);
      dirtyFlagUpdateMap.put(callBackNode_118, (b) -> isDirty_callBackNode_118 = b);
      dirtyFlagUpdateMap.put(callBackNode_142, (b) -> isDirty_callBackNode_142 = b);
      dirtyFlagUpdateMap.put(callBackNode_270, (b) -> isDirty_callBackNode_270 = b);
      dirtyFlagUpdateMap.put(clock, (b) -> isDirty_clock = b);
      dirtyFlagUpdateMap.put(derivedRateNode, (b) -> isDirty_derivedRateNode = b);
      dirtyFlagUpdateMap.put(eventFeedBatcher, (b) -> isDirty_eventFeedBatcher = b);
      dirtyFlagUpdateMap.put(filterFlowFunction_8, (b) -> isDirty_filterFlowFunction_8 = b);
      dirtyFlagUpdateMap.put(flatMapFlowFunction_17, (b) -> isDirty_flatMapFlowFunction_17 = b);
      dirtyFlagUpdateMap.put(flatMapFlowFunction_3, (b) -> isDirty_flatMapFlowFunction_3 = b);
      dirtyFlagUpdateMap.put(flatMapFlowFunction_38, (b) -> isDirty_flatMapFlowFunction_38 = b);
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
          mapRef2RefFlowFunction_103, (b) -> isDirty_mapRef2RefFlowFunction_103 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_105, (b) -> isDirty_mapRef2RefFlowFunction_105 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_107, (b) -> isDirty_mapRef2RefFlowFunction_107 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_109, (b) -> isDirty_mapRef2RefFlowFunction_109 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_113, (b) -> isDirty_mapRef2RefFlowFunction_113 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_12, (b) -> isDirty_mapRef2RefFlowFunction_12 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_14, (b) -> isDirty_mapRef2RefFlowFunction_14 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_16, (b) -> isDirty_mapRef2RefFlowFunction_16 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_19, (b) -> isDirty_mapRef2RefFlowFunction_19 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_25, (b) -> isDirty_mapRef2RefFlowFunction_25 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_27, (b) -> isDirty_mapRef2RefFlowFunction_27 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_31, (b) -> isDirty_mapRef2RefFlowFunction_31 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_33, (b) -> isDirty_mapRef2RefFlowFunction_33 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_35, (b) -> isDirty_mapRef2RefFlowFunction_35 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_37, (b) -> isDirty_mapRef2RefFlowFunction_37 = b);
      dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_4, (b) -> isDirty_mapRef2RefFlowFunction_4 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_40, (b) -> isDirty_mapRef2RefFlowFunction_40 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_42, (b) -> isDirty_mapRef2RefFlowFunction_42 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_46, (b) -> isDirty_mapRef2RefFlowFunction_46 = b);
      dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_5, (b) -> isDirty_mapRef2RefFlowFunction_5 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_50, (b) -> isDirty_mapRef2RefFlowFunction_50 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_54, (b) -> isDirty_mapRef2RefFlowFunction_54 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_56, (b) -> isDirty_mapRef2RefFlowFunction_56 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_60, (b) -> isDirty_mapRef2RefFlowFunction_60 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_61, (b) -> isDirty_mapRef2RefFlowFunction_61 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_65, (b) -> isDirty_mapRef2RefFlowFunction_65 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_67, (b) -> isDirty_mapRef2RefFlowFunction_67 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_71, (b) -> isDirty_mapRef2RefFlowFunction_71 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_73, (b) -> isDirty_mapRef2RefFlowFunction_73 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_74, (b) -> isDirty_mapRef2RefFlowFunction_74 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_76, (b) -> isDirty_mapRef2RefFlowFunction_76 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_78, (b) -> isDirty_mapRef2RefFlowFunction_78 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_82, (b) -> isDirty_mapRef2RefFlowFunction_82 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_86, (b) -> isDirty_mapRef2RefFlowFunction_86 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_88, (b) -> isDirty_mapRef2RefFlowFunction_88 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_92, (b) -> isDirty_mapRef2RefFlowFunction_92 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_94, (b) -> isDirty_mapRef2RefFlowFunction_94 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_95, (b) -> isDirty_mapRef2RefFlowFunction_95 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_97, (b) -> isDirty_mapRef2RefFlowFunction_97 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_99, (b) -> isDirty_mapRef2RefFlowFunction_99 = b);
      dirtyFlagUpdateMap.put(mergeFlowFunction_6, (b) -> isDirty_mergeFlowFunction_6 = b);
      dirtyFlagUpdateMap.put(namedFeedTableNode_117, (b) -> isDirty_namedFeedTableNode_117 = b);
      dirtyFlagUpdateMap.put(positionCache, (b) -> isDirty_positionCache = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_115, (b) -> isDirty_pushFlowFunction_115 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_63, (b) -> isDirty_pushFlowFunction_63 = b);
    }
    dirtyFlagUpdateMap.get(node).accept(dirtyFlag);
  }

  private boolean guardCheck_eventLogger() {
    return isDirty_clock;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_23() {
    return isDirty_mapRef2RefFlowFunction_10 | isDirty_mapRef2RefFlowFunction_12;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_29() {
    return isDirty_mapRef2RefFlowFunction_19 | isDirty_mapRef2RefFlowFunction_27;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_48() {
    return isDirty_mapRef2RefFlowFunction_40 | isDirty_mapRef2RefFlowFunction_46;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_58() {
    return isDirty_mapRef2RefFlowFunction_37 | isDirty_mapRef2RefFlowFunction_56;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_69() {
    return isDirty_mapRef2RefFlowFunction_65 | isDirty_mapRef2RefFlowFunction_67;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_80() {
    return isDirty_mapRef2RefFlowFunction_73 | isDirty_mapRef2RefFlowFunction_78;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_90() {
    return isDirty_mapRef2RefFlowFunction_14 | isDirty_mapRef2RefFlowFunction_16;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_101() {
    return isDirty_mapRef2RefFlowFunction_94 | isDirty_mapRef2RefFlowFunction_99;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_111() {
    return isDirty_mapRef2RefFlowFunction_88 | isDirty_mapRef2RefFlowFunction_109;
  }

  private boolean guardCheck_binaryMapToRefFlowFunction_116() {
    return isDirty_positionCache | isDirty_pushFlowFunction_63 | isDirty_pushFlowFunction_115;
  }

  private boolean guardCheck_filterFlowFunction_8() {
    return isDirty_mergeFlowFunction_6;
  }

  private boolean guardCheck_flatMapFlowFunction_3() {
    return isDirty_callBackNode_118;
  }

  private boolean guardCheck_flatMapFlowFunction_17() {
    return isDirty_callBackNode_142;
  }

  private boolean guardCheck_flatMapFlowFunction_38() {
    return isDirty_callBackNode_270;
  }

  private boolean guardCheck_globalNetMtm() {
    return isDirty_mapRef2RefFlowFunction_61;
  }

  private boolean guardCheck_instrumentNetMtm() {
    return isDirty_mapRef2RefFlowFunction_113;
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

  private boolean guardCheck_mapRef2RefFlowFunction_25() {
    return isDirty_binaryMapToRefFlowFunction_23;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_27() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_25;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_31() {
    return isDirty_binaryMapToRefFlowFunction_29;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_33() {
    return isDirty_mapRef2RefFlowFunction_31;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_35() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_33;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_37() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_35;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_40() {
    return isDirty_flatMapFlowFunction_38
        | isDirty_handlerSignal_positionSnapshotReset
        | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_42() {
    return isDirty_filterFlowFunction_8;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_46() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_42;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_50() {
    return isDirty_binaryMapToRefFlowFunction_48 | isDirty_handlerSignal_positionSnapshotReset;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_54() {
    return isDirty_mapRef2RefFlowFunction_50;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_56() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_54;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_60() {
    return isDirty_binaryMapToRefFlowFunction_58 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_61() {
    return isDirty_mapRef2RefFlowFunction_60;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_65() {
    return isDirty_filterFlowFunction_8;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_67() {
    return isDirty_filterFlowFunction_8;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_71() {
    return isDirty_binaryMapToRefFlowFunction_69;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_73() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_71;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_74() {
    return isDirty_handlerPositionSnapshot;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_76() {
    return isDirty_mapRef2RefFlowFunction_74;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_78() {
    return isDirty_mapRef2RefFlowFunction_76;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_82() {
    return isDirty_binaryMapToRefFlowFunction_80 | isDirty_handlerSignal_positionSnapshotReset;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_86() {
    return isDirty_mapRef2RefFlowFunction_82;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_88() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_86;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_92() {
    return isDirty_binaryMapToRefFlowFunction_90;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_94() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_92;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_95() {
    return isDirty_handlerPositionSnapshot;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_97() {
    return isDirty_mapRef2RefFlowFunction_95;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_99() {
    return isDirty_mapRef2RefFlowFunction_97;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_103() {
    return isDirty_binaryMapToRefFlowFunction_101;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_105() {
    return isDirty_mapRef2RefFlowFunction_103;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_107() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_105;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_109() {
    return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_107;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_113() {
    return isDirty_binaryMapToRefFlowFunction_111 | isDirty_handlerSignal_positionUpdate;
  }

  private boolean guardCheck_mergeFlowFunction_6() {
    return isDirty_mapRef2RefFlowFunction_4 | isDirty_mapRef2RefFlowFunction_5;
  }

  private boolean guardCheck_peekFlowFunction_21() {
    return isDirty_mapRef2RefFlowFunction_19;
  }

  private boolean guardCheck_peekFlowFunction_44() {
    return isDirty_mapRef2RefFlowFunction_42;
  }

  private boolean guardCheck_peekFlowFunction_52() {
    return isDirty_mapRef2RefFlowFunction_50;
  }

  private boolean guardCheck_peekFlowFunction_84() {
    return isDirty_mapRef2RefFlowFunction_82;
  }

  private boolean guardCheck_pushFlowFunction_63() {
    return isDirty_globalNetMtm;
  }

  private boolean guardCheck_pushFlowFunction_115() {
    return isDirty_instrumentNetMtm;
  }

  private boolean guardCheck_groupByMapFlowFunction_34() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_groupByMapFlowFunction_55() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_groupByMapFlowFunction_87() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_groupByMapFlowFunction_104() {
    return isDirty_derivedRateNode;
  }

  private boolean guardCheck_context() {
    return isDirty_clock;
  }

  private boolean guardCheck_globalNetMtmListener() {
    return isDirty_pushFlowFunction_63;
  }

  private boolean guardCheck_instrumentNetMtmListener() {
    return isDirty_pushFlowFunction_115;
  }

  private boolean guardCheck_derivedRateNode() {
    return isDirty_namedFeedTableNode_117;
  }

  private boolean guardCheck_eventFeedBatcher() {
    return isDirty_namedFeedTableNode_117;
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
