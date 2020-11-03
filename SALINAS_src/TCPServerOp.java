/**
* @author Mario Leonardo Salians
*/
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.util.Date;
import java.util.Objects;
import java.util.Vector;

public class TCPServerOp{
	
	private static final String usersFile = "/users.dat";
	private static Path dir = null;
	
	/**
	 * The class constructor. This method also creates a directory where server's file will be written.
	 * @throws IOException when fails to create the directory
	 */
	public TCPServerOp() throws IOException{
		dir = Paths.get("./serverFiles");
		if(!Files.exists(dir))
			Files.createDirectories(dir);
	}
	
	/**
	 * This static function returns the relative path of the user's backup file.
	 * @return the relative path of the user's backup file
	 */
	public static String getUsersFile() {
		return dir+usersFile;
	}

	/**
	 * This function implements the user registration in the server.
	 * @param w the client socket OutputStream
	 * @param r the client socket InputStream
	 * @return 0 when registration is completed
	 * @throws IOException on IOError
	 */
	public int register(BufferedWriter w, BufferedReader r) throws IOException {
		String uName = null;
		while(findUserFromName((uName = r.readLine())) != null)
			send("NO", w);
		send("OK", w);
		String psw = r.readLine();
		send("OK", w);
		long uid = (uName+psw).hashCode();
		ServerUser newU = new ServerUser(uName, psw, uid, -1);
		SimpleSocialServer.lock.lock();
		SimpleSocialServer.users.add(newU);
		SimpleSocialServer.lock.unlock();
		return 0;
	}
	
	/**
	 * This function implements the first login phase in the server. This function also creates a new authorization token.
	 * @param w the client socket OutputStream
	 * @param r the client socket InputStream
	 * @return 0 when the first login phase is completed
	 * @throws IOException on IOError
	 */
	public int login(BufferedWriter w, BufferedReader r) throws IOException {
		@SuppressWarnings("unused")
		String uName = null; ServerUser u = null;
		while((u = findUserFromName((uName = r.readLine()))) == null)
			send("NO", w);
		send("OK", w);
		while(!Objects.equals(r.readLine(), u.getPassword()))
			send("NO", w);
		send("OK", w);
		r.readLine();
		long uid = u.getUID();
		StringBuilder b = new StringBuilder();
		b.append(uid);
		send(b.toString(), w);
		r.readLine();
		long auth = new Date().getTime();
		StringBuilder a = new StringBuilder();
		a.append(auth);
		send(a.toString(), w);
		r.readLine();
		u.settAuth(auth);
		u.setOnline(true);
		return 0;
	}

	/**
	 * This function processes a friendship request. This function also checks 
	 * whether the user's authorization token is expired or not, if so provides a new login for the requesting user.
	 * @param w the client socket OutputStream
	 * @param r the client socket InputStream
	 * @return 0 when the request processing is completed
	 * @throws IOException on IOError
	 */
	public int request(BufferedWriter w, BufferedReader r) throws IOException {
		long UID = Long.parseLong(r.readLine());
		ServerUser uTmp = null;
		if((uTmp = findUser(UID)).isExpired() || !uTmp.getOnline() || uTmp.getLogged()){
			send("EXPIRED", w);
			uTmp.setOnline(false);
			postLogin(w, r, uTmp.getUserName());
		}else send("OK", w);
		String name = r.readLine();
		if(Objects.equals("EXIT", name)){
			send("ACK", w);
			return 0;
		}
		ServerUser u = null;
		if((u = findUserFromName(name)) == null)
			send("NOUSER", w);
		else if(u.hasFriend(uTmp.getUserName()))
			send("FRIEND", w);
		else if(u.containsRequest(uTmp.getUserName()) != -1)
			send("ALREADYREQ", w);
		else if(u.getPort() == -1)
			send("OFFLINE", w);
		else{
			try(Socket userRequested = new Socket(InetAddress.getByName("localhost"), u.getPort());
					BufferedWriter uWriter = new BufferedWriter(new OutputStreamWriter(userRequested.getOutputStream()));
					BufferedReader uReader = new BufferedReader(new InputStreamReader(userRequested.getInputStream(), "UTF-8"))){
				userRequested.setTcpNoDelay(true);
				userRequested.setSoTimeout(2000);
				//Handshake phase
				send("go", uWriter);
				uReader.readLine();
				send(uTmp.getUserName(), uWriter);
				uReader.readLine();
				u.getReqLock().lock();
				u.addRequestServer(uTmp.getUserName());
				u.getReqLock().unlock();
				send("OK", w);
			}catch(NullPointerException | IOException e){
				send("OFFLINE", w);
				if(u.getReqLock().isHeldByCurrentThread())
					u.getReqLock().unlock();
			}
		}
		r.readLine();
		return 0;
	}

