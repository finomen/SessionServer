package ru.kt15.finomen.sessionServer;

public interface ClientUpdateListener {
	void addClient(String address, String cname, boolean valid);

	void changeName(String address, String name);

	void changeValid(String address, boolean valid);
}
