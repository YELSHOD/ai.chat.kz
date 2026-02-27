package com.yelshod.ai.chat.kz.project;

import com.yelshod.ai.chat.kz.auth.AppPrincipal;
import com.yelshod.ai.chat.kz.project.dto.CreateProjectRequest;
import com.yelshod.ai.chat.kz.project.dto.ProjectResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ProjectResponse create(@AuthenticationPrincipal AppPrincipal principal,
                                  @Valid @RequestBody CreateProjectRequest request) {
        return projectService.create(principal.userId(), request);
    }

    @GetMapping
    public List<ProjectResponse> list(@AuthenticationPrincipal AppPrincipal principal) {
        return projectService.list(principal.userId());
    }
}
