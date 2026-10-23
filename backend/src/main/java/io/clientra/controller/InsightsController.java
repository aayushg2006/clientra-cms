package io.clientra.controller;

import io.clientra.config.AppProperties;
import io.clientra.dto.InsightDtos.DashboardKpis;
import io.clientra.dto.InsightDtos.ReportSummary;
import io.clientra.dto.InteractionDtos.InteractionDto;
import io.clientra.service.DashboardService;
import io.clientra.service.ReportPdfService;
import io.clientra.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Dashboard & Reports")
@RestController
@RequiredArgsConstructor
public class InsightsController {

    private final DashboardService dashboard;
    private final ReportService reports;
    private final ReportPdfService pdf;
    private final AppProperties props;

    @Operation(summary = "Dashboard KPIs (Redis cached for 60s)")
    @GetMapping("/api/dashboard/kpis")
    public DashboardKpis kpis() {
        return dashboard.kpis();
    }

    @GetMapping("/api/dashboard/follow-ups")
    public List<InteractionDto> followUpsToday() {
        return dashboard.followUpsToday();
    }

    @Operation(summary = "Funnel, ticket and executive reports (Admin). period = quarter | last-quarter | year")
    @GetMapping("/api/reports/summary")
    public ReportSummary summary(@RequestParam(defaultValue = "quarter") String period) {
        return reports.summary(period);
    }

    @GetMapping(value = "/api/reports/summary.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> summaryPdf(@RequestParam(defaultValue = "quarter") String period) {
        byte[] body = pdf.render(reports.summary(period));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"clientra-report-" + period + "-"
                        + LocalDate.now(props.timezone()) + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(body);
    }
}
