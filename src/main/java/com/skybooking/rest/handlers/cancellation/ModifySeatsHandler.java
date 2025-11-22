// src/main/java/com/skybooking/rest/handlers/cancellation/ModifySeatsHandler.java

package com.skybooking.rest.handlers.cancellation;

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
import java.util.ArrayList;
import java.util.List;

/**
 * 💺 Handler pour modifier les sièges d'une réservation
 */
public class ModifySeatsHandler implements HttpHandler {
    
    private final CancellationManager cancellationManager;
    private final TimeoutExecutor timeoutExecutor;
    
    public ModifySeatsHandler(CancellationManager cancellationManager,
                             TimeoutExecutor timeoutExecutor) {
        this.cancellationManager = cancellationManager;
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
            System.out.println("→ Requête MODIFY SEATS reçue");
            
            String body = RequestHelper.readRequestBody(exchange);
            JsonObject json = JsonHelper.parseJson(body);
            
            if (json == null) {
                ResponseHelper.sendError(exchange, 400, "Format JSON invalide");
                return;
            }
            
            // Validation des champs requis
            if (!json.has("reservationId") || !json.has("newSeats")) {
                ResponseHelper.sendError(exchange, 400, 
                    "Paramètres manquants: reservationId, newSeats");
                return;
            }
            
            String reservationId = json.get("reservationId").getAsString();
            JsonArray newSeatsArray = json.getAsJsonArray("newSeats");
            
            // Convertir JsonArray en String[]
            List<String> seatsList = new ArrayList<>();
            for (int i = 0; i < newSeatsArray.size(); i++) {
                seatsList.add(newSeatsArray.get(i).getAsString());
            }
            String[] newSeats = seatsList.toArray(new String[0]);
            
            System.out.println("  Réservation: " + reservationId);
            System.out.println("  Nouveaux sièges: " + String.join(", ", newSeats));
            
            // Vérifier si la modification est autorisée
            boolean canModify = timeoutExecutor.executeWithTimeout(() -> {
                return cancellationManager.canModifyReservation(reservationId);
            }, TIMEOUT_DEFAULT, "vérification modification");
            
            if (!canModify) {
                ResponseHelper.sendError(exchange, 403, 
                    "Modification non autorisée (moins de 24h avant le départ)");
                return;
            }
            
            // Appel CORBA avec timeout
            boolean modified = timeoutExecutor.executeWithTimeout(() -> {
                return cancellationManager.modifySeats(reservationId, newSeats);
            }, TIMEOUT_MODIFICATION, "modification sièges");
            
            if (modified) {
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("message", "Sièges modifiés avec succès");
                response.addProperty("reservationId", reservationId);
                
                JsonArray seatsArray = new JsonArray();
                for (String seat : newSeats) {
                    seatsArray.add(seat);
                }
                response.add("newSeats", seatsArray);
                
                System.out.println("✅ Sièges modifiés: " + reservationId);
                ResponseHelper.sendJsonResponse(exchange, 200, response);
            } else {
                ResponseHelper.sendError(exchange, 400, "Échec de la modification");
            }
            
        } catch (ModificationNotAllowedException e) {
            System.err.println("✗ Modification non autorisée: " + e.message);
            ResponseHelper.sendError(exchange, 403, e.message);
        } catch (SeatNotAvailableException e) {
            System.err.println("✗ Siège non disponible: " + e.message);
            ResponseHelper.sendError(exchange, 409, e.message);
        } catch (Exception e) {
            System.err.println("✗ Erreur serveur: " + e.getMessage());
            ResponseHelper.sendError(exchange, 500, e.getMessage());
        }
    }
}
