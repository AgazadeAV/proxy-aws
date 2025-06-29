package ru.example.proxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.UUID;

public class ProxyApp {

    private static final int PORT = 1080;
    private static final String BASE_URL = "https://zvgi0d7fm8.execute-api.us-east-2.amazonaws.com/prod";

    public static void main(String[] args) throws Exception {
        System.out.println("[ProxyApp] Starting SOCKS5 proxy on port " + PORT);

        RelayClient relayClient = new RelayClient(BASE_URL);
        SessionManager sessionManager = new SessionManager(relayClient);

        // CLI-поток
        Thread commandThread = new Thread(() -> handleCommands(sessionManager));
        commandThread.setDaemon(true);
        commandThread.start();

        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new Socks5ServerInitializer(relayClient, sessionManager));

            ChannelFuture future = bootstrap.bind(PORT).sync();
            System.out.printf("[ProxyApp] SOCKS5 proxy started and listening on 127.0.0.1:%d%n", PORT);
            future.channel().closeFuture().sync();
        } finally {
            System.out.println("[ProxyApp] Shutting down proxy...");
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            System.out.println("[ProxyApp] Shutdown complete.");
        }
    }

    private static void handleCommands(SessionManager sessionManager) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("[CLI] Введи команду: open | close <sessionId> | list | exit");

        while (true) {
            try {
                String line = reader.readLine();
                if (line == null || line.isBlank()) continue;

                String[] parts = line.trim().split("\\s+");
                String cmd = parts[0];

                switch (cmd) {
                    case "open" -> {
                        String id = UUID.randomUUID().toString();
                        sessionManager.createManualSession(id);
                        System.out.printf("[CLI] ✅ Открыта ручная сессия: %s%n", id);
                    }
                    case "close" -> {
                        if (parts.length < 2) {
                            System.out.println("[CLI] ⚠ Укажи sessionId");
                            break;
                        }
                        String id = parts[1];
                        sessionManager.closeManualSession(id);
                        System.out.printf("[CLI] 🗑 Закрыта сессия: %s%n", id);
                    }
                    case "list" -> {
                        var all = sessionManager.getManualSessions();
                        if (all.isEmpty()) System.out.println("[CLI] 🔍 Нет активных ручных сессий");
                        else {
                            System.out.println("[CLI] 📋 Активные сессии:");
                            all.forEach((id, token) -> System.out.println("  📌 " + id + " : " + token));
                        }
                    }
                    case "exit" -> {
                        System.out.println("[CLI] ⏹ Завершение работы...");
                        System.exit(0);
                    }
                    default -> System.out.printf("[CLI] ⚠ Неизвестная команда: %s%n", cmd);
                }
            } catch (Exception e) {
                System.err.printf("[CLI] Ошибка обработки команды: %s%n", e.getMessage());
            }
        }
    }
}
