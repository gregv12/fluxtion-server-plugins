/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.rest.service;

public interface RestController {

    <S, T> void addCommand(String command, CommandProcessor<S, T> commandProcessor);
}
