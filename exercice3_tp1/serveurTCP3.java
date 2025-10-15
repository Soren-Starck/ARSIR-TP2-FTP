import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class serveurTCP3 {
    public static void main(String[] args) {
        final int PORT = 3000; // n'importe quel port libre
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Serveur : démarré, en attente d'un client sur le port " + PORT + "...");

            // Accepter une seule connexion client
            try (Socket socket = serverSocket.accept();
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                System.out.println("Serveur : client connecté => " + socket.getInetAddress());

                // Envoyer l'heure actuelle
                String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                out.println(currentTime);

                System.out.println("Serveur : heure envoyée et fermeture du serveur.");
            }

            // Après la fermeture du try-with-resources, le socket et le serverSocket sont fermés automatiquement
            System.out.println("Serveur : fermé.");

        } catch (IOException e) {
            System.err.println("Exception serveur : " + e.getMessage());
        }
    }
}