import java.io.*;
import java.net.*;

public class clientTCP4 {
    public static void main(String[] args) {
        final String SERVER_ADDRESS = "localhost"; // Adresse du serveur
        final int SERVER_PORT = 3000; // Port du serveur

        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            System.out.println("Client : connecté au serveur.");

            // Envoyer la commande DATE
            out.println("DATE");
            System.out.println("Client : commande envoyée => DATE");

            // Attendre la réponse du serveur
            String responseDate = in.readLine();
            System.out.println("Client : réponse du serveur => " + responseDate);

            // Envoyer la commande HOUR
            out.println("HOUR");
            System.out.println("Client : commande envoyée => HOUR");

            // Attendre la réponse du serveur
            String responseHour = in.readLine();
            System.out.println("Client : réponse du serveur => " + responseHour);

            // Envoyer la commande FULL
            out.println("FULL");
            System.out.println("Client : commande envoyée => FULL");

            // Attendre la réponse du serveur
            String responseFull = in.readLine();
            System.out.println("Client : réponse du serveur :> " + responseFull);

            // Envoyer la commande CLOSE
            out.println("CLOSE");
            System.out.println("Client : commande envoyée => CLOSE");

            // Attendre la réponse du serveur
            String responseClose = in.readLine();
            System.out.println("Client : réponse du serveur => " + responseClose);

        } catch (IOException e) {
            System.err.println("Exception client => " + e.getMessage());
        }
    }
}