/**
* @author Mario Leonardo Salians
*/
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Console;
import java.io.FileWriter;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.NotBoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Objects;

public class TCPClientOp{
	
	private static int TCPPORT;
	private static Path dir = null;
	
	/**
	 * The class constructor. This method also creates a directory where user's file will be written
	 * @throws IOException when fails to create the directory
	 */
	public TCPClientOp() throws IOException{
		dir = Paths.get("./userFiles");
		if(!Files.exists(dir))
			Files.createDirectories(dir);
		TCPClientOp.TCPPORT = SimpleSocialClient.TCPSERVERPORT;
	}	

	/**
   	* This function implements the user registration in the client.
   	* @param r the System.in.
   	* @return 0 if registration is successfully completed, -2 otherwise
   	* @exception IOException On input error.
   	* @see IOException
   	*/
	public int register(BufferedReader r) throws IOException {
		try(Socket server = new Socket(InetAddress.getByName(SimpleSocialClient.TCPIP), TCPPORT);
			BufferedWriter socketWriter = new BufferedWriter(new OutputStreamWriter(server.getOutputStream()));
			BufferedReader socketReader = new BufferedReader(new InputStreamReader(server.getInputStream()));){
			server.setTcpNoDelay(true);
			server.setSoTimeout(10000);
			//Handshake phase
			socketReader.readLine();
			send("1", socketWriter);
			socketReader.readLine();
			print("Insert your userName");
			boolean ok = false;
			String name = null;
			while(!ok){
				while((name = r.readLine().trim()).length() <= 0)
					print("Invalid UserName, username length must be >= 0");
				send(name , socketWriter);
				if(Objects.equals(socketReader.readLine(), "OK")){
					ok = true;
					print(name+" is aviable!");
				}
					else print("username already in use, please insert another one.");
			}
			
			String psw = null;
			Console c = System.console();
			try{
				do{
					System.out.print("Now insert your password");
					if(c != null){
						System.out.println(" (won't be shown)");
						psw = new String(c.readPassword());
					}
					else{
						System.out.println();
						psw = r.readLine();
					}
				}while(psw == null || psw.length() <= 0);
			}catch(IOError e){
				print("Something went wrong. Registration couldn't be completed.");
				return -2;
			}
			send(psw, socketWriter);
			socketReader.readLine();
			print("Registration successfully completed! Now you can use SimpleSocial with your userName and password!");
		}catch(IOException e){
			System.out.println("Some error occurred while connecting: Connection refused.");
			return -2;
		}
		return 0;
	}
	
	/**
   	* This function implements the login and starts the KeepAliveThread in the client.
   	* @param r the System.in.
   	* @param kaThread is the KeepAliveThread in client
   	* @return the logged user or a user with non-significant values in case of failure
   	*/
	public User login(BufferedReader r, Thread kaThread) {
		User u = new User("", -1, -1);
		try(Socket server = new Socket(InetAddress.getByName(SimpleSocialClient.TCPIP), TCPPORT);
				BufferedWriter socketWriter = new BufferedWriter(new OutputStreamWriter(server.getOutputStream()));
				BufferedReader socketReader = new BufferedReader(new InputStreamReader(server.getInputStream()));){
			server.setTcpNoDelay(true);
			server.setSoTimeout(10000);
			//Handshake phase
			socketReader.readLine();
			send("2", socketWriter);
			socketReader.readLine();
			print("Insert your userName:");
			boolean correct = false;
			String name = null;
			do{
				send((name = r.readLine().trim()), socketWriter);
				if(Objects.equals(socketReader.readLine(), "OK")) correct = true;
				else print("Incorrect username, please insert a correct one.");
			}while(!correct);
			System.out.print("Insert the password for: "+name);
			correct = false;
			Console c = System.console();
			do{
				if(c != null){
					System.out.println(" (won't be shown)");
					send(new String(c.readPassword()), socketWriter);
				}else{
					System.out.println();
					send(r.readLine(), socketWriter);
				}
				if(Objects.equals(socketReader.readLine(), "OK")) correct = true;
				else print("Incorrect password, please insert a correct one.");
			}while(!correct);
			send("ACK", socketWriter);
			long uid = Long.parseLong(socketReader.readLine());
			send("ACK", socketWriter);
			long auth = Long.parseLong(socketReader.readLine());
			u = new User(name, uid, auth);
			send("ACK", socketWriter);
		}catch(IOException e){
			System.out.println("Some error occurred while connecting: Connection refused.");
			return new User("", -1, -1);
		}finally{
			if(SimpleSocialClient.user.getReqLock().isHeldByCurrentThread())
				SimpleSocialClient.user.getReqLock().unlock();
		}
		kaThread.start();
		return u;
	}

