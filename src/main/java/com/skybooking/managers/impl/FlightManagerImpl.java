// src/main/java/com/skybooking/managers/impl/FlightManagerImpl.java

package com.skybooking.managers.impl;

import FlightReservation.*;
import com.skybooking.database.repositories.FlightRepository;
import com.skybooking.database.repositories.SeatRepository;
import com.skybooking.managers.helpers.ManagerHelper;
import com.skybooking.utils.DateUtils;
import org.bson.Document;
import java.util.*;
import java.text.SimpleDateFormat;

/**
 * ✈️ GESTIONNAIRE DE VOLS - OTA MONDIALE
 * Simulation d'une agence de voyages internationale
 * Focus sur le marché algérien avec couverture mondiale
 * 
 * Caractéristiques:
 * - Génération de vols pour 365 jours
 * - Vols internes algériens quotidiens
 * - Vols internationaux depuis multiples hubs algériens
 * - Lazy loading des sièges pour performance optimale
 */
public class FlightManagerImpl extends FlightManagerPOA {
    
    private final FlightRepository flightRepository;
    public final SeatRepository seatRepository;
    
    // Configuration des villes algériennes
    private static final String[] ALGERIAN_CITIES = {
        "Alger", "Oran", "Constantine", "Annaba", "Tlemcen", 
        "Béjaïa", "Sétif", "Batna", "Tébessa", "Ouargla",
        "Hassi Messaoud", "Ghardaïa", "Tamanrasset", "Adrar"
    };
    
    // Configuration des destinations internationales par région
    private static final Map<String, String[]> INTERNATIONAL_DESTINATIONS = new HashMap<String, String[]>() {{
        put("Europe", new String[]{
            "Paris", "Marseille", "Lyon", "Toulouse", "Nice",
            "Londres", "Manchester", "Madrid", "Barcelone", "Rome",
            "Milan", "Berlin", "Francfort", "Munich", "Amsterdam",
            "Bruxelles", "Genève", "Zurich", "Istanbul", "Athènes",
            "Lisbonne", "Vienne", "Prague", "Varsovie"
        });
        put("Moyen-Orient", new String[]{
            "Dubaï", "Doha", "Riyad", "Jeddah", "Le Caire",
            "Beyrouth", "Amman", "Koweït", "Bahreïn", "Muscat"
        });
        put("Afrique", new String[]{
            "Tunis", "Casablanca", "Dakar", "Bamako", "Abidjan",
            "Niamey", "N'Djamena", "Nouakchott", "Tripoli"
        });
        put("Amérique", new String[]{
            "New York", "Montréal", "Toronto", "Los Angeles",
            "Miami", "Washington", "Boston", "Chicago"
        });
        put("Asie", new String[]{
            "Pékin", "Shanghai", "Singapour", "Bangkok",
            "Kuala Lumpur", "Tokyo", "Séoul", "Hong Kong"
        });
    }};
    
    // Configuration des compagnies aériennes
    private static final String[] AIRLINES = {
        "Air Algérie", "Tassili Airlines", "Air France", "Turkish Airlines",
        "Emirates", "Qatar Airways", "Lufthansa", "British Airways",
        "Iberia", "Alitalia", "Royal Air Maroc", "Tunisair"
    };
    
    // Configuration des types d'avions avec capacités
    private static final Map<String, Integer> AIRCRAFT_TYPES = new HashMap<String, Integer>() {{
        put("Airbus A320", 180);
        put("Airbus A330", 250);
        put("Boeing 737", 180);
        put("Boeing 777", 350);
        put("Boeing 787", 300);
        put("Airbus A350", 350);
    }};
    
    public FlightManagerImpl() {
        this.flightRepository = new FlightRepository();
        this.seatRepository = new SeatRepository();
        
        if (flightRepository.count() == 0) {
            initializeFlights();
        } else {
            System.out.println("✅ Base de données initialisée: " + 
                             flightRepository.count() + " vols");
        }
    }
    
