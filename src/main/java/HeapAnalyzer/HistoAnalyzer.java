package HeapAnalyzer;

import org.gridkit.jvmtool.heapdump.InstanceCallback;
import org.gridkit.jvmtool.heapdump.RefSet;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaClass;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class HistoAnalyzer implements InstanceCallback {

    public static final Comparator<ClassRecord> BY_SIZE_DESC = new Comparator<ClassRecord>() {

        public int compare(ClassRecord o1, ClassRecord o2) {
            long result = o1.totalSize - o2.totalSize;
            return result == 0 ? 0 : result > 0 ? -1 : 1;
        }

        public String toString() {
            return "BY_SIZE";
        }
    };

    public HistoAnalyzer(boolean instanceTracking) {
        if (instanceTracking) {
            known = new RefSet();
        }
    }

    private final Map<JavaClass, ClassRecord> classes = new HashMap<>();
    private RefSet known = null;

    public void feed(Instance i) {
        if (known != null) {
            if (known.getAndSet(i.getInstanceId(), true)) {
                // already accumulated
                return;
            }
        }
        JavaClass jClass = i.getJavaClass();
        ClassRecord cr = classes.get(jClass);
        if (cr == null)
            classes.put(jClass, cr = new ClassRecord(jClass));
        cr.addInstance(i);
    }

    public Collection<ClassRecord> getHisto() {
        return classes.values();
    }

}
