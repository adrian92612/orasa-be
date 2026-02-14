package com.orasa.backend.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orasa.backend.common.AppointmentStatus;
import com.orasa.backend.common.AppointmentType;
import com.orasa.backend.common.SmsStatus;
import com.orasa.backend.common.SmsTaskStatus;
import com.orasa.backend.common.SubscriptionStatus;
import com.orasa.backend.common.UserRole;
import com.orasa.backend.domain.ActivityLogEntity;
import com.orasa.backend.domain.AppointmentEntity;
import com.orasa.backend.domain.BranchEntity;
import com.orasa.backend.domain.BranchServiceEntity;
import com.orasa.backend.domain.BusinessEntity;
import com.orasa.backend.domain.BusinessReminderConfigEntity;
import com.orasa.backend.domain.ScheduledSmsTaskEntity;
import com.orasa.backend.domain.ServiceEntity;
import com.orasa.backend.domain.SmsLogEntity;
import com.orasa.backend.domain.UserEntity;
import com.orasa.backend.repository.ActivityLogRepository;
import com.orasa.backend.repository.AppointmentRepository;
import com.orasa.backend.repository.BranchRepository;
import com.orasa.backend.repository.BranchServiceRepository;
import com.orasa.backend.repository.BusinessReminderConfigRepository;
import com.orasa.backend.repository.BusinessRepository;
import com.orasa.backend.repository.ScheduledSmsTaskRepository;
import com.orasa.backend.repository.ServiceRepository;
import com.orasa.backend.repository.SmsLogRepository;
import com.orasa.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DemoDataService {

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final BranchRepository branchRepository;
    private final ServiceRepository serviceRepository;
    private final BranchServiceRepository branchServiceRepository;
    private final AppointmentRepository appointmentRepository;
    private final BusinessReminderConfigRepository reminderConfigRepository;
    private final ActivityLogRepository activityLogRepository;
    private final ScheduledSmsTaskRepository scheduledSmsTaskRepository;
    private final SmsLogRepository smsLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final JdbcTemplate jdbcTemplate;

    private static final String DEMO_OWNER_EMAIL = "adrianvillamin0612@gmail.com";
    private static final String DEMO_PASSWORD = "demo123";
    private static final String DEMO_SLUG = "orasa-demo-clinic";
    
    // Expanded list of ~50 Filipino names for better variety
    private static final List<String> FILIPINO_NAMES = List.of(
        "Juan Dela Cruz", "Maria Santos", "Jose Rizal", "Andres Bonifacio", "Gabriela Silang", 
        "Emilio Aguinaldo", "Apolinario Mabini", "Melchora Aquino", "Lapu-Lapu", "Antonio Luna",
        "Gregorio del Pilar", "Marcelo H. del Pilar", "Graciano Lopez Jaena", "Juan Luna", "Diego Silang",
        "Francisco Balagtas", "Josefa Llanes Escoda", "Teresa Magbanua", "Julian Felipe", "Agapito Conchu",
        "Gomburza", "Lakandula", "Rajah Sulayman", "Trinidad Tecson", "Gregoria de Jesus",
        "Pio Valenzuela", "Emilio Jacinto", "Mariano Ponce", "Pedro Paterno", "Felipe Agoncillo",
        "Efren Penaflorida", "Lea Salonga", "Manny Pacquiao", "Catriona Gray", "Pia Wurtzbach",
        "Liza Soberano", "Kathryn Bernardo", "Daniel Padilla", "Vice Ganda", "Anne Curtis",
        "Coco Martin", "Judy Ann Santos", "Angel Locsin", "Sarah Geronimo", "Regine Velasquez",
        "Bamboo Mañalac", "Arnel Pineda", "Apl.de.ap", "Bruno Mars", "Olivia Rodrigo"
    );

    @Transactional
    public void seedDemoData() {
        log.info("Resetting demo data...");
        clearDemoData();

        log.info("Seeding new demo data...");

        // 1. Create Business
        BusinessEntity business = BusinessEntity.builder()
                .name("Orasa Demo Clinic")
                .slug(DEMO_SLUG)
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .subscriptionStartDate(OffsetDateTime.now(clock))
                .subscriptionEndDate(OffsetDateTime.now(clock).plusMonths(12))
                .freeSmsCredits(100)
                .paidSmsCredits(50)
                .build();
        
        business = businessRepository.save(business);

        // 2. Create Reminder Configs
        BusinessReminderConfigEntity oneDayBefore = createReminderConfig(business, 1440, "Hello {{customer_name}}, gentle reminder for your appointment at {{business_name}} tomorrow {{appointment_date}} at {{appointment_time}}.");
        BusinessReminderConfigEntity oneHourBefore = createReminderConfig(business, 60, "Hi {{customer_name}}, see you in an hour for your appointment at {{branch_name}}!");

        Set<BusinessReminderConfigEntity> defaultReminders = new HashSet<>();
        defaultReminders.add(oneDayBefore);
        defaultReminders.add(oneHourBefore);

        // 3. Create Owner
        UserEntity owner = UserEntity.builder()
                .email(DEMO_OWNER_EMAIL)
                .username(DEMO_OWNER_EMAIL)
                .role(UserRole.OWNER)
                .business(business)
                .build();
        
        owner = userRepository.save(owner); // Capture updated entity

        // 4. Create Branches
        BranchEntity makatiBranch = BranchEntity.builder()
                .business(business)
                .name("Main Branch (Makati)")
                .address("123 Ayala Ave, Makati City")
                .phoneNumber("09171234567")
                .build();
        
        makatiBranch = branchRepository.save(makatiBranch);

        BranchEntity qcBranch = BranchEntity.builder()
                .business(business)
                .name("QC Branch (Tomas Morato)")
                .address("456 Tomas Morato, Quezon City")
                .phoneNumber("09181234567")
                .build();

        qcBranch = branchRepository.save(qcBranch);
        
        // Link owner to all branches
        Set<BranchEntity> ownerBranches = new HashSet<>();
        ownerBranches.add(makatiBranch);
        ownerBranches.add(qcBranch);
        owner.setBranches(ownerBranches);
        userRepository.save(owner);

        // 5. Create Services
        ServiceEntity consultation = createService(business, "General Consultation", "Standard checkup", new BigDecimal("500.00"), 30);
        ServiceEntity followUp = createService(business, "Follow-up Checkup", "Review of previous condition", new BigDecimal("300.00"), 15);
        ServiceEntity dental = createService(business, "Dental Cleaning", "Full prophylaxis", new BigDecimal("1500.00"), 60);
        ServiceEntity whitening = createService(business, "Teeth Whitening", "Laser teeth whitening", new BigDecimal("5000.00"), 90);
        ServiceEntity extraction = createService(business, "Tooth Extraction", "Simple extraction per tooth", new BigDecimal("1000.00"), 45);

        // Link services to branches
        // Makati has everything except extraction
        linkServiceToBranch(makatiBranch, consultation);
        linkServiceToBranch(makatiBranch, followUp);
        linkServiceToBranch(makatiBranch, dental);
        linkServiceToBranch(makatiBranch, whitening);

        // QC has basics + extraction
        linkServiceToBranch(qcBranch, consultation);
        linkServiceToBranch(qcBranch, followUp);
        linkServiceToBranch(qcBranch, dental);
        linkServiceToBranch(qcBranch, extraction);

        // 6. Create Staff
        createStaff(business, makatiBranch, "staff.makati1", "Makati Front Desk 1");
        createStaff(business, makatiBranch, "staff.makati2", "Makati Front Desk 2");
        createStaff(business, qcBranch, "staff.qc1", "QC Front Desk 1");
        createStaff(business, qcBranch, "staff.qc2", "QC Front Desk 2");

        // 7. Create Appointments (Historical & Future)
        LocalDate today = LocalDate.now(clock);
        Random random = new Random();

        // Generate appointments for -15 days to +15 days (30 day total window)
        // Start date: 15 days ago
        LocalDate startDate = today.minusDays(15);
        
        generateAppointments(business, makatiBranch, owner, startDate, 30, random, 
            List.of(consultation, dental, whitening), 
            defaultReminders);

        generateAppointments(business, qcBranch, owner, startDate, 30, random, 
            List.of(consultation, extraction, dental), 
            defaultReminders);

        log.info("Demo data reset and seeded successfully.");
    }

    private void clearDemoData() {
        // Find business ID first
        try {
            UUID businessId = jdbcTemplate.queryForObject(
                "SELECT id FROM businesses WHERE slug = ?", 
                UUID.class, 
                DEMO_SLUG
            );

            if (businessId == null) return;

            // Delete in order to respect FK constraints
            
            // 1. Delete Sms Logs
            jdbcTemplate.update("DELETE FROM sms_logs WHERE business_id = ?", businessId);

            // 2. Delete Scheduled Sms Tasks
            jdbcTemplate.update("DELETE FROM scheduled_sms_tasks WHERE business_id = ?", businessId);
            
            // 3. Delete Activity Logs
            jdbcTemplate.update("DELETE FROM activity_logs WHERE business_id = ?", businessId);

            // 4. Delete Appointment Reminders
            jdbcTemplate.update(
                "DELETE FROM appointment_reminders WHERE appointment_id IN (SELECT id FROM appointments WHERE business_id = ?)",
                businessId
            );

            // 5. Delete Appointments
            jdbcTemplate.update("DELETE FROM appointments WHERE business_id = ?", businessId);

            // 6. Delete Branch Services
            jdbcTemplate.update(
                "DELETE FROM branch_services WHERE branch_id IN (SELECT id FROM branches WHERE business_id = ?)",
                businessId
            );

            // 7. Delete Reminder Configs
            jdbcTemplate.update("DELETE FROM business_reminder_configs WHERE business_id = ?", businessId);

            // 8. Delete Services
            jdbcTemplate.update("DELETE FROM services WHERE business_id = ?", businessId);

            // 9. Delete User Branches
            jdbcTemplate.update(
                "DELETE FROM user_branches WHERE user_id IN (SELECT id FROM users WHERE business_id = ?)",
                businessId
            );

            // 10. Delete Users
            jdbcTemplate.update("DELETE FROM users WHERE business_id = ?", businessId);

            // 11. Delete Branches
            jdbcTemplate.update("DELETE FROM branches WHERE business_id = ?", businessId);

            // 12. Delete Business
            jdbcTemplate.update("DELETE FROM businesses WHERE id = ?", businessId);

        } catch (Exception e) {
            log.warn("Could not clear demo data (likely didn't exist): {}", e.getMessage());
        }
    }

    private BusinessReminderConfigEntity createReminderConfig(BusinessEntity business, int minutes, String template) {
        BusinessReminderConfigEntity config = BusinessReminderConfigEntity.builder()
                .businessId(business.getId())
                .leadTimeMinutes(minutes)
                .messageTemplate(template)
                .isEnabled(true)
                .build();
        return reminderConfigRepository.save(config);
    }

    private ServiceEntity createService(BusinessEntity business, String name, String description, BigDecimal price, int duration) {
        ServiceEntity service = ServiceEntity.builder()
                .businessId(business.getId())
                .name(name)
                .description(description)
                .basePrice(price)
                .durationMinutes(duration)
                .build();
        return serviceRepository.save(service);
    }

    private void linkServiceToBranch(BranchEntity branch, ServiceEntity service) {
        BranchServiceEntity link = BranchServiceEntity.builder()
                .branchId(branch.getId())
                .service(service)
                .isActive(true)
                .build();
        branchServiceRepository.save(link);
    }

    private void createStaff(BusinessEntity business, BranchEntity branch, String username, String displayName) { 
        UserEntity staff = UserEntity.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(DEMO_PASSWORD))
                .role(UserRole.STAFF)
                .business(business)
                .email(username + "@demo.com") 
                .build();
        
        Set<BranchEntity> staffBranches = new HashSet<>();
        staffBranches.add(branch);
        staff.setBranches(staffBranches);
        userRepository.save(staff);
    }

    private void generateAppointments(BusinessEntity business, BranchEntity branch, UserEntity creator, LocalDate startDate, int daysToGenerate, Random random, List<ServiceEntity> services, Set<BusinessReminderConfigEntity> reminders) {
        LocalDate today = LocalDate.now(clock);

        List<String> names = new ArrayList<>(FILIPINO_NAMES);
        Collections.shuffle(names); // Shuffle names for random selection
        int nameIndex = 0;

        for (int i = 0; i < daysToGenerate; i++) {
            LocalDate currentDate = startDate.plusDays(i);
            boolean isPast = currentDate.isBefore(today);

            // Create 3-5 appointments per day
            int count = 3 + random.nextInt(3); 
            
            // Working hours 9am - 5pm
            int currentHour = 9;

            for (int k = 0; k < count; k++) {
                if (currentHour >= 17) break; // Stop if past 5pm

                ServiceEntity service = services.get(random.nextInt(services.size()));
                
                // Get varied customer names
                String customer = names.get(nameIndex % names.size());
                nameIndex++;

                String notes = random.nextBoolean() ? "Notes for " + customer : null;

                AppointmentStatus status;
                if (isPast) {
                    // Mostly completed, some cancelled/no-show
                    int roll = random.nextInt(10);
                    if (roll < 7) status = AppointmentStatus.COMPLETED;
                    else if (roll < 9) status = AppointmentStatus.CANCELLED;
                    else status = AppointmentStatus.NO_SHOW;
                } else {
                    // Today/Future: Mix of statuses
                    int roll = random.nextInt(10);
                    if (roll < 6) status = AppointmentStatus.PENDING; // 60%
                    else if (roll < 9) status = AppointmentStatus.CONFIRMED; // 30%
                    else status = AppointmentStatus.CANCELLED; // 10%
                }

                String timeStr = String.format("%02d:00", currentHour);
                
                createAppointmentWithLogs(business, branch, creator, service, currentDate, timeStr, customer, "0917" + (1000000 + random.nextInt(9000000)), status, AppointmentType.SCHEDULED, notes, reminders, isPast);

                currentHour += 1 + (service.getDurationMinutes() / 60); // Advance time
            }
            
            // Add 1 random walk-in (No reminders for walk-ins)
            // Walk-ins only for today, and MUST be in the past (before current time)
            if (currentDate.equals(today) && random.nextBoolean()) {
                 LocalTime now = LocalTime.now(clock);
                 // Only add walk-in if current time is reasonably into the day (e.g. past 10am)
                 if (now.getHour() > 10) {
                     int maxHour = now.getHour() - 1;
                     int minHour = 9;
                     int hour = minHour + (maxHour > minHour ? random.nextInt(maxHour - minHour) : 0);
                     
                     ServiceEntity service = services.get(random.nextInt(services.size()));
                     String walkInCustomer = "Walk-in: " + names.get(nameIndex % names.size()); // Named walk-in
                     nameIndex++;
    
                     String timeStr = String.format("%02d:30", hour);
                     
                     createAppointmentWithLogs(business, branch, creator, service, currentDate, timeStr, walkInCustomer, "0918" + (1000000 + random.nextInt(9000000)), 
                        AppointmentStatus.COMPLETED, // Walk-ins today are completed or pending? Usually completed if in past
                        AppointmentType.WALK_IN, "Walked in", null, true); // Treat today's past walk-in as "isPast=true" for logs
                 }
            }
        }
    }

    private void createAppointmentWithLogs(BusinessEntity business, BranchEntity branch, UserEntity creator, ServiceEntity service, 
            LocalDate date, String timeStr, String customerName, String customerPhone, 
            AppointmentStatus status, AppointmentType type, String notes, Set<BusinessReminderConfigEntity> reminders, boolean isPast) {
        
        LocalTime time = LocalTime.parse(timeStr);
        OffsetDateTime start = date.atTime(time).atZone(ZoneId.of("Asia/Manila")).toOffsetDateTime();
        OffsetDateTime end = start.plusMinutes(service.getDurationMinutes());

        boolean remindersEnabled = (type == AppointmentType.SCHEDULED);

        AppointmentEntity appointment = AppointmentEntity.builder()
                .business(business)
                .branch(branch)
                .service(service)
                .customerName(customerName)
                .customerPhone(customerPhone)
                .startDateTime(start)
                .endDateTime(end)
                .status(status)
                .type(type)
                .notes(notes)
                .remindersEnabled(remindersEnabled)
                .selectedReminders(remindersEnabled ? reminders : null)
                .build();
        
        appointment = appointmentRepository.save(appointment);

        // 1. Create Activity Log (Created)
        createActivityLog(business, branch, creator, "Appointment Created", "Created appointment for " + customerName);

        // 2. Create Activity Log (Status Change) if not PENDING
        if (status != AppointmentStatus.PENDING) {
             createActivityLog(business, branch, creator, "Status Updated", "Detailed status changed to " + status + " for " + customerName);
        }

        // 3. Create SMS Tasks / Logs
        if (remindersEnabled && reminders != null && !reminders.isEmpty()) {
            if (isPast) {
                // Determine if successful/failed based on status
                // If COMPLETED, assume reminders sent successfully
                // If CANCELLED/NOSHOW, maybe sent, maybe failed. Let's assume sent for simplicity or mixed.
                for (BusinessReminderConfigEntity config : reminders) {
                    createSmsLog(business, appointment, customerPhone, 
                        config.getMessageTemplate().replace("{{customer_name}}", customerName), // Use config to make it real
                        SmsStatus.SENT);
                }

            } else {
                // Future -> Scheduled Tasks
                // Only schedule reminders if not cancelled/completed/no-show
                if (status == AppointmentStatus.PENDING || status == AppointmentStatus.CONFIRMED) {
                    for (BusinessReminderConfigEntity config : reminders) {
                        // Calculate scheduled time
                        OffsetDateTime scheduledTime = start.minusMinutes(config.getLeadTimeMinutes());
                        if (scheduledTime.isAfter(OffsetDateTime.now(clock))) {
                             createScheduledSmsTask(business, appointment, scheduledTime);
                        }
                    }
                }
            }
        }
    }

    private void createActivityLog(BusinessEntity business, BranchEntity branch, UserEntity user, String action, String description) {
        ActivityLogEntity log = ActivityLogEntity.builder()
                .business(business)
                .branch(branch)
                .user(user)
                .action(action)
                .description(description)
                .details("{}") // Empty details for demo
                .build();
        activityLogRepository.save(log);
    }

    private void createScheduledSmsTask(BusinessEntity business, AppointmentEntity appointment, OffsetDateTime scheduledAt) {
        ScheduledSmsTaskEntity task = ScheduledSmsTaskEntity.builder()
                .businessId(business.getId())
                .appointment(appointment)
                .scheduledAt(scheduledAt)
                .status(SmsTaskStatus.PENDING)
                .build();
        scheduledSmsTaskRepository.save(task);
    }

    private void createSmsLog(BusinessEntity business, AppointmentEntity appointment, String phone, String body, SmsStatus status) {
        SmsLogEntity log = SmsLogEntity.builder()
                .business(business)
                .appointment(appointment)
                .recipientPhone(phone)
                .messageBody(body)
                .status(status)
                .providerId("demo-provider-id-" + System.currentTimeMillis())
                .build();
        smsLogRepository.save(log);
    }
}
