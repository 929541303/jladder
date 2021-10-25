package org.jladder.core.listener;

import org.jladder.core.JladderByteBuf;
import org.jladder.core.message.JladderDisconnectMessage;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JladderForwardListener {

	private JladderMessageReceiveEvent onReceiveEventCallback;
	private JladderChannelDisconnectEvent onDisconnectEvent;
	
	public JladderForwardListener onReceive(JladderMessageReceiveEvent onReceiveEventCallback) {
		this.onReceiveEventCallback = onReceiveEventCallback;
		return this;
	}
	
	public JladderForwardListener onDisconnect(JladderChannelDisconnectEvent onDisconnectEvent) {
		this.onDisconnectEvent = onDisconnectEvent;
		return this;
	}

	public void fireReadEvent(JladderByteBuf jladderByteBuf) {
		if (onReceiveEventCallback == null) {
			log.error("error code...");
			return ;
		}
		onReceiveEventCallback.onReceive(jladderByteBuf);
	}

	public void fireDisconnectEvent(JladderDisconnectMessage msg) {
		if (onDisconnectEvent == null) {
			log.error("error code...");
			return;
		}
		onDisconnectEvent.onDisconnect(msg);
	}

	public static interface JladderMessageReceiveEvent {
		public void onReceive(JladderByteBuf byteBuf);
	}


	public static interface JladderChannelDisconnectEvent {
		public void onDisconnect(JladderDisconnectMessage msg);
	}
}
