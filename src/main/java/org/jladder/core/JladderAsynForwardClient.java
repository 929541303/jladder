package org.jladder.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import org.jladder.common.exception.JladderException;
import org.jladder.core.enumtype.JladderForwardWorkerStatusEnum;
import org.jladder.core.listener.JladderAsynForwardClientListener;
import org.jladder.core.listener.JladderForwardListener;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Sharable
public class JladderAsynForwardClient extends ChannelInboundHandlerAdapter {
	
	private EventLoopGroup eventLoopGroup;
	private Channel channel;
	private String remoteHost;
	private int remotePort;
	private String id;
	private final Bootstrap bootstrap = new Bootstrap();
	private volatile JladderForwardWorkerStatusEnum status = JladderForwardWorkerStatusEnum.Terminated;
	private JladderForwardListener onReceiveListener = new JladderForwardListener();
	private CountDownLatch connectFinishLatch = new CountDownLatch(1);
	private JladderAsynForwardClientInvokeChain jladderAsynForwardClientInvokeChain = new JladderAsynForwardClientInvokeChain();
	
	public JladderAsynForwardClient(String id, String remoteHost, int remotePort, EventLoopGroup eventLoopGroup) {
		this(id, remoteHost, remotePort, eventLoopGroup, null);
	}
	
	public JladderAsynForwardClient(String id, String remoteHost, int remotePort, EventLoopGroup eventLoopGroup, JladderAsynForwardClientListener listener) {
		this.id = id;
		this.remoteHost = remoteHost;
		this.remotePort = remotePort;
		this.eventLoopGroup = eventLoopGroup;
		this.initListener(listener);
		this.initBootStrap();
	}
	
	private void initBootStrap() {
		bootstrap.channel(NioSocketChannel.class);
		bootstrap.group(eventLoopGroup);
		// TODO 做成可配置的，避免长时阻塞，默认不配置的情况下，好像是30s
		bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);
		bootstrap.handler(new ChannelInitializer<Channel>() {
			@Override
			protected void initChannel(Channel ch) throws Exception {
				ch.pipeline().addLast(JladderAsynForwardClient.this);
			}
		});			
	}

	private void initListener(JladderAsynForwardClientListener listener) {
		jladderAsynForwardClientInvokeChain.addListener(listener);
	}

	public synchronized void connect() throws InterruptedException {
		if (status == JladderForwardWorkerStatusEnum.Running) {
			return;
		}
		// init bootstrap
		long connectStart = System.currentTimeMillis();
		ChannelFuture chanelFuture = bootstrap.connect(remoteHost, remotePort);
		chanelFuture.addListener(f -> {
			if (f.isSuccess()) {
				this.channel = ((ChannelFuture) f).channel();
				status = JladderForwardWorkerStatusEnum.Running;
				jladderAsynForwardClientInvokeChain.onConnect(new JladderChannelFuture((ChannelFuture) f));
			} else {
				log.error("connect remote[" + remoteHost + ":" + remotePort + "] error, cost_time=" + (System.currentTimeMillis() - connectStart) + " ms", f.cause());
			}
			connectFinishLatch.countDown();
		});

		connectFinishLatch.await();
	}
	
	public JladderForwardListener writeAndFlush(ByteBuf message) throws InterruptedException {
		if (status != JladderForwardWorkerStatusEnum.Running) {
			connect();
		}
		if (channel == null) {
			log.error(remoteHost + ":" + remotePort + " uninit...");
	    	jladderAsynForwardClientInvokeChain.onDisconnect(null);
			throw new JladderException(remoteHost + ":" + remotePort + " connect failed");
		}
		this.channel.writeAndFlush(message).addListener(f -> {
			if (!f.isSuccess()) {
				log.error("(" + id + ")" + this.channel.toString() + " flush error", f.cause());
			}
//			if (message.refCnt() > 0) {
//				ReferenceCountUtil.release(message);
//			}
		});
		
		return onReceiveListener;
	}

	@Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		ByteBuf byteBuf = (ByteBuf) msg;
		try {
			jladderAsynForwardClientInvokeChain.onReceiveData(new JladderByteBuf(byteBuf));
		} finally {
//			if (byteBuf.refCnt() > 0) {
//				byteBuf.release();
//			}
		}
	}

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    	log.debug(this.channel.toString() + "(" + id + ")" + " diconnect");
    	jladderAsynForwardClientInvokeChain.onDisconnect(new JladderChannelHandlerContext(ctx));
    }
    
    public void close() {
    	if (this.channel == null) {
    		return ;
    	}
    	this.channel.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    	log.error("[{}], remoteHost=" + remoteHost + ":" + remotePort + "[" + ctx.channel().toString() + "]" + " error, ", id, cause);
    	jladderAsynForwardClientInvokeChain.onDisconnect(new JladderChannelHandlerContext(ctx));
    }

	public void addListener(JladderAsynForwardClientListener listener) {
		jladderAsynForwardClientInvokeChain.addListener(listener);
	}
	
	private static class JladderAsynForwardClientInvokeChain implements JladderAsynForwardClientListener {
		private List<JladderAsynForwardClientListener> headerListener = new CopyOnWriteArrayList<JladderAsynForwardClientListener>();

		@Override
		public void onConnect(JladderChannelFuture jladderChannelFuture) {
			headerListener.forEach(listener -> {
				listener.onConnect(jladderChannelFuture);
			});
		}

		@Override
		public void onReceiveData(JladderByteBuf jladderByteBuf) {
			headerListener.forEach(listener -> {
				listener.onReceiveData(jladderByteBuf);
			});
		}

		@Override
		public void onDisconnect(JladderChannelHandlerContext jladderChannelHandlerContext) {
			headerListener.forEach(listener -> {
				listener.onDisconnect(jladderChannelHandlerContext);
			});
		}
		
		public void addListener(JladderAsynForwardClientListener listener) {
			if (listener == null) {
				return ;
			}
			this.headerListener.add(listener);
		}
	}
}