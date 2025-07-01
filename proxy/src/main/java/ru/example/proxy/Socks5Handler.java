package ru.example.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.RequiredArgsConstructor;
import ru.example.proxy.dto.ConnectCommand;
import ru.example.proxy.dto.ReceiveCommand;
import ru.example.proxy.dto.SendCommand;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@RequiredArgsConstructor
public class Socks5Handler extends ChannelInboundHandlerAdapter {

    private final ProxyRelayClient relayClient;
    private final String sessionId;

    private ResultReader reader;

    private enum State {
        HANDSHAKE, REQUEST, STREAM
    }

    private State state = State.HANDSHAKE;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
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

                String host;
                if (atyp == 0x01) {
                    host = (buf.readByte() & 0xFF) + "." + (buf.readByte() & 0xFF) + "." +
                            (buf.readByte() & 0xFF) + "." + (buf.readByte() & 0xFF);
                } else if (atyp == 0x03) {
                    int len = buf.readByte();
                    byte[] domainBytes = new byte[len];
                    buf.readBytes(domainBytes);
                    host = new String(domainBytes, StandardCharsets.UTF_8);
                } else {
                    ctx.close();
                    return;
                }

                int port = buf.readUnsignedShort();

                String json = mapper.writeValueAsString(new ConnectCommand(host, port));
                relayClient.enqueueTask(sessionId, json);

                byte[] resp = {
                        0x05, 0x00, 0x00, 0x01,
                        0, 0, 0, 0,
                        0, 0
                };
                ctx.writeAndFlush(Unpooled.wrappedBuffer(resp));

                state = State.STREAM;

                // 🔄 Запуск потока получения ответов
                reader = new ResultReader(relayClient, sessionId, ctx);
                new Thread(reader).start();

            } else if (state == State.STREAM) {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                String payload = Base64.getEncoder().encodeToString(bytes);
                String json = mapper.writeValueAsString(new SendCommand(payload));
                relayClient.enqueueTask(sessionId, json);
                String receiveJson = mapper.writeValueAsString(new ReceiveCommand());
                relayClient.enqueueTask(sessionId, receiveJson);
            }

        } finally {
            buf.release();
        }
    }

    @Override
    @SuppressWarnings("CallToPrintStackTrace")
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (reader != null) {
            reader.stop();
        }
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (reader != null) {
            reader.stop();
        }
        super.channelInactive(ctx);
    }
}
