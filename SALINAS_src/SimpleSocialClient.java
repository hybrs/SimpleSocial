/**
* @author Mario Leonardo Salians
*/
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Objects;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class SimpleSocialClient {
	static int TCPSERVERPORT;
	static int RMISERVERPORT;
	static int MCPORT;
	static String MCIP = null;
	static String TCPIP = null;
	static ReentrantLock postFileLock = new ReentrantLock();
	static User user = null;
	static ServerSocket tcpAccept = null;
	static ReentrantLock startLock = new ReentrantLock();
	static Condition cond = startLock.newCondition();
	static boolean term = false;
	
	/**
	 * This is the main method of this class. This method initializes RMI, tcpAccepter and KeepAlive
	 *  and receives and processes user's requests
	 *  @see KeepAliveClient, ClientTCPAccepter, ClientTCPAccepterHandler
	 */
	public static void main(String[] args) {
		Thread KeepAliveThread = null, tcpAcceptThread = null; String op = null;
		TCPClientOp tcpClient = null; ClientTCPAccepter a = null; RMIClientOP userStub = null, u = new RMIClient();
		if(getConfigFromFile() != -1){
			try(BufferedReader r = new BufferedReader(new InputStreamReader(System.in))){
				tcpClient = new TCPClientOp();
				a = new ClientTCPAccepter();
				tcpAcceptThread = new Thread(a);
				tcpAcceptThread.start();
				userStub = (RMIClientOP) UnicastRemoteObject.exportObject(u, 0);
				Registry reg = LocateRegistry.getRegistry("localhost", RMISERVERPORT);
				RMIServerOp followerManager = (RMIServerOp) reg.lookup(RMIServerOp.NAME);
				user = new User("",-1,-1);
				KeepAliveClient kaHandler = new KeepAliveClient();
				KeepAliveThread = new Thread(kaHandler);
				startLock.lock();
				while(SimpleSocialClient.tcpAccept == null)
					try {
						cond.await();
					} catch (InterruptedException e) {}
				startLock.unlock();
				printFirstOp();
				int retVal = 0;
				user.setFollMan(followerManager);
				while(retVal != -2 && retVal != -3 && !Objects.equals((op = r.readLine()).toLowerCase(), "exit")){
					if((retVal = selectFirstOp(op.toLowerCase().trim(), KeepAliveThread, r, tcpClient, userStub)) == -1) System.out.println("Wrong command, please try again.");
					if(retVal != -3 && retVal != -2) printFirstOp();
				}
				if(retVal != -2 && !Objects.equals(op, "exit")){
					printOp(user);
					while(!term && retVal != -2 && !Objects.equals((op = r.readLine()).toLowerCase(), "logout")){
						if((retVal = selectOp(op.toLowerCase().trim(), KeepAliveThread, r, tcpClient)) == -3) System.out.println("Wrong command, please try again.");
						if(!term && retVal != -2) printOp(user);
					}
				}
				tcpClient.logout();
			}catch (UnknownHostException e) {
				System.out.println("Some error occurred: "+e.getMessage());
			}catch (IOException e) {
				System.out.println("Some error occurred: Connection refused.");
			}catch(NotBoundException e){
				System.out.println("Remote object not found");
				return;
			}catch(Exception e){
			}finally{
				System.out.println("Closing SimpleSocialClient, please wait.");
				try{
					if(tcpAcceptThread != null){
						tcpAcceptThread.interrupt();
						tcpAcceptThread.join();
					}
					if(KeepAliveThread != null){
						KeepAliveThread.interrupt();
						KeepAliveThread.join();
					}
				}catch(InterruptedException e){}
				try{
					UnicastRemoteObject.unexportObject(u, true);
				}catch(NoSuchObjectException e){
					System.out.println("Cannot unexport remote object");
				}
				
			}
		}
		System.out.println("Leaving SimpleSocial.");
	}
	
	/**
	 * This function is used to select between the non-logged options: register, login and exit.
	 * @param op the requested operation
	 * @param kaThread the KeepAlive thread
	 * @param r System.in
	 * @param c the clientOp object to which delegate the request's processing
	 * @param userStub the RMI userStub
	 * @return -1 if the used typed a wrong request, -2 in case of an IOException/Error, 0 registration completed, -3 login completed
	 * @throws IOException
	 */
	private static int selectFirstOp(String op, Thread kaThread, BufferedReader r, TCPClientOp c, RMIClientOP userStub) throws IOException{
		if(Objects.equals(op, "register"))
			return c.register(r);
		else if(Objects.equals(op, "login"))
			if((user = c.login(r, kaThread)).getUID() != -1){
				try(BufferedReader rFile = new BufferedReader(new InputStreamReader(new FileInputStream("./userFiles/"+user.getUserName()+"Reqs.dat"), "UTF-8"))){
					String nameReq = null;
					while((nameReq = rFile.readLine())!= null){
						SimpleSocialClient.user.getReqLock().lock();
						SimpleSocialClient.user.addRequest(nameReq);
						SimpleSocialClient.user.getReqLock().unlock();
					}
				}catch(FileNotFoundException e){}
				user.setUserStub(userStub);
				user.getFollowMan().login(user.getUserName(), userStub, tcpAccept.getLocalPort());
				user.setPort(tcpAccept.getLocalPort());
				return -3;
			}
			else
				return -2;
		else return -1;
	}
	
	/**
	 * This function is used to select between the logged options.
	 * @param op the requested operation
	 * @param kaThread the KeepAlive thread
	 * @param r System.in
	 * @param c the clientOp object to which delegate the request's processing
	 * @return -3 if the used typed a wrong request, -1 in case of an IOException/Error, 0 requested operation completed
	 * @throws IOException
	 */
	private static int selectOp(String op, Thread kaThread, BufferedReader r, TCPClientOp c) throws IOException{
		if(Objects.equals(op, "request")) return c.request(r);
		else if(Objects.equals(op, "confirm")) return c.confirmRequest(r);
		else if(Objects.equals(op, "search")) return c.search(r);
		else if(Objects.equals(op, "post")) return c.post(r);
		else if(Objects.equals(op, "listfriends")) return c.listFriends(r);
		else if(Objects.equals(op, "listrequests")) return c.listRequest();
		else if(Objects.equals(op, "follow")) return c.follow(r);
		else if(Objects.equals(op, "listposts")){
			user.readPosts();
			return 0;
		}
		else if(Objects.equals(op, "listfollowed")){
			user.getFollowedLock().lock();
			int size = user.followedSize();
			System.out.println("You're following "+size+" people:");
			for(int i = 0; i< size; i++)
				System.out.println((i+1)+". "+user.getFollowed(i));
			user.getFollowedLock().unlock();
			return 0;
		}
		else return -3;
	}
	
	/**
	 * This method prints all non-logged options 
	 */
	private static void printFirstOp(){
		System.out.println("Welcome to SimpleSocial!\n"
				+ "Type:\n"
				+ "- register\n"
				+ "- login\n"
				+ "- exit");
	}
	
	/**
	 * This method prints all logged options 
	 * @param u the currently logged user
	 */
	private static void printOp(User u){
		System.out.println("Hello "+u.getUserName()+", type:\n"
				    + "- request\n"
					+ "- confirm\n"
					+ "- search\n"
					+ "- post\n"
					+ "- follow\n"
					+ "- listPosts\n"
					+ "- listFriends\n"
					+ "- listRequests\n"
					+ "- listFollowed\n"
					+ "- logout");
	}
	
	/**
	 * This function reads all the configuration options from the config file that is placed in the execution directory
	 * @return 0 if the initialization is successfully completed, -1 if some error occurs
	 */
	private static int getConfigFromFile(){
		int ret = -1;
		try(BufferedReader r = new BufferedReader(new FileReader("./configFile"))){
			TCPIP = new String(r.readLine().split(":")[1]);
			TCPSERVERPORT = Integer.parseInt((r.readLine().split(":")[1]));
			MCPORT = Integer.parseInt((r.readLine().split(":")[1]));
			MCIP = new String(r.readLine().split(":")[1]);
			RMISERVERPORT = Integer.parseInt((r.readLine().split(":")[1]));
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

