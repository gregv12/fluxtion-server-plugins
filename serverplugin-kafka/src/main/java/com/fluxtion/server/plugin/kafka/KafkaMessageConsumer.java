/*
 *
 *  * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 *  * SPDX-License-Identifier: AGPL-3.0-only
 *  *
 *  
 */


package com.fluxtion.server.plugin.kafka;

import com.fluxtion.server.service.AbstractAgentHostedEventSourceService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.time.Duration;
import java.util.List;
import java.util.Properties;

@Log4j2
public class KafkaMessageConsumer extends AbstractAgentHostedEventSourceService<ConsumerRecords<?, ?>> {

    private KafkaConsumer<String, String> consumer;
    @Getter
    @Setter
    private Properties properties;
    @Getter
    @Setter
    private String[] topics;

    protected KafkaMessageConsumer(String name) {
        super(name);
    }

    public KafkaMessageConsumer() {
        super("kafka-consumer");
    }

    @Override
    public void init() {
        log.info("Initializing KafkaMessageConsumer {}", properties);
    }

    @Override
    public void start() {
        log.info("Starting KafkaMessageConsumer");
        consumer = new KafkaConsumer<>(properties);
        consumer.subscribe(List.of(topics));
    }

    @Override
    public void tearDown() {
        consumer.close();
    }

    @Override
    public int doWork() throws Exception {
        ConsumerRecords<?, ?> records = consumer.poll(Duration.ofMillis(100));
        if (records.isEmpty()) {
            return 0;
        }

        for (ConsumerRecord<?, ?> record : records) {
            log.info("Key: " + record.key() + ", Value: " + record.value());
            log.info("Partition: " + record.partition() + ", Offset:" + record.offset());
        }

        output.publish(records);
        return records.count();
    }
}
