package ru.kt15.finomen.sessionServer;

import java.net.InetSocketAddress;

public interface DiscoverListener {
	void discoverRequestRecvd(InetSocketAddress host);

	void discoverRecvd(String host, int port);
}
