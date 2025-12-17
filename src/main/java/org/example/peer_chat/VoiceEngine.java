package org.example.peer_chat;

import javax.sound.sampled.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class VoiceEngine {
    private final int localPort;
    private DatagramSocket socket;
    private TargetDataLine mic;
    private SourceDataLine speaker;
    private volatile boolean running = false;

    public VoiceEngine(int localPort) throws LineUnavailableException, SocketException {
        this.localPort = localPort;
        socket = new DatagramSocket(localPort);
        AudioFormat fmt = new AudioFormat(16000.0f, 16, 1, true, false);
        DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, fmt);
        DataLine.Info spkInfo = new DataLine.Info(SourceDataLine.class, fmt);
        mic = (TargetDataLine) AudioSystem.getLine(micInfo);
        mic.open(fmt);
        speaker = (SourceDataLine) AudioSystem.getLine(spkInfo);
        speaker.open(fmt);
    }

    public void start(String remoteIp, int remotePort) {
        if (running) {
            // Nếu đang chạy, dừng trước khi start lại
            stop();
        }
        
        // Tạo lại socket nếu đã bị đóng hoặc null
        if (socket == null || socket.isClosed()) {
            try {
                socket = new DatagramSocket(localPort);
            } catch (SocketException e) {
                e.printStackTrace();
                return;
            }
        }
        
        // Tạo lại audio lines nếu đã bị đóng hoặc null
        try {
            AudioFormat fmt = new AudioFormat(16000.0f, 16, 1, true, false);
            if (mic == null || !mic.isOpen()) {
                DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, fmt);
                mic = (TargetDataLine) AudioSystem.getLine(micInfo);
                mic.open(fmt);
            }
            if (speaker == null || !speaker.isOpen()) {
                DataLine.Info spkInfo = new DataLine.Info(SourceDataLine.class, fmt);
                speaker = (SourceDataLine) AudioSystem.getLine(spkInfo);
                speaker.open(fmt);
            }
        } catch (LineUnavailableException e) {
            e.printStackTrace();
            return;
        }
        
        running = true;
        mic.start();
        speaker.start();

        InetAddress remote;
        try {
            remote = InetAddress.getByName(remoteIp);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        // sender
        new Thread(() -> {
            byte[] buf = new byte[2048];
            try {
                while (running) {
                    int r = mic.read(buf, 0, buf.length);
                    if (r > 0) {
                        DatagramPacket p = new DatagramPacket(buf, r, remote, remotePort);
                        socket.send(p);
                    }
                }
            } catch (Exception e) {
                if (running) e.printStackTrace();
            }
        }, "voice-send").start();

        // receiver
        new Thread(() -> {
            byte[] buf = new byte[2048];
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            try {
                while (running) {
                    socket.receive(p);
                    speaker.write(p.getData(), 0, p.getLength());
                }
            } catch (Exception e) {
                if (running) e.printStackTrace();
            }
        }, "voice-recv").start();
    }

    public void stop() {
        running = false;
        try { mic.stop(); mic.close(); } catch (Exception ignored) {}
        try { speaker.stop(); speaker.close(); } catch (Exception ignored) {}
        // KHÔNG đóng socket ở đây để có thể reuse cho lần gọi tiếp theo
        // Socket sẽ được đóng khi PeerHandle shutdown hoàn toàn
    }
    
    /**
     * Đóng socket hoàn toàn. Chỉ gọi khi PeerHandle shutdown.
     */
    public void shutdown() {
        running = false;
        try { mic.stop(); mic.close(); } catch (Exception ignored) {}
        try { speaker.stop(); speaker.close(); } catch (Exception ignored) {}
        try { 
            if (socket != null && !socket.isClosed()) {
                socket.close(); 
            }
        } catch (Exception ignored) {}
    }

    public int getLocalPort() { return localPort; }
}