import java.io.*;
import java.net.*;

public class clientTCP3 {
    public static void main(String[] args) {
        final String SERVER_ADDRESS = "localhost"; // IP de la machine serveur
        final int SERVER_PORT = 3000;

        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            System.out.println("Client : connecté, en attente de l'heure actuelle...");
            String serverTime = in.readLine();
            System.out.println("Client : heure actuelle reçue du serveur : " + serverTime);

        } catch (IOException e) {
            System.err.println("Exception client : " + e.getMessage());
        }
    }
}