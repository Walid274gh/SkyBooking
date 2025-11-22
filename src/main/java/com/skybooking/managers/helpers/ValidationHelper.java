// src/main/java/com/skybooking/managers/helpers/ValidationHelper.java

package com.skybooking.managers.helpers;

import FlightReservation.*;
import com.skybooking.utils.ValidationUtils;

/**
 * ✅ Helper pour validations dans les managers
 */
public class ValidationHelper {
    
    /**
     * Valider les données d'un passager
     */
    public static void validatePassenger(Passenger passenger, int index) 
            throws ReservationException {
        
        if (passenger.firstName == null || passenger.firstName.trim().isEmpty() ||
            passenger.lastName == null || passenger.lastName.trim().isEmpty() ||
            passenger.passportNumber == null || passenger.passportNumber.trim().isEmpty() ||
            passenger.dateOfBirth == null || passenger.dateOfBirth.trim().isEmpty() ||
            passenger.email == null || passenger.email.trim().isEmpty() ||
            passenger.phone == null || passenger.phone.trim().isEmpty()) {
            
            throw new ReservationException(
                "Informations incomplètes pour le passager " + index
            );
        }
        
        if (!ValidationUtils.isValidEmail(passenger.email)) {
            throw new ReservationException(
                "Email invalide pour le passager " + index
            );
        }
    }
    
    /**
     * Valider un montant de paiement
     */
    public static void validatePaymentAmount(double amount) throws PaymentException {
        if (!ValidationUtils.isValidAmount(amount)) {
            throw new PaymentException("Montant invalide: " + amount);
        }
    }
    
    /**
     * Valider une carte bancaire
     */
    public static void validateCard(String cardNumber, String expiryDate, String cvv) 
            throws InvalidCardException {
        
        if (!ValidationUtils.isValidCardNumber(cardNumber)) {
            throw new InvalidCardException("Numéro de carte invalide");
        }
        
        if (!ValidationUtils.isValidCVV(cvv)) {
            throw new InvalidCardException("CVV invalide");
        }
        
        if (!ValidationUtils.isValidExpiryDate(expiryDate)) {
            throw new InvalidCardException("Date d'expiration invalide ou carte expirée");
        }
    }
}