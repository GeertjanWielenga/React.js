package org.netbeans.modules.react.react.file;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.netbeans.modules.react.react.onsave.JSXUtils;
import org.openide.loaders.DataObject;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle.Messages;

@ActionID(
        category = "Edit",
        id = "org.netbeans.modules.react.react.file.CompileJSXAction"
)
@ActionRegistration(
        displayName = "#CTL_CompileJSXAction"
)
@ActionReference(path = "Loaders/text/x-jsx/Actions", position = 150)
@Messages("CTL_CompileJSXAction=Compile JSX")
public final class CompileJSXAction implements ActionListener {

    private final DataObject context;

    public CompileJSXAction(DataObject context) {
        this.context = context;
    }

    @Override
    public void actionPerformed(ActionEvent ev) {
        FileObject folder = context.getPrimaryFile().getParent();
        JSXUtils.command(folder);
    }
    
}
