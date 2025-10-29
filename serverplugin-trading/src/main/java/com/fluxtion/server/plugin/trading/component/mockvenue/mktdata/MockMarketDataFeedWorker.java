package com.fluxtion.server.plugin.trading.component.mockvenue.mktdata;

import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.server.plugin.trading.component.marketdatafeed.AbstractMarketDataFeedWorker;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketConnected;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketDataBook;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketFeedEvent;
import com.fluxtion.server.plugin.trading.service.marketdata.MultilevelMarketDataBook;
import com.fluxtion.server.service.scheduler.SchedulerService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

import java.util.List;

@Log4j2
public class MockMarketDataFeedWorker extends AbstractMarketDataFeedWorker {

    public static final String MOCK_MARKET_DATA_FEED = "mockMarketDataFeed";
    private SchedulerService schedulerService;
    private volatile MarketConnected marketConnected;
    @Getter
    @Setter
    private int publishRateMillis = 1000;
    @Getter
    @Setter
    private List<MarketDataBookConfig> marketDataBookConfigs = List.of();

    public MockMarketDataFeedWorker(String roleName) {
        super(roleName);
    }

    public MockMarketDataFeedWorker() {
        super(MOCK_MARKET_DATA_FEED);
    }

    @Override
    public void start() {
        super.start();
        log.info("Starting MockMarketDataFeedWorker publishRateMillis {}", publishRateMillis);
    }

    @ServiceRegistered
    public void scheduler(SchedulerService schedulerService) {
        log.info("Scheduler service started");
        this.schedulerService = schedulerService;
        marketConnected = new MarketConnected(getFeedName());
        this.schedulerService.scheduleAfterDelay(publishRateMillis, this::timerTriggered);
    }

    public void timerTriggered() {
        this.schedulerService.scheduleAfterDelay(publishRateMillis, this::timerTriggered);
        log.trace("timer triggered {}", schedulerService);

        if(marketConnected != null) {
            log.info("publish marketConnected {}", marketConnected);
            publish(marketConnected);
            marketConnected = null;
        }

        for (MarketDataBookConfig config : marketDataBookConfigs) {
            MarketFeedEvent marketFeedEvent;
            if (config.isMultilevel()) {
                marketFeedEvent = MarketDataBookGenerator.generateRandomMultilevel(
                        config,
                        config.getMultilevelDepth(),
                        config.getMultilevelBookConfig()
                );
            } else {
                marketFeedEvent = MarketDataBookGenerator.generateRandom(config);
            }

            if (marketFeedEvent != null) {
                log.debug("publishPrice:{}", marketFeedEvent);
                publish(marketFeedEvent);
            }
        }
    }

    @Override
    protected void subscribeToSymbol(String feedName, String venueName, String symbol) {
        log.info("service:{} subscribeToSymbol:{} venueName:{} feedName:{}", getServiceName(), symbol, venueName, feedName);
//        marketConnected = new MarketConnected(getFeedName());
    }

    @Override
    public int doWork() throws Exception {
        return 0;
    }
}
