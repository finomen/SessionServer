package ru.kt15.finomen.sessionServer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

import ru.kt15.finomen.PacketConnection;
import ru.kt15.finomen.PacketListener;
import ru.kt15.finomen.WritablePacketConnection;
import ru.kt15.net.labs.sessions.UdpPacketTypes;

public class DiscoverHandler implements PacketListener {
	private final Set<DiscoverListener> listeners = new HashSet<>();

	@Override
	public void handlePacket(PacketConnection conn, InetSocketAddress source,
			InetSocketAddress dest, byte[] packet) {
		if (packet.length == 0)
			return;
		if (packet.length == 1 && packet[0] == UdpPacketTypes.DISCOVER_REQ.ordinal()) {
			discoverRequest(source);

			String host = dest.getAddress().getHostAddress();
			ByteBuffer reply = ByteBuffer.allocate(host.length() + 4);
			reply.put((byte) 0x01);
			reply.put((byte) host.length());
			reply.put(host.getBytes());
			reply.putShort(Options.tcpPort);
			reply.flip();
			byte[] pack = new byte[reply.remaining()];
			reply.get(pack);
			((WritablePacketConnection) conn).send(new InetSocketAddress(
					"255.255.255.255", Options.clientUdpPort == -1 ? source.getPort() : Options.clientUdpPort), pack);
		} else if (packet[0] == UdpPacketTypes.DISCOVER_SRV.ordinal()) {
			ByteBuffer buf = ByteBuffer.wrap(packet);
			buf.get();
			int len = buf.get();
			byte[] hostBytes = new byte[len];
			buf.get(hostBytes);
			short port = buf.getShort();
			discover(new String(hostBytes), port);
		}
	}

	private void discoverRequest(InetSocketAddress source) {
		for (DiscoverListener listener : listeners) {
			listener.discoverRequestRecvd(source);
		}
	}

	private void discover(String host, int port) {
		for (DiscoverListener listener : listeners) {
			listener.discoverRecvd(host, port);
		}
	}

	public void addListener(DiscoverListener listener) {
		listeners.add(listener);
	}

}
