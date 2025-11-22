// src/main/java/com/skybooking/rest/CorbaRestBridge.java

package com.skybooking.rest;

import com.sun.net.httpserver.HttpServer;
import org.omg.CORBA.ORB;
import org.omg.CosNaming.*;
import FlightReservation.*;
import com.skybooking.rest.middleware.*;
import com.skybooking.rest.handlers.auth.*;
import com.skybooking.rest.handlers.flight.*;
import com.skybooking.rest.handlers.reservation.*;
import com.skybooking.rest.handlers.payment.*;
import com.skybooking.rest.handlers.account.*;
import com.skybooking.rest.handlers.download.*;
import com.skybooking.rest.handlers.admin.*;
import com.skybooking.rest.handlers.invoice.*;
import com.skybooking.rest.handlers.cancellation.*;
import com.skybooking.security.RateLimiter;
import com.skybooking.utils.Constants;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.logging.Logger;
import com.skybooking.rest.RestHealthEndpoint;

/**
 * 🌉 Pont REST-CORBA (SÉCURISÉ)
 * Expose les services CORBA via une API REST HTTP
 */
public class CorbaRestBridge {
    
    private static final Logger LOGGER = Logger.getLogger(CorbaRestBridge.class.getName());
    
    private FlightBookingSystem bookingSystem;
    private CustomerManager customerManager;
    private FlightManager flightManager;
    private ReservationManager reservationManager;
    private PaymentManager paymentManager;
    private AccountManager accountManager;
    private CancellationManager cancellationManager;
    private AdminManager adminManager;
    
    private RateLimiter rateLimiter;
    private TimeoutExecutor timeoutExecutor;
    
