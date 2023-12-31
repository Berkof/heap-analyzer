package HeapAnalyzer;

import org.netbeans.lib.profiler.heap.JavaClass;

import java.util.Comparator;

public class ResultRow extends PathKey {

    public long size;
    public int count;

    public static final Comparator<ResultRow> BY_SIZE_DESC = new Comparator<ResultRow>() {

        public int compare(ResultRow o1, ResultRow o2) {
            return Long.valueOf(o2.size).compareTo(o1.size);
        }

        public String toString() {
            return "BY_SIZE_DESC";
        }
    };



    public ResultRow(JavaClass[] path, int count, long size) {
        super(path);
        this.count = count;
        this.size = size;
    }

    @Override
    public String toString() {
        return getPathStr() +
            ", size=" + size +
            ", count=" + count +
            '}';
    }
}
