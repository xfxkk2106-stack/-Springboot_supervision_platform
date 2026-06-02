package com.supervision.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.supervision.common.BusinessException;
import com.supervision.common.ResultCode;
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
        // 检查今日是否有计划任务（fromPlan=1），如有则锁定不可添加
        Long planTaskCount = taskMapper.selectCount(
                new LambdaQueryWrapper<DailyTask>()
                        .eq(DailyTask::getMemberId, memberId)
                        .eq(DailyTask::getTaskDate, LocalDate.now())
                        .eq(DailyTask::getFromPlan, 1)
        );
        if (planTaskCount > 0) {
            throw new BusinessException(ResultCode.TASK_LOCKED, "今日计划已生效，不可添加新任务");
        }

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
        if (task.getFromPlan() != null && task.getFromPlan() == 1) {
            throw new BusinessException(ResultCode.TASK_LOCKED, "计划任务不可删除");
        }
        taskMapper.deleteById(taskId);

        // 通知房间（携带 roomStatus）
        try {
            String roomCode = getRoomCodeByMemberId(memberId);
            if (roomCode != null) {
                Long roomId = getRoomIdByMemberId(memberId);
                Map<String, Object> data = new HashMap<>();
                data.put("memberId", memberId);
                data.put("taskId", taskId);
                data.put("roomStatus", calculateRoomStatus(roomId));
                NettyWebSocketServer.sendToRoom(roomCode, "task_deleted", data);
            }
        } catch (Exception e) {
            log.error("广播任务删除事件失败", e);
        }
    }

    /**
     * 请假：创建今日请假记录（不删除任务）
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

        // 通知房间（携带 roomStatus）
        try {
            String roomCode = getRoomCodeByMemberId(memberId);
            if (roomCode != null) {
                Long roomId = getRoomIdByMemberId(memberId);
                Map<String, Object> data = new HashMap<>();
                data.put("memberId", memberId);
                data.put("roomStatus", calculateRoomStatus(roomId));
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

        // 通知房间（携带 roomStatus）
        try {
            String roomCode = getRoomCodeByMemberId(memberId);
            if (roomCode != null) {
                Long roomId = getRoomIdByMemberId(memberId);
                Map<String, Object> data = new HashMap<>();
                data.put("memberId", memberId);
                data.put("roomStatus", calculateRoomStatus(roomId));
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

            // 请假用户也显示实际任务数
            List<DailyTask> tasks = taskMapper.selectByMemberAndDate(member.getId(), today);
            long completed = tasks.stream().filter(t -> t.getIsCompleted() == 1).count();
            boolean memberAllDone = !tasks.isEmpty() && completed == tasks.size();
            status.put("totalTasks", tasks.size());
            status.put("completedTasks", completed);
            status.put("isOnLeave", onLeave);
            status.put("allDone", onLeave || memberAllDone); // 请假用户视为完成
            if (!onLeave && !memberAllDone) {
                allCompleted = false;
            }
            memberStatusList.add(status);
        }

        // 检查是否有明日计划
        Long tomorrowPlanCount = tomorrowPlanMapper.selectCount(
                new LambdaQueryWrapper<TomorrowPlan>()
                        .eq(TomorrowPlan::getTaskDate, today.plusDays(1))
                        .in(TomorrowPlan::getMemberId, members.stream().map(RoomMember::getId).toList())
        );
        boolean hasTomorrowPlans = tomorrowPlanCount > 0;

        Map<String, Object> result = new HashMap<>();
        result.put("allCompleted", allCompleted);
        result.put("hasTomorrowPlans", hasTomorrowPlans);
        result.put("members", memberStatusList);
        return result;
    }

    /**
     * 将明日计划转为今日任务（手动转换，用于测试）
     */
    @SuppressWarnings("unused")
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
                task.setFromPlan(1);
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

            // 请假用户也显示实际任务数
            List<DailyTask> tasks = taskMapper.selectByMemberAndDate(m.getId(), today);
            long completed = tasks.stream().filter(t -> t.getIsCompleted() == 1).count();
            boolean memberAllDone = !tasks.isEmpty() && completed == tasks.size();
            status.put("totalTasks", tasks.size());
            status.put("completedTasks", completed);
            status.put("isOnLeave", onLeave);
            status.put("allDone", onLeave || memberAllDone); // 请假用户视为完成
            if (!onLeave && !memberAllDone) {
                allCompleted = false;
            }
            memberStatusList.add(status);
        }

        Map<String, Object> roomStatus = new HashMap<>();
        roomStatus.put("allCompleted", allCompleted);
        roomStatus.put("hasTomorrowPlans", false);
        roomStatus.put("members", memberStatusList);
        return roomStatus;
    }

    /**
     * 获取计划状态（供前端判断按钮状态）
     */
    public Map<String, Object> getPlanStatus(Long memberId, Long roomId) {
        LocalDate today = LocalDate.now();

        // 检查用户是否有今日任务
        List<DailyTask> myTasks = taskMapper.selectByMemberAndDate(memberId, today);
        boolean hasTodayTasks = !myTasks.isEmpty();

        // 检查用户是否有明日计划
        Long planCount = tomorrowPlanMapper.selectCount(
                new LambdaQueryWrapper<TomorrowPlan>()
                        .eq(TomorrowPlan::getMemberId, memberId)
                        .eq(TomorrowPlan::getTaskDate, today.plusDays(1))
        );
        boolean hasTomorrowPlans = planCount > 0;

        // 检查其他成员是否全部完成
        List<RoomMember> members = roomMemberMapper.selectList(
                new LambdaQueryWrapper<RoomMember>().eq(RoomMember::getRoomId, roomId)
        );
        boolean allOthersCompleted = true;
        for (RoomMember m : members) {
            if (m.getId().equals(memberId)) continue;
            boolean onLeave = dailyLeaveMapper.selectCount(
                    new LambdaQueryWrapper<DailyLeave>()
                            .eq(DailyLeave::getMemberId, m.getId())
                            .eq(DailyLeave::getLeaveDate, today)
            ) > 0;
            if (onLeave) continue;
            List<DailyTask> tasks = taskMapper.selectByMemberAndDate(m.getId(), today);
            boolean memberDone = !tasks.isEmpty() && tasks.stream().allMatch(t -> t.getIsCompleted() == 1);
            if (!memberDone) {
                allOthersCompleted = false;
                break;
            }
        }

        // 检查自身任务是否全部完成
        boolean myTasksAllDone = hasTodayTasks && myTasks.stream().allMatch(t -> t.getIsCompleted() == 1);

        // 检查是否请假
        boolean onLeave = isOnLeave(memberId);

        // 判断能力（请假用户需等全员完成才可制定计划）
        boolean canCreatePlan = (myTasksAllDone || onLeave) && allOthersCompleted;
        boolean canAddTask = !hasTodayTasks;

        // 判断是否为首日（joinedAt 的日期等于今天）
        RoomMember currentMember = roomMemberMapper.selectById(memberId);
        boolean isFirstDay = false;
        if (currentMember != null && currentMember.getJoinedAt() != null) {
            LocalDate joinedDate = currentMember.getJoinedAt().toLocalDate();
            isFirstDay = joinedDate.equals(today);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("hasTodayTasks", hasTodayTasks);
        result.put("hasTomorrowPlans", hasTomorrowPlans);
        result.put("allOthersCompleted", allOthersCompleted);
        result.put("myTasksAllDone", myTasksAllDone);
        result.put("canCreatePlan", canCreatePlan);
        result.put("canAddTask", canAddTask);
        result.put("isFirstDay", isFirstDay);
        return result;
    }

    /**
     * 测试用：模拟凌晨转换逻辑
     * 与 midnightConvertAllRooms 相同，但使用 tomorrow 的日期检查计划
     * （因为白天测试时，计划的 taskDate = tomorrow，而不是 today）
     */
    public void testMidnightConvert() {
        List<Room> rooms = roomMapper.selectList(
                new LambdaQueryWrapper<Room>().eq(Room::getStatus, 1)
        );
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        for (Room room : rooms) {
            try {
                // 第一步：先踢出没有填写明日计划的成员
                // 测试时检查 taskDate = tomorrow（用户创建的明日计划）
                kickMembersWithoutTomorrowPlan(room, tomorrow);

                // 第二步：清除当天的任务（测试时，当天任务的 taskDate = today）
                List<RoomMember> members = roomMemberMapper.selectList(
                        new LambdaQueryWrapper<RoomMember>().eq(RoomMember::getRoomId, room.getId())
                );
                taskMapper.delete(
                        new LambdaQueryWrapper<DailyTask>()
                                .eq(DailyTask::getTaskDate, today)
                                .in(DailyTask::getMemberId, members.stream().map(RoomMember::getId).toList())
                );
                log.info("房间 {} 已清除当天任务", room.getRoomCode());

                // 第三步：转换剩余成员的计划为今日任务
                for (RoomMember member : members) {
                    // 获取该成员的明日计划（taskDate = tomorrow）
                    List<TomorrowPlan> plans = tomorrowPlanMapper.selectList(
                            new LambdaQueryWrapper<TomorrowPlan>()
                                    .eq(TomorrowPlan::getMemberId, member.getId())
                                    .eq(TomorrowPlan::getTaskDate, tomorrow)
                                    .orderByAsc(TomorrowPlan::getSortOrder)
                    );

                    if (plans.isEmpty()) {
                        continue;
                    }

                    // 转为今日任务
                    for (int i = 0; i < plans.size(); i++) {
                        TomorrowPlan tp = plans.get(i);
                        DailyTask task = new DailyTask();
                        task.setMemberId(member.getId());
                        task.setSubject(tp.getSubject());
                        task.setTaskContent(tp.getTaskContent());
                        task.setIsCompleted(0);
                        task.setTaskDate(today);
                        task.setSortOrder(i);
                        task.setFromPlan(1);
                        taskMapper.insert(task);
                    }

                    // 删除已转换的计划
                    tomorrowPlanMapper.delete(
                            new LambdaQueryWrapper<TomorrowPlan>()
                                    .eq(TomorrowPlan::getMemberId, member.getId())
                                    .eq(TomorrowPlan::getTaskDate, tomorrow)
                    );
                }

                // 第四步：清除所有成员的请假记录（凌晨自动销假）
                dailyLeaveMapper.delete(
                        new LambdaQueryWrapper<DailyLeave>()
                                .eq(DailyLeave::getLeaveDate, today)
                                .in(DailyLeave::getMemberId, members.stream().map(RoomMember::getId).toList())
                );
                log.info("房间 {} 已清除请假记录", room.getRoomCode());

                // 通知房间
                NettyWebSocketServer.sendToRoom(room.getRoomCode(), "tomorrow_converted", null);
            } catch (Exception e) {
                log.error("测试凌晨转换房间 {} 失败", room.getId(), e);
            }
        }
    }

    /**
     * 凌晨转换所有房间的明日计划为今日任务
     */
    public void midnightConvertAllRooms() {
        List<Room> rooms = roomMapper.selectList(
                new LambdaQueryWrapper<Room>().eq(Room::getStatus, 1)
        );
        LocalDate today = LocalDate.now();
        // 0 点时 today 已是新的一天，昨日的"明日计划"的 taskDate = today
        for (Room room : rooms) {
            try {
                // 第一步：先踢出没有填写明日计划的成员
                // 检查 taskDate = today 的计划（即昨天创建的明日计划）
                kickMembersWithoutTomorrowPlan(room, today);

                // 第二步：清除昨天的任务
                List<RoomMember> members = roomMemberMapper.selectList(
                        new LambdaQueryWrapper<RoomMember>().eq(RoomMember::getRoomId, room.getId())
                );
                LocalDate yesterday = today.minusDays(1);
                taskMapper.delete(
                        new LambdaQueryWrapper<DailyTask>()
                                .eq(DailyTask::getTaskDate, yesterday)
                                .in(DailyTask::getMemberId, members.stream().map(RoomMember::getId).toList())
                );
                log.info("房间 {} 已清除昨日任务", room.getRoomCode());

                // 第三步：转换剩余成员的计划为今日任务
                for (RoomMember member : members) {
                    // 获取该成员的计划（taskDate = today，即昨日创建的明日计划）
                    List<TomorrowPlan> plans = tomorrowPlanMapper.selectList(
                            new LambdaQueryWrapper<TomorrowPlan>()
                                    .eq(TomorrowPlan::getMemberId, member.getId())
                                    .eq(TomorrowPlan::getTaskDate, today)
                                    .orderByAsc(TomorrowPlan::getSortOrder)
                    );

                    // 转为今日任务
                    for (int i = 0; i < plans.size(); i++) {
                        TomorrowPlan tp = plans.get(i);
                        DailyTask task = new DailyTask();
                        task.setMemberId(member.getId());
                        task.setSubject(tp.getSubject());
                        task.setTaskContent(tp.getTaskContent());
                        task.setIsCompleted(0);
                        task.setTaskDate(today);
                        task.setSortOrder(i);
                        task.setFromPlan(1);
                        taskMapper.insert(task);
                    }

                    // 删除已转换的计划
                    if (!plans.isEmpty()) {
                        tomorrowPlanMapper.delete(
                                new LambdaQueryWrapper<TomorrowPlan>()
                                        .eq(TomorrowPlan::getMemberId, member.getId())
                                        .eq(TomorrowPlan::getTaskDate, today)
                        );
                    }
                }

                // 第四步：清除所有成员的请假记录（凌晨自动销假）
                dailyLeaveMapper.delete(
                        new LambdaQueryWrapper<DailyLeave>()
                                .eq(DailyLeave::getLeaveDate, yesterday)
                                .in(DailyLeave::getMemberId, members.stream().map(RoomMember::getId).toList())
                );
                log.info("房间 {} 已清除昨日请假记录", room.getRoomCode());

                // 通知房间
                NettyWebSocketServer.sendToRoom(room.getRoomCode(), "tomorrow_converted", null);
            } catch (Exception e) {
                log.error("凌晨转换房间 {} 的明日计划失败", room.getId(), e);
            }
        }
    }

    /**
     * 踢出未填写明日计划的用户
     * @param room 房间
     * @param planDate 要检查的计划日期（凌晨调用时传 today，测试时传 tomorrow）
     */
    private void kickMembersWithoutTomorrowPlan(Room room, LocalDate planDate) {
        List<RoomMember> members = roomMemberMapper.selectList(
                new LambdaQueryWrapper<RoomMember>().eq(RoomMember::getRoomId, room.getId())
        );

        // 收集需要踢出的成员
        List<RoomMember> membersToKick = new ArrayList<>();
        for (RoomMember member : members) {
            // 检查是否有指定日期的计划
            Long planCount = tomorrowPlanMapper.selectCount(
                    new LambdaQueryWrapper<TomorrowPlan>()
                            .eq(TomorrowPlan::getMemberId, member.getId())
                            .eq(TomorrowPlan::getTaskDate, planDate)
            );
            if (planCount == 0) {
                membersToKick.add(member);
            }
        }

        // 踢出成员
        for (RoomMember member : membersToKick) {
            // 如果是管理员，需要转移管理员身份
            if (member.getIsAdmin() == 1) {
                transferAdmin(room, member.getId());
            }

            // 删除成员相关数据
            cleanupMemberData(member.getId());

            // 删除成员记录
            roomMemberMapper.deleteById(member.getId());

            // 通知被踢出的用户
            try {
                Map<String, Object> kickData = new HashMap<>();
                kickData.put("memberId", member.getId());
                kickData.put("reason", "未填写明日计划");
                NettyWebSocketServer.sendToUser(member.getUid(), "member_kicked", kickData);
            } catch (Exception e) {
                log.warn("通知被踢出用户 {} 失败", member.getUid(), e);
            }

            log.info("用户 {} 因未填写明日计划被踢出房间 {}", member.getDisplayName(), room.getRoomCode());
        }

        // 如果房间没有成员了，注销房间
        Long remainingCount = roomMemberMapper.selectCount(
                new LambdaQueryWrapper<RoomMember>().eq(RoomMember::getRoomId, room.getId())
        );
        if (remainingCount == 0) {
            room.setStatus(0);
            roomMapper.updateById(room);
            log.info("房间 {} 已无成员，自动注销", room.getRoomCode());
        }
    }

    /**
     * 转移管理员身份给下一个成员
     */
    private void transferAdmin(Room room, Long currentAdminId) {
        List<RoomMember> members = roomMemberMapper.selectList(
                new LambdaQueryWrapper<RoomMember>()
                        .eq(RoomMember::getRoomId, room.getId())
                        .ne(RoomMember::getId, currentAdminId)
                        .orderByAsc(RoomMember::getJoinedAt)
        );
        if (!members.isEmpty()) {
            RoomMember newAdmin = members.get(0);
            newAdmin.setIsAdmin(1);
            roomMemberMapper.updateById(newAdmin);
            room.setCreatorId(newAdmin.getId());  // 使用 Long 类型的 id
            roomMapper.updateById(room);
            log.info("管理员身份已转移给用户 {}", newAdmin.getDisplayName());
        }
    }

    /**
     * 清理成员相关数据
     */
    private void cleanupMemberData(Long memberId) {
        LocalDate today = LocalDate.now();

        // 先删除证据记录（需要先获取任务ID）
        List<DailyTask> tasks = taskMapper.selectByMemberAndDate(memberId, today);
        for (DailyTask task : tasks) {
            evidenceMapper.delete(
                    new LambdaQueryWrapper<TaskEvidence>()
                            .eq(TaskEvidence::getTaskId, task.getId())
            );
        }

        // 删除今日任务
        taskMapper.delete(
                new LambdaQueryWrapper<DailyTask>()
                        .eq(DailyTask::getMemberId, memberId)
                        .eq(DailyTask::getTaskDate, today)
        );

        // 删除明日计划
        tomorrowPlanMapper.delete(
                new LambdaQueryWrapper<TomorrowPlan>()
                        .eq(TomorrowPlan::getMemberId, memberId)
                        .eq(TomorrowPlan::getTaskDate, today.plusDays(1))
        );

        // 删除请假记录
        dailyLeaveMapper.delete(
                new LambdaQueryWrapper<DailyLeave>()
                        .eq(DailyLeave::getMemberId, memberId)
                        .eq(DailyLeave::getLeaveDate, today)
        );
    }

    private String getRoomCodeByMemberId(Long memberId) {
        RoomMember member = roomMemberMapper.selectById(memberId);
        if (member == null) return null;
        Room room = roomMapper.selectById(member.getRoomId());
        return room != null ? room.getRoomCode() : null;
    }

    private Long getRoomIdByMemberId(Long memberId) {
        RoomMember member = roomMemberMapper.selectById(memberId);
        return member != null ? member.getRoomId() : null;
    }
}
