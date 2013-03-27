package ru.kt15.finomen.sessionServer;

import ru.kt15.finomen.BasicStreamProtocolDefinition;
import ru.kt15.net.labs.sessions.TcpClientPacketTypes;
import ru.kt15.net.labs.sessions.TcpReplicationTypes;

public class ClientProtocolDefinition extends BasicStreamProtocolDefinition {
	public ClientProtocolDefinition() {
		addPacket((byte)TcpClientPacketTypes.SESSION_REQUEST.ordinal(), TokenTypes.STRING8, TokenTypes.STRING8);
		addPacket((byte)TcpClientPacketTypes.SESSION_CHECK.ordinal(), TokenTypes.STRING8, TokenTypes.STRING8);
		addPacket((byte)TcpClientPacketTypes.SERVER_ID.ordinal(), TokenTypes.STRING8);
		addPacket((byte)TcpClientPacketTypes.ADMIN_CS.ordinal(), TokenTypes.STRING16);
		addPacket((byte)(TcpReplicationTypes.LIST_REQUEST.ordinal() | 0xF0), TokenTypes.STRING16);
		addPacket((byte)(TcpReplicationTypes.LIST_UPDATES.ordinal() | 0xF0), TokenTypes.STRING16);
	}
}
