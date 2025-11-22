// src/main/java/com/skybooking/managers/impl/AccountManagerImpl.java

package com.skybooking.managers.impl;

import FlightReservation.*;
import com.skybooking.database.repositories.*;
import com.skybooking.security.PasswordHasher;
import com.skybooking.security.TokenManager;
import com.skybooking.utils.Constants;
import com.skybooking.utils.DateUtils;
import org.bson.Document;
import java.util.*;

/**
 * 👤 IMPLÉMENTATION DU GESTIONNAIRE DE COMPTE
 * Profil, historique, préférences et méthodes de paiement
 * 
 * Améliorations :
 * - Reset tokens stockés dans MongoDB avec TTL
 * - Validation avancée des mots de passe
 * - Audit trail des changements
 * - Gestion améliorée des erreurs
 * - Gestion des destinations favorites
 */
public class AccountManagerImpl extends AccountManagerPOA {
    
    private final CustomerRepository customerRepository;
    private final ReservationRepository reservationRepository;
    private final FlightRepository flightRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final ResetTokenRepository resetTokenRepository;
    private final FavoritesRepository favoritesRepository;
    
    public AccountManagerImpl() {
        this.customerRepository = new CustomerRepository();
        this.reservationRepository = new ReservationRepository();
        this.flightRepository = new FlightRepository();
        this.paymentMethodRepository = new PaymentMethodRepository();
        this.resetTokenRepository = new ResetTokenRepository();
        this.favoritesRepository = new FavoritesRepository();
        System.out.println("✅ AccountManager initialisé avec persistance améliorée");
    }
    
    // ==================== HISTORIQUE ====================
    
    @Override
    public BookingHistory[] getBookingHistory(String customerId) {
        System.out.println("→ Récupération historique pour: " + customerId);
        
        List<Document> reservations = reservationRepository.findByCustomerId(customerId);
        List<BookingHistory> history = new ArrayList<>();
        
        for (Document resDoc : reservations) {
            String reservationId = resDoc.getString("reservationId");
            String flightId = resDoc.getString("flightId");
            
            Document flightDoc = flightRepository.findById(flightId);
            if (flightDoc == null) continue;
            
            boolean canCancel = canCancelReservation(resDoc, flightDoc);
            boolean canModify = canModifyReservation(resDoc, flightDoc);
            
            BookingHistory item = new BookingHistory(
                reservationId,
                flightDoc.getString("flightNumber"),
                flightDoc.getString("departureCity"),
                flightDoc.getString("arrivalCity"),
                flightDoc.getString("departureDate"),
                resDoc.getString("status"),
                resDoc.getDouble("totalPrice"),
                resDoc.getString("reservationDate"),
                canCancel,
                canModify
            );
            
            history.add(item);
        }
        
        history.sort((a, b) -> b.bookingDate.compareTo(a.bookingDate));
        
        System.out.println("✅ " + history.size() + " réservation(s) trouvée(s)");
        return history.toArray(new BookingHistory[0]);
    }
    
    @Override
    public BookingHistory getBookingDetails(String reservationId) {
        Document resDoc = reservationRepository.findById(reservationId);
        if (resDoc == null) return null;
        
        Document flightDoc = flightRepository.findById(resDoc.getString("flightId"));
        if (flightDoc == null) return null;
        
        return new BookingHistory(
            reservationId,
            flightDoc.getString("flightNumber"),
            flightDoc.getString("departureCity"),
            flightDoc.getString("arrivalCity"),
            flightDoc.getString("departureDate"),
            resDoc.getString("status"),
            resDoc.getDouble("totalPrice"),
            resDoc.getString("reservationDate"),
            canCancelReservation(resDoc, flightDoc),
            canModifyReservation(resDoc, flightDoc)
        );
    }
    
    // ==================== PROFIL ====================
    
