package ru.example.proxy;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5ServerEncoder;

public class SocksServerInitializer extends ChannelInitializer<SocketChannel> {

    private final RelayClient relayClient;
    private final SessionManager sessionManager;

    public SocksServerInitializer(RelayClient relayClient, SessionManager sessionManager) {
        this.relayClient = relayClient;
        this.sessionManager = sessionManager;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline().addLast(
                new SocksPortUnificationServerHandler(),
                new Socks5InitialRequestDecoder(),
                Socks5ServerEncoder.DEFAULT,
                new Socks5CommandRequestDecoder(),
                new SocksRequestHandler(relayClient, sessionManager)
        );
    }
}
