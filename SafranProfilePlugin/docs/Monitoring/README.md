# Package Monitoring : page Home, page Analysis Tables et boutons de la barre d'outils

Version du profil : [v2026-11-24](../../../SafranArchitectureProfile/v2026-11-24.txt).

Le package `Monitoring` du profil SafranArchitectureProfile contient la page **Home**, la page **Analysis Tables** et les tables (layouts et vues) qu'elles ouvrent. Ce document décrit :

- les deux boutons de la barre d'outils qui ouvrent ces pages ;
- les deux programmes qui rangent le package et mettent les tables à jour.

---

## 1. Boutons de la barre d'outils

Trois boutons du plugin Safran ouvrent une page, quel que soit le diagramme affiché :

| Bouton | Icône | Action | Entrée du `.hep` |
|---|---|---|---|
| Return home | ![Home](../../../SafranArchitectureProfile/Icons/home16.png) | ouvre la page Home | `name50` |
| Open Analysis Tables | ![Analysis Tables](../../../SafranArchitectureProfile/Icons/AnalysisTables16.png) | ouvre la page Monitoring::Analysis Tables | `name55` |
| Open Tool Documentation | ![Tool Documentation](../../../SafranArchitectureProfile/Icons/ToolDocumentation16.png) | ouvre dans le navigateur l'adresse de la vignette « Tool Documentation » de la page Home | `name56` |

- La page Analysis Tables est retrouvée par son GUID au moment du clic.
- L'adresse de la documentation est lue dans l'hyperlien de la vignette « Tool Documentation » : si l'adresse de la vignette change, le bouton suit.
- Si la page ou l'hyperlien manque, le bouton écrit un message dans le log Rhapsody et ne fait rien d'autre.

Classes : `tools.OpenAnalysisTables`, `tools.OpenToolDocumentation` (enregistrées dans `main.SafranProfilePlugin`). Tests : `test.unittest.ToolbarButtonsTest`.

Les boutons apparaissent après reconstruction de `safran_app.jar` et redémarrage de Rhapsody.

---

## 2. Programmes de mise à jour du profil

Les deux programmes sont dans `SafranProfilePlugin/src/test/java/test/manual`. Ils travaillent par l'API Rhapsody et non en éditant le fichier `.sbsx` : chaque élément créé reçoit ainsi son identifiant du serveur RMM.

**Utilisation** (dans cet ordre) :

1. ouvrir le profil SafranArchitectureProfile dans Rhapsody, connecté au serveur ;
2. dans Eclipse, lancer `OrganizeMonitoring` avec **Run As > Java Application**, d'abord avec l'argument `DRY` (la console liste ce qui serait fait, sans rien modifier), puis sans argument ;
3. faire de même avec `UpdateAnalysisTables` ;
4. enregistrer le profil.

Chaque programme :

- fait toutes ses modifications dans une seule transaction : un Ctrl+Z les annule ;
- peut être relancé sans risque : ce qui est déjà fait est sauté ;
- écrit `ATTENTION` dans la console pour tout élément qu'il laisse tel quel (élément introuvable, colonne inattendue).

### 2.1 OrganizeMonitoring : rangement et page Home

Rangement des tables posées à la racine de `Monitoring` :

| Dossier | Contenu |
|---|---|
| `OK/Diagram Indexes/A - Operational` à `D - Technical` | une table par type de diagramme du profil (22 types) |
| `OK/Analysis Tables Page` | les tables de la page Analysis Tables |
| `OK/Other Tables` | autres tables qui fonctionnent, sans vignette |
| `NOK` | tables sans layout ou fondées sur des termes de l'ancien profil, ancienne copie de la page Home |
| `TBD` | layout sans vue « Safran Architecture Model » |

Ce qui était déjà rangé à la main dans `OK`, `NOK` et `TBD` n'est pas déplacé.

Une table « fonctionne » quand, vérifié sans Rhapsody :

- son layout existe ;
- ses types d'éléments ou son motif de contexte n'utilisent que des termes du profil actuel ;
- tous ses getters Java compilent.

Le contenu réel des tables reste à contrôler dans Rhapsody.

Page Home :

- 9 tables d'index créées, avec leur vignette : Environment, Operational Flowchart, System Functional Behavior, Functional Chain, Functional Sequence, Logical Interfaces, Flow Composition, Technical Interfaces, Technical Flows ;
- une ligne de vignettes centrée par bande, même taille dans une bande ; la colonne de droite n'est pas touchée ;
- les 3 images qui pointaient vers un chemin sur un poste pointent vers `Icons\Home`.

Plan et calcul de placement : `MonitoringPlan`. Tests : `test.unittest.MonitoringPlanTest`.

### 2.2 UpdateAnalysisTables : tables de la page Analysis Tables

Les 12 tables de la page suivent la table de référence **Operational ICD** du profil SafranML v2 :

- noms avec le suffixe `_SafranMLv2_Layout` et `_SafranMLv2_View` ; le texte des vignettes ne change pas ;
- **Logical ICD** et **Technical ICD** : copies du layout Operational ICD (mêmes colonnes, mêmes getters), à partir du Logical Architecture Diagram (Logical Flow) et du Technical Architecture Diagram (Technical Connection, Technical Flow) ; les anciens layouts vont dans `NOK` ;
- les getters des trois ICD lisent les ports SysML (Flow Port) et les ports standards (Technical Port) ;
- termes du profil v2 dans les motifs : `OperatingMode`, `ActivatedFunction`, `Flow_Port`, `LogicalPortRealization`, fonctions des `FunctionalSystem` ;
- getters protégés : test `instanceof` avant tout cast, contrôle des valeurs nulles, sans doublon.

Plan détaillé, table par table : `AnalysisTablesPlan`. Tests : `test.unittest.AnalysisTablesPlanTest` ; ils compilent tous les getters et testent ceux des ICD avec Mockito.
