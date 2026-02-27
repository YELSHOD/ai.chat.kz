package com.yelshod.ai.chat.kz.project;

import com.yelshod.ai.chat.kz.user.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findAllByOwnerOrderByUpdatedAtDesc(AppUser owner);
    Optional<Project> findByPublicIdAndOwner(UUID publicId);
}
