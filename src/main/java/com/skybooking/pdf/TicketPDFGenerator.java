// src/main/java/com/skybooking/pdf/TicketPDFGenerator.java

package com.skybooking.pdf;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import FlightReservation.*;
import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * ✈️ Générateur professionnel de tickets PDF avec QR Code et Barcode
 * 
 * Fonctionnalités:
 * - Design moderne et élégant
 * - QR Code pour vérification rapide
 * - Code-barres pour scanners
 * - Logo compagnie aérienne
 * - Informations complètes du vol et passager
 * - Multi-tickets (pour familles)
 * - Ticket individuel optimisé
 * - Watermark de sécurité
 */
public class TicketPDFGenerator {
    
    // Couleurs corporate
    private static final DeviceRgb PRIMARY_COLOR = new DeviceRgb(37, 99, 235); // Bleu
    private static final DeviceRgb SECONDARY_COLOR = new DeviceRgb(249, 250, 251); // Gris clair
    private static final DeviceRgb ACCENT_COLOR = new DeviceRgb(16, 185, 129); // Vert
    private static final DeviceRgb TEXT_DARK = new DeviceRgb(17, 24, 39);
    private static final DeviceRgb TEXT_LIGHT = new DeviceRgb(107, 114, 128);
    
    private static final String TICKETS_DIR = "tickets";
    private static final String TEMP_IMAGES_DIR = "temp_images";
    