    @Override
    public boolean updateProfile(ProfileUpdate profileData) 
            throws InvalidPasswordException {
        
        System.out.println("→ Mise à jour profil: " + profileData.customerId);
        
        Document customerDoc = customerRepository.findById(profileData.customerId);
        if (customerDoc == null) {
            throw new InvalidPasswordException("Client introuvable");
        }
        
        String storedHash = customerDoc.getString("password");
        if (!PasswordHasher.verify(profileData.currentPassword, storedHash)) {
            throw new InvalidPasswordException("Mot de passe actuel incorrect");
        }
        
        // Validation de l'email
        if (!isValidEmail(profileData.email)) {
            throw new InvalidPasswordException("Format d'email invalide");
        }
        
        // Vérifier si l'email est déjà utilisé par un autre utilisateur
        Document existingEmail = customerRepository.findByEmail(profileData.email);
        if (existingEmail != null && 
            !existingEmail.getString("customerId").equals(profileData.customerId)) {
            throw new InvalidPasswordException("Cet email est déjà utilisé");
        }
        
        Document updates = new Document()
            .append("firstName", profileData.firstName)
            .append("lastName", profileData.lastName)
            .append("email", profileData.email)
            .append("phoneNumber", profileData.phoneNumber);
        
        if (profileData.newPassword != null && !profileData.newPassword.isEmpty()) {
            // Validation avancée du nouveau mot de passe
            PasswordHasher.PasswordValidationResult validation = 
                PasswordHasher.validatePassword(profileData.newPassword);
            
            if (!validation.isValid) {
                throw new InvalidPasswordException(
                    "Mot de passe invalide:\n" + validation.getMessage()
                );
            }
            
            // Avertir si le mot de passe est faible
            if (validation.strength.getScore() < 2) {
                System.out.println("⚠️ Mot de passe faible accepté avec avertissement");
            }
            
            String newHash = PasswordHasher.hash(profileData.newPassword);
            updates.append("password", newHash);
            
            // Audit trail
            logPasswordChange(profileData.customerId, "Profile update");
        }
        
        customerRepository.updateCustomer(profileData.customerId, updates);
        
        System.out.println("✅ Profil mis à jour avec succès");
        return true;
    }
    
    @Override
    public boolean changePassword(
            String customerId,
            String currentPassword,
            String newPassword)
            throws InvalidPasswordException {
        
        System.out.println("→ Changement mot de passe: " + customerId);
        
        Document customerDoc = customerRepository.findById(customerId);
        if (customerDoc == null) {
            throw new InvalidPasswordException("Client introuvable");
        }
        
        String storedHash = customerDoc.getString("password");
        if (!PasswordHasher.verify(currentPassword, storedHash)) {
            throw new InvalidPasswordException("Mot de passe actuel incorrect");
        }
        
        // Validation avancée du nouveau mot de passe
        PasswordHasher.PasswordValidationResult validation = 
            PasswordHasher.validatePassword(newPassword);
        
        if (!validation.isValid) {
            throw new InvalidPasswordException(
                "Mot de passe invalide:\n" + validation.getMessage()
            );
        }
        
        // Empêcher la réutilisation de l'ancien mot de passe
        if (PasswordHasher.verify(newPassword, storedHash)) {
            throw new InvalidPasswordException(
                "Le nouveau mot de passe doit être différent de l'ancien"
            );
        }
        
        String newHash = PasswordHasher.hash(newPassword);
        Document updates = new Document()
            .append("password", newHash)
            .append("passwordChangedAt", new Date());
        
        customerRepository.updateCustomer(customerId, updates);
        
        // Audit trail
        logPasswordChange(customerId, "Password change");
        
        System.out.println("✅ Mot de passe changé avec succès (Force: " + 
                         validation.strength.getLabel() + ")");
        return true;
    }
    
    // ==================== RÉINITIALISATION ====================
    
    @Override
    public String requestPasswordReset(String email) {
        System.out.println("→ Demande réinitialisation pour: " + email);
        
        Document customerDoc = customerRepository.findByEmail(email);
        if (customerDoc == null) {
            System.out.println("⚠ Email non trouvé (ne pas révéler à l'utilisateur)");
            // Retourner un token factice pour éviter l'énumération d'emails
            return "reset_token_fake_" + System.currentTimeMillis();
        }
        
        String customerId = customerDoc.getString("customerId");
        String token = TokenManager.generateResetToken();
        
        // Invalider les anciens tokens pour cet utilisateur
        resetTokenRepository.invalidateTokensForUser(customerId);
        
        // Créer un nouveau token dans MongoDB avec TTL
        Document resetTokenDoc = new Document()
            .append("token", token)
            .append("customerId", customerId)
            .append("email", email)
            .append("createdAt", new Date())
            .append("expiresAt", System.currentTimeMillis() + Constants.RESET_TOKEN_DURATION_MS)
            .append("used", false)
            .append("expireAt", new Date(System.currentTimeMillis() + Constants.RESET_TOKEN_DURATION_MS));
        
        resetTokenRepository.insertResetToken(resetTokenDoc);
        
        System.out.println("✅ Token généré et persisté: " + token);
        System.out.println("📧 SIMULATION EMAIL:");
        System.out.println("   À: " + email);
        System.out.println("   Sujet: Réinitialisation mot de passe SkyBooking");
        System.out.println("   Lien: http://localhost:5173/reset-password?token=" + token);
        System.out.println("   Expire dans: 30 minutes");
        
        // Audit trail
        logSecurityEvent(customerId, "Password reset requested", email);
        
        return token;
    }
    
