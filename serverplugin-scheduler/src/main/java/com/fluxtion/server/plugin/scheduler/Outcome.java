/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.scheduler;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** A closed item: what was due, what happened, who decided and what the command said. */
@Data
public class Outcome {
    private String itemId;
    private String scheduleId;
    private String actionId;
    private String title;
    private String command;
    private List<String> arguments = new ArrayList<>();
    private String restartSetId;
    private long dueAt;
    private OutcomeState state;
    /** The view (and optional typed name) that decided, or {@code scheduler} for expiry. */
    private String decidedBy;
    private long decidedAt;
    /** Command output, or the reason it failed or expired. */
    private String detail;
    private long durationMillis;
}
