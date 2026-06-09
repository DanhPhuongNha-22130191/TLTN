package secretchat.chatservice.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import secretchat.chatservice.application.port.out.ConversationRepositoryPort;
import secretchat.chatservice.domain.model.Conversation;
import secretchat.chatservice.infrastructure.persistence.entity.ConversationEntity;
import secretchat.chatservice.infrastructure.persistence.repository.ConversationRepository;
import secretchat.chatservice.infrastructure.persistence.mapper.ConversationMapper;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ConversationRepositoryAdapter implements ConversationRepositoryPort {

    private final ConversationRepository conversationRepository;
    private final ConversationMapper conversationMapper;

    @Override
    public Conversation save(Conversation conversation) {
        ConversationEntity entity = conversationMapper.toEntity(conversation);
        ConversationEntity savedEntity = conversationRepository.save(entity);
        return conversationMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Conversation> findById(Long id) {
        return conversationRepository.findById(id)
                .map(conversationMapper::toDomain);
    }

    @Override
    public List<Conversation> findAll() {
        return conversationRepository.findAll().stream()
                .map(conversationMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(Long id) {
        conversationRepository.deleteById(id);
    }

    @Override
    public boolean existsById(Long id) {
        return conversationRepository.existsById(id);
    }

    @Override
    public Optional<Conversation> findPersonalConversation(String user1, String user2) {
        return conversationRepository.findPersonalConversation(user1, user2)
                .map(conversationMapper::toDomain);
    }

    @Override
    public Optional<Conversation> findByGroupId(Long groupId) {
        return conversationRepository.findByGroupId(groupId)
                .map(conversationMapper::toDomain);
    }

    @Override
    public List<Conversation> findUserConversations(String userId) {
        return conversationRepository.findUserConversations(userId).stream()
                .map(conversationMapper::toDomain)
                .collect(Collectors.toList());
    }
}
