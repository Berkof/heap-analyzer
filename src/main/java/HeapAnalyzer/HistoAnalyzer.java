package HeapAnalyzer;

import org.gridkit.jvmtool.heapdump.HeapHistogram;
import org.gridkit.jvmtool.heapdump.InstanceCallback;
import org.gridkit.jvmtool.heapdump.RefSet;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaClass;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class HistoAnalyzer implements InstanceCallback {

    public static final Comparator<ClassRecord> BY_NAME = new Comparator<ClassRecord>() {

        public int compare(ClassRecord o1, ClassRecord o2) {
            return o1.jClass.getName().compareTo(o2.jClass.getName());
        }

        public String toString() {
            return "BY_NAME";
        }
    };

    public static final Comparator<ClassRecord> BY_SIZE = new Comparator<ClassRecord>() {

        public int compare(ClassRecord o1, ClassRecord o2) {
            long result = o1.totalSize - o2.totalSize;
            return result == 0 ? 0 : result > 0 ? 1 : -1;
        }

        public String toString() {
            return "BY_SIZE";
        }
    };

    public static final Comparator<ClassRecord> BY_SIZE_DESC = new Comparator<ClassRecord>() {

        public int compare(ClassRecord o1, ClassRecord o2) {
            long result = o1.totalSize - o2.totalSize;
            return result == 0 ? 0 : result > 0 ? -1 : 1;
        }

        public String toString() {
            return "BY_SIZE";
        }
    };

    public static final Comparator<ClassRecord> BY_COUNT = new Comparator<ClassRecord>() {

        public int compare(ClassRecord o1, ClassRecord o2) {
            return o1.instanceCount - o2.instanceCount;
        }

        public String toString() {
            return "BY_COUNT";
        }
    };

    public HistoAnalyzer(boolean instanceTracking) {
        if (instanceTracking) {
            known = new RefSet();
        }
    }

    private Map<JavaClass, ClassRecord> classes = new HashMap();
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
