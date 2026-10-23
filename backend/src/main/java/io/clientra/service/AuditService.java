package io.clientra.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.clientra.entity.ActivityLog;
import io.clientra.repository.ActivityLogRepository;
import io.clientra.repository.UserRepository;
import io.clientra.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes the activity_logs audit trail: who changed what and when. Joins the caller's transaction. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final ActivityLogRepository logRepo;
    private final UserRepository userRepo;
    private final ObjectMapper mapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void log(String entityType, Long entityId, Long customerId, String action, String summary, Object details) {
        ActivityLog entry = new ActivityLog();
        Long userId = CurrentUser.id();
        if (userId != null) {
            entry.setUser(userRepo.getReferenceById(userId));
        }
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setCustomerId(customerId);
        entry.setAction(action);
        entry.setSummary(summary);
        entry.setDetails(toJson(details));
        logRepo.save(entry);
    }

    public void log(String entityType, Long entityId, Long customerId, String action, String summary) {
        log(entityType, entityId, customerId, action, summary, null);
    }

    private String toJson(Object details) {
        if (details == null) return null;
        try {
            return mapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            log.warn("Could not serialise audit details: {}", e.getMessage());
            return null;
        }
    }
}
