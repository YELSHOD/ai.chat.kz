package com.yelshod.ai.chat.kz.project;

import com.yelshod.ai.chat.kz.auth.AppPrincipal;
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

    public ProjectController(ProjectService projectService, ChatService chatService) {
        this.projectService = projectService;
        this.chatService = chatService;
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

    @PostMapping("/{projectId}/chats")
    public ChatResponse createChatInProject(@AuthenticationPrincipal AppPrincipal principal,
                                            @PathVariable UUID projectId,
                                            @Valid @RequestBody(required = false) CreateChatRequest request) {
        CreateChatRequest actual = request == null ? new CreateChatRequest(null) : request;
        return chatService.createChatInProject(principal.userId(), projectId, actual);
    }

    @GetMapping("/{projectId}/chats")
    public List<ChatResponse> listChatsByProject(@AuthenticationPrincipal AppPrincipal principal,
                                                 @PathVariable UUID projectId) {
        return chatService.listChatsByProject(principal.userId(), projectId);
    }
}
