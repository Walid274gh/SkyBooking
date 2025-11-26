// src/main/java/com/skybooking/utils/DateUtils.java

package com.skybooking.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * 📅 Utilitaires pour manipulation des dates
 */
public class DateUtils {
    
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat DATETIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm");
    
    /**
     * Formater une date au format yyyy-MM-dd
     */
    public static String formatDate(Date date) {
        return DATE_FORMAT.format(date);
    }
    
    /**
     * Formater une date avec heure au format yyyy-MM-dd HH:mm:ss
     */
    public static String formatDateTime(Date date) {
        return DATETIME_FORMAT.format(date);
    }
    
    /**
     * Parser une date depuis une chaîne yyyy-MM-dd
     */
    public static Date parseDate(String dateStr) throws ParseException {
        return DATE_FORMAT.parse(dateStr);
    }
    
    /**
     * Parser une date avec heure depuis une chaîne yyyy-MM-dd HH:mm:ss
     */
    public static Date parseDateTime(String dateTimeStr) throws ParseException {
        return DATETIME_FORMAT.parse(dateTimeStr);
    }
    
    /**
     * Calculer les heures restantes avant une date
     */
    public static long calculateHoursRemaining(String dateStr, String timeStr) {
        try {
            Date departureDate = DATETIME_FORMAT.parse(dateStr + " " + timeStr);
            return (departureDate.getTime() - System.currentTimeMillis()) / (1000 * 60 * 60);
        } catch (ParseException e) {
            return -1;
        }
    }
    
    /**
     * Vérifier si une date est dans le futur
     */
    public static boolean isFutureDate(String dateStr) {
        try {
            Date date = DATE_FORMAT.parse(dateStr);
            Date today = resetTime(new Date());
            return !date.before(today);
        } catch (ParseException e) {
            return false;
        }
    }
    
    /**
     * Vérifier si une date est passée
     */
    public static boolean isPastDate(String dateStr) {
        return !isFutureDate(dateStr);
    }
    
    /**
     * Réinitialiser l'heure à minuit
     */
    public static Date resetTime(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }
    
    /**
     * Ajouter des jours à une date
     */
    public static Date addDays(Date date, int days) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DAY_OF_MONTH, days);
        return cal.getTime();
    }
    
    /**
     * Obtenir la date actuelle formatée
     */
    public static String getCurrentDate() {
        return formatDate(new Date());
    }
    
    /**
     * Obtenir la date et heure actuelles formatées
     */
    public static String getCurrentDateTime() {
        return formatDateTime(new Date());
    }
    
    // ==================== 🆕 MÉTHODES POUR MODULE HÔTEL ====================
    
    /**
     * 🆕 Calculer le nombre de nuits entre deux dates
     * Utilisé pour les réservations d'hôtels
     */
    public static int calculateNights(String checkInDate, String checkOutDate) {
        try {
            Date checkIn = DATE_FORMAT.parse(checkInDate);
            Date checkOut = DATE_FORMAT.parse(checkOutDate);
            
            long diffInMillis = checkOut.getTime() - checkIn.getTime();
            long diffInDays = diffInMillis / (1000 * 60 * 60 * 24);
            
            return (int) diffInDays;
        } catch (ParseException e) {
            System.err.println("⚠️ Erreur calcul nuits: " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * 🆕 Vérifier si date2 est après date1
     * Utilisé pour valider les dates de réservation d'hôtel
     */
    public static boolean isAfter(String date2, String date1) {
        try {
            Date d1 = DATE_FORMAT.parse(date1);
            Date d2 = DATE_FORMAT.parse(date2);
            return d2.after(d1);
        } catch (ParseException e) {
            System.err.println("⚠️ Erreur comparaison dates: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 🆕 Vérifier si une date est dans une période donnée
     * Utilisé pour vérifier la disponibilité des hôtels
     */
    public static boolean isDateInRange(String date, String startDate, String endDate) {
        try {
            Date d = DATE_FORMAT.parse(date);
            Date start = DATE_FORMAT.parse(startDate);
            Date end = DATE_FORMAT.parse(endDate);
            
            return !d.before(start) && !d.after(end);
        } catch (ParseException e) {
            System.err.println("⚠️ Erreur vérification période: " + e.getMessage());
            return false;
        }
    }
}