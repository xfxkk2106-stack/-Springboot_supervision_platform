package com.supervision.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.supervision.entity.Room;
import com.supervision.entity.RoomMember;
import com.supervision.entity.User;
import com.supervision.mapper.RoomMapper;
import com.supervision.mapper.RoomMemberMapper;
import com.supervision.mapper.UserMapper;
import com.supervision.netty.NettyWebSocketServer;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final String AUTH_TOKEN_PREFIX = "auth:";
    private static final String USER_TOKENS_PREFIX = "user_tokens:";
    private static final String AUTH_CODE_PREFIX = "auth_code:";
    private static final int TOKEN_EXPIRE_DAYS = 2;
    private static final int OLD_TOKEN_EXPIRE_SECONDS = 3;
    private static final String COOKIE_NAME = "authToken";
    private static final int COOKIE_MAX_AGE = TOKEN_EXPIRE_DAYS * 24 * 60 * 60;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoomMemberMapper roomMemberMapper;

    @Autowired
    private RoomMapper roomMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private TaskService taskService;

    /**
     * 创建用户并生成 authToken，存入 DB + Redis + Cookie
     */
    public Map<String, Object> createUser(String displayName, HttpServletResponse response) {
        String uid = UUID.randomUUID().toString();
        String authToken = UUID.randomUUID().toString();

        // 存入数据库
        User user = new User();
        user.setUid(uid);
        user.setDisplayName(displayName);
        user.setDeleted(0);
        userMapper.insert(user);

        // 存入 Redis
        storeTokenInRedis(uid, authToken);

        // 设置 Cookie
        setAuthCookie(response, authToken);

        Map<String, Object> result = new HashMap<>();
        result.put("uid", uid);
        result.put("authToken", authToken);
        return result;
    }

    /**
     * 根据 authToken 从 Redis 获取 uid
     */
    public String getUidByToken(String authToken) {
        if (authToken == null || authToken.isEmpty()) {
            return null;
        }
        Object uid = redisTemplate.opsForValue().get(AUTH_TOKEN_PREFIX + authToken);
        return uid != null ? uid.toString() : null;
    }

    /**
     * 校验 authToken 并返回用户信息
     */
    public Map<String, Object> validateToken(String authToken) {
        String uid = getUidByToken(authToken);
        if (uid == null) {
            return null;
        }

        // 查用户是否存在且未删除
        User user = userMapper.selectById(uid);
        if (user == null || user.getDeleted() == 1) {
            return null;
        }

        // 查房间成员信息
        RoomMember member = roomMemberMapper.selectOne(
                new LambdaQueryWrapper<RoomMember>().eq(RoomMember::getUid, uid)
        );

        Map<String, Object> result = new HashMap<>();
        result.put("uid", uid);
        result.put("displayName", user.getDisplayName());
        if (member != null) {
            Room room = roomMapper.selectById(member.getRoomId());
            result.put("memberId", member.getId());
            result.put("roomId", member.getRoomId());
            result.put("roomCode", room != null ? room.getRoomCode() : null);
            result.put("isAdmin", member.getIsAdmin() == 1);
            result.put("inRoom", true);
        } else {
            result.put("inRoom", false);
        }
        return result;
    }

    /**
     * 刷新 authToken：生成新 token，旧 token 设 3 秒过期，遍历刷新所有 token TTL
     */
    public String refreshToken(String oldAuthToken, HttpServletResponse response) {
        String uid = getUidByToken(oldAuthToken);
        if (uid == null) {
            return null;
        }

        String newAuthToken = UUID.randomUUID().toString();

        // 存入新 token
        storeTokenInRedis(uid, newAuthToken);

        // 旧 token 设 3 秒过期
        redisTemplate.expire(AUTH_TOKEN_PREFIX + oldAuthToken, OLD_TOKEN_EXPIRE_SECONDS, TimeUnit.SECONDS);
        // 从 Set 中移除旧 token
        redisTemplate.opsForSet().remove(USER_TOKENS_PREFIX + uid, oldAuthToken);

        // 遍历刷新所有 token TTL
        refreshAllTokenTTL(uid);

        // 设置新 Cookie
        setAuthCookie(response, newAuthToken);

        return newAuthToken;
    }

    /**
     * 存入 Redis：auth:{token} -> uid (TTL 2天)，user_tokens:{uid} SADD token
     */
    private void storeTokenInRedis(String uid, String authToken) {
        redisTemplate.opsForValue().set(AUTH_TOKEN_PREFIX + authToken, uid, TOKEN_EXPIRE_DAYS, TimeUnit.DAYS);
        redisTemplate.opsForSet().add(USER_TOKENS_PREFIX + uid, authToken);
    }

    /**
     * 遍历 uid 下所有 token，刷新每个 token 的 TTL 为 2 天
     */
    public void refreshAllTokenTTL(String uid) {
        Set<Object> tokens = redisTemplate.opsForSet().members(USER_TOKENS_PREFIX + uid);
        if (tokens == null) return;
        for (Object token : tokens) {
            redisTemplate.expire(AUTH_TOKEN_PREFIX + token.toString(), TOKEN_EXPIRE_DAYS, TimeUnit.DAYS);
        }
    }

    /**
     * 清理用户：逻辑删除 user + 删除 room_member + 退出房间广播
     */
    public void cleanupUser(String uid) {
        if (uid == null) return;

        try {
            // 查房间成员
            RoomMember member = roomMemberMapper.selectOne(
                    new LambdaQueryWrapper<RoomMember>().eq(RoomMember::getUid, uid)
            );

            if (member != null) {
                Long roomId = member.getRoomId();
                Long memberId = member.getId();
                Room room = roomMapper.selectById(roomId);

                // 删除房间成员记录
                roomMemberMapper.deleteById(memberId);

                // 通知房间内其他成员
                if (room != null) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("memberId", memberId);
                    data.put("displayName", member.getDisplayName());
                    try {
                        data.put("roomStatus", taskService.calculateRoomStatus(roomId));
                    } catch (Exception e) {
                        // 忽略
                    }
                    NettyWebSocketServer.sendToRoom(room.getRoomCode(), "member_left", data);
                }
            }

            // 逻辑删除用户
            User user = userMapper.selectById(uid);
            if (user != null) {
                user.setDeleted(1);
                userMapper.updateById(user);
            }

            // 清理 Redis 中该 uid 的所有 token
            cleanupUserTokens(uid);

            log.info("已清理用户: {}", uid);
        } catch (Exception e) {
            log.error("清理用户失败: {}", uid, e);
        }
    }

    /**
     * 清理 Redis 中 uid 的所有 token
     */
    public void cleanupUserTokens(String uid) {
        Set<Object> tokens = redisTemplate.opsForSet().members(USER_TOKENS_PREFIX + uid);
        if (tokens != null) {
            for (Object token : tokens) {
                redisTemplate.delete(AUTH_TOKEN_PREFIX + token.toString());
            }
        }
        redisTemplate.delete(USER_TOKENS_PREFIX + uid);
    }

    /**
     * 从 Cookie 中读取 authToken
     */
    public String getTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * 设置 HttpOnly Cookie
     */
    public void setAuthCookie(HttpServletResponse response, String authToken) {
        Cookie cookie = new Cookie(COOKIE_NAME, authToken);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(COOKIE_MAX_AGE);
        cookie.setSecure(false); // 生产环境 HTTPS 时设为 true
        response.addCookie(cookie);
    }

    /**
     * 清除 Cookie
     */
    public void clearAuthCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    /**
     * 生成授权码，关联 uid
     */
    public String generateAuthCode(String uid) {
        String code = UUID.randomUUID().toString() + UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(AUTH_CODE_PREFIX + code, uid);
        return code;
    }

    /**
     * 通过授权码获取 uid
     */
    public String getUidByAuthCode(String code) {
        if (code == null || code.isEmpty()) return null;
        Object uid = redisTemplate.opsForValue().get(AUTH_CODE_PREFIX + code);
        return uid != null ? uid.toString() : null;
    }

    /**
     * 删除指定 uid 的某个 token
     */
    public void deleteToken(String uid, String authToken) {
        redisTemplate.delete(AUTH_TOKEN_PREFIX + authToken);
        redisTemplate.opsForSet().remove(USER_TOKENS_PREFIX + uid, authToken);
    }

    /**
     * 为已有用户创建新 token（用于授权码登录）
     */
    public String createTokenForUser(String uid, HttpServletResponse response) {
        String authToken = UUID.randomUUID().toString();
        storeTokenInRedis(uid, authToken);
        refreshAllTokenTTL(uid);
        setAuthCookie(response, authToken);
        return authToken;
    }
}
