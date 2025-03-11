package org.example;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

public class Client {
    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(Client.class.getName());
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private final Gson gson = new Gson(); // GSON instance

    public void start() {
        try {
            this.socket = new Socket("localhost", 6543);
            this.out = new PrintWriter(socket.getOutputStream(), true);  // Auto-flush enabled
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            System.out.println("Connected to the server.");

            // Start reading and writing threads
            new ReadThread().start();
            new WriteThread().start();

        } catch (IOException e) {
            LOGGER.severe("IO Exception occurred: " + e.getMessage());
        }
    }

    // Thread for reading messages from the server
    private class ReadThread extends Thread {
        @Override
        public void run() {
            boolean running = true;
            while (running) {
                try {
                    // Read response from the server (as a line)
                    String response = in.readLine();  // Read line as text
                    if (response == null) {
                        System.out.println("Connection closed.");
                        return;
                    }

                    JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);

                    // Display information based on the JSON structure
                    if (jsonResponse.has("header")) {
                        System.out.println("\n=== " + jsonResponse.get("header").getAsString() + " ===");
                    }
                    if (jsonResponse.has("message")) {
                        System.out.println("\nMessage: " + jsonResponse.get("message").getAsString());
                    }
                    if (jsonResponse.has("error")) {
                        System.out.println("\nError: " + jsonResponse.get("error").getAsString());
                    }
                    if (jsonResponse.has("location")) {
                        System.out.println("\nLocation: " + jsonResponse.get("location").getAsString());
                    }
                    if (jsonResponse.has("current_weather")) {
                        System.out.println("Current Weather: " + jsonResponse.get("current_weather").getAsString());
                    }
                    if (jsonResponse.has("current_temperature")) {
                        System.out.println("Current temperature: " + jsonResponse.get("current_temperature").getAsDouble() + "°C");
                    }
                    if (jsonResponse.has("forecast")) {
                        // Ensure the 'forecast' field is an array
                        JsonElement forecastElement = jsonResponse.get("forecast");
                        if (forecastElement.isJsonArray()) {
                            JsonArray forecastArray = forecastElement.getAsJsonArray();

                            // Display the forecast
                            System.out.println("Forecast:");
                            for (JsonElement forecastItem : forecastArray) {
                                JsonObject forecastObject = forecastItem.getAsJsonObject();
                                String weather = forecastObject.get("weather").getAsString();
                                double temperature = forecastObject.get("temperature").getAsDouble();
                                System.out.println("- " + weather + ", " + temperature + "°C");
                            }
                        } else {
                            System.out.println("No forecast available or the format is incorrect.");
                        }
                    }
                    if (jsonResponse.has("option1") && jsonResponse.has("option2")) {
                        System.out.println(jsonResponse.get("option1").getAsString());
                        System.out.println(jsonResponse.get("option2").getAsString());
                        System.out.println("3. Exit");
                    }
                } catch (IOException e) {
                    running = false;
                }
            }
        }
    }

    private class WriteThread extends Thread {
        @Override
        public void run() {
            Scanner scanner = new Scanner(System.in);
            try {
                System.out.println("Select role (admin/user): ");
                String role = scanner.nextLine().trim().toLowerCase();

                if ("admin".equals(role)) {
                    handleAdminRole(scanner); // Separate method for admin
                } else if ("user".equals(role)) {
                    handleUserRole(scanner); // Separate method for user
                } else {
                    System.out.println("Invalid role selected. Exiting.");
                }
            } catch (IOException e) {
                System.out.println("Connection closed or error occurred: " + e.getMessage());
            }
        }

    }
    private void handleAdminRole(Scanner scanner) throws IOException {
        boolean running = true;
        while (running) {
            System.out.println("Enter the path to the JSON file to upload (or type 'exit' to quit): ");
            String filePath = scanner.nextLine().trim();

            // Build the request for the server
            JsonObject request = new JsonObject();
            request.addProperty("role", "admin");
            request.addProperty("filePath", filePath);

            // Send the request to the server
            out.println(gson.toJson(request));
            out.flush();

            // Wait for the server response
            String response = in.readLine();
            if (response == null) {
                break; // Exit the loop
            }
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);

            if (jsonResponse.has("message")) {
                System.out.println("\nMessage: " + jsonResponse.get("message").getAsString());
                if ("JSON data uploaded successfully.".equals(jsonResponse.get("message").getAsString()) ||
                        "Exiting...".equals(jsonResponse.get("message").getAsString())) {
                    running = false; // Exit the loop if the file was uploaded or if the user chose 'exit'
                }
            }
            if (jsonResponse.has("error")) {
                System.out.println("\nError: " + jsonResponse.get("error").getAsString());
            }
        }
    }

    private void handleUserRole(Scanner scanner) throws IOException {
        System.out.println("Enter your username: ");
        String username = scanner.nextLine().trim();

        // Build the request for the server
        JsonObject request = new JsonObject();
        request.addProperty("role", "user");
        request.addProperty("username", username);

        // Send the request to the server
        out.println(gson.toJson(request));
        out.flush();

        System.out.println("Welcome, " + username + "!");

        handleUserFlow(scanner, username);
    }

    private void handleUserFlow(Scanner scanner, String username) {
        boolean isLocationSet = false; // Flag for current location

        while (true) {
            if (!isLocationSet) {

                String currentLocation = scanner.nextLine();

                JsonObject request = new JsonObject();
                request.addProperty("username", username);
                request.addProperty("currentLocation", currentLocation);

                String jsonRequest = gson.toJson(request);
                out.println(jsonRequest); // Send the request
                out.flush();

                isLocationSet = true; // Mark the location as set
                System.out.println("Location set successfully.");
                continue; // Restart the loop to display the menu
            }

            // Read the option
            String input = scanner.nextLine();
            if (!input.matches("\\d+")) { // Check if the input is numeric
                System.out.println("Invalid input. Please enter a valid number.");
                continue;
            }

            int option = Integer.parseInt(input);
            JsonObject request = new JsonObject();

            if (option == 1) {
                // Build the request for current weather
                request.addProperty("type", "getWeather");
            } else if (option == 2) {
                // Build the request to update the location
                System.out.print("Enter your new current location: ");
                String newLocation = scanner.nextLine();
                request.addProperty("type", "updateLocation");
                request.addProperty("currentLocation", newLocation);
                System.out.println("Location updated successfully.");
            } else if (option == 3) {
                request.addProperty("type", "disconnect");
                out.println(gson.toJson(request)); // Send the disconnection request
                out.flush();
                break; //  Exit method run() and stop the thread
            } else {
                System.out.println("Invalid option. Please try again.");
                continue;
            }

            // Send the request to the server
            String jsonRequest = gson.toJson(request);
            out.println(jsonRequest); // Send the request
            out.flush();
        }

        // Close connection after exiting the loop
        try {
            socket.close();
            System.out.println("Disconnected from server.");
        } catch (IOException e) {
            System.out.println("Error while closing the connection: " + e.getMessage());
        }
    }

    public static void main( String[] args )
    {
        new Client().start();
    }
}