    @Override
    public boolean resetPassword(PasswordReset resetData) 
            throws ResetTokenExpiredException {
        
        System.out.println("→ Réinitialisation mot de passe avec token");
        
        Document tokenDoc = resetTokenRepository.findByToken(resetData.resetToken);
        
        if (tokenDoc == null) {
            throw new ResetTokenExpiredException("Token invalide ou expiré");
        }
        
        // Vérifier si le token a déjà été utilisé
        if (tokenDoc.getBoolean("used", false)) {
            throw new ResetTokenExpiredException("Token déjà utilisé");
        }
        
        // Vérifier l'expiration
        long expiresAt = tokenDoc.getLong("expiresAt");
        if (System.currentTimeMillis() > expiresAt) {
            resetTokenRepository.deleteToken(resetData.resetToken);
            throw new ResetTokenExpiredException(
                "Token expiré. Demandez un nouveau lien."
            );
        }
        
        // Validation avancée du nouveau mot de passe
        PasswordHasher.PasswordValidationResult validation = 
            PasswordHasher.validatePassword(resetData.newPassword);
        
        if (!validation.isValid) {
            throw new ResetTokenExpiredException(
                "Mot de passe invalide:\n" + validation.getMessage()
            );
        }
        
        String customerId = tokenDoc.getString("customerId");
        Document customerDoc = customerRepository.findById(customerId);
        
        if (customerDoc == null) {
            throw new ResetTokenExpiredException("Client introuvable");
        }
        
        // Empêcher la réutilisation de l'ancien mot de passe
        String oldHash = customerDoc.getString("password");
        if (PasswordHasher.verify(resetData.newPassword, oldHash)) {
            throw new ResetTokenExpiredException(
                "Le nouveau mot de passe doit être différent de l'ancien"
            );
        }
        
        String newHash = PasswordHasher.hash(resetData.newPassword);
        Document updates = new Document()
            .append("password", newHash)
            .append("passwordResetAt", new Date());
        
        customerRepository.updateCustomer(customerId, updates);
        
        // Marquer le token comme utilisé
        resetTokenRepository.markTokenAsUsed(resetData.resetToken);
        
        // Audit trail
        logPasswordChange(customerId, "Password reset");
        logSecurityEvent(customerId, "Password reset completed", tokenDoc.getString("email"));
        
        System.out.println("✅ Mot de passe réinitialisé avec succès (Force: " + 
                         validation.strength.getLabel() + ")");
        return true;
    }
    
    // ==================== MÉTHODES DE PAIEMENT ====================
    
    @Override
    public SavedPaymentMethod savePaymentMethod(SavedPaymentMethod paymentMethod) {
        System.out.println("→ Sauvegarde méthode paiement: " + paymentMethod.customerId);
        
        // Validation du type de carte
        if (!isValidCardType(paymentMethod.cardType)) {
            System.err.println("❌ Type de carte invalide: " + paymentMethod.cardType);
            return null;
        }
        
        // Validation de la date d'expiration
        if (!isValidExpiryDate(paymentMethod.expiryDate)) {
            System.err.println("❌ Date d'expiration invalide: " + paymentMethod.expiryDate);
            return null;
        }
        
        String paymentMethodId = TokenManager.generateUniqueId("PM");
        
        Document pmDoc = new Document()
            .append("paymentMethodId", paymentMethodId)
            .append("customerId", paymentMethod.customerId)
            .append("cardType", paymentMethod.cardType)
            .append("maskedCardNumber", paymentMethod.maskedCardNumber)
            .append("cardHolderName", paymentMethod.cardHolderName)
            .append("expiryDate", paymentMethod.expiryDate)
            .append("isDefault", paymentMethod.isDefault)
            .append("createdAt", new Date());
        
        paymentMethodRepository.insertPaymentMethod(pmDoc);
        
        if (paymentMethod.isDefault) {
            paymentMethodRepository.unsetDefaultPaymentMethods(
                paymentMethod.customerId, 
                paymentMethodId
            );
        }
        
        System.out.println("✅ Méthode de paiement sauvegardée: " + paymentMethodId);
        
        return new SavedPaymentMethod(
            paymentMethodId,
            paymentMethod.customerId,
            paymentMethod.cardType,
            paymentMethod.maskedCardNumber,
            paymentMethod.cardHolderName,
            paymentMethod.expiryDate,
            paymentMethod.isDefault
        );
    }
    
