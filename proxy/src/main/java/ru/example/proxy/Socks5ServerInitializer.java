package ru.example.proxy;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5ServerEncoder;

public class Socks5ServerInitializer extends ChannelInitializer<SocketChannel> {

    private final RelayClient relayClient;
    private final SessionManager sessionManager;

    public Socks5ServerInitializer(RelayClient relayClient, SessionManager sessionManager) {
        this.relayClient = relayClient;
        this.sessionManager = sessionManager;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline()
                // Автоматически определяет SOCKS-протокол
                .addLast(new SocksPortUnificationServerHandler())

                // Декодеры SOCKS5
                .addLast(new Socks5InitialRequestDecoder())
                .addLast(Socks5ServerEncoder.DEFAULT)
                .addLast(new Socks5CommandRequestDecoder())

                // Обработчик команд (CONNECT и т.д.)
                .addLast(new Socks5ProxyHandler(relayClient, sessionManager));
    }
}
