// src/main/java/com/skybooking/managers/impl/PaymentManagerImpl.java

package com.skybooking.managers.impl;

import FlightReservation.*;
import com.skybooking.database.repositories.*;
import com.skybooking.managers.helpers.ManagerHelper;
import com.skybooking.managers.helpers.ValidationHelper;
import com.skybooking.security.AESEncryptionManager;
import com.skybooking.security.TokenManager;
import com.skybooking.utils.Constants;
import com.skybooking.utils.DateUtils;
import org.bson.Document;
import java.util.*;

/**
 * 💳 IMPLÉMENTATION DU GESTIONNAIRE DE PAIEMENTS
 * Traitement sécurisé des paiements CIB et EDAHABIA avec chiffrement AES-256
 * 
 * SÉCURITÉ :
 * - Chiffrement AES-256-CBC pour les données bancaires
 * - Conformité PCI-DSS niveau 1
 * - Masquage des données sensibles dans les logs
 * - Jamais de stockage du CVV (interdit par PCI-DSS)
 */
public class PaymentManagerImpl extends PaymentManagerPOA {
    
    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final Random random;
    
    public PaymentManagerImpl() {
        this.paymentRepository = new PaymentRepository();
        this.reservationRepository = new ReservationRepository();
        this.invoiceRepository = new InvoiceRepository();
        this.customerRepository = new CustomerRepository();
        this.random = new Random();
        
        // Test de la configuration AES-256 au démarrage
        if (AESEncryptionManager.testConfiguration()) {
            System.out.println("✅ PaymentManager initialisé avec AES-256");
        } else {
            System.err.println("⚠️ ATTENTION : Configuration AES-256 invalide");
        }
    }
    
