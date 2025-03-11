package org.example;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import org.json.JSONArray;
import org.json.JSONObject;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;

public class ClientThread extends Thread {
    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(ClientThread.class.getName());
    private final Socket client;
    private final Connection connection;
    private final BufferedReader in;
    private final PrintWriter out;
    private final Gson gson = new Gson(); // GSON instance
    private String username;

    public ClientThread(Socket client, Connection connection) {
        this.client = client;
        this.connection = connection;
        try {
            this.out = new PrintWriter(client.getOutputStream(), true); // Auto-flush enabled
            this.in = new BufferedReader(new InputStreamReader(client.getInputStream()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void registerUser(String username, String role) throws SQLException {
        String query = "INSERT INTO users (username, role) VALUES (?, ?) ON CONFLICT (username) DO NOTHING";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, username);
            stmt.setString(2, role);
            stmt.executeUpdate();
        }
    }

    public void updateLocationInDatabase(String username, String currentLocation) throws SQLException {
        String query = "UPDATE users SET current_location = ? WHERE username = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, currentLocation);
            stmt.setString(2, username);
            stmt.executeUpdate();
        }
    }
    private void loadWeatherDataFromJson(String filePath) throws IOException, SQLException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IOException("File not found: " + path.toAbsolutePath());
        }
        System.out.println("Loading JSON from: " + path.toAbsolutePath());
        String jsonContent = new String(Files.readAllBytes(path));
        JSONArray jsonArray = new JSONArray(jsonContent);

        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            String location = jsonObject.getString("location");
            double latitude = jsonObject.getDouble("latitude");
            double longitude = jsonObject.getDouble("longitude");
            String weather = jsonObject.getString("current_weather");
            double temperature = jsonObject.getDouble("current_temperature");
            JSONArray forecastArray = jsonObject.getJSONArray("forecast");

            // Convert JSONArray to a raw JSON string
            String forecastJson = forecastArray.toString();

