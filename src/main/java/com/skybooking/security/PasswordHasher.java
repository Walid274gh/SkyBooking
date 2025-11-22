// src/main/java/com/skybooking/security/PasswordHasher.java

package com.skybooking.security;

import org.mindrot.jbcrypt.BCrypt;
import java.util.regex.Pattern;

/**
 * 🔐 Gestionnaire de hachage des mots de passe avec BCrypt
 * Améliorations :
 * - Ajout d'un pepper pour sécurité supplémentaire
 * - Validation de la force des mots de passe
 * - Détection des mots de passe compromis courants
 * - Méthodes de vérification avancées
 */
public class PasswordHasher {
    
    private static final int BCRYPT_ROUNDS = 12;
    
    // Pepper secret stocké dans les variables d'environnement
    private static final String PEPPER = System.getenv("PASSWORD_PEPPER") != null 
        ? System.getenv("PASSWORD_PEPPER") 
        : "DEFAULT_PEPPER_CHANGE_IN_PRODUCTION";
    
    // Patterns pour validation de force
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile(".*[A-Z].*");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile(".*[a-z].*");
    private static final Pattern DIGIT_PATTERN = Pattern.compile(".*\\d.*");
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*");
    
    // Liste des mots de passe les plus compromis (top 20)
    private static final String[] COMMON_PASSWORDS = {
        "password", "123456", "123456789", "12345678", "12345", "1234567",
        "password1", "123123", "1234567890", "qwerty", "abc123", "111111",
        "iloveyou", "admin", "welcome", "monkey", "dragon", "master",
        "sunshine", "princess"
    };
    
    /**
     * Énumération pour la force du mot de passe
     */
    public enum PasswordStrength {
        VERY_WEAK(0, "Très faible"),
        WEAK(1, "Faible"),
        MEDIUM(2, "Moyen"),
        STRONG(3, "Fort"),
        VERY_STRONG(4, "Très fort");
        
        private final int score;
        private final String label;
        
        PasswordStrength(int score, String label) {
            this.score = score;
            this.label = label;
        }
        
        public int getScore() {
            return score;
        }
        
        public String getLabel() {
            return label;
        }
    }
    
    /**
     * Hacher un mot de passe avec pepper
     */
    public static String hash(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Le mot de passe ne peut pas être vide");
        }
        
