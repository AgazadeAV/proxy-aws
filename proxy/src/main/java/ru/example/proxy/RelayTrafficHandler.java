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

        try {
            relayClient.sendPayload(sessionId, data);
        } catch (Exception e) {
            System.err.println("Send failed: " + e.getMessage());
        }
    }
}
