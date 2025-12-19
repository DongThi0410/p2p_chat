package org.example.peer_chat;

public class CallRecord implements ChatItem {
    private final String fromUser;
    private final String toUser;
    private final long startTs;
    private final long duration; // giây
    private final boolean success;
    private final boolean video; // true = video call, false = voice call

    public CallRecord(String fromUser, String toUser,
                      long startTs, long duration, boolean success, boolean video) {
        this.fromUser = fromUser;
        this.toUser = toUser;
        this.startTs = startTs;
        this.duration = duration;
        this.success = success;
        this.video = video;
    }

    public String getFromUser() { return fromUser; }
    public String getToUser() { return toUser; }
    public long getDuration() { return duration; }
    public boolean isSuccess() { return success; }
    public boolean isVideo() { return video; }

    @Override
    public long getTimestamp() { return startTs; }
}
