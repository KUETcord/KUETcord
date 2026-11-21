package com.kuetcord.messaging.repository;

import com.kuetcord.messaging.model.MessageMention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MessageMentionRepository extends JpaRepository<MessageMention, Long> {

    List<MessageMention> findByMessageId(Long messageId);

    /** Rows of [userId, lowercased username] for the given lowercase usernames (one query per message). */
    @Query("SELECT u.id, LOWER(u.username) FROM User u WHERE LOWER(u.username) IN :usernames")
    List<Object[]> resolveUsernames(@Param("usernames") Collection<String> lowercaseUsernames);
}
