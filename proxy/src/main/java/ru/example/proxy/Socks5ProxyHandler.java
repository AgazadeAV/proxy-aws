package ru.example.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.v5.*;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Socks5ProxyHandler extends SimpleChannelInboundHandler<DefaultSocks5CommandRequest> {

    private final RelayClient relayClient;
    private final SessionManager sessionManager;
    private SessionManager.Session session;
    private ChannelHandlerContext ctx;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public Socks5ProxyHandler(RelayClient relayClient, SessionManager sessionManager) {
        this.relayClient = relayClient;
        this.sessionManager = sessionManager;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DefaultSocks5CommandRequest request) {
        if (request.type() == Socks5CommandType.CONNECT) {
            System.out.printf("[Socks5Handler] Received CONNECT request: %s:%d%n", request.dstAddr(), request.dstPort());

            this.session = sessionManager.createSession(ctx);
            System.out.printf("[Socks5Handler] Session created: %s%n", session.sessionId());

            Socks5CommandResponse response = new DefaultSocks5CommandResponse(
                    Socks5CommandStatus.SUCCESS,
                    request.dstAddrType(),
                    request.dstAddr(),
                    request.dstPort()
            );
            ctx.writeAndFlush(response);
            System.out.println("[Socks5Handler] Sent SUCCESS response to client");

            scheduler.scheduleAtFixedRate(this::fetchFromAgent, 0, 200, TimeUnit.MILLISECONDS);
            System.out.println("[Socks5Handler] Started fetch loop");

            ctx.pipeline().remove(this);
            ctx.pipeline().addLast(new RelayTrafficHandler(relayClient, session.sessionId()));
            System.out.println("[Socks5Handler] Switched to RelayTrafficHandler");

        } else {
            System.out.printf("[Socks5Handler] Unsupported command: %s%n", request.type());
            ctx.writeAndFlush(new DefaultSocks5CommandResponse(
                    Socks5CommandStatus.COMMAND_UNSUPPORTED,
                    request.dstAddrType()
            ));
            ctx.close();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        System.out.printf("[Socks5Handler] Channel inactive, cleaning up session: %s%n", session != null ? session.sessionId() : "null");
        sessionManager.closeSession(ctx);
        scheduler.shutdownNow();
    }

    private void fetchFromAgent() {
        if (session == null || ctx == null || !ctx.channel().isActive()) return;

        try {
            byte[] payload = relayClient.fetchPayload(session.sessionId());
            if (payload != null && payload.length > 0) {
                System.out.printf("[Socks5Handler] Received %d bytes from agent for session %s%n", payload.length, session.sessionId());
                ByteBuf buf = Unpooled.wrappedBuffer(payload);
                ctx.writeAndFlush(buf);
            }
        } catch (Exception e) {
            System.err.printf("[Socks5Handler] Fetch failed for session %s: %s%n", session.sessionId(), e.getMessage());
        }
    }
}
