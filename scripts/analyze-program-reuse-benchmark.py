"""Recalculate frame metrics without combining different JVMs or scene protocols."""
import argparse
import csv
import hashlib
import json
import math
import statistics
from collections import defaultdict
from pathlib import Path


def analyze(root, pattern='*.json'):
    runs = []
    groups = defaultdict(list)
    for file in sorted(root.glob(pattern)):
        data = json.loads(file.read_text(encoding='utf-8-sig'))
        if data.get('success') is not True or 'results' not in data:
            continue
        diagnostic = data.get('queryProbeEnabled', False) or 'query-probe' in data.get('label', '')
        for result in data['results']:
            if result['scenario'] != 'shader':
                continue
            raw = file.with_name(file.stem + '-shader.csv')
            with raw.open(encoding='utf-8-sig', newline='') as stream:
                frames = list(csv.DictReader(stream))
            durations = [int(frame['frame_ns']) for frame in frames]
            ordered = sorted(durations)
            assert len(durations) == result['frames'], file
            elapsed = sum(durations) / 1e9
            assert abs(elapsed - result['seconds']) < .000001, file
            fps = len(durations) / elapsed
            assert abs(fps - result['avgFps']) < .00001, file
            p99 = ordered[math.ceil(len(ordered) * .99) - 1] / 1e6
            assert abs(p99 - result['p99Ms']) < .000001, file
            bins = []
            for start in range(0, math.ceil(elapsed), 5):
                selected = [int(x['frame_ns']) for x in frames
                            if start * 1e9 < int(x['time_ns']) <= (start + 5) * 1e9]
                bins.append({'startSeconds': start,
                             'fps': len(selected) * 1e9 / sum(selected) if selected else None})
            row = {'label': data['label'], 'resultFile': file.name, 'java': data['java'],
                   'commit': data.get('sourceCommit'), 'artifactSha256': data.get('artifactSha256'),
                   'warmupSeconds': data['warmupSeconds'], 'measureSeconds': data['measureSeconds'],
                   'history': [r['scenario'] for r in data['results']], 'probe': diagnostic,
                   'animationStart': data.get('controlledAnimation'),
                   'fps': fps, 'p99Ms': p99, 'maxMs': ordered[-1] / 1e6,
                   'over50': sum(x > 50_000_000 for x in durations),
                   'over100': sum(x > 100_000_000 for x in durations),
                   'notificationQueriesPerFrame': result.get('notificationQueriesPerFrame'),
                   'updateQueriesPerFrame': result.get('updateQueriesPerFrame'),
                   'rawSha256': hashlib.sha256(raw.read_bytes()).hexdigest(), 'fiveSecondBins': bins,
                   'shaderClockStart': result.get('shaderClockStart'),
                   'shaderClockEnd': result.get('shaderClockEnd')}
            runs.append(row)
            if not diagnostic:
                key = (row['commit'], row['java'], row['warmupSeconds'], row['measureSeconds'],
                       tuple(row['history']), row['animationStart'])
                groups[key].append(row)
    summary = []
    for key, rows in groups.items():
        assert len({r['artifactSha256'] for r in rows}) == 1, key
        summary.append({'commit': key[0], 'java': key[1], 'warmupSeconds': key[2],
                        'measureSeconds': key[3], 'history': key[4], 'animationStart': key[5], 'repeats': len(rows),
                        'fpsMedian': statistics.median(r['fps'] for r in rows),
                        'fpsMin': min(r['fps'] for r in rows), 'fpsMax': max(r['fps'] for r in rows),
                        'p99MedianMs': statistics.median(r['p99Ms'] for r in rows),
                        'maxMs': max(r['maxMs'] for r in rows),
                        'over50': sum(r['over50'] for r in rows),
                        'over100': sum(r['over100'] for r in rows)})
    return {'runs': runs, 'summary': summary}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('result_dir', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--pattern', default='*.json')
    args = parser.parse_args()
    report = analyze(args.result_dir, args.pattern)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    for row in report['summary']:
        print(f"{row['commit']} Java {row['java']} warm={row['warmupSeconds']} "
              f"measure={row['measureSeconds']} scenes={len(row['history'])} n={row['repeats']} "
              f"FPS={row['fpsMedian']:.2f} [{row['fpsMin']:.2f}, {row['fpsMax']:.2f}] "
              f"p99={row['p99MedianMs']:.4f}ms max={row['maxMs']:.4f}ms")
