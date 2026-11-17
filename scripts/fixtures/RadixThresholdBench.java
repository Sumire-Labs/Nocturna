package org.embeddedt.embeddium.bench;

import com.mojang.blaze3d.vertex.VertexSorting;
import org.embeddedt.embeddium.impl.modern.sorting.VertexSorters64;
import org.embeddedt.embeddium.impl.modern.sorting.VertexSorters128;
import org.embeddedt.embeddium.impl.modern.sorting.VertexSorters256;
import org.embeddedt.embeddium.impl.util.sorting.MergeSort;
import org.joml.Vector3f;
import org.openjdk.jmh.annotations.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Measures the production distance-sort path; only the radix cutoff differs between generated variants. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class RadixThresholdBench {
    @Param({"64", "128", "256"}) public int threshold;
    @Param({"64", "96", "128", "192", "255"}) public int size;
    @Param({"random", "nearly_sorted", "sorted", "ties"}) public String pattern;
    private static final int SAMPLES=128;
    private Vector3f[][] positions;
    private VertexSorting sorter;
    private int cursor;
    private final Vector3f origin=new Vector3f(.125F,1.75F,-.25F);

    @Setup(Level.Trial) public void setup(){
        positions=new Vector3f[SAMPLES][];
        var random=new SplittableRandom(580L+31L*size+pattern.hashCode());
        var variants=new VertexSorting[]{VertexSorters64.sortByDistance(origin),VertexSorters128.sortByDistance(origin),VertexSorters256.sortByDistance(origin)};
        for(int sample=0;sample<SAMPLES;sample++){
            var input=new Vector3f[size];
            for(int i=0;i<size;i++){
                if(pattern.equals("ties")){int group=random.nextInt(8);input[i]=new Vector3f(group-4,group%3,group%2);}
                else input[i]=new Vector3f((float)random.nextDouble(-16,16),(float)random.nextDouble(-16,16),(float)random.nextDouble(-16,16));
            }
            if(pattern.equals("sorted")||pattern.equals("nearly_sorted")){
                Arrays.sort(input,(a,b)->Float.compare(origin.distanceSquared(b),origin.distanceSquared(a)));
                if(pattern.equals("nearly_sorted"))for(int i=0;i<Math.max(1,size/32);i++){int a=random.nextInt(size),b=random.nextInt(size);var old=input[a];input[a]=input[b];input[b]=old;}
            }
            var keys=new float[size];for(int i=0;i<size;i++)keys[i]=origin.distanceSquared(input[i]);
            int[] expected=MergeSort.mergeSort(keys);
            for(var variant:variants)if(!Arrays.equals(expected,variant.sort(input)))throw new AssertionError("Different permutation: "+pattern+"/"+size);
            positions[sample]=input;
        }
        sorter=variants[switch(threshold){case 64->0;case 128->1;case 256->2;default->throw new AssertionError(threshold);}];
    }

    @Benchmark public int[] distanceSort(){return sorter.sort(positions[(cursor++)&(SAMPLES-1)]);}
}
