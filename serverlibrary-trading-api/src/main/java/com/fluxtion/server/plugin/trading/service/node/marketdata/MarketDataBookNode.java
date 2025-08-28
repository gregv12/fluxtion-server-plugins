package com.fluxtion.server.plugin.trading.service.node.marketdata;

import com.fluxtion.runtime.annotations.Start;
import com.fluxtion.server.plugin.trading.service.marketdata.*;
import com.fluxtion.server.service.admin.AdminCommandRegistry;
import com.fluxtion.server.plugin.trading.service.node.TradeServiceListener;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.function.Consumer;

@Log4j2
public class MarketDataBookNode implements MarketDataListener, TradeServiceListener {

    @Getter
    private final String name;
    private String feedName;
    private String venueName;
    private String subscribeSymbol;
    @Getter
    private MarketDataBook marketDataBook;
    private MarketDataFeed marketDataFeed;

    public MarketDataBookNode(String name) {
        this.name = name;
    }

    public void setFeedName(String marketDataFeedName) {
        if (marketDataFeedName != null && !marketDataFeedName.isEmpty()) {
            log.info("setMarketDataFeedName: {}", marketDataFeedName);
            this.feedName = marketDataFeedName;
            if (venueName == null) {
                setVenueName(marketDataFeedName);
            }
        }
    }

    public void setVenueName(String venueName) {
        if (venueName != null && !venueName.isEmpty()) {
            log.info("setVenueName: {}", venueName);
            this.venueName = venueName;
            if (feedName == null) {
                setFeedName(venueName);
            }
        }
    }

    public void setSubscribeSymbol(String subscribeSymbol) {
        log.info("setSubscribeSymbol: {}", subscribeSymbol);
        this.subscribeSymbol = subscribeSymbol;
    }

    public void setSubscription(String feedName, String venueName, String subscribeSymbol) {
        this.feedName = feedName;
        this.venueName = venueName;
        this.subscribeSymbol = subscribeSymbol;
        log.info("feedName: {}, venueName: {}, subscribeSymbol: {}", feedName, venueName, subscribeSymbol);
    }

    public void marketFeedRegistered(MarketDataFeed marketDataFeed, String name) {
        if (marketDataFeed.isVenueRegistered(venueName) && marketDataFeed.isAggregatedFeedRegistered(feedName)) {
            this.marketDataFeed = marketDataFeed;
            log.info("matchedMarketDataFeed: {}, service: {}, venue: {}", feedName, name, venueName);
            subscribe();
        } else {
            log.info("ignoreMarketDataFeed: {}, service: {}, venue: {}", marketDataFeed, name, venueName);
        }
    }

    public void adminClient(AdminCommandRegistry adminCommandRegistry) {
        log.info("admin: {}", adminCommandRegistry);
        adminCommandRegistry.registerCommand("mkDataBook.%s.currentBook".formatted(getName()), this::currentBook);
    }

    @Start
    public void start() {
        subscribe();
    }

    @Override
    public boolean onMarketData(MarketDataBook marketDataBook) {
        if (marketDataBook.getSymbol().equals(subscribeSymbol)
                && marketDataBook.getFeedName().equals(feedName)
                && marketDataBook.getVenueName().equals(venueName)
        ) {
            this.marketDataBook = marketDataBook;
            log.debug("onMarketData: {}", marketDataBook);
            return true;
        } else {
            log.debug("ignoreFeed: {}", marketDataBook);
        }
        return false;
    }

    @Override
    public boolean marketDataVenueConnected(MarketConnected marketConnected) {
        log.warn("marketConnected: {}", marketConnected.name());
        subscribe();
        return false;
    }

    @Override
    public boolean marketDataVenueDisconnected(MarketDisconnected marketDisconnected) {
        log.warn("marketDisconnected: {}", marketDisconnected.name());
        return false;
    }

    private void subscribe() {
        if (marketDataFeed != null & venueName != null & subscribeSymbol != null) {
            log.info("subscribe: success, marketDataFeedName: {}, venue: {}, subscribeSymbol: {}",
                    feedName, venueName, subscribeSymbol);
            marketDataFeed.subscribe(feedName, venueName, subscribeSymbol);
        } else {
            log.info("subscribe: failed, marketDataFeedName: {}, venue: {}, subscribeSymbol: {}",
                    feedName, venueName, subscribeSymbol);
        }
    }

    private void currentBook(List<String> args, Consumer<MarketDataBook> out, Consumer<String> err) {
        out.accept(marketDataBook);
    }
}