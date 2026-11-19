/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.api;

import dev.jahaziel.audit.api.AuditService;
import dev.jahaziel.audit.domain.AuditRecord;
import dev.jahaziel.authorization.api.AuthorizationService;
import dev.jahaziel.ops.ApprovalQueue;
import dev.jahaziel.ops.AuditQueryService;
import dev.jahaziel.ops.GdprService;
import dev.jahaziel.ops.OpsWebhookListener;
import dev.jahaziel.ops.ScheduledBackupService;
import dev.jahaziel.ops.SupportBundleService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Administrator-only support, backup, audit, GDPR and approval endpoints. */
@RestController
public final class OpsAdminController extends ApiControllerBase {
    private static final int AUDIT_LIMIT = 500;

    private final SupportBundleService bundles;
    private final ScheduledBackupService backups;
    private final AuditQueryService auditQuery;
    private final AuditService audit;
    private final GdprService gdpr;
    private final ApprovalQueue approvals;
    private final OpsWebhookListener webhooks;

    /** Creates the controller without webhook emission, for direct callers and tests. */
    public OpsAdminController(
            SupportBundleService bundles,
            ScheduledBackupService backups,
            AuditQueryService auditQuery,
            AuditService audit,
            GdprService gdpr,
            ApprovalQueue approvals,
            AuthorizationService auth) {
        this(bundles, backups, auditQuery, audit, gdpr, approvals, auth, null);
    }

    /** Creates the controller with the ops webhook listener. */
    @Autowired
    public OpsAdminController(
            SupportBundleService bundles,
            ScheduledBackupService backups,
            AuditQueryService auditQuery,
            AuditService audit,
            GdprService gdpr,
            ApprovalQueue approvals,
            AuthorizationService auth,
            OpsWebhookListener webhooks) {
        super(auth);
        this.bundles = bundles;
        this.backups = backups;
        this.auditQuery = auditQuery;
        this.audit = audit;
        this.gdpr = gdpr;
        this.approvals = approvals;
        this.webhooks = webhooks;
    }

    @PostMapping("/api/v1/admin/ops/support-bundle")
    public OpsDtos.BundleDto supportBundle(Principal principal) {
        requireAdmin(principal);
        Path bundle = bundles.createBundle(null, null, null);
        OpsDtos.BundleDto result = new OpsDtos.BundleDto();
        result.setFileName(bundle.getFileName().toString());
        result.setVerified(bundles.verify(bundle));
        return result;
    }

    @GetMapping("/api/v1/admin/ops/backups/status")
    public OpsDtos.BackupStatusDto backupStatus(Principal principal) {
        requireAdmin(principal);
        OpsDtos.BackupStatusDto result = new OpsDtos.BackupStatusDto();
        Path latest = backups.latest();
        result.setLatest(latest == null ? "" : latest.getFileName().toString());
        result.setCount(backups.listBackups().size());
        result.setRpoMinutes(ScheduledBackupService.RPO_MINUTES);
        result.setRtoMinutes(ScheduledBackupService.RTO_MINUTES);
        return result;
    }

    @PostMapping("/api/v1/admin/ops/audit/query")
    public List<OpsDtos.AuditDto> queryAudit(
            @RequestBody(required = false) OpsDtos.AuditQueryRequest body, Principal principal) {
        requireAdmin(principal);
        OpsDtos.AuditQueryRequest request = body == null ? new OpsDtos.AuditQueryRequest() : body;
        AuditQueryService.AuditFilter filter = filter(request);
        List<AuditQueryService.AuditEntry> found = auditQuery.query(entriesFor(null), filter);
        List<OpsDtos.AuditDto> result = new ArrayList<OpsDtos.AuditDto>();
        for (int index = 0; index < found.size(); index++) {
            result.add(view(found.get(index)));
        }
        return result;
    }

    @PostMapping("/api/v1/admin/ops/audit/export")
    public OpsDtos.AuditExportDto exportAudit(
            @RequestBody(required = false) OpsDtos.AuditQueryRequest body, Principal principal) {
        requireAdmin(principal);
        OpsDtos.AuditQueryRequest request = body == null ? new OpsDtos.AuditQueryRequest() : body;
        List<AuditQueryService.AuditEntry> found =
                auditQuery.query(entriesFor(null), filter(request));
        OpsDtos.AuditExportDto result = new OpsDtos.AuditExportDto();
        if ("json".equalsIgnoreCase(request.getFormat())) {
            result.setFormat("json");
            result.setContent(auditQuery.toJson(found));
        } else {
            result.setFormat("csv");
            result.setContent(auditQuery.toCsv(found));
        }
        return result;
    }

    @PostMapping("/api/v1/admin/ops/gdpr/export")
    public OpsDtos.GdprDto exportUser(
            @RequestBody(required = false) OpsDtos.GdprRequest body, Principal principal) {
        requireAdmin(principal);
        OpsDtos.GdprRequest request = body == null ? new OpsDtos.GdprRequest() : body;
        String userId = request.getUserId();
        Path archive = gdpr.exportUser(userId, new HashMap<String, String>(), entriesFor(userId));
        return new OpsDtos.GdprDto(archive.getFileName().toString());
    }

