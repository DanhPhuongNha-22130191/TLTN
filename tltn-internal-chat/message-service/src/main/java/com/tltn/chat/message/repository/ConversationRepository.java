package com.tltn.chat.message.repository;

import com.tltn.chat.message.model.Conversation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends MongoRepository<Conversation, String> {
    
    List<Conversation> findByParticipantIdsContaining(String participantId);
    
    @Query("{ 'type' : 'PERSONAL', 'participantIds' : { $all: [?0, ?1] } }")
    Optional<Conversation> findPersonalConversation(String user1Id, String user2Id);
}
