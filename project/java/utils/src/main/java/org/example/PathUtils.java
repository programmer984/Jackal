package org.example;

/**
 * Since we want it works on old Android devices
 * we can not use Paths or event Path classes
 * Everywhere use forward slash '/' since modern Windows understands this
 */
public class PathUtils {

    private static final String separator = "/";

    /**
     * remove extra slash on the end
     * /home/user/Desktop/
     *
     * @param path
     * @return
     */
    private static String normalize(String path) {
        return path.replaceAll("/$", "");
    }

    public static String resolve(String basePath, String another) {
        return normalize(basePath) + separator + another;
    }

}
