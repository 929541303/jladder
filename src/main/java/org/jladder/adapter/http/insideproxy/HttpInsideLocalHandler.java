package org.jladder.adapter.http.insideproxy;

import java.util.concurrent.atomic.AtomicInteger;

import org.jladder.adapter.http.wrapper.HttpRequestWrapper;
import org.jladder.adapter.http.wrapper.HttpRequestWrapperHandler;
import org.jladder.common.Constant;
import org.jladder.core.executor.JladderForwardExecutor;
import org.jladder.core.listener.JladderForwardListener;
import org.jladder.core.message.JladderDataMessage;
import org.jladder.core.message.JladderMessageBuilder;

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

	private static final AtomicInteger IdCenter = new AtomicInteger(1);
	private static final ByteBuf HTTPS_CONNECTED_LINE = PooledByteBufAllocator.DEFAULT.directBuffer();
	private static final JladderForwardExecutor JladderForwardExecutor = new JladderForwardExecutor();
	private static final AtomicInteger FDCounter = new AtomicInteger(0);
	static {
		HTTPS_CONNECTED_LINE.writeBytes(Constant.ConnectedLine.getBytes());
	}
	
	private volatile String clientIden;
	
	@Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
		clientIden = "FD-" + FDCounter.incrementAndGet();
		log.debug(ctx.channel() + " connected");
    }
    
	@Override
	protected void channelRead0(ChannelHandlerContext browserCtx, HttpRequestWrapper requestWrapper) throws Exception {
		
		if (requestWrapper.host() == null || requestWrapper.host().isEmpty()) {
			browserCtx.close(); 
			log.warn("request is invaild, or version is http/1.0, request=" + requestWrapper.toRequest());
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
			JladderDataMessage message = JladderMessageBuilder.buildNeedEncryptMessage(IdCenter.getAndIncrement(), clientIden, requestWrapper.host(), requestWrapper.port(), requestWrapper.toByteBuf());
			log.debug("[msg" + message.getMsgId() + "][" + clientIden + "] flush to outside, host=" + requestWrapper.host() + ", msgLen=" + message.getBody().readableBytes());
			JladderForwardListener listener = JladderForwardExecutor.writeAndFlush(message);
			listener.onReceive(byteBuf -> {
				browserCtx.writeAndFlush(byteBuf.toByteBuf());
			}).onDisconnect(ctx -> {
				browserCtx.close();
				log.debug("channel " + clientIden + " disconnect");
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
	    		JladderDataMessage request = JladderMessageBuilder.buildUnNeedEncryptMessage(IdCenter.getAndIncrement(), clientIden, remoteHost, remotePort, (ByteBuf) msg);
	    		log.debug("[msg" + request.getMsgId() + "]" + clientIden + " browser read " + remoteHost + ":" + remotePort + " " + browserCtx.channel().toString() + ", writelen=" + ((ByteBuf) msg).readableBytes());
	    		JladderForwardListener listener = JladderForwardExecutor.writeAndFlush(request);
	    		listener.onReceive(byteBuf -> {
	    			log.debug("[" + clientIden + "]readlen=" + byteBuf.toByteBuf().readableBytes());
	    			browserCtx.writeAndFlush(byteBuf.toByteBuf());
	    		}).onDisconnect(ctx -> {
					browserCtx.close();
					log.debug("channel " + clientIden + " disconnect by remote_server");
				});
	    	}
	    }

	    @Override
	    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
	    	log.error(clientIden + " proxy error, host=" + remoteHost + ":" + remotePort, cause);
			JladderForwardExecutor.writeAndFlush(JladderMessageBuilder.buildDisconnectMessage(IdCenter.getAndIncrement(), clientIden));
	    }

	    @Override
	    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
	    	log.debug("channel " + clientIden + " disconnect by browser");
			JladderForwardExecutor.writeAndFlush(JladderMessageBuilder.buildDisconnectMessage(IdCenter.getAndIncrement(), clientIden));
			JladderForwardExecutor.clearClientIden(clientIden);
	    }
	}

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    	log.error(clientIden + " browser error", cause);
		JladderForwardExecutor.writeAndFlush(JladderMessageBuilder.buildDisconnectMessage(IdCenter.getAndIncrement(), clientIden));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		log.debug("channel " + clientIden + " disconnect");
		JladderForwardExecutor.writeAndFlush(JladderMessageBuilder.buildDisconnectMessage(IdCenter.getAndIncrement(), clientIden));
		JladderForwardExecutor.clearClientIden(clientIden);
    }
}
