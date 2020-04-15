import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Arrays;
import java.time.LocalTime;
import java.time.Duration;
import java.util.concurrent.*;

public class ClientHandler implements Runnable { 
    private Scanner scn = new Scanner(System.in); 
    private Socket clientSocket;
    private String name = null; 
    private final DataInputStream input; 
    private final DataOutputStream output;  
    private boolean isLoggedIn; 

    //constructor setting the client thread up
    public ClientHandler(Socket clientSocket, DataInputStream input, DataOutputStream output) throws IOException {
        this.clientSocket = clientSocket;
        this.input = input;
        this.output = output;
        this.isLoggedIn = false;
    }

    //runnable that executes when thread starts
    @Override
    public void run() {
        try {
            this.isLoggedIn = login();
            while (this.isLoggedIn) {
                //start a check timeout thread
                Thread timeoutThread = checkTimeout(this);
                timeoutThread.start();    
                String userInput = this.input.readUTF();
                // interrput the timeout 
                timeoutThread.interrupt();
                // split the user input 
                String[] splitted = userInput.split(" ");
                String command = splitted[0];
                switch (command) {
                    case "HELP":
                        help(userInput);
                        break;
                    case "message":
                        message(userInput);
                        break;
                    case "broadcast":
                        broadcast(userInput);
                        break;
                    case "whoelse":
                        println(">Online Users:", this.output);
                        whoelse(userInput);
                        break;
                    case "whoelsesince":
                        whoelsesince(userInput);
                        break;
                    case "block":
                        block(userInput);
                        break; 
                    case "unblock":
                        unblock(userInput);
                        break; 
                    case "logout":
                        logout(userInput);
                        break;
                    default:
                        println(">Error. Invalid command", this.output);
                        break;
                }
            }
            // press enter so the thread will finish
            println(">Please press enter to exit", this.output);
            //signal the client to end
            print("THE END IS NEAR", this.output);
            //close all the streams & sockets
            try {
                this.input.close();
                this.output.close();
                this.clientSocket.close();
            } catch(IOException ioe) {
                ioe.printStackTrace();
            }
        } catch (IOException ioe) {
            System.out.println(">A Client disconnected unexpectedly");
        } 
    } 
    
    /** LOGIN FUNCTIONS */
    // function that takes care of the login process 
    public boolean login() throws IOException{
        String username;
        while(true) {
            // prompt & read the user input the username
            print(">Username: ", this.output);
            username = this.input.readUTF();
            // if the username is correct move on to the password section 
            if (auth(username)) {
                break;
            }
            println(">Invalid Username. Please try again", this.output);
        }

        // limit user to enter the incorrent password 3 times
        for (int times = 0; times < 3; times++) {
            // prompt & read the user input the password
            print(">Password: ", this.output);
            String password = this.input.readUTF();
            // if the username and password are both correct
            if (auth(username, password)) {
                if(online(username)) {  // if the user is online
                    println(">Error. User is alreay online", this.output);
                    return false;
                }
                if(checkServerBlocked()) { // if the user is blocked due to login faulures
                    println(">Your account is blocked due to multiple login failures. Please try again later.", this.output);
                    return false;
                }       
                println(">Welcome to the greatest messaging application ever!", this.output);
                println(">Type HELP to view available commands", this.output);
                //set the name to username
                this.name = username; 
                //notify everyone else that this user is online
                sendToEveryone(username, ">"+ username +" has logged in.");
                //check the offline messages
                checkOfflineMsg();
                return true;
            } else {
                println(">Invalid Password. Please try again", this.output);
            }
        }

        //block the user for a period of time
        serverBlock(username);
        println(">Your account is blocked due to multiple login failures. Please try again later.", this.output);
        return false;
    }

    /** HELP */
    public void help(String userInput) throws IOException {
        String[] splitted = userInput.split(" ");
        if(splitted.length != 1) {
            println(">Error use of help. Usage:help", this.output);
            return;
        }
        println(">Direct message to user. Usage:message <user> <message>", this.output);
        println(">Broadcast to every other user online. Usage:broadcast <message>", this.output);
        println(">Display all other online users. Usage:whoelse", this.output);
        println(">Display all users that logged in since the time. Usage:whoelsesince <time>", this.output);
        println(">Block user. Usage:block <user>", this.output);
        println(">Unblock user. Usage:unblock <user>", this.output);
        println(">Logout. Usage:logout", this.output);
    }