    /**
     * Génération intelligente de vols pour 365 jours
     * Architecture optimisée pour performance
     */
    private void initializeFlights() {
        System.out.println("🌍 Initialisation OTA Mondiale - Génération sur 365 jours");
        long startTime = System.currentTimeMillis();
        
        List<Document> allFlights = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        
        // Génération pour chaque jour de l'année
        for (int day = 0; day < 365; day++) {
            calendar.setTime(new Date());
            calendar.add(Calendar.DAY_OF_MONTH, day);
            String currentDate = dateFormat.format(calendar.getTime());
            
            // 1. Vols internes algériens (quotidiens)
            allFlights.addAll(generateDomesticFlights(currentDate, day));
            
            // 2. Vols internationaux depuis hubs algériens
            allFlights.addAll(generateInternationalFlights(currentDate, day));
            
            // Affichage progression tous les 30 jours
            if ((day + 1) % 30 == 0) {
                System.out.println("📅 Génération: " + (day + 1) + "/365 jours - " + 
                                 allFlights.size() + " vols créés");
            for (int i = 0; i < secondaryFlightsCount && i < secondaryDestinations.size(); i++) {
                int destIndex = (dayOffset + i) % secondaryDestinations.size();
                String destination = secondaryDestinations.get(destIndex);
                
                String flightId = "IT" + String.format("%06d", flightCounter++);
                
                int departureHour = random.nextBoolean() ? 
                    (2 + random.nextInt(8)) : (14 + random.nextInt(8));
                
                String departureTime = String.format("%02d:%02d", 
                    departureHour, random.nextInt(4) * 15);
                
                int durationMinutes = calculateInternationalDuration(hub, destination);
                Calendar arrivalCal = Calendar.getInstance();
                arrivalCal.set(Calendar.HOUR_OF_DAY, departureHour);
                arrivalCal.add(Calendar.MINUTE, durationMinutes);
                
                String arrivalDate = date;
                String arrivalTime = String.format("%02d:%02d", 
                    arrivalCal.get(Calendar.HOUR_OF_DAY), 
                    arrivalCal.get(Calendar.MINUTE));
                
                String duration = formatDuration(durationMinutes);
                
                double basePrice = calculateInternationalPrice(hub, destination);
                double economyPrice = basePrice;
                double businessPrice = basePrice * 3.0;
                double firstClassPrice = basePrice * 5.0;
                
                String airline = selectInternationalAirline(destination);
                String aircraftType = selectInternationalAircraft(destination);
                int capacity = AIRCRAFT_TYPES.get(aircraftType);
                
                String flightNumber = generateFlightNumber(airline, random);
                
                flights.add(createFlightDocument(
                    flightId, flightNumber, airline, hub, destination,
                    date, departureTime, arrivalDate, arrivalTime, duration,
                    economyPrice, businessPrice, firstClassPrice,
                    capacity, aircraftType
                ));
                
                // Vol retour
                String returnFlightId = "IT" + String.format("%06d", flightCounter++);
                int returnDepartureHour = (departureHour + 12) % 24;
                String returnDepartureTime = String.format("%02d:%02d", 
                    returnDepartureHour, random.nextInt(4) * 15);
                
                Calendar returnArrivalCal = Calendar.getInstance();
                returnArrivalCal.set(Calendar.HOUR_OF_DAY, returnDepartureHour);
                returnArrivalCal.add(Calendar.MINUTE, durationMinutes);
                
                String returnArrivalDate = date;
                String returnArrivalTime = String.format("%02d:%02d", 
                    returnArrivalCal.get(Calendar.HOUR_OF_DAY), 
                    returnArrivalCal.get(Calendar.MINUTE));
                
                String returnFlightNumber = generateFlightNumber(airline, random);
                
                flights.add(createFlightDocument(
                    returnFlightId, returnFlightNumber, airline, destination, hub,
                    date, returnDepartureTime, returnArrivalDate, returnArrivalTime, duration,
                    economyPrice, businessPrice, firstClassPrice,
                    capacity, aircraftType
                ));
            }
        }
        
        // Insertion en batch pour performance
        System.out.println("💾 Insertion des vols dans MongoDB...");
        flightRepository.insertFlightsBatch(allFlights);
        
        long duration = System.currentTimeMillis() - startTime;
        System.out.println("✅ Génération terminée: " + allFlights.size() + 
                         " vols créés en " + duration + "ms");
        System.out.println("⚡ Performance: " + (allFlights.size() * 1000 / duration) + 
                         " vols/seconde");
    }
    
    /**
     * Génération des vols internes algériens
     * Routes principales entre villes majeures
     */
    private List<Document> generateDomesticFlights(String date, int dayOffset) {
        List<Document> flights = new ArrayList<>();
        Random random = new Random(dayOffset);
        
        // Routes internes prioritaires (quotidiennes)
        String[][] domesticRoutes = {
            {"Alger", "Oran"}, {"Alger", "Constantine"}, {"Alger", "Annaba"},
            {"Alger", "Tamanrasset"}, {"Alger", "Ghardaïa"}, {"Alger", "Hassi Messaoud"},
            {"Oran", "Constantine"}, {"Oran", "Hassi Messaoud"}, {"Constantine", "Annaba"},
            {"Alger", "Béjaïa"}, {"Alger", "Tlemcen"}, {"Oran", "Annaba"}
        };
        
        int flightCounter = dayOffset * 100;
        
        for (String[] route : domesticRoutes) {
            String departure = route[0];
            String arrival = route[1];
            
            // 2-4 vols par jour sur routes principales
            int dailyFlights = departure.equals("Alger") ? 3 + random.nextInt(2) : 2;
            
            for (int f = 0; f < dailyFlights; f++) {
                String flightId = "DZ" + String.format("%06d", flightCounter++);
                
                // Horaires variés
                int departureHour = 6 + (f * 5) + random.nextInt(3);
                String departureTime = String.format("%02d:%02d", 
                    departureHour, random.nextInt(4) * 15);
                
                // Durée basée sur distance
                int durationMinutes = calculateDomesticDuration(departure, arrival);
                Calendar arrivalCal = Calendar.getInstance();
                arrivalCal.set(Calendar.HOUR_OF_DAY, departureHour);
                arrivalCal.add(Calendar.MINUTE, durationMinutes);
                
                String arrivalTime = String.format("%02d:%02d", 
                    arrivalCal.get(Calendar.HOUR_OF_DAY), 
                    arrivalCal.get(Calendar.MINUTE));
                
                String duration = formatDuration(durationMinutes);
                
                // Prix basés sur distance et demande
                double basePrice = calculateDomesticPrice(departure, arrival);
                double economyPrice = basePrice;
                double businessPrice = basePrice * 2.5;
                double firstClassPrice = basePrice * 4.0;
                
                // Compagnies algériennes prioritaires
                String airline = random.nextInt(10) < 7 ? "Air Algérie" : "Tassili Airlines";
                
                // Type d'avion adapté à la route
                String aircraftType = selectDomesticAircraft(departure, arrival);
                int capacity = AIRCRAFT_TYPES.get(aircraftType);
                
                String flightNumber = airline.equals("Air Algérie") ? 
                    "AH" + (1000 + random.nextInt(9000)) : 
                    "SF" + (1000 + random.nextInt(9000));
                
                flights.add(createFlightDocument(
                    flightId, flightNumber, airline, departure, arrival,
                    date, departureTime, date, arrivalTime, duration,
                    economyPrice, businessPrice, firstClassPrice,
                    capacity, aircraftType
                ));
                
                // Vol retour
                String returnFlightId = "DZ" + String.format("%06d", flightCounter++);
                String returnFlightNumber = airline.equals("Air Algérie") ? 
                    "AH" + (1000 + random.nextInt(9000)) : 
                    "SF" + (1000 + random.nextInt(9000));
                
                int returnDepartureHour = departureHour + 4 + random.nextInt(3);
                String returnDepartureTime = String.format("%02d:%02d", 
                    returnDepartureHour, random.nextInt(4) * 15);
                
                Calendar returnArrivalCal = Calendar.getInstance();
                returnArrivalCal.set(Calendar.HOUR_OF_DAY, returnDepartureHour);
                returnArrivalCal.add(Calendar.MINUTE, durationMinutes);
                
                String returnArrivalTime = String.format("%02d:%02d", 
                    returnArrivalCal.get(Calendar.HOUR_OF_DAY), 
                    returnArrivalCal.get(Calendar.MINUTE));
                
                flights.add(createFlightDocument(
                    returnFlightId, returnFlightNumber, airline, arrival, departure,
                    date, returnDepartureTime, date, returnArrivalTime, duration,
                    economyPrice, businessPrice, firstClassPrice,
                    capacity, aircraftType
                ));
            }
        }
        
        return flights;
    }
    
    /**
     * Génération des vols internationaux
     * Depuis multiples hubs algériens vers destinations mondiales
     */
    private List<Document> generateInternationalFlights(String date, int dayOffset) {
        List<Document> flights = new ArrayList<>();
        Random random = new Random(dayOffset + 50000);
        
        // Hubs algériens pour vols internationaux
        String[] algerianHubs = {"Alger", "Oran", "Constantine", "Annaba"};
        
        // ⭐ DESTINATIONS PRINCIPALES (vols quotidiens depuis tous les hubs)
        String[] mainDestinations = {
            "Paris", "Marseille", "Lyon", "Istanbul", "Madrid", 
            "Barcelone", "Rome", "Londres", "Dubaï", "Le Caire"
        };
        
        int flightCounter = dayOffset * 1000 + 50000;
        
        for (String hub : algerianHubs) {
            // 1. VOLS QUOTIDIENS vers destinations principales
            for (String destination : mainDestinations) {
                // Tous les hubs ont ces destinations chaque jour
                boolean shouldGenerate = true;
                
                // Alger: toutes les destinations
                // Oran: toutes sauf certaines destinations lointaines
                if (hub.equals("Constantine") || hub.equals("Annaba")) {
                    // Ces hubs ont moins de vols vers destinations lointaines
                    if (destination.equals("Dubaï") && dayOffset % 2 != 0) {
                        shouldGenerate = false;
                    }
                }
                
                if (!shouldGenerate) continue;
                if (!shouldGenerate) continue;
                
                String flightId = "IT" + String.format("%06d", flightCounter++);
                
                // Horaires internationaux (départs matinaux et nocturnes)
                int departureHour = random.nextBoolean() ? 
                    (2 + random.nextInt(8)) : (14 + random.nextInt(8));
                
                String departureTime = String.format("%02d:%02d", 
                    departureHour, random.nextInt(4) * 15);
                
                // Durée selon destination
                int durationMinutes = calculateInternationalDuration(hub, destination);
                Calendar arrivalCal = Calendar.getInstance();
                arrivalCal.set(Calendar.HOUR_OF_DAY, departureHour);
                arrivalCal.add(Calendar.MINUTE, durationMinutes);
                
                String arrivalDate = date;
                String arrivalTime = String.format("%02d:%02d", 
                    arrivalCal.get(Calendar.HOUR_OF_DAY), 
                    arrivalCal.get(Calendar.MINUTE));
                
                String duration = formatDuration(durationMinutes);
                
                // Prix internationaux
                double basePrice = calculateInternationalPrice(hub, destination);
                double economyPrice = basePrice;
                double businessPrice = basePrice * 3.0;
                double firstClassPrice = basePrice * 5.0;
                
                // Sélection compagnie internationale
                String airline = selectInternationalAirline(destination);
                String aircraftType = selectInternationalAircraft(destination);
                int capacity = AIRCRAFT_TYPES.get(aircraftType);
                
                String flightNumber = generateFlightNumber(airline, random);
                
                flights.add(createFlightDocument(
                    flightId, flightNumber, airline, hub, destination,
                    date, departureTime, arrivalDate, arrivalTime, duration,
                    economyPrice, businessPrice, firstClassPrice,
                    capacity, aircraftType
                ));
                
                // Vol retour (décalé de quelques heures)
                String returnFlightId = "IT" + String.format("%06d", flightCounter++);
                int returnDepartureHour = (departureHour + 12) % 24;
                String returnDepartureTime = String.format("%02d:%02d", 
                    returnDepartureHour, random.nextInt(4) * 15);
                
                Calendar returnArrivalCal = Calendar.getInstance();
                returnArrivalCal.set(Calendar.HOUR_OF_DAY, returnDepartureHour);
                returnArrivalCal.add(Calendar.MINUTE, durationMinutes);
                
                String returnArrivalDate = date;
                String returnArrivalTime = String.format("%02d:%02d", 
                    returnArrivalCal.get(Calendar.HOUR_OF_DAY), 
                    returnArrivalCal.get(Calendar.MINUTE));
                
                String returnFlightNumber = generateFlightNumber(airline, random);
                
                flights.add(createFlightDocument(
                    returnFlightId, returnFlightNumber, airline, destination, hub,
                    date, returnDepartureTime, returnArrivalDate, returnArrivalTime, duration,
                    economyPrice, businessPrice, firstClassPrice,
                    capacity, aircraftType
                ));
            }
            
            // 2. VOLS ROTATIFS vers autres destinations (Europe, Moyen-Orient, etc.)
            // Destinations secondaires en rotation pour variété
            List<String> secondaryDestinations = new ArrayList<>();
            for (Map.Entry<String, String[]> region : INTERNATIONAL_DESTINATIONS.entrySet()) {
                for (String dest : region.getValue()) {
                    boolean isMain = false;
                    for (String mainDest : mainDestinations) {
                        if (dest.equals(mainDest)) {
                            isMain = true;
                            break;
                        }
                    }
                    if (!isMain) {
                        secondaryDestinations.add(dest);
                    }
                }
            }
            
            // Rotation des destinations secondaires
            int secondaryFlightsCount = hub.equals("Alger") ? 5 : 
                                       hub.equals("Oran") ? 3 : 2;
            
            for (int i = 0; i < secondaryFlightsCount && i < secondaryDestinations.size(); i++) {
                int destIndex = (dayOffset + i) % secondaryDestinations.size();
                String destination = secondaryDestinations.get(destIndex);
        
        return flights;
    }
    
    /**
     * LAZY LOADING: Génération des sièges à la demande
     * Inclut les 3 classes: FIRST_CLASS, BUSINESS, ECONOMY
     */
    private void initializeSeatsForFlight(String flightId) {
        List<Document> seats = new ArrayList<>();
        String[] rows = {"A", "B", "C", "D", "E", "F"};
        
        for (int i = 1; i <= 25; i++) {
            for (String row : rows) {
                String seatNumber = i + row;
                String seatClass;
                double basePrice;
                
                if (i <= 2) {
                    seatClass = "FIRST_CLASS";
                    basePrice = 50000.0;
                } else if (i <= 6) {
                    seatClass = "BUSINESS";
                    basePrice = 30000.0;
                } else {
                    seatClass = "ECONOMY";
                    basePrice = 15000.0;
                }
                
                Document seat = new Document()
                    .append("flightId", flightId)
                    .append("seatNumber", seatNumber)
                    .append("seatClass", seatClass)
                    .append("status", "AVAILABLE")
                    .append("price", basePrice);
                
                seats.add(seat);
            }
        }
        
        seatRepository.insertSeats(seats);
        System.out.println("✅ " + seats.size() + " sièges générés (lazy) pour " + flightId);
    }
    
    // ========== MÉTHODES UTILITAIRES ==========
    
    private int calculateDomesticDuration(String from, String to) {
        if ((from.equals("Alger") && to.equals("Oran")) || 
            (from.equals("Oran") && to.equals("Alger"))) return 60;
        if (from.contains("Tamanrasset") || to.contains("Tamanrasset")) return 150;
        if (from.contains("Hassi") || to.contains("Hassi")) return 90;
        return 75;
    }
    
    private int calculateInternationalDuration(String from, String to) {
        if (to.contains("Paris") || to.contains("Marseille")) return 150;
        if (to.contains("Istanbul") || to.contains("Athènes")) return 180;
        if (to.contains("Dubaï") || to.contains("Doha")) return 300;
        if (to.contains("New York") || to.contains("Miami")) return 480;
        if (to.contains("Pékin") || to.contains("Tokyo")) return 660;
        return 180;
    }
    
    private double calculateDomesticPrice(String from, String to) {
        if (from.contains("Tamanrasset") || to.contains("Tamanrasset")) return 45000.0;
        if (from.contains("Hassi") || to.contains("Hassi")) return 35000.0;
        return 25000.0;
    }
    
    private double calculateInternationalPrice(String from, String to) {
        if (to.contains("Paris") || to.contains("Londres")) return 75000.0;
        if (to.contains("Dubaï") || to.contains("Doha")) return 95000.0;
        if (to.contains("New York") || to.contains("Miami")) return 180000.0;
        if (to.contains("Pékin") || to.contains("Tokyo")) return 250000.0;
        return 65000.0;
    }
    
    private String selectDomesticAircraft(String from, String to) {
        if (from.contains("Tamanrasset") || to.contains("Tamanrasset")) {
            return "Airbus A330";
        }
        return "Boeing 737";
    }
    
    private String selectInternationalAircraft(String destination) {
        if (destination.contains("New York") || destination.contains("Pékin")) {
            return "Boeing 777";
        }
        if (destination.contains("Dubaï") || destination.contains("Tokyo")) {
            return "Boeing 787";
        }
        return "Airbus A320";
    }
    
    private String selectInternationalAirline(String destination) {
        if (destination.contains("Paris") || destination.contains("Lyon")) return "Air France";
        if (destination.contains("Istanbul")) return "Turkish Airlines";
        if (destination.contains("Dubaï")) return "Emirates";
        if (destination.contains("Doha")) return "Qatar Airways";
        if (destination.contains("Londres")) return "British Airways";
        if (destination.contains("Berlin")) return "Lufthansa";
        return "Air Algérie";
    }
    
    private String generateFlightNumber(String airline, Random random) {
        Map<String, String> airlineCodes = new HashMap<String, String>() {{
            put("Air Algérie", "AH");
            put("Air France", "AF");
            put("Turkish Airlines", "TK");
            put("Emirates", "EK");
            put("Qatar Airways", "QR");
            put("Lufthansa", "LH");
            put("British Airways", "BA");
        }};
        
        String code = airlineCodes.getOrDefault(airline, "XX");
        return code + (1000 + random.nextInt(9000));
    }
    
    private String formatDuration(int minutes) {
        int hours = minutes / 60;
        int mins = minutes % 60;
        return String.format("%dh %02dm", hours, mins);
    }
    
    private Document createFlightDocument(
            String flightId, String flightNumber, String airline,
            String departureCity, String arrivalCity,
            String departureDate, String departureTime,
            String arrivalDate, String arrivalTime,
            String duration, double economyPrice,
            double businessPrice, double firstClassPrice,
            int availableSeats, String aircraftType) {
        
        return new Document()
            .append("flightId", flightId)
            .append("flightNumber", flightNumber)
            .append("airline", airline)
            .append("departureCity", departureCity)
            .append("arrivalCity", arrivalCity)
            .append("departureDate", departureDate)
            .append("departureTime", departureTime)
            .append("arrivalDate", arrivalDate)
            .append("arrivalTime", arrivalTime)
            .append("duration", duration)
            .append("economyPrice", economyPrice)
            .append("businessPrice", businessPrice)
            .append("firstClassPrice", firstClassPrice)
            .append("availableSeats", availableSeats)
            .append("aircraftType", aircraftType);
    }
    
    // ========== MÉTHODES CORBA ==========
    
    @Override
    public Flight[] searchFlights(
            String departureCity,
            String arrivalCity,
            String date,
            String seatClass) {
        
        System.out.println("🔍 Recherche: " + departureCity + " → " + 
                         arrivalCity + " | " + date + " | " + seatClass);
        
        List<Document> flightDocs = flightRepository.searchFlights(
            departureCity, arrivalCity, date, seatClass
        );
        
        Flight[] flights = new Flight[flightDocs.size()];
        for (int i = 0; i < flightDocs.size(); i++) {
            flights[i] = ManagerHelper.documentToFlight(flightDocs.get(i));
        }
        
        System.out.println("✅ " + flights.length + " vol(s) trouvé(s)");
        return flights;
    }
    
    @Override
    public Flight getFlightById(String flightId) throws FlightNotFoundException {
        Document doc = flightRepository.findById(flightId);
        if (doc == null) {
            throw new FlightNotFoundException("Vol non trouvé: " + flightId);
        }
        return ManagerHelper.documentToFlight(doc);
    }
    
    @Override
    public Seat[] getAvailableSeats(String flightId) throws FlightNotFoundException {
        if (flightRepository.findById(flightId) == null) {
            throw new FlightNotFoundException("Vol non trouvé: " + flightId);
        }
        
        // LAZY LOADING: Vérifier si sièges existent
        if (seatRepository.countSeatsByFlight(flightId) == 0) {
            System.out.println("⚡ Lazy loading: génération des sièges pour " + flightId);
            initializeSeatsForFlight(flightId);
        }
        
        List<Document> seatDocs = seatRepository.findSeatsByFlightId(flightId);
        Seat[] seats = new Seat[seatDocs.size()];
        
        for (int i = 0; i < seatDocs.size(); i++) {
            seats[i] = ManagerHelper.documentToSeat(seatDocs.get(i));
        }
        
        return seats;
    }
    
    @Override
    public boolean isSeatAvailable(String flightId, String seatNumber) {
        Document seatDoc = seatRepository.findSeat(flightId, seatNumber);
        if (seatDoc == null) {
            System.err.println("✗ Siège introuvable: " + seatNumber);
            return false;
        }
        
        boolean available = "AVAILABLE".equals(seatDoc.getString("status"));
        return available;
    }
    
    public boolean reserveSeat(String flightId, String seatNumber) {
        try {
            boolean updated = seatRepository.updateSeatStatusAtomic(
                flightId, seatNumber, "AVAILABLE", "OCCUPIED"
            );
            
            if (updated) {
                boolean decremented = flightRepository.decrementAvailableSeats(flightId);
                
                if (decremented) {
                    System.out.println("✅ Siège réservé: " + seatNumber);
                    return true;
                } else {
                    seatRepository.updateSeatStatus(flightId, seatNumber, "AVAILABLE");
                    System.err.println("✗ Échec - Rollback: " + seatNumber);
                    return false;
                }
            }
            
        } catch (Exception e) {
            System.err.println("✗ Erreur réservation: " + e.getMessage());
        }
        
        return false;
    }
    
    public boolean reserveSeats(String flightId, List<String> seatNumbers) {
        if (seatNumbers == null || seatNumbers.isEmpty()) {
            return false;
        }
        
        try {
            long modifiedCount = seatRepository.updateSeatsStatusBulk(
                flightId, seatNumbers, "AVAILABLE", "OCCUPIED"
            );
            
            if (modifiedCount == seatNumbers.size()) {
                boolean decremented = flightRepository.decrementAvailableSeats(
                    flightId, (int) modifiedCount
                );
                
                if (decremented) {
                    System.out.println("✅ Réservation bulk: " + modifiedCount + " sièges");
                    return true;
                } else {
                    seatRepository.updateSeatsStatusBulk(
                        flightId, seatNumbers, "OCCUPIED", "AVAILABLE"
                    );
                    return false;
                }
            }
            
        } catch (Exception e) {
            System.err.println("✗ Erreur bulk: " + e.getMessage());
        }
        
        return false;
    }
    
    public boolean releaseSeat(String flightId, String seatNumber) {
        try {
            boolean updated = seatRepository.updateSeatStatus(
                flightId, seatNumber, "AVAILABLE"
            );
            
            if (updated) {
                flightRepository.incrementAvailableSeats(flightId);
                return true;
            }
        } catch (Exception e) {
            System.err.println("✗ Erreur libération: " + e.getMessage());
        }
        return false;
    }
    
    public boolean releaseSeats(String flightId, List<String> seatNumbers) {
        if (seatNumbers == null || seatNumbers.isEmpty()) {
            return false;
        }
        
        try {
            long modifiedCount = seatRepository.updateSeatsStatusBulk(
                flightId, seatNumbers, "OCCUPIED", "AVAILABLE"
            );
            
            if (modifiedCount > 0) {
                flightRepository.incrementAvailableSeats(flightId, (int) modifiedCount);
                return true;
            }
        } catch (Exception e) {
            System.err.println("✗ Erreur libération bulk: " + e.getMessage());
        }
        return false;
    }
    
    public boolean verifyFlightDataConsistency(String flightId) {
        Document flight = flightRepository.findById(flightId);
        if (flight == null) return false;
        
        int recorded = flight.getInteger("availableSeats");
        long actual = seatRepository.countAvailableSeats(flightId);
        
        return recorded == actual;
    }
    
    public boolean fixFlightDataConsistency(String flightId) {
        long actual = seatRepository.countAvailableSeats(flightId);
        
        Document updates = new Document()
            .append("availableSeats", (int) actual)
            .append("lastConsistencyCheck", new Date());
        
        flightRepository.updateFlight(flightId, updates);
        System.out.println("✅ Cohérence corrigée pour " + flightId);
        return true;
    }
    
    public FlightStatistics getFlightStatistics(String flightId) {
        Document flight = flightRepository.findById(flightId);
        if (flight == null) return null;
        
        long totalSeats = seatRepository.countSeatsByFlight(flightId);
        long availableSeats = seatRepository.countAvailableSeats(flightId);
        long occupiedSeats = seatRepository.countOccupiedSeats(flightId);
        long reservedSeats = totalSeats - availableSeats;
        
        double occupancyRate = totalSeats > 0 
            ? (double) occupiedSeats / totalSeats * 100 
            : 0;
        
        return new FlightStatistics(
            flightId,
            flight.getString("flightNumber"),
            totalSeats,
            availableSeats,
            occupiedSeats,
            reservedSeats,
            occupancyRate
        );
    }
    
    public static class FlightStatistics {
        public final String flightId;
        public final String flightNumber;
        public final long totalSeats;
        public final long availableSeats;
        public final long occupiedSeats;
        public final long reservedSeats;
        public final double occupancyRate;
        
        public FlightStatistics(String flightId, String flightNumber,
                              long totalSeats, long availableSeats,
                              long occupiedSeats, long reservedSeats,
                              double occupancyRate) {
            this.flightId = flightId;
            this.flightNumber = flightNumber;
            this.totalSeats = totalSeats;
            this.availableSeats = availableSeats;
            this.occupiedSeats = occupiedSeats;
            this.reservedSeats = reservedSeats;
            this.occupancyRate = occupancyRate;
        }
        
        @Override
        public String toString() {
            return String.format(
                "Vol %s (%s): %d/%d sièges occupés (%.1f%% taux d'occupation)",
                flightNumber, flightId, occupiedSeats, totalSeats, occupancyRate
            );
        }
    }
}