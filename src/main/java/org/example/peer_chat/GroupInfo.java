package org.example.peer_chat;

/**
 * Thông tin cơ bản về một group (id, name, owner).
 * Dùng cho UI/sidebar để hiển thị danh sách nhóm.
 */
public class GroupInfo {
    private final String id;
    private final String name;
    private final String owner;

    public GroupInfo(String id, String name, String owner) {
        this.id = id;
        this.name = name;
        this.owner = owner;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getOwner() {
        return owner;
    }
}
