package com.fluxtion.server.plugin.trading.component.quoteconfigfeed;

import com.fluxtion.agrona.concurrent.Agent;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Agent-mounted variant of {@link AbstractQuoteConfigFeed} — twin of
 * {@link com.fluxtion.server.plugin.trading.component.quoterequestfeed.AbstractQuoteRequestFeedWorker}.
 *
 * <p>Implementing {@link Agent} lets the feed be hosted in an agent group (a {@code services:} entry
 * with {@code agentGroup:} is cast to {@link Agent} by {@code ServiceConfig.toServiceAgent()}). As a
 * mounted member it receives the group's {@code SchedulerService} via {@code @ServiceRegistered}, which
 * is what makes {@link AbstractQuoteConfigFeed}'s self-rearming poll run — a bare service never gets a
 * scheduler and silently never polls.
 */
@Log4j2
@RequiredArgsConstructor
public abstract class AbstractQuoteConfigFeedWorker extends AbstractQuoteConfigFeed implements Agent {

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
