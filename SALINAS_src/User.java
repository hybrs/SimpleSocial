/**
* @author Mario Leonardo Salians
*/
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Vector;
import java.util.concurrent.locks.ReentrantLock;

public class User{
	private static RMIServerOp followMan;
	private RMIClientOP userStub; 
	protected String userName;
	private int tcpPort;
	private long UID;
	protected long tAuth;
	private Vector<String> followed; 
	protected Vector<String> requests; 
	protected ReentrantLock reqLock; 
	protected ReentrantLock followedLock; 
	private ReentrantLock postFileLock;
	
	/*
	 * Constructor
	 */
	
	/**
	 * The class constructor. This method initializes all fields including locks.
	 * @param u the user-name to be assigned.
	 * @param uid the UID to be assigned.
	 * @param t the authorization token to be assigned.
	 */
	public User(String u, long uid, long t){
		this.userName = new String(u);
		this.UID = uid;
		this.tAuth = t;
		this.tcpPort = -1;
		this.followed = new Vector<>();
		this.requests = new Vector<>();
		this.postFileLock = new ReentrantLock();
		this.reqLock = new ReentrantLock();
		this.followedLock = new ReentrantLock();
	}	
	
	/*
	 * Getters
	 */
	
	/**
	 * This function returns the RMI userStub.
	 * @return the RMI userStub.
	 */
	public RMIClientOP getUserStub(){
		return this.userStub;
	}
	
	/**
	 * This function returns the RMI serverStub.
	 * @return the RMI serverStub.
	 */
	public RMIServerOp getFollowMan(){
		return User.followMan;
	}

	/**
	 * This function returns the friendship request in position i.
	 * @param i the position of the friendship request to be returned. 
	 * @return the friendship request in position i.
	 */
	public String getRequest(int i){
		this.reqLock.lock();
		String req = new String(this.requests.get(i));
		this.reqLock.unlock();
		return req;
	}
	
	/**
	 * This method returns a copy of the list of user's followed users.
	 * @return a copy of the list of user's followed users.
	 */
	@SuppressWarnings("unchecked")
	public Vector<String> getFollowed(){
		this.followedLock.lock();
		Vector<String> ret = (Vector<String>) this.followed.clone();
		this.followedLock.unlock();
		return ret;
	}

	/**
	 * This method returns the user-name of this user.
	 * @return the user-name of this user.
	 */
	public String getUserName() {
		return userName;
	}

	/**
	 * This method returns the UID of this user.
	 * @return the UID of this user.
	 */
	public long getUID() {
		return UID;
	}

	/**
	 * This method returns the followedLock of this user useful to perform read/write operations
	 * on the data structure, accessed in a concurrent way, that contains all user's followed users
	 * @return the followedLock of this user.
	 */
	public ReentrantLock getFollowedLock() {
		return followedLock;
	}

	/**
	 * This method returns the reqLock of this user useful to perform read/write operations
	 * on the data structure, accessed in a concurrent way, that contains all the friendship requests received by this user.
	 * @return the reqLock of this user.
	 */
	public ReentrantLock getReqLock() {
		return reqLock;
	}
	
	/**
	 * This function returns the user's followed user in position i.
	 * @param i the position of the user's followed user to be returned. 
	 * @return the user's followed user in position i.
	 */
	public String getFollowed(int i){
		this.followedLock.lock();
		String ret = this.followed.get(i);
		this.followedLock.unlock();
		return ret;
	}

	/**
	 * This function returns the number of user's followed users.
	 * @return the number of user's followed users.
	 */
	public int followedSize(){
		this.followedLock.lock();
		int size = this.followed.size();
		this.followedLock.unlock();
		return size;
	}

	/**
	 * This function returns the port number where this user is waiting for friendship requests.
	 * @return the port number where this user is waiting for friendship requests.
	 */
	public int getPort(){
		return this.tcpPort;
	}

	/**
	 * This function returns the number of non-confirmed/refused friendship requests of this user.
	 * @return the number of non-confirmed/refused friendship requests of this user.
	 */
	public int requestSize(){
		return this.requests.size();
	}
	
	/*
	 * Setters
	 */
	
	/**
	 * This method sets the port number where this user'll wait for friendship requests.
	 * @param port the chosen port number.
	 */
	public void setPort(int port) {
		this.tcpPort = port;
	}
	
	/**
	 * This method sets the RMI serverStub.
	 * @param man the RMI serverStub to be set.
	 */
	public void setFollMan(RMIServerOp man){
		User.followMan = man;
	}

	/**
	 * This method sets the RMI userStub.
	 * @param c the RMI userStub to be set.
	 */
	public void setUserStub(RMIClientOP c){
		this.userStub = c;
	}

