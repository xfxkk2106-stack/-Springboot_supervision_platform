package com.supervision.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.supervision.common.BusinessException;
import com.supervision.entity.*;
import com.supervision.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class ReviewService {

    @Autowired
    private DailyReviewMapper reviewMapper;

    @Autowired
    private TomorrowPlanMapper tomorrowPlanMapper;

    @Autowired
    private DailyTaskMapper taskMapper;

    @Autowired
    private TaskEvidenceMapper evidenceMapper;

    @Autowired
    private EvidenceReviewMapper evidenceReviewMapper;

    @Autowired
    private TaskService taskService;

    public DailyReview createOrUpdateReview(Long memberId, String summary, Integer moodRating) {
        DailyReview review = reviewMapper.selectOne(
                new LambdaQueryWrapper<DailyReview>()
                        .eq(DailyReview::getMemberId, memberId)
                        .eq(DailyReview::getReviewDate, LocalDate.now())
        );

        if (review == null) {
            review = new DailyReview();
            review.setMemberId(memberId);
            review.setReviewDate(LocalDate.now());
            review.setSummary(summary);
            review.setMoodRating(moodRating);
            reviewMapper.insert(review);
        } else {
            review.setSummary(summary);
            review.setMoodRating(moodRating);
            reviewMapper.updateById(review);
        }

        return review;
    }

    public DailyReview getTodayReview(Long memberId) {
        return reviewMapper.selectOne(
                new LambdaQueryWrapper<DailyReview>()
                        .eq(DailyReview::getMemberId, memberId)
                        .eq(DailyReview::getReviewDate, LocalDate.now())
        );
    }

    public List<TomorrowPlan> createTomorrowPlan(Long memberId, Long roomId, List<Map<String, String>> plans) {
        // 检查是否可以创建明日计划
        if (!canCreateTomorrowPlan(memberId, roomId)) {
            throw BusinessException.cannotCreateTomorrow();
        }

        LocalDate tomorrow = LocalDate.now().plusDays(1);

        for (int i = 0; i < plans.size(); i++) {
            Map<String, String> plan = plans.get(i);
            TomorrowPlan tomorrowPlan = new TomorrowPlan();
            tomorrowPlan.setMemberId(memberId);
            tomorrowPlan.setTaskDate(tomorrow);
            tomorrowPlan.setSubject(plan.get("subject"));
            tomorrowPlan.setTaskContent(plan.get("taskContent"));
            tomorrowPlan.setSortOrder(i);
            tomorrowPlanMapper.insert(tomorrowPlan);
        }

        return getTomorrowPlan(memberId);
    }

    public List<TomorrowPlan> getTomorrowPlan(Long memberId) {
        return tomorrowPlanMapper.selectList(
                new LambdaQueryWrapper<TomorrowPlan>()
                        .eq(TomorrowPlan::getMemberId, memberId)
                        .eq(TomorrowPlan::getTaskDate, LocalDate.now().plusDays(1))
                        .orderByAsc(TomorrowPlan::getSortOrder)
        );
    }

    private boolean canCreateTomorrowPlan(Long memberId, Long roomId) {
        // 0. 如果当前用户请假，跳过个人任务检查
        if (!taskService.isOnLeave(memberId)) {
            // 1. 检查今日所有任务是否完成
            List<DailyTask> todayTasks = taskMapper.selectByMemberAndDate(memberId, LocalDate.now());
            if (todayTasks.isEmpty()) {
                return false;
            }
            boolean allCompleted = todayTasks.stream().allMatch(t -> t.getIsCompleted() == 1);
            if (!allCompleted) {
                return false;
            }
        }

        // 2. 检查是否审核完对方的证据（跳过请假成员的证据）
        List<DailyTask> roomTasks = taskMapper.selectByRoomAndDate(roomId, LocalDate.now());
        for (DailyTask task : roomTasks) {
            if (!task.getMemberId().equals(memberId)) {
                // 跳过请假成员的任务
                if (taskService.isOnLeave(task.getMemberId())) {
                    continue;
                }
                List<TaskEvidence> evidences = evidenceMapper.selectList(
                        new LambdaQueryWrapper<TaskEvidence>()
                                .eq(TaskEvidence::getTaskId, task.getId())
                );
                for (TaskEvidence evidence : evidences) {
                    if (evidence.getStatus() == 0) {
                        return false;
                    }
                }
            }
        }

        return true;
    }
}
