package com.supervision.service;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.supervision.common.BusinessException;
import com.supervision.dto.RoomCreateDTO;
import com.supervision.dto.RoomJoinDTO;
import com.supervision.entity.Room;
import com.supervision.entity.RoomMember;
import com.supervision.mapper.RoomMapper;
import com.supervision.mapper.RoomMemberMapper;
import com.supervision.netty.NettyWebSocketServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RoomService {

    @Autowired
    private RoomMapper roomMapper;

    @Autowired
    private RoomMemberMapper roomMemberMapper;

    @Autowired
    private com.supervision.config.JwtConfig jwtConfig;

    public Map<String, Object> createRoom(RoomCreateDTO dto) {
        // 生成唯一房间号
        String roomCode = generateUniqueRoomCode();

        // 创建房间
        Room room = new Room();
        room.setRoomCode(roomCode);
        room.setStatus(1);
        room.setDeleted(0);
        roomMapper.insert(room);

        // 创建管理员成员
        RoomMember admin = new RoomMember();
        admin.setRoomId(room.getId());
        admin.setDisplayName(dto.getDisplayName());
        admin.setIsAdmin(1);
        admin.setIsOnline(1);
        roomMemberMapper.insert(admin);

        // 更新房间的 creatorId
        room.setCreatorId(admin.getId());
        roomMapper.updateById(room);

        // 生成 JWT
        String token = jwtConfig.generateToken(admin.getId(), room.getId(), dto.getDisplayName());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("memberId", admin.getId());
        result.put("roomId", room.getId());
        result.put("roomCode", roomCode);
        result.put("displayName", dto.getDisplayName());
        result.put("isAdmin", true);
        return result;
    }

    public Map<String, Object> joinRoom(RoomJoinDTO dto) {
        String roomCode = dto.getRoomCode().toUpperCase();

        // 查找房间
        Room room = roomMapper.selectOne(
                new LambdaQueryWrapper<Room>().eq(Room::getRoomCode, roomCode)
        );
        if (room == null) {
            throw BusinessException.roomNotFound();
        }

        if (room.getStatus() != 1) {
            throw new BusinessException("房间已关闭");
        }

        // 添加成员
        RoomMember member = new RoomMember();
        member.setRoomId(room.getId());
        member.setDisplayName(dto.getDisplayName());
        member.setIsAdmin(0);
        member.setIsOnline(1);
        roomMemberMapper.insert(member);

        // 生成 JWT
        String token = jwtConfig.generateToken(member.getId(), room.getId(), dto.getDisplayName());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("memberId", member.getId());
        result.put("roomId", room.getId());
        result.put("roomCode", roomCode);
        result.put("displayName", dto.getDisplayName());
        result.put("isAdmin", false);
        return result;
    }

    public Room getRoomInfo(String roomCode) {
        Room room = roomMapper.selectOne(
                new LambdaQueryWrapper<Room>().eq(Room::getRoomCode, roomCode.toUpperCase())
        );
        if (room == null) {
            throw BusinessException.roomNotFound();
        }
        // 获取创建者名称
        if (room.getCreatorId() != null) {
            RoomMember creator = roomMemberMapper.selectById(room.getCreatorId());
            if (creator != null) {
                room.setCreatorName(creator.getDisplayName());
            }
        }
        return room;
    }

    public List<RoomMember> getRoomMembers(String roomCode) {
        return roomMemberMapper.selectByRoomCode(roomCode.toUpperCase());
    }

    /**
     * 注销房间（仅管理员可用）
     */
    public void dissolveRoom(Long memberId, Long roomId) {
        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            throw BusinessException.roomNotFound();
        }

        // 验证是否为管理员
        if (!room.getCreatorId().equals(memberId)) {
            throw new BusinessException("只有管理员可以注销房间");
        }

        // 关闭房间
        room.setStatus(0);
        roomMapper.updateById(room);

        // 通知所有成员房间已注销
        String roomCode = room.getRoomCode();
        try {
            NettyWebSocketServer.sendToRoom(roomCode, "room_dissolved", null);
        } catch (Exception e) {
            // 忽略广播失败
        }
        try {
            NettyWebSocketServer.closeRoom(roomCode);
        } catch (Exception e) {
            // 忽略关闭失败
        }
    }

    /**
     * 退出房间（非管理员）
     */
    public void leaveRoom(Long memberId, Long roomId) {
        RoomMember member = roomMemberMapper.selectById(memberId);
        if (member == null || !member.getRoomId().equals(roomId)) {
            throw new BusinessException("您不在此房间中");
        }
        if (member.getIsAdmin() == 1) {
            throw new BusinessException("管理员不能退出房间，请使用注销房间功能");
        }

        // 删除成员记录
        roomMemberMapper.deleteById(memberId);

        // 通知房间内其他成员（使用 member_left 区分主动退出和断线）
        String roomCode = roomMapper.selectById(roomId).getRoomCode();
        Map<String, Object> data = new HashMap<>();
        data.put("memberId", memberId);
        data.put("displayName", member.getDisplayName());
        NettyWebSocketServer.sendToRoom(roomCode, "member_left", data);
    }

    /**
     * 检查用户是否为房间管理员
     */
    public boolean isAdmin(Long memberId, Long roomId) {
        Room room = roomMapper.selectById(roomId);
        return room != null && room.getCreatorId().equals(memberId);
    }

    private String generateUniqueRoomCode() {
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String code;
        do {
            code = RandomUtil.randomString(chars, 7);
        } while (roomMapper.selectCount(
                new LambdaQueryWrapper<Room>().eq(Room::getRoomCode, code)
        ) > 0);
        return code;
    }
}
