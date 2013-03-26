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
	
	private static class SERVER_ID_Parser implements PacketParser {
		byte len = -1;
		ByteBuffer buf = ByteBuffer.allocate(1);

		@Override
		public int remaining() {
			return buf.remaining();
		}

		@Override
		public void put(byte[] data) {
			buf.put(data);
			if (len == -1 && buf.remaining() == 0) {
				buf.flip();
				len = buf.get();
				buf = ByteBuffer.allocate(2 + len);
				buf.put((byte) TcpClientPacketTypes.SERVER_ID.ordinal());
				buf.put(len);
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
	
	private static class SESSION_CHECK_Parser implements PacketParser {
		byte sidl = -1;
		byte cnl = -1;
		byte[] sid;
		ByteBuffer buf = ByteBuffer.allocate(2);

		@Override
		public int remaining() {
			return buf.remaining();
		}

		@Override
		public void put(byte[] data) {
			buf.put(data);
			if (sidl == -1 && buf.remaining() == 0) {
				buf.flip();
				sidl = buf.get();
				buf = ByteBuffer.allocate(sidl);
			} else if (sid == null && buf.remaining() == 0){
				buf.flip();
				sid = new byte[sidl];
				buf.get(sid);
				buf = ByteBuffer.allocate(1);
			} else if (cnl == -1 && buf.remaining() == 0) {
				buf.flip();
				cnl = buf.get();
				buf = ByteBuffer.allocate(3 + sidl + cnl);
				buf.put((byte) TcpClientPacketTypes.SESSION_CHECK.ordinal());
				buf.put(sidl);
				buf.put(sid);
				buf.put(cnl);
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
	
	private static class SESSION_REQUEST_Parser implements PacketParser {
		byte sidl = -1;
		byte cnl = -1;
		byte[] sid;
		ByteBuffer buf = ByteBuffer.allocate(2);

		@Override
		public int remaining() {
			return buf.remaining();
		}

		@Override
		public void put(byte[] data) {
			buf.put(data);
			if (sidl == -1 && buf.remaining() == 0) {
				buf.flip();
				sidl = buf.get();
				buf = ByteBuffer.allocate(sidl);
			} else if (sid == null && buf.remaining() == 0){
				buf.flip();
				sid = new byte[sidl];
				buf.get(sid);
				buf = ByteBuffer.allocate(1);
			} else if (cnl == -1 && buf.remaining() == 0) {
				buf.flip();
				cnl = buf.get();
				buf = ByteBuffer.allocate(3 + sidl + cnl);
				buf.put((byte) TcpClientPacketTypes.SESSION_REQUEST.ordinal());
				buf.put(sidl);
				buf.put(sid);
				buf.put(cnl);
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
	
	private static class LIST_REQUEST_Parser implements PacketParser {
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
				buf.put((byte) TcpReplicationTypes.LIST_REQUEST.ordinal());
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
	
	private static class LIST_UPDATES_Parser implements PacketParser {
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
				buf.put((byte) TcpReplicationTypes.LIST_REQUEST.ordinal());
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
				parser = new LIST_REQUEST_Parser();
				break;
			case LIST_UPDATES:
				parser = new LIST_UPDATES_Parser();
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
				parser = new SERVER_ID_Parser();
				break;
			case SESSION_CHECK:
				parser = new SESSION_CHECK_Parser();
				break;
			case SESSION_REQUEST:
				parser = new SESSION_REQUEST_Parser();
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
