/**
* @author Mario Leonardo Salians
*/
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Vector;

public interface RMIClientOP extends Remote {
	
	public final static String NAME = "SimpleSocialClientRMI";

	/**
	* This function writes a post to the userFile.
	* @param p post the be written.
	* @return 0 when the operation is completed
	* @exception RemoteException on an error.
	* @see RemoteException
	*/
	public int addFollowcontent(String post) throws RemoteException;

   /**
   * This function sets the user's followed list.
   * @param followed the user's followed list.
   * @return 0 when the operation is completed
   * @exception RemoteException on an error.
   * @see RemoteException
   */
   public int addFollowed(Vector<String> followed) throws RemoteException;
	
}

