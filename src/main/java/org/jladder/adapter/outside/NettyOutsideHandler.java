package org.jladder.adapter.outside;

import org.jladder.adapter.protocol.JladderAsynHttpClient;
import org.jladder.adapter.protocol.JladderByteBuf;
import org.jladder.adapter.protocol.JladderMessage;
import org.jladder.adapter.protocol.JladderMessageReceiveEvent;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Sharable
public class NettyOutsideHandler extends SimpleChannelInboundHandler<JladderMessage> {

	private static final EventLoopGroup HttpClientEventLoopGroup = new NioEventLoopGroup(1);
	
	@Override
	protected void channelRead0(ChannelHandlerContext insideCtx, JladderMessage msg) throws Exception {
		// TODO 使用ctx.channel().eventLoop()
		log.info("outside recieve request");
		msg.getBody().retain();
		// XXX 这里为什么不能用insideCtx的eventLoop
		JladderAsynHttpClient client = new JladderAsynHttpClient(msg.getHost(), msg.getPort(), HttpClientEventLoopGroup);
		client.writeAndFlush(msg.getBody()).onReceive(new JladderMessageReceiveEvent() {
			@Override
			public void onReceive(JladderByteBuf byteBuf) {
				log.info("outside receive response and flushed");
				insideCtx.writeAndFlush(JladderMessage.buildNeedEncryptMessage(msg.getId(), msg.getHost(), msg.getPort(), byteBuf.toByteBuf().retain()));
			}
		});
	}
}
