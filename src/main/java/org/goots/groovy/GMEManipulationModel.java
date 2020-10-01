package org.goots.groovy;

import org.jboss.gm.analyzer.alignment.TestUtils;
import org.jboss.gm.common.model.ManipulationModel;

public class GMEManipulationModel
                extends TestUtils.TestManipulationModel
{
    public GMEManipulationModel( ManipulationModel m )
    {
        super(m);
        logger = new GradleLogger();
    }
}
