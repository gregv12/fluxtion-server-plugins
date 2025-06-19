package com.fluxtion.server.plugin.trading.component.quickfixj;

import com.fluxtion.runtime.event.ReplayRecord;
import com.fluxtion.server.plugin.trading.component.marketdatafeed.AbstractMarketDataFeedWorker;
import com.fluxtion.server.plugin.trading.service.marketdata.MarketFeedEvent;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import quickfix.*;
import quickfix.field.SendingTime;

import java.io.File;
import java.io.FileNotFoundException;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

@Log4j2
public abstract class QuickFixMarketDataLogReplayFeed extends AbstractMarketDataFeedWorker {

    public static final ZoneId UTC_ZONE = ZoneId.of("UTC");
    protected FixMessageHandler fixMessageHandler;
    protected final DataDictionary dataDictionary;
    @Getter
    @Setter
    protected String fixLog;
    private Scanner scanner;
    private long replayTime;
    private int lineCount;
    private final AtomicBoolean startReplay = new AtomicBoolean(false);

    public QuickFixMarketDataLogReplayFeed(
            String roleName,
            FixMessageHandler fixMessageHandler,
            DataDictionary dataDictionary,
            String fixLog) {
        super(roleName);
        this.fixMessageHandler = fixMessageHandler;
        this.dataDictionary = dataDictionary;
        this.fixLog = fixLog;
    }

    public QuickFixMarketDataLogReplayFeed(
            String roleName,
            DataDictionary dataDictionary,
            String fixLog) {
        super(roleName);
        this.dataDictionary = dataDictionary;
        this.fixLog = fixLog;
    }

    @Override
    public void init() {
        super.init();
        try {
            lineCount = 0;
            File source = new File(fixLog);
            log.debug("read file:{}, exists:{}", source, source.exists());
            scanner = new Scanner(source);
        } catch (FileNotFoundException e) {
            log.error("problem reading replay file:", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void subscribeToSymbol(String feedName, String venueName, String symbol) {
        if (startReplay.getAndSet(true)) {
            return;
        }
        log.info("start market data replay reading file:{}", fixLog);
    }

    @Override
    public int doWork() throws Exception {
        int thisCount = 0;

        if (startReplay.get()) {
            String message = null;
            try {
                while (scanner.hasNextLine()) {
                    lineCount++;
                    thisCount++;
                    message = (scanner.nextLine()).replace("^A", "\u0001");
                    log.debug(message);
                    Message messageFix = new Message(message, dataDictionary);

                    LocalDateTime sendTimeStamp = messageFix.getHeader().getUtcTimeStamp(SendingTime.FIELD);
                    replayTime = ZonedDateTime.of(sendTimeStamp, UTC_ZONE).toInstant().toEpochMilli();

                    fixMessageHandler.processFixMessage(messageFix, null);
                    if (lineCount % 100_000 == 0) {
                        log.info("{} lineCount:{}", feedName(), NumberFormat.getNumberInstance(Locale.US).format(lineCount));
                    }
                }

            } catch (InvalidMessage e) {
                log.error("problem reading line:" + lineCount + " message:", e);
                throw new RuntimeException(e);
            } catch (UnsupportedMessageType | IncorrectTagValue | FieldNotFound | IncorrectDataFormat e) {
                log.error("problem reading line:" + lineCount + " message:" + message, e);
                throw new RuntimeException(e);
            }
        }

        return thisCount;
    }

    @Override
    protected void publish(MarketFeedEvent marketFeedEvent) {
        ReplayRecord replayRecord = new ReplayRecord();
        replayRecord.setEvent(marketFeedEvent);
        replayRecord.setWallClockTime(replayTime);
        getTargetQueue().publishReplay(replayRecord);
    }
}
