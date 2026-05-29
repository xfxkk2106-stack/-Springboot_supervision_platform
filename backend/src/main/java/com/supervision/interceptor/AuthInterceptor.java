package com.supervision.interceptor;

import com.supervision.entity.RoomMember;
import com.supervision.mapper.RoomMemberMapper;
import com.supervision.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private UserService userService;

    @Autowired
    private RoomMemberMapper roomMemberMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // OPTIONS 请求放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 从 Cookie 读取 authToken
        String authToken = userService.getTokenFromCookie(request);
        if (authToken == null || authToken.isEmpty()) {
            return unauthorized(response);
        }

        // 校验 token，获取 uid
        String uid = userService.getUidByToken(authToken);
        if (uid == null) {
            // token 失效，清理 Cookie，定时任务会清理 uid 数据
            userService.clearAuthCookie(response);
            return unauthorized(response);
        }

        // 查房间成员信息
        RoomMember member = roomMemberMapper.selectOne(
                new LambdaQueryWrapper<RoomMember>().eq(RoomMember::getUid, uid)
        );

        // 刷新 token
        String newToken = userService.refreshToken(authToken, response);
        if (newToken == null) {
            return unauthorized(response);
        }

        // 设置 request attributes
        request.setAttribute("uid", uid);
        if (member != null) {
            request.setAttribute("memberId", member.getId());
            request.setAttribute("roomId", member.getRoomId());
            request.setAttribute("displayName", member.getDisplayName());
        }

        return true;
    }

    private boolean unauthorized(HttpServletResponse response) throws Exception {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"身份验证失败\"}");
        return false;
    }
}
