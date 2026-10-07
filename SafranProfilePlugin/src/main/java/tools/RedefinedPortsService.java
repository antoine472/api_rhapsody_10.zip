package tools;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPClassifier;
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
 */
public final class RedefinedPortsService {

    private static final RhapsodyLogger rhpLog = RhapsodyLogger.getInstance();

    /** Si vrai, on utilise getDisplayName() au lieu de getName() pour les libellés. */
    private static final boolean LABEL_ON = false;

    /** Métaclasses utilisateur des liens de référence gérés. */
    private static final String REF_FUNCTION = "References Function";
    private static final String REF_LOGICAL_SYSTEM = "References Logical System";

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

        // NB : plus d'élagage automatique ici. La suppression des ports
        // « non-miroir » est désormais décidée port par port par l'ingénieur
        // (outil "Update Redefined Ports" -> deleteOrphanPort).

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
     *
     * <p><b>Plus appelé</b> depuis {@link #redefinePorts(IRPModelElement)} : la
     * suppression est désormais confirmée port par port par l'ingénieur
     * ({@link #deleteOrphanPort(IRPSysMLPort)}). Conservé pour référence.</p>
     */
    @SuppressWarnings("unused")
    private static void pruneNonMirrorPorts(IRPClass reference, IRPModelElement mother) {
        if (reference == null || mother == null) return;

        IRPCollection childPorts;
        try {
            childPorts = reference.getPorts();
        } catch (Exception e) {
            return;
        }
        if (childPorts == null) return;

        // Copie défensive : on supprime des éléments pendant le parcours.
        List<Object> snapshot = new ArrayList<Object>(childPorts.toList());

        for (Object o : snapshot) {
            if (!(o instanceof IRPSysMLPort)) continue;
            IRPSysMLPort childPort = (IRPSysMLPort) o;

            // Une redéfinition valide d'un port de la mère courante -> on garde.
            if (redefinesCurrentMotherPort(childPort, mother)) continue;

            // Homonyme d'un port de la mère courante -> port à ADOPTER (réutiliser +
            // ajouter le lien de redéfinition + corriger les propriétés/direction),
            // surtout PAS à supprimer : supprimer+recréer détruirait les flows
            // rattachés à ce port.
            if (findPortByName(effectiveMotherPorts(mother), safeName(childPort)) != null) {
                continue;
            }

            // Distinguer les deux cas dans le log (les deux sont supprimés).
            String reason = hasAnyRedefines(childPort)
                    ? "orphan redefinition (target not owned by current mother)"
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

    /**
     * Ports of {@code reference} that redefine a port owned by {@code mother}
     * (the mirror of that mother). Empty when none; never throws.
     */
    public static List<IRPSysMLPort> portsRedefining(IRPClass reference, IRPModelElement mother) {
        List<IRPSysMLPort> out = new ArrayList<IRPSysMLPort>();
        if (reference == null || mother == null) return out;
        IRPCollection ports = safePorts(reference);
        if (ports == null) return out;
        for (Object o : ports.toList()) {
            if (o instanceof IRPSysMLPort && redefinesCurrentMotherPort((IRPSysMLPort) o, mother)) {
                out.add((IRPSysMLPort) o);
            }
        }
        return out;
    }

    /** True if {@code port} redefines at least one port owned by {@code mother}. */
    public static boolean redefinesPortOf(IRPSysMLPort port, IRPModelElement mother) {
        return port != null && mother != null && redefinesCurrentMotherPort(port, mother);
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
     * Vrai si {@code childPort} redéfinit au moins un port de la classe mère
     * courante ou d'un de ses ancêtres (ports hérités par la mère).
     */
    private static boolean redefinesCurrentMotherPort(IRPSysMLPort childPort, IRPModelElement mother) {
        return redefinedMotherPort(childPort, mother) != null;
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

    private static IRPModelElement safeOwner(IRPModelElement el) {
        try { return el != null ? el.getOwner() : null; } catch (Exception e) { return null; }
    }

    // ==================================================================
    // Plan (dry-run) - pour la commande "Update Redefined Ports"
    // ==================================================================

    /**
     * Difference entre une reference et sa mere : ports a ajouter / mettre a jour
     * (appliques automatiquement) et candidats soumis a confirmation port par
     * port : redefinitions renommees ({@link RenameCandidate}) et ports sans
     * correspondance dans la mere ({@link OrphanCandidate}).
     */
    public static final class PortPlan {

        /** Redefinition d'un port mere dont le nom differe sur la fille. */
        public static final class RenameCandidate {
            public final IRPSysMLPort port;
            public final IRPSysMLPort motherPort;   // pour resynchroniser Name ET Label (DisplayName)
            public final String currentName;
            public final String motherName;
            public final String referenceName;

            public RenameCandidate(IRPSysMLPort port, IRPSysMLPort motherPort,
                    String currentName, String motherName, String referenceName) {
                this.port = port;
                this.motherPort = motherPort;
                this.currentName = currentName;
                this.motherName = motherName;
                this.referenceName = referenceName;
            }
        }

        /** Port de la fille sans redefinition ni homonyme dans la mere courante. */
        public static final class OrphanCandidate {
            public final IRPSysMLPort port;
            public final String name;
            public final String referenceName;

            public OrphanCandidate(IRPSysMLPort port, String name, String referenceName) {
                this.port = port;
                this.name = name;
                this.referenceName = referenceName;
            }
        }

        public final String referenceName;
        public final List<String> toAdd = new ArrayList<String>();
        public final List<String> toRemove = new ArrayList<String>();
        public final List<String> toUpdate = new ArrayList<String>();
        public final List<RenameCandidate> renameCandidates = new ArrayList<RenameCandidate>();
        public final List<OrphanCandidate> orphanCandidates = new ArrayList<OrphanCandidate>();

        PortPlan(String referenceName) { this.referenceName = referenceName; }

        public boolean hasChanges()  {
            return !toAdd.isEmpty() || !toRemove.isEmpty() || !toUpdate.isEmpty()
                    || !renameCandidates.isEmpty() || !orphanCandidates.isEmpty();
        }
        public boolean hasRemovals() { return !toRemove.isEmpty(); }
    }

    /** Calcule, sans rien modifier, le plan de mise a jour d'une reference. */
    public static PortPlan computePlan(IRPModelElement referenceEl) {
        PortPlan plan = new PortPlan(safeName(referenceEl));
        if (!(referenceEl instanceof IRPClass)) return plan;

        IRPClass reference = (IRPClass) referenceEl;
        IRPModelElement mother = resolveMotherClassifier(reference);
        if (!(mother instanceof IRPClass)) return plan;

        // Every port shown on the mother, inherited ones included (see effectiveMotherPorts).
        List<IRPSysMLPort> motherPorts = effectiveMotherPorts(mother);

        // Ports de la fille : jamais supprimés ni renommés automatiquement. On
        // collecte des CANDIDATS soumis à confirmation port par port.
        IRPCollection childPorts = safePorts(reference);
        if (childPorts != null) {
            for (Object o : childPorts.toList()) {
                if (!(o instanceof IRPSysMLPort)) continue;
                IRPSysMLPort childPort = (IRPSysMLPort) o;

                IRPModelElement motherTarget = redefinedMotherPort(childPort, mother);
                if (motherTarget != null) {
                    // Redéfinition d'un port de la mère courante : seul le nom peut
                    // diverger (les propriétés sont traitées par la boucle mère).
                    IRPSysMLPort motherPortEl =
                            (motherTarget instanceof IRPSysMLPort) ? (IRPSysMLPort) motherTarget : null;
                    // Candidat au renommage si le Name OU le Label (DisplayName) diffère.
                    if (motherPortEl != null && nameOrLabelDiffers(childPort, motherPortEl)) {
                        plan.renameCandidates.add(new PortPlan.RenameCandidate(
                                childPort, motherPortEl,
                                describePort(childPort), describePort(motherPortEl),
                                plan.referenceName));
                    }
                } else if (findPortByName(motherPorts, safeName(childPort)) != null) {
                    // Homonyme d'un port mère : adopté (mise à jour) par la boucle mère.
                } else {
                    // Ni redéfinition ni homonyme : orphelin, suppression à confirmer.
                    plan.orphanCandidates.add(new PortPlan.OrphanCandidate(
                            childPort, safeName(childPort), plan.referenceName));
                }
            }
        }

        // Ports de la mere : manquant -> a ajouter ; present mais proprietes
        // differentes -> a mettre a jour.
        for (IRPSysMLPort motherPort : motherPorts) {
            IRPSysMLPort owned = findOwnedRedefinition(reference, motherPort);
            if (owned != null) {
                if (portPropertiesDiffer(motherPort, owned)) {
                    plan.toUpdate.add(safeName(motherPort));
                }
            } else {
                // Pas de redéfinition liée : un port homonyme existe-t-il ?
                // Oui -> à adopter (mise à jour : lien + propriétés). Non -> à ajouter.
                IRPSysMLPort byName = findPortByName(reference, safeName(motherPort));
                if (byName == null) {
                    plan.toAdd.add(safeName(motherPort));
                } else {
                    plan.toUpdate.add(safeName(motherPort));
                }
            }
        }
        return plan;
    }

    /** Vrai si au moins une propriete synchronisee differe entre le port mere et le port redefini. */
    private static boolean portPropertiesDiffer(IRPSysMLPort motherPort, IRPSysMLPort redefinedPort) {
        // Type
        try {
            IRPClassifier srcType = motherPort.getType();
            if (srcType != null && !sameElement(srcType, redefinedPort.getType())) return true;
        } catch (Exception ignore) {}
        // Direction
        try {
            String srcDir = motherPort.getPortDirection();
            if (srcDir != null && !srcDir.equals(redefinedPort.getPortDirection())) return true;
        } catch (Exception ignore) {}
        // Description
        try {
            String srcDesc = motherPort.getDescription();
            if (srcDesc != null && !srcDesc.equals(redefinedPort.getDescription())) return true;
        } catch (Exception ignore) {}
        // Stereotypes (non New Term) : difference dans un sens OU dans l'autre.
        try {
            @SuppressWarnings("unchecked")
            List<IRPStereotype> src = motherPort.getStereotypes().toList();
            IRPCollection tgtCol = redefinedPort.getStereotypes();
            @SuppressWarnings("unchecked")
            List<IRPStereotype> tgt = (tgtCol != null)
                    ? (List<IRPStereotype>) tgtCol.toList()
                    : java.util.Collections.<IRPStereotype>emptyList();

            // Manquant sur la fille (present sur la mere).
            for (IRPStereotype st : src) {
                if (st == null || isNewTermStereotype(st)) continue;
                if (!containsStereotype(tgt, st)) return true;
            }
            // En trop sur la fille (absent de la mere).
            for (IRPStereotype st : tgt) {
                if (st == null || isNewTermStereotype(st)) continue;
                if (!containsStereotype(src, st)) return true;
            }
        } catch (Exception ignore) {}
        return false;
    }

    /** Resout la classe mere d'une reference via sa generalisation de reference. */
    static IRPModelElement resolveMotherClassifier(IRPClass reference) {
        IRPCollection generalisations;
        try { generalisations = reference.getGeneralizations(); }
        catch (Exception e) { return null; }
        if (generalisations == null) return null;

        for (Object object : generalisations.toList()) {
            if (!(object instanceof IRPGeneralization)) continue;
            IRPGeneralization g = (IRPGeneralization) object;
            String udm;
            try { udm = g.getUserDefinedMetaClass(); } catch (Exception e) { continue; }
            if (REF_FUNCTION.equals(udm) || REF_LOGICAL_SYSTEM.equals(udm)) {
                try {
                    IRPModelElement base = g.getBaseClass();
                    if (base != null) return base;
                } catch (Exception ignore) {}
            }
        }
        return null;
    }

    /** The port of the mother (or of one of its ancestors) that {@code childPort} redefines, or null. */
    private static IRPModelElement redefinedMotherPort(IRPSysMLPort childPort, IRPModelElement mother) {
        IRPCollection redefines;
        try { redefines = childPort.getRedefines(); } catch (Exception e) { return null; }
        if (redefines == null) return null;
        List<IRPClass> chain = motherChain(mother);
        for (Object r : redefines.toList()) {
            if (!(r instanceof IRPModelElement)) continue;
            IRPModelElement target = (IRPModelElement) r;
            if (ownedInChain(target, chain)) return target;
        }
        return null;
    }

    private static IRPCollection safePorts(IRPClass c) {
        try { return c.getPorts(); } catch (Exception e) { return null; }
    }

    /**
     * The mother and its ancestors, nearest first, each class once. The
     * generalizations are followed recursively: a WhiteBox Function that
     * "Redefines Function" a BlackBox one shows the BlackBox ports, and so
     * does every reference to it.
     */
    static List<IRPClass> motherChain(IRPModelElement mother) {
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
            IRPCollection ports = safePorts(c);
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

    /** True if {@code port} is owned by one of the classes of {@code chain}. */
    private static boolean ownedInChain(IRPModelElement port, List<IRPClass> chain) {
        IRPModelElement owner = safeOwner(port);
        return owner != null && containsElement(chain, owner);
    }

    /** Port of {@code ports} named {@code name}, or null. */
    private static IRPSysMLPort findPortByName(List<IRPSysMLPort> ports, String name) {
        if (name == null) return null;
        for (IRPSysMLPort p : ports) {
            if (name.equals(safeName(p))) return p;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Application des décisions de l'ingénieur (outil "Update Redefined Ports")
    // ------------------------------------------------------------------

    /**
     * Aligns a redefined port's Name AND Label (DisplayName) onto its mother port.
     * Guarded; never throws. The Label is synchronised too, otherwise the port
     * would show the old label in the browser/diagrams even after the rename.
     */
    public static void applyRenameToMother(IRPSysMLPort port, IRPSysMLPort motherPort) {
        if (port == null || motherPort == null) return;
        // Name
        try {
            String n = motherPort.getName();
            if (n != null && !n.isBlank()) port.setName(n);
        } catch (Exception e) {
            rhpLog.warn("[RedefinedPorts] rename (name) failed: " + e.getMessage());
        }
        // Label / DisplayName — mirrored from the mother (blank clears the override
        // so the label follows the name).
        try {
            String dn = motherPort.getDisplayName();
            port.setDisplayName(dn == null ? "" : dn);
        } catch (Exception e) {
            rhpLog.warn("[RedefinedPorts] rename (label/displayName) failed: " + e.getMessage());
        }
    }

    /** Deletes an orphan port confirmed by the engineer. Guarded; never throws. */
    public static void deleteOrphanPort(IRPSysMLPort port) {
        if (port == null) return;
        try {
            port.deleteFromProject();
        } catch (Exception e) {
            rhpLog.warn("[RedefinedPorts] delete failed on '" + safeName(port) + "': " + e.getMessage());
        }
    }

    /** Vrai si le Name OU le Label (DisplayName) diffère entre deux ports. */
    private static boolean nameOrLabelDiffers(IRPSysMLPort a, IRPSysMLPort b) {
        return !safeGetName(a).equals(safeGetName(b))
                || !safeGetDisplayName(a).equals(safeGetDisplayName(b));
    }

    private static String safeGetName(IRPModelElement e) {
        try { String n = e.getName(); return n == null ? "" : n; } catch (Exception ex) { return ""; }
    }

    private static String safeGetDisplayName(IRPModelElement e) {
        try { String d = e.getDisplayName(); return d == null ? "" : d; } catch (Exception ex) { return ""; }
    }

    /** Libellé lisible d'un port : {@code name} + {@code "label"} si le label diffère du name. */
    private static String describePort(IRPSysMLPort p) {
        String n = safeGetName(p);
        String d = safeGetDisplayName(p);
        return (!d.isEmpty() && !d.equals(n)) ? (n + " \"" + d + "\"") : n;
    }

    /** Port possédé par {@code c} dont le nom correspond à {@code name}, ou null. */
    private static IRPSysMLPort findPortByName(IRPClass c, String name) {
        if (c == null || name == null) return null;
        IRPCollection ports = safePorts(c);
        if (ports == null) return null;
        for (Object o : ports.toList()) {
            if (o instanceof IRPSysMLPort && name.equals(safeName((IRPSysMLPort) o))) {
                return (IRPSysMLPort) o;
            }
        }
        return null;
    }

    private static String safeGuid(IRPModelElement el) {
        try { return el != null ? el.getGUID() : null; } catch (Exception e) { return null; }
    }

    private static IRPSysMLPort findOrCreateRedefinedPort(
            IRPClass reference,
            IRPSysMLPort originalPort) {

        if (reference == null || originalPort == null) {
            return null;
        }

        // 1) Appariement ROBUSTE : le port POSSÉDÉ par la référence qui redéfinit
        //    ce port mère (via getRedefines). Indépendant du nom -> évite de
        //    tomber sur un port hérité (lecture seule) ou renommé.
        IRPSysMLPort owned = findOwnedRedefinition(reference, originalPort);
        if (owned != null) {
            return owned;
        }

        String portName = safeName(originalPort);

        // 2) Repli : appariement par NOM (port homonyme legacy/manuel sans lien de
        //    redéfinition). Basé sur getPorts() — cohérent avec pruneNonMirrorPorts
        //    et computePlan, pour réutiliser exactement le port conservé (donc
        //    préserver les flows qui y sont rattachés) au lieu d'en créer un nouveau.
        IRPSysMLPort byName = findPortByName(reference, portName);
        if (byName != null) {
            return byName;
        }

        // 3) Sinon, création via addNewAggr.
        try {
            IRPSysMLPort createdPort =
                    (IRPSysMLPort) reference.addNewAggr("Flow Port", portName);

            ensureRedefinedPortProperties(originalPort, createdPort);

            return createdPort;

        } catch (Exception e) {
            logInfo("PORT Unable to create redefined port " + portName
                    + " in " + safeName(reference) + e.getMessage());
            return null;
        }
    }

    /** Port possédé par {@code reference} qui redéfinit {@code motherPort}, ou null. */
    private static IRPSysMLPort findOwnedRedefinition(IRPClass reference, IRPSysMLPort motherPort) {
        IRPCollection childPorts;
        try {
            childPorts = reference.getPorts();
        } catch (Exception e) {
            return null;
        }
        if (childPorts == null) return null;

        for (Object o : childPorts.toList()) {
            if (!(o instanceof IRPSysMLPort)) continue;
            IRPSysMLPort childPort = (IRPSysMLPort) o;

            IRPCollection redefs;
            try {
                redefs = childPort.getRedefines();
            } catch (Exception e) {
                continue;
            }
            if (redefs == null) continue;

            for (Object r : redefs.toList()) {
                if (r instanceof IRPModelElement && sameElement((IRPModelElement) r, motherPort)) {
                    return childPort;
                }
            }
        }
        return null;
    }

    private static void ensureRedefinedPortProperties(
            IRPSysMLPort originalPort,
            IRPSysMLPort redefinedPort) {

        if (originalPort == null || redefinedPort == null) {
            return;
        }

        // Diagnostic : un port en lecture seule voit ses setters ignorés en
        // silence par Rhapsody. On le signale (sans bloquer : on tente quand même).
        int ro = -1;
        try { ro = redefinedPort.isReadOnly(); } catch (Exception ignore) {}
        if (ro != 0 && ro != -1) {
            logInfo("PORT: target '" + safeName(redefinedPort) + "' isReadOnly=" + ro
                    + " -> Rhapsody may ignore property updates");
        }

        // Mise à jour des propriétés depuis le port mère — UNIQUEMENT si elles
        // ont changé (vraie mise à jour, pas de churn inutile sur le modèle).
        List<String> updated = new ArrayList<String>();

        // Type
        try {
            IRPClassifier srcType = originalPort.getType();
            IRPClassifier tgtType = redefinedPort.getType();
            if (srcType != null && !sameElement(srcType, tgtType)) {
                redefinedPort.setType(srcType);
                updated.add("type[" + safeName(tgtType) + "->" + safeName(srcType) + "]");
            }
        } catch (Exception e) {
            logInfo("PORT : type KO sur " + safeName(redefinedPort) + " : " + e.getMessage());
        }

        // Direction (à l'identique : c'est une redéfinition, pas une connexion)
        try {
            String srcDir = originalPort.getPortDirection();
            String tgtDir = redefinedPort.getPortDirection();
            if (srcDir != null && !srcDir.equals(tgtDir)) {
                redefinedPort.setPortDirection(srcDir);
                updated.add("direction[" + tgtDir + "->" + srcDir + "]");
            }
        } catch (Exception e) {
            logInfo("PORT : direction KO sur " + safeName(redefinedPort) + " : " + e.getMessage());
        }

        // Description
        try {
            String srcDesc = originalPort.getDescription();
            String tgtDesc = redefinedPort.getDescription();
            if (srcDesc != null && !srcDesc.equals(tgtDesc)) {
                redefinedPort.setDescription(srcDesc);
                updated.add("description");
            }
        } catch (Exception e) {
            logInfo("PORT : description KO sur " + safeName(redefinedPort) + " : " + e.getMessage());
        }

        // Lien de redéfinition (idempotent).
        try {
            redefinedPort.addRedefines(originalPort);
        } catch (Exception e) {
            // addRedefines déjà appliqué ou non applicable : silencieux.
        }

        // Stéréotypes (synchro exacte) — alimente aussi le journal des changements.
        copyPortStereotypes(originalPort, redefinedPort, updated);

        if (!updated.isEmpty()) {
            logInfo("PORT: updated '" + safeName(redefinedPort) + "' " + updated
                    + " from '" + safeName(originalPort) + "'");
        } else {
            rhpLog.debug("PORT: no property change for '" + safeName(redefinedPort) + "'");
        }
    }

    /**
     * Synchronise EXACTEMENT les stéréotypes non-New-Term du port redéfini sur
     * ceux du port mère : ajoute les manquants ET retire les surnuméraires (ceux
     * que la mère n'a plus). Le stéréotype New Term (« Flow Port ») n'est jamais
     * touché.
     */
    private static void copyPortStereotypes(IRPSysMLPort originalPort, IRPSysMLPort redefinedPort,
            List<String> updated) {
        try {
            @SuppressWarnings("unchecked")
            List<IRPStereotype> src = originalPort.getStereotypes().toList();
            @SuppressWarnings("unchecked")
            List<IRPStereotype> tgt = redefinedPort.getStereotypes().toList();

            // Ajoute les stéréotypes (non New Term) de la mère absents de la fille.
            for (IRPStereotype st : src) {
                if (st == null || isNewTermStereotype(st)) continue;
                if (!containsStereotype(tgt, st)) {
                    try {
                        redefinedPort.addSpecificStereotype(st);
                        updated.add("stereo+" + safeName(st));
                    } catch (Exception e) {
                        logInfo("PORT: Cannot apply stereotype '" + safeName(st) + "' on port "
                                + safeName(redefinedPort) + " : " + e.getMessage());
                    }
                }
            }

            // Retire les stéréotypes (non New Term) de la fille absents de la mère.
            for (IRPStereotype st : new ArrayList<IRPStereotype>(tgt)) {
                if (st == null || isNewTermStereotype(st)) continue;
                if (!containsStereotype(src, st)) {
                    try {
                        redefinedPort.removeStereotype(st);
                        updated.add("stereo-" + safeName(st));
                    } catch (Exception e) {
                        logInfo("PORT: Cannot remove stereotype '" + safeName(st) + "' from port "
                                + safeName(redefinedPort) + " : " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logInfo("PORT : Erreur copie stereotypes port sur " + safeName(redefinedPort) + e.getMessage());
        }
    }

    private static boolean isNewTermStereotype(IRPStereotype st) {
        try { return st != null && st.getIsNewTerm() == 1; } catch (Exception e) { return false; }
    }

    private static boolean containsStereotype(List<?> list, IRPStereotype st) {
        if (list == null || st == null) return false;
        for (Object o : list) {
            if (o instanceof IRPStereotype && sameElement((IRPStereotype) o, st)) return true;
        }
        return false;
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
     * @return the number of inherited port graphics removed.
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

        int removed = applyPortSwap(app, diagram, graphElement, swap, removalCollection);
        if (removed > 0) {
            logInfo("Successfully replaced " + removed + " inherited port(s) by their redefinition");
        }
        return removed;
    }

    /**
     * Variante pour une liste de représentations graphiques (ex. sélection d'un
     * élément dans l'arborescence -> {@link #getGraphicalRepresentations}).
     * Each representation is handled on its own, with its own diagram.
     */
    public static int hideRedefinedPorts(IRPApplication app, List<IRPGraphElement> representations) {
        if (representations == null || representations.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (IRPGraphElement graphElement : representations) {
            if (graphElement == null) continue;
            try {
                total += hideRedefinedPorts(app, graphElement);
            } catch (Exception e) {
                logInfo("hideRedefinedPorts: representation skipped - " + e.getMessage());
            }
        }
        return total;
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
        IRPCollection ownPorts = safePorts((IRPClass) owner);
        if (ownPorts == null) return swap;
        for (IRPGraphElement g : ports) {
            if (pairedInherited.contains(g)) continue;
            IRPModelElement drawn = safeModelObject(g);
            if (drawn == null || isSame(safeOwner(drawn), owner)) continue;   // a port of the reference itself
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
        return planTransfer(flow, edge, replaced, replacements, null, reference);
    }

    /**
     * {@link #planTransfer(IRPFlow, IRPGraphEdge, List, List, IRPModelElement)}
     * with the redefined port of each replaced graphic given explicitly
     * ({@code redefinedPorts}, same index, entries may be null): the dry run
     * passes the inherited graphics as their own replacements (nothing is drawn
     * yet) and still has to classify like the real swap, where the replacement
     * graphic shows the redefined port. Null: read from the replacements.
     */
    static FlowTransfer planTransfer(IRPFlow flow, IRPGraphEdge edge, List<IRPGraphElement> replaced,
            List<IRPGraphElement> replacements, List<IRPModelElement> redefinedPorts, IRPModelElement reference) {
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

        // The redefined port that replaces each touched inherited graphic (null when unknown).
        IRPModelElement redSrc = redefinedAt(iSrc, replaced, replacements, redefinedPorts);
        IRPModelElement redTrg = redefinedAt(iTrg, replaced, replacements, redefinedPorts);

        // Which flow end each trait end stands for (source first: a trait goes from end 1 to end 2).
        IRPModelElement end1 = flowEnd(flow, 1);
        IRPModelElement end2 = flowEnd(flow, 2);
        int sideSrc = sideOf(end1, end2, safeModelObject(ends[0]), redSrc, 1);
        int sideTrg = sideOf(end1, end2, safeModelObject(ends[1]), redTrg, 2);
        if (sideSrc != 0 && sideSrc == sideTrg) sideTrg = 3 - sideSrc;
        if ((iSrc >= 0 && sideSrc == 0) || (iTrg >= 0 && sideTrg == 0)) {
            return FlowTransfer.left(flow, "not attached to this port", ends, touched);
        }

        IRPModelElement newEnd1 = end1;
        IRPModelElement newEnd2 = end2;
        boolean mapped1 = false;
        boolean mapped2 = false;
        if (iSrc >= 0) {
            IRPModelElement target = redSrc != null ? redSrc : safeModelObject(ends[2]);
            if (sideSrc == 1) { newEnd1 = target; mapped1 = true; }
            else { newEnd2 = target; mapped2 = true; }
        }
        if (iTrg >= 0) {
            IRPModelElement target = redTrg != null ? redTrg : safeModelObject(ends[3]);
            if (sideTrg == 1) { newEnd1 = target; mapped1 = true; }
            else { newEnd2 = target; mapped2 = true; }
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
            IRPModelElement redefined = sameGraphic(g, ends[0]) ? redSrc : redTrg;
            Boolean onlyHere = drawnOnlyOn(flow, p, redefined, reference);
            if (onlyHere == null) return FlowTransfer.left(flow, "its diagrams cannot be read", ends, touched);
            if (!onlyHere.booleanValue()) return FlowTransfer.left(flow, "also drawn on another box", ends, touched);
        }
        return new FlowTransfer(flow, FlowKind.RECONNECT, null, newEnd1, newEnd2, mapped1, mapped2, ends, touched, null);
    }

    /**
     * The flow end (1 or 2) that a trait end drawn on the port {@code p} stands
     * for: the end naming {@code p}, or already naming the {@code redefined}
     * port that replaces it (null when unknown); {@code preferred} when both
     * ends name it; 0 when none.
     */
    private static int sideOf(IRPModelElement end1, IRPModelElement end2, IRPModelElement p,
            IRPModelElement redefined, int preferred) {
        boolean on1 = namesPort(end1, p, redefined);
        boolean on2 = namesPort(end2, p, redefined);
        if (on1 && on2) return preferred;
        return on1 ? 1 : (on2 ? 2 : 0);
    }

    /**
     * The redefined port replacing {@code replaced.get(i)}: the explicit one
     * ({@code redefinedPorts}, may be absent or null), else the model element of
     * the replacement graphic when it differs from the replaced one; null when
     * {@code i < 0} or unknown.
     */
    private static IRPModelElement redefinedAt(int i, List<IRPGraphElement> replaced,
            List<IRPGraphElement> replacements, List<IRPModelElement> redefinedPorts) {
        if (i < 0) return null;
        if (redefinedPorts != null && i < redefinedPorts.size() && redefinedPorts.get(i) != null) {
            return redefinedPorts.get(i);
        }
        IRPGraphElement replacement = replacements.get(i);
        return (replacement != replaced.get(i)) ? safeModelObject(replacement) : null;
    }

    /**
     * True when {@code flow} belongs to a definition: none of its ends is a port
     * owned by a reference class. Such a flow is shared by every reference of the
     * definition and is never modified.
     */
    static boolean isDefinitionFlow(IRPFlow flow) {
        return !isReferenceClass(safeOwner(flowEnd(flow, 1))) && !isReferenceClass(safeOwner(flowEnd(flow, 2)));
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
        if (box == null) box = safeOwner(end);
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
        IRPModelElement owner = safeOwner(diagram);
        return owner != null ? owner : safeOwner(definition);
    }

    /**
     * {@code base}, or {@code base_1}, {@code base_2}... : the first name no
     * element of {@code owner} has (same metaclass first, then any nested
     * element, as the listener does for a moved flow).
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
     * unreadable. The ends alone decide, as the listener's duplicate check does.
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

    /** One flow trait drawn on an inherited port of a reference, as planned (no change). */
    public static final class FlowPlan {
        public final IRPModelElement flow;
        public final String flowName;
        public final String referenceName;
        /** Name of the inherited port the trait is drawn on. */
        public final String portName;
        public final FlowKind kind;
        /** Name of the copy ("R1_p_2_X_q"), null unless {@code kind == COPY}. */
        public final String copyName;
        /** Why it is left as is; null unless {@code kind == LEFT}. */
        public final String reason;
        /** The trait itself: the same trait seen from two references is listed once. */
        public final IRPGraphEdge trait;

        FlowPlan(IRPModelElement flow, String referenceName, String portName, FlowTransfer t, IRPGraphEdge trait) {
            this.flow = flow;
            this.flowName = safeName(flow);
            this.referenceName = referenceName;
            this.portName = portName;
            this.kind = t.kind;
            this.copyName = t.copyName;
            this.reason = t.reason;
            this.trait = trait;
        }

        /** "f1 -> copy R1_p_2_X_q on p_2 (definition flow kept)" / "f1 (reason)" / "f1". */
        public String describe() {
            if (kind == FlowKind.COPY) {
                return flowName + " -> copy " + (copyName != null ? copyName + " " : "") + "on " + portName
                        + " (definition flow kept)";
            }
            if (kind == FlowKind.LEFT) return flowName + " (" + reason + ")";
            return flowName;
        }

        /** True when both plans describe the same trait (same drawn line). */
        public boolean sameTrait(FlowPlan other) {
            return other != null && trait != null && sameGraphic(trait, other.trait);
        }
    }

    /**
     * Dry run, no change: the flow traits drawn on the inherited ports of the
     * representation {@code refGraphElement}, each with what the port swap will
     * do (copy, reconnect, or leave as is and why). Nothing is drawn yet, so
     * the mapped end graphics are the inherited ones themselves; the redefined
     * port the reference already owns for each of them (when any: Clean
     * redefines after confirmation) is passed along, so a reference flow whose
     * end already names it is classified like the real swap does. A flow is
     * listed once per representation.
     */
    public static List<FlowPlan> planFlowReconnections(IRPGraphElement refGraphElement) {
        List<FlowPlan> out = new ArrayList<FlowPlan>();
        IRPModelElement reference = safeModelObject(refGraphElement);
        if (reference == null) return out;
        IRPDiagram diagram;
        try { diagram = refGraphElement.getDiagram(); } catch (Exception e) { return out; }
        if (diagram == null) return out;
        List<IRPGraphElement> inherited = inheritedGraphicalPorts(refGraphElement);
        List<IRPModelElement> redefinedPorts = new ArrayList<IRPModelElement>();
        for (IRPGraphElement g : inherited) {
            IRPModelElement port = safeModelObject(g);
            redefinedPorts.add((reference instanceof IRPClass && port instanceof IRPSysMLPort)
                    ? findOwnedRedefinition((IRPClass) reference, (IRPSysMLPort) port) : null);
        }
        List<IRPModelElement> seen = new ArrayList<IRPModelElement>();
        for (IRPGraphEdge edge : edgesTouchingAny(diagram, inherited)) {
            IRPModelElement m = safeModelObject(edge);
            if (!(m instanceof IRPFlow) || containsElement(seen, m)) continue;
            seen.add(m);
            FlowTransfer t = planTransfer((IRPFlow) m, edge, inherited, inherited, redefinedPorts, reference);
            String portName = t.inheritedGraphics.isEmpty() ? "?" : safeName(safeModelObject(t.inheritedGraphics.get(0)));
            out.add(new FlowPlan(m, safeName(reference), portName, t, edge));
        }
        return out;
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

    private static List<IRPGraphElement> getRedefinedPortsToRemove(List<IRPGraphElement> ports) {
        List<IRPGraphElement> portsToRemove = new ArrayList<IRPGraphElement>();

        if (ports == null) {
            logInfo("Error: ports list is null");
            return portsToRemove;
        }

        Set<IRPModelElement> redefinedModelElements = new HashSet<IRPModelElement>();

        // NB : pas de log ici — ce scan se relance à chaque sélection d'une
        // référence (idempotent) ; seul le retrait effectif est loggé.

        // 1) Collecte des éléments qui sont la cible d'un redefines (= ports mère).
        for (IRPGraphElement graphicalPort : ports) {
            if (graphicalPort == null) continue;

            IRPModelElement modelPort = graphicalPort.getModelObject();
            if (modelPort == null) continue;

            IRPCollection redefines = modelPort.getRedefines();
            if (redefines == null) continue;

            for (Object object : redefines.toList()) {
                if (!(object instanceof IRPModelElement)) continue;
                redefinedModelElements.add((IRPModelElement) object);
            }
        }

        // 2) Marque pour retrait les ports graphiques dont le modèle est redéfini (ports mère).
        for (IRPGraphElement graphicalPort : ports) {
            if (graphicalPort == null) continue;

            IRPModelElement modelPort = graphicalPort.getModelObject();
            if (modelPort == null) continue;

            if (!redefinedModelElements.contains(modelPort)) continue;

            portsToRemove.add(graphicalPort);
        }

        return portsToRemove;
    }

    // ------------------------------------------------------------------
    // Nettoyage de diagramme (outil "Clean Redefined Ports in Diagram")
    // ------------------------------------------------------------------

    /**
     * Ports graphiques HÉRITÉS (redéfinis) à masquer pour la représentation
     * {@code refGraphElement} d'une référence dans un diagramme. Purement
     * graphique (même logique que le drag & drop).
     */
    public static List<IRPGraphElement> portsToHide(IRPGraphElement refGraphElement) {
        return getRedefinedPortsToRemove(getPorts(refGraphElement));
    }

    /**
     * Ports graphiques HÉRITÉS affichés sur la représentation {@code refGraphElement}
     * d'une référence : ceux dont le port modèle appartient à la classe MÈRE (donc
     * hérités), qu'ils soient déjà redéfinis ou non. Robuste même quand la référence
     * n'a pas encore de ports redéfinis (contrairement à {@link #portsToHide}).
     */
    public static List<IRPGraphElement> inheritedGraphicalPorts(IRPGraphElement refGraphElement) {
        List<IRPGraphElement> result = new ArrayList<IRPGraphElement>();
        if (refGraphElement == null) return result;

        IRPModelElement refModel;
        try { refModel = refGraphElement.getModelObject(); } catch (Exception e) { return result; }
        if (!(refModel instanceof IRPClass)) return result;

        IRPModelElement mother = resolveMotherClassifier((IRPClass) refModel);
        if (!(mother instanceof IRPClass)) return result;

        // Inherited = owned by the mother or by one of its ancestors.
        List<IRPClass> chain = motherChain(mother);
        for (IRPGraphElement gp : getPorts(refGraphElement)) {
            if (gp == null) continue;
            IRPModelElement portModel;
            try { portModel = gp.getModelObject(); } catch (Exception e) { continue; }
            if (portModel == null) continue;
            if (ownedInChain(portModel, chain)) {
                result.add(gp);
            }
        }
        return result;
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
