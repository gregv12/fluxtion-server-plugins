/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.fluxtion.server.lib.pnl.calculator;

import com.fluxtion.runtime.EventProcessor;
import com.fluxtion.runtime.StaticEventProcessor;
import com.fluxtion.runtime.audit.Auditor;
import com.fluxtion.runtime.audit.EventLogManager;
import com.fluxtion.runtime.audit.NodeNameAuditor;
import com.fluxtion.runtime.callback.*;
import com.fluxtion.runtime.dataflow.aggregate.function.AggregateFlowFunctionWrapper;
import com.fluxtion.runtime.dataflow.aggregate.function.AggregateIdentityFlowFunction;
import com.fluxtion.runtime.dataflow.aggregate.function.primitive.DoubleSumFlowFunction;
import com.fluxtion.runtime.dataflow.function.BinaryMapFlowFunction.BinaryMapToRefFlowFunction;
import com.fluxtion.runtime.dataflow.function.FilterFlowFunction;
import com.fluxtion.runtime.dataflow.function.FlatMapFlowFunction;
import com.fluxtion.runtime.dataflow.function.MapFlowFunction.MapRef2RefFlowFunction;
import com.fluxtion.runtime.dataflow.function.MergeFlowFunction;
import com.fluxtion.runtime.dataflow.function.PushFlowFunction;
import com.fluxtion.runtime.dataflow.groupby.*;
import com.fluxtion.runtime.dataflow.groupby.GroupBy.EmptyGroupBy;
import com.fluxtion.runtime.dataflow.helpers.DefaultValue;
import com.fluxtion.runtime.dataflow.helpers.Mappers;
import com.fluxtion.runtime.event.Event;
import com.fluxtion.runtime.event.Signal;
import com.fluxtion.runtime.input.EventFeed;
import com.fluxtion.runtime.input.SubscriptionManager;
import com.fluxtion.runtime.input.SubscriptionManagerNode;
import com.fluxtion.runtime.lifecycle.BatchHandler;
import com.fluxtion.runtime.lifecycle.Lifecycle;
import com.fluxtion.runtime.node.DefaultEventHandlerNode;
import com.fluxtion.runtime.node.MutableEventProcessorContext;
import com.fluxtion.runtime.output.SinkDeregister;
import com.fluxtion.runtime.output.SinkPublisher;
import com.fluxtion.runtime.output.SinkRegistration;
import com.fluxtion.runtime.service.ServiceListener;
import com.fluxtion.runtime.service.ServiceRegistryNode;
import com.fluxtion.runtime.time.Clock;
import com.fluxtion.runtime.time.ClockStrategy.ClockStrategyEvent;
import com.fluxtion.server.lib.pnl.*;
import com.fluxtion.server.lib.pnl.refdata.Instrument;
import com.fluxtion.server.lib.pnl.refdata.SymbolLookup;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * <pre>
 * generation time                 : Not available
 * eventProcessorGenerator version : 9.3.43
 * api version                     : 9.3.43
 * </pre>
 * <p>
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
    private final CallbackImpl callbackImpl_58 = new CallbackImpl<>(1, callbackDispatcher);
    private final CallbackImpl callbackImpl_62 = new CallbackImpl<>(2, callbackDispatcher);
    public final Clock clock = new Clock();
    private final DerivedRateNode derivedRateNode_30 = new DerivedRateNode();
    private final Double double_59 = 0.0d;
    private final DefaultValue defaultValue_7 = new DefaultValue<>(0.0);
    private final DoubleSumFlowFunction doubleSumFlowFunction_159 = new DoubleSumFlowFunction();
    private final EmptyGroupBy emptyGroupBy_86 = new EmptyGroupBy<>();
    private final DefaultValue defaultValue_25 = new DefaultValue<>(emptyGroupBy_86);
    private final EmptyGroupBy emptyGroupBy_106 = new EmptyGroupBy<>();
    private final DefaultValue defaultValue_32 = new DefaultValue<>(emptyGroupBy_106);
    private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_11 =
            new GroupByFlowFunctionWrapper<>(
                    InstrumentPosition::instrument,
                    InstrumentPosition::position,
                    AggregateIdentityFlowFunction::new);
    private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_13 =
            new GroupByFlowFunctionWrapper<>(
                    Trade::getDealtInstrument, Trade::getDealtVolume, DoubleSumFlowFunction::new);
    private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_15 =
            new GroupByFlowFunctionWrapper<>(
                    Trade::getContraInstrument, Trade::getContraVolume, DoubleSumFlowFunction::new);
    private final GroupByFlowFunctionWrapper groupByFlowFunctionWrapper_28 =
            new GroupByFlowFunctionWrapper<>(
                    MidPrice::getUsdContraInstrument,
                    MidPrice::getUsdRate,
                    AggregateIdentityFlowFunction::new);
    private final GroupByMapFlowFunction groupByMapFlowFunction_19 =
            new GroupByMapFlowFunction(MathUtil::addPositions);
    private final GroupByMapFlowFunction groupByMapFlowFunction_23 =
            new GroupByMapFlowFunction(MathUtil::addPositions);
    private final GroupByMapFlowFunction groupByMapFlowFunction_36 =
            new GroupByMapFlowFunction(MathUtil::mtmPositions);
    private final GroupByMapFlowFunction groupByMapFlowFunction_41 =
            new GroupByMapFlowFunction(Instrument::instrumentName);
    private final GroupByMapFlowFunction groupByMapFlowFunction_46 =
            new GroupByMapFlowFunction(Instrument::instrumentName);
    private final GroupByMapFlowFunction groupByMapFlowFunction_50 =
            new GroupByMapFlowFunction(Instrument::instrumentName);
    private final GroupByReduceFlowFunction groupByReduceFlowFunction_38 =
            new GroupByReduceFlowFunction(doubleSumFlowFunction_159);
    private final LeftJoin leftJoin_34 = new LeftJoin();
    public final NodeNameAuditor nodeNameLookup = new NodeNameAuditor();
    private final OuterJoin outerJoin_17 = new OuterJoin();
    private final OuterJoin outerJoin_21 = new OuterJoin();
    private final SubscriptionManagerNode subscriptionManager = new SubscriptionManagerNode();
    private final MutableEventProcessorContext context =
            new MutableEventProcessorContext(
                    nodeNameLookup, callbackDispatcher, subscriptionManager, callbackDispatcher);
    private final DefaultEventHandlerNode handlerMidPrice =
            new DefaultEventHandlerNode<>(
                    2147483647, "", com.fluxtion.server.lib.pnl.MidPrice.class, "handlerMidPrice", context);
    private final FilterFlowFunction filterFlowFunction_27 =
            new FilterFlowFunction<>(handlerMidPrice, MidPrice::hasUsdRate);
    private final DefaultEventHandlerNode handlerPositionSnapshot =
            new DefaultEventHandlerNode<>(
                    2147483647,
                    "",
                    com.fluxtion.server.lib.pnl.PositionSnapshot.class,
                    "handlerPositionSnapshot",
                    context);
    private final FlatMapFlowFunction flatMapFlowFunction_10 =
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
    private final MapRef2RefFlowFunction mapRef2RefFlowFunction_4 =
            new MapRef2RefFlowFunction<>(handlerTradeBatch, TradeBatch::getFee);
    private final MapRef2RefFlowFunction mapRef2RefFlowFunction_12 =
            new MapRef2RefFlowFunction<>(
                    flatMapFlowFunction_10, groupByFlowFunctionWrapper_11::aggregate);
    private final MapRef2RefFlowFunction mapRef2RefFlowFunction_29 =
            new MapRef2RefFlowFunction<>(filterFlowFunction_27, groupByFlowFunctionWrapper_28::aggregate);
    private final MergeFlowFunction mergeFlowFunction_2 =
            new MergeFlowFunction<>(Arrays.asList(handlerTrade, flatMapFlowFunction_1));
    private final MapRef2RefFlowFunction mapRef2RefFlowFunction_3 =
            new MapRef2RefFlowFunction<>(mergeFlowFunction_2, Trade::getFee);
    private final MapRef2RefFlowFunction mapRef2RefFlowFunction_14 =
            new MapRef2RefFlowFunction<>(mergeFlowFunction_2, groupByFlowFunctionWrapper_13::aggregate);
    private final MapRef2RefFlowFunction mapRef2RefFlowFunction_16 =
            new MapRef2RefFlowFunction<>(mergeFlowFunction_2, groupByFlowFunctionWrapper_15::aggregate);
    private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_18 =
            new BinaryMapToRefFlowFunction<>(
                    mapRef2RefFlowFunction_14, mapRef2RefFlowFunction_16, outerJoin_17::join);
    private final MapRef2RefFlowFunction mapRef2RefFlowFunction_20 =
            new MapRef2RefFlowFunction<>(
                    binaryMapToRefFlowFunction_18, groupByMapFlowFunction_19::mapValues);
    private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_22 =
            new BinaryMapToRefFlowFunction<>(
                    mapRef2RefFlowFunction_20, mapRef2RefFlowFunction_12, outerJoin_21::join);
    private final MapRef2RefFlowFunction mapRef2RefFlowFunction_24 =
            new MapRef2RefFlowFunction<>(
                    binaryMapToRefFlowFunction_22, groupByMapFlowFunction_23::mapValues);
    private final MapRef2RefFlowFunction mapRef2RefFlowFunction_26 =
            new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_24, defaultValue_25::getOrDefault);
    private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_31 =
            new BinaryMapToRefFlowFunction<>(
                    mapRef2RefFlowFunction_29, mapRef2RefFlowFunction_26, derivedRateNode_30::addDerived);
    private final MapRef2RefFlowFunction mapRef2RefFlowFunction_33 =
            new MapRef2RefFlowFunction<>(binaryMapToRefFlowFunction_31, defaultValue_32::getOrDefault);
    private final BinaryMapToRefFlowFunction binaryMapToRefFlowFunction_35 =
            new BinaryMapToRefFlowFunction<>(
                    mapRef2RefFlowFunction_26, mapRef2RefFlowFunction_33, leftJoin_34::join);
    private final MapRef2RefFlowFunction mapRef2RefFlowFunction_37 =
            new MapRef2RefFlowFunction<>(
                    binaryMapToRefFlowFunction_35, groupByMapFlowFunction_36::mapValues);
    private final MapRef2RefFlowFunction mapRef2RefFlowFunction_42 =
            new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_26, groupByMapFlowFunction_41::mapKeys);
    private final MapRef2RefFlowFunction mapRef2RefFlowFunction_47 =
            new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_33, groupByMapFlowFunction_46::mapKeys);
    private final MapRef2RefFlowFunction mapRef2RefFlowFunction_51 =
            new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_37, groupByMapFlowFunction_50::mapKeys);
    private final MergeFlowFunction mergeFlowFunction_5 =
            new MergeFlowFunction<>(Arrays.asList(mapRef2RefFlowFunction_3, mapRef2RefFlowFunction_4));
    private final AggregateFlowFunctionWrapper aggregateFlowFunctionWrapper_6 =
            new AggregateFlowFunctionWrapper<>(mergeFlowFunction_5, DoubleSumFlowFunction::new);
    private final MapRef2RefFlowFunction mapRef2RefFlowFunction_8 =
            new MapRef2RefFlowFunction<>(aggregateFlowFunctionWrapper_6, defaultValue_7::getOrDefault);
    private final SinkPublisher mtmPositionListener = new SinkPublisher<>("mtmPositionListener");
    public final MapRef2RefFlowFunction mtmPositionMap =
            new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_51, GroupBy<Object, Object>::toMap);
    private final SinkPublisher netPnlListener = new SinkPublisher<>("netPnlListener");
    public final MapRef2RefFlowFunction pnl =
            new MapRef2RefFlowFunction<>(
                    mapRef2RefFlowFunction_37, groupByReduceFlowFunction_38::reduceValues);
    private final FilterFlowFunction filterFlowFunction_54 =
            new FilterFlowFunction<>(pnl, Double::isFinite);
    private final SinkPublisher pnlListener = new SinkPublisher<>("pnlListener");
    private final SinkPublisher positionListener = new SinkPublisher<>("positionListener");
    public final MapRef2RefFlowFunction positionMap =
            new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_42, GroupBy<Object, Object>::toMap);
    private final PushFlowFunction pushFlowFunction_44 =
            new PushFlowFunction<>(positionMap, positionListener::publish);
    private final PushFlowFunction pushFlowFunction_53 =
            new PushFlowFunction<>(mtmPositionMap, mtmPositionListener::publish);
    private final PushFlowFunction pushFlowFunction_55 =
            new PushFlowFunction<>(filterFlowFunction_54, pnlListener::publish);
    private final SinkPublisher rateListener = new SinkPublisher<>("rateListener");
    public final MapRef2RefFlowFunction rates =
            new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_47, GroupBy<Object, Object>::toMap);
    private final PushFlowFunction pushFlowFunction_49 =
            new PushFlowFunction<>(rates, rateListener::publish);
    public final ServiceRegistryNode serviceRegistry = new ServiceRegistryNode();
    public final MapRef2RefFlowFunction tradeFees =
            new MapRef2RefFlowFunction<>(mapRef2RefFlowFunction_8, MathUtil::round8dp);
    public final BinaryMapToRefFlowFunction netPnl =
            new BinaryMapToRefFlowFunction<>(pnl, tradeFees, Mappers::subtractDoubles);
    private final FilterFlowFunction filterFlowFunction_56 =
            new FilterFlowFunction<>(netPnl, Double::isFinite);
    private final PushFlowFunction pushFlowFunction_57 =
            new PushFlowFunction<>(filterFlowFunction_56, netPnlListener::publish);
    private final SinkPublisher tradeFeesListener = new SinkPublisher<>("tradeFeesListener");
    private final PushFlowFunction pushFlowFunction_45 =
            new PushFlowFunction<>(tradeFees, tradeFeesListener::publish);
    private final SymbolLookupNode symbolLookupNode_0 = new SymbolLookupNode();
    private final ExportFunctionAuditEvent functionAudit = new ExportFunctionAuditEvent();
    //Dirty flags
    private boolean initCalled = false;
    private boolean processing = false;
    private boolean buffering = false;
    private final IdentityHashMap<Object, BooleanSupplier> dirtyFlagSupplierMap =
            new IdentityHashMap<>(48);
    private final IdentityHashMap<Object, Consumer<Boolean>> dirtyFlagUpdateMap =
            new IdentityHashMap<>(48);

    private boolean isDirty_aggregateFlowFunctionWrapper_6 = false;
    private boolean isDirty_binaryMapToRefFlowFunction_18 = false;
    private boolean isDirty_binaryMapToRefFlowFunction_22 = false;
    private boolean isDirty_binaryMapToRefFlowFunction_31 = false;
    private boolean isDirty_binaryMapToRefFlowFunction_35 = false;
    private boolean isDirty_callbackImpl_58 = false;
    private boolean isDirty_callbackImpl_62 = false;
    private boolean isDirty_clock = false;
    private boolean isDirty_filterFlowFunction_27 = false;
    private boolean isDirty_filterFlowFunction_54 = false;
    private boolean isDirty_filterFlowFunction_56 = false;
    private boolean isDirty_flatMapFlowFunction_1 = false;
    private boolean isDirty_flatMapFlowFunction_10 = false;
    private boolean isDirty_handlerMidPrice = false;
    private boolean isDirty_handlerPositionSnapshot = false;
    private boolean isDirty_handlerSignal_positionSnapshotReset = false;
    private boolean isDirty_handlerSignal_positionUpdate = false;
    private boolean isDirty_handlerTrade = false;
    private boolean isDirty_handlerTradeBatch = false;
    private boolean isDirty_mapRef2RefFlowFunction_3 = false;
    private boolean isDirty_mapRef2RefFlowFunction_4 = false;
    private boolean isDirty_mapRef2RefFlowFunction_8 = false;
    private boolean isDirty_mapRef2RefFlowFunction_12 = false;
    private boolean isDirty_mapRef2RefFlowFunction_14 = false;
    private boolean isDirty_mapRef2RefFlowFunction_16 = false;
    private boolean isDirty_mapRef2RefFlowFunction_20 = false;
    private boolean isDirty_mapRef2RefFlowFunction_24 = false;
    private boolean isDirty_mapRef2RefFlowFunction_26 = false;
    private boolean isDirty_mapRef2RefFlowFunction_29 = false;
    private boolean isDirty_mapRef2RefFlowFunction_33 = false;
    private boolean isDirty_mapRef2RefFlowFunction_37 = false;
    private boolean isDirty_mapRef2RefFlowFunction_42 = false;
    private boolean isDirty_mapRef2RefFlowFunction_47 = false;
    private boolean isDirty_mapRef2RefFlowFunction_51 = false;
    private boolean isDirty_mergeFlowFunction_2 = false;
    private boolean isDirty_mergeFlowFunction_5 = false;
    private boolean isDirty_mtmPositionMap = false;
    private boolean isDirty_netPnl = false;
    private boolean isDirty_pnl = false;
    private boolean isDirty_positionMap = false;
    private boolean isDirty_pushFlowFunction_44 = false;
    private boolean isDirty_pushFlowFunction_45 = false;
    private boolean isDirty_pushFlowFunction_49 = false;
    private boolean isDirty_pushFlowFunction_53 = false;
    private boolean isDirty_pushFlowFunction_55 = false;
    private boolean isDirty_pushFlowFunction_57 = false;
    private boolean isDirty_rates = false;
    private boolean isDirty_tradeFees = false;

    //Forked declarations

    //Filter constants

    //unknown event handler
    private Consumer unKnownEventHandler = (e) -> {
    };

    public FluxtionPnlCalculator(Map<Object, Object> contextMap) {
        if (context != null) {
            context.replaceMappings(contextMap);
        }
        callbackImpl_58.dirtyStateMonitor = callbackDispatcher;
        callbackImpl_62.dirtyStateMonitor = callbackDispatcher;
        aggregateFlowFunctionWrapper_6.setEventProcessorContext(context);
        doubleSumFlowFunction_159.dirtyStateMonitor = callbackDispatcher;
        binaryMapToRefFlowFunction_18.setEventProcessorContext(context);
        binaryMapToRefFlowFunction_18.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
        binaryMapToRefFlowFunction_22.setEventProcessorContext(context);
        binaryMapToRefFlowFunction_31.setEventProcessorContext(context);
        binaryMapToRefFlowFunction_35.setEventProcessorContext(context);
        netPnl.setEventProcessorContext(context);
        netPnl.setUpdateTriggerNode(handlerSignal_positionUpdate);
        filterFlowFunction_27.setEventProcessorContext(context);
        filterFlowFunction_54.setEventProcessorContext(context);
        filterFlowFunction_56.setEventProcessorContext(context);
        flatMapFlowFunction_1.callback = callbackImpl_58;
        flatMapFlowFunction_1.dirtyStateMonitor = callbackDispatcher;
        flatMapFlowFunction_10.callback = callbackImpl_62;
        flatMapFlowFunction_10.dirtyStateMonitor = callbackDispatcher;
        mapRef2RefFlowFunction_3.setEventProcessorContext(context);
        mapRef2RefFlowFunction_4.setEventProcessorContext(context);
        mapRef2RefFlowFunction_8.setEventProcessorContext(context);
        mapRef2RefFlowFunction_8.setResetTriggerNode(handlerSignal_positionSnapshotReset);
        mapRef2RefFlowFunction_12.setEventProcessorContext(context);
        mapRef2RefFlowFunction_12.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
        mapRef2RefFlowFunction_12.setResetTriggerNode(handlerSignal_positionSnapshotReset);
        mapRef2RefFlowFunction_14.setEventProcessorContext(context);
        mapRef2RefFlowFunction_14.setResetTriggerNode(handlerSignal_positionSnapshotReset);
        mapRef2RefFlowFunction_16.setEventProcessorContext(context);
        mapRef2RefFlowFunction_16.setResetTriggerNode(handlerSignal_positionSnapshotReset);
        mapRef2RefFlowFunction_20.setEventProcessorContext(context);
        mapRef2RefFlowFunction_24.setEventProcessorContext(context);
        mapRef2RefFlowFunction_26.setEventProcessorContext(context);
        mapRef2RefFlowFunction_26.setUpdateTriggerNode(handlerSignal_positionUpdate);
        mapRef2RefFlowFunction_29.setEventProcessorContext(context);
        mapRef2RefFlowFunction_33.setEventProcessorContext(context);
        mapRef2RefFlowFunction_37.setEventProcessorContext(context);
        mapRef2RefFlowFunction_37.setUpdateTriggerNode(handlerSignal_positionUpdate);
        mapRef2RefFlowFunction_42.setEventProcessorContext(context);
        mapRef2RefFlowFunction_47.setEventProcessorContext(context);
        mapRef2RefFlowFunction_51.setEventProcessorContext(context);
        mtmPositionMap.setEventProcessorContext(context);
        pnl.setEventProcessorContext(context);
        pnl.setUpdateTriggerNode(handlerSignal_positionUpdate);
        positionMap.setEventProcessorContext(context);
        rates.setEventProcessorContext(context);
        tradeFees.setEventProcessorContext(context);
        tradeFees.setPublishTriggerOverrideNode(handlerSignal_positionUpdate);
        mergeFlowFunction_2.dirtyStateMonitor = callbackDispatcher;
        mergeFlowFunction_5.dirtyStateMonitor = callbackDispatcher;
        pushFlowFunction_44.setEventProcessorContext(context);
        pushFlowFunction_45.setEventProcessorContext(context);
        pushFlowFunction_49.setEventProcessorContext(context);
        pushFlowFunction_53.setEventProcessorContext(context);
        pushFlowFunction_55.setEventProcessorContext(context);
        pushFlowFunction_57.setEventProcessorContext(context);
        context.setClock(clock);
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
        callbackImpl_58.init();
        callbackImpl_62.init();
        clock.init();
        doubleSumFlowFunction_159.init();
        handlerMidPrice.init();
        filterFlowFunction_27.initialiseEventStream();
        handlerPositionSnapshot.init();
        handlerSignal_positionSnapshotReset.init();
        handlerSignal_positionUpdate.init();
        handlerTrade.init();
        handlerTradeBatch.init();
        mapRef2RefFlowFunction_4.initialiseEventStream();
        mapRef2RefFlowFunction_12.initialiseEventStream();
        mapRef2RefFlowFunction_29.initialiseEventStream();
        mapRef2RefFlowFunction_3.initialiseEventStream();
        mapRef2RefFlowFunction_14.initialiseEventStream();
        mapRef2RefFlowFunction_16.initialiseEventStream();
        binaryMapToRefFlowFunction_18.initialiseEventStream();
        mapRef2RefFlowFunction_20.initialiseEventStream();
        binaryMapToRefFlowFunction_22.initialiseEventStream();
        mapRef2RefFlowFunction_24.initialiseEventStream();
        mapRef2RefFlowFunction_26.initialiseEventStream();
        binaryMapToRefFlowFunction_31.initialiseEventStream();
        mapRef2RefFlowFunction_33.initialiseEventStream();
        binaryMapToRefFlowFunction_35.initialiseEventStream();
        mapRef2RefFlowFunction_37.initialiseEventStream();
        mapRef2RefFlowFunction_42.initialiseEventStream();
        mapRef2RefFlowFunction_47.initialiseEventStream();
        mapRef2RefFlowFunction_51.initialiseEventStream();
        aggregateFlowFunctionWrapper_6.initialiseEventStream();
        mapRef2RefFlowFunction_8.initialiseEventStream();
        mtmPositionMap.initialiseEventStream();
        pnl.initialiseEventStream();
        filterFlowFunction_54.initialiseEventStream();
        positionMap.initialiseEventStream();
        pushFlowFunction_44.initialiseEventStream();
        pushFlowFunction_53.initialiseEventStream();
        pushFlowFunction_55.initialiseEventStream();
        rates.initialiseEventStream();
        pushFlowFunction_49.initialiseEventStream();
        tradeFees.initialiseEventStream();
        netPnl.initialiseEventStream();
        filterFlowFunction_56.initialiseEventStream();
        pushFlowFunction_57.initialiseEventStream();
        pushFlowFunction_45.initialiseEventStream();
        afterEvent();
    }

    @Override
    public void start() {
        if (!initCalled) {
            throw new RuntimeException("init() must be called before start()");
        }
        processing = true;
        auditEvent(Lifecycle.LifecycleEvent.Start);
        symbolLookupNode_0.start();
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
                isDirty_callbackImpl_58 = callbackImpl_58.onEvent(typedEvent);
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
                        mapRef2RefFlowFunction_3.inputUpdated(mergeFlowFunction_2);
                        mapRef2RefFlowFunction_14.inputUpdated(mergeFlowFunction_2);
                        mapRef2RefFlowFunction_16.inputUpdated(mergeFlowFunction_2);
                    }
                }
                if (guardCheck_mapRef2RefFlowFunction_3()) {
                    isDirty_mapRef2RefFlowFunction_3 = mapRef2RefFlowFunction_3.map();
                    if (isDirty_mapRef2RefFlowFunction_3) {
                        mergeFlowFunction_5.inputStreamUpdated(mapRef2RefFlowFunction_3);
                    }
                }
                if (guardCheck_mapRef2RefFlowFunction_14()) {
                    isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
                    if (isDirty_mapRef2RefFlowFunction_14) {
                        binaryMapToRefFlowFunction_18.inputUpdated(mapRef2RefFlowFunction_14);
                    }
                }
                if (guardCheck_mapRef2RefFlowFunction_16()) {
                    isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
                    if (isDirty_mapRef2RefFlowFunction_16) {
                        binaryMapToRefFlowFunction_18.input2Updated(mapRef2RefFlowFunction_16);
                    }
                }
                if (guardCheck_binaryMapToRefFlowFunction_18()) {
                    isDirty_binaryMapToRefFlowFunction_18 = binaryMapToRefFlowFunction_18.map();
                    if (isDirty_binaryMapToRefFlowFunction_18) {
                        mapRef2RefFlowFunction_20.inputUpdated(binaryMapToRefFlowFunction_18);
                    }
                }
                if (guardCheck_mapRef2RefFlowFunction_20()) {
                    isDirty_mapRef2RefFlowFunction_20 = mapRef2RefFlowFunction_20.map();
                    if (isDirty_mapRef2RefFlowFunction_20) {
                        binaryMapToRefFlowFunction_22.inputUpdated(mapRef2RefFlowFunction_20);
                    }
                }
                if (guardCheck_binaryMapToRefFlowFunction_22()) {
                    isDirty_binaryMapToRefFlowFunction_22 = binaryMapToRefFlowFunction_22.map();
                    if (isDirty_binaryMapToRefFlowFunction_22) {
                        mapRef2RefFlowFunction_24.inputUpdated(binaryMapToRefFlowFunction_22);
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
                        binaryMapToRefFlowFunction_31.input2Updated(mapRef2RefFlowFunction_26);
                        binaryMapToRefFlowFunction_35.inputUpdated(mapRef2RefFlowFunction_26);
                        mapRef2RefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_26);
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
                        mapRef2RefFlowFunction_47.inputUpdated(mapRef2RefFlowFunction_33);
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
                        mapRef2RefFlowFunction_51.inputUpdated(mapRef2RefFlowFunction_37);
                        pnl.inputUpdated(mapRef2RefFlowFunction_37);
                    }
                }
                if (guardCheck_mapRef2RefFlowFunction_42()) {
                    isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
                    if (isDirty_mapRef2RefFlowFunction_42) {
                        positionMap.inputUpdated(mapRef2RefFlowFunction_42);
                    }
                }
                if (guardCheck_mapRef2RefFlowFunction_47()) {
                    isDirty_mapRef2RefFlowFunction_47 = mapRef2RefFlowFunction_47.map();
                    if (isDirty_mapRef2RefFlowFunction_47) {
                        rates.inputUpdated(mapRef2RefFlowFunction_47);
                    }
                }
                if (guardCheck_mapRef2RefFlowFunction_51()) {
                    isDirty_mapRef2RefFlowFunction_51 = mapRef2RefFlowFunction_51.map();
                    if (isDirty_mapRef2RefFlowFunction_51) {
                        mtmPositionMap.inputUpdated(mapRef2RefFlowFunction_51);
                    }
                }
                if (guardCheck_mergeFlowFunction_5()) {
                    isDirty_mergeFlowFunction_5 = mergeFlowFunction_5.publishMerge();
                    if (isDirty_mergeFlowFunction_5) {
                        aggregateFlowFunctionWrapper_6.inputUpdated(mergeFlowFunction_5);
                    }
                }
                if (guardCheck_aggregateFlowFunctionWrapper_6()) {
                    isDirty_aggregateFlowFunctionWrapper_6 = aggregateFlowFunctionWrapper_6.map();
                    if (isDirty_aggregateFlowFunctionWrapper_6) {
                        mapRef2RefFlowFunction_8.inputUpdated(aggregateFlowFunctionWrapper_6);
                    }
                }
                if (guardCheck_mapRef2RefFlowFunction_8()) {
                    isDirty_mapRef2RefFlowFunction_8 = mapRef2RefFlowFunction_8.map();
                    if (isDirty_mapRef2RefFlowFunction_8) {
                        tradeFees.inputUpdated(mapRef2RefFlowFunction_8);
                    }
                }
                if (guardCheck_mtmPositionMap()) {
                    isDirty_mtmPositionMap = mtmPositionMap.map();
                    if (isDirty_mtmPositionMap) {
                        pushFlowFunction_53.inputUpdated(mtmPositionMap);
                    }
                }
                if (guardCheck_pnl()) {
                    isDirty_pnl = pnl.map();
                    if (isDirty_pnl) {
                        filterFlowFunction_54.inputUpdated(pnl);
                        netPnl.inputUpdated(pnl);
                    }
                }
                if (guardCheck_filterFlowFunction_54()) {
                    isDirty_filterFlowFunction_54 = filterFlowFunction_54.filter();
                    if (isDirty_filterFlowFunction_54) {
                        pushFlowFunction_55.inputUpdated(filterFlowFunction_54);
                    }
                }
                if (guardCheck_positionMap()) {
                    isDirty_positionMap = positionMap.map();
                    if (isDirty_positionMap) {
                        pushFlowFunction_44.inputUpdated(positionMap);
                    }
                }
                if (guardCheck_pushFlowFunction_44()) {
                    isDirty_pushFlowFunction_44 = pushFlowFunction_44.push();
                }
                if (guardCheck_pushFlowFunction_53()) {
                    isDirty_pushFlowFunction_53 = pushFlowFunction_53.push();
                }
                if (guardCheck_pushFlowFunction_55()) {
                    isDirty_pushFlowFunction_55 = pushFlowFunction_55.push();
                }
                if (guardCheck_rates()) {
                    isDirty_rates = rates.map();
                    if (isDirty_rates) {
                        pushFlowFunction_49.inputUpdated(rates);
                    }
                }
                if (guardCheck_pushFlowFunction_49()) {
                    isDirty_pushFlowFunction_49 = pushFlowFunction_49.push();
                }
                if (guardCheck_tradeFees()) {
                    isDirty_tradeFees = tradeFees.map();
                    if (isDirty_tradeFees) {
                        netPnl.input2Updated(tradeFees);
                        pushFlowFunction_45.inputUpdated(tradeFees);
                    }
                }
                if (guardCheck_netPnl()) {
                    isDirty_netPnl = netPnl.map();
                    if (isDirty_netPnl) {
                        filterFlowFunction_56.inputUpdated(netPnl);
                    }
                }
                if (guardCheck_filterFlowFunction_56()) {
                    isDirty_filterFlowFunction_56 = filterFlowFunction_56.filter();
                    if (isDirty_filterFlowFunction_56) {
                        pushFlowFunction_57.inputUpdated(filterFlowFunction_56);
                    }
                }
                if (guardCheck_pushFlowFunction_45()) {
                    isDirty_pushFlowFunction_45 = pushFlowFunction_45.push();
                }
                if (guardCheck_pushFlowFunction_57()) {
                    isDirty_pushFlowFunction_57 = pushFlowFunction_57.push();
                }
                afterEvent();
                return;
            //Event Class:[com.fluxtion.runtime.callback.CallbackEvent] filterId:[2]
            case (2):
                isDirty_callbackImpl_62 = callbackImpl_62.onEvent(typedEvent);
                if (guardCheck_flatMapFlowFunction_10()) {
                    isDirty_flatMapFlowFunction_10 = true;
                    flatMapFlowFunction_10.callbackReceived();
                    if (isDirty_flatMapFlowFunction_10) {
                        mapRef2RefFlowFunction_12.inputUpdated(flatMapFlowFunction_10);
                    }
                }
                if (guardCheck_mapRef2RefFlowFunction_12()) {
                    isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
                    if (isDirty_mapRef2RefFlowFunction_12) {
                        binaryMapToRefFlowFunction_22.input2Updated(mapRef2RefFlowFunction_12);
                    }
                }
                if (guardCheck_binaryMapToRefFlowFunction_22()) {
                    isDirty_binaryMapToRefFlowFunction_22 = binaryMapToRefFlowFunction_22.map();
                    if (isDirty_binaryMapToRefFlowFunction_22) {
                        mapRef2RefFlowFunction_24.inputUpdated(binaryMapToRefFlowFunction_22);
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
                        binaryMapToRefFlowFunction_31.input2Updated(mapRef2RefFlowFunction_26);
                        binaryMapToRefFlowFunction_35.inputUpdated(mapRef2RefFlowFunction_26);
                        mapRef2RefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_26);
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
                        mapRef2RefFlowFunction_47.inputUpdated(mapRef2RefFlowFunction_33);
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
                        mapRef2RefFlowFunction_51.inputUpdated(mapRef2RefFlowFunction_37);
                        pnl.inputUpdated(mapRef2RefFlowFunction_37);
                    }
                }
                if (guardCheck_mapRef2RefFlowFunction_42()) {
                    isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
                    if (isDirty_mapRef2RefFlowFunction_42) {
                        positionMap.inputUpdated(mapRef2RefFlowFunction_42);
                    }
                }
                if (guardCheck_mapRef2RefFlowFunction_47()) {
                    isDirty_mapRef2RefFlowFunction_47 = mapRef2RefFlowFunction_47.map();
                    if (isDirty_mapRef2RefFlowFunction_47) {
                        rates.inputUpdated(mapRef2RefFlowFunction_47);
                    }
                }
                if (guardCheck_mapRef2RefFlowFunction_51()) {
                    isDirty_mapRef2RefFlowFunction_51 = mapRef2RefFlowFunction_51.map();
                    if (isDirty_mapRef2RefFlowFunction_51) {
                        mtmPositionMap.inputUpdated(mapRef2RefFlowFunction_51);
                    }
                }
                if (guardCheck_mtmPositionMap()) {
                    isDirty_mtmPositionMap = mtmPositionMap.map();
                    if (isDirty_mtmPositionMap) {
                        pushFlowFunction_53.inputUpdated(mtmPositionMap);
                    }
                }
                if (guardCheck_pnl()) {
                    isDirty_pnl = pnl.map();
                    if (isDirty_pnl) {
                        filterFlowFunction_54.inputUpdated(pnl);
                        netPnl.inputUpdated(pnl);
                    }
                }
                if (guardCheck_filterFlowFunction_54()) {
                    isDirty_filterFlowFunction_54 = filterFlowFunction_54.filter();
                    if (isDirty_filterFlowFunction_54) {
                        pushFlowFunction_55.inputUpdated(filterFlowFunction_54);
                    }
                }
                if (guardCheck_positionMap()) {
                    isDirty_positionMap = positionMap.map();
                    if (isDirty_positionMap) {
                        pushFlowFunction_44.inputUpdated(positionMap);
                    }
                }
                if (guardCheck_pushFlowFunction_44()) {
                    isDirty_pushFlowFunction_44 = pushFlowFunction_44.push();
                }
                if (guardCheck_pushFlowFunction_53()) {
                    isDirty_pushFlowFunction_53 = pushFlowFunction_53.push();
                }
                if (guardCheck_pushFlowFunction_55()) {
                    isDirty_pushFlowFunction_55 = pushFlowFunction_55.push();
                }
                if (guardCheck_rates()) {
                    isDirty_rates = rates.map();
                    if (isDirty_rates) {
                        pushFlowFunction_49.inputUpdated(rates);
                    }
                }
                if (guardCheck_pushFlowFunction_49()) {
                    isDirty_pushFlowFunction_49 = pushFlowFunction_49.push();
                }
                if (guardCheck_netPnl()) {
                    isDirty_netPnl = netPnl.map();
                    if (isDirty_netPnl) {
                        filterFlowFunction_56.inputUpdated(netPnl);
                    }
                }
                if (guardCheck_filterFlowFunction_56()) {
                    isDirty_filterFlowFunction_56 = filterFlowFunction_56.filter();
                    if (isDirty_filterFlowFunction_56) {
                        pushFlowFunction_57.inputUpdated(filterFlowFunction_56);
                    }
                }
                if (guardCheck_pushFlowFunction_57()) {
                    isDirty_pushFlowFunction_57 = pushFlowFunction_57.push();
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
                    mapRef2RefFlowFunction_14.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
                    mapRef2RefFlowFunction_16.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
                    mapRef2RefFlowFunction_8.resetTriggerNodeUpdated(handlerSignal_positionSnapshotReset);
                }
                if (guardCheck_mapRef2RefFlowFunction_12()) {
                    isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
                    if (isDirty_mapRef2RefFlowFunction_12) {
                        binaryMapToRefFlowFunction_22.input2Updated(mapRef2RefFlowFunction_12);
                    }
                }
                if (guardCheck_mapRef2RefFlowFunction_14()) {
                    isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
                    if (isDirty_mapRef2RefFlowFunction_14) {
                        binaryMapToRefFlowFunction_18.inputUpdated(mapRef2RefFlowFunction_14);
                    }
                }
                if (guardCheck_mapRef2RefFlowFunction_16()) {
                    isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
                    if (isDirty_mapRef2RefFlowFunction_16) {
                        binaryMapToRefFlowFunction_18.input2Updated(mapRef2RefFlowFunction_16);
                    }
                }
                if (guardCheck_binaryMapToRefFlowFunction_18()) {
                    isDirty_binaryMapToRefFlowFunction_18 = binaryMapToRefFlowFunction_18.map();
                    if (isDirty_binaryMapToRefFlowFunction_18) {
                        mapRef2RefFlowFunction_20.inputUpdated(binaryMapToRefFlowFunction_18);
                    }
                }
                if (guardCheck_mapRef2RefFlowFunction_20()) {
                    isDirty_mapRef2RefFlowFunction_20 = mapRef2RefFlowFunction_20.map();
                    if (isDirty_mapRef2RefFlowFunction_20) {
                        binaryMapToRefFlowFunction_22.inputUpdated(mapRef2RefFlowFunction_20);
                    }
                }
                if (guardCheck_binaryMapToRefFlowFunction_22()) {
                    isDirty_binaryMapToRefFlowFunction_22 = binaryMapToRefFlowFunction_22.map();
                    if (isDirty_binaryMapToRefFlowFunction_22) {
                        mapRef2RefFlowFunction_24.inputUpdated(binaryMapToRefFlowFunction_22);
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
                        binaryMapToRefFlowFunction_31.input2Updated(mapRef2RefFlowFunction_26);
                        binaryMapToRefFlowFunction_35.inputUpdated(mapRef2RefFlowFunction_26);
                        mapRef2RefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_26);
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
                        mapRef2RefFlowFunction_47.inputUpdated(mapRef2RefFlowFunction_33);
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
                        mapRef2RefFlowFunction_51.inputUpdated(mapRef2RefFlowFunction_37);
                        pnl.inputUpdated(mapRef2RefFlowFunction_37);
                    }
                }
                if (guardCheck_mapRef2RefFlowFunction_42()) {
                    isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
                    if (isDirty_mapRef2RefFlowFunction_42) {
                        positionMap.inputUpdated(mapRef2RefFlowFunction_42);
                    }
                }
                if (guardCheck_mapRef2RefFlowFunction_47()) {
                    isDirty_mapRef2RefFlowFunction_47 = mapRef2RefFlowFunction_47.map();
                    if (isDirty_mapRef2RefFlowFunction_47) {
                        rates.inputUpdated(mapRef2RefFlowFunction_47);
                    }
                }
                if (guardCheck_mapRef2RefFlowFunction_51()) {
                    isDirty_mapRef2RefFlowFunction_51 = mapRef2RefFlowFunction_51.map();
                    if (isDirty_mapRef2RefFlowFunction_51) {
                        mtmPositionMap.inputUpdated(mapRef2RefFlowFunction_51);
                    }
                }
                if (guardCheck_mapRef2RefFlowFunction_8()) {
                    isDirty_mapRef2RefFlowFunction_8 = mapRef2RefFlowFunction_8.map();
                    if (isDirty_mapRef2RefFlowFunction_8) {
                        tradeFees.inputUpdated(mapRef2RefFlowFunction_8);
                    }
                }
                if (guardCheck_mtmPositionMap()) {
                    isDirty_mtmPositionMap = mtmPositionMap.map();
                    if (isDirty_mtmPositionMap) {
                        pushFlowFunction_53.inputUpdated(mtmPositionMap);
                    }
                }
                if (guardCheck_pnl()) {
                    isDirty_pnl = pnl.map();
                    if (isDirty_pnl) {
                        filterFlowFunction_54.inputUpdated(pnl);
                        netPnl.inputUpdated(pnl);
                    }
                }
                if (guardCheck_filterFlowFunction_54()) {
                    isDirty_filterFlowFunction_54 = filterFlowFunction_54.filter();
                    if (isDirty_filterFlowFunction_54) {
                        pushFlowFunction_55.inputUpdated(filterFlowFunction_54);
                    }
                }
                if (guardCheck_positionMap()) {
                    isDirty_positionMap = positionMap.map();
                    if (isDirty_positionMap) {
                        pushFlowFunction_44.inputUpdated(positionMap);
                    }
                }
                if (guardCheck_pushFlowFunction_44()) {
                    isDirty_pushFlowFunction_44 = pushFlowFunction_44.push();
                }
                if (guardCheck_pushFlowFunction_53()) {
                    isDirty_pushFlowFunction_53 = pushFlowFunction_53.push();
                }
                if (guardCheck_pushFlowFunction_55()) {
                    isDirty_pushFlowFunction_55 = pushFlowFunction_55.push();
                }
                if (guardCheck_rates()) {
                    isDirty_rates = rates.map();
                    if (isDirty_rates) {
                        pushFlowFunction_49.inputUpdated(rates);
                    }
                }
                if (guardCheck_pushFlowFunction_49()) {
                    isDirty_pushFlowFunction_49 = pushFlowFunction_49.push();
                }
                if (guardCheck_tradeFees()) {
                    isDirty_tradeFees = tradeFees.map();
                    if (isDirty_tradeFees) {
                        netPnl.input2Updated(tradeFees);
                        pushFlowFunction_45.inputUpdated(tradeFees);
                    }
                }
                if (guardCheck_netPnl()) {
                    isDirty_netPnl = netPnl.map();
                    if (isDirty_netPnl) {
                        filterFlowFunction_56.inputUpdated(netPnl);
                    }
                }
                if (guardCheck_filterFlowFunction_56()) {
                    isDirty_filterFlowFunction_56 = filterFlowFunction_56.filter();
                    if (isDirty_filterFlowFunction_56) {
                        pushFlowFunction_57.inputUpdated(filterFlowFunction_56);
                    }
                }
                if (guardCheck_pushFlowFunction_45()) {
                    isDirty_pushFlowFunction_45 = pushFlowFunction_45.push();
                }
                if (guardCheck_pushFlowFunction_57()) {
                    isDirty_pushFlowFunction_57 = pushFlowFunction_57.push();
                }
                afterEvent();
                return;
            //Event Class:[com.fluxtion.runtime.event.Signal] filterString:[positionUpdate]
            case ("positionUpdate"):
                isDirty_handlerSignal_positionUpdate = handlerSignal_positionUpdate.onEvent(typedEvent);
                if (isDirty_handlerSignal_positionUpdate) {
                    mapRef2RefFlowFunction_12.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
                    binaryMapToRefFlowFunction_18.publishTriggerOverrideNodeUpdated(
                            handlerSignal_positionUpdate);
                    mapRef2RefFlowFunction_26.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
                    mapRef2RefFlowFunction_37.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
                    pnl.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
                    tradeFees.publishTriggerOverrideNodeUpdated(handlerSignal_positionUpdate);
                    netPnl.updateTriggerNodeUpdated(handlerSignal_positionUpdate);
                }
                if (guardCheck_mapRef2RefFlowFunction_12()) {
                    isDirty_mapRef2RefFlowFunction_12 = mapRef2RefFlowFunction_12.map();
                    if (isDirty_mapRef2RefFlowFunction_12) {
                        binaryMapToRefFlowFunction_22.input2Updated(mapRef2RefFlowFunction_12);
                    }
                }
                if (guardCheck_binaryMapToRefFlowFunction_18()) {
                    isDirty_binaryMapToRefFlowFunction_18 = binaryMapToRefFlowFunction_18.map();
                    if (isDirty_binaryMapToRefFlowFunction_18) {
                        mapRef2RefFlowFunction_20.inputUpdated(binaryMapToRefFlowFunction_18);
                    }
                }
                if (guardCheck_mapRef2RefFlowFunction_20()) {
                    isDirty_mapRef2RefFlowFunction_20 = mapRef2RefFlowFunction_20.map();
                    if (isDirty_mapRef2RefFlowFunction_20) {
                        binaryMapToRefFlowFunction_22.inputUpdated(mapRef2RefFlowFunction_20);
                    }
                }
                if (guardCheck_binaryMapToRefFlowFunction_22()) {
                    isDirty_binaryMapToRefFlowFunction_22 = binaryMapToRefFlowFunction_22.map();
                    if (isDirty_binaryMapToRefFlowFunction_22) {
                        mapRef2RefFlowFunction_24.inputUpdated(binaryMapToRefFlowFunction_22);
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
                        binaryMapToRefFlowFunction_31.input2Updated(mapRef2RefFlowFunction_26);
                        binaryMapToRefFlowFunction_35.inputUpdated(mapRef2RefFlowFunction_26);
                        mapRef2RefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_26);
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
                        mapRef2RefFlowFunction_47.inputUpdated(mapRef2RefFlowFunction_33);
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
                        mapRef2RefFlowFunction_51.inputUpdated(mapRef2RefFlowFunction_37);
                        pnl.inputUpdated(mapRef2RefFlowFunction_37);
                    }
                }
                if (guardCheck_mapRef2RefFlowFunction_42()) {
                    isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
                    if (isDirty_mapRef2RefFlowFunction_42) {
                        positionMap.inputUpdated(mapRef2RefFlowFunction_42);
                    }
                }
                if (guardCheck_mapRef2RefFlowFunction_47()) {
                    isDirty_mapRef2RefFlowFunction_47 = mapRef2RefFlowFunction_47.map();
                    if (isDirty_mapRef2RefFlowFunction_47) {
                        rates.inputUpdated(mapRef2RefFlowFunction_47);
                    }
                }
                if (guardCheck_mapRef2RefFlowFunction_51()) {
                    isDirty_mapRef2RefFlowFunction_51 = mapRef2RefFlowFunction_51.map();
                    if (isDirty_mapRef2RefFlowFunction_51) {
                        mtmPositionMap.inputUpdated(mapRef2RefFlowFunction_51);
                    }
                }
                if (guardCheck_mtmPositionMap()) {
                    isDirty_mtmPositionMap = mtmPositionMap.map();
                    if (isDirty_mtmPositionMap) {
                        pushFlowFunction_53.inputUpdated(mtmPositionMap);
                    }
                }
                if (guardCheck_pnl()) {
                    isDirty_pnl = pnl.map();
                    if (isDirty_pnl) {
                        filterFlowFunction_54.inputUpdated(pnl);
                        netPnl.inputUpdated(pnl);
                    }
                }
                if (guardCheck_filterFlowFunction_54()) {
                    isDirty_filterFlowFunction_54 = filterFlowFunction_54.filter();
                    if (isDirty_filterFlowFunction_54) {
                        pushFlowFunction_55.inputUpdated(filterFlowFunction_54);
                    }
                }
                if (guardCheck_positionMap()) {
                    isDirty_positionMap = positionMap.map();
                    if (isDirty_positionMap) {
                        pushFlowFunction_44.inputUpdated(positionMap);
                    }
                }
                if (guardCheck_pushFlowFunction_44()) {
                    isDirty_pushFlowFunction_44 = pushFlowFunction_44.push();
                }
                if (guardCheck_pushFlowFunction_53()) {
                    isDirty_pushFlowFunction_53 = pushFlowFunction_53.push();
                }
                if (guardCheck_pushFlowFunction_55()) {
                    isDirty_pushFlowFunction_55 = pushFlowFunction_55.push();
                }
                if (guardCheck_rates()) {
                    isDirty_rates = rates.map();
                    if (isDirty_rates) {
                        pushFlowFunction_49.inputUpdated(rates);
                    }
                }
                if (guardCheck_pushFlowFunction_49()) {
                    isDirty_pushFlowFunction_49 = pushFlowFunction_49.push();
                }
                if (guardCheck_tradeFees()) {
                    isDirty_tradeFees = tradeFees.map();
                    if (isDirty_tradeFees) {
                        netPnl.input2Updated(tradeFees);
                        pushFlowFunction_45.inputUpdated(tradeFees);
                    }
                }
                if (guardCheck_netPnl()) {
                    isDirty_netPnl = netPnl.map();
                    if (isDirty_netPnl) {
                        filterFlowFunction_56.inputUpdated(netPnl);
                    }
                }
                if (guardCheck_filterFlowFunction_56()) {
                    isDirty_filterFlowFunction_56 = filterFlowFunction_56.filter();
                    if (isDirty_filterFlowFunction_56) {
                        pushFlowFunction_57.inputUpdated(filterFlowFunction_56);
                    }
                }
                if (guardCheck_pushFlowFunction_45()) {
                    isDirty_pushFlowFunction_45 = pushFlowFunction_45.push();
                }
                if (guardCheck_pushFlowFunction_57()) {
                    isDirty_pushFlowFunction_57 = pushFlowFunction_57.push();
                }
                afterEvent();
                return;
        }
        afterEvent();
    }

    public void handleEvent(SinkDeregister typedEvent) {
        auditEvent(typedEvent);
        switch (typedEvent.filterString()) {
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
        isDirty_handlerMidPrice = handlerMidPrice.onEvent(typedEvent);
        if (isDirty_handlerMidPrice) {
            filterFlowFunction_27.inputUpdated(handlerMidPrice);
        }
        if (guardCheck_filterFlowFunction_27()) {
            isDirty_filterFlowFunction_27 = filterFlowFunction_27.filter();
            if (isDirty_filterFlowFunction_27) {
                mapRef2RefFlowFunction_29.inputUpdated(filterFlowFunction_27);
            }
        }
        if (guardCheck_mapRef2RefFlowFunction_29()) {
            isDirty_mapRef2RefFlowFunction_29 = mapRef2RefFlowFunction_29.map();
            if (isDirty_mapRef2RefFlowFunction_29) {
                binaryMapToRefFlowFunction_31.inputUpdated(mapRef2RefFlowFunction_29);
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
                mapRef2RefFlowFunction_47.inputUpdated(mapRef2RefFlowFunction_33);
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
                mapRef2RefFlowFunction_51.inputUpdated(mapRef2RefFlowFunction_37);
                pnl.inputUpdated(mapRef2RefFlowFunction_37);
            }
        }
        if (guardCheck_mapRef2RefFlowFunction_47()) {
            isDirty_mapRef2RefFlowFunction_47 = mapRef2RefFlowFunction_47.map();
            if (isDirty_mapRef2RefFlowFunction_47) {
                rates.inputUpdated(mapRef2RefFlowFunction_47);
            }
        }
        if (guardCheck_mapRef2RefFlowFunction_51()) {
            isDirty_mapRef2RefFlowFunction_51 = mapRef2RefFlowFunction_51.map();
            if (isDirty_mapRef2RefFlowFunction_51) {
                mtmPositionMap.inputUpdated(mapRef2RefFlowFunction_51);
            }
        }
        if (guardCheck_mtmPositionMap()) {
            isDirty_mtmPositionMap = mtmPositionMap.map();
            if (isDirty_mtmPositionMap) {
                pushFlowFunction_53.inputUpdated(mtmPositionMap);
            }
        }
        if (guardCheck_pnl()) {
            isDirty_pnl = pnl.map();
            if (isDirty_pnl) {
                filterFlowFunction_54.inputUpdated(pnl);
                netPnl.inputUpdated(pnl);
            }
        }
        if (guardCheck_filterFlowFunction_54()) {
            isDirty_filterFlowFunction_54 = filterFlowFunction_54.filter();
            if (isDirty_filterFlowFunction_54) {
                pushFlowFunction_55.inputUpdated(filterFlowFunction_54);
            }
        }
        if (guardCheck_pushFlowFunction_53()) {
            isDirty_pushFlowFunction_53 = pushFlowFunction_53.push();
        }
        if (guardCheck_pushFlowFunction_55()) {
            isDirty_pushFlowFunction_55 = pushFlowFunction_55.push();
        }
        if (guardCheck_rates()) {
            isDirty_rates = rates.map();
            if (isDirty_rates) {
                pushFlowFunction_49.inputUpdated(rates);
            }
        }
        if (guardCheck_pushFlowFunction_49()) {
            isDirty_pushFlowFunction_49 = pushFlowFunction_49.push();
        }
        if (guardCheck_netPnl()) {
            isDirty_netPnl = netPnl.map();
            if (isDirty_netPnl) {
                filterFlowFunction_56.inputUpdated(netPnl);
            }
        }
        if (guardCheck_filterFlowFunction_56()) {
            isDirty_filterFlowFunction_56 = filterFlowFunction_56.filter();
            if (isDirty_filterFlowFunction_56) {
                pushFlowFunction_57.inputUpdated(filterFlowFunction_56);
            }
        }
        if (guardCheck_pushFlowFunction_57()) {
            isDirty_pushFlowFunction_57 = pushFlowFunction_57.push();
        }
        afterEvent();
    }

    public void handleEvent(PositionSnapshot typedEvent) {
        auditEvent(typedEvent);
        //Default, no filter methods
        isDirty_handlerPositionSnapshot = handlerPositionSnapshot.onEvent(typedEvent);
        if (isDirty_handlerPositionSnapshot) {
            flatMapFlowFunction_10.inputUpdatedAndFlatMap(handlerPositionSnapshot);
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
                mapRef2RefFlowFunction_3.inputUpdated(mergeFlowFunction_2);
                mapRef2RefFlowFunction_14.inputUpdated(mergeFlowFunction_2);
                mapRef2RefFlowFunction_16.inputUpdated(mergeFlowFunction_2);
            }
        }
        if (guardCheck_mapRef2RefFlowFunction_3()) {
            isDirty_mapRef2RefFlowFunction_3 = mapRef2RefFlowFunction_3.map();
            if (isDirty_mapRef2RefFlowFunction_3) {
                mergeFlowFunction_5.inputStreamUpdated(mapRef2RefFlowFunction_3);
            }
        }
        if (guardCheck_mapRef2RefFlowFunction_14()) {
            isDirty_mapRef2RefFlowFunction_14 = mapRef2RefFlowFunction_14.map();
            if (isDirty_mapRef2RefFlowFunction_14) {
                binaryMapToRefFlowFunction_18.inputUpdated(mapRef2RefFlowFunction_14);
            }
        }
        if (guardCheck_mapRef2RefFlowFunction_16()) {
            isDirty_mapRef2RefFlowFunction_16 = mapRef2RefFlowFunction_16.map();
            if (isDirty_mapRef2RefFlowFunction_16) {
                binaryMapToRefFlowFunction_18.input2Updated(mapRef2RefFlowFunction_16);
            }
        }
        if (guardCheck_binaryMapToRefFlowFunction_18()) {
            isDirty_binaryMapToRefFlowFunction_18 = binaryMapToRefFlowFunction_18.map();
            if (isDirty_binaryMapToRefFlowFunction_18) {
                mapRef2RefFlowFunction_20.inputUpdated(binaryMapToRefFlowFunction_18);
            }
        }
        if (guardCheck_mapRef2RefFlowFunction_20()) {
            isDirty_mapRef2RefFlowFunction_20 = mapRef2RefFlowFunction_20.map();
            if (isDirty_mapRef2RefFlowFunction_20) {
                binaryMapToRefFlowFunction_22.inputUpdated(mapRef2RefFlowFunction_20);
            }
        }
        if (guardCheck_binaryMapToRefFlowFunction_22()) {
            isDirty_binaryMapToRefFlowFunction_22 = binaryMapToRefFlowFunction_22.map();
            if (isDirty_binaryMapToRefFlowFunction_22) {
                mapRef2RefFlowFunction_24.inputUpdated(binaryMapToRefFlowFunction_22);
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
                binaryMapToRefFlowFunction_31.input2Updated(mapRef2RefFlowFunction_26);
                binaryMapToRefFlowFunction_35.inputUpdated(mapRef2RefFlowFunction_26);
                mapRef2RefFlowFunction_42.inputUpdated(mapRef2RefFlowFunction_26);
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
                mapRef2RefFlowFunction_47.inputUpdated(mapRef2RefFlowFunction_33);
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
                mapRef2RefFlowFunction_51.inputUpdated(mapRef2RefFlowFunction_37);
                pnl.inputUpdated(mapRef2RefFlowFunction_37);
            }
        }
        if (guardCheck_mapRef2RefFlowFunction_42()) {
            isDirty_mapRef2RefFlowFunction_42 = mapRef2RefFlowFunction_42.map();
            if (isDirty_mapRef2RefFlowFunction_42) {
                positionMap.inputUpdated(mapRef2RefFlowFunction_42);
            }
        }
        if (guardCheck_mapRef2RefFlowFunction_47()) {
            isDirty_mapRef2RefFlowFunction_47 = mapRef2RefFlowFunction_47.map();
            if (isDirty_mapRef2RefFlowFunction_47) {
                rates.inputUpdated(mapRef2RefFlowFunction_47);
            }
        }
        if (guardCheck_mapRef2RefFlowFunction_51()) {
            isDirty_mapRef2RefFlowFunction_51 = mapRef2RefFlowFunction_51.map();
            if (isDirty_mapRef2RefFlowFunction_51) {
                mtmPositionMap.inputUpdated(mapRef2RefFlowFunction_51);
            }
        }
        if (guardCheck_mergeFlowFunction_5()) {
            isDirty_mergeFlowFunction_5 = mergeFlowFunction_5.publishMerge();
            if (isDirty_mergeFlowFunction_5) {
                aggregateFlowFunctionWrapper_6.inputUpdated(mergeFlowFunction_5);
            }
        }
        if (guardCheck_aggregateFlowFunctionWrapper_6()) {
            isDirty_aggregateFlowFunctionWrapper_6 = aggregateFlowFunctionWrapper_6.map();
            if (isDirty_aggregateFlowFunctionWrapper_6) {
                mapRef2RefFlowFunction_8.inputUpdated(aggregateFlowFunctionWrapper_6);
            }
        }
        if (guardCheck_mapRef2RefFlowFunction_8()) {
            isDirty_mapRef2RefFlowFunction_8 = mapRef2RefFlowFunction_8.map();
            if (isDirty_mapRef2RefFlowFunction_8) {
                tradeFees.inputUpdated(mapRef2RefFlowFunction_8);
            }
        }
        if (guardCheck_mtmPositionMap()) {
            isDirty_mtmPositionMap = mtmPositionMap.map();
            if (isDirty_mtmPositionMap) {
                pushFlowFunction_53.inputUpdated(mtmPositionMap);
            }
        }
        if (guardCheck_pnl()) {
            isDirty_pnl = pnl.map();
            if (isDirty_pnl) {
                filterFlowFunction_54.inputUpdated(pnl);
                netPnl.inputUpdated(pnl);
            }
        }
        if (guardCheck_filterFlowFunction_54()) {
            isDirty_filterFlowFunction_54 = filterFlowFunction_54.filter();
            if (isDirty_filterFlowFunction_54) {
                pushFlowFunction_55.inputUpdated(filterFlowFunction_54);
            }
        }
        if (guardCheck_positionMap()) {
            isDirty_positionMap = positionMap.map();
            if (isDirty_positionMap) {
                pushFlowFunction_44.inputUpdated(positionMap);
            }
        }
        if (guardCheck_pushFlowFunction_44()) {
            isDirty_pushFlowFunction_44 = pushFlowFunction_44.push();
        }
        if (guardCheck_pushFlowFunction_53()) {
            isDirty_pushFlowFunction_53 = pushFlowFunction_53.push();
        }
        if (guardCheck_pushFlowFunction_55()) {
            isDirty_pushFlowFunction_55 = pushFlowFunction_55.push();
        }
        if (guardCheck_rates()) {
            isDirty_rates = rates.map();
            if (isDirty_rates) {
                pushFlowFunction_49.inputUpdated(rates);
            }
        }
        if (guardCheck_pushFlowFunction_49()) {
            isDirty_pushFlowFunction_49 = pushFlowFunction_49.push();
        }
        if (guardCheck_tradeFees()) {
            isDirty_tradeFees = tradeFees.map();
            if (isDirty_tradeFees) {
                netPnl.input2Updated(tradeFees);
                pushFlowFunction_45.inputUpdated(tradeFees);
            }
        }
        if (guardCheck_netPnl()) {
            isDirty_netPnl = netPnl.map();
            if (isDirty_netPnl) {
                filterFlowFunction_56.inputUpdated(netPnl);
            }
        }
        if (guardCheck_filterFlowFunction_56()) {
            isDirty_filterFlowFunction_56 = filterFlowFunction_56.filter();
            if (isDirty_filterFlowFunction_56) {
                pushFlowFunction_57.inputUpdated(filterFlowFunction_56);
            }
        }
        if (guardCheck_pushFlowFunction_45()) {
            isDirty_pushFlowFunction_45 = pushFlowFunction_45.push();
        }
        if (guardCheck_pushFlowFunction_57()) {
            isDirty_pushFlowFunction_57 = pushFlowFunction_57.push();
        }
        afterEvent();
    }

    public void handleEvent(TradeBatch typedEvent) {
        auditEvent(typedEvent);
        //Default, no filter methods
        isDirty_handlerTradeBatch = handlerTradeBatch.onEvent(typedEvent);
        if (isDirty_handlerTradeBatch) {
            flatMapFlowFunction_1.inputUpdatedAndFlatMap(handlerTradeBatch);
            mapRef2RefFlowFunction_4.inputUpdated(handlerTradeBatch);
        }
        if (guardCheck_mapRef2RefFlowFunction_4()) {
            isDirty_mapRef2RefFlowFunction_4 = mapRef2RefFlowFunction_4.map();
            if (isDirty_mapRef2RefFlowFunction_4) {
                mergeFlowFunction_5.inputStreamUpdated(mapRef2RefFlowFunction_4);
            }
        }
        if (guardCheck_mergeFlowFunction_5()) {
            isDirty_mergeFlowFunction_5 = mergeFlowFunction_5.publishMerge();
            if (isDirty_mergeFlowFunction_5) {
                aggregateFlowFunctionWrapper_6.inputUpdated(mergeFlowFunction_5);
            }
        }
        if (guardCheck_aggregateFlowFunctionWrapper_6()) {
            isDirty_aggregateFlowFunctionWrapper_6 = aggregateFlowFunctionWrapper_6.map();
            if (isDirty_aggregateFlowFunctionWrapper_6) {
                mapRef2RefFlowFunction_8.inputUpdated(aggregateFlowFunctionWrapper_6);
            }
        }
        if (guardCheck_mapRef2RefFlowFunction_8()) {
            isDirty_mapRef2RefFlowFunction_8 = mapRef2RefFlowFunction_8.map();
            if (isDirty_mapRef2RefFlowFunction_8) {
                tradeFees.inputUpdated(mapRef2RefFlowFunction_8);
            }
        }
        if (guardCheck_tradeFees()) {
            isDirty_tradeFees = tradeFees.map();
            if (isDirty_tradeFees) {
                netPnl.input2Updated(tradeFees);
                pushFlowFunction_45.inputUpdated(tradeFees);
            }
        }
        if (guardCheck_netPnl()) {
            isDirty_netPnl = netPnl.map();
            if (isDirty_netPnl) {
                filterFlowFunction_56.inputUpdated(netPnl);
            }
        }
        if (guardCheck_filterFlowFunction_56()) {
            isDirty_filterFlowFunction_56 = filterFlowFunction_56.filter();
            if (isDirty_filterFlowFunction_56) {
                pushFlowFunction_57.inputUpdated(filterFlowFunction_56);
            }
        }
        if (guardCheck_pushFlowFunction_45()) {
            isDirty_pushFlowFunction_45 = pushFlowFunction_45.push();
        }
        if (guardCheck_pushFlowFunction_57()) {
            isDirty_pushFlowFunction_57 = pushFlowFunction_57.push();
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
        auditor.nodeRegistered(callbackImpl_58, "callbackImpl_58");
        auditor.nodeRegistered(callbackImpl_62, "callbackImpl_62");
        auditor.nodeRegistered(aggregateFlowFunctionWrapper_6, "aggregateFlowFunctionWrapper_6");
        auditor.nodeRegistered(doubleSumFlowFunction_159, "doubleSumFlowFunction_159");
        auditor.nodeRegistered(binaryMapToRefFlowFunction_18, "binaryMapToRefFlowFunction_18");
        auditor.nodeRegistered(binaryMapToRefFlowFunction_22, "binaryMapToRefFlowFunction_22");
        auditor.nodeRegistered(binaryMapToRefFlowFunction_31, "binaryMapToRefFlowFunction_31");
        auditor.nodeRegistered(binaryMapToRefFlowFunction_35, "binaryMapToRefFlowFunction_35");
        auditor.nodeRegistered(netPnl, "netPnl");
        auditor.nodeRegistered(filterFlowFunction_27, "filterFlowFunction_27");
        auditor.nodeRegistered(filterFlowFunction_54, "filterFlowFunction_54");
        auditor.nodeRegistered(filterFlowFunction_56, "filterFlowFunction_56");
        auditor.nodeRegistered(flatMapFlowFunction_1, "flatMapFlowFunction_1");
        auditor.nodeRegistered(flatMapFlowFunction_10, "flatMapFlowFunction_10");
        auditor.nodeRegistered(mapRef2RefFlowFunction_3, "mapRef2RefFlowFunction_3");
        auditor.nodeRegistered(mapRef2RefFlowFunction_4, "mapRef2RefFlowFunction_4");
        auditor.nodeRegistered(mapRef2RefFlowFunction_8, "mapRef2RefFlowFunction_8");
        auditor.nodeRegistered(mapRef2RefFlowFunction_12, "mapRef2RefFlowFunction_12");
        auditor.nodeRegistered(mapRef2RefFlowFunction_14, "mapRef2RefFlowFunction_14");
        auditor.nodeRegistered(mapRef2RefFlowFunction_16, "mapRef2RefFlowFunction_16");
        auditor.nodeRegistered(mapRef2RefFlowFunction_20, "mapRef2RefFlowFunction_20");
        auditor.nodeRegistered(mapRef2RefFlowFunction_24, "mapRef2RefFlowFunction_24");
        auditor.nodeRegistered(mapRef2RefFlowFunction_26, "mapRef2RefFlowFunction_26");
        auditor.nodeRegistered(mapRef2RefFlowFunction_29, "mapRef2RefFlowFunction_29");
        auditor.nodeRegistered(mapRef2RefFlowFunction_33, "mapRef2RefFlowFunction_33");
        auditor.nodeRegistered(mapRef2RefFlowFunction_37, "mapRef2RefFlowFunction_37");
        auditor.nodeRegistered(mapRef2RefFlowFunction_42, "mapRef2RefFlowFunction_42");
        auditor.nodeRegistered(mapRef2RefFlowFunction_47, "mapRef2RefFlowFunction_47");
        auditor.nodeRegistered(mapRef2RefFlowFunction_51, "mapRef2RefFlowFunction_51");
        auditor.nodeRegistered(mtmPositionMap, "mtmPositionMap");
        auditor.nodeRegistered(pnl, "pnl");
        auditor.nodeRegistered(positionMap, "positionMap");
        auditor.nodeRegistered(rates, "rates");
        auditor.nodeRegistered(tradeFees, "tradeFees");
        auditor.nodeRegistered(mergeFlowFunction_2, "mergeFlowFunction_2");
        auditor.nodeRegistered(mergeFlowFunction_5, "mergeFlowFunction_5");
        auditor.nodeRegistered(pushFlowFunction_44, "pushFlowFunction_44");
        auditor.nodeRegistered(pushFlowFunction_45, "pushFlowFunction_45");
        auditor.nodeRegistered(pushFlowFunction_49, "pushFlowFunction_49");
        auditor.nodeRegistered(pushFlowFunction_53, "pushFlowFunction_53");
        auditor.nodeRegistered(pushFlowFunction_55, "pushFlowFunction_55");
        auditor.nodeRegistered(pushFlowFunction_57, "pushFlowFunction_57");
        auditor.nodeRegistered(emptyGroupBy_86, "emptyGroupBy_86");
        auditor.nodeRegistered(emptyGroupBy_106, "emptyGroupBy_106");
        auditor.nodeRegistered(groupByFlowFunctionWrapper_11, "groupByFlowFunctionWrapper_11");
        auditor.nodeRegistered(groupByFlowFunctionWrapper_13, "groupByFlowFunctionWrapper_13");
        auditor.nodeRegistered(groupByFlowFunctionWrapper_15, "groupByFlowFunctionWrapper_15");
        auditor.nodeRegistered(groupByFlowFunctionWrapper_28, "groupByFlowFunctionWrapper_28");
        auditor.nodeRegistered(groupByMapFlowFunction_19, "groupByMapFlowFunction_19");
        auditor.nodeRegistered(groupByMapFlowFunction_23, "groupByMapFlowFunction_23");
        auditor.nodeRegistered(groupByMapFlowFunction_36, "groupByMapFlowFunction_36");
        auditor.nodeRegistered(groupByMapFlowFunction_41, "groupByMapFlowFunction_41");
        auditor.nodeRegistered(groupByMapFlowFunction_46, "groupByMapFlowFunction_46");
        auditor.nodeRegistered(groupByMapFlowFunction_50, "groupByMapFlowFunction_50");
        auditor.nodeRegistered(groupByReduceFlowFunction_38, "groupByReduceFlowFunction_38");
        auditor.nodeRegistered(leftJoin_34, "leftJoin_34");
        auditor.nodeRegistered(outerJoin_17, "outerJoin_17");
        auditor.nodeRegistered(outerJoin_21, "outerJoin_21");
        auditor.nodeRegistered(defaultValue_7, "defaultValue_7");
        auditor.nodeRegistered(defaultValue_25, "defaultValue_25");
        auditor.nodeRegistered(defaultValue_32, "defaultValue_32");
        auditor.nodeRegistered(subscriptionManager, "subscriptionManager");
        auditor.nodeRegistered(handlerMidPrice, "handlerMidPrice");
        auditor.nodeRegistered(handlerPositionSnapshot, "handlerPositionSnapshot");
        auditor.nodeRegistered(
                handlerSignal_positionSnapshotReset, "handlerSignal_positionSnapshotReset");
        auditor.nodeRegistered(handlerSignal_positionUpdate, "handlerSignal_positionUpdate");
        auditor.nodeRegistered(handlerTrade, "handlerTrade");
        auditor.nodeRegistered(handlerTradeBatch, "handlerTradeBatch");
        auditor.nodeRegistered(context, "context");
        auditor.nodeRegistered(mtmPositionListener, "mtmPositionListener");
        auditor.nodeRegistered(netPnlListener, "netPnlListener");
        auditor.nodeRegistered(pnlListener, "pnlListener");
        auditor.nodeRegistered(positionListener, "positionListener");
        auditor.nodeRegistered(rateListener, "rateListener");
        auditor.nodeRegistered(tradeFeesListener, "tradeFeesListener");
        auditor.nodeRegistered(derivedRateNode_30, "derivedRateNode_30");
        auditor.nodeRegistered(symbolLookupNode_0, "symbolLookupNode_0");
        auditor.nodeRegistered(double_59, "double_59");
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
        isDirty_aggregateFlowFunctionWrapper_6 = false;
        isDirty_binaryMapToRefFlowFunction_18 = false;
        isDirty_binaryMapToRefFlowFunction_22 = false;
        isDirty_binaryMapToRefFlowFunction_31 = false;
        isDirty_binaryMapToRefFlowFunction_35 = false;
        isDirty_callbackImpl_58 = false;
        isDirty_callbackImpl_62 = false;
        isDirty_clock = false;
        isDirty_filterFlowFunction_27 = false;
        isDirty_filterFlowFunction_54 = false;
        isDirty_filterFlowFunction_56 = false;
        isDirty_flatMapFlowFunction_1 = false;
        isDirty_flatMapFlowFunction_10 = false;
        isDirty_handlerMidPrice = false;
        isDirty_handlerPositionSnapshot = false;
        isDirty_handlerSignal_positionSnapshotReset = false;
        isDirty_handlerSignal_positionUpdate = false;
        isDirty_handlerTrade = false;
        isDirty_handlerTradeBatch = false;
        isDirty_mapRef2RefFlowFunction_3 = false;
        isDirty_mapRef2RefFlowFunction_4 = false;
        isDirty_mapRef2RefFlowFunction_8 = false;
        isDirty_mapRef2RefFlowFunction_12 = false;
        isDirty_mapRef2RefFlowFunction_14 = false;
        isDirty_mapRef2RefFlowFunction_16 = false;
        isDirty_mapRef2RefFlowFunction_20 = false;
        isDirty_mapRef2RefFlowFunction_24 = false;
        isDirty_mapRef2RefFlowFunction_26 = false;
        isDirty_mapRef2RefFlowFunction_29 = false;
        isDirty_mapRef2RefFlowFunction_33 = false;
        isDirty_mapRef2RefFlowFunction_37 = false;
        isDirty_mapRef2RefFlowFunction_42 = false;
        isDirty_mapRef2RefFlowFunction_47 = false;
        isDirty_mapRef2RefFlowFunction_51 = false;
        isDirty_mergeFlowFunction_2 = false;
        isDirty_mergeFlowFunction_5 = false;
        isDirty_mtmPositionMap = false;
        isDirty_netPnl = false;
        isDirty_pnl = false;
        isDirty_positionMap = false;
        isDirty_pushFlowFunction_44 = false;
        isDirty_pushFlowFunction_45 = false;
        isDirty_pushFlowFunction_49 = false;
        isDirty_pushFlowFunction_53 = false;
        isDirty_pushFlowFunction_55 = false;
        isDirty_pushFlowFunction_57 = false;
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
                    aggregateFlowFunctionWrapper_6, () -> isDirty_aggregateFlowFunctionWrapper_6);
            dirtyFlagSupplierMap.put(
                    binaryMapToRefFlowFunction_18, () -> isDirty_binaryMapToRefFlowFunction_18);
            dirtyFlagSupplierMap.put(
                    binaryMapToRefFlowFunction_22, () -> isDirty_binaryMapToRefFlowFunction_22);
            dirtyFlagSupplierMap.put(
                    binaryMapToRefFlowFunction_31, () -> isDirty_binaryMapToRefFlowFunction_31);
            dirtyFlagSupplierMap.put(
                    binaryMapToRefFlowFunction_35, () -> isDirty_binaryMapToRefFlowFunction_35);
            dirtyFlagSupplierMap.put(callbackImpl_58, () -> isDirty_callbackImpl_58);
            dirtyFlagSupplierMap.put(callbackImpl_62, () -> isDirty_callbackImpl_62);
            dirtyFlagSupplierMap.put(clock, () -> isDirty_clock);
            dirtyFlagSupplierMap.put(filterFlowFunction_27, () -> isDirty_filterFlowFunction_27);
            dirtyFlagSupplierMap.put(filterFlowFunction_54, () -> isDirty_filterFlowFunction_54);
            dirtyFlagSupplierMap.put(filterFlowFunction_56, () -> isDirty_filterFlowFunction_56);
            dirtyFlagSupplierMap.put(flatMapFlowFunction_1, () -> isDirty_flatMapFlowFunction_1);
            dirtyFlagSupplierMap.put(flatMapFlowFunction_10, () -> isDirty_flatMapFlowFunction_10);
            dirtyFlagSupplierMap.put(handlerMidPrice, () -> isDirty_handlerMidPrice);
            dirtyFlagSupplierMap.put(handlerPositionSnapshot, () -> isDirty_handlerPositionSnapshot);
            dirtyFlagSupplierMap.put(
                    handlerSignal_positionSnapshotReset, () -> isDirty_handlerSignal_positionSnapshotReset);
            dirtyFlagSupplierMap.put(
                    handlerSignal_positionUpdate, () -> isDirty_handlerSignal_positionUpdate);
            dirtyFlagSupplierMap.put(handlerTrade, () -> isDirty_handlerTrade);
            dirtyFlagSupplierMap.put(handlerTradeBatch, () -> isDirty_handlerTradeBatch);
            dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_12, () -> isDirty_mapRef2RefFlowFunction_12);
            dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_14, () -> isDirty_mapRef2RefFlowFunction_14);
            dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_16, () -> isDirty_mapRef2RefFlowFunction_16);
            dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_20, () -> isDirty_mapRef2RefFlowFunction_20);
            dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_24, () -> isDirty_mapRef2RefFlowFunction_24);
            dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_26, () -> isDirty_mapRef2RefFlowFunction_26);
            dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_29, () -> isDirty_mapRef2RefFlowFunction_29);
            dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_3, () -> isDirty_mapRef2RefFlowFunction_3);
            dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_33, () -> isDirty_mapRef2RefFlowFunction_33);
            dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_37, () -> isDirty_mapRef2RefFlowFunction_37);
            dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_4, () -> isDirty_mapRef2RefFlowFunction_4);
            dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_42, () -> isDirty_mapRef2RefFlowFunction_42);
            dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_47, () -> isDirty_mapRef2RefFlowFunction_47);
            dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_51, () -> isDirty_mapRef2RefFlowFunction_51);
            dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_8, () -> isDirty_mapRef2RefFlowFunction_8);
            dirtyFlagSupplierMap.put(mergeFlowFunction_2, () -> isDirty_mergeFlowFunction_2);
            dirtyFlagSupplierMap.put(mergeFlowFunction_5, () -> isDirty_mergeFlowFunction_5);
            dirtyFlagSupplierMap.put(mtmPositionMap, () -> isDirty_mtmPositionMap);
            dirtyFlagSupplierMap.put(netPnl, () -> isDirty_netPnl);
            dirtyFlagSupplierMap.put(pnl, () -> isDirty_pnl);
            dirtyFlagSupplierMap.put(positionMap, () -> isDirty_positionMap);
            dirtyFlagSupplierMap.put(pushFlowFunction_44, () -> isDirty_pushFlowFunction_44);
            dirtyFlagSupplierMap.put(pushFlowFunction_45, () -> isDirty_pushFlowFunction_45);
            dirtyFlagSupplierMap.put(pushFlowFunction_49, () -> isDirty_pushFlowFunction_49);
            dirtyFlagSupplierMap.put(pushFlowFunction_53, () -> isDirty_pushFlowFunction_53);
            dirtyFlagSupplierMap.put(pushFlowFunction_55, () -> isDirty_pushFlowFunction_55);
            dirtyFlagSupplierMap.put(pushFlowFunction_57, () -> isDirty_pushFlowFunction_57);
            dirtyFlagSupplierMap.put(rates, () -> isDirty_rates);
            dirtyFlagSupplierMap.put(tradeFees, () -> isDirty_tradeFees);
        }
        return dirtyFlagSupplierMap.getOrDefault(node, StaticEventProcessor.ALWAYS_FALSE);
    }

    @Override
    public void setDirty(Object node, boolean dirtyFlag) {
        if (dirtyFlagUpdateMap.isEmpty()) {
            dirtyFlagUpdateMap.put(
                    aggregateFlowFunctionWrapper_6, (b) -> isDirty_aggregateFlowFunctionWrapper_6 = b);
            dirtyFlagUpdateMap.put(
                    binaryMapToRefFlowFunction_18, (b) -> isDirty_binaryMapToRefFlowFunction_18 = b);
            dirtyFlagUpdateMap.put(
                    binaryMapToRefFlowFunction_22, (b) -> isDirty_binaryMapToRefFlowFunction_22 = b);
            dirtyFlagUpdateMap.put(
                    binaryMapToRefFlowFunction_31, (b) -> isDirty_binaryMapToRefFlowFunction_31 = b);
            dirtyFlagUpdateMap.put(
                    binaryMapToRefFlowFunction_35, (b) -> isDirty_binaryMapToRefFlowFunction_35 = b);
            dirtyFlagUpdateMap.put(callbackImpl_58, (b) -> isDirty_callbackImpl_58 = b);
            dirtyFlagUpdateMap.put(callbackImpl_62, (b) -> isDirty_callbackImpl_62 = b);
            dirtyFlagUpdateMap.put(clock, (b) -> isDirty_clock = b);
            dirtyFlagUpdateMap.put(filterFlowFunction_27, (b) -> isDirty_filterFlowFunction_27 = b);
            dirtyFlagUpdateMap.put(filterFlowFunction_54, (b) -> isDirty_filterFlowFunction_54 = b);
            dirtyFlagUpdateMap.put(filterFlowFunction_56, (b) -> isDirty_filterFlowFunction_56 = b);
            dirtyFlagUpdateMap.put(flatMapFlowFunction_1, (b) -> isDirty_flatMapFlowFunction_1 = b);
            dirtyFlagUpdateMap.put(flatMapFlowFunction_10, (b) -> isDirty_flatMapFlowFunction_10 = b);
            dirtyFlagUpdateMap.put(handlerMidPrice, (b) -> isDirty_handlerMidPrice = b);
            dirtyFlagUpdateMap.put(handlerPositionSnapshot, (b) -> isDirty_handlerPositionSnapshot = b);
            dirtyFlagUpdateMap.put(
                    handlerSignal_positionSnapshotReset,
                    (b) -> isDirty_handlerSignal_positionSnapshotReset = b);
            dirtyFlagUpdateMap.put(
                    handlerSignal_positionUpdate, (b) -> isDirty_handlerSignal_positionUpdate = b);
            dirtyFlagUpdateMap.put(handlerTrade, (b) -> isDirty_handlerTrade = b);
            dirtyFlagUpdateMap.put(handlerTradeBatch, (b) -> isDirty_handlerTradeBatch = b);
            dirtyFlagUpdateMap.put(
                    mapRef2RefFlowFunction_12, (b) -> isDirty_mapRef2RefFlowFunction_12 = b);
            dirtyFlagUpdateMap.put(
                    mapRef2RefFlowFunction_14, (b) -> isDirty_mapRef2RefFlowFunction_14 = b);
            dirtyFlagUpdateMap.put(
                    mapRef2RefFlowFunction_16, (b) -> isDirty_mapRef2RefFlowFunction_16 = b);
            dirtyFlagUpdateMap.put(
                    mapRef2RefFlowFunction_20, (b) -> isDirty_mapRef2RefFlowFunction_20 = b);
            dirtyFlagUpdateMap.put(
                    mapRef2RefFlowFunction_24, (b) -> isDirty_mapRef2RefFlowFunction_24 = b);
            dirtyFlagUpdateMap.put(
                    mapRef2RefFlowFunction_26, (b) -> isDirty_mapRef2RefFlowFunction_26 = b);
            dirtyFlagUpdateMap.put(
                    mapRef2RefFlowFunction_29, (b) -> isDirty_mapRef2RefFlowFunction_29 = b);
            dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_3, (b) -> isDirty_mapRef2RefFlowFunction_3 = b);
            dirtyFlagUpdateMap.put(
                    mapRef2RefFlowFunction_33, (b) -> isDirty_mapRef2RefFlowFunction_33 = b);
            dirtyFlagUpdateMap.put(
                    mapRef2RefFlowFunction_37, (b) -> isDirty_mapRef2RefFlowFunction_37 = b);
            dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_4, (b) -> isDirty_mapRef2RefFlowFunction_4 = b);
            dirtyFlagUpdateMap.put(
                    mapRef2RefFlowFunction_42, (b) -> isDirty_mapRef2RefFlowFunction_42 = b);
            dirtyFlagUpdateMap.put(
                    mapRef2RefFlowFunction_47, (b) -> isDirty_mapRef2RefFlowFunction_47 = b);
            dirtyFlagUpdateMap.put(
                    mapRef2RefFlowFunction_51, (b) -> isDirty_mapRef2RefFlowFunction_51 = b);
            dirtyFlagUpdateMap.put(mapRef2RefFlowFunction_8, (b) -> isDirty_mapRef2RefFlowFunction_8 = b);
            dirtyFlagUpdateMap.put(mergeFlowFunction_2, (b) -> isDirty_mergeFlowFunction_2 = b);
            dirtyFlagUpdateMap.put(mergeFlowFunction_5, (b) -> isDirty_mergeFlowFunction_5 = b);
            dirtyFlagUpdateMap.put(mtmPositionMap, (b) -> isDirty_mtmPositionMap = b);
            dirtyFlagUpdateMap.put(netPnl, (b) -> isDirty_netPnl = b);
            dirtyFlagUpdateMap.put(pnl, (b) -> isDirty_pnl = b);
            dirtyFlagUpdateMap.put(positionMap, (b) -> isDirty_positionMap = b);
            dirtyFlagUpdateMap.put(pushFlowFunction_44, (b) -> isDirty_pushFlowFunction_44 = b);
            dirtyFlagUpdateMap.put(pushFlowFunction_45, (b) -> isDirty_pushFlowFunction_45 = b);
            dirtyFlagUpdateMap.put(pushFlowFunction_49, (b) -> isDirty_pushFlowFunction_49 = b);
            dirtyFlagUpdateMap.put(pushFlowFunction_53, (b) -> isDirty_pushFlowFunction_53 = b);
            dirtyFlagUpdateMap.put(pushFlowFunction_55, (b) -> isDirty_pushFlowFunction_55 = b);
            dirtyFlagUpdateMap.put(pushFlowFunction_57, (b) -> isDirty_pushFlowFunction_57 = b);
            dirtyFlagUpdateMap.put(rates, (b) -> isDirty_rates = b);
            dirtyFlagUpdateMap.put(tradeFees, (b) -> isDirty_tradeFees = b);
        }
        dirtyFlagUpdateMap.get(node).accept(dirtyFlag);
    }

    private boolean guardCheck_aggregateFlowFunctionWrapper_6() {
        return isDirty_mergeFlowFunction_5;
    }

    private boolean guardCheck_binaryMapToRefFlowFunction_18() {
        return isDirty_handlerSignal_positionUpdate
               | isDirty_mapRef2RefFlowFunction_14
               | isDirty_mapRef2RefFlowFunction_16;
    }

    private boolean guardCheck_binaryMapToRefFlowFunction_22() {
        return isDirty_mapRef2RefFlowFunction_12 | isDirty_mapRef2RefFlowFunction_20;
    }

    private boolean guardCheck_binaryMapToRefFlowFunction_31() {
        return isDirty_mapRef2RefFlowFunction_26 | isDirty_mapRef2RefFlowFunction_29;
    }

    private boolean guardCheck_binaryMapToRefFlowFunction_35() {
        return isDirty_mapRef2RefFlowFunction_26 | isDirty_mapRef2RefFlowFunction_33;
    }

    private boolean guardCheck_netPnl() {
        return isDirty_handlerSignal_positionUpdate | isDirty_pnl | isDirty_tradeFees;
    }

    private boolean guardCheck_filterFlowFunction_27() {
        return isDirty_handlerMidPrice;
    }

    private boolean guardCheck_filterFlowFunction_54() {
        return isDirty_pnl;
    }

    private boolean guardCheck_filterFlowFunction_56() {
        return isDirty_netPnl;
    }

    private boolean guardCheck_flatMapFlowFunction_1() {
        return isDirty_callbackImpl_58;
    }

    private boolean guardCheck_flatMapFlowFunction_10() {
        return isDirty_callbackImpl_62;
    }

    private boolean guardCheck_mapRef2RefFlowFunction_3() {
        return isDirty_mergeFlowFunction_2;
    }

    private boolean guardCheck_mapRef2RefFlowFunction_4() {
        return isDirty_handlerTradeBatch;
    }

    private boolean guardCheck_mapRef2RefFlowFunction_8() {
        return isDirty_aggregateFlowFunctionWrapper_6 | isDirty_handlerSignal_positionSnapshotReset;
    }

    private boolean guardCheck_mapRef2RefFlowFunction_12() {
        return isDirty_flatMapFlowFunction_10
               | isDirty_handlerSignal_positionSnapshotReset
               | isDirty_handlerSignal_positionUpdate;
    }

    private boolean guardCheck_mapRef2RefFlowFunction_14() {
        return isDirty_handlerSignal_positionSnapshotReset | isDirty_mergeFlowFunction_2;
    }

    private boolean guardCheck_mapRef2RefFlowFunction_16() {
        return isDirty_handlerSignal_positionSnapshotReset | isDirty_mergeFlowFunction_2;
    }

    private boolean guardCheck_mapRef2RefFlowFunction_20() {
        return isDirty_binaryMapToRefFlowFunction_18;
    }

    private boolean guardCheck_mapRef2RefFlowFunction_24() {
        return isDirty_binaryMapToRefFlowFunction_22;
    }

    private boolean guardCheck_mapRef2RefFlowFunction_26() {
        return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_24;
    }

    private boolean guardCheck_mapRef2RefFlowFunction_29() {
        return isDirty_filterFlowFunction_27;
    }

    private boolean guardCheck_mapRef2RefFlowFunction_33() {
        return isDirty_binaryMapToRefFlowFunction_31;
    }

    private boolean guardCheck_mapRef2RefFlowFunction_37() {
        return isDirty_binaryMapToRefFlowFunction_35 | isDirty_handlerSignal_positionUpdate;
    }

    private boolean guardCheck_mapRef2RefFlowFunction_42() {
        return isDirty_mapRef2RefFlowFunction_26;
    }

    private boolean guardCheck_mapRef2RefFlowFunction_47() {
        return isDirty_mapRef2RefFlowFunction_33;
    }

    private boolean guardCheck_mapRef2RefFlowFunction_51() {
        return isDirty_mapRef2RefFlowFunction_37;
    }

    private boolean guardCheck_mtmPositionMap() {
        return isDirty_mapRef2RefFlowFunction_51;
    }

    private boolean guardCheck_pnl() {
        return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_37;
    }

    private boolean guardCheck_positionMap() {
        return isDirty_mapRef2RefFlowFunction_42;
    }

    private boolean guardCheck_rates() {
        return isDirty_mapRef2RefFlowFunction_47;
    }

    private boolean guardCheck_tradeFees() {
        return isDirty_handlerSignal_positionUpdate | isDirty_mapRef2RefFlowFunction_8;
    }

    private boolean guardCheck_mergeFlowFunction_2() {
        return isDirty_flatMapFlowFunction_1 | isDirty_handlerTrade;
    }

    private boolean guardCheck_mergeFlowFunction_5() {
        return isDirty_mapRef2RefFlowFunction_3 | isDirty_mapRef2RefFlowFunction_4;
    }

    private boolean guardCheck_pushFlowFunction_44() {
        return isDirty_positionMap;
    }

    private boolean guardCheck_pushFlowFunction_45() {
        return isDirty_tradeFees;
    }

    private boolean guardCheck_pushFlowFunction_49() {
        return isDirty_rates;
    }

    private boolean guardCheck_pushFlowFunction_53() {
        return isDirty_mtmPositionMap;
    }

    private boolean guardCheck_pushFlowFunction_55() {
        return isDirty_filterFlowFunction_54;
    }

    private boolean guardCheck_pushFlowFunction_57() {
        return isDirty_filterFlowFunction_56;
    }

    private boolean guardCheck_context() {
        return isDirty_clock;
    }

    private boolean guardCheck_mtmPositionListener() {
        return isDirty_pushFlowFunction_53;
    }

    private boolean guardCheck_netPnlListener() {
        return isDirty_pushFlowFunction_57;
    }

    private boolean guardCheck_pnlListener() {
        return isDirty_pushFlowFunction_55;
    }

    private boolean guardCheck_positionListener() {
        return isDirty_pushFlowFunction_44;
    }

    private boolean guardCheck_rateListener() {
        return isDirty_pushFlowFunction_49;
    }

    private boolean guardCheck_tradeFeesListener() {
        return isDirty_pushFlowFunction_45;
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
