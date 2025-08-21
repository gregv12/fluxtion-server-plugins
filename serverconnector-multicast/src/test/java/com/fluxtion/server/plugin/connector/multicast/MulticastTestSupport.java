package com.fluxtion.server.plugin.connector.multicast;

import java.net.NetworkInterface;
import java.util.Enumeration;

final class MulticastTestSupport {

    private MulticastTestSupport() {}

    static boolean isMulticastAvailable() {
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs != null && ifs.hasMoreElements()) {
                NetworkInterface ni = ifs.nextElement();
                try {
                    if (ni.isUp() && !ni.isLoopback() && ni.supportsMulticast()) {
                        return true;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return false;
    }

    static boolean canSendAndReceive(String group, int port) {
        java.net.MulticastSocket recv = null;
        java.net.DatagramSocket send = null;
        try {
            java.net.InetAddress groupAddr = java.net.InetAddress.getByName(group);
            recv = new java.net.MulticastSocket(port);
            recv.setReuseAddress(true);
            recv.setSoTimeout(200);
            // Try to enable loopback where supported
            try { recv.setLoopbackMode(false); } catch (Exception ignored) {}
            recv.joinGroup(groupAddr);

            byte[] probe = new byte[]{0x55};
            java.net.DatagramPacket pkt = new java.net.DatagramPacket(probe, probe.length, groupAddr, port);
            send = new java.net.DatagramSocket();
            send.send(pkt);

            byte[] buf = new byte[8];
            java.net.DatagramPacket in = new java.net.DatagramPacket(buf, buf.length);
            long end = System.currentTimeMillis() + 500;
            while (System.currentTimeMillis() < end) {
                try {
                    recv.receive(in);
                    return true;
                } catch (java.net.SocketTimeoutException ignored) {
                    // retry
                }
            }
        } catch (Exception ignored) {
            // fall through
        } finally {
            try { if (recv != null) { try { recv.leaveGroup(java.net.InetAddress.getByName(group)); } catch (Exception ignored) {} recv.close(); } } catch (Exception ignored) {}
            try { if (send != null) send.close(); } catch (Exception ignored) {}
        }
        return false;
    }
}