    @PostMapping("/api/v1/admin/ops/gdpr/delete")
    public OpsDtos.GdprDto deleteUser(
            @RequestBody(required = false) OpsDtos.GdprRequest body, Principal principal) {
        requireAdmin(principal);
        OpsDtos.GdprRequest request = body == null ? new OpsDtos.GdprRequest() : body;
        String pseudonym =
                gdpr.deleteUser(
                        request.getUserId(),
                        request.getConfirmation(),
                        entriesFor(request.getUserId()));
        return new OpsDtos.GdprDto(pseudonym);
    }

    @GetMapping("/api/v1/admin/ops/approvals")
    public List<OpsDtos.ApprovalDto> pending(Principal principal) {
        requireAdmin(principal);
        List<ApprovalQueue.ApprovalRequest> found = approvals.pending();
        List<OpsDtos.ApprovalDto> result = new ArrayList<OpsDtos.ApprovalDto>();
        for (int index = 0; index < found.size(); index++) {
            result.add(view(found.get(index)));
        }
        return result;
    }

    @PostMapping("/api/v1/admin/ops/approvals")
    public OpsDtos.ApprovalDto submit(
            @RequestBody(required = false) OpsDtos.ApprovalSubmitRequest body,
            Principal principal) {
        String admin = requireAdmin(principal).value().toString();
        OpsDtos.ApprovalSubmitRequest request =
                body == null ? new OpsDtos.ApprovalSubmitRequest() : body;
        Map<String, String> payload =
                request.getPayload() == null ? new HashMap<String, String>() : request.getPayload();
        ApprovalQueue.ApprovalRequest created =
                approvals.find(
                        approvals.submit(request.getType(), admin, request.getTarget(), payload));
        return view(created);
    }

    @PostMapping("/api/v1/admin/ops/approvals/{id}/approve")
    public OpsDtos.StatusDto approve(@PathVariable String id, Principal principal) {
        String admin = requireAdmin(principal).value().toString();
        approvals.approve(id, admin);
        notifyDecision(id, ApprovalQueue.APPROVED, admin, "");
        return new OpsDtos.StatusDto("APPROVED");
    }

    @PostMapping("/api/v1/admin/ops/approvals/{id}/reject")
    public OpsDtos.StatusDto reject(
            @PathVariable String id,
            @RequestBody(required = false) OpsDtos.DecisionRequest body,
            Principal principal) {
        String admin = requireAdmin(principal).value().toString();
        OpsDtos.DecisionRequest request = body == null ? new OpsDtos.DecisionRequest() : body;
        approvals.reject(id, admin, request.getReason());
        notifyDecision(id, ApprovalQueue.REJECTED, admin, request.getReason());
        return new OpsDtos.StatusDto("REJECTED");
    }

    private void notifyDecision(String id, String action, String approver, String reason) {
        if (webhooks != null) {
            webhooks.approvalDecided(new ApprovalQueue.Decision(id, action, approver, reason));
        }
    }

    private AuditQueryService.AuditFilter filter(OpsDtos.AuditQueryRequest request) {
        AuditQueryService.AuditFilter result = new AuditQueryService.AuditFilter();
        if (request.getFrom() != null) {
            result.from(parseInstant(request.getFrom()));
        }
        if (request.getTo() != null) {
            result.to(parseInstant(request.getTo()));
        }
        result.actor(request.getActor());
        result.action(request.getAction());
        result.workspace(request.getWorkspace());
        result.result(request.getResult());
        return result;
    }

    private List<AuditQueryService.AuditEntry> entriesFor(String actor) {
        List<AuditRecord> records = audit.latest(AUDIT_LIMIT);
        List<AuditQueryService.AuditEntry> result = new ArrayList<AuditQueryService.AuditEntry>();
        for (int index = 0; index < records.size(); index++) {
            AuditRecord record = records.get(index);
            if (actor != null && !actor.equals(record.actor())) {
                continue;
            }
            String target = record.target() == null ? "" : record.target().orElse("");
            String previous = record.prevHash() == null ? "" : record.prevHash().orElse("");
            String outcome = record.outcome() == null ? "" : record.outcome().name();
            result.add(
                    new AuditQueryService.AuditEntry(
                            record.eventId().toString(),
                            record.timestamp(),
                            record.actor(),
                            record.action(),
                            target,
                            outcome,
                            "",
                            previous));
        }
        return result;
    }

    private static OpsDtos.AuditDto view(AuditQueryService.AuditEntry entry) {
        OpsDtos.AuditDto result = new OpsDtos.AuditDto();
        result.setEventId(entry.eventId());
        result.setTimestamp(entry.timestamp() == null ? "" : entry.timestamp().toString());
        result.setActor(entry.actor());
        result.setAction(entry.action());
        result.setWorkspace(entry.workspace());
        result.setResult(entry.result());
        return result;
    }

    private static OpsDtos.ApprovalDto view(ApprovalQueue.ApprovalRequest request) {
        OpsDtos.ApprovalDto result = new OpsDtos.ApprovalDto();
        result.setId(request.id());
        result.setType(request.type());
        result.setRequester(request.requester());
        result.setTarget(request.target());
        result.setStatus(request.status());
        result.setCreatedAt(request.createdAt() == null ? "" : request.createdAt().toString());
        return result;
    }
}
