/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.scheduler;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** A library entry: a named admin command with fixed arguments, built from the catalogue. */
@Data
public class SchedulerAction {
    private String id;
    private String title;
    private String description;
    /** The exact admin command name, e.g. {@code strat.MAKER_A.setPriceSource}. */
    private String command;
    private List<String> arguments = new ArrayList<>();
    /** Environments this action may run in; empty = every environment except a production one. */
    private List<String> environments = new ArrayList<>();
    private boolean retired;
    private String changedBy;
    private long changedAt;
}
