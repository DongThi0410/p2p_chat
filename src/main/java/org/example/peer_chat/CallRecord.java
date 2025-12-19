package org.example.peer_chat;

public class CallRecord implements ChatItem {
    private final String fromUser;
    private final String toUser;
    private final long startTs;
    private final long duration; // giây
    private final boolean success;

    public CallRecord(String fromUser, String toUser,
                      long startTs, long duration, boolean success) {
        this.fromUser = fromUser;
        this.toUser = toUser;
        this.startTs = startTs;
        this.duration = duration;
        this.success = success;
    }

    public String getFromUser() { return fromUser; }
    public String getToUser() { return toUser; }
    public long getDuration() { return duration; }
    public boolean isSuccess() { return success; }

    @Override
    public long getTimestamp() { return startTs; }
}
