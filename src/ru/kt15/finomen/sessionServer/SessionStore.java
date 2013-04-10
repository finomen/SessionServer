package ru.kt15.finomen.sessionServer;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.UUID;

import ru.kt15.net.labs.sessions.ServerReplication;

public class SessionStore{
	private final HashMap<UUID, Session> sessions = new HashMap<>();
	private final PriorityQueue<Session> expireQueue = new PriorityQueue<>();
	private final ClientStore clientStore;
	
	public SessionStore(ClientStore clientStore) {
		this.clientStore = clientStore;
	}
	
	private void removeGarbage() {
		while (!expireQueue.isEmpty() && expireQueue.peek().validUntil.before(new Date())) {
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
	
	public Collection<Session> getSessions() {
		removeGarbage();
		return sessions.values();
	}

	public void update(ServerReplication.Session session) {
		UUID id = UUID.fromString(session.getKey().getSessionId());
		if (session.hasRemove() && session.getRemove()) 
		{
			sessions.remove(id);
			return;
		}
		
		if (!sessions.containsKey(id)) {
			Session s = new Session(id, clientStore.getClient(session.getSessionSource()),
					clientStore.getClient(session.getSessionDest()),
					new Date(session.getValidUntil()),
					session.getKey().getServerId());
			sessions.put(id, s);
		}
		
		if (session.hasValidUntil()) {
			sessions.get(id).validUntil = new Date(session.getValidUntil());
		}
	}
	
	public Session getSession(UUID sessionId, String serverId) {
		Session s = sessions.get(sessionId);
		if (s != null) {
			if (!s.serverId.equals(serverId)) {
				s = null;
			}
		}
		
		return s;
	}
}
