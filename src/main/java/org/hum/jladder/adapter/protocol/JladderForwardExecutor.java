package org.hum.jladder.adapter.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.hum.jladder.common.core.NettyProxyContext;
import org.hum.jladder.common.core.config.JladderConfig;

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
			jladderForwardWorkerList.add(new JladderForwardWorker(config.getOutsideProxyHost(), config.getOutsideProxyPort()));
		}
	}

	public JladderForwardWorkerListener writeAndFlush(JladderMessage message) {
	
		JladderForwardWorker jladderForward = select();
		
		JladderForwardWorkerListener listener = new JladderForwardWorkerListener(jladderForward);
		
		jladderForward.writeAndFlush(message);
		
		return listener;
	}
	
	private JladderForwardWorker select() {
		// TODO 先简单实现
		return jladderForwardWorkerList.get(RoundRobinRouter.getAndIncrement() % currentWorkerCount);
	}
}
