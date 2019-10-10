package org.hum.nettyproxy.adapter.socks5;

import org.hum.nettyproxy.adapter.socks5.handler.SocksProxyProcessHandler;
import org.hum.nettyproxy.common.NamedThreadFactory;
import org.hum.nettyproxy.common.core.NettyProxyContext;
import org.hum.nettyproxy.common.core.config.NettyProxyConfig;
import org.hum.nettyproxy.common.enumtype.RunModeEnum;
import org.hum.nettyproxy.common.util.NettyBootstrapUtil;
import org.hum.nettyproxy.compoment.monitor.NettyProxyMonitorHandler;
import org.hum.nettyproxy.compoment.monitor.NettyProxyMonitorManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.socks.SocksInitRequestDecoder;
import io.netty.handler.codec.socks.SocksMessageEncoder;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

public class NettySocksInsideProxyServer implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(NettySocksInsideProxyServer.class);
	private final String SocksServerThreadNamePrefix = RunModeEnum.SocksInsideServer.getName();
	private final ServerBootstrap serverBootstrap;
	private final ChannelInitializer<Channel> channelInitializer;
	private final NettyProxyMonitorManager nettyProxyMonitorManager;
	private final NettyProxyConfig config;


	public NettySocksInsideProxyServer(NettyProxyConfig config) {
		this.config = config;
		nettyProxyMonitorManager = new NettyProxyMonitorManager();
		NettyProxyContext.regist(config, nettyProxyMonitorManager);
		serverBootstrap = new ServerBootstrap();
		channelInitializer = new SocksInsideChannelInitializer();
	}

	@Override
	public void run() {
		NioEventLoopGroup bossGroup = new NioEventLoopGroup(1, new NamedThreadFactory(SocksServerThreadNamePrefix + "-boss-thread"));
		NioEventLoopGroup workerGroup = new NioEventLoopGroup(config.getWorkerCnt(), new NamedThreadFactory(SocksServerThreadNamePrefix + "-worker-thread"));
		serverBootstrap.channel(NioServerSocketChannel.class);
		serverBootstrap.group(bossGroup, workerGroup);
		serverBootstrap.childHandler(channelInitializer);
		
		// 配置TCP参数
		NettyBootstrapUtil.initTcpServerOptions(serverBootstrap, config);
		
		serverBootstrap.bind(config.getPort()).addListener(new GenericFutureListener<Future<? super Void>>() {
			@Override
			public void operationComplete(Future<? super Void> future) throws Exception {
				logger.info("socks-inside-server started, listening port: " + config.getPort());
			}
		});
	}
	
	private static class SocksInsideChannelInitializer extends ChannelInitializer<Channel> {
		private final NettyProxyMonitorHandler nettyProxyMonitorHandler = new NettyProxyMonitorHandler();
		@Override
		protected void initChannel(Channel ch) throws Exception {
			ch.pipeline().addFirst(nettyProxyMonitorHandler);
			ch.pipeline().addLast(new SocksInitRequestDecoder());
			ch.pipeline().addLast(new SocksMessageEncoder());
			ch.pipeline().addLast(new SocksProxyProcessHandler());
		}
	}
}
