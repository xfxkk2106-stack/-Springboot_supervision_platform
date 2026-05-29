package com.supervision.controller;

import com.supervision.common.Result;
import com.supervision.entity.Room;
import com.supervision.entity.RoomMember;
import com.supervision.entity.User;
import com.supervision.mapper.RoomMapper;
import com.supervision.mapper.RoomMemberMapper;
import com.supervision.mapper.UserMapper;
import com.supervision.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoomMemberMapper roomMemberMapper;

    @Autowired
    private RoomMapper roomMapper;

    /**
     * 验证用户身份（通过 Cookie 中的 authToken）
     */
    @GetMapping("/verify")
    public Result<Map<String, Object>> verify(HttpServletRequest request, HttpServletResponse response) {
        String authToken = userService.getTokenFromCookie(request);
        if (authToken == null || authToken.isEmpty()) {
            return Result.error(401, "身份验证失败");
        }

        Map<String, Object> userInfo = userService.validateToken(authToken);
        if (userInfo == null) {
            // token 失效，清理 Cookie
            userService.clearAuthCookie(response);
            return Result.error(401, "身份已失效");
        }

        // 返回 authToken 给前端（WebSocket 需要）
        userInfo.put("authToken", authToken);

        return Result.success(userInfo);
    }

    /**
     * 退出房间
     */
    @PostMapping("/logout")
    public Result<?> logout(HttpServletRequest request, HttpServletResponse response) {
        String authToken = userService.getTokenFromCookie(request);
        if (authToken != null) {
            String uid = userService.getUidByToken(authToken);
            if (uid != null) {
                // 删除当前 token
                userService.deleteToken(uid, authToken);
            }
        }
        userService.clearAuthCookie(response);
        return Result.success("已退出");
    }

    /**
     * 生成授权码
     */
    @PostMapping("/code/generate")
    public Result<Map<String, String>> generateAuthCode(HttpServletRequest request) {
        String uid = (String) request.getAttribute("uid");
        if (uid == null) {
            return Result.error(401, "身份验证失败");
        }

        String code = userService.generateAuthCode(uid);

        Map<String, String> result = new HashMap<>();
        result.put("code", code);
        return Result.success(result);
    }

    /**
     * 使用授权码加入房间
     */
    @PostMapping("/code/use")
    public Result<Map<String, Object>> useAuthCode(@RequestBody Map<String, String> body, HttpServletResponse response) {
        String code = body.get("code");
        if (code == null || code.isEmpty()) {
            return Result.error(400, "请输入授权码");
        }

        String uid = userService.getUidByAuthCode(code);
        if (uid == null) {
            return Result.error(400, "授权码无效或已过期");
        }

        // 查用户
        User user = userMapper.selectById(uid);
        if (user == null || user.getDeleted() == 1) {
            return Result.error(400, "用户不存在");
        }

        // 查房间成员
        RoomMember member = roomMemberMapper.selectOne(
                new LambdaQueryWrapper<RoomMember>().eq(RoomMember::getUid, uid)
        );
        if (member == null) {
            return Result.error(400, "该用户未加入任何房间");
        }

        Room room = roomMapper.selectById(member.getRoomId());
        if (room == null || room.getStatus() != 1) {
            return Result.error(400, "房间已关闭");
        }

        // 生成新 authToken
        String authToken = userService.createTokenForUser(uid, response);

        Map<String, Object> result = new HashMap<>();
        result.put("uid", uid);
        result.put("authToken", authToken);
        result.put("memberId", member.getId());
        result.put("roomId", member.getRoomId());
        result.put("roomCode", room.getRoomCode());
        result.put("displayName", user.getDisplayName());
        result.put("isAdmin", member.getIsAdmin() == 1);
        return Result.success(result);
    }
}
