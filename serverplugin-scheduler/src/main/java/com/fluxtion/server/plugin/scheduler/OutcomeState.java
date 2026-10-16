/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.scheduler;

/** How an item closed. */
public enum OutcomeState {
    /** Approved and the command reported no error. */
    RAN_OK,
    /** Approved (or tested) and the command failed, was refused, or could not run. */
    FAILED,
    /** A person declined it. */
    DECLINED,
    /** Nobody decided before it expired, or it fell due while the server was down. */
    EXPIRED
}
