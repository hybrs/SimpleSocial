/**
* @author Mario Leonardo Salians
*/
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ClientTCPAccepter implements Runnable {
	private final int timeout = 1000;
	
	/**
	 * This method waits on a tcp Socket for requests and delegates the communication
	 * to an handler that accept the request and save it locally
	 */
	@Override
	public void run() {
		int port = 2003, i = 0; boolean bound = false;
		ExecutorService ex = Executors.newCachedThreadPool();
		while(!bound)
			try{
				SimpleSocialClient.tcpAccept = new ServerSocket(port+i);
				bound = true;
			}catch (IOException e) {
				i++;
			}
		SimpleSocialClient.startLock.lock();
		SimpleSocialClient.cond.signal();
		SimpleSocialClient.startLock.unlock();
		try {
			SimpleSocialClient.tcpAccept.setSoTimeout(timeout);
		} catch (SocketException e) {
			SimpleSocialClient.term = true;
			return;
		}
		while(!Thread.interrupted()){
			try{
				Socket request = SimpleSocialClient.tcpAccept.accept();
				ex.submit(new ClientTCPAccepterHandler(request));
			}catch(IOException e){}
		}
		if(ex != null){
			ex.shutdownNow();
			try {
				ex.awaitTermination(1, TimeUnit.SECONDS);
			} catch (InterruptedException e) {}
		}
		return;
	}

}
