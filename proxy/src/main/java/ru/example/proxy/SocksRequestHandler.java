package ru.example.proxy;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.v5.*;

public class SocksRequestHandler extends SimpleChannelInboundHandler<Socks5CommandRequest> {

    private final RelayClient relayClient;
    private final SessionManager sessionManager;

    public SocksRequestHandler(RelayClient relayClient, SessionManager sessionManager) {
        this.relayClient = relayClient;
        this.sessionManager = sessionManager;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Socks5CommandRequest msg) {
        if (msg.type() == Socks5CommandType.CONNECT) {
            String host = msg.dstAddr();
            int port = msg.dstPort();
            System.out.printf("[CONNECT] %s:%d\n", host, port);

            // Отправляем CONNECT команду
            String json = CommandSerializer.toJsonConnect(host, port);
            relayClient.enqueueTask(sessionManager.getSessionId(), json);

            // Успешный ответ клиенту
            ctx.writeAndFlush(new DefaultSocks5CommandResponse(
                    Socks5CommandStatus.SUCCESS,
                    Socks5AddressType.IPv4
            ));

            // Меняем обработчик на наш InboundDataHandler
            ctx.pipeline().remove(this);
            ctx.pipeline().addLast(new InboundDataHandler(relayClient, sessionManager));
        } else {
            ctx.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("SOCKS error: " + cause.getMessage());
        ctx.close();
    }
}
