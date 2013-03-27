package ru.kt15.finomen.sessionServer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import ru.kt15.finomen.DataListener;
import ru.kt15.finomen.PacketConnection;
import ru.kt15.finomen.StreamConnection;
import ru.kt15.finomen.Token;
import ru.kt15.finomen.WritablePacketConnection;
import ru.kt15.net.labs.sessions.ServerReplication;
import ru.kt15.net.labs.sessions.TcpReplicationTypes;

import com.google.protobuf.InvalidProtocolBufferException;

public class ReplicationPacketListener implements DataListener {
	private final Map<InetSocketAddress, String> serverIds = new HashMap<>();
	private final ClientStore clientStore;
	private final SessionStore sessionStore;

	public ReplicationPacketListener(ClientStore clientStore, SessionStore sessionStore) {
		this.clientStore = clientStore;
		this.sessionStore = sessionStore;

	}
	
	private static ServerReplication.Host convert(Client c) {
		return ServerReplication.Host.newBuilder()
		.setKey(ServerReplication.HostKey.newBuilder().setAddress(c.host).build())
		.setName(c.computerName)
		.setValid(c.valid)
		.setTimestamp(System.currentTimeMillis()).build();
	}
	
	private static ServerReplication.Session convert(Session s) {
		return ServerReplication.Session.newBuilder()
				.setKey(ServerReplication.SessionKey.newBuilder().setServerId(s.serverId).setSessionId(s.id.toString()).build())
				.setSessionDest(s.dest.host)
				.setSessionSource(s.source.host)
				.setValidUntil(s.validUntil.getTime())
				.setTimestamp(System.currentTimeMillis()).build();
				
	}

	@Override
	public void handlePacket(PacketConnection conn, InetSocketAddress source,
			InetSocketAddress dest, List<Token<?>> packet) {

		try {
			ServerReplication.List updates = ServerReplication.List.parseFrom(((String)packet.get(1).get()).getBytes());
			switch (TcpReplicationTypes.valueOf((Byte)packet.get(0).get())) {
			case LIST_REQUEST:
				{
					ServerReplication.List.Builder toSend = ServerReplication.List.newBuilder();
					if (updates.getHostsCount() == 0 && updates.getSessionsCount() == 0) { 
						for (Client c : clientStore.getClients()) {
							ServerReplication.Host h = convert(c);
							toSend.addHosts(h);
						}
						
						for (Session s : sessionStore.getSessions()) {
							ServerReplication.Session sess = convert(s);
							toSend.addSessions(sess);
						}
					} else {
						for (ServerReplication.Session s : updates.getSessionsList()) {
							Session ss = sessionStore.getSession(UUID.fromString(s.getKey().getSessionId()), s.getKey().getServerId());
							if (ss != null) {
								toSend.addSessions(convert(ss));	
							}
						}
						
						for (ServerReplication.Host h : updates.getHostsList()) {
							Client c = clientStore.getClient(h.getKey().getAddress());
							toSend.addHosts(convert(c));
						}
					}
					
					ServerReplication.List pack = toSend.build();
					
					ByteBuffer b = ByteBuffer.allocate(3 + pack.getSerializedSize());
					b.put((byte)(TcpReplicationTypes.LIST_UPDATES.ordinal() | 0xF0));
					b.putShort((short)pack.getSerializedSize());
					b.put(pack.toByteArray());
					b.flip();
					byte[] raw = new byte[b.remaining()]; 
					b.get(raw);
					((StreamConnection)conn).Send(raw);
				}
				break;
			case LIST_UPDATES: {
					for (ServerReplication.Session session : updates.getSessionsList()) {
						sessionStore.update(session);
					}
	
					for (ServerReplication.Host host : updates.getHostsList()) {
						clientStore.update(host);
					}
				break;
			}
			case UNKNOWN:
				break;
			}
		
		} catch (InvalidProtocolBufferException e) {
			e.printStackTrace();
		}
	}

	public void registerServer(InetSocketAddress addr, String id) {
		serverIds.put(addr, id);
	}

}
