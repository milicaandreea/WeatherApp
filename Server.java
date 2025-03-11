package org.example;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.DriverManager;

public class Server {
    private Connection connection;
    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(Server.class.getName());

    public void start() {
        final int PORT = 6543;
        connectToDatabase();
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new ClientThread(clientSocket, connection).start();
            }
        } catch (IOException e) {
            LOGGER.severe("IOException occurred: " + e.getMessage());
        }
    }

    private void connectToDatabase() {
        try {
            String url = "jdbc:postgresql://localhost:5432/weather_db";
            String user = "postgres";
            String password = "1q2w3e";
            connection = DriverManager.getConnection(url, user, password);
            System.out.println("Connected to the database successfully.");
        } catch (SQLException e) {
            LOGGER.severe("IOException occurred: " + e.getMessage());
            throw new RuntimeException("Failed to connect to the database.");
        }
    }

    public static void main( String[] args )
    {
        new Server().start();
    }
}