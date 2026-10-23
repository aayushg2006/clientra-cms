package io.clientra.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "activity_logs")
@Getter
@Setter
@NoArgsConstructor
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String entityType;

    @Column(nullable = false)
    private Long entityId;

    private Long customerId;

    @Column(nullable = false)
    private String action;

    private String summary;

    /** JSON text; stored in a MySQL JSON column. */
    @Column(columnDefinition = "json")
    private String details;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
