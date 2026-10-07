package test.unittest;

import java.util.Arrays;
import java.util.List;

import com.telelogic.rhapsody.core.IRPCollection;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared helper for the pure unit tests (no live Rhapsody) of the plugin
 * toolkit's port-redefinition logic. Stubs {@link IRPCollection}, the
 * list-backed facade Rhapsody returns for every COM collection
 * ({@code getGeneralizations()}, {@code getPorts()}, {@code getRedefines()},
 * {@code getStereotypes()}, ...).
 *
 * <p>Duplicated from the equivalent helper in {@code SafranProfileListener}
 * on purpose: the two Eclipse projects are independent artifacts with no
 * shared module, each carrying its own copy of the business logic.</p>
 *
 * <p>Callers must reuse the exact same mock instance for what conceptually is
 * "the same model element" across different stubs. Mockito mocks use
 * identity-based {@code equals}/{@code hashCode} by default, which is exactly
 * what the production code relies on ({@code HashSet<IRPModelElement>},
 * {@code IRPGraphElement.equals(...)}) as long as the same reference is
 * reused.</p>
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
