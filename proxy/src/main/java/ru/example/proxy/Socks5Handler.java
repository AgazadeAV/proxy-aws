package ru.example.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.nio.charset.StandardCharsets;

public class Socks5Handler extends ChannelInboundHandlerAdapter {

    private final ProxyRelayClient relayClient;
    private final String sessionId;

    private enum State {
        HANDSHAKE, REQUEST, STREAM
    }

    private State state = State.HANDSHAKE;

    public Socks5Handler(ProxyRelayClient relayClient, String sessionId) {
        this.relayClient = relayClient;
        this.sessionId = sessionId;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buf = (ByteBuf) msg;

        if (state == State.HANDSHAKE) {
            // Read VER, NMETHODS, METHODS[n]
            buf.readByte(); // VER
            int nMethods = buf.readByte();
            buf.skipBytes(nMethods); // METHODS
            // Response: version 5, NO AUTH
            ctx.writeAndFlush(Unpooled.wrappedBuffer(new byte[]{0x05, 0x00}));
            state = State.REQUEST;

        } else if (state == State.REQUEST) {
            // VER CMD RSV ATYP DST.ADDR DST.PORT
            buf.readByte(); // VER
            int cmd = buf.readByte(); // CMD
            buf.readByte(); // RSV
            int atyp = buf.readByte(); // ATYP

            String host = null;
            if (atyp == 0x01) { // IPv4
                host = (buf.readByte() & 0xFF) + "." + (buf.readByte() & 0xFF) + "." +
                        (buf.readByte() & 0xFF) + "." + (buf.readByte() & 0xFF);
            } else if (atyp == 0x03) { // DOMAIN
                int len = buf.readByte();
                byte[] domainBytes = new byte[len];
                buf.readBytes(domainBytes);
                host = new String(domainBytes, StandardCharsets.UTF_8);
            } else {
                ctx.close();
                return;
            }

            int port = buf.readUnsignedShort();

            // Send CONNECT command to agent
            String json = CommandSerializer.toJsonConnect(host, port);
            relayClient.enqueueTask(sessionId, json);

            // Respond to client: success
            byte[] resp = {
                    0x05, 0x00, 0x00, 0x01,
                    0, 0, 0, 0, // bound addr
                    0, 0        // bound port
            };
            ctx.writeAndFlush(Unpooled.wrappedBuffer(resp));

            state = State.STREAM;

        } else if (state == State.STREAM) {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            String json = CommandSerializer.toJsonSend(bytes);
            relayClient.enqueueTask(sessionId, json);
        }

        buf.release();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
