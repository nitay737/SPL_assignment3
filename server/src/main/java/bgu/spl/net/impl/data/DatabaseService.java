package bgu.spl.net.impl.data;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class DatabaseService {

    private static final String DB_HOST = "127.0.0.1";
    private static final int DB_PORT = 7778;

    private String execute(String sqlCommand) {
        String messageToSend = sqlCommand + "\u0000"; 
        
        try (Socket socket = new Socket(DB_HOST, DB_PORT);
             OutputStream out = socket.getOutputStream();
             BufferedInputStream in = new BufferedInputStream(socket.getInputStream())) {

            out.write(messageToSend.getBytes(StandardCharsets.UTF_8));
            out.flush();

            String response = "";
            int readByte;

            while ((readByte = in.read()) != -1) {
                if (readByte == '\0') {
                    break;
                }
                response += (char) readByte;
            }

            return response;

        } catch (IOException e) {
            System.err.println("Database connection failed: " + e.getMessage());
            return "ERROR";
        }
    }

    public boolean registerUser(String username, String password) {
        String sql = String.format("INSERT INTO Users (username, password) VALUES ('%s', '%s')", username, password);
        String res = execute(sql);
        return res.equals("done");
    }

    public void addLogin(String username) {
        String sql = String.format("INSERT INTO Logins (username) VALUES ('%s')", username);
        execute(sql);
    }

    public void addLogout(String username) {
        String sql = String.format(
            "UPDATE Logins SET logout_time = CURRENT_TIMESTAMP WHERE username = '%s' AND logout_time IS NULL", 
            username);
        execute(sql);
    }

    public void addFileReport(String username, String filename) {
        String sql = String.format("INSERT INTO Reports (username, filename) VALUES ('%s', '%s')", username, filename);
        execute(sql);
    }

    public void printReport() {
        System.out.println("Database Report:");
        String usersResponse = execute("SELECT username FROM Users");
        String[] users = parseUsersList(usersResponse);
        if (users.length == 0) {
            System.out.println("No users registered yet.");
            return;
        }
        for (String user : users) {
            System.out.println("\nUser: " + user);
            String loginQuery = "SELECT login_time, logout_time FROM Logins WHERE username = '" + user + "'";
            String loginHistory = execute(loginQuery);
            loginHistory = loginHistory.replace("[", "").replace("]", "").replace("), (", "\n  ").replace("(", "").replace(")", "").replace("'", "");
            
            System.out.println("Login History:\n " + (loginHistory.isEmpty() ? "No history" : loginHistory));
            String fileQuery = "SELECT filename, upload_time FROM Reports WHERE username = '" + user + "'";
            String fileHistory = execute(fileQuery);
            fileHistory = fileHistory.replace("[", "").replace("]", "").replace("), (", "\n  ").replace("(", "").replace(")", "").replace("'", "");

            System.out.println("Uploaded Files:\n  " + (fileHistory.isEmpty() ? "No files uploaded" : fileHistory));
        }
    }

    private String[] parseUsersList(String pythonListString) {
        if (pythonListString == null || pythonListString.equals("[]") || pythonListString.startsWith("ERROR")) {
            return new String[0];
        }
        String clean = pythonListString.replace("[", "").replace("]", "");
        clean = clean.replace("(", "").replace(")", "").replace("'", "");
        String[] rawUsers = clean.split(",");
        List<String> usersList = new ArrayList<>();
        for (String u : rawUsers) {
            String trimmed = u.trim();
            if (!trimmed.isEmpty()) {
                usersList.add(trimmed);
            }
        }
        return usersList.toArray(new String[0]);
    }

    public String getPassword(String username) {
        String sql = "SELECT password FROM Users WHERE username = '" + username + "'";
        String response = execute(sql);
        if (response == null || response.equals("[]") || response.contains("ERROR")) {
            return null;
        }
        String cleanPass = response.replace("[", "")
                                .replace("]", "")
                                .replace("(", "")
                                .replace(")", "")
                                .replace("'", "")
                                .replace(",", "")
                                .trim();
        
        return cleanPass;
    }
}
