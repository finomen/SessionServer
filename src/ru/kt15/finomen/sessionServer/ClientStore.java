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

	public Collection<Client> getClients() {
		return clients.values();
	}

	public void update(Host host) {
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

		// TODO: remove

		if (add) {
			clients.put(address, client);
			addClient(client);
		}
	}

	private void addClient(Client client) {
		for (ClientUpdateListener listener : listeners) {
			listener.addClient(client.host, client.computerName, client.valid);
		}
	}

	private void changeName(Client client) {
		for (ClientUpdateListener listener : listeners) {
			listener.changeName(client.host, client.computerName);
		}
	}

	private void changeValid(Client client) {
		for (ClientUpdateListener listener : listeners) {
			listener.changeValid(client.host, client.valid);
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
		}
		return clients.get(host);
	}
}
