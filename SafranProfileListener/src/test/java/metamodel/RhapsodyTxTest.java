package metamodel;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.RhapsodyRuntimeException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests unitaires purs (sans instance Rhapsody vivante) pour {@link RhapsodyTx}.
 *
 * <p>{@code RhapsodyTx.mutate} encadre toutes les mutations faites par le
 * listener (redéfinition des ports, retrait graphique). Son contrat : la
 * mutation est toujours tentée, la transaction undo est refermée si elle a été
 * ouverte, et seule une {@link RhapsodyRuntimeException} est convertie en
 * {@code false} (re-tentable par l'appelant).</p>
 */
class RhapsodyTxTest {

    /** Cas nominal : start, mutation, end, dans cet ordre, et retour true. */
    @Test
    void mutate_runsMutationInsideUndoTransaction() {
        IRPApplication app = mock(IRPApplication.class);
        Runnable body = mock(Runnable.class);

        boolean ok = RhapsodyTx.mutate(app, "test", body::run);

        assertTrue(ok);
        InOrder order = inOrder(app, body);
        order.verify(app).startUndoTransaction();
        order.verify(body).run();
        order.verify(app).endUndoTransaction();
    }

    /** Une erreur Rhapsody pendant la mutation donne false, et la transaction est quand même refermée. */
    @Test
    void mutate_returnsFalseAndClosesTransaction_whenMutationThrowsRhapsodyError() {
        IRPApplication app = mock(IRPApplication.class);

        boolean ok = RhapsodyTx.mutate(app, "test", () -> {
            throw new RhapsodyRuntimeException("locked");
        });

        assertFalse(ok);
        verify(app).startUndoTransaction();
        verify(app).endUndoTransaction();
    }

    /**
     * Si la transaction ne peut pas être ouverte, la mutation est tentée quand
     * même, et on ne ferme pas une transaction qui n'existe pas.
     */
    @Test
    void mutate_stillRunsMutation_whenStartTransactionFails_andDoesNotEndIt() {
        IRPApplication app = mock(IRPApplication.class);
        doThrow(new RhapsodyRuntimeException("no tx")).when(app).startUndoTransaction();
        int[] runs = { 0 };

        boolean ok = RhapsodyTx.mutate(app, "test", () -> runs[0]++);

        assertTrue(ok);
        assertEquals(1, runs[0]);
        verify(app, never()).endUndoTransaction();
    }

    /** Un échec à la fermeture de la transaction n'annule pas le succès de la mutation. */
    @Test
    void mutate_returnsTrue_whenEndTransactionFails() {
        IRPApplication app = mock(IRPApplication.class);
        doThrow(new RhapsodyRuntimeException("end failed")).when(app).endUndoTransaction();

        assertTrue(RhapsodyTx.mutate(app, "test", () -> { }));
    }

    /**
     * Seules les erreurs Rhapsody sont converties en false : une autre
     * exception remonte à l'appelant, mais la transaction est refermée.
     */
    @Test
    void mutate_propagatesOtherExceptions_butClosesTransaction() {
        IRPApplication app = mock(IRPApplication.class);

        assertThrows(IllegalStateException.class, () -> RhapsodyTx.mutate(app, "test", () -> {
            throw new IllegalStateException("bug");
        }));
        verify(app).endUndoTransaction();
    }

    /** Sans application, la mutation est exécutée directement, sans transaction. */
    @Test
    void mutate_runsMutationDirectly_whenAppIsNull() {
        int[] runs = { 0 };

        assertTrue(RhapsodyTx.mutate(null, "test", () -> runs[0]++));
        assertEquals(1, runs[0]);
    }

    /** Sans application, une erreur Rhapsody donne aussi false. */
    @Test
    void mutate_returnsFalse_whenAppIsNullAndMutationThrowsRhapsodyError() {
        assertFalse(RhapsodyTx.mutate(null, "test", () -> {
            throw new RhapsodyRuntimeException("locked");
        }));
    }
}