    /**
     * 🎫 NOUVELLE MÉTHODE: Générer un PDF pour UN SEUL ticket
     * Optimisé pour téléchargement individuel
     */
    public static byte[] generateSingleTicket(
        Reservation reservation,
        Ticket ticket,
        Flight flight,
        Customer customer) throws Exception {
    
    System.out.println("╔═══════════════════════════════════════════════╗");
    System.out.println("║  📄 GÉNÉRATION PDF TICKET INDIVIDUEL         ║");
    System.out.println("╠═══════════════════════════════════════════════╣");
    System.out.println("║  Ticket: " + ticket.ticketId);
    System.out.println("║  Passager: " + ticket.passengerName);
    System.out.println("║  Siège: " + ticket.seatNumber);
    System.out.println("╚═══════════════════════════════════════════════╝");
    
    // ✅ CORRECTION: Utiliser ByteArrayOutputStream au lieu du disque
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    
    try {
        // Créer le PDF directement en mémoire
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf, PageSize.A4);
        
        // Marges
        document.setMargins(20, 20, 20, 20);
        
        // Police
        PdfFont boldFont = PdfFontFactory.createFont(
            com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD);
        PdfFont regularFont = PdfFontFactory.createFont(
            com.itextpdf.io.font.constants.StandardFonts.HELVETICA);
        
        // ========== PAGE UNIQUE DU TICKET ==========
        addSingleTicketPage(document, ticket, flight, reservation, customer, 
                           boldFont, regularFont);
        
        // Fermer le document (force l'écriture dans le stream)
        document.close();
        
        // Récupérer les bytes
        byte[] pdfBytes = baos.toByteArray();
        
        System.out.println("✅ PDF généré en mémoire (" + pdfBytes.length + " bytes)");
        
        return pdfBytes;
        
    } catch (Exception e) {
        System.err.println("❌ Erreur génération PDF: " + e.getMessage());
        e.printStackTrace();
        throw e;
    } finally {
        try {
            baos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
    
    /**
     * 🎫 PAGE DE TICKET INDIVIDUEL (version optimisée)
     */
    private static void addSingleTicketPage(Document document, Ticket ticket, 
                                           Flight flight, Reservation reservation, 
                                           Customer customer,
                                           PdfFont boldFont, PdfFont regularFont) 
                                           throws Exception {
        
        // ========== HEADER AVEC LOGO ==========
        Table headerTable = new Table(UnitValue.createPercentArray(new float[]{1, 2}));
        headerTable.setWidth(UnitValue.createPercentValue(100));
        headerTable.setBackgroundColor(PRIMARY_COLOR);
        
        Paragraph logo = new Paragraph("✈️ SkyBooking")
            .setFont(boldFont)
            .setFontSize(24)
            .setFontColor(ColorConstants.WHITE)
            .setPadding(15);
        headerTable.addCell(new Cell().add(logo).setBorder(Border.NO_BORDER));
        
        Paragraph headerInfo = new Paragraph("BILLET ÉLECTRONIQUE\nALGÉRIE 🇩🇿")
            .setFont(boldFont)
            .setFontSize(12)
            .setFontColor(ColorConstants.WHITE)
            .setTextAlignment(TextAlignment.RIGHT)
            .setPadding(15);
        headerTable.addCell(new Cell().add(headerInfo).setBorder(Border.NO_BORDER));
        
        document.add(headerTable);
        document.add(new Paragraph("\n"));
        
        // ========== TITRE PRINCIPAL ==========
        Paragraph title = new Paragraph("CONFIRMATION DE BILLET")
            .setFont(boldFont)
            .setFontSize(22)
            .setFontColor(PRIMARY_COLOR)
            .setTextAlignment(TextAlignment.CENTER)
            .setBold();
        document.add(title);
        
        document.add(new Paragraph("\n"));
        
        // ========== BOÎTE PRINCIPALE DU TICKET ==========
        Table mainBox = new Table(1);
        mainBox.setWidth(UnitValue.createPercentValue(100));
        Cell mainCell = new Cell()
            .setBorder(new SolidBorder(PRIMARY_COLOR, 3))
            .setBackgroundColor(SECONDARY_COLOR)
            .setPadding(25);
        
        // N° de ticket en grand
        Paragraph ticketIdPara = new Paragraph()
            .add(new Text("N° Ticket: ").setFont(regularFont).setFontColor(TEXT_LIGHT).setFontSize(11))
            .add(new Text(ticket.ticketId).setFont(boldFont)
                .setFontSize(15).setFontColor(PRIMARY_COLOR));
        mainCell.add(ticketIdPara);
        
        mainCell.add(new Paragraph("\n"));
        
        // ========== SECTION PASSAGER ==========
        addSectionTitle(mainCell, "👤 INFORMATIONS PASSAGER", boldFont);
        addInfoLine(mainCell, "Nom complet", ticket.passengerName, boldFont, regularFont);
        addInfoLine(mainCell, "Siège assigné", ticket.seatNumber, boldFont, regularFont);
        
        mainCell.add(new Paragraph("\n"));
        
        // ========== SECTION VOL ==========
        addSectionTitle(mainCell, "✈️ DÉTAILS DU VOL", boldFont);
        addInfoLine(mainCell, "Numéro de vol", ticket.flightNumber, boldFont, regularFont);
        addInfoLine(mainCell, "Compagnie aérienne", flight.airline, boldFont, regularFont);
        addInfoLine(mainCell, "Trajet", ticket.departureCity + " → " + ticket.arrivalCity, 
                   boldFont, regularFont);
        addInfoLine(mainCell, "Date de départ", ticket.departureDate + " à " + 
                   ticket.departureTime, boldFont, regularFont);
        addInfoLine(mainCell, "Arrivée prévue", flight.arrivalDate + " à " + flight.arrivalTime, boldFont, regularFont);
        addInfoLine(mainCell, "Durée du vol", flight.duration, boldFont, regularFont);
        addInfoLine(mainCell, "Type d'appareil", flight.aircraftType, boldFont, regularFont);
        
        mainCell.add(new Paragraph("\n"));
        
        // ========== SECTION RÉSERVATION ==========
        addSectionTitle(mainCell, "📋 INFORMATIONS DE RÉSERVATION", boldFont);
        addInfoLine(mainCell, "N° de réservation", reservation.reservationId, 
                   boldFont, regularFont);
        addInfoLine(mainCell, "Prix du billet", String.format("%.2f DZD", ticket.price), 
                   boldFont, regularFont);
        addInfoLine(mainCell, "Statut", "✅ " + reservation.status, boldFont, regularFont);
        addInfoLine(mainCell, "Date de réservation", reservation.reservationDate, 
                   boldFont, regularFont);
        
        mainCell.add(new Paragraph("\n"));
        
        // ========== SECTION CLIENT ==========
        addSectionTitle(mainCell, "👥 CONTACT PRINCIPAL", boldFont);
        addInfoLine(mainCell, "Nom", customer.firstName + " " + customer.lastName, 
                   boldFont, regularFont);
        addInfoLine(mainCell, "Email", customer.email, boldFont, regularFont);
        addInfoLine(mainCell, "Téléphone", customer.phoneNumber, boldFont, regularFont);
        
        mainBox.addCell(mainCell);
        document.add(mainBox);
        
        document.add(new Paragraph("\n\n"));
        
        // ========== QR CODE ET BARCODE CÔTE À CÔTE ==========
        Table codesTable = new Table(2);
        codesTable.setWidth(UnitValue.createPercentValue(100));
        
        // QR Code
        String qrData = String.format(
            "TICKET:%s|PAX:%s|SEAT:%s|FLIGHT:%s|DATE:%s|RES:%s|CUSTOMER:%s", 
            ticket.ticketId, ticket.passengerName, ticket.seatNumber, 
            ticket.flightNumber, ticket.departureDate, reservation.reservationId,
            customer.customerId
        );
        byte[] qrCode = generateQRCode(qrData, 200, 200);
        
        Cell qrCell = new Cell().setBorder(Border.NO_BORDER);
        qrCell.add(new Paragraph("QR Code de vérification")
            .setFont(boldFont)
            .setFontSize(11)
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(PRIMARY_COLOR));
        qrCell.add(new Paragraph("Scannez pour validation rapide")
            .setFont(regularFont)
            .setFontSize(9)
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(TEXT_LIGHT));
        qrCell.add(new Image(ImageDataFactory.create(qrCode))
            .setWidth(160)
            .setHeight(160)
            .setHorizontalAlignment(
                com.itextpdf.layout.properties.HorizontalAlignment.CENTER));
        codesTable.addCell(qrCell);
        
        // Barcode
        byte[] barcode = generateBarcode(ticket.ticketId, 350, 100);
        Cell barcodeCell = new Cell().setBorder(Border.NO_BORDER);
        barcodeCell.add(new Paragraph("Code-barres d'embarquement")
            .setFont(boldFont)
            .setFontSize(11)
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(PRIMARY_COLOR));
        barcodeCell.add(new Paragraph("Compatible scanners aéroport")
            .setFont(regularFont)
            .setFontSize(9)
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(TEXT_LIGHT));
        barcodeCell.add(new Image(ImageDataFactory.create(barcode))
            .setWidth(260)
            .setHeight(75)
            .setHorizontalAlignment(
                com.itextpdf.layout.properties.HorizontalAlignment.CENTER));
        barcodeCell.add(new Paragraph(ticket.ticketId)
            .setFont(regularFont)
            .setFontSize(8)
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(TEXT_LIGHT)
            .setMarginTop(5));
        codesTable.addCell(barcodeCell);
        
        document.add(codesTable);
        
        // ========== INSTRUCTIONS IMPORTANTES ==========
        document.add(new Paragraph("\n"));
        addImportantNotice(document, boldFont, regularFont);
        
        document.add(new Paragraph("\n"));
        addBoardingInstructions(document, boldFont, regularFont);
        
        // ========== FOOTER ==========
        document.add(new Paragraph("\n"));
        addContactInfo(document, boldFont, regularFont);
        
        // Watermark discret en bas
        Paragraph watermark = new Paragraph("Billet électronique généré le " + 
            new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date()))
            .setFont(regularFont)
            .setFontSize(7)
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(TEXT_LIGHT)
            .setMarginTop(10);
        document.add(watermark);
    }
    
    /**
     * Générer un ticket PDF complet pour une réservation (multi-tickets)
     */
    public static byte[] generateReservationTickets(
        Reservation reservation,
        Ticket[] tickets,
        Flight flight,
        Customer customer) throws Exception {
    
    System.out.println("╔═══════════════════════════════════════════════╗");
    System.out.println("║  📄 GÉNÉRATION PDF MULTI-TICKETS             ║");
    System.out.println("╠═══════════════════════════════════════════════╣");
    System.out.println("║  Réservation: " + reservation.reservationId);
    System.out.println("║  Nombre tickets: " + tickets.length);
    System.out.println("╚═══════════════════════════════════════════════╝");
    
    // ✅ CORRECTION: Utiliser ByteArrayOutputStream
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    
    try {
        // Créer le PDF en mémoire
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf, PageSize.A4);
        
        // Marges
        document.setMargins(20, 20, 20, 20);
        
        // Police
        PdfFont boldFont = PdfFontFactory.createFont(
            com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD);
        PdfFont regularFont = PdfFontFactory.createFont(
            com.itextpdf.io.font.constants.StandardFonts.HELVETICA);
        
        // ========== PAGE DE COUVERTURE ==========
        addCoverPage(document, reservation, flight, customer, tickets.length, 
                    boldFont, regularFont);
        
        // ========== TICKETS INDIVIDUELS ==========
        for (int i = 0; i < tickets.length; i++) {
            document.add(new AreaBreak());
            addTicketPage(document, tickets[i], flight, reservation, customer, 
                         i + 1, tickets.length, boldFont, regularFont);
        }
        
        // ========== PAGE RÉCAPITULATIVE ==========
        document.add(new AreaBreak());
        addSummaryPage(document, reservation, tickets, flight, customer, 
                      boldFont, regularFont);
        
        // Fermer le document
        document.close();
        
        byte[] pdfBytes = baos.toByteArray();
        
        System.out.println("✅ PDF multi-tickets généré (" + pdfBytes.length + " bytes)");
        
        return pdfBytes;
        
    } catch (Exception e) {
        System.err.println("❌ Erreur génération PDF multi-tickets: " + e.getMessage());
        e.printStackTrace();
        throw e;
    } finally {
        try {
            baos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
   /**
    * 🔍 Méthode de diagnostic pour vérifier les polices et capacités graphiques
    * Utile pour déboguer les problèmes dans Docker
    */
     public static void checkSystemCapabilities() {
    System.out.println("\n╔════════════════════════════════════════╗");
    System.out.println("║  🔍 DIAGNOSTIC SYSTÈME PDF             ║");
    System.out.println("╠════════════════════════════════════════╣");
    
    // Vérifier Java AWT Headless
    String headless = System.getProperty("java.awt.headless");
    System.out.println("║  Java AWT Headless: " + headless);
    
    // Vérifier les polices disponibles
    try {
        java.awt.GraphicsEnvironment ge = 
            java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] fontNames = ge.getAvailableFontFamilyNames();
        System.out.println("║  Polices disponibles: " + fontNames.length);
        
        // Afficher les 5 premières polices
        for (int i = 0; i < Math.min(5, fontNames.length); i++) {
            System.out.println("║    - " + fontNames[i]);
        }
    } catch (Exception e) {
        System.out.println("║  ⚠️ Erreur vérification polices: " + e.getMessage());
    }
    
    // Vérifier ZXing (QR Code)
    try {
        Class.forName("com.google.zxing.MultiFormatWriter");
        System.out.println("║  ✅ ZXing (QR Code) disponible");
    } catch (ClassNotFoundException e) {
        System.out.println("║  ❌ ZXing non disponible!");
    }
    
    // Vérifier iText
    try {
        Class.forName("com.itextpdf.kernel.pdf.PdfDocument");
        System.out.println("║  ✅ iText PDF disponible");
    } catch (ClassNotFoundException e) {
        System.out.println("║  ❌ iText non disponible!");
    }
    
    System.out.println("╚════════════════════════════════════════╝\n");
    }

    
    /**
     * ✈️ PAGE DE COUVERTURE
     */
    private static void addCoverPage(Document document, Reservation reservation, 
                                     Flight flight, Customer customer, int ticketCount,
                                     PdfFont boldFont, PdfFont regularFont) throws Exception {
        
        // Header avec logo
        Table headerTable = new Table(UnitValue.createPercentArray(new float[]{1, 2}));
        headerTable.setWidth(UnitValue.createPercentValue(100));
        
        // Logo (simulé avec texte stylisé)
        Paragraph logo = new Paragraph("✈️ SkyBooking")
            .setFont(boldFont)
            .setFontSize(28)
            .setFontColor(PRIMARY_COLOR)
            .setBold();
        headerTable.addCell(new Cell().add(logo).setBorder(Border.NO_BORDER));
        
        // Info compagnie
        Paragraph companyInfo = new Paragraph("ALGÉRIE 🇩🇿\nVotre partenaire de voyage")
            .setFont(regularFont)
            .setFontSize(10)
            .setFontColor(TEXT_LIGHT)
            .setTextAlignment(TextAlignment.RIGHT);
        headerTable.addCell(new Cell().add(companyInfo).setBorder(Border.NO_BORDER));
        
        document.add(headerTable);
        document.add(new Paragraph("\n"));
        
        // Titre principal
        Paragraph title = new Paragraph("CONFIRMATION DE RÉSERVATION")
            .setFont(boldFont)
            .setFontSize(24)
            .setFontColor(PRIMARY_COLOR)
            .setTextAlignment(TextAlignment.CENTER)
            .setBold();
        document.add(title);
        
        document.add(new Paragraph("\n"));
        
        // Boîte d'information principale
        Table mainInfoBox = new Table(1);
        mainInfoBox.setWidth(UnitValue.createPercentValue(100));
        mainInfoBox.setBackgroundColor(SECONDARY_COLOR);
        
        Cell infoCell = new Cell()
            .setBorder(new SolidBorder(PRIMARY_COLOR, 2))
            .setPadding(20);
        
        // Numéro de réservation
        Paragraph resId = new Paragraph()
            .add(new Text("Numéro de réservation: ").setFont(regularFont).setFontColor(TEXT_LIGHT))
            .add(new Text(reservation.reservationId).setFont(boldFont).setFontSize(18).setFontColor(PRIMARY_COLOR));
        infoCell.add(resId);
        
        infoCell.add(new Paragraph("\n"));
        
        // Informations du vol
        addInfoLine(infoCell, "Vol", flight.flightNumber + " - " + flight.airline, boldFont, regularFont);
        addInfoLine(infoCell, "Trajet", flight.departureCity + " → " + flight.arrivalCity, boldFont, regularFont);
        addInfoLine(infoCell, "Date de départ", flight.departureDate + " à " + flight.departureTime, boldFont, regularFont);
        addInfoLine(infoCell, "Date d'arrivée", flight.arrivalDate + " à " + flight.arrivalTime, boldFont, regularFont);
        addInfoLine(infoCell, "Durée", flight.duration, boldFont, regularFont);
        
        infoCell.add(new Paragraph("\n"));
        
        // Informations passager principal
        addInfoLine(infoCell, "Passager principal", customer.firstName + " " + customer.lastName, boldFont, regularFont);
        addInfoLine(infoCell, "Email", customer.email, boldFont, regularFont);
        addInfoLine(infoCell, "Téléphone", customer.phoneNumber, boldFont, regularFont);
        
        infoCell.add(new Paragraph("\n"));
        
        // Résumé
        addInfoLine(infoCell, "Nombre de tickets", String.valueOf(ticketCount), boldFont, regularFont);
        addInfoLine(infoCell, "Montant total", String.format("%.0f DZD", reservation.totalPrice), boldFont, regularFont);
        addInfoLine(infoCell, "Statut", "✅ " + reservation.status, boldFont, regularFont);
        addInfoLine(infoCell, "Date de réservation", reservation.reservationDate, boldFont, regularFont);
        
        mainInfoBox.addCell(infoCell);
        document.add(mainInfoBox);
        
        document.add(new Paragraph("\n\n"));
        
        // QR Code de la réservation
        String qrData = String.format("RESERVATION:%s|FLIGHT:%s|CUSTOMER:%s", 
            reservation.reservationId, flight.flightNumber, customer.customerId);
        byte[] qrCode = generateQRCode(qrData, 200, 200);
        
        Image qrImage = new Image(ImageDataFactory.create(qrCode))
            .setWidth(150)
            .setHeight(150)
            .setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER);
        
        document.add(new Paragraph("Scannez pour vérifier la réservation")
            .setFont(regularFont)
            .setFontSize(10)
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(TEXT_LIGHT));
        document.add(qrImage);
        
        // Instructions importantes
        document.add(new Paragraph("\n\n"));
        addImportantNotice(document, boldFont, regularFont);
    }
    
    /**
     * 🎫 PAGE DE TICKET INDIVIDUEL (multi-tickets)
     */
    private static void addTicketPage(Document document, Ticket ticket, Flight flight,
                                     Reservation reservation, Customer customer,
                                     int ticketNumber, int totalTickets,
                                     PdfFont boldFont, PdfFont regularFont) throws Exception {
        
        // En-tête du ticket
        Table ticketHeader = new Table(UnitValue.createPercentArray(new float[]{3, 1}));
        ticketHeader.setWidth(UnitValue.createPercentValue(100));
        ticketHeader.setBackgroundColor(PRIMARY_COLOR);
        
        Paragraph ticketTitle = new Paragraph("BILLET ÉLECTRONIQUE")
            .setFont(boldFont)
            .setFontSize(20)
            .setFontColor(ColorConstants.WHITE)
            .setPadding(10);
        ticketHeader.addCell(new Cell().add(ticketTitle).setBorder(Border.NO_BORDER));
        
        Paragraph ticketNum = new Paragraph(String.format("Ticket %d/%d", ticketNumber, totalTickets))
            .setFont(boldFont)
            .setFontSize(14)
            .setFontColor(ColorConstants.WHITE)
            .setTextAlignment(TextAlignment.RIGHT)
            .setPadding(10);
        ticketHeader.addCell(new Cell().add(ticketNum).setBorder(Border.NO_BORDER));
        
        document.add(ticketHeader);
        
        // Corps du ticket avec bordure
        Table ticketBody = new Table(1);
        ticketBody.setWidth(UnitValue.createPercentValue(100));
        Cell bodyCell = new Cell()
            .setBorder(new SolidBorder(PRIMARY_COLOR, 2))
            .setPadding(20)
            .setBackgroundColor(SECONDARY_COLOR);
        
        // Numéro de ticket
        Paragraph ticketId = new Paragraph()
            .add(new Text("N° Ticket: ").setFont(regularFont).setFontColor(TEXT_LIGHT))
            .add(new Text(ticket.ticketId).setFont(boldFont).setFontSize(12).setFontColor(PRIMARY_COLOR));
        bodyCell.add(ticketId);
        
        bodyCell.add(new Paragraph("\n"));
        
        // Section Passager
        addSectionTitle(bodyCell, "👤 INFORMATIONS PASSAGER", boldFont);
        addInfoLine(bodyCell, "Nom complet", ticket.passengerName, boldFont, regularFont);
        addInfoLine(bodyCell, "Siège", ticket.seatNumber, boldFont, regularFont);
        
        bodyCell.add(new Paragraph("\n"));
        
        // Section Vol
        addSectionTitle(bodyCell, "✈️ DÉTAILS DU VOL", boldFont);
        addInfoLine(bodyCell, "N° de vol", ticket.flightNumber, boldFont, regularFont);
        addInfoLine(bodyCell, "De", ticket.departureCity, boldFont, regularFont);
        addInfoLine(bodyCell, "Vers", ticket.arrivalCity, boldFont, regularFont);
        addInfoLine(bodyCell, "Date", ticket.departureDate, boldFont, regularFont);
        addInfoLine(bodyCell, "Heure de départ", ticket.departureTime, boldFont, regularFont);
        
        bodyCell.add(new Paragraph("\n"));
        
        // Section Paiement
        addSectionTitle(bodyCell, "💰 INFORMATIONS TARIFAIRES", boldFont);
        addInfoLine(bodyCell, "Prix du billet", String.format("%.0f DZD", ticket.price), boldFont, regularFont);
        addInfoLine(bodyCell, "N° Réservation", reservation.reservationId, boldFont, regularFont);
        
        ticketBody.addCell(bodyCell);
        document.add(ticketBody);
        
        document.add(new Paragraph("\n"));
        
        // QR Code et Barcode côte à côte
        Table codesTable = new Table(2);
        codesTable.setWidth(UnitValue.createPercentValue(100));
        
        // QR Code
        String qrData = String.format("TICKET:%s|PASSENGER:%s|SEAT:%s|FLIGHT:%s|DATE:%s", 
            ticket.ticketId, ticket.passengerName, ticket.seatNumber, 
            ticket.flightNumber, ticket.departureDate);
        byte[] qrCode = generateQRCode(qrData, 150, 150);
        
        Cell qrCell = new Cell().setBorder(Border.NO_BORDER);
        qrCell.add(new Paragraph("QR Code de vérification")
            .setFont(regularFont)
            .setFontSize(9)
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(TEXT_LIGHT));
        qrCell.add(new Image(ImageDataFactory.create(qrCode))
            .setWidth(120)
            .setHeight(120)
            .setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER));
        codesTable.addCell(qrCell);
        
        // Barcode
        byte[] barcode = generateBarcode(ticket.ticketId, 300, 80);
        Cell barcodeCell = new Cell().setBorder(Border.NO_BORDER);
        barcodeCell.add(new Paragraph("Code-barres du billet")
            .setFont(regularFont)
            .setFontSize(9)
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(TEXT_LIGHT));
        barcodeCell.add(new Image(ImageDataFactory.create(barcode))
            .setWidth(200)
            .setHeight(60)
            .setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER));
        barcodeCell.add(new Paragraph(ticket.ticketId)
            .setFont(regularFont)
            .setFontSize(8)
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(TEXT_LIGHT));
        codesTable.addCell(barcodeCell);
        
