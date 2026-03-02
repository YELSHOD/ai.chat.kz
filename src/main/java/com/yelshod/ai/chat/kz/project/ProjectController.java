package com.yelshod.ai.chat.kz.project;

import com.yelshod.ai.chat.kz.auth.AppPrincipal;
import com.yelshod.ai.chat.kz.auth.CurrentUserResolver;
import com.yelshod.ai.chat.kz.chat.ChatService;
import com.yelshod.ai.chat.kz.chat.dto.ChatResponse;
import com.yelshod.ai.chat.kz.chat.dto.CreateChatRequest;
import com.yelshod.ai.chat.kz.project.dto.CreateProjectRequest;
import com.yelshod.ai.chat.kz.project.dto.ProjectResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ChatService chatService;
    private final CurrentUserResolver currentUserResolver;

    public ProjectController(ProjectService projectService,
                             ChatService chatService,
                             CurrentUserResolver currentUserResolver) {
        this.projectService = projectService;
        this.chatService = chatService;
        this.currentUserResolver = currentUserResolver;
    }

    @PostMapping
    public ProjectResponse create(@AuthenticationPrincipal AppPrincipal principal,
                                  @Valid @RequestBody CreateProjectRequest request) {
        Long userId = currentUserResolver.resolveUserId(principal);
        return projectService.create(userId, request);
    }

    @GetMapping
    public List<ProjectResponse> list(@AuthenticationPrincipal AppPrincipal principal) {
        Long userId = currentUserResolver.resolveUserId(principal);
        return projectService.list(userId);
    }

    @PostMapping("/{projectId}/chats")
    public ChatResponse createChatInProject(@AuthenticationPrincipal AppPrincipal principal,
                                            @PathVariable UUID projectId,
                                            @Valid @RequestBody(required = false) CreateChatRequest request) {
        Long userId = currentUserResolver.resolveUserId(principal);
        CreateChatRequest actual = request == null ? new CreateChatRequest(null) : request;
        return chatService.createChatInProject(userId, projectId, actual);
    }

    @GetMapping("/{projectId}/chats")
    public List<ChatResponse> listChatsByProject(@AuthenticationPrincipal AppPrincipal principal,
                                                 @PathVariable UUID projectId) {
        Long userId = currentUserResolver.resolveUserId(principal);
        return chatService.listChatsByProject(userId, projectId);
    }
}
