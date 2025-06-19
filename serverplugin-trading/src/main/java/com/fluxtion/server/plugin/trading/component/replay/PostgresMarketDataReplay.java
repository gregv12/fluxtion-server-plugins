package com.fluxtion.server.plugin.trading.component.replay;

import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.event.ReplayRecord;
import com.fluxtion.server.plugin.jdbc.JdbcConnectionLoader;
import com.fluxtion.server.plugin.trading.component.marketdatafeed.AbstractMarketDataFeedWorker;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketConnected;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketDataBook;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketFeedEvent;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

import java.sql.Connection;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static com.fluxtion.server.plugin.trading.component.quickfixj.QuickFixMarketDataLogReplayFeed.UTC_ZONE;

@Data
@Log4j2
public class PostgresMarketDataReplay extends AbstractMarketDataFeedWorker {

    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss zzz");
    private String jdbcConnection;
    private List<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private Connection connection;
    private String startTime;
    private String endTime;
    private boolean connectedSent = false;

    protected PostgresMarketDataReplay() {
        super("postgresMarketDataReplay");
    }

    @Override
    public void onStart() {
        super.onStart();
        log.info("PostgresMarketDataReplay onStart");
    }

    @Override
    public void init() {
        super.init();
    }

    @Override
    public void start() {
        super.start();
    }

    @Override
    public void startComplete() {
        super.startComplete();
        log.info("PostgresMarketDataReplay replay starting");

        long id = 0;
        try {
            String timeQuery = timeQueryString();
            String symbolQuery = subscriptions.stream()
                    .map(Subscription::queryString)
                    .collect(Collectors.joining(" or ", "(", ")"));

            String queryString;
            if (timeQuery.isBlank() && symbolQuery.isBlank()) {
                queryString = """
                        select time, feed, venue, symbol, bid, ask
                        from market_data_book
                        order by time
                        """;
            } else {
                queryString = """
                        select time, feed, venue, symbol, bid, ask
                        from market_data_book
                        where
                            %s
                            %s
                        order by time
                        """.formatted(timeQuery, symbolQuery);
            }

            log.info("PostgresMarketDataReplay replay queryString: \n{}", queryString);

            ResultSet rs = connection.createStatement().executeQuery(queryString);
            while (rs.next()) {
                LocalDateTime sendTimeStamp = rs.getTimestamp("time").toLocalDateTime();
                long replayTime = ZonedDateTime.of(sendTimeStamp, UTC_ZONE).toInstant().toEpochMilli();
                if (!connectedSent) {
                    subscriptions.stream()
                            .map(Subscription::feed)
                            .map(MarketConnected::new)
                            .forEach(m -> publish(m, replayTime));
                    connectedSent = true;
                }

                MarketDataBook marketDataBook = new MarketDataBook(
                        rs.getString("feed"),
                        rs.getString("venue"),
                        rs.getString("symbol"),
                        id++,
                        rs.getDouble("bid"),
                        1,
                        rs.getDouble("ask"),
                        1
                );

                publish(marketDataBook, replayTime);

                if (id % 50 == 0) {
                    Thread.sleep(1);
                }
            }
        } catch (Exception e) {
            log.error(e);
        } finally {
            log.info("PostgresMarketDataReplay replay complete records processed:{}", id);
        }
    }

    @SneakyThrows
    @ServiceRegistered
    public void jdbcConnection(JdbcConnectionLoader loader, String name) {
        log.info("JdbcConnectionLoader:{}", name);
        connection = loader.getConnection(jdbcConnection);
    }

    @Override
    public int doWork() throws Exception {
        return 0;
    }

    @Override
    protected void subscribeToSymbol(String feedName, String venueName, String symbol) {
        log.info("subscribe feed:{} venue:{} symbol:{}", feedName, venueName, symbol);
        subscriptions.add(new Subscription(feedName, venueName, symbol));
    }

    protected void publish(MarketFeedEvent marketFeedEvent, long replayTime) {
        ReplayRecord replayRecord = new ReplayRecord();
        replayRecord.setEvent(marketFeedEvent);
        replayRecord.setWallClockTime(replayTime);
        getTargetQueue().publishReplay(replayRecord);
    }

    private String timeQueryString() {
        String query = "";
        if (startTime == null && endTime == null) {
            return query;
        } else if (startTime != null && endTime == null) {
            query = "(time after '%s')".formatted(startTime);
        } else if (startTime == null && endTime != null) {
            query = "(time before '%s')".formatted(endTime);
        } else {
            query = "(time between '%s' and '%s')".formatted(startTime, endTime);
        }
        return query + " and ";
    }

    private record Subscription(String feed, String venue, String symbol) {
        String queryString() {
            return "(feed = '%s' and venue = '%s' and symbol = '%S')".formatted(feed, venue, symbol);
        }
    }
}