	/**
   	* This function implements the friendship request in the client.
   	* @param r the System.in.
   	* @return 0 if the request is successfully completed, -2 otherwise
   	*/
	public int request(BufferedReader r) {
		try(Socket server = new Socket(InetAddress.getByName(SimpleSocialClient.TCPIP), TCPPORT);
			BufferedWriter socketWriter = new BufferedWriter(new OutputStreamWriter(server.getOutputStream()));
			BufferedReader socketReader = new BufferedReader(new InputStreamReader(server.getInputStream()));){
			server.setTcpNoDelay(true);
			server.setSoTimeout(10000);
			//Handshake phase
			socketReader.readLine();
			send("3", socketWriter);
			socketReader.readLine();
			StringBuilder b = new StringBuilder();
			b.append(SimpleSocialClient.user.getUID());
			send(b.toString(), socketWriter);
			if(Objects.equals(socketReader.readLine(), "EXPIRED"))
				if(postLogIn(r, socketWriter, socketReader) == -1)
					return -1;
			print("Insert the username you want to become a friend:");
			String name = null;
			while((name = r.readLine().trim()).length() == 0)
				print("Invalid string: length must be >0. Try again.");
			//If the user tries to add himself as a friend the function returns 
			if(Objects.equals(SimpleSocialClient.user.getUserName(), name)){
				print("You can't add yourself as a friend.");
				send("EXIT", socketWriter);
				socketReader.readLine();
				return 0;
			}
			//If there's already a pending request the function returns
			SimpleSocialClient.user.getReqLock().lock();
			if(SimpleSocialClient.user.containsRequestClient(name)){
				print("You've already a pending request from "+name+". Check it with listRequests.");
				send("EXIT", socketWriter);
				socketReader.readLine();
				return 0;
			}
			SimpleSocialClient.user.getReqLock().unlock();
			send(name, socketWriter);
			String resp = socketReader.readLine();
			if(Objects.equals(resp, "NOUSER"))
				print("There are no registered users named "+name);
			else if(Objects.equals(resp, "OFFLINE"))
				print(name+" is offline, please try again later.");
			else if(Objects.equals(resp, "FRIEND"))
				print("You and "+name+" are already friends.");
			else if(Objects.equals(resp, "ALREADYREQ"))
				print("You've already sent a request to "+name);
			else if(Objects.equals(resp, "OK"))
				print("Your request was successfully sent to "+name);
			send("ACK", socketWriter);
		}catch(IOException e){
			System.out.println("Some error occurred while connecting: Connection refused.");
			return -2;
		}
		return 0;
	}

