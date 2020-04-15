import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.time.LocalTime;

public class Server {
    //ArrayList to store the online client threads
    private static CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<ClientHandler>();
    // ArrayList to store server blocks
    private static ArrayList<String> serverBlockedList = new ArrayList<String>();
    // HashMap to store log for clients
    private static ConcurrentHashMap<String, LocalTime> log = new ConcurrentHashMap<String, LocalTime>();
    // HashMap to store client blocks
    private static HashMap<String, ArrayList<String>> clientBlockMap = new HashMap<String, ArrayList<String>>();
    // HashMap to store offline messages
    private static HashMap<String, ArrayList<String>> offlineMsgMap = new HashMap<String, ArrayList<String>>();
    private static int timeout = 0;
    private static int blockTime = 0;

    public static void main(String[] args) throws IOException {
        // get the arguments 
        if (args.length != 3){
            System.out.println("Required arguments: Server port, Block durtion, Timeout");
            return;
        }    
        
        // Setting the varibles up
        int port = Integer.parseInt(args[0]);
        blockTime = Integer.parseInt(args[1]);
        timeout = Integer.parseInt(args[2]);
        
        //create listener to listen
        ServerSocket listener = new ServerSocket(port); 
        while (true) {
            // accept the client              
            Socket clientSocket = listener.accept();

            // get the input and output streams 
            DataInputStream input = new DataInputStream(clientSocket.getInputStream()); 
            DataOutputStream output = new DataOutputStream(clientSocket.getOutputStream());

            // construct a new client handler
            ClientHandler client = new ClientHandler(clientSocket, input, output);  
            //add a new client into the ArrayList
            clients.add(client);

            // create a thread & run it 
            Thread clientThread = new Thread(client); 
            //start the thread
            clientThread.start();

            //create a new thread that makes sure the clients threads are correct
            Thread array = new Thread(new Runnable()  { 
                @Override
                public void run() { 
                    try {
                        //wait until a client logout
                        clientThread.join();
                    } catch (InterruptedException ie) {
                        System.out.println(ie);
                    }
                    //remove it after 
                    clients.remove(client);
                }  
            }); 
            
            array.start();
        }
    }

    //getters
    public static CopyOnWriteArrayList<ClientHandler> getClients() {
        return clients;
    }

    public static ArrayList<String> getServerBlockedList() {
        return serverBlockedList;
    }

    public static ConcurrentHashMap<String, LocalTime> getLog() {
        return log;
    }

    public static HashMap<String, ArrayList<String>> getClientBlockMap() {
        return clientBlockMap;
    }

    public static HashMap<String, ArrayList<String>> getofflineMsgMap() {
        return offlineMsgMap;
    }

    public static int getTimeout() {
        return timeout;
    }

    public static int getBlockTime() {
        return blockTime;
    }

}