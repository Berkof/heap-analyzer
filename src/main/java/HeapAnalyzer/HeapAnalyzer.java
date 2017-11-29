package HeapAnalyzer;

import HeapAnalyzer.util.TextTable;
import org.eclipse.collections.api.iterator.IntIterator;
import org.eclipse.collections.impl.map.mutable.primitive.LongIntHashMap;

import org.netbeans.lib.profiler.heap.FieldValue;
import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.HeapFactory;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaClass;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HeapAnalyzer {
    /**
     * Top classes count shitch use heap.
     */
    public static int TOP_CLASS = 2;

    /**
     * Levels of analys deepth.
     */
    public static int HOLDING_TREE_HEIGHT = 2;

    /**
     * With of analys step
     */
    public static int HOLDING_TREE_WIDTH = 20;


    public static void log(String text) {
        System.out.println(new Date() + " " + text);
    }

    public static Heap load(String filename) throws IOException {
        File sFile = new File(filename);
        if (!sFile.exists())
            throw new IllegalArgumentException("File " + filename + " doesn't exist!");
        if (!sFile.isFile())
            throw new IllegalArgumentException("File " + filename + " not a regular file!");
        log("Loading " + filename + "...");
        return HeapFactory.createFastHeap(sFile);
    }

    public static List<ClassRecord> collectBiggestClasses(Heap heap) {
        HistoAnalyzer histo = new HistoAnalyzer(true);

        for (Instance i : heap.getAllInstances())
            histo.feed(i);

        List<ClassRecord> ht = new ArrayList<ClassRecord>(histo.getHisto());
        Collections.sort(ht, HistoAnalyzer.BY_SIZE_DESC);

        return ht;

    }

    public static Map<PathKey, LongIntHashMap> collectBiggestObjects(Heap heap, List<ClassRecord> biggestClasses) {
        Map<JavaClass, LongIntHashMap> biggestClassObjects = new HashMap();
        for (ClassRecord classRecord : biggestClasses) {
            biggestClassObjects.put(classRecord.jClass, new LongIntHashMap((int) classRecord.instanceCount));
        }
        JavaClass tClass;
        LongIntHashMap tMap;
        for (Instance i : heap.getAllInstances()) {
            tClass = i.getJavaClass();
            tMap = biggestClassObjects.get(tClass);
            if (tMap != null) {
                assert i.getSize() < Integer.MAX_VALUE : "Object greater than Integer.MAX_VALUE, what u plat to optimize?";
                tMap.put(i.getInstanceId(), (int)i.getSize());
            }
        }

        Map<PathKey, LongIntHashMap> result = new HashMap();
        for(ClassRecord cr : biggestClasses)
            result.put(new PathKey(cr), biggestClassObjects.get(cr.jClass));

        return result;
    }

    public static void addResult(Instance i, boolean addSelfSize, int holdedSize, PathKey holdedPR, Map<PathKey, LongIntHashMap> result) {

        PathKey newPathRecord = new PathKey(i.getJavaClass(), holdedPR);
        LongIntHashMap pathMap = result.get(newPathRecord);
        if (pathMap == null)
            result.put(newPathRecord, pathMap = new LongIntHashMap());
        long instanceId = i.getInstanceId();
        int holded = pathMap.get(instanceId);
        if (addSelfSize)
            holded += i.getSize();
        holded += holdedSize;
        pathMap.put(instanceId, holded);

    }

    public static Map<PathKey, LongIntHashMap> doStep(Heap head, Map<PathKey, LongIntHashMap> prevLvl) {

        Map<PathKey, LongIntHashMap> result = new HashMap();

        // Prepare arrays for fast iterations
        PathKey paths[] = new PathKey[prevLvl.size()];
        LongIntHashMap maps[] = new LongIntHashMap[prevLvl.size()];
        int i = 0;
        for (Map.Entry<PathKey, LongIntHashMap> entry : prevLvl.entrySet()) {
            paths[i] = entry.getKey();
            maps[i++] = entry.getValue();
        }

        long tHoldedSize;
        long tFieldRef;
        boolean countInstanceSelfSize;
        for (Instance instance : head.getAllInstances()) {
            countInstanceSelfSize = false;

            List<FieldValue> values = instance.getFieldValues();
            if (values != null) {
                for (FieldValue fv : values) {
                    if (fv.getField().getType().getName() == "object") {
                        String value = fv.getValue();
                        //System.out.println(value);
                        if (value != null) {
                            tFieldRef = Long.valueOf(value);
                            for (i = 0;i < maps.length;i++) {
                                tHoldedSize = maps[i].get(tFieldRef);
                                if (tHoldedSize > 0L) {
                                    addResult (instance, !countInstanceSelfSize, (int)tHoldedSize, paths[i], result);
                                    countInstanceSelfSize = true;
                                }
                            }
                        }
                    }
                }
            }
        }


        return result;
    }

    public static void printResult(ResultRow[] result) {
        System.out.println(new Date() + " Next step:");
        for (ResultRow rr : result)
            System.out.println(rr);
    }

    public static ResultRow[] collectResult (Map<PathKey, LongIntHashMap> lvl) {

        PathKey tPK;
        LongIntHashMap tEntryMap;
        long tSizeSum;
        ResultRow[] result = new ResultRow[lvl.size()];
        int i = 0;
        for (Map.Entry<PathKey, LongIntHashMap> entry : lvl.entrySet()) {
            tSizeSum = 0;

            LongIntHashMap instancesMap = entry.getValue();
            IntIterator iterator = instancesMap.intIterator();
            while (iterator.hasNext()) {
                tSizeSum += iterator.next();
            }
            result[i++] = new ResultRow(entry.getKey().path, instancesMap.size(), tSizeSum);
        }
        Arrays.sort(result, ResultRow.BY_SIZE_DESC);
        return result;
    }

    public static void printBiggestClasses(List<ClassRecord> biggestClasses) {
        TextTable tt = new TextTable();
        int n = 0;
        for(ClassRecord cr: biggestClasses.subList(0, 500)) {
            tt.addRow("" + (++n), " " + cr.totalSize, " " + cr.instanceCount, " " + cr.jClass.getName());
        }
        System.out.println(tt.formatTextTableUnbordered(1000));
    }

    public static void main(String[] args) throws IOException {

        if (args.length < 1 || args.length > 4) {
            System.out.println("Start with parameters: <path/to/dump> [<BIGGEST_CLASS_TO_ANALYSE> <HOLDING_TREE_HEIGHT> <HOLDING_TREE_WIDTH>]");
            System.exit(1);
        }
        String dumppath = args[0];
        if (args.length > 1)
            TOP_CLASS = Integer.parseInt(args[1]);
        if (args.length > 2)
            HOLDING_TREE_HEIGHT = Integer.parseInt(args[2]);
        if (args.length > 3)
            HOLDING_TREE_WIDTH = Integer.parseInt(args[3]);

        Heap heap = load(dumppath);
        log( "Heap loaded. Searching for biggest classes...");

        List<ClassRecord> biggestClasses = collectBiggestClasses(heap);
        printBiggestClasses(biggestClasses);
        log("Biggest classes collected. Mapping...");

        biggestClasses = biggestClasses.subList(0, Math.min(biggestClasses.size(), TOP_CLASS));
        Map<PathKey, LongIntHashMap> biggestClassObjects = collectBiggestObjects(heap, biggestClasses);
        log("Biggest classes mapped, search for holding objects...");
        collectResult(biggestClassObjects);

        Map<PathKey, LongIntHashMap> nextLvl = biggestClassObjects;
        for (int i = 0; i < HOLDING_TREE_HEIGHT; i++) {
            nextLvl = doStep(heap, nextLvl);
            ResultRow[] result = collectResult(nextLvl);
            printResult(result);
            result = Arrays.copyOf(result, HOLDING_TREE_WIDTH);
        }
        log("Done");

    }
}
