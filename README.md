# Simple heap analyzer for java heap
This is simple heap analyzer working from bottom to top (from biggest classes to their holders) based on https://github.com/aragozin/jvm-tools

## Intro
1) https://github.com/aragozin/jvm-tools and mvn clean install for installing dependency
2) run HeapAnalyzer from u IDE or make some jar
## Description
This tool parse hava heaps (compressed too) and do:
1) Scan all heap to build class histogram and find <N> biggest (all instance counting) classes
2) Scan all heap to make Map<BiggestClass, Map<instanceId, size>
3) Scan all heap to make Map<{HolderClass,BiggestClass}, Map<holderInstanceId, sumSize>> where sumSize = size of all holded BiggestClass instances and sum of all sizes of holding they HolderInstances.

For example:
1) from histo we get that biggest classes is long[] and char[]
2) build map for long[] with all instances-sizes and map for char[] with all instances-sizes
3) iterate throught all objects in heap and if they point to any instance in map from (2) - collect it as, for example String,char[]->Map<StringInstanceId, sumOfStringObjectAndCharArray>

4) Repeat step 3 for <M> times to get root holders (not really GC root, but after 2-3 steps we go from primitives to business objects and then we can debug it in runtime or better understand heap dump). After each iteration we get longer path from big object to GC root.
TODO: add path filters to filter some primitive classes or paths

## Parameters
run HeapAnalyzer with command line arguments:
1) path to heap dump, mandatory, no default value.
2) BIGGEST_CLASS_TO_ANALYSE - number of biggest class to analyze, optional, default 2.
3) HOLDING_TREE_HEIGHT - number of step 4 iteration, optional, default 2,
4) HOLDING_TREE_WIDTH - after each step only biggest HOLDING_TREE_WIDTH path (with biggest total size) will be processed in next iteration, optional, default 20.

## Internals

Tools based on Alexey Ragozin jvm-tools (hprof-heap part of it) and do very simple think: just open heap and scan all objects one by one for few times.
First scan need to determinate biggest class in heap (just like it do jmap -histo:live <PID>). Next step will map only instances of BIGGEST_CLASS_TO_ANALYSE biggest classes. After this step we have list of biggest class only.
Second scan need to create map for each instances of classes, selected on previous step. After this step we have Map<Class, Map<location,size>> or Map<BiggestClass, Map<BiggestClassInstanceLocation,BCISize>> in another worlds.
Before third step we convert it to Map<PathKey, Map<location, size>> by just wrapping BiggestClass with Path so we see not just BiggestClass, but [BiggerstClass] and can do all future step in same way.
Third and any next scan need to collect all back refs for all out instances and aggregate it by class. After this step we get same Map<PathKey, Map<location,size>>, but PathKey contains not only [BiggestClass], but one more class name, witch instances hold some biggest class instances.
For example if we found that biggest class is char[] after first scan, then after second scan we collect full map of char[]'s in heap (all instances with it's locations) and on third scan we will test for every object (class instance) in heap:
if any of object fields have object ref type - we try to find it in map of char[] and if we can do it, then current object have reference to char[] and we know char[] size (without necessity to seek to char[] location in heap file) so we can try to add or modify record in scan result map. Record key will be: [current object class, for example String, char[]] or [java.lang.String,char[]] and value will be Map with current object (String) location and char[].size+String.size or <String.location,String.size+char[].size>.

## Example
Lets see real example. We get large heap dump (31g) and jmap show us that biggest class is long[] (more than 20g total size). But how we can find place in our code where all these long[] holded? We need get few steps of back reference analys to get biggets paths and get some application classes in it.

Use HeapAnalyzer as jar library with such parameters:
java -Xmx8g -cp heap-analyzer.jar  HeapAnalyzer.HeapAnalyzer /mnt/hd23931.hprof 2 3 20
where
2 - analyze only 2 biggest classes, in out example its long[] and java.lang.Object[]
3 - do 3 iteration or backreference analysis (and we get path with length four)
20 - use only 20 biggest paths in next step (in this example I specify 99999 by mistake so we will see some unnecesary paths with very small size, don't do it becouse of CPU cost of searching ref in huge amount of maps)
* SomeClassNNN - just obfuscation of commersial classes, not intresting in our example

We need to copy some class (long[]) and try to find biggest pathes with long[]. After first iteration we get major path of long[]
{java.util.BitSet,long[]}, size=15549369703, count=2142803} 
so if we wanna know where such BitSet stored (15g of summary BitSet.size plus linked long[].sizes) , we need to do the same:
copy path and try to find it in next iteration and we found 
{org.apache.ignite.internal.util.GridPartitionStateMap,java.util.BitSet,long[]}, size=15643590926, count=2142446}
(15g of summary GridPartitionStateMap.size plus linked BirSet.size + linked long[].size). Bingo! We found that more than 15g of 20g long[] holded by GridPartitionStateMap objects. 
If it still not enought - just increase max depth of analysis to get object with hold GridPartitionStateMap.
I recomend to start with depth=10 and run analysis with somethink like tee:
java -Xmx8g -cp heap-analyzer.jar  HeapAnalyzer.HeapAnalyzer /mnt/hd23931.hprof 5 10 20 | tee /tmp/FD5122.txt
so u can see result after each step and if it's not enought - just wait for next step to complete...
Btw, speed of heap scanning is about 200Mb for first two scans and about 100Mb for next ones (depend of HOLDING_TREE_WIDTH parameter) on my laptop (i7 and ssd is present).

System property HIST_SIZE lidmit initial class histogram size, default value is 500.

```
Wed Nov 29 15:59:49 NOVT 2017 Loading /mnt/ssd/work/gg/FD/FD-5122_memory_consumption/real/hd23931.hprof...
Wed Nov 29 16:01:44 NOVT 2017 Heap loaded. Searching for biggest classes...
1   20669881600 2200098  long[]                                                                                                  
2   1718701000  5200034  java.lang.Object[]                                                                                      
3   1505537156  30392    byte[]                                                                                                  
4   627717346   2293033  char[]                                                                                                  
5   477882636   10860969 java.util.HashMap$Node                                                                                  
6   201057304   204189   java.util.HashMap$Node[]                                                                                
7   170961840   2374470  java.util.concurrent.locks.ReentrantReadWriteLock$NonfairSync                                           
8   163505420   8175271  java.lang.Integer                                                                                       
9   163410656   5106583  java.util.ArrayList                                                                                     
10  138985280   1737316  org.apache.ignite.internal.processors.cache.persistence.file.FilePageStoreV2                            
11  120386616   59005    org.jsr166.ConcurrentHashMap8$Node[]                                                                    
12  111407140   2142445  org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionMap               
13  94267624    2142446  org.apache.ignite.internal.util.GridPartitionStateMap                                                   
14  76446744    1737426  java.io.File                                                                                            
15  74109840    3087910  java.util.concurrent.atomic.AtomicLong                                                                  
16  69781600    1744540  java.util.concurrent.locks.ReentrantReadWriteLock                                                       
17  69329600    2166550  java.util.UUID                                                                                          
18  64026256    2286652  java.lang.String                                                                                        
19  62141287    2142803  java.util.BitSet                                                                                        
20  60043228    2144401  org.apache.ignite.internal.processors.affinity.AffinityTopologyVersion                                  
21  56987328    2374472  java.util.concurrent.locks.ReentrantReadWriteLock$ReadLock                                              
22  56987328    2374472  java.util.concurrent.locks.ReentrantReadWriteLock$WriteLock                                             
23  47489440    2374472  java.util.concurrent.locks.ReentrantReadWriteLock$Sync$ThreadLocalHoldCounter                           
24  34580080    335332   org.jsr166.ConcurrentLinkedHashMap$HashEntry[]                                                          
25  33105171    649121   javax.management.MBeanAttributeInfo                                                                     
26  30626752    294488   org.apache.ignite.internal.util.StripedCompositeReadWriteLock$ReadLock                                  
27  26826560    335332   org.jsr166.ConcurrentLinkedHashMap$Segment                                                              
28  23649584    422314   org.jsr166.ConcurrentLinkedHashMap$HashEntry                                                            
29  16968704    116224   java.lang.reflect.Method                                                                                
30  16678784    260606   java.util.HashMap                                                                                       
31  14169276    322029   java.util.concurrent.ConcurrentHashMap$Node                                                             
32  13900864    146      org.apache.ignite.internal.processors.cache.persistence.file.FilePageStore[]                            
33  11808080    295202   java.util.concurrent.ConcurrentSkipListMap$Node                                                         
34  11276160    352380   org.apache.ignite.internal.processors.cache.version.GridCacheVersion                                    
35  10307736    286326   org.jsr166.LongAdder8                                                                                   
36  9618918     65883    org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtLocalPartition                       
37  9169052     134839   org.jsr166.ConcurrentHashMap8                                                                           
38  8816888     4399     java.util.concurrent.atomic.AtomicLong[]                                                                
39  8783344     274475   org.apache.ignite.internal.processors.cache.persistence.freelist.PagesList$Stripe[]                     
40  8661000     86610    java.util.concurrent.ConcurrentHashMap                                                                  
41  8102940     38666    int[]                                                                                                   
42  7758128     68656    java.lang.reflect.Field                                                                                 
43  7308576     166104   org.jsr166.ConcurrentHashMap8$Node                                                                      
44  6862325     274493   org.apache.ignite.internal.processors.cache.persistence.freelist.PagesList$Stripe                       
45  6825344     21617    java.util.concurrent.ConcurrentHashMap$Node[]                                                           
46  6459464     146806   java.util.concurrent.locks.ReentrantLock$NonfairSync                                                    
47  6038232     10607    javax.management.MBeanAttributeInfo[]                                                                   
48  5676600     141915   java.util.concurrent.ConcurrentSkipListMap$Index                                                        
49  5174448     107801   org.apache.ignite.internal.GridLoggerProxy                                                              
50  5165040     86084    java.util.LinkedHashMap$Entry                                                                           
51  4870480     121762   org.jsr166.ConcurrentLinkedDeque8$Node                                                                  
52  4545927     65883    org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore      
53  4224074     10721    org.apache.ignite.configuration.CacheConfiguration                                                      
54  3963654     48934    java.util.LinkedHashMap                                                                                 
55  3651200     36512    org.h2.value.NullableKeyConcurrentMap                                                                   
56  3519216     146634   java.util.concurrent.locks.ReentrantLock                                                                
57  3489040     87226    org.jsr166.ConcurrentLinkedDeque8                                                                       
58  3374624     76696    java.util.concurrent.CountDownLatch$Sync                                                                
59  3253584     67783    org.apache.ignite.internal.processors.cache.GridCacheLogger                                             
60  3183992     20889    org.jsr166.ConcurrentLinkedHashMap$Segment[]                                                            
61  2926280     146314   java.util.concurrent.atomic.AtomicInteger                                                               
62  2853520     71338    java.util.LinkedList$Node                                                                               
63  2803300     140165   java.util.concurrent.atomic.AtomicBoolean                                                               
64  2756160     20880    org.apache.ignite.internal.util.GridBoundedConcurrentLinkedHashMap                                      
65  2745560     68639    java.util.LinkedList                                                                                    
66  2695025     107801   org.apache.ignite.logger.slf4j.Slf4jLogger                                                              
67  2484616     5363     org.apache.ignite.internal.util.StripedCompositeReadWriteLock$ReadLock[]                                
68  2320000     58000    org.apache.ignite.internal.processors.cache.GridCacheConcurrentMap$CacheMapHolder                       
69  2298816     71838    java.util.concurrent.CopyOnWriteArrayList                                                               
70  2241720     56043    org.apache.ignite.internal.processors.cache.transactions.IgniteTxManager$CommittedVersion               
71  2174139     65883    org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtLocalPartition$2                     
72  2000768     17864    SomeType                                    
73  1840704     76696    java.util.concurrent.CountDownLatch                                                                     
74  1686916     10954    org.h2.table.Column                                                                                     
75  1651468     58981    org.apache.ignite.internal.processors.cache.GridCacheIoManager$ListenerKey                              
76  1586290     5986     org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex$1                                   
77  1553784     64741    java.util.HashSet                                                                                       
78  1441792     16384    org.apache.ignite.internal.processors.jobmetrics.GridJobMetricsSnapshot                                 
79  1439064     5214     org.apache.ignite.internal.processors.cache.GridCacheContext                                            
80  1404296     26705    javax.management.ObjectName$Property[]                                                                  
81  1335908     47711    javax.management.ObjectName$Property                                                                    
82  1293072     5214     org.apache.ignite.internal.processors.cache.CacheMetricsImpl                                            
83  1287588     21108    javax.management.MBeanOperationInfo                                                                     
84  1224216     22039    java.lang.String[]                                                                                      
85  1189584     13518    org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionFullMap           
86  1105368     5214     SomeType148                                   
87  1098880     68680    java.lang.Object                                                                                        
88  1041564     5986     org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex                                     
89  1027176     42799    java.util.Collections$UnmodifiableSet                                                                   
90  1007961     4253     org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$2    
91  933306      5214     org.apache.ignite.internal.processors.cache.query.GridCacheDistributedQueryManager                      
92  857840      10723    java.util.concurrent.ConcurrentSkipListMap                                                              
93  852520      21313    sun.reflect.annotation.AnnotationInvocationHandler                                                      
94  837848      30110    java.lang.Class[]                                                                                       
95  826056      18774    java.util.Hashtable$Entry                                                                               
96  821184      25662    org.apache.ignite.lang.IgniteUuid                                                                       
97  816816      10608    javax.management.MBeanInfo                                                                              
98  762328      13613    java.lang.ThreadLocal$ThreadLocalMap$Entry                                                              
99  750136      153      java.nio.ByteBuffer[]                                                                                   
100 740672      5216     org.h2.schema.Schema                                                                                    
101 734415      13353    javax.management.ObjectName                                                                             
102 711189      12477    java.util.TreeMap$Entry                                                                                 
103 700608      17088    org.apache.ignite.internal.processors.affinity.HistoryAffinityAssignment                                
104 695332      15803    org.apache.ignite.internal.util.GridSpinReadWriteLock                                                   
105 667264      10426    org.apache.ignite.internal.processors.cache.CacheDefaultBinaryAffinityKeyMapper                         
106 661848      2507     org.h2.command.dml.Select                                                                               
107 655928      11713    java.lang.ref.SoftReference                                                                             
108 651750      5214     org.apache.ignite.internal.processors.cache.DynamicCacheDescriptor                                      
109 641432      3256     org.apache.ignite.internal.processors.cache.distributed.dht.atomic.GridDhtAtomicCache                   
110 633697      4253     org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$1    
111 614740      8782     org.h2.expression.ExpressionColumn                                                                      
112 600912      25038    java.util.HashMap$KeySet                                                                                
113 590296      10557    javax.management.MBeanOperationInfo[]                                                                   
114 573384      6826     org.apache.ignite.internal.processors.query.h2.opt.GridH2MetaTable$MetaRow                              
115 564312      10110    org.h2.table.IndexColumn[]                                                                              
116 563112      5214     SomeType149                                 
117 547470      5214     org.apache.ignite.internal.processors.cache.datastructures.CacheDataStructuresManager                   
118 526640      13166    com.sun.jmx.mbeanserver.StandardMBeanSupport                                                            
119 514296      2213     java.util.Hashtable$Entry[]                                                                             
120 500928      20872    java.util.concurrent.atomic.AtomicLongArray                                                             
121 500592      10429    org.apache.ignite.internal.util.GridReflectionCache                                                     
122 500544      5214     org.apache.ignite.internal.processors.cache.query.continuous.CacheContinuousQueryManager                
123 493284      11211    java.util.concurrent.ConcurrentSkipListMap$HeadIndex                                                    
124 481072      4496     org.apache.xerces.impl.xs.opti.ElementImpl                                                              
125 479952      13332    org.h2.table.IndexColumn                                                                                
126 472566      11526    SomeType2                         
127 463795      2507     org.h2.table.TableFilter                                                                                
128 463730      5870     org.apache.ignite.internal.processors.query.property.QueryBinaryProperty                                
129 450528      18772    java.util.HashMap$EntrySet                                                                              
130 447280      5591     java.util.TreeMap                                                                                       
131 442484      15803    org.apache.ignite.internal.util.GridSpinReadWriteLock$1                                                 
132 440100      4401     sun.nio.ch.FileChannelImpl                                                                              
133 437840      273      java.nio.channels.SelectionKey[]                                                                        
134 437248      13664    org.apache.ignite.internal.processors.cache.distributed.dht.preloader.CachePartitionFullCountersMap     
135 427200      17088    org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTopologyFutureAdapter$CacheValidation
136 426976      13343    com.sun.jmx.mbeanserver.NamedObject                                                                     
137 417440      10436    org.apache.ignite.internal.processors.cache.ratemetrics.HitRateMetrics                                  
138 416072      9241     org.h2.table.Column[]                                                                                   
139 413000      3304     SomeType3                                         
140 410040      1608     org.apache.ignite.internal.processors.query.h2.opt.GridH2Table                                          
141 387072      10752    javax.management.ImmutableDescriptor                                                                    
142 383740      4487     short[]                                                                                                 
143 379152      15798    org.apache.ignite.internal.util.GridSpinBusyLock                                                        
144 376272      15678    org.jsr166.ConcurrentHashMap8$ValuesView                                                                
145 376248      15677    java.util.HashMap$Values                                                                                
146 376152      15673    org.apache.ignite.internal.managers.eventstorage.GridEventStorageManager$LocalListenerWrapper           
147 375408      5214     org.apache.ignite.internal.processors.cache.query.GridCacheQueryMetricsAdapter                          
148 375408      10428    org.apache.ignite.internal.util.GridBoundedConcurrentOrderedSet                                         
149 375408      5214     org.apache.ignite.internal.processors.plugin.CachePluginManager                                         
150 367770      1886     org.apache.ignite.internal.direct.stream.v2.DirectByteBufferStreamImplV2                                
151 367360      3480     org.apache.xerces.impl.xs.opti.NodeImpl[]                                                               
152 358664      3352     org.apache.xerces.impl.xs.opti.ElementImpl                                                              
153 357252      4253     org.apache.ignite.internal.processors.cache.IgniteCacheOffheapManagerImpl$CacheDataStoreImpl            
154 353304      689      java.lang.ThreadLocal$ThreadLocalMap$Entry[]                                                            
155 348473      2507     org.h2.jdbc.JdbcPreparedStatement                                                                       
156 347104      10847    org.apache.ignite.internal.processors.query.h2.database.InlineIndexHelper                               
157 344544      7178     java.lang.ref.WeakReference                                                                             
158 338910      5214     org.apache.ignite.internal.processors.cache.GridCacheTtlManager                                         
159 338910      5214     org.apache.ignite.internal.processors.cache.IgniteCacheProxyImpl                                        
160 338350      5050     org.apache.xerces.impl.xs.opti.AttrImpl                                                                 
161 334272      10446    org.apache.ignite.internal.mxbean.IgniteStandardMXBean                                                  
162 334080      10440    org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$RemoveFromLeaf                   
163 334080      10440    org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$LockBackAndRmvFromLeaf           
164 334080      10440    org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$Insert                           
165 334080      10440    org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$LockTail                         
166 334080      10440    org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$LockBackAndTail                  
167 334080      10440    org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$LockTailForward                  
168 334080      10440    org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$Replace                          
169 334080      10440    org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$AskNeighbor                      
170 334080      10440    org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$Search                           
171 333696      5214     org.apache.ignite.internal.processors.cache.GridCacheAffinityManager                                    
172 332206      2723     java.lang.reflect.Constructor                                                                           
173 323070      1958     org.apache.ignite.internal.processors.cache.distributed.dht.colocated.GridDhtColocatedCache             
174 313375      2507     org.h2.index.IndexCursor                                                                                
175 300072      12503    org.h2.value.ValueString                                                                                
176 297597      5221     org.apache.ignite.internal.processors.timeout.GridTimeoutProcessor$CancelableTask                       
177 295008      12292    java.util.concurrent.atomic.AtomicReference                                                             
178 292320      10440    org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$TreeMetaData                     
179 291960      8935     org.w3c.dom.Attr[]                                                                                      
180 283008      3216     org.apache.ignite.cache.QueryEntity                                                                     
181 280784      2507     org.h2.jdbc.JdbcResultSet                                                                               
182 279792      1608     org.apache.ignite.internal.processors.query.QueryTypeDescriptorImpl                                     
183 271180      5215     org.apache.ignite.internal.processors.query.h2.H2Schema                                                 
184 270120      2565     org.apache.xerces.impl.xs.opti.NodeImpl[]                                                               
185 269568      11232    java.util.LinkedHashMap$LinkedEntrySet                                                                  
186 266928      1608     org.apache.ignite.internal.processors.query.h2.database.H2PkHashIndex                                   
187 262680      4378     org.apache.ignite.internal.processors.query.QueryIndexDescriptorImpl                                    
188 262200      6555     java.lang.ref.ReferenceQueue                                                                            
189 261000      10440    org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$1                                
190 260480      3256     org.apache.ignite.lang.IgniteBiInClosure[]                                                              
191 257304      10721    org.apache.ignite.internal.util.GridAtomicLong                                                          
192 257250      2058     SomeType4                                      
193 255180      4253     org.apache.ignite.internal.processors.cache.IgniteCacheOffheapManagerImpl$CacheDataRowStore             
194 251460      635      org.h2.engine.Session                                                                                   
195 250560      10440    org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$AddRoot                          
196 250560      10440    org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$InitRoot                         
197 250560      10440    org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$CutRoot                          
198 250296      10429    org.apache.ignite.internal.processors.cache.GridCacheDefaultAffinityKeyMapper$1                         
199 250272      5214     SomeType150                                                
200 250272      5214     SomeType151                  
201 250272      5214     org.apache.ignite.internal.processors.cache.GridCacheGateway                                            
202 244818      3654     org.apache.xerces.impl.xs.opti.AttrImpl                                                                 
203 235884      5361     org.apache.ignite.internal.processors.cache.CacheOperationContext                                       
204 234630      5214     org.apache.ignite.internal.managers.discovery.GridDiscoveryManager$CachePredicate                       
205 229736      4418     sun.nio.fs.UnixPath                                                                                     
206 229416      5214     org.apache.ignite.internal.processors.cache.binary.CacheObjectBinaryContext                             
207 229086      3471     SomeType5                                           
208 227072      3548     SomeType6                                                 
209 225152      3518     SomeType7                                                    
210 223300      3190     SomeType8                                                  
211 219801      5361     org.apache.ignite.internal.processors.cache.GatewayProtectedCacheProxy                                  
212 215472      1608     org.apache.ignite.internal.processors.query.h2.opt.GridH2PrimaryScanIndex                               
213 213774      5214     org.apache.ignite.internal.processors.cache.GridCacheEventManager                                       
214 212250      8490     org.apache.ignite.internal.processors.marshaller.MappedName                                             
215 211760      5294     java.beans.MethodRef                                                                                    
216 211200      2200     java.lang.Package                                                                                       
217 210780      10539    java.lang.ThreadLocal                                                                                   
218 210043      5123     SomeType152                                              
219 208560      5214     SomeType153                                           
220 208560      5214     org.apache.ignite.internal.processors.cache.CacheOffheapEvictionManager                                 
221 208560      5214     org.apache.ignite.internal.processors.cache.CacheWeakQueryIteratorsHolder                               
222 204560      5114     SomeType9                                                      
223 194572      6949     java.util.Collections$SingletonList                                                                     
224 193984      1732     java.net.URL                                                                                            
225 193600      1600     SomeType10                                   
226 192632      4378     org.apache.ignite.cache.QueryIndex                                                                      
227 191968      5999     org.apache.ignite.lang.IgniteBiTuple                                                                    
228 191552      5986     org.apache.ignite.internal.processors.query.h2.database.H2Tree[]                                        
229 191552      5986     org.apache.ignite.internal.processors.query.h2.opt.GridH2IndexBase$2                                    
230 185728      5804     org.apache.ignite.internal.util.typedef.T2                                                              
231 185097      869      SomeType11                                                  
232 184536      7689     java.util.concurrent.ConcurrentHashMap$ValuesView                                                       
233 182256      7594     org.apache.ignite.internal.processors.query.h2.opt.GridH2IndexBase$1                                    
234 180536      1093     java.util.WeakHashMap$Entry[]                                                                           
235 174892      7604     org.h2.index.IndexType                                                                                  
236 173974      4702     java.io.FileDescriptor                                                                                  
237 171616      5363     org.apache.ignite.internal.util.StripedCompositeReadWriteLock                                           
238 171122      5033     org.h2.expression.Alias                                                                                 
239 167384      1372     SomeType34                                                         
240 166848      5214     org.apache.ignite.internal.GridCachePluginContext                                                       
241 166848      5214     org.apache.ignite.internal.processors.cache.query.continuous.CacheContinuousQueryManager$BackupCleaner  
242 166848      5214     org.apache.ignite.internal.processors.cache.affinity.GridCacheAffinityImpl                              
243 166848      5214     org.apache.ignite.internal.processors.query.QuerySchema                                                 
244 166144      5215     org.apache.ignite.plugin.CachePluginConfiguration[]                                                     
245 164616      6859     com.sun.proxy.$Proxy141                                                                                 
246 154368      1608     org.apache.ignite.internal.processors.query.h2.H2RowDescriptor                                          
247 150420      2507     org.h2.command.CommandContainer                                                                         
248 148962      4514     sun.reflect.generics.tree.SimpleClassTypeSignature                                                      
249 148104      4114     org.apache.ignite.internal.processors.cache.persistence.tree.io.IOVersions                              
250 145992      5214     org.apache.ignite.internal.processors.cache.GridCacheAdapter$5                                          
251 143962      1582     java.beans.MethodDescriptor                                                                             
252 143664      5986     org.apache.ignite.internal.processors.query.h2.database.H2Tree$1                                        
253 142720      2230     java.lang.ref.Finalizer                                                                                 
254 140096      1258     org.h2.value.Value[]                                                                                    
255 140096      4378     org.apache.ignite.internal.processors.query.QueryIndexKey                                               
256 137840      6892     org.h2.value.ValueInt                                                                                   
257 137825      5513     org.apache.ignite.internal.util.future.GridFutureAdapter                                                
258 134848      4214     java.util.Collections$UnmodifiableRandomAccessList                                                      
259 132608      64       org.apache.ignite.internal.processors.jobmetrics.GridJobMetricsSnapshot[]                               
260 132440      3311     SomeType35                                                          
261 132088      869      SomeType36                                                         
262 130080      5420     java.util.TreeSet                                                                                       
263 128800      5152     org.h2.expression.ValueExpression                                                                       
264 128712      5363     org.apache.ignite.internal.util.StripedCompositeReadWriteLock$WriteLock                                 
265 128640      1608     org.apache.ignite.internal.processors.query.h2.H2TableDescriptor                                        
266 127629      4401     sun.nio.ch.NativeThreadSet                                                                              
267 126290      730      org.springframework.beans.GenericTypeAwarePropertyDescriptor                                            
268 126280      328      org.apache.xerces.impl.dv.xs.XSSimpleTypeDecl                                                           
269 125840      2628     java.security.cert.Certificate[]                                                                        
270 125264      4514     sun.reflect.generics.tree.TypeArgument[]                                                                
271 125160      5215     javax.cache.configuration.FactoryBuilder$SingletonFactory                                               
272 125136      5214     org.apache.ignite.internal.processors.cache.GridCacheTtlManager$1                                       
273 125136      5214     org.apache.ignite.internal.processors.cache.query.GridCacheDistributedQueryManager$1                    
274 125136      5214     org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtCacheAdapter$3                       
275 125136      5214     org.apache.ignite.internal.processors.cache.query.GridCacheDistributedQueryManager$3                    
276 125136      5214     org.apache.ignite.internal.processors.cache.query.continuous.CacheContinuousQueryManager$1              
277 125136      5214     org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtCacheAdapter$1                       
278 125136      5214     org.apache.ignite.internal.processors.cache.query.GridCacheQueryManager$4                               
279 125136      5214     org.apache.ignite.internal.processors.cache.query.GridCacheDistributedQueryManager$2                    
280 125136      5214     org.apache.ignite.internal.processors.cache.CacheClusterMetricsMXBeanImpl                               
281 125136      5214     org.apache.ignite.internal.processors.cache.CacheLocalMetricsMXBeanImpl                                 
282 125136      5214     org.apache.ignite.internal.processors.cache.distributed.dht.GridCachePartitionedConcurrentMap           
283 125136      5214     SomeType148$DiscoveryListener                 
284 123970      322      org.apache.xerces.impl.dv.xs.XSSimpleTypeDecl                                                           
285 121545      2701     org.h2.expression.Comparison                                                                            
286 120696      1128     org.apache.xerces.impl.xs.opti.ElementImpl                                                              
287 119067      559      SomeType13                                              
288 116843      353      org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionsExchangeFuture   
289 110928      4622     java.nio.channels.spi.AbstractInterruptibleChannel$1                                                    
290 109656      4569     com.sun.proxy.$Proxy114                                                                                 
291 108876      633      org.h2.jdbc.JdbcConnection                                                                              
292 108096      4504     sun.reflect.generics.tree.ClassTypeSignature                                                            
293 107568      4482     java.util.concurrent.atomic.AtomicReferenceArray                                                        
294 107136      1674     sun.reflect.generics.repository.MethodRepository                                                        
295 107064      2974     java.lang.Class$AnnotationData                                                                          
296 106896      4454     org.apache.ignite.internal.binary.BinaryFieldMetadata                                                   
297 106456      3802     SomeType37$1                                              
298 105792      4408     java.util.TreeMap$KeySet                                                                                
299 105600      4400     org.apache.ignite.internal.processors.cache.persistence.file.RandomAccessFileIO                         
300 105576      4399     org.apache.ignite.internal.processors.cache.persistence.freelist.PagesList$CutTail                      
301 105120      2920     org.apache.ignite.internal.pagemem.wal.record.CacheState                                                
302 105072      4378     org.apache.ignite.internal.processors.query.QueryIndexDescriptorImpl$1                                  
303 104912      6557     java.lang.ref.ReferenceQueue$Lock                                                                       
304 104192      3256     org.apache.ignite.internal.processors.cache.distributed.dht.atomic.GridDhtAtomicCache$13                
305 104192      3256     org.apache.ignite.internal.processors.cache.distributed.dht.atomic.GridDhtAtomicCache$12                
306 103920      2165     java.util.Collections$UnmodifiableMap                                                                   
307 102072      4253     org.apache.ignite.internal.processors.cache.persistence.freelist.FreeListImpl$WriteRowHandler           
308 102072      4253     org.apache.ignite.internal.processors.cache.persistence.freelist.FreeListImpl$UpdateRowHandler          
309 102072      4253     org.apache.ignite.internal.processors.cache.persistence.freelist.FreeListImpl$RemoveRowHandler          
310 101424      4226     java.util.concurrent.ConcurrentHashMap$EntrySetView                                                     
311 100432      2507     org.h2.expression.Expression[]                                                                          
312 96264       4011     com.sun.proxy.$Proxy111                                                                                 
313 93312       2932     java.lang.reflect.Type[]                                                                                
314 92086       2246     SomeType38                                                                        
315 91936       1352     java.util.WeakHashMap$Entry                                                                             
316 91248       3802     SomeType39                                                   
317 91168       3256     org.apache.ignite.internal.processors.cache.distributed.dht.atomic.GridDhtAtomicCache$1                 
318 91120       670      java.util.jar.JarFile$JarFileEntry                                                                      
319 88209       297      org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTxPrepareRequest                     
320 88102       1798     SomeType40                                                       
321 87780       1140     sun.nio.cs.UTF_8$Encoder                                                                                
322 87395       227      org.apache.xerces.impl.dv.xs.XSSimpleTypeDecl                                                           
323 85568       1337     java.util.Hashtable                                                                                     
324 85552       1608     org.apache.ignite.internal.processors.query.GridQueryProperty[]                                         
325 85272       3084     sun.reflect.generics.tree.FieldTypeSignature[]                                                          
326 83681       2041     SomeType41                              
327 83312       5207     javax.cache.expiry.EternalExpiryPolicy                                                                  
328 83240       795      org.apache.xerces.impl.xs.opti.NodeImpl[]                                                               
329 82280       1870     java.lang.StackTraceElement                                                                             
330 81520       2038     sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl                                            
331 80608       2519     SomeType42                                                    
332 80448       1676     sun.reflect.generics.tree.MethodTypeSignature                                                           
333 80145       685      java.beans.PropertyDescriptor                                                                           
334 79310       1442     org.apache.ignite.internal.managers.communication.GridIoMessage                                         
335 79248       1016     org.apache.ignite.spi.discovery.tcp.messages.TcpDiscoveryCustomEventMessage                             
336 79104       3296     java.util.LinkedHashMap$LinkedKeySet                                                                    
337 78750       630      SomeType43                             
338 78750       630      SomeType44                               
339 78336       1088     java.util.WeakHashMap                                                                                   
340 78144       3256     org.apache.ignite.internal.processors.cache.distributed.dht.atomic.GridDhtAtomicCache$14                
341 78144       3256     org.apache.ignite.internal.processors.cache.distributed.dht.atomic.GridDhtAtomicCache$11                
342 78144       3256     org.apache.ignite.internal.processors.cache.distributed.dht.atomic.GridDhtAtomicCache$15                
343 78144       3256     org.apache.ignite.internal.processors.cache.distributed.dht.atomic.GridDhtAtomicCache$8                 
344 78144       3256     org.apache.ignite.internal.processors.cache.distributed.dht.atomic.GridDhtAtomicCache$9                 
345 78144       3256     org.apache.ignite.internal.processors.cache.distributed.dht.atomic.GridDhtAtomicCache$7                 
346 78144       3256     org.apache.ignite.internal.processors.cache.distributed.dht.atomic.GridDhtAtomicCache$10                
347 78144       3256     org.apache.ignite.internal.processors.cache.distributed.dht.atomic.GridDhtAtomicCache$5                 
348 78144       3256     org.apache.ignite.internal.processors.cache.distributed.dht.atomic.GridDhtAtomicCache$6                 
349 78144       3256     org.apache.ignite.internal.processors.cache.distributed.dht.atomic.GridDhtAtomicCache$3                 
350 78144       3256     org.apache.ignite.internal.processors.cache.distributed.dht.atomic.GridDhtAtomicCache$4                 
351 78144       3256     org.apache.ignite.internal.processors.cache.distributed.dht.atomic.GridDhtAtomicCache$2                 
352 77824       2048     org.apache.ignite.internal.processors.query.h2.database.io.H2ExtrasInnerIO                              
353 75480       1258     org.apache.ignite.internal.processors.query.h2.opt.GridH2RowFactory$RowSimple                           
354 74875       599      SomeType45                                    
355 74680       139      char[][]                                                                                                
356 74536       1331     SomeType46                                                            
357 71104       808      org.apache.ignite.internal.cluster.ClusterGroupAdapter                                                  
358 70912       2216     java.util.ArrayDeque                                                                                    
359 70650       1413     org.apache.ignite.cache.affinity.rendezvous.RendezvousAffinityFunction                                  
360 70416       4401     sun.nio.ch.FileDispatcherImpl                                                                           
361 69632       2048     org.apache.ignite.internal.processors.query.h2.database.io.H2ExtrasLeafIO                               
362 69360       510      org.apache.ignite.internal.managers.discovery.DiscoCache                                                
363 69344       148      java.util.concurrent.locks.Lock[]                                                                       
364 68746       929      sun.util.calendar.ZoneInfo                                                                              
365 68200       682      org.apache.ignite.internal.processors.query.h2.opt.GridH2KeyValueRowOnheap                              
366 68080       1840     org.h2.expression.Parameter                                                                             
367 67360       2105     sun.reflect.generics.factory.CoreReflectionFactory                                                      
368 66062       986      org.apache.xerces.impl.xs.opti.AttrImpl                                                                 
369 65536       2048     org.apache.ignite.internal.processors.query.h2.database.io.H2ExtrasInnerIO[]                            
370 65536       2048     org.apache.ignite.internal.processors.query.h2.database.io.H2ExtrasLeafIO[]                             
371 64000       1600     SomeType47                                                        
372 63984       186      org.apache.ignite.internal.ClusterMetricsSnapshot                                                       
373 63975       853      SomeType154                          
374 63631       323      org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionsFullMessage      
375 62832       308      org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTxFinishRequest                      
376 62656       1958     org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTransactionalCacheAdapter$9          
377 62656       1958     org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTransactionalCacheAdapter$8          
378 60768       1688     org.apache.ignite.internal.processors.query.QueryTypeIdKey                                              
379 60564       1442     org.apache.ignite.internal.util.nio.GridNioServer$WriteRequestImpl                                      
380 60480       840      java.util.Properties                                                                                    
381 60210       669      java.util.jar.JarFile                                                                                   
382 60164       676      sun.misc.URLClassPath$JarLoader                                                                         
383 59976       2499     SomeType48                              
384 59624       514      com.sun.org.apache.xerces.internal.impl.xs.XSElementDecl                                                
385 59388       2121     java.util.jar.Attributes$Name                                                                           
386 58984       808      SomeType49                                                                        
387 58650       345      org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode                                           
388 57144       2381     java.lang.Long                                                                                          
389 56400       1175     SomeType53                       
390 56064       2336     java.util.LinkedHashSet                                                                                 
391 54859       461      org.apache.xerces.dom.PSVIAttrNSImpl                                                                    
392 53676       252      SomeType14                                                    
393 53568       1674     sun.reflect.generics.scope.MethodScope                                                                  
394 53544       776      ch.qos.logback.classic.Logger                                                                           
395 53280       360      org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTxPrepareResponse                    
396 52632       612      SomeType55                                                       
397 52548       906      java.util.zip.Inflater                                                                                  
398 51851       1003     boolean[]                                                                                               
399 51456       1608     org.apache.ignite.internal.processors.cache.query.QueryTable                                            
400 51456       1608     org.apache.ignite.internal.processors.query.h2.H2TypeKey                                                
401 51456       1608     org.apache.ignite.internal.processors.query.h2.database.H2RowFactory                                    
402 51456       1608     org.apache.ignite.internal.processors.query.QueryTypeNameKey                                            
403 51040       3190     SomeType56                                             
404 49344       771      SomeType59                                                                
405 49247       1331     org.h2.expression.ConditionAndOr                                                                        
406 49008       2013     sun.reflect.generics.tree.FormalTypeParameter[]                                                         
407 48456       2019     java.util.jar.Attributes                                                                                
408 48233       139      com.sun.org.apache.xerces.internal.impl.dv.xs.XSSimpleTypeDecl                                          
409 48190       790      org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTxPrepareFuture$MiniFuture           
410 47360       740      org.apache.ignite.internal.binary.BinarySchema                                                          
411 46992       1958     org.apache.ignite.internal.processors.cache.distributed.dht.colocated.GridDhtColocatedCache$1           
412 46992       1958     org.apache.ignite.internal.processors.cache.distributed.dht.colocated.GridDhtColocatedCache$2           
413 46992       1958     org.apache.ignite.internal.processors.cache.distributed.dht.colocated.GridDhtColocatedCache$3           
414 46992       1958     org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTransactionalCacheAdapter$7          
415 46992       1958     org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTransactionalCacheAdapter$6          
416 46992       1958     org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTransactionalCacheAdapter$3          
417 46992       1958     org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTransactionalCacheAdapter$5          
418 46992       1958     org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTransactionalCacheAdapter$4          
419 46992       1958     org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTransactionalCacheAdapter$2          
420 46992       1958     org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTransactionalCacheAdapter$1          
421 46355       635      org.h2.engine.UndoLog                                                                                   
422 45962       938      SomeType34$ExternalAffinityCollectionAggregate                     
423 45423       927      java.util.zip.ZipCoder                                                                                  
424 45360       1008     org.h2.expression.ConditionInConstantSet                                                                
425 45248       808      org.apache.ignite.internal.IgniteComputeImpl                                                            
426 44642       202      SomeType15                                          
427 44640       1116     javax.xml.namespace.QName                                                                               
428 44640       279      org.apache.xerces.xs.XSNamedMap[]                                                                       
429 44208       614      sun.misc.Cleaner                                                                                        
430 44000       1100     org.apache.xerces.util.SymbolTable$Entry                                                                
431 43520       1088     org.apache.xerces.util.SymbolTable$Entry                                                                
432 43386       1033     SomeType157                          
433 43172       172      org.springframework.beans.factory.support.RootBeanDefinition                                            
434 42265       535      java.nio.DirectByteBuffer                                                                               
435 41360       220      org.apache.ignite.thread.IgniteThread                                                                   
436 41080       5        net.sf.saxon.om.NamePool$NameEntry[]                                                                    
437 41080       5        net.sf.saxon.om.NamePool$NameEntry[]                                                                    
438 41080       5        net.sf.saxon.om.NamePool$NameEntry[]                                                                    
439 40800       850      org.apache.ignite.internal.binary.BinaryFieldImpl                                                       
440 40512       633      org.h2.util.CloseWatcher                                                                                
441 40464       1676     sun.reflect.generics.tree.TypeSignature[]                                                               
442 40128       2508     SomeType60                                     
443 39534       599      org.apache.xerces.impl.dv.ValidatedInfo                                                                 
444 38986       202      SomeType61                                            
445 38889       261      org.springframework.beans.SimplePropertyDescriptor                                                      
446 38796       732      java.io.ObjectStreamField                                                                               
447 38520       963      org.dom4j.tree.DefaultAttribute                                                                         
448 38304       252      SomeType33                                                           
449 38272       736      org.h2.index.IndexCondition                                                                             
450 38200       955      java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject                                   
451 37944       1054     java.util.Vector                                                                                        
452 37714       173      org.apache.ignite.internal.util.nio.GridSelectorNioSessionImpl                                          
453 37130       790      org.apache.ignite.internal.processors.cache.distributed.GridDistributedTxMapping                        
454 37120       1160     SomeType32                                        
455 36960       660      sun.reflect.generics.reflectiveObjects.WildcardTypeImpl                                                 
456 36476       829      java.math.BigInteger                                                                                    
457 36080       902      sun.security.x509.RDN                                                                                   
458 35992       66       java.lang.String[][]                                                                                    
459 35917       733      org.apache.ignite.internal.binary.BinaryObjectImpl                                                      
460 35629       869      SomeType27                                              
461 35328       384      org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTxFinishResponse                     
462 35316       218      sun.nio.ch.SocketChannelImpl                                                                            
463 35080       350      java.security.ProtectionDomain[]                                                                        
464 34640       866      SomeType31                     
465 34504       432      org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtLocalPartition[]                     
466 34336       592      java.security.ProtectionDomain                                                                          
467 34320       858      java.net.InetAddress$InetAddressHolder                                                                  
468 34016       1063     org.h2.value.ValueTimestamp                                                                             
469 33855       915      sun.security.util.DerValue                                                                              
470 33792       1024     org.apache.ignite.spi.discovery.tcp.ServerImpl$PendingMessage                                           
471 33696       702      org.apache.xerces.xni.QName                                                                             
472 33696       702      org.apache.xerces.xni.QName                                                                             
473 33075       189      java.io.ObjectStreamClass                                                                               
474 33040       590      java.security.CodeSource                                                                                
475 33040       826      org.apache.xerces.util.SymbolTable$Entry                                                                
476 33032       15       short[][]                                                                                               
477 32940       915      sun.security.util.DerInputBuffer                                                                        
478 32928       2058     SomeType23                               
479 32736       682      org.h2.value.ValueJavaObject$NotSerialized                                                              
480 32648       404      org.apache.xerces.util.SymbolHash$Entry[]                                                               
481 32504       173      int[][]                                                                                                 
482 32320       808      SomeType12                                                                 
483 32256       1008     org.h2.expression.ConditionInConstantSet$1                                                              
484 32200       322      org.apache.ignite.internal.events.DiscoveryCustomEvent                                                  
485 32186       418      java.util.concurrent.ConcurrentHashMap$TreeNode                                                         
486 31828       146      org.apache.ignite.internal.processors.cache.persistence.MetadataStorage$MetaTree                        
487 31476       258      sun.net.www.protocol.jar.URLJarFile                                                                     
488 30877       401      org.apache.ignite.internal.binary.BinaryMetadata                                                        
489 30732       591      java.util.concurrent.locks.AbstractQueuedSynchronizer$Node                                              
490 30545       205      java.util.GregorianCalendar                                                                             
491 30448       176      org.springframework.beans.GenericTypeAwarePropertyDescriptor                                            
492 30420       845      sun.reflect.NativeConstructorAccessorImpl                                                               
493 30240       336      org.springframework.beans.PropertyValue                                                                 
494 29928       1247     java.util.LinkedHashMap$LinkedValues                                                                    
495 29744       676      org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionExchangeId        
496 29350       587      com.sun.org.apache.xerces.internal.impl.xs.XSParticleDecl                                               
497 28920       723      org.apache.xerces.util.SymbolHash$Entry                                                                 
498 28864       902      sun.security.x509.AVA[]                                                                                 
499 28864       902      sun.security.x509.AVA                                                                                   
500 28800       320      org.springframework.beans.PropertyValue                                                                 

Wed Nov 29 16:03:57 NOVT 2017 Biggest classes collected. Mapping...
Wed Nov 29 16:05:55 NOVT 2017 Biggest classes mapped, search for holding objects...
Wed Nov 29 16:09:47 NOVT 2017 Next step:
{java.util.BitSet,long[]}, size=15549369703, count=2142803}
{org.apache.ignite.internal.processors.cache.distributed.dht.preloader.CachePartitionFullCountersMap,long[]}, size=5157514944, count=13664}
{java.util.ArrayList,java.lang.Object[]}, size=1838952968, count=5106583}
{java.util.concurrent.atomic.AtomicReferenceArray,java.lang.Object[]}, size=22844016, count=4482}
{org.apache.ignite.internal.pagemem.wal.record.CacheState,long[]}, size=21257760, count=2920}
{java.util.ArrayDeque,java.lang.Object[]}, size=16039744, count=2216}
{java.util.concurrent.atomic.AtomicLongArray,long[]}, size=4339456, count=20872}
{java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=4121432, count=71838}
{javax.management.ImmutableDescriptor,java.lang.Object[]}, size=900968, count=10752}
{sun.util.calendar.ZoneInfo,long[]}, size=608850, count=893}
{java.util.Vector,java.lang.Object[]}, size=444464, count=1054}
{sun.nio.ch.NativeThreadSet,long[]}, size=303717, count=4401}
{org.apache.ignite.internal.util.nio.GridSelectorNioSessionImpl,java.lang.Object[]}, size=130442, count=173}
{org.apache.ignite.internal.util.tostring.GridToStringThreadLocal,java.lang.Object[]}, size=63440, count=305}
{java.util.Stack,java.lang.Object[]}, size=37440, count=212}
{org.apache.xerces.util.AugmentationsImpl$SmallContainer,java.lang.Object[]}, size=23956, count=113}
{org.apache.xerces.util.AugmentationsImpl$SmallContainer,java.lang.Object[]}, size=23956, count=113}
{net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=23664, count=234}
{net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=23664, count=234}
{net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=23664, count=234}
{org.apache.ignite.internal.util.GridHandleTable,java.lang.Object[]}, size=20984, count=122}
{org.apache.xerces.util.AugmentationsImpl$SmallContainer,java.lang.Object[]}, size=19292, count=91}
{org.apache.ignite.internal.marshaller.optimized.OptimizedObjectInputStream$HandleTable,java.lang.Object[]}, size=18744, count=122}
{org.apache.ignite.internal.util.GridLongList,long[]}, size=18480, count=308}
{java.io.ObjectStreamClass$FieldReflector,long[]}, size=13924, count=77}
{org.springframework.beans.factory.support.RootBeanDefinition,java.lang.Object[]}, size=13250, count=46}
{java.lang.invoke.LambdaForm$Name,java.lang.Object[]}, size=12546, count=129}
{net.sf.saxon.z.IntHashMap,java.lang.Object[]}, size=11680, count=12}
{net.sf.saxon.z.IntHashMap,java.lang.Object[]}, size=11680, count=12}
{net.sf.saxon.z.IntHashMap,java.lang.Object[]}, size=11680, count=12}
{com.sun.istack.FinalArrayList,java.lang.Object[]}, size=10880, count=80}
{com.sun.istack.FinalArrayList,java.lang.Object[]}, size=10880, count=80}
{com.sun.istack.FinalArrayList,java.lang.Object[]}, size=10880, count=80}
{java.util.IdentityHashMap,java.lang.Object[]}, size=10864, count=19}
{org.eclipse.jetty.util.ArrayTrie,java.lang.Object[]}, size=9591, count=5}
{org.springframework.beans.factory.support.RootBeanDefinition,java.lang.Object[]}, size=9021, count=31}
{sun.nio.cs.StandardCharsets$Aliases,java.lang.Object[]}, size=8272, count=1}
{com.sun.org.apache.xerces.internal.impl.xs.models.XSDFACM,java.lang.Object[]}, size=7908, count=35}
{org.apache.kafka.common.protocol.types.Struct,java.lang.Object[]}, size=7488, count=92}
{java.lang.ClassNotFoundException,java.lang.Object[]}, size=5888, count=46}
{com.sun.org.apache.xerces.internal.util.AugmentationsImpl$SmallContainer,java.lang.Object[]}, size=5700, count=25}
{java.lang.ThreadLocal$ThreadLocalMap$Entry,java.lang.Object[]}, size=5304, count=51}
{com.sun.istack.internal.FinalArrayList,java.lang.Object[]}, size=4944, count=35}
{java.net.ConnectException,java.lang.Object[]}, size=3960, count=33}
{org.apache.kafka.common.protocol.types.Struct,java.lang.Object[]}, size=3520, count=43}
{java.util.concurrent.ArrayBlockingQueue,java.lang.Object[]}, size=3364, count=7}
{org.h2.util.BitField,long[]}, size=2100, count=1}
{org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=1654, count=5}
{org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=1654, count=5}
{java.util.EnumMap,java.lang.Object[]}, size=1576, count=8}
{org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=1278, count=2}
{ch.qos.logback.core.helpers.CyclicBuffer,java.lang.Object[]}, size=1264, count=1}
{ch.qos.logback.core.helpers.CyclicBuffer,java.lang.Object[]}, size=1264, count=1}
{ch.qos.logback.core.helpers.CyclicBuffer,java.lang.Object[]}, size=1264, count=1}
{org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=1240, count=4}
{org.apache.kafka.common.protocol.types.Struct,java.lang.Object[]}, size=1216, count=15}
{org.eclipse.jetty.util.ArrayTernaryTrie,java.lang.Object[]}, size=1091, count=1}
{org.h2.mvstore.Page,java.lang.Object[]}, size=1063, count=7}
{org.apache.ignite.internal.mem.unsafe.UnsafeMemoryProvider,long[]}, size=1040, count=2}
{org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1024, count=2}
{com.sun.org.apache.xerces.internal.impl.XMLEntityManager,java.lang.Object[]}, size=996, count=4}
{java.util.PriorityQueue,java.lang.Object[]}, size=760, count=5}
{org.apache.log4j.ProvisionNode,java.lang.Object[]}, size=700, count=5}
{scala.collection.mutable.HashSet,java.lang.Object[]}, size=656, count=2}
{scala.collection.mutable.HashSet,java.lang.Object[]}, size=656, count=2}
{scala.collection.mutable.HashSet,java.lang.Object[]}, size=656, count=2}
{java.lang.OutOfMemoryError,java.lang.Object[]}, size=480, count=4}
{org.springframework.beans.factory.support.ManagedList,java.lang.Object[]}, size=436, count=4}
{java.sql.SQLException,java.lang.Object[]}, size=420, count=3}
{com.sun.xml.bind.v2.util.CollisionCheckStack,java.lang.Object[]}, size=404, count=2}
{com.sun.xml.bind.v2.util.CollisionCheckStack,java.lang.Object[]}, size=404, count=2}
{com.sun.xml.bind.v2.util.CollisionCheckStack,java.lang.Object[]}, size=404, count=2}
{java.lang.NumberFormatException,java.lang.Object[]}, size=360, count=3}
{sun.nio.cs.StandardCharsets$Cache,java.lang.Object[]}, size=336, count=1}
{sun.nio.cs.StandardCharsets$Classes,java.lang.Object[]}, size=336, count=1}
{com.google.common.collect.RegularImmutableSet,java.lang.Object[]}, size=328, count=2}
{com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=328, count=4}
{org.h2.mvstore.ConcurrentArrayList,java.lang.Object[]}, size=296, count=6}
{org.springframework.context.annotation.ConfigurationClassBeanDefinitionReader$ConfigurationClassBeanDefinition,java.lang.Object[]}, size=283, count=1}
{org.eclipse.jetty.util.BlockingArrayQueue,java.lang.Object[]}, size=260, count=1}
{org.apache.logging.log4j.core.util.datetime.FormatCache$MultipartKey,java.lang.Object[]}, size=228, count=3}
{org.springframework.beans.factory.support.ManagedList,java.lang.Object[]}, size=210, count=2}
{com.google.common.collect.RegularImmutableSet,java.lang.Object[]}, size=184, count=1}
{net.sf.saxon.expr.instruct.Bindery,long[]}, size=178, count=2}
{net.sf.saxon.expr.instruct.Bindery,long[]}, size=178, count=2}
{net.sf.saxon.expr.instruct.Bindery,long[]}, size=178, count=2}
{java.io.ObjectInputStream$HandleTable,java.lang.Object[]}, size=152, count=1}
{com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=152, count=2}
{org.postgresql.util.PSQLException,java.lang.Object[]}, size=148, count=1}
{org.springframework.context.annotation.ConfigurationClassParser$ImportStack,java.lang.Object[]}, size=148, count=1}
{org.springframework.cglib.core.ClassNameReader$EarlyExitException,java.lang.Object[]}, size=120, count=1}
{java.lang.Exception,java.lang.Object[]}, size=120, count=1}
{scala.Array$,java.lang.Object[]}, size=112, count=1}
{sun.util.locale.provider.LocaleResources$ResourceReference,java.lang.Object[]}, size=112, count=1}
{scala.Array$,java.lang.Object[]}, size=112, count=1}
{scala.Array$,java.lang.Object[]}, size=112, count=1}
{sun.invoke.util.Wrapper,long[]}, size=106, count=1}
{sun.invoke.util.Wrapper,java.lang.Object[]}, size=106, count=1}
{org.postgresql.core.v3.SimpleParameterList,java.lang.Object[]}, size=80, count=1}
{org.postgresql.core.v3.SimpleParameterList,java.lang.Object[]}, size=80, count=1}
{org.postgresql.core.v3.SimpleParameterList,java.lang.Object[]}, size=80, count=1}
{scala.collection.mutable.WrappedArray$ofRef,java.lang.Object[]}, size=57, count=1}
{scala.collection.mutable.WrappedArray$ofRef,java.lang.Object[]}, size=57, count=1}
{scala.collection.mutable.WrappedArray$ofRef,java.lang.Object[]}, size=57, count=1}
{org.apache.xerces.impl.xs.util.ObjectListImpl,java.lang.Object[]}, size=56, count=1}
{org.apache.xerces.impl.xs.util.ObjectListImpl,java.lang.Object[]}, size=56, count=1}
{ch.qos.logback.classic.spi.TurboFilterList,java.lang.Object[]}, size=56, count=1}
{ch.qos.logback.classic.spi.TurboFilterList,java.lang.Object[]}, size=56, count=1}
{ch.qos.logback.classic.spi.TurboFilterList,java.lang.Object[]}, size=56, count=1}
{org.apache.xerces.impl.Constants$ArrayEnumeration,java.lang.Object[]}, size=52, count=1}
{org.apache.xerces.impl.Constants$ArrayEnumeration,java.lang.Object[]}, size=52, count=1}
{org.apache.xerces.impl.Constants$ArrayEnumeration,java.lang.Object[]}, size=52, count=1}
{com.sun.org.apache.xerces.internal.impl.Constants$ArrayEnumeration,java.lang.Object[]}, size=52, count=1}
{scala.Array$,long[]}, size=24, count=1}
{scala.Array$,long[]}, size=24, count=1}
{scala.Array$,long[]}, size=24, count=1}
Wed Nov 29 16:20:32 NOVT 2017 Next step:
{org.apache.ignite.internal.util.GridPartitionStateMap,java.util.BitSet,long[]}, size=15643590926, count=2142446}
{org.apache.ignite.internal.processors.affinity.HistoryAffinityAssignment,java.util.ArrayList,java.lang.Object[]}, size=5868722816, count=17088}
{java.util.HashMap$Node,org.apache.ignite.internal.processors.cache.distributed.dht.preloader.CachePartitionFullCountersMap,long[]}, size=5130303336, count=13518}
{java.util.HashMap$Node,java.util.ArrayList,java.lang.Object[]}, size=1475333848, count=998644}
{javax.management.MBeanAttributeInfo,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=72052854, count=649114}
{org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtPartitionTopologyImpl,org.apache.ignite.internal.processors.cache.distributed.dht.preloader.CachePartitionFullCountersMap,long[]}, size=27827570, count=146}
{org.apache.ignite.internal.processors.affinity.GridAffinityAssignment,java.util.ArrayList,java.lang.Object[]}, size=27821730, count=146}
{java.util.HashMap$Node,org.apache.ignite.internal.pagemem.wal.record.CacheState,long[]}, size=21386240, count=2920}
{org.apache.ignite.internal.processors.affinity.GridAffinityAssignmentCache,java.util.ArrayList,java.lang.Object[]}, size=13925538, count=146}
{org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtPartitionTopologyImpl,java.util.concurrent.atomic.AtomicReferenceArray,java.lang.Object[]}, size=13904368, count=146}
{org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtLocalPartition,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=13332502, count=65883}
{org.apache.ignite.internal.util.nio.GridNioRecoveryDescriptor,java.util.ArrayDeque,java.lang.Object[]}, size=11365408, count=173}
{org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$1,java.util.concurrent.atomic.AtomicReferenceArray,java.lang.Object[]}, size=9547985, count=4253}
{java.util.Collections$UnmodifiableRandomAccessList,java.util.ArrayList,java.lang.Object[]}, size=8390832, count=3686}
{org.apache.ignite.internal.processors.cache.ratemetrics.HitRateMetrics,java.util.concurrent.atomic.AtomicLongArray,long[]}, size=4756896, count=10436}
{org.jsr166.ConcurrentLinkedHashMap$Segment,java.util.ArrayDeque,java.lang.Object[]}, size=4493984, count=1044}
{org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex$1,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=3825054, count=5986}
{org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$2,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=3725628, count=4253}
{org.apache.ignite.internal.processors.cache.IgniteCacheOffheapManagerImpl$CacheDataRowStore,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=2972847, count=4253}
{org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$1,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=2717667, count=4253}
{javax.management.MBeanOperationInfo,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=2542521, count=21009}
{org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex$1,java.util.ArrayList,java.lang.Object[]}, size=2374346, count=5986}
{org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex,java.util.ArrayList,java.lang.Object[]}, size=1829620, count=5986}
{javax.management.MBeanInfo,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=1706872, count=10608}
{org.apache.ignite.configuration.CacheConfiguration,java.util.ArrayList,java.lang.Object[]}, size=1580116, count=3450}
{org.h2.command.dml.Select,java.util.ArrayList,java.lang.Object[]}, size=1546408, count=2507}
{SomeType148,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=1397352, count=5214}
{SomeType5,java.util.ArrayList,java.lang.Object[]}, size=1173198, count=3471}
{org.apache.ignite.internal.util.nio.GridNioServer$WriteRequestImpl,org.apache.ignite.internal.util.nio.GridSelectorNioSessionImpl,java.lang.Object[]}, size=1147832, count=1442}
{SomeType11,java.util.ArrayList,java.lang.Object[]}, size=1129033, count=869}
{org.apache.ignite.internal.processors.plugin.CachePluginManager,java.util.ArrayList,java.lang.Object[]}, size=1084512, count=5214}
{sun.nio.ch.FileChannelImpl,sun.nio.ch.NativeThreadSet,long[]}, size=743817, count=4401}
{SomeType13,java.util.ArrayList,java.lang.Object[]}, size=703811, count=559}
{org.h2.table.TableFilter,java.util.ArrayList,java.lang.Object[]}, size=685227, count=2507}
{org.apache.ignite.internal.processors.query.h2.opt.GridH2Table,java.util.ArrayList,java.lang.Object[]}, size=668232, count=1608}
{org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionsExchangeFuture,java.util.ArrayList,java.lang.Object[]}, size=608427, count=353}
{sun.reflect.generics.tree.ClassTypeSignature,java.util.ArrayList,java.lang.Object[]}, size=540480, count=4504}
{org.jsr166.ConcurrentHashMap8$Node,org.apache.ignite.internal.util.nio.GridSelectorNioSessionImpl,java.lang.Object[]}, size=536992, count=346}
{java.util.concurrent.ConcurrentHashMap$Node,sun.util.calendar.ZoneInfo,long[]}, size=425442, count=563}
{java.util.HashMap$Node,sun.util.calendar.ZoneInfo,long[]}, size=377376, count=648}
{SomeType14,java.util.ArrayList,java.lang.Object[]}, size=343180, count=252}
{SomeType15,java.util.ArrayList,java.lang.Object[]}, size=331330, count=202}
{org.h2.engine.Session,java.util.ArrayList,java.lang.Object[]}, size=307340, count=635}
{org.apache.ignite.cache.QueryEntity,java.util.ArrayList,java.lang.Object[]}, size=266576, count=1608}
{java.beans.MethodDescriptor,java.util.ArrayList,java.lang.Object[]}, size=242178, count=1582}
{SomeType16,java.util.ArrayList,java.lang.Object[]}, size=184757, count=129}
{java.util.jar.JarFile,java.util.ArrayDeque,java.lang.Object[]}, size=183306, count=669}
{java.util.Collections$UnmodifiableCollection,java.util.ArrayList,java.lang.Object[]}, size=171888, count=192}
{java.util.GregorianCalendar,sun.util.calendar.ZoneInfo,long[]}, size=169647, count=193}
{sun.util.calendar.Gregorian$Date,sun.util.calendar.ZoneInfo,long[]}, size=162313, count=193}
{org.h2.engine.UndoLog,java.util.ArrayList,java.lang.Object[]}, size=158115, count=635}
{org.apache.ignite.internal.util.nio.GridTcpNioCommunicationClient,org.apache.ignite.internal.util.nio.GridSelectorNioSessionImpl,java.lang.Object[]}, size=140822, count=173}
{sun.nio.ch.SelectionKeyImpl,org.apache.ignite.internal.util.nio.GridSelectorNioSessionImpl,java.lang.Object[]}, size=139611, count=173}
{org.h2.expression.ConditionInConstantSet,java.util.ArrayList,java.lang.Object[]}, size=134064, count=1008}
{org.apache.ignite.internal.processors.cache.persistence.MetadataStorage$MetaTree,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=125122, count=146}
{SomeType17,java.util.Vector,java.lang.Object[]}, size=124264, count=4}
{org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTxPrepareRequest,java.util.BitSet,long[]}, size=106326, count=297}
{org.apache.ignite.internal.processors.cache.persistence.tree.reuse.ReuseListImpl,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=106288, count=146}
{sun.reflect.DelegatingClassLoader,java.util.Vector,java.lang.Object[]}, size=103950, count=270}
{org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode,java.util.ArrayList,java.lang.Object[]}, size=102978, count=345}
{org.apache.ignite.internal.processors.cache.persistence.MetadataStorage,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=100448, count=146}
{org.apache.ignite.internal.processors.cache.CacheGroupContext,java.util.ArrayList,java.lang.Object[]}, size=92470, count=146}
{org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl$Segment,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=83664, count=112}
{SomeType18,java.lang.Object[]}, size=82628, count=2}
{SomeType19,java.util.Vector,java.lang.Object[]}, size=82612, count=2}
{org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTxFinishRequest,org.apache.ignite.internal.util.GridLongList,long[]}, size=81312, count=308}
{org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl$PagePool,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=80598, count=114}
{sun.net.www.protocol.jar.URLJarFile,java.util.ArrayDeque,java.lang.Object[]}, size=78948, count=258}
{java.util.LinkedList$Node,org.apache.ignite.internal.util.tostring.GridToStringThreadLocal,java.lang.Object[]}, size=75640, count=305}
{org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.ArrayDeque,java.lang.Object[]}, size=65843, count=1}
{java.io.ObjectStreamClass,java.io.ObjectStreamClass$FieldReflector,long[]}, size=59991, count=189}
{org.apache.kafka.common.metrics.Sensor,java.util.ArrayList,java.lang.Object[]}, size=58320, count=162}
{SomeType20,java.util.ArrayList,java.lang.Object[]}, size=48443, count=39}
{org.apache.kafka.common.metrics.Sensor,java.util.ArrayList,java.lang.Object[]}, size=47520, count=132}
{org.apache.ignite.internal.processors.cache.IgniteCacheOffheapManagerImpl$PendingEntriesTree,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=47300, count=55}
{org.apache.ignite.events.DiscoveryEvent,java.util.ArrayList,java.lang.Object[]}, size=46896, count=32}
{ch.qos.logback.classic.Logger,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=46575, count=323}
{java.io.FilePermissionCollection,java.util.ArrayList,java.lang.Object[]}, size=43953, count=273}
{org.apache.ignite.internal.processors.cache.GridCacheSharedTtlCleanupManager,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=41120, count=1}
{org.dom4j.tree.DefaultElement,java.util.ArrayList,java.lang.Object[]}, size=40416, count=260}
{org.apache.ignite.internal.marshaller.optimized.OptimizedObjectInputStream,org.apache.ignite.internal.marshaller.optimized.OptimizedObjectInputStream$HandleTable,java.lang.Object[]}, size=39240, count=122}
{org.apache.ignite.internal.marshaller.optimized.OptimizedObjectOutputStream,org.apache.ignite.internal.util.GridHandleTable,java.lang.Object[]}, size=38918, count=122}
{org.apache.kafka.common.metrics.Sensor,java.util.ArrayList,java.lang.Object[]}, size=34560, count=96}
{org.apache.ignite.spi.discovery.tcp.ServerImpl$PendingMessages,java.util.ArrayDeque,java.lang.Object[]}, size=32872, count=1}
{sun.nio.ch.EPollArrayWrapper,java.util.BitSet,long[]}, size=32586, count=51}
{org.jsr166.ConcurrentLinkedHashMap$HashEntry,java.util.ArrayList,java.lang.Object[]}, size=32560, count=290}
{SomeType7,java.util.ArrayList,java.lang.Object[]}, size=31248, count=93}
{org.springframework.beans.MutablePropertyValues,java.util.ArrayList,java.lang.Object[]}, size=31027, count=315}
{org.dom4j.tree.DefaultElement,java.util.ArrayList,java.lang.Object[]}, size=30544, count=198}
{org.apache.xerces.dom.PSVIElementNSImpl,org.apache.xerces.impl.xs.util.ObjectListImpl,java.lang.Object[]}, size=29016, count=93}
{javax.management.MBeanParameterInfo,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=27612, count=253}
{org.apache.xerces.util.AugmentationsImpl,org.apache.xerces.util.AugmentationsImpl$SmallContainer,java.lang.Object[]}, size=26668, count=113}
{org.apache.xerces.util.AugmentationsImpl,org.apache.xerces.util.AugmentationsImpl$SmallContainer,java.lang.Object[]}, size=26668, count=113}
{org.springframework.beans.factory.support.DisposableBeanAdapter,java.util.ArrayList,java.lang.Object[]}, size=22350, count=119}
{SomeType21,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=21760, count=1}
{org.apache.xerces.util.AugmentationsImpl,org.apache.xerces.util.AugmentationsImpl$SmallContainer,java.lang.Object[]}, size=21476, count=91}
{com.sun.org.apache.xerces.internal.impl.dv.xs.XSSimpleTypeDecl,java.util.Vector,java.lang.Object[]}, size=20986, count=38}
{net.sf.saxon.style.LiteralResultElement,java.util.ArrayList,java.lang.Object[]}, size=20984, count=61}
{net.sf.saxon.style.LiteralResultElement,java.util.ArrayList,java.lang.Object[]}, size=20984, count=61}
{net.sf.saxon.style.LiteralResultElement,java.util.ArrayList,java.lang.Object[]}, size=20984, count=61}
{org.springframework.beans.MutablePropertyValues,java.util.ArrayList,java.lang.Object[]}, size=20138, count=194}
{java.security.Provider$Service,java.util.ArrayList,java.lang.Object[]}, size=20109, count=117}
{ch.qos.logback.classic.Logger,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=19600, count=136}
{java.lang.ref.SoftReference,java.io.ObjectStreamClass$FieldReflector,long[]}, size=18236, count=77}
{net.sf.saxon.style.XSLTemplate,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=18050, count=50}
{net.sf.saxon.style.XSLTemplate,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=18050, count=50}
{net.sf.saxon.style.XSLTemplate,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=18050, count=50}
{org.eclipse.jetty.util.ConcurrentArrayQueue$Block,java.util.concurrent.atomic.AtomicReferenceArray,java.lang.Object[]}, size=16736, count=4}
{sun.misc.URLClassPath,java.util.ArrayList,java.lang.Object[]}, size=15500, count=12}
{java.util.concurrent.ConcurrentHashMap$Node,org.springframework.beans.factory.support.RootBeanDefinition,java.lang.Object[]}, size=15274, count=46}
{com.sun.xml.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=13524, count=49}
{com.sun.xml.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=13524, count=49}
{com.sun.xml.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=13524, count=49}
{org.apache.ignite.internal.util.tostring.GridToStringClassDescriptor,java.util.ArrayList,java.lang.Object[]}, size=12680, count=65}
{org.apache.kafka.common.metrics.stats.Avg,java.util.ArrayList,java.lang.Object[]}, size=12312, count=114}
{org.apache.xerces.dom.AttributeMap,java.util.ArrayList,java.lang.Object[]}, size=12090, count=93}
{java.util.logging.Logger,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=11893, count=70}
{net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=11750, count=50}
{net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=11750, count=50}
{net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=11750, count=50}
{com.sun.org.apache.xerces.internal.impl.xs.XSComplexTypeDecl,com.sun.org.apache.xerces.internal.impl.xs.models.XSDFACM,java.lang.Object[]}, size=11618, count=35}
{ch.qos.logback.core.joran.spi.ElementPath,java.util.ArrayList,java.lang.Object[]}, size=11200, count=70}
{ch.qos.logback.core.joran.spi.ElementPath,java.util.ArrayList,java.lang.Object[]}, size=11200, count=70}
{ch.qos.logback.core.joran.spi.ElementPath,java.util.ArrayList,java.lang.Object[]}, size=11200, count=70}
{net.sf.saxon.style.XSLAttribute,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=10770, count=37}
{net.sf.saxon.style.XSLAttribute,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=10770, count=37}
{net.sf.saxon.style.XSLAttribute,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=10770, count=37}
{org.h2.expression.ConditionIn,java.util.ArrayList,java.lang.Object[]}, size=10703, count=75}
{org.apache.kafka.common.metrics.stats.Rate$SampledTotal,java.util.ArrayList,java.lang.Object[]}, size=10692, count=99}
{sun.misc.Launcher$AppClassLoader,java.util.Vector,java.lang.Object[]}, size=10586, count=1}
{javax.management.openmbean.OpenMBeanAttributeInfoSupport,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=10431, count=57}
{java.util.concurrent.ConcurrentHashMap$Node,org.springframework.beans.factory.support.RootBeanDefinition,java.lang.Object[]}, size=10385, count=31}
{org.apache.ignite.internal.marshaller.optimized.OptimizedClassDescriptor,java.util.ArrayList,java.lang.Object[]}, size=10376, count=24}
{org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTxPrepareFuture,java.util.ArrayList,java.lang.Object[]}, size=10330, count=5}
{org.apache.xerces.parsers.XIncludeAwareParserConfiguration,java.util.ArrayList,java.lang.Object[]}, size=10164, count=7}
{org.apache.xerces.parsers.XIncludeAwareParserConfiguration,java.util.ArrayList,java.lang.Object[]}, size=10164, count=7}
{org.apache.xerces.parsers.XIncludeAwareParserConfiguration,java.util.ArrayList,java.lang.Object[]}, size=10164, count=7}
{java.util.Hashtable$Entry,java.util.Vector,java.lang.Object[]}, size=10120, count=55}
{org.apache.kafka.common.metrics.stats.Avg,java.util.ArrayList,java.lang.Object[]}, size=9936, count=92}
{java.util.Collections$UnmodifiableRandomAccessList,com.sun.istack.internal.FinalArrayList,java.lang.Object[]}, size=9728, count=32}
{org.apache.kafka.common.metrics.stats.Count,java.util.ArrayList,java.lang.Object[]}, size=9504, count=88}
{java.util.Collections$UnmodifiableRandomAccessList,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=9424, count=31}
{java.util.Collections$UnmodifiableRandomAccessList,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=9424, count=31}
{java.util.Collections$UnmodifiableRandomAccessList,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=9424, count=31}
{sun.misc.URLClassPath,java.util.Stack,java.lang.Object[]}, size=9360, count=12}
{SomeType22,java.util.ArrayList,java.lang.Object[]}, size=9312, count=8}
{org.apache.kafka.common.metrics.stats.Max,java.util.ArrayList,java.lang.Object[]}, size=9288, count=86}
{com.google.common.cache.LocalCache$Segment,java.util.concurrent.atomic.AtomicReferenceArray,java.lang.Object[]}, size=9140, count=39}
{org.postgresql.core.v3.QueryExecutorImpl,java.util.ArrayList,java.lang.Object[]}, size=8892, count=12}
{java.util.HashMap$Node,java.util.ArrayDeque,java.lang.Object[]}, size=8892, count=39}
{org.apache.kafka.common.metrics.stats.Rate$SampledTotal,java.util.ArrayList,java.lang.Object[]}, size=8748, count=81}
{net.sf.saxon.expr.instruct.SlotManager,java.util.ArrayList,java.lang.Object[]}, size=8612, count=53}
{net.sf.saxon.expr.instruct.SlotManager,java.util.ArrayList,java.lang.Object[]}, size=8612, count=53}
{net.sf.saxon.expr.instruct.SlotManager,java.util.ArrayList,java.lang.Object[]}, size=8612, count=53}
{sun.nio.cs.StandardCharsets,sun.nio.cs.StandardCharsets$Aliases,java.lang.Object[]}, size=8272, count=1}
{net.sf.saxon.style.XSLValueOf,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=8153, count=31}
{net.sf.saxon.style.XSLValueOf,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=8153, count=31}
{net.sf.saxon.style.XSLValueOf,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=8153, count=31}
{net.sf.saxon.trans.Mode,net.sf.saxon.z.IntHashMap,java.lang.Object[]}, size=8125, count=5}
{net.sf.saxon.trans.Mode,net.sf.saxon.z.IntHashMap,java.lang.Object[]}, size=8125, count=5}
{net.sf.saxon.trans.Mode,net.sf.saxon.z.IntHashMap,java.lang.Object[]}, size=8125, count=5}
{org.apache.xerces.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=8109, count=17}
{org.apache.xerces.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=8109, count=17}
{ch.qos.logback.classic.Logger,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=7973, count=57}
{net.sf.saxon.expr.Component,java.util.ArrayList,java.lang.Object[]}, size=7680, count=50}
{net.sf.saxon.expr.Component,java.util.ArrayList,java.lang.Object[]}, size=7680, count=50}
{net.sf.saxon.expr.Component,java.util.ArrayList,java.lang.Object[]}, size=7680, count=50}
{org.apache.kafka.common.metrics.stats.Count,java.util.ArrayList,java.lang.Object[]}, size=7668, count=71}
{org.apache.kafka.common.metrics.stats.Max,java.util.ArrayList,java.lang.Object[]}, size=7560, count=70}
{com.google.common.cache.LocalCache$Segment,java.util.concurrent.atomic.AtomicReferenceArray,java.lang.Object[]}, size=7428, count=35}
{sun.reflect.annotation.TypeNotPresentExceptionProxy,java.lang.ClassNotFoundException,java.lang.Object[]}, size=7360, count=46}
{org.springframework.beans.factory.support.DisposableBeanAdapter,java.util.ArrayList,java.lang.Object[]}, size=7314, count=41}
{org.eclipse.wst.xml.xpath2.processor.internal.DefaultResultSequence,java.util.ArrayList,java.lang.Object[]}, size=7280, count=51}
{org.eclipse.wst.xml.xpath2.processor.internal.DefaultResultSequence,java.util.ArrayList,java.lang.Object[]}, size=7200, count=51}
{org.apache.xerces.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=7155, count=15}
{org.apache.kafka.common.metrics.stats.Avg,java.util.ArrayList,java.lang.Object[]}, size=7128, count=66}
{java.io.FileDescriptor,java.util.ArrayList,java.lang.Object[]}, size=7093, count=41}
{org.apache.ignite.internal.util.UUIDCollectionMessage,java.util.ArrayList,java.lang.Object[]}, size=6720, count=5}
{java.util.Collections$SynchronizedCollection,java.util.ArrayList,java.lang.Object[]}, size=6672, count=1}
{java.util.WeakHashMap$Entry,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=6560, count=46}
{org.apache.kafka.common.metrics.stats.Rate$SampledTotal,java.util.ArrayList,java.lang.Object[]}, size=6372, count=59}
{com.sun.org.apache.xerces.internal.util.AugmentationsImpl,com.sun.org.apache.xerces.internal.util.AugmentationsImpl$SmallContainer,java.lang.Object[]}, size=6300, count=25}
{javax.management.MBeanConstructorInfo,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=6213, count=57}
{org.apache.ignite.internal.util.IgniteExceptionRegistry$ExceptionInfo,java.net.ConnectException,java.lang.Object[]}, size=6072, count=33}
{net.sf.saxon.style.XSLElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=5980, count=20}
{net.sf.saxon.style.XSLElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=5980, count=20}
{net.sf.saxon.style.XSLElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=5980, count=20}
{org.postgresql.core.v3.QueryExecutorImpl,java.util.ArrayList,java.lang.Object[]}, size=5928, count=8}
{java.net.ConnectException,java.net.ConnectException,java.lang.Object[]}, size=5808, count=33}
{org.apache.ignite.internal.marshaller.optimized.OptimizedClassDescriptor$Fields,java.util.ArrayList,java.lang.Object[]}, size=5712, count=24}
{java.lang.ref.WeakReference,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=5640, count=46}
{com.sun.org.apache.xerces.internal.impl.xpath.regex.Token$UnionToken,java.util.ArrayList,java.lang.Object[]}, size=5616, count=34}
{ch.qos.logback.core.joran.spi.ElementSelector,java.util.ArrayList,java.lang.Object[]}, size=5600, count=35}
{ch.qos.logback.core.joran.spi.ElementSelector,java.util.ArrayList,java.lang.Object[]}, size=5600, count=35}
{ch.qos.logback.core.joran.spi.ElementSelector,java.util.ArrayList,java.lang.Object[]}, size=5600, count=35}
{org.apache.xerces.impl.xs.traversers.XSDocumentInfo,java.util.Vector,java.lang.Object[]}, size=5586, count=19}
{net.sf.saxon.style.XSLCallTemplate,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=5544, count=21}
{net.sf.saxon.style.XSLCallTemplate,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=5544, count=21}
{net.sf.saxon.style.XSLCallTemplate,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=5544, count=21}
{org.dom4j.tree.DefaultElement,java.util.ArrayList,java.lang.Object[]}, size=5472, count=36}
{org.apache.kafka.common.metrics.stats.Max,java.util.ArrayList,java.lang.Object[]}, size=5400, count=50}
{org.apache.kafka.common.metrics.stats.Count,java.util.ArrayList,java.lang.Object[]}, size=5400, count=50}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=5239, count=5}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=5239, count=5}
{sun.security.x509.GeneralNames,java.util.ArrayList,java.lang.Object[]}, size=5120, count=32}
{org.apache.ignite.internal.processors.query.h2.twostep.messages.GridQueryNextPageResponse,java.util.ArrayList,java.lang.Object[]}, size=5076, count=36}
{org.apache.ignite.spi.discovery.tcp.messages.TcpDiscoveryNodeAddedMessage,java.util.ArrayList,java.lang.Object[]}, size=4778, count=3}
{org.apache.ignite.internal.marshaller.optimized.OptimizedClassDescriptor$ClassFields,java.util.ArrayList,java.lang.Object[]}, size=4712, count=41}
{java.util.concurrent.ConcurrentHashMap$Node,java.util.ArrayList,java.lang.Object[]}, size=4616, count=22}
{org.apache.xerces.impl.xs.opti.SchemaConditionalIncludeHelper,java.util.ArrayList,java.lang.Object[]}, size=4520, count=5}
{org.apache.xerces.impl.xs.opti.SchemaConditionalIncludeHelper,java.util.ArrayList,java.lang.Object[]}, size=4520, count=5}
{org.apache.xerces.impl.xs.SchemaGrammar,java.util.Vector,java.lang.Object[]}, size=4496, count=8}
{org.apache.xerces.impl.xs.traversers.XSDocumentInfo,java.util.Vector,java.lang.Object[]}, size=4410, count=15}
{org.apache.xerces.impl.xs.opti.SchemaParsingConfig,java.util.ArrayList,java.lang.Object[]}, size=4215, count=5}
{org.apache.xerces.impl.xs.opti.SchemaParsingConfig,java.util.ArrayList,java.lang.Object[]}, size=4215, count=5}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=4108, count=4}
{net.sf.saxon.style.LiteralResultElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=4080, count=40}
{net.sf.saxon.style.LiteralResultElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=4080, count=40}
{net.sf.saxon.style.LiteralResultElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=4080, count=40}
{org.apache.xerces.impl.xs.SchemaGrammar,java.util.Vector,java.lang.Object[]}, size=3934, count=7}
{org.apache.kafka.common.network.Selector,java.util.ArrayList,java.lang.Object[]}, size=3705, count=5}
{org.apache.xerces.impl.xs.opti.SchemaConditionalIncludeHelper,java.util.ArrayList,java.lang.Object[]}, size=3616, count=4}
{com.mchange.v2.c3p0.impl.NewPooledConnection,java.sql.SQLException,java.lang.Object[]}, size=3600, count=12}
{oracle.jrockit.jfr.events.EventHandlerCreator$EventInfoClassLoader,java.util.Vector,java.lang.Object[]}, size=3546, count=9}
{java.util.jar.JarVerifier,java.util.ArrayList,java.lang.Object[]}, size=3456, count=12}
{java.util.Collections$SynchronizedRandomAccessList,java.util.ArrayList,java.lang.Object[]}, size=3448, count=13}
{org.apache.xerces.impl.xs.opti.SchemaParsingConfig,java.util.ArrayList,java.lang.Object[]}, size=3372, count=4}
{org.apache.xerces.impl.xs.SchemaGrammar,java.util.Vector,java.lang.Object[]}, size=3372, count=6}
{com.google.common.util.concurrent.ListenerCallQueue,java.util.ArrayDeque,java.lang.Object[]}, size=3150, count=14}
{org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=3115, count=3}
{net.sf.saxon.style.XSLWhen,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=3048, count=12}
{net.sf.saxon.style.XSLWhen,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=3048, count=12}
{net.sf.saxon.style.XSLWhen,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=3048, count=12}
{org.apache.kafka.common.network.Selector,java.util.ArrayList,java.lang.Object[]}, size=2964, count=4}
{sun.security.x509.CRLDistributionPointsExtension,java.util.ArrayList,java.lang.Object[]}, size=2960, count=16}
{org.apache.logging.log4j.core.config.Node,java.util.ArrayList,java.lang.Object[]}, size=2960, count=15}
{org.h2.engine.Database,org.h2.util.BitField,long[]}, size=2697, count=1}
{org.apache.xerces.impl.xs.traversers.XSDocumentInfo,java.util.Stack,java.lang.Object[]}, size=2660, count=19}
{org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=2523, count=3}
{org.apache.kafka.common.network.Selector$SelectorMetrics,java.util.ArrayList,java.lang.Object[]}, size=2480, count=5}
{net.sf.saxon.style.XSLOutput,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=2430, count=5}
{net.sf.saxon.style.XSLOutput,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=2430, count=5}
{net.sf.saxon.style.XSLOutput,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=2430, count=5}
{com.mchange.v2.c3p0.impl.NewPooledConnection,java.sql.SQLException,java.lang.Object[]}, size=2400, count=8}
{SomeType62,java.util.ArrayList,java.lang.Object[]}, size=2400, count=2}
{org.dom4j.tree.DefaultDocument,java.util.ArrayList,java.lang.Object[]}, size=2352, count=14}
{net.sf.saxon.style.StylesheetPackage,java.util.ArrayList,java.lang.Object[]}, size=2315, count=5}
{net.sf.saxon.style.StylesheetPackage,java.util.ArrayList,java.lang.Object[]}, size=2315, count=5}
{net.sf.saxon.style.StylesheetPackage,java.util.ArrayList,java.lang.Object[]}, size=2315, count=5}
{net.sf.saxon.style.XSLIf,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=2286, count=9}
{net.sf.saxon.style.XSLIf,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=2286, count=9}
{net.sf.saxon.style.XSLIf,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=2286, count=9}
{org.apache.kafka.common.network.Selector,java.util.ArrayList,java.lang.Object[]}, size=2223, count=3}
{net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2220, count=5}
{net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2220, count=5}
{net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2220, count=5}
{net.sf.saxon.functions.FunctionLibraryList,java.util.ArrayList,java.lang.Object[]}, size=2160, count=15}
{net.sf.saxon.functions.FunctionLibraryList,java.util.ArrayList,java.lang.Object[]}, size=2160, count=15}
{net.sf.saxon.functions.FunctionLibraryList,java.util.ArrayList,java.lang.Object[]}, size=2160, count=15}
{org.eclipse.wst.xml.xpath2.processor.internal.ast.FunctionCall,java.util.ArrayList,java.lang.Object[]}, size=2120, count=15}
{org.eclipse.wst.xml.xpath2.processor.internal.ast.FunctionCall,java.util.ArrayList,java.lang.Object[]}, size=2120, count=15}
{org.apache.xerces.impl.xs.traversers.XSDocumentInfo,java.util.Stack,java.lang.Object[]}, size=2100, count=15}
{ch.qos.logback.core.util.COWArrayList,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=2096, count=19}
{ch.qos.logback.core.util.COWArrayList,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=2096, count=19}
{ch.qos.logback.core.util.COWArrayList,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=2096, count=19}
{org.apache.xerces.impl.xs.traversers.XSDocumentInfo,java.util.Vector,java.lang.Object[]}, size=2058, count=7}
{org.apache.ignite.internal.processors.cache.ExchangeDiscoveryEvents,java.util.ArrayList,java.lang.Object[]}, size=2020, count=10}
{org.apache.xerces.impl.dtd.XMLDTDProcessor,java.util.ArrayList,java.lang.Object[]}, size=2009, count=7}
{org.apache.xerces.impl.dtd.XMLDTDProcessor,java.util.ArrayList,java.lang.Object[]}, size=2009, count=7}
{org.apache.xerces.impl.dtd.XMLDTDProcessor,java.util.ArrayList,java.lang.Object[]}, size=2009, count=7}
{org.apache.xerces.impl.dv.xs.XSSimpleTypeDecl,java.util.Vector,java.lang.Object[]}, size=1995, count=3}
{org.apache.xerces.impl.dv.xs.XSSimpleTypeDecl,java.util.Vector,java.lang.Object[]}, size=1995, count=3}
{org.apache.xerces.impl.dv.xs.XSSimpleTypeDecl,java.util.Vector,java.lang.Object[]}, size=1995, count=3}
{org.apache.kafka.common.network.Selector$SelectorMetrics,java.util.ArrayList,java.lang.Object[]}, size=1976, count=4}
{SomeType62,java.util.ArrayList,java.lang.Object[]}, size=1936, count=3}
{org.apache.ignite.internal.binary.BinaryMetadata,java.util.ArrayList,java.lang.Object[]}, size=1917, count=9}
{org.apache.xerces.dom.PSVIElementNSImpl,org.apache.xerces.impl.xs.util.ObjectListImpl,java.lang.Object[]}, size=1872, count=6}
{SomeType63,java.util.ArrayList,java.lang.Object[]}, size=1840, count=10}
{org.postgresql.core.v3.ProtocolConnectionImpl,java.util.ArrayList,java.lang.Object[]}, size=1800, count=12}
{sun.security.x509.CertificatePoliciesExtension,java.util.ArrayList,java.lang.Object[]}, size=1770, count=10}
{org.eclipse.wst.xml.xpath2.processor.internal.ast.FilterExpr,java.util.ArrayList,java.lang.Object[]}, size=1760, count=20}
{org.eclipse.wst.xml.xpath2.processor.internal.ast.FilterExpr,java.util.ArrayList,java.lang.Object[]}, size=1760, count=20}
{org.eclipse.wst.xml.xpath2.processor.internal.ast.FunctionCall,java.util.ArrayList,java.lang.Object[]}, size=1696, count=12}
{org.dom4j.tree.DefaultDocument,java.util.ArrayList,java.lang.Object[]}, size=1680, count=10}
{org.apache.logging.log4j.core.util.datetime.FastDateParser,sun.util.calendar.ZoneInfo,long[]}, size=1622, count=2}
{javax.management.openmbean.OpenMBeanParameterInfoSupport,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=1620, count=9}
{org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.util.Vector,java.lang.Object[]}, size=1610, count=5}
{org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.util.Vector,java.lang.Object[]}, size=1610, count=5}
{org.apache.logging.log4j.core.util.datetime.FastDatePrinter,sun.util.calendar.ZoneInfo,long[]}, size=1564, count=2}
{org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1562, count=1}
{org.apache.xerces.impl.validation.ValidationManager,java.util.ArrayList,java.lang.Object[]}, size=1544, count=12}
{org.apache.xerces.impl.validation.ValidationManager,java.util.ArrayList,java.lang.Object[]}, size=1544, count=12}
{org.springframework.http.converter.StringHttpMessageConverter,java.util.ArrayList,java.lang.Object[]}, size=1537, count=1}
{org.apache.kafka.common.requests.RequestSend,org.apache.kafka.common.protocol.types.Struct,java.lang.Object[]}, size=1530, count=10}
{java.util.concurrent.CopyOnWriteArraySet,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=1512, count=18}
{org.apache.ignite.internal.processors.cache.persistence.GridCacheDatabaseSharedManager,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=1505, count=1}
{org.apache.kafka.common.network.Selector$SelectorMetrics,java.util.ArrayList,java.lang.Object[]}, size=1504, count=3}
{org.apache.logging.log4j.core.config.xml.XmlConfiguration,java.util.ArrayList,java.lang.Object[]}, size=1496, count=4}
{org.apache.ignite.spi.loadbalancing.roundrobin.RoundRobinGlobalLoadBalancer$GridNodeList,java.util.ArrayList,java.lang.Object[]}, size=1488, count=1}
{org.apache.ignite.internal.processors.cache.persistence.MemoryMetricsImpl,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=1466, count=2}
{SomeType64,java.util.ArrayList,java.lang.Object[]}, size=1464, count=2}
{org.apache.xerces.impl.validation.ValidationManager,java.util.ArrayList,java.lang.Object[]}, size=1462, count=11}
{org.apache.kafka.common.requests.FetchRequest,org.apache.kafka.common.protocol.types.Struct,java.lang.Object[]}, size=1440, count=10}
{SomeType139,java.util.ArrayList,java.lang.Object[]}, size=1429, count=1}
{com.sun.org.apache.xerces.internal.impl.XMLNSDocumentScannerImpl,java.util.ArrayList,java.lang.Object[]}, size=1428, count=2}
{org.apache.kafka.clients.consumer.internals.ConsumerCoordinator,java.util.ArrayList,java.lang.Object[]}, size=1412, count=4}
{org.eclipse.wst.xml.xpath2.processor.internal.ast.FilterExpr,java.util.ArrayList,java.lang.Object[]}, size=1408, count=16}
{sun.rmi.transport.Target,java.util.Vector,java.lang.Object[]}, size=1404, count=6}
{ch.qos.logback.core.joran.spi.EventPlayer,java.util.ArrayList,java.lang.Object[]}, size=1396, count=1}
{com.sun.org.apache.xerces.internal.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=1396, count=4}
{ch.qos.logback.core.joran.spi.EventPlayer,java.util.ArrayList,java.lang.Object[]}, size=1396, count=1}
{ch.qos.logback.core.joran.spi.EventPlayer,java.util.ArrayList,java.lang.Object[]}, size=1396, count=1}
{org.apache.ignite.internal.processors.cache.persistence.MemoryPolicy,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=1374, count=2}
{net.sf.saxon.style.XSLPackage,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=1370, count=5}
{net.sf.saxon.style.XSLPackage,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=1370, count=5}
{net.sf.saxon.style.XSLPackage,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=1370, count=5}
{org.apache.ignite.internal.mem.unsafe.UnsafeMemoryProvider,java.util.ArrayList,java.lang.Object[]}, size=1360, count=2}
{org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,org.apache.ignite.internal.mem.unsafe.UnsafeMemoryProvider,long[]}, size=1358, count=2}
{com.google.common.util.concurrent.ListenerCallQueue,java.util.ArrayDeque,java.lang.Object[]}, size=1350, count=6}
{com.google.common.util.concurrent.ServiceManager$ServiceManagerState,java.util.IdentityHashMap,java.lang.Object[]}, size=1340, count=2}
{org.eclipse.jetty.util.ConcurrentArrayQueue,java.util.concurrent.atomic.AtomicReferenceArray,java.lang.Object[]}, size=1328, count=4}
{javax.management.openmbean.ArrayType,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=1314, count=9}
{org.apache.logging.log4j.core.appender.AsyncAppender$AsyncThread,java.util.concurrent.ArrayBlockingQueue,java.lang.Object[]}, size=1312, count=1}
{org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.util.Vector,java.lang.Object[]}, size=1288, count=4}
{java.util.concurrent.ThreadPoolExecutor,java.util.concurrent.ArrayBlockingQueue,java.lang.Object[]}, size=1282, count=2}
{org.apache.kafka.common.requests.RequestHeader,org.apache.kafka.common.protocol.types.Struct,java.lang.Object[]}, size=1280, count=10}
{ch.qos.logback.core.BasicStatusManager,ch.qos.logback.core.helpers.CyclicBuffer,java.lang.Object[]}, size=1264, count=1}
{ch.qos.logback.core.BasicStatusManager,ch.qos.logback.core.helpers.CyclicBuffer,java.lang.Object[]}, size=1264, count=1}
{ch.qos.logback.core.BasicStatusManager,ch.qos.logback.core.helpers.CyclicBuffer,java.lang.Object[]}, size=1264, count=1}
{org.apache.ignite.internal.IgnitionEx$IgniteNamedInstance,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1233, count=1}
{com.sun.xml.internal.stream.XMLEntityStorage,com.sun.org.apache.xerces.internal.impl.XMLEntityManager,java.lang.Object[]}, size=1228, count=4}
{org.apache.logging.log4j.core.appender.AsyncAppender,java.util.concurrent.ArrayBlockingQueue,java.lang.Object[]}, size=1227, count=1}
{org.postgresql.core.v3.ProtocolConnectionImpl,java.util.ArrayList,java.lang.Object[]}, size=1200, count=8}
{org.jsr166.ConcurrentHashMap8$Node,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=1188, count=11}
{java.util.HashMap$Node,com.sun.org.apache.xerces.internal.impl.XMLEntityManager,java.lang.Object[]}, size=1172, count=4}
{net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=1125, count=5}
{net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=1125, count=5}
{net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=1125, count=5}
{org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtLockFuture,java.util.ArrayList,java.lang.Object[]}, size=1120, count=4}
{org.apache.xerces.impl.xs.traversers.XSDTypeAlternativeTraverser,java.util.Vector,java.lang.Object[]}, size=1110, count=5}
{org.apache.xerces.impl.xs.traversers.XSDElementTraverser,java.util.Vector,java.lang.Object[]}, size=1110, count=5}
{org.apache.xerces.impl.xs.traversers.XSDTypeAlternativeTraverser,java.util.Vector,java.lang.Object[]}, size=1110, count=5}
{org.apache.xerces.impl.xs.traversers.XSDElementTraverser,java.util.Vector,java.lang.Object[]}, size=1110, count=5}
{org.apache.xerces.impl.xs.traversers.XSDGroupTraverser,java.util.Vector,java.lang.Object[]}, size=1105, count=5}
{org.apache.xerces.impl.xs.traversers.XSDGroupTraverser,java.util.Vector,java.lang.Object[]}, size=1105, count=5}
{sun.security.x509.ExtendedKeyUsageExtension,java.util.Vector,java.lang.Object[]}, size=1086, count=6}
{org.apache.kafka.common.requests.RequestSend,org.apache.kafka.common.protocol.types.Struct,java.lang.Object[]}, size=1071, count=7}
{org.apache.xerces.impl.xs.traversers.XSDSimpleTypeTraverser,java.util.Vector,java.lang.Object[]}, size=1070, count=5}
{org.apache.xerces.impl.xs.traversers.XSDSimpleTypeTraverser,java.util.Vector,java.lang.Object[]}, size=1070, count=5}
{org.apache.xerces.impl.xs.traversers.XSDKeyrefTraverser,java.util.Vector,java.lang.Object[]}, size=1065, count=5}
{org.apache.xerces.impl.xs.traversers.XSDKeyrefTraverser,java.util.Vector,java.lang.Object[]}, size=1065, count=5}
{org.apache.xerces.impl.xs.traversers.XSDAttributeTraverser,java.util.Vector,java.lang.Object[]}, size=1065, count=5}
{org.apache.xerces.impl.xs.traversers.XSDAttributeGroupTraverser,java.util.Vector,java.lang.Object[]}, size=1065, count=5}
{org.apache.xerces.impl.xs.traversers.XSDNotationTraverser,java.util.Vector,java.lang.Object[]}, size=1065, count=5}
{org.apache.xerces.impl.xs.traversers.XSDWildcardTraverser,java.util.Vector,java.lang.Object[]}, size=1065, count=5}
{org.apache.xerces.impl.xs.traversers.XSDAttributeGroupTraverser,java.util.Vector,java.lang.Object[]}, size=1065, count=5}
{org.apache.xerces.impl.xs.traversers.XSDNotationTraverser,java.util.Vector,java.lang.Object[]}, size=1065, count=5}
{org.apache.xerces.impl.xs.traversers.XSDWildcardTraverser,java.util.Vector,java.lang.Object[]}, size=1065, count=5}
{org.apache.xerces.impl.xs.traversers.XSDUniqueOrKeyTraverser,java.util.Vector,java.lang.Object[]}, size=1065, count=5}
{org.apache.xerces.impl.xs.traversers.XSDAttributeTraverser,java.util.Vector,java.lang.Object[]}, size=1065, count=5}
{org.apache.xerces.impl.xs.traversers.XSDUniqueOrKeyTraverser,java.util.Vector,java.lang.Object[]}, size=1065, count=5}
{org.apache.kafka.clients.consumer.internals.ConsumerCoordinator,java.util.ArrayList,java.lang.Object[]}, size=1059, count=3}
{ch.qos.logback.core.BasicStatusManager,java.util.ArrayList,java.lang.Object[]}, size=1048, count=1}
{ch.qos.logback.core.BasicStatusManager,java.util.ArrayList,java.lang.Object[]}, size=1048, count=1}
{ch.qos.logback.core.BasicStatusManager,java.util.ArrayList,java.lang.Object[]}, size=1048, count=1}
{org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1040, count=5}
{org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1040, count=5}
{org.apache.kafka.common.metrics.Metrics,java.util.ArrayList,java.lang.Object[]}, size=1040, count=5}
{org.apache.kafka.clients.Metadata,java.util.ArrayList,java.lang.Object[]}, size=1035, count=5}
{com.sun.xml.internal.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.internal.FinalArrayList,java.lang.Object[]}, size=1012, count=3}
{org.apache.kafka.common.requests.FetchRequest,org.apache.kafka.common.protocol.types.Struct,java.lang.Object[]}, size=1008, count=7}
{org.apache.xerces.impl.xpath.regex.Token$UnionToken,java.util.Vector,java.lang.Object[]}, size=1008, count=6}
{org.apache.xerces.impl.xpath.regex.Token$UnionToken,java.util.Vector,java.lang.Object[]}, size=1008, count=6}
{org.apache.xerces.impl.xpath.regex.Token$UnionToken,java.util.Vector,java.lang.Object[]}, size=1008, count=6}
{java.util.concurrent.DelayQueue,java.util.PriorityQueue,java.lang.Object[]}, size=1000, count=5}
{org.springframework.beans.PropertyValue,org.springframework.beans.factory.support.ManagedList,java.lang.Object[]}, size=999, count=5}
{com.sun.org.apache.xerces.internal.impl.XMLEntityScanner,com.sun.org.apache.xerces.internal.impl.XMLEntityManager,java.lang.Object[]}, size=996, count=4}
{org.apache.xerces.impl.xs.traversers.XSDocumentInfo,java.util.Stack,java.lang.Object[]}, size=980, count=7}
{org.apache.ignite.internal.processors.cache.GridCacheIoManager,java.util.ArrayList,java.lang.Object[]}, size=966, count=1}
{org.apache.ignite.internal.util.nio.GridNioServer,java.util.ArrayList,java.lang.Object[]}, size=961, count=3}
{org.h2.mvstore.MVMap,org.h2.mvstore.Page,java.lang.Object[]}, size=934, count=6}
{java.util.Hashtable$Entry,org.apache.log4j.ProvisionNode,java.lang.Object[]}, size=920, count=5}
{SomeType65,java.util.ArrayList,java.lang.Object[]}, size=896, count=4}
{org.apache.kafka.common.requests.RequestHeader,org.apache.kafka.common.protocol.types.Struct,java.lang.Object[]}, size=896, count=7}
{SomeType65,java.util.ArrayList,java.lang.Object[]}, size=896, count=4}
{org.apache.xerces.impl.xs.traversers.XSDElementTraverser,java.util.Vector,java.lang.Object[]}, size=888, count=4}
{org.apache.xerces.impl.xs.traversers.XSDTypeAlternativeTraverser,java.util.Vector,java.lang.Object[]}, size=888, count=4}
{org.apache.xerces.impl.xs.traversers.XSDGroupTraverser,java.util.Vector,java.lang.Object[]}, size=884, count=4}
{com.sun.org.apache.xerces.internal.impl.XMLDTDScannerImpl,java.util.ArrayList,java.lang.Object[]}, size=882, count=2}
{org.eclipse.wst.xml.xpath2.processor.internal.ast.AxisStep,java.util.ArrayList,java.lang.Object[]}, size=880, count=10}
{org.eclipse.wst.xml.xpath2.processor.internal.ast.AxisStep,java.util.ArrayList,java.lang.Object[]}, size=880, count=10}
{javax.crypto.CryptoPermissionCollection,java.util.Vector,java.lang.Object[]}, size=872, count=8}
{org.h2.mvstore.MVMap,org.h2.mvstore.ConcurrentArrayList,java.lang.Object[]}, size=866, count=6}
{org.springframework.expression.spel.support.StandardEvaluationContext,java.util.ArrayList,java.lang.Object[]}, size=856, count=3}
{org.apache.xerces.impl.xs.traversers.XSDSimpleTypeTraverser,java.util.Vector,java.lang.Object[]}, size=856, count=4}
{org.apache.xerces.impl.xs.traversers.XSDUniqueOrKeyTraverser,java.util.Vector,java.lang.Object[]}, size=852, count=4}
{org.apache.xerces.impl.xs.traversers.XSDKeyrefTraverser,java.util.Vector,java.lang.Object[]}, size=852, count=4}
{org.apache.xerces.impl.xs.traversers.XSDNotationTraverser,java.util.Vector,java.lang.Object[]}, size=852, count=4}
{org.apache.xerces.impl.xs.traversers.XSDWildcardTraverser,java.util.Vector,java.lang.Object[]}, size=852, count=4}
{org.apache.xerces.impl.xs.traversers.XSDAttributeGroupTraverser,java.util.Vector,java.lang.Object[]}, size=852, count=4}
{org.apache.xerces.impl.xs.traversers.XSDAttributeTraverser,java.util.Vector,java.lang.Object[]}, size=852, count=4}
{javax.management.remote.rmi.NoCallStackClassLoader,java.util.Vector,java.lang.Object[]}, size=850, count=2}
{org.apache.xerces.impl.xs.traversers.OverrideTransformationManager$DocumentContext,java.util.ArrayList,java.lang.Object[]}, size=840, count=5}
{org.apache.xerces.impl.xs.traversers.OverrideTransformationManager$DocumentContext,java.util.ArrayList,java.lang.Object[]}, size=840, count=5}
{ch.qos.logback.classic.pattern.DateConverter,java.util.ArrayList,java.lang.Object[]}, size=836, count=4}
{ch.qos.logback.classic.pattern.DateConverter,java.util.ArrayList,java.lang.Object[]}, size=836, count=4}
{ch.qos.logback.classic.pattern.DateConverter,java.util.ArrayList,java.lang.Object[]}, size=836, count=4}
{org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=832, count=4}
{org.apache.kafka.common.metrics.Metrics,java.util.ArrayList,java.lang.Object[]}, size=832, count=4}
{org.apache.kafka.clients.Metadata,java.util.ArrayList,java.lang.Object[]}, size=812, count=4}
{org.apache.logging.log4j.core.config.LoggerConfig,java.util.ArrayList,java.lang.Object[]}, size=810, count=5}
{scala.util.parsing.json.Lexer,scala.collection.mutable.HashSet,java.lang.Object[]}, size=802, count=1}
{scala.util.parsing.json.Lexer,scala.collection.mutable.HashSet,java.lang.Object[]}, size=802, count=1}
{com.sun.jmx.remote.util.ClassLoaderWithRepository,java.util.Vector,java.lang.Object[]}, size=802, count=2}
{scala.util.parsing.json.Lexer,scala.collection.mutable.HashSet,java.lang.Object[]}, size=802, count=1}
{org.eclipse.wst.xml.xpath2.processor.ast.XPath,java.util.ArrayList,java.lang.Object[]}, size=800, count=5}
{org.eclipse.wst.xml.xpath2.processor.internal.ast.ParExpr,java.util.ArrayList,java.lang.Object[]}, size=800, count=5}
{org.eclipse.wst.xml.xpath2.processor.ast.XPath,java.util.ArrayList,java.lang.Object[]}, size=800, count=5}
{org.eclipse.wst.xml.xpath2.processor.internal.ast.ParExpr,java.util.ArrayList,java.lang.Object[]}, size=800, count=5}
{org.apache.ignite.internal.managers.communication.GridIoManager,java.util.IdentityHashMap,java.lang.Object[]}, size=796, count=1}
{com.sun.xml.bind.v2.runtime.XMLSerializer,com.sun.xml.bind.v2.util.CollisionCheckStack,java.lang.Object[]}, size=796, count=2}
{com.sun.xml.bind.v2.runtime.XMLSerializer,com.sun.xml.bind.v2.util.CollisionCheckStack,java.lang.Object[]}, size=796, count=2}
{com.sun.xml.bind.v2.runtime.XMLSerializer,com.sun.xml.bind.v2.util.CollisionCheckStack,java.lang.Object[]}, size=796, count=2}
{com.sun.org.apache.xerces.internal.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=786, count=1}
{javax.management.remote.rmi.RMIConnectionImpl$CombinedClassLoader,java.util.Vector,java.lang.Object[]}, size=786, count=2}
{org.apache.xerces.dom.AttributeMap,java.util.ArrayList,java.lang.Object[]}, size=780, count=6}
{ch.qos.logback.classic.pattern.LoggerConverter,java.util.ArrayList,java.lang.Object[]}, size=772, count=4}
{SomeType66,java.util.ArrayList,java.lang.Object[]}, size=772, count=4}
{ch.qos.logback.classic.pattern.LoggerConverter,java.util.ArrayList,java.lang.Object[]}, size=772, count=4}
{ch.qos.logback.classic.pattern.LoggerConverter,java.util.ArrayList,java.lang.Object[]}, size=772, count=4}
{javax.management.remote.rmi.RMIConnectionImpl$CombinedClassLoader$ClassLoaderWrapper,java.util.Vector,java.lang.Object[]}, size=770, count=2}
{org.springframework.context.support.ClassPathXmlApplicationContext,java.util.ArrayList,java.lang.Object[]}, size=756, count=3}
{org.springframework.context.support.ClassPathXmlApplicationContext,java.util.ArrayList,java.lang.Object[]}, size=756, count=3}
{SomeType67,java.util.ArrayList,java.lang.Object[]}, size=753, count=3}
{org.apache.ignite.internal.processors.cache.GridCacheProcessor,java.util.IdentityHashMap,java.lang.Object[]}, size=745, count=1}
{org.apache.curator.framework.state.ConnectionStateManager,java.util.concurrent.ArrayBlockingQueue,java.lang.Object[]}, size=744, count=2}
{org.postgresql.core.v3.QueryExecutorImpl,java.util.ArrayList,java.lang.Object[]}, size=741, count=1}
{net.sf.saxon.Controller,net.sf.saxon.expr.instruct.Bindery,long[]}, size=740, count=2}
{net.sf.saxon.Controller,net.sf.saxon.expr.instruct.Bindery,long[]}, size=740, count=2}
{net.sf.saxon.Controller,net.sf.saxon.expr.instruct.Bindery,long[]}, size=740, count=2}
{SomeType68,java.util.ArrayList,java.lang.Object[]}, size=712, count=4}
{org.apache.ignite.internal.managers.eventstorage.GridEventStorageManager,java.util.IdentityHashMap,java.lang.Object[]}, size=712, count=1}
{SomeType69,java.util.ArrayList,java.lang.Object[]}, size=706, count=2}
{org.apache.kafka.clients.consumer.internals.ConsumerCoordinator,java.util.ArrayList,java.lang.Object[]}, size=706, count=2}
{SomeType70,java.util.ArrayList,java.lang.Object[]}, size=705, count=3}
{SomeType71,java.util.ArrayList,java.lang.Object[]}, size=704, count=4}
{org.eclipse.wst.xml.xpath2.processor.internal.ast.AxisStep,java.util.ArrayList,java.lang.Object[]}, size=704, count=8}
{com.oracle.jrockit.jfr.Producer,java.util.ArrayList,java.lang.Object[]}, size=700, count=2}
{org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=700, count=5}
{org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=700, count=5}
{sun.rmi.transport.proxy.RMIMasterSocketFactory,java.util.Vector,java.lang.Object[]}, size=696, count=1}
{java.util.logging.LogManager$RootLogger,java.util.ArrayList,java.lang.Object[]}, size=687, count=1}
{org.apache.ignite.internal.managers.deployment.GridDeploymentManager,java.util.IdentityHashMap,java.lang.Object[]}, size=682, count=1}
{org.apache.ignite.internal.managers.checkpoint.GridCheckpointManager,java.util.IdentityHashMap,java.lang.Object[]}, size=674, count=1}
{org.dom4j.tree.DefaultDocument,java.util.ArrayList,java.lang.Object[]}, size=672, count=4}
{org.apache.xerces.impl.xs.traversers.OverrideTransformationManager$DocumentContext,java.util.ArrayList,java.lang.Object[]}, size=672, count=4}
{com.google.common.util.concurrent.ServiceManager$ServiceManagerState,java.util.IdentityHashMap,java.lang.Object[]}, size=670, count=1}
{net.sf.saxon.tree.tiny.TinyTree,java.util.ArrayList,java.lang.Object[]}, size=662, count=2}
{net.sf.saxon.tree.tiny.TinyTree,java.util.ArrayList,java.lang.Object[]}, size=662, count=2}
{net.sf.saxon.tree.tiny.TinyTree,java.util.ArrayList,java.lang.Object[]}, size=662, count=2}
{java.util.logging.Logger,java.util.ArrayList,java.lang.Object[]}, size=661, count=3}
{sun.misc.Launcher$ExtClassLoader,java.util.Vector,java.lang.Object[]}, size=658, count=1}
{org.apache.ignite.internal.processors.plugin.IgnitePluginProcessor,java.util.IdentityHashMap,java.lang.Object[]}, size=656, count=1}
{org.eclipse.jetty.io.SelectorManager$ManagedSelector,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=656, count=4}
{org.apache.ignite.internal.managers.collision.GridCollisionManager,java.util.IdentityHashMap,java.lang.Object[]}, size=650, count=1}
{org.apache.ignite.internal.managers.indexing.GridIndexingManager,java.util.IdentityHashMap,java.lang.Object[]}, size=650, count=1}
{java.sql.SQLException,java.sql.SQLException,java.lang.Object[]}, size=648, count=3}
{org.apache.ignite.internal.managers.failover.GridFailoverManager,java.util.IdentityHashMap,java.lang.Object[]}, size=642, count=1}
{org.apache.ignite.internal.managers.loadbalancer.GridLoadBalancerManager,java.util.IdentityHashMap,java.lang.Object[]}, size=642, count=1}
{org.eclipse.wst.xml.xpath2.processor.ast.XPath,java.util.ArrayList,java.lang.Object[]}, size=640, count=4}
{org.eclipse.wst.xml.xpath2.processor.internal.ast.ParExpr,java.util.ArrayList,java.lang.Object[]}, size=640, count=4}
{net.sf.saxon.trans.KeyDefinitionSet,java.util.ArrayList,java.lang.Object[]}, size=635, count=5}
{net.sf.saxon.trans.KeyDefinitionSet,java.util.ArrayList,java.lang.Object[]}, size=635, count=5}
{net.sf.saxon.trans.KeyDefinitionSet,java.util.ArrayList,java.lang.Object[]}, size=635, count=5}
{ch.qos.logback.core.rolling.helper.DateTokenConverter,java.util.ArrayList,java.lang.Object[]}, size=630, count=3}
{ch.qos.logback.core.rolling.helper.DateTokenConverter,java.util.ArrayList,java.lang.Object[]}, size=630, count=3}
{ch.qos.logback.core.rolling.helper.DateTokenConverter,java.util.ArrayList,java.lang.Object[]}, size=630, count=3}
{com.sun.org.apache.xerces.internal.impl.XMLEntityScanner,java.util.ArrayList,java.lang.Object[]}, size=628, count=4}
{javax.management.openmbean.CompositeType,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=628, count=4}
{sun.security.provider.PolicyFile$PolicyEntry,java.util.ArrayList,java.lang.Object[]}, size=624, count=3}
{org.apache.kafka.common.metrics.Metrics,java.util.ArrayList,java.lang.Object[]}, size=624, count=3}
{org.springframework.expression.spel.support.StandardEvaluationContext,java.util.ArrayList,java.lang.Object[]}, size=616, count=2}
{okhttp3.Dispatcher,java.util.ArrayDeque,java.lang.Object[]}, size=616, count=1}
{com.sun.org.apache.xerces.internal.parsers.XIncludeAwareParserConfiguration,com.sun.org.apache.xerces.internal.impl.XMLEntityManager,java.lang.Object[]}, size=613, count=1}
{net.sf.saxon.serialize.XMLEmitter,java.util.Stack,java.lang.Object[]}, size=610, count=2}
{org.apache.ignite.lang.IgniteBiTuple,java.util.ArrayList,java.lang.Object[]}, size=608, count=2}
{org.apache.xerces.impl.xs.traversers.DOMOverrideImpl,java.util.ArrayList,java.lang.Object[]}, size=605, count=5}
{org.apache.xerces.impl.xs.traversers.DOMOverrideImpl,java.util.ArrayList,java.lang.Object[]}, size=605, count=5}
{org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.IdentityHashMap,java.lang.Object[]}, size=592, count=1}
{org.apache.kafka.clients.Metadata,java.util.ArrayList,java.lang.Object[]}, size=589, count=3}
{org.apache.ignite.internal.IgniteKernal$2,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=584, count=1}
{sun.rmi.transport.tcp.TCPChannel,java.util.ArrayList,java.lang.Object[]}, size=580, count=4}
{org.apache.logging.log4j.core.LoggerContext,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=576, count=4}
{org.apache.ignite.internal.processors.rest.GridRestProcessor,java.util.EnumMap,java.lang.Object[]}, size=564, count=1}
{com.sun.org.apache.xerces.internal.impl.xs.SchemaGrammar,java.util.Vector,java.lang.Object[]}, size=562, count=1}
{org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=560, count=4}
{org.apache.curator.framework.imps.NamespaceFacade,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=538, count=2}
{org.apache.ignite.internal.StripedExecutorMXBeanAdapter,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=536, count=1}
{net.sf.saxon.serialize.XMLEmitter,java.util.Stack,java.lang.Object[]}, size=530, count=2}
{net.sf.saxon.serialize.MessageEmitter,java.util.Stack,java.lang.Object[]}, size=530, count=2}
{net.sf.saxon.serialize.XMLEmitter,java.util.Stack,java.lang.Object[]}, size=530, count=2}
{net.sf.saxon.serialize.MessageEmitter,java.util.Stack,java.lang.Object[]}, size=530, count=2}
{net.sf.saxon.serialize.MessageEmitter,java.util.Stack,java.lang.Object[]}, size=530, count=2}
{java.lang.NumberFormatException,java.lang.NumberFormatException,java.lang.Object[]}, size=528, count=3}
{SomeType73,java.util.ArrayList,java.lang.Object[]}, size=528, count=3}
{net.sf.saxon.style.XSLForEach,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=526, count=2}
{net.sf.saxon.style.XSLForEach,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=526, count=2}
{net.sf.saxon.style.XSLForEach,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=526, count=2}
{org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=519, count=1}
{net.sf.saxon.style.PackageVersion,java.util.ArrayList,java.lang.Object[]}, size=504, count=3}
{net.sf.saxon.style.PackageVersion,java.util.ArrayList,java.lang.Object[]}, size=504, count=3}
{SomeType74,java.util.ArrayList,java.lang.Object[]}, size=504, count=3}
{net.sf.saxon.style.PackageVersion,java.util.ArrayList,java.lang.Object[]}, size=504, count=3}
{SomeType67,java.util.ArrayList,java.lang.Object[]}, size=502, count=2}
{com.sun.org.apache.xerces.internal.impl.validation.ValidationManager,java.util.Vector,java.lang.Object[]}, size=498, count=3}
{org.eclipse.jetty.server.ServerConnector,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=495, count=1}
{com.sun.org.apache.xerces.internal.impl.xs.opti.SchemaParsingConfig,com.sun.org.apache.xerces.internal.impl.XMLEntityManager,java.lang.Object[]}, size=492, count=1}
{org.apache.ignite.internal.managers.deployment.GridDeploymentClassLoader,java.util.Vector,java.lang.Object[]}, size=491, count=1}
{org.apache.curator.framework.imps.CuratorFrameworkImpl,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=490, count=2}
{org.apache.xerces.impl.xs.traversers.DOMOverrideImpl,java.util.ArrayList,java.lang.Object[]}, size=484, count=4}
{javax.management.openmbean.OpenMBeanOperationInfoSupport,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=483, count=3}
{it.sauronsoftware.cron4j.Scheduler,java.util.ArrayList,java.lang.Object[]}, size=482, count=1}
{org.codehaus.jackson.map.introspect.AnnotatedClass,java.util.ArrayList,java.lang.Object[]}, size=472, count=1}
{SomeType70,java.util.ArrayList,java.lang.Object[]}, size=470, count=2}
{org.apache.kafka.common.requests.RequestSend,org.apache.kafka.common.protocol.types.Struct,java.lang.Object[]}, size=459, count=3}
{com.mchange.v2.resourcepool.BasicResourcePool,org.postgresql.util.PSQLException,java.lang.Object[]}, size=453, count=1}
{org.apache.kafka.clients.producer.internals.BufferPool,java.util.ArrayDeque,java.lang.Object[]}, size=452, count=1}
{org.apache.kafka.clients.producer.internals.BufferPool,java.util.ArrayDeque,java.lang.Object[]}, size=452, count=1}
{org.apache.kafka.clients.producer.internals.BufferPool,java.util.ArrayDeque,java.lang.Object[]}, size=452, count=1}
{net.sf.saxon.event.PipelineConfiguration,java.util.Stack,java.lang.Object[]}, size=450, count=2}
{net.sf.saxon.event.PipelineConfiguration,java.util.Stack,java.lang.Object[]}, size=450, count=2}
{net.sf.saxon.event.PipelineConfiguration,java.util.Stack,java.lang.Object[]}, size=450, count=2}
{org.apache.ignite.internal.processors.cache.distributed.near.GridNearLockResponse,java.util.ArrayList,java.lang.Object[]}, size=446, count=2}
{net.sf.saxon.serialize.XMLIndenter,java.util.ArrayList,java.lang.Object[]}, size=442, count=2}
{SomeType75,java.util.Vector,java.lang.Object[]}, size=442, count=1}
{net.sf.saxon.serialize.XMLIndenter,java.util.ArrayList,java.lang.Object[]}, size=442, count=2}
{net.sf.saxon.serialize.XMLIndenter,java.util.ArrayList,java.lang.Object[]}, size=442, count=2}
{com.google.common.collect.Multimaps$CustomSetMultimap,java.util.EnumMap,java.lang.Object[]}, size=432, count=2}
{org.apache.kafka.common.requests.FetchRequest,org.apache.kafka.common.protocol.types.Struct,java.lang.Object[]}, size=432, count=3}
{sun.security.ssl.ProtocolList,java.util.ArrayList,java.lang.Object[]}, size=432, count=3}
{com.google.common.collect.AbstractMapBasedMultimap$AsMap,java.util.EnumMap,java.lang.Object[]}, size=424, count=2}
{SomeType76,java.util.ArrayList,java.lang.Object[]}, size=418, count=1}
{SomeType77,java.util.ArrayList,java.lang.Object[]}, size=416, count=2}
{SomeType77,java.util.ArrayList,java.lang.Object[]}, size=416, count=2}
{org.apache.ignite.spi.discovery.tcp.ServerImpl,java.util.ArrayDeque,java.lang.Object[]}, size=414, count=1}
{com.sun.org.apache.xerces.internal.jaxp.validation.XMLSchemaValidatorComponentManager,com.sun.org.apache.xerces.internal.impl.XMLEntityManager,java.lang.Object[]}, size=412, count=1}
{org.apache.xerces.util.ParserConfigurationSettings,java.util.ArrayList,java.lang.Object[]}, size=408, count=1}
{org.apache.xerces.util.ParserConfigurationSettings,java.util.ArrayList,java.lang.Object[]}, size=408, count=1}
{org.apache.xerces.util.ParserConfigurationSettings,java.util.ArrayList,java.lang.Object[]}, size=408, count=1}
{oracle.jrockit.jfr.events.JavaProducerDescriptor,java.util.ArrayList,java.lang.Object[]}, size=408, count=2}
{javax.management.remote.rmi.RMIConnectorServer,java.util.ArrayList,java.lang.Object[]}, size=408, count=2}
{org.eclipse.jetty.util.thread.QueuedThreadPool,org.eclipse.jetty.util.BlockingArrayQueue,java.lang.Object[]}, size=406, count=1}
{org.apache.ignite.internal.processors.cache.GridCacheDeploymentManager$CacheClassLoader,java.util.Vector,java.lang.Object[]}, size=401, count=1}
{org.apache.kafka.common.internals.ClusterResourceListeners,java.util.ArrayList,java.lang.Object[]}, size=400, count=5}
{com.sun.xml.bind.v2.runtime.IllegalAnnotationsException$Builder,java.util.ArrayList,java.lang.Object[]}, size=400, count=5}
{javax.management.NotificationBroadcasterSupport,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=400, count=4}
{com.sun.xml.bind.v2.runtime.IllegalAnnotationsException$Builder,java.util.ArrayList,java.lang.Object[]}, size=400, count=5}
{com.sun.xml.bind.v2.runtime.IllegalAnnotationsException$Builder,java.util.ArrayList,java.lang.Object[]}, size=400, count=5}
{org.eclipse.jetty.server.Server,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=396, count=1}
{sun.reflect.misc.MethodUtil,java.util.Vector,java.lang.Object[]}, size=394, count=1}
{SomeType78,java.util.Vector,java.lang.Object[]}, size=393, count=1}
{SomeType79,java.util.Vector,java.lang.Object[]}, size=393, count=1}
{javax.management.remote.rmi.RMIJRMPServerImpl,java.util.ArrayList,java.lang.Object[]}, size=392, count=2}
{java.util.ResourceBundle$RBClassLoader,java.util.Vector,java.lang.Object[]}, size=385, count=1}
{sun.nio.cs.StandardCharsets,sun.nio.cs.StandardCharsets$Cache,java.lang.Object[]}, size=384, count=1}
{org.apache.kafka.common.requests.RequestHeader,org.apache.kafka.common.protocol.types.Struct,java.lang.Object[]}, size=384, count=3}
{org.apache.zookeeper.client.StaticHostProvider,java.util.ArrayList,java.lang.Object[]}, size=384, count=3}
{com.sun.org.apache.xerces.internal.parsers.DOMParser,java.util.Stack,java.lang.Object[]}, size=379, count=1}
{com.google.common.cache.LocalCache$StrongValueReference,com.google.common.collect.RegularImmutableSet,java.lang.Object[]}, size=376, count=2}
{org.springframework.beans.PropertyValue,org.springframework.beans.factory.support.ManagedList,java.lang.Object[]}, size=374, count=2}
{org.apache.curator.framework.state.ConnectionStateManager,java.util.concurrent.ArrayBlockingQueue,java.lang.Object[]}, size=372, count=1}
{org.apache.curator.framework.state.ConnectionStateManager,java.util.concurrent.ArrayBlockingQueue,java.lang.Object[]}, size=372, count=1}
{org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=370, count=1}
{SomeType80,java.util.ArrayList,java.lang.Object[]}, size=368, count=2}
{java.util.concurrent.ConcurrentHashMap$Node,org.apache.logging.log4j.core.util.datetime.FormatCache$MultipartKey,java.lang.Object[]}, size=360, count=3}
{sun.security.x509.PolicyMappingsExtension,java.util.ArrayList,java.lang.Object[]}, size=354, count=2}
{sun.security.x509.AuthorityInfoAccessExtension,java.util.ArrayList,java.lang.Object[]}, size=354, count=2}
{SomeType136,java.util.ArrayList,java.lang.Object[]}, size=352, count=1}
{SomeType138,java.util.ArrayList,java.lang.Object[]}, size=352, count=1}
{org.springframework.beans.factory.config.ConstructorArgumentValues$ValueHolder,org.springframework.beans.factory.support.ManagedList,java.lang.Object[]}, size=340, count=2}
{org.springframework.beans.factory.config.ConstructorArgumentValues$ValueHolder,org.springframework.beans.factory.support.ManagedList,java.lang.Object[]}, size=340, count=2}
{SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{javax.management.remote.rmi.RMIJRMPServerImpl$ExportedWrapper,java.util.ArrayList,java.lang.Object[]}, size=336, count=2}
{SomeType74,java.util.ArrayList,java.lang.Object[]}, size=336, count=2}
{com.mchange.v2.cfg.MConfig$PathsKey,java.util.ArrayList,java.lang.Object[]}, size=336, count=2}
{sun.nio.cs.StandardCharsets,sun.nio.cs.StandardCharsets$Classes,java.lang.Object[]}, size=336, count=1}
{com.mchange.v2.cfg.MConfig$PathsKey,java.util.ArrayList,java.lang.Object[]}, size=336, count=2}
{org.apache.ignite.internal.managers.eventstorage.GridEventStorageManager$Listeners,java.util.ArrayList,java.lang.Object[]}, size=336, count=2}
{com.mchange.v2.cfg.MConfig$PathsKey,java.util.ArrayList,java.lang.Object[]}, size=336, count=2}
{ch.qos.logback.core.joran.spi.ConfigurationWatchList,java.util.ArrayList,java.lang.Object[]}, size=332, count=1}
{ch.qos.logback.core.joran.spi.ConfigurationWatchList,java.util.ArrayList,java.lang.Object[]}, size=332, count=1}
{ch.qos.logback.core.joran.spi.ConfigurationWatchList,java.util.ArrayList,java.lang.Object[]}, size=332, count=1}
{com.sun.org.apache.xerces.internal.parsers.XIncludeAwareParserConfiguration,java.util.ArrayList,java.lang.Object[]}, size=328, count=1}
{java.util.concurrent.ConcurrentHashMap$Node,org.springframework.context.annotation.ConfigurationClassBeanDefinitionReader$ConfigurationClassBeanDefinition,java.lang.Object[]}, size=327, count=1}
{org.apache.kafka.common.internals.ClusterResourceListeners,java.util.ArrayList,java.lang.Object[]}, size=320, count=4}
{org.apache.ignite.internal.processors.cache.CacheObjectsReleaseFuture,java.util.ArrayList,java.lang.Object[]}, size=306, count=2}
{org.apache.ignite.internal.processors.rest.protocols.http.jetty.GridJettyRestHandler,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=305, count=1}
{SomeType81,java.util.ArrayList,java.lang.Object[]}, size=301, count=1}
{SomeType81,java.util.ArrayList,java.lang.Object[]}, size=301, count=1}
{com.mchange.v2.c3p0.impl.NewPooledConnection,java.sql.SQLException,java.lang.Object[]}, size=300, count=1}
{SomeType82,java.util.ArrayList,java.lang.Object[]}, size=291, count=3}
{org.apache.ignite.internal.processors.cache.GridCachePartitionExchangeManager,java.util.ArrayList,java.lang.Object[]}, size=288, count=1}
{com.sun.org.apache.xerces.internal.impl.dtd.XMLDTDProcessor,java.util.ArrayList,java.lang.Object[]}, size=287, count=1}
{org.apache.ignite.internal.processors.rest.protocols.tcp.redis.GridRedisNioListener,java.util.EnumMap,java.lang.Object[]}, size=284, count=1}
{org.eclipse.jetty.server.HttpConnectionFactory,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=277, count=1}
{org.apache.ignite.internal.client.marshaller.optimized.GridClientOptimizedMarshaller$ClientMarshallerContext,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=274, count=2}
{org.h2.message.TraceSystem,java.util.concurrent.atomic.AtomicReferenceArray,java.lang.Object[]}, size=270, count=1}
{org.apache.curator.framework.imps.NamespaceFacade,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=269, count=1}
{org.apache.curator.framework.imps.NamespaceFacade,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=269, count=1}
{javax.management.openmbean.SimpleType,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=266, count=2}
{net.sf.saxon.serialize.XMLIndenter,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=264, count=2}
{net.sf.saxon.serialize.XMLIndenter,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=264, count=2}
{java.util.Collections$SynchronizedMap,java.util.IdentityHashMap,java.lang.Object[]}, size=264, count=1}
{SomeType83,java.util.ArrayList,java.lang.Object[]}, size=264, count=1}
{SomeType147,java.util.ArrayList,java.lang.Object[]}, size=264, count=1}
{net.sf.saxon.serialize.XMLIndenter,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=264, count=2}
{org.apache.logging.log4j.core.config.xml.XmlConfiguration,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=256, count=4}
{org.apache.zookeeper.client.StaticHostProvider,java.util.ArrayList,java.lang.Object[]}, size=256, count=2}
{org.apache.ignite.marshaller.jdk.JdkMarshallerObjectInputStream,java.io.ObjectInputStream$HandleTable,java.lang.Object[]}, size=256, count=1}
{org.apache.curator.framework.imps.CuratorFrameworkImpl,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=245, count=1}
{org.apache.curator.framework.imps.CuratorFrameworkImpl,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=245, count=1}
{com.sun.xml.internal.bind.v2.model.impl.RuntimeEnumLeafInfoImpl,java.util.EnumMap,java.lang.Object[]}, size=245, count=1}
{SomeType84,java.util.ArrayList,java.lang.Object[]}, size=244, count=1}
{org.apache.kafka.common.internals.ClusterResourceListeners,java.util.ArrayList,java.lang.Object[]}, size=240, count=3}
{org.eclipse.jetty.http.HttpGenerator$1,java.util.ArrayList,java.lang.Object[]}, size=240, count=1}
{org.apache.logging.log4j.core.util.DefaultShutdownCallbackRegistry,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=240, count=2}
{SomeType85,java.util.ArrayList,java.lang.Object[]}, size=240, count=1}
{com.google.common.util.concurrent.ServiceManager,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=240, count=2}
{okhttp3.ConnectionPool,java.util.ArrayDeque,java.lang.Object[]}, size=237, count=1}
{org.apache.ignite.internal.processors.query.h2.opt.GridH2MetaTable,java.util.ArrayList,java.lang.Object[]}, size=236, count=1}
{org.eclipse.jetty.util.thread.ShutdownThread,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=236, count=1}
{org.zeromq.ZContext,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=234, count=2}
{org.apache.logging.log4j.core.config.NullConfiguration,java.util.ArrayList,java.lang.Object[]}, size=233, count=1}
{org.apache.logging.log4j.core.config.DefaultConfiguration,java.util.ArrayList,java.lang.Object[]}, size=233, count=1}
{ch.qos.logback.core.joran.spi.Interpreter,java.util.Stack,java.lang.Object[]}, size=228, count=1}
{ch.qos.logback.core.joran.spi.Interpreter,java.util.Stack,java.lang.Object[]}, size=228, count=1}
{ch.qos.logback.core.joran.spi.Interpreter,java.util.Stack,java.lang.Object[]}, size=228, count=1}
{org.apache.ignite.internal.pagemem.wal.record.DataRecord,java.util.ArrayList,java.lang.Object[]}, size=224, count=2}
{com.google.common.collect.Multimaps$CustomSetMultimap,java.util.EnumMap,java.lang.Object[]}, size=216, count=1}
{it.sauronsoftware.cron4j.MemoryTaskCollector,java.util.ArrayList,java.lang.Object[]}, size=212, count=1}
{com.sun.org.apache.xalan.internal.xsltc.compiler.AbsolutePathPattern,java.util.ArrayList,java.lang.Object[]}, size=212, count=1}
{com.google.common.collect.AbstractMapBasedMultimap$AsMap,java.util.EnumMap,java.lang.Object[]}, size=212, count=1}
{com.google.common.cache.LocalCache$StrongValueReference,com.google.common.collect.RegularImmutableSet,java.lang.Object[]}, size=208, count=1}
{com.sun.org.apache.xerces.internal.impl.xs.XMLSchemaValidator$ValueStoreCache,java.util.Stack,java.lang.Object[]}, size=204, count=1}
{SomeType86,java.util.ArrayList,java.lang.Object[]}, size=199, count=1}
{SomeType82,java.util.ArrayList,java.lang.Object[]}, size=194, count=2}
{ch.qos.logback.core.joran.action.NestedBasicPropertyIA,java.util.Stack,java.lang.Object[]}, size=192, count=1}
{com.sun.org.apache.xerces.internal.impl.xs.XMLSchemaValidator$XSIErrorReporter,java.util.Vector,java.lang.Object[]}, size=192, count=1}
{ch.qos.logback.core.joran.action.NestedComplexPropertyIA,java.util.Stack,java.lang.Object[]}, size=192, count=1}
{ch.qos.logback.core.joran.action.NestedBasicPropertyIA,java.util.Stack,java.lang.Object[]}, size=192, count=1}
{ch.qos.logback.core.joran.action.NestedComplexPropertyIA,java.util.Stack,java.lang.Object[]}, size=192, count=1}
{java.util.concurrent.ConcurrentHashMap$Node,org.springframework.context.annotation.ConfigurationClassParser$ImportStack,java.lang.Object[]}, size=192, count=1}
{ch.qos.logback.core.joran.action.NestedComplexPropertyIA,java.util.Stack,java.lang.Object[]}, size=192, count=1}
{org.apache.ignite.internal.processors.service.GridServiceProcessor,java.util.ArrayList,java.lang.Object[]}, size=192, count=1}
{ch.qos.logback.core.joran.action.NestedBasicPropertyIA,java.util.Stack,java.lang.Object[]}, size=192, count=1}
{sun.security.provider.PolicyFile$PolicyInfo,java.util.ArrayList,java.lang.Object[]}, size=192, count=1}
{ch.qos.logback.core.joran.conditional.ThenAction,java.util.Stack,java.lang.Object[]}, size=184, count=1}
{ch.qos.logback.core.joran.conditional.ElseAction,java.util.Stack,java.lang.Object[]}, size=184, count=1}
{ch.qos.logback.core.joran.conditional.IfAction,java.util.Stack,java.lang.Object[]}, size=184, count=1}
{ch.qos.logback.core.joran.conditional.ThenAction,java.util.Stack,java.lang.Object[]}, size=184, count=1}
{ch.qos.logback.core.joran.conditional.ElseAction,java.util.Stack,java.lang.Object[]}, size=184, count=1}
{ch.qos.logback.core.joran.conditional.IfAction,java.util.Stack,java.lang.Object[]}, size=184, count=1}
{ch.qos.logback.core.joran.conditional.IfAction,java.util.Stack,java.lang.Object[]}, size=184, count=1}
{ch.qos.logback.core.joran.conditional.ElseAction,java.util.Stack,java.lang.Object[]}, size=184, count=1}
{ch.qos.logback.core.joran.conditional.ThenAction,java.util.Stack,java.lang.Object[]}, size=184, count=1}
{com.mchange.v2.log.jdk14logging.ForwardingLogger,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=183, count=1}
{com.mchange.v2.log.jdk14logging.ForwardingLogger,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=183, count=1}
{com.mchange.v2.log.jdk14logging.ForwardingLogger,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=183, count=1}
{java.lang.Exception,java.lang.Exception,java.lang.Object[]}, size=176, count=1}
{com.sun.org.apache.xpath.internal.axes.IteratorPool,java.util.ArrayList,java.lang.Object[]}, size=176, count=2}
{org.springframework.cglib.core.ClassNameReader$EarlyExitException,org.springframework.cglib.core.ClassNameReader$EarlyExitException,java.lang.Object[]}, size=176, count=1}
{org.eclipse.jetty.server.ServerConnector$ServerConnectorManager,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=172, count=1}
{SomeType87,java.util.ArrayList,java.lang.Object[]}, size=169, count=1}
{org.apache.ignite.internal.processors.cache.ClusterCachesInfo,java.util.ArrayList,java.lang.Object[]}, size=169, count=1}
{kafka.utils.ZkUtils,java.util.ArrayList,java.lang.Object[]}, size=162, count=1}
{kafka.utils.ZkUtils,java.util.ArrayList,java.lang.Object[]}, size=162, count=1}
{java.net.SocketPermissionCollection,java.util.ArrayList,java.lang.Object[]}, size=161, count=1}
{org.springframework.util.comparator.CompoundComparator,java.util.ArrayList,java.lang.Object[]}, size=160, count=1}
{jdk.internal.instrumentation.Tracer,java.util.ArrayList,java.lang.Object[]}, size=160, count=1}
{java.util.concurrent.ConcurrentHashMap$Node,sun.util.locale.provider.LocaleResources$ResourceReference,java.lang.Object[]}, size=156, count=1}
{org.apache.ignite.internal.processors.cache.binary.BinaryMetadataTransport,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=154, count=1}
{org.eclipse.jetty.util.thread.ScheduledExecutorScheduler,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=153, count=1}
{SomeType88,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=152, count=1}
{java.lang.invoke.LambdaForm$BasicType,sun.invoke.util.Wrapper,long[]}, size=152, count=1}
{java.lang.invoke.LambdaForm$BasicType,sun.invoke.util.Wrapper,java.lang.Object[]}, size=152, count=1}
{org.postgresql.core.v3.ProtocolConnectionImpl,java.util.ArrayList,java.lang.Object[]}, size=150, count=1}
{javax.management.openmbean.TabularType,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=149, count=1}
{org.apache.log4j.Hierarchy,java.util.Vector,java.lang.Object[]}, size=146, count=1}
{SomeType89,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=144, count=1}
{sun.net.ProgressMonitor,java.util.ArrayList,java.lang.Object[]}, size=144, count=1}
{SomeType89,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=144, count=1}
{ch.qos.logback.core.joran.spi.InterpretationContext,java.util.ArrayList,java.lang.Object[]}, size=140, count=1}
{org.apache.logging.log4j.status.StatusLogger,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=140, count=1}
{ch.qos.logback.core.joran.spi.InterpretationContext,java.util.ArrayList,java.lang.Object[]}, size=140, count=1}
{ch.qos.logback.core.joran.spi.InterpretationContext,java.util.Stack,java.lang.Object[]}, size=140, count=1}
{com.sun.org.apache.xerces.internal.impl.xs.XMLSchemaValidator$ValueStoreCache,java.util.Vector,java.lang.Object[]}, size=140, count=1}
{ch.qos.logback.core.joran.spi.InterpretationContext,java.util.Stack,java.lang.Object[]}, size=140, count=1}
{ch.qos.logback.core.joran.spi.InterpretationContext,java.util.Stack,java.lang.Object[]}, size=140, count=1}
{ch.qos.logback.core.joran.spi.InterpretationContext,java.util.ArrayList,java.lang.Object[]}, size=140, count=1}
{org.apache.ignite.internal.MarshallerContextImpl,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=137, count=1}
{org.apache.logging.log4j.core.appender.AsyncAppender$AsyncThread,java.util.ArrayList,java.lang.Object[]}, size=136, count=1}
{com.sun.org.apache.xerces.internal.impl.xs.opti.SchemaParsingConfig,java.util.ArrayList,java.lang.Object[]}, size=136, count=1}
{org.apache.ignite.internal.processors.marshaller.GridMarshallerMappingProcessor,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=136, count=1}
{org.apache.ignite.internal.processors.rest.GridRestProcessor,java.util.ArrayList,java.lang.Object[]}, size=136, count=1}
{org.h2.mvstore.db.TransactionStore,java.util.BitSet,long[]}, size=134, count=1}
{org.springframework.http.converter.json.MappingJacksonHttpMessageConverter,java.util.ArrayList,java.lang.Object[]}, size=128, count=1}
{org.apache.zookeeper.client.StaticHostProvider,java.util.ArrayList,java.lang.Object[]}, size=128, count=1}
{com.google.common.util.concurrent.ServiceManager,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=120, count=1}
{SomeType90,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=120, count=1}
{org.eclipse.jetty.server.HttpConfiguration,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=116, count=1}
{javax.management.remote.rmi.RMIConnectorServer,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=112, count=2}
{oracle.jrockit.jfr.Settings$Aggregator,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=112, count=1}
{com.google.common.cache.LocalCache$StrongValueReference,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=112, count=1}
{org.springframework.aop.framework.adapter.DefaultAdvisorAdapterRegistry,java.util.ArrayList,java.lang.Object[]}, size=104, count=1}
{SomeType91,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=96, count=1}
{org.apache.kafka.common.requests.MetadataRequest,org.apache.kafka.common.protocol.types.Struct,java.lang.Object[]}, size=96, count=1}
{org.apache.kafka.common.requests.MetadataRequest,org.apache.kafka.common.protocol.types.Struct,java.lang.Object[]}, size=96, count=1}
{SomeType92,java.util.ArrayList,java.lang.Object[]}, size=96, count=1}
{org.apache.kafka.common.requests.MetadataRequest,org.apache.kafka.common.protocol.types.Struct,java.lang.Object[]}, size=96, count=1}
{SomeType91,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=96, count=1}
{org.springframework.http.converter.ByteArrayHttpMessageConverter,java.util.ArrayList,java.lang.Object[]}, size=96, count=1}
{scala.util.matching.Regex,scala.collection.mutable.WrappedArray$ofRef,java.lang.Object[]}, size=89, count=1}
{scala.util.matching.Regex,scala.collection.mutable.WrappedArray$ofRef,java.lang.Object[]}, size=89, count=1}
{scala.util.matching.Regex,scala.collection.mutable.WrappedArray$ofRef,java.lang.Object[]}, size=89, count=1}
{org.slf4j.helpers.BasicMarker,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=88, count=1}
{org.slf4j.helpers.BasicMarker,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=88, count=1}
{scala.collection.mutable.WrappedArray$,scala.collection.mutable.WrappedArray$ofRef,java.lang.Object[]}, size=81, count=1}
{scala.collection.mutable.WrappedArray$,scala.collection.mutable.WrappedArray$ofRef,java.lang.Object[]}, size=81, count=1}
{scala.collection.mutable.WrappedArray$,scala.collection.mutable.WrappedArray$ofRef,java.lang.Object[]}, size=81, count=1}
{ch.qos.logback.core.joran.spi.Interpreter,java.util.ArrayList,java.lang.Object[]}, size=80, count=1}
{com.sun.xml.internal.bind.v2.runtime.IllegalAnnotationsException$Builder,java.util.ArrayList,java.lang.Object[]}, size=80, count=1}
{it.sauronsoftware.cron4j.FileTaskCollector,java.util.ArrayList,java.lang.Object[]}, size=80, count=1}
{ch.qos.logback.core.joran.spi.Interpreter,java.util.ArrayList,java.lang.Object[]}, size=80, count=1}
{ch.qos.logback.core.joran.spi.Interpreter,java.util.ArrayList,java.lang.Object[]}, size=80, count=1}
{java.util.logging.LogManager$RootLogger,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=64, count=1}
{org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.ArrayList,java.lang.Object[]}, size=56, count=1}
{org.apache.logging.log4j.core.config.NullConfiguration,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=56, count=1}
{org.apache.logging.log4j.core.config.DefaultConfiguration,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=56, count=1}
{SomeType76,ch.qos.logback.classic.spi.TurboFilterList,java.lang.Object[]}, size=56, count=1}
{org.eclipse.jetty.util.thread.QueuedThreadPool,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=56, count=1}
{SomeType76,ch.qos.logback.classic.spi.TurboFilterList,java.lang.Object[]}, size=56, count=1}
{SomeType76,ch.qos.logback.classic.spi.TurboFilterList,java.lang.Object[]}, size=56, count=1}
Wed Nov 29 17:03:08 NOVT 2017 Next step:
{org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionMap,org.apache.ignite.internal.util.GridPartitionStateMap,java.util.BitSet,long[]}, size=15754997969, count=2142445}
{java.util.concurrent.ConcurrentSkipListMap$Node,org.apache.ignite.internal.processors.affinity.HistoryAffinityAssignment,java.util.ArrayList,java.lang.Object[]}, size=5869406336, count=17088}
{java.util.HashMap$Node,java.util.HashMap$Node,org.apache.ignite.internal.processors.cache.distributed.dht.preloader.CachePartitionFullCountersMap,long[]}, size=1783346720, count=3828}
{org.apache.ignite.internal.processors.cache.GridCacheAffinityManager,org.apache.ignite.internal.processors.affinity.GridAffinityAssignmentCache,java.util.ArrayList,java.lang.Object[]}, size=1007844558, count=5214}
{java.util.HashMap$Node,java.util.HashMap$Node,java.util.ArrayList,java.lang.Object[]}, size=370747984, count=240196}
{org.apache.ignite.internal.managers.discovery.GridDiscoveryManager$CachePredicate,org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.ArrayDeque,java.lang.Object[]}, size=343305402, count=5214}
{org.apache.ignite.internal.processors.cache.distributed.dht.GridCachePartitionedConcurrentMap,org.apache.ignite.internal.processors.cache.CacheGroupContext,java.util.ArrayList,java.lang.Object[]}, size=136096090, count=5214}
{org.apache.ignite.internal.processors.cache.GridCacheContext,org.apache.ignite.internal.processors.cache.CacheGroupContext,java.util.ArrayList,java.lang.Object[]}, size=135970954, count=5214}
{org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtLocalPartition,org.apache.ignite.internal.processors.cache.CacheGroupContext,java.util.ArrayList,java.lang.Object[]}, size=72363615, count=65883}
{org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$2,org.apache.ignite.internal.processors.cache.CacheGroupContext,java.util.ArrayList,java.lang.Object[]}, size=41964440, count=4253}
{java.lang.Package,SomeType18,java.lang.Object[]}, size=30726220, count=742}
{org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionSupplier,org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtPartitionTopologyImpl,org.apache.ignite.internal.processors.cache.distributed.dht.preloader.CachePartitionFullCountersMap,long[]}, size=27827570, count=146}
{org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPreloader,org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtPartitionTopologyImpl,org.apache.ignite.internal.processors.cache.distributed.dht.preloader.CachePartitionFullCountersMap,long[]}, size=27827570, count=146}
{org.apache.ignite.internal.processors.cache.CacheGroupContext,org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtPartitionTopologyImpl,org.apache.ignite.internal.processors.cache.distributed.dht.preloader.CachePartitionFullCountersMap,long[]}, size=27827570, count=146}
{java.util.concurrent.atomic.AtomicReference,org.apache.ignite.internal.processors.affinity.GridAffinityAssignment,java.util.ArrayList,java.lang.Object[]}, size=27825234, count=146}
{java.lang.Package,SomeType17,java.util.Vector,java.lang.Object[]}, size=27085894, count=767}
{org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtLocalPartition,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=24376710, count=65883}
{java.util.HashMap$Node,org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode,java.util.ArrayList,java.lang.Object[]}, size=24112590, count=70397}
{java.lang.Package,SomeType19,java.util.Vector,java.lang.Object[]}, size=24013160, count=580}
{org.h2.expression.ExpressionColumn,org.h2.engine.Database,org.h2.util.BitField,long[]}, size=23685054, count=8782}
{org.apache.ignite.internal.util.nio.GridSelectorNioSessionImpl,org.apache.ignite.internal.util.nio.GridNioRecoveryDescriptor,java.util.ArrayDeque,java.lang.Object[]}, size=22768530, count=173}
{org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex,org.h2.engine.Database,org.h2.util.BitField,long[]}, size=16144242, count=5986}
{org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtLocalPartition$2,org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtLocalPartition,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=15506641, count=65883}
{org.h2.schema.Schema,org.h2.engine.Database,org.h2.util.BitField,long[]}, size=14808224, count=5216}
{org.apache.ignite.internal.processors.cache.CacheGroupContext,org.apache.ignite.internal.processors.affinity.GridAffinityAssignmentCache,java.util.ArrayList,java.lang.Object[]}, size=13925538, count=146}
{org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPreloader,org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtPartitionTopologyImpl,java.util.concurrent.atomic.AtomicReferenceArray,java.lang.Object[]}, size=13922034, count=146}
{org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionSupplier,org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtPartitionTopologyImpl,java.util.concurrent.atomic.AtomicReferenceArray,java.lang.Object[]}, size=13912690, count=146}
{org.apache.ignite.internal.processors.cache.CacheGroupContext,org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtPartitionTopologyImpl,java.util.concurrent.atomic.AtomicReferenceArray,java.lang.Object[]}, size=13904368, count=146}
{SomeType,SomeType15,java.util.ArrayList,java.lang.Object[]}, size=11428286, count=3934}
{org.jsr166.ConcurrentHashMap8$Node,org.apache.ignite.internal.util.nio.GridNioRecoveryDescriptor,java.util.ArrayDeque,java.lang.Object[]}, size=11373020, count=173}
{org.apache.ignite.internal.processors.query.property.QueryBinaryProperty,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=9632670, count=5870}
{org.apache.ignite.internal.processors.cache.IgniteCacheOffheapManagerImpl$CacheDataRowStore,org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$1,java.util.concurrent.atomic.AtomicReferenceArray,java.lang.Object[]}, size=9547985, count=4253}
{org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore,org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$1,java.util.concurrent.atomic.AtomicReferenceArray,java.lang.Object[]}, size=9547985, count=4253}
{org.apache.ignite.internal.processors.cache.persistence.freelist.FreeListImpl$RemoveRowHandler,org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$1,java.util.concurrent.atomic.AtomicReferenceArray,java.lang.Object[]}, size=9547985, count=4253}
{org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$2,org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$1,java.util.concurrent.atomic.AtomicReferenceArray,java.lang.Object[]}, size=9547985, count=4253}
{org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$1,org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$1,java.util.concurrent.atomic.AtomicReferenceArray,java.lang.Object[]}, size=9547985, count=4253}
{org.apache.ignite.internal.processors.cache.persistence.freelist.FreeListImpl$WriteRowHandler,org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$1,java.util.concurrent.atomic.AtomicReferenceArray,java.lang.Object[]}, size=9547985, count=4253}
{org.apache.ignite.internal.processors.cache.persistence.freelist.PagesList$CutTail,org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$1,java.util.concurrent.atomic.AtomicReferenceArray,java.lang.Object[]}, size=9547985, count=4253}
{org.apache.ignite.internal.processors.cache.persistence.freelist.FreeListImpl$UpdateRowHandler,org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$1,java.util.concurrent.atomic.AtomicReferenceArray,java.lang.Object[]}, size=9547985, count=4253}
{org.apache.ignite.thread.IgniteThread,SomeType19,java.util.Vector,java.lang.Object[]}, size=9128680, count=220}
{SomeType149,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=8533824, count=5214}
{org.apache.ignite.internal.managers.discovery.DiscoCache,java.util.Collections$UnmodifiableRandomAccessList,java.util.ArrayList,java.lang.Object[]}, size=8424240, count=510}
{org.apache.ignite.internal.processors.plugin.CachePluginManager,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=8403972, count=5214}
{org.apache.ignite.internal.processors.cache.binary.CacheObjectBinaryContext,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=8373684, count=5214}
{org.apache.ignite.internal.processors.cache.query.continuous.CacheContinuousQueryManager$BackupCleaner,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=8311116, count=5214}
{org.apache.ignite.internal.GridCachePluginContext,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=8259692, count=5214}
{SomeType,SomeType11,java.util.ArrayList,java.lang.Object[]}, size=8210140, count=5492}
{org.apache.ignite.internal.processors.cache.GridCacheContext,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=8144268, count=5214}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$RemoveFromLeaf,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex$1,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=7650108, count=5986}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$Search,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex$1,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=7650108, count=5986}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$LockTail,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex$1,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=7650108, count=5986}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$AskNeighbor,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex$1,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=7650108, count=5986}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$LockBackAndTail,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex$1,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=7650108, count=5986}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$LockBackAndRmvFromLeaf,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex$1,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=7650108, count=5986}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$LockTailForward,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex$1,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=7650108, count=5986}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$Insert,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex$1,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=7650108, count=5986}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$Replace,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex$1,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=7650108, count=5986}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$AskNeighbor,org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$2,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=7587352, count=4253}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$LockBackAndTail,org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$2,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=7587352, count=4253}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$LockBackAndRmvFromLeaf,org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$2,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=7587352, count=4253}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$RemoveFromLeaf,org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$2,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=7587352, count=4253}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$Insert,org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$2,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=7587352, count=4253}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$LockTailForward,org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$2,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=7587352, count=4253}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$Replace,org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$2,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=7587352, count=4253}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$LockTail,org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$2,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=7587352, count=4253}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$Search,org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$2,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=7587352, count=4253}
{org.h2.expression.Comparison,org.h2.engine.Database,org.h2.util.BitField,long[]}, size=7406142, count=2701}
{SomeType,SomeType13,java.util.ArrayList,java.lang.Object[]}, size=6619427, count=4631}
{org.h2.table.Column,org.apache.ignite.internal.processors.query.h2.opt.GridH2Table,java.util.ArrayList,java.lang.Object[]}, size=6170790, count=10694}
{org.apache.ignite.internal.processors.cache.CacheMetricsImpl,org.apache.ignite.internal.processors.cache.ratemetrics.HitRateMetrics,java.util.concurrent.atomic.AtomicLongArray,long[]}, size=6048240, count=5214}
{java.util.HashMap$Node,SomeType11,java.util.ArrayList,java.lang.Object[]}, size=5836345, count=4345}
{java.security.ProtectionDomain,SomeType17,java.util.Vector,java.lang.Object[]}, size=5219944, count=146}
{SomeType3,SomeType13,java.util.ArrayList,java.lang.Object[]}, size=5132473, count=1488}
{SomeType35,SomeType13,java.util.ArrayList,java.lang.Object[]}, size=5023135, count=1494}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$LockTailForward,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex$1,java.util.ArrayList,java.lang.Object[]}, size=4940244, count=5986}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$Insert,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex$1,java.util.ArrayList,java.lang.Object[]}, size=4940244, count=5986}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$AskNeighbor,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex$1,java.util.ArrayList,java.lang.Object[]}, size=4940244, count=5986}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$Replace,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex$1,java.util.ArrayList,java.lang.Object[]}, size=4940244, count=5986}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$Search,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex$1,java.util.ArrayList,java.lang.Object[]}, size=4940244, count=5986}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$LockBackAndRmvFromLeaf,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex$1,java.util.ArrayList,java.lang.Object[]}, size=4940244, count=5986}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$LockBackAndTail,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex$1,java.util.ArrayList,java.lang.Object[]}, size=4940244, count=5986}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$RemoveFromLeaf,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex$1,java.util.ArrayList,java.lang.Object[]}, size=4940244, count=5986}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$LockTail,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex$1,java.util.ArrayList,java.lang.Object[]}, size=4940244, count=5986}
{org.apache.ignite.internal.processors.query.h2.opt.GridH2Table,org.h2.engine.Database,org.h2.util.BitField,long[]}, size=4746816, count=1608}
{sun.reflect.DelegatingClassLoader,SomeType19,java.util.Vector,java.lang.Object[]}, size=4502354, count=109}
{org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex$1,org.apache.ignite.internal.processors.cache.persistence.tree.reuse.ReuseListImpl,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=4357808, count=5986}
{org.apache.ignite.internal.processors.query.h2.database.H2PkHashIndex,org.h2.engine.Database,org.h2.util.BitField,long[]}, size=4336776, count=1608}
{org.apache.ignite.internal.processors.cache.IgniteCacheOffheapManagerImpl$CacheDataStoreImpl,org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$2,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=4082880, count=4253}
{org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex$1,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,org.apache.ignite.internal.mem.unsafe.UnsafeMemoryProvider,long[]}, size=4064494, count=5986}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$1,org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$2,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=3831953, count=4253}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$InitRoot,org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$2,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=3827700, count=4253}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$AddRoot,org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$2,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=3827700, count=4253}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$CutRoot,org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$2,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=3827700, count=4253}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$InitRoot,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex$1,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=3825054, count=5986}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$1,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex$1,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=3825054, count=5986}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$AddRoot,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex$1,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=3825054, count=5986}
{org.apache.ignite.internal.processors.query.h2.database.H2Tree$1,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex$1,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=3825054, count=5986}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$CutRoot,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex$1,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=3825054, count=5986}
{org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$1,org.apache.ignite.internal.processors.cache.persistence.MemoryMetricsImpl,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=3751146, count=4253}
{org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex,org.apache.ignite.internal.processors.query.h2.opt.GridH2Table,java.util.ArrayList,java.lang.Object[]}, size=3603546, count=5986}
{SomeType,SomeType14,java.util.ArrayList,java.lang.Object[]}, size=3549188, count=2076}
{SomeType3,SomeType11,java.util.ArrayList,java.lang.Object[]}, size=3461516, count=1189}
{org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex$1,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex,java.util.ArrayList,java.lang.Object[]}, size=3415910, count=5986}
{SomeType35,SomeType11,java.util.ArrayList,java.lang.Object[]}, size=3369291, count=1189}
{org.apache.ignite.internal.processors.cache.GridCacheContext,org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode,java.util.ArrayList,java.lang.Object[]}, size=3368244, count=5214}
{org.apache.ignite.internal.processors.cache.IgniteCacheOffheapManagerImpl$CacheDataRowStore,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,org.apache.ignite.internal.mem.unsafe.UnsafeMemoryProvider,long[]}, size=3142967, count=4253}
{org.apache.ignite.internal.managers.discovery.GridDiscoveryManager$CachePredicate,org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.IdentityHashMap,java.lang.Object[]}, size=3086688, count=5214}
{java.security.ProtectionDomain,SomeType18,java.lang.Object[]}, size=3061528, count=74}
{org.h2.expression.ExpressionColumn,org.h2.table.TableFilter,java.util.ArrayList,java.lang.Object[]}, size=3017954, count=8782}
{org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore,org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$1,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=3011124, count=4253}
{org.springframework.core.io.ClassPathResource,SomeType18,java.lang.Object[]}, size=2977488, count=72}
{org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$2,org.apache.ignite.internal.processors.cache.IgniteCacheOffheapManagerImpl$CacheDataRowStore,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=2972847, count=4253}
{org.apache.ignite.internal.processors.cache.IgniteCacheOffheapManagerImpl$CacheDataStoreImpl,org.apache.ignite.internal.processors.cache.IgniteCacheOffheapManagerImpl$CacheDataRowStore,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=2972847, count=4253}
{org.springframework.core.type.classreading.AnnotationMetadataReadingVisitor,SomeType18,java.lang.Object[]}, size=2941246, count=71}
{org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$1,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,org.apache.ignite.internal.mem.unsafe.UnsafeMemoryProvider,long[]}, size=2887787, count=4253}
{org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$2,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,org.apache.ignite.internal.mem.unsafe.UnsafeMemoryProvider,long[]}, size=2887787, count=4253}
{org.apache.ignite.internal.processors.cache.persistence.freelist.PagesList$CutTail,org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$1,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=2819739, count=4253}
{org.apache.ignite.internal.processors.cache.persistence.freelist.FreeListImpl$WriteRowHandler,org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$1,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=2819739, count=4253}
{org.apache.ignite.internal.processors.cache.persistence.freelist.FreeListImpl$UpdateRowHandler,org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$1,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=2819739, count=4253}
{org.apache.ignite.internal.processors.cache.persistence.freelist.FreeListImpl$RemoveRowHandler,org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$1,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=2819739, count=4253}
{org.apache.ignite.internal.processors.cache.IgniteCacheOffheapManagerImpl$CacheDataRowStore,org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$1,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=2717667, count=4253}
{org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$1,org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$1,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=2717667, count=4253}
{org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$2,org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager$GridCacheDataStore$1,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=2717667, count=4253}
{com.sun.jmx.mbeanserver.StandardMBeanSupport,javax.management.MBeanInfo,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=2646326, count=13166}
{SomeType,SomeType16,java.util.ArrayList,java.lang.Object[]}, size=2589537, count=1469}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$1,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex$1,java.util.ArrayList,java.lang.Object[]}, size=2523996, count=5986}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$AddRoot,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex$1,java.util.ArrayList,java.lang.Object[]}, size=2518010, count=5986}
{org.apache.ignite.internal.processors.query.h2.database.H2Tree$1,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex$1,java.util.ArrayList,java.lang.Object[]}, size=2518010, count=5986}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$CutRoot,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex$1,java.util.ArrayList,java.lang.Object[]}, size=2518010, count=5986}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$InitRoot,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex$1,java.util.ArrayList,java.lang.Object[]}, size=2518010, count=5986}
{java.security.ProtectionDomain,SomeType19,java.util.Vector,java.lang.Object[]}, size=2275020, count=55}
{org.springframework.core.type.classreading.AnnotationMetadataReadingVisitor,SomeType18,java.lang.Object[]}, size=2237004, count=54}
{org.springframework.core.io.ClassPathResource,SomeType18,java.lang.Object[]}, size=2233116, count=54}
{org.apache.ignite.internal.processors.query.h2.opt.GridH2IndexBase$2,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex,java.util.ArrayList,java.lang.Object[]}, size=2021172, count=5986}
{org.apache.ignite.internal.mxbean.IgniteStandardMXBean,javax.management.MBeanInfo,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=2016078, count=10446}
{SomeType10,SomeType13,java.util.ArrayList,java.lang.Object[]}, size=2010661, count=564}
{org.apache.ignite.internal.processors.query.h2.opt.GridH2IndexBase$1,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex,java.util.ArrayList,java.lang.Object[]}, size=1973284, count=5986}
{SomeType47,SomeType13,java.util.ArrayList,java.lang.Object[]}, size=1969792, count=567}
{sun.reflect.DelegatingClassLoader,SomeType18,java.lang.Object[]}, size=1941758, count=47}
{SomeType10,SomeType11,java.util.ArrayList,java.lang.Object[]}, size=1941407, count=544}
{org.apache.ignite.internal.processors.cache.GridCacheContext,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=1929180, count=5214}
{SomeType47,SomeType11,java.util.ArrayList,java.lang.Object[]}, size=1901636, count=544}
{org.h2.table.TableFilter,org.h2.command.dml.Select,java.util.ArrayList,java.lang.Object[]}, size=1893838, count=2507}
{java.lang.Thread,SomeType18,java.lang.Object[]}, size=1825340, count=44}
{java.util.HashMap$Node,SomeType14,java.util.ArrayList,java.lang.Object[]}, size=1771340, count=1260}
{org.h2.engine.UndoLog,org.h2.engine.Database,org.h2.util.BitField,long[]}, size=1758950, count=635}
{org.h2.engine.Session,org.h2.engine.Database,org.h2.util.BitField,long[]}, size=1712595, count=635}
{java.util.HashMap$Node,SomeType15,java.util.ArrayList,java.lang.Object[]}, size=1701090, count=1010}
{org.h2.command.CommandContainer,org.h2.command.dml.Select,java.util.ArrayList,java.lang.Object[]}, size=1696828, count=2507}
{java.util.HashMap$Node,java.util.HashMap$Node,org.apache.ignite.internal.pagemem.wal.record.CacheState,long[]}, size=1688000, count=640}
{org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTxPrepareFuture$MiniFuture,org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTxPrepareFuture,java.util.ArrayList,java.lang.Object[]}, size=1680330, count=790}
{SomeType150,SomeType147,java.util.ArrayList,java.lang.Object[]}, size=1626768, count=5214}
{java.util.LinkedList$Node,SomeType148,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=1605912, count=5214}
{org.apache.ignite.internal.processors.query.h2.database.H2PkHashIndex,org.apache.ignite.internal.processors.query.h2.opt.GridH2Table,java.util.ArrayList,java.lang.Object[]}, size=1603392, count=1608}
{org.apache.ignite.internal.processors.cache.IgniteCacheOffheapManagerImpl$CacheDataRowStore,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=1573610, count=4253}
{SomeType3,SomeType15,java.util.ArrayList,java.lang.Object[]}, size=1565930, count=680}
{org.h2.jdbc.JdbcPreparedStatement,org.h2.engine.Session,java.util.ArrayList,java.lang.Object[]}, size=1561861, count=2507}
{java.util.concurrent.ConcurrentHashMap$Node,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex,java.util.ArrayList,java.lang.Object[]}, size=1549132, count=4378}
{SomeType35,SomeType15,java.util.ArrayList,java.lang.Object[]}, size=1546550, count=680}
{SomeType148$DiscoveryListener,SomeType148,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=1522488, count=5214}
{com.mchange.v2.async.ThreadPoolAsynchronousRunner$PoolThread,SomeType18,java.lang.Object[]}, size=1494504, count=36}
{java.util.HashMap$Node,SomeType13,java.util.ArrayList,java.lang.Object[]}, size=1456814, count=1118}
{org.h2.index.IndexCursor,org.apache.ignite.internal.processors.query.h2.opt.GridH2Table,java.util.ArrayList,java.lang.Object[]}, size=1440492, count=2507}
{org.springframework.core.type.classreading.AnnotationMetadataReadingVisitor,SomeType17,java.util.Vector,java.lang.Object[]}, size=1408212, count=34}
{org.apache.ignite.internal.processors.cache.GridCacheContext,SomeType148,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=1397352, count=5214}
{org.h2.command.dml.Select,org.h2.table.TableFilter,java.util.ArrayList,java.lang.Object[]}, size=1347075, count=2507}
{org.apache.ignite.internal.cluster.ClusterGroupAdapter,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1333200, count=808}
{SomeType11,SomeType11,java.util.ArrayList,java.lang.Object[]}, size=1314130, count=869}
{org.apache.ignite.internal.IgniteComputeImpl,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1307344, count=808}
{java.util.LinkedList$Node,org.apache.ignite.internal.processors.plugin.CachePluginManager,java.util.ArrayList,java.lang.Object[]}, size=1293072, count=5214}
{SomeType36,SomeType11,java.util.ArrayList,java.lang.Object[]}, size=1261121, count=869}
{SomeType35,SomeType16,java.util.ArrayList,java.lang.Object[]}, size=1243119, count=584}
{SomeType3,SomeType16,java.util.ArrayList,java.lang.Object[]}, size=1242096, count=575}
{SomeType4,SomeType11,java.util.ArrayList,java.lang.Object[]}, size=1237658, count=869}
{org.h2.command.dml.Select,org.h2.engine.Session,java.util.ArrayList,java.lang.Object[]}, size=1213388, count=2507}
{org.h2.index.IndexCursor,org.h2.engine.Session,java.util.ArrayList,java.lang.Object[]}, size=1213388, count=2507}
{org.h2.command.CommandContainer,org.h2.engine.Session,java.util.ArrayList,java.lang.Object[]}, size=1213388, count=2507}
{org.h2.table.TableFilter,org.h2.engine.Session,java.util.ArrayList,java.lang.Object[]}, size=1213388, count=2507}
{java.util.concurrent.ConcurrentHashMap$Node,SomeType11,java.util.ArrayList,java.lang.Object[]}, size=1167269, count=869}
{SomeType27,SomeType11,java.util.ArrayList,java.lang.Object[]}, size=1164662, count=869}
{java.util.LinkedList$Node,SomeType11,java.util.ArrayList,java.lang.Object[]}, size=1163793, count=869}
{SomeType32,SomeType11,java.util.ArrayList,java.lang.Object[]}, size=1156841, count=869}
{org.h2.table.TableFilter,org.apache.ignite.internal.processors.query.h2.opt.GridH2Table,java.util.ArrayList,java.lang.Object[]}, size=1127117, count=2507}
{SomeType3,SomeType14,java.util.ArrayList,java.lang.Object[]}, size=1106582, count=506}
{java.lang.Package,sun.misc.Launcher$AppClassLoader,java.util.Vector,java.lang.Object[]}, size=1089564, count=102}
{SomeType35,SomeType14,java.util.ArrayList,java.lang.Object[]}, size=1088781, count=504}
{org.apache.ignite.internal.processors.cache.GridCacheContext,org.apache.ignite.internal.processors.plugin.CachePluginManager,java.util.ArrayList,java.lang.Object[]}, size=1084512, count=5214}
{java.lang.Thread,SomeType19,java.util.Vector,java.lang.Object[]}, size=1078402, count=26}
{java.util.HashMap$Node,SomeType5,java.util.ArrayList,java.lang.Object[]}, size=1017266, count=2663}
{com.mchange.v2.async.ThreadPoolAsynchronousRunner$PoolThread,SomeType18,java.lang.Object[]}, size=996336, count=24}
{org.apache.ignite.internal.processors.cache.distributed.dht.colocated.GridDhtColocatedCache,org.apache.ignite.configuration.CacheConfiguration,java.util.ArrayList,java.lang.Object[]}, size=951329, count=1527}
{org.apache.ignite.internal.processors.cache.DynamicCacheDescriptor,org.apache.ignite.configuration.CacheConfiguration,java.util.ArrayList,java.lang.Object[]}, size=936889, count=1607}
{SomeType149,org.apache.ignite.configuration.CacheConfiguration,java.util.ArrayList,java.lang.Object[]}, size=909570, count=1607}
{org.apache.ignite.internal.processors.query.h2.opt.GridH2PrimaryScanIndex,org.apache.ignite.internal.processors.query.h2.opt.GridH2Table,java.util.ArrayList,java.lang.Object[]}, size=883704, count=1608}
{org.apache.ignite.internal.processors.plugin.CachePluginManager,org.apache.ignite.configuration.CacheConfiguration,java.util.ArrayList,java.lang.Object[]}, size=851718, count=1607}
{java.nio.channels.spi.AbstractInterruptibleChannel$1,sun.nio.ch.FileChannelImpl,sun.nio.ch.NativeThreadSet,long[]}, size=849441, count=4401}
{org.apache.ignite.internal.processors.cache.persistence.file.RandomAccessFileIO,sun.nio.ch.FileChannelImpl,sun.nio.ch.NativeThreadSet,long[]}, size=849248, count=4400}
{java.util.TimerThread,SomeType18,java.lang.Object[]}, size=829880, count=20}
{java.util.ResourceBundle$LoaderReference,SomeType18,java.lang.Object[]}, size=827400, count=20}
{SomeType13,SomeType11,java.util.ArrayList,java.lang.Object[]}, size=809714, count=521}
{org.apache.ignite.internal.processors.query.h2.H2TableDescriptor,org.apache.ignite.internal.processors.query.h2.opt.GridH2Table,java.util.ArrayList,java.lang.Object[]}, size=796872, count=1608}
{org.apache.ignite.internal.GridCachePluginContext,org.apache.ignite.configuration.CacheConfiguration,java.util.ArrayList,java.lang.Object[]}, size=787438, count=1607}
{SomeType4,SomeType13,java.util.ArrayList,java.lang.Object[]}, size=773686, count=559}
{java.util.AbstractMap$1,org.apache.ignite.internal.util.GridPartitionStateMap,java.util.BitSet,long[]}, size=765574, count=46}
{org.springframework.core.type.classreading.MethodMetadataReadingVisitor,SomeType18,java.lang.Object[]}, size=744948, count=18}
{org.springframework.core.type.classreading.MethodMetadataReadingVisitor,SomeType17,java.util.Vector,java.lang.Object[]}, size=744804, count=18}
{org.dom4j.tree.DefaultElement,org.dom4j.tree.DefaultElement,java.util.ArrayList,java.lang.Object[]}, size=743728, count=487}
{org.jsr166.ConcurrentHashMap8$Node,org.apache.ignite.internal.processors.query.h2.opt.GridH2Table,java.util.ArrayList,java.lang.Object[]}, size=738984, count=1608}
{java.util.concurrent.ConcurrentHashMap$Node,org.apache.ignite.internal.processors.query.h2.opt.GridH2Table,java.util.ArrayList,java.lang.Object[]}, size=738984, count=1608}
{org.apache.ignite.internal.processors.cache.GridCacheContext,org.apache.ignite.configuration.CacheConfiguration,java.util.ArrayList,java.lang.Object[]}, size=736014, count=1607}
{java.util.LinkedList$Node,SomeType13,java.util.ArrayList,java.lang.Object[]}, size=726171, count=559}
{SomeType10,SomeType15,java.util.ArrayList,java.lang.Object[]}, size=703422, count=250}
{SomeType47,SomeType15,java.util.ArrayList,java.lang.Object[]}, size=689733, count=250}
{org.h2.index.IndexCursor,org.h2.table.TableFilter,java.util.ArrayList,java.lang.Object[]}, size=685227, count=2507}
{SomeType10,SomeType16,java.util.ArrayList,java.lang.Object[]}, size=667942, count=249}
{java.util.AbstractMap$2,org.apache.ignite.internal.util.GridPartitionStateMap,java.util.BitSet,long[]}, size=667177, count=145}
{SomeType47,SomeType16,java.util.ArrayList,java.lang.Object[]}, size=664437, count=255}
{java.util.LinkedList$Node,org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionsExchangeFuture,java.util.ArrayList,java.lang.Object[]}, size=622547, count=353}
{org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionsExchangeFuture$1,org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionsExchangeFuture,java.util.ArrayList,java.lang.Object[]}, size=620076, count=353}
{org.apache.ignite.internal.managers.discovery.GridDiscoveryManager$CachePredicate,org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.ArrayList,java.lang.Object[]}, size=526614, count=5214}
{SomeType10,SomeType14,java.util.ArrayList,java.lang.Object[]}, size=509440, count=167}
{SomeType47,SomeType14,java.util.ArrayList,java.lang.Object[]}, size=490157, count=161}
{org.h2.expression.ConditionInConstantSet$1,org.h2.engine.Session,java.util.ArrayList,java.lang.Object[]}, size=487872, count=1008}
{org.dom4j.tree.DefaultText,org.dom4j.tree.DefaultElement,java.util.ArrayList,java.lang.Object[]}, size=478384, count=256}
{org.apache.ignite.internal.processors.query.h2.opt.GridH2PrimaryScanIndex,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex,java.util.ArrayList,java.lang.Object[]}, size=473120, count=1608}
{org.apache.ignite.internal.events.DiscoveryCustomEvent,java.util.Collections$UnmodifiableCollection,java.util.ArrayList,java.lang.Object[]}, size=471880, count=322}
{org.dom4j.tree.DefaultElement,org.dom4j.tree.DefaultElement,java.util.ArrayList,java.lang.Object[]}, size=468288, count=355}
{SomeType93,SomeType11,java.util.ArrayList,java.lang.Object[]}, size=416584, count=296}
{org.h2.jdbc.JdbcConnection,org.h2.engine.Session,java.util.ArrayList,java.lang.Object[]}, size=415248, count=633}
{org.h2.engine.Session,org.h2.engine.UndoLog,java.util.ArrayList,java.lang.Object[]}, size=409575, count=635}
{SomeType14,SomeType14,java.util.ArrayList,java.lang.Object[]}, size=396856, count=252}
{SomeType33,SomeType14,java.util.ArrayList,java.lang.Object[]}, size=381484, count=252}
{java.util.HashMap$Node,SomeType16,java.util.ArrayList,java.lang.Object[]}, size=380866, count=258}
{SomeType45,SomeType14,java.util.ArrayList,java.lang.Object[]}, size=374680, count=252}
{SomeType4,SomeType14,java.util.ArrayList,java.lang.Object[]}, size=374680, count=252}
{SomeType44,SomeType14,java.util.ArrayList,java.lang.Object[]}, size=374680, count=252}
{SomeType43,SomeType14,java.util.ArrayList,java.lang.Object[]}, size=374680, count=252}
{java.util.ResourceBundle$LoaderReference,SomeType19,java.util.Vector,java.lang.Object[]}, size=372258, count=9}
{SomeType61,SomeType15,java.util.ArrayList,java.lang.Object[]}, size=370316, count=202}
{java.util.TreeMap$Entry,java.util.Collections$UnmodifiableCollection,java.util.ArrayList,java.lang.Object[]}, size=364490, count=378}
{SomeType,SomeType20,java.util.ArrayList,java.lang.Object[]}, size=359494, count=238}
{SomeType44,SomeType15,java.util.ArrayList,java.lang.Object[]}, size=356580, count=202}
{SomeType94,SomeType15,java.util.ArrayList,java.lang.Object[]}, size=356580, count=202}
{SomeType95,SomeType15,java.util.ArrayList,java.lang.Object[]}, size=356580, count=202}
{SomeType43,SomeType15,java.util.ArrayList,java.lang.Object[]}, size=356580, count=202}
{SomeType45,SomeType15,java.util.ArrayList,java.lang.Object[]}, size=356580, count=202}
{SomeType4,SomeType15,java.util.ArrayList,java.lang.Object[]}, size=356580, count=202}
{java.util.concurrent.ConcurrentHashMap$Node,SomeType14,java.util.ArrayList,java.lang.Object[]}, size=354268, count=252}
{SomeType96,SomeType14,java.util.ArrayList,java.lang.Object[]}, size=353550, count=259}
{java.util.GregorianCalendar,sun.util.calendar.Gregorian$Date,sun.util.calendar.ZoneInfo,long[]}, size=353383, count=193}
{java.util.LinkedList$Node,SomeType14,java.util.ArrayList,java.lang.Object[]}, size=353260, count=252}
{SomeType32,SomeType14,java.util.ArrayList,java.lang.Object[]}, size=351244, count=252}
{org.h2.util.CloseWatcher,org.h2.engine.Session,java.util.ArrayList,java.lang.Object[]}, size=346884, count=633}
{java.lang.Thread,sun.misc.Launcher$AppClassLoader,java.util.Vector,java.lang.Object[]}, size=344224, count=32}
{java.util.concurrent.ConcurrentHashMap$Node,SomeType15,java.util.ArrayList,java.lang.Object[]}, size=340218, count=202}
{java.util.LinkedList$Node,SomeType15,java.util.ArrayList,java.lang.Object[]}, size=339410, count=202}
{java.lang.Thread,SomeType17,java.util.Vector,java.lang.Object[]}, size=334918, count=14}
{java.util.HashMap$Node,org.h2.engine.Session,java.util.ArrayList,java.lang.Object[]}, size=334224, count=633}
{SomeType15,SomeType20,java.util.ArrayList,java.lang.Object[]}, size=332358, count=195}
{SomeType15,SomeType15,java.util.ArrayList,java.lang.Object[]}, size=331330, count=202}
{SomeType96,SomeType15,java.util.ArrayList,java.lang.Object[]}, size=331330, count=202}
{java.util.LinkedList$Node,org.apache.ignite.cache.QueryEntity,java.util.ArrayList,java.lang.Object[]}, size=330896, count=1608}
{org.apache.ignite.internal.managers.discovery.ConsistentIdMapper,org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.ArrayDeque,java.lang.Object[]}, size=329215, count=5}
{SomeType96,SomeType11,java.util.ArrayList,java.lang.Object[]}, size=317590, count=249}
{SomeType97,SomeType15,java.util.ArrayList,java.lang.Object[]}, size=311539, count=183}
{org.h2.table.TableFilter,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex,java.util.ArrayList,java.lang.Object[]}, size=311355, count=629}
{SomeType59,SomeType5,java.util.ArrayList,java.lang.Object[]}, size=309942, count=771}
{SomeType96,SomeType20,java.util.ArrayList,java.lang.Object[]}, size=298818, count=195}
{java.util.HashMap$Node,sun.nio.ch.SelectionKeyImpl,org.apache.ignite.internal.util.nio.GridSelectorNioSessionImpl,java.lang.Object[]}, size=294446, count=346}
{java.util.HashMap$Node,javax.management.MBeanAttributeInfo,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=292950, count=1890}
{org.dom4j.tree.DefaultText,org.dom4j.tree.DefaultElement,java.util.ArrayList,java.lang.Object[]}, size=290384, count=192}
{SomeType98,SomeType18,java.lang.Object[]}, size=289262, count=7}
{sun.reflect.generics.tree.MethodTypeSignature,sun.reflect.generics.tree.ClassTypeSignature,java.util.ArrayList,java.lang.Object[]}, size=279048, count=1661}
{org.apache.ignite.internal.processors.cache.distributed.GridDistributedTxMapping,org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode,java.util.ArrayList,java.lang.Object[]}, size=272550, count=790}
{org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtPartitionTopologyImpl,org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionsExchangeFuture,java.util.ArrayList,java.lang.Object[]}, size=271560, count=146}
{java.util.jar.JarFile$JarFileEntry,java.util.jar.JarFile,java.util.ArrayDeque,java.lang.Object[]}, size=271420, count=662}
{org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionDemander,org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionsExchangeFuture,java.util.ArrayList,java.lang.Object[]}, size=263238, count=146}
{ch.qos.logback.classic.Logger,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=262288, count=776}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$Replace,org.apache.ignite.internal.processors.cache.persistence.MetadataStorage$MetaTree,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=254916, count=146}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$Insert,org.apache.ignite.internal.processors.cache.persistence.MetadataStorage$MetaTree,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=254916, count=146}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$LockBackAndTail,org.apache.ignite.internal.processors.cache.persistence.MetadataStorage$MetaTree,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=254916, count=146}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$LockBackAndRmvFromLeaf,org.apache.ignite.internal.processors.cache.persistence.MetadataStorage$MetaTree,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=254916, count=146}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$AskNeighbor,org.apache.ignite.internal.processors.cache.persistence.MetadataStorage$MetaTree,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=254916, count=146}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$LockTailForward,org.apache.ignite.internal.processors.cache.persistence.MetadataStorage$MetaTree,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=254916, count=146}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$Search,org.apache.ignite.internal.processors.cache.persistence.MetadataStorage$MetaTree,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=254916, count=146}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$LockTail,org.apache.ignite.internal.processors.cache.persistence.MetadataStorage$MetaTree,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=254916, count=146}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$RemoveFromLeaf,org.apache.ignite.internal.processors.cache.persistence.MetadataStorage$MetaTree,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=254916, count=146}
{SomeType99,SomeType18,java.lang.Object[]}, size=248460, count=6}
{org.apache.ignite.internal.processors.affinity.GridAffinityAssignmentCache,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=248054, count=146}
{org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionsExchangeFuture,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=246791, count=353}
{sun.misc.URLClassPath$JarLoader,java.util.jar.JarFile,java.util.ArrayDeque,java.lang.Object[]}, size=242847, count=669}
{SomeType16,SomeType15,java.util.ArrayList,java.lang.Object[]}, size=230326, count=127}
{java.lang.ref.Finalizer,java.util.jar.JarFile,java.util.ArrayDeque,java.lang.Object[]}, size=226122, count=669}
{org.jsr166.ConcurrentHashMap8$Node,org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode,java.util.ArrayList,java.lang.Object[]}, size=216144, count=632}
{org.apache.ignite.internal.events.DiscoveryCustomEvent,org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode,java.util.ArrayList,java.lang.Object[]}, size=215240, count=322}
{SomeType97,SomeType11,java.util.ArrayList,java.lang.Object[]}, size=208067, count=159}
{sun.reflect.DelegatingClassLoader,SomeType17,java.util.Vector,java.lang.Object[]}, size=206876, count=6}
{org.h2.expression.ConditionIn,org.h2.engine.Database,org.h2.util.BitField,long[]}, size=205650, count=75}
{org.h2.message.Trace,org.h2.message.TraceSystem,java.util.concurrent.atomic.AtomicReferenceArray,java.lang.Object[]}, size=200960, count=640}
{SomeType4,SomeType16,java.util.ArrayList,java.lang.Object[]}, size=200882, count=129}
{SomeType45,SomeType16,java.util.ArrayList,java.lang.Object[]}, size=200882, count=129}
{SomeType44,SomeType16,java.util.ArrayList,java.lang.Object[]}, size=200882, count=129}
{SomeType43,SomeType16,java.util.ArrayList,java.lang.Object[]}, size=200882, count=129}
{org.apache.xerces.dom.PSVIAttrNSImpl,org.apache.xerces.dom.PSVIElementNSImpl,org.apache.xerces.impl.xs.util.ObjectListImpl,java.lang.Object[]}, size=198691, count=461}
{org.h2.index.IndexCursor,org.apache.ignite.internal.processors.query.h2.database.H2TreeIndex,java.util.ArrayList,java.lang.Object[]}, size=194990, count=629}
{java.util.LinkedList$Node,SomeType16,java.util.ArrayList,java.lang.Object[]}, size=189917, count=129}
{org.apache.ignite.internal.managers.discovery.DiscoCache,org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode,java.util.ArrayList,java.lang.Object[]}, size=188700, count=510}
{SomeType17,SomeType17,java.util.Vector,java.lang.Object[]}, size=186396, count=4}
{java.util.WeakHashMap$Entry,SomeType17,java.util.Vector,java.lang.Object[]}, size=186390, count=5}
{java.util.HashMap$Node,sun.net.www.protocol.jar.URLJarFile,java.util.ArrayDeque,java.lang.Object[]}, size=180600, count=516}
{org.h2.expression.ConditionInConstantSet$1,org.h2.expression.ConditionInConstantSet,java.util.ArrayList,java.lang.Object[]}, size=166320, count=1008}
{SomeType99,SomeType18,java.lang.Object[]}, size=165640, count=4}
{SomeType97,SomeType14,java.util.ArrayList,java.lang.Object[]}, size=161836, count=108}
{org.h2.expression.ConditionNot,org.h2.expression.ConditionInConstantSet,java.util.ArrayList,java.lang.Object[]}, size=159264, count=1008}
{SomeType3,SomeType20,java.util.ArrayList,java.lang.Object[]}, size=151014, count=87}
{SomeType35,SomeType20,java.util.ArrayList,java.lang.Object[]}, size=144809, count=87}
{org.apache.xerces.impl.xs.XSAnnotationImpl,org.apache.xerces.impl.xs.SchemaGrammar,java.util.Vector,java.lang.Object[]}, size=140184, count=236}
{org.apache.xerces.impl.xs.XSAnnotationImpl,org.apache.xerces.impl.xs.SchemaGrammar,java.util.Vector,java.lang.Object[]}, size=140184, count=236}
{org.apache.ignite.internal.util.nio.GridSelectorNioSessionImpl,sun.nio.ch.SelectionKeyImpl,org.apache.ignite.internal.util.nio.GridSelectorNioSessionImpl,java.lang.Object[]}, size=139611, count=173}
{org.apache.ignite.internal.processors.cache.persistence.MetadataStorage$MetaTree,org.apache.ignite.internal.processors.cache.persistence.tree.reuse.ReuseListImpl,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=138116, count=146}
{ch.qos.logback.classic.Logger,ch.qos.logback.classic.Logger,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=134387, count=775}
{sun.reflect.DelegatingClassLoader,sun.reflect.DelegatingClassLoader,java.util.Vector,java.lang.Object[]}, size=132300, count=270}
{org.dom4j.tree.DefaultAttribute,org.dom4j.tree.DefaultElement,java.util.ArrayList,java.lang.Object[]}, size=131504, count=726}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$1,org.apache.ignite.internal.processors.cache.persistence.MetadataStorage$MetaTree,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=128772, count=146}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$CutRoot,org.apache.ignite.internal.processors.cache.persistence.MetadataStorage$MetaTree,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=128626, count=146}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$AddRoot,org.apache.ignite.internal.processors.cache.persistence.MetadataStorage$MetaTree,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=128626, count=146}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$InitRoot,org.apache.ignite.internal.processors.cache.persistence.MetadataStorage$MetaTree,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=128626, count=146}
{org.apache.ignite.internal.processors.cache.persistence.MetadataStorage,org.apache.ignite.internal.processors.cache.persistence.MetadataStorage$MetaTree,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=125122, count=146}
{org.apache.zookeeper.ClientCnxn$SendThread,SomeType18,java.lang.Object[]}, size=124617, count=3}
{SomeType100,SomeType17,java.util.Vector,java.lang.Object[]}, size=124572, count=2}
{org.apache.zookeeper.ClientCnxn$EventThread,SomeType18,java.lang.Object[]}, size=124557, count=3}
{com.mchange.v2.async.ThreadPoolAsynchronousRunner$PoolThread,SomeType19,java.util.Vector,java.lang.Object[]}, size=124518, count=3}
{java.lang.reflect.WeakCache$CacheKey,SomeType17,java.util.Vector,java.lang.Object[]}, size=124472, count=4}
{java.util.TimerThread,SomeType19,java.util.Vector,java.lang.Object[]}, size=124458, count=3}
{SomeType101,SomeType17,java.util.Vector,java.lang.Object[]}, size=124305, count=3}
{SomeType102,SomeType17,java.util.Vector,java.lang.Object[]}, size=124264, count=2}
{SomeType103,SomeType17,java.util.Vector,java.lang.Object[]}, size=124206, count=3}
{SomeType104,SomeType17,java.util.Vector,java.lang.Object[]}, size=124161, count=3}
{org.apache.ignite.internal.marshaller.optimized.OptimizedMarshaller,SomeType19,java.util.Vector,java.lang.Object[]}, size=124092, count=3}
{org.jsr166.ConcurrentHashMap8$Node,SomeType19,java.util.Vector,java.lang.Object[]}, size=124050, count=3}
{org.apache.kafka.clients.consumer.internals.AbstractCoordinator$HeartbeatThread,SomeType18,java.lang.Object[]}, size=123942, count=3}
{SomeType19,SomeType19,java.util.Vector,java.lang.Object[]}, size=123918, count=2}
{org.apache.ignite.internal.processors.cache.CacheGroupContext,org.apache.ignite.internal.processors.cache.persistence.MemoryPolicy,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=122932, count=146}
{org.apache.ignite.internal.managers.communication.GridIoMessage,org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTxPrepareRequest,java.util.BitSet,long[]}, size=122661, count=297}
{com.sun.org.apache.xerces.internal.impl.xs.XSElementDecl,com.sun.org.apache.xerces.internal.impl.xs.XSComplexTypeDecl,com.sun.org.apache.xerces.internal.impl.xs.models.XSDFACM,java.lang.Object[]}, size=122374, count=221}
{org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager,org.apache.ignite.internal.processors.cache.persistence.tree.reuse.ReuseListImpl,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=122202, count=146}
{net.sf.saxon.tree.util.AttributeCollectionImpl,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=119808, count=234}
{net.sf.saxon.tree.util.AttributeCollectionImpl,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=119808, count=234}
{net.sf.saxon.tree.util.AttributeCollectionImpl,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=119808, count=234}
{java.security.ProtectionDomain,sun.reflect.DelegatingClassLoader,java.util.Vector,java.lang.Object[]}, size=119610, count=270}
{java.io.ObjectStreamClass,java.io.ObjectStreamClass,java.io.ObjectStreamClass$FieldReflector,long[]}, size=119356, count=189}
{org.apache.ignite.internal.processors.cache.persistence.tree.reuse.ReuseListImpl,org.apache.ignite.internal.processors.cache.persistence.tree.reuse.ReuseListImpl,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=119282, count=146}
{org.apache.xerces.impl.xs.XSAnnotationImpl,org.apache.xerces.impl.xs.SchemaGrammar,java.util.Vector,java.lang.Object[]}, size=115236, count=194}
{org.apache.ignite.internal.processors.cache.persistence.MetadataStorage,org.apache.ignite.internal.processors.cache.persistence.tree.reuse.ReuseListImpl,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=113442, count=146}
{java.util.concurrent.ConcurrentHashMap$Node,java.util.concurrent.ConcurrentHashMap$Node,sun.util.calendar.ZoneInfo,long[]}, size=111764, count=126}
{ch.qos.logback.classic.Logger,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=110188, count=326}
{org.jsr166.ConcurrentHashMap8$Node,org.jsr166.ConcurrentHashMap8$Node,org.apache.ignite.internal.util.nio.GridSelectorNioSessionImpl,java.lang.Object[]}, size=110124, count=69}
{org.apache.ignite.internal.processors.cache.persistence.freelist.PagesList$CutTail,org.apache.ignite.internal.processors.cache.persistence.tree.reuse.ReuseListImpl,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=109792, count=146}
{SomeType105,SomeType14,java.util.ArrayList,java.lang.Object[]}, size=109520, count=72}
{com.sun.org.apache.xerces.internal.impl.xs.XSElementDecl,com.sun.org.apache.xerces.internal.impl.dv.xs.XSSimpleTypeDecl,java.util.Vector,java.lang.Object[]}, size=106314, count=150}
{sun.reflect.DelegatingClassLoader,sun.misc.Launcher$AppClassLoader,java.util.Vector,java.lang.Object[]}, size=105860, count=10}
{org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionDemander$RebalanceFuture,org.apache.ignite.internal.processors.cache.CacheGroupContext,java.util.ArrayList,java.lang.Object[]}, size=105464, count=146}
{org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager,org.apache.ignite.internal.processors.cache.persistence.MetadataStorage,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=100448, count=146}
{java.util.HashMap$Node,SomeType20,java.util.ArrayList,java.lang.Object[]}, size=100318, count=78}
{org.apache.ignite.internal.processors.cache.persistence.tree.reuse.ReuseListImpl,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,org.apache.ignite.internal.mem.unsafe.UnsafeMemoryProvider,long[]}, size=99134, count=146}
{org.apache.ignite.internal.processors.cache.persistence.MetadataStorage$MetaTree,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,org.apache.ignite.internal.mem.unsafe.UnsafeMemoryProvider,long[]}, size=99134, count=146}
{org.apache.ignite.internal.processors.cache.persistence.MetadataStorage,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,org.apache.ignite.internal.mem.unsafe.UnsafeMemoryProvider,long[]}, size=99134, count=146}
{java.util.concurrent.ConcurrentHashMap$Node,org.apache.ignite.internal.processors.cache.CacheGroupContext,java.util.ArrayList,java.lang.Object[]}, size=98894, count=146}
{org.apache.ignite.internal.managers.communication.GridIoMessage,org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTxFinishRequest,org.apache.ignite.internal.util.GridLongList,long[]}, size=98252, count=308}
{java.lang.ThreadLocal$ThreadLocalMap$Entry,java.util.GregorianCalendar,sun.util.calendar.ZoneInfo,long[]}, size=97240, count=104}
{ch.qos.logback.classic.Logger,SomeType76,ch.qos.logback.classic.spi.TurboFilterList,java.lang.Object[]}, size=97000, count=776}
{org.dom4j.tree.DefaultAttribute,org.dom4j.tree.DefaultElement,java.util.ArrayList,java.lang.Object[]}, size=96672, count=534}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$RemoveFromLeaf,org.apache.ignite.internal.processors.cache.IgniteCacheOffheapManagerImpl$PendingEntriesTree,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=96360, count=55}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$LockBackAndRmvFromLeaf,org.apache.ignite.internal.processors.cache.IgniteCacheOffheapManagerImpl$PendingEntriesTree,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=96360, count=55}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$LockTail,org.apache.ignite.internal.processors.cache.IgniteCacheOffheapManagerImpl$PendingEntriesTree,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=96360, count=55}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$Search,org.apache.ignite.internal.processors.cache.IgniteCacheOffheapManagerImpl$PendingEntriesTree,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=96360, count=55}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$Insert,org.apache.ignite.internal.processors.cache.IgniteCacheOffheapManagerImpl$PendingEntriesTree,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=96360, count=55}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$AskNeighbor,org.apache.ignite.internal.processors.cache.IgniteCacheOffheapManagerImpl$PendingEntriesTree,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=96360, count=55}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$LockBackAndTail,org.apache.ignite.internal.processors.cache.IgniteCacheOffheapManagerImpl$PendingEntriesTree,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=96360, count=55}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$LockTailForward,org.apache.ignite.internal.processors.cache.IgniteCacheOffheapManagerImpl$PendingEntriesTree,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=96360, count=55}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$Replace,org.apache.ignite.internal.processors.cache.IgniteCacheOffheapManagerImpl$PendingEntriesTree,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=96360, count=55}
{org.apache.ignite.internal.processors.cache.CacheGroupContext$2,org.apache.ignite.internal.processors.cache.CacheGroupContext,java.util.ArrayList,java.lang.Object[]}, size=95974, count=146}
{java.lang.ref.Finalizer,sun.net.www.protocol.jar.URLJarFile,java.util.ArrayDeque,java.lang.Object[]}, size=95460, count=258}
{java.util.LinkedList$Node,java.util.LinkedList$Node,org.apache.ignite.internal.util.tostring.GridToStringThreadLocal,java.lang.Object[]}, size=92496, count=254}
{org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtPartitionTopologyImpl,org.apache.ignite.internal.processors.cache.CacheGroupContext,java.util.ArrayList,java.lang.Object[]}, size=92470, count=146}
{org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager,org.apache.ignite.internal.processors.cache.CacheGroupContext,java.util.ArrayList,java.lang.Object[]}, size=92470, count=146}
{org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPreloader,org.apache.ignite.internal.processors.cache.CacheGroupContext,java.util.ArrayList,java.lang.Object[]}, size=92470, count=146}
{org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionDemander,org.apache.ignite.internal.processors.cache.CacheGroupContext,java.util.ArrayList,java.lang.Object[]}, size=92470, count=146}
{org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionSupplier,org.apache.ignite.internal.processors.cache.CacheGroupContext,java.util.ArrayList,java.lang.Object[]}, size=92470, count=146}
{java.util.LinkedHashMap$Entry,java.security.Provider$Service,java.util.ArrayList,java.lang.Object[]}, size=90177, count=381}
{org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl$Segment,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,org.apache.ignite.internal.mem.unsafe.UnsafeMemoryProvider,long[]}, size=88144, count=112}
{java.util.HashMap$Node,java.util.HashMap$Node,sun.util.calendar.ZoneInfo,long[]}, size=86762, count=169}
{org.apache.xerces.dom.PSVIElementNSImpl,org.apache.xerces.dom.PSVIElementNSImpl,org.apache.xerces.impl.xs.util.ObjectListImpl,java.lang.Object[]}, size=85800, count=93}
{org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl$PagePool,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,org.apache.ignite.internal.mem.unsafe.UnsafeMemoryProvider,long[]}, size=85158, count=114}
{org.apache.ignite.internal.processors.cache.IgniteCacheOffheapManagerImpl$PendingEntriesTree,org.apache.ignite.internal.processors.cache.CacheGroupContext,java.util.ArrayList,java.lang.Object[]}, size=84696, count=55}
{java.util.LinkedList$Node,SomeType7,java.util.ArrayList,java.lang.Object[]}, size=83848, count=223}
{org.springframework.beans.factory.support.DefaultListableBeanFactory,SomeType18,java.lang.Object[]}, size=83366, count=2}
{org.apache.kafka.clients.consumer.internals.AbstractCoordinator$HeartbeatThread,SomeType17,java.util.Vector,java.lang.Object[]}, size=83304, count=4}
{org.apache.zookeeper.ClientCnxn$SendThread,SomeType18,java.lang.Object[]}, size=83078, count=2}
{org.apache.zookeeper.ClientCnxn$EventThread,SomeType18,java.lang.Object[]}, size=83038, count=2}
{org.springframework.context.annotation.ConfigurationClassPostProcessor,SomeType18,java.lang.Object[]}, size=82856, count=2}
{SomeType102,SomeType18,java.lang.Object[]}, size=82820, count=2}
{SomeType69,SomeType17,java.util.Vector,java.lang.Object[]}, size=82774, count=2}
{java.util.WeakHashMap$Entry,SomeType19,java.util.Vector,java.lang.Object[]}, size=82748, count=2}
{java.lang.reflect.WeakCache$CacheKey,SomeType18,java.lang.Object[]}, size=82732, count=2}
{org.jsr166.ConcurrentHashMap8$Node,SomeType18,java.lang.Object[]}, size=82716, count=2}
{java.lang.reflect.WeakCache$CacheKey,SomeType19,java.util.Vector,java.lang.Object[]}, size=82716, count=2}
{java.util.HashMap$Node,SomeType18,java.lang.Object[]}, size=82716, count=2}
{org.h2.table.MetaTable,org.h2.engine.Database,org.h2.util.BitField,long[]}, size=82708, count=29}
{org.springframework.core.io.DefaultResourceLoader$ClassPathContextResource,SomeType18,java.lang.Object[]}, size=82708, count=2}
{org.springframework.core.io.DefaultResourceLoader$ClassPathContextResource,SomeType17,java.util.Vector,java.lang.Object[]}, size=82692, count=2}
{org.springframework.expression.spel.support.StandardTypeLocator,SomeType18,java.lang.Object[]}, size=82692, count=2}
{org.springframework.core.io.DefaultResourceLoader,SomeType18,java.lang.Object[]}, size=82676, count=2}
{SomeType100,SomeType18,java.lang.Object[]}, size=82628, count=2}
{org.springframework.context.event.SimpleApplicationEventMulticaster,SomeType18,java.lang.Object[]}, size=82628, count=2}
{org.springframework.context.support.ClassPathXmlApplicationContext,SomeType18,java.lang.Object[]}, size=82628, count=2}
{org.apache.kafka.clients.consumer.internals.AbstractCoordinator$HeartbeatThread,SomeType18,java.lang.Object[]}, size=82628, count=2}
{SomeType98,SomeType18,java.lang.Object[]}, size=82628, count=2}
{org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl$Segment,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl$PagePool,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=79184, count=112}
{java.util.LinkedList,java.util.LinkedList$Node,org.apache.ignite.internal.util.tostring.GridToStringThreadLocal,java.lang.Object[]}, size=74504, count=139}
{java.lang.ref.SoftReference,java.io.ObjectStreamClass,java.io.ObjectStreamClass$FieldReflector,long[]}, size=70575, count=189}
{org.apache.ignite.internal.util.nio.GridNioRecoveryDescriptor,org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode,java.util.ArrayList,java.lang.Object[]}, size=69546, count=173}
{java.util.concurrent.ConcurrentHashMap$Node,org.apache.kafka.common.metrics.Sensor,java.util.ArrayList,java.lang.Object[]}, size=67468, count=167}
{org.apache.ignite.internal.processors.cache.query.continuous.CacheContinuousQueryHandler,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=66963, count=39}
{java.text.SimpleDateFormat,java.util.GregorianCalendar,sun.util.calendar.ZoneInfo,long[]}, size=66776, count=68}
{org.apache.ignite.internal.managers.discovery.GridDiscoveryManager$DiscoveryWorker,org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.ArrayDeque,java.lang.Object[]}, size=65843, count=1}
{org.apache.ignite.internal.managers.discovery.GridDiscoveryManager$MetricsUpdater,org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.ArrayDeque,java.lang.Object[]}, size=65843, count=1}
{SomeType133$5,org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.ArrayDeque,java.lang.Object[]}, size=65843, count=1}
{org.apache.ignite.internal.processors.cache.binary.BinaryMetadataTransport,org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.ArrayDeque,java.lang.Object[]}, size=65843, count=1}
{org.apache.ignite.internal.managers.discovery.GridDiscoveryManager$5,org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.ArrayDeque,java.lang.Object[]}, size=65843, count=1}
{java.util.LinkedList$Node,org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.ArrayDeque,java.lang.Object[]}, size=65843, count=1}
{org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.ArrayDeque,java.lang.Object[]}, size=65843, count=1}
{org.apache.ignite.internal.processors.marshaller.MarshallerMappingTransport,org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.ArrayDeque,java.lang.Object[]}, size=65843, count=1}
{org.apache.ignite.internal.managers.GridManagerAdapter$1,org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.ArrayDeque,java.lang.Object[]}, size=65843, count=1}
{SomeType133$2,org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.ArrayDeque,java.lang.Object[]}, size=65843, count=1}
{org.apache.ignite.internal.managers.discovery.GridDiscoveryManager$6,org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.ArrayDeque,java.lang.Object[]}, size=65843, count=1}
{org.apache.ignite.internal.managers.discovery.GridDiscoveryManager$4,org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.ArrayDeque,java.lang.Object[]}, size=65843, count=1}
{org.apache.ignite.internal.managers.discovery.GridDiscoveryManager$7,org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.ArrayDeque,java.lang.Object[]}, size=65843, count=1}
{SomeType133$3,org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.ArrayDeque,java.lang.Object[]}, size=65843, count=1}
{sun.rmi.transport.Target,sun.misc.Launcher$AppClassLoader,java.util.Vector,java.lang.Object[]}, size=64080, count=6}
{org.apache.ignite.internal.processors.cache.query.continuous.CacheContinuousQueryHandler$1,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=63141, count=39}
{org.apache.ignite.internal.processors.cache.CacheGroupDescriptor,org.apache.ignite.configuration.CacheConfiguration,java.util.ArrayList,java.lang.Object[]}, size=63012, count=118}
{java.util.HashMap$Node,SomeType17,java.util.Vector,java.lang.Object[]}, size=62220, count=2}
{java.util.TreeMap$Entry,org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode,java.util.ArrayList,java.lang.Object[]}, size=62197, count=175}
{com.sun.org.apache.xerces.internal.impl.xs.XSComplexTypeDecl,com.sun.org.apache.xerces.internal.impl.xs.SchemaGrammar,java.util.Vector,java.lang.Object[]}, size=61456, count=92}
{java.util.concurrent.ConcurrentHashMap$Node,ch.qos.logback.classic.Logger,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=60787, count=323}
{SomeType20,SomeType14,java.util.ArrayList,java.lang.Object[]}, size=60636, count=38}
{org.springframework.beans.factory.support.RootBeanDefinition,org.springframework.beans.MutablePropertyValues,java.util.ArrayList,java.lang.Object[]}, size=60480, count=172}
{org.apache.xerces.util.SymbolHash$Entry,org.apache.xerces.impl.xs.SchemaGrammar,java.util.Vector,java.lang.Object[]}, size=55986, count=93}
{java.util.HashMap$Node,java.io.FilePermissionCollection,java.util.ArrayList,java.lang.Object[]}, size=55965, count=273}
{java.util.concurrent.ConcurrentHashMap$Node,org.apache.kafka.common.metrics.Sensor,java.util.ArrayList,java.lang.Object[]}, size=54944, count=136}
{org.apache.ignite.internal.processors.cache.CacheGroupContext,org.apache.ignite.configuration.CacheConfiguration,java.util.ArrayList,java.lang.Object[]}, size=54044, count=118}
{org.apache.ignite.internal.processors.cache.CacheGroupContext,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=54020, count=146}
{org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=54020, count=146}
{org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionDemander$RebalanceFuture,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=54020, count=146}
{org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionDemander,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=54020, count=146}
{org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPreloader,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=54020, count=146}
{org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtPartitionTopologyImpl,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=54020, count=146}
{sun.reflect.generics.tree.ClassSignature,sun.reflect.generics.tree.ClassTypeSignature,java.util.ArrayList,java.lang.Object[]}, size=53920, count=337}
{SomeType13,SomeType13,java.util.ArrayList,java.lang.Object[]}, size=53324, count=38}
{SomeType43,SomeType20,java.util.ArrayList,java.lang.Object[]}, size=53318, count=39}
{SomeType4,SomeType20,java.util.ArrayList,java.lang.Object[]}, size=53318, count=39}
{SomeType44,SomeType20,java.util.ArrayList,java.lang.Object[]}, size=53318, count=39}
{java.security.ProtectionDomain,sun.misc.Launcher$AppClassLoader,java.util.Vector,java.lang.Object[]}, size=53220, count=5}
{org.apache.ignite.internal.processors.cache.distributed.dht.atomic.GridDhtAtomicCache,org.apache.ignite.configuration.CacheConfiguration,java.util.ArrayList,java.lang.Object[]}, size=52400, count=80}
{ch.qos.logback.classic.Logger,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=52109, count=107}
{ch.qos.logback.classic.Logger,ch.qos.logback.classic.Logger,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=51289, count=325}
{java.util.LinkedList$Node,SomeType20,java.util.ArrayList,java.lang.Object[]}, size=50003, count=39}
{net.sf.saxon.expr.Component,net.sf.saxon.style.StylesheetPackage,java.util.ArrayList,java.lang.Object[]}, size=49500, count=50}
{net.sf.saxon.expr.Component,net.sf.saxon.style.StylesheetPackage,java.util.ArrayList,java.lang.Object[]}, size=49500, count=50}
{net.sf.saxon.expr.Component,net.sf.saxon.style.StylesheetPackage,java.util.ArrayList,java.lang.Object[]}, size=49500, count=50}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$1,org.apache.ignite.internal.processors.cache.IgniteCacheOffheapManagerImpl$PendingEntriesTree,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=48675, count=55}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$CutRoot,org.apache.ignite.internal.processors.cache.IgniteCacheOffheapManagerImpl$PendingEntriesTree,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=48620, count=55}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$InitRoot,org.apache.ignite.internal.processors.cache.IgniteCacheOffheapManagerImpl$PendingEntriesTree,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=48620, count=55}
{org.apache.ignite.internal.processors.cache.persistence.tree.BPlusTree$AddRoot,org.apache.ignite.internal.processors.cache.IgniteCacheOffheapManagerImpl$PendingEntriesTree,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=48620, count=55}
{java.util.HashMap$Node,SomeType22,java.util.ArrayList,java.lang.Object[]}, size=48320, count=40}
{org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionExchangeId,org.apache.ignite.events.DiscoveryEvent,java.util.ArrayList,java.lang.Object[]}, size=48072, count=31}
{com.sun.xml.bind.v2.model.impl.FieldPropertySeed,com.sun.xml.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=48048, count=156}
{com.sun.xml.bind.v2.model.impl.FieldPropertySeed,com.sun.xml.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=48048, count=156}
{com.sun.xml.bind.v2.model.impl.FieldPropertySeed,com.sun.xml.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=48048, count=156}
{org.apache.kafka.common.metrics.Sensor,org.apache.kafka.common.metrics.Metrics,java.util.ArrayList,java.lang.Object[]}, size=47952, count=162}
{org.apache.ignite.internal.processors.cache.persistence.GridCacheOffheapManager,org.apache.ignite.internal.processors.cache.IgniteCacheOffheapManagerImpl$PendingEntriesTree,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=47300, count=55}
{com.sun.xml.bind.v2.model.impl.RuntimeAttributePropertyInfoImpl,com.sun.xml.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=46875, count=125}
{com.sun.xml.bind.v2.model.impl.RuntimeAttributePropertyInfoImpl,com.sun.xml.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=46875, count=125}
{com.sun.xml.bind.v2.model.impl.RuntimeAttributePropertyInfoImpl,com.sun.xml.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=46875, count=125}
{org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionsExchangeFuture,org.apache.ignite.events.DiscoveryEvent,java.util.ArrayList,java.lang.Object[]}, size=46708, count=31}
{SomeType32,SomeType13,java.util.ArrayList,java.lang.Object[]}, size=46446, count=38}
{SomeType10,SomeType20,java.util.ArrayList,java.lang.Object[]}, size=45140, count=19}
{com.sun.org.apache.xerces.internal.util.SymbolHash$Entry,com.sun.org.apache.xerces.internal.impl.dv.xs.XSSimpleTypeDecl,java.util.Vector,java.lang.Object[]}, size=45012, count=76}
{org.jsr166.ConcurrentLinkedDeque8$Node,org.jsr166.ConcurrentLinkedHashMap$HashEntry,java.util.ArrayList,java.lang.Object[]}, size=44160, count=290}
{org.apache.ignite.internal.marshaller.optimized.OptimizedObjectStreamRegistry$StreamHolder,org.apache.ignite.internal.marshaller.optimized.OptimizedObjectInputStream,org.apache.ignite.internal.marshaller.optimized.OptimizedObjectInputStream$HandleTable,java.lang.Object[]}, size=44120, count=122}
{SomeType47,SomeType20,java.util.ArrayList,java.lang.Object[]}, size=43925, count=19}
{com.sun.jmx.remote.util.ClassLoaderWithRepository,sun.misc.Launcher$AppClassLoader,java.util.Vector,java.lang.Object[]}, size=42586, count=2}
{org.springframework.beans.factory.support.DefaultListableBeanFactory,SomeType17,java.util.Vector,java.lang.Object[]}, size=42390, count=2}
{net.sf.saxon.expr.StringLiteral,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=42200, count=152}
{net.sf.saxon.expr.StringLiteral,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=42200, count=152}
{net.sf.saxon.expr.StringLiteral,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=42200, count=152}
{org.springframework.core.io.DefaultResourceLoader$ClassPathContextResource,SomeType17,java.util.Vector,java.lang.Object[]}, size=41732, count=2}
{org.springframework.beans.factory.support.DefaultListableBeanFactory,SomeType18,java.lang.Object[]}, size=41683, count=1}
{org.springframework.beans.factory.support.DefaultListableBeanFactory,SomeType17,java.util.Vector,java.lang.Object[]}, size=41675, count=1}
{org.springframework.context.event.SimpleApplicationEventMulticaster,SomeType17,java.util.Vector,java.lang.Object[]}, size=41652, count=2}
{org.springframework.context.support.ClassPathXmlApplicationContext,SomeType17,java.util.Vector,java.lang.Object[]}, size=41652, count=2}
{org.apache.ignite.internal.processors.cache.persistence.wal.FileWriteAheadLogManager$FileArchiver,SomeType19,java.util.Vector,java.lang.Object[]}, size=41526, count=1}
{org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi$CommunicationWorker,SomeType19,java.util.Vector,java.lang.Object[]}, size=41518, count=1}
{SomeType107,SomeType18,java.lang.Object[]}, size=41501, count=1}
{org.I0Itec.zkclient.ZkEventThread,SomeType18,java.lang.Object[]}, size=41493, count=1}
{org.apache.kafka.common.utils.KafkaThread,SomeType18,java.lang.Object[]}, size=41493, count=1}
{org.I0Itec.zkclient.ZkEventThread,SomeType18,java.lang.Object[]}, size=41493, count=1}
{org.apache.kafka.common.utils.KafkaThread,SomeType18,java.lang.Object[]}, size=41493, count=1}
{org.eclipse.jetty.util.thread.ShutdownThread,SomeType19,java.util.Vector,java.lang.Object[]}, size=41486, count=1}
{SomeType108,SomeType18,java.lang.Object[]}, size=41436, count=1}
{org.apache.ignite.internal.managers.deployment.GridDeployment,SomeType19,java.util.Vector,java.lang.Object[]}, size=41436, count=1}
{org.springframework.context.annotation.ConfigurationClassPostProcessor,SomeType18,java.lang.Object[]}, size=41428, count=1}
{org.springframework.context.annotation.ConfigurationClassPostProcessor,SomeType17,java.util.Vector,java.lang.Object[]}, size=41420, count=1}
{SomeType79,SomeType17,java.util.Vector,java.lang.Object[]}, size=41419, count=1}
{org.eclipse.jetty.util.thread.ScheduledExecutorScheduler,SomeType19,java.util.Vector,java.lang.Object[]}, size=41403, count=1}
{org.springframework.core.type.classreading.MethodMetadataReadingVisitor,SomeType18,java.lang.Object[]}, size=41386, count=1}
{org.apache.ignite.internal.managers.deployment.GridDeploymentPerLoaderStore,SomeType19,java.util.Vector,java.lang.Object[]}, size=41386, count=1}
{SomeType109,SomeType17,java.util.Vector,java.lang.Object[]}, size=41370, count=1}
{org.jsr166.ConcurrentLinkedHashMap$HashEntry,SomeType19,java.util.Vector,java.lang.Object[]}, size=41362, count=1}
{SomeType110,SomeType17,java.util.Vector,java.lang.Object[]}, size=41362, count=1}
{org.apache.ignite.internal.processors.cache.GridCacheSharedContext,org.apache.ignite.internal.processors.cache.GridCacheSharedTtlCleanupManager,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=41354, count=1}
{SomeType111,SomeType17,java.util.Vector,java.lang.Object[]}, size=41354, count=1}
{org.springframework.core.io.DefaultResourceLoader$ClassPathContextResource,SomeType18,java.lang.Object[]}, size=41354, count=1}
{java.util.concurrent.ConcurrentHashMap$Node,SomeType17,java.util.Vector,java.lang.Object[]}, size=41350, count=1}
{SomeType112,SomeType17,java.util.Vector,java.lang.Object[]}, size=41347, count=1}
{org.springframework.expression.spel.support.StandardTypeLocator,SomeType18,java.lang.Object[]}, size=41346, count=1}
{SomeType113,SomeType18,java.lang.Object[]}, size=41346, count=1}
{org.springframework.core.io.ClassPathResource,SomeType17,java.util.Vector,java.lang.Object[]}, size=41346, count=1}
{SomeType114,SomeType17,java.util.Vector,java.lang.Object[]}, size=41346, count=1}
{org.springframework.expression.spel.support.StandardTypeLocator,SomeType17,java.util.Vector,java.lang.Object[]}, size=41338, count=1}
{org.springframework.core.io.DefaultResourceLoader,SomeType18,java.lang.Object[]}, size=41338, count=1}
{org.springframework.core.io.DefaultResourceLoader,SomeType17,java.util.Vector,java.lang.Object[]}, size=41330, count=1}
{SomeType78,SomeType18,java.lang.Object[]}, size=41314, count=1}
{org.springframework.context.support.ClassPathXmlApplicationContext,SomeType18,java.lang.Object[]}, size=41314, count=1}
{org.springframework.context.event.SimpleApplicationEventMulticaster,SomeType18,java.lang.Object[]}, size=41314, count=1}
{SomeType79,SomeType18,java.lang.Object[]}, size=41314, count=1}
{SomeType75,SomeType19,java.util.Vector,java.lang.Object[]}, size=41306, count=1}
{org.springframework.aop.aspectj.annotation.AnnotationAwareAspectJAutoProxyCreator,SomeType17,java.util.Vector,java.lang.Object[]}, size=41306, count=1}
{org.apache.logging.log4j.core.appender.AsyncAppender$AsyncThread,SomeType17,java.util.Vector,java.lang.Object[]}, size=41306, count=1}
{org.apache.ignite.spi.discovery.tcp.ServerImpl$RingMessageWorker,SomeType19,java.util.Vector,java.lang.Object[]}, size=41306, count=1}
{org.apache.ignite.spi.discovery.tcp.ServerImpl$TcpServer,SomeType19,java.util.Vector,java.lang.Object[]}, size=41306, count=1}
{org.springframework.context.support.ClassPathXmlApplicationContext,SomeType17,java.util.Vector,java.lang.Object[]}, size=41306, count=1}
{SomeType17,SomeType19,java.util.Vector,java.lang.Object[]}, size=41306, count=1}
{it.sauronsoftware.cron4j.TimerThread,SomeType19,java.util.Vector,java.lang.Object[]}, size=41306, count=1}
{org.springframework.context.event.SimpleApplicationEventMulticaster,SomeType17,java.util.Vector,java.lang.Object[]}, size=41306, count=1}
{org.apache.ignite.spi.discovery.tcp.ServerImpl$SocketReader,SomeType19,java.util.Vector,java.lang.Object[]}, size=41306, count=1}
{SomeType115,SomeType17,java.util.Vector,java.lang.Object[]}, size=41306, count=1}
{org.apache.ignite.internal.processors.cache.GridCacheSharedTtlCleanupManager$CleanupWorker,org.apache.ignite.internal.processors.cache.GridCacheSharedTtlCleanupManager,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=41194, count=1}
{java.util.LinkedList$Node,org.apache.ignite.internal.processors.cache.GridCacheSharedTtlCleanupManager,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=41160, count=1}
{ch.qos.logback.classic.Logger,SomeType76,ch.qos.logback.classic.spi.TurboFilterList,java.lang.Object[]}, size=40750, count=326}
{org.apache.ignite.internal.processors.cache.IgniteCacheOffheapManagerImpl$PendingEntriesTree,org.apache.ignite.internal.processors.cache.persistence.tree.reuse.ReuseListImpl,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=40040, count=55}
{java.util.concurrent.ConcurrentHashMap$Node,org.apache.kafka.common.metrics.Sensor,java.util.ArrayList,java.lang.Object[]}, size=39996, count=99}
{net.sf.saxon.pattern.ContentTypeTest,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=39285, count=81}
{net.sf.saxon.pattern.ContentTypeTest,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=39285, count=81}
{net.sf.saxon.pattern.ContentTypeTest,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=39285, count=81}
{java.lang.ref.WeakReference,java.util.logging.LogManager$RootLogger,java.util.ArrayList,java.lang.Object[]}, size=39159, count=57}
{org.apache.kafka.common.metrics.Sensor,org.apache.kafka.common.metrics.Metrics,java.util.ArrayList,java.lang.Object[]}, size=39072, count=132}
{org.apache.ignite.internal.marshaller.optimized.OptimizedObjectStreamRegistry$StreamHolder,org.apache.ignite.internal.marshaller.optimized.OptimizedObjectOutputStream,org.apache.ignite.internal.util.GridHandleTable,java.lang.Object[]}, size=38918, count=122}
{java.util.logging.Logger,java.util.logging.LogManager$RootLogger,java.util.ArrayList,java.lang.Object[]}, size=38472, count=56}
{sun.nio.ch.EPollSelectorImpl,sun.nio.ch.EPollArrayWrapper,java.util.BitSet,long[]}, size=38400, count=51}
{org.apache.ignite.internal.processors.cache.IgniteCacheOffheapManagerImpl$PendingEntriesTree,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,org.apache.ignite.internal.mem.unsafe.UnsafeMemoryProvider,long[]}, size=37345, count=55}
{org.apache.xerces.impl.xs.XSComplexTypeDecl,org.apache.xerces.impl.xs.SchemaGrammar,java.util.Vector,java.lang.Object[]}, size=36676, count=53}
{org.apache.xerces.impl.xs.XSComplexTypeDecl,org.apache.xerces.impl.xs.SchemaGrammar,java.util.Vector,java.lang.Object[]}, size=36676, count=53}
{com.sun.org.apache.xerces.internal.impl.dv.xs.XSSimpleTypeDecl,com.sun.org.apache.xerces.internal.impl.xs.SchemaGrammar,java.util.Vector,java.lang.Object[]}, size=36360, count=40}
{org.springframework.beans.factory.support.RootBeanDefinition,org.springframework.beans.MutablePropertyValues,java.util.ArrayList,java.lang.Object[]}, size=36236, count=101}
{java.util.logging.LogManager$LoggerWeakRef,java.util.logging.Logger,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=35152, count=142}
{org.apache.xerces.dom.TextImpl,org.apache.xerces.dom.PSVIElementNSImpl,org.apache.xerces.impl.xs.util.ObjectListImpl,java.lang.Object[]}, size=34652, count=94}
{java.io.ObjectStreamClass$ClassDataSlot,java.io.ObjectStreamClass,java.io.ObjectStreamClass$FieldReflector,long[]}, size=34140, count=93}
{org.apache.ignite.spi.discovery.tcp.ServerImpl$RingMessageWorker,org.apache.ignite.spi.discovery.tcp.ServerImpl$PendingMessages,java.util.ArrayDeque,java.lang.Object[]}, size=32872, count=1}
{org.apache.logging.log4j.core.Logger$PrivateConfig,org.apache.logging.log4j.core.config.xml.XmlConfiguration,java.util.ArrayList,java.lang.Object[]}, size=32796, count=74}
{org.apache.xerces.dom.AttributeMap,org.apache.xerces.dom.PSVIElementNSImpl,org.apache.xerces.impl.xs.util.ObjectListImpl,java.lang.Object[]}, size=32178, count=93}
{java.util.ResourceBundle$LoaderReference,sun.misc.Launcher$AppClassLoader,java.util.Vector,java.lang.Object[]}, size=31926, count=3}
{java.util.LinkedHashMap$Entry,com.sun.xml.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=31248, count=93}
{java.util.LinkedHashMap$Entry,com.sun.xml.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=31248, count=93}
{java.util.LinkedHashMap$Entry,com.sun.xml.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=31248, count=93}
{scala.util.parsing.combinator.Parsers$$anon$3,scala.util.parsing.json.Lexer,scala.collection.mutable.HashSet,java.lang.Object[]}, size=31154, count=37}
{scala.util.parsing.combinator.Parsers$$anon$3,scala.util.parsing.json.Lexer,scala.collection.mutable.HashSet,java.lang.Object[]}, size=31154, count=37}
{scala.util.parsing.combinator.Parsers$$anon$3,scala.util.parsing.json.Lexer,scala.collection.mutable.HashSet,java.lang.Object[]}, size=31154, count=37}
{java.lang.invoke.LambdaForm$Name,java.lang.invoke.LambdaForm$BasicType,sun.invoke.util.Wrapper,java.lang.Object[]}, size=31108, count=154}
{SomeType,SomeType22,java.util.ArrayList,java.lang.Object[]}, size=30904, count=24}
{org.dom4j.tree.DefaultElement,org.dom4j.tree.DefaultElement,java.util.ArrayList,java.lang.Object[]}, size=30728, count=57}
{org.apache.xerces.dom.PSVIElementNSImpl,org.apache.xerces.dom.AttributeMap,java.util.ArrayList,java.lang.Object[]}, size=30690, count=93}
{org.apache.xerces.impl.xs.XSComplexTypeDecl,org.apache.xerces.impl.xs.SchemaGrammar,java.util.Vector,java.lang.Object[]}, size=30448, count=44}
{java.util.LinkedHashMap$Entry,org.springframework.beans.factory.support.DisposableBeanAdapter,java.util.ArrayList,java.lang.Object[]}, size=29490, count=119}
{org.apache.xerces.util.XMLAttributesImpl$Attribute,org.apache.xerces.util.AugmentationsImpl,org.apache.xerces.util.AugmentationsImpl$SmallContainer,java.lang.Object[]}, size=28896, count=96}
{org.apache.xerces.util.XMLAttributesImpl$Attribute,org.apache.xerces.util.AugmentationsImpl,org.apache.xerces.util.AugmentationsImpl$SmallContainer,java.lang.Object[]}, size=28896, count=96}
{org.springframework.beans.factory.support.GenericBeanDefinition,org.springframework.beans.MutablePropertyValues,java.util.ArrayList,java.lang.Object[]}, size=28640, count=108}
{com.sun.xml.bind.v2.model.impl.RuntimeTypeRefImpl,com.sun.xml.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=28638, count=86}
{com.sun.xml.bind.v2.model.impl.RuntimeTypeRefImpl,com.sun.xml.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=28638, count=86}
{com.sun.xml.bind.v2.model.impl.RuntimeTypeRefImpl,com.sun.xml.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=28638, count=86}
{org.apache.kafka.common.metrics.Sensor,org.apache.kafka.common.metrics.Metrics,java.util.ArrayList,java.lang.Object[]}, size=28416, count=96}
{java.util.WeakHashMap$Entry,sun.net.www.protocol.jar.URLJarFile,java.util.ArrayDeque,java.lang.Object[]}, size=27676, count=74}
{net.sf.saxon.functions.StringJoin,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=27440, count=92}
{net.sf.saxon.functions.StringJoin,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=27440, count=92}
{net.sf.saxon.functions.StringJoin,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=27440, count=92}
{net.sf.saxon.expr.instruct.FixedAttribute,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=26793, count=87}
{net.sf.saxon.expr.instruct.FixedAttribute,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=26793, count=87}
{net.sf.saxon.expr.instruct.FixedAttribute,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=26793, count=87}
{net.sf.saxon.expr.AxisExpression,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=26773, count=89}
{net.sf.saxon.expr.AxisExpression,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=26773, count=89}
{net.sf.saxon.expr.AxisExpression,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=26773, count=89}
{net.sf.saxon.style.LiteralResultElement,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=26413, count=61}
{net.sf.saxon.style.LiteralResultElement,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=26413, count=61}
{net.sf.saxon.style.LiteralResultElement,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=26413, count=61}
{com.sun.org.apache.xerces.internal.util.SymbolHash$Entry,com.sun.org.apache.xerces.internal.impl.xs.XSComplexTypeDecl,com.sun.org.apache.xerces.internal.impl.xs.models.XSDFACM,java.lang.Object[]}, size=26036, count=70}
{java.util.concurrent.ConcurrentHashMap$Node,ch.qos.logback.classic.Logger,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=25584, count=136}
{org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionsExchangeFuture$8,org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionsExchangeFuture,java.util.ArrayList,java.lang.Object[]}, size=25578, count=14}
{org.h2.index.MetaIndex,org.h2.engine.Database,org.h2.util.BitField,long[]}, size=25344, count=9}
{net.sf.saxon.expr.AtomicSequenceConverter,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=25217, count=87}
{net.sf.saxon.expr.AtomicSequenceConverter,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=25217, count=87}
{net.sf.saxon.expr.AtomicSequenceConverter,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=25217, count=87}
{net.sf.saxon.style.XSLTemplate,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=24800, count=50}
{net.sf.saxon.style.XSLTemplate,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=24800, count=50}
{net.sf.saxon.style.XSLTemplate,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=24800, count=50}
{org.springframework.beans.factory.support.GenericBeanDefinition,org.springframework.beans.MutablePropertyValues,java.util.ArrayList,java.lang.Object[]}, size=24784, count=93}
{java.util.HashMap$Node,java.util.Collections$UnmodifiableRandomAccessList,java.util.ArrayList,java.lang.Object[]}, size=24672, count=64}
{org.h2.expression.Aggregate,org.h2.command.dml.Select,java.util.ArrayList,java.lang.Object[]}, size=24570, count=35}
{net.sf.saxon.style.LiteralResultElement,net.sf.saxon.style.LiteralResultElement,java.util.ArrayList,java.lang.Object[]}, size=24424, count=48}
{net.sf.saxon.style.LiteralResultElement,net.sf.saxon.style.LiteralResultElement,java.util.ArrayList,java.lang.Object[]}, size=24424, count=48}
{net.sf.saxon.style.LiteralResultElement,net.sf.saxon.style.LiteralResultElement,java.util.ArrayList,java.lang.Object[]}, size=24424, count=48}
{SomeType96,SomeType13,java.util.ArrayList,java.lang.Object[]}, size=24362, count=11}
{org.apache.ignite.events.DiscoveryEvent,org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode,java.util.ArrayList,java.lang.Object[]}, size=24184, count=32}
{SomeType22,SomeType14,java.util.ArrayList,java.lang.Object[]}, size=24024, count=8}
{org.dom4j.tree.DefaultText,org.dom4j.tree.DefaultElement,java.util.ArrayList,java.lang.Object[]}, size=23680, count=34}
{net.sf.saxon.style.ExpressionContext,net.sf.saxon.style.LiteralResultElement,java.util.ArrayList,java.lang.Object[]}, size=23424, count=61}
{net.sf.saxon.style.ExpressionContext,net.sf.saxon.style.LiteralResultElement,java.util.ArrayList,java.lang.Object[]}, size=23424, count=61}
{java.util.Hashtable$Entry,org.apache.xerces.impl.xs.traversers.XSDocumentInfo,java.util.Vector,java.lang.Object[]}, size=22932, count=78}
{org.apache.xerces.util.XMLAttributesImpl$Attribute,org.apache.xerces.util.AugmentationsImpl,org.apache.xerces.util.AugmentationsImpl$SmallContainer,java.lang.Object[]}, size=22876, count=76}
{net.sf.saxon.expr.instruct.FixedElement,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=22555, count=65}
{net.sf.saxon.expr.instruct.FixedElement,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=22555, count=65}
{net.sf.saxon.expr.instruct.FixedElement,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=22555, count=65}
{net.sf.saxon.style.ExpressionContext,net.sf.saxon.style.LiteralResultElement,java.util.ArrayList,java.lang.Object[]}, size=21824, count=61}
{SomeType116,SomeType21,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=21760, count=1}
{java.util.Hashtable$Entry,org.apache.xerces.impl.xs.traversers.XSDocumentInfo,java.util.Vector,java.lang.Object[]}, size=21756, count=74}
{java.util.concurrent.ConcurrentHashMap$Node,java.lang.ref.SoftReference,java.io.ObjectStreamClass$FieldReflector,long[]}, size=21624, count=77}
{javax.management.remote.rmi.RMIConnectorServer,sun.misc.Launcher$AppClassLoader,java.util.Vector,java.lang.Object[]}, size=21388, count=2}
{com.sun.jmx.mbeanserver.ClassLoaderRepositorySupport$LoaderEntry,sun.misc.Launcher$AppClassLoader,java.util.Vector,java.lang.Object[]}, size=21236, count=2}
{javax.management.remote.rmi.RMIConnectionImpl$CombinedClassLoader$ClassLoaderWrapper,sun.misc.Launcher$AppClassLoader,java.util.Vector,java.lang.Object[]}, size=21172, count=2}
{javax.management.remote.rmi.RMIJRMPServerImpl,sun.misc.Launcher$AppClassLoader,java.util.Vector,java.lang.Object[]}, size=21172, count=2}
{javax.management.remote.rmi.RMIConnectionImpl$CombinedClassLoader,sun.misc.Launcher$AppClassLoader,java.util.Vector,java.lang.Object[]}, size=21172, count=2}
{javax.management.remote.rmi.RMIConnectionImpl,sun.misc.Launcher$AppClassLoader,java.util.Vector,java.lang.Object[]}, size=21172, count=2}
{org.apache.zookeeper.ClientCnxn$SendThread,SomeType17,java.util.Vector,java.lang.Object[]}, size=21051, count=1}
{org.apache.zookeeper.ClientCnxn$EventThread,SomeType17,java.util.Vector,java.lang.Object[]}, size=21031, count=1}
{SomeType117,SomeType17,java.util.Vector,java.lang.Object[]}, size=21022, count=1}
{org.apache.kafka.common.utils.KafkaThread,SomeType17,java.util.Vector,java.lang.Object[]}, size=21005, count=1}
{org.apache.logging.log4j.spi.Provider,SomeType17,java.util.Vector,java.lang.Object[]}, size=20882, count=1}
{org.springframework.expression.spel.support.StandardTypeLocator,SomeType17,java.util.Vector,java.lang.Object[]}, size=20858, count=1}
{SomeType45,SomeType22,java.util.ArrayList,java.lang.Object[]}, size=20624, count=16}
{net.sf.saxon.style.ExpressionContext,net.sf.saxon.style.XSLTemplate,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=20050, count=50}
{net.sf.saxon.style.ExpressionContext,net.sf.saxon.style.XSLTemplate,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=20050, count=50}
{net.sf.saxon.style.ExpressionContext,net.sf.saxon.style.XSLTemplate,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=20050, count=50}
{net.sf.saxon.style.XSLApplyTemplates,net.sf.saxon.trans.Mode,net.sf.saxon.z.IntHashMap,java.lang.Object[]}, size=19954, count=11}
{net.sf.saxon.style.XSLApplyTemplates,net.sf.saxon.trans.Mode,net.sf.saxon.z.IntHashMap,java.lang.Object[]}, size=19954, count=11}
{net.sf.saxon.style.XSLApplyTemplates,net.sf.saxon.trans.Mode,net.sf.saxon.z.IntHashMap,java.lang.Object[]}, size=19954, count=11}
{SomeType93,SomeType14,java.util.ArrayList,java.lang.Object[]}, size=19643, count=15}
{net.sf.saxon.expr.instruct.ApplyTemplates,net.sf.saxon.trans.Mode,net.sf.saxon.z.IntHashMap,java.lang.Object[]}, size=18832, count=11}
{net.sf.saxon.expr.instruct.ApplyTemplates,net.sf.saxon.trans.Mode,net.sf.saxon.z.IntHashMap,java.lang.Object[]}, size=18832, count=11}
{net.sf.saxon.expr.instruct.ApplyTemplates,net.sf.saxon.trans.Mode,net.sf.saxon.z.IntHashMap,java.lang.Object[]}, size=18832, count=11}
{org.apache.kafka.common.metrics.KafkaMetric,org.apache.kafka.common.metrics.stats.Avg,java.util.ArrayList,java.lang.Object[]}, size=18696, count=114}
{org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtCacheEntry,org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtLocalPartition,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=18483, count=61}
{com.sun.org.apache.xerces.internal.impl.xs.XSAttributeDecl,com.sun.org.apache.xerces.internal.impl.dv.xs.XSSimpleTypeDecl,java.util.Vector,java.lang.Object[]}, size=18422, count=30}
{org.apache.ignite.internal.util.lang.gridfunc.TransformCollectionView,java.util.Collections$UnmodifiableRandomAccessList,java.util.ArrayList,java.lang.Object[]}, size=17904, count=6}
{org.apache.ignite.internal.util.nio.GridNioServer$DirectNioClientWorker,org.apache.ignite.internal.util.nio.GridNioServer,java.util.ArrayList,java.lang.Object[]}, size=16938, count=18}
{ch.qos.logback.classic.Logger,ch.qos.logback.classic.Logger,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=16162, count=106}
{net.sf.saxon.expr.Atomizer,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=16120, count=56}
{net.sf.saxon.expr.Atomizer,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=16120, count=56}
{net.sf.saxon.expr.Atomizer,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=16120, count=56}
{com.sun.xml.internal.bind.v2.model.impl.GetterSetterPropertySeed,com.sun.xml.internal.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.internal.FinalArrayList,java.lang.Object[]}, size=16028, count=35}
{org.dom4j.tree.DefaultAttribute,org.dom4j.tree.DefaultElement,java.util.ArrayList,java.lang.Object[]}, size=16008, count=87}
{java.util.HashMap$Node,org.apache.ignite.internal.util.tostring.GridToStringClassDescriptor,java.util.ArrayList,java.lang.Object[]}, size=15540, count=65}
{ch.qos.logback.core.joran.event.StartEvent,ch.qos.logback.core.joran.spi.ElementPath,java.util.ArrayList,java.lang.Object[]}, size=15456, count=69}
{ch.qos.logback.core.joran.event.StartEvent,ch.qos.logback.core.joran.spi.ElementPath,java.util.ArrayList,java.lang.Object[]}, size=15456, count=69}
{ch.qos.logback.core.joran.event.StartEvent,ch.qos.logback.core.joran.spi.ElementPath,java.util.ArrayList,java.lang.Object[]}, size=15456, count=69}
{net.sf.saxon.style.XSLAttribute,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=15355, count=37}
{net.sf.saxon.style.XSLAttribute,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=15355, count=37}
{net.sf.saxon.style.XSLAttribute,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=15355, count=37}
{java.util.Hashtable$Entry,org.apache.xerces.impl.xs.traversers.XSDocumentInfo,java.util.Vector,java.lang.Object[]}, size=15288, count=52}
{SomeType118,SomeType5,java.util.ArrayList,java.lang.Object[]}, size=15170, count=37}
{org.apache.kafka.common.metrics.KafkaMetric,org.apache.kafka.common.metrics.stats.Avg,java.util.ArrayList,java.lang.Object[]}, size=15088, count=92}
{sun.reflect.generics.repository.FieldRepository,sun.reflect.generics.tree.ClassTypeSignature,java.util.ArrayList,java.lang.Object[]}, size=14880, count=93}
{com.sun.org.apache.xerces.internal.impl.xs.XSAttributeDecl,com.sun.org.apache.xerces.internal.impl.xs.XSComplexTypeDecl,com.sun.org.apache.xerces.internal.impl.xs.models.XSDFACM,java.lang.Object[]}, size=14274, count=33}
{java.util.Hashtable$Entry,org.apache.xerces.impl.xs.traversers.XSDocumentInfo,java.util.Stack,java.lang.Object[]}, size=14264, count=78}
{org.apache.logging.log4j.core.Logger,org.apache.logging.log4j.core.LoggerContext,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=14208, count=74}
{org.apache.kafka.common.metrics.KafkaMetric,org.apache.kafka.common.metrics.stats.Max,java.util.ArrayList,java.lang.Object[]}, size=14104, count=86}
{java.util.HashMap$Node,com.sun.xml.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=14080, count=44}
{java.util.HashMap$Node,com.sun.xml.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=14080, count=44}
{java.util.HashMap$Node,com.sun.xml.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=14080, count=44}
{net.sf.saxon.trans.Rule,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=13860, count=45}
{net.sf.saxon.trans.Rule,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=13860, count=45}
{org.apache.kafka.common.metrics.stats.Rate,org.apache.kafka.common.metrics.stats.Rate$SampledTotal,java.util.ArrayList,java.lang.Object[]}, size=13860, count=99}
{net.sf.saxon.trans.Rule,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=13860, count=45}
{net.sf.saxon.style.XSLTemplate,net.sf.saxon.style.XSLPackage,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=13700, count=50}
{net.sf.saxon.style.XSLTemplate,net.sf.saxon.style.XSLPackage,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=13700, count=50}
{net.sf.saxon.style.XSLTemplate,net.sf.saxon.style.XSLPackage,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=13700, count=50}
{com.sun.org.apache.xerces.internal.util.AugmentationsImpl$SmallContainer,com.sun.org.apache.xerces.internal.util.AugmentationsImpl,com.sun.org.apache.xerces.internal.util.AugmentationsImpl$SmallContainer,java.lang.Object[]}, size=13700, count=25}
{com.sun.xml.internal.bind.v2.model.impl.RuntimeElementPropertyInfoImpl,com.sun.xml.internal.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.internal.FinalArrayList,java.lang.Object[]}, size=13616, count=32}
{org.apache.logging.log4j.core.pattern.LiteralPatternConverter,org.apache.logging.log4j.core.config.xml.XmlConfiguration,java.util.ArrayList,java.lang.Object[]}, size=13536, count=32}
{java.util.Hashtable$Entry,org.apache.xerces.impl.xs.traversers.XSDocumentInfo,java.util.Stack,java.lang.Object[]}, size=13528, count=74}
{java.util.WeakHashMap$Entry,javax.management.MBeanInfo,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=13511, count=59}
{org.h2.expression.ConditionAndOr,org.h2.expression.ConditionIn,java.util.ArrayList,java.lang.Object[]}, size=13478, count=75}
{net.sf.saxon.expr.ContextItemExpression,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=13365, count=51}
{net.sf.saxon.expr.ContextItemExpression,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=13365, count=51}
{net.sf.saxon.expr.ContextItemExpression,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=13365, count=51}
{com.sun.xml.internal.bind.v2.model.impl.RuntimeElementPropertyInfoImpl,java.util.Collections$UnmodifiableRandomAccessList,com.sun.istack.internal.FinalArrayList,java.lang.Object[]}, size=13216, count=32}
{net.sf.saxon.expr.StringLiteral,net.sf.saxon.style.XSLAttribute,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=13036, count=38}
{net.sf.saxon.expr.StringLiteral,net.sf.saxon.style.XSLAttribute,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=13036, count=38}
{net.sf.saxon.expr.StringLiteral,net.sf.saxon.style.XSLAttribute,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=13036, count=38}
{net.sf.saxon.expr.instruct.Template,net.sf.saxon.expr.Component,java.util.ArrayList,java.lang.Object[]}, size=13030, count=50}
{net.sf.saxon.expr.instruct.Template,net.sf.saxon.expr.Component,java.util.ArrayList,java.lang.Object[]}, size=13030, count=50}
{net.sf.saxon.expr.instruct.Template,net.sf.saxon.expr.Component,java.util.ArrayList,java.lang.Object[]}, size=13030, count=50}
{org.apache.ignite.internal.processors.cache.ExchangeDiscoveryEvents,org.apache.ignite.events.DiscoveryEvent,java.util.ArrayList,java.lang.Object[]}, size=12832, count=8}
{com.sun.xml.bind.v2.model.impl.RuntimeElementPropertyInfoImpl,java.util.Collections$UnmodifiableRandomAccessList,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=12803, count=31}
{com.sun.xml.bind.v2.model.impl.RuntimeElementPropertyInfoImpl,java.util.Collections$UnmodifiableRandomAccessList,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=12803, count=31}
{com.sun.xml.bind.v2.model.impl.RuntimeElementPropertyInfoImpl,java.util.Collections$UnmodifiableRandomAccessList,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=12803, count=31}
{org.apache.kafka.common.network.Selector$SelectorMetrics,org.apache.kafka.common.metrics.Sensor,java.util.ArrayList,java.lang.Object[]}, size=12600, count=5}
{com.sun.xml.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.xml.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=12480, count=30}
{com.sun.xml.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.xml.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=12480, count=30}
{com.sun.xml.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.xml.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=12480, count=30}
{ch.qos.logback.core.spi.ContextAwareBase,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=12424, count=28}
{org.apache.kafka.common.metrics.stats.Rate,org.apache.kafka.common.metrics.stats.Count,java.util.ArrayList,java.lang.Object[]}, size=12320, count=88}
{net.sf.saxon.style.ExpressionContext,net.sf.saxon.style.XSLAttribute,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=12250, count=37}
{net.sf.saxon.style.ExpressionContext,net.sf.saxon.style.XSLAttribute,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=12250, count=37}
{net.sf.saxon.style.ExpressionContext,net.sf.saxon.style.XSLAttribute,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=12250, count=37}
{net.sf.saxon.expr.SimpleStepExpression,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=12195, count=45}
{net.sf.saxon.expr.SimpleStepExpression,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=12195, count=45}
{net.sf.saxon.expr.SimpleStepExpression,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=12195, count=45}
{org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTxLocal,org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTxPrepareFuture,java.util.ArrayList,java.lang.Object[]}, size=12155, count=5}
{org.apache.ignite.internal.marshaller.optimized.OptimizedClassDescriptor,org.apache.ignite.internal.MarshallerContextImpl,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=12104, count=53}
{org.apache.xerces.dom.PSVIAttrNSImpl,org.apache.xerces.dom.PSVIElementNSImpl,org.apache.xerces.impl.xs.util.ObjectListImpl,java.lang.Object[]}, size=12068, count=28}
{net.sf.saxon.style.XSLValueOf,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=12028, count=31}
{net.sf.saxon.style.XSLValueOf,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=12028, count=31}
{net.sf.saxon.style.XSLValueOf,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=12028, count=31}
{net.sf.saxon.expr.Component,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=11750, count=50}
{net.sf.saxon.expr.Component,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=11750, count=50}
{net.sf.saxon.expr.Component,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=11750, count=50}
{com.sun.jmx.mbeanserver.PerInterface,javax.management.MBeanInfo,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=11686, count=54}
{SomeType15,SomeType14,java.util.ArrayList,java.lang.Object[]}, size=11574, count=7}
{net.sf.saxon.expr.instruct.Block,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=11520, count=40}
{net.sf.saxon.expr.instruct.Block,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=11520, count=40}
{net.sf.saxon.expr.instruct.Block,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=11520, count=40}
{org.apache.kafka.common.metrics.KafkaMetric,org.apache.kafka.common.metrics.stats.Max,java.util.ArrayList,java.lang.Object[]}, size=11480, count=70}
{org.jsr166.ConcurrentHashMap8$Node,org.apache.ignite.internal.marshaller.optimized.OptimizedClassDescriptor,java.util.ArrayList,java.lang.Object[]}, size=11432, count=24}
{org.apache.kafka.common.metrics.stats.Rate,org.apache.kafka.common.metrics.stats.Rate$SampledTotal,java.util.ArrayList,java.lang.Object[]}, size=11340, count=81}
{net.sf.saxon.style.XSLTemplate,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=11250, count=50}
{net.sf.saxon.style.XSLTemplate,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=11250, count=50}
{net.sf.saxon.style.XSLTemplate,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=11250, count=50}
{org.postgresql.jdbc2.TimestampUtils,java.util.GregorianCalendar,sun.util.calendar.ZoneInfo,long[]}, size=11100, count=12}
{net.sf.saxon.functions.StringFn,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=10923, count=37}
{net.sf.saxon.functions.StringFn,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=10923, count=37}
{net.sf.saxon.functions.StringFn,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=10923, count=37}
{org.apache.kafka.common.metrics.KafkaMetric,org.apache.kafka.common.metrics.stats.Avg,java.util.ArrayList,java.lang.Object[]}, size=10824, count=66}
{java.util.TimerThread,sun.misc.Launcher$AppClassLoader,java.util.Vector,java.lang.Object[]}, size=10766, count=1}
{org.apache.logging.log4j.spi.Provider,sun.misc.Launcher$AppClassLoader,java.util.Vector,java.lang.Object[]}, size=10642, count=1}
{java.lang.reflect.WeakCache$CacheKey,sun.misc.Launcher$AppClassLoader,java.util.Vector,java.lang.Object[]}, size=10638, count=1}
{sun.misc.Launcher,sun.misc.Launcher$AppClassLoader,java.util.Vector,java.lang.Object[]}, size=10610, count=1}
{SomeType119,SomeType22,java.util.ArrayList,java.lang.Object[]}, size=10592, count=8}
{SomeType19,sun.misc.Launcher$AppClassLoader,java.util.Vector,java.lang.Object[]}, size=10586, count=1}
{sun.reflect.misc.MethodUtil,sun.misc.Launcher$AppClassLoader,java.util.Vector,java.lang.Object[]}, size=10586, count=1}
{java.util.ResourceBundle$RBClassLoader,sun.misc.Launcher$AppClassLoader,java.util.Vector,java.lang.Object[]}, size=10586, count=1}
{SomeType17,sun.misc.Launcher$AppClassLoader,java.util.Vector,java.lang.Object[]}, size=10586, count=1}
{org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTxPrepareFuture$PrepareTimeoutObject,org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTxPrepareFuture,java.util.ArrayList,java.lang.Object[]}, size=10570, count=5}
{java.util.concurrent.ConcurrentHashMap$Node,ch.qos.logback.classic.Logger,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=10481, count=57}
{SomeType4,SomeType22,java.util.ArrayList,java.lang.Object[]}, size=10312, count=8}
{SomeType44,SomeType22,java.util.ArrayList,java.lang.Object[]}, size=10312, count=8}
{SomeType43,SomeType22,java.util.ArrayList,java.lang.Object[]}, size=10312, count=8}
{java.util.TreeMap$Entry,javax.management.openmbean.SimpleType,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=10260, count=54}
{java.util.logging.Logger,java.util.logging.LogManager$RootLogger,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=10248, count=56}
{java.net.SocketOutputStream,java.io.FileDescriptor,java.util.ArrayList,java.lang.Object[]}, size=10168, count=41}
{java.net.SocketInputStream,java.io.FileDescriptor,java.util.ArrayList,java.lang.Object[]}, size=10168, count=41}
{org.apache.xerces.jaxp.SAXParserImpl$JAXPSAXParser,org.apache.xerces.parsers.XIncludeAwareParserConfiguration,java.util.ArrayList,java.lang.Object[]}, size=10164, count=7}
{org.apache.xerces.jaxp.SAXParserImpl$JAXPSAXParser,org.apache.xerces.parsers.XIncludeAwareParserConfiguration,java.util.ArrayList,java.lang.Object[]}, size=10164, count=7}
{org.apache.xerces.jaxp.SAXParserImpl$JAXPSAXParser,org.apache.xerces.parsers.XIncludeAwareParserConfiguration,java.util.ArrayList,java.lang.Object[]}, size=10164, count=7}
{java.util.LinkedHashMap$Entry,sun.reflect.annotation.TypeNotPresentExceptionProxy,java.lang.ClassNotFoundException,java.lang.Object[]}, size=10120, count=46}
{org.apache.kafka.common.network.Selector$SelectorMetrics,org.apache.kafka.common.metrics.Sensor,java.util.ArrayList,java.lang.Object[]}, size=10080, count=4}
{net.sf.saxon.expr.instruct.CallTemplate,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=10059, count=21}
{net.sf.saxon.expr.instruct.CallTemplate,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=10059, count=21}
{net.sf.saxon.expr.instruct.CallTemplate,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=10059, count=21}
{org.postgresql.core.v3.ProtocolConnectionImpl,org.postgresql.core.v3.QueryExecutorImpl,java.util.ArrayList,java.lang.Object[]}, size=10020, count=12}
{org.apache.kafka.common.metrics.stats.Rate,org.apache.kafka.common.metrics.stats.Count,java.util.ArrayList,java.lang.Object[]}, size=9940, count=71}
{java.util.LinkedHashMap$Entry,org.springframework.beans.factory.support.DisposableBeanAdapter,java.util.ArrayList,java.lang.Object[]}, size=9774, count=41}
{org.apache.ignite.internal.marshaller.optimized.OptimizedClassDescriptor,org.apache.ignite.internal.marshaller.optimized.OptimizedClassDescriptor$Fields,java.util.ArrayList,java.lang.Object[]}, size=9720, count=24}
{java.util.concurrent.ConcurrentHashMap$Node,SomeType22,java.util.ArrayList,java.lang.Object[]}, size=9664, count=8}
{java.util.LinkedList$Node,SomeType22,java.util.ArrayList,java.lang.Object[]}, size=9632, count=8}
{java.util.Hashtable$Entry,org.apache.xerces.impl.xs.traversers.XSDocumentInfo,java.util.Stack,java.lang.Object[]}, size=9568, count=52}
{java.lang.ClassNotFoundException,java.util.Collections$UnmodifiableRandomAccessList,java.util.ArrayList,java.lang.Object[]}, size=9568, count=46}
{ch.qos.logback.core.spi.ContextAwareBase,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=9464, count=28}
{ch.qos.logback.core.spi.ContextAwareBase,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=9464, count=28}
{net.sf.saxon.style.ExpressionContext,net.sf.saxon.style.XSLValueOf,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=9393, count=31}
{net.sf.saxon.style.ExpressionContext,net.sf.saxon.style.XSLValueOf,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=9393, count=31}
{net.sf.saxon.style.ExpressionContext,net.sf.saxon.style.XSLValueOf,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=9393, count=31}
{com.sun.jmx.mbeanserver.MXBeanSupport,javax.management.MBeanInfo,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=9355, count=43}
{SomeType22,SomeType22,java.util.ArrayList,java.lang.Object[]}, size=9312, count=8}
{SomeType96,SomeType22,java.util.ArrayList,java.lang.Object[]}, size=9312, count=8}
{org.dom4j.tree.DefaultDocument,org.dom4j.tree.DefaultElement,java.util.ArrayList,java.lang.Object[]}, size=9280, count=10}
{org.apache.xerces.impl.XMLEntityScanner,org.apache.xerces.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=8993, count=17}
{org.apache.xerces.impl.XMLEntityScanner,org.apache.xerces.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=8993, count=17}
{javax.management.openmbean.OpenMBeanAttributeInfoSupport,javax.management.openmbean.SimpleType,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=8880, count=37}
{org.springframework.context.annotation.ScannedGenericBeanDefinition,org.springframework.beans.MutablePropertyValues,java.util.ArrayList,java.lang.Object[]}, size=8712, count=33}
{SomeType3,SomeType22,java.util.ArrayList,java.lang.Object[]}, size=8693, count=7}
{com.sun.xml.bind.v2.model.impl.RuntimeElementPropertyInfoImpl,com.sun.xml.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=8556, count=31}
{com.sun.xml.bind.v2.model.impl.RuntimeElementPropertyInfoImpl,com.sun.xml.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=8556, count=31}
{com.sun.xml.bind.v2.model.impl.RuntimeElementPropertyInfoImpl,com.sun.xml.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=8556, count=31}
{net.sf.saxon.style.XSLElement,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=8480, count=20}
{net.sf.saxon.style.XSLElement,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=8480, count=20}
{net.sf.saxon.style.XSLElement,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=8480, count=20}
{net.sf.saxon.trans.RuleManager,net.sf.saxon.trans.Mode,net.sf.saxon.z.IntHashMap,java.lang.Object[]}, size=8345, count=5}
{net.sf.saxon.trans.RuleManager,net.sf.saxon.trans.Mode,net.sf.saxon.z.IntHashMap,java.lang.Object[]}, size=8345, count=5}
{net.sf.saxon.trans.RuleManager,net.sf.saxon.trans.Mode,net.sf.saxon.z.IntHashMap,java.lang.Object[]}, size=8345, count=5}
{org.h2.engine.Setting,org.h2.engine.Database,org.h2.util.BitField,long[]}, size=8310, count=3}
{org.apache.kafka.common.metrics.stats.Rate,org.apache.kafka.common.metrics.stats.Rate$SampledTotal,java.util.ArrayList,java.lang.Object[]}, size=8260, count=59}
{net.sf.saxon.expr.instruct.Template,net.sf.saxon.expr.instruct.SlotManager,java.util.ArrayList,java.lang.Object[]}, size=8200, count=50}
{net.sf.saxon.expr.instruct.Template,net.sf.saxon.expr.instruct.SlotManager,java.util.ArrayList,java.lang.Object[]}, size=8200, count=50}
{net.sf.saxon.style.XSLTemplate,net.sf.saxon.expr.instruct.SlotManager,java.util.ArrayList,java.lang.Object[]}, size=8200, count=50}
{net.sf.saxon.expr.instruct.Template,net.sf.saxon.expr.instruct.SlotManager,java.util.ArrayList,java.lang.Object[]}, size=8200, count=50}
{net.sf.saxon.style.XSLTemplate,net.sf.saxon.expr.instruct.SlotManager,java.util.ArrayList,java.lang.Object[]}, size=8200, count=50}
{org.apache.kafka.common.metrics.KafkaMetric,org.apache.kafka.common.metrics.stats.Max,java.util.ArrayList,java.lang.Object[]}, size=8200, count=50}
{net.sf.saxon.style.XSLTemplate,net.sf.saxon.expr.instruct.SlotManager,java.util.ArrayList,java.lang.Object[]}, size=8200, count=50}
{java.security.ProtectionDomain,oracle.jrockit.jfr.events.EventHandlerCreator$EventInfoClassLoader,java.util.Vector,java.lang.Object[]}, size=8136, count=18}
{org.dom4j.tree.DefaultDocument,org.dom4j.tree.DefaultElement,java.util.ArrayList,java.lang.Object[]}, size=8128, count=14}
{net.sf.saxon.expr.CastExpression,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=8083, count=27}
{net.sf.saxon.expr.CastExpression,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=8083, count=27}
{net.sf.saxon.expr.CastExpression,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=8083, count=27}
{java.util.ResourceBundle$LoaderReference,java.util.ResourceBundle$RBClassLoader,java.util.Vector,java.lang.Object[]}, size=7938, count=18}
{org.apache.xerces.impl.XMLEntityScanner,org.apache.xerces.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=7935, count=15}
{org.apache.ignite.internal.util.IgniteExceptionRegistry$ExceptionInfo,java.net.ConnectException,java.net.ConnectException,java.lang.Object[]}, size=7920, count=33}
{java.util.WeakHashMap$Entry,java.lang.ref.WeakReference,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=7884, count=46}
{SomeType10,SomeType22,java.util.ArrayList,java.lang.Object[]}, size=7620, count=6}
{org.apache.kafka.common.network.Selector$SelectorMetrics,org.apache.kafka.common.metrics.Sensor,java.util.ArrayList,java.lang.Object[]}, size=7560, count=3}
{scala.util.parsing.combinator.token.StdTokens$Keyword,scala.util.parsing.json.Lexer,scala.collection.mutable.HashSet,java.lang.Object[]}, size=7506, count=9}
{scala.util.parsing.combinator.token.StdTokens$Keyword,scala.util.parsing.json.Lexer,scala.collection.mutable.HashSet,java.lang.Object[]}, size=7506, count=9}
{scala.util.parsing.combinator.token.StdTokens$Keyword,scala.util.parsing.json.Lexer,scala.collection.mutable.HashSet,java.lang.Object[]}, size=7506, count=9}
{org.apache.kafka.clients.consumer.internals.Fetcher$FetchManagerMetrics,org.apache.kafka.common.metrics.Sensor,java.util.ArrayList,java.lang.Object[]}, size=7488, count=4}
{net.sf.saxon.style.XSLAttribute,net.sf.saxon.style.XSLElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=7475, count=25}
{net.sf.saxon.style.XSLAttribute,net.sf.saxon.style.XSLElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=7475, count=25}
{net.sf.saxon.style.XSLAttribute,net.sf.saxon.style.XSLElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=7475, count=25}
{org.postgresql.jdbc2.TimestampUtils,java.util.GregorianCalendar,sun.util.calendar.ZoneInfo,long[]}, size=7400, count=8}
{java.util.concurrent.ConcurrentLinkedDeque$Node,org.apache.ignite.internal.util.IgniteExceptionRegistry$ExceptionInfo,java.net.ConnectException,java.lang.Object[]}, size=7392, count=33}
{org.apache.xerces.impl.XMLEntityManager,org.apache.xerces.util.AugmentationsImpl,org.apache.xerces.util.AugmentationsImpl$SmallContainer,java.lang.Object[]}, size=7361, count=17}
{org.apache.xerces.impl.XMLEntityManager,org.apache.xerces.util.AugmentationsImpl,org.apache.xerces.util.AugmentationsImpl$SmallContainer,java.lang.Object[]}, size=7361, count=17}
{net.sf.saxon.style.StylesheetFunctionLibrary,net.sf.saxon.style.StylesheetPackage,java.util.ArrayList,java.lang.Object[]}, size=7320, count=15}
{net.sf.saxon.style.StylesheetFunctionLibrary,net.sf.saxon.style.StylesheetPackage,java.util.ArrayList,java.lang.Object[]}, size=7320, count=15}
{net.sf.saxon.style.StylesheetFunctionLibrary,net.sf.saxon.style.StylesheetPackage,java.util.ArrayList,java.lang.Object[]}, size=7320, count=15}
{com.mchange.v2.resourcepool.BasicResourcePool,com.mchange.v2.c3p0.impl.NewPooledConnection,java.sql.SQLException,java.lang.Object[]}, size=7260, count=12}
{org.apache.ignite.internal.managers.communication.GridIoMessage,org.apache.ignite.internal.processors.query.h2.twostep.messages.GridQueryNextPageResponse,java.util.ArrayList,java.lang.Object[]}, size=7056, count=36}
{org.apache.kafka.common.metrics.stats.Rate,org.apache.kafka.common.metrics.stats.Count,java.util.ArrayList,java.lang.Object[]}, size=7000, count=50}
{java.util.HashMap$Node,org.apache.ignite.internal.util.UUIDCollectionMessage,java.util.ArrayList,java.lang.Object[]}, size=6940, count=5}
{org.apache.xerces.impl.XMLDTDScannerImpl,org.apache.xerces.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=6934, count=12}
{org.apache.xerces.impl.XMLDTDScannerImpl,org.apache.xerces.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=6934, count=12}
{org.apache.xerces.impl.xs.traversers.XSDocumentInfo,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=6878, count=19}
{net.sf.saxon.style.XSLCallTemplate,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=6867, count=21}
{net.sf.saxon.style.XSLCallTemplate,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=6867, count=21}
{net.sf.saxon.style.XSLCallTemplate,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=6867, count=21}
{net.sf.saxon.style.XSLElement,net.sf.saxon.style.XSLTemplate,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=6859, count=19}
{net.sf.saxon.style.XSLElement,net.sf.saxon.style.XSLTemplate,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=6859, count=19}
{net.sf.saxon.style.XSLElement,net.sf.saxon.style.XSLTemplate,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=6859, count=19}
{org.apache.xerces.impl.xs.XSGroupDecl,org.apache.xerces.impl.xs.SchemaGrammar,java.util.Vector,java.lang.Object[]}, size=6798, count=11}
{org.apache.xerces.impl.xs.XSGroupDecl,org.apache.xerces.impl.xs.SchemaGrammar,java.util.Vector,java.lang.Object[]}, size=6798, count=11}
{net.sf.saxon.style.ExpressionContext,net.sf.saxon.style.XSLElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=6780, count=20}
{net.sf.saxon.style.ExpressionContext,net.sf.saxon.style.XSLElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=6780, count=20}
{net.sf.saxon.style.ExpressionContext,net.sf.saxon.style.XSLElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=6780, count=20}
{java.util.HashMap$Node,org.apache.xerces.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=6773, count=13}
{java.util.HashMap$Node,org.apache.xerces.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=6773, count=13}
{java.util.HashMap$Node,ch.qos.logback.core.joran.spi.ElementSelector,java.util.ArrayList,java.lang.Object[]}, size=6744, count=35}
{java.util.HashMap$Node,ch.qos.logback.core.joran.spi.ElementSelector,java.util.ArrayList,java.lang.Object[]}, size=6744, count=35}
{java.util.HashMap$Node,ch.qos.logback.core.joran.spi.ElementSelector,java.util.ArrayList,java.lang.Object[]}, size=6744, count=35}
{java.util.Collections$SynchronizedCollection,java.util.Collections$SynchronizedCollection,java.util.ArrayList,java.lang.Object[]}, size=6704, count=1}
{org.postgresql.core.v3.ProtocolConnectionImpl,org.postgresql.core.v3.QueryExecutorImpl,java.util.ArrayList,java.lang.Object[]}, size=6680, count=8}
{org.apache.ignite.internal.processors.query.h2.IgniteH2Indexing,java.util.Collections$SynchronizedCollection,java.util.ArrayList,java.lang.Object[]}, size=6672, count=1}
{java.net.ConnectException,java.util.Collections$UnmodifiableRandomAccessList,java.util.ArrayList,java.lang.Object[]}, size=6600, count=33}
{com.sun.org.apache.xerces.internal.util.XMLAttributesImpl$Attribute,com.sun.org.apache.xerces.internal.util.AugmentationsImpl,com.sun.org.apache.xerces.internal.util.AugmentationsImpl$SmallContainer,java.lang.Object[]}, size=6520, count=20}
{org.springframework.context.annotation.CommonAnnotationBeanPostProcessor,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=6512, count=3}
{org.apache.xerces.impl.XMLEntityManager,org.apache.xerces.util.AugmentationsImpl,org.apache.xerces.util.AugmentationsImpl$SmallContainer,java.lang.Object[]}, size=6495, count=15}
{org.apache.xerces.impl.XMLVersionDetector,org.apache.xerces.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=6396, count=12}
{org.apache.xerces.impl.XMLVersionDetector,org.apache.xerces.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=6396, count=12}
{java.lang.ref.WeakReference,java.util.logging.LogManager$RootLogger,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=6384, count=57}
{net.sf.saxon.style.ExpressionContext,net.sf.saxon.style.XSLCallTemplate,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=6384, count=21}
{net.sf.saxon.style.ExpressionContext,net.sf.saxon.style.XSLCallTemplate,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=6384, count=21}
{net.sf.saxon.style.ExpressionContext,net.sf.saxon.style.XSLCallTemplate,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=6384, count=21}
{org.apache.xerces.impl.XMLNSDocumentScannerImpl,org.apache.xerces.impl.validation.ValidationManager,java.util.ArrayList,java.lang.Object[]}, size=6332, count=12}
{org.apache.xerces.impl.XMLNSDocumentScannerImpl,org.apache.xerces.impl.validation.ValidationManager,java.util.ArrayList,java.lang.Object[]}, size=6332, count=12}
{java.util.HashMap$Node,org.apache.xerces.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=6252, count=12}
{org.apache.xerces.impl.XMLDTDScannerImpl,org.apache.xerces.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=6215, count=11}
{SomeType35,SomeType22,java.util.ArrayList,java.lang.Object[]}, size=6025, count=5}
{ch.qos.logback.classic.Logger,SomeType76,ch.qos.logback.classic.spi.TurboFilterList,java.lang.Object[]}, size=5992, count=107}
{net.sf.saxon.functions.Exists,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=5900, count=20}
{net.sf.saxon.functions.Exists,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=5900, count=20}
{net.sf.saxon.functions.Exists,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=5900, count=20}
{org.apache.xerces.impl.XMLVersionDetector,org.apache.xerces.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=5863, count=11}
{org.apache.xerces.impl.XMLNSDocumentScannerImpl,org.apache.xerces.impl.validation.ValidationManager,java.util.ArrayList,java.lang.Object[]}, size=5851, count=11}
{org.apache.xerces.parsers.XIncludeAwareParserConfiguration,org.apache.xerces.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=5831, count=7}
{org.apache.xerces.parsers.XIncludeAwareParserConfiguration,org.apache.xerces.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=5831, count=7}
{org.apache.xerces.parsers.XIncludeAwareParserConfiguration,org.apache.xerces.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=5831, count=7}
{org.apache.xerces.impl.xs.assertion.XSAssertImpl,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=5819, count=5}
{org.apache.xerces.impl.xs.assertion.XSAssertImpl,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=5819, count=5}
{java.net.ConnectException,java.net.ConnectException,java.net.ConnectException,java.lang.Object[]}, size=5808, count=33}
{net.sf.saxon.expr.instruct.Choose,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=5803, count=21}
{net.sf.saxon.expr.instruct.Choose,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=5803, count=21}
{net.sf.saxon.expr.instruct.Choose,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=5803, count=21}
{org.apache.ignite.internal.util.nio.GridNioServer$ByteBufferNioClientWorker,org.apache.ignite.internal.util.nio.GridNioServer,java.util.ArrayList,java.lang.Object[]}, size=5800, count=8}
{net.sf.saxon.style.XSLCallTemplate,net.sf.saxon.style.XSLTemplate,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=5776, count=16}
{net.sf.saxon.style.XSLCallTemplate,net.sf.saxon.style.XSLTemplate,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=5776, count=16}
{net.sf.saxon.style.XSLCallTemplate,net.sf.saxon.style.XSLTemplate,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=5776, count=16}
{org.apache.xerces.impl.XMLNSDocumentScannerImpl,org.apache.xerces.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=5724, count=12}
{org.apache.xerces.impl.XMLNSDocumentScannerImpl,org.apache.xerces.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=5724, count=12}
{org.apache.xerces.impl.XMLDTDScannerImpl,org.apache.xerces.impl.dtd.XMLDTDProcessor,java.util.ArrayList,java.lang.Object[]}, size=5712, count=7}
{org.apache.xerces.impl.XMLDTDScannerImpl,org.apache.xerces.impl.dtd.XMLDTDProcessor,java.util.ArrayList,java.lang.Object[]}, size=5712, count=7}
{org.apache.xerces.impl.XMLDTDScannerImpl,org.apache.xerces.impl.dtd.XMLDTDProcessor,java.util.ArrayList,java.lang.Object[]}, size=5712, count=7}
{net.sf.saxon.style.XSLTemplate,net.sf.saxon.style.XSLElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=5681, count=19}
{net.sf.saxon.style.XSLTemplate,net.sf.saxon.style.XSLElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=5681, count=19}
{net.sf.saxon.style.XSLTemplate,net.sf.saxon.style.XSLElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=5681, count=19}
{net.sf.saxon.style.ExpressionContext,net.sf.saxon.style.LiteralResultElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=5680, count=40}
{org.apache.kafka.clients.consumer.internals.Fetcher$FetchManagerMetrics,org.apache.kafka.common.metrics.Sensor,java.util.ArrayList,java.lang.Object[]}, size=5616, count=3}
{org.apache.xerces.impl.xs.XSElementDecl,org.apache.xerces.impl.xs.SchemaGrammar,java.util.Vector,java.lang.Object[]}, size=5584, count=8}
{org.h2.engine.User,org.h2.engine.Database,org.h2.util.BitField,long[]}, size=5584, count=2}
{net.sf.saxon.expr.Literal,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=5580, count=20}
{net.sf.saxon.expr.Literal,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=5580, count=20}
{net.sf.saxon.expr.Literal,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=5580, count=20}
{org.apache.xerces.impl.xs.traversers.XSAttributeChecker,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=5579, count=5}
{org.apache.xerces.impl.xs.traversers.XSAttributeChecker,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=5579, count=5}
{org.apache.xerces.impl.xs.traversers.DOMOverrideImpl,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=5564, count=5}
{org.apache.xerces.impl.xs.traversers.DOMOverrideImpl,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=5564, count=5}
{org.apache.xerces.impl.xs.XSGroupDecl,org.apache.xerces.impl.xs.SchemaGrammar,java.util.Vector,java.lang.Object[]}, size=5562, count=9}
{net.sf.saxon.style.XSLValueOf,net.sf.saxon.style.XSLAttribute,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=5510, count=19}
{net.sf.saxon.style.XSLValueOf,net.sf.saxon.style.XSLAttribute,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=5510, count=19}
{net.sf.saxon.style.XSLValueOf,net.sf.saxon.style.XSLAttribute,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=5510, count=19}
{org.apache.xerces.impl.xs.traversers.OverrideTransformationManager,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=5459, count=5}
{org.apache.xerces.impl.xs.traversers.OverrideTransformationManager,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=5459, count=5}
{org.apache.xerces.impl.xs.traversers.XSDocumentInfo,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=5430, count=15}
{java.util.logging.Logger,java.util.logging.Logger,java.util.ArrayList,java.lang.Object[]}, size=5396, count=14}
{org.postgresql.core.v3.SimpleQuery,org.postgresql.core.v3.ProtocolConnectionImpl,java.util.ArrayList,java.lang.Object[]}, size=5376, count=24}
{net.sf.saxon.expr.instruct.ComputedElement,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=5376, count=16}
{net.sf.saxon.expr.instruct.ComputedElement,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=5376, count=16}
{net.sf.saxon.expr.instruct.ComputedElement,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=5376, count=16}
{SomeType19,sun.misc.URLClassPath,java.util.ArrayList,java.lang.Object[]}, size=5366, count=2}
{org.apache.xerces.impl.xs.opti.SchemaDOMParser,org.apache.xerces.impl.xs.opti.SchemaConditionalIncludeHelper,java.util.ArrayList,java.lang.Object[]}, size=5250, count=5}
{org.apache.xerces.impl.xs.opti.SchemaDOMParser,org.apache.xerces.impl.xs.opti.SchemaConditionalIncludeHelper,java.util.ArrayList,java.lang.Object[]}, size=5250, count=5}
{org.apache.xerces.impl.XMLNSDocumentScannerImpl,org.apache.xerces.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=5247, count=11}
{org.apache.xerces.impl.xs.traversers.XSDAttributeTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=5239, count=5}
{org.apache.xerces.impl.xs.traversers.XSDGroupTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=5239, count=5}
{org.apache.xerces.impl.xs.traversers.XSDTypeAlternativeTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=5239, count=5}
{org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=5239, count=5}
{org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=5239, count=5}
{org.apache.xerces.impl.xs.traversers.XSDWildcardTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=5239, count=5}
{org.apache.xerces.impl.xs.traversers.XSDNotationTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=5239, count=5}
{org.apache.xerces.impl.xs.traversers.XSDSimpleTypeTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=5239, count=5}
{org.apache.xerces.impl.xs.traversers.XSDAttributeTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=5239, count=5}
{org.apache.xerces.impl.xs.traversers.XSDGroupTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=5239, count=5}
{org.apache.xerces.impl.xs.traversers.XSDKeyrefTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=5239, count=5}
{org.apache.xerces.impl.xs.traversers.XSDUniqueOrKeyTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=5239, count=5}
{org.apache.xerces.impl.xs.traversers.XSDSimpleTypeTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=5239, count=5}
{org.apache.xerces.impl.xs.traversers.XSDAttributeGroupTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=5239, count=5}
{org.apache.xerces.impl.xs.traversers.XSDElementTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=5239, count=5}
{org.apache.xerces.impl.xs.traversers.XSDElementTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=5239, count=5}
{org.apache.xerces.impl.xs.traversers.XSDAttributeGroupTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=5239, count=5}
{org.apache.xerces.impl.xs.traversers.XSDTypeAlternativeTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=5239, count=5}
{org.apache.xerces.impl.xs.traversers.XSDWildcardTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=5239, count=5}
{org.apache.xerces.impl.xs.traversers.XSDNotationTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=5239, count=5}
{org.apache.xerces.impl.xs.traversers.XSDKeyrefTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=5239, count=5}
{org.apache.xerces.impl.xs.traversers.XSDUniqueOrKeyTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=5239, count=5}
{org.apache.xerces.jaxp.SAXParserImpl$JAXPSAXParser,org.apache.xerces.impl.dtd.XMLDTDProcessor,java.util.ArrayList,java.lang.Object[]}, size=5201, count=7}
{org.apache.xerces.jaxp.SAXParserImpl$JAXPSAXParser,org.apache.xerces.impl.dtd.XMLDTDProcessor,java.util.ArrayList,java.lang.Object[]}, size=5201, count=7}
{org.apache.xerces.jaxp.SAXParserImpl$JAXPSAXParser,org.apache.xerces.impl.dtd.XMLDTDProcessor,java.util.ArrayList,java.lang.Object[]}, size=5201, count=7}
{java.beans.EventSetDescriptor,java.beans.MethodDescriptor,java.util.ArrayList,java.lang.Object[]}, size=5094, count=9}
{scala.util.parsing.combinator.Parsers$$anonfun$acceptIf$1,scala.util.parsing.json.Lexer,scala.collection.mutable.HashSet,java.lang.Object[]}, size=5052, count=6}
{scala.util.parsing.combinator.Parsers$$anonfun$acceptIf$1,scala.util.parsing.json.Lexer,scala.collection.mutable.HashSet,java.lang.Object[]}, size=5052, count=6}
{scala.util.parsing.combinator.Parsers$$anonfun$acceptIf$1,scala.util.parsing.json.Lexer,scala.collection.mutable.HashSet,java.lang.Object[]}, size=5052, count=6}
{SomeType17,sun.misc.URLClassPath,java.util.ArrayList,java.lang.Object[]}, size=5020, count=4}
{scala.util.parsing.combinator.lexical.StdLexical$$anonfun$scala$util$parsing$combinator$lexical$StdLexical$$parseDelim$1$1,scala.util.parsing.json.Lexer,scala.collection.mutable.HashSet,java.lang.Object[]}, size=5004, count=6}
{scala.util.parsing.combinator.lexical.StdLexical$$anonfun$scala$util$parsing$combinator$lexical$StdLexical$$parseDelim$1$1,scala.util.parsing.json.Lexer,scala.collection.mutable.HashSet,java.lang.Object[]}, size=5004, count=6}
{scala.util.parsing.combinator.Parsers$$anonfun$success$1,scala.util.parsing.json.Lexer,scala.collection.mutable.HashSet,java.lang.Object[]}, size=5004, count=6}
{scala.util.parsing.combinator.Parsers$$anonfun$success$1,scala.util.parsing.json.Lexer,scala.collection.mutable.HashSet,java.lang.Object[]}, size=5004, count=6}
{scala.util.parsing.combinator.Parsers$$anonfun$success$1,scala.util.parsing.json.Lexer,scala.collection.mutable.HashSet,java.lang.Object[]}, size=5004, count=6}
{scala.util.parsing.combinator.lexical.StdLexical$$anonfun$scala$util$parsing$combinator$lexical$StdLexical$$parseDelim$1$1,scala.util.parsing.json.Lexer,scala.collection.mutable.HashSet,java.lang.Object[]}, size=5004, count=6}
{net.sf.saxon.style.XSLAttribute,net.sf.saxon.style.XSLValueOf,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=4997, count=19}
{net.sf.saxon.style.XSLAttribute,net.sf.saxon.style.XSLValueOf,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=4997, count=19}
{net.sf.saxon.style.XSLAttribute,net.sf.saxon.style.XSLValueOf,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=4997, count=19}
{java.util.LinkedHashMap$Entry,org.apache.ignite.spi.discovery.tcp.messages.TcpDiscoveryNodeAddedMessage,java.util.ArrayList,java.lang.Object[]}, size=4958, count=3}
{org.apache.xerces.impl.xs.XSElementDecl,org.apache.xerces.impl.xs.SchemaGrammar,java.util.Vector,java.lang.Object[]}, size=4886, count=7}
{net.sf.saxon.expr.ItemChecker,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=4851, count=21}
{net.sf.saxon.expr.ItemChecker,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=4851, count=21}
{net.sf.saxon.expr.ItemChecker,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=4851, count=21}
{com.mchange.v2.resourcepool.BasicResourcePool,com.mchange.v2.c3p0.impl.NewPooledConnection,java.sql.SQLException,java.lang.Object[]}, size=4840, count=8}
{net.sf.saxon.functions.ExecutableFunctionLibrary,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=4760, count=10}
{net.sf.saxon.functions.ExecutableFunctionLibrary,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=4760, count=10}
{net.sf.saxon.functions.ExecutableFunctionLibrary,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=4760, count=10}
{SomeType100,sun.misc.URLClassPath,java.util.ArrayList,java.lang.Object[]}, size=4738, count=2}
{org.apache.logging.log4j.core.Logger$PrivateConfig,org.apache.logging.log4j.core.config.xml.XmlConfiguration,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=4736, count=74}
{net.sf.saxon.style.XSLCallTemplate,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=4725, count=21}
{net.sf.saxon.style.XSLCallTemplate,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=4725, count=21}
{net.sf.saxon.style.XSLCallTemplate,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=4725, count=21}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDTypeAlternativeTraverser,java.util.Vector,java.lang.Object[]}, size=4695, count=5}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDTypeAlternativeTraverser,java.util.Vector,java.lang.Object[]}, size=4695, count=5}
{net.sf.saxon.lib.StandardEntityResolver,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=4680, count=10}
{net.sf.saxon.lib.StandardEntityResolver,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=4680, count=10}
{net.sf.saxon.lib.StandardEntityResolver,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=4680, count=10}
{net.sf.saxon.style.XSLChoose,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=4596, count=12}
{net.sf.saxon.style.XSLChoose,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=4596, count=12}
{net.sf.saxon.style.XSLChoose,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=4596, count=12}
{org.apache.xerces.impl.xs.assertion.XSAssertImpl,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=4572, count=4}
{oracle.jrockit.jfr.events.EventHandlerCreator$EventInfoClassLoader,oracle.jrockit.jfr.events.EventHandlerCreator$EventInfoClassLoader,java.util.Vector,java.lang.Object[]}, size=4572, count=9}
{net.sf.saxon.style.XSLWhen,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=4548, count=12}
{net.sf.saxon.style.XSLWhen,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=4548, count=12}
{net.sf.saxon.style.XSLWhen,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=4548, count=12}
{com.mchange.v2.c3p0.impl.NewPooledConnection,java.sql.SQLException,java.sql.SQLException,java.lang.Object[]}, size=4512, count=12}
{net.sf.saxon.style.XSLOtherwise,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=4452, count=12}
{net.sf.saxon.style.XSLOtherwise,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=4452, count=12}
{net.sf.saxon.style.XSLOtherwise,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=4452, count=12}
{java.util.WeakHashMap$Entry,com.mchange.v2.c3p0.impl.NewPooledConnection,java.sql.SQLException,java.lang.Object[]}, size=4416, count=12}
{java.lang.ref.WeakReference,java.util.logging.Logger,java.util.ArrayList,java.lang.Object[]}, size=4402, count=14}
{org.apache.xerces.impl.xs.traversers.XSAttributeChecker,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=4380, count=4}
{org.apache.xerces.dom.PSVIElementNSImpl,org.apache.xerces.dom.PSVIElementNSImpl,org.apache.xerces.impl.xs.util.ObjectListImpl,java.lang.Object[]}, size=4368, count=6}
{org.apache.xerces.impl.xs.traversers.DOMOverrideImpl,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=4368, count=4}
{com.sun.org.apache.xerces.internal.impl.XMLNSDocumentScannerImpl$NSContentDriver,com.sun.org.apache.xerces.internal.impl.XMLNSDocumentScannerImpl,java.util.ArrayList,java.lang.Object[]}, size=4364, count=2}
{SomeType120,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=4354, count=2}
{org.apache.kafka.clients.consumer.internals.AbstractCoordinator$GroupCoordinatorMetrics,org.apache.kafka.common.metrics.Sensor,java.util.ArrayList,java.lang.Object[]}, size=4320, count=4}
{org.apache.xerces.impl.xs.traversers.OverrideTransformationManager,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=4284, count=4}
{org.apache.xerces.util.SymbolHash$Entry,org.apache.xerces.impl.dv.xs.XSSimpleTypeDecl,java.util.Vector,java.lang.Object[]}, size=4230, count=6}
{org.apache.xerces.util.SymbolHash$Entry,org.apache.xerces.impl.dv.xs.XSSimpleTypeDecl,java.util.Vector,java.lang.Object[]}, size=4230, count=6}
{org.apache.xerces.util.SymbolHash$Entry,org.apache.xerces.impl.dv.xs.XSSimpleTypeDecl,java.util.Vector,java.lang.Object[]}, size=4230, count=6}
{org.apache.kafka.common.network.Selector$SelectorMetrics,org.apache.kafka.common.network.Selector,java.util.ArrayList,java.lang.Object[]}, size=4225, count=5}
{net.sf.saxon.style.XSLTemplate,net.sf.saxon.style.XSLCallTemplate,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=4224, count=16}
{net.sf.saxon.style.XSLTemplate,net.sf.saxon.style.XSLCallTemplate,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=4224, count=16}
{net.sf.saxon.style.XSLTemplate,net.sf.saxon.style.XSLCallTemplate,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=4224, count=16}
{org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionsExchangeFuture$8,org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode,java.util.ArrayList,java.lang.Object[]}, size=4220, count=14}
{org.apache.xerces.impl.xs.opti.SchemaDOMParser,org.apache.xerces.impl.xs.opti.SchemaParsingConfig,java.util.ArrayList,java.lang.Object[]}, size=4215, count=5}
{org.apache.xerces.impl.xs.opti.SchemaDOMParser,org.apache.xerces.impl.xs.opti.SchemaParsingConfig,java.util.ArrayList,java.lang.Object[]}, size=4215, count=5}
{org.apache.xerces.impl.xs.opti.SchemaDOMParser,org.apache.xerces.impl.xs.opti.SchemaConditionalIncludeHelper,java.util.ArrayList,java.lang.Object[]}, size=4200, count=4}
{org.apache.xerces.impl.xs.XSElementDecl,org.apache.xerces.impl.xs.SchemaGrammar,java.util.Vector,java.lang.Object[]}, size=4188, count=6}
{SomeType120,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=4178, count=2}
{java.util.HashMap$Node,sun.rmi.transport.Target,java.util.Vector,java.lang.Object[]}, size=4170, count=15}
{java.util.concurrent.ConcurrentHashMap$Node,java.util.concurrent.ConcurrentHashMap$Node,org.springframework.beans.factory.support.RootBeanDefinition,java.lang.Object[]}, size=4161, count=11}
{java.util.jar.JarFile,java.util.jar.JarVerifier,java.util.ArrayList,java.lang.Object[]}, size=4146, count=11}
{org.apache.kafka.clients.NetworkClient,org.apache.kafka.common.network.Selector,java.util.ArrayList,java.lang.Object[]}, size=4145, count=5}
{org.apache.xerces.impl.xs.traversers.XSDGroupTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=4108, count=4}
{org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=4108, count=4}
{org.apache.xerces.impl.xs.traversers.XSDWildcardTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=4108, count=4}
{org.apache.xerces.impl.xs.traversers.XSDElementTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=4108, count=4}
{org.apache.xerces.impl.xs.traversers.XSDKeyrefTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=4108, count=4}
{org.apache.xerces.impl.xs.traversers.XSDUniqueOrKeyTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=4108, count=4}
{org.apache.xerces.impl.xs.traversers.XSDSimpleTypeTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=4108, count=4}
{org.apache.xerces.impl.xs.traversers.XSDAttributeTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=4108, count=4}
{org.apache.xerces.impl.xs.traversers.XSDTypeAlternativeTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=4108, count=4}
{org.apache.xerces.impl.xs.traversers.XSDAttributeGroupTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=4108, count=4}
{org.apache.xerces.impl.xs.traversers.XSDNotationTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=4108, count=4}
{net.sf.saxon.style.LiteralResultElement,net.sf.saxon.style.LiteralResultElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=4104, count=28}
{net.sf.saxon.style.LiteralResultElement,net.sf.saxon.style.LiteralResultElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=4104, count=28}
{net.sf.saxon.style.LiteralResultElement,net.sf.saxon.style.LiteralResultElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=4104, count=28}
{net.sf.saxon.style.ExpressionContext,net.sf.saxon.style.LiteralResultElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=4080, count=40}
{net.sf.saxon.style.ExpressionContext,net.sf.saxon.style.LiteralResultElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=4080, count=40}
{sun.security.x509.DistributionPoint,sun.security.x509.GeneralNames,java.util.ArrayList,java.lang.Object[]}, size=4028, count=19}
{net.sf.saxon.Configuration,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=4000, count=5}
{net.sf.saxon.Configuration,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=4000, count=5}
{net.sf.saxon.Configuration,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=4000, count=5}
{com.mchange.v2.c3p0.util.ConnectionEventSupport,com.mchange.v2.c3p0.impl.NewPooledConnection,java.sql.SQLException,java.lang.Object[]}, size=3984, count=12}
{com.mchange.v2.c3p0.util.StatementEventSupport,com.mchange.v2.c3p0.impl.NewPooledConnection,java.sql.SQLException,java.lang.Object[]}, size=3984, count=12}
{java.util.Collections$SynchronizedRandomAccessList,java.util.Collections$SynchronizedRandomAccessList,java.util.ArrayList,java.lang.Object[]}, size=3968, count=13}
{java.util.TreeMap$Entry,sun.security.x509.CRLDistributionPointsExtension,java.util.ArrayList,java.lang.Object[]}, size=3872, count=16}
{org.postgresql.jdbc4.Jdbc4Connection,org.postgresql.core.v3.ProtocolConnectionImpl,java.util.ArrayList,java.lang.Object[]}, size=3804, count=12}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDTypeAlternativeTraverser,java.util.Vector,java.lang.Object[]}, size=3756, count=4}
{org.apache.kafka.clients.consumer.internals.Fetcher$FetchManagerMetrics,org.apache.kafka.common.metrics.Sensor,java.util.ArrayList,java.lang.Object[]}, size=3744, count=2}
{java.util.jar.JarVerifier$3,java.util.jar.JarVerifier,java.util.ArrayList,java.lang.Object[]}, size=3744, count=12}
{org.apache.kafka.common.network.Selector$SelectorMetrics$1,org.apache.kafka.common.network.Selector,java.util.ArrayList,java.lang.Object[]}, size=3705, count=5}
{org.springframework.context.support.ClassPathXmlApplicationContext,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=3703, count=3}
{org.apache.ignite.internal.marshaller.optimized.OptimizedClassDescriptor,org.apache.ignite.internal.client.marshaller.optimized.GridClientOptimizedMarshaller$ClientMarshallerContext,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=3648, count=12}
{org.apache.xerces.util.SymbolHash$Entry,org.apache.xerces.impl.xs.SchemaGrammar,java.util.Vector,java.lang.Object[]}, size=3612, count=6}
{sun.net.www.protocol.jar.URLJarFile$URLJarFileEntry,sun.net.www.protocol.jar.URLJarFile,java.util.ArrayDeque,java.lang.Object[]}, size=3600, count=8}
{org.apache.logging.log4j.core.layout.PatternLayout,org.apache.logging.log4j.core.config.xml.XmlConfiguration,java.util.ArrayList,java.lang.Object[]}, size=3584, count=8}
{org.postgresql.core.v3.SimpleQuery,org.postgresql.core.v3.ProtocolConnectionImpl,java.util.ArrayList,java.lang.Object[]}, size=3584, count=16}
{java.util.jar.JarFile$JarFileEntry,sun.net.www.protocol.jar.URLJarFile,java.util.ArrayDeque,java.lang.Object[]}, size=3536, count=8}
{net.sf.saxon.style.ExpressionContext,net.sf.saxon.style.XSLWhen,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=3528, count=12}
{net.sf.saxon.style.ExpressionContext,net.sf.saxon.style.XSLWhen,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=3528, count=12}
{net.sf.saxon.style.ExpressionContext,net.sf.saxon.style.XSLWhen,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=3528, count=12}
{org.postgresql.core.v3.QueryExecutorImpl,org.postgresql.core.v3.ProtocolConnectionImpl,java.util.ArrayList,java.lang.Object[]}, size=3492, count=12}
{net.sf.saxon.style.XSLChoose,net.sf.saxon.style.XSLAttribute,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=3480, count=12}
{net.sf.saxon.style.XSLChoose,net.sf.saxon.style.XSLAttribute,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=3480, count=12}
{net.sf.saxon.style.XSLChoose,net.sf.saxon.style.XSLAttribute,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=3480, count=12}
{net.sf.saxon.style.XSLIf,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=3411, count=9}
{net.sf.saxon.style.XSLIf,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=3411, count=9}
{net.sf.saxon.style.XSLIf,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=3411, count=9}
{org.apache.kafka.common.network.Selector$SelectorMetrics,org.apache.kafka.common.network.Selector,java.util.ArrayList,java.lang.Object[]}, size=3380, count=4}
{org.apache.logging.log4j.core.pattern.MessagePatternConverter,org.apache.logging.log4j.core.config.xml.XmlConfiguration,java.util.ArrayList,java.lang.Object[]}, size=3376, count=8}
{org.apache.xerces.impl.xs.opti.SchemaDOMParser,org.apache.xerces.impl.xs.opti.SchemaParsingConfig,java.util.ArrayList,java.lang.Object[]}, size=3372, count=4}
{org.apache.kafka.clients.producer.internals.Sender$SenderMetrics,org.apache.kafka.common.metrics.Sensor,java.util.ArrayList,java.lang.Object[]}, size=3344, count=1}
{org.apache.kafka.clients.producer.internals.Sender$SenderMetrics,org.apache.kafka.common.metrics.Sensor,java.util.ArrayList,java.lang.Object[]}, size=3344, count=1}
{org.apache.kafka.clients.producer.internals.Sender$SenderMetrics,org.apache.kafka.common.metrics.Sensor,java.util.ArrayList,java.lang.Object[]}, size=3344, count=1}
{org.apache.xerces.impl.dtd.XMLNSDTDValidator,org.apache.xerces.impl.validation.ValidationManager,java.util.ArrayList,java.lang.Object[]}, size=3339, count=7}
{org.apache.xerces.impl.dtd.XMLNSDTDValidator,org.apache.xerces.impl.validation.ValidationManager,java.util.ArrayList,java.lang.Object[]}, size=3339, count=7}
{org.apache.xerces.impl.dtd.XMLNSDTDValidator,org.apache.xerces.impl.validation.ValidationManager,java.util.ArrayList,java.lang.Object[]}, size=3339, count=7}
{org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=3322, count=3}
{org.apache.kafka.clients.NetworkClient,org.apache.kafka.common.network.Selector,java.util.ArrayList,java.lang.Object[]}, size=3316, count=4}
{org.springframework.context.event.SimpleApplicationEventMulticaster,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=3283, count=3}
{java.lang.ref.WeakReference,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=3259, count=3}
{net.sf.saxon.style.LiteralResultElement,net.sf.saxon.style.XSLTemplate,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=3249, count=9}
{net.sf.saxon.style.LiteralResultElement,net.sf.saxon.style.XSLTemplate,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=3249, count=9}
{net.sf.saxon.style.LiteralResultElement,net.sf.saxon.style.XSLTemplate,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=3249, count=9}
{org.springframework.beans.factory.annotation.RequiredAnnotationBeanPostProcessor,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=3247, count=3}
{java.util.HashMap$Node,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=3247, count=3}
{org.apache.kafka.clients.consumer.internals.AbstractCoordinator$GroupCoordinatorMetrics,org.apache.kafka.common.metrics.Sensor,java.util.ArrayList,java.lang.Object[]}, size=3240, count=3}
{org.springframework.context.support.DefaultLifecycleProcessor,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=3238, count=3}
{org.springframework.beans.factory.annotation.QualifierAnnotationAutowireCandidateResolver,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=3235, count=3}
{net.sf.saxon.expr.ComponentBinding,net.sf.saxon.expr.Component,java.util.ArrayList,java.lang.Object[]}, size=3213, count=21}
{net.sf.saxon.expr.ComponentBinding,net.sf.saxon.expr.Component,java.util.ArrayList,java.lang.Object[]}, size=3213, count=21}
{net.sf.saxon.expr.ComponentBinding,net.sf.saxon.expr.Component,java.util.ArrayList,java.lang.Object[]}, size=3213, count=21}
{org.springframework.beans.factory.config.BeanExpressionContext,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=3211, count=3}
{org.springframework.context.annotation.ConfigurationClassPostProcessor$ImportAwareBeanPostProcessor,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=3187, count=3}
{org.springframework.context.expression.BeanFactoryResolver,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=3187, count=3}
{org.apache.kafka.common.network.Selector,org.apache.kafka.common.network.Selector$SelectorMetrics,java.util.ArrayList,java.lang.Object[]}, size=3185, count=5}
{org.apache.ignite.internal.processors.query.CacheQueryObjectValueContext,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=3172, count=2}
{org.apache.xerces.dom.TextImpl,org.apache.xerces.dom.PSVIElementNSImpl,org.apache.xerces.impl.xs.util.ObjectListImpl,java.lang.Object[]}, size=3158, count=7}
{SomeType17,sun.misc.URLClassPath,java.util.Stack,java.lang.Object[]}, size=3120, count=4}
{org.springframework.context.support.AbstractApplicationContext$BeanPostProcessorChecker,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=3115, count=3}
{org.springframework.context.support.ClassPathXmlApplicationContext,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=3111, count=3}
{net.sf.saxon.style.XSLIf,net.sf.saxon.style.LiteralResultElement,java.util.ArrayList,java.lang.Object[]}, size=3096, count=5}
{net.sf.saxon.style.XSLTemplate,net.sf.saxon.style.LiteralResultElement,java.util.ArrayList,java.lang.Object[]}, size=3096, count=9}
{net.sf.saxon.style.XSLTemplate,net.sf.saxon.style.LiteralResultElement,java.util.ArrayList,java.lang.Object[]}, size=3096, count=9}
{net.sf.saxon.style.XSLTemplate,net.sf.saxon.style.LiteralResultElement,java.util.ArrayList,java.lang.Object[]}, size=3096, count=9}
{net.sf.saxon.style.XSLIf,net.sf.saxon.style.LiteralResultElement,java.util.ArrayList,java.lang.Object[]}, size=3096, count=5}
{net.sf.saxon.style.XSLIf,net.sf.saxon.style.LiteralResultElement,java.util.ArrayList,java.lang.Object[]}, size=3096, count=5}
{org.apache.xerces.jaxp.validation.SimpleXMLSchema,org.apache.xerces.impl.xs.SchemaGrammar,java.util.Vector,java.lang.Object[]}, size=3090, count=5}
{org.apache.xerces.jaxp.validation.SimpleXMLSchema,org.apache.xerces.impl.xs.SchemaGrammar,java.util.Vector,java.lang.Object[]}, size=3090, count=5}
{org.apache.xerces.jaxp.validation.SimpleXMLSchema,org.apache.xerces.impl.xs.SchemaGrammar,java.util.Vector,java.lang.Object[]}, size=3090, count=5}
{SomeType108,org.dom4j.tree.DefaultDocument,java.util.ArrayList,java.lang.Object[]}, size=3084, count=7}
{org.apache.ignite.internal.processors.cache.persistence.GridCacheDatabaseSharedManager$6,org.apache.ignite.internal.processors.cache.persistence.GridCacheDatabaseSharedManager,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=3074, count=2}
{sun.net.www.protocol.jar.JarURLConnection,sun.net.www.protocol.jar.URLJarFile,java.util.ArrayDeque,java.lang.Object[]}, size=3073, count=7}
{net.sf.saxon.Controller,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=3063, count=5}
{net.sf.saxon.Controller,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=3063, count=5}
{net.sf.saxon.Controller,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=3063, count=5}
{org.apache.ignite.internal.processors.cache.persistence.GridCacheDatabaseSharedManager$8,org.apache.ignite.internal.processors.cache.persistence.GridCacheDatabaseSharedManager,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=3058, count=2}
{org.apache.ignite.internal.processors.cache.persistence.GridCacheDatabaseSharedManager$7,org.apache.ignite.internal.processors.cache.persistence.GridCacheDatabaseSharedManager,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=3058, count=2}
{javax.management.openmbean.OpenMBeanAttributeInfoSupport,javax.management.openmbean.ArrayType,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=3036, count=12}
{org.dom4j.tree.DefaultElement,org.dom4j.tree.DefaultDocument,java.util.ArrayList,java.lang.Object[]}, size=3024, count=14}
{org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,org.apache.ignite.internal.processors.cache.persistence.GridCacheDatabaseSharedManager,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=3010, count=2}
{com.mchange.v2.c3p0.impl.NewPooledConnection,java.sql.SQLException,java.sql.SQLException,java.lang.Object[]}, size=3008, count=8}
{SomeType19,sun.misc.URLClassPath,java.util.Stack,java.lang.Object[]}, size=3000, count=2}
{org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionsExchangeFuture,org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode,java.util.ArrayList,java.lang.Object[]}, size=2980, count=10}
{net.sf.saxon.style.XSLOutput,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=2975, count=5}
{net.sf.saxon.style.XSLOutput,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=2975, count=5}
{net.sf.saxon.style.XSLOutput,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=2975, count=5}
{org.apache.kafka.common.network.Selector$SelectorMetrics$1,org.apache.kafka.common.network.Selector,java.util.ArrayList,java.lang.Object[]}, size=2964, count=4}
{org.apache.ignite.internal.managers.discovery.ConsistentIdMapper,org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.IdentityHashMap,java.lang.Object[]}, size=2960, count=5}
{java.util.WeakHashMap$Entry,com.mchange.v2.c3p0.impl.NewPooledConnection,java.sql.SQLException,java.lang.Object[]}, size=2944, count=8}
{net.sf.saxon.style.StylesheetPackage,net.sf.saxon.functions.FunctionLibraryList,java.util.ArrayList,java.lang.Object[]}, size=2915, count=5}
{net.sf.saxon.style.StylesheetPackage,net.sf.saxon.functions.FunctionLibraryList,java.util.ArrayList,java.lang.Object[]}, size=2915, count=5}
{net.sf.saxon.style.StylesheetPackage,net.sf.saxon.functions.FunctionLibraryList,java.util.ArrayList,java.lang.Object[]}, size=2915, count=5}
{javax.management.StandardMBean,javax.management.MBeanInfo,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=2895, count=15}
{net.sf.saxon.style.XSLWhen,net.sf.saxon.style.XSLValueOf,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=2893, count=11}
{net.sf.saxon.style.XSLWhen,net.sf.saxon.style.XSLValueOf,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=2893, count=11}
{net.sf.saxon.style.XSLWhen,net.sf.saxon.style.XSLValueOf,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=2893, count=11}
{org.apache.ignite.internal.processors.query.h2.opt.GridH2MetaTable,org.h2.engine.Database,org.h2.util.BitField,long[]}, size=2877, count=1}
{SomeType16,SomeType16,java.util.ArrayList,java.lang.Object[]}, size=2876, count=2}
{org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTxPrepareFuture,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=2860, count=5}
{net.sf.saxon.tree.linked.DocumentImpl,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2825, count=5}
{net.sf.saxon.tree.linked.DocumentImpl,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2825, count=5}
{net.sf.saxon.tree.linked.DocumentImpl,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2825, count=5}
{ch.qos.logback.core.rolling.helper.FileNamePattern,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=2820, count=6}
{com.mchange.v2.c3p0.management.DynamicPooledDataSourceManagerMBean,javax.management.MBeanInfo,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=2796, count=12}
{net.sf.saxon.style.XSLValueOf,net.sf.saxon.style.XSLWhen,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=2794, count=11}
{net.sf.saxon.style.XSLValueOf,net.sf.saxon.style.XSLWhen,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=2794, count=11}
{net.sf.saxon.style.XSLValueOf,net.sf.saxon.style.XSLWhen,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=2794, count=11}
{com.sun.org.apache.xerces.internal.impl.xpath.regex.Token$ParenToken,com.sun.org.apache.xerces.internal.impl.xpath.regex.Token$UnionToken,java.util.ArrayList,java.lang.Object[]}, size=2784, count=14}
{org.h2.engine.Role,org.h2.engine.Database,org.h2.util.BitField,long[]}, size=2775, count=1}
{org.h2.mvstore.db.ValueDataType,org.h2.engine.Database,org.h2.util.BitField,long[]}, size=2745, count=1}
{java.util.HashMap$Node,org.h2.engine.Database,org.h2.util.BitField,long[]}, size=2741, count=1}
{net.sf.saxon.tree.linked.DocumentImpl,net.sf.saxon.style.XSLPackage,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=2740, count=5}
{net.sf.saxon.tree.linked.DocumentImpl,net.sf.saxon.style.XSLPackage,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=2740, count=5}
{net.sf.saxon.tree.linked.DocumentImpl,net.sf.saxon.style.XSLPackage,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=2740, count=5}
{net.sf.saxon.style.Compilation,net.sf.saxon.style.StylesheetPackage,java.util.ArrayList,java.lang.Object[]}, size=2740, count=5}
{net.sf.saxon.style.Compilation,net.sf.saxon.style.StylesheetPackage,java.util.ArrayList,java.lang.Object[]}, size=2740, count=5}
{net.sf.saxon.style.Compilation,net.sf.saxon.style.StylesheetPackage,java.util.ArrayList,java.lang.Object[]}, size=2740, count=5}
{java.util.WeakHashMap$Entry,java.util.WeakHashMap$Entry,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=2732, count=13}
{java.util.zip.ZipFile$ZipFileInflaterInputStream,sun.net.www.protocol.jar.URLJarFile,java.util.ArrayDeque,java.lang.Object[]}, size=2709, count=7}
{org.h2.store.LobStorageMap,org.h2.engine.Database,org.h2.util.BitField,long[]}, size=2697, count=1}
{org.springframework.context.event.SimpleApplicationEventMulticaster,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=2691, count=3}
{SomeType100,sun.misc.URLClassPath,java.util.Stack,java.lang.Object[]}, size=2680, count=2}
{net.sf.saxon.expr.instruct.ApplyTemplates,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=2673, count=11}
{net.sf.saxon.expr.instruct.ApplyTemplates,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=2673, count=11}
{net.sf.saxon.expr.instruct.ApplyTemplates,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=2673, count=11}
{java.lang.ref.WeakReference,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=2667, count=3}
{java.util.HashMap$Node,org.springframework.context.support.ClassPathXmlApplicationContext,java.util.ArrayList,java.lang.Object[]}, size=2664, count=9}
{java.util.HashMap$Node,org.springframework.context.support.ClassPathXmlApplicationContext,java.util.ArrayList,java.lang.Object[]}, size=2664, count=9}
{com.mchange.v2.c3p0.util.StatementEventSupport,com.mchange.v2.c3p0.impl.NewPooledConnection,java.sql.SQLException,java.lang.Object[]}, size=2656, count=8}
{org.eclipse.wst.xml.xpath2.processor.internal.types.ElementType,org.apache.xerces.dom.PSVIElementNSImpl,org.apache.xerces.impl.xs.util.ObjectListImpl,java.lang.Object[]}, size=2656, count=4}
{com.mchange.v2.c3p0.util.ConnectionEventSupport,com.mchange.v2.c3p0.impl.NewPooledConnection,java.sql.SQLException,java.lang.Object[]}, size=2656, count=8}
{org.eclipse.wst.xml.xpath2.processor.internal.types.ElementType,org.apache.xerces.dom.PSVIElementNSImpl,org.apache.xerces.impl.xs.util.ObjectListImpl,java.lang.Object[]}, size=2656, count=4}
{java.util.HashMap$Node,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=2655, count=3}
{net.sf.saxon.style.ExpressionContext,net.sf.saxon.style.XSLIf,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=2646, count=9}
{net.sf.saxon.style.ExpressionContext,net.sf.saxon.style.XSLIf,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=2646, count=9}
{net.sf.saxon.style.ExpressionContext,net.sf.saxon.style.XSLIf,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=2646, count=9}
{org.springframework.context.support.DefaultLifecycleProcessor,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=2646, count=3}
{org.springframework.beans.factory.annotation.QualifierAnnotationAutowireCandidateResolver,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=2643, count=3}
{org.apache.kafka.common.network.Selector$SelectorMetrics$1,org.apache.kafka.common.network.Selector$SelectorMetrics,java.util.ArrayList,java.lang.Object[]}, size=2640, count=5}
{net.sf.saxon.style.ExpressionContext,net.sf.saxon.style.XSLOutput,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=2630, count=5}
{net.sf.saxon.style.ExpressionContext,net.sf.saxon.style.XSLOutput,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=2630, count=5}
{net.sf.saxon.style.ExpressionContext,net.sf.saxon.style.XSLOutput,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=2630, count=5}
{java.lang.invoke.LambdaForm$Name,java.lang.invoke.LambdaForm$BasicType,sun.invoke.util.Wrapper,long[]}, size=2626, count=13}
{net.sf.saxon.PreparedStylesheet,net.sf.saxon.functions.FunctionLibraryList,java.util.ArrayList,java.lang.Object[]}, size=2605, count=5}
{net.sf.saxon.PreparedStylesheet,net.sf.saxon.functions.FunctionLibraryList,java.util.ArrayList,java.lang.Object[]}, size=2605, count=5}
{net.sf.saxon.PreparedStylesheet,net.sf.saxon.functions.FunctionLibraryList,java.util.ArrayList,java.lang.Object[]}, size=2605, count=5}
{org.eclipse.wst.xml.xpath2.processor.internal.ast.FilterExpr,org.eclipse.wst.xml.xpath2.processor.internal.ast.FunctionCall,java.util.ArrayList,java.lang.Object[]}, size=2600, count=15}
{org.eclipse.wst.xml.xpath2.processor.internal.ast.FilterExpr,org.eclipse.wst.xml.xpath2.processor.internal.ast.FunctionCall,java.util.ArrayList,java.lang.Object[]}, size=2600, count=15}
{org.apache.ignite.spi.discovery.tcp.messages.TcpDiscoveryNodeAddedMessage,org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode,java.util.ArrayList,java.lang.Object[]}, size=2592, count=6}
{net.sf.saxon.jaxp.TransformerImpl,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2580, count=5}
{net.sf.saxon.jaxp.TransformerImpl,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2580, count=5}
{net.sf.saxon.jaxp.TransformerImpl,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2580, count=5}
{java.util.zip.ZipFile$ZipFileInputStream,sun.net.www.protocol.jar.URLJarFile,java.util.ArrayDeque,java.lang.Object[]}, size=2541, count=7}
{java.lang.ref.Finalizer,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2540, count=5}
{java.lang.ref.Finalizer,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2540, count=5}
{java.lang.ref.Finalizer,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2540, count=5}
{org.apache.kafka.common.network.Selector,org.apache.kafka.common.network.Selector$SelectorMetrics,java.util.ArrayList,java.lang.Object[]}, size=2540, count=4}
{org.postgresql.jdbc4.Jdbc4Connection,org.postgresql.core.v3.ProtocolConnectionImpl,java.util.ArrayList,java.lang.Object[]}, size=2536, count=8}
{org.apache.kafka.common.network.Selector$SelectorMetrics,org.apache.kafka.common.network.Selector,java.util.ArrayList,java.lang.Object[]}, size=2535, count=3}
{org.apache.xerces.impl.xs.traversers.XSDocumentInfo,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=2534, count=7}
{scala.util.parsing.combinator.Parsers$Failure,scala.util.parsing.json.Lexer,scala.collection.mutable.HashSet,java.lang.Object[]}, size=2529, count=3}
{org.springframework.context.support.AbstractApplicationContext$BeanPostProcessorChecker,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=2523, count=3}
{org.apache.logging.log4j.core.config.Node,org.apache.logging.log4j.core.config.Node,java.util.ArrayList,java.lang.Object[]}, size=2520, count=9}
{net.sf.saxon.lib.ConversionRules,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2505, count=5}
{net.sf.saxon.lib.ConversionRules,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2505, count=5}
{net.sf.saxon.lib.ConversionRules,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2505, count=5}
{SomeType121,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=2497, count=1}
{java.util.HashMap$Node,SomeType62,java.util.ArrayList,java.lang.Object[]}, size=2488, count=2}
{org.apache.kafka.clients.NetworkClient,org.apache.kafka.common.network.Selector,java.util.ArrayList,java.lang.Object[]}, size=2487, count=3}
{org.eclipse.wst.xml.xpath2.processor.internal.ast.XPathExpr,org.eclipse.wst.xml.xpath2.processor.internal.ast.FilterExpr,java.util.ArrayList,java.lang.Object[]}, size=2480, count=20}
{org.eclipse.wst.xml.xpath2.processor.internal.ast.XPathExpr,org.eclipse.wst.xml.xpath2.processor.internal.ast.FilterExpr,java.util.ArrayList,java.lang.Object[]}, size=2480, count=20}
{net.sf.saxon.style.XSLApplyTemplates,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=2475, count=11}
{net.sf.saxon.style.XSLApplyTemplates,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=2475, count=11}
{net.sf.saxon.style.XSLApplyTemplates,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=2475, count=11}
{java.util.Hashtable$Entry,org.apache.xerces.impl.xs.SchemaGrammar,java.util.Vector,java.lang.Object[]}, size=2424, count=4}
{java.util.Hashtable$Entry,org.apache.xerces.impl.xs.SchemaGrammar,java.util.Vector,java.lang.Object[]}, size=2424, count=4}
{net.sf.saxon.expr.PackageData,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2410, count=5}
{net.sf.saxon.expr.PackageData,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2410, count=5}
{net.sf.saxon.expr.PackageData,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2410, count=5}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=2385, count=5}
{org.apache.xerces.impl.xs.opti.SchemaParsingConfig,org.apache.xerces.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=2385, count=5}
{org.apache.xerces.impl.xs.opti.SchemaParsingConfig,org.apache.xerces.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=2385, count=5}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=2385, count=5}
{org.springframework.context.annotation.CommonAnnotationBeanPostProcessor,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=2384, count=1}
{net.sf.saxon.query.XQueryFunctionLibrary,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2380, count=5}
{net.sf.saxon.type.TypeHierarchy,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2380, count=5}
{net.sf.saxon.s9api.Processor,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2380, count=5}
{net.sf.saxon.query.XQueryFunctionLibrary,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2380, count=5}
{net.sf.saxon.query.XQueryFunctionLibrary,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2380, count=5}
{net.sf.saxon.s9api.Processor,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2380, count=5}
{net.sf.saxon.type.TypeHierarchy,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2380, count=5}
{net.sf.saxon.s9api.Processor,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2380, count=5}
{net.sf.saxon.type.TypeHierarchy,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2380, count=5}
{org.apache.ignite.internal.processors.cache.ExchangeContext,org.apache.ignite.internal.processors.cache.ExchangeDiscoveryEvents,java.util.ArrayList,java.lang.Object[]}, size=2370, count=10}
{net.sf.saxon.expr.parser.Optimizer,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2365, count=5}
{net.sf.saxon.expr.parser.Optimizer,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2365, count=5}
{net.sf.saxon.expr.parser.Optimizer,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2365, count=5}
{net.sf.saxon.lib.StandardURIResolver,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2345, count=5}
{net.sf.saxon.lib.StandardURIResolver,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2345, count=5}
{net.sf.saxon.lib.StandardURIResolver,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2345, count=5}
{net.sf.saxon.lib.SerializerFactory,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2340, count=5}
{java.util.TreeMap$Entry,sun.security.x509.CertificatePoliciesExtension,java.util.ArrayList,java.lang.Object[]}, size=2340, count=10}
{net.sf.saxon.lib.SerializerFactory,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2340, count=5}
{net.sf.saxon.functions.ConstructorFunctionLibrary,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2340, count=5}
{net.sf.saxon.functions.ConstructorFunctionLibrary,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2340, count=5}
{net.sf.saxon.lib.SerializerFactory,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2340, count=5}
{net.sf.saxon.functions.ConstructorFunctionLibrary,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2340, count=5}
{com.sun.jmx.remote.util.ClassLogger,java.util.logging.Logger,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=2333, count=12}
{org.postgresql.core.v3.QueryExecutorImpl,org.postgresql.core.v3.ProtocolConnectionImpl,java.util.ArrayList,java.lang.Object[]}, size=2328, count=8}
{java.util.HashMap$Node,org.apache.xerces.impl.dtd.XMLDTDProcessor,java.util.ArrayList,java.lang.Object[]}, size=2317, count=7}
{java.util.HashMap$Node,org.apache.xerces.impl.dtd.XMLDTDProcessor,java.util.ArrayList,java.lang.Object[]}, size=2317, count=7}
{java.util.HashMap$Node,org.apache.xerces.impl.dtd.XMLDTDProcessor,java.util.ArrayList,java.lang.Object[]}, size=2317, count=7}
{java.util.HashMap$Node,org.apache.ignite.internal.binary.BinaryMetadata,java.util.ArrayList,java.lang.Object[]}, size=2313, count=9}
{SomeType108,org.dom4j.tree.DefaultDocument,java.util.ArrayList,java.lang.Object[]}, size=2290, count=5}
{ch.qos.logback.core.spi.ContextAwareBase,SomeType76,ch.qos.logback.classic.spi.TurboFilterList,java.lang.Object[]}, size=2288, count=28}
{ch.qos.logback.core.spi.ContextAwareBase,SomeType76,ch.qos.logback.classic.spi.TurboFilterList,java.lang.Object[]}, size=2288, count=28}
{java.util.HashMap$Node,SomeType63,java.util.ArrayList,java.lang.Object[]}, size=2280, count=10}
{org.apache.kafka.common.network.Selector$SelectorMetrics$1,org.apache.kafka.common.network.Selector,java.util.ArrayList,java.lang.Object[]}, size=2223, count=3}
{net.sf.saxon.PreparedStylesheet,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2220, count=5}
{net.sf.saxon.PreparedStylesheet,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2220, count=5}
{net.sf.saxon.PreparedStylesheet,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=2220, count=5}
{org.apache.ignite.internal.processors.cache.binary.BinaryMetadataHolder,org.apache.ignite.internal.binary.BinaryMetadata,java.util.ArrayList,java.lang.Object[]}, size=2205, count=9}
{sun.security.x509.AuthorityKeyIdentifierExtension,sun.security.x509.GeneralNames,java.util.ArrayList,java.lang.Object[]}, size=2170, count=10}
{org.apache.kafka.clients.consumer.internals.AbstractCoordinator$HeartbeatThread,org.apache.kafka.clients.consumer.internals.ConsumerCoordinator,java.util.ArrayList,java.lang.Object[]}, size=2168, count=4}
{org.dom4j.tree.DefaultElement,org.dom4j.tree.DefaultDocument,java.util.ArrayList,java.lang.Object[]}, size=2160, count=10}
{org.apache.kafka.clients.consumer.internals.AbstractCoordinator$GroupCoordinatorMetrics,org.apache.kafka.common.metrics.Sensor,java.util.ArrayList,java.lang.Object[]}, size=2160, count=2}
{java.security.ProtectionDomain,sun.misc.Launcher$ExtClassLoader,java.util.Vector,java.lang.Object[]}, size=2148, count=3}
{javax.management.openmbean.OpenMBeanParameterInfoSupport,javax.management.openmbean.SimpleType,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=2133, count=9}
{SomeType122,SomeType68,java.util.ArrayList,java.lang.Object[]}, size=2128, count=8}
{org.eclipse.jetty.server.AbstractConnector$Acceptor,org.eclipse.jetty.server.ServerConnector,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=2124, count=4}
{org.apache.kafka.common.network.Selector$SelectorMetrics$1,org.apache.kafka.common.network.Selector$SelectorMetrics,java.util.ArrayList,java.lang.Object[]}, size=2104, count=4}
{net.sf.saxon.style.XSLApplyTemplates,net.sf.saxon.style.XSLElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=2093, count=7}
{net.sf.saxon.style.XSLApplyTemplates,net.sf.saxon.style.XSLElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=2093, count=7}
{net.sf.saxon.style.XSLApplyTemplates,net.sf.saxon.style.XSLElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=2093, count=7}
{org.eclipse.wst.xml.xpath2.processor.internal.ast.FilterExpr,org.eclipse.wst.xml.xpath2.processor.internal.ast.FunctionCall,java.util.ArrayList,java.lang.Object[]}, size=2080, count=12}
{org.apache.xerces.dom.AttributeMap,org.apache.xerces.dom.PSVIElementNSImpl,org.apache.xerces.impl.xs.util.ObjectListImpl,java.lang.Object[]}, size=2076, count=6}
{java.util.HashMap$Node,org.apache.xerces.impl.validation.ValidationManager,java.util.ArrayList,java.lang.Object[]}, size=2072, count=12}
{java.util.HashMap$Node,org.apache.xerces.impl.validation.ValidationManager,java.util.ArrayList,java.lang.Object[]}, size=2072, count=12}
{java.util.HashMap$Node,SomeType62,java.util.ArrayList,java.lang.Object[]}, size=2068, count=3}
{org.apache.logging.log4j.core.pattern.LiteralPatternConverter,org.apache.logging.log4j.core.config.xml.XmlConfiguration,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=2048, count=32}
{ch.qos.logback.core.spi.AppenderAttachableImpl,ch.qos.logback.core.util.COWArrayList,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=2040, count=15}
{ch.qos.logback.core.spi.AppenderAttachableImpl,ch.qos.logback.core.util.COWArrayList,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=2040, count=15}
{ch.qos.logback.core.spi.AppenderAttachableImpl,ch.qos.logback.core.util.COWArrayList,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=2040, count=15}
{java.net.SocksSocketImpl,java.io.FileDescriptor,java.util.ArrayList,java.lang.Object[]}, size=2034, count=6}
{org.apache.kafka.clients.ClientRequest,org.apache.kafka.common.requests.RequestSend,org.apache.kafka.common.protocol.types.Struct,java.lang.Object[]}, size=2030, count=10}
{ch.qos.logback.core.rolling.helper.FileNamePattern,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=2028, count=6}
{ch.qos.logback.core.rolling.helper.FileNamePattern,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=2028, count=6}
{org.apache.xerces.parsers.XIncludeAwareParserConfiguration,org.apache.xerces.impl.dtd.XMLDTDProcessor,java.util.ArrayList,java.lang.Object[]}, size=2009, count=7}
{org.apache.xerces.parsers.XIncludeAwareParserConfiguration,org.apache.xerces.impl.dtd.XMLDTDProcessor,java.util.ArrayList,java.lang.Object[]}, size=2009, count=7}
{org.apache.xerces.parsers.XIncludeAwareParserConfiguration,org.apache.xerces.impl.dtd.XMLDTDProcessor,java.util.ArrayList,java.lang.Object[]}, size=2009, count=7}
{com.sun.org.apache.xerces.internal.impl.XMLEntityManager,com.sun.org.apache.xerces.internal.impl.XMLEntityScanner,com.sun.org.apache.xerces.internal.impl.XMLEntityManager,java.lang.Object[]}, size=1992, count=4}
{sun.rmi.runtime.Log$LoggerLog,java.util.logging.Logger,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=1991, count=10}
{org.eclipse.wst.xml.xpath2.processor.internal.ast.XPathExpr,org.eclipse.wst.xml.xpath2.processor.internal.ast.FilterExpr,java.util.ArrayList,java.lang.Object[]}, size=1984, count=16}
{ch.qos.logback.classic.encoder.PatternLayoutEncoder,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=1984, count=4}
{org.apache.xerces.dom.PSVIElementNSImpl,org.apache.xerces.dom.AttributeMap,java.util.ArrayList,java.lang.Object[]}, size=1980, count=6}
{org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1950, count=5}
{org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1950, count=5}
{org.apache.ignite.internal.processors.cache.GridCachePartitionExchangeManager,org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionsExchangeFuture,java.util.ArrayList,java.lang.Object[]}, size=1947, count=1}
{org.springframework.beans.factory.config.BeanExpressionContext,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=1946, count=2}
{java.util.HashMap$Node,org.apache.xerces.impl.validation.ValidationManager,java.util.ArrayList,java.lang.Object[]}, size=1946, count=11}
{oracle.jrockit.jfr.NativeProducerDescriptor,java.util.Collections$UnmodifiableRandomAccessList,java.util.ArrayList,java.lang.Object[]}, size=1940, count=1}
{org.springframework.context.expression.BeanFactoryResolver,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=1930, count=2}
{org.apache.kafka.common.network.Selector,org.apache.kafka.common.network.Selector$SelectorMetrics,java.util.ArrayList,java.lang.Object[]}, size=1927, count=3}
{org.apache.logging.log4j.core.config.LoggerConfig,org.apache.logging.log4j.core.config.xml.XmlConfiguration,java.util.ArrayList,java.lang.Object[]}, size=1920, count=4}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=1908, count=4}
{org.apache.xerces.impl.xs.opti.SchemaParsingConfig,org.apache.xerces.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=1908, count=4}
{java.util.concurrent.ConcurrentHashMap$Node,java.util.concurrent.ConcurrentHashMap$Node,org.springframework.beans.factory.support.RootBeanDefinition,java.lang.Object[]}, size=1871, count=5}
{com.mchange.v2.c3p0.management.DynamicPooledDataSourceManagerMBean,javax.management.MBeanInfo,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=1864, count=8}
{org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTxLocal,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=1850, count=5}
{org.apache.kafka.common.requests.RequestSend,org.apache.kafka.common.requests.RequestHeader,org.apache.kafka.common.protocol.types.Struct,java.lang.Object[]}, size=1850, count=10}
{org.apache.logging.log4j.core.LoggerContext,org.apache.logging.log4j.core.config.xml.XmlConfiguration,java.util.ArrayList,java.lang.Object[]}, size=1848, count=4}
{javax.management.openmbean.OpenMBeanAttributeInfoSupport,javax.management.openmbean.CompositeType,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=1848, count=7}
{com.sun.org.apache.xerces.internal.impl.XMLEntityManager,com.sun.org.apache.xerces.internal.util.AugmentationsImpl,com.sun.org.apache.xerces.internal.util.AugmentationsImpl$SmallContainer,java.lang.Object[]}, size=1844, count=4}
{org.apache.kafka.clients.consumer.internals.Fetcher$1,org.apache.kafka.common.requests.FetchRequest,org.apache.kafka.common.protocol.types.Struct,java.lang.Object[]}, size=1840, count=10}
{net.sf.saxon.event.PipelineConfiguration,net.sf.saxon.Controller,net.sf.saxon.expr.instruct.Bindery,long[]}, size=1820, count=4}
{net.sf.saxon.event.PipelineConfiguration,net.sf.saxon.Controller,net.sf.saxon.expr.instruct.Bindery,long[]}, size=1820, count=4}
{net.sf.saxon.event.PipelineConfiguration,net.sf.saxon.Controller,net.sf.saxon.expr.instruct.Bindery,long[]}, size=1820, count=4}
{java.util.Hashtable$Entry,org.apache.xerces.impl.xs.SchemaGrammar,java.util.Vector,java.lang.Object[]}, size=1818, count=3}
{java.util.logging.LogManager$LoggerWeakRef,java.util.logging.Logger,java.util.ArrayList,java.lang.Object[]}, size=1808, count=6}
{com.sun.org.apache.xerces.internal.impl.xpath.regex.RegularExpression,com.sun.org.apache.xerces.internal.impl.xpath.regex.Token$UnionToken,java.util.ArrayList,java.lang.Object[]}, size=1806, count=7}
{org.apache.ignite.internal.processors.job.GridJobProcessor,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1805, count=1}
{com.sun.org.apache.xerces.internal.impl.XMLEntityScanner,com.sun.org.apache.xerces.internal.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=1800, count=4}
{org.h2.mvstore.Page,org.h2.mvstore.MVMap,org.h2.mvstore.Page,java.lang.Object[]}, size=1790, count=7}
{org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,org.apache.ignite.internal.processors.cache.persistence.MemoryMetricsImpl,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=1784, count=2}
{org.apache.ignite.internal.processors.query.h2.IgniteH2Indexing,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1778, count=1}
{net.sf.saxon.event.PipelineConfiguration,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=1776, count=4}
{net.sf.saxon.event.PipelineConfiguration,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=1776, count=4}
{net.sf.saxon.event.PipelineConfiguration,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=1776, count=4}
{org.apache.ignite.internal.managers.deployment.GridDeploymentClassLoader,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1773, count=1}
{org.dom4j.tree.DefaultDocument,org.dom4j.tree.DefaultElement,java.util.ArrayList,java.lang.Object[]}, size=1768, count=4}
{org.apache.ignite.internal.managers.communication.GridIoManager,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1766, count=1}
{org.apache.ignite.internal.IgniteKernal,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1763, count=1}
{org.apache.kafka.clients.consumer.internals.ConsumerCoordinator,org.apache.kafka.clients.Metadata,java.util.ArrayList,java.lang.Object[]}, size=1760, count=4}
{java.util.concurrent.atomic.AtomicReference,org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionsExchangeFuture,java.util.ArrayList,java.lang.Object[]}, size=1739, count=1}
{net.sf.saxon.style.XSLAttribute,net.sf.saxon.style.LiteralResultElement,java.util.ArrayList,java.lang.Object[]}, size=1720, count=5}
{net.sf.saxon.style.XSLCallTemplate,net.sf.saxon.style.LiteralResultElement,java.util.ArrayList,java.lang.Object[]}, size=1720, count=5}
{net.sf.saxon.style.XSLAttribute,net.sf.saxon.style.LiteralResultElement,java.util.ArrayList,java.lang.Object[]}, size=1720, count=5}
{net.sf.saxon.style.XSLCallTemplate,net.sf.saxon.style.LiteralResultElement,java.util.ArrayList,java.lang.Object[]}, size=1720, count=5}
{net.sf.saxon.style.XSLCallTemplate,net.sf.saxon.style.LiteralResultElement,java.util.ArrayList,java.lang.Object[]}, size=1720, count=5}
{net.sf.saxon.style.XSLAttribute,net.sf.saxon.style.LiteralResultElement,java.util.ArrayList,java.lang.Object[]}, size=1720, count=5}
{org.apache.ignite.internal.processors.continuous.GridContinuousProcessor,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1711, count=1}
{net.sf.saxon.style.XSLPackage,net.sf.saxon.style.PackageVersion,java.util.ArrayList,java.lang.Object[]}, size=1710, count=5}
{net.sf.saxon.style.XSLPackage,net.sf.saxon.style.PackageVersion,java.util.ArrayList,java.lang.Object[]}, size=1710, count=5}
{net.sf.saxon.style.XSLPackage,net.sf.saxon.style.PackageVersion,java.util.ArrayList,java.lang.Object[]}, size=1710, count=5}
{SomeType133,org.apache.ignite.internal.processors.cache.persistence.GridCacheDatabaseSharedManager,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=1709, count=1}
{org.apache.ignite.internal.processors.service.GridServiceProcessor,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1698, count=1}
{SomeType147,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1690, count=1}
{org.apache.logging.log4j.core.util.datetime.FastDateFormat,org.apache.logging.log4j.core.util.datetime.FastDateParser,sun.util.calendar.ZoneInfo,long[]}, size=1686, count=2}
{scala.util.parsing.combinator.Parsers$Failure,scala.util.parsing.json.Lexer,scala.collection.mutable.HashSet,java.lang.Object[]}, size=1686, count=2}
{org.apache.ignite.internal.processors.rest.GridRestProcessor,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1682, count=1}
{org.apache.ignite.internal.managers.eventstorage.GridEventStorageManager,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1682, count=1}
{org.apache.ignite.internal.processors.cache.ClusterCachesInfo,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1675, count=1}
{net.sf.saxon.functions.NameFn,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=1674, count=6}
{net.sf.saxon.functions.NameFn,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=1674, count=6}
{net.sf.saxon.functions.NameFn,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=1674, count=6}
{org.apache.ignite.internal.cluster.IgniteClusterImpl,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1674, count=1}
{ch.qos.logback.classic.PatternLayout,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=1672, count=4}
{org.apache.ignite.internal.processors.task.GridTaskProcessor,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1668, count=1}
{org.apache.kafka.clients.consumer.internals.AbstractCoordinator$GroupCoordinatorMetrics,org.apache.kafka.clients.consumer.internals.ConsumerCoordinator,java.util.ArrayList,java.lang.Object[]}, size=1668, count=4}
{org.apache.ignite.internal.managers.deployment.GridDeploymentPerVersionStore,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1662, count=1}
{ch.qos.logback.core.rolling.RollingFileAppender,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=1662, count=3}
{org.apache.ignite.internal.processors.cache.binary.BinaryMetadataTransport,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1660, count=1}
{org.apache.ignite.internal.processors.cluster.ClusterProcessor,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1658, count=1}
{org.apache.ignite.internal.managers.deployment.GridDeploymentManager,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1652, count=1}
{SomeType158,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1651, count=1}
{org.apache.ignite.internal.processors.query.h2.twostep.GridReduceQueryExecutor,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1650, count=1}
{SomeType159,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1650, count=1}
{org.apache.ignite.internal.processors.query.h2.twostep.GridMapQueryExecutor,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1650, count=1}
{org.apache.ignite.internal.managers.checkpoint.GridCheckpointManager,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1644, count=1}
{org.apache.ignite.internal.processors.datastructures.DataStructuresProcessor,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1643, count=1}
{SomeType160,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1642, count=1}
{org.apache.ignite.internal.processors.jobmetrics.GridJobMetricsProcessor,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1642, count=1}
{org.apache.ignite.internal.cluster.ClusterNodeLocalMapImpl,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1638, count=1}
{org.apache.ignite.internal.processors.resource.GridResourceProcessor,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1634, count=1}
{com.sun.xml.internal.stream.XMLEntityStorage,com.sun.org.apache.xerces.internal.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=1628, count=4}
{org.apache.ignite.internal.processors.timeout.GridTimeoutProcessor,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1626, count=1}
{org.apache.ignite.internal.processors.plugin.IgnitePluginProcessor,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1626, count=1}
{org.apache.ignite.internal.managers.deployment.GridDeploymentCommunication,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1626, count=1}
{org.apache.kafka.clients.consumer.internals.AbstractCoordinator$HeartbeatThread,org.apache.kafka.clients.consumer.internals.ConsumerCoordinator,java.util.ArrayList,java.lang.Object[]}, size=1626, count=3}
{org.apache.ignite.internal.managers.deployment.GridDeploymentLocalStore,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1626, count=1}
{org.apache.xerces.impl.xs.opti.SchemaParsingConfig,org.apache.xerces.impl.validation.ValidationManager,java.util.ArrayList,java.lang.Object[]}, size=1625, count=5}
{org.apache.xerces.impl.xs.opti.SchemaParsingConfig,org.apache.xerces.impl.validation.ValidationManager,java.util.ArrayList,java.lang.Object[]}, size=1625, count=5}
{org.apache.logging.log4j.core.pattern.PatternParser,org.apache.logging.log4j.core.config.xml.XmlConfiguration,java.util.ArrayList,java.lang.Object[]}, size=1624, count=4}
{org.apache.ignite.internal.managers.collision.GridCollisionManager,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1620, count=1}
{org.apache.ignite.internal.managers.indexing.GridIndexingManager,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1620, count=1}
{org.apache.ignite.internal.processors.closure.GridClosureProcessor,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1619, count=1}
{SomeType156,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1619, count=1}
{org.apache.ignite.internal.processors.pool.PoolProcessor,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1618, count=1}
{org.apache.ignite.internal.processors.port.GridPortProcessor,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1618, count=1}
{org.apache.ignite.internal.processors.cache.persistence.GridCacheDatabaseSharedManager$FileLockHolder,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1618, count=1}
{org.apache.ignite.internal.processors.affinity.GridAffinityProcessor,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1618, count=1}
{org.apache.ignite.internal.processors.rest.handlers.task.GridTaskCommandHandler,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1614, count=1}
{org.apache.ignite.internal.managers.failover.GridFailoverManager,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1612, count=1}
{org.apache.ignite.internal.managers.loadbalancer.GridLoadBalancerManager,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1612, count=1}
{org.apache.ignite.internal.processors.cache.binary.BinaryMetadataFileStore,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1610, count=1}
{SomeType136,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1610, count=1}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.util.Vector,java.lang.Object[]}, size=1610, count=5}
{org.apache.ignite.internal.processors.session.GridTaskSessionProcessor,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1610, count=1}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.util.Vector,java.lang.Object[]}, size=1610, count=5}
{org.apache.ignite.internal.processors.odbc.ClientListenerNioListener,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1606, count=1}
{org.apache.kafka.clients.consumer.internals.ConsumerCoordinator$ConsumerCoordinatorMetrics,org.apache.kafka.clients.consumer.internals.ConsumerCoordinator,java.util.ArrayList,java.lang.Object[]}, size=1604, count=4}
{org.apache.ignite.internal.processors.cache.persistence.GridCacheDatabaseSharedManager$Checkpointer,org.apache.ignite.internal.processors.cache.persistence.GridCacheDatabaseSharedManager,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=1604, count=1}
{org.springframework.beans.factory.support.DisposableBeanAdapter,org.springframework.http.converter.StringHttpMessageConverter,java.util.ArrayList,java.lang.Object[]}, size=1603, count=1}
{org.apache.ignite.internal.processors.hadoop.HadoopNoopProcessor,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1602, count=1}
{org.apache.ignite.internal.processors.rest.handlers.query.QueryCommandHandler,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1602, count=1}
{org.apache.ignite.internal.processors.igfs.IgfsNoopProcessor,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1602, count=1}
{org.apache.ignite.internal.processors.rest.handlers.redis.string.GridRedisSetCommandHandler,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1602, count=1}
{org.apache.ignite.internal.processors.platform.PlatformNoopProcessor,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1602, count=1}
{org.apache.ignite.internal.processors.rest.handlers.redis.string.GridRedisGetCommandHandler,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1602, count=1}
{SomeType155,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1602, count=1}
{org.apache.kafka.common.network.Selector$SelectorMetrics$1,org.apache.kafka.common.network.Selector$SelectorMetrics,java.util.ArrayList,java.lang.Object[]}, size=1600, count=3}
{SomeType123,SomeType68,java.util.ArrayList,java.lang.Object[]}, size=1596, count=6}
{SomeType145,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1594, count=1}
{SomeType144,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1594, count=1}
{org.apache.ignite.internal.processors.rest.handlers.task.GridTaskCommandHandler$1,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1594, count=1}
{org.apache.ignite.internal.processors.rest.handlers.log.GridLogCommandHandler,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1594, count=1}
{org.apache.ignite.internal.processors.rest.handlers.cluster.GridChangeStateCommandHandler,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1594, count=1}
{org.apache.ignite.internal.processors.query.h2.twostep.GridMapQueryExecutor$2,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1594, count=1}
{org.apache.ignite.internal.processors.rest.handlers.version.GridVersionCommandHandler,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1594, count=1}
{org.apache.ignite.internal.GridPluginContext,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1594, count=1}
{org.apache.ignite.internal.processors.query.h2.twostep.GridReduceQueryExecutor$2,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1594, count=1}
{org.apache.ignite.internal.processors.cache.binary.IgniteBinaryImpl,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1594, count=1}
{org.apache.ignite.internal.processors.rest.handlers.top.GridTopologyCommandHandler,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1594, count=1}
{org.apache.ignite.internal.processors.rest.handlers.datastructures.DataStructuresCommandHandler,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1594, count=1}
{org.apache.ignite.internal.processors.query.h2.ddl.DdlStatementsProcessor,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1594, count=1}
{org.apache.ignite.internal.processors.rest.handlers.cache.GridCacheCommandHandler,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1594, count=1}
{SomeType141,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1586, count=1}
{org.apache.ignite.internal.processors.rest.handlers.redis.GridRedisConnectionCommandHandler,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1586, count=1}
{org.apache.ignite.internal.IgniteSchedulerImpl,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1586, count=1}
{SomeType140,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1586, count=1}
{java.util.HashMap$Node,com.sun.org.apache.xerces.internal.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=1572, count=4}
{ch.qos.logback.core.spi.ContextAwareBase,SomeType76,ch.qos.logback.classic.spi.TurboFilterList,java.lang.Object[]}, size=1568, count=28}
{com.google.common.util.concurrent.AbstractExecutionThreadService$1,java.util.Collections$SynchronizedRandomAccessList,java.util.ArrayList,java.lang.Object[]}, size=1568, count=4}
{org.apache.logging.log4j.core.util.datetime.FastDateFormat,org.apache.logging.log4j.core.util.datetime.FastDatePrinter,sun.util.calendar.ZoneInfo,long[]}, size=1564, count=2}
{org.apache.ignite.internal.processors.cache.GridCacheProcessor,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1562, count=1}
{org.apache.ignite.internal.processors.cluster.GridClusterStateProcessor,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1562, count=1}
{org.apache.ignite.internal.processors.odbc.ClientListenerProcessor,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1562, count=1}
{org.apache.ignite.internal.processors.marshaller.GridMarshallerMappingProcessor,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1562, count=1}
{org.apache.ignite.internal.processors.cache.persistence.MemoryPolicy,org.apache.ignite.internal.processors.cache.persistence.MemoryMetricsImpl,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=1562, count=2}
{org.apache.ignite.internal.processors.schedule.IgniteScheduleProcessor,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1562, count=1}
{org.apache.ignite.internal.processors.cache.binary.CacheObjectBinaryProcessorImpl,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1562, count=1}
{org.apache.ignite.internal.managers.deployment.GridDeploymentPerLoaderStore,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1562, count=1}
{org.apache.ignite.internal.processors.query.GridQueryProcessor,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1562, count=1}
{org.apache.ignite.internal.processors.marshaller.MarshallerMappingTransport,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1562, count=1}
{SomeType135,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1562, count=1}
{SomeType137,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1562, count=1}
{org.apache.ignite.internal.processors.rest.protocols.tcp.GridTcpRestProtocol,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1562, count=1}
{org.apache.ignite.internal.processors.rest.protocols.http.jetty.GridJettyRestProtocol,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1562, count=1}
{org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1562, count=1}
{org.apache.ignite.internal.processors.cache.GridCacheSharedContext,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1562, count=1}
{org.apache.ignite.internal.processors.datastreamer.DataStreamProcessor,org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1562, count=1}
{org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1560, count=4}
{org.h2.table.Column,org.apache.ignite.internal.processors.query.h2.opt.GridH2MetaTable,java.util.ArrayList,java.lang.Object[]}, size=1560, count=4}
{ch.qos.logback.core.rolling.FixedWindowRollingPolicy,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=1557, count=3}
{java.util.HashMap$Node,org.apache.ignite.internal.processors.cache.persistence.MemoryMetricsImpl,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=1554, count=2}
{org.apache.ignite.spi.loadbalancing.roundrobin.RoundRobinGlobalLoadBalancer,org.apache.ignite.spi.loadbalancing.roundrobin.RoundRobinGlobalLoadBalancer$GridNodeList,java.util.ArrayList,java.lang.Object[]}, size=1552, count=1}
{com.sun.org.apache.xerces.internal.impl.XMLDocumentFragmentScannerImpl$ElementStack,com.sun.org.apache.xerces.internal.impl.XMLNSDocumentScannerImpl,java.util.ArrayList,java.lang.Object[]}, size=1548, count=2}
{org.apache.ignite.internal.processors.cache.persistence.MemoryMetricsImpl,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,org.apache.ignite.internal.mem.unsafe.UnsafeMemoryProvider,long[]}, size=1546, count=2}
{SomeType133$SnapshotWorker,SomeType139,java.util.ArrayList,java.lang.Object[]}, size=1545, count=1}
{java.util.LinkedList$Node,org.apache.ignite.internal.processors.cache.persistence.GridCacheDatabaseSharedManager,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=1545, count=1}
{org.apache.xerces.impl.XMLEntityManager,org.apache.xerces.impl.validation.ValidationManager,java.util.ArrayList,java.lang.Object[]}, size=1544, count=12}
{org.apache.xerces.impl.XMLEntityManager,org.apache.xerces.impl.validation.ValidationManager,java.util.ArrayList,java.lang.Object[]}, size=1544, count=12}
{com.sun.xml.bind.v2.model.annotation.XmlSchemaQuick,com.sun.xml.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=1540, count=5}
{com.sun.xml.bind.v2.model.annotation.XmlSchemaQuick,com.sun.xml.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=1540, count=5}
{com.sun.xml.bind.v2.model.annotation.LocatableAnnotation,com.sun.xml.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=1540, count=5}
{org.apache.kafka.clients.consumer.internals.AbstractCoordinator$GroupCoordinatorMetrics$1,org.apache.kafka.clients.consumer.internals.ConsumerCoordinator,java.util.ArrayList,java.lang.Object[]}, size=1540, count=4}
{com.sun.xml.bind.v2.model.annotation.XmlSchemaQuick,com.sun.xml.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=1540, count=5}
{org.apache.kafka.clients.consumer.internals.ConsumerCoordinator$ConsumerCoordinatorMetrics$1,org.apache.kafka.clients.consumer.internals.ConsumerCoordinator,java.util.ArrayList,java.lang.Object[]}, size=1540, count=4}
{com.sun.xml.bind.v2.model.annotation.LocatableAnnotation,com.sun.xml.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=1540, count=5}
{com.sun.xml.bind.v2.model.annotation.LocatableAnnotation,com.sun.xml.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.FinalArrayList,java.lang.Object[]}, size=1540, count=5}
{org.apache.ignite.internal.processors.cache.persistence.GridCacheDatabaseSharedManager$CheckpointHistory,org.apache.ignite.internal.processors.cache.persistence.GridCacheDatabaseSharedManager,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=1537, count=1}
{org.apache.ignite.internal.processors.cache.persistence.GridCacheDatabaseSharedManager$5,org.apache.ignite.internal.processors.cache.persistence.GridCacheDatabaseSharedManager,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=1533, count=1}
{com.sun.org.apache.xerces.internal.impl.XMLDocumentFragmentScannerImpl$ElementStack2,com.sun.org.apache.xerces.internal.impl.XMLNSDocumentScannerImpl,java.util.ArrayList,java.lang.Object[]}, size=1532, count=2}
{org.apache.ignite.internal.processors.cache.persistence.MemoryMetricsMXBeanImpl,org.apache.ignite.internal.processors.cache.persistence.MemoryMetricsImpl,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=1530, count=2}
{java.util.HashMap$Node,com.sun.org.apache.xerces.internal.impl.XMLNSDocumentScannerImpl,java.util.ArrayList,java.lang.Object[]}, size=1516, count=2}
{SomeType124,SomeType64,java.util.ArrayList,java.lang.Object[]}, size=1512, count=2}
{java.lang.Package,sun.misc.Launcher$ExtClassLoader,java.util.Vector,java.lang.Object[]}, size=1508, count=2}
{org.apache.kafka.clients.consumer.internals.ConsumerCoordinator$1,org.apache.kafka.clients.consumer.internals.ConsumerCoordinator,java.util.ArrayList,java.lang.Object[]}, size=1508, count=4}
{org.apache.ignite.internal.processors.cache.GridCacheSharedContext,org.apache.ignite.internal.processors.cache.persistence.GridCacheDatabaseSharedManager,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=1505, count=1}
{ch.qos.logback.core.joran.spi.Interpreter,ch.qos.logback.core.joran.spi.EventPlayer,java.util.ArrayList,java.lang.Object[]}, size=1484, count=1}
{ch.qos.logback.core.joran.spi.Interpreter,ch.qos.logback.core.joran.spi.EventPlayer,java.util.ArrayList,java.lang.Object[]}, size=1484, count=1}
{ch.qos.logback.core.joran.spi.Interpreter,ch.qos.logback.core.joran.spi.EventPlayer,java.util.ArrayList,java.lang.Object[]}, size=1484, count=1}
{com.sun.org.apache.xerces.internal.impl.XMLDocumentScannerImpl$PrologDriver,com.sun.org.apache.xerces.internal.impl.XMLNSDocumentScannerImpl,java.util.ArrayList,java.lang.Object[]}, size=1476, count=2}
{com.sun.org.apache.xerces.internal.impl.XMLDocumentScannerImpl$TrailingMiscDriver,com.sun.org.apache.xerces.internal.impl.XMLNSDocumentScannerImpl,java.util.ArrayList,java.lang.Object[]}, size=1476, count=2}
{com.sun.org.apache.xerces.internal.impl.XMLDocumentScannerImpl$XMLDeclDriver,com.sun.org.apache.xerces.internal.impl.XMLNSDocumentScannerImpl,java.util.ArrayList,java.lang.Object[]}, size=1476, count=2}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDocumentInfo,java.util.Vector,java.lang.Object[]}, size=1470, count=5}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDocumentInfo,java.util.Vector,java.lang.Object[]}, size=1470, count=5}
{net.sf.saxon.style.XSLIf,net.sf.saxon.style.XSLAttribute,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=1466, count=5}
{net.sf.saxon.style.XSLIf,net.sf.saxon.style.XSLAttribute,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=1466, count=5}
{net.sf.saxon.style.XSLIf,net.sf.saxon.style.XSLAttribute,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=1466, count=5}
{com.sun.xml.internal.bind.v2.model.impl.RuntimeEnumConstantImpl,com.sun.xml.internal.bind.v2.model.impl.RuntimeEnumLeafInfoImpl,java.util.EnumMap,java.lang.Object[]}, size=1465, count=5}
{SomeType102,SomeType64,java.util.ArrayList,java.lang.Object[]}, size=1464, count=2}
{org.apache.xerces.impl.XMLEntityManager,org.apache.xerces.impl.validation.ValidationManager,java.util.ArrayList,java.lang.Object[]}, size=1462, count=11}
{java.util.HashMap$Node,org.apache.ignite.internal.processors.cache.persistence.MemoryPolicy,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=1462, count=2}
{jdk.internal.instrumentation.Tracer$InstrumentationData,jdk.internal.instrumentation.Tracer,java.util.ArrayList,java.lang.Object[]}, size=1456, count=7}
{org.apache.xerces.impl.xs.traversers.XSDTypeAlternativeTraverser,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1450, count=5}
{org.apache.xerces.impl.xs.traversers.XSDElementTraverser,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1450, count=5}
{org.apache.xerces.impl.xs.traversers.XSDTypeAlternativeTraverser,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1450, count=5}
{org.apache.xerces.impl.xs.traversers.XSDElementTraverser,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1450, count=5}
{ch.qos.logback.core.recovery.ResilientFileOutputStream,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=1449, count=3}
{org.apache.xerces.impl.xs.traversers.XSDGroupTraverser,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1445, count=5}
{org.apache.xerces.impl.xs.traversers.XSDGroupTraverser,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1445, count=5}
{com.google.common.util.concurrent.ServiceManager$ServiceManagerState$2,com.google.common.util.concurrent.ServiceManager$ServiceManagerState,java.util.IdentityHashMap,java.lang.Object[]}, size=1444, count=2}
{com.google.common.util.concurrent.ServiceManager$ServiceManagerState$3,com.google.common.util.concurrent.ServiceManager$ServiceManagerState,java.util.IdentityHashMap,java.lang.Object[]}, size=1444, count=2}
{org.apache.kafka.clients.consumer.internals.ConsumerCoordinator$ConsumerCoordinatorMetrics,org.apache.kafka.common.metrics.Sensor,java.util.ArrayList,java.lang.Object[]}, size=1440, count=4}
{org.apache.kafka.clients.consumer.KafkaConsumer,org.apache.kafka.clients.Metadata,java.util.ArrayList,java.lang.Object[]}, size=1440, count=4}
{java.lang.ref.WeakReference,com.google.common.util.concurrent.ServiceManager$ServiceManagerState,java.util.IdentityHashMap,java.lang.Object[]}, size=1436, count=2}
{java.util.TreeMap$Entry,sun.security.x509.ExtendedKeyUsageExtension,java.util.Vector,java.lang.Object[]}, size=1428, count=6}
{org.apache.logging.log4j.core.appender.AsyncAppender$AsyncThread,org.apache.logging.log4j.core.appender.AsyncAppender,java.util.concurrent.ArrayBlockingQueue,java.lang.Object[]}, size=1423, count=1}
{net.sf.saxon.serialize.XMLIndenter,net.sf.saxon.serialize.XMLEmitter,java.util.Stack,java.lang.Object[]}, size=1422, count=2}
{org.apache.kafka.clients.ClientRequest,org.apache.kafka.common.requests.RequestSend,org.apache.kafka.common.protocol.types.Struct,java.lang.Object[]}, size=1421, count=7}
{org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl$PagePool,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=1414, count=2}
{ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=1413, count=3}
{org.apache.kafka.clients.consumer.KafkaConsumer,org.apache.kafka.clients.consumer.internals.ConsumerCoordinator,java.util.ArrayList,java.lang.Object[]}, size=1412, count=4}
{org.apache.xerces.impl.xs.traversers.XSDSimpleTypeTraverser,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1410, count=5}
{java.lang.ref.Finalizer,java.util.concurrent.ThreadPoolExecutor,java.util.concurrent.ArrayBlockingQueue,java.lang.Object[]}, size=1410, count=2}
{org.apache.xerces.impl.xs.traversers.XSDSimpleTypeTraverser,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1410, count=5}
{org.apache.xerces.impl.xs.traversers.XSDAttributeTraverser,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1405, count=5}
{org.apache.xerces.impl.xs.traversers.XSDKeyrefTraverser,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1405, count=5}
{org.apache.xerces.impl.xs.traversers.XSDUniqueOrKeyTraverser,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1405, count=5}
{org.apache.xerces.impl.xs.traversers.XSDNotationTraverser,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1405, count=5}
{org.apache.xerces.impl.xs.traversers.XSDWildcardTraverser,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1405, count=5}
{org.apache.xerces.impl.xs.traversers.XSDNotationTraverser,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1405, count=5}
{org.apache.xerces.impl.xs.traversers.XSDWildcardTraverser,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1405, count=5}
{org.apache.xerces.impl.xs.traversers.XSDUniqueOrKeyTraverser,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1405, count=5}
{org.apache.xerces.impl.xs.traversers.XSDKeyrefTraverser,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1405, count=5}
{org.apache.kafka.common.Cluster,java.util.Collections$UnmodifiableRandomAccessList,java.util.ArrayList,java.lang.Object[]}, size=1405, count=5}
{org.apache.xerces.impl.xs.traversers.XSDAttributeTraverser,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1405, count=5}
{org.apache.xerces.impl.xs.traversers.XSDAttributeGroupTraverser,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1405, count=5}
{org.apache.xerces.impl.xs.traversers.XSDAttributeGroupTraverser,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1405, count=5}
{com.google.common.util.concurrent.ServiceManager,com.google.common.util.concurrent.ServiceManager$ServiceManagerState,java.util.IdentityHashMap,java.lang.Object[]}, size=1404, count=2}
{net.sf.saxon.expr.StringLiteral,net.sf.saxon.style.XSLElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=1404, count=4}
{net.sf.saxon.expr.StringLiteral,net.sf.saxon.style.XSLElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=1404, count=4}
{net.sf.saxon.expr.StringLiteral,net.sf.saxon.style.XSLElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=1404, count=4}
{com.google.common.util.concurrent.ServiceManager$ServiceManagerState$1,com.google.common.util.concurrent.ServiceManager$ServiceManagerState,java.util.IdentityHashMap,java.lang.Object[]}, size=1388, count=2}
{ch.qos.logback.core.rolling.helper.Compressor,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=1386, count=3}
{net.sf.saxon.style.XSLApplyTemplates,net.sf.saxon.style.LiteralResultElement,java.util.ArrayList,java.lang.Object[]}, size=1376, count=4}
{net.sf.saxon.style.XSLApplyTemplates,net.sf.saxon.style.LiteralResultElement,java.util.ArrayList,java.lang.Object[]}, size=1376, count=4}
{net.sf.saxon.style.XSLApplyTemplates,net.sf.saxon.style.LiteralResultElement,java.util.ArrayList,java.lang.Object[]}, size=1376, count=4}
{java.util.logging.LogManager$LoggerWeakRef,java.util.logging.LogManager$RootLogger,java.util.ArrayList,java.lang.Object[]}, size=1374, count=2}
{net.sf.saxon.style.XSLOutput,net.sf.saxon.style.XSLPackage,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=1370, count=5}
{net.sf.saxon.style.StylesheetPackage,net.sf.saxon.style.XSLPackage,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=1370, count=5}
{net.sf.saxon.style.StylesheetPackage,net.sf.saxon.style.XSLPackage,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=1370, count=5}
{net.sf.saxon.style.XSLOutput,net.sf.saxon.style.XSLPackage,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=1370, count=5}
{net.sf.saxon.style.StylesheetPackage,net.sf.saxon.style.XSLPackage,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=1370, count=5}
{net.sf.saxon.style.XSLOutput,net.sf.saxon.style.XSLPackage,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=1370, count=5}
{ch.qos.logback.core.joran.action.AppenderRefAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=1365, count=3}
{ch.qos.logback.core.joran.action.NOPAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=1362, count=3}
{ch.qos.logback.core.rolling.helper.RenameUtil,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=1362, count=3}
{ch.qos.logback.core.joran.action.PropertyAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=1362, count=3}
{org.apache.kafka.clients.consumer.internals.Fetcher,org.apache.kafka.clients.Metadata,java.util.ArrayList,java.lang.Object[]}, size=1360, count=4}
{org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,org.apache.ignite.internal.mem.unsafe.UnsafeMemoryProvider,java.util.ArrayList,java.lang.Object[]}, size=1360, count=2}
{net.sf.saxon.tree.tiny.TinyTree,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=1358, count=2}
{org.apache.ignite.internal.processors.cache.persistence.MemoryPolicy,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,org.apache.ignite.internal.mem.unsafe.UnsafeMemoryProvider,long[]}, size=1358, count=2}
{net.sf.saxon.tree.tiny.TinyTree,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=1358, count=2}
{net.sf.saxon.tree.tiny.TinyTree,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=1358, count=2}
{SomeType93,SomeType15,java.util.ArrayList,java.lang.Object[]}, size=1357, count=1}
{SomeType125,SomeType66,java.util.ArrayList,java.lang.Object[]}, size=1352, count=4}
{ch.qos.logback.classic.encoder.PatternLayoutEncoder,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=1352, count=4}
{ch.qos.logback.classic.PatternLayout,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=1352, count=4}
{ch.qos.logback.classic.encoder.PatternLayoutEncoder,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=1352, count=4}
{ch.qos.logback.classic.PatternLayout,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=1352, count=4}
{org.eclipse.jetty.io.SelectorManager$ManagedSelector,org.eclipse.jetty.util.ConcurrentArrayQueue,java.util.concurrent.atomic.AtomicReferenceArray,java.lang.Object[]}, size=1328, count=4}
{org.apache.kafka.clients.consumer.internals.ConsumerCoordinator,org.apache.kafka.clients.Metadata,java.util.ArrayList,java.lang.Object[]}, size=1320, count=3}
{org.apache.xerces.impl.xs.XMLSchemaLoaderExt,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=1320, count=1}
{org.apache.xerces.impl.xs.XMLSchemaLoaderExt,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=1320, count=1}
{org.apache.logging.log4j.core.config.LoggerConfig,java.util.concurrent.CopyOnWriteArraySet,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=1314, count=9}
{java.util.concurrent.locks.AbstractQueuedSynchronizer$Node,org.apache.logging.log4j.core.appender.AsyncAppender$AsyncThread,java.util.concurrent.ArrayBlockingQueue,java.lang.Object[]}, size=1312, count=1}
{sun.security.provider.certpath.X509CertPath,java.util.Collections$UnmodifiableRandomAccessList,java.util.ArrayList,java.lang.Object[]}, size=1312, count=6}
{org.apache.logging.log4j.core.appender.AsyncAppender,org.apache.logging.log4j.core.appender.AsyncAppender$AsyncThread,java.util.concurrent.ArrayBlockingQueue,java.lang.Object[]}, size=1312, count=1}
{com.sun.xml.internal.bind.v2.model.impl.RuntimeAttributePropertyInfoImpl,com.sun.xml.internal.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.internal.FinalArrayList,java.lang.Object[]}, size=1309, count=3}
{SomeType20,SomeType20,java.util.ArrayList,java.lang.Object[]}, size=1306, count=1}
{SomeType97,SomeType22,java.util.ArrayList,java.lang.Object[]}, size=1301, count=1}
{org.apache.xerces.impl.xs.opti.SchemaParsingConfig,org.apache.xerces.impl.validation.ValidationManager,java.util.ArrayList,java.lang.Object[]}, size=1300, count=4}
{org.apache.logging.log4j.core.config.AppenderControl,org.apache.logging.log4j.core.appender.AsyncAppender,java.util.concurrent.ArrayBlockingQueue,java.lang.Object[]}, size=1295, count=1}
{org.apache.kafka.common.requests.RequestSend,org.apache.kafka.common.requests.RequestHeader,org.apache.kafka.common.protocol.types.Struct,java.lang.Object[]}, size=1295, count=7}
{org.apache.kafka.clients.consumer.internals.Fetcher$1,org.apache.kafka.common.requests.FetchRequest,org.apache.kafka.common.protocol.types.Struct,java.lang.Object[]}, size=1288, count=7}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.util.Vector,java.lang.Object[]}, size=1288, count=4}
{org.springframework.aop.aspectj.annotation.AnnotationAwareAspectJAutoProxyCreator,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=1285, count=1}
{org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,java.util.concurrent.ThreadPoolExecutor,java.util.concurrent.ArrayBlockingQueue,java.lang.Object[]}, size=1282, count=2}
{org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtLockFuture$LockTimeoutObject,org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtLockFuture,java.util.ArrayList,java.lang.Object[]}, size=1280, count=4}
{org.jsr166.ConcurrentHashMap8$Node,org.apache.ignite.internal.IgnitionEx$IgniteNamedInstance,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=1277, count=1}
{java.util.concurrent.ConcurrentHashMap$Node,org.apache.logging.log4j.core.appender.AsyncAppender,java.util.concurrent.ArrayBlockingQueue,java.lang.Object[]}, size=1271, count=1}
{net.sf.saxon.style.XSLAttribute,net.sf.saxon.style.XSLIf,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=1270, count=5}
{net.sf.saxon.style.XSLAttribute,net.sf.saxon.style.XSLIf,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=1270, count=5}
{net.sf.saxon.style.LiteralResultElement,net.sf.saxon.style.XSLIf,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=1270, count=5}
{net.sf.saxon.style.XSLAttribute,net.sf.saxon.style.XSLIf,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=1270, count=5}
{net.sf.saxon.style.LiteralResultElement,net.sf.saxon.style.XSLIf,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=1270, count=5}
{net.sf.saxon.style.LiteralResultElement,net.sf.saxon.style.XSLIf,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=1270, count=5}
{SomeType76,ch.qos.logback.core.BasicStatusManager,ch.qos.logback.core.helpers.CyclicBuffer,java.lang.Object[]}, size=1264, count=1}
{SomeType76,ch.qos.logback.core.BasicStatusManager,ch.qos.logback.core.helpers.CyclicBuffer,java.lang.Object[]}, size=1264, count=1}
{SomeType76,ch.qos.logback.core.BasicStatusManager,ch.qos.logback.core.helpers.CyclicBuffer,java.lang.Object[]}, size=1264, count=1}
{org.apache.logging.log4j.core.appender.DefaultErrorHandler,org.apache.logging.log4j.core.appender.AsyncAppender,java.util.concurrent.ArrayBlockingQueue,java.lang.Object[]}, size=1263, count=1}
{net.sf.saxon.serialize.XMLIndenter,net.sf.saxon.serialize.XMLEmitter,java.util.Stack,java.lang.Object[]}, size=1262, count=2}
{net.sf.saxon.serialize.XMLIndenter,net.sf.saxon.serialize.XMLEmitter,java.util.Stack,java.lang.Object[]}, size=1262, count=2}
{com.sun.org.apache.xerces.internal.impl.XMLEntityManager,com.sun.org.apache.xerces.internal.impl.XMLEntityScanner,java.util.ArrayList,java.lang.Object[]}, size=1256, count=4}
{org.apache.kafka.clients.consumer.internals.AbstractCoordinator$GroupCoordinatorMetrics,org.apache.kafka.clients.consumer.internals.ConsumerCoordinator,java.util.ArrayList,java.lang.Object[]}, size=1251, count=3}
{ch.qos.logback.classic.PatternLayout,ch.qos.logback.classic.pattern.DateConverter,java.util.ArrayList,java.lang.Object[]}, size=1244, count=4}
{ch.qos.logback.classic.PatternLayout,ch.qos.logback.classic.pattern.DateConverter,java.util.ArrayList,java.lang.Object[]}, size=1244, count=4}
{ch.qos.logback.classic.PatternLayout,ch.qos.logback.classic.pattern.DateConverter,java.util.ArrayList,java.lang.Object[]}, size=1244, count=4}
{org.eclipse.wst.xml.xpath2.processor.internal.ast.XPathExpr,org.eclipse.wst.xml.xpath2.processor.internal.ast.AxisStep,java.util.ArrayList,java.lang.Object[]}, size=1240, count=10}
{org.eclipse.wst.xml.xpath2.processor.internal.ast.XPathExpr,org.eclipse.wst.xml.xpath2.processor.internal.ast.AxisStep,java.util.ArrayList,java.lang.Object[]}, size=1240, count=10}
{org.apache.kafka.clients.NetworkClient$DefaultMetadataUpdater,org.apache.kafka.clients.Metadata,java.util.ArrayList,java.lang.Object[]}, size=1240, count=5}
{com.sun.org.apache.xerces.internal.impl.xs.XSGroupDecl,com.sun.org.apache.xerces.internal.impl.xs.SchemaGrammar,java.util.Vector,java.lang.Object[]}, size=1236, count=2}
{org.apache.kafka.clients.consumer.internals.ConsumerNetworkClient,org.apache.kafka.clients.Metadata,java.util.ArrayList,java.lang.Object[]}, size=1228, count=4}
{com.sun.org.apache.xerces.internal.impl.XMLEntityManager,com.sun.xml.internal.stream.XMLEntityStorage,com.sun.org.apache.xerces.internal.impl.XMLEntityManager,java.lang.Object[]}, size=1228, count=4}
{java.util.concurrent.ConcurrentHashMap$Node,javax.crypto.CryptoPermissionCollection,java.util.Vector,java.lang.Object[]}, size=1224, count=8}
{org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=1217, count=1}
{org.apache.xerces.impl.xs.XMLSchemaLoaderExt,org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,java.lang.Object[]}, size=1216, count=1}
{org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=1214, count=1}
{org.springframework.aop.aspectj.annotation.AnnotationAwareAspectJAutoProxyCreator$BeanFactoryAspectJAdvisorsBuilderAdapter,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=1209, count=1}
{org.apache.ignite.internal.util.nio.GridNioServer$GridNioAcceptWorker,org.apache.ignite.internal.util.nio.GridNioServer,java.util.ArrayList,java.lang.Object[]}, size=1207, count=3}
{org.apache.kafka.clients.consumer.internals.ConsumerCoordinator$ConsumerCoordinatorMetrics,org.apache.kafka.clients.consumer.internals.ConsumerCoordinator,java.util.ArrayList,java.lang.Object[]}, size=1203, count=3}
{org.springframework.transaction.aspectj.AnnotationTransactionAspect,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=1201, count=1}
{org.springframework.transaction.interceptor.TransactionInterceptor,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=1201, count=1}
{net.sf.saxon.style.XSLIf,net.sf.saxon.style.XSLElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=1196, count=4}
{net.sf.saxon.style.XSLIf,net.sf.saxon.style.XSLElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=1196, count=4}
{net.sf.saxon.style.XSLIf,net.sf.saxon.style.XSLElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=1196, count=4}
{java.util.LinkedHashMap$Entry,com.sun.xml.internal.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.internal.FinalArrayList,java.lang.Object[]}, size=1192, count=3}
{org.springframework.beans.factory.annotation.RequiredAnnotationBeanPostProcessor,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=1189, count=1}
{org.springframework.aop.framework.autoproxy.AbstractAdvisorAutoProxyCreator$BeanFactoryAdvisorRetrievalHelperAdapter,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=1185, count=1}
{com.google.common.util.concurrent.AbstractExecutionThreadService$1,java.util.Collections$SynchronizedRandomAccessList,java.util.ArrayList,java.lang.Object[]}, size=1176, count=3}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDocumentInfo,java.util.Vector,java.lang.Object[]}, size=1176, count=4}
{org.springframework.context.annotation.ConfigurationClassEnhancer$BeanMethodInterceptor,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=1169, count=1}
{org.springframework.context.annotation.ConfigurationClassPostProcessor$ImportAwareBeanPostProcessor,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=1169, count=1}
{org.apache.ignite.spi.discovery.tcp.ServerImpl$RingMessageWorker,org.apache.ignite.spi.discovery.tcp.ServerImpl,java.util.ArrayDeque,java.lang.Object[]}, size=1163, count=1}
{org.apache.xerces.impl.xs.traversers.XSDTypeAlternativeTraverser,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1160, count=4}
{org.apache.kafka.common.metrics.Metrics$2,org.apache.kafka.common.metrics.Metrics,java.util.ArrayList,java.lang.Object[]}, size=1160, count=5}
{net.sf.saxon.style.XSLElement,net.sf.saxon.style.XSLAttribute,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=1160, count=4}
{net.sf.saxon.style.XSLElement,net.sf.saxon.style.XSLAttribute,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=1160, count=4}
{org.apache.xerces.impl.xs.traversers.XSDElementTraverser,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1160, count=4}
{net.sf.saxon.style.XSLElement,net.sf.saxon.style.XSLAttribute,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=1160, count=4}
{org.apache.xerces.impl.xs.traversers.XSDGroupTraverser,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1156, count=4}
{org.apache.kafka.clients.consumer.internals.AbstractCoordinator$GroupCoordinatorMetrics$1,org.apache.kafka.clients.consumer.internals.ConsumerCoordinator,java.util.ArrayList,java.lang.Object[]}, size=1155, count=3}
{org.apache.kafka.clients.consumer.internals.ConsumerCoordinator$ConsumerCoordinatorMetrics$1,org.apache.kafka.clients.consumer.internals.ConsumerCoordinator,java.util.ArrayList,java.lang.Object[]}, size=1155, count=3}
{org.springframework.context.annotation.ConfigurationClassBeanDefinitionReader,org.springframework.beans.factory.support.DefaultListableBeanFactory,java.util.ArrayList,java.lang.Object[]}, size=1145, count=1}
{java.util.HashMap$Node,com.sun.xml.internal.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.internal.FinalArrayList,java.lang.Object[]}, size=1144, count=3}
{javax.management.remote.rmi.NoCallStackClassLoader,javax.management.remote.rmi.NoCallStackClassLoader,java.util.Vector,java.lang.Object[]}, size=1140, count=2}
{org.apache.curator.framework.imps.NamespaceImpl,org.apache.curator.framework.imps.CuratorFrameworkImpl,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=1140, count=4}
{com.sun.org.apache.xerces.internal.parsers.XIncludeAwareParserConfiguration,com.sun.org.apache.xerces.internal.parsers.DOMParser,java.util.Stack,java.lang.Object[]}, size=1137, count=1}
{org.apache.xerces.parsers.XIncludeAwareParserConfiguration,org.apache.xerces.impl.validation.ValidationManager,java.util.ArrayList,java.lang.Object[]}, size=1134, count=7}
{org.apache.xerces.parsers.XIncludeAwareParserConfiguration,org.apache.xerces.impl.validation.ValidationManager,java.util.ArrayList,java.lang.Object[]}, size=1134, count=7}
{org.apache.xerces.parsers.XIncludeAwareParserConfiguration,org.apache.xerces.impl.validation.ValidationManager,java.util.ArrayList,java.lang.Object[]}, size=1134, count=7}
{org.apache.kafka.clients.consumer.internals.ConsumerCoordinator$1,org.apache.kafka.clients.consumer.internals.ConsumerCoordinator,java.util.ArrayList,java.lang.Object[]}, size=1131, count=3}
{org.apache.xerces.impl.xs.traversers.XSDSimpleTypeTraverser,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1128, count=4}
{net.sf.saxon.style.XSLPackage,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=1125, count=5}
{net.sf.saxon.style.XSLPackage,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=1125, count=5}
{SomeType32,SomeType20,java.util.ArrayList,java.lang.Object[]}, size=1125, count=1}
{net.sf.saxon.style.XSLPackage,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=1125, count=5}
{org.apache.xerces.impl.xs.traversers.XSDAttributeTraverser,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1124, count=4}
{org.apache.xerces.impl.xs.traversers.XSDNotationTraverser,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1124, count=4}
{org.apache.kafka.common.Cluster,java.util.Collections$UnmodifiableRandomAccessList,java.util.ArrayList,java.lang.Object[]}, size=1124, count=4}
{org.apache.xerces.impl.xs.traversers.XSDWildcardTraverser,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1124, count=4}
{org.apache.xerces.impl.xs.traversers.XSDAttributeGroupTraverser,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1124, count=4}
{org.apache.xerces.impl.xs.traversers.XSDUniqueOrKeyTraverser,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1124, count=4}
{org.apache.xerces.impl.xs.traversers.XSDKeyrefTraverser,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1124, count=4}
{org.eclipse.jetty.io.SelectorManager$ManagedSelector,org.eclipse.jetty.server.ServerConnector$ServerConnectorManager,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=1120, count=4}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDElementTraverser,java.util.Vector,java.lang.Object[]}, size=1110, count=5}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDElementTraverser,java.util.Vector,java.lang.Object[]}, size=1110, count=5}
{org.apache.curator.framework.imps.CuratorFrameworkImpl,org.apache.curator.framework.state.ConnectionStateManager,java.util.concurrent.ArrayBlockingQueue,java.lang.Object[]}, size=1106, count=2}
{org.apache.ignite.internal.util.nio.GridNioServer$HeadFilter,org.apache.ignite.internal.util.nio.GridNioServer,java.util.ArrayList,java.lang.Object[]}, size=1105, count=3}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDGroupTraverser,java.util.Vector,java.lang.Object[]}, size=1105, count=5}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDGroupTraverser,java.util.Vector,java.lang.Object[]}, size=1105, count=5}
{net.sf.saxon.Controller,net.sf.saxon.serialize.MessageEmitter,java.util.Stack,java.lang.Object[]}, size=1092, count=2}
{org.apache.logging.log4j.core.config.xml.XmlConfiguration,org.apache.logging.log4j.core.config.LoggerConfig,java.util.ArrayList,java.lang.Object[]}, size=1092, count=3}
{net.sf.saxon.Controller,net.sf.saxon.serialize.MessageEmitter,java.util.Stack,java.lang.Object[]}, size=1092, count=2}
{net.sf.saxon.Controller,net.sf.saxon.serialize.MessageEmitter,java.util.Stack,java.lang.Object[]}, size=1092, count=2}
{org.apache.kafka.clients.consumer.internals.AbstractCoordinator$HeartbeatThread,org.apache.kafka.clients.consumer.internals.ConsumerCoordinator,java.util.ArrayList,java.lang.Object[]}, size=1084, count=2}
{org.apache.ignite.internal.util.nio.GridNioServer$SizeBasedBalancer,org.apache.ignite.internal.util.nio.GridNioServer,java.util.ArrayList,java.lang.Object[]}, size=1081, count=3}
{com.mchange.v2.cfg.BasicMultiPropertiesConfig,java.util.Collections$UnmodifiableRandomAccessList,java.util.ArrayList,java.lang.Object[]}, size=1080, count=3}
{org.apache.kafka.clients.consumer.internals.ConsumerCoordinator$ConsumerCoordinatorMetrics,org.apache.kafka.common.metrics.Sensor,java.util.ArrayList,java.lang.Object[]}, size=1080, count=3}
{org.apache.kafka.clients.consumer.KafkaConsumer,org.apache.kafka.clients.Metadata,java.util.ArrayList,java.lang.Object[]}, size=1080, count=3}
{com.mchange.v2.cfg.BasicMultiPropertiesConfig,java.util.Collections$UnmodifiableRandomAccessList,java.util.ArrayList,java.lang.Object[]}, size=1080, count=3}
{com.mchange.v2.cfg.BasicMultiPropertiesConfig,java.util.Collections$UnmodifiableRandomAccessList,java.util.ArrayList,java.lang.Object[]}, size=1080, count=3}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDSimpleTypeTraverser,java.util.Vector,java.lang.Object[]}, size=1070, count=5}
{org.jsr166.ConcurrentHashMap8$Node,org.apache.ignite.internal.managers.deployment.GridDeploymentClassLoader,java.util.Vector,java.lang.Object[]}, size=1070, count=2}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDSimpleTypeTraverser,java.util.Vector,java.lang.Object[]}, size=1070, count=5}
{org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi$TcpDiscoverySpiMBeanImpl,org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=1070, count=1}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDNotationTraverser,java.util.Vector,java.lang.Object[]}, size=1065, count=5}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDKeyrefTraverser,java.util.Vector,java.lang.Object[]}, size=1065, count=5}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDAttributeGroupTraverser,java.util.Vector,java.lang.Object[]}, size=1065, count=5}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDUniqueOrKeyTraverser,java.util.Vector,java.lang.Object[]}, size=1065, count=5}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDWildcardTraverser,java.util.Vector,java.lang.Object[]}, size=1065, count=5}
{SomeType67,SomeType74,java.util.ArrayList,java.lang.Object[]}, size=1065, count=3}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDAttributeTraverser,java.util.Vector,java.lang.Object[]}, size=1065, count=5}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDUniqueOrKeyTraverser,java.util.Vector,java.lang.Object[]}, size=1065, count=5}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDAttributeGroupTraverser,java.util.Vector,java.lang.Object[]}, size=1065, count=5}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDWildcardTraverser,java.util.Vector,java.lang.Object[]}, size=1065, count=5}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDAttributeTraverser,java.util.Vector,java.lang.Object[]}, size=1065, count=5}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDNotationTraverser,java.util.Vector,java.lang.Object[]}, size=1065, count=5}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDKeyrefTraverser,java.util.Vector,java.lang.Object[]}, size=1065, count=5}
{java.util.HashMap$Node,org.apache.xerces.impl.xs.traversers.OverrideTransformationManager$DocumentContext,java.util.ArrayList,java.lang.Object[]}, size=1060, count=5}
{java.util.HashMap$Node,org.apache.xerces.impl.xs.traversers.OverrideTransformationManager$DocumentContext,java.util.ArrayList,java.lang.Object[]}, size=1060, count=5}
{org.apache.kafka.clients.consumer.KafkaConsumer,org.apache.kafka.clients.consumer.internals.ConsumerCoordinator,java.util.ArrayList,java.lang.Object[]}, size=1059, count=3}
{com.sun.xml.bind.v2.runtime.MarshallerImpl,com.sun.xml.bind.v2.runtime.XMLSerializer,com.sun.xml.bind.v2.util.CollisionCheckStack,java.lang.Object[]}, size=1058, count=2}
{com.sun.xml.bind.v2.runtime.MarshallerImpl,com.sun.xml.bind.v2.runtime.XMLSerializer,com.sun.xml.bind.v2.util.CollisionCheckStack,java.lang.Object[]}, size=1058, count=2}
{com.sun.xml.bind.v2.runtime.MarshallerImpl,com.sun.xml.bind.v2.runtime.XMLSerializer,com.sun.xml.bind.v2.util.CollisionCheckStack,java.lang.Object[]}, size=1058, count=2}
{org.jsr166.ConcurrentHashMap8$Node,org.h2.engine.Session,java.util.ArrayList,java.lang.Object[]}, size=1056, count=2}
{SomeType76,ch.qos.logback.core.BasicStatusManager,java.util.ArrayList,java.lang.Object[]}, size=1048, count=1}
{SomeType76,ch.qos.logback.core.BasicStatusManager,java.util.ArrayList,java.lang.Object[]}, size=1048, count=1}
{SomeType76,ch.qos.logback.core.BasicStatusManager,java.util.ArrayList,java.lang.Object[]}, size=1048, count=1}
{javax.management.StandardEmitterMBean,javax.management.MBeanInfo,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=1045, count=5}
{javax.management.openmbean.TabularType,java.util.Collections$UnmodifiableRandomAccessList,java.util.ArrayList,java.lang.Object[]}, size=1044, count=4}
{org.apache.kafka.common.network.Selector$SelectorMetrics,org.apache.kafka.common.metrics.Metrics,java.util.ArrayList,java.lang.Object[]}, size=1040, count=5}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1040, count=5}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=1040, count=5}
{oracle.jrockit.jfr.openmbean.Member,javax.management.openmbean.SimpleType,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=1038, count=6}
{java.util.HashMap$Node,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=1035, count=5}
{java.util.HashMap$Node,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=1035, count=5}
{java.util.HashMap$Node,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=1035, count=5}
{org.apache.logging.log4j.core.config.xml.XmlConfiguration,org.apache.logging.log4j.core.config.Node,java.util.ArrayList,java.lang.Object[]}, size=1034, count=4}
{javax.management.remote.rmi.RMIConnectionImpl,com.sun.jmx.remote.util.ClassLoaderWithRepository,java.util.Vector,java.lang.Object[]}, size=1030, count=2}
{org.apache.kafka.clients.consumer.internals.Fetcher,org.apache.kafka.clients.Metadata,java.util.ArrayList,java.lang.Object[]}, size=1020, count=3}
{org.h2.mvstore.Page,org.h2.mvstore.MVMap,org.h2.mvstore.ConcurrentArrayList,java.lang.Object[]}, size=1017, count=7}
{ch.qos.logback.core.recovery.ResilientFileOutputStream,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=1014, count=3}
{ch.qos.logback.core.joran.action.AppenderRefAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=1014, count=3}
{ch.qos.logback.core.joran.action.PropertyAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=1014, count=3}
{ch.qos.logback.core.joran.action.AppenderRefAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=1014, count=3}
{ch.qos.logback.core.recovery.ResilientFileOutputStream,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=1014, count=3}
{ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=1014, count=3}
{ch.qos.logback.core.rolling.FixedWindowRollingPolicy,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=1014, count=3}
{ch.qos.logback.core.rolling.helper.RenameUtil,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=1014, count=3}
{ch.qos.logback.core.joran.action.NOPAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=1014, count=3}
{ch.qos.logback.core.rolling.helper.Compressor,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=1014, count=3}
{ch.qos.logback.core.rolling.RollingFileAppender,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=1014, count=3}
{ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=1014, count=3}
{ch.qos.logback.core.joran.action.PropertyAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=1014, count=3}
{ch.qos.logback.core.rolling.RollingFileAppender,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=1014, count=3}
{ch.qos.logback.core.rolling.helper.Compressor,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=1014, count=3}
{ch.qos.logback.core.rolling.helper.RenameUtil,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=1014, count=3}
{ch.qos.logback.core.joran.action.NOPAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=1014, count=3}
{ch.qos.logback.core.rolling.FixedWindowRollingPolicy,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=1014, count=3}
{ch.qos.logback.core.status.InfoStatus,ch.qos.logback.core.joran.action.NestedComplexPropertyIA,java.util.Stack,java.lang.Object[]}, size=1008, count=4}
{ch.qos.logback.core.status.InfoStatus,ch.qos.logback.core.joran.action.NestedComplexPropertyIA,java.util.Stack,java.lang.Object[]}, size=1008, count=4}
{ch.qos.logback.core.status.InfoStatus,ch.qos.logback.core.joran.action.NestedComplexPropertyIA,java.util.Stack,java.lang.Object[]}, size=1008, count=4}
{java.util.LinkedList$Node,org.apache.ignite.internal.processors.cache.GridCacheIoManager,java.util.ArrayList,java.lang.Object[]}, size=1006, count=1}
{org.apache.ignite.internal.processors.cache.GridCacheIoManager$OrderedMessageListener,org.apache.ignite.internal.processors.cache.GridCacheIoManager,java.util.ArrayList,java.lang.Object[]}, size=998, count=1}
{org.apache.ignite.internal.processors.cache.persistence.PersistenceMetricsImpl,org.apache.ignite.internal.processors.cache.ratemetrics.HitRateMetrics,java.util.concurrent.atomic.AtomicLongArray,long[]}, size=997, count=1}
{SomeType126,SomeType66,java.util.ArrayList,java.lang.Object[]}, size=996, count=4}
{javax.management.remote.rmi.RMIConnectionImpl$CombinedClassLoader,javax.management.remote.rmi.RMIConnectionImpl$CombinedClassLoader$ClassLoaderWrapper,java.util.Vector,java.lang.Object[]}, size=996, count=2}
{org.eclipse.wst.xml.xpath2.processor.internal.ast.XPathExpr,org.eclipse.wst.xml.xpath2.processor.internal.ast.AxisStep,java.util.ArrayList,java.lang.Object[]}, size=992, count=8}
{org.apache.ignite.internal.processors.cache.GridCacheIoManager$1,org.apache.ignite.internal.processors.cache.GridCacheIoManager,java.util.ArrayList,java.lang.Object[]}, size=990, count=1}
{java.util.concurrent.ConcurrentHashMap$Node,org.springframework.expression.spel.support.StandardEvaluationContext,java.util.ArrayList,java.lang.Object[]}, size=988, count=3}
{com.sun.org.apache.xerces.internal.impl.xs.XMLSchemaLoader,com.sun.org.apache.xerces.internal.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=981, count=1}
{org.apache.ignite.configuration.IgniteConfiguration,SomeType78,java.util.Vector,java.lang.Object[]}, size=980, count=1}
{javax.management.remote.rmi.RMIConnectionImpl$CombinedClassLoader$ClassLoaderWrapper,javax.management.remote.rmi.RMIConnectionImpl$CombinedClassLoader$ClassLoaderWrapper,java.util.Vector,java.lang.Object[]}, size=980, count=2}
{ch.qos.logback.core.spi.ContextAwareBase,ch.qos.logback.classic.pattern.DateConverter,java.util.ArrayList,java.lang.Object[]}, size=980, count=4}
{ch.qos.logback.core.spi.ContextAwareBase,ch.qos.logback.classic.pattern.DateConverter,java.util.ArrayList,java.lang.Object[]}, size=980, count=4}
{ch.qos.logback.core.spi.ContextAwareBase,ch.qos.logback.classic.pattern.DateConverter,java.util.ArrayList,java.lang.Object[]}, size=980, count=4}
{org.apache.kafka.clients.NetworkClient$DefaultMetadataUpdater,org.apache.kafka.clients.Metadata,java.util.ArrayList,java.lang.Object[]}, size=976, count=4}
{java.util.HashMap$Node,com.sun.org.apache.xerces.internal.impl.XMLDTDScannerImpl,java.util.ArrayList,java.lang.Object[]}, size=970, count=2}
{org.h2.engine.Database,org.h2.engine.Session,java.util.ArrayList,java.lang.Object[]}, size=968, count=1}
{org.apache.ignite.internal.processors.cache.persistence.GridCacheDatabaseSharedManager,org.apache.ignite.internal.processors.cache.persistence.MemoryPolicy,org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,long[]}, size=968, count=1}
{org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionsExchangeFuture,org.apache.ignite.internal.processors.cache.CacheObjectsReleaseFuture,java.util.ArrayList,java.lang.Object[]}, size=968, count=2}
{org.apache.ignite.internal.processors.cache.GridCacheSharedContext,org.apache.ignite.internal.processors.cache.GridCacheIoManager,java.util.ArrayList,java.lang.Object[]}, size=966, count=1}
{java.security.ProtectionDomain,javax.management.remote.rmi.NoCallStackClassLoader,java.util.Vector,java.lang.Object[]}, size=966, count=2}
{org.eclipse.wst.xml.xpath2.processor.internal.ast.FilterExpr,org.eclipse.wst.xml.xpath2.processor.internal.ast.ParExpr,java.util.ArrayList,java.lang.Object[]}, size=960, count=5}
{org.eclipse.wst.xml.xpath2.processor.internal.ast.FilterExpr,org.eclipse.wst.xml.xpath2.processor.internal.ast.ParExpr,java.util.ArrayList,java.lang.Object[]}, size=960, count=5}
{SomeType127,org.apache.curator.framework.imps.CuratorFrameworkImpl,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=954, count=3}
{org.apache.ignite.internal.processors.cache.persistence.wal.FileWriteAheadLogManager,org.apache.ignite.internal.managers.eventstorage.GridEventStorageManager,java.util.IdentityHashMap,java.lang.Object[]}, size=949, count=1}
{com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory$CompositeMapping,javax.management.openmbean.CompositeType,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=948, count=4}
{net.sf.saxon.expr.StringLiteral,net.sf.saxon.style.XSLValueOf,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=945, count=3}
{net.sf.saxon.expr.StringLiteral,net.sf.saxon.style.XSLValueOf,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=945, count=3}
{net.sf.saxon.expr.StringLiteral,net.sf.saxon.style.XSLValueOf,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=945, count=3}
{com.sun.xml.bind.v2.runtime.output.NamespaceContextImpl,com.sun.xml.bind.v2.runtime.XMLSerializer,com.sun.xml.bind.v2.util.CollisionCheckStack,java.lang.Object[]}, size=934, count=2}
{com.sun.xml.bind.v2.runtime.output.NamespaceContextImpl,com.sun.xml.bind.v2.runtime.XMLSerializer,com.sun.xml.bind.v2.util.CollisionCheckStack,java.lang.Object[]}, size=934, count=2}
{com.sun.xml.bind.v2.runtime.output.NamespaceContextImpl,com.sun.xml.bind.v2.runtime.XMLSerializer,com.sun.xml.bind.v2.util.CollisionCheckStack,java.lang.Object[]}, size=934, count=2}
{org.apache.logging.log4j.core.pattern.LiteralPatternConverter,org.apache.logging.log4j.core.config.DefaultConfiguration,java.util.ArrayList,java.lang.Object[]}, size=932, count=4}
{org.apache.logging.log4j.core.config.DefaultReliabilityStrategy,org.apache.logging.log4j.core.config.LoggerConfig,java.util.ArrayList,java.lang.Object[]}, size=930, count=5}
{org.apache.kafka.common.metrics.Metrics$2,org.apache.kafka.common.metrics.Metrics,java.util.ArrayList,java.lang.Object[]}, size=928, count=4}
{org.postgresql.jdbc2.TimestampUtils,java.util.GregorianCalendar,sun.util.calendar.ZoneInfo,long[]}, size=925, count=1}
{org.apache.kafka.clients.consumer.internals.ConsumerNetworkClient,org.apache.kafka.clients.Metadata,java.util.ArrayList,java.lang.Object[]}, size=921, count=3}
{java.security.ProtectionDomain,com.sun.jmx.remote.util.ClassLoaderWithRepository,java.util.Vector,java.lang.Object[]}, size=918, count=2}
{ch.qos.logback.core.spi.ContextAwareBase,ch.qos.logback.classic.pattern.LoggerConverter,java.util.ArrayList,java.lang.Object[]}, size=916, count=4}
{SomeType108,org.dom4j.tree.DefaultDocument,java.util.ArrayList,java.lang.Object[]}, size=916, count=2}
{ch.qos.logback.core.spi.ContextAwareBase,ch.qos.logback.classic.pattern.LoggerConverter,java.util.ArrayList,java.lang.Object[]}, size=916, count=4}
{ch.qos.logback.core.spi.ContextAwareBase,ch.qos.logback.classic.pattern.LoggerConverter,java.util.ArrayList,java.lang.Object[]}, size=916, count=4}
{java.util.Hashtable$Entry,java.util.Hashtable$Entry,java.util.Vector,java.lang.Object[]}, size=912, count=4}
{ch.qos.logback.classic.joran.action.LevelAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=910, count=2}
{org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode,java.util.ArrayList,java.lang.Object[]}, size=908, count=1}
{ch.qos.logback.classic.jmx.JMXConfigurator,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=906, count=1}
{java.security.ProtectionDomain,sun.reflect.misc.MethodUtil,java.util.Vector,java.lang.Object[]}, size=904, count=2}
{java.security.ProtectionDomain,javax.management.remote.rmi.RMIConnectionImpl$CombinedClassLoader,java.util.Vector,java.lang.Object[]}, size=902, count=2}
{ch.qos.logback.core.pattern.LiteralConverter,ch.qos.logback.classic.pattern.LoggerConverter,java.util.ArrayList,java.lang.Object[]}, size=900, count=4}
{net.sf.saxon.style.XSLIf,net.sf.saxon.style.LiteralResultElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=900, count=5}
{ch.qos.logback.core.pattern.LiteralConverter,ch.qos.logback.classic.pattern.LoggerConverter,java.util.ArrayList,java.lang.Object[]}, size=900, count=4}
{ch.qos.logback.core.pattern.LiteralConverter,ch.qos.logback.classic.pattern.LoggerConverter,java.util.ArrayList,java.lang.Object[]}, size=900, count=4}
{org.apache.curator.framework.imps.NamespaceFacade,org.apache.curator.framework.imps.CuratorFrameworkImpl,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=900, count=2}
{net.sf.saxon.style.XSLIf,net.sf.saxon.style.LiteralResultElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=900, count=5}
{net.sf.saxon.style.XSLIf,net.sf.saxon.style.LiteralResultElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=900, count=5}
{scala.util.parsing.json.JSON$,scala.util.parsing.json.Lexer,scala.collection.mutable.HashSet,java.lang.Object[]}, size=899, count=1}
{scala.util.parsing.json.JSON$,scala.util.parsing.json.Lexer,scala.collection.mutable.HashSet,java.lang.Object[]}, size=899, count=1}
{scala.util.parsing.json.JSON$,scala.util.parsing.json.Lexer,scala.collection.mutable.HashSet,java.lang.Object[]}, size=899, count=1}
{com.sun.xml.bind.v2.model.impl.RuntimeModelBuilder,com.sun.xml.bind.v2.runtime.IllegalAnnotationsException$Builder,java.util.ArrayList,java.lang.Object[]}, size=895, count=5}
{com.sun.xml.bind.v2.model.impl.RuntimeModelBuilder,com.sun.xml.bind.v2.runtime.IllegalAnnotationsException$Builder,java.util.ArrayList,java.lang.Object[]}, size=895, count=5}
{com.sun.xml.bind.v2.model.impl.RuntimeModelBuilder,com.sun.xml.bind.v2.runtime.IllegalAnnotationsException$Builder,java.util.ArrayList,java.lang.Object[]}, size=895, count=5}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDElementTraverser,java.util.Vector,java.lang.Object[]}, size=888, count=4}
{java.security.ProtectionDomain,javax.management.remote.rmi.RMIConnectionImpl$CombinedClassLoader$ClassLoaderWrapper,java.util.Vector,java.lang.Object[]}, size=886, count=2}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDGroupTraverser,java.util.Vector,java.lang.Object[]}, size=884, count=4}
{org.apache.kafka.clients.consumer.internals.ConsumerCoordinator,org.apache.kafka.clients.Metadata,java.util.ArrayList,java.lang.Object[]}, size=880, count=2}
{org.eclipse.jetty.util.component.ContainerLifeCycle$Bean,org.eclipse.jetty.util.thread.QueuedThreadPool,org.eclipse.jetty.util.BlockingArrayQueue,java.lang.Object[]}, size=876, count=2}
{net.sf.saxon.s9api.XsltTransformer,net.sf.saxon.Controller,net.sf.saxon.expr.instruct.Bindery,long[]}, size=870, count=2}
{net.sf.saxon.s9api.XsltTransformer,net.sf.saxon.Controller,net.sf.saxon.expr.instruct.Bindery,long[]}, size=870, count=2}
{net.sf.saxon.s9api.XsltTransformer,net.sf.saxon.Controller,net.sf.saxon.expr.instruct.Bindery,long[]}, size=870, count=2}
{sun.rmi.transport.tcp.TCPConnection,sun.rmi.transport.tcp.TCPChannel,java.util.ArrayList,java.lang.Object[]}, size=868, count=4}
{org.h2.engine.Database,org.h2.message.TraceSystem,java.util.concurrent.atomic.AtomicReferenceArray,java.lang.Object[]}, size=867, count=1}
{java.util.concurrent.ConcurrentHashMap$Node,org.h2.mvstore.MVMap,org.h2.mvstore.Page,java.lang.Object[]}, size=865, count=5}
{org.apache.ignite.internal.processors.cache.persistence.MemoryMetricsImpl,org.apache.ignite.internal.processors.cache.ratemetrics.HitRateMetrics,java.util.concurrent.atomic.AtomicLongArray,long[]}, size=864, count=2}
{org.springframework.context.support.AbstractApplicationContext$BeanPostProcessorChecker,org.springframework.context.support.ClassPathXmlApplicationContext,java.util.ArrayList,java.lang.Object[]}, size=864, count=3}
{SomeType128,SomeType71,java.util.ArrayList,java.lang.Object[]}, size=864, count=4}
{org.dom4j.tree.DefaultElement,org.dom4j.tree.DefaultDocument,java.util.ArrayList,java.lang.Object[]}, size=864, count=4}
{org.springframework.context.support.AbstractApplicationContext$BeanPostProcessorChecker,org.springframework.context.support.ClassPathXmlApplicationContext,java.util.ArrayList,java.lang.Object[]}, size=864, count=3}
{org.apache.zookeeper.ClientCnxn,org.apache.zookeeper.client.StaticHostProvider,java.util.ArrayList,java.lang.Object[]}, size=861, count=3}
{org.apache.xerces.dom.PSVIDocumentImpl,org.apache.xerces.dom.PSVIElementNSImpl,org.apache.xerces.impl.xs.util.ObjectListImpl,java.lang.Object[]}, size=860, count=1}
{org.apache.xerces.dom.PSVIDocumentImpl,org.apache.xerces.dom.PSVIElementNSImpl,org.apache.xerces.impl.xs.util.ObjectListImpl,java.lang.Object[]}, size=860, count=1}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDSimpleTypeTraverser,java.util.Vector,java.lang.Object[]}, size=856, count=4}
{java.util.HashMap$Node,net.sf.saxon.trans.KeyDefinitionSet,java.util.ArrayList,java.lang.Object[]}, size=855, count=5}
{org.apache.curator.framework.imps.NamespaceWatcher,org.apache.curator.framework.imps.CuratorFrameworkImpl,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=855, count=3}
{java.util.HashMap$Node,net.sf.saxon.trans.KeyDefinitionSet,java.util.ArrayList,java.lang.Object[]}, size=855, count=5}
{java.util.HashMap$Node,net.sf.saxon.trans.KeyDefinitionSet,java.util.ArrayList,java.lang.Object[]}, size=855, count=5}
{org.springframework.beans.support.ResourceEditorRegistrar,org.springframework.context.support.ClassPathXmlApplicationContext,java.util.ArrayList,java.lang.Object[]}, size=852, count=3}
{org.springframework.beans.support.ResourceEditorRegistrar,org.springframework.context.support.ClassPathXmlApplicationContext,java.util.ArrayList,java.lang.Object[]}, size=852, count=3}
{org.springframework.core.io.support.PathMatchingResourcePatternResolver,org.springframework.context.support.ClassPathXmlApplicationContext,java.util.ArrayList,java.lang.Object[]}, size=852, count=3}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDKeyrefTraverser,java.util.Vector,java.lang.Object[]}, size=852, count=4}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDNotationTraverser,java.util.Vector,java.lang.Object[]}, size=852, count=4}
{org.springframework.core.io.support.PathMatchingResourcePatternResolver,org.springframework.context.support.ClassPathXmlApplicationContext,java.util.ArrayList,java.lang.Object[]}, size=852, count=3}
{org.springframework.context.support.AbstractApplicationContext$ApplicationListenerDetector,org.springframework.context.support.ClassPathXmlApplicationContext,java.util.ArrayList,java.lang.Object[]}, size=852, count=3}
{sun.misc.Launcher$AppClassLoader,sun.misc.URLClassPath,java.util.ArrayList,java.lang.Object[]}, size=852, count=1}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDUniqueOrKeyTraverser,java.util.Vector,java.lang.Object[]}, size=852, count=4}
{org.springframework.context.support.AbstractApplicationContext$ApplicationListenerDetector,org.springframework.context.support.ClassPathXmlApplicationContext,java.util.ArrayList,java.lang.Object[]}, size=852, count=3}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDWildcardTraverser,java.util.Vector,java.lang.Object[]}, size=852, count=4}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDAttributeTraverser,java.util.Vector,java.lang.Object[]}, size=852, count=4}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDAttributeGroupTraverser,java.util.Vector,java.lang.Object[]}, size=852, count=4}
{com.google.common.util.concurrent.AbstractScheduledService$1,java.util.Collections$SynchronizedRandomAccessList,java.util.ArrayList,java.lang.Object[]}, size=848, count=2}
{java.util.HashMap$Node,org.apache.xerces.impl.xs.traversers.OverrideTransformationManager$DocumentContext,java.util.ArrayList,java.lang.Object[]}, size=848, count=4}
{org.apache.log4j.Logger,org.apache.log4j.Hierarchy,java.util.Vector,java.lang.Object[]}, size=844, count=4}
{scala.util.parsing.combinator.Parsers$Failure,scala.util.parsing.json.Lexer,scala.collection.mutable.HashSet,java.lang.Object[]}, size=843, count=1}
{org.apache.kafka.common.Cluster,java.util.Collections$UnmodifiableRandomAccessList,java.util.ArrayList,java.lang.Object[]}, size=843, count=3}
{net.sf.saxon.style.StylesheetPackage,net.sf.saxon.style.PackageVersion,java.util.ArrayList,java.lang.Object[]}, size=840, count=5}
{net.sf.saxon.style.StylesheetPackage,net.sf.saxon.style.PackageVersion,java.util.ArrayList,java.lang.Object[]}, size=840, count=5}
{net.sf.saxon.style.StylesheetPackage,net.sf.saxon.style.PackageVersion,java.util.ArrayList,java.lang.Object[]}, size=840, count=5}
{java.util.LinkedList$Node,org.apache.ignite.internal.managers.communication.GridIoManager,java.util.IdentityHashMap,java.lang.Object[]}, size=836, count=1}
{org.apache.ignite.internal.managers.communication.GridIoManager$GridUserMessageListener,org.apache.ignite.internal.managers.communication.GridIoManager,java.util.IdentityHashMap,java.lang.Object[]}, size=836, count=1}
{org.postgresql.core.v3.ProtocolConnectionImpl,org.postgresql.core.v3.QueryExecutorImpl,java.util.ArrayList,java.lang.Object[]}, size=835, count=1}
{org.apache.kafka.clients.Metadata,org.apache.kafka.common.internals.ClusterResourceListeners,java.util.ArrayList,java.lang.Object[]}, size=835, count=5}
{scala.util.parsing.combinator.Parsers$$anonfun$failure$1,scala.util.parsing.json.Lexer,scala.collection.mutable.HashSet,java.lang.Object[]}, size=834, count=1}
{org.apache.kafka.clients.consumer.internals.AbstractCoordinator$GroupCoordinatorMetrics,org.apache.kafka.clients.consumer.internals.ConsumerCoordinator,java.util.ArrayList,java.lang.Object[]}, size=834, count=2}
{scala.util.parsing.combinator.Parsers$$anonfun$failure$1,scala.util.parsing.json.Lexer,scala.collection.mutable.HashSet,java.lang.Object[]}, size=834, count=1}
{scala.util.parsing.combinator.Parsers$$anonfun$failure$1,scala.util.parsing.json.Lexer,scala.collection.mutable.HashSet,java.lang.Object[]}, size=834, count=1}
{org.apache.kafka.clients.consumer.KafkaConsumer,org.apache.kafka.common.metrics.Metrics,java.util.ArrayList,java.lang.Object[]}, size=832, count=4}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSAttributeChecker,java.util.Vector,java.lang.Object[]}, size=832, count=4}
{org.apache.kafka.clients.consumer.internals.ConsumerCoordinator$ConsumerCoordinatorMetrics,org.apache.kafka.common.metrics.Metrics,java.util.ArrayList,java.lang.Object[]}, size=832, count=4}
{org.apache.kafka.clients.consumer.internals.Fetcher$FetchManagerMetrics,org.apache.kafka.common.metrics.Metrics,java.util.ArrayList,java.lang.Object[]}, size=832, count=4}
{org.apache.kafka.clients.consumer.internals.AbstractCoordinator$GroupCoordinatorMetrics,org.apache.kafka.common.metrics.Metrics,java.util.ArrayList,java.lang.Object[]}, size=832, count=4}
{org.apache.kafka.common.network.Selector$SelectorMetrics,org.apache.kafka.common.metrics.Metrics,java.util.ArrayList,java.lang.Object[]}, size=832, count=4}
{org.springframework.context.support.ApplicationContextAwareProcessor,org.springframework.context.support.ClassPathXmlApplicationContext,java.util.ArrayList,java.lang.Object[]}, size=828, count=3}
{org.springframework.context.support.ApplicationContextAwareProcessor,org.springframework.context.support.ClassPathXmlApplicationContext,java.util.ArrayList,java.lang.Object[]}, size=828, count=3}
{org.apache.ignite.internal.managers.GridManagerAdapter$1,org.apache.ignite.internal.managers.communication.GridIoManager,java.util.IdentityHashMap,java.lang.Object[]}, size=828, count=1}
{java.lang.ref.Finalizer,com.oracle.jrockit.jfr.Producer,java.util.ArrayList,java.lang.Object[]}, size=828, count=2}
{scala.util.parsing.combinator.token.StdTokens$NumericLit$,scala.util.parsing.json.Lexer,scala.collection.mutable.HashSet,java.lang.Object[]}, size=826, count=1}
{scala.util.parsing.combinator.token.StdTokens$NumericLit$,scala.util.parsing.json.Lexer,scala.collection.mutable.HashSet,java.lang.Object[]}, size=826, count=1}
{scala.util.parsing.combinator.token.StdTokens$NumericLit$,scala.util.parsing.json.Lexer,scala.collection.mutable.HashSet,java.lang.Object[]}, size=826, count=1}
{scala.util.parsing.combinator.token.StdTokens$StringLit$,scala.util.parsing.json.Lexer,scala.collection.mutable.HashSet,java.lang.Object[]}, size=826, count=1}
{scala.util.parsing.combinator.token.StdTokens$StringLit$,scala.util.parsing.json.Lexer,scala.collection.mutable.HashSet,java.lang.Object[]}, size=826, count=1}
{scala.util.parsing.combinator.token.StdTokens$StringLit$,scala.util.parsing.json.Lexer,scala.collection.mutable.HashSet,java.lang.Object[]}, size=826, count=1}
{java.util.HashMap$Node,net.sf.saxon.expr.Component,java.util.ArrayList,java.lang.Object[]}, size=820, count=5}
{org.apache.ignite.internal.managers.communication.GridIoManager$7,org.apache.ignite.internal.managers.communication.GridIoManager,java.util.IdentityHashMap,java.lang.Object[]}, size=820, count=1}
{java.util.HashMap$Node,net.sf.saxon.expr.Component,java.util.ArrayList,java.lang.Object[]}, size=820, count=5}
{org.apache.ignite.internal.managers.communication.GridIoManager$2,org.apache.ignite.internal.managers.communication.GridIoManager,java.util.IdentityHashMap,java.lang.Object[]}, size=820, count=1}
{org.apache.ignite.internal.managers.communication.GridIoManager$4,org.apache.ignite.internal.managers.communication.GridIoManager,java.util.IdentityHashMap,java.lang.Object[]}, size=820, count=1}
{java.util.HashMap$Node,net.sf.saxon.expr.Component,java.util.ArrayList,java.lang.Object[]}, size=820, count=5}
{org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi,org.apache.ignite.internal.util.nio.GridNioServer,java.util.ArrayList,java.lang.Object[]}, size=819, count=1}
{net.sf.saxon.tree.tiny.TinyDocumentImpl,net.sf.saxon.tree.tiny.TinyTree,java.util.ArrayList,java.lang.Object[]}, size=814, count=2}
{net.sf.saxon.tree.tiny.TinyDocumentImpl,net.sf.saxon.tree.tiny.TinyTree,java.util.ArrayList,java.lang.Object[]}, size=814, count=2}
{net.sf.saxon.tree.tiny.TinyDocumentImpl,net.sf.saxon.tree.tiny.TinyTree,java.util.ArrayList,java.lang.Object[]}, size=814, count=2}
{java.util.TreeMap$Entry,javax.management.openmbean.ArrayType,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=812, count=4}
{net.sf.saxon.style.XSLTemplate,net.sf.saxon.style.LiteralResultElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=808, count=8}
{net.sf.saxon.style.XSLTemplate,net.sf.saxon.style.LiteralResultElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=808, count=8}
{net.sf.saxon.style.XSLTemplate,net.sf.saxon.style.LiteralResultElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=808, count=8}
{com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory$CollectionMapping,javax.management.openmbean.ArrayType,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=808, count=4}
{org.apache.kafka.clients.consumer.internals.ConsumerCoordinator$ConsumerCoordinatorMetrics,org.apache.kafka.clients.consumer.internals.ConsumerCoordinator,java.util.ArrayList,java.lang.Object[]}, size=802, count=2}
{com.sun.jmx.remote.util.ClassLoaderWithRepository,com.sun.jmx.remote.util.ClassLoaderWithRepository,java.util.Vector,java.lang.Object[]}, size=802, count=2}
{SomeType98,SomeType70,java.util.ArrayList,java.lang.Object[]}, size=801, count=3}
{org.apache.xerces.impl.xs.assertion.XSAssertImpl,org.eclipse.wst.xml.xpath2.processor.ast.XPath,java.util.ArrayList,java.lang.Object[]}, size=800, count=5}
{org.apache.xerces.impl.xs.assertion.XSAssertImpl,org.eclipse.wst.xml.xpath2.processor.ast.XPath,java.util.ArrayList,java.lang.Object[]}, size=800, count=5}
{org.apache.ignite.internal.processors.cache.binary.BinaryMetadataTransport$MetadataRequestListener,org.apache.ignite.internal.managers.communication.GridIoManager,java.util.IdentityHashMap,java.lang.Object[]}, size=796, count=1}
{org.apache.ignite.internal.processors.marshaller.GridMarshallerMappingProcessor$MissingMappingRequestListener,org.apache.ignite.internal.managers.communication.GridIoManager,java.util.IdentityHashMap,java.lang.Object[]}, size=796, count=1}
{org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.managers.communication.GridIoManager,java.util.IdentityHashMap,java.lang.Object[]}, size=796, count=1}
{org.apache.ignite.internal.processors.cache.GridCacheProcessor$RemovedItemsCleanupTask,org.apache.ignite.internal.processors.cache.GridCacheProcessor,java.util.IdentityHashMap,java.lang.Object[]}, size=793, count=1}
{org.apache.curator.framework.state.ConnectionStateManager$1,org.apache.curator.framework.state.ConnectionStateManager,java.util.concurrent.ArrayBlockingQueue,java.lang.Object[]}, size=792, count=2}
{sun.rmi.transport.WeakRef,javax.management.remote.rmi.RMIJRMPServerImpl$ExportedWrapper,java.util.ArrayList,java.lang.Object[]}, size=792, count=2}
{org.apache.xerces.impl.xpath.regex.RegularExpression,org.apache.xerces.impl.xpath.regex.Token$UnionToken,java.util.Vector,java.lang.Object[]}, size=786, count=3}
{org.apache.xerces.impl.xpath.regex.RegularExpression,org.apache.xerces.impl.xpath.regex.Token$UnionToken,java.util.Vector,java.lang.Object[]}, size=786, count=3}
{javax.management.remote.rmi.RMIConnectionImpl$CombinedClassLoader,javax.management.remote.rmi.RMIConnectionImpl$CombinedClassLoader,java.util.Vector,java.lang.Object[]}, size=786, count=2}
{javax.management.remote.rmi.RMIConnectionImpl,javax.management.remote.rmi.RMIConnectionImpl$CombinedClassLoader,java.util.Vector,java.lang.Object[]}, size=786, count=2}
{org.apache.xerces.impl.xpath.regex.RegularExpression,org.apache.xerces.impl.xpath.regex.Token$UnionToken,java.util.Vector,java.lang.Object[]}, size=786, count=3}
{java.util.LinkedList$Node,org.apache.ignite.internal.processors.cache.GridCacheProcessor,java.util.IdentityHashMap,java.lang.Object[]}, size=785, count=1}
{java.util.logging.Logger,java.util.logging.Logger,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=784, count=14}
{java.lang.ref.WeakReference,java.util.logging.Logger,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=784, count=14}
{com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory$ArrayMapping,javax.management.openmbean.ArrayType,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=776, count=4}
{net.sf.saxon.style.XSLForEach,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=776, count=2}
{net.sf.saxon.style.XSLForEach,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=776, count=2}
{net.sf.saxon.style.XSLForEach,net.sf.saxon.style.Compilation,java.util.Stack,java.lang.Object[]}, size=776, count=2}
{org.apache.kafka.clients.consumer.internals.AbstractCoordinator$GroupCoordinatorMetrics$1,org.apache.kafka.clients.consumer.internals.ConsumerCoordinator,java.util.ArrayList,java.lang.Object[]}, size=770, count=2}
{org.apache.kafka.clients.consumer.internals.ConsumerCoordinator$ConsumerCoordinatorMetrics$1,org.apache.kafka.clients.consumer.internals.ConsumerCoordinator,java.util.ArrayList,java.lang.Object[]}, size=770, count=2}
{SomeType98,SomeType69,java.util.ArrayList,java.lang.Object[]}, size=770, count=2}
{java.lang.ref.WeakReference,org.apache.logging.log4j.core.LoggerContext,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=768, count=4}
{org.eclipse.wst.xml.xpath2.processor.internal.ast.FilterExpr,org.eclipse.wst.xml.xpath2.processor.internal.ast.ParExpr,java.util.ArrayList,java.lang.Object[]}, size=768, count=4}
{org.springframework.context.annotation.ConfigurationClassPostProcessor,org.springframework.context.support.ClassPathXmlApplicationContext,java.util.ArrayList,java.lang.Object[]}, size=756, count=3}
{org.apache.kafka.clients.consumer.internals.ConsumerCoordinator$1,org.apache.kafka.clients.consumer.internals.ConsumerCoordinator,java.util.ArrayList,java.lang.Object[]}, size=754, count=2}
{org.eclipse.jetty.util.thread.NonBlockingThread,org.eclipse.jetty.io.SelectorManager$ManagedSelector,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=752, count=4}
{java.util.LinkedList$Node,org.apache.ignite.internal.managers.eventstorage.GridEventStorageManager,java.util.IdentityHashMap,java.lang.Object[]}, size=752, count=1}
{SomeType136,org.apache.ignite.internal.processors.cache.GridCacheProcessor,java.util.IdentityHashMap,java.lang.Object[]}, size=745, count=1}
{org.apache.ignite.internal.processors.cluster.GridClusterStateProcessor,org.apache.ignite.internal.processors.cache.GridCacheProcessor,java.util.IdentityHashMap,java.lang.Object[]}, size=745, count=1}
{org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.processors.cache.GridCacheProcessor,java.util.IdentityHashMap,java.lang.Object[]}, size=745, count=1}
{org.apache.curator.framework.imps.NamespaceFacade,org.apache.curator.framework.state.ConnectionStateManager,java.util.concurrent.ArrayBlockingQueue,java.lang.Object[]}, size=744, count=2}
{org.apache.ignite.internal.managers.GridManagerAdapter$1,org.apache.ignite.internal.managers.eventstorage.GridEventStorageManager,java.util.IdentityHashMap,java.lang.Object[]}, size=744, count=1}
{org.apache.ignite.internal.processors.cache.persistence.pagemem.PageMemoryImpl,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=740, count=2}
{ch.qos.logback.core.spi.ContextAwareBase,ch.qos.logback.core.rolling.helper.DateTokenConverter,java.util.ArrayList,java.lang.Object[]}, size=738, count=3}
{ch.qos.logback.core.spi.ContextAwareBase,ch.qos.logback.core.rolling.helper.DateTokenConverter,java.util.ArrayList,java.lang.Object[]}, size=738, count=3}
{ch.qos.logback.core.spi.ContextAwareBase,ch.qos.logback.core.rolling.helper.DateTokenConverter,java.util.ArrayList,java.lang.Object[]}, size=738, count=3}
{org.apache.ignite.internal.managers.eventstorage.GridEventStorageManager$RequestListener,org.apache.ignite.internal.managers.eventstorage.GridEventStorageManager,java.util.IdentityHashMap,java.lang.Object[]}, size=736, count=1}
{org.apache.curator.framework.recipes.cache.PathChildrenCache,org.apache.curator.framework.imps.CuratorFrameworkImpl,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=734, count=2}
{ch.qos.logback.core.pattern.LiteralConverter,ch.qos.logback.core.rolling.helper.DateTokenConverter,java.util.ArrayList,java.lang.Object[]}, size=726, count=3}
{ch.qos.logback.core.pattern.LiteralConverter,ch.qos.logback.core.rolling.helper.DateTokenConverter,java.util.ArrayList,java.lang.Object[]}, size=726, count=3}
{ch.qos.logback.core.pattern.LiteralConverter,ch.qos.logback.core.rolling.helper.DateTokenConverter,java.util.ArrayList,java.lang.Object[]}, size=726, count=3}
{net.sf.saxon.style.XSLAttribute,net.sf.saxon.style.XSLTemplate,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=722, count=2}
{com.google.common.util.concurrent.ServiceManager$ServiceManagerState$3,com.google.common.util.concurrent.ServiceManager$ServiceManagerState,java.util.IdentityHashMap,java.lang.Object[]}, size=722, count=1}
{com.google.common.util.concurrent.ServiceManager$ServiceManagerState$2,com.google.common.util.concurrent.ServiceManager$ServiceManagerState,java.util.IdentityHashMap,java.lang.Object[]}, size=722, count=1}
{net.sf.saxon.style.XSLAttribute,net.sf.saxon.style.XSLTemplate,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=722, count=2}
{net.sf.saxon.style.XSLAttribute,net.sf.saxon.style.XSLTemplate,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=722, count=2}
{java.util.LinkedList$Node,org.apache.ignite.internal.managers.deployment.GridDeploymentManager,java.util.IdentityHashMap,java.lang.Object[]}, size=722, count=1}
{org.apache.kafka.clients.consumer.internals.ConsumerCoordinator$ConsumerCoordinatorMetrics,org.apache.kafka.common.metrics.Sensor,java.util.ArrayList,java.lang.Object[]}, size=720, count=2}
{org.apache.kafka.clients.consumer.KafkaConsumer,org.apache.kafka.clients.Metadata,java.util.ArrayList,java.lang.Object[]}, size=720, count=2}
{java.lang.ref.WeakReference,com.google.common.util.concurrent.ServiceManager$ServiceManagerState,java.util.IdentityHashMap,java.lang.Object[]}, size=718, count=1}
{java.util.concurrent.ConcurrentHashMap$Node,org.h2.mvstore.MVMap,org.h2.mvstore.ConcurrentArrayList,java.lang.Object[]}, size=715, count=5}
{java.util.LinkedList$Node,org.apache.ignite.internal.managers.checkpoint.GridCheckpointManager,java.util.IdentityHashMap,java.lang.Object[]}, size=714, count=1}
{com.sun.org.apache.xerces.internal.impl.xs.opti.SchemaParsingConfig,com.sun.org.apache.xerces.internal.impl.XMLNSDocumentScannerImpl,java.util.ArrayList,java.lang.Object[]}, size=714, count=1}
{com.sun.org.apache.xerces.internal.parsers.XIncludeAwareParserConfiguration,com.sun.org.apache.xerces.internal.impl.XMLNSDocumentScannerImpl,java.util.ArrayList,java.lang.Object[]}, size=714, count=1}
{org.apache.ignite.internal.managers.GridManagerAdapter$1,org.apache.ignite.internal.managers.deployment.GridDeploymentManager,java.util.IdentityHashMap,java.lang.Object[]}, size=714, count=1}
{com.sun.org.apache.xerces.internal.parsers.XIncludeAwareParserConfiguration,com.sun.org.apache.xerces.internal.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=713, count=1}
{org.apache.kafka.clients.NetworkClient$DefaultMetadataUpdater,org.apache.kafka.clients.Metadata,java.util.ArrayList,java.lang.Object[]}, size=712, count=3}
{SomeType125,SomeType68,java.util.ArrayList,java.lang.Object[]}, size=712, count=4}
{org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.managers.eventstorage.GridEventStorageManager,java.util.IdentityHashMap,java.lang.Object[]}, size=712, count=1}
{org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi,org.apache.ignite.lang.IgniteBiTuple,java.util.ArrayList,java.lang.Object[]}, size=711, count=1}
{SomeType67,SomeType74,java.util.ArrayList,java.lang.Object[]}, size=710, count=2}
{org.apache.kafka.clients.consumer.KafkaConsumer,org.apache.kafka.clients.consumer.internals.ConsumerCoordinator,java.util.ArrayList,java.lang.Object[]}, size=706, count=2}
{org.apache.curator.framework.recipes.locks.LockInternals,org.apache.curator.framework.imps.NamespaceFacade,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=706, count=2}
{org.apache.ignite.internal.managers.GridManagerAdapter$1,org.apache.ignite.internal.managers.checkpoint.GridCheckpointManager,java.util.IdentityHashMap,java.lang.Object[]}, size=706, count=1}
{org.apache.logging.log4j.core.util.DefaultShutdownCallbackRegistry$1,org.apache.logging.log4j.core.util.DefaultShutdownCallbackRegistry,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=704, count=4}
{java.util.concurrent.ConcurrentHashMap$Node,org.springframework.expression.spel.support.StandardEvaluationContext,java.util.ArrayList,java.lang.Object[]}, size=704, count=2}
{com.google.common.util.concurrent.ServiceManager,com.google.common.util.concurrent.ServiceManager$ServiceManagerState,java.util.IdentityHashMap,java.lang.Object[]}, size=702, count=1}
{net.sf.saxon.serialize.XMLEmitter,net.sf.saxon.event.PipelineConfiguration,java.util.Stack,java.lang.Object[]}, size=700, count=2}
{net.sf.saxon.serialize.XMLEmitter,net.sf.saxon.event.PipelineConfiguration,java.util.Stack,java.lang.Object[]}, size=700, count=2}
{org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=700, count=5}
{org.apache.xerces.impl.xs.traversers.XSDSimpleTypeTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=700, count=5}
{org.apache.xerces.impl.xs.traversers.XSDWildcardTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=700, count=5}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDocumentInfo,java.util.Stack,java.lang.Object[]}, size=700, count=5}
{org.apache.xerces.impl.xs.traversers.DOMOverrideImpl,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=700, count=5}
{org.apache.xerces.impl.xs.traversers.XSDAttributeGroupTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=700, count=5}
{org.apache.xerces.impl.xs.traversers.XSDNotationTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=700, count=5}
{org.apache.xerces.impl.xs.traversers.XSDTypeAlternativeTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=700, count=5}
{org.apache.xerces.impl.xs.assertion.XSAssertImpl,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=700, count=5}
{org.apache.xerces.impl.xs.traversers.OverrideTransformationManager,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=700, count=5}
{org.apache.xerces.impl.xs.traversers.XSDAttributeGroupTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=700, count=5}
{org.apache.xerces.impl.xs.traversers.OverrideTransformationManager,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=700, count=5}
{org.apache.xerces.impl.xs.traversers.XSDUniqueOrKeyTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=700, count=5}
{org.apache.xerces.impl.xs.traversers.XSDKeyrefTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=700, count=5}
{org.apache.xerces.impl.xs.traversers.XSDElementTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=700, count=5}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDocumentInfo,java.util.Stack,java.lang.Object[]}, size=700, count=5}
{org.apache.xerces.impl.xs.traversers.XSDUniqueOrKeyTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=700, count=5}
{org.apache.xerces.impl.xs.traversers.XSDAttributeTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=700, count=5}
{org.apache.xerces.impl.xs.traversers.XSAttributeChecker,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=700, count=5}
{org.apache.xerces.impl.xs.traversers.DOMOverrideImpl,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=700, count=5}
{org.apache.xerces.impl.xs.traversers.XSDTypeAlternativeTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=700, count=5}
{org.apache.xerces.impl.xs.traversers.XSDAttributeTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=700, count=5}
{org.apache.xerces.impl.xs.traversers.XSDNotationTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=700, count=5}
{org.apache.xerces.impl.xs.traversers.XSDElementTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=700, count=5}
{org.apache.xerces.impl.xs.assertion.XSAssertImpl,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=700, count=5}
{org.apache.xerces.impl.xs.traversers.XSDKeyrefTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=700, count=5}
{org.apache.xerces.impl.xs.traversers.XSAttributeChecker,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=700, count=5}
{org.apache.xerces.impl.xs.traversers.XSDWildcardTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=700, count=5}
{org.apache.xerces.impl.xs.traversers.XSDSimpleTypeTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=700, count=5}
{org.apache.xerces.impl.xs.traversers.XSDGroupTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=700, count=5}
{org.apache.xerces.impl.xs.traversers.XSDGroupTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=700, count=5}
{org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=700, count=5}
{net.sf.saxon.serialize.XMLEmitter,net.sf.saxon.event.PipelineConfiguration,java.util.Stack,java.lang.Object[]}, size=700, count=2}
{org.apache.ignite.internal.managers.checkpoint.GridCheckpointManager$CheckpointRequestListener,org.apache.ignite.internal.managers.checkpoint.GridCheckpointManager,java.util.IdentityHashMap,java.lang.Object[]}, size=698, count=1}
{com.sun.jmx.mbeanserver.MXBeanSupport,SomeType74,java.util.ArrayList,java.lang.Object[]}, size=696, count=3}
{org.springframework.context.annotation.ConfigurationClassBeanDefinitionReader$ConfigurationClassBeanDefinition,org.springframework.beans.MutablePropertyValues,java.util.ArrayList,java.lang.Object[]}, size=696, count=2}
{org.apache.kafka.common.metrics.Metrics$2,org.apache.kafka.common.metrics.Metrics,java.util.ArrayList,java.lang.Object[]}, size=696, count=3}
{java.util.LinkedList$Node,org.apache.ignite.internal.processors.plugin.IgnitePluginProcessor,java.util.IdentityHashMap,java.lang.Object[]}, size=696, count=1}
{com.google.common.util.concurrent.ServiceManager$ServiceManagerState$1,com.google.common.util.concurrent.ServiceManager$ServiceManagerState,java.util.IdentityHashMap,java.lang.Object[]}, size=694, count=1}
{java.util.LinkedList$Node,org.apache.ignite.internal.managers.collision.GridCollisionManager,java.util.IdentityHashMap,java.lang.Object[]}, size=690, count=1}
{java.util.LinkedList$Node,org.apache.ignite.internal.managers.indexing.GridIndexingManager,java.util.IdentityHashMap,java.lang.Object[]}, size=690, count=1}
{net.sf.saxon.style.XSLForEach,net.sf.saxon.style.LiteralResultElement,java.util.ArrayList,java.lang.Object[]}, size=688, count=1}
{net.sf.saxon.style.XSLForEach,net.sf.saxon.style.LiteralResultElement,java.util.ArrayList,java.lang.Object[]}, size=688, count=1}
{net.sf.saxon.style.XSLForEach,net.sf.saxon.style.LiteralResultElement,java.util.ArrayList,java.lang.Object[]}, size=688, count=1}
{java.util.logging.LogManager,java.util.logging.LogManager$RootLogger,java.util.ArrayList,java.lang.Object[]}, size=687, count=1}
{org.apache.ignite.logger.java.JavaLogger,java.util.logging.LogManager$RootLogger,java.util.ArrayList,java.lang.Object[]}, size=687, count=1}
{org.apache.ignite.internal.managers.GridManagerAdapter$1,org.apache.ignite.internal.managers.indexing.GridIndexingManager,java.util.IdentityHashMap,java.lang.Object[]}, size=682, count=1}
{org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.managers.deployment.GridDeploymentManager,java.util.IdentityHashMap,java.lang.Object[]}, size=682, count=1}
{java.util.LinkedList$Node,org.apache.ignite.internal.managers.failover.GridFailoverManager,java.util.IdentityHashMap,java.lang.Object[]}, size=682, count=1}
{java.util.LinkedList$Node,org.apache.ignite.internal.managers.loadbalancer.GridLoadBalancerManager,java.util.IdentityHashMap,java.lang.Object[]}, size=682, count=1}
{org.apache.ignite.internal.managers.GridManagerAdapter$1,org.apache.ignite.internal.managers.collision.GridCollisionManager,java.util.IdentityHashMap,java.lang.Object[]}, size=682, count=1}
{org.apache.kafka.clients.consumer.internals.Fetcher,org.apache.kafka.clients.Metadata,java.util.ArrayList,java.lang.Object[]}, size=680, count=2}
{com.sun.org.apache.xerces.internal.impl.xs.XSElementDecl,com.sun.org.apache.xerces.internal.impl.xs.SchemaGrammar,java.util.Vector,java.lang.Object[]}, size=678, count=1}
{ch.qos.logback.classic.joran.action.LevelAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=676, count=2}
{ch.qos.logback.classic.joran.action.LevelAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=676, count=2}
{org.apache.ignite.internal.managers.GridManagerAdapter$1,org.apache.ignite.internal.managers.failover.GridFailoverManager,java.util.IdentityHashMap,java.lang.Object[]}, size=674, count=1}
{org.apache.ignite.internal.managers.GridManagerAdapter$1,org.apache.ignite.internal.managers.loadbalancer.GridLoadBalancerManager,java.util.IdentityHashMap,java.lang.Object[]}, size=674, count=1}
{org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.managers.checkpoint.GridCheckpointManager,java.util.IdentityHashMap,java.lang.Object[]}, size=674, count=1}
{org.apache.logging.log4j.core.LoggerContext$1,org.apache.logging.log4j.core.LoggerContext,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=672, count=4}
{it.sauronsoftware.cron4j.TimerThread,it.sauronsoftware.cron4j.Scheduler,java.util.ArrayList,java.lang.Object[]}, size=669, count=1}
{org.apache.kafka.clients.Metadata,org.apache.kafka.common.internals.ClusterResourceListeners,java.util.ArrayList,java.lang.Object[]}, size=668, count=4}
{com.sun.xml.internal.bind.v2.model.impl.RuntimeTypeRefImpl,com.sun.xml.internal.bind.v2.model.impl.RuntimeClassInfoImpl,com.sun.istack.internal.FinalArrayList,java.lang.Object[]}, size=666, count=2}
{com.sun.jmx.mbeanserver.WeakIdentityHashMap$IdentityWeakReference,SomeType74,java.util.ArrayList,java.lang.Object[]}, size=660, count=3}
{java.sql.SQLException,java.util.Collections$UnmodifiableRandomAccessList,java.util.ArrayList,java.lang.Object[]}, size=660, count=3}
{java.util.HashMap$Node,SomeType73,java.util.ArrayList,java.lang.Object[]}, size=660, count=3}
{sun.misc.Launcher$AppClassLoader,sun.misc.Launcher$ExtClassLoader,java.util.Vector,java.lang.Object[]}, size=658, count=1}
{org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.processors.plugin.IgnitePluginProcessor,java.util.IdentityHashMap,java.lang.Object[]}, size=656, count=1}
{org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.managers.indexing.GridIndexingManager,java.util.IdentityHashMap,java.lang.Object[]}, size=650, count=1}
{org.apache.curator.framework.state.ConnectionStateManager,org.apache.curator.framework.imps.CuratorFrameworkImpl,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=650, count=2}
{SomeType129,org.springframework.context.support.ClassPathXmlApplicationContext,java.util.ArrayList,java.lang.Object[]}, size=650, count=2}
{org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.managers.collision.GridCollisionManager,java.util.IdentityHashMap,java.lang.Object[]}, size=650, count=1}
{java.sql.SQLException,java.sql.SQLException,java.sql.SQLException,java.lang.Object[]}, size=648, count=3}
{ch.qos.logback.core.rolling.helper.FileNamePattern,SomeType76,ch.qos.logback.classic.spi.TurboFilterList,java.lang.Object[]}, size=648, count=6}
{ch.qos.logback.core.rolling.helper.FileNamePattern,SomeType76,ch.qos.logback.classic.spi.TurboFilterList,java.lang.Object[]}, size=648, count=6}
{org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode$1,org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode,java.util.ArrayList,java.lang.Object[]}, size=644, count=2}
{org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.managers.loadbalancer.GridLoadBalancerManager,java.util.IdentityHashMap,java.lang.Object[]}, size=642, count=1}
{org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.managers.failover.GridFailoverManager,java.util.IdentityHashMap,java.lang.Object[]}, size=642, count=1}
{org.apache.ignite.internal.processors.timeout.GridTimeoutProcessor$CancelableTask,org.apache.ignite.internal.IgniteKernal$2,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=641, count=1}
{SomeType116,SomeType79,java.util.Vector,java.lang.Object[]}, size=641, count=1}
{org.apache.xerces.impl.xs.assertion.XSAssertImpl,org.eclipse.wst.xml.xpath2.processor.ast.XPath,java.util.ArrayList,java.lang.Object[]}, size=640, count=4}
{org.apache.ignite.internal.processors.rest.GridRestProcessor$4,org.apache.ignite.internal.processors.rest.GridRestProcessor,java.util.EnumMap,java.lang.Object[]}, size=638, count=1}
{org.apache.curator.framework.imps.NamespaceFacadeCache,org.apache.curator.framework.imps.NamespaceFacade,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=634, count=2}
{org.apache.ignite.spi.discovery.tcp.ServerImpl$SocketReader,org.apache.ignite.spi.discovery.tcp.ServerImpl,java.util.ArrayDeque,java.lang.Object[]}, size=634, count=1}
{java.util.HashMap$Node,com.sun.org.apache.xerces.internal.impl.validation.ValidationManager,java.util.Vector,java.lang.Object[]}, size=630, count=3}
{org.apache.ignite.spi.discovery.tcp.ServerImpl$TcpServer,org.apache.ignite.spi.discovery.tcp.ServerImpl,java.util.ArrayDeque,java.lang.Object[]}, size=630, count=1}
{org.apache.ignite.internal.managers.deployment.GridDeploymentPerVersionStore$SharedDeployment,org.apache.ignite.internal.managers.deployment.GridDeploymentClassLoader,java.util.Vector,java.lang.Object[]}, size=630, count=1}
{com.sun.org.apache.xerces.internal.impl.XMLErrorReporter,com.sun.org.apache.xerces.internal.impl.XMLEntityScanner,com.sun.org.apache.xerces.internal.impl.XMLEntityManager,java.lang.Object[]}, size=628, count=2}
{org.apache.kafka.common.network.Selector$SelectorMetrics,org.apache.kafka.common.metrics.Metrics,java.util.ArrayList,java.lang.Object[]}, size=624, count=3}
{org.apache.kafka.clients.consumer.internals.ConsumerCoordinator$ConsumerCoordinatorMetrics,org.apache.kafka.common.metrics.Metrics,java.util.ArrayList,java.lang.Object[]}, size=624, count=3}
{net.sf.saxon.expr.instruct.ForEach,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=624, count=2}
{net.sf.saxon.expr.instruct.ForEach,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=624, count=2}
{org.apache.kafka.clients.consumer.internals.Fetcher$FetchManagerMetrics,org.apache.kafka.common.metrics.Metrics,java.util.ArrayList,java.lang.Object[]}, size=624, count=3}
{net.sf.saxon.expr.instruct.ForEach,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=624, count=2}
{org.apache.kafka.clients.consumer.internals.AbstractCoordinator$GroupCoordinatorMetrics,org.apache.kafka.common.metrics.Metrics,java.util.ArrayList,java.lang.Object[]}, size=624, count=3}
{org.apache.kafka.clients.consumer.KafkaConsumer,org.apache.kafka.common.metrics.Metrics,java.util.ArrayList,java.lang.Object[]}, size=624, count=3}
{org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode,java.util.ArrayList,java.lang.Object[]}, size=621, count=1}
{java.util.HashMap$Node,javax.management.openmbean.SimpleType,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=620, count=2}
{org.apache.curator.framework.imps.FailedDeleteManager,org.apache.curator.framework.imps.NamespaceFacade,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=618, count=2}
{com.sun.org.apache.xerces.internal.jaxp.validation.SimpleXMLSchema,com.sun.org.apache.xerces.internal.impl.xs.SchemaGrammar,java.util.Vector,java.lang.Object[]}, size=618, count=1}
{org.apache.curator.framework.imps.NamespaceImpl,org.apache.curator.framework.imps.NamespaceFacade,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=618, count=2}
{okhttp3.OkHttpClient,okhttp3.Dispatcher,java.util.ArrayDeque,java.lang.Object[]}, size=616, count=1}
{org.apache.kafka.clients.consumer.internals.ConsumerNetworkClient,org.apache.kafka.clients.Metadata,java.util.ArrayList,java.lang.Object[]}, size=614, count=2}
{com.sun.org.apache.xerces.internal.parsers.DOMParser,com.sun.org.apache.xerces.internal.parsers.XIncludeAwareParserConfiguration,com.sun.org.apache.xerces.internal.impl.XMLEntityManager,java.lang.Object[]}, size=613, count=1}
{org.eclipse.jetty.server.ServerConnector$ServerConnectorManager,org.eclipse.jetty.server.ServerConnector,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=611, count=1}
{org.codehaus.jackson.map.deser.BeanDeserializer,org.codehaus.jackson.map.introspect.AnnotatedClass,java.util.ArrayList,java.lang.Object[]}, size=610, count=1}
{org.apache.kafka.clients.ClientRequest,org.apache.kafka.common.requests.RequestSend,org.apache.kafka.common.protocol.types.Struct,java.lang.Object[]}, size=609, count=3}
{org.apache.ignite.internal.processors.cache.GridCachePartitionExchangeManager$2,org.apache.ignite.internal.processors.cache.GridCachePartitionExchangeManager,java.util.ArrayList,java.lang.Object[]}, size=608, count=1}
{org.apache.ignite.internal.processors.cache.GridCachePartitionExchangeManager$3,org.apache.ignite.internal.processors.cache.GridCachePartitionExchangeManager,java.util.ArrayList,java.lang.Object[]}, size=608, count=1}
{org.apache.ignite.internal.processors.cache.GridCachePartitionExchangeManager$4,org.apache.ignite.internal.processors.cache.GridCachePartitionExchangeManager,java.util.ArrayList,java.lang.Object[]}, size=608, count=1}
{net.sf.saxon.style.ExpressionContext,net.sf.saxon.style.XSLForEach,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=606, count=2}
{net.sf.saxon.style.ExpressionContext,net.sf.saxon.style.XSLForEach,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=606, count=2}
{net.sf.saxon.style.ExpressionContext,net.sf.saxon.style.XSLForEach,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=606, count=2}
{org.apache.xerces.impl.xs.traversers.OverrideTransformationManager,org.apache.xerces.impl.xs.traversers.DOMOverrideImpl,java.util.ArrayList,java.lang.Object[]}, size=605, count=5}
{com.mchange.v2.resourcepool.BasicResourcePool,com.mchange.v2.c3p0.impl.NewPooledConnection,java.sql.SQLException,java.lang.Object[]}, size=605, count=1}
{org.apache.xerces.impl.xs.traversers.OverrideTransformationManager,org.apache.xerces.impl.xs.traversers.DOMOverrideImpl,java.util.ArrayList,java.lang.Object[]}, size=605, count=5}
{java.util.LinkedList$Node,org.apache.ignite.internal.processors.rest.GridRestProcessor,java.util.EnumMap,java.lang.Object[]}, size=604, count=1}
{org.apache.curator.framework.imps.NamespaceWatcherMap,org.apache.curator.framework.imps.NamespaceFacade,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=602, count=2}
{org.apache.ignite.spi.discovery.tcp.ServerImpl,org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode,java.util.ArrayList,java.lang.Object[]}, size=600, count=1}
{org.apache.xerces.impl.xpath.regex.Token$ParenToken,org.apache.xerces.impl.xpath.regex.Token$UnionToken,java.util.Vector,java.lang.Object[]}, size=600, count=3}
{org.apache.xerces.impl.xpath.regex.Token$ParenToken,org.apache.xerces.impl.xpath.regex.Token$UnionToken,java.util.Vector,java.lang.Object[]}, size=600, count=3}
{org.apache.xerces.impl.xpath.regex.Token$ParenToken,org.apache.xerces.impl.xpath.regex.Token$UnionToken,java.util.Vector,java.lang.Object[]}, size=600, count=3}
{java.lang.NumberFormatException,java.util.Collections$UnmodifiableRandomAccessList,java.util.ArrayList,java.lang.Object[]}, size=600, count=3}
{net.sf.saxon.style.XSLForEach,net.sf.saxon.style.XSLElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=598, count=1}
{net.sf.saxon.style.XSLForEach,net.sf.saxon.style.XSLElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=598, count=1}
{net.sf.saxon.style.XSLForEach,net.sf.saxon.style.XSLElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=598, count=1}
{com.sun.org.apache.xerces.internal.impl.xs.XMLSchemaValidator,com.sun.org.apache.xerces.internal.impl.xs.XMLSchemaValidator$ValueStoreCache,java.util.Vector,java.lang.Object[]}, size=597, count=1}
{org.apache.xerces.impl.xs.XSGrammarBucket,org.apache.xerces.impl.xs.SchemaGrammar,java.util.Vector,java.lang.Object[]}, size=594, count=1}
{org.apache.xerces.impl.xs.XSGrammarBucket,org.apache.xerces.impl.xs.SchemaGrammar,java.util.Vector,java.lang.Object[]}, size=594, count=1}
{org.apache.xerces.impl.xs.XSGrammarBucket,org.apache.xerces.impl.xs.SchemaGrammar,java.util.Vector,java.lang.Object[]}, size=594, count=1}
{org.apache.ignite.internal.managers.discovery.GridDiscoveryManager$DiscoveryWorker,org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.IdentityHashMap,java.lang.Object[]}, size=592, count=1}
{org.apache.ignite.internal.managers.discovery.GridDiscoveryManager$MetricsUpdater,org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.IdentityHashMap,java.lang.Object[]}, size=592, count=1}
{org.apache.ignite.internal.managers.discovery.GridDiscoveryManager$5,org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.IdentityHashMap,java.lang.Object[]}, size=592, count=1}
{org.apache.ignite.internal.processors.marshaller.MarshallerMappingTransport,org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.IdentityHashMap,java.lang.Object[]}, size=592, count=1}
{org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.IdentityHashMap,java.lang.Object[]}, size=592, count=1}
{SomeType133$2,org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.IdentityHashMap,java.lang.Object[]}, size=592, count=1}
{org.apache.ignite.internal.processors.cache.binary.BinaryMetadataTransport,org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.IdentityHashMap,java.lang.Object[]}, size=592, count=1}
{org.apache.ignite.internal.managers.GridManagerAdapter$1,org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.IdentityHashMap,java.lang.Object[]}, size=592, count=1}
{org.apache.ignite.internal.managers.discovery.GridDiscoveryManager$6,org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.IdentityHashMap,java.lang.Object[]}, size=592, count=1}
{SomeType133$3,org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.IdentityHashMap,java.lang.Object[]}, size=592, count=1}
{java.util.LinkedList$Node,org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.IdentityHashMap,java.lang.Object[]}, size=592, count=1}
{org.apache.ignite.internal.managers.discovery.GridDiscoveryManager$4,org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.IdentityHashMap,java.lang.Object[]}, size=592, count=1}
{org.apache.ignite.internal.managers.discovery.GridDiscoveryManager$7,org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.IdentityHashMap,java.lang.Object[]}, size=592, count=1}
{SomeType133$5,org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.IdentityHashMap,java.lang.Object[]}, size=592, count=1}
{net.sf.saxon.expr.AdjacentTextNodeMerger,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=590, count=2}
{net.sf.saxon.expr.AdjacentTextNodeMerger,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=590, count=2}
{net.sf.saxon.expr.AdjacentTextNodeMerger,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=590, count=2}
{org.apache.ignite.internal.processors.rest.GridRestProcessor$1,org.apache.ignite.internal.processors.rest.GridRestProcessor,java.util.EnumMap,java.lang.Object[]}, size=588, count=1}
{java.lang.Package,org.apache.ignite.internal.managers.deployment.GridDeploymentClassLoader,java.util.Vector,java.lang.Object[]}, size=587, count=1}
{ch.qos.logback.core.rolling.RollingFileAppender,SomeType76,ch.qos.logback.classic.spi.TurboFilterList,java.lang.Object[]}, size=576, count=3}
{com.google.common.collect.AbstractMapBasedMultimap$AsMap,com.google.common.collect.Multimaps$CustomSetMultimap,java.util.EnumMap,java.lang.Object[]}, size=576, count=2}
{ch.qos.logback.core.rolling.RollingFileAppender,SomeType76,ch.qos.logback.classic.spi.TurboFilterList,java.lang.Object[]}, size=576, count=3}
{com.sun.jmx.mbeanserver.StandardMBeanSupport,org.apache.ignite.internal.StripedExecutorMXBeanAdapter,org.apache.ignite.internal.util.StripedExecutor,long[]}, size=576, count=1}
{com.google.common.collect.Multimaps$CustomSetMultimap,com.google.common.collect.AbstractMapBasedMultimap$AsMap,java.util.EnumMap,java.lang.Object[]}, size=576, count=2}
{org.apache.zookeeper.ClientCnxn,org.apache.zookeeper.client.StaticHostProvider,java.util.ArrayList,java.lang.Object[]}, size=574, count=2}
{org.apache.curator.framework.imps.NamespaceImpl,org.apache.curator.framework.imps.CuratorFrameworkImpl,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=570, count=2}
{org.apache.curator.framework.imps.FailedDeleteManager,org.apache.curator.framework.imps.CuratorFrameworkImpl,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=570, count=2}
{org.apache.curator.framework.imps.NamespaceImpl,org.apache.curator.framework.imps.CuratorFrameworkImpl,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=570, count=2}
{com.sun.org.apache.xerces.internal.parsers.DOMParser,com.sun.org.apache.xerces.internal.parsers.XIncludeAwareParserConfiguration,java.util.ArrayList,java.lang.Object[]}, size=567, count=1}
{org.apache.ignite.internal.GridKernalContextImpl,org.apache.ignite.internal.processors.rest.GridRestProcessor,java.util.EnumMap,java.lang.Object[]}, size=564, count=1}
{java.util.concurrent.ConcurrentHashMap$Node,org.apache.logging.log4j.core.LoggerContext,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=564, count=3}
{com.sun.org.apache.xerces.internal.impl.xs.XSComplexTypeDecl,com.sun.org.apache.xerces.internal.impl.xs.XSComplexTypeDecl,com.sun.org.apache.xerces.internal.impl.xs.models.XSDFACM,java.lang.Object[]}, size=564, count=2}
{org.apache.ignite.internal.processors.cache.GridCacheMvccManager,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=563, count=1}
{org.apache.xerces.impl.xs.traversers.XSDUniqueOrKeyTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=560, count=4}
{org.apache.xerces.impl.xs.traversers.XSDAttributeTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=560, count=4}
{org.apache.xerces.impl.xs.traversers.XSDKeyrefTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=560, count=4}
{org.apache.xerces.impl.xs.traversers.DOMOverrideImpl,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=560, count=4}
{org.apache.xerces.impl.xs.traversers.XSDComplexTypeTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=560, count=4}
{org.apache.xerces.impl.xs.traversers.XSDNotationTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=560, count=4}
{org.apache.xerces.impl.xs.traversers.XSDTypeAlternativeTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=560, count=4}
{org.apache.xerces.impl.xs.traversers.XSDAttributeGroupTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=560, count=4}
{org.apache.xerces.impl.xs.traversers.XSDHandler,org.apache.xerces.impl.xs.traversers.XSDocumentInfo,java.util.Stack,java.lang.Object[]}, size=560, count=4}
{org.apache.xerces.impl.xs.traversers.XSDSimpleTypeTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=560, count=4}
{org.apache.xerces.impl.xs.traversers.OverrideTransformationManager,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=560, count=4}
{org.apache.xerces.impl.xs.traversers.XSDGroupTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=560, count=4}
{org.apache.xerces.impl.xs.traversers.XSDElementTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=560, count=4}
{org.apache.xerces.impl.xs.assertion.XSAssertImpl,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=560, count=4}
{org.apache.xerces.impl.xs.traversers.XSDWildcardTraverser,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=560, count=4}
{org.apache.xerces.impl.xs.traversers.XSAttributeChecker,org.apache.xerces.impl.xs.traversers.XSDHandler,java.util.Vector,java.lang.Object[]}, size=560, count=4}
{org.apache.kafka.clients.producer.internals.RecordAccumulator,org.apache.kafka.clients.producer.internals.BufferPool,java.util.ArrayDeque,java.lang.Object[]}, size=557, count=1}
{org.apache.kafka.clients.producer.internals.RecordAccumulator,org.apache.kafka.clients.producer.internals.BufferPool,java.util.ArrayDeque,java.lang.Object[]}, size=557, count=1}
{org.apache.kafka.clients.producer.internals.RecordAccumulator,org.apache.kafka.clients.producer.internals.BufferPool,java.util.ArrayDeque,java.lang.Object[]}, size=557, count=1}
{net.sf.saxon.serialize.ReconfigurableSerializer,net.sf.saxon.serialize.XMLIndenter,java.util.ArrayList,java.lang.Object[]}, size=556, count=2}
{org.apache.ignite.internal.managers.communication.GridIoMessage,org.apache.ignite.internal.processors.cache.distributed.near.GridNearLockResponse,java.util.ArrayList,java.lang.Object[]}, size=556, count=2}
{org.apache.kafka.common.requests.RequestSend,org.apache.kafka.common.requests.RequestHeader,org.apache.kafka.common.protocol.types.Struct,java.lang.Object[]}, size=555, count=3}
{org.apache.curator.framework.imps.NamespaceWatcherMap,org.apache.curator.framework.imps.CuratorFrameworkImpl,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=554, count=2}
{org.h2.mvstore.MVStore,org.h2.mvstore.MVMap,org.h2.mvstore.Page,java.lang.Object[]}, size=554, count=1}
{org.apache.curator.framework.imps.CuratorFrameworkImpl$2,org.apache.curator.framework.imps.CuratorFrameworkImpl,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=554, count=2}
{org.apache.curator.framework.imps.CuratorFrameworkImpl,org.apache.curator.framework.state.ConnectionStateManager,java.util.concurrent.ArrayBlockingQueue,java.lang.Object[]}, size=553, count=1}
{org.apache.curator.framework.imps.CuratorFrameworkImpl,org.apache.curator.framework.state.ConnectionStateManager,java.util.concurrent.ArrayBlockingQueue,java.lang.Object[]}, size=553, count=1}
{org.apache.kafka.clients.consumer.internals.Fetcher$1,org.apache.kafka.common.requests.FetchRequest,org.apache.kafka.common.protocol.types.Struct,java.lang.Object[]}, size=552, count=3}
{org.apache.ignite.spi.IgniteSpiAdapter$1,org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=551, count=1}
{java.security.ProtectionDomain,org.apache.ignite.internal.managers.deployment.GridDeploymentClassLoader,java.util.Vector,java.lang.Object[]}, size=549, count=1}
{org.eclipse.jetty.server.Server,org.eclipse.jetty.util.thread.QueuedThreadPool,org.eclipse.jetty.util.BlockingArrayQueue,java.lang.Object[]}, size=546, count=1}
{org.apache.ignite.internal.processors.cache.transactions.IgniteTxManager,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=544, count=1}
{com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory$EnumMapping,javax.management.openmbean.SimpleType,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=543, count=3}
{com.mchange.v2.c3p0.impl.C3P0PooledConnectionPool,com.mchange.v2.resourcepool.BasicResourcePool,org.postgresql.util.PSQLException,java.lang.Object[]}, size=539, count=1}
{org.apache.ignite.internal.processors.schedule.IgniteScheduleProcessor,it.sauronsoftware.cron4j.Scheduler,java.util.ArrayList,java.lang.Object[]}, size=538, count=1}
{org.apache.curator.framework.imps.CuratorFrameworkImpl$4,org.apache.curator.framework.imps.CuratorFrameworkImpl,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=538, count=2}
{org.apache.curator.framework.imps.CuratorFrameworkImpl$3,org.apache.curator.framework.imps.CuratorFrameworkImpl,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=538, count=2}
{org.apache.curator.framework.imps.CuratorFrameworkImpl$1,org.apache.curator.framework.imps.CuratorFrameworkImpl,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=538, count=2}
{javax.management.remote.rmi.RMIJRMPServerImpl,javax.management.remote.rmi.RMIJRMPServerImpl$ExportedWrapper,java.util.ArrayList,java.lang.Object[]}, size=536, count=2}
{ch.qos.logback.classic.encoder.PatternLayoutEncoder,SomeType76,ch.qos.logback.classic.spi.TurboFilterList,java.lang.Object[]}, size=536, count=4}
{ch.qos.logback.classic.encoder.PatternLayoutEncoder,SomeType76,ch.qos.logback.classic.spi.TurboFilterList,java.lang.Object[]}, size=536, count=4}
{SomeType98,SomeType70,java.util.ArrayList,java.lang.Object[]}, size=534, count=2}
{org.apache.ignite.internal.processors.rest.protocols.http.jetty.GridJettyRestHandler,org.eclipse.jetty.server.Server,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=533, count=1}
{java.lang.NumberFormatException,java.lang.NumberFormatException,java.lang.NumberFormatException,java.lang.Object[]}, size=528, count=3}
{org.eclipse.jetty.util.component.ContainerLifeCycle$Bean,org.eclipse.jetty.server.ServerConnector,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=527, count=1}
{net.sf.saxon.style.LiteralResultElement,net.sf.saxon.style.XSLForEach,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=526, count=2}
{net.sf.saxon.style.LiteralResultElement,net.sf.saxon.style.XSLForEach,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=526, count=2}
{net.sf.saxon.style.XSLElement,net.sf.saxon.style.XSLForEach,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=526, count=2}
{net.sf.saxon.style.XSLElement,net.sf.saxon.style.XSLForEach,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=526, count=2}
{net.sf.saxon.style.LiteralResultElement,net.sf.saxon.style.XSLForEach,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=526, count=2}
{net.sf.saxon.style.XSLElement,net.sf.saxon.style.XSLForEach,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=526, count=2}
{org.apache.ignite.internal.processors.cache.GridCacheDeploymentManager$CacheClassLoader,org.apache.ignite.internal.processors.cache.GridCacheDeploymentManager$CacheClassLoader,java.util.Vector,java.lang.Object[]}, size=522, count=1}
{ch.qos.logback.core.ConsoleAppender,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=521, count=1}
{org.apache.ignite.internal.managers.GridManagerAdapter$1,org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=519, count=1}
{org.apache.ignite.configuration.IgniteConfiguration,org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=519, count=1}
{org.apache.ignite.spi.discovery.tcp.ServerImpl,org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=519, count=1}
{java.lang.ref.Finalizer,com.mchange.v2.resourcepool.BasicResourcePool,org.postgresql.util.PSQLException,java.lang.Object[]}, size=517, count=1}
{org.apache.ignite.internal.processors.cache.GridCacheDeploymentManager,org.apache.ignite.internal.processors.cache.GridCacheDeploymentManager$CacheClassLoader,java.util.Vector,java.lang.Object[]}, size=514, count=1}
{com.google.common.collect.Multimaps$Keys,com.google.common.collect.Multimaps$CustomSetMultimap,java.util.EnumMap,java.lang.Object[]}, size=512, count=2}
{ch.qos.logback.core.spi.FilterAttachableImpl,ch.qos.logback.core.util.COWArrayList,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=512, count=4}
{org.apache.logging.log4j.core.layout.PatternLayout,org.apache.logging.log4j.core.config.xml.XmlConfiguration,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=512, count=8}
{org.apache.kafka.clients.producer.KafkaProducer,org.apache.kafka.common.metrics.Sensor,java.util.ArrayList,java.lang.Object[]}, size=512, count=1}
{org.apache.kafka.clients.producer.KafkaProducer,org.apache.kafka.common.metrics.Sensor,java.util.ArrayList,java.lang.Object[]}, size=512, count=1}
{com.google.common.cache.LocalCache$WeakEntry,com.google.common.cache.LocalCache$StrongValueReference,com.google.common.collect.RegularImmutableSet,java.lang.Object[]}, size=512, count=2}
{ch.qos.logback.core.spi.FilterAttachableImpl,ch.qos.logback.core.util.COWArrayList,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=512, count=4}
{ch.qos.logback.core.spi.FilterAttachableImpl,ch.qos.logback.core.util.COWArrayList,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=512, count=4}
{org.apache.kafka.clients.producer.KafkaProducer,org.apache.kafka.common.metrics.Sensor,java.util.ArrayList,java.lang.Object[]}, size=512, count=1}
{org.apache.logging.log4j.core.pattern.MessagePatternConverter,org.apache.logging.log4j.core.config.xml.XmlConfiguration,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=512, count=8}
{sun.reflect.misc.MethodUtil,sun.reflect.misc.MethodUtil,java.util.Vector,java.lang.Object[]}, size=508, count=1}
{SomeType78,SomeType78,java.util.Vector,java.lang.Object[]}, size=506, count=1}
{com.mchange.v2.resourcepool.BasicResourcePool$CheckIdleResourcesTask,com.mchange.v2.resourcepool.BasicResourcePool,org.postgresql.util.PSQLException,java.lang.Object[]}, size=505, count=1}
{SomeType86,SomeType75,java.util.Vector,java.lang.Object[]}, size=505, count=1}
{com.mchange.v2.resourcepool.BasicResourcePool$CullTask,com.mchange.v2.resourcepool.BasicResourcePool,org.postgresql.util.PSQLException,java.lang.Object[]}, size=505, count=1}
{SomeType120,org.springframework.context.support.ClassPathXmlApplicationContext,java.util.ArrayList,java.lang.Object[]}, size=504, count=2}
{SomeType120,org.springframework.context.support.ClassPathXmlApplicationContext,java.util.ArrayList,java.lang.Object[]}, size=504, count=2}
{org.apache.kafka.clients.Metadata,org.apache.kafka.common.internals.ClusterResourceListeners,java.util.ArrayList,java.lang.Object[]}, size=501, count=3}
{net.sf.saxon.style.XSLCallTemplate,net.sf.saxon.style.LiteralResultElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=500, count=5}
{net.sf.saxon.style.XSLAttribute,net.sf.saxon.style.LiteralResultElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=500, count=5}
{net.sf.saxon.style.XSLCallTemplate,net.sf.saxon.style.LiteralResultElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=500, count=5}
{net.sf.saxon.style.XSLAttribute,net.sf.saxon.style.LiteralResultElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=500, count=5}
{net.sf.saxon.style.XSLAttribute,net.sf.saxon.style.LiteralResultElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=500, count=5}
{java.security.ProtectionDomain,SomeType75,java.util.Vector,java.lang.Object[]}, size=500, count=1}
{net.sf.saxon.style.XSLCallTemplate,net.sf.saxon.style.LiteralResultElement,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=500, count=5}
{org.apache.ignite.internal.processors.cache.CacheAffinitySharedManager,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=499, count=1}
{java.util.HashMap$Node,com.mchange.v2.resourcepool.BasicResourcePool,org.postgresql.util.PSQLException,java.lang.Object[]}, size=497, count=1}
{java.util.HashMap$Node,oracle.jrockit.jfr.events.JavaProducerDescriptor,java.util.ArrayList,java.lang.Object[]}, size=496, count=2}
{com.sun.org.apache.xpath.internal.axes.WalkingIterator,com.sun.org.apache.xpath.internal.axes.IteratorPool,java.util.ArrayList,java.lang.Object[]}, size=496, count=2}
{com.sun.org.apache.xerces.internal.impl.xs.opti.SchemaDOMParser,com.sun.org.apache.xerces.internal.impl.xs.opti.SchemaParsingConfig,com.sun.org.apache.xerces.internal.impl.XMLEntityManager,java.lang.Object[]}, size=492, count=1}
{sun.misc.Launcher$ExtClassLoader,sun.misc.URLClassPath,java.util.ArrayList,java.lang.Object[]}, size=491, count=1}
{org.apache.ignite.internal.managers.deployment.GridDeploymentClassLoader,org.apache.ignite.internal.managers.deployment.GridDeploymentClassLoader,java.util.Vector,java.lang.Object[]}, size=491, count=1}
{org.apache.curator.framework.imps.NamespaceFacadeCache,org.apache.curator.framework.imps.CuratorFrameworkImpl,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=490, count=2}
{java.util.ResourceBundle$RBClassLoader,java.util.ResourceBundle$RBClassLoader,java.util.Vector,java.lang.Object[]}, size=490, count=1}
{SomeType115,SomeType78,java.util.Vector,java.lang.Object[]}, size=489, count=1}
{net.sf.saxon.expr.GeneralComparison20,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=488, count=2}
{net.sf.saxon.expr.GeneralComparison20,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=488, count=2}
{net.sf.saxon.expr.GeneralComparison20,net.sf.saxon.expr.instruct.Template,java.util.ArrayList,java.lang.Object[]}, size=488, count=2}
{ch.qos.logback.core.joran.action.DefinePropertyAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=487, count=1}
{org.apache.logging.log4j.core.config.LoggerConfig,org.apache.logging.log4j.core.config.LoggerConfig,java.util.ArrayList,java.lang.Object[]}, size=486, count=3}
{org.apache.ignite.internal.processors.query.GridQueryProcessor,org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode,java.util.ArrayList,java.lang.Object[]}, size=485, count=1}
{org.apache.xerces.impl.xs.traversers.OverrideTransformationManager,org.apache.xerces.impl.xs.traversers.DOMOverrideImpl,java.util.ArrayList,java.lang.Object[]}, size=484, count=4}
{SomeType67,java.util.Collections$UnmodifiableRandomAccessList,java.util.ArrayList,java.lang.Object[]}, size=480, count=3}
{org.apache.ignite.internal.processors.cache.GridCacheIoManager,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=480, count=1}
{ch.qos.logback.core.status.InfoStatus,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=478, count=1}
{org.apache.xerces.impl.xs.XMLSchemaLoaderExt,org.apache.xerces.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=477, count=1}
{org.apache.xerces.impl.xs.XMLSchemaLoaderExt,org.apache.xerces.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=477, count=1}
{org.apache.xerces.impl.xs.XMLSchemaLoaderExt,org.apache.xerces.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=477, count=1}
{com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderImpl,com.sun.org.apache.xerces.internal.parsers.DOMParser,java.util.Stack,java.lang.Object[]}, size=475, count=1}
{org.apache.ignite.internal.processors.cache.version.GridCacheVersionManager,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=475, count=1}
{org.apache.ignite.internal.processors.cache.persistence.file.FilePageStoreManager,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=474, count=1}
{javax.management.remote.rmi.RMIConnectionImpl,javax.management.remote.rmi.RMIJRMPServerImpl,java.util.ArrayList,java.lang.Object[]}, size=472, count=2}
{ch.qos.logback.core.rolling.FixedWindowRollingPolicy,SomeType76,ch.qos.logback.classic.spi.TurboFilterList,java.lang.Object[]}, size=471, count=3}
{ch.qos.logback.core.rolling.FixedWindowRollingPolicy,SomeType76,ch.qos.logback.classic.spi.TurboFilterList,java.lang.Object[]}, size=471, count=3}
{ch.qos.logback.core.joran.action.StatusListenerAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=471, count=1}
{com.sun.org.apache.xerces.internal.jaxp.validation.ValidatorImpl,com.sun.org.apache.xerces.internal.jaxp.validation.XMLSchemaValidatorComponentManager,com.sun.org.apache.xerces.internal.impl.XMLEntityManager,java.lang.Object[]}, size=471, count=1}
{ch.qos.logback.core.joran.util.beans.BeanDescriptionCache,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=470, count=1}
{net.sf.saxon.expr.EarlyEvaluationContext,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=468, count=1}
{net.sf.saxon.expr.EarlyEvaluationContext,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=468, count=1}
{java.util.TreeMap$Entry,sun.security.x509.PolicyMappingsExtension,java.util.ArrayList,java.lang.Object[]}, size=468, count=2}
{java.util.TreeMap$Entry,sun.security.x509.AuthorityInfoAccessExtension,java.util.ArrayList,java.lang.Object[]}, size=468, count=2}
{net.sf.saxon.expr.EarlyEvaluationContext,net.sf.saxon.Configuration,java.util.ArrayList,java.lang.Object[]}, size=468, count=1}
{com.sun.jmx.mbeanserver.MXBeanSupport,SomeType74,java.util.ArrayList,java.lang.Object[]}, size=464, count=2}
{org.apache.ignite.internal.processors.rest.protocols.http.jetty.GridJettyRestProtocol,org.eclipse.jetty.server.Server,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=464, count=1}
{ch.qos.logback.classic.joran.action.LoggerContextListenerAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=463, count=1}
{ch.qos.logback.classic.joran.action.ReceiverAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=463, count=1}
{ch.qos.logback.classic.joran.action.EvaluatorAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=463, count=1}
{ch.qos.logback.core.joran.action.ShutdownHookAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=463, count=1}
{ch.qos.logback.core.joran.action.AppenderAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=463, count=1}
{ch.qos.logback.core.joran.action.IncludeAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=463, count=1}
{ch.qos.logback.core.joran.action.ParamAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=463, count=1}
{ch.qos.logback.classic.joran.action.ConfigurationAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=462, count=1}
{ch.qos.logback.classic.sift.SiftAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=462, count=1}
{ch.qos.logback.core.joran.spi.SimpleRuleStore,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=462, count=1}
{org.h2.store.LobStorageMap,org.h2.mvstore.MVMap,org.h2.mvstore.Page,java.lang.Object[]}, size=460, count=1}
{com.google.common.util.concurrent.ServiceManager$ServiceManagerState,java.util.Collections$SynchronizedRandomAccessList,java.util.ArrayList,java.lang.Object[]}, size=460, count=2}
{java.security.ProtectionDomain,org.apache.ignite.internal.processors.cache.GridCacheDeploymentManager$CacheClassLoader,java.util.Vector,java.lang.Object[]}, size=459, count=1}
{org.apache.ignite.internal.processors.cluster.GridClusterStateProcessor,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=458, count=1}
{SomeType137,SomeType138,java.util.ArrayList,java.lang.Object[]}, size=457, count=1}
{org.jsr166.ConcurrentHashMap8$Node,org.jsr166.ConcurrentHashMap8$Node,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=456, count=3}
{javax.management.remote.rmi.RMIJRMPServerImpl$ExportedWrapper,javax.management.remote.rmi.RMIJRMPServerImpl,java.util.ArrayList,java.lang.Object[]}, size=456, count=2}
{oracle.jrockit.jfr.events.JavaProducerDescriptor,java.util.Collections$UnmodifiableCollection,java.util.ArrayList,java.lang.Object[]}, size=456, count=2}
{java.util.HashMap$Node,SomeType80,java.util.ArrayList,java.lang.Object[]}, size=456, count=2}
{ch.qos.logback.core.joran.action.TimestampAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=455, count=1}
{ch.qos.logback.core.joran.action.NewRuleAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=455, count=1}
{ch.qos.logback.core.joran.action.ConversionRuleAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=455, count=1}
{ch.qos.logback.classic.joran.action.InsertFromJNDIAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=454, count=1}
{ch.qos.logback.core.joran.action.ContextPropertyAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=454, count=1}
{ch.qos.logback.classic.joran.action.JMXConfiguratorAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=454, count=1}
{ch.qos.logback.core.joran.util.beans.BeanDescriptionFactory,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=454, count=1}
{ch.qos.logback.classic.joran.action.ConsolePluginAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=454, count=1}
{ch.qos.logback.classic.joran.action.ContextNameAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=454, count=1}
{SomeType130,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=451, count=1}
{java.security.ProtectionDomain,SomeType78,java.util.Vector,java.lang.Object[]}, size=451, count=1}
{java.security.ProtectionDomain,SomeType79,java.util.Vector,java.lang.Object[]}, size=451, count=1}
{net.sf.saxon.serialize.ReconfigurableSerializer,net.sf.saxon.event.PipelineConfiguration,java.util.Stack,java.lang.Object[]}, size=450, count=2}
{org.apache.curator.framework.imps.NamespaceFacade,org.apache.curator.framework.imps.CuratorFrameworkImpl,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=450, count=1}
{net.sf.saxon.serialize.ReconfigurableSerializer,net.sf.saxon.event.PipelineConfiguration,java.util.Stack,java.lang.Object[]}, size=450, count=2}
{net.sf.saxon.serialize.XMLIndenter,net.sf.saxon.event.PipelineConfiguration,java.util.Stack,java.lang.Object[]}, size=450, count=2}
{org.apache.curator.framework.imps.NamespaceFacade,org.apache.curator.framework.imps.CuratorFrameworkImpl,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=450, count=1}
{SomeType131,SomeType79,java.util.Vector,java.lang.Object[]}, size=450, count=1}
{net.sf.saxon.serialize.ReconfigurableSerializer,net.sf.saxon.event.PipelineConfiguration,java.util.Stack,java.lang.Object[]}, size=450, count=2}
{org.apache.ignite.internal.processors.cache.GridCacheSharedTtlCleanupManager,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=450, count=1}
{net.sf.saxon.serialize.XMLIndenter,net.sf.saxon.event.PipelineConfiguration,java.util.Stack,java.lang.Object[]}, size=450, count=2}
{net.sf.saxon.serialize.XMLIndenter,net.sf.saxon.event.PipelineConfiguration,java.util.Stack,java.lang.Object[]}, size=450, count=2}
{org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNodesRing,org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode,java.util.ArrayList,java.lang.Object[]}, size=450, count=1}
{org.postgresql.core.v3.SimpleQuery,org.postgresql.core.v3.ProtocolConnectionImpl,java.util.ArrayList,java.lang.Object[]}, size=448, count=2}
{org.springframework.expression.spel.ast.MethodReference$CachedMethodExecutor,java.util.Collections$UnmodifiableRandomAccessList,java.util.ArrayList,java.lang.Object[]}, size=448, count=2}
{org.apache.ignite.spi.discovery.tcp.ServerImpl$EnsuredMessageHistory,org.apache.ignite.spi.discovery.tcp.ServerImpl,java.util.ArrayDeque,java.lang.Object[]}, size=446, count=1}
{org.jsr166.ConcurrentHashMap8$Node,org.apache.ignite.internal.processors.cache.GridCacheDeploymentManager$CacheClassLoader,java.util.Vector,java.lang.Object[]}, size=445, count=1}
{org.apache.kafka.clients.producer.internals.BufferPool,org.apache.kafka.common.metrics.Sensor,java.util.ArrayList,java.lang.Object[]}, size=444, count=1}
{org.apache.kafka.clients.producer.internals.BufferPool,org.apache.kafka.common.metrics.Sensor,java.util.ArrayList,java.lang.Object[]}, size=444, count=1}
{org.apache.kafka.clients.producer.internals.BufferPool,org.apache.kafka.common.metrics.Sensor,java.util.ArrayList,java.lang.Object[]}, size=444, count=1}
{java.security.ProtectionDomain,java.util.ResourceBundle$RBClassLoader,java.util.Vector,java.lang.Object[]}, size=443, count=1}
{SomeType75,SomeType75,java.util.Vector,java.lang.Object[]}, size=442, count=1}
{net.sf.saxon.expr.instruct.Bindery,net.sf.saxon.expr.instruct.SlotManager,java.util.ArrayList,java.lang.Object[]}, size=442, count=2}
{net.sf.saxon.expr.instruct.Bindery,net.sf.saxon.expr.instruct.SlotManager,java.util.ArrayList,java.lang.Object[]}, size=442, count=2}
{ch.qos.logback.classic.selector.DefaultContextSelector,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=442, count=1}
{net.sf.saxon.serialize.ReconfigurableSerializer,net.sf.saxon.serialize.XMLIndenter,java.util.ArrayList,java.lang.Object[]}, size=442, count=2}
{net.sf.saxon.expr.instruct.Bindery,net.sf.saxon.expr.instruct.SlotManager,java.util.ArrayList,java.lang.Object[]}, size=442, count=2}
{net.sf.saxon.serialize.ReconfigurableSerializer,net.sf.saxon.serialize.XMLIndenter,java.util.ArrayList,java.lang.Object[]}, size=442, count=2}
{com.sun.org.apache.xerces.internal.impl.xs.opti.SchemaParsingConfig,com.sun.org.apache.xerces.internal.impl.XMLDTDScannerImpl,java.util.ArrayList,java.lang.Object[]}, size=441, count=1}
{com.sun.org.apache.xerces.internal.parsers.XIncludeAwareParserConfiguration,com.sun.org.apache.xerces.internal.impl.XMLDTDScannerImpl,java.util.ArrayList,java.lang.Object[]}, size=441, count=1}
{com.sun.jmx.mbeanserver.WeakIdentityHashMap$IdentityWeakReference,SomeType74,java.util.ArrayList,java.lang.Object[]}, size=440, count=2}
{SomeType135,SomeType136,java.util.ArrayList,java.lang.Object[]}, size=440, count=1}
{org.apache.ignite.spi.discovery.tcp.TcpDiscoveryImpl$1,org.apache.ignite.spi.discovery.tcp.ServerImpl,java.util.ArrayDeque,java.lang.Object[]}, size=438, count=1}
{org.apache.ignite.spi.discovery.tcp.TcpDiscoveryImpl$2,org.apache.ignite.spi.discovery.tcp.ServerImpl,java.util.ArrayDeque,java.lang.Object[]}, size=438, count=1}
{org.jsr166.ConcurrentHashMap8$Node,SomeType78,java.util.Vector,java.lang.Object[]}, size=437, count=1}
{javax.management.openmbean.OpenMBeanOperationInfoSupport,javax.management.openmbean.SimpleType,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=436, count=2}
{com.google.common.util.concurrent.ServiceManager$ServiceManagerState,com.google.common.collect.Multimaps$CustomSetMultimap,java.util.EnumMap,java.lang.Object[]}, size=432, count=2}
{okhttp3.OkHttpClient,okhttp3.ConnectionPool,java.util.ArrayDeque,java.lang.Object[]}, size=432, count=1}
{org.eclipse.jetty.util.thread.QueuedThreadPool$3,org.eclipse.jetty.util.thread.QueuedThreadPool,org.eclipse.jetty.util.BlockingArrayQueue,java.lang.Object[]}, size=430, count=1}
{org.h2.store.LobStorageMap,org.h2.mvstore.MVMap,org.h2.mvstore.ConcurrentArrayList,java.lang.Object[]}, size=429, count=1}
{org.eclipse.jetty.util.component.ContainerLifeCycle$Bean,org.eclipse.jetty.server.Server,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=428, count=1}
{org.apache.ignite.internal.processors.cache.jta.CacheNoopJtaManager,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=426, count=1}
{org.apache.ignite.internal.processors.cache.transactions.IgniteTxHandler,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=426, count=1}
{org.apache.ignite.internal.processors.cache.persistence.wal.serializer.TxRecordSerializer,SomeType78,java.util.Vector,java.lang.Object[]}, size=425, count=1}
{java.util.HashMap$Node,com.mchange.v2.cfg.MConfig$PathsKey,java.util.ArrayList,java.lang.Object[]}, size=424, count=2}
{java.util.HashMap$Node,com.mchange.v2.cfg.MConfig$PathsKey,java.util.ArrayList,java.lang.Object[]}, size=424, count=2}
{org.jsr166.ConcurrentHashMap8$Node,org.apache.ignite.internal.managers.eventstorage.GridEventStorageManager$Listeners,java.util.ArrayList,java.lang.Object[]}, size=424, count=2}
{java.util.HashMap$Node,com.mchange.v2.cfg.MConfig$PathsKey,java.util.ArrayList,java.lang.Object[]}, size=424, count=2}
{javax.management.openmbean.ArrayType,javax.management.openmbean.SimpleType,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=422, count=2}
{sun.net.www.protocol.jar.URLJarFile,java.util.jar.JarVerifier,java.util.ArrayList,java.lang.Object[]}, size=422, count=1}
{org.apache.logging.log4j.core.pattern.LiteralPatternConverter,org.apache.logging.log4j.core.config.DefaultConfiguration,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=420, count=4}
{java.util.LinkedList$Node,org.springframework.beans.factory.config.ConstructorArgumentValues$ValueHolder,org.springframework.beans.factory.support.ManagedList,java.lang.Object[]}, size=420, count=2}
{java.util.LinkedList$Node,org.springframework.beans.factory.config.ConstructorArgumentValues$ValueHolder,org.springframework.beans.factory.support.ManagedList,java.lang.Object[]}, size=420, count=2}
{ch.qos.logback.classic.joran.action.RootLoggerAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=418, count=1}
{ch.qos.logback.core.joran.action.NestedBasicPropertyIA,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=418, count=1}
{ch.qos.logback.core.joran.spi.InterpretationContext,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=418, count=1}
{ch.qos.logback.core.joran.conditional.ElseAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=418, count=1}
{ch.qos.logback.core.joran.conditional.ThenAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=418, count=1}
{ch.qos.logback.core.joran.action.NestedComplexPropertyIA,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=418, count=1}
{ch.qos.logback.core.joran.spi.CAI_WithLocatorSupport,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=418, count=1}
{ch.qos.logback.core.joran.conditional.IfAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=418, count=1}
{ch.qos.logback.classic.joran.action.LoggerAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=418, count=1}
{ch.qos.logback.classic.joran.JoranConfigurator,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=418, count=1}
{ch.qos.logback.core.joran.spi.ConfigurationWatchList,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=418, count=1}
{org.apache.kafka.clients.consumer.internals.AbstractCoordinator$GroupCoordinatorMetrics,org.apache.kafka.common.metrics.Metrics,java.util.ArrayList,java.lang.Object[]}, size=416, count=2}
{org.apache.kafka.clients.consumer.internals.Fetcher$FetchManagerMetrics,org.apache.kafka.common.metrics.Metrics,java.util.ArrayList,java.lang.Object[]}, size=416, count=2}
{org.apache.ignite.internal.processors.cache.persistence.wal.serializer.RecordV1Serializer,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=416, count=1}
{org.apache.kafka.clients.consumer.KafkaConsumer,org.apache.kafka.common.metrics.Metrics,java.util.ArrayList,java.lang.Object[]}, size=416, count=2}
{org.apache.kafka.clients.consumer.internals.ConsumerCoordinator$ConsumerCoordinatorMetrics,org.apache.kafka.common.metrics.Metrics,java.util.ArrayList,java.lang.Object[]}, size=416, count=2}
{org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi,org.apache.ignite.spi.discovery.tcp.ServerImpl,java.util.ArrayDeque,java.lang.Object[]}, size=414, count=1}
{org.eclipse.jetty.server.ServerConnector,org.eclipse.jetty.server.ServerConnector$ServerConnectorManager,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=411, count=1}
{org.apache.ignite.internal.processors.cache.distributed.GridCacheTxFinishSync,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=410, count=1}
{com.sun.org.apache.xerces.internal.impl.xs.opti.SchemaParsingConfig,com.sun.org.apache.xerces.internal.impl.validation.ValidationManager,java.util.Vector,java.lang.Object[]}, size=409, count=1}
{java.util.HashMap$Node,javax.management.remote.rmi.RMIConnectorServer,java.util.ArrayList,java.lang.Object[]}, size=408, count=2}
{org.apache.xerces.impl.xs.XMLSchemaLoaderExt,org.apache.xerces.util.ParserConfigurationSettings,java.util.ArrayList,java.lang.Object[]}, size=408, count=1}
{org.apache.xerces.impl.xs.XMLSchemaLoaderExt,org.apache.xerces.util.ParserConfigurationSettings,java.util.ArrayList,java.lang.Object[]}, size=408, count=1}
{org.apache.xerces.impl.xs.XMLSchemaLoaderExt,org.apache.xerces.util.ParserConfigurationSettings,java.util.ArrayList,java.lang.Object[]}, size=408, count=1}
{javax.management.remote.rmi.RMIJRMPServerImpl,javax.management.remote.rmi.RMIConnectorServer,java.util.ArrayList,java.lang.Object[]}, size=408, count=2}
{org.eclipse.jetty.server.ServerConnector$ServerConnectorManager,org.eclipse.jetty.util.thread.QueuedThreadPool,org.eclipse.jetty.util.BlockingArrayQueue,java.lang.Object[]}, size=406, count=1}
{org.eclipse.jetty.server.ServerConnector,org.eclipse.jetty.util.thread.QueuedThreadPool,org.eclipse.jetty.util.BlockingArrayQueue,java.lang.Object[]}, size=406, count=1}
{sun.security.x509.SubjectAlternativeNameExtension,sun.security.x509.GeneralNames,java.util.ArrayList,java.lang.Object[]}, size=402, count=2}
{org.apache.ignite.internal.processors.cache.transactions.TxDeadlockDetection,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=402, count=1}
{SomeType134,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=402, count=1}
{org.apache.ignite.internal.managers.discovery.ConsistentIdMapper,org.apache.ignite.internal.managers.discovery.GridDiscoveryManager,java.util.ArrayList,java.lang.Object[]}, size=400, count=5}
{org.apache.curator.framework.imps.NamespaceFacade,java.util.concurrent.DelayQueue,java.util.PriorityQueue,java.lang.Object[]}, size=400, count=2}
{org.apache.kafka.common.errors.GroupCoordinatorNotAvailableException,java.util.Collections$UnmodifiableRandomAccessList,java.util.ArrayList,java.lang.Object[]}, size=400, count=2}
{org.apache.curator.framework.imps.CuratorFrameworkImpl,java.util.concurrent.DelayQueue,java.util.PriorityQueue,java.lang.Object[]}, size=400, count=2}
{org.apache.kafka.common.errors.GroupCoordinatorNotAvailableException,java.util.Collections$UnmodifiableRandomAccessList,java.util.ArrayList,java.lang.Object[]}, size=400, count=2}
{org.apache.kafka.common.errors.GroupCoordinatorNotAvailableException,java.util.Collections$UnmodifiableRandomAccessList,java.util.ArrayList,java.lang.Object[]}, size=400, count=2}
{oracle.jrockit.jfr.MetaProducer,com.oracle.jrockit.jfr.Producer,java.util.ArrayList,java.lang.Object[]}, size=399, count=1}
{SomeType102,SomeType86,java.util.ArrayList,java.lang.Object[]}, size=398, count=2}
{org.apache.curator.framework.state.ConnectionStateManager$1,org.apache.curator.framework.state.ConnectionStateManager,java.util.concurrent.ArrayBlockingQueue,java.lang.Object[]}, size=396, count=1}
{org.apache.curator.framework.state.ConnectionStateManager$1,org.apache.curator.framework.state.ConnectionStateManager,java.util.concurrent.ArrayBlockingQueue,java.lang.Object[]}, size=396, count=1}
{org.eclipse.jetty.server.ServerConnector,org.eclipse.jetty.server.Server,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=396, count=1}
{org.eclipse.jetty.server.Server,org.eclipse.jetty.server.Server,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=396, count=1}
{org.apache.ignite.internal.processors.cache.transactions.IgniteTransactionsImpl,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=394, count=1}
{sun.reflect.DelegatingClassLoader,sun.reflect.misc.MethodUtil,java.util.Vector,java.lang.Object[]}, size=394, count=1}
{org.apache.ignite.internal.ClusterLocalNodeMetricsMXBeanImpl,org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode,java.util.ArrayList,java.lang.Object[]}, size=394, count=1}
{org.apache.ignite.internal.managers.deployment.GridDeploymentClassLoader,SomeType78,java.util.Vector,java.lang.Object[]}, size=393, count=1}
{org.apache.ignite.internal.processors.cache.GridCacheDeploymentManager$CacheClassLoader,SomeType78,java.util.Vector,java.lang.Object[]}, size=393, count=1}
{SomeType115,SomeType79,java.util.Vector,java.lang.Object[]}, size=393, count=1}
{SomeType79,SomeType79,java.util.Vector,java.lang.Object[]}, size=393, count=1}
{ch.qos.logback.core.joran.spi.ConfigurationWatchList,ch.qos.logback.core.joran.spi.ConfigurationWatchList,java.util.ArrayList,java.lang.Object[]}, size=392, count=1}
{javax.management.remote.rmi.RMIConnectorServer,javax.management.remote.rmi.RMIJRMPServerImpl,java.util.ArrayList,java.lang.Object[]}, size=392, count=2}
{ch.qos.logback.core.joran.spi.ConfigurationWatchList,ch.qos.logback.core.joran.spi.ConfigurationWatchList,java.util.ArrayList,java.lang.Object[]}, size=392, count=1}
{ch.qos.logback.core.joran.spi.ConfigurationWatchList,ch.qos.logback.core.joran.spi.ConfigurationWatchList,java.util.ArrayList,java.lang.Object[]}, size=392, count=1}
{com.sun.jmx.mbeanserver.MBeanServerDelegateImpl,javax.management.MBeanInfo,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=386, count=2}
{sun.management.jmxremote.ConnectorBootstrap$PermanentExporter,javax.management.remote.rmi.RMIJRMPServerImpl$ExportedWrapper,java.util.ArrayList,java.lang.Object[]}, size=384, count=2}
{org.apache.ignite.internal.processors.cache.GridCachePartitionExchangeManager$ExchangeWorker,org.apache.ignite.internal.processors.cache.GridCachePartitionExchangeManager,java.util.ArrayList,java.lang.Object[]}, size=381, count=1}
{net.sf.saxon.serialize.ReconfigurableSerializer,net.sf.saxon.serialize.XMLIndenter,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=378, count=2}
{net.sf.saxon.serialize.ReconfigurableSerializer,net.sf.saxon.serialize.XMLIndenter,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=378, count=2}
{java.util.HashMap$Node,ch.qos.logback.core.joran.spi.ConfigurationWatchList,java.util.ArrayList,java.lang.Object[]}, size=376, count=1}
{com.mchange.v2.c3p0.impl.NewPooledConnection,java.sql.SQLException,java.sql.SQLException,java.lang.Object[]}, size=376, count=1}
{java.util.HashMap$Node,ch.qos.logback.core.joran.spi.ConfigurationWatchList,java.util.ArrayList,java.lang.Object[]}, size=376, count=1}
{java.util.HashMap$Node,ch.qos.logback.core.joran.spi.ConfigurationWatchList,java.util.ArrayList,java.lang.Object[]}, size=376, count=1}
{SomeType76,ch.qos.logback.classic.Logger,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=375, count=1}
{org.apache.curator.framework.imps.NamespaceFacade,org.apache.curator.framework.state.ConnectionStateManager,java.util.concurrent.ArrayBlockingQueue,java.lang.Object[]}, size=372, count=1}
{org.apache.curator.framework.imps.NamespaceFacade,org.apache.curator.framework.state.ConnectionStateManager,java.util.concurrent.ArrayBlockingQueue,java.lang.Object[]}, size=372, count=1}
{java.util.concurrent.ConcurrentHashMap$Node,java.util.concurrent.ConcurrentHashMap$Node,org.springframework.context.annotation.ConfigurationClassBeanDefinitionReader$ConfigurationClassBeanDefinition,java.lang.Object[]}, size=371, count=1}
{org.apache.ignite.internal.processors.cache.persistence.wal.FileWriteAheadLogManager,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=370, count=1}
{org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi,org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode,java.util.ArrayList,java.lang.Object[]}, size=370, count=1}
{org.apache.ignite.internal.processors.cache.persistence.wal.serializer.TxRecordSerializer,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=370, count=1}
{org.apache.ignite.internal.processors.cache.GridCachePartitionExchangeManager,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=370, count=1}
{org.apache.ignite.internal.processors.cache.GridCacheProcessor,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=370, count=1}
{org.apache.ignite.internal.processors.cache.persistence.GridCacheDatabaseSharedManager,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=370, count=1}
{SomeType133,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=370, count=1}
{org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi,org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode,java.util.ArrayList,java.lang.Object[]}, size=370, count=1}
{org.apache.ignite.internal.processors.cache.GridCacheDeploymentManager,org.apache.ignite.internal.processors.cache.GridCacheSharedContext,java.util.ArrayList,java.lang.Object[]}, size=370, count=1}
{java.util.WeakHashMap$Entry,com.mchange.v2.c3p0.impl.NewPooledConnection,java.sql.SQLException,java.lang.Object[]}, size=368, count=1}
{org.springframework.beans.factory.support.DisposableBeanAdapter,SomeType81,java.util.ArrayList,java.lang.Object[]}, size=367, count=1}
{org.springframework.beans.factory.support.DisposableBeanAdapter,SomeType81,java.util.ArrayList,java.lang.Object[]}, size=367, count=1}
{org.h2.message.TraceSystem,org.h2.message.TraceSystem,java.util.concurrent.atomic.AtomicReferenceArray,java.lang.Object[]}, size=364, count=1}
{ch.qos.logback.core.recovery.ResilientFileOutputStream,SomeType76,ch.qos.logback.classic.spi.TurboFilterList,java.lang.Object[]}, size=363, count=3}
{ch.qos.logback.core.recovery.ResilientFileOutputStream,SomeType76,ch.qos.logback.classic.spi.TurboFilterList,java.lang.Object[]}, size=363, count=3}
{org.apache.ignite.internal.processors.rest.protocols.tcp.GridTcpRestProtocol,org.apache.ignite.internal.util.nio.GridNioServer,java.util.ArrayList,java.lang.Object[]}, size=359, count=1}
{org.apache.ignite.internal.processors.rest.protocols.tcp.GridTcpRestNioListener,org.apache.ignite.internal.processors.rest.protocols.tcp.redis.GridRedisNioListener,java.util.EnumMap,java.lang.Object[]}, size=356, count=1}
{SomeType75,sun.misc.URLClassPath,java.util.ArrayList,java.lang.Object[]}, size=355, count=1}
{org.apache.curator.framework.recipes.locks.LockInternals,org.apache.curator.framework.imps.NamespaceFacade,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=353, count=1}
{SomeType132,com.google.common.util.concurrent.ServiceManager,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=352, count=2}
{SomeType76,ch.qos.logback.classic.Logger,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=351, count=1}
{com.sun.org.apache.xerces.internal.jaxp.validation.XMLSchemaValidatorComponentManager,com.sun.org.apache.xerces.internal.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=349, count=1}
{com.sun.org.apache.xerces.internal.impl.xs.opti.SchemaParsingConfig,com.sun.org.apache.xerces.internal.impl.XMLEntityManager,java.util.Stack,java.lang.Object[]}, size=349, count=1}
{org.apache.ignite.internal.processors.odbc.ClientListenerProcessor,org.apache.ignite.internal.util.nio.GridNioServer,java.util.ArrayList,java.lang.Object[]}, size=347, count=1}
{com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory$IdentityMapping,javax.management.openmbean.SimpleType,javax.management.ImmutableDescriptor,java.lang.Object[]}, size=346, count=2}
{java.util.concurrent.ConcurrentHashMap$Node,SomeType81,java.util.ArrayList,java.lang.Object[]}, size=345, count=1}
{java.util.concurrent.ConcurrentHashMap$Node,SomeType81,java.util.ArrayList,java.lang.Object[]}, size=345, count=1}
{org.apache.logging.log4j.core.config.DefaultConfiguration,org.apache.logging.log4j.core.config.LoggerConfig,java.util.ArrayList,java.lang.Object[]}, size=339, count=1}
{org.apache.logging.log4j.core.config.NullConfiguration,org.apache.logging.log4j.core.config.LoggerConfig,java.util.ArrayList,java.lang.Object[]}, size=339, count=1}
{ch.qos.logback.classic.joran.action.ContextNameAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.action.IncludeAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.util.beans.BeanDescriptionCache,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.action.NestedBasicPropertyIA,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.action.NewRuleAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.classic.joran.action.ConfigurationAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.action.DefinePropertyAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.spi.InterpretationContext,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.classic.joran.action.ConsolePluginAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.classic.joran.action.RootLoggerAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.spi.ConfigurationWatchList,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.classic.selector.DefaultContextSelector,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.util.beans.BeanDescriptionFactory,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.action.ConversionRuleAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.action.AppenderAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.action.ShutdownHookAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.classic.joran.action.ConsolePluginAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.action.NewRuleAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.util.beans.BeanDescriptionFactory,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.action.DefinePropertyAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{SomeType130,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.ConsoleAppender,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.action.NestedBasicPropertyIA,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.conditional.IfAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.spi.CAI_WithLocatorSupport,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.action.ConversionRuleAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.classic.joran.action.RootLoggerAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.classic.joran.action.ConfigurationAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.classic.joran.action.ContextNameAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.action.ShutdownHookAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.action.ContextPropertyAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.action.AppenderAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.classic.joran.action.LoggerContextListenerAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.classic.joran.action.LoggerAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.action.NestedComplexPropertyIA,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.spi.ConfigurationWatchList,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.spi.SimpleRuleStore,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.action.IncludeAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.classic.joran.action.InsertFromJNDIAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.action.TimestampAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.action.ParamAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.status.InfoStatus,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.classic.joran.action.ReceiverAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.classic.joran.JoranConfigurator,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{SomeType130,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.classic.sift.SiftAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.action.StatusListenerAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.conditional.ElseAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.ConsoleAppender,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.conditional.ThenAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.classic.joran.action.EvaluatorAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.conditional.ElseAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.action.StatusListenerAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.classic.joran.action.JMXConfiguratorAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.action.TimestampAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.classic.joran.action.EvaluatorAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.classic.joran.action.JMXConfiguratorAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.classic.joran.JoranConfigurator,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.status.InfoStatus,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.action.ContextPropertyAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.util.beans.BeanDescriptionCache,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.classic.sift.SiftAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.conditional.ThenAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.classic.joran.action.InsertFromJNDIAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.action.NestedComplexPropertyIA,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.classic.joran.action.LoggerAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.classic.joran.action.LoggerContextListenerAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.classic.selector.DefaultContextSelector,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.classic.joran.action.ReceiverAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.spi.InterpretationContext,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.action.ParamAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.spi.CAI_WithLocatorSupport,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.conditional.IfAction,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{ch.qos.logback.core.joran.spi.SimpleRuleStore,SomeType76,java.util.ArrayList,java.lang.Object[]}, size=338, count=1}
{org.eclipse.jetty.util.component.ContainerLifeCycle$Bean,org.apache.ignite.internal.processors.rest.protocols.http.jetty.GridJettyRestHandler,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=337, count=1}
{java.util.LinkedHashMap$Entry,org.eclipse.jetty.server.HttpConnectionFactory,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=337, count=1}
{ch.qos.logback.core.rolling.helper.FileNamePattern,SomeType76,ch.qos.logback.classic.spi.TurboFilterList,java.lang.Object[]}, size=336, count=6}
{SomeType88,org.apache.curator.framework.imps.CuratorFrameworkImpl,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=333, count=1}
{java.io.ObjectInputStream$BlockDataInputStream,org.apache.ignite.marshaller.jdk.JdkMarshallerObjectInputStream,java.io.ObjectInputStream$HandleTable,java.lang.Object[]}, size=333, count=1}
{com.mchange.v2.c3p0.util.ConnectionEventSupport,com.mchange.v2.c3p0.impl.NewPooledConnection,java.sql.SQLException,java.lang.Object[]}, size=332, count=1}
{com.mchange.v2.c3p0.util.StatementEventSupport,com.mchange.v2.c3p0.impl.NewPooledConnection,java.sql.SQLException,java.lang.Object[]}, size=332, count=1}
{java.util.HashMap$Node,com.sun.org.apache.xerces.internal.impl.dtd.XMLDTDProcessor,java.util.ArrayList,java.lang.Object[]}, size=331, count=1}
{org.h2.mvstore.db.TransactionStore,org.h2.mvstore.MVMap,org.h2.mvstore.Page,java.lang.Object[]}, size=331, count=1}
{org.springframework.beans.factory.support.DisposableBeanAdapter,SomeType83,java.util.ArrayList,java.lang.Object[]}, size=330, count=1}
{com.sun.org.apache.xerces.internal.jaxp.validation.XMLSchemaValidatorComponentManager,com.sun.org.apache.xerces.internal.impl.validation.ValidationManager,java.util.Vector,java.lang.Object[]}, size=329, count=1}
{net.sf.saxon.PreparedStylesheet,net.sf.saxon.expr.instruct.SlotManager,java.util.ArrayList,java.lang.Object[]}, size=328, count=2}
{net.sf.saxon.PreparedStylesheet,net.sf.saxon.expr.instruct.SlotManager,java.util.ArrayList,java.lang.Object[]}, size=328, count=2}
{java.util.LinkedList$Node,org.apache.ignite.internal.processors.cache.GridCachePartitionExchangeManager,java.util.ArrayList,java.lang.Object[]}, size=328, count=1}
{net.sf.saxon.PreparedStylesheet,net.sf.saxon.expr.instruct.SlotManager,java.util.ArrayList,java.lang.Object[]}, size=328, count=2}
{ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy,SomeType76,ch.qos.logback.classic.spi.TurboFilterList,java.lang.Object[]}, size=327, count=3}
{ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy,SomeType76,ch.qos.logback.classic.spi.TurboFilterList,java.lang.Object[]}, size=327, count=3}
{net.sf.saxon.tree.linked.TextImpl,net.sf.saxon.style.XSLAttribute,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=326, count=1}
{net.sf.saxon.tree.linked.TextImpl,net.sf.saxon.style.XSLAttribute,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=326, count=1}
{net.sf.saxon.tree.linked.TextImpl,net.sf.saxon.style.XSLAttribute,net.sf.saxon.tree.util.AttributeCollectionImpl,long[]}, size=326, count=1}
{org.apache.curator.framework.state.ConnectionStateManager,org.apache.curator.framework.imps.CuratorFrameworkImpl,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=325, count=1}
{org.apache.curator.framework.state.ConnectionStateManager,org.apache.curator.framework.imps.CuratorFrameworkImpl,com.google.common.collect.RegularImmutableList,java.lang.Object[]}, size=325, count=1}
{java.util.LinkedHashMap$Entry,SomeType147,java.util.ArrayList,java.lang.Object[]}, size=324, count=1}
{org.apache.ignite.logger.slf4j.Slf4jLogger,ch.qos.logback.classic.Logger,java.util.concurrent.CopyOnWriteArrayList,java.lang.Object[]}, size=324, count=2}
{org.springframework.context.annotation.ConfigurationClassBeanDefinitionReader,org.springframework.context.support.ClassPathXmlApplicationContext,java.util.ArrayList,java.lang.Object[]}, size=324, count=1}
{org.apache.ignite.internal.processors.cache.GridCacheProcessor,org.apache.ignite.internal.processors.cache.ClusterCachesInfo,java.util.ArrayList,java.lang.Object[]}, size=322, count=1}
{SomeType67,java.util.Collections$UnmodifiableRandomAccessList,java.util.ArrayList,java.lang.Object[]}, size=320, count=2}
{java.util.Collections$SynchronizedMap,java.util.Collections$SynchronizedMap,java.util.IdentityHashMap,java.lang.Object[]}, size=320, count=1}
{com.sun.xml.internal.bind.v2.runtime.LeafBeanInfoImpl,com.sun.xml.internal.bind.v2.model.impl.RuntimeEnumLeafInfoImpl,java.util.EnumMap,java.lang.Object[]}, size=320, count=1}
{org.postgresql.jdbc4.Jdbc4Connection,org.postgresql.core.v3.ProtocolConnectionImpl,java.util.ArrayList,java.lang.Object[]}, size=317, count=1}
```

