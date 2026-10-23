package io.clientra.config;

import io.clientra.entity.*;
import io.clientra.entity.enums.*;
import io.clientra.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

/**
 * Seeds a small, realistic demo data set on first start (empty users table).
 * The named records mirror the screens in the FA-I / FA-II prototype.
 * Demo password for every account: Clientra@123
 */
@Slf4j
@Component
@Profile("!test")
@org.springframework.core.annotation.Order(1)
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

    public static final String DEMO_PASSWORD = "Clientra@123";

    private final AppProperties props;
    private final Clock clock;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate tx;
    private final JdbcTemplate jdbc;
    private final UserRepository userRepo;
    private final TagRepository tagRepo;
    private final ProductRepository productRepo;
    private final CustomerRepository customerRepo;
    private final InteractionRepository interactionRepo;
    private final OrderRepository orderRepo;
    private final SupportTicketRepository ticketRepo;
    private final ActivityLogRepository logRepo;

    private final Random random = new Random(42);
    private final List<Object[]> backdates = new ArrayList<>();
    private ZoneId zone;
    private LocalDate today;

    private User admin, neha, karan, arjun, ritu, sana;
    private final Map<String, Tag> tags = new HashMap<>();
    private final Map<String, Product> products = new LinkedHashMap<>();

    @Override
    public void run(ApplicationArguments args) {
        if (!props.seed().enabled() || userRepo.count() > 0) {
            return;
        }
        zone = props.timezone();
        today = LocalDate.now(clock.withZone(zone));
        tx.executeWithoutResult(status -> seed());
        backdates.forEach(b -> jdbc.update("UPDATE " + b[0] + " SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.from((Instant) b[2]), b[1]));
        log.info("Demo data seeded: {} users, {} customers, {} orders, {} tickets. Password for all demo users: {}",
                userRepo.count(), customerRepo.count(), orderRepo.count(), ticketRepo.count(), DEMO_PASSWORD);
    }

    private void seed() {
        seedUsers();
        seedTags();
        seedProducts();
        Map<String, Customer> named = seedNamedCustomers();
        List<Customer> generated = seedGeneratedCustomers();
        seedNamedActivity(named);
        seedGeneratedActivity(generated);
        seedTickets(named, generated);
        recalculateLifetimeValues();
    }

    // ---------------------------------------------------------------- users, tags, products

    private void seedUsers() {
        admin = user("Aditi Kulkarni", "admin@clientra.in", Role.ADMIN, "Administrator");
        neha = user("Neha Sharma", "neha.sharma@clientra.in", Role.ADMIN, "Sales Manager");
        karan = user("Karan Mehta", "karan.mehta@clientra.in", Role.SALES_EXEC, "Sales Executive");
        arjun = user("Arjun Nair", "arjun.nair@clientra.in", Role.SALES_EXEC, "Sales Executive");
        ritu = user("Ritu Desai", "ritu.desai@clientra.in", Role.SUPPORT_AGENT, "Support Agent");
        sana = user("Sana Qureshi", "sana.qureshi@clientra.in", Role.SUPPORT_AGENT, "Support Agent");
    }

    private User user(String name, String email, Role role, String title) {
        User u = new User();
        u.setFullName(name);
        u.setEmail(email);
        u.setRole(role);
        u.setTitle(title);
        u.setPhone("98" + (10000000 + random.nextInt(89999999)));
        u.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        return userRepo.save(u);
    }

    private void seedTags() {
        for (String[] t : new String[][]{
                {"VIP", "#7C3AED"}, {"Wholesale", "#14776B"}, {"Hot Lead", "#7C3AED"},
                {"Distributor", "#2563EB"}, {"Festive Buyer", "#D97706"}, {"Credit Hold", "#DC2626"}}) {
            tags.put(t[0], tagRepo.save(new Tag(t[0], t[1])));
        }
    }

    private void seedProducts() {
        Object[][] rows = {
                {"GRN-BAS-25", "Basmati Rice 25kg", "Grains", "2400", "5"},
                {"OIL-SUN-15", "Sunflower Oil 15L", "Edible Oil", "2250", "5"},
                {"PUL-TOR-30", "Toor Dal 30kg", "Pulses", "3750", "5"},
                {"GRN-WHT-50", "Wheat Flour 50kg", "Grains", "1850", "5"},
                {"SWT-SUG-50", "Sugar 50kg", "Sweeteners", "2100", "5"},
                {"SPC-TUR-05", "Turmeric Powder 5kg", "Spices", "950", "5"},
                {"BEV-TEA-10", "Premium Assam Tea 10kg", "Beverages", "4300", "5"},
                {"TEX-COT-50", "Cotton Blend Fabric (50m roll)", "Textiles", "5200", "12"},
                {"TEX-POL-50", "Polyester Fabric (50m roll)", "Textiles", "4100", "12"},
                {"TEX-LIN-30", "Linen Fabric (30m roll)", "Textiles", "6800", "12"},
                {"PKG-CRT-100", "Packaging Cartons (100 pcs)", "Packaging", "1500", "18"},
                {"ELC-LED-20", "LED Panel Light 20W (box of 10)", "Electricals", "3200", "18"},
                {"ELC-WIR-90", "Copper Wire 90m Coil", "Electricals", "2650", "18"},
                {"SPC-CHL-05", "Red Chilli Powder 5kg", "Spices", "1100", "5"},
        };
        for (Object[] r : rows) {
            Product p = new Product();
            p.setSku((String) r[0]);
            p.setName((String) r[1]);
            p.setCategory((String) r[2]);
            p.setUnitPrice(new BigDecimal((String) r[3]));
            p.setGstRate(new BigDecimal((String) r[4]));
            products.put(p.getSku(), productRepo.save(p));
        }
        Product retired = new Product();
        retired.setSku("OIL-GRN-05");
        retired.setName("Groundnut Oil 5L (discontinued)");
        retired.setCategory("Edible Oil");
        retired.setUnitPrice(new BigDecimal("980"));
        retired.setActive(false);
        productRepo.save(retired);
    }

    // ---------------------------------------------------------------- customers

    private Map<String, Customer> seedNamedCustomers() {
        Map<String, Customer> m = new LinkedHashMap<>();
        m.put("arvind", customer("Arvind", "Rao", "arvind@raotextiles.com", "9820041000", "Rao Textiles Pvt Ltd", "Purchase Head",
                CustomerType.BUSINESS, Segment.ENTERPRISE, CustomerStatus.ACTIVE, Source.REFERRAL, neha,
                daysAgo(590), "Plot 14, MIDC", "Bhiwandi", "MH", "421302", "VIP", "Wholesale"));
        m.put("meera", customer("Meera", "Pillai", "meera.p@gmail.com", "9833012233", null, null,
                CustomerType.INDIVIDUAL, Segment.RETAIL, CustomerStatus.ACTIVE, Source.WEBSITE, karan,
                daysAgo(300), "B-302, Lake View Apartments, Powai", "Mumbai", "MH", "400076"));
        m.put("nexa", customer("Nexa", "Logistics", "procurement@nexa.in", "9867455120", "Nexa Logistics LLP", "Procurement Desk",
                CustomerType.BUSINESS, Segment.SMB, CustomerStatus.LEAD, Source.CAMPAIGN, neha,
                daysAgo(6), "Unit 9, Turbhe MIDC", "Navi Mumbai", "MH", "400705", "Hot Lead"));
        m.put("sameer", customer("Sameer", "Joshi", "sameer.j@outlook.com", "9821177345", "Joshi Electricals", "Owner",
                CustomerType.BUSINESS, Segment.SMB, CustomerStatus.ACTIVE, Source.WALK_IN, karan,
                daysAgo(410), "Shop 3, Station Road", "Thane", "MH", "400601"));
        m.put("farah", customer("Farah", "Khan", "farah.k@yahoo.com", "9930456781", null, null,
                CustomerType.INDIVIDUAL, Segment.RETAIL, CustomerStatus.INACTIVE, Source.WEBSITE, ritu,
                daysAgo(380), "12 Hill Road, Bandra West", "Mumbai", "MH", "400050"));
        m.put("vikram", customer("Vikram", "Gupta", "vikram@greenmart.co", "9819023456", "GreenMart Stores", "Category Manager",
                CustomerType.BUSINESS, Segment.ENTERPRISE, CustomerStatus.CHURNED, Source.REFERRAL, neha,
                daysAgo(700), "GreenMart House, LBS Marg", "Ghatkopar", "MH", "400086"));
        m.put("rahul", customer("Rahul", "Verma", "rahul@vermafoods.in", "9930012000", "Verma Foods Pvt Ltd", "Director",
                CustomerType.BUSINESS, Segment.SMB, CustomerStatus.LEAD, Source.WEBSITE, karan,
                daysAgo(11), "Shop 7, Sector 17, Vashi", "Navi Mumbai", "MH", "400703", "Wholesale", "Hot Lead"));
        m.put("priyanka", customer("Priyanka", "Nair", "priyanka@nairexports.com", "9845098765", "Nair Exports", "Founder",
                CustomerType.BUSINESS, Segment.ENTERPRISE, CustomerStatus.ACTIVE, Source.REFERRAL, arjun,
                daysAgo(520), "45 MG Road", "Kochi", "KL", "682016", "VIP"));
        m.put("imran", customer("Imran", "Shaikh", "imran@shaikhtraders.in", "9867001122", "Shaikh Traders", "Partner",
                CustomerType.BUSINESS, Segment.SMB, CustomerStatus.ACTIVE, Source.WALK_IN, arjun,
                daysAgo(260), "Gala 21, Masjid Bunder", "Mumbai", "MH", "400009", "Distributor"));
        m.put("kavita", customer("Kavita", "Menon", "kavita.menon@menonmart.in", "9820776655", "Menon Mart", "Owner",
                CustomerType.BUSINESS, Segment.SMB, CustomerStatus.INACTIVE, Source.CAMPAIGN, karan,
                daysAgo(450), "22 Linking Road, Khar", "Mumbai", "MH", "400052"));
        m.put("rohit", customer("Rohit", "Bansal", "rohit.bansal@gmail.com", "9811223344", null, null,
                CustomerType.INDIVIDUAL, Segment.RETAIL, CustomerStatus.ACTIVE, Source.WEBSITE, arjun,
                daysAgo(140), "C-14, Sector 50", "Noida", "UP", "201301", "Festive Buyer"));
        return m;
    }

    private List<Customer> seedGeneratedCustomers() {
        String[] first = {"Ananya", "Rajesh", "Pooja", "Suresh", "Deepa", "Manoj", "Sneha", "Aakash", "Lakshmi", "Varun",
                "Nisha", "Harish", "Swati", "Gaurav", "Ishita", "Prakash", "Ritika", "Naveen", "Shalini", "Kunal",
                "Divya", "Amit", "Tanvi", "Siddharth", "Asha", "Yash", "Bhavna", "Rakesh", "Megha", "Nikhil",
                "Zoya", "Tarun", "Pallavi", "Vivek", "Anjali", "Omkar", "Ritesh", "Shreya", "Mohit"};
        String[] last = {"Iyer", "Patel", "Deshpande", "Reddy", "Chopra", "Kapoor", "Bhatt", "Saxena", "Malhotra", "Pandey",
                "Agarwal", "Sinha", "Chauhan", "Kulkarni", "Banerjee", "Mishra", "Thakur", "Jain", "Shetty", "Naik"};
        String[] companies = {"Sunrise Traders", "Om Sai Distributors", "Pinnacle Retail", "Shree Ganesh Stores", "BlueLeaf Foods",
                "Metro Kirana Mart", "Vardhman Textiles", "Ekta Enterprises", "Coastal Spices Co", "Prime Electricals",
                "Harmony Hotels", "UrbanBasket", "Royal Caterers", "Fresh Farm Supplies", "Kaveri Agencies"};
        String[][] cities = {{"Pune", "MH", "411001"}, {"Mumbai", "MH", "400001"}, {"Bengaluru", "KA", "560001"},
                {"Ahmedabad", "GJ", "380009"}, {"Hyderabad", "TS", "500001"}, {"Chennai", "TN", "600002"},
                {"Nashik", "MH", "422001"}, {"Surat", "GJ", "395003"}, {"Jaipur", "RJ", "302001"}, {"Indore", "MP", "452001"}};
        User[] owners = {neha, karan, karan, arjun, arjun, neha};
        List<Customer> result = new ArrayList<>();
        for (int i = 0; i < first.length; i++) {
            String fn = first[i];
            String ln = last[random.nextInt(last.length)];
            boolean business = random.nextInt(100) < 55;
            String company = business ? companies[random.nextInt(companies.length)] : null;
            int roll = random.nextInt(100);
            CustomerStatus status = roll < 22 ? CustomerStatus.LEAD : roll < 72 ? CustomerStatus.ACTIVE
                    : roll < 88 ? CustomerStatus.INACTIVE : CustomerStatus.CHURNED;
            Segment segment = business ? (random.nextInt(100) < 25 ? Segment.ENTERPRISE : Segment.SMB)
                    : (random.nextInt(100) < 85 ? Segment.RETAIL : Segment.OTHER);
            Source source = Source.values()[random.nextInt(Source.values().length)];
            int age = status == CustomerStatus.LEAD ? 1 + random.nextInt(45) : 40 + random.nextInt(560);
            String[] city = cities[random.nextInt(cities.length)];
            String email = (fn + "." + ln).toLowerCase() + (business ? "@" + company.toLowerCase().replaceAll("[^a-z]", "") + ".in" : "@gmail.com");
            String phone = "9" + (100000000 + random.nextInt(899999999));
            String[] tagNames = random.nextInt(100) < 20 ? new String[]{randomTag()} : new String[0];
            Customer c = customer(fn, ln, email, phone, company, business ? "Purchase Manager" : null,
                    business ? CustomerType.BUSINESS : CustomerType.INDIVIDUAL, segment, status, source,
                    owners[random.nextInt(owners.length)], daysAgo(age),
                    (10 + random.nextInt(180)) + ", " + randomStreet(), city[0], city[1], city[2], tagNames);
            result.add(c);
        }
        return result;
    }

    private Customer customer(String first, String last, String email, String phone, String company, String jobTitle,
                              CustomerType type, Segment segment, CustomerStatus status, Source source, User owner,
                              Instant createdAt, String line1, String city, String state, String pin, String... tagNames) {
        Customer c = new Customer();
        c.setFirstName(first);
        c.setLastName(last);
        c.setEmail(email);
        c.setPhone(phone);
        c.setCompanyName(company);
        c.setJobTitle(jobTitle);
        c.setCustomerType(type);
        c.setSegment(segment);
        c.setStatus(status);
        c.setSource(source);
        c.setOwner(owner);
        Address a = new Address();
        a.setAddressType(AddressType.BILLING);
        a.setLine1(line1);
        a.setCity(city);
        a.setState(state);
        a.setPostalCode(pin);
        a.setPrimary(true);
        c.addAddress(a);
        for (String t : tagNames) {
            c.getTags().add(tags.get(t));
        }
        c = customerRepo.save(c);
        c.setCustomerCode(String.format("CUST-%05d", c.getId()));
        backdate("customers", c.getId(), createdAt);
        audit(owner, "CUSTOMER", c.getId(), c.getId(), "CREATED", "Customer created as " + status.name().toLowerCase(), createdAt);
        return c;
    }

    // ---------------------------------------------------------------- interactions & orders

    private void seedNamedActivity(Map<String, Customer> m) {
        Customer arvind = m.get("arvind");
        interaction(arvind, neha, InteractionType.CALL, "Quarterly bulk order discussion",
                "Interested in 500 m cotton blend at revised rate; requested quote by Friday.",
                at(0, 10, 15), at(-7, 11, 0));
        interaction(arvind, neha, InteractionType.EMAIL, "Festive season price list shared", null, at(32, 16, 20), null);
        interaction(arvind, neha, InteractionType.MEETING, "Annual contract review at Bhiwandi office",
                "Discussed volume slabs for FY27 and credit period.", at(70, 12, 0), null);
        interaction(arvind, neha, InteractionType.CALL, "Follow-up on revised quote", null, at(3, 9, 30), at(0, 11, 30));
        audit(admin, "CUSTOMER", arvind.getId(), arvind.getId(), "TAG_ADDED", "Tag “VIP” added, segment changed to Enterprise", at(38, 10, 0));

        interaction(m.get("meera"), karan, InteractionType.QUOTE, "Quote for monthly grocery subscription", null, at(2, 15, 10), at(0, 14, 0));
        interaction(m.get("nexa"), neha, InteractionType.DEMO, "Product demo for warehouse procurement team",
                "Scheduled online demo of bulk catalogue.", at(4, 17, 0), at(0, 16, 30));
        interaction(m.get("nexa"), neha, InteractionType.EMAIL, "Introductory email with catalogue", null, at(6, 11, 20), null);
        interaction(m.get("rahul"), karan, InteractionType.CALL, "Discovery call - wholesale grocery needs", null, at(10, 12, 40), at(-2, 12, 0));
        interaction(m.get("sameer"), karan, InteractionType.MEETING, "Bulk pricing renegotiation", null, at(18, 15, 0), null);
        interaction(m.get("farah"), ritu, InteractionType.NOTE, "Customer requested refund for cancelled order", null, at(72, 10, 0), null);
        interaction(m.get("vikram"), neha, InteractionType.CALL, "Win-back call - competitor pricing", "Moved to a cheaper distributor.", at(117, 16, 40), null);
        interaction(m.get("priyanka"), arjun, InteractionType.MEETING, "Export packaging requirements", null, at(14, 12, 5), at(-3, 15, 0));
        interaction(m.get("imran"), arjun, InteractionType.CALL, "Monthly restock call", null, at(22, 15, 45), null);
        interaction(m.get("kavita"), karan, InteractionType.EMAIL, "Re-engagement offer sent", null, at(101, 10, 0), at(1, 10, 30));
        interaction(m.get("rohit"), arjun, InteractionType.CALL, "Diwali hamper enquiry", null, at(23, 13, 10), null);

        // Orders: Arvind is the long-standing enterprise account
        String[][] arvindBaskets = {{"TEX-COT-50:6", "TEX-POL-50:4"}, {"TEX-LIN-30:5"}, {"TEX-COT-50:8", "PKG-CRT-100:2"},
                {"TEX-POL-50:10"}, {"TEX-COT-50:5", "TEX-LIN-30:2"}, {"TEX-COT-50:12"}, {"TEX-POL-50:6", "TEX-LIN-30:3"}};
        for (int i = 0; i < arvindBaskets.length; i++) {
            order(arvind, i % 2 == 0 ? neha : karan, at(190 - i * 26, 11, 0), PaymentStatus.PAID, OrderStatus.DELIVERED, arvindBaskets[i]);
        }
        order(arvind, karan, at(21, 12, 30), PaymentStatus.PAID, OrderStatus.SHIPPED, "TEX-COT-50:7", "TEX-POL-50:4", "TEX-LIN-30:2", "PKG-CRT-100:2");

        order(m.get("meera"), karan, at(140, 18, 0), PaymentStatus.PAID, OrderStatus.DELIVERED, "GRN-BAS-25:2", "OIL-SUN-15:3");
        order(m.get("meera"), karan, at(65, 18, 0), PaymentStatus.PAID, OrderStatus.DELIVERED, "BEV-TEA-10:1", "SPC-TUR-05:4");
        order(m.get("meera"), karan, at(12, 17, 30), PaymentStatus.PAID, OrderStatus.SHIPPED, "SPC-TUR-05:2", "SPC-CHL-05:2", "GRN-WHT-50:0");

        order(m.get("sameer"), karan, at(200, 11, 0), PaymentStatus.PAID, OrderStatus.DELIVERED, "ELC-LED-20:6", "ELC-WIR-90:5");
        order(m.get("sameer"), karan, at(95, 11, 0), PaymentStatus.PAID, OrderStatus.DELIVERED, "ELC-WIR-90:8");
        order(m.get("sameer"), karan, at(24, 11, 0), PaymentStatus.PAID, OrderStatus.DELIVERED, "ELC-LED-20:4", "ELC-WIR-90:2");

        order(m.get("farah"), karan, at(240, 13, 0), PaymentStatus.PAID, OrderStatus.DELIVERED, "GRN-BAS-25:1", "SWT-SUG-50:1", "SPC-TUR-05:4");
        order(m.get("farah"), karan, at(31, 13, 0), PaymentStatus.REFUNDED, OrderStatus.CANCELLED, "SWT-SUG-50:1");

        order(m.get("vikram"), neha, at(330, 10, 0), PaymentStatus.PAID, OrderStatus.DELIVERED, "GRN-BAS-25:20", "OIL-SUN-15:20");
        order(m.get("vikram"), neha, at(250, 10, 0), PaymentStatus.PAID, OrderStatus.DELIVERED, "PUL-TOR-30:10", "GRN-WHT-50:12");
        order(m.get("vikram"), neha, at(117, 10, 0), PaymentStatus.PAID, OrderStatus.DELIVERED, "GRN-BAS-25:10", "BEV-TEA-10:6");

        order(m.get("rahul"), karan, at(11, 15, 0), PaymentStatus.PENDING, OrderStatus.CONFIRMED, "GRN-BAS-25:4", "OIL-SUN-15:5", "PUL-TOR-30:1");

        order(m.get("priyanka"), arjun, at(160, 12, 0), PaymentStatus.PAID, OrderStatus.DELIVERED, "TEX-LIN-30:8", "PKG-CRT-100:6");
        order(m.get("priyanka"), arjun, at(75, 12, 0), PaymentStatus.PAID, OrderStatus.DELIVERED, "TEX-COT-50:9", "TEX-LIN-30:4");
        order(m.get("priyanka"), arjun, at(14, 12, 0), PaymentStatus.PAID, OrderStatus.DELIVERED, "TEX-LIN-30:6", "PKG-CRT-100:4");

        order(m.get("imran"), arjun, at(120, 14, 0), PaymentStatus.PAID, OrderStatus.DELIVERED, "GRN-BAS-25:5", "PUL-TOR-30:3", "SWT-SUG-50:4");
        order(m.get("imran"), arjun, at(27, 14, 0), PaymentStatus.PAID, OrderStatus.DELIVERED, "GRN-BAS-25:4", "PUL-TOR-30:2", "OIL-SUN-15:2", "SPC-TUR-05:3", "BEV-TEA-10:1");

        order(m.get("kavita"), karan, at(210, 16, 0), PaymentStatus.PAID, OrderStatus.DELIVERED, "GRN-WHT-50:10", "SWT-SUG-50:8");
        order(m.get("kavita"), karan, at(101, 16, 0), PaymentStatus.PAID, OrderStatus.DELIVERED, "OIL-SUN-15:6");

        order(m.get("rohit"), arjun, at(60, 19, 0), PaymentStatus.PAID, OrderStatus.DELIVERED, "BEV-TEA-10:2", "SPC-CHL-05:3");
        order(m.get("rohit"), arjun, at(5, 19, 0), PaymentStatus.PENDING, OrderStatus.CONFIRMED, "BEV-TEA-10:1", "GRN-BAS-25:1");
    }

    private void seedGeneratedActivity(List<Customer> customers) {
        String[] skus = products.keySet().toArray(String[]::new);
        InteractionType[] types = {InteractionType.CALL, InteractionType.EMAIL, InteractionType.MEETING, InteractionType.NOTE};
        String[] subjects = {"Introductory call", "Price list shared", "Requirement discussion", "Payment reminder",
                "Feedback on last delivery", "New catalogue walkthrough", "Credit terms discussion", "Restock check-in"};
        for (Customer c : customers) {
            long ageDays = Duration.between(createdOf(c), Instant.now(clock)).toDays();
            int interactions = 1 + random.nextInt(4);
            for (int i = 0; i < interactions; i++) {
                int d = (int) Math.max(0, random.nextLong(Math.max(1, ageDays)));
                Instant followUp = random.nextInt(100) < 15 ? at(-(1 + random.nextInt(10)), 10 + random.nextInt(7), 0) : null;
                interaction(c, c.getOwner(), types[random.nextInt(types.length)], subjects[random.nextInt(subjects.length)],
                        null, at(d, 9 + random.nextInt(9), random.nextInt(4) * 15), followUp);
            }
            if (c.getStatus() == CustomerStatus.LEAD) {
                continue;
            }
            int orders = c.getStatus() == CustomerStatus.ACTIVE ? 2 + random.nextInt(4) : 1 + random.nextInt(3);
            int maxAge = (int) Math.min(ageDays, 330);
            int minAge = c.getStatus() == CustomerStatus.ACTIVE ? 0 : 100;
            for (int i = 0; i < orders; i++) {
                int d = minAge + random.nextInt(Math.max(1, maxAge - minAge));
                int lines = 1 + random.nextInt(3);
                String[] basket = new String[lines];
                for (int l = 0; l < lines; l++) {
                    basket[l] = skus[random.nextInt(skus.length)] + ":" + (1 + random.nextInt(c.getSegment() == Segment.RETAIL ? 3 : 10));
                }
                int roll = random.nextInt(100);
                PaymentStatus ps = d < 10 && roll < 40 ? PaymentStatus.PENDING : roll < 4 ? PaymentStatus.REFUNDED : PaymentStatus.PAID;
                OrderStatus os = ps == PaymentStatus.REFUNDED ? OrderStatus.CANCELLED
                        : d < 4 ? OrderStatus.CONFIRMED : d < 9 ? OrderStatus.SHIPPED : OrderStatus.DELIVERED;
                order(c, c.getOwner().getRole() == Role.SUPPORT_AGENT ? karan : c.getOwner(), at(d, 10 + random.nextInt(8), 0), ps, os, basket);
            }
        }
    }

    private void interaction(Customer c, User u, InteractionType type, String subject, String notes, Instant at, Instant followUp) {
        Interaction i = new Interaction();
        i.setCustomer(c);
        i.setUser(u);
        i.setType(type);
        i.setSubject(subject);
        i.setNotes(notes);
        i.setInteractionAt(at);
        i.setFollowUpAt(followUp);
        i.setFollowUpDone(followUp != null && followUp.isBefore(at(0, 0, 0)));
        interactionRepo.save(i);
    }

    private void order(Customer c, User by, Instant date, PaymentStatus payment, OrderStatus status, String... lines) {
        Order o = new Order();
        o.setCustomer(c);
        o.setCreatedBy(by);
        o.setOrderDate(date);
        o.setStatus(status);
        o.setPaymentStatus(payment);
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        for (String line : lines) {
            String[] parts = line.split(":");
            int qty = Integer.parseInt(parts[1]);
            if (qty <= 0) continue;
            Product p = products.get(parts[0]);
            OrderItem item = new OrderItem();
            item.setProduct(p);
            item.setQuantity(qty);
            item.setUnitPrice(p.getUnitPrice());
            BigDecimal lineTotal = p.getUnitPrice().multiply(BigDecimal.valueOf(qty));
            item.setLineTotal(lineTotal);
            o.addItem(item);
            total = total.add(lineTotal);
            tax = tax.add(includedGst(lineTotal, p.getGstRate()));
        }
        o.setTotalAmount(total);
        o.setTaxAmount(tax);
        if (payment != PaymentStatus.PENDING) {
            o.setPaidAt(date.plus(Duration.ofHours(2 + random.nextInt(30))));
        }
        o = orderRepo.save(o);
        o.setOrderNumber(String.format("ORD-%d-%04d", LocalDate.ofInstant(date, zone).getYear(), o.getId()));
    }

    private static BigDecimal includedGst(BigDecimal gross, BigDecimal ratePercent) {
        BigDecimal divisor = BigDecimal.ONE.add(ratePercent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        return gross.subtract(gross.divide(divisor, 2, RoundingMode.HALF_UP));
    }

    // ---------------------------------------------------------------- tickets

    private void seedTickets(Map<String, Customer> m, List<Customer> generated) {
        List<Customer> pool = new ArrayList<>(generated.stream().filter(c -> c.getStatus() != CustomerStatus.LEAD).toList());
        String[][] historic = {
                {"Short delivery of 2 cartons", "DELIVERY"}, {"Invoice amount mismatch", "BILLING"},
                {"Damaged packaging on arrival", "PRODUCT_QUALITY"}, {"Unable to log in to portal", "ACCOUNT"},
                {"Late delivery to warehouse", "DELIVERY"}, {"GST invoice not received", "BILLING"},
                {"Wrong product variant shipped", "PRODUCT_QUALITY"}, {"Change of delivery address", "ACCOUNT"},
                {"Quality issue in rice batch", "PRODUCT_QUALITY"}, {"Delivery slot rescheduling", "DELIVERY"},
                {"Credit note request", "BILLING"}, {"Duplicate charge on payment", "BILLING"},
                {"Courier did not call before delivery", "DELIVERY"}, {"Stale stock received", "PRODUCT_QUALITY"}};
        Customer arvind = m.get("arvind");
        Order arvindOrder = orderRepo.findByCustomerIdOrderByOrderDateDesc(arvind.getId()).get(0);

        // Ticket ids start at 1170; the prototype tickets keep the numbers shown on the FA screens.
        int h = 0;
        for (int number = 1170; number <= 1192; number++) {
            switch (number) {
                case 1171 -> ticket(m.get("meera"), null, sana, "Password reset", "Customer cannot reset the online ordering portal password.",
                        TicketChannel.CHAT, TicketCategory.ACCOUNT, TicketPriority.LOW, TicketStatus.RESOLVED, at(2, 15, 0), 25 / 60.0, List.of());
                case 1176 -> ticket(m.get("vikram"), null, ritu, "Delivery delayed", "Consignment for GreenMart Ghatkopar delayed by 3 days.",
                        TicketChannel.PHONE, TicketCategory.DELIVERY, TicketPriority.HIGH, TicketStatus.RESOLVED, at(3, 10, 0), 3.2, List.of());
                case 1179 -> ticket(m.get("nexa"), null, sana, "Need GST details update", "Please update GSTIN on our account before the first order.",
                        TicketChannel.EMAIL, TicketCategory.ACCOUNT, TicketPriority.LOW, TicketStatus.AWAITING_CUSTOMER, minutesAgo(26 * 60), -1, List.of(
                                new String[]{"customer", "Our GSTIN has changed after the LLP conversion. Please update it on the account."},
                                new String[]{"agent", "Sure - could you share the new GST certificate so we can verify it?"}));
                case 1185 -> ticket(m.get("sameer"), null, ritu, "Bulk pricing not applied", "Slab discount for 10+ coils was not applied on the last invoice.",
                        TicketChannel.CHAT, TicketCategory.BILLING, TicketPriority.MEDIUM, TicketStatus.IN_PROGRESS, minutesAgo(10 * 60), -1, List.of(
                                new String[]{"customer", "The 8% slab discount is missing on the invoice for my last order."},
                                new String[]{"agent", "Thanks Sameer, checking with billing and will revert shortly."}));
                case 1187 -> ticket(arvind, arvindOrder, ritu, "Wrong shade delivered (3 rolls)",
                        "Received 3 rolls of ‘Sea Green’ instead of ‘Teal Blue’.",
                        TicketChannel.PHONE, TicketCategory.PRODUCT_QUALITY, TicketPriority.HIGH, TicketStatus.IN_PROGRESS, minutesAgo(138), -1, List.of(
                                new String[]{"customer", "Received 3 rolls of ‘Sea Green’ instead of ‘Teal Blue’ in order "
                                        + arvindOrder.getOrderNumber() + ". Need replacement before Friday."},
                                new String[]{"agent", "Apologies, Mr. Rao. Warehouse has confirmed the mix-up. Replacement rolls are being dispatched today; pickup of the wrong rolls is scheduled."},
                                new String[]{"internal", "Courier AWB 77823410. Offered 5% credit on next order as goodwill."}));
                case 1188 -> ticket(m.get("imran"), null, sana, "Duplicate debit on UPI payment", "Amount debited twice for the same order.",
                        TicketChannel.WEB, TicketCategory.BILLING, TicketPriority.HIGH, TicketStatus.OPEN, minutesAgo(30), -1, List.of());
                case 1190 -> ticket(m.get("farah"), null, ritu, "Refund for cancelled order", "Refund for the cancelled sugar order has not been received.",
                        TicketChannel.PHONE, TicketCategory.BILLING, TicketPriority.MEDIUM, TicketStatus.OPEN, minutesAgo(240), -1, List.of());
                case 1191 -> ticket(m.get("priyanka"), null, null, "Export documentation copy needed", "Need certified copies of the last two invoices.",
                        TicketChannel.EMAIL, TicketCategory.OTHER, TicketPriority.LOW, TicketStatus.OPEN, minutesAgo(15 * 60), -1, List.of());
                case 1192 -> ticket(m.get("meera"), null, sana, "Unable to download invoice", "Invoice PDF link shows an error.",
                        TicketChannel.EMAIL, TicketCategory.ACCOUNT, TicketPriority.LOW, TicketStatus.OPEN, minutesAgo(120), -1, List.of());
                default -> {
                    String[] row = historic[h++];
                    Customer c = pool.get(random.nextInt(pool.size()));
                    TicketPriority pr = TicketPriority.values()[random.nextInt(3)];
                    int age = 9 + random.nextInt(150);
                    double hoursToResolve = 1 + random.nextInt(slaHours(pr) + 6);
                    ticket(c, null, random.nextBoolean() ? ritu : sana, row[0], null,
                            TicketChannel.values()[random.nextInt(TicketChannel.values().length)],
                            TicketCategory.valueOf(row[1]), pr, TicketStatus.RESOLVED, at(age, 10 + random.nextInt(7), 0), hoursToResolve, List.of());
                }
            }
        }
    }

    /**
     * @param resolveAfterHours hours until resolution for resolved tickets, negative for unresolved
     * @param thread            [kind, body] where kind is customer | agent | internal
     */
    private SupportTicket ticket(Customer c, Order order, User assignee, String subject, String description, TicketChannel channel,
                                 TicketCategory category, TicketPriority priority, TicketStatus status, Instant createdAt,
                                 double resolveAfterHours, List<String[]> thread) {
        SupportTicket t = new SupportTicket();
        t.setCustomer(c);
        t.setOrder(order);
        t.setAssignee(assignee);
        t.setCreatedBy(assignee != null ? assignee : ritu);
        t.setSubject(subject);
        t.setDescription(description);
        t.setChannel(channel);
        t.setCategory(category);
        t.setPriority(priority);
        t.setStatus(status);
        t.setSlaDueAt(createdAt.plus(Duration.ofHours(slaHours(priority))));
        if (resolveAfterHours >= 0) {
            Instant resolved = createdAt.plusSeconds((long) (resolveAfterHours * 3600));
            t.setResolvedAt(resolved);
            t.setFirstResponseAt(createdAt.plusSeconds((long) Math.min(resolveAfterHours * 3600, 900 + random.nextInt(3600))));
            t.setSlaBreached(resolved.isAfter(t.getSlaDueAt()));
        } else {
            t.setSlaBreached(t.getSlaDueAt().isBefore(Instant.now(clock)));
            for (int i = 0; i < thread.size(); i++) {
                if (thread.get(i)[0].equals("agent")) {
                    t.setFirstResponseAt(createdAt.plus(Duration.ofMinutes(38L * i)));
                    break;
                }
            }
        }
        Instant cursor = createdAt;
        for (String[] msg : thread) {
            TicketComment comment = new TicketComment();
            comment.setTicket(t);
            comment.setFromCustomer(msg[0].equals("customer"));
            comment.setInternal(msg[0].equals("internal"));
            comment.setAuthor(msg[0].equals("customer") ? null : assignee);
            comment.setBody(msg[1]);
            t.getComments().add(comment);
        }
        t = ticketRepo.save(t);
        t.setTicketNumber("TKT-" + t.getId());
        backdate("support_tickets", t.getId(), createdAt);
        for (TicketComment comment : t.getComments()) {
            backdate("ticket_comments", comment.getId(), cursor);
            cursor = cursor.plus(Duration.ofMinutes(38));
        }
        String who = assignee != null ? assignee.getFullName() : "unassigned";
        audit(t.getCreatedBy(), "TICKET", t.getId(), c.getId(), "CREATED", "Ticket " + t.getTicketNumber() + " created", createdAt);
        if (assignee != null) {
            audit(null, "TICKET", t.getId(), c.getId(), "ASSIGNED", "Assigned to " + who + " (auto)", createdAt.plus(Duration.ofMinutes(3)));
        }
        if (status != TicketStatus.OPEN) {
            audit(assignee, "TICKET", t.getId(), c.getId(), "STATUS_CHANGED", "Status → " + label(status),
                    t.getResolvedAt() != null ? t.getResolvedAt() : createdAt.plus(Duration.ofMinutes(38)));
        }
        if (thread.stream().anyMatch(msg -> msg[0].equals("internal"))) {
            audit(assignee, "TICKET", t.getId(), c.getId(), "NOTE_ADDED", "Internal note added", createdAt.plus(Duration.ofMinutes(68)));
        }
        return t;
    }

    private int slaHours(TicketPriority p) {
        return switch (p) {
            case HIGH -> props.sla().highHours();
            case MEDIUM -> props.sla().mediumHours();
            case LOW -> props.sla().lowHours();
        };
    }

    private static String label(TicketStatus s) {
        return switch (s) {
            case OPEN -> "Open";
            case IN_PROGRESS -> "In Progress";
            case AWAITING_CUSTOMER -> "Awaiting Customer";
            case RESOLVED -> "Resolved";
        };
    }

    // ---------------------------------------------------------------- helpers

    private void recalculateLifetimeValues() {
        Map<Long, BigDecimal> ltv = new HashMap<>();
        for (Object[] row : orderRepo.paidStatsByCustomer()) {
            ltv.put((Long) row[0], (BigDecimal) row[3]);
        }
        for (Customer c : customerRepo.findAll()) {
            c.setLifetimeValue(ltv.getOrDefault(c.getId(), BigDecimal.ZERO));
        }
    }

    private void audit(User user, String entity, Long entityId, Long customerId, String action, String summary, Instant when) {
        ActivityLog a = new ActivityLog();
        a.setUser(user);
        a.setEntityType(entity);
        a.setEntityId(entityId);
        a.setCustomerId(customerId);
        a.setAction(action);
        a.setSummary(summary);
        a = logRepo.save(a);
        backdate("activity_logs", a.getId(), when);
    }

    private void backdate(String table, Long id, Instant when) {
        backdates.add(new Object[]{table, id, when});
    }

    private Instant createdOf(Customer c) {
        return backdates.stream().filter(b -> b[0].equals("customers") && b[1].equals(c.getId()))
                .map(b -> (Instant) b[2]).findFirst().orElse(Instant.now(clock));
    }

    /** A business-local time {@code days} days ago (negative = in the future). */
    private Instant at(int daysAgo, int hour, int minute) {
        return today.minusDays(daysAgo).atTime(hour, minute).atZone(zone).toInstant();
    }

    private Instant daysAgo(int days) {
        return at(days, 10, 0);
    }

    private Instant minutesAgo(long minutes) {
        return Instant.now(clock).minus(Duration.ofMinutes(minutes));
    }

    private int hourOffset(int hoursAgo) {
        return Math.max(0, LocalTime.now(clock.withZone(zone)).getHour() + hoursAgo);
    }

    private String randomTag() {
        String[] names = {"Wholesale", "Distributor", "Festive Buyer", "VIP", "Credit Hold"};
        return names[random.nextInt(names.length)];
    }

    private String randomStreet() {
        String[] streets = {"MG Road", "Station Road", "Market Yard", "Ring Road", "Industrial Estate", "Main Bazaar", "Sector 5"};
        return streets[random.nextInt(streets.length)];
    }
}
