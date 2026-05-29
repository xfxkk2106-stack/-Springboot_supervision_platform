package com.supervision.controller;

import com.supervision.common.Result;
import com.supervision.dto.RoomCreateDTO;
import com.supervision.dto.RoomJoinDTO;
import com.supervision.entity.Room;
import com.supervision.entity.RoomMember;
import com.supervision.service.RoomService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/room")
public class RoomController {

    @Autowired
    private RoomService roomService;

    @PostMapping("/create")
    public Result<Map<String, Object>> createRoom(@Valid @RequestBody RoomCreateDTO dto, HttpServletResponse response) {
        return Result.success(roomService.createRoom(dto, response));
    }

    @PostMapping("/join")
    public Result<Map<String, Object>> joinRoom(@Valid @RequestBody RoomJoinDTO dto, HttpServletResponse response) {
        return Result.success(roomService.joinRoom(dto, response));
    }

    @GetMapping("/{roomCode}/info")
    public Result<Room> getRoomInfo(@PathVariable String roomCode) {
        return Result.success(roomService.getRoomInfo(roomCode));
    }

    @GetMapping("/{roomCode}/members")
    public Result<List<RoomMember>> getRoomMembers(@PathVariable String roomCode) {
        return Result.success(roomService.getRoomMembers(roomCode));
    }

    @PostMapping("/leave")
    public Result<?> leaveRoom(HttpServletRequest request) {
        Long memberId = (Long) request.getAttribute("memberId");
        Long roomId = (Long) request.getAttribute("roomId");
        roomService.leaveRoom(memberId, roomId);
        return Result.success("已退出房间");
    }

    @PostMapping("/dissolve")
    public Result<?> dissolveRoom(HttpServletRequest request) {
        Long memberId = (Long) request.getAttribute("memberId");
        Long roomId = (Long) request.getAttribute("roomId");
        roomService.dissolveRoom(memberId, roomId);
        return Result.success("房间已注销");
    }

    @GetMapping("/check-admin")
    public Result<Boolean> checkAdmin(HttpServletRequest request) {
        Long memberId = (Long) request.getAttribute("memberId");
        Long roomId = (Long) request.getAttribute("roomId");
        return Result.success(roomService.isAdmin(memberId, roomId));
    }
}
