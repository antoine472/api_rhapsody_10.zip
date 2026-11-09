package metamodel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPDiagram;
import com.telelogic.rhapsody.core.IRPFlow;
import com.telelogic.rhapsody.core.IRPGeneralization;
import com.telelogic.rhapsody.core.IRPGraphEdge;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPGraphNode;
import com.telelogic.rhapsody.core.IRPGraphicalProperty;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPPort;
import com.telelogic.rhapsody.core.IRPStereotype;
import com.telelogic.rhapsody.core.IRPSysMLPort;

import logging.RhapsodyLogger;

/**
 * Service central des ports redéfinis pour les références Safran
 * ({@code FunctionWithReference} / {@code LogicalSystemReference}).
 *
 * <p>Cette classe regroupe la logique éprouvée du prototype
 * {@code PriseEnMain} :</p>
 * <ul>
 *   <li><b>Redéfinition (Besoin A)</b> — {@link #redefinePorts(IRPModelElement)} :
 *       à partir d'une classe de référence, retrouve la {@code Function} /
 *       {@code LogicalSystem} mère via ses généralisations
 *       {@code "References Function"} / {@code "References Logical System"} et
 *       crée/complète les {@code Flow Port} redéfinis
 *       ({@code addRedefines}, copie type/direction/stéréotypes).</li>
 *   <li><b>Remplacement graphique (Besoin B)</b> —
 *       {@link #hideRedefinedPorts(IRPApplication, IRPGraphElement)} : dans un
 *       diagramme, chaque port hérité dessiné sur une référence est remplacé par
 *       son port redéfini, à la même place. Les flows dessinés sur le port
 *       remplacé sont pris en charge : un flow de la définition est copié sur le
 *       port redéfini (le flow d'origine n'est jamais modifié), un flow déjà
 *       rattaché à une référence est reconnecté ; rien n'est supprimé ni
 *       renommé dans le modèle.</li>
 * </ul>
 *
 * <p>Logique et chaînes de stéréotypes conservées telles quelles depuis le
 * prototype validé ; seule la sortie log passe par {@link RhapsodyLogger} pour
 * s'aligner sur le reste du plugin.</p>
 *
 * <p><b>ATTENTION — suppression de ports à la création du lien.</b>
 * {@link #redefinePorts(IRPModelElement)} appelle {@link #pruneNonMirrorPorts}
 * qui <b>supprime</b> ({@code deleteFromProject}) tout port de la référence qui
 * n'est pas une redéfinition d'un port de la mère courante. Dans le flux normal,
 * le lien « References… » est posé sur une classe <b>neuve</b> (sans port), donc
 * rien n'est supprimé. En revanche, si le lien est ajouté sur une classe qui
 * possède <b>déjà des ports</b> (et d'éventuels flows rattachés), ces ports
 * non-miroir sont <b>supprimés</b> à ce moment-là, <b>sans confirmation</b> (le
 * listener réagit en arrière-plan, aucun dialogue possible). Comportement
 * volontairement conservé ici ; la suppression <i>avec confirmation port par
 * port</i> est, elle, assurée par l'outil explicite « Update Redefined Ports »
 * (SafranProfilePlugin).</p>
 */
public final class RedefinedPortsService {

    private static final RhapsodyLogger rhpLog = RhapsodyLogger.getInstance();

    /** Si vrai, on utilise getDisplayName() au lieu de getName() pour les libellés. */
    private static final boolean LABEL_ON = false;

    /** Métaclasses utilisateur des liens de référence gérés. */
    private static final String REF_FUNCTION = "References Function";
    private static final String REF_LOGICAL_SYSTEM = "References Logical System";

    /**
     * Verrou de sérialisation du retrait graphique (Besoin B).
     *
     * <p>Deux chemins différés indépendants ({@code FunctionWithReference}
     * "SafranPortHiding" et {@code ReferencesFunction}
     * "SafranPortRedefinition") peuvent appeler {@code hideRedefinedPorts} sur
     * des threads timer distincts pour la même référence. Sans verrou, les deux
     * calculent la même liste de ports à retirer puis appellent
     * {@code removeGraphElements} sur des éléments déjà retirés -> « Membre
     * introuvable » en plein milieu d'une mutation graphique, hors transaction,
     * ce qui corrompt la pile d'undo de Rhapsody. Le scan des ports est
     * (re)calculé SOUS le verrou : le second entrant ne trouve plus rien et
     * devient un no-op.</p>
     */
    private static final Object GRAPHICS_CLEANUP_LOCK = new Object();

    private RedefinedPortsService() {
    }

    // ==================================================================
    // BESOIN A — Redéfinition des ports
    // ==================================================================

    /**
     * Vrai si {@code element} est une classe de référence, c.-à-d. porte une
     * généralisation {@code "References Function"} ou
     * {@code "References Logical System"}.
     */
    public static boolean isReferenceClass(IRPModelElement element) {
        if (!(element instanceof IRPClass)) return false;
        try {
            IRPCollection generalisations = ((IRPClass) element).getGeneralizations();
            if (generalisations == null) return false;
            for (Object object : generalisations.toList()) {
                if (!(object instanceof IRPGeneralization)) continue;
                String udm = ((IRPGeneralization) object).getUserDefinedMetaClass();
                if (REF_FUNCTION.equals(udm) || REF_LOGICAL_SYSTEM.equals(udm)) {
                    return true;
                }
            }
        } catch (Exception ignore) {
        }
        return false;
    }

    /**
     * Crée/complète les ports redéfinis d'une classe de référence à partir des
     * ports de sa classe mère (Function / LogicalSystem).
     *
     * @param systemReference la classe de référence
     *                        ({@code FunctionWithReference} / {@code LogicalSystemReference})
     * @return true si tout est en ordre ; false en cas d'échec RE-TENTABLE
     *         (mère pas encore résolue, échec COM transitoire sur un port...) —
     *         l'appelant peut alors re-tenter plus tard (l'opération est
     *         idempotente : les ports déjà créés sont réutilisés).
     */
    public static boolean redefinePorts(IRPModelElement systemReference) {

        if (!(systemReference instanceof IRPClass)) {
            return true; // rien à faire, ne pas re-tenter
        }
        IRPClass reference = (IRPClass) systemReference;

        IRPCollection generalisations = reference.getGeneralizations();

        IRPModelElement instanceType = null;
        String referenceName = "";

        if (generalisations != null) {
            for (Object object : generalisations.toList()) {
                if (!(object instanceof IRPGeneralization)) {
                    continue;
                }
                IRPGeneralization generalisation = (IRPGeneralization) object;

                if (!(REF_FUNCTION.equals(generalisation.getUserDefinedMetaClass()))
                        && !(REF_LOGICAL_SYSTEM.equals(generalisation.getUserDefinedMetaClass()))) {
                    continue;
                }

                instanceType = generalisation.getBaseClass(); // classe mère
                if (instanceType == null) {
                    continue;
                }

                referenceName = instanceType.getName();
                logInfo("\u00C9l\u00E9ment r\u00E9f\u00E9renc\u00E9 : " + instanceType.getName());

                if (!(instanceType instanceof IRPClass)) {
                    logInfo("PORT: instanceType is not IRPClass for " + instanceType.getName());
                    return true; // structurel, ne pas re-tenter
                }
            }
        }

        // Mère pas (encore) résolue : cas transitoire juste après la création du
        // lien -> RE-TENTABLE.
        if (!(instanceType instanceof IRPClass)) {
            logInfo("PORT: mother class not resolved yet for " + safeName(reference) + " -> retry");
            return false;
        }

        referenceName = safeName(reference);

        // Cas 3 : élaguer les ports "non-miroir" de la référence (redéfinitions
        // orphelines OU ports ajoutés manuellement sans redéfinition) AVANT de
        // (re)créer les redéfinitions valides. La référence ne doit refléter que
        // les ports de la mère courante.
        // NB (comportement assumé) : si la référence possède DÉJÀ des ports
        // non-miroir (et d'éventuels flows), ils sont SUPPRIMÉS ici, sans
        // confirmation. Cf. le javadoc de la classe. En pratique le lien est
        // posé sur une classe neuve -> rien à supprimer.
        pruneNonMirrorPorts(reference, instanceType);

        boolean allPortsOk = true;
        try {
            // Every port shown on the mother, the ones it inherits included (a
            // WhiteBox Function may own none and show its BlackBox ports).
            List<IRPSysMLPort> typePorts = effectiveMotherPorts(instanceType);

            for (IRPSysMLPort originalPort : typePorts) {
                if (originalPort == null) {
                    continue;
                }

                IRPSysMLPort redefinedPort = findOrCreateRedefinedPort(reference, originalPort);

                if (redefinedPort == null) {
                    // Échec typiquement transitoire (appel COM pendant que
                    // Rhapsody est occupé) -> signaler pour re-tentative.
                    logInfo("PORT: unable to create or reuse redefined port "
                            + safeName(originalPort) + " in " + referenceName + " -> will retry");
                    allPortsOk = false;
                    continue;
                }

                ensureRedefinedPortProperties(originalPort, redefinedPort);
            }

        } catch (Exception e) {
            logInfo("PORT : Erreur globale creation ports redefinis pour " + referenceName + e.getMessage());
            return false;
        }

        if (allPortsOk) {
            // Confirmation explicite (utile pour vérifier qu'un retry a complété
            // le miroir après un échec COM transitoire).
            logInfo("PORT: redefinition complete for " + referenceName
                    + " (mirror of " + safeName(instanceType) + ")");
        }
        return allPortsOk;
    }

