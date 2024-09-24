/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.spring;

import com.fluxtion.compiler.extern.spring.FluxtionSpring;
import com.fluxtion.runtime.EventProcessor;
import com.fluxtion.runtime.annotations.feature.Preview;
import com.fluxtion.runtime.audit.EventLogControlEvent;
import lombok.Data;

import java.nio.file.Path;
import java.util.function.Supplier;

@Preview
@Data
public class SpringEventHandlerBuilder<T extends EventProcessor<?>> implements Supplier<T> {

    private String springFile;
    private boolean addEventAuditor = true;
    private EventLogControlEvent.LogLevel auditLogLevel;

    @Override
    @SuppressWarnings("unchecked")
    public T get() {
        Path springFilePath = Path.of(springFile);
        if (!springFilePath.toFile().exists()) {
            throw new RuntimeException("File not found: " + springFile);
        }
        return (T) FluxtionSpring.compile(springFilePath, cfg -> {
            if (addEventAuditor) {
                cfg.addEventAudit(auditLogLevel != null ? auditLogLevel : EventLogControlEvent.LogLevel.INFO);
            }
        });
    }
}
