/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.kafka;

import com.fluxtion.agrona.concurrent.Agent;
import com.fluxtion.runtime.lifecycle.Lifecycle;
import com.fluxtion.runtime.output.AbstractMessageSink;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

@Log4j2
public class KafkaMessagePublisher extends AbstractMessageSink<Object> implements Lifecycle, Agent {

    @Getter
    @Setter
    private Properties properties;
    @Getter
    @Setter
    private boolean flushEveryMessage = true;
    @Getter
    @Setter
    private String topic;
    private KafkaProducer<Object, Object> producer;
    private final AtomicBoolean flushMessageBuffer = new AtomicBoolean(false);

    @Override
    public void init() {
        properties = properties == null ? new Properties() : properties;
        log.info("Initializing KafkaMessagePublisher {}", properties);
        producer = createProducer(properties);
    }

    protected KafkaProducer<Object, Object> createProducer(Properties props) {
        return new KafkaProducer<>(props);
    }

    @Override
    protected void sendToSink(Object value) {
        log.debug("sink publish:{}", value);
        ProducerRecord<Object, Object> producerRecord = new ProducerRecord<>(topic, value);
        producer.send(producerRecord);
        flushMessageBuffer.set(true);
        if (flushEveryMessage) {
            producer.flush();
        }
    }

    @Override
    public int doWork() throws Exception {
        if (flushMessageBuffer.getAndSet(false)) {
            producer.flush();
        }
        return 0;
    }

    @Override
    public String roleName() {
        return "";
    }

    @Override
    public void tearDown() {
        producer.flush();
        producer.close();
    }
}
