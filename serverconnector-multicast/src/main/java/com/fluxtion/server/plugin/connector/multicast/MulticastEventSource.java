/*
 * Copyright (c) 2025 gregory higgins.
 * All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program.  If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */

package com.fluxtion.server.plugin.connector.multicast;

import com.fluxtion.runtime.event.NamedFeedEvent;
import com.fluxtion.server.dispatch.EventToQueuePublisher;
import com.fluxtion.server.service.AbstractAgentHostedEventSourceService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Log4j2
@SuppressWarnings("all")
public class MulticastEventSource extends AbstractAgentHostedEventSourceService {

    @Getter
    @Setter
    private String multicastGroup = "230.0.0.1";
    @Getter
    @Setter
    private int multicastPort = 4446;
    @Getter
    @Setter
    private String networkInterfaceName;
    @Getter
    @Setter
    private boolean cacheEventLog = false;

    private volatile boolean publishToQueue = false;

    private MulticastSocket socket;
    private InetAddress groupAddr;
    private NetworkInterface netIf;

    public MulticastEventSource() {
        this("multicast-event-source");
    }

    public MulticastEventSource(String name) {
        super(name);
    }

    @Override
    public void onStart() {
        try {
            groupAddr = InetAddress.getByName(multicastGroup);
            if (!groupAddr.isMulticastAddress()) {
                throw new IllegalArgumentException("Address is not multicast: " + multicastGroup);
            }
            socket = new MulticastSocket(multicastPort);
            socket.setReuseAddress(true);
            socket.setSoTimeout(1);
            if (networkInterfaceName != null && !networkInterfaceName.isEmpty()) {
                netIf = NetworkInterface.getByName(networkInterfaceName);
                if (netIf != null) {
                    socket.setNetworkInterface(netIf);
                } else {
                    log.warn("Network interface '{}' not found, default will be used", networkInterfaceName);
                }
            }
            // Join group
            if (netIf != null) {
                socket.joinGroup(new InetSocketAddress(groupAddr, multicastPort), netIf);
            } else {
                socket.joinGroup(groupAddr);
            }

            output.setCacheEventLog(cacheEventLog);
            if (cacheEventLog) {
                log.info("cacheEventLog: {}", cacheEventLog);
                doWork();
            }
            log.info("MulticastEventSource started group:{} port:{} iface:{}", multicastGroup, multicastPort, networkInterfaceName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to start MulticastEventSource", e);
        }
    }

    @Override
    public void startComplete() {
        publishToQueue = true;
        output.dispatchCachedEventLog();
    }

    @Override
    public <T> NamedFeedEvent<T>[] eventLog() {
        List<NamedFeedEvent> eventLog = output.getEventLog();
        return (NamedFeedEvent<T>[]) eventLog.toArray(new NamedFeedEvent[0]);
    }

    @Override
    public int doWork() {
        if (socket == null) return 0;
        int count = 0;
        try {
            while (true) {
                byte[] buf = new byte[64 * 1024];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet); // will timeout quickly due to soTimeout
                int len = packet.getLength();
                if (len > 0) {
                    String msg = new String(packet.getData(), packet.getOffset(), len, StandardCharsets.UTF_8);
                    publish(msg);
                    count++;
                }
            }
        } catch (SocketTimeoutException e) {
            // normal - no more data available now
        } catch (IOException e) {
            log.error("Error receiving multicast packet", e);
        }
        return count;
    }

    private void publish(Object o) {
        if (publishToQueue) {
            log.debug("publish record:{}", o);
            output.publish(o);
        } else {
            log.debug("cache record:{}", o);
            output.cache(o);
        }
    }

    @Override
    public void tearDown() {
        try {
            if (socket != null) {
                try {
                    if (netIf != null) {
                        socket.leaveGroup(new InetSocketAddress(groupAddr, multicastPort), netIf);
                    } else {
                        socket.leaveGroup(groupAddr);
                    }
                } catch (Exception ignored) {
                }
                socket.close();
            }
        } catch (Exception e) {
            // ignore
        }
    }

    //for testing
    void setOutput(EventToQueuePublisher<String> eventToQueue) {
        this.output = eventToQueue;
    }
}
