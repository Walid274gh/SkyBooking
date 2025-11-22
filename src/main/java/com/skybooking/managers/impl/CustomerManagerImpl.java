// src/main/java/com/skybooking/managers/impl/CustomerManagerImpl.java

package com.skybooking.managers.impl;

import FlightReservation.*;
import com.skybooking.database.repositories.CustomerRepository;
import com.skybooking.managers.helpers.ManagerHelper;
import com.skybooking.security.PasswordHasher;
import com.skybooking.security.SessionManager;
import com.skybooking.security.TokenManager;
import org.bson.Document;

/**
 * 👤 IMPLÉMENTATION SÉCURISÉE DU GESTIONNAIRE DE CLIENTS
 * Gestion de l'authentification avec sessions persistantes
 */
public class CustomerManagerImpl extends CustomerManagerPOA {
    
    private final CustomerRepository customerRepository;
    private final SessionManager sessionManager;
    
    public CustomerManagerImpl() {
        this.customerRepository = new CustomerRepository();
        this.sessionManager = new SessionManager();
        System.out.println("✅ CustomerManager initialisé avec MongoDB + BCrypt + SessionManager unifié");
    }
    
    @Override
    public LoginResponse login(String username, String password) 
            throws InvalidCredentialsException {
        
        Document customerDoc = customerRepository.findByUsername(username);
        
        if (customerDoc == null) {
            throw new InvalidCredentialsException(
                "Nom d'utilisateur ou mot de passe incorrect"
            );
        }
        
        // Vérification du mot de passe avec BCrypt
        String storedHash = customerDoc.getString("password");
        if (!PasswordHasher.verify(password, storedHash)) {
            throw new InvalidCredentialsException(
                "Nom d'utilisateur ou mot de passe incorrect"
            );
        }
        
        String customerId = customerDoc.getString("customerId");
        
        // 🔐 CRÉER UNE SESSION SÉCURISÉE
        SessionManager.Session session = sessionManager.createSession(customerId, "CUSTOMER");
        
        System.out.println("✅ Client connecté avec session (MongoDB) : " + username);
        System.out.println("   🔑 Token: " + session.token.substring(0, 16) + "...");
        
        // ✅ Créer Customer sans token
        Customer customer = new Customer(
            customerId,
            customerDoc.getString("username"),
            customerDoc.getString("firstName"),
            customerDoc.getString("lastName"),
            customerDoc.getString("email"),
            customerDoc.getString("phoneNumber")
        );
        
        // ✅ Retourner LoginResponse avec customer et token séparés
        return new LoginResponse(customer, session.token);
    }
    
    @Override
    public LoginResponse registerCustomer(
            String username,
            String password,
            String firstName,
            String lastName,
            String email,
            String phoneNumber)
            throws CustomerAlreadyExistsException {
        
        if (customerRepository.usernameExists(username)) {
            throw new CustomerAlreadyExistsException(
                "Ce nom d'utilisateur existe déjà"
            );
        }
        
        String customerId = TokenManager.generateUniqueId("CUST");
        String hashedPassword = PasswordHasher.hash(password);
        
        Document customerDoc = new Document()
            .append("customerId", customerId)
            .append("username", username)
            .append("password", hashedPassword)
            .append("firstName", firstName)
            .append("lastName", lastName)
            .append("email", email)
            .append("phoneNumber", phoneNumber);
        
        customerRepository.insertCustomer(customerDoc);
        
        // 🔐 CRÉER UNE SESSION AUTOMATIQUEMENT APRÈS INSCRIPTION
        SessionManager.Session session = sessionManager.createSession(customerId, "CUSTOMER");
        
        System.out.println("✅ Nouveau client enregistré (MongoDB + BCrypt + Session) : " + username);
        System.out.println("   🔑 Token: " + session.token.substring(0, 16) + "...");
        
        // ✅ Créer Customer sans token
        Customer customer = new Customer(
            customerId,
            username,
            firstName,
            lastName,
            email,
            phoneNumber
        );
        
        // ✅ Retourner LoginResponse avec customer et token séparés
        return new LoginResponse(customer, session.token);
    }
    
    /**
     * 🔐 VALIDATION DE SESSION SÉCURISÉE
     */
    @Override
    public Customer validateSession(String customerId, String sessionToken) 
            throws InvalidCredentialsException {
        
        // Vérifier que la session existe et est valide
        if (!sessionManager.validateSession(customerId, sessionToken)) {
            throw new InvalidCredentialsException("Session invalide ou expirée");
        }
        
        // Récupérer les données du client
        Document customerDoc = customerRepository.findById(customerId);
        if (customerDoc == null) {
            throw new InvalidCredentialsException("Client non trouvé");
        }
        
        System.out.println("✅ Session validée pour: " + customerDoc.getString("username"));
        
        // ✅ Retourner Customer sans token
        return new Customer(
            customerId,
            customerDoc.getString("username"),
            customerDoc.getString("firstName"),
            customerDoc.getString("lastName"),
            customerDoc.getString("email"),
            customerDoc.getString("phoneNumber")
        );
    }
    
    @Override
    public Customer getCustomerById(String customerId) {
        Document doc = customerRepository.findById(customerId);
        if (doc == null) return null;
        
        return new Customer(
            customerId,
            doc.getString("username"),
            doc.getString("firstName"),
            doc.getString("lastName"),
            doc.getString("email"),
            doc.getString("phoneNumber")
        );
    }
    
    @Override
    public boolean updateCustomer(Customer customer) {
        Document updates = new Document()
            .append("firstName", customer.firstName)
            .append("lastName", customer.lastName)
            .append("email", customer.email)
            .append("phoneNumber", customer.phoneNumber);
        
        customerRepository.updateCustomer(customer.customerId, updates);
        System.out.println("✅ Client mis à jour (MongoDB) : " + customer.username);
        return true;
    }
    
    @Override
    public void logout(String customerId) {
        // 🔐 SUPPRIMER LA SESSION
        sessionManager.destroySession(customerId);
        System.out.println("✅ Client déconnecté et session supprimée : " + customerId);
    }
}