	/**
   	* This function implements the friendship request confirmation in the client.
   	* @param r the System.in.
   	* @return 0 if the request is successfully completed, -2 otherwise
   	*/
	public int confirmRequest(BufferedReader r) {
		SimpleSocialClient.user.getReqLock().lock();
		int size1 = SimpleSocialClient.user.requestSize();
		SimpleSocialClient.user.getReqLock().unlock();
		if(size1 == 0){
			print("You haven't pending requests.");
			return 0;
		}
		try(Socket server = new Socket(InetAddress.getByName(SimpleSocialClient.TCPIP), TCPPORT);
				BufferedWriter socketWriter = new BufferedWriter(new OutputStreamWriter(server.getOutputStream()));
				BufferedReader socketReader = new BufferedReader(new InputStreamReader(server.getInputStream()));){
			server.setTcpNoDelay(true);
			server.setSoTimeout(10000);
			//Handshake phase
			socketReader.readLine();
			send("4", socketWriter);
			socketReader.readLine();
			StringBuilder b = new StringBuilder();
			b.append(SimpleSocialClient.user.getUID());
			send(b.toString(), socketWriter);
			if(Objects.equals(socketReader.readLine(), "EXPIRED"))
				if(postLogIn(r, socketWriter, socketReader) == -1)
					return -1;
			listRequest();
			print("Type the name of the person whom you want to accept/refuse the friendship request in this format:"
					+ "\n<name> <Y/N>      [where Y is to accept and N is to refuse]");
			String resp = null;
			String[] respSplitted = null;
			boolean ok = false;
			do{
				 resp = r.readLine();
				 respSplitted = resp.split(" ");
				 if(respSplitted.length < 2) 
					 print("Wrong format.\nType the name of the person whom you want to accept/refuse the friendship request in this format:"
					+ "\n<name> <Y/N>      [where Y is to accept and N is to refuse]");
				 else if(!Objects.equals(respSplitted[respSplitted.length-1], "Y") && !Objects.equals(respSplitted[respSplitted.length-1], "N"))
					 print("Wrong format.\nType the name of the person whom you want to accept/refuse the friendship request in this format:"
								+ "\n<name> <Y/N>      [where Y is to accept and N is to refuse]");
				 else ok = true;
			}while(!ok);
			String name = "";
			for(int i  = 0; i < respSplitted.length-1; i++){
				if(i == 0)
					name = respSplitted[0];
				else name = name+" "+respSplitted[i];
			}
			String confirm = respSplitted[respSplitted.length-1];
			send(name, socketWriter);
			if(!Objects.equals(socketReader.readLine(), "NO")){
				send(confirm, socketWriter);
				socketReader.readLine();
				if(Objects.equals(confirm, "Y"))
					print(name+" is now your friend!");
				else print("Friendship request was refused.");
			}else print("There's no valid friendship request for you by "+name);
			SimpleSocialClient.user.getReqLock().lock();
			SimpleSocialClient.user.removeRequest(name);
			int size = SimpleSocialClient.user.requestSize();
			if(size == 0)
				Files.delete(Paths.get(dir+"/"+SimpleSocialClient.user.getUserName()+"Reqs.dat"));
			else
				try(BufferedWriter fileWriter = new BufferedWriter(new FileWriter(dir+"/"+SimpleSocialClient.user.getUserName()+"Reqs.dat", false))){
						for (int i = 0; i < size; i++){
							fileWriter.write(SimpleSocialClient.user.getRequest(i)+"\r\n");
							fileWriter.flush();
						}
				}catch(IOException e){
					print("Some error occurred in writing friendship requests: "+e.getMessage());
				}
			SimpleSocialClient.user.getReqLock().unlock();
		}catch(IOException e){
			System.out.println("Some error occurred while connecting: Connection refused.");
			return -2;
		}
		return 0;
	}

	/**
   	* This function lists the friendship requests the client has recieved and not accepted yet.
   	* @return 0 if the request is successfully completed.
   	*/
	public int listRequest(){
		SimpleSocialClient.user.getReqLock().lock();
		int size = SimpleSocialClient.user.requestSize();
		print("You have "+size+" (local)friendship request(s) pending:");
		for (int i = 0; i < size; i++) {
			print((i+1)+". "+SimpleSocialClient.user.getRequest(i));
		}
		SimpleSocialClient.user.getReqLock().unlock();
		return 0;
	}
	
	/**
   	* This function implements the user search in the client.
   	* @param r the System.in.
   	* @return 0 if the request is successfully completed, -2 otherwise
   	*/
	public int search(BufferedReader r) {
		try(Socket server = new Socket(InetAddress.getByName(SimpleSocialClient.TCPIP), TCPPORT);
			BufferedWriter socketWriter = new BufferedWriter(new OutputStreamWriter(server.getOutputStream()));
			BufferedReader socketReader = new BufferedReader(new InputStreamReader(server.getInputStream()));){
			server.setTcpNoDelay(true);
			server.setSoTimeout(10000);
			//Handshake phase
			socketReader.readLine();
			send("6", socketWriter);
			socketReader.readLine();
			StringBuilder b = new StringBuilder();
			b.append(SimpleSocialClient.user.getUID());
			send(b.toString(), socketWriter);
			if(Objects.equals(socketReader.readLine(), "EXPIRED"))
				if(postLogIn(r, socketWriter, socketReader) == -1)
					return -1;
			print("Insert the username(or part of it) you want to search:");
			String name = null;
			while((name = r.readLine().trim()).length() == 0)
				print("Invalid string: length must be >0. Try again.");
			send(name, socketWriter);
			int num = 0;
			if((num = Integer.parseInt(socketReader.readLine())) == 0){
				print("Sorry, no matches found for \""+name+"\"");
				send("ACK", socketWriter);
			}
			else{
				send("ACK", socketWriter);
				print(num+" matches found for \""+name+"\":");
				for(int i = 0; i < num; i++){
					print((i+1)+". "+socketReader.readLine());
					send("ACK", socketWriter);
				}
			}
		}catch(IOException e){
			System.out.println("Some error occurred while connecting: Connection refused.");
			return -2;
		}
		return 0;
	}

