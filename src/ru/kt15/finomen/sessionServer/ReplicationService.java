package ru.kt15.finomen.sessionServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import com.google.protobuf.InvalidProtocolBufferException;

import ru.kt15.finomen.DataListener;
import ru.kt15.finomen.IOService;
import ru.kt15.finomen.PacketConnection;
import ru.kt15.finomen.StreamConnection;
import ru.kt15.finomen.Token;
import ru.kt15.net.labs.sessions.ServerProtocolDefinition;
import ru.kt15.net.labs.sessions.ServerReplication;
import ru.kt15.net.labs.sessions.TCPServerPacketTypes;
import ru.kt15.net.labs.sessions.TcpClientPacketTypes;
import ru.kt15.net.labs.sessions.TcpReplicationTypes;

public class ReplicationService implements Runnable, DataListener, ClientUpdateListener, DiscoverListener {
	
	private class DelayedCheck {
		final Date expiration;
		private final StreamConnection clientConnection;
		private final Client dest;
		private boolean sended = false;
		private String serverId;
		UUID sessionId;
		
		DelayedCheck(StreamConnection clientConnection, UUID sessionId, String serverId, Client dest) {
			this.clientConnection = clientConnection;
			this.sessionId = sessionId;
			this.serverId = serverId;
			this.dest = dest;
			expiration = new Date(new Date().getTime() + Options.delayedCheckTTL);
		}
		
		void timeout() {
			if (!sended) {
				send(false);
			}
		}
		
		void got() {
			send(sessionStore.validateSession(sessionId, dest));
		}
			
		private void send(boolean valid) {
			String stringId = sessionId.toString() + serverId;
			ByteBuffer buf = ByteBuffer.allocate(2 + stringId.length());
			buf.put((byte)(valid ? TCPServerPacketTypes.SESSION_VALID : TCPServerPacketTypes.SESSION_FAIL).ordinal());
			buf.put((byte)stringId.length());
			buf.put(stringId.getBytes());
			buf.flip();
			byte[] bin = new byte[buf.remaining()];
			buf.get(bin);
			clientConnection.Send(bin);
			sended = true;
		}
	}
	
	
	private final IOService ioService;
	private final ClientStore clientStore;
	private final SessionStore sessionStore;
	private final Map<String, StreamConnection> connections = new HashMap<>();
	private final Queue<DelayedCheck> deadlineQueue = new LinkedList<>();
	private final Map<UUID, DelayedCheck> waitingChecks = new HashMap<>();
	private final Map<String, String> hostToId = new HashMap<>();
	private ServerReplication.List.Builder currentList = ServerReplication.List.newBuilder();
	
	public ReplicationService(IOService ioService, ClientStore clientStore, SessionStore sessionStore) {
		this.ioService = ioService;
		this.clientStore = clientStore;
		clientStore.addListener(this);
		this.sessionStore = sessionStore;
		new Thread(this).start();
	}
 	
	public void addConnection(StreamConnection connection, String id) {
		System.out.println("Connected server " + id);
		hostToId.put(connection.getRemote().getAddress().getHostAddress(), id);
		connections.put(id, connection);
		connection.addRecvListener(this);
	}	
	
	public void addSession(Session session) {
		ServerReplication.Session sess = 
				ServerReplication.Session.newBuilder()
				.setKey(
						ServerReplication.SessionKey.newBuilder()
							.setServerId(session.serverId)
							.setSessionId(session.id.toString())
						)
				.setSessionDest(session.dest.host)
				.setSessionSource(session.source.host)
				.setValidUntil(session.validUntil.getTime())
				.setTimestamp(System.currentTimeMillis()).build();
		currentList.addSessions(sess);
	}
	
	public void updateSession(Session session) {
		ServerReplication.Session sess = 
				ServerReplication.Session.newBuilder()
				.setKey(
						ServerReplication.SessionKey.newBuilder()
							.setServerId(session.serverId)
							.setSessionId(session.id.toString())
						)
				.setValidUntil(session.validUntil.getTime())
				.setTimestamp(System.currentTimeMillis()).build();
		currentList.addSessions(sess);
	}
	
	@Override
	public void addClient(Client client) {
		ServerReplication.Host host = 
				ServerReplication.Host.newBuilder()
				.setKey(
						ServerReplication.HostKey.newBuilder()
							.setAddress(client.host)
						)
				.setName(client.computerName)
				.setValid(client.valid)
				.setTimestamp(System.currentTimeMillis()).build();
		currentList.addHosts(host);
	}
	
	@Override
	public void changeValid(Client client) {
		ServerReplication.Host host = 
				ServerReplication.Host.newBuilder()
				.setKey(
						ServerReplication.HostKey.newBuilder()
							.setAddress(client.host)
						)
				.setValid(client.valid)
				.setTimestamp(System.currentTimeMillis()).build();
		currentList.addHosts(host);
	}
	
	@Override
	public void changeName(Client client) {
		ServerReplication.Host host = 
				ServerReplication.Host.newBuilder()
				.setKey(
						ServerReplication.HostKey.newBuilder()
							.setAddress(client.host)
						)
				.setName(client.computerName)
				.setValid(client.valid)
				.setTimestamp(System.currentTimeMillis()).build();
		currentList.addHosts(host);
	}
	
