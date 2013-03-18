package ru.kt15.finomen.sessionServer;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.google.protobuf.InvalidProtocolBufferException;

import ru.kt15.finomen.PacketConnection;
import ru.kt15.finomen.PacketListener;
import ru.kt15.net.labs.sessions.ServerReplication.Host;
import ru.kt15.net.labs.sessions.ServerReplication.List;
import ru.kt15.net.labs.sessions.ServerReplication.Session;
import ru.kt15.net.labs.sessions.TcpReplicationTypes;

public class ReplicationPacketListener implements PacketListener {
	private final Map<InetSocketAddress, String> serverIds = new HashMap<>();
	private final ClientStore clientStore;

	public ReplicationPacketListener(ClientStore clientStore) {
		this.clientStore = clientStore;

	}

	@Override
	public void handlePacket(PacketConnection conn, InetSocketAddress source,
			InetSocketAddress dest, byte[] packet) {

		switch (TcpReplicationTypes.valueOf(packet[0])) {
		case LIST_REQUEST:
			break;
		case LIST_UPDATES: {
			try {
				List updates = List.parseFrom(Arrays.copyOfRange(packet, 1,
						packet.length));
				for (Session session : updates.getSessionsList()) {
					// TODO:
				}

				for (Host host : updates.getHostsList()) {
					clientStore.update(host);
				}
			} catch (InvalidProtocolBufferException e) {
				e.printStackTrace();
			}
			break;
		}
		case UNKNOWN:
			break;
		}
	}

	public void registerServer(InetSocketAddress addr, String id) {
		serverIds.put(addr, id);
	}

}
