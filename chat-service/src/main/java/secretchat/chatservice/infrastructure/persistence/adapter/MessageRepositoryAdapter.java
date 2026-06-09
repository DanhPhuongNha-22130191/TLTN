package secretchat.chatservice.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import secretchat.chatservice.application.port.out.MessageRepositoryPort;
import secretchat.chatservice.domain.model.Message;
import secretchat.chatservice.infrastructure.persistence.entity.MessageEntity;
import secretchat.chatservice.infrastructure.persistence.repository.MessageRepository;
import secretchat.chatservice.infrastructure.persistence.mapper.MessageMapper;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MessageRepositoryAdapter implements MessageRepositoryPort {

    private final MessageRepository messageRepository;
    private final MessageMapper messageMapper;

    @Override
    public Message save(Message message) {
        MessageEntity entity = messageMapper.toEntity(message);
        MessageEntity savedEntity = messageRepository.save(entity);
        return messageMapper.toDomain(savedEntity);
    }

    @Override
    public List<Message> findByConversationId(Long conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(messageMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Message findById(Long id) {
        return messageRepository.findById(id)
                .map(messageMapper::toDomain)
                .orElseThrow(() -> new secretchat.chatservice.application.exception.BusinessException("Message not found"));
    }
}
