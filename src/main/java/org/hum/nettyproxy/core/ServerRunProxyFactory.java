package org.hum.nettyproxy.core;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.hum.nettyproxy.ServerRun.Starter;
import org.hum.nettyproxy.adapter.http.NettyHttpInsideProxyServer;
import org.hum.nettyproxy.adapter.http.NettyHttpSimpleProxyServer;
import org.hum.nettyproxy.adapter.socks5.NettySocksInsideProxyServer;
import org.hum.nettyproxy.common.enumtype.RunModeEnum;
import org.hum.nettyproxy.server.NettyOutsideProxyServer;

public class ServerRunProxyFactory {
	
	private static final NettyHttpSimpleStarter httpSimpleStarter = new NettyHttpSimpleStarter();
	private static final NettyHttpInsideProxyStarter httpInsideProxyStarter = new NettyHttpInsideProxyStarter();
	private static final OutsideProxyStarter outsideProxyStarter = new OutsideProxyStarter();
	private static final NettySocksInsideProxyStarter socksInsideProxyStarter = new NettySocksInsideProxyStarter();
	static final ExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();

	public static Starter create(RunModeEnum runMode) {
		if (runMode == RunModeEnum.HttpSimpleProxy) {
			return httpSimpleStarter;
		} else if (runMode == RunModeEnum.HttpInsideServer) {
			return httpInsideProxyStarter;
		} else if (runMode == RunModeEnum.OutsideServer) {
			return outsideProxyStarter;
		} else if (runMode == RunModeEnum.SocksInsideServer) {
			return socksInsideProxyStarter;
		}
		
		throw new IllegalArgumentException("unimplement run_mode=" + runMode.getCode());
	}
}

class NettyHttpSimpleStarter implements Starter {
	
	@Override
	public void start(NettyProxyConfig config) {
		ServerRunProxyFactory.EXECUTOR_SERVICE.execute(new NettyHttpSimpleProxyServer(config));
	}
}

class NettyHttpInsideProxyStarter implements Starter {
	
	@Override
	public void start(NettyProxyConfig config) {
		ServerRunProxyFactory.EXECUTOR_SERVICE.execute(new NettyHttpInsideProxyServer(config));
	}
}

class OutsideProxyStarter implements Starter {
	
	@Override
	public void start(NettyProxyConfig config) {
		ServerRunProxyFactory.EXECUTOR_SERVICE.execute(new NettyOutsideProxyServer(config));
	}
}

class NettySocksInsideProxyStarter implements Starter {
	
	@Override
	public void start(NettyProxyConfig config) {
		ServerRunProxyFactory.EXECUTOR_SERVICE.execute(new NettySocksInsideProxyServer(config));
	}
}