        document.add(codesTable);
        
        // Instructions d'embarquement
        document.add(new Paragraph("\n"));
        addBoardingInstructions(document, boldFont, regularFont);
    }
    
    /**
     * 📋 PAGE RÉCAPITULATIVE
     */
    private static void addSummaryPage(Document document, Reservation reservation,
                                      Ticket[] tickets, Flight flight, Customer customer,
                                      PdfFont boldFont, PdfFont regularFont) throws Exception {
        
        // Titre
        Paragraph title = new Paragraph("RÉCAPITULATIF DE LA RÉSERVATION")
            .setFont(boldFont)
            .setFontSize(20)
            .setFontColor(PRIMARY_COLOR)
            .setTextAlignment(TextAlignment.CENTER);
        document.add(title);
        
        document.add(new Paragraph("\n"));
        
        // Tableau des tickets
        Table ticketsTable = new Table(UnitValue.createPercentArray(new float[]{1, 3, 2, 2, 2}));
        ticketsTable.setWidth(UnitValue.createPercentValue(100));
        
        // En-tête du tableau
        addTableHeader(ticketsTable, "#", boldFont);
        addTableHeader(ticketsTable, "Passager", boldFont);
        addTableHeader(ticketsTable, "Siège", boldFont);
        addTableHeader(ticketsTable, "N° Ticket", boldFont);
        addTableHeader(ticketsTable, "Prix", boldFont);
        
        // Lignes des tickets
        double total = 0;
        for (int i = 0; i < tickets.length; i++) {
            Ticket t = tickets[i];
            ticketsTable.addCell(createTableCell(String.valueOf(i + 1), regularFont));
            ticketsTable.addCell(createTableCell(t.passengerName, regularFont));
            ticketsTable.addCell(createTableCell(t.seatNumber, regularFont));
            ticketsTable.addCell(createTableCell(t.ticketId.substring(0, Math.min(12, t.ticketId.length())) + "...", regularFont));
            ticketsTable.addCell(createTableCell(String.format("%.0f DZD", t.price), regularFont));
            total += t.price;
        }
        
        // Ligne totale
        Cell totalLabelCell = new Cell(1, 4)
            .add(new Paragraph("TOTAL")
                .setFont(boldFont)
                .setFontSize(12)
                .setTextAlignment(TextAlignment.RIGHT))
            .setBackgroundColor(PRIMARY_COLOR)
            .setFontColor(ColorConstants.WHITE)
            .setPadding(10)
            .setBorder(Border.NO_BORDER);
        ticketsTable.addCell(totalLabelCell);
        
        Cell totalValueCell = new Cell()
            .add(new Paragraph(String.format("%.0f DZD", total))
                .setFont(boldFont)
                .setFontSize(12)
                .setTextAlignment(TextAlignment.CENTER))
            .setBackgroundColor(ACCENT_COLOR)
            .setFontColor(ColorConstants.WHITE)
            .setPadding(10)
            .setBorder(Border.NO_BORDER);
        ticketsTable.addCell(totalValueCell);
        
        document.add(ticketsTable);
        
        // Informations de contact
        document.add(new Paragraph("\n\n"));
        addContactInfo(document, boldFont, regularFont);
    }
    
    // ========== MÉTHODES UTILITAIRES ==========
    
    private static void addInfoLine(Cell cell, String label, String value, 
                                   PdfFont boldFont, PdfFont regularFont) {
        Paragraph p = new Paragraph()
            .add(new Text(label + ": ").setFont(regularFont).setFontColor(TEXT_LIGHT).setFontSize(10))
            .add(new Text(value).setFont(boldFont).setFontSize(11).setFontColor(TEXT_DARK));
        cell.add(p);
    }
    
    private static void addSectionTitle(Cell cell, String title, PdfFont boldFont) {
        Paragraph p = new Paragraph(title)
            .setFont(boldFont)
            .setFontSize(13)
            .setFontColor(PRIMARY_COLOR)
            .setBold();
        cell.add(p);
        cell.add(new Paragraph("\n"));
    }
    
    private static void addTableHeader(Table table, String text, PdfFont boldFont) {
        Cell cell = new Cell()
            .add(new Paragraph(text).setFont(boldFont).setFontColor(ColorConstants.WHITE))
            .setBackgroundColor(PRIMARY_COLOR)
            .setTextAlignment(TextAlignment.CENTER)
            .setPadding(8)
            .setBorder(Border.NO_BORDER);
        table.addHeaderCell(cell);
    }
    
    private static Cell createTableCell(String text, PdfFont regularFont) {
        return new Cell()
            .add(new Paragraph(text).setFont(regularFont).setFontSize(9))
            .setPadding(6)
            .setTextAlignment(TextAlignment.CENTER)
            .setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f));
    }
    
    private static void addImportantNotice(Document document, PdfFont boldFont, PdfFont regularFont) {
        Table noticeBox = new Table(1);
        noticeBox.setWidth(UnitValue.createPercentValue(100));
        
        Cell noticeCell = new Cell()
            .setBackgroundColor(new DeviceRgb(254, 243, 199)) // Jaune clair
            .setBorder(new SolidBorder(new DeviceRgb(245, 158, 11), 1))
            .setPadding(15);
        
        Paragraph noticeTitle = new Paragraph("⚠️ INFORMATIONS IMPORTANTES")
            .setFont(boldFont)
            .setFontSize(12)
            .setFontColor(new DeviceRgb(146, 64, 14));
        noticeCell.add(noticeTitle);
        
        String[] notices = {
            "• Présentez-vous à l'aéroport 2 heures avant le départ",
            "• Munissez-vous d'une pièce d'identité valide",
            "• Imprimez ce billet ou présentez-le sur votre mobile",
            "• Vérifiez les conditions de bagages de votre compagnie",
            "• Les billets sont nominatifs et non transférables"
        };
        
        for (String notice : notices) {
            noticeCell.add(new Paragraph(notice)
                .setFont(regularFont)
                .setFontSize(9)
                .setMarginTop(3));
        }
        
        noticeBox.addCell(noticeCell);
        document.add(noticeBox);
    }
    
    private static void addBoardingInstructions(Document document, PdfFont boldFont, PdfFont regularFont) {
        Table instructionsBox = new Table(1);
        instructionsBox.setWidth(UnitValue.createPercentValue(100));
        
        Cell instructionsCell = new Cell()
            .setBackgroundColor(new DeviceRgb(219, 234, 254)) // Bleu clair
            .setBorder(new SolidBorder(PRIMARY_COLOR, 1))
            .setPadding(12);
        
        Paragraph instructionsTitle = new Paragraph("📋 INSTRUCTIONS D'EMBARQUEMENT")
            .setFont(boldFont)
            .setFontSize(11)
            .setFontColor(PRIMARY_COLOR);
        instructionsCell.add(instructionsTitle);
        
        instructionsCell.add(new Paragraph(
            "1. Enregistrez-vous en ligne 24h avant le vol\n" +
            "2. Imprimez votre carte d'embarquement ou téléchargez-la\n" +
            "3. Déposez vos bagages au comptoir de la compagnie\n" +
            "4. Présentez-vous à la porte d'embarquement 30 min avant"
        ).setFont(regularFont).setFontSize(9).setMarginTop(5));
        
        instructionsBox.addCell(instructionsCell);
        document.add(instructionsBox);
    }
    
    private static void addContactInfo(Document document, PdfFont boldFont, PdfFont regularFont) {
        Table contactBox = new Table(1);
        contactBox.setWidth(UnitValue.createPercentValue(100));
        
        Cell contactCell = new Cell()
            .setBackgroundColor(SECONDARY_COLOR)
            .setBorder(new SolidBorder(TEXT_LIGHT, 1))
            .setPadding(15);
        
        Paragraph contactTitle = new Paragraph("📞 BESOIN D'AIDE ?")
            .setFont(boldFont)
            .setFontSize(12)
            .setFontColor(PRIMARY_COLOR);
        contactCell.add(contactTitle);
        
        contactCell.add(new Paragraph(
            "📧 Email: support@skybooking.dz\n" +
            "📱 Téléphone: +213 562 998 526\n" +
            "🌐 Site web: www.skybooking.dz\n" +
            "⏰ Service client: 7j/7, 24h/24"
        ).setFont(regularFont).setFontSize(9).setMarginTop(5));
        
        contactBox.addCell(contactCell);
        document.add(contactBox);
    }
    
    /**
     * Générer un QR Code
     */
    private static byte[] generateQRCode(String data, int width, int height) throws Exception {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1);
        
        BitMatrix matrix = new MultiFormatWriter().encode(
            data, BarcodeFormat.QR_CODE, width, height, hints
        );
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", outputStream);
        return outputStream.toByteArray();
    }
    
    /**
     * Générer un Code-barres (CODE_128)
     */
    private static byte[] generateBarcode(String data, int width, int height) throws Exception {
        BitMatrix matrix = new MultiFormatWriter().encode(
            data, BarcodeFormat.CODE_128, width, height
        );
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", outputStream);
        return outputStream.toByteArray();
    }
    
    /**
     * Créer les dossiers nécessaires
     */
    private static void createDirectories() {
        new File(TICKETS_DIR).mkdirs();
        new File(TEMP_IMAGES_DIR).mkdirs();
    }
    
    /**
     * Nettoyer les fichiers temporaires
     */
    public static void cleanupTempFiles() {
        File tempDir = new File(TEMP_IMAGES_DIR);
        if (tempDir.exists() && tempDir.isDirectory()) {
            File[] files = tempDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
        }
    }
}