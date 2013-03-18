package ru.kt15.finomen.sessionServer;

import ru.kt15.finomen.StreamConnection;

public interface AdminHandler {
	void setAdminConnection(StreamConnection conn);

	void setUdpLogging(boolean active);

	void setTcpLogging(boolean active);

	void setAdminLogging(boolean active);
}
