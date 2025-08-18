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

import com.fluxtion.runtime.lifecycle.Lifecycle;
import com.fluxtion.runtime.output.AbstractMessageSink;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;

@Log4j2
public class MulticastMessageSink extends AbstractMessageSink<Object> implements Lifecycle {

    @Getter
    @Setter
    private String multicastGroup = "230.0.0.1";
    @Getter
    @Setter
    private int multicastPort = 4446;
    @Getter
    @Setter
    private String networkInterfaceName;

    private InetAddress groupAddr;
    private DatagramSocket socket;
    private NetworkInterface netIf;

    @Override
    public void init() {
        try {
            groupAddr = InetAddress.getByName(multicastGroup);
            if (!groupAddr.isMulticastAddress()) {
                throw new IllegalArgumentException("Address is not multicast: " + multicastGroup);
            }
            if (networkInterfaceName != null && !networkInterfaceName.isEmpty()) {
                netIf = NetworkInterface.getByName(networkInterfaceName);
                if (netIf == null) {
                    log.warn("Network interface '{}' not found, default will be used", networkInterfaceName);
                }
            }
            // DatagramSocket is fine for sending to a multicast address.
            socket = new DatagramSocket();
            if (netIf != null) {
                try {
                    socket.setOption(StandardSocketOptions.IP_MULTICAST_IF, netIf);
                } catch (Exception e) {
                    log.debug("Could not set multicast interface option on DatagramSocket: {}", e.getMessage());
                }
            }
            log.info("MulticastMessageSink initialised group:{} port:{} iface:{}", multicastGroup, multicastPort, networkInterfaceName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialise MulticastMessageSink", e);
        }
    }

    @Override
    protected void sendToSink(Object value) {
        try {
            byte[] payload;
            if (value instanceof byte[] bytes) {
                payload = bytes;
            } else {
                // Default to UTF-8 encoding of toString()
                payload = String.valueOf(value).getBytes(StandardCharsets.UTF_8);
            }
            DatagramPacket packet = new DatagramPacket(payload, payload.length, groupAddr, multicastPort);
            socket.send(packet);
            log.trace("Multicast sink published {} bytes", payload.length);
        } catch (IOException e) {
            log.error("Error sending multicast packet", e);
        }
    }

    @Override
    public void tearDown() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Exception e) {
            // ignore
        }
    }
}
