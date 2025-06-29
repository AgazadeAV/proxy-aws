package ru.example.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Socks5HandlerTest extends ChannelInboundHandlerAdapter {

    private enum State {
        HANDSHAKE, REQUEST, STREAM
    }

    private State state = State.HANDSHAKE;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buf = (ByteBuf) msg;

        try {
            if (state == State.HANDSHAKE) {
                buf.readByte(); // VER
                int nMethods = buf.readByte();
                buf.skipBytes(nMethods); // METHODS
                ctx.writeAndFlush(Unpooled.wrappedBuffer(new byte[]{0x05, 0x00}));
                state = State.REQUEST;

            } else if (state == State.REQUEST) {
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

                System.out.println("[CONNECT] Host: " + host + ", Port: " + port);

                // Успешный ответ клиенту
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
                String encoded = Base64.getEncoder().encodeToString(bytes);
                System.out.println("[SEND] Base64: " + encoded);
            }

        } finally {
            buf.release();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
