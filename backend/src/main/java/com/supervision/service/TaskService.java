package com.supervision.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.supervision.common.BusinessException;
import com.supervision.dto.TaskCreateDTO;
import com.supervision.entity.*;
import com.supervision.mapper.*;
import com.supervision.netty.NettyWebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    @Autowired
    private DailyTaskMapper taskMapper;

    @Autowired
    private TaskEvidenceMapper evidenceMapper;

    @Autowired
    private RoomMapper roomMapper;

    @Autowired
    private RoomMemberMapper roomMemberMapper;

    @Autowired
    private DailyLeaveMapper dailyLeaveMapper;

    @Autowired
    private TomorrowPlanMapper tomorrowPlanMapper;

    public DailyTask createTask(Long memberId, TaskCreateDTO dto) {
        DailyTask task = new DailyTask();
        task.setMemberId(memberId);
        task.setSubject(dto.getSubject());
        task.setTaskContent(dto.getTaskContent());
        task.setIsCompleted(0);
        task.setTaskDate(LocalDate.now());
        task.setSortOrder(0);
        taskMapper.insert(task);

        // 通知房间内其他成员
        try {
            String roomCode = getRoomCodeByMemberId(memberId);
            if (roomCode != null) {
                NettyWebSocketServer.sendToRoom(roomCode, "task_created", task.getId());
            }
        } catch (Exception e) {
            // 忽略
        }

        return task;
    }

    public List<DailyTask> getTodayTasks(Long memberId, Long roomId) {
        return taskMapper.selectByRoomAndDate(roomId, LocalDate.now());
    }

    /**
     * 获取当前用户今日任务
     */
    public List<DailyTask> getMyTodayTasks(Long memberId) {
        return taskMapper.selectByMemberAndDate(memberId, LocalDate.now());
    }

    /**
     * 获取指定成员今日任务
     */
    public List<DailyTask> getMemberTodayTasks(Long memberId) {
        return taskMapper.selectByMemberAndDate(memberId, LocalDate.now());
    }

    /**
     * 获取指定成员历史任务（排除今天）
     */
    public List<DailyTask> getMemberHistoryTasks(Long memberId, int days) {
        LocalDate endDate = LocalDate.now().minusDays(1);
        LocalDate startDate = LocalDate.now().minusDays(days);
        return taskMapper.selectByMemberAndDateRange(memberId, startDate, endDate);
    }

    public DailyTask completeTask(Long memberId, Long taskId) {
        DailyTask task = taskMapper.selectById(taskId);
        if (task == null || !task.getMemberId().equals(memberId)) {
            throw new BusinessException("任务不存在");
        }

        if (task.getIsCompleted() == 1) {
            throw new BusinessException("任务已完成");
        }

        // 检查是否有证据
        Long evidenceCount = evidenceMapper.selectCount(
                new LambdaQueryWrapper<TaskEvidence>().eq(TaskEvidence::getTaskId, taskId)
        );
        if (evidenceCount == 0) {
            throw BusinessException.taskNoEvidence();
        }

        task.setIsCompleted(1);
        task.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        // 计算房间完成状态并通过 WebSocket 广播（含状态数据，实现即时解锁）
        try {
            RoomMember completer = roomMemberMapper.selectById(memberId);
            if (completer != null) {
                Long roomId = completer.getRoomId();
                Room room = roomMapper.selectById(roomId);
                if (room != null) {
                    Map<String, Object> roomStatus = calculateRoomStatus(roomId);

                    Map<String, Object> broadcastData = new HashMap<>();
                    broadcastData.put("taskId", task.getId());
                    broadcastData.put("roomStatus", roomStatus);
                    NettyWebSocketServer.sendToRoom(room.getRoomCode(), "task_completed", broadcastData);
                }
            }
        } catch (Exception e) {
            log.error("广播任务完成事件失败", e);
        }

        return task;
    }

    public void deleteTask(Long memberId, Long taskId) {
        DailyTask task = taskMapper.selectById(taskId);
        if (task == null || !task.getMemberId().equals(memberId)) {
            throw new BusinessException("任务不存在");
        }
        if (task.getIsCompleted() == 1) {
            throw new BusinessException("已完成的任务不能删除");
        }
        taskMapper.deleteById(taskId);
    }

    /**
     * 请假：创建今日请假记录 + 自动删除今日任务
     */
    public void requestLeave(Long memberId) {
        // 检查今天是否已请假
        if (isOnLeave(memberId)) {
            throw new BusinessException("今天已经请假了");
        }

        // 创建请假记录
        DailyLeave leave = new DailyLeave();
        leave.setMemberId(memberId);
        leave.setLeaveDate(LocalDate.now());
        dailyLeaveMapper.insert(leave);

        // 自动删除今日未完成的任务
        List<DailyTask> todayTasks = taskMapper.selectByMemberAndDate(memberId, LocalDate.now());
        for (DailyTask task : todayTasks) {
            if (task.getIsCompleted() == 0) {
                taskMapper.deleteById(task.getId());
            }
        }

        // 通知房间
        try {
            String roomCode = getRoomCodeByMemberId(memberId);
            if (roomCode != null) {
                Map<String, Object> data = new HashMap<>();
                data.put("memberId", memberId);
                NettyWebSocketServer.sendToRoom(roomCode, "member_leave_changed", data);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 取消请假
     */
    public void cancelLeave(Long memberId) {
        int deleted = dailyLeaveMapper.delete(
                new LambdaQueryWrapper<DailyLeave>()
                        .eq(DailyLeave::getMemberId, memberId)
                        .eq(DailyLeave::getLeaveDate, LocalDate.now())
        );
        if (deleted == 0) {
            throw new BusinessException("今天没有请假记录");
        }

        // 通知房间
        try {
            String roomCode = getRoomCodeByMemberId(memberId);
            if (roomCode != null) {
                Map<String, Object> data = new HashMap<>();
                data.put("memberId", memberId);
                NettyWebSocketServer.sendToRoom(roomCode, "member_leave_changed", data);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 查询今天是否请假
     */
    public boolean isOnLeave(Long memberId) {
        return dailyLeaveMapper.selectCount(
                new LambdaQueryWrapper<DailyLeave>()
                        .eq(DailyLeave::getMemberId, memberId)
                        .eq(DailyLeave::getLeaveDate, LocalDate.now())
        ) > 0;
    }

    /**
     * 获取房间级完成状态
     * 自动转换：如果全员完成且有明日计划，自动转为今日任务
     */
    public Map<String, Object> getRoomCompletionStatus(Long roomId) {
        List<RoomMember> members = roomMemberMapper.selectList(
                new LambdaQueryWrapper<RoomMember>().eq(RoomMember::getRoomId, roomId)
        );
        LocalDate today = LocalDate.now();
        boolean allCompleted = true;
        boolean hasTomorrowPlans = false;
        List<Map<String, Object>> memberStatusList = new ArrayList<>();

        for (RoomMember member : members) {
            Map<String, Object> status = new HashMap<>();
            status.put("memberId", member.getId());
            status.put("displayName", member.getDisplayName());

            // 检查是否请假
            boolean onLeave = dailyLeaveMapper.selectCount(
                    new LambdaQueryWrapper<DailyLeave>()
                            .eq(DailyLeave::getMemberId, member.getId())
                            .eq(DailyLeave::getLeaveDate, today)
            ) > 0;

            if (onLeave) {
                status.put("totalTasks", 0);
                status.put("completedTasks", 0);
                status.put("isOnLeave", true);
                status.put("allDone", true);
            } else {
                List<DailyTask> tasks = taskMapper.selectByMemberAndDate(member.getId(), today);
                long completed = tasks.stream().filter(t -> t.getIsCompleted() == 1).count();
                boolean memberAllDone = !tasks.isEmpty() && completed == tasks.size();
                status.put("totalTasks", tasks.size());
                status.put("completedTasks", completed);
                status.put("isOnLeave", false);
                status.put("allDone", memberAllDone);
                if (!memberAllDone) {
                    allCompleted = false;
                }
            }
            memberStatusList.add(status);
        }

        // 检查是否有明日计划
        Long tomorrowPlanCount = tomorrowPlanMapper.selectCount(
                new LambdaQueryWrapper<TomorrowPlan>()
                        .eq(TomorrowPlan::getTaskDate, today.plusDays(1))
                        .in(TomorrowPlan::getMemberId, members.stream().map(RoomMember::getId).toList())
        );
        hasTomorrowPlans = tomorrowPlanCount > 0;

        // 全员完成且有明日计划 → 自动转换
        if (allCompleted && hasTomorrowPlans) {
            convertTomorrowToToday(roomId);
            hasTomorrowPlans = false; // 转换后不再有明日计划
        }

        Map<String, Object> result = new HashMap<>();
        result.put("allCompleted", allCompleted);
        result.put("hasTomorrowPlans", hasTomorrowPlans);
        result.put("members", memberStatusList);
        return result;
    }

    /**
     * 将明日计划转为今日任务
     */
    public void convertTomorrowToToday(Long roomId) {
        List<RoomMember> members = roomMemberMapper.selectList(
                new LambdaQueryWrapper<RoomMember>().eq(RoomMember::getRoomId, roomId)
        );
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        for (RoomMember member : members) {
            // 跳过已有今日任务的成员
            List<DailyTask> existingTasks = taskMapper.selectByMemberAndDate(member.getId(), today);
            if (!existingTasks.isEmpty()) {
                // 仍删除明日计划（已不需要）
                tomorrowPlanMapper.delete(
                        new LambdaQueryWrapper<TomorrowPlan>()
                                .eq(TomorrowPlan::getMemberId, member.getId())
                                .eq(TomorrowPlan::getTaskDate, tomorrow)
                );
                continue;
            }

            // 获取该成员的明日计划
            List<TomorrowPlan> tomorrowPlans = tomorrowPlanMapper.selectList(
                    new LambdaQueryWrapper<TomorrowPlan>()
                            .eq(TomorrowPlan::getMemberId, member.getId())
                            .eq(TomorrowPlan::getTaskDate, tomorrow)
                            .orderByAsc(TomorrowPlan::getSortOrder)
            );

            // 转为今日任务
            for (int i = 0; i < tomorrowPlans.size(); i++) {
                TomorrowPlan tp = tomorrowPlans.get(i);
                DailyTask task = new DailyTask();
                task.setMemberId(member.getId());
                task.setSubject(tp.getSubject());
                task.setTaskContent(tp.getTaskContent());
                task.setIsCompleted(0);
                task.setTaskDate(today);
                task.setSortOrder(i);
                taskMapper.insert(task);
            }

            // 删除已转换的明日计划
            if (!tomorrowPlans.isEmpty()) {
                tomorrowPlanMapper.delete(
                        new LambdaQueryWrapper<TomorrowPlan>()
                                .eq(TomorrowPlan::getMemberId, member.getId())
                                .eq(TomorrowPlan::getTaskDate, tomorrow)
                );
            }
        }

        // 通知房间
        try {
            String roomCode = roomMapper.selectById(roomId).getRoomCode();
            NettyWebSocketServer.sendToRoom(roomCode, "tomorrow_converted", null);
        } catch (Exception ignored) {
        }
    }

    /**
     * 计算房间完成状态（公共方法，供 completeTask 和 EvidenceService 共用）
     * 返回 roomStatus Map，含 allCompleted、hasTomorrowPlans、members
     */
    public Map<String, Object> calculateRoomStatus(Long roomId) {
        List<RoomMember> members = roomMemberMapper.selectList(
                new LambdaQueryWrapper<RoomMember>().eq(RoomMember::getRoomId, roomId)
        );
        LocalDate today = LocalDate.now();
        boolean allCompleted = true;
        List<Map<String, Object>> memberStatusList = new ArrayList<>();

        for (RoomMember m : members) {
            Map<String, Object> status = new HashMap<>();
            status.put("memberId", m.getId());
            status.put("displayName", m.getDisplayName());

            boolean onLeave = dailyLeaveMapper.selectCount(
                    new LambdaQueryWrapper<DailyLeave>()
                            .eq(DailyLeave::getMemberId, m.getId())
                            .eq(DailyLeave::getLeaveDate, today)
            ) > 0;

            if (onLeave) {
                status.put("totalTasks", 0);
                status.put("completedTasks", 0);
                status.put("isOnLeave", true);
                status.put("allDone", true);
            } else {
                List<DailyTask> tasks = taskMapper.selectByMemberAndDate(m.getId(), today);
                long completed = tasks.stream().filter(t -> t.getIsCompleted() == 1).count();
                boolean memberAllDone = !tasks.isEmpty() && completed == tasks.size();
                status.put("totalTasks", tasks.size());
                status.put("completedTasks", completed);
                status.put("isOnLeave", false);
                status.put("allDone", memberAllDone);
                if (!memberAllDone) {
                    allCompleted = false;
                }
            }
            memberStatusList.add(status);
        }

        // 全员完成时自动将明日计划转为今日任务
        if (allCompleted) {
            Long tomorrowPlanCount = tomorrowPlanMapper.selectCount(
                    new LambdaQueryWrapper<TomorrowPlan>()
                            .eq(TomorrowPlan::getTaskDate, today.plusDays(1))
                            .in(TomorrowPlan::getMemberId, members.stream().map(RoomMember::getId).toList())
            );
            if (tomorrowPlanCount > 0) {
                convertTomorrowToToday(roomId);
            }
        }

        Map<String, Object> roomStatus = new HashMap<>();
        roomStatus.put("allCompleted", allCompleted);
        roomStatus.put("hasTomorrowPlans", false);
        roomStatus.put("members", memberStatusList);
        return roomStatus;
    }

    private String getRoomCodeByMemberId(Long memberId) {
        RoomMember member = roomMemberMapper.selectById(memberId);
        if (member == null) return null;
        Room room = roomMapper.selectById(member.getRoomId());
        return room != null ? room.getRoomCode() : null;
    }
}
