package io.clientra.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "ticket_comments")
@Getter
@Setter
@NoArgsConstructor
public class TicketComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id")
    private SupportTicket ticket;

    /** Staff author; null when the message came from the customer. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    private boolean fromCustomer;

    private boolean internal;

    @Column(nullable = false)
    private String body;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
