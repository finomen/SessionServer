package ru.kt15.finomen.sessionServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ru.kt15.finomen.DatagramConnection;
import ru.kt15.finomen.IOService;
import ru.kt15.finomen.ServerConnection;

public class Main {

	/**
	 * @param args
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static void main(String[] args) throws IOException,
			InterruptedException {
		System.out.print("Starting server core...");
		IOService ioService = new IOService();
		System.out.println("OK");
		System.out.print("Initializing client storage...");
		ClientStore clientStore = new ClientStore();
		System.out.println("OK");
		System.out.println("Initializing discover service...");
		DiscoverHandler discoverHandler = new DiscoverHandler();
		List<DatagramConnection> udpConnections = new ArrayList<>();
		for (NetworkInterface ifc : Collections.list(NetworkInterface
				.getNetworkInterfaces())) {
			if (ifc.isUp()) {
				for (InetAddress addr : Collections
						.list(ifc.getInetAddresses())) {
					System.out.println(" * " + addr.getHostAddress());
					DatagramConnection conn = new DatagramConnection(ioService,
							true, new InetSocketAddress(addr, Options.udpPort));
					conn.addRecvListener(discoverHandler);
					udpConnections.add(conn);
				}
			}
		}

		discoverHandler.addListener(clientStore);
		System.out.println("Done");
		System.out.println("Initializing session storage...");
		// TODO:
		System.out.println("DISABLED");
		System.out.println("Initializing replication controller...");
		ReplicationPacketListener replicationListener = new ReplicationPacketListener(
				clientStore);
		System.out.println("OK");
		System.out.println("Initializing administartion controller...");
		AdminController adminController = new AdminController(clientStore,
				ioService);
		for (DatagramConnection dg : udpConnections) {
			adminController.addUdpConnection(dg);
		}
		System.out.println("OK");
		System.out.println("Initializing client connection controller...");
		ClientPacketListener clientListener = new ClientPacketListener(
				replicationListener, adminController);
		System.out.println("OK");
		System.out.println("Start listening...");
		ServerConnection serverConnection = new ServerConnection(
				new ClientProtocolDefinition(), ioService, Options.tcpPort);
		adminController.addTcpConnection(serverConnection);
		serverConnection.addRecvListener(clientListener);
		System.out.println("OK");
	}

}
