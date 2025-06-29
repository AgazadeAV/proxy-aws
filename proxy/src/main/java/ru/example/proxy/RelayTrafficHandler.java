package ru.example.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class RelayTrafficHandler extends ChannelInboundHandlerAdapter {

    private final RelayClient relayClient;
    private final String sessionId;

    public RelayTrafficHandler(RelayClient relayClient, String sessionId) {
        this.relayClient = relayClient;
        this.sessionId = sessionId;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buf = (ByteBuf) msg;
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);

        System.out.printf("[RelayTrafficHandler] Received %d bytes from client for session %s%n", data.length, sessionId);

        try {
            relayClient.sendPayload(sessionId, data);
            System.out.printf("[RelayTrafficHandler] Sent %d bytes to AWS for session %s%n", data.length, sessionId);
        } catch (Exception e) {
            System.err.printf("[RelayTrafficHandler] Failed to send payload for session %s: %s%n", sessionId, e.getMessage());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.printf("[RelayTrafficHandler] Exception in session %s: %s%n", sessionId, cause.getMessage());
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        System.out.printf("[RelayTrafficHandler] Channel closed for session %s%n", sessionId);
    }
}
