package ru.kt15.finomen.sessionServer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;

import com.google.protobuf.InvalidProtocolBufferException;

import ru.kt15.finomen.PacketConnection;
import ru.kt15.finomen.PacketListener;
import ru.kt15.finomen.StreamConnection;
import ru.kt15.finomen.WritablePacketConnection;
import ru.kt15.net.labs.sessions.ServerControl.CS;
import ru.kt15.net.labs.sessions.TCPServerPacketTypes;
import ru.kt15.net.labs.sessions.TcpClientPacketTypes;

public class ClientPacketListener implements PacketListener {
	private final ReplicationPacketListener replicationListener;
	private final AdminHandler adminListener;
	
	public ClientPacketListener(ReplicationPacketListener replicationListener, AdminHandler adminListener) {
		this.replicationListener = replicationListener;
		this.adminListener = adminListener;
	}
	
	@Override
	public void handlePacket(PacketConnection conn, InetSocketAddress source,
			InetSocketAddress dest, byte[] packet) {
		switch(TcpClientPacketTypes.valueOf(packet[0])) {
		case SERVER_ID:
		{
			String id = new String(Arrays.copyOfRange(packet, 3, 3 + packet[1]));
			replicationListener.registerServer(source, id);
			String myId = Options.serverUUID.toString();
			ByteBuffer buf = ByteBuffer.allocate(myId.length() + 2);
			buf.put((byte) TCPServerPacketTypes.SERVER_ACK.ordinal());
			buf.put((byte) myId.length());
			buf.put(myId.getBytes());
			buf.flip();
			byte[] pack = new byte[buf.remaining()];
			buf.get(pack);
			((StreamConnection)conn).Send(pack);
			conn.removeAllListeners();
			conn.addRecvListener(replicationListener);
			break;
		}
		case SESSION_CHECK:
			//TODO:
			break;
		case SESSION_REQUEST:
			//TODO:
			break;
		case ADMIN_CS:
			try {
				adminListener.setAdminConnection((StreamConnection) conn);
				CS cs = CS.parseFrom(Arrays.copyOfRange(packet, 3, packet.length));
				
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
