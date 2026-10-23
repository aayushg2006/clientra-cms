package io.clientra.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import io.clientra.config.AppProperties;
import io.clientra.dto.InsightDtos.*;
import io.clientra.util.Formats;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Renders the Reports & Analytics summary as a branded A4 PDF (OpenPDF). */
@Service
@RequiredArgsConstructor
public class ReportPdfService {

    private static final Color TEAL = new Color(0x14, 0x77, 0x6B);
    private static final Color TEAL_DARK = new Color(0x0E, 0x4A, 0x43);
    private static final Color SAND = new Color(0xF7, 0xF4, 0xEE);
    private static final Color INK = new Color(0x1F, 0x29, 0x33);
    private static final Color MUTED = new Color(0x6B, 0x72, 0x80);

    private final AppProperties props;
    private final Clock clock;

    public byte[] render(ReportSummary r) {
        // OpenPDF's built-in fonts have no rupee glyph, so amounts use "Rs."
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, out);
        doc.open();

        Font h1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, TEAL_DARK);
        Font h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, INK);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 9, MUTED);

        Paragraph title = new Paragraph("Clientra CMS — Reports & Analytics", h1);
        doc.add(title);
        String generated = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").format(ZonedDateTime.now(clock.withZone(props.timezone())));
        doc.add(new Paragraph(r.periodLabel() + " · " + r.rangeLabel() + "   |   Generated " + generated, small));
        doc.add(Chunk.NEWLINE);

        PdfPTable kpis = new PdfPTable(4);
        kpis.setWidthPercentage(100);
        kpiCell(kpis, "Customer Retention", pct(r.retention().value()), delta(r.retention().delta(), " pts"));
        kpiCell(kpis, "Lead → Customer Conversion", pct(r.conversion().value()), r.conversion().note());
        kpiCell(kpis, "Avg. Ticket Resolution", r.avgResolutionHours().value() == null ? "—" : r.avgResolutionHours().value() + " h",
                delta(r.avgResolutionHours().delta(), " h"));
        kpiCell(kpis, "SLA Compliance", pct(r.slaCompliance().value()), r.slaBreaches() + " breaches");
        doc.add(kpis);
        doc.add(new Paragraph("Revenue in period: " + rs(r.revenue()) + "   ·   Paying customers in period: " + r.ordersPaid(), small));
        doc.add(Chunk.NEWLINE);

        doc.add(new Paragraph("Customer Lifecycle Funnel", h2));
        table(doc, new float[]{3, 1, 1}, List.of("Stage", "Customers", "% of leads"),
                r.funnel().stream().map(f -> List.of(f.stage(), String.valueOf(f.count()), Math.round(f.percent()) + "%")).toList());

        doc.add(new Paragraph("Tickets by Category (" + r.ticketTotal() + " tickets)", h2));
        table(doc, new float[]{3, 1, 1}, List.of("Category", "Tickets", "Share"),
                r.ticketCategories().stream().map(c -> List.of(c.category(), String.valueOf(c.count()), Math.round(c.percent()) + "%")).toList());

        doc.add(new Paragraph("Sales Executive Performance", h2));
        table(doc, new float[]{2.4f, 1.2f, 1.2f, 1.2f, 1.4f, 1.1f},
                List.of("Sales Executive", "Customers owned", "Interactions", "Orders closed", "Revenue", "Conversion"),
                r.executives().stream().map(e -> List.of(e.name(), String.valueOf(e.customersOwned()), String.valueOf(e.interactions()),
                        String.valueOf(e.ordersClosed()), rs(e.revenue()), Math.round(e.conversionPercent()) + "%")).toList());

        doc.close();
        return out.toByteArray();
    }

    private void kpiCell(PdfPTable t, String label, String value, String note) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(SAND);
        cell.setBorderColor(Color.WHITE);
        cell.setBorderWidth(3);
        cell.setPadding(8);
        cell.addElement(new Paragraph(label, FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED)));
        cell.addElement(new Paragraph(value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, INK)));
        if (note != null) cell.addElement(new Paragraph(note, FontFactory.getFont(FontFactory.HELVETICA, 8, TEAL)));
        t.addCell(cell);
    }

    private void table(Document doc, float[] widths, List<String> header, List<List<String>> rows) {
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        t.setSpacingBefore(6);
        t.setSpacingAfter(14);
        Font hf = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        Font cf = FontFactory.getFont(FontFactory.HELVETICA, 9, INK);
        for (String h : header) {
            PdfPCell c = new PdfPCell(new Phrase(h, hf));
            c.setBackgroundColor(TEAL);
            c.setPadding(6);
            c.setBorderColor(TEAL);
            t.addCell(c);
        }
        boolean shade = false;
        for (List<String> row : rows) {
            for (String v : row) {
                PdfPCell c = new PdfPCell(new Phrase(v, cf));
                c.setPadding(5);
                c.setBorderColor(new Color(0xE8, 0xE1, 0xD4));
                if (shade) c.setBackgroundColor(SAND);
                t.addCell(c);
            }
            shade = !shade;
        }
        if (rows.isEmpty()) {
            PdfPCell c = new PdfPCell(new Phrase("No data for this period", cf));
            c.setColspan(header.size());
            c.setPadding(6);
            t.addCell(c);
        }
        doc.add(t);
    }

    private static String pct(Double v) {
        return v == null ? "—" : v + "%";
    }

    private static String delta(Double d, String unit) {
        if (d == null) return "vs previous period: n/a";
        return (d >= 0 ? "+" : "") + d + unit + " vs previous period";
    }

    private static String rs(java.math.BigDecimal v) {
        return Formats.inr(v).replace("₹", "Rs. ");
    }
}
