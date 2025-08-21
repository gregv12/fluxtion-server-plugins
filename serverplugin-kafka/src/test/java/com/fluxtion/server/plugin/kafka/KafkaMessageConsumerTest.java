package com.fluxtion.server.plugin.kafka;

import com.fluxtion.server.dispatch.EventToQueuePublisher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KafkaMessageConsumerTest {

    @Test
    void start_subscribes_andDoWorkPublishesAndReturnsCount_thenEmptyReturnsZero_andTearDownCloses() throws Exception {
        @SuppressWarnings("unchecked")
        KafkaConsumer<String, String> mockConsumer = mock(KafkaConsumer.class);

        // Build records for one poll
        String topic = "topicA";
        ConsumerRecord<String, String> r1 = new ConsumerRecord<>(topic, 0, 10, "k1", "v1");
        ConsumerRecord<String, String> r2 = new ConsumerRecord<>(topic, 0, 11, "k2", "v2");
        Map<TopicPartition, List<ConsumerRecord<String, String>>> recMap = new HashMap<>();
        recMap.put(new TopicPartition(topic, 0), List.of(r1, r2));
        ConsumerRecords<String, String> some = new ConsumerRecords<>(recMap);

        when(mockConsumer.poll(any(Duration.class)))
                .thenReturn((ConsumerRecords) some)
                .thenReturn(ConsumerRecords.empty());

        // Testable consumer overriding creation
        KafkaMessageConsumer cons = new KafkaMessageConsumer() {
            @Override
            protected KafkaConsumer<String, String> createConsumer(Properties props) {
                return mockConsumer;
            }
        };
        cons.setProperties(new Properties());
        cons.setTopics(new String[]{topic});

        // Capture publisher
        AtomicReference<ConsumerRecords<?, ?>> published = new AtomicReference<>();
        AtomicInteger publishCount = new AtomicInteger();
        EventToQueuePublisher<ConsumerRecords<?, ?>> capPublisher = new EventToQueuePublisher<>("test") {
            @Override
            public void publish(ConsumerRecords<?, ?> event) {
                published.set(event);
                publishCount.incrementAndGet();
            }
        };
        cons.setOutput(capPublisher);

        cons.start();
        // verify subscribe
        verify(mockConsumer, times(1)).subscribe(eq(List.of(topic)));

        int work1 = cons.doWork();
        assertEquals(2, work1);
        assertNotNull(published.get());
        assertEquals(1, publishCount.get());

        int work2 = cons.doWork();
        assertEquals(0, work2);
        assertEquals(1, publishCount.get());

        cons.tearDown();
        verify(mockConsumer, times(1)).close();
    }
}
