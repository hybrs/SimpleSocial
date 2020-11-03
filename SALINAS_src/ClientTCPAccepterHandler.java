/**
* @author Mario Leonardo Salians
*/
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketException;

public class ClientTCPAccepterHandler implements Runnable {

	private Socket client;
	private final int timeout = 10000;
	private static final String reqFile = "./userFiles/"+SimpleSocialClient.user.getUserName()+"Reqs.dat";
	
	/**
	 * The class constructor
	 * @param c the socket where the handler communicates with the server
	 */
	public ClientTCPAccepterHandler(Socket c) {
		this.client = c;
	}
	
	/**
	 * This method accept the request sent from SimpleSocialServer and save it locally
	 */
	@Override
	public void run() {
		try {
			client.setTcpNoDelay(true);
			client.setSoTimeout(timeout);
		} catch (SocketException e) {
			System.out.println("AccepterHandler - Some error occurred while setting up the socket: "+e.getMessage());
		}
		SimpleSocialClient.user.getReqLock().lock();
		try(	BufferedWriter w = new BufferedWriter(new OutputStreamWriter(client.getOutputStream()));
				BufferedReader r = new BufferedReader(new InputStreamReader(client.getInputStream())); 
				BufferedWriter fileWriter = new BufferedWriter(new FileWriter(reqFile, true))){
			r.readLine();
			w.write("ACK\r\n");
			w.flush();
			String name = r.readLine();
			w.write("ACK\r\n");
			w.flush();
			SimpleSocialClient.user.addRequest(name);
			fileWriter.write(name+"\r\n");
			fileWriter.flush();
			SimpleSocialClient.user.getReqLock().unlock();
		} catch (IOException e) {
			System.out.println("AccepterHandler - Some error occurred in retriving IOStream from socket: "+e.getMessage());
		}finally{
			if(SimpleSocialClient.user.getReqLock().isHeldByCurrentThread())
				SimpleSocialClient.user.getReqLock().unlock();
			try {
				client.close();
			} catch (IOException e) {}
		}
		
	}

}
