package com.supervision.controller;

import com.supervision.common.Result;
import com.supervision.dto.TaskCreateDTO;
import com.supervision.entity.DailyTask;
import com.supervision.entity.RoomMember;
import com.supervision.mapper.RoomMemberMapper;
import com.supervision.service.TaskService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/task")
public class TaskController {

    @Autowired
    private TaskService taskService;

    @Autowired
    private RoomMemberMapper roomMemberMapper;

    @PostMapping("/create")
    public Result<DailyTask> createTask(@Valid @RequestBody TaskCreateDTO dto, HttpServletRequest request) {
        Long memberId = (Long) request.getAttribute("memberId");
        return Result.success(taskService.createTask(memberId, dto));
    }

    @GetMapping("/today")
    public Result<List<DailyTask>> getTodayTasks(HttpServletRequest request) {
        Long memberId = (Long) request.getAttribute("memberId");
        Long roomId = (Long) request.getAttribute("roomId");
        return Result.success(taskService.getTodayTasks(memberId, roomId));
    }

    @GetMapping("/my-today")
    public Result<List<DailyTask>> getMyTodayTasks(HttpServletRequest request) {
        Long memberId = (Long) request.getAttribute("memberId");
        return Result.success(taskService.getMyTodayTasks(memberId));
    }

    @GetMapping("/member/{memberId}")
    public Result<List<DailyTask>> getMemberTodayTasks(@PathVariable Long memberId) {
        return Result.success(taskService.getMemberTodayTasks(memberId));
    }

    @GetMapping("/member/{memberId}/history")
    public Result<List<DailyTask>> getMemberHistoryTasks(
            @PathVariable Long memberId,
            @RequestParam(defaultValue = "30") int days,
            HttpServletRequest request) {
        Long currentRoomId = (Long) request.getAttribute("roomId");

        // 验证目标成员与当前用户在同一房间
        RoomMember target = roomMemberMapper.selectById(memberId);
        if (target == null || !target.getRoomId().equals(currentRoomId)) {
            return Result.error(403, "无权查看该成员的任务");
        }

        return Result.success(taskService.getMemberHistoryTasks(memberId, days));
    }

    @PutMapping("/{id}/complete")
    public Result<DailyTask> completeTask(@PathVariable Long id, HttpServletRequest request) {
        Long memberId = (Long) request.getAttribute("memberId");
        return Result.success(taskService.completeTask(memberId, id));
    }

    @DeleteMapping("/{id}")
    public Result<?> deleteTask(@PathVariable Long id, HttpServletRequest request) {
        Long memberId = (Long) request.getAttribute("memberId");
        taskService.deleteTask(memberId, id);
        return Result.success();
    }

    @PostMapping("/leave")
    public Result<?> requestLeave(HttpServletRequest request) {
        Long memberId = (Long) request.getAttribute("memberId");
        taskService.requestLeave(memberId);
        return Result.success("已请假");
    }

    @DeleteMapping("/leave")
    public Result<?> cancelLeave(HttpServletRequest request) {
        Long memberId = (Long) request.getAttribute("memberId");
        taskService.cancelLeave(memberId);
        return Result.success("已取消请假");
    }

    @GetMapping("/room-status")
    public Result<Map<String, Object>> getRoomStatus(HttpServletRequest request) {
        Long roomId = (Long) request.getAttribute("roomId");
        if (roomId == null) {
            // 用户不在房间中，返回空状态
            return Result.success(Map.of(
                "allCompleted", false,
                "hasTomorrowPlans", false,
                "members", List.of()
            ));
        }
        return Result.success(taskService.getRoomCompletionStatus(roomId));
    }

    @GetMapping("/plan-status")
    public Result<Map<String, Object>> getPlanStatus(HttpServletRequest request) {
        Long memberId = (Long) request.getAttribute("memberId");
        Long roomId = (Long) request.getAttribute("roomId");
        if (memberId == null || roomId == null) {
            // 用户不在房间中，返回默认状态
            return Result.success(Map.of(
                "hasTodayTasks", false,
                "hasTomorrowPlans", false,
                "allOthersCompleted", false,
                "myTasksAllDone", false,
                "canCreatePlan", false,
                "canAddTask", false,
                "isFirstDay", false
            ));
        }
        return Result.success(taskService.getPlanStatus(memberId, roomId));
    }
}
