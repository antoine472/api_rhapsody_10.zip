package main.gui.tools;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;

import utils.DialogPlacement;
import utils.TreeDiagramLayout;
import utils.TreeDiagramLayout.Orientation;

/**
 * Boite de dialogue de "Rearrange Tree Layout" : choix de l'orientation et
 * du nombre de niveaux a reorganiser (comme la profondeur de Generate LBS / FBS / TBD).
 *
 * <p><b>Presentation</b> : deux cartes cliquables, chacune avec un apercu
 * dessine de la disposition (arbre indente ou organigramme). Meme style que
 * les autres fenetres du plugin ({@link UiKit}) : palette, bouton principal
 * arrondi, titre de section. Aucun changement global de Look and Feel, ce qui
 * est sans risque dans la JVM partagee de Rhapsody.</p>
 *
 * <p><b>Placement</b> : la fenetre est centree sur l'ecran ou se trouve la
 * souris, c'est-a-dire celui ou l'utilisateur vient de faire le clic droit
 * dans Rhapsody ({@link DialogPlacement}), et elle reste au premier plan,
 * devant Rhapsody.</p>
 *
 * <p><b>Profondeur</b> : liste "All levels (*)", "1 level", "2 levels"...
 * limitee a la profondeur reelle de l'arbre. Les blocs plus profonds gardent
 * leur disposition et suivent leur ancetre.</p>
 *
 * <p><b>Clavier</b> : fleches gauche / droite ou touches V / H pour choisir,
 * Entree pour appliquer, Echap pour annuler. Un double-clic sur une carte
 * applique directement.</p>
 */
public final class TreeLayoutOrientationDialog {

	/** Taille de la zone d'apercu dessinee dans chaque carte. */
	private static final int PREVIEW_W = 176;
	private static final int PREVIEW_H = 96;

	private TreeLayoutOrientationDialog() {
		// classe utilitaire : pas d'instance
	}

	/**
	 * Choix de l'utilisateur.
	 *
	 * @param orientation orientation de la mise en page
	 * @param depth       nombre de niveaux reorganises, ou TreeDiagramLayout.ALL_LEVELS
	 */
	public record Choice(Orientation orientation, int depth) {}

	/** Element de la liste des profondeurs : valeur et libelle affiche. */
	private record DepthItem(int depth, String label) {
		@Override
		public String toString() { return label; }
	}

