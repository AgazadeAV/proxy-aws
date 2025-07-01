package ru.example.proxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Base64;
import java.util.UUID;

public class ProxyApp {

    private static final int PORT = 1080;

    private static Channel serverChannel;
    private static EventLoopGroup bossGroup;
    private static EventLoopGroup workerGroup;

    private static String sessionId;

    public static void main(String[] args) throws Exception {
        System.out.println("[ProxyApp] Starting SOCKS5 proxy on port " + PORT);

        ProxyRelayClient relayClient = new ProxyRelayClient(
                "https://sqs.us-east-2.amazonaws.com/302010997651/proxy-to-agent.fifo",
                "https://sqs.us-east-2.amazonaws.com/302010997651/agent-to-proxy.fifo"
        );

        // CLI thread
        Thread cliThread = new Thread(() -> handleCommands(relayClient));
        cliThread.setDaemon(true);
        cliThread.start();

        // Wait forever while CLI handles lifecycle
        synchronized (ProxyApp.class) {
            ProxyApp.class.wait();
        }
    }

    private static void handleCommands(ProxyRelayClient relayClient) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {
                System.out.print("> ");
                String line = reader.readLine();
                if (line == null) break;

                switch (line.trim().toLowerCase()) {
                    case "open":
                        if (serverChannel != null) {
                            System.out.println("[ProxyApp] Session already open");
                            break;
                        }
                        sessionId = UUID.randomUUID().toString();
                        String token = Base64.getEncoder().encodeToString(sessionId.getBytes());
                        relayClient.openSession(sessionId, token);
                        startSocksServer(relayClient, sessionId);
                        System.out.println("[ProxyApp] Session started:");
                        System.out.println("Session ID: " + sessionId);
                        System.out.println("Token: " + token);
                        break;

                    case "close":
                        if (serverChannel == null) {
                            System.out.println("[ProxyApp] No active session");
                            break;
                        }
                        relayClient.deleteSession(sessionId);
                        stopSocksServer();
                        System.out.println("[ProxyApp] Session closed");
                        break;

                    case "exit":
                        if (serverChannel != null) {
                            relayClient.deleteSession(sessionId);
                            stopSocksServer();
                        }
                        System.out.println("[ProxyApp] Bye!");
                        System.exit(0);
                        break;

                    default:
                        System.out.println("[ProxyApp] Unknown command");
                }
            }
        } catch (Exception e) {
            System.err.println("[ProxyApp] CLI error: " + e.getMessage());
        }
    }

    private static void startSocksServer(ProxyRelayClient relayClient, String sessionId) throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addLast(new Socks5Handler(relayClient, sessionId));
                    }
                });

        ChannelFuture future = bootstrap.bind(PORT).sync();
        serverChannel = future.channel();
        System.out.println("[ProxyApp] SOCKS5 proxy started on port " + PORT);
    }

    private static void stopSocksServer() throws InterruptedException {
        if (serverChannel != null) {
            serverChannel.close().sync();
            serverChannel = null;
        }
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
    }
}