    public CorbaRestBridge(String[] args) throws Exception {
        System.out.println("→ Initialisation du pont REST-CORBA...");
        
        // Initialiser Rate Limiter avec MongoDB
        this.rateLimiter = new RateLimiter();
        this.timeoutExecutor = new TimeoutExecutor();
        
        System.out.println("✅ Rate Limiter initialisé (MongoDB + Cache)");
        
        try {
            // Initialiser CORBA
            ORB orb = ORB.init(args, null);
            System.out.println("✅ ORB initialisé");
            
            // Obtenir le service de nommage
            org.omg.CORBA.Object objRef = 
                orb.resolve_initial_references(Constants.NAMING_SERVICE);
            NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);
            System.out.println("✅ Service de nommage contacté");
            
            try {
                // Récupérer le système de réservation
                bookingSystem = FlightBookingSystemHelper.narrow(
                    ncRef.resolve_str(Constants.CORBA_SERVICE_NAME));
                System.out.println("✅ Système de réservation trouvé");
            } catch (Exception e) {
                System.err.println("✗ Impossible de se connecter à CORBA!");
                System.err.println("Assurez-vous que:");
                System.err.println("  1. Le service de nommage (orbd) est actif sur le port 1050");
                System.err.println("  2. Le serveur FlightBookingServer est en cours d'exécution");
                System.err.println("  3. Les deux services utilisent le même port ORB");
                throw new Exception("Erreur de connexion CORBA: " + e.getMessage(), e);
            }
            
            // Obtenir les managers
            customerManager = bookingSystem.getCustomerManager();
            flightManager = bookingSystem.getFlightManager();
            reservationManager = bookingSystem.getReservationManager();
            paymentManager = bookingSystem.getPaymentManager();
            accountManager = bookingSystem.getAccountManager();
            cancellationManager = bookingSystem.getCancellationManager();
            adminManager = bookingSystem.getAdminManager();
            System.out.println("✅ Tous les managers récupérés");
            
            System.out.println("✅ Connexion CORBA établie avec succès");
            
        } catch (Exception e) {
            System.err.println("✗ Erreur lors de l'initialisation: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    
    public void startServer(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        
        System.out.println("→ Création des routes REST...");
        
        server.createContext("/api/health", new RestHealthEndpoint());
        
        // ==================== ROUTES AUTHENTIFICATION ====================
        server.createContext("/api/login", 
            new LoginHandler(customerManager, rateLimiter, timeoutExecutor));
        server.createContext("/api/register", 
            new RegisterHandler(customerManager, timeoutExecutor));
        server.createContext("/api/validate-session", 
            new ValidateSessionHandler(customerManager, timeoutExecutor));
        
        // ==================== ROUTES FLIGHTS ====================
        server.createContext("/api/flights/search", 
            new SearchFlightsHandler(flightManager, timeoutExecutor));
        server.createContext("/api/flights/", 
            new FlightDetailHandler(flightManager, timeoutExecutor));
        server.createContext("/api/seats/", 
            new SeatsHandler(flightManager, timeoutExecutor));
        
        // ==================== ROUTES RESERVATIONS ====================
        server.createContext("/api/reservations", 
            new ReservationHandler(reservationManager, timeoutExecutor));
        server.createContext("/api/tickets/", 
            new TicketsHandler(reservationManager, timeoutExecutor));
        
        // ==================== ROUTES PAYMENTS ====================
        server.createContext("/api/payments/process", 
            new ProcessPaymentHandler(paymentManager, timeoutExecutor));
        server.createContext("/api/payments/customer/", 
            new CustomerPaymentsHandler(paymentManager, timeoutExecutor));
        server.createContext("/api/payments/refund/", 
            new RefundPaymentHandler(paymentManager, timeoutExecutor));
        server.createContext("/api/payments/", 
            new GetPaymentHandler(paymentManager, timeoutExecutor));
        
        // ==================== ROUTES INVOICES ====================
        server.createContext("/api/invoices/generate/", 
            new GenerateInvoiceHandler(paymentManager, reservationManager, 
                                      flightManager, customerManager, timeoutExecutor));
        server.createContext("/api/invoices/customer/", 
            new CustomerInvoicesHandler(paymentManager, timeoutExecutor));
        server.createContext("/api/invoices/", 
            new GetInvoiceHandler(paymentManager, timeoutExecutor));
        
        // ==================== ROUTES CANCELLATION ====================
        server.createContext("/api/cancellation/cancel", 
            new CancelReservationHandler(cancellationManager, timeoutExecutor));
        server.createContext("/api/cancellation/modify-seats", 
            new ModifySeatsHandler(cancellationManager, timeoutExecutor));
        
        // ==================== ROUTES ACCOUNT ====================
        server.createContext("/api/account/history", 
            new BookingHistoryHandler(accountManager, timeoutExecutor));
        server.createContext("/api/account/profile", 
            new ProfileHandler(accountManager, timeoutExecutor));
        server.createContext("/api/account/password", 
            new PasswordHandler(accountManager, timeoutExecutor));
        server.createContext("/api/account/newsletter", 
            new NewsletterHandler(accountManager, timeoutExecutor));
        server.createContext("/api/account/favorites", 
            new FavoritesHandler(accountManager, timeoutExecutor));
        server.createContext("/api/account/popular-destinations", 
        new PopularDestinationsHandler(accountManager, timeoutExecutor));
        
        // ==================== ROUTES DOWNLOAD ====================
        // Téléchargement groupé (tous les tickets)
        server.createContext("/api/download/tickets/", 
            new DownloadTicketsHandler(reservationManager, flightManager, 
                                      customerManager, timeoutExecutor));
        
        // 🆕 NOUVELLE ROUTE: Téléchargement individuel (un seul ticket)
        server.createContext("/api/download/ticket/", 
            new DownloadSingleTicketHandler(reservationManager, flightManager, 
                                           customerManager, timeoutExecutor));
        
        // Téléchargement de facture
        server.createContext("/api/download/invoice/", 
            new DownloadInvoiceHandler(paymentManager, reservationManager, 
                                      flightManager, customerManager, timeoutExecutor));
        
        // ==================== ROUTES ADMIN ====================
        server.createContext("/api/admin/login", 
            new AdminLoginHandler(adminManager, timeoutExecutor));
        server.createContext("/api/admin/dashboard/stats", 
            new DashboardHandler(adminManager, timeoutExecutor));
        server.createContext("/api/admin/flights", 
            new FlightManagementHandler(adminManager, timeoutExecutor));
        server.createContext("/api/admin/users", 
            new UserManagementHandler(adminManager, timeoutExecutor));
        server.createContext("/api/admin/analytics/top-routes", 
            new TopRoutesHandler(adminManager, timeoutExecutor));
        server.createContext("/api/admin/analytics/revenue-trend", 
            new RevenueTrendHandler(adminManager, timeoutExecutor));
        
        server.setExecutor(null);
        server.start();
        
        System.out.println("✅ REST API démarrée sur le port " + port);
        System.out.println("✅ Health check: http://localhost:" + port + "/api/health");
        
        // Afficher les statistiques du Rate Limiter
        RateLimiter.RateLimiterStats stats = rateLimiter.getStats();
        
        System.out.println("\n╔═══════════════════════════════════════════════════╗");
        System.out.println("║       ✅ SERVEUR REST-CORBA DÉMARRÉ (SÉCURISÉ)    ║");
        System.out.println("╠═══════════════════════════════════════════════════╣");
        System.out.println("║ Port       : " + String.format("%-38s", port) + "║");
        System.out.println("║ URL        : " + String.format("%-38s", "http://localhost:" + port) + "║");
        System.out.println("╠═══════════════════════════════════════════════════╣");
        System.out.println("║              🔒 SÉCURITÉ ACTIVÉE                   ║");
        System.out.println("╠═══════════════════════════════════════════════════╣");
        System.out.println("║ Rate Limiting    : " + 
            String.format("%-33s", Constants.MAX_LOGIN_ATTEMPTS + " tentatives / 15 min") + "║");
        System.out.println("║ Lockout          : " + 
            String.format("%-33s", "30 minutes") + "║");
        System.out.println("║ Timeout CORBA    : " + 
            String.format("%-33s", "10-20 secondes") + "║");
        System.out.println("║ Stockage         : " + 
            String.format("%-33s", "MongoDB + Cache Local") + "║");
        System.out.println("║ TTL Auto         : " + 
            String.format("%-33s", "Activé (MongoDB)") + "║");
        System.out.println("║ 🔐 Sessions      : " + 
            String.format("%-33s", "Token sécurisé (24h)") + "║");
        System.out.println("╠═══════════════════════════════════════════════════╣");
        System.out.println("║         📊 STATISTIQUES RATE LIMITER              ║");
        System.out.println("╠═══════════════════════════════════════════════════╣");
        System.out.println("║ Tentatives totales  : " + 
            String.format("%-30s", stats.totalAttempts) + "║");
        System.out.println("║ Tentatives récentes : " + 
            String.format("%-30s", stats.recentAttempts) + "║");
        System.out.println("║ Échecs récents      : " + 
            String.format("%-30s", stats.failedAttempts) + "║");
        System.out.println("║ Comptes bloqués     : " + 
            String.format("%-30s", stats.lockedAccounts) + "║");
        System.out.println("╠═══════════════════════════════════════════════════╣");
        System.out.println("║         ✅ ROUTES ENREGISTRÉES (33 total)         ║");
        System.out.println("╠═══════════════════════════════════════════════════╣");
        System.out.println("║ • Authentification     : 3 routes                 ║");
        System.out.println("║ • Vols                 : 3 routes                 ║");
        System.out.println("║ • Réservations         : 2 routes                 ║");
        System.out.println("║ • Paiements            : 4 routes                 ║");
        System.out.println("║ • Factures             : 3 routes                 ║");
        System.out.println("║ • Annulation           : 2 routes                 ║");
        System.out.println("║ • Compte               : 6 routes                 ║");
        System.out.println("║ • Téléchargements      : 3 routes (🆕 +1)         ║");
        System.out.println("║ • Administration       : 6 routes                 ║");
        System.out.println("║ • Health Check         : 1 route                  ║");
        System.out.println("╠═══════════════════════════════════════════════════╣");
        System.out.println("║         ✅ AUTRES PROTECTIONS                      ║");
        System.out.println("╠═══════════════════════════════════════════════════╣");
        System.out.println("║ • Validation stricte des entrées                   ║");
        System.out.println("║ • CORS configuré                                   ║");
        System.out.println("║ • Sessions persistantes (MongoDB)                  ║");
        System.out.println("║ • Hachage BCrypt avec pepper                       ║");
        System.out.println("║ • Reset tokens sécurisés (TTL 30 min)              ║");
        System.out.println("║ • 🔐 Validation token à chaque requête             ║");
        System.out.println("║ • 🎫 Téléchargement individuel de tickets          ║");
        System.out.println("╚═══════════════════════════════════════════════════╝");
        System.out.println("\n🚀 Serveur prêt à recevoir des requêtes!");
        System.out.println("🔒 Toutes les sessions client sont maintenant sécurisées");
        System.out.println("✅ Toutes les routes frontend sont maintenant disponibles");
        System.out.println("🆕 Nouveau: Téléchargement individuel de tickets activé");
        System.out.println("🛡️ Appuyez sur Ctrl+C pour arrêter le serveur\n");
    }
    
    public void shutdown() {
        if (timeoutExecutor != null) {
            timeoutExecutor.shutdown();
        }
        
        // Afficher les statistiques finales
        if (rateLimiter != null) {
            System.out.println("\n→ Statistiques finales du Rate Limiter:");
            RateLimiter.RateLimiterStats stats = rateLimiter.getStats();
            System.out.println("  " + stats.toString());
        }
        
        System.out.println("✅ Pont REST-CORBA arrêté proprement");
    }
    
    // ==================== MAIN ====================
    
    public static void main(String[] args) {
        try {
            System.out.println("╔═══════════════════════════════════════════════════╗");
            System.out.println("║       DÉMARRAGE DU PONT REST-CORBA (SÉCURISÉ)    ║");
            System.out.println("╚═══════════════════════════════════════════════════╝\n");
            
            CorbaRestBridge bridge = new CorbaRestBridge(args);
            
            // Shutdown Hook pour nettoyage propre
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n→ Arrêt du pont REST-CORBA...");
                bridge.shutdown();
            }));
            
            bridge.startServer(Constants.REST_PORT);
            
        } catch (Exception e) {
            System.err.println("✗ ERREUR FATALE: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}