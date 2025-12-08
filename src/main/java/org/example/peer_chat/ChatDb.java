package org.example.peer_chat;


import java.sql.*;
import java.util.ArrayList;
import java.util.List;


public class ChatDb {
    private final String url;

    public ChatDb(String dbPath) {
        this.url = "jdbc:sqlite:" + dbPath;
        init();
    }

    private void init() {
        String sql = "CREATE TABLE IF NOT EXISTS messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "from_user TEXT," +
                "to_user TEXT," +
                "content TEXT," +
                "is_file INTEGER," +
                "file_path TEXT," +
                "ts INTEGER" +
                ");";
        try (Connection c = DriverManager.getConnection(url);
             Statement s = c.createStatement()) {
            s.execute(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }



    public void insertMessage(Message m) {
        String sql = "INSERT INTO messages (from_user,to_user,content,is_file,file_path,ts) VALUES (?,?,?,?,?,?)";
        try (Connection c = DriverManager.getConnection(url);
             PreparedStatement p = c.prepareStatement(sql)) {
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

    public List<Message> loadConversationAsc(String me, String peer, int limit) {
        List<Message> out = new ArrayList<>();
        String sql = "SELECT from_user,to_user,content,is_file,file_path FROM messages " +
                "WHERE (from_user=? AND to_user=?) OR (from_user=? AND to_user=?) " +
                "ORDER BY ts ASC LIMIT ?";
        try (Connection c = DriverManager.getConnection(url);
             PreparedStatement p = c.prepareStatement(sql)) {
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
                    out.add(new Message(from, to, content, isFile, fp));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return out;
    }
}
