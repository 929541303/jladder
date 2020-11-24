package org.jladder.adapter.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.jladder.adapter.protocol.listener.JladderOnReceiveDataListener;
import org.jladder.common.core.NettyProxyContext;
import org.jladder.common.core.config.JladderConfig;

/**
 * Proxy连接池只关注实现连接池的策略即可
 * @author hudaming
 */
public class JladderForwardExecutor {
	
	private List<JladderForwardWorker> jladderForwardWorkerList = new ArrayList<>();
	private AtomicInteger RoundRobinRouter = new AtomicInteger(0);
	private int currentWorkerCount = 20;
	
	public JladderForwardExecutor() {
		JladderConfig config = NettyProxyContext.getConfig();
		for (int i = 0 ;i < currentWorkerCount; i ++) {
			JladderForwardWorker jladderForwardWorker = new JladderForwardWorker(config.getOutsideProxyHost(), config.getOutsideProxyPort());
			jladderForwardWorker.connect();
			jladderForwardWorkerList.add(jladderForwardWorker);
		}
	}

	public JladderOnReceiveDataListener writeAndFlush(JladderMessage message) {
		return select().writeAndFlush(message);
	}
	
	private JladderForwardWorker select() {
		// TODO select实现要确保，一次只服务一个客户端会话
		return jladderForwardWorkerList.get(RoundRobinRouter.getAndIncrement() % currentWorkerCount);
	}
}
