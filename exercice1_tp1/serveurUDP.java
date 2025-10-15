package exercice1;

import java.net.*;
import java.util.Dictionary;
import java.util.Hashtable;

public class serveurUDP {

    public static void main(String[] args) throws Exception {
    
        Dictionary<String, String> id = new Hashtable<>();
        id.put("anonymous", "");
        id.put("foo", "bar");
    
        boolean server_opened = true;
        boolean logged_in = false;
        String current_user = null;


        while (server_opened) { 
            DatagramSocket socket = new DatagramSocket(12345); // Port d'écoute
            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            System.out.println("Serveur en attente...");
            socket.receive(packet); // Attend un message

            String message = new String(packet.getData(), 0, packet.getLength());
            System.out.println("Reçu du client: " + message);

                String command = "";
                String contain = "";

            if (message.split(" ").length <= 1) {
                command = message.split(" ")[0];
            
            } else if (message.split(" ").length >= 2) {
                contain = message.split(" ")[1];
                command = message.split(" ")[0];
            }   
            
            String reponse = "";
            // Traitement du message

            try {
            if (!logged_in) {
                if (command.equals("USER")) {
                    if (id.get(contain) != null) {
                        current_user = contain;
                        reponse = "331 User name okay, need password.";
                        System.out.println("331 User name okay, need password.");
                    } else {
                        reponse = "530 Not logged in.";
                        System.out.println("530 Not logged in (user not found).");
                    }

                } else if (command.equals("PASS")) {
                    if (id.get(current_user).equals(contain)) {
                        reponse = "230 User logged in, proceed.";
                        System.out.println("230 User logged in, proceed.");
                        logged_in = true;
                    } else {
                        if (id.get(current_user) == null) {
                            reponse = "530 Not logged in.";
                            System.out.println("530 Not logged in (no password existing).");
                        } else {
                            reponse = "530 Not logged in.";
                            System.out.println("530 Not logged in (wrong password).");}
                            System.out.println("current user: " + current_user);
                            System.out.println("given password: " + contain);
                            System.out.println("wanted password: " + id.get(current_user));
                            
                            
                    }

                } 
            }
            
            
            else {
                if (command.equals("QUIT")) {
                    reponse = "221 Service closing control connection.";
                    System.out.println("221 Service closing control connection.");
                    server_opened = false;
                } else {
                    reponse = "403 Access denied.";
                    System.out.println("403 Access denied.");
                }
            }
            
            if (command.equals("SHUTDOWN")) {

                reponse = "0x0000003B Server turning off.";
                System.out.println("0x0000003B Server turning off.");
                server_opened = false;
            }

        } catch (ArrayIndexOutOfBoundsException e) {
            reponse = "500 Syntax error, command unrecognized.";
            System.out.println("500 Syntax error, command unrecognized.");
        }
            // Réponse

            byte[] reponseBytes = reponse.getBytes();
            DatagramPacket reponsePacket = new DatagramPacket(
                reponseBytes, reponseBytes.length, packet.getAddress(), packet.getPort());
            socket.send(reponsePacket);
            
            socket.close();
        }
        
    }
}