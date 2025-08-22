package com.fluxtion.server.plugin.trading.component.quickfixj;

import org.apache.logging.log4j.core.Appender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class QuickFixMarketDataFeedLogTypeExceptionHandledTest {

    private Appender attached;

    @AfterEach
    void tearDown() {
        if (attached != null) {
            LogCaptureUtil.detachFromLogger(QuickFixMarketDataFeed.class, attached);
        }
    }

    @Test
    void fallsBackToFileLogFactoryWhenLogTypeLookupThrows() throws Exception {
        Path tmp = createSettingsFileWithoutLogType();

        attached = LogCaptureUtil.attachToLogger(QuickFixMarketDataFeed.class);

        QuickFixMarketDataFeed mdf = new QuickFixMarketDataFeed();
        mdf.setConfig(tmp.toString());
        mdf.setLocalFile(true);
        mdf.init();

        boolean sawFallback = LogCaptureUtil.anyMessageMatches((LogCaptureUtil.InMemoryAppender) attached,
                msg -> msg.contains("LogFactory:FileLogFactory"));
        assertTrue(sawFallback, "Expected QuickFixMarketDataFeed to fall back to FileLogFactory when LogType lookup fails");

        boolean sawWarn = LogCaptureUtil.anyMessageMatches((LogCaptureUtil.InMemoryAppender) attached,
                msg -> msg.contains("Failed to get LogType from settings"));
        assertTrue(sawWarn, "Expected a warning when LogType lookup throws");
    }

    private static Path createSettingsFileWithoutLogType() throws IOException {
        String content = "" +
                "[DEFAULT]\n" +
                "ConnectionType=initiator\n" +
                "SocketConnectHost=127.0.0.1\n" +
                "SocketConnectPort=12345\n" +
                "FileStorePath=target/fixstore\n" +
                "FileLogPath=target/fixlog\n" +
                "StartTime=00:00:00\n" +
                "EndTime=23:59:59\n" +
                // Intentionally omit LogType to trigger ConfigError on getString
                "UseDataDictionary=N\n" +
                "HeartBtInt=30\n" +
                "BeginString=FIX.4.2\n" +
                "\n" +
                "[SESSION]\n" +
                "SenderCompID=MD\n" +
                "TargetCompID=MDT\n";
        Path tmp = Files.createTempFile("qfj-md-no-logtype-", ".cfg");
        Files.writeString(tmp, content);
        tmp.toFile().deleteOnExit();
        return tmp;
    }
}
