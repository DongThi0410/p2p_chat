package org.example.peer_chat;

public interface MessageListener {
    void onMessage(String sender, String msg);
    void onFileReceived(String sender, String filename, String absolutePath, long size);
    void onIncomingCall(String callerName, String callerIp, int callerVoicePort);
    void onIncomingVideoCall(String callerName, String callerIp, int callerVoicePort);
    void onVoiceCallStarted(String peerName);
    void onVideoCallStarted(String peerName);
    void onCallEnded(String peerName);
    void onCallRejected(String peerName);

    void onRemoteVideoOn(String peerName);
    void onRemoteVideoOff(String peerName);
}

