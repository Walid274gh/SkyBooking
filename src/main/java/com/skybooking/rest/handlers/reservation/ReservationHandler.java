// src/main/java/com/skybooking/rest/handlers/reservation/ReservationHandler.java

package com.skybooking.rest.handlers.reservation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.skybooking.rest.middleware.CorsMiddleware;
import com.skybooking.rest.middleware.TimeoutExecutor;
import com.skybooking.rest.utils.JsonHelper;
import com.skybooking.rest.utils.RequestHelper;
import com.skybooking.rest.utils.ResponseHelper;
import com.skybooking.utils.Constants;
import FlightReservation.*;
import java.io.IOException;

/**
 * 📋 Handler pour créer une réservation
 */
public class ReservationHandler implements HttpHandler {
    
    private final ReservationManager reservationManager;
    private final TimeoutExecutor timeoutExecutor;
    
    public ReservationHandler(ReservationManager reservationManager,
                             TimeoutExecutor timeoutExecutor) {
        this.reservationManager = reservationManager;
        this.timeoutExecutor = timeoutExecutor;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        CorsMiddleware.setCorsHeaders(exchange);
        
        if (CorsMiddleware.handlePreFlight(exchange)) return;
        
        if (!CorsMiddleware.isMethodAllowed(exchange, "POST")) {
            ResponseHelper.sendError(exchange, 405, "Méthode non autorisée");
            return;
        }
        
        try {
            System.out.println("→ Requête RESERVATION reçue");
            
            String body = RequestHelper.readRequestBody(exchange);
            
            if (body == null || body.trim().isEmpty()) {
                ResponseHelper.sendError(exchange, 400, "Corps de requête vide");
                return;
            }
            
            JsonObject json = JsonHelper.parseJson(body);
            if (json == null) {
                ResponseHelper.sendError(exchange, 400, "Format JSON invalide");
                return;
            }
            
            // Validation des champs requis
            if (!json.has("customerId") || !json.has("flightId") || 
                !json.has("seats") || !json.has("passengers")) {
                ResponseHelper.sendError(exchange, 400, "Paramètres manquants");
                return;
            }
            
            String customerId = json.get("customerId").getAsString();
            String flightId = json.get("flightId").getAsString();
            
            System.out.println("  Client: " + customerId);
            System.out.println("  Vol: " + flightId);
            
            // Extraire les sièges
            JsonArray seatsArray = json.getAsJsonArray("seats");
            String[] seatNumbers = new String[seatsArray.size()];
            for (int i = 0; i < seatsArray.size(); i++) {
                seatNumbers[i] = seatsArray.get(i).getAsJsonObject()
                    .get("seatNumber").getAsString();
            }
            
            System.out.println("  Sièges: " + String.join(", ", seatNumbers));
            
            // Extraire les passagers
            JsonArray passengersArray = json.getAsJsonArray("passengers");
            Passenger[] passengers = new Passenger[passengersArray.size()];
            for (int i = 0; i < passengersArray.size(); i++) {
                JsonObject p = passengersArray.get(i).getAsJsonObject();
                
                // Validation des champs passagers
                if (!p.has("firstName") || !p.has("lastName") || 
                    !p.has("passportNumber") || !p.has("dateOfBirth") ||
                    !p.has("email") || !p.has("phone")) {
                    ResponseHelper.sendError(exchange, 400, 
                        "Informations incomplètes pour le passager " + (i + 1));
                    return;
                }
                
                passengers[i] = new Passenger(
                    p.get("firstName").getAsString(),
                    p.get("lastName").getAsString(),
                    p.get("passportNumber").getAsString(),
                    p.get("dateOfBirth").getAsString(),
                    p.get("email").getAsString(),
                    p.get("phone").getAsString()
                );
                
                System.out.println("  Passager " + (i+1) + ": " + 
                    p.get("firstName").getAsString() + " " + 
                    p.get("lastName").getAsString());
            }
            
            // Appel CORBA avec timeout (20 secondes pour réservation)
            Reservation reservation = timeoutExecutor.executeWithTimeout(() -> {
                return reservationManager.createReservation(
                    customerId, flightId, seatNumbers, passengers
                );
            }, Constants.TIMEOUT_RESERVATION, "création de réservation");
            
            // Réponse
            JsonObject response = JsonHelper.reservationToJson(reservation);
            System.out.println("✅ Réservation créée: " + reservation.reservationId);
            ResponseHelper.sendJsonResponse(exchange, 201, response);
            
        } catch (SeatNotAvailableException e) {
            System.err.println("❌ Siège non disponible: " + e.message);
            ResponseHelper.sendError(exchange, 409, e.message);
        } catch (ReservationException e) {
            System.err.println("❌ Erreur réservation: " + e.message);
            ResponseHelper.sendError(exchange, 400, e.message);
        } catch (Exception e) {
            System.err.println("❌ Erreur serveur: " + e.getMessage());
            ResponseHelper.sendError(exchange, 500, e.getMessage());
        }
    }
}