        String pepperedPassword = password + PEPPER;
        return BCrypt.hashpw(pepperedPassword, BCrypt.gensalt(BCRYPT_ROUNDS));
    }
    
    /**
     * Vérifier un mot de passe avec pepper
     */
    public static boolean verify(String password, String hashedPassword) {
        try {
            if (password == null || hashedPassword == null) {
                return false;
            }
            
            String pepperedPassword = password + PEPPER;
            return BCrypt.checkpw(pepperedPassword, hashedPassword);
        } catch (Exception e) {
            System.err.println("❌ Erreur vérification mot de passe: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Vérifier si un hash est valide
     */
    public static boolean isValidHash(String hash) {
        try {
            return hash != null && hash.startsWith("$2a$") && hash.length() == 60;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Calculer la force d'un mot de passe
     */
    public static PasswordStrength checkStrength(String password) {
        if (password == null || password.isEmpty()) {
            return PasswordStrength.VERY_WEAK;
        }
        
        int score = 0;
        
        // Critère 1: Longueur
        if (password.length() >= 8) score++;
        if (password.length() >= 12) score++;
        if (password.length() >= 16) score++;
        
        // Critère 2: Complexité
        if (UPPERCASE_PATTERN.matcher(password).matches()) score++;
        if (LOWERCASE_PATTERN.matcher(password).matches()) score++;
        if (DIGIT_PATTERN.matcher(password).matches()) score++;
        if (SPECIAL_CHAR_PATTERN.matcher(password).matches()) score++;
        
        // Pénalité: Mots de passe courants
        if (isCommonPassword(password)) {
            score = Math.max(0, score - 3);
        }
        
        // Pénalité: Séquences répétitives
        if (hasRepetitiveSequence(password)) {
            score = Math.max(0, score - 1);
        }
        
        // Conversion du score en force
        if (score <= 2) return PasswordStrength.VERY_WEAK;
        if (score <= 4) return PasswordStrength.WEAK;
        if (score <= 6) return PasswordStrength.MEDIUM;
        if (score <= 8) return PasswordStrength.STRONG;
        return PasswordStrength.VERY_STRONG;
    }
    
    /**
     * Valider un mot de passe selon les critères de sécurité
     */
    public static PasswordValidationResult validatePassword(String password) {
        PasswordValidationResult result = new PasswordValidationResult();
        
        if (password == null || password.isEmpty()) {
            result.isValid = false;
            result.errors.add("Le mot de passe ne peut pas être vide");
            return result;
        }
        
        // Longueur minimale
        if (password.length() < 8) {
            result.isValid = false;
            result.errors.add("Le mot de passe doit contenir au moins 8 caractères");
        }
        
        // Au moins une majuscule
        if (!UPPERCASE_PATTERN.matcher(password).matches()) {
            result.warnings.add("Ajoutez au moins une lettre majuscule pour renforcer la sécurité");
        }
        
        // Au moins une minuscule
        if (!LOWERCASE_PATTERN.matcher(password).matches()) {
            result.warnings.add("Ajoutez au moins une lettre minuscule pour renforcer la sécurité");
        }
        
        // Au moins un chiffre
        if (!DIGIT_PATTERN.matcher(password).matches()) {
            result.warnings.add("Ajoutez au moins un chiffre pour renforcer la sécurité");
        }
        
        // Au moins un caractère spécial
        if (!SPECIAL_CHAR_PATTERN.matcher(password).matches()) {
            result.warnings.add("Ajoutez au moins un caractère spécial (!@#$%^&*...) pour renforcer la sécurité");
        }
        
        // Vérification des mots de passe courants
        if (isCommonPassword(password)) {
            result.isValid = false;
            result.errors.add("Ce mot de passe est trop courant et facilement devinable");
        }
        
        // Vérification des séquences répétitives
        if (hasRepetitiveSequence(password)) {
            result.warnings.add("Évitez les séquences répétitives (ex: aaa, 111)");
        }
        
        // Calcul de la force
        result.strength = checkStrength(password);
        
        // Si très faible, refuser
        if (result.strength == PasswordStrength.VERY_WEAK) {
            result.isValid = false;
            result.errors.add("Le mot de passe est trop faible");
        }
        
        return result;
    }
    
    /**
     * Vérifier si le mot de passe est dans la liste des mots courants
     */
    private static boolean isCommonPassword(String password) {
        String lowerPassword = password.toLowerCase();
        for (String common : COMMON_PASSWORDS) {
            if (lowerPassword.equals(common)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Détecter les séquences répétitives (aaa, 111, etc.)
     */
    private static boolean hasRepetitiveSequence(String password) {
        if (password.length() < 3) {
            return false;
        }
        
        for (int i = 0; i < password.length() - 2; i++) {
            if (password.charAt(i) == password.charAt(i + 1) && 
                password.charAt(i) == password.charAt(i + 2)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Générer un mot de passe aléatoire fort
     */
    public static String generateStrongPassword(int length) {
        if (length < 12) {
            length = 12; // Minimum de sécurité
        }
        
        String uppercase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lowercase = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String special = "!@#$%^&*()_+-=[]{}|;:,.<>?";
        String all = uppercase + lowercase + digits + special;
        
        StringBuilder password = new StringBuilder();
        java.security.SecureRandom random = new java.security.SecureRandom();
        
        // Garantir au moins un caractère de chaque type
        password.append(uppercase.charAt(random.nextInt(uppercase.length())));
        password.append(lowercase.charAt(random.nextInt(lowercase.length())));
        password.append(digits.charAt(random.nextInt(digits.length())));
        password.append(special.charAt(random.nextInt(special.length())));
        
        // Compléter avec des caractères aléatoires
        for (int i = 4; i < length; i++) {
            password.append(all.charAt(random.nextInt(all.length())));
        }
        
        // Mélanger les caractères
        char[] chars = password.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = chars[i];
            chars[i] = chars[j];
            chars[j] = temp;
        }
        
        return new String(chars);
    }
    
    /**
     * Classe pour le résultat de validation
     */
    public static class PasswordValidationResult {
        public boolean isValid = true;
        public PasswordStrength strength;
        public java.util.List<String> errors = new java.util.ArrayList<>();
        public java.util.List<String> warnings = new java.util.ArrayList<>();
        
        public String getMessage() {
            StringBuilder sb = new StringBuilder();
            
            if (!errors.isEmpty()) {
                sb.append("Erreurs:\n");
                for (String error : errors) {
                    sb.append("  ❌ ").append(error).append("\n");
                }
            }
            
            if (!warnings.isEmpty()) {
                sb.append("Avertissements:\n");
                for (String warning : warnings) {
                    sb.append("  ⚠️ ").append(warning).append("\n");
                }
            }
            
            if (strength != null) {
                sb.append("Force: ").append(strength.getLabel());
            }
            
            return sb.toString();
        }
    }
}
