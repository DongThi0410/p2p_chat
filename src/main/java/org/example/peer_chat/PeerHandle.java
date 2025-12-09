package org.example.peer_chat;

import javax.sound.sampled.LineUnavailableException;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PeerHandle {
    private final String name;
    private final ServerSocket serverSocket;
    private final int listenPort;
    private final ChatDb db;
    private final MessageHandler messageHandler;
    private final PeerDiscovery discovery;
    private final VoiceEngine voiceEngine;

    // cache: peerName -> "ip:tcpPort"
    private final Map<String, String> cachedPeers = new ConcurrentHashMap<>();

    private MessageListener listener;
    private volatile boolean inCall = false;
    private volatile String currentCallPeer = null;

    public PeerHandle(String name, ChatDb db) throws IOException, LineUnavailableException {
        this.name = name;
        this.db = db;

        this.serverSocket = new ServerSocket(0);
        this.listenPort = serverSocket.getLocalPort();

        // create local voice UDP port (random)
        int localVoicePort = chooseRandomPort();

        this.messageHandler = new MessageHandler(this.name, serverSocket, this::onIncomingMessage, this::onIncomingFile);
        this.voiceEngine = new VoiceEngine(localVoicePort);

        // discovery announces tcp port and voice port appended to addr via cached value
        this.discovery = new PeerDiscovery(this.name, this.listenPort, (peerName, addr) -> {
            // addr is "ip:tcpPort" — keep as-is, but we will request voice port through CALL_REQUEST
            String old = cachedPeers.put(peerName, addr);
            if (old == null || !old.equals(addr)) {
                System.out.println("[Discovered peer] " + peerName + " -> " + addr);
                if (listener != null) listener.onMessage("SYSTEM", "Peer online: " + peerName);
            }
        });

        System.out.printf("[PeerHandle] %s TCP:%d VOICE:%d%n", name, listenPort, localVoicePort);
    }

    private int chooseRandomPort() {
        try (java.net.ServerSocket s = new java.net.ServerSocket(0)) {
            return s.getLocalPort();
        } catch (IOException e) {
            return 7000 + new Random().nextInt(2000);
        }
    }

    public String getName() {
        return name;
    }

    public int getListenPort() {
        return listenPort;
    }

    public void setListener(MessageListener listener) {
        this.listener = listener;
    }

    public List<String> getPeerList() {
        return cachedPeers.keySet().stream().sorted().collect(Collectors.toList());
    }

    public String lookup(String peerName) {
        return cachedPeers.get(peerName);
    }

    public void sendToByName(String peerName, String message) {
        String addr = lookup(peerName);
        if (addr == null) {
            System.out.println("[sendToByName] Peer not found: " + peerName);
            return;
        }
        messageHandler.sendText(addr, message);
    }

    public void sendFileByName(String peerName, String filePath) {
        String addr = lookup(peerName);
        if (addr == null) {
            System.out.println("[sendFile] Peer not found: " + peerName);
            return;
        }
        try {
            messageHandler.sendFile(addr, filePath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // start a call: send request to target; when accept received, both sides will start voiceEngine
    public void startVoiceCall(String peerName) {
        if (inCall) {
            System.out.println("[Call] Already in call");
            return;
        }

        String addr = lookup(peerName);
        if (addr == null) {
            System.out.println("[Call] Peer not found");
            return;
        }

        currentCallPeer = peerName;
        inCall = true;
        if (listener != null) listener.onCallStarted(peerName);

        // addr = ip:tcpPort
        // send CALL_REQUEST|callerName|callerIp|callerVoicePort
        String myIp = getLocalAddress();
        String msg = "CALL_REQUEST|" + name + "|" + myIp + "|" + voiceEngine.getLocalPort();
        messageHandler.sendText(addr, msg);
        System.out.println("[Call] requested call to " + peerName + " via " + addr);
    }

    // called when user accepts an incoming CALL_REQUEST
    public void acceptCall(String callerName, String callerIp, int callerVoicePort) {

        if (inCall) return;
        currentCallPeer = callerName;
        inCall = true;
        if (listener != null) listener.onCallStarted(callerName);

        String addr = lookup(callerName);
        if (addr != null) {
            String myIp = getLocalAddress();
            String resp = "CALL_ACCEPT|" + name + "|" + myIp + "|" + voiceEngine.getLocalPort();
            messageHandler.sendText(addr, resp);
        }
        // start local voice engine to send/receive to caller
        voiceEngine.start(callerIp, callerVoicePort);
        System.out.println("[Call] accepted and started voice with " + callerName);
    }


    public void stopVoiceCall() {
        if (!inCall) return;
        voiceEngine.stop();
        if (currentCallPeer != null) {
            String addr = lookup(currentCallPeer);
            if (addr != null) {
                messageHandler.sendText(addr, "CALL_END|" + name);
            }
        }
        if (listener != null && currentCallPeer != null) listener.onCallEnded(currentCallPeer);
        inCall = false;
        currentCallPeer = null;
    }

    private void onIncomingMessage(String sender, String message) {
        // signaling: CALL_REQUEST|caller|ip|voicePort
        if (message != null && message.startsWith("CALL_REQUEST|")) {
            String[] p = message.split("\\|");
            if (p.length == 4) {
                String caller = p[1];
                String ip = p[2];
                int voicePort = Integer.parseInt(p[3]);

                // notify UI
                if (listener != null) listener.onIncomingCall(caller, ip, voicePort);
                return;
            }
        }

        if (message != null && message.startsWith("CALL_ACCEPT|")) {
            String[] p = message.split("\\|");
            if (p.length == 4) {
                String accepter = p[1];
                String ip = p[2];
                int voicePort = Integer.parseInt(p[3]);
                // other side accepted — start voice engine towards accepter
                voiceEngine.start(ip, voicePort);
                System.out.println("[Call] remote accepted. starting voice to " + accepter + "@" + ip + ":" + voicePort);
                return;
            }
        }
        if (message != null && message.startsWith("CALL_END|")) {
            String[] p = message.split("\\|");
            String ender = p[1];

            voiceEngine.stop();
            inCall = false;

            if (listener != null)
                listener.onCallEnded(ender);

            return;
        }

        // normal chat message
        db.insertMessage(new Message(sender, name, message, false, null));
        if (listener != null) listener.onMessage(sender, message);
    }

    private void onIncomingFile(String sender, String filename, String absPath, long size) {
        db.insertMessage(new Message(sender, name, filename, true, absPath));
        if (listener != null) listener.onFileReceived(sender, filename, absPath, size);
    }

    public void shutdown() {
        discovery.stop();
        messageHandler.stop();
        try {
            serverSocket.close();
        } catch (IOException ignored) {
        }
        voiceEngine.stop();
    }

    // helper to get local IP (best-effort)
    private String getLocalAddress() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;

                Enumeration<InetAddress> adds = ni.getInetAddresses();
                while (adds.hasMoreElements()) {
                    InetAddress a = adds.nextElement();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        return a.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

}