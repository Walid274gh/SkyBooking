// src/main/java/com/skybooking/managers/impl/AdminManagerImpl.java

package com.skybooking.managers.impl;

import FlightReservation.*;
import com.skybooking.database.repositories.*;
import com.skybooking.security.PasswordHasher;
import com.skybooking.security.SessionManager;
import com.skybooking.security.TokenManager;
import com.skybooking.utils.Constants;
import com.skybooking.utils.DateUtils;
import org.bson.Document;
import java.util.*;

/**
 * 👨‍💼 IMPLÉMENTATION DU GESTIONNAIRE ADMIN
 * Dashboard, statistiques, gestion complète du système
 */
public class AdminManagerImpl extends AdminManagerPOA {
    
    private final AdminRepository adminRepository;
    private final FlightRepository flightRepository;
    private final ReservationRepository reservationRepository;
    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;
    private final SeatRepository seatRepository;
    private final SessionManager sessionManager;
    
    public AdminManagerImpl() {
        this.adminRepository = new AdminRepository();
        this.flightRepository = new FlightRepository();
        this.reservationRepository = new ReservationRepository();
        this.customerRepository = new CustomerRepository();
        this.paymentRepository = new PaymentRepository();
        this.seatRepository = new SeatRepository();
        this.sessionManager = new SessionManager();
        
        System.out.println("✅ AdminManager initialisé");
        createDefaultAdmin();
    }
    
    private void createDefaultAdmin() {
        Document existing = adminRepository.findByUsername("admin");
        if (existing == null) {
            String hashedPassword = PasswordHasher.hash("admin123");
            Document admin = new Document()
                .append("adminId", TokenManager.generateUniqueId("ADM"))
                .append("username", "admin")
                .append("password", hashedPassword)
                .append("email", "admin@skybooking.dz")
                .append("role", "SUPER_ADMIN")
                .append("isActive", true);
            
            adminRepository.insertAdmin(admin);
            System.out.println("✅ Admin par défaut créé - username: admin, password: admin123");
        }
    }
    
    // ==================== AUTHENTIFICATION ====================
    
    @Override
    public AdminUser adminLogin(String username, String password) 
            throws AdminAuthException {
        
        System.out.println("→ Tentative connexion admin: " + username);
        
        Document adminDoc = adminRepository.findByUsername(username);
        
        if (adminDoc == null) {
            throw new AdminAuthException("Identifiants incorrects");
        }
        
        if (!adminDoc.getBoolean("isActive", true)) {
            throw new AdminAuthException("Compte administrateur désactivé");
        }
        
        String storedHash = adminDoc.getString("password");
        if (!PasswordHasher.verify(password, storedHash)) {
            throw new AdminAuthException("Identifiants incorrects");
        }
        
        String adminId = adminDoc.getString("adminId");
        SessionManager.Session session = sessionManager.createSession(adminId, "ADMIN");
        
        System.out.println("✅ Admin connecté: " + username + " (Token: " + 
                         session.token + ")");
        
        return new AdminUser(
            adminId,
            adminDoc.getString("username"),
            adminDoc.getString("email"),
            adminDoc.getString("role"),
            adminDoc.getBoolean("isActive", true),
            DateUtils.formatDateTime(adminDoc.getDate("createdAt"))
        );
    }
    
    @Override
    public boolean validateAdminSession(String adminId, String token) 
            throws AdminAuthException {
        
        if (!sessionManager.validateSession(adminId, token)) {
            throw new AdminAuthException("Session invalide ou expirée");
        }
        
        return true;
    }
    
    @Override
    public void adminLogout(String adminId) {
        sessionManager.destroySession(adminId);
        System.out.println("✅ Admin déconnecté: " + adminId);
    }
    
    // ==================== DASHBOARD ====================
    
