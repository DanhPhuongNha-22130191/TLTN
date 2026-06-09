package secretchat.chatservice.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "group_members")
@IdClass(GroupMemberId.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMemberEntity {

    @Id
    @Column(name = "group_id")
    private Long groupId;

    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "nickname")
    private String nickname;

    @Column(name = "invited_by")
    private String invitedBy;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private GroupEntity group;
}
