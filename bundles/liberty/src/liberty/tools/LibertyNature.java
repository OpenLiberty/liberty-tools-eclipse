package liberty.tools;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.runtime.CoreException;

/**
 * Represents a Liberty nature or type.
 */
public class LibertyNature implements IProjectNature {
    // NATURE_ID must follow the following convention: <bundle-symbolic-name> . <nature-id-as-per-plugin.xml>
    public static final String NATURE_ID = "liberty.libertyNature";
    private IProject project;

    @Override
    public void configure() throws CoreException {
    }

    @Override
    public void deconfigure() throws CoreException {
    }

    @Override
    public IProject getProject() {
        return project;
    }

    @Override
    public void setProject(IProject project) {
        this.project = project;
    }
}