    /** MESSAGE */
    public void message(String userInput) throws IOException{
        String[] splitted = userInput.split(" ");
        if(splitted.length < 3) {
            println(">Error use of message. Usage:message <user> <message>", this.output);
            return;
        }
        // set the recipient
        String recipient = splitted[1];
        if (!auth(recipient)) {
            println(">Error."+ recipient +" is not a valid user!", this.output);
            return;
        } else if(recipient.equals(this.name)) {
            println(">Error.Cannot send message to yourself!", this.output);
            return;
        } else if (isBlocked(recipient, this.name)) {
            println(">Message could not be delivered as " + recipient + " has blocked you", this.output);
            return;
        } else if (isBlocked(this.name, recipient)) {
            println(">Message could not be delivered as " + recipient + " was blocked by you", this.output);
            return;
        }

        // obtain the message by joinning the remaining string
        String array[] = Arrays.copyOfRange(splitted, 2, splitted.length);
        String message = String.join(" ", array);
        message = this.name + ": " + message;
        
        //send the message
        for (ClientHandler client: Server.getClients()) { 
            if (client.isLoggedIn) { // if client is online send it to them
                println(message, client.output);
                break; 
            } else { //if not save it in the offline message
                //get the blocked hashmap
                HashMap hash = Server.getofflineMsgMap();
                if (hash.containsKey(recipient)) {
                    //add the target into the blacklist by first typecasting
                    ((ArrayList<String>) hash.get(recipient)).add(message);
                } else {
                    //create a new array list 
                    ArrayList<String> list = new ArrayList<String>();
                    //add the message into the list & put it in the offlineMap
                    list.add(message);
                    hash.put(recipient, list);
                }
                break;
            }
        }
    } 

    /** BROADCAST */
    //broadcast message to everyone excluding blocked or being blocked 
    public void broadcast(String userInput) throws IOException {
        String[] splitted = userInput.split(" ");
        if(splitted.length < 2) {
            println(">Error use of message. Usage:broadcast <message>", this.output);
            return;
        }
        // obtain the message by joinning the remaining string
        String array[] = Arrays.copyOfRange(splitted, 1, splitted.length);
        String message = this.name + ":" + String.join(" ", array);
        sendToEveryone(this.name, message);
        
        // let the user know that message was not send to some online recipients
        for (ClientHandler client: Server.getClients()) {
            if(isBlocked(client.name, this.name) || isBlocked(this.name, client.name)) {
                println(">Your message could not delivered to some recipients", this.output); 
                break;
            }
        }
    }

    /** WHOELSE */
    // show a list of clients that's online excluding the client self & users that blocked this client
    public void whoelse(String userInput) throws IOException {
        String[] splitted = userInput.split(" ");
        if (splitted.length != 1) {
            println(">Error use of whoelse. Usage:whoelse", this.output);
            return;
        }

        //show the user who else is online right now
        for(ClientHandler client: Server.getClients()) {            
            if (!client.isLoggedIn) continue;
            if(!(this.name.equals(client.name)) && !isBlocked(this.name, client.name) && !isBlocked(client.name, this.name)){
                println(client.name, this.output);
            }
        }
    }

    /** WHOELSESINCE */
    // show a list of clients that was online for the period of time
    public void whoelsesince(String userInput) throws IOException {
        String[] splitted = userInput.split(" ");
        if(splitted.length != 2) {
            println(">Error use of whoelsesince. Usage:whoelsesince <time>", this.output);
            return;
        }
        //test if the time is a long
        try {
            long timeout = Long.parseLong(splitted[1]);
            //show the users
            println(">Users that was online from now to " + splitted[1] + " second ago:", this.output);
            //calculate the time
            LocalTime now = LocalTime.now(); 
            LocalTime time = now.minus(Duration.ofSeconds(timeout));

            //get the log data
            ConcurrentHashMap logs = Server.getLog();
            //call whoelse to print the people that is currently online
            whoelse("whoelse");
            logs.forEach((key, value) -> printWhoelesince(key, value, time));
        } catch (NumberFormatException nfe) {
            println(">Error. Time has to be numbers in seconds", this.output);
        }  
    }

