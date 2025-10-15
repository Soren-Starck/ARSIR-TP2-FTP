import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;

public class Serveur {
    private static final int PORT = 12345;

    public static void main(String[] args) {
        System.out.println("Serveur de Tic-Tac-Toe demarre sur le port " + PORT);

        try (ServerSocket serveurSocket = new ServerSocket(PORT)) {
            while (true) {
                System.out.println("\nEn attente de deux joueurs...");

                Socket socketJoueur1 = serveurSocket.accept();
                System.out.println("Joueur 1 connecte depuis " + socketJoueur1.getInetAddress());

                Socket socketJoueur2 = serveurSocket.accept();
                System.out.println("Joueur 2 connecte depuis " + socketJoueur2.getInetAddress());

                lancerPartie(socketJoueur1, socketJoueur2);
            }

        } catch (IOException e) {
            System.err.println("Erreur du serveur: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void lancerPartie(Socket socketJoueur1, Socket socketJoueur2) {
        Jeu jeu = new Jeu();

        Random random = new Random();
        boolean joueur1CommenceEnX = random.nextBoolean();

        Joueur joueur1, joueur2;

        if (joueur1CommenceEnX) {
            joueur1 = new Joueur(socketJoueur1, jeu, 'X');
            joueur2 = new Joueur(socketJoueur2, jeu, 'O');
            jeu.definirJoueurDepart('X');
            System.out.println("Le joueur 1 (X) commence");
        } else {
            joueur1 = new Joueur(socketJoueur1, jeu, 'O');
            joueur2 = new Joueur(socketJoueur2, jeu, 'X');
            jeu.definirJoueurDepart('X');
            System.out.println("Le joueur 2 (X) commence");
        }

        joueur1.definirAdversaire(joueur2);
        joueur2.definirAdversaire(joueur1);

        joueur1.start();
        joueur2.start();

        try {
            joueur1.attendrePreparation();
            joueur2.attendrePreparation();

            System.out.println("Les deux joueurs sont prets. Debut de la partie!");

            joueur1.join();
            joueur2.join();

            System.out.println("Partie terminee.");
            if (jeu.getGagnant() == 'N') {
                System.out.println("Resultat: Match nul");
            } else {
                System.out.println("Resultat: Le joueur " + jeu.getGagnant() + " a gagne!");
            }

        } catch (InterruptedException e) {
            System.err.println("Erreur lors de l'attente des threads: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }
}
