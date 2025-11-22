// src/main/java/com/skybooking/rest/handlers/admin/FlightManagementHandler.java

package com.skybooking.rest.handlers.admin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.skybooking.rest.middleware.CorsMiddleware;
import com.skybooking.rest.middleware.TimeoutExecutor;
import com.skybooking.rest.utils.JsonHelper;
import com.skybooking.rest.utils.RequestHelper;
import com.skybooking.rest.utils.ResponseHelper;
import static com.skybooking.utils.Constants.*;
import FlightReservation.*;
import java.io.IOException;
import java.util.Map;

/**
 * ✈️ Handler pour la gestion admin des vols
 */
public class FlightManagementHandler implements HttpHandler {
    
    private final AdminManager adminManager;
    private final TimeoutExecutor timeoutExecutor;
    
    public FlightManagementHandler(AdminManager adminManager,
                                  TimeoutExecutor timeoutExecutor) {
        this.adminManager = adminManager;
        this.timeoutExecutor = timeoutExecutor;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        CorsMiddleware.setCorsHeaders(exchange);
        
        if (CorsMiddleware.handlePreFlight(exchange)) return;
        
        String method = exchange.getRequestMethod();
        
        try {
            switch (method) {
                case "GET":
                    handleGetFlights(exchange);
                    break;
                case "POST":
                    handleCreateFlight(exchange);
                    break;
                case "PUT":
                    handleUpdateFlight(exchange);
                    break;
                case "DELETE":
                    handleDeleteFlight(exchange);
                    break;
                default:
                    ResponseHelper.sendError(exchange, 405, "Méthode non autorisée");
            }
        } catch (Exception e) {
            System.err.println("❌ Erreur flight management: " + e.getMessage());
            ResponseHelper.sendError(exchange, 500, e.getMessage());
        }
    }
    
    private void handleGetFlights(HttpExchange exchange) throws Exception {
        System.out.println("→ GET ALL FLIGHTS (Admin)");
        
        FlightManagementData[] flights = timeoutExecutor.executeWithTimeout(() -> {
            return adminManager.getAllFlights();
        }, 15, "get all flights");
        
        JsonArray jsonArray = new JsonArray();
        for (FlightManagementData flight : flights) {
            jsonArray.add(flightManagementToJson(flight));
        }
        
        System.out.println("✅ " + flights.length + " vol(s) retourné(s)");
        ResponseHelper.sendJsonResponse(exchange, 200, jsonArray);
    }
    
    private void handleCreateFlight(HttpExchange exchange) throws Exception {
        System.out.println("→ CREATE FLIGHT (Admin)");
        
        String body = RequestHelper.readRequestBody(exchange);
        JsonObject json = JsonHelper.parseJson(body);
        
        if (json == null) {
            ResponseHelper.sendError(exchange, 400, "Format JSON invalide");
            return;
        }
        
        FlightManagementData flightData = jsonToFlightManagement(json);
        
        String flightId = timeoutExecutor.executeWithTimeout(() -> {
            return adminManager.createFlight(flightData);
        }, 15, "create flight");
        
        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        response.addProperty("flightId", flightId);
        
        System.out.println("✅ Vol créé: " + flightId);
        ResponseHelper.sendJsonResponse(exchange, 201, response);
    }
    
    private void handleUpdateFlight(HttpExchange exchange) throws Exception {
        System.out.println("→ UPDATE FLIGHT (Admin)");
        
        String body = RequestHelper.readRequestBody(exchange);
        JsonObject json = JsonHelper.parseJson(body);
        
        if (json == null) {
            ResponseHelper.sendError(exchange, 400, "Format JSON invalide");
            return;
        }
        
        FlightManagementData flightData = jsonToFlightManagement(json);
        
        boolean success = timeoutExecutor.executeWithTimeout(() -> {
            return adminManager.updateFlight(flightData);
        }, 15, "update flight");
        
        JsonObject response = new JsonObject();
        response.addProperty("success", success);
        
        System.out.println("✅ Vol mis à jour");
        ResponseHelper.sendJsonResponse(exchange, 200, response);
    }
    
    private void handleDeleteFlight(HttpExchange exchange) throws Exception {
        System.out.println("→ DELETE FLIGHT (Admin)");
        
        Map<String, String> params = RequestHelper.parseQueryParams(exchange.getRequestURI());
        String flightId = params.get("flightId");
        
        if (flightId == null || flightId.isEmpty()) {
            ResponseHelper.sendError(exchange, 400, "flightId manquant");
            return;
        }
        
        boolean success = timeoutExecutor.executeWithTimeout(() -> {
            return adminManager.deleteFlight(flightId);
        }, 15, "delete flight");
        
        JsonObject response = new JsonObject();
        response.addProperty("success", success);
        
        System.out.println("✅ Vol supprimé: " + flightId);
        ResponseHelper.sendJsonResponse(exchange, 200, response);
    }
    
    private JsonObject flightManagementToJson(FlightManagementData flight) {
        JsonObject json = new JsonObject();
        json.addProperty("flightId", flight.flightId);
        json.addProperty("flightNumber", flight.flightNumber);
        json.addProperty("airline", flight.airline);
        json.addProperty("departureCity", flight.departureCity);
        json.addProperty("arrivalCity", flight.arrivalCity);
        json.addProperty("departureDate", flight.departureDate);
        json.addProperty("departureTime", flight.departureTime);
        json.addProperty("arrivalDate", flight.arrivalDate);
        json.addProperty("arrivalTime", flight.arrivalTime);
        json.addProperty("duration", flight.duration);
        json.addProperty("economyPrice", flight.economyPrice);
        json.addProperty("businessPrice", flight.businessPrice);
        json.addProperty("firstClassPrice", flight.firstClassPrice);
        json.addProperty("totalSeats", flight.totalSeats);
        json.addProperty("availableSeats", flight.availableSeats);
        json.addProperty("aircraftType", flight.aircraftType);
        json.addProperty("status", flight.status);
        return json;
    }
    
    private FlightManagementData jsonToFlightManagement(JsonObject json) {
        return new FlightManagementData(
            json.has("flightId") ? json.get("flightId").getAsString() : "",
            json.get("flightNumber").getAsString(),
            json.get("airline").getAsString(),
            json.get("departureCity").getAsString(),
            json.get("arrivalCity").getAsString(),
            json.get("departureDate").getAsString(),
            json.get("departureTime").getAsString(),
            json.get("arrivalDate").getAsString(),
            json.get("arrivalTime").getAsString(),
            json.get("duration").getAsString(),
            json.get("economyPrice").getAsDouble(),
            json.get("businessPrice").getAsDouble(),
            json.get("firstClassPrice").getAsDouble(),
            (int) json.get("totalSeats").getAsLong(),
            (int) (json.has("availableSeats") ? json.get("availableSeats").getAsLong() : json.get("totalSeats").getAsLong()),
            json.get("aircraftType").getAsString(),
            json.has("status") ? json.get("status").getAsString() : "ACTIVE"
        );
    }
}