    /**
     * Cas 3 — Élagage des ports « non-miroir » de la référence.
     *
     * <p>La référence ne doit contenir que le miroir des ports de la mère : pour
     * chaque port de la référence qui n'est PAS une redéfinition valide d'un port
     * de la classe mère courante {@code mother}, on <b>supprime</b> le port
     * ({@code deleteFromProject}) et on écrit un WARN. Cela couvre :</p>
     * <ul>
     *   <li>les redéfinitions orphelines (cible n'appartenant plus à la mère) ;</li>
     *   <li>les ports ajoutés manuellement sans redéfinition (ports « en trop »).</li>
     * </ul>
     *
     * <p>Appelé AVANT la (re)création des redéfinitions valides. Encadré par la
     * transaction undo de l'appelant (donc annulable).</p>
     */
    private static void pruneNonMirrorPorts(IRPClass reference, IRPModelElement mother) {
        if (reference == null || mother == null) return;

        IRPCollection childPorts;
        try {
            childPorts = reference.getPorts();
        } catch (Exception e) {
            return;
        }
        if (childPorts == null) return;

        // Without the full list of the mother's ancestors, a valid mirror of an
        // inherited port would look like an orphan: delete nothing.
        boolean[] chainComplete = { true };
        List<IRPClass> chain = motherChain(mother, chainComplete);
        if (!chainComplete[0]) {
            rhpLog.warn("[RedefinedPorts] Prune skipped for reference '" + safeName(reference)
                    + "': the ancestors of mother '" + safeName(mother)
                    + "' could not all be read, no port deleted.");
            return;
        }

        // Copie défensive : on supprime des éléments pendant le parcours.
        List<Object> snapshot = new ArrayList<Object>(childPorts.toList());

        for (Object o : snapshot) {
            if (!(o instanceof IRPSysMLPort)) continue;
            IRPSysMLPort childPort = (IRPSysMLPort) o;

            // Une redéfinition valide d'un port de la mère courante -> on garde.
            // Un port illisible (erreur COM) -> on garde aussi : jamais de
            // suppression sur un doute.
            Boolean mirror = mirrorsChain(childPort, chain);
            if (mirror == null) {
                rhpLog.warn("[RedefinedPorts] Port '" + safeName(childPort) + "' in reference '"
                        + safeName(reference) + "' could not be read, kept.");
                continue;
            }
            if (mirror.booleanValue()) continue;

            // Distinguer les deux cas dans le log (les deux sont supprimés).
            String reason = hasAnyRedefines(childPort)
                    ? "orphan redefinition (target not in the mother or its ancestors)"
                    : "extra port (no redefinition of the mother)";

            rhpLog.warn("[RedefinedPorts] Deleting non-mirror port '" + safeName(childPort)
                    + "' in reference '" + safeName(reference)
                    + "' - " + reason + " (mother '" + safeName(mother) + "').");
            try {
                childPort.deleteFromProject();
            } catch (Exception e) {
                rhpLog.warn("[RedefinedPorts] deleteFromProject failed on '"
                        + safeName(childPort) + "': " + e.getMessage());
            }
        }
    }

