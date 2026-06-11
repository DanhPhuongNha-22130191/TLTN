package secretchat.chatservice.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import secretchat.chatservice.domain.model.Friend;
import secretchat.chatservice.infrastructure.persistence.entity.FriendEntity;

@Component
public class FriendMapper {

    public FriendEntity toEntity(Friend domain) {
        if (domain == null) {
            return null;
        }
        return FriendEntity.builder()
                .id(domain.getId())
                .userId(domain.getUserId())
                .friendId(domain.getFriendId())
                .status(domain.getStatus())
                .createdAt(domain.getCreatedAt())
                .build();
    }

    public Friend toDomain(FriendEntity entity) {
        if (entity == null) {
            return null;
        }
        return Friend.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .friendId(entity.getFriendId())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
