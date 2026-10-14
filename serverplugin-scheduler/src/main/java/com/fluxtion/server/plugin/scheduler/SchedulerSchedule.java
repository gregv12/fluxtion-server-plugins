/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.scheduler;

import lombok.Data;

/** When an action falls due, and how long a due item waits for a decision. */
@Data
public class SchedulerSchedule {
    private String id;
    private String actionId;
    private TriggerType trigger;
    /** ONCE: local date-time {@code 2026-10-15T09:00}; DAILY: local time {@code 09:00}; AFTER_RESTART: unused. */
    private String at;
    /** IANA zone the time is in, e.g. {@code Europe/London}; defaults to UTC. */
    private String zone = "UTC";
    /** Minutes an item waits for approval before it expires; 0 = the scheduler's default for the trigger. */
    private int expiryMinutes;
    /** Position in the restart set (AFTER_RESTART only), lowest first. */
    private int restartOrder;
    private boolean enabled = true;
    /** Next time this schedule falls due (epoch ms); maintained by the scheduler, 0 = none. */
    private long nextDueAt;
    private String changedBy;
    private long changedAt;
}
