package ru.kt15.finomen.sessionServer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

import ru.kt15.finomen.PacketConnection;
import ru.kt15.finomen.PacketListener;
import ru.kt15.finomen.WritablePacketConnection;

public class DiscoverHandler implements PacketListener {
	private final Set<DiscoverListener> listeners = new HashSet<>();

	@Override
	public void handlePacket(PacketConnection conn, InetSocketAddress source,
			InetSocketAddress dest, byte[] packet) {
		if (packet.length == 1 && packet[0] == 0x00) {
			discoverRequest(source);

			String host = dest.getAddress().getHostAddress();
			ByteBuffer reply = ByteBuffer.allocate(host.length() + 4);
			reply.put((byte) 0x01);
			reply.put((byte) host.length());
			reply.put(host.getBytes());
			reply.putShort(Options.udpPort);
			reply.flip();
			byte[] pack = new byte[reply.remaining()];
			reply.get(pack);
			((WritablePacketConnection) conn).send(new InetSocketAddress(
					"255.255.255.255", Options.clientUdpPort), pack);
		} else if (packet[0] == 0x01) {
			ByteBuffer buf = ByteBuffer.wrap(packet);
			buf.get();
			int len = buf.get();
			byte[] hostBytes = new byte[len];
			buf.get(hostBytes);
			int port = buf.getInt();
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
