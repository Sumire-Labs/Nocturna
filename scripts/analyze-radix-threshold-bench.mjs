import fs from 'node:fs';
import assert from 'node:assert/strict';

const [input='.git/research/radix-threshold-bench/thresholds.json',output='.git/research/radix-threshold-bench/analysis.json']=process.argv.slice(2);
const results=JSON.parse(fs.readFileSync(input,'utf8').replace(/^\uFEFF/,''));
const mean=values=>values.reduce((a,b)=>a+b,0)/values.length;
const median=values=>{const sorted=[...values].sort((a,b)=>a-b);return sorted.length%2?sorted[sorted.length>>1]:(sorted[sorted.length/2-1]+sorted[sorted.length/2])/2;};
const rows=results.map(r=>{
    assert.equal(r.mode,'avgt');assert.equal(r.primaryMetric.scoreUnit,'ns/op');
    const forkMeans=r.primaryMetric.rawData.map(mean);
    return {pattern:r.params.pattern,size:Number(r.params.size),threshold:Number(r.params.threshold),
        meanNs:r.primaryMetric.score,errorNs:r.primaryMetric.scoreError,confidence:r.primaryMetric.scoreConfidence,
        forkMeansNs:forkMeans,medianForkNs:median(forkMeans),
        allocatedBytes:r.secondaryMetrics['gc.alloc.rate.norm'].score};
}).sort((a,b)=>a.pattern.localeCompare(b.pattern)||a.size-b.size||a.threshold-b.threshold);
const thresholds=[64,128,256];
const key=r=>r.pattern+':'+r.size;
const reference=new Map(rows.filter(r=>r.threshold===64).map(r=>[key(r),r]));
const summary=thresholds.map(threshold=>{
    const selected=rows.filter(r=>r.threshold===threshold);assert.equal(selected.length,reference.size);
    return {threshold,equalWeightMeanNs:mean(selected.map(r=>r.meanNs)),
        geometricMeanRatioTo64:Math.exp(mean(selected.map(r=>Math.log(r.meanNs/reference.get(key(r)).meanNs)))),
        equalWeightMeanBytes:mean(selected.map(r=>r.allocatedBytes))};
});
const patterns=[...new Set(rows.map(r=>r.pattern))].map(pattern=>({pattern,policies:thresholds.map(threshold=>{
    const selected=rows.filter(r=>r.pattern===pattern&&r.threshold===threshold);
    return {threshold,meanNs:mean(selected.map(r=>r.meanNs)),medianForkNs:mean(selected.map(r=>r.medianForkNs))};
})}));
fs.writeFileSync(output,JSON.stringify({input,summary,patterns,rows},null,2)+'\n');
console.log(JSON.stringify({summary,patterns},null,2));
console.log('Random positions, ns/sort: size | cutoff 64 | cutoff 128 | cutoff 256');
for(const size of [...new Set(rows.map(r=>r.size))].sort((a,b)=>a-b))console.log([size,...thresholds.map(t=>rows.find(r=>r.pattern==='random'&&r.size===size&&r.threshold===t)?.meanNs.toFixed(1))].join(' | '));
