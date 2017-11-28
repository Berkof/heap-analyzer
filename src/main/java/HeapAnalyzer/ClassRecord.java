package HeapAnalyzer;

import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaClass;

public class ClassRecord {
    public JavaClass jClass;
    public int instanceCount;
    public long totalSize;

    public ClassRecord(JavaClass jClass) {
        this.jClass = jClass;
    }

    public void addInstance(Instance i) {
        ++instanceCount;
        totalSize += i.getSize();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ClassRecord that = (ClassRecord) o;

        if (instanceCount != that.instanceCount) return false;
        if (totalSize != that.totalSize) return false;
        return jClass != null ? jClass.equals(that.jClass) : that.jClass == null;
    }

    @Override
    public int hashCode() {
        int result = jClass != null ? jClass.hashCode() : 0;
        result = 31 * result + instanceCount;
        result = 31 * result + (int) (totalSize ^ (totalSize >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "ClassRecord{" +
            "jClass=" + jClass.getName() +
            ", instanceCount=" + instanceCount +
            ", totalSize=" + totalSize +
            '}';
    }
}
