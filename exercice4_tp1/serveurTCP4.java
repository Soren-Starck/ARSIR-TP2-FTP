import java.io.*;
import java.net.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class serveurTCP4 {
    public static void main(String[] args) {
        final int PORT = 3000;

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Serveur : démarré, en attente d'un client...");

            // Accepter une connexion client
            try (Socket socket = serverSocket.accept();
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                System.out.println("Serveur : client connecté => " + socket.getInetAddress());

                String command;
                // Boucle de traitement des commandes du client
                while ((command = in.readLine()) != null) {
                    System.out.println("Serveur : commande reçue => " + command);

                    switch (command.toUpperCase()) {
                        case "DATE":
                            String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                            out.println(date);
                            break;
                        case "HOUR":
                            String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                            out.println(time);
                            break;
                        case "FULL":
                            String full = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                            out.println(full);
                            break;
                        case "CLOSE":
                            out.println("au revoir !");
                            System.out.println("Serveur : déconnexion demandée par le client.");
                            return; // Sort de la boucle et ferme le serveur
                        default:
                            out.println("Serveur : commande inconnue");
                    }
                }
            }
            // Fermeture du serveur après la déconnexion du client
            System.out.println("Serveur : fermé.");
        } catch (IOException e) {
            System.err.println("Exception serveur => " + e.getMessage());
        }
    }
}