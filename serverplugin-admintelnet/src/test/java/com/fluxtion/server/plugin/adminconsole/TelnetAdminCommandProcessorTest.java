package com.fluxtion.server.plugin.adminconsole;

import com.fluxtion.server.service.admin.AdminCommandRegistry;
import com.fluxtion.server.service.admin.AdminCommandRequest;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TelnetAdminCommandProcessorTest {

    @Test
    void processCommand_buildsRequest_andCallsRegistry() throws Exception {
        TelnetAdminCommandProcessor proc = new TelnetAdminCommandProcessor();

        // Capture the request forwarded to the registry
        AtomicReference<AdminCommandRequest> captured = new AtomicReference<>();
        AdminCommandRegistry registry = new AdminCommandRegistry() {
            @Override
            public <OUT, ERR> void registerCommand(String name, com.fluxtion.server.service.admin.AdminFunction<OUT, ERR> command) {
                // not used in this test
            }

            @Override
            public void processAdminCommandRequest(AdminCommandRequest request) {
                captured.set(request);
            }

            @Override
            public List<String> commandList() {
                return List.of();
            }
        };
        proc.adminRegistry(registry, "test-registry");

        // Build a simple in-memory terminal
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Terminal terminal = TerminalBuilder.builder()
                .dumb(true)
                .system(false)
                .streams(new ByteArrayInputStream(new byte[0]), out)
                .build();

        // Invoke private processCommand via reflection
        Method m = TelnetAdminCommandProcessor.class.getDeclaredMethod("processCommand", Terminal.class, String[].class);
        m.setAccessible(true);
        String[] args = new String[]{"echo", "hello", "world"};
        m.invoke(proc, terminal, args);

        // Verify the registry was called with a properly populated request
        AdminCommandRequest req = captured.get();
        assertNotNull(req, "AdminCommandRequest should be forwarded to the registry");
        assertEquals("echo", req.getCommand());
        assertEquals(List.of("hello", "world"), req.getArguments());
        assertNotNull(req.getOutput(), "Output consumer should be set");
        assertNotNull(req.getErrOutput(), "Err output consumer should be set");
    }

    @Test
    void processCommand_withoutRegistry_doesNotThrow() throws Exception {
        TelnetAdminCommandProcessor proc = new TelnetAdminCommandProcessor();

        // Build a simple in-memory terminal
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Terminal terminal = TerminalBuilder.builder()
                .dumb(true)
                .system(false)
                .streams(new ByteArrayInputStream(new byte[0]), out)
                .build();

        Method m = TelnetAdminCommandProcessor.class.getDeclaredMethod("processCommand", Terminal.class, String[].class);
        m.setAccessible(true);
        assertDoesNotThrow(() -> {
            try {
                m.invoke(proc, terminal, new String[]{"noop"});
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
