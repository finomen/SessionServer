package ru.kt15.finomen.sessionServer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import ru.kt15.finomen.IOService;
import ru.kt15.finomen.PacketConnection;
import ru.kt15.finomen.PacketListener;
import ru.kt15.finomen.StreamConnection;
import ru.kt15.net.labs.sessions.ServerControl;
import ru.kt15.net.labs.sessions.ServerControl.Packet;
import ru.kt15.net.labs.sessions.ServerControl.SC;
import ru.kt15.net.labs.sessions.TCPServerPacketTypes;

import com.google.protobuf.ByteString;

public class AdminController implements AdminHandler, Runnable {
	private boolean logTcp = false;
	private boolean logUdp = false;
	private boolean logAdmin = false;
	private final IOService ioService;

	private SC.Builder currentPacketLog = SC.newBuilder();
	private final Object packetLock = new Object();

	private final Thread worker;

	private StreamConnection adminConnection;
	private final ClientStore clientStore;

	private PacketListener udpHandler = new PacketListener() {
		@Override
		public void handlePacket(PacketConnection conn,
				InetSocketAddress source, InetSocketAddress dest, byte[] packet) {
			if (logUdp) {
				appendUdpPacketLog(Packet.newBuilder()
						.setData(ByteString.copyFrom(packet))
						.setDestHost(dest.getAddress().getHostAddress())
						.setDestPort(dest.getPort())
						.setSourceHost(source.getAddress().getHostAddress())
						.setSourcePort(source.getPort()).build());
			}
		}
	};

	private PacketListener tcpHandler = new PacketListener() {
		@Override
		public void handlePacket(PacketConnection conn,
				InetSocketAddress source, InetSocketAddress dest, byte[] packet) {
			if (conn.equals(adminConnection) && !logAdmin) {
				return;
			}

			if (logTcp) {
				appendTcpPacketLog(Packet.newBuilder()
						.setData(ByteString.copyFrom(packet))
						.setDestHost(dest.getAddress().getHostAddress())
						.setDestPort(dest.getPort())
						.setSourceHost(source.getAddress().getHostAddress())
						.setSourcePort(source.getPort()).build());
			}
		}
	};

	public AdminController(ClientStore clientStore, IOService ioService) {
		this.clientStore = clientStore;
		this.ioService = ioService;
		worker = new Thread(this);
		worker.start();
	}

	private void appendUdpPacketLog(Packet packet) {
		synchronized (packetLock) {
			currentPacketLog.addUdpPackets(packet);
		}
	}

	private void appendTcpPacketLog(Packet packet) {
		synchronized (packetLock) {
			currentPacketLog.addTcpPackets(packet);
		}
	}

	@Override
	public void setTcpLogging(boolean active) {
		logTcp = active;
	}

	@Override
	public void setUdpLogging(boolean active) {
		logUdp = active;

	}

	@Override
	public void setAdminLogging(boolean active) {
		logAdmin = active;

	}

	@Override
	public void run() {
		while (!Thread.interrupted()) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				return;
			}

			synchronized (packetLock) {
				currentPacketLog.setLastRoutineTime(ioService
						.getLastCycleTime());
				currentPacketLog.setAverageRoutimeTime(ioService
						.getAverageCycleTime());

				for (Client client : clientStore.getClients()) {
					ServerControl.Host host = ServerControl.Host.newBuilder()
							.setAddress(client.host)
							.setName(client.computerName)
							.setValid(client.valid).build();
					currentPacketLog.addHosts(host);
				}

				SC sc = currentPacketLog.build();
				currentPacketLog = SC.newBuilder();

				ByteBuffer buf = ByteBuffer
						.allocate(sc.getSerializedSize() + 3);
				buf.put((byte) TCPServerPacketTypes.ADMIN_SC.ordinal());
				buf.putShort((short) sc.getSerializedSize());
				buf.put(sc.toByteArray());
				buf.flip();
				byte[] pack = new byte[buf.remaining()];
				buf.get(pack);

				if (adminConnection != null) {
					if (!adminConnection.Send(pack)) {
						adminConnection = null;
					}
				}
			}
		}
	}

	public void stop() throws InterruptedException {
		worker.interrupt();
		worker.join();
	}

	@Override
	public void setAdminConnection(StreamConnection conn) {
		adminConnection = conn;
	}

	public void addTcpConnection(PacketConnection conn) {
		conn.addRecvListener(tcpHandler);
		conn.addSendListener(tcpHandler);
	}

	public void addUdpConnection(PacketConnection conn) {
		conn.addRecvListener(udpHandler);
		conn.addSendListener(udpHandler);
	}
}
