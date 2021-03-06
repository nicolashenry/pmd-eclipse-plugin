/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.eclipse.ui.actions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.PlatformUI;

import net.sourceforge.pmd.eclipse.plugin.PMDPlugin;
import net.sourceforge.pmd.eclipse.runtime.writer.IAstWriter;
import net.sourceforge.pmd.eclipse.runtime.writer.WriterException;
import net.sourceforge.pmd.eclipse.ui.nls.StringKeys;
import net.sourceforge.pmd.eclipse.util.IOUtil;
import net.sourceforge.pmd.lang.ast.JavaCharStream;
import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.ast.JavaParser;
import net.sourceforge.pmd.lang.java.ast.ParseException;

/**
 * Process PMDGenerateAST action menu. Generate a AST from the selected file.
 *
 * @author Philippe Herlin
 *
 */
public class PMDGenerateASTAction extends AbstractUIAction implements IRunnableWithProgress {

    private static final Logger LOG = Logger.getLogger(PMDGenerateASTAction.class);

    private IStructuredSelection structuredSelection;

    /**
     * @see org.eclipse.ui.IActionDelegate#run(IAction)
     */
    public void run(IAction action) {
        LOG.info("Generation AST action requested");

        // If action is selected from a view, process the selection
        if (isViewPart()) {
            ISelection sel = targetSelection();
            if (sel instanceof IStructuredSelection) {
                this.structuredSelection = (IStructuredSelection) sel;
                ProgressMonitorDialog dialog = new ProgressMonitorDialog(
                        PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell());
                try {
                    dialog.run(false, false, this);
                } catch (InvocationTargetException e) {
                    showErrorById(StringKeys.ERROR_INVOCATIONTARGET_EXCEPTION, e);
                } catch (InterruptedException e) {
                    showErrorById(StringKeys.ERROR_INTERRUPTED_EXCEPTION, e);
                }
            }
        }

        // If action is selected from an editor, process the file currently
        // edited
        if (isEditorPart()) {
            IEditorInput editorInput = ((IEditorPart) targetPart()).getEditorInput();
            if (editorInput instanceof IFileEditorInput) {
                generateAST(((IFileEditorInput) editorInput).getFile());
            } else {
                LOG.debug("The kind of editor input is not supported. The editor input if of type: "
                        + editorInput.getClass().getName());
            }
        } else {
            // else this is not supported
            LOG.debug("This action is not supported on this kind of part. This part type is: " + targetPartClassName());
        }
    }

    /**
     * @see org.eclipse.ui.IActionDelegate#selectionChanged(IAction, ISelection)
     */
    public void selectionChanged(IAction action, ISelection selection) {
    }

    /**
     * Generate a AST for a file
     * 
     * @param file
     *            a file
     */
    private void generateAST(IFile file) {
        LOG.info("Generating AST for file " + file.getName());
        ByteArrayOutputStream byteArrayOutputStream = null;
        ByteArrayInputStream astInputStream = null;
        try {
            JavaParser parser = new JavaParser(new JavaCharStream(file.getContents()));
            parser.setJdkVersion(Integer.MAX_VALUE);
            ASTCompilationUnit compilationUnit = parser.CompilationUnit();
            byteArrayOutputStream = new ByteArrayOutputStream();
            IAstWriter astWriter = PMDPlugin.getDefault().getAstWriter();
            astWriter.write(byteArrayOutputStream, compilationUnit);
            byteArrayOutputStream.flush();

            IFile astFile = createASTFile(file);

            if (astFile != null) {
                if (astFile.exists()) {
                    astFile.delete(false, null);
                }
                astInputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
                astFile.create(astInputStream, false, null);
            }

        } catch (CoreException e) {
            showErrorById(StringKeys.ERROR_CORE_EXCEPTION, e);
        } catch (ParseException e) {
            showErrorById(StringKeys.ERROR_PMD_EXCEPTION, e);
        } catch (WriterException e) {
            showErrorById(StringKeys.ERROR_PMD_EXCEPTION, e);
        } catch (IOException e) {
            showErrorById(StringKeys.ERROR_IO_EXCEPTION, e);
        } finally {
            IOUtil.closeQuietly(byteArrayOutputStream);
            IOUtil.closeQuietly(astInputStream);
        }
    }

    private static IFile createASTFile(IFile file) {

        String astName = astNameFor(file);

        IFile astFile = null;
        IContainer parent = file.getParent();
        if (parent instanceof IFolder) {
            astFile = ((IFolder) parent).getFile(astName);
        } else if (parent instanceof IProject) {
            astFile = ((IProject) parent).getFile(astName);
        }
        return astFile;
    }

    private static String astNameFor(IFile file) {

        String name = file.getName();
        int dotPosition = name.indexOf('.');
        String astName = name.substring(0, dotPosition) + ".ast";
        return astName;
    }

    /**
     * @see org.eclipse.jface.operation.IRunnableWithProgress#run(org.eclipse.core.runtime.IProgressMonitor)
     */
    public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
        monitor.beginTask("", structuredSelection.size());
        for (Iterator<?> i = structuredSelection.iterator(); i.hasNext();) {
            Object element = i.next();
            if (element instanceof IAdaptable) {
                IAdaptable adaptable = (IAdaptable) element;
                IResource resource = (IResource) adaptable.getAdapter(IResource.class);
                if (resource != null) {
                    monitor.subTask(resource.getName());
                    generateAST((IFile) resource);
                    monitor.worked(1);
                } else {
                    LOG.warn("The selected object cannot adapt to a resource");
                    LOG.debug("   -> selected object : " + element);
                }
            } else {
                LOG.warn("The selected object is not adaptable");
                LOG.debug("   -> selected object : " + element);
            }
        }
    }

}
