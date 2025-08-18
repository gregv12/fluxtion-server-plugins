package com.fluxtion.server.plugin.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KafkaMessagePublisherTest {

    @Test
    void sendToSink_sendsRecordAndFlushes_whenFlushEveryMessageTrue() {
        @SuppressWarnings("unchecked")
        KafkaProducer<Object, Object> mockProducer = mock(KafkaProducer.class);

        // Testable publisher overriding producer creation
        KafkaMessagePublisher pub = new KafkaMessagePublisher() {
            @Override
            protected KafkaProducer<Object, Object> createProducer(Properties props) {
                return mockProducer;
            }
        };
        pub.setTopic("test-topic");
        pub.setFlushEveryMessage(true);
        pub.setProperties(new Properties());

        pub.init();
        pub.sendToSink("hello");

        ArgumentCaptor<ProducerRecord<Object, Object>> recCap = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(mockProducer, times(1)).send(recCap.capture());
        ProducerRecord<Object, Object> sent = recCap.getValue();
        assertEquals("test-topic", sent.topic());
        assertEquals("hello", sent.value());
        verify(mockProducer, times(1)).flush();

        pub.tearDown();
        // tearDown should flush and close
        verify(mockProducer, atLeastOnce()).flush();
        verify(mockProducer, times(1)).close();
    }

    @Test
    void doWork_flushesWhenBufferSet_andNoPerMessageFlush() throws Exception {
        @SuppressWarnings("unchecked")
        KafkaProducer<Object, Object> mockProducer = mock(KafkaProducer.class);

        KafkaMessagePublisher pub = new KafkaMessagePublisher() {
            @Override
            protected KafkaProducer<Object, Object> createProducer(Properties props) {
                return mockProducer;
            }
        };
        pub.setTopic("topic");
        pub.setFlushEveryMessage(false);
        pub.setProperties(new Properties());
        pub.init();

        pub.sendToSink("v1"); // marks buffer for flush
        // doWork should perform a flush once
        int work = pub.doWork();
        assertEquals(0, work); // doWork returns 0 in current implementation
        verify(mockProducer, times(1)).flush();

        // second doWork should not flush again since flag was reset
        pub.doWork();
        verify(mockProducer, times(1)).flush();
    }
}
