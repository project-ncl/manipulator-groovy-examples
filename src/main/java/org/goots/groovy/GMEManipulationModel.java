package org.goots.groovy;

import org.jboss.gm.analyzer.alignment.TestUtils;
import org.jboss.gm.common.model.ManipulationModel;

public class GMEManipulationModel
                extends TestUtils.TestManipulationModel
{
    public GMEManipulationModel( ManipulationModel m )
    {
        group = m.getGroup();
        name = m.getName();
        version = m.getVersion();
        alignedDependencies = m.getAlignedDependencies();
        children = m.getChildren();
        logger = new GradleLogger();
    }
}
