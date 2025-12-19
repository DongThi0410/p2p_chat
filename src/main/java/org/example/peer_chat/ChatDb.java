package org.example.peer_chat;

import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ChatDb {

    private final String url;

    public ChatDb(String dbPath) {
        this.url = "jdbc:sqlite:" + dbPath;
        init();
    }

    private void init() {

        String sqlUser = "CREATE TABLE IF NOT EXISTS users ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "username TEXT UNIQUE NOT NULL,"
                + "password_hash TEXT NOT NULL"
                + ")";

        String sqlMessages = "CREATE TABLE IF NOT EXISTS messages ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "from_user TEXT,"
                + "to_user TEXT,"
                + "content TEXT,"
                + "is_file INTEGER,"
                + "file_path TEXT,"
                + "ts INTEGER"
                + ");";

        String sqlCalls = "CREATE TABLE IF NOT EXISTS call_history ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "from_user TEXT,"
                + "to_user TEXT,"
                + "is_video INTEGER,"
                + "timestamp INTEGER,"
                + "duration_seconds INTEGER,"
                + "accepted INTEGER"
                + ");";

        String sqlGroups = "CREATE TABLE IF NOT EXISTS groups ("
                + "id TEXT PRIMARY KEY,"
                + "name TEXT NOT NULL,"
                + "owner TEXT NOT NULL"
                + ");";

        String sqlGroupMembers = "CREATE TABLE IF NOT EXISTS group_members ("
                + "group_id TEXT NOT NULL,"
                + "member_name TEXT NOT NULL,"
                + "PRIMARY KEY (group_id, member_name),"
                + "FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE"
                + ");";

        String sqlGroupMessages = "CREATE TABLE IF NOT EXISTS group_messages ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "group_id TEXT NOT NULL,"
                + "from_user TEXT NOT NULL,"
                + "content TEXT NOT NULL,"
                + "ts INTEGER NOT NULL"
                + ");";

        String sqlGroupFiles = "CREATE TABLE IF NOT EXISTS group_files ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "group_id TEXT NOT NULL,"
                + "from_user TEXT NOT NULL,"
                + "filename TEXT NOT NULL,"
                + "file_path TEXT NOT NULL,"
                + "ts INTEGER NOT NULL"
                + ");";

        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            s.execute(sqlMessages);
            s.execute(sqlCalls);
            s.execute(sqlUser);
            s.execute(sqlGroups);
            s.execute(sqlGroupMembers);
            s.execute(sqlGroupMessages);
            s.execute(sqlGroupFiles);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private String hashPassword(String password) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(password.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public boolean registerUser(String username, String password) {
        String sql = "INSERT INTO users(username, password_hash) VALUES (?, ?)";
        try (Connection c = DriverManager.getConnection(url); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, username);
            p.setString(2, hashPassword(password));
            p.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Username đã tồn tại hoặc lỗi DB: " + e.getMessage());
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean loginUser(String username, String password) {
        String sql = "SELECT password_hash FROM users WHERE username=?";
        try (Connection c = DriverManager.getConnection(url); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, username);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString(1);
                    return storedHash.equals(hashPassword(password));
                } else {
                    return false; // user không tồn tại
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public void insertMessage(Message m) {
        String sql = "INSERT INTO messages (from_user,to_user,content,is_file,file_path,ts) VALUES (?,?,?,?,?,?)";
        try (Connection c = DriverManager.getConnection(url); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, m.getFromUser());
            p.setString(2, m.getToUser());
            p.setString(3, m.getContent());
            p.setInt(4, m.isFile() ? 1 : 0);
            p.setString(5, m.getFilePath());
            p.setLong(6, System.currentTimeMillis());
            p.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<String> getAllUsers() {
        List<String> users = new ArrayList<>();
        String sql = "SELECT username FROM users";
        try (Connection c = DriverManager.getConnection(url);
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                users.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return users;
    }

    public void insertCallRecord(CallRecord record) {
        String sql = "INSERT INTO call_history (from_user, to_user, is_video, timestamp, duration_seconds, accepted) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = DriverManager.getConnection(url); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, record.getFromUser());
            p.setString(2, record.getToUser());
            p.setInt(3, record.isVideo() ? 1 : 0);
            p.setLong(4, record.getTimestamp()); // startTs
            p.setLong(5, record.getDuration()); // duration in seconds
            p.setInt(6, record.isSuccess() ? 1 : 0); // success -> accepted
            p.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<CallRecord> loadCallHistory(String me, String peer, int limit) {
        List<CallRecord> out = new ArrayList<>();
        String sql = "SELECT from_user, to_user, is_video, timestamp, duration_seconds, accepted "
                + "FROM call_history "
                + "WHERE (from_user=? AND to_user=?) OR (from_user=? AND to_user=?) "
                + "ORDER BY timestamp ASC LIMIT ?";
        try (Connection c = DriverManager.getConnection(url); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, me);
            p.setString(2, peer);
            p.setString(3, peer);
            p.setString(4, me);
            p.setInt(5, limit);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    out.add(new CallRecord(
                            rs.getString("from_user"),
                            rs.getString("to_user"),
                            rs.getLong("timestamp"), // startTs
                            rs.getLong("duration_seconds"), // duration
                            rs.getInt("accepted") == 1, // success
                            rs.getInt("is_video") == 1 // video?
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return out;
    }

    public List<Message> loadConversationAsc(String me, String peer, int limit) {
        List<Message> out = new ArrayList<>();
        String sql = "SELECT from_user,to_user,content,is_file,file_path,ts FROM messages "
                + "WHERE (from_user=? AND to_user=?) OR (from_user=? AND to_user=?) "
                + "ORDER BY ts ASC LIMIT ?";
        try (Connection c = DriverManager.getConnection(url); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, me);
            p.setString(2, peer);
            p.setString(3, peer);
            p.setString(4, me);
            p.setInt(5, limit);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    String from = rs.getString(1);
                    String to = rs.getString(2);
                    String content = rs.getString(3);
                    boolean isFile = rs.getInt(4) == 1;
                    String fp = rs.getString(5);
                    long timestamp = rs.getLong(6);
                    Message msg = new Message(from, to, content, isFile, fp);
                    msg.setTimestamp(timestamp); // Set timestamp cho Message
                    out.add(msg);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return out;
    }

    // ==== Group chat helpers ====

    public void insertGroup(String groupId, String name, String owner) {
        String sql = "INSERT OR REPLACE INTO groups (id, name, owner) VALUES (?,?,?)";
        try (Connection c = DriverManager.getConnection(url); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, groupId);
            p.setString(2, name);
            p.setString(3, owner);
            p.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void insertGroupMembers(String groupId, List<String> members) {
        String sql = "INSERT OR REPLACE INTO group_members (group_id, member_name) VALUES (?,?)";
        try (Connection c = DriverManager.getConnection(url);
                PreparedStatement p = c.prepareStatement(sql)) {
            for (String m : members) {
                p.setString(1, groupId);
                p.setString(2, m);
                p.addBatch();
            }
            p.executeBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteGroup(String groupId) {
        String sql = "DELETE FROM groups WHERE id=?";
        try (Connection c = DriverManager.getConnection(url); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, groupId);
            p.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<String> getGroupMembers(String groupId) {
        List<String> members = new ArrayList<>();
        String sql = "SELECT member_name FROM group_members WHERE group_id=?";
        try (Connection c = DriverManager.getConnection(url); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, groupId);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    members.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return members;
    }

    public String getGroupOwner(String groupId) {
        String sql = "SELECT owner FROM groups WHERE id = ?";
        try (Connection c = DriverManager.getConnection(url);
                PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, groupId);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("owner");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // Xóa 1 thành viên khỏi group
    public void removeGroupMember(String groupId, String member) {
        String sql = "DELETE FROM group_members WHERE group_id=? AND member_name=?";
        try (Connection c = DriverManager.getConnection(url);
                PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, groupId);
            p.setString(2, member);
            p.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Xóa group nếu không còn thành viên nào
    public void deleteGroupIfEmpty(String groupId) {
        String sql = "SELECT COUNT(*) FROM group_members WHERE group_id=?";
        try (Connection c = DriverManager.getConnection(url);
                PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, groupId);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    deleteGroup(groupId);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Đổi tên group
    public void renameGroup(String groupId, String newName) {
        String sql = "UPDATE groups SET name=? WHERE id=?";
        try (Connection c = DriverManager.getConnection(url);
                PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, newName);
            p.setString(2, groupId);
            p.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ==== Group message history ====

    public void insertGroupMessage(String groupId, String fromUser, String content) {
        String sql = "INSERT INTO group_messages (group_id, from_user, content, ts) VALUES (?,?,?,?)";
        try (Connection c = DriverManager.getConnection(url); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, groupId);
            p.setString(2, fromUser);
            p.setString(3, content);
            p.setLong(4, System.currentTimeMillis());
            p.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Message> loadGroupMessagesAsc(String groupId, int limit) {
        List<Message> out = new ArrayList<>();
        String sql = "SELECT from_user, content, ts FROM group_messages WHERE group_id=? ORDER BY ts ASC LIMIT ?";
        try (Connection c = DriverManager.getConnection(url); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, groupId);
            p.setInt(2, limit);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    String from = rs.getString(1);
                    String content = rs.getString(2);
                    long ts = rs.getLong(3);
                    // toUser = null, isFile=false, filePath=null cho group message
                    Message m = new Message(from, null, content, false, null);
                    m.setTimestamp(ts);
                    out.add(m);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return out;
    }

    // ==== Group file / image / voice history ====

    public void insertGroupFile(String groupId, String fromUser, String filename, String filePath) {
        String sql = "INSERT INTO group_files (group_id, from_user, filename, file_path, ts) VALUES (?,?,?,?,?)";
        try (Connection c = DriverManager.getConnection(url); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, groupId);
            p.setString(2, fromUser);
            p.setString(3, filename);
            p.setString(4, filePath);
            p.setLong(5, System.currentTimeMillis());
            p.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Message> loadGroupFilesAsc(String groupId, int limit) {
        List<Message> out = new ArrayList<>();
        String sql = "SELECT from_user, filename, file_path, ts FROM group_files WHERE group_id=? ORDER BY ts ASC LIMIT ?";
        try (Connection c = DriverManager.getConnection(url); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, groupId);
            p.setInt(2, limit);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    String from = rs.getString(1);
                    String filename = rs.getString(2);
                    String path = rs.getString(3);
                    long ts = rs.getLong(4);
                    Message m = new Message(from, null, filename, true, path);
                    m.setTimestamp(ts);
                    out.add(m);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return out;
    }

    /**
     * Trả về danh sách tất cả group (id,name,owner) mà user này là thành viên.
     */
    public List<GroupInfo> loadGroupsForUser(String username) {
        List<GroupInfo> groups = new ArrayList<>();
        String sql = "SELECT g.id, g.name, g.owner FROM groups g " +
                "JOIN group_members gm ON g.id = gm.group_id " +
                "WHERE gm.member_name = ? ORDER BY g.name ASC";
        try (Connection c = DriverManager.getConnection(url); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, username);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("id");
                    String name = rs.getString("name");
                    String owner = rs.getString("owner");
                    groups.add(new GroupInfo(id, name, owner));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return groups;
    }
}
