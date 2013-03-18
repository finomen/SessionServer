package ru.kt15.finomen.sessionServer;

import java.nio.ByteBuffer;

import ru.kt15.finomen.StreamProtocolDefinition;
import ru.kt15.net.labs.sessions.TcpClientPacketTypes;
import ru.kt15.net.labs.sessions.TcpReplicationTypes;

public class ClientProtocolDefinition implements StreamProtocolDefinition,
		Cloneable {
	private static interface PacketParser {
		int remaining();

		void put(byte[] data);

		byte[] build();
	}

	private static class ADMIN_CS_Parser implements PacketParser {
		short len = -1;
		ByteBuffer buf = ByteBuffer.allocate(2);

		@Override
		public int remaining() {
			return buf.remaining();
		}

		@Override
		public void put(byte[] data) {
			buf.put(data);
			if (len == -1 && buf.remaining() == 0) {
				buf.flip();
				len = buf.getShort();
				buf = ByteBuffer.allocate(3 + len);
				buf.put((byte) TcpClientPacketTypes.ADMIN_CS.ordinal());
				buf.putShort(len);
			}
		}

		@Override
		public byte[] build() {
			buf.flip();
			byte[] pack = new byte[buf.remaining()];
			buf.get(pack);
			return pack;
		}

	}

	private PacketParser parser = null;
	private boolean replication = false;

	@Override
	public void put(byte[] data) {
		if (parser != null) {
			parser.put(data);
			return;
		}

		if (replication) {
			TcpReplicationTypes packetId = TcpReplicationTypes.valueOf(data[0]);
			switch (packetId) {
			case LIST_REQUEST:
				// TODO:
				break;
			case LIST_UPDATES:
				// TODO:
				break;
			case UNKNOWN:
				break;
			default:
				break;

			}
		} else {
			TcpClientPacketTypes packetId = TcpClientPacketTypes
					.valueOf(data[0]);
			switch (packetId) {
			case ADMIN_CS:
				parser = new ADMIN_CS_Parser();
				break;
			case SERVER_ID:
				// TODO:
				break;
			case SESSION_CHECK:
				// TODO:
				break;
			case SESSION_REQUEST:
				// TODO:
				break;
			case UNKNOWN:
				break;
			default:
				break;

			}
		}
	}

	@Override
	public int remaining() {
		if (parser != null) {
			return parser.remaining();
		}

		return 1;
	}

	@Override
	public byte[] build() {
		byte[] res = parser.build();
		parser = null;
		return res;
	}

	@Override
	public StreamProtocolDefinition clone() {
		try {
			return (StreamProtocolDefinition) super.clone();
		} catch (CloneNotSupportedException e) {
			return null;
		}
	}

}
