/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.fluxtion.server.plugin.trading.component.quickfixj;

import com.fluxtion.runtime.lifecycle.Lifecycle;
import com.fluxtion.server.service.LifeCycleEventSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuickFixAcceptorLifecycleTest {

    /**
     * Regression guard: QuickFixAcceptor binds its FIX connector in init() and starts it
     * in start(). The server's LifecycleManager only invokes init()/start() for plain
     * (non-LifeCycleEventSource) services, so the acceptor must be a plain Lifecycle service
     * and must NOT be a LifeCycleEventSource — otherwise the FIX acceptor silently never starts.
     */
    @Test
    void isPlainLifecycleService_notAnEventSource() {
        QuickFixAcceptor acceptor = new QuickFixAcceptor("dummy.cfg");
        assertTrue(acceptor instanceof Lifecycle,
                "must be a Lifecycle service so init()/start() are invoked by the server");
        assertFalse(acceptor instanceof LifeCycleEventSource,
                "must NOT be a LifeCycleEventSource — those are skipped by the plain-service "
                        + "lifecycle loop, so init()/start() (the FIX connector bind/start) would never run");
    }
}
