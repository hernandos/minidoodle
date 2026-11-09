package de.com.saenz.minidoodle.infrastructure.persistence.jpa.entity;

import de.com.saenz.minidoodle.domain.model.SlotStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA entity representing a time slot.
 * Includes @Version for Optimistic Locking to prevent double-booking race conditions.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "time_slots",
        indexes = {
                @Index(name = "idx_time_slots_user_start_end", columnList = "user_id, start_time, end_time")
        }
)
public class TimeSlotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "time_slots_id_seq")
    @SequenceGenerator(name = "time_slots_id_seq", sequenceName = "time_slots_id_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SlotStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
