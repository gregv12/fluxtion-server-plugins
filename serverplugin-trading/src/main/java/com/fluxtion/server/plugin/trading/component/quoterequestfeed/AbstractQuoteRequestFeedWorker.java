package com.fluxtion.server.plugin.trading.component.quoterequestfeed;

import com.fluxtion.agrona.concurrent.Agent;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Agent-mounted variant of {@link AbstractQuoteRequestFeed} — the twin of
 * {@code AbstractMarketDataFeedWorker}.
 *
 * <p>Implementing {@link Agent} is what lets the feed be hosted in an agent group: a {@code services:}
 * entry with {@code agentGroup:} is turned into a {@code ServiceAgent} by
 * {@code ServiceConfig.toServiceAgent()}, which casts the instance to {@link Agent} — so a feed that is
 * only a {@code LifeCycleEventSource} throws {@code ClassCastException} at boot. As a mounted agent
 * member the feed also receives the group's {@code SchedulerService} via {@code @ServiceRegistered},
 * which is what makes {@link AbstractQuoteRequestFeed}'s self-rearming poll actually run (a bare service
 * never gets a scheduler, so it silently never polls).
 *
 * <p>The poll is scheduler-driven, so {@link #doWork()} has nothing to do each duty cycle — exactly as
 * {@code MockMarketDataFeedWorker.doWork()} returns 0.
 */
@Log4j2
@RequiredArgsConstructor
public abstract class AbstractQuoteRequestFeedWorker extends AbstractQuoteRequestFeed implements Agent {

    private final String roleName;

    @Override
    public String roleName() {
        return roleName;
    }

    @Override
    public int doWork() {
        return 0;   // polling is scheduler-driven; no per-duty-cycle work
    }
}