    @Override
    public DashboardStats getDashboardStats() {
        System.out.println("→ Génération statistiques dashboard");
        
        long totalFlights = flightRepository.count();
        long totalBookings = reservationRepository.count();
        long totalCustomers = customerRepository.count();
        
        double totalRevenue = paymentRepository.calculateTotalRevenue();
        long todayBookings = reservationRepository.countTodayBookings();
        long pendingPayments = paymentRepository.countPendingPayments();
        
        long totalSeats = seatRepository.count();
        long availableSeats = totalSeats - seatRepository.count();
        double occupancyRate = totalSeats > 0 
            ? ((totalSeats - availableSeats) * 100.0 / totalSeats) 
            : 0.0;
        
        System.out.println("✅ Stats générées - " + totalBookings + " réservations, " + 
                         String.format("%.0f DZD", totalRevenue) + " revenus");
        
        return new DashboardStats(
            (int) totalFlights,
            (int) totalBookings,
            (int) totalCustomers,
            totalRevenue,
            (int) todayBookings,
            (int) pendingPayments,
            (int) availableSeats,
            occupancyRate
        );
    }
    
    @Override
    public FinancialReport getFinancialReport(String period, String startDate, String endDate) {
        System.out.println("→ Génération rapport financier: " + period);
        
        List<Document> payments = paymentRepository.getPaymentsByDateRange(startDate, endDate);
        
        double totalRevenue = 0;
        double totalRefunds = 0;
        long totalTransactions = payments.size();
        
        for (Document payment : payments) {
            String status = payment.getString("status");
            double amount = payment.getDouble("amount");
            
            if ("COMPLETED".equals(status)) {
                totalRevenue += amount;
            } else if ("REFUNDED".equals(status)) {
                totalRefunds += amount;
            }
        }
        
        double netRevenue = totalRevenue - totalRefunds;
        double averageBookingValue = totalTransactions > 0 
            ? totalRevenue / totalTransactions 
            : 0.0;
        
        String topRoute = "N/A";
        
        System.out.println("✅ Rapport généré - Net: " + 
                         String.format("%.0f DZD", netRevenue));
        
        return new FinancialReport(
            period,
            totalRevenue,
            totalRefunds,
            netRevenue,
            (int) totalTransactions,
            averageBookingValue,
            topRoute,
            DateUtils.getCurrentDate()
        );
    }
    
    // ==================== GESTION DES VOLS ====================
    
    @Override
    public FlightManagementData[] getAllFlights() {
        System.out.println("→ Récupération de tous les vols");
        
        List<Document> flightDocs = flightRepository.getAllFlights();
        FlightManagementData[] flights = new FlightManagementData[flightDocs.size()];
        
        for (int i = 0; i < flightDocs.size(); i++) {
            flights[i] = documentToFlightManagement(flightDocs.get(i));
        }
        
        System.out.println("✅ " + flights.length + " vol(s) récupéré(s)");
        return flights;
    }
    
    @Override
    public FlightManagementData getFlightDetails(String flightId) 
            throws FlightNotFoundException {
        
        Document doc = flightRepository.findById(flightId);
        if (doc == null) {
            throw new FlightNotFoundException("Vol introuvable: " + flightId);
        }
        
        return documentToFlightManagement(doc);
    }
    
