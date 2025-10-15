package exercice1;

import java.net.*;
import java.util.Scanner;


public class clientUDP {
    public static void main(String[] args) throws Exception {
        boolean open = true;
        while (open) {
            DatagramSocket socket = new DatagramSocket();
            Scanner scanner = new Scanner(System.in);
            System.out.print("Entrez un message : ");
            String message = scanner.nextLine();        
        
            byte[] buffer = message.getBytes();

            if (message.equals("SHUTDOWN")) {
                // Cas de commande d'extinction
                open = false;

            }
        
            // Autres cas
            InetAddress adresse = InetAddress.getByName("localhost");
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, adresse, 12345);
            socket.send(packet);

            // Réception de la réponse
            byte[] bufferReponse = new byte[1024];
            DatagramPacket reponsePacket = new DatagramPacket(bufferReponse, bufferReponse.length);
            socket.receive(reponsePacket);

            String reponse = new String(reponsePacket.getData(), 0, reponsePacket.getLength(), "UTF-8");
            System.out.println("Réponse du serveur: " + reponse);
            
        socket.close();
        }
    }
}