/**
* @author Mario Leonardo Salians
*/
import java.util.Date;
import java.util.Objects;
import java.util.Vector;
import java.util.concurrent.locks.ReentrantLock;

public class ServerUser extends User{
	private final int secondsInADay = 86400;  
	private boolean isOnline; 
	private boolean toBeLogged;
	private String password; 
	private Vector<String> friends; 
	private Vector<String> followers; 
	private Vector<String> newPosts; 
	private ReentrantLock postLock; 
	private ReentrantLock followLock; 
	private ReentrantLock friendLock; 
	
	/**
	* The Class constructor.
	* @param u the user name.
	* @param p the user password.
	* @param uid the user UID.
	* @param t the user authToken.
	*/
	public ServerUser(String u, String p, long uid, long t) {
		super(u, uid, t);
		this.password = new String(p);
		this.friends = new Vector<>();
		this.followers = new Vector<>();
		this.newPosts = new Vector<>();
		this.isOnline = false;
		this.toBeLogged = true;
		this.postLock = new ReentrantLock();
		this.followLock = new ReentrantLock();
		this.friendLock = new ReentrantLock();
	}
	
	/*
	 * Getters
	 */
	
	/**
	 * This function returns the unread post in position i
	 * @param i the position of the unread post to be returned
	 * @return the unread post in position i
	 */
	public String getPost(int i){
		this.postLock.lock();
		String post =  new String(this.newPosts.get(i));
		this.postLock.unlock();
		return post;
	}
	
	/**
	 * This function returns the user's follower in position i
	 * @param i the position of the follower to be returned
	 * @return the users's follower in position i
	 */
	public String getFollower(int i){
		this.followLock.lock();
		String foll =  this.followers.get(i);
		this.followLock.unlock();
		return foll;
	}
	
	/**
	* This function returns the user size in bytes.
	* @return the user size in bytes
	*/
	public long getSize(){
		long size = 6*4;
		size += this.userName.length() + 2;
		size += this.password.length() + 2;
		size += 2*10;
		size += 6*(this.friendSize() + this.followerSize() + this.followedSize() + this.postSize() +this.requestSize());
		friendLock.lock();
		for(int i = 0; i < this.friendSize(); i ++)
			size += this.getFriend(i).length()+2;
		friendLock.unlock();
		followLock.lock();
		for(int i = 0; i < this.followerSize(); i ++)
			size += this.getFollower(i).length()+2;
		followLock.unlock();
		followedLock.lock();
		for(int i = 0; i < this.followedSize(); i ++)
			size += this.getFollowed(i).length() +1;
		followedLock.unlock();
		postLock.lock();
		for(int i = 0; i < this.newPosts.size(); i ++)
			size += this.getPost(i).length()+2;
		postLock.unlock();
		reqLock.lock();
		for(int i = 0; i < this.requestSize(); i ++)
			size += this.getRequest(i).length()+2;
		reqLock.unlock();
		return size*2;
	}
	
	/**
	 * This function returns the user's friend in position i
	 * @param i the position of the user's friend to be returned
	 * @return the user's friend in position i
	 */
	public String getFriend(int i){
		return new String(this.friends.get(i));
	}

	/**
	 * This function returns the user's online status
	 * @return the user's online status
	 */
	public boolean getOnline(){
		return this.isOnline;
	}

	/**
	 * This function reads the toBeLogged value that tells whether 
	 * the user needs to be logged again(token expired, server crash or new user)
	 * @return the user's to beLogged value
	 */
	public boolean getLogged(){
		return this.toBeLogged;
	}
	
	/**
	 * This function returns the user's authorization token
	 * @return the user's authorization token
	 */
	public long getAuth() {
		return super.tAuth;
	}
	
	/**
	 * This function returns the user's password
	 * @return the user's password
	 */
	public String getPassword() {
		return password;
	}
	
	/**
	 * This function returns the user's postLock useful to perform read/write operations
	 * on the data structure, accessed in a concurrent way, that contains all user's posts
	 * @return the user's postLock
	 */
	public ReentrantLock getPostLock() {
		return postLock;
	}
	
	/**
	 * This function returns the user's followLock useful to perform read/write operations
	 * on the data structure, accessed in a concurrent way, that contains all user's followers
	 * @return the user's followLock
	 */
	public ReentrantLock getFollowLock() {
		return followLock;
	}
	
	/**
	 * This function returns the number of user's friends
	 * @return the number of user's friends
	 */
	public int friendSize(){
		this.friendLock.lock();
		int size =  this.friends.size();
		this.friendLock.unlock();
		return size;
	}
	