	/**
   	* This function lists the client's friends.
   	* @param r the System.in.
   	* @return 0 if the request is successfully completed, -2 otherwise
   	*/
	public int listFriends(BufferedReader r) {
		try(Socket server = new Socket(InetAddress.getByName(SimpleSocialClient.TCPIP), TCPPORT);
			BufferedWriter socketWriter = new BufferedWriter(new OutputStreamWriter(server.getOutputStream()));
			BufferedReader socketReader = new BufferedReader(new InputStreamReader(server.getInputStream()));){
			server.setTcpNoDelay(true);
			server.setSoTimeout(10000);
			//Handshake phase
			socketReader.readLine();
			send("7", socketWriter);
			socketReader.readLine();
			StringBuilder b = new StringBuilder();
			b.append(SimpleSocialClient.user.getUID());
			send(b.toString(), socketWriter);
			if(Objects.equals(socketReader.readLine(), "EXPIRED"))
				if(postLogIn(r, socketWriter, socketReader) == -1)
					return -1;
			int size = Integer.parseInt(socketReader.readLine());
			send("ACK", socketWriter);
			print("Your have "+size+" friends");
			for (int i = 0; i < size; i++) {
				print((i+1)+". "+socketReader.readLine());
				send("ACK", socketWriter);
			}
		}catch(IOException e){
			System.out.println("Some error occurred while connecting: Connection refused.");
			return -2;
		}
		return 0;
	}

	/**
   	* This function is used to post a content in the client.
   	* @param r the System.in.
   	* @return 0 if the request is successfully completed, -2 otherwise
   	*/
	public int post(BufferedReader r) {
		try(Socket server = new Socket(InetAddress.getByName(SimpleSocialClient.TCPIP), TCPPORT);
			BufferedWriter socketWriter = new BufferedWriter(new OutputStreamWriter(server.getOutputStream()));
			BufferedReader socketReader = new BufferedReader(new InputStreamReader(server.getInputStream()));){
			server.setTcpNoDelay(true);
			server.setSoTimeout(10000);
			//Handshake phase
			socketReader.readLine();
			send("8", socketWriter);
			socketReader.readLine();
			StringBuilder b = new StringBuilder();
			b.append(SimpleSocialClient.user.getUID());
			send(b.toString(), socketWriter);
			if(Objects.equals(socketReader.readLine(), "EXPIRED"))
				if(postLogIn(r, socketWriter, socketReader) == -1)
					return -1;
			print("Write your post: ");
			String post = null;
			while((post = r.readLine().trim()).length() == 0)
				print("Invalid string: length must be >0. Try again.");
			send(post, socketWriter);
			if(socketReader.readLine() == null) throw new IOException();
		}catch(IOException e){
			System.out.println("Some error occurred while connecting: Connection refused.");
			return -2;
		}
		return 0;
	}

	/**
	 * This function adds the currently logged user in some user's followers list
	 * @param r System.in
	 * @return 0 if the follow request is successfully processed, -2 if an IOError occurs.
	 * @throws IOException
	 */
	public int follow(BufferedReader r) throws IOException{
		try(Socket server = new Socket(InetAddress.getByName(SimpleSocialClient.TCPIP), TCPPORT);
				BufferedWriter socketWriter = new BufferedWriter(new OutputStreamWriter(server.getOutputStream()));
				BufferedReader socketReader = new BufferedReader(new InputStreamReader(server.getInputStream()));){
			server.setTcpNoDelay(true);
			server.setSoTimeout(10000);
			//Handshake phase
			socketReader.readLine();
			send("10", socketWriter);
			socketReader.readLine();
			StringBuilder b = new StringBuilder();
			b.append(SimpleSocialClient.user.getUID());
			send(b.toString(), socketWriter);
			if(Objects.equals(socketReader.readLine(), "EXPIRED"))
				if(postLogIn(r, socketWriter, socketReader) == -1)
					return -1;
			System.out.println("Please insert the userName you want to follow.");
			String name = null; boolean alreadyFoll = false;
			while((name = r.readLine().trim()).length() <= 0 || 
					Objects.equals(name, SimpleSocialClient.user.getUserName()) || 
					(alreadyFoll = SimpleSocialClient.user.findFollowed(name))){
				if(alreadyFoll){
					System.out.println("You already follow "+name);
					return 0;
				}
				else System.out.println("Wrong name. Please insert the userName you want to follow [userName length MUST be > 0 and you can't follow yourself!].");
			}
			if(SimpleSocialClient.user.getFollowMan().follow(SimpleSocialClient.user.getUserName(), name) == -1 ){
				System.out.println("Wrong name. There's no user called "+name);
				return 0;
			}
			SimpleSocialClient.user.getFollowedLock().lock();
			SimpleSocialClient.user.addFollowed(name);
			SimpleSocialClient.user.getFollowedLock().unlock();
			System.out.println("You're following "+name+"!");
		}catch(IOException e){
			System.out.println("Some error occurred while connecting: Connection refused.");
			return -2;
		}finally{
			if(SimpleSocialClient.user.getFollowedLock().isHeldByCurrentThread())
				SimpleSocialClient.user.getFollowedLock().unlock();
		}
		return 0;
	}
	
