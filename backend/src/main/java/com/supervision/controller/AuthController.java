package com.supervision.controller;

import com.supervision.common.Result;
import com.supervision.config.JwtConfig;
import com.supervision.dto.RoomJoinDTO;
import com.supervision.entity.RoomMember;
import com.supervision.mapper.RoomMemberMapper;
import com.supervision.service.RoomService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private RoomService roomService;

    @Autowired
    private RoomMemberMapper roomMemberMapper;

    @Autowired
    private JwtConfig jwtConfig;

    @PostMapping("/join-room")
    public Result<Map<String, Object>> joinRoom(@Valid @RequestBody RoomJoinDTO dto) {
        return Result.success(roomService.joinRoom(dto));
    }

    /**
     * 验证 token 是否有效，返回用户信息
     */
    @GetMapping("/verify")
    public Result<Map<String, Object>> verifyToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Result.error(401, "未登录或登录已过期");
        }

        String token = authHeader.substring(7);
        if (!jwtConfig.validateToken(token)) {
            return Result.error(401, "未登录或登录已过期");
        }

        Long memberId = jwtConfig.getMemberId(token);
        Long roomId = jwtConfig.getRoomId(token);
        String displayName = jwtConfig.getDisplayName(token);

        // 检查成员是否存在
        RoomMember member = roomMemberMapper.selectById(memberId);
        if (member == null) {
            return Result.error(401, "用户不存在");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("memberId", memberId);
        result.put("roomId", roomId);
        result.put("displayName", displayName);
        result.put("isAdmin", member.getIsAdmin() == 1);

        return Result.success(result);
    }
}
