package ru.example.proxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class ProxyApp {

    private static final int PORT = 1080;

    public static void main(String[] args) throws Exception {
        RelayClient relayClient = new RelayClient("https://zvgi0d7fm8.execute-api.us-east-2.amazonaws.com/prod");
        SessionManager sessionManager = new SessionManager(relayClient);

        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new SocksServerInitializer(relayClient, sessionManager));

            System.out.println("[SOCKS5] Proxy started on port " + PORT);
            b.bind(PORT).sync().channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
