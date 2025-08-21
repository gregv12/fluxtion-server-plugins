package com.fluxtion.server.plugin.trading.component.quickfixj;

import org.apache.logging.log4j.core.Appender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.fluxtion.server.plugin.trading.component.quickfixj.LogCaptureUtil.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QuickFixAcceptorSlf4jLogFactoryTest {

    private Appender attached;

    @AfterEach
    void tearDown() {
        if (attached != null) {
            LogCaptureUtil.detachFromLogger(QuickFixAcceptor.class, attached);
        }
    }

    @Test
    void usesSlf4jLogFactoryWhenConfigured() throws Exception {
        Path tmp = createSettingsFile("acceptor");

        attached = LogCaptureUtil.attachToLogger(QuickFixAcceptor.class);

        QuickFixAcceptor acceptor = new QuickFixAcceptor(tmp.toString(), true);
        acceptor.init();

        boolean sawSlf4j = LogCaptureUtil.anyMessageMatches((LogCaptureUtil.InMemoryAppender) attached,
                msg -> msg.contains("LogFactory:SLF4JLogFactory"));
        assertTrue(sawSlf4j, "Expected QuickFixAcceptor to log that SLF4JLogFactory is used when LogType=SLF4J");
    }

    private static Path createSettingsFile(String type) throws IOException {
        String content;
        if ("acceptor".equals(type)) {
            content = "" +
                    "[DEFAULT]\n" +
                    "ConnectionType=acceptor\n" +
                    "SocketAcceptPort=12346\n" +
                    "FileStorePath=target/fixstore\n" +
                    "FileLogPath=target/fixlog\n" +
                    "StartTime=00:00:00\n" +
                    "EndTime=23:59:59\n" +
                    "LogType=SLF4J\n" +
                    "UseDataDictionary=N\n" +
                    "HeartBtInt=30\n" +
                    "BeginString=FIX.4.2\n" +
                    "\n" +
                    "[SESSION]\n" +
                    "SenderCompID=ACCEPTOR\n" +
                    "TargetCompID=CLIENT\n";
        } else {
            throw new IllegalArgumentException("Unsupported type:" + type);
        }
        Path tmp = Files.createTempFile("qfj-acceptor-", ".cfg");
        Files.writeString(tmp, content);
        tmp.toFile().deleteOnExit();
        return tmp;
    }
}
