package io.openliberty.tools.eclipse.ui;

import java.net.MalformedURLException;
import java.net.URL;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.Project;
import io.openliberty.tools.eclipse.logging.Trace;

/**
 * Table label provider for entries in the table containing the dashboard content.
 */
public class DashboardEntryLabelProvider extends LabelProvider implements ITableLabelProvider {

    /**
     * Image representing a Maven project.
     */
    private Image mavenImg;

    /**
     * Image representing a Gradle project.
     */
    private Image gradleImg;

    /**
     * DevModeOperations reference.
     */
    private DevModeOperations devModeOps;

    /**
     * Constructor.
     * 
     * @param devModeOps DevModeOperations instance.
     */
    public DashboardEntryLabelProvider(DevModeOperations devModeOps) {
        this.devModeOps = devModeOps;

        try {
            ImageDescriptor mavenImgDesc = ImageDescriptor.createFromURL(new URL("platform:/plugin/org.eclipse.m2e.core.ui/icons/m2.png"));
            mavenImg = mavenImgDesc.createImage();
            ImageDescriptor buildshipImageDesc = ImageDescriptor
                    .createFromURL(new URL("platform:/plugin/org.eclipse.buildship.ui/icons/full/ovr16/gradle_logo@2x.png"));
            gradleImg = buildshipImageDesc.createImage();
        } catch (MalformedURLException e) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, "Unable to create URL for specified image path." + e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Image getColumnImage(Object element, int columnIndex) {
        // Currently, the table under which the dashboard content is organized consists of a single
        // column and a single row. The content is the string containing the name of the project.
        String projectName = null;
        Image img = null;
        if (element != null && element instanceof String) {
            projectName = (String) element;
            Project project = devModeOps.getDashboardProject(projectName);

            if (project != null) {
                if (project.getBuildType() == Project.BuildType.GRADLE) {
                    img = gradleImg;
                } else {
                    img = mavenImg;
                }
            }
        }

        return img;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getColumnText(Object element, int columnIndex) {
        // Currently the table consists of a single column and a single row, and the
        // content is a string containing the name of the project.
        // Therefore, the columnIndex is not used.
        String columnText = null;
        if (element != null && element instanceof String) {
            columnText = element.toString();
        }

        return columnText;
    }
}
