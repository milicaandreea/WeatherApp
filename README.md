# 🌦️ Weather Info System (Java + PostgreSQL)

This is a Java-based client-server application that provides real-time weather information for various locations. The system supports two roles:  
- **Admin**, who can upload weather data from a JSON file into a PostgreSQL database.  
- **User**, who can query current weather and forecasts for a specific location.

## 📁 Project Structure

- `Server.java` – Launches the server and connects to the PostgreSQL database.  
- `Client.java` – Handles user interaction and client-side logic.  
- `ClientThread.java` – Handles individual client connections and processes requests.  
- `weather.json` – Sample weather data used for populating the database.

## 🧠 Features

### ✅ Admin Role

- Uploads weather data from a JSON file (e.g., `weather.json`)  
- Server parses and inserts this data into the database if not already present (using `ON CONFLICT DO NOTHING`)  
- Admin can type `exit` to close the session

### ✅ User Role

- Enters a username to register/log in  
- Sets their current location  
- Can perform the following actions:  
  1. Get current weather and forecast for the selected location  
  2. Change the current location  
  3. Exit the application

## 🔌 Database Connection

The application connects to a PostgreSQL server with the following default configuration:

URL: jdbc:postgresql://localhost:5432/weather_db  
User: postgres  
Password: 1q2w3e

Make sure the database is created and has the following schema:

CREATE TABLE users (  
    username VARCHAR PRIMARY KEY,  
    role VARCHAR,  
    current_location VARCHAR  
);

CREATE TABLE weather_data (  
    location VARCHAR PRIMARY KEY,  
    latitude DOUBLE PRECISION,  
    longitude DOUBLE PRECISION,  
    current_weather VARCHAR,  
    current_temperature DOUBLE PRECISION,  
    forecast JSON  
);

## ▶️ How to Run the Application

- start the server
- start the client

## 💡 Example Interaction

### Admin:

Select role (admin/user):  
admin  
Enter the path to the JSON file to upload:  
weather.json  
Message: JSON data uploaded successfully.

### User:

Select role (admin/user):  
user  
Enter your username:  
jane_doe  
Enter your location:  
London  
1. Get weather for current location  
2. Change current location  
3. Exit

## 📦 Dependencies

- Java 8 or higher  
- PostgreSQL JDBC Driver  
- Gson – for JSON parsing  
- org.json – used during admin uploads

## 🔧 Additional Notes

- The server supports multiple simultaneous client connections using multithreading (ClientThread).  
- Communication is handled using JSON messages over sockets.  
- The code is modular and adheres to good object-oriented design principles.
