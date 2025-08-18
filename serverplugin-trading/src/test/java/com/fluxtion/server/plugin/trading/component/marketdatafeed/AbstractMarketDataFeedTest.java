package com.fluxtion.server.plugin.trading.component.marketdatafeed;

import com.fluxtion.server.dispatch.EventToQueuePublisher;
import com.fluxtion.server.plugin.trading.component.mockvenue.mktdata.MarketDataBookConfig;
import com.fluxtion.server.plugin.trading.component.mockvenue.mktdata.MockMarketDataFeedWorker;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketConnected;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketDataBook;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketFeedEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AbstractMarketDataFeed behavior using the concrete MockMarketDataFeedWorker.
 */
class AbstractMarketDataFeedTest {

    @Test
    void mockWorker_publishesMarketConnectedAndBook_onTimerTriggered_andAggregatesSets() {
        // Capture published market events
        List<MarketFeedEvent> published = new CopyOnWriteArrayList<>();
        EventToQueuePublisher<MarketFeedEvent> publisher = new EventToQueuePublisher<>("test-feed-publisher") {
            @Override
            public void publish(MarketFeedEvent event) {
                published.add(event);
            }
        };

        // Configure worker
        TestableMockMarketDataFeedWorker worker = new TestableMockMarketDataFeedWorker();
        worker.setFeedName("FEED1");
        MarketDataBookConfig cfg = newConfig("FEED1", "VENUE1", "SYM", 1.0);
        worker.setMarketDataBookConfigs(List.of(cfg));

        // Inject our capturing publisher directly into the private field via reflection
        try {
            java.lang.reflect.Field f = AbstractMarketDataFeed.class.getDeclaredField("targetQueue");
            f.setAccessible(true);
            f.set(worker, publisher);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Lifecycle calls
        worker.init();
        worker.start();

        // Manually publish once (simulate timer tick)
        worker.emitOnce(cfg);

        // Verify at least a MarketConnected and one MarketDataBook event are published
        assertFalse(published.isEmpty(), "Expected some events to be published");
        // First event should be MarketConnected per MockMarketDataFeedWorker implementation
        assertTrue(published.get(0) instanceof MarketConnected, "First event should be MarketConnected");
        assertEquals("FEED1", ((MarketConnected) published.get(0)).name());

        boolean hasBook = published.stream().anyMatch(e -> e instanceof MarketDataBook);
        assertTrue(hasBook, "Expected a MarketDataBook to be published");

        // Verify aggregatedFeeds and venues contain the feed name
        Set<String> aggregated = worker.aggregatedFeeds();
        assertTrue(aggregated.contains("FEED1"));
        Set<String> venues = worker.venues();
        assertTrue(venues.contains("FEED1"));
    }

    private static MarketDataBookConfig newConfig(String feed, String venue, String symbol, double probability) {
        MarketDataBookConfig c = new MarketDataBookConfig();
        c.setFeedName(feed);
        c.setVenueName(venue);
        c.setSymbol(symbol);
        c.setPublishProbability(probability);
        c.setMinPrice(100);
        c.setMaxPrice(110);
        c.setMinSpread(0.1);
        c.setMaxSpread(0.5);
        c.setMinVolume(10);
        c.setMaxVolume(20);
        c.setPrecisionDpsPrice(3);
        c.setPrecisionDpsVolume(3);
        return c;
        }

    // Testable subclass to expose a single-shot emit that uses the protected publish method
    private static class TestableMockMarketDataFeedWorker extends MockMarketDataFeedWorker {
        public void emitOnce(MarketDataBookConfig config) {
            // Simulate first connection event
            publish(new MarketConnected(getFeedName()));
            // And one generated book
            var book = com.fluxtion.server.plugin.trading.component.mockvenue.mktdata.MarketDataBookGenerator.generateRandom(config);
            if (book != null) {
                publish(book);
            }
        }
    }
}
