import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class Serveur {
    private static final int PORT = 12345;

    public static void main(String[] args) {
        System.out.println("Serveur de capitalisation demarre sur le port " + PORT);

        try (ServerSocket serveurSocket = new ServerSocket(PORT)) {
            while (true) {
                System.out.println("En attente d'un client...");

                try (Socket socketClient = serveurSocket.accept()) {
                    System.out.println("Client connecte depuis " + socketClient.getInetAddress());

                    gererClient(socketClient);

                    System.out.println("Client deconnecte");
                } catch (IOException e) {
                    System.err.println("Erreur lors de la gestion du client: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur du serveur: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void gererClient(Socket socketClient) throws IOException {
        BufferedReader entree = new BufferedReader(
            new InputStreamReader(socketClient.getInputStream())
        );
        PrintWriter sortie = new PrintWriter(socketClient.getOutputStream(), true);

        String ligne;
        while ((ligne = entree.readLine()) != null) {
            System.out.println("Recu: " + ligne);

            String ligneCapitalisee = ligne.toUpperCase();

            System.out.println("Envoye: " + ligneCapitalisee);
            sortie.println(ligneCapitalisee);
        }
    }
}
