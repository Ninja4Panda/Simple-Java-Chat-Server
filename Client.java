import java.io.*;
import java.net.*;
import java.util.*;

public class Client {
    public static void main(String[] args) throws Exception {
        // get the arguments 
        if (args.length != 2){
            System.out.println("Required arguments: Server IP, Server port");
            return;
        }    

        // Setting up host & port
        String ip = args[0];        
        int port = Integer.parseInt(args[1]);
        
        // Create socket which connects to the server
        Socket server = new Socket(ip, port);

        // get the input and output streams & setup the scanner  
        Scanner scn = new Scanner(System.in); 
        DataInputStream input = new DataInputStream(server.getInputStream()); 
        DataOutputStream output = new DataOutputStream(server.getOutputStream());

        // seperate the read & send processes so that it can be done simultaneously
        // sendMessage thread 
        Thread sendMessage = new Thread(new Runnable()  { 
            @Override
            public void run() { 
                while (!Thread.currentThread().isInterrupted()) { 
                    // read the message to deliver. 
                    String msg = " ";
                    if (scn.hasNextLine()) msg = scn.nextLine(); 
                    try { 
                        // write on the output stream 
                        output.writeUTF(msg); 
                    } catch (IOException e) { 
                        e.printStackTrace(); 
                    } 
                } 
            } 
        }); 

        // readMessage thread 
        Thread readMessage = new Thread(new Runnable()  { 
            @Override
            public void run() { 
                while (true) { 
                    try { 
                        // read the message sent to this client 
                        String msg = input.readUTF(); 
                        if ("THE END IS NEAR".equals(msg)) break;
                        System.out.print(msg);  
                    } catch (IOException e) { 
                        System.out.println(">Sorry! The Server is currrently down due to some technical issues"); 
                        System.out.println(">Please press enter to exit");
                        break;
                    }
                } 
            } 
        }); 

        // start the threads
        sendMessage.start(); 
        readMessage.start(); 

        //wait until read is finished then finish the send thread
        readMessage.join();
        sendMessage.interrupt();
    }
}