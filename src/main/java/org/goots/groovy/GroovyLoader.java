package org.goots.groovy;


import java.io.File;

class GroovyLoader {

    private static final String GL = "GroovyLoader.class";

    private GroovyLoader()  {}

    /**
     * Utility method to load any groovy scripts as source for the tests.
     * @param name - the of the script
     * @return a File reference
     */
    static File loadGroovy (String name)
    {
        String path = GroovyLoader.class.getResource(GL).getPath();
        return new File (path.replaceAll("(.*)(target/classes)(.*)(GroovyLoader.class)", "$1src/main/groovy$3" + name));
    }
}
