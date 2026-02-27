package com.yelshod.ai.chat.kz.chat;

import com.yelshod.ai.chat.kz.project.Project;
import com.yelshod.ai.chat.kz.user.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatRepository extends JpaRepository<Chat, Long> {

    List<Chat> findAllByUserOrderByUpdatedAtDesc(AppUser user);

    Optional<Chat> findByPublicIdAndUser(UUID publicId, AppUser user);

    List<Chat> findAllByProjectOrderByUpdatedAtDesc(Project project);
}
