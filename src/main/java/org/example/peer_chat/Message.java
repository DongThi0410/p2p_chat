package org.example.peer_chat;

public class Message {
    private final String fromUser;
    private final String toUser;
    private final String content;
    private final boolean file;
    private final String filePath;

    public Message(String fromUser, String toUser, String content, boolean file, String filePath) {
        this.fromUser = fromUser;
        this.toUser = toUser;
        this.content = content;
        this.file = file;
        this.filePath = filePath;
    }

    public String getFromUser() { return fromUser; }
    public String getToUser() { return toUser; }
    public String getContent() { return content; }
    public boolean isFile() { return file; }
    public String getFilePath() { return filePath; }
}