	/**
	 * This function processes a friendship request confirmation. This function also checks 
	 * whether the user's authorization token is expired or not, if so provides a new login for the requesting user.
	 * @param w the client socket OutputStream
	 * @param r the client socket InputStream
	 * @return 0 when the confirmation processing is completed, -1 if there's an IOError during the confirmation processing
	 * @throws IOException if an IOError occurs during the user's authentication.
	 */
	public int confirmRequest(BufferedWriter w, BufferedReader r) throws IOException{
		long UID = Long.parseLong(r.readLine());
		ServerUser uTmp = null;
		if((uTmp = findUser(UID)).isExpired() || !uTmp.getOnline() || uTmp.getLogged()){
			send("EXPIRED", w);
			uTmp.setOnline(false);
			postLogin(w, r, uTmp.getUserName());
		}else send("OK", w);
		String name = r.readLine();
		ServerUser requested = null;
		try{
			uTmp.getReqLock().lock();
			int req = -1;
			if((req = uTmp.containsRequest(name)) == -1){
				send("NO", w);
				r.readLine();
			}else{
				send("OK", w);
				String confirm = r.readLine();
				send("DONE", w);
				if(Objects.equals(confirm, "Y")){
					requested = findUserFromName(name);
					uTmp.getFriendLock().lock();
					requested.getFriendLock().lock();
					uTmp.addFriend(name);
					requested.addFriend(uTmp.getUserName());
					requested.getFriendLock().unlock();
					uTmp.getFriendLock().unlock();
				}
				uTmp.removeRequestServer(req);
			}
			uTmp.getReqLock().unlock();
		}catch(IOException e){
			return -1;
		}finally{
			if(requested != null )
				if(requested.getFriendLock().isHeldByCurrentThread())
					requested.getFriendLock().unlock();
			if(uTmp != null ){
				if(uTmp.getFriendLock().isHeldByCurrentThread())
					uTmp.getFriendLock().unlock();
				if(uTmp.getReqLock().isHeldByCurrentThread())
					uTmp.getReqLock().unlock();	
			}
		}
		return 0;
	}

	/**
	 * This function processes a search request. This function also checks 
	 * whether the user's authorization token is expired or not, if so provides a new login for the requesting user.
	 * @param w the client socket OutputStream
	 * @param r the client socket InputStream
	 * @return 0 when the search processing is completed.
	 * @throws IOException if an IOError occurs.
	 */
	public int search(BufferedWriter w, BufferedReader r) throws IOException{
		long UID = Long.parseLong(r.readLine());
		ServerUser uTmp = null;
		if((uTmp = findUser(UID)).isExpired() || !uTmp.getOnline() || uTmp.getLogged()){
			send("EXPIRED", w);
			uTmp.setOnline(false);
			postLogin(w, r, uTmp.getUserName());
		}else send("OK", w);
		String name = r.readLine();
		Vector <String> found = new Vector<>();
		SimpleSocialServer.lock.lock();
		for(ServerUser u : SimpleSocialServer.users)
			if(u.getUserName().toLowerCase().contains(name)) found.add(u.getUserName());
		SimpleSocialServer.lock.unlock();
		StringBuilder b = new StringBuilder();
		b.append(found.size());
		send(b.toString(), w);
		r.readLine();
		if(found.size() > 0)
			for(int i = 0; i < found.size(); i++){
				send(found.get(i), w);
				r.readLine();
			}	
		return 0;
	}

	/**
	 * This method provides a new login for the user whose session is expired.
	 * @param w the client socket OutputStream
	 * @param r the client socket InputStream
	 * @param uName the user-name of the user whose session is expired.
	 * @throws IOException if an IOError occurs.
	 */
	private void postLogin(BufferedWriter w, BufferedReader r, String uName) throws IOException {
		ServerUser u = null;
		while(!Objects.equals(r.readLine(), uName))
			send("NO", w);
		u = findUserFromName(uName);
		send("OK", w);
		while(!Objects.equals(r.readLine(), u.getPassword()))
			send("NO", w);
		send("OK", w);
		r.readLine();
		long auth = new Date().getTime();
		StringBuilder a = new StringBuilder();
		a.append(auth);
		send(a.toString(), w);
		if(!Objects.equals("ERR", r.readLine()));
			u.setOnline(true);
		return;
	}

	/**
	 * This function sends the requesting user his friends list. This function also checks whether 
	 * the user's authorization token is expired or not, if so provides a new login for the requesting user.
	 * @param w the client socket OutputStream
	 * @param r the client socket InputStream
	 * @return 0 when the all friends are sent, -1 if there's an IOError while sending friends.
	 * @throws IOException if an IOError occurs during the user's authentication.
	 */
	public int listFriends(BufferedWriter w, BufferedReader r) throws IOException {
		long UID = Long.parseLong(r.readLine());
		ServerUser uTmp = null;
		if((uTmp = findUser(UID)).isExpired() || !uTmp.getOnline() || uTmp.getLogged()){
			send("EXPIRED", w);
			uTmp.setOnline(false);
			postLogin(w, r, uTmp.getUserName());
		}else send("OK", w);
		uTmp.getFriendLock().lock();
		try{
			StringBuilder b =new StringBuilder();
			int size = 0;
			b.append((size = uTmp.friendSize()));
			send(b.toString(), w);
			r.readLine();
			for (int i = 0; i < size; i++) {
				String friendName = uTmp.getFriend(i);
				if(findUserFromName(friendName).getOnline())
					send(friendName+" - online", w);
				else send(friendName+" - offline", w);
				r.readLine();
			}
			uTmp.getFriendLock().unlock();
		}catch(IOException e){
			if(uTmp.getFriendLock().isHeldByCurrentThread())
				uTmp.getFriendLock().unlock();
			return -1;
		}catch(Exception e){
			e.printStackTrace();
		}
		return 0;
	}

