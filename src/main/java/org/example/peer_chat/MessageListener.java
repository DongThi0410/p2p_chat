package org.example.peer_chat;

import java.util.List;

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

    // ==== Group chat callbacks (v1: chỉ dùng để test flow invite / create) ====

    /**
     * Được gọi khi có lời mời tham gia group mới.
     */
    default void onGroupInviteReceived(String groupId, String groupName, String owner, List<String> members) {
        // optional
    }

    /**
     * Được gọi khi group đã được tạo thành công (đủ người đồng ý).
     */
    default void onGroupCreated(String groupId, String groupName, String owner, List<String> members) {
        // optional
    }

    /**
     * Được gọi khi nhận tin nhắn trong group. (Sẽ dùng sau khi triển khai
     * GROUP_MSG)
     */
    default void onGroupMessage(String groupId, String from, String content) {
        // optional
    }

    // ==== Group management callbacks ====
    default void onGroupMemberLeft(String groupId, String member) {
    }

    default void onGroupRenamed(String groupId, String newName) {
    }

    default void onGroupMembersChanged(String groupId) {
    }

    default void onGroupFileReceived(String groupId, String from, String filename, String path, long size) {
    }
}
