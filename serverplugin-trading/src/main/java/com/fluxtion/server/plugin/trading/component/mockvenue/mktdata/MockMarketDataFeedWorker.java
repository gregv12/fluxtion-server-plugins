package com.fluxtion.server.plugin.trading.component.mockvenue.mktdata;

import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.server.plugin.trading.component.marketdatafeed.AbstractMarketDataFeedWorker;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketConnected;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketDataBook;
import com.fluxtion.server.service.scheduler.SchedulerService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

@Log4j2
public class MockMarketDataFeedWorker extends AbstractMarketDataFeedWorker {

    public static final String MOCK_MARKET_DATA_FEED = "mockMarketDataFeed";
    private static final Random RANDOM = new Random();
    private final AtomicLong atomicLong = new AtomicLong(0);
    private SchedulerService schedulerService;
    private volatile MarketConnected marketConnected;
    @Getter
    @Setter
    private int publishRateMillis = 1000;
    @Getter
    @Setter
    private double baseMid = 65_000;
    @Getter
    @Setter
    private double spread = 50;
    @Getter
    @Setter
    private double size = 1;
    @Getter
    @Setter
    private double rangePercent = 0.02;

    public MockMarketDataFeedWorker(String roleName) {
        super(roleName);
    }

    public MockMarketDataFeedWorker() {
        super(MOCK_MARKET_DATA_FEED);
    }

    @Override
    public void start() {
        super.start();
        log.info("Starting MockMarketDataFeedWorker publishRateMillis {}, baseMid {}", publishRateMillis, baseMid);
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

        double percentChange = RANDOM.nextDouble(rangePercent);
        if(percentChange > 0) {

            double mid = baseMid + baseMid * percentChange;
            double bid = mid - spread;
            double ask = mid + spread;
            double bidSize = size * RANDOM.nextInt(10) + size;
            double askSize = size * RANDOM.nextInt(10) + size;
            MarketDataBook marketDataBook = new MarketDataBook(
                    getFeedName(),
                    getFeedName(),
                    "XBT/USD",
                    atomicLong.incrementAndGet(),
                    bid, bidSize,
                    ask, askSize
            );
            log.debug("publishPrice:{}", marketDataBook);
            publish(marketDataBook);
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