    /** Vrai si {@code childPort} porte au moins une redéfinition (quelle que soit sa cible). */
    private static boolean hasAnyRedefines(IRPSysMLPort childPort) {
        try {
            IRPCollection redefines = childPort.getRedefines();
            return redefines != null && redefines.getCount() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Whether {@code childPort} redefines a port of the mother or of one of its
     * ancestors ({@code chain}; without them the prune would delete the mirror
     * of a BlackBox port of a WhiteBox mother). {@code null} when it cannot be
     * told (COM error while reading the port or a redefined port's owner): the
     * caller must then keep the port.
     */
    private static Boolean mirrorsChain(IRPSysMLPort childPort, List<IRPClass> chain) {
        List<?> redefines;
        try {
            IRPCollection c = childPort.getRedefines();
            if (c == null) return Boolean.FALSE;
            redefines = c.toList();
        } catch (Exception e) {
            return null;
        }

        boolean unknown = false;
        for (Object r : redefines) {
            if (!(r instanceof IRPModelElement)) continue;
            IRPModelElement owner;
            try {
                owner = ((IRPModelElement) r).getOwner();
            } catch (Exception e) {
                unknown = true;
                continue;
            }
            if (owner != null && containsElement(chain, owner)) {
                return Boolean.TRUE;
            }
        }
        return unknown ? null : Boolean.FALSE;
    }

    /**
     * The mother and its ancestors, nearest first, each class once. The
     * generalizations are followed recursively: a WhiteBox Function that
     * "Redefines Function" a BlackBox one shows the BlackBox ports, and so
     * does every reference to it.
     */
    static List<IRPClass> motherChain(IRPModelElement mother) {
        return motherChain(mother, null);
    }

    /**
     * {@link #motherChain(IRPModelElement)}; {@code complete[0]} is set to false
     * when a generalization could not be read (the chain may then miss ancestors).
     */
    static List<IRPClass> motherChain(IRPModelElement mother, boolean[] complete) {
        List<IRPClass> chain = new ArrayList<IRPClass>();
        if (!(mother instanceof IRPClass)) return chain;
        Set<String> seen = new HashSet<String>();
        Deque<IRPClass> todo = new ArrayDeque<IRPClass>();
        todo.add((IRPClass) mother);
        while (!todo.isEmpty()) {
            IRPClass c = todo.poll();
            String guid = safeGuid(c);
            if (!seen.add(guid != null ? guid : "id:" + System.identityHashCode(c))) continue;
            chain.add(c);
            try {
                IRPCollection gens = c.getGeneralizations();
                if (gens == null) continue;
                for (Object o : gens.toList()) {
                    if (!(o instanceof IRPGeneralization)) continue;
                    IRPModelElement base = ((IRPGeneralization) o).getBaseClass();
                    if (base instanceof IRPClass) todo.add((IRPClass) base);
                }
            } catch (Exception e) {
                // An unreadable generalization: keep the chain found so far.
                if (complete != null) complete[0] = false;
            }
        }
        return chain;
    }

    /**
     * Every port shown on {@code mother}: its own ports, then the ones it
     * inherits along {@link #motherChain}. The nearest definition wins: a port
     * redefined, or shadowed by name, by a port closer to the mother is left out.
     */
    static List<IRPSysMLPort> effectiveMotherPorts(IRPModelElement mother) {
        List<IRPSysMLPort> result = new ArrayList<IRPSysMLPort>();
        Set<String> names = new HashSet<String>();
        List<IRPModelElement> redefinedNearer = new ArrayList<IRPModelElement>();
        for (IRPClass c : motherChain(mother)) {
            IRPCollection ports;
            try { ports = c.getPorts(); } catch (Exception e) { ports = null; }
            if (ports == null) continue;
            for (Object o : ports.toList()) {
                if (!(o instanceof IRPSysMLPort)) continue;
                IRPSysMLPort p = (IRPSysMLPort) o;
                if (containsElement(redefinedNearer, p) || !names.add(safeName(p))) continue;
                result.add(p);
                try {
                    IRPCollection redefines = p.getRedefines();
                    if (redefines == null) continue;
                    for (Object r : redefines.toList()) {
                        if (r instanceof IRPModelElement) redefinedNearer.add((IRPModelElement) r);
                    }
                } catch (Exception ignore) {}
            }
        }
        return result;
    }

    /** True if {@code el} is one of {@code list} (same proxy or same GUID). */
    private static boolean containsElement(List<? extends IRPModelElement> list, IRPModelElement el) {
        for (IRPModelElement x : list) {
            if (x == el || sameElement(x, el)) return true;
        }
        return false;
    }

    private static String safeGuid(IRPModelElement el) {
        try { return el != null ? el.getGUID() : null; } catch (Exception e) { return null; }
    }

    private static boolean sameElement(IRPModelElement a, IRPModelElement b) {
        if (a == null || b == null) return false;
        try {
            String ga = a.getGUID();
            return ga != null && ga.equals(b.getGUID());
        } catch (Exception e) {
            return false;
        }
    }


    private static IRPSysMLPort findOrCreateRedefinedPort(
            IRPClass logicalSystemReference,
            IRPSysMLPort originalPort) {

        if (logicalSystemReference == null || originalPort == null) {
            return null;
        }

        String portName = safeName(originalPort);

        // Réutilisation d'un port redéfini existant (idempotence).
        try {
            IRPModelElement existingPort =
                    logicalSystemReference.findNestedElement(portName, "Flow Port");

            if (existingPort instanceof IRPSysMLPort) {
                return (IRPSysMLPort) existingPort;
            }
        } catch (Exception e) {
            logInfo("PORT : Error while searching existing redefined port " + portName + e.getMessage());
        }

        // Sinon, création via addNewAggr.
        try {
            IRPSysMLPort createdPort =
                    (IRPSysMLPort) logicalSystemReference.addNewAggr("Flow Port", portName);

            ensureRedefinedPortProperties(originalPort, createdPort);

            return createdPort;

        } catch (Exception e) {
            logInfo("PORT Unable to create redefined port " + portName
                    + " in " + safeName(logicalSystemReference) + e.getMessage());
            return null;
        }
    }

    private static void ensureRedefinedPortProperties(
            IRPSysMLPort originalPort,
            IRPSysMLPort redefinedPort) {

        if (originalPort == null || redefinedPort == null) {
            return;
        }

        try {
            if (originalPort.getType() != null) {
                redefinedPort.setType(originalPort.getType());
            }
        } catch (Exception e) {
            logInfo("PORT : Impossible copier type sur " + safeName(redefinedPort) + e.getMessage());
        }

        try {
            redefinedPort.setPortDirection(originalPort.getPortDirection());
        } catch (Exception e) {
            logInfo("PORT : Impossible copier direction sur " + safeName(redefinedPort) + e.getMessage());
        }

        try {
            redefinedPort.addRedefines(originalPort);
        } catch (Exception e) {
            // addRedefines déjà appliqué ou non applicable : silencieux (comportement d'origine).
        }

        copyPortStereotypes(originalPort, redefinedPort);
    }

    private static void copyPortStereotypes(IRPSysMLPort originalPort, IRPSysMLPort redefinedPort) {
        try {
            List<IRPStereotype> portStereotypes = originalPort.getStereotypes().toList();

            for (IRPStereotype stereotype : portStereotypes) {
                if (stereotype != null && stereotype.getIsNewTerm() == 0) {
                    try {
                        redefinedPort.addSpecificStereotype(stereotype);
                    } catch (Exception e) {
                        logInfo("PORT: Cannot apply stereotype on port " + safeName(redefinedPort));
                    }
                }
            }
        } catch (Exception e) {
            logInfo("PORT : Erreur copie stereotypes port sur " + safeName(redefinedPort) + e.getMessage());
        }
    }

    // ==================================================================
    // BESOIN B — Remplacement graphique des ports hérités par les ports
    // redéfinis (flows copiés ou reconnectés, rien de supprimé)
    // ==================================================================

    /**
     * Replaces, on the representation {@code graphElement} of a reference, each
     * drawn inherited port by its redefined port AT THE SAME PLACE:
     * <ul>
     *   <li>both drawn: the redefined port takes the position of the inherited
     *       one, then the inherited one is removed;</li>
     *   <li>only the inherited one drawn: the redefined port is drawn at its
     *       position, then the inherited one is removed (kept if drawing fails,
     *       so a port never vanishes from the diagram);</li>
     *   <li>only the redefined one drawn: left as is (the usual state after a
     *       previous cleanup; it cannot be told apart from a port placed by hand).</li>
     * </ul>
     *
     * @return le nombre de ports hérités retirés (remplacés).
     */
    public static int hideRedefinedPorts(IRPApplication app, IRPGraphElement graphElement) {
        if (graphElement == null) {
            logInfo("Error: graphElement is null");
            return 0;
        }

        IRPModelElement modelElement = graphElement.getModelObject();
        if (modelElement == null) {
            logInfo("Warning: skipping element with null model object");
            return 0;
        }

        // Section critique sérialisée : le scan est refait SOUS le verrou pour
        // qu'un second appel concurrent (autre timer) retrouve les ports déjà
        // remplacés -> rien à faire, au lieu de « Membre introuvable ».
        synchronized (GRAPHICS_CLEANUP_LOCK) {
            return swapOnRepresentation(app, graphElement);
        }
    }

    /**
     * Variante pour une liste de représentations graphiques (ex. sélection d'un
     * élément dans l'arborescence -> {@link #getGraphicalRepresentations}).
     */
    public static int hideRedefinedPorts(IRPApplication app, List<IRPGraphElement> representations) {
        if (representations == null || representations.isEmpty()) {
            return 0;
        }

        synchronized (GRAPHICS_CLEANUP_LOCK) {
            int totalRemoved = 0;
            // PAR représentation : chaque diagramme ne traite que SES propres ports.
            for (IRPGraphElement graphElement : representations) {
                if (graphElement == null) {
                    continue;
                }
                totalRemoved += swapOnRepresentation(app, graphElement);
            }
            return totalRemoved;
        }
    }

    /**
     * One representation: plan, then positions, drawings and ONE removal inside
     * an undo transaction guarded per diagram (a failing diagram neither stops
     * the others nor corrupts the global undo stack). Caller holds the lock.
     */
    private static int swapOnRepresentation(IRPApplication app, IRPGraphElement graphElement) {
        PortSwap swap = planPortSwap(graphElement, getPorts(graphElement));
        if (swap.isEmpty()) {
            return 0;
        }

        IRPDiagram diagram = graphElement.getDiagram();
        if (diagram == null) {
            logInfo("Warning: cannot remove ports because diagram is null");
            return 0;
        }

        // Created before any change: without it nothing is touched.
        IRPCollection removalCollection = app.createNewCollection();
        if (removalCollection == null) {
            logInfo("Warning: cannot create removal collection");
            return 0;
        }

        int[] count = { 0 };
        boolean done = false;
        try {
            done = RhapsodyTx.mutate(app, "hideRedefinedPorts",
                    () -> count[0] = applyPortSwap(app, diagram, graphElement, swap, removalCollection));
        } catch (Exception e) {
            logInfo("hideRedefinedPorts: skipped stale replacement - " + e.getMessage());
        }
        if (!done) {
            return 0;
        }
        if (count[0] > 0) {
            logInfo("Successfully replaced " + count[0] + " inherited port(s) by their redefinition");
        }
        return count[0];
    }

    // ------------------------------------------------------------------
    // Redefined port drawn at the place of the inherited port it replaces
    // ------------------------------------------------------------------

    /** What to change on one representation. */
    static final class PortSwap {
        /** {redefined graphic, inherited graphic}: both drawn. */
        final List<IRPGraphElement[]> moves = new ArrayList<IRPGraphElement[]>();
        /** Redefined port to draw at the place of {@link #inheritedOfAdditions} (same index). */
        final List<IRPModelElement> additions = new ArrayList<IRPModelElement>();
        final List<IRPGraphElement> inheritedOfAdditions = new ArrayList<IRPGraphElement>();
        /** {redefined graphic, other inherited graphic it also redefines}: removed only. */
        final List<IRPGraphElement[]> removals = new ArrayList<IRPGraphElement[]>();

        boolean isEmpty() { return moves.isEmpty() && additions.isEmpty() && removals.isEmpty(); }
    }

    /** Pairs the drawn ports of {@code node} with the redefined ports that replace them. No change. */
    static PortSwap planPortSwap(IRPGraphElement node, List<IRPGraphElement> ports) {
        PortSwap swap = new PortSwap();
        List<IRPGraphElement> pairedInherited = new ArrayList<IRPGraphElement>();

        // 1) Both drawn: a drawn port redefines another drawn port of the same node.
        for (IRPGraphElement g : ports) {
            IRPModelElement m = safeModelObject(g);
            if (m == null) continue;
            boolean placed = false;
            for (IRPModelElement target : redefinedTargets(m)) {
                IRPGraphElement inherited = graphicOf(ports, target, g);
                if (inherited == null || pairedInherited.contains(inherited)) continue;
                if (!placed) {
                    swap.moves.add(new IRPGraphElement[] { g, inherited });   // takes this place
                    placed = true;
                } else {
                    swap.removals.add(new IRPGraphElement[] { g, inherited });
                }
                pairedInherited.add(inherited);
            }
        }

        // 2) Only the inherited one drawn: the reference owns a port redefining it.
        IRPModelElement owner = safeModelObject(node);
        if (!(owner instanceof IRPClass)) return swap;
        IRPCollection ownPorts;
        try { ownPorts = ((IRPClass) owner).getPorts(); } catch (Exception e) { ownPorts = null; }
        if (ownPorts == null) return swap;
        for (IRPGraphElement g : ports) {
            if (pairedInherited.contains(g)) continue;
            IRPModelElement drawn = safeModelObject(g);
            if (drawn == null || isSame(ownerOf(drawn), owner)) continue;   // a port of the reference itself
            for (Object o : ownPorts.toList()) {
                if (!(o instanceof IRPSysMLPort)) continue;
                IRPSysMLPort redefined = (IRPSysMLPort) o;
                if (!containsElement(redefinedTargets(redefined), drawn)) continue;
                if (graphicOf(ports, redefined, null) == null) {
                    swap.additions.add(redefined);
                    swap.inheritedOfAdditions.add(g);
                    pairedInherited.add(g);
                }
                break;
            }
        }
        return swap;
    }

    /**
     * Applies {@code swap} on the representation {@code node} of {@code diagram}:
     * positions first, drawings next, then ONE pass over the flows drawn on the
     * replaced inherited ports ({@link #transferFlows}), then ONE removal of the
     * replaced inherited graphics. An inherited graphic whose flow copy could not
     * be drawn is kept (a flow never vanishes from a diagram).
     *
     * @return the number of inherited graphics removed
     */
    private static int applyPortSwap(IRPApplication app, IRPDiagram diagram, IRPGraphElement node,
            PortSwap swap, IRPCollection removal) {
        // Replaced inherited graphics of this node, each with the graphic of the
        // redefined port that takes its place (same index).
        List<IRPGraphElement> replaced = new ArrayList<IRPGraphElement>();
        List<IRPGraphElement> replacements = new ArrayList<IRPGraphElement>();
        for (IRPGraphElement[] move : swap.moves) {
            copyPosition(move[1], move[0]);
            replaced.add(move[1]);
            replacements.add(move[0]);
        }
        for (IRPGraphElement[] extra : swap.removals) {
            replaced.add(extra[1]);
            replacements.add(extra[0]);
        }
        for (int i = 0; i < swap.additions.size(); i++) {
            IRPGraphElement inherited = swap.inheritedOfAdditions.get(i);
            IRPGraphNode drawn = drawAtPlaceOf(diagram, swap.additions.get(i), inherited);
            if (drawn != null) {
                replaced.add(inherited);
                replacements.add(drawn);
            }
        }
        if (replaced.isEmpty()) return 0;

        List<IRPGraphElement> kept = transferFlows(app, diagram, node, replaced, replacements);

        int count = 0;
        for (IRPGraphElement g : replaced) {
            if (containsGraphic(kept, g)) {
                logInfo("PORT: inherited port '" + safeName(safeModelObject(g)) + "' kept drawn on '"
                        + safeName(safeModelObject(node)) + "' (a flow copy could not be drawn)");
                continue;
            }
            removal.addGraphicalItem(g);
            count++;
        }
        if (count > 0) {
            diagram.removeGraphElements(removal);
        }
        return count;
    }

    // ------------------------------------------------------------------
    // Flows drawn on a replaced inherited port: copied or reconnected,
    // never deleted
    // ------------------------------------------------------------------

    /** What the port swap does with one flow drawn on a replaced inherited port. */
    public enum FlowKind {
        /** A flow of the definition: a copy is created on the redefined port, the flow is kept untouched. */
        COPY,
        /** A flow already attached to a reference (ours): its end is moved to the redefined port. */
        RECONNECT,
        /** Left as is (see the reason). */
        LEFT
    }

    /**
     * One trait of a flow, with what the port swap does with it. Computed from
     * the trait's end graphics BEFORE any change (an old graphic proxy may fail
     * after a model change: "Membre introuvable").
     */
    public static final class FlowTransfer {
        public final IRPFlow flow;
        public final FlowKind kind;
        /** Why the flow is left as is; null unless {@code kind == LEFT}. */
        public final String reason;
        /** Ends of the copy, or of the reconnected flow: redefined port when mapped, else the current end. */
        final IRPModelElement newEnd1;
        final IRPModelElement newEnd2;
        /** Which ends change. */
        final boolean mapped1;
        final boolean mapped2;
        /** {old source, old target, new source, new target} of the trait. */
        final IRPGraphElement[] ends;
        /** The replaced inherited graphics this trait touches. */
        final List<IRPGraphElement> inheritedGraphics;
        /** Name of the copy ("R1_p_2_X_q"), null when not a copy. */
        public final String copyName;

        FlowTransfer(IRPFlow flow, FlowKind kind, String reason, IRPModelElement newEnd1, IRPModelElement newEnd2,
                boolean mapped1, boolean mapped2, IRPGraphElement[] ends, List<IRPGraphElement> inheritedGraphics,
                String copyName) {
            this.flow = flow;
            this.kind = kind;
            this.reason = reason;
            this.newEnd1 = newEnd1;
            this.newEnd2 = newEnd2;
            this.mapped1 = mapped1;
            this.mapped2 = mapped2;
            this.ends = ends;
            this.inheritedGraphics = inheritedGraphics;
            this.copyName = copyName;
        }

        static FlowTransfer left(IRPFlow flow, String reason, IRPGraphElement[] ends,
                List<IRPGraphElement> inheritedGraphics) {
            return new FlowTransfer(flow, FlowKind.LEFT, reason, null, null, false, false, ends,
                    inheritedGraphics, null);
        }
    }

    /**
     * ONE pass over the flows drawn on the replaced inherited graphics of the
     * representation {@code node} of the reference R. A flow's end names the port
     * of the DEFINITION (shared by every reference), only the diagram tells which
     * reference it is drawn on, so:
     * <ul>
     *   <li>a flow of the definition (no end owned by a reference) is never
     *       touched: a COPY with its characteristics is created between R's
     *       redefined port(s) and the other end, and drawn at its place;</li>
     *   <li>a flow already attached to a reference (an end owned by one, e.g. the
     *       copy made while cleaning R1 and now seen on R2's inherited port) is
     *       RECONNECTED to the redefined port, unless a trait of it touches the
     *       port under another box.</li>
     * </ul>
     * One pass per node: a flow between two inherited ports of the same box is
     * handled once, both ends mapped. Never throws.
     *
     * @return the inherited graphics to keep drawn (their flow copy could not be drawn)
     */
    private static List<IRPGraphElement> transferFlows(IRPApplication app, IRPDiagram diagram, IRPGraphElement node,
            List<IRPGraphElement> replaced, List<IRPGraphElement> replacements) {
        List<IRPGraphElement> keep = new ArrayList<IRPGraphElement>();
        IRPModelElement reference = safeModelObject(node);
        if (reference == null) return keep;

        List<IRPModelElement> done = new ArrayList<IRPModelElement>();
        for (IRPGraphEdge edge : edgesTouchingAny(diagram, replaced)) {
            IRPModelElement m = safeModelObject(edge);
            if (!(m instanceof IRPFlow) || containsElement(done, m)) continue;
            IRPFlow flow = (IRPFlow) m;
            done.add(flow);
            try {
                FlowTransfer t = planTransfer(flow, edge, replaced, replacements, reference);
                if (t.kind == FlowKind.LEFT) {
                    logInfo("FLOW: '" + safeName(flow) + "' on '" + safeName(reference)
                            + "' left as is (" + t.reason + ")");
                    continue;
                }
                if (t.kind == FlowKind.COPY) {
                    IRPFlow copy = findFlowBetween(t.newEnd1, t.newEnd2);
                    if (copy != null) {
                        logInfo("FLOW: '" + safeName(flow) + "' already copied as '" + safeName(copy)
                                + "' on '" + safeName(reference) + "'");
                    } else {
                        copy = createFlowCopy(flow, flowOwnerFor(diagram, flow), t, reference);
                    }
                    if (copy == null || !redrawEdge(app, diagram, copy, t.ends)) {
                        keep.addAll(t.inheritedGraphics);
                    }
                } else {
                    if (t.mapped1 && !isSame(flowEnd(flow, 1), t.newEnd1)) flow.setEnd1(t.newEnd1);
                    if (t.mapped2 && !isSame(flowEnd(flow, 2), t.newEnd2)) flow.setEnd2(t.newEnd2);
                    logInfo("FLOW: '" + safeName(flow) + "' reconnected to '"
                            + safeName(t.mapped1 ? t.newEnd1 : t.newEnd2) + "' of '" + safeName(reference) + "'");
                    redrawEdge(app, diagram, flow, t.ends);
                }
            } catch (Exception e) {
                logInfo("FLOW: cannot transfer '" + safeName(flow) + "' - " + e.getMessage());
            }
        }
        return keep;
    }

    /**
     * Decides, without changing anything, what the port swap does with the trait
     * {@code edge} of {@code flow}: each end graphic that is one of the
     * {@code replaced} inherited graphics is mapped to the redefined port drawn
     * by the matching {@code replacement} (same index). When both flow ends name
     * the same port (R1.p to R2.p), the trait's source is end 1 and its target
     * end 2. Everything is read NOW, while the proxies are valid.
     */
    public static FlowTransfer planTransfer(IRPFlow flow, IRPGraphEdge edge, List<IRPGraphElement> replaced,
            List<IRPGraphElement> replacements, IRPModelElement reference) {
        IRPGraphElement[] ends = new IRPGraphElement[4];
        try {
            ends[0] = edge.getSource();
            ends[1] = edge.getTarget();
        } catch (Exception e) {
            rhpLog.debug("FLOW: ends of the old trait unreadable - " + e.getMessage());
        }
        int iSrc = indexOfGraphic(replaced, ends[0]);
        int iTrg = indexOfGraphic(replaced, ends[1]);
        ends[2] = iSrc >= 0 ? replacements.get(iSrc) : ends[0];
        ends[3] = iTrg >= 0 ? replacements.get(iTrg) : ends[1];
        List<IRPGraphElement> touched = new ArrayList<IRPGraphElement>();
        if (iSrc >= 0) touched.add(ends[0]);
        if (iTrg >= 0 && iTrg != iSrc) touched.add(ends[1]);
        if (iSrc < 0 && iTrg < 0) return FlowTransfer.left(flow, "not attached to this port", ends, touched);

        // Which flow end each trait end stands for (source first: a trait goes from end 1 to end 2).
        IRPModelElement end1 = flowEnd(flow, 1);
        IRPModelElement end2 = flowEnd(flow, 2);
        int sideSrc = sideOf(end1, end2, ends[0], ends[2], 1);
        int sideTrg = sideOf(end1, end2, ends[1], ends[3], 2);
        if (sideSrc != 0 && sideSrc == sideTrg) sideTrg = 3 - sideSrc;
        if ((iSrc >= 0 && sideSrc == 0) || (iTrg >= 0 && sideTrg == 0)) {
            return FlowTransfer.left(flow, "not attached to this port", ends, touched);
        }

        IRPModelElement newEnd1 = end1;
        IRPModelElement newEnd2 = end2;
        boolean mapped1 = false;
        boolean mapped2 = false;
        if (iSrc >= 0) {
            if (sideSrc == 1) { newEnd1 = safeModelObject(ends[2]); mapped1 = true; }
            else { newEnd2 = safeModelObject(ends[2]); mapped2 = true; }
        }
        if (iTrg >= 0) {
            if (sideTrg == 1) { newEnd1 = safeModelObject(ends[3]); mapped1 = true; }
            else { newEnd2 = safeModelObject(ends[3]); mapped2 = true; }
        }
        if ((mapped1 && newEnd1 == null) || (mapped2 && newEnd2 == null)) {
            return FlowTransfer.left(flow, "the redefined port cannot be read", ends, touched);
        }

        if (isDefinitionFlow(flow)) {
            IRPGraphElement g1 = sideSrc == 1 ? ends[2] : (sideTrg == 1 ? ends[3] : null);
            IRPGraphElement g2 = sideSrc == 2 ? ends[2] : (sideTrg == 2 ? ends[3] : null);
            String name = copyName(g1, newEnd1, g2, newEnd2);
            return new FlowTransfer(flow, FlowKind.COPY, null, newEnd1, newEnd2, mapped1, mapped2, ends, touched, name);
        }

        // A reference flow: moved, unless a trait of it touches the port under another box.
        for (IRPGraphElement g : touched) {
            IRPModelElement p = safeModelObject(g);
            IRPModelElement redefined = safeModelObject(ends[sameGraphic(g, ends[0]) ? 2 : 3]);
            Boolean onlyHere = drawnOnlyOn(flow, p, redefined, reference);
            if (onlyHere == null) return FlowTransfer.left(flow, "its diagrams cannot be read", ends, touched);
            if (!onlyHere.booleanValue()) return FlowTransfer.left(flow, "also drawn on another box", ends, touched);
        }
        return new FlowTransfer(flow, FlowKind.RECONNECT, null, newEnd1, newEnd2, mapped1, mapped2, ends, touched, null);
    }

    /**
     * The flow end (1 or 2) that the trait end {@code graphic} stands for: the
     * end naming the graphic's port, or already naming the port of its
     * {@code replacement}; {@code preferred} when both ends name it; 0 when none.
     */
    private static int sideOf(IRPModelElement end1, IRPModelElement end2, IRPGraphElement graphic,
            IRPGraphElement replacement, int preferred) {
        IRPModelElement p = safeModelObject(graphic);
        IRPModelElement redefined = (replacement != graphic) ? safeModelObject(replacement) : null;
        boolean on1 = namesPort(end1, p, redefined);
        boolean on2 = namesPort(end2, p, redefined);
        if (on1 && on2) return preferred;
        return on1 ? 1 : (on2 ? 2 : 0);
    }

    /**
     * True when {@code flow} belongs to a definition: none of its ends is a port
     * owned by a reference class. Such a flow is shared by every reference of the
     * definition and is never modified.
     */
    static boolean isDefinitionFlow(IRPFlow flow) {
        return !isReferenceClass(ownerOf(flowEnd(flow, 1))) && !isReferenceClass(ownerOf(flowEnd(flow, 2)));
    }

    /**
     * Name of a flow copy: for each end, in End1/End2 order, the name of the box
     * it is drawn on (the model element shown by the graphical parent of the end
     * graphic; fallback: the owner of the end port) followed by the name of the
     * port, e.g. "R1_p_2_X_q" (an end on a class itself gives only the class
     * name), sanitized to a valid Rhapsody name.
     */
    static String copyName(IRPGraphElement g1, IRPModelElement end1, IRPGraphElement g2, IRPModelElement end2) {
        return sanitizeName(endName(g1, end1) + "_" + endName(g2, end2));
    }

    /** "box_port" for an end on a port, "box" for an end on a class. */
    private static String endName(IRPGraphElement endGraphic, IRPModelElement end) {
        String box = boxName(endGraphic, end);
        if (!(end instanceof IRPSysMLPort) && !(end instanceof IRPPort)) return box;
        String port = safeGetName(end);
        return port.isEmpty() ? box : box + "_" + port;
    }

    private static String boxName(IRPGraphElement endGraphic, IRPModelElement end) {
        // A flow may end on a class itself (GenericFlow): that class is the box.
        if (end != null && !(end instanceof IRPSysMLPort) && !(end instanceof IRPPort)) {
            String n = safeGetName(end);
            if (!n.isEmpty()) return n;
        }
        IRPModelElement box = null;
        try {
            IRPGraphElement parent = (endGraphic != null) ? endGraphic.getGraphicalParent() : null;
            box = safeModelObject(parent);
        } catch (Exception ignore) {}
        if (box == null) box = ownerOf(end);
        String n = safeGetName(box);
        return n.isEmpty() ? "flow" : n;
    }

    /** Letters (accents kept), digits and underscores only; never empty; never starts with a digit. */
    public static String sanitizeName(String raw) {
        if (raw == null) return "flow";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean ok = Character.isLetterOrDigit(c) || c == '_';
            sb.append(ok ? c : '_');
        }
        if (sb.length() == 0) return "flow";
        if (Character.isDigit(sb.charAt(0))) sb.insert(0, '_');
        return sb.toString();
    }

    /**
     * Creates the copy of {@code definition} planned by {@code t} under
     * {@code owner}: same New Term (or a plain Flow), ends set BEFORE the
     * direction, same conveyed items, non-New-Term stereotypes and description;
     * name and label identical, unique under the owner ("R1_p_2_X_q", "R1_p_2_X_q_1", ...).
     *
     * @return the copy, or null when it could not be created
     */
    private static IRPFlow createFlowCopy(IRPFlow definition, IRPModelElement owner, FlowTransfer t,
            IRPModelElement reference) {
        if (owner == null) {
            logInfo("FLOW: '" + safeName(definition) + "' not copied (no owner for the copy)");
            return null;
        }
        String udmc = null;
        try { udmc = definition.getUserDefinedMetaClass(); } catch (Exception ignore) {}
        String metaType = (udmc == null || udmc.isBlank()) ? "Flow" : udmc;
        String name = uniqueFlowName(owner, t.copyName != null ? t.copyName : "flow", metaType);

        IRPFlow copy;
        try {
            IRPModelElement created = owner.addNewAggr(metaType, name);
            if (!(created instanceof IRPFlow)) {
                logInfo("FLOW: '" + safeName(definition) + "' not copied (addNewAggr(" + metaType
                        + ") returned " + (created == null ? "null" : created.getMetaClass()) + ")");
                return null;
            }
            copy = (IRPFlow) created;
        } catch (Exception e) {
            logInfo("FLOW: '" + safeName(definition) + "' not copied - " + e.getMessage());
            return null;
        }

        // Ends first: the direction values refer to them.
        try { copy.setEnd1(t.newEnd1); } catch (Exception e) { logInfo("FLOW: end 1 of '" + name + "' not set - " + e.getMessage()); }
        try { copy.setEnd2(t.newEnd2); } catch (Exception e) { logInfo("FLOW: end 2 of '" + name + "' not set - " + e.getMessage()); }
        try {
            String dir = definition.getDirection();
            if (dir != null && !dir.isBlank()) copy.setDirection(dir);
        } catch (Exception e) {
            logInfo("FLOW: direction of '" + name + "' not set - " + e.getMessage());
        }
        try {
            IRPCollection conveyed = definition.getConveyed();
            if (conveyed != null) {
                for (Object o : conveyed.toList()) {
                    if (o instanceof IRPModelElement) copy.addConveyed((IRPModelElement) o);
                }
            }
        } catch (Exception e) {
            logInfo("FLOW: conveyed items of '" + name + "' not copied - " + e.getMessage());
        }
        try {
            IRPCollection stereotypes = definition.getStereotypes();
            if (stereotypes != null) {
                for (Object o : stereotypes.toList()) {
                    if (!(o instanceof IRPStereotype) || isNewTermStereotype((IRPStereotype) o)) continue;
                    try { copy.addSpecificStereotype((IRPStereotype) o); }
                    catch (Exception e) { logInfo("FLOW: stereotype '" + safeName((IRPStereotype) o) + "' not applied on '" + name + "' - " + e.getMessage()); }
                }
            }
        } catch (Exception e) {
            logInfo("FLOW: stereotypes of '" + name + "' not copied - " + e.getMessage());
        }
        try {
            String desc = definition.getDescription();
            if (desc != null && !desc.isEmpty()) copy.setDescription(desc);
        } catch (Exception e) {
            logInfo("FLOW: description of '" + name + "' not copied - " + e.getMessage());
        }
        try { copy.setDisplayName(name); } catch (Exception e) { logInfo("FLOW: label of '" + name + "' not set - " + e.getMessage()); }

        logInfo("FLOW: '" + safeName(definition) + "' copied as '" + name + "' (" + safeName(t.newEnd1) + " -> "
                + safeName(t.newEnd2) + ") on '" + safeName(reference) + "', definition flow kept");
        return copy;
    }

    /** Owner of a flow copy: the diagram's owner (where the engineer draws it), else the definition's. */
    private static IRPModelElement flowOwnerFor(IRPDiagram diagram, IRPFlow definition) {
        IRPModelElement owner = ownerOf(diagram);
        return owner != null ? owner : ownerOf(definition);
    }

    /**
     * {@code base}, or {@code base_1}, {@code base_2}... : the first name no
     * element of {@code owner} has (same metaclass first, then any nested
     * element, as {@link Flow} does for a moved flow).
     */
    static String uniqueFlowName(IRPModelElement owner, String base, String metaType) {
        String candidate = base;
        int k = 1;
        while (hasNestedNamed(owner, candidate, metaType) && k < 1000) {
            candidate = base + "_" + k;
            k++;
        }
        return candidate;
    }

    private static boolean hasNestedNamed(IRPModelElement owner, String name, String metaType) {
        if (owner == null || name == null) return false;
        try {
            if (owner.findNestedElement(name, metaType) != null) return true;
        } catch (Exception ignore) {}
        try {
            IRPCollection nested = owner.getNestedElements();
            if (nested != null) {
                for (Object o : nested.toList()) {
                    if (o instanceof IRPModelElement && name.equals(safeGetName((IRPModelElement) o))) return true;
                }
            }
        } catch (Exception ignore) {}
        return false;
    }

    /**
     * The flow already joining {@code a} and {@code b} (either orientation),
     * found among the elements referencing {@code a}; null when none or
     * unreadable. The ends alone decide, as {@link Flow}'s duplicate check does.
     */
    static IRPFlow findFlowBetween(IRPModelElement a, IRPModelElement b) {
        if (a == null || b == null) return null;
        try {
            IRPCollection refs = a.getReferences();
            if (refs == null) return null;
            for (Object o : refs.toList()) {
                if (!(o instanceof IRPFlow)) continue;
                IRPFlow f = (IRPFlow) o;
                IRPModelElement e1 = flowEnd(f, 1);
                IRPModelElement e2 = flowEnd(f, 2);
                if ((isSame(e1, a) && isSame(e2, b)) || (isSame(e1, b) && isSame(e2, a))) return f;
            }
        } catch (Exception ignore) {}
        return null;
    }

    private static boolean namesPort(IRPModelElement end, IRPModelElement p, IRPModelElement redefined) {
        return isSame(end, p) || (redefined != null && isSame(end, redefined));
    }

    /** Index of {@code g} in {@code list} (same proxy or equal), -1 when absent. */
    private static int indexOfGraphic(List<IRPGraphElement> list, IRPGraphElement g) {
        for (int i = 0; i < list.size(); i++) {
            if (sameGraphic(list.get(i), g)) return i;
        }
        return -1;
    }

    private static boolean containsGraphic(List<IRPGraphElement> list, IRPGraphElement g) {
        return indexOfGraphic(list, g) >= 0;
    }

    /** Traits of {@code diagram} starting or ending on one of {@code portGraphics}, each once. */
    private static List<IRPGraphEdge> edgesTouchingAny(IRPDiagram diagram, List<IRPGraphElement> portGraphics) {
        List<IRPGraphEdge> out = new ArrayList<IRPGraphEdge>();
        if (portGraphics.isEmpty()) return out;
        try {
            for (Object o : diagram.getGraphicalElements().toList()) {
                if (!(o instanceof IRPGraphEdge)) continue;
                IRPGraphEdge edge = (IRPGraphEdge) o;
                IRPGraphElement s = edge.getSource();
                IRPGraphElement t = edge.getTarget();
                if (containsGraphic(portGraphics, s) || containsGraphic(portGraphics, t)) out.add(edge);
            }
        } catch (Exception e) {
            logInfo("FLOW: traits of the diagram unreadable - " + e.getMessage());
        }
        return out;
    }

    private static String safeGetName(IRPModelElement e) {
        try { String n = e != null ? e.getName() : null; return n == null ? "" : n; } catch (Exception ex) { return ""; }
    }

    private static boolean isNewTermStereotype(IRPStereotype st) {
        try { return st != null && st.getIsNewTerm() == 1; } catch (Exception e) { return false; }
    }

    /** Port named by end 1 or 2 of {@code flow}, or null. */
    private static IRPModelElement flowEnd(IRPFlow flow, int end) {
        try {
            IRPModelElement port = (end == 1) ? flow.getEnd1SysMLPort() : flow.getEnd2SysMLPort();
            if (port != null) return port;
        } catch (Exception ignore) {}
        try {
            return (end == 1) ? flow.getEnd1() : flow.getEnd2();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * TRUE when every trait of {@code flow}, in every diagram, touches p (or the
     * redefined port) under a box of {@code reference}; FALSE when a trait
     * touches it only under other boxes; null when a diagram cannot be read.
     * A trait whose two ends are on p (two references of the same mother) is
     * fine as long as one of them is under {@code reference}.
     */
    private static Boolean drawnOnlyOn(IRPFlow flow, IRPModelElement p, IRPModelElement redefined,
            IRPModelElement reference) {
        try {
            IRPCollection refs = flow.getReferences();
            if (refs == null) return Boolean.TRUE;
            for (Object o : refs.toList()) {
                if (!(o instanceof IRPDiagram)) continue;
                IRPCollection graphics = ((IRPDiagram) o).getCorrespondingGraphicElements(flow);
                if (graphics == null) continue;
                for (Object ge : graphics.toList()) {
                    if (!(ge instanceof IRPGraphEdge)) continue;
                    IRPGraphEdge edge = (IRPGraphEdge) ge;
                    int onPort = 0;
                    boolean underReference = false;
                    for (IRPGraphElement endGraphic : new IRPGraphElement[] { edge.getSource(), edge.getTarget() }) {
                        if (!namesPort(safeModelObject(endGraphic), p, redefined)) continue;
                        onPort++;
                        IRPGraphElement box = endGraphic.getGraphicalParent();
                        if (isSame(safeModelObject(box), reference)) underReference = true;
                    }
                    if (onPort > 0 && !underReference) return Boolean.FALSE;
                }
            }
            return Boolean.TRUE;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Draws {@code flow} between the new ends {@code ends[2]} and {@code ends[3]}
     * (see {@link #planTransfer}: the ends of the old trait, the inherited port
     * replaced by the redefined one, read before any model change). For a
     * reconnected flow the old trait is never touched through its old proxy:
     * the traits still shown for the flow are read again and removed, unless
     * one of them already joins the two new ends (Rhapsody rerouted it); a
     * fresh copy has no trait yet. Then Rhapsody draws the trait itself, like
     * its "Complete Relations" command ({@link IRPDiagram#completeRelations},
     * 0 = only between the two ports); {@code addNewEdgeForElement} is kept as
     * a fallback. None of these steps aborts the next ones.
     *
     * @return true when the flow is drawn between the two new ends afterwards
     */
    private static boolean redrawEdge(IRPApplication app, IRPDiagram diagram, IRPFlow flow, IRPGraphElement[] ends) {
        String name = safeName(flow);
        IRPGraphElement newSrc = ends[2];
        IRPGraphElement newTrg = ends[3];
        if (newSrc == null || newTrg == null) {
            logInfo("FLOW: trait of '" + name + "' not redrawn (an end is missing)");
            return false;
        }

        // Fresh proxies: the old trait may be gone or rebuilt since setEnd.
        List<IRPGraphEdge> traits = traitsOf(diagram, flow);
        for (IRPGraphEdge trait : traits) {
            if (joins(trait, newSrc, newTrg)) {
                rhpLog.debug("FLOW: trait of '" + name + "' already rerouted by Rhapsody");
                return true;
            }
        }

        // One collection per old-trait removal, always created before the ends one.
        IRPCollection old = app.createNewCollection();
        if (old == null) return false;
        if (!traits.isEmpty()) {
            try {
                for (IRPGraphEdge trait : traits) old.addGraphicalItem(trait);
                diagram.removeGraphElements(old);
            } catch (Exception e) {
                rhpLog.debug("FLOW: old trait of '" + name + "' not removed - " + e.getMessage());
            }
        }

        IRPCollection endsCollection = app.createNewCollection();
        if (endsCollection != null) {
            try {
                endsCollection.addGraphicalItem(newSrc);
                endsCollection.addGraphicalItem(newTrg);
                diagram.completeRelations(endsCollection, 0);
            } catch (Exception e) {
                logInfo("FLOW: completeRelations failed for '" + name + "' - " + e.getMessage());
            }
        }
        if (isDrawn(diagram, flow)) {
            rhpLog.debug("FLOW: trait of '" + name + "' drawn (completeRelations)");
            return true;
        }

        // Fallback: explicit connector between the two port graphics.
        if (newSrc instanceof IRPGraphNode && newTrg instanceof IRPGraphNode) {
            int[] c1 = centerOf(newSrc);
            int[] c2 = centerOf(newTrg);
            try {
                diagram.addNewEdgeForElement(flow, (IRPGraphNode) newSrc, c1[0], c1[1],
                        (IRPGraphNode) newTrg, c2[0], c2[1]);
            } catch (Exception e) {
                logInfo("FLOW: addNewEdgeForElement failed for '" + name + "' - " + e.getMessage());
            }
        }
        if (isDrawn(diagram, flow)) {
            rhpLog.debug("FLOW: trait of '" + name + "' drawn (addNewEdgeForElement)");
            return true;
        }
        rhpLog.warn("FLOW: trait of '" + name + "' could not be drawn (the flow exists in the model)");
        return false;
    }

    /** Traits (fresh proxies) showing {@code flow} in {@code diagram}; empty when unreadable. */
    private static List<IRPGraphEdge> traitsOf(IRPDiagram diagram, IRPFlow flow) {
        List<IRPGraphEdge> out = new ArrayList<IRPGraphEdge>();
        try {
            IRPCollection graphics = diagram.getCorrespondingGraphicElements(flow);
            if (graphics == null) return out;
            for (Object o : graphics.toList()) {
                if (o instanceof IRPGraphEdge) out.add((IRPGraphEdge) o);
            }
        } catch (Exception ignore) {}
        return out;
    }

    /** True when {@code trait} goes from {@code a} to {@code b} or from {@code b} to {@code a}. */
    private static boolean joins(IRPGraphEdge trait, IRPGraphElement a, IRPGraphElement b) {
        try {
            IRPGraphElement s = trait.getSource();
            IRPGraphElement t = trait.getTarget();
            return (sameGraphic(s, a) && sameGraphic(t, b)) || (sameGraphic(s, b) && sameGraphic(t, a));
        } catch (Exception e) {
            return false;
        }
    }

    /** True when {@code diagram} shows {@code flow} as a connector. */
    private static boolean isDrawn(IRPDiagram diagram, IRPFlow flow) {
        try {
            IRPCollection graphics = diagram.getCorrespondingGraphicElements(flow);
            if (graphics == null) return false;
            for (Object o : graphics.toList()) {
                if (o instanceof IRPGraphEdge) return true;
            }
        } catch (Exception ignore) {}
        return false;
    }

    /** Centre of a box from its Position / Width / Height, 0 when unreadable. */
    private static int[] centerOf(IRPGraphElement g) {
        int[] xy = parseInts(graphicalValue(g, "Position"), 2);
        int[] w = parseInts(graphicalValue(g, "Width"), 1);
        int[] h = parseInts(graphicalValue(g, "Height"), 1);
        int x = (xy != null) ? xy[0] : 0;
        int y = (xy != null) ? xy[1] : 0;
        return new int[] { x + ((w != null) ? w[0] / 2 : 0), y + ((h != null) ? h[0] / 2 : 0) };
    }

    private static boolean sameGraphic(IRPGraphElement a, IRPGraphElement b) {
        return a != null && b != null && (a == b || a.equals(b));
    }

    /** Gives {@code to} the position of {@code from}. Never throws. */
    private static void copyPosition(IRPGraphElement from, IRPGraphElement to) {
        String position = graphicalValue(from, "Position");
        if (position == null) {
            logInfo("PORT: position of the inherited port unreadable, redefined port left where it is");
            return;
        }
        try {
            to.setGraphicalProperty("Position", position);
            rhpLog.debug("PORT: '" + safeName(safeModelObject(to)) + "' moved to " + position
                    + " (place of the inherited port)");
        } catch (Exception e) {
            logInfo("PORT: cannot move '" + safeName(safeModelObject(to)) + "' to " + position
                    + " - " + e.getMessage());
        }
    }

    /**
     * Draws {@code port} at the position and size of {@code inherited}.
     *
     * @return the drawn port, or null
     */
    private static IRPGraphNode drawAtPlaceOf(IRPDiagram diagram, IRPModelElement port, IRPGraphElement inherited) {
        int[] xy = parseInts(graphicalValue(inherited, "Position"), 2);
        if (xy == null) {
            logInfo("PORT: position of the inherited port unreadable, '" + safeName(port) + "' not drawn");
            return null;
        }
        int[] w = parseInts(graphicalValue(inherited, "Width"), 1);
        int[] h = parseInts(graphicalValue(inherited, "Height"), 1);
        int width = (w != null) ? w[0] : 12;
        int height = (h != null) ? h[0] : 12;
        try {
            IRPGraphNode drawn = diagram.addNewNodeForElement(port, xy[0], xy[1], width, height);
            rhpLog.debug("PORT: '" + safeName(port) + "' drawn at " + xy[0] + "," + xy[1]
                    + " (" + width + "x" + height + ") -> " + (drawn != null));
            return drawn;
        } catch (Exception e) {
            logInfo("PORT: cannot draw '" + safeName(port) + "' - " + e.getMessage());
            return null;
        }
    }

    /** Value of a graphical property, or null. Never throws. */
    private static String graphicalValue(IRPGraphElement g, String property) {
        try {
            IRPGraphicalProperty p = g.getGraphicalProperty(property);
            String v = (p != null) ? p.getValue() : null;
            return (v == null || v.isBlank()) ? null : v.trim();
        } catch (Exception e) {
            return null;
        }
    }

    /** The first {@code n} integers of "a,b,..." (spaces allowed), or null. */
    private static int[] parseInts(String value, int n) {
        if (value == null) return null;
        String[] parts = value.split(",");
        if (parts.length < n) return null;
        int[] out = new int[n];
        try {
            for (int i = 0; i < n; i++) out[i] = Integer.parseInt(parts[i].trim());
        } catch (NumberFormatException e) {
            return null;
        }
        return out;
    }

    /** Graphic of {@code ports}, other than {@code except}, showing {@code model}; or null. */
    private static IRPGraphElement graphicOf(List<IRPGraphElement> ports, IRPModelElement model,
            IRPGraphElement except) {
        for (IRPGraphElement g : ports) {
            if (g == except) continue;
            if (isSame(safeModelObject(g), model)) return g;
        }
        return null;
    }

    /** Ports that {@code port} redefines; empty when unreadable. */
    private static List<IRPModelElement> redefinedTargets(IRPModelElement port) {
        List<IRPModelElement> out = new ArrayList<IRPModelElement>();
        try {
            IRPCollection c = port.getRedefines();
            if (c == null) return out;
            for (Object o : c.toList()) {
                if (o instanceof IRPModelElement) out.add((IRPModelElement) o);
            }
        } catch (Exception ignore) {}
        return out;
    }

    private static boolean isSame(IRPModelElement a, IRPModelElement b) {
        return a != null && b != null && (a == b || sameElement(a, b));
    }

    private static IRPModelElement safeModelObject(IRPGraphElement g) {
        try { return g != null ? g.getModelObject() : null; } catch (Exception e) { return null; }
    }

    private static IRPModelElement ownerOf(IRPModelElement el) {
        try { return el != null ? el.getOwner() : null; } catch (Exception e) { return null; }
    }

    // ------------------------------------------------------------------
    // Détection des ports graphiques et des ports redéfinis à retirer
    // ------------------------------------------------------------------

    private static List<IRPGraphElement> getPorts(IRPGraphElement graphElement) {
        List<IRPGraphElement> ports = new ArrayList<IRPGraphElement>();

        if (graphElement == null) {
            logInfo("Error: graphElement is null");
            return ports;
        }

        IRPDiagram diagram = graphElement.getDiagram();
        if (diagram == null) {
            logInfo("Error: diagram is null for graphElement");
            return ports;
        }

        IRPModelElement parentModelElement = graphElement.getModelObject();
        if (parentModelElement == null) {
            logInfo("Error: model object is null for graphElement");
            return ports;
        }

        for (Object object : diagram.getGraphicalElements().toList()) {
            if (!(object instanceof IRPGraphElement)) {
                continue;
            }

            IRPGraphElement currentGraphElement = (IRPGraphElement) object;
            IRPGraphElement graphicalParent = currentGraphElement.getGraphicalParent();

            if (graphicalParent == null || !graphicalParent.equals(graphElement)) {
                continue;
            }

            IRPModelElement modelElement = currentGraphElement.getModelObject();
            if (modelElement == null) {
                continue;
            }

            String userDefinedMetaclass = modelElement.getUserDefinedMetaClass();
            String metaclass = modelElement.getMetaClass();

            if (!"Flow Port".equals(userDefinedMetaclass)
                    && !"SysMLPort".equals(metaclass)
                    && !"Port".equals(metaclass)) {
                continue;
            }

            ports.add(currentGraphElement);
        }

        return ports;
    }

    private static List<IRPGraphElement> getPorts(List<IRPGraphElement> representations) {
        List<IRPGraphElement> ports = new ArrayList<IRPGraphElement>();
        if (representations == null) return ports;

        for (IRPGraphElement graphElement : representations) {
            ports.addAll(getPorts(graphElement));
        }
        return ports;
    }

    /**
     * Représentations graphiques d'un élément modèle dans les diagrammes qui le
     * référencent (utilisé pour une sélection depuis l'arborescence).
     */
    public static List<IRPGraphElement> getGraphicalRepresentations(IRPModelElement modelElement) {

        List<IRPGraphElement> graphicalRepresentations = new ArrayList<IRPGraphElement>();

        if (modelElement == null) {
            logInfo("Warning: model element is null");
            return graphicalRepresentations;
        }

        IRPCollection references;
        try {
            references = modelElement.getReferences();
        } catch (Exception e) {
            logInfo("Warning: cannot retrieve references for " + modelElement.getName() + " - " + e.getMessage());
            return graphicalRepresentations;
        }

        if (references == null || references.getCount() == 0) {
            logInfo("No reference found for model element: " + modelElement.getName());
            return graphicalRepresentations;
        }

        for (Object referenceObject : references.toList()) {
            if (!(referenceObject instanceof IRPDiagram)) {
                continue;
            }

            IRPDiagram diagram = (IRPDiagram) referenceObject;
            IRPCollection correspondingGraphics;
            try {
                correspondingGraphics = diagram.getCorrespondingGraphicElements(modelElement);
            } catch (Exception e) {
                logInfo("Warning: cannot retrieve graphical representations from diagram "
                        + diagram.getName() + " - " + e.getMessage());
                continue;
            }

            if (correspondingGraphics == null || correspondingGraphics.getCount() == 0) {
                continue;
            }

            for (Object graphicalObject : correspondingGraphics.toList()) {
                if (!(graphicalObject instanceof IRPGraphElement)) {
                    continue;
                }
                graphicalRepresentations.add((IRPGraphElement) graphicalObject);
                logInfo("Graphical representation found in diagram: " + diagram.getName());
            }
        }

        return graphicalRepresentations;
    }

    // ------------------------------------------------------------------
    // Utilitaires
    // ------------------------------------------------------------------

    private static String safeName(IRPModelElement element) {
        if (element == null) {
            return "null";
        }
        try {
            String name = getElementText(element);
            return name != null ? name : "null";
        } catch (Exception e) {
            return "<?>";
        }
    }

    private static String getElementText(final IRPModelElement element) {
        if (element == null) {
            return "null";
        }
        try {
            final String value = LABEL_ON ? element.getDisplayName() : element.getName();
            if (value == null || value.trim().isEmpty()) {
                return "<unnamed>";
            }
            return value;
        } catch (Exception e) {
            return "<unavailable>";
        }
    }

    private static void logInfo(String message) {
        rhpLog.info(message);
    }
}
