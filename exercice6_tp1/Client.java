import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    private static final String HOTE = "localhost";
    private static final int PORT = 12345;

    private Socket socket;
    private BufferedReader entree;
    private PrintWriter sortie;
    private Scanner scannerUtilisateur;
    private char monSymbole;

    public Client() {
        scannerUtilisateur = new Scanner(System.in);
    }

    public void connecter() {
        try {
            socket = new Socket(HOTE, PORT);
            entree = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            sortie = new PrintWriter(socket.getOutputStream(), true);

            System.out.println("Connecte au serveur de Tic-Tac-Toe");

            jouer();

        } catch (IOException e) {
            System.err.println("Erreur de connexion au serveur: " + e.getMessage());
        } finally {
            fermerConnexion();
        }
    }

    private void jouer() throws IOException {
        String ligne;

        while ((ligne = entree.readLine()) != null) {
            if (ligne.startsWith("BIENVENUE ")) {
                monSymbole = ligne.charAt(10);
                System.out.println("\n=== BIENVENUE AU TIC-TAC-TOE ===");
                System.out.println("Vous etes le joueur: " + monSymbole);
                System.out.println("================================\n");

            } else if (ligne.equals("ATTENTE")) {
                System.out.println("En attente de l'adversaire...");

            } else if (ligne.equals("VOTRE_TOUR")) {
                System.out.println("\n>>> C'est votre tour!");

            } else if (ligne.startsWith("GRILLE")) {
                String grille = ligne.substring(6);
                System.out.println(grille);

            } else if (ligne.equals("COUP_VALIDE")) {
                System.out.println("Coup joue avec succes!");

            } else if (ligne.equals("COUP_INVALIDE")) {
                System.out.println("Coup invalide! Reessayez.");

            } else if (ligne.startsWith("ERREUR ")) {
                System.out.println("Erreur: " + ligne.substring(7));

            } else if (ligne.startsWith("COUP_ADVERSAIRE ")) {
                String[] parties = ligne.split(" ");
                System.out.println("\nL'adversaire a joue en position (" + parties[1] + ", " + parties[2] + ")");
                System.out.println("En attente de votre coup...");

            } else if (ligne.equals("VICTOIRE")) {
                System.out.println("\n*** FELICITATIONS! VOUS AVEZ GAGNE! ***\n");
                break;

            } else if (ligne.equals("DEFAITE")) {
                System.out.println("\n*** VOUS AVEZ PERDU. ***\n");
                break;

            } else if (ligne.equals("MATCH_NUL")) {
                System.out.println("\n*** MATCH NUL! ***\n");
                break;
            }

            if (ligne.equals("VOTRE_TOUR")) {
                demanderCoup();
            }
        }
    }

    private void demanderCoup() {
        System.out.println("\nEntrez votre coup (format: ligne colonne, ex: 0 1)");
        System.out.println("Les coordonnees vont de 0 a 2");
        System.out.print("Votre coup: ");

        String coup = scannerUtilisateur.nextLine().trim();
        sortie.println(coup);
    }

    private void fermerConnexion() {
        try {
            if (scannerUtilisateur != null) scannerUtilisateur.close();
            if (entree != null) entree.close();
            if (sortie != null) sortie.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("Erreur lors de la fermeture: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        Client client = new Client();
        client.connecter();
    }
}
