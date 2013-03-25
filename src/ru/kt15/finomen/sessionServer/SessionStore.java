package ru.kt15.finomen.sessionServer;

import java.util.Date;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.UUID;

public class SessionStore{
	private final HashMap<UUID, Session> sessions = new HashMap<>();
	private final PriorityQueue<Session> expireQueue = new PriorityQueue<>();
	
	public SessionStore() {
	}
	
	private void removeGarbage() {
		while (expireQueue.peek().validUntil.after(new Date())) {
			Session s = expireQueue.poll();
			sessions.remove(s.id);
		}
	}
	
	public boolean validateSession(UUID id, Client client) {
		removeGarbage();
		if (sessions.containsKey(id)) {
			return sessions.get(id).isValid(client);
		}
		
		return false;
	}
	
	public Session requestSession(Client source, Client dest) {
		removeGarbage();
		if (source.valid && dest.valid) {
			Session s = new Session(source, dest);
			sessions.put(s.id, s);
			expireQueue.add(s);
			return s;
		}
		
		return null;
	}
}
