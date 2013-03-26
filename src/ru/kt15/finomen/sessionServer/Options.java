package ru.kt15.finomen.sessionServer;

import java.util.UUID;

public class Options {
	static short udpPort = 9999;
	static short clientUdpPort = -1; // detect
	static short tcpPort = 9998;
	static UUID serverUUID = UUID
			.fromString("188f502e-e4f6-4030-8797-112dc7a0d11e");
	static long sessionTTL = 10;
}
