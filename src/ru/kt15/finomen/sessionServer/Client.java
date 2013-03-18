package ru.kt15.finomen.sessionServer;

class Client {
	final String host;
	String computerName = "";
	boolean valid = false;

	Client(String host) {
		this.host = host;
	}
}