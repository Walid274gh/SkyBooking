// src/main/java/com/skybooking/managers/impl/HotelManagerImpl.java

package com.skybooking.managers.impl;

import FlightReservation.*;
import com.skybooking.database.repositories.HotelRepository;
import com.skybooking.database.repositories.HotelReservationRepository;
import com.skybooking.managers.helpers.ManagerHelper;
import com.skybooking.utils.DateUtils;
import org.bson.Document;
import java.util.*;

/**
 * 🏨 GESTIONNAIRE D'HÔTELS - DYNAMIC PACKAGING
 * Implémente la réduction automatique de 15% si lié à une réservation de vol
 */
public class HotelManagerImpl extends HotelManagerPOA {
    
    private final HotelRepository hotelRepository;
    private final HotelReservationRepository hotelReservationRepository;
    
    // ✅ CORRECTION: Changement du type Interface vers Implementation
    private final ReservationManagerImpl reservationManager;
    
    // Configuration de la réduction Dynamic Packaging
    private static final double FLIGHT_DISCOUNT_PERCENTAGE = 15.0;
    
    // ✅ CORRECTION: Changement du paramètre Constructor
    public HotelManagerImpl(ReservationManagerImpl reservationManager) {
        this.hotelRepository = new HotelRepository();
        this.hotelReservationRepository = new HotelReservationRepository();
        this.reservationManager = reservationManager;
        
        // Initialiser les données si la base est vide
        if (hotelRepository.count() == 0) {
            System.out.println("🏨 Initialisation des données hôtels...");
            hotelRepository.initializeHotels();
        } else {
            System.out.println("✅ Base hôtels initialisée: " + hotelRepository.count() + " hôtels");
        }
    }
    
    @Override
    public Hotel[] searchHotels(
            String city,
            String checkInDate,
            String checkOutDate,
            int numberOfRooms,
            int minStarRating) {
        
        System.out.println("🔍 Recherche hôtels: " + city + 
                         " | " + checkInDate + " → " + checkOutDate +
                         " | " + numberOfRooms + " chambre(s) | " + 
                         minStarRating + "⭐+");
        
        // Validation des dates
        if (!DateUtils.isFutureDate(checkInDate)) {
            System.err.println("❌ Date check-in dans le passé");
            return new Hotel[0];
        }
        
        if (!DateUtils.isAfter(checkOutDate, checkInDate)) {
            System.err.println("❌ Date check-out invalide");
            return new Hotel[0];
        }
        
        List<Document> hotelDocs = hotelRepository.searchHotels(
            city, numberOfRooms, minStarRating
        );
        
        Hotel[] hotels = new Hotel[hotelDocs.size()];
        for (int i = 0; i < hotelDocs.size(); i++) {
            hotels[i] = ManagerHelper.documentToHotel(hotelDocs.get(i));
        }
        
        System.out.println("✅ " + hotels.length + " hôtel(s) trouvé(s)");
        return hotels;
    }
    
    @Override
    public Hotel getHotelById(String hotelId) throws HotelNotFoundException {
        Document doc = hotelRepository.findById(hotelId);
        if (doc == null) {
            throw new HotelNotFoundException("Hôtel non trouvé: " + hotelId);
        }
        return ManagerHelper.documentToHotel(doc);
    }
    
    @Override
    public HotelReservation bookHotel(
            String customerId,
            String hotelId,
            String checkInDate,
            String checkOutDate,
            int numberOfRooms,
            String flightReservationId) 
            throws HotelBookingException, NoRoomsAvailableException {
        
        System.out.println("📝 Réservation hôtel: " + hotelId + 
                         " | Client: " + customerId +
                         " | Vol lié: " + flightReservationId);
        
        try {
            // 1. Vérifier que l'hôtel existe
            Document hotelDoc = hotelRepository.findById(hotelId);
            if (hotelDoc == null) {
                throw new HotelBookingException("Hôtel non trouvé: " + hotelId);
            }
            
            // 2. Vérifier disponibilité
            int availableRooms = hotelDoc.getInteger("availableRooms", 0);
            if (availableRooms < numberOfRooms) {
                throw new NoRoomsAvailableException(
                    "Seulement " + availableRooms + " chambre(s) disponible(s)"
                );
            }
            
            // 3. Calculer le nombre de nuits
            int numberOfNights = DateUtils.calculateNights(checkInDate, checkOutDate);
            if (numberOfNights <= 0) {
                throw new HotelBookingException("Durée de séjour invalide");
            }
            
            // 4. Calculer le prix
            double pricePerNight = hotelDoc.getDouble("pricePerNight");
            double originalPrice = pricePerNight * numberOfNights * numberOfRooms;
            
            // 5. 🎯 VÉRIFIER LE DYNAMIC PACKAGING
            double discountPercentage = 0.0;
            boolean hasFlightDiscount = false;
            
            if (flightReservationId != null && !flightReservationId.trim().isEmpty()) {
                hasFlightDiscount = verifyFlightReservation(
                    customerId, 
                    flightReservationId,
                    hotelDoc.getString("city")
                );
                
                if (hasFlightDiscount) {
                    discountPercentage = FLIGHT_DISCOUNT_PERCENTAGE;
                    System.out.println("✨ Dynamic Packaging activé: -" + 
                                     discountPercentage + "%");
                }
            }
            
            double finalPrice = originalPrice * (1 - discountPercentage / 100);
            
            // 6. Générer l'ID de réservation
            String hotelReservationId = "HR" + System.currentTimeMillis() + 
                                       customerId.hashCode();
            
            // 7. Créer la réservation
            Document reservation = new Document()
                .append("hotelReservationId", hotelReservationId)
                .append("customerId", customerId)
                .append("hotelId", hotelId)
                .append("hotelName", hotelDoc.getString("hotelName"))
                .append("checkInDate", checkInDate)
                .append("checkOutDate", checkOutDate)
                .append("numberOfNights", numberOfNights)
                .append("numberOfRooms", numberOfRooms)
                .append("originalPrice", originalPrice)
                .append("discountPercentage", discountPercentage)
                .append("finalPrice", finalPrice)
                .append("status", "CONFIRMED")
                .append("reservationDate", DateUtils.getCurrentDateTime())
                .append("flightReservationId", flightReservationId)
                .append("hasFlightDiscount", hasFlightDiscount);
            
            // 8. ✅ CORRECTION: Utilisation de insertReservation au lieu de insert
            hotelReservationRepository.insertReservation(reservation);
            
            // 9. Décrémenter les chambres disponibles
            boolean updated = hotelRepository.decrementAvailableRooms(hotelId, numberOfRooms);
            if (!updated) {
                // Rollback
                hotelReservationRepository.delete(hotelReservationId);
                throw new HotelBookingException("Échec de la réservation (concurrence)");
            }
            
            System.out.println("✅ Réservation hôtel créée: " + hotelReservationId);
            if (hasFlightDiscount) {
                System.out.println("💰 Économie réalisée: " + 
                    String.format("%.2f DZD (-%.0f%%)", 
                    originalPrice - finalPrice, discountPercentage));
            }
            
            return ManagerHelper.documentToHotelReservation(reservation);
            
        } catch (HotelBookingException | NoRoomsAvailableException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("❌ Erreur réservation hôtel: " + e.getMessage());
            e.printStackTrace();
            throw new HotelBookingException("Erreur lors de la réservation: " + e.getMessage());
        }
    }
    
