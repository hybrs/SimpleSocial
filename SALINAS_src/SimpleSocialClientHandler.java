/**
* @author Mario Leonardo Salians
*/
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketException;

public class SimpleSocialClientHandler implements Runnable {

	private Socket client;
	private TCPServerOp tcpOp;
	private final int timeout = 150000;
	
	/**
	 * The class constructor.
	 * @param op the serverOp object to which delegate the request's processing
	 * @param client the socket on which the client is connected
	 */
	public SimpleSocialClientHandler(TCPServerOp op, Socket client) {
		this.client = client;
		this.tcpOp = op;
	}
	
	/**
	 * This method accepts and process a single request, then closes the connection.
	 */
	@Override
	public void run() {
		int op = 0;
		try(	BufferedWriter userWriter = new BufferedWriter(new OutputStreamWriter(client.getOutputStream()));
				BufferedReader userReader = new BufferedReader(new InputStreamReader(client.getInputStream()));){
			this.client.setSoTimeout(timeout);
			this.client.setTcpNoDelay(true);
			//Handshake phase
			TCPServerOp.send("go", userWriter);
			op = Integer.parseInt(userReader.readLine());
			TCPServerOp.send("ACK", userWriter);
			selectOp(tcpOp, op, userWriter, userReader);
			TCPServerOp.send("end", userWriter);
			client.close();
		}catch(SocketException e){
			System.out.println("ClientHandler - Idle Client. Closing connection.");
		}catch(IOException e){
			System.out.println("ClientHandler - Something went wrong: "+e.getMessage());
		}catch (Exception e) {
			e.printStackTrace();
		}finally{
			try {
				client.close();
			} catch (IOException e) {
				System.out.println("ClientHandler - Something went wrong: "+e.getMessage());
				
			}
		}
	}
	
	/**
	 * This method delegates the request processing to the specific functions
	 * @param tcpOp the serverOp object to which delegate the request's processing
	 * @param op the int that represent the requested operation
	 * @param w the client socket OutputStream
	 * @param r the client socket InputStream
	 * @throws IOException
	 */
	private void selectOp(TCPServerOp tcpOp, int op, BufferedWriter w, BufferedReader r) throws IOException{
		switch (op) {
		case 1:
			tcpOp.register(w, r);
			break;
		case 2:
			tcpOp.login(w, r);
			break;
		case 3:
			tcpOp.request(w, r);
			break;
		case 4:
			tcpOp.confirmRequest(w, r);
			break;
		case 6:
			tcpOp.search(w, r);
			break;
		case 7:
			tcpOp.listFriends(w, r);
			break;
		case 8:
			tcpOp.post(w, r);
			break;
		case 9:
			tcpOp.logout(w, r);
			break;
		case 10:
			tcpOp.follow(w, r);
			break;	
		default:
			break;
		}
	}

}
