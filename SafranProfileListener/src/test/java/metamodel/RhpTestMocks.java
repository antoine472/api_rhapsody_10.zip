package metamodel;

import java.util.Arrays;
import java.util.List;

import com.telelogic.rhapsody.core.IRPCollection;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared helper for the pure unit tests (no live Rhapsody) of the redefinition
 * listeners. Stubs {@link IRPCollection}, the list-backed facade Rhapsody
 * returns for every COM collection ({@code getGeneralizations()},
 * {@code getPorts()}, {@code getRedefines()}, {@code getStereotypes()}, ...).
 *
 * <p>Callers must reuse the exact same mock instance for what conceptually is
 * "the same model element" across different stubs (e.g. a port returned by
 * both {@code getPorts()} and later matched via {@code getGUID()}). Mockito
 * mocks use identity-based {@code equals}/{@code hashCode} by default, which
 * is exactly what the production code relies on ({@code HashSet<IRPModelElement>},
 * {@code IRPGraphElement.equals(...)}) as long as the same reference is reused.</p>
 */
final class RhpTestMocks {

    private RhpTestMocks() {
    }

    static IRPCollection collectionOf(Object... items) {
        IRPCollection collection = mock(IRPCollection.class);
        List<Object> list = Arrays.asList(items);
        when(collection.toList()).thenReturn(list);
        when(collection.getCount()).thenReturn(list.size());
        return collection;
    }
}
