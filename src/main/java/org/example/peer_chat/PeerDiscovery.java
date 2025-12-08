package org.example.peer_chat;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicBoolean;
public class PeerDiscovery {

    public interface DiscoveryCallback {
        void onPeerOnline(String name, String addr);
    }

    private final String selfName;
    private final int listenPort;
    private final DiscoveryCallback callback;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private final String MCAST_ADDR = "230.0.0.0";
    private final int MCAST_PORT = 9999;
    private MulticastSocket mcastSocket;
    private InetAddress group;
    private NetworkInterface ni;

    public PeerDiscovery(String selfName, int listenPort, DiscoveryCallback callback) throws IOException {
        this.selfName = selfName;
        this.listenPort = listenPort;
        this.callback = callback;

        initMulticastSocket();
        startBroadcastLoop();
        startListenLoop();
    }

    private void initMulticastSocket() throws IOException {
        group = InetAddress.getByName(MCAST_ADDR);

        // tìm network interface LAN
        ni = getLANNetworkInterface();
        if (ni == null) throw new IOException("Cannot find LAN network interface");

        mcastSocket = new MulticastSocket(MCAST_PORT);
        mcastSocket.setReuseAddress(true);
        mcastSocket.joinGroup(new InetSocketAddress(group, MCAST_PORT), ni);

        System.out.println("[PeerDiscovery] Joined multicast group " + MCAST_ADDR + ":" + MCAST_PORT +
                " on interface " + ni.getDisplayName());
    }

    private NetworkInterface getLANNetworkInterface() throws SocketException {
        Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
        while (nis.hasMoreElements()) {
            NetworkInterface n = nis.nextElement();
            if (!n.isUp() || n.isLoopback() || n.isVirtual()) continue;

            // kiểm tra có IPv4
            Enumeration<InetAddress> addrs = n.getInetAddresses();
            while (addrs.hasMoreElements()) {
                InetAddress a = addrs.nextElement();
                if (a instanceof Inet4Address && !a.isLoopbackAddress()) return n;
            }
        }
        return null;
    }

    private void startBroadcastLoop() {
        Thread t = new Thread(() -> {
            try (DatagramSocket sender = new DatagramSocket()) {
                sender.setBroadcast(true);
                String msg = "HELLO|" + selfName + "|" + listenPort;
                byte[] buf = msg.getBytes(StandardCharsets.UTF_8);
                DatagramPacket packet = new DatagramPacket(buf, buf.length, group, MCAST_PORT);

                while (running.get()) {
                    try {
                        sender.send(packet);
                        Thread.sleep(2000);
                    } catch (Exception e) {
                        System.err.println("[PeerDiscovery broadcast] " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                if (running.get()) System.err.println("[PeerDiscovery broadcast] fatal: " + e.getMessage());
            }
        }, "udp-broadcast");
        t.setDaemon(true);
        t.start();
    }

    private void startListenLoop() {
        Thread t = new Thread(() -> {
            byte[] buf = new byte[512];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            while (running.get()) {
                try {
                    mcastSocket.receive(packet);
                    String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    String[] p = msg.split("\\|");
                    if (p.length == 3 && "HELLO".equals(p[0])) {
                        String peerName = p[1];
                        int port = Integer.parseInt(p[2]);
                        if (!peerName.equals(selfName)) {
                            String addr = packet.getAddress().getHostAddress() + ":" + port;
                            callback.onPeerOnline(peerName, addr);
                        }
                    }
                } catch (IOException e) {
                    if (running.get()) System.err.println("[PeerDiscovery listen] " + e.getMessage());
                }
            }
        }, "udp-listen");
        t.setDaemon(true);
        t.start();
    }

    public void stop() {
        running.set(false);
        try {
            if (mcastSocket != null) mcastSocket.leaveGroup(new InetSocketAddress(group, MCAST_PORT), ni);
            if (mcastSocket != null) mcastSocket.close();
        } catch (Exception ignored) {}
    }
}