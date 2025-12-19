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

        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            s.execute(sqlMessages);
            s.execute(sqlCalls);
            s.execute(sqlUser);
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
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
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
            p.setLong(4, record.getTimestamp());    // startTs
            p.setLong(5, record.getDuration());     // duration in seconds
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
}
