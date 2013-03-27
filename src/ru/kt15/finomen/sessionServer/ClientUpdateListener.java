package ru.kt15.finomen.sessionServer;

public interface ClientUpdateListener {
	void addClient(Client client);

	void changeName(Client client);

	void changeValid(Client client);
}