    /**
     * 🔍 Vérifier qu'une réservation de vol existe et correspond
     * @return true si la réduction doit être appliquée
     */
    private boolean verifyFlightReservation(
            String customerId, 
            String flightReservationId,
            String hotelCity) {
        
        try {
            // Récupérer la réservation de vol
            Reservation flightRes = reservationManager.getReservation(flightReservationId);
            
            // Vérifier que c'est bien le client
            if (!flightRes.customerId.equals(customerId)) {
                System.out.println("⚠️ Réservation de vol ne correspond pas au client");
                return false;
            }
            
            // Vérifier que la réservation est confirmée
            if (!"CONFIRMED".equals(flightRes.status)) {
                System.out.println("⚠️ Réservation de vol non confirmée");
                return false;
            }
            
            System.out.println("✓ Réservation de vol valide pour Dynamic Packaging");
            return true;
            
        } catch (Exception e) {
            System.err.println("⚠️ Impossible de vérifier la réservation de vol: " + 
                             e.getMessage());
            return false;
        }
    }
    
    @Override
    public HotelReservation[] getCustomerHotelReservations(String customerId) {
        List<Document> docs = hotelReservationRepository.findByCustomerId(customerId);
        
        HotelReservation[] reservations = new HotelReservation[docs.size()];
        for (int i = 0; i < docs.size(); i++) {
            reservations[i] = ManagerHelper.documentToHotelReservation(docs.get(i));
        }
        
        System.out.println("✅ " + reservations.length + " réservation(s) hôtel trouvée(s)");
        return reservations;
    }
    
    @Override
    public HotelReservation getHotelReservation(String hotelReservationId) 
            throws HotelBookingException {
        
        Document doc = hotelReservationRepository.findById(hotelReservationId);
        if (doc == null) {
            throw new HotelBookingException(
                "Réservation d'hôtel non trouvée: " + hotelReservationId
            );
        }
        return ManagerHelper.documentToHotelReservation(doc);
    }
    
    @Override
    public boolean cancelHotelReservation(String hotelReservationId) 
            throws HotelBookingException {
        
        try {
            Document reservation = hotelReservationRepository.findById(hotelReservationId);
            if (reservation == null) {
                throw new HotelBookingException("Réservation non trouvée");
            }
            
            // Vérifier que la réservation peut être annulée
            String status = reservation.getString("status");
            if ("CANCELLED".equals(status)) {
                throw new HotelBookingException("Réservation déjà annulée");
            }
            
            // Mettre à jour le statut
            boolean updated = hotelReservationRepository.updateStatus(
                hotelReservationId, "CANCELLED"
            );
            
            if (updated) {
                // Remettre les chambres disponibles
                String hotelId = reservation.getString("hotelId");
                int numberOfRooms = reservation.getInteger("numberOfRooms");
                hotelRepository.incrementAvailableRooms(hotelId, numberOfRooms);
                
                System.out.println("✅ Réservation hôtel annulée: " + hotelReservationId);
                return true;
            }
            
            return false;
            
        } catch (HotelBookingException e) {
            throw e;
        } catch (Exception e) {
            throw new HotelBookingException("Erreur annulation: " + e.getMessage());
        }
    }
    
    @Override
    public boolean checkAvailability(
            String hotelId,
            String checkInDate,
            String checkOutDate,
            int numberOfRooms) {
        
        Document hotel = hotelRepository.findById(hotelId);
        if (hotel == null) return false;
        
        int availableRooms = hotel.getInteger("availableRooms", 0);
        return availableRooms >= numberOfRooms;
    }
}
