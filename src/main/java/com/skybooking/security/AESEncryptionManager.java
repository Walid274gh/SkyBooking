// src/main/java/com/skybooking/security/AESEncryptionManager.java

package com.skybooking.security;

import com.skybooking.utils.Constants;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 🔐 AES-256 Encryption Manager
 * 
 * Implémentation de l'algorithme AES
 * avec clé de 256 bits et mode CBC (Cipher Block Chaining).
 * 
 * UTILISATION :
 * - Données bancaires (numéros de carte, CVV)
 * - Informations d'identité (passeports, CNI)
 * - Données personnelles sensibles (RGPD/PCI-DSS)
 */
public class AESEncryptionManager {

    private static final SecureRandom secureRandom = new SecureRandom();
    
    /**
     * Prépare la clé AES-256 à partir de la clé secrète
     */
    private static SecretKey prepareKey(String secretKey) {
        try {
            byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
            
            // Hash SHA-256 pour obtenir exactement 256 bits
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] hashedKey = sha.digest(keyBytes);
            
            if (hashedKey.length != 32) {
                throw new IllegalStateException("Erreur : clé SHA-256 invalide");
            }
            
            return new SecretKeySpec(hashedKey, Constants.AES_ALGORITHM);
            
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la préparation de la clé AES-256", e);
        }
    }
    
    /**
     * Génère un vecteur d'initialisation aléatoire cryptographiquement sûr
     */
    private static byte[] generateIV() {
        byte[] iv = new byte[Constants.AES_IV_SIZE];
        secureRandom.nextBytes(iv);
        return iv;
    }
    
    /**
     * 🔒 CHIFFRE une donnée sensible avec AES-256-CBC
     */
    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            throw new IllegalArgumentException("Le texte à chiffrer ne peut pas être vide");
        }
        
        try {
            // Récupération de la clé depuis l'environnement
            String secretKeyString = System.getenv(Constants.AES_KEY_ENV);
            if (secretKeyString == null || secretKeyString.length() < 32) {
                throw new IllegalStateException(
                    "La variable d'environnement " + Constants.AES_KEY_ENV + 
                    " doit faire au moins 32 caractères"
                );
            }
            
            SecretKey secretKey = prepareKey(secretKeyString);
            
            // Génération d'un IV unique pour cette opération
            byte[] iv = generateIV();
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            
            // Initialisation du Cipher (configure les 14 rounds AES-256)
            Cipher cipher = Cipher.getInstance(Constants.AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
            
            // Chiffrement (exécution des rounds SubBytes, ShiftRows, MixColumns, AddRoundKey)
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            
            // Concaténation : [IV][Données chiffrées]
            byte[] combined = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);
            
            // Encodage Base64 pour stockage
            return Base64.getEncoder().encodeToString(combined);
            
        } catch (Exception e) {
            System.err.println("❌ ERREUR CRITIQUE lors du chiffrement AES-256 : " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Impossible de chiffrer les données", e);
        }
    }
    
    /**
     * 🔓 DÉCHIFFRE une donnée avec AES-256-CBC
     */
    public static String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            throw new IllegalArgumentException("Le texte chiffré ne peut pas être vide");
        }
        
        try {
            // Récupération de la clé
            String secretKeyString = System.getenv(Constants.AES_KEY_ENV);
            if (secretKeyString == null || secretKeyString.length() < 32) {
                throw new IllegalStateException(
                    "La variable d'environnement " + Constants.AES_KEY_ENV + 
                    " doit faire au moins 32 caractères"
                );
            }
            
            SecretKey secretKey = prepareKey(secretKeyString);
            
            // Décodage Base64
            byte[] combined = Base64.getDecoder().decode(encryptedText);
            
            if (combined.length < Constants.AES_IV_SIZE) {
                throw new IllegalArgumentException("Données chiffrées corrompues (trop courtes)");
            }
            
            // Extraction de l'IV (16 premiers octets)
            byte[] iv = Arrays.copyOfRange(combined, 0, Constants.AES_IV_SIZE);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            
            // Extraction des données chiffrées (octets restants)
            byte[] encryptedBytes = Arrays.copyOfRange(combined, Constants.AES_IV_SIZE, combined.length);
            
            // Initialisation du Cipher pour déchiffrement
            Cipher cipher = Cipher.getInstance(Constants.AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
            
            // Déchiffrement (exécution des rounds inverses)
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            
            return new String(decryptedBytes, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            System.err.println("❌ ERREUR CRITIQUE lors du déchiffrement AES-256 : " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Impossible de déchiffrer les données", e);
        }
    }
    
    /**
     * 🎭 MASQUE une donnée pour affichage
     * Utile pour afficher les 4 derniers chiffres d'une carte ou d'un passeport
     */
    public static String mask(String data, int visibleChars) {
        if (data == null || data.length() <= visibleChars) {
            return "****";
        }
        
        int maskedLength = data.length() - visibleChars;
        StringBuilder masked = new StringBuilder();
        
        for (int i = 0; i < maskedLength; i++) {
            masked.append("*");
        }
        
        masked.append(data.substring(maskedLength));
        return masked.toString();
    }
    
    /**
     * ✅ TESTE la configuration AES-256
     */
    public static boolean testConfiguration() {
        try {
            String testData = "SkyBooking Test AES-256";
            String encrypted = encrypt(testData);
            String decrypted = decrypt(encrypted);
            
            boolean success = testData.equals(decrypted);
            
            if (success) {
                System.out.println("✅ Configuration AES-256 validée");
                System.out.println("   Algorithme : AES-256-CBC");
                System.out.println("   Taille clé : 256 bits");
                System.out.println("   Mode : CBC avec IV aléatoire");
                System.out.println("   Padding : PKCS5");
            } else {
                System.err.println("❌ Échec du test AES-256");
            }
            
            return success;
            
        } catch (Exception e) {
            System.err.println("❌ Erreur lors du test AES-256 : " + e.getMessage());
            return false;
        }
    }
}
