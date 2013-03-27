package ru.kt15.finomen.sessionServer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import ru.kt15.finomen.DataListener;
import ru.kt15.finomen.PacketConnection;
import ru.kt15.finomen.StreamConnection;
import ru.kt15.finomen.Token;
import ru.kt15.net.labs.sessions.ServerControl.CS;
import ru.kt15.net.labs.sessions.ServerControl.ValidateRequest;
import ru.kt15.net.labs.sessions.TCPServerPacketTypes;
import ru.kt15.net.labs.sessions.TcpClientPacketTypes;

import com.google.protobuf.InvalidProtocolBufferException;

public class ClientPacketListener implements DataListener {
	private final ReplicationPacketListener replicationListener;
	private final Set<StreamConnection> replicationConnections = new HashSet<>();
	private final AdminHandler adminListener;
	private final SessionStore sessionStore;
	private final ClientStore clientStore;

	public ClientPacketListener(ReplicationPacketListener replicationListener,
			AdminHandler adminListener, SessionStore sessionStore, ClientStore clientStore) {
		this.replicationListener = replicationListener;
		this.adminListener = adminListener;
		this.sessionStore = sessionStore;
		this.clientStore = clientStore;
	}

	@Override
	public void handlePacket(PacketConnection conn, InetSocketAddress source,
			InetSocketAddress dest, List<Token<?>> packet) {
		switch (TcpClientPacketTypes.valueOf((Byte)packet.get(0).get())) {
		case SERVER_ID: {
			String id = (String) packet.get(1).get();
			replicationListener.registerServer(source, id);
			String myId = Options.serverUUID.toString();
			ByteBuffer buf = ByteBuffer.allocate(myId.length() + 2);
			buf.put((byte) TCPServerPacketTypes.SERVER_ACK.ordinal());
			buf.put((byte) myId.length());
			buf.put(myId.getBytes());
			buf.flip();
			byte[] pack = new byte[buf.remaining()];
			buf.get(pack);
			((StreamConnection) conn).Send(pack);
			conn.removeRecvListener(this);
			conn.addRecvListener(replicationListener);
			replicationConnections.add(((StreamConnection) conn));
			break;
		}
		case SESSION_CHECK: {
			String id = (String) packet.get(1).get();
			String host = (String) packet.get(2).get();
			String sessionId = "";
			if (id.length() > Options.serverUUID.toString().length()) {
				sessionId = id.substring(0, Options.serverUUID.toString().length());
			} else {
				sessionId = id;
				System.out.println("Bad id: `" + sessionId + "`");
			}
			//String serverId = id.substring(sessionId.length());
			//TODO: cross-server request
			Client destination = clientStore.getClient(source.getAddress().getHostAddress());
			
			boolean valid = false;
			try {
				valid = sessionStore.validateSession(UUID.fromString(sessionId), destination);
			} catch(IllegalArgumentException e) {
				
			}
			valid = valid && host.equals(destination.computerName);
			ByteBuffer buf = ByteBuffer.allocate(2 + id.length());
			buf.put((byte)(valid ? TCPServerPacketTypes.SESSION_VALID : TCPServerPacketTypes.SESSION_FAIL).ordinal());
			buf.put((byte) id.length());
			buf.put(id.getBytes());
			buf.flip();
			byte[] pack = new byte[buf.remaining()];
			buf.get(pack);
			((StreamConnection) conn).Send(pack);
			break;
		}
		case SESSION_REQUEST: {
			String hostName = (String) packet.get(1).get();
			String remoteHost = (String) packet.get(2).get();
			Client from = clientStore.getClient(source.getAddress().getHostAddress());
			Client to = clientStore.getClient(remoteHost);

			if (from.computerName.isEmpty()) {
				from.computerName = hostName;
				from.valid = true;
			}
			
			boolean valid = from.valid && from.computerName.equals(hostName);
			Session s =  valid ? sessionStore.requestSession(from, to) : null;
			valid = valid && s != null;
			
			String sid = "";
			if (valid) {
				sid = s.id.toString() + s.serverId;
			}
			
			ByteBuffer buf = ByteBuffer.allocate(2 + remoteHost.length() + (valid ? (sid.length() + 1) : 0));
			buf.put((byte)(valid ? TCPServerPacketTypes.SESSION_ACK : TCPServerPacketTypes.SESSION_REJ).ordinal());
			if (valid) {
				buf.put((byte)sid.length());
				buf.put(sid.getBytes());
				
				for (StreamConnection c : replicationConnections) {
					replicationListener.update(c);
				}
				
			}
			
			buf.put((byte)remoteHost.length());
			buf.put(remoteHost.getBytes());
			
			buf.flip();
			byte[] pack = new byte[buf.remaining()];
			buf.get(pack);
			((StreamConnection) conn).Send(pack);
			
			break;
		}
		case ADMIN_CS:
			try {
				adminListener.setAdminConnection((StreamConnection) conn);
				CS cs = CS.parseFrom(((byte[])packet.get(1).get()));

				if (cs.hasLogTcp())
					adminListener.setTcpLogging(cs.getLogTcp());
				if (cs.hasLogUdp())
					adminListener.setUdpLogging(cs.getLogUdp());
				if (cs.hasLogAdmin())
					adminListener.setAdminLogging(cs.getLogAdmin());
				for (ValidateRequest req : cs.getUserValidateList()) {
					Client c = clientStore.getClient(req.getHost());
					if (c.computerName.equals(req.getName())) {
						c.valid = req.getValid();
					}
				}
			} catch (InvalidProtocolBufferException e) {
				e.printStackTrace();
			}
			break;
		case UNKNOWN:
			break;
		default:
			break;

		}
	}

}
