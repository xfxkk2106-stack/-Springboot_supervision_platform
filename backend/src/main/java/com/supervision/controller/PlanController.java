package com.supervision.controller;

import com.supervision.common.Result;
import com.supervision.dto.PlanCreateDTO;
import com.supervision.entity.LearningPlan;
import com.supervision.entity.RoomMember;
import com.supervision.mapper.RoomMemberMapper;
import com.supervision.service.PlanService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/plan")
public class PlanController {

    @Autowired
    private PlanService planService;

    @Autowired
    private RoomMemberMapper roomMemberMapper;

    @PostMapping("/create")
    public Result<LearningPlan> createPlan(@Valid @RequestBody PlanCreateDTO dto, HttpServletRequest request) {
        Long memberId = (Long) request.getAttribute("memberId");
        return Result.success(planService.createPlan(memberId, dto));
    }

    @GetMapping("/list")
    public Result<List<LearningPlan>> getPlanList(
            @RequestParam(required = false) String type,
            HttpServletRequest request) {
        Long memberId = (Long) request.getAttribute("memberId");
        return Result.success(planService.getPlanList(memberId, type));
    }

    @GetMapping("/member/{memberId}")
    public Result<List<LearningPlan>> getMemberPlanList(
            @PathVariable Long memberId,
            @RequestParam(required = false) String type,
            HttpServletRequest request) {
        Long currentMemberId = (Long) request.getAttribute("memberId");
        Long currentRoomId = (Long) request.getAttribute("roomId");

        // 验证目标成员与当前用户在同一房间
        RoomMember target = roomMemberMapper.selectById(memberId);
        if (target == null || !target.getRoomId().equals(currentRoomId)) {
            return Result.error(403, "无权查看该成员的计划");
        }

        return Result.success(planService.getMemberPlanList(memberId, type));
    }

    @PutMapping("/{id}/update")
    public Result<LearningPlan> updatePlan(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> body,
            HttpServletRequest request) {
        Long memberId = (Long) request.getAttribute("memberId");
        return Result.success(planService.updatePlan(memberId, id, body.get("status")));
    }

    @DeleteMapping("/{id}")
    public Result<?> deletePlan(@PathVariable Long id, HttpServletRequest request) {
        Long memberId = (Long) request.getAttribute("memberId");
        planService.deletePlan(memberId, id);
        return Result.success();
    }
}
