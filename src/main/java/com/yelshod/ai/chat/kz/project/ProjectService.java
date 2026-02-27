package com.yelshod.ai.chat.kz.project;

import com.yelshod.ai.chat.kz.common.ApiException;
import com.yelshod.ai.chat.kz.project.dto.CreateProjectRequest;
import com.yelshod.ai.chat.kz.project.dto.ProjectResponse;
import com.yelshod.ai.chat.kz.user.AppUser;
import com.yelshod.ai.chat.kz.user.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {

    private final AppUserRepository appUserRepository;
    private final ProjectRepository projectRepository;

    public ProjectService(AppUserRepository appUserRepository, ProjectRepository projectRepository) {
        this.appUserRepository = appUserRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public ProjectResponse create(Long userId, CreateProjectRequest request) {
        AppUser owner = appUserRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));

        Project project = new Project();
        project.setPublicId(UUID.randomUUID());
        project.setOwner(owner);
        project.setTitle(request.title().trim());

        Instant now = Instant.now();
        project.setCreatedAt(now);
        project.setUpdatedAt(now);

        Project saved = projectRepository.save(project);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(Long userId) {
        AppUser owner = appUserRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));

        return projectRepository.findAllByOwnerOrderByUpdatedAtDesc(owner)
                .stream()
                .map(this::toResponse)
                .toList();
    }


    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getPublicId(),
                project.getTitle(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
