package com.supervision.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class NettyWebSocketServer implements CommandLineRunner, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(NettyWebSocketServer.class);

    @Value("${netty.port:8081}")
    private int port;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private NettyChannelHandler channelHandler;

    @Override
    public void run(String... args) throws Exception {
        new Thread(this::start).start();
    }

    private void start() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        channelHandler = new NettyChannelHandler();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new HttpServerCodec());
                            pipeline.addLast(new HttpObjectAggregator(65536));
                            pipeline.addLast(channelHandler);
                        }
                    });

            ChannelFuture future = bootstrap.bind(port).sync();
            serverChannel = future.channel();
            log.info("Netty WebSocket 服务器启动在端口 {}", port);
        } catch (Exception e) {
            log.error("Netty WebSocket 服务器启动失败", e);
        }
    }

    @Override
    public void destroy() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        log.info("Netty WebSocket 服务器已关闭");
    }

    /**
     * 供其他服务调用的静态广播方法
     */
    public static void sendToRoom(String roomCode, String type, Object data) {
        if (NettyChannelHandler.instance != null) {
            NettyChannelHandler.instance.sendToRoomStatic(roomCode, type, data);
        }
    }

    /**
     * 关闭房间内所有连接
     */
    public static void closeRoom(String roomCode) {
        if (NettyChannelHandler.instance != null) {
            NettyChannelHandler.instance.closeRoom(roomCode);
        }
    }

    /**
     * 发送消息给指定用户并关闭连接
     */
    public static void sendToUser(String uid, String type, Object data) {
        if (NettyChannelHandler.instance != null) {
            NettyChannelHandler.instance.sendToUserAndClose(uid, type, data);
        }
    }
}