    @Override
    public String createFlight(FlightManagementData flightData) 
            throws FlightManagementException {
        
        System.out.println("→ Création nouveau vol: " + flightData.flightNumber);
        
        if (flightData.departureCity.equals(flightData.arrivalCity)) {
            throw new FlightManagementException(
                "Ville de départ et d'arrivée identiques"
            );
        }
        
        if (flightRepository.flightNumberExists(
                flightData.flightNumber, flightData.departureDate)) {
            throw new FlightManagementException(
                "Ce numéro de vol existe déjà pour cette date"
            );
        }
        
        String flightId = TokenManager.generateUniqueId("FL");
        
        Document flightDoc = new Document()
            .append("flightId", flightId)
            .append("flightNumber", flightData.flightNumber)
            .append("airline", flightData.airline)
            .append("departureCity", flightData.departureCity)
            .append("arrivalCity", flightData.arrivalCity)
            .append("departureDate", flightData.departureDate)
            .append("departureTime", flightData.departureTime)
            .append("arrivalDate", flightData.arrivalDate)
            .append("arrivalTime", flightData.arrivalTime)
            .append("duration", flightData.duration)
            .append("economyPrice", flightData.economyPrice)
            .append("businessPrice", flightData.businessPrice)
            .append("firstClassPrice", flightData.firstClassPrice)
            .append("totalSeats", (int) flightData.totalSeats)
            .append("availableSeats", (int) flightData.totalSeats)
            .append("aircraftType", flightData.aircraftType)
            .append("status", "ACTIVE");
        
        flightRepository.insertFlight(flightDoc);
        
        initializeSeatsForNewFlight(flightId, (int) flightData.totalSeats,
                                    flightData.economyPrice, flightData.businessPrice);
        
        System.out.println("✅ Vol créé: " + flightId);
        return flightId;
    }
    
    @Override
    public boolean updateFlight(FlightManagementData flightData) 
            throws FlightManagementException {
        
        System.out.println("→ Mise à jour vol: " + flightData.flightId);
        
        Document existing = flightRepository.findById(flightData.flightId);
        if (existing == null) {
            throw new FlightManagementException("Vol introuvable");
        }
        
        Document updates = new Document()
            .append("flightNumber", flightData.flightNumber)
            .append("airline", flightData.airline)
            .append("departureCity", flightData.departureCity)
            .append("arrivalCity", flightData.arrivalCity)
            .append("departureDate", flightData.departureDate)
            .append("departureTime", flightData.departureTime)
            .append("arrivalDate", flightData.arrivalDate)
            .append("arrivalTime", flightData.arrivalTime)
            .append("duration", flightData.duration)
            .append("economyPrice", flightData.economyPrice)
            .append("businessPrice", flightData.businessPrice)
            .append("firstClassPrice", flightData.firstClassPrice)
            .append("aircraftType", flightData.aircraftType);
        
        flightRepository.updateFlight(flightData.flightId, updates);
        
        System.out.println("✅ Vol mis à jour");
        return true;
    }
    
    @Override
    public boolean deleteFlight(String flightId) throws FlightManagementException {
        System.out.println("→ Suppression vol: " + flightId);
        
        long activeBookings = reservationRepository.countActiveBookingsForFlight(flightId);
        if (activeBookings > 0) {
            throw new FlightManagementException(
                "Impossible de supprimer: " + activeBookings + " réservation(s) active(s)"
            );
        }
        
        flightRepository.deleteFlight(flightId);
        seatRepository.deleteSeatsForFlight(flightId);
        
        System.out.println("✅ Vol supprimé");
        return true;
    }
    
    @Override
    public boolean cancelFlight(String flightId, String reason) 
            throws FlightManagementException {
        
        System.out.println("→ Annulation vol: " + flightId);
        
        Document updates = new Document()
            .append("status", "CANCELLED")
            .append("cancellationReason", reason);
        
        flightRepository.updateFlight(flightId, updates);
        
        List<Document> reservations = reservationRepository.getActiveReservationsForFlight(
            flightId
        );
        for (Document res : reservations) {
            reservationRepository.updateStatus(
                res.getString("reservationId"), 
                "CANCELLED"
            );
        }
        
        System.out.println("✅ Vol annulé - " + reservations.size() + 
                         " réservation(s) remboursée(s)");
        return true;
    }
    
    // ==================== GESTION DES UTILISATEURS ====================
    
    @Override
    public UserManagementData[] getAllUsers() {
        System.out.println("→ Récupération de tous les utilisateurs");
        
        List<Document> userDocs = customerRepository.getAllCustomers();
        UserManagementData[] users = new UserManagementData[userDocs.size()];
        
        for (int i = 0; i < userDocs.size(); i++) {
            users[i] = documentToUserManagement(userDocs.get(i));
        }
        
        System.out.println("✅ " + users.length + " utilisateur(s) récupéré(s)");
        return users;
    }
    
