// src/main/java/com/skybooking/rest/utils/JsonHelper.java

package com.skybooking.rest.utils;

import com.google.gson.*;
import FlightReservation.*;

/**
 * 📄 Utilitaires pour conversion JSON
 */
public class JsonHelper {
    
    private static final Gson GSON = new Gson();
    
    /**
     * Parser une chaîne JSON
     */
    public static JsonObject parseJson(String body) {
        try {
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (JsonSyntaxException e) {
            System.err.println("⌫ JSON invalide: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Convertir Flight en JSON
     */
    public static JsonObject flightToJson(Flight flight) {
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
        json.addProperty("availableSeats", flight.availableSeats);
        json.addProperty("aircraftType", flight.aircraftType);
        return json;
    }
    
    /**
     * Convertir Seat en JSON
     */
    public static JsonObject seatToJson(Seat seat) {
        JsonObject json = new JsonObject();
        json.addProperty("seatNumber", seat.seatNumber);
        json.addProperty("seatClass", seat.seatClass);
        json.addProperty("status", seat.status);
        json.addProperty("price", seat.price);
        return json;
    }
    
    /**
     * Convertir Reservation en JSON
     */
    public static JsonObject reservationToJson(Reservation reservation) {
        JsonObject json = new JsonObject();
        json.addProperty("reservationId", reservation.reservationId);
        json.addProperty("customerId", reservation.customerId);
        json.addProperty("flightId", reservation.flightId);
        json.addProperty("status", reservation.status);
        json.addProperty("totalPrice", reservation.totalPrice);
        json.addProperty("reservationDate", reservation.reservationDate);
        return json;
    }
    
    /**
     * Convertir Payment en JSON
     */
    public static JsonObject paymentToJson(Payment payment) {
        JsonObject json = new JsonObject();
        json.addProperty("paymentId", payment.paymentId);
        json.addProperty("reservationId", payment.reservationId);
        json.addProperty("customerId", payment.customerId);
        json.addProperty("amount", payment.amount);
        json.addProperty("method", payment.method.toString());
        json.addProperty("status", payment.status.toString());
        json.addProperty("transactionId", payment.transactionId);
        json.addProperty("cardNumber", payment.cardNumber);
        json.addProperty("paymentDate", payment.paymentDate);
        json.addProperty("bankReference", payment.bankReference);
        return json;
    }
    
    /**
     * Convertir Invoice en JSON
     */
    public static JsonObject invoiceToJson(Invoice invoice) {
        JsonObject json = new JsonObject();
        json.addProperty("invoiceId", invoice.invoiceId);
        json.addProperty("paymentId", invoice.paymentId);
        json.addProperty("reservationId", invoice.reservationId);
        json.addProperty("customerName", invoice.customerName);
        json.addProperty("email", invoice.email);
        json.addProperty("amount", invoice.amount);
        json.addProperty("taxAmount", invoice.taxAmount);
        json.addProperty("totalAmount", invoice.totalAmount);
        json.addProperty("issueDate", invoice.issueDate);
        json.addProperty("dueDate", invoice.dueDate);
        return json;
    }
    
    /**
     * ✅ Convertir Customer en JSON 
     */
    public static JsonObject customerToJson(Customer customer) {
        JsonObject json = new JsonObject();
        json.addProperty("customerId", customer.customerId);
        json.addProperty("username", customer.username);
        json.addProperty("firstName", customer.firstName);
        json.addProperty("lastName", customer.lastName);
        json.addProperty("email", customer.email);
        json.addProperty("phoneNumber", customer.phoneNumber);
        return json;
    }
}
