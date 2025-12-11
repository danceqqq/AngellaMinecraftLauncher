package com.launcher.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.launcher.api.model.PlayerInfo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HttpApiServer {
    private final int port;
    private final MinecraftServer server;
    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private volatile boolean running = false;
    private final Gson gson = new Gson();

    public HttpApiServer(int port, MinecraftServer server) {
        this.port = port;
        this.server = server;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        executorService = Executors.newCachedThreadPool();
        running = true;
        
        executorService.submit(() -> {
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    executorService.submit(() -> handleRequest(clientSocket));
                } catch (IOException e) {
                    if (running) {
                        LauncherApiMod.LOGGER.error("Ошибка принятия соединения:", e);
                    }
                }
            }
        });
    }

    public void stop() throws IOException {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    private void handleRequest(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true)) {
            
            // Читаем HTTP запрос
            String requestLine = in.readLine();
            if (requestLine == null) {
                return;
            }
            
            String[] requestParts = requestLine.split(" ");
            if (requestParts.length < 2) {
                sendErrorResponse(out, 400, "Bad Request");
                return;
            }
            
            String method = requestParts[0];
            String path = requestParts[1];
            
            // Читаем остальные заголовки
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                // Игнорируем заголовки
            }
            
            // Обрабатываем запрос
            if (method.equals("GET")) {
                if (path.equals("/api/players") || path.equals("/api/players/")) {
                    handleGetPlayers(out);
                } else if (path.startsWith("/api/player/")) {
                    String playerName = path.substring("/api/player/".length());
                    handleGetPlayer(out, playerName);
                } else if (path.equals("/api/status") || path.equals("/api/status/")) {
                    handleGetStatus(out);
                } else {
                    sendErrorResponse(out, 404, "Not Found");
                }
            } else {
                sendErrorResponse(out, 405, "Method Not Allowed");
            }
            
        } catch (IOException e) {
            LauncherApiMod.LOGGER.error("Ошибка обработки запроса:", e);
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                // Игнорируем
            }
        }
    }

    private void handleGetPlayers(PrintWriter out) {
        try {
            List<PlayerInfo> players = new ArrayList<>();
            
            if (server != null && server.getPlayerManager() != null) {
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    PlayerInfo info = PlayerInfo.fromPlayer(player);
                    players.add(info);
                }
            }
            
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("online", players.size());
            response.addProperty("max", server != null ? server.getPlayerManager().getMaxPlayerCount() : 0);
            response.add("players", gson.toJsonTree(players));
            
            sendJsonResponse(out, 200, response.toString());
        } catch (Exception e) {
            LauncherApiMod.LOGGER.error("Ошибка получения списка игроков:", e);
            sendErrorResponse(out, 500, "Internal Server Error");
        }
    }

    private void handleGetPlayer(PrintWriter out, String playerName) {
        try {
            if (server != null && server.getPlayerManager() != null) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerName);
                if (player != null) {
                    PlayerInfo info = PlayerInfo.fromPlayer(player);
                    JsonObject response = new JsonObject();
                    response.addProperty("success", true);
                    response.add("player", gson.toJsonTree(info));
                    sendJsonResponse(out, 200, response.toString());
                    return;
                }
            }
            
            sendErrorResponse(out, 404, "Player not found");
        } catch (Exception e) {
            LauncherApiMod.LOGGER.error("Ошибка получения информации об игроке:", e);
            sendErrorResponse(out, 500, "Internal Server Error");
        }
    }

    private void handleGetStatus(PrintWriter out) {
        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        response.addProperty("online", server != null && server.getPlayerManager() != null 
            ? server.getPlayerManager().getCurrentPlayerCount() : 0);
        response.addProperty("max", server != null ? server.getPlayerManager().getMaxPlayerCount() : 0);
        response.addProperty("version", server != null ? server.getVersion() : "unknown");
        
        sendJsonResponse(out, 200, response.toString());
    }

    private void sendJsonResponse(PrintWriter out, int statusCode, String json) {
        try {
            byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
            out.print("HTTP/1.1 " + statusCode + " OK\r\n");
            out.print("Content-Type: application/json; charset=UTF-8\r\n");
            out.print("Access-Control-Allow-Origin: *\r\n");
            out.print("Access-Control-Allow-Methods: GET, OPTIONS\r\n");
            out.print("Access-Control-Allow-Headers: Content-Type\r\n");
            out.print("Content-Length: " + jsonBytes.length + "\r\n");
            out.print("Connection: close\r\n");
            out.print("\r\n");
            out.print(json);
            out.flush();
        } catch (Exception e) {
            LauncherApiMod.LOGGER.error("Error sending JSON response:", e);
        }
    }

    private void sendErrorResponse(PrintWriter out, int statusCode, String message) {
        try {
            JsonObject error = new JsonObject();
            error.addProperty("success", false);
            error.addProperty("error", message);
            String json = error.toString();
            byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
            out.print("HTTP/1.1 " + statusCode + " OK\r\n");
            out.print("Content-Type: application/json; charset=UTF-8\r\n");
            out.print("Access-Control-Allow-Origin: *\r\n");
            out.print("Content-Length: " + jsonBytes.length + "\r\n");
            out.print("Connection: close\r\n");
            out.print("\r\n");
            out.print(json);
            out.flush();
        } catch (Exception e) {
            LauncherApiMod.LOGGER.error("Error sending error response:", e);
        }
    }
}

