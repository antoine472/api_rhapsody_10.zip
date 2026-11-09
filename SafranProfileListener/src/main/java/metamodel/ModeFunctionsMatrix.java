package metamodel;

import java.awt.Dialog;

import javax.swing.JDialog;
import javax.swing.JOptionPane;

import com.telelogic.rhapsody.core.IRPMatrixView;
import com.telelogic.rhapsody.core.IRPModelElement;

public class ModeFunctionsMatrix extends ModelElement {

    private IRPMatrixView rhpMatrixView;

    public ModeFunctionsMatrix(IRPModelElement modelElement) {
        super(modelElement);
        rhpMatrixView = (IRPMatrixView) modelElement;
        rhpLog.debug("Created ModeFunctionsMatrix");
    }

    @Override
    public void afterAdded() {
        super.afterAdded();
        rhpLog.debug("After add element ModeFunctionsMatrix");

        showAlwaysOnTopMessage();
    }

	private void showAlwaysOnTopMessage() {
        String msg = "Update the scope of the Matrix using the Feature properties\n"
                   + "and select the system definition domain package.";

        // Create an always-on-top modal dialog
        final JDialog dialog = new JDialog();
        dialog.setAlwaysOnTop(true);     // stays above Rhapsody
        dialog.setModalityType(Dialog.DEFAULT_MODALITY_TYPE);

        JOptionPane.showMessageDialog(
                dialog,
                msg,
                "Mode Functions Matrix",
                JOptionPane.INFORMATION_MESSAGE
        );

        dialog.dispose();
    }
}
