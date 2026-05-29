package com.supervision.netty;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supervision.entity.RoomMember;
import com.supervision.mapper.RoomMemberMapper;
import com.supervision.service.UserService;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@ChannelHandler.Sharable
public class NettyChannelHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger log = LoggerFactory.getLogger(NettyChannelHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // roomCode -> Set of channels
    private final ConcurrentHashMap<String, CopyOnWriteArraySet<Channel>> roomChannels = new ConcurrentHashMap<>();
    // channel -> member info
    private final ConcurrentHashMap<Channel, Map<String, Object>> channelInfo = new ConcurrentHashMap<>();

    private WebSocketServerHandshaker handshaker;

    // 静态引用，供 NettyWebSocketServer 调用
    static NettyChannelHandler instance;

    public NettyChannelHandler() {
        instance = this;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof FullHttpRequest) {
            handleHttpRequest(ctx, (FullHttpRequest) msg);
        } else if (msg instanceof TextWebSocketFrame) {
            handleTextFrame(ctx, (TextWebSocketFrame) msg);
        } else if (msg instanceof CloseWebSocketFrame) {
            handleCloseFrame(ctx, (CloseWebSocketFrame) msg);
        } else if (msg instanceof PingWebSocketFrame) {
            ctx.channel().write(new PongWebSocketFrame(((PingWebSocketFrame) msg).content().retain()));
        }
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest request) {
        // 验证是 WebSocket 升级请求
        if (!"websocket".equalsIgnoreCase(request.headers().get(HttpHeaderNames.UPGRADE))) {
            sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
            return;
        }

        URI uri;
        try {
            uri = new URI(request.uri());
        } catch (URISyntaxException e) {
            sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
            return;
        }

        String path = uri.getPath();
        if (!path.startsWith("/ws/room/")) {
            sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND));
            return;
        }

        // 提取 roomCode
        String roomCode = path.substring(path.lastIndexOf('/') + 1);
        if (roomCode.isEmpty()) {
            sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
            return;
        }

        // 提取并验证 authToken
        String query = uri.getRawQuery();
        String authToken = null;
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("token=")) {
                    authToken = param.substring(6);
                    break;
                }
            }
        }

        if (authToken == null || authToken.isEmpty()) {
            sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED));
            return;
        }

        // 通过 Redis 校验 authToken 获取 uid
        UserService userService = SpringContextHolder.getBean(UserService.class);
        String uid = userService.getUidByToken(authToken);
        if (uid == null) {
            sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED));
            return;
        }

        // 查房间成员信息
        RoomMemberMapper mapper = SpringContextHolder.getBean(RoomMemberMapper.class);
        RoomMember member = mapper.selectOne(
                new LambdaQueryWrapper<RoomMember>().eq(RoomMember::getUid, uid)
        );
        if (member == null) {
            sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED));
            return;
        }

        Long memberId = member.getId();
        Long roomId = member.getRoomId();
        String displayName = member.getDisplayName();

        // 执行 WebSocket 握手
        WebSocketServerHandshakerFactory factory = new WebSocketServerHandshakerFactory(
                "ws://" + request.headers().get(HttpHeaderNames.HOST) + request.uri(), null, false);
        handshaker = factory.newHandshaker(request);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
            return;
        }
        handshaker.handshake(ctx.channel(), request);

        // 保存会话信息
        Channel channel = ctx.channel();
        Map<String, Object> info = new ConcurrentHashMap<>();
        info.put("memberId", memberId);
        info.put("roomId", roomId);
        info.put("displayName", displayName);
        info.put("roomCode", roomCode);
        channelInfo.put(channel, info);

        // 添加到房间
        roomChannels.computeIfAbsent(roomCode, k -> new CopyOnWriteArraySet<>()).add(channel);

        // 更新数据库在线状态
        try {
            member.setIsOnline(1);
            mapper.updateById(member);
        } catch (Exception e) {
            log.error("更新在线状态失败", e);
        }

        // 通知房间内其他成员
        Map<String, Object> onlineData = new HashMap<>();
        onlineData.put("memberId", memberId);
        onlineData.put("displayName", displayName);
        onlineData.put("roomId", roomId);
        onlineData.put("isAdmin", member.getIsAdmin());

        Map<String, Object> onlineMsg = new HashMap<>();
        onlineMsg.put("type", "member_online");
        onlineMsg.put("data", onlineData);
        broadcastToRoom(roomCode, channel, onlineMsg);

        // 给当前用户发送房间内已在线成员列表
        sendOnlineMembersToChannel(roomCode, channel, memberId);

        log.info("用户 {} 加入房间 {}", displayName, roomCode);
    }

    private void handleTextFrame(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        Channel channel = ctx.channel();
        Map<String, Object> info = channelInfo.get(channel);
        if (info == null) return;

        String roomCode = (String) info.get("roomCode");
        try {
            Map<String, Object> payload = objectMapper.readValue(
                    frame.text(),
                    new TypeReference<Map<String, Object>>() {}
            );
            broadcastToRoom(roomCode, channel, payload);
        } catch (Exception e) {
            log.error("解析消息失败", e);
        }
    }

    private void handleCloseFrame(ChannelHandlerContext ctx, CloseWebSocketFrame frame) {
        if (handshaker != null) {
            handshaker.close(ctx.channel(), frame);
        } else {
            ctx.channel().close();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        Map<String, Object> info = channelInfo.remove(channel);
        if (info == null) return;

        String roomCode = (String) info.get("roomCode");
        Long memberId = (Long) info.get("memberId");
        String displayName = (String) info.get("displayName");

        // 从房间移除
        CopyOnWriteArraySet<Channel> channels = roomChannels.get(roomCode);
        if (channels != null) {
            channels.remove(channel);
            if (channels.isEmpty()) {
                roomChannels.remove(roomCode);
            }
        }

        // 更新数据库离线状态（如果成员还存在）
        boolean memberExists = true;
        try {
            RoomMemberMapper mapper = SpringContextHolder.getBean(RoomMemberMapper.class);
            RoomMember member = mapper.selectById(memberId);
            if (member != null) {
                member.setIsOnline(0);
                mapper.updateById(member);
            } else {
                // 成员已被删除（主动退出房间），跳过广播
                memberExists = false;
            }
        } catch (Exception e) {
            log.error("更新离线状态失败", e);
        }

        // 检查是否还有其他连接（多标签页）
        boolean hasOtherSession = false;
        if (channels != null) {
            for (Channel c : channels) {
                Map<String, Object> cInfo = channelInfo.get(c);
                if (cInfo != null && memberId.equals(cInfo.get("memberId"))) {
                    hasOtherSession = true;
                    break;
                }
            }
        }

        // 通知房间内其他成员（仅当成员未被主动删除时）
        if (!hasOtherSession && memberExists) {
            Map<String, Object> data = new HashMap<>();
            data.put("memberId", memberId);
            data.put("displayName", displayName);

            Map<String, Object> msg = new HashMap<>();
            msg.put("type", "member_offline");
            msg.put("data", data);

            broadcastToRoom(roomCode, channel, msg);
        }

        log.info("用户 {} 离开房间 {}", displayName, roomCode);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("WebSocket 异常", cause);
        ctx.channel().close();
    }

    /**
     * 给新连接的用户发送房间内已在线成员列表
     */
    private void sendOnlineMembersToChannel(String roomCode, Channel newChannel, Long newMemberId) {
        CopyOnWriteArraySet<Channel> channels = roomChannels.get(roomCode);
        if (channels == null) return;

        List<Map<String, Object>> onlineMembers = new ArrayList<>();
        for (Channel c : channels) {
            if (!c.equals(newChannel) && c.isActive()) {
                Map<String, Object> cInfo = channelInfo.get(c);
                if (cInfo != null) {
                    Map<String, Object> memberData = new HashMap<>();
                    memberData.put("memberId", cInfo.get("memberId"));
                    memberData.put("displayName", cInfo.get("displayName"));
                    onlineMembers.add(memberData);
                }
            }
        }

        if (!onlineMembers.isEmpty()) {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "room_online_members");
            message.put("data", onlineMembers);
            sendToChannel(newChannel, message);
        }
    }

    /**
     * 广播消息到房间（排除指定 channel）
     */
    void broadcastToRoom(String roomCode, Channel excludeChannel, Map<String, Object> message) {
        CopyOnWriteArraySet<Channel> channels = roomChannels.get(roomCode);
        if (channels == null) return;

        String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            return;
        }

        for (Channel channel : channels) {
            if (!channel.equals(excludeChannel) && channel.isActive()) {
                channel.writeAndFlush(new TextWebSocketFrame(payload));
            }
        }
    }

    /**
     * 发送消息到单个 channel
     */
    private void sendToChannel(Channel channel, Map<String, Object> message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            channel.writeAndFlush(new TextWebSocketFrame(payload));
        } catch (Exception e) {
            // 忽略
        }
    }

    /**
     * 关闭房间内所有连接
     */
    void closeRoom(String roomCode) {
        CopyOnWriteArraySet<Channel> channels = roomChannels.remove(roomCode);
        if (channels == null) return;

        for (Channel channel : channels) {
            if (channel.isActive()) {
                channel.writeAndFlush(new CloseWebSocketFrame(1000, "房间已注销"));
                channel.close();
            }
        }
    }

    /**
     * 供外部调用的静态广播方法
     */
    void sendToRoomStatic(String roomCode, String type, Object data) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", type);
        message.put("data", data);
        broadcastToRoom(roomCode, null, message);
    }

    private void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest request, FullHttpResponse response) {
        if (HttpUtil.isKeepAlive(request)) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}
