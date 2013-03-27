package ru.kt15.finomen.sessionServer;

import java.util.UUID;

public class Options {
	static short udpPort = 9999;
	static short clientUdpPort = 9999; // detect
	static short tcpPort = 9999;
	static UUID serverUUID = UUID
			.fromString("188f502e-e4f6-4030-8797-112dc7a0d11e");
	static long sessionTTL = 10 * 1000;
	static long delayedCheckTTL = 5 * 1000;
	static long replicationRoutineLoop = 50;
}
