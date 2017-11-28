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