    // print the user that was online within the time frame
    private void printWhoelesince(Object key, Object value, LocalTime time) {
        try {
            // if user isn't online now but was online within the time frame
            LocalTime logoutTime = LocalTime.parse(value.toString());
            String name = key.toString();
            if (!online(name) && time.isBefore(logoutTime) && !isBlocked(this.name, name) && !isBlocked(name, this.name)) {
                println(name, this.output);
            }
        } catch (IOException ioe) {
            System.out.println(ioe);
        }
    }

    /** BLOCK */
    //block a user
    public void block(String userInput) throws IOException {
        String[] splitted = userInput.split(" ");
        if(splitted.length != 2) {
            println(">Error use of block. Usage: block <user>", this.output);
            return;
        }

        // setting the target to block
        String target = splitted[1];
        if (!auth(target)) {
            println(">Error."+ target +" is not a valid user!", this.output);
            return;
        } else if(target.equals(this.name)) {
            println(">Error. Cannot block yourself!", this.output);
            return;
        } else if (isBlocked(this.name, target)) {
            println(">Error. "+target+" was already blocked", this.output);
            return;
        }

        // prompt for user to confirm
        while (true) {
            println(">Block "+target+"? yes/no", this.output);
            //start a timeout thread
            Thread timeoutThread = checkTimeout(this);
            timeoutThread.start();
            String choice = this.input.readUTF();
            // interrput the timeout 
            timeoutThread.interrupt();
            if ("no".equals(choice)) {
                println(">Action has been cancelled", this.output);
                return;
            } else if("yes".equals(choice)) {
                break;
            }
        }

        //get the blocked hashmap
        HashMap hash = Server.getClientBlockMap();
        //find this user
        if (hash.containsKey(this.name)) {
            //add the target into the blacklist by first typecasting
            ((ArrayList<String>) hash.get(this.name)).add(target);
        } else {
            //create a new array list 
            ArrayList<String> list = new ArrayList<String>();
            //add the target into the blacklist
            list.add(target);
            hash.put(this.name, list);
        }
        println(">"+target+" is now blocked", this.output);
    }

    /** UNBLOCK  */
    //unblock a user that was blocked before
    public void unblock(String userInput) throws IOException {
        String[] splitted = userInput.split(" ");
        if(splitted.length != 2) {
            println(">Error use of unblock. Usage: unblock <user>", this.output);
            return;
        }

        // setting the target to unblock
        String target = splitted[1];
        if (!auth(target)) {
            println(">Error."+ target +" is not a valid user!", this.output);
            return;
        } else if(target.equals(this.name)) {
            println(">Error. Cannot unblock yourself!", this.output);
            return;
        } else if (!isBlocked(this.name, target)) {
            println(">Error. "+target+" was never blocked", this.output);
            return;
        }

        // prompt for user to confirm
        while (true) {
            println(">Unblock "+target+"? yes/no", this.output);
            //start a timeout thread
            Thread timeoutThread = checkTimeout(this);
            timeoutThread.start();
            String choice = this.input.readUTF();
            // interrput the timeout 
            timeoutThread.interrupt();
            if ("no".equals(choice)) {
                println(">Action has been cancelled", this.output);
                return;
            } else if("yes".equals(choice)) {
                break;
            }
        }

        //get the block hashmap
        HashMap hash = Server.getClientBlockMap();
        //remove the target from the blacklist by first typecasting
        ((ArrayList<String>) hash.get(this.name)).remove(target);

        //let the user know target is now unblocked
        println(">"+target+" is now unblocked", this.output);
    }

    /** LOGOUT */
    public void logout(String userInput) throws IOException {
        String[] splitted = userInput.split(" ");
        if(splitted.length != 1) {
            println(">Error use of logout. Usage:logout", this.output);
            return;
        }

        //setting the logged in flag to false
        this.isLoggedIn = false;
        sendToEveryone(this.name, ">"+ this.name +" has logged out.");
        println(">You have been logged out", this.output);
        
        // store the logout time
        ConcurrentHashMap log = Server.getLog();
        LocalTime logoutTime = LocalTime.now();
        if (log.containsKey(this.name)) {
            // replace the old key with the new logout time
            log.replace(this.name, logoutTime);
        } else {
            // add the new key value pair into the log
            log.put(this.name, logoutTime);
        }
    }

    /** HELPER */
    // faster print
    private void print(String msg, DataOutputStream stream) throws IOException {
        stream.writeUTF(msg);
    }

