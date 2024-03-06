import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class EmpruntLivreWindow extends JFrame {
    private JComboBox<String> adherentsCombo;
    private JComboBox<String> livresCombo;
    private JLabel livreDetailsLabel;
    private JButton emprunterButton;

    public EmpruntLivreWindow() {
        super("Emprunter un livre");

        JPanel panel = new JPanel(new GridLayout(5, 2, 5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        panel.add(new JLabel("Sélectionner un adhérent:"));
        adherentsCombo = new JComboBox<>();
        loadAdherentsCombo();
        panel.add(adherentsCombo);

        panel.add(new JLabel("Sélectionner un livre:"));
        livresCombo = new JComboBox<>();
        loadLivresCombo();
        livresCombo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateLivreDetails();
            }
        });
        panel.add(livresCombo);

        panel.add(new JLabel("Détails du livre:"));
        livreDetailsLabel = new JLabel();
        panel.add(livreDetailsLabel);

        emprunterButton = new JButton("Emprunter");
        emprunterButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                emprunterLivre();
            }
        });
        panel.add(emprunterButton);

        add(panel);
        pack();
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);
    }

    private void loadAdherentsCombo() {
        try {
            Class.forName("com.mysql.jdbc.Driver");
            Connection con = DriverManager.getConnection("jdbc:mysql://localhost/e5-bilotheque-java", "root", "root");
            Statement stmt = con.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT CONCAT(nom, ' ', prenom) AS nom_complet FROM adherent");

            while (rs.next()) {
                adherentsCombo.addItem(rs.getString("nom_complet"));
            }

            con.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void loadLivresCombo() {
        try {
            Class.forName("com.mysql.jdbc.Driver");
            Connection con = DriverManager.getConnection("jdbc:mysql://localhost/e5-bilotheque-java", "root", "root");
            Statement stmt = con.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT titre FROM livre WHERE disponibilite > 0");
    
            while (rs.next()) {
                livresCombo.addItem(rs.getString("titre"));
            }
    
            con.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    

    private void updateLivreDetails() {
        String selectedLivre = (String) livresCombo.getSelectedItem();
        if (selectedLivre != null) {
            try {
                Class.forName("com.mysql.jdbc.Driver");
                Connection con = DriverManager.getConnection("jdbc:mysql://localhost/e5-bilotheque-java", "root", "root");
                PreparedStatement stmt = con.prepareStatement("SELECT titre, prix, autnum_1 FROM livre WHERE titre = ?");
                stmt.setString(1, selectedLivre);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    String prix = rs.getString("prix");
                    int autnum = rs.getInt("autnum_1");

                    String auteur = getAuteurName(autnum);

                    livreDetailsLabel.setText(" Prix: " + prix + ", Auteur: " + auteur);
                }

                con.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private String getAuteurName(int autnum) {
        String auteur = "";
        try {
            Class.forName("com.mysql.jdbc.Driver");
            Connection con = DriverManager.getConnection("jdbc:mysql://localhost/e5-bilotheque-java", "root", "root");
            PreparedStatement stmt = con.prepareStatement("SELECT nom, prenom FROM auteur WHERE autnum = ?");
            stmt.setInt(1, autnum);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String nom = rs.getString("nom");
                String prenom = rs.getString("prenom");
                auteur = nom + " " + prenom;
            }

            con.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return auteur;
    }

    private void emprunterLivre() {
        String selectedAdherent = (String) adherentsCombo.getSelectedItem();
        String selectedLivre = (String) livresCombo.getSelectedItem();

        if (selectedAdherent == null || selectedLivre == null) {
            JOptionPane.showMessageDialog(this, "Veuillez sélectionner un adhérent et un livre.", "Erreur", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            Class.forName("com.mysql.jdbc.Driver");
            Connection con = DriverManager.getConnection("jdbc:mysql://localhost/e5-bilotheque-java", "root", "root");

            // Vérification de la disponibilité du livre
            PreparedStatement checkStmt = con.prepareStatement("SELECT disponibilite FROM livre WHERE titre = ?");
            checkStmt.setString(1, selectedLivre);
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next()) {
                int disponibilite = rs.getInt("disponibilite");
                if (disponibilite > 0) {
                    // Mise à jour de la disponibilité
                    PreparedStatement updateStmt = con.prepareStatement("UPDATE livre SET disponibilite = ? WHERE titre = ?");
                    updateStmt.setInt(1, disponibilite - 1);
                    updateStmt.setString(2, selectedLivre);
                    updateStmt.executeUpdate();

                    // Calcul de la date de retour (4 semaines plus tard)
                    LocalDate dateEmprunt = LocalDate.now();
                    LocalDate dateRetour = dateEmprunt.plusWeeks(4);

                    // Insertion de l'emprunt dans la base de données
                    PreparedStatement insertStmt = con.prepareStatement("INSERT INTO emprunts (id_adherent, id_livre, date_emprunt, date_retour) VALUES (?, (SELECT ISBN FROM livre WHERE titre = ?), ?, ?)");
                    insertStmt.setInt(1, getAdherentId(selectedAdherent));
                    insertStmt.setString(2, selectedLivre);
                    insertStmt.setDate(3, Date.valueOf(dateEmprunt));
                    insertStmt.setDate(4, Date.valueOf(dateRetour));
                    insertStmt.executeUpdate();

                    JOptionPane.showMessageDialog(this, "Livre emprunté avec succès. Date de retour : " + dateRetour.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                } else {
                    JOptionPane.showMessageDialog(this, "Le livre sélectionné n'est pas disponible.", "Erreur", JOptionPane.ERROR_MESSAGE);
                }
            }

            con.close();
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Erreur lors de l'emprunt du livre : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    private int getAdherentId(String nomComplet) {
        int adherentId = -1;
        try {
            Class.forName("com.mysql.jdbc.Driver");
            Connection con = DriverManager.getConnection("jdbc:mysql://localhost/e5-bilotheque-java", "root", "root");
            PreparedStatement stmt = con.prepareStatement("SELECT adhnum FROM adherent WHERE CONCAT(nom, ' ', prenom) = ?");
            stmt.setString(1, nomComplet);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                adherentId = rs.getInt("adhnum");
            }

            con.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return adherentId;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new EmpruntLivreWindow();
            }
        });
    }
}