            // Save the data into the database
            String query = "INSERT INTO weather_data (location, latitude, longitude, current_weather, current_temperature, forecast) VALUES (?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT (location) DO NOTHING";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, location);
                stmt.setDouble(2, latitude);
                stmt.setDouble(3, longitude);
                stmt.setString(4, weather);
                stmt.setDouble(5, temperature);
                stmt.setObject(6, forecastJson, java.sql.Types.OTHER); // Insert JSON as type "OTHER" for PostgreSQL
                stmt.executeUpdate();
            }
        }
    }

    @Override
    public void run() {
        boolean isLocationSet = false;
        String currentLocation = null;
        System.out.println("Client connected: " + client.getInetAddress());
        try {
            String jsonRequest = in.readLine();
            JsonObject request = gson.fromJson(jsonRequest, JsonObject.class);

            if (request.has("role")) {
                String role = request.get("role").getAsString();

                if ("admin".equals(role)) {
                    boolean fileUploaded = false;

                    while (!fileUploaded) {
                        if (request.has("filePath")) {
                            String filePath = request.get("filePath").getAsString();
                            try {
                                loadWeatherDataFromJson(filePath); // Loading data from filePath
                                sendResponse(createMessage("JSON data uploaded successfully."));
                                System.out.println("Admin task completed. Closing connection."); // Log for the server
                                fileUploaded = true; // Exit loop
                                client.close(); // Close connection
                                return; // Exit run() for admin
                            } catch (IOException | SQLException e) {
                                sendError("Failed to load JSON data: " + e.getMessage());
                                sendResponse(createMessage("Please provide a valid file path or type 'exit' to quit."));
                            }
                        } else {
                            sendError("Missing 'filePath' for admin. Please provide a valid file path.");
                        }

                        // Wait for new request from client
                        try {
                            jsonRequest = in.readLine();
                            request = gson.fromJson(jsonRequest, JsonObject.class);
                            if (request.has("filePath") && "exit".equalsIgnoreCase(request.get("filePath").getAsString())) {
                                sendResponse(createMessage("Exiting..."));
                                client.close(); // Close connection
                                return; // Exit run()
                            }
                        } catch (IOException e) {
                            LOGGER.severe("Error while reading input: " + e.getMessage());
                            break;
                        }
                    }
                }

                else if ("user".equals(role)) {
                    if (request.has("username")) {
                        this.username = request.get("username").getAsString();
                        registerUser(this.username, "user");
                    } else {
                        sendError("Missing 'username' for user.");
                    }
                } else {
                    sendError("Invalid role.");
                }
            } else {
                sendError("Missing 'role' field in request.");
            }
        } catch (Exception e) {
            LOGGER.severe("Exception occurred: " + e.getMessage());
            try {
                sendError("An error occurred: " + e.getMessage());
            } catch (IOException ioException) {
                LOGGER.severe("Error sending error response: " + ioException.getMessage());
            }
        }
        while (true) {
            try {
                // If location is not yet set
                if (!isLocationSet) {
                    sendResponse(createMenu("Please set your current location:", false));
                    String jsonRequest = in.readLine();
                    JsonObject request = gson.fromJson(jsonRequest, JsonObject.class);
                    if (request.has("currentLocation")) {
                        currentLocation = request.get("currentLocation").getAsString();
                        updateLocationInDatabase(username, currentLocation);
                        isLocationSet = true;
                    } else {
                        sendError("Missing 'currentLocation' field in request");
                    }
                } else {
                    sendResponse(createMenu("Options:", true));
                    String jsonRequest = in.readLine();
                    JsonObject request = gson.fromJson(jsonRequest, JsonObject.class);
                    if (request.has("type")) {
                        String type = request.get("type").getAsString();
                        switch (type) {
                            case "getWeather":
                                handleGetWeather(currentLocation);
                                break;
                            case "updateLocation":
                                if (request.has("currentLocation")) {
                                    currentLocation = request.get("currentLocation").getAsString();
                                    updateLocationInDatabase(username, currentLocation);
                                } else {
                                    sendError("Missing 'currentLocation' field in request");
                                }
                                break;
                            case "disconnect":
                                System.out.println("Client disconnected: " + client.getInetAddress());
                                return; // Exit the run() to terminate the thread
                            default:
                                sendError("Invalid 'type' field in request");
                                break;
                        }
                    } else {
                        sendError("Missing 'type' field in request");
                    }
                }
            } catch (IOException | SQLException e) {
                LOGGER.severe("Exception occurred: " + e.getMessage());
                try {
                    sendError("An error occurred: " + e.getMessage());
                } catch (IOException ioException) {
                    LOGGER.severe("Error sending error response: " + ioException.getMessage());
                }
            }
        }
    }

    private void handleGetWeather(String currentLocation) throws SQLException, IOException {
        String query = "SELECT current_weather, current_temperature, forecast FROM weather_data WHERE location = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, currentLocation);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String weather = rs.getString("current_weather");
                    double temperature = rs.getDouble("current_temperature");
                    String forecastJson = rs.getString("forecast");

                    // Build JSON response
                    JsonObject response = new JsonObject();
                    response.addProperty("location", currentLocation);
                    response.addProperty("current_weather", weather);
                    response.addProperty("current_temperature", temperature);

                    // Parse and add forecast
                    if (forecastJson != null) {
                        JsonArray forecastArray = new Gson().fromJson(forecastJson, JsonArray.class);
                        response.add("forecast", forecastArray);
                    } else {
                        response.addProperty("forecast", "No forecast available.");
                    }

                    sendResponse(response);
                } else {
                    sendError("Weather data not available for location: " + currentLocation);
                }
            }
        }
    }
    private JsonObject createMenu(String header, boolean isOptionsMenu) {
        JsonObject menu = new JsonObject();
        menu.addProperty("header", header);
        if (isOptionsMenu) {
            menu.addProperty("option1", "1. Get weather for current location");
            menu.addProperty("option2", "2. Change current location");
        }
        return menu;
    }

    private JsonObject createMessage(String message) {
        JsonObject response = new JsonObject();
        response.addProperty("message", message);
        return response;
    }

    private void sendResponse(JsonObject response) throws IOException {
        String jsonResponse = gson.toJson(response);
        out.println(jsonResponse); // Send response as JSON
        System.out.println("Sent response to client: " + jsonResponse);
    }

    private void sendError(String errorMessage) throws IOException {
        JsonObject errorResponse = new JsonObject();
        errorResponse.addProperty("error", errorMessage);
        sendResponse(errorResponse);
    }
}
