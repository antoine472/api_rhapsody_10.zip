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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.ParseException;
import java.util.EnumMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.WindowConstants;

import utils.BlockTextFit;
import utils.DialogPlacement;
import utils.TreeDiagramLayout;
import utils.TreeDiagramLayout.Orientation;
import utils.TreeDiagramLayout.Spacing;

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
 * <p><b>Espacements</b> : deux champs numeriques, dont le libelle suit
 * l'orientation choisie (Vertical : indentation et ecart entre blocs ;
 * Horizontal : ecart entre freres et ecart entre niveaux). Chaque orientation
 * garde ses propres valeurs ; le lien "Reset" remet les valeurs par defaut.</p>
 *
 * <p><b>Taille des blocs</b> : case a cocher "Fit width to the displayed name" :
 * la largeur des blocs reorganises est ajustee pour que leur nom tienne sur
 * une ligne (voir {@link utils.BlockTextFit}).</p>
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
	 * @param spacing     espacements choisis pour cette orientation
	 * @param fitWidth    vrai si la largeur des blocs doit etre ajustee a leur nom
	 */
	public record Choice(Orientation orientation, int depth, Spacing spacing, boolean fitWidth) {}

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
		return ask(elementName, initial, maxDepth, initialDepth, new EnumMap<>(Orientation.class));
	}

	/**
	 * Comme {@link #ask(String, Orientation, int, int)}, avec les espacements
	 * memorises par orientation.
	 *
	 * @param spacings espacements par orientation (une orientation absente prend
	 *                 les valeurs par defaut). Mis a jour sur Apply avec les
	 *                 valeurs affichees pour chaque orientation ; inchange sur Cancel.
	 */
	public static Choice ask(String elementName, Orientation initial, int maxDepth, int initialDepth,
			Map<Orientation, Spacing> spacings) {
		return ask(elementName, initial, maxDepth, initialDepth, spacings, false);
	}

	/**
	 * Version complete.
	 *
	 * @param initialFitWidth etat initial de la case "Fit width to the displayed name"
	 */
	public static Choice ask(String elementName, Orientation initial, int maxDepth, int initialDepth,
			Map<Orientation, Spacing> spacings, boolean initialFitWidth) {
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
				"Vertical", "Indented list, as in Generate LBS / FBS / TBS");
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

		// Espacements : copie de travail par orientation (le parametre n'est
		// modifie que sur Apply), et deux champs numeriques partages
		final Map<Orientation, Spacing> working = new EnumMap<>(Orientation.class);
		for (Orientation o : Orientation.values()) {
			Spacing s = spacings.get(o);
			working.put(o, s != null ? s : Spacing.defaults(o));
		}
		final JSpinner hSpin = spacingSpinner();
		final JSpinner vSpin = spacingSpinner();
		// Largeur fixe = la plus longue des deux valeurs possibles : la boite
		// n'etant pas redimensionnable, changer d'orientation ne doit rien decaler
		final JLabel hLabel = fixedWidthLabel("Indent", "Between siblings");
		final JLabel vLabel = fixedWidthLabel("Between blocks", "Between levels");
		// Ajustement de la largeur des blocs au nom affiche
		final JCheckBox fitWidthBox = new JCheckBox("Fit width to the displayed name", initialFitWidth);
		fitWidthBox.setOpaque(false);
		fitWidthBox.setForeground(UiKit.INK);
		fitWidthBox.setFocusPainted(false);
		fitWidthBox.setToolTipText("Width of each rearranged block set so that its name fits on one line"
				+ " (" + BlockTextFit.MIN_WIDTH + " to " + BlockTextFit.MAX_WIDTH + ")");

		// Orientation dont les valeurs sont actuellement affichees dans les champs
		final Orientation[] shown = { null };

		final Runnable refresh = () -> {
			vertical.setSelected(chosen[0] == vertical);
			horizontal.setSelected(chosen[0] == horizontal);

			Orientation target = chosen[0].orientation;
			if (shown[0] != target) {
				// Memorise les valeurs de l'orientation quittee, affiche celles de la nouvelle
				if (shown[0] != null) working.put(shown[0], readSpacing(hSpin, vSpin));
				Spacing s = working.get(target);
				hSpin.setValue(s.horizontal());
				vSpin.setValue(s.vertical());
				shown[0] = target;
			}
			if (target == Orientation.VERTICAL) {
				hLabel.setText("Indent");
				vLabel.setText("Between blocks");
				hSpin.setToolTipText("Horizontal shift of each level (default " + TreeDiagramLayout.INDENT + RANGE);
				vSpin.setToolTipText("Vertical gap between two stacked blocks (default " + TreeDiagramLayout.V_GAP + RANGE);
			} else {
				hLabel.setText("Between siblings");
				vLabel.setText("Between levels");
				hSpin.setToolTipText("Horizontal gap between two siblings (default " + TreeDiagramLayout.H_GAP + RANGE);
				vSpin.setToolTipText("Vertical gap between a parent and its children (default " + TreeDiagramLayout.LEVEL_GAP + RANGE);
			}
		};

		// Validation commune a Apply et au double-clic : choix + espacements memorises
		final Runnable accept = () -> {
			Spacing s = readSpacing(hSpin, vSpin);
			working.put(chosen[0].orientation, s);
			spacings.putAll(working);
			result[0] = new Choice(chosen[0].orientation, selectedDepth(depthBox), s, fitWidthBox.isSelected());
			dialog.dispose();
		};

		for (final OptionCard card : new OptionCard[] { vertical, horizontal }) {
			card.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					chosen[0] = card;
					refresh.run();
					// Double-clic : choix et validation en un geste
					if (e.getClickCount() >= 2) {
						accept.run();
					}
				}
			});
		}

		JPanel cards = new JPanel(new GridLayout(1, 2, 12, 0));
		cards.setOpaque(false);
		cards.add(vertical);
		cards.add(horizontal);

		// Reglages sous les cartes, sur une grille pour aligner les controles :
		//   LEVELS TO REARRANGE  [liste.................]  aide
		//   SPACING              libelle [n]  libelle [n]  Reset
		//   BLOCK SIZE           [x] Fit width to the displayed name   aide
		JPanel settings = new JPanel(new GridBagLayout());
		settings.setOpaque(false);

		cell(settings, UiKit.sectionTitle("LEVELS TO REARRANGE", UiKit.INK2), 0, 0, 1, 0, 14);
		cell(settings, depthBox, 1, 0, 4, 0, 10);
		cell(settings, UiKit.muted("Deeper blocks follow their parent."), 5, 0, 1, 0, 0);

		cell(settings, UiKit.sectionTitle("SPACING", UiKit.INK2), 0, 1, 1, 8, 14);
		cell(settings, hLabel, 1, 1, 1, 8, 6);
		cell(settings, hSpin, 2, 1, 1, 8, 14);
		cell(settings, vLabel, 3, 1, 1, 8, 6);
		cell(settings, vSpin, 4, 1, 1, 8, 10);
		cell(settings, UiKit.link("Reset", e -> {
			Spacing d = Spacing.defaults(chosen[0].orientation);
			hSpin.setValue(d.horizontal());
			vSpin.setValue(d.vertical());
		}), 5, 1, 1, 8, 0);

		cell(settings, UiKit.sectionTitle("BLOCK SIZE", UiKit.INK2), 0, 2, 1, 8, 14);
		cell(settings, fitWidthBox, 1, 2, 4, 8, 10);
		cell(settings, UiKit.muted("Height and selected block unchanged."), 5, 2, 1, 8, 0);

		JPanel center = new JPanel(new BorderLayout(0, 12));
		center.setOpaque(false);
		center.add(cards, BorderLayout.CENTER);
		center.add(settings, BorderLayout.SOUTH);
		root.add(center, BorderLayout.CENTER);

		// -- Sud : rappel clavier + Apply (par defaut) + Cancel -----------------
		JButton btnApply = UiKit.primary("Apply");
		JButton btnCancel = UiKit.neutral("Cancel");
		btnApply.addActionListener(e -> accept.run());
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

	/** Champ numerique d'espacement : pas de 10, bornes MIN_SPACING a MAX_SPACING. */
	private static JSpinner spacingSpinner() {
		JSpinner spinner = new JSpinner(new SpinnerNumberModel(
				TreeDiagramLayout.MIN_SPACING, TreeDiagramLayout.MIN_SPACING,
				TreeDiagramLayout.MAX_SPACING, 10));
		((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setColumns(4);
		spinner.setMaximumSize(spinner.getPreferredSize());
		return spinner;
	}

	/**
	 * Lit les deux champs. Une saisie en cours (tapee mais pas encore validee)
	 * est d'abord prise en compte ; une saisie invalide garde la derniere valeur correcte.
	 */
	private static Spacing readSpacing(JSpinner hSpin, JSpinner vSpin) {
		return new Spacing(spinnerValue(hSpin), spinnerValue(vSpin));
	}

	private static int spinnerValue(JSpinner spinner) {
		try {
			spinner.commitEdit();
		} catch (ParseException e) {
			// saisie invalide : on garde la valeur precedente du champ
		}
		return ((Number) spinner.getValue()).intValue();
	}

	/** Plage autorisee, rappelee dans les infobulles des champs. */
	private static final String RANGE = ", from " + TreeDiagramLayout.MIN_SPACING
			+ " to " + TreeDiagramLayout.MAX_SPACING + ")";

	/**
	 * Place un composant dans la grille des reglages, aligne a gauche.
	 *
	 * @param top   marge au-dessus (separe les lignes)
	 * @param right marge a droite (separe les colonnes)
	 */
	private static void cell(JPanel grid, JComponent comp, int x, int y, int width, int top, int right) {
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = x;
		c.gridy = y;
		c.gridwidth = width;
		c.anchor = GridBagConstraints.WEST;
		c.fill = (width > 1) ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
		c.insets = new Insets(top, 0, 0, right);
		grid.add(comp, c);
	}

	/** Libelle dont la largeur est celle du plus long des textes donnes. */
	private static JLabel fixedWidthLabel(String... texts) {
		JLabel label = new JLabel(texts[0]);
		label.setForeground(UiKit.INK);
		int width = 0;
		for (String s : texts) width = Math.max(width, label.getFontMetrics(label.getFont()).stringWidth(s));
		Dimension d = label.getPreferredSize();
		label.setPreferredSize(new Dimension(width + 2, d.height));
		return label;
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
