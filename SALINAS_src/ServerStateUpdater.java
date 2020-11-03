/**
* @author Mario Leonardo Salians
*/
import java.io.FileWriter;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.MappedByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Vector;
import java.util.concurrent.locks.ReentrantLock;

public class ServerStateUpdater implements Runnable{

	private final String usersFile = "/users.dat";
	private Path dir = null;
	//Backup frequency
	private final long timeout = 5000;
	
	/**
	 * The class constructor. This constructor creates a directory in which 
	 * the server will be able to backup its data.
	 * @exception IOException
	 */
	public ServerStateUpdater() throws IOException {
		dir = Paths.get("./serverFiles");
		if(!Files.exists(dir))
			Files.createDirectories(dir);
	}

	/**
	*	This method executes the server's backup. Backup occurs every 5 seconds. If the previous tmpFile still exists, it's deleted and a new tmpFile it's created.
	*	Initially the server status is only saved to the temporary file, to avoid losing any data in case of failure,
	*	when the writing ends, the old backup file is replaced with the temporary file. If something goes wrong the server status 
	* 	can't be saved anymore, so the whole server's process must terminate.
	*/
	@Override
	public void run() {
		Vector<ReentrantLock> locks = null;
		if(Files.exists(Paths.get(dir+"/usersTmp.dat")))
			try {
				Files.delete(Paths.get(dir+"/usersTmp.dat"));
			} catch (IOException e2) {
				System.out.println("UpdateUser - Some error occurred while deleting tmp file: "+e2.getMessage());
			}
		try{
			while(!Thread.interrupted()){
				Thread.sleep(timeout);
				try(FileWriter file = new FileWriter(dir+"/usersTmp.dat", false);){
				} catch (IOException e1) {
					System.out.println("UpdateUsers - Some error occurred : "+e1.getMessage());
					SimpleSocialServer.term = true;
					return;
				}
				FileChannel inChannel= FileChannel.open(Paths.get(dir+"/usersTmp.dat"),	StandardOpenOption.READ,StandardOpenOption.WRITE);
				SimpleSocialServer.lock.lock();
				int size = 0;
				int sizeU = SimpleSocialServer.users.size();
				for(int i = 0; i < sizeU; i++)
					size += SimpleSocialServer.users.get(i).getSize();
				if(sizeU > 0){
					MappedByteBuffer mappedFile= inChannel.map(MapMode.READ_WRITE, 0, size);
					mappedFile.putInt(sizeU);
					for(ServerUser user : SimpleSocialServer.users){
						locks = new Vector<>();
						locks.add(0, user.getPostLock());
						locks.add(1, user.getFriendLock());
						locks.add(2, user.getFollowedLock());
						locks.add(3, user.getFollowLock());
						locks.add(4, user.getReqLock());
						//Putting username
						mappedFile.putInt(user.getUserName().length());
						for(int i = 0; i < user.getUserName().length(); i++)
							mappedFile.putChar(user.getUserName().charAt(i));
						
						//Putting password
						mappedFile.putInt(user.getPassword().length());
						for(int i = 0; i < user.getPassword().length(); i++)
							mappedFile.putChar(user.getPassword().charAt(i));
						
						//Putting UID e token
						mappedFile.putLong(user.getUID());
						mappedFile.putLong(user.getAuth());
						
						locks.get(0).lock();
						//Putting toBeSentPosts
						int postS = user.postSize();
						mappedFile.putInt(postS);
						for(int i = 0; i <postS; i++){
							String post = user.getPost(i);
							int len = post.length();
							mappedFile.putInt(len);
							for (int j = 0; j < len; j++)
								mappedFile.putChar(post.charAt(j));
						}
						locks.get(0).unlock();
						
						locks.get(1).lock();
						//Putting friends
						int frS = user.friendSize();
						mappedFile.putInt(frS);
						for(int i = 0; i < frS; i++){
							String friend = user.getFriend(i);
							int len = friend.length();
							mappedFile.putInt(len);
							for (int j = 0; j < len; j++)
								mappedFile.putChar(friend.charAt(j));
						}
						locks.get(1).unlock();
						
						locks.get(2).lock();
						//Putting followed
						int fS = user.followedSize();
						mappedFile.putInt(fS);
						for(int i = 0; i < fS; i++){
							String foll = user.getFollowed(i);
							int len = foll.length();
							mappedFile.putInt(len);
							for (int j = 0; j < len; j++)
								mappedFile.putChar(foll.charAt(j));
						}
						locks.get(2).unlock();
						
						locks.get(3).lock();
						//Putting followers
						int fS1 = user.followerSize();
						mappedFile.putInt(fS1);
						for(int i = 0; i < fS1; i++){
							String foll = user.getFollower(i);
							int len = foll.length();
							mappedFile.putInt(len);
							for (int j = 0; j < len; j++)
								mappedFile.putChar(foll.charAt(j));
						}
						locks.get(3).unlock();
						
						locks.get(4).lock();
						//Putting requests
						int rS = user.requestSize();
						mappedFile.putInt(rS);
						for(int i = 0; i < rS; i++){
							String req = user.getRequest(i);
							int len = req.length();
							mappedFile.putInt(len);
							for (int j = 0; j < len; j++)
								mappedFile.putChar(req.charAt(j));
						}
						locks.get(4).unlock();
					}
				}
				inChannel.close();
				SimpleSocialServer.lock.unlock();
				try {
					Files.move(Paths.get(dir+"/usersTmp.dat"), Paths.get(dir+usersFile), StandardCopyOption.REPLACE_EXISTING);
					if(Files.exists(Paths.get(dir+"/usersTmp.dat"))) Files.delete(Paths.get(dir+"/usersTemp.dat"));
				} catch (IOException e) {
					System.out.println("Some error occurred while renaming and deleting tmp file: "+e.getMessage());
				}
			}
		}catch(InterruptedException e){
			System.out.println("UpdateUsers - Interrupt recieved.");
		}catch(BufferOverflowException e){
			System.out.println("UpdateUsers - Buffer OF");
		}catch(ReadOnlyBufferException e){
			System.out.println("UpdateUsers - Buffer ReadOnly");
		}catch(IOException e){
			System.out.println("UpdateUsers - Something went wrong: "+e.getMessage());
		}finally{
			if(SimpleSocialServer.lock.isHeldByCurrentThread())
				SimpleSocialServer.lock.unlock();
			if(locks != null)
				for (int i = 0; i < 5; i++) 
					if(locks.get(i).isHeldByCurrentThread())
						locks.get(i).unlock();
			SimpleSocialServer.term = true;
		}
		return;
	}
	
