/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
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
public class KafkaMessageConsumer extends AbstractAgentHostedEventSourceService {

    private KafkaConsumer<String, String> consumer;
    @Getter
    @Setter
    private Properties properties;
    @Getter
    @Setter
    private String name;

    protected KafkaMessageConsumer(String name) {
        super(name);
    }

    public KafkaMessageConsumer() {
        super("kafka-consumer");
    }

    @Override
    public void init() {

        System.out.println("properties:" + properties);
        log.info("properties:{}", properties);
//        String groupId = "my-application";
//        String bootstrapServers = "localhost:9092";

//        // create consumer configs
//        properties = new Properties();
//        properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
//        properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
//        properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
//        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId);
//        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        log.info("Initializing KafkaMessageConsumer {}", properties);
    }

    @Override
    public void start() {
        log.info("Starting KafkaMessageConsumer");
        final String topic = "CONNECTOR.TOPIC";
        // create consumer
        consumer = new KafkaConsumer<>(properties);
        // subscribe consumer to our topic(s)
        consumer.subscribe(List.of(topic));
    }

    @Override
    public void tearDown() {

    }


    @Override
    public int doWork() throws Exception {
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));

        for (ConsumerRecord<String, String> record : records) {
            log.info("Key: " + record.key() + ", Value: " + record.value());
            log.info("Partition: " + record.partition() + ", Offset:" + record.offset());
        }
        return records.count();
    }

    @Override
    public String roleName() {
        return name;
    }
}
