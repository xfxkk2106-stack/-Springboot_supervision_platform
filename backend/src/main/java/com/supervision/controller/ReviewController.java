package com.supervision.controller;

import com.supervision.common.Result;
import com.supervision.entity.DailyReview;
import com.supervision.entity.TomorrowPlan;
import com.supervision.service.ReviewService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ReviewController {

    @Autowired
    private ReviewService reviewService;

    @PostMapping("/review/create")
    public Result<DailyReview> createReview(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long memberId = (Long) request.getAttribute("memberId");
        String summary = (String) body.get("summary");
        Integer moodRating = (Integer) body.get("moodRating");
        return Result.success(reviewService.createOrUpdateReview(memberId, summary, moodRating));
    }

    @GetMapping("/review/today")
    public Result<DailyReview> getTodayReview(HttpServletRequest request) {
        Long memberId = (Long) request.getAttribute("memberId");
        return Result.success(reviewService.getTodayReview(memberId));
    }

    @PostMapping("/tomorrow/create")
    public Result<List<TomorrowPlan>> createTomorrowPlan(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long memberId = (Long) request.getAttribute("memberId");
        Long roomId = (Long) request.getAttribute("roomId");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> plans = (List<Map<String, String>>) body.get("plans");
        return Result.success(reviewService.createTomorrowPlan(memberId, roomId, plans));
    }

    @GetMapping("/tomorrow/today")
    public Result<List<TomorrowPlan>> getTomorrowPlan(HttpServletRequest request) {
        Long memberId = (Long) request.getAttribute("memberId");
        return Result.success(reviewService.getTomorrowPlan(memberId));
    }
}
