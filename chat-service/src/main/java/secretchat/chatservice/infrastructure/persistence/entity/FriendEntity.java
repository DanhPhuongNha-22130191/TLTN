package secretchat.chatservice.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "friends", uniqueConstraints = {@UniqueConstraint(columnNames = {"user_id", "friend_id"})})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "friend_id", nullable = false)
    private String friendId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