    @Override
    public SavedPaymentMethod[] getSavedPaymentMethods(String customerId) {
        List<Document> pmDocs = paymentMethodRepository.findByCustomerId(customerId);
        
        SavedPaymentMethod[] methods = new SavedPaymentMethod[pmDocs.size()];
        for (int i = 0; i < pmDocs.size(); i++) {
            Document doc = pmDocs.get(i);
            methods[i] = new SavedPaymentMethod(
                doc.getString("paymentMethodId"),
                doc.getString("customerId"),
                doc.getString("cardType"),
                doc.getString("maskedCardNumber"),
                doc.getString("cardHolderName"),
                doc.getString("expiryDate"),
                doc.getBoolean("isDefault", false)
            );
        }
        
        System.out.println("→ " + methods.length + " méthode(s) de paiement");
        return methods;
    }
    
    @Override
    public boolean deletePaymentMethod(String paymentMethodId) {
        boolean deleted = paymentMethodRepository.deletePaymentMethod(paymentMethodId);
        if (deleted) {
            System.out.println("✅ Méthode de paiement supprimée: " + paymentMethodId);
        }
        return deleted;
    }
    
    @Override
    public boolean setDefaultPaymentMethod(String paymentMethodId) {
        Document pmDoc = paymentMethodRepository.findById(paymentMethodId);
        if (pmDoc == null) return false;
        
        String customerId = pmDoc.getString("customerId");
        
        paymentMethodRepository.unsetDefaultPaymentMethods(customerId, paymentMethodId);
        paymentMethodRepository.setPaymentMethodDefault(paymentMethodId, true);
        
        System.out.println("✅ Méthode par défaut définie: " + paymentMethodId);
        return true;
    }
    
    // ==================== DESTINATIONS ====================
    
    @Override
    public Destination[] getPopularDestinations() {
        // Destinations populaires avec données enrichies
        List<Destination> destinations = new ArrayList<>();
        
        // Destinations statiques de base
        Map<String, DestinationData> baseDestinations = new HashMap<String, DestinationData>() {{
            put("Paris", new DestinationData("FR", "/images/paris.jpg", 45000.0));
            put("Istanbul", new DestinationData("TR", "/images/istanbul.jpg", 54000.0));
            put("Tamanrasset", new DestinationData("DZ", "/images/tamanrasset.jpg", 36000.0));
            put("Marseille", new DestinationData("FR", "/images/marseille.jpg", 48000.0));
            put("Madrid", new DestinationData("ES", "/images/madrid.jpg", 52000.0));
            put("Tunis", new DestinationData("TN", "/images/tunis.jpg", 28000.0));
            put("Londres", new DestinationData("GB", "/images/london.jpg", 65000.0));
            put("Rome", new DestinationData("IT", "/images/rome.jpg", 58000.0));
            put("Dubaï", new DestinationData("AE", "/images/dubai.jpg", 95000.0));
            put("Barcelone", new DestinationData("ES", "/images/barcelona.jpg", 55000.0));
        }};
        
        // Compter les vols disponibles pour chaque destination
        for (Map.Entry<String, DestinationData> entry : baseDestinations.entrySet()) {
            String cityName = entry.getKey();
            DestinationData data = entry.getValue();
            
            // Compter les vols vers cette destination
            List<Document> flights = flightRepository.searchFlights(
                null, cityName, null, null
            );
            
            int flightCount = flights.size();
            
            destinations.add(new Destination(
                cityName,
                data.countryCode,
                data.imageUrl,
                flightCount,
                data.startingPrice
            ));
        }
        
        // Trier par nombre de vols décroissant
        destinations.sort((a, b) -> Integer.compare(b.flightCount, a.flightCount));
        
        // Limiter à 6 destinations
        if (destinations.size() > Constants.POPULAR_DESTINATIONS_LIMIT) {
            destinations = destinations.subList(0, Constants.POPULAR_DESTINATIONS_LIMIT);
        }
        
        System.out.println("📍 " + destinations.size() + " destinations populaires");
        return destinations.toArray(new Destination[0]);
    }
    
