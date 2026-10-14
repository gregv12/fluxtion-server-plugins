/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.scheduler;

/** When a schedule makes its action due. */
public enum TriggerType {
    /** Once, at a date and time. */
    ONCE,
    /** Every day at a time. */
    DAILY,
    /** After each server start (and on demand), as part of the restart set. */
    AFTER_RESTART
}
