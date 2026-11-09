package metamodel;

import org.junit.jupiter.api.Test;

import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPFlow;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPStereotype;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires purs (sans instance Rhapsody vivante) pour {@link SafranFactory}.
 *
 * <p>La fabrique choisit la classe de traitement {@code metamodel.<Nom>} d'après
 * le New Term de l'élément créé, sinon d'après sa métaclasse, et retombe sur
 * {@link ModelElement} (sans effet) si aucune classe ne correspond.</p>
 */
class SafranFactoryTest {

    private static IRPModelElement elementWith(Class<? extends IRPModelElement> type,
                                               String newTerm, String metaClass) {
        IRPModelElement el = mock(type);
        when(el.getGUID()).thenReturn("GUID-" + newTerm + "-" + metaClass);
        if (newTerm != null) {
            IRPStereotype st = mock(IRPStereotype.class);
            when(st.getName()).thenReturn(newTerm);
            when(el.getNewTermStereotype()).thenReturn(st);
        }
        when(el.getMetaClass()).thenReturn(metaClass);
        return el;
    }

    /** Le New Term désigne la classe de traitement. */
    @Test
    void create_usesTheNewTermHandler() {
        IRPModelElement el = elementWith(IRPFlow.class, "FunctionalFlow", "Flow");

        assertTrue(new SafranFactory().create(el) instanceof FunctionalFlow);
    }

    /** Sans New Term, la métaclasse sert de nom de classe. */
    @Test
    void create_fallsBackOnMetaClass_whenNoNewTerm() {
        IRPModelElement el = elementWith(IRPClass.class, null, "FunctionWithReference");

        assertEquals(FunctionWithReference.class, new SafranFactory().create(el).getClass());
    }

    /** Un New Term au nom vide compte comme absent. */
    @Test
    void create_fallsBackOnMetaClass_whenNewTermNameIsEmpty() {
        IRPModelElement el = elementWith(IRPClass.class, "", "FunctionWithReference");

        assertEquals(FunctionWithReference.class, new SafranFactory().create(el).getClass());
    }

    /**
     * Un flow standard (métaclasse "Flow", sans New Term Safran) n'a pas de
     * traitement : {@link Flow} n'a pas de constructeur {@code (IRPModelElement)},
     * seules ses sous-classes en ont un. Pas de détection de doublon dans ce cas.
     */
    @Test
    void create_returnsDefaultModelElement_forPlainFlowWithoutNewTerm() {
        IRPModelElement el = elementWith(IRPFlow.class, null, "Flow");

        assertEquals(ModelElement.class, new SafranFactory().create(el).getClass());
    }

    /** Aucun traitement dédié : ModelElement par défaut (afterAdded sans effet). */
    @Test
    void create_returnsDefaultModelElement_whenNoHandlerExists() {
        IRPModelElement el = elementWith(IRPClass.class, "SomeUnknownTerm", "Class");

        assertEquals(ModelElement.class, new SafranFactory().create(el).getClass());
    }

    /**
     * Le constructeur du traitement échoue (ici un traitement de flow sur un
     * élément qui n'est pas un flow) : ModelElement par défaut, pas d'exception.
     */
    @Test
    void create_returnsDefaultModelElement_whenHandlerConstructionFails() {
        IRPModelElement el = elementWith(IRPClass.class, "LogicalFlow", "Class");

        assertEquals(ModelElement.class, new SafranFactory().create(el).getClass());
    }
}
