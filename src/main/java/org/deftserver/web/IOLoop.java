package org.deftserver.web;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.Iterator;

import org.deftserver.web.protocol.HttpProtocol;
import org.deftserver.web.protocol.HttpProtocolImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IOLoop {
	
	private final static Logger logger = LoggerFactory.getLogger(IOLoop.class);

	private static final long TIMEOUT = 250;	// 0.25s in ms
	private static final long CALLBACK_PERIOD = 2 * 1000;	//2s in ms

	private final Application application;
	private ServerSocketChannel channel;
	private Selector selector;
	private long lastCallback;
	

	protected IOLoop(Application application) {
		this.application = application;
		try {
			channel = ServerSocketChannel.open();
			channel.configureBlocking(false);
			selector = Selector.open();
		} catch (IOException e) {
			logger.error("Error creating ServerSocketChannel: {}", e);
		}
	}

	public void start() {
		Thread.currentThread().setName("I/O-LOOP");
		//Thread.currentThread().setPriority(Thread.MAX_PRIORITY);

		registerSelector();
		HttpProtocol protocol = new HttpProtocolImpl(application);
		while (true) {
			try {
				if (selector.select(TIMEOUT) == 0) {
					invokeCallback(protocol);
					continue;
				}

				Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
				while (keys.hasNext()) {
					SelectionKey key = keys.next();
					if (key.isAcceptable()) {
						protocol.handleAccept(key);
					}
					if (key.isReadable()) {
						protocol.handleRead(key);
					}
					keys.remove();
				}
				invokeCallback(protocol);

			} catch (IOException e) {
				logger.error("Exception received in IOLoop: {}", e);			}
		}
	}

	private void invokeCallback(HttpProtocol protocol) {
		long now = System.currentTimeMillis();
		if (now >= lastCallback + CALLBACK_PERIOD) {
			protocol.handleCallback();
			lastCallback = now;
		}
	}

	protected void listen(int port) {
		InetSocketAddress endpoint = new InetSocketAddress(port);	// use "any" address
		try {
			channel.socket().bind(endpoint);
		} catch (IOException e) {
			logger.error("Could not bind socket: {}", e);
		}
	}

	private void registerSelector() {
		try {
			channel.register(selector, SelectionKey.OP_ACCEPT);
		} catch (ClosedChannelException e) {
			logger.error("Could not register selector: {}", e);		
		}		
	}

}