	/**
	*	Is a static method that restore the last-saved server's status. If the backup file is corrupted 
	*	or not exists an empty Vector is returned
	*	@return Vector<ServerUser> that represents the last saved server state, or an empty Vector in case of failure
	*   @exception IOException on an IOError
	*   @see IOException
	*/
	public static Vector<ServerUser> readUsers() throws IOException{
		SimpleSocialServer.lock.lock();
		Path dir = Paths.get("./serverFiles");
		if(!Files.exists(dir))
			Files.createDirectories(dir);
		Vector<ServerUser> users = new Vector<>();
		try (FileChannel inChannel= FileChannel.open(Paths.get(dir+"/users.dat"),
				StandardOpenOption.READ, StandardOpenOption.WRITE)){
			long size = inChannel.size();
			MappedByteBuffer mappedFile= inChannel.map(MapMode.READ_WRITE, 0, size);
			int lim = mappedFile.getInt();
			int g = 0;
			while(g < lim){
				//Getting UserName
				int uns = mappedFile.getInt();
				StringBuilder b = new StringBuilder(uns);
				for (int i = 0; i < uns; i++)
					b.append(mappedFile.getChar());
				String uName = b.toString();
				
				//Getting Password
				int ps = mappedFile.getInt();
				StringBuilder a = new StringBuilder(ps);
				for (int i = 0; i < ps; i++)
					a.append(mappedFile.getChar());
				String pass = a.toString();
				
				//Getting UID and token
				long UID = mappedFile.getLong();
				long token = mappedFile.getLong();
				ServerUser u = new ServerUser(uName, pass, UID, token);
				
				//Getting Posts
				int postsSize = mappedFile.getInt();
				for (int j = 0; j < postsSize; j++) {
					int postSize = mappedFile.getInt();
					StringBuilder post = new StringBuilder(postSize);
					for (int k = 0; k < postSize; k++)
						post.append(mappedFile.getChar());
					u.addPost(post.toString());
				}
				
				//Getting Friends
				int friendsSize = mappedFile.getInt();
				for (int j = 0; j < friendsSize; j++) {
					int nameSize = mappedFile.getInt();
					StringBuilder name = new StringBuilder(nameSize);
					for (int k = 0; k < nameSize; k++)
						name.append(mappedFile.getChar());
					u.addFriend(name.toString());
				}
				
				//Getting Followed
				int follSize1 = mappedFile.getInt();
				for (int j = 0; j < follSize1; j++) {
					int nameSize = mappedFile.getInt();
					StringBuilder name = new StringBuilder(nameSize);
					for (int k = 0; k < nameSize; k++)
						name.append(mappedFile.getChar());
					u.addFollowed(name.toString());
				}
				
				//Getting Followers
				int follSize = mappedFile.getInt();
				for (int j = 0; j < follSize; j++) {
					int nameSize = mappedFile.getInt();
					StringBuilder name = new StringBuilder(nameSize);
					for (int k = 0; k < nameSize; k++)
						name.append(mappedFile.getChar());
					u.addFollower(name.toString());
				}
				
				//Getting requests
				int reqSize = mappedFile.getInt();
				for (int j = 0; j < reqSize; j++) {
					int nameSize = mappedFile.getInt();
					StringBuilder name = new StringBuilder(nameSize);
					for (int k = 0; k < nameSize; k++)
						name.append(mappedFile.getChar());
					u.addRequest(name.toString());
				}
				users.add(u);
				g++;
			}
		}catch(BufferOverflowException | BufferUnderflowException e){
			System.out.println("File it's corrupted, deleting it and rewriting: "+e.getMessage());
			if(Files.exists(Paths.get(dir+"/users.dat")));
					Files.delete(Paths.get(dir+"/users.dat"));
			return new Vector<>();
		}catch (IOException e) {
			return new Vector<>();
		}finally{
			if(SimpleSocialServer.lock.isHeldByCurrentThread())
				SimpleSocialServer.lock.unlock();
		}
		if(SimpleSocialServer.lock.isHeldByCurrentThread())
			SimpleSocialServer.lock.unlock();
		return users;
	}
}
