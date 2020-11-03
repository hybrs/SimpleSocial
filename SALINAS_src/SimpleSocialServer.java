/**
* @author Mario Leonardo Salians
*/
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

public class SimpleSocialServer {
	
	static int TCPPORT;
	static int RMISERVERPORT;
	static int MCPORT;
	static int UDPPORT;
	static String UDPIP;
	static String MCIP = null;
	static String TCPIP = null;
	static ReentrantLock lock = new ReentrantLock();
	static Vector<ServerUser> users = null;
	static boolean term = false;

	/**
	 * This is the SimplesocialServer main method. It restores the most recently saved server status from file, 
	 * initializes RMI and KeepAlive, starts the backup daemon and waits for client to come.
	 * @param args no parameters
	 */
	public static void main(String[] args) {
		ServerStateUpdater stateUp = null;
		Thread update = null;
		if(getConfigFromFile() != -1){
			try {
				users = ServerStateUpdater.readUsers();
			} catch (IOException e2) {
				System.out.println("Internal Server error - Something went wrong in initializing users list: "+e2.getMessage());
				return;
			}
			ExecutorService ex = Executors.newCachedThreadPool();
			Thread keepAliveThread = null;
			RMIServerOp manager = new RMIServer();
			try(ServerSocket server = new ServerSocket()){
				RMIServerOp managerStub = (RMIServerOp) UnicastRemoteObject.exportObject(manager, 0);
				Registry registry = LocateRegistry.createRegistry(RMISERVERPORT);
				registry.rebind(RMIServerOp.NAME, managerStub);
				System.out.println("RMIServer set-up finished");
				try {
					stateUp = new ServerStateUpdater();
				} catch (IOException e2) {
					System.out.println("Internal Server error - Something went wrong: "+e2.getMessage());
					return;
				}
				update = new Thread(stateUp);
				update.start();
				TCPServerOp tcpOp = new TCPServerOp();
				InetSocketAddress add = new InetSocketAddress(InetAddress.getByName(TCPIP), TCPPORT);
				server.bind(add);
				server.setSoTimeout(1000);
				KeepAliveServer kaHandler = new KeepAliveServer();
				keepAliveThread = new Thread(kaHandler);
				keepAliveThread.start();
				while(!term){
					try{
						Socket client = server.accept();
						ex.submit(new SimpleSocialClientHandler(tcpOp, client));
					}catch(SocketTimeoutException e){
					}catch (IOException e) {
						System.out.println("Server error: "+e.getMessage());
						return;
					}
				}
				System.out.println("Internal Server Error - Some error occurred in KeepAlive or Updater.");
			}catch(RemoteException e){
				System.out.println("Internal Server error - Error setting up the RMI server: "+e.getMessage());
			}catch(UnknownHostException e){
				System.out.println("Internal Server error: "+e.getMessage());
			}catch (IOException e) {
				System.out.println("Internal Server error: "+e.getMessage());
			}finally{
				System.out.println("Closing SimplesocialServer, please wait");
				if(keepAliveThread != null){
					keepAliveThread.interrupt();
					try {
						keepAliveThread.join();
					} catch (InterruptedException e) {}
				}
				if(update != null){
					update.interrupt();
					try {
						update.join();
					} catch (InterruptedException e) {}
				}
				if(ex!=null) ex.shutdownNow();
				try {
					UnicastRemoteObject.unexportObject(manager, true);
				} catch (NoSuchObjectException e) {
					System.out.println("Some error occurred while unexporting serverStub: "+e.getMessage());
				}
			}
		}
		System.out.println("Closing SimpleSocialServer.");
	}
	
	/**
	 * This function reads all the configuration options from the config file that is placed in the execution directory
	 * @return 0 if the initialization is successfully completed, -1 if some error occurs
	 */
	private static int getConfigFromFile(){
		int ret = -1;
		try(BufferedReader r = new BufferedReader(new FileReader("./configFile"))){
			TCPIP = new String(r.readLine().split(":")[1]);
			TCPPORT = Integer.parseInt((r.readLine().split(":")[1]));
			MCPORT = Integer.parseInt((r.readLine().split(":")[1]));
			MCIP = new String(r.readLine().split(":")[1]);
			RMISERVERPORT = Integer.parseInt((r.readLine().split(":")[1]));
			UDPPORT = Integer.parseInt((r.readLine().split(":")[1]));
			UDPIP = new String(r.readLine().split(":")[1]);
			ret = 0;
		} catch (FileNotFoundException e) {
			System.out.println("Config file not found. Please put it in the execution directory then restart.");
		} catch(NumberFormatException e){
			System.out.println("Config file has an unsupported format. Please check it and restart.");
		} catch (IOException e) {
			System.out.println("Some error occurred while reading from config file: "+e.getMessage()+"\nPlease restart.");
		}
		return ret;
	}
}

