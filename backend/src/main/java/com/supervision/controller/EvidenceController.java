package com.supervision.controller;

import com.supervision.common.Result;
import com.supervision.dto.EvidenceReviewDTO;
import com.supervision.entity.EvidenceReview;
import com.supervision.entity.TaskEvidence;
import com.supervision.service.EvidenceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/evidence")
public class EvidenceController {

    @Autowired
    private EvidenceService evidenceService;

    @PostMapping("/upload")
    public Result<List<TaskEvidence>> uploadEvidence(
            @RequestParam("taskId") Long taskId,
            @RequestParam("files") MultipartFile[] files) throws IOException {
        return Result.success(evidenceService.uploadEvidence(taskId, files));
    }

    @GetMapping("/task/{taskId}")
    public Result<List<TaskEvidence>> getTaskEvidence(@PathVariable Long taskId) {
        return Result.success(evidenceService.getTaskEvidence(taskId));
    }

    @DeleteMapping("/{id}")
    public Result<?> deleteEvidence(@PathVariable Long id, HttpServletRequest request) {
        Long memberId = (Long) request.getAttribute("memberId");
        evidenceService.deleteEvidence(id, memberId);
        return Result.success();
    }

    @PostMapping("/{id}/review")
    public Result<EvidenceReview> reviewEvidence(
            @PathVariable Long id,
            @Valid @RequestBody EvidenceReviewDTO dto,
            HttpServletRequest request) {
        Long reviewerId = (Long) request.getAttribute("memberId");
        return Result.success(evidenceService.reviewEvidence(id, reviewerId, dto));
    }
}
