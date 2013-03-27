package ru.kt15.finomen.sessionServer;

class Client {
	final String host;
	String computerName = "";
	boolean valid = false;

	Client(String host) {
		this.host = host;
	}
	
	@Override
	public boolean equals(Object o) {
		if (o instanceof Client) {
			Client c = (Client)o;
			return c.host.equals(host);
		}
		
		return false;
	}
}