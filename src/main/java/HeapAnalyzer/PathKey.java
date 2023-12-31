package HeapAnalyzer;

import org.netbeans.lib.profiler.heap.JavaClass;

import java.util.Arrays;


public class PathKey {

    public JavaClass[] path;

    public PathKey(JavaClass[] path) {

        this.path = path;
    }

    public PathKey(ClassRecord cr) {
        assert cr.jClass != null : "Can't create PathRecord from ClassRecord with jClass = null!";
        this.path = new JavaClass[]{cr.jClass};
    }

    public PathKey(JavaClass rootClass, PathKey oldPath) {
        assert rootClass != null : "Can't create PathRecord with rootClass = null!";
        path = new JavaClass[1+oldPath.path.length];
        path[0] = rootClass;
        System.arraycopy(oldPath.path, 0, path, 1, oldPath.path.length);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PathKey that = (PathKey) o;

        return Arrays.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(path);
    }

    public String getPathStr() {
        StringBuilder result = new StringBuilder("{");
        for (JavaClass jClass : path) {
            if (jClass == null)
                System.out.println("WOW!");
            assert jClass.getName() != null : "Can;t getPathStr from class with null className";
            result.append(jClass.getName()).append(",");
        }
        result.deleteCharAt(result.length()-1);
        result.append("}");
        return result.toString();
    }

    @Override
    public String toString() {
        return getPathStr();
    }
}
