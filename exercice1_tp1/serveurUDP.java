package exercice1;

import java.net.*;
import java.time.LocalDateTime;


public class serveurUDP {
    public static void main(String[] args) throws Exception {
        boolean server_opened = true;
        while (server_opened) { 
            DatagramSocket socket = new DatagramSocket(12345); // Port d'écoute
            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            System.out.println("Serveur en attente...");
            socket.receive(packet); // Attend un message

            String message = new String(packet.getData(), 0, packet.getLength());
            System.out.println("Reçu du client: " + message);

            // Réponse

            String maintenant = LocalDateTime.now().toString();
            String date = maintenant.split("T")[0];
            String heure = maintenant.split("T")[1];
            String reponse = "Date : " + date + ", Heure : " + heure;
            byte[] reponseBytes = reponse.getBytes();
            DatagramPacket reponsePacket = new DatagramPacket(
                reponseBytes, reponseBytes.length, packet.getAddress(), packet.getPort());
            socket.send(reponsePacket);
            
            if (message.equals("stop")) {
                server_opened = false;
            }
            socket.close();
        }
        
    }
}