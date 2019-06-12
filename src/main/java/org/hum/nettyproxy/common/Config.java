package org.hum.nettyproxy.common;

public class Config {

//	public static final String PROXY_HOST = "47.75.102.227";
	public static final String PROXY_HOST = "127.0.0.1";
	public static final int PROXY_PORT = 5432;
	public static final int CONNECT_TIMEOUT = 3000;
	// Netty参数配置
	public static final int DEFAULT_WORKER_COUNT = Runtime.getRuntime().availableProcessors() * 10; 
	
}