	/**
	 * Affiche la boite (modale) et attend le choix de l'utilisateur.
	 *
	 * @param elementName  nom de l'element selectionne, affiche en en-tete
	 * @param initial      orientation preselectionnee (par exemple le dernier choix)
	 * @param maxDepth     nombre de niveaux sous l'element (au moins 1)
	 * @param initialDepth profondeur preselectionnee, ou TreeDiagramLayout.ALL_LEVELS
	 * @return le choix, ou null si l'utilisateur annule
	 */
	public static Choice ask(String elementName, Orientation initial, int maxDepth, int initialDepth) {
		final JDialog dialog = new JDialog((Frame) null, "Rearrange Tree Layout", true);
		dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		dialog.setAlwaysOnTop(true);   // devant Rhapsody, dont la fenetre est native
		dialog.setResizable(false);

		JPanel root = new JPanel(new BorderLayout(0, 14));
		root.setBackground(UiKit.SURFACE);
		root.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));
		dialog.setContentPane(root);

		// -- Nord : titre de section, nom de l'element, rappel du comportement --
		JPanel header = new JPanel();
		header.setOpaque(false);
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

		JLabel title = UiKit.sectionTitle("REARRANGE THE CHILDREN OF", UiKit.ACCENT);
		title.setAlignmentX(JComponent.LEFT_ALIGNMENT);
		JLabel name = new JLabel(elementName);
		name.setForeground(UiKit.INK);
		name.setFont(name.getFont().deriveFont(Font.BOLD, name.getFont().getSize2D() + 3f));
		name.setAlignmentX(JComponent.LEFT_ALIGNMENT);

		header.add(title);
		header.add(Box.createVerticalStrut(4));
		header.add(name);
		header.add(Box.createVerticalStrut(4));
		header.add(UiKit.muted("The selected block and the rest of the diagram stay in place."));
		root.add(header, BorderLayout.NORTH);

		// -- Centre : les deux cartes d'orientation ------------------------------
		final OptionCard vertical = new OptionCard(Orientation.VERTICAL,
				"Vertical", "Indented list, as in Generate LBS");
		final OptionCard horizontal = new OptionCard(Orientation.HORIZONTAL,
				"Horizontal", "Organization chart, children in a row");

		// Etat partage entre les ecouteurs : carte choisie et resultat final
		final OptionCard[] chosen = { initial == Orientation.HORIZONTAL ? horizontal : vertical };
		final Choice[] result = { null };

		// Liste des profondeurs : "All levels (*)" puis 1 a maxDepth niveaux
		final JComboBox<DepthItem> depthBox = new JComboBox<>();
		depthBox.addItem(new DepthItem(TreeDiagramLayout.ALL_LEVELS, "All levels (*)"));
		for (int d = 1; d <= Math.max(1, maxDepth); d++) {
			depthBox.addItem(new DepthItem(d, d == 1 ? "1 level (children only)" : d + " levels"));
		}
		depthBox.setSelectedIndex(0);
		for (int i = 0; i < depthBox.getItemCount(); i++) {
			if (depthBox.getItemAt(i).depth() == initialDepth) depthBox.setSelectedIndex(i);
		}
		depthBox.setToolTipText("Number of levels rearranged below the selected block");

		final Runnable refresh = () -> {
			vertical.setSelected(chosen[0] == vertical);
			horizontal.setSelected(chosen[0] == horizontal);
		};

		for (final OptionCard card : new OptionCard[] { vertical, horizontal }) {
			card.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					chosen[0] = card;
					refresh.run();
					// Double-clic : choix et validation en un geste
					if (e.getClickCount() >= 2) {
						result[0] = new Choice(card.orientation, selectedDepth(depthBox));
						dialog.dispose();
					}
				}
			});
		}

		JPanel cards = new JPanel(new GridLayout(1, 2, 12, 0));
		cards.setOpaque(false);
		cards.add(vertical);
		cards.add(horizontal);

		// Ligne de profondeur sous les cartes
		JPanel depthRow = new JPanel();
		depthRow.setOpaque(false);
		depthRow.setLayout(new BoxLayout(depthRow, BoxLayout.X_AXIS));
		JLabel depthLabel = UiKit.sectionTitle("LEVELS TO REARRANGE", UiKit.INK2);
		depthRow.add(depthLabel);
		depthRow.add(Box.createHorizontalStrut(10));
		depthBox.setMaximumSize(depthBox.getPreferredSize());
		depthRow.add(depthBox);
		depthRow.add(Box.createHorizontalStrut(10));
		depthRow.add(UiKit.muted("Deeper blocks keep their layout and follow their parent."));
		depthRow.add(Box.createHorizontalGlue());

		JPanel center = new JPanel(new BorderLayout(0, 12));
		center.setOpaque(false);
		center.add(cards, BorderLayout.CENTER);
		center.add(depthRow, BorderLayout.SOUTH);
		root.add(center, BorderLayout.CENTER);

		// -- Sud : rappel clavier + Apply (par defaut) + Cancel -----------------
		JButton btnApply = UiKit.primary("Apply");
		JButton btnCancel = UiKit.neutral("Cancel");
		btnApply.addActionListener(e -> {
			result[0] = new Choice(chosen[0].orientation, selectedDepth(depthBox));
			dialog.dispose();
		});
		btnCancel.addActionListener(e -> dialog.dispose());
		// Meme hauteur pour les deux boutons
		btnCancel.setPreferredSize(new Dimension(btnCancel.getPreferredSize().width,
				btnApply.getPreferredSize().height));

		JLabel hint = UiKit.muted("Arrows or V / H to choose, Esc: nothing is changed");
		hint.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		buttons.setOpaque(false);
		buttons.add(hint);
		buttons.add(btnApply);
		buttons.add(btnCancel);
		root.add(buttons, BorderLayout.SOUTH);

		// -- Clavier --------------------------------------------------------------
		dialog.getRootPane().setDefaultButton(btnApply);   // Entree = Apply
		UiKit.onEscape(dialog, dialog::dispose);            // Echap = Cancel
		bindKey(dialog, KeyEvent.VK_LEFT,  () -> { chosen[0] = vertical;   refresh.run(); });
		bindKey(dialog, KeyEvent.VK_V,     () -> { chosen[0] = vertical;   refresh.run(); });
		bindKey(dialog, KeyEvent.VK_RIGHT, () -> { chosen[0] = horizontal; refresh.run(); });
		bindKey(dialog, KeyEvent.VK_H,     () -> { chosen[0] = horizontal; refresh.run(); });

		// -- Affichage : ecran de Rhapsody, premier plan, focus sur Apply --------
		refresh.run();
		dialog.pack();
		DialogPlacement.centerOnActiveScreen(dialog);
		dialog.addWindowListener(new WindowAdapter() {
			@Override
			public void windowOpened(WindowEvent e) {
				// Rhapsody etant une application native, on force le premier plan
				dialog.toFront();
				btnApply.requestFocusInWindow();
			}
		});
		dialog.setVisible(true);   // bloque jusqu'a Apply, Cancel, Echap ou fermeture

		return result[0];
	}

	/** Profondeur choisie dans la liste. */
	private static int selectedDepth(JComboBox<DepthItem> depthBox) {
		DepthItem item = (DepthItem) depthBox.getSelectedItem();
		return item != null ? item.depth() : TreeDiagramLayout.ALL_LEVELS;
	}

	/** Associe une touche a une action, quelle que soit la zone qui a le focus. */
	private static void bindKey(JDialog dialog, int keyCode, Runnable action) {
		dialog.getRootPane().registerKeyboardAction(e -> action.run(),
				KeyStroke.getKeyStroke(keyCode, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
	}

	// ======================================================================
	// Carte d'orientation
	// ======================================================================

	/**
	 * Carte cliquable : apercu dessine, titre et sous-titre. Fond et bordure
	 * en couleur d'accent quand elle est choisie, fond leger au survol.
	 */
	private static final class OptionCard extends JPanel {

		private static final long serialVersionUID = 1L;

		final Orientation orientation;
		private final JLabel titleLabel;
		private boolean selected;
		private boolean hover;

		OptionCard(Orientation orientation, String title, String subtitle) {
			super(new BorderLayout(0, 8));
			this.orientation = orientation;
			setOpaque(false);   // le fond arrondi est peint dans paintComponent
			setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
			setCursor(UiKit.HAND);
			setToolTipText(title + " : " + subtitle);

			add(new Preview(this), BorderLayout.CENTER);

			JPanel texts = new JPanel();
			texts.setOpaque(false);
			texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));
			titleLabel = new JLabel(title);
			titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, titleLabel.getFont().getSize2D() + 1f));
			titleLabel.setForeground(UiKit.INK);
			texts.add(titleLabel);
			texts.add(Box.createVerticalStrut(2));
			texts.add(UiKit.muted(subtitle));
			add(texts, BorderLayout.SOUTH);

			// Survol : simple retour visuel
			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseEntered(MouseEvent e) { hover = true;  repaint(); }

				@Override
				public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
			});
		}

		void setSelected(boolean value) {
			selected = value;
			titleLabel.setForeground(value ? UiKit.ACCENT : UiKit.INK);
			repaint();
		}

		boolean isSelectedCard() {
			return selected;
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			try {
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				int w = getWidth() - 1;
				int h = getHeight() - 1;

				// Fond : accent leger si choisie, gris tres clair au survol, blanc sinon
				g2.setColor(selected ? UiKit.ACCENT_WEAK : (hover ? UiKit.GROUP_BG : UiKit.SURFACE));
				g2.fillRoundRect(0, 0, w, h, 12, 12);

				// Bordure : 2 px en accent si choisie, 1 px discrete sinon
				g2.setColor(selected ? UiKit.ACCENT : UiKit.HAIR);
				g2.setStroke(new BasicStroke(selected ? 2f : 1f));
				g2.drawRoundRect(1, 1, w - 2, h - 2, 12, 12);
			} finally {
				g2.dispose();
			}
			super.paintComponent(g);
		}
	}

	// ======================================================================
	// Apercu dessine
	// ======================================================================

	/** Mini-diagramme : un parent, trois enfants et leurs liens, dans l'orientation de la carte. */
	private static final class Preview extends JComponent {

		private static final long serialVersionUID = 1L;

		private final OptionCard card;

		Preview(OptionCard card) {
			this.card = card;
			setPreferredSize(new Dimension(PREVIEW_W, PREVIEW_H));
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			try {
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				Color ink = card.isSelectedCard() ? UiKit.ACCENT : UiKit.INK2;
				if (card.orientation == Orientation.VERTICAL) {
					paintVertical(g2, ink);
				} else {
					paintHorizontal(g2, ink);
				}
			} finally {
				g2.dispose();
			}
		}

		/** Arbre indente : enfants empiles, decales, relies a l'epine du parent. */
		private void paintVertical(Graphics2D g2, Color ink) {
			int ox = (getWidth() - 100) / 2;   // dessin de 100 px de large, centre
			int spine = ox + 9;                // x + largeur / 8, comme TreeDiagramLayout

			g2.setColor(UiKit.INK2);
			g2.setStroke(new BasicStroke(1f));
			g2.drawLine(spine, 24, spine, 81);
			for (int y : new int[] { 34, 54, 74 }) {
				g2.drawLine(spine, y + 7, ox + 28, y + 7);
			}

			block(g2, ox, 8, 72, 16, ink);
			for (int y : new int[] { 34, 54, 74 }) {
				block(g2, ox + 28, y, 72, 14, ink);
			}
		}

		/** Organigramme : enfants en rangee, relies par un bus horizontal. */
		private void paintHorizontal(Graphics2D g2, Color ink) {
			int cx = getWidth() / 2;
			int childW = 44;
			int gap = 10;
			int x0 = cx - (3 * childW + 2 * gap) / 2;
			int bus = 42;

			g2.setColor(UiKit.INK2);
			g2.setStroke(new BasicStroke(1f));
			g2.drawLine(cx, 24, cx, bus);
			g2.drawLine(x0 + childW / 2, bus, x0 + 2 * (childW + gap) + childW / 2, bus);
			for (int i = 0; i < 3; i++) {
				int x = x0 + i * (childW + gap) + childW / 2;
				g2.drawLine(x, bus, x, 60);
			}

			block(g2, cx - 32, 8, 64, 16, ink);
			for (int i = 0; i < 3; i++) {
				block(g2, x0 + i * (childW + gap), 60, childW, 14, ink);
			}
		}

		/** Bloc stylise : fond blanc, bandeau de titre colore, contour. */
		private static void block(Graphics2D g2, int x, int y, int w, int h, Color ink) {
			g2.setColor(UiKit.SURFACE);
			g2.fillRoundRect(x, y, w, h, 4, 4);
			g2.setColor(ink == UiKit.ACCENT ? UiKit.ACCENT_WEAK : UiKit.HAIR);
			g2.fillRoundRect(x + 1, y + 1, w - 1, 5, 3, 3);
			g2.setColor(ink);
			g2.drawRoundRect(x, y, w, h, 4, 4);
		}
	}
}
