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
        String remoteAddr = ch.remoteAddress().toString();
        System.out.printf("[Socks5Initializer] New connection from %s%n", remoteAddr);

        ch.pipeline()
                .addLast(new SocksPortUnificationServerHandler())
                .addLast(new Socks5InitialRequestDecoder())
                .addLast(Socks5ServerEncoder.DEFAULT)
                .addLast(new Socks5CommandRequestDecoder())
                .addLast(new Socks5ProxyHandler(relayClient, sessionManager));

        System.out.printf("[Socks5Initializer] Pipeline initialized for %s%n", remoteAddr);
    }
}
