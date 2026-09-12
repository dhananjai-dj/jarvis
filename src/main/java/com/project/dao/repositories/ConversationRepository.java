package com.project.dao.repositories;

import com.project.dao.entities.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

   List<Conversation> findTop10BySessionIdOrderByIdDesc(String sessionId);
   List<Conversation> findAllBySessionId(String sessionId);
}