    // faster print with new line
    private void println(String msg, DataOutputStream stream) throws IOException {
        stream.writeUTF(msg);
        stream.writeUTF("\n");
    }

    // block the user by name
    private void serverBlock(String name) {
        Thread block = new Thread(new Runnable()  { 
            @Override
            public void run() { 
                // add the user to the blocklist
                Server.getServerBlockedList().add(name);
                try {
                    //make it wait for the blocktime
                    Thread.sleep(Server.getBlockTime()*1000);
                } catch (InterruptedException ie) {
                    System.out.println(ie);
                }
                //remove it after 
                Server.getServerBlockedList().remove(name);
            }  
        }); 
        //start the thread only if the user was not online & already blocked 
        if(!online(name) && !checkServerBlocked()) block.start();
    }

    // check if the user is blocked by server
    private boolean checkServerBlocked() {
        if (Server.getServerBlockedList().contains(this.name)) {
            return true;    
        } 
        return false;
    }

    // checks if there is any msgs while user is offline and show it
    private void checkOfflineMsg() throws IOException {
        HashMap hash = Server.getofflineMsgMap();
        if (hash.containsKey(this.name)) {
            ArrayList<String> offlineMsgList = (ArrayList<String>) hash.get(this.name);
            if (offlineMsgList.size() == 0) {
                println(">No message received while offline", this.output);
            } else {
                println(">Message received while offline:", this.output);
                for (String msg: offlineMsgList) {
                    println(msg, this.output);
                }
                offlineMsgList.clear();
            }
        } else {
            println(">No message received while offline", this.output);
        }
    }

    // send to everyone that is online and has not blocked sender or blocked by user 
    private void sendToEveryone(String sender, String msg) throws IOException{
        //send the message 
        for (ClientHandler client: Server.getClients()) {
            if(online(client.name) && !(sender.equals(client.name)) && !isBlocked(client.name, sender) && !isBlocked(sender, client.name)) {
                println(msg, client.output); 
            }
        }
    }

    // function that checks if the user is already online 
    private boolean online(String name) {
        for (ClientHandler client: Server.getClients()) {
            if (client.name == null) continue;
            if (client.name.equals(name)) {
                return client.isLoggedIn;
            }
        }
        return false;
    }

    //check if target is blocked by user
    private boolean isBlocked(String user, String target) {
        HashMap hash = Server.getClientBlockMap();
        if (hash.containsKey(user)) {
            ArrayList<String> blacklist = (ArrayList<String>) hash.get(user);
            if (blacklist.contains(target)) return true;
        }
        return false;
    }

    // overloaded function that checks if the username is valid
    private boolean auth(String username) throws IOException {
        BufferedReader fr = new BufferedReader(new FileReader("credentials.txt"));
        String st;
        //read the credentials.txt 
        while ((st = fr.readLine()) != null) {
            String[] splitted = st.split(" ");
            String authUsername = splitted[0];
            if (authUsername.equals(username)) return true;
        }
        // The credentials did not match
        return false;
    }

    // overloaded function that checks if the username & password is valid
    private boolean auth(String username, String password) throws IOException {
        BufferedReader fr = new BufferedReader(new FileReader("credentials.txt"));
        String st;
        //read the credentials.txt 
        while ((st = fr.readLine()) != null) {
            String[] splitted = st.split(" ");
            String authUsername = splitted[0];
            String authPassword = splitted[1];
            if (authUsername.equals(username) && authPassword.equals(password)) return true;
        }
        // The credentials did not match
        return false;
    }
    
    // check timeout and logout the user out 
    private Thread checkTimeout(ClientHandler client) {
        Thread timeout = new Thread(new Runnable()  { 
            @Override
            public void run() { 
                try {
                    //make it wait for the timeout
                    Thread.sleep((Server.getTimeout()-5)*1000);
                    client.println(">You will be logged out in 5 seconds due to inactivity!", client.output);
                    Thread.sleep(5*1000);
                    //log the user out
                    client.logout("logout");
                    client.println(">Please press enter to exit", client.output);
                    client.print("THE END IS NEAR", client.output);
                } catch (InterruptedException ie) {
                    //interrupted exception is expected from main thread 
                } catch (IOException ioe) {
                    System.out.println(ioe);
                }
            }
        });
        return timeout;
    }
}   