	/**
	 * This function posts a new content and sends it to all user's followers. This function also checks whether the user's authorization token
	 * is expired or not, if so provides a new login for the requesting user.
	 * @param w the client socket OutputStream
	 * @param r the client socket InputStream
	 * @return 0 when the content is posted and sent to all user's followers, or locally stored for those offline followers.
	 * @throws IOException if an IOError occurs during the user's authentication.
	 */
	public int post(BufferedWriter w, BufferedReader r) throws IOException {
		long UID = Long.parseLong(r.readLine());
		ServerUser uTmp = null;
		if((uTmp = findUser(UID)).isExpired() || !uTmp.getOnline() || uTmp.getLogged()){
			send("EXPIRED", w);
			uTmp.setOnline(false);
			postLogin(w, r, uTmp.getUserName());
		}else send("OK", w);
		String post = r.readLine();
		send("ACK", w);
		int size = uTmp.followerSize();
		SimpleSocialServer.lock.lock();
		for(int i = 0; i < size; i++){
			ServerUser follower = findUserFromName(uTmp.getFollower(i));
			if(follower.getOnline())
				try{
					follower.getUserStub().addFollowcontent(uTmp.getUserName()+" - "+post);
					}catch(RemoteException e){
						follower.addPost(uTmp.getUserName()+" - "+post);
					}
			else follower.addPost(uTmp.getUserName()+" - "+post);
		}
		SimpleSocialServer.lock.unlock();
		return 0;
	}

	/**
	 * This function logs-out the requesting user.
	 * @param w the client socket OutputStream
	 * @param r the client socket InputStream
	 * @return 0 when the logout processing is completed.
	 * @throws NumberFormatException if UID is not a valid number.
	 * @throws IOException if an IOError occurs.
	 */
	public int logout(BufferedWriter w, BufferedReader r) throws NumberFormatException, IOException {
		long UID = Long.parseLong(r.readLine());
		ServerUser u = findUser(UID);
		if(u != null){
			u.setOnline(false);
			u.setLogged(true);
		}
		send("ACK", w);
		return 0;
	}
		
	/**
   	* This function sends msg to userWriter
   	* @param msg the message to be sent
   	* @param userWriter the BufferedWriter where to write msg
   	* @exception IOException On input error.
   	* @see IOException
   	*/
	public static void send(String msg, BufferedWriter userWriter) throws IOException{
		userWriter.write(msg+"\r\n");
		userWriter.flush();
	}
	
	/**
	 * This function search an user by user-name. If there's an user with the specified user-name then returns it, if not so, it returns null.
	 * @param uName the user-name to be searched.
	 * @return ServerUser u such that u.userName == uName if found, null otherwise. 
	 */
	public static ServerUser findUserFromName(String uName){
		ServerUser ret = null; int i = 0;
		SimpleSocialServer.lock.lock();
		while(ret == null && i < SimpleSocialServer.users.size()){
			if(Objects.equals(SimpleSocialServer.users.get(i).getUserName(), uName))
				ret = SimpleSocialServer.users.get(i);
			i++;
		}
		SimpleSocialServer.lock.unlock();
		return ret;
	}
	
	/**
	 * This function search an user by UID. If there's an user with the specified UID then returns it, if not so, it returns null.
	 * @param uid the UID to be searched.
	 * @return ServerUser u such that u.UID == uid if found, null otherwise. 
	 */
	public static ServerUser findUser(long uid){
		ServerUser ret = null; int i = 0;
		SimpleSocialServer.lock.lock();
		while(ret == null && i < SimpleSocialServer.users.size()){
			if(SimpleSocialServer.users.get(i).equals(uid)) ret = SimpleSocialServer.users.get(i);
			i++;
		}
		SimpleSocialServer.lock.unlock();
		return ret;
	}
	
	/**
	 * This method ensures that on a follow request the requesting user authentication token is still valid.
	 * @param w the client socket OutputStream
	 * @param r the client socket InputStream
	 * @throws NumberFormatException if UID is not a valid number.
	 * @throws IOException if an IOError occurs.
	 */
	public void follow(BufferedWriter w, BufferedReader r) throws NumberFormatException, IOException {
		long UID = Long.parseLong(r.readLine());
		ServerUser uTmp = null;
		if((uTmp = findUser(UID)).isExpired() || !uTmp.getOnline() || uTmp.getLogged()){
			send("EXPIRED", w);
			uTmp.setOnline(false);
			postLogin(w, r, uTmp.getUserName());
		}else send("OK", w);
	}
}
