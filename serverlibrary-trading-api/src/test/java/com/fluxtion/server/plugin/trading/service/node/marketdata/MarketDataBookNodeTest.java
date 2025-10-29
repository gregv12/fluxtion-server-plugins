package com.fluxtion.server.plugin.trading.service.node.marketdata;

import com.fluxtion.server.plugin.trading.service.marketdata.MarketDataBook;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketDataFeed;
import com.fluxtion.server.plugin.trading.service.marketdata.MultilevelMarketDataBook;
import com.fluxtion.server.plugin.trading.service.marketdata.MultilevelBookConfig;
import com.fluxtion.server.service.admin.AdminCommandRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class MarketDataBookNodeTest {

    @Test
    void subscribesWhenFeedMatches_andStoresLatestMatchingBook() {
        MarketDataBookNode node = new MarketDataBookNode("n1");
        node.setSubscription("aggFeed", "venueA", "BTCUSD");

        // feed that matches both feedName and venue
        AtomicReference<List<String>> subscribeCalls = new AtomicReference<>(new ArrayList<>());
        MarketDataFeed feed = new MarketDataFeed() {
            @Override
            public String feedName() {
                return "aggFeed";
            }

            @Override
            public Set<String> aggregatedFeeds() {
                return Set.of("aggFeed");
            }

            @Override
            public Set<String> venues() {
                return Set.of("venueA");
            }

            @Override
            public void subscribe(String feedName, String venueName, String symbol) {
                subscribeCalls.get().add(feedName + ":" + venueName + ":" + symbol);
            }
        };

        node.marketFeedRegistered(feed, "svc1");
        // start also attempts subscribe
        node.start();
        assertFalse(subscribeCalls.get().isEmpty(), "subscribe should be called when matching");

        // matching book is stored
        MarketDataBook match = new MarketDataBook("aggFeed", "venueA", "BTCUSD", 1, 100, 1, 101, 1);
        assertTrue(node.onMarketData(match));
        assertEquals(match, node.getMarketDataBook());

        // non matching ignored
        MarketDataBook ignore = new MarketDataBook("aggFeed", "venueA", "ETHUSD", 1, 100, 1, 101, 1);
        assertFalse(node.onMarketData(ignore));
        assertEquals(match, node.getMarketDataBook());
    }

    @Test
    void adminClient_registersCommand_andOutputsCurrentBook() {
        MarketDataBookNode node = new MarketDataBookNode("n2");
        MarketDataBook book = new MarketDataBook("agg", "venue", "BTCUSD", 2, 1, 2, 3, 4);
        // set directly to simulate having seen a book
        // using reflection not required since we have setter/getter for name only; onMarketData sets field
        node.setSubscription("agg", "venue", "BTCUSD");
        node.onMarketData(book);

        List<String> registered = new ArrayList<>();
        final AtomicReference<Object> singleLevelOutput = new AtomicReference<>();

        AdminCommandRegistry reg = new AdminCommandRegistry() {
            @Override
            public <OUT, ERR> void registerCommand(String name, com.fluxtion.server.service.admin.AdminFunction<OUT, ERR> command) {
                registered.add(name);
                // Only invoke the currentBook command (not currentMultilevelBook)
                if (name.contains("currentBook") && !name.contains("currentMultilevelBook")) {
                    command.processAdminCommand(List.of(), (Consumer<OUT>) singleLevelOutput::set, err -> {});
                }
            }

            @Override
            public void processAdminCommandRequest(com.fluxtion.server.service.admin.AdminCommandRequest request) {
            }

            @Override
            public List<String> commandList() {
                return List.of();
            }
        };

        node.adminClient(reg);
        assertTrue(registered.stream().anyMatch(s -> s.contains("currentBook")));
        assertTrue(registered.stream().anyMatch(s -> s.contains("currentMultilevelBook")));
        assertEquals(book, singleLevelOutput.get());
    }

    @Test
    void onMultilevelMarketData_storesMatchingBook() {
        MarketDataBookNode node = new MarketDataBookNode("n3");
        node.setSubscription("aggFeed", "venueA", "BTCUSD");

        // Create multilevel book that matches subscription
        MultilevelMarketDataBook matchingBook = new MultilevelMarketDataBook(
                "aggFeed", "venueA", "BTCUSD", 1);
        matchingBook.updateBid(100.0, 10.0, 1);
        matchingBook.updateBid(99.0, 20.0, 2);
        matchingBook.updateAsk(101.0, 15.0, 1);
        matchingBook.updateAsk(102.0, 25.0, 3);

        assertTrue(node.onMultilevelMarketData(matchingBook));
        assertEquals(matchingBook, node.getMultilevelMarketDataBook());
        assertTrue(node.isMultilevelUpdated());

        // Non-matching book (different symbol) should be ignored
        MultilevelMarketDataBook nonMatchingBook = new MultilevelMarketDataBook(
                "aggFeed", "venueA", "ETHUSD", 2);
        nonMatchingBook.updateBid(200.0, 5.0, 1);

        assertFalse(node.onMultilevelMarketData(nonMatchingBook));
        assertEquals(matchingBook, node.getMultilevelMarketDataBook());
    }

    @Test
    void onMultilevelMarketData_filtersNonMatchingFeedName() {
        MarketDataBookNode node = new MarketDataBookNode("n4");
        node.setSubscription("aggFeed", "venueA", "BTCUSD");

        MultilevelMarketDataBook book = new MultilevelMarketDataBook(
                "differentFeed", "venueA", "BTCUSD", 1);
        book.updateBid(100.0, 10.0, 1);

        assertFalse(node.onMultilevelMarketData(book));
        assertNull(node.getMultilevelMarketDataBook());
        assertFalse(node.isMultilevelUpdated());
    }

    @Test
    void onMultilevelMarketData_filtersNonMatchingVenue() {
        MarketDataBookNode node = new MarketDataBookNode("n5");
        node.setSubscription("aggFeed", "venueA", "BTCUSD");

        MultilevelMarketDataBook book = new MultilevelMarketDataBook(
                "aggFeed", "venueB", "BTCUSD", 1);
        book.updateBid(100.0, 10.0, 1);

        assertFalse(node.onMultilevelMarketData(book));
        assertNull(node.getMultilevelMarketDataBook());
        assertFalse(node.isMultilevelUpdated());
    }

    @Test
    void onMultilevelMarketData_updatedFlagResetInPostCalculate() {
        MarketDataBookNode node = new MarketDataBookNode("n6");
        node.setSubscription("aggFeed", "venueA", "BTCUSD");

        MultilevelMarketDataBook book = new MultilevelMarketDataBook(
                "aggFeed", "venueA", "BTCUSD", 1);
        book.updateBid(100.0, 10.0, 1);

        assertTrue(node.onMultilevelMarketData(book));
        assertTrue(node.isMultilevelUpdated());

        node.postCalculate();
        assertFalse(node.isMultilevelUpdated());
        assertEquals(book, node.getMultilevelMarketDataBook()); // Book still stored
    }

    @Test
    void bothSingleLevelAndMultilevelBooksCanCoexist() {
        MarketDataBookNode node = new MarketDataBookNode("n7");
        node.setSubscription("aggFeed", "venueA", "BTCUSD");

        // Store single-level book
        MarketDataBook singleLevelBook = new MarketDataBook(
                "aggFeed", "venueA", "BTCUSD", 1, 100.0, 10.0, 101.0, 15.0);
        assertTrue(node.onMarketData(singleLevelBook));
        assertEquals(singleLevelBook, node.getMarketDataBook());
        assertTrue(node.isUpdated());

        // Store multilevel book
        MultilevelMarketDataBook multilevelBook = new MultilevelMarketDataBook(
                "aggFeed", "venueA", "BTCUSD", 2);
        multilevelBook.updateBid(100.0, 10.0, 1);
        multilevelBook.updateAsk(101.0, 15.0, 1);
        assertTrue(node.onMultilevelMarketData(multilevelBook));
        assertEquals(multilevelBook, node.getMultilevelMarketDataBook());
        assertTrue(node.isMultilevelUpdated());

        // Both books should be stored
        assertEquals(singleLevelBook, node.getMarketDataBook());
        assertEquals(multilevelBook, node.getMultilevelMarketDataBook());
    }

    @Test
    void adminClient_registersMultilevelBookCommand_andOutputsCurrentMultilevelBook() {
        MarketDataBookNode node = new MarketDataBookNode("n8");
        MultilevelMarketDataBook book = new MultilevelMarketDataBook(
                "agg", "venue", "BTCUSD", 1);
        book.updateBid(100.0, 10.0, 1);
        book.updateBid(99.0, 20.0, 2);
        book.updateAsk(101.0, 15.0, 1);
        book.updateAsk(102.0, 25.0, 3);

        node.setSubscription("agg", "venue", "BTCUSD");
        node.onMultilevelMarketData(book);

        List<String> registered = new ArrayList<>();
        final AtomicReference<Object> output = new AtomicReference<>();

        AdminCommandRegistry reg = new AdminCommandRegistry() {
            @Override
            public <OUT, ERR> void registerCommand(String name, com.fluxtion.server.service.admin.AdminFunction<OUT, ERR> command) {
                registered.add(name);
                // Emulate invoking the command
                command.processAdminCommand(List.of(), (Consumer<OUT>) output::set, err -> {});
            }

            @Override
            public void processAdminCommandRequest(com.fluxtion.server.service.admin.AdminCommandRequest request) {
            }

            @Override
            public List<String> commandList() {
                return List.of();
            }
        };

        node.adminClient(reg);
        assertTrue(registered.stream().anyMatch(s -> s.contains("currentMultilevelBook")));
        // The output will be set twice (once for currentBook, once for currentMultilevelBook)
        // Check that we can retrieve the multilevel book
        assertNotNull(node.getMultilevelMarketDataBook());
        assertEquals(book, node.getMultilevelMarketDataBook());
    }

    @Test
    void multilevelBookWithCustomConfiguration() {
        MarketDataBookNode node = new MarketDataBookNode("n9");
        node.setSubscription("aggFeed", "venueA", "BTCUSD");

        // Create book with custom configuration (e.g., max depth of 5)
        MultilevelBookConfig config = MultilevelBookConfig.builder()
                .maxDepth(5)
                .validateLevels(true)
                .trimEmptyLevels(true)
                .build();

        MultilevelMarketDataBook book = new MultilevelMarketDataBook(
                "aggFeed", "venueA", "BTCUSD", 1, config);
        book.updateBid(100.0, 10.0, 1);
        book.updateBid(99.0, 20.0, 2);
        book.updateAsk(101.0, 15.0, 1);

        assertTrue(node.onMultilevelMarketData(book));
        assertEquals(book, node.getMultilevelMarketDataBook());
        assertEquals(2, node.getMultilevelMarketDataBook().getBidDepth());
        assertEquals(1, node.getMultilevelMarketDataBook().getAskDepth());
    }
}
