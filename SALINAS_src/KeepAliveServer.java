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

public class KeepAliveServer implements Runnable{
	
	private static final long timeout = 10000;
	private static boolean intInSleep = false;

	@Override
	/**
	*	This method implements the server's KeepAlive. Every time an UDP packet is sent to the multicast group, the KeepAlive waits 
	*	a response on a separate UDP socket, when a response is received, the KeepAlive
	*	extracts the UID and checks whether the user's session is expired (expired token, server crash or user not logged):
	*		1. if not, the user is marked as "ONLINE"
	*		2. else, no operation is done
	*	If the KeepAlive thread is interrupted it means that something went wrong, so the execution ends.
	*/
	public void run(){
		try(MulticastSocket mc = new MulticastSocket(SimpleSocialServer.MCPORT);
				DatagramSocket udp = new DatagramSocket(SimpleSocialServer.UDPPORT, InetAddress.getByName(SimpleSocialServer.UDPIP));){
			InetAddress multicastGroup = InetAddress.getByName(SimpleSocialServer.MCIP);
			mc.setTimeToLive(1);
			mc.setLoopbackMode(false);
			mc.setReuseAddress(true);
			udp.setSoTimeout(10);
			while(!Thread.interrupted() && !intInSleep){
				DatagramPacket packet = new DatagramPacket(ByteBuffer.allocate(Integer.SIZE).putInt(SimpleSocialServer.UDPPORT).array(), Integer.SIZE, multicastGroup, SimpleSocialServer.MCPORT);
				mc.send(packet);
				long time = System.currentTimeMillis()/1000;
				int size = SimpleSocialServer.users.size();
				if(size > 0){
					allOff();
					while((System.currentTimeMillis()/1000-time) <= 10){
						byte[] buff = new byte[Long.SIZE];
						DatagramPacket resp = new DatagramPacket(buff, Long.SIZE);
						try{
							udp.receive(resp);
							DataInputStream in = new DataInputStream(new ByteArrayInputStream(resp.getData()));
							ServerUser u = TCPServerOp.findUser(in.readLong());
							if( u != null)
								if(!u.getLogged())
									u.setOnline(true);
						}catch(SocketTimeoutException e){}
					}
					SimpleSocialServer.lock.lock();
					int online = 0;
					for(ServerUser u : SimpleSocialServer.users)
						if(u.getOnline())
							online++;
					System.out.println("KeepAliveThread - "+online+" users online");
					SimpleSocialServer.lock.unlock();
				}
				try {
					Thread.sleep(timeout);
				} catch (InterruptedException e) {
					intInSleep = true;
				}
			}
		} catch (IOException e) {
			System.out.println("KeepAliveThread - Some error occurred: "+e.getMessage());
			SimpleSocialServer.term = true;
		}
		return;
	}
	
	/**
	 * This method sets offline all registered users
	 */
	private static void allOff(){
		SimpleSocialServer.lock.lock();
		for(ServerUser u : SimpleSocialServer.users)
			u.setOnline(false);
		SimpleSocialServer.lock.unlock();
	}

}
