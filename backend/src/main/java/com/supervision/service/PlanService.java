package com.supervision.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.supervision.common.BusinessException;
import com.supervision.dto.PlanCreateDTO;
import com.supervision.entity.LearningPlan;
import com.supervision.mapper.LearningPlanMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PlanService {

    @Autowired
    private LearningPlanMapper planMapper;

    public LearningPlan createPlan(Long memberId, PlanCreateDTO dto) {
        LearningPlan plan = new LearningPlan();
        plan.setMemberId(memberId);
        plan.setPlanType(dto.getPlanType());
        plan.setTitle(dto.getTitle());
        plan.setTargetDate(dto.getTargetDate());
        plan.setStatus(0);
        planMapper.insert(plan);
        return plan;
    }

    public List<LearningPlan> getPlanList(Long memberId, String type) {
        return queryPlans(memberId, type);
    }

    public List<LearningPlan> getMemberPlanList(Long memberId, String type) {
        return queryPlans(memberId, type);
    }

    private List<LearningPlan> queryPlans(Long memberId, String type) {
        LambdaQueryWrapper<LearningPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningPlan::getMemberId, memberId);
        if (type != null && !type.isEmpty()) {
            wrapper.eq(LearningPlan::getPlanType, type);
        }
        wrapper.orderByDesc(LearningPlan::getCreatedAt);
        return planMapper.selectList(wrapper);
    }

    public LearningPlan updatePlan(Long memberId, Long planId, Integer status) {
        LearningPlan plan = planMapper.selectById(planId);
        if (plan == null || !plan.getMemberId().equals(memberId)) {
            throw new BusinessException("计划不存在");
        }
        plan.setStatus(status);
        planMapper.updateById(plan);
        return plan;
    }

    public void deletePlan(Long memberId, Long planId) {
        LearningPlan plan = planMapper.selectById(planId);
        if (plan == null || !plan.getMemberId().equals(memberId)) {
            throw new BusinessException("计划不存在");
        }
        planMapper.deleteById(planId);
    }
}