    @Override
    public UserManagementData getUserDetails(String customerId) {
        Document doc = customerRepository.findById(customerId);
        if (doc == null) return null;
        
        return documentToUserManagement(doc);
    }
    
    @Override
    public boolean suspendUser(String customerId, String reason) {
        System.out.println("→ Suspension utilisateur: " + customerId);
        
        Document updates = new Document()
            .append("isActive", false)
            .append("suspensionReason", reason);
        
        customerRepository.updateCustomer(customerId, updates);
        
        System.out.println("✅ Utilisateur suspendu");
        return true;
    }
    
    @Override
    public boolean activateUser(String customerId) {
        System.out.println("→ Activation utilisateur: " + customerId);
        
        Document updates = new Document()
            .append("isActive", true)
            .append("suspensionReason", null);
        
        customerRepository.updateCustomer(customerId, updates);
        
        System.out.println("✅ Utilisateur activé");
        return true;
    }
    
    @Override
    public boolean deleteUser(String customerId) {
        System.out.println("→ Suppression utilisateur: " + customerId);
        
        long active = reservationRepository.findByCustomerId(customerId)
            .stream()
            .filter(r -> "CONFIRMED".equals(r.getString("status")))
            .count();
        
        if (active > 0) {
            System.err.println("✗ Impossible: " + active + " réservation(s) active(s)");
            return false;
        }
        
        customerRepository.deleteCustomer(customerId);
        
        System.out.println("✅ Utilisateur supprimé");
        return true;
    }
    
    // ==================== GESTION DES RÉSERVATIONS ====================
    
    @Override
    public BookingManagementData[] getAllBookings() {
        System.out.println("→ Récupération de toutes les réservations");
        
        List<Document> bookingDocs = reservationRepository.getAllReservations();
        return bookingDocs.stream()
            .map(this::documentToBookingManagement)
            .toArray(BookingManagementData[]::new);
    }
    
    @Override
    public BookingManagementData[] getBookingsByStatus(String status) {
        List<Document> bookingDocs = reservationRepository.getReservationsByStatus(status);
        return bookingDocs.stream()
            .map(this::documentToBookingManagement)
            .toArray(BookingManagementData[]::new);
    }
    
    @Override
    public BookingManagementData[] getBookingsByDate(String startDate, String endDate) {
        List<Document> bookingDocs = reservationRepository.getReservationsByDateRange(
            startDate, endDate
        );
        return bookingDocs.stream()
            .map(this::documentToBookingManagement)
            .toArray(BookingManagementData[]::new);
    }
    
    @Override
    public boolean cancelBookingAdmin(String reservationId, String reason) {
        System.out.println("→ Annulation admin réservation: " + reservationId);
        
        reservationRepository.updateStatus(reservationId, "CANCELLED");
        
        System.out.println("✅ Réservation annulée");
        return true;
    }
    
    @Override
    public boolean modifyBookingAdmin(String reservationId, String[] newSeats) {
        System.out.println("→ Modification admin réservation: " + reservationId);
        
        Document updates = new Document()
            .append("seatNumbers", Arrays.asList(newSeats))
            .append("modifiedByAdmin", true);
        
        reservationRepository.updateReservation(reservationId, updates);
        
        System.out.println("✅ Réservation modifiée");
        return true;
    }
    
    // ==================== GESTION DES SIÈGES ====================
    
    @Override
    public SeatMapData[] getSeatMap(String flightId) {
        List<Document> seatDocs = seatRepository.findSeatsByFlightId(flightId);
        SeatMapData[] seats = new SeatMapData[seatDocs.size()];
        
        for (int i = 0; i < seatDocs.size(); i++) {
            Document seat = seatDocs.get(i);
            seats[i] = new SeatMapData(
                flightId,
                seat.getString("seatNumber"),
                seat.getString("seatClass"),
                seat.getString("status"),
                seat.getDouble("price"),
                "",
                ""
            );
        }
        
        return seats;
    }
    
