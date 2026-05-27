package com.supervision.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.supervision.common.BusinessException;
import com.supervision.dto.TaskCreateDTO;
import com.supervision.entity.DailyTask;
import com.supervision.entity.Room;
import com.supervision.entity.RoomMember;
import com.supervision.entity.TaskEvidence;
import com.supervision.mapper.DailyTaskMapper;
import com.supervision.mapper.RoomMapper;
import com.supervision.mapper.RoomMemberMapper;
import com.supervision.mapper.TaskEvidenceMapper;
import com.supervision.netty.NettyWebSocketServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TaskService {

    @Autowired
    private DailyTaskMapper taskMapper;

    @Autowired
    private TaskEvidenceMapper evidenceMapper;

    @Autowired
    private RoomMapper roomMapper;

    @Autowired
    private RoomMemberMapper roomMemberMapper;

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

        // 通知房间内其他成员
        try {
            String roomCode = getRoomCodeByMemberId(memberId);
            if (roomCode != null) {
                NettyWebSocketServer.sendToRoom(roomCode, "task_completed", task.getId());
            }
        } catch (Exception e) {
            // 忽略
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

    private String getRoomCodeByMemberId(Long memberId) {
        RoomMember member = roomMemberMapper.selectById(memberId);
        if (member == null) return null;
        Room room = roomMapper.selectById(member.getRoomId());
        return room != null ? room.getRoomCode() : null;
    }
}
