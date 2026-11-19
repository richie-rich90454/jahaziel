/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api;

import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.club.api.ClubService;
import dev.jahaziel.club.checkpoint.CheckpointService;
import dev.jahaziel.club.domain.ClubSession;
import dev.jahaziel.club.showcase.ShowcaseGallery;
import dev.jahaziel.club.tutorial.TutorialLesson;
import dev.jahaziel.club.tutorial.TutorialParser;
import dev.jahaziel.identity.domain.UserId;
import dev.jahaziel.workspace.domain.WorkspaceId;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

/** Tutorials, checkpoints, showcase and advisory export for a club session. */
@RestController
@RequestMapping("/api/v1/club")
public final class ClubLearningController extends ApiControllerBase {
    private final ClubService club;
    private final CheckpointService checkpoints;
    private final ClubRegistry registry;
    private final TutorialParser parser = new TutorialParser();

    public ClubLearningController(
            ClubService club,
            CheckpointService checkpoints,
            ClubRegistry registry,
            AuthorizationService auth) {
        super(auth);
        this.club = club;
        this.checkpoints = checkpoints;
        this.registry = registry;
    }

    @PostMapping("/tutorials")
    @ResponseStatus(HttpStatus.CREATED)
    public ClubDtos.LessonDto createLesson(
            @RequestBody(required = false) ClubDtos.LessonRequest body, Principal principal) {
        requireAdmin(principal);
        ClubDtos.LessonRequest request = body == null ? new ClubDtos.LessonRequest() : body;
        TutorialLesson lesson = parser.parse(request.getMarkdown());
        registry.putLesson(lesson);
        return view(lesson);
    }

    @GetMapping("/tutorials/{lessonId}")
    public ClubDtos.LessonDto lesson(@PathVariable String lessonId, Principal principal) {
        requireUser(principal);
        return view(registry.findLesson(lessonId));
    }

    @PostMapping("/sessions/{sessionId}/steps/verify")
    public ClubDtos.StepResultDto verify(
            @PathVariable String sessionId,
            @RequestBody(required = false) ClubDtos.StepRequest body,
            Principal principal) {
        UserId user = requireUser(principal);
        ClubSession session = registry.findSession(sessionId);
        requireMember(session, user);
        ClubDtos.StepRequest request = body == null ? new ClubDtos.StepRequest() : body;
        TutorialLesson lesson = registry.findLesson(request.getLessonId());
        TutorialLesson.Step step = lesson.step(request.getStepId());
        if (step == null) {
            throw ApiFailureException.badRequest("Unknown tutorial step");
        }
        WorkspaceId workspace = session.workspaceOf(user);
        requireWorkspaceFor(user, workspace, WRITE);
        return view(club.verifyStep(session, user, lesson.id(), step));
    }

    @GetMapping("/sessions/{sessionId}/tutorials/{lessonId}/progress")
    public DataDtos.CountDto personalProgress(
            @PathVariable String sessionId, @PathVariable String lessonId, Principal principal) {
        UserId user = requireUser(principal);
        ClubSession session = registry.findSession(sessionId);
        requireMember(session, user);
        return new DataDtos.CountDto(club.personalCompleted(session, user, lessonId));
    }

    @PostMapping("/sessions/{sessionId}/checkpoints")
    @ResponseStatus(HttpStatus.CREATED)
    public ClubDtos.CheckpointDto submit(
            @PathVariable String sessionId,
            @RequestBody(required = false) ClubDtos.CheckpointRequest body,
            Principal principal) {
        UserId user = requireUser(principal);
        ClubSession session = registry.findSession(sessionId);
        ClubDtos.CheckpointRequest request = body == null ? new ClubDtos.CheckpointRequest() : body;
        WorkspaceId workspace = session.workspaceOf(user);
        requireWorkspaceFor(user, workspace, WRITE);
        return view(
                club.submitCheckpoint(
                        session,
                        user,
                        workspace,
                        request.getTag(),
                        request.getNote(),
                        request.getContent()));
    }

    @GetMapping("/sessions/{sessionId}/checkpoints")
    public List<ClubDtos.CheckpointDto> list(@PathVariable String sessionId, Principal principal) {
        UserId user = requireUser(principal);
        ClubSession session = registry.findSession(sessionId);
        requireMember(session, user);
        List<CheckpointService.Submission> found = checkpoints.bySession(sessionId);
        List<ClubDtos.CheckpointDto> result = new ArrayList<ClubDtos.CheckpointDto>();
        for (int index = 0; index < found.size(); index++) {
            result.add(view(found.get(index)));
        }
        return result;
    }

