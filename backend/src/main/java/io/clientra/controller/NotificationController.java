package io.clientra.controller;

import io.clientra.dto.NotificationDto;
import io.clientra.security.CurrentUser;
import io.clientra.service.NotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Notifications")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    public Page<NotificationDto> list(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "15") int size) {
        return service.list(CurrentUser.require().id(), page, Math.min(size, 50));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", service.unreadCount(CurrentUser.require().id()));
    }

    @PatchMapping("/{id}/read")
    public void markRead(@PathVariable Long id) {
        service.markRead(CurrentUser.require().id(), id);
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead() {
        return Map.of("updated", service.markAllRead(CurrentUser.require().id()));
    }
}
