package io.clientra.service;

import io.clientra.dto.CustomerDtos.*;
import io.clientra.entity.Customer;
import io.clientra.entity.Tag;
import io.clientra.entity.User;
import io.clientra.entity.enums.CustomerStatus;
import io.clientra.entity.enums.CustomerType;
import io.clientra.entity.enums.Segment;
import io.clientra.entity.enums.Source;
import io.clientra.exception.ApiException;
import io.clientra.exception.BusinessRuleException;
import io.clientra.repository.CustomerRepository;
import io.clientra.repository.UserRepository;
import io.clientra.repository.spec.CustomerSpecs;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/** CSV import (with a per-row validation report) and CSV export of the filtered customer list. */
@Service
@RequiredArgsConstructor
public class CustomerCsvService {

    static final String[] HEADERS = {"firstName", "lastName", "email", "phone", "companyName", "customerType", "segment",
            "source", "ownerEmail", "tags", "addressLine1", "city", "state", "postalCode"};
    private static final int MAX_ROWS = 2000;

    private final CustomerService customerService;
    private final CustomerRepository customerRepo;
    private final UserRepository userRepo;
    private final AuditService audit;
    private final Validator validator;
    private final TransactionTemplate tx;

    public byte[] template() {
        StringWriter out = new StringWriter();
        try (CSVPrinter p = new CSVPrinter(out, CSVFormat.DEFAULT.builder().setHeader(HEADERS).build())) {
            p.printRecord("Rahul", "Verma", "rahul.verma@example.com", "9930012000", "Verma Foods Pvt Ltd", "BUSINESS", "SMB",
                    "WEBSITE", "karan.mehta@clientra.in", "Wholesale;Hot Lead", "Shop 7, Sector 17, Vashi", "Navi Mumbai", "MH", "400703");
            p.printRecord("Meera", "Iyer", "meera.iyer@example.com", "9833012299", "", "INDIVIDUAL", "RETAIL",
                    "REFERRAL", "", "", "", "Pune", "MH", "411001");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Each row is validated and saved in its own transaction, so one bad row never blocks the rest. */
    @CacheEvict(cacheNames = {"dashboard-kpis", "report-summary"}, allEntries = true)
    public ImportResult importCsv(MultipartFile file) {
        if (file.isEmpty()) throw new BusinessRuleException("The uploaded file is empty");
        List<ImportError> errors = new ArrayList<>();
        int total = 0;
        int imported = 0;
        Map<String, Long> ownersByEmail = userRepo.findByActiveTrueOrderByFullName().stream()
                .collect(Collectors.toMap(u -> u.getEmail().toLowerCase(), User::getId));
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                     .setIgnoreHeaderCase(true).setTrim(true).setIgnoreEmptyLines(true).build().parse(reader)) {
            Set<String> headers = parser.getHeaderMap().keySet().stream().map(String::toLowerCase).collect(Collectors.toSet());
            for (String required : List.of("firstname", "email")) {
                if (!headers.contains(required)) {
                    throw new BusinessRuleException("CSV must have a '" + required + "' column. Download the template for the expected format.");
                }
            }
            for (CSVRecord r : parser) {
                total++;
                int row = (int) r.getRecordNumber() + 1;
                if (total > MAX_ROWS) {
                    errors.add(new ImportError(row, null, "Row limit of " + MAX_ROWS + " reached; remaining rows skipped"));
                    break;
                }
                String email = get(r, "email");
                try {
                    CustomerRequest req = toRequest(r, ownersByEmail);
                    Set<ConstraintViolation<CustomerRequest>> violations = validator.validate(req);
                    if (!violations.isEmpty()) {
                        errors.add(new ImportError(row, email, violations.iterator().next().getMessage()));
                        continue;
                    }
                    tx.executeWithoutResult(s -> customerService.createEntity(req, "IMPORTED"));
                    imported++;
                } catch (ApiException e) {
                    errors.add(new ImportError(row, email, e.getMessage()));
                } catch (IllegalArgumentException e) {
                    errors.add(new ImportError(row, email, e.getMessage()));
                }
            }
        } catch (IOException | UncheckedIOException e) {
            throw new BusinessRuleException("Could not read the CSV file: " + e.getMessage());
        }
        final int importedCount = imported;
        if (imported > 0) {
            tx.executeWithoutResult(s -> audit.log("CUSTOMER", 0L, null, "BULK_IMPORT",
                    "Imported " + importedCount + " customers from " + file.getOriginalFilename()));
        }
        return new ImportResult(total, imported, total - imported, errors);
    }

    private CustomerRequest toRequest(CSVRecord r, Map<String, Long> owners) {
        CustomerType type = parseEnum(CustomerType.class, get(r, "customerType"), "customerType");
        String ownerEmail = get(r, "ownerEmail");
        Long ownerId = null;
        if (ownerEmail != null) {
            ownerId = owners.get(ownerEmail.toLowerCase());
            if (ownerId == null) throw new IllegalArgumentException("Owner " + ownerEmail + " is not an active user");
        }
        String line1 = get(r, "addressLine1");
        String city = get(r, "city");
        List<AddressDto> addresses = line1 == null && city == null ? List.of()
                : List.of(new AddressDto(null, io.clientra.entity.enums.AddressType.BILLING, line1 != null ? line1 : city, null,
                city, get(r, "state"), get(r, "postalCode"), "India", true));
        String tags = get(r, "tags");
        return new CustomerRequest(get(r, "firstName"), get(r, "lastName"), get(r, "email"), get(r, "phone"),
                get(r, "companyName"), null, type != null ? type : (get(r, "companyName") != null ? CustomerType.BUSINESS : CustomerType.INDIVIDUAL),
                parseEnum(Segment.class, get(r, "segment"), "segment"), parseEnum(Source.class, get(r, "source"), "source"),
                ownerId, addresses, tags == null ? List.of() : Arrays.stream(tags.split("[;|]")).map(String::trim).filter(s -> !s.isEmpty()).toList(),
                null, false, null);
    }

    private static String get(CSVRecord r, String column) {
        for (String h : r.getParser().getHeaderNames()) {
            if (h.equalsIgnoreCase(column)) {
                String v = r.get(h);
                return v == null || v.isBlank() ? null : v.trim();
            }
        }
        return null;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String column) {
        if (value == null) return null;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase().replace(' ', '_').replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + column + " '" + value + "'. Allowed: "
                    + Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.joining(", ")));
        }
    }

    @Transactional(readOnly = true)
    public byte[] export(String q, CustomerStatus status, Segment segment, Long ownerId, Long tagId, ZoneId zone) {
        Specification<Customer> spec = Specification.where(CustomerSpecs.notDeleted()).and(CustomerSpecs.text(q))
                .and(CustomerSpecs.status(status)).and(CustomerSpecs.segment(segment))
                .and(CustomerSpecs.owner(ownerId)).and(CustomerSpecs.tag(tagId));
        List<Customer> customers = customerRepo.findAll(spec, Sort.by("customerCode"));
        DateTimeFormatter date = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(zone);
        StringWriter out = new StringWriter();
        out.write('﻿'); // BOM so Excel opens UTF-8 (₹, names) correctly
        try (CSVPrinter p = new CSVPrinter(out, CSVFormat.DEFAULT.builder().setHeader("Customer Code", "First Name", "Last Name",
                "Email", "Phone", "Company", "Type", "Segment", "Status", "Source", "Owner", "Lifetime Value (INR)", "Churn Risk",
                "Tags", "City", "Customer Since").build())) {
            for (Customer c : customers) {
                String city = c.getAddresses().isEmpty() ? "" : Objects.toString(c.getAddresses().get(0).getCity(), "");
                p.printRecord(c.getCustomerCode(), c.getFirstName(), Objects.toString(c.getLastName(), ""), c.getEmail(),
                        Objects.toString(c.getPhone(), ""), Objects.toString(c.getCompanyName(), ""), c.getCustomerType(),
                        c.getSegment(), c.getStatus(), Objects.toString(c.getSource(), ""),
                        c.getOwner() != null ? c.getOwner().getFullName() : "", c.getLifetimeValue().toPlainString(),
                        Objects.toString(c.getChurnScore(), ""),
                        c.getTags().stream().map(Tag::getName).sorted().collect(Collectors.joining(";")), city,
                        c.getCreatedAt() != null ? date.format(c.getCreatedAt()) : "");
            }
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "EXPORT_FAILED", "Could not build CSV");
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }
}
