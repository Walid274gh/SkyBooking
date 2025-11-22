// src/main/java/com/skybooking/managers/helpers/ManagerHelper.java

package com.skybooking.managers.helpers;

import FlightReservation.*;
import org.bson.Document;

/**
 * 📄 Helper pour conversions entre CORBA et MongoDB
 */
public class ManagerHelper {
    
    /**
     * Convertir Document MongoDB en Customer CORBA
     */
    public static Customer documentToCustomer(Document doc) {
        return new Customer(
            doc.getString("customerId"),
            doc.getString("username"),
            doc.getString("firstName"),
            doc.getString("lastName"),
            doc.getString("email"),
            doc.getString("phoneNumber")
        );
    }
    
    /**
     * Convertir Document MongoDB en Flight CORBA
     */
    public static Flight documentToFlight(Document doc) {
        return new Flight(
            doc.getString("flightId"),
            doc.getString("flightNumber"),
            doc.getString("airline"),
            doc.getString("departureCity"),
            doc.getString("arrivalCity"),
            doc.getString("departureDate"),
            doc.getString("departureTime"),
            doc.getString("arrivalDate"),
            doc.getString("arrivalTime"),
            doc.getString("duration"),
            doc.getDouble("economyPrice"),
            doc.getDouble("businessPrice"),
            doc.getDouble("firstClassPrice"),
            doc.getInteger("availableSeats"),
            doc.getString("aircraftType")
        );
    }
    
    /**
     * Convertir Document MongoDB en Seat CORBA
     */
    public static Seat documentToSeat(Document doc) {
        return new Seat(
            doc.getString("seatNumber"),
            doc.getString("seatClass"),
            doc.getString("status"),
            doc.getDouble("price")
        );
    }
    
    /**
     * Convertir Document MongoDB en Reservation CORBA
     */
    public static Reservation documentToReservation(Document doc) {
        return new Reservation(
            doc.getString("reservationId"),
            doc.getString("customerId"),
            doc.getString("flightId"),
            doc.getString("status"),
            doc.getDouble("totalPrice"),
            doc.getString("reservationDate")
        );
    }
    
    /**
     * Convertir Document MongoDB en Ticket CORBA
     */
    public static Ticket documentToTicket(Document doc) {
        return new Ticket(
            doc.getString("ticketId"),
            doc.getString("reservationId"),
            doc.getString("passengerName"),
            doc.getString("seatNumber"),
            doc.getString("flightNumber"),
            doc.getString("departureCity"),
            doc.getString("arrivalCity"),
            doc.getString("departureDate"),
            doc.getString("departureTime"),
            doc.getDouble("price")
        );
    }
    
    /**
     * Convertir String en PaymentStatus CORBA
     */
    private static PaymentStatus stringToPaymentStatus(String statusStr) {
        if (statusStr == null) {
            return PaymentStatus.PENDING; // Valeur par défaut
        }
        switch (statusStr.toUpperCase()) {
            case "PENDING":
                return PaymentStatus.PENDING;
            case "PROCESSING":
                return PaymentStatus.PROCESSING;
            case "COMPLETED":
                return PaymentStatus.COMPLETED;
            case "FAILED":
                return PaymentStatus.FAILED;
            case "REFUNDED":
                return PaymentStatus.REFUNDED;
            default:
                System.err.println("⚠️ Status inconnu: " + statusStr + ", utilisation de PENDING");
                return PaymentStatus.PENDING;
        }
    }
    
    /**
     * Convertir Document MongoDB en Payment CORBA
     */
    public static Payment documentToPayment(Document doc) {
        return new Payment(
            doc.getString("paymentId"),
            doc.getString("reservationId"),
            doc.getString("customerId"),
            doc.getDouble("amount"),
            "CIB".equals(doc.getString("method")) ? PaymentMethod.CIB : PaymentMethod.EDAHABIA,
            stringToPaymentStatus(doc.getString("status")),
            doc.getString("transactionId"),
            doc.getString("cardNumber"),
            doc.getString("paymentDate"),
            doc.getString("bankReference")
        );
    }
    
    /**
     * Convertir Document MongoDB en Invoice CORBA
     */
    public static Invoice documentToInvoice(Document doc) {
        return new Invoice(
            doc.getString("invoiceId"),
            doc.getString("paymentId"),
            doc.getString("reservationId"),
            doc.getString("customerName"),
            doc.getString("email"),
            doc.getDouble("amount"),
            doc.getDouble("taxAmount"),
            doc.getDouble("totalAmount"),
            doc.getString("issueDate"),
            doc.getString("dueDate")
        );
    }
}