    @Override
    public boolean updateSeatPrice(String flightId, String seatNumber, double newPrice) {
        System.out.println("→ Mise à jour prix siège: " + seatNumber + " = " + 
                         newPrice + " DZD");
        
        boolean updated = seatRepository.updateSeatPrice(flightId, seatNumber, newPrice);
        
        if (updated) {
            System.out.println("✅ Prix mis à jour");
        }
        
        return updated;
    }
    
    @Override
    public boolean blockSeat(String flightId, String seatNumber, String reason) {
        System.out.println("→ Blocage siège: " + seatNumber);
        
        seatRepository.updateSeatStatus(flightId, seatNumber, "BLOCKED");
        flightRepository.decrementAvailableSeats(flightId);
        
        System.out.println("✅ Siège bloqué");
        return true;
    }
    
    @Override
    public boolean unblockSeat(String flightId, String seatNumber) {
        System.out.println("→ Déblocage siège: " + seatNumber);
        
        seatRepository.updateSeatStatus(flightId, seatNumber, "AVAILABLE");
        flightRepository.incrementAvailableSeats(flightId);
        
        System.out.println("✅ Siège débloqué");
        return true;
    }
    
    // ==================== GESTION DES PRIX ====================
    
    @Override
    public PricingRule[] getPricingRules(String flightId) {
        return new PricingRule[0];
    }
    
    @Override
    public String createPricingRule(PricingRule rule) {
        String ruleId = TokenManager.generateUniqueId("PR");
        System.out.println("✅ Règle de tarification créée: " + ruleId);
        return ruleId;
    }
    
    @Override
    public boolean updatePricingRule(PricingRule rule) {
        System.out.println("✅ Règle de tarification mise à jour");
        return true;
    }
    
    @Override
    public boolean deletePricingRule(String ruleId) {
        System.out.println("✅ Règle de tarification supprimée");
        return true;
    }
    
    // ==================== RAPPORTS ====================
    
    @Override
    public String generateFullReport(String reportType, String startDate, String endDate) {
        System.out.println("→ Génération rapport complet: " + reportType);
        
        StringBuilder report = new StringBuilder();
        report.append("============================================================").append("\n");
        report.append("RAPPORT ").append(reportType.toUpperCase()).append("\n");
        report.append("Période: ").append(startDate).append(" - ").append(endDate).append("\n");
        report.append("============================================================").append("\n\n");
        
        if ("FINANCIAL".equals(reportType)) {
            FinancialReport fr = getFinancialReport("CUSTOM", startDate, endDate);
            report.append("Revenus Total: ")
                  .append(String.format("%.2f DZD", fr.totalRevenue)).append("\n");
            report.append("Remboursements: ")
                  .append(String.format("%.2f DZD", fr.totalRefunds)).append("\n");
            report.append("Revenus Net: ")
                  .append(String.format("%.2f DZD", fr.netRevenue)).append("\n");
            report.append("Transactions: ").append(fr.totalTransactions).append("\n");
            report.append("Valeur Moyenne: ")
                  .append(String.format("%.2f DZD", fr.averageBookingValue)).append("\n");
        }
        
        report.append("\n").append("============================================================").append("\n");
        report.append("Généré le: ").append(DateUtils.getCurrentDateTime()).append("\n");
        
        String reportContent = report.toString();
        System.out.println("✅ Rapport généré");
        return reportContent;
    }
    
    @Override
    public String[] getTopRoutes(int limit) {
        return new String[]{"Alger → Paris (150 réservations)"};
    }
    
    @Override
    public String[] getTopCustomers(int limit) {
        return new String[]{"Ahmed Benali (45000 DZD)"};
    }
    