	public void validateSession(StreamConnection clientConnection, UUID sessionId, String serverId, Client dest) {
		DelayedCheck check = new DelayedCheck(clientConnection, sessionId, serverId, dest);
		waitingChecks.put(sessionId, check);
		deadlineQueue.add(check);
		requestSession(serverId, sessionId);
	}
	
	private void requestSession(String serverId, UUID sessionId) {		
		ServerReplication.List list = ServerReplication.List.newBuilder()
				.addSessions(
						ServerReplication.Session.newBuilder()
						.setKey(
								ServerReplication.SessionKey.newBuilder()
								.setServerId(serverId)
								.setSessionId(sessionId.toString())
								.build()
						)
						.build()
				).build();
		send(TcpReplicationTypes.LIST_REQUEST, list);
	}
	
	private void send(TcpReplicationTypes type, ServerReplication.List packet) {
		System.out.println("Send updates");
		ByteBuffer buf = ByteBuffer.allocate(3 + packet.getSerializedSize());
		buf.put((byte)(0xF0 | type.ordinal()));
		buf.putShort((short)packet.getSerializedSize());
		buf.put(packet.toByteArray());
		buf.flip();
		byte[] bin = new byte[buf.remaining()];
		buf.get(bin);
		Set<String> badHosts = new HashSet<>();
		for (String id : connections.keySet()) {
			System.out.println("Send update to " + id);
			if (!connections.get(id).Send(bin)) {
				badHosts.add(id);
			}
		}
		
		for (String host : badHosts) {
			connections.remove(host);
		}
	}

	@Override
	public void run() {
		while (true) {
			ServerReplication.List list;
			synchronized (this) {
				list = currentList.build();
				currentList = ServerReplication.List.newBuilder();
			}
			
			for (Session s : sessionStore.getSessions()) {
				currentList.addSessions(ServerReplication.Session.newBuilder()
						.setKey(ServerReplication.SessionKey.newBuilder()
								.setServerId(s.serverId)
								.setSessionId(s.id.toString()).build()
								)
								.setValidUntil(s.validUntil.getTime()));
			}
			
			if (list.getSessionsCount() > 0 || list.getHostsCount() > 0) {
				send(TcpReplicationTypes.LIST_UPDATES, list);
			}
			
			
			synchronized (this) {
				while (!deadlineQueue.isEmpty() && deadlineQueue.peek().expiration.before(new Date())) {
					DelayedCheck check = deadlineQueue.poll();
					waitingChecks.remove(check.sessionId);
					check.timeout();
				}
			}
			
			try {
				Thread.sleep(Options.replicationRoutineLoop);
			} catch (InterruptedException e) {
				return;
			}
		}
	}

	@Override
	public void handlePacket(PacketConnection conn, InetSocketAddress source,
			InetSocketAddress dest, List<Token<?>> packet) {
		try {
			ServerReplication.List updates = ServerReplication.List.parseFrom(((byte[])packet.get(1).get()));
			switch (TcpReplicationTypes.valueOf(((Byte)packet.get(0).get()) & (byte)0x0F) ) {
			case LIST_REQUEST:
				{
					System.out.println("List request");
					ServerReplication.List.Builder toSend = ServerReplication.List.newBuilder();
					if (updates.getHostsCount() == 0 && updates.getSessionsCount() == 0) { 
						for (Client c : clientStore.getClients()) {
							if (!c.computerName.isEmpty()) {
								ServerReplication.Host h = convert(c);
								toSend.addHosts(h);
							}
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
							
							if (!c.computerName.isEmpty()) {
								toSend.addHosts(convert(c));
							}
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
						UUID id = UUID.fromString(session.getKey().getSessionId());
						if (waitingChecks.containsKey(id)) {
							DelayedCheck check = waitingChecks.remove(id);
							check.got();
						}
					}
	
					for (ServerReplication.Host host : updates.getHostsList()) {
						clientStore.update(host);
					}
				break;
			}
			case UNKNOWN:
				if ((Byte)packet.get(0).get() == TCPServerPacketTypes.SERVER_ACK.ordinal()) {
					String serverId = (String) packet.get(1).get();
					addConnection((StreamConnection)conn, serverId);
				}
				break;
			}
		
		} catch (InvalidProtocolBufferException e) {
			e.printStackTrace();
		}
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
	public void discoverRequestRecvd(InetSocketAddress host) {
	}

	@Override
	public void discoverRecvd(String host, int port) {
		if (hostToId.containsKey(host) && connections.containsKey(hostToId.get(host))) {
			return;
		}
		
		try {
			StreamConnection conn = new StreamConnection(new ServerProtocolDefinition(), new InetSocketAddress(host, port), ioService);
			conn.addRecvListener(this);
			String stringId = Options.serverUUID.toString();
			ByteBuffer buf = ByteBuffer.allocate(2 + stringId.length());
			buf.put((byte) TcpClientPacketTypes.SERVER_ID.ordinal());
			buf.put((byte) stringId.length());
			buf.put(stringId.getBytes());
			buf.flip();
			byte[] bin = new byte[buf.remaining()];
			buf.get(bin);
			conn.Send(bin);
		} catch (IOException e) {
		}
		
	}
}
