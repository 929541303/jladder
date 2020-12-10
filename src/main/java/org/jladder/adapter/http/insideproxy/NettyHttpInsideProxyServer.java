package org.jladder.adapter.http.insideproxy;

import org.jladder.adapter.http.common.HttpConstant;
import org.jladder.adapter.http.wrapper.HttpRequestWrapperHandler;
import org.jladder.common.NamedThreadFactory;
import org.jladder.common.core.NettyProxyContext;
import org.jladder.common.core.config.JladderConfig;
import org.jladder.common.enumtype.RunModeEnum;
import org.jladder.common.util.NettyBootstrapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

/**
 * 翻墙双服务器，墙内服务器（HTTP协议）
 * @author hudaming
 */
public class NettyHttpInsideProxyServer implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(NettyHttpInsideProxyServer.class);
	private final String HttpInsideServerThreadNamePrefix = RunModeEnum.HttpInsideServer.getName();
	
	private final ServerBootstrap serverBootstrap;
	private final HttpInsideChannelInitializer httpChannelInitializer;
	private final JladderConfig config;

	public NettyHttpInsideProxyServer(JladderConfig config) {
		this.config = config;
		NettyProxyContext.regist(config);
		serverBootstrap = new ServerBootstrap();
		httpChannelInitializer = new HttpInsideChannelInitializer();
	}
	
	@Override
	public void run() {
		NioEventLoopGroup bossGroup = new NioEventLoopGroup(1, new NamedThreadFactory(HttpInsideServerThreadNamePrefix + "-boss-thread"));
		NioEventLoopGroup workerGroup = new NioEventLoopGroup(config.getWorkerCnt(), new NamedThreadFactory(HttpInsideServerThreadNamePrefix + "-worker-thread"));
		serverBootstrap.channel(NioServerSocketChannel.class);
		serverBootstrap.group(bossGroup, workerGroup);
		serverBootstrap.childHandler(httpChannelInitializer);
		
		// 配置TCP参数
		NettyBootstrapUtil.initTcpServerOptions(serverBootstrap, config);
		
		serverBootstrap.bind(config.getPort()).addListener(new GenericFutureListener<Future<? super Void>>() {
			@Override
			public void operationComplete(Future<? super Void> future) throws Exception {
				logger.info("http-inside-server started, listening port: " + config.getPort());
			}
		});
	}
	
	private static class HttpInsideChannelInitializer extends ChannelInitializer<Channel> {
		
		@Override
		protected void initChannel(Channel ch) throws Exception {
			ch.pipeline().addLast(new io.netty.handler.codec.http.HttpRequestDecoder());
			ch.pipeline().addLast(new HttpObjectAggregator(HttpConstant.HTTP_OBJECT_AGGREGATOR_LEN));
			ch.pipeline().addLast(new HttpRequestWrapperHandler());
			ch.pipeline().addLast(new HttpInsideLocalHandler());
		}
	}
}