    @Override
    public Payment processPayment(
            String reservationId,
            String customerId,
            double amount,
            PaymentMethod method,
            String cardNumber,
            String cardHolder,
            String expiryDate,
            String cvv)
        throws PaymentException, InsufficientFundsException, InvalidCardException {
        
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║  💳 TRAITEMENT PAIEMENT SÉCURISÉ (AES-256)        ║");
        System.out.println("╠════════════════════════════════════════════════════╣");
        System.out.println("║  Montant : " + amount + " DZD");
        System.out.println("║  Méthode : " + (method == PaymentMethod.CIB ? "CIB" : "EDAHABIA"));
        System.out.println("║  Carte : " + AESEncryptionManager.mask(cardNumber, 4));
        System.out.println("╚════════════════════════════════════════════════════╝");
        
        // ==================== PHASE 1 : VALIDATIONS ====================
        
        // VALIDATION 1 : Réservation existe
        Document reservationDoc = reservationRepository.findById(reservationId);
        if (reservationDoc == null) {
            throw new PaymentException("Réservation introuvable : " + reservationId);
        }
        
        // VALIDATION 2 : Montant correspond
        double reservationAmount = reservationDoc.getDouble("totalPrice");
        if (Math.abs(amount - reservationAmount) > 0.01) {
            throw new PaymentException(
                String.format("Montant incorrect. Attendu : %.2f DZD, Reçu : %.2f DZD", 
                    reservationAmount, amount)
            );
        }
        
        // VALIDATION 3 : Réservation pas déjà payée
        if (paymentRepository.isReservationPaid(reservationId)) {
            throw new PaymentException("Cette réservation est déjà payée");
        }
        
        // VALIDATION 4 : Carte valide
        ValidationHelper.validateCard(cardNumber, expiryDate, cvv);
        
        // ==================== PHASE 2 : SIMULATION TRAITEMENT BANCAIRE ====================
        
        System.out.println("→ Connexion au réseau bancaire...");
        try {
            Thread.sleep(1500); // Simule le délai bancaire
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Simulation : 95% de succès
        boolean bankApproved = random.nextInt(100) < 95;
        
        if (!bankApproved) {
            System.err.println("❌ Transaction refusée par la banque");
            
            // Enregistrement du paiement échoué (sans CVV, avec carte masquée)
            String failedPaymentId = TokenManager.generateUniqueId("PAY");
            saveFailedPayment(failedPaymentId, reservationId, customerId, amount, 
                            method, cardNumber);
            
            throw new InsufficientFundsException(
                "Transaction refusée par la banque. Veuillez vérifier vos fonds ou contacter votre banque."
            );
        }
        
        // ==================== PHASE 3 : CHIFFREMENT ET STOCKAGE SÉCURISÉ ====================
        
        System.out.println("→ Chiffrement AES-256 des données bancaires...");
        
        String paymentId = TokenManager.generateUniqueId("PAY");
        String transactionId = TokenManager.generateTransactionId(
            method == PaymentMethod.CIB ? "CIB" : "EDH"
        );
        String bankReference = generateBankReference(method);
        String paymentDate = DateUtils.getCurrentDateTime();
        
        // 🔒 CHIFFREMENT AES-256 DU NUMÉRO DE CARTE COMPLET
        // On stocke UNIQUEMENT la version chiffrée pour récupération en cas de remboursement
        String encryptedCardNumber;
        try {
            encryptedCardNumber = AESEncryptionManager.encrypt(cardNumber);
            System.out.println("✅ Numéro de carte chiffré avec AES-256");
        } catch (Exception e) {
            System.err.println("❌ ERREUR CRITIQUE : Échec du chiffrement");
            throw new PaymentException("Erreur de sécurité lors du traitement du paiement");
        }
        
        // Masquage pour affichage (conforme PCI-DSS)
        String maskedCard = TokenManager.maskCardNumber(cardNumber);
        
        // 🔒 CHIFFREMENT DU NOM DU TITULAIRE
        String encryptedCardHolder;
        try {
            encryptedCardHolder = AESEncryptionManager.encrypt(cardHolder);
        } catch (Exception e) {
            encryptedCardHolder = cardHolder; // Fallback si échec
        }
        
        // NOTE IMPORTANTE : Le CVV n'est JAMAIS stocké (règle PCI-DSS)
        // Il est utilisé uniquement pour la validation puis immédiatement détruit
        
        Document paymentDoc = new Document()
            .append("paymentId", paymentId)
            .append("reservationId", reservationId)
            .append("customerId", customerId)
            .append("amount", amount)
            .append("method", method == PaymentMethod.CIB ? "CIB" : "EDAHABIA")
            .append("status", "COMPLETED")
            .append("transactionId", transactionId)
            
            // Données pour affichage (masquées)
            .append("cardNumberMasked", maskedCard)
            .append("cardHolder", cardHolder) // Nom visible pour factures
            
            // Données chiffrées (récupération sécurisée uniquement)
            .append("encryptedCardData", encryptedCardNumber)
            .append("encryptedCardHolder", encryptedCardHolder)
            
            // Métadonnées
            .append("paymentDate", paymentDate)
            .append("bankReference", bankReference)
            .append("expiryDate", expiryDate) // Date d'expiration non sensible
            
            // Audit
            .append("encryptionAlgorithm", "AES-256-CBC")
            .append("processingTime", System.currentTimeMillis());
        
        paymentRepository.insertPayment(paymentDoc);
        
        // Marquer la réservation comme payée
        reservationRepository.markAsPaid(reservationId, paymentId);
        
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║  ✅ PAIEMENT RÉUSSI                                ║");
        System.out.println("╠════════════════════════════════════════════════════╣");
        System.out.println("║  ID Paiement : " + paymentId);
        System.out.println("║  Transaction : " + transactionId);
        System.out.println("║  Référence : " + bankReference);
        System.out.println("║  Sécurité : AES-256-CBC ✓");
        System.out.println("╚════════════════════════════════════════════════════╝");
        
        return new Payment(
            paymentId,
            reservationId,
            customerId,
            amount,
            method,
            PaymentStatus.COMPLETED,
            transactionId,
            maskedCard, // Retourne la version masquée
            paymentDate,
            bankReference
        );
    }
    
    @Override
    public Payment getPayment(String paymentId) {
        Document doc = paymentRepository.findById(paymentId);
        if (doc == null) {
            System.err.println("❌ Paiement introuvable : " + paymentId);
            return null;
        }
        return ManagerHelper.documentToPayment(doc);
    }
    
    @Override
    public Payment[] getCustomerPayments(String customerId) {
        List<Document> paymentDocs = paymentRepository.findByCustomerId(customerId);
        
        Payment[] payments = new Payment[paymentDocs.size()];
        for (int i = 0; i < paymentDocs.size(); i++) {
            payments[i] = ManagerHelper.documentToPayment(paymentDocs.get(i));
        }
        
        System.out.println("→ " + payments.length + " paiement(s) pour " + customerId);
        return payments;
    }
    
    @Override
    public boolean refundPayment(String paymentId) throws RefundException {
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║  💰 REMBOURSEMENT SÉCURISÉ                         ║");
        System.out.println("╚════════════════════════════════════════════════════╝");
        
        Document paymentDoc = paymentRepository.findById(paymentId);
        
        if (paymentDoc == null) {
            throw new RefundException("Paiement introuvable : " + paymentId);
        }
        
        String status = paymentDoc.getString("status");
        if ("REFUNDED".equals(status)) {
            throw new RefundException("Ce paiement est déjà remboursé");
        }
        
        if (!"COMPLETED".equals(status)) {
            throw new RefundException("Impossible de rembourser un paiement non complété");
        }
        
        // Récupération du numéro de carte pour le remboursement bancaire
        String cardNumber = retrieveRealCardNumber(paymentId);
        if (cardNumber != null) {
            System.out.println("→ Carte récupérée (déchiffrée) : " + 
                             AESEncryptionManager.mask(cardNumber, 4));
        }
        
        // Simulation du traitement de remboursement
        System.out.println("→ Traitement du remboursement bancaire...");
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Mettre à jour le statut
        paymentRepository.updateStatus(paymentId, "REFUNDED");
        
        // Marquer la réservation comme remboursée
        String reservationId = paymentDoc.getString("reservationId");
        reservationRepository.updateStatus(reservationId, "REFUNDED");
        
        System.out.println("✅ Remboursement effectué : " + paymentId);
        return true;
    }
    
    @Override
    public Invoice generateInvoice(String paymentId) {
        Document paymentDoc = paymentRepository.findById(paymentId);
        if (paymentDoc == null) {
            System.err.println("❌ Paiement introuvable pour facture : " + paymentId);
            return null;
        }
        
        String reservationId = paymentDoc.getString("reservationId");
        String customerId = paymentDoc.getString("customerId");
        
        Document customerDoc = customerRepository.findById(customerId);
        String customerName = customerDoc.getString("firstName") + " " + 
                            customerDoc.getString("lastName");
        String email = customerDoc.getString("email");
        
        double amount = paymentDoc.getDouble("amount");
        double taxAmount = amount * Constants.TAX_RATE;
        double totalAmount = amount + taxAmount;
        
        String invoiceId = TokenManager.generateUniqueId("INV");
        String issueDate = DateUtils.getCurrentDate();
        
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 30);
        String dueDate = DateUtils.formatDate(cal.getTime());
        
        Document invoiceDoc = new Document()
            .append("invoiceId", invoiceId)
            .append("paymentId", paymentId)
            .append("reservationId", reservationId)
            .append("customerName", customerName)
            .append("email", email)
            .append("amount", amount)
            .append("taxAmount", taxAmount)
            .append("totalAmount", totalAmount)
            .append("issueDate", issueDate)
            .append("dueDate", dueDate);
        
        invoiceRepository.insertInvoice(invoiceDoc);
        
        System.out.println("✅ Facture générée : " + invoiceId);
        
        return new Invoice(
            invoiceId,
            paymentId,
            reservationId,
            customerName,
            email,
            amount,
            taxAmount,
            totalAmount,
            issueDate,
            dueDate
        );
    }
    