    @PostMapping("/sessions/{sessionId}/checkpoints/{submissionId}/feedback")
    public ClubDtos.StatusDto feedback(
            @PathVariable String sessionId,
            @PathVariable String submissionId,
            @RequestBody(required = false) ClubDtos.FeedbackDto body,
            Principal principal) {
        UserId user = requireUser(principal);
        ClubSession session = registry.findSession(sessionId);
        ClubDtos.FeedbackDto request = body == null ? new ClubDtos.FeedbackDto() : body;
        checkpoints.feedback(session, user, submissionId, request.getFeedback());
        return new ClubDtos.StatusDto("recorded");
    }

    @PostMapping("/sessions/{sessionId}/checkpoints/{submissionId}/promote")
    public ClubDtos.ProjectDto promote(
            @PathVariable String sessionId,
            @PathVariable String submissionId,
            @RequestBody(required = false) ClubDtos.CurateRequest body,
            Principal principal) {
        UserId user = requireUser(principal);
        ClubSession session = registry.findSession(sessionId);
        ClubDtos.CurateRequest request = body == null ? new ClubDtos.CurateRequest() : body;
        return view(
                checkpoints.promote(
                        session,
                        user,
                        submissionId,
                        request.getTitle(),
                        request.getTags(),
                        request.getScreenshots()));
    }

    @GetMapping("/showcase")
    public List<ClubDtos.ProjectDto> showcase(Principal principal) {
        requireUser(principal);
        List<ShowcaseGallery.Project> found = club.showcase().list();
        List<ClubDtos.ProjectDto> result = new ArrayList<ClubDtos.ProjectDto>();
        for (int index = 0; index < found.size(); index++) {
            result.add(view(found.get(index)));
        }
        return result;
    }

    @GetMapping("/showcase/{id}")
    public ClubDtos.ProjectDto project(@PathVariable String id, Principal principal) {
        requireUser(principal);
        ShowcaseGallery.Project found = club.showcase().find(id);
        if (found == null) {
            throw ApiFailureException.badRequest("Unknown showcase project");
        }
        return view(found);
    }

    @PostMapping("/showcase")
    @ResponseStatus(HttpStatus.CREATED)
    public ClubDtos.ProjectDto curate(
            @RequestBody(required = false) ClubDtos.CurateRequest body, Principal principal) {
        UserId admin = requireAdmin(principal);
        ClubDtos.CurateRequest request = body == null ? new ClubDtos.CurateRequest() : body;
        return view(
                club.showcase()
                        .curate(
                                admin,
                                request.getTitle(),
                                request.getDescription(),
                                request.getTags(),
                                request.getScreenshots(),
                                request.getSourceLabel()));
    }

    @GetMapping("/sessions/{sessionId}/export")
    public ClubDtos.ExportDto export(@PathVariable String sessionId, Principal principal) {
        UserId user = requireUser(principal);
        ClubSession session = registry.findSession(sessionId);
        requireMember(session, user);
        ClubDtos.ExportDto result = new ClubDtos.ExportDto();
        result.setPatch(club.exporter().patch(session, checkpoints.bySession(sessionId)));
        return result;
    }

    private static void requireMember(ClubSession session, UserId user) {
        if (session.roleOf(user) == null) {
            throw ApiFailureException.forbidden();
        }
    }

    private static ClubDtos.LessonDto view(TutorialLesson lesson) {
        ClubDtos.LessonDto result = new ClubDtos.LessonDto();
        result.setId(lesson.id());
        result.setTitle(lesson.title());
        result.setObjective(lesson.objective());
        List<String> steps = new ArrayList<String>();
        for (int index = 0; index < lesson.steps().size(); index++) {
            steps.add(lesson.steps().get(index).id());
        }
        result.setSteps(steps);
        return result;
    }

    private static ClubDtos.StepResultDto view(ClubService.StepResult result) {
        ClubDtos.StepResultDto dto = new ClubDtos.StepResultDto();
        dto.setPassed(result.passed());
        dto.setDetail(result.detail());
        dto.setAggregate(result.aggregateCompleted());
        dto.setPersonal(result.personalCompleted());
        return dto;
    }

    private static ClubDtos.CheckpointDto view(CheckpointService.Submission submission) {
        ClubDtos.CheckpointDto result = new ClubDtos.CheckpointDto();
        result.setId(submission.id());
        result.setSnapshotTag(submission.snapshotTag());
        result.setNote(submission.note());
        result.setFeedback(submission.feedback());
        result.setPromotedTo(submission.promotedTo());
        return result;
    }

    private static ClubDtos.ProjectDto view(ShowcaseGallery.Project project) {
        ClubDtos.ProjectDto result = new ClubDtos.ProjectDto();
        result.setId(project.id());
        result.setTitle(project.title());
        result.setDescription(project.description());
        result.setTags(project.tags());
        result.setScreenshots(project.screenshots());
        result.setSourceLabel(project.sourceLabel());
        return result;
    }
}
