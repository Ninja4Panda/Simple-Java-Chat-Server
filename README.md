# Program Design
The approach taken was having one Server that keeps running and create new handler threads for every client that connects to the server. The handler handles all functionality for each client. Two threads from the client side handling the sending and receiving of messages both system and other users.

# Application Layer Message Format
There are two types of message format:
1. From System (starts with >)
   
   **Scenario:**\
   ---After login---
   >\>Welcome to the greatest messaging...

2. From user (sender: message)
   
   **Scenario:**\
   ---A messaging B & on B screen---
   >A: Hi              

# Brief Description
When the server begins, it sits and listen for clients to join. 

After a client joins, a new thread is created. Every client thread created is added into an ArrayList that is kept and maintained by the server. The thread then prompt the user to input credentials. A greeting and help messages will pop up for the user. 

An interesting functionality would be blocking for a period of time after 3 consecutive failed attempts. It opens a new thread to keep track of the time and add or remove the user from the blocklist that is stored in the server. 

Some ambiguities were also evidence in the assignment such as the user blocking. The approach that made the most sense would be if A blocked B. B can't message A and get presence notification of A. But A also can't message B and get notifcation of B. As it doesn't make sense to have a one-way communcation. 

# Desgin Tradeoffs
For the logout due to inactivity, a new thread was created to keep track of the time. After timeout is reached user is directly logged out in the new thread instead of signalling the main thread to log out causing the main thread to keep running for a litte longer even though the client is already logged out. 

Timeout only begins after the login process is done so that user can take their time when logging in. 
An edge case was accounted for as a new thread is created before the user even logged in hence a the name cannot be determined. To make sure there won't be any null pointer expection when comparing client names. An if statement was adding beforehand.   

Assuming that the inactive timeout is longer than 5 seconds, a 5 seconds warning will prompt if user is about to be logged out.

# Improvements And Extensions
Create back up files in case server goes down and all clients data will be lost.
Some functionality can be implemented in the client side to decrease load on server.
A threadID can be used to distinish between each thread instead of using names.  

# Reference
* https://www.journaldev.com/378/java-util-concurrentmodificationexception
* https://www.geeksforgeeks.org/multi-threaded-chat-application-set-2/ 
* https://www.javaspecialists.eu/archive/Issue056.html
* http://pirate.shu.edu/~wachsmut/Teaching/CSAS2214/Virtual/Lectures/chat-client-server.html
