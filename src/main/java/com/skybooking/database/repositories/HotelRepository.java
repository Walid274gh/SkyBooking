// src/main/java/com/skybooking/database/repositories/HotelRepository.java

package com.skybooking.database.repositories;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;
import java.util.*;

/**
 * 🏨 Repository pour la gestion des hôtels
 */
public class HotelRepository extends BaseRepository {
    
    public HotelRepository() {
        super("hotels");
    }
    
    /**
     * Trouver un hôtel par ID
     */
    public Document findById(String hotelId) {
        return collection.find(Filters.eq("hotelId", hotelId)).first();
    }
    
    /**
     * 🆕 CORRECTION: Méthode publique pour insérer un hôtel
     * Remplace l'appel direct à insert() protégé
     */
    public void insertHotel(Document hotel) {
        if (!hotel.containsKey("createdAt")) {
            hotel.append("createdAt", new Date());
        }
        if (!hotel.containsKey("updatedAt")) {
            hotel.append("updatedAt", new Date());
        }
        insert(hotel); // Appel protected autorisé depuis la sous-classe
    }
    
    /**
     * Rechercher des hôtels avec filtres
     */
    public List<Document> searchHotels(String city, int numberOfRooms, int minStarRating) {
        List<Bson> filters = new ArrayList<>();
        
        if (city != null && !city.isEmpty()) {
            filters.add(Filters.regex("city", city, "i"));
        }
        
        filters.add(Filters.gte("availableRooms", numberOfRooms));
        
        if (minStarRating > 0) {
            filters.add(Filters.gte("starRating", minStarRating));
        }
        
        Bson combinedFilter = filters.isEmpty() 
            ? new Document() 
            : Filters.and(filters);
        
        return collection.find(combinedFilter)
                        .into(new ArrayList<>());
    }
    
    /**
     * Décrémenter les chambres disponibles de manière atomique
     */
    public boolean decrementAvailableRooms(String hotelId, int quantity) {
        if (quantity <= 0) return false;
        
        Bson filter = Filters.and(
            Filters.eq("hotelId", hotelId),
            Filters.gte("availableRooms", quantity)
        );
        
        Bson update = Updates.combine(
            Updates.inc("availableRooms", -quantity),
            Updates.set("updatedAt", new Date())
        );
        
        return collection.updateOne(filter, update).getModifiedCount() > 0;
    }
    
    /**
     * Incrémenter les chambres disponibles
     */
    public boolean incrementAvailableRooms(String hotelId, int quantity) {
        if (quantity <= 0) return false;
        
        Bson update = Updates.combine(
            Updates.inc("availableRooms", quantity),
            Updates.set("updatedAt", new Date())
        );
        
        return collection.updateOne(
            Filters.eq("hotelId", hotelId), 
            update
        ).getModifiedCount() > 0;
    }
    
    /**
     * Obtenir tous les hôtels
     */
    public List<Document> getAllHotels() {
        return collection.find().into(new ArrayList<>());
    }
    
    // ==================== MÉTHODES ADMIN ====================
    
    /**
     * Mettre à jour un hôtel
     */
    public boolean update(String hotelId, Document updates) {
        return collection.updateOne(
            Filters.eq("hotelId", hotelId),
            new Document("$set", updates)
        ).getModifiedCount() > 0;
    }
    
    /**
     * Supprimer un hôtel
     */
    public boolean delete(String hotelId) {
        return collection.deleteOne(
            Filters.eq("hotelId", hotelId)
        ).getDeletedCount() > 0;
    }
    
    /**
     * Compter les hôtels par statut
     */
    public long countByStatus(String status) {
        return collection.countDocuments(
            Filters.eq("status", status)
        );
    }
    
