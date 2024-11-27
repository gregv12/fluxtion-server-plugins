package com.fluxtion.server.plugin.kafka;

import com.fluxtion.runtime.lifecycle.Lifecycle;
import com.fluxtion.runtime.output.MessageSink;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class KafkaPublisher implements Lifecycle, MessageSink<Object> {

    @Override
    public void init() {
        //publish this as a sink registration
    }

    @Override
    public void tearDown() {

    }


    @Override
    public void accept(Object o) {
        log.info("sink publish:{}", o);
    }
}