	/**
   	* This function logs out the client.
   	* @return 0 if the request is successfully completed, -2 otherwise
   	*/	
   	public int logout() {
		try(Socket server = new Socket(InetAddress.getByName(SimpleSocialClient.TCPIP), TCPPORT);
			BufferedWriter socketWriter = new BufferedWriter(new OutputStreamWriter(server.getOutputStream()));
			BufferedReader socketReader = new BufferedReader(new InputStreamReader(server.getInputStream()));){
			server.setTcpNoDelay(true);
			server.setSoTimeout(10000);
			//Handshake phase
			socketReader.readLine();
			send("9", socketWriter);
			socketReader.readLine();
			StringBuilder b = new StringBuilder();
			b.append(SimpleSocialClient.user.getUID());
			send(b.toString(), socketWriter);
			socketReader.readLine();
		}catch(IOException e){
			return -2;
		}
		return 0;
	}

	/**
   	* This function prints a message on the stdout.
   	* @param msg the message to be printed
   	*/
	private void print(String msg){
		System.out.println(msg);
	}
	
	/**
   	* This function sends msg to userWriter
   	* @param msg the message to be sent
   	* @param userWriter the BufferedWriter where to write msg
   	* @exception IOException On input error.
   	* @see IOException
   	*/
	private void send(String msg, BufferedWriter userWriter) throws IOException{
		userWriter.write(msg+"\r\n");
		userWriter.flush();
	}
	
	/**
   	* This function logs-in the user in case his authentication token expires.
   	* @param r the System.in
   	* @param socketWriter the BufferedWriter where to write all messages to the server
   	* @param socketReader the BufferedReader where to read all server's messages
   	* @return 0 if the request is successfully completed, -1 otherwise
   	* @exception IOException On input error.
   	* @see IOException
   	*/
	private int postLogIn(BufferedReader r, BufferedWriter socketWriter, BufferedReader socketReader) throws IOException{
		print("Your session is expired, please login.\nInsert your userName:");
		boolean correct = false;
		String name = null;
		do{
			send((name = r.readLine().trim()), socketWriter);
			if(Objects.equals(socketReader.readLine(), "OK")) correct = true;
			else print("Incorrect username, please insert a correct one.");
		}while(!correct);
		print("Insert the password for: "+name+" (won't be shown)");
		Console c = System.console();
		do{
			if(c != null){
				System.out.println(" (won't be shown)");
				send(new String(c.readPassword()), socketWriter);
			}else{
				System.out.println();
				send(r.readLine(), socketWriter);
			}
			if(Objects.equals(socketReader.readLine(), "OK")) correct = true;
			else print("Incorrect password, please insert a correct one.");
		}while(!correct);
		send("ACK", socketWriter);
		long auth = Long.parseLong(socketReader.readLine());
		SimpleSocialClient.user.settAuth(auth);	
		Registry reg = LocateRegistry.getRegistry("localhost", SimpleSocialClient.RMISERVERPORT);
		RMIServerOp followManager  = null;
		try {
			followManager = (RMIServerOp) reg.lookup(RMIServerOp.NAME);
		} catch (NotBoundException e) {
			System.out.println("Could not bind to RMI server");
			send("ERR", socketWriter);
			return -1;
		}
		SimpleSocialClient.user.setFollMan(followManager);
		SimpleSocialClient.user.getFollowMan().login(SimpleSocialClient.user.getUserName(), SimpleSocialClient.user.getUserStub(), SimpleSocialClient.tcpAccept.getLocalPort());
		send("ACK", socketWriter);
		return 0;
	}

}
