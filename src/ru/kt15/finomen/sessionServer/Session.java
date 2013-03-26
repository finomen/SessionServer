package ru.kt15.finomen.sessionServer;

import java.util.Date;
import java.util.UUID;

public class Session implements Comparable<Session> {
	final UUID id;
	final String serverId;
	final Client source;
	final Client dest;
	Date validUntil;

	Session(Client source, Client dest) {
		this.id = UUID.randomUUID();
		this.source = source;
		this.dest = dest;
		serverId = Options.serverUUID.toString();
		validUntil = new Date(new Date().getTime() + Options.sessionTTL);
	}
	
	Session(UUID id, Client source, Client dest, Date validUntil, String serverId) {
		this.id = id;
		this.source = source;
		this.dest = dest;
		this.validUntil = validUntil;
		this.serverId = serverId;	
	}

	boolean isValid(Client realDest) {
		return validUntil.before(new Date()) && source.valid && dest.valid
				&& realDest.host.equals(dest.host)
				&& dest.computerName.equals(realDest.computerName);
	}

	@Override
	public int compareTo(Session s) {
		return validUntil.compareTo(s.validUntil);
	}
	
}
