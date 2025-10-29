package com.fluxtion.server.plugin.trading.component.marketdatafeed;

import com.fluxtion.runtime.StaticEventProcessor;
import com.fluxtion.server.plugin.trading.service.marketdata.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MarketListenerInvocationStrategy.
 * Verifies that market feed events are properly dispatched to MarketDataListener callbacks.
 */
class MarketListenerInvocationStrategyTest {

    private MarketListenerInvocationStrategy strategy;
    private TestMarketDataListener listener;

    @BeforeEach
    void setUp() {
        strategy = new MarketListenerInvocationStrategy();
        listener = new TestMarketDataListener();
    }

    @Test
    void dispatchMarketDataBook_invokesOnMarketDataCallback() {
        // Given
        MarketDataBook book = new MarketDataBook(
                "TEST_FEED",
                "TEST_VENUE",
                "BTCUSD",
                1L,
                100.0,
                10.0,
                101.0,
                15.0
        );

        // When
        strategy.dispatchEvent(book, listener);

        // Then
        assertEquals(1, listener.marketDataBooks.size());
        assertEquals(book, listener.marketDataBooks.get(0));
        assertTrue(listener.multilevelBooks.isEmpty());
        assertTrue(listener.connectedEvents.isEmpty());
        assertTrue(listener.disconnectedEvents.isEmpty());
    }

    @Test
    void dispatchMultilevelMarketDataBook_invokesOnMultilevelMarketDataCallback() {
        // Given
        MultilevelMarketDataBook multilevelBook = new MultilevelMarketDataBook(
                "TEST_FEED",
                "TEST_VENUE",
                "BTCUSD",
                2L,
                MultilevelBookConfig.defaultConfig()
        );
        multilevelBook.updateBid(100.0, 10.0, 1);
        multilevelBook.updateAsk(101.0, 15.0, 1);

        // When
        strategy.dispatchEvent(multilevelBook, listener);

        // Then
        assertEquals(1, listener.multilevelBooks.size());
        assertEquals(multilevelBook, listener.multilevelBooks.get(0));
        assertTrue(listener.marketDataBooks.isEmpty());
        assertTrue(listener.connectedEvents.isEmpty());
        assertTrue(listener.disconnectedEvents.isEmpty());
    }

    @Test
    void dispatchMarketConnected_invokesMarketDataVenueConnectedCallback() {
        // Given
        MarketConnected connected = new MarketConnected("TEST_FEED");

        // When
        strategy.dispatchEvent(connected, listener);

        // Then
        assertEquals(1, listener.connectedEvents.size());
        assertEquals(connected, listener.connectedEvents.get(0));
        assertTrue(listener.marketDataBooks.isEmpty());
        assertTrue(listener.multilevelBooks.isEmpty());
        assertTrue(listener.disconnectedEvents.isEmpty());
    }

    @Test
    void dispatchMarketDisconnected_invokesMarketDataVenueDisconnectedCallback() {
        // Given
        MarketDisconnected disconnected = new MarketDisconnected("TEST_FEED");

        // When
        strategy.dispatchEvent(disconnected, listener);

        // Then
        assertEquals(1, listener.disconnectedEvents.size());
        assertEquals(disconnected, listener.disconnectedEvents.get(0));
        assertTrue(listener.marketDataBooks.isEmpty());
        assertTrue(listener.multilevelBooks.isEmpty());
        assertTrue(listener.connectedEvents.isEmpty());
    }

    @Test
    void dispatchMultipleEvents_invokesCorrectCallbacks() {
        // Given
        MarketConnected connected = new MarketConnected("TEST_FEED");
        MarketDataBook book = new MarketDataBook("TEST_FEED", "TEST_VENUE", "BTCUSD", 1L,
                100.0, 10.0, 101.0, 15.0);
        MultilevelMarketDataBook multilevelBook = new MultilevelMarketDataBook(
                "TEST_FEED", "TEST_VENUE", "BTCUSD", 2L, MultilevelBookConfig.defaultConfig());
        multilevelBook.updateBid(100.0, 10.0, 1);
        multilevelBook.updateAsk(101.0, 15.0, 1);
        MarketDisconnected disconnected = new MarketDisconnected("TEST_FEED");

        // When
        strategy.dispatchEvent(connected, listener);
        strategy.dispatchEvent(book, listener);
        strategy.dispatchEvent(multilevelBook, listener);
        strategy.dispatchEvent(disconnected, listener);

        // Then
        assertEquals(1, listener.connectedEvents.size());
        assertEquals(1, listener.marketDataBooks.size());
        assertEquals(1, listener.multilevelBooks.size());
        assertEquals(1, listener.disconnectedEvents.size());
    }

    @Test
    void isValidTarget_returnsTrueForMarketDataListener() {
        // Given
        TestMarketDataListener listener = new TestMarketDataListener();

        // When
        boolean result = strategy.isValidTarget(listener);

        // Then
        assertTrue(result);
    }

    @Test
    void isValidTarget_returnsFalseForNonMarketDataListener() {
        // Given
        StaticEventProcessor nonListener = new StaticEventProcessor() {
            @Override
            public <T> T getExportedService(Class<T> aClass) {
                return null;
            }

            @Override
            public <T> boolean exportsService(Class<T> aClass) {
                return false;
            }

            @Override
            public void onEvent(Object o) {
            }
        };

        // When
        boolean result = strategy.isValidTarget(nonListener);

        // Then
        assertFalse(result);
    }

    @Test
    void dispatchEmptyMultilevelBook_handlesCorrectly() {
        // Given
        MultilevelMarketDataBook emptyBook = new MultilevelMarketDataBook(
                "TEST_FEED",
                "TEST_VENUE",
                "BTCUSD",
                3L,
                MultilevelBookConfig.defaultConfig()
        );

        // When
        strategy.dispatchEvent(emptyBook, listener);

        // Then
        assertEquals(1, listener.multilevelBooks.size());
        assertTrue(listener.multilevelBooks.get(0).isEmpty());
    }

    /**
     * Test implementation of MarketDataListener that captures all callback invocations.
     */
    private static class TestMarketDataListener implements StaticEventProcessor, MarketDataListener {
        final List<MarketDataBook> marketDataBooks = new ArrayList<>();
        final List<MultilevelMarketDataBook> multilevelBooks = new ArrayList<>();
        final List<MarketConnected> connectedEvents = new ArrayList<>();
        final List<MarketDisconnected> disconnectedEvents = new ArrayList<>();

        @Override
        public boolean onMarketData(MarketDataBook marketDataBook) {
            marketDataBooks.add(marketDataBook);
            return true;
        }

        @Override
        public boolean onMultilevelMarketData(MultilevelMarketDataBook multilevelBook) {
            multilevelBooks.add(multilevelBook);
            return true;
        }

        @Override
        public boolean marketDataVenueConnected(MarketConnected marketConnected) {
            connectedEvents.add(marketConnected);
            return true;
        }

        @Override
        public boolean marketDataVenueDisconnected(MarketDisconnected marketDisconnected) {
            disconnectedEvents.add(marketDisconnected);
            return true;
        }

        @Override
        public <T> T getExportedService(Class<T> aClass) {
            if (aClass == MarketDataListener.class) {
                return aClass.cast(this);
            }
            return null;
        }

        @Override
        public <T> boolean exportsService(Class<T> aClass) {
            return aClass == MarketDataListener.class;
        }

        @Override
        public void onEvent(Object o) {
        }
    }
}
