package fr.umontpellier.iut.rails;

import com.google.gson.Gson;
import fr.umontpellier.iut.gui.GameServer;
import fr.umontpellier.iut.rails.data.*;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Jeu implements Runnable {
    /**
     * Liste des joueurs
     */
    private final List<Joueur> joueurs;
    /**
     * Le joueur dont c'est le tour
     */
    private Joueur joueurCourant;
    /**
     * Liste des villes disponibles sur le plateau de jeu
     */
    private final List<Ville> portsLibres;
    /**
     * Liste des routes disponibles sur le plateau de jeu
     */
    private final List<Route> routesLibres;
    /**
     * Pile de pioche et défausse des cartes wagon
     */
    private final PilesCartesTransport pilesDeCartesWagon;
    /**
     * Pile de pioche et défausse des cartes bateau
     */
    private final PilesCartesTransport pilesDeCartesBateau;
    /**
     * Cartes de la pioche face visible (normalement il y a 6 cartes face visible)
     */
    private final List<CarteTransport> cartesTransportVisibles;
    /**
     * Pile des cartes "Destination"
     */
    private final List<Destination> pileDestinations;
    /**
     * File d'attente des instructions recues par le serveur
     */
    private final BlockingQueue<String> inputQueue;
    /**
     * Messages d'information du jeu
     */
    private final List<String> log;

    private String instruction;
    private Collection<Bouton> boutons;

    public Jeu(String[] nomJoueurs) {
        // initialisation des entrées/sorties
        inputQueue = new LinkedBlockingQueue<>();
        log = new ArrayList<>();

        // création des villes et des routes
        Plateau plateau = Plateau.makePlateauMonde();
        portsLibres = plateau.getPorts();
        routesLibres = plateau.getRoutes();

        // création des piles de pioche et défausses des cartes Transport (wagon et
        // bateau)
        ArrayList<CarteTransport> cartesWagon = new ArrayList<>();
        ArrayList<CarteTransport> cartesBateau = new ArrayList<>();
        for (Couleur c : Couleur.values()) {
            if (c == Couleur.GRIS) {
                continue;
            }
            for (int i = 0; i < 4; i++) {
                // Cartes wagon simples avec une ancre
                cartesWagon.add(new CarteTransport(TypeCarteTransport.WAGON, c, false, true));
            }
            for (int i = 0; i < 7; i++) {
                // Cartes wagon simples sans ancre
                cartesWagon.add(new CarteTransport(TypeCarteTransport.WAGON, c, false, false));
            }
            for (int i = 0; i < 4; i++) {
                // Cartes bateau simples (toutes avec une ancre)
                cartesBateau.add(new CarteTransport(TypeCarteTransport.BATEAU, c, false, true));
            }
            for (int i = 0; i < 6; i++) {
                // Cartes bateau doubles (toutes sans ancre)
                cartesBateau.add(new CarteTransport(TypeCarteTransport.BATEAU, c, true, false));
            }
        }
        for (int i = 0; i < 14; i++) {
            // Cartes wagon joker
            cartesWagon.add(new CarteTransport(TypeCarteTransport.JOKER, Couleur.GRIS, false, true));
        }
        pilesDeCartesWagon = new PilesCartesTransport(cartesWagon);
        pilesDeCartesBateau = new PilesCartesTransport(cartesBateau);

        // création de la liste pile de cartes transport visibles
        // (les cartes seront retournées plus tard, au début de la partie dans run())
        cartesTransportVisibles = new ArrayList<>();

        // création des destinations
        pileDestinations = Destination.makeDestinationsMonde();
        Collections.shuffle(pileDestinations);

        // création des joueurs
        ArrayList<Joueur.CouleurJouer> couleurs = new ArrayList<>(Arrays.asList(Joueur.CouleurJouer.values()));
        Collections.shuffle(couleurs);
        joueurs = new ArrayList<>();
        for (String nomJoueur : nomJoueurs) {
            joueurs.add(new Joueur(nomJoueur, this, couleurs.remove(0)));
        }
        this.joueurCourant = joueurs.get(0);
    }



    public List<Joueur> getJoueurs() {
        return joueurs;
    }

    public List<Ville> getPortsLibres() {
        return new ArrayList<>(portsLibres);
    }

    public boolean removePortsLibres(Ville ville){
        return portsLibres.remove(ville);
    }

    public Ville getPortFromNom(String nom){
        int i = 0;
        while(!nom.equals(this.portsLibres.get(i).nom())){
            i++;
        }
        Ville ville = this.getPortsLibres().get(i);
        return ville;
    }

    public List<Route> getRoutesLibres() {
        return new ArrayList<>(routesLibres);
    }

    public boolean removeRoutesLibre(Route r){
        return routesLibres.remove(r);
    }

    /**
     * Renvoie une route se trouvant dans la liste routesLibres
     * @param nom Le nom d'une route
     * @return une route 
     */
    public Route getRouteFromNom(String nom){
        int i = 0;
        while(!nom.equals(this.routesLibres.get(i).getNom())){
            i++;
        }
        Route route = this.getRoutesLibres().get(i);
        return route;
    }

    public List<CarteTransport> getCartesTransportVisibles() {
        return new ArrayList<>(cartesTransportVisibles);
    }

    public void addCartesTransportVisibles(CarteTransport carte){
        cartesTransportVisibles.add(carte);
    }

    public CarteTransport getCarteTransportVisiblesFromNom(String nom){
        int i = 0;
        while(!nom.equals(cartesTransportVisibles.get(i).getNom())){
            i++;
        }
        CarteTransport carte = cartesTransportVisibles.get(i);
        return carte;
    }

    public boolean removeCarteTransportVisibles(CarteTransport carte){
        return cartesTransportVisibles.remove(carte);
    }

    public boolean cartesTransportVisiblesSontValide(){
        int nbJoker = 0;
        for(CarteTransport c : cartesTransportVisibles){
            if(c.getType()==TypeCarteTransport.JOKER){
                nbJoker++;
            }
        }
        return nbJoker<3;
    }

    public void resetCartesTransportVisibles(){

        if(pilesDeCartesWagon.size()>=1 && pilesDeCartesBateau.size()>=3 ){
            do{
                for(CarteTransport c : cartesTransportVisibles){
                    if(c.getType()==TypeCarteTransport.JOKER || c.getType()==TypeCarteTransport.WAGON){
                        pilesDeCartesWagon.defausser(c);
                    }
                    else if(c.getType() == TypeCarteTransport.BATEAU){
                        pilesDeCartesBateau.defausser(c);
                    }
                }
                cartesTransportVisibles.clear();
        
                if(pilesDeCartesBateau.size()>=3 && pilesDeCartesWagon.size()>=3){
                    for(int i =0; i<3; i++){
                        cartesTransportVisibles.add(piocherCarteBateau());
                        cartesTransportVisibles.add(piocherCarteWagon());
                    }
                }
                else if(pilesDeCartesWagon.size()>=6-pilesDeCartesBateau.size()){
                    for(int i =0; i<6-pilesDeCartesBateau.size(); i++){
                        cartesTransportVisibles.add(piocherCarteWagon());
                    }
                    for(int i =0; i<pilesDeCartesBateau.size(); i++){
                        cartesTransportVisibles.add(piocherCarteBateau());
                    }
                }
                else if(pilesDeCartesBateau.size()>=6-pilesDeCartesWagon.size()){
                    for(int i =0; i<6-pilesDeCartesWagon.size(); i++){
                        cartesTransportVisibles.add(piocherCarteBateau());
                    }
                    for(int i =0; i<pilesDeCartesWagon.size(); i++){
                        cartesTransportVisibles.add(piocherCarteWagon());
                    }
                }
                else{
                    for(int i=0; i<pilesDeCartesWagon.size(); i++){
                        cartesTransportVisibles.add(piocherCarteWagon());
                    }
                    for(int i=0; i<pilesDeCartesBateau.size(); i++){
                        cartesTransportVisibles.add(piocherCarteBateau());
                    }
                }
            }while(!cartesTransportVisiblesSontValide());
        }
    }

    /**
     * Exécute la partie
     *
     * C'est cette méthode qui est appelée pour démarrer la partie. Elle doit intialiser le jeu
     * (retourner les cartes transport visibles, puis demander à chaque joueur de choisir ses destinations initiales
     * et le nombre de pions wagon qu'il souhaite prendre) puis exécuter les tours des joueurs en appelant la
     * méthode Joueur.jouerTour() jusqu'à ce que la condition de fin de partie soit réalisée.
     */
    public void run() {
        // IMPORTANT : Le corps de cette fonction est à réécrire entièrement
        // Un exemple très simple est donné pour illustrer l'utilisation de certaines méthodes

        //var pour début de fin de partie
        boolean finDePartie = false;

        //compteur de tour
        int compteur = 2*getJoueurs().size()+1;

        //ajoue des cartes de Transport Visible
        for(int i=0; i<3; i++){
            cartesTransportVisibles.add(piocherCarteBateau());
            cartesTransportVisibles.add(piocherCarteWagon());
        }

        if(!cartesTransportVisiblesSontValide()){
            resetCartesTransportVisibles();
        }
        
        for (Joueur j: joueurs) {
            joueurCourant = j;
            j.setUp();
        }
        while(compteur > 0) {
            for (Joueur j : joueurs) {
                joueurCourant = j;
                j.jouerTour();
                if(j.getNbPions()<=6){
                    finDePartie= true;
                }
                if(finDePartie){
                    compteur--;
                }
                if(compteur==0){
                    break;
                }
            }

        }

        // Fin de la partie

        //calcul score
        for(Joueur j : joueurs){
            j.calculerScoreFinal();
        }

        //affichage gagnant
        int scoremax = 0;
        String gagnant = "";
        for(Joueur j : joueurs){
            if(j.getScore() >scoremax){
                scoremax = j.getScore();
                gagnant = j.getNom();
            }
        }

        prompt("Fin de la partie.", new ArrayList<>(), true);
    }


    /**
     * Pioche une carte de la pile de pioche des cartes wagon.
     *
     * @return la carte qui a été piochée (ou null si aucune carte disponible)
     */
    public CarteTransport piocherCarteWagon() {
        return pilesDeCartesWagon.piocher();
    }

    public void defausserWagon(CarteTransport c){
        pilesDeCartesWagon.defausser(c);
    }

    public boolean piocheWagonEstVide() {
        return pilesDeCartesWagon.estVide();
    }

    /**
     * Pioche une carte de la pile de pioche des cartes bateau.
     *
     * @return la carte qui a été piochée (ou null si aucune carte disponible)
     */
    public CarteTransport piocherCarteBateau() {
        return pilesDeCartesBateau.piocher();
    }

    public void defausserBateau(CarteTransport c){
        pilesDeCartesBateau.defausser(c);
    }

    public boolean piocheBateauEstVide() {
        return pilesDeCartesBateau.estVide();
    }

    /**
     * Pioche une carte de la pile de pioche des destinations.
     *
     * @return la carte qui a été piochée (ou null si aucune carte disponible)
     */
    public Destination piocheDestination(){
        if(!piocheDestinationEstVide()){
            return this.pileDestinations.remove(0);
        }
        else{
            return null;
        }
    }

    public void replacerDestination(Destination destination){
        this.pileDestinations.add(destination);
    }

    public boolean piocheDestinationEstVide() {
        return pileDestinations.size()==0;
    }



    /**
     * Ajoute un message au log du jeu
     */
    public void log(String message) {
        log.add(message);
    }

    /**
     * Ajoute un message à la file d'entrées
     */
    public void addInput(String message) {
        inputQueue.add(message);
    }

    /**
     * Lit une ligne de l'entrée standard
     * C'est cette méthode qui doit être appelée à chaque fois qu'on veut lire
     * l'entrée clavier de l'utilisateur (par exemple dans {@code Player.choisir})
     *
     * @return une chaîne de caractères correspondant à l'entrée suivante dans la
     * file
     */
    public String lireLigne() {
        try {
            return inputQueue.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Envoie l'état de la partie pour affichage aux joueurs avant de faire un choix
     *
     * @param instruction l'instruction qui est donnée au joueur
     * @param boutons     labels des choix proposés s'il y en a
     * @param peutPasser  indique si le joueur peut passer sans faire de choix
     */
    public void prompt(String instruction, Collection<Bouton> boutons, boolean peutPasser) {
        this.instruction = instruction;
        this.boutons = boutons;

        System.out.println();
        System.out.println(this);
        if (boutons.isEmpty()) {
            System.out.printf(">>> %s: %s <<<\n", joueurCourant.getNom(), instruction);
        } else {
            StringJoiner joiner = new StringJoiner(" / ");
            for (Bouton bouton : boutons) {
                joiner.add(bouton.toPrompt());
            }
            System.out.printf(">>> %s: %s [%s] <<<\n", joueurCourant.getNom(), instruction, joiner);
        }
        GameServer.setEtatJeu(new Gson().toJson(dataMap()));
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner("\n");
        for (Joueur j : joueurs) {
            joiner.add(j.toString());
        }
        return joiner.toString();
    }

    public Map<String, Object> dataMap() {
        return Map.ofEntries(
                Map.entry("joueurs", joueurs.stream().map(Joueur::dataMap).toList()),
                Map.entry("joueurCourant", joueurs.indexOf(joueurCourant)),
                Map.entry("piocheWagon", pilesDeCartesWagon.dataMap()),
                Map.entry("piocheBateau", pilesDeCartesBateau.dataMap()),
                Map.entry("cartesTransportVisibles", cartesTransportVisibles),
                Map.entry("nbDestinations", pileDestinations.size()),
                Map.entry("instruction", instruction),
                Map.entry("boutons", boutons),
                Map.entry("log", log));
    }
}
