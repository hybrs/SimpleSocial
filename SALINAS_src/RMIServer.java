/**
* @author Mario Leonardo Salians
*/
import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;

public class RMIServer extends RemoteObject implements RMIServerOp{

	private static final long serialVersionUID = -8639538324623660121L;

	/**
   	* This function adds userName as a follower of toFollow if and only if toFollow is online.
   	* @param userName the user-name of the user that wants to follow toFollow.
   	* @param toFollow the user-name of the user to be followed.
   	* @return 0 when the operation is successfully completed, -1 otherwise.
   	* @exception RemoteException on an error.
   	* @see RemoteException
   	*/
	@Override
	public int follow(String userName, String toFollow) throws RemoteException {
		ServerUser u = null;
		if((u = TCPServerOp.findUserFromName(toFollow)) == null) return -1;
		else{
			u.addFollower(userName);
			TCPServerOp.findUserFromName(userName).addFollowed(toFollow);
		}
		return 0;
	}

	/**
   	* This function completes the login of uName setting his userStub, tcpPort for checking his online status
   	* and sending him unread posts and followed users
   	* @param uName the user-name of the user to be logged.
   	* @param userStub the userStub of the user to be logged.
   	* @return 0 when the operation is successfully completed, -1 otherwise.
   	* @exception RemoteException on an error.
   	* @see RemoteException
   	*/
	@Override
	public int login(String uName, RMIClientOP userStub, int port) throws RemoteException {
		ServerUser u = TCPServerOp.findUserFromName(uName);
		u.setUserStub(userStub);
		u.setPort(port);
		int size = u.postSize();
		for(int i = 0; i < size; i++)
			userStub.addFollowcontent(u.getPost(i));
		u.deleteAllPosts();
		u.setLogged(false);
		userStub.addFollowed(u.getFollowed());
		return 0;
	}
}
