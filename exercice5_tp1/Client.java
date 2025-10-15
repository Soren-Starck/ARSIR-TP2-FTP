import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    private static final String HOTE = "localhost";
    private static final int PORT = 12345;

    public static void main(String[] args) {
        System.out.println("Client de capitalisation");
        System.out.println("Connexion au serveur " + HOTE + ":" + PORT);

        try (
            Socket socket = new Socket(HOTE, PORT);
            BufferedReader entree = new BufferedReader(
                new InputStreamReader(socket.getInputStream())
            );
            PrintWriter sortie = new PrintWriter(socket.getOutputStream(), true);
            Scanner scannerUtilisateur = new Scanner(System.in)
        ) {
            System.out.println("Connecte au serveur");
            System.out.println("Entrez des lignes de texte (Ctrl+D ou tapez 'EXIT' pour quitter):");
            System.out.println();

            Thread threadLecture = new Thread(() -> {
                try {
                    String reponse;
                    while ((reponse = entree.readLine()) != null) {
                        System.out.println(reponse);
                    }
                } catch (IOException e) {
                    // Connexion fermee
                }
            });
            threadLecture.start();

            while (scannerUtilisateur.hasNextLine()) {
                String ligne = scannerUtilisateur.nextLine();

                if (ligne.equalsIgnoreCase("EXIT")) {
                    break;
                }

                sortie.println(ligne);
            }

            System.out.println("\nDeconnexion...");

        } catch (IOException e) {
            System.err.println("Erreur de connexion: " + e.getMessage());
        }
    }
}
