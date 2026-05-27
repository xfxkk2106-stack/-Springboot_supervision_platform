package com.supervision.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.supervision.common.BusinessException;
import com.supervision.dto.EvidenceReviewDTO;
import com.supervision.entity.DailyTask;
import com.supervision.entity.EvidenceReview;
import com.supervision.entity.Room;
import com.supervision.entity.RoomMember;
import com.supervision.entity.TaskEvidence;
import com.supervision.mapper.DailyTaskMapper;
import com.supervision.mapper.EvidenceReviewMapper;
import com.supervision.mapper.RoomMapper;
import com.supervision.mapper.RoomMemberMapper;
import com.supervision.mapper.TaskEvidenceMapper;
import com.supervision.netty.NettyWebSocketServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EvidenceService {

    @Autowired
    private TaskEvidenceMapper evidenceMapper;

    @Autowired
    private EvidenceReviewMapper reviewMapper;

    @Autowired
    private DailyTaskMapper taskMapper;

    @Autowired
    private RoomMemberMapper roomMemberMapper;

    @Autowired
    private RoomMapper roomMapper;

    @Autowired
    private MinioService minioService;

    public List<TaskEvidence> uploadEvidence(Long taskId, MultipartFile[] files) throws IOException {
        List<TaskEvidence> evidences = new ArrayList<>();

        for (MultipartFile file : files) {
            String imageUrl = minioService.uploadFile(file);

            TaskEvidence evidence = new TaskEvidence();
            evidence.setTaskId(taskId);
            evidence.setImageUrl(imageUrl);
            evidence.setStatus(0); // 待审核
            evidenceMapper.insert(evidence);
            evidences.add(evidence);
        }

        return evidences;
    }

    public void deleteEvidence(Long evidenceId, Long memberId) {
        TaskEvidence evidence = evidenceMapper.selectById(evidenceId);
        if (evidence == null) {
            throw new BusinessException("证据不存在");
        }
        // 验证是否是自己的证据
        DailyTask task = taskMapper.selectById(evidence.getTaskId());
        if (task == null || !task.getMemberId().equals(memberId)) {
            throw new BusinessException("只能删除自己的证据");
        }
        // 只有待审核状态才能删除
        if (evidence.getStatus() != 0) {
            throw new BusinessException("已审核的证据不能删除");
        }
        // 删除 MinIO 文件
        minioService.deleteFile(evidence.getImageUrl());
        // 删除数据库记录
        evidenceMapper.deleteById(evidenceId);
    }

    public List<TaskEvidence> getTaskEvidence(Long taskId) {
        return evidenceMapper.selectList(
                new LambdaQueryWrapper<TaskEvidence>()
                        .eq(TaskEvidence::getTaskId, taskId)
                        .orderByDesc(TaskEvidence::getUploadedAt)
        );
    }

    public EvidenceReview reviewEvidence(Long evidenceId, Long reviewerId, EvidenceReviewDTO dto) {
        TaskEvidence evidence = evidenceMapper.selectById(evidenceId);
        if (evidence == null) {
            throw new BusinessException("证据不存在");
        }

        // 检查是否是自己的证据
        DailyTask task = taskMapper.selectById(evidence.getTaskId());
        if (task != null && task.getMemberId().equals(reviewerId)) {
            throw new BusinessException("不能审核自己的学习证据");
        }

        // 更新证据状态
        evidence.setStatus(dto.getResult());
        evidenceMapper.updateById(evidence);

        // 创建审核记录
        EvidenceReview review = new EvidenceReview();
        review.setEvidenceId(evidenceId);
        review.setReviewerId(reviewerId);
        review.setResult(dto.getResult());
        review.setComment(dto.getComment());
        reviewMapper.insert(review);

        // 审核通过时，自动完成对应任务
        if (dto.getResult() == 1) {
            if (task != null && task.getIsCompleted() == 0) {
                task.setIsCompleted(1);
                task.setCompletedAt(LocalDateTime.now());
                taskMapper.updateById(task);
            }
        }

        // 通过 WebSocket 通知被审核人
        if (task != null) {
            try {
                RoomMember member = roomMemberMapper.selectById(task.getMemberId());
                if (member != null) {
                    Room room = roomMapper.selectById(member.getRoomId());
                    if (room != null) {
                        Map<String, Object> wsData = new HashMap<>();
                        wsData.put("evidenceId", evidenceId);
                        wsData.put("result", dto.getResult());
                        wsData.put("taskId", task.getId());
                        wsData.put("memberId", task.getMemberId());
                        NettyWebSocketServer.sendToRoom(room.getRoomCode(), "evidence_reviewed", wsData);
                    }
                }
            } catch (Exception e) {
                // 忽略 WebSocket 通知失败
            }
        }

        return review;
    }
}
