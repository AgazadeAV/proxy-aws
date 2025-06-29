package ru.example.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandRequest;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.handler.codec.socksx.v5.Socks5CommandType;

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
            // Создать сессию
            this.session = sessionManager.createSession(ctx);

            // Отправить успешный ответ клиенту
            Socks5CommandResponse response = new DefaultSocks5CommandResponse(
                    Socks5CommandStatus.SUCCESS,
                    request.dstAddrType(),
                    request.dstAddr(),
                    request.dstPort()
            );
            ctx.writeAndFlush(response);

            // Запустить фоновый fetch loop
            scheduler.scheduleAtFixedRate(this::fetchFromAgent, 0, 200, TimeUnit.MILLISECONDS);

            // Переключиться в raw mode (обработка байтов)
            ctx.pipeline().remove(this);
            ctx.pipeline().addLast(new RelayTrafficHandler(relayClient, session.sessionId()));

        } else {
            ctx.writeAndFlush(new DefaultSocks5CommandResponse(
                    Socks5CommandStatus.COMMAND_UNSUPPORTED,
                    request.dstAddrType()
            ));
            ctx.close();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        sessionManager.closeSession(ctx);
        scheduler.shutdownNow();
    }

    private void fetchFromAgent() {
        if (session == null || ctx == null || !ctx.channel().isActive()) return;

        try {
            byte[] payload = relayClient.fetchPayload(session.sessionId());
            if (payload != null && payload.length > 0) {
                ByteBuf buf = Unpooled.wrappedBuffer(payload);
                ctx.writeAndFlush(buf);
            }
        } catch (Exception e) {
            System.err.println("Fetch failed: " + e.getMessage());
        }
    }
}
