package ru.kt15.finomen.sessionServer;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import ru.kt15.net.labs.sessions.ServerReplication.Host;

public class ClientStore implements DiscoverListener {
	private final Set<ClientUpdateListener> listeners = new HashSet<>();
	private final Map<String, Client> clients = new HashMap<>();

	public void addListener(ClientUpdateListener listener) {
		listeners.add(listener);
	}
	
	public Collection<Client> getClients() {
		return clients.values();
	}

	public void update(Host host) {
		if (host.hasRemove() && host.getRemove()) {
			clients.remove(host.getKey().getAddress());
		}
		
		String address = host.getKey().getAddress();
		Client client = null;
		boolean add = false;
		if (clients.containsKey(address)) {
			client = clients.get(address);
		} else {
			client = new Client(address);
			add = true;
		}

		if (host.hasName() && !host.getName().equals(client.computerName)) {
			client.computerName = host.getName();
			if (!add) {
				changeName(client);
			}
		}

		if (host.hasValid() && host.getValid() != client.valid) {
			client.valid = host.getValid();
			if (!add) {
				changeValid(client);
			}
		}

		if (add) {
			clients.put(address, client);
			addClient(client);
		}
	}
	
	public void setValid(Client client, boolean valid) {
		if (client.valid == valid) {
			return;
		}
		
		client.valid = valid;
		changeValid(client);
	}
	
	public void setName(Client client, String name) {
		if (client.computerName.equals(name)) {
			return;
		}
		client.computerName = name;
		changeName(client);
	}

	private void addClient(Client client) {
		for (ClientUpdateListener listener : listeners) {
			listener.addClient(client);
		}
	}

	private void changeName(Client client) {
		for (ClientUpdateListener listener : listeners) {
			listener.changeName(client);
		}
	}

	private void changeValid(Client client) {
		for (ClientUpdateListener listener : listeners) {
			listener.changeValid(client);
		}
	}

	@Override
	public void discoverRequestRecvd(InetSocketAddress host) {
		String address = host.getAddress().getHostAddress();
		if (!clients.containsKey(address)) {
			Client client = new Client(address);
			clients.put(address, client);
			addClient(client);
		}
	}

	@Override
	public void discoverRecvd(String host, int port) {
	}
	
	public Client getClient(String host) {
		if (!clients.containsKey(host)) {
			clients.put(host,  new Client(host));
			addClient(clients.get(host));
		}
		return clients.get(host);
	}
}
