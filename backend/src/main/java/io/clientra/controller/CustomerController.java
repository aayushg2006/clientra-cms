package io.clientra.controller;

import io.clientra.config.AppProperties;
import io.clientra.dto.CustomerDtos.*;
import io.clientra.entity.enums.CustomerStatus;
import io.clientra.entity.enums.Segment;
import io.clientra.service.CustomerCsvService;
import io.clientra.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Customers")
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerCsvService csvService;
    private final AppProperties props;

    @Operation(summary = "Search, filter and paginate customers")
    @GetMapping
    public Page<CustomerDto> search(@RequestParam(required = false) String q,
                                    @RequestParam(required = false) CustomerStatus status,
                                    @RequestParam(required = false) Segment segment,
                                    @RequestParam(required = false) Long ownerId,
                                    @RequestParam(required = false) Long tagId,
                                    @PageableDefault(size = 20, sort = "lifetimeValue", direction = Sort.Direction.DESC) Pageable pageable) {
        return customerService.search(q, status, segment, ownerId, tagId, pageable);
    }

    @GetMapping("/stats")
    public CustomerStats stats() {
        return customerService.stats();
    }

    @Operation(summary = "Lightweight lookup for pickers (orders, tickets)")
    @GetMapping("/lookup")
    public List<CustomerLookup> lookup(@RequestParam(defaultValue = "") String q) {
        return customerService.lookup(q);
    }

    @Operation(summary = "Customer 360 profile")
    @GetMapping("/{id}")
    public Customer360 profile(@PathVariable Long id) {
        return customerService.getProfile(id);
    }

    @Operation(summary = "Check whether an email or phone already belongs to a customer")
    @GetMapping("/check-duplicate")
    public DuplicateCheck checkDuplicate(@RequestParam(required = false) String email,
                                         @RequestParam(required = false) String phone,
                                         @RequestParam(required = false) Long excludeId) {
        return customerService.checkDuplicate(email, phone, excludeId);
    }

    @Operation(summary = "Create customer (409 on duplicate email/phone)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','SALES_EXEC')")
    public CustomerDto create(@Valid @RequestBody CustomerRequest req) {
        return customerService.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SALES_EXEC')")
    public Customer360 update(@PathVariable Long id, @Valid @RequestBody CustomerRequest req) {
        return customerService.update(id, req);
    }

    @Operation(summary = "Change lifecycle status (Lead / Active / Inactive / Churned)")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SALES_EXEC')")
    public CustomerDto changeStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        return customerService.changeStatus(id, req.status());
    }

    @PatchMapping("/{id}/notes")
    @PreAuthorize("hasAnyRole('ADMIN','SALES_EXEC')")
    public void updateNotes(@PathVariable Long id, @Valid @RequestBody NotesRequest req) {
        customerService.updateNotes(id, req.notes());
    }

    @Operation(summary = "Soft delete (Admin only)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        customerService.softDelete(id);
    }

    @Operation(summary = "Export the filtered list as CSV")
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) CustomerStatus status,
                                         @RequestParam(required = false) Segment segment,
                                         @RequestParam(required = false) Long ownerId,
                                         @RequestParam(required = false) Long tagId) {
        byte[] csv = csvService.export(q, status, segment, ownerId, tagId, props.timezone());
        return file(csv, "clientra-customers-" + LocalDate.now(props.timezone()) + ".csv");
    }

    @GetMapping(value = "/import/template", produces = "text/csv")
    @PreAuthorize("hasAnyRole('ADMIN','SALES_EXEC')")
    public ResponseEntity<byte[]> importTemplate() {
        return file(csvService.template(), "clientra-customer-import-template.csv");
    }

    @Operation(summary = "Import customers from CSV and return a validation report")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','SALES_EXEC')")
    public ImportResult importCsv(@RequestParam("file") MultipartFile file) {
        return csvService.importCsv(file);
    }

    private static ResponseEntity<byte[]> file(byte[] body, String name) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(body);
    }
}