    @Override
    public Destination[] getFavoriteDestinations(String customerId) {
        System.out.println("→ Récupération favorites pour: " + customerId);
        
        List<Document> favoriteDocs = favoritesRepository.findByCustomerId(customerId);
        List<Destination> destinations = new ArrayList<>();
        
        for (Document favDoc : favoriteDocs) {
            String cityName = favDoc.getString("cityName");
            String countryCode = favDoc.getString("countryCode");
            
            // Rechercher les vols disponibles pour cette destination
            List<Document> flights = flightRepository.searchFlights(
                null, cityName, null, null
            );
            
            int flightCount = flights.size();
            
            // Trouver le prix le plus bas
            double startingPrice = flights.stream()
                .mapToDouble(f -> f.getDouble("economyPrice"))
                .min()
                .orElse(0.0);
            
            String imageUrl = "/images/" + cityName.toLowerCase().replace(" ", "-") + ".jpg";
            
            destinations.add(new Destination(
                cityName,
                countryCode,
                imageUrl,
                flightCount,
                startingPrice
            ));
        }
        
        System.out.println("✅ " + destinations.size() + " destination(s) favorite(s)");
        return destinations.toArray(new Destination[0]);
    }
    
    @Override
    public boolean addFavoriteDestination(String customerId, String cityName) {
        System.out.println("→ Ajout destination favorite: " + cityName + " pour " + customerId);
        
        // Vérifier que le client existe
        Document customerDoc = customerRepository.findById(customerId);
        if (customerDoc == null) {
            System.err.println("❌ Client introuvable: " + customerId);
            return false;
        }
        
        // Vérifier la limite de destinations favorites
        long currentCount = favoritesRepository.countByCustomerId(customerId);
        if (currentCount >= Constants.MAX_FAVORITES_PER_USER) {
            System.err.println("❌ Limite atteinte: " + Constants.MAX_FAVORITES_PER_USER + " favorites max");
            return false;
        }
        
        // Déterminer le code pays (mapping simple)
        String countryCode = determineCountryCode(cityName);
        
        // Ajouter la destination favorite
        boolean added = favoritesRepository.addFavorite(customerId, cityName, countryCode);
        
        if (added) {
            System.out.println("✅ Destination favorite ajoutée: " + cityName);
            logUserActivity(customerId, "Added favorite destination: " + cityName);
        }
        
        return added;
    }
    
    @Override
    public boolean removeFavoriteDestination(String customerId, String cityName) {
        System.out.println("→ Suppression destination favorite: " + cityName + " pour " + customerId);
        
        boolean removed = favoritesRepository.removeFavorite(customerId, cityName);
        
        if (removed) {
            System.out.println("✅ Destination favorite retirée: " + cityName);
            logUserActivity(customerId, "Removed favorite destination: " + cityName);
        }
        
        return removed;
    }
    
    // ==================== NEWSLETTER ====================
    
    @Override
    public boolean subscribeNewsletter(Newsletter newsletter) {
        // À implémenter avec NewsletterRepository
        System.out.println("✅ Newsletter souscrite: " + newsletter.email);
        return true;
    }
    
    @Override
    public boolean unsubscribeNewsletter(String email) {
        // À implémenter avec NewsletterRepository
        System.out.println("✅ Newsletter désabonnée: " + email);
        return true;
    }
    
    // ==================== MÉTHODES PRIVÉES ====================
    
    private boolean canCancelReservation(Document resDoc, Document flightDoc) {
        String status = resDoc.getString("status");
        if (!"CONFIRMED".equals(status)) return false;
        
        long hoursRemaining = DateUtils.calculateHoursRemaining(
            flightDoc.getString("departureDate"),
            flightDoc.getString("departureTime")
        );
        
        return hoursRemaining >= Constants.HOURS_PARTIAL_REFUND;
    }
    
