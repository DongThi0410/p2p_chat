package org.example.peer_chat;

import javax.sound.sampled.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.function.Consumer;
public class VoiceCallHandler {

    private final String selfName;
    private final int voicePort;
    private final Consumer<String> incomingCallCallback;

    private volatile boolean running = true;

    public VoiceCallHandler(String selfName, int voicePort, Consumer<String> incomingCallCallback) {
        this.selfName = selfName;
        this.voicePort = voicePort;
        this.incomingCallCallback = incomingCallCallback;

        startListener();
    }

    private void startListener() {
        new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(voicePort)) {
                System.out.println("[Voice] Listening on port " + voicePort);

                while (running) {
                    Socket s = ss.accept();

                    DataInputStream dis = new DataInputStream(s.getInputStream());
                    String type = dis.readUTF();

                    if ("CALL".equals(type)) {
                        String caller = dis.readUTF();
                        incomingCallCallback.accept(caller);
                    }
                }
            } catch (Exception e) {
                if (running) System.err.println("[Voice] Listener error: " + e.getMessage());
            }
        }, "voice-listener").start();
    }

    public void startCall(String targetIp, int targetPort, String callerName) {
        new Thread(() -> {
            try (Socket s = new Socket(targetIp, targetPort);
                 DataOutputStream dos = new DataOutputStream(s.getOutputStream())) {

                dos.writeUTF("CALL");
                dos.writeUTF(callerName);
                dos.flush();

                System.out.println("[Voice] Calling " + targetIp + ":" + targetPort);

            } catch (Exception e) {
                System.err.println("[Voice] Cannot start call: " + e.getMessage());
            }
        }).start();
    }

    public void stop() {
        running = false;
    }
    public void acceptCall(String caller) {
        System.out.println("[Voice] Call accepted from " + caller);
        // TODO: start audio stream
    }

    public void rejectCall(String caller) {
        System.out.println("[Voice] Call rejected from " + caller);
        // TODO: notify caller or just ignore
    }

}