	/**
	 * This function returns the number of user's follower 
	 * @return the number of user's followers
	 */
	public int followerSize(){
		this.followLock.lock();
		int size =  this.followers.size();
		this.followLock.unlock();
		return size;
	}
	
	/**
	 * This function returns the number of user's unread posts 
	 * @return the number of user's unread posts
	 */
	public int postSize(){
		this.postLock.lock();
		int size = this.newPosts.size();
		this.postLock.unlock();
		return size;
	}
	
	/**
	 * This function returns the user's friendLock useful to perform read/write operations
	 * on the data structure, accessed in a concurrent way, that contains all user's friends
	 * @return the user's friendLock
	 */
	public ReentrantLock getFriendLock() {
		return friendLock;
	}
	
	/*
	 * Setters
	 */
	
	/**
	 * This method sets the user's online status
	 * @param v the online status to be set
	 */
	public void setOnline(boolean v){ 
		this.isOnline = v;
	}
	
	/**
	 * This method sets the user's toBeLogged value
	 * @param x the user's toBeLogged value to be set
	 */
	public void setLogged(boolean x){
		this.toBeLogged = x;
	}
	
	/*
	 * Methods
	 */
	
	/**
	 * This method delete all unread user's posts saved locally
	 */
	public void deleteAllPosts(){
		this.postLock.lock();
		this.newPosts.removeAllElements();
		this.postLock.unlock();
	}

	/**
	 * This method adds a friend in the user's friends list
	 * @param name the name of the friend to be added
	 */
	public void addFriend(String name){
		this.friendLock.lock();
		this.friends.add(name);
		this.friendLock.unlock();
	}

	/**
	 * This method adds a follower in the user's followers list
	 * @param userStub the RMI userStub of the follower to be added that will be used for sending new posts
	 */
	void addFollower(String userStub){
		this.followLock.lock();
		this.followers.add(userStub);
		this.followLock.unlock();
	}

	/**
	 * This function reads the authorization token value and decides whether the user session is expired or not
	 * @return whether the user session is expired or not
	 */
	public boolean isExpired(){
		return ((new Date().getTime()-new Date(this.tAuth).getTime())/1000 > secondsInADay);
	}
	
	/**
	 * This method adds a post in the user's unread posts list
	 * @param string post to be added
	 */
	public void addPost(String string) {
		this.postLock.lock();
		this.newPosts.add(string);
		this.postLock.unlock();
	}
	
	/**
	* This method adds a friendship request in the server format: name-requestDate
	* this format helps the server to recognize an expired request(after 10 days)
	* @param name
	*/
	public void addRequestServer(String name){
		this.reqLock.lock();
		this.requests.add(name+"-"+new Date().getTime());
		this.reqLock.unlock();
	}
	
	/**
	 * This function tests whether the user has already a pending request for name
	 * @param name the requester name to be searched
	 * @return the position of the request sent from name, if exists, or -1 if no request from name was sent
	 */
	public int containsRequest(String name){
		int ret = -1;
		int size = this.requestSize(), i = 0;
		boolean found = false;
		while(i < size && !found){
			String[] req = this.getRequest(i).split("-");
			String nameReq = "";
			//Name parsing
			for(int j = 0 ; j < req.length-1; j++)
				if(j!= 0)
					nameReq = nameReq +"-"+req[j];
				else nameReq = req[j];
			//Verify expiration date
			long time = Long.parseLong(req[req.length-1]);
			if((new Date().getTime()-time) > (secondsInADay*10))
				this.requests.remove(i);
			else{
				if(Objects.equals(nameReq, name)){
					found = true;
					ret = i;
				}else i++;
			}
		}
		return ret;	
	}
	
	/**
	 * This method removes the friendship request in position i
	 * @param i the position of the friendship request to be removed
	 */
	public void removeRequestServer(int i){
		this.requests.remove(i);
	}
	
	/**
	 * This function checks whether the current user and uTmp are friends 
	 * @param uTmp the user to be checked as friend of the current user
	 * @return true if the current user and uTmp are friends, false otherwise
	 */
	public boolean hasFriend(String uTmp) {
		boolean ret = false; 
		this.friendLock.lock();
		int lim = this.friendSize(), i = 0;
		while(!ret && i < lim){
			String friend = this.getFriend(i);
			if(Objects.equals(uTmp, friend)){
				ret = true;
			}
			i++;
		}
		this.friendLock.unlock();
		return ret;
	}
}