    private boolean canModifyReservation(Document resDoc, Document flightDoc) {
        return canCancelReservation(resDoc, flightDoc);
    }
    
    /**
     * Valider le format d'un email
     */
    private boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        return email.matches(emailRegex);
    }
    
    /**
     * Valider le type de carte
     */
    private boolean isValidCardType(String cardType) {
        List<String> validTypes = Arrays.asList("VISA", "MASTERCARD", "AMEX", "CIB");
        return validTypes.contains(cardType);
    }
    
    /**
     * Valider la date d'expiration (format MM/YY)
     */
    private boolean isValidExpiryDate(String expiryDate) {
        if (expiryDate == null || !expiryDate.matches("\\d{2}/\\d{2}")) {
            return false;
        }
        
        try {
            String[] parts = expiryDate.split("/");
            int month = Integer.parseInt(parts[0]);
            int year = Integer.parseInt(parts[1]) + 2000; // YY -> YYYY
            
            if (month < 1 || month > 12) {
                return false;
            }
            
            // Vérifier que la carte n'est pas expirée
            Calendar now = Calendar.getInstance();
            int currentYear = now.get(Calendar.YEAR);
            int currentMonth = now.get(Calendar.MONTH) + 1;
            
            if (year < currentYear || (year == currentYear && month < currentMonth)) {
                return false;
            }
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Déterminer le code pays basé sur le nom de la ville
     */
    private String determineCountryCode(String cityName) {
        Map<String, String> cityCountryMap = new HashMap<String, String>() {{
            // Algérie
            put("Alger", "DZ"); put("Oran", "DZ"); put("Constantine", "DZ");
            put("Annaba", "DZ"); put("Tamanrasset", "DZ"); put("Tlemcen", "DZ");
            
            // France
            put("Paris", "FR"); put("Marseille", "FR"); put("Lyon", "FR");
            put("Toulouse", "FR"); put("Nice", "FR");
            
            // Autres pays
            put("Istanbul", "TR"); put("Londres", "GB"); put("Madrid", "ES");
            put("Barcelone", "ES"); put("Rome", "IT"); put("Milan", "IT");
            put("Berlin", "DE"); put("Amsterdam", "NL"); put("Bruxelles", "BE");
            put("Tunis", "TN"); put("Casablanca", "MA"); put("Le Caire", "EG");
            put("Dubaï", "AE"); put("Doha", "QA"); put("New York", "US");
        }};
        
        return cityCountryMap.getOrDefault(cityName, "XX");
    }
    
    /**
     * Logger les changements de mot de passe pour audit
     */
    private void logPasswordChange(String customerId, String action) {
        Document auditLog = new Document()
            .append("customerId", customerId)
            .append("action", action)
            .append("timestamp", new Date())
            .append("ipAddress", "N/A") // À implémenter avec le contexte de requête
            .append("userAgent", "N/A");
        
        // À sauvegarder dans une collection d'audit
        System.out.println("📝 Audit: " + action + " pour " + customerId);
    }
    
    /**
     * Logger les événements de sécurité
     */
    private void logSecurityEvent(String customerId, String event, String details) {
        Document securityLog = new Document()
            .append("customerId", customerId)
            .append("event", event)
            .append("details", details)
            .append("timestamp", new Date())
            .append("severity", "INFO");
        
        System.out.println("🔒 Security Event: " + event + " - " + customerId);
    }
    
    /**
     * Logger l'activité utilisateur
     */
    private void logUserActivity(String customerId, String activity) {
        Document activityLog = new Document()
            .append("customerId", customerId)
            .append("activity", activity)
            .append("timestamp", new Date())
            .append("type", "user_action");
        
        System.out.println("📝 Activity: " + activity + " - " + customerId);
    }
    
    /**
     * Classe helper pour les données de destination
     */
    private static class DestinationData {
        final String countryCode;
        final String imageUrl;
        final double startingPrice;
        
        DestinationData(String countryCode, String imageUrl, double startingPrice) {
            this.countryCode = countryCode;
            this.imageUrl = imageUrl;
            this.startingPrice = startingPrice;
        }
    }
}
