/**
* @author Mario Leonardo Salians
*/
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RMIServerOp extends Remote{
	public final static String NAME = "SimpleSocialServerRMI";

	/**
   	* This function adds userName as a follower of toFollow if and only if toFollow is online.
   	* @param userName the user-name of the user that wants to follow toFollow.
   	* @param toFollow the user-name of the user to be followed.
   	* @return 0 when the operation is successfully completed, -1 otherwise.
   	* @exception RemoteException on an error.
   	* @see RemoteException
   	*/
   	public int follow(String userName, String toFollow) throws RemoteException;

	/**
   	* This function completes the login of uName setting his userStub, tcpPort for checking his online status
   	* and sending him unread posts and followed users
   	* @param uName the user-name of the user to be logged.
   	* @param userStub the userStub of the user to be logged.
   	* @return 0 when the operation is successfully completed, -1 otherwise.
   	* @exception RemoteException on an error.
   	* @see RemoteException
   	*/
   	public int login(String uName, RMIClientOP userStub, int port) throws RemoteException;

}
