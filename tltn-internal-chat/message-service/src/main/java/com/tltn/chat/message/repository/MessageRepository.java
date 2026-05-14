package com.tltn.chat.message.repository;

import com.tltn.chat.message.model.Message;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends MongoRepository<Message, String> {
    
    List<Message> findByConversationIdOrderByTimestampAsc(String conversationId);
}
