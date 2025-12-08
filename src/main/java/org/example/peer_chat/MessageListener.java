package org.example.peer_chat;

public interface MessageListener {
    void onMessage(String sender, String msg);
    void onFileReceived(String sender, String filename, String absolutePath, long size);
    void onIncomingCall(String callerName, String callerIp, int callerVoicePort);
    void onCallStarted(String peerName);
    void onCallEnded(String peerName);
}

