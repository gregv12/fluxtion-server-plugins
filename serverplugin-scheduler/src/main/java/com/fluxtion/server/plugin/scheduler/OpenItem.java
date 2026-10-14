/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.scheduler;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * A due action waiting for a decision. It holds the exact command resolved when it fell due, so editing the
 * library later never changes an item already waiting.
 */
@Data
public class OpenItem {
    private String id;
    private String scheduleId;
    private String actionId;
    private String title;
    private String description;
    private String command;
    private List<String> arguments = new ArrayList<>();
    private long dueAt;
    private long expiresAt;
    /** Set when the item belongs to a restart set; items of one set are decided in {@code restartOrder}. */
    private String restartSetId;
    private int restartOrder;
    /** True while an approved command is executing; an item found running after a restart closes as FAILED. */
    private boolean running;
    /** Set once decided and saved before it is recorded, so a stop mid-close is finished at the next start. */
    private Outcome pendingOutcome;
}