    /**
     * 🎲 Initialiser des données factices d'hôtels
     */
    public void initializeHotels() {
        System.out.println("🏨 Génération des hôtels factices...");
        
        List<Document> hotels = new ArrayList<>();
        
        // Hôtels algériens
        String[] algerianCities = {
            "Alger", "Oran", "Constantine", "Annaba", "Tlemcen",
            "Béjaïa", "Sétif", "Batna", "Ghardaïa", "Tamanrasset"
        };
        
        // Hôtels internationaux
        String[][] internationalCities = {
            {"Paris", "France"}, {"Marseille", "France"}, {"Lyon", "France"},
            {"Londres", "UK"}, {"Madrid", "Espagne"}, {"Rome", "Italie"},
            {"Dubaï", "UAE"}, {"Istanbul", "Turquie"}, {"Le Caire", "Egypte"},
            {"New York", "USA"}, {"Tokyo", "Japon"}
        };
        
        int hotelCounter = 1;
        
        // Générer hôtels algériens (2-3 par ville)
        for (String city : algerianCities) {
            int hotelsInCity = 2 + new Random().nextInt(2);
            
            for (int i = 0; i < hotelsInCity; i++) {
                hotels.add(createHotel(
                    "HTL" + String.format("%05d", hotelCounter++),
                    generateHotelName(city, i),
                    city,
                    3 + new Random().nextInt(3), // 3-5 étoiles
                    8000 + new Random().nextInt(12000) // 8000-20000 DZD
                ));
            }
        }
        
        // Générer hôtels internationaux (1-2 par ville)
        for (String[] cityCountry : internationalCities) {
            String city = cityCountry[0];
            int hotelsInCity = 1 + new Random().nextInt(2);
            
            for (int i = 0; i < hotelsInCity; i++) {
                hotels.add(createHotel(
                    "HTL" + String.format("%05d", hotelCounter++),
                    generateHotelName(city, i),
                    city,
                    3 + new Random().nextInt(3), // 3-5 étoiles
                    15000 + new Random().nextInt(25000) // 15000-40000 DZD
                ));
            }
        }
        
        // Insertion en batch
        if (!hotels.isEmpty()) {
            collection.insertMany(hotels);
            System.out.println("✅ " + hotels.size() + " hôtels insérés dans MongoDB");
        }
    }
    
    /**
     * Créer un document hôtel
     */
    private Document createHotel(
            String hotelId,
            String hotelName,
            String city,
            int starRating,
            double pricePerNight) {
        
        Random random = new Random(hotelId.hashCode());
        
        // Générer commodités aléatoires
        List<String> allAmenities = Arrays.asList(
            "WiFi", "Parking", "Piscine", "Restaurant", "Spa", 
            "Salle de sport", "Room service", "Bar", "Climatisation"
        );
        
        List<String> selectedAmenities = new ArrayList<>();
        int amenitiesCount = 4 + random.nextInt(4); // 4-7 commodités
        
        for (int i = 0; i < amenitiesCount && i < allAmenities.size(); i++) {
            selectedAmenities.add(allAmenities.get(i));
        }
        
        return new Document()
            .append("hotelId", hotelId)
            .append("hotelName", hotelName)
            .append("city", city)
            .append("address", generateAddress(city))
            .append("starRating", starRating)
            .append("description", generateDescription(hotelName, starRating))
            .append("pricePerNight", pricePerNight)
            .append("totalRooms", 10 + random.nextInt(41)) // 10-50 chambres
            .append("availableRooms", 10 + random.nextInt(41))
            .append("imageUrl", generateImageUrl(starRating))
            .append("amenities", String.join(",", selectedAmenities))
            .append("reviewScore", 7.0 + random.nextDouble() * 3.0) // 7.0-10.0
            .append("reviewCount", 50 + random.nextInt(451)) // 50-500 avis
            .append("status", "ACTIVE")
            .append("createdAt", new Date());
    }
    
    /**
     * Générer un nom d'hôtel
     */
    private String generateHotelName(String city, int index) {
        String[] prefixes = {
            "Grand Hôtel", "Le Royal", "Plaza", "Ibis", "Novotel",
            "Sofitel", "Sheraton", "Hilton", "Marriott", "Radisson"
        };
        
        String prefix = prefixes[index % prefixes.length];
        return prefix + " " + city;
    }
    
    /**
     * Générer une adresse
     */
    private String generateAddress(String city) {
        String[] streets = {
            "Rue Didouche Mourad", "Avenue de l'Indépendance", 
            "Boulevard Mohamed V", "Rue Larbi Ben M'hidi",
            "Avenue du 1er Novembre", "Rue des Frères Bouadou"
        };
        
        Random random = new Random(city.hashCode());
        int number = 1 + random.nextInt(200);
        String street = streets[random.nextInt(streets.length)];
        
        return number + " " + street + ", " + city;
    }
    
    /**
     * Générer une description
     */
    private String generateDescription(String hotelName, int stars) {
        String luxury = stars >= 4 ? "de luxe " : "";
        return hotelName + " est un établissement " + luxury + 
               "situé au cœur de la ville, offrant confort et services de qualité. " +
               "Idéal pour les voyageurs d'affaires et de loisirs.";
    }
    
    /**
     * Générer une URL d'image (placeholder)
     */
    private String generateImageUrl(int stars) {
        return "https://placeholder.com/hotel-" + stars + "-stars.jpg";
    }
}
