/**
* @author Mario Leonardo Salians
*/
import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;
import java.util.Vector;

public class RMIClient extends RemoteObject implements RMIClientOP{

	private static final long serialVersionUID = 2524527333574741379L;
	
	/**
   	* This function writes a post to the userFile.
   	* @param p post the be written.
   	* @return 0 when the operation is completed
   	* @exception RemoteException on an error.
   	* @see RemoteException
   	*/
	@Override
	public int addFollowcontent(String post) throws RemoteException {
		SimpleSocialClient.user.addPostToFile(post+"\r\n", "./userFiles/"+SimpleSocialClient.user.getUserName()+"Posts.dat");
		return 0;
	}

	/**
   	* This function sets the user's followed list.
   	* @param followed the user's followed list.
   	* @return 0 when the operation is completed
   	* @exception RemoteException on an error.
   	* @see RemoteException
   	*/
	@Override
	public int addFollowed(Vector<String> followed) throws RemoteException {
		for(String x : followed)
			if(!SimpleSocialClient.user.containsFollowed(x))
				SimpleSocialClient.user.addFollowed(x);
		return 0;
	}

}
