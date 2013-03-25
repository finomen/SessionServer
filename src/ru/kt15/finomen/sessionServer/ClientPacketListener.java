package ru.kt15.finomen.sessionServer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

import com.google.protobuf.InvalidProtocolBufferException;

import ru.kt15.finomen.PacketConnection;
import ru.kt15.finomen.PacketListener;
import ru.kt15.finomen.StreamConnection;
import ru.kt15.net.labs.sessions.ServerControl.CS;
import ru.kt15.net.labs.sessions.TCPServerPacketTypes;
import ru.kt15.net.labs.sessions.TcpClientPacketTypes;

public class ClientPacketListener implements PacketListener {
	private final ReplicationPacketListener replicationListener;
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
			InetSocketAddress dest, byte[] packet) {
		switch (TcpClientPacketTypes.valueOf(packet[0])) {
		case SERVER_ID: {
			String id = new String(Arrays.copyOfRange(packet, 2, 2 + packet[1]));
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
			conn.removeAllListeners();
			conn.addRecvListener(replicationListener);
			break;
		}
		case SESSION_CHECK: {
			String id = new String(Arrays.copyOfRange(packet, 2, 2 + packet[1]));
			int p = 3 + id.length();
			String host = new String(Arrays.copyOfRange(packet, p, 2 + packet[p]));
			String sessionId = id.substring(0, Options.serverUUID.toString().length());
			String serverId = id.substring(sessionId.length());
			//TODO: cross-server request
			Client destination = clientStore.getClient(source.getAddress().getHostAddress());
			boolean valid = sessionStore.validateSession(UUID.fromString(sessionId), destination);
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
			String hostName = new String(Arrays.copyOfRange(packet, 2, 2 + packet[1]));
			int p = 3 + hostName.length();
			String remoteHost = new String(Arrays.copyOfRange(packet, p, 2 + packet[p]));
			Client from = clientStore.getClient(source.getAddress().getHostAddress());
			Client to = clientStore.getClient(source.getAddress().getHostAddress());

			if (from.computerName.isEmpty()) {
				from.computerName = hostName;
				from.valid = false;
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
				CS cs = CS.parseFrom(Arrays.copyOfRange(packet, 3,
						packet.length));

				if (cs.hasLogTcp())
					adminListener.setTcpLogging(cs.getLogTcp());
				if (cs.hasLogUdp())
					adminListener.setUdpLogging(cs.getLogUdp());
				if (cs.hasLogAdmin())
					adminListener.setAdminLogging(cs.getLogAdmin());
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
