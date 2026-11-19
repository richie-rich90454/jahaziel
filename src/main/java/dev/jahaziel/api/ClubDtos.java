/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api;

import java.util.List;

/** Plain request and response bodies for the Developer Club endpoints. */
public final class ClubDtos {
    private ClubDtos() {}

    /** Session creation input. Timestamps use ISO-8601 text. */
    public static final class CreateRequest {
        private String template;
        private String startsAt;
        private String endsAt;
        private int capacity;

        public String getTemplate() {
            return template;
        }

        public void setTemplate(String value) {
            this.template = value;
        }

        public String getStartsAt() {
            return startsAt;
        }

        public void setStartsAt(String value) {
            this.startsAt = value;
        }

        public String getEndsAt() {
            return endsAt;
        }

        public void setEndsAt(String value) {
            this.endsAt = value;
        }

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int value) {
            this.capacity = value;
        }
    }

    /** Join code and requested role. */
    public static final class JoinRequest {
        private String code;
        private String role;

        public String getCode() {
            return code;
        }

        public void setCode(String value) {
            this.code = value;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String value) {
            this.role = value;
        }
    }

    /** One session view; the join code is only returned to the authenticated caller. */
    public static final class SessionDto {
        private String id;
        private String joinCode;
        private String template;
        private int capacity;
        private int participants;
        private boolean leaderboard;
        private boolean locked;

        public String getId() {
            return id;
        }

        public void setId(String value) {
            this.id = value;
        }

        public String getJoinCode() {
            return joinCode;
        }

        public void setJoinCode(String value) {
            this.joinCode = value;
        }

        public String getTemplate() {
            return template;
        }

        public void setTemplate(String value) {
            this.template = value;
        }

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int value) {
            this.capacity = value;
        }

        public int getParticipants() {
            return participants;
        }

        public void setParticipants(int value) {
            this.participants = value;
        }

        public boolean isLeaderboard() {
            return leaderboard;
        }

        public void setLeaderboard(boolean value) {
            this.leaderboard = value;
        }

        public boolean isLocked() {
            return locked;
        }

        public void setLocked(boolean value) {
            this.locked = value;
        }
    }

    /** A workspace identifier returned after a fork or demo follow. */
    public static final class WorkspaceRefDto {
        private String workspaceId;

        public String getWorkspaceId() {
            return workspaceId;
        }

        public void setWorkspaceId(String value) {
            this.workspaceId = value;
        }
    }

    /** Raw lesson markdown submitted by an instructor. */
    public static final class LessonRequest {
        private String markdown;

        public String getMarkdown() {
            return markdown;
        }

        public void setMarkdown(String value) {
            this.markdown = value;
        }
    }

    /** One parsed lesson view. */
    public static final class LessonDto {
        private String id;
        private String title;
        private String objective;
        private List<String> steps;

        public String getId() {
            return id;
        }

        public void setId(String value) {
            this.id = value;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String value) {
            this.title = value;
        }

        public String getObjective() {
            return objective;
        }

        public void setObjective(String value) {
            this.objective = value;
        }

        public List<String> getSteps() {
            return steps;
        }

        public void setSteps(List<String> value) {
            this.steps = value;
        }
    }

    /** Identifies a lesson step to verify. */
    public static final class StepRequest {
        private String lessonId;
        private String stepId;

        public String getLessonId() {
            return lessonId;
        }

        public void setLessonId(String value) {
            this.lessonId = value;
        }

        public String getStepId() {
            return stepId;
        }

        public void setStepId(String value) {
            this.stepId = value;
        }
    }

    /** Bounded verification result. */
    public static final class StepResultDto {
        private boolean passed;
        private String detail;
        private int aggregate;
        private int personal;

        public boolean isPassed() {
            return passed;
        }

        public void setPassed(boolean value) {
            this.passed = value;
        }

        public String getDetail() {
            return detail;
        }

        public void setDetail(String value) {
            this.detail = value;
        }

        public int getAggregate() {
            return aggregate;
        }

        public void setAggregate(int value) {
            this.aggregate = value;
        }

        public int getPersonal() {
            return personal;
        }

        public void setPersonal(int value) {
            this.personal = value;
        }
    }

    /** Checkpoint submission input. */
    public static final class CheckpointRequest {
        private String tag;
        private String note;
        private String content;

        public String getTag() {
            return tag;
        }

        public void setTag(String value) {
            this.tag = value;
        }

        public String getNote() {
            return note;
        }

        public void setNote(String value) {
            this.note = value;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String value) {
            this.content = value;
        }
    }

    /** One checkpoint view; the submitted body is never returned to a participant. */
    public static final class CheckpointDto {
        private String id;
        private String snapshotTag;
        private String note;
        private String feedback;
        private String promotedTo;

        public String getId() {
            return id;
        }

        public void setId(String value) {
            this.id = value;
        }

        public String getSnapshotTag() {
            return snapshotTag;
        }

        public void setSnapshotTag(String value) {
            this.snapshotTag = value;
        }

        public String getNote() {
            return note;
        }

        public void setNote(String value) {
            this.note = value;
        }

        public String getFeedback() {
            return feedback;
        }

        public void setFeedback(String value) {
            this.feedback = value;
        }

        public String getPromotedTo() {
            return promotedTo;
        }

        public void setPromotedTo(String value) {
            this.promotedTo = value;
        }
    }

    /** Instructor feedback on a checkpoint. */
    public static final class FeedbackDto {
        private String feedback;

        public String getFeedback() {
            return feedback;
        }

        public void setFeedback(String value) {
            this.feedback = value;
        }
    }

    /** Admin curation input; screenshots are references, never bytes. */
    public static final class CurateRequest {
        private String title;
        private String description;
        private List<String> tags;
        private List<String> screenshots;
        private String sourceLabel;

        public String getTitle() {
            return title;
        }

        public void setTitle(String value) {
            this.title = value;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String value) {
            this.description = value;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> value) {
            this.tags = value;
        }

        public List<String> getScreenshots() {
            return screenshots;
        }

        public void setScreenshots(List<String> value) {
            this.screenshots = value;
        }

        public String getSourceLabel() {
            return sourceLabel;
        }

        public void setSourceLabel(String value) {
            this.sourceLabel = value;
        }
    }

    /** One showcase preview. */
    public static final class ProjectDto {
        private String id;
        private String title;
        private String description;
        private List<String> tags;
        private List<String> screenshots;
        private String sourceLabel;

        public String getId() {
            return id;
        }

        public void setId(String value) {
            this.id = value;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String value) {
            this.title = value;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String value) {
            this.description = value;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> value) {
            this.tags = value;
        }

        public List<String> getScreenshots() {
            return screenshots;
        }

        public void setScreenshots(List<String> value) {
            this.screenshots = value;
        }

        public String getSourceLabel() {
            return sourceLabel;
        }

        public void setSourceLabel(String value) {
            this.sourceLabel = value;
        }
    }

    /** Chat post input. */
    public static final class ChatRequest {
        private String text;

        public String getText() {
            return text;
        }

        public void setText(String value) {
            this.text = value;
        }
    }

    /** One club chat message view. */
    public static final class ChatDto {
        private String id;
        private String author;
        private String text;
        private boolean pinned;

        public String getId() {
            return id;
        }

        public void setId(String value) {
            this.id = value;
        }

        public String getAuthor() {
            return author;
        }

        public void setAuthor(String value) {
            this.author = value;
        }

        public String getText() {
            return text;
        }

        public void setText(String value) {
            this.text = value;
        }

        public boolean isPinned() {
            return pinned;
        }

        public void setPinned(boolean value) {
            this.pinned = value;
        }
    }

    /** A participant target for staff actions. */
    public static final class TargetRequest {
        private String target;

        public String getTarget() {
            return target;
        }

        public void setTarget(String value) {
            this.target = value;
        }
    }

    /** A message identifier for pinning. */
    public static final class MessageRefRequest {
        private String messageId;

        public String getMessageId() {
            return messageId;
        }

        public void setMessageId(String value) {
            this.messageId = value;
        }
    }

    /** The next demo controller. */
    public static final class TransferRequest {
        private String next;

        public String getNext() {
            return next;
        }

        public void setNext(String value) {
            this.next = value;
        }
    }

    /** Advisory patch text for a session export. */
    public static final class ExportDto {
        private String patch;

        public String getPatch() {
            return patch;
        }

        public void setPatch(String value) {
            this.patch = value;
        }
    }

    /** Generic status body. */
    public static final class StatusDto {
        private String status;

        public StatusDto() {}

        public StatusDto(String value) {
            this.status = value;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String value) {
            this.status = value;
        }
    }
}
