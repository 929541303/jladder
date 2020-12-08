package org.jladder.adapter.http.insideproxy;

import java.util.Random;

import org.jladder.adapter.http.wrapper.HttpRequestWrapper;
import org.jladder.adapter.http.wrapper.HttpRequestWrapperHandler;
import org.jladder.adapter.protocol.executor.JladderForwardExecutor;
import org.jladder.adapter.protocol.listener.JladderForwardListener;
import org.jladder.adapter.protocol.message.JladderDataMessage;
import org.jladder.adapter.protocol.message.JladderMessageBuilder;
import org.jladder.common.Constant;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpObjectAggregator;
import lombok.extern.slf4j.Slf4j;

/**
 * HTTP/HTTPS 加密转发
 * <pre>
 *   针对HTTP请求，需要程序进行加密解密转发；而针对HTTPS请求，加解密由SSL协议完成，因此只需要透传转发。
 * </pre>
 * @author hudaming
 */
@Slf4j
public class HttpInsideLocalHandler extends SimpleChannelInboundHandler<HttpRequestWrapper> {

	private static final ByteBuf HTTPS_CONNECTED_LINE = PooledByteBufAllocator.DEFAULT.directBuffer();
	private final static JladderForwardExecutor JladderForwardExecutor = new JladderForwardExecutor();
	static {
		HTTPS_CONNECTED_LINE.writeBytes(Constant.ConnectedLine.getBytes());
	}
	
	private String clientIden;
	
	@Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
		clientIden = System.nanoTime() + "" + new Random().nextInt();
		log.info(clientIden + " browser connect");
    }
    
	@Override
	protected void channelRead0(ChannelHandlerContext browserCtx, HttpRequestWrapper requestWrapper) throws Exception {
		log.info(clientIden + " browser read " + requestWrapper.host() + " " + browserCtx.channel().toString());

		if (requestWrapper.host() == null || requestWrapper.host().isEmpty()) {
			/**
			 * 这里不要close，否则用Chrome访问news.baidu.com会导致EmptyResponse
			 * 在调试时发现，decode第一个请求正常，但第二个请求则不是一个正常的http请求，此时disscard比close更有利于后面处理
			 */
			// browserCtx.close(); 
			return;
		}
		
		// 转发前记录真实IP，防止转发中丢失源IP地址
		requestWrapper.header("x-forwarded-for", browserCtx.channel().remoteAddress().toString());
		
		if (requestWrapper.isHttps()) {
			browserCtx.pipeline().remove(this);
			browserCtx.pipeline().remove(io.netty.handler.codec.http.HttpRequestDecoder.class);
			browserCtx.pipeline().remove(HttpObjectAggregator.class);
			browserCtx.pipeline().remove(HttpRequestWrapperHandler.class);
			browserCtx.pipeline().addLast(new SimpleForwardChannelHandler(clientIden, requestWrapper.host(), requestWrapper.port()));
			browserCtx.writeAndFlush(HTTPS_CONNECTED_LINE.retain());
			return ;
		} else {
			JladderDataMessage message = JladderMessageBuilder.buildNeedEncryptMessage(clientIden, requestWrapper.host(), requestWrapper.port(), requestWrapper.toByteBuf());
			JladderForwardListener receiveListener = JladderForwardExecutor.writeAndFlush(message);
			receiveListener.onReceive(byteBuf -> {
				browserCtx.writeAndFlush(byteBuf.toByteBuf());
			}).onDisconnect(ctx -> {
				browserCtx.close();
				log.info("channel " + clientIden + " disconnect");
			});
		}
	}
	
	private static class SimpleForwardChannelHandler extends ChannelInboundHandlerAdapter {
		
		private String clientIden;
		private String remoteHost;
		private int remotePort;
		
		public SimpleForwardChannelHandler(String clientIden, String host, int port) {
			this.clientIden = clientIden;
			this.remoteHost = host;
			this.remotePort = port;
		}

	    @Override
	    public void channelRead(ChannelHandlerContext browserCtx, Object msg) throws Exception {
	    	if (msg instanceof ByteBuf) {
	    		JladderDataMessage request = JladderMessageBuilder.buildUnNeedEncryptMessage(clientIden, remoteHost, remotePort, (ByteBuf) msg);
	    		JladderForwardListener receiveListener = JladderForwardExecutor.writeAndFlush(request);
	    		receiveListener.onReceive(byteBuf -> {
	    			browserCtx.writeAndFlush(byteBuf.toByteBuf());
	    		}).onDisconnect(ctx -> {
					browserCtx.close();
					log.info("channel " + clientIden + " disconnect");
				});
	    	}
	    }

	    @Override
	    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
	    	log.error(clientIden + " proxy error", cause);
			JladderForwardExecutor.writeAndFlush(JladderMessageBuilder.buildDisconnectMessage(clientIden));
	    }

	    @Override
	    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
	    	log.info("channel " + clientIden + " disconnect");
			JladderForwardExecutor.writeAndFlush(JladderMessageBuilder.buildDisconnectMessage(clientIden));
	    }
	}

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    	log.error(clientIden + " browser error", cause);
		JladderForwardExecutor.writeAndFlush(JladderMessageBuilder.buildDisconnectMessage(clientIden));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		log.info("channel " + clientIden + " disconnect");
		JladderForwardExecutor.writeAndFlush(JladderMessageBuilder.buildDisconnectMessage(clientIden));
    }
}
