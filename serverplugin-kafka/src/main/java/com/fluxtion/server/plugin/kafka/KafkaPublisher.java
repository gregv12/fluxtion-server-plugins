/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.kafka;

import com.fluxtion.runtime.lifecycle.Lifecycle;
import com.fluxtion.runtime.output.AbstractMessageSink;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class KafkaPublisher extends AbstractMessageSink<Object>
        implements Lifecycle {

    @Override
    public void init() {
        //publish this as a sink registration
    }

    @Override
    public void tearDown() {

    }

    @Override
    protected void sendToSink(Object value) {
        log.info("sink publish:{}", value);
    }
}
