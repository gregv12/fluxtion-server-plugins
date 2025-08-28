package com.fluxtion.server.plugin.trading.service.node.marketdata;

import com.fluxtion.server.plugin.trading.service.marketdata.MarketDataBook;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketDataFeed;
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
        final AtomicReference<Object> output = new AtomicReference<>();

        AdminCommandRegistry reg = new AdminCommandRegistry() {
            @Override
            public <OUT, ERR> void registerCommand(String name, com.fluxtion.server.service.admin.AdminFunction<OUT, ERR> command) {
                registered.add(name);
                // emulate invoking the command
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
        assertTrue(registered.stream().anyMatch(s -> s.contains("currentBook")));
        assertEquals(book, output.get());
    }
}