	/**
	 * This method sets the authorization token for this user.
	 * @param man the authorization token to be set.
	 */
	public void settAuth(long tAuth) {
		this.tAuth = tAuth;
	}
	
	/*
	 * Methods
	 */
	
	/**
	 * This function checks whether this user is equals to the other passed as an argument.
	 * @param u the user to be checked
	 * @return true if this user is equals to u, false otherwise.
	 */
	public boolean equals(User u){
		return (this.UID == u.UID);
	}
	
	/**
	 * This function checks whether this user's UID is equals to uid.
	 * @param uid the user's UID to be checked
	 * @return true if this user's UID is equals to uid, false otherwise.
	 */
	public boolean equals(long uid){
		return (this.UID == uid);
	}
	
	/**
	 * This method adds a followed user to this user's list.
	 * @param foll the user to be added.
	 */
	public void addFollowed(String foll){
		this.followedLock.lock();
		this.followed.add(foll);
		this.followedLock.unlock();
	}
	
	/**
	 * This method adds a friendship request to the local user's list.
	 * @param string the request to be added.
	 */
	public void addRequest(String string) {
		this.reqLock.lock();
		this.requests.add(string);
		this.reqLock.unlock();
	}
	
	/**
	 * This function writes a post to the userFile to prevent a data loss in case of user failure or user not logged out properly.
	 * @param data post to be written
	 * @param outFile the path of the output file
	 * @return 0 if the post is successfully locally saved, -1 if an IOerror occurs while writing on file.
	 */
	public int addPostToFile(String data, String outFile){
		this.postFileLock.lock();
		try(FileWriter out = new FileWriter(outFile, true)){
			out.write(data);
			out.flush();
		}catch(IOException e){
			System.out.println("Some error occurred while writing a post: "+e.getMessage());
			return -1;
		}finally{
			this.postFileLock.unlock();
		}
		return 0;
	}
	
	/**
	 * This function reads all unread posts locally saved and prints them to the stdout.
	 * @return 0 if reading is successfully completed, -1 if an IOerror occurs while reading or deleting the local file.
	 */
	public int readPosts(){
		Path dir = Paths.get("./userFiles");
		String postsFile = "/"+this.getUserName()+"Posts.dat";
		this.postFileLock.lock();
		int index = 1;
		try(FileReader in = new FileReader(dir+postsFile);
				BufferedReader r = new BufferedReader(in)){
			String post = null;
			while((post = r.readLine()) != null){
				System.out.println(index+". "+post);
				index++;
			}
		}catch(FileNotFoundException e){
			System.out.println("You have no post to be read.");
			return 0;
		}catch(IOException e){
			System.out.println("Something went wrong while reading posts: "+e.getMessage());
			return -1;
		}finally{
			this.postFileLock.unlock();
		}
		try {
			Files.delete(Paths.get(dir+postsFile));
		}catch(NoSuchFileException e){
			return 0;
		}catch (IOException e) {
			System.out.println("Something went wrong while deleting post file: "+e.getMessage());
			return -1;
		}
		return 0;
	}
	
	/**
	 * This function checks whether the user's followed list contains foll.
	 * @param foll the user to be searched.
	 * @return true if foll is contained in the user's followed list, false otherwise.
	 */
	public boolean findFollowed(String foll){
		this.followedLock.lock();
		boolean ret = false; int i = 0, size = this.followedSize();
		while(!ret && i < size){
			if(Objects.equals(getFollowed(i), foll)) ret = true;
			i++;
		}
		this.followedLock.unlock();
		return ret;
	}
	
	/**
	 * This function checks whether the user's requests list contains a request from name.
	 * @param name the user-name of the request to be searched.
	 * @return true if there's a friendship request from name for this user, false otherwise.
	 */
	public boolean containsRequestClient(String name){
		int size = this.requestSize(), i = 0;
		boolean found = false;
		while( !found && i < size){
			if(Objects.equals(this.getRequest(i), name))
				found = true;
			i++;
		}
		return found;	
	}
	
	/**
	 * This function checks whether the user's followed list contains a name.
	 * @param name the user-name to be searched.
	 * @return true if there's a followed named name, false otherwise.
	 */
	public boolean containsFollowed(String name){
		int size = this.followedSize(), i = 0;
		boolean found = false;
		while( !found && i < size){
			if(Objects.equals(this.getFollowed(i), name))
				found = true;
			i++;
		}
		return found;	
	}
	
	/**
	 * This method removes a friendship request sent from name to this user.
	 * @param name the request sender's name.
	 */
	public void removeRequest(String name){
		if(this.requests.contains(name))
			this.requests.remove(name);
	}
}