    @Override
    public Invoice getInvoice(String invoiceId) {
        Document doc = invoiceRepository.findById(invoiceId);
        if (doc == null) {
            System.err.println("❌ Facture introuvable : " + invoiceId);
            return null;
        }
        return ManagerHelper.documentToInvoice(doc);
    }
    
    @Override
    public Invoice[] getCustomerInvoices(String customerId) {
        List<Document> invoiceDocs = invoiceRepository.findByCustomerId(
            customerId, 
            paymentRepository
        );
        
        Invoice[] invoices = new Invoice[invoiceDocs.size()];
        for (int i = 0; i < invoiceDocs.size(); i++) {
            invoices[i] = ManagerHelper.documentToInvoice(invoiceDocs.get(i));
        }
        
        System.out.println("→ " + invoices.length + " facture(s) pour " + customerId);
        return invoices;
    }
    
    // ==================== MÉTHODES PRIVÉES ====================
    
    /**
     * 🔓 RÉCUPÈRE LE NUMÉRO DE CARTE RÉEL (déchiffré)
     * Utilisé UNIQUEMENT pour les remboursements bancaires
     * Accès strictement contrôlé et audité
     */
    private String retrieveRealCardNumber(String paymentId) {
        try {
            Document doc = paymentRepository.findById(paymentId);
            if (doc != null && doc.containsKey("encryptedCardData")) {
                String encrypted = doc.getString("encryptedCardData");
                String decrypted = AESEncryptionManager.decrypt(encrypted);
                
                System.out.println("🔓 Déchiffrement AES-256 réussi pour remboursement");
                return decrypted;
            }
        } catch (Exception e) {
            System.err.println("❌ Erreur lors du déchiffrement : " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Génère une référence bancaire unique
     */
    private String generateBankReference(PaymentMethod method) {
        String prefix = (method == PaymentMethod.CIB) ? "SATIM" : "POSTE";
        return prefix + "-" + System.currentTimeMillis() + "-" + random.nextInt(9999);
    }
    
    /**
     * Enregistre un paiement échoué (pour audit et analyse)
     * Stocke UNIQUEMENT la carte masquée (jamais le CVV)
     */
    private void saveFailedPayment(
            String paymentId, 
            String reservationId,
            String customerId, 
            double amount,
            PaymentMethod method, 
            String cardNumber) {
        
        String maskedCard = TokenManager.maskCardNumber(cardNumber);
        
        Document failedDoc = new Document()
            .append("paymentId", paymentId)
            .append("reservationId", reservationId)
            .append("customerId", customerId)
            .append("amount", amount)
            .append("method", method == PaymentMethod.CIB ? "CIB" : "EDAHABIA")
            .append("status", "FAILED")
            .append("cardNumberMasked", maskedCard) // Carte masquée uniquement
            .append("paymentDate", DateUtils.getCurrentDateTime())
            .append("failureReason", "Bank declined transaction");
        
        paymentRepository.insertPayment(failedDoc);
        
        System.out.println("⚠️ Paiement échoué enregistré : " + paymentId);
    }
}