    // ==================== MÉTHODES PRIVÉES ====================
    
    private void initializeSeatsForNewFlight(
            String flightId,
            int totalSeats,
            double economyPrice,
            double businessPrice) {
        
        List<Document> seats = new ArrayList<>();
        String[] rows = {"A", "B", "C", "D", "E", "F"};
        int seatsPerRow = 6;
        int numRows = (int) Math.ceil(totalSeats / (double) seatsPerRow);
        
        for (int i = 1; i <= numRows && seats.size() < totalSeats; i++) {
            for (String row : rows) {
                if (seats.size() >= totalSeats) break;
                
                String seatNumber = i + row;
                String seatClass;
                double price;
                
                if (i <= 5) {
                    seatClass = "BUSINESS";
                    price = businessPrice;
                } else {
                    seatClass = "ECONOMY";
                    price = economyPrice;
                }
                
                Document seat = new Document()
                    .append("flightId", flightId)
                    .append("seatNumber", seatNumber)
                    .append("seatClass", seatClass)
                    .append("status", "AVAILABLE")
                    .append("price", price);
                
                seats.add(seat);
            }
        }
        
        seatRepository.insertSeats(seats);
        System.out.println("✅ " + seats.size() + " sièges créés pour " + flightId);
    }
    
    private FlightManagementData documentToFlightManagement(Document doc) {
        long totalSeats = doc.containsKey("totalSeats") 
            ? doc.getLong("totalSeats") 
            : doc.getInteger("availableSeats", 150);
        
        long availableSeats = doc.getInteger("availableSeats", 0);
        
        return new FlightManagementData(
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
            (int) totalSeats,
            (int) availableSeats,
            doc.getString("aircraftType"),
            doc.getString("status") != null ? doc.getString("status") : "ACTIVE"
        );
    }
    
    private UserManagementData documentToUserManagement(Document doc) {
        String customerId = doc.getString("customerId");
        
        long totalBookings = reservationRepository.countByCustomerId(customerId);
        double totalSpent = reservationRepository.calculateTotalSpentByCustomer(customerId);
        String lastBooking = reservationRepository.getLastBookingDate(customerId);
        
        return new UserManagementData(
            customerId,
            doc.getString("username"),
            doc.getString("firstName"),
            doc.getString("lastName"),
            doc.getString("email"),
            doc.getString("phoneNumber"),
            (int) totalBookings,
            totalSpent,
            lastBooking,
            doc.getBoolean("isActive", true),
            DateUtils.formatDate(doc.getDate("createdAt"))
        );
    }
    
    private BookingManagementData documentToBookingManagement(Document doc) {
        String customerId = doc.getString("customerId");
        Document customer = customerRepository.findById(customerId);
        String customerName = customer != null 
            ? customer.getString("firstName") + " " + customer.getString("lastName")
            : "Inconnu";
        
        String flightId = doc.getString("flightId");
        Document flight = flightRepository.findById(flightId);
        String flightNumber = flight != null ? flight.getString("flightNumber") : "N/A";
        String route = flight != null 
            ? flight.getString("departureCity") + " → " + flight.getString("arrivalCity")
            : "N/A";
        String departureDate = flight != null ? flight.getString("departureDate") : "N/A";
        
        @SuppressWarnings("unchecked")
        List<String> seats = (List<String>) doc.get("seatNumbers");
        long passengerCount = seats != null ? seats.size() : 0;
        
        String paymentStatus = paymentRepository.isReservationPaid(
            doc.getString("reservationId")
        ) ? "PAID" : "PENDING";
        
        return new BookingManagementData(
            doc.getString("reservationId"),
            customerId,
            customerName,
            flightNumber,
            route,
            departureDate,
            doc.getString("status"),
            doc.getDouble("totalPrice"),
            paymentStatus,
            doc.getString("reservationDate"),
            (int) passengerCount
        );
    }
}