// src/main/java/com/skybooking/pdf/InvoicePDFGenerator.java

package com.skybooking.pdf;

import com.itextpdf.kernel.pdf.*;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.*;
import com.itextpdf.kernel.colors.*;
import com.itextpdf.kernel.font.*;
import FlightReservation.*;
import java.io.*;
import java.nio.file.*;

/**
 * 🧾 Générateur de factures PDF professionnel
 */
public class InvoicePDFGenerator {
    
    private static final String INVOICES_DIR = "invoices";
    private static final DeviceRgb PRIMARY_COLOR = new DeviceRgb(37, 99, 235);
    
    public static byte[] generateInvoicePDF(
        Invoice invoice,
        Payment payment,
        Reservation reservation,
        Customer customer,
        Flight flight) throws Exception {
    
    System.out.println("╔═══════════════════════════════════════╗");
    System.out.println("║  🧾 GÉNÉRATION FACTURE PDF           ║");
    System.out.println("╠═══════════════════════════════════════╣");
    System.out.println("║  Facture: " + invoice.invoiceId);
    System.out.println("║  Montant: " + invoice.totalAmount + " DZD");
    System.out.println("╚═══════════════════════════════════════╝");
    
    // ✅ CORRECTION: Utiliser ByteArrayOutputStream au lieu du disque
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    
    try {
        // Créer le PDF en mémoire
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);
        
        // Polices
        PdfFont boldFont = PdfFontFactory.createFont(
            com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD);
        PdfFont regularFont = PdfFontFactory.createFont(
            com.itextpdf.io.font.constants.StandardFonts.HELVETICA);
        
        // En-tête
        addInvoiceHeader(document, invoice, boldFont, regularFont);
        
        // Informations client
        addCustomerInfo(document, customer, boldFont, regularFont);
        
        // Détails réservation
        addReservationDetails(document, reservation, flight, boldFont, regularFont);
        
        // Détails paiement
        addPaymentDetails(document, payment, boldFont, regularFont);
        
        // Total
        addInvoiceTotal(document, invoice, boldFont, regularFont);
        
        // Pied de page
        addInvoiceFooter(document, regularFont);
        
        // Fermer le document
        document.close();
        
        // Récupérer les bytes
        byte[] pdfBytes = baos.toByteArray();
        
        System.out.println("✅ Facture PDF générée (" + pdfBytes.length + " bytes)");
        
        return pdfBytes;
        
    } catch (Exception e) {
        System.err.println("❌ Erreur génération facture: " + e.getMessage());
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
    
    private static void addInvoiceHeader(Document document, Invoice invoice,
                                        PdfFont boldFont, PdfFont regularFont) {
        Table headerTable = new Table(2);
        headerTable.setWidth(UnitValue.createPercentValue(100));
        
        // Logo + Nom
        Cell logoCell = new Cell();
        logoCell.add(new Paragraph("✈️ SkyBooking Algérie")
            .setFont(boldFont)
            .setFontSize(24)
            .setFontColor(PRIMARY_COLOR));
        logoCell.add(new Paragraph("Réservation de vols")
            .setFont(regularFont)
            .setFontSize(10));
        logoCell.setBorder(com.itextpdf.layout.borders.Border.NO_BORDER);
        headerTable.addCell(logoCell);
        
        // Facture
        Cell invoiceCell = new Cell();
        invoiceCell.add(new Paragraph("FACTURE")
            .setFont(boldFont)
            .setFontSize(20)
            .setTextAlignment(TextAlignment.RIGHT));
        invoiceCell.add(new Paragraph(invoice.invoiceId)
            .setFont(regularFont)
            .setFontSize(12)
            .setTextAlignment(TextAlignment.RIGHT));
        invoiceCell.add(new Paragraph("Date: " + invoice.issueDate)
            .setFont(regularFont)
            .setFontSize(10)
            .setTextAlignment(TextAlignment.RIGHT));
        invoiceCell.setBorder(com.itextpdf.layout.borders.Border.NO_BORDER);
        headerTable.addCell(invoiceCell);
        
        document.add(headerTable);
        document.add(new Paragraph("\n"));
    }
    
    private static void addCustomerInfo(Document document, Customer customer,
                                       PdfFont boldFont, PdfFont regularFont) {
        document.add(new Paragraph("FACTURÉ À")
            .setFont(boldFont)
            .setFontSize(12));
        
        document.add(new Paragraph(customer.firstName + " " + customer.lastName)
            .setFont(regularFont));
        document.add(new Paragraph(customer.email)
            .setFont(regularFont)
            .setFontSize(10));
        document.add(new Paragraph(customer.phoneNumber)
            .setFont(regularFont)
            .setFontSize(10));
        
        document.add(new Paragraph("\n"));
    }
    
    private static void addReservationDetails(Document document, Reservation reservation,
                                             Flight flight, PdfFont boldFont, PdfFont regularFont) {
        Table table = new Table(new float[]{3, 1, 2});
        table.setWidth(UnitValue.createPercentValue(100));
        
        // En-tête
        table.addHeaderCell(new Cell().add(new Paragraph("DESCRIPTION").setFont(boldFont))
            .setBackgroundColor(PRIMARY_COLOR).setFontColor(ColorConstants.WHITE));
        table.addHeaderCell(new Cell().add(new Paragraph("QUANTITÉ").setFont(boldFont))
            .setBackgroundColor(PRIMARY_COLOR).setFontColor(ColorConstants.WHITE));
        table.addHeaderCell(new Cell().add(new Paragraph("MONTANT").setFont(boldFont))
            .setBackgroundColor(PRIMARY_COLOR).setFontColor(ColorConstants.WHITE));
        
        // Ligne
        String description = String.format("Vol %s\n%s → %s\nDate: %s",
            flight.flightNumber, flight.departureCity, flight.arrivalCity, flight.departureDate);
        
        table.addCell(new Cell().add(new Paragraph(description).setFont(regularFont)));
        table.addCell(new Cell().add(new Paragraph("1").setFont(regularFont)));
        table.addCell(new Cell().add(new Paragraph(String.format("%.0f DZD", reservation.totalPrice))
            .setFont(regularFont)));
        
        document.add(table);
        document.add(new Paragraph("\n"));
    }
    
    private static void addPaymentDetails(Document document, Payment payment,
                                         PdfFont boldFont, PdfFont regularFont) {
        document.add(new Paragraph("INFORMATIONS DE PAIEMENT")
            .setFont(boldFont)
            .setFontSize(12));
        
        document.add(new Paragraph("Méthode: " + payment.method.toString())
            .setFont(regularFont)
            .setFontSize(10));
        document.add(new Paragraph("Transaction: " + payment.transactionId)
            .setFont(regularFont)
            .setFontSize(10));
        document.add(new Paragraph("Référence: " + payment.bankReference)
            .setFont(regularFont)
            .setFontSize(10));
        document.add(new Paragraph("Date: " + payment.paymentDate)
            .setFont(regularFont)
            .setFontSize(10));
        
        document.add(new Paragraph("\n"));
    }
    
    private static void addInvoiceTotal(Document document, Invoice invoice,
                                       PdfFont boldFont, PdfFont regularFont) {
        Table totalTable = new Table(new float[]{3, 2});
        totalTable.setWidth(UnitValue.createPercentValue(50));
        totalTable.setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.RIGHT);
        
        totalTable.addCell(new Cell().add(new Paragraph("Montant HT:").setFont(regularFont))
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));
        totalTable.addCell(new Cell().add(new Paragraph(String.format("%.0f DZD", invoice.amount))
            .setFont(regularFont).setTextAlignment(TextAlignment.RIGHT))
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));
        
        totalTable.addCell(new Cell().add(new Paragraph("TVA (19%):").setFont(regularFont))
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));
        totalTable.addCell(new Cell().add(new Paragraph(String.format("%.0f DZD", invoice.taxAmount))
            .setFont(regularFont).setTextAlignment(TextAlignment.RIGHT))
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));
        
        totalTable.addCell(new Cell().add(new Paragraph("TOTAL TTC:").setFont(boldFont).setFontSize(14))
            .setBackgroundColor(PRIMARY_COLOR).setFontColor(ColorConstants.WHITE));
        totalTable.addCell(new Cell().add(new Paragraph(String.format("%.0f DZD", invoice.totalAmount))
            .setFont(boldFont).setFontSize(14).setTextAlignment(TextAlignment.RIGHT))
            .setBackgroundColor(PRIMARY_COLOR).setFontColor(ColorConstants.WHITE));
        
        document.add(totalTable);
    }
    
    private static void addInvoiceFooter(Document document, PdfFont regularFont) {
        document.add(new Paragraph("\n\n"));
        document.add(new Paragraph("Merci pour votre confiance!")
            .setFont(regularFont)
            .setTextAlignment(TextAlignment.CENTER)
            .setFontSize(10));
        document.add(new Paragraph("SkyBooking Algérie - contact@skybooking.dz - +213 562 998 526")
            .setFont(regularFont)
            .setTextAlignment(TextAlignment.CENTER)
            .setFontSize(8)
            .setFontColor(ColorConstants.GRAY));
    }
}
