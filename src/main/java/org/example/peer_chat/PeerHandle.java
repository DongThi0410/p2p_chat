package org.example.peer_chat;

import javax.sound.sampled.LineUnavailableException;
import java.io.IOException;
import java.net.*;
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
    private VoiceEngine voiceEngine;
    private VideoEngine videoEngine;

    // cache: peerName -> "ip:tcpPort"
    private final Map<String, String> cachedPeers = new ConcurrentHashMap<>();

    private MessageListener listener;
    private volatile boolean inCall = false;
    private volatile String currentCallPeer = null;
    int localVoicePort;
    int localVideoPort;
    // lưu tạm videoPort của caller khi nhận CALL_REQUEST_VIDEO, dùng cho acceptVideoCall()
    private volatile int pendingCallerVideoPort = -1;

    public PeerHandle(String name, ChatDb db) throws IOException {
        this.name = name;
        this.db = db;

        this.serverSocket = new ServerSocket(0);
        this.listenPort = serverSocket.getLocalPort();

        localVoicePort = chooseRandomPort();
        localVideoPort = chooseRandomPort();

        this.messageHandler = new MessageHandler(this.name, serverSocket, this::onIncomingMessage,
                this::onIncomingFile);

        this.discovery = new PeerDiscovery(this.name, this.listenPort, (peerName, addr) -> {

            String old = cachedPeers.put(peerName, addr);
            if (old == null || !old.equals(addr)) {
                System.out.println("[Discovered peer] " + peerName + " -> " + addr);
                if (listener != null)
                    listener.onMessage("SYSTEM", "Peer online: " + peerName);
            }
        });

        System.out.printf("[PeerHandle] %s TCP:%d VOICE:%d VIDEO:%d%n", name, listenPort, localVoicePort, localVideoPort);
    }

    private VoiceEngine getVoiceEngine() throws LineUnavailableException, SocketException {
        if (voiceEngine == null) {
            voiceEngine = new VoiceEngine(localVoicePort);
        }
        return voiceEngine;
    }

    private VideoEngine getVideoEngine() {
        if (videoEngine == null) {
            videoEngine = new VideoEngine(localVideoPort);
        }
        return videoEngine;
    }

    // Expose video engine for UI controllers (e.g. VideoCallModalController)
    public VideoEngine getVideoEngineForUi() {
        return getVideoEngine();
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

    public void setListener(MessageListener listener) {
        this.listener = listener;
    }

    public List<String> getPeerList() {
        return cachedPeers.keySet().stream().sorted().collect(Collectors.toList());
    }

    public void removePeer(String peerName) {
        cachedPeers.remove(peerName);
        if (listener != null) {
            listener.onMessage("SYSTEM", "Peer offline: " + peerName);
            System.out.println("[removePeer] Peer offline: " + peerName);
        }
    }

    public void broadcastOffline() {
        for (String peerName : cachedPeers.keySet()) {
            sendToByName(peerName, "SYSTEM|OFFLINE|" + name);
        }
    }

    public void logout() {
        broadcastOffline();    // gửi tới các peer khác trước
        removePeer(name);  // xóa khỏi cachedPeers của chính mình
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

    // start a call: send request to target; when accept received, both sides will
    // start voiceEngine
    public void startVoiceCall(String peerName) throws SocketException, LineUnavailableException {
        if (inCall) {
            System.out.println("[Call] Already in call");
            return;
        }

        String addr = lookup(peerName);
        if (addr == null) {
            System.out.println("[Call] Peer not found");
            return;
        }

        // Đánh dấu đang trong quá trình gọi (chưa được accept)
        // KHÔNG set inCall = true và KHÔNG gọi onCallStarted() ở đây
        // onCallStarted() chỉ được gọi khi nhận CALL_ACCEPT
        currentCallPeer = peerName;

        // addr = ip:tcpPort
        // send CALL_REQUEST|callerName|callerIp|callerVoicePort
        String myIp = getLocalAddress();
        String msg = "CALL_REQUEST|" + name + "|" + myIp + "|" + getVoiceEngine().getLocalPort();

        messageHandler.sendText(addr, msg);
        System.out.println("[Call] requested call to " + peerName + " via " + addr);
    }

    // start a video call: similar to voice call but with VIDEO signaling
    public void startVideoCall(String peerName) throws SocketException, LineUnavailableException {
        if (inCall) {
            System.out.println("[VideoCall] Already in call");
            return;
        }

        String addr = lookup(peerName);
        if (addr == null) {
            System.out.println("[VideoCall] Peer not found");
            return;
        }

        // Đánh dấu đang trong quá trình gọi video (chưa được accept)
        currentCallPeer = peerName;

        // send CALL_REQUEST_VIDEO|callerName|callerIp|callerVoicePort|callerVideoPort
        String myIp = getLocalAddress();
        String msg = "CALL_REQUEST_VIDEO|" + name + "|" + myIp + "|" + getVoiceEngine().getLocalPort() + "|" + getVideoEngine().getLocalPort();
        messageHandler.sendText(addr, msg);
        System.out.println("[VideoCall] requested video call to " + peerName + " via " + addr);
    }

    // called when user accepts an incoming CALL_REQUEST
    public void acceptCall(String callerName, String callerIp, int callerVoicePort) throws SocketException, LineUnavailableException {

        if (inCall)
            return;
        currentCallPeer = callerName;
        inCall = true;

        String addr = lookup(callerName);

        if (addr != null) {
            String myIp = getLocalAddress();
            String resp = "CALL_ACCEPT|" + name + "|" + myIp + "|" + getVoiceEngine().getLocalPort();
            messageHandler.sendText(addr, resp);
        }
        // start local voice engine to send/receive to caller
        getVoiceEngine().start(callerIp, callerVoicePort);
        System.out.println("[Call] accepted and started voice with " + callerName);

        // Notify UI that call has started (for side B - the accepter)
        if (listener != null) {
            listener.onVoiceCallStarted(callerName);
        }
    }

    // called when user accepts an incoming CALL_REQUEST_VIDEO
    public void acceptVideoCall(String callerName, String callerIp, int callerVoicePort) throws SocketException, LineUnavailableException {
        if (inCall)
            return;

        currentCallPeer = callerName;
        inCall = true;

        String addr = lookup(callerName);

        if (addr != null) {
            String myIp = getLocalAddress();
            String resp = "CALL_ACCEPT_VIDEO|" + name + "|" + myIp + "|" + getVoiceEngine().getLocalPort() + "|" + getVideoEngine().getLocalPort();
            messageHandler.sendText(addr, resp);
        }

        // callerVoicePort là cổng voice của caller, pendingCallerVideoPort là cổng video
        int callerVideoPort = pendingCallerVideoPort;
        pendingCallerVideoPort = -1;

        getVoiceEngine().start(callerIp, callerVoicePort);
        if (callerVideoPort > 0) {
            getVideoEngine().start(callerIp, callerVideoPort);
        }
        System.out.println("[VideoCall] accepted and started video call with " + callerName);

        // Notify UI that video call has started (for side B - the accepter)
        if (listener != null) {
            listener.onVideoCallStarted(callerName);
        }
    }

    public void stopVoiceCall() throws SocketException, LineUnavailableException {
        System.out.println("[Peer] stopVoiceCall()");
        System.out.println("[Peer] inCall=" + inCall);
        System.out.println("[Peer] currentCallPeer=" + currentCallPeer);
        String peer = currentCallPeer;

        getVoiceEngine().stop();

        if (peer != null) {
            String addr = lookup(peer);
            if (addr != null) {
                System.out.println("[Peer] sending CALL_END to " + peer);
                messageHandler.sendText(addr, "CALL_END|" + name);
            }
        }
        inCall = false;
        currentCallPeer = null;

        // Notify listener that call has ended
        if (listener != null) {
            listener.onCallEnded(peer);
        }
    }

    private void onIncomingMessage(String sender, String message) throws SocketException, LineUnavailableException {
        // signaling: CALL_REQUEST|caller|ip|voicePort
        if (message != null && message.startsWith("SYSTEM|OFFLINE|")) {
            String offlineUser = message.split("\\|")[2];
            cachedPeers.remove(offlineUser);

            if (listener != null) {
                listener.onMessage("SYSTEM", "Peer offline:" + offlineUser);
            }
            return;
        }
        if (message != null && message.startsWith("CALL_REQUEST|")) {
            String[] p = message.split("\\|");
            if (p.length == 4) {
                String caller = p[1];
                String ip = p[2];
                int voicePort = Integer.parseInt(p[3]);

                // notify UI
                if (listener != null)
                    listener.onIncomingCall(caller, ip, voicePort);
                return;
            }
        }

        // signaling: CALL_REQUEST_VIDEO|caller|ip|voicePort|videoPort
        if (message != null && message.startsWith("CALL_REQUEST_VIDEO|")) {
            String[] p = message.split("\\|");
            if (p.length == 5) {
                String caller = p[1];
                String ip = p[2];
                int voicePort = Integer.parseInt(p[3]);
                int videoPort = Integer.parseInt(p[4]);

                // Lưu lại cổng video của caller để acceptVideoCall() dùng khi start VideoEngine
                pendingCallerVideoPort = videoPort;

                System.out.println("[VideoCall] incoming request from " + caller + " voicePort=" + voicePort + " videoPort=" + videoPort);

                if (listener != null)
                    listener.onIncomingVideoCall(caller, ip, voicePort);
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
                currentCallPeer = accepter;
                inCall = true;
                getVoiceEngine().start(ip, voicePort);
                System.out
                        .println("[Call] remote accepted. starting voice to " + accepter + "@" + ip + ":" + voicePort);

                // notify UI so caller side can transition from Calling.fxml to VoiceCall UI
                if (listener != null)
                    listener.onVoiceCallStarted(accepter);
                return;
            }
        }

        if (message != null && message.startsWith("CALL_ACCEPT_VIDEO|")) {
            String[] p = message.split("\\|");
            if (p.length == 5) {
                String accepter = p[1];
                String ip = p[2];
                int voicePort = Integer.parseInt(p[3]);
                int videoPort = Integer.parseInt(p[4]);
                // other side accepted video call — start engines towards accepter
                currentCallPeer = accepter;
                inCall = true;
                getVoiceEngine().start(ip, voicePort);
                getVideoEngine().start(ip, videoPort);
                System.out.println("[VideoCall] remote accepted. starting video call to " + accepter + "@" + ip + ":" + voicePort + " videoPort=" + videoPort);

                // notify UI so caller side can transition from "Đang gọi..." to VideoCallModal UI
                if (listener != null)
                    listener.onVideoCallStarted(accepter);
                return;
            }
        }

        if (message != null && message.startsWith("CALL_END|")) {
            String[] p = message.split("\\|");
            if (p.length >= 2) {  // Add length check for safety
                String ender = p[1];
                System.out.println("[Peer] Received CALL_END from " + ender);

                // Stop voice/video engines and clean up
                getVoiceEngine().stop();
                if (videoEngine != null) {
                    videoEngine.stop();
                }

                inCall = false;
                currentCallPeer = null;

                // Notify UI
                if (listener != null) {
                    listener.onCallEnded(ender);
                }
            }
            return;  // Important: return after handling CALL_END
        }

        if (message != null && message.startsWith("CALL_REJECT|")) {
            String[] p = message.split("\\|");
            String rejecter = p[1];

            System.out.println("[Call] rejected by " + rejecter);

            inCall = false;
            currentCallPeer = null;

            if (listener != null) {
                listener.onCallRejected(rejecter);
            }
            return;
        }

        // normal chat message
        db.insertMessage(new Message(sender, name, message, false, null));
        if (listener != null)
            listener.onMessage(sender, message);
    }

    private void onIncomingFile(String sender, String filename, String absPath, long size) {
        db.insertMessage(new Message(sender, name, filename, true, absPath));
        if (listener != null)
            listener.onFileReceived(sender, filename, absPath, size);
    }

    public void rejectCall(String callerName) {
        String addr = lookup(callerName);
        if (addr != null) {
            messageHandler.sendText(addr, "CALL_REJECT|" + name);
        }
    }

    public void shutdown() throws SocketException, LineUnavailableException {
        discovery.stop();
        messageHandler.stop();
        try {
            serverSocket.close();
        } catch (IOException ignored) {}

        if (voiceEngine != null) {
            voiceEngine.shutdown();

            voiceEngine = null;
        }
    }

    // helper to get local IP (best-effort)
    private String getLocalAddress() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual())
                    continue;

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