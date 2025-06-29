package ru.example.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class InboundDataHandler extends ChannelInboundHandlerAdapter {

    private final RelayClient relayClient;
    private final SessionManager sessionManager;

    public InboundDataHandler(RelayClient relayClient, SessionManager sessionManager) {
        this.relayClient = relayClient;
        this.sessionManager = sessionManager;
        startPolling();
    }

    // Перехватываем байты от клиента (например, impacket)
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof ByteBuf buf) {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            buf.release();

            System.out.println("[SEND] " + bytes.length + " bytes");
            String json = CommandSerializer.toJsonSend(bytes);
            relayClient.enqueueTask(sessionManager.getSessionId(), json);
        }
    }

    // Параллельно запускаем polling на результат
    private void startPolling() {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(500);
                    String response = relayClient.fetchResult(sessionManager.getSessionId());
                    if (response != null && !response.isEmpty()) {
                        String encoded = JsonParser.extractPayload(response);
                        if (encoded != null) {
                            byte[] data = Base64.getDecoder().decode(encoded);
                            ChannelHandlerContext ctx = this.ctxRef;
                            if (ctx != null && ctx.channel().isActive()) {
                                ctx.writeAndFlush(ctx.alloc().buffer().writeBytes(data));
                                System.out.println("[RECEIVE] " + data.length + " bytes");
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    System.err.println("[RECEIVE] Error: " + e.getMessage());
                }
            }
        }).start();
    }

    private ChannelHandlerContext ctxRef;

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        this.ctxRef = ctx;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("Inbound error: " + cause.getMessage());
        ctx.close();
    }
}
