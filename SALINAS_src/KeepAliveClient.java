/**
* @author Mario Leonardo Salians
*/
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;

public class KeepAliveClient implements Runnable {
	/**
	 * This method implements the client's KeepAlive that waits for the server's KeepAlive
	 * message and sends back a response, containing the user UID, to a separate UDP socket 
	 */
	@Override
	public void run(){
		try(MulticastSocket client = new MulticastSocket(SimpleSocialClient.MCPORT);
				DatagramSocket udp = new DatagramSocket();){
			InetAddress multicastGroup = InetAddress.getByName(SimpleSocialClient.MCIP);
			client.setSoTimeout(1000);
			client.joinGroup(multicastGroup);
			while(!Thread.interrupted()){
				DatagramPacket packet = new DatagramPacket(new byte[Integer.SIZE], Integer.SIZE);
				try{
					client.receive(packet);
					DataInputStream in = new DataInputStream(new ByteArrayInputStream(packet.getData()));
					udp.send(new DatagramPacket(ByteBuffer.allocate(Long.SIZE).putLong(SimpleSocialClient.user.getUID()).array(), Long.SIZE, InetAddress.getByName("localhost"), in.readInt()));
				}catch(SocketTimeoutException e){}
			}
			client.leaveGroup(multicastGroup);
		} catch (IOException e) {
			System.out.println("Something went wrong in KeepAlive: "+e.getMessage());
			SimpleSocialClient.term = true;
			return;
		}
	}

}
